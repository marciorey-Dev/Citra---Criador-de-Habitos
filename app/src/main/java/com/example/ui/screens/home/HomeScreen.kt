package com.example.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.Task
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.example.util.squashBounce
import com.example.util.squashBounceClickable
import com.example.util.staggeredEntrance
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.data.model.TaskPriority
import com.example.data.model.isReadyForMainList
import com.example.ui.MainViewModel
import com.example.ui.components.CitraMascote
import com.example.ui.components.MascotHumor
import com.example.ui.theme.GreenAccent
import com.example.ui.theme.RedAction
import com.example.ui.tutorial.tutorialTarget
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToTimer: () -> Unit,
    onNavigateToOrganization: () -> Unit,
    onQuickAddTask: () -> Unit,
    onQuickAddNote: () -> Unit,
    isInitialLaunch: Boolean = false
) {
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val completingTaskIds = remember { mutableStateListOf<Long>() }
    val completingHabitIds = remember { mutableStateListOf<Long>() }

    fun completeTaskAnimated(task: Task) {
        if (task.id in completingTaskIds) return
        completingTaskIds.add(task.id)
        try {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        } catch (_: Throwable) {}
        coroutineScope.launch {
            delay(320)
            viewModel.toggleTask(task)
            completingTaskIds.remove(task.id)
        }
    }
    val tasks by viewModel.tasks.collectAsState()
    val habits by viewModel.habits.collectAsState()
    val pomodoroState by viewModel.pomodoroState.collectAsState()
    val welcomePhrasesEnabled by viewModel.welcomePhrasesEnabled.collectAsState()
    val dailyWelcomePhrase by viewModel.dailyWelcomePhrase.collectAsState()
    val todayStr = viewModel.todayString

    val pendingTasks = remember(tasks) { tasks.filter { !it.isCompleted && it.isReadyForMainList() } }
    val currentTask = pendingTasks.firstOrNull()
    val nextTasks = pendingTasks.drop(1).take(3)

    val dateDisplay = remember {
        val sdf = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("pt", "BR"))
        sdf.format(Date()).replaceFirstChar { it.uppercase() }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        // 1. Greeting & Date Header
        item {
            Column(modifier = Modifier.staggeredEntrance(index = 0, enabled = isInitialLaunch)) {
                Text(
                    text = dateDisplay,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (welcomePhrasesEnabled) {
                        dailyWelcomePhrase
                    } else if (pendingTasks.isNotEmpty()) {
                        "Pronto para focar?"
                    } else {
                        "Tudo em dia por hoje!"
                    },
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // 2. Hero Focus Card (Active Session OR Start Focus Banner)
        item {
            val primaryColor = MaterialTheme.colorScheme.primary
            val tertiaryColor = MaterialTheme.colorScheme.tertiary

            Box(modifier = Modifier.staggeredEntrance(index = 1, enabled = isInitialLaunch)) {
                if (pomodoroState.isRunning || pomodoroState.isPaused) {
                    val mins = pomodoroState.remainingSeconds / 60
                    val secs = pomodoroState.remainingSeconds % 60
                    val timeStr = String.format("%02d:%02d", mins, secs)

                    Card(
                        shape = RoundedCornerShape(26.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .tutorialTarget("home_pomodoro_card")
                            .squashBounceClickable { onNavigateToTimer() }
                            .testTag("home_active_timer_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .background(primaryColor, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                CitraMascote(
                                    humor = MascotHumor.FELIZ,
                                    olharX = 0f,
                                    olharY = 0f,
                                    tamanho = 34.dp
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (pomodoroState.isPaused) "Sessão Pausada" else "Sessão em Andamento",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = timeStr,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                if (pomodoroState.linkedTask != null) {
                                    Text(
                                        text = "Foco em: ${pomodoroState.linkedTask!!.title}",
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Button(
                                onClick = { onNavigateToTimer() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = primaryColor,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                modifier = Modifier.squashBounce()
                            ) {
                                Text("Abrir", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(26.dp),
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .tutorialTarget("home_pomodoro_card")
                            .clip(RoundedCornerShape(26.dp))
                            .squashBounceClickable { onNavigateToTimer() }
                            .testTag("home_start_focus_btn")
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            primaryColor,
                                            primaryColor.copy(alpha = 0.92f),
                                            tertiaryColor.copy(alpha = 0.9f)
                                        )
                                    )
                                )
                        ) {
                            // Subtle ambient decorative circles
                            Box(
                                modifier = Modifier
                                    .size(120.dp)
                                    .align(Alignment.TopEnd)
                                    .offset(x = 28.dp, y = (-28).dp)
                                    .background(Color.White.copy(alpha = 0.10f), CircleShape)
                            )
                            Box(
                                modifier = Modifier
                                    .size(90.dp)
                                    .align(Alignment.BottomStart)
                                    .offset(x = (-20).dp, y = 24.dp)
                                    .background(Color.White.copy(alpha = 0.08f), CircleShape)
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CitraMascote(
                                            humor = MascotHumor.FELIZ,
                                            olharX = 0f,
                                            olharY = 0f,
                                            tamanho = 28.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Iniciar Sessão de Foco",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "${pomodoroState.focusMinutes} min de concentração  •  ${pomodoroState.totalCycles} ciclos",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .background(Color.White.copy(alpha = 0.24f), CircleShape)
                                        .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Iniciar foco",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Quick Actions Section ("O que fazer a seguir")
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .staggeredEntrance(index = 2, enabled = isInitialLaunch)
                    .tutorialTarget("home_tasks_shortcut"),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "O que fazer a seguir",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                                RoundedCornerShape(16.dp)
                            )
                            .squashBounceClickable { onQuickAddNote() }
                            .testTag("home_quick_add_note_btn")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NoteAdd,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Nova Nota",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                                RoundedCornerShape(16.dp)
                            )
                            .squashBounceClickable { onQuickAddTask() }
                            .testTag("home_quick_add_task_btn")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Nova Tarefa",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        }

        // 4. Empty Tasks Card OR Current & Upcoming Tasks
        if (pendingTasks.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .staggeredEntrance(index = 3, enabled = isInitialLaunch)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(24.dp)
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "✨", fontSize = 26.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Nenhuma tarefa pendente!",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Toque em '+ Nova Tarefa' para planejar seu dia.",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            if (currentTask != null) {
                item {
                    val priorityColor = when (currentTask.priority) {
                        TaskPriority.ALTA -> RedAction
                        TaskPriority.MEDIA -> MaterialTheme.colorScheme.primary
                        TaskPriority.BAIXA -> GreenAccent
                    }
                    val isCompleting = currentTask.id in completingTaskIds
                    val cardBg by animateColorAsState(
                        targetValue = if (isCompleting) Color(0xFFE8F7ED) else MaterialTheme.colorScheme.surface,
                        animationSpec = tween(280),
                        label = "currentTaskBg"
                    )
                    val cardBorder by animateColorAsState(
                        targetValue = if (isCompleting) Color(0xFF4ADE80) else priorityColor.copy(alpha = 0.45f),
                        animationSpec = tween(280),
                        label = "currentTaskBorder"
                    )
                    val taskTitleColor by animateColorAsState(
                        targetValue = if (isCompleting) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onSurface,
                        animationSpec = tween(280),
                        label = "currentTaskTitle"
                    )

                    AnimatedVisibility(
                        visible = currentTask.id !in completingTaskIds,
                        exit = shrinkVertically(tween(300)) + fadeOut(tween(250)),
                        modifier = Modifier.staggeredEntrance(index = 3, enabled = isInitialLaunch)
                    ) {
                        Card(
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.5.dp, cardBorder, RoundedCornerShape(22.dp))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isCompleting) Color(0xFF16A34A).copy(alpha = 0.15f) else priorityColor.copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = if (isCompleting) "CONCLUINDO..." else "TAREFA ATUAL • ${currentTask.priority.label.uppercase()}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (isCompleting) Color(0xFF16A34A) else priorityColor,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                    ) {
                                        Text(
                                            text = currentTask.category,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = currentTask.isCompleted || isCompleting,
                                        onCheckedChange = { completeTaskAnimated(currentTask) },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = Color(0xFF16A34A),
                                            checkmarkColor = Color.White
                                        ),
                                        modifier = Modifier.squashBounce()
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { completeTaskAnimated(currentTask) }
                                    ) {
                                        Text(
                                            text = currentTask.title,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            textDecoration = if (isCompleting || currentTask.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                                            color = taskTitleColor
                                        )
                                        if (currentTask.dueDateText.isNotBlank()) {
                                            Text(
                                                text = "📅 ${currentTask.dueDateText}",
                                                fontSize = 11.5.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            viewModel.linkTaskToPomodoro(currentTask)
                                            onNavigateToTimer()
                                        },
                                        modifier = Modifier
                                            .size(42.dp)
                                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                            .squashBounce()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Focar nesta tarefa",
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (nextTasks.isNotEmpty()) {
                item {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.staggeredEntrance(index = 3, enabled = isInitialLaunch)
                    ) {
                        nextTasks.forEach { task ->
                            val isTaskCompleting = task.id in completingTaskIds
                            val nextBg by animateColorAsState(
                                targetValue = if (isTaskCompleting) Color(0xFFE8F7ED) else MaterialTheme.colorScheme.surface,
                                animationSpec = tween(280),
                                label = "nextTaskBg"
                            )
                            val nextBorder by animateColorAsState(
                                targetValue = if (isTaskCompleting) Color(0xFF4ADE80) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                animationSpec = tween(280),
                                label = "nextTaskBorder"
                            )
                            val nextTitleColor by animateColorAsState(
                                targetValue = if (isTaskCompleting) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onSurface,
                                animationSpec = tween(280),
                                label = "nextTaskTitleColor"
                            )

                            AnimatedVisibility(
                                visible = task.id !in completingTaskIds,
                                exit = shrinkVertically(tween(300)) + fadeOut(tween(250))
                            ) {
                                Card(
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = nextBg),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, nextBorder, RoundedCornerShape(18.dp))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { completeTaskAnimated(task) }
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = task.isCompleted || isTaskCompleting,
                                            onCheckedChange = { completeTaskAnimated(task) },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = Color(0xFF16A34A),
                                                checkmarkColor = Color.White
                                            ),
                                            modifier = Modifier.squashBounce()
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = task.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            textDecoration = if (isTaskCompleting || task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                                            color = nextTitleColor,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = task.category,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (pendingTasks.size > 1) {
                item {
                    TextButton(
                        onClick = { onNavigateToOrganization() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .squashBounce()
                    ) {
                        Text(
                            "Ver todas as ${pendingTasks.size} tarefas pendentes →",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 5. Habits Section ("Hábitos de Hoje")
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .staggeredEntrance(index = 4, enabled = isInitialLaunch),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .tutorialTarget("home_habits_shortcut"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Hábitos de Hoje",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val completedToday = habits.count { h ->
                        h.completedDatesCsv.split(",").any { it.trim() == todayStr } || h.id in completingHabitIds
                    }
                    Text(
                        text = "$completedToday de ${habits.size} feitos",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (habits.isEmpty()) {
                    Text(
                        text = "Nenhum hábito cadastrado ainda.",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(habits, key = { it.id }) { habit ->
                            val isDone = habit.completedDatesCsv.split(",").any { it.trim() == todayStr } || habit.id in completingHabitIds
                            val habitBg by animateColorAsState(
                                targetValue = if (isDone) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.surface,
                                animationSpec = tween(300),
                                label = "habitBg"
                            )
                            val habitBorder by animateColorAsState(
                                targetValue = if (isDone) MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                animationSpec = tween(300),
                                label = "habitBorder"
                            )

                            Card(
                                shape = RoundedCornerShape(22.dp),
                                colors = CardDefaults.cardColors(containerColor = habitBg),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                modifier = Modifier
                                    .width(156.dp)
                                    .clip(RoundedCornerShape(22.dp))
                                    .border(width = 1.dp, color = habitBorder, shape = RoundedCornerShape(22.dp))
                                    .squashBounceClickable {
                                        completingHabitIds.add(habit.id)
                                        try {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        } catch (_: Throwable) {}
                                        coroutineScope.launch {
                                            delay(250)
                                            viewModel.toggleHabitToday(habit)
                                            completingHabitIds.remove(habit.id)
                                        }
                                    }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(text = habit.iconEmoji, fontSize = 18.sp)
                                            Text(
                                                text = habit.reminderTime,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isDone) {
                                                    MaterialTheme.colorScheme.onSecondaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                        }

                                        AnimatedContent(
                                            targetState = isDone,
                                            transitionSpec = {
                                                scaleIn(tween(250)) togetherWith scaleOut(tween(200))
                                            },
                                            label = "habitDoneIcon"
                                        ) { done ->
                                            if (done) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "Concluído",
                                                    tint = MaterialTheme.colorScheme.secondary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.RadioButtonUnchecked,
                                                    contentDescription = "Pendente",
                                                    tint = MaterialTheme.colorScheme.outlineVariant,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Text(
                                        text = habit.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp,
                                        lineHeight = 18.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (isDone) {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )

                                    Spacer(modifier = Modifier.height(14.dp))

                                    Text(
                                        text = "🔥 ${habit.currentStreak} dias",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDone) {
                                            MaterialTheme.colorScheme.secondary
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        }
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
