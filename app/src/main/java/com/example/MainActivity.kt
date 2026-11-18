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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.example.ui.MainViewModel
import com.example.ui.PomodoroPhase
import com.example.ui.components.AchievementsDialog
import com.example.ui.components.BackupDialog
import com.example.ui.components.CitraMascote
import com.example.ui.components.CreditsDialog
import com.example.ui.components.MascotHumor
import com.example.ui.components.WhatsNewDialog
import com.example.ui.navigation.Screen
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
    private var pendingWidgetAction by mutableStateOf<String?>(null)
    private var pendingWidgetNoteId by mutableStateOf<Long?>(null)
    private var pendingExternalDocumentUri by mutableStateOf<android.net.Uri?>(null)
    private var pendingExternalDocumentMime by mutableStateOf<String?>(null)
    private var pendingOpenTimerTab by mutableStateOf(false)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkAlarmIntent(intent)
        handleWidgetIntent(intent)
        handleExternalViewIntent(intent)
        if (intent.getBooleanExtra(NotificationHelper.EXTRA_OPEN_TIMER_TAB, false)) {
            pendingOpenTimerTab = true
        }
    }

    private fun handleExternalViewIntent(intent: android.content.Intent?) {
        if (intent?.action == android.content.Intent.ACTION_VIEW) {
            val dataUri = intent.data ?: intent.clipData?.getItemAt(0)?.uri
            if (dataUri != null) {
                pendingExternalDocumentUri = dataUri
                pendingExternalDocumentMime = intent.type
            }
        }
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
        if (intent?.getBooleanExtra(NotificationHelper.EXTRA_OPEN_TIMER_TAB, false) == true) {
            pendingOpenTimerTab = true
        }
        val extraType = intent?.getStringExtra(NotificationHelper.EXTRA_TYPE) ?: NotificationHelper.TYPE_TASK
        if (intent?.getBooleanExtra(NotificationHelper.EXTRA_LOOP_ALARM, false) == true &&
            extraType != NotificationHelper.TYPE_HABIT &&
            extraType != NotificationHelper.TYPE_HABIT_MISSED
        ) {
            val id = intent.getIntExtra(NotificationHelper.EXTRA_ID, -1)
            val title = intent.getStringExtra(NotificationHelper.EXTRA_TITLE) ?: "Citra"
            val taskId = intent.getLongExtra(NotificationHelper.EXTRA_TASK_ID, -1L)
            val habitId = intent.getLongExtra(NotificationHelper.EXTRA_HABIT_ID, -1L)
            val soundUri = intent.getStringExtra(NotificationHelper.EXTRA_SOUND_URI) ?: ""
            ActiveAlarmManager.onAlarmTriggered(
                this,
                ActiveAlarmData(
                    notificationId = id,
                    type = extraType,
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
        handleExternalViewIntent(intent)

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

            val activeAlarm by ActiveAlarmManager.activeAlarm.collectAsState()

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
                    val isPomodoroAlarm = currentAlarm.type == NotificationHelper.TYPE_POMODORO
                    AlarmScreen(
                        alarmData = currentAlarm,
                        secondaryActionLabel = if (isPomodoroAlarm) "Abrir Pausa" else "Adiar 10 minutos",
                        secondaryActionIcon = if (isPomodoroAlarm) Icons.Default.PlayArrow else Icons.Default.Snooze,
                        onDismiss = {
                            val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                            if (currentAlarm.notificationId > 0) nm.cancel(currentAlarm.notificationId)
                            com.example.service.AlarmRingService.stopAlarm(this@MainActivity)
                            ActiveAlarmManager.dismissAlarm(this@MainActivity)
                        },
                        onSnooze = {
                            val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                            if (currentAlarm.notificationId > 0) nm.cancel(currentAlarm.notificationId)
                            if (isPomodoroAlarm) {
                                pendingOpenTimerTab = true
                            } else {
                                val snoozeTime = System.currentTimeMillis() + 10 * 60 * 1000L
                                NotificationHelper.scheduleTaskSnooze(this@MainActivity, currentAlarm.taskId, currentAlarm.title, snoozeTime, currentAlarm.soundUri)
                            }
                            com.example.service.AlarmRingService.stopAlarm(this@MainActivity)
                            ActiveAlarmManager.dismissAlarm(this@MainActivity)
                        }
                    )
                    return@CitraTheme
                }

                val isStartupScanDone by viewModel.isStartupScanDone.collectAsState()
                val hasCompletedOnboarding by viewModel.hasCompletedOnboarding.collectAsState()

                LaunchedEffect(Unit) {
                    viewModel.performStartupFolderScan()
                    viewModel.runDueFinanceLinksOnStartup()
                }

                // Organic Loading Screen: Wait until local resources are resolved
                if (!isStartupScanDone) {
                    CitraLoadingScreen(loadingText = "Carregando recursos do Citra...")
                    return@CitraTheme
                }

                // First-launch Citra Mascot intro animation & adaptive personalization quiz
                if (!hasCompletedOnboarding) {
                    val savedProfileAnswers by viewModel.onboardingProfileAnswers.collectAsState()
                    com.example.ui.screens.welcome.WelcomeIntroScreen(
                        initialAnswers = savedProfileAnswers,
                        onPreviewThemeMode = { viewModel.setThemeMode(it) },
                        onPreviewThemeAccent = { viewModel.setThemeAccent(it) },
                        onFinishOnboarding = { displayName, photoPath, profileAnswers ->
                            viewModel.completeOnboarding(displayName, photoPath, profileAnswers)
                        }
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

                LaunchedEffect(pendingOpenTimerTab, screens) {
                    if (pendingOpenTimerTab) {
                        val timerIdx = screens.indexOf(Screen.Timer).takeIf { it >= 0 } ?: 1
                        pagerState.scrollToPage(timerIdx)
                        pendingOpenTimerTab = false
                    }
                }

                // Handle Widget Actions
                LaunchedEffect(pendingWidgetAction, pendingWidgetNoteId, screens) {
                    val action = pendingWidgetAction ?: return@LaunchedEffect
                    val notesPageIndex = screens.indexOf(Screen.Notes).coerceAtLeast(0)
                    when (action) {
                        com.example.widget.CitraWidgetHelper.ACTION_NAVIGATE_TIMER -> {
                            pagerState.scrollToPage(1)
                        }
                        com.example.widget.CitraWidgetHelper.ACTION_NAVIGATE_HABITS -> {
                            viewModel.setOrganizationSubTab(0)
                            pagerState.scrollToPage(2)
                        }
                        com.example.widget.CitraWidgetHelper.ACTION_NAVIGATE_NEW_NOTE -> {
                            pagerState.scrollToPage(notesPageIndex)
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
                                    pagerState.scrollToPage(notesPageIndex)
                                }
                            } else {
                                pagerState.scrollToPage(notesPageIndex)
                            }
                        }
                    }
                    pendingWidgetAction = null
                    pendingWidgetNoteId = null
                }

                // Handle OS "Open with / Default app" intents for PDF and Word (.docx)
                LaunchedEffect(pendingExternalDocumentUri, screens) {
                    val externalUri = pendingExternalDocumentUri ?: return@LaunchedEffect
                    val notesPageIndex = screens.indexOf(Screen.Notes).coerceAtLeast(0)
                    pagerState.scrollToPage(notesPageIndex)
                    viewModel.openOrRegisterExternalDocumentFromIntent(
                        uri = externalUri,
                        mimeType = pendingExternalDocumentMime,
                        context = this@MainActivity
                    )
                    pendingExternalDocumentUri = null
                    pendingExternalDocumentMime = null
                }

                var showMenu by remember { mutableStateOf(false) }
                var showAchievementsDialog by remember { mutableStateOf(false) }
                var showBackupDialog by remember { mutableStateOf(false) }
                var showCreditsDialog by remember { mutableStateOf(false) }
                var showSettingsDialog by remember { mutableStateOf(false) }
                var showAddCategoryDialog by remember { mutableStateOf(false) }
                var showQuickAddTaskDialog by remember { mutableStateOf(false) }

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


                // Handle back press to return to Home or Timer (instant transition without intermediate scroll lag)
                BackHandler(enabled = pagerState.currentPage != 0) {
                    coroutineScope.launch {
                        pagerState.scrollToPage(0)
                    }
                }

                val organizationSubTab by viewModel.organizationSubTab.collectAsState()
                val timerAllowSwipe by viewModel.timerAllowSwipe.collectAsState()

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
                val notesSearchQuery by viewModel.notesSearchQuery.collectAsState()
                val trashNotes by viewModel.trashNotes.collectAsState()

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = if (isNoteEditorOpen) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
                    topBar = {
                        val isTimerImmersive = (pomodoroState.isRunning || pomodoroState.isPaused) &&
                                currentScreen == Screen.Timer
                        val isTimerActive = pomodoroState.isRunning || pomodoroState.isPaused
                        if (!isNoteEditorOpen && !isTimerImmersive) {
                            if (currentScreen == Screen.Notes) {
                                // Flush-left Notes search bar on the exact same top strip as the 3-dots menu
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .statusBarsPadding()
                                            .height(64.dp)
                                            .padding(start = 10.dp, end = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(44.dp)
                                                .tutorialTarget("notes_search_input")
                                                .testTag("notes_search_input")
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(horizontal = 14.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Search,
                                                    contentDescription = "Pesquisar",
                                                    tint = MaterialTheme.colorScheme.outline,
                                                    modifier = Modifier.size(19.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Box(
                                                    modifier = Modifier.weight(1f),
                                                    contentAlignment = Alignment.CenterStart
                                                ) {
                                                    if (notesSearchQuery.isEmpty()) {
                                                        Text(
                                                            text = "Pesquisar notas, PDFs ou Words...",
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.outline,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    BasicTextField(
                                                        value = notesSearchQuery,
                                                        onValueChange = { viewModel.setNotesSearchQuery(it) },
                                                        singleLine = true,
                                                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        ),
                                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                }
                                                if (notesSearchQuery.isNotEmpty()) {
                                                    IconButton(
                                                        onClick = { viewModel.setNotesSearchQuery("") },
                                                        modifier = Modifier.size(28.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Clear,
                                                            contentDescription = "Limpar pesquisa",
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        if (isTimerActive) {
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
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(text = "🍊", fontSize = 12.sp)
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text(
                                                        text = formatted,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = if (pomodoroState.isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }

                                        CitraTopOverflowMenuBox(
                                            showMenu = showMenu,
                                            onOpenMenu = { showMenu = true },
                                            onDismissMenu = { showMenu = false },
                                            isNotesScreen = true,
                                            trashCount = trashNotes.size,
                                            onOpenSettings = { showSettingsDialog = true },
                                            onOpenAddCategory = { showAddCategoryDialog = true },
                                            onOpenAchievements = { showAchievementsDialog = true },
                                            onOpenBackup = { showBackupDialog = true },
                                            onOpenCredits = { showCreditsDialog = true },
                                            onOpenNotesTrash = { viewModel.triggerOpenNotesTrash() },
                                            onOpenArchivedNotes = { viewModel.triggerOpenArchivedNotes() }
                                        )
                                    }
                                }
                            } else {
                                TopAppBar(
                                    navigationIcon = {
                                        if (currentScreen == Screen.Home) {
                                            Spacer(modifier = Modifier.width(48.dp))
                                        }
                                    },
                                    title = {
                                        if (currentScreen == Screen.Home) {
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
                                                if (isTimerActive && currentScreen != Screen.Timer) {
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
                                        // Settings button on "Eu" screen
                                        if (currentScreen == Screen.Profile) {
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

                                        CitraTopOverflowMenuBox(
                                            showMenu = showMenu,
                                            onOpenMenu = { showMenu = true },
                                            onDismissMenu = { showMenu = false },
                                            isNotesScreen = false,
                                            trashCount = trashNotes.size,
                                            onOpenSettings = { showSettingsDialog = true },
                                            onOpenAddCategory = { showAddCategoryDialog = true },
                                            onOpenAchievements = { showAchievementsDialog = true },
                                            onOpenBackup = { showBackupDialog = true },
                                            onOpenCredits = { showCreditsDialog = true },
                                            onOpenNotesTrash = { viewModel.triggerOpenNotesTrash() },
                                            onOpenArchivedNotes = { viewModel.triggerOpenArchivedNotes() }
                                        )
                                    },
                                    colors = TopAppBarDefaults.topAppBarColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    )
                                )
                            }
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
                    val isTimerLockedFromSwipe = !timerAllowSwipe &&
                        screens.getOrNull(pagerState.currentPage) == Screen.Timer
                    HorizontalPager(
                        state = pagerState,
                        userScrollEnabled = !isNoteEditorOpen && !isTimerLockedFromSwipe,
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
                                onOpenSettings = { showSettingsDialog = true },
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
        com.example.notifications.AppForegroundTracker.onActivityStarted()
        AmbientBreakMusicManager.onAppForegrounded(this)
    }

    override fun onResume() {
        super.onResume()
        AmbientBreakMusicManager.onAppForegrounded(this)
        ActiveAlarmManager.checkAndRestore(this)
        viewModel.preferencesManager.reloadReactiveTimerState()
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
        com.example.notifications.AppForegroundTracker.onActivityStopped()
        // Pause note audio and background ambient audio on leaving app
        com.example.util.NoteAudioPlaybackManager.pause()
        AmbientBreakMusicManager.onAppBackgrounded()

        viewModel.triggerAutoBackup()

        // Ensure TimerForegroundService owns ongoing notification seamlessly while app is in background
        val state = viewModel.pomodoroState.value
        if (state.isRunning || state.isPaused) {
            com.example.service.TimerForegroundService.syncWithSnapshot(this)
        }
    }
}

@Composable
private fun CitraTopOverflowMenuBox(
    showMenu: Boolean,
    onOpenMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    isNotesScreen: Boolean,
    trashCount: Int,
    onOpenSettings: () -> Unit,
    onOpenAddCategory: () -> Unit,
    onOpenAchievements: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenCredits: () -> Unit,
    onOpenNotesTrash: () -> Unit,
    onOpenArchivedNotes: () -> Unit
) {
    Box {
        IconButton(
            onClick = onOpenMenu,
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
            onDismissRequest = onDismissMenu,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            DropdownMenuItem(
                text = { Text("Configurações") },
                leadingIcon = {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                modifier = Modifier.tutorialTarget("top_menu_settings"),
                onClick = {
                    onDismissMenu()
                    onOpenSettings()
                }
            )

            DropdownMenuItem(
                text = { Text("Nova Categoria") },
                leadingIcon = {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                onClick = {
                    onDismissMenu()
                    onOpenAddCategory()
                }
            )

            DropdownMenuItem(
                text = { Text("Conquistas") },
                leadingIcon = {
                    Icon(Icons.Default.Stars, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                modifier = Modifier.tutorialTarget("top_menu_achievements"),
                onClick = {
                    onDismissMenu()
                    onOpenAchievements()
                }
            )

            DropdownMenuItem(
                text = { Text("Backup & Restauração") },
                leadingIcon = {
                    Icon(Icons.Default.Backup, contentDescription = null)
                },
                modifier = Modifier.tutorialTarget("top_menu_backup"),
                onClick = {
                    onDismissMenu()
                    onOpenBackup()
                }
            )

            HorizontalDivider()

            DropdownMenuItem(
                text = { Text("Sobre") },
                leadingIcon = {
                    Icon(Icons.Default.Info, contentDescription = null)
                },
                onClick = {
                    onDismissMenu()
                    onOpenCredits()
                }
            )

            if (isNotesScreen) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Trash icon button with ONLY a number badge when waiting notes > 0
                    Box(
                        modifier = Modifier.size(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            onClick = {
                                onDismissMenu()
                                onOpenNotesTrash()
                            },
                            modifier = Modifier.testTag("menu_notes_trash_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Lixeira",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        if (trashCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-4).dp, y = 4.dp)
                                    .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                                    .background(MaterialTheme.colorScheme.error, CircleShape)
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$trashCount",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // Archived icon button with ZERO signals/badges
                    IconButton(
                        onClick = {
                            onDismissMenu()
                            onOpenArchivedNotes()
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("menu_notes_archived_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Archive,
                            contentDescription = "Arquivadas",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
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
