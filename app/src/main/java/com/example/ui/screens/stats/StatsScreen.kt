package com.example.ui.screens.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Habit
import com.example.ui.MainViewModel
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

enum class StatsPeriod(val label: String) {
    SEMANAL("Semanal"),
    MENSAL("Mensal"),
    ANUAL("Anual")
}

@Composable
fun StatsScreen(
    viewModel: MainViewModel
) {
    val focusSessions by viewModel.focusSessions.collectAsState()
    val habits by viewModel.habits.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val todayStr = viewModel.todayString

    var selectedPeriod by remember { mutableStateOf(StatsPeriod.SEMANAL) }
    var periodOffset by remember { mutableStateOf(0) } // 0 = current, -1 = previous, etc.

    // Reset offset when period changes
    LaunchedEffect(selectedPeriod) {
        periodOffset = 0
    }

    // Dynamic colors for categories
    val palette = listOf(
        Color(0xFF2563EB), Color(0xFF7C3AED), Color(0xFFD97706),
        Color(0xFFDB2777), Color(0xFF059669), Color(0xFFFF6F00),
        Color(0xFF0891B2), Color(0xFF4F46E5)
    )
    val categoryColorMap = remember(categories) {
        categories.mapIndexed { idx, cat ->
            cat.name to palette[idx % palette.size]
        }.toMap()
    }

    // Calculate time bounds, title, and bar data based on period & offset
    val periodInfo = remember(selectedPeriod, periodOffset, focusSessions) {
        val cal = Calendar.getInstance()
        when (selectedPeriod) {
            StatsPeriod.SEMANAL -> {
                cal.firstDayOfWeek = Calendar.MONDAY
                cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                cal.add(Calendar.WEEK_OF_YEAR, periodOffset)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val startMs = cal.timeInMillis

                val endCal = cal.clone() as Calendar
                endCal.add(Calendar.DAY_OF_YEAR, 6)
                endCal.set(Calendar.HOUR_OF_DAY, 23)
                endCal.set(Calendar.MINUTE, 59)
                endCal.set(Calendar.SECOND, 59)
                val endMs = endCal.timeInMillis

                val df = SimpleDateFormat("dd 'de' MMM", Locale("pt", "BR"))
                val title = if (periodOffset == 0) "Semana Atual (${df.format(cal.time)} - ${df.format(endCal.time)})"
                else "Semana de ${df.format(cal.time)} a ${df.format(endCal.time)}"

                val dayLabels = listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")
                val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val bars = (0..6).map { dayIndex ->
                    val dayCal = cal.clone() as Calendar
                    dayCal.add(Calendar.DAY_OF_YEAR, dayIndex)
                    val dStr = dayFormat.format(dayCal.time)
                    val mins = focusSessions.filter {
                        val sCal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
                        dayFormat.format(sCal.time) == dStr
                    }.sumOf { it.durationMinutes }
                    Triple(dayLabels[dayIndex], mins, dStr == todayStr)
                }

                PeriodComputation(startMs, endMs, title, bars)
            }
            StatsPeriod.MENSAL -> {
                cal.add(Calendar.MONTH, periodOffset)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val startMs = cal.timeInMillis

                val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                val endCal = cal.clone() as Calendar
                endCal.set(Calendar.DAY_OF_MONTH, maxDays)
                endCal.set(Calendar.HOUR_OF_DAY, 23)
                endCal.set(Calendar.MINUTE, 59)
                endCal.set(Calendar.SECOND, 59)
                val endMs = endCal.timeInMillis

                val monthFormat = SimpleDateFormat("MMMM 'de' yyyy", Locale("pt", "BR"))
                val monthName = monthFormat.format(cal.time).replaceFirstChar { it.uppercase() }
                val title = if (periodOffset == 0) "Mês Atual ($monthName)" else monthName

                // Split month into 4-5 weekly buckets
                val bars = mutableListOf<Triple<String, Int, Boolean>>()
                var bucketStart = 1
                var weekNum = 1
                val currentCal = Calendar.getInstance()
                val isCurrentMonth = currentCal.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
                        currentCal.get(Calendar.MONTH) == cal.get(Calendar.MONTH)

                while (bucketStart <= maxDays) {
                    val bucketEnd = minOf(bucketStart + 6, maxDays)
                    val bStartCal = cal.clone() as Calendar
                    bStartCal.set(Calendar.DAY_OF_MONTH, bucketStart)
                    val bEndCal = cal.clone() as Calendar
                    bEndCal.set(Calendar.DAY_OF_MONTH, bucketEnd)
                    bEndCal.set(Calendar.HOUR_OF_DAY, 23)
                    bEndCal.set(Calendar.MINUTE, 59)

                    val mins = focusSessions.filter {
                        it.timestamp in bStartCal.timeInMillis..bEndCal.timeInMillis
                    }.sumOf { it.durationMinutes }

                    val isCurrentWeekInMonth = isCurrentMonth && currentCal.get(Calendar.DAY_OF_MONTH) in bucketStart..bucketEnd
                    bars.add(Triple("Sem $weekNum", mins, isCurrentWeekInMonth))

                    bucketStart += 7
                    weekNum++
                }

                PeriodComputation(startMs, endMs, title, bars)
            }
            StatsPeriod.ANUAL -> {
                cal.add(Calendar.YEAR, periodOffset)
                cal.set(Calendar.MONTH, Calendar.JANUARY)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val startMs = cal.timeInMillis

                val endCal = cal.clone() as Calendar
                endCal.set(Calendar.MONTH, Calendar.DECEMBER)
                endCal.set(Calendar.DAY_OF_MONTH, 31)
                endCal.set(Calendar.HOUR_OF_DAY, 23)
                endCal.set(Calendar.MINUTE, 59)
                endCal.set(Calendar.SECOND, 59)
                val endMs = endCal.timeInMillis

                val year = cal.get(Calendar.YEAR)
                val title = if (periodOffset == 0) "Ano Atual ($year)" else "Ano de $year"

                val monthShortNames = listOf("Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez")
                val currentCal = Calendar.getInstance()
                val isCurrentYear = currentCal.get(Calendar.YEAR) == year

                val bars = (0..11).map { m ->
                    val mCal = cal.clone() as Calendar
                    mCal.set(Calendar.MONTH, m)
                    mCal.set(Calendar.DAY_OF_MONTH, 1)
                    val mEndCal = cal.clone() as Calendar
                    mEndCal.set(Calendar.MONTH, m)
                    mEndCal.set(Calendar.DAY_OF_MONTH, mCal.getActualMaximum(Calendar.DAY_OF_MONTH))
                    mEndCal.set(Calendar.HOUR_OF_DAY, 23)
                    mEndCal.set(Calendar.MINUTE, 59)

                    val mins = focusSessions.filter {
                        it.timestamp in mCal.timeInMillis..mEndCal.timeInMillis
                    }.sumOf { it.durationMinutes }

                    val isCurrentMonth = isCurrentYear && currentCal.get(Calendar.MONTH) == m
                    Triple(monthShortNames[m], mins, isCurrentMonth)
                }

                PeriodComputation(startMs, endMs, title, bars)
            }
        }
    }

    // Sessions in the active period window
    val periodSessions = remember(focusSessions, periodInfo) {
        focusSessions.filter { it.timestamp in periodInfo.startMs..periodInfo.endMs }
    }

    val totalPeriodMinutes = remember(periodSessions) {
        periodSessions.sumOf { it.durationMinutes }
    }
    val totalPeriodHours = totalPeriodMinutes / 60
    val totalPeriodRemMins = totalPeriodMinutes % 60
    val formattedPeriodTime = if (totalPeriodHours > 0) "${totalPeriodHours}h ${totalPeriodRemMins}m" else "${totalPeriodRemMins}m"

    // Group sessions by category for the Pie Chart in this period
    val categoryTotals = remember(periodSessions) {
        if (periodSessions.isEmpty()) {
            mapOf("Sem foco registrado" to 1)
        } else {
            periodSessions.groupBy { it.category }
                .mapValues { entry -> entry.value.sumOf { it.durationMinutes } }
        }
    }

    // Most productive bucket in this period
    val mostProductiveBucket = remember(periodInfo.bars) {
        val top = periodInfo.bars.maxByOrNull { it.second }
        if (top != null && top.second > 0) "${top.first} (${top.second}m)" else "—"
    }

    // Habit Statistics: Completed vs Pending
    val completedHabitsCount = remember(habits, todayStr) {
        habits.count { h -> h.completedDatesCsv.split(",").any { it.trim() == todayStr } }
    }
    val pendingHabitsCount = habits.size - completedHabitsCount

    val dayFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val dailyFocusMinutes = remember(focusSessions, todayStr) {
        focusSessions.filter {
            dayFormat.format(Date(it.timestamp)) == todayStr
        }.sumOf { it.durationMinutes }
    }

    val weeklyFocusMinutes = remember(focusSessions) {
        val cal = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startWeek = cal.timeInMillis
        focusSessions.filter { it.timestamp >= startWeek }.sumOf { it.durationMinutes }
    }

    val plannedMinutes = remember(periodSessions) {
        periodSessions.sumOf { (it.cycleCount.coerceAtLeast(1) * 25).coerceAtMost(it.durationMinutes) }
    }
    val extraMinutes = remember(totalPeriodMinutes, plannedMinutes) {
        (totalPeriodMinutes - plannedMinutes).coerceAtLeast(0)
    }

    val previousPeriodTotalMinutes = remember(focusSessions, periodInfo) {
        val windowDuration = periodInfo.endMs - periodInfo.startMs
        val prevStart = periodInfo.startMs - windowDuration
        val prevEnd = periodInfo.startMs - 1
        focusSessions.filter { it.timestamp in prevStart..prevEnd }.sumOf { it.durationMinutes }
    }
    val periodDiff = totalPeriodMinutes - previousPeriodTotalMinutes

    val past7DaysHabitsHistory = remember(habits) {
        val cal = Calendar.getInstance()
        var completedCount = 0
        var missedCount = 0
        for (i in 0..6) {
            val dCal = cal.clone() as Calendar
            dCal.add(Calendar.DAY_OF_YEAR, -i)
            val dStr = dayFormat.format(dCal.time)
            habits.forEach { h ->
                val wasCompleted = h.completedDatesCsv.split(",").any { it.trim() == dStr }
                if (wasCompleted) completedCount++ else missedCount++
            }
        }
        completedCount to missedCount
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Header & Period Selector
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Estatísticas & Histórico",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Compreenda seus padrões com clareza e ritmo saudável",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Period Filter Tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatsPeriod.entries.forEach { period ->
                        FilterChip(
                            selected = selectedPeriod == period,
                            onClick = { selectedPeriod = period },
                            label = {
                                Text(
                                    text = period.label,
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedPeriod == period) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 1. DADOS DE INÍCIO: TEMPO DIÁRIO, SEMANAL & COMPARAÇÃO DE PERÍODOS
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Visão Geral de Produtividade",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // Comparação de períodos
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (periodDiff >= 0) GreenContainer else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = if (periodDiff >= 0) "+${periodDiff}m vs anterior" else "${periodDiff}m vs anterior",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (periodDiff >= 0) OnGreenContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Foco Hoje", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "${dailyFocusMinutes / 60}h ${dailyFocusMinutes % 60}m",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text("Foco Semanal", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "${weeklyFocusMinutes / 60}h ${weeklyFocusMinutes % 60}m",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // Tempo Planejado vs Tempo Extra
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Planejado: ${plannedMinutes}m",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = GreenAccent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Tempo Extra: ${extraMinutes}m",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = GreenAccent
                            )
                        }
                    }
                }
            }
        }

        // 2. HISTÓRICO DE HÁBITOS CUMPRIDOS E FALHADOS (Sem tom de culpa)
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Histórico de Hábitos (Últimos 7 dias)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "✅", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${past7DaysHabitsHistory.first} cumpridos",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = GreenAccent
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "🍃", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${past7DaysHabitsHistory.second} não realizados",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = "🌱 Padrão saudável: dias mais calmos fazem parte da sua rotina. Cada dia é uma nova oportunidade!",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        lineHeight = 15.sp
                    )
                }
            }
        }

        // 3. PROGRESSO POR CATEGORIA NO INÍCIO
        if (totalPeriodMinutes > 0) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Progresso por Categoria (${selectedPeriod.label})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )

                        categoryTotals.filter { it.key != "Sem foco registrado" }.forEach { (catName, catMins) ->
                            val pct = (catMins.toFloat() / totalPeriodMinutes.toFloat()).coerceIn(0f, 1f)
                            val catColor = categoryColorMap[catName] ?: OrangePrimary

                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(catName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text("${catMins}m (${(pct * 100).roundToInt()}%)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { pct },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp),
                                    color = catColor,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4 Summary Metric Cards (with readable Orange numbers in Dark Mode)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricCard(
                    title = "Foco (${selectedPeriod.label})",
                    value = formattedPeriodTime,
                    icon = Icons.Default.Timer,
                    iconTint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Mais Produtivo",
                    value = mostProductiveBucket,
                    icon = Icons.Default.TrendingUp,
                    iconTint = GreenAccent,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricCard(
                    title = "Hábitos Concluídos Hoje",
                    value = "$completedHabitsCount de ${habits.size}",
                    icon = Icons.Default.CheckCircle,
                    iconTint = GreenAccent,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Hábitos Pendentes",
                    value = "$pendingHabitsCount pendentes",
                    icon = Icons.Default.Schedule,
                    iconTint = RedAction,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Focus Bar Chart with Period Navigation
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Period Navigator
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { periodOffset-- }) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Período Anterior")
                        }
                        Text(
                            text = periodInfo.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = { if (periodOffset < 0) periodOffset++ },
                            enabled = periodOffset < 0
                        ) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Próximo Período")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val maxMins = (periodInfo.bars.maxOfOrNull { it.second } ?: 60).coerceAtLeast(60)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        periodInfo.bars.forEach { (label, mins, isCurrent) ->
                            val heightFraction = (mins.toFloat() / maxMins.toFloat()).coerceIn(0.08f, 1f)

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (mins > 0) {
                                    Text(
                                        text = "${mins}m",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                }

                                Box(
                                    modifier = Modifier
                                        .width(if (periodInfo.bars.size > 8) 12.dp else 18.dp)
                                        .fillMaxHeight(heightFraction)
                                        .background(
                                            color = if (mins > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
                                        )
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Category Pie Chart with Historical Comparison for the Selected Period
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("category_pie_chart_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Distribuição por Categoria",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Histórico: ${periodInfo.title}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val sumMins = if (periodSessions.isEmpty()) 1 else categoryTotals.values.sum().coerceAtLeast(1)

                    if (periodSessions.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Nenhuma sessão de foco registrada neste período.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Canvas Pie
                            Box(
                                modifier = Modifier
                                    .size(130.dp)
                                    .padding(6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    var currentAngle = -90f
                                    val strokeWidth = 22.dp.toPx()
                                    val diameter = size.minDimension - strokeWidth
                                    val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
                                    val arcSize = Size(diameter, diameter)

                                    categoryTotals.forEach { (cat, mins) ->
                                        val sweep = (mins.toFloat() / sumMins.toFloat()) * 360f
                                        val color = categoryColorMap[cat] ?: OrangePrimary
                                        drawArc(
                                            color = color,
                                            startAngle = currentAngle,
                                            sweepAngle = sweep,
                                            useCenter = false,
                                            topLeft = topLeft,
                                            size = arcSize,
                                            style = Stroke(width = strokeWidth)
                                        )
                                        currentAngle += sweep
                                    }
                                }

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "100%",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Foco",
                                        fontSize = 10.sp,
                                        color = Slate400
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            // Legend
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                categoryTotals.forEach { (cat, mins) ->
                                    val pct = (mins * 100) / sumMins
                                    val color = categoryColorMap[cat] ?: OrangePrimary
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .background(color, CircleShape)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = cat,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1
                                            )
                                        }
                                        Text(
                                            text = "$pct% (${mins}m)",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Habit History & Performance Section
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Histórico de Hábitos & Desempenho",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Status de cumprimento de cada hábito definido",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (habits.isEmpty()) {
                        Text(
                            text = "Nenhum hábito cadastrado ainda.",
                            fontSize = 12.sp,
                            color = Slate400
                        )
                    } else {
                        habits.forEach { habit ->
                            val isDoneToday = habit.completedDatesCsv.split(",")
                                .any { it.trim() == todayStr }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(text = habit.iconEmoji, fontSize = 18.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = habit.title,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Sequência: ${habit.currentStreak} dias | Notifica às ${habit.reminderTime}",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isDoneToday) GreenContainer else MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = if (isDoneToday) "Concluído Hoje" else "Pendente",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDoneToday) OnGreenContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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

private data class PeriodComputation(
    val startMs: Long,
    val endMs: Long,
    val title: String,
    val bars: List<Triple<String, Int, Boolean>>
)

@Composable
fun MetricCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(18.dp)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(iconTint.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
            Text(
                text = title,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
