package com.example.data.backup

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesManager
import com.example.data.model.FinanceBudget
import com.example.data.model.FinanceCategory
import com.example.data.model.FinanceEntry
import com.example.data.model.FinanceExchangeRate
import com.example.data.model.FinanceLinkExecution
import com.example.data.model.FinanceNoteLink
import com.example.data.model.FinanceRecurringRule
import com.example.data.model.FinanceWallet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object FinanceCloudSyncManager {

    suspend fun exportFinancesToJson(
        database: AppDatabase,
        prefs: PreferencesManager
    ): String = withContext(Dispatchers.IO) {
        val dao = database.financeDao()
        val entries = dao.getAllEntriesIncludingDeleted()
        val categories = dao.getAllCategoriesSync()
        val links = dao.getAllLinksSync()
        val executions = dao.getAllExecutionsSync()

        val root = JSONObject()
        root.put("schemaVersion", 1)
        root.put("exportedAt", System.currentTimeMillis())

        val settingsObj = JSONObject().apply {
            put("financesEnabled", prefs.financesEnabled.value)
            put("financesSetupCompleted", prefs.financesSetupCompleted.value)
            put("financeDefaultCurrency", prefs.financeDefaultCurrency.value)
            put("financeBaseBalanceMinor", prefs.financeBaseBalanceMinor.value)
            put("financeHideValues", prefs.financeHideValues.value)
            put("financePinCode", prefs.financePinCode.value ?: JSONObject.NULL)
            put("financeBiometricEnabled", prefs.financeBiometricEnabled.value)
        }
        root.put("settings", settingsObj)

        val catArr = JSONArray()
        categories.forEach { c ->
            catArr.put(JSONObject().apply {
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
        root.put("categories", catArr)

        val entriesArr = JSONArray()
        entries.forEach { e ->
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

        val linksArr = JSONArray()
        links.forEach { l ->
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

        val execArr = JSONArray()
        executions.forEach { ex ->
            execArr.put(JSONObject().apply {
                put("id", ex.id)
                put("linkId", ex.linkId)
                put("executedAt", ex.executedAt)
                put("entriesCount", ex.entriesCount)
                put("resultStatus", ex.resultStatus)
                put("previousNoteContent", ex.previousNoteContent ?: JSONObject.NULL)
            })
        }
        root.put("executions", execArr)

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

        val recArr = JSONArray()
        dao.getAllRecurringRulesSync().forEach { rr ->
            recArr.put(JSONObject().apply {
                put("id", rr.id)
                put("type", rr.type)
                put("name", rr.name)
                put("categoryId", rr.categoryId)
                put("walletName", rr.walletName)
                put("currencyCode", rr.currencyCode)
                put("amountMinor", rr.amountMinor)
                put("priority", rr.priority)
                put("frequency", rr.frequency)
                put("reminderDaysBefore", rr.reminderDaysBefore)
                put("nextDueMillis", rr.nextDueMillis)
                put("lastRemindedDueMillis", rr.lastRemindedDueMillis)
                put("isActive", rr.isActive)
                put("updatedAt", rr.updatedAt)
            })
        }
        root.put("recurringRules", recArr)

        root.toString()
    }

    /**
     * Merges cloud finance data into local Room database using UUID + updatedAt (newest wins).
     * When [restoreSettings] is true, restores finance preferences including PIN lock.
     */
    suspend fun mergeAndRestoreFromCloudJson(
        jsonStr: String,
        database: AppDatabase,
        prefs: PreferencesManager,
        restoreSettings: Boolean = true
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (jsonStr.isBlank()) return@withContext false
            val root = JSONObject(jsonStr)
            val dao = database.financeDao()

            // 1. Merge Categories (newest updatedAt wins)
            if (root.has("categories")) {
                val localCats = dao.getAllCategoriesSync().associateBy { it.id }
                val catArr = root.getJSONArray("categories")
                val toUpsert = mutableListOf<FinanceCategory>()
                for (i in 0 until catArr.length()) {
                    val o = catArr.getJSONObject(i)
                    val id = o.getString("id")
                    val updatedAt = o.optLong("updatedAt", 0L)
                    val existing = localCats[id]
                    if (existing == null || updatedAt > existing.updatedAt) {
                        toUpsert.add(
                            FinanceCategory(
                                id = id,
                                type = o.getString("type"),
                                name = o.getString("name"),
                                iconName = o.optString("iconName", "shopping_cart"),
                                colorHex = o.optLong("colorHex", 0xFFFF8B1F),
                                isDefault = o.optBoolean("isDefault", false),
                                isArchived = o.optBoolean("isArchived", false),
                                updatedAt = updatedAt
                            )
                        )
                    }
                }
                if (toUpsert.isNotEmpty()) dao.insertCategories(toUpsert)
            }

            // 2. Merge Entries (UUID + updatedAt newest wins, preserving deletedAt tombstones)
            if (root.has("entries")) {
                val localEntries = dao.getAllEntriesIncludingDeleted().associateBy { it.id }
                val entriesArr = root.getJSONArray("entries")
                val toUpsert = mutableListOf<FinanceEntry>()
                for (i in 0 until entriesArr.length()) {
                    val o = entriesArr.getJSONObject(i)
                    val id = o.getString("id")
                    val updatedAt = o.optLong("updatedAt", 0L)
                    val existing = localEntries[id]
                    if (existing == null || updatedAt > existing.updatedAt) {
                        toUpsert.add(
                            FinanceEntry(
                                id = id,
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
                                totalAmountMinor = o.optLong("totalAmountMinor", 0L),
                                priority = o.optString("priority", "MANDATORY"),
                                tagsCsv = o.optString("tagsCsv", ""),
                                attachmentUri = if (o.isNull("attachmentUri")) null else o.optString("attachmentUri"),
                                isPlanned = o.optBoolean("isPlanned", false),
                                plannedAmountMinor = if (o.isNull("plannedAmountMinor")) null else o.optLong("plannedAmountMinor"),
                                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                                updatedAt = updatedAt,
                                deletedAt = if (o.isNull("deletedAt")) null else o.optLong("deletedAt")
                            )
                        )
                    }
                }
                if (toUpsert.isNotEmpty()) dao.insertEntries(toUpsert)
            }

            // 3. Merge Note Links (UUID + updatedAt newest wins)
            if (root.has("links")) {
                val localLinks = dao.getAllLinksSync().associateBy { it.id }
                val linksArr = root.getJSONArray("links")
                val toUpsert = mutableListOf<FinanceNoteLink>()
                for (i in 0 until linksArr.length()) {
                    val o = linksArr.getJSONObject(i)
                    val id = o.getString("id")
                    val updatedAt = o.optLong("updatedAt", 0L)
                    val existing = localLinks[id]
                    if (existing == null || updatedAt > existing.updatedAt) {
                        toUpsert.add(
                            FinanceNoteLink(
                                id = id,
                                noteId = o.optLong("noteId", 0L),
                                noteUuid = o.optString("noteUuid", ""),
                                blockId = o.optString("blockId", ""),
                                sourceType = o.optString("sourceType", "BOTH"),
                                frequency = o.optString("frequency", "MONTHLY"),
                                scheduleHour = o.optInt("scheduleHour", 23),
                                scheduleMinute = o.optInt("scheduleMinute", 59),
                                writeMode = o.optString("writeMode", "REPLACE_BLOCK"),
                                newNoteTitleTemplate = o.optString("newNoteTitleTemplate", "Despesas Mensais · {mês} {ano}"),
                                groupBy = o.optString("groupBy", "PRIORITY"),
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
                                updatedAt = updatedAt
                            )
                        )
                    }
                }
                if (toUpsert.isNotEmpty()) dao.insertLinks(toUpsert)
            }

            // 4. Merge Link Executions
            if (root.has("executions")) {
                val execArr = root.getJSONArray("executions")
                val toUpsert = mutableListOf<FinanceLinkExecution>()
                for (i in 0 until execArr.length()) {
                    val o = execArr.getJSONObject(i)
                    toUpsert.add(
                        FinanceLinkExecution(
                            id = o.getString("id"),
                            linkId = o.getString("linkId"),
                            executedAt = o.optLong("executedAt", System.currentTimeMillis()),
                            entriesCount = o.optInt("entriesCount", 0),
                            resultStatus = o.optString("resultStatus", "SUCCESS"),
                            previousNoteContent = if (o.isNull("previousNoteContent")) null else o.optString("previousNoteContent")
                        )
                    )
                }
                if (toUpsert.isNotEmpty()) dao.insertExecutions(toUpsert)
            }

            // 5. Restore Settings (including PIN lock if configured)
            if (restoreSettings && root.has("settings")) {
                val s = root.getJSONObject("settings")
                prefs.setFinancesEnabled(s.optBoolean("financesEnabled", true))
                prefs.setFinancesSetupCompleted(s.optBoolean("financesSetupCompleted", true))
                prefs.setFinanceDefaultCurrency(s.optString("financeDefaultCurrency", "AOA"))
                prefs.setFinanceBaseBalanceMinor(s.optLong("financeBaseBalanceMinor", 0L))
                prefs.setFinanceHideValues(s.optBoolean("financeHideValues", false))
                val pin = if (s.isNull("financePinCode")) null else s.optString("financePinCode")
                prefs.setFinancePinCode(pin)
                prefs.setFinanceBiometricEnabled(s.optBoolean("financeBiometricEnabled", false))
            }

            // 6. Merge Wallets
            if (root.has("wallets")) {
                val localWallets = dao.getAllWalletsSync().associateBy { it.id }
                val arr = root.getJSONArray("wallets")
                val toUpsert = mutableListOf<FinanceWallet>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val id = o.getString("id")
                    val updatedAt = o.optLong("updatedAt", 0L)
                    val existing = localWallets[id]
                    if (existing == null || updatedAt > existing.updatedAt) {
                        toUpsert.add(
                            FinanceWallet(
                                id = id,
                                name = o.getString("name"),
                                type = o.optString("type", "CASH"),
                                currencyCode = o.optString("currencyCode", "AOA"),
                                initialBalanceMinor = o.optLong("initialBalanceMinor", 0L),
                                colorHex = o.optLong("colorHex", 0xFFFF8B1F),
                                isDefault = o.optBoolean("isDefault", false),
                                updatedAt = updatedAt
                            )
                        )
                    }
                }
                if (toUpsert.isNotEmpty()) dao.insertWallets(toUpsert)
            }

            // 7. Merge Exchange Rates
            if (root.has("exchangeRates")) {
                val localRates = dao.getAllExchangeRatesSync().associateBy { it.id }
                val arr = root.getJSONArray("exchangeRates")
                val toUpsert = mutableListOf<FinanceExchangeRate>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val id = o.getString("id")
                    val updatedAt = o.optLong("updatedAt", 0L)
                    val existing = localRates[id]
                    if (existing == null || updatedAt > existing.updatedAt) {
                        toUpsert.add(
                            FinanceExchangeRate(
                                id = id,
                                fromCurrency = o.getString("fromCurrency"),
                                toCurrency = o.getString("toCurrency"),
                                rate = o.optDouble("rate", 1.0),
                                recordedAt = o.optLong("recordedAt", System.currentTimeMillis()),
                                updatedAt = updatedAt
                            )
                        )
                    }
                }
                if (toUpsert.isNotEmpty()) dao.insertExchangeRates(toUpsert)
            }

            // 8. Merge Budgets
            if (root.has("budgets")) {
                val localBudgets = dao.getAllBudgetsSync().associateBy { it.id }
                val arr = root.getJSONArray("budgets")
                val toUpsert = mutableListOf<FinanceBudget>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val id = o.getString("id")
                    val updatedAt = o.optLong("updatedAt", 0L)
                    val existing = localBudgets[id]
                    if (existing == null || updatedAt > existing.updatedAt) {
                        toUpsert.add(
                            FinanceBudget(
                                id = id,
                                categoryId = o.getString("categoryId"),
                                limitMinor = o.optLong("limitMinor", 0L),
                                currencyCode = o.optString("currencyCode", "AOA"),
                                alert80SentMonth = o.optString("alert80SentMonth", ""),
                                alert100SentMonth = o.optString("alert100SentMonth", ""),
                                updatedAt = updatedAt
                            )
                        )
                    }
                }
                if (toUpsert.isNotEmpty()) dao.insertBudgets(toUpsert)
            }

            // 9. Merge Recurring Rules
            if (root.has("recurringRules")) {
                val localRules = dao.getAllRecurringRulesSync().associateBy { it.id }
                val arr = root.getJSONArray("recurringRules")
                val toUpsert = mutableListOf<FinanceRecurringRule>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val id = o.getString("id")
                    val updatedAt = o.optLong("updatedAt", 0L)
                    val existing = localRules[id]
                    if (existing == null || updatedAt > existing.updatedAt) {
                        toUpsert.add(
                            FinanceRecurringRule(
                                id = id,
                                type = o.optString("type", "EXPENSE"),
                                name = o.getString("name"),
                                categoryId = o.getString("categoryId"),
                                walletName = o.optString("walletName", "Carteira Principal"),
                                currencyCode = o.optString("currencyCode", "AOA"),
                                amountMinor = o.optLong("amountMinor", 0L),
                                priority = o.optString("priority", "MANDATORY"),
                                frequency = o.optString("frequency", "MONTHLY"),
                                reminderDaysBefore = o.optInt("reminderDaysBefore", 1),
                                nextDueMillis = o.optLong("nextDueMillis", System.currentTimeMillis()),
                                lastRemindedDueMillis = o.optLong("lastRemindedDueMillis", 0L),
                                isActive = o.optBoolean("isActive", true),
                                updatedAt = updatedAt
                            )
                        )
                    }
                }
                if (toUpsert.isNotEmpty()) dao.insertRecurringRules(toUpsert)
            }

            true
        } catch (_: Exception) {
            false
        }
    }
}
