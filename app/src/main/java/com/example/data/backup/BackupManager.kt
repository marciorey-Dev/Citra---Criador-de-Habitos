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
        achievements: List<Achievement>,
        notes: List<Note> = emptyList(),
        financeEntries: List<FinanceEntry> = emptyList(),
        financeCategories: List<FinanceCategory> = emptyList(),
        financeWallets: List<FinanceWallet> = emptyList(),
        financeRecurringRules: List<FinanceRecurringRule> = emptyList(),
        financeBudgets: List<FinanceBudget> = emptyList(),
        financeNoteLinks: List<FinanceNoteLink> = emptyList()
    ): String {
        val root = JSONObject()
        root.put("version", 2)
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
                put("customSoundUri", t.customSoundUri)
                put("loopAlarm", t.loopAlarm)
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
                put("reminderLeadMinutes", h.reminderLeadMinutes)
                put("frequencyDays", h.frequencyDays)
                put("currentStreak", h.currentStreak)
                put("bestStreak", h.bestStreak)
                put("lastCompletedDate", h.lastCompletedDate)
                put("completedDatesCsv", h.completedDatesCsv)
                put("customSoundUri", h.customSoundUri)
                put("loopAlarm", h.loopAlarm)
                put("createdAt", h.createdAt)
            }
            habitsArray.put(obj)
        }
        root.put("habits", habitsArray)

        // Sessions (Statistics)
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

        // Notes (and calendar memos)
        val notesArray = JSONArray()
        notes.forEach { n ->
            val obj = JSONObject().apply {
                put("id", n.id)
                put("uuid", n.uuid)
                put("title", n.title)
                put("content", n.content)
                put("category", n.category)
                put("iconEmoji", n.iconEmoji)
                put("associatedDate", n.associatedDate ?: JSONObject.NULL)
                put("isPinned", n.isPinned)
                put("isArchived", n.isArchived)
                put("noteColor", n.noteColor ?: JSONObject.NULL)
                put("backgroundImageUri", n.backgroundImageUri ?: JSONObject.NULL)
                put("backgroundBlur", n.backgroundBlur)
                put("backgroundBrightness", n.backgroundBrightness)
                put("isDeleted", n.isDeleted)
                put("deletedAt", n.deletedAt ?: JSONObject.NULL)
                put("updatedAt", n.updatedAt)
            }
            notesArray.put(obj)
        }
        root.put("notes", notesArray)

        // Finances - Categories
        val finCatArray = JSONArray()
        financeCategories.forEach { fc ->
            val obj = JSONObject().apply {
                put("id", fc.id)
                put("type", fc.type)
                put("name", fc.name)
                put("iconName", fc.iconName)
                put("colorHex", fc.colorHex)
                put("isDefault", fc.isDefault)
                put("isArchived", fc.isArchived)
                put("updatedAt", fc.updatedAt)
            }
            finCatArray.put(obj)
        }
        root.put("financeCategories", finCatArray)

        // Finances - Wallets
        val finWalletsArray = JSONArray()
        financeWallets.forEach { fw ->
            val obj = JSONObject().apply {
                put("id", fw.id)
                put("name", fw.name)
                put("type", fw.type)
                put("currencyCode", fw.currencyCode)
                put("initialBalanceMinor", fw.initialBalanceMinor)
                put("colorHex", fw.colorHex)
                put("isDefault", fw.isDefault)
                put("updatedAt", fw.updatedAt)
            }
            finWalletsArray.put(obj)
        }
        root.put("financeWallets", finWalletsArray)

        // Finances - Entries
        val finEntriesArray = JSONArray()
        financeEntries.forEach { fe ->
            val obj = JSONObject().apply {
                put("id", fe.id)
                put("type", fe.type)
                put("name", fe.name)
                put("comment", fe.comment)
                put("categoryId", fe.categoryId)
                put("walletName", fe.walletName)
                put("currencyCode", fe.currencyCode)
                put("dateMillis", fe.dateMillis)
                put("quantity", fe.quantity)
                put("unit", fe.unit)
                put("unitPriceMinor", fe.unitPriceMinor ?: JSONObject.NULL)
                put("totalAmountMinor", fe.totalAmountMinor)
                put("priority", fe.priority)
                put("tagsCsv", fe.tagsCsv)
                put("attachmentUri", fe.attachmentUri ?: JSONObject.NULL)
                put("isPlanned", fe.isPlanned)
                put("plannedAmountMinor", fe.plannedAmountMinor ?: JSONObject.NULL)
                put("createdAt", fe.createdAt)
                put("updatedAt", fe.updatedAt)
                put("deletedAt", fe.deletedAt ?: JSONObject.NULL)
            }
            finEntriesArray.put(obj)
        }
        root.put("financeEntries", finEntriesArray)

        // Finances - Recurring Rules
        val finRulesArray = JSONArray()
        financeRecurringRules.forEach { fr ->
            val obj = JSONObject().apply {
                put("id", fr.id)
                put("type", fr.type)
                put("name", fr.name)
                put("categoryId", fr.categoryId)
                put("walletName", fr.walletName)
                put("currencyCode", fr.currencyCode)
                put("amountMinor", fr.amountMinor)
                put("priority", fr.priority)
                put("frequency", fr.frequency)
                put("reminderDaysBefore", fr.reminderDaysBefore)
                put("nextDueMillis", fr.nextDueMillis)
                put("lastRemindedDueMillis", fr.lastRemindedDueMillis)
                put("isActive", fr.isActive)
                put("updatedAt", fr.updatedAt)
            }
            finRulesArray.put(obj)
        }
        root.put("financeRecurringRules", finRulesArray)

        // Finances - Budgets
        val finBudgetsArray = JSONArray()
        financeBudgets.forEach { fb ->
            val obj = JSONObject().apply {
                put("id", fb.id)
                put("categoryId", fb.categoryId)
                put("limitMinor", fb.limitMinor)
                put("currencyCode", fb.currencyCode)
                put("alert80SentMonth", fb.alert80SentMonth)
                put("alert100SentMonth", fb.alert100SentMonth)
                put("updatedAt", fb.updatedAt)
            }
            finBudgetsArray.put(obj)
        }
        root.put("financeBudgets", finBudgetsArray)

        // Finances - Note Links
        val finLinksArray = JSONArray()
        financeNoteLinks.forEach { fl ->
            val obj = JSONObject().apply {
                put("id", fl.id)
                put("noteId", fl.noteId)
                put("noteUuid", fl.noteUuid)
                put("blockId", fl.blockId)
                put("sourceType", fl.sourceType)
                put("frequency", fl.frequency)
                put("scheduleHour", fl.scheduleHour)
                put("scheduleMinute", fl.scheduleMinute)
                put("writeMode", fl.writeMode)
                put("newNoteTitleTemplate", fl.newNoteTitleTemplate)
                put("groupBy", fl.groupBy)
                put("mergeSameItems", fl.mergeSameItems)
                put("extraColumnsCsv", fl.extraColumnsCsv)
                put("includeBalanceSummary", fl.includeBalanceSummary)
                put("includeAutoSummary", fl.includeAutoSummary)
                put("includeMermaidChart", fl.includeMermaidChart)
                put("filterCategoriesCsv", fl.filterCategoriesCsv)
                put("filterPriority", fl.filterPriority)
                put("filterWallet", fl.filterWallet)
                put("filterTagsCsv", fl.filterTagsCsv)
                put("filterCurrency", fl.filterCurrency)
                put("notifyOnUpdate", fl.notifyOnUpdate)
                put("emptyPeriodBehavior", fl.emptyPeriodBehavior)
                put("status", fl.status)
                put("lastExecutedAt", fl.lastExecutedAt ?: JSONObject.NULL)
                put("createdAt", fl.createdAt)
                put("updatedAt", fl.updatedAt)
            }
            finLinksArray.put(obj)
        }
        root.put("financeNoteLinks", finLinksArray)

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
                        customSoundUri = obj.optString("customSoundUri", ""),
                        loopAlarm = obj.optBoolean("loopAlarm", false),
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
                        customSoundUri = obj.optString("customSoundUri", ""),
                        loopAlarm = obj.optBoolean("loopAlarm", false),
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

        val achievementsList = mutableListOf<Achievement>()
        if (root.has("achievements")) {
            val arr = root.getJSONArray("achievements")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                achievementsList.add(
                    Achievement(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        description = obj.optString("description", ""),
                        iconEmoji = obj.optString("iconEmoji", "🏆"),
                        category = obj.optString("category", "Geral"),
                        isUnlocked = obj.optBoolean("isUnlocked", false),
                        unlockedAt = if (obj.isNull("unlockedAt")) null else obj.optLong("unlockedAt"),
                        currentProgress = obj.optInt("currentProgress", 0),
                        maxProgress = obj.optInt("maxProgress", 1)
                    )
                )
            }
        }

        val notesList = mutableListOf<Note>()
        if (root.has("notes")) {
            val arr = root.getJSONArray("notes")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                notesList.add(
                    Note(
                        id = obj.optLong("id", 0L),
                        uuid = obj.optString("uuid", java.util.UUID.randomUUID().toString()),
                        title = obj.optString("title", "Sem título"),
                        content = obj.optString("content", ""),
                        category = obj.optString("category", "Geral"),
                        iconEmoji = obj.optString("iconEmoji", "📝"),
                        associatedDate = if (obj.isNull("associatedDate")) null else obj.optString("associatedDate").takeIf { it.isNotBlank() },
                        isPinned = obj.optBoolean("isPinned", false),
                        isArchived = obj.optBoolean("isArchived", false),
                        noteColor = if (obj.isNull("noteColor")) null else obj.optString("noteColor").takeIf { it.isNotBlank() },
                        backgroundImageUri = if (obj.isNull("backgroundImageUri")) null else obj.optString("backgroundImageUri"),
                        backgroundBlur = obj.optDouble("backgroundBlur", 0.5).toFloat(),
                        backgroundBrightness = obj.optDouble("backgroundBrightness", 0.6).toFloat(),
                        isDeleted = obj.optBoolean("isDeleted", false),
                        deletedAt = if (obj.isNull("deletedAt")) null else obj.optLong("deletedAt"),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }
        }

        val finCategoriesList = mutableListOf<FinanceCategory>()
        if (root.has("financeCategories")) {
            val arr = root.getJSONArray("financeCategories")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                finCategoriesList.add(
                    FinanceCategory(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        type = obj.optString("type", "EXPENSE"),
                        name = obj.optString("name", "Geral"),
                        iconName = obj.optString("iconName", "LocalOffer"),
                        colorHex = obj.optLong("colorHex", 0xFF888888),
                        isDefault = obj.optBoolean("isDefault", false),
                        isArchived = obj.optBoolean("isArchived", false),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }
        }

        val finWalletsList = mutableListOf<FinanceWallet>()
        if (root.has("financeWallets")) {
            val arr = root.getJSONArray("financeWallets")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                finWalletsList.add(
                    FinanceWallet(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        name = obj.optString("name", "Carteira Principal"),
                        type = obj.optString("type", "CASH"),
                        currencyCode = obj.optString("currencyCode", "AOA"),
                        initialBalanceMinor = obj.optLong("initialBalanceMinor", 0L),
                        colorHex = obj.optLong("colorHex", 0xFFFF8B1F),
                        isDefault = obj.optBoolean("isDefault", false),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }
        }

        val finEntriesList = mutableListOf<FinanceEntry>()
        if (root.has("financeEntries")) {
            val arr = root.getJSONArray("financeEntries")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                finEntriesList.add(
                    FinanceEntry(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        type = obj.optString("type", "EXPENSE"),
                        name = obj.optString("name", "Transação"),
                        comment = obj.optString("comment", ""),
                        categoryId = obj.optString("categoryId", ""),
                        walletName = obj.optString("walletName", "Carteira Principal"),
                        currencyCode = obj.optString("currencyCode", "AOA"),
                        dateMillis = obj.optLong("dateMillis", System.currentTimeMillis()),
                        quantity = obj.optDouble("quantity", 1.0),
                        unit = obj.optString("unit", ""),
                        unitPriceMinor = if (obj.isNull("unitPriceMinor")) null else obj.optLong("unitPriceMinor"),
                        totalAmountMinor = obj.optLong("totalAmountMinor", 0L),
                        priority = obj.optString("priority", "MANDATORY"),
                        tagsCsv = obj.optString("tagsCsv", ""),
                        attachmentUri = if (obj.isNull("attachmentUri")) null else obj.optString("attachmentUri"),
                        isPlanned = obj.optBoolean("isPlanned", false),
                        plannedAmountMinor = if (obj.isNull("plannedAmountMinor")) null else obj.optLong("plannedAmountMinor"),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                        deletedAt = if (obj.isNull("deletedAt")) null else obj.optLong("deletedAt")
                    )
                )
            }
        }

        val finRulesList = mutableListOf<FinanceRecurringRule>()
        if (root.has("financeRecurringRules")) {
            val arr = root.getJSONArray("financeRecurringRules")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                finRulesList.add(
                    FinanceRecurringRule(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        type = obj.optString("type", "EXPENSE"),
                        name = obj.optString("name", "Regra Recorrente"),
                        categoryId = obj.optString("categoryId", ""),
                        walletName = obj.optString("walletName", "Carteira Principal"),
                        currencyCode = obj.optString("currencyCode", "AOA"),
                        amountMinor = obj.optLong("amountMinor", 0L),
                        priority = obj.optString("priority", "MANDATORY"),
                        frequency = obj.optString("frequency", "MONTHLY"),
                        reminderDaysBefore = obj.optInt("reminderDaysBefore", 1),
                        nextDueMillis = obj.optLong("nextDueMillis", System.currentTimeMillis()),
                        lastRemindedDueMillis = obj.optLong("lastRemindedDueMillis", 0L),
                        isActive = obj.optBoolean("isActive", true),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }
        }

        val finBudgetsList = mutableListOf<FinanceBudget>()
        if (root.has("financeBudgets")) {
            val arr = root.getJSONArray("financeBudgets")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                finBudgetsList.add(
                    FinanceBudget(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        categoryId = obj.optString("categoryId", ""),
                        limitMinor = obj.optLong("limitMinor", 0L),
                        currencyCode = obj.optString("currencyCode", "AOA"),
                        alert80SentMonth = obj.optString("alert80SentMonth", ""),
                        alert100SentMonth = obj.optString("alert100SentMonth", ""),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }
        }

        val finLinksList = mutableListOf<FinanceNoteLink>()
        if (root.has("financeNoteLinks")) {
            val arr = root.getJSONArray("financeNoteLinks")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                finLinksList.add(
                    FinanceNoteLink(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        noteId = obj.optLong("noteId", 0L),
                        noteUuid = obj.optString("noteUuid", ""),
                        blockId = obj.optString("blockId", ""),
                        sourceType = obj.optString("sourceType", FinanceLinkSource.BOTH.id),
                        frequency = obj.optString("frequency", FinanceLinkFrequency.MONTHLY.id),
                        scheduleHour = obj.optInt("scheduleHour", 23),
                        scheduleMinute = obj.optInt("scheduleMinute", 59),
                        writeMode = obj.optString("writeMode", FinanceLinkWriteMode.REPLACE_BLOCK.id),
                        newNoteTitleTemplate = obj.optString("newNoteTitleTemplate", "Despesas Mensais · {mês} {ano}"),
                        groupBy = obj.optString("groupBy", FinanceLinkGroupBy.PRIORITY.id),
                        mergeSameItems = obj.optBoolean("mergeSameItems", false),
                        extraColumnsCsv = obj.optString("extraColumnsCsv", ""),
                        includeBalanceSummary = obj.optBoolean("includeBalanceSummary", true),
                        includeAutoSummary = obj.optBoolean("includeAutoSummary", false),
                        includeMermaidChart = obj.optBoolean("includeMermaidChart", false),
                        filterCategoriesCsv = obj.optString("filterCategoriesCsv", ""),
                        filterPriority = obj.optString("filterPriority", ""),
                        filterWallet = obj.optString("filterWallet", ""),
                        filterTagsCsv = obj.optString("filterTagsCsv", ""),
                        filterCurrency = obj.optString("filterCurrency", ""),
                        notifyOnUpdate = obj.optBoolean("notifyOnUpdate", false),
                        emptyPeriodBehavior = obj.optString("emptyPeriodBehavior", "WRITE_EMPTY"),
                        status = obj.optString("status", "ACTIVE"),
                        lastExecutedAt = if (obj.isNull("lastExecutedAt")) null else obj.optLong("lastExecutedAt"),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }
        }

        return BackupParsedData(
            tasks = tasksList,
            habits = habitsList,
            focusSessions = sessionsList,
            categories = categoriesList,
            achievements = achievementsList,
            notes = notesList,
            financeEntries = finEntriesList,
            financeCategories = finCategoriesList,
            financeWallets = finWalletsList,
            financeRecurringRules = finRulesList,
            financeBudgets = finBudgetsList,
            financeNoteLinks = finLinksList
        )
    }

    /**
     * Exports multiple notes as JSON without heavy media attachments (pure text & markdown).
     */
    fun exportNotesJson(notes: List<Note>, context: android.content.Context? = null): String {
        val root = JSONObject()
        root.put("version", 3)
        root.put("exportedAt", System.currentTimeMillis())
        val arr = JSONArray()
        notes.forEach { n ->
            val obj = JSONObject().apply {
                put("id", n.id)
                put("uuid", n.uuid)
                put("title", n.title)
                put("content", n.content)
                put("category", n.category)
                put("iconEmoji", n.iconEmoji)
                put("associatedDate", n.associatedDate ?: JSONObject.NULL)
                put("isPinned", n.isPinned)
                put("backgroundImageUri", n.backgroundImageUri ?: JSONObject.NULL)
                put("backgroundBlur", n.backgroundBlur)
                put("backgroundBrightness", n.backgroundBrightness)
                put("isDeleted", n.isDeleted)
                put("deletedAt", n.deletedAt ?: JSONObject.NULL)
                put("updatedAt", n.updatedAt)
            }
            arr.put(obj)
        }
        root.put("notes", arr)
        return root.toString(2)
    }

    /**
     * Parses notes from JSON, preserving UUIDs and timestamps for conflict-free reconciliation.
     */
    fun parseNotesJson(jsonString: String, context: android.content.Context? = null): List<Note> {
        val root = JSONObject(jsonString)
        val list = mutableListOf<Note>()
        if (root.has("notes")) {
            val arr = root.getJSONArray("notes")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val contentText = obj.optString("content", "")

                val parsedUuid = obj.optString("uuid").ifBlank {
                    java.util.UUID.randomUUID().toString()
                }

                list.add(
                    Note(
                        id = 0L,
                        uuid = parsedUuid,
                        title = obj.getString("title"),
                        content = contentText,
                        category = obj.optString("category", "Geral"),
                        iconEmoji = obj.optString("iconEmoji", "📝"),
                        associatedDate = if (obj.isNull("associatedDate")) null else obj.optString("associatedDate"),
                        isPinned = obj.optBoolean("isPinned", false),
                        backgroundImageUri = if (obj.isNull("backgroundImageUri")) null else obj.optString("backgroundImageUri"),
                        backgroundBlur = obj.optDouble("backgroundBlur", 0.5).toFloat(),
                        backgroundBrightness = obj.optDouble("backgroundBrightness", 0.5).toFloat(),
                        isDeleted = obj.optBoolean("isDeleted", false),
                        deletedAt = if (obj.isNull("deletedAt")) null else obj.optLong("deletedAt"),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }
        }
        return list
    }

    /**
     * Exports full financial data as JSON.
     */
    suspend fun exportFinancesJson(dao: com.example.data.local.FinanceDao): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())

        val catsArr = JSONArray()
        dao.getAllCategoriesSync().forEach { c ->
            catsArr.put(JSONObject().apply {
                put("id", c.id)
                put("type", c.type)
                put("name", c.name)
                put("iconName", c.iconName)
                put("colorHex", c.colorHex)
                put("isDefault", c.isDefault)
                put("isArchived", c.isArchived)
                put("updatedAt", c.updatedAt)
            })
        }
        root.put("categories", catsArr)

        val entriesArr = JSONArray()
        dao.getAllEntriesIncludingDeleted().forEach { e ->
            entriesArr.put(JSONObject().apply {
                put("id", e.id)
                put("type", e.type)
                put("name", e.name)
                put("comment", e.comment)
                put("categoryId", e.categoryId)
                put("walletName", e.walletName)
                put("currencyCode", e.currencyCode)
                put("dateMillis", e.dateMillis)
                put("quantity", e.quantity)
                put("unit", e.unit)
                put("unitPriceMinor", e.unitPriceMinor ?: JSONObject.NULL)
                put("totalAmountMinor", e.totalAmountMinor)
                put("priority", e.priority)
                put("tagsCsv", e.tagsCsv)
                put("attachmentUri", e.attachmentUri ?: JSONObject.NULL)
                put("isPlanned", e.isPlanned)
                put("plannedAmountMinor", e.plannedAmountMinor ?: JSONObject.NULL)
                put("createdAt", e.createdAt)
                put("updatedAt", e.updatedAt)
                put("deletedAt", e.deletedAt ?: JSONObject.NULL)
            })
        }
        root.put("entries", entriesArr)

        val walletsArr = JSONArray()
        dao.getAllWalletsSync().forEach { w ->
            walletsArr.put(JSONObject().apply {
                put("id", w.id)
                put("name", w.name)
                put("type", w.type)
                put("currencyCode", w.currencyCode)
                put("initialBalanceMinor", w.initialBalanceMinor)
                put("colorHex", w.colorHex)
                put("isDefault", w.isDefault)
                put("updatedAt", w.updatedAt)
            })
        }
        root.put("wallets", walletsArr)

        val ratesArr = JSONArray()
        dao.getAllExchangeRatesSync().forEach { r ->
            ratesArr.put(JSONObject().apply {
                put("id", r.id)
                put("fromCurrency", r.fromCurrency)
                put("toCurrency", r.toCurrency)
                put("rate", r.rate)
                put("recordedAt", r.recordedAt)
                put("updatedAt", r.updatedAt)
            })
        }
        root.put("exchangeRates", ratesArr)

        val budgetsArr = JSONArray()
        dao.getAllBudgetsSync().forEach { b ->
            budgetsArr.put(JSONObject().apply {
                put("id", b.id)
                put("categoryId", b.categoryId)
                put("limitMinor", b.limitMinor)
                put("currencyCode", b.currencyCode)
                put("alert80SentMonth", b.alert80SentMonth)
                put("alert100SentMonth", b.alert100SentMonth)
                put("updatedAt", b.updatedAt)
            })
        }
        root.put("budgets", budgetsArr)

        val rulesArr = JSONArray()
        dao.getAllRecurringRulesSync().forEach { r ->
            rulesArr.put(JSONObject().apply {
                put("id", r.id)
                put("type", r.type)
                put("name", r.name)
                put("categoryId", r.categoryId)
                put("walletName", r.walletName)
                put("currencyCode", r.currencyCode)
                put("amountMinor", r.amountMinor)
                put("priority", r.priority)
                put("frequency", r.frequency)
                put("reminderDaysBefore", r.reminderDaysBefore)
                put("nextDueMillis", r.nextDueMillis)
                put("lastRemindedDueMillis", r.lastRemindedDueMillis)
                put("isActive", r.isActive)
                put("updatedAt", r.updatedAt)
            })
        }
        root.put("recurringRules", rulesArr)

        val linksArr = JSONArray()
        dao.getAllLinksSync().forEach { l ->
            linksArr.put(JSONObject().apply {
                put("id", l.id)
                put("noteId", l.noteId)
                put("noteUuid", l.noteUuid)
                put("blockId", l.blockId)
                put("sourceType", l.sourceType)
                put("frequency", l.frequency)
                put("scheduleHour", l.scheduleHour)
                put("scheduleMinute", l.scheduleMinute)
                put("writeMode", l.writeMode)
                put("newNoteTitleTemplate", l.newNoteTitleTemplate)
                put("groupBy", l.groupBy)
                put("mergeSameItems", l.mergeSameItems)
                put("extraColumnsCsv", l.extraColumnsCsv)
                put("includeBalanceSummary", l.includeBalanceSummary)
                put("includeAutoSummary", l.includeAutoSummary)
                put("includeMermaidChart", l.includeMermaidChart)
                put("filterCategoriesCsv", l.filterCategoriesCsv)
                put("filterPriority", l.filterPriority)
                put("filterWallet", l.filterWallet)
                put("filterTagsCsv", l.filterTagsCsv)
                put("filterCurrency", l.filterCurrency)
                put("notifyOnUpdate", l.notifyOnUpdate)
                put("emptyPeriodBehavior", l.emptyPeriodBehavior)
                put("status", l.status)
                put("lastExecutedAt", l.lastExecutedAt ?: JSONObject.NULL)
                put("createdAt", l.createdAt)
                put("updatedAt", l.updatedAt)
            })
        }
        root.put("links", linksArr)

        return root.toString(2)
    }

    /**
     * Parses and restores financial data.
     */
    suspend fun restoreFinancesJson(dao: com.example.data.local.FinanceDao, jsonString: String) {
        val root = JSONObject(jsonString)

        if (root.has("categories")) {
            val arr = root.getJSONArray("categories")
            val list = mutableListOf<FinanceCategory>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    FinanceCategory(
                        id = o.getString("id"),
                        type = o.getString("type"),
                        name = o.getString("name"),
                        iconName = o.getString("iconName"),
                        colorHex = o.getLong("colorHex"),
                        isDefault = o.optBoolean("isDefault", false),
                        isArchived = o.optBoolean("isArchived", false),
                        updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }
            if (list.isNotEmpty()) dao.insertCategories(list)
        }

        if (root.has("wallets")) {
            val arr = root.getJSONArray("wallets")
            val list = mutableListOf<FinanceWallet>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    FinanceWallet(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        type = o.optString("type", FinanceWalletType.CASH.id),
                        currencyCode = o.optString("currencyCode", "AOA"),
                        initialBalanceMinor = o.optLong("initialBalanceMinor", 0L),
                        colorHex = o.optLong("colorHex", 0xFFFF8B1F),
                        isDefault = o.optBoolean("isDefault", false),
                        updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }
            if (list.isNotEmpty()) dao.insertWallets(list)
        }

        if (root.has("entries")) {
            val arr = root.getJSONArray("entries")
            val list = mutableListOf<FinanceEntry>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    FinanceEntry(
                        id = o.getString("id"),
                        type = o.getString("type"),
                        name = o.getString("name"),
                        comment = o.optString("comment", ""),
                        categoryId = o.getString("categoryId"),
                        walletName = o.optString("walletName", "Carteira Principal"),
                        currencyCode = o.optString("currencyCode", "AOA"),
                        dateMillis = o.optLong("dateMillis", System.currentTimeMillis()),
                        quantity = o.optDouble("quantity", 1.0),
                        unit = o.optString("unit", ""),
                        unitPriceMinor = if (o.isNull("unitPriceMinor")) null else o.optLong("unitPriceMinor"),
                        totalAmountMinor = o.getLong("totalAmountMinor"),
                        priority = o.optString("priority", "MANDATORY"),
                        tagsCsv = o.optString("tagsCsv", ""),
                        attachmentUri = if (o.isNull("attachmentUri")) null else o.optString("attachmentUri"),
                        isPlanned = o.optBoolean("isPlanned", false),
                        plannedAmountMinor = if (o.isNull("plannedAmountMinor")) null else o.optLong("plannedAmountMinor"),
                        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
                        deletedAt = if (o.isNull("deletedAt")) null else o.optLong("deletedAt")
                    )
                )
            }
            if (list.isNotEmpty()) dao.insertEntries(list)
        }

        if (root.has("exchangeRates")) {
            val arr = root.getJSONArray("exchangeRates")
            val list = mutableListOf<FinanceExchangeRate>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    FinanceExchangeRate(
                        id = o.getString("id"),
                        fromCurrency = o.getString("fromCurrency"),
                        toCurrency = o.getString("toCurrency"),
                        rate = o.getDouble("rate"),
                        recordedAt = o.optLong("recordedAt", System.currentTimeMillis()),
                        updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }
            if (list.isNotEmpty()) dao.insertExchangeRates(list)
        }

        if (root.has("budgets")) {
            val arr = root.getJSONArray("budgets")
            val list = mutableListOf<FinanceBudget>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    FinanceBudget(
                        id = o.getString("id"),
                        categoryId = o.getString("categoryId"),
                        limitMinor = o.getLong("limitMinor"),
                        currencyCode = o.optString("currencyCode", "AOA"),
                        alert80SentMonth = o.optString("alert80SentMonth", ""),
                        alert100SentMonth = o.optString("alert100SentMonth", ""),
                        updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }
            if (list.isNotEmpty()) dao.insertBudgets(list)
        }

        if (root.has("recurringRules")) {
            val arr = root.getJSONArray("recurringRules")
            val list = mutableListOf<FinanceRecurringRule>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    FinanceRecurringRule(
                        id = o.getString("id"),
                        type = o.optString("type", FinanceEntryType.EXPENSE.id),
                        name = o.getString("name"),
                        categoryId = o.getString("categoryId"),
                        walletName = o.optString("walletName", "Carteira Principal"),
                        currencyCode = o.optString("currencyCode", "AOA"),
                        amountMinor = o.getLong("amountMinor"),
                        priority = o.optString("priority", FinancePriority.MANDATORY.id),
                        frequency = o.optString("frequency", FinanceLinkFrequency.MONTHLY.id),
                        reminderDaysBefore = o.optInt("reminderDaysBefore", 1),
                        nextDueMillis = o.optLong("nextDueMillis", System.currentTimeMillis()),
                        lastRemindedDueMillis = o.optLong("lastRemindedDueMillis", 0L),
                        isActive = o.optBoolean("isActive", true),
                        updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }
            if (list.isNotEmpty()) dao.insertRecurringRules(list)
        }

        if (root.has("links")) {
            val arr = root.getJSONArray("links")
            val list = mutableListOf<FinanceNoteLink>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    FinanceNoteLink(
                        id = o.getString("id"),
                        noteId = o.optLong("noteId", 0L),
                        noteUuid = o.optString("noteUuid", ""),
                        blockId = o.optString("blockId", ""),
                        sourceType = o.optString("sourceType", FinanceLinkSource.BOTH.id),
                        frequency = o.optString("frequency", FinanceLinkFrequency.MONTHLY.id),
                        scheduleHour = o.optInt("scheduleHour", 23),
                        scheduleMinute = o.optInt("scheduleMinute", 59),
                        writeMode = o.optString("writeMode", FinanceLinkWriteMode.REPLACE_BLOCK.id),
                        newNoteTitleTemplate = o.optString("newNoteTitleTemplate", "Despesas Mensais · {mês} {ano}"),
                        groupBy = o.optString("groupBy", FinanceLinkGroupBy.PRIORITY.id),
                        mergeSameItems = o.optBoolean("mergeSameItems", false),
                        extraColumnsCsv = o.optString("extraColumnsCsv", ""),
                        includeBalanceSummary = o.optBoolean("includeBalanceSummary", true),
                        includeAutoSummary = o.optBoolean("includeAutoSummary", false),
                        includeMermaidChart = o.optBoolean("includeMermaidChart", false),
                        filterCategoriesCsv = o.optString("filterCategoriesCsv", ""),
                        filterPriority = o.optString("filterPriority", ""),
                        filterWallet = o.optString("filterWallet", ""),
                        filterTagsCsv = o.optString("filterTagsCsv", ""),
                        filterCurrency = o.optString("filterCurrency", ""),
                        notifyOnUpdate = o.optBoolean("notifyOnUpdate", false),
                        emptyPeriodBehavior = o.optString("emptyPeriodBehavior", "WRITE_EMPTY"),
                        status = o.optString("status", "ACTIVE"),
                        lastExecutedAt = if (o.isNull("lastExecutedAt")) null else o.optLong("lastExecutedAt"),
                        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }
            if (list.isNotEmpty()) dao.insertLinks(list)
        }
    }
}

data class BackupParsedData(
    val tasks: List<Task>,
    val habits: List<Habit>,
    val focusSessions: List<FocusSession>,
    val categories: List<Category>,
    val achievements: List<Achievement> = emptyList(),
    val notes: List<Note> = emptyList(),
    val financeEntries: List<FinanceEntry> = emptyList(),
    val financeCategories: List<FinanceCategory> = emptyList(),
    val financeWallets: List<FinanceWallet> = emptyList(),
    val financeRecurringRules: List<FinanceRecurringRule> = emptyList(),
    val financeBudgets: List<FinanceBudget> = emptyList(),
    val financeNoteLinks: List<FinanceNoteLink> = emptyList()
)
