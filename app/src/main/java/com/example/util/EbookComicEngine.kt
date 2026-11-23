package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Base64
import com.example.data.model.NoteDocumentType
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.util.zip.ZipInputStream

data class EbookParagraph(
    val text: String,
    val isHeading: Boolean = false,
    val headingLevel: Int = 0,
    val isItalic: Boolean = false,
    val isBold: Boolean = false
)

sealed interface EbookPageContent {
    data class ImagePage(val bytes: ByteArray, val label: String) : EbookPageContent
    data class TextPage(
        val paragraphs: List<EbookParagraph>,
        val isCoverHeader: Boolean = false
    ) : EbookPageContent
}

data class EbookDocument(
    val title: String,
    val author: String,
    val docType: NoteDocumentType,
    val coverBytes: ByteArray? = null,
    val pages: List<EbookPageContent>
)

/**
 * Pure Kotlin reader engine for EPUB, MOBI/AZW, CBZ, and FB2 formats.
 * Paginates books into A4-like white sheets (and preserves image pages for covers/comics)
 * so all reading formats behave consistently with the Citra PDF reader.
 */
object EbookComicEngine {

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")

    fun buildParsedFromStream(
        input: InputStream,
        docType: NoteDocumentType,
        fallbackTitle: String = "Livro"
    ): EbookDocument {
        val bytes = input.readBytes()
        return buildParsedFromBytes(bytes, docType, fallbackTitle)
    }

    fun buildParsedFromBytes(
        bytes: ByteArray,
        docType: NoteDocumentType,
        fallbackTitle: String = "Livro"
    ): EbookDocument {
        return when (docType) {
            NoteDocumentType.CBZ -> parseCbz(bytes, fallbackTitle)
            NoteDocumentType.EPUB -> parseEpub(bytes, fallbackTitle)
            NoteDocumentType.MOBI -> parseMobi(bytes, fallbackTitle)
            NoteDocumentType.FB2 -> parseFb2(bytes, fallbackTitle)
            else -> EbookDocument(
                title = fallbackTitle,
                author = "",
                docType = docType,
                pages = listOf(
                    EbookPageContent.TextPage(
                        listOf(EbookParagraph("Formato não suportado para leitura paginada."))
                    )
                )
            )
        }
    }

    // =========================================================================
    // CBZ (Comic Book ZIP)
    // =========================================================================
    fun parseCbz(bytes: ByteArray, fallbackTitle: String = "Quadrinho"): EbookDocument {
        val entries = mutableListOf<Pair<String, ByteArray>>()
        try {
            ZipInputStream(bytes.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name.replace('\\', '/')
                    val base = name.substringAfterLast('/')
                    val ext = base.substringAfterLast('.', "").lowercase()
                    if (!entry.isDirectory &&
                        !name.contains("__MACOSX") &&
                        !base.startsWith(".") &&
                        ext in IMAGE_EXTENSIONS
                    ) {
                        val content = readBoundedBytes(zis, 24 * 1024 * 1024)
                        if (content.isNotEmpty()) {
                            entries.add(name to content)
                        }
                    }
                    entry = zis.nextEntry
                }
            }
        } catch (_: Exception) {
        }

        val sorted = entries.sortedWith { a, b -> naturalCompare(a.first, b.first) }
        val pages = sorted.mapIndexed { idx, (name, data) ->
            EbookPageContent.ImagePage(
                bytes = data,
                label = name.substringAfterLast('/').ifBlank { "Página ${idx + 1}" }
            )
        }
        val finalPages = pages.ifEmpty {
            listOf(
                EbookPageContent.TextPage(
                    listOf(EbookParagraph("Nenhuma página de imagem encontrada neste arquivo .cbz."))
                )
            )
        }
        return EbookDocument(
            title = fallbackTitle,
            author = "",
            docType = NoteDocumentType.CBZ,
            coverBytes = sorted.firstOrNull()?.second,
            pages = finalPages
        )
    }

    // =========================================================================
    // EPUB (ZIP + container.xml + OPF + XHTML chapters)
    // =========================================================================
    fun parseEpub(bytes: ByteArray, fallbackTitle: String = "EPUB"): EbookDocument {
        val zipMap = linkedMapOf<String, ByteArray>()
        try {
            ZipInputStream(bytes.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val normName = entry.name.replace('\\', '/').trimStart('/')
                        val content = readBoundedBytes(zis, 16 * 1024 * 1024)
                        zipMap[normName] = content
                    }
                    entry = zis.nextEntry
                }
            }
        } catch (_: Exception) {
        }

        // 1. Locate OPF path from META-INF/container.xml
        val containerBytes = findZipEntryCaseInsensitive(zipMap, "META-INF/container.xml")
        var opfPath = containerBytes?.let { parseContainerOpfPath(it) }
        if (opfPath.isNullOrBlank()) {
            opfPath = zipMap.keys.firstOrNull { it.endsWith(".opf", ignoreCase = true) }
        }

        var bookTitle = fallbackTitle
        var bookAuthor = ""
        val spineFiles = mutableListOf<String>()
        var coverBytes: ByteArray? = null

        if (!opfPath.isNullOrBlank()) {
            val opfBytes = findZipEntryCaseInsensitive(zipMap, opfPath)
            val opfDir = opfPath.substringBeforeLast('/', "")
            if (opfBytes != null) {
                val opf = parseOpf(opfBytes)
                if (!opf.title.isNullOrBlank()) bookTitle = opf.title
                if (!opf.author.isNullOrBlank()) bookAuthor = opf.author

                // Resolve cover image
                val coverHref = opf.coverImageHref
                if (!coverHref.isNullOrBlank()) {
                    val resolvedCover = resolveRelativePath(opfDir, coverHref)
                    coverBytes = findZipEntryCaseInsensitive(zipMap, resolvedCover)
                }

                // Resolve spine HTML files
                opf.spineIdRefs.forEach { idRef ->
                    val href = opf.manifestById[idRef]?.href
                    if (!href.isNullOrBlank()) {
                        spineFiles.add(resolveRelativePath(opfDir, href))
                    }
                }
            }
        }

        // Fallback if OPF missing/malformed: sort .xhtml / .html files naturally
        if (spineFiles.isEmpty()) {
            spineFiles.addAll(
                zipMap.keys
                    .filter {
                        val low = it.lowercase()
                        low.endsWith(".xhtml") || low.endsWith(".html") || low.endsWith(".htm")
                    }
                    .sortedWith { a, b -> naturalCompare(a, b) }
            )
        }

        // Fallback cover detection by filename if not specified in OPF
        if (coverBytes == null) {
            val coverCandidateKey = zipMap.keys.firstOrNull { key ->
                val low = key.lowercase()
                val ext = low.substringAfterLast('.', "")
                ext in IMAGE_EXTENSIONS && (low.contains("cover") || low.contains("capa"))
            }
            if (coverCandidateKey != null) {
                coverBytes = zipMap[coverCandidateKey]
            }
        }

        val allPages = mutableListOf<EbookPageContent>()
        if (coverBytes != null) {
            allPages.add(EbookPageContent.ImagePage(coverBytes, "Capa"))
        }

        val textParagraphs = mutableListOf<EbookParagraph>()
        spineFiles.forEach { path ->
            val htmlBytes = findZipEntryCaseInsensitive(zipMap, path) ?: return@forEach
            val html = String(htmlBytes, Charsets.UTF_8)
            val parsed = parseHtmlToParagraphs(html)
            if (parsed.isNotEmpty()) {
                // Separate chapter content cleanly if already non-empty
                if (textParagraphs.isNotEmpty()) {
                    allPages.addAll(paginateParagraphs(textParagraphs))
                    textParagraphs.clear()
                }
                textParagraphs.addAll(parsed)
            }
        }
        if (textParagraphs.isNotEmpty()) {
            allPages.addAll(paginateParagraphs(textParagraphs))
        }

        if (allPages.isEmpty()) {
            allPages.add(
                EbookPageContent.TextPage(
                    listOf(
                        EbookParagraph(bookTitle, isHeading = true, headingLevel = 1),
                        EbookParagraph("Não foi possível extrair texto legível deste EPUB.")
                    ),
                    isCoverHeader = true
                )
            )
        }

        return EbookDocument(
            title = bookTitle,
            author = bookAuthor,
            docType = NoteDocumentType.EPUB,
            coverBytes = coverBytes,
            pages = allPages
        )
    }

    // =========================================================================
    // MOBI / AZW (PalmDB + PalmDOC LZ77 + EXTH)
    // =========================================================================
    fun parseMobi(bytes: ByteArray, fallbackTitle: String = "MOBI"): EbookDocument {
        if (bytes.size < 86) {
            return EbookDocument(
                title = fallbackTitle,
                author = "",
                docType = NoteDocumentType.MOBI,
                pages = listOf(EbookPageContent.TextPage(listOf(EbookParagraph("Arquivo MOBI inválido ou vazio."))))
            )
        }

        var bookTitle = fallbackTitle
        var bookAuthor = ""
        var coverBytes: ByteArray? = null
        val textOutput = ByteArrayOutputStream()
        var encodingCharset: Charset = Charsets.UTF_8

        try {
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            // PalmDB name is bytes 0..31
            val pdbName = String(bytes, 0, 32, Charsets.ISO_8859_1).trim('\u0000', ' ')
            if (pdbName.isNotBlank() && fallbackTitle == "MOBI") {
                bookTitle = pdbName
            }

            val numRecords = buf.getShort(76).toInt() and 0xFFFF
            if (numRecords < 2 || 78 + numRecords * 8 > bytes.size) {
                throw IllegalStateException("Cabeçalho PalmDB inválido")
            }

            val recordOffsets = IntArray(numRecords)
            for (i in 0 until numRecords) {
                recordOffsets[i] = buf.getInt(78 + i * 8)
            }

            fun getRecordBytes(idx: Int): ByteArray {
                if (idx < 0 || idx >= numRecords) return ByteArray(0)
                val start = recordOffsets[idx]
                val end = if (idx + 1 < numRecords) recordOffsets[idx + 1] else bytes.size
                if (start < 0 || start >= bytes.size || end <= start) return ByteArray(0)
                val clampedEnd = minOf(end, bytes.size)
                return bytes.copyOfRange(start, clampedEnd)
            }

            val rec0 = getRecordBytes(0)
            if (rec0.size < 16) throw IllegalStateException("Registro 0 inválido")
            val r0Buf = ByteBuffer.wrap(rec0).order(ByteOrder.BIG_ENDIAN)
            val compression = r0Buf.getShort(0).toInt() and 0xFFFF
            val textRecordCount = (r0Buf.getShort(8).toInt() and 0xFFFF).coerceAtMost(numRecords - 1)
            var extraDataFlags = 0
            var firstImageIndex = -1
            var coverOffset = -1

            // Check MOBI header starting at offset 16
            if (rec0.size >= 132 &&
                rec0[16] == 'M'.code.toByte() &&
                rec0[17] == 'O'.code.toByte() &&
                rec0[18] == 'B'.code.toByte() &&
                rec0[19] == 'I'.code.toByte()
            ) {
                val mobiHeaderLen = r0Buf.getInt(20)
                val encodingCode = r0Buf.getInt(28)
                encodingCharset = when (encodingCode) {
                    65001 -> Charsets.UTF_8
                    1252 -> Charset.forName("windows-1252")
                    else -> Charsets.UTF_8
                }
                val fullNameOffset = r0Buf.getInt(84)
                val fullNameLen = r0Buf.getInt(88)
                if (fullNameOffset > 0 && fullNameLen > 0 && fullNameOffset + fullNameLen <= rec0.size) {
                    val fullTitle = String(rec0, fullNameOffset, fullNameLen, encodingCharset).trim('\u0000', ' ')
                    if (fullTitle.isNotBlank()) bookTitle = fullTitle
                }
                if (rec0.size >= 112) {
                    firstImageIndex = r0Buf.getInt(108)
                }
                val exthFlags = if (rec0.size >= 132) r0Buf.getInt(128) else 0
                if (mobiHeaderLen >= 228 && rec0.size >= 244) {
                    extraDataFlags = r0Buf.getInt(240)
                }

                // Parse EXTH header if bit 6 (0x40) is set
                val exthStart = 16 + mobiHeaderLen
                if ((exthFlags and 0x40) != 0 &&
                    exthStart + 12 <= rec0.size &&
                    rec0[exthStart] == 'E'.code.toByte() &&
                    rec0[exthStart + 1] == 'X'.code.toByte() &&
                    rec0[exthStart + 2] == 'T'.code.toByte() &&
                    rec0[exthStart + 3] == 'H'.code.toByte()
                ) {
                    val exthCount = r0Buf.getInt(exthStart + 8)
                    var pos = exthStart + 12
                    for (j in 0 until exthCount) {
                        if (pos + 8 > rec0.size) break
                        val recType = r0Buf.getInt(pos)
                        val recLen = r0Buf.getInt(pos + 4)
                        if (recLen < 8 || pos + recLen > rec0.size) break
                        val payloadStart = pos + 8
                        val payloadLen = recLen - 8
                        when (recType) {
                            100 -> bookAuthor = String(rec0, payloadStart, payloadLen, encodingCharset).trim('\u0000', ' ')
                            503 -> {
                                val updatedTitle = String(rec0, payloadStart, payloadLen, encodingCharset).trim('\u0000', ' ')
                                if (updatedTitle.isNotBlank()) bookTitle = updatedTitle
                            }
                            201 -> if (payloadLen >= 4) {
                                coverOffset = r0Buf.getInt(payloadStart)
                            }
                        }
                        pos += recLen
                    }
                }
            }

            if (firstImageIndex > 0 && coverOffset >= 0 && firstImageIndex + coverOffset < numRecords) {
                val candidate = getRecordBytes(firstImageIndex + coverOffset)
                if (looksLikeBitmap(candidate)) {
                    coverBytes = candidate
                }
            }

            // Decompress text records
            if (compression == 1 || compression == 2) {
                for (rIdx in 1..textRecordCount) {
                    val raw = getRecordBytes(rIdx)
                    val trimmed = trimMobiTrailingEntries(raw, extraDataFlags)
                    val chunk = if (compression == 2) decompressPalmDoc(trimmed) else trimmed
                    textOutput.write(chunk)
                }
            } else {
                // Fallback: Huff/CDIC unsupported natively — extract readable UTF-8 segments if any
                return EbookDocument(
                    title = bookTitle,
                    author = bookAuthor,
                    docType = NoteDocumentType.MOBI,
                    coverBytes = coverBytes,
                    pages = buildMobiUnsupportedCompressionPages(bookTitle, bookAuthor)
                )
            }
        } catch (_: Exception) {
        }

        val decodedText = String(textOutput.toByteArray(), encodingCharset)
        val paragraphs = if (decodedText.contains('<') && decodedText.contains('>')) {
            parseHtmlToParagraphs(decodedText)
        } else {
            decodedText
                .split(Regex("\\r?\\n\\s*\\r?\\n"))
                .mapNotNull { chunk ->
                    val cleaned = chunk.replace(Regex("\\s+"), " ").trim()
                    if (cleaned.isBlank()) null else EbookParagraph(cleaned)
                }
        }

        val allPages = mutableListOf<EbookPageContent>()
        if (coverBytes != null) {
            allPages.add(EbookPageContent.ImagePage(coverBytes, "Capa"))
        } else {
            val headerLines = mutableListOf(EbookParagraph(bookTitle, isHeading = true, headingLevel = 1))
            if (bookAuthor.isNotBlank()) {
                headerLines.add(EbookParagraph(bookAuthor, isItalic = true))
            }
            allPages.add(EbookPageContent.TextPage(headerLines, isCoverHeader = true))
        }
        allPages.addAll(paginateParagraphs(paragraphs))

        if (allPages.size == 1 && paragraphs.isEmpty()) {
            allPages.add(
                EbookPageContent.TextPage(
                    listOf(EbookParagraph("Nenhum conteúdo textual legível foi encontrado neste arquivo MOBI."))
                )
            )
        }

        return EbookDocument(
            title = bookTitle,
            author = bookAuthor,
            docType = NoteDocumentType.MOBI,
            coverBytes = coverBytes,
            pages = allPages
        )
    }

    private fun buildMobiUnsupportedCompressionPages(title: String, author: String): List<EbookPageContent> {
        val list = mutableListOf(
            EbookParagraph(title, isHeading = true, headingLevel = 1)
        )
        if (author.isNotBlank()) list.add(EbookParagraph(author, isItalic = true))
        list.add(
            EbookParagraph(
                "Este arquivo utiliza compressão avançada (Huff/CDIC ou DRM) não suportada diretamente sem DRM-free PalmDOC."
            )
        )
        return listOf(EbookPageContent.TextPage(list, isCoverHeader = true))
    }

    private fun trimMobiTrailingEntries(data: ByteArray, extraDataFlags: Int): ByteArray {
        if (extraDataFlags == 0 || data.isEmpty()) return data
        var end = data.size
        // Multibyte / extra trailing flags bits 1..15 indicate trailing size-encoded entries
        var flags = extraDataFlags ushr 1
        while (flags != 0 && end > 0) {
            if ((flags and 1) != 0) {
                var size = 0
                var shift = 0
                var pos = end - 1
                while (pos >= 0 && shift < 28) {
                    val b = data[pos].toInt() and 0xFF
                    size = size or ((b and 0x7F) shl shift)
                    shift += 7
                    pos--
                    if ((b and 0x80) != 0) break
                }
                if (size in 1..end) {
                    end -= size
                }
            }
            flags = flags ushr 1
        }
        if ((extraDataFlags and 1) != 0 && end > 0) {
            val overlap = (data[end - 1].toInt() and 0x03) + 1
            if (overlap in 1 until end) {
                end -= overlap
            }
        }
        return if (end == data.size) data else data.copyOf(end.coerceAtLeast(0))
    }

    private fun decompressPalmDoc(compressed: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(compressed.size * 2)
        var i = 0
        while (i < compressed.size) {
            val c = compressed[i++].toInt() and 0xFF
            when {
                c == 0 -> out.write(0)
                c in 1..8 -> {
                    val count = minOf(c, compressed.size - i)
                    out.write(compressed, i, count)
                    i += count
                }
                c <= 0x7F -> out.write(c)
                c >= 0xC0 -> {
                    out.write(' '.code)
                    out.write(c xor 0x80)
                }
                else -> {
                    if (i >= compressed.size) break
                    val next = compressed[i++].toInt() and 0xFF
                    val pair = (c shl 8) or next
                    val distance = (pair ushr 3) and 0x07FF
                    val length = (pair and 0x07) + 3
                    val existing = out.toByteArray()
                    val startPos = existing.size - distance
                    if (distance > 0 && startPos >= 0) {
                        for (k in 0 until length) {
                            val srcIdx = startPos + (k % distance)
                            if (srcIdx in existing.indices) {
                                out.write(existing[srcIdx].toInt() and 0xFF)
                            }
                        }
                    }
                }
            }
        }
        return out.toByteArray()
    }

    private fun looksLikeBitmap(data: ByteArray): Boolean {
        if (data.size < 8) return false
        val isJpeg = data[0] == 0xFF.toByte() && data[1] == 0xD8.toByte()
        val isPng = data[0] == 0x89.toByte() && data[1] == 0x50.toByte() && data[2] == 0x4E.toByte()
        val isGif = data[0] == 'G'.code.toByte() && data[1] == 'I'.code.toByte() && data[2] == 'F'.code.toByte()
        val isWebp = data.size >= 12 &&
            data[0] == 'R'.code.toByte() && data[1] == 'I'.code.toByte() &&
            data[8] == 'W'.code.toByte() && data[9] == 'E'.code.toByte()
        return isJpeg || isPng || isGif || isWebp
    }

    // =========================================================================
    // FB2 (FictionBook XML)
    // =========================================================================
    fun parseFb2(bytes: ByteArray, fallbackTitle: String = "FB2"): EbookDocument {
        var bookTitle = fallbackTitle
        val authors = mutableListOf<String>()
        var coverRefId: String? = null
        val binaryMap = mutableMapOf<String, ByteArray>()
        val paragraphs = mutableListOf<EbookParagraph>()

        try {
            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
            val parser = factory.newPullParser()
            parser.setInput(bytes.inputStream(), null)

            var event = parser.eventType
            var inTitleInfo = false
            var inAuthor = false
            var inCoverpage = false
            var inBody = false
            var inSectionTitle = false
            var firstName = ""
            var lastName = ""
            var currentBinaryId: String? = null
            val textBuf = StringBuilder()
            var capturingParagraphTag: String? = null

            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        val tag = parser.name?.substringAfter(':')?.lowercase().orEmpty()
                        when (tag) {
                            "title-info" -> inTitleInfo = true
                            "author" -> if (inTitleInfo) {
                                inAuthor = true; firstName = ""; lastName = ""
                            }
                            "coverpage" -> if (inTitleInfo) inCoverpage = true
                            "body" -> inBody = true
                            "binary" -> {
                                currentBinaryId = parser.getAttributeValue(null, "id")?.trimStart('#')
                                textBuf.clear()
                            }
                            "image" -> {
                                val href = (0 until parser.attributeCount).firstNotNullOfOrNull { idx ->
                                    val attrName = parser.getAttributeName(idx)
                                    if (attrName.endsWith("href", ignoreCase = true)) {
                                        parser.getAttributeValue(idx)?.trimStart('#')
                                    } else null
                                }
                                if (inCoverpage && !href.isNullOrBlank() && coverRefId == null) {
                                    coverRefId = href
                                }
                            }
                            "book-title" -> if (inTitleInfo) textBuf.clear()
                            "first-name", "last-name" -> if (inAuthor) textBuf.clear()
                            "title" -> if (inBody) inSectionTitle = true
                            "subtitle", "p", "v", "text-author" -> if (inBody) {
                                capturingParagraphTag = tag
                                textBuf.clear()
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        textBuf.append(parser.text ?: "")
                    }
                    XmlPullParser.END_TAG -> {
                        val tag = parser.name?.substringAfter(':')?.lowercase().orEmpty()
                        when (tag) {
                            "title-info" -> inTitleInfo = false
                            "coverpage" -> inCoverpage = false
                            "author" -> if (inAuthor) {
                                val full = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
                                if (full.isNotBlank()) authors.add(full)
                                inAuthor = false
                            }
                            "first-name" -> if (inAuthor) firstName = textBuf.toString().trim()
                            "last-name" -> if (inAuthor) lastName = textBuf.toString().trim()
                            "book-title" -> if (inTitleInfo) {
                                val t = textBuf.toString().trim()
                                if (t.isNotBlank()) bookTitle = t
                            }
                            "binary" -> {
                                val binId = currentBinaryId
                                if (!binId.isNullOrBlank()) {
                                    try {
                                        val cleanB64 = textBuf.toString().replace("\\s".toRegex(), "")
                                        binaryMap[binId] = Base64.decode(cleanB64, Base64.DEFAULT)
                                    } catch (_: Exception) {
                                    }
                                }
                                currentBinaryId = null
                                textBuf.clear()
                            }
                            "title" -> if (inBody) inSectionTitle = false
                            "body" -> inBody = false
                            "subtitle", "p", "v", "text-author" -> {
                                if (inBody && capturingParagraphTag == tag) {
                                    val clean = textBuf.toString().replace(Regex("\\s+"), " ").trim()
                                    if (clean.isNotBlank()) {
                                        val isHead = inSectionTitle || tag == "subtitle"
                                        paragraphs.add(
                                            EbookParagraph(
                                                text = clean,
                                                isHeading = isHead,
                                                headingLevel = if (inSectionTitle) 2 else if (tag == "subtitle") 3 else 0,
                                                isItalic = tag == "text-author"
                                            )
                                        )
                                    }
                                    capturingParagraphTag = null
                                    textBuf.clear()
                                }
                            }
                        }
                    }
                }
                event = parser.next()
            }
        } catch (_: Exception) {
        }

        val authorStr = authors.joinToString(", ")
        val coverBytes = coverRefId?.let { binaryMap[it] } ?: binaryMap.values.firstOrNull { looksLikeBitmap(it) }
        val allPages = mutableListOf<EbookPageContent>()
        if (coverBytes != null) {
            allPages.add(EbookPageContent.ImagePage(coverBytes, "Capa"))
        } else {
            val header = mutableListOf(EbookParagraph(bookTitle, isHeading = true, headingLevel = 1))
            if (authorStr.isNotBlank()) header.add(EbookParagraph(authorStr, isItalic = true))
            allPages.add(EbookPageContent.TextPage(header, isCoverHeader = true))
        }
        allPages.addAll(paginateParagraphs(paragraphs))

        return EbookDocument(
            title = bookTitle,
            author = authorStr,
            docType = NoteDocumentType.FB2,
            coverBytes = coverBytes,
            pages = allPages
        )
    }

    // =========================================================================
    // Bitmap Page & Cover/Page-1 Rendering
    // =========================================================================
    fun renderPageBitmap(
        document: EbookDocument,
        pageIndex: Int,
        targetWidthPx: Int
    ): Bitmap? {
        val page = document.pages.getOrNull(pageIndex) ?: return null
        val safeWidth = targetWidthPx.coerceIn(180, 1800)

        return when (page) {
            is EbookPageContent.ImagePage -> renderImagePageBitmap(page.bytes, safeWidth)
            is EbookPageContent.TextPage -> renderTextPageBitmap(
                page = page,
                pageNumber = pageIndex + 1,
                totalPages = document.pages.size,
                bookTitle = document.title,
                targetWidthPx = safeWidth
            )
        }
    }

    fun renderCoverOrPageOneThumbnail(
        context: Context,
        pathOrUri: String?,
        docType: NoteDocumentType,
        targetWidthPx: Int = 320
    ): Bitmap? {
        if (pathOrUri.isNullOrBlank()) return null
        return try {
            val fallbackTitle = pathOrUri.substringAfterLast('/').substringBeforeLast('.').ifBlank { docType.label }
            val parsed = ExternalDocumentUriHelper.openInputStream(context, pathOrUri)?.use { stream ->
                buildParsedFromStream(stream, docType, fallbackTitle)
            } ?: return null

            if (parsed.coverBytes != null) {
                renderImagePageBitmap(parsed.coverBytes, targetWidthPx)
            } else {
                renderPageBitmap(parsed, 0, targetWidthPx)
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun renderImagePageBitmap(imageBytes: ByteArray, targetWidthPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= targetWidthPx) {
            sampleSize *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val decoded = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, decodeOpts) ?: return null
        if (decoded.width == targetWidthPx) return decoded

        val scaledHeight = ((decoded.height.toFloat() / decoded.width.toFloat()) * targetWidthPx)
            .toInt()
            .coerceIn(160, (targetWidthPx * 2.4f).toInt())
        val scaled = Bitmap.createScaledBitmap(decoded, targetWidthPx, scaledHeight, true)
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    private fun renderTextPageBitmap(
        page: EbookPageContent.TextPage,
        pageNumber: Int,
        totalPages: Int,
        bookTitle: String,
        targetWidthPx: Int
    ): Bitmap {
        val sheetHeight = (targetWidthPx * 1.414f).toInt().coerceAtLeast(targetWidthPx)
        val bitmap = Bitmap.createBitmap(targetWidthPx, sheetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(AndroidColor.WHITE)

        val marginX = (targetWidthPx * 0.085f)
        val marginTop = (sheetHeight * 0.065f)
        val marginBottom = (sheetHeight * 0.065f)
        val contentWidth = (targetWidthPx - marginX * 2).toInt().coerceAtLeast(80)
        val scale = targetWidthPx / 720f

        val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.parseColor("#64748B")
            textSize = (18f * scale).coerceAtLeast(9f)
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        }
        if (!page.isCoverHeader && bookTitle.isNotBlank() && scale >= 0.45f) {
            val truncated = if (bookTitle.length > 42) bookTitle.take(39) + "..." else bookTitle
            canvas.drawText(truncated, marginX, marginTop * 0.65f, headerPaint)
        }

        val h1Paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.parseColor("#0F172A")
            textSize = (36f * scale).coerceAtLeast(13f)
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        val h2Paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.parseColor("#0F172A")
            textSize = (28f * scale).coerceAtLeast(11f)
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.parseColor("#1E293B")
            textSize = (23f * scale).coerceAtLeast(9.5f)
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        }
        val italicPaint = TextPaint(bodyPaint).apply {
            typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
            color = AndroidColor.parseColor("#475569")
        }
        val boldPaint = TextPaint(bodyPaint).apply {
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }

        var y = if (page.isCoverHeader) sheetHeight * 0.28f else marginTop

        for (para in page.paragraphs) {
            if (para.text.isBlank()) continue
            val paint = when {
                para.isHeading && para.headingLevel <= 1 -> h1Paint
                para.isHeading -> h2Paint
                para.isBold -> boldPaint
                para.isItalic -> italicPaint
                else -> bodyPaint
            }
            val align = if (page.isCoverHeader) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL
            val layout = StaticLayout.Builder
                .obtain(para.text, 0, para.text.length, paint, contentWidth)
                .setAlignment(align)
                .setLineSpacing(0f, 1.32f)
                .build()

            if (y + layout.height > sheetHeight - marginBottom) break
            canvas.save()
            canvas.translate(marginX, y)
            layout.draw(canvas)
            canvas.restore()

            y += layout.height + (if (para.isHeading) 18f else 12f) * scale
        }

        if (scale >= 0.45f) {
            val footerText = "$pageNumber / $totalPages"
            val bounds = Rect()
            headerPaint.getTextBounds(footerText, 0, footerText.length, bounds)
            canvas.drawText(
                footerText,
                (targetWidthPx - bounds.width()) / 2f,
                sheetHeight - marginBottom * 0.42f,
                headerPaint
            )
        }

        return bitmap
    }

    // =========================================================================
    // Markdown Conversion ("Converter para nota")
    // =========================================================================
    fun extractMarkdown(
        context: Context,
        pathOrUri: String?,
        docType: NoteDocumentType
    ): String? {
        if (pathOrUri.isNullOrBlank()) return null
        return try {
            val fallbackTitle = pathOrUri.substringAfterLast('/').substringBeforeLast('.').ifBlank { docType.label }
            ExternalDocumentUriHelper.openInputStream(context, pathOrUri)?.use { stream ->
                val doc = buildParsedFromStream(stream, docType, fallbackTitle)
                extractMarkdown(doc)
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun extractMarkdown(document: EbookDocument): String {
        val sb = StringBuilder()
        if (document.title.isNotBlank()) {
            sb.append("# ").append(document.title).append("\n")
        }
        if (document.author.isNotBlank()) {
            sb.append("*").append(document.author).append("*\n")
        }
        if (sb.isNotEmpty()) sb.append("\n")

        document.pages.forEachIndexed { idx, page ->
            when (page) {
                is EbookPageContent.ImagePage -> {
                    sb.append("> [Página de imagem ${idx + 1}: ${page.label}]\n\n")
                }
                is EbookPageContent.TextPage -> {
                    if (page.isCoverHeader && idx == 0) return@forEachIndexed
                    page.paragraphs.forEach { para ->
                        if (para.isHeading) {
                            val hashes = "#".repeat(para.headingLevel.coerceIn(1, 4))
                            sb.append(hashes).append(" ").append(para.text).append("\n\n")
                        } else if (para.isBold) {
                            sb.append("**").append(para.text).append("**\n\n")
                        } else if (para.isItalic) {
                            sb.append("*").append(para.text).append("*\n\n")
                        } else {
                            sb.append(para.text).append("\n\n")
                        }
                    }
                }
            }
        }
        return sb.toString().trim().ifBlank { "# ${document.title}\n\n*(Conteúdo sem texto extraível)*" }
    }

    // =========================================================================
    // Internal Parsing Helpers
    // =========================================================================
    private data class OpfItem(val id: String, val href: String, val mediaType: String)
    private data class ParsedOpf(
        val title: String?,
        val author: String?,
        val manifestById: Map<String, OpfItem>,
        val spineIdRefs: List<String>,
        val coverImageHref: String?
    )

    private fun parseContainerOpfPath(bytes: ByteArray): String? {
        return try {
            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
            val parser = factory.newPullParser()
            parser.setInput(bytes.inputStream(), null)
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG &&
                    parser.name?.substringAfter(':')?.equals("rootfile", ignoreCase = true) == true
                ) {
                    val path = parser.getAttributeValue(null, "full-path")
                    if (!path.isNullOrBlank()) return path.trim()
                }
                event = parser.next()
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun parseOpf(bytes: ByteArray): ParsedOpf {
        var title: String? = null
        var author: String? = null
        val manifestById = linkedMapOf<String, OpfItem>()
        val spineIdRefs = mutableListOf<String>()
        var coverMetaId: String? = null
        var coverImageHref: String? = null

        try {
            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
            val parser = factory.newPullParser()
            parser.setInput(bytes.inputStream(), null)

            var event = parser.eventType
            var captureTag: String? = null
            val textBuf = StringBuilder()

            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        val tag = parser.name?.substringAfter(':')?.lowercase().orEmpty()
                        when (tag) {
                            "title", "creator" -> {
                                captureTag = tag
                                textBuf.clear()
                            }
                            "meta" -> {
                                val name = parser.getAttributeValue(null, "name")
                                val content = parser.getAttributeValue(null, "content")
                                if (name.equals("cover", ignoreCase = true) && !content.isNullOrBlank()) {
                                    coverMetaId = content.trim()
                                }
                            }
                            "item" -> {
                                val id = parser.getAttributeValue(null, "id").orEmpty()
                                val href = parser.getAttributeValue(null, "href").orEmpty()
                                val mediaType = parser.getAttributeValue(null, "media-type").orEmpty()
                                val properties = parser.getAttributeValue(null, "properties").orEmpty()
                                if (id.isNotBlank() && href.isNotBlank()) {
                                    manifestById[id] = OpfItem(id, href, mediaType)
                                    if (properties.contains("cover-image", ignoreCase = true)) {
                                        coverImageHref = href
                                    }
                                }
                            }
                            "itemref" -> {
                                val idref = parser.getAttributeValue(null, "idref").orEmpty()
                                if (idref.isNotBlank()) spineIdRefs.add(idref)
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (captureTag != null) textBuf.append(parser.text ?: "")
                    }
                    XmlPullParser.END_TAG -> {
                        val tag = parser.name?.substringAfter(':')?.lowercase().orEmpty()
                        if (tag == captureTag) {
                            val valStr = textBuf.toString().trim()
                            if (valStr.isNotBlank()) {
                                if (tag == "title" && title.isNullOrBlank()) title = valStr
                                if (tag == "creator" && author.isNullOrBlank()) author = valStr
                            }
                            captureTag = null
                            textBuf.clear()
                        }
                    }
                }
                event = parser.next()
            }
        } catch (_: Exception) {
        }

        if (coverImageHref == null && !coverMetaId.isNullOrBlank()) {
            coverImageHref = manifestById[coverMetaId]?.href
        }
        return ParsedOpf(title, author, manifestById, spineIdRefs, coverImageHref)
    }

    private fun parseHtmlToParagraphs(html: String): List<EbookParagraph> {
        val bodyOnly = Regex("<body[^>]*>([\\s\\S]*?)</body>", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1) ?: html

        val cleaned = bodyOnly
            .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<(h[1-4])[^>]*>([\\s\\S]*?)</\\1>", RegexOption.IGNORE_CASE)) { m ->
                val level = m.groupValues[1].drop(1).toIntOrNull() ?: 2
                "\n@@H$level@@${stripTagsAndUnescape(m.groupValues[2])}@@\n"
            }
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</(p|div|li|blockquote|section|tr|h[56])>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<li[^>]*>", RegexOption.IGNORE_CASE), "• ")

        val plain = stripTagsAndUnescape(cleaned)
        val result = mutableListOf<EbookParagraph>()
        val hMarker = Regex("^@@H([1-4])@@(.*)@@$")

        for (rawLine in plain.lines()) {
            val line = rawLine.replace(Regex("[ \\t\\x0B]+"), " ").trim()
            if (line.isBlank()) continue
            val match = hMarker.matchEntire(line)
            if (match != null) {
                val lvl = match.groupValues[1].toIntOrNull() ?: 2
                val headingText = match.groupValues[2].trim()
                if (headingText.isNotBlank()) {
                    result.add(EbookParagraph(headingText, isHeading = true, headingLevel = lvl))
                }
            } else {
                result.add(EbookParagraph(line))
            }
        }
        return result
    }

    private fun stripTagsAndUnescape(input: String): String {
        val noTags = input.replace(Regex("<[^>]+>"), "")
        return noTags
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("&hellip;", "…")
            .replace(Regex("&#(\\d+);")) { m ->
                m.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: ""
            }
            .replace(Regex("&#x([0-9a-fA-F]+);")) { m ->
                m.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: ""
            }
    }

    private fun paginateParagraphs(paragraphs: List<EbookParagraph>): List<EbookPageContent.TextPage> {
        if (paragraphs.isEmpty()) return emptyList()
        val pages = mutableListOf<EbookPageContent.TextPage>()
        val current = mutableListOf<EbookParagraph>()
        var charBudget = 0
        val maxCharsPerPage = 1050

        for (para in paragraphs) {
            val estimatedWeight = para.text.length + (if (para.isHeading) 180 else 75)
            if (current.isNotEmpty() && charBudget + estimatedWeight > maxCharsPerPage) {
                pages.add(EbookPageContent.TextPage(current.toList()))
                current.clear()
                charBudget = 0
            }
            // Split oversized single paragraph so no single page overflows bottom margin
            if (para.text.length > maxCharsPerPage) {
                var remaining = para.text
                while (remaining.length > maxCharsPerPage) {
                    var cut = remaining.lastIndexOf(' ', maxCharsPerPage)
                    if (cut < maxCharsPerPage / 2) cut = maxCharsPerPage
                    val slice = remaining.substring(0, cut).trim()
                    if (slice.isNotEmpty()) {
                        pages.add(EbookPageContent.TextPage(listOf(para.copy(text = slice))))
                    }
                    remaining = remaining.substring(cut).trim()
                }
                if (remaining.isNotEmpty()) {
                    current.add(para.copy(text = remaining))
                    charBudget = remaining.length + 75
                }
            } else {
                current.add(para)
                charBudget += estimatedWeight
            }
        }
        if (current.isNotEmpty()) {
            pages.add(EbookPageContent.TextPage(current.toList()))
        }
        return pages
    }

    private fun resolveRelativePath(baseDir: String, relativeHref: String): String {
        val cleanHref = relativeHref.substringBefore('#').trimStart('/')
        if (baseDir.isBlank()) return cleanHref
        val segments = (baseDir.split('/') + cleanHref.split('/')).filter { it.isNotBlank() && it != "." }
        val resolved = mutableListOf<String>()
        for (seg in segments) {
            if (seg == "..") {
                if (resolved.isNotEmpty()) resolved.removeAt(resolved.lastIndex)
            } else {
                resolved.add(seg)
            }
        }
        return resolved.joinToString("/")
    }

    private fun findZipEntryCaseInsensitive(map: Map<String, ByteArray>, path: String): ByteArray? {
        map[path]?.let { return it }
        return map.entries.firstOrNull { it.key.equals(path, ignoreCase = true) }?.value
    }

    private fun readBoundedBytes(input: InputStream, maxBytes: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (total < maxBytes) {
            val read = input.read(buf, 0, minOf(buf.size, maxBytes - total))
            if (read <= 0) break
            out.write(buf, 0, read)
            total += read
        }
        return out.toByteArray()
    }

    private fun naturalCompare(a: String, b: String): Int {
        val chunksA = splitAlphaNum(a.lowercase())
        val chunksB = splitAlphaNum(b.lowercase())
        val len = minOf(chunksA.size, chunksB.size)
        for (i in 0 until len) {
            val ca = chunksA[i]
            val cb = chunksB[i]
            val numA = ca.toLongOrNull()
            val numB = cb.toLongOrNull()
            val cmp = if (numA != null && numB != null) numA.compareTo(numB) else ca.compareTo(cb)
            if (cmp != 0) return cmp
        }
        return chunksA.size.compareTo(chunksB.size)
    }

    private fun splitAlphaNum(s: String): List<String> {
        val res = mutableListOf<String>()
        var i = 0
        while (i < s.length) {
            val start = i
            val isDigit = s[i].isDigit()
            while (i < s.length && s[i].isDigit() == isDigit) i++
            res.add(s.substring(start, i))
        }
        return res
    }
}
