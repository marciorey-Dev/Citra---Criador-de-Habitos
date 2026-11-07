package com.example.data.backup

import com.example.data.model.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class FinancePeriodWindow(
    val startMillis: Long,
    val endMillis: Long,
    val label: String,
    val periodKey: String
)

object FinanceNoteMarkdownEngine {

    private val monthNamesPt = arrayOf(
        "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
        "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
    )

    fun computePeriodWindow(
        frequency: FinanceLinkFrequency,
        nowMillis: Long = System.currentTimeMillis()
    ): FinancePeriodWindow {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        return when (frequency) {
            FinanceLinkFrequency.DAILY -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
                val end = cal.timeInMillis
                val fmt = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "AO"))
                val keyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                FinancePeriodWindow(start, end, fmt.format(Date(start)), keyFmt.format(Date(start)))
            }
            FinanceLinkFrequency.WEEKLY -> {
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
                val end = cal.timeInMillis
                cal.add(Calendar.DAY_OF_YEAR, -6)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                val fmt = SimpleDateFormat("dd/MM", Locale("pt", "AO"))
                val weekNum = cal.get(Calendar.WEEK_OF_YEAR)
                val year = cal.get(Calendar.YEAR)
                FinancePeriodWindow(
                    start,
                    end,
                    "Semana $weekNum (${fmt.format(Date(start))} – ${fmt.format(Date(end))})",
                    "$year-W$weekNum"
                )
            }
            FinanceLinkFrequency.ANNUAL -> {
                val year = cal.get(Calendar.YEAR)
                cal.set(year, Calendar.JANUARY, 1, 0, 0, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                cal.set(year, Calendar.DECEMBER, 31, 23, 59, 59)
                cal.set(Calendar.MILLISECOND, 999)
                val end = cal.timeInMillis
                FinancePeriodWindow(start, end, "Ano $year", "$year")
            }
            FinanceLinkFrequency.MONTHLY, FinanceLinkFrequency.MANUAL -> {
                val year = cal.get(Calendar.YEAR)
                val month = cal.get(Calendar.MONTH)
                cal.set(year, month, 1, 0, 0, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                cal.set(year, month, maxDay, 23, 59, 59)
                cal.set(Calendar.MILLISECOND, 999)
                val end = cal.timeInMillis
                val monthName = monthNamesPt[month]
                FinancePeriodWindow(
                    start,
                    end,
                    "$monthName $year",
                    String.format(Locale.US, "%04d-%02d", year, month + 1)
                )
            }
        }
    }

    fun resolveNewNoteTitle(template: String, nowMillis: Long = System.currentTimeMillis()): String {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val monthName = monthNamesPt[cal.get(Calendar.MONTH)]
        val year = cal.get(Calendar.YEAR).toString()
        val day = cal.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
        return template
            .replace("{mês}", monthName, ignoreCase = true)
            .replace("{mes}", monthName, ignoreCase = true)
            .replace("{ano}", year, ignoreCase = true)
            .replace("{dia}", day, ignoreCase = true)
    }

    fun isDueForExecution(link: FinanceNoteLink, nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (link.status != "ACTIVE") return false
        val freq = FinanceLinkFrequency.fromId(link.frequency)
        if (freq == FinanceLinkFrequency.MANUAL) return false
        val last = link.lastExecutedAt ?: return true
        val elapsedMillis = nowMillis - last
        val minIntervalMillis = when (freq) {
            FinanceLinkFrequency.DAILY -> 20L * 60L * 60L * 1000L
            FinanceLinkFrequency.WEEKLY -> 6L * 24L * 60L * 60L * 1000L
            FinanceLinkFrequency.MONTHLY -> 27L * 24L * 60L * 60L * 1000L
            FinanceLinkFrequency.ANNUAL -> 360L * 24L * 60L * 60L * 1000L
            FinanceLinkFrequency.MANUAL -> Long.MAX_VALUE
        }
        return elapsedMillis >= minIntervalMillis
    }

    fun stripFinanceMarkers(content: String): String {
        return content.replace(Regex("<!--\\s*citra:fin:[^>]*-->\\s*\\n?"), "").trim()
    }

    fun hasManagedBlock(content: String, blockId: String): Boolean {
        val startRegex = Regex("<!--\\s*citra:fin:inicio\\s+id=${Regex.escape(blockId)}[^>]*-->")
        val endRegex = Regex("<!--\\s*citra:fin:fim\\s+id=${Regex.escape(blockId)}\\s*-->")
        return (startRegex.containsMatchIn(content) && endRegex.containsMatchIn(content)) || content.isNotBlank()
    }

    fun buildStartMarker(link: FinanceNoteLink, periodKey: String? = null): String {
        return ""
    }

    fun buildEndMarker(blockId: String): String {
        return ""
    }

    fun replaceOrInsertManagedBlock(
        originalContent: String,
        link: FinanceNoteLink,
        innerMarkdown: String,
        periodWindow: FinancePeriodWindow
    ): String {
        val mode = FinanceLinkWriteMode.fromId(link.writeMode)

        if (mode == FinanceLinkWriteMode.APPEND_SECTION) {
            val clean = stripFinanceMarkers(originalContent)
            val section = "\n\n### ${periodWindow.label}\n$innerMarkdown"
            return (clean + section).trimStart()
        }

        // REPLACE_BLOCK or NEW_NOTE_PER_PERIOD
        val startRegex = Regex("<!--\\s*citra:fin:inicio\\s+id=${Regex.escape(link.blockId)}[^>]*-->")
        val endRegex = Regex("<!--\\s*citra:fin:fim\\s+id=${Regex.escape(link.blockId)}\\s*-->")
        val startMatch = startRegex.find(originalContent)
        val endMatch = if (startMatch != null) endRegex.find(originalContent, startMatch.range.last + 1) else null

        return if (startMatch != null && endMatch != null) {
            val replaced = originalContent.replaceRange(startMatch.range.first..endMatch.range.last, innerMarkdown)
            stripFinanceMarkers(replaced)
        } else {
            val clean = stripFinanceMarkers(originalContent)
            if (clean.isBlank()) innerMarkdown else "$clean\n\n$innerMarkdown"
        }
    }

    fun filterEntriesForLink(
        allEntries: List<FinanceEntry>,
        link: FinanceNoteLink,
        periodWindow: FinancePeriodWindow
    ): List<FinanceEntry> {
        val catFilter = link.filterCategoriesCsv
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        val tagFilter = link.filterTagsCsv
            .split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()

        return allEntries.filter { entry ->
            if (entry.deletedAt != null) return@filter false
            if (entry.dateMillis !in periodWindow.startMillis..periodWindow.endMillis) return@filter false

            when (FinanceLinkSource.fromId(link.sourceType)) {
                FinanceLinkSource.EXPENSE -> if (entry.type != FinanceEntryType.EXPENSE.id) return@filter false
                FinanceLinkSource.INCOME -> if (entry.type != FinanceEntryType.INCOME.id) return@filter false
                FinanceLinkSource.BOTH -> {}
            }

            if (catFilter.isNotEmpty() && !catFilter.contains(entry.categoryId)) return@filter false
            if (link.filterPriority.isNotBlank() && entry.priority != link.filterPriority) return@filter false
            if (link.filterWallet.isNotBlank() && !entry.walletName.equals(link.filterWallet, ignoreCase = true)) return@filter false
            if (link.filterCurrency.isNotBlank() && !entry.currencyCode.equals(link.filterCurrency, ignoreCase = true)) return@filter false
            if (tagFilter.isNotEmpty()) {
                val entryTags = entry.tagsCsv.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
                if (entryTags.intersect(tagFilter).isEmpty()) return@filter false
            }
            true
        }
    }

    fun generateBlockInnerMarkdown(
        link: FinanceNoteLink,
        allEntries: List<FinanceEntry>,
        categories: List<FinanceCategory>,
        currentBalanceMinor: Long,
        defaultCurrency: FinanceCurrency,
        nowMillis: Long = System.currentTimeMillis()
    ): Pair<String, Int> {
        val freq = FinanceLinkFrequency.fromId(link.frequency)
        val window = computePeriodWindow(freq, nowMillis)
        val filtered = filterEntriesForLink(allEntries, link, window)
        val categoryMap = categories.associateBy { it.id }

        if (filtered.isEmpty()) {
            val emptyText = if (link.emptyPeriodBehavior == "DO_NOTHING") {
                ""
            } else {
                "_Sem lançamentos neste período (${window.label})._"
            }
            return emptyText to 0
        }

        val extraCols = link.extraColumnsCsv
            .split(",")
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }

        val sb = StringBuilder()
        var hasInconsistency = false

        fun isPending(e: FinanceEntry): Boolean =
            e.unitPriceMinor == null && e.totalAmountMinor == 0L

        fun formatQty(e: FinanceEntry): String {
            if (isPending(e) && e.quantity == 1.0 && e.unit.isBlank()) return "—"
            val qtyStr = if (e.quantity == e.quantity.toLong().toDouble()) {
                e.quantity.toLong().toString()
            } else {
                String.format(Locale("pt", "AO"), "%.2f", e.quantity).trimEnd('0').trimEnd(',')
            }
            return if (e.unit.isNotBlank()) "$qtyStr ${e.unit}" else qtyStr
        }

        fun appendTable(rows: List<FinanceEntry>) {
            val processed = if (link.mergeSameItems) {
                rows.groupBy { "${it.name.trim().lowercase()}|${it.unit.trim().lowercase()}|${it.unitPriceMinor ?: -1L}" }
                    .values
                    .map { group ->
                        val first = group.first()
                        if (group.size == 1) first
                        else first.copy(
                            quantity = group.sumOf { it.quantity },
                            totalAmountMinor = group.sumOf { it.totalAmountMinor }
                        )
                    }
            } else {
                rows
            }

            val headers = mutableListOf("Item", "Quantidade", "Valor Unit.", "Valor Total")
            if ("CATEGORY" in extraCols) headers.add("Categoria")
            if ("DATE" in extraCols) headers.add("Data")
            if ("COMMENT" in extraCols) headers.add("Comentário")

            sb.append("| ${headers.joinToString(" | ")} |\n")
            sb.append("|${headers.joinToString("|") { "---" }}|\n")

            val dateFmt = SimpleDateFormat("dd/MM", Locale("pt", "AO"))

            processed.forEach { e ->
                val curr = FinanceCurrency.fromCode(e.currencyCode)
                val pending = isPending(e)
                if (!pending && e.unitPriceMinor != null && e.totalAmountMinor > 0L) {
                    val expected = kotlin.math.round(e.quantity * e.unitPriceMinor).toLong()
                    if (expected != e.totalAmountMinor) {
                        hasInconsistency = true
                    }
                }
                val itemName = if (e.priority == FinancePriority.MANDATORY.id && e.type == FinanceEntryType.EXPENSE.id) {
                    "**${e.name}**"
                } else {
                    e.name
                }
                val qtyCol = formatQty(e)
                val unitCol = if (e.unitPriceMinor != null) formatFinanceAmount(e.unitPriceMinor, curr) else "—"
                val totalCol = if (pending) "⚠️ Pendente" else formatFinanceAmount(e.totalAmountMinor, curr)

                val cols = mutableListOf(itemName, qtyCol, unitCol, totalCol)
                if ("CATEGORY" in extraCols) cols.add(categoryMap[e.categoryId]?.name ?: "Outros")
                if ("DATE" in extraCols) cols.add(dateFmt.format(Date(e.dateMillis)))
                if ("COMMENT" in extraCols) cols.add(e.comment.ifBlank { "—" })

                sb.append("| ${cols.joinToString(" | ")} |\n")
            }
        }

        val groupBy = FinanceLinkGroupBy.fromId(link.groupBy)
        val expenses = filtered.filter { it.type == FinanceEntryType.EXPENSE.id }
        val incomes = filtered.filter { it.type == FinanceEntryType.INCOME.id }

        when (groupBy) {
            FinanceLinkGroupBy.PRIORITY -> {
                if (incomes.isNotEmpty()) {
                    sb.append("### Rendas\n")
                    appendTable(incomes)
                    sb.append("\n")
                }
                val mandatoryExp = expenses.filter { it.priority == FinancePriority.MANDATORY.id }
                val optionalExp = expenses.filter { it.priority == FinancePriority.OPTIONAL.id }
                if (mandatoryExp.isNotEmpty()) {
                    sb.append("### Despesas Obrigatórias\n")
                    appendTable(mandatoryExp)
                    sb.append("\n")
                }
                if (optionalExp.isNotEmpty()) {
                    sb.append("### Despesas Opcionais\n")
                    sb.append("_Estas despesas não serão calculadas no saldo final._\n\n")
                    appendTable(optionalExp)
                    sb.append("\n")
                }
            }
            FinanceLinkGroupBy.CATEGORY -> {
                val grouped = filtered.groupBy { categoryMap[it.categoryId]?.name ?: "Outros" }
                grouped.forEach { (catName, list) ->
                    sb.append("### $catName\n")
                    appendTable(list)
                    sb.append("\n")
                }
            }
            FinanceLinkGroupBy.NONE -> {
                appendTable(filtered)
                sb.append("\n")
            }
        }

        // Balance Summary & Simulation
        if (link.includeBalanceSummary) {
            val mandatoryTotal = expenses
                .filter { it.priority == FinancePriority.MANDATORY.id && !isPending(it) }
                .sumOf { it.totalAmountMinor }
            val optionalTotal = expenses
                .filter { it.priority == FinancePriority.OPTIONAL.id && !isPending(it) }
                .sumOf { it.totalAmountMinor }
            val incomeTotal = incomes
                .filter { !isPending(it) }
                .sumOf { it.totalAmountMinor }

            if (expenses.isNotEmpty()) {
                val finalBalance = currentBalanceMinor - mandatoryTotal
                sb.append("| Total das despesas | Saldo | Saldo final |\n")
                sb.append("|---|---|---|\n")
                sb.append(
                    "| ${formatFinanceAmount(mandatoryTotal, defaultCurrency)} | " +
                        "${formatFinanceAmount(currentBalanceMinor, defaultCurrency)} | " +
                        "${formatFinanceAmount(currentBalanceMinor, defaultCurrency)} − ${formatFinanceAmount(mandatoryTotal, defaultCurrency)} = **${formatFinanceAmount(finalBalance, defaultCurrency)}** |\n\n"
                )

                if (optionalTotal > 0L) {
                    val totalWithOptional = mandatoryTotal + optionalTotal
                    val simulatedBalance = currentBalanceMinor - totalWithOptional
                    sb.append("| Total dos opcionais | Saldo final se incluir os opcionais |\n")
                    sb.append("|---|---|\n")
                    sb.append(
                        "| ${formatFinanceAmount(optionalTotal, defaultCurrency)} | " +
                            "${formatFinanceAmount(currentBalanceMinor, defaultCurrency)} − ${formatFinanceAmount(totalWithOptional, defaultCurrency)} = **${formatFinanceAmount(simulatedBalance, defaultCurrency)}** |\n\n"
                    )
                }
            }

            if (incomes.isNotEmpty()) {
                sb.append("| Total das rendas |\n")
                sb.append("|---|\n")
                sb.append("| **${formatFinanceAmount(incomeTotal, defaultCurrency)}** |\n\n")
            }
        }

        // Auto Summary
        if (link.includeAutoSummary && filtered.isNotEmpty()) {
            val validExpenses = expenses.filter { !isPending(it) }
            val topExpense = validExpenses.maxByOrNull { it.totalAmountMinor }
            val totalExp = validExpenses.sumOf { it.totalAmountMinor }
            sb.append("**Resumo automático (${window.label}):**\n")
            if (topExpense != null) {
                sb.append("- Maior despesa: **${topExpense.name}** (${formatFinanceAmount(topExpense.totalAmountMinor, defaultCurrency)})\n")
            }
            if (totalExp > 0L) {
                val byCat = validExpenses.groupBy { categoryMap[it.categoryId]?.name ?: "Outros" }
                    .mapValues { (_, list) -> list.sumOf { it.totalAmountMinor } }
                    .entries
                    .sortedByDescending { it.value }
                    .take(3)
                byCat.forEach { (cat, sum) ->
                    val pct = ((sum.toDouble() / totalExp.toDouble()) * 100.0).toInt()
                    sb.append("- $cat: ${formatFinanceAmount(sum, defaultCurrency)} ($pct%)\n")
                }
            }
            sb.append("\n")
        }



        if (hasInconsistency) {
            sb.append("> ⚠️ **Alerta de inconsistência:** alguns itens possuem quantidade × valor unitário diferente do valor total.\n")
        }

        return sb.toString().trim() to filtered.size
    }

    fun buildNewNoteScaffold(
        template: FinanceNoteTemplate,
        noteTitle: String,
        link: FinanceNoteLink,
        innerMarkdown: String
    ): String {
        val headerNote = when (template) {
            FinanceNoteTemplate.SHOPPING_LIST -> "\n\n## Notas de Compras\n- [ ] Rever preços antes de fechar a lista\n"
            FinanceNoteTemplate.MONTHLY_EXPENSES -> "\n\n## Resumo\nEscreva aqui as suas observações pessoais sobre o mês...\n"
            else -> ""
        }
        return "# $noteTitle\n\n$innerMarkdown$headerNote"
    }
}
