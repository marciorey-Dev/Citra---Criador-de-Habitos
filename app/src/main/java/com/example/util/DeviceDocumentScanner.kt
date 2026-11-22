package com.example.util

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.example.data.local.RecentExternalDocEntry
import com.example.data.model.Note
import com.example.data.model.NoteDocumentType
import com.example.data.model.resolvedDocumentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class DiscoveredExternalDocument(
    val uri: String,
    val title: String,
    val docType: NoteDocumentType,
    val modifiedAt: Long,
    val sourceLabel: String,
    val isInVault: Boolean
)

/**
 * Play-Policy-compliant scanner that discovers accessible .pdf and .docx documents
 * across Citra Docs folders, MediaStore.Files, user-linked SAF folders, recent history,
 * and existing vault documents — without requiring prohibited storage permissions.
 */
object DeviceDocumentScanner {

    private const val MAX_RESULTS_PER_TYPE = 180
    private const val MAX_SAF_ITEMS_PER_TREE = 120

    suspend fun scanDocuments(
        context: Context,
        targetType: NoteDocumentType,
        recentDocs: List<RecentExternalDocEntry>,
        authorizedTreeUris: Set<String>,
        existingNotes: List<Note>
    ): List<DiscoveredExternalDocument> = withContext(Dispatchers.IO) {
        val activeVaultUris = existingNotes
            .filter { !it.isDeleted && it.resolvedDocumentType == targetType }
            .mapNotNull { it.externalUri?.trim()?.takeIf { u -> u.isNotBlank() } }
            .toSet()

        val collected = linkedMapOf<String, DiscoveredExternalDocument>()

        fun addCandidate(
            uri: String,
            title: String,
            modifiedAt: Long,
            sourceLabel: String
        ) {
            val cleanUri = uri.trim()
            if (cleanUri.isBlank()) return
            val existing = collected[cleanUri]
            val inVault = cleanUri in activeVaultUris
            if (existing == null) {
                collected[cleanUri] = DiscoveredExternalDocument(
                    uri = cleanUri,
                    title = title.ifBlank { "Documento" },
                    docType = targetType,
                    modifiedAt = modifiedAt,
                    sourceLabel = sourceLabel,
                    isInVault = inVault
                )
            } else if (modifiedAt > existing.modifiedAt || inVault) {
                collected[cleanUri] = existing.copy(
                    modifiedAt = maxOf(existing.modifiedAt, modifiedAt),
                    isInVault = existing.isInVault || inVault
                )
            }
        }

        // 1. Existing vault items of this type (so they remain searchable/discoverable)
        existingNotes
            .filter { !it.isDeleted && it.resolvedDocumentType == targetType && !it.externalUri.isNullOrBlank() }
            .forEach { note ->
                addCandidate(
                    uri = note.externalUri!!,
                    title = note.title,
                    modifiedAt = note.updatedAt,
                    sourceLabel = "No Cofre (${note.category})"
                )
            }

        // 2. Recent user-picked or opened documents
        recentDocs
            .filter { NoteDocumentType.fromId(it.docType) == targetType }
            .forEach { entry ->
                addCandidate(
                    uri = entry.uri,
                    title = entry.title,
                    modifiedAt = entry.addedAt,
                    sourceLabel = "Recente"
                )
            }

        // 3. Scan Citra Docs & app-accessible folders
        scanLocalAccessibleFolders(context, targetType).forEach { file ->
            val rawName = file.name.substringBeforeLast(".").ifBlank { file.name }
            addCandidate(
                uri = file.absolutePath,
                title = rawName,
                modifiedAt = file.lastModified(),
                sourceLabel = file.parentFile?.name ?: "Citra Docs"
            )
        }

        // 4. Scan user-authorized SAF directory trees
        authorizedTreeUris.forEach { treeUriStr ->
            scanSafTree(context, treeUriStr, targetType).forEach { doc ->
                addCandidate(
                    uri = doc.uri,
                    title = doc.title,
                    modifiedAt = doc.modifiedAt,
                    sourceLabel = doc.sourceLabel
                )
            }
        }

        // 5. Query MediaStore.Files for system-indexed PDF / DOCX documents
        scanMediaStoreFiles(context, targetType).forEach { doc ->
            addCandidate(
                uri = doc.uri,
                title = doc.title,
                modifiedAt = doc.modifiedAt,
                sourceLabel = doc.sourceLabel
            )
        }

        // Deduplicate identical titles + type when a content:// and file:// copy point to same doc name,
        // keeping the vault-linked or newest entry first
        val sorted = collected.values.sortedWith(
            compareByDescending<DiscoveredExternalDocument> { it.isInVault }
                .thenByDescending { it.modifiedAt }
                .thenBy { it.title.lowercase() }
        )

        sorted.take(MAX_RESULTS_PER_TYPE)
    }

    private fun scanLocalAccessibleFolders(
        context: Context,
        targetType: NoteDocumentType
    ): List<File> {
        val ext = if (targetType == NoteDocumentType.PDF) ".pdf" else ".docx"
        val folders = mutableListOf<File>()
        folders.addAll(CitraDocsFileManager.getAllDocsFolders(context))
        try {
            @Suppress("DEPRECATION")
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloads != null) folders.add(downloads)
        } catch (_: Exception) {}
        try {
            @Suppress("DEPRECATION")
            val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            if (docs != null) folders.add(docs)
        } catch (_: Exception) {}

        val found = mutableListOf<File>()
        val seenPaths = mutableSetOf<String>()
        for (folder in folders.distinctBy { it.absolutePath }) {
            if (!folder.exists() || !folder.isDirectory || !folder.canRead()) continue
            walkFolderDepth(folder, ext, depth = 0, maxDepth = 2, out = found, seenPaths = seenPaths)
            if (found.size >= MAX_RESULTS_PER_TYPE) break
        }
        return found
    }

    private fun walkFolderDepth(
        dir: File,
        ext: String,
        depth: Int,
        maxDepth: Int,
        out: MutableList<File>,
        seenPaths: MutableSet<String>
    ) {
        if (out.size >= MAX_RESULTS_PER_TYPE) return
        val children = try { dir.listFiles() } catch (_: Exception) { null } ?: return
        for (child in children) {
            if (out.size >= MAX_RESULTS_PER_TYPE) return
            if (child.isDirectory && depth < maxDepth && !child.name.startsWith(".")) {
                walkFolderDepth(child, ext, depth + 1, maxDepth, out, seenPaths)
            } else if (child.isFile && child.canRead() && child.name.lowercase().endsWith(ext) && child.length() > 0L) {
                val path = child.absolutePath
                if (seenPaths.add(path)) {
                    out.add(child)
                }
            }
        }
    }

    private fun scanMediaStoreFiles(
        context: Context,
        targetType: NoteDocumentType
    ): List<DiscoveredExternalDocument> {
        val results = mutableListOf<DiscoveredExternalDocument>()
        val ext = if (targetType == NoteDocumentType.PDF) ".pdf" else ".docx"
        val targetMime = if (targetType == NoteDocumentType.PDF) {
            "application/pdf"
        } else {
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        }

        val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.SIZE
        )

        val selection = "${MediaStore.Files.FileColumns.SIZE} > 0 AND (" +
            "${MediaStore.Files.FileColumns.MIME_TYPE} = ? OR " +
            "LOWER(${MediaStore.Files.FileColumns.DISPLAY_NAME}) LIKE ?)"
        val selectionArgs = arrayOf(targetMime, "%$ext")
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

        try {
            context.contentResolver.query(
                collectionUri,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val dateIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)

                while (cursor.moveToNext() && results.size < MAX_RESULTS_PER_TYPE) {
                    val id = cursor.getLong(idIdx)
                    val displayName = if (nameIdx != -1) cursor.getString(nameIdx).orEmpty() else ""
                    if (displayName.isBlank() || !displayName.lowercase().endsWith(ext)) continue
                    val modSec = if (dateIdx != -1) cursor.getLong(dateIdx) else 0L
                    val contentUri = ContentUris.withAppendedId(collectionUri, id)
                    val title = displayName.substringBeforeLast(".").ifBlank { displayName }
                    results.add(
                        DiscoveredExternalDocument(
                            uri = contentUri.toString(),
                            title = title,
                            docType = targetType,
                            modifiedAt = if (modSec > 0L) modSec * 1000L else System.currentTimeMillis(),
                            sourceLabel = "Armazenamento",
                            isInVault = false
                        )
                    )
                }
            }
        } catch (_: Throwable) {
            // Ignore provider/permission restrictions gracefully on scoped Android builds
        }
        return results
    }

    private fun scanSafTree(
        context: Context,
        treeUriStr: String,
        targetType: NoteDocumentType
    ): List<DiscoveredExternalDocument> {
        val results = mutableListOf<DiscoveredExternalDocument>()
        val ext = if (targetType == NoteDocumentType.PDF) ".pdf" else ".docx"
        try {
            val treeUri = Uri.parse(treeUriStr)
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            scanSafFolderRecursive(
                context = context,
                treeUri = treeUri,
                parentDocId = rootDocId,
                ext = ext,
                targetType = targetType,
                depth = 0,
                out = results
            )
        } catch (_: Throwable) {
        }
        return results
    }

    private fun scanSafFolderRecursive(
        context: Context,
        treeUri: Uri,
        parentDocId: String,
        ext: String,
        targetType: NoteDocumentType,
        depth: Int,
        out: MutableList<DiscoveredExternalDocument>
    ) {
        if (depth > 2 || out.size >= MAX_SAF_ITEMS_PER_TREE) return
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        try {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val modIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (cursor.moveToNext() && out.size < MAX_SAF_ITEMS_PER_TREE) {
                    val docId = if (idIdx != -1) cursor.getString(idIdx).orEmpty() else continue
                    val name = if (nameIdx != -1) cursor.getString(nameIdx).orEmpty() else ""
                    val mime = if (mimeIdx != -1) cursor.getString(mimeIdx).orEmpty() else ""
                    val mod = if (modIdx != -1) cursor.getLong(modIdx) else System.currentTimeMillis()

                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (!name.startsWith(".")) {
                            scanSafFolderRecursive(context, treeUri, docId, ext, targetType, depth + 1, out)
                        }
                    } else if (name.lowercase().endsWith(ext)) {
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        val title = name.substringBeforeLast(".").ifBlank { name }
                        out.add(
                            DiscoveredExternalDocument(
                                uri = docUri.toString(),
                                title = title,
                                docType = targetType,
                                modifiedAt = mod,
                                sourceLabel = "Pasta vinculada",
                                isInVault = false
                            )
                        )
                    }
                }
            }
        } catch (_: Throwable) {
        }
    }
}
