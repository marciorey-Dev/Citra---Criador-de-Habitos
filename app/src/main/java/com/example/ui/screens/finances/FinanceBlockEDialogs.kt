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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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

    var fromWallet by remember(wallets) { mutableStateOf(wallets.firstOrNull()?.name ?: "Carteira Principal") }
    var toWallet by remember(wallets) { mutableStateOf(wallets.getOrNull(1)?.name ?: wallets.firstOrNull()?.name ?: "") }
    var fromMenuExpanded by remember { mutableStateOf(false) }
    var toMenuExpanded by remember { mutableStateOf(false) }
    var transferAmountText by remember { mutableStateOf("") }

    fun walletBalance(w: FinanceWallet): Long {
        val net = entries.filter { !it.isPlanned && it.walletName.equals(w.name, ignoreCase = true) }
            .sumOf { if (it.type == FinanceEntryType.INCOME.id) it.totalAmountMinor else -it.totalAmountMinor }
        return w.initialBalanceMinor + net
    }

    val totalConsolidatedMinor = remember(wallets, entries) {
        wallets.sumOf { walletBalance(it) }
    }

    val recentMovements = remember(entries) {
        entries.filter { !it.isPlanned }.sortedByDescending { it.dateMillis }.take(5)
    }

    val todayLabel = remember {
        SimpleDateFormat("'Hoje,' dd MMM", Locale("pt", "AO")).format(Date())
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.background,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .clickable { onDismiss() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Voltar",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Carteiras & Contas",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = GreenAccent.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, GreenAccent.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CloudDone,
                                    contentDescription = null,
                                    tint = GreenAccent,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Salvo",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GreenAccent
                                )
                            }
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. PATRIMÔNIO TOTAL CONSOLIDADO Card
                    item {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "PATRIMÔNIO TOTAL\nCONSOLIDADO",
                                            fontSize = 11.sp,
                                            lineHeight = 14.sp,
                                            letterSpacing = 0.6.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainer,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.AccountBalanceWallet,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "${wallets.size} Carteiras",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = formatFinanceAmount(totalConsolidatedMinor, defaultCurrency, hideValues),
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = { showAddWallet = !showAddWallet },
                                        shape = RoundedCornerShape(22.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                    ) {
                                        Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(17.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Nova Carteira", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(22.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainer,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                            .clip(RoundedCornerShape(22.dp))
                                            .clickable(enabled = wallets.size >= 2) {
                                                val tmp = fromWallet
                                                fromWallet = toWallet
                                                toWallet = tmp
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxSize(),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.SwapHoriz,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Transferência",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Inline Add Wallet Form
                    if (showAddWallet) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text("Adicionar Carteira", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                                    OutlinedTextField(
                                        value = newName,
                                        onValueChange = { newName = it },
                                        label = { Text("Nome (ex.: Conta BAI, Multicaixa)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp)
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
                                        shape = RoundedCornerShape(14.dp)
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
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Text("Guardar carteira", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // 2. Minhas Carteiras Section Header
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Minhas Carteiras",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Text(
                                        text = "${wallets.size} Ativas",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Organizar",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Wallet Cards
                    items(wallets, key = { it.id }) { wallet ->
                        val bal = walletBalance(wallet)
                        val curr = FinanceCurrency.fromCode(wallet.currencyCode)
                        val walletColor = Color(wallet.colorHex)
                        val shareFraction = if (totalConsolidatedMinor > 0L) {
                            (bal.toFloat() / totalConsolidatedMinor.toFloat()).coerceIn(0f, 1f)
                        } else 0f
                        val sharePctText = String.format(Locale.US, "%.1f%%", shareFraction * 100f)

                        val statusLabel = when {
                            wallet.isDefault -> "• Padrão"
                            wallet.type == FinanceWalletType.CASH.id -> "Em Mãos"
                            else -> "⚑ Meta Ativa"
                        }
                        val statusColor = if (wallet.isDefault) GreenAccent else MaterialTheme.colorScheme.primary

                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .background(walletColor.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = when (FinanceWalletType.fromId(wallet.type)) {
                                                FinanceWalletType.CASH -> Icons.Default.Payments
                                                FinanceWalletType.BANK -> Icons.Default.AccountBalance
                                                FinanceWalletType.MOBILE -> Icons.Default.PhoneAndroid
                                            },
                                            contentDescription = null,
                                            tint = walletColor,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = wallet.name,
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
                                                color = statusColor.copy(alpha = 0.14f),
                                                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.3f))
                                            ) {
                                                Text(
                                                    text = statusLabel,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = statusColor,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${curr.displayName} • $sharePctText do total",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    if (!wallet.isDefault) {
                                        IconButton(
                                            onClick = { onDeleteWallet(wallet) },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.DeleteOutline,
                                                contentDescription = "Remover",
                                                tint = RedAction,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                LinearProgressIndicator(
                                    progress = { shareFraction },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = walletColor,
                                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Saldo Atual",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = formatFinanceAmount(bal, curr, hideValues),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 16.sp,
                                        color = if (!hideValues && bal < 0L) RedAction else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    // 3. Inline Transferência Rápida Card
                    if (wallets.size >= 2) {
                        item {
                            val fromWObj = wallets.find { it.name == fromWallet } ?: wallets.first()
                            val toWObj = wallets.find { it.name == toWallet } ?: wallets.last()
                            val parsedAmt = parseDecimalToMinorUnits(transferAmountText) ?: 0L

                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.SwapHoriz,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = "Transferência Rápida",
                                                fontSize = 15.5.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Movimente valores entre contas instantaneamente",
                                                fontSize = 11.5.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    // Stacked DE / PARA with overlapping circular swap button
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            // DE (ORIGEM)
                                            Box {
                                                Surface(
                                                    shape = RoundedCornerShape(16.dp),
                                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(16.dp))
                                                        .clickable { fromMenuExpanded = true }
                                                ) {
                                                    Column(modifier = Modifier.padding(12.dp)) {
                                                        Text(
                                                            text = "DE (ORIGEM)",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            letterSpacing = 0.6.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Text(
                                                                text = fromWObj.name,
                                                                fontWeight = FontWeight.ExtraBold,
                                                                fontSize = 14.sp,
                                                                color = MaterialTheme.colorScheme.onSurface
                                                            )
                                                            Text(
                                                                text = formatFinanceAmount(walletBalance(fromWObj), defaultCurrency, hideValues),
                                                                fontSize = 12.5.sp,
                                                                fontWeight = FontWeight.SemiBold,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                }
                                                DropdownMenu(
                                                    expanded = fromMenuExpanded,
                                                    onDismissRequest = { fromMenuExpanded = false }
                                                ) {
                                                    wallets.forEach { w ->
                                                        DropdownMenuItem(
                                                            text = { Text(w.name) },
                                                            onClick = {
                                                                fromWallet = w.name
                                                                fromMenuExpanded = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }

                                            // PARA (DESTINO)
                                            Box {
                                                Surface(
                                                    shape = RoundedCornerShape(16.dp),
                                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(16.dp))
                                                        .clickable { toMenuExpanded = true }
                                                ) {
                                                    Column(modifier = Modifier.padding(12.dp)) {
                                                        Text(
                                                            text = "PARA (DESTINO)",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            letterSpacing = 0.6.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Text(
                                                                text = toWObj.name,
                                                                fontWeight = FontWeight.ExtraBold,
                                                                fontSize = 14.sp,
                                                                color = MaterialTheme.colorScheme.onSurface
                                                            )
                                                            Text(
                                                                text = formatFinanceAmount(walletBalance(toWObj), defaultCurrency, hideValues),
                                                                fontSize = 12.5.sp,
                                                                fontWeight = FontWeight.SemiBold,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                }
                                                DropdownMenu(
                                                    expanded = toMenuExpanded,
                                                    onDismissRequest = { toMenuExpanded = false }
                                                ) {
                                                    wallets.forEach { w ->
                                                        DropdownMenuItem(
                                                            text = { Text(w.name) },
                                                            onClick = {
                                                                toWallet = w.name
                                                                toMenuExpanded = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // Overlapping Circular Swap Button
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primary,
                                            border = BorderStroke(2.5.dp, MaterialTheme.colorScheme.surface),
                                            modifier = Modifier
                                                .size(38.dp)
                                                .align(Alignment.Center)
                                                .clip(CircleShape)
                                                .clickable {
                                                    val tmp = fromWallet
                                                    fromWallet = toWallet
                                                    toWallet = tmp
                                                }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Default.SwapVert,
                                                    contentDescription = "Inverter contas",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = transferAmountText,
                                            onValueChange = { transferAmountText = it },
                                            label = { Text("Valor (${defaultCurrency.symbol})") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(14.dp)
                                        )

                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                                Text(
                                                    text = "Data da Operação",
                                                    fontSize = 10.5.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = todayLabel,
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            if (parsedAmt > 0L && fromWallet != toWallet) {
                                                onTransfer(fromWallet, toWallet, parsedAmt, defaultCurrency.code)
                                                transferAmountText = ""
                                            }
                                        },
                                        enabled = fromWallet != toWallet && parsedAmt > 0L,
                                        shape = RoundedCornerShape(22.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                    ) {
                                        Icon(Icons.Default.CheckCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (parsedAmt > 0L) {
                                                "Confirmar Transferência (${formatFinanceAmount(parsedAmt, defaultCurrency, hideValues)})"
                                            } else {
                                                "Confirmar Transferência"
                                            },
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 13.5.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 4. Câmbio & Multi-Moeda Card
                    item {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.CurrencyExchange,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = "Câmbio & Multi-Moeda",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Conversão Automática",
                                                fontSize = 11.5.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = GreenAccent.copy(alpha = 0.15f),
                                        border = BorderStroke(1.dp, GreenAccent.copy(alpha = 0.35f))
                                    ) {
                                        Text(
                                            text = "Ativo",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = GreenAccent,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Moeda Base do Sistema:",
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = defaultCurrency.displayName,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 5. Últimas Movimentações
                    if (recentMovements.isNotEmpty()) {
                        item {
                            Text(
                                text = "Últimas Movimentações",
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        items(recentMovements, key = { "mov_${it.id}" }) { mov ->
                            val mCurr = FinanceCurrency.fromCode(mov.currencyCode)
                            val isInc = mov.type == FinanceEntryType.INCOME.id
                            val dateStr = SimpleDateFormat("dd MMM, HH:mm", Locale("pt", "AO")).format(Date(mov.dateMillis))
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(
                                                (if (isInc) GreenAccent else MaterialTheme.colorScheme.primary).copy(alpha = 0.15f),
                                                RoundedCornerShape(12.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isInc) Icons.Default.SouthWest else Icons.Default.NorthEast,
                                            contentDescription = null,
                                            tint = if (isInc) GreenAccent else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = mov.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${mov.walletName} • $dateStr",
                                            fontSize = 11.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = formatFinanceAmount(mov.totalAmountMinor, mCurr, hideValues),
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "✓ Sucesso",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = GreenAccent
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(12.dp))
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

@Composable
fun SearchableCurrencyPickerDialog(
    selectedCurrency: FinanceCurrency,
    onDismiss: () -> Unit,
    onSelectCurrency: (FinanceCurrency) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredCurrencies = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            FinanceCurrency.entries
        } else {
            FinanceCurrency.entries.filter { it.matchesSearch(searchQuery) }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 580.dp)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Selecionar Moeda",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Pesquise por moeda, sigla ou país",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Search TextField
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text("Ex: MT, Moçambique, USD, Real...", fontSize = 13.5.sp)
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Pesquisar", tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Limpar", modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (filteredCurrencies.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.SearchOff,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Nenhuma moeda encontrada",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredCurrencies, key = { it.code }) { curr ->
                            val isSelected = curr == selectedCurrency
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onSelectCurrency(curr) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = curr.symbol,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 13.sp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = curr.displayName,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                                    modifier = Modifier.padding(2.dp)
                                                ) {
                                                    Text(
                                                        text = curr.code,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                            if (curr.countryOrOrigin.isNotBlank()) {
                                                Text(
                                                    text = curr.countryOrOrigin,
                                                    fontSize = 11.5.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }

                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = "Selecionada",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
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
}
