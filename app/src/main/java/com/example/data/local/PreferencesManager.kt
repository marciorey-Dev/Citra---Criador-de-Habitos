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
    val overrideSilentMode: Boolean = false,
    val pomodoroDisruptiveAlarm: Boolean = false
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

    // Notification settings (backed by process-wide shared StateFlow)
    val settings: StateFlow<NotificationSettings> get() = sharedSettings.asStateFlow()

    private val _hasSeenTutorial = MutableStateFlow(prefs.getBoolean("has_seen_tutorial", true))
    val hasSeenTutorial: StateFlow<Boolean> = _hasSeenTutorial.asStateFlow()

    // Contextual Surface Spotlight Tutorials
    private val _allTutorialsSkipped = MutableStateFlow(prefs.getBoolean("all_tutorials_skipped", false))
    val allTutorialsSkipped: StateFlow<Boolean> = _allTutorialsSkipped.asStateFlow()

    private val _completedTutorialAreas = MutableStateFlow(
        prefs.getStringSet("completed_tutorial_areas", emptySet()) ?: emptySet()
    )
    val completedTutorialAreas: StateFlow<Set<String>> = _completedTutorialAreas.asStateFlow()

    // Local Offline Profile & First-Launch Onboarding
    private val _hasCompletedOnboarding = MutableStateFlow(prefs.getBoolean("has_completed_onboarding", false))
    val hasCompletedOnboarding: StateFlow<Boolean> = _hasCompletedOnboarding.asStateFlow()

    private val _userDisplayName = MutableStateFlow(prefs.getString("user_display_name", "Viajante Citra") ?: "Viajante Citra")
    val userDisplayName: StateFlow<String> = _userDisplayName.asStateFlow()

    private val _userPhotoPath = MutableStateFlow<String?>(prefs.getString("user_photo_path", null))
    val userPhotoPath: StateFlow<String?> = _userPhotoPath.asStateFlow()

    // App Global Background Settings (Wallpaper, Blur, Darkness)
    private val _appBgUri = MutableStateFlow<String?>(prefs.getString("app_bg_uri", null))
    val appBgUri: StateFlow<String?> = _appBgUri.asStateFlow()

    private val _appBgBlur = MutableStateFlow(prefs.getFloat("app_bg_blur", 0.5f))
    val appBgBlur: StateFlow<Float> = _appBgBlur.asStateFlow()

    private val _appBgBrightness = MutableStateFlow(prefs.getFloat("app_bg_brightness", 0.6f))
    val appBgBrightness: StateFlow<Float> = _appBgBrightness.asStateFlow()

    init {
        ensureReactiveStoreSynced()
    }

    // Timer Citra Physics Balls (JSON array of diameters/durations) backed by process-wide shared StateFlow
    val citraTimerBallsJson: StateFlow<String> get() = sharedCitraTimerBallsJson.asStateFlow()

    // Pending integrated in-screen celebration after focus finishes (foreground OR background/lockscreen)
    val pendingTimerCelebration: StateFlow<PendingTimerCelebration?> get() = sharedPendingCelebration.asStateFlow()

    // Increments whenever timer snapshot changes from UI or background Receiver/Service/Worker
    val timerSnapshotRevision: StateFlow<Long> get() = sharedTimerSnapshotRevision.asStateFlow()

    private fun ensureReactiveStoreSynced() {
        synchronized(reactiveStoreLock) {
            val id = System.identityHashCode(prefs)
            if (boundPrefsIdentity != id) {
                boundPrefsIdentity = id
                sharedCitraTimerBallsJson.value = prefs.getString("citra_timer_balls_json", "[]") ?: "[]"
                sharedPendingCelebration.value = loadPendingTimerCelebrationInternal()
                sharedSettings.value = loadSettings()
                sharedTimerSnapshotRevision.value = 0L
            }
        }
    }

    // Timer Behavior Preferences: continuous cycles, dial tap to pause/resume, horizontal swipe navigation
    private val _timerContinuousMode = MutableStateFlow(prefs.getBoolean("timer_continuous_mode", false))
    val timerContinuousMode: StateFlow<Boolean> = _timerContinuousMode.asStateFlow()

    private val _timerTapToToggle = MutableStateFlow(prefs.getBoolean("timer_tap_to_toggle", true))
    val timerTapToToggle: StateFlow<Boolean> = _timerTapToToggle.asStateFlow()

    private val _timerAllowSwipe = MutableStateFlow(prefs.getBoolean("timer_allow_swipe", true))
    val timerAllowSwipe: StateFlow<Boolean> = _timerAllowSwipe.asStateFlow()

    // Habit Consecutive Days Gratification Points
    private val _habitRewardPoints = MutableStateFlow(prefs.getInt("habit_reward_points", 0))
    val habitRewardPoints: StateFlow<Int> = _habitRewardPoints.asStateFlow()

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
        sharedCitraTimerBallsJson.value = json
    }

    fun addCitraTimerBall(
        durationMinutes: Int,
        sessionKeyMillis: Long = 0L,
        sessionToken: String = ""
    ) {
        val safeMins = durationMinutes.coerceIn(1, 180)
        synchronized(citraBallLock) {
            val now = System.currentTimeMillis()
            val lastToken = prefs.getString("last_citra_ball_token", "") ?: ""
            val lastSessionKey = prefs.getLong("last_citra_ball_session_key", 0L)
            if (sessionToken.isNotBlank() && lastToken == sessionToken) {
                return
            }
            if (sessionKeyMillis > 0L && lastSessionKey == sessionKeyMillis) {
                return
            }
            val updated = try {
                val current = prefs.getString("citra_timer_balls_json", "[]") ?: "[]"
                val arr = org.json.JSONArray(current)
                arr.put(safeMins)
                arr.toString()
            } catch (_: Exception) {
                org.json.JSONArray().apply { put(safeMins) }.toString()
            }
            prefs.edit()
                .putString("citra_timer_balls_json", updated)
                .putString("last_citra_ball_token", sessionToken)
                .putLong("last_citra_ball_session_key", sessionKeyMillis)
                .putLong("last_citra_ball_added_at", now)
                .commit()
            sharedCitraTimerBallsJson.value = updated
        }
    }

    fun clearCitraTimerBalls() {
        prefs.edit().putString("citra_timer_balls_json", "[]").commit()
        sharedCitraTimerBallsJson.value = "[]"
    }

    fun refreshCitraTimerBalls() {
        val current = prefs.getString("citra_timer_balls_json", "[]") ?: "[]"
        if (sharedCitraTimerBallsJson.value != current) {
            sharedCitraTimerBallsJson.value = current
        }
        val celeb = loadPendingTimerCelebrationInternal()
        if (sharedPendingCelebration.value != celeb) {
            sharedPendingCelebration.value = celeb
        }
    }

    fun reloadReactiveTimerState() {
        refreshCitraTimerBalls()
        sharedTimerSnapshotRevision.value = sharedTimerSnapshotRevision.value + 1L
    }

    fun setOnboardingCompleted(completed: Boolean = true) {
        prefs.edit().putBoolean("has_completed_onboarding", completed).apply()
        _hasCompletedOnboarding.value = completed
    }

    fun setUserDisplayName(name: String) {
        val clean = name.trim().ifBlank { "Viajante Citra" }
        prefs.edit().putString("user_display_name", clean).apply()
        _userDisplayName.value = clean
    }

    fun setUserPhotoPath(path: String?) {
        prefs.edit().apply {
            if (path.isNullOrBlank()) remove("user_photo_path") else putString("user_photo_path", path)
        }.apply()
        _userPhotoPath.value = path?.takeIf { it.isNotBlank() }
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
            overrideSilentMode = prefs.getBoolean("override_silent_mode", false),
            pomodoroDisruptiveAlarm = prefs.getBoolean("pomodoro_disruptive_alarm", false)
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
        val s = sharedSettings.value
        val result = linkedMapOf<String, Any>(
            "theme_mode" to (prefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM"),
            "theme_accent" to _themeAccent.value.name,
            "default_start_tab" to _defaultStartTab.value,
            "keep_screen_on" to _keepScreenOn.value,
            "task_sound" to s.taskSound,
            "task_sound_uri" to s.taskSoundUri,
            "habit_sound" to s.habitSound,
            "habit_sound_uri" to s.habitSoundUri,
            "pomodoro_sound" to s.pomodoroSound,
            "pomodoro_sound_uri" to s.pomodoroSoundUri,
            "achievement_sound" to s.achievementSound,
            "alert_sound" to s.alertSound,
            "vibrate" to s.vibrate,
            "heads_up" to s.headsUpBanner,
            "notify_achievements" to s.notifyAchievements,
            "notify_missed_habits" to s.notifyMissedHabits,
            "ambient_music_in_app" to s.ambientBreakMusicInApp,
            "ambient_music_volume" to s.ambientMusicVolume,
            "override_silent_mode" to s.overrideSilentMode,
            "pomodoro_disruptive_alarm" to s.pomodoroDisruptiveAlarm,
            "daily_random_color" to _dailyRandomColor.value,
            "timer_continuous_mode" to _timerContinuousMode.value,
            "timer_tap_to_toggle" to _timerTapToToggle.value,
            "timer_allow_swipe" to _timerAllowSwipe.value,
            "last_used_focus_mins" to _lastUsedFocusMinutes.value,
            "habit_reward_points" to _habitRewardPoints.value,
            "notes_sort_option" to _notesSortOption.value,
            "notes_sort_ascending" to _notesSortAscending.value,
            "notes_layout_mode" to _notesLayoutMode.value,
            "default_note_open_mode" to _defaultNoteOpenMode.value,
            "last_note_editor_mode" to getLastNoteEditorMode(),
            "toolbar_items_order" to getToolbarOrder().joinToString(","),
            "citra_timer_balls_json" to citraTimerBallsJson.value,
            "recent_external_docs_json" to (prefs.getString("recent_external_docs_json", "[]") ?: "[]"),
            "has_completed_onboarding" to _hasCompletedOnboarding.value,
            "user_display_name" to _userDisplayName.value,
            "user_photo_path" to (_userPhotoPath.value ?: ""),
            "app_bg_uri" to (_appBgUri.value ?: ""),
            "app_bg_blur" to _appBgBlur.value,
            "app_bg_brightness" to _appBgBrightness.value,
            "welcome_phrases_enabled" to _welcomePhrasesEnabled.value,
            "disabled_welcome_categories" to _disabledWelcomeCategories.value.sorted(),
            "focus_distraction_free_enabled" to _focusDistractionFreeEnabled.value,
            "focus_allowed_packages" to _focusAllowedPackages.value.sorted(),
            "all_tutorials_skipped" to _allTutorialsSkipped.value,
            "completed_tutorial_areas" to _completedTutorialAreas.value.sorted(),
            "finances_enabled" to _financesEnabled.value,
            "finances_setup_completed" to _financesSetupCompleted.value,
            "finance_default_currency" to _financeDefaultCurrency.value,
            "finance_base_balance_minor" to _financeBaseBalanceMinor.value,
            "finance_hide_values" to _financeHideValues.value,
            "finance_pin_code" to (_financePinCode.value ?: ""),
            "finance_biometric_enabled" to _financeBiometricEnabled.value
        )
        return result
    }

    fun importPreferencesMap(map: Map<String, Any>) {
        if (map.isEmpty()) return
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
        (map["timer_continuous_mode"] as? Boolean)?.let {
            editor.putBoolean("timer_continuous_mode", it)
            _timerContinuousMode.value = it
        }
        (map["timer_tap_to_toggle"] as? Boolean)?.let {
            editor.putBoolean("timer_tap_to_toggle", it)
            _timerTapToToggle.value = it
        }
        (map["timer_allow_swipe"] as? Boolean)?.let {
            editor.putBoolean("timer_allow_swipe", it)
            _timerAllowSwipe.value = it
        }
        (map["last_used_focus_mins"] as? Number)?.toInt()?.let {
            val safe = it.coerceIn(1, 180)
            editor.putInt("last_used_focus_mins", safe)
            _lastUsedFocusMinutes.value = safe
        }
        (map["habit_reward_points"] as? Number)?.toInt()?.let {
            val safe = it.coerceAtLeast(0)
            editor.putInt("habit_reward_points", safe)
            _habitRewardPoints.value = safe
        }
        (map["notes_sort_option"] as? String)?.let {
            editor.putString("notes_sort_option", it)
            _notesSortOption.value = it
        }
        (map["notes_sort_ascending"] as? Boolean)?.let {
            editor.putBoolean("notes_sort_ascending", it)
            _notesSortAscending.value = it
        }
        (map["notes_layout_mode"] as? String)?.let {
            editor.putString("notes_layout_mode", it)
            _notesLayoutMode.value = it
        }
        (map["default_note_open_mode"] as? String)?.let {
            editor.putString("default_note_open_mode", it)
            _defaultNoteOpenMode.value = it
        }
        (map["last_note_editor_mode"] as? String)?.let {
            editor.putString("last_note_editor_mode", it)
        }
        (map["toolbar_items_order"] as? String)?.takeIf { it.isNotBlank() }?.let {
            editor.putString("toolbar_items_order", it)
        }
        (map["citra_timer_balls_json"] as? String)?.takeIf { it.isNotBlank() }?.let {
            editor.putString("citra_timer_balls_json", it)
            sharedCitraTimerBallsJson.value = it
        }
        (map["recent_external_docs_json"] as? String)?.takeIf { it.isNotBlank() }?.let {
            editor.putString("recent_external_docs_json", it)
        }
        (map["has_completed_onboarding"] as? Boolean)?.let {
            editor.putBoolean("has_completed_onboarding", it)
            _hasCompletedOnboarding.value = it
        }
        (map["user_display_name"] as? String)?.takeIf { it.isNotBlank() }?.let {
            val clean = it.trim()
            editor.putString("user_display_name", clean)
            _userDisplayName.value = clean
        }
        if (map.containsKey("user_photo_path")) {
            val photo = (map["user_photo_path"] as? String)?.takeIf { it.isNotBlank() }
            if (photo != null) editor.putString("user_photo_path", photo) else editor.remove("user_photo_path")
            _userPhotoPath.value = photo
        }
        if (map.containsKey("app_bg_uri") || map.containsKey("app_bg_blur") || map.containsKey("app_bg_brightness")) {
            val bgUri = (map["app_bg_uri"] as? String)?.takeIf { it.isNotBlank() } ?: _appBgUri.value
            val bgBlur = (map["app_bg_blur"] as? Number)?.toFloat() ?: _appBgBlur.value
            val bgBrightness = (map["app_bg_brightness"] as? Number)?.toFloat() ?: _appBgBrightness.value
            if (map.containsKey("app_bg_uri") && (map["app_bg_uri"] as? String).isNullOrBlank()) {
                editor.remove("app_bg_uri")
                _appBgUri.value = null
            } else if (bgUri != null) {
                editor.putString("app_bg_uri", bgUri)
                _appBgUri.value = bgUri
            }
            editor.putFloat("app_bg_blur", bgBlur)
            editor.putFloat("app_bg_brightness", bgBrightness)
            _appBgBlur.value = bgBlur
            _appBgBrightness.value = bgBrightness
        }
        (map["welcome_phrases_enabled"] as? Boolean)?.let {
            editor.putBoolean("welcome_phrases_enabled", it)
            _welcomePhrasesEnabled.value = it
        }
        toStringSet(map["disabled_welcome_categories"])?.let { cats ->
            editor.putStringSet("disabled_welcome_categories", cats)
            _disabledWelcomeCategories.value = cats
        }
        (map["focus_distraction_free_enabled"] as? Boolean)?.let {
            editor.putBoolean("focus_distraction_free_enabled", it)
            _focusDistractionFreeEnabled.value = it
        }
        toStringSet(map["focus_allowed_packages"])?.let { pkgs ->
            editor.putStringSet("focus_allowed_packages", pkgs)
            _focusAllowedPackages.value = pkgs
        }
        (map["all_tutorials_skipped"] as? Boolean)?.let {
            editor.putBoolean("all_tutorials_skipped", it)
            _allTutorialsSkipped.value = it
        }
        toStringSet(map["completed_tutorial_areas"])?.let { areas ->
            editor.putStringSet("completed_tutorial_areas", areas)
            _completedTutorialAreas.value = areas
        }
        (map["finances_enabled"] as? Boolean)?.let {
            editor.putBoolean("finances_enabled", it)
            _financesEnabled.value = it
        }
        (map["finances_setup_completed"] as? Boolean)?.let {
            editor.putBoolean("finances_setup_completed", it)
            _financesSetupCompleted.value = it
        }
        (map["finance_default_currency"] as? String)?.takeIf { it.isNotBlank() }?.let {
            editor.putString("finance_default_currency", it)
            _financeDefaultCurrency.value = it
        }
        (map["finance_base_balance_minor"] as? Number)?.toLong()?.let {
            editor.putLong("finance_base_balance_minor", it)
            _financeBaseBalanceMinor.value = it
        }
        (map["finance_hide_values"] as? Boolean)?.let {
            editor.putBoolean("finance_hide_values", it)
            _financeHideValues.value = it
        }
        if (map.containsKey("finance_pin_code")) {
            val pin = (map["finance_pin_code"] as? String)?.takeIf { it.isNotBlank() }
            if (pin != null) editor.putString("finance_pin_code", pin) else editor.remove("finance_pin_code")
            _financePinCode.value = pin
        }
        (map["finance_biometric_enabled"] as? Boolean)?.let {
            editor.putBoolean("finance_biometric_enabled", it)
            _financeBiometricEnabled.value = it
        }

        // Standard notification and sound effects settings
        val curSettings = sharedSettings.value
        val newSettings = curSettings.copy(
            taskSound = (map["task_sound"] as? String) ?: curSettings.taskSound,
            taskSoundUri = (map["task_sound_uri"] as? String) ?: curSettings.taskSoundUri,
            habitSound = (map["habit_sound"] as? String) ?: curSettings.habitSound,
            habitSoundUri = (map["habit_sound_uri"] as? String) ?: curSettings.habitSoundUri,
            pomodoroSound = (map["pomodoro_sound"] as? String) ?: curSettings.pomodoroSound,
            pomodoroSoundUri = (map["pomodoro_sound_uri"] as? String) ?: curSettings.pomodoroSoundUri,
            achievementSound = (map["achievement_sound"] as? String) ?: curSettings.achievementSound,
            alertSound = (map["alert_sound"] as? String) ?: curSettings.alertSound,
            vibrate = (map["vibrate"] as? Boolean) ?: curSettings.vibrate,
            headsUpBanner = (map["heads_up"] as? Boolean) ?: curSettings.headsUpBanner,
            notifyAchievements = (map["notify_achievements"] as? Boolean) ?: curSettings.notifyAchievements,
            notifyMissedHabits = (map["notify_missed_habits"] as? Boolean) ?: curSettings.notifyMissedHabits,
            ambientBreakMusicInApp = (map["ambient_music_in_app"] as? Boolean) ?: curSettings.ambientBreakMusicInApp,
            ambientMusicVolume = ((map["ambient_music_volume"] as? Number)?.toFloat()) ?: curSettings.ambientMusicVolume,
            overrideSilentMode = (map["override_silent_mode"] as? Boolean) ?: curSettings.overrideSilentMode,
            pomodoroDisruptiveAlarm = (map["pomodoro_disruptive_alarm"] as? Boolean) ?: curSettings.pomodoroDisruptiveAlarm
        )
        editor.apply()
        if (map.containsKey("recent_external_docs_json")) {
            _recentExternalDocs.value = loadRecentExternalDocs()
        }
        updateSettings(newSettings)
    }

    private fun toStringSet(raw: Any?): Set<String>? {
        return when (raw) {
            is Collection<*> -> raw.mapNotNull { it?.toString() }.toSet()
            is org.json.JSONArray -> buildSet {
                for (i in 0 until raw.length()) {
                    add(raw.optString(i))
                }
            }
            else -> null
        }
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
            putBoolean("pomodoro_disruptive_alarm", newSettings.pomodoroDisruptiveAlarm)
            apply()
        }
        sharedSettings.value = newSettings
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
        synchronized(timerSnapshotLock) {
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
                if (!snapshot.linkedTaskTitle.isNullOrBlank()) putString("timer_linked_task_title", snapshot.linkedTaskTitle)
                else remove("timer_linked_task_title")
                if (snapshot.linkedHabitId != null) putLong("timer_linked_habit", snapshot.linkedHabitId)
                else remove("timer_linked_habit")
                if (!snapshot.linkedHabitTitle.isNullOrBlank()) putString("timer_linked_habit_title", snapshot.linkedHabitTitle)
                else remove("timer_linked_habit_title")
                putLong("timer_last_ts", snapshot.lastTimestamp)
                putLong("timer_target_end_ts", snapshot.targetEndTimeMillis)
                putBoolean("timer_is_stopwatch", snapshot.isStopwatchMode)
                putLong("timer_stopwatch_start_ts", snapshot.stopwatchStartTimeMillis)
                putInt("timer_stopwatch_accum_secs", snapshot.stopwatchAccumulatedSeconds)
                putString("timer_session_token", snapshot.sessionToken)
                putBoolean("timer_completed_processed", snapshot.isCompletedProcessed)
                commit()
            }
            com.example.notifications.DirectBootAlarmStore.saveTimerSlot(
                context = context,
                isRunning = snapshot.isRunning && !snapshot.isStopwatchMode && snapshot.targetEndTimeMillis > 0L,
                targetEndTimeMillis = snapshot.targetEndTimeMillis,
                phaseName = snapshot.phaseName,
                sessionToken = snapshot.sessionToken
            )
            sharedTimerSnapshotRevision.value = sharedTimerSnapshotRevision.value + 1L
        }
    }

    fun loadTimerSnapshot(): SavedTimerSnapshot? {
        synchronized(timerSnapshotLock) {
            if (!prefs.contains("timer_rem_secs") && !prefs.contains("timer_is_stopwatch")) return null
            val linkedTask = if (prefs.contains("timer_linked_task")) prefs.getLong("timer_linked_task", 0L) else null
            val linkedHabit = if (prefs.contains("timer_linked_habit")) prefs.getLong("timer_linked_habit", 0L) else null
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
                linkedTaskTitle = prefs.getString("timer_linked_task_title", null),
                linkedHabitId = linkedHabit,
                linkedHabitTitle = prefs.getString("timer_linked_habit_title", null),
                lastTimestamp = prefs.getLong("timer_last_ts", 0L),
                targetEndTimeMillis = prefs.getLong("timer_target_end_ts", 0L),
                isStopwatchMode = prefs.getBoolean("timer_is_stopwatch", false),
                stopwatchStartTimeMillis = prefs.getLong("timer_stopwatch_start_ts", 0L),
                stopwatchAccumulatedSeconds = prefs.getInt("timer_stopwatch_accum_secs", 0),
                sessionToken = prefs.getString("timer_session_token", "") ?: "",
                isCompletedProcessed = prefs.getBoolean("timer_completed_processed", false)
            )
        }
    }

    /**
     * Atomically claims completion of a FOCUS phase for [sessionToken] / [targetEndTimeMillis] so that
     * concurrent AlarmReceiver, ForegroundService, BootReceiver, and ViewModel checks NEVER record
     * the same FocusSession twice in Room or double Citra balls.
     */
    fun tryClaimFocusSessionCompletion(
        targetEndTimeMillis: Long,
        nextBreakSeconds: Int,
        sessionToken: String = "",
        celebrationTitle: String = "Sessão Concluída!",
        completedFocusMinutes: Int = 15,
        completedCycle: Int = 1,
        totalCycles: Int = 1
    ): Boolean {
        synchronized(focusClaimLock) {
            val now = System.currentTimeMillis()
            val lastClaimedToken = prefs.getString("last_claimed_focus_token", "") ?: ""
            val lastClaimedTarget = prefs.getLong("last_claimed_focus_target_ms", 0L)
            val lastClaimedAt = prefs.getLong("last_claimed_focus_at_ms", 0L)
            val snapshotTarget = prefs.getLong("timer_target_end_ts", 0L)
            val snapshotToken = prefs.getString("timer_session_token", "") ?: ""
            val snapshotProcessed = prefs.getBoolean("timer_completed_processed", false)
            val currentPhase = prefs.getString("timer_phase", "FOCUS") ?: "FOCUS"

            val effectiveToken = sessionToken.ifBlank { snapshotToken }
            val effectiveKey = if (targetEndTimeMillis > 0L) targetEndTimeMillis else snapshotTarget

            if (effectiveToken.isNotBlank() && effectiveToken == lastClaimedToken) return false
            if (snapshotProcessed) return false
            if (effectiveKey > 0L) {
                if (effectiveKey == lastClaimedTarget) return false
            } else {
                if ((now - lastClaimedAt) in 0L until 15_000L) return false
                if (prefs.contains("timer_phase") && currentPhase != "FOCUS") return false
            }

            if (prefs.contains("timer_phase") && currentPhase != "FOCUS") {
                return false
            }

            val finalBreakMins = (nextBreakSeconds / 60).coerceAtLeast(1)
            val celebToken = effectiveToken.ifBlank { "focus_${effectiveKey}_${now}" }

            prefs.edit().apply {
                putString("last_claimed_focus_token", celebToken)
                putLong("last_claimed_focus_target_ms", effectiveKey)
                putLong("last_claimed_focus_at_ms", now)
                putBoolean("timer_completed_processed", true)
                putString("timer_phase", "BREAK")
                putInt("timer_rem_secs", nextBreakSeconds.coerceAtLeast(0))
                putBoolean("timer_running", false)
                putBoolean("timer_paused", false)
                putLong("timer_target_end_ts", 0L)
                putLong("timer_last_ts", now)
                putString("timer_session_token", "")

                putBoolean("pending_celebration_active", true)
                putString("pending_celebration_token", celebToken)
                putString("pending_celebration_title", celebrationTitle)
                putInt("pending_celebration_focus_mins", completedFocusMinutes)
                putInt("pending_celebration_cycle", completedCycle)
                putInt("pending_celebration_total_cycles", totalCycles)
                putInt("pending_celebration_break_mins", finalBreakMins)
                putBoolean("pending_celebration_all_done", false)
                commit()
            }

            com.example.notifications.DirectBootAlarmStore.clearTimerSlot(context)
            sharedPendingCelebration.value = PendingTimerCelebration(
                sessionToken = celebToken,
                title = celebrationTitle,
                completedFocusMinutes = completedFocusMinutes,
                completedCycle = completedCycle,
                totalCycles = totalCycles,
                breakMinutes = finalBreakMins,
                isAllCyclesCompleted = false
            )
            sharedTimerSnapshotRevision.value = sharedTimerSnapshotRevision.value + 1L
            return true
        }
    }

    /**
     * Atomically claims completion of a BREAK phase so UI/Receiver/Service never advance cycles twice.
     */
    fun tryClaimBreakCompletion(
        targetEndTimeMillis: Long,
        sessionToken: String = "",
        nextCycle: Int,
        totalCycles: Int,
        nextFocusSeconds: Int
    ): Boolean {
        synchronized(focusClaimLock) {
            val now = System.currentTimeMillis()
            val lastClaimedBreakToken = prefs.getString("last_claimed_break_token", "") ?: ""
            val lastClaimedBreakTarget = prefs.getLong("last_claimed_break_target_ms", 0L)
            val snapshotToken = prefs.getString("timer_session_token", "") ?: ""
            val snapshotTarget = prefs.getLong("timer_target_end_ts", 0L)
            val currentPhase = prefs.getString("timer_phase", "FOCUS") ?: "FOCUS"

            if (prefs.contains("timer_phase") && currentPhase != "BREAK") return false
            val effectiveToken = sessionToken.ifBlank { snapshotToken }
            val effectiveKey = if (targetEndTimeMillis > 0L) targetEndTimeMillis else snapshotTarget

            if (effectiveToken.isNotBlank() && effectiveToken == lastClaimedBreakToken) return false
            if (effectiveKey > 0L && effectiveKey == lastClaimedBreakTarget) return false

            val finalToken = effectiveToken.ifBlank { "break_${effectiveKey}_$now" }
            val hasMoreCycles = nextCycle <= totalCycles

            prefs.edit().apply {
                putString("last_claimed_break_token", finalToken)
                putLong("last_claimed_break_target_ms", effectiveKey)
                putBoolean("timer_completed_processed", true)
                putString("timer_phase", "FOCUS")
                putInt("timer_current_cycle", if (hasMoreCycles) nextCycle else 1)
                putInt("timer_rem_secs", nextFocusSeconds.coerceAtLeast(60))
                putBoolean("timer_running", false)
                putBoolean("timer_paused", false)
                putLong("timer_target_end_ts", 0L)
                putLong("timer_last_ts", now)
                putString("timer_session_token", "")
                commit()
            }
            com.example.notifications.DirectBootAlarmStore.clearTimerSlot(context)
            sharedTimerSnapshotRevision.value = sharedTimerSnapshotRevision.value + 1L
            return true
        }
    }

    private fun loadPendingTimerCelebrationInternal(): PendingTimerCelebration? {
        if (!prefs.getBoolean("pending_celebration_active", false)) return null
        return PendingTimerCelebration(
            sessionToken = prefs.getString("pending_celebration_token", "") ?: "",
            title = prefs.getString("pending_celebration_title", "Sessão Concluída!") ?: "Sessão Concluída!",
            completedFocusMinutes = prefs.getInt("pending_celebration_focus_mins", 15),
            completedCycle = prefs.getInt("pending_celebration_cycle", 1),
            totalCycles = prefs.getInt("pending_celebration_total_cycles", 1),
            breakMinutes = prefs.getInt("pending_celebration_break_mins", 5),
            isAllCyclesCompleted = prefs.getBoolean("pending_celebration_all_done", false)
        )
    }

    fun loadPendingTimerCelebration(): PendingTimerCelebration? {
        synchronized(focusClaimLock) {
            return loadPendingTimerCelebrationInternal()
        }
    }

    fun clearPendingTimerCelebration() {
        synchronized(focusClaimLock) {
            prefs.edit().apply {
                putBoolean("pending_celebration_active", false)
                remove("pending_celebration_token")
                remove("pending_celebration_title")
                remove("pending_celebration_focus_mins")
                remove("pending_celebration_cycle")
                remove("pending_celebration_total_cycles")
                remove("pending_celebration_break_mins")
                remove("pending_celebration_all_done")
                commit()
            }
            sharedPendingCelebration.value = null
        }
    }

    fun savePendingSnooze(entityType: String, entityId: Long, title: String, triggerTime: Long, soundUri: String) {
        synchronized(snoozeLock) {
            try {
                val arr = org.json.JSONArray(prefs.getString("pending_snoozes_json", "[]") ?: "[]")
                val out = org.json.JSONArray()
                val now = System.currentTimeMillis()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val trig = obj.optLong("triggerTime", 0L)
                    val same = obj.optString("type") == entityType && obj.optLong("entityId") == entityId
                    if (!same && trig > now - 60_000L) {
                        out.put(obj)
                    }
                }
                out.put(
                    org.json.JSONObject().apply {
                        put("type", entityType)
                        put("entityId", entityId)
                        put("title", title)
                        put("triggerTime", triggerTime)
                        put("soundUri", soundUri)
                    }
                )
                prefs.edit().putString("pending_snoozes_json", out.toString()).commit()
            } catch (_: Exception) {}
        }
    }

    fun getPendingSnoozes(): List<PendingSnoozeEntry> {
        synchronized(snoozeLock) {
            val raw = prefs.getString("pending_snoozes_json", "[]") ?: "[]"
            val now = System.currentTimeMillis()
            val list = mutableListOf<PendingSnoozeEntry>()
            val validArr = org.json.JSONArray()
            try {
                val arr = org.json.JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val trigger = obj.optLong("triggerTime", 0L)
                    if (trigger > now) {
                        val entry = PendingSnoozeEntry(
                            type = obj.optString("type", "task"),
                            entityId = obj.optLong("entityId", -1L),
                            title = obj.optString("title", "Citra"),
                            triggerTime = trigger,
                            soundUri = obj.optString("soundUri", "")
                        )
                        list.add(entry)
                        validArr.put(obj)
                    }
                }
                if (validArr.length() != arr.length()) {
                    prefs.edit().putString("pending_snoozes_json", validArr.toString()).apply()
                }
            } catch (_: Exception) {}
            return list
        }
    }

    fun removePendingSnooze(entityType: String, entityId: Long) {
        synchronized(snoozeLock) {
            try {
                val arr = org.json.JSONArray(prefs.getString("pending_snoozes_json", "[]") ?: "[]")
                val out = org.json.JSONArray()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val same = obj.optString("type") == entityType && obj.optLong("entityId") == entityId
                    if (!same) out.put(obj)
                }
                prefs.edit().putString("pending_snoozes_json", out.toString()).commit()
            } catch (_: Exception) {}
        }
    }

    fun clearTimerSnapshot() {
        synchronized(timerSnapshotLock) {
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
                remove("timer_linked_task_title")
                remove("timer_linked_habit")
                remove("timer_linked_habit_title")
                remove("timer_last_ts")
                remove("timer_target_end_ts")
                remove("timer_is_stopwatch")
                remove("timer_stopwatch_start_ts")
                remove("timer_stopwatch_accum_secs")
                remove("timer_session_token")
                remove("timer_completed_processed")
                commit()
            }
            com.example.notifications.DirectBootAlarmStore.clearTimerSlot(context)
            sharedTimerSnapshotRevision.value = sharedTimerSnapshotRevision.value + 1L
        }
    }

    fun setTimerContinuousMode(enabled: Boolean) {
        _timerContinuousMode.value = enabled
        prefs.edit().putBoolean("timer_continuous_mode", enabled).apply()
    }

    fun setTimerTapToToggle(enabled: Boolean) {
        _timerTapToToggle.value = enabled
        prefs.edit().putBoolean("timer_tap_to_toggle", enabled).apply()
    }

    fun setTimerAllowSwipe(enabled: Boolean) {
        _timerAllowSwipe.value = enabled
        prefs.edit().putBoolean("timer_allow_swipe", enabled).apply()
    }

    fun addHabitRewardPoints(delta: Int): Int {
        synchronized(habitRewardLock) {
            val next = (_habitRewardPoints.value + delta).coerceAtLeast(0)
            prefs.edit().putInt("habit_reward_points", next).commit()
            _habitRewardPoints.value = next
            return next
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

    // Notes Organization Preferences: primary sort, secondary asc/desc direction, and layout mode
    private val _notesSortOption = MutableStateFlow(
        prefs.getString("notes_sort_option", "LAST_UPDATED") ?: "LAST_UPDATED"
    )
    val notesSortOption: StateFlow<String> = _notesSortOption.asStateFlow()

    private val _notesSortAscending = MutableStateFlow(
        prefs.getBoolean("notes_sort_ascending", false)
    )
    val notesSortAscending: StateFlow<Boolean> = _notesSortAscending.asStateFlow()

    private val _notesLayoutMode = MutableStateFlow(
        prefs.getString("notes_layout_mode", "GRID_2") ?: "GRID_2"
    )
    val notesLayoutMode: StateFlow<String> = _notesLayoutMode.asStateFlow()

    fun setNotesSortOption(optionName: String) {
        _notesSortOption.value = optionName
        prefs.edit().putString("notes_sort_option", optionName).apply()
    }

    fun setNotesSortAscending(ascending: Boolean) {
        _notesSortAscending.value = ascending
        prefs.edit().putBoolean("notes_sort_ascending", ascending).apply()
    }

    fun setNotesLayoutMode(modeName: String) {
        _notesLayoutMode.value = modeName
        prefs.edit().putString("notes_layout_mode", modeName).apply()
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

    fun getToolbarOrder(): List<String> {
        val raw = prefs.getString("toolbar_items_order", null)
        if (raw.isNullOrBlank()) {
            return DEFAULT_TOOLBAR_ORDER
        }
        val list = raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (list == LEGACY_DEFAULT_TOOLBAR_ORDER) {
            saveToolbarOrder(DEFAULT_TOOLBAR_ORDER)
            return DEFAULT_TOOLBAR_ORDER
        }
        val combined = list + DEFAULT_TOOLBAR_ORDER.filterNot { it in list }
        return combined
    }

    fun saveToolbarOrder(order: List<String>) {
        prefs.edit().putString("toolbar_items_order", order.joinToString(",")).apply()
    }

    // Recent external PDF / DOCX documents history
    private val _recentExternalDocs = MutableStateFlow(loadRecentExternalDocs())
    val recentExternalDocs: StateFlow<List<RecentExternalDocEntry>> = _recentExternalDocs.asStateFlow()

    private fun loadRecentExternalDocs(): List<RecentExternalDocEntry> {
        val raw = prefs.getString("recent_external_docs_json", null) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val uri = obj.optString("uri").trim()
                    val title = obj.optString("title").trim()
                    val docType = obj.optString("docType").trim()
                    val addedAt = obj.optLong("addedAt", 0L)
                    if (uri.isNotBlank() && docType.isNotBlank()) {
                        add(
                            RecentExternalDocEntry(
                                uri = uri,
                                title = title.ifBlank { "Documento" },
                                docType = docType,
                                addedAt = addedAt
                            )
                        )
                    }
                }
            }.sortedByDescending { it.addedAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun recordRecentExternalDocs(entries: List<RecentExternalDocEntry>) {
        if (entries.isEmpty()) return
        synchronized(recentExternalDocsLock) {
            val map = linkedMapOf<String, RecentExternalDocEntry>()
            for (item in entries) {
                val cleanUri = item.uri.trim()
                if (cleanUri.isNotBlank()) {
                    map[cleanUri] = item.copy(uri = cleanUri)
                }
            }
            for (existing in _recentExternalDocs.value) {
                val cleanUri = existing.uri.trim()
                if (cleanUri.isNotBlank() && !map.containsKey(cleanUri)) {
                    map[cleanUri] = existing
                }
            }
            val updated = map.values
                .sortedByDescending { it.addedAt }
                .take(60)
            val arr = org.json.JSONArray()
            for (doc in updated) {
                arr.put(
                    org.json.JSONObject().apply {
                        put("uri", doc.uri)
                        put("title", doc.title)
                        put("docType", doc.docType)
                        put("addedAt", doc.addedAt)
                    }
                )
            }
            prefs.edit().putString("recent_external_docs_json", arr.toString()).apply()
            _recentExternalDocs.value = updated
        }
    }

    fun removeRecentExternalDoc(uri: String) {
        val clean = uri.trim()
        synchronized(recentExternalDocsLock) {
            val updated = _recentExternalDocs.value.filterNot { it.uri == clean }
            val arr = org.json.JSONArray()
            for (doc in updated) {
                arr.put(
                    org.json.JSONObject().apply {
                        put("uri", doc.uri)
                        put("title", doc.title)
                        put("docType", doc.docType)
                        put("addedAt", doc.addedAt)
                    }
                )
            }
            prefs.edit().putString("recent_external_docs_json", arr.toString()).apply()
            _recentExternalDocs.value = updated
        }
    }

    companion object {
        private val citraBallLock = Any()
        private val timerSnapshotLock = Any()
        private val focusClaimLock = Any()
        private val habitRewardLock = Any()
        private val recentExternalDocsLock = Any()
        private val snoozeLock = Any()
        private val reactiveStoreLock = Any()

        @Volatile
        private var boundPrefsIdentity: Int = 0
        private val sharedCitraTimerBallsJson = MutableStateFlow("[]")
        private val sharedPendingCelebration = MutableStateFlow<PendingTimerCelebration?>(null)
        private val sharedTimerSnapshotRevision = MutableStateFlow(0L)
        private val sharedSettings = MutableStateFlow(NotificationSettings())

        private val LEGACY_DEFAULT_TOOLBAR_ORDER = listOf(
            "bold", "italic", "inline_code", "titles", "checklist", "list",
            "code_block", "math", "table", "divider", "image", "audio", "record",
            "to_task", "quote", "callout", "details",
            "strikethrough", "underline", "highlight", "pagebreak",
            "find_replace", "link_entity", "pagination", "toc"
        )

        val DEFAULT_TOOLBAR_ORDER = listOf(
            // Primeiro módulo (exatamente os 13 símbolos permitidos no topo por padrão):
            "bold", "italic", "inline_code", "titles", "checklist", "list",
            "code_block", "math", "table", "divider", "image", "audio", "record",
            // Segundo módulo (tachado, sublinhado e destaque em primeiro):
            "strikethrough", "underline", "highlight",
            "to_task", "quote", "callout", "details",
            "pagebreak", "find_replace", "link_entity", "pagination", "toc"
        )
    }
}

data class PendingTimerCelebration(
    val sessionToken: String,
    val title: String,
    val completedFocusMinutes: Int,
    val completedCycle: Int,
    val totalCycles: Int,
    val breakMinutes: Int,
    val isAllCyclesCompleted: Boolean = false
)

data class PendingSnoozeEntry(
    val type: String,
    val entityId: Long,
    val title: String,
    val triggerTime: Long,
    val soundUri: String
)

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
    val linkedTaskTitle: String? = null,
    val linkedHabitId: Long? = null,
    val linkedHabitTitle: String? = null,
    val lastTimestamp: Long = 0L,
    val targetEndTimeMillis: Long = 0L,
    val isStopwatchMode: Boolean = false,
    val stopwatchStartTimeMillis: Long = 0L,
    val stopwatchAccumulatedSeconds: Int = 0,
    val sessionToken: String = "",
    val isCompletedProcessed: Boolean = false
)

data class RecentExternalDocEntry(
    val uri: String,
    val title: String,
    val docType: String,
    val addedAt: Long = System.currentTimeMillis()
)
