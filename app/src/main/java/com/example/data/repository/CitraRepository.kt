package com.example.data.repository

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesManager
import com.example.data.model.*
import com.example.notifications.NextAlarmScheduler
import com.example.notifications.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class CitraRepository(
    private val context: Context,
    private val database: AppDatabase
) {
    val taskDao = database.taskDao()
    val habitDao = database.habitDao()
    val noteDao = database.noteDao()
    val focusSessionDao = database.focusSessionDao()
    val categoryDao = database.categoryDao()
    val achievementDao = database.achievementDao()
    val noteVersionDao = database.noteVersionDao()
    val financeDao = database.financeDao()
    val preferencesManager = PreferencesManager(context)

    // Categories
    val allCategories: Flow<List<Category>> = categoryDao.getAllCategories()

    suspend fun addCategory(name: String, emoji: String) {
        categoryDao.insertCategory(Category(name = name.trim(), emoji = emoji.trim(), isCustom = true))
    }

    suspend fun deleteCategory(category: Category) {
        categoryDao.deleteCategory(category)
    }

    // Tasks
    val allTasks: Flow<List<Task>> = taskDao.getAllTasks()
    val pendingTasks: Flow<List<Task>> = taskDao.getPendingTasks()

    fun getTasksByDate(dateStr: String): Flow<List<Task>> = taskDao.getTasksByDate(dateStr)

    suspend fun insertTask(task: Task): Long {
        val id = taskDao.insertTask(task)
        NextAlarmScheduler.scheduleNextAlarm(context)
        return id
    }

    suspend fun updateTask(task: Task) {
        taskDao.updateTask(task)
        if (task.isCompleted) {
            checkTaskAchievements()
        }
        NextAlarmScheduler.scheduleNextAlarm(context)
    }

    suspend fun toggleTaskCompleted(task: Task): Boolean {
        val updated = !task.isCompleted
        val completedAt = if (updated) System.currentTimeMillis() else null
        taskDao.updateTask(task.copy(isCompleted = updated, completedAt = completedAt))
        if (updated) {
            checkTaskAchievements()
        }
        NextAlarmScheduler.scheduleNextAlarm(context)
        return updated
    }

    suspend fun deleteTask(task: Task) {
        taskDao.deleteTask(task)
        NextAlarmScheduler.scheduleNextAlarm(context)
    }

    // Habits
    val allHabits: Flow<List<Habit>> = habitDao.getAllHabits()

    suspend fun insertHabit(habit: Habit): Long {
        val id = habitDao.insertHabit(habit)
        NextAlarmScheduler.scheduleNextAlarm(context)
        return id
    }

    suspend fun updateHabit(habit: Habit) {
        habitDao.updateHabit(habit)
        NextAlarmScheduler.scheduleNextAlarm(context)
    }

    suspend fun deleteHabit(habit: Habit) {
        habitDao.deleteHabit(habit)
        NextAlarmScheduler.scheduleNextAlarm(context)
    }

    suspend fun toggleHabitTodayWithConsecutiveDays(habit: Habit): Pair<Boolean, Int> {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayCal = Calendar.getInstance()
        val todayStr = sdf.format(todayCal.time)
        val yesterdayCal = (todayCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        val yesterdayStr = sdf.format(yesterdayCal.time)

        val completedDates = habit.completedDatesCsv.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toMutableSet()
        val isAlreadyDone = completedDates.contains(todayStr)

        return if (isAlreadyDone) {
            completedDates.remove(todayStr)
            val computed = calculateConsecutiveDaysEndingAt(completedDates, yesterdayCal, sdf)
            val newConsecutive = if (computed > 0) computed else (habit.currentStreak - 1).coerceAtLeast(0)
            habitDao.updateHabit(
                habit.copy(
                    currentStreak = newConsecutive,
                    completedDatesCsv = completedDates.joinToString(",")
                )
            )
            Pair(false, newConsecutive)
        } else {
            completedDates.add(todayStr)
            val computed = calculateConsecutiveDaysEndingAt(completedDates, todayCal, sdf)
            val legacyConsecutive = if (habit.lastCompletedDate == yesterdayStr && habit.currentStreak > 0) {
                habit.currentStreak + 1
            } else {
                1
            }
            val newConsecutive = maxOf(computed, legacyConsecutive)
            val newBest = maxOf(newConsecutive, habit.bestStreak)
            habitDao.updateHabit(
                habit.copy(
                    currentStreak = newConsecutive,
                    bestStreak = newBest,
                    lastCompletedDate = todayStr,
                    completedDatesCsv = completedDates.joinToString(",")
                )
            )
            checkHabitAchievements(newConsecutive)
            Pair(true, newConsecutive)
        }
    }

    suspend fun toggleHabitToday(habit: Habit): Boolean {
        return toggleHabitTodayWithConsecutiveDays(habit).first
    }

    private fun calculateConsecutiveDaysEndingAt(
        completedDates: Set<String>,
        endCal: Calendar,
        sdf: SimpleDateFormat
    ): Int {
        var count = 0
        val cursor = endCal.clone() as Calendar
        while (count < 3650) {
            val dayStr = sdf.format(cursor.time)
            if (completedDates.contains(dayStr)) {
                count++
                cursor.add(Calendar.DAY_OF_YEAR, -1)
            } else {
                break
            }
        }
        return count
    }

    // Notes
    val allNotes: Flow<List<Note>> = noteDao.getAllNotes()
    val trashNotes: Flow<List<Note>> = noteDao.getTrashNotes()

    fun searchNotes(query: String): Flow<List<Note>> = noteDao.searchNotes(query)

    fun getNotesByCategory(category: String): Flow<List<Note>> = noteDao.getNotesByCategory(category)

    fun getNotesByDate(dateStr: String): Flow<List<Note>> = noteDao.getNotesByDate(dateStr)

    suspend fun getNoteById(id: Long): Note? = noteDao.getNoteById(id)
    suspend fun getNoteByUuid(uuid: String): Note? = noteDao.getNoteByUuid(uuid)
    suspend fun getNoteByTitle(title: String): Note? = noteDao.getNoteByTitle(title)

    suspend fun insertNote(note: Note): Long {
        val noteUuid = note.uuid.ifBlank { UUID.randomUUID().toString() }
        val finalNote = note.copy(uuid = noteUuid)
        val id = noteDao.insertNote(finalNote)
        if (!finalNote.isExternalDocument && !finalNote.isDeleted) {
            com.example.util.CitraDocsFileManager.saveNoteMarkdown(
                context = context,
                title = finalNote.title,
                content = finalNote.content,
                uuid = noteUuid
            )
        }
        checkNoteAchievements()
        return id
    }

    suspend fun upsertNoteByUuid(note: Note): Long {
        val existing = if (note.uuid.isNotBlank()) noteDao.getNoteByUuid(note.uuid) else null
        return if (existing != null) {
            val updated = note.copy(id = existing.id)
            updateNote(updated)
            existing.id
        } else {
            insertNote(note)
        }
    }

    suspend fun upsertNotesLocally(incomingNotes: List<Note>): Int {
        var count = 0
        incomingNotes.forEach { incomingNote ->
            val noteUuid = incomingNote.uuid.ifBlank { java.util.UUID.randomUUID().toString() }
            val existing = noteDao.getNoteByUuid(noteUuid)
            if (existing != null) {
                if (incomingNote.updatedAt >= existing.updatedAt) {
                    val merged = incomingNote.copy(
                        id = existing.id,
                        uuid = noteUuid
                    )
                    noteDao.updateNote(merged)
                    if (!merged.isExternalDocument) {
                        com.example.util.CitraDocsFileManager.saveNoteMarkdown(
                            context = context,
                            title = merged.title,
                            content = merged.content,
                            uuid = merged.uuid,
                            oldTitle = existing.title
                        )
                    }
                }
            } else {
                val existingByTitle = if (incomingNote.isExternalDocument) null else noteDao.getNoteByTitle(incomingNote.title)?.takeIf { !it.isExternalDocument }
                if (existingByTitle != null) {
                    val merged = incomingNote.copy(
                        id = existingByTitle.id,
                        uuid = noteUuid
                    )
                    noteDao.updateNote(merged)
                    com.example.util.CitraDocsFileManager.saveNoteMarkdown(
                        context = context,
                        title = merged.title,
                        content = merged.content,
                        uuid = merged.uuid,
                        oldTitle = existingByTitle.title
                    )
                } else {
                    noteDao.insertNote(incomingNote.copy(id = 0L, uuid = noteUuid))
                    if (!incomingNote.isExternalDocument) {
                        com.example.util.CitraDocsFileManager.saveNoteMarkdown(
                            context = context,
                            title = incomingNote.title,
                            content = incomingNote.content,
                            uuid = noteUuid
                        )
                    }
                    count++
                }
            }
        }
        checkNoteAchievements()
        return count
    }

    suspend fun deduplicateLocalNotes(): Int {
        val allNotes = noteDao.getAllNotesSync()
        if (allNotes.isEmpty()) return 0
        var removed = 0

        // 1. Deduplicate by UUID
        val byUuid = allNotes.filter { it.uuid.isNotBlank() }.groupBy { it.uuid }
        byUuid.forEach { (_, notes) ->
            if (notes.size > 1) {
                val winner = notes.maxByOrNull { it.updatedAt } ?: notes.first()
                notes.filter { it.id != winner.id }.forEach { dupe ->
                    noteDao.permanentlyDelete(dupe.id)
                    removed++
                }
            }
        }

        // 2. Deduplicate by identical normalized title & content (skip external links with shared empty content unless same externalUri)
        val refreshed = noteDao.getAllNotesSync()
        val byContent = refreshed.groupBy {
            if (it.isExternalDocument) {
                "${it.documentType}:::${it.externalUri.orEmpty().trim()}"
            } else {
                "${it.title.trim().lowercase()}:::${it.content.trim()}"
            }
        }
        byContent.forEach { (_, dupes) ->
            if (dupes.size > 1) {
                val winner = dupes.maxByOrNull { it.updatedAt } ?: dupes.first()
                dupes.filter { it.id != winner.id }.forEach { dupe ->
                    noteDao.permanentlyDelete(dupe.id)
                    removed++
                }
            }
        }
        return removed
    }

    suspend fun insertNotes(notes: List<Note>): Int {
        return upsertNotesLocally(notes)
    }

    suspend fun updateNote(note: Note) {
        val oldNote = noteDao.getNoteById(note.id)
        noteDao.updateNote(note)
        if (!note.isExternalDocument && !note.isDeleted && !note.isArchived) {
            com.example.util.CitraDocsFileManager.saveNoteMarkdown(
                context = context,
                title = note.title,
                content = note.content,
                uuid = note.uuid.ifBlank { null },
                oldTitle = oldNote?.title
            )
        } else if (note.isDeleted || note.isArchived) {
            com.example.util.CitraDocsFileManager.deleteNoteMarkdown(context, note.title, note.uuid)
        }
    }

    suspend fun moveToTrash(note: Note) {
        noteDao.moveToTrash(note.id, System.currentTimeMillis())
    }

    suspend fun restoreFromTrash(note: Note) {
        noteDao.restoreFromTrash(note.id)
    }

    suspend fun permanentlyDeleteNote(note: Note) {
        noteDao.permanentlyDelete(note.id)
    }

    suspend fun cleanOldTrash() {
        val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        noteDao.cleanOldTrash(cutoff)
    }

    suspend fun deleteNote(note: Note) = moveToTrash(note)

    val allArchivedNotes: Flow<List<Note>> = noteDao.getArchivedNotes()

    suspend fun setNoteArchived(note: Note, isArchived: Boolean) {
        noteDao.setArchived(note.id, isArchived)
    }

    suspend fun getAllNotesSync(): List<Note> = noteDao.getAllNotesSync()

    // Focus Sessions
    val allSessions: Flow<List<FocusSession>> = focusSessionDao.getAllSessions()

    fun getSessionsByWeek(weekId: String): Flow<List<FocusSession>> = focusSessionDao.getSessionsByWeek(weekId)

    fun getAvailableWeeks(): Flow<List<String>> = focusSessionDao.getAvailableWeeks()

    suspend fun recordFocusSession(session: FocusSession) {
        val weekId = getMondayOfWeek(session.timestamp)
        val finalSession = session.copy(weekIdentifier = weekId)
        focusSessionDao.insertSession(finalSession)

        if (session.linkedTaskId != null) {
            val task = taskDao.getTaskById(session.linkedTaskId)
            if (task != null) {
                taskDao.updateTask(task.copy(focusMinutesCompleted = task.focusMinutesCompleted + session.durationMinutes))
            }
        }

        checkFocusAchievements(session.durationMinutes, session.breakMinutes)
    }

    // Achievements
    val allAchievements: Flow<List<Achievement>> = achievementDao.getAllAchievements()
    val unlockedAchievementsCount: Flow<Int> = achievementDao.getUnlockedCount()

    private suspend fun unlockAchievement(id: String) {
        val achievement = achievementDao.getAchievementById(id) ?: return
        if (!achievement.isUnlocked) {
            achievementDao.updateAchievement(
                achievement.copy(
                    isUnlocked = true,
                    unlockedAt = System.currentTimeMillis(),
                    currentProgress = achievement.maxProgress
                )
            )
            val settings = preferencesManager.settings.value
            if (settings.notifyAchievements) {
                val sound = if (settings.achievementSound.isNotBlank()) settings.achievementSound else settings.alertSound
                NotificationHelper.showInstantNotification(
                    context = context,
                    id = id.hashCode(),
                    channelId = NotificationHelper.CHANNEL_ALERTS,
                    title = "🏆 Conquista Desbloqueada!",
                    message = "${achievement.iconEmoji} ${achievement.title}: ${achievement.description}",
                    customSoundUriString = sound,
                    vibratePhone = settings.vibrate
                )
            }
        }
    }

    suspend fun deleteAllTasks() {
        taskDao.deleteAllTasks()
    }

    suspend fun deleteAllHabits() {
        habitDao.deleteAllHabits()
    }

    suspend fun deleteAllNotes() {
        noteDao.deleteAllNotes()
    }

    suspend fun resetAllData() {
        taskDao.deleteAllTasks()
        habitDao.deleteAllHabits()
        noteDao.deleteAllNotes()
        focusSessionDao.deleteAllSessions()
        withContext(Dispatchers.IO) {
            com.example.data.backup.AutoDeviceStorageManager.clearSnapshot(context)
            com.example.util.CitraDocsFileManager.deleteAllDocsFiles(context)
            com.example.util.MediaStorageHelper.deleteAllCitraPicFiles(context)
        }
    }

    private suspend fun checkFocusAchievements(focusMinutes: Int, breakMinutes: Int) {
        if (focusMinutes >= 15) unlockAchievement("focus_15")
        if (focusMinutes >= 25) unlockAchievement("focus_25")
        if (focusMinutes >= 30) unlockAchievement("focus_30")
        if (focusMinutes >= 45) unlockAchievement("focus_45")
        if (focusMinutes >= 60) unlockAchievement("focus_60")
        if (focusMinutes >= 90) unlockAchievement("focus_90")
        if (focusMinutes >= 120) unlockAchievement("focus_120")

        if (breakMinutes in 5..9) unlockAchievement("break_5")
        if (breakMinutes in 10..14) unlockAchievement("break_10")
        if (breakMinutes >= 15) unlockAchievement("break_15")
    }

    private suspend fun checkTaskAchievements() {
        val tasks = taskDao.getAllTasks().firstOrNull() ?: emptyList()
        val completedCount = tasks.count { it.isCompleted }
        if (completedCount >= 1) unlockAchievement("task_1")
        if (completedCount >= 5) unlockAchievement("task_5")
        if (completedCount >= 10) unlockAchievement("task_10")
        if (completedCount >= 20) unlockAchievement("task_20")
    }

    private suspend fun checkHabitAchievements(streak: Int) {
        if (streak >= 3) unlockAchievement("streak_3")
        if (streak >= 7) unlockAchievement("streak_7") // 1 semana
        if (streak >= 14) unlockAchievement("streak_14") // 2 semanas
        if (streak >= 21) unlockAchievement("streak_21") // 3 semanas
        if (streak >= 28) unlockAchievement("streak_28") // 4 semanas
        if (streak >= 35) unlockAchievement("streak_35") // 5 semanas
        if (streak >= 42) unlockAchievement("streak_42") // 6 semanas
        if (streak >= 49) unlockAchievement("streak_49") // 7 semanas
        if (streak >= 56) unlockAchievement("streak_56") // 8 semanas
        if (streak >= 63) unlockAchievement("streak_63") // 9 semanas
    }

    private suspend fun checkNoteAchievements() {
        val notes = noteDao.getAllNotes().firstOrNull() ?: emptyList()
        if (notes.size >= 1) unlockAchievement("note_1")
        if (notes.size >= 5) unlockAchievement("note_5")
        if (notes.size >= 10) unlockAchievement("note_10")
    }

    // Backup & Restore
    suspend fun restoreBackup(
        tasks: List<Task>,
        habits: List<Habit>,
        sessions: List<FocusSession>,
        categories: List<Category>,
        achievements: List<Achievement> = emptyList(),
        notes: List<Note> = emptyList(),
        financeEntries: List<FinanceEntry> = emptyList(),
        financeCategories: List<FinanceCategory> = emptyList(),
        financeWallets: List<FinanceWallet> = emptyList(),
        financeRecurringRules: List<FinanceRecurringRule> = emptyList(),
        financeBudgets: List<FinanceBudget> = emptyList(),
        financeNoteLinks: List<FinanceNoteLink> = emptyList(),
        noteVersions: List<NoteVersion> = emptyList(),
        financeExchangeRates: List<FinanceExchangeRate> = emptyList(),
        financeLinkExecutions: List<FinanceLinkExecution> = emptyList()
    ) {
        if (tasks.isNotEmpty()) taskDao.insertTasks(tasks)
        if (habits.isNotEmpty()) habitDao.insertHabits(habits)
        if (sessions.isNotEmpty()) {
            val existingSessions = focusSessionDao.getAllSessionsSync()
            val existingTimestamps = existingSessions.map { it.timestamp }.toSet()
            val newSessions = sessions.filter { it.timestamp !in existingTimestamps }.map { it.copy(id = 0L) }
            if (newSessions.isNotEmpty()) {
                focusSessionDao.insertSessions(newSessions)
            }
        }
        if (categories.isNotEmpty()) {
            categories.forEach { categoryDao.insertCategory(it) }
        }
        if (achievements.isNotEmpty()) {
            achievements.forEach { cloudAch ->
                val localAch = achievementDao.getAchievementById(cloudAch.id)
                if (localAch == null) {
                    achievementDao.insertDefaultAchievements(listOf(cloudAch))
                } else if (cloudAch.isUnlocked && !localAch.isUnlocked) {
                    achievementDao.updateAchievement(
                        localAch.copy(
                            isUnlocked = true,
                            unlockedAt = cloudAch.unlockedAt ?: System.currentTimeMillis(),
                            currentProgress = maxOf(localAch.currentProgress, cloudAch.currentProgress)
                        )
                    )
                } else if (cloudAch.currentProgress > localAch.currentProgress) {
                    achievementDao.updateAchievement(
                        localAch.copy(currentProgress = cloudAch.currentProgress)
                    )
                }
            }
        }
        val noteIdMap = mutableMapOf<Long, Long>()
        if (notes.isNotEmpty()) {
            val existingNotes = noteDao.getAllNotesIncludingDeletedSync()
            val existingByUuid = existingNotes.filter { it.uuid.isNotBlank() }.associateBy { it.uuid }.toMutableMap()
            val usedIds = existingNotes.map { it.id }.toMutableSet()

            notes.forEach { incoming ->
                val oldId = incoming.id
                val cleanUuid = incoming.uuid.ifBlank { UUID.randomUUID().toString() }
                val existingMatch = existingByUuid[cleanUuid]
                val savedId = if (existingMatch != null) {
                    val merged = incoming.copy(id = existingMatch.id, uuid = cleanUuid)
                    noteDao.updateNote(merged)
                    existingByUuid[cleanUuid] = merged
                    existingMatch.id
                } else {
                    val candidateId = if (incoming.id > 0L && !usedIds.contains(incoming.id)) incoming.id else 0L
                    val insertedId = noteDao.insertNote(incoming.copy(id = candidateId, uuid = cleanUuid))
                    usedIds.add(insertedId)
                    existingByUuid[cleanUuid] = incoming.copy(id = insertedId, uuid = cleanUuid)
                    insertedId
                }
                if (oldId > 0L) {
                    noteIdMap[oldId] = savedId
                }
                if (!incoming.isExternalDocument && !incoming.isDeleted) {
                    com.example.util.CitraDocsFileManager.saveNoteMarkdown(
                        context = context,
                        title = incoming.title,
                        content = incoming.content,
                        uuid = cleanUuid
                    )
                }
            }
        }
        if (noteVersions.isNotEmpty()) {
            val existingVersions = noteVersionDao.getAllVersionsSync()
            val existingKeys = existingVersions.map { "${it.noteId}:${it.versionNumber}" }.toMutableSet()
            val mappedVersions = noteVersions.mapNotNull { v ->
                val targetNoteId = noteIdMap[v.noteId] ?: v.noteId
                val key = "$targetNoteId:${v.versionNumber}"
                if (targetNoteId <= 0L || existingKeys.contains(key)) {
                    null
                } else {
                    existingKeys.add(key)
                    v.copy(id = 0L, noteId = targetNoteId)
                }
            }
            if (mappedVersions.isNotEmpty()) {
                noteVersionDao.insertVersions(mappedVersions)
            }
        }
        if (financeCategories.isNotEmpty()) {
            financeDao.insertCategories(financeCategories)
        }
        if (financeWallets.isNotEmpty()) {
            financeDao.insertWallets(financeWallets)
        }
        if (financeEntries.isNotEmpty()) {
            financeDao.insertEntries(financeEntries)
        }
        if (financeRecurringRules.isNotEmpty()) {
            financeDao.insertRecurringRules(financeRecurringRules)
        }
        if (financeBudgets.isNotEmpty()) {
            financeDao.insertBudgets(financeBudgets)
        }
        if (financeNoteLinks.isNotEmpty()) {
            val mappedLinks = financeNoteLinks.map { link ->
                val remappedNoteId = noteIdMap[link.noteId]
                    ?: (if (link.noteUuid.isNotBlank()) noteDao.getNoteByUuid(link.noteUuid)?.id else null)
                    ?: link.noteId
                link.copy(noteId = remappedNoteId)
            }
            financeDao.insertLinks(mappedLinks)
        }
        if (financeExchangeRates.isNotEmpty()) {
            financeDao.insertExchangeRates(financeExchangeRates)
        }
        if (financeLinkExecutions.isNotEmpty()) {
            financeDao.insertExecutions(financeLinkExecutions)
        }
        com.example.notifications.NextAlarmScheduler.scheduleNextAlarm(context)
    }

    suspend fun seedInitialDataIfNeeded() {
        cleanOldTrash()
        // If device backup exists on Android storage, restore user data and skip mock seeding
        val restoredFromDevice = com.example.data.backup.AutoDeviceStorageManager.tryRestoreFullAppSnapshot(context, database)
        if (restoredFromDevice) {
            com.example.notifications.NextAlarmScheduler.scheduleNextAlarm(context)
            return
        }
        // Seed default categories
        val defaultCategories = listOf(
            Category(name = "Rascunhos", emoji = "📝", isCustom = false),
            Category(name = "Estudos", emoji = "📚", isCustom = false),
            Category(name = "Trabalho", emoji = "💼", isCustom = false),
            Category(name = "Logística", emoji = "🚚", isCustom = false),
            Category(name = "Leitura", emoji = "📖", isCustom = false),
            Category(name = "Criatividade", emoji = "🎨", isCustom = false),
            Category(name = "Saúde", emoji = "🏃", isCustom = false),
            Category(name = "Outro", emoji = "🎯", isCustom = false)
        )
        categoryDao.insertDefaultCategories(defaultCategories)

        // Seed default achievements
        val defaultAchievements = listOf(
            Achievement("focus_15", "Primeiro Foco", "Complete uma sessão de foco de 15 minutos", "🌱", "Foco"),
            Achievement("focus_25", "Arancia Clássico", "Complete uma sessão de foco de 25 minutos", "⏱️", "Foco"),
            Achievement("focus_30", "Foco Dedicado", "Complete uma sessão de foco de 30 minutos", "🎯", "Foco"),
            Achievement("focus_45", "Imersão Criativa", "Complete uma sessão de foco de 45 minutos", "🎨", "Foco"),
            Achievement("focus_60", "Citra Amarelo", "Ganhe o Citra Amarelo ao completar uma sessão de 60 minutos", "🍋", "Foco"),
            Achievement("focus_90", "Maratona Produtiva", "Supere 90 minutos de foco intenso", "🚀", "Foco"),
            Achievement("focus_120", "Citra Dourado", "Ganhe o Citra Dourado ao completar uma sessão de 120 minutos", "🌟", "Foco"),
            Achievement("break_5", "Pausa Sagrada", "Realize uma pausa saudável de 5 minutos", "☕", "Foco"),
            Achievement("break_10", "Respiro Profundo", "Realize uma pausa relaxante de 10 minutos", "🧘", "Foco"),
            Achievement("break_15", "Descanso Revigorante", "Descanse por 15 minutos entre ciclos", "🌿", "Foco"),
            Achievement("task_1", "Primeiro Passo", "Conclua sua primeira tarefa", "✅", "Tarefas"),
            Achievement("task_5", "Produtor Ativo", "Conclua 5 tarefas no aplicativo", "📋", "Tarefas"),
            Achievement("task_10", "Em Chamas", "Conclua 10 tarefas", "⚡", "Tarefas"),
            Achievement("task_20", "Mestre da Execução", "Conclua 20 tarefas com sucesso", "🏆", "Tarefas"),
            Achievement("streak_3", "Ritmo Inicial", "Cumpra um hábito por 3 dias seguidos", "🌱", "Hábitos"),
            Achievement("streak_7", "1 Semana de Check-in", "Cumpra 7 dias seguidos de hábito", "🌟", "Hábitos"),
            Achievement("streak_14", "2 Semanas de Disciplina", "Cumpra 14 dias seguidos de hábito", "💎", "Hábitos"),
            Achievement("streak_21", "3 Semanas de Transformação", "Cumpra 21 dias seguidos de hábito", "🛡️", "Hábitos"),
            Achievement("streak_28", "4 Semanas (1 Mês)", "Cumpra 28 dias seguidos de hábito", "🏰", "Hábitos"),
            Achievement("streak_35", "5 Semanas Inabalável", "Cumpra 35 dias seguidos de hábito", "🏵️", "Hábitos"),
            Achievement("streak_42", "6 Semanas de Mestria", "Cumpra 42 dias seguidos de hábito", "⚔️", "Hábitos"),
            Achievement("streak_49", "7 Semanas Imbatível", "Cumpra 49 dias seguidos de hábito", "🔮", "Hábitos"),
            Achievement("streak_56", "8 Semanas de Excelência", "Cumpra 56 dias seguidos de hábito", "🌌", "Hábitos"),
            Achievement("streak_63", "9 Semanas Lendárias", "Cumpra 63 dias seguidos de hábito", "🪐", "Hábitos"),
            Achievement("note_1", "Caderno Aberto", "Crie sua primeira anotação em Markdown", "📝", "Anotações"),
            Achievement("note_5", "Mente Organizada", "Salve 5 anotações com formatação", "📚", "Anotações")
        )
        achievementDao.insertDefaultAchievements(defaultAchievements)

        // Ensure existing streak_3 achievement never retains old fire/streak text
        val existingStreak3 = achievementDao.getAchievementById("streak_3")
        if (existingStreak3 != null && (existingStreak3.description.contains("sequência", ignoreCase = true) || existingStreak3.iconEmoji == "🔥")) {
            achievementDao.updateAchievement(
                existingStreak3.copy(
                    title = "Ritmo Inicial",
                    description = "Cumpra um hábito por 3 dias seguidos",
                    iconEmoji = "🌱"
                )
            )
        }

        // Seed initial note if needed
        val currentNotes = noteDao.getAllNotes().firstOrNull()
        if (currentNotes.isNullOrEmpty()) {
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            noteDao.insertNote(
                Note(
                    title = "Guia do Citra 🍊",
                    content = """# 🍊 Bem-vindo ao Citra!

> [!TIP] Produtividade leve, gamificada e 100% offline — sua rotina organizada em um só lugar.

O **Citra** combina foco ==Pomodoro flexível== (`15 a 120 min`), músicas locais e conquistas para ajudar você a manter consistência no dia a dia.

---

## ✨ O que o app faz

| Pilar | Para que serve |
| --- | --- |
| **⏱️ Timer & Foco** | Ciclos personalizáveis, pausas e som ambiente |
| **✅ Tarefas** | Prioridades (*Alta, Média, Baixa*), prazos, alarmes e vínculo com foco |
| **🌱 Hábitos** | Frequência diária ou personalizada, check-in rápido e **sequências** |
| **📅 Calendário** | Visão unificada de tarefas, hábitos e notas em cada data |
| **📝 Notas (.md)** | Editor Markdown com imagens, áudios, cores, PDF/DOCX e pasta `Citra Docs` |
| **💰 Finanças** | Carteiras, receitas, despesas, metas e **notas vinculadas** automáticas |

---

## 🚀 Primeiros passos

- [x] Conhecer o **Guia do Citra**
- [ ] Criar sua primeira **tarefa** ou **hábito** na aba de Organização
- [ ] Iniciar um ciclo no **Timer** e desbloquear conquistas
- [ ] Personalizar suas notas com `**negrito**`, `*itálico*`, `==destaque==` e tabelas

> *"Pequenos passos constantes constroem grandes resultados."*""",
                    category = "Estudos",
                    iconEmoji = "📘",
                    associatedDate = todayStr
                )
            )
        }
    }

    // --- Note Version History ---
    fun getVersionsForNote(noteId: Long): Flow<List<NoteVersion>> = noteVersionDao.getVersionsForNote(noteId)

    suspend fun restoreNoteVersion(note: Note, version: NoteVersion) {
        val updated = note.copy(
            title = version.title,
            content = version.content,
            updatedAt = System.currentTimeMillis()
        )
        noteDao.updateNote(updated)
    }

    suspend fun createLocalCopyOfVersion(version: NoteVersion): Long {
        return noteDao.insertNote(
            Note(
                title = "${version.title} (Cópia Local)",
                content = version.content,
                category = "Geral",
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private fun getMondayOfWeek(timestamp: Long): String = Companion.getMondayOfWeek(timestamp)

    companion object {
        fun getMondayOfWeek(timestamp: Long): String {
            val cal = Calendar.getInstance().apply {
                timeInMillis = timestamp
                firstDayOfWeek = Calendar.MONDAY
                set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            }
            return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
        }
    }
}
