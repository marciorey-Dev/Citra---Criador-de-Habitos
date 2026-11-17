package com.example.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.example.notifications.ActiveAlarmData
import com.example.notifications.ActiveAlarmManager
import com.example.notifications.NotificationHelper
import com.example.ui.screens.alarm.AlarmActivity
import com.example.util.SoundEffectsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Dedicated Foreground Service (`mediaPlayback`) that holds WakeLock + AudioFocus
 * while ringing an alarm (Tasks with loopAlarm, Focus/Break timer end on lockscreen,
 * or Habits with optional loop audio without full-screen hijack).
 */
class AlarmRingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var autoSilenceJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null

    private var currentNotificationId: Int = NotificationHelper.TIMER_ALERT_NOTIFICATION_ID
    private var currentType: String = NotificationHelper.TYPE_TASK
    private var currentTitle: String = "Citra"
    private var currentTaskId: Long = -1L
    private var currentHabitId: Long = -1L
    private var currentSoundUri: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        NotificationHelper.initNotificationChannels(this)
        val action = intent?.action ?: ACTION_START_RING

        when (action) {
            ACTION_DISMISS -> {
                val nid = intent?.getIntExtra(NotificationHelper.EXTRA_ID, currentNotificationId)
                    ?: currentNotificationId
                safeStartForeground(nid, buildSilencePlaceholderNotification())
                stopAlarmAndCleanup(nid)
                return START_NOT_STICKY
            }

            ACTION_SNOOZE -> {
                val nid = intent?.getIntExtra(NotificationHelper.EXTRA_ID, currentNotificationId)
                    ?: currentNotificationId
                val type = intent?.getStringExtra(NotificationHelper.EXTRA_TYPE) ?: currentType
                val title = intent?.getStringExtra(NotificationHelper.EXTRA_TITLE) ?: currentTitle
                val taskId = intent?.getLongExtra(NotificationHelper.EXTRA_TASK_ID, currentTaskId) ?: currentTaskId
                val habitId = intent?.getLongExtra(NotificationHelper.EXTRA_HABIT_ID, currentHabitId) ?: currentHabitId
                val soundUri = intent?.getStringExtra(NotificationHelper.EXTRA_SOUND_URI) ?: currentSoundUri

                safeStartForeground(nid, buildSilencePlaceholderNotification())
                val triggerAt = System.currentTimeMillis() + 10L * 60L * 1000L
                if (type == NotificationHelper.TYPE_HABIT || type == NotificationHelper.TYPE_HABIT_MISSED) {
                    NotificationHelper.scheduleHabitSnooze(this, habitId, title, triggerAt, soundUri)
                } else {
                    NotificationHelper.scheduleTaskSnooze(this, taskId, title, triggerAt, soundUri)
                }
                stopAlarmAndCleanup(nid)
                return START_NOT_STICKY
            }

            ACTION_OPEN_TIMER -> {
                val nid = intent?.getIntExtra(NotificationHelper.EXTRA_ID, currentNotificationId)
                    ?: currentNotificationId
                safeStartForeground(nid, buildSilencePlaceholderNotification())
                stopAlarmAndCleanup(nid)
                val mainIntent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(NotificationHelper.EXTRA_OPEN_TIMER_TAB, true)
                }
                try {
                    startActivity(mainIntent)
                } catch (_: Exception) {}
                return START_NOT_STICKY
            }

            else -> {
                val nid = intent?.getIntExtra(NotificationHelper.EXTRA_ID, NotificationHelper.TIMER_ALERT_NOTIFICATION_ID)
                    ?: NotificationHelper.TIMER_ALERT_NOTIFICATION_ID
                val type = intent?.getStringExtra(NotificationHelper.EXTRA_TYPE) ?: NotificationHelper.TYPE_TASK
                val title = intent?.getStringExtra(NotificationHelper.EXTRA_TITLE) ?: "Citra"
                val message = intent?.getStringExtra(NotificationHelper.EXTRA_MESSAGE) ?: "Hora do seu compromisso!"
                val soundUri = intent?.getStringExtra(NotificationHelper.EXTRA_SOUND_URI) ?: ""
                val taskId = intent?.getLongExtra(NotificationHelper.EXTRA_TASK_ID, -1L) ?: -1L
                val habitId = intent?.getLongExtra(NotificationHelper.EXTRA_HABIT_ID, -1L) ?: -1L
                val loopAudio = intent?.getBooleanExtra(NotificationHelper.EXTRA_LOOP_ALARM, true) ?: true
                val rawFullScreen = intent?.getBooleanExtra(NotificationHelper.EXTRA_USE_FULL_SCREEN, false) ?: false
                // Policy: Habits NEVER launch FullScreenIntent / AlarmActivity
                val useFullScreen = rawFullScreen &&
                    type != NotificationHelper.TYPE_HABIT &&
                    type != NotificationHelper.TYPE_HABIT_MISSED
                val vibrate = intent?.getBooleanExtra(EXTRA_VIBRATE, true) ?: true
                val overrideSilent = intent?.getBooleanExtra(EXTRA_OVERRIDE_SILENT, false) ?: false

                currentNotificationId = nid
                currentType = type
                currentTitle = title
                currentTaskId = taskId
                currentHabitId = habitId
                currentSoundUri = soundUri

                val notification = buildAlarmNotification(
                    notificationId = nid,
                    type = type,
                    title = title,
                    message = message,
                    soundUri = soundUri,
                    taskId = taskId,
                    habitId = habitId,
                    useFullScreen = useFullScreen
                )
                safeStartForeground(nid, notification)

                acquireWakeLock()
                requestAlarmAudioFocus()

                if (useFullScreen) {
                    ActiveAlarmManager.onAlarmTriggered(
                        context = this,
                        data = ActiveAlarmData(
                            notificationId = nid,
                            type = type,
                            title = title,
                            taskId = taskId,
                            habitId = habitId,
                            soundUri = soundUri,
                            loopAlarm = loopAudio
                        )
                    )
                }

                val isSilentOrVibrate = SoundEffectsManager.isDeviceSilentOrVibrate(this)
                val shouldPlaySound = overrideSilent || !isSilentOrVibrate
                if (shouldPlaySound) {
                    SoundEffectsManager.playAlarmAudio(
                        context = this,
                        soundIdOrUri = soundUri,
                        loop = loopAudio,
                        overrideSilent = overrideSilent || useFullScreen
                    )
                }
                if (vibrate && (overrideSilent || !SoundEffectsManager.isDeviceSilent(this))) {
                    startAlarmVibration(loopAudio)
                }

                // Safety auto-silence after 3 minutes so a forgotten alarm doesn't drain battery indefinitely
                autoSilenceJob?.cancel()
                autoSilenceJob = serviceScope.launch {
                    delay(180_000L)
                    stopAlarmAndCleanup(nid, cancelNotification = false)
                }
                return START_NOT_STICKY
            }
        }
    }

    private fun safeStartForeground(notificationId: Int, notification: Notification) {
        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(this, notificationId, notification, fgsType)
        } catch (_: Exception) {
            startForeground(notificationId, notification)
        }
    }

    private fun buildSilencePlaceholderNotification(): Notification {
        return NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ALERTS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Citra")
            .setContentText("Alarme encerrado")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()
    }

    private fun buildAlarmNotification(
        notificationId: Int,
        type: String,
        title: String,
        message: String,
        soundUri: String,
        taskId: Long,
        habitId: Long,
        useFullScreen: Boolean
    ): Notification {
        val channelId = NotificationHelper.getOrCreateSilentChannel(this)

        val contentIntent = if (useFullScreen) {
            Intent(this, AlarmActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(NotificationHelper.EXTRA_ID, notificationId)
                putExtra(NotificationHelper.EXTRA_TYPE, type)
                putExtra(NotificationHelper.EXTRA_TITLE, title)
                putExtra(NotificationHelper.EXTRA_MESSAGE, message)
                putExtra(NotificationHelper.EXTRA_SOUND_URI, soundUri)
                putExtra(NotificationHelper.EXTRA_TASK_ID, taskId)
                putExtra(NotificationHelper.EXTRA_HABIT_ID, habitId)
            }
        } else {
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                if (type == NotificationHelper.TYPE_POMODORO) {
                    putExtra(NotificationHelper.EXTRA_OPEN_TIMER_TAB, true)
                }
            }
        }

        val contentPending = PendingIntent.getActivity(
            this,
            notificationId + 1000,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(this, AlarmRingService::class.java).apply {
            action = ACTION_DISMISS
            putExtra(NotificationHelper.EXTRA_ID, notificationId)
        }
        val dismissPending = serviceActionPendingIntent(dismissIntent, notificationId + 2000)

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setOngoing(true)
            .setAutoCancel(false)
            .setSilent(true) // Audio managed cleanly by MediaPlayer with USAGE_ALARM inside this FGS
            .setContentIntent(contentPending)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Desligar", dismissPending)

        if (type == NotificationHelper.TYPE_POMODORO) {
            val openTimerIntent = Intent(this, AlarmRingService::class.java).apply {
                action = ACTION_OPEN_TIMER
                putExtra(NotificationHelper.EXTRA_ID, notificationId)
            }
            val openTimerPending = serviceActionPendingIntent(openTimerIntent, notificationId + 3000)
            builder.addAction(android.R.drawable.ic_media_play, "Abrir Pausa", openTimerPending)
        } else {
            val snoozeIntent = Intent(this, AlarmRingService::class.java).apply {
                action = ACTION_SNOOZE
                putExtra(NotificationHelper.EXTRA_ID, notificationId)
                putExtra(NotificationHelper.EXTRA_TYPE, type)
                putExtra(NotificationHelper.EXTRA_TITLE, title)
                putExtra(NotificationHelper.EXTRA_TASK_ID, taskId)
                putExtra(NotificationHelper.EXTRA_HABIT_ID, habitId)
                putExtra(NotificationHelper.EXTRA_SOUND_URI, soundUri)
            }
            val snoozePending = serviceActionPendingIntent(snoozeIntent, notificationId + 3000)
            builder.addAction(android.R.drawable.ic_popup_reminder, "Adiar 10 min", snoozePending)
        }

        if (useFullScreen) {
            val fullScreenIntent = Intent(this, AlarmActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(NotificationHelper.EXTRA_ID, notificationId)
                putExtra(NotificationHelper.EXTRA_TYPE, type)
                putExtra(NotificationHelper.EXTRA_TITLE, title)
                putExtra(NotificationHelper.EXTRA_MESSAGE, message)
                putExtra(NotificationHelper.EXTRA_SOUND_URI, soundUri)
                putExtra(NotificationHelper.EXTRA_TASK_ID, taskId)
                putExtra(NotificationHelper.EXTRA_HABIT_ID, habitId)
            }
            val fullScreenPending = PendingIntent.getActivity(
                this,
                notificationId + 81000,
                fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setFullScreenIntent(fullScreenPending, true)
        }

        return builder.build()
    }

    private fun serviceActionPendingIntent(intent: Intent, requestCode: Int): PendingIntent {
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

    private fun acquireWakeLock() {
        try {
            releaseWakeLock()
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "citra:AlarmRingServiceLock").apply {
                setReferenceCounted(false)
                acquire(180_000L)
            }
        } catch (_: Exception) {}
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {}
        wakeLock = null
    }

    private fun requestAlarmAudioFocus() {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            audioManager = am
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .build()
                focusRequest = req
                am.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(null, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            }
        } catch (_: Exception) {}
    }

    private fun abandonAudioFocus() {
        try {
            val am = audioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { am.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
            }
        } catch (_: Exception) {}
        focusRequest = null
    }

    private fun startAlarmVibration(loop: Boolean) {
        try {
            val pattern = longArrayOf(0L, 600L, 400L)
            val repeatIdx = if (loop) 0 else -1
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createWaveform(pattern, repeatIdx))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                v?.vibrate(VibrationEffect.createWaveform(pattern, repeatIdx))
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                v?.vibrate(pattern, repeatIdx)
            }
        } catch (_: Exception) {}
    }

    private fun cancelVibration() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.cancel()
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                v?.cancel()
            }
        } catch (_: Exception) {}
    }

    private fun stopAlarmAndCleanup(notificationId: Int, cancelNotification: Boolean = true) {
        autoSilenceJob?.cancel()
        SoundEffectsManager.stopAlarmAudio()
        cancelVibration()
        abandonAudioFocus()
        releaseWakeLock()
        ActiveAlarmManager.dismissAlarm(this)
        if (cancelNotification) {
            try {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.cancel(notificationId)
            } catch (_: Exception) {}
        }
        try {
            ServiceCompat.stopForeground(
                this,
                if (cancelNotification) ServiceCompat.STOP_FOREGROUND_REMOVE else ServiceCompat.STOP_FOREGROUND_DETACH
            )
        } catch (_: Exception) {
            @Suppress("DEPRECATION")
            stopForeground(cancelNotification)
        }
        stopSelf()
    }

    override fun onDestroy() {
        autoSilenceJob?.cancel()
        SoundEffectsManager.stopAlarmAudio()
        cancelVibration()
        abandonAudioFocus()
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START_RING = "com.example.citra.alarm.START_RING"
        const val ACTION_DISMISS = "com.example.citra.alarm.DISMISS"
        const val ACTION_SNOOZE = "com.example.citra.alarm.SNOOZE"
        const val ACTION_OPEN_TIMER = "com.example.citra.alarm.OPEN_TIMER"

        const val EXTRA_VIBRATE = "extra_vibrate"
        const val EXTRA_OVERRIDE_SILENT = "extra_override_silent"

        fun startAlarm(
            context: Context,
            notificationId: Int,
            type: String,
            title: String,
            message: String,
            soundUri: String = "",
            taskId: Long = -1L,
            habitId: Long = -1L,
            loopAudio: Boolean = true,
            useFullScreen: Boolean = false,
            vibrate: Boolean = true,
            overrideSilent: Boolean = false
        ) {
            val appCtx = context.applicationContext ?: context
            val intent = Intent(appCtx, AlarmRingService::class.java).apply {
                action = ACTION_START_RING
                putExtra(NotificationHelper.EXTRA_ID, notificationId)
                putExtra(NotificationHelper.EXTRA_TYPE, type)
                putExtra(NotificationHelper.EXTRA_TITLE, title)
                putExtra(NotificationHelper.EXTRA_MESSAGE, message)
                putExtra(NotificationHelper.EXTRA_SOUND_URI, soundUri)
                putExtra(NotificationHelper.EXTRA_TASK_ID, taskId)
                putExtra(NotificationHelper.EXTRA_HABIT_ID, habitId)
                putExtra(NotificationHelper.EXTRA_LOOP_ALARM, loopAudio)
                putExtra(NotificationHelper.EXTRA_USE_FULL_SCREEN, useFullScreen)
                putExtra(EXTRA_VIBRATE, vibrate)
                putExtra(EXTRA_OVERRIDE_SILENT, overrideSilent)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appCtx.startForegroundService(intent)
                } else {
                    appCtx.startService(intent)
                }
            } catch (_: Exception) {}
        }

        fun stopAlarm(context: Context) {
            val appCtx = context.applicationContext ?: context
            SoundEffectsManager.stopAlarmAudio()
            ActiveAlarmManager.dismissAlarm(appCtx)
            try {
                appCtx.stopService(Intent(appCtx, AlarmRingService::class.java))
            } catch (_: Exception) {}
            try {
                val nm = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.cancel(NotificationHelper.TIMER_ALERT_NOTIFICATION_ID)
            } catch (_: Exception) {}
        }
    }
}
