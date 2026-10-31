package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getInstance(context)
                    val tasks = db.taskDao().getAllTasksSync()
                    val habits = db.habitDao().getAllHabitsSync()

                    val prefs = PreferencesManager(context)
                    val settings = prefs.settings.value
                    val taskSound = if (settings.taskSoundUri.isNotBlank()) settings.taskSoundUri else settings.taskSound
                    val habitSound = if (settings.habitSoundUri.isNotBlank()) settings.habitSoundUri else settings.habitSound

                    // Ensure channels are ready
                    NotificationHelper.initNotificationChannels(context)

                    // Reschedule active tasks
                    tasks.filter { !it.isCompleted && it.dueDateTimestamp != null }.forEach { task ->
                        NotificationHelper.scheduleTaskReminder(
                            context = context,
                            taskId = task.id,
                            title = task.title,
                            dueTimestamp = task.dueDateTimestamp!!,
                            minutesBefore = task.reminderMinutesBefore,
                            customSoundUri = taskSound
                        )
                    }

                    // Reschedule daily habits
                    habits.filter { it.reminderTime.isNotBlank() }.forEach { habit ->
                        NotificationHelper.scheduleHabitReminder(
                            context = context,
                            habitId = habit.id,
                            title = habit.title,
                            timeString = habit.reminderTime,
                            leadMinutes = habit.reminderLeadMinutes,
                            customSoundUri = habitSound
                        )
                    }
                } catch (_: Exception) {
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
