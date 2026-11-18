package com.example.ui.screens.organization.notes

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.sp
import com.example.util.InlineMarkdownHelper

enum class ListStyleType {
    BULLET,
    ARABIC,
    ROMAN,
    ALPHA
}

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

    private const val MAX_INLINE_LINE_CHARS = 2_500
    private const val LARGE_DOC_CHAR_THRESHOLD = 12_000
    private const val LARGE_DOC_LINE_THRESHOLD = 300
    private const val CURSOR_WINDOW_CHARS = 6_000
    private const val MAX_TOTAL_STYLE_SPANS = 3_500
    private const val MAX_MEDIA_TOKENS = 40
    private const val MAX_DATA_URI_LENGTH = 512

    private const val FLAG_BOLD = 1
    private const val FLAG_ITALIC = 2
    private const val FLAG_STRIKE = 4
    private const val FLAG_UNDERLINE = 8
    private const val FLAG_HIGHLIGHT = 16
    private const val FLAG_QUOTE = 32
    private const val FLAG_HIDDEN = 64

    private val MEDIA_TOKEN_HIGHLIGHT_REGEX = Regex("(!?\\[(?:audio:)?[^\\]\\n]*\\]\\([^)\\n]*\\))")
    private val AUDIO_TOKEN_REGEX = Regex("(!?\\[audio:([^\\]\\n]*)\\]\\(([^)\\n]*)\\))")
    private val IMAGE_TOKEN_REGEX = Regex("(!\\[(?!audio:)([^\\]\\n]*)\\]\\(([^)\\n]*)\\))")

    private fun safeAddStyle(
        builder: AnnotatedString.Builder,
        style: SpanStyle,
        start: Int,
        end: Int,
        textLen: Int,
        spanCounter: IntArray? = null
    ) {
        val s = start.coerceIn(0, textLen)
        val e = end.coerceIn(0, textLen)
        if (s >= e) return
        if (spanCounter != null) {
            if (spanCounter[0] >= MAX_TOTAL_STYLE_SPANS) return
            spanCounter[0]++
        }
        builder.addStyle(style, s, e)
    }

    fun findCursorLine(text: String, cursorOffset: Int): CursorLineInfo {
        if (text.isEmpty()) return CursorLineInfo(0, 0, 0, "")
        val safeCursor = cursorOffset.coerceIn(0, text.length)
        var lineStart = 0
        var lineIndex = 0
        val len = text.length
        while (lineStart <= len) {
            val nl = text.indexOf('\n', lineStart)
            val lineEnd = if (nl == -1) len else nl
            if (safeCursor in lineStart..lineEnd || (nl == -1 && safeCursor >= lineEnd)) {
                return CursorLineInfo(
                    lineIndex = lineIndex,
                    lineStart = lineStart,
                    lineEnd = lineEnd,
                    lineContent = text.substring(lineStart, lineEnd)
                )
            }
            if (nl == -1) break
            lineStart = nl + 1
            lineIndex++
        }
        return CursorLineInfo(0, 0, len, text)
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

        return try {
            val textLen = text.length
            val builder = AnnotatedString.Builder(text)
            val cursor = selection.start.coerceIn(0, textLen)
            val cursorLine = findCursorLine(text, cursor)

            val isHugeDoc = textLen > LARGE_DOC_CHAR_THRESHOLD
            val windowStart = (cursor - CURSOR_WINDOW_CHARS).coerceAtLeast(0)
            val windowEnd = (cursor + CURSOR_WINDOW_CHARS).coerceAtMost(textLen)
            val spanCounter = intArrayOf(0)

            var lineStart = 0
            var idx = 0
            while (lineStart <= textLen) {
                val nl = text.indexOf('\n', lineStart)
                val lineEnd = if (nl == -1) textLen else nl
                val contentEnd = if (lineEnd > lineStart && text[lineEnd - 1] == '\r') lineEnd - 1 else lineEnd

                val isActive = (idx == cursorLine.lineIndex)
                var indentEnd = lineStart
                while (indentEnd < contentEnd && text[indentEnd].isWhitespace()) {
                    indentEnd++
                }
                val indentLen = indentEnd - lineStart
                val trimmedLen = contentEnd - indentEnd

                if (trimmedLen > 0 && spanCounter[0] < MAX_TOTAL_STYLE_SPANS) {
                    val trimmedPreview = text.substring(indentEnd, minOf(contentEnd, indentEnd + 24))

                    when {
                        // H1 - H5 Headers
                        trimmedPreview.startsWith("##### ") -> {
                            applyHeaderStyle(builder, lineStart, contentEnd, indentLen + 6, 13, isActive, primaryColor, textLen, spanCounter)
                        }
                        trimmedPreview.startsWith("#### ") -> {
                            applyHeaderStyle(builder, lineStart, contentEnd, indentLen + 5, 15, isActive, primaryColor, textLen, spanCounter)
                        }
                        trimmedPreview.startsWith("### ") -> {
                            applyHeaderStyle(builder, lineStart, contentEnd, indentLen + 4, 17, isActive, primaryColor, textLen, spanCounter)
                        }
                        trimmedPreview.startsWith("## ") -> {
                            applyHeaderStyle(builder, lineStart, contentEnd, indentLen + 3, 20, isActive, primaryColor, textLen, spanCounter)
                        }
                        trimmedPreview.startsWith("# ") -> {
                            applyHeaderStyle(builder, lineStart, contentEnd, indentLen + 2, 24, isActive, primaryColor, textLen, spanCounter)
                        }

                        // Checklists (with paragraph indentation)
                        trimmedPreview.startsWith("- [ ] ") || trimmedPreview.startsWith("* [ ] ") -> {
                            val prefixEnd = (lineStart + indentLen + 6).coerceAtMost(contentEnd)
                            if (lineStart < contentEnd) {
                                builder.addStyle(
                                    ParagraphStyle(textIndent = TextIndent(firstLine = 16.sp, restLine = 36.sp)),
                                    lineStart,
                                    contentEnd
                                )
                            }
                            safeAddStyle(
                                builder,
                                SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold),
                                lineStart + indentLen,
                                prefixEnd,
                                textLen,
                                spanCounter
                            )
                        }
                        trimmedPreview.startsWith("- [x] ", ignoreCase = true) || trimmedPreview.startsWith("* [x] ", ignoreCase = true) -> {
                            val prefixEnd = (lineStart + indentLen + 6).coerceAtMost(contentEnd)
                            if (lineStart < contentEnd) {
                                builder.addStyle(
                                    ParagraphStyle(textIndent = TextIndent(firstLine = 16.sp, restLine = 36.sp)),
                                    lineStart,
                                    contentEnd
                                )
                            }
                            safeAddStyle(
                                builder,
                                SpanStyle(color = Color(0xFF10B981), fontWeight = FontWeight.Bold),
                                lineStart + indentLen,
                                prefixEnd,
                                textLen,
                                spanCounter
                            )
                            if (prefixEnd < contentEnd) {
                                safeAddStyle(
                                    builder,
                                    SpanStyle(textDecoration = TextDecoration.LineThrough, color = textColor.copy(alpha = 0.5f)),
                                    prefixEnd,
                                    contentEnd,
                                    textLen,
                                    spanCounter
                                )
                            }
                        }

                        // Bullet Lists (with paragraph indentation)
                        trimmedPreview.startsWith("- ") || trimmedPreview.startsWith("* ") || trimmedPreview.startsWith("• ") -> {
                            val prefixEnd = (lineStart + indentLen + 2).coerceAtMost(contentEnd)
                            if (lineStart < contentEnd) {
                                builder.addStyle(
                                    ParagraphStyle(textIndent = TextIndent(firstLine = 16.sp, restLine = 32.sp)),
                                    lineStart,
                                    contentEnd
                                )
                            }
                            safeAddStyle(
                                builder,
                                SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold),
                                lineStart + indentLen,
                                prefixEnd,
                                textLen,
                                spanCounter
                            )
                        }

                        // Ordered Lists: Arabic (1. ), Roman (I. ), Alpha (a) ) with paragraph indentation
                        ORDERED_LIST_PREVIEW_REGEX.find(trimmedPreview) != null -> {
                            val match = ORDERED_LIST_PREVIEW_REGEX.find(trimmedPreview)!!
                            val prefixEnd = (lineStart + indentLen + match.value.length).coerceAtMost(contentEnd)
                            if (lineStart < contentEnd) {
                                builder.addStyle(
                                    ParagraphStyle(textIndent = TextIndent(firstLine = 16.sp, restLine = 34.sp)),
                                    lineStart,
                                    contentEnd
                                )
                            }
                            safeAddStyle(
                                builder,
                                SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold),
                                lineStart + indentLen,
                                prefixEnd,
                                textLen,
                                spanCounter
                            )
                        }

                        // Quotes
                        trimmedPreview.startsWith("> ") -> {
                            val prefixEnd = (lineStart + indentLen + 2).coerceAtMost(contentEnd)
                            safeAddStyle(
                                builder,
                                SpanStyle(color = primaryColor.copy(alpha = 0.7f), fontWeight = FontWeight.Bold),
                                lineStart + indentLen,
                                prefixEnd,
                                textLen,
                                spanCounter
                            )
                            safeAddStyle(
                                builder,
                                SpanStyle(fontStyle = FontStyle.Italic, color = textColor.copy(alpha = 0.85f)),
                                prefixEnd,
                                contentEnd,
                                textLen,
                                spanCounter
                            )
                        }

                        // Code block or Math delimiters
                        trimmedPreview.startsWith("```") || trimmedPreview.startsWith("$$") -> {
                            safeAddStyle(
                                builder,
                                SpanStyle(
                                    fontFamily = FontFamily.Monospace,
                                    color = primaryColor,
                                    background = if (isDarkBg) Color(0xFF1E293B) else Color(0xFFF1F5F9)
                                ),
                                lineStart,
                                contentEnd,
                                textLen,
                                spanCounter
                            )
                        }

                        // Dividers
                        trimmedLen == 3 && (trimmedPreview == "---" || trimmedPreview == "***") -> {
                            safeAddStyle(
                                builder,
                                SpanStyle(color = primaryColor.copy(alpha = 0.5f), fontWeight = FontWeight.ExtraBold),
                                lineStart,
                                contentEnd,
                                textLen,
                                spanCounter
                            )
                        }

                        // Page Break
                        (trimmedLen == 18 && text.regionMatches(indentEnd, "<!-- pagebreak -->", 0, 18, ignoreCase = true)) ||
                            (trimmedLen == 15 && text.regionMatches(indentEnd, "---pagebreak---", 0, 15, ignoreCase = true)) -> {
                            safeAddStyle(
                                builder,
                                SpanStyle(
                                    color = primaryColor,
                                    fontWeight = FontWeight.Bold,
                                    background = if (isDarkBg) Color(0xFF1E293B) else Color(0xFFF1F5F9)
                                ),
                                lineStart,
                                contentEnd,
                                textLen,
                                spanCounter
                            )
                        }
                    }
                }

                // Apply inline formatting if within safe limits
                val lineLength = contentEnd - lineStart
                val inCursorWindow = !isHugeDoc && idx < LARGE_DOC_LINE_THRESHOLD ||
                    (contentEnd >= windowStart && lineStart <= windowEnd)
                if (lineLength in 1..MAX_INLINE_LINE_CHARS && inCursorWindow && spanCounter[0] < MAX_TOTAL_STYLE_SPANS) {
                    applyInlineFormattingToRange(
                        builder = builder,
                        fullText = text,
                        start = lineStart,
                        end = contentEnd,
                        textLen = textLen,
                        primaryColor = primaryColor,
                        textColor = textColor,
                        isDarkBg = isDarkBg,
                        spanCounter = spanCounter
                    )
                }

                if (nl == -1) break
                lineStart = nl + 1
                idx++
            }

            builder.toAnnotatedString()
        } catch (_: Throwable) {
            AnnotatedString(text)
        }
    }

    private fun applyHeaderStyle(
        builder: AnnotatedString.Builder,
        lineStart: Int,
        lineEnd: Int,
        prefixLen: Int,
        spSize: Int,
        isActive: Boolean,
        primaryColor: Color,
        textLen: Int,
        spanCounter: IntArray
    ) {
        val prefixEnd = (lineStart + prefixLen).coerceAtMost(lineEnd)
        safeAddStyle(
            builder,
            SpanStyle(
                fontSize = if (isActive) spSize.sp else (spSize - 4).sp,
                color = if (isActive) primaryColor else primaryColor.copy(alpha = 0.35f),
                fontWeight = FontWeight.Bold
            ),
            lineStart,
            prefixEnd,
            textLen,
            spanCounter
        )
        if (prefixEnd < lineEnd) {
            safeAddStyle(
                builder,
                SpanStyle(
                    fontSize = spSize.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryColor
                ),
                prefixEnd,
                lineEnd,
                textLen,
                spanCounter
            )
        }
    }

    private fun applyInlineFormattingToRange(
        builder: AnnotatedString.Builder,
        fullText: String,
        start: Int,
        end: Int,
        textLen: Int,
        primaryColor: Color,
        textColor: Color,
        isDarkBg: Boolean,
        spanCounter: IntArray
    ) {
        if (start >= end || spanCounter[0] >= MAX_TOTAL_STYLE_SPANS) return
        val chunk = fullText.substring(start, end)
        val analyzed = InlineMarkdownHelper.analyzeInlineSpans(chunk)
        val highlightColor = if (isDarkBg) Color(0xFF6B5800) else Color(0xFFFFF176)
        val codeBg = if (isDarkBg) Color(0xFF1E293B) else Color(0xFFF1F5F9)

        val n = chunk.length
        val mask = IntArray(n)
        for (k in 0 until n) {
            if (analyzed.hidden[k]) mask[k] = mask[k] or FLAG_HIDDEN
        }

        for (span in analyzed.styleSpans) {
            val s = span.start.coerceIn(0, n)
            val e = span.end.coerceIn(0, n)
            val bit = when (span.kind) {
                InlineMarkdownHelper.StyleKind.BOLD -> FLAG_BOLD
                InlineMarkdownHelper.StyleKind.ITALIC -> FLAG_ITALIC
                InlineMarkdownHelper.StyleKind.STRIKETHROUGH -> FLAG_STRIKE
                InlineMarkdownHelper.StyleKind.UNDERLINE -> FLAG_UNDERLINE
                InlineMarkdownHelper.StyleKind.HIGHLIGHT -> FLAG_HIGHLIGHT
                InlineMarkdownHelper.StyleKind.QUOTE -> FLAG_QUOTE
            }
            for (k in s until e) {
                mask[k] = mask[k] or bit
            }
        }
        for (link in analyzed.linkLabels) {
            val s = link.labelStart.coerceIn(0, n)
            val e = link.labelEnd.coerceIn(0, n)
            for (k in s until e) {
                mask[k] = mask[k] or FLAG_UNDERLINE
            }
        }

        var idx = 0
        while (idx < n && spanCounter[0] < MAX_TOTAL_STYLE_SPANS) {
            val cur = mask[idx]
            var runEnd = idx + 1
            while (runEnd < n && mask[runEnd] == cur) {
                runEnd++
            }

            if (cur != 0) {
                val b = (cur and FLAG_BOLD) != 0
                val it = (cur and FLAG_ITALIC) != 0
                val st = (cur and FLAG_STRIKE) != 0
                val un = (cur and FLAG_UNDERLINE) != 0
                val hl = (cur and FLAG_HIGHLIGHT) != 0
                val qt = (cur and FLAG_QUOTE) != 0
                val hid = (cur and FLAG_HIDDEN) != 0

                val deco = when {
                    st && un -> TextDecoration.combine(listOf(TextDecoration.LineThrough, TextDecoration.Underline))
                    st -> TextDecoration.LineThrough
                    un -> TextDecoration.Underline
                    else -> null
                }

                safeAddStyle(
                    builder,
                    SpanStyle(
                        fontWeight = if (b) FontWeight.Bold else null,
                        fontStyle = if (it || qt) FontStyle.Italic else null,
                        textDecoration = deco,
                        background = if (hl) highlightColor else Color.Unspecified,
                        color = when {
                            hid -> textColor.copy(alpha = 0.42f)
                            hl && isDarkBg -> Color(0xFFFEF08A)
                            hl -> Color(0xFF713F12)
                            st -> textColor.copy(alpha = 0.65f)
                            qt -> textColor.copy(alpha = 0.8f)
                            else -> Color.Unspecified
                        }
                    ),
                    start + idx,
                    start + runEnd,
                    textLen,
                    spanCounter
                )
            }
            idx = runEnd
        }

        // Inline links
        for (link in analyzed.linkLabels) {
            if (spanCounter[0] >= MAX_TOTAL_STYLE_SPANS) break
            safeAddStyle(
                builder,
                SpanStyle(
                    color = Color(0xFF3B82F6),
                    textDecoration = TextDecoration.Underline
                ),
                start + link.labelStart,
                start + link.labelEnd,
                textLen,
                spanCounter
            )
        }

        // Atomic tokens: Inline Code, Math, Bare URLs
        for (atomic in analyzed.atomics) {
            if (spanCounter[0] >= MAX_TOTAL_STYLE_SPANS) break
            val s = start + atomic.start
            val e = start + atomic.end
            when (atomic.kind) {
                InlineMarkdownHelper.AtomicKind.CODE -> safeAddStyle(
                    builder,
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = codeBg,
                        color = primaryColor
                    ),
                    s,
                    e,
                    textLen,
                    spanCounter
                )
                InlineMarkdownHelper.AtomicKind.MATH_BLOCK -> {
                    val mathBg = if (isDarkBg) Color(0xFF1E293B) else Color(0xFFFEF3C7)
                    val mathFg = if (isDarkBg) Color(0xFFFDE68A) else Color(0xFF78350F)
                    safeAddStyle(
                        builder,
                        SpanStyle(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontStyle = FontStyle.Italic,
                            background = mathBg,
                            color = mathFg
                        ),
                        s,
                        e,
                        textLen,
                        spanCounter
                    )
                }
                InlineMarkdownHelper.AtomicKind.MATH_INLINE -> {
                    val mathBg = if (isDarkBg) Color(0xFF334155).copy(alpha = 0.5f) else Color(0xFFF1F5F9)
                    val mathFg = if (isDarkBg) Color(0xFF93C5FD) else Color(0xFF0F172A)
                    safeAddStyle(
                        builder,
                        SpanStyle(
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.SemiBold,
                            background = mathBg,
                            color = mathFg
                        ),
                        s,
                        e,
                        textLen,
                        spanCounter
                    )
                }
                InlineMarkdownHelper.AtomicKind.BARE_URL -> safeAddStyle(
                    builder,
                    SpanStyle(
                        color = Color(0xFF3B82F6),
                        textDecoration = TextDecoration.Underline
                    ),
                    s,
                    e,
                    textLen,
                    spanCounter
                )
            }
        }

        // Audio or Image token
        if (chunk.indexOf('[') != -1 && chunk.indexOf('(') != -1) {
            for (match in MEDIA_TOKEN_HIGHLIGHT_REGEX.findAll(chunk)) {
                if (spanCounter[0] >= MAX_TOTAL_STYLE_SPANS) break
                val s = start + match.range.first
                val e = start + match.range.last + 1
                val isAudio = match.value.contains("[audio:") || match.value.contains("![audio:")
                val pillBg = if (isAudio) (if (isDarkBg) Color(0xFF064E3B) else Color(0xFFD1FAE5)) else (if (isDarkBg) Color(0xFF1E3A8A) else Color(0xFFDBEAFE))
                val pillFg = if (isAudio) Color(0xFF10B981) else Color(0xFF3B82F6)
                safeAddStyle(
                    builder,
                    SpanStyle(
                        background = pillBg,
                        color = pillFg,
                        fontWeight = FontWeight.SemiBold
                    ),
                    s,
                    e,
                    textLen,
                    spanCounter
                )
            }
        }
    }

    private val ORDERED_LIST_PREVIEW_REGEX = Regex("^(?:\\d+\\.|[IVXLCDM]+\\.|[a-z]\\))\\s+")
    private val ARABIC_LIST_REGEX = Regex("^(\\d+)\\.\\s+(.*)$")
    private val ROMAN_LIST_REGEX = Regex("^([IVXLCDM]+)\\.\\s+(.*)$")
    private val ALPHA_LIST_REGEX = Regex("^([a-z])\\)\\s+(.*)$")

    private fun extractKnownLinePrefix(trimmedLine: String): String? {
        val staticPrefixes = listOf(
            "##### ", "#### ", "### ", "## ", "# ",
            "- [ ] ", "- [x] ", "- [X] ", "* [ ] ", "* [x] ", "* [X] ",
            "- ", "* ", "• ", "> "
        )
        staticPrefixes.firstOrNull { trimmedLine.startsWith(it) }?.let { return it }
        ORDERED_LIST_PREVIEW_REGEX.find(trimmedLine)?.let { return it.value }
        return null
    }

    private fun isWrappedWithStyle(body: String, prefix: String, suffix: String): Boolean {
        if (body.isEmpty()) return false
        return when (prefix) {
            "*" -> {
                val leadStars = body.takeWhile { it == '*' }.length
                val trailStars = body.takeLastWhile { it == '*' }.length
                body.length > leadStars + trailStars && (leadStars % 2 == 1) && (trailStars % 2 == 1)
            }
            "**" -> {
                val leadStars = body.takeWhile { it == '*' }.length
                val trailStars = body.takeLastWhile { it == '*' }.length
                body.length > leadStars + trailStars && leadStars >= 2 && trailStars >= 2
            }
            else -> body.startsWith(prefix) && body.endsWith(suffix) && body.length >= prefix.length + suffix.length
        }
    }

    private fun toRoman(number: Int): String {
        val n = number.coerceIn(1, 3999)
        val values = intArrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
        val symbols = arrayOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")
        val sb = StringBuilder()
        var rem = n
        for (i in values.indices) {
            while (rem >= values[i]) {
                rem -= values[i]
                sb.append(symbols[i])
            }
        }
        return sb.toString()
    }

    private fun fromRoman(roman: String): Int? {
        if (roman.isEmpty()) return null
        val map = mapOf('I' to 1, 'V' to 5, 'X' to 10, 'L' to 50, 'C' to 100, 'D' to 500, 'M' to 1000)
        var total = 0
        var prev = 0
        for (ch in roman.reversed()) {
            val valNum = map[ch] ?: return null
            if (valNum < prev) total -= valNum else total += valNum
            prev = valNum
        }
        return if (total > 0) total else null
    }

    /**
     * Encloses the current selection with prefix and suffix, or toggles it off if the selection
     * is already wrapped by that exact style (supporting multi-paragraph selections cleanly).
     */
    fun wrapSelection(value: TextFieldValue, prefix: String, suffix: String): TextFieldValue {
        val text = value.text
        val selection = value.selection

        return if (selection.start != selection.end) {
            val min = minOf(selection.start, selection.end)
            val max = maxOf(selection.start, selection.end)
            val selected = text.substring(min, max)
            val before = text.substring(0, min)
            val after = text.substring(max)

            // Multi-paragraph / multi-line selection support
            if (selected.contains('\n')) {
                val lines = selected.split('\n')
                val nonBlankInfos = lines.mapIndexedNotNull { idx, line ->
                    if (line.isBlank()) null
                    else {
                        val leadWsLen = line.indexOfFirst { !it.isWhitespace() }.let { if (it == -1) line.length else it }
                        val leadWs = line.substring(0, leadWsLen)
                        val trimmed = line.substring(leadWsLen)
                        val isAtStartOfLine = idx > 0 || min == 0 || text[min - 1] == '\n'
                        val blockPrefix = if (isAtStartOfLine) extractKnownLinePrefix(trimmed) ?: "" else ""
                        val body = trimmed.removePrefix(blockPrefix)
                        Triple(idx, leadWs + blockPrefix, body)
                    }
                }

                if (nonBlankInfos.isEmpty()) return value

                val allAlreadyWrapped = nonBlankInfos.all { (_, _, body) ->
                    body.isBlank() || isWrappedWithStyle(body, prefix, suffix)
                }

                val infoMap = nonBlankInfos.associateBy { it.first }
                val transformedLines = lines.mapIndexed { idx, rawLine ->
                    val info = infoMap[idx]
                    if (info == null || info.third.isBlank()) {
                        rawLine
                    } else {
                        val head = info.second
                        val body = info.third
                        if (allAlreadyWrapped) {
                            head + body.removePrefix(prefix).removeSuffix(suffix)
                        } else if (isWrappedWithStyle(body, prefix, suffix)) {
                            head + body
                        } else {
                            head + prefix + body + suffix
                        }
                    }
                }
                val newSelected = transformedLines.joinToString("\n")
                val newText = before + newSelected + after
                return value.copy(
                    text = newText,
                    selection = TextRange(min, min + newSelected.length)
                )
            }

            val shouldUnwrapSurrounding = when (prefix) {
                "*" -> {
                    val starsBefore = before.takeLastWhile { it == '*' }.length
                    val starsAfter = after.takeWhile { it == '*' }.length
                    (starsBefore % 2 == 1) && (starsAfter % 2 == 1)
                }
                "**" -> {
                    val starsBefore = before.takeLastWhile { it == '*' }.length
                    val starsAfter = after.takeWhile { it == '*' }.length
                    starsBefore >= 2 && starsAfter >= 2
                }
                else -> before.endsWith(prefix) && after.startsWith(suffix)
            }

            if (shouldUnwrapSurrounding) {
                val newBefore = before.dropLast(prefix.length)
                val newAfter = after.drop(suffix.length)
                val newText = newBefore + selected + newAfter
                val newSelection = TextRange(newBefore.length, newBefore.length + selected.length)
                value.copy(text = newText, selection = newSelection)
            } else {
                val canUnwrapInner = isWrappedWithStyle(selected, prefix, suffix)

                if (canUnwrapInner) {
                    val unwrapped = selected.removePrefix(prefix).removeSuffix(suffix)
                    val newText = before + unwrapped + after
                    val newSelection = TextRange(min, min + unwrapped.length)
                    value.copy(text = newText, selection = newSelection)
                } else {
                    val newText = before + prefix + selected + suffix + after
                    val newSelection = TextRange(min + prefix.length, min + prefix.length + selected.length)
                    value.copy(text = newText, selection = newSelection)
                }
            }
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
        val selStart = minOf(value.selection.start, value.selection.end).coerceIn(0, text.length)
        val selEnd = maxOf(value.selection.start, value.selection.end).coerceIn(0, text.length)

        if (selStart != selEnd) {
            val firstLineStart = if (selStart == 0) 0 else {
                val prevNl = text.lastIndexOf('\n', selStart - 1)
                if (prevNl == -1) 0 else prevNl + 1
            }
            val lastLineEnd = text.indexOf('\n', selEnd).let { if (it == -1) text.length else it }
            val selectedBlock = text.substring(firstLineStart, lastLineEnd)
            val lines = selectedBlock.split('\n')
            val nonBlankLines = lines.filter { it.isNotBlank() }
            val allHaveTarget = nonBlankLines.isNotEmpty() && nonBlankLines.all {
                it.trimStart().startsWith(targetPrefix)
            }

            val transformed = lines.joinToString("\n") { line ->
                if (line.isBlank()) {
                    line
                } else {
                    val trimmed = line.trimStart()
                    val indent = line.substring(0, line.length - trimmed.length)
                    val existingPrefix = extractKnownLinePrefix(trimmed)
                    when {
                        allHaveTarget && existingPrefix == targetPrefix -> indent + trimmed.removePrefix(targetPrefix)
                        existingPrefix != null -> indent + targetPrefix + trimmed.removePrefix(existingPrefix)
                        else -> indent + targetPrefix + trimmed
                    }
                }
            }

            val newText = text.substring(0, firstLineStart) + transformed + text.substring(lastLineEnd)
            return value.copy(
                text = newText,
                selection = TextRange(firstLineStart, firstLineStart + transformed.length)
            )
        }

        val cursor = selStart
        val info = findCursorLine(text, cursor)
        val line = info.lineContent
        val trimmed = line.trimStart()
        val indent = line.substring(0, line.length - trimmed.length)

        val existingPrefix = extractKnownLinePrefix(trimmed)

        val newLine = when {
            existingPrefix == targetPrefix -> indent + trimmed.removePrefix(targetPrefix)
            existingPrefix != null -> indent + targetPrefix + trimmed.removePrefix(existingPrefix)
            else -> indent + targetPrefix + trimmed
        }

        val newText = text.substring(0, info.lineStart) + newLine + text.substring(info.lineEnd)
        val diff = newLine.length - line.length
        val newCursor = (cursor + diff).coerceIn(0, newText.length)

        return value.copy(text = newText, selection = TextRange(newCursor, newCursor))
    }

    /**
     * Toggles or applies one of the 4 list styles (BULLET, ARABIC 1., ROMAN I., ALPHA a))
     * to the cursor line or across all selected paragraphs, numbering sequentially.
     */
    fun toggleListStyle(value: TextFieldValue, style: ListStyleType): TextFieldValue {
        if (style == ListStyleType.BULLET) {
            return toggleLinePrefix(value, "- ")
        }
        val text = value.text
        val selStart = minOf(value.selection.start, value.selection.end).coerceIn(0, text.length)
        val selEnd = maxOf(value.selection.start, value.selection.end).coerceIn(0, text.length)

        fun matchesStyle(trimmed: String): Boolean = when (style) {
            ListStyleType.BULLET -> trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ")
            ListStyleType.ARABIC -> ARABIC_LIST_REGEX.containsMatchIn(trimmed)
            ListStyleType.ROMAN -> ROMAN_LIST_REGEX.containsMatchIn(trimmed)
            ListStyleType.ALPHA -> ALPHA_LIST_REGEX.containsMatchIn(trimmed)
        }

        fun formatMarker(ordinal: Int): String = when (style) {
            ListStyleType.BULLET -> "- "
            ListStyleType.ARABIC -> "$ordinal. "
            ListStyleType.ROMAN -> "${toRoman(ordinal)}. "
            ListStyleType.ALPHA -> "${'a' + ((ordinal - 1) % 26)}) "
        }

        if (selStart != selEnd) {
            val firstLineStart = if (selStart == 0) 0 else {
                val prevNl = text.lastIndexOf('\n', selStart - 1)
                if (prevNl == -1) 0 else prevNl + 1
            }
            val lastLineEnd = text.indexOf('\n', selEnd).let { if (it == -1) text.length else it }
            val selectedBlock = text.substring(firstLineStart, lastLineEnd)
            val lines = selectedBlock.split('\n')
            val nonBlank = lines.filter { it.isNotBlank() }
            val allAlreadyStyle = nonBlank.isNotEmpty() && nonBlank.all { matchesStyle(it.trimStart()) }

            var seq = 1
            val transformed = lines.joinToString("\n") { line ->
                if (line.isBlank()) {
                    line
                } else {
                    val trimmed = line.trimStart()
                    val indent = line.substring(0, line.length - trimmed.length)
                    val existing = extractKnownLinePrefix(trimmed) ?: ""
                    val body = trimmed.removePrefix(existing)
                    if (allAlreadyStyle) {
                        indent + body
                    } else {
                        val marker = formatMarker(seq++)
                        indent + marker + body
                    }
                }
            }
            val newText = text.substring(0, firstLineStart) + transformed + text.substring(lastLineEnd)
            return value.copy(
                text = newText,
                selection = TextRange(firstLineStart, firstLineStart + transformed.length)
            )
        }

        val info = findCursorLine(text, selStart)
        val line = info.lineContent
        val trimmed = line.trimStart()
        val indent = line.substring(0, line.length - trimmed.length)
        val existing = extractKnownLinePrefix(trimmed) ?: ""
        val body = trimmed.removePrefix(existing)

        val newLine = if (matchesStyle(trimmed)) {
            indent + body
        } else {
            val prevOrdinal = if (info.lineStart > 1) {
                val prevLineEnd = info.lineStart - 1
                val prevNl = text.lastIndexOf('\n', prevLineEnd - 1)
                val prevLineStart = if (prevNl == -1) 0 else prevNl + 1
                val prevTrimmed = text.substring(prevLineStart, prevLineEnd).trimStart()
                when (style) {
                    ListStyleType.ARABIC -> ARABIC_LIST_REGEX.find(prevTrimmed)?.groupValues?.get(1)?.toIntOrNull()?.plus(1)
                    ListStyleType.ROMAN -> ROMAN_LIST_REGEX.find(prevTrimmed)?.groupValues?.get(1)?.let { fromRoman(it) }?.plus(1)
                    ListStyleType.ALPHA -> ALPHA_LIST_REGEX.find(prevTrimmed)?.groupValues?.get(1)?.firstOrNull()?.let { (it - 'a') + 2 }
                    ListStyleType.BULLET -> 1
                }
            } else null

            indent + formatMarker(prevOrdinal ?: 1) + body
        }

        val newText = text.substring(0, info.lineStart) + newLine + text.substring(info.lineEnd)
        val diff = newLine.length - line.length
        val newCursor = (selStart + diff).coerceIn(0, newText.length)
        return value.copy(text = newText, selection = TextRange(newCursor, newCursor))
    }

    /**
     * Automatically continues lists/checklists on Enter, or removes the empty prefix on a second Enter.
     */
    fun handleNewlineAutoContinue(oldValue: TextFieldValue, newValue: TextFieldValue): TextFieldValue {
        if (newValue.text.length != oldValue.text.length + 1) return newValue
        if (!newValue.selection.collapsed) return newValue
        val cursor = newValue.selection.start
        if (cursor <= 0 || cursor > newValue.text.length) return newValue
        if (newValue.text[cursor - 1] != '\n') return newValue

        val newlinePos = cursor - 1
        val prevNl = newValue.text.lastIndexOf('\n', newlinePos - 1)
        val lineStart = if (prevNl == -1) 0 else prevNl + 1
        val prevLine = newValue.text.substring(lineStart, newlinePos)
        val trimmed = prevLine.trimStart()
        val indent = prevLine.substring(0, prevLine.length - trimmed.length)

        val nextPrefix: String
        val bodyEmpty: Boolean

        when {
            trimmed.startsWith("- [ ] ") || trimmed.startsWith("- [x] ") || trimmed.startsWith("- [X] ") -> {
                nextPrefix = "- [ ] "
                bodyEmpty = trimmed.length == 6
            }
            trimmed.startsWith("* [ ] ") || trimmed.startsWith("* [x] ") || trimmed.startsWith("* [X] ") -> {
                nextPrefix = "* [ ] "
                bodyEmpty = trimmed.length == 6
            }
            trimmed.startsWith("- ") -> {
                nextPrefix = "- "
                bodyEmpty = trimmed.length == 2
            }
            trimmed.startsWith("* ") -> {
                nextPrefix = "* "
                bodyEmpty = trimmed.length == 2
            }
            ARABIC_LIST_REGEX.matches(trimmed) -> {
                val m = ARABIC_LIST_REGEX.find(trimmed)!!
                val num = m.groupValues[1].toIntOrNull() ?: 1
                nextPrefix = "${num + 1}. "
                bodyEmpty = m.groupValues[2].isEmpty()
            }
            ROMAN_LIST_REGEX.matches(trimmed) -> {
                val m = ROMAN_LIST_REGEX.find(trimmed)!!
                val num = fromRoman(m.groupValues[1]) ?: 1
                nextPrefix = "${toRoman(num + 1)}. "
                bodyEmpty = m.groupValues[2].isEmpty()
            }
            ALPHA_LIST_REGEX.matches(trimmed) -> {
                val m = ALPHA_LIST_REGEX.find(trimmed)!!
                val ch = m.groupValues[1].firstOrNull() ?: 'a'
                val nextCh = if (ch < 'z') (ch + 1) else 'a'
                nextPrefix = "$nextCh) "
                bodyEmpty = m.groupValues[2].isEmpty()
            }
            else -> return newValue
        }

        return if (bodyEmpty) {
            val updated = newValue.text.substring(0, lineStart) + newValue.text.substring(cursor)
            newValue.copy(text = updated, selection = TextRange(lineStart, lineStart))
        } else {
            val insert = indent + nextPrefix
            val updated = newValue.text.substring(0, cursor) + insert + newValue.text.substring(cursor)
            val newCur = cursor + insert.length
            newValue.copy(text = updated, selection = TextRange(newCur, newCur))
        }
    }

    /**
     * Wraps selected paragraphs inside a block (e.g. Code, Math, Collapsible) or inserts defaultTemplate if nothing is selected.
     */
    fun wrapOrInsertBlock(
        value: TextFieldValue,
        blockPrefix: String,
        blockSuffix: String,
        defaultTemplate: String
    ): TextFieldValue {
        val text = value.text
        val selStart = minOf(value.selection.start, value.selection.end).coerceIn(0, text.length)
        val selEnd = maxOf(value.selection.start, value.selection.end).coerceIn(0, text.length)

        if (selStart == selEnd) {
            return insertMediaToken(value, defaultTemplate)
        }

        val selected = text.substring(selStart, selEnd).trim()
        if (selected.isEmpty()) {
            return insertMediaToken(value, defaultTemplate)
        }

        val needsLeadingNewline = selStart > 0 && text[selStart - 1] != '\n'
        val needsTrailingNewline = selEnd < text.length && text[selEnd] != '\n'
        val sb = StringBuilder()
        if (needsLeadingNewline) sb.append("\n")
        sb.append(blockPrefix)
        sb.append(selected)
        sb.append(blockSuffix)
        if (needsTrailingNewline) sb.append("\n")

        val insertion = sb.toString()
        val newText = text.substring(0, selStart) + insertion + text.substring(selEnd)
        val newPos = selStart + insertion.length
        return value.copy(text = newText, selection = TextRange(newPos, newPos))
    }

    /**
     * Wraps selected paragraphs inside a Markdown Callout block or inserts defaultText if nothing is selected.
     */
    fun wrapSelectionInCallout(
        value: TextFieldValue,
        calloutTag: String,
        defaultText: String
    ): TextFieldValue {
        val text = value.text
        val selStart = minOf(value.selection.start, value.selection.end).coerceIn(0, text.length)
        val selEnd = maxOf(value.selection.start, value.selection.end).coerceIn(0, text.length)

        if (selStart == selEnd) {
            return insertMediaToken(value, "> [!$calloutTag] $defaultText")
        }

        val selected = text.substring(selStart, selEnd).trim()
        if (selected.isEmpty()) {
            return insertMediaToken(value, "> [!$calloutTag] $defaultText")
        }

        val quotedLines = selected.lines().joinToString("\n") { line ->
            if (line.isBlank()) ">" else "> ${line.trimStart()}"
        }
        val block = "> [!$calloutTag]\n$quotedLines"
        val needsLeadingNewline = selStart > 0 && text[selStart - 1] != '\n'
        val needsTrailingNewline = selEnd < text.length && text[selEnd] != '\n'
        val sb = StringBuilder()
        if (needsLeadingNewline) sb.append("\n")
        sb.append(block)
        if (needsTrailingNewline) sb.append("\n")

        val insertion = sb.toString()
        val newText = text.substring(0, selStart) + insertion + text.substring(selEnd)
        val newPos = selStart + insertion.length
        return value.copy(text = newText, selection = TextRange(newPos, newPos))
    }

    /**
     * Inserts text at the current cursor position.
     */
    fun insertTextAtCursor(value: TextFieldValue, textToInsert: String): TextFieldValue {
        val text = value.text
        val pos = value.selection.start.coerceIn(0, text.length)
        val newText = text.substring(0, pos) + textToInsert + text.substring(pos)
        val newPos = pos + textToInsert.length
        return value.copy(text = newText, selection = TextRange(newPos, newPos))
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
        if (markdown.isEmpty() || markdown.indexOf('(') == -1 || markdown.indexOf('[') == -1) {
            return emptyList()
        }
        return try {
            val list = mutableListOf<MediaTokenInfo>()

            // 1. Audio: ![audio:Title](url) or [audio:Title](url)
            for (match in AUDIO_TOKEN_REGEX.findAll(markdown)) {
                if (list.size >= MAX_MEDIA_TOKENS) break
                val raw = match.value
                val title = match.groupValues[2]
                val url = match.groupValues[3]
                if (url.startsWith("data:", ignoreCase = true) && url.length > MAX_DATA_URI_LENGTH) continue
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
            for (match in IMAGE_TOKEN_REGEX.findAll(markdown)) {
                if (list.size >= MAX_MEDIA_TOKENS) break
                val raw = match.value
                val meta = match.groupValues[2]
                val url = match.groupValues[3]
                if (url.startsWith("data:", ignoreCase = true) && url.length > MAX_DATA_URI_LENGTH) continue
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

            list.sortedBy { it.startOffset }
        } catch (_: Throwable) {
            emptyList()
        }
    }
}
