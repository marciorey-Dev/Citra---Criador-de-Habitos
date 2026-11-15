package com.example

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import com.example.ui.screens.organization.notes.ObsidianMarkdownEngine
import com.example.util.InlineMarkdownHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InlineMarkdownHelperTest {

    private fun styleAt(annotated: androidx.compose.ui.text.AnnotatedString, target: String): List<SpanStyle> {
        val idx = annotated.text.indexOf(target)
        assertTrue("Expected '$target' in '${annotated.text}'", idx >= 0)
        val end = idx + target.length
        return annotated.spanStyles
            .filter { it.start <= idx && it.end >= end }
            .map { it.item }
    }

    @Test
    fun bold_works_in_reading_mode() {
        val res = InlineMarkdownHelper.parseInlineFormatting("Texto **negrito** normal", isDark = false)
        assertEquals("Texto negrito normal", res.text)
        val boldSpans = styleAt(res, "negrito")
        assertTrue(boldSpans.any { it.fontWeight == FontWeight.Bold })
        val normalSpans = styleAt(res, "normal")
        assertTrue(normalSpans.none { it.fontWeight == FontWeight.Bold })
    }

    @Test
    fun triple_asterisk_combines_bold_and_italic() {
        val res = InlineMarkdownHelper.parseInlineFormatting("***ambos***", isDark = false)
        assertEquals("ambos", res.text)
        val spans = styleAt(res, "ambos")
        assertTrue(spans.any { it.fontWeight == FontWeight.Bold && it.fontStyle == FontStyle.Italic })
    }

    @Test
    fun nested_bold_and_italic_shared_closing_stars_work() {
        val boldFirst = InlineMarkdownHelper.parseInlineFormatting("**negrito e *itálico***", isDark = false)
        assertEquals("negrito e itálico", boldFirst.text)
        assertTrue(styleAt(boldFirst, "negrito e ").any { it.fontWeight == FontWeight.Bold && it.fontStyle == null })
        assertTrue(styleAt(boldFirst, "itálico").any { it.fontWeight == FontWeight.Bold && it.fontStyle == FontStyle.Italic })

        val italicFirst = InlineMarkdownHelper.parseInlineFormatting("*itálico e **negrito***", isDark = false)
        assertEquals("itálico e negrito", italicFirst.text)
        assertTrue(styleAt(italicFirst, "itálico e ").any { it.fontStyle == FontStyle.Italic && it.fontWeight == null })
        assertTrue(styleAt(italicFirst, "negrito").any { it.fontWeight == FontWeight.Bold && it.fontStyle == FontStyle.Italic })
    }

    @Test
    fun full_synergy_bold_italic_strike_underline_highlight_combines() {
        val res = InlineMarkdownHelper.parseInlineFormatting(
            "***~~<u>==tudo==</u>~~*** e ==<u>~~**reverso**~~</u>==",
            isDark = false
        )
        assertEquals("tudo e reverso", res.text)

        val tudoStyles = styleAt(res, "tudo")
        val combinedDeco = TextDecoration.combine(listOf(TextDecoration.LineThrough, TextDecoration.Underline))
        assertTrue(
            tudoStyles.any {
                it.fontWeight == FontWeight.Bold &&
                    it.fontStyle == FontStyle.Italic &&
                    it.textDecoration == combinedDeco &&
                    it.background == InlineMarkdownHelper.HighlightYellow
            }
        )

        val revStyles = styleAt(res, "reverso")
        assertTrue(
            revStyles.any {
                it.fontWeight == FontWeight.Bold &&
                    it.textDecoration == combinedDeco &&
                    it.background == InlineMarkdownHelper.HighlightYellow
            }
        )
    }

    @Test
    fun snake_case_and_multiplication_stay_literal() {
        val res = InlineMarkdownHelper.parseInlineFormatting("var my_variable_name = 2 * 3 * 4", isDark = false)
        assertEquals("var my_variable_name = 2 * 3 * 4", res.text)
        assertTrue(res.spanStyles.none { it.item.fontStyle == FontStyle.Italic })
    }

    @Test
    fun markdown_link_with_styled_label_keeps_bold_and_url() {
        val res = InlineMarkdownHelper.parseInlineFormatting("[**Site** _oficial_](https://example.com/a_b*c)", isDark = false)
        assertEquals("Site oficial 🔗", res.text)
        assertTrue(styleAt(res, "Site").any { it.fontWeight == FontWeight.Bold && it.textDecoration == TextDecoration.Underline })
        assertTrue(styleAt(res, "oficial").any { it.fontStyle == FontStyle.Italic && it.textDecoration == TextDecoration.Underline })
        val links = res.getStringAnnotations("LINK", 0, res.length)
        assertFalse(links.isEmpty())
        assertEquals("https://example.com/a_b*c", links.first().item)
    }

    @Test
    fun toolbar_wrapSelection_combines_and_toggles_bold_and_italic() {
        val start = TextFieldValue("palavra", TextRange(0, 7))
        val afterBold = ObsidianMarkdownEngine.wrapSelection(start, "**", "**")
        assertEquals("**palavra**", afterBold.text)
        assertEquals(TextRange(2, 9), afterBold.selection)

        val afterItalic = ObsidianMarkdownEngine.wrapSelection(afterBold, "*", "*")
        assertEquals("***palavra***", afterItalic.text)
        assertEquals(TextRange(3, 10), afterItalic.selection)

        val afterToggleItalicOff = ObsidianMarkdownEngine.wrapSelection(afterItalic, "*", "*")
        assertEquals("**palavra**", afterToggleItalicOff.text)
        assertEquals(TextRange(2, 9), afterToggleItalicOff.selection)

        val afterToggleBoldOff = ObsidianMarkdownEngine.wrapSelection(afterToggleItalicOff, "**", "**")
        assertEquals("palavra", afterToggleBoldOff.text)
        assertEquals(TextRange(0, 7), afterToggleBoldOff.selection)
    }
}
