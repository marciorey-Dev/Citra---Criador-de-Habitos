package com.example.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.PreferencesManager
import com.example.data.local.SavedTimerSnapshot
import com.example.notifications.NotificationHelper
import com.example.notifications.TimerCompletionCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Dedicated Foreground Service for live Pomodoro/Stopwatch notification using native
 * Android Chronometer (notification ID = TIMER_ONGOING_NOTIFICATION_ID = 99998)
 * with Pausar / Retomar / Interromper action buttons.
 */
class TimerForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var countdownJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        NotificationHelper.initNotificationChannels(this)
        val prefs = PreferencesManager(this)
        val action = intent?.action ?: ACTION_SYNC

        when (action) {
            ACTION_STOP_SERVICE -> {
                countdownJob?.cancel()
                val placeholder = buildPlaceholderNotification()
                safeStartForeground(placeholder)
                stopSelfCleanly()
                return START_NOT_STICKY
            }

            ACTION_PAUSE -> {
                val snap = prefs.loadTimerSnapshot()
                if (snap == null) {
                    safeStartForeground(buildPlaceholderNotification())
                    stopSelfCleanly()
                    return START_NOT_STICKY
                }
                val now = System.currentTimeMillis()
                val updated = if (snap.isStopwatchMode) {
                    val elapsed = if (snap.isRunning && snap.stopwatchStartTimeMillis > 0L) {
                        ((now - snap.stopwatchStartTimeMillis) / 1000L).toInt().coerceAtLeast(0)
                    } else {
                        snap.stopwatchAccumulatedSeconds
                    }
                    snap.copy(
                        isRunning = false,
                        isPaused = true,
                        stopwatchAccumulatedSeconds = elapsed,
                        lastTimestamp = now
                    )
                } else {
                    val rem = if (snap.isRunning && snap.targetEndTimeMillis > 0L) {
                        ((snap.targetEndTimeMillis - now + 999L) / 1000L).toInt().coerceAtLeast(0)
                    } else {
                        snap.remainingSeconds
                    }
                    snap.copy(
                        isRunning = false,
                        isPaused = true,
                        remainingSeconds = rem,
                        targetEndTimeMillis = 0L,
                        lastTimestamp = now
                    )
                }
                countdownJob?.cancel()
                NotificationHelper.cancelTimerEndAlarm(this)
                try {
                    FocusDistractionService.stop(this)
                } catch (_: Exception) {}
                prefs.saveTimerSnapshot(updated)
                safeStartForeground(buildNotificationForSnapshot(updated))
                return START_NOT_STICKY
            }

            ACTION_RESUME -> {
                val snap = prefs.loadTimerSnapshot()
                if (snap == null) {
                    safeStartForeground(buildPlaceholderNotification())
                    stopSelfCleanly()
                    return START_NOT_STICKY
                }
                val now = System.currentTimeMillis()
                val updated = if (snap.isStopwatchMode) {
                    val startMs = now - (snap.stopwatchAccumulatedSeconds * 1000L)
                    snap.copy(
                        isRunning = true,
                        isPaused = false,
                        stopwatchStartTimeMillis = startMs,
                        lastTimestamp = now
                    )
                } else {
                    val rem = snap.remainingSeconds.coerceAtLeast(1)
                    val targetMs = now + (rem * 1000L)
                    snap.copy(
                        isRunning = true,
                        isPaused = false,
                        targetEndTimeMillis = targetMs,
                        lastTimestamp = now,
                        isCompletedProcessed = false
                    )
                }
                prefs.saveTimerSnapshot(updated)
                if (!updated.isStopwatchMode) {
                    val phaseLabel = if (updated.phaseName == "BREAK") "Pausa" else "Foco"
                    NotificationHelper.scheduleTimerEndAlarm(this, updated.targetEndTimeMillis, phaseLabel)
                    if (prefs.focusDistractionFreeEnabled.value && updated.phaseName == "FOCUS") {
                        try {
                            FocusDistractionService.start(this)
                        } catch (_: Exception) {}
                    }
                }
                safeStartForeground(buildNotificationForSnapshot(updated))
                startCountdownWatcherIfNeeded(updated)
                return START_STICKY
            }

            ACTION_STOP_TIMER -> {
                countdownJob?.cancel()
                NotificationHelper.cancelTimerEndAlarm(this)
                try {
                    FocusDistractionService.stop(this)
                } catch (_: Exception) {}
                prefs.clearTimerSnapshot()
                safeStartForeground(buildPlaceholderNotification())
                stopSelfCleanly()
                return START_NOT_STICKY
            }

            else -> {
                val snap = prefs.loadTimerSnapshot()
                if (snap == null || (!snap.isRunning && !snap.isPaused)) {
                    safeStartForeground(buildPlaceholderNotification())
                    stopSelfCleanly()
                    return START_NOT_STICKY
                }
                safeStartForeground(buildNotificationForSnapshot(snap))
                startCountdownWatcherIfNeeded(snap)
                return if (snap.isRunning) START_STICKY else START_NOT_STICKY
            }
        }
    }

    private fun startCountdownWatcherIfNeeded(snapshot: SavedTimerSnapshot) {
        countdownJob?.cancel()
        if (!snapshot.isRunning || snapshot.isPaused || snapshot.isStopwatchMode || snapshot.targetEndTimeMillis <= 0L) {
            return
        }
        countdownJob = serviceScope.launch {
            val targetMs = snapshot.targetEndTimeMillis
            while (true) {
                val remainingMs = targetMs - System.currentTimeMillis()
                if (remainingMs <= 0L) {
                    TimerCompletionCoordinator.completeExpiredTimerIfNeeded(
                        context = this@TimerForegroundService,
                        triggerSource = "TimerForegroundService",
                        forceNow = true
                    )
                    break
                }
                delay(minOf(remainingMs, 10_000L))
            }
        }
    }

    private fun safeStartForeground(notification: Notification) {
        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(
                this,
                NotificationHelper.TIMER_ONGOING_NOTIFICATION_ID,
                notification,
                fgsType
            )
        } catch (_: Exception) {
            startForeground(NotificationHelper.TIMER_ONGOING_NOTIFICATION_ID, notification)
        }
    }

    private fun stopSelfCleanly() {
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun buildPlaceholderNotification(): Notification {
        return NotificationCompat.Builder(this, NotificationHelper.CHANNEL_TIMER_ONGOING)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Arancia Citra")
            .setContentText("Sincronizando temporizador...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()
    }

    private fun buildNotificationForSnapshot(snapshot: SavedTimerSnapshot): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(NotificationHelper.EXTRA_OPEN_TIMER_TAB, true)
        }
        val openPending = PendingIntent.getActivity(
            this,
            NotificationHelper.TIMER_ONGOING_NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val phaseLabel = when {
            snapshot.isStopwatchMode -> "Cronômetro"
            snapshot.phaseName == "BREAK" -> "Pausa (${snapshot.currentCycle}/${snapshot.totalCycles})"
            else -> "Foco (${snapshot.currentCycle}/${snapshot.totalCycles})"
        }

        val now = System.currentTimeMillis()
        val builder = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_TIMER_ONGOING)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(snapshot.isRunning && !snapshot.isPaused)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openPending)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (snapshot.isPaused) {
            val secs = if (snapshot.isStopwatchMode) snapshot.stopwatchAccumulatedSeconds else snapshot.remainingSeconds
            val formatted = String.format("%02d:%02d", secs / 60, secs % 60)
            builder.setContentTitle("Arancia: $phaseLabel • Pausado ($formatted)")
            builder.setContentText("Toque para retornar ao timer")
            builder.setUsesChronometer(false)

            val resumePending = servicePendingIntent(ACTION_RESUME, 99801)
            val stopPending = servicePendingIntent(ACTION_STOP_TIMER, 99802)
            builder.addAction(android.R.drawable.ic_media_play, "Retomar", resumePending)
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Interromper", stopPending)
        } else if (snapshot.isStopwatchMode) {
            val baseTime = snapshot.stopwatchStartTimeMillis.takeIf { it > 0L }
                ?: (now - snapshot.stopwatchAccumulatedSeconds * 1000L)
            builder.setContentTitle("Arancia: $phaseLabel")
            builder.setContentText("Contagem progressiva em andamento")
            builder.setShowWhen(true)
            builder.setWhen(baseTime)
            builder.setUsesChronometer(true)
            builder.setChronometerCountDown(false)

            val pausePending = servicePendingIntent(ACTION_PAUSE, 99803)
            val stopPending = servicePendingIntent(ACTION_STOP_TIMER, 99804)
            builder.addAction(android.R.drawable.ic_media_pause, "Pausar", pausePending)
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Interromper", stopPending)
        } else {
            val targetMs = snapshot.targetEndTimeMillis.takeIf { it > 0L }
                ?: (now + snapshot.remainingSeconds * 1000L)
            builder.setContentTitle("Arancia: $phaseLabel")
            val contextDesc = when {
                !snapshot.linkedTaskTitle.isNullOrBlank() -> "Tarefa: ${snapshot.linkedTaskTitle}"
                !snapshot.linkedHabitTitle.isNullOrBlank() -> "Hábito: ${snapshot.linkedHabitTitle}"
                else -> "Sessão em andamento (${snapshot.selectedCategory})"
            }
            builder.setContentText(contextDesc)
            builder.setShowWhen(true)
            builder.setWhen(targetMs)
            builder.setUsesChronometer(true)
            builder.setChronometerCountDown(true)

            val pausePending = servicePendingIntent(ACTION_PAUSE, 99805)
            val stopPending = servicePendingIntent(ACTION_STOP_TIMER, 99806)
            builder.addAction(android.R.drawable.ic_media_pause, "Pausar", pausePending)
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Interromper", stopPending)
        }

        return builder.build()
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, TimerForegroundService::class.java).apply {
            this.action = action
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getService(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    override fun onDestroy() {
        countdownJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_SYNC = "com.example.citra.timer.SYNC"
        const val ACTION_PAUSE = "com.example.citra.timer.PAUSE"
        const val ACTION_RESUME = "com.example.citra.timer.RESUME"
        const val ACTION_STOP_TIMER = "com.example.citra.timer.STOP_TIMER"
        const val ACTION_STOP_SERVICE = "com.example.citra.timer.STOP_SERVICE"

        fun syncWithSnapshot(context: Context) {
            val appCtx = context.applicationContext ?: context
            val snap = PreferencesManager(appCtx).loadTimerSnapshot()
            if (snap == null || (!snap.isRunning && !snap.isPaused)) {
                stop(appCtx)
                return
            }
            val intent = Intent(appCtx, TimerForegroundService::class.java).apply {
                action = ACTION_SYNC
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appCtx.startForegroundService(intent)
                } else {
                    appCtx.startService(intent)
                }
            } catch (_: Exception) {}
        }

        fun stop(context: Context) {
            val appCtx = context.applicationContext ?: context
            try {
                val nm = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                nm?.cancel(NotificationHelper.TIMER_ONGOING_NOTIFICATION_ID)
            } catch (_: Exception) {}
            try {
                appCtx.stopService(Intent(appCtx, TimerForegroundService::class.java))
            } catch (_: Exception) {}
        }
    }
}
