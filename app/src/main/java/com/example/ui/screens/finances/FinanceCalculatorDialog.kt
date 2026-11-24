package com.example.ui.screens.finances

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun FinanceCalculatorDialog(
    onDismiss: () -> Unit
) {
    var display by remember { mutableStateOf("0") }
    var currentOp by remember { mutableStateOf<String?>(null) }
    var firstOperand by remember { mutableStateOf<Double?>(null) }
    var clearOnNextInput by remember { mutableStateOf(false) }

    fun appendDigit(digit: String) {
        if (display == "0" || clearOnNextInput) {
            display = digit
            clearOnNextInput = false
        } else {
            if (display.length < 12) {
                display += digit
            }
        }
    }

    fun appendDecimal() {
        if (clearOnNextInput) {
            display = "0,"
            clearOnNextInput = false
        } else if (!display.contains(",")) {
            display += ","
        }
    }

    fun handleOperator(op: String) {
        val value = display.replace(",", ".").toDoubleOrNull() ?: 0.0
        if (firstOperand == null) {
            firstOperand = value
        } else if (currentOp != null && !clearOnNextInput) {
            val prev = firstOperand!!
            val result = when (currentOp) {
                "+" -> prev + value
                "-" -> prev - value
                "×" -> prev * value
                "÷" -> if (value != 0.0) prev / value else 0.0
                else -> value
            }
            firstOperand = result
            display = formatCalcResult(result)
        }
        currentOp = op
        clearOnNextInput = true
    }

    fun calculateResult() {
        val value = display.replace(",", ".").toDoubleOrNull() ?: 0.0
        if (firstOperand != null && currentOp != null) {
            val prev = firstOperand!!
            val result = when (currentOp) {
                "+" -> prev + value
                "-" -> prev - value
                "×" -> prev * value
                "÷" -> if (value != 0.0) prev / value else 0.0
                else -> value
            }
            display = formatCalcResult(result)
            firstOperand = null
            currentOp = null
            clearOnNextInput = true
        }
    }

    fun clearAll() {
        display = "0"
        currentOp = null
        firstOperand = null
        clearOnNextInput = false
    }

    fun backspace() {
        if (clearOnNextInput) return
        if (display.length > 1) {
            display = display.dropLast(1)
        } else {
            display = "0"
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.925f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Calculate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = "Calculadora Básica",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                // Display Screen
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (currentOp != null && firstOperand != null) {
                            Text(
                                text = "${formatCalcResult(firstOperand!!)} $currentOp",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = display,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            textAlign = TextAlign.End
                        )
                    }
                }

                // Keypad grid
                val btnModifier = Modifier
                    .weight(1f)
                    .height(52.dp)

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Row 1
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { clearAll() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                            shape = RoundedCornerShape(14.dp),
                            modifier = btnModifier
                        ) { Text("C", fontWeight = FontWeight.Bold, fontSize = 16.sp) }

                        Button(
                            onClick = { backspace() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = MaterialTheme.colorScheme.onSurface),
                            shape = RoundedCornerShape(14.dp),
                            modifier = btnModifier
                        ) { Text("⌫", fontWeight = FontWeight.Bold, fontSize = 16.sp) }

                        Button(
                            onClick = { handleOperator("÷") },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer),
                            shape = RoundedCornerShape(14.dp),
                            modifier = btnModifier
                        ) { Text("÷", fontWeight = FontWeight.Bold, fontSize = 18.sp) }

                        Button(
                            onClick = { handleOperator("×") },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer),
                            shape = RoundedCornerShape(14.dp),
                            modifier = btnModifier
                        ) { Text("×", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                    }

                    // Row 2
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(onClick = { appendDigit("7") }, shape = RoundedCornerShape(14.dp), modifier = btnModifier) { Text("7", fontSize = 18.sp) }
                        Button(onClick = { appendDigit("8") }, shape = RoundedCornerShape(14.dp), modifier = btnModifier) { Text("8", fontSize = 18.sp) }
                        Button(onClick = { appendDigit("9") }, shape = RoundedCornerShape(14.dp), modifier = btnModifier) { Text("9", fontSize = 18.sp) }
                        Button(
                            onClick = { handleOperator("-") },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer),
                            shape = RoundedCornerShape(14.dp),
                            modifier = btnModifier
                        ) { Text("-", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                    }

                    // Row 3
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(onClick = { appendDigit("4") }, shape = RoundedCornerShape(14.dp), modifier = btnModifier) { Text("4", fontSize = 18.sp) }
                        Button(onClick = { appendDigit("5") }, shape = RoundedCornerShape(14.dp), modifier = btnModifier) { Text("5", fontSize = 18.sp) }
                        Button(onClick = { appendDigit("6") }, shape = RoundedCornerShape(14.dp), modifier = btnModifier) { Text("6", fontSize = 18.sp) }
                        Button(
                            onClick = { handleOperator("+") },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer),
                            shape = RoundedCornerShape(14.dp),
                            modifier = btnModifier
                        ) { Text("+", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                    }

                    // Row 4
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(onClick = { appendDigit("1") }, shape = RoundedCornerShape(14.dp), modifier = btnModifier) { Text("1", fontSize = 18.sp) }
                        Button(onClick = { appendDigit("2") }, shape = RoundedCornerShape(14.dp), modifier = btnModifier) { Text("2", fontSize = 18.sp) }
                        Button(onClick = { appendDigit("3") }, shape = RoundedCornerShape(14.dp), modifier = btnModifier) { Text("3", fontSize = 18.sp) }
                        Button(
                            onClick = { calculateResult() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                            shape = RoundedCornerShape(14.dp),
                            modifier = btnModifier
                        ) { Text("=", fontWeight = FontWeight.Bold, fontSize = 20.sp) }
                    }

                    // Row 5
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(onClick = { appendDigit("0") }, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(2f).height(52.dp)) { Text("0", fontSize = 18.sp) }
                        Button(onClick = { appendDecimal() }, shape = RoundedCornerShape(14.dp), modifier = btnModifier) { Text(",", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                        Button(
                            onClick = {
                                val current = display.replace(",", ".").toDoubleOrNull() ?: 0.0
                                display = formatCalcResult(-current)
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = MaterialTheme.colorScheme.onSurface),
                            modifier = btnModifier
                        ) { Text("±", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

private fun formatCalcResult(value: Double): String {
    return if (value % 1.0 == 0.0 && value in -9999999999.0..9999999999.0) {
        value.toLong().toString()
    } else {
        String.format(java.util.Locale.US, "%.2f", value).replace(".", ",")
    }
}
