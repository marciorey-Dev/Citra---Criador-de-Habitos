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
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import com.example.data.auth.AuthRepository
import com.example.ui.MainViewModel
import com.example.ui.PomodoroPhase
import com.example.ui.components.AchievementsDialog
import com.example.ui.components.BackupDialog
import com.example.ui.components.CitraMascote
import com.example.ui.components.CreditsDialog
import com.example.ui.components.MascotHumor
import com.example.ui.components.WhatsNewDialog
import com.example.ui.navigation.Screen
import com.example.ui.screens.auth.AuthScreen
import com.example.ui.screens.calendar.CalendarScreen
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.organization.NotesSubTab
import com.example.ui.screens.organization.OrganizationScreen
import com.example.ui.screens.organization.QuickAddTaskDialog
import com.example.ui.screens.profile.ProfileScreen
import com.example.ui.screens.settings.SettingsDialog
import com.example.ui.screens.timer.TimerScreen
import com.example.ui.theme.CitraTheme
import com.example.ui.tutorial.CitraSpotlightOverlay
import com.example.ui.tutorial.CitraTutorialArea
import com.example.ui.tutorial.tutorialTarget
import com.example.util.AmbientBreakMusicManager
import com.example.notifications.ActiveAlarmData
import com.example.notifications.ActiveAlarmManager
import com.example.ui.screens.alarm.AlarmScreen
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val authRepository by lazy { AuthRepository.getInstance(this) }
    private var pendingWidgetAction by mutableStateOf<String?>(null)
    private var pendingWidgetNoteId by mutableStateOf<Long?>(null)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkAlarmIntent(intent)
        handleWidgetIntent(intent)
    }

    private fun handleWidgetIntent(intent: android.content.Intent?) {
        if (intent == null) return
        val action = intent.action
        if (action != null && action.startsWith("com.example.citra.")) {
            pendingWidgetAction = action
            pendingWidgetNoteId = intent.getLongExtra(com.example.widget.CitraWidgetHelper.EXTRA_NOTE_ID, -1L).takeIf { it > 0 }
        }
    }

    private fun checkAlarmIntent(intent: android.content.Intent?) {
        ActiveAlarmManager.checkAndRestore(this)
        if (intent?.getBooleanExtra(NotificationHelper.EXTRA_LOOP_ALARM, false) == true) {
            val id = intent.getIntExtra(NotificationHelper.EXTRA_ID, -1)
            val type = intent.getStringExtra(NotificationHelper.EXTRA_TYPE) ?: NotificationHelper.TYPE_TASK
            val title = intent.getStringExtra(NotificationHelper.EXTRA_TITLE) ?: "Citra"
            val taskId = intent.getLongExtra(NotificationHelper.EXTRA_TASK_ID, -1L)
            val habitId = intent.getLongExtra(NotificationHelper.EXTRA_HABIT_ID, -1L)
            val soundUri = intent.getStringExtra(NotificationHelper.EXTRA_SOUND_URI) ?: ""
            ActiveAlarmManager.onAlarmTriggered(
                this,
                ActiveAlarmData(
                    notificationId = id,
                    type = type,
                    title = title,
                    taskId = taskId,
                    habitId = habitId,
                    soundUri = soundUri,
                    loopAlarm = true
                )
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkAlarmIntent(intent)
        handleWidgetIntent(intent)

        setContent {
            val isDarkThemeSetting by viewModel.isDarkTheme.collectAsState()
            val themeAccent by viewModel.themeAccent.collectAsState()
            val defaultStartTab by viewModel.defaultStartTab.collectAsState()
            val keepScreenOn by viewModel.keepScreenOn.collectAsState()
            val pomodoroState by viewModel.pomodoroState.collectAsState()
            val categories by viewModel.categories.collectAsState()
            val isNoteEditorOpen by viewModel.isNoteEditorOpen.collectAsState()
            val whatsNewVisible by viewModel.whatsNewVisible.collectAsState()

            val notificationSettings by viewModel.notificationSettings.collectAsState()
            val currentUser by authRepository.currentUser.collectAsState()
            val isGuestMode by authRepository.isGuestMode.collectAsState()
            val isAuthReady by authRepository.isAuthReady.collectAsState()
            val syncFrequency by viewModel.syncFrequency.collectAsState()
            val lastSyncTimestamp by viewModel.lastSyncTimestamp.collectAsState()

            val activeAlarm by ActiveAlarmManager.activeAlarm.collectAsState()
            val hasSocialNotifications by viewModel.hasSocialNotifications.collectAsState()

            LaunchedEffect(Unit) {
                ActiveAlarmManager.checkAndRestore(this@MainActivity)
            }

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

            // Sync Ambient Break Music with notificationSettings and Timer Break phase
            LaunchedEffect(notificationSettings.ambientBreakMusicInApp, notificationSettings.ambientMusicVolume) {
                AmbientBreakMusicManager.init(
                    this@MainActivity,
                    alwaysPlaying = notificationSettings.ambientBreakMusicInApp,
                    volume = notificationSettings.ambientMusicVolume
                )
            }

            LaunchedEffect(pomodoroState.currentPhase, pomodoroState.isRunning) {
                val isBreak = pomodoroState.isRunning && (pomodoroState.currentPhase == PomodoroPhase.BREAK)
                AmbientBreakMusicManager.onBreakStateChanged(this@MainActivity, isBreak)
            }

            val currentDensity = androidx.compose.ui.platform.LocalDensity.current
            val screenWidthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
            // 390dp reference screen anchor
            val scaleFactor = (screenWidthDp.toFloat() / 390f).coerceIn(0.85f, 1.15f)
            val clampedFontScale = currentDensity.fontScale.coerceIn(0.9f, 1.1f)
            val normalizedDensity = androidx.compose.ui.unit.Density(
                density = currentDensity.density * scaleFactor,
                fontScale = clampedFontScale
            )

            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides normalizedDensity
            ) {
                CitraTheme(darkTheme = effectiveDark, accent = themeAccent) {
                // Save any open note immediately before showing the 00:00 alarm screen
                LaunchedEffect(activeAlarm) {
                    if (activeAlarm != null && isNoteEditorOpen) {
                        com.example.ui.screens.organization.notes.NoteEditorSaveBridge.requestSave()
                    }
                }

                // If active alarm loop is ringing, display AlarmScreen immediately
                activeAlarm?.let { currentAlarm ->
                    AlarmScreen(
                        alarmData = currentAlarm,
                        onDismiss = {
                            val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                            if (currentAlarm.notificationId > 0) nm.cancel(currentAlarm.notificationId)
                            ActiveAlarmManager.dismissAlarm(this@MainActivity)
                        },
                        onSnooze = {
                            val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                            if (currentAlarm.notificationId > 0) nm.cancel(currentAlarm.notificationId)
                            val snoozeTime = System.currentTimeMillis() + 10 * 60 * 1000L
                            if (currentAlarm.type == NotificationHelper.TYPE_HABIT) {
                                NotificationHelper.scheduleHabitSnooze(this@MainActivity, currentAlarm.habitId, currentAlarm.title, snoozeTime, currentAlarm.soundUri)
                            } else {
                                NotificationHelper.scheduleTaskSnooze(this@MainActivity, currentAlarm.taskId, currentAlarm.title, snoozeTime, currentAlarm.soundUri)
                            }
                            ActiveAlarmManager.dismissAlarm(this@MainActivity)
                        }
                    )
                    return@CitraTheme
                }

                val isStartupScanDone by viewModel.isStartupScanDone.collectAsState()

                LaunchedEffect(Unit) {
                    viewModel.performStartupFolderScan()
                    viewModel.runDueFinanceLinksOnStartup()
                }

                // Organic Loading Screen: Wait until auth state and resources are resolved
                if (!isAuthReady || !isStartupScanDone) {
                    CitraLoadingScreen(loadingText = "Carregando recursos do Citra...")
                    return@CitraTheme
                }

                // If user is not logged in and hasn't chosen guest mode yet, show first-launch AuthScreen
                if (currentUser == null && !isGuestMode) {
                    AuthScreen(
                        authRepository = authRepository,
                        onAuthSuccess = { /* currentUser will update via StateFlow */ },
                        onContinueAsGuest = { /* isGuestMode will update via StateFlow */ }
                    )
                    return@CitraTheme
                }

                val snackbarHostState = remember { SnackbarHostState() }
                val coroutineScope = rememberCoroutineScope()
                val financesEnabled by viewModel.financesEnabled.collectAsState()

                val screens = remember(financesEnabled) { Screen.screensFor(financesEnabled) }

                // Initial page based on defaultStartTab preference
                val initialPageIndex = remember(defaultStartTab) {
                    when (defaultStartTab) {
                        "TIMER" -> 1
                        "ORGANIZATION" -> 2
                        "NOTES" -> 3
                        "CALENDAR" -> 2
                        "STATS", "PROFILE" -> 4
                        else -> 0 // "HOME"
                    }
                }

                LaunchedEffect(defaultStartTab) {
                    if (defaultStartTab == "CALENDAR") {
                        viewModel.setOrganizationSubTab(2)
                    }
                }

                // HorizontalPager state for fast swipe between menus
                val pagerState = rememberPagerState(
                    initialPage = initialPageIndex,
                    pageCount = { screens.size }
                )

                // Handle Widget Actions
                LaunchedEffect(pendingWidgetAction, pendingWidgetNoteId) {
                    val action = pendingWidgetAction ?: return@LaunchedEffect
                    when (action) {
                        com.example.widget.CitraWidgetHelper.ACTION_NAVIGATE_TIMER -> {
                            pagerState.scrollToPage(1)
                        }
                        com.example.widget.CitraWidgetHelper.ACTION_NAVIGATE_HABITS -> {
                            viewModel.setOrganizationSubTab(0)
                            pagerState.scrollToPage(2)
                        }
                        com.example.widget.CitraWidgetHelper.ACTION_NAVIGATE_NEW_NOTE -> {
                            pagerState.scrollToPage(3)
                            viewModel.triggerCreateNote()
                        }
                        com.example.widget.CitraWidgetHelper.ACTION_NAVIGATE_OPEN_NOTE -> {
                            val noteId = pendingWidgetNoteId
                            if (noteId != null && noteId > 0) {
                                val targetNote = viewModel.notes.value.find { it.id == noteId }
                                    ?: kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        viewModel.repository.noteDao.getNoteById(noteId)
                                    }
                                if (targetNote != null) {
                                    viewModel.openNote(targetNote)
                                    pagerState.scrollToPage(3)
                                }
                            } else {
                                pagerState.scrollToPage(3)
                            }
                        }
                    }
                    pendingWidgetAction = null
                    pendingWidgetNoteId = null
                }

                var showMenu by remember { mutableStateOf(false) }
                var showAchievementsDialog by remember { mutableStateOf(false) }
                var showBackupDialog by remember { mutableStateOf(false) }
                var showCreditsDialog by remember { mutableStateOf(false) }
                var showSettingsDialog by remember { mutableStateOf(false) }
                var showAddCategoryDialog by remember { mutableStateOf(false) }
                var showQuickAddTaskDialog by remember { mutableStateOf(false) }
                var showAuthDialog by remember { mutableStateOf(false) }
                var showInboxDialog by remember { mutableStateOf(false) }
                val unreadInboxCount by viewModel.unreadInboxCount.collectAsState()

                val appBgUri by viewModel.appBgUri.collectAsState()
                val appBgBlur by viewModel.appBgBlur.collectAsState()
                val appBgBrightness by viewModel.appBgBrightness.collectAsState()

                var processedAppBgBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

                LaunchedEffect(appBgUri, appBgBlur) {
                    val uriStr = appBgUri
                    if (!uriStr.isNullOrBlank()) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            try {
                                val boundsOpts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                val localFile = java.io.File(uriStr)
                                if (localFile.exists()) {
                                    android.graphics.BitmapFactory.decodeFile(localFile.absolutePath, boundsOpts)
                                } else {
                                    com.example.util.MediaStorageHelper.openInputStream(this@MainActivity, uriStr)?.use {
                                        android.graphics.BitmapFactory.decodeStream(it, null, boundsOpts)
                                    }
                                }
                                var sampleSize = 1
                                val maxDim = maxOf(boundsOpts.outWidth, boundsOpts.outHeight)
                                while (maxDim / sampleSize > 1080) {
                                    sampleSize *= 2
                                }
                                val decodeOpts = android.graphics.BitmapFactory.Options().apply {
                                    inSampleSize = sampleSize
                                    inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                                }
                                val original = if (localFile.exists()) {
                                    android.graphics.BitmapFactory.decodeFile(localFile.absolutePath, decodeOpts)
                                } else {
                                    com.example.util.MediaStorageHelper.openInputStream(this@MainActivity, uriStr)?.use {
                                        android.graphics.BitmapFactory.decodeStream(it, null, decodeOpts)
                                    }
                                }
                                if (original != null) {
                                    val radius = (appBgBlur * 20).toInt().coerceAtLeast(0)
                                    processedAppBgBitmap = if (radius > 0) com.example.util.FastBlur.blur(original, radius) else original
                                } else {
                                    processedAppBgBitmap = null
                                }
                            } catch (_: Throwable) {
                                processedAppBgBitmap = null
                            }
                        }
                    } else {
                        processedAppBgBitmap = null
                    }
                }

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

                // Auto-sync according to configured frequency (Default daily, or 1h, weekly, on app open)
                // Auto-sync & Cloud settings loader (Silent save, load on user account entry)
                val syncEnabled by viewModel.syncEnabled.collectAsState()
                LaunchedEffect(currentUser?.uid, syncFrequency, syncEnabled) {
                    val user = currentUser
                    if (user != null && !user.isGuest) {
                        try {
                            val cloudData = authRepository.fetchCloudUserData()
                            if (cloudData != null) {
                                // Load and apply user settings saved in the cloud for this account
                                viewModel.applyCloudSettings(cloudData.settings)

                                if (viewModel.tasks.value.isEmpty() && viewModel.habits.value.isEmpty()) {
                                    cloudData.backupJson?.let { viewModel.restoreBackupJson(it) }
                                    cloudData.notesJson?.let { viewModel.importNotesJson(it) }
                                    cloudData.financesJson?.let { viewModel.restoreFinancesJson(it) }
                                }
                            }
                        } catch (_: Exception) {}

                        if (syncEnabled) {
                            val now = System.currentTimeMillis()
                            val shouldSync = when (syncFrequency) {
                                "ON_APP_OPEN" -> true
                                "1_HOUR" -> (now - lastSyncTimestamp) >= 60 * 60 * 1000L
                                "WEEKLY" -> (now - lastSyncTimestamp) >= 7 * 24 * 60 * 60 * 1000L
                                else -> (now - lastSyncTimestamp) >= 24 * 60 * 60 * 1000L // "DAILY"
                            }
                            if (shouldSync) {
                                viewModel.updateLastSyncTimestamp(now)
                                try {
                                    val cloudData = authRepository.fetchCloudUserData()
                                    if (cloudData != null) {
                                        cloudData.notesJson?.let { viewModel.importNotesJson(it) }
                                        cloudData.financesJson?.let { viewModel.restoreFinancesJson(it) }
                                    }
                                    val financesJson = viewModel.exportFinancesJson()
                                    val settingsMap = viewModel.getSettingsMapForCloud()
                                    authRepository.syncAllUserDataToCloud(
                                        tasks = viewModel.tasks.value,
                                        habits = viewModel.habits.value,
                                        notes = viewModel.notes.value,
                                        categories = viewModel.categories.value,
                                        focusSessions = viewModel.focusSessions.value,
                                        achievements = viewModel.achievements.value,
                                        settingsMap = settingsMap,
                                        financesJson = financesJson
                                    )
                                } catch (_: Exception) {}
                            }
                        }
                    }
                }

                // Handle back press to return to Home or Timer (instant transition without intermediate scroll lag)
                BackHandler(enabled = pagerState.currentPage != 0) {
                    coroutineScope.launch {
                        pagerState.scrollToPage(0)
                    }
                }

                val organizationSubTab by viewModel.organizationSubTab.collectAsState()

                // Trigger guided tutorial when entering each area for the first time
                LaunchedEffect(pagerState.currentPage, organizationSubTab, screens) {
                    when (screens.getOrNull(pagerState.currentPage)) {
                        Screen.Home -> viewModel.startTutorial(CitraTutorialArea.HOME)
                        Screen.Timer -> viewModel.startTutorial(CitraTutorialArea.TIMER)
                        Screen.Organization -> {
                            if (organizationSubTab == 0) {
                                viewModel.startTutorial(CitraTutorialArea.TASKS)
                            } else if (organizationSubTab == 1) {
                                viewModel.startTutorial(CitraTutorialArea.HABITS)
                            }
                        }
                        Screen.Notes -> viewModel.startTutorial(CitraTutorialArea.NOTES)
                        Screen.Finances -> if (financesEnabled) viewModel.startTutorial(CitraTutorialArea.FINANCES)
                        Screen.Profile -> viewModel.startTutorial(CitraTutorialArea.STATS)
                        else -> {}
                    }
                }

                val currentScreen = screens[pagerState.currentPage]

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        val isTimerImmersive = (pomodoroState.isRunning || pomodoroState.isPaused) &&
                                screens[pagerState.currentPage] == Screen.Timer
                        if (!isNoteEditorOpen && !isTimerImmersive) {
                            TopAppBar(
                                navigationIcon = {
                                    if (screens[pagerState.currentPage] == Screen.Home) {
                                        Spacer(modifier = Modifier.width(48.dp))
                                    }
                                },
                                title = {
                                    val isTimerActive = pomodoroState.isRunning || pomodoroState.isPaused
                                    if (screens[pagerState.currentPage] == Screen.Home) {
                                        Box(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Text(
                                                    text = "🍊 Citra",
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 20.sp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    textAlign = TextAlign.Center
                                                )
                                                if (isTimerActive) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    val displaySec = if (pomodoroState.isStopwatchMode) pomodoroState.stopwatchSeconds else pomodoroState.remainingSeconds
                                                    val mins = displaySec / 60
                                                    val secs = displaySec % 60
                                                    val formatted = String.format("%02d:%02d", mins, secs)

                                                    Surface(
                                                        shape = RoundedCornerShape(16.dp),
                                                        color = if (pomodoroState.isRunning) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                                        modifier = Modifier
                                                            .clickable {
                                                                coroutineScope.launch { pagerState.scrollToPage(1) }
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
                                        }
                                    } else {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            // Floating Top Mini-timer counter when timer is active outside the Timer tab
                                            if (isTimerActive && screens[pagerState.currentPage] != Screen.Timer) {
                                                val displaySec = if (pomodoroState.isStopwatchMode) pomodoroState.stopwatchSeconds else pomodoroState.remainingSeconds
                                                val mins = displaySec / 60
                                                val secs = displaySec % 60
                                                val formatted = String.format("%02d:%02d", mins, secs)

                                                Surface(
                                                    shape = RoundedCornerShape(16.dp),
                                                    color = if (pomodoroState.isRunning) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                                    modifier = Modifier
                                                        .clickable {
                                                            coroutineScope.launch { pagerState.scrollToPage(1) }
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
                                    }
                                },
                            actions = {
                                // Settings button on "Eu" screen (page 4) per request
                                if (pagerState.currentPage == 4) {
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
                                }

                                // Notification Bell Button with Unread Badge
                                val isInboxAccountLoggedIn = currentUser != null && currentUser?.isGuest == false
                                IconButton(
                                    onClick = { showInboxDialog = true },
                                    modifier = Modifier.testTag("top_inbox_bell_button")
                                ) {
                                    BadgedBox(
                                        badge = {
                                            if (isInboxAccountLoggedIn && unreadInboxCount > 0) {
                                                Badge(
                                                    containerColor = MaterialTheme.colorScheme.primary,
                                                    contentColor = androidx.compose.ui.graphics.Color.White
                                                ) {
                                                    Text(
                                                        text = if (unreadInboxCount > 99) "99+" else "$unreadInboxCount",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Notifications,
                                            contentDescription = "Caixa de Entrada",
                                            tint = when {
                                                !isInboxAccountLoggedIn -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                                unreadInboxCount > 0 -> MaterialTheme.colorScheme.primary
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                }

                                // 3-Dots Menu Button
                                Box {
                                    IconButton(
                                        onClick = { showMenu = true },
                                        modifier = Modifier
                                            .testTag("top_menu_button")
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
                                            modifier = Modifier.tutorialTarget("top_menu_settings"),
                                            onClick = {
                                                showMenu = false
                                                showSettingsDialog = true
                                            }
                                        )

                                        // Nova Categoria
                                        DropdownMenuItem(
                                            text = { Text("Nova Categoria") },
                                            leadingIcon = {
                                                Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            },
                                            onClick = {
                                                showMenu = false
                                                showAddCategoryDialog = true
                                            }
                                        )

                                        // Conquistas (Renamed per user request)
                                        DropdownMenuItem(
                                            text = { Text("Conquistas") },
                                            leadingIcon = {
                                                Icon(Icons.Default.Stars, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            },
                                            modifier = Modifier.tutorialTarget("top_menu_achievements"),
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
                                            modifier = Modifier.tutorialTarget("top_menu_backup"),
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
                        var offsetX by remember { mutableFloatStateOf(0f) }
                        var offsetY by remember { mutableFloatStateOf(0f) }

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.inverseSurface,
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .offset {
                                    androidx.compose.ui.unit.IntOffset(
                                        offsetX.toInt(),
                                        offsetY.coerceAtLeast(0f).toInt()
                                    )
                                }
                                .pointerInput(data) {
                                    detectDragGestures(
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            offsetX += dragAmount.x
                                            offsetY += dragAmount.y
                                        },
                                        onDragEnd = {
                                            if (kotlin.math.abs(offsetX) > 50f || offsetY > 40f) {
                                                data.dismiss()
                                            } else {
                                                offsetX = 0f
                                                offsetY = 0f
                                            }
                                        }
                                    )
                                }
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
                    val isTimerImmersive = (pomodoroState.isRunning || pomodoroState.isPaused) &&
                            screens[pagerState.currentPage] == Screen.Timer
                    if (!isNoteEditorOpen && !isTimerImmersive) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary,
                            tonalElevation = 6.dp,
                            windowInsets = WindowInsets.navigationBars,
                            modifier = Modifier
                                .tutorialTarget("bottom_navigation_bar")
                                .testTag("main_bottom_nav")
                        ) {
                            screens.forEachIndexed { index, screen ->
                                val selected = pagerState.currentPage == index
                                NavigationBarItem(
                                    icon = {
                                        BadgedBox(
                                            badge = {
                                                if (screen == Screen.Profile && hasSocialNotifications) {
                                                    Badge(
                                                        containerColor = Color(0xFFE53935),
                                                        modifier = Modifier.size(8.dp)
                                                    )
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                                contentDescription = screen.title
                                            )
                                        }
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
                                            pagerState.scrollToPage(index)
                                        }
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = Color.Transparent,
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (processedAppBgBitmap != null) {
                        Image(
                            bitmap = processedAppBgBitmap!!.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Color.Black.copy(alpha = (1f - appBgBrightness).coerceIn(0.15f, 0.85f))
                                )
                        )
                    }

                    // HorizontalPager: instant tab navigation on button clicks while preserving smooth user manual swiping
                    HorizontalPager(
                        state = pagerState,
                        userScrollEnabled = !isNoteEditorOpen && !(pomodoroState.isRunning && screens.getOrNull(pagerState.currentPage) == Screen.Timer),
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        when (screens[page]) {
                            Screen.Home -> HomeScreen(
                                viewModel = viewModel,
                                onNavigateToTimer = {
                                    coroutineScope.launch { pagerState.scrollToPage(1) }
                                },
                                onNavigateToOrganization = {
                                    coroutineScope.launch { pagerState.scrollToPage(2) }
                                },
                                onQuickAddTask = {
                                    showQuickAddTaskDialog = true
                                },
                                onQuickAddNote = {
                                    viewModel.triggerCreateNote()
                                    val notesPageIndex = screens.indexOf(Screen.Notes)
                                    if (notesPageIndex >= 0) {
                                        coroutineScope.launch { pagerState.scrollToPage(notesPageIndex) }
                                    }
                                }
                            )
                            Screen.Timer -> TimerScreen(
                                viewModel = viewModel,
                                onNavigateToTasks = {
                                    coroutineScope.launch { pagerState.scrollToPage(2) }
                                }
                            )
                            Screen.Organization -> OrganizationScreen(
                                viewModel = viewModel,
                                onNavigateToTimerWithTask = {
                                    coroutineScope.launch { pagerState.scrollToPage(1) }
                                }
                            )
                            Screen.Notes -> NotesSubTab(
                                viewModel = viewModel
                            )
                            Screen.Profile -> ProfileScreen(
                                viewModel = viewModel,
                                authRepository = authRepository,
                                onOpenSettings = { showSettingsDialog = true },
                                onOpenAuthGate = { showAuthDialog = true },
                                onNavigateToNotes = {
                                    val notesIndex = screens.indexOf(Screen.Notes)
                                    if (notesIndex >= 0) {
                                        coroutineScope.launch { pagerState.scrollToPage(notesIndex) }
                                    }
                                }
                            )
                            Screen.Calendar -> CalendarScreen(
                                viewModel = viewModel,
                                onStartPomodoroForTask = {
                                    viewModel.linkTaskToPomodoro(it)
                                    coroutineScope.launch { pagerState.scrollToPage(1) }
                                }
                            )
                            Screen.Finances -> com.example.ui.screens.finances.FinancesScreen(
                                viewModel = viewModel,
                                onOpenFinanceSettings = { showSettingsDialog = true }
                            )
                        }
                    }
                }
            }

                if (showAuthDialog) {
                    Dialog(
                        onDismissRequest = { showAuthDialog = false },
                        properties = DialogProperties(usePlatformDefaultWidth = false)
                    ) {
                        AuthScreen(
                            authRepository = authRepository,
                            onAuthSuccess = { showAuthDialog = false },
                            onContinueAsGuest = { showAuthDialog = false }
                        )
                    }
                }

                if (showSettingsDialog) {
                    SettingsDialog(
                        viewModel = viewModel,
                        onDismiss = { showSettingsDialog = false }
                    )
                }

                if (showAddCategoryDialog) {
                    com.example.ui.components.AddCategoryDialog(
                        onDismiss = { showAddCategoryDialog = false },
                        onConfirm = { name, emoji ->
                            viewModel.addCustomCategory(name, emoji)
                            showAddCategoryDialog = false
                        }
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



                if (showCreditsDialog) {
                    CreditsDialog(onDismiss = { showCreditsDialog = false })
                }

                if (showInboxDialog) {
                    com.example.ui.screens.inbox.InboxDialog(
                        viewModel = viewModel,
                        onOpenAuthGate = {
                            showInboxDialog = false
                            showAuthDialog = true
                        },
                        onDismiss = { showInboxDialog = false }
                    )
                }

                val pendingCloudFinanceRestoreJson by viewModel.pendingCloudFinanceRestoreJson.collectAsState()
                if (pendingCloudFinanceRestoreJson != null) {
                    AlertDialog(
                        onDismissRequest = { viewModel.dismissCloudFinanceRestore() },
                        icon = { Icon(Icons.Default.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        title = { Text("Restaurar os dados da nuvem?", fontWeight = FontWeight.Bold) },
                        text = {
                            Text(
                                "Encontramos um backup de Finanças guardado na sua conta Google. Deseja restaurar os lançamentos, categorias, configurações e vínculos?",
                                fontSize = 14.sp
                            )
                        },
                        confirmButton = {
                            Button(onClick = { viewModel.confirmRestoreCloudFinances() }) {
                                Text("Restaurar")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { viewModel.dismissCloudFinanceRestore() }) {
                                Text("Agora não")
                            }
                        }
                    )
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

                // Spotlight Tutorial Overlay
                val activeTutorial by viewModel.activeTutorial.collectAsState()
                if (activeTutorial != null) {
                    CitraSpotlightOverlay(
                        activeTutorial = activeTutorial!!,
                        onNextStep = { viewModel.nextTutorialStep() },
                        onSkipArea = { viewModel.skipCurrentTutorialArea() },
                        onSkipAll = { viewModel.skipAllTutorials() }
                    )
                }
            }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AmbientBreakMusicManager.onAppForegrounded(this)
    }

    override fun onResume() {
        super.onResume()
        AmbientBreakMusicManager.onAppForegrounded(this)
        ActiveAlarmManager.checkAndRestore(this)
        com.example.notifications.NextAlarmScheduler.scheduleNextAlarm(this)
        viewModel.restoreTimerSnapshotOnStartup()
    }

    override fun onPause() {
        super.onPause()
        // Note audio is paused when leaving the app
        com.example.util.NoteAudioPlaybackManager.pause()
        AmbientBreakMusicManager.onAppBackgrounded()
    }

    override fun onStop() {
        super.onStop()
        // Pause note audio and background ambient audio on leaving app
        com.example.util.NoteAudioPlaybackManager.pause()
        AmbientBreakMusicManager.onAppBackgrounded()

        viewModel.triggerAutoBackup()

        // Sync all user data to Firebase Firestore if logged in and sync is enabled
        val user = authRepository.currentUser.value
        if (viewModel.syncEnabled.value && user != null && !user.isGuest) {
            val settingsMap = mapOf<String, Any>(
                "themeAccent" to viewModel.themeAccent.value.name,
                "keepScreenOn" to viewModel.keepScreenOn.value,
                "ambientMusicInApp" to viewModel.notificationSettings.value.ambientBreakMusicInApp,
                "ambientMusicVolume" to viewModel.notificationSettings.value.ambientMusicVolume
            )
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                authRepository.syncAllUserDataToCloud(
                    tasks = viewModel.tasks.value,
                    habits = viewModel.habits.value,
                    notes = viewModel.notes.value,
                    categories = viewModel.categories.value,
                    focusSessions = viewModel.focusSessions.value,
                    achievements = viewModel.achievements.value,
                    settingsMap = settingsMap
                )
            }
            viewModel.backupFinancesNow(showToast = false)
        }

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

@Composable
fun CitraLoadingScreen(loadingText: String = "Carregando...") {
    val infiniteTransition = rememberInfiniteTransition(label = "citra_loading_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CitraMascote(
                humor = MascotHumor.FELIZ,
                olharX = 0f,
                olharY = 0f,
                tamanho = 88.dp,
                modifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "CITRA",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 4.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = loadingText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                )
            )
        }
    }
}
