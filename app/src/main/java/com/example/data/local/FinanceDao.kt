package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.FinanceBudget
import com.example.data.model.FinanceCategory
import com.example.data.model.FinanceEntry
import com.example.data.model.FinanceExchangeRate
import com.example.data.model.FinanceLinkExecution
import com.example.data.model.FinanceNoteLink
import com.example.data.model.FinanceRecurringRule
import com.example.data.model.FinanceWallet
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanceDao {
    @Query("SELECT * FROM finance_categories WHERE isArchived = 0 ORDER BY isDefault DESC, name ASC")
    fun getActiveCategories(): Flow<List<FinanceCategory>>

    @Query("SELECT * FROM finance_categories ORDER BY isDefault DESC, name ASC")
    fun getAllCategories(): Flow<List<FinanceCategory>>

    @Query("SELECT * FROM finance_categories ORDER BY isDefault DESC, name ASC")
    suspend fun getAllCategoriesSync(): List<FinanceCategory>

    @Query("SELECT COUNT(*) FROM finance_categories")
    suspend fun getCategoryCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: FinanceCategory)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<FinanceCategory>)

    @Update
    suspend fun updateCategory(category: FinanceCategory)

    @Query("SELECT * FROM finance_entries WHERE deletedAt IS NULL ORDER BY dateMillis DESC, createdAt DESC")
    fun getActiveEntries(): Flow<List<FinanceEntry>>

    @Query("SELECT * FROM finance_entries")
    suspend fun getAllEntriesIncludingDeleted(): List<FinanceEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: FinanceEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<FinanceEntry>)

    @Update
    suspend fun updateEntry(entry: FinanceEntry)

    @Query("UPDATE finance_entries SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun softDeleteEntry(id: String, deletedAt: Long)

    @Query("DELETE FROM finance_entries")
    suspend fun deleteAllEntries()

    @Query("DELETE FROM finance_categories")
    suspend fun deleteAllCategories()

    // Finance Note Links
    @Query("SELECT * FROM finance_note_links WHERE status = 'ACTIVE' ORDER BY createdAt DESC")
    fun getActiveLinks(): Flow<List<FinanceNoteLink>>

    @Query("SELECT * FROM finance_note_links ORDER BY createdAt DESC")
    fun getAllLinks(): Flow<List<FinanceNoteLink>>

    @Query("SELECT * FROM finance_note_links ORDER BY createdAt DESC")
    suspend fun getAllLinksSync(): List<FinanceNoteLink>

    @Query("SELECT * FROM finance_note_links WHERE id = :id LIMIT 1")
    suspend fun getLinkById(id: String): FinanceNoteLink?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLink(link: FinanceNoteLink)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLinks(links: List<FinanceNoteLink>)

    @Update
    suspend fun updateLink(link: FinanceNoteLink)

    @Query("DELETE FROM finance_note_links WHERE id = :id")
    suspend fun deleteLinkById(id: String)

    @Query("DELETE FROM finance_note_links")
    suspend fun deleteAllLinks()

    // Finance Link Executions
    @Query("SELECT * FROM finance_link_executions WHERE linkId = :linkId ORDER BY executedAt DESC")
    fun getExecutionsForLink(linkId: String): Flow<List<FinanceLinkExecution>>

    @Query("SELECT * FROM finance_link_executions ORDER BY executedAt DESC")
    suspend fun getAllExecutionsSync(): List<FinanceLinkExecution>

    @Query("SELECT * FROM finance_link_executions WHERE linkId = :linkId ORDER BY executedAt DESC LIMIT 1")
    suspend fun getLatestExecutionForLink(linkId: String): FinanceLinkExecution?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExecution(execution: FinanceLinkExecution)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExecutions(executions: List<FinanceLinkExecution>)

    @Query("DELETE FROM finance_link_executions")
    suspend fun deleteAllExecutions()

    // Wallets
    @Query("SELECT * FROM finance_wallets ORDER BY isDefault DESC, name ASC")
    fun getAllWallets(): Flow<List<FinanceWallet>>

    @Query("SELECT * FROM finance_wallets ORDER BY isDefault DESC, name ASC")
    suspend fun getAllWalletsSync(): List<FinanceWallet>

    @Query("SELECT COUNT(*) FROM finance_wallets")
    suspend fun getWalletCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWallet(wallet: FinanceWallet)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWallets(wallets: List<FinanceWallet>)

    @Query("DELETE FROM finance_wallets WHERE id = :id")
    suspend fun deleteWalletById(id: String)

    @Query("DELETE FROM finance_wallets")
    suspend fun deleteAllWallets()

    // Exchange Rates
    @Query("SELECT * FROM finance_exchange_rates ORDER BY recordedAt DESC")
    fun getAllExchangeRates(): Flow<List<FinanceExchangeRate>>

    @Query("SELECT * FROM finance_exchange_rates ORDER BY recordedAt DESC")
    suspend fun getAllExchangeRatesSync(): List<FinanceExchangeRate>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExchangeRate(rate: FinanceExchangeRate)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExchangeRates(rates: List<FinanceExchangeRate>)

    @Query("DELETE FROM finance_exchange_rates WHERE id = :id")
    suspend fun deleteExchangeRateById(id: String)

    @Query("DELETE FROM finance_exchange_rates")
    suspend fun deleteAllExchangeRates()

    // Budgets
    @Query("SELECT * FROM finance_budgets ORDER BY updatedAt DESC")
    fun getAllBudgets(): Flow<List<FinanceBudget>>

    @Query("SELECT * FROM finance_budgets ORDER BY updatedAt DESC")
    suspend fun getAllBudgetsSync(): List<FinanceBudget>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(budget: FinanceBudget)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudgets(budgets: List<FinanceBudget>)

    @Query("DELETE FROM finance_budgets WHERE id = :id")
    suspend fun deleteBudgetById(id: String)

    @Query("DELETE FROM finance_budgets")
    suspend fun deleteAllBudgets()

    // Recurring Rules
    @Query("SELECT * FROM finance_recurring_rules ORDER BY nextDueMillis ASC")
    fun getAllRecurringRules(): Flow<List<FinanceRecurringRule>>

    @Query("SELECT * FROM finance_recurring_rules ORDER BY nextDueMillis ASC")
    suspend fun getAllRecurringRulesSync(): List<FinanceRecurringRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecurringRule(rule: FinanceRecurringRule)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecurringRules(rules: List<FinanceRecurringRule>)

    @Query("DELETE FROM finance_recurring_rules WHERE id = :id")
    suspend fun deleteRecurringRuleById(id: String)

    @Query("DELETE FROM finance_recurring_rules")
    suspend fun deleteAllRecurringRules()
}
