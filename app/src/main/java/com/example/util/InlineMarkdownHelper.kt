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

    internal enum class StyleKind {
        BOLD, ITALIC, STRIKETHROUGH, UNDERLINE, HIGHLIGHT, QUOTE
    }

    internal data class StyleSpan(
        val kind: StyleKind,
        val start: Int,
        val end: Int
    )

    internal enum class AtomicKind {
        MATH_BLOCK, MATH_INLINE, CODE, BARE_URL
    }

    internal data class AtomicToken(
        val kind: AtomicKind,
        val start: Int,
        val end: Int,
        val content: String,
        val extraUrl: String = ""
    )

    internal data class LinkLabelSpan(
        val labelStart: Int,
        val labelEnd: Int,
        val fullUrl: String
    )

    internal data class AnalyzedInline(
        val styleSpans: List<StyleSpan>,
        val hidden: BooleanArray,
        val atomics: List<AtomicToken>,
        val linkLabels: List<LinkLabelSpan>
    )

    private data class StarRun(
        val start: Int,
        var end: Int,
        val canOpen: Boolean,
        val canClose: Boolean
    ) {
        val remaining: Int get() = end - start
    }

    private data class MarkerOpener(
        val key: String,
        val styleKind: StyleKind,
        val openStart: Int,
        val openEnd: Int
    )

    private data class HtmlTagMatch(
        val groupKey: String,
        val styleKind: StyleKind,
        val isClosing: Boolean,
        val length: Int
    )

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

    internal fun analyzeInlineSpans(rawText: String): AnalyzedInline {
        val n = rawText.length
        val hidden = BooleanArray(n)
        val protectedChars = BooleanArray(n)
        val atomics = mutableListOf<AtomicToken>()
        val linkLabels = mutableListOf<LinkLabelSpan>()
        val styleSpans = mutableListOf<StyleSpan>()

        fun markHiddenAndProtected(s: Int, e: Int) {
            val start = s.coerceIn(0, n)
            val end = e.coerceIn(0, n)
            for (k in start until end) {
                hidden[k] = true
                protectedChars[k] = true
            }
        }

        // Pass 1: Identify protected atomic tokens (Math, Inline Code, Links, Bare URLs)
        var i = 0
        while (i < n) {
            if (protectedChars[i]) {
                i++
                continue
            }

            // 1. LaTeX Block Math: $$formula$$
            if (rawText.startsWith("$$", i)) {
                val end = rawText.indexOf("$$", i + 2)
                if (end != -1) {
                    atomics.add(AtomicToken(AtomicKind.MATH_BLOCK, i, end + 2, rawText.substring(i + 2, end)))
                    for (k in i until end + 2) protectedChars[k] = true
                    i = end + 2
                    continue
                }
            }

            // 2. LaTeX Inline Math: $formula$
            if (rawText[i] == '$' && !rawText.startsWith("$$", i)) {
                val end = rawText.indexOf('$', i + 1)
                if (end > i + 1 && !rawText.substring(i + 1, end).contains('\n')) {
                    atomics.add(AtomicToken(AtomicKind.MATH_INLINE, i, end + 1, rawText.substring(i + 1, end)))
                    for (k in i until end + 1) protectedChars[k] = true
                    i = end + 1
                    continue
                }
            }

            // 3. Inline Code: <>...</>, <code>...</code>, `...`
            if (rawText.startsWith("<>", i)) {
                val end = rawText.indexOf("</>", i + 2)
                if (end != -1) {
                    atomics.add(AtomicToken(AtomicKind.CODE, i, end + 3, rawText.substring(i + 2, end)))
                    for (k in i until end + 3) protectedChars[k] = true
                    i = end + 3
                    continue
                }
            }
            if (rawText.startsWith("<code>", i, ignoreCase = true)) {
                val end = rawText.indexOf("</code>", i + 6, ignoreCase = true)
                if (end != -1) {
                    atomics.add(AtomicToken(AtomicKind.CODE, i, end + 7, rawText.substring(i + 6, end)))
                    for (k in i until end + 7) protectedChars[k] = true
                    i = end + 7
                    continue
                }
            }
            if (rawText[i] == '`' && !rawText.startsWith("```", i)) {
                val end = rawText.indexOf('`', i + 1)
                if (end != -1) {
                    atomics.add(AtomicToken(AtomicKind.CODE, i, end + 1, rawText.substring(i + 1, end)))
                    for (k in i until end + 1) protectedChars[k] = true
                    i = end + 1
                    continue
                }
            }

            // 4. Markdown Link: [label](url) (skip image/audio ![...](...))
            if (rawText[i] == '[' && (i == 0 || rawText[i - 1] != '!')) {
                val closeLabel = rawText.indexOf("](", i + 1)
                if (closeLabel != -1) {
                    val innerLabel = rawText.substring(i + 1, closeLabel)
                    if (!innerLabel.contains(']') && !innerLabel.contains('\n')) {
                        val closeUrl = rawText.indexOf(')', closeLabel + 2)
                        if (closeUrl != -1 && !rawText.substring(closeLabel + 2, closeUrl).contains('\n')) {
                            val rawUrl = rawText.substring(closeLabel + 2, closeUrl)
                            val fullUrl = normalizeUrl(rawUrl)
                            markHiddenAndProtected(i, i + 1)
                            markHiddenAndProtected(closeLabel, closeUrl + 1)
                            linkLabels.add(LinkLabelSpan(i + 1, closeLabel, fullUrl))
                            i++ // Advance inside label so nested inline styles and code still parse!
                            continue
                        }
                    }
                }
            }

            val insideLinkLabel = linkLabels.any { i in it.labelStart until it.labelEnd }

            // 5. Pipe Link: url|label
            if (!insideLinkLabel && (i == 0 || rawText[i - 1].isWhitespace())) {
                val pipeMatch = PIPE_LINK_REGEX.find(rawText.substring(i))
                if (pipeMatch != null && pipeMatch.range.first == 0) {
                    val rawUrl = pipeMatch.groupValues[1]
                    if (rawUrl.contains('.') || rawUrl.contains('/')) {
                        val label = pipeMatch.groupValues[2].substringBefore(" ").ifEmpty { pipeMatch.groupValues[2] }
                        val fullUrl = normalizeUrl(rawUrl)
                        val labelStart = i + rawUrl.length + 1
                        val labelEnd = labelStart + label.length
                        markHiddenAndProtected(i, labelStart)
                        linkLabels.add(LinkLabelSpan(labelStart, labelEnd, fullUrl))
                        i = labelStart
                        continue
                    }
                }
            }

            // 6. Bare URLs: https://... or www....
            if (!insideLinkLabel && (rawText[i] == 'h' || rawText[i] == 'w')) {
                val bareMatch = BARE_URL_REGEX.find(rawText.substring(i))
                if (bareMatch != null && bareMatch.range.first == 0) {
                    val matchedUrl = bareMatch.value
                    val fullUrl = normalizeUrl(matchedUrl)
                    val end = i + matchedUrl.length
                    atomics.add(AtomicToken(AtomicKind.BARE_URL, i, end, matchedUrl, fullUrl))
                    for (k in i until end) protectedChars[k] = true
                    i = end
                    continue
                }
            }

            i++
        }

        // Pass 2: Match emphasis (*), underscores (_ / __), strike (~~), highlight (==), and inline HTML tags
        val starStack = mutableListOf<StarRun>()
        val markerStack = mutableListOf<MarkerOpener>()

        i = 0
        while (i < n) {
            if (protectedChars[i]) {
                i++
                continue
            }

            // A. Asterisk runs (*, **, ***, etc.) - CommonMark-style stack matching for arbitrary combinations
            if (rawText[i] == '*') {
                var j = i + 1
                while (j < n && rawText[j] == '*' && !protectedChars[j]) {
                    j++
                }
                val runLen = j - i
                val before = if (i > 0) rawText[i - 1] else null
                val after = if (j < n) rawText[j] else null
                val beforeWs = before == null || before.isWhitespace()
                val afterWs = after == null || after.isWhitespace()

                if (runLen == 1 && beforeWs && afterWs) {
                    // Standalone " * " (multiplication) stays literal
                    i = j
                    continue
                }

                val canClose = !beforeWs
                val canOpen = !afterWs
                var remStart = i
                val remEnd = j

                while (remEnd > remStart && canClose) {
                    val openerIdx = starStack.indexOfLast { it.remaining > 0 }
                    if (openerIdx == -1) break
                    val opener = starStack[openerIdx]
                    if (opener.end == remStart) break

                    val take = if (opener.remaining >= 2 && (remEnd - remStart) >= 2) 2 else 1
                    val openConsumeStart = opener.end - take
                    val openConsumeEnd = opener.end
                    opener.end -= take

                    val closeConsumeStart = remStart
                    val closeConsumeEnd = remStart + take
                    remStart += take

                    for (k in openConsumeStart until openConsumeEnd) hidden[k] = true
                    for (k in closeConsumeStart until closeConsumeEnd) hidden[k] = true

                    val kind = if (take == 2) StyleKind.BOLD else StyleKind.ITALIC
                    styleSpans.add(StyleSpan(kind, openConsumeEnd, closeConsumeStart))

                    if (opener.remaining == 0) {
                        starStack.removeAt(openerIdx)
                    }
                }

                if (remEnd > remStart && canOpen) {
                    starStack.add(StarRun(start = remStart, end = remEnd, canOpen = canOpen, canClose = canClose))
                }

                i = j
                continue
            }

            // B. Inline HTML tags (<u>, <b>, <strong>, <i>, <em>, <s>, <del>, <mark>, <q>, <blockquote>)
            if (rawText[i] == '<') {
                val tag = matchInlineHtmlTag(rawText, i)
                if (tag != null && (i until i + tag.length).none { protectedChars[it] }) {
                    val tagEnd = i + tag.length
                    if (tag.isClosing) {
                        val openIdx = markerStack.indexOfLast { it.key == tag.groupKey }
                        if (openIdx != -1) {
                            val opener = markerStack.removeAt(openIdx)
                            for (k in opener.openStart until opener.openEnd) hidden[k] = true
                            for (k in i until tagEnd) hidden[k] = true
                            styleSpans.add(StyleSpan(opener.styleKind, opener.openEnd, i))
                        }
                    } else {
                        markerStack.add(MarkerOpener(tag.groupKey, tag.styleKind, i, tagEnd))
                    }
                    i = tagEnd
                    continue
                }
            }

            // C. Two-character symmetrical markers: ~~, ==, __
            val twoCharMarker = when {
                rawText.startsWith("~~", i) && i + 1 < n && !protectedChars[i + 1] -> "~~" to StyleKind.STRIKETHROUGH
                rawText.startsWith("==", i) && i + 1 < n && !protectedChars[i + 1] -> "==" to StyleKind.HIGHLIGHT
                rawText.startsWith("__", i) && i + 1 < n && !protectedChars[i + 1] -> "__" to StyleKind.UNDERLINE
                else -> null
            }
            if (twoCharMarker != null) {
                val (key, kind) = twoCharMarker
                val markerEnd = i + 2
                val before = if (i > 0) rawText[i - 1] else null
                val after = if (markerEnd < n) rawText[markerEnd] else null
                val openIdx = markerStack.indexOfLast { it.key == key }
                val canClose = openIdx != -1 && markerStack[openIdx].openEnd < i && before != null && !before.isWhitespace()
                val canOpen = after != null && !after.isWhitespace()

                if (canClose) {
                    val opener = markerStack.removeAt(openIdx)
                    for (k in opener.openStart until opener.openEnd) hidden[k] = true
                    for (k in i until markerEnd) hidden[k] = true
                    styleSpans.add(StyleSpan(opener.styleKind, opener.openEnd, i))
                } else if (canOpen) {
                    markerStack.add(MarkerOpener(key, kind, i, markerEnd))
                }
                i = markerEnd
                continue
            }

            // D. Single underscore _italic_ (respecting word boundaries so snake_case_ids stay literal)
            if (rawText[i] == '_') {
                val before = if (i > 0) rawText[i - 1] else null
                val after = if (i + 1 < n) rawText[i + 1] else null
                val openIdx = markerStack.indexOfLast { it.key == "_" }
                val canClose = openIdx != -1 &&
                    markerStack[openIdx].openEnd < i &&
                    before != null && !before.isWhitespace() &&
                    (after == null || !after.isLetterOrDigit())
                val canOpen = after != null && !after.isWhitespace() &&
                    (before == null || !before.isLetterOrDigit())

                if (canClose) {
                    val opener = markerStack.removeAt(openIdx)
                    hidden[opener.openStart] = true
                    hidden[i] = true
                    styleSpans.add(StyleSpan(StyleKind.ITALIC, opener.openEnd, i))
                } else if (canOpen) {
                    markerStack.add(MarkerOpener("_", StyleKind.ITALIC, i, i + 1))
                }
                i++
                continue
            }

            i++
        }

        return AnalyzedInline(
            styleSpans = styleSpans,
            hidden = hidden,
            atomics = atomics,
            linkLabels = linkLabels
        )
    }

    private fun matchInlineHtmlTag(text: String, pos: Int): HtmlTagMatch? {
        val candidates = listOf(
            "<u>" to HtmlTagMatch("tag_u", StyleKind.UNDERLINE, false, 3),
            "</u>" to HtmlTagMatch("tag_u", StyleKind.UNDERLINE, true, 4),
            "<b>" to HtmlTagMatch("tag_b", StyleKind.BOLD, false, 3),
            "</b>" to HtmlTagMatch("tag_b", StyleKind.BOLD, true, 4),
            "<strong>" to HtmlTagMatch("tag_b", StyleKind.BOLD, false, 8),
            "</strong>" to HtmlTagMatch("tag_b", StyleKind.BOLD, true, 9),
            "<i>" to HtmlTagMatch("tag_i", StyleKind.ITALIC, false, 3),
            "</i>" to HtmlTagMatch("tag_i", StyleKind.ITALIC, true, 4),
            "<em>" to HtmlTagMatch("tag_i", StyleKind.ITALIC, false, 4),
            "</em>" to HtmlTagMatch("tag_i", StyleKind.ITALIC, true, 5),
            "<s>" to HtmlTagMatch("tag_s", StyleKind.STRIKETHROUGH, false, 3),
            "</s>" to HtmlTagMatch("tag_s", StyleKind.STRIKETHROUGH, true, 4),
            "<del>" to HtmlTagMatch("tag_s", StyleKind.STRIKETHROUGH, false, 5),
            "</del>" to HtmlTagMatch("tag_s", StyleKind.STRIKETHROUGH, true, 6),
            "<mark>" to HtmlTagMatch("tag_mark", StyleKind.HIGHLIGHT, false, 6),
            "</mark>" to HtmlTagMatch("tag_mark", StyleKind.HIGHLIGHT, true, 7),
            "<q>" to HtmlTagMatch("tag_quote", StyleKind.QUOTE, false, 3),
            "</q>" to HtmlTagMatch("tag_quote", StyleKind.QUOTE, true, 4),
            "<blockquote>" to HtmlTagMatch("tag_quote", StyleKind.QUOTE, false, 12),
            "</blockquote>" to HtmlTagMatch("tag_quote", StyleKind.QUOTE, true, 13)
        )
        return candidates.firstOrNull { text.startsWith(it.first, pos, ignoreCase = true) }?.second
    }

    /**
     * Parses inline markdown markers with full synergistic combination of styles:
     * Negrito + Itálico + Tachado + Sublinhado + Destaque + Código + Citação + Links externos.
     */
    fun parseInlineFormatting(
        rawText: String,
        isDark: Boolean,
        linkColor: Color = Color(0xFFF97316)
    ): AnnotatedString {
        if (rawText.isEmpty()) return AnnotatedString("")

        val codeBgColor = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
        val baseTextColor = if (isDark) Color.White else Color(0xFF1E293B)
        val analyzed = analyzeInlineSpans(rawText)
        val atomicByStart = analyzed.atomics.associateBy { it.start }
        val sortedLinks = analyzed.linkLabels.sortedBy { it.labelStart }
        val n = rawText.length

        return buildAnnotatedString {
            var currentLink: LinkLabelSpan? = null
            var linkIdx = 0

            fun syncLinkAt(pos: Int) {
                val active = currentLink
                if (active != null && pos >= active.labelEnd) {
                    withStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.SemiBold)) {
                        append(" 🔗")
                    }
                    pop()
                    currentLink = null
                }
                while (currentLink == null && linkIdx < sortedLinks.size && sortedLinks[linkIdx].labelStart <= pos) {
                    val nextLink = sortedLinks[linkIdx++]
                    if (pos <= nextLink.labelEnd) {
                        pushStringAnnotation(tag = "LINK", annotation = nextLink.fullUrl)
                        if (nextLink.labelStart == nextLink.labelEnd) {
                            withStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.SemiBold)) {
                                append(" 🔗")
                            }
                            pop()
                        } else {
                            currentLink = nextLink
                        }
                    }
                }
            }

            var i = 0
            while (i < n) {
                syncLinkAt(i)

                val atomic = atomicByStart[i]
                if (atomic != null) {
                    val ctx = contextAt(i, analyzed.styleSpans, currentLink)
                    when (atomic.kind) {
                        AtomicKind.MATH_BLOCK -> append(LatexFormulaRenderer.formatAnnotatedBlock(atomic.content, isDark, linkColor))
                        AtomicKind.MATH_INLINE -> append(LatexFormulaRenderer.formatAnnotatedInline(atomic.content, isDark))
                        AtomicKind.CODE -> appendStyledToken(
                            " ${atomic.content} ",
                            ctx.copy(isCode = true),
                            isDark,
                            linkColor,
                            codeBgColor,
                            baseTextColor,
                            this
                        )
                        AtomicKind.BARE_URL -> {
                            pushStringAnnotation(tag = "LINK", annotation = atomic.extraUrl)
                            appendStyledToken(
                                "${atomic.content} 🔗",
                                ctx.copy(linkUrl = atomic.extraUrl, isUnderline = true),
                                isDark,
                                linkColor,
                                codeBgColor,
                                baseTextColor,
                                this
                            )
                            pop()
                        }
                    }
                    i = atomic.end
                    continue
                }

                if (analyzed.hidden[i]) {
                    i++
                    continue
                }

                val ctx = contextAt(i, analyzed.styleSpans, currentLink)
                var endRun = i + 1
                while (
                    endRun < n &&
                    !analyzed.hidden[endRun] &&
                    atomicByStart[endRun] == null &&
                    (currentLink == null || endRun < currentLink!!.labelEnd) &&
                    (linkIdx >= sortedLinks.size || endRun < sortedLinks[linkIdx].labelStart) &&
                    contextAt(endRun, analyzed.styleSpans, currentLink) == ctx
                ) {
                    endRun++
                }
                appendStyledToken(
                    rawText.substring(i, endRun),
                    ctx,
                    isDark,
                    linkColor,
                    codeBgColor,
                    baseTextColor,
                    this
                )
                i = endRun
            }

            syncLinkAt(n)
        }
    }

    private fun contextAt(
        pos: Int,
        styleSpans: List<StyleSpan>,
        currentLink: LinkLabelSpan?
    ): InlineStyleContext {
        var isBold = false
        var isItalic = false
        var isStrike = false
        var isUnderline = currentLink != null
        var isHighlight = false
        var isQuote = false

        for (span in styleSpans) {
            if (pos in span.start until span.end) {
                when (span.kind) {
                    StyleKind.BOLD -> isBold = true
                    StyleKind.ITALIC -> isItalic = true
                    StyleKind.STRIKETHROUGH -> isStrike = true
                    StyleKind.UNDERLINE -> isUnderline = true
                    StyleKind.HIGHLIGHT -> isHighlight = true
                    StyleKind.QUOTE -> isQuote = true
                }
            }
        }

        return InlineStyleContext(
            isBold = isBold,
            isItalic = isItalic,
            isStrikethrough = isStrike,
            isUnderline = isUnderline,
            isHighlight = isHighlight,
            isCode = false,
            isQuote = isQuote,
            linkUrl = currentLink?.fullUrl
        )
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
                else -> Color.Unspecified
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

    private val PIPE_LINK_REGEX = Regex("^([a-zA-Z0-9.:/_-]+)\\|([^\n]+)")
    private val BARE_URL_REGEX = Regex("^(https?://[a-zA-Z0-9._~:/?#\\[\\]@!$&'()*+,;=-]+|www\\.[a-zA-Z0-9._~:/?#\\[\\]@!$&'()*+,;=-]+)")
}
