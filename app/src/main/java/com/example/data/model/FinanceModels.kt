package com.example.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

enum class FinanceEntryType(val id: String, val label: String) {
    EXPENSE("EXPENSE", "Despesa"),
    INCOME("INCOME", "Renda");

    companion object {
        fun fromId(id: String): FinanceEntryType =
            entries.find { it.id == id } ?: EXPENSE
    }
}

enum class FinancePriority(val id: String, val label: String) {
    MANDATORY("MANDATORY", "Obrigatória"),
    OPTIONAL("OPTIONAL", "Opcional");

    companion object {
        fun fromId(id: String): FinancePriority =
            entries.find { it.id == id } ?: MANDATORY
    }
}

enum class FinanceCurrency(val code: String, val symbol: String, val displayName: String) {
    AOA("AOA", "Kz", "Kwanza (Kz)"),
    USD("USD", "$", "Dólar ($)"),
    EUR("EUR", "€", "Euro (€)"),
    BRL("BRL", "R$", "Real (R$)"),
    GBP("GBP", "£", "Libra (£)");

    companion object {
        fun fromCode(code: String): FinanceCurrency =
            entries.find { it.code == code } ?: AOA
    }
}

@Entity(tableName = "finance_categories")
data class FinanceCategory(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val type: String, // "EXPENSE" or "INCOME"
    val name: String,
    val iconName: String,
    val colorHex: Long,
    val isDefault: Boolean = false,
    val isArchived: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "finance_entries")
data class FinanceEntry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val type: String, // "EXPENSE" or "INCOME"
    val name: String,
    val comment: String = "",
    val categoryId: String,
    val walletName: String = "Carteira Principal",
    val currencyCode: String = "AOA",
    val dateMillis: Long = System.currentTimeMillis(),
    val quantity: Double = 1.0,
    val unit: String = "",
    val unitPriceMinor: Long? = null,
    val totalAmountMinor: Long,
    val priority: String = "MANDATORY", // "MANDATORY" or "OPTIONAL"
    val tagsCsv: String = "",
    val attachmentUri: String? = null,
    val isPlanned: Boolean = false,
    val plannedAmountMinor: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null
)

enum class FinanceWalletType(val id: String, val label: String) {
    CASH("CASH", "Dinheiro vivo"),
    BANK("BANK", "Conta bancária"),
    MOBILE("MOBILE", "Carteira móvel");

    companion object {
        fun fromId(id: String): FinanceWalletType = entries.find { it.id == id } ?: CASH
    }
}

@Entity(tableName = "finance_wallets")
data class FinanceWallet(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: String = FinanceWalletType.CASH.id,
    val currencyCode: String = "AOA",
    val initialBalanceMinor: Long = 0L,
    val colorHex: Long = 0xFFFF8B1F,
    val isDefault: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "finance_exchange_rates")
data class FinanceExchangeRate(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val fromCurrency: String,
    val toCurrency: String,
    val rate: Double,
    val recordedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "finance_budgets")
data class FinanceBudget(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val categoryId: String,
    val limitMinor: Long,
    val currencyCode: String = "AOA",
    val alert80SentMonth: String = "",
    val alert100SentMonth: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "finance_recurring_rules")
data class FinanceRecurringRule(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val type: String = FinanceEntryType.EXPENSE.id,
    val name: String,
    val categoryId: String,
    val walletName: String = "Carteira Principal",
    val currencyCode: String = "AOA",
    val amountMinor: Long,
    val priority: String = FinancePriority.MANDATORY.id,
    val frequency: String = FinanceLinkFrequency.MONTHLY.id,
    val reminderDaysBefore: Int = 1,
    val nextDueMillis: Long = System.currentTimeMillis(),
    val lastRemindedDueMillis: Long = 0L,
    val isActive: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)

fun convertFinanceMinorUnits(
    amountMinor: Long,
    fromCurrency: String,
    toCurrency: String,
    rates: List<FinanceExchangeRate>
): Long {
    if (fromCurrency.equals(toCurrency, ignoreCase = true) || amountMinor == 0L) return amountMinor
    val direct = rates
        .filter { it.fromCurrency.equals(fromCurrency, ignoreCase = true) && it.toCurrency.equals(toCurrency, ignoreCase = true) }
        .maxByOrNull { it.recordedAt }
    if (direct != null && direct.rate > 0.0) {
        return kotlin.math.round(amountMinor * direct.rate).toLong()
    }
    val inverse = rates
        .filter { it.fromCurrency.equals(toCurrency, ignoreCase = true) && it.toCurrency.equals(fromCurrency, ignoreCase = true) }
        .maxByOrNull { it.recordedAt }
    if (inverse != null && inverse.rate > 0.0) {
        return kotlin.math.round(amountMinor / inverse.rate).toLong()
    }
    return amountMinor
}

enum class FinanceLinkSource(val id: String, val label: String) {
    EXPENSE("EXPENSE", "Despesas"),
    INCOME("INCOME", "Rendas"),
    BOTH("BOTH", "Ambas");

    companion object {
        fun fromId(id: String): FinanceLinkSource = entries.find { it.id == id } ?: BOTH
    }
}

enum class FinanceLinkFrequency(val id: String, val label: String) {
    DAILY("DAILY", "Diária"),
    WEEKLY("WEEKLY", "Semanal"),
    MONTHLY("MONTHLY", "Mensal"),
    ANNUAL("ANNUAL", "Anual"),
    MANUAL("MANUAL", "Manual");

    companion object {
        fun fromId(id: String): FinanceLinkFrequency = entries.find { it.id == id } ?: MONTHLY
    }
}

enum class FinanceLinkWriteMode(val id: String, val label: String) {
    REPLACE_BLOCK("REPLACE_BLOCK", "Substituir o bloco"),
    APPEND_SECTION("APPEND_SECTION", "Acrescentar nova secção por período"),
    NEW_NOTE_PER_PERIOD("NEW_NOTE_PER_PERIOD", "Criar nota nova por período");

    companion object {
        fun fromId(id: String): FinanceLinkWriteMode = entries.find { it.id == id } ?: REPLACE_BLOCK
    }
}

enum class FinanceLinkGroupBy(val id: String, val label: String) {
    PRIORITY("PRIORITY", "Prioridade"),
    CATEGORY("CATEGORY", "Categoria"),
    NONE("NONE", "Sem grupos");

    companion object {
        fun fromId(id: String): FinanceLinkGroupBy = entries.find { it.id == id } ?: PRIORITY
    }
}

enum class FinanceNoteTemplate(
    val id: String,
    val title: String,
    val description: String,
    val defaultSource: FinanceLinkSource,
    val defaultFrequency: FinanceLinkFrequency,
    val defaultGroupBy: FinanceLinkGroupBy,
    val defaultAutoSummary: Boolean
) {
    MONTHLY_EXPENSES(
        id = "MONTHLY_EXPENSES",
        title = "Despesas Mensais",
        description = "Obrigatórias, opcionais, saldo final e simulação",
        defaultSource = FinanceLinkSource.EXPENSE,
        defaultFrequency = FinanceLinkFrequency.MONTHLY,
        defaultGroupBy = FinanceLinkGroupBy.PRIORITY,
        defaultAutoSummary = false
    ),
    MONTHLY_INCOME(
        id = "MONTHLY_INCOME",
        title = "Rendas do Mês",
        description = "Tabela de rendas por categoria e total",
        defaultSource = FinanceLinkSource.INCOME,
        defaultFrequency = FinanceLinkFrequency.MONTHLY,
        defaultGroupBy = FinanceLinkGroupBy.CATEGORY,
        defaultAutoSummary = false
    ),
    FULL_BALANCE(
        id = "FULL_BALANCE",
        title = "Balanço Completo",
        description = "Rendas, despesas, saldo e resumo automático",
        defaultSource = FinanceLinkSource.BOTH,
        defaultFrequency = FinanceLinkFrequency.MONTHLY,
        defaultGroupBy = FinanceLinkGroupBy.PRIORITY,
        defaultAutoSummary = true
    ),
    SHOPPING_LIST(
        id = "SHOPPING_LIST",
        title = "Lista de Compras",
        description = "Itens com preço previsto, ligados ao planeado vs. real",
        defaultSource = FinanceLinkSource.EXPENSE,
        defaultFrequency = FinanceLinkFrequency.WEEKLY,
        defaultGroupBy = FinanceLinkGroupBy.NONE,
        defaultAutoSummary = false
    ),
    WEEKLY_REPORT(
        id = "WEEKLY_REPORT",
        title = "Relatório Semanal",
        description = "Resumo dos últimos 7 dias",
        defaultSource = FinanceLinkSource.BOTH,
        defaultFrequency = FinanceLinkFrequency.WEEKLY,
        defaultGroupBy = FinanceLinkGroupBy.CATEGORY,
        defaultAutoSummary = true
    ),
    ANNUAL_REPORT(
        id = "ANNUAL_REPORT",
        title = "Relatório Anual",
        description = "Soma consolidada do ano",
        defaultSource = FinanceLinkSource.BOTH,
        defaultFrequency = FinanceLinkFrequency.ANNUAL,
        defaultGroupBy = FinanceLinkGroupBy.CATEGORY,
        defaultAutoSummary = true
    )
}

@Entity(tableName = "finance_note_links")
data class FinanceNoteLink(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val noteId: Long,
    val noteUuid: String,
    val blockId: String,
    val sourceType: String = FinanceLinkSource.BOTH.id,
    val frequency: String = FinanceLinkFrequency.MONTHLY.id,
    val scheduleHour: Int = 23,
    val scheduleMinute: Int = 59,
    val writeMode: String = FinanceLinkWriteMode.REPLACE_BLOCK.id,
    val newNoteTitleTemplate: String = "Despesas Mensais · {mês} {ano}",
    val groupBy: String = FinanceLinkGroupBy.PRIORITY.id,
    val mergeSameItems: Boolean = false,
    val extraColumnsCsv: String = "", // "CATEGORY,DATE,COMMENT"
    val includeBalanceSummary: Boolean = true,
    val includeAutoSummary: Boolean = false,
    val includeMermaidChart: Boolean = false,
    val filterCategoriesCsv: String = "",
    val filterPriority: String = "",
    val filterWallet: String = "",
    val filterTagsCsv: String = "",
    val filterCurrency: String = "",
    val notifyOnUpdate: Boolean = false,
    val emptyPeriodBehavior: String = "WRITE_EMPTY", // "WRITE_EMPTY" or "DO_NOTHING"
    val status: String = "ACTIVE", // "ACTIVE" or "PAUSED"
    val lastExecutedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "finance_link_executions")
data class FinanceLinkExecution(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val linkId: String,
    val executedAt: Long = System.currentTimeMillis(),
    val entriesCount: Int = 0,
    val resultStatus: String = "SUCCESS",
    val previousNoteContent: String? = null
)

fun formatFinanceAmount(
    amountMinor: Long,
    currency: FinanceCurrency,
    hideValues: Boolean = false
): String {
    if (hideValues) {
        return "•••• ${currency.symbol}"
    }
    val isNegative = amountMinor < 0
    val absMinor = kotlin.math.abs(amountMinor)
    val whole = absMinor / 100
    val cents = (absMinor % 100).toInt()
    val numberFormat = NumberFormat.getIntegerInstance(Locale("pt", "AO"))
    val wholeFormatted = numberFormat.format(whole)
    val sign = if (isNegative) "−" else ""
    return if (cents == 0) {
        "$sign$wholeFormatted ${currency.symbol}"
    } else {
        val centsStr = cents.toString().padStart(2, '0')
        "$sign$wholeFormatted,$centsStr ${currency.symbol}"
    }
}

fun parseDecimalToMinorUnits(input: String): Long? {
    val normalized = input.trim()
        .replace(" ", "")
        .replace(",", ".")
    val value = normalized.toDoubleOrNull() ?: return null
    return kotlin.math.round(value * 100.0).toLong()
}

fun formatMinorUnitsForInput(amountMinor: Long): String {
    val whole = amountMinor / 100
    val cents = kotlin.math.abs((amountMinor % 100).toInt())
    return if (cents == 0) {
        whole.toString()
    } else {
        "$whole,${cents.toString().padStart(2, '0')}"
    }
}

object FinanceCategoryIcons {
    val availableIcons: List<Pair<String, ImageVector>> = listOf(
        "shopping_cart" to Icons.Default.ShoppingCart,
        "directions_bus" to Icons.Default.DirectionsBus,
        "home" to Icons.Default.Home,
        "medical_services" to Icons.Default.MedicalServices,
        "school" to Icons.Default.School,
        "sports_esports" to Icons.Default.SportsEsports,
        "work" to Icons.Default.Work,
        "receipt_long" to Icons.Default.ReceiptLong,
        "card_giftcard" to Icons.Default.CardGiftcard,
        "handshake" to Icons.Default.Handshake,
        "trending_up" to Icons.Default.TrendingUp,
        "add_circle" to Icons.Default.AddCircle,
        "restaurant" to Icons.Default.Restaurant,
        "local_cafe" to Icons.Default.LocalCafe,
        "flight" to Icons.Default.Flight,
        "pets" to Icons.Default.Pets,
        "build" to Icons.Default.Build,
        "star" to Icons.Default.Star
    )

    val availableColors: List<Long> = listOf(
        0xFFFF8B1F, // Citra Orange
        0xFF3FA34D, // Citra Green
        0xFF2196F3, // Blue
        0xFFE8451F, // Coral Red
        0xFF9C27B0, // Purple
        0xFF009688, // Teal
        0xFFFFB300, // Amber
        0xFF3F51B5, // Indigo
        0xFFE91E63, // Pink
        0xFF607D8B  // Slate
    )

    fun iconForName(name: String): ImageVector =
        availableIcons.find { it.first == name }?.second ?: Icons.Default.Category

    fun defaultCategories(): List<FinanceCategory> = listOf(
        // 6 Default Expense Categories
        FinanceCategory(
            id = "def_exp_food",
            type = FinanceEntryType.EXPENSE.id,
            name = "Alimentação",
            iconName = "shopping_cart",
            colorHex = 0xFFFF8B1F,
            isDefault = true
        ),
        FinanceCategory(
            id = "def_exp_transport",
            type = FinanceEntryType.EXPENSE.id,
            name = "Transporte",
            iconName = "directions_bus",
            colorHex = 0xFF2196F3,
            isDefault = true
        ),
        FinanceCategory(
            id = "def_exp_home",
            type = FinanceEntryType.EXPENSE.id,
            name = "Casa e contas",
            iconName = "home",
            colorHex = 0xFF9C27B0,
            isDefault = true
        ),
        FinanceCategory(
            id = "def_exp_health",
            type = FinanceEntryType.EXPENSE.id,
            name = "Saúde e cuidados",
            iconName = "medical_services",
            colorHex = 0xFFE8451F,
            isDefault = true
        ),
        FinanceCategory(
            id = "def_exp_edu",
            type = FinanceEntryType.EXPENSE.id,
            name = "Educação",
            iconName = "school",
            colorHex = 0xFF3F51B5,
            isDefault = true
        ),
        FinanceCategory(
            id = "def_exp_leisure",
            type = FinanceEntryType.EXPENSE.id,
            name = "Lazer",
            iconName = "sports_esports",
            colorHex = 0xFF009688,
            isDefault = true
        ),
        // 6 Default Income Categories
        FinanceCategory(
            id = "def_inc_salary",
            type = FinanceEntryType.INCOME.id,
            name = "Salário",
            iconName = "work",
            colorHex = 0xFF3FA34D,
            isDefault = true
        ),
        FinanceCategory(
            id = "def_inc_freelance",
            type = FinanceEntryType.INCOME.id,
            name = "Negócio / Freelance",
            iconName = "receipt_long",
            colorHex = 0xFF009688,
            isDefault = true
        ),
        FinanceCategory(
            id = "def_inc_gift",
            type = FinanceEntryType.INCOME.id,
            name = "Presente",
            iconName = "card_giftcard",
            colorHex = 0xFFE91E63,
            isDefault = true
        ),
        FinanceCategory(
            id = "def_inc_debt",
            type = FinanceEntryType.INCOME.id,
            name = "Dívida recebida",
            iconName = "handshake",
            colorHex = 0xFF2196F3,
            isDefault = true
        ),
        FinanceCategory(
            id = "def_inc_invest",
            type = FinanceEntryType.INCOME.id,
            name = "Investimentos",
            iconName = "trending_up",
            colorHex = 0xFFFFB300,
            isDefault = true
        ),
        FinanceCategory(
            id = "def_inc_other",
            type = FinanceEntryType.INCOME.id,
            name = "Outras",
            iconName = "add_circle",
            colorHex = 0xFF607D8B,
            isDefault = true
        )
    )
}
