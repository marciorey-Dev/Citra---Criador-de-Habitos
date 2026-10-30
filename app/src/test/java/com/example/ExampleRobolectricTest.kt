package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.backup.BackupManager
import com.example.data.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("HabitCraft", appName)
    }

    @Test
    fun `backup manager json roundtrip`() {
        val task = Task(id = 1L, title = "Revisão", priority = TaskPriority.ALTA)
        val habit = Habit(id = 1L, title = "Exercício", currentStreak = 2)
        val session = FocusSession(id = 1L, category = "Estudos", durationMinutes = 45)
        val category = Category(name = "Programação", emoji = "💻", isCustom = true)
        val achievement = Achievement(id = "focus_45", title = "Imersão", description = "45m", iconEmoji = "🎨", category = "Foco")

        val json = BackupManager.createBackupJson(
            tasks = listOf(task),
            habits = listOf(habit),
            focusSessions = listOf(session),
            categories = listOf(category),
            achievements = listOf(achievement)
        )
        assertTrue(json.contains("Programação"))
        assertTrue(json.contains("Revisão"))

        val parsed = BackupManager.parseBackupJson(json)
        assertEquals(1, parsed.tasks.size)
        assertEquals("Revisão", parsed.tasks[0].title)
        assertEquals(1, parsed.habits.size)
        assertEquals("Exercício", parsed.habits[0].title)
        assertEquals(1, parsed.categories.size)
        assertEquals("Programação", parsed.categories[0].name)
    }
}
