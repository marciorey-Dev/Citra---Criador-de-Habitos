package com.example.ui.screens.timer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.PomodoroPhase
import com.example.ui.components.AddCategoryDialog
import com.example.ui.theme.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(
    viewModel: MainViewModel,
    onNavigateToTasks: () -> Unit
) {
    val pomodoroState by viewModel.pomodoroState.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    val habits by viewModel.habits.collectAsState()
    val scrollState = rememberScrollState()

    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showPauseConfirmDialog by remember { mutableStateOf(false) }
    var showSelectCategoryBeforeStartDialog by remember { mutableStateOf(false) }
    var isCategoryTrashVisible by remember { mutableStateOf(false) }
    var categoryToDelete by remember { mutableStateOf<com.example.data.model.Category?>(null) }

    var showManualTimeInputDialog by remember { mutableStateOf(false) }

    // Presets for quick selection
    val focusPresets = remember { listOf(10, 15, 20, 25, 30, 45, 60, 90, 120) }
    val breakPresets = remember { listOf(5, 10, 15, 20, 25, 30) }
    val cyclePresets = remember { listOf(1, 2, 3, 4, 6, 8, 12) }

    val isRunning = pomodoroState.isRunning

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Phase and Cycle Indicator Header
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (pomodoroState.currentPhase == PomodoroPhase.FOCUS)
                    MaterialTheme.colorScheme.primaryContainer
                else GreenContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (pomodoroState.currentPhase == PomodoroPhase.FOCUS) "🎯 Modo Foco" else "☕ Modo Pausa",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = if (pomodoroState.currentPhase == PomodoroPhase.FOCUS)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else OnGreenContainer
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                ) {
                    Text(
                        text = "Ciclo ${pomodoroState.currentCycle} de ${pomodoroState.totalCycles}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Citrus Countdown Clock
        val totalSec = pomodoroState.totalPhaseSeconds.coerceAtLeast(1)
        val currentProgress = (pomodoroState.remainingSeconds.toFloat() / totalSec.toFloat()).coerceIn(0f, 1f)
        val animatedProgress by animateFloatAsState(
            targetValue = currentProgress,
            animationSpec = tween(500),
            label = "citrusPomodoroProgress"
        )

        val seedCount = if (pomodoroState.focusMinutes <= 30) 12 else 24
        val visibleSeeds = (seedCount * currentProgress).roundToInt().coerceIn(0, seedCount)

        val minutes = pomodoroState.remainingSeconds / 60
        val seconds = pomodoroState.remainingSeconds % 60
        val formattedTime = String.format("%02d:%02d", minutes, seconds)

        Box(
            modifier = Modifier
                .size(260.dp)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            val strokeColor = if (pomodoroState.currentPhase == PomodoroPhase.FOCUS)
                MaterialTheme.colorScheme.primary
            else GreenAccent
            val strokeSecondary = if (pomodoroState.currentPhase == PomodoroPhase.FOCUS)
                RedAction
            else GreenLight

            Canvas(modifier = Modifier.fillMaxSize()) {
                val peelThickness = 14.dp.toPx()
                val diameter = size.minDimension - peelThickness
                val centerOffset = Offset(size.width / 2, size.height / 2)
                val outerRadius = diameter / 2
                val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
                val arcSize = Size(diameter, diameter)

                // 1. Soft citrus pulp background
                drawCircle(
                    color = strokeColor.copy(alpha = 0.12f),
                    radius = outerRadius,
                    center = centerOffset
                )

                // 2. Citrus segment membrane lines
                val segmentCount = 10
                for (s in 0 until segmentCount) {
                    val segAngle = Math.toRadians((s * 360.0 / segmentCount) - 90.0)
                    val segStartX = centerOffset.x + (38.dp.toPx() * kotlin.math.cos(segAngle)).toFloat()
                    val segStartY = centerOffset.y + (38.dp.toPx() * kotlin.math.sin(segAngle)).toFloat()
                    val segEndX = centerOffset.x + ((outerRadius - peelThickness) * kotlin.math.cos(segAngle)).toFloat()
                    val segEndY = centerOffset.y + ((outerRadius - peelThickness) * kotlin.math.sin(segAngle)).toFloat()
                    drawLine(
                        color = strokeColor.copy(alpha = 0.22f),
                        start = Offset(segStartX, segStartY),
                        end = Offset(segEndX, segEndY),
                        strokeWidth = 1.5.dp.toPx()
                    )
                }

                // 3. Inner pith rim
                drawCircle(
                    color = Color(0xFFFFD180).copy(alpha = 0.5f),
                    radius = outerRadius - peelThickness + 1.dp.toPx(),
                    center = centerOffset,
                    style = Stroke(width = 2.dp.toPx())
                )

                // 4. Background rind track
                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = peelThickness, cap = StrokeCap.Round)
                )

                // 5. Active Citrus Peel progress arc
                val sweepAngle = animatedProgress * 360f
                drawArc(
                    brush = Brush.sweepGradient(listOf(strokeColor, strokeSecondary, strokeColor)),
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = peelThickness, cap = StrokeCap.Round)
                )

                // 6. Analog Citrus Seeds disappearing clockwise
                val seedRadius = outerRadius - peelThickness - 18.dp.toPx()
                for (i in 0 until seedCount) {
                    if (i < visibleSeeds) {
                        val angleDeg = (i * 360.0 / seedCount) - 90.0
                        val angleRad = Math.toRadians(angleDeg)
                        val sx = centerOffset.x + (seedRadius * kotlin.math.cos(angleRad)).toFloat()
                        val sy = centerOffset.y + (seedRadius * kotlin.math.sin(angleRad)).toFloat()

                        rotate(degrees = angleDeg.toFloat() + 90f, pivot = Offset(sx, sy)) {
                            drawOval(
                                color = Color(0xFFFFF9C4),
                                topLeft = Offset(sx - 3.5.dp.toPx(), sy - 6.5.dp.toPx()),
                                size = Size(7.dp.toPx(), 13.dp.toPx())
                            )
                            drawOval(
                                color = strokeColor,
                                topLeft = Offset(sx - 3.5.dp.toPx(), sy - 6.5.dp.toPx()),
                                size = Size(7.dp.toPx(), 13.dp.toPx()),
                                style = Stroke(width = 1.2.dp.toPx())
                            )
                        }
                    }
                }

                // 7. Core circle
                drawCircle(
                    color = strokeColor.copy(alpha = 0.25f),
                    radius = 36.dp.toPx(),
                    center = centerOffset
                )
            }

            // Digital Time Display at center with translucent edit pencil
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = !isRunning) {
                            showManualTimeInputDialog = true
                        }
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = formattedTime,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Editar tempo",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (pomodoroState.isRunning) GreenContainer else MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = if (pomodoroState.isRunning) "Em andamento" else if (pomodoroState.isPaused) "Pausado" else "Pronto",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (pomodoroState.isRunning) OnGreenContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "🍊 Dial cítrico • $visibleSeeds/$seedCount sementes ativas",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Main Controls: Only Reset and Play/Pause
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    val isActive = pomodoroState.isRunning || pomodoroState.isPaused ||
                            pomodoroState.remainingSeconds < pomodoroState.totalPhaseSeconds
                    if (isActive) {
                        showResetConfirmDialog = true
                    } else {
                        viewModel.resetPomodoro()
                    }
                },
                modifier = Modifier
                    .size(54.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    .testTag("pomodoro_reset_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reiniciar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(32.dp))

            Button(
                onClick = {
                    if (pomodoroState.isRunning) {
                        showPauseConfirmDialog = true
                    } else {
                        if (pomodoroState.selectedCategory.isBlank()) {
                            showSelectCategoryBeforeStartDialog = true
                        } else {
                            viewModel.toggleStartPausePomodoro()
                        }
                    }
                },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (pomodoroState.isRunning) RedAction else MaterialTheme.colorScheme.primary
                ),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .size(80.dp)
                    .testTag("pomodoro_play_pause_button")
            ) {
                Icon(
                    imageVector = if (pomodoroState.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (pomodoroState.isRunning) "Pausar" else "Iniciar",
                    tint = Color.White,
                    modifier = Modifier.size(42.dp)
                )
            }
        }

        // Running hint
        if (isRunning) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "⏸️ Pause ou reinicie para alterar as configurações do timer",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 1. CATEGORIA (Posicionada logo abaixo do timer conforme solicitado)
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Categoria",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (pomodoroState.selectedCategory.isNotBlank()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = pomodoroState.selectedCategory,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isCategoryTrashVisible) {
                            IconButton(
                                onClick = { isCategoryTrashVisible = false },
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(RedAction.copy(alpha = 0.15f), CircleShape)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Lixeira de Categorias",
                                    tint = RedAction,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        TextButton(
                            onClick = { showAddCategoryDialog = true },
                            enabled = !isRunning,
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Nova", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(categories) { cat ->
                        val isSelected = pomodoroState.selectedCategory == cat.name
                        InputChip(
                            selected = isSelected,
                            onClick = { if (!isRunning) viewModel.setPomodoroCategory(cat.name) },
                            enabled = !isRunning,
                            label = { Text(cat.name, fontSize = 12.sp) },
                            leadingIcon = { Text(cat.emoji, fontSize = 14.sp) },
                            trailingIcon = if (isCategoryTrashVisible && cat.isCustom) {
                                {
                                    IconButton(
                                        onClick = { categoryToDelete = cat },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Remover", tint = RedAction, modifier = Modifier.size(14.dp))
                                    }
                                }
                            } else null
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (pomodoroState.selectedCategory.isBlank()) "⚠️ Nenhuma categoria definida" else "Selecione a categoria para pontuar o foco",
                        fontSize = 11.sp,
                        color = if (pomodoroState.selectedCategory.isBlank()) RedAction else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = { isCategoryTrashVisible = !isCategoryTrashVisible },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Text(if (isCategoryTrashVisible) "Concluir" else "Gerenciar", fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. CONFIGURAÇÃO DA SESSÃO: PRESETS RÁPIDOS
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Configuração da Sessão",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "⏱️ Ajuste Rápido",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // 1. Tempo de Foco
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tempo de Foco",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "${pomodoroState.focusMinutes} min",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(focusPresets) { mins ->
                            val isSelected = pomodoroState.focusMinutes == mins
                            FilterChip(
                                selected = isSelected,
                                onClick = { if (!isRunning) viewModel.setFocusDuration(mins) },
                                enabled = !isRunning,
                                label = { Text("${mins}m", fontSize = 12.sp) }
                            )
                        }
                    }
                }

                // 2. Tempo de Pausa
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tempo de Pausa",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = GreenContainer
                        ) {
                            Text(
                                text = "${pomodoroState.breakMinutes} min",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = OnGreenContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(breakPresets) { mins ->
                            val isSelected = pomodoroState.breakMinutes == mins
                            FilterChip(
                                selected = isSelected,
                                onClick = { if (!isRunning) viewModel.setBreakDuration(mins) },
                                enabled = !isRunning,
                                label = { Text("${mins}m", fontSize = 12.sp) }
                            )
                        }
                    }
                }

                // 3. Ciclos de Repetição
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Ciclos de Repetição",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "${pomodoroState.totalCycles}x",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(cyclePresets) { cycles ->
                            val isSelected = pomodoroState.totalCycles == cycles
                            FilterChip(
                                selected = isSelected,
                                onClick = { if (!isRunning) viewModel.setTotalCycles(cycles) },
                                enabled = !isRunning,
                                label = { Text("${cycles}x", fontSize = 12.sp) }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showAddCategoryDialog) {
        AddCategoryDialog(
            onDismiss = { showAddCategoryDialog = false },
            onConfirm = { name, emoji ->
                viewModel.addCustomCategory(name, emoji)
                showAddCategoryDialog = false
            }
        )
    }

    if (showManualTimeInputDialog) {
        var tempMinutesText by remember { mutableStateOf(pomodoroState.focusMinutes.toString()) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showManualTimeInputDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Ajustar Tempo do Timer",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "Digite a minutagem desejada para o foco (valor de 1 a 120 minutos):",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = tempMinutesText,
                        onValueChange = { input ->
                            val digitsOnly = input.filter { it.isDigit() }.take(3)
                            tempMinutesText = digitsOnly
                            val num = digitsOnly.toIntOrNull()
                            errorMessage = when {
                                digitsOnly.isEmpty() -> "Informe os minutos"
                                num == null || num <= 0 -> "O valor deve ser maior que 0"
                                num > 120 -> "O tempo máximo é de 120 minutos"
                                else -> null
                            }
                        },
                        label = { Text("Tempo de Foco (min)") },
                        singleLine = true,
                        isError = errorMessage != null,
                        supportingText = {
                            if (errorMessage != null) {
                                Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
                            } else {
                                Text("Máximo: 120 min")
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val num = tempMinutesText.toIntOrNull()
                        if (num != null && num in 1..120) {
                            viewModel.setFocusDuration(num)
                            showManualTimeInputDialog = false
                        }
                    },
                    enabled = errorMessage == null && tempMinutesText.isNotBlank(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Definir")
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualTimeInputDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Modal Empático para Pausar Sessão
    if (showPauseConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showPauseConfirmDialog = false },
            icon = { Text("🌿", fontSize = 34.sp) },
            title = {
                Text(
                    text = "Pausa Consciente",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "Respire fundo! Pausas conscientes renovam o foco. Descanse um pouco e volte quando estiver pronto.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            },
            confirmButton = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            showPauseConfirmDialog = false
                            viewModel.toggleStartPausePomodoro()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RedAction),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Pausar Sessão", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { showPauseConfirmDialog = false },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Continuar Focado")
                    }
                }
            }
        )
    }

    // Confirmação de Reinício com Botões Centralizados Esticados
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            icon = { Text("🍊", fontSize = 32.sp) },
            title = {
                Text(
                    text = "Interromper Sessão de Foco?",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "Tem a certeza que deseja interromper agora? Você estava indo tão bem... Seu foco é muito valioso! Deseja mesmo pausar e zerar esta sessão?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            },
            confirmButton = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showResetConfirmDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Continuar Focado", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = {
                            showResetConfirmDialog = false
                            viewModel.resetPomodoro()
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Interromper Mesmo", color = RedAction)
                    }
                }
            }
        )
    }

    // Exigência de Categoria Antes de Começar o Timer com Aviso
    if (showSelectCategoryBeforeStartDialog) {
        AlertDialog(
            onDismissRequest = { showSelectCategoryBeforeStartDialog = false },
            icon = { Text("🎯", fontSize = 32.sp) },
            title = {
                Text(
                    text = "Defina a Categoria do Foco",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFFFFBEB),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("⚠️", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Lembre-se: sempre defina a categoria antes de usar o timer para registrar suas estatísticas corretamente.",
                                fontSize = 11.5.sp,
                                color = Color(0xFF78350F),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Text("Toque em uma categoria para iniciar imediatamente:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(categories) { cat ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable {
                                    viewModel.setPomodoroCategory(cat.name)
                                    showSelectCategoryBeforeStartDialog = false
                                    viewModel.toggleStartPausePomodoro()
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(cat.emoji, fontSize = 15.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(cat.name, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSelectCategoryBeforeStartDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Exclusão de Categoria
    if (categoryToDelete != null) {
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = RedAction) },
            title = { Text("Excluir Categoria?") },
            text = { Text("Deseja apagar a categoria \"${categoryToDelete?.name}\"?") },
            confirmButton = {
                Button(
                    onClick = {
                        categoryToDelete?.let { viewModel.deleteCustomCategory(it) }
                        categoryToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedAction)
                ) {
                    Text("Excluir", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Session Completed Dialog
    if (pomodoroState.showCycleCompletedDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissCycleCompletedDialog() },
            icon = { Text("🎉", fontSize = 36.sp) },
            title = { Text("Sessão Completa!", fontWeight = FontWeight.Bold) },
            text = { Text(pomodoroState.completedSummary) },
            confirmButton = {
                Button(
                    onClick = { viewModel.dismissCycleCompletedDialog() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Continuar", color = Color.White)
                }
            }
        )
    }
}
