package com.example.ui.screens.organization

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Task
import com.example.ui.MainViewModel
import com.example.ui.screens.calendar.CalendarScreen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import com.example.util.squashBounceClickable
import com.example.util.staggeredEntrance

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizationScreen(
    viewModel: MainViewModel,
    onNavigateToTimerWithTask: (Task) -> Unit,
    isInitialLaunch: Boolean = false
) {
    val selectedTabIndex by viewModel.organizationSubTab.collectAsState()
    val isNoteEditorOpen by viewModel.isNoteEditorOpen.collectAsState()
    val tabs = listOf(
        Triple("Tarefas", Icons.Default.Checklist, "organization_tab_tasks"),
        Triple("Hábitos", Icons.Default.LocalFireDepartment, "organization_tab_habits"),
        Triple("Calendário", Icons.Default.CalendarMonth, "organization_tab_calendar")
    )

    Column(modifier = Modifier.fillMaxSize()) {
        if (!isNoteEditorOpen) {
            // Top Segmented Pill Navigation with Animated Sliding Indicator
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
                    .staggeredEntrance(index = 0, enabled = isInitialLaunch)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                        val totalW = maxWidth
                        val tabW = totalW / tabs.size
                        val indicatorOffset by animateDpAsState(
                            targetValue = tabW * selectedTabIndex,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "tabIndicatorSlide"
                        )

                        // Smooth Sliding Background Pill
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 2.5.dp,
                            modifier = Modifier
                                .offset(x = indicatorOffset)
                                .width(tabW)
                                .height(38.dp)
                        ) {}

                        // Clickable Tab Labels
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            tabs.forEachIndexed { index, (title, icon, testTag) ->
                                val isSelected = selectedTabIndex == index
                                val contentColor by animateColorAsState(
                                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    animationSpec = tween(220),
                                    label = "tabContentColor"
                                )

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(CircleShape)
                                        .squashBounceClickable { viewModel.setOrganizationSubTab(index) }
                                        .testTag(testTag),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = contentColor,
                                            modifier = Modifier.size(17.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = title,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                            color = contentColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Tab Content
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTabIndex) {
                0 -> TasksSubTab(
                    viewModel = viewModel,
                    onStartPomodoroForTask = { task ->
                        viewModel.linkTaskToPomodoro(task)
                        onNavigateToTimerWithTask(task)
                    }
                )
                1 -> HabitsSubTab(viewModel = viewModel)
                2 -> CalendarScreen(
                    viewModel = viewModel,
                    onStartPomodoroForTask = { task ->
                        viewModel.linkTaskToPomodoro(task)
                        onNavigateToTimerWithTask(task)
                    }
                )
            }
        }
    }
}
