package com.example.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.MainActivity
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import com.example.data.model.isScheduledForDate
import com.example.data.model.getEffectiveDueTimestamp

object NextAlarmScheduler {
    const val REQUEST_CODE_NEXT_REMINDER = 90001
    const val ACTION_TRIGGER_SCHEDULED_REMINDER = "com.example.citra.TRIGGER_SCHEDULED_REMINDER"

    const val TYPE_TASK_ADVANCE = "task_advance"
    const val TYPE_TASK_71H = "task_71h"
    const val TYPE_TASK_EXACT = "task_exact"
    const val TYPE_HABIT_ADVANCE = "habit_advance"
    const val TYPE_HABIT_EXACT = "habit_exact"

    data class ScheduledReminderCandidate(
        val triggerTime: Long,
        val type: String,
        val entityId: Long,
        val title: String,
        val message: String,
        val soundUri: String,
        val loopAlarm: Boolean = false
    )

    fun scheduleNextAlarm(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val prefs = PreferencesManager(context)
                val settings = prefs.settings.value

                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return@launch
                val intent = Intent(context, CitraNotificationReceiver::class.java).apply {
                    action = ACTION_TRIGGER_SCHEDULED_REMINDER
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    REQUEST_CODE_NEXT_REMINDER,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val now = System.currentTimeMillis()
                val tasks = db.taskDao().getAllTasksSync().filter { !it.isCompleted }
                val habits = db.habitDao().getAllHabitsSync().filter { it.reminderTime.isNotBlank() }

                val defaultTaskSound = if (settings.taskSoundUri.isNotBlank()) settings.taskSoundUri else settings.taskSound
                val defaultHabitSound = if (settings.habitSoundUri.isNotBlank()) settings.habitSoundUri else settings.habitSound

                val candidates = mutableListOf<ScheduledReminderCandidate>()

                // 1. Evaluate Tasks
                tasks.forEach { task ->
                    val due = task.getEffectiveDueTimestamp() ?: return@forEach
                    val sound = if (task.customSoundUri.isNotBlank()) task.customSoundUri else defaultTaskSound

                    // 71 hours before reminder
                    val time71hMs = 71L * 60L * 60L * 1000L
                    val trigger71h = due - time71hMs
                    if (trigger71h > now + 1500L) {
                        candidates.add(
                            ScheduledReminderCandidate(
                                triggerTime = trigger71h,
                                type = TYPE_TASK_71H,
                                entityId = task.id,
                                title = "Aviso (71h): ${task.title}",
                                message = "Faltam 71 horas para o prazo da sua tarefa!",
                                soundUri = sound,
                                loopAlarm = false
                            )
                        )
                    }

                    // Advance reminder
                    if (task.reminderMinutesBefore > 0) {
                        val advTime = due - (task.reminderMinutesBefore * 60 * 1000L)
                        if (advTime > now + 1500L) {
                            candidates.add(
                                ScheduledReminderCandidate(
                                    triggerTime = advTime,
                                    type = TYPE_TASK_ADVANCE,
                                    entityId = task.id,
                                    title = "Tarefa em Breve: ${task.title}",
                                    message = "Vence em ${task.reminderMinutesBefore} minutos!",
                                    soundUri = sound,
                                    loopAlarm = false
                                )
                            )
                        }
                    }

                    // Exact reminder
                    if (due > now + 1500L) {
                        candidates.add(
                            ScheduledReminderCandidate(
                                triggerTime = due,
                                type = TYPE_TASK_EXACT,
                                entityId = task.id,
                                title = "Prazo da Tarefa: ${task.title}",
                                message = "Prazo final agora! Conclua sua tarefa.",
                                soundUri = sound,
                                loopAlarm = task.loopAlarm
                            )
                        )
                    }
                }

                // 2. Evaluate Habits
                habits.forEach { habit ->
                    val parts = habit.reminderTime.split(":")
                    if (parts.size == 2) {
                        val h = parts[0].toIntOrNull() ?: return@forEach
                        val m = parts[1].toIntOrNull() ?: return@forEach
                        val sound = if (habit.customSoundUri.isNotBlank()) habit.customSoundUri else defaultHabitSound

                        val calToday = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, h)
                            set(Calendar.MINUTE, m)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }

                        // Find next target date matching habit frequency (within next 14 days)
                        val dayFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                        var targetCal: Calendar? = null
                        for (dayOffset in 0..14) {
                            val checkCal = (calToday.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, dayOffset) }
                            val dateStr = dayFormat.format(checkCal.time)
                            if (checkCal.timeInMillis > now + 1500L && habit.isScheduledForDate(dateStr, checkCal)) {
                                targetCal = checkCal
                                break
                            }
                        }
                        if (targetCal == null) return@forEach

                        // Advance reminder for habit
                        if (habit.reminderLeadMinutes > 0) {
                            val advCal = (targetCal.clone() as Calendar).apply {
                                add(Calendar.MINUTE, -habit.reminderLeadMinutes)
                            }
                            if (advCal.timeInMillis > now + 1500L) {
                                candidates.add(
                                    ScheduledReminderCandidate(
                                        triggerTime = advCal.timeInMillis,
                                        type = TYPE_HABIT_ADVANCE,
                                        entityId = habit.id,
                                        title = "Hábito em ${habit.reminderLeadMinutes} min: ${habit.title}",
                                        message = "Horário programado: ${habit.reminderTime}. Prepare-se!",
                                        soundUri = sound,
                                        loopAlarm = false
                                    )
                                )
                            }
                        }

                        // Exact reminder for habit (always loopAlarm = false to avoid 00:00 alarm lock)
                        if (targetCal.timeInMillis > now + 1500L) {
                            candidates.add(
                                ScheduledReminderCandidate(
                                    triggerTime = targetCal.timeInMillis,
                                    type = TYPE_HABIT_EXACT,
                                    entityId = habit.id,
                                    title = "Hora do Hábito: ${habit.title}",
                                    message = "Hora de praticar seu hábito! Mantenha sua sequência ativa.",
                                    soundUri = sound,
                                    loopAlarm = false
                                )
                            )
                        }
                    }
                }

                // Find the single NEXT soonest reminder
                val nextCandidate = candidates.minByOrNull { it.triggerTime }

                if (nextCandidate != null) {
                    val updateIntent = Intent(context, CitraNotificationReceiver::class.java).apply {
                        action = ACTION_TRIGGER_SCHEDULED_REMINDER
                        putExtra(NotificationHelper.EXTRA_TYPE, if (nextCandidate.type.startsWith("habit")) NotificationHelper.TYPE_HABIT else NotificationHelper.TYPE_TASK)
                        putExtra(NotificationHelper.EXTRA_TITLE, nextCandidate.title)
                        putExtra(NotificationHelper.EXTRA_MESSAGE, nextCandidate.message)
                        putExtra(NotificationHelper.EXTRA_ID, (nextCandidate.entityId % 10000).toInt())
                        putExtra(NotificationHelper.EXTRA_SOUND_URI, nextCandidate.soundUri)
                        putExtra(NotificationHelper.EXTRA_LOOP_ALARM, nextCandidate.loopAlarm)
                        if (nextCandidate.type.startsWith("habit")) {
                            putExtra(NotificationHelper.EXTRA_HABIT_ID, nextCandidate.entityId)
                        } else {
                            putExtra(NotificationHelper.EXTRA_TASK_ID, nextCandidate.entityId)
                        }
                    }
                    val updatePending = PendingIntent.getBroadcast(
                        context,
                        REQUEST_CODE_NEXT_REMINDER,
                        updateIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    val showIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    val showPending = PendingIntent.getActivity(
                        context,
                        REQUEST_CODE_NEXT_REMINDER,
                        showIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    val clockInfo = AlarmManager.AlarmClockInfo(nextCandidate.triggerTime, showPending)
                    alarmManager.setAlarmClock(clockInfo, updatePending)
                } else {
                    alarmManager.cancel(pendingIntent)
                }
            } catch (_: Exception) {}
        }
    }
}
