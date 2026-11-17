package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.example.data.model.Note
import com.example.data.model.NoteDocumentType
import java.io.File
import java.io.InputStream
import java.net.URLDecoder

object ExternalDocumentUriHelper {

    val DOCUMENT_PICKER_MIME_TYPES = arrayOf(
        "application/pdf",
        "application/x-pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    )

    val PDF_PICKER_MIME_TYPES = arrayOf(
        "application/pdf",
        "application/x-pdf",
        "application/octet-stream"
    )

    val DOCX_PICKER_MIME_TYPES = arrayOf(
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-word.document.macroEnabled.12",
        "application/octet-stream"
    )

    val MARKDOWN_IMPORT_MIME_TYPES = arrayOf(
        "text/markdown",
        "text/plain",
        "*/*"
    )

    val OTHER_IMPORT_MIME_TYPES = arrayOf(
        "text/plain",
        "text/html",
        "text/csv",
        "application/json",
        "application/xml",
        "text/xml",
        "*/*"
    )

    const val OTHER_SUPPORTED_EXTENSIONS_LABEL = ".txt · .html · .csv · .json · .tex · .xml · .citradoc"

    val ALL_IMPORT_AND_LINK_MIME_TYPES = arrayOf(
        "application/pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "text/markdown",
        "text/plain",
        "text/html",
        "application/json",
        "*/*"
    )

    fun takePersistableReadPermission(context: Context, uri: Uri) {
        try {
            if (uri.scheme == "content") {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        } catch (_: SecurityException) {
            // Transient URI providers may decline persistable permission; stream still works for the session
        } catch (_: Exception) {
        }
    }

    fun releasePersistableReadPermissionIfUnused(
        context: Context,
        uriString: String?,
        remainingNotes: List<Note>
    ) {
        if (uriString.isNullOrBlank() || !uriString.startsWith("content://")) return
        val stillReferenced = remainingNotes.any { !it.isDeleted && it.externalUri == uriString }
        if (stillReferenced) return
        try {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(uriString),
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
        }
    }

    fun resolveDisplayName(context: Context, uri: Uri): String? {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1 && cursor.moveToFirst()) {
                        val name = cursor.getString(index)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            } catch (_: Exception) {
            }
        }
        val rawLast = uri.lastPathSegment ?: uri.path?.substringAfterLast('/')
        return rawLast?.substringAfterLast('/')?.substringAfterLast(':')?.takeIf { it.isNotBlank() }
    }

    fun detectExternalDocumentTypeFromMeta(
        displayName: String?,
        mimeType: String?
    ): NoteDocumentType? {
        val name = (displayName ?: "").lowercase().trim()
        val mime = (mimeType ?: "").lowercase().trim()
        return when {
            mime == "application/pdf" ||
                mime == "application/x-pdf" ||
                name.endsWith(".pdf") -> NoteDocumentType.PDF
            mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
                mime == "application/vnd.ms-word.document.macroenabled.12" ||
                name.endsWith(".docx") -> NoteDocumentType.DOCX
            else -> null
        }
    }

    /**
     * Sniffs binary header / ZIP structure so image-heavy PDFs and .docx files whose MIME type
     * is reported as application/octet-stream or without extension are still reliably recognized.
     */
    fun detectExternalDocumentTypeFromBytes(bytes: ByteArray): NoteDocumentType? {
        if (bytes.size < 4) return null
        val headerLimit = minOf(bytes.size, 1024)
        val asciiHeader = String(bytes, 0, headerLimit, Charsets.ISO_8859_1)
        if (asciiHeader.contains("%PDF-")) {
            return NoteDocumentType.PDF
        }
        if (bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
            return try {
                java.util.zip.ZipInputStream(bytes.inputStream()).use { zis ->
                    var entry = zis.nextEntry
                    var foundDocx = false
                    while (entry != null) {
                        val entryName = entry.name.replace('\\', '/')
                        if (entryName.equals("word/document.xml", ignoreCase = true)) {
                            foundDocx = true
                            break
                        }
                        entry = zis.nextEntry
                    }
                    if (foundDocx) NoteDocumentType.DOCX else null
                }
            } catch (_: Exception) {
                null
            }
        }
        return null
    }

    fun detectExternalDocumentType(
        context: Context,
        uri: Uri,
        displayName: String? = null,
        mimeTypeHint: String? = null
    ): NoteDocumentType? {
        val resolvedName = displayName ?: resolveDisplayName(context, uri)
        val mime = try {
            context.contentResolver.getType(uri)?.takeIf { it.isNotBlank() } ?: mimeTypeHint
        } catch (_: Exception) {
            mimeTypeHint
        }
        detectExternalDocumentTypeFromMeta(resolvedName, mime)?.let { return it }

        // Magic-byte fallback when MIME is generic (e.g. application/octet-stream) or filename lacks extension
        return try {
            openInputStream(context, uri.toString())?.use { input ->
                val head = ByteArray(1024)
                val readCount = input.read(head)
                if (readCount >= 4) {
                    val actualHead = if (readCount == head.size) head else head.copyOf(readCount)
                    val asciiHead = String(actualHead, Charsets.ISO_8859_1)
                    when {
                        asciiHead.contains("%PDF-") -> NoteDocumentType.PDF
                        actualHead[0] == 0x50.toByte() && actualHead[1] == 0x4B.toByte() -> {
                            openInputStream(context, uri.toString())?.use { fullStream ->
                                java.util.zip.ZipInputStream(fullStream).use { zis ->
                                    var entry = zis.nextEntry
                                    while (entry != null) {
                                        if (entry.name.replace('\\', '/').equals("word/document.xml", ignoreCase = true)) {
                                            return NoteDocumentType.DOCX
                                        }
                                        entry = zis.nextEntry
                                    }
                                }
                            }
                            null
                        }
                        else -> null
                    }
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun openInputStream(context: Context, pathOrUri: String?): InputStream? {
        if (pathOrUri.isNullOrBlank()) return null
        val trimmed = pathOrUri.trim()
        return try {
            when {
                trimmed.startsWith("content://") -> {
                    context.contentResolver.openInputStream(Uri.parse(trimmed))
                }
                trimmed.startsWith("file://") -> {
                    val path = Uri.parse(trimmed).path ?: trimmed.removePrefix("file://")
                    val f = File(path)
                    if (f.exists() && f.canRead()) f.inputStream() else null
                }
                trimmed.startsWith("/") -> {
                    val f = File(trimmed)
                    if (f.exists() && f.canRead()) f.inputStream() else null
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun openParcelFileDescriptor(context: Context, pathOrUri: String?): ParcelFileDescriptor? {
        if (pathOrUri.isNullOrBlank()) return null
        val trimmed = pathOrUri.trim()
        return try {
            when {
                trimmed.startsWith("content://") -> {
                    context.contentResolver.openFileDescriptor(Uri.parse(trimmed), "r")
                }
                trimmed.startsWith("file://") -> {
                    val path = Uri.parse(trimmed).path ?: trimmed.removePrefix("file://")
                    val f = File(path)
                    if (f.exists() && f.canRead()) {
                        ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
                    } else null
                }
                trimmed.startsWith("/") -> {
                    val f = File(trimmed)
                    if (f.exists() && f.canRead()) {
                        ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
                    } else null
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun isAccessible(context: Context, pathOrUri: String?): Boolean {
        if (pathOrUri.isNullOrBlank()) return false
        return try {
            openParcelFileDescriptor(context, pathOrUri)?.use { pfd ->
                pfd.statSize != 0L
            } ?: openInputStream(context, pathOrUri)?.use { true } ?: false
        } catch (_: Exception) {
            false
        }
    }

    fun formatReadablePath(pathOrUri: String?): String {
        if (pathOrUri.isNullOrBlank()) return "Caminho desconhecido"
        val raw = pathOrUri.trim()
        return try {
            if (raw.startsWith("content://")) {
                val uri = Uri.parse(raw)
                val decoded = URLDecoder.decode(uri.lastPathSegment ?: uri.toString(), "UTF-8")
                when {
                    decoded.contains("primary:") -> decoded.substringAfter("primary:")
                    decoded.contains(':') -> decoded.substringAfter(':')
                    else -> decoded
                }.ifBlank { raw }
            } else {
                raw.removePrefix("file://")
            }
        } catch (_: Exception) {
            raw
        }
    }
}
