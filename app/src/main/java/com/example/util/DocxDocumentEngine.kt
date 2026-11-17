package com.example.util

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

data class DocxRun(
    val text: String,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val isStrikethrough: Boolean = false,
    val isHighlight: Boolean = false,
    val isCode: Boolean = false,
    val linkUrl: String? = null,
    val colorHex: String? = null
)

sealed interface DocxBlock {
    data class Heading(val level: Int, val runs: List<DocxRun>) : DocxBlock
    data class Paragraph(val runs: List<DocxRun>, val alignment: String = "left") : DocxBlock
    data class ListItem(
        val ordered: Boolean,
        val indexNumber: Int,
        val indentLevel: Int,
        val runs: List<DocxRun>
    ) : DocxBlock
    data class ChecklistItem(val checked: Boolean, val runs: List<DocxRun>) : DocxBlock
    data class Quote(val runs: List<DocxRun>) : DocxBlock
    data class Callout(val type: String, val runs: List<DocxRun>) : DocxBlock
    data class Code(val code: String, val language: String = "") : DocxBlock
    data class Math(val formula: String) : DocxBlock
    data class Table(
        val headers: List<List<DocxRun>>,
        val rows: List<List<List<DocxRun>>>
    ) : DocxBlock
    data object Divider : DocxBlock
    data object PageBreak : DocxBlock
    data class Image(
        val mediaPath: String,
        val alt: String = "",
        val widthPercent: Int = 100,
        val alignment: String = "center",
        val sideText: String = "",
        val bytes: ByteArray? = null
    ) : DocxBlock
}

data class DocxParsedDocument(
    val blocks: List<DocxBlock>
)

sealed interface DocxConversionResult {
    data class Success(val markdown: String, val parsed: DocxParsedDocument) : DocxConversionResult
    data class Error(val message: String) : DocxConversionResult
}

/**
 * Pure Kotlin OpenXML (.docx) reader and Citra Markdown converter.
 * Supports paragraphs, headings (H1-H5), bold/italic/underline/strike/highlight/code combinations,
 * hyperlinks, bullet/ordered lists, checklists, quotes, callouts, tables (including side-by-side images),
 * code blocks, math formulas, dividers, page breaks, and embedded images.
 */
object DocxDocumentEngine {

    private const val MAX_MEDIA_BYTES = 10 * 1024 * 1024 // 10 MB safety limit per embedded image

    private data class DocxRelationship(
        val id: String,
        val type: String,
        val target: String,
        val isExternal: Boolean
    )

    private data class DocxStyleDef(
        val styleId: String,
        val styleType: String = "paragraph",
        val name: String = "",
        val basedOn: String? = null,
        val outlineLevel: Int? = null,
        val fontSizeHalfPt: Int? = null,
        val isBold: Boolean? = null,
        val isItalic: Boolean? = null,
        val isUnderline: Boolean? = null,
        val isStrike: Boolean? = null,
        val isMonospace: Boolean = false
    )

    private data class NumberingLevelRule(
        val ordered: Boolean,
        val start: Int = 1
    )

    private class DocxNumberingCatalog(
        private val rulesByNumAndLevel: Map<String, Map<Int, NumberingLevelRule>>
    ) {
        fun isOrdered(numId: String, ilvl: Int): Boolean {
            val levels = rulesByNumAndLevel[numId] ?: return false
            val rule = levels[ilvl] ?: levels[0] ?: levels.values.firstOrNull()
            return rule?.ordered ?: false
        }

        fun startAt(numId: String, ilvl: Int): Int {
            val levels = rulesByNumAndLevel[numId] ?: return 1
            val rule = levels[ilvl] ?: levels[0]
            return (rule?.start ?: 1).coerceAtLeast(1)
        }
    }

    private data class CellContent(
        val blocks: List<DocxBlock>
    )

    fun parseDocx(inputStream: InputStream): DocxParsedDocument {
        var documentXml: ByteArray? = null
        var relsXml: ByteArray? = null
        var stylesXml: ByteArray? = null
        var numberingXml: ByteArray? = null
        val mediaMap = mutableMapOf<String, ByteArray>()

        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val name = entry.name.replace('\\', '/').trimStart('/')
                    val lower = name.lowercase()
                    when {
                        lower == "word/document.xml" -> documentXml = zis.readBytes()
                        lower == "word/_rels/document.xml.rels" -> relsXml = zis.readBytes()
                        lower == "word/styles.xml" -> stylesXml = zis.readBytes()
                        lower == "word/numbering.xml" -> numberingXml = zis.readBytes()
                        lower.startsWith("word/media/") || lower.startsWith("media/") || isImageZipPath(lower) -> {
                            val bytes = zis.readBytes()
                            if (bytes.isNotEmpty() && bytes.size <= MAX_MEDIA_BYTES) {
                                mediaMap[name] = bytes
                                mediaMap[lower] = bytes
                                mediaMap[name.removePrefix("word/")] = bytes
                                mediaMap[lower.removePrefix("word/")] = bytes
                                val fileNameOnly = lower.substringAfterLast('/')
                                if (fileNameOnly.isNotEmpty() && !mediaMap.containsKey("filename:$fileNameOnly")) {
                                    mediaMap["filename:$fileNameOnly"] = bytes
                                }
                            }
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val docBytes = documentXml ?: throw IllegalArgumentException("Arquivo .docx inválido: word/document.xml ausente.")
        val rels = relsXml?.let { parseRelationships(it) } ?: emptyMap()
        val styles = stylesXml?.let { parseStyles(it) } ?: emptyMap()
        val numbering = numberingXml?.let { parseNumbering(it) } ?: DocxNumberingCatalog(emptyMap())

        val rawBlocks = parseDocumentXml(
            xmlBytes = docBytes,
            rels = rels,
            styles = styles,
            numbering = numbering,
            mediaMap = mediaMap
        )

        val cleanedBlocks = postProcessBlocks(rawBlocks)
        return DocxParsedDocument(blocks = cleanedBlocks)
    }

    private fun isImageZipPath(lowerPath: String): Boolean {
        val ext = lowerPath.substringAfterLast('.', "")
        return ext in setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "tif", "tiff")
    }

    fun convertToMarkdown(
        inputStream: InputStream,
        saveImage: ((ByteArray, String) -> String?)? = null
    ): DocxConversionResult {
        return try {
            val parsed = parseDocx(inputStream)
            val md = toCitraMarkdown(parsed, saveImage)
            DocxConversionResult.Success(markdown = md, parsed = parsed)
        } catch (e: Exception) {
            DocxConversionResult.Error(e.message ?: "Não foi possível ler este arquivo .docx.")
        }
    }

    fun toCitraMarkdown(
        doc: DocxParsedDocument,
        saveImage: ((ByteArray, String) -> String?)? = null
    ): String {
        val sb = StringBuilder()
        val blocks = doc.blocks

        var orderedCounter = 0

        for (idx in blocks.indices) {
            val block = blocks[idx]
            if (block !is DocxBlock.ListItem || !block.ordered) {
                orderedCounter = 0
            }

            when (block) {
                is DocxBlock.Heading -> {
                    val level = block.level.coerceIn(1, 5)
                    val text = formatRunsToMarkdown(block.runs, insideHeading = true).trim()
                    if (text.isNotEmpty()) {
                        if (sb.isNotEmpty() && !sb.endsWith("\n\n")) {
                            if (!sb.endsWith("\n")) sb.append("\n")
                            sb.append("\n")
                        }
                        sb.append("#".repeat(level)).append(" ").append(text).append("\n")
                    }
                }

                is DocxBlock.Paragraph -> {
                    val text = formatRunsToMarkdown(block.runs).trimEnd()
                    if (text.isBlank()) {
                        if (sb.isNotEmpty() && !sb.endsWith("\n\n")) {
                            sb.append("\n")
                        }
                    } else {
                        sb.append(text).append("\n")
                    }
                }

                is DocxBlock.ListItem -> {
                    val indent = "  ".repeat(block.indentLevel.coerceIn(0, 4))
                    val text = formatRunsToMarkdown(block.runs).trim()
                    if (block.ordered) {
                        orderedCounter++
                        val num = if (block.indexNumber > 0) block.indexNumber else orderedCounter
                        sb.append(indent).append("$num. ").append(text).append("\n")
                    } else {
                        sb.append(indent).append("- ").append(text).append("\n")
                    }
                }

                is DocxBlock.ChecklistItem -> {
                    val mark = if (block.checked) "x" else " "
                    val text = formatRunsToMarkdown(block.runs, stripStrikethroughIfChecked = block.checked).trim()
                    sb.append("- [$mark] ").append(text).append("\n")
                }

                is DocxBlock.Quote -> {
                    val text = formatRunsToMarkdown(block.runs).trim()
                    if (text.isNotEmpty()) {
                        text.lines().forEach { line ->
                            sb.append("> ").append(line.trim()).append("\n")
                        }
                    }
                }

                is DocxBlock.Callout -> {
                    val typeTag = when (block.type.lowercase()) {
                        "tip" -> "TIP"
                        "warning" -> "WARNING"
                        "important", "danger" -> "IMPORTANT"
                        else -> "INFO"
                    }
                    val text = formatRunsToMarkdown(block.runs).trim()
                    if (text.isNotEmpty()) {
                        sb.append("> [!").append(typeTag).append("] ").append(text).append("\n")
                    }
                }

                is DocxBlock.Code -> {
                    if (sb.isNotEmpty() && !sb.endsWith("\n\n")) {
                        if (!sb.endsWith("\n")) sb.append("\n")
                    }
                    sb.append("```").append(block.language.trim()).append("\n")
                    sb.append(block.code.trimEnd('\n')).append("\n")
                    sb.append("```\n")
                }

                is DocxBlock.Math -> {
                    val formula = block.formula.trim()
                    if (formula.isNotEmpty()) {
                        sb.append("$$ ").append(formula).append(" $$\n")
                    }
                }

                is DocxBlock.Table -> {
                    if (block.headers.isNotEmpty()) {
                        if (sb.isNotEmpty() && !sb.endsWith("\n\n")) {
                            if (!sb.endsWith("\n")) sb.append("\n")
                            sb.append("\n")
                        }
                        val headerCells = block.headers.map { runs ->
                            formatRunsToMarkdown(runs)
                                .replace("\n", "<br>")
                                .replace("|", "&#124;")
                                .trim()
                        }
                        sb.append("| ").append(headerCells.joinToString(" | ")).append(" |\n")
                        sb.append("| ").append(headerCells.joinToString(" | ") { "---" }).append(" |\n")
                        for (row in block.rows) {
                            val rowCells = headerCells.indices.map { colIdx ->
                                val cellRuns = row.getOrNull(colIdx).orEmpty()
                                formatRunsToMarkdown(cellRuns)
                                    .replace("\n", "<br>")
                                    .replace("|", "&#124;")
                                    .trim()
                            }
                            sb.append("| ").append(rowCells.joinToString(" | ")).append(" |\n")
                        }
                    }
                }

                is DocxBlock.Divider -> {
                    sb.append("---\n")
                }

                is DocxBlock.PageBreak -> {
                    sb.append("<!-- pagebreak -->\n")
                }

                is DocxBlock.Image -> {
                    val ext = block.mediaPath.substringAfterLast('.', "jpg")
                        .lowercase()
                        .takeIf { it in setOf("jpg", "jpeg", "png", "webp", "gif") } ?: "jpg"
                    val persistedPath = if (block.bytes != null && saveImage != null) {
                        saveImage(block.bytes, ext)
                    } else null

                    val targetUrl = persistedPath ?: block.mediaPath
                    if (persistedPath != null) {
                        val sideClean = block.sideText
                            .replace("\n", "<br>")
                            .replace("|", "&#124;")
                            .trim()
                        val sidePart = if (sideClean.isNotEmpty()) "|side:$sideClean" else ""
                        val altPart = if (block.alt.isNotBlank()) "${block.alt.replace("|", "")}|" else ""
                        sb.append("![$altPart${block.widthPercent.coerceIn(25, 100)}%|${block.alignment}$sidePart]($targetUrl)\n")
                    } else {
                        val desc = block.alt.ifBlank { block.mediaPath.substringAfterLast('/') }
                        sb.append("*[Imagem: $desc]*\n")
                        if (block.sideText.isNotBlank()) {
                            sb.append(block.sideText.trim()).append("\n")
                        }
                    }
                }
            }
        }

        return sb.toString()
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    /**
     * Converts styled DocxRuns into Citra Markdown inline syntax, coalescing identical adjacent styles
     * and keeping whitespace outside delimiter markers so InlineMarkdownHelper always renders combinations cleanly.
     */
    fun formatRunsToMarkdown(
        runs: List<DocxRun>,
        insideHeading: Boolean = false,
        stripStrikethroughIfChecked: Boolean = false
    ): String {
        if (runs.isEmpty()) return ""

        val coalesced = mutableListOf<DocxRun>()
        for (run in runs) {
            if (run.text.isEmpty()) continue
            val normalized = if (stripStrikethroughIfChecked && run.isStrikethrough) {
                run.copy(isStrikethrough = false)
            } else {
                run
            }
            val prev = coalesced.lastOrNull()
            if (prev != null &&
                prev.isBold == normalized.isBold &&
                prev.isItalic == normalized.isItalic &&
                prev.isUnderline == normalized.isUnderline &&
                prev.isStrikethrough == normalized.isStrikethrough &&
                prev.isHighlight == normalized.isHighlight &&
                prev.isCode == normalized.isCode &&
                prev.linkUrl == normalized.linkUrl
            ) {
                coalesced[coalesced.lastIndex] = prev.copy(text = prev.text + normalized.text)
            } else {
                coalesced.add(normalized)
            }
        }

        val out = StringBuilder()
        for (run in coalesced) {
            val raw = run.text
            if (raw.isEmpty()) continue

            val leadingWs = raw.takeWhile { it == ' ' || it == '\t' }
            val trailingWs = raw.drop(leadingWs.length).takeLastWhile { it == ' ' || it == '\t' }
            val core = raw.substring(leadingWs.length, raw.length - trailingWs.length)

            if (core.isEmpty()) {
                out.append(raw)
                continue
            }

            var formatted = if (run.isCode) {
                val cleanCode = core.replace("`", "'")
                "`$cleanCode`"
            } else {
                var inner = core
                if (run.isUnderline) inner = "<u>$inner</u>"
                if (run.isStrikethrough) inner = "~~$inner~~"
                if (run.isHighlight) inner = "==$inner=="
                val applyBold = run.isBold && !insideHeading
                when {
                    applyBold && run.isItalic -> inner = "***$inner***"
                    applyBold -> inner = "**$inner**"
                    run.isItalic -> inner = "*$inner*"
                }
                inner
            }

            if (!run.linkUrl.isNullOrBlank()) {
                val safeUrl = run.linkUrl.trim()
                if (formatted == safeUrl || core == safeUrl) {
                    formatted = safeUrl
                } else {
                    formatted = "[$formatted]($safeUrl)"
                }
            }

            out.append(leadingWs).append(formatted).append(trailingWs)
        }
        return out.toString()
    }

    private fun newParser(bytes: ByteArray): XmlPullParser {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), "UTF-8")
        return parser
    }

    private fun attr(parser: XmlPullParser, localName: String): String? {
        for (i in 0 until parser.attributeCount) {
            val name = parser.getAttributeName(i)
            val clean = name.substringAfter(':')
            if (clean == localName || name == localName) {
                return parser.getAttributeValue(i)
            }
        }
        return null
    }

    private fun localTag(rawName: String?): String = rawName?.substringAfter(':') ?: ""

    private fun parseRelationships(xmlBytes: ByteArray): Map<String, DocxRelationship> {
        val map = mutableMapOf<String, DocxRelationship>()
        val parser = newParser(xmlBytes)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && localTag(parser.name) == "Relationship") {
                val id = attr(parser, "Id").orEmpty()
                val type = attr(parser, "Type").orEmpty()
                val target = attr(parser, "Target").orEmpty()
                val mode = attr(parser, "TargetMode").orEmpty()
                if (id.isNotBlank() && target.isNotBlank()) {
                    map[id] = DocxRelationship(
                        id = id,
                        type = type,
                        target = target,
                        isExternal = mode.equals("External", ignoreCase = true)
                    )
                }
            }
            event = parser.next()
        }
        return map
    }

    private fun parseStyles(xmlBytes: ByteArray): Map<String, DocxStyleDef> {
        val rawMap = mutableMapOf<String, DocxStyleDef>()
        val parser = newParser(xmlBytes)
        var event = parser.eventType

        var currentId: String? = null
        var currentType = "paragraph"
        var currentName = ""
        var currentBasedOn: String? = null
        var currentOutline: Int? = null
        var currentSize: Int? = null
        var currentBold: Boolean? = null
        var currentItalic: Boolean? = null
        var currentUnderline: Boolean? = null
        var currentStrike: Boolean? = null
        var currentMono = false
        var inRPr = false
        var inPPr = false

        while (event != XmlPullParser.END_DOCUMENT) {
            val tag = localTag(parser.name)
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (tag) {
                        "style" -> {
                            currentId = attr(parser, "styleId")
                            currentType = attr(parser, "type").orEmpty().lowercase().ifBlank { "paragraph" }
                            currentName = ""
                            currentBasedOn = null
                            currentOutline = null
                            currentSize = null
                            currentBold = null
                            currentItalic = null
                            currentUnderline = null
                            currentStrike = null
                            currentMono = false
                        }
                        "name" -> if (currentId != null && !inRPr && !inPPr) {
                            currentName = attr(parser, "val").orEmpty()
                        }
                        "basedOn" -> if (currentId != null) {
                            currentBasedOn = attr(parser, "val")
                        }
                        "pPr" -> inPPr = true
                        "rPr" -> inRPr = true
                        "outlineLvl" -> if (inPPr) {
                            currentOutline = attr(parser, "val")?.toIntOrNull()
                        }
                        "sz", "szCs" -> if (inRPr && currentSize == null) {
                            currentSize = attr(parser, "val")?.toIntOrNull()
                        }
                        "b", "bCs" -> if (inRPr) {
                            val v = attr(parser, "val")
                            currentBold = v == null || (!v.equals("0") && !v.equals("false", true))
                        }
                        "i", "iCs" -> if (inRPr) {
                            val v = attr(parser, "val")
                            currentItalic = v == null || (!v.equals("0") && !v.equals("false", true))
                        }
                        "u" -> if (inRPr) {
                            val v = attr(parser, "val")
                            currentUnderline = v == null || !v.equals("none", true)
                        }
                        "strike", "dstrike" -> if (inRPr) {
                            val v = attr(parser, "val")
                            currentStrike = v == null || (!v.equals("0") && !v.equals("false", true))
                        }
                        "rFonts" -> if (inRPr) {
                            val ascii = attr(parser, "ascii").orEmpty()
                            val hAnsi = attr(parser, "hAnsi").orEmpty()
                            val cs = attr(parser, "cs").orEmpty()
                            if (isMonospaceFontName(ascii) || isMonospaceFontName(hAnsi) || isMonospaceFontName(cs)) {
                                currentMono = true
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (tag) {
                        "pPr" -> inPPr = false
                        "rPr" -> inRPr = false
                        "style" -> {
                            val id = currentId
                            if (!id.isNullOrBlank()) {
                                rawMap[id] = DocxStyleDef(
                                    styleId = id,
                                    styleType = currentType,
                                    name = currentName,
                                    basedOn = currentBasedOn,
                                    outlineLevel = currentOutline,
                                    fontSizeHalfPt = currentSize,
                                    isBold = currentBold,
                                    isItalic = currentItalic,
                                    isUnderline = currentUnderline,
                                    isStrike = currentStrike,
                                    isMonospace = currentMono
                                )
                            }
                            currentId = null
                        }
                    }
                }
            }
            event = parser.next()
        }

        // Resolve inheritance chains safely
        return rawMap.mapValues { (_, def) ->
            var outline = def.outlineLevel
            var size = def.fontSizeHalfPt
            var bold = def.isBold
            var italic = def.isItalic
            var underline = def.isUnderline
            var strike = def.isStrike
            var mono = def.isMonospace
            var parentId = def.basedOn
            var hops = 0
            while (parentId != null && hops < 8) {
                val parent = rawMap[parentId] ?: break
                if (outline == null) outline = parent.outlineLevel
                if (size == null) size = parent.fontSizeHalfPt
                if (bold == null) bold = parent.isBold
                if (italic == null) italic = parent.isItalic
                if (underline == null) underline = parent.isUnderline
                if (strike == null) strike = parent.isStrike
                if (!mono) mono = parent.isMonospace
                parentId = parent.basedOn
                hops++
            }
            def.copy(
                outlineLevel = outline,
                fontSizeHalfPt = size,
                isBold = bold,
                isItalic = italic,
                isUnderline = underline,
                isStrike = strike,
                isMonospace = mono
            )
        }
    }

    private fun parseNumbering(xmlBytes: ByteArray): DocxNumberingCatalog {
        val abstractLevels = mutableMapOf<String, MutableMap<Int, NumberingLevelRule>>()
        val numToAbstract = mutableMapOf<String, String>()
        val numOverrides = mutableMapOf<String, MutableMap<Int, Int>>()

        val parser = newParser(xmlBytes)
        var event = parser.eventType
        var currentAbstractId: String? = null
        var currentAbstractIlvl: Int? = null
        var currentLvlOrdered: Boolean? = null
        var currentLvlStart = 1

        var currentNumId: String? = null
        var currentOverrideIlvl: Int? = null

        while (event != XmlPullParser.END_DOCUMENT) {
            val tag = localTag(parser.name)
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (tag) {
                        "abstractNum" -> {
                            currentAbstractId = attr(parser, "abstractNumId")
                        }
                        "lvl" -> if (currentAbstractId != null) {
                            currentAbstractIlvl = attr(parser, "ilvl")?.toIntOrNull() ?: 0
                            currentLvlOrdered = null
                            currentLvlStart = 1
                        }
                        "start" -> if (currentAbstractId != null && currentAbstractIlvl != null) {
                            currentLvlStart = attr(parser, "val")?.toIntOrNull() ?: 1
                        }
                        "numFmt" -> if (currentAbstractId != null && currentAbstractIlvl != null) {
                            val fmt = attr(parser, "val").orEmpty().lowercase()
                            currentLvlOrdered = fmt !in setOf("bullet", "none", "")
                        }
                        "num" -> {
                            currentNumId = attr(parser, "numId")
                        }
                        "abstractNumId" -> {
                            val absId = attr(parser, "val")
                            if (currentNumId != null && absId != null) {
                                numToAbstract[currentNumId] = absId
                            }
                        }
                        "lvlOverride" -> if (currentNumId != null) {
                            currentOverrideIlvl = attr(parser, "ilvl")?.toIntOrNull()
                        }
                        "startOverride" -> {
                            val nId = currentNumId
                            val oLvl = currentOverrideIlvl
                            val startVal = attr(parser, "val")?.toIntOrNull()
                            if (nId != null && oLvl != null && startVal != null) {
                                numOverrides.getOrPut(nId) { mutableMapOf() }[oLvl] = startVal
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (tag) {
                        "lvl" -> {
                            val absId = currentAbstractId
                            val ilvl = currentAbstractIlvl
                            if (absId != null && ilvl != null) {
                                val rule = NumberingLevelRule(
                                    ordered = currentLvlOrdered ?: false,
                                    start = currentLvlStart
                                )
                                abstractLevels.getOrPut(absId) { mutableMapOf() }[ilvl] = rule
                            }
                            currentAbstractIlvl = null
                        }
                        "abstractNum" -> currentAbstractId = null
                        "lvlOverride" -> currentOverrideIlvl = null
                        "num" -> currentNumId = null
                    }
                }
            }
            event = parser.next()
        }

        val resolved = mutableMapOf<String, Map<Int, NumberingLevelRule>>()
        for ((numId, absId) in numToAbstract) {
            val baseLevels = abstractLevels[absId] ?: continue
            val overrides = numOverrides[numId]
            if (overrides.isNullOrEmpty()) {
                resolved[numId] = baseLevels
            } else {
                val merged = baseLevels.toMutableMap()
                for ((lvl, startOverride) in overrides) {
                    val existing = merged[lvl] ?: NumberingLevelRule(ordered = true, start = startOverride)
                    merged[lvl] = existing.copy(start = startOverride)
                }
                resolved[numId] = merged
            }
        }
        return DocxNumberingCatalog(resolved)
    }

    private fun parseDocumentXml(
        xmlBytes: ByteArray,
        rels: Map<String, DocxRelationship>,
        styles: Map<String, DocxStyleDef>,
        numbering: DocxNumberingCatalog,
        mediaMap: Map<String, ByteArray>
    ): List<DocxBlock> {
        val parser = newParser(xmlBytes)
        val result = mutableListOf<DocxBlock>()
        val numberingCounters = mutableMapOf<Pair<String, Int>, Int>()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && localTag(parser.name) == "body") {
                event = parser.next()
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.END_TAG && localTag(parser.name) == "body") break
                    if (event == XmlPullParser.START_TAG) {
                        when (localTag(parser.name)) {
                            "p" -> result.addAll(
                                parseParagraphElement(parser, rels, styles, numbering, numberingCounters, mediaMap)
                            )
                            "tbl" -> parseTableElement(parser, rels, styles, numbering, numberingCounters, mediaMap)
                                ?.let { result.add(it) }
                        }
                    }
                    event = parser.next()
                }
                break
            }
            event = parser.next()
        }
        return result
    }

    private fun parseTableElement(
        parser: XmlPullParser,
        rels: Map<String, DocxRelationship>,
        styles: Map<String, DocxStyleDef>,
        numbering: DocxNumberingCatalog,
        numberingCounters: MutableMap<Pair<String, Int>, Int>,
        mediaMap: Map<String, ByteArray>
    ): DocxBlock? {
        val parsedRows = mutableListOf<List<CellContent>>()
        var currentRow: MutableList<CellContent>? = null
        var currentCellBlocks: MutableList<DocxBlock>? = null
        var depth = 1

        while (depth > 0) {
            val event = parser.next()
            if (event == XmlPullParser.END_DOCUMENT) break
            val tag = localTag(parser.name)
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (tag) {
                        "tr" -> if (depth == 1) {
                            currentRow = mutableListOf()
                            depth++
                        } else depth++
                        "tc" -> if (depth == 2) {
                            currentCellBlocks = mutableListOf()
                            depth++
                        } else depth++
                        "p" -> if (depth == 3) {
                            currentCellBlocks?.addAll(
                                parseParagraphElement(parser, rels, styles, numbering, numberingCounters, mediaMap)
                            )
                        } else {
                            depth++
                        }
                        "tbl" -> if (depth >= 3) {
                            val nested = parseTableElement(parser, rels, styles, numbering, numberingCounters, mediaMap)
                            if (nested != null) currentCellBlocks?.add(nested)
                        } else {
                            depth++
                        }
                        else -> depth++
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (tag) {
                        "tc" -> {
                            if (depth == 3) {
                                currentRow?.add(CellContent(currentCellBlocks?.toList().orEmpty()))
                                currentCellBlocks = null
                            }
                            depth--
                        }
                        "tr" -> {
                            if (depth == 2) {
                                currentRow?.let { parsedRows.add(it) }
                                currentRow = null
                            }
                            depth--
                        }
                        else -> depth--
                    }
                }
            }
        }

        if (parsedRows.isEmpty()) return null

        // Detect Citra's side-by-side image+text borderless layout table (1 row, 2 cells, one is an image)
        if (parsedRows.size == 1 && parsedRows[0].size == 2) {
            val left = parsedRows[0][0].blocks
            val right = parsedRows[0][1].blocks
            val leftImg = left.singleOrNull() as? DocxBlock.Image
            val rightImg = right.singleOrNull() as? DocxBlock.Image
            if (leftImg != null && rightImg == null) {
                val side = right.joinToString("\n") { blockPlainOrMarkdownText(it) }.trim()
                return leftImg.copy(alignment = "left", widthPercent = 50, sideText = side)
            }
            if (rightImg != null && leftImg == null) {
                val side = left.joinToString("\n") { blockPlainOrMarkdownText(it) }.trim()
                return rightImg.copy(alignment = "right", widthPercent = 50, sideText = side)
            }
        }

        val colCount = parsedRows.maxOfOrNull { it.size } ?: 0
        if (colCount == 0) return null

        fun cellToRuns(cell: CellContent): List<DocxRun> {
            val combined = mutableListOf<DocxRun>()
            cell.blocks.forEachIndexed { idx, blk ->
                if (idx > 0) combined.add(DocxRun("\n"))
                combined.addAll(blockToRuns(blk))
            }
            return combined
        }

        val headerRuns = (0 until colCount).map { c ->
            parsedRows.first().getOrNull(c)?.let(::cellToRuns) ?: emptyList()
        }
        val bodyRows = parsedRows.drop(1).map { row ->
            (0 until colCount).map { c ->
                row.getOrNull(c)?.let(::cellToRuns) ?: emptyList()
            }
        }
        return DocxBlock.Table(headers = headerRuns, rows = bodyRows)
    }

    private fun blockToRuns(block: DocxBlock): List<DocxRun> = when (block) {
        is DocxBlock.Heading -> block.runs
        is DocxBlock.Paragraph -> block.runs
        is DocxBlock.ListItem -> listOf(DocxRun(if (block.ordered) "${block.indexNumber}. " else "• ")) + block.runs
        is DocxBlock.ChecklistItem -> listOf(DocxRun(if (block.checked) "☑ " else "☐ ")) + block.runs
        is DocxBlock.Quote -> block.runs
        is DocxBlock.Callout -> block.runs
        is DocxBlock.Code -> listOf(DocxRun(block.code, isCode = true))
        is DocxBlock.Math -> listOf(DocxRun(block.formula))
        is DocxBlock.Image -> listOf(DocxRun("[Imagem: ${block.alt.ifBlank { block.mediaPath.substringAfterLast('/') }}]"))
        is DocxBlock.Table -> listOf(DocxRun("[Tabela]"))
        DocxBlock.Divider -> listOf(DocxRun("---"))
        DocxBlock.PageBreak -> emptyList()
    }

    private fun blockPlainOrMarkdownText(block: DocxBlock): String {
        return formatRunsToMarkdown(blockToRuns(block))
    }

    private fun resolveMediaEntry(rawTarget: String, mediaMap: Map<String, ByteArray>): Pair<String, ByteArray?> {
        var cleaned = rawTarget.replace('\\', '/').trim()
        while (cleaned.startsWith("../")) cleaned = cleaned.removePrefix("../")
        while (cleaned.startsWith("./")) cleaned = cleaned.removePrefix("./")
        cleaned = cleaned.trimStart('/')
        val fullWordPath = if (cleaned.startsWith("word/", ignoreCase = true)) cleaned else "word/$cleaned"
        val bytes = mediaMap[fullWordPath]
            ?: mediaMap[fullWordPath.lowercase()]
            ?: mediaMap[cleaned]
            ?: mediaMap[cleaned.lowercase()]
            ?: mediaMap["filename:${cleaned.substringAfterLast('/').lowercase()}"]
        return fullWordPath to bytes
    }

    private fun parseParagraphElement(
        parser: XmlPullParser,
        rels: Map<String, DocxRelationship>,
        styles: Map<String, DocxStyleDef>,
        numbering: DocxNumberingCatalog,
        numberingCounters: MutableMap<Pair<String, Int>, Int>,
        mediaMap: Map<String, ByteArray>
    ): List<DocxBlock> {
        var pStyleId: String? = null
        var alignment = "left"
        var hasLeftBorder = false
        var leftBorderColor: String? = null
        var hasBottomBorder = false
        var shadingFill: String? = null
        var indentLeft = 0
        var numId: String? = null
        var ilvl = 0
        var outlineLevel: Int? = null
        var isMathParagraph = false
        var hasPageBreakBefore = false
        var hasPageBreakAfter = false
        var hasSectionBreak = false
        var sectionBreakIsContinuous = false

        val runs = mutableListOf<DocxRun>()
        val extractedImages = mutableListOf<DocxBlock.Image>()
        val mathTokens = StringBuilder()

        var inPPr = false
        var inPBdr = false
        var inSectPr = false
        var inR = false
        var inRPr = false
        var inMath = false
        var currentLinkUrl: String? = null
        val fieldDisplayStack = mutableListOf<Boolean>()
        var activeFieldLinkUrl: String? = null

        var rStyleId: String? = null
        var rBold: Boolean? = null
        var rItalic: Boolean? = null
        var rStrike: Boolean? = null
        var rUnderline: Boolean? = null
        var rHighlight = false
        var rCode = false
        var rColor: String? = null
        var rFontSize: Int? = null
        val maxFontSizes = mutableListOf<Int>()
        val currentRunText = StringBuilder()
        var currentDrawingWidthPercent = 100
        var currentDrawingAlt: String? = null

        fun flushRun() {
            val txt = currentRunText.toString()
            currentRunText.setLength(0)
            if (txt.isEmpty()) return
            val pStyleDef = pStyleId?.let { styles[it] }
            val rStyleDef = rStyleId?.let { styles[it] }
            val resolvedBold = rBold ?: rStyleDef?.isBold ?: pStyleDef?.isBold ?: false
            val resolvedItalic = rItalic ?: rStyleDef?.isItalic ?: pStyleDef?.isItalic ?: false
            val resolvedUnderline = rUnderline ?: rStyleDef?.isUnderline ?: pStyleDef?.isUnderline ?: false
            val resolvedStrike = rStrike ?: rStyleDef?.isStrike ?: pStyleDef?.isStrike ?: false
            val resolvedCode = rCode || (rStyleDef?.isMonospace == true) || (pStyleDef?.isMonospace == true)
            runs.add(
                DocxRun(
                    text = txt,
                    isBold = resolvedBold,
                    isItalic = resolvedItalic,
                    isUnderline = resolvedUnderline,
                    isStrikethrough = resolvedStrike,
                    isHighlight = rHighlight,
                    isCode = resolvedCode,
                    linkUrl = currentLinkUrl ?: activeFieldLinkUrl,
                    colorHex = rColor
                )
            )
            rFontSize?.let { maxFontSizes.add(it) }
        }

        var depth = 1
        while (depth > 0) {
            val event = parser.next()
            if (event == XmlPullParser.END_DOCUMENT) break
            val tag = localTag(parser.name)

            when (event) {
                XmlPullParser.START_TAG -> {
                    depth++
                    when (tag) {
                        "pPr" -> if (depth == 2) inPPr = true
                        "sectPr" -> if (inPPr) {
                            inSectPr = true
                            hasSectionBreak = true
                        }
                        "type" -> if (inSectPr) {
                            val v = attr(parser, "val").orEmpty().lowercase()
                            if (v == "continuous") sectionBreakIsContinuous = true
                        }
                        "pageBreakBefore" -> if (inPPr) {
                            val v = attr(parser, "val")
                            if (v == null || (!v.equals("0") && !v.equals("false", true))) {
                                hasPageBreakBefore = true
                            }
                        }
                        "pBdr" -> if (inPPr) inPBdr = true
                        "left" -> if (inPBdr) {
                            val v = attr(parser, "val").orEmpty()
                            if (!v.equals("none", true) && !v.equals("nil", true)) {
                                hasLeftBorder = true
                                leftBorderColor = attr(parser, "color")
                            }
                        }
                        "bottom" -> if (inPBdr) {
                            val v = attr(parser, "val").orEmpty()
                            if (!v.equals("none", true) && !v.equals("nil", true)) {
                                hasBottomBorder = true
                            }
                        }
                        "pStyle" -> if (inPPr) pStyleId = attr(parser, "val")
                        "jc" -> if (inPPr && !inSectPr) alignment = attr(parser, "val").orEmpty().lowercase().ifBlank { "left" }
                        "ind" -> if (inPPr) indentLeft = attr(parser, "left")?.toIntOrNull() ?: indentLeft
                        "outlineLvl" -> if (inPPr) outlineLevel = attr(parser, "val")?.toIntOrNull()
                        "numId" -> if (inPPr) numId = attr(parser, "val")
                        "ilvl" -> if (inPPr) ilvl = attr(parser, "val")?.toIntOrNull() ?: 0
                        "shd" -> {
                            val fill = attr(parser, "fill")?.uppercase()
                            if (!fill.isNullOrBlank() && fill != "AUTO" && fill != "FFFFFF") {
                                if (inRPr) {
                                    if (fill != "NONE") rHighlight = true
                                } else if (inPPr) {
                                    shadingFill = fill
                                }
                            }
                        }
                        "hyperlink" -> {
                            val rId = attr(parser, "id")
                            val rel = rId?.let { rels[it] }
                            if (rel != null) {
                                currentLinkUrl = rel.target
                            }
                        }
                        "fldChar" -> {
                            when (attr(parser, "fldCharType")?.lowercase()) {
                                "begin" -> fieldDisplayStack.add(false)
                                "separate" -> if (fieldDisplayStack.isNotEmpty()) {
                                    fieldDisplayStack[fieldDisplayStack.lastIndex] = true
                                }
                                "end" -> {
                                    if (fieldDisplayStack.isNotEmpty()) {
                                        fieldDisplayStack.removeAt(fieldDisplayStack.lastIndex)
                                    }
                                    activeFieldLinkUrl = null
                                }
                            }
                        }
                        "instrText", "delText" -> {
                            val instr = safeNextText(parser)
                            depth--
                            if (tag == "instrText" && instr.contains("HYPERLINK", ignoreCase = true)) {
                                Regex("HYPERLINK\\s+\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(instr)?.let {
                                    activeFieldLinkUrl = it.groupValues[1]
                                }
                            }
                        }
                        "oMathPara", "oMath" -> {
                            inMath = true
                            if (tag == "oMathPara") isMathParagraph = true
                        }
                        "r" -> {
                            if (!inMath) {
                                inR = true
                                rStyleId = null
                                rBold = null
                                rItalic = null
                                rStrike = null
                                rUnderline = null
                                rHighlight = false
                                rCode = false
                                rColor = null
                                rFontSize = null
                                currentRunText.setLength(0)
                            }
                        }
                        "rPr" -> if (inR) inRPr = true
                        "rStyle" -> if (inRPr) rStyleId = attr(parser, "val")
                        "b", "bCs" -> if (inRPr) {
                            val v = attr(parser, "val")
                            rBold = v == null || (!v.equals("0") && !v.equals("false", true))
                        }
                        "i", "iCs" -> if (inRPr) {
                            val v = attr(parser, "val")
                            rItalic = v == null || (!v.equals("0") && !v.equals("false", true))
                        }
                        "strike", "dstrike" -> if (inRPr) {
                            val v = attr(parser, "val")
                            rStrike = v == null || (!v.equals("0") && !v.equals("false", true))
                        }
                        "u" -> if (inRPr) {
                            val v = attr(parser, "val")
                            rUnderline = v == null || !v.equals("none", true)
                        }
                        "highlight" -> if (inRPr) {
                            val v = attr(parser, "val")
                            rHighlight = v == null || !v.equals("none", true)
                        }
                        "color" -> if (inRPr) {
                            rColor = attr(parser, "val")?.takeIf { !it.equals("auto", true) }
                        }
                        "sz", "szCs" -> if (inRPr && rFontSize == null) {
                            rFontSize = attr(parser, "val")?.toIntOrNull()
                        }
                        "rFonts" -> if (inRPr) {
                            val ascii = attr(parser, "ascii").orEmpty()
                            val hAnsi = attr(parser, "hAnsi").orEmpty()
                            val cs = attr(parser, "cs").orEmpty()
                            if (isMonospaceFontName(ascii) || isMonospaceFontName(hAnsi) || isMonospaceFontName(cs)) {
                                rCode = true
                            }
                        }
                        "br" -> if (inR) {
                            val type = attr(parser, "type")
                            if (type.equals("page", ignoreCase = true)) {
                                if (runs.isEmpty() && currentRunText.isEmpty() && extractedImages.isEmpty()) {
                                    hasPageBreakBefore = true
                                } else {
                                    hasPageBreakAfter = true
                                }
                            } else {
                                currentRunText.append("\n")
                            }
                        }
                        "tab" -> if (inR) currentRunText.append("    ")
                        "t" -> {
                            val textContent = safeNextText(parser)
                            depth--
                            val insideHiddenFieldCode = fieldDisplayStack.lastOrNull() == false
                            if (!insideHiddenFieldCode) {
                                if (inMath) {
                                    mathTokens.append(textContent)
                                } else if (inR) {
                                    currentRunText.append(textContent)
                                }
                            }
                        }
                        "docPr" -> {
                            val descr = attr(parser, "descr")?.trim()?.takeIf { it.isNotEmpty() }
                            val title = attr(parser, "title")?.trim()?.takeIf { it.isNotEmpty() }
                            val name = attr(parser, "name")?.trim()?.takeIf { it.isNotEmpty() }
                            currentDrawingAlt = descr ?: title ?: name
                        }
                        "extent" -> {
                            val cx = attr(parser, "cx")?.toLongOrNull()
                            if (cx != null && cx > 0) {
                                val pct = ((cx.toDouble() / 5400000.0) * 100.0).toInt()
                                currentDrawingWidthPercent = when {
                                    pct <= 35 -> 25
                                    pct <= 62 -> 50
                                    pct <= 85 -> 75
                                    else -> 100
                                }
                            }
                        }
                        "blip", "imagedata" -> {
                            val embedId = attr(parser, "embed") ?: attr(parser, "id") ?: attr(parser, "relid")
                            val rel = embedId?.let { rels[it] }
                            if (rel != null) {
                                val (normalizedPath, imgBytes) = resolveMediaEntry(rel.target, mediaMap)
                                extractedImages.add(
                                    DocxBlock.Image(
                                        mediaPath = normalizedPath,
                                        alt = currentDrawingAlt ?: normalizedPath.substringAfterLast('/'),
                                        widthPercent = currentDrawingWidthPercent,
                                        alignment = alignment,
                                        bytes = imgBytes
                                    )
                                )
                                currentDrawingAlt = null
                            }
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    when (tag) {
                        "pBdr" -> inPBdr = false
                        "sectPr" -> inSectPr = false
                        "pPr" -> inPPr = false
                        "rPr" -> inRPr = false
                        "r" -> {
                            if (inR && !inMath) flushRun()
                            inR = false
                        }
                        "hyperlink" -> currentLinkUrl = null
                        "oMath", "oMathPara" -> {
                            if (tag == "oMathPara") inMath = false
                            else if (!isMathParagraph) inMath = false
                        }
                    }
                    depth--
                }
            }
        }

        val blocks = mutableListOf<DocxBlock>()
        if (hasPageBreakBefore) {
            blocks.add(DocxBlock.PageBreak)
        }
        val trailingSectionPageBreak = hasSectionBreak && !sectionBreakIsContinuous
        val emitAfterBreak = hasPageBreakAfter || trailingSectionPageBreak

        val plainText = runs.joinToString("") { it.text }.trim()
        if (runs.isEmpty() && extractedImages.isEmpty() && mathTokens.isBlank()) {
            if (hasBottomBorder) {
                blocks.add(DocxBlock.Divider)
            }
            if (emitAfterBreak && !hasPageBreakBefore) {
                blocks.add(DocxBlock.PageBreak)
            }
            return blocks
        }

        // Math formula block if paragraph is primarily oMath or EFF6FF math shading
        if ((isMathParagraph || (mathTokens.isNotBlank() && plainText.isEmpty())) ||
            (shadingFill == "EFF6FF" && plainText.isNotEmpty())
        ) {
            val formula = mathTokens.toString().ifBlank { plainText }.trim()
            if (formula.isNotEmpty()) {
                blocks.add(DocxBlock.Math(formula))
            }
            if (emitAfterBreak) blocks.add(DocxBlock.PageBreak)
            return blocks
        }

        // Divider if empty paragraph with bottom border
        if (plainText.isEmpty() && extractedImages.isEmpty() && hasBottomBorder) {
            blocks.add(DocxBlock.Divider)
            if (emitAfterBreak) blocks.add(DocxBlock.PageBreak)
            return blocks
        }

        // Code block if dark shaded code background (Citra & VS Code exports)
        if ((shadingFill == "0F172A" || shadingFill == "1E293B") && plainText.isNotEmpty()) {
            val lines = plainText.lines()
            val firstLine = lines.firstOrNull()?.trim().orEmpty()
            val lang = if (lines.size > 1 && firstLine.length in 1..16 && firstLine.all { it.isLetterOrDigit() || it == '_' || it == '+' || it == '#' }) {
                firstLine.lowercase()
            } else ""
            val codeBody = if (lang.isNotEmpty()) lines.drop(1).joinToString("\n") else plainText
            blocks.add(DocxBlock.Code(code = codeBody, language = lang))
            if (emitAfterBreak) blocks.add(DocxBlock.PageBreak)
            return blocks
        }

        // Standalone images
        if (extractedImages.isNotEmpty() && plainText.isEmpty()) {
            blocks.addAll(extractedImages)
            if (emitAfterBreak) blocks.add(DocxBlock.PageBreak)
            return blocks
        }

        val pStyleDef = pStyleId?.let { styles[it] }
        val tocLevel = resolveTocLevel(pStyleId, pStyleDef?.name)
        val headingLevel = if (tocLevel == null) {
            resolveHeadingLevel(pStyleId, pStyleDef, outlineLevel, runs, maxFontSizes)
        } else null

        when {
            isTocHeadingStyle(pStyleId, pStyleDef?.name) && plainText.isNotEmpty() -> {
                blocks.add(DocxBlock.Heading(level = 2, runs = trimRunEdges(runs)))
            }

            tocLevel != null && plainText.isNotEmpty() -> {
                blocks.add(
                    DocxBlock.ListItem(
                        ordered = false,
                        indexNumber = 0,
                        indentLevel = (tocLevel - 1).coerceIn(0, 4),
                        runs = trimRunEdges(runs)
                    )
                )
            }

            headingLevel != null && plainText.isNotEmpty() -> {
                blocks.add(DocxBlock.Heading(level = headingLevel, runs = trimRunEdges(runs)))
            }

            // Callout detection (shaded left-border box, semantic callout tint shading, or explicit TIP:/INFO:/WARNING:/IMPORTANT: prefix)
            (hasLeftBorder && shadingFill != null) ||
                shadingFill in setOf("ECFDF5", "FFFBEB", "FEF2F2", "F0F9FF") ||
                matchesCalloutPrefix(plainText) -> {
                val (calloutType, cleanedRuns) = extractCallout(plainText, runs, shadingFill, leftBorderColor)
                blocks.add(DocxBlock.Callout(type = calloutType, runs = cleanedRuns))
            }

            // Quote detection (left border or Quote style)
            hasLeftBorder || pStyleId?.contains("quote", ignoreCase = true) == true ||
                pStyleDef?.name?.contains("quote", ignoreCase = true) == true ||
                pStyleDef?.name?.contains("citação", ignoreCase = true) == true -> {
                if (plainText.isNotEmpty()) {
                    blocks.add(DocxBlock.Quote(runs = trimRunEdges(runs)))
                }
            }

            // Checklist detection (☐ / ☑ / [ ] / [x])
            plainText.startsWith("☐") || plainText.startsWith("☑") ||
                plainText.startsWith("[ ] ") || plainText.startsWith("[x] ", ignoreCase = true) -> {
                val checked = plainText.startsWith("☑") || plainText.startsWith("[x]", ignoreCase = true)
                val prefixRegex = Regex("^\\s*(?:☐|☑|\\[[xX ]\\])\\s*")
                val cleaned = stripLeadingPrefixFromRuns(runs, prefixRegex)
                blocks.add(DocxBlock.ChecklistItem(checked = checked, runs = cleaned))
            }

            // Explicit Word numbering or bullet symbol
            numId != null || startsWithBulletSymbol(plainText) || startsWithNumberedPrefix(plainText) -> {
                val activeNumId = numId?.takeIf { it != "0" }
                val isOrderedFromNum = activeNumId?.let { numbering.isOrdered(it, ilvl) }
                val orderedMatch = Regex("^\\s*(\\d+)[.)]\\s+").find(plainText)
                val isOrdered = isOrderedFromNum ?: (orderedMatch != null)

                val numIndex = if (isOrdered) {
                    val explicitNum = orderedMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
                    if (explicitNum != null) {
                        explicitNum
                    } else if (activeNumId != null) {
                        val key = activeNumId to ilvl
                        val existing = numberingCounters[key]
                        val nextVal = if (existing == null) numbering.startAt(activeNumId, ilvl) else existing + 1
                        numberingCounters[key] = nextVal
                        // Reset deeper levels under the same numId
                        numberingCounters.keys.filter { it.first == activeNumId && it.second > ilvl }
                            .forEach { numberingCounters.remove(it) }
                        nextVal
                    } else 1
                } else 0

                val cleaned = when {
                    startsWithBulletSymbol(plainText) -> stripLeadingPrefixFromRuns(runs, Regex("^\\s*[•◦▪▫‣–—*-]\\s+"))
                    orderedMatch != null -> stripLeadingPrefixFromRuns(runs, Regex("^\\s*\\d+[.)]\\s+"))
                    else -> trimRunEdges(runs)
                }
                blocks.add(
                    DocxBlock.ListItem(
                        ordered = isOrdered,
                        indexNumber = numIndex,
                        indentLevel = ilvl,
                        runs = cleaned
                    )
                )
            }

            // All-monospace paragraph without shading -> Code block
            runs.isNotEmpty() && plainText.isNotEmpty() && runs.all { it.isCode || it.text.isBlank() } && plainText.length > 12 -> {
                blocks.add(DocxBlock.Code(code = plainText, language = ""))
            }

            else -> {
                if (runs.isNotEmpty()) {
                    blocks.add(DocxBlock.Paragraph(runs = runs, alignment = alignment))
                } else {
                    blocks.add(DocxBlock.Paragraph(runs = emptyList(), alignment = alignment))
                }
            }
        }

        if (extractedImages.isNotEmpty() && plainText.isNotEmpty()) {
            blocks.addAll(extractedImages)
        }
        if (emitAfterBreak) {
            blocks.add(DocxBlock.PageBreak)
        }
        return blocks
    }

    private fun isTocHeadingStyle(styleId: String?, styleName: String?): Boolean {
        return listOfNotNull(styleId, styleName).any { raw ->
            val low = raw.lowercase().trim()
            low == "tocheading" || low == "toc heading" || low == "sumário título"
        }
    }

    private fun resolveTocLevel(styleId: String?, styleName: String?): Int? {
        for (raw in listOfNotNull(styleId, styleName)) {
            val low = raw.lowercase().trim()
            Regex("^(?:toc|sum[aá]rio|[ií]ndice)\\s*([1-9])$").find(low)?.let {
                return it.groupValues[1].toIntOrNull()?.coerceIn(1, 5)
            }
        }
        return null
    }

    private fun safeNextText(parser: XmlPullParser): String {
        val sb = StringBuilder()
        var event = parser.next()
        while (event != XmlPullParser.END_DOCUMENT && event != XmlPullParser.END_TAG) {
            if (event == XmlPullParser.TEXT || event == XmlPullParser.CDSECT) {
                sb.append(parser.text.orEmpty())
            }
            event = parser.next()
        }
        return sb.toString()
    }

    private fun resolveHeadingLevel(
        styleId: String?,
        styleDef: DocxStyleDef?,
        outlineLevel: Int?,
        runs: List<DocxRun>,
        fontSizes: List<Int>
    ): Int? {
        val candidates = listOfNotNull(styleId, styleDef?.name)
        for (raw in candidates) {
            val s = raw.lowercase().trim()
            Regex("(?:heading|titulo|título|t)\\s*([1-6])").find(s)?.let {
                return it.groupValues[1].toInt().coerceIn(1, 5)
            }
            if (s == "title") return 1
            if (s == "subtitle") return 2
        }
        if (outlineLevel != null && outlineLevel in 0..4) {
            return (outlineLevel + 1).coerceIn(1, 5)
        }
        // Heuristic: short single-line all-bold paragraph with enlarged font size
        val maxHalfPt = fontSizes.maxOrNull() ?: styleDef?.fontSizeHalfPt
        val plain = runs.joinToString("") { it.text }.trim()
        val allBold = runs.isNotEmpty() && runs.filter { it.text.isNotBlank() }.all { it.isBold }
        if (maxHalfPt != null && allBold && plain.length in 2..110 && !plain.contains('\n')) {
            return when {
                maxHalfPt >= 44 -> 1
                maxHalfPt >= 36 -> 2
                maxHalfPt >= 30 -> 3
                maxHalfPt >= 26 -> 4
                else -> null
            }
        }
        return null
    }

    private fun matchesCalloutPrefix(text: String): Boolean {
        val upper = text.trimStart().uppercase()
        return upper.startsWith("INFO:") || upper.startsWith("TIP:") ||
            upper.startsWith("WARNING:") || upper.startsWith("IMPORTANT:") ||
            upper.startsWith("DICA:") || upper.startsWith("AVISO:") ||
            upper.startsWith("IMPORTANTE:")
    }

    private fun extractCallout(
        plain: String,
        runs: List<DocxRun>,
        shadingFill: String?,
        leftBorderColor: String?
    ): Pair<String, List<DocxRun>> {
        val upper = plain.trimStart().uppercase()
        val detectedType = when {
            upper.startsWith("TIP:") || upper.startsWith("DICA:") || shadingFill == "ECFDF5" || leftBorderColor.equals("10B981", true) -> "tip"
            upper.startsWith("WARNING:") || upper.startsWith("AVISO:") || shadingFill == "FFFBEB" || leftBorderColor.equals("F59E0B", true) -> "warning"
            upper.startsWith("IMPORTANT:") || upper.startsWith("IMPORTANTE:") || upper.startsWith("DANGER:") || shadingFill == "FEF2F2" || leftBorderColor.equals("EF4444", true) -> "important"
            else -> "info"
        }
        val cleaned = stripLeadingPrefixFromRuns(
            runs,
            Regex("^\\s*(?:INFO|TIP|WARNING|IMPORTANT|DANGER|DICA|AVISO|IMPORTANTE)\\s*:\\s*", RegexOption.IGNORE_CASE)
        )
        return detectedType to cleaned
    }

    private fun startsWithBulletSymbol(text: String): Boolean {
        return Regex("^\\s*[•◦▪▫‣]\\s+").containsMatchIn(text) ||
            Regex("^\\s*[-*]\\s+\\S").containsMatchIn(text)
    }

    private fun startsWithNumberedPrefix(text: String): Boolean {
        return Regex("^\\s*\\d{1,3}[.)]\\s+\\S").containsMatchIn(text)
    }

    private fun stripLeadingPrefixFromRuns(runs: List<DocxRun>, regex: Regex): List<DocxRun> {
        val full = runs.joinToString("") { it.text }
        val match = regex.find(full) ?: return trimRunEdges(runs)
        var toSkip = match.range.last + 1
        val result = mutableListOf<DocxRun>()
        for (run in runs) {
            if (toSkip <= 0) {
                result.add(run)
            } else if (run.text.length <= toSkip) {
                toSkip -= run.text.length
            } else {
                result.add(run.copy(text = run.text.substring(toSkip)))
                toSkip = 0
            }
        }
        return result
    }

    private fun trimRunEdges(runs: List<DocxRun>): List<DocxRun> {
        if (runs.isEmpty()) return emptyList()
        val mutable = runs.toMutableList()
        while (mutable.isNotEmpty()) {
            val trimmedStart = mutable.first().text.trimStart()
            if (trimmedStart.isEmpty()) mutable.removeAt(0)
            else {
                mutable[0] = mutable.first().copy(text = trimmedStart)
                break
            }
        }
        while (mutable.isNotEmpty()) {
            val trimmedEnd = mutable.last().text.trimEnd()
            if (trimmedEnd.isEmpty()) mutable.removeAt(mutable.lastIndex)
            else {
                mutable[mutable.lastIndex] = mutable.last().copy(text = trimmedEnd)
                break
            }
        }
        return mutable
    }

    private fun postProcessBlocks(blocks: List<DocxBlock>): List<DocxBlock> {
        // Coalesce consecutive Code blocks and drop Citra self-export metadata line if present
        val filtered = blocks.filterNot { blk ->
            blk is DocxBlock.Paragraph && blk.runs.joinToString("") { it.text }.let { txt ->
                txt.contains("Exportado pelo Citra em") && txt.contains("[ Categoria:")
            }
        }
        val merged = mutableListOf<DocxBlock>()
        for (blk in filtered) {
            val prev = merged.lastOrNull()
            if (blk is DocxBlock.Code && prev is DocxBlock.Code && prev.language == blk.language) {
                merged[merged.lastIndex] = prev.copy(code = prev.code + "\n" + blk.code)
            } else {
                merged.add(blk)
            }
        }
        return merged
    }

    private fun isMonospaceFontName(name: String): Boolean {
        if (name.isBlank()) return false
        val lower = name.lowercase()
        return lower.contains("courier") ||
            lower.contains("consolas") ||
            lower.contains("mono") ||
            lower.contains("fira code") ||
            lower.contains("jetbrains") ||
            lower.contains("menlo") ||
            lower.contains("lucida console") ||
            lower.contains("source code")
    }
}
