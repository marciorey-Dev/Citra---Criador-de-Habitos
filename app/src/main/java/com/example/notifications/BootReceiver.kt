package com.example.notifications

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.core.os.UserManagerCompat
import com.example.data.local.PreferencesManager
import com.example.service.TimerForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val isSupportedAction = action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_USER_UNLOCKED ||
            action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)

        if (!isSupportedAction) return

        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = try {
            pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "citra:boot_rescheduler")?.apply {
                setReferenceCounted(false)
                acquire(20_000L)
            }
        } catch (_: Exception) {
            null
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                NotificationHelper.initNotificationChannels(context)

                // Before user unlocks PIN on reboot, restore slots from Device Encrypted (Direct Boot) storage
                if (!UserManagerCompat.isUserUnlocked(context)) {
                    NextAlarmScheduler.scheduleSlotsFromDirectBootStore(context)
                    return@launch
                }

                // Unlocked: full Room-authoritative rescheduling across independent Task & Habit slots
                NextAlarmScheduler.scheduleNextAlarmSync(context)
                NotificationHelper.scheduleDailyMidnightColorAlarm(context)

                // Reconcile active Pomodoro/Timer snapshot across reboot / time adjustment
                val prefs = PreferencesManager(context)
                val snapshot = prefs.loadTimerSnapshot()
                if (snapshot != null && snapshot.isRunning) {
                    if (snapshot.isStopwatchMode) {
                        TimerForegroundService.syncWithSnapshot(context)
                    } else if (snapshot.targetEndTimeMillis > 0L) {
                        val now = System.currentTimeMillis()
                        if (snapshot.targetEndTimeMillis > now + 1000L) {
                            NotificationHelper.scheduleTimerEndAlarm(
                                context = context,
                                triggerTimeMillis = snapshot.targetEndTimeMillis,
                                phaseLabel = if (snapshot.phaseName == "BREAK") "Pausa" else "Foco"
                            )
                            TimerForegroundService.syncWithSnapshot(context)
                        } else {
                            // Timer finished while phone was powered off or clock jumped forward
                            TimerCompletionCoordinator.completeExpiredTimerIfNeeded(
                                context = context,
                                triggerSource = "BootReceiver:$action",
                                forceNow = true
                            )
                        }
                    }
                } else if (snapshot != null && snapshot.isPaused) {
                    TimerForegroundService.syncWithSnapshot(context)
                }
            } catch (_: Exception) {
            } finally {
                try {
                    if (wakeLock?.isHeld == true) wakeLock.release()
                } catch (_: Exception) {}
                try {
                    pendingResult.finish()
                } catch (_: Exception) {}
            }
        }
    }
}
