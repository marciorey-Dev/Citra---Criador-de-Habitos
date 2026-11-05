package com.example.ui.screens.finances

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.model.*
import com.example.ui.theme.RedAction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FinanceEntryEditorDialog(
    existingEntry: FinanceEntry?,
    initialType: FinanceEntryType,
    defaultCurrencyCode: String,
    categories: List<FinanceCategory>,
    allCategories: List<FinanceCategory>,
    wallets: List<FinanceWallet> = emptyList(),
    onDismiss: () -> Unit,
    onSaveEntry: (entry: FinanceEntry, isNew: Boolean) -> Unit,
    onSaveCategory: (FinanceCategory) -> Unit,
    onArchiveCategory: (FinanceCategory, Boolean) -> Unit
) {
    val context = LocalContext.current

    var entryType by remember {
        mutableStateOf(
            existingEntry?.let { FinanceEntryType.fromId(it.type) } ?: initialType
        )
    }
    var name by remember { mutableStateOf(existingEntry?.name ?: "") }
    var comment by remember { mutableStateOf(existingEntry?.comment ?: "") }

    // Quantity & Unit Price vs Total
    var useQuantityMode by remember {
        mutableStateOf(existingEntry?.unitPriceMinor != null)
    }
    var totalAmountText by remember {
        mutableStateOf(
            existingEntry?.let { formatMinorUnitsForInput(it.totalAmountMinor) } ?: ""
        )
    }
    var quantityText by remember {
        mutableStateOf(
            existingEntry?.let {
                if (it.quantity % 1.0 == 0.0) it.quantity.toInt().toString()
                else it.quantity.toString().replace(".", ",")
            } ?: "1"
        )
    }
    var unitText by remember { mutableStateOf(existingEntry?.unit ?: "") }
    var unitPriceText by remember {
        mutableStateOf(
            existingEntry?.unitPriceMinor?.let { formatMinorUnitsForInput(it) } ?: ""
        )
    }

    val filteredCategories = remember(categories, entryType) {
        categories.filter { it.type == entryType.id }
    }
    var selectedCategoryId by remember(filteredCategories) {
        mutableStateOf(
            existingEntry?.categoryId?.takeIf { id -> filteredCategories.any { it.id == id } }
                ?: filteredCategories.firstOrNull()?.id
                ?: ""
        )
    }

    var dateMillis by remember { mutableLongStateOf(existingEntry?.dateMillis ?: System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }

    var selectedCurrency by remember {
        mutableStateOf(
            FinanceCurrency.fromCode(existingEntry?.currencyCode ?: defaultCurrencyCode)
        )
    }
    var currencyExpanded by remember { mutableStateOf(false) }

    var walletName by remember { mutableStateOf(existingEntry?.walletName ?: wallets.firstOrNull()?.name ?: "Carteira Principal") }
    var isPlanned by remember { mutableStateOf(existingEntry?.isPlanned ?: false) }
    var priority by remember {
        mutableStateOf(
            existingEntry?.let { FinancePriority.fromId(it.priority) } ?: FinancePriority.MANDATORY
        )
    }

    var tagInput by remember { mutableStateOf("") }
    var tags by remember {
        mutableStateOf(
            existingEntry?.tagsCsv
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList()
        )
    }

    var attachmentUri by remember { mutableStateOf(existingEntry?.attachmentUri) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var showManageCategories by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val saved = com.example.util.MediaStorageHelper.saveNoteBackground(context, uri)
            attachmentUri = saved ?: uri.toString()
        }
    }

    val computedTotalMinor: Long? = remember(useQuantityMode, totalAmountText, quantityText, unitPriceText) {
        if (!useQuantityMode) {
            parseDecimalToMinorUnits(totalAmountText)
        } else {
            val qty = quantityText.trim().replace(",", ".").toDoubleOrNull()
            val unitMinor = parseDecimalToMinorUnits(unitPriceText)
            if (qty != null && qty > 0.0 && unitMinor != null) {
                kotlin.math.round(qty * unitMinor).toLong()
            } else {
                null
            }
        }
    }

    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale("pt", "AO")) }
    val presetUnits = remember { listOf("un", "kg", "L", "monte", "garrafa", "pacote", "corte") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (existingEntry == null) "Novo Lançamento" else "Editar Lançamento",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Type selector (Despesa / Renda)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                RoundedCornerShape(14.dp)
                            )
                            .padding(4.dp)
                    ) {
                        FinanceEntryType.entries.forEach { t ->
                            val selected = entryType == t
                            Button(
                                onClick = {
                                    entryType = t
                                    errorMsg = null
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                shape = RoundedCornerShape(10.dp),
                                elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(t.label, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Name (required)
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            errorMsg = null
                        },
                        label = { Text("Nome *") },
                        placeholder = {
                            Text(if (entryType == FinanceEntryType.EXPENSE) "Ex.: Arroz, Transporte..." else "Ex.: Salário, Freelance...")
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("finance_entry_name_input")
                    )

                    // Value Mode Toggle: Total vs Quantity x Unit Price
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Valor",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        FilterChip(
                            selected = useQuantityMode,
                            onClick = { useQuantityMode = !useQuantityMode },
                            label = { Text("Quantidade × Unitário", fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Calculate,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }

                    if (!useQuantityMode) {
                        OutlinedTextField(
                            value = totalAmountText,
                            onValueChange = {
                                totalAmountText = it
                                errorMsg = null
                            },
                            label = { Text("Valor total *") },
                            placeholder = { Text("0") },
                            suffix = { Text(selectedCurrency.symbol, fontWeight = FontWeight.Bold) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("finance_entry_total_input")
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = quantityText,
                                onValueChange = {
                                    quantityText = it
                                    errorMsg = null
                                },
                                label = { Text("Qtd. *") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = unitText,
                                onValueChange = { unitText = it },
                                label = { Text("Unidade") },
                                placeholder = { Text("kg, L...") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = unitPriceText,
                                onValueChange = {
                                    unitPriceText = it
                                    errorMsg = null
                                },
                                label = { Text("Unit. *") },
                                suffix = { Text(selectedCurrency.symbol, fontSize = 12.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1.3f)
                            )
                        }

                        // Quick unit chips
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(presetUnits) { u ->
                                SuggestionChip(
                                    onClick = { unitText = u },
                                    label = { Text(u, fontSize = 11.5.sp) }
                                )
                            }
                        }

                        if (computedTotalMinor != null) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Valor total calculado:", fontSize = 12.5.sp)
                                    Text(
                                        text = formatFinanceAmount(computedTotalMinor, selectedCurrency),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    // Category Selection + Manage Categories
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Categoria *",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        TextButton(
                            onClick = { showManageCategories = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Gerir categorias", fontSize = 12.sp)
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        filteredCategories.forEach { cat ->
                            val isSelected = cat.id == selectedCategoryId
                            val catColor = Color(cat.colorHex)
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedCategoryId = cat.id },
                                label = { Text(cat.name, fontSize = 12.5.sp) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = FinanceCategoryIcons.iconForName(cat.iconName),
                                        contentDescription = null,
                                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else catColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }

                    // Comment (optional)
                    OutlinedTextField(
                        value = comment,
                        onValueChange = { comment = it },
                        label = { Text("Comentário (opcional)") },
                        placeholder = { Text("Aparece abaixo do nome em letra menor") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Date & Currency Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = dateFormatter.format(Date(dateMillis)),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Data") },
                            trailingIcon = {
                                IconButton(onClick = { showDatePicker = true }) {
                                    Icon(Icons.Default.CalendarToday, contentDescription = "Escolher data", modifier = Modifier.size(18.dp))
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showDatePicker = true }
                        )

                        ExposedDropdownMenuBox(
                            expanded = currencyExpanded,
                            onExpandedChange = { currencyExpanded = !currencyExpanded },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = "${selectedCurrency.symbol} (${selectedCurrency.code})",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Moeda") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = currencyExpanded) },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = currencyExpanded,
                                onDismissRequest = { currencyExpanded = false }
                            ) {
                                FinanceCurrency.entries.forEach { curr ->
                                    DropdownMenuItem(
                                        text = { Text(curr.displayName) },
                                        onClick = {
                                            selectedCurrency = curr
                                            currencyExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Priority (Obrigatória vs Opcional)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Prioridade",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FinancePriority.entries.forEach { p ->
                                FilterChip(
                                    selected = priority == p,
                                    onClick = { priority = p },
                                    label = { Text(p.label, fontSize = 12.5.sp) },
                                    leadingIcon = if (priority == p) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null
                                )
                            }
                        }
                    }

                    // Wallet
                    OutlinedTextField(
                        value = walletName,
                        onValueChange = { walletName = it },
                        label = { Text("Carteira") },
                        leadingIcon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Tags
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = tagInput,
                                onValueChange = { tagInput = it },
                                label = { Text("Etiquetas (opcional)") },
                                placeholder = { Text("Ex.: mercado, urgente") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            )
                            FilledTonalIconButton(
                                onClick = {
                                    val cleaned = tagInput.trim().removePrefix("#")
                                    if (cleaned.isNotEmpty() && !tags.contains(cleaned)) {
                                        tags = tags + cleaned
                                    }
                                    tagInput = ""
                                }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Adicionar etiqueta")
                            }
                        }
                        if (tags.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                tags.forEach { tag ->
                                    InputChip(
                                        selected = false,
                                        onClick = { tags = tags - tag },
                                        label = { Text("#$tag", fontSize = 12.sp) },
                                        trailingIcon = {
                                            Icon(Icons.Default.Close, contentDescription = "Remover", modifier = Modifier.size(14.dp))
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Attachment (Receipt / Photo)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Anexo (talão ou recibo)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            OutlinedButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (attachmentUri == null) "Anexar foto" else "Trocar foto", fontSize = 12.5.sp)
                            }
                        }
                        if (attachmentUri != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                            ) {
                                AsyncImage(
                                    model = attachmentUri,
                                    contentDescription = "Anexo do lançamento",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                IconButton(
                                    onClick = { attachmentUri = null },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .size(28.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Remover anexo", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    // Wallet & Currency selectors
                    if (wallets.isNotEmpty()) {
                        Text("Carteira", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(wallets, key = { it.id }) { w ->
                                FilterChip(
                                    selected = walletName.equals(w.name, ignoreCase = true),
                                    onClick = { walletName = w.name },
                                    label = { Text(w.name, fontSize = 12.sp) }
                                )
                            }
                        }
                    }

                    Text("Moeda", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(FinanceCurrency.entries, key = { it.code }) { curr ->
                            FilterChip(
                                selected = selectedCurrency == curr,
                                onClick = { selectedCurrency = curr },
                                label = { Text("${curr.code} (${curr.symbol})", fontSize = 12.sp) }
                            )
                        }
                    }

                    // Planned vs Real toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { isPlanned = !isPlanned }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Item planeado (Lista de compras)", fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                            Text(
                                "Não entra no saldo até marcar ✔ como pago",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = isPlanned, onCheckedChange = { isPlanned = it })
                    }

                    if (errorMsg != null) {
                        Text(
                            text = errorMsg!!,
                            color = RedAction,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Footer Save Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val trimmedName = name.trim()
                            if (trimmedName.isEmpty()) {
                                errorMsg = "Informe o nome do lançamento"
                                return@Button
                            }
                            val totalMinor = computedTotalMinor
                            if (totalMinor == null || totalMinor <= 0L) {
                                errorMsg = "Informe um valor válido maior que zero"
                                return@Button
                            }
                            val catId = selectedCategoryId.ifBlank {
                                filteredCategories.firstOrNull()?.id ?: ""
                            }
                            val qty = if (useQuantityMode) {
                                quantityText.trim().replace(",", ".").toDoubleOrNull() ?: 1.0
                            } else {
                                1.0
                            }
                            val unitPrice = if (useQuantityMode) {
                                parseDecimalToMinorUnits(unitPriceText)
                            } else {
                                null
                            }
                            val entry = FinanceEntry(
                                id = existingEntry?.id ?: UUID.randomUUID().toString(),
                                type = entryType.id,
                                name = trimmedName,
                                comment = comment.trim(),
                                categoryId = catId,
                                walletName = walletName.trim().ifBlank { "Carteira Principal" },
                                currencyCode = selectedCurrency.code,
                                dateMillis = dateMillis,
                                quantity = qty,
                                unit = if (useQuantityMode) unitText.trim() else "",
                                unitPriceMinor = unitPrice,
                                totalAmountMinor = totalMinor,
                                priority = priority.id,
                                tagsCsv = tags.joinToString(","),
                                attachmentUri = attachmentUri,
                                isPlanned = isPlanned,
                                plannedAmountMinor = if (isPlanned) totalMinor else existingEntry?.plannedAmountMinor,
                                createdAt = existingEntry?.createdAt ?: System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis()
                            )
                            onSaveEntry(entry, existingEntry == null)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("save_finance_entry_btn")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Guardar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { dateMillis = it }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showManageCategories) {
        ManageFinanceCategoriesDialog(
            entryType = entryType,
            categories = allCategories.filter { it.type == entryType.id },
            onDismiss = { showManageCategories = false },
            onSaveCategory = onSaveCategory,
            onArchiveCategory = onArchiveCategory
        )
    }
}

@Composable
fun ManageFinanceCategoriesDialog(
    entryType: FinanceEntryType,
    categories: List<FinanceCategory>,
    onDismiss: () -> Unit,
    onSaveCategory: (FinanceCategory) -> Unit,
    onArchiveCategory: (FinanceCategory, Boolean) -> Unit
) {
    var editingCategory by remember { mutableStateOf<FinanceCategory?>(null) }
    var isCreating by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.78f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Categorias de ${entryType.label}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    FilledTonalButton(
                        onClick = {
                            editingCategory = null
                            isCreating = true
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Nova", fontSize = 12.5.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { cat ->
                        val catColor = Color(cat.colorHex)
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(
                                alpha = if (cat.isArchived) 0.25f else 0.55f
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(catColor.copy(alpha = 0.18f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = FinanceCategoryIcons.iconForName(cat.iconName),
                                        contentDescription = null,
                                        tint = catColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = cat.name,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                    if (cat.isArchived) {
                                        Text(
                                            text = "Arquivada",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        editingCategory = cat
                                        isCreating = false
                                    }
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Editar categoria", modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = { onArchiveCategory(cat, !cat.isArchived) }
                                ) {
                                    Icon(
                                        imageVector = if (cat.isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                                        contentDescription = if (cat.isArchived) "Desarquivar" else "Arquivar",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) {
                        Text("Concluir")
                    }
                }
            }
        }
    }

    if (isCreating || editingCategory != null) {
        EditFinanceCategoryDialog(
            entryType = entryType,
            existing = editingCategory,
            onDismiss = {
                isCreating = false
                editingCategory = null
            },
            onSave = { cat ->
                onSaveCategory(cat)
                isCreating = false
                editingCategory = null
            }
        )
    }
}

@Composable
private fun EditFinanceCategoryDialog(
    entryType: FinanceEntryType,
    existing: FinanceCategory?,
    onDismiss: () -> Unit,
    onSave: (FinanceCategory) -> Unit
) {
    var catName by remember { mutableStateOf(existing?.name ?: "") }
    var selectedIcon by remember { mutableStateOf(existing?.iconName ?: "shopping_cart") }
    var selectedColor by remember { mutableStateOf(existing?.colorHex ?: 0xFFFF8B1F) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existing == null) "Nova Categoria" else "Editar Categoria",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = catName,
                    onValueChange = { catName = it },
                    label = { Text("Nome da categoria") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Ícone", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(FinanceCategoryIcons.availableIcons) { (iconKey, iconVec) ->
                        val selected = selectedIcon == iconKey
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) Color(selectedColor).copy(alpha = 0.22f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                                .border(
                                    width = if (selected) 2.dp else 0.dp,
                                    color = if (selected) Color(selectedColor) else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedIcon = iconKey },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = iconVec,
                                contentDescription = iconKey,
                                tint = if (selected) Color(selectedColor) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Text("Cor", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(FinanceCategoryIcons.availableColors) { colorHex ->
                        val selected = selectedColor == colorHex
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(colorHex))
                                .border(
                                    width = if (selected) 3.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = colorHex }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = catName.trim()
                    if (trimmed.isNotEmpty()) {
                        onSave(
                            FinanceCategory(
                                id = existing?.id ?: UUID.randomUUID().toString(),
                                type = entryType.id,
                                name = trimmed,
                                iconName = selectedIcon,
                                colorHex = selectedColor,
                                isDefault = existing?.isDefault ?: false,
                                isArchived = existing?.isArchived ?: false
                            )
                        )
                    }
                }
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
