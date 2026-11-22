package com.example

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.backup.AutoDeviceStorageManager
import com.example.data.local.AppDatabase
import com.example.data.model.Habit
import com.example.data.model.Note
import com.example.data.model.NoteDocumentType
import com.example.data.model.Task
import com.example.data.model.TaskPriority
import com.example.data.model.isExternalDocument
import com.example.util.CitraDocsFileManager
import com.example.util.CitraPersistenceQueue
import com.example.util.DeviceDocumentScanner
import com.example.util.FastBlur
import com.example.util.MediaStorageHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CitraOptimizationAndReaderTest {

    @Test
    fun `migration 15 to 16 creates all performance indices and preserves data`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.cacheDir, "migration_15_16_test_${System.nanoTime()}.db")
        if (dbFile.exists()) dbFile.delete()

        val sqlDb = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        sqlDb.use { db ->
            db.execSQL(
                """
                CREATE TABLE notes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    uuid TEXT NOT NULL,
                    title TEXT NOT NULL,
                    content TEXT NOT NULL,
                    category TEXT NOT NULL,
                    iconEmoji TEXT NOT NULL,
                    associatedDate TEXT,
                    isPinned INTEGER NOT NULL,
                    isArchived INTEGER NOT NULL,
                    noteColor TEXT,
                    backgroundImageUri TEXT,
                    backgroundBlur REAL NOT NULL,
                    backgroundBrightness REAL NOT NULL,
                    isDeleted INTEGER NOT NULL,
                    deletedAt INTEGER,
                    documentType TEXT NOT NULL,
                    externalUri TEXT,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE TABLE note_versions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, noteId INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, isCompleted INTEGER NOT NULL, isWontDo INTEGER NOT NULL, dueDateTimestamp INTEGER)")
            db.execSQL("CREATE TABLE focus_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, weekIdentifier TEXT NOT NULL, timestamp INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE finance_entries (id TEXT NOT NULL PRIMARY KEY, deletedAt INTEGER, dateMillis INTEGER NOT NULL, categoryId TEXT NOT NULL)")
            db.execSQL("INSERT INTO notes (uuid, title, content, category, iconEmoji, isPinned, isArchived, backgroundBlur, backgroundBrightness, isDeleted, documentType, updatedAt) VALUES ('u-1', 'Nota Teste', 'Corpo', 'Geral', '📝', 0, 0, 0.5, 0.5, 0, 'MARKDOWN', 12345)")
            db.version = 15
        }

        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.absolutePath)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(16) {
                override fun onCreate(db: SupportSQLiteDatabase) {}
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    AppDatabase.MIGRATION_15_16.migrate(db)
                }
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val migratedDb = helper.writableDatabase
        try {
            val indices = mutableSetOf<String>()
            migratedDb.query("SELECT name FROM sqlite_master WHERE type = 'index'").use { c ->
                while (c.moveToNext()) {
                    indices.add(c.getString(0))
                }
            }
            assertTrue(indices.contains("index_notes_isDeleted"))
            assertTrue(indices.contains("index_notes_isArchived"))
            assertTrue(indices.contains("index_notes_uuid"))
            assertTrue(indices.contains("index_note_versions_noteId"))
            assertTrue(indices.contains("index_tasks_isCompleted"))
            assertTrue(indices.contains("index_focus_sessions_weekIdentifier"))
            assertTrue(indices.contains("index_finance_entries_deletedAt"))
            assertTrue(indices.contains("index_finance_entries_dateMillis"))
            assertTrue(indices.contains("index_finance_entries_categoryId"))

            migratedDb.query("SELECT title FROM notes WHERE uuid = 'u-1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Nota Teste", c.getString(0))
            }
        } finally {
            helper.close()
            dbFile.delete()
        }

        val realDb = AppDatabase.getInstance(context)
        realDb.openHelper.writableDatabase.query("PRAGMA journal_mode").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("wal", c.getString(0).lowercase())
        }
    }

    @Test
    fun `persistence queue debounces stale writes and flush saves latest markdown`() {
        runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uuid = "test-uuid-${UUID.randomUUID()}"
        val title = "NotaFila_${System.currentTimeMillis()}"

        CitraPersistenceQueue.scheduleMarkdownSave(
            context = context,
            title = title,
            content = "Conteúdo antigo 1",
            uuid = uuid,
            debounceMs = 5000L
        )
        CitraPersistenceQueue.scheduleMarkdownSave(
            context = context,
            title = title,
            content = "Conteúdo antigo 2",
            uuid = uuid,
            debounceMs = 5000L
        )
        val savedFile = CitraPersistenceQueue.flushMarkdownSaveNow(
            context = context,
            title = title,
            content = "Conteúdo final persistido com sucesso",
            uuid = uuid
        )

        assertNotNull(savedFile)
        assertTrue(savedFile!!.exists())
        val raw = savedFile.readText(Charsets.UTF_8)
        assertTrue(raw.contains("citra_id: $uuid"))
        assertTrue(raw.contains("Conteúdo final persistido com sucesso"))
        assertFalse(raw.contains("Conteúdo antigo 1"))

        savedFile.delete()
        }
    }

    @Test
    fun `auto device storage snapshot writes atomically and restores cleanly`() {
        runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        AutoDeviceStorageManager.clearSnapshot(context)

        val task = Task(
            id = 10L,
            title = "Revisar performance",
            category = "Trabalho",
            priority = TaskPriority.ALTA,
            dueDateText = "Hoje",
            isCompleted = false
        )
        val habit = Habit(
            id = 20L,
            title = "Leitura diária",
            category = "Estudos",
            iconEmoji = "📖",
            reminderTime = "20:00",
            frequencyDays = "Todos os dias",
            currentStreak = 3,
            bestStreak = 5
        )
        val noteUuid = UUID.randomUUID().toString()
        val note = Note(
            id = 30L,
            uuid = noteUuid,
            title = "Plano Atômico",
            content = "Texto salvo atomicamente",
            category = "Geral",
            documentType = NoteDocumentType.MARKDOWN.id,
            updatedAt = 999999L
        )

        AutoDeviceStorageManager.saveFullAppSnapshot(
            context = context,
            tasks = listOf(task),
            habits = listOf(habit),
            notes = listOf(note),
            focusSessions = emptyList(),
            categories = emptyList(),
            achievements = emptyList()
        )

        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val tempFile = File(dir, ".citra_persistent_backup.json.tmp")
        val primaryFile = File(dir, "citra_persistent_backup.json")
        assertFalse(tempFile.exists())
        assertTrue(primaryFile.exists() && primaryFile.length() > 0L)

        val testDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val restored = AutoDeviceStorageManager.tryRestoreFullAppSnapshot(context, testDb)
            assertTrue(restored)
            val restoredTasks = testDb.taskDao().getAllTasksSync()
            val restoredHabits = testDb.habitDao().getAllHabitsSync()
            val restoredNotes = testDb.noteDao().getAllNotesSync()
            assertTrue(restoredTasks.any { it.title == "Revisar performance" })
            assertTrue(restoredHabits.any { it.title == "Leitura diária" && it.currentStreak == 3 })
            assertTrue(restoredNotes.any { it.uuid == noteUuid && it.content == "Texto salvo atomicamente" })
        } finally {
            testDb.close()
            AutoDeviceStorageManager.clearSnapshot(context)
        }
        }
    }

    @Test
    fun `decodeSampledBitmap and FastBlur cap large bitmaps safely`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val largeBitmap = Bitmap.createBitmap(2400, 1600, Bitmap.Config.ARGB_8888)
        val tempPng = File(context.cacheDir, "large_test_${System.currentTimeMillis()}.png")
        FileOutputStream(tempPng).use { out ->
            largeBitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
        }
        largeBitmap.recycle()

        try {
            val decoded = MediaStorageHelper.decodeSampledBitmap(
                context = context,
                source = tempPng.absolutePath,
                maxDimensionPx = 600,
                preferredConfig = Bitmap.Config.RGB_565
            )
            assertNotNull(decoded)
            assertTrue(maxOf(decoded!!.width, decoded.height) <= 1200)

            val blurred = FastBlur.blur(decoded, 18)
            assertNotNull(blurred)
            assertTrue(maxOf(blurred!!.width, blurred.height) <= 720)
            blurred.recycle()
            if (!decoded.isRecycled) decoded.recycle()
        } finally {
            tempPng.delete()
        }
    }

    @Test
    fun `device document scanner discovers pdf and docx files and tracks vault status`() {
        runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val docsDir = CitraDocsFileManager.getDocsFolder(context)
        val vaultPdf = File(docsDir, "vault_doc_${System.currentTimeMillis()}.pdf").apply {
            writeBytes("%PDF-1.4\n%CitraTest\n".toByteArray())
        }
        val freePdf = File(docsDir, "transient_doc_${System.currentTimeMillis()}.pdf").apply {
            writeBytes("%PDF-1.4\n%FreeDoc\n".toByteArray())
        }
        val otherDocx = File(docsDir, "sample_word_${System.currentTimeMillis()}.docx").apply {
            writeBytes(buildMinimalDocx("Documento de teste"))
        }

        try {
            val vaultNote = Note(
                id = 1L,
                uuid = "pdf-vault-1",
                title = vaultPdf.nameWithoutExtension,
                content = "",
                category = "Estudos",
                documentType = NoteDocumentType.PDF.id,
                externalUri = vaultPdf.absolutePath,
                updatedAt = System.currentTimeMillis()
            )

            val pdfResults = DeviceDocumentScanner.scanDocuments(
                context = context,
                targetType = NoteDocumentType.PDF,
                recentDocs = emptyList(),
                authorizedTreeUris = emptySet(),
                existingNotes = listOf(vaultNote)
            )
            val foundVault = pdfResults.firstOrNull { it.uri == vaultPdf.absolutePath }
            val foundFree = pdfResults.firstOrNull { it.uri == freePdf.absolutePath }
            assertNotNull(foundVault)
            assertNotNull(foundFree)
            assertTrue(foundVault!!.isInVault)
            assertFalse(foundFree!!.isInVault)
            assertFalse(pdfResults.any { it.uri == otherDocx.absolutePath })

            val docxResults = DeviceDocumentScanner.scanDocuments(
                context = context,
                targetType = NoteDocumentType.DOCX,
                recentDocs = emptyList(),
                authorizedTreeUris = emptySet(),
                existingNotes = listOf(vaultNote)
            )
            assertTrue(docxResults.any { it.uri == otherDocx.absolutePath && !it.isInVault })
        } finally {
            vaultPdf.delete()
            freePdf.delete()
            otherDocx.delete()
        }
        }
    }

    @Test
    fun `external reader activity opens docx in isolated mode without writing to db`() {
        runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val docxFile = File(context.cacheDir, "external_isolated_${System.currentTimeMillis()}.docx")
        docxFile.writeBytes(buildMinimalDocx("Leitura isolada no Citra"))

        val db = AppDatabase.getInstance(context)
        val beforeCount = db.noteDao().getAllNotesSync().count { it.isExternalDocument }

        try {
            val viewIntent = Intent(context, ExternalReaderActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                setDataAndType(
                    Uri.fromFile(docxFile),
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                )
            }
            val controller = Robolectric.buildActivity(ExternalReaderActivity::class.java, viewIntent)
                .create()
                .start()
                .resume()
            val activity = controller.get()
            assertNotNull(activity)
            assertFalse(activity.isFinishing)

            val afterCount = db.noteDao().getAllNotesSync().count { it.isExternalDocument }
            assertEquals(beforeCount, afterCount)
            controller.pause().stop().destroy()
        } finally {
            docxFile.delete()
        }
        }
    }

    private fun buildMinimalDocx(paragraphText: String): ByteArray {
        val documentXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body>
                <w:p><w:r><w:t>$paragraphText</w:t></w:r></w:p>
              </w:body>
            </w:document>
        """.trimIndent()
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("word/document.xml"))
            zos.write(documentXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        return baos.toByteArray()
    }
}
