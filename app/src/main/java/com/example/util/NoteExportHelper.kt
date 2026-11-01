package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Base64
import androidx.core.content.FileProvider
import com.example.ui.screens.organization.notes.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max

object NoteExportHelper {

    private fun sanitizeFilename(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._\\- ]"), "_").trim().ifBlank { "Nota" }
    }

    private fun getExportDir(context: Context): File {
        val dir = File(context.cacheDir, "exports")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun loadBitmapFromUrl(context: Context, url: String): Bitmap? {
        if (url.isBlank()) return null
        return try {
            if (url.startsWith("content://") || url.startsWith("file://")) {
                val uri = Uri.parse(url)
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } else {
                val file = File(url)
                if (file.exists()) {
                    BitmapFactory.decodeFile(file.absolutePath)
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun loadAudioBytes(context: Context, url: String): Pair<ByteArray, String>? {
        if (url.isBlank()) return null
        return try {
            val bytes = if (url.startsWith("content://") || url.startsWith("file://")) {
                val uri = Uri.parse(url)
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } else {
                val file = File(url)
                if (file.exists()) file.readBytes() else null
            }
            if (bytes == null || bytes.isEmpty()) return null
            val mime = when {
                url.endsWith(".mp3", ignoreCase = true) -> "audio/mpeg"
                url.endsWith(".wav", ignoreCase = true) -> "audio/wav"
                url.endsWith(".ogg", ignoreCase = true) -> "audio/ogg"
                else -> "audio/mp4"
            }
            Pair(bytes, mime)
        } catch (_: Exception) {
            null
        }
    }

    private fun colorToHex(colorInt: Int): String {
        return String.format("%06X", 0xFFFFFF and colorInt)
    }

    private fun escapeXml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    private fun renderInlineHtml(text: String, themeColorHex: String): String {
        var result = escapeHtml(text)
        result = result.replace(Regex("\\*\\*(.*?)\\*\\*"), "<strong>$1</strong>")
        result = result.replace(Regex("(?<!\\*)\\*(?!\\*)(.*?)(?<!\\*)\\*(?!\\*)"), "<em>$1</em>")
        result = result.replace(Regex("_(.*?)_"), "<em>$1</em>")
        result = result.replace(Regex("~~(.*?)~~"), "<del>$1</del>")
        result = result.replace(Regex("`(.*?)`"), "<code style=\"background:#e2e8f0; padding:2px 6px; border-radius:4px;\">$1</code>")
        result = result.replace(Regex("\\[(.*?)\\]\\((.*?)\\)"), "<a href=\"$2\" style=\"color:#$themeColorHex; font-weight:bold;\">$1 🔗</a>")
        result = result.replace(Regex("(\\S+)\\|([\\S ]+)"), "<a href=\"$1\" style=\"color:#$themeColorHex; font-weight:bold;\">$2 🔗</a>")
        return result
    }

    private data class InlineSpan(
        val text: String,
        val isBold: Boolean = false,
        val isItalic: Boolean = false,
        val isStrike: Boolean = false,
        val isCode: Boolean = false
    )

    private fun parseInlineSpans(input: String): List<InlineSpan> {
        val result = mutableListOf<InlineSpan>()
        val regex = Regex("(\\*\\*.*?\\*\\*|~~.*?~~|`.*?`|(?<!\\*)\\*.*?\\*(?!\\*))")
        var lastIdx = 0
        for (m in regex.findAll(input)) {
            if (m.range.first > lastIdx) {
                result.add(InlineSpan(text = input.substring(lastIdx, m.range.first)))
            }
            val raw = m.value
            when {
                raw.startsWith("**") && raw.endsWith("**") && raw.length >= 4 ->
                    result.add(InlineSpan(text = raw.substring(2, raw.length - 2), isBold = true))
                raw.startsWith("~~") && raw.endsWith("~~") && raw.length >= 4 ->
                    result.add(InlineSpan(text = raw.substring(2, raw.length - 2), isStrike = true))
                raw.startsWith("`") && raw.endsWith("`") && raw.length >= 2 ->
                    result.add(InlineSpan(text = raw.substring(1, raw.length - 1), isCode = true))
                raw.startsWith("*") && raw.endsWith("*") && raw.length >= 2 ->
                    result.add(InlineSpan(text = raw.substring(1, raw.length - 1), isItalic = true))
                else -> result.add(InlineSpan(text = raw))
            }
            lastIdx = m.range.last + 1
        }
        if (lastIdx < input.length) {
            result.add(InlineSpan(text = input.substring(lastIdx)))
        }
        return if (result.isEmpty()) listOf(InlineSpan(text = input)) else result
    }

    private fun renderDocxRuns(text: String, defaultColorHex: String = "334155"): String {
        val spans = parseInlineSpans(text)
        val sb = StringBuilder()
        for (span in spans) {
            val escaped = escapeXml(span.text).replace("\n", "</w:t><w:br/><w:t xml:space=\"preserve\">")
            sb.append("<w:r><w:rPr>")
            if (span.isBold) sb.append("<w:b/>")
            if (span.isItalic) sb.append("<w:i/>")
            if (span.isStrike) sb.append("<w:strike/>")
            if (span.isCode) {
                sb.append("<w:rFonts w:ascii=\"Consolas\" w:hAnsi=\"Consolas\"/>")
                sb.append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"F1F5F9\"/>")
                sb.append("<w:color w:val=\"0F172A\"/>")
            } else {
                sb.append("<w:color w:val=\"$defaultColorHex\"/>")
            }
            sb.append("</w:rPr><w:t xml:space=\"preserve\">$escaped</w:t></w:r>")
        }
        return sb.toString()
    }

    /**
     * Exports Note as a standard styled Microsoft Word (.docx) OpenXML document.
     * Preserves markdown headings, bold, italic, code, quotes, tables, checklists, callouts, and embedded images!
     */
    fun exportToDocx(
        context: Context,
        noteTitle: String,
        noteCategory: String,
        blocks: List<VisualBlock>,
        themeColor: Int = Color.parseColor("#FF6F00")
    ): Uri {
        val themeHex = colorToHex(themeColor)
        val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        val displayTitle = noteTitle.ifBlank { "Sem Título" }

        val imageEntries = mutableListOf<Pair<String, ByteArray>>() // id to bytes
        var imageCounter = 0

        val docSb = StringBuilder()
        docSb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
        docSb.append("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" ")
        docSb.append("xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" ")
        docSb.append("xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\" ")
        docSb.append("xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" ")
        docSb.append("xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">\n")
        docSb.append("<w:body>\n")

        // Title with theme color bottom border
        docSb.append("<w:p><w:pPr>")
        docSb.append("<w:spacing w:before=\"120\" w:after=\"160\"/>")
        docSb.append("<w:pBdr><w:bottom w:val=\"single\" w:sz=\"24\" w:space=\"8\" w:color=\"$themeHex\"/></w:pBdr>")
        docSb.append("</w:pPr>")
        docSb.append("<w:r><w:rPr><w:b/><w:sz w:val=\"48\"/><w:color w:val=\"0F172A\"/></w:rPr>")
        docSb.append("<w:t xml:space=\"preserve\">${escapeXml(displayTitle)}</w:t></w:r></w:p>\n")

        // Metadata: Category & Date
        docSb.append("<w:p><w:pPr><w:spacing w:after=\"260\"/></w:pPr>")
        docSb.append("<w:r><w:rPr><w:b/><w:sz w:val=\"20\"/><w:color w:val=\"$themeHex\"/></w:rPr>")
        docSb.append("<w:t xml:space=\"preserve\">[ Categoria: ${escapeXml(noteCategory)} ] </w:t></w:r>")
        docSb.append("<w:r><w:rPr><w:sz w:val=\"18\"/><w:color w:val=\"64748B\"/></w:rPr>")
        docSb.append("<w:t xml:space=\"preserve\"> • Exportado pelo Citra em $dateStr</w:t></w:r></w:p>\n")

        // Iterate blocks
        for (block in blocks) {
            when (block) {
                is HeadingBlock -> {
                    val (szVal, colHex, spaceBefore) = when (block.level) {
                        1 -> Triple("36", themeHex, "280")
                        2 -> Triple("32", "1E293B", "240")
                        3 -> Triple("28", themeHex, "200")
                        4 -> Triple("26", "334155", "160")
                        else -> Triple("24", "475569", "120")
                    }
                    docSb.append("<w:p><w:pPr><w:spacing w:before=\"$spaceBefore\" w:after=\"100\"/></w:pPr>")
                    docSb.append("<w:r><w:rPr><w:b/><w:sz w:val=\"$szVal\"/><w:color w:val=\"$colHex\"/></w:rPr>")
                    docSb.append("<w:t xml:space=\"preserve\">${escapeXml(block.text.ifBlank { " " })}</w:t></w:r></w:p>\n")
                }

                is TextBlock -> {
                    if (block.text.isNotBlank()) {
                        docSb.append("<w:p><w:pPr><w:spacing w:after=\"140\" w:line=\"276\" w:lineRule=\"auto\"/></w:pPr>")
                        docSb.append(renderDocxRuns(block.text))
                        docSb.append("</w:p>\n")
                    } else {
                        docSb.append("<w:p><w:pPr><w:spacing w:after=\"80\"/></w:pPr></w:p>\n")
                    }
                }

                is ChecklistBlock -> {
                    val markSymbol = if (block.isChecked) "☑ " else "☐ "
                    val markCol = if (block.isChecked) themeHex else "94A3B8"
                    docSb.append("<w:p><w:pPr><w:ind w:left=\"360\"/><w:spacing w:after=\"80\"/></w:pPr>")
                    docSb.append("<w:r><w:rPr><w:b/><w:sz w:val=\"24\"/><w:color w:val=\"$markCol\"/></w:rPr>")
                    docSb.append("<w:t xml:space=\"preserve\">$markSymbol</w:t></w:r>")
                    if (block.isChecked) {
                        docSb.append("<w:r><w:rPr><w:strike/><w:color w:val=\"94A3B8\"/></w:rPr>")
                        docSb.append("<w:t xml:space=\"preserve\">${escapeXml(block.text)}</w:t></w:r>")
                    } else {
                        docSb.append(renderDocxRuns(block.text))
                    }
                    docSb.append("</w:p>\n")
                }

                is BulletBlock -> {
                    docSb.append("<w:p><w:pPr><w:ind w:left=\"360\"/><w:spacing w:after=\"80\"/></w:pPr>")
                    docSb.append("<w:r><w:rPr><w:b/><w:color w:val=\"$themeHex\"/></w:rPr>")
                    docSb.append("<w:t xml:space=\"preserve\">• </w:t></w:r>")
                    docSb.append(renderDocxRuns(block.text))
                    docSb.append("</w:p>\n")
                }

                is QuoteBlock -> {
                    docSb.append("<w:p><w:pPr><w:ind w:left=\"420\"/>")
                    docSb.append("<w:pBdr><w:left w:val=\"single\" w:sz=\"24\" w:space=\"12\" w:color=\"$themeHex\"/></w:pBdr>")
                    docSb.append("<w:spacing w:before=\"120\" w:after=\"120\"/></w:pPr>")
                    docSb.append("<w:r><w:rPr><w:i/><w:color w:val=\"475569\"/></w:rPr>")
                    docSb.append("<w:t xml:space=\"preserve\">${escapeXml(block.text)}</w:t></w:r></w:p>\n")
                }

                is CodeBlock -> {
                    docSb.append("<w:p><w:pPr><w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"F1F5F9\"/>")
                    docSb.append("<w:ind w:left=\"240\" w:right=\"240\"/>")
                    docSb.append("<w:spacing w:before=\"120\" w:after=\"120\"/></w:pPr>")
                    val lines = block.code.lines()
                    for ((lIdx, line) in lines.withIndex()) {
                        docSb.append("<w:r><w:rPr><w:rFonts w:ascii=\"Consolas\" w:hAnsi=\"Consolas\"/>")
                        docSb.append("<w:sz w:val=\"19\"/><w:color w:val=\"0F172A\"/></w:rPr>")
                        docSb.append("<w:t xml:space=\"preserve\">${escapeXml(line)}</w:t>")
                        if (lIdx < lines.size - 1) docSb.append("<w:br/>")
                        docSb.append("</w:r>")
                    }
                    docSb.append("</w:p>\n")
                }

                is MathBlock -> {
                    docSb.append("<w:p><w:pPr><w:jc w:val=\"center\"/><w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"EFF6FF\"/>")
                    docSb.append("<w:spacing w:before=\"120\" w:after=\"120\"/></w:pPr>")
                    docSb.append("<w:r><w:rPr><w:rFonts w:ascii=\"Times New Roman\" w:hAnsi=\"Times New Roman\"/>")
                    docSb.append("<w:i/><w:sz w:val=\"24\"/><w:color w:val=\"0F172A\"/></w:rPr>")
                    docSb.append("<w:t xml:space=\"preserve\">${escapeXml(block.formula)}</w:t></w:r></w:p>\n")
                }

                is TableBlock -> {
                    docSb.append("<w:tbl><w:tblPr><w:tblW w:w=\"0\" w:type=\"auto\"/>")
                    docSb.append("<w:tblBorders>")
                    docSb.append("<w:top w:val=\"single\" w:sz=\"6\" w:space=\"0\" w:color=\"CBD5E1\"/>")
                    docSb.append("<w:left w:val=\"single\" w:sz=\"6\" w:space=\"0\" w:color=\"CBD5E1\"/>")
                    docSb.append("<w:bottom w:val=\"single\" w:sz=\"6\" w:space=\"0\" w:color=\"CBD5E1\"/>")
                    docSb.append("<w:right w:val=\"single\" w:sz=\"6\" w:space=\"0\" w:color=\"CBD5E1\"/>")
                    docSb.append("<w:insideH w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"E2E8F0\"/>")
                    docSb.append("<w:insideV w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"E2E8F0\"/>")
                    docSb.append("</w:tblBorders></w:tblPr>\n")

                    // Header row with theme tint
                    docSb.append("<w:tr><w:trPr><w:tblHeader/></w:trPr>")
                    for (header in block.headers) {
                        docSb.append("<w:tc><w:tcPr><w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"F8FAFC\"/>")
                        docSb.append("<w:tcMar><w:top w:w=\"120\"/><w:bottom w:w=\"120\"/><w:left w:w=\"140\"/><w:right w:w=\"140\"/></w:tcMar></w:tcPr>")
                        docSb.append("<w:p><w:pPr><w:spacing w:before=\"60\" w:after=\"60\"/></w:pPr>")
                        docSb.append("<w:r><w:rPr><w:b/><w:sz w:val=\"20\"/><w:color w:val=\"0F172A\"/></w:rPr>")
                        docSb.append("<w:t xml:space=\"preserve\">${escapeXml(header)}</w:t></w:r></w:p></w:tc>")
                    }
                    docSb.append("</w:tr>\n")

                    // Data rows
                    for (row in block.rows) {
                        docSb.append("<w:tr>")
                        for (cIdx in 0 until block.headers.size) {
                            val cellText = if (cIdx < row.size) row[cIdx] else ""
                            docSb.append("<w:tc><w:tcPr><w:tcMar><w:top w:w=\"100\"/><w:bottom w:w=\"100\"/><w:left w:w=\"140\"/><w:right w:w=\"140\"/></w:tcMar></w:tcPr>")
                            docSb.append("<w:p><w:pPr><w:spacing w:before=\"40\" w:after=\"40\"/></w:pPr>")
                            docSb.append("<w:r><w:rPr><w:sz w:val=\"20\"/><w:color w:val=\"334155\"/></w:rPr>")
                            docSb.append("<w:t xml:space=\"preserve\">${escapeXml(cellText)}</w:t></w:r></w:p></w:tc>")
                        }
                        docSb.append("</w:tr>\n")
                    }
                    docSb.append("</w:tbl>\n")
                }

                is CalloutBlock -> {
                    val calloutCol = when (block.type) {
                        "tip" -> "10B981"
                        "warning" -> "F59E0B"
                        "important" -> "EF4444"
                        else -> themeHex
                    }
                    val calloutBg = when (block.type) {
                        "tip" -> "ECFDF5"
                        "warning" -> "FFFBEB"
                        "important" -> "FEF2F2"
                        else -> "F8FAFC"
                    }
                    docSb.append("<w:p><w:pPr><w:ind w:left=\"300\" w:right=\"300\"/>")
                    docSb.append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"$calloutBg\"/>")
                    docSb.append("<w:pBdr><w:left w:val=\"single\" w:sz=\"28\" w:space=\"12\" w:color=\"$calloutCol\"/></w:pBdr>")
                    docSb.append("<w:spacing w:before=\"140\" w:after=\"140\"/></w:pPr>")
                    docSb.append("<w:r><w:rPr><w:b/><w:color w:val=\"$calloutCol\"/></w:rPr>")
                    docSb.append("<w:t xml:space=\"preserve\">${block.type.uppercase()}: </w:t></w:r>")
                    docSb.append(renderDocxRuns(block.text))
                    docSb.append("</w:p>\n")
                }

                is ToggleBlock -> {
                    docSb.append("<w:p><w:pPr><w:spacing w:before=\"100\" w:after=\"60\"/></w:pPr>")
                    docSb.append("<w:r><w:rPr><w:b/><w:color w:val=\"$themeHex\"/></w:rPr>")
                    docSb.append("<w:t xml:space=\"preserve\">▼ ${escapeXml(block.title)}</w:t></w:r></w:p>\n")
                    docSb.append("<w:p><w:pPr><w:ind w:left=\"360\"/><w:spacing w:after=\"100\"/></w:pPr>")
                    docSb.append(renderDocxRuns(block.content))
                    docSb.append("</w:p>\n")
                }

                is DividerBlock -> {
                    docSb.append("<w:p><w:pPr><w:pBdr><w:bottom w:val=\"single\" w:sz=\"12\" w:space=\"1\" w:color=\"$themeHex\"/></w:pBdr>")
                    docSb.append("<w:spacing w:before=\"140\" w:after=\"140\"/></w:pPr></w:p>\n")
                }

                is ImageBlock -> {
                    val bmp = loadBitmapFromUrl(context, block.url)
                    if (bmp != null) {
                        imageCounter++
                        val relId = "rIdImg$imageCounter"
                        val bos = ByteArrayOutputStream()
                        bmp.compress(Bitmap.CompressFormat.JPEG, 85, bos)
                        imageEntries.add(relId to bos.toByteArray())

                        val hasSide = block.widthPercent < 100 && (block.alignment == "left" || block.alignment == "right") && block.sideText.isNotBlank()
                        if (hasSide) {
                            val imgFraction = (block.widthPercent / 100f).coerceIn(0.25f, 0.75f)
                            val totalEmu = 5400000L
                            val imgEmu = (totalEmu * imgFraction).toLong()
                            val textEmu = totalEmu - imgEmu
                            val emuH = ((imgEmu * bmp.height) / bmp.width.coerceAtLeast(1)).coerceIn(500000L, 6000000L)

                            val imgTwips = imgEmu / 635L
                            val textTwips = textEmu / 635L

                            val drawingXml = buildString {
                                append("<w:r><w:drawing><wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\">")
                                append("<wp:extent cx=\"$imgEmu\" cy=\"$emuH\"/>")
                                append("<wp:effectExtent l=\"0\" t=\"0\" r=\"0\" b=\"0\"/>")
                                append("<wp:docPr id=\"$imageCounter\" name=\"Image_$imageCounter\"/>")
                                append("<wp:cNvGraphicFramePr><a:graphicFrameLocks noChangeAspect=\"1\"/></wp:cNvGraphicFramePr>")
                                append("<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">")
                                append("<pic:pic><pic:nvPicPr><pic:cNvPr id=\"$imageCounter\" name=\"image_$imageCounter.jpeg\"/><pic:cNvPicPr/></pic:nvPicPr>")
                                append("<pic:blipFill><a:blip r:embed=\"$relId\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>")
                                append("<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"$imgEmu\" cy=\"$emuH\"/></a:xfrm>")
                                append("<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr></pic:pic>")
                                append("</a:graphicData></a:graphic></wp:inline></w:drawing></w:r>")
                            }

                            val textCellXml = buildString {
                                val lines = block.sideText.split("\n")
                                for (l in lines) {
                                    append("<w:p><w:pPr><w:spacing w:before=\"40\" w:after=\"40\"/></w:pPr><w:r><w:rPr><w:sz w:val=\"20\"/></w:rPr><w:t xml:space=\"preserve\">${escapeXml(l)}</w:t></w:r></w:p>")
                                }
                            }

                            docSb.append("<w:tbl>")
                            docSb.append("<w:tblPr><w:tblW w:w=\"10000\" w:type=\"pct\"/><w:tblBorders><w:top w:val=\"none\"/><w:left w:val=\"none\"/><w:bottom w:val=\"none\"/><w:right w:val=\"none\"/><w:insideH w:val=\"none\"/><w:insideV w:val=\"none\"/></w:tblBorders></w:tblPr>")
                            docSb.append("<w:tr>")
                            if (block.alignment == "left") {
                                docSb.append("<w:tc><w:tcPr><w:tcW w:w=\"$imgTwips\" w:type=\"dxa\"/><w:vAlign w:val=\"top\"/></w:tcPr><w:p>$drawingXml</w:p></w:tc>")
                                docSb.append("<w:tc><w:tcPr><w:tcW w:w=\"$textTwips\" w:type=\"dxa\"/><w:vAlign w:val=\"top\"/></w:tcPr>$textCellXml</w:tc>")
                            } else {
                                docSb.append("<w:tc><w:tcPr><w:tcW w:w=\"$textTwips\" w:type=\"dxa\"/><w:vAlign w:val=\"top\"/></w:tcPr>$textCellXml</w:tc>")
                                docSb.append("<w:tc><w:tcPr><w:tcW w:w=\"$imgTwips\" w:type=\"dxa\"/><w:vAlign w:val=\"top\"/></w:tcPr><w:p>$drawingXml</w:p></w:tc>")
                            }
                            docSb.append("</w:tr></w:tbl>\n")
                        } else {
                            // 1 inch = 914400 EMUs. Printable width ~ 5,400,000 EMUs.
                            val maxEmuW = (5400000L * block.widthPercent / 100).coerceIn(1000000L, 5400000L)
                            val emuH = ((maxEmuW * bmp.height) / bmp.width.coerceAtLeast(1)).coerceIn(500000L, 6000000L)

                            val jcVal = when (block.alignment) {
                                "left" -> "left"
                                "right" -> "right"
                                else -> "center"
                            }
                            docSb.append("<w:p><w:pPr><w:jc w:val=\"$jcVal\"/><w:spacing w:before=\"120\" w:after=\"120\"/></w:pPr>")
                            docSb.append("<w:r><w:drawing><wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\">")
                            docSb.append("<wp:extent cx=\"$maxEmuW\" cy=\"$emuH\"/>")
                            docSb.append("<wp:effectExtent l=\"0\" t=\"0\" r=\"0\" b=\"0\"/>")
                            docSb.append("<wp:docPr id=\"$imageCounter\" name=\"Image_$imageCounter\"/>")
                            docSb.append("<wp:cNvGraphicFramePr><a:graphicFrameLocks noChangeAspect=\"1\"/></wp:cNvGraphicFramePr>")
                            docSb.append("<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">")
                            docSb.append("<pic:pic><pic:nvPicPr><pic:cNvPr id=\"$imageCounter\" name=\"image_$imageCounter.jpeg\"/><pic:cNvPicPr/></pic:nvPicPr>")
                            docSb.append("<pic:blipFill><a:blip r:embed=\"$relId\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>")
                            docSb.append("<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"$maxEmuW\" cy=\"$emuH\"/></a:xfrm>")
                            docSb.append("<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr></pic:pic>")
                            docSb.append("</a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>\n")
                        }
                    } else {
                        docSb.append("<w:p><w:pPr><w:spacing w:before=\"80\" w:after=\"80\"/></w:pPr>")
                        docSb.append("<w:r><w:rPr><w:color w:val=\"64748B\"/><w:i/></w:rPr>")
                        docSb.append("<w:t xml:space=\"preserve\">🖼️ [Imagem: ${escapeXml(block.alt)}]</w:t></w:r></w:p>\n")
                    }
                }

                is AudioBlock -> {
                    docSb.append("<w:p><w:pPr><w:ind w:left=\"240\" w:right=\"240\"/>")
                    docSb.append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"F1F5F9\"/>")
                    docSb.append("<w:pBdr><w:left w:val=\"single\" w:sz=\"20\" w:space=\"8\" w:color=\"$themeHex\"/></w:pBdr>")
                    docSb.append("<w:spacing w:before=\"80\" w:after=\"80\"/></w:pPr>")
                    docSb.append("<w:r><w:rPr><w:b/><w:color w:val=\"$themeHex\"/></w:rPr>")
                    docSb.append("<w:t xml:space=\"preserve\">🎵 Áudio Citra: </w:t></w:r>")
                    docSb.append("<w:r><w:rPr><w:color w:val=\"0F172A\"/></w:rPr>")
                    docSb.append("<w:t xml:space=\"preserve\">${escapeXml(block.title)}</w:t></w:r></w:p>\n")
                }
            }
        }

        // Section properties
        docSb.append("<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/>") // A4 size in twips
        docSb.append("<w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\"/></w:sectPr>\n")
        docSb.append("</w:body></w:document>")

        // Assemble DOCX ZIP
        val file = File(getExportDir(context), "${sanitizeFilename(noteTitle)}.docx")
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            // 1. [Content_Types].xml
            zos.putNextEntry(ZipEntry("[Content_Types].xml"))
            val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Default Extension="jpeg" ContentType="image/jpeg"/>
  <Default Extension="jpg" ContentType="image/jpeg"/>
  <Default Extension="png" ContentType="image/png"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""
            zos.write(contentTypes.toByteArray())
            zos.closeEntry()

            // 2. _rels/.rels
            zos.putNextEntry(ZipEntry("_rels/.rels"))
            val rootRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""
            zos.write(rootRels.toByteArray())
            zos.closeEntry()

            // 3. word/_rels/document.xml.rels
            zos.putNextEntry(ZipEntry("word/_rels/document.xml.rels"))
            val docRelsSb = StringBuilder()
            docRelsSb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
            docRelsSb.append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n")
            for ((idx, item) in imageEntries.withIndex()) {
                val relId = item.first
                docRelsSb.append("  <Relationship Id=\"$relId\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/image_${idx + 1}.jpeg\"/>\n")
            }
            docRelsSb.append("</Relationships>")
            zos.write(docRelsSb.toString().toByteArray())
            zos.closeEntry()

            // 4. word/document.xml
            zos.putNextEntry(ZipEntry("word/document.xml"))
            zos.write(docSb.toString().toByteArray())
            zos.closeEntry()

            // 5. word/media/image_X.jpeg
            for ((idx, item) in imageEntries.withIndex()) {
                zos.putNextEntry(ZipEntry("word/media/image_${idx + 1}.jpeg"))
                zos.write(item.second)
                zos.closeEntry()
            }
        }

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /**
     * Exports Note as a standard styled PDF document using Android's native PdfDocument.
     * Incorporates app theme colors and renders full embedded images.
     */
    fun exportToPdf(
        context: Context,
        noteTitle: String,
        noteCategory: String,
        blocks: List<VisualBlock>,
        themeColor: Int = Color.parseColor("#FF6F00")
    ): Uri {
        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 40f
        val contentWidth = (pageWidth - (margin * 2)).toInt()

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas
        var y = margin

        fun checkNewPage(neededHeight: Float) {
            if (y + neededHeight > pageHeight - margin) {
                document.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = document.startPage(pageInfo)
                canvas = page.canvas
                y = margin
            }
        }

        // Header Top Accent Line in Theme Color
        val accentLinePaint = Paint().apply {
            color = themeColor
            strokeWidth = 3f
        }
        canvas.drawLine(margin, y, margin + contentWidth, y, accentLinePaint)
        y += 14f

        // Title
        val titlePaint = TextPaint().apply {
            color = Color.parseColor("#0F172A")
            textSize = 22f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val displayTitle = noteTitle.ifBlank { "Sem Título" }
        val titleLayout = createStaticLayout(displayTitle, titlePaint, contentWidth)
        titleLayout.draw(canvas)
        y += titleLayout.height + 6f

        // Category & Date Badge
        val metaPaint = TextPaint().apply {
            color = themeColor
            textSize = 10f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        val metaText = "Categoria: $noteCategory  •  Exportado em $dateStr  •  Citra"
        canvas.drawText(metaText, margin, y + 10f, metaPaint)
        y += 20f

        // Divider
        val dividerPaint = Paint().apply {
            color = Color.parseColor("#E2E8F0")
            strokeWidth = 1.5f
        }
        canvas.drawLine(margin, y, margin + contentWidth, y, dividerPaint)
        y += 16f

        val bodyPaint = TextPaint().apply {
            color = Color.parseColor("#334155")
            textSize = 12f
            isAntiAlias = true
        }

        val codePaint = TextPaint().apply {
            color = Color.parseColor("#0F172A")
            textSize = 10f
            typeface = Typeface.MONOSPACE
            isAntiAlias = true
        }

        val quotePaint = TextPaint().apply {
            color = Color.parseColor("#475569")
            textSize = 11.5f
            textSkewX = -0.25f
            isAntiAlias = true
        }

        // Blocks iteration
        for (block in blocks) {
            when (block) {
                is HeadingBlock -> {
                    val size = when (block.level) {
                        1 -> 18f
                        2 -> 16f
                        3 -> 14f
                        4 -> 13f
                        else -> 12f
                    }
                    val hPaint = TextPaint().apply {
                        color = if (block.level == 1) themeColor else Color.parseColor("#1E293B")
                        textSize = size
                        isFakeBoldText = true
                        isAntiAlias = true
                    }
                    val layout = createStaticLayout(block.text.ifBlank { " " }, hPaint, contentWidth)
                    checkNewPage(layout.height + 14f)
                    canvas.save()
                    canvas.translate(margin, y)
                    layout.draw(canvas)
                    canvas.restore()
                    y += layout.height + 10f
                }

                is TextBlock -> {
                    if (block.text.isNotBlank()) {
                        val layout = createStaticLayout(block.text, bodyPaint, contentWidth)
                        checkNewPage(layout.height + 8f)
                        canvas.save()
                        canvas.translate(margin, y)
                        layout.draw(canvas)
                        canvas.restore()
                        y += layout.height + 8f
                    } else {
                        y += 6f
                    }
                }

                is ChecklistBlock -> {
                    val textLayout = createStaticLayout(block.text.ifBlank { " " }, bodyPaint, contentWidth - 24)
                    checkNewPage(max(16f, textLayout.height.toFloat()) + 6f)

                    val boxPaint = Paint().apply {
                        color = if (block.isChecked) themeColor else Color.parseColor("#94A3B8")
                        style = if (block.isChecked) Paint.Style.FILL else Paint.Style.STROKE
                        strokeWidth = 1.5f
                        isAntiAlias = true
                    }
                    val boxRect = RectF(margin, y + 2f, margin + 12f, y + 14f)
                    canvas.drawRoundRect(boxRect, 3f, 3f, boxPaint)

                    if (block.isChecked) {
                        val checkMarkPaint = Paint().apply {
                            color = Color.WHITE
                            strokeWidth = 1.8f
                            style = Paint.Style.STROKE
                            isAntiAlias = true
                        }
                        canvas.drawLine(margin + 3f, y + 8f, margin + 6f, y + 11f, checkMarkPaint)
                        canvas.drawLine(margin + 6f, y + 11f, margin + 10f, y + 5f, checkMarkPaint)
                    }

                    canvas.save()
                    canvas.translate(margin + 20f, y)
                    textLayout.draw(canvas)
                    canvas.restore()
                    y += textLayout.height + 6f
                }

                is BulletBlock -> {
                    val textLayout = createStaticLayout(block.text.ifBlank { " " }, bodyPaint, contentWidth - 28)
                    checkNewPage(max(14f, textLayout.height.toFloat()) + 6f)

                    val bulletPaint = Paint().apply {
                        color = themeColor
                        style = Paint.Style.FILL
                        isAntiAlias = true
                    }
                    canvas.drawCircle(margin + 12f, y + 6f, 2.5f, bulletPaint)

                    canvas.save()
                    canvas.translate(margin + 24f, y)
                    textLayout.draw(canvas)
                    canvas.restore()
                    y += textLayout.height + 6f
                }

                is QuoteBlock -> {
                    val textLayout = createStaticLayout(block.text.ifBlank { " " }, quotePaint, contentWidth - 24)
                    val qHeight = textLayout.height + 8f
                    checkNewPage(qHeight + 8f)

                    val barPaint = Paint().apply {
                        color = themeColor
                        strokeWidth = 3f
                        isAntiAlias = true
                    }
                    canvas.drawLine(margin + 2f, y, margin + 2f, y + qHeight, barPaint)

                    canvas.save()
                    canvas.translate(margin + 14f, y + 2f)
                    textLayout.draw(canvas)
                    canvas.restore()
                    y += qHeight + 8f
                }

                is CodeBlock -> {
                    val codeLayout = createStaticLayout(block.code.ifBlank { " " }, codePaint, contentWidth - 24)
                    val bgHeight = codeLayout.height + 20f
                    checkNewPage(bgHeight + 10f)

                    val bgPaint = Paint().apply {
                        color = Color.parseColor("#F1F5F9")
                        style = Paint.Style.FILL
                        isAntiAlias = true
                    }
                    val borderPaint = Paint().apply {
                        color = Color.parseColor("#CBD5E1")
                        style = Paint.Style.STROKE
                        strokeWidth = 1f
                        isAntiAlias = true
                    }
                    val rect = RectF(margin, y, margin + contentWidth, y + bgHeight)
                    canvas.drawRoundRect(rect, 8f, 8f, bgPaint)
                    canvas.drawRoundRect(rect, 8f, 8f, borderPaint)

                    canvas.save()
                    canvas.translate(margin + 12f, y + 10f)
                    codeLayout.draw(canvas)
                    canvas.restore()
                    y += bgHeight + 10f
                }

                is MathBlock -> {
                    val mathTextPaint = TextPaint().apply {
                        color = Color.parseColor("#0F172A")
                        textSize = 13f
                        typeface = Typeface.SERIF
                        isAntiAlias = true
                    }
                    val mathLayout = createStaticLayout(block.formula, mathTextPaint, contentWidth - 24)
                    val bgHeight = mathLayout.height + 16f
                    checkNewPage(bgHeight + 8f)

                    val bgPaint = Paint().apply {
                        color = Color.parseColor("#EFF6FF")
                        style = Paint.Style.FILL
                        isAntiAlias = true
                    }
                    val rect = RectF(margin, y, margin + contentWidth, y + bgHeight)
                    canvas.drawRoundRect(rect, 8f, 8f, bgPaint)

                    canvas.save()
                    canvas.translate(margin + 12f, y + 8f)
                    mathLayout.draw(canvas)
                    canvas.restore()
                    y += bgHeight + 8f
                }

                is TableBlock -> {
                    val cols = block.headers.size.coerceAtLeast(1)
                    val colWidth = contentWidth / cols
                    val rowHeight = 22f
                    val totalHeight = (block.rows.size + 1) * rowHeight + 10f
                    checkNewPage(totalHeight)

                    val tableHeaderPaint = Paint().apply {
                        color = Color.parseColor("#F8FAFC")
                        style = Paint.Style.FILL
                    }
                    val cellBorderPaint = Paint().apply {
                        color = Color.parseColor("#CBD5E1")
                        style = Paint.Style.STROKE
                        strokeWidth = 1f
                    }
                    val tableTextPaint = TextPaint().apply {
                        color = Color.parseColor("#0F172A")
                        textSize = 10f
                        isAntiAlias = true
                    }

                    canvas.drawRect(margin, y, margin + contentWidth, y + rowHeight, tableHeaderPaint)
                    canvas.drawRect(margin, y, margin + contentWidth, y + rowHeight, cellBorderPaint)
                    for ((cIdx, header) in block.headers.withIndex()) {
                        val cx = margin + (cIdx * colWidth) + 6f
                        canvas.drawText(header, cx, y + 15f, tableTextPaint)
                    }
                    y += rowHeight

                    for (row in block.rows) {
                        canvas.drawRect(margin, y, margin + contentWidth, y + rowHeight, cellBorderPaint)
                        for (cIdx in 0 until cols) {
                            val cellText = if (cIdx < row.size) row[cIdx] else ""
                            val cx = margin + (cIdx * colWidth) + 6f
                            canvas.drawText(cellText, cx, y + 15f, tableTextPaint)
                        }
                        y += rowHeight
                    }
                    y += 10f
                }

                is CalloutBlock -> {
                    val calloutBorderColor = when (block.type) {
                        "tip" -> Color.parseColor("#10B981")
                        "warning" -> Color.parseColor("#F59E0B")
                        "important" -> Color.parseColor("#EF4444")
                        else -> themeColor
                    }
                    val calloutBgColor = when (block.type) {
                        "tip" -> Color.parseColor("#ECFDF5")
                        "warning" -> Color.parseColor("#FFFBEB")
                        "important" -> Color.parseColor("#FEF2F2")
                        else -> Color.parseColor("#F8FAFC")
                    }
                    val cTextLayout = createStaticLayout(block.text, bodyPaint, contentWidth - 30)
                    val cHeight = cTextLayout.height + 20f
                    checkNewPage(cHeight + 8f)

                    val cBgPaint = Paint().apply {
                        color = calloutBgColor
                        style = Paint.Style.FILL
                    }
                    val cBorderPaint = Paint().apply {
                        color = calloutBorderColor
                        strokeWidth = 3f
                    }
                    val cRect = RectF(margin, y, margin + contentWidth, y + cHeight)
                    canvas.drawRoundRect(cRect, 8f, 8f, cBgPaint)
                    canvas.drawLine(margin, y, margin, y + cHeight, cBorderPaint)

                    canvas.save()
                    canvas.translate(margin + 16f, y + 10f)
                    cTextLayout.draw(canvas)
                    canvas.restore()
                    y += cHeight + 8f
                }

                is ToggleBlock -> {
                    val tTitlePaint = TextPaint().apply {
                        color = themeColor
                        textSize = 13f
                        isFakeBoldText = true
                        isAntiAlias = true
                    }
                    val tTitleLayout = createStaticLayout("▼ ${block.title}", tTitlePaint, contentWidth - 10)
                    val tContentLayout = createStaticLayout(block.content, bodyPaint, contentWidth - 20)
                    val tHeight = tTitleLayout.height + tContentLayout.height + 14f
                    checkNewPage(tHeight)

                    canvas.save()
                    canvas.translate(margin, y)
                    tTitleLayout.draw(canvas)
                    canvas.restore()
                    y += tTitleLayout.height + 4f

                    canvas.save()
                    canvas.translate(margin + 14f, y)
                    tContentLayout.draw(canvas)
                    canvas.restore()
                    y += tContentLayout.height + 8f
                }

                is DividerBlock -> {
                    checkNewPage(16f)
                    canvas.drawLine(margin, y + 6f, margin + contentWidth, y + 6f, dividerPaint)
                    y += 16f
                }

                is ImageBlock -> {
                    val bmp = loadBitmapFromUrl(context, block.url)
                    if (bmp != null) {
                        val hasSide = block.widthPercent < 100 && (block.alignment == "left" || block.alignment == "right") && block.sideText.isNotBlank()
                        if (hasSide) {
                            val imgFraction = (block.widthPercent / 100f).coerceIn(0.25f, 0.75f)
                            val gap = 14f
                            val totalAvailable = contentWidth - gap
                            val imgW = totalAvailable * imgFraction
                            val textW = totalAvailable * (1f - imgFraction)
                            val scale = (imgW / bmp.width.toFloat()).coerceAtMost(360f / bmp.height.toFloat())
                            val drawW = bmp.width * scale
                            val drawH = bmp.height * scale

                            val textLayout = createStaticLayout(block.sideText, bodyPaint, textW.toInt().coerceAtLeast(60))
                            val rowH = maxOf(drawH, textLayout.height.toFloat())
                            checkNewPage(rowH + 16f)

                            val (imgX, textX) = if (block.alignment == "left") {
                                Pair(margin, margin + imgW + gap)
                            } else {
                                Pair(margin + textW + gap, margin)
                            }

                            val rect = RectF(imgX, y, imgX + drawW, y + drawH)
                            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                            canvas.drawBitmap(bmp, null, rect, paint)

                            canvas.save()
                            canvas.translate(textX, y)
                            textLayout.draw(canvas)
                            canvas.restore()

                            y += rowH + 16f
                        } else {
                            val maxW = (contentWidth.toFloat() * (block.widthPercent.toFloat() / 100f)).coerceAtLeast(100f)
                            val scale = (maxW / bmp.width.toFloat()).coerceAtMost(360f / bmp.height.toFloat())
                            val drawW = bmp.width * scale
                            val drawH = bmp.height * scale
                            checkNewPage(drawH + 16f)

                            val drawX = when (block.alignment) {
                                "left" -> margin
                                "right" -> margin + contentWidth - drawW
                                else -> margin + (contentWidth - drawW) / 2f
                            }
                            val rect = RectF(drawX, y, drawX + drawW, y + drawH)
                            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                            canvas.drawBitmap(bmp, null, rect, paint)
                            y += drawH + 14f
                        }
                    } else {
                        checkNewPage(26f)
                        val imgLabel = if (block.alt.isNotBlank()) "🖼️ [Imagem: ${block.alt}]" else "🖼️ [Imagem]"
                        canvas.drawText(imgLabel, margin, y + 14f, metaPaint)
                        y += 24f
                    }
                }

                is AudioBlock -> {
                    checkNewPage(30f)
                    val audioLabel = "🎵 [Áudio Citra: ${block.title}]"
                    canvas.drawText(audioLabel, margin, y + 14f, metaPaint)
                    y += 24f
                }
            }
        }

        document.finishPage(page)

        val file = File(getExportDir(context), "${sanitizeFilename(noteTitle)}.pdf")
        val outputStream = FileOutputStream(file)
        document.writeTo(outputStream)
        outputStream.flush()
        outputStream.close()
        document.close()

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /**
     * Generates a complete standalone HTML document with modern responsive CSS,
     * embedding images as Base64 and transforming audio tracks into an advanced interactive player art piece!
     */
    fun exportToHtml(
        context: Context,
        noteTitle: String,
        noteCategory: String,
        blocks: List<VisualBlock>,
        themeColor: Int = Color.parseColor("#FF6F00")
    ): String {
        val themeHex = colorToHex(themeColor)
        val title = noteTitle.ifBlank { "Sem Título" }
        val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        val sb = StringBuilder()

        sb.append("<!DOCTYPE html>\n<html lang=\"pt-BR\">\n<head>\n")
        sb.append("<meta charset=\"utf-8\">\n")
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
        sb.append("<title>${escapeHtml(title)} - Citra</title>\n")
        sb.append("<style>\n")
        sb.append(":root { --primary: #$themeHex; --primary-light: #$themeHex" + "1A; --primary-dark: #$themeHex; }\n")
        sb.append("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; line-height: 1.6; color: #1e293b; max-width: 820px; margin: 30px auto; padding: 0 20px; background: #f8fafc; }\n")
        sb.append(".card { background: #ffffff; padding: 36px; border-radius: 20px; box-shadow: 0 10px 30px rgba(0,0,0,0.06); border: 1px solid #e2e8f0; }\n")
        sb.append("h1 { color: #0f172a; border-bottom: 3px solid var(--primary); padding-bottom: 12px; margin-top: 0; font-size: 2rem; }\n")
        sb.append(".meta { color: #64748b; font-size: 0.88rem; margin-bottom: 24px; display: flex; align-items: center; gap: 14px; flex-wrap: wrap; }\n")
        sb.append(".badge { background: var(--primary-light); color: var(--primary); padding: 4px 12px; border-radius: 12px; font-weight: bold; font-size: 0.82rem; border: 1px solid var(--primary); }\n")
        sb.append("blockquote { border-left: 4px solid var(--primary); margin: 18px 0; padding: 12px 20px; background: var(--primary-light); border-radius: 0 12px 12px 0; color: #334155; }\n")
        sb.append("pre { background: #0f172a; color: #f8fafc; padding: 16px; border-radius: 12px; overflow-x: auto; font-family: monospace; font-size: 0.9rem; }\n")
        sb.append("table { width: 100%; border-collapse: collapse; margin: 20px 0; border-radius: 8px; overflow: hidden; }\n")
        sb.append("th, td { border: 1px solid #cbd5e1; padding: 10px 14px; text-align: left; }\n")
        sb.append("th { background: #f1f5f9; border-bottom: 2px solid var(--primary); font-weight: bold; }\n")
        sb.append(".callout { padding: 14px 18px; border-radius: 12px; margin: 16px 0; border-left: 5px solid; }\n")
        sb.append(".callout-info { background: #eff6ff; border-color: var(--primary); color: #1e3a8a; }\n")
        sb.append(".callout-tip { background: #ecfdf5; border-color: #10b981; color: #064e3b; }\n")
        sb.append(".callout-warning { background: #fffbeb; border-color: #f59e0b; color: #78350f; }\n")
        sb.append(".callout-important { background: #fef2f2; border-color: #ef4444; color: #7f1d1d; }\n")
        sb.append(".checklist-item { display: flex; align-items: center; gap: 10px; margin: 8px 0; }\n")
        sb.append(".checked { text-decoration: line-through; color: #94a3b8; }\n")
        sb.append("details { background: #f1f5f9; padding: 12px 16px; border-radius: 10px; margin: 12px 0; border-left: 3px solid var(--primary); }\n")
        sb.append("summary { font-weight: bold; cursor: pointer; color: #0f172a; }\n")
        sb.append(".math { background: #f8fafc; font-family: 'Times New Roman', serif; font-size: 1.2rem; padding: 14px; border-radius: 10px; border: 1px dashed #cbd5e1; text-align: center; }\n")
        sb.append("img.note-img { max-width: 100%; height: auto; border-radius: 14px; box-shadow: 0 4px 16px rgba(0,0,0,0.08); margin: 10px 0; }\n")

        // Advanced Interactive Audio Player Styles
        sb.append("/* Advanced Citra Audio Player Art */\n")
        sb.append(".citra-audio-card { background: linear-gradient(135deg, #1e293b 0%, #0f172a 100%); color: #f8fafc; padding: 20px; border-radius: 18px; margin: 20px 0; border: 1px solid var(--primary); box-shadow: 0 8px 24px rgba(0,0,0,0.15); display: flex; flex-direction: column; gap: 14px; }\n")
        sb.append(".audio-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; }\n")
        sb.append(".audio-title-group { display: flex; align-items: center; gap: 10px; }\n")
        sb.append(".vinyl-art { width: 44.dp; height: 44px; min-width: 44px; border-radius: 50%; background: radial-gradient(circle, #334155 35%, #0f172a 70%); border: 2px solid var(--primary); display: flex; align-items: center; justify-content: center; box-shadow: 0 0 10px var(--primary); }\n")
        sb.append(".wave-bars { display: flex; align-items: flex-end; gap: 3px; height: 24px; }\n")
        sb.append(".wave-bar { width: 4px; border-radius: 2px; background: var(--primary); animation: waveAnim 1.2s infinite ease-in-out; }\n")
        sb.append("@keyframes waveAnim { 0%, 100% { height: 6px; } 50% { height: 22px; } }\n")
        sb.append(".audio-controls { display: flex; align-items: center; gap: 14px; width: 100%; }\n")
        sb.append(".play-btn { width: 40px; height: 40px; border-radius: 50%; border: none; background: var(--primary); color: white; cursor: pointer; font-size: 16px; display: flex; align-items: center; justify-content: center; transition: transform 0.2s; }\n")
        sb.append(".play-btn:hover { transform: scale(1.08); }\n")
        sb.append(".scrubber { flex: 1; accent-color: var(--primary); height: 6px; border-radius: 3px; cursor: pointer; }\n")
        sb.append(".time-label { font-size: 0.8rem; font-family: monospace; color: #94a3b8; min-width: 70px; text-align: right; }\n")
        sb.append(".footer { margin-top: 40px; text-align: center; font-size: 0.82rem; color: #94a3b8; }\n")
        sb.append("</style>\n</head>\n<body>\n<div class=\"card\">\n")
        sb.append("<h1>${escapeHtml(title)}</h1>\n")
        sb.append("<div class=\"meta\"><span class=\"badge\">$noteCategory</span><span>Exportado em $dateStr</span><span>Citra</span></div>\n")

        var audioIndex = 0
        for (block in blocks) {
            when (block) {
                is HeadingBlock -> sb.append("<h${block.level}>${renderInlineHtml(block.text, themeHex)}</h${block.level}>\n")
                is TextBlock -> if (block.text.isNotBlank()) sb.append("<p>${renderInlineHtml(block.text, themeHex)}</p>\n")
                is ChecklistBlock -> {
                    val chk = if (block.isChecked) "checked" else ""
                    val cls = if (block.isChecked) "checked" else ""
                    sb.append("<div class=\"checklist-item\"><input type=\"checkbox\" $chk disabled style=\"accent-color:var(--primary);\"><span class=\"$cls\">${renderInlineHtml(block.text, themeHex)}</span></div>\n")
                }
                is BulletBlock -> sb.append("<ul><li>${renderInlineHtml(block.text, themeHex)}</li></ul>\n")
                is QuoteBlock -> sb.append("<blockquote>${renderInlineHtml(block.text, themeHex)}</blockquote>\n")
                is CodeBlock -> sb.append("<pre><code class=\"language-${block.language}\">${escapeHtml(block.code)}</code></pre>\n")
                is MathBlock -> sb.append("<div class=\"math\">$$ ${escapeHtml(block.formula)} $$</div>\n")
                is TableBlock -> {
                    sb.append("<table><thead><tr>")
                    for (h in block.headers) sb.append("<th>${escapeHtml(h)}</th>")
                    sb.append("</tr></thead><tbody>\n")
                    for (row in block.rows) {
                        sb.append("<tr>")
                        for (c in row) sb.append("<td>${escapeHtml(c)}</td>")
                        sb.append("</tr>\n")
                    }
                    sb.append("</tbody></table>\n")
                }
                is CalloutBlock -> sb.append("<div class=\"callout callout-${block.type}\"><strong>${block.type.uppercase()}:</strong> ${renderInlineHtml(block.text, themeHex)}</div>\n")
                is ToggleBlock -> sb.append("<details open><summary>${escapeHtml(block.title)}</summary><p>${renderInlineHtml(block.content, themeHex)}</p></details>\n")
                is DividerBlock -> sb.append("<hr style=\"border:none; border-top: 2px solid var(--primary); margin: 24px 0;\">\n")

                is ImageBlock -> {
                    val bmp = loadBitmapFromUrl(context, block.url)
                    val imgSrc = if (bmp != null) {
                        val bos = ByteArrayOutputStream()
                        bmp.compress(Bitmap.CompressFormat.JPEG, 85, bos)
                        val b64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
                        "data:image/jpeg;base64,$b64"
                    } else if (block.url.startsWith("http://") || block.url.startsWith("https://")) {
                        block.url
                    } else ""

                    if (imgSrc.isNotBlank()) {
                        val hasSide = block.widthPercent < 100 && (block.alignment == "left" || block.alignment == "right") && block.sideText.isNotBlank()
                        if (hasSide) {
                            val direction = if (block.alignment == "left") "row" else "row-reverse"
                            val imgPercent = block.widthPercent.coerceIn(25, 75)
                            val textPercent = 100 - imgPercent
                            sb.append("<div style=\"display:flex; flex-direction:$direction; align-items:flex-start; gap:16px; margin:16px 0;\">\n")
                            sb.append("  <div style=\"width:$imgPercent%; flex-shrink:0;\">\n")
                            sb.append("    <img class=\"note-img\" src=\"$imgSrc\" alt=\"${escapeHtml(block.alt)}\" style=\"width:100%; border-radius:8px; display:block;\">\n")
                            sb.append("  </div>\n")
                            sb.append("  <div style=\"width:$textPercent%; flex-grow:1; font-size:0.95rem; line-height:1.6; color:#1e293b;\">\n")
                            sb.append("    ${renderInlineHtml(block.sideText.replace("\n", "<br>"), themeHex)}\n")
                            sb.append("  </div>\n")
                            sb.append("</div>\n")
                        } else {
                            sb.append("<div style=\"text-align:${block.alignment}; margin:16px 0;\">\n")
                            sb.append("  <img class=\"note-img\" src=\"$imgSrc\" alt=\"${escapeHtml(block.alt)}\" style=\"width:${block.widthPercent}%;\">\n")
                            sb.append("</div>\n")
                        }
                    } else {
                        sb.append("<p style=\"color:#64748b; font-style:italic;\">🖼️ [Imagem: ${escapeHtml(block.alt)}]</p>\n")
                    }
                }

                is AudioBlock -> {
                    audioIndex++
                    val playerId = "citra_audio_$audioIndex"
                    val audioData = loadAudioBytes(context, block.url)
                    val audioSrc = if (audioData != null) {
                        val b64 = Base64.encodeToString(audioData.first, Base64.NO_WRAP)
                        "data:${audioData.second};base64,$b64"
                    } else if (block.url.startsWith("http://") || block.url.startsWith("https://")) {
                        block.url
                    } else ""

                    sb.append("<div class=\"citra-audio-card\">\n")
                    sb.append("  <div class=\"audio-header\">\n")
                    sb.append("    <div class=\"audio-title-group\">\n")
                    sb.append("      <div class=\"vinyl-art\">🎵</div>\n")
                    sb.append("      <div>\n")
                    sb.append("        <strong style=\"color:#f8fafc;\">${escapeHtml(block.title)}</strong>\n")
                    sb.append("        <div style=\"font-size:0.75rem; color:#94a3b8;\">Áudio Incorporado Citra • Arte Interna</div>\n")
                    sb.append("      </div>\n")
                    sb.append("    </div>\n")
                    sb.append("    <div class=\"wave-bars\">\n")
                    sb.append("      <div class=\"wave-bar\" style=\"animation-delay:0.1s;\"></div>\n")
                    sb.append("      <div class=\"wave-bar\" style=\"animation-delay:0.3s;\"></div>\n")
                    sb.append("      <div class=\"wave-bar\" style=\"animation-delay:0.2s;\"></div>\n")
                    sb.append("      <div class=\"wave-bar\" style=\"animation-delay:0.4s;\"></div>\n")
                    sb.append("      <div class=\"wave-bar\" style=\"animation-delay:0.15s;\"></div>\n")
                    sb.append("    </div>\n")
                    sb.append("  </div>\n")

                    if (audioSrc.isNotBlank()) {
                        sb.append("  <audio id=\"$playerId\" src=\"$audioSrc\" preload=\"metadata\"></audio>\n")
                        sb.append("  <div class=\"audio-controls\">\n")
                        sb.append("    <button class=\"play-btn\" onclick=\"toggleAudio('$playerId', this)\">▶</button>\n")
                        sb.append("    <input type=\"range\" class=\"scrubber\" id=\"prog_$playerId\" value=\"0\" min=\"0\" max=\"100\" onchange=\"seekAudio('$playerId', this.value)\">\n")
                        sb.append("    <span class=\"time-label\" id=\"time_$playerId\">00:00</span>\n")
                        sb.append("  </div>\n")
                    } else {
                        sb.append("  <div style=\"font-size:0.8rem; color:#94a3b8;\">Áudio não disponível localmente.</div>\n")
                    }
                    sb.append("</div>\n")
                }
            }
        }

        // Script for interactive audio player
        sb.append("<script>\n")
        sb.append("function toggleAudio(id, btn) {\n")
        sb.append("  var a = document.getElementById(id);\n")
        sb.append("  if (a.paused) { a.play(); btn.innerText = '⏸'; } else { a.pause(); btn.innerText = '▶'; }\n")
        sb.append("}\n")
        sb.append("function seekAudio(id, val) {\n")
        sb.append("  var a = document.getElementById(id);\n")
        sb.append("  if (a.duration) { a.currentTime = (val / 100) * a.duration; }\n")
        sb.append("}\n")
        sb.append("document.querySelectorAll('audio').forEach(function(a) {\n")
        sb.append("  var id = a.id;\n")
        sb.append("  var prog = document.getElementById('prog_' + id);\n")
        sb.append("  var time = document.getElementById('time_' + id);\n")
        sb.append("  a.addEventListener('timeupdate', function() {\n")
        sb.append("    if (a.duration) {\n")
        sb.append("      prog.value = (a.currentTime / a.duration) * 100;\n")
        sb.append("      var m = Math.floor(a.currentTime / 60);\n")
        sb.append("      var s = Math.floor(a.currentTime % 60);\n")
        sb.append("      time.innerText = (m < 10 ? '0' + m : m) + ':' + (s < 10 ? '0' + s : s);\n")
        sb.append("    }\n")
        sb.append("  });\n")
        sb.append("  a.addEventListener('ended', function() {\n")
        sb.append("    prog.value = 0;\n")
        sb.append("    var btn = a.parentElement.querySelector('.play-btn');\n")
        sb.append("    if (btn) btn.innerText = '▶';\n")
        sb.append("  });\n")
        sb.append("});\n")
        sb.append("</script>\n")

        sb.append("<div class=\"footer\">Citra • Produtividade, Foco &amp; Hábitos</div>\n")
        sb.append("</div>\n</body>\n</html>")
        return sb.toString()
    }

    /**
     * Synthesizes a beautiful graphical note card as a PNG image for sharing.
     * Incorporates theme colors and renders full embedded images.
     */
    fun shareAsImage(
        context: Context,
        noteTitle: String,
        noteCategory: String,
        blocks: List<VisualBlock>,
        themeColor: Int = Color.parseColor("#FF6F00")
    ): Uri {
        val width = 900
        val padding = 48f
        val contentWidth = (width - (padding * 2)).toInt()

        val titlePaint = TextPaint().apply {
            color = Color.parseColor("#0F172A")
            textSize = 34f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val metaPaint = TextPaint().apply {
            color = themeColor
            textSize = 15f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val bodyPaint = TextPaint().apply {
            color = Color.parseColor("#334155")
            textSize = 20f
            isAntiAlias = true
        }
        val checkedPaint = TextPaint().apply {
            color = Color.parseColor("#94A3B8")
            textSize = 20f
            isAntiAlias = true
        }
        val italicPaint = TextPaint().apply {
            color = Color.parseColor("#475569")
            textSize = 20f
            textSkewX = -0.22f
            isAntiAlias = true
        }
        val codeTextPaint = TextPaint().apply {
            color = Color.parseColor("#0F172A")
            textSize = 16f
            typeface = Typeface.MONOSPACE
            isAntiAlias = true
        }

        fun getHeadingPaint(level: Int): TextPaint = TextPaint().apply {
            color = if (level <= 2) themeColor else Color.parseColor("#0F172A")
            textSize = when (level) {
                1 -> 28f
                2 -> 24f
                3 -> 21f
                else -> 18f
            }
            isFakeBoldText = true
            isAntiAlias = true
        }

        val loadedBitmaps = mutableMapOf<String, Bitmap?>()
        for (b in blocks) {
            if (b is ImageBlock && b.url.isNotBlank() && !loadedBitmaps.containsKey(b.url)) {
                loadedBitmaps[b.url] = loadBitmapFromUrl(context, b.url)
            }
        }

        val titleLayout = createStaticLayout(noteTitle.ifBlank { "Sem Título" }, titlePaint, contentWidth)
        var measuredHeight = titleLayout.height + 10f + 32f + 24f

        for (block in blocks) {
            when (block) {
                is TextBlock -> {
                    if (block.text.isNotBlank()) {
                        val layout = createStaticLayout(block.text, bodyPaint, contentWidth)
                        measuredHeight += layout.height + 14f
                    } else {
                        measuredHeight += 12f
                    }
                }
                is HeadingBlock -> {
                    val hPaint = getHeadingPaint(block.level)
                    val layout = createStaticLayout(block.text.ifBlank { " " }, hPaint, contentWidth)
                    measuredHeight += layout.height + 18f
                }
                is ChecklistBlock -> {
                    val p = if (block.isChecked) checkedPaint else bodyPaint
                    val layout = createStaticLayout(block.text.ifBlank { " " }, p, contentWidth - 36)
                    measuredHeight += maxOf(28f, layout.height.toFloat()) + 10f
                }
                is BulletBlock -> {
                    val layout = createStaticLayout(block.text.ifBlank { " " }, bodyPaint, contentWidth - 28)
                    measuredHeight += maxOf(26f, layout.height.toFloat()) + 10f
                }
                is QuoteBlock -> {
                    val layout = createStaticLayout(block.text.ifBlank { " " }, italicPaint, contentWidth - 24)
                    measuredHeight += maxOf(32f, layout.height.toFloat()) + 16f
                }
                is CodeBlock -> {
                    val layout = createStaticLayout(block.code.ifBlank { " " }, codeTextPaint, contentWidth - 32)
                    measuredHeight += layout.height + 34f
                }
                is ImageBlock -> {
                    val bmp = loadedBitmaps[block.url]
                    if (bmp != null) {
                        val hasSide = block.widthPercent < 100 && (block.alignment == "left" || block.alignment == "right") && block.sideText.isNotBlank()
                        if (hasSide) {
                            val imgFraction = (block.widthPercent / 100f).coerceIn(0.25f, 0.75f)
                            val gap = 16f
                            val totalAvailable = contentWidth - gap
                            val imgW = totalAvailable * imgFraction
                            val textW = totalAvailable * (1f - imgFraction)
                            val scale = imgW / bmp.width.toFloat()
                            val drawH = bmp.height * scale
                            val textLayout = createStaticLayout(block.sideText, bodyPaint, textW.toInt().coerceAtLeast(60))
                            measuredHeight += maxOf(drawH, textLayout.height.toFloat()) + 18f
                        } else {
                            val maxW = (contentWidth.toFloat() * (block.widthPercent.toFloat() / 100f)).coerceAtLeast(100f)
                            val scale = maxW / bmp.width.toFloat()
                            val drawH = bmp.height * scale
                            measuredHeight += drawH + 18f
                        }
                    }
                }
                is CalloutBlock -> {
                    val layout = createStaticLayout(block.text.ifBlank { " " }, bodyPaint, contentWidth - 32)
                    measuredHeight += layout.height + 42f
                }
                is DividerBlock -> {
                    measuredHeight += 24f
                }
                is TableBlock -> {
                    val rowCount = block.rows.size + 1
                    measuredHeight += (rowCount * 32f) + 16f
                }
                is ToggleBlock -> {
                    val layout = createStaticLayout(block.title.ifBlank { " " }, bodyPaint, contentWidth - 28)
                    measuredHeight += layout.height + 14f
                }
                else -> {
                    measuredHeight += 16f
                }
            }
        }

        val totalHeight = (padding + 14f + measuredHeight + padding + 40f).toInt().coerceAtLeast(400)
        val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Gradient Background
        val bgPaint = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, totalHeight.toFloat(), Color.parseColor("#FFFFFF"), Color.parseColor("#F8FAFC"), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), totalHeight.toFloat(), bgPaint)

        // Top accent bar in Theme Color
        val accentBarPaint = Paint().apply { color = themeColor }
        canvas.drawRect(0f, 0f, width.toFloat(), 14f, accentBarPaint)

        var y = padding + 14f

        canvas.save()
        canvas.translate(padding, y)
        titleLayout.draw(canvas)
        canvas.restore()
        y += titleLayout.height + 10f

        val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        canvas.drawText("📁 $noteCategory   •   📅 $dateStr   •   🍊 Citra", padding, y + 14f, metaPaint)
        y += 32f

        val divPaint = Paint().apply { color = Color.parseColor("#E2E8F0"); strokeWidth = 2f }
        canvas.drawLine(padding, y, width - padding, y, divPaint)
        y += 24f

        for (block in blocks) {
            when (block) {
                is TextBlock -> {
                    if (block.text.isNotBlank()) {
                        val layout = createStaticLayout(block.text, bodyPaint, contentWidth)
                        canvas.save()
                        canvas.translate(padding, y)
                        layout.draw(canvas)
                        canvas.restore()
                        y += layout.height + 14f
                    } else {
                        y += 12f
                    }
                }
                is HeadingBlock -> {
                    val hPaint = getHeadingPaint(block.level)
                    val layout = createStaticLayout(block.text.ifBlank { " " }, hPaint, contentWidth)
                    canvas.save()
                    canvas.translate(padding, y)
                    layout.draw(canvas)
                    canvas.restore()
                    y += layout.height + 18f
                }
                is ChecklistBlock -> {
                    val boxPaint = Paint().apply {
                        color = if (block.isChecked) themeColor else Color.parseColor("#94A3B8")
                        style = if (block.isChecked) Paint.Style.FILL else Paint.Style.STROKE
                        strokeWidth = 2f
                    }
                    val p = if (block.isChecked) checkedPaint else bodyPaint
                    val layout = createStaticLayout(block.text.ifBlank { " " }, p, contentWidth - 36)
                    val itemH = maxOf(28f, layout.height.toFloat())

                    canvas.drawRoundRect(RectF(padding, y + 4f, padding + 20f, y + 24f), 4f, 4f, boxPaint)
                    if (block.isChecked) {
                        val checkMarkPaint = Paint().apply {
                            color = Color.WHITE
                            strokeWidth = 2.5f
                            style = Paint.Style.STROKE
                            isAntiAlias = true
                        }
                        canvas.drawLine(padding + 4f, y + 14f, padding + 9f, y + 19f, checkMarkPaint)
                        canvas.drawLine(padding + 9f, y + 19f, padding + 16f, y + 8f, checkMarkPaint)
                    }

                    canvas.save()
                    canvas.translate(padding + 28f, y + 2f)
                    layout.draw(canvas)
                    canvas.restore()

                    if (block.isChecked) {
                        val strikePaint = Paint().apply {
                            color = Color.parseColor("#94A3B8")
                            strokeWidth = 2f
                        }
                        for (i in 0 until layout.lineCount) {
                            val lineY = y + 2f + layout.getLineBaseline(i) - 4f
                            val lineLeft = padding + 28f + layout.getLineLeft(i)
                            val lineRight = padding + 28f + layout.getLineRight(i)
                            canvas.drawLine(lineLeft, lineY, lineRight, lineY, strikePaint)
                        }
                    }
                    y += itemH + 10f
                }
                is BulletBlock -> {
                    val bDot = Paint().apply { color = themeColor; isAntiAlias = true }
                    val layout = createStaticLayout(block.text.ifBlank { " " }, bodyPaint, contentWidth - 28)
                    val itemH = maxOf(26f, layout.height.toFloat())

                    canvas.drawCircle(padding + 8f, y + 12f, 4.5f, bDot)
                    canvas.save()
                    canvas.translate(padding + 22f, y)
                    layout.draw(canvas)
                    canvas.restore()
                    y += itemH + 10f
                }
                is QuoteBlock -> {
                    val layout = createStaticLayout(block.text.ifBlank { " " }, italicPaint, contentWidth - 24)
                    val itemH = maxOf(32f, layout.height.toFloat())
                    val qBar = Paint().apply { color = themeColor; strokeWidth = 4f }
                    canvas.drawLine(padding, y, padding, y + itemH, qBar)

                    canvas.save()
                    canvas.translate(padding + 16f, y)
                    layout.draw(canvas)
                    canvas.restore()
                    y += itemH + 16f
                }
                is CodeBlock -> {
                    val layout = createStaticLayout(block.code.ifBlank { " " }, codeTextPaint, contentWidth - 32)
                    val boxH = layout.height + 24f
                    val cBg = Paint().apply { color = Color.parseColor("#F1F5F9"); isAntiAlias = true }
                    canvas.drawRoundRect(RectF(padding, y, width - padding, y + boxH), 10f, 10f, cBg)

                    canvas.save()
                    canvas.translate(padding + 16f, y + 12f)
                    layout.draw(canvas)
                    canvas.restore()
                    y += boxH + 14f
                }
                is ImageBlock -> {
                    val bmp = loadedBitmaps[block.url]
                    if (bmp != null) {
                        val hasSide = block.widthPercent < 100 && (block.alignment == "left" || block.alignment == "right") && block.sideText.isNotBlank()
                        if (hasSide) {
                            val imgFraction = (block.widthPercent / 100f).coerceIn(0.25f, 0.75f)
                            val gap = 16f
                            val totalAvailable = contentWidth - gap
                            val imgW = totalAvailable * imgFraction
                            val textW = totalAvailable * (1f - imgFraction)
                            val scale = imgW / bmp.width.toFloat()
                            val drawW = bmp.width * scale
                            val drawH = bmp.height * scale

                            val textLayout = createStaticLayout(block.sideText, bodyPaint, textW.toInt().coerceAtLeast(60))
                            val rowH = maxOf(drawH, textLayout.height.toFloat())

                            val (imgX, textX) = if (block.alignment == "left") {
                                Pair(padding, padding + imgW + gap)
                            } else {
                                Pair(padding + textW + gap, padding)
                            }

                            val rect = RectF(imgX, y, imgX + drawW, y + drawH)
                            val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                            canvas.drawBitmap(bmp, null, rect, p)

                            canvas.save()
                            canvas.translate(textX, y)
                            textLayout.draw(canvas)
                            canvas.restore()

                            y += rowH + 18f
                        } else {
                            val maxW = (contentWidth.toFloat() * (block.widthPercent.toFloat() / 100f)).coerceAtLeast(100f)
                            val scale = maxW / bmp.width.toFloat()
                            val drawW = bmp.width * scale
                            val drawH = bmp.height * scale

                            val drawX = when (block.alignment) {
                                "right" -> width - padding - drawW
                                "center" -> padding + ((contentWidth - drawW) / 2f)
                                else -> padding
                            }

                            val rect = RectF(drawX, y, drawX + drawW, y + drawH)
                            val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                            canvas.drawBitmap(bmp, null, rect, p)
                            y += drawH + 18f
                        }
                    }
                }
                is CalloutBlock -> {
                    val (bgColor, borderColor) = when (block.type) {
                        "tip" -> Pair(Color.parseColor("#ECFDF5"), Color.parseColor("#10B981"))
                        "warning" -> Pair(Color.parseColor("#FFFBEB"), Color.parseColor("#F59E0B"))
                        "important" -> Pair(Color.parseColor("#FEF2F2"), Color.parseColor("#EF4444"))
                        else -> Pair(Color.parseColor("#EFF6FF"), Color.parseColor("#3B82F6"))
                    }
                    val layout = createStaticLayout(block.text.ifBlank { " " }, bodyPaint, contentWidth - 32)
                    val calloutH = layout.height + 30f
                    val cBg = Paint().apply { color = bgColor; isAntiAlias = true }
                    val cBorder = Paint().apply { color = borderColor; strokeWidth = 3f; style = Paint.Style.STROKE; isAntiAlias = true }

                    val rect = RectF(padding, y, width - padding, y + calloutH)
                    canvas.drawRoundRect(rect, 10f, 10f, cBg)
                    canvas.drawRoundRect(rect, 10f, 10f, cBorder)

                    canvas.save()
                    canvas.translate(padding + 16f, y + 14f)
                    layout.draw(canvas)
                    canvas.restore()
                    y += calloutH + 14f
                }
                is TableBlock -> {
                    val colCount = maxOf(1, block.headers.size)
                    val colW = contentWidth / colCount.toFloat()
                    val tBorder = Paint().apply { color = Color.parseColor("#CBD5E1"); strokeWidth = 1.5f; style = Paint.Style.STROKE }
                    val hBg = Paint().apply { color = Color.parseColor("#F1F5F9") }
                    val cellTextPaint = TextPaint().apply { color = Color.parseColor("#0F172A"); textSize = 16f; isAntiAlias = true }
                    val headerTextPaint = TextPaint().apply { color = Color.parseColor("#0F172A"); textSize = 16f; isFakeBoldText = true; isAntiAlias = true }

                    // Header row
                    canvas.drawRect(padding, y, width - padding, y + 32f, hBg)
                    canvas.drawRect(padding, y, width - padding, y + 32f, tBorder)
                    block.headers.forEachIndexed { i, h ->
                        canvas.drawText(h, padding + (i * colW) + 8f, y + 22f, headerTextPaint)
                    }
                    y += 32f

                    // Data rows
                    block.rows.forEach { row ->
                        canvas.drawRect(padding, y, width - padding, y + 28f, tBorder)
                        row.forEachIndexed { i, cell ->
                            canvas.drawText(cell, padding + (i * colW) + 8f, y + 20f, cellTextPaint)
                        }
                        y += 28f
                    }
                    y += 16f
                }
                is DividerBlock -> {
                    val linePaint = Paint().apply { color = Color.parseColor("#E2E8F0"); strokeWidth = 2f }
                    canvas.drawLine(padding, y + 10f, width - padding, y + 10f, linePaint)
                    y += 24f
                }
                is ToggleBlock -> {
                    val layout = createStaticLayout(block.title.ifBlank { " " }, bodyPaint, contentWidth - 28)
                    val tPaint = Paint().apply { color = themeColor; isAntiAlias = true }
                    canvas.drawText("▶", padding, y + 18f, tPaint)
                    canvas.save()
                    canvas.translate(padding + 22f, y)
                    layout.draw(canvas)
                    canvas.restore()
                    y += layout.height + 14f
                }
                else -> y += 12f
            }
        }

        val file = File(getExportDir(context), "${sanitizeFilename(noteTitle)}.png")
        val stream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 95, stream)
        stream.flush()
        stream.close()

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun exportToTxt(noteTitle: String, noteCategory: String, blocks: List<VisualBlock>): String {
        val sb = StringBuilder()
        sb.append("=====================================\n")
        sb.append(noteTitle.ifBlank { "Sem Título" }).append("\n")
        sb.append("Categoria: ").append(noteCategory).append("\n")
        sb.append("=====================================\n\n")

        for (block in blocks) {
            when (block) {
                is HeadingBlock -> sb.append("#".repeat(block.level)).append(" ").append(block.text).append("\n\n")
                is TextBlock -> sb.append(block.text).append("\n\n")
                is ChecklistBlock -> {
                    val mark = if (block.isChecked) "[X]" else "[ ]"
                    sb.append(mark).append(" ").append(block.text).append("\n")
                }
                is BulletBlock -> sb.append("  • ").append(block.text).append("\n")
                is QuoteBlock -> sb.append("  > ").append(block.text).append("\n\n")
                is CodeBlock -> sb.append("[CÓDIGO - ").append(block.language.uppercase()).append("]\n").append(block.code).append("\n\n")
                is MathBlock -> sb.append("[MATH: ").append(block.formula).append("]\n\n")
                is TableBlock -> {
                    sb.append("| ").append(block.headers.joinToString(" | ")).append(" |\n")
                    for (row in block.rows) {
                        sb.append("| ").append(row.joinToString(" | ")).append(" |\n")
                    }
                    sb.append("\n")
                }
                is CalloutBlock -> sb.append("[AVISO - ").append(block.type.uppercase()).append("]: ").append(block.text).append("\n\n")
                is ToggleBlock -> sb.append("▼ ").append(block.title).append("\n   ").append(block.content).append("\n\n")
                is DividerBlock -> sb.append("-------------------------------------\n\n")
                is ImageBlock -> sb.append("[IMAGEM: ").append(block.alt).append("]\n\n")
                is AudioBlock -> sb.append("[ÁUDIO: ").append(block.title).append("]\n\n")
            }
        }
        return sb.toString().trimEnd()
    }

    fun exportToMdFile(context: Context, noteTitle: String, blocks: List<VisualBlock>): Uri {
        val md = SmoothMarkdownConverter.blocksToMarkdown(blocks)
        val file = File(getExportDir(context), "${sanitizeFilename(noteTitle)}.md")
        file.writeText(md)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun exportToTxtFile(context: Context, noteTitle: String, noteCategory: String, blocks: List<VisualBlock>): Uri {
        val txt = exportToTxt(noteTitle, noteCategory, blocks)
        val file = File(getExportDir(context), "${sanitizeFilename(noteTitle)}.txt")
        file.writeText(txt)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun exportToHtmlFile(
        context: Context,
        noteTitle: String,
        noteCategory: String,
        blocks: List<VisualBlock>,
        themeColor: Int = Color.parseColor("#FF6F00")
    ): Uri {
        val html = exportToHtml(context, noteTitle, noteCategory, blocks, themeColor)
        val file = File(getExportDir(context), "${sanitizeFilename(noteTitle)}.html")
        file.writeText(html)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun shareUri(context: Context, uri: Uri, mimeType: String, title: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }

    private fun createStaticLayout(text: String, paint: TextPaint, width: Int): StaticLayout {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(10))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.15f)
                .setIncludePad(false)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(text, paint, width.coerceAtLeast(10), Layout.Alignment.ALIGN_NORMAL, 1.15f, 0f, false)
        }
    }
}
