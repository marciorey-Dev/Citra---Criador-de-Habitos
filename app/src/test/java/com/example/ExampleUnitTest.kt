package com.example

import com.example.data.model.Habit
import com.example.data.model.Task
import com.example.data.model.TaskPriority
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun task_creation_defaults() {
        val task = Task(
            title = "Estudar Kotlin e Compose",
            category = "Estudos",
            priority = TaskPriority.ALTA
        )
        assertEquals("Estudar Kotlin e Compose", task.title)
        assertEquals("Estudos", task.category)
        assertEquals(TaskPriority.ALTA, task.priority)
        assertFalse(task.isCompleted)
        assertEquals(0, task.focusMinutesCompleted)
    }

    @Test
    fun habit_streak_tracking() {
        val habit = Habit(
            title = "Beber 2L de água",
            category = "Saúde",
            iconEmoji = "💧",
            reminderTime = "08:30",
            frequencyDays = "ALL",
            currentStreak = 3,
            bestStreak = 5,
            lastCompletedDate = "2026-10-26",
            completedDatesCsv = "2026-10-24,2026-10-25,2026-10-26"
        )
        val dates = habit.completedDatesCsv.split(",")
        assertEquals(3, dates.size)
        assertTrue(dates.contains("2026-10-26"))
        assertEquals(3, habit.currentStreak)
    }
}
