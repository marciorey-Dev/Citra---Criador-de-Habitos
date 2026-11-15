package com.example

import com.example.ui.screens.organization.notes.NotePreviewBlockKind
import com.example.ui.screens.organization.notes.NotePreviewParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotePreviewParserTest {

    @Test
    fun parses_headings_tasks_bullets_quotes_and_preserves_inline_styles() {
        val md = """
            # **Título** Importante
            ![foto](/storage/emulated/0/img.jpg)
            - [ ] Comprar ==café==
            - [x] Enviar *relatório*
            - Ponto com ~~rasura~~ e <u>sublinhado</u>
            > Citação **forte**
            Texto final normal
        """.trimIndent()

        val elements = NotePreviewParser.parseElements(md, maxElements = 6)
        assertEquals(6, elements.size)

        assertEquals(NotePreviewBlockKind.HEADING, elements[0].kind)
        assertEquals("**Título** Importante", elements[0].text)

        assertEquals(NotePreviewBlockKind.TASK_UNCHECKED, elements[1].kind)
        assertEquals("Comprar ==café==", elements[1].text)

        assertEquals(NotePreviewBlockKind.TASK_CHECKED, elements[2].kind)
        assertEquals("Enviar *relatório*", elements[2].text)

        assertEquals(NotePreviewBlockKind.BULLET, elements[3].kind)
        assertEquals("Ponto com ~~rasura~~ e <u>sublinhado</u>", elements[3].text)

        assertEquals(NotePreviewBlockKind.QUOTE, elements[4].kind)
        assertEquals("Citação **forte**", elements[4].text)

        assertEquals(NotePreviewBlockKind.PARAGRAPH, elements[5].kind)
        assertEquals("Texto final normal", elements[5].text)
    }

    @Test
    fun collapses_code_and_math_blocks_cleanly() {
        val md = """
            ```kotlin
            fun hello() {
                println("Citra")
            }
            ```
            $$
            2^3 + \sqrt{9}
            $$
        """.trimIndent()

        val elements = NotePreviewParser.parseElements(md, maxElements = 4)
        assertEquals(2, elements.size)

        assertEquals(NotePreviewBlockKind.CODE, elements[0].kind)
        assertEquals("KOTLIN", elements[0].prefix)
        assertTrue(elements[0].text.contains("fun hello()"))

        assertEquals(NotePreviewBlockKind.MATH, elements[1].kind)
        assertTrue(elements[1].text.isNotBlank())
    }
}
