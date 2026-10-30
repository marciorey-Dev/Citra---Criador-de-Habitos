package com.example.ui.screens.organization

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Task
import com.example.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizationScreen(
    viewModel: MainViewModel,
    onNavigateToTimerWithTask: (Task) -> Unit
) {
    val selectedTabIndex by viewModel.organizationSubTab.collectAsState()
    val isNoteEditorOpen by viewModel.isNoteEditorOpen.collectAsState()
    val tabs = listOf(
        Triple("Tarefas", Icons.Default.Assignment, "organization_tab_tasks"),
        Triple("Hábitos", Icons.Default.Whatshot, "organization_tab_habits"),
        Triple("Notas", Icons.Default.Description, "organization_tab_notes")
    )

    Column(modifier = Modifier.fillMaxSize()) {
        if (!isNoteEditorOpen) {
            // Top Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Organização",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Tarefas, Hábitos Diários & Anotações",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Top Tab Switcher
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEachIndexed { index, (title, icon, testTag) ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { viewModel.setOrganizationSubTab(index) },
                        icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        text = {
                            Text(
                                text = title,
                                fontSize = 13.sp,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        modifier = Modifier.testTag(testTag)
                    )
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
                2 -> NotesSubTab(viewModel = viewModel)
            }
        }
    }
}
