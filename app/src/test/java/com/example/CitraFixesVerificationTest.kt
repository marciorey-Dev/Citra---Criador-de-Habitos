package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.backup.BackupManager
import com.example.data.local.PreferencesManager
import com.example.data.model.*
import com.example.ui.screens.organization.notes.NotePreviewBlockKind
import com.example.ui.screens.organization.notes.NotePreviewParser
import com.example.ui.theme.ThemeAccent
import com.example.util.BatchExportFormat
import com.example.util.LatexFormulaRenderer
import com.example.util.NoteExportHelper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CitraFixesVerificationTest {

    private val rawLatexCommands = listOf(
        "\\frac", "\\sqrt", "\\sin", "\\log", "\\ln",
        "\\quad", "\\Delta", "\\pm", "\\sigma", "\\mu",
        "\\pi", "\\left", "\\right", "≤ft"
    )

    private fun assertNoRawLatexArtifacts(rendered: String, formula: String) {
        for (cmd in rawLatexCommands) {
            assertFalse(
                "Rendered output '$rendered' for '$formula' should not contain raw command '$cmd'",
                rendered.contains(cmd)
            )
        }
        assertFalse(
            "Rendered output '$rendered' for '$formula' should not contain stray backslashes",
            rendered.contains("\\")
        )
    }

    @Test
    fun `all five user latex formulas render without raw commands or syntax errors`() {
        val f1 = """x_v = -\frac{b}{2a}, \quad y_v = -\frac{\Delta}{4a}"""
        val f2 = """\log_b(a) = \frac{\log_c(a)}{\log_c(b)} = \frac{\ln(a)}{\ln(b)}"""
        val f3 = """\frac{a}{\sin(A)} = \frac{b}{\sin(B)} = \frac{c}{\sin(C)} = 2R"""
        val f4 = """x = \frac{-b \pm \sqrt{b^2 - 4ac}}{2a}"""
        val f5 = """f(x) = \frac{1}{\sigma \sqrt{2\pi}} e^{-\frac{1}{2}\left(\frac{x-\mu}{\sigma}\right)^2}"""

        val formulas = listOf(f1, f2, f3, f4, f5)
        for (f in formulas) {
            assertNull("Expected valid syntax for: $f", LatexFormulaRenderer.validateFormula(f))

            val plain = LatexFormulaRenderer.renderFormulaToText(f)
            assertTrue("Expected non-empty plain output for: $f", plain.isNotBlank())
            assertNoRawLatexArtifacts(plain, f)

            val annotated = LatexFormulaRenderer.formatAnnotatedBlock(f, isDark = false).text
            assertTrue("Expected non-empty annotated output for: $f", annotated.isNotBlank())
            assertNoRawLatexArtifacts(annotated, f)
        }

        val r1 = LatexFormulaRenderer.renderFormulaToText(f1)
        assertTrue(r1.contains("Δ"))
        assertTrue(r1.contains("/"))

        val r2 = LatexFormulaRenderer.renderFormulaToText(f2)
        assertTrue(r2.contains("log_b(a)"))
        assertTrue(r2.contains("ln(a)"))
        assertTrue(r2.contains("ln(b)"))

        val r3 = LatexFormulaRenderer.renderFormulaToText(f3)
        assertTrue(r3.contains("sin(A)"))
        assertTrue(r3.contains("sin(B)"))
        assertTrue(r3.contains("sin(C)"))
        assertTrue(r3.contains("2R"))

        val r4 = LatexFormulaRenderer.renderFormulaToText(f4)
        assertTrue(r4.contains("±"))
        assertTrue(r4.contains("√"))

        val r5 = LatexFormulaRenderer.renderFormulaToText(f5)
        assertTrue(r5.contains("σ"))
        assertTrue(r5.contains("π"))
        assertTrue(r5.contains("μ"))
        assertTrue(r5.contains("√"))
    }

    @Test
    fun `note preview parser renders math blocks cleanly`() {
        val md = """
            # Equações
            $$ x = \frac{-b \pm \sqrt{b^2 - 4ac}}{2a} $$
            $$ f(x) = \frac{1}{\sigma \sqrt{2\pi}} e^{-\frac{1}{2}\left(\frac{x-\mu}{\sigma}\right)^2} $$
        """.trimIndent()

        val elements = NotePreviewParser.parseElements(md, maxElements = 5)
        val mathItems = elements.filter { it.kind == NotePreviewBlockKind.MATH }
        assertEquals(2, mathItems.size)
        assertNoRawLatexArtifacts(mathItems[0].text, "bhaskara")
        assertNoRawLatexArtifacts(mathItems[1].text, "normal")
    }

    @Test
    fun `exported filenames preserve accented characters across formats and zip`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val accentedTitle = "Anotação de Matemática — Época & Símbolos çãõéíóú"
        val sanitized = NoteExportHelper.sanitizeFilename(accentedTitle)
        assertEquals(accentedTitle, sanitized)

        // Forbidden OS chars are replaced while accents remain intact
        val unsafeTitle = "Relatório/Financeiro: 1ª Edição? \"Teste\"*"
        val safeName = NoteExportHelper.sanitizeFilename(unsafeTitle)
        assertTrue(safeName.contains("Relatório"))
        assertTrue(safeName.contains("Edição"))
        assertFalse(safeName.contains("/"))
        assertFalse(safeName.contains(":"))
        assertFalse(safeName.contains("?"))

        val exportDir = File(context.cacheDir, "exports")
        exportDir.deleteRecursively()

        val note = Note(
            title = "Revisão de Física — Equação de 2º Grau",
            content = "# Fórmula\n\n$$ x = \\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a} $$",
            category = "Matemática"
        )
        val batchOut = NoteExportHelper.exportNotesBatch(
            context = context,
            notes = listOf(note),
            format = BatchExportFormat.MARKDOWN
        )
        assertTrue(
            "Single batch MD filename should keep accents: ${batchOut.fileName}",
            batchOut.fileName.contains("Revisão") && batchOut.fileName.contains("Equação")
        )

        val note2 = Note(
            title = "Lição de História — Revolução",
            content = "Resumo com acentuação.",
            category = "História"
        )
        val zipOut = NoteExportHelper.exportNotesBatch(
            context = context,
            notes = listOf(note, note2),
            format = BatchExportFormat.TXT
        )
        assertTrue(zipOut.fileName.endsWith(".zip"))
        val zipFile = File(exportDir, zipOut.fileName)
        assertTrue(zipFile.exists())

        val entryNames = mutableListOf<String>()
        ZipInputStream(zipFile.inputStream(), Charsets.UTF_8).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entryNames.add(entry.name)
                entry = zis.nextEntry
            }
        }
        assertTrue(entryNames.any { it.contains("Revisão") && it.contains("Equação") })
        assertTrue(entryNames.any { it.contains("Lição") && it.contains("Revolução") })
    }

    @Test
    fun `full local backup roundtrips all entities preferences and metadata`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferencesManager(context)
        prefs.setUserDisplayName("Márcio Rey")
        prefs.setOnboardingCompleted(true)
        prefs.setThemeAccent(ThemeAccent.AZUL_OCEANO)
        prefs.setFinancesEnabled(true)
        prefs.setFinancesSetupCompleted(true)
        prefs.setFinanceDefaultCurrency("EUR")
        prefs.setFinancePinCode("1234")
        prefs.setWelcomePhrasesEnabled(false)

        val tasks = listOf(
            Task(id = 1L, title = "Feita", isCompleted = true, completedAt = 1000L),
            Task(id = 2L, title = "Não feita", isCompleted = false, isWontDo = true, isDisruptive = true),
            Task(id = 3L, title = "Por fazer", isCompleted = false, dueDateText = "2026-05-01")
        )
        val habits = listOf(
            Habit(
                id = 10L,
                title = "Meditação",
                reminderLeadMinutes = 15,
                currentStreak = 5,
                bestStreak = 12,
                completedDatesCsv = "2026-04-20,2026-04-21"
            )
        )
        val notes = listOf(
            Note(id = 100L, uuid = "u-1", title = "Normal", content = "A", noteColor = "#FFE4C4", isArchived = false, isDeleted = false),
            Note(id = 101L, uuid = "u-2", title = "Arquivada", content = "B", noteColor = "#D1FAE5", isArchived = true, isDeleted = false),
            Note(id = 102L, uuid = "u-3", title = "Lixeira", content = "C", isArchived = false, isDeleted = true, deletedAt = 9999L, associatedDate = "2026-04-21")
        )
        val versions = listOf(
            NoteVersion(id = 1L, noteId = 100L, versionNumber = 1, title = "Normal v1", content = "Old", authorName = "Márcio Rey")
        )
        val sessions = listOf(FocusSession(id = 1L, category = "Estudos", durationMinutes = 50, breakMinutes = 10))
        val categories = listOf(Category(name = "Ciências", emoji = "🧪", isCustom = true))
        val achievements = listOf(Achievement("focus_45", "Imersão", "45m", "🎨", "Foco", isUnlocked = true, unlockedAt = 5555L, currentProgress = 1, maxProgress = 1))
        val finCats = listOf(FinanceCategory(id = "fc1", type = "EXPENSE", name = "Livros", iconName = "school", colorHex = 0xFF3F51B5, isArchived = true))
        val finWallets = listOf(FinanceWallet(id = "fw1", name = "Banco", type = "BANK", currencyCode = "EUR", initialBalanceMinor = 50000L))
        val finEntries = listOf(FinanceEntry(id = "fe1", type = "EXPENSE", name = "Livro", categoryId = "fc1", walletName = "Banco", currencyCode = "EUR", totalAmountMinor = 2500L, deletedAt = 7777L))
        val finRules = listOf(FinanceRecurringRule(id = "fr1", name = "Assinatura", categoryId = "fc1", amountMinor = 999L))
        val finBudgets = listOf(FinanceBudget(id = "fb1", categoryId = "fc1", limitMinor = 20000L))
        val finLinks = listOf(FinanceNoteLink(id = "fl1", noteId = 100L, noteUuid = "u-1", blockId = "b1"))
        val finRates = listOf(FinanceExchangeRate(id = "er1", fromCurrency = "EUR", toCurrency = "AOA", rate = 980.5))
        val finExecs = listOf(FinanceLinkExecution(id = "ex1", linkId = "fl1", entriesCount = 4, resultStatus = "SUCCESS"))

        val json = BackupManager.createBackupJson(
            tasks = tasks,
            habits = habits,
            focusSessions = sessions,
            categories = categories,
            achievements = achievements,
            notes = notes,
            financeEntries = finEntries,
            financeCategories = finCats,
            financeWallets = finWallets,
            financeRecurringRules = finRules,
            financeBudgets = finBudgets,
            financeNoteLinks = finLinks,
            noteVersions = versions,
            financeExchangeRates = finRates,
            financeLinkExecutions = finExecs,
            preferences = prefs.exportPreferencesMap()
        )

        val parsed = BackupManager.parseBackupJson(json)
        assertEquals(3, parsed.tasks.size)
        assertTrue(parsed.tasks.any { it.isCompleted })
        assertTrue(parsed.tasks.any { it.isWontDo && it.isDisruptive })
        assertEquals(15, parsed.habits.single().reminderLeadMinutes)
        assertEquals(12, parsed.habits.single().bestStreak)
        assertEquals(3, parsed.notes.size)
        assertTrue(parsed.notes.any { it.isArchived && it.noteColor == "#D1FAE5" })
        assertTrue(parsed.notes.any { it.isDeleted && it.associatedDate == "2026-04-21" })
        assertEquals(1, parsed.noteVersions.size)
        assertEquals(1, parsed.financeCategories.size)
        assertTrue(parsed.financeCategories.single().isArchived)
        assertEquals(1, parsed.financeEntries.size)
        assertEquals(7777L, parsed.financeEntries.single().deletedAt)
        assertEquals(1, parsed.financeExchangeRates.size)
        assertEquals(1, parsed.financeLinkExecutions.size)

        // Reset prefs cleanly and import back from parsed.preferences
        prefs.setUserDisplayName("Estudante Citra")
        prefs.setThemeAccent(ThemeAccent.TANGERINA)
        prefs.setFinancePinCode(null)
        prefs.importPreferencesMap(parsed.preferences)

        assertEquals("Márcio Rey", prefs.userDisplayName.value)
        assertEquals(ThemeAccent.AZUL_OCEANO, prefs.themeAccent.value)
        assertEquals("EUR", prefs.financeDefaultCurrency.value)
        assertEquals("1234", prefs.financePinCode.value)
        assertFalse(prefs.welcomePhrasesEnabled.value)
    }

    @org.junit.After
    fun tearDownFileProviderCache() {
        try {
            val field = androidx.core.content.FileProvider::class.java.getDeclaredField("sCache")
            field.isAccessible = true
            val cache = field.get(null) as? MutableMap<*, *>
            cache?.clear()
        } catch (_: Exception) {}
    }
}
