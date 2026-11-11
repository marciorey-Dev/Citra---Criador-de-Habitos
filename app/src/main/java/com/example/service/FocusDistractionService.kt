package com.example.service

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.PreferencesManager
import com.example.ui.components.CitraMascotView
import com.example.util.FocusPermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FocusDistractionService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var mascotView: CitraMascotView? = null
    private var isOverlayShowing = false

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var monitorJob: Job? = null

    private lateinit var preferencesManager: PreferencesManager
    private var allowedPackages = setOf<String>()

    private var lastKnownForegroundPackage: String? = null
    private val cachedLauncherPackages = mutableSetOf<String>()

    private val knownLauncherSubstrings = listOf(
        "launcher",
        "nexuslauncher",
        "trebuchet",
        "quickstep",
        "home"
    )

    private val knownLauncherPackages = setOf(
        "com.google.android.apps.nexuslauncher",
        "com.android.launcher3",
        "com.android.launcher",
        "com.sec.android.app.launcher",
        "com.sec.android.app.twlauncher",
        "com.miui.home",
        "com.mi.android.globallauncher",
        "com.huawei.android.launcher",
        "com.oppo.launcher",
        "com.coloros.launcher",
        "com.vivo.launcher",
        "com.bbk.launcher2",
        "com.oneplus.launcher",
        "com.motorola.launcher3",
        "com.motorola.homescreen",
        "com.transsion.hilauncher",
        "com.transsion.xoslauncher",
        "com.teslacoilsw.launcher",
        "com.microsoft.launcher",
        "app.lawnchair",
        "app.lawnchair.playstore",
        "ch.deletescape.lawnchair.plah",
        "com.smartlauncher",
        "ginlemon.flowerfree",
        "com.actionlauncher.playstore",
        "com.niagaralauncher",
        "bitpit.launcher",
        "com.lge.launcher2",
        "com.lge.launcher3",
        "com.sonyericsson.home",
        "com.asus.launcher"
    )

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        preferencesManager = PreferencesManager(this)
        allowedPackages = preferencesManager.focusAllowedPackages.value
        lastKnownForegroundPackage = packageName
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_STOP -> {
                stopForegroundService()
                return START_NOT_STICKY
            }
            ACTION_TEST_OVERLAY -> {
                showOverlay("Modo de Teste: Prévia da Tela de Foco 🍊")
                return START_NOT_STICKY
            }
            ACTION_SHOW_TASK_OVERLAY -> {
                val taskTitle = intent?.getStringExtra(EXTRA_TASK_TITLE) ?: "Tarefa Urgente"
                showOverlay(
                    titleMessage = "Tarefa Urgente! ⚠️",
                    subtitleMessage = "A tarefa \"$taskTitle\" atingiu o prazo e é perturbadora/urgente, não pode esperar!\nAbra o Citra agora para conferir ou concluir."
                )
                return START_NOT_STICKY
            }
            else -> {
                startForegroundNotification()
                lastKnownForegroundPackage = packageName
                startMonitoring()
            }
        }

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Modo Sem Perturbações (Citra)",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Modo sem perturbações"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun startForegroundNotification() {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            this.action = ACTION_OPEN_FROM_FOCUS_OVERLAY
            this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Modo Sem Perturbações")
            .setContentText("Citra")
            .setSmallIcon(R.drawable.app_icon_fg)
            .setContentIntent(pendingIntent)
            .setOngoing(false)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        startForeground(NOTIFICATION_ID, notification)
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun isScreenOffOrLocked(): Boolean {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (powerManager?.isInteractive == false) return true

        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (keyguardManager?.isKeyguardLocked == true) return true

        return false
    }

    private fun isLauncherPackage(pkg: String): Boolean {
        if (pkg.isBlank()) return false
        val lower = pkg.lowercase()

        // 1. Direct match in known launcher packages
        if (knownLauncherPackages.contains(lower) || cachedLauncherPackages.contains(lower)) {
            return true
        }

        // 2. Common substring match (covers virtually all OEM and third-party launchers)
        for (sub in knownLauncherSubstrings) {
            if (lower.contains(sub)) {
                cachedLauncherPackages.add(lower)
                return true
            }
        }

        // 3. Dynamic query via PackageManager for CATEGORY_HOME
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val activities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(
                    homeIntent,
                    PackageManager.ResolveInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(homeIntent, 0)
            }
            for (ri in activities) {
                val p = ri.activityInfo?.packageName?.lowercase()
                if (p != null) {
                    cachedLauncherPackages.add(p)
                    if (p == lower) return true
                }
            }

            val resolved = packageManager.resolveActivity(homeIntent, 0)
            val resolvedPkg = resolved?.activityInfo?.packageName?.lowercase()
            if (resolvedPkg != null) {
                cachedLauncherPackages.add(resolvedPkg)
                if (resolvedPkg == lower) return true
            }
        } catch (_: Exception) {}

        return false
    }

    private fun isSystemOrEssentialPackage(pkg: String): Boolean {
        if (pkg.isBlank()) return true
        val lower = pkg.lowercase()

        // Android core framework and system UI
        if (lower == "android" ||
            lower == "com.android.systemui" ||
            lower.startsWith("com.android.internal")
        ) {
            return true
        }

        // Keyboards / IMEs
        if (lower.contains("inputmethod") ||
            lower.contains("keyboard") ||
            lower.contains("honeyboard") ||
            lower.contains("swiftkey")
        ) {
            return true
        }

        // Phone calls / In-call UI (allow user to answer emergency calls)
        if (lower == "com.android.phone" ||
            lower == "com.google.android.dialer" ||
            lower == "com.android.dialer" ||
            lower.contains("incallui")
        ) {
            return true
        }

        // System Settings, Permission Controller, Package Installer, Media Picker
        if (lower == "com.android.settings" ||
            lower == "com.google.android.settings" ||
            lower.contains("permissioncontroller") ||
            lower.contains("packageinstaller") ||
            lower.contains("documentsui")
        ) {
            return true
        }

        return false
    }

    private fun startMonitoring() {
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            val myPackage = packageName

            while (isActive) {
                // If screen is off or keyguard is locked, keep overlay hidden
                if (isScreenOffOrLocked()) {
                    hideOverlay()
                    delay(800L)
                    continue
                }

                allowedPackages = preferencesManager.focusAllowedPackages.value
                val isDistractionFreeActive = preferencesManager.focusDistractionFreeEnabled.value

                if (!isDistractionFreeActive) {
                    hideOverlay()
                    stopSelf()
                    break
                }

                if (!FocusPermissionHelper.hasOverlayPermission(this@FocusDistractionService) ||
                    !FocusPermissionHelper.hasUsageStatsPermission(this@FocusDistractionService)
                ) {
                    hideOverlay()
                    delay(1500L)
                    continue
                }

                val currentApp = getForegroundPackage(usageStatsManager)

                if (currentApp != null) {
                    val isCitra = currentApp == myPackage
                    val isLauncher = isLauncherPackage(currentApp)
                    val isSystem = isSystemOrEssentialPackage(currentApp)
                    val isWhitelisted = allowedPackages.contains(currentApp)

                    if (isCitra || isLauncher || isSystem || isWhitelisted) {
                        // User is in Citra, on the Home screen / Launcher, in system UI, or in an allowed app
                        hideOverlay()
                    } else {
                        // User actually launched another unallowed app!
                        showOverlay("Hora de Focar! 🍊")
                    }
                }

                delay(500L)
            }
        }
    }

    private fun getForegroundPackage(usageStatsManager: UsageStatsManager?): String? {
        if (usageStatsManager == null) return lastKnownForegroundPackage
        val now = System.currentTimeMillis()
        val events = try {
            usageStatsManager.queryEvents(now - 5000L, now)
        } catch (_: Exception) {
            null
        } ?: return lastKnownForegroundPackage

        var latestPkg: String? = null
        var latestTimestamp = 0L
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                if (event.timeStamp >= latestTimestamp) {
                    latestTimestamp = event.timeStamp
                    latestPkg = event.packageName
                }
            }
        }

        if (latestPkg != null) {
            lastKnownForegroundPackage = latestPkg
        }

        return lastKnownForegroundPackage
    }

    private fun showOverlay(
        titleMessage: String,
        subtitleMessage: String = "O Modo Sem Perturbações do Citra está ativo!\nDeixe as distrações para depois e mantenha seu ritmo até o fim do timer."
    ) {
        if (isOverlayShowing) return
        if (!FocusPermissionHelper.hasOverlayPermission(this)) return

        serviceScope.launch(Dispatchers.Main) {
            try {
                if (overlayView == null) {
                    overlayView = createOverlayView(titleMessage, subtitleMessage)
                }

                val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    layoutType,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.CENTER
                }

                windowManager?.addView(overlayView, params)
                isOverlayShowing = true
            } catch (e: Exception) {
                android.util.Log.e("FocusOverlay", "Failed to show overlay", e)
            }
        }
    }

    private fun hideOverlay() {
        if (!isOverlayShowing || overlayView == null) return

        serviceScope.launch(Dispatchers.Main) {
            try {
                mascotView?.stopAnimations()
                mascotView = null
                overlayView?.let { windowManager?.removeView(it) }
                overlayView = null
                isOverlayShowing = false
            } catch (e: Exception) {
                android.util.Log.e("FocusOverlay", "Failed to hide overlay", e)
            }
        }
    }

    private fun createOverlayView(titleMessage: String, subtitleMessage: String): View {
        val rootLayout = FrameLayout(this).apply {
            // Full-screen vibrant Citra Orange matching the login intro screen
            setBackgroundColor(Color.parseColor("#FFFF8B1F"))
        }

        val contentContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dpToPx(28), dpToPx(48), dpToPx(28), dpToPx(48))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        }

        // Animated Citra Mascot View with double blinking eyes & subtle breathing pulse
        val mascot = CitraMascotView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(200), dpToPx(200)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dpToPx(20)
            }
        }
        this@FocusDistractionService.mascotView = mascot
        contentContainer.addView(mascot)

        // Title
        val titleView = TextView(this).apply {
            text = titleMessage
            setTextColor(Color.WHITE)
            textSize = 25f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(8)
            }
        }
        contentContainer.addView(titleView)

        // Subtitle message
        val descView = TextView(this).apply {
            text = subtitleMessage
            setTextColor(Color.parseColor("#F5FFFFFF"))
            textSize = 14.5f
            gravity = Gravity.CENTER
            setLineSpacing(dpToPx(3).toFloat(), 1f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(32)
            }
        }
        contentContainer.addView(descView)

        // Primary Button: Voltar ao Citra
        val returnButton = Button(this).apply {
            text = "VOLTAR PARA O CITRA 🍊"
            setTextColor(Color.parseColor("#1F1005"))
            textSize = 14.5f
            setTypeface(null, Typeface.BOLD)
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dpToPx(18).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(54)
            ).apply {
                bottomMargin = dpToPx(14)
            }
            setOnClickListener {
                hideOverlay()
                val returnIntent = Intent(context, MainActivity::class.java).apply {
                    this.action = ACTION_OPEN_FROM_FOCUS_OVERLAY
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                context.startActivity(returnIntent)
            }
        }
        contentContainer.addView(returnButton)

        // Secondary Text Button: Fechar / Pausar
        val dismissBtn = TextView(this).apply {
            text = "Pausar Foco Temporariamente"
            setTextColor(Color.parseColor("#E6FFFFFF"))
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dpToPx(16), dpToPx(10), dpToPx(16), dpToPx(10))
            setOnClickListener {
                hideOverlay()
                val pauseIntent = Intent(context, MainActivity::class.java).apply {
                    this.action = ACTION_PAUSE_FROM_FOCUS_OVERLAY
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                context.startActivity(pauseIntent)
            }
        }
        contentContainer.addView(dismissBtn)

        rootLayout.addView(contentContainer)
        return rootLayout
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    private fun stopForegroundService() {
        monitorJob?.cancel()
        hideOverlay()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        hideOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "citra_focus_service_channel"
        const val NOTIFICATION_ID = 4040
        const val ACTION_START = "com.example.citra.action.START_FOCUS_OVERLAY"
        const val ACTION_STOP = "com.example.citra.action.STOP_FOCUS_OVERLAY"
        const val ACTION_TEST_OVERLAY = "com.example.citra.action.TEST_FOCUS_OVERLAY"
        const val ACTION_SHOW_TASK_OVERLAY = "com.example.citra.action.SHOW_TASK_OVERLAY"
        const val EXTRA_TASK_TITLE = "extra_task_title"
        const val ACTION_OPEN_FROM_FOCUS_OVERLAY = "com.example.citra.action.OPEN_FROM_FOCUS_OVERLAY"
        const val ACTION_PAUSE_FROM_FOCUS_OVERLAY = "com.example.citra.action.PAUSE_FROM_FOCUS_OVERLAY"

        fun start(context: Context) {
            val intent = Intent(context, FocusDistractionService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FocusDistractionService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun testOverlay(context: Context) {
            val intent = Intent(context, FocusDistractionService::class.java).apply {
                action = ACTION_TEST_OVERLAY
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun showDisruptiveTaskOverlay(context: Context, taskTitle: String) {
            val intent = Intent(context, FocusDistractionService::class.java).apply {
                action = ACTION_SHOW_TASK_OVERLAY
                putExtra(EXTRA_TASK_TITLE, taskTitle)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
