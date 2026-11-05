package com.example.ui.screens.finances

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.FinanceCurrency
import com.example.data.model.parseDecimalToMinorUnits
import com.example.ui.theme.RedAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceFirstTimeWizardDialog(
    onDismiss: () -> Unit,
    onComplete: (
        currencyCode: String,
        initialBalanceMinor: Long?,
        pinCode: String?,
        biometricEnabled: Boolean
    ) -> Unit
) {
    var selectedCurrency by remember { mutableStateOf(FinanceCurrency.AOA) }
    var currencyMenuExpanded by remember { mutableStateOf(false) }
    var initialBalanceText by remember { mutableStateOf("") }
    var enableLock by remember { mutableStateOf(false) }
    var pinText by remember { mutableStateOf("") }
    var biometricEnabled by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Configurar Finanças",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Assistente rápido de primeira utilização",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 1. Default Currency (Kwanza Kz first)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "1. Moeda padrão",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    ExposedDropdownMenuBox(
                        expanded = currencyMenuExpanded,
                        onExpandedChange = { currencyMenuExpanded = !currencyMenuExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedCurrency.displayName,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = currencyMenuExpanded)
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = currencyMenuExpanded,
                            onDismissRequest = { currencyMenuExpanded = false }
                        ) {
                            FinanceCurrency.entries.forEach { currency ->
                                DropdownMenuItem(
                                    text = { Text(currency.displayName) },
                                    onClick = {
                                        selectedCurrency = currency
                                        currencyMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // 2. Initial Balance (optional)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "2. Saldo atual inicial (opcional)",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    OutlinedTextField(
                        value = initialBalanceText,
                        onValueChange = { initialBalanceText = it },
                        placeholder = { Text("Ex.: 7000 (pode definir depois)") },
                        suffix = { Text(selectedCurrency.symbol, fontWeight = FontWeight.Bold) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 3. PIN / Biometric Lock (optional)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "3. Bloqueio por PIN ou biometria",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Opcional — protege a aba de Finanças",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = enableLock,
                                onCheckedChange = {
                                    enableLock = it
                                    if (!it) {
                                        pinText = ""
                                        biometricEnabled = false
                                        pinError = null
                                    }
                                }
                            )
                        }

                        if (enableLock) {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = pinText,
                                onValueChange = { input ->
                                    if (input.length <= 6 && input.all { it.isDigit() }) {
                                        pinText = input
                                        pinError = null
                                    }
                                },
                                label = { Text("PIN (4 a 6 dígitos)") },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (pinError != null) {
                                Text(
                                    text = pinError!!,
                                    color = RedAction,
                                    fontSize = 11.5.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { biometricEnabled = !biometricEnabled },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Permitir desbloqueio por biometria", fontSize = 13.sp)
                                Checkbox(
                                    checked = biometricEnabled,
                                    onCheckedChange = { biometricEnabled = it }
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (enableLock && pinText.isNotEmpty() && pinText.length < 4) {
                                pinError = "O PIN deve ter entre 4 e 6 dígitos"
                                return@Button
                            }
                            if (enableLock && pinText.isEmpty() && !biometricEnabled) {
                                pinError = "Defina um PIN ou ative a biometria"
                                return@Button
                            }
                            val initialMinor = if (initialBalanceText.isBlank()) {
                                null
                            } else {
                                parseDecimalToMinorUnits(initialBalanceText)
                            }
                            onComplete(
                                selectedCurrency.code,
                                initialMinor,
                                if (enableLock) pinText.ifBlank { null } else null,
                                enableLock && biometricEnabled
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("complete_finance_wizard_btn")
                    ) {
                        Text("Ativar Finanças", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun FinanceLockOverlay(
    expectedPin: String?,
    biometricEnabled: Boolean,
    onUnlocked: () -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Finanças bloqueadas",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Finanças Protegidas",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = if (!expectedPin.isNullOrBlank()) {
                    "Introduza o seu PIN para aceder aos seus dados financeiros"
                } else {
                    "Confirme a sua identidade para continuar"
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (!expectedPin.isNullOrBlank()) {
                OutlinedTextField(
                    value = enteredPin,
                    onValueChange = { input ->
                        if (input.length <= 6 && input.all { it.isDigit() }) {
                            enteredPin = input
                            errorText = null
                            if (input == expectedPin) {
                                onUnlocked()
                            }
                        }
                    },
                    label = { Text("PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(0.75f)
                )

                if (errorText != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorText!!,
                        color = RedAction,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (enteredPin == expectedPin) {
                            onUnlocked()
                        } else {
                            errorText = "PIN incorreto"
                            enteredPin = ""
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .height(48.dp)
                ) {
                    Text("Desbloquear", fontWeight = FontWeight.Bold)
                }
            }

            if (biometricEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { onUnlocked() },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Usar Biometria", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun FinanceLockSettingsDialog(
    currentPin: String?,
    currentBiometric: Boolean,
    onDismiss: () -> Unit,
    onSave: (pin: String?, biometric: Boolean) -> Unit
) {
    var enableLock by remember { mutableStateOf(!currentPin.isNullOrBlank() || currentBiometric) }
    var pinText by remember { mutableStateOf(currentPin ?: "") }
    var biometricEnabled by remember { mutableStateOf(currentBiometric) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Bloqueio de Finanças", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Exigir PIN ou biometria", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Switch(
                        checked = enableLock,
                        onCheckedChange = {
                            enableLock = it
                            if (!it) {
                                pinText = ""
                                biometricEnabled = false
                                errorMsg = null
                            }
                        }
                    )
                }

                if (enableLock) {
                    OutlinedTextField(
                        value = pinText,
                        onValueChange = { input ->
                            if (input.length <= 6 && input.all { it.isDigit() }) {
                                pinText = input
                                errorMsg = null
                            }
                        },
                        label = { Text("PIN (4 a 6 dígitos)") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { biometricEnabled = !biometricEnabled },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Desbloqueio por biometria", fontSize = 13.sp)
                        Checkbox(checked = biometricEnabled, onCheckedChange = { biometricEnabled = it })
                    }

                    if (errorMsg != null) {
                        Text(errorMsg!!, color = RedAction, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (enableLock && pinText.isNotEmpty() && pinText.length < 4) {
                        errorMsg = "O PIN deve ter entre 4 e 6 dígitos"
                        return@Button
                    }
                    if (enableLock && pinText.isEmpty() && !biometricEnabled) {
                        errorMsg = "Defina um PIN ou ative a biometria"
                        return@Button
                    }
                    onSave(
                        if (enableLock) pinText.ifBlank { null } else null,
                        enableLock && biometricEnabled
                    )
                    onDismiss()
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

@Composable
fun EraseFinanceDataDoubleConfirmDialog(
    onDismiss: () -> Unit,
    onConfirmErase: () -> Unit
) {
    var step by remember { mutableIntStateOf(1) }
    var confirmationText by remember { mutableStateOf("") }

    if (step == 1) {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = RedAction) },
            title = { Text("Apagar dados de finanças?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Esta ação vai eliminar permanentemente todas as suas despesas, rendas, categorias personalizadas e saldo configurado.\n\nNota: As notas Markdown já geradas mantêm o último texto extraído.",
                    fontSize = 13.5.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { step = 2 },
                    colors = ButtonDefaults.buttonColors(containerColor = RedAction)
                ) {
                    Text("Continuar")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancelar")
                }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = RedAction) },
            title = { Text("Confirmação final", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Para confirmar que deseja apagar todos os dados de finanças, escreva APAGAR abaixo:",
                        fontSize = 13.5.sp
                    )
                    OutlinedTextField(
                        value = confirmationText,
                        onValueChange = { confirmationText = it },
                        placeholder = { Text("APAGAR") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onConfirmErase()
                        onDismiss()
                    },
                    enabled = confirmationText.trim().equals("APAGAR", ignoreCase = true),
                    colors = ButtonDefaults.buttonColors(containerColor = RedAction)
                ) {
                    Text("Apagar Tudo")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancelar")
                }
            }
        )
    }
}
