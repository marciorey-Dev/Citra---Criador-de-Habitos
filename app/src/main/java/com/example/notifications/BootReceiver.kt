package com.example.notifications

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.local.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            (action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)
        ) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    NotificationHelper.initNotificationChannels(context)

                    // 1. Recalculate and schedule the next single reminder from Room truth
                    NextAlarmScheduler.scheduleNextAlarm(context)

                    // 2. Check if a focus timer was running before reboot/time change
                    val prefs = PreferencesManager(context)
                    val snapshot = prefs.loadTimerSnapshot()
                    if (snapshot != null && snapshot.isRunning && snapshot.targetEndTimeMillis > 0L) {
                        val now = System.currentTimeMillis()
                        if (snapshot.targetEndTimeMillis > now) {
                            NotificationHelper.scheduleTimerEndAlarm(
                                context = context,
                                triggerTimeMillis = snapshot.targetEndTimeMillis,
                                phaseLabel = snapshot.phaseName
                            )
                        } else {
                            // Timer completed while phone was off/time was changed
                            prefs.saveTimerSnapshot(
                                snapshot.copy(
                                    isRunning = false,
                                    isPaused = false,
                                    remainingSeconds = 0,
                                    targetEndTimeMillis = 0L
                                )
                            )
                        }
                    }
                } catch (_: Exception) {
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
