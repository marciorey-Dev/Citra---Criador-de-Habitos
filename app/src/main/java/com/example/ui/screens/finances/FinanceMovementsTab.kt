package com.example.ui.screens.finances

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FinanceCategory
import com.example.data.model.FinanceCategoryIcons
import com.example.data.model.FinanceCurrency
import com.example.data.model.FinanceEntry
import com.example.data.model.FinanceEntryType
import com.example.data.model.formatFinanceAmount
import com.example.ui.theme.GreenAccent
import com.example.ui.theme.RedAction
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class MovementTypeFilter(val label: String) {
    ALL("Todos"),
    EXPENSE("Despesas"),
    INCOME("Rendas")
}

private data class MovementDayGroup(
    val dayKey: String,
    val headerLabel: String,
    val entries: List<FinanceEntry>
)

@Composable
fun FinanceMovementsTab(
    realEntries: List<FinanceEntry>,
    categoryMap: Map<String, FinanceCategory>,
    hideValues: Boolean,
    periodMode: FinancePeriodMode,
    periodStartMillis: Long,
    periodEndMillis: Long,
    periodTitle: String,
    onSelectPeriodMode: (FinancePeriodMode) -> Unit,
    onShiftPeriod: (Int) -> Unit,
    onOpenCustomRangePicker: () -> Unit,
    onEditEntry: (FinanceEntry) -> Unit,
    onDuplicateEntry: (FinanceEntry) -> Unit,
    onDeleteEntry: (FinanceEntry) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf(MovementTypeFilter.ALL) }

    val filteredEntries = remember(
        realEntries,
        periodStartMillis,
        periodEndMillis,
        typeFilter,
        searchQuery,
        categoryMap
    ) {
        val q = searchQuery.trim()
        realEntries.filter { entry ->
            val inRange = entry.dateMillis in periodStartMillis..periodEndMillis
            val matchesType = when (typeFilter) {
                MovementTypeFilter.ALL -> true
                MovementTypeFilter.EXPENSE -> entry.type == FinanceEntryType.EXPENSE.id
                MovementTypeFilter.INCOME -> entry.type == FinanceEntryType.INCOME.id
            }
            val catName = categoryMap[entry.categoryId]?.name.orEmpty()
            val matchesSearch = q.isEmpty() ||
                entry.name.contains(q, ignoreCase = true) ||
                catName.contains(q, ignoreCase = true) ||
                entry.walletName.contains(q, ignoreCase = true) ||
                entry.comment.contains(q, ignoreCase = true)
            inRange && matchesType && matchesSearch
        }.sortedByDescending { it.dateMillis }
    }

    val ptLocale = remember { Locale.Builder().setLanguage("pt").setRegion("AO").build() }
    val keyFormatter = remember(ptLocale) { SimpleDateFormat("yyyy-MM-dd", ptLocale) }
    val displayDateFormatter = remember(ptLocale) { SimpleDateFormat("EEEE, dd 'de' MMM", ptLocale) }

    val dayGroups = remember(filteredEntries, keyFormatter, displayDateFormatter) {
        val todayCal = Calendar.getInstance()
        val todayKey = keyFormatter.format(todayCal.time)
        todayCal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayKey = keyFormatter.format(todayCal.time)

        filteredEntries
            .groupBy { keyFormatter.format(Date(it.dateMillis)) }
            .entries
            .map { (dayKey, items) ->
                val sampleMillis = items.first().dateMillis
                val label = when (dayKey) {
                    todayKey -> "Hoje"
                    yesterdayKey -> "Ontem"
                    else -> displayDateFormatter.format(Date(sampleMillis))
                        .replaceFirstChar { it.titlecase(ptLocale) }
                }
                MovementDayGroup(
                    dayKey = dayKey,
                    headerLabel = label,
                    entries = items
                )
            }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("finances_movements_tab"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 92.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Search Bar + Type Filter Pills
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Buscar por nome, categoria ou carteira...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = if (searchQuery.isNotBlank()) {
                        {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = "Limpar busca")
                            }
                        }
                    } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("finances_movements_search_input")
                )

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        MovementTypeFilter.entries.forEach { filter ->
                            val selected = typeFilter == filter
                            val bg = when {
                                selected && filter == MovementTypeFilter.INCOME -> GreenAccent
                                selected -> MaterialTheme.colorScheme.primary
                                else -> Color.Transparent
                            }
                            Surface(
                                shape = CircleShape,
                                color = bg,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(CircleShape)
                                    .clickable { typeFilter = filter }
                                    .testTag("finances_movements_filter_${filter.name.lowercase()}")
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = filter.label,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Shared Period Navigator
        item {
            FinancePeriodNavigatorCard(
                periodMode = periodMode,
                periodTitle = periodTitle,
                onSelectPeriodMode = onSelectPeriodMode,
                onShiftPeriod = onShiftPeriod,
                onOpenCustomRangePicker = onOpenCustomRangePicker
            )
        }

        // 3. Date-Grouped Chronological List (Zero mixed items)
        if (dayGroups.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Text(
                        text = if (searchQuery.isNotBlank()) {
                            "Nenhum movimento encontrado"
                        } else {
                            "Sem movimentos neste período"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = if (searchQuery.isNotBlank()) {
                            "Tente limpar a pesquisa ou trocar o filtro acima."
                        } else {
                            "Toque em '+ Novo lançamento' para registrar uma despesa ou renda."
                        },
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            dayGroups.forEach { group ->
                item(key = "header_${group.dayKey}") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 2.dp, start = 4.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = group.headerLabel,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${group.entries.size} lançamento(s)",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                items(group.entries, key = { it.id }) { entry ->
                    FinanceEntryRowCard(
                        entry = entry,
                        category = categoryMap[entry.categoryId],
                        hideValues = hideValues,
                        onEdit = { onEditEntry(entry) },
                        onDuplicate = { onDuplicateEntry(entry) },
                        onDelete = { onDeleteEntry(entry) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FinanceEntryRowCard(
    entry: FinanceEntry,
    category: FinanceCategory?,
    hideValues: Boolean,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val catColor = Color(category?.colorHex ?: 0xFFFF8B1F)
    val entryCurrency = remember(entry.currencyCode) { FinanceCurrency.fromCode(entry.currencyCode) }
    val isIncome = entry.type == FinanceEntryType.INCOME.id

    val subtitleText = remember(entry, category, entryCurrency, hideValues) {
        buildString {
            if (!category?.name.isNullOrBlank()) {
                append(category!!.name)
            }
            if (entry.unitPriceMinor != null) {
                val qtyStr = if (entry.quantity % 1.0 == 0.0) {
                    entry.quantity.toInt().toString()
                } else {
                    entry.quantity.toString().replace(".", ",")
                }
                val unitPart = if (entry.unit.isNotBlank()) " ${entry.unit}" else ""
                if (isNotEmpty()) append(" · ")
                append("$qtyStr$unitPart × ${formatFinanceAmount(entry.unitPriceMinor, entryCurrency, hideValues)}")
            }
            if (entry.comment.isNotBlank()) {
                if (isNotEmpty()) append(" · ")
                append(entry.comment)
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onEdit)
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(catColor.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = FinanceCategoryIcons.iconForName(category?.iconName ?: "shopping_cart"),
                    contentDescription = category?.name,
                    tint = catColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.name,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!entry.attachmentUri.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Tem anexo",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                if (subtitleText.isNotEmpty()) {
                    Text(
                        text = subtitleText,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (entry.walletName.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = if (isIncome) GreenAccent else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = entry.walletName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = if (isIncome) {
                    "+${formatFinanceAmount(entry.totalAmountMinor, entryCurrency, hideValues)}"
                } else {
                    "−${formatFinanceAmount(entry.totalAmountMinor, entryCurrency, hideValues)}"
                },
                fontSize = 14.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isIncome) GreenAccent else MaterialTheme.colorScheme.onSurface
            )

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Opções do lançamento",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        text = { Text("Editar") },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                        text = { Text("Duplicar") },
                        onClick = {
                            menuExpanded = false
                            onDuplicate()
                        }
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = RedAction) },
                        text = { Text("Apagar", color = RedAction) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}
