package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager

class CitraNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "citra:alarm_wakelock")
        wakeLock?.acquire(15_000L) // Hold wake lock for up to 15 seconds to ensure notification/sound finishes

        try {
            val type = intent.getStringExtra(NotificationHelper.EXTRA_TYPE) ?: NotificationHelper.TYPE_TASK
            val title = intent.getStringExtra(NotificationHelper.EXTRA_TITLE) ?: "Citra"
            val message = intent.getStringExtra(NotificationHelper.EXTRA_MESSAGE) ?: "Você tem um lembrete pendente."
            val id = intent.getIntExtra(NotificationHelper.EXTRA_ID, (System.currentTimeMillis() % 10000).toInt())
            val soundUri = intent.getStringExtra(NotificationHelper.EXTRA_SOUND_URI) ?: ""

            val channelId = when (type) {
                NotificationHelper.TYPE_HABIT -> NotificationHelper.CHANNEL_HABITS
                NotificationHelper.TYPE_ACHIEVEMENT -> NotificationHelper.CHANNEL_ALERTS
                else -> NotificationHelper.CHANNEL_TASKS
            }

            NotificationHelper.showInstantNotification(
                context = context,
                id = id,
                channelId = channelId,
                title = title,
                message = message,
                customSoundUriString = soundUri
            )

            // For habits: reschedule for the exact same time tomorrow
            if (type == NotificationHelper.TYPE_HABIT) {
                val habitId = intent.getLongExtra(NotificationHelper.EXTRA_HABIT_ID, -1L)
                val habitTime = intent.getStringExtra(NotificationHelper.EXTRA_HABIT_TIME)
                val habitLead = intent.getIntExtra(NotificationHelper.EXTRA_HABIT_LEAD, 10)
                val isAdvance = intent.getBooleanExtra(NotificationHelper.EXTRA_IS_ADVANCE, false)

                if (habitId > 0 && !habitTime.isNullOrBlank()) {
                    val cleanTitle = title
                        .replace("Hora do Hábito: ", "")
                        .replace(Regex("Hábito em \\d+ min: "), "")
                    NotificationHelper.rescheduleHabitOccurrenceForNextDay(
                        context = context,
                        habitId = habitId,
                        title = cleanTitle,
                        timeString = habitTime,
                        leadMinutes = habitLead,
                        customSoundUri = soundUri,
                        isAdvance = isAdvance
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
