package com.example.ui.screens.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.tutorial.tutorialTarget
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
    var periodOffset by remember { mutableStateOf(0) }

    LaunchedEffect(selectedPeriod) {
        periodOffset = 0
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    val palette = remember(primaryColor, secondaryColor, tertiaryColor) {
        listOf(
            primaryColor,
            tertiaryColor,
            secondaryColor,
            Color(0xFF2563EB),
            Color(0xFF7C3AED),
            Color(0xFFDB2777),
            Color(0xFF0891B2),
            Color(0xFFD97706)
        )
    }
    val categoryColorMap = remember(categories, palette) {
        categories.mapIndexed { idx, cat ->
            cat.name to palette[idx % palette.size]
        }.toMap()
    }

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

                val df = SimpleDateFormat("dd MMM", Locale("pt", "BR"))
                val title = if (periodOffset == 0) "Esta Semana (${df.format(cal.time)} - ${df.format(endCal.time)})"
                else "${df.format(cal.time)} - ${df.format(endCal.time)}"

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
                val title = monthFormat.format(cal.time).replaceFirstChar { it.uppercase() }

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
                    bars.add(Triple("S$weekNum", mins, isCurrentWeekInMonth))

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
                val title = "Ano de $year"

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

    val periodSessions = remember(focusSessions, periodInfo) {
        focusSessions.filter { it.timestamp in periodInfo.startMs..periodInfo.endMs }
    }

    val totalPeriodMinutes = remember(periodSessions) {
        periodSessions.sumOf { it.durationMinutes }
    }

    val categoryTotals = remember(periodSessions) {
        if (periodSessions.isEmpty()) {
            emptyMap()
        } else {
            periodSessions.groupBy { it.category }
                .mapValues { entry -> entry.value.sumOf { it.durationMinutes } }
                .toList()
                .sortedByDescending { it.second }
                .toMap()
        }
    }

    val avgMinutesPerBucket = remember(periodInfo.bars, totalPeriodMinutes) {
        if (periodInfo.bars.isEmpty()) 0 else (totalPeriodMinutes.toFloat() / periodInfo.bars.size).roundToInt()
    }

    val mostProductiveBucket = remember(periodInfo.bars) {
        val top = periodInfo.bars.maxByOrNull { it.second }
        if (top != null && top.second > 0) "${top.first} (${top.second}m)" else "—"
    }

    val completedHabitsCount = remember(habits, todayStr) {
        habits.count { h -> h.completedDatesCsv.split(",").any { it.trim() == todayStr } }
    }
    val bestStreak = remember(habits) {
        habits.maxOfOrNull { it.currentStreak } ?: 0
    }

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

    val previousPeriodTotalMinutes = remember(focusSessions, periodInfo) {
        val windowDuration = periodInfo.endMs - periodInfo.startMs
        val prevStart = periodInfo.startMs - windowDuration
        val prevEnd = periodInfo.startMs - 1
        focusSessions.filter { it.timestamp in prevStart..prevEnd }.sumOf { it.durationMinutes }
    }
    val periodDiff = totalPeriodMinutes - previousPeriodTotalMinutes

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header & Period Filter Pills
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Estatísticas & Histórico",
                fontSize = 23.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.4).sp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Compreenda seus padrões com clareza e ritmo saudável",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .tutorialTarget("stats_period_selector"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatsPeriod.entries.forEach { period ->
                    val isSelected = selectedPeriod == period
                    Surface(
                        shape = CircleShape,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        shadowElevation = if (isSelected) 2.dp else 0.dp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                },
                                shape = CircleShape
                            )
                            .clickable { selectedPeriod = period }
                    ) {
                        Text(
                            text = period.label,
                            fontSize = 12.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp)
                        )
                    }
                }
            }
        }

        // 2. Productivity Overview Card (Clean 2-stat highlight)
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    RoundedCornerShape(22.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Visão Geral de Produtividade",
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Surface(
                        shape = CircleShape,
                        color = if (periodDiff >= 0) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (periodDiff >= 0) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                                contentDescription = null,
                                tint = if (periodDiff >= 0) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (periodDiff >= 0) "+${periodDiff}m vs anterior" else "${periodDiff}m vs anterior",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (periodDiff >= 0) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Foco Hoje",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${dailyFocusMinutes / 60}h ${dailyFocusMinutes % 60}m",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Foco Semanal",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${weeklyFocusMinutes / 60}h ${weeklyFocusMinutes % 60}m",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // 3. Tactile Rhythm Bar Chart Card
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    RoundedCornerShape(22.dp)
                )
                .tutorialTarget("stats_focus_chart")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ritmo ${selectedPeriod.label}",
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = periodInfo.title,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { periodOffset-- },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronLeft,
                                contentDescription = "Período Anterior",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { if (periodOffset < 0) periodOffset++ },
                            enabled = periodOffset < 0,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Próximo Período",
                                tint = if (periodOffset < 0) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                                }
                            )
                        }
                    }
                }

                val maxMins = (periodInfo.bars.maxOfOrNull { it.second } ?: 60).coerceAtLeast(60)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(136.dp)
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    periodInfo.bars.forEach { (label, mins, isCurrent) ->
                        val heightFraction = (mins.toFloat() / maxMins.toFloat()).coerceIn(0.12f, 1f)

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(if (periodInfo.bars.size > 8) 14.dp else 22.dp)
                                    .fillMaxHeight(heightFraction)
                                    .background(
                                        color = if (isCurrent) {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                        },
                                        shape = CircleShape
                                    )
                                    .padding(2.dp),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                if (mins > 0) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                color = if (isCurrent) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.78f)
                                                },
                                                shape = CircleShape
                                            )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = label,
                                fontSize = if (periodInfo.bars.size > 8) 9.5.sp else 11.sp,
                                fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.SemiBold,
                                color = if (isCurrent) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                            )
                            Text(
                                text = "Média: ${avgMinutesPerBucket}m",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = "Pico: $mostProductiveBucket",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }

        // 4. Category Pie Chart Card (Preserved & Styled)
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    RoundedCornerShape(22.dp)
                )
                .tutorialTarget("stats_category_pie")
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
                    Text(
                        text = "Distribuição por Categoria",
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = selectedPeriod.label,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (periodSessions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Nenhuma sessão de foco registrada neste período.",
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    val sumMins = categoryTotals.values.sum().coerceAtLeast(1)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(132.dp)
                                .padding(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                var currentAngle = -90f
                                val strokeWidth = 22.dp.toPx()
                                val diameter = size.minDimension - strokeWidth
                                val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
                                val arcSize = Size(diameter, diameter)

                                categoryTotals.entries.forEachIndexed { idx, (cat, mins) ->
                                    val sweep = (mins.toFloat() / sumMins.toFloat()) * 360f
                                    val color = categoryColorMap[cat] ?: palette[idx % palette.size]
                                    drawArc(
                                        color = color,
                                        startAngle = currentAngle,
                                        sweepAngle = sweep,
                                        useCenter = false,
                                        topLeft = topLeft,
                                        size = arcSize,
                                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                                    )
                                    currentAngle += sweep
                                }
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${totalPeriodMinutes}m",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Total",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            categoryTotals.entries.forEachIndexed { idx, (cat, mins) ->
                                val pct = ((mins.toFloat() / sumMins.toFloat()) * 100f).roundToInt()
                                val color = categoryColorMap[cat] ?: palette[idx % palette.size]
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
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = cat,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = "$pct% (${mins}m)",
                                        fontSize = 11.5.sp,
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

        // 5. Compact Habits Summary Card
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    RoundedCornerShape(22.dp)
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Eco,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Hábitos de Hoje",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (habits.isEmpty()) {
                                "Nenhum hábito cadastrado"
                            } else {
                                "$completedHabitsCount de ${habits.size} concluídos hoje"
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (bestStreak > 0) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalFireDepartment,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "${bestStreak}d",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
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
