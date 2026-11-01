package com.example.ui.screens.organization.notes

import java.util.UUID

sealed interface VisualBlock {
    val id: String
}

data class HeadingBlock(
    override val id: String = UUID.randomUUID().toString(),
    val level: Int = 1, // 1 to 5
    val text: String = ""
) : VisualBlock

data class TextBlock(
    override val id: String = UUID.randomUUID().toString(),
    val text: String = ""
) : VisualBlock

data class ChecklistBlock(
    override val id: String = UUID.randomUUID().toString(),
    val isChecked: Boolean = false,
    val text: String = ""
) : VisualBlock

data class BulletBlock(
    override val id: String = UUID.randomUUID().toString(),
    val text: String = ""
) : VisualBlock

data class TableBlock(
    override val id: String = UUID.randomUUID().toString(),
    val headers: List<String> = listOf("Item", "Status"),
    val rows: List<List<String>> = listOf(listOf("Exemplo", "Concluído"))
) : VisualBlock

data class QuoteBlock(
    override val id: String = UUID.randomUUID().toString(),
    val text: String = ""
) : VisualBlock

data class CodeBlock(
    override val id: String = UUID.randomUUID().toString(),
    val code: String = "",
    val language: String = "kotlin"
) : VisualBlock

data class MathBlock(
    override val id: String = UUID.randomUUID().toString(),
    val formula: String = "E = mc^2"
) : VisualBlock

data class DividerBlock(
    override val id: String = UUID.randomUUID().toString()
) : VisualBlock

data class ImageBlock(
    override val id: String = UUID.randomUUID().toString(),
    val alt: String = "",
    val url: String = "",
    val widthPercent: Int = 100, // 25, 50, 75, 100
    val alignment: String = "center", // "left", "center", "right"
    val sideText: String = "" // text next to image when 25%, 50%, or 75%
) : VisualBlock

data class AudioBlock(
    val title: String = "Áudio Gravado/Importado",
    val url: String = "",
    val volume: Float = 1.0f,
    val isLoop: Boolean = false,
    override val id: String = if (url.isNotBlank()) "audio_${url.hashCode()}" else UUID.randomUUID().toString()
) : VisualBlock

data class CalloutBlock(
    override val id: String = UUID.randomUUID().toString(),
    val type: String = "info", // "info", "tip", "warning", "important"
    val text: String = ""
) : VisualBlock

data class ToggleBlock(
    override val id: String = UUID.randomUUID().toString(),
    val title: String = "Resumo / Tópico Recolhível",
    val content: String = "",
    val isExpanded: Boolean = true
) : VisualBlock

object SmoothMarkdownConverter {

    /**
     * Parses standard Markdown into smooth, user-friendly VisualBlocks
     * (Hiding raw syntax like ###, |---| etc.)
     */
    fun markdownToBlocks(content: String): List<VisualBlock> {
        if (content.isEmpty()) {
            return listOf(TextBlock(text = ""))
        }

        val blocks = mutableListOf<VisualBlock>()
        val lines = content.lines()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            when {
                // Code block (``` ... ```)
                trimmed.startsWith("```") -> {
                    val lang = trimmed.removePrefix("```").trim()
                    val codeLines = mutableListOf<String>()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith("```")) {
                        codeLines.add(lines[i])
                        i++
                    }
                    if (i < lines.size) i++ // skip closing ```
                    blocks.add(CodeBlock(code = codeLines.joinToString("\n"), language = if (lang.isBlank()) "kotlin" else lang))
                    continue
                }

                // Math block ($$ ... $$)
                trimmed.startsWith("$$") -> {
                    val mathLines = mutableListOf<String>()
                    if (trimmed.endsWith("$$") && trimmed.length > 2) {
                        mathLines.add(trimmed.removePrefix("$$").removeSuffix("$$").trim())
                        i++
                    } else {
                        i++
                        while (i < lines.size && !lines[i].trim().endsWith("$$")) {
                            mathLines.add(lines[i])
                            i++
                        }
                        if (i < lines.size) {
                            mathLines.add(lines[i].trim().removeSuffix("$$").trim())
                            i++
                        }
                    }
                    blocks.add(MathBlock(formula = mathLines.joinToString(" ").trim()))
                    continue
                }

                // Table (| Col 1 | Col 2 |)
                trimmed.startsWith("|") && trimmed.endsWith("|") && trimmed.count { it == '|' } >= 2 -> {
                    val tableLines = mutableListOf<String>()
                    while (i < lines.size && lines[i].trim().startsWith("|") && lines[i].trim().endsWith("|")) {
                        tableLines.add(lines[i].trim())
                        i++
                    }
                    val parsed = parseTableLines(tableLines)
                    if (parsed != null) {
                        blocks.add(parsed)
                    }
                    continue
                }

                // Divider (--- or ***)
                trimmed == "---" || trimmed == "***" || trimmed == "___" -> {
                    blocks.add(DividerBlock())
                    i++
                    continue
                }

                // Audio (![audio:title|vol:..|loop](url) or [audio:...](url))
                Regex("^!?\\[audio:(.*?)\\]\\((.*?)\\)$").matches(trimmed) -> {
                    val match = Regex("^!?\\[audio:(.*?)\\]\\((.*?)\\)$").find(trimmed)
                    if (match != null) {
                        val rawTitle = match.groupValues[1]
                        val parts = rawTitle.split("|").map { it.trim() }
                        var title = parts.getOrNull(0) ?: "Áudio"
                        var volume = 1.0f
                        var isLoop = false
                        for (p in parts.drop(1)) {
                            when {
                                p.startsWith("vol:") -> volume = (p.removePrefix("vol:").toFloatOrNull() ?: 100f) / 100f
                                p == "loop" -> isLoop = true
                            }
                        }
                        blocks.add(AudioBlock(title = title, url = match.groupValues[2], volume = volume.coerceIn(0f, 1f), isLoop = isLoop))
                    }
                    i++
                    continue
                }

                // Image (![alt|width|align|side:text](url))
                Regex("^!\\[(.*?)\\]\\((.*?)\\)$").matches(trimmed) -> {
                    val match = Regex("^!\\[(.*?)\\]\\((.*?)\\)$").find(trimmed)
                    if (match != null) {
                        val rawAlt = match.groupValues[1]
                        val parts = rawAlt.split("|")
                        var alt = ""
                        var width = 100
                        var align = "center"
                        var side = ""
                        for (rawPart in parts) {
                            val part = rawPart.trim()
                            when {
                                part.endsWith("%") -> width = part.removeSuffix("%").toIntOrNull() ?: 100
                                part.toIntOrNull() != null -> width = part.toInt()
                                part.equals("left", ignoreCase = true) || part.equals("right", ignoreCase = true) || part.equals("center", ignoreCase = true) -> align = part.lowercase()
                                rawPart.startsWith("side:") || part.startsWith("side:") -> {
                                    val rawSide = if (rawPart.startsWith("side:")) rawPart.removePrefix("side:") else part.removePrefix("side:")
                                    side = rawSide.replace("<br>", "\n").replace("<br/>", "\n").replace("<br />", "\n").replace("&#124;", "|")
                                }
                                else -> alt = part
                            }
                        }
                        blocks.add(ImageBlock(alt = alt, url = match.groupValues[2], widthPercent = width.coerceIn(25, 100), alignment = align, sideText = side))
                    }
                    i++
                    continue
                }

                // Callouts (> [!INFO], > [!TIP], > [!WARNING], > [!IMPORTANT], > [!NOTE])
                trimmed.startsWith("> [!") -> {
                    val endBracket = trimmed.indexOf("]")
                    if (endBracket != -1) {
                        val typeName = trimmed.substring(4, endBracket).lowercase()
                        val type = when (typeName) {
                            "tip" -> "tip"
                            "warning" -> "warning"
                            "important", "danger" -> "important"
                            else -> "info"
                        }
                        val text = trimmed.substring(endBracket + 1).trim()
                        blocks.add(CalloutBlock(type = type, text = text))
                        i++
                        continue
                    }
                }

                // Collapsible Toggle (<details><summary>...</summary>...</details>)
                trimmed.startsWith("<details>") -> {
                    var summary = "Resumo"
                    val contentLines = mutableListOf<String>()
                    i++
                    while (i < lines.size && !lines[i].trim().contains("</details>")) {
                        val l = lines[i].trim()
                        if (l.startsWith("<summary>") && l.endsWith("</summary>")) {
                            summary = l.removePrefix("<summary>").removeSuffix("</summary>").trim()
                        } else {
                            contentLines.add(lines[i])
                        }
                        i++
                    }
                    if (i < lines.size) i++ // skip </details>
                    blocks.add(ToggleBlock(title = summary, content = contentLines.joinToString("\n").trim()))
                    continue
                }

                // Headers
                trimmed.startsWith("##### ") -> {
                    blocks.add(HeadingBlock(level = 5, text = trimmed.removePrefix("##### ")))
                    i++
                    continue
                }
                trimmed.startsWith("#### ") -> {
                    blocks.add(HeadingBlock(level = 4, text = trimmed.removePrefix("#### ")))
                    i++
                    continue
                }
                trimmed.startsWith("### ") -> {
                    blocks.add(HeadingBlock(level = 3, text = trimmed.removePrefix("### ")))
                    i++
                    continue
                }
                trimmed.startsWith("## ") -> {
                    blocks.add(HeadingBlock(level = 2, text = trimmed.removePrefix("## ")))
                    i++
                    continue
                }
                trimmed.startsWith("# ") -> {
                    blocks.add(HeadingBlock(level = 1, text = trimmed.removePrefix("# ")))
                    i++
                    continue
                }

                // Checklist
                trimmed.startsWith("- [x] ") || trimmed.startsWith("- [X] ") -> {
                    blocks.add(ChecklistBlock(isChecked = true, text = trimmed.substring(6)))
                    i++
                    continue
                }
                trimmed.startsWith("- [ ] ") -> {
                    blocks.add(ChecklistBlock(isChecked = false, text = trimmed.substring(6)))
                    i++
                    continue
                }

                // Bullet
                trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ") -> {
                    blocks.add(BulletBlock(text = trimmed.substring(2)))
                    i++
                    continue
                }

                // Quote
                trimmed.startsWith("> ") -> {
                    blocks.add(QuoteBlock(text = trimmed.removePrefix("> ")))
                    i++
                    continue
                }

                // Normal paragraph / text (preserves empty lines and text lines faithfully)
                else -> {
                    blocks.add(TextBlock(text = line))
                    i++
                }
            }
        }

        return if (blocks.isEmpty()) listOf(TextBlock(text = "")) else blocks
    }

    /**
     * Converts visual blocks back into standard, compliant Markdown
     * for saving in database, exporting, and multi-app interoperability.
     */
    fun blocksToMarkdown(blocks: List<VisualBlock>): String {
        val sb = StringBuilder()
        for ((index, block) in blocks.withIndex()) {
            when (block) {
                is HeadingBlock -> {
                    val prefix = "#".repeat(block.level.coerceIn(1, 5))
                    sb.append("$prefix ${block.text}\n")
                }
                is TextBlock -> {
                    sb.append("${block.text}\n")
                }
                is ChecklistBlock -> {
                    val mark = if (block.isChecked) "x" else " "
                    sb.append("- [$mark] ${block.text}\n")
                }
                is BulletBlock -> {
                    sb.append("- ${block.text}\n")
                }
                is TableBlock -> {
                    val safeHeaders = block.headers.map { it.replace("\n", "<br>").replace("|", "&#124;") }
                    sb.append("| ${safeHeaders.joinToString(" | ")} |\n")
                    sb.append("| ${block.headers.map { "---" }.joinToString(" | ")} |\n")
                    for (row in block.rows) {
                        val safeRow = row.map { it.replace("\n", "<br>").replace("|", "&#124;") }
                        sb.append("| ${safeRow.joinToString(" | ")} |\n")
                    }
                }
                is QuoteBlock -> {
                    sb.append("> ${block.text}\n")
                }
                is CodeBlock -> {
                    sb.append("```${block.language}\n${block.code}\n```\n")
                }
                is MathBlock -> {
                    sb.append("$$ ${block.formula} $$\n")
                }
                is DividerBlock -> {
                    sb.append("---\n")
                }
                is ImageBlock -> {
                    val encodedSide = block.sideText.replace("\n", "<br>").replace("|", "&#124;")
                    val sidePart = if (block.sideText.isNotEmpty()) "|side:$encodedSide" else ""
                    val altPart = if (block.alt.isNotEmpty()) "${block.alt}|" else ""
                    sb.append("![$altPart${block.widthPercent}%|${block.alignment}$sidePart](${block.url})\n")
                }
                is AudioBlock -> {
                    val volPart = if (block.volume < 0.99f || block.volume > 1.01f) "|vol:${(block.volume * 100).toInt()}" else ""
                    val loopPart = if (block.isLoop) "|loop" else ""
                    sb.append("![audio:${block.title}$volPart$loopPart](${block.url})\n")
                }
                is CalloutBlock -> {
                    sb.append("> [!${block.type.uppercase()}] ${block.text}\n")
                }
                is ToggleBlock -> {
                    sb.append("<details><summary>${block.title}</summary>\n${block.content}\n</details>\n")
                }
            }
        }
        return sb.toString().removeSuffix("\n")
    }

    private fun parseTableLines(lines: List<String>): TableBlock? {
        val nonDelimiterRows = lines.mapNotNull { row ->
            val rawCells = row.split("|")
            if (rawCells.size < 2) return@mapNotNull null
            val cells = rawCells.subList(1, rawCells.size - 1).map { rawCell ->
                var c = rawCell
                if (c.startsWith(" ")) c = c.substring(1)
                if (c.endsWith(" ")) c = c.substring(0, c.length - 1)
                c.replace("<br>", "\n").replace("<br/>", "\n").replace("<br />", "\n").replace("&#124;", "|")
            }
            if (cells.all { it.trim().matches(Regex("^-+$")) }) null else cells
        }
        if (nonDelimiterRows.isEmpty()) return null
        val headers = nonDelimiterRows.first()
        val dataRows = nonDelimiterRows.drop(1)
        return TableBlock(headers = headers, rows = if (dataRows.isEmpty()) listOf(headers.map { "" }) else dataRows)
    }
}
