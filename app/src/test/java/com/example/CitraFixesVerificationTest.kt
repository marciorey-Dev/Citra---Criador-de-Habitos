package com.example

import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.luminance
import androidx.test.core.app.ApplicationProvider
import coil.ImageLoader
import coil.request.ErrorResult
import coil.request.ImageRequest
import com.example.data.model.Note
import com.example.data.model.NoteDocumentType
import com.example.ui.screens.organization.notes.PaperNotePalette
import com.example.util.CitraDocsFileManager
import com.example.util.MediaStorageHelper
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CitraFixesVerificationTest {

    @Test
    fun `offline call factory and image loader block http and https without crashing`() {
        val request = Request.Builder().url("https://example.com/photo.jpg").build()
        val call = OfflineBlockingCallFactory.newCall(request)

        val latch = CountDownLatch(1)
        var capturedFailure: IOException? = null
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                capturedFailure = e
                latch.countDown()
            }

            override fun onResponse(call: Call, response: Response) {
                latch.countDown()
            }
        })

        assertTrue("Expected async failure callback", latch.await(2, TimeUnit.SECONDS))
        assertNotNull(capturedFailure)

        assertTrue(OfflineNetworkBlockingInterceptor.isRemoteImageData("https://example.com/a.png"))
        assertTrue(OfflineNetworkBlockingInterceptor.isRemoteImageData("http://example.com/b.jpg"))
        assertFalse(OfflineNetworkBlockingInterceptor.isRemoteImageData("/storage/emulated/0/Pictures/Citra Pic/a.jpg"))

        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = CitraApplication()
        // Attach base context via ApplicationProvider's app or execute directly with ImageLoader
        val loader = ImageLoader.Builder(context)
            .components { add(OfflineNetworkBlockingInterceptor(allowNetwork = false)) }
            .callFactory(OfflineBlockingCallFactory)
            .build()

        val result = runBlocking {
            loader.execute(
                ImageRequest.Builder(context)
                    .data("https://example.com/test.png")
                    .build()
            )
        }
        assertTrue("Remote image in offline app must produce ErrorResult safely", result is ErrorResult)
        assertNotNull(app)
    }

    @Test
    fun `paper note palette meta and icon colors have high contrast in both light and dark themes`() {
        val sampleOptions = PaperNotePalette.options.map { it.key } + listOf(null)
        sampleOptions.forEach { colorKey ->
            val light = PaperNotePalette.resolvePaperColors(colorKey, isDark = false, isPinned = false)
            val dark = PaperNotePalette.resolvePaperColors(colorKey, isDark = true, isPinned = true)

            val lightBgLum = light.background.luminance()
            val lightMetaLum = light.metaTextColor.luminance()
            val lightIconLum = light.mutedIconColor.luminance()
            assertTrue(
                "Light meta text must be darker than paper background for $colorKey",
                lightMetaLum < lightBgLum && abs(lightBgLum - lightMetaLum) > 0.35f
            )
            assertTrue(
                "Light icons must be darker than paper background for $colorKey",
                lightIconLum < lightBgLum && abs(lightBgLum - lightIconLum) > 0.30f
            )

            val darkBgLum = dark.background.luminance()
            val darkMetaLum = dark.metaTextColor.luminance()
            val darkIconLum = dark.mutedIconColor.luminance()
            assertTrue(
                "Dark meta text must be much brighter than dark paper background for $colorKey",
                darkMetaLum > darkBgLum && abs(darkMetaLum - darkBgLum) > 0.35f
            )
            assertTrue(
                "Dark icons must be much brighter than dark paper background for $colorKey",
                darkIconLum > darkBgLum && abs(darkIconLum - darkBgLum) > 0.35f
            )
        }

        val pdfDark = PaperNotePalette.resolvePaperColors(
            Note(id = 1L, title = "PDF", content = "", documentType = NoteDocumentType.PDF.id, updatedAt = 1L),
            isDark = true
        )
        val docxLight = PaperNotePalette.resolvePaperColors(
            Note(id = 2L, title = "DOCX", content = "", documentType = NoteDocumentType.DOCX.id, updatedAt = 1L),
            isDark = false
        )
        assertTrue(pdfDark.metaTextColor.luminance() > pdfDark.background.luminance() + 0.35f)
        assertTrue(docxLight.metaTextColor.luminance() + 0.35f < docxLight.background.luminance())
    }

    @Test
    fun `citra folder paths and existing media files do not create duplicates`() {
        assertTrue(MediaStorageHelper.isCitraFolderPath("/storage/emulated/0/Pictures/Citra Pic/foto.jpg"))
        assertTrue(MediaStorageHelper.isCitraFolderPath("/storage/emulated/0/Recordings/Citra Records/rec.m4a"))
        assertTrue(MediaStorageHelper.isCitraFolderPath("/storage/emulated/0/Documents/Citra Docs/nota.md"))
        assertTrue(MediaStorageHelper.isCitraFolderPath("/storage/emulated/0/Download/Citra/export.pdf"))
        assertTrue(MediaStorageHelper.isCitraFolderPath("Pictures/Citra Pic/"))
        assertFalse(MediaStorageHelper.isCitraFolderPath("/storage/emulated/0/DCIM/Camera/IMG_001.jpg"))

        val extracted = MediaStorageHelper.extractExternalStorageRelativePath(
            "content://com.android.externalstorage.documents/document/primary%3APictures%2FCitra%20Pic%2Ffoto.jpg"
        )
        assertEquals("Pictures/Citra Pic/foto.jpg", extracted)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val picDir = MediaStorageHelper.getCitraPicFolder(context)
        val recDir = MediaStorageHelper.getCitraRecordsFolder(context)
        val docsDir = CitraDocsFileManager.getDocsFolder(context)

        val existingImg = File(picDir, "existing_citra_img.jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }
        val existingAudio = File(recDir, "existing_citra_rec.m4a").apply {
            writeBytes(byteArrayOf(5, 6, 7, 8))
        }
        val existingNote = File(docsDir, "Minha Nota.md").apply {
            writeText("# Minha Nota\n\nConteúdo")
        }

        val countBeforePic = picDir.listFiles()?.size ?: 0
        val savedImgPath = MediaStorageHelper.saveNoteImage(context, Uri.fromFile(existingImg).toString())
        val countAfterPic = picDir.listFiles()?.size ?: 0
        assertEquals(existingImg.absolutePath, savedImgPath)
        assertEquals("Loading image from Citra Pic must not create a duplicate", countBeforePic, countAfterPic)

        val countBeforeRec = recDir.listFiles()?.size ?: 0
        val savedAudioPath = MediaStorageHelper.saveNoteAudio(context, existingAudio.absolutePath)
        val countAfterRec = recDir.listFiles()?.size ?: 0
        assertEquals(existingAudio.absolutePath, savedAudioPath)
        assertEquals("Loading audio from Citra Records must not create a duplicate", countBeforeRec, countAfterRec)

        val resolvedNote = MediaStorageHelper.resolveExistingCitraFile(context, existingNote.absolutePath)
        assertEquals(existingNote.absolutePath, resolvedNote?.absolutePath)

        val mdInput = "Texto\n![center|100%](${existingImg.absolutePath})\n[audio:Rec](${existingAudio.absolutePath})"
        val processedMd = MediaStorageHelper.processAndPersistImportedNoteMedia(context, mdInput)
        assertEquals(mdInput, processedMd)
        assertEquals(countBeforePic, picDir.listFiles()?.size ?: 0)
        assertEquals(countBeforeRec, recDir.listFiles()?.size ?: 0)
    }
}
