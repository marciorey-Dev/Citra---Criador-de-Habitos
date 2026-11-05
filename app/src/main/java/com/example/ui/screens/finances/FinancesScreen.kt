package com.example.ui.screens.finances

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
    val requiresLock = !pinCode.isNullOrBlank() || biometricEnabled

    LaunchedEffect(Unit) {
        viewModel.ensureDefaultFinanceCategories()
    }

    if (requiresLock && !isUnlocked) {
        FinanceLockOverlay(
            expectedPin = pinCode,
            biometricEnabled = biometricEnabled,
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

    var showEditBalanceDialog by remember { mutableStateOf(false) }
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("finances_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Top Balance & Currency Card
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showEditBalanceDialog = true }
                                .padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = "Saldo atual",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = formatFinanceAmount(currentBalanceMinor, activeCurrency, hideValues),
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (!hideValues && currentBalanceMinor < 0L) {
                                        RedAction
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Definir saldo atual",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { viewModel.setFinanceHideValues(!hideValues) }
                            ) {
                                Icon(
                                    imageVector = if (hideValues) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (hideValues) "Mostrar valores" else "Ocultar valores",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Currency selector (Kz first)
                            Box {
                                AssistChip(
                                    onClick = { currencyMenuExpanded = true },
                                    label = {
                                        Text(
                                            text = activeCurrency.symbol,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Default.ArrowDropDown,
                                            contentDescription = "Mudar moeda",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                )
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

                            // Overflow menu with upcoming visual actions
                            Box {
                                IconButton(onClick = { overflowMenuExpanded = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Mais opções")
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

                    Spacer(modifier = Modifier.height(12.dp))

                    // Type Selector: [ DESPESAS ] [ RENDAS ]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                RoundedCornerShape(14.dp)
                            )
                            .padding(4.dp)
                    ) {
                        FinanceEntryType.entries.forEach { type ->
                            val selected = selectedType == type
                            Button(
                                onClick = { selectedType = type },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                shape = RoundedCornerShape(10.dp),
                                elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("finance_type_${type.id.lowercase()}")
                            ) {
                                Text(
                                    text = if (type == FinanceEntryType.EXPENSE) "DESPESAS" else "RENDAS",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Compact Calendar & Period Selector
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = {
                                if (periodMode == FinancePeriodMode.CUSTOM) {
                                    showCustomRangePicker = true
                                } else {
                                    anchorMillis = shiftFinanceAnchor(anchorMillis, periodMode, -1)
                                }
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Período anterior")
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    if (periodMode == FinancePeriodMode.CUSTOM) {
                                        showCustomRangePicker = true
                                    }
                                }
                        ) {
                            Text(
                                text = periodTitle,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Total: ${formatFinanceAmount(periodTotalMinor, activeCurrency, hideValues)}",
                                fontSize = 12.sp,
                                color = if (selectedType == FinanceEntryType.EXPENSE) RedAction else GreenAccent,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        IconButton(
                            onClick = {
                                if (periodMode == FinancePeriodMode.CUSTOM) {
                                    showCustomRangePicker = true
                                } else {
                                    anchorMillis = shiftFinanceAnchor(anchorMillis, periodMode, 1)
                                }
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Próximo período")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(FinancePeriodMode.entries) { mode ->
                            val selected = periodMode == mode
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    periodMode = mode
                                    if (mode == FinancePeriodMode.CUSTOM) {
                                        showCustomRangePicker = true
                                    }
                                },
                                label = { Text(mode.label, fontSize = 12.sp) }
                            )
                        }
                    }
                }
            }
        }

        // 3. Pie Chart Card
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (pieSlices.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.PieChartOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Sem ${if (selectedType == FinanceEntryType.EXPENSE) "despesas" else "rendas"} neste período",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Adicione um lançamento abaixo para ver a distribuição por categoria",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Canvas(
                            modifier = Modifier.size(136.dp)
                        ) {
                            val strokeWidth = 28.dp.toPx()
                            val diameter = size.minDimension - strokeWidth
                            val topLeft = Offset(
                                (size.width - diameter) / 2f,
                                (size.height - diameter) / 2f
                            )
                            var startAngle = -90f
                            pieSlices.forEach { slice ->
                                val sweep = (slice.fraction * 360f).coerceAtLeast(2f)
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

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            pieSlices.take(5).forEach { slice ->
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
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = slice.categoryName,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "$pct%",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Primary "+ Adicionar despesa / renda" Button
        item {
            Button(
                onClick = {
                    editingEntry = null
                    showEntryEditor = true
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("add_finance_entry_btn")
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (selectedType == FinanceEntryType.EXPENSE) "Adicionar despesa" else "Adicionar renda",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }

        // 5. Entries List
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

        // 6. Planeados / Lista de Compras (Planeado vs. Real)
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
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
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
                            shape = RoundedCornerShape(10.dp),
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

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Edit Current Balance Dialog
    if (showEditBalanceDialog) {
        var balanceInput by remember {
            mutableStateOf(formatMinorUnitsForInput(currentBalanceMinor))
        }
        var balanceError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showEditBalanceDialog = false },
            icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Definir Saldo Atual", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Informe o valor que tem disponível agora. As próximas despesas subtraem e as rendas somam a este saldo.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = balanceInput,
                        onValueChange = {
                            balanceInput = it
                            balanceError = null
                        },
                        label = { Text("Saldo atual") },
                        suffix = { Text(activeCurrency.symbol, fontWeight = FontWeight.Bold) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (balanceError != null) {
                        Text(balanceError!!, color = RedAction, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = parseDecimalToMinorUnits(balanceInput)
                        if (parsed == null) {
                            balanceError = "Valor inválido"
                            return@Button
                        }
                        viewModel.setFinanceCurrentBalance(parsed)
                        showEditBalanceDialog = false
                    }
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditBalanceDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
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

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onEdit() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(catColor.copy(alpha = 0.16f), CircleShape),
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
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (entry.priority == FinancePriority.OPTIONAL.id) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "Opcional",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
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
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$percentage%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = formatFinanceAmount(entry.totalAmountMinor, entryCurrency, hideValues),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Opções do lançamento",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
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
