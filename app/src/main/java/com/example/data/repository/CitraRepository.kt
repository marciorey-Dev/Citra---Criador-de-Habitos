package com.example.data.repository

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesManager
import com.example.data.model.*
import com.example.notifications.NotificationHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import java.text.SimpleDateFormat
import java.util.*

class CitraRepository(
    private val context: Context,
    private val database: AppDatabase
) {
    private val taskDao = database.taskDao()
    private val habitDao = database.habitDao()
    private val noteDao = database.noteDao()
    private val focusSessionDao = database.focusSessionDao()
    private val categoryDao = database.categoryDao()
    private val achievementDao = database.achievementDao()
    private val preferencesManager = PreferencesManager(context)

    // Categories
    val allCategories: Flow<List<Category>> = categoryDao.getAllCategories()

    suspend fun addCategory(name: String, emoji: String) {
        categoryDao.insertCategory(Category(name = name.trim(), emoji = emoji.trim(), isCustom = true))
    }

    suspend fun deleteCategory(category: Category) {
        if (category.isCustom) {
            categoryDao.deleteCategory(category)
        }
    }

    // Tasks
    val allTasks: Flow<List<Task>> = taskDao.getAllTasks()
    val pendingTasks: Flow<List<Task>> = taskDao.getPendingTasks()

    fun getTasksByDate(dateStr: String): Flow<List<Task>> = taskDao.getTasksByDate(dateStr)

    suspend fun insertTask(task: Task): Long {
        val id = taskDao.insertTask(task)
        if (task.dueDateTimestamp != null && task.dueDateTimestamp > System.currentTimeMillis()) {
            val settings = preferencesManager.settings.value
            val sound = if (task.customSoundUri.isNotBlank()) task.customSoundUri
                else if (settings.taskSoundUri.isNotBlank()) settings.taskSoundUri else settings.taskSound
            NotificationHelper.scheduleTaskReminder(
                context = context,
                taskId = id,
                title = task.title,
                dueTimestamp = task.dueDateTimestamp,
                minutesBefore = task.reminderMinutesBefore,
                customSoundUri = sound,
                loopAlarm = task.loopAlarm
            )
        }
        return id
    }

    suspend fun updateTask(task: Task) {
        taskDao.updateTask(task)
        if (task.isCompleted) {
            NotificationHelper.cancelTaskReminder(context, task.id)
            checkTaskAchievements()
        } else if (task.dueDateTimestamp != null && task.dueDateTimestamp > System.currentTimeMillis()) {
            val settings = preferencesManager.settings.value
            val sound = if (task.customSoundUri.isNotBlank()) task.customSoundUri
                else if (settings.taskSoundUri.isNotBlank()) settings.taskSoundUri else settings.taskSound
            NotificationHelper.scheduleTaskReminder(
                context = context,
                taskId = task.id,
                title = task.title,
                dueTimestamp = task.dueDateTimestamp,
                minutesBefore = task.reminderMinutesBefore,
                customSoundUri = sound,
                loopAlarm = task.loopAlarm
            )
        }
    }

    suspend fun toggleTaskCompleted(task: Task): Boolean {
        val updated = !task.isCompleted
        val completedAt = if (updated) System.currentTimeMillis() else null
        taskDao.updateTask(task.copy(isCompleted = updated, completedAt = completedAt))
        if (updated) {
            NotificationHelper.cancelTaskReminder(context, task.id)
            checkTaskAchievements()
        }
        return updated
    }

    suspend fun deleteTask(task: Task) {
        NotificationHelper.cancelTaskReminder(context, task.id)
        taskDao.deleteTask(task)
    }

    // Habits
    val allHabits: Flow<List<Habit>> = habitDao.getAllHabits()

    suspend fun insertHabit(habit: Habit): Long {
        val id = habitDao.insertHabit(habit)
        if (habit.reminderTime.isNotBlank()) {
            val settings = preferencesManager.settings.value
            val sound = if (habit.customSoundUri.isNotBlank()) habit.customSoundUri
                else if (settings.habitSoundUri.isNotBlank()) settings.habitSoundUri else settings.habitSound
            NotificationHelper.scheduleHabitReminder(
                context = context,
                habitId = id,
                title = habit.title,
                timeString = habit.reminderTime,
                leadMinutes = habit.reminderLeadMinutes,
                customSoundUri = sound,
                loopAlarm = habit.loopAlarm
            )
        }
        return id
    }

    suspend fun updateHabit(habit: Habit) {
        habitDao.updateHabit(habit)
        if (habit.reminderTime.isNotBlank()) {
            val settings = preferencesManager.settings.value
            val sound = if (habit.customSoundUri.isNotBlank()) habit.customSoundUri
                else if (settings.habitSoundUri.isNotBlank()) settings.habitSoundUri else settings.habitSound
            NotificationHelper.scheduleHabitReminder(
                context = context,
                habitId = habit.id,
                title = habit.title,
                timeString = habit.reminderTime,
                leadMinutes = habit.reminderLeadMinutes,
                customSoundUri = sound,
                loopAlarm = habit.loopAlarm
            )
        }
    }

    suspend fun deleteHabit(habit: Habit) {
        NotificationHelper.cancelHabitReminder(context, habit.id)
        habitDao.deleteHabit(habit)
    }

    suspend fun toggleHabitToday(habit: Habit): Boolean {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val completedDates = habit.completedDatesCsv.split(",").filter { it.isNotBlank() }.toMutableSet()
        val isAlreadyDone = completedDates.contains(todayStr)

        return if (isAlreadyDone) {
            completedDates.remove(todayStr)
            val newStreak = (habit.currentStreak - 1).coerceAtLeast(0)
            habitDao.updateHabit(
                habit.copy(
                    currentStreak = newStreak,
                    completedDatesCsv = completedDates.joinToString(",")
                )
            )
            false
        } else {
            completedDates.add(todayStr)
            val newStreak = habit.currentStreak + 1
            val newBest = maxOf(newStreak, habit.bestStreak)
            habitDao.updateHabit(
                habit.copy(
                    currentStreak = newStreak,
                    bestStreak = newBest,
                    lastCompletedDate = todayStr,
                    completedDatesCsv = completedDates.joinToString(",")
                )
            )
            checkHabitAchievements(newStreak)
            true
        }
    }

    // Notes
    val allNotes: Flow<List<Note>> = noteDao.getAllNotes()
    val trashNotes: Flow<List<Note>> = noteDao.getTrashNotes()

    fun searchNotes(query: String): Flow<List<Note>> = noteDao.searchNotes(query)

    fun getNotesByCategory(category: String): Flow<List<Note>> = noteDao.getNotesByCategory(category)

    fun getNotesByDate(dateStr: String): Flow<List<Note>> = noteDao.getNotesByDate(dateStr)

    suspend fun insertNote(note: Note): Long {
        val id = noteDao.insertNote(note)
        checkNoteAchievements()
        return id
    }

    suspend fun insertNotes(notes: List<Note>): Int {
        noteDao.insertNotes(notes)
        checkNoteAchievements()
        return notes.size
    }

    suspend fun updateNote(note: Note) = noteDao.updateNote(note)

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
    suspend fun restoreBackup(tasks: List<Task>, habits: List<Habit>, sessions: List<FocusSession>, categories: List<Category>) {
        if (tasks.isNotEmpty()) taskDao.insertTasks(tasks)
        if (habits.isNotEmpty()) habitDao.insertHabits(habits)
        if (sessions.isNotEmpty()) focusSessionDao.insertSessions(sessions)
        if (categories.isNotEmpty()) {
            categories.forEach { categoryDao.insertCategory(it) }
        }
    }

    suspend fun seedInitialDataIfNeeded() {
        cleanOldTrash()
        // If device backup exists on Android storage, restore user data and skip mock seeding
        val restoredFromDevice = com.example.data.backup.AutoDeviceStorageManager.tryRestoreFullAppSnapshot(context, database)
        if (restoredFromDevice) {
            return
        }
        // Seed default categories
        val defaultCategories = listOf(
            Category(name = "Estudos", emoji = "📚", isCustom = false),
            Category(name = "Trabalho", emoji = "💼", isCustom = false),
            Category(name = "Leitura", emoji = "📖", isCustom = false),
            Category(name = "Criatividade", emoji = "🎨", isCustom = false),
            Category(name = "Saúde", emoji = "🏃", isCustom = false),
            Category(name = "Outro", emoji = "🎯", isCustom = false)
        )
        categoryDao.insertDefaultCategories(defaultCategories)

        // Seed default achievements
        val defaultAchievements = listOf(
            Achievement("focus_15", "Primeiro Foco", "Complete uma sessão de foco de 15 minutos", "🌱", "Foco"),
            Achievement("focus_25", "Pomodoro Clássico", "Complete uma sessão de foco de 25 minutos", "⏱️", "Foco"),
            Achievement("focus_30", "Foco Dedicado", "Complete uma sessão de foco de 30 minutos", "🎯", "Foco"),
            Achievement("focus_45", "Imersão Criativa", "Complete uma sessão de foco de 45 minutos", "🎨", "Foco"),
            Achievement("focus_60", "Hora de Ouro", "Complete uma sessão ininterrupta de 60 minutos", "⭐", "Foco"),
            Achievement("focus_90", "Maratona Produtiva", "Supere 90 minutos de foco intenso", "🚀", "Foco"),
            Achievement("focus_120", "Mestre do Hiperfoco", "Alcance a marca de 120 minutos de foco", "👑", "Foco"),
            Achievement("break_5", "Pausa Sagrada", "Realize uma pausa saudável de 5 minutos", "☕", "Foco"),
            Achievement("break_10", "Respiro Profundo", "Realize uma pausa relaxante de 10 minutos", "🧘", "Foco"),
            Achievement("break_15", "Descanso Revigorante", "Descanse por 15 minutos entre ciclos", "🌿", "Foco"),
            Achievement("task_1", "Primeiro Passo", "Conclua sua primeira tarefa", "✅", "Tarefas"),
            Achievement("task_5", "Produtor Ativo", "Conclua 5 tarefas no aplicativo", "📋", "Tarefas"),
            Achievement("task_10", "Em Chamas", "Conclua 10 tarefas", "⚡", "Tarefas"),
            Achievement("task_20", "Mestre da Execução", "Conclua 20 tarefas com sucesso", "🏆", "Tarefas"),
            Achievement("streak_3", "Chama Inicial", "Mantenha uma sequência de 3 dias em um hábito", "🔥", "Hábitos"),
            Achievement("streak_7", "1 Semana de Check-in", "Mantenha 7 dias seguidos de hábito", "🌟", "Hábitos"),
            Achievement("streak_14", "2 Semanas de Disciplina", "Mantenha 14 dias seguidos de hábito", "💎", "Hábitos"),
            Achievement("streak_21", "3 Semanas de Transformação", "Mantenha 21 dias seguidos de hábito", "🛡️", "Hábitos"),
            Achievement("streak_28", "4 Semanas (1 Mês)", "Mantenha 28 dias seguidos de hábito", "🏰", "Hábitos"),
            Achievement("streak_35", "5 Semanas Inabalável", "Mantenha 35 dias seguidos de hábito", "🏵️", "Hábitos"),
            Achievement("streak_42", "6 Semanas de Mestria", "Mantenha 42 dias seguidos de hábito", "⚔️", "Hábitos"),
            Achievement("streak_49", "7 Semanas Imbatível", "Mantenha 49 dias seguidos de hábito", "🔮", "Hábitos"),
            Achievement("streak_56", "8 Semanas de Excelência", "Mantenha 56 dias seguidos de hábito", "🌌", "Hábitos"),
            Achievement("streak_63", "9 Semanas Lendárias", "Mantenha 63 dias seguidos de hábito", "🪐", "Hábitos"),
            Achievement("note_1", "Caderno Aberto", "Crie sua primeira anotação em Markdown", "📝", "Anotações"),
            Achievement("note_5", "Mente Organizada", "Salve 5 anotações com formatação", "📚", "Anotações")
        )
        achievementDao.insertDefaultAchievements(defaultAchievements)

        // Seed initial task if needed
        val currentTasks = taskDao.getAllTasks().firstOrNull()
        if (currentTasks.isNullOrEmpty()) {
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            taskDao.insertTask(
                Task(
                    title = "Revisar anotações de estudo",
                    category = "Estudos",
                    priority = TaskPriority.ALTA,
                    dueDateText = todayStr
                )
            )
            taskDao.insertTask(
                Task(
                    title = "Planejar metas semanais",
                    category = "Trabalho",
                    priority = TaskPriority.MEDIA,
                    dueDateText = todayStr
                )
            )
        }

        // Seed initial habit if needed
        val currentHabits = habitDao.getAllHabits().firstOrNull()
        if (currentHabits.isNullOrEmpty()) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -1)
            val yesterdayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
            habitDao.insertHabit(
                Habit(
                    title = "Beber 2L de água",
                    category = "Saúde",
                    iconEmoji = "💧",
                    reminderTime = "08:30",
                    frequencyDays = "ALL",
                    currentStreak = 3,
                    bestStreak = 5,
                    lastCompletedDate = yesterdayStr,
                    completedDatesCsv = yesterdayStr
                )
            )
            habitDao.insertHabit(
                Habit(
                    title = "Leitura de 15 minutos",
                    category = "Leitura",
                    iconEmoji = "📖",
                    reminderTime = "21:00",
                    frequencyDays = "ALL",
                    currentStreak = 1,
                    bestStreak = 4
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
                    content = """# Bem-vindo ao Citra!
Seu companheiro de produtividade, estudos e organização.

### Recursos:
- [x] Temporizador Pomodoro Flexível (15 a 120 min de foco, 5 a 25 min de pausa, ciclos repetíveis)
- [x] Suporte a músicas e playlists locais do aparelho
- [x] Lembretes e notificações pontuais para tarefas e hábitos
- [x] Calendário integrado com tarefas e anotações
- [x] Sistema rico de conquistas gamificadas
- [x] Backup e restauração local de dados (.json)""",
                    category = "Estudos",
                    iconEmoji = "📘",
                    associatedDate = todayStr
                )
            )
        }
    }

    private fun getMondayOfWeek(timestamp: Long): String {
        val cal = Calendar.getInstance().apply {
            timeInMillis = timestamp
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        }
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
    }
}
