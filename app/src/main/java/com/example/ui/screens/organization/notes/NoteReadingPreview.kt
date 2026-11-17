package com.example.ui.screens.organization.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.backup.FinanceNoteMarkdownEngine
import com.example.ui.theme.isAppDarkTheme
import com.example.util.InlineMarkdownHelper
import com.example.util.LatexFormulaRenderer

enum class NotePreviewBlockKind {
    HEADING,
    TASK_CHECKED,
    TASK_UNCHECKED,
    BULLET,
    NUMBERED,
    QUOTE,
    CODE,
    MATH,
    TABLE,
    PARAGRAPH
}

data class NotePreviewElement(
    val kind: NotePreviewBlockKind,
    val text: String,
    val prefix: String = "",
    val headingLevel: Int = 0
)

object NotePreviewParser {

    private val HEADING_REGEX = Regex("^(#{1,6})\\s+(.+)$")
    private val CHECKLIST_REGEX = Regex("^[-*+]\\s+\\[([xX ])]\\s*(.*)$")
    private val BULLET_REGEX = Regex("^[-*+]\\s+(.+)$")
    private val NUMBERED_REGEX = Regex("^(\\d+\\.)\\s+(.+)$")
    private val QUOTE_REGEX = Regex("^>+\\s?(.*)$")
    private val CALLOUT_TAG_REGEX = Regex("^\\[![A-Za-z0-9_-]+]\\s*(.*)$")
    private val TABLE_SEP_REGEX = Regex("^\\|?\\s*:?-+:?\\s*(\\|\\s*:?-+:?\\s*)+\\|?$")
    private val HR_REGEX = Regex("^([-*_])\\1{2,}$")
    private val INLINE_IMAGE_OR_AUDIO_REGEX = Regex("!\\[(?:audio:)?[^\\]]*]\\([^)]*\\)")
    private val HTML_TAG_ONLY_LINE = Regex("^</?(br|hr|div|p|span|center)/?>$", RegexOption.IGNORE_CASE)

    fun parseElements(content: String, maxElements: Int = 5): List<NotePreviewElement> {
        if (content.isBlank() || maxElements <= 0) return emptyList()

        val cleanedContent = FinanceNoteMarkdownEngine.stripFinanceMarkers(content)
        val lines = cleanedContent.lines()
        val result = mutableListOf<NotePreviewElement>()

        var i = 0
        while (i < lines.size && result.size < maxElements) {
            val rawLine = lines[i]
            val trimmed = rawLine.trim()

            if (trimmed.isEmpty() || HR_REGEX.matches(trimmed) || HTML_TAG_ONLY_LINE.matches(trimmed)) {
                i++
                continue
            }

            // Fenced code block: ```lang ... ```
            if (trimmed.startsWith("```")) {
                val lang = trimmed.removePrefix("```").trim()
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    val codeLine = lines[i].trim()
                    if (codeLine.isNotEmpty() && codeLines.size < 2) {
                        codeLines.add(codeLine)
                    }
                    i++
                }
                if (i < lines.size) i++ // consume closing ```
                val snippet = codeLines.joinToString("  ").ifBlank { "..." }
                result.add(
                    NotePreviewElement(
                        kind = NotePreviewBlockKind.CODE,
                        text = snippet,
                        prefix = lang.uppercase().ifBlank { "CODE" }
                    )
                )
                continue
            }

            // Multi-line or single-line math block: $$...$$
            if (trimmed.startsWith("$$")) {
                val mathContent = if (trimmed.endsWith("$$") && trimmed.length > 4) {
                    trimmed.removePrefix("$$").removeSuffix("$$").trim()
                } else {
                    val buf = mutableListOf<String>()
                    val firstPart = trimmed.removePrefix("$$").trim()
                    if (firstPart.isNotEmpty()) buf.add(firstPart)
                    i++
                    while (i < lines.size && !lines[i].trim().endsWith("$$")) {
                        val mLine = lines[i].trim()
                        if (mLine.isNotEmpty()) buf.add(mLine)
                        i++
                    }
                    if (i < lines.size) {
                        val lastPart = lines[i].trim().removeSuffix("$$").trim()
                        if (lastPart.isNotEmpty()) buf.add(lastPart)
                    }
                    buf.joinToString(" ")
                }
                i++
                if (mathContent.isNotBlank()) {
                    result.add(
                        NotePreviewElement(
                            kind = NotePreviewBlockKind.MATH,
                            text = LatexFormulaRenderer.renderFormulaToText(mathContent)
                        )
                    )
                }
                continue
            }

            // Strip standalone media embeds from lines
            val withoutMedia = trimmed.replace(INLINE_IMAGE_OR_AUDIO_REGEX, "").trim()
            if (withoutMedia.isEmpty()) {
                i++
                continue
            }

            // Skip markdown table alignment separator lines
            if (TABLE_SEP_REGEX.matches(withoutMedia)) {
                i++
                continue
            }

            val headingMatch = HEADING_REGEX.matchEntire(withoutMedia)
            if (headingMatch != null) {
                result.add(
                    NotePreviewElement(
                        kind = NotePreviewBlockKind.HEADING,
                        text = headingMatch.groupValues[2].trim(),
                        headingLevel = headingMatch.groupValues[1].length
                    )
                )
                i++
                continue
            }

            val checklistMatch = CHECKLIST_REGEX.matchEntire(withoutMedia)
            if (checklistMatch != null) {
                val checked = checklistMatch.groupValues[1].equals("x", ignoreCase = true)
                val body = checklistMatch.groupValues[2].trim()
                if (body.isNotEmpty()) {
                    result.add(
                        NotePreviewElement(
                            kind = if (checked) NotePreviewBlockKind.TASK_CHECKED else NotePreviewBlockKind.TASK_UNCHECKED,
                            text = body
                        )
                    )
                }
                i++
                continue
            }

            val bulletMatch = BULLET_REGEX.matchEntire(withoutMedia)
            if (bulletMatch != null) {
                result.add(
                    NotePreviewElement(
                        kind = NotePreviewBlockKind.BULLET,
                        text = bulletMatch.groupValues[1].trim(),
                        prefix = "•"
                    )
                )
                i++
                continue
            }

            val numberedMatch = NUMBERED_REGEX.matchEntire(withoutMedia)
            if (numberedMatch != null) {
                result.add(
                    NotePreviewElement(
                        kind = NotePreviewBlockKind.NUMBERED,
                        text = numberedMatch.groupValues[2].trim(),
                        prefix = numberedMatch.groupValues[1]
                    )
                )
                i++
                continue
            }

            val quoteMatch = QUOTE_REGEX.matchEntire(withoutMedia)
            if (quoteMatch != null) {
                var quoteText = quoteMatch.groupValues[1].trim()
                val calloutMatch = CALLOUT_TAG_REGEX.matchEntire(quoteText)
                if (calloutMatch != null) {
                    quoteText = calloutMatch.groupValues[1].trim()
                }
                if (quoteText.isNotEmpty()) {
                    result.add(
                        NotePreviewElement(
                            kind = NotePreviewBlockKind.QUOTE,
                            text = quoteText
                        )
                    )
                }
                i++
                continue
            }

            // Markdown table row
            if (withoutMedia.startsWith("|") && withoutMedia.endsWith("|")) {
                val cells = withoutMedia
                    .trim('|')
                    .split("|")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                if (cells.isNotEmpty()) {
                    result.add(
                        NotePreviewElement(
                            kind = NotePreviewBlockKind.TABLE,
                            text = cells.joinToString("  ·  ")
                        )
                    )
                }
                i++
                continue
            }

            result.add(
                NotePreviewElement(
                    kind = NotePreviewBlockKind.PARAGRAPH,
                    text = withoutMedia
                )
            )
            i++
        }

        return result
    }
}

/**
 * Renders a mini Reading-Mode preview of Markdown content for note cards on the Notes tab.
 * Uses InlineMarkdownHelper so bold, italic, underline, strikethrough, highlight, code, and links
 * appear styled consistently with Reading Mode without showing note background images.
 */
@Composable
fun NoteReadingPreview(
    content: String,
    modifier: Modifier = Modifier,
    maxElements: Int = 4,
    maxTotalLines: Int = 5,
    compact: Boolean = false,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    secondaryTextColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val isDark = isAppDarkTheme()
    val elements = remember(content, maxElements) {
        NotePreviewParser.parseElements(content, maxElements = maxElements)
    }

    if (elements.isEmpty()) return

    val baseFontSize = if (compact) 11.sp else 12.sp
    val baseLineHeight = if (compact) 14.5.sp else 16.5.sp

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (compact) 2.5.dp else 4.dp)
    ) {
        var remainingLines = maxTotalLines.coerceAtLeast(1)

        elements.forEach { el ->
            if (remainingLines <= 0) return@forEach

            val elementLineCap = when (el.kind) {
                NotePreviewBlockKind.PARAGRAPH -> minOf(remainingLines, if (elements.size == 1) maxTotalLines else 2)
                else -> 1
            }

            when (el.kind) {
                NotePreviewBlockKind.HEADING -> {
                    val annotated = remember(el.text, isDark, accentColor) {
                        InlineMarkdownHelper.parseInlineFormatting(
                            rawText = el.text,
                            isDark = isDark,
                            linkColor = accentColor
                        )
                    }
                    Text(
                        text = annotated,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = when {
                            el.headingLevel <= 2 && compact -> 12.sp
                            el.headingLevel <= 2 -> 13.sp
                            compact -> 11.5.sp
                            else -> 12.5.sp
                        },
                        lineHeight = baseLineHeight,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    remainingLines -= 1
                }

                NotePreviewBlockKind.TASK_CHECKED, NotePreviewBlockKind.TASK_UNCHECKED -> {
                    val isChecked = el.kind == NotePreviewBlockKind.TASK_CHECKED
                    val annotated = remember(el.text, isDark, accentColor) {
                        InlineMarkdownHelper.parseInlineFormatting(
                            rawText = el.text,
                            isDark = isDark,
                            linkColor = accentColor
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = if (isChecked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                            contentDescription = null,
                            tint = if (isChecked) accentColor else secondaryTextColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = annotated,
                            style = MaterialTheme.typography.bodySmall.copy(
                                textDecoration = if (isChecked) TextDecoration.LineThrough else null
                            ),
                            fontSize = baseFontSize,
                            lineHeight = baseLineHeight,
                            color = if (isChecked) secondaryTextColor.copy(alpha = 0.55f) else secondaryTextColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    remainingLines -= 1
                }

                NotePreviewBlockKind.BULLET, NotePreviewBlockKind.NUMBERED -> {
                    val annotated = remember(el.text, isDark, accentColor) {
                        InlineMarkdownHelper.parseInlineFormatting(
                            rawText = el.text,
                            isDark = isDark,
                            linkColor = accentColor
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = el.prefix,
                            fontSize = baseFontSize,
                            lineHeight = baseLineHeight,
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        )
                        Text(
                            text = annotated,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = baseFontSize,
                            lineHeight = baseLineHeight,
                            color = secondaryTextColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    remainingLines -= 1
                }

                NotePreviewBlockKind.QUOTE -> {
                    val annotated = remember(el.text, isDark, accentColor) {
                        InlineMarkdownHelper.parseInlineFormatting(
                            rawText = el.text,
                            isDark = isDark,
                            linkColor = accentColor
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(accentColor.copy(alpha = 0.08f))
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(2.5.dp)
                                .height(13.dp)
                                .background(accentColor, RoundedCornerShape(2.dp))
                        )
                        Text(
                            text = annotated,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = baseFontSize,
                            lineHeight = baseLineHeight,
                            fontStyle = FontStyle.Italic,
                            color = secondaryTextColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    remainingLines -= 1
                }

                NotePreviewBlockKind.CODE -> {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isDark) Color(0xFF0F172A).copy(alpha = 0.75f) else Color(0xFF1E293B).copy(alpha = 0.08f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = el.prefix,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = accentColor
                            )
                            Text(
                                text = el.text,
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace,
                                color = secondaryTextColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    remainingLines -= 1
                }

                NotePreviewBlockKind.MATH -> {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = accentColor.copy(alpha = 0.08f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = "∑",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = accentColor
                            )
                            Text(
                                text = el.text,
                                fontSize = baseFontSize,
                                fontFamily = FontFamily.Serif,
                                fontStyle = FontStyle.Italic,
                                color = textColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    remainingLines -= 1
                }

                NotePreviewBlockKind.TABLE, NotePreviewBlockKind.PARAGRAPH -> {
                    val annotated = remember(el.text, isDark, accentColor) {
                        InlineMarkdownHelper.parseInlineFormatting(
                            rawText = el.text,
                            isDark = isDark,
                            linkColor = accentColor
                        )
                    }
                    Text(
                        text = annotated,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = baseFontSize,
                        lineHeight = baseLineHeight,
                        color = secondaryTextColor,
                        maxLines = elementLineCap,
                        overflow = TextOverflow.Ellipsis
                    )
                    remainingLines -= elementLineCap
                }
            }
        }
    }
}
