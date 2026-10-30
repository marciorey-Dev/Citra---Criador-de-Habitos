package com.example.ui.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Category
import com.example.data.model.Habit
import com.example.data.model.Note
import com.example.data.model.Task
import com.example.data.model.TaskPriority
import com.example.ui.MainViewModel
import com.example.ui.screens.organization.QuickAddTaskDialog
import com.example.ui.screens.organization.TaskItemCard
import com.example.ui.screens.organization.notes.FullScreenNoteEditor
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CalendarScreen(
    viewModel: MainViewModel,
    onStartPomodoroForTask: (Task) -> Unit
) {
    val tasks by viewModel.tasks.collectAsState()
    val habits by viewModel.habits.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val selectedDateStr by viewModel.selectedCalendarDate.collectAsState()

    var showAddTaskDialog by remember { mutableStateOf(false) }
    var activeNoteForEditor by remember { mutableStateOf<Note?>(null) }
    var isCreatingNoteForCalendar by remember { mutableStateOf(false) }

    // Toggleable view filters to prevent visual pollution as requested
    var showHabitsFilter by remember { mutableStateOf(true) }
    var showTasksFilter by remember { mutableStateOf(true) }
    var showNotesFilter by remember { mutableStateOf(true) }

    // Calendar state: Year and Month
    val currentCal = remember { Calendar.getInstance() }
    var displayedYear by remember { mutableStateOf(currentCal.get(Calendar.YEAR)) }
    var displayedMonth by remember { mutableStateOf(currentCal.get(Calendar.MONTH)) }

    val monthYearFormat = remember { SimpleDateFormat("MMMM yyyy", Locale("pt", "BR")) }
    val dayFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    // Month display title
    val monthTitle = remember(displayedYear, displayedMonth) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, displayedYear)
            set(Calendar.MONTH, displayedMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        monthYearFormat.format(cal.time).replaceFirstChar { it.uppercase() }
    }

    // Days in current displayed month
    val daysInMonth = remember(displayedYear, displayedMonth) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, displayedYear)
            set(Calendar.MONTH, displayedMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // 1=Sun, 2=Mon...
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val days = mutableListOf<String?>()
        for (i in 1 until firstDayOfWeek) {
            days.add(null)
        }
        for (day in 1..maxDays) {
            cal.set(Calendar.DAY_OF_MONTH, day)
            days.add(dayFormat.format(cal.time))
        }
        days
    }

    // Filter tasks, habits, and notes for the selected date
    val tasksForDay = remember(tasks, selectedDateStr, showTasksFilter) {
        if (!showTasksFilter) emptyList()
        else tasks.filter { task ->
            task.dueDateText.startsWith(selectedDateStr) ||
            task.dueDateText.contains(selectedDateStr) ||
            (task.dueDateTimestamp != null && dayFormat.format(Date(task.dueDateTimestamp)) == selectedDateStr)
        }
    }

    val habitsForDay = remember(habits, selectedDateStr, showHabitsFilter) {
        if (!showHabitsFilter) emptyList()
        else habits.filter { habit ->
            habit.completedDatesCsv.split(",").any { it.trim() == selectedDateStr }
        }
    }

    val notesForDay = remember(notes, selectedDateStr, showNotesFilter) {
        if (!showNotesFilter) emptyList()
        else notes.filter { it.associatedDate == selectedDateStr }
    }

    // Full screen smooth note editor if editing or creating note
    if (isCreatingNoteForCalendar || activeNoteForEditor != null) {
        FullScreenNoteEditor(
            initialNote = activeNoteForEditor,
            categories = categories,
            allNotes = notes,
            tasks = tasks,
            habits = habits,
            onClose = {
                isCreatingNoteForCalendar = false
                activeNoteForEditor = null
            },
            onSaveAndClose = { title, content, cat, isPinned, bgUri, blur, brightness ->
                viewModel.saveNote(
                    id = activeNoteForEditor?.id ?: 0L,
                    title = title,
                    content = content,
                    category = cat,
                    iconEmoji = "📝",
                    associatedDate = selectedDateStr,
                    isPinned = isPinned,
                    backgroundImageUri = bgUri,
                    backgroundBlur = blur,
                    backgroundBrightness = brightness
                )
                isCreatingNoteForCalendar = false
                activeNoteForEditor = null
            },
            onDelete = if (activeNoteForEditor != null) {
                {
                    viewModel.moveNoteToTrash(activeNoteForEditor!!)
                    isCreatingNoteForCalendar = false
                    activeNoteForEditor = null
                }
            } else null,
            onOpenLinkedNote = { note ->
                activeNoteForEditor = note
                isCreatingNoteForCalendar = false
            },
            onConvertChecklistToTask = { taskTitle, dueDate ->
                viewModel.addTask(
                    title = taskTitle,
                    category = "Geral",
                    priority = TaskPriority.MEDIA,
                    dueDateText = dueDate,
                    dueTimestamp = null,
                    reminderMinutesBefore = 0
                )
            }
        )
        return
    }

    // Unified scrollable container so users can freely scroll down to see all tasks, habits, and notes
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Month Navigation Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (displayedMonth == 0) {
                            displayedMonth = 11
                            displayedYear--
                        } else {
                            displayedMonth--
                        }
                    }
                ) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Mês Anterior")
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = monthTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = {
                        if (displayedMonth == 11) {
                            displayedMonth = 0
                            displayedYear++
                        } else {
                            displayedMonth++
                        }
                    }
                ) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Próximo Mês")
                }
            }
        }

        // 2. View Mode Filter Chips (Tarefas, Hábitos, Notas)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = showTasksFilter,
                    onClick = { showTasksFilter = !showTasksFilter },
                    label = { Text("Tarefas", fontSize = 11.sp) },
                    leadingIcon = {
                        if (showTasksFilter) Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp))
                    },
                    modifier = Modifier.weight(1f)
                )

                FilterChip(
                    selected = showHabitsFilter,
                    onClick = { showHabitsFilter = !showHabitsFilter },
                    label = { Text("Hábitos", fontSize = 11.sp) },
                    leadingIcon = {
                        if (showHabitsFilter) Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp))
                    },
                    modifier = Modifier.weight(1f)
                )

                FilterChip(
                    selected = showNotesFilter,
                    onClick = { showNotesFilter = !showNotesFilter },
                    label = { Text("Notas", fontSize = 11.sp) },
                    leadingIcon = {
                        if (showNotesFilter) Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp))
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 3. Weekday Labels
        item {
            val weekdayLabels = listOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                weekdayLabels.forEach { label ->
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // 4. Month Calendar Grid (Weeks)
        item {
            val weeks = daysInMonth.chunked(7)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                weeks.forEach { week ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        week.forEach { dateStr ->
                            if (dateStr == null) {
                                Spacer(modifier = Modifier.weight(1f))
                            } else {
                                val isSelected = dateStr == selectedDateStr
                                val dayNumber = dateStr.substringAfterLast("-").toIntOrNull() ?: 1

                                val hasTasks = showTasksFilter && tasks.any { t ->
                                    t.dueDateText.startsWith(dateStr) || (t.dueDateTimestamp != null && dayFormat.format(Date(t.dueDateTimestamp)) == dateStr)
                                }
                                val hasHabits = showHabitsFilter && habits.any { h ->
                                    h.completedDatesCsv.split(",").any { it.trim() == dateStr }
                                }
                                val hasNotes = showNotesFilter && notes.any { n ->
                                    n.associatedDate == dateStr
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1.1f)
                                        .padding(2.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            when {
                                                isSelected -> MaterialTheme.colorScheme.primaryContainer
                                                else -> Color.Transparent
                                            }
                                        )
                                        .clickable { viewModel.selectCalendarDate(dateStr) }
                                        .testTag("cal_day_$dateStr"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "$dayNumber",
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (hasTasks) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(4.dp)
                                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                                )
                                            }
                                            if (hasHabits) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(4.dp)
                                                        .background(GreenAccent, CircleShape)
                                                )
                                            }
                                            if (hasNotes) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(4.dp)
                                                        .background(Color(0xFF2563EB), CircleShape)
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

        // 5. Selected Day Agenda Header & Action buttons
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Agenda de $selectedDateStr",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val totalItems = tasksForDay.size + habitsForDay.size + notesForDay.size
                        Text(
                            text = "$totalItems item(s) registrado(s)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = { showAddTaskDialog = true },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Tarefa", fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = { isCreatingNoteForCalendar = true },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.NoteAdd, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Nota", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // 6. Empty State if nothing for day
        if (tasksForDay.isEmpty() && habitsForDay.isEmpty() && notesForDay.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.EventAvailable,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Nenhum item registrado para este dia.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 7. Tasks Section
        if (tasksForDay.isNotEmpty()) {
            item {
                Text(
                    text = "Tarefas (${tasksForDay.size})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            items(tasksForDay, key = { "task_${it.id}" }) { task ->
                TaskItemCard(
                    task = task,
                    onToggle = { viewModel.toggleTask(task) },
                    onEdit = {},
                    onPlay = { onStartPomodoroForTask(task) },
                    onDelete = { viewModel.deleteTask(task) }
                )
            }
        }

        // 8. Habits Completed Section
        if (habitsForDay.isNotEmpty()) {
            item {
                Text(
                    text = "Hábitos Concluídos (${habitsForDay.size})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = GreenAccent,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(habitsForDay, key = { "habit_${it.id}" }) { habit ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = GreenContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = habit.iconEmoji, fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = habit.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = OnGreenContainer)
                            Text(text = "Sequência atual: ${habit.currentStreak} dias", fontSize = 11.sp, color = GreenAccent)
                        }
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GreenAccent, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        // 9. Notes Section
        if (notesForDay.isNotEmpty()) {
            item {
                Text(
                    text = "Anotações (${notesForDay.size})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF2563EB),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(notesForDay, key = { "note_${it.id}" }) { note ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                        .clickable { activeNoteForEditor = note }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = note.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = note.content.take(50).replace("\n", " "),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Abrir nota",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    if (showAddTaskDialog) {
        QuickAddTaskDialog(
            categories = categories,
            onDismiss = { showAddTaskDialog = false },
            onConfirm = { title, category, priority, dueText, dueTimestamp, minutesBefore ->
                viewModel.addTask(title, category, priority, dueText, dueTimestamp, minutesBefore)
                showAddTaskDialog = false
            }
        )
    }
}
