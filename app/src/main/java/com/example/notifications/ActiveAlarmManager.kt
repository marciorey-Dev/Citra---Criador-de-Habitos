package com.example.notifications

import android.content.Context
import com.example.util.SoundEffectsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ActiveAlarmData(
    val notificationId: Int,
    val type: String, // "task"
    val title: String,
    val taskId: Long = -1L,
    val habitId: Long = -1L,
    val soundUri: String = "",
    val loopAlarm: Boolean = true,
    val triggeredAt: Long = System.currentTimeMillis()
)

object ActiveAlarmManager {
    private val _activeAlarm = MutableStateFlow<ActiveAlarmData?>(null)
    val activeAlarm: StateFlow<ActiveAlarmData?> = _activeAlarm.asStateFlow()

    fun onAlarmTriggered(context: Context, data: ActiveAlarmData) {
        // Habits must NEVER hijack the app with the 00:00 alarm screen
        if (data.type == NotificationHelper.TYPE_HABIT || data.type == NotificationHelper.TYPE_HABIT_MISSED) {
            return
        }

        _activeAlarm.value = data
        val prefs = context.getSharedPreferences("citra_active_alarm", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("is_ringing", true)
            .putInt("notification_id", data.notificationId)
            .putString("type", data.type)
            .putString("title", data.title)
            .putLong("task_id", data.taskId)
            .putLong("habit_id", data.habitId)
            .putString("sound_uri", data.soundUri)
            .putLong("triggered_at", System.currentTimeMillis())
            .apply()
    }

    fun checkAndRestore(context: Context): ActiveAlarmData? {
        if (_activeAlarm.value != null) {
            return _activeAlarm.value
        }
        val prefs = context.getSharedPreferences("citra_active_alarm", Context.MODE_PRIVATE)
        val isRinging = prefs.getBoolean("is_ringing", false)
        val triggeredAt = prefs.getLong("triggered_at", 0L)
        val now = System.currentTimeMillis()

        // Expire after 90 seconds or if alarm type was habit to avoid sticky 00:00 on launch
        val type = prefs.getString("type", NotificationHelper.TYPE_TASK) ?: NotificationHelper.TYPE_TASK
        if (isRinging && type != NotificationHelper.TYPE_HABIT && (now - triggeredAt) < 90_000L) {
            val data = ActiveAlarmData(
                notificationId = prefs.getInt("notification_id", -1),
                type = type,
                title = prefs.getString("title", "Citra") ?: "Citra",
                taskId = prefs.getLong("task_id", -1L),
                habitId = prefs.getLong("habit_id", -1L),
                soundUri = prefs.getString("sound_uri", "") ?: "",
                loopAlarm = true,
                triggeredAt = triggeredAt
            )
            _activeAlarm.value = data
            return data
        } else if (isRinging) {
            // Dismiss stale alarm record
            prefs.edit().clear().apply()
        }
        return null
    }

    fun dismissAlarm(context: Context) {
        _activeAlarm.value = null
        val prefs = context.getSharedPreferences("citra_active_alarm", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        SoundEffectsManager.stopAlarmAudio()
    }
}
