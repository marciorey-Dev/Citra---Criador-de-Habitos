package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.example.data.model.Note
import com.example.data.model.NoteDocumentType
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.URLDecoder

data class RenameExternalDocumentResult(
    val success: Boolean,
    val newUri: String? = null,
    val sanitizedTitle: String,
    val fileRenamedOnStorage: Boolean = false
)

object ExternalDocumentUriHelper {

    val DOCUMENT_PICKER_MIME_TYPES = arrayOf(
        "application/pdf",
        "application/x-pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/epub+zip",
        "application/x-mobipocket-ebook",
        "application/vnd.amazon.ebook",
        "application/vnd.comicbook+zip",
        "application/x-cbz",
        "application/x-fictionbook+xml"
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

    val EBOOK_PICKER_MIME_TYPES = arrayOf(
        "application/epub+zip",
        "application/x-mobipocket-ebook",
        "application/vnd.amazon.ebook",
        "application/vnd.comicbook+zip",
        "application/x-cbz",
        "application/x-fictionbook+xml",
        "application/zip",
        "application/octet-stream",
        "*/*"
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
        "application/epub+zip",
        "application/x-mobipocket-ebook",
        "application/vnd.comicbook+zip",
        "application/x-fictionbook+xml",
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
        try {
            if (uri.scheme == "content") {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
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
                mime == "application/vnd.ms-word.document.macroEnabled.12" ||
                name.endsWith(".docx") -> NoteDocumentType.DOCX
            mime == "application/epub+zip" ||
                name.endsWith(".epub") -> NoteDocumentType.EPUB
            mime == "application/x-mobipocket-ebook" ||
                mime == "application/vnd.amazon.ebook" ||
                name.endsWith(".mobi") ||
                name.endsWith(".azw") ||
                name.endsWith(".azw3") -> NoteDocumentType.MOBI
            mime == "application/vnd.comicbook+zip" ||
                mime == "application/x-cbz" ||
                name.endsWith(".cbz") -> NoteDocumentType.CBZ
            mime == "application/x-fictionbook+xml" ||
                name.endsWith(".fb2") ||
                name.endsWith(".fb2.zip") -> NoteDocumentType.FB2
            else -> null
        }
    }

    /**
     * Sniffs binary header / ZIP structure so image-heavy PDFs, .docx, .epub, .mobi, .cbz, and .fb2
     * files whose MIME type is reported as application/octet-stream or without extension are recognized.
     */
    fun detectExternalDocumentTypeFromBytes(bytes: ByteArray): NoteDocumentType? {
        if (bytes.size < 4) return null
        val headerLimit = minOf(bytes.size, 2048)
        val asciiHeader = String(bytes, 0, headerLimit, Charsets.ISO_8859_1)
        if (asciiHeader.contains("%PDF-")) {
            return NoteDocumentType.PDF
        }
        if (bytes.size >= 68) {
            val palmId = String(bytes, 60, 8, Charsets.ISO_8859_1)
            if (palmId == "BOOKMOBI" || palmId == "TEXtREAd") {
                return NoteDocumentType.MOBI
            }
        }
        if (asciiHeader.contains("<FictionBook", ignoreCase = true)) {
            return NoteDocumentType.FB2
        }
        if (bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
            return try {
                java.util.zip.ZipInputStream(bytes.inputStream()).use { zis ->
                    var entry = zis.nextEntry
                    var hasImages = false
                    var hasDocx = false
                    var hasEpub = false
                    while (entry != null) {
                        val entryName = entry.name.replace('\\', '/')
                        val lower = entryName.lowercase()
                        when {
                            lower == "word/document.xml" -> {
                                hasDocx = true
                                break
                            }
                            lower == "mimetype" || lower == "meta-inf/container.xml" || lower.endsWith(".opf") -> {
                                hasEpub = true
                            }
                            lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                                lower.endsWith(".png") || lower.endsWith(".webp") -> {
                                hasImages = true
                            }
                        }
                        entry = zis.nextEntry
                    }
                    when {
                        hasDocx -> NoteDocumentType.DOCX
                        hasEpub -> NoteDocumentType.EPUB
                        hasImages -> NoteDocumentType.CBZ
                        else -> null
                    }
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
                val head = ByteArray(2048)
                val readCount = input.read(head)
                if (readCount >= 4) {
                    val actualHead = if (readCount == head.size) head else head.copyOf(readCount)
                    val asciiHead = String(actualHead, Charsets.ISO_8859_1)
                    when {
                        asciiHead.contains("%PDF-") -> NoteDocumentType.PDF
                        actualHead.size >= 68 && String(actualHead, 60, 8, Charsets.ISO_8859_1).let {
                            it == "BOOKMOBI" || it == "TEXtREAd"
                        } -> NoteDocumentType.MOBI
                        asciiHead.contains("<FictionBook", ignoreCase = true) -> NoteDocumentType.FB2
                        actualHead[0] == 0x50.toByte() && actualHead[1] == 0x4B.toByte() -> {
                            openInputStream(context, uri.toString())?.use { fullStream ->
                                java.util.zip.ZipInputStream(fullStream).use { zis ->
                                    var entry = zis.nextEntry
                                    var hasEpub = false
                                    var hasImages = false
                                    while (entry != null) {
                                        val lower = entry.name.replace('\\', '/').lowercase()
                                        when {
                                            lower == "word/document.xml" -> return NoteDocumentType.DOCX
                                            lower == "mimetype" || lower == "meta-inf/container.xml" || lower.endsWith(".opf") -> hasEpub = true
                                            lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp") -> hasImages = true
                                        }
                                        entry = zis.nextEntry
                                    }
                                    when {
                                        hasEpub -> NoteDocumentType.EPUB
                                        hasImages -> NoteDocumentType.CBZ
                                        else -> null
                                    }
                                }
                            }
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

    fun openOutputStream(context: Context, pathOrUri: String?): OutputStream? {
        if (pathOrUri.isNullOrBlank()) return null
        val trimmed = pathOrUri.trim()
        return try {
            when {
                trimmed.startsWith("content://") -> {
                    val uri = Uri.parse(trimmed)
                    try {
                        context.contentResolver.openOutputStream(uri, "wt")
                    } catch (_: Exception) {
                        context.contentResolver.openOutputStream(uri, "w")
                    }
                }
                trimmed.startsWith("file://") -> {
                    val path = Uri.parse(trimmed).path ?: trimmed.removePrefix("file://")
                    val f = File(path)
                    if (f.exists() && f.canWrite()) FileOutputStream(f, false) else null
                }
                trimmed.startsWith("/") -> {
                    val f = File(trimmed)
                    if (f.exists() && f.canWrite()) FileOutputStream(f, false) else null
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun writeBytesToExternalDocument(context: Context, pathOrUri: String, bytes: ByteArray): Boolean {
        return try {
            val out = openOutputStream(context, pathOrUri) ?: return false
            out.use { stream ->
                stream.write(bytes)
                stream.flush()
            }
            true
        } catch (_: Exception) {
            false
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

    fun getImportedDocsFolder(context: Context): File {
        return File(context.filesDir, "citra_imported_docs").apply {
            if (!exists()) mkdirs()
        }
    }

    fun isInternalImportedFile(context: Context, pathOrUri: String?): Boolean {
        if (pathOrUri.isNullOrBlank()) return false
        val trimmed = pathOrUri.trim()
        val folderPath = getImportedDocsFolder(context).absolutePath
        return trimmed.startsWith(folderPath)
    }

    fun computeOriginTag(sourcePathOrUri: String): String {
        return try {
            val md = java.security.MessageDigest.getInstance("SHA-1")
            val digest = md.digest(sourcePathOrUri.trim().toByteArray(Charsets.UTF_8))
            digest.take(4).joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            (sourcePathOrUri.hashCode() and 0x7FFFFFFF).toString(16)
        }
    }

    /**
     * Copies an external document into app-private persistent storage (`citra_imported_docs`)
     * so imported documents never lose their link when temporary OS grants expire or external files move.
     * Also keeps the file directly writable for inline DOCX editing.
     */
    fun copyDocumentToInternalStorage(
        context: Context,
        sourcePathOrUri: String?,
        title: String,
        docType: NoteDocumentType
    ): String? {
        if (sourcePathOrUri.isNullOrBlank()) return null
        val trimmed = sourcePathOrUri.trim()
        val folder = getImportedDocsFolder(context)
        if (isInternalImportedFile(context, trimmed)) {
            val existing = File(trimmed)
            if (existing.exists() && existing.canRead() && existing.length() > 0L) {
                return existing.absolutePath
            }
        }

        val ext = docType.fileExtension
        val cleanBase = title.trim()
            .replace(Regex("\\.(pdf|docx|epub|mobi|azw3?|cbz|fb2)$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[/\\\\:*?\"<>|\\x00-\\x1F]"), "_")
            .trim()
            .take(48)
            .ifBlank { "Documento" }
        val tag = computeOriginTag(trimmed)
        val destFile = File(folder, "${cleanBase}_$tag.$ext")
        if (destFile.exists() && destFile.canRead() && destFile.length() > 0L) {
            return destFile.absolutePath
        }

        val tempFile = File(folder, ".tmp_${System.nanoTime()}_$tag.$ext")
        return try {
            val copied = openInputStream(context, trimmed)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
                true
            } ?: false

            if (!copied || !tempFile.exists() || tempFile.length() == 0L) {
                tempFile.delete()
                null
            } else {
                if (destFile.exists()) destFile.delete()
                if (tempFile.renameTo(destFile)) {
                    destFile.absolutePath
                } else {
                    tempFile.copyTo(destFile, overwrite = true)
                    tempFile.delete()
                    destFile.absolutePath
                }
            }
        } catch (_: Exception) {
            try { tempFile.delete() } catch (_: Exception) {}
            null
        }
    }

    fun deleteInternalImportedDocumentIfUnused(
        context: Context,
        pathOrUri: String?,
        remainingNotes: List<Note>
    ) {
        if (!isInternalImportedFile(context, pathOrUri)) return
        val trimmed = pathOrUri!!.trim()
        val stillReferenced = remainingNotes.any { !it.isDeleted && it.externalUri == trimmed }
        if (stillReferenced) return
        try {
            File(trimmed).delete()
        } catch (_: Exception) {
        }
    }

    fun deleteAllInternalImportedDocuments(context: Context) {
        try {
            getImportedDocsFolder(context).listFiles()?.forEach { it.delete() }
        } catch (_: Exception) {
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

    fun renameExternalDocumentFile(
        context: Context,
        pathOrUri: String?,
        newTitle: String,
        docType: NoteDocumentType
    ): RenameExternalDocumentResult {
        val ext = docType.fileExtension
        val cleanBase = newTitle.trim()
            .replace(Regex("\\.(pdf|docx|epub|mobi|azw3?|cbz|fb2)$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[/\\\\:*?\"<>|\\x00-\\x1F]"), "_")
            .trim()
            .ifBlank { "Documento" }
        val targetFilename = "$cleanBase.$ext"

        if (pathOrUri.isNullOrBlank()) {
            return RenameExternalDocumentResult(success = true, newUri = null, sanitizedTitle = cleanBase, fileRenamedOnStorage = false)
        }

        val trimmed = pathOrUri.trim()
        try {
            when {
                trimmed.startsWith("content://") -> {
                    val uri = Uri.parse(trimmed)
                    if (DocumentsContract.isDocumentUri(context, uri)) {
                        try {
                            val renamedUri = DocumentsContract.renameDocument(context.contentResolver, uri, targetFilename)
                            if (renamedUri != null) {
                                takePersistableReadPermission(context, renamedUri)
                                return RenameExternalDocumentResult(
                                    success = true,
                                    newUri = renamedUri.toString(),
                                    sanitizedTitle = cleanBase,
                                    fileRenamedOnStorage = true
                                )
                            }
                        } catch (_: Exception) {
                            // Some SAF providers (Downloads/third-party) deny renameDocument
                        }
                    }
                }
                trimmed.startsWith("file://") || trimmed.startsWith("/") -> {
                    val path = if (trimmed.startsWith("file://")) Uri.parse(trimmed).path ?: trimmed.removePrefix("file://") else trimmed
                    val srcFile = File(path)
                    if (srcFile.exists() && srcFile.parentFile != null) {
                        val destFile = File(srcFile.parentFile, targetFilename)
                        if (srcFile.absolutePath == destFile.absolutePath) {
                            return RenameExternalDocumentResult(success = true, newUri = trimmed, sanitizedTitle = cleanBase, fileRenamedOnStorage = true)
                        }
                        if (!destFile.exists() && srcFile.renameTo(destFile)) {
                            val nextPath = if (trimmed.startsWith("file://")) Uri.fromFile(destFile).toString() else destFile.absolutePath
                            return RenameExternalDocumentResult(success = true, newUri = nextPath, sanitizedTitle = cleanBase, fileRenamedOnStorage = true)
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }

        return RenameExternalDocumentResult(
            success = true,
            newUri = pathOrUri,
            sanitizedTitle = cleanBase,
            fileRenamedOnStorage = false
        )
    }

    fun shareExternalDocument(
        context: Context,
        title: String,
        pathOrUri: String?,
        docType: NoteDocumentType
    ): Boolean {
        if (pathOrUri.isNullOrBlank()) return false
        val ext = docType.fileExtension
        val mime = docType.mimeType
        val safeTitle = title.trim()
            .replace(Regex("\\.(pdf|docx|epub|mobi|azw3?|cbz|fb2)$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[/\\\\:*?\"<>|\\x00-\\x1F]"), "_")
            .trim()
            .ifBlank { "Documento" }

        return try {
            val sharedDir = File(context.cacheDir, "shared_docs").apply { if (!exists()) mkdirs() }
            val tempFile = File(sharedDir, "$safeTitle.$ext")
            val copied = openInputStream(context, pathOrUri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
                true
            } ?: false
            if (!copied || !tempFile.exists() || tempFile.length() == 0L) return false

            val shareUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, shareUri)
                putExtra(Intent.EXTRA_SUBJECT, safeTitle)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(sendIntent, "Enviar para...").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(chooser)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun printExternalDocument(
        context: Context,
        title: String,
        pathOrUri: String?,
        docType: NoteDocumentType,
        docxParsed: DocxParsedDocument? = null,
        ebookParsed: EbookDocument? = null
    ): Boolean {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return false
        val jobName = title.ifBlank { "Documento Citra" }

        if (docType.isEbookOrComic) {
            val doc = ebookParsed ?: run {
                val stream = openInputStream(context, pathOrUri) ?: return false
                stream.use { EbookComicEngine.buildParsedFromStream(it, docType, jobName) }
            }
            val adapter = object : PrintDocumentAdapter() {
                private var currentAttributes: PrintAttributes? = null

                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    newAttributes: PrintAttributes,
                    cancellationSignal: CancellationSignal?,
                    callback: LayoutResultCallback,
                    extras: Bundle?
                ) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback.onLayoutCancelled()
                        return
                    }
                    currentAttributes = newAttributes
                    val info = PrintDocumentInfo.Builder("$jobName.pdf")
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(doc.pages.size.coerceAtLeast(1))
                        .build()
                    callback.onLayoutFinished(info, true)
                }

                override fun onWrite(
                    pages: Array<out PageRange>,
                    destination: ParcelFileDescriptor,
                    cancellationSignal: CancellationSignal?,
                    callback: WriteResultCallback
                ) {
                    val attrs = currentAttributes ?: PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                        .build()
                    val pdfDoc = PrintedPdfDocument(context, attrs)
                    try {
                        val pageW = pdfDoc.pageContentRect.width().takeIf { it > 0 } ?: 595
                        val pageH = pdfDoc.pageContentRect.height().takeIf { it > 0 } ?: 842
                        doc.pages.indices.forEach { idx ->
                            if (cancellationSignal?.isCanceled == true) {
                                pdfDoc.close()
                                callback.onWriteCancelled()
                                return
                            }
                            val page = pdfDoc.startPage(idx)
                            val bmp = EbookComicEngine.renderPageBitmap(doc, idx, 900)
                            if (bmp != null) {
                                val srcRect = android.graphics.Rect(0, 0, bmp.width, bmp.height)
                                val scale = minOf(pageW.toFloat() / bmp.width, pageH.toFloat() / bmp.height)
                                val drawW = bmp.width * scale
                                val drawH = bmp.height * scale
                                val left = (pageW - drawW) / 2f
                                val top = (pageH - drawH) / 2f
                                val dstRect = android.graphics.RectF(left, top, left + drawW, top + drawH)
                                page.canvas.drawBitmap(bmp, srcRect, dstRect, null)
                                bmp.recycle()
                            }
                            pdfDoc.finishPage(page)
                        }
                        FileOutputStream(destination.fileDescriptor).use { out ->
                            pdfDoc.writeTo(out)
                        }
                        callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        callback.onWriteFailed(e.message ?: "Erro ao imprimir livro.")
                    } finally {
                        pdfDoc.close()
                    }
                }
            }
            printManager.print(jobName, adapter, null)
            return true
        }

        if (docType == NoteDocumentType.PDF) {
            if (pathOrUri.isNullOrBlank()) return false
            val adapter = object : PrintDocumentAdapter() {
                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    newAttributes: PrintAttributes,
                    cancellationSignal: CancellationSignal?,
                    callback: LayoutResultCallback,
                    extras: Bundle?
                ) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback.onLayoutCancelled()
                        return
                    }
                    val info = PrintDocumentInfo.Builder("$jobName.pdf")
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .build()
                    callback.onLayoutFinished(info, true)
                }

                override fun onWrite(
                    pages: Array<out PageRange>,
                    destination: ParcelFileDescriptor,
                    cancellationSignal: CancellationSignal?,
                    callback: WriteResultCallback
                ) {
                    try {
                        openInputStream(context, pathOrUri)?.use { input ->
                            FileOutputStream(destination.fileDescriptor).use { out ->
                                val buf = ByteArray(16 * 1024)
                                while (true) {
                                    if (cancellationSignal?.isCanceled == true) {
                                        callback.onWriteCancelled()
                                        return
                                    }
                                    val read = input.read(buf)
                                    if (read <= 0) break
                                    out.write(buf, 0, read)
                                }
                                out.flush()
                            }
                        } ?: run {
                            callback.onWriteFailed("Não foi possível abrir o PDF para impressão.")
                            return
                        }
                        callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        callback.onWriteFailed(e.message ?: "Erro ao imprimir PDF.")
                    }
                }
            }
            printManager.print(jobName, adapter, null)
            return true
        } else {
            val doc = docxParsed ?: run {
                val stream = openInputStream(context, pathOrUri) ?: return false
                stream.use { DocxDocumentEngine.parseDocx(it) }
            }
            val adapter = object : PrintDocumentAdapter() {
                private var currentAttributes: PrintAttributes? = null

                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    newAttributes: PrintAttributes,
                    cancellationSignal: CancellationSignal?,
                    callback: LayoutResultCallback,
                    extras: Bundle?
                ) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback.onLayoutCancelled()
                        return
                    }
                    currentAttributes = newAttributes
                    val info = PrintDocumentInfo.Builder("$jobName.pdf")
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .build()
                    callback.onLayoutFinished(info, true)
                }

                override fun onWrite(
                    pages: Array<out PageRange>,
                    destination: ParcelFileDescriptor,
                    cancellationSignal: CancellationSignal?,
                    callback: WriteResultCallback
                ) {
                    val attrs = currentAttributes ?: PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                        .build()
                    val pdfDoc = PrintedPdfDocument(context, attrs)
                    try {
                        val pageW = pdfDoc.pageContentRect.width().takeIf { it > 0 } ?: 595
                        val pageH = pdfDoc.pageContentRect.height().takeIf { it > 0 } ?: 842
                        val margin = 48f
                        val usableW = (pageW - margin * 2).toInt().coerceAtLeast(200)

                        var pageIndex = 0
                        var page = pdfDoc.startPage(pageIndex)
                        var canvas = page.canvas
                        var y = margin

                        fun ensureSpace(needed: Float) {
                            if (y + needed > pageH - margin) {
                                pdfDoc.finishPage(page)
                                pageIndex++
                                page = pdfDoc.startPage(pageIndex)
                                canvas = page.canvas
                                y = margin
                            }
                        }

                        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = AndroidColor.parseColor("#0F172A")
                            textSize = 18f
                            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        }
                        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = AndroidColor.parseColor("#1E293B")
                            textSize = 11.5f
                        }

                        for (block in doc.blocks) {
                            if (cancellationSignal?.isCanceled == true) {
                                pdfDoc.close()
                                callback.onWriteCancelled()
                                return
                            }
                            when (block) {
                                is DocxBlock.PageBreak -> {
                                    pdfDoc.finishPage(page)
                                    pageIndex++
                                    page = pdfDoc.startPage(pageIndex)
                                    canvas = page.canvas
                                    y = margin
                                }
                                is DocxBlock.Divider -> {
                                    ensureSpace(16f)
                                    val linePaint = Paint().apply {
                                        color = AndroidColor.parseColor("#CBD5E1")
                                        strokeWidth = 1f
                                    }
                                    canvas.drawLine(margin, y + 6f, pageW - margin, y + 6f, linePaint)
                                    y += 14f
                                }
                                else -> {
                                    val (text, paint, indentPx) = when (block) {
                                        is DocxBlock.Heading -> Triple(
                                            block.runs.joinToString("") { it.text },
                                            TextPaint(titlePaint).apply {
                                                textSize = when (block.level) {
                                                    1 -> 18f
                                                    2 -> 15f
                                                    3 -> 13.5f
                                                    else -> 12f
                                                }
                                            },
                                            0
                                        )
                                        is DocxBlock.ListItem -> {
                                            val prefix = if (block.ordered) "${block.indexNumber}. " else "• "
                                            Triple(prefix + block.runs.joinToString("") { it.text }, bodyPaint, 18 + block.indentLevel * 12)
                                        }
                                        is DocxBlock.ChecklistItem -> {
                                            val prefix = if (block.checked) "☑ " else "☐ "
                                            Triple(prefix + block.runs.joinToString("") { it.text }, bodyPaint, 18)
                                        }
                                        is DocxBlock.Quote -> Triple(block.runs.joinToString("") { it.text }, bodyPaint, 16)
                                        is DocxBlock.Callout -> Triple("${block.type.uppercase()}: ${block.runs.joinToString("") { it.text }}", bodyPaint, 12)
                                        is DocxBlock.Code -> Triple(block.code, bodyPaint, 8)
                                        is DocxBlock.Math -> Triple(block.formula, bodyPaint, 0)
                                        is DocxBlock.Paragraph -> Triple(block.runs.joinToString("") { it.text }, bodyPaint, 0)
                                        is DocxBlock.Table -> Triple(
                                            (listOf(block.headers) + block.rows).joinToString("\n") { row ->
                                                row.joinToString(" | ") { cell -> cell.joinToString("") { it.text } }
                                            },
                                            bodyPaint,
                                            0
                                        )
                                        is DocxBlock.Image -> Triple("[Imagem: ${block.alt}]", bodyPaint, 0)
                                        DocxBlock.Divider, DocxBlock.PageBreak -> Triple("", bodyPaint, 0)
                                    }
                                    if (text.isBlank()) {
                                        y += 12f
                                    } else {
                                        @Suppress("DEPRECATION")
                                        val layout = StaticLayout.Builder
                                            .obtain(text, 0, text.length, paint, (usableW - indentPx).coerceAtLeast(100))
                                            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                                            .build()
                                        ensureSpace(layout.height + 6f)
                                        canvas.save()
                                        canvas.translate(margin + indentPx, y)
                                        layout.draw(canvas)
                                        canvas.restore()
                                        y += layout.height + 8f
                                    }
                                }
                            }
                        }

                        pdfDoc.finishPage(page)
                        FileOutputStream(destination.fileDescriptor).use { out ->
                            pdfDoc.writeTo(out)
                        }
                        callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        callback.onWriteFailed(e.message ?: "Erro ao imprimir documento.")
                    } finally {
                        pdfDoc.close()
                    }
                }
            }
            printManager.print(jobName, adapter, null)
            return true
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
