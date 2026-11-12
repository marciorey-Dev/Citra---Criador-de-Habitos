package com.example.ui.screens.timer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.PomodoroPhase
import com.example.ui.components.AddCategoryDialog
import com.example.ui.theme.RedAction
import com.example.ui.tutorial.tutorialTarget
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(
    viewModel: MainViewModel,
    onNavigateToTasks: () -> Unit
) {
    val pomodoroState by viewModel.pomodoroState.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val citraTimerBallsJson by viewModel.citraTimerBallsJson.collectAsState()
    val isDistractionFreeEnabled by viewModel.focusDistractionFreeEnabled.collectAsState()
    val scrollState = rememberScrollState()

    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showFocusDistractionDialog by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showSelectCategoryBeforeStartDialog by remember { mutableStateOf(false) }
    var isCategoryTrashVisible by remember { mutableStateOf(false) }
    var categoryToDelete by remember { mutableStateOf<com.example.data.model.Category?>(null) }
    var isEditingTimeInline by remember { mutableStateOf(false) }
    var inlineMinutesText by remember { mutableStateOf(pomodoroState.focusMinutes.toString()) }

    LaunchedEffect(pomodoroState.focusMinutes) {
        if (!isEditingTimeInline) {
            inlineMinutesText = pomodoroState.focusMinutes.toString()
        }
    }

    val focusPresets = remember { listOf(10, 15, 20, 25, 30, 45, 60, 90, 120) }
    val breakPresets = remember { listOf(5, 10, 15, 20, 25, 30) }
    val cyclePresets = remember { listOf(1, 2, 3, 4, 6, 8, 12) }

    val isRunning = pomodoroState.isRunning
    val isSessionActive = pomodoroState.isRunning || pomodoroState.isPaused

    // Smoothly expand the citrus dial when entering immersive full-screen focus mode
    val dialScale by animateFloatAsState(
        targetValue = if (isSessionActive) 1.12f else 1.0f,
        animationSpec = tween(durationMillis = 450),
        label = "citrusDialExpand"
    )

    val selectedCatObj = remember(categories, pomodoroState.selectedCategory) {
        categories.find { it.name == pomodoroState.selectedCategory }
    }

    // Progress & time calculation for Timer vs Cronômetro
    val totalSec = pomodoroState.totalPhaseSeconds.coerceAtLeast(1)
    val currentProgress = if (pomodoroState.isStopwatchMode) {
        ((pomodoroState.stopwatchSeconds % 60).toFloat() / 60f).coerceIn(0.04f, 1f)
    } else {
        (pomodoroState.remainingSeconds.toFloat() / totalSec.toFloat()).coerceIn(0f, 1f)
    }
    val animatedProgress by animateFloatAsState(
        targetValue = currentProgress,
        animationSpec = tween(500),
        label = "citrusPomodoroProgress"
    )

    val seedCount = 12
    val visibleSeeds = (seedCount * currentProgress).roundToInt().coerceIn(0, seedCount)

    val displaySeconds = if (pomodoroState.isStopwatchMode) {
        pomodoroState.stopwatchSeconds
    } else {
        pomodoroState.remainingSeconds
    }
    val minutes = displaySeconds / 60
    val seconds = displaySeconds % 60
    val formattedTime = String.format("%02d:%02d", minutes, seconds)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Os Citras surgem exclusivamente quando o timer DE PAUSA estiver contando ativamente
        val isTimerInBreakCounting = !pomodoroState.isStopwatchMode &&
            pomodoroState.currentPhase == PomodoroPhase.BREAK &&
            pomodoroState.isRunning
        AnimatedVisibility(
            visible = isTimerInBreakCounting && citraTimerBallsJson.isNotBlank() && citraTimerBallsJson != "[]",
            enter = fadeIn(tween(350)),
            exit = fadeOut(tween(280))
        ) {
            CitraPhysicsOverlay(
                ballsJson = citraTimerBallsJson,
                onBallsChanged = { viewModel.saveTimerCitraBallsJson(it) }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (!isSessionActive) Modifier.verticalScroll(scrollState) else Modifier)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (isSessionActive) Arrangement.Center else Arrangement.Top
        ) {
            // 1. IDLE TOP BANNER + OPTIONAL SETTINGS ICON (Fades out when active)
            AnimatedVisibility(
                visible = !isSessionActive,
                enter = fadeIn(tween(350)),
                exit = fadeOut(tween(280))
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = if (pomodoroState.currentPhase == PomodoroPhase.BREAK && !pomodoroState.isStopwatchMode) {
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            RoundedCornerShape(24.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = { showFocusDistractionDialog = true },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isDistractionFreeEnabled) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                            } else {
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                            },
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isDistractionFreeEnabled) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                }
                            ),
                            modifier = Modifier
                                .testTag("btn_focus_distraction_mode")
                                .clip(RoundedCornerShape(16.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = when {
                                        pomodoroState.isStopwatchMode -> "⏱️"
                                        pomodoroState.currentPhase == PomodoroPhase.BREAK -> "☕"
                                        isDistractionFreeEnabled -> "🛡️"
                                        else -> "🎯"
                                    },
                                    fontSize = 16.sp
                                )
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = when {
                                                pomodoroState.isStopwatchMode -> "Cronômetro"
                                                pomodoroState.currentPhase == PomodoroPhase.BREAK -> "Pausa"
                                                isDistractionFreeEnabled -> "Sem Perturbações"
                                                else -> "Modo Foco"
                                            },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.5.sp,
                                            color = if (isDistractionFreeEnabled) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                        Icon(
                                            imageVector = Icons.Default.Tune,
                                            contentDescription = "Configurar Modo Sem Perturbações",
                                            modifier = Modifier.size(13.dp),
                                            tint = if (isDistractionFreeEnabled) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            }
                                        )
                                    }
                                    if (isDistractionFreeEnabled && !pomodoroState.isStopwatchMode && pomodoroState.currentPhase != PomodoroPhase.BREAK) {
                                        Text(
                                            text = "Bloqueio de apps ativo",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (pomodoroState.currentPhase == PomodoroPhase.BREAK && !pomodoroState.isStopwatchMode) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable { viewModel.skipOptionalBreak() }
                                ) {
                                    Text(
                                        text = "Pular",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            } else {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                                ) {
                                    Text(
                                        text = if (pomodoroState.isStopwatchMode) {
                                            "Sem ciclos"
                                        } else {
                                            "Ciclo ${pomodoroState.currentCycle} de ${pomodoroState.totalCycles}"
                                        },
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                    )
                                }
                            }

                            // Hidden optional settings icon button
                            if (!pomodoroState.isStopwatchMode) {
                                IconButton(
                                    onClick = { showSettingsSheet = true },
                                    modifier = Modifier
                                        .size(34.dp)
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), CircleShape)
                                        .tutorialTarget("timer_settings_btn")
                                        .testTag("pomodoro_optional_settings_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = "Definições da sessão",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. IMMERSIVE CATEGORY TAG ABOVE DIAL (Fades in when active)
            AnimatedVisibility(
                visible = isSessionActive,
                enter = fadeIn(tween(400)),
                exit = fadeOut(tween(250))
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .padding(bottom = 28.dp)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                            CircleShape
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = selectedCatObj?.emoji ?: if (pomodoroState.isStopwatchMode) "⏱️" else "🎯",
                            fontSize = 16.sp
                        )
                        Text(
                            text = pomodoroState.selectedCategory.ifBlank {
                                if (pomodoroState.isStopwatchMode) "Cronômetro" else "Foco"
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            if (!isSessionActive) {
                Spacer(modifier = Modifier.height(22.dp))
            }

            // 3. CITRUS DIAL (Expands slightly when active)
            Box(
                modifier = Modifier
                    .size(276.dp)
                    .scale(dialScale)
                    .tutorialTarget("timer_circle_display")
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                val strokeColor = if (pomodoroState.currentPhase == PomodoroPhase.BREAK && !pomodoroState.isStopwatchMode) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.primary
                }
                val strokeSecondary = MaterialTheme.colorScheme.tertiary

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val peelThickness = 14.dp.toPx()
                    val diameter = size.minDimension - peelThickness
                    val centerOffset = Offset(size.width / 2, size.height / 2)
                    val outerRadius = diameter / 2
                    val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
                    val arcSize = Size(diameter, diameter)

                    // Soft citrus pulp background
                    drawCircle(
                        color = strokeColor.copy(alpha = 0.10f),
                        radius = outerRadius,
                        center = centerOffset
                    )

                    // 12 Citrus segment membrane lines
                    val segmentCount = 12
                    for (s in 0 until segmentCount) {
                        val segAngle = Math.toRadians((s * 360.0 / segmentCount) - 90.0)
                        val segStartX = centerOffset.x + (40.dp.toPx() * kotlin.math.cos(segAngle)).toFloat()
                        val segStartY = centerOffset.y + (40.dp.toPx() * kotlin.math.sin(segAngle)).toFloat()
                        val segEndX = centerOffset.x + ((outerRadius - peelThickness) * kotlin.math.cos(segAngle)).toFloat()
                        val segEndY = centerOffset.y + ((outerRadius - peelThickness) * kotlin.math.sin(segAngle)).toFloat()
                        drawLine(
                            color = strokeColor.copy(alpha = 0.20f),
                            start = Offset(segStartX, segStartY),
                            end = Offset(segEndX, segEndY),
                            strokeWidth = 1.4.dp.toPx()
                        )
                    }

                    // Inner pith rim
                    drawCircle(
                        color = strokeSecondary.copy(alpha = 0.35f),
                        radius = outerRadius - peelThickness + 1.dp.toPx(),
                        center = centerOffset,
                        style = Stroke(width = 2.dp.toPx())
                    )

                    // Background rind track
                    drawArc(
                        color = trackColor,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = peelThickness, cap = StrokeCap.Round)
                    )

                    // Active Citrus Peel progress arc
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

                    // 12 Analog Citrus Seeds
                    val seedRadius = outerRadius - peelThickness - 18.dp.toPx()
                    for (i in 0 until seedCount) {
                        if (i < visibleSeeds) {
                            val angleDeg = (i * 360.0 / seedCount) - 90.0
                            val angleRad = Math.toRadians(angleDeg)
                            val sx = centerOffset.x + (seedRadius * kotlin.math.cos(angleRad)).toFloat()
                            val sy = centerOffset.y + (seedRadius * kotlin.math.sin(angleRad)).toFloat()

                            rotate(degrees = angleDeg.toFloat() + 90f, pivot = Offset(sx, sy)) {
                                drawOval(
                                    color = strokeSecondary.copy(alpha = 0.25f),
                                    topLeft = Offset(sx - 3.5.dp.toPx(), sy - 6.5.dp.toPx()),
                                    size = Size(7.dp.toPx(), 13.dp.toPx())
                                )
                                drawOval(
                                    color = strokeColor,
                                    topLeft = Offset(sx - 3.5.dp.toPx(), sy - 6.5.dp.toPx()),
                                    size = Size(7.dp.toPx(), 13.dp.toPx()),
                                    style = Stroke(width = 1.4.dp.toPx())
                                )
                            }
                        }
                    }

                    // Core circle
                    drawCircle(
                        color = strokeColor.copy(alpha = 0.20f),
                        radius = 38.dp.toPx(),
                        center = centerOffset
                    )
                }

                // Digital Time Display at center
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isEditingTimeInline && !isSessionActive && !pomodoroState.isStopwatchMode) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            BasicTextField(
                                value = inlineMinutesText,
                                onValueChange = { input ->
                                    val digits = input.filter { it.isDigit() }.take(3)
                                    inlineMinutesText = digits
                                    val parsed = digits.toIntOrNull()
                                    if (parsed != null && parsed in 1..180) {
                                        viewModel.setFocusDuration(parsed)
                                    }
                                },
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(
                                    fontSize = 44.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center
                                ),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        val finalM = (inlineMinutesText.toIntOrNull() ?: pomodoroState.focusMinutes).coerceIn(1, 180)
                                        viewModel.setFocusDuration(finalM)
                                        inlineMinutesText = finalM.toString()
                                        isEditingTimeInline = false
                                    }
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier.widthIn(min = 64.dp, max = 104.dp)
                            )
                            Text(
                                text = "m",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        Text(
                            text = formattedTime,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-1).sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = !isSessionActive && !pomodoroState.isStopwatchMode) {
                                    inlineMinutesText = (pomodoroState.remainingSeconds / 60).coerceAtLeast(1).toString()
                                    isEditingTimeInline = true
                                }
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // 4. IDLE SUBTITLE & 3-BUTTON CONTROLS (Reset | Play | Cronômetro/Timer)
            AnimatedVisibility(
                visible = !isSessionActive,
                enter = fadeIn(tween(350)),
                exit = fadeOut(tween(280))
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(modifier = Modifier.height(10.dp))
                    if (pomodoroState.isStopwatchMode) {
                        Text(
                            text = "⏱️ Cronômetro livre • Sobe até pausar",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (!pomodoroState.isStopwatchMode && pomodoroState.currentPhase != PomodoroPhase.BREAK) {
                        Spacer(modifier = Modifier.height(8.dp))
                        FluidMinutesScrollPicker(
                            selectedMinutes = pomodoroState.focusMinutes,
                            onMinuteSelected = { mins ->
                                viewModel.setFocusDuration(mins)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    if (pomodoroState.currentPhase == PomodoroPhase.BREAK && !pomodoroState.isStopwatchMode) {
                        val hasPendingSessions = pomodoroState.currentCycle < pomodoroState.totalCycles
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. Interromper / Parar Button (asks confirmation only if pending sessions exist in cycle)
                            OutlinedButton(
                                onClick = {
                                    if (hasPendingSessions) {
                                        showResetConfirmDialog = true
                                    } else {
                                        viewModel.stopCycle()
                                    }
                                },
                                shape = CircleShape,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = RedAction),
                                border = androidx.compose.foundation.BorderStroke(1.5.dp, RedAction.copy(alpha = 0.5f)),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                modifier = Modifier
                                    .height(52.dp)
                                    .testTag("pomodoro_break_stop_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "Interromper Pausa",
                                    tint = RedAction,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Parar", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = RedAction)
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            // 2. Iniciar Pausa Button
                            Button(
                                onClick = { viewModel.startBreakCountdown() },
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 5.dp),
                                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
                                modifier = Modifier
                                    .height(54.dp)
                                    .testTag("pomodoro_start_break_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Iniciar Pausa",
                                    tint = MaterialTheme.colorScheme.onSecondary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Iniciar Pausa", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondary)
                            }

                            // 3. Pular para próxima sessão (se definido mais de um ciclo e houver sessões restantes)
                            if (pomodoroState.totalCycles > 1 && hasPendingSessions) {
                                Spacer(modifier = Modifier.width(14.dp))
                                FilledTonalButton(
                                    onClick = { viewModel.skipToNextSession() },
                                    shape = CircleShape,
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                    modifier = Modifier
                                        .height(52.dp)
                                        .testTag("pomodoro_skip_to_next_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SkipNext,
                                        contentDescription = "Pular para Próxima Sessão",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "Próxima",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left: Reset Button
                            IconButton(
                                onClick = { viewModel.resetPomodoro() },
                                modifier = Modifier
                                    .size(58.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f), CircleShape)
                                    .testTag("pomodoro_reset_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Reiniciar",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(26.dp))

                            // Center: Primary Play Button
                            Button(
                                onClick = {
                                    if (pomodoroState.selectedCategory.isBlank()) {
                                        showSelectCategoryBeforeStartDialog = true
                                    } else {
                                        viewModel.toggleStartPausePomodoro()
                                    }
                                },
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier
                                    .size(84.dp)
                                    .testTag("pomodoro_play_pause_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Iniciar",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(44.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(26.dp))

                            // Right: Cronômetro / Timer Mode Toggle Button
                            IconButton(
                                onClick = { viewModel.toggleStopwatchMode() },
                                modifier = Modifier
                                    .size(58.dp)
                                    .background(
                                        color = if (pomodoroState.isStopwatchMode) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
                                        },
                                        shape = CircleShape
                                    )
                                    .border(
                                        width = if (pomodoroState.isStopwatchMode) 1.5.dp else 0.dp,
                                        color = if (pomodoroState.isStopwatchMode) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            Color.Transparent
                                        },
                                        shape = CircleShape
                                    )
                                    .tutorialTarget("timer_mode_toggle")
                                    .testTag("pomodoro_stopwatch_toggle_btn")
                            ) {
                                Icon(
                                    imageVector = if (pomodoroState.isStopwatchMode) {
                                        Icons.Default.HourglassBottom
                                    } else {
                                        Icons.Default.Timer
                                    },
                                    contentDescription = if (pomodoroState.isStopwatchMode) {
                                        "Voltar para Timer"
                                    } else {
                                        "Mudar para Cronômetro"
                                    },
                                    tint = if (pomodoroState.isStopwatchMode) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Category Selector Card
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                RoundedCornerShape(24.dp)
                            )
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
                                Text(
                                    text = "Categoria",
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isCategoryTrashVisible) {
                                        TextButton(
                                            onClick = { isCategoryTrashVisible = false },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                        ) {
                                            Text("Concluir", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        TextButton(
                                            onClick = { isCategoryTrashVisible = true },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                        ) {
                                            Text(
                                                "Gerenciar",
                                                fontSize = 11.5.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    TextButton(
                                        onClick = { showAddCategoryDialog = true },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Add,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            "Nova",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(categories, key = { it.name }) { cat ->
                                    val isSelected = pomodoroState.selectedCategory == cat.name
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                        },
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .border(
                                                width = if (isSelected) 1.5.dp else 1.dp,
                                                color = if (isSelected) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                                },
                                                shape = RoundedCornerShape(16.dp)
                                            )
                                            .clickable { viewModel.setPomodoroCategory(cat.name) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(cat.emoji, fontSize = 15.sp)
                                            Text(
                                                text = cat.name,
                                                fontSize = 12.5.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                color = if (isSelected) {
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                }
                                            )
                                            if (isCategoryTrashVisible) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Remover",
                                                    tint = RedAction,
                                                    modifier = Modifier
                                                        .size(16.dp)
                                                        .clickable { categoryToDelete = cat }
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

            // 5. IMMERSIVE ACTIVE CONTROLS: ONLY PAUSE/RESUME & INTERROMPER
            AnimatedVisibility(
                visible = isSessionActive,
                enter = fadeIn(tween(400)),
                exit = fadeOut(tween(250))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 42.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pause / Resume Button
                    Button(
                        onClick = { viewModel.toggleStartPausePomodoro() },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
                        modifier = Modifier
                            .height(54.dp)
                            .testTag("pomodoro_play_pause_button")
                    ) {
                        Icon(
                            imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isRunning) "Pausar" else "Retomar",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isRunning) "Pausar" else "Retomar",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }

                    // Interromper (Stop) Button
                    OutlinedButton(
                        onClick = {
                            val hasPendingSessions = !pomodoroState.isStopwatchMode && pomodoroState.currentCycle < pomodoroState.totalCycles
                            if (hasPendingSessions) {
                                showResetConfirmDialog = true
                            } else {
                                viewModel.resetPomodoro()
                            }
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = RedAction
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.5.dp,
                            RedAction.copy(alpha = 0.5f)
                        ),
                        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp),
                        modifier = Modifier
                            .height(54.dp)
                            .testTag("pomodoro_interrupt_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Interromper",
                            tint = RedAction,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Interromper",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = RedAction
                        )
                    }
                }
            }
        }
    }

    // Optional Session Settings Bottom Sheet (Focus Duration, Optional Break, Cycles)
    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Text(
                    text = "Definições da Sessão",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Tempo de Foco
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Tempo de Foco", fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "${pomodoroState.focusMinutes} min",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(focusPresets) { mins ->
                            FilterChip(
                                selected = pomodoroState.focusMinutes == mins,
                                onClick = { viewModel.setFocusDuration(mins) },
                                label = { Text("${mins}m", fontSize = 12.sp) }
                            )
                        }
                    }
                }

                // Tempo de Pausa (Opcional)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Pausa (Opcional)", fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "${pomodoroState.breakMinutes} min",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(breakPresets) { mins ->
                            FilterChip(
                                selected = pomodoroState.breakMinutes == mins,
                                onClick = { viewModel.setBreakDuration(mins) },
                                label = { Text("${mins}m", fontSize = 12.sp) }
                            )
                        }
                    }
                }

                // Ciclos de Repetição
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Ciclos de Repetição", fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "${pomodoroState.totalCycles}x",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(cyclePresets) { cycles ->
                            FilterChip(
                                selected = pomodoroState.totalCycles == cycles,
                                onClick = { viewModel.setTotalCycles(cycles) },
                                label = { Text("${cycles}x", fontSize = 12.sp) }
                            )
                        }
                    }
                }

                Button(
                    onClick = { showSettingsSheet = false },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Concluir", fontWeight = FontWeight.Bold)
                }
            }
        }
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

    if (showFocusDistractionDialog) {
        FocusDistractionDialog(
            preferencesManager = viewModel.preferencesManager,
            onDismiss = { showFocusDistractionDialog = false }
        )
    }

    // Confirm Interromper (Stop & Reset)
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            icon = { Text("🍊", fontSize = 32.sp) },
            title = {
                Text(
                    text = if (pomodoroState.isStopwatchMode) "Encerrar Cronômetro?" else "Interromper Sessão?",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = if (pomodoroState.isStopwatchMode) {
                        "Deseja parar e zerar o cronômetro atual?"
                    } else {
                        "Deseja mesmo interromper e zerar esta sessão de foco?"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
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
                        Text("Continuar", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = {
                            showResetConfirmDialog = false
                            viewModel.resetPomodoro()
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Interromper", color = RedAction, fontWeight = FontWeight.Bold)
                    }
                }
            }
        )
    }

    // Select Category Before Starting
    if (showSelectCategoryBeforeStartDialog) {
        AlertDialog(
            onDismissRequest = { showSelectCategoryBeforeStartDialog = false },
            icon = { Text("🎯", fontSize = 32.sp) },
            title = {
                Text(
                    text = "Defina a Categoria",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Toque em uma categoria para iniciar imediatamente:",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(categories, key = { it.name }) { cat ->
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
                    Text("Continuar", color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        )
    }
}

@Composable
fun FluidMinutesScrollPicker(
    selectedMinutes: Int,
    onMinuteSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val rawValues = remember { (10..120 step 5).toList() }
    val minuteList = remember(selectedMinutes) {
        if (selectedMinutes in 10..120 && selectedMinutes !in rawValues) {
            (rawValues + selectedMinutes).sorted()
        } else {
            rawValues
        }
    }
    val listState = rememberLazyListState()
    val flingBehavior = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(lazyListState = listState)

    // Dynamically track and select whichever item is closest to the center while dragging/sliding
    LaunchedEffect(listState, minuteList) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val center = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
            layoutInfo.visibleItemsInfo.minByOrNull { item ->
                val itemCenter = item.offset + item.size / 2
                kotlin.math.abs(itemCenter - center)
            }?.index
        }
        .filterNotNull()
        .distinctUntilChanged()
        .collect { centerIndex: Int ->
            if (listState.isScrollInProgress) {
                val mins = minuteList.getOrNull(centerIndex)
                if (mins != null && mins != selectedMinutes) {
                    onMinuteSelected(mins)
                }
            }
        }
    }

    // Smoothly scroll to center the selected minute when not actively dragging
    LaunchedEffect(selectedMinutes) {
        if (!listState.isScrollInProgress) {
            val targetIdx = minuteList.indexOf(selectedMinutes)
            if (targetIdx >= 0) {
                listState.animateScrollToItem(targetIdx)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val horizontalPadding = (maxWidth / 2) - 34.dp

            LazyRow(
                state = listState,
                flingBehavior = flingBehavior,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                contentPadding = PaddingValues(horizontal = horizontalPadding),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                items(minuteList, key = { it }) { mins ->
                    val isSelected = mins == selectedMinutes
                    val distance = kotlin.math.abs(mins - selectedMinutes)
                    val targetAlpha = when {
                        isSelected -> 1f
                        distance <= 5 -> 0.65f
                        distance <= 10 -> 0.40f
                        distance <= 20 -> 0.25f
                        else -> 0.15f
                    }
                    val alpha by animateFloatAsState(targetValue = targetAlpha, label = "alpha_$mins")
                    val scale by animateFloatAsState(
                        targetValue = if (isSelected) 1.18f else 0.90f,
                        label = "scale_$mins"
                    )

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        },
                        shadowElevation = if (isSelected) 4.dp else 0.dp,
                        modifier = Modifier
                            .scale(scale)
                            .clip(RoundedCornerShape(16.dp))
                            .border(
                                width = if (isSelected) 2.dp else 0.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                onMinuteSelected(mins)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "${mins}m",
                                fontSize = if (isSelected) 16.sp else 13.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

