package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.data.backup.BackupManager
import com.example.data.model.Note
import com.example.data.model.NoteDocumentType
import com.example.data.model.isDocxDocument
import com.example.data.model.isExternalDocument
import com.example.data.model.isPdfDocument
import com.example.data.model.resolvedDocumentType
import com.example.ui.screens.organization.notes.BulletBlock
import com.example.ui.screens.organization.notes.CalloutBlock
import com.example.ui.screens.organization.notes.ChecklistBlock
import com.example.ui.screens.organization.notes.CodeBlock
import com.example.ui.screens.organization.notes.HeadingBlock
import com.example.ui.screens.organization.notes.ImageBlock
import com.example.ui.screens.organization.notes.MathBlock
import com.example.ui.screens.organization.notes.NoteDocumentFilter
import com.example.ui.screens.organization.notes.NoteSortOption
import com.example.ui.screens.organization.notes.QuoteBlock
import com.example.ui.screens.organization.notes.SmoothMarkdownConverter
import com.example.ui.screens.organization.notes.TableBlock
import com.example.ui.screens.organization.notes.filterNotesByDocumentType
import com.example.ui.screens.organization.notes.groupNotesByCategory
import com.example.ui.screens.organization.notes.sortNotes
import com.example.util.DocxConversionResult
import com.example.util.DocxDocumentEngine
import com.example.util.DocxRun
import com.example.util.InlineMarkdownHelper
import com.example.util.PdfDocumentEngine
import com.example.util.PdfStyledSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExternalDocumentEngineTest {

    private fun buildSyntheticDocx(
        documentXml: String,
        stylesXml: String? = null,
        numberingXml: String? = null,
        relsXml: String? = null,
        media: Map<String, ByteArray> = emptyMap()
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            fun addEntry(name: String, bytes: ByteArray) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(bytes)
                zos.closeEntry()
            }
            addEntry("word/document.xml", documentXml.toByteArray(Charsets.UTF_8))
            if (stylesXml != null) addEntry("word/styles.xml", stylesXml.toByteArray(Charsets.UTF_8))
            if (numberingXml != null) addEntry("word/numbering.xml", numberingXml.toByteArray(Charsets.UTF_8))
            if (relsXml != null) addEntry("word/_rels/document.xml.rels", relsXml.toByteArray(Charsets.UTF_8))
            media.forEach { (path, bytes) -> addEntry(path, bytes) }
        }
        return baos.toByteArray()
    }

    @Test
    fun `docx formatRunsToMarkdown produces valid combinable inline citra markdown`() {
        val runs = listOf(
            DocxRun("Texto "),
            DocxRun("negrito", isBold = true),
            DocxRun(" + "),
            DocxRun("combinado ", isBold = true, isItalic = true, isUnderline = true, isStrikethrough = true, isHighlight = true),
            DocxRun("println()", isCode = true),
            DocxRun(" e "),
            DocxRun("Citra", isBold = true, linkUrl = "https://example.org")
        )

        val md = DocxDocumentEngine.formatRunsToMarkdown(runs)
        assertTrue(md.contains("**negrito**"))
        assertTrue(md.contains("***==~~<u>combinado</u>~~==*** "))
        assertTrue(md.contains("`println()`"))
        assertTrue(md.contains("[**Citra**](https://example.org)"))

        val annotated = InlineMarkdownHelper.parseInlineFormatting(md, isDark = false)
        assertFalse("Rendered text must strip markdown markers", annotated.text.contains("**"))
        assertFalse("Rendered text must strip highlight markers", annotated.text.contains("=="))
        assertTrue(annotated.text.contains("combinado"))
        assertTrue(annotated.text.contains("Citra"))
    }

    @Test
    fun `docx engine parses headings lists checklists tables code math callouts and converts cleanly to citra blocks`() {
        val stylesXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:style w:type="paragraph" w:styleId="Heading1">
                <w:name w:val="heading 1"/>
                <w:pPr><w:outlineLvl w:val="0"/></w:pPr>
                <w:rPr><w:b/><w:sz w:val="44"/></w:rPr>
              </w:style>
              <w:style w:type="paragraph" w:styleId="Heading2">
                <w:name w:val="heading 2"/>
                <w:pPr><w:outlineLvl w:val="1"/></w:pPr>
                <w:rPr><w:b/><w:sz w:val="34"/></w:rPr>
              </w:style>
            </w:styles>
        """.trimIndent()

        val numberingXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:numbering xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:abstractNum w:abstractNumId="1">
                <w:lvl w:ilvl="0"><w:numFmt w:val="bullet"/></w:lvl>
              </w:abstractNum>
              <w:abstractNum w:abstractNumId="2">
                <w:lvl w:ilvl="0"><w:numFmt w:val="decimal"/></w:lvl>
              </w:abstractNum>
              <w:num w:numId="10"><w:abstractNumId w:val="1"/></w:num>
              <w:num w:numId="20"><w:abstractNumId w:val="2"/></w:num>
            </w:numbering>
        """.trimIndent()

        val relsXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rIdLink1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink" Target="https://citra.app" TargetMode="External"/>
              <Relationship Id="rIdImg1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/photo.png"/>
            </Relationships>
        """.trimIndent()

        val documentXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
                        xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
                        xmlns:m="http://schemas.openxmlformats.org/officeDocument/2006/math"
                        xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
              <w:body>
                <w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>Guia Citra</w:t></w:r></w:p>
                <w:p><w:pPr><w:pStyle w:val="Heading2"/></w:pPr><w:r><w:t>Resumo</w:t></w:r></w:p>
                <w:p>
                  <w:r><w:t>Veja </w:t></w:r>
                  <w:hyperlink r:id="rIdLink1"><w:r><w:rPr><w:b/><w:i/></w:rPr><w:t>documentação</w:t></w:r></w:hyperlink>
                </w:p>
                <w:p><w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="10"/></w:numPr></w:pPr><w:r><w:t>Item em tópico</w:t></w:r></w:p>
                <w:p><w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="20"/></w:numPr></w:pPr><w:r><w:t>Passo um</w:t></w:r></w:p>
                <w:p><w:r><w:t>☐ Revisar pauta</w:t></w:r></w:p>
                <w:p><w:r><w:t>☑ Enviar relatório</w:t></w:r></w:p>
                <w:p>
                  <w:pPr><w:pBdr><w:left w:val="single" w:color="F97316"/></w:pBdr></w:pPr>
                  <w:r><w:t>Citação importante</w:t></w:r>
                </w:p>
                <w:p>
                  <w:pPr><w:shd w:val="clear" w:fill="ECFDF5"/></w:pPr>
                  <w:r><w:t>Dica útil para o usuário</w:t></w:r>
                </w:p>
                <w:p>
                  <w:pPr><w:shd w:val="clear" w:fill="1E293B"/></w:pPr>
                  <w:r><w:rPr><w:rFonts w:ascii="Courier New"/></w:rPr><w:t>val x = 42</w:t></w:r>
                </w:p>
                <w:p>
                  <m:oMathPara><m:oMath><m:r><m:t>E = mc^2</m:t></m:r></m:oMath></m:oMathPara>
                </w:p>
                <w:tbl>
                  <w:tr>
                    <w:tc><w:p><w:r><w:t>Coluna A</w:t></w:r></w:p></w:tc>
                    <w:tc><w:p><w:r><w:t>Coluna B</w:t></w:r></w:p></w:tc>
                  </w:tr>
                  <w:tr>
                    <w:tc><w:p><w:r><w:t>Valor 1</w:t></w:r></w:p></w:tc>
                    <w:tc><w:p><w:r><w:t>Valor 2</w:t></w:r></w:p></w:tc>
                  </w:tr>
                </w:tbl>
                <w:p><w:r><w:drawing><a:blip r:embed="rIdImg1"/></w:drawing></w:r></w:p>
              </w:body>
            </w:document>
        """.trimIndent()

        val bytes = buildSyntheticDocx(
            documentXml = documentXml,
            stylesXml = stylesXml,
            numberingXml = numberingXml,
            relsXml = relsXml,
            media = mapOf("word/media/photo.png" to byteArrayOf(1, 2, 3, 4))
        )

        val result = DocxDocumentEngine.convertToMarkdown(ByteArrayInputStream(bytes)) { imgBytes, ext ->
            assertEquals(4, imgBytes.size)
            assertEquals("png", ext)
            "/storage/emulated/0/Pictures/Citra Pic/converted.png"
        }

        assertTrue(result is DocxConversionResult.Success)
        val md = (result as DocxConversionResult.Success).markdown

        assertTrue(md.contains("# Guia Citra"))
        assertTrue(md.contains("## Resumo"))
        assertTrue(md.contains("[***documentação***](https://citra.app)"))
        assertTrue(md.contains("- Item em tópico"))
        assertTrue(md.contains("1. Passo um"))
        assertTrue(md.contains("- [ ] Revisar pauta"))
        assertTrue(md.contains("- [x] Enviar relatório"))
        assertTrue(md.contains("> Citação importante"))
        assertTrue(md.contains("> [!TIP] Dica útil para o usuário"))
        assertTrue(md.contains("```"))
        assertTrue(md.contains("val x = 42"))
        assertTrue(md.contains("$$ E = mc^2 $$"))
        assertTrue(md.contains("| Coluna A | Coluna B |"))
        assertTrue(md.contains("| Valor 1 | Valor 2 |"))
        assertTrue(md.contains("converted.png"))

        // Round-trip through Citra's own SmoothMarkdownConverter
        val citraBlocks = SmoothMarkdownConverter.markdownToBlocks(md)
        assertTrue(citraBlocks.any { it is HeadingBlock && it.level == 1 && it.text == "Guia Citra" })
        assertTrue(citraBlocks.any { it is HeadingBlock && it.level == 2 && it.text == "Resumo" })
        assertTrue(citraBlocks.any { it is BulletBlock && it.text == "Item em tópico" })
        assertTrue(citraBlocks.any { it is ChecklistBlock && !it.isChecked && it.text == "Revisar pauta" })
        assertTrue(citraBlocks.any { it is ChecklistBlock && it.isChecked && it.text == "Enviar relatório" })
        assertTrue(citraBlocks.any { it is QuoteBlock })
        assertTrue(citraBlocks.any { it is CalloutBlock && it.type == "tip" })
        assertTrue(citraBlocks.any { it is CodeBlock && it.code.contains("val x = 42") })
        assertTrue(citraBlocks.any { it is MathBlock && it.formula == "E = mc^2" })
        assertTrue(citraBlocks.any { it is TableBlock && it.headers == listOf("Coluna A", "Coluna B") })
        assertTrue(citraBlocks.any { it is ImageBlock && it.url.endsWith("converted.png") })
    }

    @Test
    fun `pdf structured layout engine maps headings inline formatting lists tables code blocks and strips page numbers`() {
        val page1 = listOf(
            // Title (20pt)
            PdfStyledSegment("Relatório Anual", xStart = 50f, xEnd = 220f, y = 80f, fontSize = 20f, isBold = true),
            // Subtitle (15pt)
            PdfStyledSegment("Objetivos Gerais", xStart = 50f, xEnd = 190f, y = 115f, fontSize = 15f, isBold = true),
            // Normal body prose (11pt)
            PdfStyledSegment("Texto principal com ", xStart = 50f, xEnd = 160f, y = 145f, fontSize = 11f),
            PdfStyledSegment("ênfase", xStart = 162f, xEnd = 205f, y = 145f, fontSize = 11f, isBold = true),
            PdfStyledSegment(" e ", xStart = 207f, xEnd = 220f, y = 145f, fontSize = 11f),
            PdfStyledSegment("itálico", xStart = 222f, xEnd = 260f, y = 145f, fontSize = 11f, isItalic = true),
            // Body extra lines so median size == 11f reliably
            PdfStyledSegment("Continuação normal da linha seguinte.", xStart = 50f, xEnd = 280f, y = 162f, fontSize = 11f),
            PdfStyledSegment("Mais uma linha de corpo comum.", xStart = 50f, xEnd = 250f, y = 179f, fontSize = 11f),
            // Bullet and checklist
            PdfStyledSegment("• Item de lista", xStart = 60f, xEnd = 180f, y = 210f, fontSize = 11f),
            PdfStyledSegment("[ ] Tarefa aberta", xStart = 60f, xEnd = 190f, y = 230f, fontSize = 11f),
            PdfStyledSegment("[x] Tarefa feita", xStart = 60f, xEnd = 190f, y = 250f, fontSize = 11f),
            // Monospace code block
            PdfStyledSegment("fun citra() =", xStart = 60f, xEnd = 160f, y = 290f, fontSize = 10f, isMonospace = true),
            PdfStyledSegment("    return true", xStart = 60f, xEnd = 170f, y = 306f, fontSize = 10f, isMonospace = true),
            // Two-column table
            PdfStyledSegment("Mês", xStart = 50f, xEnd = 100f, y = 350f, fontSize = 11f, isBold = true),
            PdfStyledSegment("Valor", xStart = 220f, xEnd = 280f, y = 350f, fontSize = 11f, isBold = true),
            PdfStyledSegment("Jan", xStart = 50f, xEnd = 95f, y = 372f, fontSize = 11f),
            PdfStyledSegment("R$ 500", xStart = 220f, xEnd = 285f, y = 372f, fontSize = 11f),
            // Running page footer that should be stripped
            PdfStyledSegment("Página 1 de 2", xStart = 240f, xEnd = 310f, y = 760f, fontSize = 9f, pageNumber = 1)
        )

        val md = PdfDocumentEngine.convertStructuredPagesToMarkdown(listOf(page1))

        assertTrue(md.contains("# Relatório Anual"))
        assertTrue(md.contains("## Objetivos Gerais"))
        assertTrue(md.contains("**ênfase**"))
        assertTrue(md.contains("*itálico*"))
        assertTrue(md.contains("- Item de lista"))
        assertTrue(md.contains("- [ ] Tarefa aberta"))
        assertTrue(md.contains("- [x] Tarefa feita"))
        assertTrue(md.contains("```"))
        assertTrue(md.contains("fun citra() ="))
        assertTrue(md.contains("| **Mês** | **Valor** |") || md.contains("| Mês | Valor |"))
        assertTrue(md.contains("| Jan | R$ 500 |"))
        assertFalse("Running page footer must be stripped", md.contains("Página 1 de 2"))

        val citraBlocks = SmoothMarkdownConverter.markdownToBlocks(md)
        assertTrue(citraBlocks.any { it is HeadingBlock && it.text == "Relatório Anual" })
        assertTrue(citraBlocks.any { it is ChecklistBlock && !it.isChecked })
        assertTrue(citraBlocks.any { it is ChecklistBlock && it.isChecked })
        assertTrue(citraBlocks.any { it is CodeBlock })
        assertTrue(citraBlocks.any { it is TableBlock })
    }

    @Test
    fun `document filters and category grouping organize markdown pdf and docx together`() {
        val mdNote = Note(
            id = 1L,
            title = "Anotação Markdown",
            content = "Conteúdo normal",
            category = "Estudos",
            documentType = NoteDocumentType.MARKDOWN.id
        )
        val pdfNote = Note(
            id = 2L,
            title = "Apostila PDF",
            content = "",
            category = "Estudos",
            documentType = NoteDocumentType.PDF.id,
            externalUri = "content://com.android.providers.downloads/document/101"
        )
        val docxNote = Note(
            id = 3L,
            title = "Contrato Word",
            content = "",
            category = "Trabalho",
            documentType = NoteDocumentType.DOCX.id,
            externalUri = "content://com.android.providers.downloads/document/202"
        )

        assertFalse(mdNote.isExternalDocument)
        assertTrue(pdfNote.isExternalDocument)
        assertTrue(pdfNote.isPdfDocument)
        assertFalse(pdfNote.isDocxDocument)
        assertTrue(docxNote.isExternalDocument)
        assertTrue(docxNote.isDocxDocument)
        assertEquals(NoteDocumentType.PDF, pdfNote.resolvedDocumentType)

        val all = listOf(mdNote, pdfNote, docxNote)
        assertEquals(3, filterNotesByDocumentType(all, NoteDocumentFilter.ALL).size)
        assertEquals(listOf(1L), filterNotesByDocumentType(all, NoteDocumentFilter.NOTES).map { it.id })
        assertEquals(listOf(2L), filterNotesByDocumentType(all, NoteDocumentFilter.PDF).map { it.id })
        assertEquals(listOf(3L), filterNotesByDocumentType(all, NoteDocumentFilter.DOCX).map { it.id })

        val sorted = sortNotes(all, NoteSortOption.CATEGORY, ascending = true)
        val grouped = groupNotesByCategory(
            sortedNotes = sorted,
            sortOption = NoteSortOption.CATEGORY,
            ascending = true,
            knownCategories = listOf("Estudos", "Trabalho", "Geral")
        )
        assertEquals(listOf("Estudos", "Trabalho"), grouped.map { it.categoryName })
        assertEquals(listOf(1L, 2L), grouped[0].notes.map { it.id })
        assertEquals(listOf(3L), grouped[1].notes.map { it.id })
    }

    @Test
    fun `backup manager preserves external documentType and externalUri without storing file contents`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val pdfDoc = Note(
            id = 10L,
            uuid = "pdf-uuid-123",
            title = "Manual Técnico",
            content = "",
            category = "Estudos",
            documentType = NoteDocumentType.PDF.id,
            externalUri = "content://docs/manual.pdf",
            isPinned = true
        )
        val docxDoc = Note(
            id = 11L,
            uuid = "docx-uuid-456",
            title = "Projeto Final",
            content = "",
            category = "Trabalho",
            documentType = NoteDocumentType.DOCX.id,
            externalUri = "content://docs/projeto.docx"
        )

        val json = BackupManager.exportNotesJson(listOf(pdfDoc, docxDoc), context)
        val restored = BackupManager.parseNotesJson(json, context)

        assertEquals(2, restored.size)
        assertEquals(NoteDocumentType.PDF.id, restored[0].documentType)
        assertEquals("content://docs/manual.pdf", restored[0].externalUri)
        assertEquals("", restored[0].content)
        assertTrue(restored[0].isPinned)

        assertEquals(NoteDocumentType.DOCX.id, restored[1].documentType)
        assertEquals("content://docs/projeto.docx", restored[1].externalUri)
        assertEquals("", restored[1].content)
    }

    @Test
    fun `external intent detector resolves PDF and Word mime types and octet-stream filename fallbacks`() {
        assertEquals(
            NoteDocumentType.PDF,
            com.example.util.ExternalDocumentUriHelper.detectExternalDocumentTypeFromMeta(
                displayName = "documento_sem_extensao",
                mimeType = "application/pdf"
            )
        )
        assertEquals(
            NoteDocumentType.DOCX,
            com.example.util.ExternalDocumentUriHelper.detectExternalDocumentTypeFromMeta(
                displayName = "relatorio",
                mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            )
        )
        assertEquals(
            NoteDocumentType.PDF,
            com.example.util.ExternalDocumentUriHelper.detectExternalDocumentTypeFromMeta(
                displayName = "Guia_Leitura.PDF",
                mimeType = "application/octet-stream"
            )
        )
        assertEquals(
            NoteDocumentType.DOCX,
            com.example.util.ExternalDocumentUriHelper.detectExternalDocumentTypeFromMeta(
                displayName = "Contrato_Final.docx",
                mimeType = "application/octet-stream"
            )
        )
        assertEquals(
            null,
            com.example.util.ExternalDocumentUriHelper.detectExternalDocumentTypeFromMeta(
                displayName = "foto.png",
                mimeType = "image/png"
            )
        )
    }

    @Test
    fun `other supported extensions label explicitly exposes non-markdown non-pdf non-docx formats`() {
        val label = com.example.util.ExternalDocumentUriHelper.OTHER_SUPPORTED_EXTENSIONS_LABEL
        listOf(".txt", ".html", ".csv", ".json", ".tex", ".xml", ".citradoc").forEach { ext ->
            assertTrue("Expected $ext in $label", label.contains(ext))
        }
    }

    @Test
    fun `backup manager preserves isArchived state so archived notes stay separate from active notes`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val activeNote = Note(
            id = 1L,
            uuid = "active-1",
            title = "Ativa",
            content = "Visível",
            isArchived = false
        )
        val archivedNote = Note(
            id = 2L,
            uuid = "arch-2",
            title = "Arquivada",
            content = "Guardada",
            isArchived = true
        )

        val json = BackupManager.exportNotesJson(listOf(activeNote, archivedNote), context)
        val restored = BackupManager.parseNotesJson(json, context)

        assertEquals(2, restored.size)
        assertFalse(restored.first { it.uuid == "active-1" }.isArchived)
        assertTrue(restored.first { it.uuid == "arch-2" }.isArchived)
    }
}
