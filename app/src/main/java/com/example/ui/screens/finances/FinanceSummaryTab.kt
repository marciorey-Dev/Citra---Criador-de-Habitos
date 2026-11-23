package com.example.ui.screens.finances

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.PieChartOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SouthEast
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FinanceCategory
import com.example.data.model.FinanceCurrency
import com.example.data.model.FinanceEntry
import com.example.data.model.FinanceEntryType
import com.example.data.model.FinanceExchangeRate
import com.example.data.model.convertFinanceMinorUnits
import com.example.data.model.formatFinanceAmount
import com.example.ui.theme.GreenAccent
import com.example.ui.theme.RedAction
import com.example.ui.tutorial.tutorialTarget
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class FinancePeriodMode(val label: String) {
    DAY("Dia"),
    WEEK("Semana"),
    MONTH("Mês"),
    YEAR("Ano"),
    CUSTOM("Período")
}

data class FinancePieSlice(
    val categoryName: String,
    val color: Color,
    val amountMinor: Long,
    val fraction: Float
)

@Composable
fun FinanceSummaryTab(
    listState: LazyListState,
    currentBalanceMinor: Long,
    activeCurrency: FinanceCurrency,
    hideValues: Boolean,
    realEntries: List<FinanceEntry>,
    categoryMap: Map<String, FinanceCategory>,
    exchangeRates: List<FinanceExchangeRate>,
    periodMode: FinancePeriodMode,
    periodStartMillis: Long,
    periodEndMillis: Long,
    periodTitle: String,
    onSelectPeriodMode: (FinancePeriodMode) -> Unit,
    onShiftPeriod: (Int) -> Unit,
    onOpenCustomRangePicker: () -> Unit,
    onOpenCurrencyPicker: () -> Unit,
    onToggleHideValues: () -> Unit,
    onOpenFinanceSettings: () -> Unit,
    onOpenNewEntry: (FinanceEntryType) -> Unit
) {
    var chartMode by remember { mutableStateOf(FinanceEntryType.EXPENSE) }

    val periodEntries = remember(realEntries, periodStartMillis, periodEndMillis) {
        realEntries.filter { it.dateMillis in periodStartMillis..periodEndMillis }
    }

    val periodIncomeMinor = remember(periodEntries, activeCurrency, exchangeRates) {
        periodEntries
            .filter { it.type == FinanceEntryType.INCOME.id }
            .sumOf { convertFinanceMinorUnits(it.totalAmountMinor, it.currencyCode, activeCurrency.code, exchangeRates) }
    }

    val periodExpenseMinor = remember(periodEntries, activeCurrency, exchangeRates) {
        periodEntries
            .filter { it.type == FinanceEntryType.EXPENSE.id }
            .sumOf { convertFinanceMinorUnits(it.totalAmountMinor, it.currencyCode, activeCurrency.code, exchangeRates) }
    }

    val periodNetMinor = periodIncomeMinor - periodExpenseMinor

    val chartEntries = remember(periodEntries, chartMode) {
        periodEntries.filter { it.type == chartMode.id }
    }

    val chartTotalMinor = if (chartMode == FinanceEntryType.EXPENSE) periodExpenseMinor else periodIncomeMinor

    // Cap donut visualization at 4 main slices + optional aggregated "Outros" (max 5 slices)
    val chartSlices = remember(chartEntries, categoryMap, chartTotalMinor, activeCurrency, exchangeRates) {
        if (chartTotalMinor <= 0L) {
            emptyList()
        } else {
            val grouped = chartEntries
                .groupBy { it.categoryId }
                .map { (catId, list) ->
                    val sum = list.sumOf {
                        convertFinanceMinorUnits(it.totalAmountMinor, it.currencyCode, activeCurrency.code, exchangeRates)
                    }
                    val cat = categoryMap[catId]
                    FinancePieSlice(
                        categoryName = cat?.name ?: "Outros",
                        color = Color(cat?.colorHex ?: 0xFFFF8B1F),
                        amountMinor = sum,
                        fraction = (sum.toFloat() / chartTotalMinor.toFloat()).coerceIn(0f, 1f)
                    )
                }
                .sortedByDescending { it.amountMinor }

            if (grouped.size <= 5) {
                grouped
            } else {
                val top4 = grouped.take(4)
                val restSum = grouped.drop(4).sumOf { it.amountMinor }
                top4 + FinancePieSlice(
                    categoryName = "Outros",
                    color = Color(0xFF78909C),
                    amountMinor = restSum,
                    fraction = (restSum.toFloat() / chartTotalMinor.toFloat()).coerceIn(0f, 1f)
                )
            }
        }
    }

    val isExpenseChart = chartMode == FinanceEntryType.EXPENSE

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .testTag("finances_summary_tab"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 92.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Single Consolidated Balance Card
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .tutorialTarget("finances_summary_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "SALDO ATUAL",
                            fontSize = 11.5.sp,
                            letterSpacing = 0.8.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable(onClick = onOpenCurrencyPicker)
                                    .testTag("finance_currency_pill")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        text = "${activeCurrency.symbol} · ${activeCurrency.code}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Icon(
                                        Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Mudar moeda padrão",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            IconButton(
                                onClick = onToggleHideValues,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("finance_privacy_toggle")
                            ) {
                                Icon(
                                    imageVector = if (hideValues) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (hideValues) "Mostrar valores" else "Ocultar valores",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = onOpenFinanceSettings,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("finance_settings_shortcut")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Configurações e PIN de Finanças",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = formatFinanceAmount(currentBalanceMinor, activeCurrency, hideValues),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (!hideValues && currentBalanceMinor < 0L) {
                            RedAction
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
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

        // 3. "Entrou vs. Saiu" Scoreboard for Active Period
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Income side
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = GreenAccent.copy(alpha = if (!isExpenseChart) 0.14f else 0.07f),
                            border = BorderStroke(
                                width = if (!isExpenseChart) 1.5.dp else 1.dp,
                                color = GreenAccent.copy(alpha = if (!isExpenseChart) 0.55f else 0.25f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { chartMode = FinanceEntryType.INCOME }
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.NorthEast,
                                        contentDescription = null,
                                        tint = GreenAccent,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Entrou (Rendas)",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = GreenAccent
                                    )
                                }
                                Text(
                                    text = "+${formatFinanceAmount(periodIncomeMinor, activeCurrency, hideValues)}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = GreenAccent,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Expense side
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = if (isExpenseChart) 0.12f else 0.05f),
                            border = BorderStroke(
                                width = if (isExpenseChart) 1.5.dp else 1.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = if (isExpenseChart) 0.55f else 0.22f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { chartMode = FinanceEntryType.EXPENSE }
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SouthEast,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Saiu (Despesas)",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    text = "−${formatFinanceAmount(periodExpenseMinor, activeCurrency, hideValues)}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Period Balance Strip
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Balanço do período",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = (if (periodNetMinor > 0L) "+" else "") +
                                    formatFinanceAmount(periodNetMinor, activeCurrency, hideValues),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = when {
                                    hideValues -> MaterialTheme.colorScheme.onSurface
                                    periodNetMinor >= 0L -> GreenAccent
                                    else -> RedAction
                                }
                            )
                        }
                    }
                }
            }
        }

        // 4. Clean Donut Distribution Card (Top 4-5 categories)
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isExpenseChart) "Principais despesas" else "Principais rendas",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = periodTitle,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Compact Donut filter toggle
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(modifier = Modifier.padding(3.dp)) {
                                FinanceEntryType.entries.forEach { mode ->
                                    val selected = chartMode == mode
                                    val pillColor = when {
                                        selected && mode == FinanceEntryType.EXPENSE -> MaterialTheme.colorScheme.primary
                                        selected && mode == FinanceEntryType.INCOME -> GreenAccent
                                        else -> Color.Transparent
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(pillColor)
                                            .clickable { chartMode = mode }
                                            .padding(horizontal = 10.dp, vertical = 5.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (mode == FinanceEntryType.EXPENSE) "Despesas" else "Rendas",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (chartSlices.isEmpty()) {
                        EmptyFinanceChartPlaceholder(
                            isExpenseMode = isExpenseChart,
                            onAddClick = { onOpenNewEntry(chartMode) }
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier.size(164.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val strokeWidth = 22.dp.toPx()
                                    val diameter = size.minDimension - strokeWidth
                                    val topLeft = Offset(
                                        (size.width - diameter) / 2f,
                                        (size.height - diameter) / 2f
                                    )
                                    var startAngle = -90f
                                    chartSlices.forEach { slice ->
                                        val sweep = (slice.fraction * 360f).coerceAtLeast(3f)
                                        drawArc(
                                            color = slice.color,
                                            startAngle = startAngle,
                                            sweepAngle = sweep,
                                            useCenter = false,
                                            topLeft = topLeft,
                                            size = Size(diameter, diameter),
                                            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                                        )
                                        startAngle += sweep
                                    }
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "TOTAL",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = formatFinanceAmount(chartTotalMinor, activeCurrency, hideValues),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isExpenseChart) MaterialTheme.colorScheme.onSurface else GreenAccent,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            chartSlices.forEach { slice ->
                                val pct = kotlin.math.round(slice.fraction * 100f).toInt()
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(slice.color, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = slice.categoryName,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = formatFinanceAmount(slice.amountMinor, activeCurrency, hideValues),
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "$pct%",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = slice.color
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FinancePeriodNavigatorCard(
    periodMode: FinancePeriodMode,
    periodTitle: String,
    onSelectPeriodMode: (FinancePeriodMode) -> Unit,
    onShiftPeriod: (Int) -> Unit,
    onOpenCustomRangePicker: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable {
                            if (periodMode == FinancePeriodMode.CUSTOM) onOpenCustomRangePicker()
                            else onShiftPeriod(-1)
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Período anterior",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            if (periodMode == FinancePeriodMode.CUSTOM) onOpenCustomRangePicker()
                        }
                        .padding(vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = periodTitle,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable {
                            if (periodMode == FinancePeriodMode.CUSTOM) onOpenCustomRangePicker()
                            else onShiftPeriod(1)
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Próximo período",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FinancePeriodMode.entries.forEach { mode ->
                    val selected = periodMode == mode
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow,
                        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                onSelectPeriodMode(mode)
                                if (mode == FinancePeriodMode.CUSTOM) onOpenCustomRangePicker()
                            }
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = mode.label,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyFinanceChartPlaceholder(
    isExpenseMode: Boolean,
    onAddClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(MaterialTheme.colorScheme.surfaceContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PieChartOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp)
            )
        }
        Text(
            text = "Sem ${if (isExpenseMode) "despesas" else "rendas"} neste período",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Registre um lançamento em segundos para ver a distribuição por categoria.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(2.dp))
        OutlinedButton(
            onClick = onAddClick,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.heightIn(min = 44.dp)
        ) {
            Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isExpenseMode) "Lançar despesa" else "Lançar renda",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

fun computeFinancePeriodRange(
    mode: FinancePeriodMode,
    anchorMillis: Long,
    customStartMillis: Long,
    customEndMillis: Long
): Triple<Long, Long, String> {
    val cal = Calendar.getInstance().apply { timeInMillis = anchorMillis }
    val locale = Locale.Builder().setLanguage("pt").setRegion("AO").build()
    return when (mode) {
        FinancePeriodMode.DAY -> {
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
            val title = SimpleDateFormat("dd MMM yyyy", locale).format(Date(start))
            Triple(start, end, title)
        }
        FinancePeriodMode.WEEK -> {
            cal.firstDayOfWeek = Calendar.MONDAY
            cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val start = cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, 6)
            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            cal.set(Calendar.MILLISECOND, 999)
            val end = cal.timeInMillis
            val fmt = SimpleDateFormat("dd MMM", locale)
            Triple(start, end, "${fmt.format(Date(start))} – ${fmt.format(Date(end))}")
        }
        FinancePeriodMode.MONTH -> {
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val start = cal.timeInMillis
            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            cal.set(Calendar.MILLISECOND, 999)
            val end = cal.timeInMillis
            val raw = SimpleDateFormat("MMMM yyyy", locale).format(Date(start))
            val title = raw.replaceFirstChar { it.titlecase(locale) }
            Triple(start, end, title)
        }
        FinancePeriodMode.YEAR -> {
            cal.set(Calendar.DAY_OF_YEAR, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val start = cal.timeInMillis
            cal.set(Calendar.MONTH, Calendar.DECEMBER)
            cal.set(Calendar.DAY_OF_MONTH, 31)
            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            cal.set(Calendar.MILLISECOND, 999)
            val end = cal.timeInMillis
            Triple(start, end, cal.get(Calendar.YEAR).toString())
        }
        FinancePeriodMode.CUSTOM -> {
            val sCal = Calendar.getInstance().apply {
                timeInMillis = minOf(customStartMillis, customEndMillis)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val eCal = Calendar.getInstance().apply {
                timeInMillis = maxOf(customStartMillis, customEndMillis)
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }
            val fmt = SimpleDateFormat("dd/MM/yy", locale)
            Triple(
                sCal.timeInMillis,
                eCal.timeInMillis,
                "${fmt.format(Date(sCal.timeInMillis))} – ${fmt.format(Date(eCal.timeInMillis))}"
            )
        }
    }
}

fun shiftFinanceAnchor(
    anchorMillis: Long,
    mode: FinancePeriodMode,
    delta: Int
): Long {
    val cal = Calendar.getInstance().apply { timeInMillis = anchorMillis }
    when (mode) {
        FinancePeriodMode.DAY -> cal.add(Calendar.DAY_OF_YEAR, delta)
        FinancePeriodMode.WEEK -> cal.add(Calendar.WEEK_OF_YEAR, delta)
        FinancePeriodMode.MONTH -> cal.add(Calendar.MONTH, delta)
        FinancePeriodMode.YEAR -> cal.add(Calendar.YEAR, delta)
        FinancePeriodMode.CUSTOM -> {}
    }
    return cal.timeInMillis
}
