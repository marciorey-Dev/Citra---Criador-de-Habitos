package com.example.ui.screens.organization

import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.Category
import com.example.data.model.Habit
import com.example.data.model.calculateWeeklyStreak
import com.example.data.model.getFrequencyLabel
import com.example.ui.MainViewModel
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.theme.OrangeContainer
import com.example.ui.theme.isAppDarkTheme
import com.example.ui.tutorial.tutorialTarget
import com.example.util.SoundEffectsManager
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
                            text = "${habit.reminderTime} • ${habit.getFrequencyLabel()}",
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
                            text = "Sequência: ${habit.calculateWeeklyStreak()} dias",
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
                    val isPast = dateStr < todaySdf

                    val pillBg = when {
                        isCompleted -> MaterialTheme.colorScheme.secondary
                        isToday -> MaterialTheme.colorScheme.primary
                        isPast -> MaterialTheme.colorScheme.error // Vermelho para dia pulado sem apagar histórico
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    }
                    val pillFg = when {
                        isCompleted -> MaterialTheme.colorScheme.onSecondary
                        isToday -> MaterialTheme.colorScheme.onPrimary
                        isPast -> MaterialTheme.colorScheme.onError
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
                    // Top drag handle pill
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

                    // Header Row: Title & Close Button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Novo Hábito Diário",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )

                        Spacer(modifier = Modifier.weight(1f))

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

                    // Input Field: "Nome do Hábito"
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
                                    text = "Nome do Hábito",
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
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Emoji Section (interactive tile, keyboard input, and presets)
                    HabitEmojiSection(
                        selectedEmoji = selectedEmoji,
                        onSelectEmoji = { selectedEmoji = it }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Horário Fixo
                    Text(
                        text = "Horário Fixo:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = labelColor
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = cardBg,
                        border = BorderStroke(1.dp, cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { openTimePicker() }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "⏰ Notificar diariamente às: $reminderTime",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = "Editar horário",
                                tint = subTextColor,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Avisar com antecedência (máx 10 min):
                    Text(
                        text = "Avisar com antecedência (máx 10 min):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = labelColor
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(0 to "No horário", 5 to "5 min", 10 to "10 min").forEach { (mins, lbl) ->
                            val isSelected = reminderLeadMinutes == mins
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
                                    .clickable { reminderLeadMinutes = mins }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Text(
                                        text = lbl,
                                        fontSize = 12.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) greenText else textColor
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Toque em Loop Contínuo
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

                    Spacer(modifier = Modifier.height(20.dp))

                    // Action buttons: Cancelar | Criar Hábito
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.Transparent,
                            border = BorderStroke(1.dp, if (isDark) Color(0xFF4A4237) else Color(0xFFDDD2C6)),
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
                                    color = orangeCta
                                )
                            }
                        }

                        Button(
                            onClick = {
                                if (title.isNotBlank()) {
                                    onConfirm(title.trim(), category, selectedEmoji, reminderTime, reminderLeadMinutes, frequencyDays, customSoundUri, loopAlarm)
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
                                .weight(1.25f)
                                .height(48.dp)
                        ) {
                            Text(
                                text = "Criar Hábito",
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
    var frequencyDays by remember { mutableStateOf(habit.frequencyDays) }
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
                        Text(
                            text = "Editar Hábito",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )

                        Spacer(modifier = Modifier.weight(1f))

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

                    Spacer(modifier = Modifier.height(16.dp))

                    HabitEmojiSection(
                        selectedEmoji = selectedEmoji,
                        onSelectEmoji = { selectedEmoji = it }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Horário Fixo:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = labelColor
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = cardBg,
                        border = BorderStroke(1.dp, cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { openTimePicker() }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "⏰ Notificar diariamente às: $reminderTime",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = "Editar horário",
                                tint = subTextColor,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Avisar com antecedência (máx 10 min):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = labelColor
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(0 to "No horário", 5 to "5 min", 10 to "10 min").forEach { (mins, lbl) ->
                            val isSelected = reminderLeadMinutes == mins
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
                                    .clickable { reminderLeadMinutes = mins }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Text(
                                        text = lbl,
                                        fontSize = 12.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) greenText else textColor
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

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

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.Transparent,
                            border = BorderStroke(1.dp, if (isDark) Color(0xFF4A4237) else Color(0xFFDDD2C6)),
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
                                    color = orangeCta
                                )
                            }
                        }

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
                                            frequencyDays = frequencyDays,
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
                                .weight(1.25f)
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
private fun HabitFrequencySelector(
    frequencyDays: String,
    onFrequencyChanged: (String) -> Unit
) {
    var mode by remember(frequencyDays) {
        mutableStateOf(
            when {
                frequencyDays.equals("ALL", ignoreCase = true) || frequencyDays.isBlank() || frequencyDays.equals("Todos os dias", ignoreCase = true) -> "ALL"
                frequencyDays.equals("ALTERNATE", ignoreCase = true) -> "ALTERNATE"
                else -> "CUSTOM"
            }
        )
    }
    val daysOfWeek = listOf(
        1 to "Dom",
        2 to "Seg",
        3 to "Ter",
        4 to "Qua",
        5 to "Qui",
        6 to "Sex",
        7 to "Sáb"
    )
    val selectedDays = remember(frequencyDays, mode) {
        if (mode == "CUSTOM") {
            frequencyDays.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
        } else emptySet()
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("Frequência de Repetição:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = mode == "ALL",
                onClick = {
                    mode = "ALL"
                    onFrequencyChanged("ALL")
                },
                label = { Text("Diário", fontSize = 11.sp) }
            )
            FilterChip(
                selected = mode == "ALTERNATE",
                onClick = {
                    mode = "ALTERNATE"
                    onFrequencyChanged("ALTERNATE")
                },
                label = { Text("Dia sim, dia não", fontSize = 11.sp) }
            )
            FilterChip(
                selected = mode == "CUSTOM",
                onClick = {
                    mode = "CUSTOM"
                    val fallback = if (selectedDays.isEmpty()) "7" else selectedDays.joinToString(",")
                    onFrequencyChanged(fallback)
                },
                label = { Text("Dias", fontSize = 11.sp) }
            )
        }

        if (mode == "CUSTOM") {
            Text(
                text = "Selecione os dias da semana (ex: todo sábado):",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                daysOfWeek.forEach { (dayInt, dayLabel) ->
                    val isDaySelected = selectedDays.contains(dayInt)
                    Surface(
                        selected = isDaySelected,
                        onClick = {
                            val newSet = if (isDaySelected) {
                                if (selectedDays.size > 1) selectedDays - dayInt else selectedDays
                            } else {
                                selectedDays + dayInt
                            }
                            onFrequencyChanged(newSet.sorted().joinToString(","))
                        },
                        shape = CircleShape,
                        color = if (isDaySelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isDaySelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text(
                                text = dayLabel,
                                fontSize = 11.sp,
                                fontWeight = if (isDaySelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        Text(
            text = "📅 Vinculado ao Calendário: fica assinalado e tocará para sempre nos dias definidos. Apagar o hábito remove os agendamentos.",
            fontSize = 10.5.sp,
            color = MaterialTheme.colorScheme.primary,
            lineHeight = 14.sp
        )
    }
}

@Composable
private fun HabitEmojiSection(
    selectedEmoji: String,
    onSelectEmoji: (String) -> Unit
) {
    val isDark = isAppDarkTheme()
    val cardBg = if (isDark) Color(0xFF26231E) else Color(0xFFFAF5EE)
    val cardBorder = if (isDark) Color(0xFF38322B) else Color(0xFFEDE3D7)
    val textColor = if (isDark) Color(0xFFF8FAFC) else Color(0xFF1A1715)
    val subTextColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF7D7268)
    val labelColor = if (isDark) Color(0xFFCBD5E1) else Color(0xFF5D544C)
    val orangeCta = Color(0xFFFF6A00)

    val suggestedEmojis = listOf(
        "💧", "🏃", "📖", "🧘", "🍏", "💪", "🎯", "✍️"
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Escolha ou digite qualquer Emoji (campo livre):",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = labelColor
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (isDark) Color(0xFF352B20) else Color(0xFFFDECE2),
                border = BorderStroke(1.dp, cardBorder),
                modifier = Modifier.size(70.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                ) {
                    Text(
                        text = selectedEmoji.ifBlank { "💧" },
                        fontSize = 32.sp,
                        lineHeight = 38.sp
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = cardBg,
                border = BorderStroke(1.dp, cardBorder),
                modifier = Modifier
                    .weight(1f)
                    .height(70.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Emoji do Hábito (1 emoji)",
                        fontSize = 11.sp,
                        color = subTextColor
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    BasicTextField(
                        value = selectedEmoji,
                        onValueChange = { input ->
                            if (input.isEmpty()) {
                                onSelectEmoji("")
                            } else {
                                onSelectEmoji(com.example.util.EmojiHelper.filterToSingleEmoji(input, fallback = ""))
                            }
                        },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 20.sp,
                            lineHeight = 28.sp,
                            fontWeight = FontWeight.Medium,
                            color = textColor
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            suggestedEmojis.forEach { emoji ->
                val isSelected = selectedEmoji == emoji
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) {
                        if (isDark) Color(0xFF3D2314) else Color(0xFFFFF1E6)
                    } else {
                        cardBg
                    },
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) orangeCta else cardBorder
                    ),
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelectEmoji(emoji) }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = emoji, fontSize = 17.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun HabitAudioSelectionRow(
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
        Text("Toque do Hábito:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
