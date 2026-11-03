package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.ui.theme.ThemeAccent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

import com.example.util.SoundEffectsManager

data class NotificationSettings(
    val taskSound: String = SoundEffectsManager.DEFAULT_TASK_SOUND_ID,
    val taskSoundUri: String = "",
    val habitSound: String = SoundEffectsManager.DEFAULT_HABIT_SOUND_ID,
    val habitSoundUri: String = "",
    val pomodoroSound: String = SoundEffectsManager.DEFAULT_POMODORO_SOUND_ID,
    val pomodoroSoundUri: String = "",
    val achievementSound: String = SoundEffectsManager.DEFAULT_ACHIEVEMENT_SOUND_ID,
    val alertSound: String = SoundEffectsManager.DEFAULT_NOTIFICATION_SOUND_ID,
    val vibrate: Boolean = true,
    val headsUpBanner: Boolean = true,
    val notifyAchievements: Boolean = true,
    val notifyMissedHabits: Boolean = true,
    val ambientBreakMusicInApp: Boolean = false,
    val ambientMusicVolume: Float = 0.5f
)

class PreferencesManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("citra_prefs", Context.MODE_PRIVATE)

    // Theme Accent
    private val _themeAccent = MutableStateFlow(loadThemeAccent())
    val themeAccent: StateFlow<ThemeAccent> = _themeAccent.asStateFlow()

    // Daily Random Theme Accent (at 00h / once per day)
    private val _dailyRandomColor = MutableStateFlow(prefs.getBoolean("daily_random_color", false))
    val dailyRandomColor: StateFlow<Boolean> = _dailyRandomColor.asStateFlow()

    // Persistent Theme Mode: null = system, true = dark, false = light
    private val _themeMode = MutableStateFlow<Boolean?>(loadThemeMode())
    val themeMode: StateFlow<Boolean?> = _themeMode.asStateFlow()

    // Default start tab: "HOME", "TIMER", "ORGANIZATION", "NOTES", "CALENDAR", "STATS"
    private val _defaultStartTab = MutableStateFlow(prefs.getString("default_start_tab", "HOME") ?: "HOME")
    val defaultStartTab: StateFlow<String> = _defaultStartTab.asStateFlow()

    // Keep screen on during timer
    private val _keepScreenOn = MutableStateFlow(prefs.getBoolean("keep_screen_on", true))
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    // Notification settings
    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<NotificationSettings> = _settings.asStateFlow()

    private val _hasSeenTutorial = MutableStateFlow(prefs.getBoolean("has_seen_tutorial", true))
    val hasSeenTutorial: StateFlow<Boolean> = _hasSeenTutorial.asStateFlow()

    // Sync Frequency: "1_HOUR", "DAILY", "WEEKLY", "ON_APP_OPEN"
    private val _syncFrequency = MutableStateFlow(prefs.getString("sync_frequency", "DAILY") ?: "DAILY")
    val syncFrequency: StateFlow<String> = _syncFrequency.asStateFlow()

    fun setSyncFrequency(frequency: String) {
        prefs.edit().putString("sync_frequency", frequency).apply()
        _syncFrequency.value = frequency
    }

    fun getLastSyncTimestamp(): Long {
        return prefs.getLong("last_sync_timestamp", 0L)
    }

    fun setLastSyncTimestamp(timestamp: Long = System.currentTimeMillis()) {
        prefs.edit().putLong("last_sync_timestamp", timestamp).apply()
    }

    private fun loadThemeMode(): Boolean? {
        return when (prefs.getString("theme_mode", "SYSTEM")) {
            "DARK" -> true
            "LIGHT" -> false
            else -> null
        }
    }

    private fun loadThemeAccent(): ThemeAccent {
        val dailyEnabled = prefs.getBoolean("daily_random_color", false)
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val lastDate = prefs.getString("last_daily_color_date", "")
        if (dailyEnabled && today != lastDate) {
            val nextAccent = ThemeAccent.entries.random()
            prefs.edit().putString("theme_accent", nextAccent.name).putString("last_daily_color_date", today).apply()
            return nextAccent
        }
        val name = prefs.getString("theme_accent", ThemeAccent.TANGERINA.name) ?: ThemeAccent.TANGERINA.name
        return try {
            ThemeAccent.valueOf(name)
        } catch (_: Exception) {
            ThemeAccent.TANGERINA
        }
    }

    fun setDailyRandomColor(enabled: Boolean) {
        prefs.edit().putBoolean("daily_random_color", enabled).apply()
        _dailyRandomColor.value = enabled
        if (enabled) {
            checkDailyRandomColor(force = true)
        }
    }

    fun checkDailyRandomColor(force: Boolean = false) {
        if (!_dailyRandomColor.value) return
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val lastDate = prefs.getString("last_daily_color_date", "")
        if (force || today != lastDate) {
            val allAccents = ThemeAccent.entries
            val current = _themeAccent.value
            val candidates = allAccents.filter { it != current }.ifEmpty { allAccents }
            val nextAccent = candidates.random()
            setThemeAccent(nextAccent)
            prefs.edit().putString("last_daily_color_date", today).apply()
        }
    }

    private fun loadSettings(): NotificationSettings {
        return NotificationSettings(
            taskSound = prefs.getString("task_sound", SoundEffectsManager.DEFAULT_TASK_SOUND_ID) ?: SoundEffectsManager.DEFAULT_TASK_SOUND_ID,
            taskSoundUri = prefs.getString("task_sound_uri", "") ?: "",
            habitSound = prefs.getString("habit_sound", SoundEffectsManager.DEFAULT_HABIT_SOUND_ID) ?: SoundEffectsManager.DEFAULT_HABIT_SOUND_ID,
            habitSoundUri = prefs.getString("habit_sound_uri", "") ?: "",
            pomodoroSound = prefs.getString("pomodoro_sound", SoundEffectsManager.DEFAULT_POMODORO_SOUND_ID) ?: SoundEffectsManager.DEFAULT_POMODORO_SOUND_ID,
            pomodoroSoundUri = prefs.getString("pomodoro_sound_uri", "") ?: "",
            achievementSound = prefs.getString("achievement_sound", SoundEffectsManager.DEFAULT_ACHIEVEMENT_SOUND_ID) ?: SoundEffectsManager.DEFAULT_ACHIEVEMENT_SOUND_ID,
            alertSound = prefs.getString("alert_sound", SoundEffectsManager.DEFAULT_NOTIFICATION_SOUND_ID) ?: SoundEffectsManager.DEFAULT_NOTIFICATION_SOUND_ID,
            vibrate = prefs.getBoolean("vibrate", true),
            headsUpBanner = prefs.getBoolean("heads_up", true),
            notifyAchievements = prefs.getBoolean("notify_achievements", true),
            notifyMissedHabits = prefs.getBoolean("notify_missed_habits", true),
            ambientBreakMusicInApp = prefs.getBoolean("ambient_music_in_app", false),
            ambientMusicVolume = prefs.getFloat("ambient_music_volume", 0.5f)
        )
    }

    fun setThemeMode(dark: Boolean?) {
        val str = when (dark) {
            true -> "DARK"
            false -> "LIGHT"
            null -> "SYSTEM"
        }
        prefs.edit().putString("theme_mode", str).apply()
        _themeMode.value = dark
    }

    fun setThemeAccent(accent: ThemeAccent) {
        prefs.edit().putString("theme_accent", accent.name).apply()
        _themeAccent.value = accent
    }

    fun exportPreferencesMap(): Map<String, Any> {
        val s = _settings.value
        return mapOf(
            "theme_mode" to (prefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM"),
            "theme_accent" to _themeAccent.value.name,
            "default_start_tab" to _defaultStartTab.value,
            "keep_screen_on" to _keepScreenOn.value,
            "task_sound" to s.taskSound,
            "habit_sound" to s.habitSound,
            "pomodoro_sound" to s.pomodoroSound,
            "achievement_sound" to s.achievementSound,
            "alert_sound" to s.alertSound,
            "vibrate" to s.vibrate,
            "heads_up" to s.headsUpBanner,
            "notify_achievements" to s.notifyAchievements,
            "notify_missed_habits" to s.notifyMissedHabits,
            "ambient_music_in_app" to s.ambientBreakMusicInApp,
            "ambient_music_volume" to s.ambientMusicVolume,
            "daily_random_color" to _dailyRandomColor.value
        )
    }

    fun importPreferencesMap(map: Map<String, Any>) {
        val editor = prefs.edit()
        (map["daily_random_color"] as? Boolean)?.let {
            editor.putBoolean("daily_random_color", it)
            _dailyRandomColor.value = it
        }
        (map["theme_mode"] as? String)?.let {
            editor.putString("theme_mode", it)
            _themeMode.value = when (it) {
                "DARK" -> true
                "LIGHT" -> false
                else -> null
            }
        }
        (map["theme_accent"] as? String)?.let { accentName ->
            try {
                val acc = ThemeAccent.valueOf(accentName)
                editor.putString("theme_accent", acc.name)
                _themeAccent.value = acc
            } catch (_: Exception) {}
        }
        (map["default_start_tab"] as? String)?.let {
            editor.putString("default_start_tab", it)
            _defaultStartTab.value = it
        }
        (map["keep_screen_on"] as? Boolean)?.let {
            editor.putBoolean("keep_screen_on", it)
            _keepScreenOn.value = it
        }

        // Standard notification and sound effects settings
        val curSettings = _settings.value
        val newSettings = curSettings.copy(
            taskSound = (map["task_sound"] as? String) ?: curSettings.taskSound,
            habitSound = (map["habit_sound"] as? String) ?: curSettings.habitSound,
            pomodoroSound = (map["pomodoro_sound"] as? String) ?: curSettings.pomodoroSound,
            achievementSound = (map["achievement_sound"] as? String) ?: curSettings.achievementSound,
            alertSound = (map["alert_sound"] as? String) ?: curSettings.alertSound,
            vibrate = (map["vibrate"] as? Boolean) ?: curSettings.vibrate,
            headsUpBanner = (map["heads_up"] as? Boolean) ?: curSettings.headsUpBanner,
            notifyAchievements = (map["notify_achievements"] as? Boolean) ?: curSettings.notifyAchievements,
            notifyMissedHabits = (map["notify_missed_habits"] as? Boolean) ?: curSettings.notifyMissedHabits,
            ambientBreakMusicInApp = (map["ambient_music_in_app"] as? Boolean) ?: curSettings.ambientBreakMusicInApp,
            ambientMusicVolume = ((map["ambient_music_volume"] as? Number)?.toFloat()) ?: curSettings.ambientMusicVolume
        )
        editor.apply()
        updateSettings(newSettings)
    }

    fun setDefaultStartTab(tab: String) {
        prefs.edit().putString("default_start_tab", tab).apply()
        _defaultStartTab.value = tab
    }

    fun setKeepScreenOn(enabled: Boolean) {
        prefs.edit().putBoolean("keep_screen_on", enabled).apply()
        _keepScreenOn.value = enabled
    }

    fun updateSettings(newSettings: NotificationSettings) {
        prefs.edit().apply {
            putString("task_sound", newSettings.taskSound)
            putString("task_sound_uri", newSettings.taskSoundUri)
            putString("habit_sound", newSettings.habitSound)
            putString("habit_sound_uri", newSettings.habitSoundUri)
            putString("pomodoro_sound", newSettings.pomodoroSound)
            putString("pomodoro_sound_uri", newSettings.pomodoroSoundUri)
            putString("achievement_sound", newSettings.achievementSound)
            putString("alert_sound", newSettings.alertSound)
            putBoolean("vibrate", newSettings.vibrate)
            putBoolean("heads_up", newSettings.headsUpBanner)
            putBoolean("notify_achievements", newSettings.notifyAchievements)
            putBoolean("notify_missed_habits", newSettings.notifyMissedHabits)
            putBoolean("ambient_music_in_app", newSettings.ambientBreakMusicInApp)
            putFloat("ambient_music_volume", newSettings.ambientMusicVolume)
            apply()
        }
        _settings.value = newSettings
        try {
            com.example.notifications.NotificationHelper.prewarmChannelsForNewSettings(context, newSettings)
        } catch (_: Exception) {}
    }

    fun setTutorialSeen(seen: Boolean = true) {
        prefs.edit().putBoolean("has_seen_tutorial", seen).apply()
        _hasSeenTutorial.value = seen
    }

    fun getLastSeenWhatsNewVersion(): String {
        return prefs.getString("whats_new_version", "") ?: ""
    }

    fun setLastSeenWhatsNewVersion(version: String = "0.6.0") {
        prefs.edit().putString("whats_new_version", version).apply()
    }

    // Timer Snapshot Persistence (Protects against app kill, clear from recents, or reboot)
    fun saveTimerSnapshot(snapshot: SavedTimerSnapshot) {
        prefs.edit().apply {
            putInt("timer_focus_mins", snapshot.focusMinutes)
            putInt("timer_break_mins", snapshot.breakMinutes)
            putInt("timer_total_cycles", snapshot.totalCycles)
            putInt("timer_current_cycle", snapshot.currentCycle)
            putInt("timer_rem_secs", snapshot.remainingSeconds)
            putString("timer_phase", snapshot.phaseName)
            putBoolean("timer_running", snapshot.isRunning)
            putBoolean("timer_paused", snapshot.isPaused)
            putString("timer_category", snapshot.selectedCategory)
            if (snapshot.linkedTaskId != null) putLong("timer_linked_task", snapshot.linkedTaskId)
            else remove("timer_linked_task")
            putLong("timer_last_ts", snapshot.lastTimestamp)
            apply()
        }
    }

    fun loadTimerSnapshot(): SavedTimerSnapshot? {
        if (!prefs.contains("timer_rem_secs")) return null
        val linkedTask = if (prefs.contains("timer_linked_task")) prefs.getLong("timer_linked_task", 0L) else null
        return SavedTimerSnapshot(
            focusMinutes = prefs.getInt("timer_focus_mins", 15),
            breakMinutes = prefs.getInt("timer_break_mins", 5),
            totalCycles = prefs.getInt("timer_total_cycles", 1),
            currentCycle = prefs.getInt("timer_current_cycle", 1),
            remainingSeconds = prefs.getInt("timer_rem_secs", 15 * 60),
            phaseName = prefs.getString("timer_phase", "FOCUS") ?: "FOCUS",
            isRunning = prefs.getBoolean("timer_running", false),
            isPaused = prefs.getBoolean("timer_paused", false),
            selectedCategory = prefs.getString("timer_category", "Estudos") ?: "Estudos",
            linkedTaskId = linkedTask,
            lastTimestamp = prefs.getLong("timer_last_ts", 0L)
        )
    }

    fun clearTimerSnapshot() {
        prefs.edit().apply {
            remove("timer_focus_mins")
            remove("timer_break_mins")
            remove("timer_total_cycles")
            remove("timer_current_cycle")
            remove("timer_rem_secs")
            remove("timer_phase")
            remove("timer_running")
            remove("timer_paused")
            remove("timer_category")
            remove("timer_linked_task")
            remove("timer_last_ts")
            apply()
        }
    }

    fun getLastNoteEditorMode(): String {
        return prefs.getString("last_note_editor_mode", "LIVE_PREVIEW") ?: "LIVE_PREVIEW"
    }

    fun setLastNoteEditorMode(mode: String) {
        prefs.edit().putString("last_note_editor_mode", mode).apply()
    }
}

data class SavedTimerSnapshot(
    val focusMinutes: Int = 15,
    val breakMinutes: Int = 5,
    val totalCycles: Int = 1,
    val currentCycle: Int = 1,
    val remainingSeconds: Int = 15 * 60,
    val phaseName: String = "FOCUS",
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val selectedCategory: String = "Estudos",
    val linkedTaskId: Long? = null,
    val lastTimestamp: Long = 0L
)
