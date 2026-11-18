package com.example

import android.content.Context
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.PreferencesManager
import com.example.data.model.Note
import com.example.ui.screens.organization.notes.ListStyleType
import com.example.ui.screens.organization.notes.NumberedBlock
import com.example.ui.screens.organization.notes.ObsidianMarkdownEngine
import com.example.ui.screens.organization.notes.SmoothMarkdownConverter
import com.example.util.DocxBlock
import com.example.util.DocxDocumentEngine
import com.example.util.MediaStorageHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CitraV126FixesTest {

    @Test
    fun `wrapSelection applies and toggles formatting across multiple paragraphs preserving prefixes`() {
        val raw = "# Primeiro\n- [ ] Segundo\n1. Terceiro\nI. Quarto\na) Quinto"
        val tfv = TextFieldValue(raw, selection = TextRange(0, raw.length))

        val bolded = ObsidianMarkdownEngine.wrapSelection(tfv, "**", "**")
        assertEquals(
            "# **Primeiro**\n- [ ] **Segundo**\n1. **Terceiro**\nI. **Quarto**\na) **Quinto**",
            bolded.text
        )

        val unbolded = ObsidianMarkdownEngine.wrapSelection(bolded, "**", "**")
        assertEquals(raw, unbolded.text)
    }

    @Test
    fun `wrapSelection supports highlight underline and strikethrough across selected lines`() {
        val raw = "Linha um\n\nLinha dois"
        val tfv = TextFieldValue(raw, selection = TextRange(0, raw.length))

        val underlined = ObsidianMarkdownEngine.wrapSelection(tfv, "<u>", "</u>")
        assertEquals("<u>Linha um</u>\n\n<u>Linha dois</u>", underlined.text)

        val reverted = ObsidianMarkdownEngine.wrapSelection(underlined, "<u>", "</u>")
        assertEquals(raw, reverted.text)
    }

    @Test
    fun `toggleListStyle switches between bullet arabic roman and alpha lists`() {
        val base = "Item um\nItem dois\nItem três"
        val tfv = TextFieldValue(base, selection = TextRange(0, base.length))

        val arabic = ObsidianMarkdownEngine.toggleListStyle(tfv, ListStyleType.ARABIC)
        assertEquals("1. Item um\n2. Item dois\n3. Item três", arabic.text)

        val roman = ObsidianMarkdownEngine.toggleListStyle(
            arabic.copy(selection = TextRange(0, arabic.text.length)),
            ListStyleType.ROMAN
        )
        assertEquals("I. Item um\nII. Item dois\nIII. Item três", roman.text)

        val alpha = ObsidianMarkdownEngine.toggleListStyle(
            roman.copy(selection = TextRange(0, roman.text.length)),
            ListStyleType.ALPHA
        )
        assertEquals("a) Item um\nb) Item dois\nc) Item três", alpha.text)

        val toggledOff = ObsidianMarkdownEngine.toggleListStyle(
            alpha.copy(selection = TextRange(0, alpha.text.length)),
            ListStyleType.ALPHA
        )
        assertEquals(base, toggledOff.text)
    }

    @Test
    fun `handleNewlineAutoContinue increments list markers and exits on blank line`() {
        val oldRoman = TextFieldValue("II. Capítulo dois", TextRange("II. Capítulo dois".length))
        val newRomanRaw = TextFieldValue("II. Capítulo dois\n", TextRange("II. Capítulo dois\n".length))
        val continuedRoman = ObsidianMarkdownEngine.handleNewlineAutoContinue(oldRoman, newRomanRaw)
        assertEquals("II. Capítulo dois\nIII. ", continuedRoman.text)

        val oldAlpha = TextFieldValue("b) Opção B", TextRange("b) Opção B".length))
        val newAlphaRaw = TextFieldValue("b) Opção B\n", TextRange("b) Opção B\n".length))
        val continuedAlpha = ObsidianMarkdownEngine.handleNewlineAutoContinue(oldAlpha, newAlphaRaw)
        assertEquals("b) Opção B\nc) ", continuedAlpha.text)

        val oldEmptyMarker = TextFieldValue("1. ", TextRange(3))
        val newEmptyMarkerRaw = TextFieldValue("1. \n", TextRange(4))
        val exitedList = ObsidianMarkdownEngine.handleNewlineAutoContinue(oldEmptyMarker, newEmptyMarkerRaw)
        assertEquals("", exitedList.text)
    }

    @Test
    fun `SmoothMarkdownConverter round-trips NumberedBlock styles`() {
        val md = "1. Primeiro\nI. Segundo\na) Terceiro"
        val blocks = SmoothMarkdownConverter.markdownToBlocks(md)
        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is NumberedBlock)
        assertEquals("1.", (blocks[0] as NumberedBlock).marker)
        assertEquals("I.", (blocks[1] as NumberedBlock).marker)
        assertEquals("a)", (blocks[2] as NumberedBlock).marker)

        val backToMd = SmoothMarkdownConverter.blocksToMarkdown(blocks)
        assertTrue(backToMd.contains("1. Primeiro"))
        assertTrue(backToMd.contains("I. Segundo"))
        assertTrue(backToMd.contains("a) Terceiro"))
    }

    @Test
    fun `toolbar order places strikethrough underline and highlight first in module 2`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferencesManager(context)

        // Default order
        val defaultOrder = PreferencesManager.DEFAULT_TOOLBAR_ORDER
        assertEquals(listOf("strikethrough", "underline", "highlight"), defaultOrder.drop(13).take(3))

        // Legacy saved order migration keeps them first in Module 2
        val legacyOrder = listOf(
            "bold", "italic", "inline_code", "titles", "checklist", "list",
            "code_block", "math", "table", "divider", "image", "audio", "record",
            "to_task", "quote", "callout", "details",
            "strikethrough", "underline", "highlight", "pagebreak",
            "find_replace", "link_entity", "pagination", "toc"
        )
        prefs.saveToolbarOrder(legacyOrder)
        val migrated = prefs.getToolbarOrder()
        assertEquals(listOf("strikethrough", "underline", "highlight"), migrated.drop(13).take(3))
    }

    @Test
    fun `pomodoroDisruptiveAlarm persists and round-trips through backup export and import`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferencesManager(context)
        val current = prefs.settings.value
        prefs.updateSettings(current.copy(pomodoroDisruptiveAlarm = true))
        assertTrue(prefs.settings.value.pomodoroDisruptiveAlarm)

        val exported = prefs.exportPreferencesMap()
        prefs.updateSettings(current.copy(pomodoroDisruptiveAlarm = false))
        assertFalse(prefs.settings.value.pomodoroDisruptiveAlarm)

        prefs.importPreferencesMap(exported)
        assertTrue(prefs.settings.value.pomodoroDisruptiveAlarm)
    }

    @Test
    fun `updateDocxTextInPlace edits and clears paragraphs while preserving zip structure`() {
        val documentXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body>
                <w:p><w:r><w:t>Título Original</w:t></w:r></w:p>
                <w:p><w:r><w:t>Parágrafo que será apagado</w:t></w:r></w:p>
                <w:p><w:r><w:t>Parágrafo intacto</w:t></w:r></w:p>
              </w:body>
            </w:document>
        """.trimIndent()
        val stylesXml = """<?xml version="1.0" encoding="UTF-8"?><w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"/>"""

        val inputBytes = ByteArrayOutputStream().apply {
            ZipOutputStream(this).use { zos ->
                zos.putNextEntry(ZipEntry("word/document.xml"))
                zos.write(documentXml.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
                zos.putNextEntry(ZipEntry("word/styles.xml"))
                zos.write(stylesXml.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }.toByteArray()

        val initialParsed = DocxDocumentEngine.parseDocx(ByteArrayInputStream(inputBytes))
        assertEquals(3, initialParsed.blocks.size)
        val p0 = (initialParsed.blocks[0] as DocxBlock.Paragraph).sourceParagraphIndex
        val p1 = (initialParsed.blocks[1] as DocxBlock.Paragraph).sourceParagraphIndex
        assertEquals(0, p0)
        assertEquals(1, p1)

        val editedOut = ByteArrayOutputStream()
        val ok = DocxDocumentEngine.updateDocxTextInPlace(
            inputStream = ByteArrayInputStream(inputBytes),
            outputStream = editedOut,
            editsByParagraphIndex = mapOf(
                p0 to "Novo Título Editado",
                p1 to ""
            )
        )
        assertTrue(ok)

        val updatedBytes = editedOut.toByteArray()
        val reparsed = DocxDocumentEngine.parseDocx(ByteArrayInputStream(updatedBytes))
        assertEquals(3, reparsed.blocks.size)
        assertEquals("Novo Título Editado", (reparsed.blocks[0] as DocxBlock.Paragraph).runs.joinToString("") { it.text })
        assertEquals("", (reparsed.blocks[1] as DocxBlock.Paragraph).runs.joinToString("") { it.text })
        assertEquals("Parágrafo intacto", (reparsed.blocks[2] as DocxBlock.Paragraph).runs.joinToString("") { it.text })

        // Ensure untouched ZIP entries remain present
        var hasStyles = false
        ZipInputStream(ByteArrayInputStream(updatedBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "word/styles.xml") hasStyles = true
                entry = zis.nextEntry
            }
        }
        assertTrue(hasStyles)
    }

    @Test
    fun `resolveCachedExternalImage reuses existing cached file without downloading`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val url = "https://example.com/images/principio_mach.png?purpose=fullsize"
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
        val hashHex = digest.take(10).joinToString("") { "%02x".format(it) }

        val citraPicDir = File(context.filesDir, "Citra Pic").apply { mkdirs() }
        val cachedFile = File(citraPicDir, "citra_url_${hashHex}.png")
        cachedFile.writeBytes(byteArrayOf(1, 2, 3, 4))

        val resolved = MediaStorageHelper.resolveCachedExternalImage(context, url)
        assertNotNull(resolved)
        assertEquals(cachedFile.absolutePath, resolved!!.absolutePath)

        val md = "Texto antes\n![Principio de Mach]($url)\nTexto depois"
        val updatedMd = MediaStorageHelper.resolveAndDownloadRemoteImages(context, md)
        assertEquals("Texto antes\n![Principio de Mach](${cachedFile.absolutePath})\nTexto depois", updatedMd)

        cachedFile.delete()
    }

    @Test
    fun `deleteUnreferencedMediaFiles deletes exclusive note media while sparing shared and app-protected media`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val imagesDir = File(context.filesDir, "images").apply { mkdirs() }
        val exclusiveImg = File(imagesDir, "citra_pic_exclusive_1.jpg").apply { writeBytes(byteArrayOf(9, 8, 7)) }
        val sharedImg = File(imagesDir, "citra_pic_shared_2.jpg").apply { writeBytes(byteArrayOf(6, 5, 4)) }
        val bgImg = File(imagesDir, "citra_pic_bg_3.jpg").apply { writeBytes(byteArrayOf(3, 2, 1)) }

        val deletedNote = Note(
            id = 10L,
            title = "Apagada",
            content = "![center|100%](${exclusiveImg.absolutePath})\n![center|100%](${sharedImg.absolutePath})"
        )
        val survivingNote = Note(
            id = 20L,
            title = "Viva",
            content = "![center|100%](${sharedImg.absolutePath})"
        )

        val candidates = MediaStorageHelper.extractAllReferencedLocalMediaPaths(deletedNote) + bgImg.absolutePath
        val deletedCount = MediaStorageHelper.deleteUnreferencedMediaFiles(
            context = context,
            candidatePaths = candidates,
            survivingNotes = listOf(survivingNote),
            protectedUris = setOf(bgImg.absolutePath)
        )

        assertEquals(1, deletedCount)
        assertFalse("Exclusive media should be deleted from storage", exclusiveImg.exists())
        assertTrue("Media still used by surviving note must remain", sharedImg.exists())
        assertTrue("Protected app background must remain", bgImg.exists())

        sharedImg.delete()
        bgImg.delete()
    }
}
