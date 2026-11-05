package com.example.ui.screens.finances

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.*
import com.example.ui.theme.GreenAccent
import com.example.ui.theme.RedAction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FinanceWalletsDialog(
    wallets: List<FinanceWallet>,
    entries: List<FinanceEntry>,
    defaultCurrency: FinanceCurrency,
    hideValues: Boolean,
    onDismiss: () -> Unit,
    onSaveWallet: (FinanceWallet) -> Unit,
    onDeleteWallet: (FinanceWallet) -> Unit,
    onTransfer: (fromWallet: String, toWallet: String, amountMinor: Long, currencyCode: String) -> Unit
) {
    var showAddWallet by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newType by remember { mutableStateOf(FinanceWalletType.CASH) }
    var newInitialText by remember { mutableStateOf("0") }

    var showTransfer by remember { mutableStateOf(false) }
    var fromWallet by remember { mutableStateOf(wallets.firstOrNull()?.name ?: "Carteira Principal") }
    var toWallet by remember { mutableStateOf(wallets.getOrNull(1)?.name ?: wallets.firstOrNull()?.name ?: "") }
    var transferAmountText by remember { mutableStateOf("") }

    fun walletBalance(w: FinanceWallet): Long {
        val net = entries.filter { !it.isPlanned && it.walletName.equals(w.name, ignoreCase = true) }
            .sumOf { if (it.type == FinanceEntryType.INCOME.id) it.totalAmountMinor else -it.totalAmountMinor }
        return w.initialBalanceMinor + net
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Carteiras e Contas", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }
                HorizontalDivider()

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    showAddWallet = !showAddWallet
                                    showTransfer = false
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Nova carteira", fontSize = 12.5.sp)
                            }
                            FilledTonalButton(
                                onClick = {
                                    showTransfer = !showTransfer
                                    showAddWallet = false
                                },
                                enabled = wallets.size >= 2,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Transferir", fontSize = 12.5.sp)
                            }
                        }
                    }

                    if (showAddWallet) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text("Adicionar Carteira", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    OutlinedTextField(
                                        value = newName,
                                        onValueChange = { newName = it },
                                        label = { Text("Nome (ex.: Conta BAI, Multicaixa)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        FinanceWalletType.entries.forEach { wt ->
                                            FilterChip(
                                                selected = newType == wt,
                                                onClick = { newType = wt },
                                                label = { Text(wt.label, fontSize = 11.5.sp) }
                                            )
                                        }
                                    }
                                    OutlinedTextField(
                                        value = newInitialText,
                                        onValueChange = { newInitialText = it },
                                        label = { Text("Saldo inicial (${defaultCurrency.symbol})") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    Button(
                                        onClick = {
                                            val trimmed = newName.trim()
                                            if (trimmed.isNotEmpty()) {
                                                val initMinor = parseDecimalToMinorUnits(newInitialText) ?: 0L
                                                onSaveWallet(
                                                    FinanceWallet(
                                                        name = trimmed,
                                                        type = newType.id,
                                                        currencyCode = defaultCurrency.code,
                                                        initialBalanceMinor = initMinor
                                                    )
                                                )
                                                newName = ""
                                                newInitialText = "0"
                                                showAddWallet = false
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Guardar carteira")
                                    }
                                }
                            }
                        }
                    }

                    if (showTransfer && wallets.size >= 2) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text("Transferência entre carteiras", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("De:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        wallets.forEach { w ->
                                            FilterChip(
                                                selected = fromWallet == w.name,
                                                onClick = { fromWallet = w.name },
                                                label = { Text(w.name, fontSize = 12.sp) }
                                            )
                                        }
                                    }
                                    Text("Para:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        wallets.forEach { w ->
                                            FilterChip(
                                                selected = toWallet == w.name,
                                                onClick = { toWallet = w.name },
                                                label = { Text(w.name, fontSize = 12.sp) }
                                            )
                                        }
                                    }
                                    OutlinedTextField(
                                        value = transferAmountText,
                                        onValueChange = { transferAmountText = it },
                                        label = { Text("Valor (${defaultCurrency.symbol})") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    Button(
                                        onClick = {
                                            val amt = parseDecimalToMinorUnits(transferAmountText) ?: 0L
                                            if (amt > 0L && fromWallet != toWallet) {
                                                onTransfer(fromWallet, toWallet, amt, defaultCurrency.code)
                                                transferAmountText = ""
                                                showTransfer = false
                                            }
                                        },
                                        enabled = fromWallet != toWallet && (parseDecimalToMinorUnits(transferAmountText) ?: 0L) > 0L,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Confirmar transferência")
                                    }
                                }
                            }
                        }
                    }

                    items(wallets, key = { it.id }) { wallet ->
                        val bal = walletBalance(wallet)
                        val curr = FinanceCurrency.fromCode(wallet.currencyCode)
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(wallet.colorHex).copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = when (FinanceWalletType.fromId(wallet.type)) {
                                            FinanceWalletType.CASH -> Icons.Default.Payments
                                            FinanceWalletType.BANK -> Icons.Default.AccountBalance
                                            FinanceWalletType.MOBILE -> Icons.Default.PhoneAndroid
                                        },
                                        contentDescription = null,
                                        tint = Color(wallet.colorHex)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(wallet.name, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                                    Text(
                                        FinanceWalletType.fromId(wallet.type).label,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = formatFinanceAmount(bal, curr, hideValues),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.5.sp,
                                    color = if (!hideValues && bal < 0L) RedAction else MaterialTheme.colorScheme.onSurface
                                )
                                if (!wallet.isDefault) {
                                    IconButton(onClick = { onDeleteWallet(wallet) }) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Remover", tint = RedAction)
                                    }
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
fun FinanceBudgetsDialog(
    budgets: List<FinanceBudget>,
    categories: List<FinanceCategory>,
    monthExpensesByCategory: Map<String, Long>,
    defaultCurrency: FinanceCurrency,
    hideValues: Boolean,
    onDismiss: () -> Unit,
    onSaveBudget: (categoryId: String, limitMinor: Long, currencyCode: String) -> Unit,
    onDeleteBudget: (FinanceBudget) -> Unit
) {
    val expenseCategories = remember(categories) {
        categories.filter { it.type == FinanceEntryType.EXPENSE.id && !it.isArchived }
    }
    var selectedCatId by remember { mutableStateOf(expenseCategories.firstOrNull()?.id ?: "") }
    var limitText by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PieChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Orçamentos Mensais", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }
                HorizontalDivider()

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("Definir limite mensal", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    expenseCategories.forEach { cat ->
                                        FilterChip(
                                            selected = selectedCatId == cat.id,
                                            onClick = { selectedCatId = cat.id },
                                            label = { Text(cat.name, fontSize = 12.sp) }
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = limitText,
                                    onValueChange = { limitText = it },
                                    label = { Text("Limite mensal (${defaultCurrency.symbol})") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                Button(
                                    onClick = {
                                        val limit = parseDecimalToMinorUnits(limitText) ?: 0L
                                        if (selectedCatId.isNotEmpty() && limit > 0L) {
                                            onSaveBudget(selectedCatId, limit, defaultCurrency.code)
                                            limitText = ""
                                        }
                                    },
                                    enabled = selectedCatId.isNotEmpty() && (parseDecimalToMinorUnits(limitText) ?: 0L) > 0L,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Guardar orçamento")
                                }
                            }
                        }
                    }

                    items(budgets, key = { it.id }) { budget ->
                        val cat = categories.find { it.id == budget.categoryId }
                        val spent = monthExpensesByCategory[budget.categoryId] ?: 0L
                        val pct = if (budget.limitMinor > 0L) ((spent * 100L) / budget.limitMinor).toInt() else 0
                        val barColor = when {
                            pct >= 100 -> RedAction
                            pct >= 80 -> Color(0xFFFF9800)
                            else -> GreenAccent
                        }
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        cat?.name ?: "Categoria",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (pct >= 80) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = barColor.copy(alpha = 0.16f)
                                            ) {
                                                Text(
                                                    text = if (pct >= 100) "100%+ Limite!" else "$pct% (Alerta 80%)",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = barColor,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                        IconButton(onClick = { onDeleteBudget(budget) }) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Remover", tint = RedAction)
                                        }
                                    }
                                }
                                LinearProgressIndicator(
                                    progress = { (pct / 100f).coerceIn(0f, 1f) },
                                    color = barColor,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )
                                Text(
                                    text = "${formatFinanceAmount(spent, defaultCurrency, hideValues)} de ${formatFinanceAmount(budget.limitMinor, defaultCurrency, hideValues)} ($pct%)",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FinanceRecurringDialog(
    rules: List<FinanceRecurringRule>,
    categories: List<FinanceCategory>,
    wallets: List<FinanceWallet>,
    defaultCurrency: FinanceCurrency,
    hideValues: Boolean,
    onDismiss: () -> Unit,
    onSaveRule: (FinanceRecurringRule) -> Unit,
    onDeleteRule: (FinanceRecurringRule) -> Unit
) {
    var type by remember { mutableStateOf(FinanceEntryType.EXPENSE) }
    var name by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var frequency by remember { mutableStateOf(FinanceLinkFrequency.MONTHLY) }
    var reminderDays by remember { mutableStateOf("1") }
    val filteredCats = remember(categories, type) {
        categories.filter { it.type == type.id && !it.isArchived }
    }
    var selectedCatId by remember(type, filteredCats) {
        mutableStateOf(filteredCats.firstOrNull()?.id ?: "")
    }
    var selectedWallet by remember {
        mutableStateOf(wallets.firstOrNull()?.name ?: "Carteira Principal")
    }
    val dateFmt = remember { SimpleDateFormat("dd/MM/yyyy", Locale("pt", "AO")) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Repeat, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Lançamentos Recorrentes", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }
                HorizontalDivider()

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("Nova recorrência", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FinanceEntryType.entries.forEach { t ->
                                        FilterChip(
                                            selected = type == t,
                                            onClick = { type = t },
                                            label = { Text(t.label) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = name,
                                    onValueChange = { name = it },
                                    label = { Text("Nome (ex.: Salário, Renda, Internet)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                OutlinedTextField(
                                    value = amountText,
                                    onValueChange = { amountText = it },
                                    label = { Text("Valor (${defaultCurrency.symbol})") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    filteredCats.forEach { cat ->
                                        FilterChip(
                                            selected = selectedCatId == cat.id,
                                            onClick = { selectedCatId = cat.id },
                                            label = { Text(cat.name, fontSize = 12.sp) }
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(
                                        FinanceLinkFrequency.DAILY,
                                        FinanceLinkFrequency.WEEKLY,
                                        FinanceLinkFrequency.MONTHLY,
                                        FinanceLinkFrequency.ANNUAL
                                    ).forEach { f ->
                                        FilterChip(
                                            selected = frequency == f,
                                            onClick = { frequency = f },
                                            label = { Text(f.label, fontSize = 12.sp) }
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = reminderDays,
                                    onValueChange = { reminderDays = it },
                                    label = { Text("Lembrar quantos dias antes") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                Button(
                                    onClick = {
                                        val amt = parseDecimalToMinorUnits(amountText) ?: 0L
                                        if (name.isNotBlank() && amt > 0L) {
                                            val rem = reminderDays.toIntOrNull()?.coerceAtLeast(0) ?: 1
                                            onSaveRule(
                                                FinanceRecurringRule(
                                                    type = type.id,
                                                    name = name.trim(),
                                                    categoryId = selectedCatId,
                                                    walletName = selectedWallet,
                                                    currencyCode = defaultCurrency.code,
                                                    amountMinor = amt,
                                                    frequency = frequency.id,
                                                    reminderDaysBefore = rem,
                                                    nextDueMillis = System.currentTimeMillis()
                                                )
                                            )
                                            name = ""
                                            amountText = ""
                                        }
                                    },
                                    enabled = name.isNotBlank() && (parseDecimalToMinorUnits(amountText) ?: 0L) > 0L,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Guardar recorrência")
                                }
                            }
                        }
                    }

                    items(rules, key = { it.id }) { rule ->
                        val curr = FinanceCurrency.fromCode(rule.currencyCode)
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(rule.name, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                                    Text(
                                        "${FinanceLinkFrequency.fromId(rule.frequency).label} · Próxima: ${dateFmt.format(Date(rule.nextDueMillis))}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = formatFinanceAmount(rule.amountMinor, curr, hideValues),
                                    fontWeight = FontWeight.Bold,
                                    color = if (rule.type == FinanceEntryType.INCOME.id) GreenAccent else RedAction
                                )
                                IconButton(onClick = { onDeleteRule(rule) }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Remover", tint = RedAction)
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
fun FinanceExchangeRatesDialog(
    rates: List<FinanceExchangeRate>,
    defaultCurrency: FinanceCurrency,
    onDismiss: () -> Unit,
    onSaveRate: (fromCurrency: String, toCurrency: String, rate: Double) -> Unit,
    onDeleteRate: (FinanceExchangeRate) -> Unit
) {
    var fromCurr by remember {
        mutableStateOf(FinanceCurrency.entries.firstOrNull { it != defaultCurrency } ?: FinanceCurrency.USD)
    }
    var rateText by remember { mutableStateOf("") }
    val dateFmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "AO")) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CurrencyExchange, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Câmbio Manual e Histórico", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }
                HorizontalDivider()

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    "Definir taxa para ${defaultCurrency.code} (${defaultCurrency.symbol})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FinanceCurrency.entries.filter { it != defaultCurrency }.forEach { c ->
                                        FilterChip(
                                            selected = fromCurr == c,
                                            onClick = { fromCurr = c },
                                            label = { Text("${c.code} (${c.symbol})") }
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = rateText,
                                    onValueChange = { rateText = it },
                                    label = { Text("1 ${fromCurr.code} = ? ${defaultCurrency.code}") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                Button(
                                    onClick = {
                                        val parsed = rateText.trim().replace(",", ".").toDoubleOrNull()
                                        if (parsed != null && parsed > 0.0) {
                                            onSaveRate(fromCurr.code, defaultCurrency.code, parsed)
                                            rateText = ""
                                        }
                                    },
                                    enabled = (rateText.trim().replace(",", ".").toDoubleOrNull() ?: 0.0) > 0.0,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Guardar taxa de câmbio")
                                }
                            }
                        }
                    }

                    item {
                        Text("Histórico de taxas", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    items(rates, key = { it.id }) { r ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "1 ${r.fromCurrency} = ${r.rate} ${r.toCurrency}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        dateFmt.format(Date(r.recordedAt)),
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { onDeleteRate(r) }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Remover", tint = RedAction)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
