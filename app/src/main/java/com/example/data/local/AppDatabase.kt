package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.*

@Database(
    entities = [
        Task::class,
        Habit::class,
        Note::class,
        FocusSession::class,
        Category::class,
        Achievement::class,
        NoteVersion::class,
        FinanceCategory::class,
        FinanceEntry::class,
        FinanceNoteLink::class,
        FinanceLinkExecution::class,
        FinanceWallet::class,
        FinanceExchangeRate::class,
        FinanceBudget::class,
        FinanceRecurringRule::class
    ],
    version = 14,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun habitDao(): HabitDao
    abstract fun noteDao(): NoteDao
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun achievementDao(): AchievementDao
    abstract fun noteVersionDao(): NoteVersionDao
    abstract fun financeDao(): FinanceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS finance_categories (
                        id TEXT NOT NULL PRIMARY KEY,
                        type TEXT NOT NULL,
                        name TEXT NOT NULL,
                        iconName TEXT NOT NULL,
                        colorHex INTEGER NOT NULL,
                        isDefault INTEGER NOT NULL DEFAULT 0,
                        isArchived INTEGER NOT NULL DEFAULT 0,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS finance_entries (
                        id TEXT NOT NULL PRIMARY KEY,
                        type TEXT NOT NULL,
                        name TEXT NOT NULL,
                        comment TEXT NOT NULL DEFAULT '',
                        categoryId TEXT NOT NULL,
                        walletName TEXT NOT NULL DEFAULT 'Carteira Principal',
                        currencyCode TEXT NOT NULL DEFAULT 'AOA',
                        dateMillis INTEGER NOT NULL,
                        quantity REAL NOT NULL DEFAULT 1.0,
                        unit TEXT NOT NULL DEFAULT '',
                        unitPriceMinor INTEGER,
                        totalAmountMinor INTEGER NOT NULL,
                        priority TEXT NOT NULL DEFAULT 'MANDATORY',
                        tagsCsv TEXT NOT NULL DEFAULT '',
                        attachmentUri TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        deletedAt INTEGER
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS finance_note_links (
                        id TEXT NOT NULL PRIMARY KEY,
                        noteId INTEGER NOT NULL,
                        noteUuid TEXT NOT NULL,
                        blockId TEXT NOT NULL,
                        sourceType TEXT NOT NULL DEFAULT 'BOTH',
                        frequency TEXT NOT NULL DEFAULT 'MONTHLY',
                        scheduleHour INTEGER NOT NULL DEFAULT 23,
                        scheduleMinute INTEGER NOT NULL DEFAULT 59,
                        writeMode TEXT NOT NULL DEFAULT 'REPLACE_BLOCK',
                        newNoteTitleTemplate TEXT NOT NULL DEFAULT 'Despesas Mensais · {mês} {ano}',
                        groupBy TEXT NOT NULL DEFAULT 'PRIORITY',
                        mergeSameItems INTEGER NOT NULL DEFAULT 0,
                        extraColumnsCsv TEXT NOT NULL DEFAULT '',
                        includeBalanceSummary INTEGER NOT NULL DEFAULT 1,
                        includeAutoSummary INTEGER NOT NULL DEFAULT 0,
                        includeMermaidChart INTEGER NOT NULL DEFAULT 0,
                        filterCategoriesCsv TEXT NOT NULL DEFAULT '',
                        filterPriority TEXT NOT NULL DEFAULT '',
                        filterWallet TEXT NOT NULL DEFAULT '',
                        filterTagsCsv TEXT NOT NULL DEFAULT '',
                        filterCurrency TEXT NOT NULL DEFAULT '',
                        notifyOnUpdate INTEGER NOT NULL DEFAULT 0,
                        emptyPeriodBehavior TEXT NOT NULL DEFAULT 'WRITE_EMPTY',
                        status TEXT NOT NULL DEFAULT 'ACTIVE',
                        lastExecutedAt INTEGER,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS finance_link_executions (
                        id TEXT NOT NULL PRIMARY KEY,
                        linkId TEXT NOT NULL,
                        executedAt INTEGER NOT NULL,
                        entriesCount INTEGER NOT NULL DEFAULT 0,
                        resultStatus TEXT NOT NULL DEFAULT 'SUCCESS',
                        previousNoteContent TEXT
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE finance_entries ADD COLUMN isPlanned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE finance_entries ADD COLUMN plannedAmountMinor INTEGER")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS finance_wallets (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        type TEXT NOT NULL DEFAULT 'CASH',
                        currencyCode TEXT NOT NULL DEFAULT 'AOA',
                        initialBalanceMinor INTEGER NOT NULL DEFAULT 0,
                        colorHex INTEGER NOT NULL,
                        isDefault INTEGER NOT NULL DEFAULT 0,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS finance_exchange_rates (
                        id TEXT NOT NULL PRIMARY KEY,
                        fromCurrency TEXT NOT NULL,
                        toCurrency TEXT NOT NULL,
                        rate REAL NOT NULL,
                        recordedAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS finance_budgets (
                        id TEXT NOT NULL PRIMARY KEY,
                        categoryId TEXT NOT NULL,
                        limitMinor INTEGER NOT NULL,
                        currencyCode TEXT NOT NULL DEFAULT 'AOA',
                        alert80SentMonth TEXT NOT NULL DEFAULT '',
                        alert100SentMonth TEXT NOT NULL DEFAULT '',
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS finance_recurring_rules (
                        id TEXT NOT NULL PRIMARY KEY,
                        type TEXT NOT NULL DEFAULT 'EXPENSE',
                        name TEXT NOT NULL,
                        categoryId TEXT NOT NULL,
                        walletName TEXT NOT NULL DEFAULT 'Carteira Principal',
                        currencyCode TEXT NOT NULL DEFAULT 'AOA',
                        amountMinor INTEGER NOT NULL,
                        priority TEXT NOT NULL DEFAULT 'MANDATORY',
                        frequency TEXT NOT NULL DEFAULT 'MONTHLY',
                        reminderDaysBefore INTEGER NOT NULL DEFAULT 1,
                        nextDueMillis INTEGER NOT NULL,
                        lastRemindedDueMillis INTEGER NOT NULL DEFAULT 0,
                        isActive INTEGER NOT NULL DEFAULT 1,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notes ADD COLUMN noteColor TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN isDisruptive INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN isWontDo INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "citra.db"
                )
                .addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
