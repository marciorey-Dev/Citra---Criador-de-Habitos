package com.example.ui.screens.organization

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Category
import com.example.data.model.Task
import com.example.data.model.TaskPriority
import com.example.ui.MainViewModel
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.tutorial.tutorialTarget
import java.text.SimpleDateFormat
import java.util.*

@Composable
private fun taskCategoryIconFor(category: String): ImageVector {
    return when (category.lowercase().trim()) {
        "estudos", "study" -> Icons.Default.MenuBook
        "criatividade", "ideias" -> Icons.Default.Palette
        "comunidade", "social" -> Icons.Default.VolunteerActivism
        "saúde", "fitness" -> Icons.Default.Eco
        "trabalho", "work", "finanças", "logística" -> Icons.Default.Payments
        else -> Icons.Default.Label
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksSubTab(
    viewModel: MainViewModel,
    onStartPomodoroForTask: (Task) -> Unit
) {
    val tasks by viewModel.tasks.collectAsState()
    val categories by viewModel.categories.collectAsState()
    var filterMode by remember { mutableStateOf("PENDING") } // ALL, PENDING, COMPLETED
    var showAddDialog by remember { mutableStateOf(false) }
    var taskToEdit by remember { mutableStateOf<Task?>(null) }
    var taskToDelete by remember { mutableStateOf<Task?>(null) }

    val pendingCount = remember(tasks) { tasks.count { !it.isCompleted } }
    val completedCount = remember(tasks) { tasks.count { it.isCompleted } }
    val completionPercent = remember(tasks, completedCount) {
        if (tasks.isEmpty()) 0 else (completedCount * 100) / tasks.size
    }

    val filteredTasks = remember(tasks, filterMode) {
        when (filterMode) {
            "PENDING" -> tasks.filter { !it.isCompleted }
            "COMPLETED" -> tasks.filter { it.isCompleted }
            else -> tasks
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 6.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Secondary Pill Filter Chips
            item {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .tutorialTarget("tasks_filter_row"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        TaskFilterPill(
                            label = "Pendentes ($pendingCount)",
                            selected = filterMode == "PENDING",
                            showActiveDot = true,
                            onClick = { filterMode = "PENDING" }
                        )
                    }
                    item {
                        TaskFilterPill(
                            label = "Todas (${tasks.size})",
                            selected = filterMode == "ALL",
                            showActiveDot = false,
                            onClick = { filterMode = "ALL" }
                        )
                    }
                    item {
                        TaskFilterPill(
                            label = "Concluídas ($completedCount)",
                            selected = filterMode == "COMPLETED",
                            showActiveDot = false,
                            onClick = { filterMode = "COMPLETED" }
                        )
                    }
                }
            }

            // Warm Motivation Banner
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                                        RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Eco,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = when (pendingCount) {
                                        0 -> "Todas as tarefas concluídas!"
                                        1 -> "1 tarefa para completar hoje"
                                        else -> "$pendingCount tarefas para completar hoje"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Cultive seu foco como uma fruta doce.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "$completionPercent%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            if (filteredTasks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp)
                            .tutorialTarget("tasks_list_first_item")
                            .tutorialTarget("tasks_checkbox_area"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.CheckCircleOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (filterMode == "COMPLETED") "Nenhuma tarefa concluída ainda." else "Tudo em dia! Nenhuma tarefa pendente.",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Toque no '+' para criação rápida de tarefas.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            } else {
                items(filteredTasks, key = { it.id }) { task ->
                    val isFirst = filteredTasks.firstOrNull()?.id == task.id
                    TaskItemCard(
                        task = task,
                        isFirst = isFirst,
                        onToggle = { viewModel.toggleTask(task) },
                        onEdit = { taskToEdit = task },
                        onPlay = { onStartPomodoroForTask(task) },
                        onDelete = { taskToDelete = task }
                    )
                }
            }
        }

        // Warm Tactile Circular FAB
        FloatingActionButton(
            onClick = { showAddDialog = true },
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(56.dp)
                .tutorialTarget("tasks_add_button")
                .testTag("add_task_fab")
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Adicionar Tarefa",
                modifier = Modifier.size(28.dp)
            )
        }
    }

    // Quick Task Creation Dialog (Essential fields: Name & Date only)
    if (showAddDialog) {
        QuickAddTaskDialog(
            categories = categories,
            onDismiss = { showAddDialog = false },
            onConfirm = { title, category, priority, dueText, dueTimestamp, minutesBefore, soundUri, loop ->
                viewModel.addTask(title, category, priority, dueText, dueTimestamp, minutesBefore, soundUri, loop)
                showAddDialog = false
            }
        )
    }

    if (taskToEdit != null) {
        EditTaskDialog(
            task = taskToEdit!!,
            categories = categories,
            onDismiss = { taskToEdit = null },
            onConfirm = { updatedTask ->
                viewModel.updateTask(updatedTask)
                taskToEdit = null
            }
        )
    }

    // Confirmation dialog before deletion
    if (taskToDelete != null) {
        ConfirmDeleteDialog(
            title = "Excluir Tarefa",
            message = "Deseja realmente excluir '${taskToDelete!!.title}'? Esta ação não pode ser desfeita.",
            onConfirm = {
                viewModel.deleteTask(taskToDelete!!)
                taskToDelete = null
            },
            onDismiss = { taskToDelete = null }
        )
    }
}

@Composable
private fun TaskFilterPill(
    label: String,
    selected: Boolean,
    showActiveDot: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    }
    val textColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = CircleShape,
        color = bgColor,
        shadowElevation = if (selected) 1.dp else 0.dp,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (selected && showActiveDot) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(MaterialTheme.colorScheme.secondary, CircleShape)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = textColor
            )
        }
    }
}

@Composable
fun TaskItemCard(
    task: Task,
    isFirst: Boolean = false,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    val (priorityBg, priorityFg, priorityDot) = when (task.priority) {
        TaskPriority.ALTA -> Triple(
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.error
        )
        TaskPriority.MEDIA -> Triple(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.tertiary
        )
        TaskPriority.BAIXA -> Triple(
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
            MaterialTheme.colorScheme.onSecondaryContainer,
            MaterialTheme.colorScheme.secondary
        )
    }

    val categoryBg = when (task.category.lowercase().trim()) {
        "estudos", "study" -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
        "criatividade", "ideias", "comunidade" -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    }
    val categoryFg = when (task.category.lowercase().trim()) {
        "estudos", "study" -> MaterialTheme.colorScheme.onSecondaryContainer
        "criatividade", "ideias", "comunidade" -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (task.isCompleted) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (task.isCompleted) 0.dp else 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isFirst) Modifier.tutorialTarget("tasks_list_first_item") else Modifier)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f),
                shape = RoundedCornerShape(20.dp)
            )
            .clip(RoundedCornerShape(20.dp))
            .clickable { onEdit() }
            .testTag("task_item_${task.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Row: Tactile Checkbox + Title + Play/Delete Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Tactile Soft Rounded Checkbox
                    Box(
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(24.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (task.isCompleted) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
                                }
                            )
                            .border(
                                width = 1.dp,
                                color = if (task.isCompleted) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                                },
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { onToggle() }
                            .then(if (isFirst) Modifier.tutorialTarget("tasks_checkbox_area") else Modifier)
                            .testTag("task_checkbox_${task.id}"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (task.isCompleted) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Concluída",
                                tint = MaterialTheme.colorScheme.onSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                            color = if (task.isCompleted) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (task.focusMinutesCompleted > 0) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${task.focusMinutesCompleted} min de foco registrados",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Quick Actions: Circular Play + Delete
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!task.isCompleted) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shadowElevation = 1.dp,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable { onPlay() }
                                .testTag("task_play_${task.id}")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Iniciar Timer",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Excluir Tarefa",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Bottom Row: Category Pill + Priority Pill + Due Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    // Category Pill Chip
                    Surface(
                        shape = CircleShape,
                        color = categoryBg
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = taskCategoryIconFor(task.category),
                                contentDescription = null,
                                tint = categoryFg,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = task.category,
                                style = MaterialTheme.typography.labelSmall,
                                color = categoryFg,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Priority Pill Chip
                    Surface(
                        shape = CircleShape,
                        color = priorityBg
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(priorityDot, CircleShape)
                            )
                            Text(
                                text = task.priority.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = priorityFg
                            )
                        }
                    }
                }

                // Due Date
                if (task.dueDateText.isNotBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = task.dueDateText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

// A. CRIAÇÃO RÁPIDA DE TAREFAS: Essencial é apenas Nome e Data
@Composable
fun QuickAddTaskDialog(
    categories: List<Category>,
    initialTitle: String = "",
    onDismiss: () -> Unit,
    onConfirm: (title: String, category: String, priority: TaskPriority, dueDateText: String, dueTimestamp: Long?, reminderMinutesBefore: Int) -> Unit
) {
    QuickAddTaskDialog(
        categories = categories,
        initialTitle = initialTitle,
        onDismiss = onDismiss,
        onConfirm = { title, category, priority, dueText, dueTimestamp, minutesBefore, _, _ ->
            onConfirm(title, category, priority, dueText, dueTimestamp, minutesBefore)
        }
    )
}

@Composable
fun QuickAddTaskDialog(
    categories: List<Category>,
    initialTitle: String = "",
    onDismiss: () -> Unit,
    onConfirm: (title: String, category: String, priority: TaskPriority, dueDateText: String, dueTimestamp: Long?, reminderMinutesBefore: Int, customSoundUri: String, loopAlarm: Boolean) -> Unit
) {
    val context = LocalContext.current
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }
    var category by remember { mutableStateOf(categories.firstOrNull()?.name ?: "Geral") }
    var priority by remember { mutableStateOf(TaskPriority.MEDIA) }

    val cal = remember { Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 2) } }
    var selectedTimestamp by remember { mutableStateOf<Long?>(cal.timeInMillis) }
    var dueDateText by remember {
        mutableStateOf(SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(cal.time))
    }
    var reminderMinutesBefore by remember { mutableStateOf(15) }
    var customSoundUri by remember { mutableStateOf("") }
    var loopAlarm by remember { mutableStateOf(false) }
    var showMoreOptions by remember { mutableStateOf(false) }

    fun openDateTimePicker() {
        val currentCal = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val timeCal = Calendar.getInstance()
                TimePickerDialog(
                    context,
                    { _, hourOfDay, minute ->
                        val picked = Calendar.getInstance().apply {
                            set(year, month, dayOfMonth, hourOfDay, minute, 0)
                        }
                        selectedTimestamp = picked.timeInMillis
                        dueDateText = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(picked.time)
                    },
                    timeCal.get(Calendar.HOUR_OF_DAY),
                    timeCal.get(Calendar.MINUTE),
                    true
                ).show()
            },
            currentCal.get(Calendar.YEAR),
            currentCal.get(Calendar.MONTH),
            currentCal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "⚡", fontSize = 22.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Nova Tarefa", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Campo essencial: Nome
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Nome da Tarefa *") },
                    placeholder = { Text("Ex: Ler capítulo 3...") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("task_title_input")
                )

                // 2. Campo essencial: Data
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { openDateTimePicker() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Event, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Prazo: $dueDateText", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                        Icon(Icons.Default.EditCalendar, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }

                // Opções opcionais (expansíveis)
                TextButton(
                    onClick = { showMoreOptions = !showMoreOptions },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = if (showMoreOptions) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (showMoreOptions) "Menos detalhes" else "+ Mais opções (Prioridade, Categoria, Alarme)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                AnimatedVisibility(visible = showMoreOptions) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Category selection
                        Text("Categoria:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(categories) { cat ->
                                FilterChip(
                                    selected = category == cat.name,
                                    onClick = { category = cat.name },
                                    label = { Text("${cat.emoji} ${cat.name}", fontSize = 11.sp) }
                                )
                            }
                        }

                        // Priority
                        Text("Prioridade:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TaskPriority.values().forEach { prio ->
                                val isSelected = priority == prio
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { priority = prio },
                                    label = { Text(prio.label) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // Lead reminder time
                        Text("Lembrar com antecedência:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(0 to "No prazo", 15 to "15m", 30 to "30m", 60 to "1h").forEach { (mins, lbl) ->
                                FilterChip(
                                    selected = reminderMinutesBefore == mins,
                                    onClick = { reminderMinutesBefore = mins },
                                    label = { Text(lbl, fontSize = 11.sp) }
                                )
                            }
                        }

                        // Opção de Toque em Loop
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Toque em Loop Contínuo", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("Toca som repetidamente até você dispensar", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = loopAlarm,
                                onCheckedChange = { loopAlarm = it }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onConfirm(title.trim(), category, priority, dueDateText, selectedTimestamp, reminderMinutesBefore, customSoundUri, loopAlarm)
                    }
                },
                enabled = title.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("save_task_btn")
            ) {
                Text("Salvar Tarefa", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) {
                Text("Cancelar")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
fun EditTaskDialog(
    task: Task,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onConfirm: (Task) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(task.title) }
    var category by remember { mutableStateOf(task.category) }
    var priority by remember { mutableStateOf(task.priority) }
    var dueDateText by remember { mutableStateOf(task.dueDateText) }
    var selectedTimestamp by remember { mutableStateOf(task.dueDateTimestamp) }
    var reminderMinutesBefore by remember { mutableStateOf(task.reminderMinutesBefore) }
    var customSoundUri by remember { mutableStateOf(task.customSoundUri) }
    var loopAlarm by remember { mutableStateOf(task.loopAlarm) }

    fun openDateTimePicker() {
        val currentCal = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val timeCal = Calendar.getInstance()
                TimePickerDialog(
                    context,
                    { _, hourOfDay, minute ->
                        val picked = Calendar.getInstance().apply {
                            set(year, month, dayOfMonth, hourOfDay, minute, 0)
                        }
                        selectedTimestamp = picked.timeInMillis
                        dueDateText = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(picked.time)
                    },
                    timeCal.get(Calendar.HOUR_OF_DAY),
                    timeCal.get(Calendar.MINUTE),
                    true
                ).show()
            },
            currentCal.get(Calendar.YEAR),
            currentCal.get(Calendar.MONTH),
            currentCal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar Tarefa", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título da Tarefa") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { openDateTimePicker() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "📅 Prazo: $dueDateText", fontSize = 13.sp)
                        Icon(Icons.Default.EditCalendar, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }

                Text("Categoria:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(categories) { cat ->
                        FilterChip(
                            selected = category == cat.name,
                            onClick = { category = cat.name },
                            label = { Text("${cat.emoji} ${cat.name}", fontSize = 11.sp) }
                        )
                    }
                }

                Text("Prioridade:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TaskPriority.values().forEach { prio ->
                        FilterChip(
                            selected = priority == prio,
                            onClick = { priority = prio },
                            label = { Text(prio.label) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Toque em Loop Contínuo", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Toca som repetidamente até você dispensar", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = loopAlarm,
                        onCheckedChange = { loopAlarm = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onConfirm(
                            task.copy(
                                title = title.trim(),
                                category = category,
                                priority = priority,
                                dueDateText = dueDateText,
                                dueDateTimestamp = selectedTimestamp,
                                reminderMinutesBefore = reminderMinutesBefore,
                                customSoundUri = customSoundUri,
                                loopAlarm = loopAlarm
                            )
                        )
                    }
                },
                enabled = title.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Salvar Alterações")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) {
                Text("Cancelar")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
