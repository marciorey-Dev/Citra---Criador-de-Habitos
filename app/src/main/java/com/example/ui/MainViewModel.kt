package com.example.ui

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.LocalMusicPlayer
import com.example.data.backup.BackupManager
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesManager
import com.example.data.local.NotificationSettings
import com.example.data.local.SavedTimerSnapshot
import com.example.data.model.*
import com.example.data.repository.CitraRepository
import com.example.notifications.NotificationHelper
import com.example.ui.theme.ThemeAccent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class PomodoroPhase(val label: String) {
    FOCUS("Foco"),
    BREAK("Pausa")
}

data class FlexiblePomodoroState(
    val focusMinutes: Int = 15,     // default 15 min
    val breakMinutes: Int = 5,      // default 5 min
    val totalCycles: Int = 1,       // default 1x
    val currentCycle: Int = 1,
    val currentPhase: PomodoroPhase = PomodoroPhase.FOCUS,
    val remainingSeconds: Int = 15 * 60,
    val totalPhaseSeconds: Int = 15 * 60,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val selectedCategory: String = "",
    val linkedTask: Task? = null,
    val linkedHabit: Habit? = null,
    val showCycleCompletedDialog: Boolean = false,
    val completedSummary: String = ""
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    val repository = CitraRepository(application, database)
    val musicPlayer = LocalMusicPlayer(application)
    val preferencesManager = PreferencesManager(application)

    // Inspiring citrus quotes
    val citrusQuotes = listOf(
        "Quando a vida te dá laranjas, faça uma sessão de foco produtiva!",
        "Pequenos hábitos diários constroem grandes conquistas.",
        "O foco não é sobre perfeição, é sobre consistência diária.",
        "Cada ciclo completado é uma semente para o seu futuro.",
        "Respire fundo, comece com 15 minutos e conquiste seus objetivos.",
        "Uma fatia de cada vez: seu progresso é real e duradouro."
    )

    fun getRandomQuote(): String = citrusQuotes.random()

    // User Preferences
    val themeAccent: StateFlow<ThemeAccent> = preferencesManager.themeAccent
    val defaultStartTab: StateFlow<String> = preferencesManager.defaultStartTab
    val keepScreenOn: StateFlow<Boolean> = preferencesManager.keepScreenOn
    val notificationSettings: StateFlow<NotificationSettings> = preferencesManager.settings
    val hasSeenTutorial: StateFlow<Boolean> = preferencesManager.hasSeenTutorial

    // Theme Mode: null = system, true = dark, false = light
    private val _isDarkTheme = MutableStateFlow<Boolean?>(null)
    val isDarkTheme: StateFlow<Boolean?> = _isDarkTheme.asStateFlow()

    fun setThemeAccent(accent: ThemeAccent) {
        preferencesManager.setThemeAccent(accent)
    }

    fun setDefaultStartTab(tab: String) {
        preferencesManager.setDefaultStartTab(tab)
    }

    fun setKeepScreenOn(enabled: Boolean) {
        preferencesManager.setKeepScreenOn(enabled)
    }


    fun updateNotificationSettings(newSettings: NotificationSettings) {
        preferencesManager.updateSettings(newSettings)
        viewModelScope.launch {
            _userMessage.emit("Configurações de notificação salvas!")
        }
    }

    fun setTutorialSeen(seen: Boolean = true) {
        preferencesManager.setTutorialSeen(seen)
    }

    fun setThemeMode(dark: Boolean?) {
        _isDarkTheme.value = dark
    }

    fun toggleThemeMode() {
        _isDarkTheme.update { current ->
            when (current) {
                null -> false
                false -> true
                true -> null
            }
        }
    }

    // Database Flows
    val tasks: StateFlow<List<Task>> = repository.allTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val habits: StateFlow<List<Habit>> = repository.allHabits
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notes: StateFlow<List<Note>> = repository.allNotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trashNotes: StateFlow<List<Note>> = repository.trashNotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<Category>> = repository.allCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val focusSessions: StateFlow<List<FocusSession>> = repository.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableWeeks: StateFlow<List<String>> = repository.getAvailableWeeks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val achievements: StateFlow<List<Achievement>> = repository.allAchievements
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unlockedAchievementsCount: StateFlow<Int> = repository.unlockedAchievementsCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Flexible Pomodoro State
    private val _pomodoroState = MutableStateFlow(FlexiblePomodoroState())
    val pomodoroState: StateFlow<FlexiblePomodoroState> = _pomodoroState.asStateFlow()

    // Calendar selected date (YYYY-MM-DD)
    val todayString: String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    private val _selectedCalendarDate = MutableStateFlow(todayString)
    val selectedCalendarDate: StateFlow<String> = _selectedCalendarDate.asStateFlow()

    // Notes category filter
    private val _selectedNoteCategoryFilter = MutableStateFlow("Todas")
    val selectedNoteCategoryFilter: StateFlow<String> = _selectedNoteCategoryFilter.asStateFlow()

    // Feedback channel
    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    private var timerJob: Job? = null

    // Organization sub-tab: 0=Tasks, 1=Habits, 2=Notes
    private val _organizationSubTab = MutableStateFlow(0)
    val organizationSubTab: StateFlow<Int> = _organizationSubTab.asStateFlow()

    // Trigger to immediately start creating note from Home shortcut
    private val _openCreateNoteTrigger = MutableStateFlow(false)
    val openCreateNoteTrigger: StateFlow<Boolean> = _openCreateNoteTrigger.asStateFlow()

    fun setOrganizationSubTab(tab: Int) {
        _organizationSubTab.value = tab
    }

    fun triggerCreateNote() {
        _organizationSubTab.value = 2
        _openCreateNoteTrigger.value = true
    }

    fun resetCreateNoteTrigger() {
        _openCreateNoteTrigger.value = false
    }

    // What's New dialog for v0.7.4
    private val _whatsNewVisible = MutableStateFlow(false)
    val whatsNewVisible: StateFlow<Boolean> = _whatsNewVisible.asStateFlow()

    fun dismissWhatsNew() {
        preferencesManager.setLastSeenWhatsNewVersion("0.7.4")
        _whatsNewVisible.value = false
    }

    // Sync Frequency and Timestamp
    val syncFrequency: StateFlow<String> = preferencesManager.syncFrequency
    fun setSyncFrequency(frequency: String) = preferencesManager.setSyncFrequency(frequency)
    private val _lastSyncTimestamp = MutableStateFlow(preferencesManager.getLastSyncTimestamp())
    val lastSyncTimestamp: StateFlow<Long> = _lastSyncTimestamp.asStateFlow()

    fun updateLastSyncTimestamp(timestamp: Long = System.currentTimeMillis()) {
        preferencesManager.setLastSyncTimestamp(timestamp)
        _lastSyncTimestamp.value = timestamp
    }

    // Flag indicating whether note full screen editor is currently visible
    private val _isNoteEditorOpen = MutableStateFlow(false)
    val isNoteEditorOpen: StateFlow<Boolean> = _isNoteEditorOpen.asStateFlow()

    fun setNoteEditorOpen(open: Boolean) {
        _isNoteEditorOpen.value = open
    }

    init {
        NotificationHelper.initNotificationChannels(application)
        restoreTimerSnapshotOnStartup()
        if (preferencesManager.getLastSeenWhatsNewVersion() != "0.7.4") {
            _whatsNewVisible.value = true
        }
        viewModelScope.launch {
            repository.seedInitialDataIfNeeded()
        }
    }

    fun triggerAutoBackup() {
        viewModelScope.launch {
            com.example.data.backup.AutoDeviceStorageManager.saveFullAppSnapshot(
                context = getApplication(),
                tasks = tasks.value,
                habits = habits.value,
                notes = notes.value,
                focusSessions = focusSessions.value,
                categories = categories.value,
                achievements = achievements.value
            )
        }
    }

    private fun restoreTimerSnapshotOnStartup() {
        val snapshot = preferencesManager.loadTimerSnapshot() ?: return
        try {
            val phase = try {
                PomodoroPhase.valueOf(snapshot.phaseName)
            } catch (_: Exception) {
                PomodoroPhase.FOCUS
            }
            val totalSec = if (phase == PomodoroPhase.FOCUS) snapshot.focusMinutes * 60 else snapshot.breakMinutes * 60

            if (snapshot.isRunning) {
                // Calculate elapsed wall-clock seconds since app was closed/killed
                val elapsedSeconds = ((System.currentTimeMillis() - snapshot.lastTimestamp) / 1000L).toInt().coerceAtLeast(0)
                val remainingSec = snapshot.remainingSeconds - elapsedSeconds

                if (remainingSec > 0) {
                    // Timer is still running! Resume smoothly without pausing!
                    _pomodoroState.update {
                        it.copy(
                            focusMinutes = snapshot.focusMinutes,
                            breakMinutes = snapshot.breakMinutes,
                            totalCycles = snapshot.totalCycles,
                            currentCycle = snapshot.currentCycle,
                            currentPhase = phase,
                            remainingSeconds = remainingSec,
                            totalPhaseSeconds = totalSec,
                            isRunning = true,
                            isPaused = false,
                            selectedCategory = snapshot.selectedCategory
                        )
                    }
                    startPomodoro()
                } else {
                    // Time completed while app was closed!
                    _pomodoroState.update {
                        it.copy(
                            focusMinutes = snapshot.focusMinutes,
                            breakMinutes = snapshot.breakMinutes,
                            totalCycles = snapshot.totalCycles,
                            currentCycle = snapshot.currentCycle,
                            currentPhase = phase,
                            remainingSeconds = 0,
                            totalPhaseSeconds = totalSec,
                            isRunning = true,
                            isPaused = false,
                            selectedCategory = snapshot.selectedCategory
                        )
                    }
                    onPhaseCompleted()
                }
            } else if (snapshot.isPaused) {
                // Was explicitly paused
                _pomodoroState.update {
                    it.copy(
                        focusMinutes = snapshot.focusMinutes,
                        breakMinutes = snapshot.breakMinutes,
                        totalCycles = snapshot.totalCycles,
                        currentCycle = snapshot.currentCycle,
                        currentPhase = phase,
                        remainingSeconds = snapshot.remainingSeconds.coerceAtLeast(0),
                        totalPhaseSeconds = totalSec,
                        isRunning = false,
                        isPaused = true,
                        selectedCategory = snapshot.selectedCategory
                    )
                }
                NotificationHelper.showTimerOngoingNotification(
                    context = getApplication(),
                    remainingSeconds = snapshot.remainingSeconds,
                    phaseLabel = phase.label,
                    isPaused = true
                )
            } else {
                NotificationHelper.cancelTimerNotification(getApplication())
            }
        } catch (_: Exception) {}
    }

    fun persistTimerState() {
        val s = _pomodoroState.value
        preferencesManager.saveTimerSnapshot(
            SavedTimerSnapshot(
                focusMinutes = s.focusMinutes,
                breakMinutes = s.breakMinutes,
                totalCycles = s.totalCycles,
                currentCycle = s.currentCycle,
                remainingSeconds = s.remainingSeconds,
                phaseName = s.currentPhase.name,
                isRunning = s.isRunning,
                isPaused = s.isPaused,
                selectedCategory = s.selectedCategory,
                linkedTaskId = s.linkedTask?.id,
                lastTimestamp = System.currentTimeMillis()
            )
        )
    }

    // Category actions
    fun addCustomCategory(name: String, emoji: String) {
        viewModelScope.launch {
            if (name.isNotBlank()) {
                repository.addCategory(name, emoji.ifBlank { "🎯" })
                _userMessage.emit("Categoria '$name' adicionada!")
            }
        }
    }

    fun deleteCustomCategory(category: Category) {
        viewModelScope.launch {
            repository.deleteCategory(category)
            _userMessage.emit("Categoria removida.")
        }
    }

    // Flexible Pomodoro Actions
    fun setFocusDuration(minutes: Int) {
        val safe = minutes.coerceIn(10, 120)
        _pomodoroState.update { current ->
            if (current.isRunning) current
            else {
                val totalSec = if (current.currentPhase == PomodoroPhase.FOCUS) safe * 60 else current.breakMinutes * 60
                current.copy(
                    focusMinutes = safe,
                    totalPhaseSeconds = totalSec,
                    remainingSeconds = if (current.currentPhase == PomodoroPhase.FOCUS) totalSec else current.remainingSeconds
                )
            }
        }
    }

    fun setBreakDuration(minutes: Int) {
        val safe = minutes.coerceIn(5, 25)
        _pomodoroState.update { current ->
            if (current.isRunning) current
            else {
                val totalSec = if (current.currentPhase == PomodoroPhase.BREAK) safe * 60 else current.focusMinutes * 60
                current.copy(
                    breakMinutes = safe,
                    totalPhaseSeconds = totalSec,
                    remainingSeconds = if (current.currentPhase == PomodoroPhase.BREAK) totalSec else current.remainingSeconds
                )
            }
        }
    }

    fun setTotalCycles(cycles: Int) {
        val safe = cycles.coerceIn(1, 8)
        _pomodoroState.update { it.copy(totalCycles = safe) }
    }

    fun setPomodoroCategory(category: String) {
        _pomodoroState.update { it.copy(selectedCategory = category) }
    }

    fun linkTaskToPomodoro(task: Task?) {
        _pomodoroState.update {
            it.copy(
                linkedTask = task,
                selectedCategory = task?.category ?: it.selectedCategory
            )
        }
    }

    fun linkHabitToPomodoro(habit: Habit?) {
        _pomodoroState.update {
            it.copy(linkedHabit = habit)
        }
    }

    fun toggleStartPausePomodoro() {
        if (_pomodoroState.value.isRunning) {
            pausePomodoro()
        } else {
            startPomodoro()
        }
    }

    fun startPomodoro() {
        vibrate(40)
        _pomodoroState.update { it.copy(isRunning = true, isPaused = false) }
        persistTimerState()

        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_pomodoroState.value.remainingSeconds > 0 && _pomodoroState.value.isRunning) {
                delay(1000L)
                val currentRem = _pomodoroState.value.remainingSeconds - 1
                _pomodoroState.update { it.copy(remainingSeconds = currentRem) }
                persistTimerState()

                // Update ongoing notification
                NotificationHelper.showTimerOngoingNotification(
                    context = getApplication(),
                    remainingSeconds = currentRem,
                    phaseLabel = _pomodoroState.value.currentPhase.label,
                    isPaused = false
                )
            }

            if (_pomodoroState.value.remainingSeconds <= 0 && _pomodoroState.value.isRunning) {
                onPhaseCompleted()
            }
        }
    }

    fun pausePomodoro() {
        vibrate(30)
        timerJob?.cancel()
        timerJob = null
        _pomodoroState.update { it.copy(isRunning = false, isPaused = true) }
        persistTimerState()

        NotificationHelper.showTimerOngoingNotification(
            context = getApplication(),
            remainingSeconds = _pomodoroState.value.remainingSeconds,
            phaseLabel = _pomodoroState.value.currentPhase.label,
            isPaused = true
        )
    }

    fun resetPomodoro() {
        vibrate(40)
        timerJob?.cancel()
        timerJob = null
        val state = _pomodoroState.value
        val sec = state.focusMinutes * 60
        _pomodoroState.update {
            it.copy(
                isRunning = false,
                isPaused = false,
                currentPhase = PomodoroPhase.FOCUS,
                currentCycle = 1,
                remainingSeconds = sec,
                totalPhaseSeconds = sec
            )
        }
        preferencesManager.clearTimerSnapshot()
        NotificationHelper.cancelTimerNotification(getApplication())
        musicPlayer.stop()
    }

    private fun onPhaseCompleted() {
        vibrate(250)
        NotificationHelper.cancelTimerNotification(getApplication())
        val state = _pomodoroState.value

        if (state.currentPhase == PomodoroPhase.FOCUS) {
            // Record focus session
            viewModelScope.launch {
                val session = FocusSession(
                    category = state.selectedCategory,
                    durationMinutes = state.focusMinutes,
                    breakMinutes = state.breakMinutes,
                    cycleCount = state.currentCycle,
                    linkedTaskId = state.linkedTask?.id,
                    linkedTaskTitle = state.linkedTask?.title
                )
                repository.recordFocusSession(session)

                // Synergy: Update linked task focus minutes
                if (state.linkedTask != null) {
                    val updatedTask = state.linkedTask.copy(
                        focusMinutesCompleted = state.linkedTask.focusMinutesCompleted + state.focusMinutes
                    )
                    repository.updateTask(updatedTask)
                }

                // Synergy: Check-in linked habit if attached!
                if (state.linkedHabit != null) {
                    val isAlreadyDone = state.linkedHabit.completedDatesCsv.split(",")
                        .any { it.trim() == todayString }
                    if (!isAlreadyDone) {
                        repository.toggleHabitToday(state.linkedHabit)
                        _userMessage.emit("Hábito '${state.linkedHabit.title}' marcado como feito hoje!")
                    }
                }
            }

            // Cancel running timer job so next phase starts cleanly paused!
            timerJob?.cancel()
            timerJob = null
            musicPlayer.stop()

            val vibEnabled = notificationSettings.value.vibrate
            NotificationHelper.showInstantNotification(
                context = getApplication(),
                id = 101,
                channelId = NotificationHelper.CHANNEL_ALERTS,
                title = "Ciclo de Foco Concluído!",
                message = "Excelente dedicação de ${state.focusMinutes}m! Pausa de ${state.breakMinutes}m pronta (toque para iniciar).",
                customSoundUriString = notificationSettings.value.pomodoroSoundUri,
                vibratePhone = vibEnabled
            )

            // Switch to break phase (cleanly PAUSED)
            val breakSec = state.breakMinutes * 60
            _pomodoroState.update {
                it.copy(
                    isRunning = false,
                    isPaused = true,
                    currentPhase = PomodoroPhase.BREAK,
                    remainingSeconds = breakSec,
                    totalPhaseSeconds = breakSec
                )
            }
            persistTimerState()
        } else {
            // Break phase completed
            timerJob?.cancel()
            timerJob = null
            musicPlayer.stop()

            val nextCycle = state.currentCycle + 1
            if (nextCycle > state.totalCycles) {
                // All cycles in session completed!
                val focusSec = state.focusMinutes * 60
                val randomQuote = getRandomQuote()
                _pomodoroState.update {
                    it.copy(
                        isRunning = false,
                        isPaused = false,
                        currentPhase = PomodoroPhase.FOCUS,
                        currentCycle = 1,
                        remainingSeconds = focusSec,
                        totalPhaseSeconds = focusSec,
                        showCycleCompletedDialog = true,
                        completedSummary = "Você completou todos os ${state.totalCycles} ciclos de foco!\n\n\"$randomQuote\""
                    )
                }
                preferencesManager.clearTimerSnapshot()

                val vibEnabled = notificationSettings.value.vibrate
                NotificationHelper.showInstantNotification(
                    context = getApplication(),
                    id = 102,
                    channelId = NotificationHelper.CHANNEL_ALERTS,
                    title = "🎉 Sessão Completa de Estudos!",
                    message = "Parabéns! Todos os ${state.totalCycles} ciclos foram finalizados com êxito.",
                    customSoundUriString = notificationSettings.value.pomodoroSoundUri,
                    vibratePhone = vibEnabled
                )
            } else {
                // Next cycle focus (cleanly PAUSED, waiting for user to start)
                val focusSec = state.focusMinutes * 60
                _pomodoroState.update {
                    it.copy(
                        isRunning = false,
                        isPaused = true,
                        currentPhase = PomodoroPhase.FOCUS,
                        currentCycle = nextCycle,
                        remainingSeconds = focusSec,
                        totalPhaseSeconds = focusSec
                    )
                }
                persistTimerState()

                NotificationHelper.showInstantNotification(
                    context = getApplication(),
                    id = 103,
                    channelId = NotificationHelper.CHANNEL_ALERTS,
                    title = "Pausa Concluída",
                    message = "Pronto para o Ciclo $nextCycle de ${state.totalCycles} de foco? Toque em Iniciar quando quiser.",
                    customSoundUriString = notificationSettings.value.pomodoroSoundUri
                )
            }
        }
    }

    fun dismissCycleCompletedDialog() {
        _pomodoroState.update { it.copy(showCycleCompletedDialog = false) }
    }

    // Task Actions
    fun addTask(
        title: String,
        category: String,
        priority: TaskPriority,
        dueDateText: String,
        dueTimestamp: Long?,
        reminderMinutesBefore: Int
    ) {
        viewModelScope.launch {
            repository.insertTask(
                Task(
                    title = title,
                    category = category.ifBlank { "Estudos" },
                    priority = priority,
                    dueDateText = dueDateText.ifBlank { todayString },
                    dueDateTimestamp = dueTimestamp,
                    reminderMinutesBefore = reminderMinutesBefore
                )
            )
            _userMessage.emit("Tarefa criada com sucesso!")
        }
    }

    fun updateTask(task: Task) {
        viewModelScope.launch {
            repository.updateTask(task)
            _userMessage.emit("Tarefa atualizada!")
        }
    }

    fun toggleTask(task: Task) {
        viewModelScope.launch {
            vibrate(35)
            val isDone = repository.toggleTaskCompleted(task)
            if (isDone) {
                _userMessage.emit("Tarefa '${task.title}' concluída!")
            }
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            repository.deleteTask(task)
            if (_pomodoroState.value.linkedTask?.id == task.id) {
                _pomodoroState.update { it.copy(linkedTask = null) }
            }
            _userMessage.emit("Tarefa excluída com sucesso.")
        }
    }

    // Habit Actions
    fun addHabit(
        title: String,
        category: String,
        iconEmoji: String,
        reminderTime: String,
        reminderLeadMinutes: Int,
        frequencyDays: String
    ) {
        viewModelScope.launch {
            repository.insertHabit(
                Habit(
                    title = title,
                    category = category.ifBlank { "Saúde" },
                    iconEmoji = iconEmoji.ifBlank { "✨" },
                    reminderTime = reminderTime.ifBlank { "08:00" },
                    reminderLeadMinutes = reminderLeadMinutes,
                    frequencyDays = frequencyDays.ifBlank { "ALL" }
                )
            )
            _userMessage.emit("Hábito '$title' adicionado (lembrete às $reminderTime)!")
        }
    }

    fun updateHabit(habit: Habit) {
        viewModelScope.launch {
            repository.updateHabit(habit)
            _userMessage.emit("Hábito atualizado!")
        }
    }

    fun toggleHabitToday(habit: Habit) {
        viewModelScope.launch {
            vibrate(40)
            val isDone = repository.toggleHabitToday(habit)
            if (isDone) {
                _userMessage.emit("Hábito cumprido hoje! Sequência de ${habit.currentStreak + 1} dias!")
            } else {
                _userMessage.emit("Hábito desmarcado para hoje.")
            }
        }
    }

    fun deleteHabit(habit: Habit) {
        viewModelScope.launch {
            repository.deleteHabit(habit)
            if (_pomodoroState.value.linkedHabit?.id == habit.id) {
                _pomodoroState.update { it.copy(linkedHabit = null) }
            }
            _userMessage.emit("Hábito removido com sucesso.")
        }
    }

    // Notes Actions
    fun setSelectedNoteCategoryFilter(category: String) {
        _selectedNoteCategoryFilter.value = category
    }

    fun togglePinNote(note: Note) {
        viewModelScope.launch {
            val updated = note.copy(isPinned = !note.isPinned, updatedAt = System.currentTimeMillis())
            repository.updateNote(updated)
            _userMessage.emit(if (updated.isPinned) "Nota fixada no topo!" else "Nota desafixada.")
        }
    }

    fun saveNote(
        id: Long,
        title: String,
        content: String,
        category: String,
        iconEmoji: String = "📝",
        associatedDate: String? = null,
        isPinned: Boolean = false,
        backgroundImageUri: String? = null,
        backgroundBlur: Float = 0.5f,
        backgroundBrightness: Float = 0.5f
    ) {
        viewModelScope.launch {
            val note = Note(
                id = id,
                title = title.ifBlank { "Sem título" },
                content = content,
                category = category.ifBlank { "Geral" },
                iconEmoji = iconEmoji.ifBlank { "📝" },
                associatedDate = associatedDate,
                isPinned = isPinned,
                backgroundImageUri = backgroundImageUri,
                backgroundBlur = backgroundBlur,
                backgroundBrightness = backgroundBrightness,
                isDeleted = false,
                updatedAt = System.currentTimeMillis()
            )
            if (id == 0L) {
                repository.insertNote(note)
                _userMessage.emit("Anotação salva!")
            } else {
                repository.updateNote(note)
                _userMessage.emit("Anotação atualizada!")
            }
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            repository.moveToTrash(note)
            _userMessage.emit("Anotação movida para a Lixeira (30 dias).")
        }
    }

    fun moveNoteToTrash(note: Note) = deleteNote(note)

    fun restoreNoteFromTrash(note: Note) {
        viewModelScope.launch {
            repository.restoreFromTrash(note)
            _userMessage.emit("Anotação restaurada com sucesso!")
        }
    }

    fun permanentlyDeleteNote(note: Note) {
        viewModelScope.launch {
            repository.permanentlyDeleteNote(note)
            _userMessage.emit("Anotação excluída definitivamente.")
        }
    }

    // Calendar Action
    fun selectCalendarDate(dateStr: String) {
        _selectedCalendarDate.value = dateStr
    }

    // Backup & Restore
    fun exportBackupJson(): String {
        return BackupManager.createBackupJson(
            tasks = tasks.value,
            habits = habits.value,
            focusSessions = focusSessions.value,
            categories = categories.value,
            achievements = achievements.value
        )
    }

    fun restoreBackupJson(jsonString: String) {
        viewModelScope.launch {
            try {
                val parsed = BackupManager.parseBackupJson(jsonString)
                repository.restoreBackup(
                    tasks = parsed.tasks,
                    habits = parsed.habits,
                    sessions = parsed.focusSessions,
                    categories = parsed.categories
                )
                _userMessage.emit("Backup local restaurado com sucesso!")
            } catch (e: Exception) {
                _userMessage.emit("Erro ao restaurar backup: arquivo inválido.")
            }
        }
    }

    fun exportNotesJson(): String {
        return BackupManager.exportNotesJson(notes.value)
    }

    fun importNotesJson(jsonString: String) {
        viewModelScope.launch {
            try {
                val parsed = BackupManager.parseNotesJson(jsonString)
                val count = repository.insertNotes(parsed)
                _userMessage.emit("$count anotações importadas com sucesso!")
            } catch (e: Exception) {
                _userMessage.emit("Erro ao importar anotações.")
            }
        }
    }

    // Haptics helper
    private fun vibrate(durationMs: Long) {
        try {
            val context = getApplication<Application>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        } catch (_: Exception) {}
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        musicPlayer.release()
    }
}
