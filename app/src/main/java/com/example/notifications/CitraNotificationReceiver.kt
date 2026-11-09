package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesManager
import com.example.data.model.FocusSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CitraNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = pm?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
            "citra:alarm_wakelock"
        )
        wakeLock?.acquire(30_000L) // Hold wake lock for up to 30 seconds

        val action = intent.action

        if (action == NotificationHelper.ACTION_DAILY_MIDNIGHT) {
            try {
                val prefs = PreferencesManager(context)
                prefs.checkDailyRandomColor(force = true)
                NotificationHelper.scheduleDailyMidnightColorAlarm(context)
            } finally {
                wakeLock?.release()
            }
            return
        }

        // Handle exact timer completion in background/lockscreen
        if (action == NotificationHelper.ACTION_TIMER_END) {
            try {
                val prefs = PreferencesManager(context)
                val settings = prefs.settings.value
                val sound = if (settings.pomodoroSoundUri.isNotBlank()) settings.pomodoroSoundUri else settings.pomodoroSound

                NotificationHelper.showInstantNotification(
                    context = context,
                    id = NotificationHelper.TIMER_NOTIFICATION_ID,
                    channelId = NotificationHelper.CHANNEL_ALERTS,
                    title = "Sessão Concluída!",
                    message = "Seu tempo de foco no Arancia foi concluído com sucesso!",
                    customSoundUriString = sound,
                    vibratePhone = settings.vibrate,
                    loopAlarm = false
                )

                // Save completed focus session to Room
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val snapshot = prefs.loadTimerSnapshot()
                        if (snapshot != null) {
                            if (snapshot.phaseName == "FOCUS") {
                                val db = AppDatabase.getInstance(context)
                                val session = FocusSession(
                                    durationMinutes = snapshot.focusMinutes,
                                    category = snapshot.selectedCategory,
                                    timestamp = System.currentTimeMillis()
                                )
                                db.focusSessionDao().insertSession(session)
                                prefs.addCitraTimerBall(snapshot.focusMinutes)

                                val breakSec = snapshot.breakMinutes * 60
                                prefs.saveTimerSnapshot(
                                    snapshot.copy(
                                        phaseName = "BREAK",
                                        remainingSeconds = breakSec,
                                        isRunning = false,
                                        isPaused = false,
                                        targetEndTimeMillis = 0L
                                    )
                                )
                            } else {
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
                    } catch (_: Exception) {}
                }
            } finally {
                try {
                    if (wakeLock?.isHeld == true) wakeLock.release()
                } catch (_: Exception) {}
            }
            return
        }

        try {
            val type = intent.getStringExtra(NotificationHelper.EXTRA_TYPE) ?: NotificationHelper.TYPE_TASK
            val title = intent.getStringExtra(NotificationHelper.EXTRA_TITLE) ?: "Citra"
            val message = intent.getStringExtra(NotificationHelper.EXTRA_MESSAGE) ?: "Você tem um lembrete pendente."
            val id = intent.getIntExtra(NotificationHelper.EXTRA_ID, (System.currentTimeMillis() % 10000).toInt())
            val soundUriExtra = intent.getStringExtra(NotificationHelper.EXTRA_SOUND_URI) ?: ""
            // Habits NEVER use loopAlarm, preventing sticky 00:00 lockscreen on app open
            val loopAlarm = if (type == NotificationHelper.TYPE_HABIT || type == NotificationHelper.TYPE_HABIT_MISSED) {
                false
            } else {
                intent.getBooleanExtra(NotificationHelper.EXTRA_LOOP_ALARM, false)
            }

            val prefs = PreferencesManager(context)
            val settings = prefs.settings.value

            // Handle missed habit notification
            if (type == NotificationHelper.TYPE_HABIT_MISSED) {
                if (!settings.notifyMissedHabits) return
                val habitId = intent.getLongExtra(NotificationHelper.EXTRA_HABIT_ID, -1L)
                if (habitId > 0) {
                    val habit = runBlocking {
                        try {
                            AppDatabase.getInstance(context).habitDao().getHabitById(habitId)
                        } catch (_: Exception) { null }
                    }
                    val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    val isDoneToday = habit?.completedDatesCsv?.contains(todayStr) == true
                    if (isDoneToday) return
                }
            }

            val effectiveSound = if (soundUriExtra.isNotBlank()) {
                soundUriExtra
            } else {
                when (type) {
                    NotificationHelper.TYPE_HABIT, NotificationHelper.TYPE_HABIT_MISSED -> if (settings.habitSoundUri.isNotBlank()) settings.habitSoundUri else settings.habitSound
                    NotificationHelper.TYPE_ACHIEVEMENT -> if (settings.achievementSound.isNotBlank()) settings.achievementSound else settings.alertSound
                    NotificationHelper.TYPE_POMODORO -> if (settings.pomodoroSoundUri.isNotBlank()) settings.pomodoroSoundUri else settings.pomodoroSound
                    else -> if (settings.taskSoundUri.isNotBlank()) settings.taskSoundUri else settings.taskSound
                }
            }

            val channelId = when (type) {
                NotificationHelper.TYPE_HABIT, NotificationHelper.TYPE_HABIT_MISSED -> NotificationHelper.CHANNEL_HABITS
                NotificationHelper.TYPE_ACHIEVEMENT, NotificationHelper.TYPE_POMODORO -> NotificationHelper.CHANNEL_ALERTS
                else -> NotificationHelper.CHANNEL_TASKS
            }

            val taskId = intent.getLongExtra(NotificationHelper.EXTRA_TASK_ID, -1L)
            val habitIdExtra = intent.getLongExtra(NotificationHelper.EXTRA_HABIT_ID, -1L)
            val isDisruptive = intent.getBooleanExtra(NotificationHelper.EXTRA_IS_DISRUPTIVE, false)

            NotificationHelper.showInstantNotification(
                context = context,
                id = id,
                channelId = channelId,
                title = title,
                message = message,
                customSoundUriString = effectiveSound,
                vibratePhone = settings.vibrate,
                loopAlarm = loopAlarm,
                taskId = taskId,
                habitId = habitIdExtra
            )

            // If task was marked as disruptive, display the overlay alert
            if (isDisruptive || (type == NotificationHelper.TYPE_TASK && taskId > 0)) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val task = if (taskId > 0) {
                            AppDatabase.getInstance(context).taskDao().getTaskById(taskId)
                        } else null
                        if (isDisruptive || task?.isDisruptive == true) {
                            val cleanTitle = task?.title ?: title.replace("Prazo da Tarefa: ", "")
                            com.example.service.FocusDistractionService.showDisruptiveTaskOverlay(context, cleanTitle)
                        }
                    } catch (_: Exception) {}
                }
            }

            // Immediately schedule the NEXT single alarm from Room truth
            NextAlarmScheduler.scheduleNextAlarm(context)
        } finally {
            try {
                if (wakeLock?.isHeld == true) {
                    wakeLock.release()
                }
            } catch (_: Exception) {}
        }
    }
}
