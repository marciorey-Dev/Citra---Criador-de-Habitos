package com.example.notifications

import android.content.Context
import com.example.util.SoundEffectsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ActiveAlarmData(
    val notificationId: Int,
    val type: String, // "task" or "habit"
    val title: String,
    val taskId: Long = -1L,
    val habitId: Long = -1L,
    val soundUri: String = "",
    val loopAlarm: Boolean = true
)

object ActiveAlarmManager {
    private val _activeAlarm = MutableStateFlow<ActiveAlarmData?>(null)
    val activeAlarm: StateFlow<ActiveAlarmData?> = _activeAlarm.asStateFlow()

    fun onAlarmTriggered(context: Context, data: ActiveAlarmData) {
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
            .apply()
    }

    fun checkAndRestore(context: Context): ActiveAlarmData? {
        if (_activeAlarm.value != null && SoundEffectsManager.isAlarmPlaying()) {
            return _activeAlarm.value
        }
        val prefs = context.getSharedPreferences("citra_active_alarm", Context.MODE_PRIVATE)
        val isRinging = prefs.getBoolean("is_ringing", false)
        if (isRinging && SoundEffectsManager.isAlarmPlaying()) {
            val data = ActiveAlarmData(
                notificationId = prefs.getInt("notification_id", -1),
                type = prefs.getString("type", NotificationHelper.TYPE_TASK) ?: NotificationHelper.TYPE_TASK,
                title = prefs.getString("title", "Citra") ?: "Citra",
                taskId = prefs.getLong("task_id", -1L),
                habitId = prefs.getLong("habit_id", -1L),
                soundUri = prefs.getString("sound_uri", "") ?: "",
                loopAlarm = true
            )
            _activeAlarm.value = data
            return data
        } else if (!SoundEffectsManager.isAlarmPlaying()) {
            _activeAlarm.value = null
            prefs.edit().clear().apply()
        }
        return _activeAlarm.value
    }

    fun dismissAlarm(context: Context) {
        _activeAlarm.value = null
        val prefs = context.getSharedPreferences("citra_active_alarm", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        SoundEffectsManager.stopAlarmAudio()
    }
}
