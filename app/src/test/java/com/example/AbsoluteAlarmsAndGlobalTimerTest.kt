package com.example

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesManager
import com.example.data.local.SavedTimerSnapshot
import com.example.data.model.Habit
import com.example.data.model.Task
import com.example.notifications.AlarmHealthChecker
import com.example.notifications.DirectBootAlarmStore
import com.example.notifications.NextAlarmScheduler
import com.example.notifications.NotificationHelper
import com.example.notifications.TimerCompletionCoordinator
import com.example.ui.MainViewModel
import com.example.ui.PomodoroPhase
import com.example.ui.screens.timer.TimerScreen
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AbsoluteAlarmsAndGlobalTimerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var app: Application
    private lateinit var db: AppDatabase
    private lateinit var prefs: PreferencesManager

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        db = AppDatabase.getInstance(app)

        app.getSharedPreferences("citra_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        DirectBootAlarmStore.clearAll(app)

        runBlocking {
            db.focusSessionDao().deleteAllSessions()
            db.taskDao().deleteAllTasks()
            db.habitDao().deleteAllHabits()
        }

        prefs = PreferencesManager(app)
        prefs.clearCitraTimerBalls()
        prefs.clearPendingTimerCelebration()
        prefs.reloadReactiveTimerState()
    }

    @After
    fun tearDown() {
        app.getSharedPreferences("citra_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        DirectBootAlarmStore.clearAll(app)
        prefs.clearCitraTimerBalls()
        prefs.clearPendingTimerCelebration()
        prefs.reloadReactiveTimerState()
    }

    @Test
    fun `concurrent timer completions are idempotent — 1 FocusSession, 1 Citra ball, 1 task increment`() = runBlocking {
        val taskId = db.taskDao().insertTask(
            Task(
                title = "Estudar Arquitetura",
                category = "Estudos",
                focusMinutesCompleted = 10
            )
        )
        val habitId = db.habitDao().insertHabit(
            Habit(
                title = "Revisão Diária",
                category = "Estudos",
                reminderTime = "09:00"
            )
        )

        val now = System.currentTimeMillis()
        val targetEnd = now - 5_000L
        prefs.saveTimerSnapshot(
            SavedTimerSnapshot(
                focusMinutes = 25,
                breakMinutes = 5,
                totalCycles = 2,
                currentCycle = 1,
                remainingSeconds = 0,
                phaseName = "FOCUS",
                isRunning = true,
                isPaused = false,
                selectedCategory = "Estudos",
                linkedTaskId = taskId,
                linkedTaskTitle = "Estudar Arquitetura",
                linkedHabitId = habitId,
                linkedHabitTitle = "Revisão Diária",
                lastTimestamp = targetEnd,
                targetEndTimeMillis = targetEnd,
                isStopwatchMode = false,
                sessionToken = "concurrent-token-abc"
            )
        )

        val results = (1..6).map { idx ->
            async {
                TimerCompletionCoordinator.completeExpiredTimerIfNeeded(
                    context = app,
                    triggerSource = "TestConcurrent#$idx",
                    forceNow = true
                )
            }
        }.awaitAll()

        assertEquals("Exactly one caller should claim completion", 1, results.count { it })

        val sessions = db.focusSessionDao().getAllSessionsSync()
        assertEquals("Strictly 1 FocusSession recorded in Room", 1, sessions.size)
        assertEquals(25, sessions.first().durationMinutes)
        assertEquals(taskId, sessions.first().linkedTaskId)

        val ballsArr = JSONArray(prefs.citraTimerBallsJson.value)
        assertEquals("Strictly 1 Citra ball added", 1, ballsArr.length())

        val updatedTask = db.taskDao().getTaskById(taskId)
        assertEquals("Task focusMinutesCompleted incremented once (10 + 25)", 35, updatedTask?.focusMinutesCompleted)

        val celebration = prefs.pendingTimerCelebration.value
        assertNotNull("Pending celebration should be stored", celebration)
        assertEquals(25, celebration?.completedFocusMinutes)

        val nextSnapshot = prefs.loadTimerSnapshot()
        assertEquals("Phase transitioned atomically to BREAK", "BREAK", nextSnapshot?.phaseName)
        assertEquals(false, nextSnapshot?.isRunning)
    }

    @Test
    fun `confirmTimerCelebration clears celebration while keeping Citra balls and BREAK phase`() = runBlocking {
        val now = System.currentTimeMillis()
        val targetEnd = now - 3_000L
        prefs.saveTimerSnapshot(
            SavedTimerSnapshot(
                focusMinutes = 15,
                breakMinutes = 5,
                totalCycles = 2,
                currentCycle = 1,
                remainingSeconds = 0,
                phaseName = "FOCUS",
                isRunning = true,
                selectedCategory = "Foco",
                lastTimestamp = targetEnd,
                targetEndTimeMillis = targetEnd,
                sessionToken = "token-break-transition"
            )
        )

        val claimed = TimerCompletionCoordinator.completeExpiredTimerIfNeeded(
            context = app,
            triggerSource = "TestBreakTransition",
            forceNow = true
        )
        assertTrue(claimed)

        val viewModel = MainViewModel(app)
        assertNotNull(viewModel.pendingTimerCelebration.value)
        assertEquals(1, JSONArray(viewModel.citraTimerBallsJson.value).length())
        assertEquals(PomodoroPhase.BREAK, viewModel.pomodoroState.value.currentPhase)

        viewModel.confirmTimerCelebration()

        assertNull(viewModel.pendingTimerCelebration.value)
        assertNull(prefs.loadPendingTimerCelebration())
        assertEquals(1, JSONArray(viewModel.citraTimerBallsJson.value).length())
        assertEquals(PomodoroPhase.BREAK, viewModel.pomodoroState.value.currentPhase)
    }

    @Test
    fun `TimerScreen integrated celebration renders emoji, title, Continuar button and transitions into BREAK with Citras`() {
        composeTestRule.mainClock.autoAdvance = false

        val now = System.currentTimeMillis()
        val targetEnd = now - 2_000L
        prefs.saveTimerSnapshot(
            SavedTimerSnapshot(
                focusMinutes = 20,
                breakMinutes = 5,
                totalCycles = 2,
                currentCycle = 1,
                remainingSeconds = 0,
                phaseName = "FOCUS",
                isRunning = true,
                selectedCategory = "Estudos",
                lastTimestamp = targetEnd,
                targetEndTimeMillis = targetEnd,
                sessionToken = "token-compose-ui"
            )
        )

        runBlocking {
            TimerCompletionCoordinator.completeExpiredTimerIfNeeded(
                context = app,
                triggerSource = "TestComposeUI",
                forceNow = true
            )
        }

        val viewModel = MainViewModel(app)
        composeTestRule.setContent {
            MaterialTheme {
                TimerScreen(
                    viewModel = viewModel,
                    onNavigateToTasks = {}
                )
            }
        }

        composeTestRule.mainClock.advanceTimeBy(600L)

        composeTestRule.onNodeWithTag("timer_celebration_overlay").assertIsDisplayed()
        composeTestRule.onNodeWithTag("timer_celebration_emoji").assertIsDisplayed()
        composeTestRule.onNodeWithTag("timer_celebration_title").assertIsDisplayed()
        composeTestRule.onNodeWithTag("timer_celebration_continue_btn").assertIsDisplayed()
        composeTestRule.onNodeWithText("Continuar").assertIsDisplayed()

        // Click "Continuar" -> celebration clears, phase remains BREAK, Citras stay visible
        composeTestRule.onNodeWithTag("timer_celebration_continue_btn").performClick()
        composeTestRule.mainClock.advanceTimeBy(600L)

        assertNull(viewModel.pendingTimerCelebration.value)
        assertEquals(PomodoroPhase.BREAK, viewModel.pomodoroState.value.currentPhase)
        assertEquals(
            0,
            composeTestRule.onAllNodesWithTag("timer_celebration_overlay").fetchSemanticsNodes().size
        )
    }

    @Test
    fun `multi-slot NextAlarmScheduler schedules 370-day future task and habit independently and restores DirectBoot slots`() = runBlocking {
        val now = System.currentTimeMillis()
        val calFutureTask = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, 370)
            set(Calendar.HOUR_OF_DAY, 14)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
        }
        val taskId = db.taskDao().insertTask(
            Task(
                title = "Renovação Anual",
                category = "Geral",
                dueDateTimestamp = calFutureTask.timeInMillis,
                loopAlarm = true
            )
        )

        val calHabit = Calendar.getInstance().apply {
            timeInMillis = now + 2 * 60 * 60 * 1000L
        }
        val habitTime = String.format(
            "%02d:%02d",
            calHabit.get(Calendar.HOUR_OF_DAY),
            calHabit.get(Calendar.MINUTE)
        )
        val habitId = db.habitDao().insertHabit(
            Habit(
                title = "Hidratação",
                category = "Saúde",
                reminderTime = habitTime,
                frequencyDays = "0,1,2,3,4,5,6",
                loopAlarm = false
            )
        )

        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val shadowAlarmManager = Shadows.shadowOf(alarmManager)
        shadowAlarmManager.scheduledAlarms.clear()

        NextAlarmScheduler.scheduleNextAlarmSync(app, nowMs = now)

        val scheduled = shadowAlarmManager.scheduledAlarms
        assertTrue(
            "Expected both Task slot and Habit slot scheduled simultaneously (found ${scheduled.size})",
            scheduled.size >= 2
        )

        val savedTaskSlot = DirectBootAlarmStore.loadTaskSlot(app)
        val savedHabitSlot = DirectBootAlarmStore.loadHabitSlot(app)
        assertNotNull("DirectBoot Task slot must be saved", savedTaskSlot)
        assertNotNull("DirectBoot Habit slot must be saved", savedHabitSlot)
        assertEquals(taskId, savedTaskSlot?.entityId)
        assertEquals(NextAlarmScheduler.TYPE_TASK_71H, savedTaskSlot?.type)
        assertEquals(habitId, savedHabitSlot?.entityId)

        // Now add an urgent task 1 hour ahead (<71h window) and verify TYPE_TASK_EXACT keeps loopAlarm = true
        val urgentTaskId = db.taskDao().insertTask(
            Task(
                title = "Reunião Urgente",
                category = "Trabalho",
                dueDateTimestamp = now + 60L * 60L * 1000L,
                loopAlarm = true
            )
        )
        NextAlarmScheduler.scheduleNextAlarmSync(app, nowMs = now)
        val urgentSlot = DirectBootAlarmStore.loadTaskSlot(app)
        assertEquals(urgentTaskId, urgentSlot?.entityId)
        assertEquals(NextAlarmScheduler.TYPE_TASK_EXACT, urgentSlot?.type)
        assertEquals(true, urgentSlot?.loopAlarm)

        // Verify DirectBoot slot recovery works cleanly even if AlarmManager was wiped
        shadowAlarmManager.scheduledAlarms.clear()
        NextAlarmScheduler.scheduleSlotsFromDirectBootStore(app)
        assertTrue(
            "DirectBoot recovery should re-arm both slots",
            shadowAlarmManager.scheduledAlarms.size >= 2
        )
    }

    @Test
    fun `notification IDs stay isolated so cancelling ongoing chronometer never wipes alert notification`() {
        assertNotEquals(
            NotificationHelper.TIMER_ONGOING_NOTIFICATION_ID,
            NotificationHelper.TIMER_ALERT_NOTIFICATION_ID
        )

        NotificationHelper.initNotificationChannels(app)
        NotificationHelper.showInstantNotification(
            context = app,
            id = NotificationHelper.TIMER_ALERT_NOTIFICATION_ID,
            channelId = NotificationHelper.CHANNEL_ALERTS,
            title = "Sessão 1 de 2 Concluída!",
            message = "Pausa pronta.",
            loopAlarm = false,
            useFullScreen = false
        )

        NotificationHelper.cancelTimerNotification(app)

        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val activeIds = nm.activeNotifications.map { it.id }
        assertTrue(
            "Alert notification (99999) should stay intact",
            NotificationHelper.TIMER_ALERT_NOTIFICATION_ID in activeIds
        )
        assertFalse(
            "Ongoing chronometer ID (99998) should not match or remain active",
            NotificationHelper.TIMER_ONGOING_NOTIFICATION_ID in activeIds
        )
        assertNotNull(AlarmHealthChecker.inspect(app))
    }
}
