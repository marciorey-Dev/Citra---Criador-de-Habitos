package com.example.notifications

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesManager
import com.example.data.model.FocusSession
import com.example.data.repository.CitraRepository
import com.example.service.AlarmRingService
import com.example.service.FocusDistractionService
import com.example.service.TimerForegroundService
import com.example.util.SoundEffectsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Single authoritative, idempotent coordinator for completing active countdown timers
 * (FOCUS -> Celebration -> BREAK, or BREAK -> next FOCUS cycle).
 *
 * Protected by both coroutine [completionMutex] and SharedPreferences atomic claim
 * ([PreferencesManager.tryClaimFocusSessionCompletion] / [PreferencesManager.tryClaimBreakCompletion]),
 * guaranteeing that concurrent calls from CitraNotificationReceiver, TimerForegroundService,
 * BootReceiver, AlarmWatchdogWorker, and MainViewModel never duplicate FocusSessions or Citra balls.
 */
object TimerCompletionCoordinator {

    private val completionMutex = Mutex()

    suspend fun completeExpiredTimerIfNeeded(
        context: Context,
        triggerSource: String,
        forceNow: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext ?: context
        completionMutex.withLock {
            val prefs = PreferencesManager(appContext)
            val snapshot = prefs.loadTimerSnapshot() ?: return@withLock false

            if (!snapshot.isRunning || snapshot.isStopwatchMode) {
                return@withLock false
            }

            val now = System.currentTimeMillis()
            val targetEnd = if (snapshot.targetEndTimeMillis > 0L) {
                snapshot.targetEndTimeMillis
            } else if (snapshot.lastTimestamp > 0L && snapshot.remainingSeconds >= 0) {
                snapshot.lastTimestamp + (snapshot.remainingSeconds * 1000L)
            } else {
                0L
            }

            if (targetEnd <= 0L) return@withLock false
            if (!forceNow && targetEnd > now + 1000L) {
                return@withLock false
            }

            val settings = prefs.settings.value
            val sound = if (settings.pomodoroSoundUri.isNotBlank()) {
                settings.pomodoroSoundUri
            } else {
                settings.pomodoroSound
            }

            if (snapshot.phaseName == "FOCUS") {
                val breakSec = (snapshot.breakMinutes * 60).coerceAtLeast(60)
                val celebrationTitle = "Sessão ${snapshot.currentCycle} de ${snapshot.totalCycles} Concluída!"

                val claimed = prefs.tryClaimFocusSessionCompletion(
                    targetEndTimeMillis = targetEnd,
                    nextBreakSeconds = breakSec,
                    sessionToken = snapshot.sessionToken,
                    celebrationTitle = celebrationTitle,
                    completedFocusMinutes = snapshot.focusMinutes,
                    completedCycle = snapshot.currentCycle,
                    totalCycles = snapshot.totalCycles
                )
                if (!claimed) {
                    return@withLock false
                }

                // Stop live timer foreground service & ongoing notification (NEVER touching TIMER_ALERT_NOTIFICATION_ID)
                TimerForegroundService.stop(appContext)
                NotificationHelper.cancelTimerNotification(appContext)
                try {
                    FocusDistractionService.stop(appContext)
                } catch (_: Exception) {}

                val db = AppDatabase.getInstance(appContext)
                val repository = CitraRepository(appContext, db)
                val session = FocusSession(
                    category = snapshot.selectedCategory,
                    durationMinutes = snapshot.focusMinutes,
                    breakMinutes = 0,
                    cycleCount = snapshot.currentCycle,
                    timestamp = now,
                    weekIdentifier = CitraRepository.getMondayOfWeek(now),
                    linkedTaskId = snapshot.linkedTaskId,
                    linkedTaskTitle = snapshot.linkedTaskTitle
                )
                repository.recordFocusSession(session)

                // Link habit check-in if configured and not already completed today
                if (snapshot.linkedHabitId != null) {
                    try {
                        val habit = db.habitDao().getHabitById(snapshot.linkedHabitId)
                        if (habit != null) {
                            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(now))
                            val alreadyDone = habit.completedDatesCsv.split(",").any { it.trim() == todayStr }
                            if (!alreadyDone) {
                                repository.toggleHabitTodayWithConsecutiveDays(habit)
                            }
                        }
                    } catch (_: Exception) {}
                }

                // Add strictly 1 Citra ball using atomic token/timestamp deduplication
                prefs.addCitraTimerBall(
                    durationMinutes = snapshot.focusMinutes,
                    sessionKeyMillis = targetEnd,
                    sessionToken = snapshot.sessionToken
                )

                val message = "Excelente dedicação de ${snapshot.focusMinutes}m! Pausa de ${snapshot.breakMinutes}m pronta."
                if (AppForegroundTracker.isAppVisibleAndUnlocked(appContext) && !settings.pomodoroDisruptiveAlarm) {
                    // Immediate integrated celebration inside TimerScreen + respectful completion chime
                    try {
                        SoundEffectsManager.playAlarmAudio(
                            context = appContext,
                            soundIdOrUri = sound,
                            loop = false,
                            overrideSilent = settings.overrideSilentMode
                        )
                    } catch (_: Exception) {}
                    if (settings.vibrate) {
                        NotificationHelper.triggerVibration(appContext, 350L)
                    }
                } else {
                    // Screen locked, app backgrounded, or disruptive session alarm enabled: ring via dedicated AlarmRingService + FullScreenIntent (AlarmActivity)
                    AlarmRingService.startAlarm(
                        context = appContext,
                        notificationId = NotificationHelper.TIMER_ALERT_NOTIFICATION_ID,
                        type = NotificationHelper.TYPE_POMODORO,
                        title = celebrationTitle,
                        message = message,
                        soundUri = sound,
                        loopAudio = true,
                        useFullScreen = true,
                        vibrate = settings.vibrate,
                        overrideSilent = settings.overrideSilentMode
                    )
                }

                return@withLock true
            } else {
                // BREAK phase completed
                val nextCycle = snapshot.currentCycle + 1
                val focusSec = (snapshot.focusMinutes * 60).coerceAtLeast(60)
                val claimed = prefs.tryClaimBreakCompletion(
                    targetEndTimeMillis = targetEnd,
                    sessionToken = snapshot.sessionToken,
                    nextCycle = nextCycle,
                    totalCycles = snapshot.totalCycles,
                    nextFocusSeconds = focusSec
                )
                if (!claimed) {
                    return@withLock false
                }

                TimerForegroundService.stop(appContext)
                NotificationHelper.cancelTimerNotification(appContext)
                try {
                    FocusDistractionService.stop(appContext)
                } catch (_: Exception) {}

                val hasMore = snapshot.currentCycle < snapshot.totalCycles
                val title = if (hasMore) "Pausa Concluída!" else "Ciclos Finalizados!"
                val message = if (hasMore) {
                    "Hora de retomar o Ciclo ${snapshot.currentCycle + 1}!"
                } else {
                    "Todos os ${snapshot.totalCycles} ciclos de foco foram completados!"
                }

                if (AppForegroundTracker.isAppVisibleAndUnlocked(appContext) && !settings.pomodoroDisruptiveAlarm) {
                    try {
                        SoundEffectsManager.playAlarmAudio(
                            context = appContext,
                            soundIdOrUri = sound,
                            loop = false,
                            overrideSilent = settings.overrideSilentMode
                        )
                    } catch (_: Exception) {}
                    if (settings.vibrate) {
                        NotificationHelper.triggerVibration(appContext, 350L)
                    }
                } else {
                    AlarmRingService.startAlarm(
                        context = appContext,
                        notificationId = NotificationHelper.TIMER_ALERT_NOTIFICATION_ID,
                        type = NotificationHelper.TYPE_POMODORO,
                        title = title,
                        message = message,
                        soundUri = sound,
                        loopAudio = true,
                        useFullScreen = true,
                        vibrate = settings.vibrate,
                        overrideSilent = settings.overrideSilentMode
                    )
                }

                return@withLock true
            }
        }
    }
}
