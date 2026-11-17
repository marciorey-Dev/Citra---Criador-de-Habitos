package com.example.notifications

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import org.json.JSONObject

data class DirectBootSlotRecord(
    val slotKey: String,
    val triggerTime: Long,
    val type: String,
    val entityId: Long,
    val title: String,
    val message: String,
    val soundUri: String,
    val loopAlarm: Boolean,
    val isDisruptive: Boolean
)

/**
 * Mirrors scheduled alarm slots and active timer metadata into Device Encrypted (DE) storage
 * so BootReceiver (directBootAware = true) can restore exact AlarmClock slots on LOCKED_BOOT_COMPLETED
 * before the user enters their lockscreen PIN/pattern.
 */
object DirectBootAlarmStore {
    private const val PREFS_NAME = "citra_direct_boot_alarms"
    private const val KEY_TASK_SLOT = "slot_task_json"
    private const val KEY_HABIT_SLOT = "slot_habit_json"
    private const val KEY_TIMER_END_MS = "timer_target_end_ms"
    private const val KEY_TIMER_PHASE = "timer_phase_name"
    private const val KEY_TIMER_RUNNING = "timer_is_running"
    private const val KEY_TIMER_TOKEN = "timer_session_token"

    private fun getDePrefs(context: Context): SharedPreferences {
        val deContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                context.createDeviceProtectedStorageContext()
            } catch (_: Exception) {
                context
            }
        } else {
            context
        }
        return deContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveTaskSlot(context: Context, record: DirectBootSlotRecord?) {
        val prefs = getDePrefs(context)
        if (record == null) {
            prefs.edit().remove(KEY_TASK_SLOT).apply()
            return
        }
        prefs.edit().putString(KEY_TASK_SLOT, encodeRecord(record)).apply()
    }

    fun loadTaskSlot(context: Context): DirectBootSlotRecord? {
        val raw = getDePrefs(context).getString(KEY_TASK_SLOT, null) ?: return null
        return decodeRecord(raw)
    }

    fun saveHabitSlot(context: Context, record: DirectBootSlotRecord?) {
        val prefs = getDePrefs(context)
        if (record == null) {
            prefs.edit().remove(KEY_HABIT_SLOT).apply()
            return
        }
        prefs.edit().putString(KEY_HABIT_SLOT, encodeRecord(record)).apply()
    }

    fun loadHabitSlot(context: Context): DirectBootSlotRecord? {
        val raw = getDePrefs(context).getString(KEY_HABIT_SLOT, null) ?: return null
        return decodeRecord(raw)
    }

    fun saveTimerSlot(
        context: Context,
        isRunning: Boolean,
        targetEndTimeMillis: Long,
        phaseName: String,
        sessionToken: String
    ) {
        getDePrefs(context).edit()
            .putBoolean(KEY_TIMER_RUNNING, isRunning)
            .putLong(KEY_TIMER_END_MS, if (isRunning) targetEndTimeMillis else 0L)
            .putString(KEY_TIMER_PHASE, phaseName)
            .putString(KEY_TIMER_TOKEN, sessionToken)
            .apply()
    }

    fun clearTimerSlot(context: Context) {
        getDePrefs(context).edit()
            .putBoolean(KEY_TIMER_RUNNING, false)
            .putLong(KEY_TIMER_END_MS, 0L)
            .remove(KEY_TIMER_PHASE)
            .remove(KEY_TIMER_TOKEN)
            .apply()
    }

    fun loadTimerSlot(context: Context): Triple<Boolean, Long, String> {
        val prefs = getDePrefs(context)
        val running = prefs.getBoolean(KEY_TIMER_RUNNING, false)
        val endMs = prefs.getLong(KEY_TIMER_END_MS, 0L)
        val phase = prefs.getString(KEY_TIMER_PHASE, "FOCUS") ?: "FOCUS"
        return Triple(running, endMs, phase)
    }

    fun clearAll(context: Context) {
        getDePrefs(context).edit().clear().apply()
    }

    private fun encodeRecord(record: DirectBootSlotRecord): String {
        return JSONObject().apply {
            put("slotKey", record.slotKey)
            put("triggerTime", record.triggerTime)
            put("type", record.type)
            put("entityId", record.entityId)
            put("title", record.title)
            put("message", record.message)
            put("soundUri", record.soundUri)
            put("loopAlarm", record.loopAlarm)
            put("isDisruptive", record.isDisruptive)
        }.toString()
    }

    private fun decodeRecord(raw: String): DirectBootSlotRecord? {
        return try {
            val obj = JSONObject(raw)
            DirectBootSlotRecord(
                slotKey = obj.optString("slotKey", ""),
                triggerTime = obj.optLong("triggerTime", 0L),
                type = obj.optString("type", ""),
                entityId = obj.optLong("entityId", -1L),
                title = obj.optString("title", "Citra"),
                message = obj.optString("message", ""),
                soundUri = obj.optString("soundUri", ""),
                loopAlarm = obj.optBoolean("loopAlarm", false),
                isDisruptive = obj.optBoolean("isDisruptive", false)
            ).takeIf { it.triggerTime > 0L }
        } catch (_: Exception) {
            null
        }
    }
}
