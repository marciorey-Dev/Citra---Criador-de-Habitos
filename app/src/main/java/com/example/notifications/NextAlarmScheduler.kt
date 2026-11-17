package com.example.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.data.local.AppDatabase
import com.example.data.local.PendingSnoozeEntry
import com.example.data.local.PreferencesManager
import com.example.data.model.Habit
import com.example.data.model.Task
import com.example.data.model.getEffectiveDueTimestamp
import com.example.data.model.isScheduledForDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Multi-Slot Calendar & Clock Alarm Scheduler.
 *
 * Uses three strictly independent `AlarmManager.setAlarmClock` slots so that
 * Tasks, Timer, and Habits NEVER evict or cancel each other:
 *  - [REQUEST_CODE_NEXT_TASK] (90001): Soonest Task reminder (unlimited lookahead) or Task snooze
 *  - [NotificationHelper.REQUEST_CODE_TIMER_END] (90002): Active Focus/Break timer end
 *  - [REQUEST_CODE_NEXT_HABIT] (90003): Soonest Habit reminder (366-day scan) / Missed check / Habit snooze
 *
 * Both Task and Habit slots are mirrored to [DirectBootAlarmStore] (Device Encrypted storage)
 * so [BootReceiver] restores them immediately on LOCKED_BOOT_COMPLETED before PIN unlock.
 */
object NextAlarmScheduler {
    const val REQUEST_CODE_NEXT_REMINDER = 90001 // Legacy alias for Task slot
    const val REQUEST_CODE_NEXT_TASK = 90001
    const val REQUEST_CODE_NEXT_HABIT = 90003

    const val ACTION_TRIGGER_SCHEDULED_REMINDER = "com.example.citra.TRIGGER_SCHEDULED_REMINDER"
    const val ACTION_TRIGGER_SCHEDULED_TASK = "com.example.citra.TRIGGER_SCHEDULED_TASK"
    const val ACTION_TRIGGER_SCHEDULED_HABIT = "com.example.citra.TRIGGER_SCHEDULED_HABIT"

    const val TYPE_TASK_ADVANCE = "task_advance"
    const val TYPE_TASK_71H = "task_71h"
    const val TYPE_TASK_EXACT = "task_exact"
    const val TYPE_TASK_SNOOZE = "task_snooze"
    const val TYPE_HABIT_ADVANCE = "habit_advance"
    const val TYPE_HABIT_EXACT = "habit_exact"
    const val TYPE_HABIT_MISSED = "habit_missed"
    const val TYPE_HABIT_SNOOZE = "habit_snooze"

    private val schedulerMutex = Mutex()

    data class ScheduledReminderCandidate(
        val triggerTime: Long,
        val type: String,
        val entityId: Long,
        val title: String,
        val message: String,
        val soundUri: String,
        val loopAlarm: Boolean = false,
        val isDisruptive: Boolean = false
    )

    fun scheduleNextAlarm(context: Context) {
        val appCtx = context.applicationContext ?: context
        CoroutineScope(Dispatchers.IO).launch {
            scheduleNextAlarmSync(appCtx)
        }
    }

    suspend fun scheduleNextAlarmSync(
        context: Context,
        nowMs: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext ?: context
        schedulerMutex.withLock {
            try {
                val db = AppDatabase.getInstance(appCtx)
                val prefs = PreferencesManager(appCtx)
                val settings = prefs.settings.value

                val alarmManager = appCtx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                    ?: return@withLock

                val tasks = db.taskDao().getAllTasksSync().filter { !it.isCompleted && !it.isWontDo }
                val habits = db.habitDao().getAllHabitsSync().filter { it.reminderTime.isNotBlank() }
                val snoozes = prefs.getPendingSnoozes()

                val defaultTaskSound = if (settings.taskSoundUri.isNotBlank()) settings.taskSoundUri else settings.taskSound
                val defaultHabitSound = if (settings.habitSoundUri.isNotBlank()) settings.habitSoundUri else settings.habitSound

                val nextTask = findNextTaskCandidate(
                    tasks = tasks,
                    snoozes = snoozes,
                    defaultTaskSound = defaultTaskSound,
                    nowMs = nowMs
                )
                val nextHabit = findNextHabitCandidate(
                    habits = habits,
                    snoozes = snoozes,
                    defaultHabitSound = defaultHabitSound,
                    notifyMissedHabits = settings.notifyMissedHabits,
                    nowMs = nowMs
                )

                applyTaskSlot(appCtx, alarmManager, nextTask)
                applyHabitSlot(appCtx, alarmManager, nextHabit)
            } catch (_: Exception) {}
        }
    }

    fun findNextTaskCandidate(
        tasks: List<Task>,
        snoozes: List<PendingSnoozeEntry>,
        defaultTaskSound: String,
        nowMs: Long = System.currentTimeMillis()
    ): ScheduledReminderCandidate? {
        val candidates = mutableListOf<ScheduledReminderCandidate>()
        val minThreshold = nowMs + 1000L

        tasks.forEach { task ->
            val due = task.getEffectiveDueTimestamp() ?: return@forEach
            val sound = if (task.customSoundUri.isNotBlank()) task.customSoundUri else defaultTaskSound

            // 71 hours before reminder (unlimited lookahead: works +365 days ahead)
            val trigger71h = due - (71L * 60L * 60L * 1000L)
            if (trigger71h > minThreshold) {
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

            // Custom advance reminder
            if (task.reminderMinutesBefore > 0) {
                val advTime = due - (task.reminderMinutesBefore * 60L * 1000L)
                if (advTime > minThreshold) {
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

            // Exact due reminder
            if (due > minThreshold) {
                candidates.add(
                    ScheduledReminderCandidate(
                        triggerTime = due,
                        type = TYPE_TASK_EXACT,
                        entityId = task.id,
                        title = "Prazo da Tarefa: ${task.title}",
                        message = "Prazo final agora! Conclua sua tarefa.",
                        soundUri = sound,
                        loopAlarm = task.loopAlarm,
                        isDisruptive = task.isDisruptive
                    )
                )
            }
        }

        // Include active snoozes for tasks
        snoozes.filter { it.type == NotificationHelper.TYPE_TASK && it.triggerTime > minThreshold }
            .forEach { snooze ->
                candidates.add(
                    ScheduledReminderCandidate(
                        triggerTime = snooze.triggerTime,
                        type = TYPE_TASK_SNOOZE,
                        entityId = snooze.entityId,
                        title = snooze.title.ifBlank { "Lembrete Adiado" },
                        message = "Alarme adiado por 10 minutos.",
                        soundUri = snooze.soundUri.ifBlank { defaultTaskSound },
                        loopAlarm = true,
                        isDisruptive = false
                    )
                )
            }

        return candidates.minByOrNull { it.triggerTime }
    }

    fun findNextHabitCandidate(
        habits: List<Habit>,
        snoozes: List<PendingSnoozeEntry>,
        defaultHabitSound: String,
        notifyMissedHabits: Boolean,
        nowMs: Long = System.currentTimeMillis()
    ): ScheduledReminderCandidate? {
        val candidates = mutableListOf<ScheduledReminderCandidate>()
        val minThreshold = nowMs + 1000L
        val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        habits.forEach { habit ->
            val parts = habit.reminderTime.split(":")
            if (parts.size != 2) return@forEach
            val h = parts[0].toIntOrNull() ?: return@forEach
            val m = parts[1].toIntOrNull() ?: return@forEach
            val sound = if (habit.customSoundUri.isNotBlank()) habit.customSoundUri else defaultHabitSound
            val completedDates = habit.completedDatesCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()

            val calToday = Calendar.getInstance().apply {
                timeInMillis = nowMs
                set(Calendar.HOUR_OF_DAY, h)
                set(Calendar.MINUTE, m)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // Full 366-day annual lookahead for any scheduled uncompleted occurrence
            for (dayOffset in 0..366) {
                val checkCal = (calToday.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_YEAR, dayOffset)
                }
                val dateStr = dayFormat.format(checkCal.time)
                val alreadyDone = completedDates.contains(dateStr)
                if (alreadyDone) continue
                if (!habit.isScheduledForDate(dateStr, checkCal)) continue

                var foundFutureForHabit = false

                // 1. Advance reminder
                if (habit.reminderLeadMinutes > 0) {
                    val advTime = checkCal.timeInMillis - (habit.reminderLeadMinutes * 60L * 1000L)
                    if (advTime > minThreshold) {
                        candidates.add(
                            ScheduledReminderCandidate(
                                triggerTime = advTime,
                                type = TYPE_HABIT_ADVANCE,
                                entityId = habit.id,
                                title = "Hábito em ${habit.reminderLeadMinutes} min: ${habit.title}",
                                message = "Horário programado: ${habit.reminderTime}. Prepare-se!",
                                soundUri = sound,
                                loopAlarm = false
                            )
                        )
                        foundFutureForHabit = true
                    }
                }

                // 2. Exact time reminder
                if (checkCal.timeInMillis > minThreshold) {
                    candidates.add(
                        ScheduledReminderCandidate(
                            triggerTime = checkCal.timeInMillis,
                            type = TYPE_HABIT_EXACT,
                            entityId = habit.id,
                            title = "Hora do Hábito: ${habit.title}",
                            message = "Hora de praticar seu hábito! Mantenha a constância hoje.",
                            soundUri = sound,
                            loopAlarm = habit.loopAlarm
                        )
                    )
                    foundFutureForHabit = true
                }

                // 3. Missed-habit reminder (+5 min after deadline if enabled)
                if (notifyMissedHabits) {
                    val missedTime = checkCal.timeInMillis + (5L * 60L * 1000L)
                    if (missedTime > minThreshold) {
                        candidates.add(
                            ScheduledReminderCandidate(
                                triggerTime = missedTime,
                                type = TYPE_HABIT_MISSED,
                                entityId = habit.id,
                                title = "Hábito não realizado: ${habit.title}",
                                message = "Já passaram 5 minutos do horário. Ainda dá tempo de completar hoje!",
                                soundUri = sound,
                                loopAlarm = false
                            )
                        )
                        foundFutureForHabit = true
                    }
                }

                if (foundFutureForHabit) break
            }
        }

        // Include active habit snoozes
        snoozes.filter {
            (it.type == NotificationHelper.TYPE_HABIT || it.type == NotificationHelper.TYPE_HABIT_MISSED) &&
                it.triggerTime > minThreshold
        }.forEach { snooze ->
            candidates.add(
                ScheduledReminderCandidate(
                    triggerTime = snooze.triggerTime,
                    type = TYPE_HABIT_SNOOZE,
                    entityId = snooze.entityId,
                    title = snooze.title.ifBlank { "Hábito Adiado" },
                    message = "Lembrete adiado por 10 minutos.",
                    soundUri = snooze.soundUri.ifBlank { defaultHabitSound },
                    loopAlarm = false
                )
            )
        }

        return candidates.minByOrNull { it.triggerTime }
    }

    private fun applyTaskSlot(
        context: Context,
        alarmManager: AlarmManager,
        candidate: ScheduledReminderCandidate?
    ) {
        if (candidate == null) {
            DirectBootAlarmStore.saveTaskSlot(context, null)
            cancelSlot(context, alarmManager, REQUEST_CODE_NEXT_TASK, ACTION_TRIGGER_SCHEDULED_TASK)
            return
        }

        val slotData = DirectBootSlotRecord(
            slotKey = "task",
            triggerTime = candidate.triggerTime,
            type = candidate.type,
            entityId = candidate.entityId,
            title = candidate.title,
            message = candidate.message,
            soundUri = candidate.soundUri,
            loopAlarm = candidate.loopAlarm,
            isDisruptive = candidate.isDisruptive
        )
        DirectBootAlarmStore.saveTaskSlot(context, slotData)

        val pending = buildSlotPendingIntent(
            context = context,
            requestCode = REQUEST_CODE_NEXT_TASK,
            action = ACTION_TRIGGER_SCHEDULED_TASK,
            slot = slotData,
            isHabitSlot = false
        )
        NotificationHelper.tryScheduleExact(context, alarmManager, candidate.triggerTime, pending)
    }

    private fun applyHabitSlot(
        context: Context,
        alarmManager: AlarmManager,
        candidate: ScheduledReminderCandidate?
    ) {
        if (candidate == null) {
            DirectBootAlarmStore.saveHabitSlot(context, null)
            cancelSlot(context, alarmManager, REQUEST_CODE_NEXT_HABIT, ACTION_TRIGGER_SCHEDULED_HABIT)
            return
        }

        val slotData = DirectBootSlotRecord(
            slotKey = "habit",
            triggerTime = candidate.triggerTime,
            type = candidate.type,
            entityId = candidate.entityId,
            title = candidate.title,
            message = candidate.message,
            soundUri = candidate.soundUri,
            loopAlarm = candidate.loopAlarm,
            isDisruptive = false
        )
        DirectBootAlarmStore.saveHabitSlot(context, slotData)

        val pending = buildSlotPendingIntent(
            context = context,
            requestCode = REQUEST_CODE_NEXT_HABIT,
            action = ACTION_TRIGGER_SCHEDULED_HABIT,
            slot = slotData,
            isHabitSlot = true
        )
        NotificationHelper.tryScheduleExact(context, alarmManager, candidate.triggerTime, pending)
    }

    /**
     * Used during `LOCKED_BOOT_COMPLETED` (Direct Boot before PIN unlock) when Room & regular
     * SharedPreferences in Credential Encrypted storage are not yet accessible.
     */
    fun scheduleSlotsFromDirectBootStore(context: Context) {
        val appCtx = context.applicationContext ?: context
        val alarmManager = appCtx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val now = System.currentTimeMillis()

        DirectBootAlarmStore.loadTaskSlot(appCtx)?.let { taskSlot ->
            if (taskSlot.triggerTime > now) {
                val p = buildSlotPendingIntent(
                    context = appCtx,
                    requestCode = REQUEST_CODE_NEXT_TASK,
                    action = ACTION_TRIGGER_SCHEDULED_TASK,
                    slot = taskSlot,
                    isHabitSlot = false
                )
                NotificationHelper.tryScheduleExact(appCtx, alarmManager, taskSlot.triggerTime, p)
            }
        }

        DirectBootAlarmStore.loadHabitSlot(appCtx)?.let { habitSlot ->
            if (habitSlot.triggerTime > now) {
                val p = buildSlotPendingIntent(
                    context = appCtx,
                    requestCode = REQUEST_CODE_NEXT_HABIT,
                    action = ACTION_TRIGGER_SCHEDULED_HABIT,
                    slot = habitSlot,
                    isHabitSlot = true
                )
                NotificationHelper.tryScheduleExact(appCtx, alarmManager, habitSlot.triggerTime, p)
            }
        }

        val (timerRunning, timerEndMs, timerPhase) = DirectBootAlarmStore.loadTimerSlot(appCtx)
        if (timerRunning && timerEndMs > now) {
            NotificationHelper.scheduleTimerEndAlarm(
                context = appCtx,
                triggerTimeMillis = timerEndMs,
                phaseLabel = if (timerPhase == "BREAK") "Pausa" else "Foco"
            )
        }
    }

    private fun buildSlotPendingIntent(
        context: Context,
        requestCode: Int,
        action: String,
        slot: DirectBootSlotRecord,
        isHabitSlot: Boolean
    ): PendingIntent {
        val notificationType = when {
            slot.type == TYPE_HABIT_MISSED -> NotificationHelper.TYPE_HABIT_MISSED
            isHabitSlot -> NotificationHelper.TYPE_HABIT
            else -> NotificationHelper.TYPE_TASK
        }
        val intent = Intent(context, CitraNotificationReceiver::class.java).apply {
            this.action = action
            putExtra(NotificationHelper.EXTRA_TYPE, notificationType)
            putExtra(NotificationHelper.EXTRA_TITLE, slot.title)
            putExtra(NotificationHelper.EXTRA_MESSAGE, slot.message)
            putExtra(
                NotificationHelper.EXTRA_ID,
                if (isHabitSlot) 20000 + (slot.entityId % 10000).toInt() else (slot.entityId % 10000).toInt()
            )
            putExtra(NotificationHelper.EXTRA_SOUND_URI, slot.soundUri)
            putExtra(NotificationHelper.EXTRA_LOOP_ALARM, slot.loopAlarm)
            putExtra(NotificationHelper.EXTRA_IS_DISRUPTIVE, slot.isDisruptive)
            if (isHabitSlot) {
                putExtra(NotificationHelper.EXTRA_HABIT_ID, slot.entityId)
            } else {
                putExtra(NotificationHelper.EXTRA_TASK_ID, slot.entityId)
            }
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun cancelSlot(
        context: Context,
        alarmManager: AlarmManager,
        requestCode: Int,
        action: String
    ) {
        try {
            val intent = Intent(context, CitraNotificationReceiver::class.java).apply {
                this.action = action
            }
            val p = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(p)
        } catch (_: Exception) {}
    }
}
