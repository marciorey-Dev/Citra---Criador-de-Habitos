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
    val createdAt: Long = System.currentTimeMillis()
)
