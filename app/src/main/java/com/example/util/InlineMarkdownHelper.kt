package com.example.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

object InlineMarkdownHelper {

    val HighlightYellow = Color(0xFFFEF08A)
    val HighlightGreen = Color(0xFFBBF7D0)
    val HighlightPink = Color(0xFFFBCFE8)
    val HighlightBlue = Color(0xFFBFDBFE)
    val HighlightOrange = Color(0xFFFED7AA)

    fun toggleOrWrap(
        text: String,
        prefix: String,
        suffix: String
    ): String {
        val trimmed = text.trim()
        return if (trimmed.startsWith(prefix) && trimmed.endsWith(suffix) && trimmed.length >= prefix.length + suffix.length) {
            trimmed.removePrefix(prefix).removeSuffix(suffix)
        } else {
            "$prefix$text$suffix"
        }
    }

    fun wrapSelection(
        text: String,
        selectionStart: Int,
        selectionEnd: Int,
        prefix: String,
        suffix: String
    ): Pair<String, Int> {
        val start = selectionStart.coerceIn(0, text.length)
        val end = selectionEnd.coerceIn(0, text.length)
        val minPos = minOf(start, end)
        val maxPos = maxOf(start, end)

        return if (minPos == maxPos) {
            val newText = text.substring(0, minPos) + prefix + suffix + text.substring(minPos)
            val newCursor = minPos + prefix.length
            Pair(newText, newCursor)
        } else {
            val selected = text.substring(minPos, maxPos)
            if (selected.startsWith(prefix) && selected.endsWith(suffix) && selected.length >= prefix.length + suffix.length) {
                // Toggle off
                val unwrapped = selected.removePrefix(prefix).removeSuffix(suffix)
                val newText = text.substring(0, minPos) + unwrapped + text.substring(maxPos)
                val newCursor = minPos + unwrapped.length
                Pair(newText, newCursor)
            } else {
                val newText = text.substring(0, minPos) + prefix + selected + suffix + text.substring(maxPos)
                val newCursor = minPos + prefix.length + selected.length + suffix.length
                Pair(newText, newCursor)
            }
        }
    }

    /**
     * Parses inline markdown markers and returns a beautifully styled AnnotatedString
     * including bold, italic, code, strikethrough, underline, highlight, and links.
     */
    fun parseInlineFormatting(
        rawText: String,
        isDark: Boolean,
        linkColor: Color = Color(0xFFF97316)
    ): AnnotatedString {
        if (rawText.isEmpty()) return AnnotatedString("")

        // Strip markers sequentially while recording spans
        val codeBgColor = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
        val baseTextColor = if (isDark) Color.White else Color(0xFF1E293B)

        return buildAnnotatedString {
            // Links: [label](url) or url|label
            // Strikethrough: ~~text~~
            // Bold: **text**
            // Italic: *text* or _text_
            // Inline code: `text`
            // Underline: <u>text</u>
            // Highlight: ==text==

            var cursor = 0
            val textLen = rawText.length

            while (cursor < textLen) {
                // 1. Link format: url|label or [label](url)
                if (rawText.startsWith("[", cursor) && rawText.indexOf("](", cursor) != -1) {
                    val closeLabel = rawText.indexOf("](", cursor)
                    val closeUrl = rawText.indexOf(")", closeLabel)
                    if (closeUrl != -1) {
                        val label = rawText.substring(cursor + 1, closeLabel)
                        val url = rawText.substring(closeLabel + 2, closeUrl)
                        pushStringAnnotation(tag = "LINK", annotation = url)
                        withStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline)) {
                            append(label)
                            withStyle(SpanStyle(color = linkColor.copy(alpha = 0.5f))) {
                                append(" 🔗")
                            }
                        }
                        pop()
                        cursor = closeUrl + 1
                        continue
                    }
                }

                // Pipe link: url|label
                val pipeMatch = Regex("^([a-zA-Z0-9.:/_-]+)\\|([a-zA-Z0-9À-ÿ _\\-]+)").find(rawText.substring(cursor))
                if (pipeMatch != null && pipeMatch.range.first == 0) {
                    val url = pipeMatch.groupValues[1]
                    val label = pipeMatch.groupValues[2]
                    pushStringAnnotation(tag = "LINK", annotation = url)
                    withStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline)) {
                        append(label)
                        withStyle(SpanStyle(color = linkColor.copy(alpha = 0.5f))) {
                            append(" 🔗")
                        }
                    }
                    pop()
                    cursor += pipeMatch.value.length
                    continue
                }

                // 2. Bold: **text**
                if (rawText.startsWith("**", cursor)) {
                    val end = rawText.indexOf("**", cursor + 2)
                    if (end != -1) {
                        val inner = rawText.substring(cursor + 2, end)
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(inner)
                        }
                        cursor = end + 2
                        continue
                    }
                }

                // 3. Strikethrough: ~~text~~
                if (rawText.startsWith("~~", cursor)) {
                    val end = rawText.indexOf("~~", cursor + 2)
                    if (end != -1) {
                        val inner = rawText.substring(cursor + 2, end)
                        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = baseTextColor.copy(alpha = 0.6f))) {
                            append(inner)
                        }
                        cursor = end + 2
                        continue
                    }
                }

                // 4. Highlight: ==text==
                if (rawText.startsWith("==", cursor)) {
                    val end = rawText.indexOf("==", cursor + 2)
                    if (end != -1) {
                        val inner = rawText.substring(cursor + 2, end)
                        withStyle(SpanStyle(background = HighlightYellow, color = Color(0xFF713F12), fontWeight = FontWeight.SemiBold)) {
                            append(inner)
                        }
                        cursor = end + 2
                        continue
                    }
                }

                // 5. Underline: <u>text</u>
                if (rawText.startsWith("<u>", cursor)) {
                    val end = rawText.indexOf("</u>", cursor + 3)
                    if (end != -1) {
                        val inner = rawText.substring(cursor + 3, end)
                        withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                            append(inner)
                        }
                        cursor = end + 4
                        continue
                    }
                }

                // 6. Inline code: `text` or <>text</> or <code>text</code>
                if (rawText.startsWith("<>", cursor)) {
                    val end = rawText.indexOf("</>", cursor + 2)
                    if (end != -1) {
                        val inner = rawText.substring(cursor + 2, end)
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBgColor, color = if (isDark) Color(0xFF38BDF8) else Color(0xFF0F172A))) {
                            append(" $inner ")
                        }
                        cursor = end + 3
                        continue
                    }
                }
                if (rawText.startsWith("<code>", cursor)) {
                    val end = rawText.indexOf("</code>", cursor + 6)
                    if (end != -1) {
                        val inner = rawText.substring(cursor + 6, end)
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBgColor, color = if (isDark) Color(0xFF38BDF8) else Color(0xFF0F172A))) {
                            append(" $inner ")
                        }
                        cursor = end + 7
                        continue
                    }
                }
                if (rawText.startsWith("`", cursor) && !rawText.startsWith("```", cursor)) {
                    val end = rawText.indexOf("`", cursor + 1)
                    if (end != -1) {
                        val inner = rawText.substring(cursor + 1, end)
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBgColor, color = if (isDark) Color(0xFF38BDF8) else Color(0xFF0F172A))) {
                            append(" $inner ")
                        }
                        cursor = end + 1
                        continue
                    }
                }

                // 7. Italic: *text* or _text_
                if (rawText.startsWith("*", cursor) && !rawText.startsWith("**", cursor)) {
                    val end = rawText.indexOf("*", cursor + 1)
                    if (end != -1) {
                        val inner = rawText.substring(cursor + 1, end)
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(inner)
                        }
                        cursor = end + 1
                        continue
                    }
                }
                if (rawText.startsWith("_", cursor)) {
                    val end = rawText.indexOf("_", cursor + 1)
                    if (end != -1) {
                        val inner = rawText.substring(cursor + 1, end)
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(inner)
                        }
                        cursor = end + 1
                        continue
                    }
                }

                // Normal character
                append(rawText[cursor])
                cursor++
            }
        }
    }
}
