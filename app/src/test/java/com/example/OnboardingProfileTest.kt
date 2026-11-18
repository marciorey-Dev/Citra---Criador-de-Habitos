package com.example

import com.example.data.local.AlertStyleChoice
import com.example.data.local.FinanceStyleChoice
import com.example.data.local.FocusStyleChoice
import com.example.data.local.GuidanceStyleChoice
import com.example.data.local.MainGoalChoice
import com.example.data.local.NotesStyleChoice
import com.example.data.local.OnboardingProfileAnswers
import com.example.data.local.PreferencesManager
import com.example.data.local.ProfileDefaultsResolver
import com.example.data.local.ThemeBrightnessChoice
import com.example.data.local.VisualMoodChoice
import com.example.ui.theme.ThemeAccent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OnboardingProfileTest {

    @Test
    fun resolveDisruptiveDeepFocusProfileSetsUrgentAlertsAndImmersionDefaults() {
        val answers = OnboardingProfileAnswers(
            mainGoal = MainGoalChoice.FOCUS_STUDY,
            alertStyle = AlertStyleChoice.DISRUPTIVE,
            focusStyle = FocusStyleChoice.DEEP_IMMERSION,
            financeStyle = FinanceStyleChoice.DISABLED,
            financeHideValues = true, // Ignored when finances disabled
            notesStyle = NotesStyleChoice.COMPACT_LIST,
            themeBrightness = ThemeBrightnessChoice.DARK,
            visualMood = VisualMoodChoice.FOCO,
            guidanceStyle = GuidanceStyleChoice.SELF_SUFFICIENT,
            welcomePhrasesEnabled = false
        )

        val resolved = ProfileDefaultsResolver.resolve(answers)

        assertEquals("TIMER", resolved.defaultStartTab)
        assertEquals(ProfileDefaultsResolver.TASK_ALERT_DISRUPTIVE, resolved.defaultTaskAlertMode)
        assertTrue(resolved.pomodoroDisruptiveAlarm)
        assertTrue(resolved.overrideSilentMode)
        assertEquals(45, resolved.focusMinutes)
        assertTrue(resolved.timerContinuousMode)
        assertFalse(resolved.timerAllowSwipe)
        assertTrue(resolved.focusDistractionFreeEnabled)
        assertFalse(resolved.financesEnabled)
        assertFalse(resolved.financeHideValues)
        assertEquals("EDIT", resolved.defaultNoteOpenMode)
        assertEquals("LIST", resolved.notesLayoutMode)
        assertEquals(true, resolved.themeMode)
        assertEquals(ThemeAccent.AZUL_OCEANO, resolved.themeAccent)
        assertFalse(resolved.dailyRandomColor)
        assertTrue(resolved.allTutorialsSkipped)
        assertFalse(resolved.welcomePhrasesEnabled)
    }

    @Test
    fun resolveReaderAndFinanceProfileEnablesReadingModeAndPrivateFinances() {
        val answers = OnboardingProfileAnswers(
            mainGoal = MainGoalChoice.NOTES_DOCS,
            alertStyle = AlertStyleChoice.DISCREET,
            focusStyle = FocusStyleChoice.CLASSIC_FLOW,
            financeStyle = FinanceStyleChoice.ENABLED,
            financeHideValues = true,
            financeCurrencyCode = "BRL",
            notesStyle = NotesStyleChoice.READ_FOLDERS,
            themeBrightness = ThemeBrightnessChoice.SYSTEM,
            visualMood = VisualMoodChoice.SURPRISE_DAILY,
            guidanceStyle = GuidanceStyleChoice.GUIDED,
            welcomePhrasesEnabled = true
        )

        val resolved = ProfileDefaultsResolver.resolve(answers)

        assertEquals("NOTES", resolved.defaultStartTab)
        assertEquals(ProfileDefaultsResolver.TASK_ALERT_NOTIFICATION, resolved.defaultTaskAlertMode)
        assertFalse(resolved.pomodoroDisruptiveAlarm)
        assertFalse(resolved.overrideSilentMode)
        assertEquals(25, resolved.focusMinutes)
        assertTrue(resolved.ambientBreakMusicInApp)
        assertTrue(resolved.financesEnabled)
        assertTrue(resolved.financeHideValues)
        assertEquals("BRL", resolved.financeCurrencyCode)
        assertEquals("READING", resolved.defaultNoteOpenMode)
        assertEquals("STACKED_CATEGORIES", resolved.notesLayoutMode)
        assertNull(resolved.themeMode)
        assertTrue(resolved.dailyRandomColor)
        assertFalse(resolved.allTutorialsSkipped)
        assertTrue(resolved.welcomePhrasesEnabled)
    }

    @Test
    fun answersJsonRoundTripAndBackupMapPreserveProfileAndTaskAlertMode() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = PreferencesManager(context)

        val original = OnboardingProfileAnswers(
            mainGoal = MainGoalChoice.CALENDAR,
            alertStyle = AlertStyleChoice.NORMAL_ALARM,
            focusStyle = FocusStyleChoice.LIGHT_SPRINTS,
            financeStyle = FinanceStyleChoice.ENABLED,
            financeHideValues = true,
            financeCurrencyCode = "EUR",
            notesStyle = NotesStyleChoice.READ_FOLDERS,
            themeBrightness = ThemeBrightnessChoice.LIGHT,
            visualMood = VisualMoodChoice.CALMA,
            guidanceStyle = GuidanceStyleChoice.SELF_SUFFICIENT,
            welcomePhrasesEnabled = false
        )

        val reparsed = OnboardingProfileAnswers.fromJsonString(original.toJsonString())
        assertEquals(original, reparsed)

        prefs.setDefaultTaskAlertMode(ProfileDefaultsResolver.TASK_ALERT_DISRUPTIVE)
        prefs.saveOnboardingProfileAnswers(original)

        val exported = prefs.exportPreferencesMap()
        assertEquals(ProfileDefaultsResolver.TASK_ALERT_DISRUPTIVE, exported["default_task_alert_mode"])

        prefs.setDefaultTaskAlertMode(ProfileDefaultsResolver.TASK_ALERT_NOTIFICATION)
        prefs.saveOnboardingProfileAnswers(OnboardingProfileAnswers())

        prefs.importPreferencesMap(exported)
        assertEquals(ProfileDefaultsResolver.TASK_ALERT_DISRUPTIVE, prefs.defaultTaskAlertMode.value)
        assertEquals(original, prefs.onboardingProfileAnswers.value)
    }
}
