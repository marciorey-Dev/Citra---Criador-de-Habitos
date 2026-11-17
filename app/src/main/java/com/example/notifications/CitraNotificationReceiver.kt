package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.os.UserManagerCompat
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesManager
import com.example.service.AlarmRingService
import com.example.service.FocusDistractionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CitraNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = try {
            @Suppress("DEPRECATION")
            pm?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "citra:alarm_wakelock"
            )?.apply {
                setReferenceCounted(false)
                acquire(30_000L)
            }
        } catch (_: Exception) {
            null
        }

        val action = intent.action

        if (action == NotificationHelper.ACTION_DAILY_MIDNIGHT) {
            try {
                if (UserManagerCompat.isUserUnlocked(context)) {
                    val prefs = PreferencesManager(context)
                    prefs.checkDailyRandomColor(force = true)
                }
                NotificationHelper.scheduleDailyMidnightColorAlarm(context)
            } finally {
                safeRelease(wakeLock)
            }
            return
        }

        if (action == NotificationHelper.ACTION_TIMER_END) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    if (UserManagerCompat.isUserUnlocked(context)) {
                        TimerCompletionCoordinator.completeExpiredTimerIfNeeded(
                            context = context,
                            triggerSource = "CitraNotificationReceiver",
                            forceNow = true
                        )
                        NextAlarmScheduler.scheduleNextAlarmSync(context)
                    } else {
                        // Device locked in Direct Boot before first unlock: ring via AlarmRingService
                        val title = intent.getStringExtra(NotificationHelper.EXTRA_TITLE) ?: "Sessão Concluída!"
                        val message = intent.getStringExtra(NotificationHelper.EXTRA_MESSAGE) ?: "Seu ciclo no Arancia terminou!"
                        AlarmRingService.startAlarm(
                            context = context,
                            notificationId = NotificationHelper.TIMER_ALERT_NOTIFICATION_ID,
                            type = NotificationHelper.TYPE_POMODORO,
                            title = title,
                            message = message,
                            loopAudio = true,
                            useFullScreen = true,
                            vibrate = true,
                            overrideSilent = false
                        )
                    }
                } catch (_: Exception) {
                } finally {
                    safeRelease(wakeLock)
                    try {
                        pendingResult.finish()
                    } catch (_: Exception) {}
                }
            }
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val type = intent.getStringExtra(NotificationHelper.EXTRA_TYPE) ?: NotificationHelper.TYPE_TASK
                val isHabit = type == NotificationHelper.TYPE_HABIT || type == NotificationHelper.TYPE_HABIT_MISSED
                val title = intent.getStringExtra(NotificationHelper.EXTRA_TITLE) ?: "Citra"
                val message = intent.getStringExtra(NotificationHelper.EXTRA_MESSAGE) ?: "Você tem um lembrete pendente."
                val id = intent.getIntExtra(
                    NotificationHelper.EXTRA_ID,
                    (System.currentTimeMillis() % 10000).toInt().coerceAtLeast(100)
                )
                val soundUriExtra = intent.getStringExtra(NotificationHelper.EXTRA_SOUND_URI) ?: ""
                val loopAlarm = intent.getBooleanExtra(NotificationHelper.EXTRA_LOOP_ALARM, false)
                val taskId = intent.getLongExtra(NotificationHelper.EXTRA_TASK_ID, -1L)
                val habitIdExtra = intent.getLongExtra(NotificationHelper.EXTRA_HABIT_ID, -1L)
                val isDisruptive = intent.getBooleanExtra(NotificationHelper.EXTRA_IS_DISRUPTIVE, false)

                val unlocked = UserManagerCompat.isUserUnlocked(context)
                if (!unlocked) {
                    val shouldFullScreen = loopAlarm && !isHabit
                    if (loopAlarm) {
                        AlarmRingService.startAlarm(
                            context = context,
                            notificationId = id,
                            type = type,
                            title = title,
                            message = message,
                            soundUri = soundUriExtra,
                            taskId = taskId,
                            habitId = habitIdExtra,
                            loopAudio = true,
                            useFullScreen = shouldFullScreen,
                            vibrate = true,
                            overrideSilent = false
                        )
                    } else {
                        NotificationHelper.initNotificationChannels(context)
                        NotificationHelper.showInstantNotification(
                            context = context,
                            id = id,
                            channelId = if (isHabit) NotificationHelper.CHANNEL_HABITS else NotificationHelper.CHANNEL_TASKS,
                            title = title,
                            message = message,
                            customSoundUriString = soundUriExtra,
                            vibratePhone = true,
                            loopAlarm = false,
                            taskId = taskId,
                            habitId = habitIdExtra,
                            useFullScreen = false
                        )
                    }
                    return@launch
                }

                NotificationHelper.initNotificationChannels(context)
                val prefs = PreferencesManager(context)
                val settings = prefs.settings.value

                // Clear any pending snooze for this entity so it won't re-trigger in NextAlarmScheduler
                if (isHabit && habitIdExtra > 0) {
                    prefs.removePendingSnooze(NotificationHelper.TYPE_HABIT, habitIdExtra)
                } else if (!isHabit && taskId > 0) {
                    prefs.removePendingSnooze(NotificationHelper.TYPE_TASK, taskId)
                }

                // If missed-habit check fired, verify habit wasn't already completed today
                var shouldAbortAlert = false
                if (type == NotificationHelper.TYPE_HABIT_MISSED) {
                    if (!settings.notifyMissedHabits) {
                        shouldAbortAlert = true
                    } else if (habitIdExtra > 0) {
                        val habit = try {
                            AppDatabase.getInstance(context).habitDao().getHabitById(habitIdExtra)
                        } catch (_: Exception) {
                            null
                        }
                        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                        if (habit == null || habit.completedDatesCsv.split(",").any { it.trim() == todayStr }) {
                            shouldAbortAlert = true
                        }
                    }
                }

                if (!shouldAbortAlert) {
                    val effectiveSound = if (soundUriExtra.isNotBlank()) {
                        soundUriExtra
                    } else {
                        when (type) {
                            NotificationHelper.TYPE_HABIT, NotificationHelper.TYPE_HABIT_MISSED ->
                                if (settings.habitSoundUri.isNotBlank()) settings.habitSoundUri else settings.habitSound
                            NotificationHelper.TYPE_ACHIEVEMENT ->
                                if (settings.achievementSound.isNotBlank()) settings.achievementSound else settings.alertSound
                            NotificationHelper.TYPE_POMODORO ->
                                if (settings.pomodoroSoundUri.isNotBlank()) settings.pomodoroSoundUri else settings.pomodoroSound
                            else ->
                                if (settings.taskSoundUri.isNotBlank()) settings.taskSoundUri else settings.taskSound
                        }
                    }

                    // Policy: ONLY Tasks with loopAlarm == true (or Timer End) launch FullScreenIntent / AlarmActivity.
                    // Habits NEVER launch AlarmActivity on lockscreen.
                    val shouldFullScreen = loopAlarm && !isHabit

                    if (loopAlarm) {
                        AlarmRingService.startAlarm(
                            context = context,
                            notificationId = id,
                            type = type,
                            title = title,
                            message = message,
                            soundUri = effectiveSound,
                            taskId = taskId,
                            habitId = habitIdExtra,
                            loopAudio = true,
                            useFullScreen = shouldFullScreen,
                            vibrate = settings.vibrate,
                            overrideSilent = settings.overrideSilentMode
                        )
                    } else {
                        val channelId = when (type) {
                            NotificationHelper.TYPE_HABIT, NotificationHelper.TYPE_HABIT_MISSED -> NotificationHelper.CHANNEL_HABITS
                            NotificationHelper.TYPE_ACHIEVEMENT, NotificationHelper.TYPE_POMODORO -> NotificationHelper.CHANNEL_ALERTS
                            else -> NotificationHelper.CHANNEL_TASKS
                        }
                        NotificationHelper.showInstantNotification(
                            context = context,
                            id = id,
                            channelId = channelId,
                            title = title,
                            message = message,
                            customSoundUriString = effectiveSound,
                            vibratePhone = settings.vibrate,
                            loopAlarm = false,
                            taskId = taskId,
                            habitId = habitIdExtra,
                            useFullScreen = false
                        )
                    }

                    if (!isHabit && (isDisruptive || taskId > 0)) {
                        try {
                            val task = if (taskId > 0) {
                                AppDatabase.getInstance(context).taskDao().getTaskById(taskId)
                            } else {
                                null
                            }
                            if (isDisruptive || task?.isDisruptive == true) {
                                val cleanTitle = task?.title ?: title.replace("Prazo da Tarefa: ", "")
                                FocusDistractionService.showDisruptiveTaskOverlay(context, cleanTitle)
                            }
                        } catch (_: Exception) {}
                    }
                }

                // Synchronously re-arm next independent Task & Habit AlarmClock slots before releasing goAsync()
                NextAlarmScheduler.scheduleNextAlarmSync(context)
            } catch (_: Exception) {
            } finally {
                safeRelease(wakeLock)
                try {
                    pendingResult.finish()
                } catch (_: Exception) {}
            }
        }
    }

    private fun safeRelease(wakeLock: PowerManager.WakeLock?) {
        try {
            if (wakeLock?.isHeld == true) wakeLock.release()
        } catch (_: Exception) {}
    }
}
