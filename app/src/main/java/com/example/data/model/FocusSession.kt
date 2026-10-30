package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "focus_sessions")
data class FocusSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val category: String, // Estudos, Trabalho, Leitura, etc.
    val durationMinutes: Int,
    val breakMinutes: Int = 5,
    val cycleCount: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val linkedTaskId: Long? = null,
    val linkedTaskTitle: String? = null,
    val weekIdentifier: String = "" // "YYYY-MM-DD" of Monday of that week
)
