package com.example.ui.screens.calendar

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Habit
import com.example.data.model.Note
import com.example.data.model.Task
import com.example.data.model.TaskPriority
import com.example.data.model.isScheduledForDate
import com.example.data.model.getFrequencyLabel
import com.example.ui.MainViewModel
import com.example.ui.screens.organization.QuickAddTaskDialog
import com.example.ui.screens.organization.notes.FullScreenNoteEditor
import com.example.ui.screens.organization.notes.NoteEditorMode
import java.text.SimpleDateFormat
import java.util.*

private sealed class CalendarTimelineEntry(val sortTime: String) {
    class HabitEntry(val habit: Habit, val isCompletedOnDay: Boolean, time: String) : CalendarTimelineEntry(time)
    class TaskEntry(val task: Task, time: String) : CalendarTimelineEntry(time)
    class NoteEntry(val note: Note, time: String) : CalendarTimelineEntry(time)
}

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
    var showAddMenuDialog by remember { mutableStateOf(false) }
    var activeNoteForEditor by remember { mutableStateOf<Note?>(null) }
    var isCreatingNoteForCalendar by remember { mutableStateOf(false) }

    // Retractable calendar mode: false = Semanal (retraído), true = Mensal (expandido)
    var isMonthExpanded by remember { mutableStateOf(false) }

    val dayFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val todayStr = remember { dayFormat.format(Date()) }

    val selectedCal = remember(selectedDateStr) {
        Calendar.getInstance().apply {
            try {
                time = dayFormat.parse(selectedDateStr) ?: Date()
            } catch (_: Exception) {
                time = Date()
            }
        }
    }

    var displayedYear by remember(selectedDateStr) { mutableStateOf(selectedCal.get(Calendar.YEAR)) }
    var displayedMonth by remember(selectedDateStr) { mutableStateOf(selectedCal.get(Calendar.MONTH)) }

    val monthNameFormat = remember { SimpleDateFormat("MMMM", Locale("pt", "BR")) }
    val fullDayTitleFormat = remember { SimpleDateFormat("EEEE, d 'de' MMMM", Locale("pt", "BR")) }

    val monthName = remember(displayedYear, displayedMonth) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, displayedYear)
            set(Calendar.MONTH, displayedMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        monthNameFormat.format(cal.time).replaceFirstChar { it.uppercase() }
    }

    val selectedDayHeaderTitle = remember(selectedCal) {
        fullDayTitleFormat.format(selectedCal.time).uppercase()
    }

    // 7 days of the current week (Monday -> Sunday) around selectedDateStr
    val weekDaysList = remember(selectedDateStr) {
        val cal = (selectedCal.clone() as Calendar).apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        }
        List(7) {
            val str = dayFormat.format(cal.time)
            cal.add(Calendar.DAY_OF_MONTH, 1)
            str
        }
    }

    // Full month grid (Monday -> Sunday)
    val monthDaysList = remember(displayedYear, displayedMonth) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, displayedYear)
            set(Calendar.MONTH, displayedMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // 1=Sun, 2=Mon...
        val leadingEmpty = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - Calendar.MONDAY
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val list = mutableListOf<String?>()
        repeat(leadingEmpty) { list.add(null) }
        for (d in 1..maxDays) {
            cal.set(Calendar.DAY_OF_MONTH, d)
            list.add(dayFormat.format(cal.time))
        }
        while (list.size % 7 != 0) {
            list.add(null)
        }
        list
    }

    var showNotesInCalendar by remember { mutableStateOf(true) }
    var showHabitsInCalendar by remember { mutableStateOf(true) }
    var showTasksInCalendar by remember { mutableStateOf(true) }

    // Filter tasks, habits, and notes for the selected date
    val tasksForDay = remember(tasks, selectedDateStr, showTasksInCalendar) {
        if (!showTasksInCalendar) emptyList()
        else tasks.filter { task ->
            task.dueDateText.startsWith(selectedDateStr) ||
                task.dueDateText.contains(selectedDateStr) ||
                (task.dueDateTimestamp != null && dayFormat.format(Date(task.dueDateTimestamp)) == selectedDateStr)
        }
    }

    val habitsForDay = remember(habits, selectedDateStr, showHabitsInCalendar) {
        if (!showHabitsInCalendar) emptyList()
        else {
            val parts = selectedDateStr.split("-")
            val cal = Calendar.getInstance().apply {
                if (parts.size == 3) {
                    set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
                }
            }
            habits.filter { habit ->
                habit.isScheduledForDate(selectedDateStr, cal)
            }
        }
    }

    val notesForDay = remember(notes, selectedDateStr, showNotesInCalendar) {
        if (!showNotesInCalendar) emptyList()
        else notes.filter { it.associatedDate == selectedDateStr }
    }

    // Build chronological "Fluxo do Dia" entries
    val timelineEntries = remember(tasksForDay, habitsForDay, notesForDay, selectedDateStr) {
        val list = mutableListOf<CalendarTimelineEntry>()

        habitsForDay.forEach { habit ->
            val isDone = habit.completedDatesCsv.split(",").any { it.trim() == selectedDateStr }
            val timeStr = habit.reminderTime.ifBlank { "08:00" }
            list.add(CalendarTimelineEntry.HabitEntry(habit, isDone, timeStr))
        }

        tasksForDay.forEach { task ->
            val timeStr = when {
                task.dueDateTimestamp != null -> timeFormat.format(Date(task.dueDateTimestamp))
                task.dueDateText.contains(":") -> {
                    val match = Regex("(\\d{2}:\\d{2})").find(task.dueDateText)
                    match?.groupValues?.getOrNull(1) ?: "09:00"
                }
                else -> "09:00"
            }
            list.add(CalendarTimelineEntry.TaskEntry(task, timeStr))
        }

        notesForDay.forEach { note ->
            val timeStr = timeFormat.format(Date(note.updatedAt))
            list.add(CalendarTimelineEntry.NoteEntry(note, timeStr))
        }

        list.sortedBy { it.sortTime }
    }

    val totalItems = timelineEntries.size
    val completedItems = remember(timelineEntries) {
        timelineEntries.count { entry ->
            when (entry) {
                is CalendarTimelineEntry.HabitEntry -> entry.isCompletedOnDay
                is CalendarTimelineEntry.TaskEntry -> entry.task.isCompleted
                is CalendarTimelineEntry.NoteEntry -> true
            }
        }
    }
    val dayFocusPercent = remember(totalItems, completedItems) {
        if (totalItems == 0) 0 else (completedItems * 100) / totalItems
    }

    // Full screen smooth note editor if editing or creating note
    if (isCreatingNoteForCalendar || activeNoteForEditor != null) {
        key(activeNoteForEditor?.id ?: if (isCreatingNoteForCalendar) -1L else 0L) {
            FullScreenNoteEditor(
                initialNote = activeNoteForEditor,
                categories = categories,
                allNotes = notes,
                tasks = tasks,
                habits = habits,
                viewModel = viewModel,
                onClose = {
                    isCreatingNoteForCalendar = false
                    activeNoteForEditor = null
                },
                onSaveAndClose = { title, content, cat, isPinned, bgUri, blur, brightness, nColor ->
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
                        backgroundBrightness = brightness,
                        uuid = activeNoteForEditor?.uuid,
                        oldTitle = activeNoteForEditor?.title,
                        noteColor = nColor
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
                initialMode = if (viewModel.getLastNoteEditorMode() == "READING") NoteEditorMode.READING else NoteEditorMode.LIVE_PREVIEW,
                onModeChanged = { mode ->
                    viewModel.setLastNoteEditorMode(mode.name)
                },
                onToggleChecklistTaskSync = { checklistText, isChecked ->
                    viewModel.syncChecklistToTask(checklistText, isChecked)
                },
                onConvertChecklistToDirectTask = { taskTitle, taskCategory, taskPriority, dueDate, dueTimestamp, reminderMinutes ->
                    val priorityEnum = when (taskPriority) {
                        "ALTA" -> TaskPriority.ALTA
                        "BAIXA" -> TaskPriority.BAIXA
                        else -> TaskPriority.MEDIA
                    }
                    viewModel.addTask(
                        title = taskTitle,
                        category = taskCategory,
                        priority = priorityEnum,
                        dueDateText = dueDate,
                        dueTimestamp = dueTimestamp,
                        reminderMinutesBefore = reminderMinutes
                    )
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
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 6.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Mês & Controle de Navegação + Alternador Retrátil (Semanal / Mensal)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Retractable Mode Toggle + Month/Year Title
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { isMonthExpanded = !isMonthExpanded }
                            .padding(vertical = 2.dp)
                            .testTag("cal_toggle_mode_btn")
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isMonthExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (isMonthExpanded) "Recolher para Semanal" else "Expandir para Mensal",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = monthName,
                                style = MaterialTheme.typography.titleLarge,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "$displayedYear",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 1.dp)
                            )
                        }
                    }

                    // Right: Previous / Hoje / Next Pill
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        shadowElevation = 1.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        if (isMonthExpanded) {
                                            val cal = Calendar.getInstance().apply {
                                                set(Calendar.YEAR, displayedYear)
                                                set(Calendar.MONTH, displayedMonth)
                                                set(Calendar.DAY_OF_MONTH, 1)
                                                add(Calendar.MONTH, -1)
                                            }
                                            displayedYear = cal.get(Calendar.YEAR)
                                            displayedMonth = cal.get(Calendar.MONTH)
                                            viewModel.selectCalendarDate(dayFormat.format(cal.time))
                                        } else {
                                            val cal = (selectedCal.clone() as Calendar).apply {
                                                add(Calendar.DAY_OF_MONTH, -7)
                                            }
                                            viewModel.selectCalendarDate(dayFormat.format(cal.time))
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronLeft,
                                    contentDescription = if (isMonthExpanded) "Mês anterior" else "Semana anterior",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { viewModel.selectCalendarDate(todayStr) }
                            ) {
                                Text(
                                    text = "Hoje",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        if (isMonthExpanded) {
                                            val cal = Calendar.getInstance().apply {
                                                set(Calendar.YEAR, displayedYear)
                                                set(Calendar.MONTH, displayedMonth)
                                                set(Calendar.DAY_OF_MONTH, 1)
                                                add(Calendar.MONTH, 1)
                                            }
                                            displayedYear = cal.get(Calendar.YEAR)
                                            displayedMonth = cal.get(Calendar.MONTH)
                                            viewModel.selectCalendarDate(dayFormat.format(cal.time))
                                        } else {
                                            val cal = (selectedCal.clone() as Calendar).apply {
                                                add(Calendar.DAY_OF_MONTH, 7)
                                            }
                                            viewModel.selectCalendarDate(dayFormat.format(cal.time))
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = if (isMonthExpanded) "Próximo mês" else "Próxima semana",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 2. Mini Calendário & Grade Semanal / Mensal Retrátil
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .animateContentSize()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        // Weekday Header (Seg .. Dom)
                        val weekdayLabels = listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")
                        val selectedDayIndex = remember(selectedCal) {
                            val dow = selectedCal.get(Calendar.DAY_OF_WEEK)
                            if (dow == Calendar.SUNDAY) 6 else dow - Calendar.MONDAY
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            weekdayLabels.forEachIndexed { idx, label ->
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (idx == selectedDayIndex) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        // Retractable Calendar Grid: Weekly (1 row) vs Monthly (all weeks)
                        val rowsToRender = if (isMonthExpanded) {
                            monthDaysList.chunked(7)
                        } else {
                            listOf(weekDaysList)
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowsToRender.forEach { weekRow ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    weekRow.forEach { dateStr ->
                                        if (dateStr == null) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        } else {
                                            val isSelected = dateStr == selectedDateStr
                                            val isToday = dateStr == todayStr
                                            val dayNumber = dateStr.substringAfterLast("-").toIntOrNull() ?: 1

                                            val hasTasks = tasks.any { t ->
                                                t.dueDateText.startsWith(dateStr) ||
                                                    (t.dueDateTimestamp != null && dayFormat.format(Date(t.dueDateTimestamp)) == dateStr)
                                            }
                                            val hasHabits = habits.any { h ->
                                                val parts = dateStr.split("-")
                                                val c = Calendar.getInstance().apply {
                                                    if (parts.size == 3) {
                                                        set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
                                                    }
                                                }
                                                h.isScheduledForDate(dateStr, c)
                                            }
                                            val hasNotes = notes.any { n ->
                                                n.associatedDate == dateStr
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .background(
                                                        when {
                                                            isSelected -> MaterialTheme.colorScheme.primary
                                                            isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                                            else -> Color.Transparent
                                                        }
                                                    )
                                                    .clickable { viewModel.selectCalendarDate(dateStr) }
                                                    .padding(vertical = 7.dp)
                                                    .testTag("cal_day_$dateStr"),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isToday && !isSelected) {
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.TopEnd)
                                                            .padding(end = 5.dp)
                                                            .size(6.dp)
                                                            .background(MaterialTheme.colorScheme.secondary, CircleShape)
                                                    )
                                                }

                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Text(
                                                        text = "$dayNumber",
                                                        fontSize = if (isSelected) 16.sp else 14.sp,
                                                        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.SemiBold,
                                                        color = if (isSelected) {
                                                            MaterialTheme.colorScheme.onPrimary
                                                        } else {
                                                            MaterialTheme.colorScheme.onSurface
                                                        }
                                                    )

                                                    Row(
                                                        modifier = Modifier.height(6.dp),
                                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        if (hasNotes && showNotesInCalendar) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(5.dp)
                                                                    .background(
                                                                        if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                                                        CircleShape
                                                                    )
                                                            )
                                                        }
                                                        if (hasHabits && showHabitsInCalendar) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(5.dp)
                                                                    .background(
                                                                        if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.secondary,
                                                                        CircleShape
                                                                    )
                                                            )
                                                        }
                                                        if (hasTasks && showTasksInCalendar) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(5.dp)
                                                                    .background(
                                                                        if (isSelected) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.tertiary,
                                                                        CircleShape
                                                                    )
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

                        // Legenda sutil de produtividade + Modo
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    LegendDotItem(
                                        color = MaterialTheme.colorScheme.primary,
                                        label = "Notas",
                                        enabled = showNotesInCalendar,
                                        onClick = { showNotesInCalendar = !showNotesInCalendar }
                                    )
                                    LegendDotItem(
                                        color = MaterialTheme.colorScheme.secondary,
                                        label = "Hábitos",
                                        enabled = showHabitsInCalendar,
                                        onClick = { showHabitsInCalendar = !showHabitsInCalendar }
                                    )
                                    LegendDotItem(
                                        color = MaterialTheme.colorScheme.tertiary,
                                        label = "Tarefas",
                                        enabled = showTasksInCalendar,
                                        onClick = { showTasksInCalendar = !showTasksInCalendar }
                                    )
                                }

                                Text(
                                    text = if (isMonthExpanded) "Mensal" else "Semanal",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { isMonthExpanded = !isMonthExpanded }
                                )
                            }
                        }
                    }
                }
            }

            // 3. Card de Resumo do Dia
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = selectedDayHeaderTitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${tasksForDay.size} compromissos • ${habitsForDay.size} hábitos",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Barra de progresso de infusão
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Dia $dayFocusPercent% focado",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "$completedItems de $totalItems concluídos",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(12.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                    .padding(2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth((dayFocusPercent / 100f).coerceIn(0f, 1f))
                                        .fillMaxHeight()
                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                )
                            }
                        }
                    }
                }
            }

            // 4. Linha do Tempo / Agenda do Dia ("Fluxo do Dia")
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Fluxo do Dia",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Horário Local",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (timelineEntries.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                RoundedCornerShape(20.dp)
                            )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 28.dp, horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.EventAvailable,
                                contentDescription = null,
                                modifier = Modifier.size(38.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                            )
                            Text(
                                text = "Nenhum compromisso ou hábito neste dia.",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { showAddTaskDialog = true },
                                    shape = CircleShape
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Tarefa", fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = { isCreatingNoteForCalendar = true },
                                    shape = CircleShape
                                ) {
                                    Icon(Icons.Default.NoteAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Nota", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            } else {
                itemsIndexed(
                    items = timelineEntries,
                    key = { index, entry ->
                        when (entry) {
                            is CalendarTimelineEntry.HabitEntry -> "h_${entry.habit.id}_$index"
                            is CalendarTimelineEntry.TaskEntry -> "t_${entry.task.id}_$index"
                            is CalendarTimelineEntry.NoteEntry -> "n_${entry.note.id}_$index"
                        }
                    }
                ) { index, entry ->
                    val isLast = index == timelineEntries.lastIndex
                    val timeLabelColor = when (entry) {
                        is CalendarTimelineEntry.HabitEntry -> if (entry.isCompletedOnDay) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                        is CalendarTimelineEntry.TaskEntry -> MaterialTheme.colorScheme.primary
                        is CalendarTimelineEntry.NoteEntry -> MaterialTheme.colorScheme.tertiary
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Left Time Column + Vertical Connector Line
                        Column(
                            modifier = Modifier
                                .width(44.dp)
                                .fillMaxHeight()
                                .padding(top = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = entry.sortTime,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = timeLabelColor
                            )
                            if (!isLast) {
                                Box(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .weight(1f)
                                        .padding(vertical = 6.dp)
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant,
                                            CircleShape
                                        )
                                )
                            }
                        }

                        // Right Timeline Card
                        Box(modifier = Modifier.weight(1f)) {
                            when (entry) {
                                is CalendarTimelineEntry.HabitEntry -> {
                                    TimelineHabitCard(
                                        habit = entry.habit,
                                        isDone = entry.isCompletedOnDay,
                                        canToggle = selectedDateStr == todayStr,
                                        onToggle = { viewModel.toggleHabitToday(entry.habit) }
                                    )
                                }
                                is CalendarTimelineEntry.TaskEntry -> {
                                    TimelineTaskCard(
                                        task = entry.task,
                                        timeText = entry.sortTime,
                                        onToggle = { viewModel.toggleTask(entry.task) },
                                        onPlay = { onStartPomodoroForTask(entry.task) },
                                        onDelete = { viewModel.deleteTask(entry.task) }
                                    )
                                }
                                is CalendarTimelineEntry.NoteEntry -> {
                                    TimelineNoteCard(
                                        note = entry.note,
                                        onClick = { activeNoteForEditor = entry.note }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Botão Flutuante de Ação (FAB Cítrico)
        FloatingActionButton(
            onClick = { showAddMenuDialog = true },
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(56.dp)
                .testTag("cal_add_fab")
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Novo Agendamento ou Tarefa",
                modifier = Modifier.size(30.dp)
            )
        }
    }

    if (showAddMenuDialog) {
        AlertDialog(
            onDismissRequest = { showAddMenuDialog = false },
            title = {
                Text(text = "Novo Registro no Calendário", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showAddMenuDialog = false
                                showAddTaskDialog = true
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Nova Tarefa", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Data fixada em $selectedDateStr (escolha só o horário)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showAddMenuDialog = false
                                isCreatingNoteForCalendar = true
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.NoteAdd, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Nova Nota", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Nota vinculada ao dia $selectedDateStr", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddMenuDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showAddTaskDialog) {
        QuickAddTaskDialog(
            categories = categories,
            initialDateStr = selectedDateStr,
            onlyAskTime = true,
            onDismiss = { showAddTaskDialog = false },
            onConfirm = { title, category, priority, dueText, dueTimestamp, minutesBefore, soundUri, loopAlarm, disruptive ->
                viewModel.addTask(title, category, priority, dueText, dueTimestamp, minutesBefore, soundUri, loopAlarm, disruptive)
                showAddTaskDialog = false
            }
        )
    }
}

@Composable
private fun LegendDotItem(
    color: Color,
    label: String,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = if (onClick != null) {
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onClick() }
                .padding(horizontal = 4.dp, vertical = 3.dp)
        } else Modifier
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(if (enabled) color else color.copy(alpha = 0.25f), CircleShape)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (enabled) FontWeight.Bold else FontWeight.Normal,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            textDecoration = if (enabled) null else androidx.compose.ui.text.style.TextDecoration.LineThrough
        )
    }
}

@Composable
private fun TimelineHabitCard(
    habit: Habit,
    isDone: Boolean,
    canToggle: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isDone) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        shadowElevation = if (isDone) 0.dp else 1.5.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                RoundedCornerShape(20.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isDone) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Done,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Concluído",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                    ) {
                        Text(
                            text = habit.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "Hábito",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                if (isDone) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalFireDepartment,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "${habit.currentStreak}d",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                if (isDone) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = habit.iconEmoji.ifBlank { "🍊" }, fontSize = 20.sp)
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = habit.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None,
                            color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isDone) {
                                "Meta alcançada • ${habit.getFrequencyLabel()} • ${habit.currentStreak}d seguidos"
                            } else {
                                "${habit.getFrequencyLabel()} • Avisa ${habit.reminderLeadMinutes}m antes • ${habit.currentStreak}d seguidos"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isDone) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = CircleShape,
                    color = if (isDone) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(enabled = canToggle) { onToggle() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Completar hábito",
                            tint = if (isDone) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineTaskCard(
    task: Task,
    timeText: String,
    onToggle: () -> Unit,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    val accentColor = when (task.priority) {
        TaskPriority.ALTA -> MaterialTheme.colorScheme.primary
        TaskPriority.MEDIA -> MaterialTheme.colorScheme.tertiary
        TaskPriority.BAIXA -> MaterialTheme.colorScheme.secondary
    }

    val (priorityBg, priorityFg, priorityLabel) = when (task.priority) {
        TaskPriority.ALTA -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            "Alta Prioridade"
        )
        TaskPriority.MEDIA -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "Média"
        )
        TaskPriority.BAIXA -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            "Baixa"
        )
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.5.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                RoundedCornerShape(20.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // Left visual accent bar
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(accentColor)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = task.category,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }

                        Surface(
                            shape = CircleShape,
                            color = priorityBg
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(priorityFg, CircleShape)
                                )
                                Text(
                                    text = priorityLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = priorityFg
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Remover tarefa",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (task.isCompleted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { onToggle() },
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
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                                color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Previsto para as $timeText",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }

                    if (!task.isCompleted) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shadowElevation = 1.dp,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .clickable { onPlay() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Iniciar timer",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineNoteCard(
    note: Note,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.5.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                RoundedCornerShape(20.dp)
            )
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
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
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = note.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = note.content.take(50).replace("\n", " ").ifBlank { "Nota vinculada ao dia" },
                        style = MaterialTheme.typography.bodySmall,
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
