package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesManager
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
        wakeLock?.acquire(30_000L) // Hold wake lock for up to 30 seconds to ensure screen turns on and sound completes

        try {
            val type = intent.getStringExtra(NotificationHelper.EXTRA_TYPE) ?: NotificationHelper.TYPE_TASK
            val title = intent.getStringExtra(NotificationHelper.EXTRA_TITLE) ?: "Citra"
            val message = intent.getStringExtra(NotificationHelper.EXTRA_MESSAGE) ?: "Você tem um lembrete pendente."
            val id = intent.getIntExtra(NotificationHelper.EXTRA_ID, (System.currentTimeMillis() % 10000).toInt())
            val soundUriExtra = intent.getStringExtra(NotificationHelper.EXTRA_SOUND_URI) ?: ""
            val loopAlarm = intent.getBooleanExtra(NotificationHelper.EXTRA_LOOP_ALARM, false)

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
                    // If already completed today, do not alert as missed!
                    if (isDoneToday) return
                }
            }

            // Fallback to active settings if extra is blank
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

            // For habits: reschedule for the exact same time tomorrow
            if (type == NotificationHelper.TYPE_HABIT || type == NotificationHelper.TYPE_HABIT_MISSED) {
                val habitId = intent.getLongExtra(NotificationHelper.EXTRA_HABIT_ID, -1L)
                val habitTime = intent.getStringExtra(NotificationHelper.EXTRA_HABIT_TIME)
                val habitLead = intent.getIntExtra(NotificationHelper.EXTRA_HABIT_LEAD, 10)
                val isAdvance = intent.getBooleanExtra(NotificationHelper.EXTRA_IS_ADVANCE, false)

                if (habitId > 0 && !habitTime.isNullOrBlank() && type == NotificationHelper.TYPE_HABIT) {
                    val cleanTitle = title
                        .replace("Hora do Hábito: ", "")
                        .replace(Regex("Hábito em \\d+ min: "), "")
                    NotificationHelper.rescheduleHabitOccurrenceForNextDay(
                        context = context,
                        habitId = habitId,
                        title = cleanTitle,
                        timeString = habitTime,
                        leadMinutes = habitLead,
                        customSoundUri = effectiveSound,
                        isAdvance = isAdvance,
                        loopAlarm = loopAlarm
                    )
                }
            }
        } finally {
            try {
                if (wakeLock?.isHeld == true) {
                    wakeLock.release()
                }
            } catch (_: Exception) {}
        }
    }
}
