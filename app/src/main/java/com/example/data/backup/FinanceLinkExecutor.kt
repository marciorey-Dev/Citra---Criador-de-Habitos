package com.example.data.backup

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesManager
import com.example.data.model.*
import com.example.notifications.NotificationHelper
import com.example.util.CitraDocsFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

sealed class LinkExecutionOutcome {
    data class Success(val noteTitle: String, val entriesCount: Int) : LinkExecutionOutcome()
    data object DeferredNoteOpen : LinkExecutionOutcome()
    data object PausedNoteDeleted : LinkExecutionOutcome()
    data object PausedMarkersDeleted : LinkExecutionOutcome()
    data object ModuleDisabled : LinkExecutionOutcome()
    data class Error(val message: String) : LinkExecutionOutcome()
}

object FinanceLinkExecutor {

    @Volatile
    var activeEditorNoteUuid: String? = null

    @Volatile
    var activeEditorNoteId: Long? = null

    suspend fun executeLink(
        context: Context,
        link: FinanceNoteLink,
        isManualTrigger: Boolean = false,
        nowMillis: Long = System.currentTimeMillis()
    ): LinkExecutionOutcome = withContext(Dispatchers.IO) {
        val prefs = PreferencesManager(context)
        if (!prefs.financesEnabled.value) {
            return@withContext LinkExecutionOutcome.ModuleDisabled
        }

        val db = AppDatabase.getInstance(context)
        val financeDao = db.financeDao()
        val noteDao = db.noteDao()

        // Check if target note is currently open in editor
        if (!isManualTrigger && (
                (link.noteUuid.isNotBlank() && link.noteUuid == activeEditorNoteUuid) ||
                    (link.noteId > 0L && link.noteId == activeEditorNoteId)
                )
        ) {
            return@withContext LinkExecutionOutcome.DeferredNoteOpen
        }

        val allEntries = financeDao.getAllEntriesIncludingDeleted()
        val categories = financeDao.getAllCategoriesSync()
        val activeEntries = allEntries.filter { it.deletedAt == null }
        val baseBalance = prefs.financeBaseBalanceMinor.value
        val currentBalance = baseBalance + activeEntries.sumOf {
            if (it.type == FinanceEntryType.INCOME.id) it.totalAmountMinor else -it.totalAmountMinor
        }
        val defaultCurrency = FinanceCurrency.fromCode(prefs.financeDefaultCurrency.value)

        val freq = FinanceLinkFrequency.fromId(link.frequency)
        val window = FinanceNoteMarkdownEngine.computePeriodWindow(freq, nowMillis)
        val (innerMarkdown, entriesCount) = FinanceNoteMarkdownEngine.generateBlockInnerMarkdown(
            link = link,
            allEntries = allEntries,
            categories = categories,
            currentBalanceMinor = currentBalance,
            defaultCurrency = defaultCurrency,
            nowMillis = nowMillis
        )

        if (entriesCount == 0 && link.emptyPeriodBehavior == "DO_NOTHING") {
            financeDao.updateLink(link.copy(lastExecutedAt = nowMillis, updatedAt = nowMillis))
            return@withContext LinkExecutionOutcome.Success("Sem alterações", 0)
        }

        val writeMode = FinanceLinkWriteMode.fromId(link.writeMode)
        if (writeMode == FinanceLinkWriteMode.NEW_NOTE_PER_PERIOD) {
            val resolvedTitle = FinanceNoteMarkdownEngine.resolveNewNoteTitle(link.newNoteTitleTemplate, nowMillis)
            val existingByTitle = noteDao.getNoteByTitle(resolvedTitle)
            val targetNote = if (existingByTitle != null && !existingByTitle.isDeleted) {
                val updatedContent = FinanceNoteMarkdownEngine.replaceOrInsertManagedBlock(
                    originalContent = existingByTitle.content,
                    link = link,
                    innerMarkdown = innerMarkdown,
                    periodWindow = window
                )
                financeDao.insertExecution(
                    FinanceLinkExecution(
                        linkId = link.id,
                        executedAt = nowMillis,
                        entriesCount = entriesCount,
                        resultStatus = "SUCCESS",
                        previousNoteContent = existingByTitle.content
                    )
                )
                val updated = existingByTitle.copy(content = updatedContent, updatedAt = nowMillis)
                noteDao.updateNote(updated)
                updated
            } else {
                val newUuid = UUID.randomUUID().toString()
                val startMarker = FinanceNoteMarkdownEngine.buildStartMarker(link)
                val endMarker = FinanceNoteMarkdownEngine.buildEndMarker(link.blockId)
                val content = "# $resolvedTitle\n\n$startMarker\n$innerMarkdown\n$endMarker"
                val created = Note(
                    uuid = newUuid,
                    title = resolvedTitle,
                    content = content,
                    category = "Finanças",
                    iconEmoji = "💰",
                    updatedAt = nowMillis
                )
                val newId = noteDao.insertNote(created)
                financeDao.insertExecution(
                    FinanceLinkExecution(
                        linkId = link.id,
                        executedAt = nowMillis,
                        entriesCount = entriesCount,
                        resultStatus = "SUCCESS",
                        previousNoteContent = null
                    )
                )
                created.copy(id = newId)
            }

            CitraDocsFileManager.saveNoteMarkdown(
                context = context,
                title = targetNote.title,
                content = targetNote.content,
                uuid = targetNote.uuid
            )
            financeDao.updateLink(
                link.copy(
                    noteId = targetNote.id,
                    noteUuid = targetNote.uuid,
                    lastExecutedAt = nowMillis,
                    updatedAt = nowMillis
                )
            )
            if (link.notifyOnUpdate) {
                NotificationHelper.showInstantNotification(
                    context = context,
                    id = link.id.hashCode(),
                    channelId = NotificationHelper.CHANNEL_ALERTS,
                    title = "Nota financeira atualizada",
                    message = "${targetNote.title} atualizada: $entriesCount lançamentos"
                )
            }
            return@withContext LinkExecutionOutcome.Success(targetNote.title, entriesCount)
        }

        // Find existing note by UUID or ID
        val targetNote = (if (link.noteUuid.isNotBlank()) noteDao.getNoteByUuid(link.noteUuid) else null)
            ?: (if (link.noteId > 0L) noteDao.getNoteById(link.noteId) else null)

        if (targetNote == null || targetNote.isDeleted) {
            financeDao.updateLink(link.copy(status = "PAUSED", updatedAt = nowMillis))
            financeDao.insertExecution(
                FinanceLinkExecution(
                    linkId = link.id,
                    executedAt = nowMillis,
                    entriesCount = 0,
                    resultStatus = "PAUSED_NOTE_DELETED",
                    previousNoteContent = null
                )
            )
            return@withContext LinkExecutionOutcome.PausedNoteDeleted
        }

        if (writeMode == FinanceLinkWriteMode.REPLACE_BLOCK &&
            !FinanceNoteMarkdownEngine.hasManagedBlock(targetNote.content, link.blockId)
        ) {
            financeDao.updateLink(link.copy(status = "PAUSED", updatedAt = nowMillis))
            financeDao.insertExecution(
                FinanceLinkExecution(
                    linkId = link.id,
                    executedAt = nowMillis,
                    entriesCount = 0,
                    resultStatus = "PAUSED_MARKERS_DELETED",
                    previousNoteContent = targetNote.content
                )
            )
            return@withContext LinkExecutionOutcome.PausedMarkersDeleted
        }

        val previousContent = targetNote.content
        val updatedContent = FinanceNoteMarkdownEngine.replaceOrInsertManagedBlock(
            originalContent = previousContent,
            link = link,
            innerMarkdown = innerMarkdown,
            periodWindow = window
        )

        val updatedNote = targetNote.copy(
            content = updatedContent,
            updatedAt = nowMillis
        )
        noteDao.updateNote(updatedNote)
        CitraDocsFileManager.saveNoteMarkdown(
            context = context,
            title = updatedNote.title,
            content = updatedNote.content,
            uuid = updatedNote.uuid
        )

        financeDao.insertExecution(
            FinanceLinkExecution(
                linkId = link.id,
                executedAt = nowMillis,
                entriesCount = entriesCount,
                resultStatus = "SUCCESS",
                previousNoteContent = previousContent
            )
        )
        financeDao.updateLink(
            link.copy(
                noteId = updatedNote.id,
                noteUuid = updatedNote.uuid,
                lastExecutedAt = nowMillis,
                updatedAt = nowMillis
            )
        )

        if (link.notifyOnUpdate) {
            NotificationHelper.showInstantNotification(
                context = context,
                id = link.id.hashCode(),
                channelId = NotificationHelper.CHANNEL_ALERTS,
                title = "Nota financeira atualizada",
                message = "${updatedNote.title} atualizada: $entriesCount lançamentos"
            )
        }

        LinkExecutionOutcome.Success(updatedNote.title, entriesCount)
    }

    suspend fun runDueLinks(context: Context): Int = withContext(Dispatchers.IO) {
        val prefs = PreferencesManager(context)
        if (!prefs.financesEnabled.value) return@withContext 0
        val db = AppDatabase.getInstance(context)
        val links = db.financeDao().getAllLinksSync()
        val now = System.currentTimeMillis()
        var executed = 0
        for (link in links) {
            if (FinanceNoteMarkdownEngine.isDueForExecution(link, now)) {
                val outcome = executeLink(context, link, isManualTrigger = false, nowMillis = now)
                if (outcome is LinkExecutionOutcome.Success) {
                    executed++
                }
            }
        }
        executed
    }

    suspend fun undoLastExecution(context: Context, link: FinanceNoteLink): Boolean = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        val financeDao = db.financeDao()
        val noteDao = db.noteDao()
        val lastExec = financeDao.getLatestExecutionForLink(link.id) ?: return@withContext false
        val prevContent = lastExec.previousNoteContent ?: return@withContext false
        val note = (if (link.noteUuid.isNotBlank()) noteDao.getNoteByUuid(link.noteUuid) else null)
            ?: (if (link.noteId > 0L) noteDao.getNoteById(link.noteId) else null)
            ?: return@withContext false

        val now = System.currentTimeMillis()
        val restored = note.copy(content = prevContent, updatedAt = now)
        noteDao.updateNote(restored)
        CitraDocsFileManager.saveNoteMarkdown(
            context = context,
            title = restored.title,
            content = restored.content,
            uuid = restored.uuid
        )
        true
    }

    suspend fun recreateMarkersForPausedLink(context: Context, link: FinanceNoteLink): LinkExecutionOutcome =
        withContext(Dispatchers.IO) {
            val db = AppDatabase.getInstance(context)
            val noteDao = db.noteDao()
            val financeDao = db.financeDao()
            val note = (if (link.noteUuid.isNotBlank()) noteDao.getNoteByUuid(link.noteUuid) else null)
                ?: (if (link.noteId > 0L) noteDao.getNoteById(link.noteId) else null)
                ?: return@withContext LinkExecutionOutcome.PausedNoteDeleted

            val startMarker = FinanceNoteMarkdownEngine.buildStartMarker(link)
            val endMarker = FinanceNoteMarkdownEngine.buildEndMarker(link.blockId)
            val appended = "${note.content.trimEnd()}\n\n$startMarker\n$endMarker"
            noteDao.updateNote(note.copy(content = appended, updatedAt = System.currentTimeMillis()))
            val reactivated = link.copy(status = "ACTIVE", updatedAt = System.currentTimeMillis())
            financeDao.updateLink(reactivated)
            executeLink(context, reactivated, isManualTrigger = true)
        }

    suspend fun recreateNoteForPausedLink(context: Context, link: FinanceNoteLink): LinkExecutionOutcome =
        withContext(Dispatchers.IO) {
            val db = AppDatabase.getInstance(context)
            val noteDao = db.noteDao()
            val financeDao = db.financeDao()
            val now = System.currentTimeMillis()
            val title = FinanceNoteMarkdownEngine.resolveNewNoteTitle(link.newNoteTitleTemplate, now)
            val newUuid = UUID.randomUUID().toString()
            val startMarker = FinanceNoteMarkdownEngine.buildStartMarker(link)
            val endMarker = FinanceNoteMarkdownEngine.buildEndMarker(link.blockId)
            val newNote = Note(
                uuid = newUuid,
                title = title,
                content = "# $title\n\n$startMarker\n$endMarker",
                category = "Finanças",
                iconEmoji = "💰",
                updatedAt = now
            )
            val newId = noteDao.insertNote(newNote)
            val reactivated = link.copy(
                noteId = newId,
                noteUuid = newUuid,
                status = "ACTIVE",
                updatedAt = now
            )
            financeDao.updateLink(reactivated)
            executeLink(context, reactivated, isManualTrigger = true, nowMillis = now)
        }
}
