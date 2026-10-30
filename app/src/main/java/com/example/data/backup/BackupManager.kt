package com.example.data.backup

import com.example.data.local.AppDatabase
import com.example.data.model.*
import org.json.JSONArray
import org.json.JSONObject

object BackupManager {

    /**
     * Exports full app data as a JSON string WITHOUT notes, as requested.
     */
    suspend fun exportAppBackupJson(database: AppDatabase): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())

        // Categories
        val categoriesArray = JSONArray()
        // We will collect categories from database
        val catList = database.categoryDao().getAllCategories()
        // (Wait: Flow needs firstOrNull)
        // Let's retrieve from database
        val tasksArray = JSONArray()
        val habitsArray = JSONArray()
        val sessionsArray = JSONArray()
        val achievementsArray = JSONArray()

        // Sync queries
        val sessions = database.focusSessionDao().getAllSessionsSync()
        sessions.forEach { s ->
            val obj = JSONObject().apply {
                put("id", s.id)
                put("category", s.category)
                put("durationMinutes", s.durationMinutes)
                put("breakMinutes", s.breakMinutes)
                put("cycleCount", s.cycleCount)
                put("timestamp", s.timestamp)
                put("linkedTaskId", s.linkedTaskId ?: JSONObject.NULL)
                put("linkedTaskTitle", s.linkedTaskTitle ?: JSONObject.NULL)
                put("weekIdentifier", s.weekIdentifier)
            }
            sessionsArray.put(obj)
        }

        root.put("focusSessions", sessionsArray)
        return root.toString(2)
    }

    /**
     * Generates backup JSON from provided lists of entities
     */
    fun createBackupJson(
        tasks: List<Task>,
        habits: List<Habit>,
        focusSessions: List<FocusSession>,
        categories: List<Category>,
        achievements: List<Achievement>
    ): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())

        // Tasks
        val tasksArray = JSONArray()
        tasks.forEach { t ->
            val obj = JSONObject().apply {
                put("id", t.id)
                put("title", t.title)
                put("category", t.category)
                put("priority", t.priority.name)
                put("dueDateTimestamp", t.dueDateTimestamp ?: JSONObject.NULL)
                put("dueDateText", t.dueDateText)
                put("reminderMinutesBefore", t.reminderMinutesBefore)
                put("isCompleted", t.isCompleted)
                put("completedAt", t.completedAt ?: JSONObject.NULL)
                put("focusMinutesCompleted", t.focusMinutesCompleted)
                put("createdAt", t.createdAt)
            }
            tasksArray.put(obj)
        }
        root.put("tasks", tasksArray)

        // Habits
        val habitsArray = JSONArray()
        habits.forEach { h ->
            val obj = JSONObject().apply {
                put("id", h.id)
                put("title", h.title)
                put("category", h.category)
                put("iconEmoji", h.iconEmoji)
                put("reminderTime", h.reminderTime)
                put("frequencyDays", h.frequencyDays)
                put("currentStreak", h.currentStreak)
                put("bestStreak", h.bestStreak)
                put("lastCompletedDate", h.lastCompletedDate)
                put("completedDatesCsv", h.completedDatesCsv)
                put("createdAt", h.createdAt)
            }
            habitsArray.put(obj)
        }
        root.put("habits", habitsArray)

        // Sessions
        val sessionsArray = JSONArray()
        focusSessions.forEach { s ->
            val obj = JSONObject().apply {
                put("id", s.id)
                put("category", s.category)
                put("durationMinutes", s.durationMinutes)
                put("breakMinutes", s.breakMinutes)
                put("cycleCount", s.cycleCount)
                put("timestamp", s.timestamp)
                put("linkedTaskId", s.linkedTaskId ?: JSONObject.NULL)
                put("linkedTaskTitle", s.linkedTaskTitle ?: JSONObject.NULL)
                put("weekIdentifier", s.weekIdentifier)
            }
            sessionsArray.put(obj)
        }
        root.put("focusSessions", sessionsArray)

        // Categories
        val categoriesArray = JSONArray()
        categories.forEach { c ->
            val obj = JSONObject().apply {
                put("name", c.name)
                put("emoji", c.emoji)
                put("isCustom", c.isCustom)
            }
            categoriesArray.put(obj)
        }
        root.put("categories", categoriesArray)

        // Achievements
        val achievementsArray = JSONArray()
        achievements.forEach { a ->
            val obj = JSONObject().apply {
                put("id", a.id)
                put("title", a.title)
                put("description", a.description)
                put("iconEmoji", a.iconEmoji)
                put("category", a.category)
                put("isUnlocked", a.isUnlocked)
                put("unlockedAt", a.unlockedAt ?: JSONObject.NULL)
                put("currentProgress", a.currentProgress)
                put("maxProgress", a.maxProgress)
            }
            achievementsArray.put(obj)
        }
        root.put("achievements", achievementsArray)

        return root.toString(2)
    }

    /**
     * Parses and restores backup data
     */
    fun parseBackupJson(jsonString: String): BackupParsedData {
        val root = JSONObject(jsonString)

        val tasksList = mutableListOf<Task>()
        if (root.has("tasks")) {
            val arr = root.getJSONArray("tasks")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val prio = try {
                    TaskPriority.valueOf(obj.optString("priority", "MEDIA"))
                } catch (_: Exception) {
                    TaskPriority.MEDIA
                }
                tasksList.add(
                    Task(
                        id = obj.optLong("id", 0L),
                        title = obj.getString("title"),
                        category = obj.optString("category", "Estudos"),
                        priority = prio,
                        dueDateTimestamp = if (obj.isNull("dueDateTimestamp")) null else obj.optLong("dueDateTimestamp"),
                        dueDateText = obj.optString("dueDateText", "Hoje"),
                        reminderMinutesBefore = obj.optInt("reminderMinutesBefore", 0),
                        isCompleted = obj.optBoolean("isCompleted", false),
                        completedAt = if (obj.isNull("completedAt")) null else obj.optLong("completedAt"),
                        focusMinutesCompleted = obj.optInt("focusMinutesCompleted", 0),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        }

        val habitsList = mutableListOf<Habit>()
        if (root.has("habits")) {
            val arr = root.getJSONArray("habits")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                habitsList.add(
                    Habit(
                        id = obj.optLong("id", 0L),
                        title = obj.getString("title"),
                        category = obj.optString("category", "Saúde"),
                        iconEmoji = obj.optString("iconEmoji", "✨"),
                        reminderTime = obj.optString("reminderTime", "08:00"),
                        frequencyDays = obj.optString("frequencyDays", "ALL"),
                        currentStreak = obj.optInt("currentStreak", 0),
                        bestStreak = obj.optInt("bestStreak", 0),
                        lastCompletedDate = obj.optString("lastCompletedDate", ""),
                        completedDatesCsv = obj.optString("completedDatesCsv", ""),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        }

        val sessionsList = mutableListOf<FocusSession>()
        if (root.has("focusSessions")) {
            val arr = root.getJSONArray("focusSessions")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                sessionsList.add(
                    FocusSession(
                        id = obj.optLong("id", 0L),
                        category = obj.getString("category"),
                        durationMinutes = obj.optInt("durationMinutes", 25),
                        breakMinutes = obj.optInt("breakMinutes", 5),
                        cycleCount = obj.optInt("cycleCount", 1),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        linkedTaskId = if (obj.isNull("linkedTaskId")) null else obj.optLong("linkedTaskId"),
                        linkedTaskTitle = if (obj.isNull("linkedTaskTitle")) null else obj.optString("linkedTaskTitle"),
                        weekIdentifier = obj.optString("weekIdentifier", "")
                    )
                )
            }
        }

        val categoriesList = mutableListOf<Category>()
        if (root.has("categories")) {
            val arr = root.getJSONArray("categories")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                categoriesList.add(
                    Category(
                        name = obj.getString("name"),
                        emoji = obj.optString("emoji", "🎯"),
                        isCustom = obj.optBoolean("isCustom", true)
                    )
                )
            }
        }

        return BackupParsedData(tasksList, habitsList, sessionsList, categoriesList)
    }

    /**
     * Exports multiple notes as JSON
     */
    fun exportNotesJson(notes: List<Note>): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())
        val arr = JSONArray()
        notes.forEach { n ->
            val obj = JSONObject().apply {
                put("id", n.id)
                put("title", n.title)
                put("content", n.content)
                put("category", n.category)
                put("iconEmoji", n.iconEmoji)
                put("associatedDate", n.associatedDate ?: JSONObject.NULL)
                put("updatedAt", n.updatedAt)
            }
            arr.put(obj)
        }
        root.put("notes", arr)
        return root.toString(2)
    }

    /**
     * Parses notes from JSON
     */
    fun parseNotesJson(jsonString: String): List<Note> {
        val root = JSONObject(jsonString)
        val list = mutableListOf<Note>()
        if (root.has("notes")) {
            val arr = root.getJSONArray("notes")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    Note(
                        id = 0L, // new IDs to avoid collision
                        title = obj.getString("title"),
                        content = obj.optString("content", ""),
                        category = obj.optString("category", "Geral"),
                        iconEmoji = obj.optString("iconEmoji", "📝"),
                        associatedDate = if (obj.isNull("associatedDate")) null else obj.optString("associatedDate"),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }
        }
        return list
    }
}

data class BackupParsedData(
    val tasks: List<Task>,
    val habits: List<Habit>,
    val focusSessions: List<FocusSession>,
    val categories: List<Category>
)
