package com.example.ui.screens.organization.notes

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

data class CursorLineInfo(
    val lineIndex: Int,
    val lineStart: Int,
    val lineEnd: Int,
    val lineContent: String
)

data class MediaTokenInfo(
    val isAudio: Boolean,
    val rawMatch: String,
    val titleOrAlt: String,
    val url: String,
    val widthPercent: Int = 100,
    val alignment: String = "center",
    val sideText: String = "",
    val startOffset: Int = 0,
    val endOffset: Int = 0
)

/**
 * Obsidian Live Preview Engine for Jetpack Compose:
 * - Dynamic cursor line detection
 * - Active line vs Inactive line styling
 * - Seamless inline and block formatting injection
 * - Media token detection for interactive widgets
 */
object ObsidianMarkdownEngine {

    fun findCursorLine(text: String, cursorOffset: Int): CursorLineInfo {
        val safeCursor = cursorOffset.coerceIn(0, text.length)
        val lines = text.split("\n")
        var currentOffset = 0
        for ((idx, line) in lines.withIndex()) {
            val lineLength = line.length
            val lineEnd = currentOffset + lineLength
            if (safeCursor in currentOffset..lineEnd || (idx == lines.lastIndex && safeCursor >= lineEnd)) {
                return CursorLineInfo(
                    lineIndex = idx,
                    lineStart = currentOffset,
                    lineEnd = lineEnd,
                    lineContent = line
                )
            }
            currentOffset = lineEnd + 1 // +1 for the newline character
        }
        return CursorLineInfo(0, 0, text.length, text)
    }

    /**
     * Builds an AnnotatedString that powers the Obsidian Live Preview:
     * - Active Line (where cursor is): reveals markdown syntax in clean, highlighted colors
     * - Inactive Lines: applies rich typography (H1-H5 scale, subtle prefix markers, list & quote emphasis)
     * - Both: bold, italic, strikethrough, underline, highlight, inline code, and links
     */
    fun buildLivePreviewAnnotatedString(
        text: String,
        selection: TextRange,
        primaryColor: Color,
        textColor: Color,
        isDarkBg: Boolean
    ): AnnotatedString {
        if (text.isEmpty()) return AnnotatedString("")

        val builder = AnnotatedString.Builder(text)
        val cursor = selection.start.coerceIn(0, text.length)
        val cursorLine = findCursorLine(text, cursor)

        val lines = text.split("\n")
        var offset = 0

        for ((idx, line) in lines.withIndex()) {
            val lineStart = offset
            val lineEnd = offset + line.length
            val isActive = (idx == cursorLine.lineIndex)
            val trimmed = line.trimStart()
            val indentLen = line.length - trimmed.length

            when {
                // H1 - H5 Headers
                trimmed.startsWith("##### ") -> {
                    val prefixLen = indentLen + 6
                    applyHeaderStyle(builder, lineStart, lineEnd, prefixLen, 13, isActive, primaryColor, textColor)
                }
                trimmed.startsWith("#### ") -> {
                    val prefixLen = indentLen + 5
                    applyHeaderStyle(builder, lineStart, lineEnd, prefixLen, 15, isActive, primaryColor, textColor)
                }
                trimmed.startsWith("### ") -> {
                    val prefixLen = indentLen + 4
                    applyHeaderStyle(builder, lineStart, lineEnd, prefixLen, 17, isActive, primaryColor, textColor)
                }
                trimmed.startsWith("## ") -> {
                    val prefixLen = indentLen + 3
                    applyHeaderStyle(builder, lineStart, lineEnd, prefixLen, 20, isActive, primaryColor, textColor)
                }
                trimmed.startsWith("# ") -> {
                    val prefixLen = indentLen + 2
                    applyHeaderStyle(builder, lineStart, lineEnd, prefixLen, 24, isActive, primaryColor, textColor)
                }

                // Checklists
                trimmed.startsWith("- [ ] ") -> {
                    val prefixEnd = (lineStart + indentLen + 6).coerceAtMost(lineEnd)
                    builder.addStyle(
                        SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold),
                        lineStart + indentLen,
                        prefixEnd
                    )
                }
                trimmed.startsWith("- [x] ", ignoreCase = true) -> {
                    val prefixEnd = (lineStart + indentLen + 6).coerceAtMost(lineEnd)
                    builder.addStyle(
                        SpanStyle(color = Color(0xFF10B981), fontWeight = FontWeight.Bold),
                        lineStart + indentLen,
                        prefixEnd
                    )
                    // Mark finished item with strikethrough and lighter text
                    if (prefixEnd < lineEnd) {
                        builder.addStyle(
                            SpanStyle(textDecoration = TextDecoration.LineThrough, color = textColor.copy(alpha = 0.5f)),
                            prefixEnd,
                            lineEnd
                        )
                    }
                }

                // Bullet Lists
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    val prefixEnd = (lineStart + indentLen + 2).coerceAtMost(lineEnd)
                    builder.addStyle(
                        SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold),
                        lineStart + indentLen,
                        prefixEnd
                    )
                }

                // Quotes
                trimmed.startsWith("> ") -> {
                    val prefixEnd = (lineStart + indentLen + 2).coerceAtMost(lineEnd)
                    builder.addStyle(
                        SpanStyle(color = primaryColor.copy(alpha = 0.7f), fontWeight = FontWeight.Bold),
                        lineStart + indentLen,
                        prefixEnd
                    )
                    builder.addStyle(
                        SpanStyle(fontStyle = FontStyle.Italic, color = textColor.copy(alpha = 0.85f)),
                        prefixEnd,
                        lineEnd
                    )
                }

                // Code block or Math delimiters
                trimmed.startsWith("```") || trimmed.startsWith("$$") -> {
                    builder.addStyle(
                        SpanStyle(fontFamily = FontFamily.Monospace, color = primaryColor, background = if (isDarkBg) Color(0xFF1E293B) else Color(0xFFF1F5F9)),
                        lineStart,
                        lineEnd
                    )
                }

                // Dividers
                trimmed == "---" || trimmed == "***" -> {
                    builder.addStyle(
                        SpanStyle(color = primaryColor.copy(alpha = 0.5f), fontWeight = FontWeight.ExtraBold),
                        lineStart,
                        lineEnd
                    )
                }
            }

            // Apply inline formatting across this line
            applyInlineFormattingToRange(builder, text, lineStart, lineEnd, primaryColor, textColor, isDarkBg)

            offset = lineEnd + 1 // +1 for newline
        }

        return builder.toAnnotatedString()
    }

    private fun applyHeaderStyle(
        builder: AnnotatedString.Builder,
        lineStart: Int,
        lineEnd: Int,
        prefixLen: Int,
        spSize: Int,
        isActive: Boolean,
        primaryColor: Color,
        textColor: Color
    ) {
        val prefixEnd = (lineStart + prefixLen).coerceAtMost(lineEnd)
        // Prefix (#, ##, etc.)
        builder.addStyle(
            SpanStyle(
                fontSize = if (isActive) spSize.sp else (spSize - 4).sp,
                color = if (isActive) primaryColor else primaryColor.copy(alpha = 0.35f),
                fontWeight = FontWeight.Bold
            ),
            lineStart,
            prefixEnd
        )
        // Header Text
        if (prefixEnd < lineEnd) {
            builder.addStyle(
                SpanStyle(
                    fontSize = spSize.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryColor
                ),
                prefixEnd,
                lineEnd
            )
        }
    }

    private fun applyInlineFormattingToRange(
        builder: AnnotatedString.Builder,
        fullText: String,
        start: Int,
        end: Int,
        primaryColor: Color,
        textColor: Color,
        isDarkBg: Boolean
    ) {
        if (start >= end) return
        val chunk = fullText.substring(start, end)

        // 1. Bold (**text**)
        Regex("\\*\\*(.*?)\\*\\*").findAll(chunk).forEach { match ->
            val s = start + match.range.first
            val e = start + match.range.last + 1
            builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold), s, e)
        }

        // 2. Italic (*text*)
        Regex("(?<!\\*)\\*(?!\\*)(.*?)(?<!\\*)\\*(?!\\*)").findAll(chunk).forEach { match ->
            val s = start + match.range.first
            val e = start + match.range.last + 1
            builder.addStyle(SpanStyle(fontStyle = FontStyle.Italic), s, e)
        }

        // 3. Strikethrough (~~text~~)
        Regex("~~(.*?)~~").findAll(chunk).forEach { match ->
            val s = start + match.range.first
            val e = start + match.range.last + 1
            builder.addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), s, e)
        }

        // 4. Underline (<u>text</u>)
        Regex("<u>(.*?)</u>").findAll(chunk).forEach { match ->
            val s = start + match.range.first
            val e = start + match.range.last + 1
            builder.addStyle(SpanStyle(textDecoration = TextDecoration.Underline), s, e)
        }

        // 5. Highlight (==text==)
        val highlightColor = if (isDarkBg) Color(0xFF6B5800) else Color(0xFFFFF176)
        Regex("==(.*?)==").findAll(chunk).forEach { match ->
            val s = start + match.range.first
            val e = start + match.range.last + 1
            builder.addStyle(SpanStyle(background = highlightColor), s, e)
        }

        // 6. Inline code (`code`)
        val codeBg = if (isDarkBg) Color(0xFF1E293B) else Color(0xFFF1F5F9)
        Regex("`([^`]+)`").findAll(chunk).forEach { match ->
            val s = start + match.range.first
            val e = start + match.range.last + 1
            builder.addStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = codeBg,
                    color = primaryColor
                ),
                s,
                e
            )
        }

        // 7. Links ([label](url))
        Regex("\\[(.*?)\\]\\((.*?)\\)").findAll(chunk).forEach { match ->
            val s = start + match.range.first
            val e = start + match.range.last + 1
            builder.addStyle(
                SpanStyle(
                    color = Color(0xFF3B82F6),
                    textDecoration = TextDecoration.Underline
                ),
                s,
                e
            )
        }

        // 8. Audio or Image token
        Regex("(!?\\[(audio:)?(.*?)\\]\\(.*?\\))").findAll(chunk).forEach { match ->
            val s = start + match.range.first
            val e = start + match.range.last + 1
            val isAudio = match.value.contains("[audio:") || match.value.contains("![audio:")
            val pillBg = if (isAudio) (if (isDarkBg) Color(0xFF064E3B) else Color(0xFFD1FAE5)) else (if (isDarkBg) Color(0xFF1E3A8A) else Color(0xFFDBEAFE))
            val pillFg = if (isAudio) Color(0xFF10B981) else Color(0xFF3B82F6)
            builder.addStyle(
                SpanStyle(
                    background = pillBg,
                    color = pillFg,
                    fontWeight = FontWeight.SemiBold
                ),
                s,
                e
            )
        }
    }

    /**
     * Encloses the current selection with prefix and suffix.
     * If nothing is selected, inserts prefix and suffix and puts cursor in the middle.
     */
    fun wrapSelection(value: TextFieldValue, prefix: String, suffix: String): TextFieldValue {
        val text = value.text
        val selection = value.selection

        return if (selection.start != selection.end) {
            val min = minOf(selection.start, selection.end)
            val max = maxOf(selection.start, selection.end)
            val selected = text.substring(min, max)
            val newText = text.substring(0, min) + prefix + selected + suffix + text.substring(max)
            val newSelection = TextRange(min + prefix.length, min + prefix.length + selected.length)
            value.copy(text = newText, selection = newSelection)
        } else {
            val pos = selection.start.coerceIn(0, text.length)
            val newText = text.substring(0, pos) + prefix + suffix + text.substring(pos)
            val newPos = pos + prefix.length
            value.copy(text = newText, selection = TextRange(newPos, newPos))
        }
    }

    /**
     * Toggles line prefix (such as "# ", "## ", "- [ ] ", "- ", "> ").
     * If the line already begins with this prefix, removes it.
     * If it begins with another header or list symbol, replaces it cleanly.
     */
    fun toggleLinePrefix(value: TextFieldValue, targetPrefix: String): TextFieldValue {
        val text = value.text
        val cursor = value.selection.start.coerceIn(0, text.length)
        val info = findCursorLine(text, cursor)
        val line = info.lineContent
        val trimmed = line.trimStart()
        val indent = line.substring(0, line.length - trimmed.length)

        // Known prefixes that can be replaced
        val knownPrefixes = listOf(
            "##### ", "#### ", "### ", "## ", "# ",
            "- [ ] ", "- [x] ", "- [X] ",
            "- ", "* ", "> "
        )

        val existingPrefix = knownPrefixes.firstOrNull { trimmed.startsWith(it) }

        val newLine = when {
            existingPrefix == targetPrefix -> {
                // Remove prefix (toggle off)
                indent + trimmed.removePrefix(targetPrefix)
            }
            existingPrefix != null -> {
                // Replace prefix with new target
                indent + targetPrefix + trimmed.removePrefix(existingPrefix)
            }
            else -> {
                // Add prefix to line
                indent + targetPrefix + trimmed
            }
        }

        val newText = text.substring(0, info.lineStart) + newLine + text.substring(info.lineEnd)
        val diff = newLine.length - line.length
        val newCursor = (cursor + diff).coerceIn(0, newText.length)

        return value.copy(text = newText, selection = TextRange(newCursor, newCursor))
    }

    /**
     * Inserts a media token (image, audio, table, etc.) on its own clean line.
     */
    fun insertMediaToken(value: TextFieldValue, token: String): TextFieldValue {
        val text = value.text
        val pos = value.selection.start.coerceIn(0, text.length)

        val needsLeadingNewline = pos > 0 && text[pos - 1] != '\n'
        val needsTrailingNewline = pos < text.length && text[pos] != '\n'

        val sb = StringBuilder()
        if (needsLeadingNewline) sb.append("\n")
        sb.append(token)
        if (needsTrailingNewline) sb.append("\n") else sb.append("\n")

        val insertion = sb.toString()
        val newText = text.substring(0, pos) + insertion + text.substring(pos)
        val newPos = pos + insertion.length

        return value.copy(text = newText, selection = TextRange(newPos, newPos))
    }

    /**
     * Finds all media tokens (Audio and Images) within the markdown text
     * so that interactive widgets (WhatsApp player, resizable side-by-side images) can be shown.
     */
    fun parseMediaTokens(markdown: String): List<MediaTokenInfo> {
        val list = mutableListOf<MediaTokenInfo>()

        // 1. Audio: ![audio:Title](url) or [audio:Title](url)
        val audioRegex = Regex("(!?\\[audio:(.*?)\\]\\((.*?)\\))")
        audioRegex.findAll(markdown).forEach { match ->
            val raw = match.value
            val title = match.groupValues[2]
            val url = match.groupValues[3]
            list.add(
                MediaTokenInfo(
                    isAudio = true,
                    rawMatch = raw,
                    titleOrAlt = title,
                    url = url,
                    startOffset = match.range.first,
                    endOffset = match.range.last + 1
                )
            )
        }

        // 2. Images: ![alt|size|align|side:text](url) or ![align|size](url) or ![alt](url)
        val imgRegex = Regex("(!\\[(?!audio:)(.*?)\\]\\((.*?)\\))")
        imgRegex.findAll(markdown).forEach { match ->
            val raw = match.value
            val meta = match.groupValues[2]
            val url = match.groupValues[3]
            val parts = meta.split("|")

            var width = 100
            var align = "center"
            var alt = ""
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

            list.add(
                MediaTokenInfo(
                    isAudio = false,
                    rawMatch = raw,
                    titleOrAlt = alt,
                    url = url,
                    widthPercent = width.coerceIn(25, 100),
                    alignment = align,
                    sideText = side,
                    startOffset = match.range.first,
                    endOffset = match.range.last + 1
                )
            )
        }

        return list.sortedBy { it.startOffset }
    }
}
