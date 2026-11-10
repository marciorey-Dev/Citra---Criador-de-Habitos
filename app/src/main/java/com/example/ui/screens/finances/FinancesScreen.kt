package com.example.ui.screens.finances

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.ui.tutorial.CitraTutorialArea
import com.example.ui.tutorial.tutorialTarget
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.MainViewModel
import com.example.ui.theme.GreenAccent
import com.example.ui.theme.RedAction
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinancesScreen(
    viewModel: MainViewModel,
    onOpenFinanceSettings: () -> Unit
) {
    val defaultCurrencyCode by viewModel.financeDefaultCurrency.collectAsState()
    val baseBalanceMinor by viewModel.financeBaseBalanceMinor.collectAsState()
    val hideValues by viewModel.financeHideValues.collectAsState()
    val pinCode by viewModel.financePinCode.collectAsState()
    val biometricEnabled by viewModel.financeBiometricEnabled.collectAsState()
    val categories by viewModel.financeCategories.collectAsState()
    val allCategories by viewModel.allFinanceCategories.collectAsState()
    val entries by viewModel.financeEntries.collectAsState()

    var isUnlocked by remember { mutableStateOf(false) }
    val requiresLock = !pinCode.isNullOrBlank()

    LaunchedEffect(Unit) {
        viewModel.ensureDefaultFinanceCategories()
    }

    if (requiresLock && !isUnlocked) {
        FinanceLockOverlay(
            expectedPin = pinCode,
            biometricEnabled = false,
            onUnlocked = { isUnlocked = true }
        )
        return
    }

    val activeCurrency = remember(defaultCurrencyCode) {
        FinanceCurrency.fromCode(defaultCurrencyCode)
    }

    val wallets by viewModel.financeWallets.collectAsState()
    val exchangeRates by viewModel.financeExchangeRates.collectAsState()
    val budgets by viewModel.financeBudgets.collectAsState()
    val recurringRules by viewModel.financeRecurringRules.collectAsState()

    val realEntries = remember(entries) { entries.filter { !it.isPlanned } }
    val plannedEntries = remember(entries) { entries.filter { it.isPlanned } }

    // Current balance = baseBalance + totalIncome - totalExpense (converted to activeCurrency)
    val currentBalanceMinor = remember(baseBalanceMinor, realEntries, activeCurrency, exchangeRates) {
        val net = realEntries.sumOf {
            val converted = convertFinanceMinorUnits(it.totalAmountMinor, it.currencyCode, activeCurrency.code, exchangeRates)
            if (it.type == FinanceEntryType.INCOME.id) converted else -converted
        }
        baseBalanceMinor + net
    }

    var selectedType by remember { mutableStateOf(FinanceEntryType.EXPENSE) }
    var periodMode by remember { mutableStateOf(FinancePeriodMode.MONTH) }
    var anchorMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var customStartMillis by remember { mutableLongStateOf(System.currentTimeMillis() - 7L * 86_400_000L) }
    var customEndMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showCustomRangePicker by remember { mutableStateOf(false) }

    var showEntryEditor by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<FinanceEntry?>(null) }
    var currencyMenuExpanded by remember { mutableStateOf(false) }
    var overflowMenuExpanded by remember { mutableStateOf(false) }
    var showLinkCreatorDialog by remember { mutableStateOf(false) }
    var showLinksManagerDialog by remember { mutableStateOf(false) }
    var showWalletsDialog by remember { mutableStateOf(false) }
    var showBudgetsDialog by remember { mutableStateOf(false) }
    var showRecurringDialog by remember { mutableStateOf(false) }
    var showExchangeRatesDialog by remember { mutableStateOf(false) }
    var completingPlannedEntry by remember { mutableStateOf<FinanceEntry?>(null) }

    // Compute period [startMillis, endMillis]
    val (periodStartMillis, periodEndMillis, periodTitle) = remember(
        periodMode,
        anchorMillis,
        customStartMillis,
        customEndMillis
    ) {
        computeFinancePeriodRange(periodMode, anchorMillis, customStartMillis, customEndMillis)
    }

    val periodEntries = remember(realEntries, selectedType, periodStartMillis, periodEndMillis) {
        realEntries.filter { entry ->
            entry.type == selectedType.id &&
                entry.dateMillis in periodStartMillis..periodEndMillis
        }
    }

    val periodTotalMinor = remember(periodEntries, activeCurrency, exchangeRates) {
        periodEntries.sumOf {
            convertFinanceMinorUnits(it.totalAmountMinor, it.currencyCode, activeCurrency.code, exchangeRates)
        }
    }

    val categoryMap = remember(allCategories) {
        allCategories.associateBy { it.id }
    }

    // Pie chart slices grouped by category
    val pieSlices = remember(periodEntries, categoryMap, periodTotalMinor, activeCurrency, exchangeRates) {
        if (periodTotalMinor <= 0L) {
            emptyList()
        } else {
            periodEntries
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
                        fraction = sum.toFloat() / periodTotalMinor.toFloat()
                    )
                }
                .sortedByDescending { it.amountMinor }
        }
    }

    // 5-day strip centered around anchorMillis (-2..+2 days)
    val fiveDayStrip = remember(anchorMillis, realEntries, selectedType, activeCurrency, exchangeRates) {
        val locale = Locale("pt", "AO")
        val dayNameFmt = SimpleDateFormat("EEE", locale)
        val dayNumFmt = SimpleDateFormat("dd", locale)
        (-2..2).map { offset ->
            val cal = Calendar.getInstance().apply {
                timeInMillis = anchorMillis
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val start = cal.timeInMillis
            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            cal.set(Calendar.MILLISECOND, 999)
            val end = cal.timeInMillis
            val dayTotal = realEntries
                .filter { it.type == selectedType.id && it.dateMillis in start..end }
                .sumOf { convertFinanceMinorUnits(it.totalAmountMinor, it.currencyCode, activeCurrency.code, exchangeRates) }
            val rawDow = dayNameFmt.format(Date(start)).replace(".", "")
                .replaceFirstChar { it.titlecase(locale) }
            FinanceDayStripItem(
                dayMillis = start,
                label = "$rawDow ${dayNumFmt.format(Date(start))}",
                totalMinor = dayTotal,
                isSelected = offset == 0
            )
        }
    }

    val incomeCategoriesWithTotals = remember(allCategories, periodEntries, activeCurrency, exchangeRates) {
        allCategories
            .filter { it.type == FinanceEntryType.INCOME.id && !it.isArchived }
            .map { cat ->
                val sum = periodEntries
                    .filter { it.categoryId == cat.id }
                    .sumOf { convertFinanceMinorUnits(it.totalAmountMinor, it.currencyCode, activeCurrency.code, exchangeRates) }
                cat to sum
            }
    }

    val isExpenseMode = selectedType == FinanceEntryType.EXPENSE
    val modeAccentColor = if (isExpenseMode) MaterialTheme.colorScheme.primary else GreenAccent

    val listState = rememberLazyListState()
    val activeTutorial by viewModel.activeTutorial.collectAsState()

    LaunchedEffect(activeTutorial?.area, activeTutorial?.stepIndex) {
        if (activeTutorial?.area == CitraTutorialArea.FINANCES) {
            val targetIndex = when (activeTutorial?.currentStep?.targetKey) {
                "finances_summary_card", "finances_wallets_bar", "finances_header_actions" -> 0
                "finances_type_toggle" -> 1
                "finances_period_nav" -> 2
                "finances_distribution_card" -> 3
                "finances_add_button" -> 4
                "finances_list_area" -> 5
                else -> 0
            }
            listState.animateScrollToItem(targetIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("finances_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Top SALDO ATUAL & Wallets Card
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp, horizontal = 2.dp)
                        ) {
                            Text(
                                text = "SALDO ATUAL",
                                fontSize = 11.5.sp,
                                letterSpacing = 0.8.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.tutorialTarget("finances_header_actions")
                        ) {
                            // Currency selector pill (Kz ▾)
                            Box {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { currencyMenuExpanded = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = activeCurrency.symbol,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.5.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Icon(
                                            Icons.Default.ArrowDropDown,
                                            contentDescription = "Mudar moeda",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                DropdownMenu(
                                    expanded = currencyMenuExpanded,
                                    onDismissRequest = { currencyMenuExpanded = false }
                                ) {
                                    FinanceCurrency.entries.forEach { curr ->
                                        DropdownMenuItem(
                                            text = { Text(curr.displayName) },
                                            onClick = {
                                                viewModel.setFinanceDefaultCurrency(curr.code)
                                                currencyMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = { viewModel.setFinanceHideValues(!hideValues) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = if (hideValues) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (hideValues) "Mostrar valores" else "Ocultar valores",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(
                                onClick = { showWalletsDialog = true },
                                modifier = Modifier
                                    .size(34.dp)
                                    .tutorialTarget(if (wallets.isEmpty()) "finances_wallets_bar" else "finances_wallet_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountBalanceWallet,
                                    contentDescription = "Carteiras e contas",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Overflow menu
                            Box {
                                IconButton(
                                    onClick = { overflowMenuExpanded = true },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        contentDescription = "Mais opções",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                DropdownMenu(
                                    expanded = overflowMenuExpanded,
                                    onDismissRequest = { overflowMenuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        leadingIcon = { Icon(Icons.Default.NoteAdd, contentDescription = null) },
                                        text = { Text("Vincular a nota") },
                                        onClick = {
                                            overflowMenuExpanded = false
                                            showLinkCreatorDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        leadingIcon = { Icon(Icons.Default.SyncAlt, contentDescription = null) },
                                        text = { Text("Gerir vínculos") },
                                        onClick = {
                                            overflowMenuExpanded = false
                                            showLinksManagerDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        leadingIcon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null) },
                                        text = { Text("Carteiras e contas") },
                                        onClick = {
                                            overflowMenuExpanded = false
                                            showWalletsDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        leadingIcon = { Icon(Icons.Default.PieChart, contentDescription = null) },
                                        text = { Text("Orçamentos por categoria") },
                                        onClick = {
                                            overflowMenuExpanded = false
                                            showBudgetsDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        leadingIcon = { Icon(Icons.Default.Repeat, contentDescription = null) },
                                        text = { Text("Lançamentos recorrentes") },
                                        onClick = {
                                            overflowMenuExpanded = false
                                            showRecurringDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        leadingIcon = { Icon(Icons.Default.CurrencyExchange, contentDescription = null) },
                                        text = { Text("Câmbio e moedas") },
                                        onClick = {
                                            overflowMenuExpanded = false
                                            showExchangeRatesDialog = true
                                        }
                                    )
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                        text = { Text("Configurações de Finanças") },
                                        onClick = {
                                            overflowMenuExpanded = false
                                            onOpenFinanceSettings()
                                        }
                                    )
                                }
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

                    if (wallets.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .tutorialTarget("finances_wallets_bar")
                        ) {
                            items(wallets, key = { it.id }) { wallet ->
                                val wNet = realEntries
                                    .filter { it.walletName.equals(wallet.name, ignoreCase = true) }
                                    .sumOf {
                                        val conv = convertFinanceMinorUnits(it.totalAmountMinor, it.currencyCode, activeCurrency.code, exchangeRates)
                                        if (it.type == FinanceEntryType.INCOME.id) conv else -conv
                                    }
                                val wInit = convertFinanceMinorUnits(wallet.initialBalanceMinor, wallet.currencyCode, activeCurrency.code, exchangeRates)
                                val wBal = wInit + wNet
                                val dotColor = Color(wallet.colorHex)

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { showWalletsDialog = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(dotColor, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "${wallet.name}: ",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = formatFinanceAmount(wBal, activeCurrency, hideValues),
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = dotColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Segmented Pill Toggle: [ ↘ DESPESAS ] [ ↗ RENDAS ]
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .tutorialTarget("finances_type_toggle")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FinanceEntryType.entries.forEach { type ->
                        val selected = selectedType == type
                        val pillBg = when {
                            selected && type == FinanceEntryType.EXPENSE -> MaterialTheme.colorScheme.primary
                            selected && type == FinanceEntryType.INCOME -> GreenAccent
                            else -> Color.Transparent
                        }
                        val pillContent = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = pillBg,
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { selectedType = type }
                                .testTag("finance_type_${type.id.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (type == FinanceEntryType.EXPENSE) Icons.Default.SouthEast else Icons.Default.NorthEast,
                                    contentDescription = null,
                                    tint = pillContent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (type == FinanceEntryType.EXPENSE) "DESPESAS" else "RENDAS",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp,
                                    color = pillContent
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Period Navigator & 5-Day Strip Card
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .tutorialTarget("finances_period_nav")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
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
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .clickable {
                                    if (periodMode == FinancePeriodMode.CUSTOM) {
                                        showCustomRangePicker = true
                                    } else {
                                        anchorMillis = shiftFinanceAnchor(anchorMillis, periodMode, -1)
                                    }
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = "Período anterior",
                                    modifier = Modifier.size(18.dp)
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
                                    if (periodMode == FinancePeriodMode.CUSTOM) {
                                        showCustomRangePicker = true
                                    }
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
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .clickable {
                                    if (periodMode == FinancePeriodMode.CUSTOM) {
                                        showCustomRangePicker = true
                                    } else {
                                        anchorMillis = shiftFinanceAnchor(anchorMillis, periodMode, 1)
                                    }
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Próximo período",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Period Mode Pills
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FinancePeriodMode.entries.forEach { mode ->
                            val selected = periodMode == mode
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (selected) GreenAccent else MaterialTheme.colorScheme.surfaceContainerLow,
                                border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        periodMode = mode
                                        if (mode == FinancePeriodMode.CUSTOM) {
                                            showCustomRangePicker = true
                                        }
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

                    // 5-Day Strip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        fiveDayStrip.forEach { dayItem ->
                            val cellBg = when {
                                dayItem.isSelected && isExpenseMode -> MaterialTheme.colorScheme.primary
                                dayItem.isSelected && !isExpenseMode -> GreenAccent.copy(alpha = 0.18f)
                                else -> MaterialTheme.colorScheme.surfaceContainerLow
                            }
                            val cellBorder = when {
                                dayItem.isSelected && isExpenseMode -> null
                                dayItem.isSelected && !isExpenseMode -> androidx.compose.foundation.BorderStroke(1.5.dp, GreenAccent)
                                else -> androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            }
                            val topTextColor = when {
                                dayItem.isSelected && isExpenseMode -> Color.White.copy(alpha = 0.9f)
                                dayItem.isSelected && !isExpenseMode -> GreenAccent
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            val valTextColor = when {
                                dayItem.isSelected && isExpenseMode -> Color.White
                                dayItem.isSelected && !isExpenseMode -> GreenAccent
                                dayItem.totalMinor > 0L -> MaterialTheme.colorScheme.onSurface
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = cellBg,
                                border = cellBorder,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { anchorMillis = dayItem.dayMillis }
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = dayItem.label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = topTextColor,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = formatFinanceAmount(dayItem.totalMinor, activeCurrency, hideValues),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = valTextColor,
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

        // 4. Distribution Card (Donut Chart + Legend, plus 2-col Income Categories in Rendas mode)
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .tutorialTarget("finances_distribution_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (isExpenseMode) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Distribuição por Categoria",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Text(
                                    text = "${pieSlices.size} Categorias",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        if (pieSlices.isEmpty()) {
                            EmptyFinanceChartPlaceholder(isExpenseMode = true)
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
                                        pieSlices.forEach { slice ->
                                            val sweep = (slice.fraction * 360f).coerceAtLeast(3f)
                                            drawArc(
                                                color = slice.color,
                                                startAngle = startAngle,
                                                sweepAngle = sweep,
                                                useCenter = false,
                                                topLeft = topLeft,
                                                size = Size(diameter, diameter),
                                                style = Stroke(width = strokeWidth)
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
                                            text = formatFinanceAmount(periodTotalMinor, activeCurrency, hideValues),
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                pieSlices.take(6).forEach { slice ->
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
                                            text = "$pct%",
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = slice.color
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // RENDAS MODE
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Distribuição de Renda",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Consolidado de $periodTitle",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = GreenAccent.copy(alpha = 0.14f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, GreenAccent.copy(alpha = 0.35f))
                            ) {
                                Text(
                                    text = "+${formatFinanceAmount(periodTotalMinor, activeCurrency, hideValues)}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = GreenAccent,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }

                        if (pieSlices.isEmpty()) {
                            EmptyFinanceChartPlaceholder(isExpenseMode = false)
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.size(126.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val strokeWidth = 18.dp.toPx()
                                        val diameter = size.minDimension - strokeWidth
                                        val topLeft = Offset(
                                            (size.width - diameter) / 2f,
                                            (size.height - diameter) / 2f
                                        )
                                        var startAngle = -90f
                                        pieSlices.forEach { slice ->
                                            val sweep = (slice.fraction * 360f).coerceAtLeast(3f)
                                            drawArc(
                                                color = slice.color,
                                                startAngle = startAngle,
                                                sweepAngle = sweep,
                                                useCenter = false,
                                                topLeft = topLeft,
                                                size = Size(diameter, diameter),
                                                style = Stroke(width = strokeWidth)
                                            )
                                            startAngle += sweep
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "Total",
                                            fontSize = 10.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "+${formatFinanceAmount(periodTotalMinor, activeCurrency, hideValues)}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = GreenAccent,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    pieSlices.take(4).forEach { slice ->
                                        val pct = kotlin.math.round(slice.fraction * 100f).toInt()
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(9.dp)
                                                        .background(slice.color, CircleShape)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = slice.categoryName,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = formatFinanceAmount(slice.amountMinor, activeCurrency, hideValues),
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = slice.color
                                                    )
                                                }
                                                Text(
                                                    text = "$pct%",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = slice.color
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 2-Column Grid: Categorias de Renda
                        if (incomeCategoriesWithTotals.isNotEmpty()) {
                            Text(
                                text = "Categorias de Renda",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                incomeCategoriesWithTotals.chunked(2).forEach { rowCats ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        rowCats.forEach { (cat, sumMinor) ->
                                            val catColor = Color(cat.colorHex)
                                            val hasVal = sumMinor > 0L
                                            Surface(
                                                shape = RoundedCornerShape(14.dp),
                                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = FinanceCategoryIcons.iconForName(cat.iconName),
                                                        contentDescription = null,
                                                        tint = if (hasVal) catColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = cat.name,
                                                        fontSize = 11.5.sp,
                                                        fontWeight = if (hasVal) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (hasVal) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = formatFinanceAmount(sumMinor, activeCurrency, hideValues),
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = if (hasVal) catColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                        }
                                        if (rowCats.size == 1) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Primary "+ Adicionar despesa / renda" CTA Button
        item {
            Button(
                onClick = {
                    editingEntry = null
                    showEntryEditor = true
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = modeAccentColor,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("add_finance_entry_btn")
                    .tutorialTarget("finances_add_button")
            ) {
                Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isExpenseMode) "Adicionar despesa" else "Adicionar renda",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.5.sp
                )
            }
        }

        // 6. Entries Section Header + List
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
                    .tutorialTarget("finances_list_area"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isExpenseMode) "Despesas do período" else "Lançamentos de Renda",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isExpenseMode) MaterialTheme.colorScheme.surface else GreenAccent.copy(alpha = 0.16f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isExpenseMode) MaterialTheme.colorScheme.outlineVariant else GreenAccent.copy(alpha = 0.35f)
                    )
                ) {
                    Text(
                        text = if (isExpenseMode) "${periodEntries.size} itens" else "${periodEntries.size}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isExpenseMode) MaterialTheme.colorScheme.onSurfaceVariant else GreenAccent,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }
        }

        items(periodEntries, key = { it.id }) { entry ->
            val category = categoryMap[entry.categoryId]
            val convertedAmt = convertFinanceMinorUnits(entry.totalAmountMinor, entry.currencyCode, activeCurrency.code, exchangeRates)
            val pct = if (periodTotalMinor > 0L) {
                kotlin.math.round((convertedAmt.toDouble() / periodTotalMinor.toDouble()) * 100.0).toInt()
            } else {
                0
            }
            FinanceEntryRowCard(
                entry = entry,
                category = category,
                percentage = pct,
                hideValues = hideValues,
                onEdit = {
                    editingEntry = entry
                    showEntryEditor = true
                },
                onDuplicate = { viewModel.duplicateFinanceEntry(entry) },
                onDelete = { viewModel.deleteFinanceEntry(entry) }
            )
        }

        // 7. Planeados / Lista de Compras (Planeado vs. Real)
        if (plannedEntries.isNotEmpty()) {
            item {
                Text(
                    text = "Planeados / Lista de Compras (${plannedEntries.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            items(plannedEntries, key = { "planned_${it.id}" }) { pEntry ->
                val curr = FinanceCurrency.fromCode(pEntry.currencyCode)
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable {
                            editingEntry = pEntry
                            showEntryEditor = true
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(pEntry.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                "Previsto: ${formatFinanceAmount(pEntry.totalAmountMinor, curr, hideValues)}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalButton(
                            onClick = { completingPlannedEntry = pEntry },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pago", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 8. Bottom Linked Note Card
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { showLinkCreatorDialog = true }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isExpenseMode) "Vincular este período a uma nota" else "Relatório Vinculado",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isExpenseMode) "Sincronize com seu caderno de compras ou..." else "Modelo: 'Rendas do Mês' • Automático",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Vincular a nota →",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }


    // Custom Date Range Picker Dialog
    if (showCustomRangePicker) {
        val rangeState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = customStartMillis,
            initialSelectedEndDateMillis = customEndMillis
        )
        DatePickerDialog(
            onDismissRequest = { showCustomRangePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        rangeState.selectedStartDateMillis?.let { customStartMillis = it }
                        rangeState.selectedEndDateMillis?.let { customEndMillis = it }
                        showCustomRangePicker = false
                    }
                ) {
                    Text("Aplicar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomRangePicker = false }) {
                    Text("Cancelar")
                }
            }
        ) {
            DateRangePicker(
                state = rangeState,
                modifier = Modifier.height(420.dp)
            )
        }
    }

    // Add / Edit Entry Dialog
    if (showEntryEditor) {
        FinanceEntryEditorDialog(
            existingEntry = editingEntry,
            initialType = selectedType,
            defaultCurrencyCode = defaultCurrencyCode,
            categories = categories,
            allCategories = allCategories,
            wallets = wallets,
            onDismiss = {
                showEntryEditor = false
                editingEntry = null
            },
            onSaveEntry = { entry, isNew ->
                viewModel.saveFinanceEntry(entry, isNew)
                viewModel.checkBudgetAlerts()
            },
            onSaveCategory = { cat ->
                viewModel.saveFinanceCategory(cat)
            },
            onArchiveCategory = { cat, archived ->
                viewModel.archiveFinanceCategory(cat, archived)
            }
        )
    }

    if (completingPlannedEntry != null) {
        val target = completingPlannedEntry!!
        val curr = FinanceCurrency.fromCode(target.currencyCode)
        var actualText by remember(target.id) { mutableStateOf(formatMinorUnitsForInput(target.totalAmountMinor)) }
        AlertDialog(
            onDismissRequest = { completingPlannedEntry = null },
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GreenAccent) },
            title = { Text("Confirmar pagamento", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${target.name} · Previsto: ${formatFinanceAmount(target.totalAmountMinor, curr)}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = actualText,
                        onValueChange = { actualText = it },
                        label = { Text("Valor real pago (${curr.symbol})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val actual = parseDecimalToMinorUnits(actualText) ?: target.totalAmountMinor
                        viewModel.completePlannedEntry(target, actual)
                        completingPlannedEntry = null
                    }
                ) {
                    Text("Confirmar")
                }
            },
            dismissButton = {
                TextButton(onClick = { completingPlannedEntry = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showWalletsDialog) {
        FinanceWalletsDialog(
            wallets = wallets,
            entries = entries,
            defaultCurrency = activeCurrency,
            hideValues = hideValues,
            onDismiss = { showWalletsDialog = false },
            onSaveWallet = { viewModel.saveFinanceWallet(it) },
            onDeleteWallet = { viewModel.deleteFinanceWallet(it) },
            onTransfer = { from, to, amt, curr -> viewModel.transferBetweenWallets(from, to, amt, curr) }
        )
    }

    if (showBudgetsDialog) {
        val monthExpensesByCat = remember(realEntries, activeCurrency, exchangeRates) {
            val cal = Calendar.getInstance()
            cal.set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), 1, 0, 0, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val start = cal.timeInMillis
            realEntries.filter { it.type == FinanceEntryType.EXPENSE.id && it.dateMillis >= start }
                .groupBy { it.categoryId }
                .mapValues { (_, list) ->
                    list.sumOf { convertFinanceMinorUnits(it.totalAmountMinor, it.currencyCode, activeCurrency.code, exchangeRates) }
                }
        }
        FinanceBudgetsDialog(
            budgets = budgets,
            categories = allCategories,
            monthExpensesByCategory = monthExpensesByCat,
            defaultCurrency = activeCurrency,
            hideValues = hideValues,
            onDismiss = { showBudgetsDialog = false },
            onSaveBudget = { catId, limit, curr -> viewModel.saveFinanceBudget(catId, limit, curr) },
            onDeleteBudget = { viewModel.deleteFinanceBudget(it) }
        )
    }

    if (showRecurringDialog) {
        FinanceRecurringDialog(
            rules = recurringRules,
            categories = allCategories,
            wallets = wallets,
            defaultCurrency = activeCurrency,
            hideValues = hideValues,
            onDismiss = { showRecurringDialog = false },
            onSaveRule = { viewModel.saveFinanceRecurringRule(it) },
            onDeleteRule = { viewModel.deleteFinanceRecurringRule(it) }
        )
    }

    if (showExchangeRatesDialog) {
        FinanceExchangeRatesDialog(
            rates = exchangeRates,
            defaultCurrency = activeCurrency,
            onDismiss = { showExchangeRatesDialog = false },
            onSaveRate = { from, to, rate -> viewModel.saveFinanceExchangeRate(from, to, rate) },
            onDeleteRate = { viewModel.deleteFinanceExchangeRate(it) }
        )
    }

    if (showLinkCreatorDialog) {
        val allNotes by viewModel.notes.collectAsState()
        FinanceNoteLinkCreatorDialog(
            notes = allNotes,
            categories = allCategories,
            entries = entries,
            currentBalanceMinor = currentBalanceMinor,
            defaultCurrency = activeCurrency,
            preselectedNote = null,
            onDismiss = { showLinkCreatorDialog = false },
            onConfirmCreateLink = { link, createNewNote, newNoteTitle, template ->
                viewModel.createAndRunFinanceNoteLink(link, createNewNote, newNoteTitle, template)
                showLinkCreatorDialog = false
            }
        )
    }

    if (showLinksManagerDialog) {
        val allNotes by viewModel.notes.collectAsState()
        val links by viewModel.financeNoteLinks.collectAsState()
        FinanceNoteLinksManagerDialog(
            links = links,
            notes = allNotes,
            onDismiss = { showLinksManagerDialog = false },
            onCreateNewLink = {
                showLinksManagerDialog = false
                showLinkCreatorDialog = true
            },
            onRunNow = { viewModel.runFinanceLinkNow(it) },
            onUndoLast = { viewModel.undoFinanceLinkLastExecution(it) },
            onTogglePause = { viewModel.toggleFinanceLinkPause(it) },
            onRecreateMarkers = { viewModel.recreateMarkersForFinanceLink(it) },
            onRecreateNote = { viewModel.recreateNoteForFinanceLink(it) },
            onDeleteLink = { viewModel.deleteFinanceNoteLink(it) }
        )
    }
}

@Composable
private fun FinanceEntryRowCard(
    entry: FinanceEntry,
    category: FinanceCategory?,
    percentage: Int,
    hideValues: Boolean,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val catColor = Color(category?.colorHex ?: 0xFFFF8B1F)
    val entryCurrency = remember(entry.currencyCode) { FinanceCurrency.fromCode(entry.currencyCode) }

    val subtitleText = remember(entry) {
        buildString {
            if (entry.unitPriceMinor != null) {
                val qtyStr = if (entry.quantity % 1.0 == 0.0) {
                    entry.quantity.toInt().toString()
                } else {
                    entry.quantity.toString().replace(".", ",")
                }
                val unitPart = if (entry.unit.isNotBlank()) " ${entry.unit}" else ""
                append("$qtyStr$unitPart × ${formatFinanceAmount(entry.unitPriceMinor, entryCurrency, hideValues)}")
            }
            if (entry.comment.isNotBlank()) {
                if (isNotEmpty()) append(" · ")
                append(entry.comment)
            }
        }
    }

    val isIncome = entry.type == FinanceEntryType.INCOME.id
    val dateBadge = remember(entry.dateMillis) {
        SimpleDateFormat("dd MMM", Locale("pt", "AO")).format(Date(entry.dateMillis))
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onEdit() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
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
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isIncome) GreenAccent.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainer,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isIncome) GreenAccent.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Text(
                            text = "$percentage%",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isIncome) GreenAccent else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
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
                } else if (category != null) {
                    Text(
                        text = category.name,
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
                            color = if (isIncome) GreenAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (isIncome) {
                        "+${formatFinanceAmount(entry.totalAmountMinor, entryCurrency, hideValues)}"
                    } else {
                        formatFinanceAmount(entry.totalAmountMinor, entryCurrency, hideValues)
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isIncome) GreenAccent else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = dateBadge,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(32.dp)
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
                    onDismissRequest = { menuExpanded = false }
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

@Composable
private fun EmptyFinanceChartPlaceholder(isExpenseMode: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 22.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
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
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Sem ${if (isExpenseMode) "despesas" else "rendas"} neste período",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Adicione um lançamento abaixo para ver a distribuição por categoria",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

private data class FinanceDayStripItem(
    val dayMillis: Long,
    val label: String,
    val totalMinor: Long,
    val isSelected: Boolean
)

private data class FinancePieSlice(
    val categoryName: String,
    val color: Color,
    val amountMinor: Long,
    val fraction: Float
)

private fun computeFinancePeriodRange(
    mode: FinancePeriodMode,
    anchorMillis: Long,
    customStartMillis: Long,
    customEndMillis: Long
): Triple<Long, Long, String> {
    val cal = Calendar.getInstance().apply { timeInMillis = anchorMillis }
    val locale = Locale("pt", "AO")
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

private fun shiftFinanceAnchor(
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
