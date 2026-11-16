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

    @Test
    fun extensive_formatted_paste_does_not_crash_and_preserves_length_invariant() {
        val sampleSection = buildString {
            append("# Título Principal com **Negrito** e ==Destaque==\r\n")
            append("## Subtítulo com _Itálico_ e ~~Tachado~~ e <u>Sublinhado</u>\r\n")
            append("- [ ] Tarefa pendente com ***~~<u>==tudo==</u>~~***\n")
            append("- [x] Tarefa concluída com [Link **Formatado**](https://example.com/a_b?x=1&y=2)\n")
            append("> Citação importante com `codigoInline()` e \$x^2 + y^2 = z^2\$\n")
            append("| Col 1 | Col 2 |\n| --- | --- |\n| Val 1 | Val 2 |\n")
            append("```kotlin\nval texto = \"escaped \\\"quote\\\" " + "a".repeat(300) + "\"\n/* " + "c".repeat(300) + " */\n```\n")
            append("$$\n\\sum_{i=1}^{n} i^2 = \\frac{n(n+1)(2n+1)}{6}\n$$\n")
            append("Unclosed **bold *italic ~~strike <u>underline ==highlight `code [link(url\n")
            append("![img|50%|left|side:Texto](media/sample.jpg)\n")
            append("![oversized](data:image/png;base64," + "A".repeat(4000) + ")\n")
            append("---\n")
        }

        // Repeat until document exceeds 55,000 chars
        val hugeDoc = buildString(60_000) {
            append("---\ncitra_id: 1234-abcd-5678\n---\n")
            while (length < 55_000) {
                append(sampleSection)
            }
        }
        assertTrue(hugeDoc.length > 55_000)

        val startMs = System.currentTimeMillis()
        val liveStyled = ObsidianMarkdownEngine.buildLivePreviewAnnotatedString(
            text = hugeDoc,
            selection = TextRange(25_000),
            primaryColor = androidx.compose.ui.graphics.Color(0xFFEA580C),
            textColor = androidx.compose.ui.graphics.Color.Black,
            isDarkBg = false
        )
        // OffsetMapping.Identity requires exact UTF-16 character length match
        assertEquals(hugeDoc.length, liveStyled.text.length)

        val mediaTokens = ObsidianMarkdownEngine.parseMediaTokens(hugeDoc)
        assertTrue(mediaTokens.size <= 40)
        assertTrue(mediaTokens.none { it.url.startsWith("data:") && it.url.length > 512 })

        val blocks = com.example.ui.screens.organization.notes.SmoothMarkdownConverter.markdownToBlocks(hugeDoc)
        assertTrue(blocks.isNotEmpty())

        val highlighted = com.example.util.CodeHighlighter.highlight(
            code = "val s = \"" + "x\\\"".repeat(3_000) + "\"\n/* " + "y".repeat(3_000) + " */",
            language = "kotlin"
        )
        assertTrue(highlighted.text.isNotEmpty())

        val stripped = com.example.util.CitraDocsFileManager.stripFrontmatter(hugeDoc)
        assertEquals("1234-abcd-5678", com.example.util.CitraDocsFileManager.extractUuidFromMarkdown(hugeDoc))
        assertFalse(stripped.startsWith("---\ncitra_id:"))

        // Single-line massive paragraph with unclosed tokens must also finish rapidly without exception
        val megaParagraph = "**a** *b* ~~c~~ <u>d</u> ==e== `f` ".repeat(900) + "**unclosed"
        val readingStyled = InlineMarkdownHelper.parseInlineFormatting(megaParagraph, isDark = false)
        assertTrue(readingStyled.text.isNotEmpty())

        val elapsedMs = System.currentTimeMillis() - startMs
        assertTrue("Processing extensive paste took too long: ${elapsedMs}ms", elapsedMs < 5_000)
    }
}
