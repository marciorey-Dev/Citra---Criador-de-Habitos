package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TaskPriority(val label: String) {
    ALTA("Alta"),
    MEDIA("Média"),
    BAIXA("Baixa")
}

@Entity(
    tableName = "tasks",
    indices = [
        Index(value = ["isCompleted"]),
        Index(value = ["isWontDo"]),
        Index(value = ["dueDateTimestamp"])
    ]
)
data class Task(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val category: String = "Estudos",
    val priority: TaskPriority = TaskPriority.MEDIA,
    val dueDateTimestamp: Long? = null,
    val dueDateText: String = "Hoje", // e.g. "YYYY-MM-DD" or formatted text
    val reminderMinutesBefore: Int = 0, // 0 = no momento, 15, 30, 60, 1440 (1 dia)
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val focusMinutesCompleted: Int = 0,
    val customSoundUri: String = "",
    val loopAlarm: Boolean = false,
    val isDisruptive: Boolean = false,
    val isWontDo: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

private val taskIsoDateTimeFormat = object : ThreadLocal<java.text.SimpleDateFormat>() {
    override fun initialValue(): java.text.SimpleDateFormat =
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
}
private val taskIsoDateFormat = object : ThreadLocal<java.text.SimpleDateFormat>() {
    override fun initialValue(): java.text.SimpleDateFormat =
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
}
private val taskBrDateTimeFormat = object : ThreadLocal<java.text.SimpleDateFormat>() {
    override fun initialValue(): java.text.SimpleDateFormat =
        java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.US)
}
private val taskBrDateFormat = object : ThreadLocal<java.text.SimpleDateFormat>() {
    override fun initialValue(): java.text.SimpleDateFormat =
        java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.US)
}

fun Task.getEffectiveDueTimestamp(): Long? {
    dueDateTimestamp?.let { if (it > 0) return it }
    return try {
        if (dueDateText.isNotBlank()) {
            val clean = dueDateText.trim()
            val sdf = when {
                clean.contains("-") && clean.contains(":") -> taskIsoDateTimeFormat.get()
                clean.contains("-") -> taskIsoDateFormat.get()
                clean.contains("/") && clean.contains(":") -> taskBrDateTimeFormat.get()
                clean.contains("/") -> taskBrDateFormat.get()
                else -> null
            }
            sdf?.parse(clean)?.time
        } else null
    } catch (_: Exception) { null }
}

fun Task.isReadyForMainList(now: Long = System.currentTimeMillis()): Boolean {
    // 72 hours window in milliseconds (72 * 60 * 60 * 1000L)
    val maxLeadTimeMs = 72L * 60L * 60L * 1000L

    val targetTs = getEffectiveDueTimestamp() ?: return true // No due date set: immediately visible

    val diff = targetTs - now
    // If due date is more than 72 hours in future, stay hidden from main lists until 72h window
    return diff <= maxLeadTimeMs
}
