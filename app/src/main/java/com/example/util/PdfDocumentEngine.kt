package com.example.util

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.InputStream
import kotlin.math.abs
import kotlin.math.roundToInt

data class PdfStyledSegment(
    val text: String,
    val xStart: Float,
    val xEnd: Float,
    val y: Float,
    val fontSize: Float,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isMonospace: Boolean = false,
    val pageNumber: Int = 1
)

sealed interface PdfConversionResult {
    data class Success(val markdown: String, val pageCount: Int) : PdfConversionResult
    data class Error(val message: String) : PdfConversionResult
}

/**
 * Extracts structured typography and layout from PDF documents via PDFBox-Android
 * and converts them into clean Citra Markdown obeying Citra's formatting rules:
 * Headings (#-#####), **bold**, *italic*, `code` / ```fenced code```,
 * checklists (- [ ] / - [x]), bullet & numbered lists, quotes (> ), callouts (> [!INFO|TIP|WARNING|IMPORTANT]),
 * pipe tables (| col |), and paragraph continuity.
 */
object PdfDocumentEngine {

    @Volatile
    private var pdfBoxInitialized = false

    fun ensureInitialized(context: Context?) {
        if (pdfBoxInitialized || context == null) return
        synchronized(this) {
            if (!pdfBoxInitialized) {
                try {
                    PDFBoxResourceLoader.init(context.applicationContext)
                } catch (_: Throwable) {
                    // Ignore in non-Android unit test harnesses if assets are unavailable
                }
                pdfBoxInitialized = true
            }
        }
    }

    fun extractMarkdown(
        context: Context?,
        inputStream: InputStream
    ): PdfConversionResult {
        ensureInitialized(context)
        return try {
            PDDocument.load(inputStream).use { document ->
                if (document.isEncrypted) {
                    return PdfConversionResult.Error("Este PDF está protegido por senha e não pode ser convertido automaticamente.")
                }
                val pageCount = document.numberOfPages
                val pages = mutableListOf<List<PdfStyledSegment>>()

                val stripper = object : PDFTextStripper() {
                    private var currentPageSegments = mutableListOf<PdfStyledSegment>()
                    private var activePageNum = 1

                    init {
                        sortByPosition = true
                    }

                    override fun startPage(page: com.tom_roush.pdfbox.pdmodel.PDPage?) {
                        super.startPage(page)
                        activePageNum = currentPageNo
                        currentPageSegments = mutableListOf()
                    }

                    override fun endPage(page: com.tom_roush.pdfbox.pdmodel.PDPage?) {
                        pages.add(currentPageSegments.toList())
                        super.endPage(page)
                    }

                    override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
                        if (text.isNullOrEmpty() || textPositions.isNullOrEmpty()) return
                        // Break textPositions into runs with uniform font/style and without wide horizontal column jumps
                        var buf = StringBuilder()
                        var firstPos = textPositions.first()
                        var lastPos = firstPos
                        var curBold = detectBold(firstPos)
                        var curItalic = detectItalic(firstPos)
                        var curMono = detectMonospace(firstPos)
                        var curSize = resolveFontSize(firstPos)

                        fun flushSegment() {
                            val str = buf.toString()
                            if (str.isNotEmpty()) {
                                currentPageSegments.add(
                                    PdfStyledSegment(
                                        text = str,
                                        xStart = firstPos.xDirAdj,
                                        xEnd = lastPos.xDirAdj + lastPos.widthDirAdj,
                                        y = firstPos.yDirAdj,
                                        fontSize = curSize,
                                        isBold = curBold,
                                        isItalic = curItalic,
                                        isMonospace = curMono,
                                        pageNumber = activePageNum
                                    )
                                )
                            }
                            buf = StringBuilder()
                        }

                        for (tp in textPositions) {
                            val unicode = tp.unicode ?: continue
                            val b = detectBold(tp)
                            val it = detectItalic(tp)
                            val mo = detectMonospace(tp)
                            val sz = resolveFontSize(tp)
                            val gapX = tp.xDirAdj - (lastPos.xDirAdj + lastPos.widthDirAdj)
                            val yJump = abs(tp.yDirAdj - lastPos.yDirAdj) > maxOf(curSize, sz) * 0.45f
                            val isWhitespace = unicode.all { ch -> ch.isWhitespace() }

                            val styleChanged = !isWhitespace && (b != curBold || it != curItalic || mo != curMono || abs(sz - curSize) > 1.2f)
                            val columnSplit = gapX > maxOf(curSize, 8f) * 1.15f

                            if (buf.isNotEmpty() && (styleChanged || columnSplit || yJump)) {
                                flushSegment()
                                firstPos = tp
                                curBold = b
                                curItalic = it
                                curMono = mo
                                curSize = sz
                            } else if (buf.isEmpty()) {
                                firstPos = tp
                                curBold = b
                                curItalic = it
                                curMono = mo
                                curSize = sz
                            }

                            buf.append(unicode)
                            lastPos = tp
                        }
                        flushSegment()
                    }
                }

                // Trigger PDFTextStripper traversal safely
                stripper.getText(document)

                val markdown = convertStructuredPagesToMarkdown(pages)
                PdfConversionResult.Success(markdown = markdown, pageCount = pageCount)
            }
        } catch (e: OutOfMemoryError) {
            PdfConversionResult.Error("O PDF é muito grande para extrair de uma vez na memória disponível.")
        } catch (e: Exception) {
            PdfConversionResult.Error(e.message ?: "Erro ao ler o texto estruturado deste PDF.")
        }
    }

    private data class VisualLine(
        val pageNumber: Int,
        val y: Float,
        val xStart: Float,
        val xEnd: Float,
        val maxFontSize: Float,
        val dominantFontSize: Float,
        val segments: List<PdfStyledSegment>,
        val plainText: String,
        val isMostlyBold: Boolean,
        val isMostlyItalic: Boolean,
        val isAllMonospace: Boolean,
        val cells: List<List<PdfStyledSegment>>
    )

    /**
     * Pure Kotlin layout analyzer converting positioned styled spans per PDF page
     * into Citra Markdown. Exposed internally for direct deterministic JVM unit tests.
     */
    fun convertStructuredPagesToMarkdown(pages: List<List<PdfStyledSegment>>): String {
        if (pages.isEmpty()) return ""

        val allSegments = pages.flatten().filter { it.text.isNotBlank() }
        if (allSegments.isEmpty()) return ""

        // Compute dominant body font size (weighted by character count)
        val sizeBuckets = mutableMapOf<Int, Int>()
        for (seg in allSegments) {
            val rounded = seg.fontSize.roundToInt().coerceIn(6, 72)
            val charCount = seg.text.count { !it.isWhitespace() }
            if (charCount > 0) {
                sizeBuckets[rounded] = (sizeBuckets[rounded] ?: 0) + charCount
            }
        }
        val bodyFontSize = (sizeBuckets.maxByOrNull { it.value }?.key ?: 11).toFloat()

        // Group each page into horizontal VisualLines
        val pageLines = pages.mapIndexed { idx, segs ->
            val pageNum = idx + 1
            buildVisualLinesForPage(pageNum, segs, bodyFontSize)
        }

        // Strip standalone page numbers (multi-page) or explicit "Página X de Y" running footers at page edges
        val cleanedPageLines = pageLines.map { lines ->
            stripRunningPageArtifacts(lines, totalPages = pageLines.size)
        }

        val docOut = StringBuilder()

        for (pIdx in cleanedPageLines.indices) {
            val lines = cleanedPageLines[pIdx]
            if (lines.isEmpty()) continue

            if (pIdx > 0 && docOut.isNotBlank()) {
                if (!docOut.endsWith("\n\n")) {
                    if (!docOut.endsWith("\n")) docOut.append("\n")
                    docOut.append("\n")
                }
            }

            var i = 0
            while (i < lines.size) {
                val line = lines[i]
                val trimmed = line.plainText.trim()
                if (trimmed.isEmpty()) {
                    i++
                    continue
                }

                // Ignore Citra own-PDF export footer stamp if round-tripping
                if (trimmed.contains("Exportado pelo Citra", ignoreCase = true) && trimmed.startsWith("[")) {
                    i++
                    continue
                }

                // 1. Detect multi-column table (2+ consecutive lines with >= 2 aligned columns separated by wide gaps)
                if (line.cells.size >= 2 && isTableBlockStart(lines, i, bodyFontSize)) {
                    val tableLines = mutableListOf<VisualLine>()
                    val targetColCount = line.cells.size
                    while (i < lines.size && lines[i].cells.size >= 2 &&
                        abs(lines[i].cells.size - targetColCount) <= 1 &&
                        lines[i].maxFontSize <= bodyFontSize * 1.35f
                    ) {
                        tableLines.add(lines[i])
                        i++
                    }
                    appendMarkdownTable(docOut, tableLines)
                    continue
                }

                // 2. Detect multi-line monospace code block
                if (line.isAllMonospace && trimmed.length >= 2) {
                    val codeLines = mutableListOf<String>()
                    val startX = line.xStart
                    while (i < lines.size && lines[i].isAllMonospace) {
                        val indentSpaces = ((lines[i].xStart - startX) / (bodyFontSize * 0.55f)).toInt().coerceIn(0, 12)
                        codeLines.add(" ".repeat(indentSpaces) + lines[i].plainText.trimEnd())
                        i++
                    }
                    if (codeLines.size >= 2 || codeLines.first().length > 24) {
                        if (docOut.isNotEmpty() && !docOut.endsWith("\n\n")) {
                            if (!docOut.endsWith("\n")) docOut.append("\n")
                        }
                        docOut.append("```\n")
                        codeLines.forEach { docOut.append(it).append("\n") }
                        docOut.append("```\n\n")
                    } else {
                        docOut.append("`${codeLines.first().trim()}`\n\n")
                    }
                    continue
                }

                // 3. Horizontal rule
                if (Regex("^[-_─━═*]{3,}$").matches(trimmed)) {
                    docOut.append("---\n\n")
                    i++
                    continue
                }

                // 4. Headings (# to #####) based on size ratio vs bodyFontSize
                val headingLevel = classifyHeadingLevel(line, bodyFontSize)
                if (headingLevel != null) {
                    // Merge wrapped heading lines of same font size and close Y spacing
                    val headingBuf = StringBuilder(formatSegmentsToInlineMarkdown(line.segments, stripBold = true).trim())
                    var j = i + 1
                    while (j < lines.size &&
                        classifyHeadingLevel(lines[j], bodyFontSize) == headingLevel &&
                        (lines[j].y - lines[j - 1].y) <= line.maxFontSize * 1.45f
                    ) {
                        headingBuf.append(" ").append(formatSegmentsToInlineMarkdown(lines[j].segments, stripBold = true).trim())
                        j++
                    }
                    i = j
                    val cleanHeading = headingBuf.toString().replace(Regex("^#+\\s+"), "").trim()
                    if (cleanHeading.isNotEmpty()) {
                        if (docOut.isNotEmpty() && !docOut.endsWith("\n\n")) {
                            if (!docOut.endsWith("\n")) docOut.append("\n")
                            docOut.append("\n")
                        }
                        docOut.append("#".repeat(headingLevel)).append(" ").append(cleanHeading).append("\n\n")
                    }
                    continue
                }

                // 5. Callouts
                val calloutMatch = Regex("^(INFO|TIP|WARNING|IMPORTANT|DANGER|NOTA|DICA|AVISO|IMPORTANTE)\\s*:\\s*(.+)$", RegexOption.IGNORE_CASE)
                    .matchEntire(trimmed)
                if (calloutMatch != null) {
                    val tag = when (calloutMatch.groupValues[1].uppercase()) {
                        "TIP", "DICA" -> "TIP"
                        "WARNING", "AVISO" -> "WARNING"
                        "IMPORTANT", "DANGER", "IMPORTANTE" -> "IMPORTANT"
                        else -> "INFO"
                    }
                    val bodySegments = stripPrefixFromSegments(line.segments, Regex("^\\s*(?:INFO|TIP|WARNING|IMPORTANT|DANGER|NOTA|DICA|AVISO|IMPORTANTE)\\s*:\\s*", RegexOption.IGNORE_CASE))
                    docOut.append("> [!$tag] ").append(formatSegmentsToInlineMarkdown(bodySegments).trim()).append("\n\n")
                    i++
                    continue
                }

                // 6. Checklists
                if (trimmed.startsWith("☐") || trimmed.startsWith("□") || trimmed.startsWith("[ ] ") ||
                    trimmed.startsWith("☑") || trimmed.startsWith("✓") || trimmed.startsWith("✔") ||
                    trimmed.startsWith("[x] ", ignoreCase = true)
                ) {
                    val checked = trimmed.startsWith("☑") || trimmed.startsWith("✓") || trimmed.startsWith("✔") ||
                        trimmed.startsWith("[x]", ignoreCase = true)
                    val cleaned = stripPrefixFromSegments(line.segments, Regex("^\\s*(?:☐|□|☑|✓|✔|\\[[xX ]\\])\\s*"))
                    val mark = if (checked) "x" else " "
                    docOut.append("- [$mark] ").append(formatSegmentsToInlineMarkdown(cleaned).trim()).append("\n")
                    i++
                    continue
                }

                // 7. Bullet list item
                if (Regex("^[•◦▪▫‣–—]\\s+.+$").matches(trimmed) || Regex("^[-*]\\s+.+$").matches(trimmed)) {
                    val cleaned = stripPrefixFromSegments(line.segments, Regex("^\\s*[•◦▪▫‣–—*-]\\s+"))
                    val itemBuf = StringBuilder(formatSegmentsToInlineMarkdown(cleaned).trim())
                    var j = i + 1
                    while (j < lines.size &&
                        isWrappedContinuation(lines[j - 1], lines[j], bodyFontSize) &&
                        lines[j].xStart >= line.xStart + bodyFontSize * 0.4f
                    ) {
                        itemBuf.append(" ").append(formatSegmentsToInlineMarkdown(lines[j].segments).trim())
                        j++
                    }
                    i = j
                    docOut.append("- ").append(itemBuf.toString()).append("\n")
                    continue
                }

                // 8. Numbered list item
                val numMatch = Regex("^(\\d{1,3})[.)]\\s+(.+)$").matchEntire(trimmed)
                if (numMatch != null) {
                    val numStr = numMatch.groupValues[1]
                    val cleaned = stripPrefixFromSegments(line.segments, Regex("^\\s*\\d{1,3}[.)]\\s+"))
                    val itemBuf = StringBuilder(formatSegmentsToInlineMarkdown(cleaned).trim())
                    var j = i + 1
                    while (j < lines.size &&
                        isWrappedContinuation(lines[j - 1], lines[j], bodyFontSize) &&
                        lines[j].xStart >= line.xStart + bodyFontSize * 0.4f
                    ) {
                        itemBuf.append(" ").append(formatSegmentsToInlineMarkdown(lines[j].segments).trim())
                        j++
                    }
                    i = j
                    docOut.append("$numStr. ").append(itemBuf.toString()).append("\n")
                    continue
                }

                // 9. Quote line
                if (trimmed.startsWith("> ") || (line.isMostlyItalic && line.xStart > bodyFontSize * 5f && trimmed.length > 20)) {
                    val cleaned = if (trimmed.startsWith("> ")) {
                        stripPrefixFromSegments(line.segments, Regex("^\\s*>\\s+"))
                    } else {
                        line.segments
                    }
                    docOut.append("> ").append(formatSegmentsToInlineMarkdown(cleaned, stripItalic = trimmed.startsWith("> ").not()).trim()).append("\n\n")
                    i++
                    continue
                }

                // 10. Normal prose paragraph (merge wrapped continuation lines cleanly)
                val paraBuf = StringBuilder(formatSegmentsToInlineMarkdown(line.segments).trim())
                var j = i + 1
                while (j < lines.size && isWrappedContinuation(lines[j - 1], lines[j], bodyFontSize)) {
                    val nextText = formatSegmentsToInlineMarkdown(lines[j].segments).trim()
                    if (paraBuf.endsWith("-") && nextText.firstOrNull()?.isLowerCase() == true) {
                        paraBuf.deleteCharAt(paraBuf.length - 1).append(nextText)
                    } else {
                        paraBuf.append(" ").append(nextText)
                    }
                    j++
                }
                i = j
                docOut.append(paraBuf.toString()).append("\n\n")
            }
        }

        return docOut.toString()
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun buildVisualLinesForPage(
        pageNumber: Int,
        rawSegments: List<PdfStyledSegment>,
        bodyFontSize: Float
    ): List<VisualLine> {
        val nonEmpty = rawSegments.filter { it.text.isNotEmpty() }
            .sortedWith(compareBy<PdfStyledSegment> { it.y }.thenBy { it.xStart })
        if (nonEmpty.isEmpty()) return emptyList()

        val rowGroups = mutableListOf<MutableList<PdfStyledSegment>>()
        for (seg in nonEmpty) {
            val lastRow = rowGroups.lastOrNull()
            if (lastRow == null) {
                rowGroups.add(mutableListOf(seg))
            } else {
                val avgY = lastRow.map { it.y }.average().toFloat()
                val avgSize = maxOf(lastRow.maxOf { it.fontSize }, seg.fontSize)
                if (abs(seg.y - avgY) <= avgSize * 0.48f) {
                    lastRow.add(seg)
                } else {
                    rowGroups.add(mutableListOf(seg))
                }
            }
        }

        return rowGroups.mapNotNull { rawRow ->
            val sorted = rawRow.sortedBy { it.xStart }
            val plain = joinSegmentsPlainText(sorted, bodyFontSize)
            if (plain.isBlank()) return@mapNotNull null

            val cells = splitRowIntoCells(sorted, bodyFontSize)
            val totalChars = sorted.sumOf { it.text.count { c -> !c.isWhitespace() } }.coerceAtLeast(1)
            val boldChars = sorted.filter { it.isBold }.sumOf { it.text.count { c -> !c.isWhitespace() } }
            val italicChars = sorted.filter { it.isItalic }.sumOf { it.text.count { c -> !c.isWhitespace() } }
            val monoChars = sorted.filter { it.isMonospace }.sumOf { it.text.count { c -> !c.isWhitespace() } }

            val sizeWeights = sorted.groupBy { it.fontSize.roundToInt() }
                .mapValues { (_, list) -> list.sumOf { it.text.count { c -> !c.isWhitespace() } } }
            val domSize = (sizeWeights.maxByOrNull { it.value }?.key ?: bodyFontSize.roundToInt()).toFloat()

            VisualLine(
                pageNumber = pageNumber,
                y = sorted.map { it.y }.average().toFloat(),
                xStart = sorted.minOf { it.xStart },
                xEnd = sorted.maxOf { it.xEnd },
                maxFontSize = sorted.maxOf { it.fontSize },
                dominantFontSize = domSize,
                segments = sorted,
                plainText = plain,
                isMostlyBold = boldChars.toFloat() / totalChars >= 0.7f,
                isMostlyItalic = italicChars.toFloat() / totalChars >= 0.7f,
                isAllMonospace = monoChars.toFloat() / totalChars >= 0.85f,
                cells = cells
            )
        }
    }

    private fun joinSegmentsPlainText(segments: List<PdfStyledSegment>, bodyFontSize: Float): String {
        val sb = StringBuilder()
        var prevEnd = Float.NaN
        for (seg in segments) {
            if (!prevEnd.isNaN()) {
                val gap = seg.xStart - prevEnd
                if (gap > maxOf(seg.fontSize, bodyFontSize) * 0.25f &&
                    !sb.endsWith(" ") &&
                    !seg.text.startsWith(" ")
                ) {
                    sb.append(" ")
                }
            }
            sb.append(seg.text)
            prevEnd = seg.xEnd
        }
        return sb.toString()
    }

    private fun splitRowIntoCells(segments: List<PdfStyledSegment>, bodyFontSize: Float): List<List<PdfStyledSegment>> {
        if (segments.isEmpty()) return emptyList()
        val cells = mutableListOf<MutableList<PdfStyledSegment>>()
        var current = mutableListOf<PdfStyledSegment>()
        var prevEnd = Float.NaN

        for (seg in segments) {
            if (seg.text.isBlank()) continue
            if (!prevEnd.isNaN()) {
                val gap = seg.xStart - prevEnd
                val colThreshold = maxOf(bodyFontSize, seg.fontSize) * 2.4f
                if (gap >= colThreshold && current.isNotEmpty()) {
                    cells.add(current)
                    current = mutableListOf()
                }
            }
            current.add(seg)
            prevEnd = seg.xEnd
        }
        if (current.isNotEmpty()) cells.add(current)
        return cells
    }

    private fun isTableBlockStart(lines: List<VisualLine>, idx: Int, bodyFontSize: Float): Boolean {
        val curr = lines[idx]
        if (curr.cells.size < 2 || curr.maxFontSize > bodyFontSize * 1.4f) return false
        val next = lines.getOrNull(idx + 1) ?: return false
        return next.cells.size >= 2 && abs(next.cells.size - curr.cells.size) <= 1
    }

    private fun appendMarkdownTable(out: StringBuilder, rows: List<VisualLine>) {
        if (rows.isEmpty()) return
        val colCount = rows.maxOf { it.cells.size }
        if (out.isNotEmpty() && !out.endsWith("\n\n")) {
            if (!out.endsWith("\n")) out.append("\n")
            out.append("\n")
        }
        val header = (0 until colCount).map { c ->
            val segs = rows.first().cells.getOrNull(c).orEmpty()
            formatSegmentsToInlineMarkdown(segs).replace("|", "&#124;").trim()
        }
        out.append("| ").append(header.joinToString(" | ")).append(" |\n")
        out.append("| ").append(header.joinToString(" | ") { "---" }).append(" |\n")
        for (row in rows.drop(1)) {
            val cells = (0 until colCount).map { c ->
                val segs = row.cells.getOrNull(c).orEmpty()
                formatSegmentsToInlineMarkdown(segs).replace("|", "&#124;").trim()
            }
            out.append("| ").append(cells.joinToString(" | ")).append(" |\n")
        }
        out.append("\n")
    }

    private fun classifyHeadingLevel(line: VisualLine, bodyFontSize: Float): Int? {
        val text = line.plainText.trim()
        if (text.length > 140) return null
        if (Regex("^[•◦▪▫‣☐☑□✓✔*-]\\s+").containsMatchIn(text)) return null
        if (Regex("^\\d+[.)]\\s+").containsMatchIn(text) && text.length > 60) return null

        val ratio = line.dominantFontSize / bodyFontSize.coerceAtLeast(6f)
        return when {
            ratio >= 1.72f -> 1
            ratio >= 1.42f -> 2
            ratio >= 1.24f -> 3
            ratio >= 1.11f -> 4
            line.isMostlyBold && ratio >= 0.98f && text.length <= 80 && !text.endsWith(".") && !text.endsWith(",") && !text.endsWith(";") -> 5
            else -> null
        }
    }

    private fun isWrappedContinuation(prev: VisualLine, next: VisualLine, bodyFontSize: Float): Boolean {
        if (prev.pageNumber != next.pageNumber) return false
        val dy = next.y - prev.y
        if (dy < 0f || dy > bodyFontSize * 1.65f) return false
        if (next.isAllMonospace || next.cells.size >= 2) return false
        if (classifyHeadingLevel(next, bodyFontSize) != null) return false

        val nextTrim = next.plainText.trim()
        if (nextTrim.startsWith("•") || nextTrim.startsWith("- ") || nextTrim.startsWith("* ") ||
            nextTrim.startsWith("☐") || nextTrim.startsWith("☑") ||
            Regex("^\\d{1,3}[.)]\\s+").containsMatchIn(nextTrim) ||
            Regex("^(INFO|TIP|WARNING|IMPORTANT|NOTA|DICA|AVISO)\\s*:", RegexOption.IGNORE_CASE).containsMatchIn(nextTrim)
        ) {
            return false
        }

        val prevTrim = prev.plainText.trim()
        val endsSentence = prevTrim.endsWith(".") || prevTrim.endsWith("!") || prevTrim.endsWith("?") || prevTrim.endsWith(":")
        // If line is much shorter than typical wrap width AND ends with sentence punctuation, treat as new paragraph
        if (endsSentence && dy > bodyFontSize * 1.35f) return false
        return !endsSentence || dy <= bodyFontSize * 1.3f
    }

    private fun stripRunningPageArtifacts(lines: List<VisualLine>, totalPages: Int): List<VisualLine> {
        if (lines.size <= 2) return lines
        val explicitPageFooterPattern = Regex(
            "^(?:p[aá]g(?:ina|\\.)?\\s+\\d{1,4}(?:\\s*(?:/|de|of)\\s*\\d{1,4})?|page\\s+\\d{1,4}(?:\\s*(?:/|of)\\s*\\d{1,4})?|[-–—]\\s*\\d{1,4}\\s*[-–—]|\\d{1,4}\\s*/\\s*\\d{1,4})$",
            RegexOption.IGNORE_CASE
        )
        val barePageNumPattern = Regex("^\\d{1,4}\\.?$")
        return lines.filterIndexed { index, line ->
            val atEdge = index == 0 || index == lines.lastIndex
            if (!atEdge) return@filterIndexed true
            val trimmed = line.plainText.trim()
            val isExplicitMarker = explicitPageFooterPattern.matches(trimmed)
            val isBareNumberOnMultiPage = totalPages > 1 && barePageNumPattern.matches(trimmed)
            !(isExplicitMarker || isBareNumberOnMultiPage)
        }
    }

    private fun formatSegmentsToInlineMarkdown(
        segments: List<PdfStyledSegment>,
        stripBold: Boolean = false,
        stripItalic: Boolean = false
    ): String {
        if (segments.isEmpty()) return ""
        val out = StringBuilder()
        var prevEnd = Float.NaN

        // Coalesce touching/space-separated segments with matching style
        data class Merged(val text: String, val bold: Boolean, val italic: Boolean, val mono: Boolean)
        val merged = mutableListOf<Merged>()

        for (seg in segments) {
            val b = seg.isBold && !stripBold
            val it = seg.isItalic && !stripItalic
            val mo = seg.isMonospace

            val needsSpace = !prevEnd.isNaN() &&
                (seg.xStart - prevEnd) > seg.fontSize * 0.25f &&
                !seg.text.startsWith(" ") &&
                merged.lastOrNull()?.text?.endsWith(" ") != true
            val segText = if (needsSpace) " ${seg.text}" else seg.text
            prevEnd = seg.xEnd

            val prev = merged.lastOrNull()
            if (prev != null && prev.bold == b && prev.italic == it && prev.mono == mo) {
                merged[merged.lastIndex] = prev.copy(text = prev.text + segText)
            } else {
                merged.add(Merged(segText, b, it, mo))
            }
        }

        for (m in merged) {
            val raw = m.text
            val leading = raw.takeWhile { it.isWhitespace() }
            val trailing = raw.drop(leading.length).takeLastWhile { it.isWhitespace() }
            val core = raw.substring(leading.length, raw.length - trailing.length)
            if (core.isEmpty()) {
                out.append(raw)
                continue
            }
            val styled = when {
                m.mono -> "`${core.replace("`", "'")}`"
                m.bold && m.italic -> "***$core***"
                m.bold -> "**$core**"
                m.italic -> "*$core*"
                else -> core
            }
            out.append(leading).append(styled).append(trailing)
        }
        return out.toString()
    }

    private fun stripPrefixFromSegments(segments: List<PdfStyledSegment>, regex: Regex): List<PdfStyledSegment> {
        val full = segments.joinToString("") { it.text }
        val match = regex.find(full) ?: return segments
        var toSkip = match.range.last + 1
        val out = mutableListOf<PdfStyledSegment>()
        for (seg in segments) {
            if (toSkip <= 0) {
                out.add(seg)
            } else if (seg.text.length <= toSkip) {
                toSkip -= seg.text.length
            } else {
                out.add(seg.copy(text = seg.text.substring(toSkip)))
                toSkip = 0
            }
        }
        return out
    }

    private fun resolveFontSize(tp: TextPosition): Float {
        val pt = tp.fontSizeInPt
        val ys = tp.yScale
        return when {
            pt in 4f..120f && ys > pt * 1.15f && ys <= 120f -> ys
            pt in 4f..120f -> pt
            ys in 4f..120f -> ys
            else -> 11f
        }
    }

    private fun detectBold(tp: TextPosition): Boolean {
        val name = tp.font?.name.orEmpty().lowercase()
        val desc = tp.font?.fontDescriptor
        val weight = desc?.fontWeight ?: 0f
        return desc?.isForceBold == true ||
            weight >= 600f ||
            name.contains("bold") ||
            name.contains("black") ||
            name.contains("heavy") ||
            name.contains("semibold") ||
            name.contains("demi") ||
            name.contains("-bd")
    }

    private fun detectItalic(tp: TextPosition): Boolean {
        val name = tp.font?.name.orEmpty().lowercase()
        val desc = tp.font?.fontDescriptor
        return (desc?.italicAngle ?: 0f) != 0f ||
            name.contains("italic") ||
            name.contains("oblique") ||
            name.contains("slant") ||
            name.contains("-it")
    }

    private fun detectMonospace(tp: TextPosition): Boolean {
        val name = tp.font?.name.orEmpty().lowercase()
        val desc = tp.font?.fontDescriptor
        return desc?.isFixedPitch == true ||
            name.contains("courier") ||
            name.contains("consolas") ||
            name.contains("mono") ||
            name.contains("menlo") ||
            name.contains("fira") ||
            name.contains("jetbrains")
    }
}
