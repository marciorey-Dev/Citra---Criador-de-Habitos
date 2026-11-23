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
        val targetTypes: Set<NoteDocumentType> = if (targetType.isEbookOrComic) {
            NoteDocumentType.EBOOK_COMIC_TYPES
        } else {
            setOf(targetType)
        }

        val matchingVaultNotes = existingNotes.filter {
            !it.isDeleted && it.resolvedDocumentType in targetTypes && !it.externalUri.isNullOrBlank()
        }
        val activeVaultUris = matchingVaultNotes
            .mapNotNull { it.externalUri?.trim()?.takeIf { u -> u.isNotBlank() } }
            .toSet()

        // Match origin digests so external files copied to citra_imported_docs still show as imported
        val importedOriginTags = activeVaultUris
            .filter { ExternalDocumentUriHelper.isInternalImportedFile(context, it) }
            .mapNotNull { path ->
                val base = path.substringAfterLast('/').substringBeforeLast('.')
                val tag = base.substringAfterLast('_', "")
                tag.takeIf { it.length >= 6 }
            }
            .toSet()

        fun isUriImported(cleanUri: String): Boolean {
            if (cleanUri in activeVaultUris) return true
            if (importedOriginTags.isNotEmpty()) {
                val originTag = ExternalDocumentUriHelper.computeOriginTag(cleanUri)
                if (originTag in importedOriginTags) return true
            }
            return false
        }

        val collected = linkedMapOf<String, DiscoveredExternalDocument>()

        fun addCandidate(
            uri: String,
            title: String,
            docType: NoteDocumentType,
            modifiedAt: Long,
            sourceLabel: String
        ) {
            val cleanUri = uri.trim()
            if (cleanUri.isBlank() || docType !in targetTypes) return
            val existing = collected[cleanUri]
            val inVault = isUriImported(cleanUri)
            if (existing == null) {
                collected[cleanUri] = DiscoveredExternalDocument(
                    uri = cleanUri,
                    title = title.ifBlank { "Documento" },
                    docType = docType,
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

        // 1. Existing imported items of this type (so they remain searchable/discoverable)
        matchingVaultNotes.forEach { note ->
            addCandidate(
                uri = note.externalUri!!,
                title = note.title,
                docType = note.resolvedDocumentType,
                modifiedAt = note.updatedAt,
                sourceLabel = "Importado (${note.category})"
            )
        }

        // 2. Recent user-picked or opened documents
        recentDocs
            .filter { NoteDocumentType.fromId(it.docType) in targetTypes }
            .forEach { entry ->
                addCandidate(
                    uri = entry.uri,
                    title = entry.title,
                    docType = NoteDocumentType.fromId(entry.docType),
                    modifiedAt = entry.addedAt,
                    sourceLabel = "Recente"
                )
            }

        // 3. Scan Citra Docs & app-accessible folders
        scanLocalAccessibleFolders(context, targetTypes).forEach { (file, detectedType) ->
            val rawName = file.name.substringBeforeLast(".").ifBlank { file.name }
            addCandidate(
                uri = file.absolutePath,
                title = rawName,
                docType = detectedType,
                modifiedAt = file.lastModified(),
                sourceLabel = file.parentFile?.name ?: "Citra Docs"
            )
        }

        // 4. Scan user-authorized SAF directory trees
        authorizedTreeUris.forEach { treeUriStr ->
            scanSafTree(context, treeUriStr, targetTypes).forEach { doc ->
                addCandidate(
                    uri = doc.uri,
                    title = doc.title,
                    docType = doc.docType,
                    modifiedAt = doc.modifiedAt,
                    sourceLabel = doc.sourceLabel
                )
            }
        }

        // 5. Query MediaStore.Files for system-indexed documents
        scanMediaStoreFiles(context, targetTypes).forEach { doc ->
            addCandidate(
                uri = doc.uri,
                title = doc.title,
                docType = doc.docType,
                modifiedAt = doc.modifiedAt,
                sourceLabel = doc.sourceLabel
            )
        }

        val sorted = collected.values.sortedWith(
            compareByDescending<DiscoveredExternalDocument> { it.isInVault }
                .thenByDescending { it.modifiedAt }
                .thenBy { it.title.lowercase() }
        )

        sorted.take(MAX_RESULTS_PER_TYPE)
    }

    private fun matchExtensionToType(fileName: String, targetTypes: Set<NoteDocumentType>): NoteDocumentType? {
        val low = fileName.lowercase().trim()
        return when {
            low.endsWith(".pdf") && NoteDocumentType.PDF in targetTypes -> NoteDocumentType.PDF
            low.endsWith(".docx") && NoteDocumentType.DOCX in targetTypes -> NoteDocumentType.DOCX
            low.endsWith(".epub") && NoteDocumentType.EPUB in targetTypes -> NoteDocumentType.EPUB
            (low.endsWith(".mobi") || low.endsWith(".azw") || low.endsWith(".azw3")) &&
                NoteDocumentType.MOBI in targetTypes -> NoteDocumentType.MOBI
            low.endsWith(".cbz") && NoteDocumentType.CBZ in targetTypes -> NoteDocumentType.CBZ
            (low.endsWith(".fb2") || low.endsWith(".fb2.zip")) &&
                NoteDocumentType.FB2 in targetTypes -> NoteDocumentType.FB2
            else -> null
        }
    }

    private fun extensionsForTypes(targetTypes: Set<NoteDocumentType>): List<String> {
        val list = mutableListOf<String>()
        if (NoteDocumentType.PDF in targetTypes) list.add(".pdf")
        if (NoteDocumentType.DOCX in targetTypes) list.add(".docx")
        if (NoteDocumentType.EPUB in targetTypes) list.add(".epub")
        if (NoteDocumentType.MOBI in targetTypes) {
            list.add(".mobi"); list.add(".azw"); list.add(".azw3")
        }
        if (NoteDocumentType.CBZ in targetTypes) list.add(".cbz")
        if (NoteDocumentType.FB2 in targetTypes) list.add(".fb2")
        return list
    }

    private fun scanLocalAccessibleFolders(
        context: Context,
        targetTypes: Set<NoteDocumentType>
    ): List<Pair<File, NoteDocumentType>> {
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

        val found = mutableListOf<Pair<File, NoteDocumentType>>()
        val seenPaths = mutableSetOf<String>()
        for (folder in folders.distinctBy { it.absolutePath }) {
            if (!folder.exists() || !folder.isDirectory || !folder.canRead()) continue
            walkFolderDepth(folder, targetTypes, depth = 0, maxDepth = 2, out = found, seenPaths = seenPaths)
            if (found.size >= MAX_RESULTS_PER_TYPE) break
        }
        return found
    }

    private fun walkFolderDepth(
        dir: File,
        targetTypes: Set<NoteDocumentType>,
        depth: Int,
        maxDepth: Int,
        out: MutableList<Pair<File, NoteDocumentType>>,
        seenPaths: MutableSet<String>
    ) {
        if (out.size >= MAX_RESULTS_PER_TYPE) return
        val children = try { dir.listFiles() } catch (_: Exception) { null } ?: return
        for (child in children) {
            if (out.size >= MAX_RESULTS_PER_TYPE) return
            if (child.isDirectory && depth < maxDepth && !child.name.startsWith(".")) {
                walkFolderDepth(child, targetTypes, depth + 1, maxDepth, out, seenPaths)
            } else if (child.isFile && child.canRead() && child.length() > 0L) {
                val matchedType = matchExtensionToType(child.name, targetTypes)
                if (matchedType != null) {
                    val path = child.absolutePath
                    if (seenPaths.add(path)) {
                        out.add(child to matchedType)
                    }
                }
            }
        }
    }

    private fun scanMediaStoreFiles(
        context: Context,
        targetTypes: Set<NoteDocumentType>
    ): List<DiscoveredExternalDocument> {
        val results = mutableListOf<DiscoveredExternalDocument>()
        val extensions = extensionsForTypes(targetTypes)
        if (extensions.isEmpty()) return results

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

        val likeClauses = extensions.joinToString(" OR ") {
            "LOWER(${MediaStore.Files.FileColumns.DISPLAY_NAME}) LIKE ?"
        }
        val selection = "${MediaStore.Files.FileColumns.SIZE} > 0 AND ($likeClauses)"
        val selectionArgs = extensions.map { "%$it" }.toTypedArray()
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
                    val matchedType = matchExtensionToType(displayName, targetTypes) ?: continue
                    val modSec = if (dateIdx != -1) cursor.getLong(dateIdx) else 0L
                    val contentUri = ContentUris.withAppendedId(collectionUri, id)
                    val title = displayName.substringBeforeLast(".").ifBlank { displayName }
                    results.add(
                        DiscoveredExternalDocument(
                            uri = contentUri.toString(),
                            title = title,
                            docType = matchedType,
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
        targetTypes: Set<NoteDocumentType>
    ): List<DiscoveredExternalDocument> {
        val results = mutableListOf<DiscoveredExternalDocument>()
        try {
            val treeUri = Uri.parse(treeUriStr)
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            scanSafFolderRecursive(
                context = context,
                treeUri = treeUri,
                parentDocId = rootDocId,
                targetTypes = targetTypes,
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
        targetTypes: Set<NoteDocumentType>,
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
                            scanSafFolderRecursive(context, treeUri, docId, targetTypes, depth + 1, out)
                        }
                    } else {
                        val matchedType = matchExtensionToType(name, targetTypes)
                        if (matchedType != null) {
                            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                            val title = name.substringBeforeLast(".").ifBlank { name }
                            out.add(
                                DiscoveredExternalDocument(
                                    uri = docUri.toString(),
                                    title = title,
                                    docType = matchedType,
                                    modifiedAt = mod,
                                    sourceLabel = "Pasta vinculada",
                                    isInVault = false
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Throwable) {
        }
    }
}
