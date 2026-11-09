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
    val ambientMusicVolume: Float = 0.5f,
    val overrideSilentMode: Boolean = false
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

    // Contextual Surface Spotlight Tutorials
    private val _allTutorialsSkipped = MutableStateFlow(prefs.getBoolean("all_tutorials_skipped", false))
    val allTutorialsSkipped: StateFlow<Boolean> = _allTutorialsSkipped.asStateFlow()

    private val _completedTutorialAreas = MutableStateFlow(
        prefs.getStringSet("completed_tutorial_areas", emptySet()) ?: emptySet()
    )
    val completedTutorialAreas: StateFlow<Set<String>> = _completedTutorialAreas.asStateFlow()

    // Sync Enabled & Frequency: "1_HOUR", "DAILY", "WEEKLY", "ON_APP_OPEN"
    private val _syncEnabled = MutableStateFlow(prefs.getBoolean("sync_enabled", true))
    val syncEnabled: StateFlow<Boolean> = _syncEnabled.asStateFlow()

    private val _syncFrequency = MutableStateFlow(prefs.getString("sync_frequency", "DAILY") ?: "DAILY")
    val syncFrequency: StateFlow<String> = _syncFrequency.asStateFlow()

    // App Global Background Settings (Wallpaper, Blur, Darkness)
    private val _appBgUri = MutableStateFlow<String?>(prefs.getString("app_bg_uri", null))
    val appBgUri: StateFlow<String?> = _appBgUri.asStateFlow()

    private val _appBgBlur = MutableStateFlow(prefs.getFloat("app_bg_blur", 0.5f))
    val appBgBlur: StateFlow<Float> = _appBgBlur.asStateFlow()

    private val _appBgBrightness = MutableStateFlow(prefs.getFloat("app_bg_brightness", 0.6f))
    val appBgBrightness: StateFlow<Float> = _appBgBrightness.asStateFlow()

    // Timer Citra Physics Balls (JSON array of diameters/durations)
    private val _citraTimerBallsJson = MutableStateFlow(prefs.getString("citra_timer_balls_json", "[]") ?: "[]")
    val citraTimerBallsJson: StateFlow<String> = _citraTimerBallsJson.asStateFlow()

    // Daily Welcome Phrases
    private val _welcomePhrasesEnabled = MutableStateFlow(prefs.getBoolean("welcome_phrases_enabled", true))
    val welcomePhrasesEnabled: StateFlow<Boolean> = _welcomePhrasesEnabled.asStateFlow()

    private val _disabledWelcomeCategories = MutableStateFlow(
        prefs.getStringSet("disabled_welcome_categories", emptySet()) ?: emptySet()
    )
    val disabledWelcomeCategories: StateFlow<Set<String>> = _disabledWelcomeCategories.asStateFlow()

    private val _dailyWelcomePhrase = MutableStateFlow(resolveDailyWelcomePhrase())
    val dailyWelcomePhrase: StateFlow<String> = _dailyWelcomePhrase.asStateFlow()

    // Finance Module Preferences (disabled by default)
    private val _financesEnabled = MutableStateFlow(prefs.getBoolean("finances_enabled", false))
    val financesEnabled: StateFlow<Boolean> = _financesEnabled.asStateFlow()

    private val _financesSetupCompleted = MutableStateFlow(prefs.getBoolean("finances_setup_completed", false))
    val financesSetupCompleted: StateFlow<Boolean> = _financesSetupCompleted.asStateFlow()

    private val _financeDefaultCurrency = MutableStateFlow(prefs.getString("finance_default_currency", "AOA") ?: "AOA")
    val financeDefaultCurrency: StateFlow<String> = _financeDefaultCurrency.asStateFlow()

    private val _financeBaseBalanceMinor = MutableStateFlow(prefs.getLong("finance_base_balance_minor", 0L))
    val financeBaseBalanceMinor: StateFlow<Long> = _financeBaseBalanceMinor.asStateFlow()

    private val _financeHideValues = MutableStateFlow(prefs.getBoolean("finance_hide_values", false))
    val financeHideValues: StateFlow<Boolean> = _financeHideValues.asStateFlow()

    private val _financePinCode = MutableStateFlow<String?>(prefs.getString("finance_pin_code", null))
    val financePinCode: StateFlow<String?> = _financePinCode.asStateFlow()

    private val _financeBiometricEnabled = MutableStateFlow(prefs.getBoolean("finance_biometric_enabled", false))
    val financeBiometricEnabled: StateFlow<Boolean> = _financeBiometricEnabled.asStateFlow()

    // Distraction-Free Focus Mode (Modo Sem Perturbações)
    private val _focusDistractionFreeEnabled = MutableStateFlow(prefs.getBoolean("focus_distraction_free_enabled", false))
    val focusDistractionFreeEnabled: StateFlow<Boolean> = _focusDistractionFreeEnabled.asStateFlow()

    private val _focusAllowedPackages = MutableStateFlow(
        prefs.getStringSet("focus_allowed_packages", emptySet()) ?: emptySet()
    )
    val focusAllowedPackages: StateFlow<Set<String>> = _focusAllowedPackages.asStateFlow()

    fun setFocusDistractionFreeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("focus_distraction_free_enabled", enabled).apply()
        _focusDistractionFreeEnabled.value = enabled
    }

    fun setFocusAllowedPackages(packages: Set<String>) {
        prefs.edit().putStringSet("focus_allowed_packages", packages).apply()
        _focusAllowedPackages.value = packages
    }

    fun toggleFocusAllowedPackage(packageName: String) {
        val current = _focusAllowedPackages.value.toMutableSet()
        if (current.contains(packageName)) {
            current.remove(packageName)
        } else {
            current.add(packageName)
        }
        setFocusAllowedPackages(current)
    }

    private fun resolveDailyWelcomePhrase(forceNew: Boolean = false): String {
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val lastDate = prefs.getString("last_welcome_date", null)
        val savedPhrase = prefs.getString("current_daily_phrase", null)
        val disabled = prefs.getStringSet("disabled_welcome_categories", emptySet()) ?: emptySet()

        if (!forceNew && lastDate == todayStr && !savedPhrase.isNullOrBlank()) {
            val matching = DailyWelcomePhrasesCatalog.allPhrases.find { it.text == savedPhrase }
            if (matching == null || !disabled.contains(matching.category.id)) {
                return savedPhrase
            }
        }

        val pool = DailyWelcomePhrasesCatalog.allPhrases.filter { !disabled.contains(it.category.id) }
            .ifEmpty { DailyWelcomePhrasesCatalog.allPhrases }

        val usedSet = (prefs.getStringSet("used_welcome_phrases", emptySet()) ?: emptySet()).toMutableSet()
        var available = pool.filter { !usedSet.contains(it.id) }
        if (available.isEmpty()) {
            usedSet.clear()
            available = pool
        }

        val picked = available.random()
        usedSet.add(picked.id)

        prefs.edit()
            .putString("last_welcome_date", todayStr)
            .putString("current_daily_phrase", picked.text)
            .putStringSet("used_welcome_phrases", usedSet)
            .apply()

        return picked.text
    }

    fun setWelcomePhrasesEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("welcome_phrases_enabled", enabled).apply()
        _welcomePhrasesEnabled.value = enabled
        if (enabled) {
            _dailyWelcomePhrase.value = resolveDailyWelcomePhrase(forceNew = false)
        }
    }

    fun toggleWelcomeCategory(categoryId: String) {
        val current = _disabledWelcomeCategories.value.toMutableSet()
        if (current.contains(categoryId)) {
            current.remove(categoryId)
        } else {
            // Keep at least one category enabled
            if (current.size < WelcomePhraseCategory.entries.size - 1) {
                current.add(categoryId)
            }
        }
        prefs.edit().putStringSet("disabled_welcome_categories", current).apply()
        _disabledWelcomeCategories.value = current
        _dailyWelcomePhrase.value = resolveDailyWelcomePhrase(forceNew = false)
    }

    fun setAppBackground(uri: String?, blur: Float = _appBgBlur.value, brightness: Float = _appBgBrightness.value) {
        prefs.edit().apply {
            if (uri != null) putString("app_bg_uri", uri) else remove("app_bg_uri")
            putFloat("app_bg_blur", blur)
            putFloat("app_bg_brightness", brightness)
        }.commit()
        _appBgUri.value = uri
        _appBgBlur.value = blur
        _appBgBrightness.value = brightness
    }

    fun clearAppBackground() {
        try {
            val bgFile = java.io.File(context.filesDir, "app_background.jpg")
            if (bgFile.exists()) bgFile.delete()
        } catch (_: Exception) {}
        setAppBackground(null, 0.5f, 0.6f)
    }

    fun saveCitraTimerBallsJson(json: String) {
        prefs.edit().putString("citra_timer_balls_json", json).apply()
        _citraTimerBallsJson.value = json
    }

    fun addCitraTimerBall(durationMinutes: Int) {
        val safeMins = durationMinutes.coerceIn(1, 180)
        try {
            val current = prefs.getString("citra_timer_balls_json", "[]") ?: "[]"
            val arr = org.json.JSONArray(current)
            arr.put(safeMins)
            val updated = arr.toString()
            prefs.edit().putString("citra_timer_balls_json", updated).commit()
            _citraTimerBallsJson.value = updated
        } catch (_: Exception) {
            val arr = org.json.JSONArray().apply { put(safeMins) }
            val updated = arr.toString()
            prefs.edit().putString("citra_timer_balls_json", updated).commit()
            _citraTimerBallsJson.value = updated
        }
    }

    fun clearCitraTimerBalls() {
        prefs.edit().putString("citra_timer_balls_json", "[]").commit()
        _citraTimerBallsJson.value = "[]"
    }

    fun setSyncEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("sync_enabled", enabled).apply()
        _syncEnabled.value = enabled
    }

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
            com.example.notifications.NotificationHelper.scheduleDailyMidnightColorAlarm(context)
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
            ambientMusicVolume = prefs.getFloat("ambient_music_volume", 0.5f),
            overrideSilentMode = prefs.getBoolean("override_silent_mode", false)
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
            "override_silent_mode" to s.overrideSilentMode,
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
            ambientMusicVolume = ((map["ambient_music_volume"] as? Number)?.toFloat()) ?: curSettings.ambientMusicVolume,
            overrideSilentMode = (map["override_silent_mode"] as? Boolean) ?: curSettings.overrideSilentMode
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
            putBoolean("override_silent_mode", newSettings.overrideSilentMode)
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

    fun isTutorialAreaCompleted(areaId: String): Boolean {
        return _allTutorialsSkipped.value || _completedTutorialAreas.value.contains(areaId)
    }

    fun markTutorialAreaCompleted(areaId: String) {
        val current = _completedTutorialAreas.value.toMutableSet()
        current.add(areaId)
        prefs.edit().putStringSet("completed_tutorial_areas", current).apply()
        _completedTutorialAreas.value = current
    }

    fun skipAllTutorials() {
        prefs.edit().putBoolean("all_tutorials_skipped", true).apply()
        _allTutorialsSkipped.value = true
    }

    fun resetAllTutorials() {
        prefs.edit()
            .putBoolean("all_tutorials_skipped", false)
            .remove("completed_tutorial_areas")
            .apply()
        _allTutorialsSkipped.value = false
        _completedTutorialAreas.value = emptySet()
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
            putLong("timer_target_end_ts", snapshot.targetEndTimeMillis)
            putBoolean("timer_is_stopwatch", snapshot.isStopwatchMode)
            putLong("timer_stopwatch_start_ts", snapshot.stopwatchStartTimeMillis)
            putInt("timer_stopwatch_accum_secs", snapshot.stopwatchAccumulatedSeconds)
            apply()
        }
    }

    fun loadTimerSnapshot(): SavedTimerSnapshot? {
        if (!prefs.contains("timer_rem_secs") && !prefs.contains("timer_is_stopwatch")) return null
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
            lastTimestamp = prefs.getLong("timer_last_ts", 0L),
            targetEndTimeMillis = prefs.getLong("timer_target_end_ts", 0L),
            isStopwatchMode = prefs.getBoolean("timer_is_stopwatch", false),
            stopwatchStartTimeMillis = prefs.getLong("timer_stopwatch_start_ts", 0L),
            stopwatchAccumulatedSeconds = prefs.getInt("timer_stopwatch_accum_secs", 0)
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

    fun setFinancesEnabled(enabled: Boolean) {
        _financesEnabled.value = enabled
        prefs.edit().putBoolean("finances_enabled", enabled).apply()
    }

    fun setFinancesSetupCompleted(completed: Boolean) {
        _financesSetupCompleted.value = completed
        prefs.edit().putBoolean("finances_setup_completed", completed).apply()
    }

    fun setFinanceDefaultCurrency(currencyCode: String) {
        _financeDefaultCurrency.value = currencyCode
        prefs.edit().putString("finance_default_currency", currencyCode).apply()
    }

    fun setFinanceBaseBalanceMinor(balanceMinor: Long) {
        _financeBaseBalanceMinor.value = balanceMinor
        prefs.edit().putLong("finance_base_balance_minor", balanceMinor).apply()
    }

    fun setFinanceHideValues(hide: Boolean) {
        _financeHideValues.value = hide
        prefs.edit().putBoolean("finance_hide_values", hide).apply()
    }

    fun setFinancePinCode(pin: String?) {
        _financePinCode.value = pin
        prefs.edit().putString("finance_pin_code", pin).apply()
    }

    fun setFinanceBiometricEnabled(enabled: Boolean) {
        _financeBiometricEnabled.value = enabled
        prefs.edit().putBoolean("finance_biometric_enabled", enabled).apply()
    }

    private val _lastFinanceSyncTimestamp = MutableStateFlow(prefs.getLong("last_finance_sync_timestamp", 0L))
    val lastFinanceSyncTimestamp: StateFlow<Long> = _lastFinanceSyncTimestamp.asStateFlow()

    fun setLastFinanceSyncTimestamp(timestamp: Long = System.currentTimeMillis()) {
        _lastFinanceSyncTimestamp.value = timestamp
        prefs.edit().putLong("last_finance_sync_timestamp", timestamp).apply()
    }

    // Default note open mode (EDIT or READ)
    private val _defaultNoteOpenMode = MutableStateFlow(prefs.getString("default_note_open_mode", "EDIT") ?: "EDIT")
    val defaultNoteOpenMode: StateFlow<String> = _defaultNoteOpenMode.asStateFlow()

    fun setDefaultNoteOpenMode(mode: String) {
        _defaultNoteOpenMode.value = mode
        prefs.edit().putString("default_note_open_mode", mode).apply()
    }

    // Last used focus session minutes (remember size of last session)
    private val _lastUsedFocusMinutes = MutableStateFlow(prefs.getInt("last_used_focus_mins", 15))
    val lastUsedFocusMinutes: StateFlow<Int> = _lastUsedFocusMinutes.asStateFlow()

    fun saveLastUsedFocusMinutes(minutes: Int) {
        val safe = minutes.coerceIn(1, 180)
        _lastUsedFocusMinutes.value = safe
        prefs.edit().putInt("last_used_focus_mins", safe).apply()
    }

    fun getSavedToolbarPosition(): Pair<Float, Float> {
        val x = prefs.getFloat("saved_tb_offset_x", 0f)
        val y = prefs.getFloat("saved_tb_offset_y", 0f)
        return Pair(x, y)
    }

    fun saveToolbarPosition(x: Float, y: Float) {
        prefs.edit().putFloat("saved_tb_offset_x", x).putFloat("saved_tb_offset_y", y).apply()
    }

    fun resetFinanceDataPreferences() {
        _financeBaseBalanceMinor.value = 0L
        _financeHideValues.value = false
        _financePinCode.value = null
        _financeBiometricEnabled.value = false
        _financesSetupCompleted.value = false
        prefs.edit()
            .putLong("finance_base_balance_minor", 0L)
            .putBoolean("finance_hide_values", false)
            .remove("finance_pin_code")
            .putBoolean("finance_biometric_enabled", false)
            .putBoolean("finances_setup_completed", false)
            .apply()
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
    val lastTimestamp: Long = 0L,
    val targetEndTimeMillis: Long = 0L,
    val isStopwatchMode: Boolean = false,
    val stopwatchStartTimeMillis: Long = 0L,
    val stopwatchAccumulatedSeconds: Int = 0
)
