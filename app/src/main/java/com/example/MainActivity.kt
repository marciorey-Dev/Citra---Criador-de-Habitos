package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.notifications.NotificationHelper
import com.example.ui.MainViewModel
import com.example.ui.components.AchievementsDialog
import com.example.ui.components.BackupDialog
import com.example.ui.components.CreditsDialog
import com.example.ui.components.WhatsNewDialog
import com.example.ui.navigation.Screen
import com.example.ui.screens.calendar.CalendarScreen
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.organization.OrganizationScreen
import com.example.ui.screens.organization.QuickAddTaskDialog
import com.example.ui.screens.settings.SettingsDialog
import com.example.ui.screens.stats.StatsScreen
import com.example.ui.screens.timer.TimerScreen
import com.example.ui.theme.HabitCraftTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val isDarkThemeSetting by viewModel.isDarkTheme.collectAsState()
            val themeAccent by viewModel.themeAccent.collectAsState()
            val defaultStartTab by viewModel.defaultStartTab.collectAsState()
            val keepScreenOn by viewModel.keepScreenOn.collectAsState()
            val pomodoroState by viewModel.pomodoroState.collectAsState()
            val categories by viewModel.categories.collectAsState()
            val isNoteEditorOpen by viewModel.isNoteEditorOpen.collectAsState()
            val whatsNewVisible by viewModel.whatsNewVisible.collectAsState()

            val systemDark = isSystemInDarkTheme()
            val effectiveDark = isDarkThemeSetting ?: systemDark

            // Handle Keep Screen On during active Timer
            DisposableEffect(keepScreenOn, pomodoroState.isRunning) {
                if (keepScreenOn && pomodoroState.isRunning) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                onDispose {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            HabitCraftTheme(darkTheme = effectiveDark, accent = themeAccent) {
                val snackbarHostState = remember { SnackbarHostState() }
                val coroutineScope = rememberCoroutineScope()

                val screens = remember { Screen.allScreens }

                // Initial page based on defaultStartTab preference
                val initialPageIndex = remember(defaultStartTab) {
                    when (defaultStartTab) {
                        "TIMER" -> 1
                        "ORGANIZATION", "NOTES" -> 2
                        "CALENDAR" -> 3
                        "STATS" -> 4
                        else -> 0 // "HOME"
                    }
                }

                LaunchedEffect(defaultStartTab) {
                    if (defaultStartTab == "NOTES") {
                        viewModel.setOrganizationSubTab(2)
                    }
                }

                // HorizontalPager state for fast swipe between menus
                val pagerState = rememberPagerState(
                    initialPage = initialPageIndex,
                    pageCount = { screens.size }
                )

                var showMenu by remember { mutableStateOf(false) }
                var showAchievementsDialog by remember { mutableStateOf(false) }
                var showBackupDialog by remember { mutableStateOf(false) }
                var showCreditsDialog by remember { mutableStateOf(false) }
                var showSettingsDialog by remember { mutableStateOf(false) }
                var showQuickAddTaskDialog by remember { mutableStateOf(false) }

                val achievements by viewModel.achievements.collectAsState()

                // Request notification permission on Android 13+
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) {}

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                // Listen for snackbar feedback
                LaunchedEffect(Unit) {
                    viewModel.userMessage.collectLatest { msg ->
                        snackbarHostState.showSnackbar(
                            message = msg,
                            duration = SnackbarDuration.Short
                        )
                    }
                }

                // Handle back press to return to Home or Timer
                BackHandler(enabled = pagerState.currentPage != 0) {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(0)
                    }
                }

                val currentScreen = screens[pagerState.currentPage]

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        if (!isNoteEditorOpen) {
                            TopAppBar(
                            title = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Screen Title (Persistent "HabitCraft" title removed per request)
                                    Text(
                                        text = currentScreen.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    // Floating Top Mini-timer counter when timer is active outside the Timer tab
                                    val isTimerActive = pomodoroState.isRunning || pomodoroState.isPaused
                                    if (isTimerActive && pagerState.currentPage != 1) {
                                        val mins = pomodoroState.remainingSeconds / 60
                                        val secs = pomodoroState.remainingSeconds % 60
                                        val formatted = String.format("%02d:%02d", mins, secs)

                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = if (pomodoroState.isRunning) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier
                                                .clickable {
                                                    coroutineScope.launch { pagerState.animateScrollToPage(1) }
                                                }
                                                .testTag("top_floating_timer_pill")
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(text = "🍊", fontSize = 12.sp)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = formatted,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = if (pomodoroState.isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = if (pomodoroState.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp),
                                                    tint = if (pomodoroState.isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                            actions = {
                                // Direct Settings Gear Button
                                IconButton(
                                    onClick = { showSettingsDialog = true },
                                    modifier = Modifier.testTag("top_settings_gear_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Configurações",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // 3-Dots Menu Button
                                Box {
                                    IconButton(
                                        onClick = { showMenu = true },
                                        modifier = Modifier.testTag("top_menu_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "Mais Opções",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = showMenu,
                                        onDismissRequest = { showMenu = false }
                                    ) {
                                        // Configurações
                                        DropdownMenuItem(
                                            text = { Text("Configurações") },
                                            leadingIcon = {
                                                Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            },
                                            onClick = {
                                                showMenu = false
                                                showSettingsDialog = true
                                            }
                                        )

                                        // Conquistas (Renamed per user request)
                                        DropdownMenuItem(
                                            text = { Text("Conquistas") },
                                            leadingIcon = {
                                                Icon(Icons.Default.Stars, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            },
                                            onClick = {
                                                showMenu = false
                                                showAchievementsDialog = true
                                            }
                                        )

                                        // Backup & Restauração
                                        DropdownMenuItem(
                                            text = { Text("Backup & Restauração") },
                                            leadingIcon = {
                                                Icon(Icons.Default.Backup, contentDescription = null)
                                            },
                                            onClick = {
                                                showMenu = false
                                                showBackupDialog = true
                                            }
                                        )

                                        Divider()

                                        // Sobre (Renamed from Créditos e Copyright)
                                        DropdownMenuItem(
                                            text = { Text("Sobre") },
                                            leadingIcon = {
                                                Icon(Icons.Default.Info, contentDescription = null)
                                            },
                                            onClick = {
                                                showMenu = false
                                                showCreditsDialog = true
                                            }
                                        )
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    }
                },
                snackbarHost = {
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier.padding(16.dp)
                    ) { data ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.inverseSurface,
                            shadowElevation = 6.dp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🍊",
                                    fontSize = 20.sp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = data.visuals.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.inverseOnSurface,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                },
                bottomBar = {
                    if (!isNoteEditorOpen) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary,
                            tonalElevation = 6.dp,
                            windowInsets = WindowInsets.navigationBars,
                            modifier = Modifier.testTag("main_bottom_nav")
                        ) {
                            screens.forEachIndexed { index, screen ->
                                val selected = pagerState.currentPage == index
                                NavigationBarItem(
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                            contentDescription = screen.title
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = screen.title,
                                            fontSize = 11.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    selected = selected,
                                    onClick = {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(index)
                                        }
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.testTag("nav_item_${screen.route}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                // HorizontalPager: allows swiping quickly between the tabs!
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = !isNoteEditorOpen,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) { page ->
                        when (screens[page]) {
                            Screen.Home -> HomeScreen(
                                viewModel = viewModel,
                                onNavigateToTimer = {
                                    coroutineScope.launch { pagerState.animateScrollToPage(1) }
                                },
                                onNavigateToOrganization = {
                                    coroutineScope.launch { pagerState.animateScrollToPage(2) }
                                },
                                onQuickAddTask = {
                                    showQuickAddTaskDialog = true
                                },
                                onQuickAddNote = {
                                    viewModel.triggerCreateNote()
                                    coroutineScope.launch { pagerState.animateScrollToPage(2) }
                                }
                            )
                            Screen.Timer -> TimerScreen(
                                viewModel = viewModel,
                                onNavigateToTasks = {
                                    coroutineScope.launch { pagerState.animateScrollToPage(2) }
                                }
                            )
                            Screen.Organization -> OrganizationScreen(
                                viewModel = viewModel,
                                onNavigateToTimerWithTask = {
                                    coroutineScope.launch { pagerState.animateScrollToPage(1) }
                                }
                            )
                            Screen.Calendar -> CalendarScreen(
                                viewModel = viewModel,
                                onStartPomodoroForTask = {
                                    viewModel.linkTaskToPomodoro(it)
                                    coroutineScope.launch { pagerState.animateScrollToPage(1) }
                                }
                            )
                            Screen.Stats -> StatsScreen(
                                viewModel = viewModel
                            )
                        }
                    }
                }

                if (showSettingsDialog) {
                    SettingsDialog(
                        viewModel = viewModel,
                        onDismiss = { showSettingsDialog = false }
                    )
                }

                if (showAchievementsDialog) {
                    AchievementsDialog(
                        achievements = achievements,
                        onDismiss = { showAchievementsDialog = false }
                    )
                }

                if (showBackupDialog) {
                    BackupDialog(
                        onExportBackup = { viewModel.exportBackupJson() },
                        onRestoreBackup = { viewModel.restoreBackupJson(it) },
                        onDismiss = { showBackupDialog = false }
                    )
                }

                if (whatsNewVisible) {
                    WhatsNewDialog(
                        onDismiss = { viewModel.dismissWhatsNew() }
                    )
                }

                if (showCreditsDialog) {
                    CreditsDialog(onDismiss = { showCreditsDialog = false })
                }

                // Quick Add Task Dialog from Home shortcut
                if (showQuickAddTaskDialog) {
                    QuickAddTaskDialog(
                        categories = categories,
                        onDismiss = { showQuickAddTaskDialog = false },
                        onConfirm = { title, category, priority, dueText, dueTimestamp, minutesBefore ->
                            viewModel.addTask(title, category, priority, dueText, dueTimestamp, minutesBefore)
                            showQuickAddTaskDialog = false
                        }
                    )
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.triggerAutoBackup()
        // Ongoing floating notification when user leaves app while timer is running
        val state = viewModel.pomodoroState.value
        if (state.isRunning || state.isPaused) {
            NotificationHelper.showTimerOngoingNotification(
                context = this,
                remainingSeconds = state.remainingSeconds,
                phaseLabel = state.currentPhase.label,
                isPaused = state.isPaused
            )
        }
    }
}
