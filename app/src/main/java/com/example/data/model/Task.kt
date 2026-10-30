package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TaskPriority(val label: String) {
    ALTA("Alta"),
    MEDIA("Média"),
    BAIXA("Baixa")
}

@Entity(tableName = "tasks")
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
    val createdAt: Long = System.currentTimeMillis()
)
