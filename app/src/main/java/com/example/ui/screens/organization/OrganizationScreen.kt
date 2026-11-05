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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizationScreen(
    viewModel: MainViewModel,
    onNavigateToTimerWithTask: (Task) -> Unit
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
            // Top Segmented Pill Navigation
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(5.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        tabs.forEachIndexed { index, (title, icon, testTag) ->
                            val isSelected = selectedTabIndex == index
                            Surface(
                                shape = CircleShape,
                                color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                                shadowElevation = if (isSelected) 2.dp else 0.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(CircleShape)
                                    .clickable { viewModel.setOrganizationSubTab(index) }
                                    .testTag(testTag)
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 9.dp, horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = title,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
