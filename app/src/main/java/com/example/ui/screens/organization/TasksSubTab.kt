package com.example.ui.screens.organization

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.Category
import com.example.data.model.Task
import com.example.data.model.TaskPriority
import com.example.data.model.isReadyForMainList
import com.example.ui.MainViewModel
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.theme.isAppDarkTheme
import com.example.ui.tutorial.tutorialTarget
import com.example.util.SoundEffectsManager
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

    val pendingCount = remember(tasks) { tasks.count { !it.isCompleted && it.isReadyForMainList() } }
    val completedCount = remember(tasks) { tasks.count { it.isCompleted } }
    val futureCount = remember(tasks) { tasks.count { !it.isCompleted && !it.isReadyForMainList() } }
    val completionPercent = remember(tasks, completedCount) {
        val totalVisible = tasks.count { it.isReadyForMainList() || it.isCompleted }
        if (totalVisible == 0) 0 else (completedCount * 100) / totalVisible
    }

    val filteredTasks = remember(tasks, filterMode) {
        when (filterMode) {
            "PENDING" -> tasks.filter { !it.isCompleted && it.isReadyForMainList() }
            "COMPLETED" -> tasks.filter { it.isCompleted }
            "FUTURE" -> tasks.filter { !it.isCompleted && !it.isReadyForMainList() }
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
                    if (futureCount > 0) {
                        item {
                            TaskFilterPill(
                                label = "Agendadas (+3d) ($futureCount)",
                                selected = filterMode == "FUTURE",
                                showActiveDot = false,
                                onClick = { filterMode = "FUTURE" }
                            )
                        }
                    }
                    item {
                        TaskFilterPill(
                            label = "Concluídas ($completedCount)",
                            selected = filterMode == "COMPLETED",
                            showActiveDot = false,
                            onClick = { filterMode = "COMPLETED" }
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
    initialDateStr: String? = null,
    onlyAskTime: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (title: String, category: String, priority: TaskPriority, dueDateText: String, dueTimestamp: Long?, reminderMinutesBefore: Int) -> Unit
) {
    QuickAddTaskDialog(
        categories = categories,
        initialTitle = initialTitle,
        initialDateStr = initialDateStr,
        onlyAskTime = onlyAskTime,
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
    initialDateStr: String? = null,
    onlyAskTime: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (title: String, category: String, priority: TaskPriority, dueDateText: String, dueTimestamp: Long?, reminderMinutesBefore: Int, customSoundUri: String, loopAlarm: Boolean) -> Unit
) {
    val context = LocalContext.current
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }
    var category by remember { mutableStateOf(categories.firstOrNull()?.name ?: "Geral") }
    var priority by remember { mutableStateOf(TaskPriority.MEDIA) }

    val cal = remember(initialDateStr) {
        val c = Calendar.getInstance()
        if (!initialDateStr.isNullOrBlank()) {
            try {
                val parts = initialDateStr.split("-")
                if (parts.size == 3) {
                    c.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt(), 12, 0, 0)
                }
            } catch (_: Exception) {}
        } else {
            c.add(Calendar.HOUR_OF_DAY, 2)
        }
        c
    }
    var selectedTimestamp by remember(cal) { mutableStateOf<Long?>(cal.timeInMillis) }
    var dueDateText by remember(cal) {
        mutableStateOf(SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(cal.time))
    }
    var reminderMinutesBefore by remember { mutableStateOf(15) }
    var customSoundUri by remember { mutableStateOf("") }
    var loopAlarm by remember { mutableStateOf(false) }
    var showMoreOptions by remember { mutableStateOf(false) }

    fun openDateTimePicker() {
        val currentCal = Calendar.getInstance().apply {
            selectedTimestamp?.let { timeInMillis = it }
        }
        if (onlyAskTime) {
            TimePickerDialog(
                context,
                { _, hourOfDay, minute ->
                    val picked = (currentCal.clone() as Calendar).apply {
                        set(Calendar.HOUR_OF_DAY, hourOfDay)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, 0)
                    }
                    selectedTimestamp = picked.timeInMillis
                    dueDateText = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(picked.time)
                },
                currentCal.get(Calendar.HOUR_OF_DAY),
                currentCal.get(Calendar.MINUTE),
                true
            ).show()
        } else {
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
    }

    val isDark = isAppDarkTheme()
    val warmBg = if (isDark) Color(0xFF1E1C19) else Color(0xFFFAF7F2)
    val cardBg = if (isDark) Color(0xFF26231E) else Color(0xFFFAF5EE)
    val cardBorder = if (isDark) Color(0xFF38322B) else Color(0xFFEDE3D7)
    val textColor = if (isDark) Color(0xFFF8FAFC) else Color(0xFF1A1715)
    val subTextColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF7D7268)
    val labelColor = if (isDark) Color(0xFFCBD5E1) else Color(0xFF5D544C)
    val closeBtnBg = if (isDark) Color(0xFF2E2A24) else Color(0xFFF1EAE0)
    val greenContainer = if (isDark) Color(0xFF1B3824) else Color(0xFFE8F7ED)
    val greenBorder = if (isDark) Color(0xFF4ADE80) else Color(0xFF48BB78)
    val greenText = if (isDark) Color(0xFF4ADE80) else Color(0xFF1B5E20)
    val orangeCta = Color(0xFFFF6A00)

    val formattedDisplayDate = remember(selectedTimestamp, cal) {
        val target = selectedTimestamp ?: cal.timeInMillis
        SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.getDefault()).format(Date(target))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = warmBg,
                shadowElevation = 10.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    // Top drag pill
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(42.dp)
                            .height(4.5.dp)
                            .background(
                                color = if (isDark) Color(0xFF423B33) else Color(0xFFD6CBC3),
                                shape = CircleShape
                            )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Header Row: Yellow lightning bolt icon, Title, Subtitle, Close Button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (isDark) Color(0xFF3E331A) else Color(0xFFFFF3CD),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Nova Tarefa",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Defina detalhes e compromissos",
                                fontSize = 12.sp,
                                color = subTextColor
                            )
                        }

                        Surface(
                            shape = CircleShape,
                            color = closeBtnBg,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable { onDismiss() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Fechar",
                                    tint = subTextColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Label: NOME DA TAREFA *
                    Text(
                        text = "NOME DA TAREFA *",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = labelColor
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Title input container
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = cardBg,
                        border = BorderStroke(1.dp, cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            if (title.isEmpty()) {
                                Text(
                                    text = "Revisar layout aconchegante",
                                    fontSize = 14.5.sp,
                                    color = subTextColor.copy(alpha = 0.65f)
                                )
                            }
                            BasicTextField(
                                value = title,
                                onValueChange = { title = it },
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = textColor
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("task_title_input")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Date & Time Picker Card
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = cardBg,
                        border = BorderStroke(1.dp, cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { openDateTimePicker() }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isDark) Color(0xFF3D2314) else Color(0xFFFFEFE3),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarToday,
                                        contentDescription = null,
                                        tint = Color(0xFFFF6F00),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (onlyAskTime) "Prazo selecionado" else "Prazo definido",
                                    fontSize = 11.5.sp,
                                    color = subTextColor
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = formattedDisplayDate,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.EditCalendar,
                                contentDescription = "Editar prazo",
                                tint = Color(0xFFFF6F00),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Collapsible toggle link: "^ Menos detalhes" / "v Mais detalhes"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showMoreOptions = !showMoreOptions }
                            .padding(vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (showMoreOptions) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = orangeCta,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (showMoreOptions) "Menos detalhes" else "Mais detalhes",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = orangeCta
                        )
                    }

                    AnimatedVisibility(visible = showMoreOptions) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.padding(top = 10.dp)
                        ) {
                            // CATEGORIA
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "CATEGORIA:",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = labelColor
                                )
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(categories) { cat ->
                                        val isSelected = category.equals(cat.name, ignoreCase = true)
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = if (isSelected) greenContainer else cardBg,
                                            border = BorderStroke(
                                                width = if (isSelected) 1.5.dp else 1.dp,
                                                color = if (isSelected) greenBorder else cardBorder
                                            ),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(14.dp))
                                                .clickable { category = cat.name }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(text = cat.emoji, fontSize = 13.sp)
                                                Text(
                                                    text = cat.name,
                                                    fontSize = 12.5.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) greenText else textColor
                                                )
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = greenText,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // PRIORIDADE
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "PRIORIDADE:",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = labelColor
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    TaskPriority.values().forEach { prio ->
                                        val isSelected = priority == prio
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = if (isSelected) greenContainer else cardBg,
                                            border = BorderStroke(
                                                width = if (isSelected) 1.5.dp else 1.dp,
                                                color = if (isSelected) greenBorder else cardBorder
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(42.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .clickable { priority = prio }
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                                Text(
                                                    text = if (isSelected) "${prio.label} •" else prio.label,
                                                    fontSize = 12.5.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) greenText else textColor
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // LEMBRAR COM ANTECEDÊNCIA
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "LEMBRAR COM ANTECEDÊNCIA:",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = labelColor
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(0 to "No prazo", 15 to "15m", 30 to "30m", 60 to "1h").forEach { (mins, lbl) ->
                                        val isSelected = reminderMinutesBefore == mins
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = if (isSelected) greenContainer else cardBg,
                                            border = BorderStroke(
                                                width = if (isSelected) 1.5.dp else 1.dp,
                                                color = if (isSelected) greenBorder else cardBorder
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(42.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .clickable { reminderMinutesBefore = mins }
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                                Text(
                                                    text = lbl,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) greenText else textColor
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Loop Alarm Card
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = cardBg,
                                border = BorderStroke(1.dp, cardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Toque em Loop Contínuo",
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = textColor
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Toca som repetidamente até você dispensar",
                                            fontSize = 11.sp,
                                            color = subTextColor
                                        )
                                    }
                                    Switch(
                                        checked = loopAlarm,
                                        onCheckedChange = { loopAlarm = it },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = orangeCta
                                        )
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Action buttons row: Cancelar | Salvar Tarefa →
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isDark) Color(0xFF2E2A24) else Color(0xFFEFEAE4),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onDismiss() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Cancelar",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = textColor
                                )
                            }
                        }

                        Button(
                            onClick = {
                                if (title.isNotBlank()) {
                                    onConfirm(title.trim(), category, priority, dueDateText, selectedTimestamp, reminderMinutesBefore, customSoundUri, loopAlarm)
                                }
                            },
                            enabled = title.isNotBlank(),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = orangeCta,
                                contentColor = Color.White,
                                disabledContainerColor = orangeCta.copy(alpha = 0.4f),
                                disabledContentColor = Color.White.copy(alpha = 0.6f)
                            ),
                            modifier = Modifier
                                .weight(1.35f)
                                .height(48.dp)
                                .testTag("save_task_btn")
                        ) {
                            Text(
                                text = "Salvar Tarefa →",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
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

    val isDark = isAppDarkTheme()
    val warmBg = if (isDark) Color(0xFF1E1C19) else Color(0xFFFAF7F2)
    val cardBg = if (isDark) Color(0xFF26231E) else Color(0xFFFAF5EE)
    val cardBorder = if (isDark) Color(0xFF38322B) else Color(0xFFEDE3D7)
    val textColor = if (isDark) Color(0xFFF8FAFC) else Color(0xFF1A1715)
    val subTextColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF7D7268)
    val labelColor = if (isDark) Color(0xFFCBD5E1) else Color(0xFF5D544C)
    val closeBtnBg = if (isDark) Color(0xFF2E2A24) else Color(0xFFF1EAE0)
    val greenContainer = if (isDark) Color(0xFF1B3824) else Color(0xFFE8F7ED)
    val greenBorder = if (isDark) Color(0xFF4ADE80) else Color(0xFF48BB78)
    val greenText = if (isDark) Color(0xFF4ADE80) else Color(0xFF1B5E20)
    val orangeCta = Color(0xFFFF6A00)

    val formattedDisplayDate = remember(selectedTimestamp, dueDateText) {
        if (selectedTimestamp != null && selectedTimestamp!! > 0) {
            SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.getDefault()).format(Date(selectedTimestamp!!))
        } else {
            dueDateText
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = warmBg,
                shadowElevation = 10.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(42.dp)
                            .height(4.5.dp)
                            .background(
                                color = if (isDark) Color(0xFF423B33) else Color(0xFFD6CBC3),
                                shape = CircleShape
                            )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (isDark) Color(0xFF3E331A) else Color(0xFFFFF3CD),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Editar Tarefa",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Ajuste detalhes e compromissos",
                                fontSize = 12.sp,
                                color = subTextColor
                            )
                        }

                        Surface(
                            shape = CircleShape,
                            color = closeBtnBg,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable { onDismiss() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Fechar",
                                    tint = subTextColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "NOME DA TAREFA *",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = labelColor
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = cardBg,
                        border = BorderStroke(1.dp, cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            BasicTextField(
                                value = title,
                                onValueChange = { title = it },
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = textColor
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = cardBg,
                        border = BorderStroke(1.dp, cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { openDateTimePicker() }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isDark) Color(0xFF3D2314) else Color(0xFFFFEFE3),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarToday,
                                        contentDescription = null,
                                        tint = Color(0xFFFF6F00),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Prazo definido",
                                    fontSize = 11.5.sp,
                                    color = subTextColor
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = formattedDisplayDate,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.EditCalendar,
                                contentDescription = "Editar prazo",
                                tint = Color(0xFFFF6F00),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "CATEGORIA:",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = labelColor
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(categories) { cat ->
                                    val isSelected = category.equals(cat.name, ignoreCase = true)
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (isSelected) greenContainer else cardBg,
                                        border = BorderStroke(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = if (isSelected) greenBorder else cardBorder
                                        ),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(14.dp))
                                            .clickable { category = cat.name }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(text = cat.emoji, fontSize = 13.sp)
                                            Text(
                                                text = cat.name,
                                                fontSize = 12.5.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) greenText else textColor
                                            )
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = greenText,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "PRIORIDADE:",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = labelColor
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TaskPriority.values().forEach { prio ->
                                    val isSelected = priority == prio
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (isSelected) greenContainer else cardBg,
                                        border = BorderStroke(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = if (isSelected) greenBorder else cardBorder
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .clickable { priority = prio }
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            Text(
                                                text = if (isSelected) "${prio.label} •" else prio.label,
                                                fontSize = 12.5.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) greenText else textColor
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = cardBg,
                            border = BorderStroke(1.dp, cardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Toque em Loop Contínuo",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textColor
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Toca som repetidamente até você dispensar",
                                        fontSize = 11.sp,
                                        color = subTextColor
                                    )
                                }
                                Switch(
                                    checked = loopAlarm,
                                    onCheckedChange = { loopAlarm = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = orangeCta
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isDark) Color(0xFF2E2A24) else Color(0xFFEFEAE4),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onDismiss() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Cancelar",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = textColor
                                )
                            }
                        }

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
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = orangeCta,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .weight(1.35f)
                                .height(48.dp)
                        ) {
                            Text(
                                text = "Salvar Alterações",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TaskAudioSelectionRow(
    customSoundUri: String,
    onSoundSelected: (String) -> Unit,
    onClear: () -> Unit
) {
    val context = LocalContext.current
    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            onSoundSelected(uri.toString())
        }
    }

    val displayName = remember(customSoundUri) {
        SoundEffectsManager.getAudioDisplayName(context, customSoundUri)
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Toque da Tarefa:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { audioPicker.launch("audio/*") }
                ) {
                    Icon(
                        imageVector = if (customSoundUri.isNotBlank()) Icons.Default.MusicNote else Icons.Default.NotificationsNone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = displayName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (customSoundUri.isNotBlank()) {
                        IconButton(
                            onClick = { SoundEffectsManager.playPreview(context, customSoundUri) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = "Testar som", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        }
                        IconButton(
                            onClick = {
                                SoundEffectsManager.stopPreview()
                                onClear()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Remover som", modifier = Modifier.size(16.dp))
                        }
                    } else {
                        TextButton(
                            onClick = { audioPicker.launch("audio/*") },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Escolher", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
