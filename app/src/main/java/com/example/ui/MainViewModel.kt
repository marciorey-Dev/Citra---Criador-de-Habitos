package com.example.ui

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import java.io.File
import com.example.audio.LocalMusicPlayer
import com.example.data.backup.BackupManager
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesManager
import com.example.data.local.NotificationSettings
import com.example.data.local.SavedTimerSnapshot
import com.example.data.model.*
import com.example.data.repository.CitraRepository
import com.example.notifications.NextAlarmScheduler
import com.example.notifications.NotificationHelper
import com.example.ui.theme.ThemeAccent
import com.example.ui.tutorial.ActiveTutorialState
import com.example.ui.tutorial.CitraTutorialArea
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
    val isStopwatchMode: Boolean = false,
    val stopwatchSeconds: Int = 0,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val selectedCategory: String = "",
    val linkedTask: Task? = null,
    val linkedHabit: Habit? = null,
    val showCycleCompletedDialog: Boolean = false,
    val completedSummary: String = ""
)

sealed class BatchExportUiState {
    data object Idle : BatchExportUiState()
    data class Exporting(
        val exported: Int,
        val total: Int,
        val currentTitle: String,
        val format: com.example.util.BatchExportFormat
    ) : BatchExportUiState()
    data class Completed(
        val output: com.example.util.NoteExportHelper.BatchExportOutput,
        val format: com.example.util.BatchExportFormat
    ) : BatchExportUiState()
    data class Error(val message: String) : BatchExportUiState()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    val repository = CitraRepository(application, database)
    val musicPlayer = LocalMusicPlayer(application)
    val preferencesManager = PreferencesManager(application)

    // Local Offline Profile & First-Launch Onboarding States
    val hasCompletedOnboarding: StateFlow<Boolean> = preferencesManager.hasCompletedOnboarding
    val localDisplayName: StateFlow<String> = preferencesManager.userDisplayName
    val localPhotoPath: StateFlow<String?> = preferencesManager.userPhotoPath

    fun setLocalDisplayName(name: String) {
        preferencesManager.setUserDisplayName(name)
    }

    fun setLocalPhotoPath(path: String?) {
        preferencesManager.setUserPhotoPath(path)
    }

    fun completeOnboarding(displayName: String, photoPath: String?) {
        if (displayName.isNotBlank()) {
            preferencesManager.setUserDisplayName(displayName)
        }
        if (!photoPath.isNullOrBlank()) {
            preferencesManager.setUserPhotoPath(photoPath)
        }
        preferencesManager.setOnboardingCompleted(true)
    }

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
    val dailyRandomColor: StateFlow<Boolean> = preferencesManager.dailyRandomColor
    val defaultStartTab: StateFlow<String> = preferencesManager.defaultStartTab
    val keepScreenOn: StateFlow<Boolean> = preferencesManager.keepScreenOn
    val notificationSettings: StateFlow<NotificationSettings> = preferencesManager.settings
    val hasSeenTutorial: StateFlow<Boolean> = preferencesManager.hasSeenTutorial
    val timerContinuousMode: StateFlow<Boolean> = preferencesManager.timerContinuousMode
    val timerTapToToggle: StateFlow<Boolean> = preferencesManager.timerTapToToggle
    val timerAllowSwipe: StateFlow<Boolean> = preferencesManager.timerAllowSwipe
    val habitRewardPoints: StateFlow<Int> = preferencesManager.habitRewardPoints
    val notesSortOption: StateFlow<String> = preferencesManager.notesSortOption
    val notesSortAscending: StateFlow<Boolean> = preferencesManager.notesSortAscending
    val notesLayoutMode: StateFlow<String> = preferencesManager.notesLayoutMode

    fun setTimerContinuousMode(enabled: Boolean) {
        preferencesManager.setTimerContinuousMode(enabled)
    }

    fun setTimerTapToToggle(enabled: Boolean) {
        preferencesManager.setTimerTapToToggle(enabled)
    }

    fun setTimerAllowSwipe(enabled: Boolean) {
        preferencesManager.setTimerAllowSwipe(enabled)
    }

    fun setNotesSortOption(optionName: String) {
        preferencesManager.setNotesSortOption(optionName)
    }

    fun setNotesSortAscending(ascending: Boolean) {
        preferencesManager.setNotesSortAscending(ascending)
    }

    fun toggleNotesSortAscending() {
        preferencesManager.setNotesSortAscending(!preferencesManager.notesSortAscending.value)
    }

    fun setNotesLayoutMode(modeName: String) {
        preferencesManager.setNotesLayoutMode(modeName)
    }

    // Persistent Theme Mode: null = system, true = dark, false = light
    val isDarkTheme: StateFlow<Boolean?> = preferencesManager.themeMode

    fun setThemeAccent(accent: ThemeAccent) {
        preferencesManager.setThemeAccent(accent)
    }

    fun setDailyRandomColor(enabled: Boolean) {
        preferencesManager.setDailyRandomColor(enabled)
    }

    fun setDefaultStartTab(tab: String) {
        preferencesManager.setDefaultStartTab(tab)
    }

    fun setKeepScreenOn(enabled: Boolean) {
        preferencesManager.setKeepScreenOn(enabled)
    }

    val welcomePhrasesEnabled: StateFlow<Boolean> = preferencesManager.welcomePhrasesEnabled
    val disabledWelcomeCategories: StateFlow<Set<String>> = preferencesManager.disabledWelcomeCategories
    val dailyWelcomePhrase: StateFlow<String> = preferencesManager.dailyWelcomePhrase

    fun setWelcomePhrasesEnabled(enabled: Boolean) {
        preferencesManager.setWelcomePhrasesEnabled(enabled)
    }

    fun toggleWelcomeCategory(categoryId: String) {
        preferencesManager.toggleWelcomeCategory(categoryId)
    }


    fun updateNotificationSettings(newSettings: NotificationSettings) {
        preferencesManager.updateSettings(newSettings)
        NextAlarmScheduler.scheduleNextAlarm(getApplication())
        viewModelScope.launch {
            _userMessage.emit("Configurações de notificação salvas!")
        }
    }

    fun setTutorialSeen(seen: Boolean = true) {
        preferencesManager.setTutorialSeen(seen)
    }

    val allTutorialsSkipped: StateFlow<Boolean> = preferencesManager.allTutorialsSkipped
    val completedTutorialAreas: StateFlow<Set<String>> = preferencesManager.completedTutorialAreas

    private val _activeTutorial = MutableStateFlow<ActiveTutorialState?>(null)
    val activeTutorial: StateFlow<ActiveTutorialState?> = _activeTutorial.asStateFlow()

    fun startTutorial(area: CitraTutorialArea) {
        if (!preferencesManager.isTutorialAreaCompleted(area.id) && _activeTutorial.value == null) {
            _activeTutorial.value = ActiveTutorialState(area = area, stepIndex = 0)
        }
    }

    fun nextTutorialStep() {
        val current = _activeTutorial.value ?: return
        if (current.isLastStep) {
            preferencesManager.markTutorialAreaCompleted(current.area.id)
            _activeTutorial.value = null
        } else {
            _activeTutorial.value = current.copy(stepIndex = current.stepIndex + 1)
        }
    }

    fun skipCurrentTutorialArea() {
        val current = _activeTutorial.value ?: return
        preferencesManager.markTutorialAreaCompleted(current.area.id)
        _activeTutorial.value = null
    }

    fun skipAllTutorials() {
        preferencesManager.skipAllTutorials()
        _activeTutorial.value = null
        viewModelScope.launch {
            _userMessage.emit("Tutoriais desativados.")
        }
    }

    fun resetAllTutorials() {
        preferencesManager.resetAllTutorials()
        _activeTutorial.value = null
        viewModelScope.launch {
            _userMessage.emit("Tutoriais redefinidos! Serão exibidos ao navegar pelas áreas.")
        }
    }

    fun setThemeMode(dark: Boolean?) {
        preferencesManager.setThemeMode(dark)
    }

    fun toggleThemeMode() {
        val current = isDarkTheme.value
        val next = when (current) {
            null -> false
            false -> true
            true -> null
        }
        preferencesManager.setThemeMode(next)
    }

    val appBgUri: StateFlow<String?> = preferencesManager.appBgUri
    val appBgBlur: StateFlow<Float> = preferencesManager.appBgBlur
    val appBgBrightness: StateFlow<Float> = preferencesManager.appBgBrightness

    fun setAppBackground(uri: String?, blur: Float, brightness: Float) {
        preferencesManager.setAppBackground(uri, blur, brightness)
    }

    fun clearAppBackground() {
        preferencesManager.clearAppBackground()
    }

    val citraTimerBallsJson: StateFlow<String> = preferencesManager.citraTimerBallsJson

    fun saveTimerCitraBallsJson(json: String) {
        preferencesManager.saveCitraTimerBallsJson(json)
    }

    fun clearTimerCitraBalls() {
        preferencesManager.clearCitraTimerBalls()
    }

    private fun spawnTimerCitraBall(durationMinutes: Int, sessionKeyMillis: Long = 0L) {
        preferencesManager.addCitraTimerBall(durationMinutes, sessionKeyMillis)
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

    // Flexible Pomodoro State (remembers last used session size)
    private val _pomodoroState = MutableStateFlow(
        FlexiblePomodoroState(
            focusMinutes = preferencesManager.lastUsedFocusMinutes.value,
            remainingSeconds = preferencesManager.lastUsedFocusMinutes.value * 60,
            totalPhaseSeconds = preferencesManager.lastUsedFocusMinutes.value * 60
        )
    )
    val pomodoroState: StateFlow<FlexiblePomodoroState> = _pomodoroState.asStateFlow()

    // Archived notes & preferences
    val archivedNotes: StateFlow<List<Note>> = repository.allArchivedNotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentExternalDocs: StateFlow<List<com.example.data.local.RecentExternalDocEntry>> =
        preferencesManager.recentExternalDocs

    val defaultNoteOpenMode: StateFlow<String> = preferencesManager.defaultNoteOpenMode
    fun setDefaultNoteOpenMode(mode: String) = preferencesManager.setDefaultNoteOpenMode(mode)

    fun setNoteArchived(note: Note, isArchived: Boolean) {
        viewModelScope.launch {
            repository.setNoteArchived(note, isArchived)
            val msg = when (note.resolvedDocumentType) {
                NoteDocumentType.PDF -> if (isArchived) "PDF arquivado com sucesso" else "PDF restaurado para as notas"
                NoteDocumentType.DOCX -> if (isArchived) "Word (.docx) arquivado com sucesso" else "Word (.docx) restaurado para as notas"
                NoteDocumentType.MARKDOWN -> if (isArchived) "Nota arquivada com sucesso" else "Nota restaurada para as notas principais"
            }
            _userMessage.emit(msg)
        }
    }

    fun moveNotesToTrash(targetNotes: List<Note>) {
        if (targetNotes.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            targetNotes.forEach { note ->
                repository.moveToTrash(note)
            }
            com.example.widget.CitraWidgetHelper.updateAllWidgets(getApplication())
            _userMessage.emit("${targetNotes.size} ${if (targetNotes.size == 1) "item movido" else "itens movidos"} para a Lixeira.")
        }
    }

    fun setNotesArchived(targetNotes: List<Note>, isArchived: Boolean) {
        if (targetNotes.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            targetNotes.forEach { note ->
                repository.setNoteArchived(note, isArchived)
            }
            com.example.widget.CitraWidgetHelper.updateAllWidgets(getApplication())
            val msg = if (isArchived) {
                "${targetNotes.size} ${if (targetNotes.size == 1) "item arquivado" else "itens arquivados"}."
            } else {
                "${targetNotes.size} ${if (targetNotes.size == 1) "item desarquivado" else "itens desarquivados"}."
            }
            _userMessage.emit(msg)
        }
    }

    fun updateNotesColor(targetNotes: List<Note>, noteColor: String?) {
        if (targetNotes.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            targetNotes.forEach { note ->
                repository.updateNote(note.copy(noteColor = noteColor, updatedAt = now))
            }
            com.example.widget.CitraWidgetHelper.updateAllWidgets(getApplication())
            _userMessage.emit("Cor atualizada em ${targetNotes.size} ${if (targetNotes.size == 1) "item" else "itens"}.")
        }
    }

    fun updateNotesCategory(targetNotes: List<Note>, newCategory: String) {
        if (targetNotes.isEmpty()) return
        val cleanCategory = newCategory.trim().ifBlank { "Geral" }
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            targetNotes.forEach { note ->
                repository.updateNote(note.copy(category = cleanCategory, updatedAt = now))
            }
            com.example.widget.CitraWidgetHelper.updateAllWidgets(getApplication())
            _userMessage.emit("Categoria '$cleanCategory' aplicada a ${targetNotes.size} ${if (targetNotes.size == 1) "item" else "itens"}.")
        }
    }

    private val _batchExportState = MutableStateFlow<BatchExportUiState>(BatchExportUiState.Idle)
    val batchExportState: StateFlow<BatchExportUiState> = _batchExportState.asStateFlow()
    private var batchExportJob: Job? = null
    @Volatile
    private var batchExportCancelled: Boolean = false

    fun startBatchExport(
        context: Context,
        targetNotes: List<Note>,
        format: com.example.util.BatchExportFormat,
        themeColor: Int
    ) {
        if (targetNotes.isEmpty()) return
        batchExportJob?.cancel()
        batchExportCancelled = false
        val total = targetNotes.size
        _batchExportState.value = BatchExportUiState.Exporting(
            exported = 0,
            total = total,
            currentTitle = targetNotes.first().title.ifBlank { "Preparando..." },
            format = format
        )
        batchExportJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val output = com.example.util.NoteExportHelper.exportNotesBatch(
                    context = context,
                    notes = targetNotes,
                    format = format,
                    themeColor = themeColor,
                    onProgress = { exported, expTotal, currentTitle ->
                        if (!batchExportCancelled) {
                            _batchExportState.value = BatchExportUiState.Exporting(
                                exported = exported,
                                total = expTotal,
                                currentTitle = currentTitle,
                                format = format
                            )
                        }
                    },
                    isCancelled = { batchExportCancelled }
                )
                if (!batchExportCancelled) {
                    _batchExportState.value = BatchExportUiState.Completed(output = output, format = format)
                } else {
                    _batchExportState.value = BatchExportUiState.Idle
                }
            } catch (e: Exception) {
                if (batchExportCancelled || e is kotlinx.coroutines.CancellationException) {
                    _batchExportState.value = BatchExportUiState.Idle
                } else {
                    _batchExportState.value = BatchExportUiState.Error(
                        e.message ?: "Erro inesperado ao exportar as notas selecionadas."
                    )
                }
            }
        }
    }

    fun cancelBatchExport() {
        batchExportCancelled = true
        batchExportJob?.cancel()
        batchExportJob = null
        _batchExportState.value = BatchExportUiState.Idle
        viewModelScope.launch {
            _userMessage.emit("Exportação cancelada.")
        }
    }

    fun clearBatchExportState() {
        _batchExportState.value = BatchExportUiState.Idle
    }

    fun ensureCategoryForDraft(): String {
        val existing = categories.value.find { it.name.equals("Rascunhos", ignoreCase = true) }
        if (existing != null) return existing.name
        val fallback = categories.value.firstOrNull()?.name ?: "Rascunhos"
        viewModelScope.launch {
            repository.addCategory("Rascunhos", "📝")
        }
        return fallback
    }

    // Pending note to open from Finances creation
    private val _pendingOpenNoteFromFinances = MutableStateFlow<Note?>(null)
    val pendingOpenNoteFromFinances: StateFlow<Note?> = _pendingOpenNoteFromFinances.asStateFlow()
    fun clearPendingOpenNoteFromFinances() { _pendingOpenNoteFromFinances.value = null }

    // Calendar selected date (YYYY-MM-DD)
    val todayString: String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    private val _selectedCalendarDate = MutableStateFlow(todayString)
    val selectedCalendarDate: StateFlow<String> = _selectedCalendarDate.asStateFlow()

    // Notes category & document type filters
    private val _selectedNoteCategoryFilter = MutableStateFlow("Todas")
    val selectedNoteCategoryFilter: StateFlow<String> = _selectedNoteCategoryFilter.asStateFlow()

    private val _selectedNoteDocumentFilter = MutableStateFlow(
        com.example.ui.screens.organization.notes.NoteDocumentFilter.ALL
    )
    val selectedNoteDocumentFilter: StateFlow<com.example.ui.screens.organization.notes.NoteDocumentFilter> =
        _selectedNoteDocumentFilter.asStateFlow()

    fun setSelectedNoteDocumentFilter(filter: com.example.ui.screens.organization.notes.NoteDocumentFilter) {
        _selectedNoteDocumentFilter.value = filter
    }

    // Feedback channel
    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    private var timerJob: Job? = null
    private var timerTargetEndTimeMillis: Long = 0L
    private var stopwatchStartTimeMillis: Long = 0L

    // Organization sub-tab: 0=Tasks, 1=Habits, 2=Notes
    private val _organizationSubTab = MutableStateFlow(0)
    val organizationSubTab: StateFlow<Int> = _organizationSubTab.asStateFlow()

    // Hoisted Notes search query (shared between TopAppBar and NotesSubTab)
    private val _notesSearchQuery = MutableStateFlow("")
    val notesSearchQuery: StateFlow<String> = _notesSearchQuery.asStateFlow()

    fun setNotesSearchQuery(query: String) {
        _notesSearchQuery.value = query
    }

    // Trigger to immediately start creating note from Home shortcut
    private val _openCreateNoteTrigger = MutableStateFlow(false)
    val openCreateNoteTrigger: StateFlow<Boolean> = _openCreateNoteTrigger.asStateFlow()

    // Triggers for Trash and Archived dialogs from top 3-dots menu
    private val _openNotesTrashTrigger = MutableStateFlow(false)
    val openNotesTrashTrigger: StateFlow<Boolean> = _openNotesTrashTrigger.asStateFlow()

    private val _openArchivedNotesTrigger = MutableStateFlow(false)
    val openArchivedNotesTrigger: StateFlow<Boolean> = _openArchivedNotesTrigger.asStateFlow()

    fun setOrganizationSubTab(tab: Int) {
        _organizationSubTab.value = tab
    }

    fun triggerCreateNote() {
        _openCreateNoteTrigger.value = true
    }

    fun resetCreateNoteTrigger() {
        _openCreateNoteTrigger.value = false
    }

    fun triggerOpenNotesTrash() {
        _openNotesTrashTrigger.value = true
    }

    fun resetOpenNotesTrashTrigger() {
        _openNotesTrashTrigger.value = false
    }

    fun triggerOpenArchivedNotes() {
        _openArchivedNotesTrigger.value = true
    }

    fun resetOpenArchivedNotesTrigger() {
        _openArchivedNotesTrigger.value = false
    }

    // What's New dialog for v0.7.11
    private val _whatsNewVisible = MutableStateFlow(false)
    val whatsNewVisible: StateFlow<Boolean> = _whatsNewVisible.asStateFlow()

    fun dismissWhatsNew() {
        preferencesManager.setLastSeenWhatsNewVersion("0.7.11")
        _whatsNewVisible.value = false
    }

    fun getLastNoteEditorMode(): String = preferencesManager.getLastNoteEditorMode()
    fun setLastNoteEditorMode(mode: String) = preferencesManager.setLastNoteEditorMode(mode)

    // Modo Sem Perturbações (Distraction-Free Focus Mode)
    val focusDistractionFreeEnabled: StateFlow<Boolean> = preferencesManager.focusDistractionFreeEnabled
    val focusAllowedPackages: StateFlow<Set<String>> = preferencesManager.focusAllowedPackages

    fun setFocusDistractionFreeEnabled(enabled: Boolean) {
        preferencesManager.setFocusDistractionFreeEnabled(enabled)
        if (!enabled) {
            try {
                com.example.service.FocusDistractionService.stop(getApplication())
            } catch (_: Exception) {}
        } else if (_pomodoroState.value.isRunning && _pomodoroState.value.currentPhase == PomodoroPhase.FOCUS) {
            try {
                com.example.service.FocusDistractionService.start(getApplication())
            } catch (_: Exception) {}
        }
    }

    // Flag indicating whether note full screen editor is currently visible
    private val _isNoteEditorOpen = MutableStateFlow(false)
    val isNoteEditorOpen: StateFlow<Boolean> = _isNoteEditorOpen.asStateFlow()

    fun setNoteEditorOpen(open: Boolean) {
        _isNoteEditorOpen.value = open
    }

    private val _noteToOpen = MutableStateFlow<Note?>(null)
    val noteToOpen: StateFlow<Note?> = _noteToOpen.asStateFlow()

    fun openNote(note: Note) {
        _noteToOpen.value = note
    }

    fun clearNoteToOpen() {
        _noteToOpen.value = null
    }

    init {
        preferencesManager.checkDailyRandomColor()
        NotificationHelper.initNotificationChannels(application)
        restoreTimerSnapshotOnStartup()
        NextAlarmScheduler.scheduleNextAlarm(application)
        if (preferencesManager.getLastSeenWhatsNewVersion() != "0.7.11") {
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

    fun restoreTimerSnapshotOnStartup() {
        preferencesManager.refreshCitraTimerBalls()
        val snapshot = preferencesManager.loadTimerSnapshot() ?: return
        try {
            val phase = try {
                PomodoroPhase.valueOf(snapshot.phaseName)
            } catch (_: Exception) {
                PomodoroPhase.FOCUS
            }
            val totalSec = if (phase == PomodoroPhase.FOCUS) snapshot.focusMinutes * 60 else snapshot.breakMinutes * 60

            if (snapshot.isStopwatchMode) {
                if (snapshot.isRunning) {
                    val startTs = if (snapshot.stopwatchStartTimeMillis > 0L) {
                        snapshot.stopwatchStartTimeMillis
                    } else {
                        System.currentTimeMillis() - (snapshot.stopwatchAccumulatedSeconds * 1000L)
                    }
                    if (timerJob?.isActive == true && _pomodoroState.value.isRunning && _pomodoroState.value.isStopwatchMode && stopwatchStartTimeMillis == startTs) {
                        return
                    }
                    stopwatchStartTimeMillis = startTs
                    val elapsed = ((System.currentTimeMillis() - startTs) / 1000L).toInt().coerceAtLeast(0)
                    _pomodoroState.update {
                        it.copy(
                            isStopwatchMode = true,
                            stopwatchSeconds = elapsed,
                            isRunning = true,
                            isPaused = false,
                            selectedCategory = snapshot.selectedCategory
                        )
                    }
                    startPomodoro()
                } else if (snapshot.isPaused) {
                    _pomodoroState.update {
                        it.copy(
                            isStopwatchMode = true,
                            stopwatchSeconds = snapshot.stopwatchAccumulatedSeconds,
                            isRunning = false,
                            isPaused = true,
                            selectedCategory = snapshot.selectedCategory
                        )
                    }
                }
                return
            }

            if (snapshot.isRunning) {
                val targetEnd = if (snapshot.targetEndTimeMillis > 0L) {
                    snapshot.targetEndTimeMillis
                } else {
                    snapshot.lastTimestamp + (snapshot.remainingSeconds * 1000L)
                }
                val now = System.currentTimeMillis()
                val remainingSec = ((targetEnd - now + 999L) / 1000L).toInt()

                if (timerJob?.isActive == true && _pomodoroState.value.isRunning && timerTargetEndTimeMillis == targetEnd && remainingSec > 0) {
                    return
                }
                timerTargetEndTimeMillis = targetEnd

                if (remainingSec > 0) {
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
            } else if (phase == PomodoroPhase.BREAK && snapshot.remainingSeconds > 0) {
                _pomodoroState.update {
                    it.copy(
                        focusMinutes = snapshot.focusMinutes,
                        breakMinutes = snapshot.breakMinutes,
                        totalCycles = snapshot.totalCycles,
                        currentCycle = snapshot.currentCycle,
                        currentPhase = PomodoroPhase.BREAK,
                        remainingSeconds = snapshot.remainingSeconds,
                        totalPhaseSeconds = snapshot.breakMinutes * 60,
                        isRunning = false,
                        isPaused = false,
                        selectedCategory = snapshot.selectedCategory
                    )
                }
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
                lastTimestamp = System.currentTimeMillis(),
                targetEndTimeMillis = timerTargetEndTimeMillis,
                isStopwatchMode = s.isStopwatchMode,
                stopwatchStartTimeMillis = stopwatchStartTimeMillis,
                stopwatchAccumulatedSeconds = s.stopwatchSeconds
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
        val safe = minutes.coerceIn(1, 180)
        preferencesManager.saveLastUsedFocusMinutes(safe)
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

    fun startBreakCountdown() {
        val current = _pomodoroState.value
        if (current.currentPhase != PomodoroPhase.BREAK) return
        startPomodoro()
    }

    fun skipToNextSession() {
        val current = _pomodoroState.value
        timerJob?.cancel()
        timerJob = null
        musicPlayer.stop()
        val nextCycle = (current.currentCycle + 1).coerceAtMost(current.totalCycles)
        val focusSec = current.focusMinutes * 60
        _pomodoroState.update {
            it.copy(
                isRunning = false,
                isPaused = false,
                currentCycle = nextCycle,
                currentPhase = PomodoroPhase.FOCUS,
                remainingSeconds = focusSec,
                totalPhaseSeconds = focusSec
            )
        }
        startPomodoro()
    }

    fun stopCycle() {
        resetPomodoro()
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

    fun toggleStopwatchMode() {
        val current = _pomodoroState.value
        if (current.isRunning || current.isPaused) return
        vibrate(25)
        val nextMode = !current.isStopwatchMode
        val sec = current.focusMinutes * 60
        _pomodoroState.update {
            it.copy(
                isStopwatchMode = nextMode,
                stopwatchSeconds = 0,
                currentPhase = PomodoroPhase.FOCUS,
                remainingSeconds = sec,
                totalPhaseSeconds = sec
            )
        }
    }

    fun skipOptionalBreak() {
        val current = _pomodoroState.value
        if (current.currentPhase != PomodoroPhase.BREAK) return
        timerJob?.cancel()
        timerJob = null
        val focusSec = current.focusMinutes * 60
        _pomodoroState.update {
            it.copy(
                isRunning = false,
                isPaused = false,
                currentPhase = PomodoroPhase.FOCUS,
                remainingSeconds = focusSec,
                totalPhaseSeconds = focusSec
            )
        }
        persistTimerState()
    }

    fun startPomodoro() {
        vibrate(40)
        _pomodoroState.update { it.copy(isRunning = true, isPaused = false) }

        if (preferencesManager.focusDistractionFreeEnabled.value && _pomodoroState.value.currentPhase == PomodoroPhase.FOCUS) {
            try {
                com.example.service.FocusDistractionService.start(getApplication())
            } catch (_: Exception) {}
        }

        if (_pomodoroState.value.isStopwatchMode) {
            stopwatchStartTimeMillis = System.currentTimeMillis() - (_pomodoroState.value.stopwatchSeconds * 1000L)
            persistTimerState()

            timerJob?.cancel()
            timerJob = viewModelScope.launch {
                while (_pomodoroState.value.isRunning && _pomodoroState.value.isStopwatchMode) {
                    val now = System.currentTimeMillis()
                    val elapsed = ((now - stopwatchStartTimeMillis) / 1000L).toInt().coerceAtLeast(0)
                    _pomodoroState.update { it.copy(stopwatchSeconds = elapsed) }
                    persistTimerState()

                    NotificationHelper.showTimerOngoingNotification(
                        context = getApplication(),
                        remainingSeconds = elapsed,
                        phaseLabel = "Cronômetro",
                        isPaused = false
                    )
                    delay(500L)
                }
            }
        } else {
            val now = System.currentTimeMillis()
            timerTargetEndTimeMillis = now + (_pomodoroState.value.remainingSeconds * 1000L)
            persistTimerState()

            // Schedule system AlarmClock that pierces Doze mode and lockscreen
            NotificationHelper.scheduleTimerEndAlarm(
                context = getApplication(),
                triggerTimeMillis = timerTargetEndTimeMillis,
                phaseLabel = _pomodoroState.value.currentPhase.label
            )

            timerJob?.cancel()
            timerJob = viewModelScope.launch {
                while (_pomodoroState.value.isRunning && !_pomodoroState.value.isStopwatchMode) {
                    val currentTime = System.currentTimeMillis()
                    val rem = ((timerTargetEndTimeMillis - currentTime + 999L) / 1000L).toInt().coerceAtLeast(0)
                    _pomodoroState.update { it.copy(remainingSeconds = rem) }
                    persistTimerState()

                    NotificationHelper.showTimerOngoingNotification(
                        context = getApplication(),
                        remainingSeconds = rem,
                        phaseLabel = _pomodoroState.value.currentPhase.label,
                        isPaused = false
                    )

                    if (rem <= 0) {
                        onPhaseCompleted()
                        break
                    }
                    delay(500L)
                }
            }
        }
    }

    fun pausePomodoro() {
        vibrate(30)
        timerJob?.cancel()
        timerJob = null
        NotificationHelper.cancelTimerEndAlarm(getApplication())
        try {
            com.example.service.FocusDistractionService.stop(getApplication())
        } catch (_: Exception) {}

        val now = System.currentTimeMillis()
        if (_pomodoroState.value.isStopwatchMode) {
            val elapsed = ((now - stopwatchStartTimeMillis) / 1000L).toInt().coerceAtLeast(0)
            _pomodoroState.update { it.copy(isRunning = false, isPaused = true, stopwatchSeconds = elapsed) }
        } else {
            val rem = ((timerTargetEndTimeMillis - now + 999L) / 1000L).toInt().coerceAtLeast(0)
            _pomodoroState.update { it.copy(isRunning = false, isPaused = true, remainingSeconds = rem) }
        }
        persistTimerState()

        val state = _pomodoroState.value
        NotificationHelper.showTimerOngoingNotification(
            context = getApplication(),
            remainingSeconds = if (state.isStopwatchMode) state.stopwatchSeconds else state.remainingSeconds,
            phaseLabel = if (state.isStopwatchMode) "Cronômetro" else state.currentPhase.label,
            isPaused = true
        )
    }

    fun resetPomodoro() {
        vibrate(40)
        timerJob?.cancel()
        timerJob = null
        NotificationHelper.cancelTimerEndAlarm(getApplication())
        try {
            com.example.service.FocusDistractionService.stop(getApplication())
        } catch (_: Exception) {}
        timerTargetEndTimeMillis = 0L
        stopwatchStartTimeMillis = 0L
        val state = _pomodoroState.value

        if (state.isStopwatchMode && state.stopwatchSeconds >= 60 && state.selectedCategory.isNotBlank()) {
            val elapsedMins = state.stopwatchSeconds / 60
            viewModelScope.launch {
                val session = FocusSession(
                    category = state.selectedCategory,
                    durationMinutes = elapsedMins,
                    breakMinutes = 0,
                    cycleCount = 0,
                    linkedTaskId = state.linkedTask?.id,
                    linkedTaskTitle = state.linkedTask?.title
                )
                repository.recordFocusSession(session)
            }
        }

        val sec = state.focusMinutes * 60
        _pomodoroState.update {
            it.copy(
                isRunning = false,
                isPaused = false,
                stopwatchSeconds = 0,
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
        NotificationHelper.cancelTimerEndAlarm(getApplication())
        try {
            com.example.service.FocusDistractionService.stop(getApplication())
        } catch (_: Exception) {}
        val completedSessionKey = timerTargetEndTimeMillis
        timerTargetEndTimeMillis = 0L
        stopwatchStartTimeMillis = 0L
        val state = _pomodoroState.value
        val isContinuous = preferencesManager.timerContinuousMode.value

        if (state.currentPhase == PomodoroPhase.FOCUS) {
            val breakSec = state.breakMinutes * 60
            // Atomically claim focus session completion so AlarmReceiver and foreground timerJob/onResume NEVER double-insert
            val claimed = preferencesManager.tryClaimFocusSessionCompletion(
                targetEndTimeMillis = completedSessionKey,
                nextBreakSeconds = breakSec
            )

            if (claimed) {
                viewModelScope.launch {
                    val session = FocusSession(
                        category = state.selectedCategory,
                        durationMinutes = state.focusMinutes,
                        breakMinutes = 0,
                        cycleCount = state.currentCycle,
                        linkedTaskId = state.linkedTask?.id,
                        linkedTaskTitle = state.linkedTask?.title
                    )
                    repository.recordFocusSession(session)
                    spawnTimerCitraBall(state.focusMinutes, completedSessionKey)

                    val awardMessage = when {
                        state.focusMinutes >= 120 -> "🌟 Incrível! Você ganhou o Citra Dourado (120 min)!"
                        state.focusMinutes >= 60 -> "🍋 Parabéns! Você ganhou o Citra Amarelo (60 min)!"
                        else -> "🍊 Novo Citra criado no menu do timer!"
                    }
                    _userMessage.emit(awardMessage)

                    if (state.linkedHabit != null) {
                        val isAlreadyDone = state.linkedHabit.completedDatesCsv.split(",")
                            .any { it.trim() == todayString }
                        if (!isAlreadyDone) {
                            repository.toggleHabitToday(state.linkedHabit)
                            _userMessage.emit("Hábito '${state.linkedHabit.title}' marcado como feito hoje!")
                        }
                    }
                }
            }

            timerJob?.cancel()
            timerJob = null
            musicPlayer.stop()

            val vibEnabled = notificationSettings.value.vibrate
            _pomodoroState.update {
                it.copy(
                    isRunning = false,
                    isPaused = false,
                    currentPhase = PomodoroPhase.BREAK,
                    remainingSeconds = breakSec,
                    totalPhaseSeconds = breakSec
                )
            }
            persistTimerState()

            if (claimed) {
                NotificationHelper.showInstantNotification(
                    context = getApplication(),
                    id = NotificationHelper.TIMER_NOTIFICATION_ID,
                    channelId = NotificationHelper.CHANNEL_ALERTS,
                    title = "Sessão ${state.currentCycle} de ${state.totalCycles} Concluída!",
                    message = "Excelente dedicação de ${state.focusMinutes}m! Pausa de ${state.breakMinutes}m pronta.",
                    customSoundUriString = notificationSettings.value.pomodoroSoundUri,
                    vibratePhone = vibEnabled
                )
            }

            if (isContinuous) {
                startPomodoro()
            }
        } else {
            timerJob?.cancel()
            timerJob = null
            musicPlayer.stop()

            val vibEnabled = notificationSettings.value.vibrate
            if (state.currentCycle < state.totalCycles) {
                val nextCycle = state.currentCycle + 1
                val focusSec = state.focusMinutes * 60
                _pomodoroState.update {
                    it.copy(
                        isRunning = false,
                        isPaused = false,
                        currentPhase = PomodoroPhase.FOCUS,
                        currentCycle = nextCycle,
                        remainingSeconds = focusSec,
                        totalPhaseSeconds = focusSec
                    )
                }
                persistTimerState()

                NotificationHelper.showInstantNotification(
                    context = getApplication(),
                    id = NotificationHelper.TIMER_NOTIFICATION_ID,
                    channelId = NotificationHelper.CHANNEL_ALERTS,
                    title = "Pausa Concluída",
                    message = if (isContinuous) "Iniciando Ciclo $nextCycle de ${state.totalCycles} automaticamente!" else "Pronto para o Ciclo $nextCycle de ${state.totalCycles} de foco?",
                    customSoundUriString = notificationSettings.value.pomodoroSoundUri,
                    vibratePhone = vibEnabled
                )

                if (isContinuous) {
                    startPomodoro()
                }
            } else {
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

                NotificationHelper.showInstantNotification(
                    context = getApplication(),
                    id = NotificationHelper.TIMER_NOTIFICATION_ID,
                    channelId = NotificationHelper.CHANNEL_ALERTS,
                    title = "🎉 Ciclos Concluídos!",
                    message = "Parabéns! Todos os ${state.totalCycles} ciclos foram finalizados com êxito.",
                    customSoundUriString = notificationSettings.value.pomodoroSoundUri,
                    vibratePhone = vibEnabled
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
        reminderMinutesBefore: Int,
        customSoundUri: String = "",
        loopAlarm: Boolean = false,
        isDisruptive: Boolean = false
    ) {
        viewModelScope.launch {
            repository.insertTask(
                Task(
                    title = title,
                    category = category.ifBlank { "Estudos" },
                    priority = priority,
                    dueDateText = dueDateText.ifBlank { todayString },
                    dueDateTimestamp = dueTimestamp,
                    reminderMinutesBefore = reminderMinutesBefore,
                    customSoundUri = customSoundUri,
                    loopAlarm = loopAlarm,
                    isDisruptive = isDisruptive
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

    fun setTaskWontDo(task: Task, wontDo: Boolean) {
        viewModelScope.launch {
            vibrate(40)
            val updated = task.copy(
                isWontDo = wontDo,
                isCompleted = if (wontDo) false else task.isCompleted
            )
            repository.updateTask(updated)
            if (wontDo) {
                _userMessage.emit("Tarefa marcada como 'Não vou fazer'")
            } else {
                _userMessage.emit("Tarefa reativada!")
            }
        }
    }


    fun toggleTask(task: Task) {
        viewModelScope.launch {
            vibrate(35)
            val isDone = repository.toggleTaskCompleted(task)
            if (isDone) {
                _userMessage.emit("Tarefa '${task.title}' concluída!")
            }
            // Bidirectional sync: sync task completion status with all checklist items across notes
            try {
                val cleanTitle = task.title.trim()
                if (cleanTitle.isNotEmpty()) {
                    for (note in notes.value) {
                        if (note.isExternalDocument) continue
                        val lines = note.content.lines().toMutableList()
                        var noteModified = false
                        for (i in lines.indices) {
                            val line = lines[i]
                            val trimmed = line.trimStart()
                            val indent = line.substring(0, line.length - trimmed.length)
                            if (isDone) {
                                if (trimmed.startsWith("- [ ]") || trimmed.startsWith("* [ ]")) {
                                    val itemText = trimmed.substring(5).trim()
                                    val cleanItem = itemText.removePrefix("~~").removeSuffix("~~").trim()
                                    if (cleanItem.equals(cleanTitle, ignoreCase = true) ||
                                        cleanItem.startsWith(cleanTitle, ignoreCase = true) ||
                                        cleanTitle.startsWith(cleanItem, ignoreCase = true)) {
                                        lines[i] = "$indent- [x] ~~$cleanItem~~"
                                        noteModified = true
                                    }
                                }
                            } else {
                                if (trimmed.startsWith("- [x]", ignoreCase = true) || trimmed.startsWith("- [X]") ||
                                    trimmed.startsWith("* [x]", ignoreCase = true) || trimmed.startsWith("* [X]")) {
                                    val itemText = trimmed.substring(5).trim()
                                    val cleanItem = itemText.removePrefix("~~").removeSuffix("~~").trim()
                                    if (cleanItem.equals(cleanTitle, ignoreCase = true) ||
                                        cleanItem.startsWith(cleanTitle, ignoreCase = true) ||
                                        cleanTitle.startsWith(itemText, ignoreCase = true)) {
                                        lines[i] = "$indent- [ ] $cleanItem"
                                        noteModified = true
                                    }
                                }
                            }
                        }
                        if (noteModified) {
                            val newContent = lines.joinToString("\n")
                            repository.updateNote(note.copy(content = newContent, updatedAt = System.currentTimeMillis()))
                            com.example.util.CitraDocsFileManager.saveNoteMarkdown(getApplication(), note.title, newContent)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun syncChecklistToTask(checklistText: String, isChecked: Boolean) {
        val clean = checklistText.trim()
            .removePrefix("- [ ]").removePrefix("- [x]").removePrefix("- [X]")
            .removePrefix("* [ ]").removePrefix("* [x]").removePrefix("* [X]")
            .removePrefix("~~").removeSuffix("~~")
            .trim()
        if (clean.isBlank()) return
        val matchingTask = tasks.value.firstOrNull {
            val tTitle = it.title.trim()
            tTitle.equals(clean, ignoreCase = true) ||
            clean.startsWith(tTitle, ignoreCase = true) ||
            tTitle.startsWith(clean, ignoreCase = true)
        }
        if (matchingTask != null && matchingTask.isCompleted != isChecked) {
            viewModelScope.launch {
                val toggled = repository.toggleTaskCompleted(matchingTask)
                if (toggled) {
                    val soundSetting = notificationSettings.value.taskSoundUri.ifBlank { notificationSettings.value.taskSound }
                    if (soundSetting.isNotBlank()) {
                        com.example.util.SoundEffectsManager.playSoundById(getApplication(), soundSetting)
                    }
                    vibrate(35)
                    _userMessage.emit("Tarefa '${matchingTask.title}' concluída! ✅")
                } else {
                    _userMessage.emit("Tarefa '${matchingTask.title}' desmarcada.")
                }
            }
        }
    }

    private val _isStartupScanDone = MutableStateFlow(false)
    val isStartupScanDone: StateFlow<Boolean> = _isStartupScanDone.asStateFlow()

    fun performStartupFolderScan() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            var recordsError: String? = null

            // Verificar pasta "Citra Records"
            try {
                val recFolder = com.example.util.MediaStorageHelper.getCitraRecordsFolder(context)
                if (!recFolder.exists()) {
                    val created = recFolder.mkdirs()
                    if (!created && !recFolder.exists()) {
                        recordsError = "Pasta 'Citra Records' não pôde ser criada. Verifique as permissões de armazenamento."
                    }
                }
            } catch (e: Exception) {
                recordsError = "Erro na pasta Citra Records: ${e.message}"
            }

            // Reconcile and deduplicate local notes seamlessly
            try {
                repository.deduplicateLocalNotes()
            } catch (_: Exception) {}

            kotlinx.coroutines.delay(250)
            _isStartupScanDone.value = true

            if (recordsError != null) {
                _userMessage.emit(recordsError)
            }
        }
    }

    fun importMultipleMarkdownFiles(
        uris: List<Uri>,
        context: Context,
        preferredCategory: String? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            var importedNotesCount = 0
            var linkedDocsCount = 0
            var skippedDuplicatesCount = 0
            var errorCount = 0

            val currentNotes = repository.getAllNotesSync()
            val existingTitles = currentNotes.filter { !it.isExternalDocument }
                .map { it.title.trim().lowercase() }.toMutableSet()
            val existingExternalUris = currentNotes.mapNotNull { it.externalUri?.trim() }.toMutableSet()
            val resolvedCategory = preferredCategory
                ?.trim()
                ?.takeIf { it.isNotBlank() && !it.equals("Todas", ignoreCase = true) }
                ?: "Geral"

            val recentDocsToSave = mutableListOf<com.example.data.local.RecentExternalDocEntry>()

            for (uri in uris) {
                try {
                    com.example.util.ExternalDocumentUriHelper.takePersistableReadPermission(context, uri)
                    val filename = com.example.util.ExternalDocumentUriHelper.resolveDisplayName(context, uri)
                        ?: queryFileName(context, uri)
                        ?: "Documento_${System.currentTimeMillis()}"
                    val rawTitle = filename.substringBeforeLast(".").ifBlank { filename }.trim()

                    val externalDocType = com.example.util.ExternalDocumentUriHelper.detectExternalDocumentType(
                        context = context,
                        uri = uri,
                        displayName = filename
                    )
                    if (externalDocType != null) {
                        val uriStr = uri.toString()
                        recentDocsToSave.add(
                            com.example.data.local.RecentExternalDocEntry(
                                uri = uriStr,
                                title = rawTitle,
                                docType = externalDocType.id,
                                addedAt = System.currentTimeMillis()
                            )
                        )
                        if (existingExternalUris.contains(uriStr)) {
                            skippedDuplicatesCount++
                            continue
                        }
                        val docIcon = if (externalDocType == NoteDocumentType.PDF) "📄" else "📘"
                        val docLinkNote = Note(
                            uuid = java.util.UUID.randomUUID().toString(),
                            title = rawTitle,
                            content = "",
                            category = resolvedCategory,
                            iconEmoji = docIcon,
                            documentType = externalDocType.id,
                            externalUri = uriStr,
                            updatedAt = System.currentTimeMillis()
                        )
                        repository.insertNote(docLinkNote)
                        existingExternalUris.add(uriStr)
                        linkedDocsCount++
                        continue
                    }

                    val rawContent = context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader(Charsets.UTF_8).readText()
                    }

                    if (rawContent != null) {
                        // Check if it's a full Citra backup JSON
                        val trimmed = rawContent.trim()
                        if (filename.lowercase().endsWith(".json") && trimmed.contains("\"version\"") && (trimmed.contains("\"tasks\"") || trimmed.contains("\"notes\""))) {
                            try {
                                val backupData = BackupManager.parseBackupJson(rawContent)
                                val parsedNotes = BackupManager.parseNotesJson(rawContent, context)
                                for (n in parsedNotes) {
                                    repository.upsertNoteByUuid(n)
                                    if (!n.isExternalDocument) {
                                        com.example.util.CitraDocsFileManager.saveNoteMarkdown(context, n.title, n.content, uuid = n.uuid)
                                    }
                                    importedNotesCount++
                                }
                                for (t in backupData.tasks) {
                                    repository.insertTask(t.copy(id = 0L))
                                }
                                for (h in backupData.habits) {
                                    repository.insertHabit(h.copy(id = 0L))
                                }
                                for (c in backupData.categories) {
                                    repository.categoryDao.insertCategory(c)
                                }
                                continue
                            } catch (_: Exception) {}
                        }

                        // Check if it's a JSON array of notes
                        if (filename.lowercase().endsWith(".json") && trimmed.startsWith("[")) {
                            try {
                                val array = org.json.JSONArray(trimmed)
                                var anyImported = false
                                for (i in 0 until array.length()) {
                                    val item = array.optJSONObject(i)
                                    if (item != null) {
                                        val nTitle = item.optString("title", "Nota ${i + 1}")
                                        val nContent = item.optString("content", item.optString("body", item.toString(2)))
                                        val nCat = item.optString("category", resolvedCategory)
                                        val nUuid = item.optString("uuid").ifBlank { java.util.UUID.randomUUID().toString() }
                                        repository.upsertNoteByUuid(
                                            Note(
                                                uuid = nUuid,
                                                title = nTitle,
                                                content = nContent,
                                                category = nCat,
                                                updatedAt = System.currentTimeMillis()
                                            )
                                        )
                                        com.example.util.CitraDocsFileManager.saveNoteMarkdown(context, nTitle, nContent, uuid = nUuid)
                                        importedNotesCount++
                                        anyImported = true
                                    }
                                }
                                if (anyImported) continue
                            } catch (_: Exception) {}
                        }

                        val existingCitraFile = com.example.util.MediaStorageHelper.resolveExistingCitraFile(context, uri.toString())
                        val isFromCitraDocs = existingCitraFile?.let { f ->
                            com.example.util.CitraDocsFileManager.getAllDocsFolders(context).any { docsDir ->
                                f.parentFile?.absolutePath == docsDir.absolutePath
                            }
                        } == true

                        val markdownContent = com.example.util.CitraDocsFileManager.convertContentToMarkdown(filename, rawContent)
                        val fileUuid = com.example.util.CitraDocsFileManager.extractUuidFromMarkdown(markdownContent)
                        val cleanContent = com.example.util.CitraDocsFileManager.stripFrontmatter(markdownContent)

                        val existingByUuid = if (fileUuid != null) repository.getNoteByUuid(fileUuid) else null
                        val existingByTitle = if (existingCitraFile != null) {
                            currentNotes.firstOrNull {
                                !it.isExternalDocument && it.title.trim().equals(rawTitle, ignoreCase = true)
                            }
                        } else null
                        val matchedNote = existingByUuid?.takeIf { !it.isExternalDocument } ?: existingByTitle

                        if (matchedNote != null) {
                            repository.updateNote(
                                matchedNote.copy(
                                    title = rawTitle,
                                    content = cleanContent,
                                    updatedAt = System.currentTimeMillis(),
                                    isDeleted = false,
                                    deletedAt = null
                                )
                            )
                            if (existingCitraFile != null) {
                                try {
                                    android.media.MediaScannerConnection.scanFile(
                                        context,
                                        arrayOf(existingCitraFile.absolutePath),
                                        arrayOf("text/markdown", "text/plain"),
                                        null
                                    )
                                } catch (_: Exception) {}
                            } else if (!isFromCitraDocs) {
                                com.example.util.CitraDocsFileManager.saveNoteMarkdown(context, rawTitle, cleanContent, uuid = matchedNote.uuid)
                            }
                            importedNotesCount++
                        } else {
                            val finalTitle = if (existingCitraFile != null || !existingTitles.contains(rawTitle.lowercase())) {
                                rawTitle
                            } else {
                                "$rawTitle (${System.currentTimeMillis().toString().takeLast(4)})"
                            }
                            existingTitles.add(finalTitle.lowercase())
                            val newUuid = fileUuid ?: java.util.UUID.randomUUID().toString()
                            repository.insertNote(
                                Note(
                                    uuid = newUuid,
                                    title = finalTitle,
                                    content = cleanContent,
                                    category = resolvedCategory,
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                            if (existingCitraFile != null) {
                                try {
                                    android.media.MediaScannerConnection.scanFile(
                                        context,
                                        arrayOf(existingCitraFile.absolutePath),
                                        arrayOf("text/markdown", "text/plain"),
                                        null
                                    )
                                } catch (_: Exception) {}
                            } else {
                                com.example.util.CitraDocsFileManager.saveNoteMarkdown(context, finalTitle, cleanContent, uuid = newUuid)
                            }
                            importedNotesCount++
                        }
                    } else {
                        errorCount++
                    }
                } catch (_: Exception) {
                    errorCount++
                }
            }

            if (recentDocsToSave.isNotEmpty()) {
                preferencesManager.recordRecentExternalDocs(recentDocsToSave)
            }

            if (linkedDocsCount > 0 || importedNotesCount > 0) {
                com.example.widget.CitraWidgetHelper.updateAllWidgets(getApplication())
            }

            val parts = mutableListOf<String>()
            if (linkedDocsCount > 0) {
                parts.add("$linkedDocsCount documento(s) (PDF/Word) adicionado(s)")
            }
            if (importedNotesCount > 0) {
                parts.add("$importedNotesCount nota(s) importada(s)")
            }
            if (skippedDuplicatesCount > 0) {
                parts.add("$skippedDuplicatesCount já adicionado(s)")
            }
            if (errorCount > 0) {
                parts.add("$errorCount com erro")
            }
            val msg = if (parts.isEmpty()) "Nenhum arquivo importado." else parts.joinToString(" • ") + "!"
            _userMessage.emit(msg)
        }
    }

    fun openOrAddRecentExternalDocs(
        entries: List<com.example.data.local.RecentExternalDocEntry>,
        preferredCategory: String? = null,
        openIfSingle: Boolean = true
    ) {
        if (entries.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val allExisting = repository.getAllNotesSync()
            val resolvedCategory = preferredCategory
                ?.trim()
                ?.takeIf { it.isNotBlank() && !it.equals("Todas", ignoreCase = true) }
                ?: "Geral"
            var addedCount = 0
            var resolvedSingle: Note? = null

            for (entry in entries) {
                val docType = NoteDocumentType.fromId(entry.docType)
                val existing = allExisting.firstOrNull {
                    it.externalUri?.trim() == entry.uri.trim() && it.resolvedDocumentType == docType
                }
                if (existing != null) {
                    val activeCopy = if (existing.isArchived || existing.isDeleted) {
                        val restored = existing.copy(
                            isArchived = false,
                            isDeleted = false,
                            deletedAt = null,
                            updatedAt = System.currentTimeMillis()
                        )
                        repository.updateNote(restored)
                        restored
                    } else {
                        existing
                    }
                    if (entries.size == 1) resolvedSingle = activeCopy
                } else {
                    val docIcon = if (docType == NoteDocumentType.PDF) "📄" else "📘"
                    val newDoc = Note(
                        uuid = java.util.UUID.randomUUID().toString(),
                        title = entry.title.ifBlank { "Documento" },
                        content = "",
                        category = resolvedCategory,
                        iconEmoji = docIcon,
                        documentType = docType.id,
                        externalUri = entry.uri,
                        updatedAt = System.currentTimeMillis()
                    )
                    val newId = repository.insertNote(newDoc)
                    addedCount++
                    if (entries.size == 1) resolvedSingle = newDoc.copy(id = newId)
                }
            }

            preferencesManager.recordRecentExternalDocs(
                entries.map { it.copy(addedAt = System.currentTimeMillis()) }
            )
            if (addedCount > 0) {
                com.example.widget.CitraWidgetHelper.updateAllWidgets(getApplication())
            }
            if (openIfSingle && entries.size == 1 && resolvedSingle != null) {
                _noteToOpen.value = resolvedSingle
            } else if (addedCount > 0) {
                _userMessage.emit("$addedCount documento(s) carregado(s) para as notas!")
            } else {
                _userMessage.emit("Documentos já estão na lista de notas.")
            }
        }
    }

    fun removeRecentExternalDoc(uri: String) {
        preferencesManager.removeRecentExternalDoc(uri)
    }

    fun openOrRegisterExternalDocumentFromIntent(
        uri: Uri,
        mimeType: String?,
        context: Context
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                com.example.util.ExternalDocumentUriHelper.takePersistableReadPermission(context, uri)
                val filename = com.example.util.ExternalDocumentUriHelper.resolveDisplayName(context, uri)
                    ?: queryFileName(context, uri)
                    ?: "Documento_${System.currentTimeMillis()}"
                val docType = com.example.util.ExternalDocumentUriHelper.detectExternalDocumentType(
                    context = context,
                    uri = uri,
                    displayName = filename,
                    mimeTypeHint = mimeType
                )
                if (docType == null) {
                    _userMessage.emit("Formato de arquivo não suportado para leitura externa.")
                    return@launch
                }

                val uriStr = uri.toString()
                val rawTitle = filename.substringBeforeLast(".").ifBlank { filename }.trim()
                preferencesManager.recordRecentExternalDocs(
                    listOf(
                        com.example.data.local.RecentExternalDocEntry(
                            uri = uriStr,
                            title = rawTitle,
                            docType = docType.id,
                            addedAt = System.currentTimeMillis()
                        )
                    )
                )

                val existingNotes = repository.getAllNotesSync()
                val existing = existingNotes.firstOrNull {
                    it.externalUri?.trim() == uriStr && it.resolvedDocumentType == docType
                }

                if (existing != null) {
                    if (existing.isArchived) {
                        repository.setNoteArchived(existing, false)
                    }
                    _noteToOpen.value = existing.copy(isArchived = false)
                } else {
                    val docIcon = if (docType == NoteDocumentType.PDF) "📄" else "📘"
                    val newDoc = Note(
                        uuid = java.util.UUID.randomUUID().toString(),
                        title = rawTitle,
                        content = "",
                        category = "Geral",
                        iconEmoji = docIcon,
                        documentType = docType.id,
                        externalUri = uriStr,
                        updatedAt = System.currentTimeMillis()
                    )
                    val newId = repository.insertNote(newDoc)
                    com.example.widget.CitraWidgetHelper.updateAllWidgets(getApplication())
                    _noteToOpen.value = newDoc.copy(id = newId)
                    val label = if (docType == NoteDocumentType.PDF) "PDF" else "Word (.docx)"
                    _userMessage.emit("$label '$rawTitle' adicionado ao menu de notas!")
                }
            } catch (_: Exception) {
                _userMessage.emit("Não foi possível abrir o arquivo selecionado.")
            }
        }
    }

    private fun queryFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1 && cursor.moveToFirst()) {
                        name = cursor.getString(index)
                    }
                }
            } catch (_: Exception) {}
        }
        return name ?: uri.path?.substringAfterLast('/')
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
        frequencyDays: String,
        customSoundUri: String = "",
        loopAlarm: Boolean = false
    ) {
        viewModelScope.launch {
            repository.insertHabit(
                Habit(
                    title = title,
                    category = category.ifBlank { "Saúde" },
                    iconEmoji = iconEmoji.ifBlank { "✨" },
                    reminderTime = reminderTime.ifBlank { "08:00" },
                    reminderLeadMinutes = reminderLeadMinutes,
                    frequencyDays = frequencyDays.ifBlank { "ALL" },
                    customSoundUri = customSoundUri,
                    loopAlarm = loopAlarm
                )
            )
            com.example.widget.CitraWidgetHelper.updateAllWidgets(getApplication())
            _userMessage.emit("Hábito '$title' adicionado (lembrete às $reminderTime)!")
        }
    }

    fun updateHabit(habit: Habit) {
        viewModelScope.launch {
            repository.updateHabit(habit)
            com.example.widget.CitraWidgetHelper.updateAllWidgets(getApplication())
            _userMessage.emit("Hábito atualizado!")
        }
    }

    fun toggleHabitToday(habit: Habit) {
        viewModelScope.launch {
            vibrate(40)
            val (isDone, consecutiveDays) = repository.toggleHabitTodayWithConsecutiveDays(habit)
            com.example.widget.CitraWidgetHelper.updateAllWidgets(getApplication())
            if (isDone) {
                val points = if (consecutiveDays >= 2) {
                    (10 + (consecutiveDays - 1) * 5).coerceAtMost(100)
                } else {
                    10
                }
                preferencesManager.addHabitRewardPoints(points)
                val msg = if (consecutiveDays >= 2) {
                    "🌟 $consecutiveDays dias seguidos em '${habit.title}'! +$points pts de recompensa pela constância!"
                } else {
                    "✨ '${habit.title}' cumprido hoje! +$points pts pela dedicação!"
                }
                _userMessage.emit(msg)
            } else {
                _userMessage.emit("Hábito desmarcado para hoje.")
            }
        }
    }

    fun deleteHabit(habit: Habit) {
        viewModelScope.launch {
            repository.deleteHabit(habit)
            com.example.notifications.NotificationHelper.cancelHabitReminder(getApplication(), habit.id)
            com.example.notifications.NextAlarmScheduler.scheduleNextAlarm(getApplication())
            com.example.widget.CitraWidgetHelper.updateAllWidgets(getApplication())
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
            com.example.widget.CitraWidgetHelper.updateAllWidgets(getApplication())
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
        backgroundBrightness: Float = 0.5f,
        uuid: String? = null,
        oldTitle: String? = null,
        noteColor: String? = null,
        isArchived: Boolean = false,
        onSaved: ((Long) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val existing = if (id != 0L) repository.getNoteById(id) else null
            val resolvedUuid = uuid ?: existing?.uuid ?: java.util.UUID.randomUUID().toString()
            val previousTitle = oldTitle ?: existing?.title
            val finalTitle = title.ifBlank { "Sem título" }
            val cleanContent = com.example.util.CitraDocsFileManager.stripFrontmatter(content)

            val note = Note(
                id = id,
                uuid = resolvedUuid,
                title = finalTitle,
                content = cleanContent,
                category = category.ifBlank { "Geral" },
                iconEmoji = iconEmoji.ifBlank { "📝" },
                associatedDate = associatedDate,
                isPinned = isPinned,
                backgroundImageUri = backgroundImageUri,
                backgroundBlur = backgroundBlur,
                backgroundBrightness = backgroundBrightness,
                isDeleted = false,
                isArchived = isArchived || (existing?.isArchived == true),
                noteColor = noteColor ?: existing?.noteColor,
                documentType = existing?.documentType ?: NoteDocumentType.MARKDOWN.id,
                externalUri = existing?.externalUri,
                updatedAt = System.currentTimeMillis()
            )
            val resultId = if (id == 0L) {
                repository.insertNote(note)
            } else {
                repository.updateNote(note)
                id
            }

            if (!note.isExternalDocument) {
                // Sync to Citra Docs, cleaning old file if title changed
                com.example.util.CitraDocsFileManager.saveNoteMarkdown(
                    context = getApplication(),
                    title = finalTitle,
                    content = cleanContent,
                    uuid = resolvedUuid,
                    oldTitle = previousTitle
                )
            }

            com.example.widget.CitraWidgetHelper.updateAllWidgets(getApplication())
            onSaved?.invoke(resultId)
        }
    }

    fun updateNoteCategory(note: Note, newCategory: String, onUpdated: ((Note) -> Unit)? = null) {
        val cleanCategory = newCategory.trim().ifBlank { "Geral" }
        viewModelScope.launch {
            val updated = note.copy(
                category = cleanCategory,
                updatedAt = System.currentTimeMillis()
            )
            repository.updateNote(updated)
            com.example.widget.CitraWidgetHelper.updateAllWidgets(getApplication())
            onUpdated?.invoke(updated)
            _userMessage.emit("Categoria alterada para '$cleanCategory'.")
        }
    }

    fun relinkExternalDocumentUri(
        note: Note,
        newUri: Uri,
        context: Context,
        onUpdated: ((Note) -> Unit)? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            com.example.util.ExternalDocumentUriHelper.takePersistableReadPermission(context, newUri)
            val displayName = com.example.util.ExternalDocumentUriHelper.resolveDisplayName(context, newUri)
            val detected = com.example.util.ExternalDocumentUriHelper.detectExternalDocumentType(context, newUri, displayName)
            val cleanTitle = displayName?.substringBeforeLast(".")?.ifBlank { note.title } ?: note.title
            val updated = note.copy(
                title = cleanTitle,
                externalUri = newUri.toString(),
                documentType = detected?.id ?: note.documentType,
                updatedAt = System.currentTimeMillis()
            )
            repository.updateNote(updated)
            preferencesManager.recordRecentExternalDocs(
                listOf(
                    com.example.data.local.RecentExternalDocEntry(
                        uri = newUri.toString(),
                        title = cleanTitle,
                        docType = updated.documentType,
                        addedAt = System.currentTimeMillis()
                    )
                )
            )
            com.example.widget.CitraWidgetHelper.updateAllWidgets(getApplication())
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                onUpdated?.invoke(updated)
            }
            _userMessage.emit("Caminho do documento atualizado!")
        }
    }

    fun convertExternalDocumentToEditableNote(
        docNote: Note,
        context: Context,
        onConverted: (Note) -> Unit,
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val uriString = docNote.externalUri
            if (uriString.isNullOrBlank()) {
                val err = "Caminho do arquivo não encontrado."
                _userMessage.emit(err)
                kotlinx.coroutines.withContext(Dispatchers.Main) { onError(err) }
                return@launch
            }

            val markdownResult: Pair<String?, String?> = try {
                val stream = com.example.util.ExternalDocumentUriHelper.openInputStream(context, uriString)
                if (stream == null) {
                    null to "Não foi possível abrir o arquivo. Verifique se o PDF ou Word (.docx) ainda existe ou selecione-o novamente."
                } else {
                    stream.use { input ->
                        when (docNote.resolvedDocumentType) {
                            NoteDocumentType.PDF -> {
                                val res = com.example.util.PdfDocumentEngine.extractMarkdown(context, input) { bytes, ext ->
                                    saveDocxExtractedImage(context, bytes, ext)
                                }
                                when (res) {
                                    is com.example.util.PdfConversionResult.Success -> res.markdown to null
                                    is com.example.util.PdfConversionResult.Error -> null to res.message
                                }
                            }
                            NoteDocumentType.DOCX -> {
                                val res = com.example.util.DocxDocumentEngine.convertToMarkdown(input) { bytes, ext ->
                                    saveDocxExtractedImage(context, bytes, ext)
                                }
                                when (res) {
                                    is com.example.util.DocxConversionResult.Success -> res.markdown to null
                                    is com.example.util.DocxConversionResult.Error -> null to res.message
                                }
                            }
                            NoteDocumentType.MARKDOWN -> docNote.content to null
                        }
                    }
                }
            } catch (e: Exception) {
                null to (e.message ?: "Erro inesperado ao converter o documento.")
            }

            val extractedMd = markdownResult.first
            val errorMsg = markdownResult.second
            if (errorMsg != null || extractedMd == null) {
                val finalErr = errorMsg ?: "Falha ao converter documento."
                _userMessage.emit(finalErr)
                kotlinx.coroutines.withContext(Dispatchers.Main) { onError(finalErr) }
                return@launch
            }

            val existingTitles = repository.getAllNotesSync()
                .filter { !it.isExternalDocument }
                .map { it.title.trim().lowercase() }
                .toSet()

            val baseTitle = docNote.title.ifBlank { "Documento Convertido" }
            val finalTitle = if (existingTitles.contains(baseTitle.lowercase())) {
                "$baseTitle (Nota)"
            } else {
                baseTitle
            }
            val finalContent = extractedMd.ifBlank { "# $finalTitle\n\n*(Documento sem texto extraível)*" }
            val newUuid = java.util.UUID.randomUUID().toString()
            val newNote = Note(
                id = 0L,
                uuid = newUuid,
                title = finalTitle,
                content = finalContent,
                category = docNote.category.ifBlank { "Geral" },
                iconEmoji = "📝",
                isPinned = docNote.isPinned,
                noteColor = docNote.noteColor,
                documentType = NoteDocumentType.MARKDOWN.id,
                externalUri = null,
                updatedAt = System.currentTimeMillis()
            )
            val newId = repository.insertNote(newNote)
            com.example.util.CitraDocsFileManager.saveNoteMarkdown(
                context = context,
                title = finalTitle,
                content = finalContent,
                uuid = newUuid
            )
            com.example.widget.CitraWidgetHelper.updateAllWidgets(getApplication())
            val savedNote = newNote.copy(id = newId)
            _userMessage.emit("Convertido para a nota '$finalTitle'!")
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                onConverted(savedNote)
            }
        }
    }

    private fun saveDocxExtractedImage(context: Context, bytes: ByteArray, ext: String): String? {
        return try {
            val dir = com.example.util.MediaStorageHelper.getCitraPicFolder(context)
            val safeExt = ext.lowercase().takeIf { it in setOf("jpg", "jpeg", "png", "webp", "gif") } ?: "jpg"
            val destFile = File(dir, "citra_docx_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(6)}.$safeExt")
            destFile.writeBytes(bytes)
            destFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            repository.moveToTrash(note)
            com.example.widget.CitraWidgetHelper.updateAllWidgets(getApplication())
            val msg = when (note.resolvedDocumentType) {
                NoteDocumentType.PDF -> "PDF movido para a Lixeira (seu arquivo original permanece intacto)."
                NoteDocumentType.DOCX -> "Documento Word (.docx) movido para a Lixeira (seu arquivo original permanece intacto)."
                NoteDocumentType.MARKDOWN -> "Anotação movida para a Lixeira (30 dias)."
            }
            _userMessage.emit(msg)
        }
    }

    fun moveNoteToTrash(note: Note) = deleteNote(note)

    fun restoreNoteFromTrash(note: Note) {
        viewModelScope.launch {
            repository.restoreFromTrash(note)
            _userMessage.emit("Anotação restaurada com sucesso!")
        }
    }

    fun restoreAllTrashedNotes() {
        viewModelScope.launch {
            val currentTrash = trashNotes.value
            if (currentTrash.isEmpty()) return@launch
            currentTrash.forEach { note ->
                repository.restoreFromTrash(note)
            }
            _userMessage.emit("Todas as ${currentTrash.size} anotações foram restauradas!")
        }
    }

    fun permanentlyDeleteNote(note: Note, context: Context? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = context ?: getApplication()
            if (note.isExternalDocument) {
                val remaining = repository.getAllNotesSync().filter { it.id != note.id }
                com.example.util.ExternalDocumentUriHelper.releasePersistableReadPermissionIfUnused(
                    ctx,
                    note.externalUri,
                    remaining
                )
            } else {
                com.example.util.CitraDocsFileManager.deleteNoteMarkdown(ctx, note.title, note.uuid)
            }
            repository.permanentlyDeleteNote(note)
            val msg = when (note.resolvedDocumentType) {
                NoteDocumentType.PDF -> "PDF removido do Citra (arquivo original preservado)."
                NoteDocumentType.DOCX -> "Word (.docx) removido do Citra (arquivo original preservado)."
                NoteDocumentType.MARKDOWN -> "Anotação excluída definitivamente do Citra e dos arquivos do dispositivo."
            }
            _userMessage.emit(msg)
        }
    }

    fun emptyNoteTrash(context: Context? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentTrash = trashNotes.value
            if (currentTrash.isEmpty()) return@launch
            val ctx = context ?: getApplication()
            currentTrash.forEach { note ->
                if (!note.isExternalDocument) {
                    com.example.util.CitraDocsFileManager.deleteNoteMarkdown(ctx, note.title, note.uuid)
                }
                repository.permanentlyDeleteNote(note)
            }
            _userMessage.emit("Lixeira esvaziada (${currentTrash.size} itens removidos).")
        }
    }

    fun deleteAllTasks() {
        viewModelScope.launch {
            repository.deleteAllTasks()
            _userMessage.emit("Todas as tarefas foram apagadas.")
        }
    }

    fun deleteAllHabits() {
        viewModelScope.launch {
            repository.deleteAllHabits()
            _userMessage.emit("Todos os hábitos foram apagados.")
        }
    }

    fun deleteAllNotes() {
        viewModelScope.launch {
            repository.deleteAllNotes()
            _userMessage.emit("Todas as anotações foram apagadas.")
        }
    }

    fun resetAllAppData() {
        viewModelScope.launch {
            repository.resetAllData()
            _userMessage.emit("Todos os dados do app foram resetados com sucesso.")
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
            achievements = achievements.value,
            notes = notes.value,
            financeEntries = financeEntries.value,
            financeCategories = financeCategories.value,
            financeWallets = financeWallets.value,
            financeRecurringRules = financeRecurringRules.value,
            financeBudgets = financeBudgets.value,
            financeNoteLinks = financeNoteLinks.value
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
                    categories = parsed.categories,
                    achievements = parsed.achievements,
                    notes = parsed.notes,
                    financeEntries = parsed.financeEntries,
                    financeCategories = parsed.financeCategories,
                    financeWallets = parsed.financeWallets,
                    financeRecurringRules = parsed.financeRecurringRules,
                    financeBudgets = parsed.financeBudgets,
                    financeNoteLinks = parsed.financeNoteLinks
                )
                _userMessage.emit("Backup local completo restaurado com sucesso!")
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
                val count = repository.upsertNotesLocally(parsed)
                _userMessage.emit("$count anotações importadas sem duplicatas!")
            } catch (e: Exception) {
                _userMessage.emit("Erro ao importar anotações.")
            }
        }
    }

    suspend fun exportFinancesJson(): String {
        return BackupManager.exportFinancesJson(database.financeDao())
    }

    fun restoreFinancesJson(jsonString: String?) {
        if (jsonString.isNullOrBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                BackupManager.restoreFinancesJson(database.financeDao(), jsonString)
            } catch (_: Exception) {}
        }
    }

    fun getVersionsForNote(noteId: Long): Flow<List<NoteVersion>> = repository.getVersionsForNote(noteId)

    fun restoreNoteVersion(note: Note, version: NoteVersion) {
        viewModelScope.launch {
            repository.restoreNoteVersion(note, version)
            _userMessage.emit("Versão #${version.versionNumber} restaurada!")
        }
    }

    fun createLocalCopyOfVersion(version: NoteVersion) {
        viewModelScope.launch {
            repository.createLocalCopyOfVersion(version)
            _userMessage.emit("Cópia local criada com sucesso!")
        }
    }

    fun restoreSingleNote(note: Note) {
        viewModelScope.launch {
            repository.insertNote(note.copy(id = 0L))
            _userMessage.emit("Nota '${note.title}' restaurada com sucesso!")
        }
    }

    fun restoreSingleTask(task: Task) {
        viewModelScope.launch {
            repository.insertTask(task.copy(id = 0L))
            _userMessage.emit("Tarefa '${task.title}' restaurada com sucesso!")
        }
    }

    fun restoreSingleHabit(habit: Habit) {
        viewModelScope.launch {
            repository.insertHabit(habit.copy(id = 0L))
            _userMessage.emit("Hábito '${habit.title}' restaurado com sucesso!")
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

    // ==================== FINANCES MODULE (V1.2 - BLOCKS A, B, C & D) ====================

    val financesEnabled: StateFlow<Boolean> = preferencesManager.financesEnabled
    val financesSetupCompleted: StateFlow<Boolean> = preferencesManager.financesSetupCompleted
    val financeDefaultCurrency: StateFlow<String> = preferencesManager.financeDefaultCurrency
    val financeBaseBalanceMinor: StateFlow<Long> = preferencesManager.financeBaseBalanceMinor
    val financeHideValues: StateFlow<Boolean> = preferencesManager.financeHideValues
    val financePinCode: StateFlow<String?> = preferencesManager.financePinCode
    val financeBiometricEnabled: StateFlow<Boolean> = preferencesManager.financeBiometricEnabled

    val financeCategories: StateFlow<List<FinanceCategory>> =
        database.financeDao().getActiveCategories()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allFinanceCategories: StateFlow<List<FinanceCategory>> =
        database.financeDao().getAllCategories()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val financeEntries: StateFlow<List<FinanceEntry>> =
        database.financeDao().getActiveEntries()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val financeNoteLinks: StateFlow<List<FinanceNoteLink>> =
        database.financeDao().getAllLinks()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val financeWallets: StateFlow<List<FinanceWallet>> =
        database.financeDao().getAllWallets()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val financeExchangeRates: StateFlow<List<FinanceExchangeRate>> =
        database.financeDao().getAllExchangeRates()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val financeBudgets: StateFlow<List<FinanceBudget>> =
        database.financeDao().getAllBudgets()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val financeRecurringRules: StateFlow<List<FinanceRecurringRule>> =
        database.financeDao().getAllRecurringRules()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun ensureDefaultFinanceCategories() {
        viewModelScope.launch(Dispatchers.IO) {
            if (database.financeDao().getCategoryCount() == 0) {
                database.financeDao().insertCategories(FinanceCategoryIcons.defaultCategories())
            }
            if (database.financeDao().getWalletCount() == 0) {
                database.financeDao().insertWallets(
                    listOf(
                        FinanceWallet(
                            id = "def_wallet_cash",
                            name = "Principal",
                            type = FinanceWalletType.CASH.id,
                            currencyCode = preferencesManager.financeDefaultCurrency.value,
                            colorHex = 0xFFFF8B1F,
                            isDefault = true
                        )
                    )
                )
            }
        }
    }

    fun setFinancesEnabled(enabled: Boolean) {
        preferencesManager.setFinancesEnabled(enabled)
        com.example.data.backup.FinanceLinkWorker.syncSchedule(getApplication(), enabled)
        if (enabled) {
            ensureDefaultFinanceCategories()
            runDueFinanceLinksOnStartup()
            processDueRecurringRules()
        }
    }

    fun completeFinanceSetup(
        currencyCode: String,
        initialBalanceMinor: Long?,
        pinCode: String?,
        biometricEnabled: Boolean
    ) {
        preferencesManager.setFinanceDefaultCurrency(currencyCode)
        if (initialBalanceMinor != null) {
            val netEntries = financeEntries.value.sumOf {
                if (it.type == FinanceEntryType.INCOME.id) it.totalAmountMinor else -it.totalAmountMinor
            }
            preferencesManager.setFinanceBaseBalanceMinor(initialBalanceMinor - netEntries)
        }
        preferencesManager.setFinancePinCode(pinCode?.takeIf { it.isNotBlank() })
        preferencesManager.setFinanceBiometricEnabled(biometricEnabled)
        preferencesManager.setFinancesSetupCompleted(true)
        preferencesManager.setFinancesEnabled(true)
        ensureDefaultFinanceCategories()
        com.example.data.backup.FinanceLinkWorker.syncSchedule(getApplication(), true)
    }

    fun setFinanceDefaultCurrency(currencyCode: String) {
        preferencesManager.setFinanceDefaultCurrency(currencyCode)
    }

    fun setFinanceCurrentBalance(desiredBalanceMinor: Long) {
        val netEntries = financeEntries.value.sumOf {
            if (it.type == FinanceEntryType.INCOME.id) it.totalAmountMinor else -it.totalAmountMinor
        }
        preferencesManager.setFinanceBaseBalanceMinor(desiredBalanceMinor - netEntries)
    }

    fun setFinanceHideValues(hide: Boolean) {
        preferencesManager.setFinanceHideValues(hide)
    }

    fun setFinanceLock(pinCode: String?, biometricEnabled: Boolean) {
        preferencesManager.setFinancePinCode(pinCode?.takeIf { it.isNotBlank() })
        preferencesManager.setFinanceBiometricEnabled(biometricEnabled)
    }

    fun saveFinanceEntry(entry: FinanceEntry, isNew: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            if (isNew) {
                database.financeDao().insertEntry(entry.copy(createdAt = now, updatedAt = now))
            } else {
                database.financeDao().updateEntry(entry.copy(updatedAt = now))
            }
        }
    }

    fun duplicateFinanceEntry(entry: FinanceEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            database.financeDao().insertEntry(
                entry.copy(
                    id = java.util.UUID.randomUUID().toString(),
                    dateMillis = now,
                    createdAt = now,
                    updatedAt = now,
                    deletedAt = null
                )
            )
            _userMessage.emit("Lançamento '${entry.name}' duplicado.")
        }
    }

    fun deleteFinanceEntry(entry: FinanceEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            database.financeDao().softDeleteEntry(entry.id, System.currentTimeMillis())
            _userMessage.emit("Lançamento '${entry.name}' removido.")
        }
    }

    fun saveFinanceCategory(category: FinanceCategory) {
        viewModelScope.launch(Dispatchers.IO) {
            database.financeDao().insertCategory(
                category.copy(updatedAt = System.currentTimeMillis())
            )
        }
    }

    fun archiveFinanceCategory(category: FinanceCategory, archived: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            database.financeDao().updateCategory(
                category.copy(isArchived = archived, updatedAt = System.currentTimeMillis())
            )
        }
    }

    fun eraseAllFinanceData() {
        viewModelScope.launch(Dispatchers.IO) {
            database.financeDao().deleteAllEntries()
            database.financeDao().deleteAllCategories()
            database.financeDao().deleteAllLinks()
            database.financeDao().deleteAllExecutions()
            preferencesManager.resetFinanceDataPreferences()
            com.example.data.backup.FinanceLinkWorker.syncSchedule(getApplication(), false)
            if (financesEnabled.value) {
                database.financeDao().insertCategories(FinanceCategoryIcons.defaultCategories())
            }
            _userMessage.emit("Todos os dados de finanças foram apagados.")
        }
    }

    // Bloco D: Finance Note Links
    fun runDueFinanceLinksOnStartup() {
        if (!preferencesManager.financesEnabled.value) return
        viewModelScope.launch(Dispatchers.IO) {
            com.example.data.backup.FinanceLinkWorker.syncSchedule(getApplication(), true)
            com.example.data.backup.FinanceLinkExecutor.runDueLinks(getApplication())
        }
    }

    fun setActiveEditorNote(note: Note?) {
        com.example.data.backup.FinanceLinkExecutor.activeEditorNoteUuid = note?.uuid
        com.example.data.backup.FinanceLinkExecutor.activeEditorNoteId = note?.id
        if (note == null && preferencesManager.financesEnabled.value) {
            runDueFinanceLinksOnStartup()
        }
    }

    fun createAndRunFinanceNoteLink(
        link: FinanceNoteLink,
        createNewNote: Boolean,
        newNoteTitle: String,
        template: FinanceNoteTemplate?
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val finalLink = if (createNewNote) {
                val newUuid = java.util.UUID.randomUUID().toString()
                val startMarker = com.example.data.backup.FinanceNoteMarkdownEngine.buildStartMarker(link)
                val endMarker = com.example.data.backup.FinanceNoteMarkdownEngine.buildEndMarker(link.blockId)
                val initialNote = Note(
                    uuid = newUuid,
                    title = newNoteTitle,
                    content = "# $newNoteTitle\n\n",
                    category = "Finanças",
                    iconEmoji = "💰",
                    updatedAt = now
                )
                val newId = database.noteDao().insertNote(initialNote)
                link.copy(noteId = newId, noteUuid = newUuid, createdAt = now, updatedAt = now)
            } else {
                link.copy(createdAt = now, updatedAt = now)
            }

            database.financeDao().insertLink(finalLink)
            val outcome = com.example.data.backup.FinanceLinkExecutor.executeLink(
                context = getApplication(),
                link = finalLink,
                isManualTrigger = true,
                nowMillis = now
            )
            val finalNote = database.noteDao().getNoteById(finalLink.noteId)
            if (finalNote != null) {
                _pendingOpenNoteFromFinances.value = finalNote
            }
            emitLinkOutcomeMessage(outcome)
        }
    }

    fun runFinanceLinkNow(link: FinanceNoteLink) {
        viewModelScope.launch(Dispatchers.IO) {
            val outcome = com.example.data.backup.FinanceLinkExecutor.executeLink(
                context = getApplication(),
                link = link,
                isManualTrigger = true
            )
            emitLinkOutcomeMessage(outcome)
        }
    }

    fun undoFinanceLinkLastExecution(link: FinanceNoteLink) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = com.example.data.backup.FinanceLinkExecutor.undoLastExecution(getApplication(), link)
            if (ok) {
                _userMessage.emit("Última atualização da nota desfeita!")
            } else {
                _userMessage.emit("Nenhuma atualização anterior disponível para desfazer.")
            }
        }
    }

    fun toggleFinanceLinkPause(link: FinanceNoteLink) {
        viewModelScope.launch(Dispatchers.IO) {
            val nextStatus = if (link.status == "ACTIVE") "PAUSED" else "ACTIVE"
            database.financeDao().updateLink(
                link.copy(status = nextStatus, updatedAt = System.currentTimeMillis())
            )
        }
    }

    fun recreateMarkersForFinanceLink(link: FinanceNoteLink) {
        viewModelScope.launch(Dispatchers.IO) {
            val outcome = com.example.data.backup.FinanceLinkExecutor.recreateMarkersForPausedLink(getApplication(), link)
            emitLinkOutcomeMessage(outcome)
        }
    }

    fun recreateNoteForFinanceLink(link: FinanceNoteLink) {
        viewModelScope.launch(Dispatchers.IO) {
            val outcome = com.example.data.backup.FinanceLinkExecutor.recreateNoteForPausedLink(getApplication(), link)
            emitLinkOutcomeMessage(outcome)
        }
    }

    fun deleteFinanceNoteLink(link: FinanceNoteLink) {
        viewModelScope.launch(Dispatchers.IO) {
            database.financeDao().deleteLinkById(link.id)
            _userMessage.emit("Vínculo removido.")
        }
    }

    private suspend fun emitLinkOutcomeMessage(outcome: com.example.data.backup.LinkExecutionOutcome) {
        when (outcome) {
            is com.example.data.backup.LinkExecutionOutcome.Success ->
                _userMessage.emit("Nota '${outcome.noteTitle}' atualizada (${outcome.entriesCount} lançamentos).")
            is com.example.data.backup.LinkExecutionOutcome.DeferredNoteOpen ->
                _userMessage.emit("Nota aberta no editor. A atualização ocorrerá ao fechar.")
            is com.example.data.backup.LinkExecutionOutcome.PausedNoteDeleted ->
                _userMessage.emit("Nota apagada: vínculo pausado. Pode recriar a nota em Gerir vínculos.")
            is com.example.data.backup.LinkExecutionOutcome.PausedMarkersDeleted ->
                _userMessage.emit("Marcadores apagados: vínculo pausado. Pode recriar o bloco em Gerir vínculos.")
            is com.example.data.backup.LinkExecutionOutcome.ModuleDisabled ->
                _userMessage.emit("Ative o módulo de Finanças para atualizar vínculos.")
            is com.example.data.backup.LinkExecutionOutcome.Error ->
                _userMessage.emit(outcome.message)
        }
    }

    fun emitUserMessage(message: String) {
        viewModelScope.launch {
            _userMessage.emit(message)
        }
    }

    // ==================== BLOCO E: WALLETS, MULTI-CURRENCY, BUDGETS, RECURRING, PLANNED VS REAL ====================

    fun saveFinanceWallet(wallet: FinanceWallet) {
        viewModelScope.launch(Dispatchers.IO) {
            database.financeDao().insertWallet(wallet.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    fun deleteFinanceWallet(wallet: FinanceWallet) {
        viewModelScope.launch(Dispatchers.IO) {
            database.financeDao().deleteWalletById(wallet.id)
        }
    }

    fun transferBetweenWallets(
        fromWallet: String,
        toWallet: String,
        amountMinor: Long,
        currencyCode: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val outEntry = FinanceEntry(
                type = FinanceEntryType.EXPENSE.id,
                name = "Transferência para $toWallet",
                comment = "Transferência entre carteiras",
                categoryId = "transfer_out",
                walletName = fromWallet,
                currencyCode = currencyCode,
                dateMillis = now,
                totalAmountMinor = amountMinor,
                priority = FinancePriority.MANDATORY.id,
                createdAt = now,
                updatedAt = now
            )
            val inEntry = FinanceEntry(
                type = FinanceEntryType.INCOME.id,
                name = "Transferência de $fromWallet",
                comment = "Transferência entre carteiras",
                categoryId = "transfer_in",
                walletName = toWallet,
                currencyCode = currencyCode,
                dateMillis = now,
                totalAmountMinor = amountMinor,
                priority = FinancePriority.MANDATORY.id,
                createdAt = now,
                updatedAt = now
            )
            database.financeDao().insertEntries(listOf(outEntry, inEntry))
            val curr = FinanceCurrency.fromCode(currencyCode)
            _userMessage.emit("Transferido ${formatFinanceAmount(amountMinor, curr)} de $fromWallet para $toWallet.")
        }
    }

    fun saveFinanceExchangeRate(fromCurrency: String, toCurrency: String, rate: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            database.financeDao().insertExchangeRate(
                FinanceExchangeRate(
                    fromCurrency = fromCurrency,
                    toCurrency = toCurrency,
                    rate = rate,
                    recordedAt = now,
                    updatedAt = now
                )
            )
            _userMessage.emit("Taxa guardada: 1 $fromCurrency = $rate $toCurrency")
        }
    }

    fun deleteFinanceExchangeRate(rate: FinanceExchangeRate) {
        viewModelScope.launch(Dispatchers.IO) {
            database.financeDao().deleteExchangeRateById(rate.id)
        }
    }

    fun saveFinanceBudget(categoryId: String, limitMinor: Long, currencyCode: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = database.financeDao().getAllBudgetsSync().find { it.categoryId == categoryId }
            val budget = (existing ?: FinanceBudget(categoryId = categoryId, limitMinor = limitMinor)).copy(
                limitMinor = limitMinor,
                currencyCode = currencyCode,
                updatedAt = System.currentTimeMillis()
            )
            database.financeDao().insertBudget(budget)
            checkBudgetAlerts()
        }
    }

    fun deleteFinanceBudget(budget: FinanceBudget) {
        viewModelScope.launch(Dispatchers.IO) {
            database.financeDao().deleteBudgetById(budget.id)
        }
    }

    fun checkBudgetAlerts() {
        viewModelScope.launch(Dispatchers.IO) {
            val budgets = database.financeDao().getAllBudgetsSync()
            if (budgets.isEmpty()) return@launch
            val entries = database.financeDao().getAllEntriesIncludingDeleted()
                .filter { it.deletedAt == null && !it.isPlanned && it.type == FinanceEntryType.EXPENSE.id }
            val rates = database.financeDao().getAllExchangeRatesSync()
            val categories = database.financeDao().getAllCategoriesSync().associateBy { it.id }

            val cal = java.util.Calendar.getInstance()
            val year = cal.get(java.util.Calendar.YEAR)
            val month = cal.get(java.util.Calendar.MONTH)
            val monthKey = String.format(java.util.Locale.US, "%04d-%02d", year, month + 1)
            cal.set(year, month, 1, 0, 0, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            val monthStart = cal.timeInMillis

            budgets.forEach { b ->
                val spent = entries
                    .filter { it.categoryId == b.categoryId && it.dateMillis >= monthStart }
                    .sumOf { convertFinanceMinorUnits(it.totalAmountMinor, it.currencyCode, b.currencyCode, rates) }
                val pct = if (b.limitMinor > 0L) ((spent * 100L) / b.limitMinor).toInt() else 0
                val catName = categories[b.categoryId]?.name ?: "Categoria"
                if (pct >= 100 && b.alert100SentMonth != monthKey) {
                    database.financeDao().insertBudget(b.copy(alert100SentMonth = monthKey, alert80SentMonth = monthKey))
                    com.example.notifications.NotificationHelper.showInstantNotification(
                        context = getApplication(),
                        id = ("budget_100_${b.id}").hashCode(),
                        channelId = com.example.notifications.NotificationHelper.CHANNEL_ALERTS,
                        title = "Orçamento atingido (100%)",
                        message = "A categoria $catName atingiu $pct% do orçamento mensal."
                    )
                    _userMessage.emit("⚠️ Orçamento de $catName atingiu $pct%!")
                } else if (pct >= 80 && b.alert80SentMonth != monthKey) {
                    database.financeDao().insertBudget(b.copy(alert80SentMonth = monthKey))
                    com.example.notifications.NotificationHelper.showInstantNotification(
                        context = getApplication(),
                        id = ("budget_80_${b.id}").hashCode(),
                        channelId = com.example.notifications.NotificationHelper.CHANNEL_ALERTS,
                        title = "Alerta de orçamento (80%)",
                        message = "A categoria $catName já consumiu $pct% do limite mensal."
                    )
                    _userMessage.emit("Aviso: $catName atingiu $pct% do orçamento mensal.")
                }
            }
        }
    }

    fun saveFinanceRecurringRule(rule: FinanceRecurringRule) {
        viewModelScope.launch(Dispatchers.IO) {
            database.financeDao().insertRecurringRule(rule.copy(updatedAt = System.currentTimeMillis()))
            processDueRecurringRules()
        }
    }

    fun deleteFinanceRecurringRule(rule: FinanceRecurringRule) {
        viewModelScope.launch(Dispatchers.IO) {
            database.financeDao().deleteRecurringRuleById(rule.id)
        }
    }

    fun processDueRecurringRules() {
        if (!preferencesManager.financesEnabled.value) return
        viewModelScope.launch(Dispatchers.IO) {
            val rules = database.financeDao().getAllRecurringRulesSync()
            val now = System.currentTimeMillis()
            var insertedCount = 0
            rules.filter { it.isActive }.forEach { rule ->
                var currentDue = rule.nextDueMillis
                var updatedRule = rule
                while (currentDue <= now) {
                    database.financeDao().insertEntry(
                        FinanceEntry(
                            type = rule.type,
                            name = rule.name,
                            comment = "Lançamento recorrente",
                            categoryId = rule.categoryId,
                            walletName = rule.walletName,
                            currencyCode = rule.currencyCode,
                            dateMillis = currentDue,
                            totalAmountMinor = rule.amountMinor,
                            priority = rule.priority,
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                    insertedCount++
                    val cal = java.util.Calendar.getInstance().apply { timeInMillis = currentDue }
                    when (FinanceLinkFrequency.fromId(rule.frequency)) {
                        FinanceLinkFrequency.DAILY -> cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                        FinanceLinkFrequency.WEEKLY -> cal.add(java.util.Calendar.WEEK_OF_YEAR, 1)
                        FinanceLinkFrequency.ANNUAL -> cal.add(java.util.Calendar.YEAR, 1)
                        else -> cal.add(java.util.Calendar.MONTH, 1)
                    }
                    currentDue = cal.timeInMillis
                    updatedRule = updatedRule.copy(nextDueMillis = currentDue, updatedAt = now)
                }

                // Advance reminder check
                val remMillis = updatedRule.reminderDaysBefore * 86_400_000L
                if (updatedRule.reminderDaysBefore > 0 &&
                    (updatedRule.nextDueMillis - now) in 1L..remMillis &&
                    updatedRule.lastRemindedDueMillis != updatedRule.nextDueMillis
                ) {
                    val curr = FinanceCurrency.fromCode(updatedRule.currencyCode)
                    com.example.notifications.NotificationHelper.showInstantNotification(
                        context = getApplication(),
                        id = ("rec_${updatedRule.id}").hashCode(),
                        channelId = com.example.notifications.NotificationHelper.CHANNEL_ALERTS,
                        title = "Lembrete financeiro: ${updatedRule.name}",
                        message = "Vence em breve (${formatFinanceAmount(updatedRule.amountMinor, curr)})."
                    )
                    updatedRule = updatedRule.copy(lastRemindedDueMillis = updatedRule.nextDueMillis, updatedAt = now)
                }

                if (updatedRule != rule) {
                    database.financeDao().insertRecurringRule(updatedRule)
                }
            }
            if (insertedCount > 0) {
                _userMessage.emit("$insertedCount lançamento(s) recorrente(s) registado(s).")
                checkBudgetAlerts()
                }
        }
    }

    fun completePlannedEntry(entry: FinanceEntry, actualAmountMinor: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val planned = entry.plannedAmountMinor ?: entry.totalAmountMinor
            val updated = entry.copy(
                isPlanned = false,
                plannedAmountMinor = planned,
                totalAmountMinor = actualAmountMinor,
                dateMillis = now,
                updatedAt = now
            )
            database.financeDao().updateEntry(updated)
            val curr = FinanceCurrency.fromCode(entry.currencyCode)
            val diff = planned - actualAmountMinor
            val msg = when {
                diff > 0L -> "${entry.name}: previsto ${formatFinanceAmount(planned, curr)}, pago ${formatFinanceAmount(actualAmountMinor, curr)} (poupou ${formatFinanceAmount(diff, curr)})."
                diff < 0L -> "${entry.name}: previsto ${formatFinanceAmount(planned, curr)}, pago ${formatFinanceAmount(actualAmountMinor, curr)} (+${formatFinanceAmount(-diff, curr)})."
                else -> "${entry.name} marcado como pago (${formatFinanceAmount(actualAmountMinor, curr)})."
            }
            _userMessage.emit(msg)
            checkBudgetAlerts()
        }
    }
}
