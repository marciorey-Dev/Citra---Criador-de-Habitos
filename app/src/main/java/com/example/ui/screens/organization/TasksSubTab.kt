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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Category
import com.example.data.model.Task
import com.example.data.model.TaskPriority
import com.example.ui.MainViewModel
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

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

    val filteredTasks = when (filterMode) {
        "PENDING" -> tasks.filter { !it.isCompleted }
        "COMPLETED" -> tasks.filter { it.isCompleted }
        else -> tasks
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Filter Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filterMode == "PENDING",
                    onClick = { filterMode = "PENDING" },
                    label = { Text("Pendentes (${tasks.count { !it.isCompleted }})") }
                )
                FilterChip(
                    selected = filterMode == "ALL",
                    onClick = { filterMode = "ALL" },
                    label = { Text("Todas (${tasks.size})") }
                )
                FilterChip(
                    selected = filterMode == "COMPLETED",
                    onClick = { filterMode = "COMPLETED" },
                    label = { Text("Concluídas (${tasks.count { it.isCompleted }})") }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (filteredTasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (filterMode == "COMPLETED") "Nenhuma tarefa concluída ainda." else "Tudo em dia! Nenhuma tarefa pendente.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Toque no '+' para criação rápida de tarefas.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filteredTasks, key = { it.id }) { task ->
                        TaskItemCard(
                            task = task,
                            onToggle = { viewModel.toggleTask(task) },
                            onEdit = { taskToEdit = task },
                            onPlay = { onStartPomodoroForTask(task) },
                            onDelete = { taskToDelete = task }
                        )
                    }
                }
            }
        }

        // Floating Action Button
        FloatingActionButton(
            onClick = { showAddDialog = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("add_task_fab")
        ) {
            Icon(Icons.Default.Add, contentDescription = "Adicionar Tarefa")
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

    // Protection against errors: Confirmation dialog before deletion
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
fun TaskItemCard(
    task: Task,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    val (priorityBadgeBg, priorityBadgeText) = when (task.priority) {
        TaskPriority.ALTA -> RedContainer to RedAction
        TaskPriority.MEDIA -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
        TaskPriority.BAIXA -> GreenContainer to GreenAccent
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (task.isCompleted) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (task.isCompleted) 0.dp else 1.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
            .border(
                width = 1.dp,
                color = if (task.isCompleted) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(16.dp)
            )
            .testTag("task_item_${task.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox
            Checkbox(
                checked = task.isCompleted,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = GreenAccent,
                    uncheckedColor = priorityBadgeText
                ),
                modifier = Modifier.testTag("task_checkbox_${task.id}")
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Task Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Category Badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = task.category,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Priority Badge (original clean pill)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = priorityBadgeBg
                    ) {
                        Text(
                            text = "● ${task.priority.label}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = priorityBadgeText,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Due Date
                    if (task.dueDateText.isNotBlank()) {
                        Text(
                            text = "📅 ${task.dueDateText}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Play / Pomodoro Link Button
            if (!task.isCompleted) {
                IconButton(
                    onClick = onPlay,
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                        .testTag("task_play_${task.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Focar nesta tarefa",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Delete Button
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Excluir Tarefa",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
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
