package com.example.ui.screens.organization

import android.app.TimePickerDialog
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Category
import com.example.data.model.Habit
import com.example.ui.MainViewModel
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

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

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Clean Header
            val totalStreak = habits.sumOf { it.currentStreak }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Seus Hábitos",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "🔥", fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$totalStreak dias de sequência",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (habits.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Nenhum hábito cadastrado.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Toque no '+' para definir um hábito com horário fixo.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(habits, key = { it.id }) { habit ->
                        val isDoneToday = habit.completedDatesCsv.split(",")
                            .any { it.trim() == todayStr }

                        HabitItemCard(
                            habit = habit,
                            isDoneToday = isDoneToday,
                            onToggleToday = { viewModel.toggleHabitToday(habit) },
                            onEdit = { habitToEdit = habit },
                            onDelete = { habitToDelete = habit }
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
                .testTag("add_habit_fab")
        ) {
            Icon(Icons.Default.Add, contentDescription = "Adicionar Hábito")
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

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDoneToday) {
                MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
            } else if (isExpiredToday) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.22f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDoneToday) 0.dp else 1.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
            .border(
                width = if (isExpiredToday) 2.dp else 1.dp,
                color = if (isDoneToday) {
                    GreenAccent.copy(alpha = 0.7f)
                } else if (isExpiredToday) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                },
                shape = RoundedCornerShape(18.dp)
            )
            .testTag("habit_item_${habit.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Emoji Icon
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(
                            if (isDoneToday) {
                                GreenContainer.copy(alpha = 0.7f)
                            } else if (isExpiredToday) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            },
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = habit.iconEmoji, fontSize = 22.sp)
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Habit Info
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = habit.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isExpiredToday && !isDoneToday) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f, fill = false),
                            maxLines = 1,
                            softWrap = false
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = habit.category,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        if (isExpiredToday) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    text = "⚠️ Expirado",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    val leadText = if (habit.reminderLeadMinutes > 0) " (avisa ${habit.reminderLeadMinutes}m antes)" else ""
                    Text(
                        text = "⏰ ${habit.reminderTime}$leadText • Sequência: ${habit.currentStreak} dias",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // 7 Weekday consistency circles below reminder time
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
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        weekDays.forEach { (label, dateStr) ->
                            val isCompleted = completedSet.contains(dateStr)
                            val isToday = dateStr == todaySdf
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(
                                        color = if (isCompleted) GreenAccent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                        shape = CircleShape
                                    )
                                    .border(
                                        width = if (isToday) 1.5.dp else 0.dp,
                                        color = if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isCompleted) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                } else {
                                    Text(
                                        text = label,
                                        fontSize = 9.sp,
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Check-in Button
                IconButton(
                    onClick = onToggleToday,
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            if (isDoneToday) GreenAccent else MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape
                        )
                        .testTag("habit_toggle_${habit.id}")
                ) {
                    Icon(
                        imageVector = if (isDoneToday) Icons.Default.Check else Icons.Default.Done,
                        contentDescription = "Marcar Hábito",
                        tint = if (isDoneToday) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Delete Button
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Excluir Hábito",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
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
    var reminderLeadMinutes by remember { mutableStateOf(10) } // Opção no menu do hábito
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

                // Antecedência do Hábito (Máximo 10 minutos conforme V0.4.0)
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
        "💧", "🏃", "📖", "🧘", "🍏", "💪", "🎯", "✍️",
        "🌿", "🚶", "🚴", "💤", "🧼", "💊", "☕", "✨"
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Escolha ou digite um Emoji:", fontSize = 12.sp, fontWeight = FontWeight.Bold)

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
                Text(selectedEmoji, fontSize = 22.sp)
            }

            OutlinedTextField(
                value = selectedEmoji,
                onValueChange = { if (it.length <= 4) onSelectEmoji(it) },
                label = { Text("Emoji") },
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
