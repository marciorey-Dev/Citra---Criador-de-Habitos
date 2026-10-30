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
import androidx.core.content.FileProvider
import com.example.ui.screens.organization.notes.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
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

    /**
     * Exports Note as a standard styled PDF document using Android's native PdfDocument.
     */
    fun exportToPdf(
        context: Context,
        noteTitle: String,
        noteCategory: String,
        blocks: List<VisualBlock>
    ): Uri {
        val document = PdfDocument()
        val pageWidth = 595 // Standard A4 width in PostScript points
        val pageHeight = 842 // Standard A4 height in PostScript points
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

        // 1. Header: Title, Category & Date
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
            color = Color.parseColor("#64748B")
            textSize = 10f
            isAntiAlias = true
        }
        val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        val metaText = "Categoria: $noteCategory  •  Exportado em $dateStr  •  HabitCraft"
        canvas.drawText(metaText, margin, y + 10f, metaPaint)
        y += 20f

        // Divider
        val dividerPaint = Paint().apply {
            color = Color.parseColor("#E2E8F0")
            strokeWidth = 1.5f
        }
        canvas.drawLine(margin, y, margin + contentWidth, y, dividerPaint)
        y += 16f

        // Text paints for various blocks
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

        // 2. Iterate through blocks
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
                        color = Color.parseColor("#1E293B")
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

                    // Draw checkbox square
                    val boxPaint = Paint().apply {
                        color = if (block.isChecked) Color.parseColor("#10B981") else Color.parseColor("#94A3B8")
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

                    // Indented bullet
                    val bulletPaint = Paint().apply {
                        color = Color.parseColor("#EA580C")
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

                    // Quote bar on left
                    val barPaint = Paint().apply {
                        color = Color.parseColor("#F97316")
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

                    // Header row
                    canvas.drawRect(margin, y, margin + contentWidth, y + rowHeight, tableHeaderPaint)
                    canvas.drawRect(margin, y, margin + contentWidth, y + rowHeight, cellBorderPaint)
                    for ((cIdx, header) in block.headers.withIndex()) {
                        val cx = margin + (cIdx * colWidth) + 6f
                        canvas.drawText(header, cx, y + 15f, tableTextPaint)
                    }
                    y += rowHeight

                    // Data rows
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
                    val calloutBgColor = when (block.type) {
                        "tip" -> Color.parseColor("#ECFDF5")
                        "warning" -> Color.parseColor("#FFFBEB")
                        "important" -> Color.parseColor("#FEF2F2")
                        else -> Color.parseColor("#EFF6FF")
                    }
                    val calloutBorderColor = when (block.type) {
                        "tip" -> Color.parseColor("#10B981")
                        "warning" -> Color.parseColor("#F59E0B")
                        "important" -> Color.parseColor("#EF4444")
                        else -> Color.parseColor("#3B82F6")
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
                    val tTitleLayout = createStaticLayout("▼ ${block.title}", titlePaint.apply { textSize = 13f }, contentWidth - 10)
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
                    checkNewPage(30f)
                    val imgLabel = if (block.alt.isNotBlank()) "🖼️ [Imagem: ${block.alt}]" else "🖼️ [Imagem]"
                    canvas.drawText(imgLabel, margin, y + 14f, metaPaint)
                    y += 24f
                }

                is AudioBlock -> {
                    checkNewPage(30f)
                    val audioLabel = "🎵 [Áudio: ${block.title}]"
                    canvas.drawText(audioLabel, margin, y + 14f, metaPaint)
                    y += 24f
                }
            }
        }

        document.finishPage(page)

        // Write to file
        val file = File(getExportDir(context), "${sanitizeFilename(noteTitle)}.pdf")
        val outputStream = FileOutputStream(file)
        document.writeTo(outputStream)
        outputStream.flush()
        outputStream.close()
        document.close()

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /**
     * Generates a complete standalone HTML document with modern responsive CSS.
     */
    fun exportToHtml(noteTitle: String, noteCategory: String, blocks: List<VisualBlock>): String {
        val title = noteTitle.ifBlank { "Sem Título" }
        val sb = StringBuilder()
        sb.append("<!DOCTYPE html>\n<html lang=\"pt-BR\">\n<head>\n")
        sb.append("<meta charset=\"utf-8\">\n")
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
        sb.append("<title>${escapeHtml(title)} - HabitCraft</title>\n")
        sb.append("<style>\n")
        sb.append("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; line-height: 1.6; color: #1e293b; max-width: 800px; margin: 40px auto; padding: 0 20px; background: #f8fafc; }\n")
        sb.append(".card { background: #ffffff; padding: 36px; border-radius: 18px; box-shadow: 0 4px 20px rgba(0,0,0,0.06); border: 1px solid #e2e8f0; }\n")
        sb.append("h1 { color: #0f172a; border-bottom: 2px solid #f97316; padding-bottom: 12px; margin-top: 0; }\n")
        sb.append(".meta { color: #64748b; font-size: 0.88rem; margin-bottom: 24px; display: flex; gap: 14px; }\n")
        sb.append(".badge { background: #ffedd5; color: #c2410c; padding: 3px 10px; border-radius: 12px; font-weight: bold; font-size: 0.8rem; }\n")
        sb.append("blockquote { border-left: 4px solid #f97316; margin: 16px 0; padding: 10px 18px; background: #fff7ed; border-radius: 0 10px 10px 0; color: #7c2d12; }\n")
        sb.append("pre { background: #0f172a; color: #f8fafc; padding: 16px; border-radius: 12px; overflow-x: auto; font-family: monospace; font-size: 0.9rem; }\n")
        sb.append("table { width: 100%; border-collapse: collapse; margin: 20px 0; }\n")
        sb.append("th, td { border: 1px solid #cbd5e1; padding: 10px 14px; text-align: left; }\n")
        sb.append("th { background: #f1f5f9; font-weight: bold; }\n")
        sb.append(".callout { padding: 14px 18px; border-radius: 12px; margin: 16px 0; border-left: 5px solid; }\n")
        sb.append(".callout-info { background: #eff6ff; border-color: #3b82f6; color: #1e3a8a; }\n")
        sb.append(".callout-tip { background: #ecfdf5; border-color: #10b981; color: #064e3b; }\n")
        sb.append(".callout-warning { background: #fffbeb; border-color: #f59e0b; color: #78350f; }\n")
        sb.append(".callout-important { background: #fef2f2; border-color: #ef4444; color: #7f1d1d; }\n")
        sb.append(".checklist-item { display: flex; align-items: center; gap: 10px; margin: 6px 0; }\n")
        sb.append(".checked { text-decoration: line-through; color: #94a3b8; }\n")
        sb.append("details { background: #f1f5f9; padding: 12px 16px; border-radius: 10px; margin: 12px 0; }\n")
        sb.append("summary { font-weight: bold; cursor: pointer; color: #0f172a; }\n")
        sb.append(".math { background: #f8fafc; font-family: 'Times New Roman', serif; font-size: 1.15rem; padding: 12px; border-radius: 8px; border: 1px dashed #cbd5e1; text-align: center; }\n")
        sb.append(".footer { margin-top: 36px; text-align: center; font-size: 0.8rem; color: #94a3b8; }\n")
        sb.append("</style>\n</head>\n<body>\n<div class=\"card\">\n")
        sb.append("<h1>${escapeHtml(title)}</h1>\n")
        sb.append("<div class=\"meta\"><span class=\"badge\">$noteCategory</span><span>Exportado do HabitCraft</span></div>\n")

        for (block in blocks) {
            when (block) {
                is HeadingBlock -> sb.append("<h${block.level}>${renderInlineHtml(block.text)}</h${block.level}>\n")
                is TextBlock -> if (block.text.isNotBlank()) sb.append("<p>${renderInlineHtml(block.text)}</p>\n")
                is ChecklistBlock -> {
                    val chk = if (block.isChecked) "checked" else ""
                    val cls = if (block.isChecked) "checked" else ""
                    sb.append("<div class=\"checklist-item\"><input type=\"checkbox\" $chk disabled><span class=\"$cls\">${renderInlineHtml(block.text)}</span></div>\n")
                }
                is BulletBlock -> sb.append("<ul><li>${renderInlineHtml(block.text)}</li></ul>\n")
                is QuoteBlock -> sb.append("<blockquote>${renderInlineHtml(block.text)}</blockquote>\n")
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
                is CalloutBlock -> sb.append("<div class=\"callout callout-${block.type}\"><strong>${block.type.uppercase()}:</strong> ${renderInlineHtml(block.text)}</div>\n")
                is ToggleBlock -> sb.append("<details open><summary>${escapeHtml(block.title)}</summary><p>${renderInlineHtml(block.content)}</p></details>\n")
                is DividerBlock -> sb.append("<hr>\n")
                is ImageBlock -> sb.append("<p><img src=\"${block.url}\" alt=\"${escapeHtml(block.alt)}\" style=\"max-width:100%; border-radius:10px;\"></p>\n")
                is AudioBlock -> sb.append("<p><audio controls src=\"${block.url}\">${escapeHtml(block.title)}</audio></p>\n")
            }
        }

        sb.append("<div class=\"footer\">HabitCraft • Produtividade & Notas</div>\n")
        sb.append("</div>\n</body>\n</html>")
        return sb.toString()
    }

    /**
     * Exports as structured plain text with clean indentation.
     */
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

    /**
     * Synthesizes a beautiful graphical note card as a PNG image for sharing.
     */
    fun shareAsImage(
        context: Context,
        noteTitle: String,
        noteCategory: String,
        blocks: List<VisualBlock>
    ): Uri {
        val width = 900
        val padding = 48f
        val contentWidth = (width - (padding * 2)).toInt()

        // Measure needed height
        val titlePaint = TextPaint().apply {
            color = Color.parseColor("#0F172A")
            textSize = 34f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val bodyPaint = TextPaint().apply {
            color = Color.parseColor("#334155")
            textSize = 20f
            isAntiAlias = true
        }
        val metaPaint = TextPaint().apply {
            color = Color.parseColor("#64748B")
            textSize = 15f
            isAntiAlias = true
        }

        var estimatedHeight = 160f
        for (block in blocks.take(25)) {
            when (block) {
                is TextBlock -> estimatedHeight += createStaticLayout(block.text, bodyPaint, contentWidth).height + 14f
                is HeadingBlock -> estimatedHeight += 40f
                is ChecklistBlock -> estimatedHeight += 32f
                is BulletBlock -> estimatedHeight += 28f
                is QuoteBlock -> estimatedHeight += 44f
                is CodeBlock -> estimatedHeight += 80f
                is MathBlock -> estimatedHeight += 48f
                else -> estimatedHeight += 30f
            }
        }
        val totalHeight = estimatedHeight.coerceIn(400f, 2600f).toInt()

        val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Gradient Background
        val bgPaint = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, totalHeight.toFloat(), Color.parseColor("#FFFFFF"), Color.parseColor("#FFF7ED"), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), totalHeight.toFloat(), bgPaint)

        // Orange accent bar on top
        val accentBarPaint = Paint().apply { color = Color.parseColor("#F97316") }
        canvas.drawRect(0f, 0f, width.toFloat(), 12f, accentBarPaint)

        var y = padding + 12f

        // Draw Title
        val titleLayout = createStaticLayout(noteTitle.ifBlank { "Sem Título" }, titlePaint, contentWidth)
        canvas.save()
        canvas.translate(padding, y)
        titleLayout.draw(canvas)
        canvas.restore()
        y += titleLayout.height + 10f

        // Meta
        val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        canvas.drawText("📁 $noteCategory   •   📅 $dateStr   •   🍊 HabitCraft", padding, y + 14f, metaPaint)
        y += 32f

        // Divider
        val divPaint = Paint().apply { color = Color.parseColor("#E2E8F0"); strokeWidth = 2f }
        canvas.drawLine(padding, y, width - padding, y, divPaint)
        y += 24f

        for (block in blocks.take(25)) {
            if (y > totalHeight - 60f) break
            when (block) {
                is TextBlock -> {
                    if (block.text.isNotBlank()) {
                        val layout = createStaticLayout(block.text, bodyPaint, contentWidth)
                        canvas.save()
                        canvas.translate(padding, y)
                        layout.draw(canvas)
                        canvas.restore()
                        y += layout.height + 12f
                    }
                }
                is HeadingBlock -> {
                    val hPaint = TextPaint().apply { color = Color.parseColor("#0F172A"); textSize = 26f; isFakeBoldText = true; isAntiAlias = true }
                    canvas.drawText(block.text, padding, y + 22f, hPaint)
                    y += 34f
                }
                is ChecklistBlock -> {
                    val boxPaint = Paint().apply {
                        color = if (block.isChecked) Color.parseColor("#10B981") else Color.parseColor("#94A3B8")
                        style = if (block.isChecked) Paint.Style.FILL else Paint.Style.STROKE
                        strokeWidth = 2f
                    }
                    canvas.drawRoundRect(RectF(padding, y + 4f, padding + 18f, y + 22f), 4f, 4f, boxPaint)
                    canvas.drawText(block.text, padding + 28f, y + 20f, bodyPaint)
                    y += 32f
                }
                is BulletBlock -> {
                    val bDot = Paint().apply { color = Color.parseColor("#EA580C") }
                    canvas.drawCircle(padding + 12f, y + 14f, 4f, bDot)
                    canvas.drawText(block.text, padding + 28f, y + 20f, bodyPaint)
                    y += 30f
                }
                is QuoteBlock -> {
                    val qBar = Paint().apply { color = Color.parseColor("#F97316"); strokeWidth = 4f }
                    canvas.drawLine(padding, y, padding, y + 36f, qBar)
                    canvas.drawText(block.text, padding + 16f, y + 24f, bodyPaint)
                    y += 44f
                }
                is CodeBlock -> {
                    val cBg = Paint().apply { color = Color.parseColor("#F1F5F9") }
                    canvas.drawRoundRect(RectF(padding, y, width - padding, y + 54f), 10f, 10f, cBg)
                    val cText = TextPaint().apply { color = Color.parseColor("#0F172A"); textSize = 16f; typeface = Typeface.MONOSPACE }
                    canvas.drawText(block.code.take(40), padding + 16f, y + 32f, cText)
                    y += 66f
                }
                else -> y += 10f
            }
        }

        // Save PNG to cache
        val file = File(getExportDir(context), "${sanitizeFilename(noteTitle)}.png")
        val stream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 95, stream)
        stream.flush()
        stream.close()

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
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

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    private fun renderInlineHtml(text: String): String {
        var result = escapeHtml(text)
        // Bold: **text**
        result = result.replace(Regex("\\*\\*(.*?)\\*\\*"), "<strong>$1</strong>")
        // Italic: *text* or _text_
        result = result.replace(Regex("(?<!\\*)\\*(?!\\*)(.*?)(?<!\\*)\\*(?!\\*)"), "<em>$1</em>")
        result = result.replace(Regex("_(.*?)_"), "<em>$1</em>")
        // Strikethrough: ~~text~~
        result = result.replace(Regex("~~(.*?)~~"), "<del>$1</del>")
        // Inline code: `text`
        result = result.replace(Regex("`(.*?)`"), "<code style=\"background:#e2e8f0; padding:2px 6px; border-radius:4px;\">$1</code>")
        // Links: [label](url) or url|label
        result = result.replace(Regex("\\[(.*?)\\]\\((.*?)\\)"), "<a href=\"$2\" style=\"color:#f97316; font-weight:bold;\">$1 🔗</a>")
        result = result.replace(Regex("(\\S+)\\|([\\S ]+)"), "<a href=\"$1\" style=\"color:#f97316; font-weight:bold;\">$2 🔗</a>")
        return result
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

    fun exportToHtmlFile(context: Context, noteTitle: String, noteCategory: String, blocks: List<VisualBlock>): Uri {
        val html = exportToHtml(noteTitle, noteCategory, blocks)
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
}
