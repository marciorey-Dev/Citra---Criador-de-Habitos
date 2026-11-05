package com.example.ui.screens.organization

import android.app.TimePickerDialog
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Category
import com.example.data.model.Habit
import com.example.ui.MainViewModel
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.theme.OrangeContainer
import com.example.ui.tutorial.tutorialTarget
import java.text.SimpleDateFormat
import java.util.*

@Composable
private fun habitCategoryIconFor(category: String): ImageVector {
    return when (category.lowercase().trim()) {
        "criatividade", "ideias" -> Icons.Default.Palette
        "estudos", "study", "leitura" -> Icons.Default.MenuBook
        "saúde", "saúde & energia", "fitness" -> Icons.Default.DirectionsRun
        "trabalho", "work", "finanças", "logística" -> Icons.Default.Payments
        else -> Icons.Default.Eco
    }
}

@Composable
fun HabitsSubTab(
    viewModel: MainViewModel
) {
    val habits by viewModel.habits.collectAsState()
    val categories by viewModel.categories.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var habitToEdit by remember { mutableStateOf<Habit?>(null) }
    var habitToDelete by remember { mutableStateOf<Habit?>(null) }

    val todayStr = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    val pendingTodayCount = remember(habits, todayStr) {
        habits.count { habit ->
            habit.completedDatesCsv.split(",").none { it.trim() == todayStr }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 6.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Cozy Encouragement Banner
            item {
                val bannerBrush = Brush.horizontalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    )
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(bannerBrush)
                        .padding(14.dp)
                        .tutorialTarget("habits_streak_counter")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            shadowElevation = 3.dp,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Eco,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "RITMO DE HOJE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 0.6.sp
                            )
                            Text(
                                text = when {
                                    habits.isEmpty() -> "Comece sua rotina hoje!"
                                    pendingTodayCount == 0 -> "Todos os hábitos concluídos hoje!"
                                    pendingTodayCount == 1 -> "1 hábito pronto para florescer!"
                                    else -> "$pendingTodayCount hábitos prontos para florescer!"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Dedique tempo ao seu bem-estar sem pressa.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (habits.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp)
                            .tutorialTarget("habits_checkin_first_item"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.LocalFireDepartment,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Nenhum hábito cadastrado.",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Toque no '+' para definir um hábito com horário fixo.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            } else {
                items(habits, key = { it.id }) { habit ->
                    val isDoneToday = habit.completedDatesCsv.split(",")
                        .any { it.trim() == todayStr }
                    val isFirst = habits.firstOrNull()?.id == habit.id

                    HabitItemCard(
                        habit = habit,
                        isDoneToday = isDoneToday,
                        isFirst = isFirst,
                        onToggleToday = { viewModel.toggleHabitToday(habit) },
                        onEdit = { habitToEdit = habit },
                        onDelete = { habitToDelete = habit }
                    )
                }
            }

            // Cozy Daily Quote Card
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Pequenas repetições diárias regam grandes realizações. Respeite o seu ritmo!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Cozy Circular Floating Action Button
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
                .tutorialTarget("habits_add_button")
                .testTag("add_habit_fab")
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Adicionar Hábito",
                modifier = Modifier.size(30.dp)
            )
        }
    }

    if (showAddDialog) {
        AddHabitDialog(
            categories = categories,
            onDismiss = { showAddDialog = false },
            onConfirm = { title, category, emoji, time, leadMinutes, frequency, soundUri, loop ->
                viewModel.addHabit(title, category, emoji, time, leadMinutes, frequency, soundUri, loop)
                showAddDialog = false
            }
        )
    }

    if (habitToEdit != null) {
        EditHabitDialog(
            habit = habitToEdit!!,
            categories = categories,
            onDismiss = { habitToEdit = null },
            onConfirm = { updated ->
                viewModel.updateHabit(updated)
                habitToEdit = null
            }
        )
    }

    // Protection against errors: Confirmation dialog before deletion
    if (habitToDelete != null) {
        ConfirmDeleteDialog(
            title = "Excluir Hábito",
            message = "Deseja realmente excluir '${habitToDelete!!.title}'? Todo o histórico de sequências deste hábito será removido.",
            onConfirm = {
                viewModel.deleteHabit(habitToDelete!!)
                habitToDelete = null
            },
            onDismiss = { habitToDelete = null }
        )
    }
}

@Composable
fun HabitItemCard(
    habit: Habit,
    isDoneToday: Boolean,
    isFirst: Boolean = false,
    onToggleToday: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isExpiredToday = remember(isDoneToday, habit.reminderTime) {
        if (isDoneToday || habit.reminderTime.isBlank() || !habit.reminderTime.contains(":")) {
            false
        } else {
            try {
                val parts = habit.reminderTime.split(":")
                val rHour = parts[0].trim().toInt()
                val rMin = parts[1].trim().toInt()
                val now = Calendar.getInstance()
                val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
                val reminderMinutes = rHour * 60 + rMin
                nowMinutes >= reminderMinutes
            } catch (e: Exception) {
                false
            }
        }
    }

    val cardContainer = when {
        isDoneToday -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        isExpiredToday -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.22f)
        else -> MaterialTheme.colorScheme.surface
    }

    val cardBorder = when {
        isDoneToday -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)
        isExpiredToday -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDoneToday) 0.dp else 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isExpiredToday) 1.5.dp else 1.dp,
                color = cardBorder,
                shape = RoundedCornerShape(20.dp)
            )
            .clip(RoundedCornerShape(20.dp))
            .clickable { onEdit() }
            .testTag("habit_item_${habit.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Badges Row: Category + Lead Reminder + Status Pill
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
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = habitCategoryIconFor(habit.category),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = habit.category,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (habit.reminderLeadMinutes > 0) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Alarm,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "${habit.reminderLeadMinutes}m antes",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Status Pill
                val (statusBg, statusDot, statusText, statusFg) = when {
                    isDoneToday -> listOf(
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.secondary,
                        "Concluído",
                        MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    isExpiredToday -> listOf(
                        MaterialTheme.colorScheme.errorContainer,
                        MaterialTheme.colorScheme.error,
                        "Expirado",
                        MaterialTheme.colorScheme.onErrorContainer
                    )
                    else -> listOf(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                        MaterialTheme.colorScheme.tertiary,
                        "Pendente hoje",
                        MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = statusBg as Color
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(statusDot as Color, CircleShape)
                        )
                        Text(
                            text = statusText as String,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusFg as Color
                        )
                    }
                }
            }

            // Main Body Row: Visual Avatar + Info Block + Check-in Button & Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Visual Icon Avatar
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(
                            color = when {
                                isDoneToday -> MaterialTheme.colorScheme.secondaryContainer
                                isExpiredToday -> MaterialTheme.colorScheme.errorContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                            },
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = habit.iconEmoji.ifBlank { "🍊" }, fontSize = 26.sp)
                }

                // Info Block
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = habit.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isExpiredToday && !isDoneToday) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = habit.reminderTime,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            imageVector = Icons.Default.LocalFireDepartment,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Sequência: ${habit.currentStreak} dias",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Check-in & Delete Actions
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isDoneToday) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                        },
                        shadowElevation = if (isDoneToday) 3.dp else 1.dp,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable { onToggleToday() }
                            .then(if (isFirst) Modifier.tutorialTarget("habits_checkin_first_item") else Modifier)
                            .testTag("habit_toggle_${habit.id}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isDoneToday) Icons.Default.Check else Icons.Default.Done,
                                contentDescription = "Marcar Hábito",
                                tint = if (isDoneToday) {
                                    MaterialTheme.colorScheme.onSecondary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Excluir Hábito",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            // Week Day Indicator Pills (S T Q Q S S D)
            val completedSet = remember(habit.completedDatesCsv) {
                habit.completedDatesCsv.split(",").map { it.trim() }.toSet()
            }
            val weekDays = remember {
                val list = mutableListOf<Pair<String, String>>()
                val cal = Calendar.getInstance()
                cal.firstDayOfWeek = Calendar.MONDAY
                cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val dayLabels = listOf("S", "T", "Q", "Q", "S", "S", "D")
                for (i in 0..6) {
                    list.add(Pair(dayLabels[i], sdf.format(cal.time)))
                    cal.add(Calendar.DAY_OF_MONTH, 1)
                }
                list
            }
            val todaySdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                weekDays.forEach { (label, dateStr) ->
                    val isCompleted = completedSet.contains(dateStr)
                    val isToday = dateStr == todaySdf

                    val pillBg = when {
                        isCompleted -> MaterialTheme.colorScheme.secondary
                        isToday -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    }
                    val pillFg = when {
                        isCompleted -> MaterialTheme.colorScheme.onSecondary
                        isToday -> MaterialTheme.colorScheme.onPrimary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Surface(
                        shape = CircleShape,
                        color = pillBg,
                        shadowElevation = if (isToday || isCompleted) 1.dp else 0.dp,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isToday || isCompleted) FontWeight.Bold else FontWeight.SemiBold,
                                color = pillFg
                            )
                        }
                    }
                }
            }
        }
    }
}

// Dialogs with custom Antecedência (reminderLeadMinutes) inside
@Composable
fun AddHabitDialog(
    categories: List<Category>,
    onDismiss: () -> Unit,
    onConfirm: (title: String, category: String, iconEmoji: String, reminderTime: String, reminderLeadMinutes: Int, frequencyDays: String, customSoundUri: String, loopAlarm: Boolean) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(categories.firstOrNull()?.name ?: "Saúde") }
    var selectedEmoji by remember { mutableStateOf("💧") }
    var reminderTime by remember { mutableStateOf("08:00") }
    var reminderLeadMinutes by remember { mutableStateOf(10) }
    var frequencyDays by remember { mutableStateOf("ALL") }
    var customSoundUri by remember { mutableStateOf("") }
    var loopAlarm by remember { mutableStateOf(false) }

    fun openTimePicker() {
        val parts = reminderTime.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 8
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0

        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                reminderTime = String.format("%02d:%02d", hourOfDay, minute)
            },
            h,
            m,
            true
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo Hábito Diário", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Nome do Hábito") },
                    placeholder = { Text("Ex: Beber 2L de água...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                HabitEmojiSection(
                    selectedEmoji = selectedEmoji,
                    onSelectEmoji = { selectedEmoji = it }
                )

                Text("Horário Fixo:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { openTimePicker() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "⏰ Notificar diariamente às: $reminderTime", fontSize = 13.sp)
                        Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }

                Text("Avisar com antecedência (máx 10 min):", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "No horário", 5 to "5 min", 10 to "10 min").forEach { (mins, lbl) ->
                        FilterChip(
                            selected = reminderLeadMinutes == mins,
                            onClick = { reminderLeadMinutes = mins },
                            label = { Text(lbl, fontSize = 11.sp) }
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
                        onConfirm(title.trim(), category, selectedEmoji, reminderTime, reminderLeadMinutes, frequencyDays, customSoundUri, loopAlarm)
                    }
                },
                enabled = title.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Criar Hábito")
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
fun EditHabitDialog(
    habit: Habit,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onConfirm: (Habit) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(habit.title) }
    var category by remember { mutableStateOf(habit.category) }
    var selectedEmoji by remember { mutableStateOf(habit.iconEmoji) }
    var reminderTime by remember { mutableStateOf(habit.reminderTime) }
    var reminderLeadMinutes by remember { mutableStateOf(habit.reminderLeadMinutes) }
    var customSoundUri by remember { mutableStateOf(habit.customSoundUri) }
    var loopAlarm by remember { mutableStateOf(habit.loopAlarm) }

    fun openTimePicker() {
        val parts = reminderTime.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 8
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0

        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                reminderTime = String.format("%02d:%02d", hourOfDay, minute)
            },
            h,
            m,
            true
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar Hábito", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Nome do Hábito") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                HabitEmojiSection(
                    selectedEmoji = selectedEmoji,
                    onSelectEmoji = { selectedEmoji = it }
                )

                Text("Horário Fixo:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { openTimePicker() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "⏰ $reminderTime", fontSize = 13.sp)
                        Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }

                Text("Avisar com antecedência (máx 10 min):", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "No horário", 5 to "5 min", 10 to "10 min").forEach { (mins, lbl) ->
                        FilterChip(
                            selected = reminderLeadMinutes == mins,
                            onClick = { reminderLeadMinutes = mins },
                            label = { Text(lbl, fontSize = 11.sp) }
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
                            habit.copy(
                                title = title.trim(),
                                category = category,
                                iconEmoji = selectedEmoji,
                                reminderTime = reminderTime,
                                reminderLeadMinutes = reminderLeadMinutes,
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

@Composable
private fun HabitEmojiSection(
    selectedEmoji: String,
    onSelectEmoji: (String) -> Unit
) {
    val suggestedEmojis = listOf(
        "💧", "🏃", "📖", "🧘", "🍏", "💪", "🎯", "✍️"
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Escolha ou digite qualquer Emoji (campo livre):", fontSize = 12.sp, fontWeight = FontWeight.Bold)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(OrangeContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(selectedEmoji.ifBlank { "🍊" }, fontSize = 22.sp)
            }

            OutlinedTextField(
                value = selectedEmoji,
                onValueChange = { onSelectEmoji(it) },
                label = { Text("Qualquer Emoji do Teclado") },
                placeholder = { Text("Digite ou cole...") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        suggestedEmojis.chunked(8).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                row.forEach { emoji ->
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                color = if (selectedEmoji == emoji) OrangeContainer else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { onSelectEmoji(emoji) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = emoji, fontSize = 18.sp)
                    }
                }
            }
        }
    }
}
