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
    override val id: String = UUID.randomUUID().toString(),
    val title: String = "Áudio Gravado/Importado",
    val url: String = "",
    val volume: Float = 1.0f,
    val isLoop: Boolean = false
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
        if (content.isBlank()) {
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

                // Audio (![audio:title](url))
                Regex("^!\\[audio:(.*?)\\]\\((.*?)\\)$").matches(trimmed) -> {
                    val match = Regex("^!\\[audio:(.*?)\\]\\((.*?)\\)$").find(trimmed)
                    if (match != null) {
                        blocks.add(AudioBlock(title = match.groupValues[1], url = match.groupValues[2]))
                    }
                    i++
                    continue
                }

                // Image (![alt|width|align](url) or ![alt](url))
                Regex("^!\\[(.*?)\\]\\((.*?)\\)$").matches(trimmed) -> {
                    val match = Regex("^!\\[(.*?)\\]\\((.*?)\\)$").find(trimmed)
                    if (match != null) {
                        val rawAlt = match.groupValues[1]
                        val parts = rawAlt.split("|")
                        val alt = parts.getOrNull(0) ?: ""
                        val width = parts.getOrNull(1)?.toIntOrNull() ?: 100
                        val align = parts.getOrNull(2) ?: "center"
                        blocks.add(ImageBlock(alt = alt, url = match.groupValues[2], widthPercent = width, alignment = align))
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

                // Normal paragraph / text
                else -> {
                    if (trimmed.isNotBlank()) {
                        blocks.add(TextBlock(text = line))
                    } else if (i > 0 && lines[i - 1].trim().isBlank() && blocks.isNotEmpty() && blocks.last() !is TextBlock) {
                        // Only add explicit blank block if multiple empty lines exist intentionally
                        blocks.add(TextBlock(text = ""))
                    }
                    i++
                }
            }
        }

        // Clean up redundant consecutive empty text blocks to guarantee no compounding spaces
        val cleanBlocks = mutableListOf<VisualBlock>()
        for (b in blocks) {
            if (b is TextBlock && b.text.isBlank()) {
                if (cleanBlocks.isNotEmpty() && cleanBlocks.last() is TextBlock && (cleanBlocks.last() as TextBlock).text.isBlank()) {
                    continue // Skip consecutive empty blocks
                }
            }
            cleanBlocks.add(b)
        }

        if (cleanBlocks.isEmpty()) {
            cleanBlocks.add(TextBlock(text = ""))
        }

        return cleanBlocks
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
                    sb.append("$prefix ${block.text}\n\n")
                }
                is TextBlock -> {
                    sb.append("${block.text}\n\n")
                }
                is ChecklistBlock -> {
                    val mark = if (block.isChecked) "x" else " "
                    sb.append("- [$mark] ${block.text}\n")
                }
                is BulletBlock -> {
                    sb.append("- ${block.text}\n")
                }
                is TableBlock -> {
                    sb.append("| ${block.headers.joinToString(" | ")} |\n")
                    sb.append("| ${block.headers.map { "---" }.joinToString(" | ")} |\n")
                    for (row in block.rows) {
                        sb.append("| ${row.joinToString(" | ")} |\n")
                    }
                    sb.append("\n")
                }
                is QuoteBlock -> {
                    sb.append("> ${block.text}\n\n")
                }
                is CodeBlock -> {
                    sb.append("```${block.language}\n${block.code}\n```\n\n")
                }
                is MathBlock -> {
                    sb.append("$$ ${block.formula} $$\n\n")
                }
                is DividerBlock -> {
                    sb.append("---\n\n")
                }
                is ImageBlock -> {
                    sb.append("![${block.alt}|${block.widthPercent}|${block.alignment}](${block.url})\n\n")
                }
                is AudioBlock -> {
                    sb.append("![audio:${block.title}](${block.url})\n\n")
                }
                is CalloutBlock -> {
                    sb.append("> [!${block.type.uppercase()}] ${block.text}\n\n")
                }
                is ToggleBlock -> {
                    sb.append("<details><summary>${block.title}</summary>\n${block.content}\n</details>\n\n")
                }
            }
        }
        return sb.toString().trimEnd()
    }

    private fun parseTableLines(lines: List<String>): TableBlock? {
        val nonDelimiterRows = lines.mapNotNull { row ->
            val cells = row.split("|")
                .map { it.trim() }
                .filterIndexed { index, _ -> index > 0 && index < row.split("|").size - 1 }
            if (cells.all { it.matches(Regex("^-+$")) }) null else cells
        }
        if (nonDelimiterRows.isEmpty()) return null
        val headers = nonDelimiterRows.first()
        val dataRows = nonDelimiterRows.drop(1)
        return TableBlock(headers = headers, rows = if (dataRows.isEmpty()) listOf(headers.map { "" }) else dataRows)
    }
}
