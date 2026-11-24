package com.example.ui.screens.finances

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FinanceCurrency
import com.example.data.model.FinanceEntry
import com.example.data.model.FinanceEntryType
import com.example.data.model.convertFinanceMinorUnits
import com.example.data.model.formatFinanceAmount
import com.example.data.model.formatMinorUnitsForInput
import com.example.data.model.parseDecimalToMinorUnits
import com.example.ui.MainViewModel
import com.example.ui.theme.GreenAccent
import com.example.ui.tutorial.CitraTutorialArea
import com.example.ui.tutorial.tutorialTarget
import com.example.util.squashBounceClickable
import java.util.Calendar

private data class FinanceSubTabItem(
    val title: String,
    val icon: ImageVector,
    val testTag: String,
    val tutorialKey: String? = null
)

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
    val financeNoteLinks by viewModel.financeNoteLinks.collectAsState()
    val activeTutorial by viewModel.activeTutorial.collectAsState()

    val realEntries = remember(entries) { entries.filter { !it.isPlanned } }
    val plannedEntries = remember(entries) { entries.filter { it.isPlanned } }
    val activeRecurringCount = remember(recurringRules) { recurringRules.count { it.isActive } }

    val currentBalanceMinor = remember(baseBalanceMinor, realEntries, activeCurrency, exchangeRates) {
        val net = realEntries.sumOf {
            val converted = convertFinanceMinorUnits(
                it.totalAmountMinor,
                it.currencyCode,
                activeCurrency.code,
                exchangeRates
            )
            if (it.type == FinanceEntryType.INCOME.id) converted else -converted
        }
        baseBalanceMinor + net
    }

    val categoryMap = remember(allCategories) {
        allCategories.associateBy { it.id }
    }

    var selectedSubTab by rememberSaveable { mutableIntStateOf(0) }

    var periodMode by remember { mutableStateOf(FinancePeriodMode.MONTH) }
    var anchorMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var customStartMillis by remember { mutableLongStateOf(System.currentTimeMillis() - 7L * 86_400_000L) }
    var customEndMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showCustomRangePicker by remember { mutableStateOf(false) }

    var showEntryEditor by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<FinanceEntry?>(null) }
    var editorInitialType by remember { mutableStateOf(FinanceEntryType.EXPENSE) }
    var defaultEditorIsPlanned by remember { mutableStateOf(false) }

    var currencyMenuExpanded by remember { mutableStateOf(false) }
    var showPlannedListDialog by remember { mutableStateOf(false) }
    var showLinkCreatorDialog by remember { mutableStateOf(false) }
    var showLinksManagerDialog by remember { mutableStateOf(false) }
    var showWalletsDialog by remember { mutableStateOf(false) }
    var showBudgetsDialog by remember { mutableStateOf(false) }
    var showRecurringDialog by remember { mutableStateOf(false) }
    var showCalculatorDialog by remember { mutableStateOf(false) }
    var completingPlannedEntry by remember { mutableStateOf<FinanceEntry?>(null) }

    val (periodStartMillis, periodEndMillis, periodTitle) = remember(
        periodMode,
        anchorMillis,
        customStartMillis,
        customEndMillis
    ) {
        computeFinancePeriodRange(periodMode, anchorMillis, customStartMillis, customEndMillis)
    }

    val summaryListState = rememberLazyListState()

    LaunchedEffect(activeTutorial?.area, activeTutorial?.stepIndex) {
        if (activeTutorial?.area == CitraTutorialArea.FINANCES) {
            selectedSubTab = 0
            summaryListState.animateScrollToItem(0)
        }
    }

    val subTabs = remember {
        listOf(
            FinanceSubTabItem(
                title = "Resumo",
                icon = Icons.Default.PieChart,
                testTag = "finances_tab_summary"
            ),
            FinanceSubTabItem(
                title = "Movimentos",
                icon = Icons.Default.ReceiptLong,
                testTag = "finances_tab_movements"
            ),
            FinanceSubTabItem(
                title = "Ferramentas",
                icon = Icons.Default.Tune,
                testTag = "finances_tab_tools",
                tutorialKey = "finances_tools_tab"
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("finances_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Sliding-pill Segmented Bar (consistent with OrganizationScreen)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp)
                    ) {
                        val totalW = maxWidth
                        val tabW = totalW / subTabs.size
                        val indicatorOffset by animateDpAsState(
                            targetValue = tabW * selectedSubTab,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "financeTabIndicatorSlide"
                        )

                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 2.5.dp,
                            modifier = Modifier
                                .offset(x = indicatorOffset)
                                .width(tabW)
                                .height(42.dp)
                        ) {}

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            subTabs.forEachIndexed { index, tabItem ->
                                val isSelected = selectedSubTab == index
                                val contentColor by animateColorAsState(
                                    targetValue = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    animationSpec = tween(200),
                                    label = "financeTabColor_$index"
                                )

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .then(
                                            if (tabItem.tutorialKey != null) {
                                                Modifier.tutorialTarget(tabItem.tutorialKey)
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .clip(CircleShape)
                                        .squashBounceClickable { selectedSubTab = index }
                                        .testTag(tabItem.testTag),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = tabItem.icon,
                                            contentDescription = null,
                                            tint = contentColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = tabItem.title,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                            color = contentColor,
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

            Box(modifier = Modifier.weight(1f)) {
                Crossfade(
                    targetState = selectedSubTab,
                    label = "financeSubTabContent"
                ) { tabIndex ->
                    when (tabIndex) {
                        0 -> FinanceSummaryTab(
                            listState = summaryListState,
                            currentBalanceMinor = currentBalanceMinor,
                            activeCurrency = activeCurrency,
                            hideValues = hideValues,
                            realEntries = realEntries,
                            categoryMap = categoryMap,
                            exchangeRates = exchangeRates,
                            periodMode = periodMode,
                            periodStartMillis = periodStartMillis,
                            periodEndMillis = periodEndMillis,
                            periodTitle = periodTitle,
                            onSelectPeriodMode = { periodMode = it },
                            onShiftPeriod = { delta ->
                                anchorMillis = shiftFinanceAnchor(anchorMillis, periodMode, delta)
                            },
                            onOpenCustomRangePicker = { showCustomRangePicker = true },
                            onOpenCurrencyPicker = { currencyMenuExpanded = true },
                            onToggleHideValues = { viewModel.setFinanceHideValues(!hideValues) },
                            onOpenFinanceSettings = onOpenFinanceSettings,
                            onOpenNewEntry = { type ->
                                editingEntry = null
                                editorInitialType = type
                                defaultEditorIsPlanned = false
                                showEntryEditor = true
                            }
                        )

                        1 -> FinanceMovementsTab(
                            realEntries = realEntries,
                            categoryMap = categoryMap,
                            hideValues = hideValues,
                            periodMode = periodMode,
                            periodStartMillis = periodStartMillis,
                            periodEndMillis = periodEndMillis,
                            periodTitle = periodTitle,
                            onSelectPeriodMode = { periodMode = it },
                            onShiftPeriod = { delta ->
                                anchorMillis = shiftFinanceAnchor(anchorMillis, periodMode, delta)
                            },
                            onOpenCustomRangePicker = { showCustomRangePicker = true },
                            onEditEntry = { entry ->
                                editingEntry = entry
                                editorInitialType = FinanceEntryType.fromId(entry.type)
                                defaultEditorIsPlanned = entry.isPlanned
                                showEntryEditor = true
                            },
                            onDuplicateEntry = { viewModel.duplicateFinanceEntry(it) },
                            onDeleteEntry = { viewModel.deleteFinanceEntry(it) }
                        )

                        else -> FinanceToolsTab(
                            walletsCount = wallets.size,
                            plannedCount = plannedEntries.size,
                            budgetsCount = budgets.size,
                            activeRecurringCount = activeRecurringCount,
                            noteLinksCount = financeNoteLinks.size,
                            onOpenWallets = { showWalletsDialog = true },
                            onOpenPlannedList = { showPlannedListDialog = true },
                            onOpenBudgets = { showBudgetsDialog = true },
                            onOpenRecurring = { showRecurringDialog = true },
                            onOpenNoteLinksManager = {
                                if (financeNoteLinks.isEmpty()) {
                                    showLinkCreatorDialog = true
                                } else {
                                    showLinksManagerDialog = true
                                }
                            },
                            onOpenCalculator = { showCalculatorDialog = true },
                            onOpenFinanceSettings = onOpenFinanceSettings
                        )
                    }
                }
            }
        }

        // Persistent Quick Entry FAB on Resumo & Movimentos
        AnimatedVisibility(
            visible = selectedSubTab != 2,
            enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.85f),
            exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.85f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            ExtendedFloatingActionButton(
                onClick = {
                    editingEntry = null
                    editorInitialType = FinanceEntryType.EXPENSE
                    defaultEditorIsPlanned = false
                    showEntryEditor = true
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null
                    )
                },
                text = {
                    Text(
                        text = "Novo lançamento",
                        fontWeight = FontWeight.Bold
                    )
                },
                shape = RoundedCornerShape(18.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier
                    .tutorialTarget("finances_add_button")
                    .testTag("add_finance_entry_btn")
            )
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
            initialType = editorInitialType,
            defaultCurrencyCode = defaultCurrencyCode,
            categories = categories,
            allCategories = allCategories,
            wallets = wallets,
            initialIsPlanned = defaultEditorIsPlanned,
            initialDateMillis = anchorMillis,
            onDismiss = {
                showEntryEditor = false
                editingEntry = null
                defaultEditorIsPlanned = false
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

    // Planned & Shopping List Dialog (from Ferramentas)
    if (showPlannedListDialog) {
        FinancePlannedListDialog(
            plannedEntries = plannedEntries,
            hideValues = hideValues,
            onDismiss = { showPlannedListDialog = false },
            onAddPlannedItem = {
                showPlannedListDialog = false
                editingEntry = null
                editorInitialType = FinanceEntryType.EXPENSE
                defaultEditorIsPlanned = true
                showEntryEditor = true
            },
            onEditEntry = { entry ->
                showPlannedListDialog = false
                editingEntry = entry
                editorInitialType = FinanceEntryType.fromId(entry.type)
                defaultEditorIsPlanned = true
                showEntryEditor = true
            },
            onMarkAsPaid = { entry ->
                showPlannedListDialog = false
                completingPlannedEntry = entry
            }
        )
    }

    // Confirm Planned Item Payment Dialog
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
            realEntries
                .filter { it.type == FinanceEntryType.EXPENSE.id && it.dateMillis >= start }
                .groupBy { it.categoryId }
                .mapValues { (_, list) ->
                    list.sumOf {
                        convertFinanceMinorUnits(
                            it.totalAmountMinor,
                            it.currencyCode,
                            activeCurrency.code,
                            exchangeRates
                        )
                    }
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

    if (currencyMenuExpanded) {
        SearchableCurrencyPickerDialog(
            selectedCurrency = activeCurrency,
            onDismiss = { currencyMenuExpanded = false },
            onSelectCurrency = { curr ->
                viewModel.setFinanceDefaultCurrency(curr.code)
                currencyMenuExpanded = false
            }
        )
    }

    if (showCalculatorDialog) {
        FinanceCalculatorDialog(
            onDismiss = { showCalculatorDialog = false }
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
        FinanceNoteLinksManagerDialog(
            links = financeNoteLinks,
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
