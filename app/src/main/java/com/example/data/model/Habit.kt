package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val category: String = "Saúde",
    val iconEmoji: String = "✨",
    val reminderTime: String = "08:00", // "HH:mm"
    val reminderLeadMinutes: Int = 10,  // Antecedência configurada individualmente no hábito
    val frequencyDays: String = "ALL", // "ALL" or comma-separated day integers (1=Sun, 2=Mon... 7=Sat)
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val lastCompletedDate: String = "", // e.g. "2026-10-27"
    val completedDatesCsv: String = "", // comma-separated YYYY-MM-DD
    val customSoundUri: String = "",
    val loopAlarm: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

fun Habit.calculateWeeklyStreak(todayCal: java.util.Calendar = java.util.Calendar.getInstance()): Int {
    val completedSet = completedDatesCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
    val mondayCal = (todayCal.clone() as java.util.Calendar).apply {
        firstDayOfWeek = java.util.Calendar.MONDAY
        set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
    val checkCal = (mondayCal.clone() as java.util.Calendar)
    val todayStr = sdf.format(todayCal.time)

    var streak = 0
    while (true) {
        val dateStr = sdf.format(checkCal.time)
        if (dateStr > todayStr) break
        if (completedSet.contains(dateStr)) {
            streak++
        }
        checkCal.add(java.util.Calendar.DAY_OF_MONTH, 1)
        if (checkCal.get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.MONDAY) break
    }
    return streak
}

fun Habit.isScheduledForDate(dateStr: String, dateCal: java.util.Calendar): Boolean {
    // If completed on this date, always include it
    if (completedDatesCsv.split(",").any { it.trim() == dateStr }) return true

    val cleanFreq = frequencyDays.trim()
    return when {
        cleanFreq.equals("ALL", ignoreCase = true) || cleanFreq.isBlank() || cleanFreq.equals("Todos os dias", ignoreCase = true) -> true
        cleanFreq.equals("ALTERNATE", ignoreCase = true) || cleanFreq.equals("ALTERNADO", ignoreCase = true) -> {
            val createdCal = java.util.Calendar.getInstance().apply {
                timeInMillis = createdAt
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            val targetZero = (dateCal.clone() as java.util.Calendar).apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            val diffDays = ((targetZero.timeInMillis - createdCal.timeInMillis) / (1000L * 60 * 60 * 24)).toInt()
            diffDays >= 0 && (diffDays % 2 == 0)
        }
        else -> {
            val dayOfWeek = dateCal.get(java.util.Calendar.DAY_OF_WEEK)
            val daysList = cleanFreq.split(",").mapNotNull { it.trim().toIntOrNull() }
            if (daysList.isEmpty()) true else daysList.contains(dayOfWeek)
        }
    }
}

fun Habit.getFrequencyLabel(): String {
    val cleanFreq = frequencyDays.trim()
    return when {
        cleanFreq.equals("ALL", ignoreCase = true) || cleanFreq.isBlank() || cleanFreq.equals("Todos os dias", ignoreCase = true) -> "Diário"
        cleanFreq.equals("ALTERNATE", ignoreCase = true) || cleanFreq.equals("ALTERNADO", ignoreCase = true) -> "Dia sim, dia não"
        cleanFreq == "2,3,4,5,6" -> "Dias úteis (Seg-Sex)"
        cleanFreq == "1,7" -> "Fim de semana (Sáb-Dom)"
        cleanFreq == "7" -> "Todo sábado"
        cleanFreq == "1" -> "Todo domingo"
        else -> {
            val dayMap = mapOf(
                1 to "Dom", 2 to "Seg", 3 to "Ter", 4 to "Qua", 5 to "Qui", 6 to "Sex", 7 to "Sáb"
            )
            val parts = cleanFreq.split(",").mapNotNull { it.trim().toIntOrNull() }
            if (parts.isEmpty()) "Diário"
            else parts.sorted().joinToString(", ") { dayMap[it] ?: "Dia $it" }
        }
    }
}
