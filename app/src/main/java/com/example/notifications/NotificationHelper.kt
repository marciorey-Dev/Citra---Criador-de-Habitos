package com.example.notifications

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.NotificationSettings
import com.example.data.local.PreferencesManager
import com.example.util.SoundEffectsManager
import java.util.Calendar

object NotificationHelper {
    const val CHANNEL_TASKS = "citra_tasks"
    const val CHANNEL_HABITS = "citra_habits"
    const val CHANNEL_ALERTS = "citra_alerts"
    const val CHANNEL_TIMER_ONGOING = "citra_timer_ongoing"

    const val TIMER_NOTIFICATION_ID = 99999

    const val EXTRA_TYPE = "notification_type"
    const val EXTRA_TITLE = "notification_title"
    const val EXTRA_MESSAGE = "notification_message"
    const val EXTRA_ID = "notification_id"
    const val EXTRA_SOUND_URI = "notification_sound_uri"
    const val EXTRA_HABIT_ID = "notification_habit_id"
    const val EXTRA_HABIT_TIME = "notification_habit_time"
    const val EXTRA_HABIT_LEAD = "notification_habit_lead"
    const val EXTRA_IS_ADVANCE = "notification_is_advance"
    const val EXTRA_LOOP_ALARM = "notification_loop_alarm"
    const val EXTRA_IS_MISSED = "notification_is_missed"

    const val TYPE_TASK = "task"
    const val TYPE_HABIT = "habit"
    const val TYPE_HABIT_MISSED = "habit_missed"
    const val TYPE_POMODORO = "pomodoro"
    const val TYPE_ACHIEVEMENT = "achievement"

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true
        } else true
    }

    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                try {
                    val intent = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {}
            }
        }
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            am?.canScheduleExactAlarms() ?: true
        } else true
    }

    fun requestExactAlarmPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
    }

    /**
     * Resolves or dynamically creates a NotificationChannel configured specifically
     * with the user's selected sound URI and AudioAttributes.USAGE_ALARM.
     * This bypasses the Android 8.0+ limitation where channel sound cannot be changed once created.
     */
    fun getOrCreateSoundChannel(
        context: Context,
        baseChannelId: String,
        channelName: String,
        soundIdOrUri: String
    ): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return baseChannelId
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val sanitizedKey = soundIdOrUri.replace(Regex("[^a-zA-Z0-9_]"), "_").take(32)
        val dynamicChannelId = "${baseChannelId}_v2_${sanitizedKey.ifEmpty { "def" }}"

        val existing = notificationManager.getNotificationChannel(dynamicChannelId)
        if (existing == null) {
            val soundUri = SoundEffectsManager.getSoundUri(context, soundIdOrUri)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            val channel = NotificationChannel(
                dynamicChannelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificações com toque personalizado do Citra"
                setSound(soundUri, audioAttributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500)
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }

        return dynamicChannelId
    }

    fun initNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val prefs = PreferencesManager(context)
            val settings = prefs.settings.value

            val taskSound = if (settings.taskSoundUri.isNotBlank()) settings.taskSoundUri else settings.taskSound
            val habitSound = if (settings.habitSoundUri.isNotBlank()) settings.habitSoundUri else settings.habitSound
            val pomodoroSound = if (settings.pomodoroSoundUri.isNotBlank()) settings.pomodoroSoundUri else settings.pomodoroSound

            // Pre-create initial sound channels
            getOrCreateSoundChannel(context, CHANNEL_TASKS, "Lembretes de Tarefas", taskSound)
            getOrCreateSoundChannel(context, CHANNEL_HABITS, "Lembretes de Hábitos", habitSound)
            getOrCreateSoundChannel(context, CHANNEL_ALERTS, "Pomodoro & Conquistas", pomodoroSound)

            // Low priority channel for ongoing countdown
            val timerOngoingChannel = NotificationChannel(
                CHANNEL_TIMER_ONGOING,
                "Contador Pomodoro Ativo",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mostra a contagem regressiva enquanto o timer estiver em execução"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(timerOngoingChannel)
        }
    }

    /**
     * Called whenever user updates their sound settings to prepare updated channels immediately.
     */
    fun prewarmChannelsForNewSettings(context: Context, settings: NotificationSettings) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val taskSound = if (settings.taskSoundUri.isNotBlank()) settings.taskSoundUri else settings.taskSound
            val habitSound = if (settings.habitSoundUri.isNotBlank()) settings.habitSoundUri else settings.habitSound
            val pomodoroSound = if (settings.pomodoroSoundUri.isNotBlank()) settings.pomodoroSoundUri else settings.pomodoroSound

            getOrCreateSoundChannel(context, CHANNEL_TASKS, "Lembretes de Tarefas", taskSound)
            getOrCreateSoundChannel(context, CHANNEL_HABITS, "Lembretes de Hábitos", habitSound)
            getOrCreateSoundChannel(context, CHANNEL_ALERTS, "Pomodoro & Conquistas", pomodoroSound)
        }
    }

    fun showInstantNotification(
        context: Context,
        id: Int,
        channelId: String,
        title: String,
        message: String,
        customSoundUriString: String = "",
        vibratePhone: Boolean = true,
        loopAlarm: Boolean = false
    ) {
        // Resolve effective sound
        val effectiveSound = if (customSoundUriString.isNotBlank()) {
            customSoundUriString
        } else {
            val prefs = PreferencesManager(context)
            val settings = prefs.settings.value
            when {
                channelId.contains("habit") -> if (settings.habitSoundUri.isNotBlank()) settings.habitSoundUri else settings.habitSound
                channelId.contains("alert") -> if (settings.pomodoroSoundUri.isNotBlank()) settings.pomodoroSoundUri else settings.pomodoroSound
                else -> if (settings.taskSoundUri.isNotBlank()) settings.taskSoundUri else settings.taskSound
            }
        }

        val baseChannel = when {
            channelId.contains("habit") -> CHANNEL_HABITS
            channelId.contains("alert") -> CHANNEL_ALERTS
            else -> CHANNEL_TASKS
        }
        val channelName = when {
            channelId.contains("habit") -> "Lembretes de Hábitos"
            channelId.contains("alert") -> "Pomodoro & Conquistas"
            else -> "Lembretes de Tarefas"
        }

        val activeChannelId = getOrCreateSoundChannel(context, baseChannel, channelName, effectiveSound)
        val soundUri = SoundEffectsManager.getSoundUri(context, effectiveSound)

        // Activity intent
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Full Screen Intent to wake up and display on lock screen
        val fullScreenIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            id + 80000,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(context, activeChannelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(!loopAlarm)
            .setOngoing(loopAlarm)
            .setSound(soundUri)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setVibrate(longArrayOf(0, 500, 250, 500))

        val notification = notificationBuilder.build()
        if (loopAlarm) {
            notification.flags = notification.flags or Notification.FLAG_INSISTENT
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(id, notification)

        // Active audio playback via STREAM_ALARM (Dual Engine)
        SoundEffectsManager.playAlarmAudio(context, effectiveSound, loop = loopAlarm)

        if (vibratePhone) {
            triggerVibration(context, 500)
        }
    }

    fun showTimerOngoingNotification(
        context: Context,
        remainingSeconds: Int,
        phaseLabel: String,
        isPaused: Boolean
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            TIMER_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mins = remainingSeconds / 60
        val secs = remainingSeconds % 60
        val timeFormatted = String.format("%02d:%02d", mins, secs)
        val statusText = if (isPaused) "Pausado" else "Em andamento"

        val notification = NotificationCompat.Builder(context, CHANNEL_TIMER_ONGOING)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Pomodoro: $phaseLabel ($timeFormatted)")
            .setContentText("Status: $statusText • Toque para retornar ao app")
            .setOngoing(!isPaused)
            .setAutoCancel(false)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(TIMER_NOTIFICATION_ID, notification)
    }

    fun cancelTimerNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(TIMER_NOTIFICATION_ID)
    }

    fun scheduleTaskReminder(
        context: Context,
        taskId: Long,
        title: String,
        dueTimestamp: Long,
        minutesBefore: Int,
        customSoundUri: String = "",
        loopAlarm: Boolean = false
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val now = System.currentTimeMillis()

        val effectiveSound = if (customSoundUri.isNotBlank()) {
            customSoundUri
        } else {
            val settings = PreferencesManager(context).settings.value
            if (settings.taskSoundUri.isNotBlank()) settings.taskSoundUri else settings.taskSound
        }

        // 1. Advance reminder (if minutesBefore > 0)
        if (minutesBefore > 0) {
            val advanceTriggerTime = dueTimestamp - (minutesBefore * 60 * 1000L)
            if (advanceTriggerTime > now) {
                val advanceIntent = Intent(context, CitraNotificationReceiver::class.java).apply {
                    putExtra(EXTRA_TYPE, TYPE_TASK)
                    putExtra(EXTRA_TITLE, "Tarefa em Breve: $title")
                    putExtra(EXTRA_MESSAGE, "Vence em $minutesBefore minutos! Fique atento.")
                    putExtra(EXTRA_ID, taskId.toInt())
                    putExtra(EXTRA_SOUND_URI, effectiveSound)
                    putExtra(EXTRA_LOOP_ALARM, loopAlarm)
                }
                val pendingAdvance = PendingIntent.getBroadcast(
                    context,
                    taskId.toInt(),
                    advanceIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                tryScheduleExact(context, alarmManager, advanceTriggerTime, pendingAdvance)
            }
        }

        // 2. Exact-time reminder (at deadline)
        if (dueTimestamp > now) {
            val exactIntent = Intent(context, CitraNotificationReceiver::class.java).apply {
                putExtra(EXTRA_TYPE, TYPE_TASK)
                putExtra(EXTRA_TITLE, "Prazo da Tarefa: $title")
                putExtra(EXTRA_MESSAGE, "Prazo final agora! Conclua sua tarefa.")
                putExtra(EXTRA_ID, (taskId + 50000).toInt())
                putExtra(EXTRA_SOUND_URI, effectiveSound)
                putExtra(EXTRA_LOOP_ALARM, loopAlarm)
            }
            val pendingExact = PendingIntent.getBroadcast(
                context,
                (taskId + 50000).toInt(),
                exactIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            tryScheduleExact(context, alarmManager, dueTimestamp, pendingExact)
        }
    }

    /**
     * Schedules alarm using AlarmClockInfo as first-class citizen.
     * AlarmClockInfo pierces through Doze mode, app standby, and OEM restrictions.
     */
    private fun tryScheduleExact(context: Context, alarmManager: AlarmManager, triggerTime: Long, pendingIntent: PendingIntent) {
        try {
            val showIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val showPendingIntent = PendingIntent.getActivity(
                context,
                (triggerTime % 100000).toInt(),
                showIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val clockInfo = AlarmManager.AlarmClockInfo(triggerTime, showPendingIntent)
            alarmManager.setAlarmClock(clockInfo, pendingIntent)
        } catch (_: Exception) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
            } catch (_: Exception) {
                try {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } catch (_: Exception) {}
            }
        }
    }

    fun cancelTaskReminder(context: Context, taskId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, CitraNotificationReceiver::class.java)

        val p1 = PendingIntent.getBroadcast(context, taskId.toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmManager.cancel(p1)

        val p2 = PendingIntent.getBroadcast(context, (taskId + 50000).toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmManager.cancel(p2)
    }

    fun scheduleHabitReminder(
        context: Context,
        habitId: Long,
        title: String,
        timeString: String,
        leadMinutes: Int = 10,
        customSoundUri: String = "",
        loopAlarm: Boolean = false
    ) {
        val parts = timeString.split(":")
        if (parts.size != 2) return
        val hour = parts[0].toIntOrNull() ?: return
        val minute = parts[1].toIntOrNull() ?: return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val now = System.currentTimeMillis()

        val effectiveSound = if (customSoundUri.isNotBlank()) {
            customSoundUri
        } else {
            val settings = PreferencesManager(context).settings.value
            if (settings.habitSoundUri.isNotBlank()) settings.habitSoundUri else settings.habitSound
        }

        // 1. Advance reminder (if leadMinutes > 0)
        if (leadMinutes > 0) {
            val calAdvance = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.MINUTE, -leadMinutes)
                if (timeInMillis <= now) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            val intentAdvance = Intent(context, CitraNotificationReceiver::class.java).apply {
                putExtra(EXTRA_TYPE, TYPE_HABIT)
                putExtra(EXTRA_TITLE, "Hábito em $leadMinutes min: $title")
                putExtra(EXTRA_MESSAGE, "Horário programado: $timeString. Prepare-se para seu hábito!")
                putExtra(EXTRA_ID, (habitId + 10000).toInt())
                putExtra(EXTRA_SOUND_URI, effectiveSound)
                putExtra(EXTRA_HABIT_ID, habitId)
                putExtra(EXTRA_HABIT_TIME, timeString)
                putExtra(EXTRA_HABIT_LEAD, leadMinutes)
                putExtra(EXTRA_IS_ADVANCE, true)
                putExtra(EXTRA_LOOP_ALARM, loopAlarm)
            }
            val pAdvance = PendingIntent.getBroadcast(
                context,
                (habitId + 10000).toInt(),
                intentAdvance,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            tryScheduleExact(context, alarmManager, calAdvance.timeInMillis, pAdvance)
        }

        // 2. Exact-time reminder (at scheduled habit time)
        val calExact = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        val intentExact = Intent(context, CitraNotificationReceiver::class.java).apply {
            putExtra(EXTRA_TYPE, TYPE_HABIT)
            putExtra(EXTRA_TITLE, "Hora do Hábito: $title")
            putExtra(EXTRA_MESSAGE, "Hora exata de praticar seu hábito! Mantenha sua sequência ativa.")
            putExtra(EXTRA_ID, (habitId + 60000).toInt())
            putExtra(EXTRA_SOUND_URI, effectiveSound)
            putExtra(EXTRA_HABIT_ID, habitId)
            putExtra(EXTRA_HABIT_TIME, timeString)
            putExtra(EXTRA_HABIT_LEAD, leadMinutes)
            putExtra(EXTRA_IS_ADVANCE, false)
            putExtra(EXTRA_LOOP_ALARM, loopAlarm)
        }
        val pExact = PendingIntent.getBroadcast(
            context,
            (habitId + 60000).toInt(),
            intentExact,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        tryScheduleExact(context, alarmManager, calExact.timeInMillis, pExact)

        // 3. Missed habit alert (5 minutes after expiration)
        val settings = PreferencesManager(context).settings.value
        if (settings.notifyMissedHabits) {
            val calMissed = Calendar.getInstance().apply {
                timeInMillis = calExact.timeInMillis + (5 * 60 * 1000L)
            }
            val intentMissed = Intent(context, CitraNotificationReceiver::class.java).apply {
                putExtra(EXTRA_TYPE, TYPE_HABIT_MISSED)
                putExtra(EXTRA_TITLE, "⚠️ Hábito não realizado: $title")
                putExtra(EXTRA_MESSAGE, "Já passaram 5 minutos do horário limite. Ainda dá tempo de completar seu hábito hoje!")
                putExtra(EXTRA_ID, (habitId + 90000).toInt())
                putExtra(EXTRA_SOUND_URI, effectiveSound)
                putExtra(EXTRA_HABIT_ID, habitId)
                putExtra(EXTRA_HABIT_TIME, timeString)
                putExtra(EXTRA_HABIT_LEAD, leadMinutes)
                putExtra(EXTRA_LOOP_ALARM, false)
            }
            val pMissed = PendingIntent.getBroadcast(
                context,
                (habitId + 90000).toInt(),
                intentMissed,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            tryScheduleExact(context, alarmManager, calMissed.timeInMillis, pMissed)
        }
    }

    /**
     * Reschedules habit alarm for the following day immediately upon firing,
     * ensuring daily persistence without relying on inexact setRepeating.
     */
    fun rescheduleHabitOccurrenceForNextDay(
        context: Context,
        habitId: Long,
        title: String,
        timeString: String,
        leadMinutes: Int,
        customSoundUri: String,
        isAdvance: Boolean,
        loopAlarm: Boolean = false
    ) {
        val parts = timeString.split(":")
        if (parts.size != 2) return
        val hour = parts[0].toIntOrNull() ?: return
        val minute = parts[1].toIntOrNull() ?: return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val effectiveSound = if (customSoundUri.isNotBlank()) {
            customSoundUri
        } else {
            val settings = PreferencesManager(context).settings.value
            if (settings.habitSoundUri.isNotBlank()) settings.habitSoundUri else settings.habitSound
        }

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (isAdvance) {
                add(Calendar.MINUTE, -leadMinutes)
            }
            // Add exactly 1 day for next occurrence
            add(Calendar.DAY_OF_YEAR, 1)
        }

        val requestCode = if (isAdvance) (habitId + 10000).toInt() else (habitId + 60000).toInt()
        val intent = Intent(context, CitraNotificationReceiver::class.java).apply {
            putExtra(EXTRA_TYPE, TYPE_HABIT)
            putExtra(EXTRA_TITLE, if (isAdvance) "Hábito em $leadMinutes min: $title" else "Hora do Hábito: $title")
            putExtra(EXTRA_MESSAGE, if (isAdvance) "Horário programado: $timeString. Prepare-se para seu hábito!" else "Hora exata de praticar seu hábito! Mantenha sua sequência ativa.")
            putExtra(EXTRA_ID, requestCode)
            putExtra(EXTRA_SOUND_URI, effectiveSound)
            putExtra(EXTRA_HABIT_ID, habitId)
            putExtra(EXTRA_HABIT_TIME, timeString)
            putExtra(EXTRA_HABIT_LEAD, leadMinutes)
            putExtra(EXTRA_IS_ADVANCE, isAdvance)
            putExtra(EXTRA_LOOP_ALARM, loopAlarm)
        }
        val pIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        tryScheduleExact(context, alarmManager, cal.timeInMillis, pIntent)
    }

    fun cancelHabitReminder(context: Context, habitId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, CitraNotificationReceiver::class.java)

        val p1 = PendingIntent.getBroadcast(context, (habitId + 10000).toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmManager.cancel(p1)

        val p2 = PendingIntent.getBroadcast(context, (habitId + 60000).toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmManager.cancel(p2)

        val p3 = PendingIntent.getBroadcast(context, (habitId + 90000).toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmManager.cancel(p3)
    }

    fun triggerVibration(context: Context, durationMs: Long = 400) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                v?.vibrate(durationMs)
            }
        } catch (_: Exception) {}
    }
}
