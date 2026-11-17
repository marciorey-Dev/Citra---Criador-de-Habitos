package com.example.notifications

import android.content.Context
import androidx.core.os.UserManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.local.PreferencesManager
import com.example.service.TimerForegroundService
import java.util.concurrent.TimeUnit

/**
 * Self-healing WorkManager Watchdog executing every 6 hours to guarantee that
 * pending Task, Habit, and Timer AlarmClock slots + Direct Boot mirrors survive
 * aggressive OEM task killers or system updates.
 */
class AlarmWatchdogWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val ctx = applicationContext
            NotificationHelper.initNotificationChannels(ctx)

            if (!UserManagerCompat.isUserUnlocked(ctx)) {
                NextAlarmScheduler.scheduleSlotsFromDirectBootStore(ctx)
                return Result.success()
            }

            NextAlarmScheduler.scheduleNextAlarmSync(ctx)

            val prefs = PreferencesManager(ctx)
            val snapshot = prefs.loadTimerSnapshot()
            if (snapshot != null && snapshot.isRunning && !snapshot.isStopwatchMode && snapshot.targetEndTimeMillis > 0L) {
                val now = System.currentTimeMillis()
                if (snapshot.targetEndTimeMillis <= now + 1000L) {
                    TimerCompletionCoordinator.completeExpiredTimerIfNeeded(
                        context = ctx,
                        triggerSource = "AlarmWatchdogWorker",
                        forceNow = true
                    )
                } else {
                    NotificationHelper.scheduleTimerEndAlarm(
                        context = ctx,
                        triggerTimeMillis = snapshot.targetEndTimeMillis,
                        phaseLabel = if (snapshot.phaseName == "BREAK") "Pausa" else "Foco"
                    )
                    TimerForegroundService.syncWithSnapshot(ctx)
                }
            }

            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "citra_alarm_watchdog_6h"

        fun schedulePeriodicWatchdog(context: Context) {
            try {
                val request = PeriodicWorkRequestBuilder<AlarmWatchdogWorker>(6, TimeUnit.HOURS)
                    .build()
                WorkManager.getInstance(context.applicationContext ?: context)
                    .enqueueUniquePeriodicWork(
                        UNIQUE_WORK_NAME,
                        ExistingPeriodicWorkPolicy.KEEP,
                        request
                    )
            } catch (_: Exception) {}
        }
    }
}
