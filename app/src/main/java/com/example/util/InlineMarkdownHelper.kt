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

    private data class InlineStyleContext(
        val isBold: Boolean = false,
        val isItalic: Boolean = false,
        val isStrikethrough: Boolean = false,
        val isUnderline: Boolean = false,
        val isHighlight: Boolean = false,
        val isCode: Boolean = false,
        val isQuote: Boolean = false,
        val linkUrl: String? = null
    )

    fun normalizeUrl(rawUrl: String): String {
        val clean = rawUrl.trim()
        return when {
            clean.startsWith("http://", ignoreCase = true) || clean.startsWith("https://", ignoreCase = true) -> clean
            clean.startsWith("www.", ignoreCase = true) -> "https://$clean"
            clean.contains(".") && !clean.contains(" ") -> "https://$clean"
            else -> clean
        }
    }

    /**
     * Parses inline markdown markers with full synergistic combination of styles:
     * Negrito + Itálico + Tachado + Sublinhado + Citação + Links externos.
     */
    fun parseInlineFormatting(
        rawText: String,
        isDark: Boolean,
        linkColor: Color = Color(0xFFF97316)
    ): AnnotatedString {
        if (rawText.isEmpty()) return AnnotatedString("")

        val codeBgColor = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
        val baseTextColor = if (isDark) Color.White else Color(0xFF1E293B)

        return buildAnnotatedString {
            parseFragment(
                text = rawText,
                context = InlineStyleContext(),
                isDark = isDark,
                linkColor = linkColor,
                codeBgColor = codeBgColor,
                baseTextColor = baseTextColor,
                builder = this
            )
        }
    }

    private fun parseFragment(
        text: String,
        context: InlineStyleContext,
        isDark: Boolean,
        linkColor: Color,
        codeBgColor: Color,
        baseTextColor: Color,
        builder: AnnotatedString.Builder
    ) {
        var cursor = 0
        val textLen = text.length

        while (cursor < textLen) {
            // 1. LaTeX Block Math: $$formula$$
            if (text.startsWith("$$", cursor)) {
                val end = text.indexOf("$$", cursor + 2)
                if (end != -1) {
                    val inner = text.substring(cursor + 2, end)
                    builder.append(LatexFormulaRenderer.formatAnnotatedBlock(inner, isDark, linkColor))
                    cursor = end + 2
                    continue
                }
            }

            // 2. LaTeX Inline Math: $formula$
            if (text.startsWith("$", cursor) && !text.startsWith("$$", cursor)) {
                val end = text.indexOf("$", cursor + 1)
                if (end != -1 && end > cursor + 1 && !text.substring(cursor + 1, end).contains("\n")) {
                    val inner = text.substring(cursor + 1, end)
                    builder.append(LatexFormulaRenderer.formatAnnotatedInline(inner, isDark))
                    cursor = end + 1
                    continue
                }
            }

            // 3. Markdown Link: [label](url)
            if (text.startsWith("[", cursor) && text.indexOf("](", cursor) != -1) {
                val closeLabel = text.indexOf("](", cursor)
                val closeUrl = text.indexOf(")", closeLabel)
                if (closeUrl != -1) {
                    val label = text.substring(cursor + 1, closeLabel)
                    val rawUrl = text.substring(closeLabel + 2, closeUrl)
                    val fullUrl = normalizeUrl(rawUrl)

                    builder.pushStringAnnotation(tag = "LINK", annotation = fullUrl)
                    parseFragment(
                        text = label,
                        context = context.copy(linkUrl = fullUrl, isUnderline = true),
                        isDark = isDark,
                        linkColor = linkColor,
                        codeBgColor = codeBgColor,
                        baseTextColor = baseTextColor,
                        builder = builder
                    )
                    // Append external link icon in theme link color
                    builder.withStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.SemiBold)) {
                        builder.append(" 🔗")
                    }
                    builder.pop()
                    cursor = closeUrl + 1
                    continue
                }
            }

            // 4. Pipe Link: url|label
            val pipeMatch = Regex("^([a-zA-Z0-9.:/_-]+)\\|([^\n]+)").find(text.substring(cursor))
            if (pipeMatch != null && pipeMatch.range.first == 0 && (pipeMatch.groupValues[1].contains(".") || pipeMatch.groupValues[1].contains("/"))) {
                val rawUrl = pipeMatch.groupValues[1]
                val label = pipeMatch.groupValues[2].substringBefore(" ").ifEmpty { pipeMatch.groupValues[2] }
                val fullUrl = normalizeUrl(rawUrl)

                builder.pushStringAnnotation(tag = "LINK", annotation = fullUrl)
                parseFragment(
                    text = label,
                    context = context.copy(linkUrl = fullUrl, isUnderline = true),
                    isDark = isDark,
                    linkColor = linkColor,
                    codeBgColor = codeBgColor,
                    baseTextColor = baseTextColor,
                    builder = builder
                )
                builder.withStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.SemiBold)) {
                    builder.append(" 🔗")
                }
                builder.pop()
                cursor += rawUrl.length + 1 + label.length
                continue
            }

            // 5. Bare URLs: https://... or www....
            val bareUrlMatch = Regex("^(https?://[a-zA-Z0-9._~:/?#\\[\\]@!$&'()*+,;=-]+|www\\.[a-zA-Z0-9._~:/?#\\[\\]@!$&'()*+,;=-]+)").find(text.substring(cursor))
            if (bareUrlMatch != null && bareUrlMatch.range.first == 0 && context.linkUrl == null) {
                val matchedUrl = bareUrlMatch.value
                val fullUrl = normalizeUrl(matchedUrl)
                builder.pushStringAnnotation(tag = "LINK", annotation = fullUrl)
                builder.withStyle(
                    SpanStyle(
                        color = linkColor,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = TextDecoration.Underline
                    )
                ) {
                    builder.append(matchedUrl)
                    builder.append(" 🔗")
                }
                builder.pop()
                cursor += matchedUrl.length
                continue
            }

            // 6. Bold: **text**
            if (text.startsWith("**", cursor)) {
                val end = text.indexOf("**", cursor + 2)
                if (end != -1) {
                    val inner = text.substring(cursor + 2, end)
                    parseFragment(
                        text = inner,
                        context = context.copy(isBold = true),
                        isDark = isDark,
                        linkColor = linkColor,
                        codeBgColor = codeBgColor,
                        baseTextColor = baseTextColor,
                        builder = builder
                    )
                    cursor = end + 2
                    continue
                }
            }

            // 7. Strikethrough: ~~text~~
            if (text.startsWith("~~", cursor)) {
                val end = text.indexOf("~~", cursor + 2)
                if (end != -1) {
                    val inner = text.substring(cursor + 2, end)
                    parseFragment(
                        text = inner,
                        context = context.copy(isStrikethrough = true),
                        isDark = isDark,
                        linkColor = linkColor,
                        codeBgColor = codeBgColor,
                        baseTextColor = baseTextColor,
                        builder = builder
                    )
                    cursor = end + 2
                    continue
                }
            }

            // 8. Underline: <u>text</u> or __text__
            if (text.startsWith("<u>", cursor)) {
                val end = text.indexOf("</u>", cursor + 3)
                if (end != -1) {
                    val inner = text.substring(cursor + 3, end)
                    parseFragment(
                        text = inner,
                        context = context.copy(isUnderline = true),
                        isDark = isDark,
                        linkColor = linkColor,
                        codeBgColor = codeBgColor,
                        baseTextColor = baseTextColor,
                        builder = builder
                    )
                    cursor = end + 4
                    continue
                }
            }
            if (text.startsWith("__", cursor)) {
                val end = text.indexOf("__", cursor + 2)
                if (end != -1) {
                    val inner = text.substring(cursor + 2, end)
                    parseFragment(
                        text = inner,
                        context = context.copy(isUnderline = true),
                        isDark = isDark,
                        linkColor = linkColor,
                        codeBgColor = codeBgColor,
                        baseTextColor = baseTextColor,
                        builder = builder
                    )
                    cursor = end + 2
                    continue
                }
            }

            // 9. Highlight: ==text==
            if (text.startsWith("==", cursor)) {
                val end = text.indexOf("==", cursor + 2)
                if (end != -1) {
                    val inner = text.substring(cursor + 2, end)
                    parseFragment(
                        text = inner,
                        context = context.copy(isHighlight = true),
                        isDark = isDark,
                        linkColor = linkColor,
                        codeBgColor = codeBgColor,
                        baseTextColor = baseTextColor,
                        builder = builder
                    )
                    cursor = end + 2
                    continue
                }
            }

            // 10. Quote / Citação inline: <blockquote>text</blockquote> or <q>text</q>
            if (text.startsWith("<blockquote>", cursor)) {
                val end = text.indexOf("</blockquote>", cursor + 12)
                if (end != -1) {
                    val inner = text.substring(cursor + 12, end)
                    parseFragment(
                        text = inner,
                        context = context.copy(isQuote = true),
                        isDark = isDark,
                        linkColor = linkColor,
                        codeBgColor = codeBgColor,
                        baseTextColor = baseTextColor,
                        builder = builder
                    )
                    cursor = end + 13
                    continue
                }
            }
            if (text.startsWith("<q>", cursor)) {
                val end = text.indexOf("</q>", cursor + 3)
                if (end != -1) {
                    val inner = text.substring(cursor + 3, end)
                    parseFragment(
                        text = inner,
                        context = context.copy(isQuote = true),
                        isDark = isDark,
                        linkColor = linkColor,
                        codeBgColor = codeBgColor,
                        baseTextColor = baseTextColor,
                        builder = builder
                    )
                    cursor = end + 4
                    continue
                }
            }

            // 11. Inline code: `text` or <>text</> or <code>text</code>
            if (text.startsWith("<>", cursor)) {
                val end = text.indexOf("</>", cursor + 2)
                if (end != -1) {
                    val inner = text.substring(cursor + 2, end)
                    appendStyledToken(" $inner ", context.copy(isCode = true), isDark, linkColor, codeBgColor, baseTextColor, builder)
                    cursor = end + 3
                    continue
                }
            }
            if (text.startsWith("<code>", cursor)) {
                val end = text.indexOf("</code>", cursor + 6)
                if (end != -1) {
                    val inner = text.substring(cursor + 6, end)
                    appendStyledToken(" $inner ", context.copy(isCode = true), isDark, linkColor, codeBgColor, baseTextColor, builder)
                    cursor = end + 7
                    continue
                }
            }
            if (text.startsWith("`", cursor) && !text.startsWith("```", cursor)) {
                val end = text.indexOf("`", cursor + 1)
                if (end != -1) {
                    val inner = text.substring(cursor + 1, end)
                    appendStyledToken(" $inner ", context.copy(isCode = true), isDark, linkColor, codeBgColor, baseTextColor, builder)
                    cursor = end + 1
                    continue
                }
            }

            // 12. Italic: *text* or _text_
            if (text.startsWith("*", cursor) && !text.startsWith("**", cursor)) {
                val end = text.indexOf("*", cursor + 1)
                if (end != -1) {
                    val inner = text.substring(cursor + 1, end)
                    parseFragment(
                        text = inner,
                        context = context.copy(isItalic = true),
                        isDark = isDark,
                        linkColor = linkColor,
                        codeBgColor = codeBgColor,
                        baseTextColor = baseTextColor,
                        builder = builder
                    )
                    cursor = end + 1
                    continue
                }
            }
            if (text.startsWith("_", cursor) && !text.startsWith("__", cursor)) {
                val end = text.indexOf("_", cursor + 1)
                if (end != -1) {
                    val inner = text.substring(cursor + 1, end)
                    parseFragment(
                        text = inner,
                        context = context.copy(isItalic = true),
                        isDark = isDark,
                        linkColor = linkColor,
                        codeBgColor = codeBgColor,
                        baseTextColor = baseTextColor,
                        builder = builder
                    )
                    cursor = end + 1
                    continue
                }
            }

            // Normal text character (or span of non-special characters)
            val nextSpecial = findNextSpecialChar(text, cursor)
            val chunk = text.substring(cursor, nextSpecial)
            appendStyledToken(chunk, context, isDark, linkColor, codeBgColor, baseTextColor, builder)
            cursor = nextSpecial
        }
    }

    private fun findNextSpecialChar(text: String, start: Int): Int {
        for (i in start until text.length) {
            val c = text[i]
            if (c == '*' || c == '_' || c == '~' || c == '`' || c == '<' || c == '=' || c == '[' || c == '$' || c == '|' ||
                (c == 'w' && text.startsWith("www.", i)) ||
                (c == 'h' && (text.startsWith("http://", i) || text.startsWith("https://", i)))
            ) {
                return if (i == start) start + 1 else i
            }
        }
        return text.length
    }

    private fun appendStyledToken(
        token: String,
        context: InlineStyleContext,
        isDark: Boolean,
        linkColor: Color,
        codeBgColor: Color,
        baseTextColor: Color,
        builder: AnnotatedString.Builder
    ) {
        val decorations = mutableListOf<TextDecoration>()
        if (context.isStrikethrough) decorations.add(TextDecoration.LineThrough)
        if (context.isUnderline || context.linkUrl != null) decorations.add(TextDecoration.Underline)
        val textDecoration = if (decorations.isNotEmpty()) TextDecoration.combine(decorations) else null

        val spanStyle = SpanStyle(
            fontWeight = if (context.isBold) FontWeight.Bold else if (context.isHighlight) FontWeight.SemiBold else null,
            fontStyle = if (context.isItalic || context.isQuote) FontStyle.Italic else null,
            textDecoration = textDecoration,
            color = when {
                context.linkUrl != null -> linkColor
                context.isHighlight -> Color(0xFF713F12)
                context.isStrikethrough -> baseTextColor.copy(alpha = 0.65f)
                context.isQuote -> if (isDark) Color(0xFFCBD5E1) else Color(0xFF475569)
                else -> null
            },
            background = when {
                context.isHighlight -> HighlightYellow
                context.isCode -> codeBgColor
                else -> Color.Transparent
            },
            fontFamily = if (context.isCode) FontFamily.Monospace else null
        )

        builder.withStyle(spanStyle) {
            builder.append(token)
        }
    }
}
