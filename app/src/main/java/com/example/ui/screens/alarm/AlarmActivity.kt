package com.example.ui.screens.alarm

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Snooze
import com.example.MainActivity
import com.example.notifications.ActiveAlarmData
import com.example.notifications.ActiveAlarmManager
import com.example.notifications.NotificationHelper
import com.example.service.AlarmRingService
import com.example.ui.theme.CitraTheme

/**
 * Lightweight, dedicated lockscreen Activity launched by FullScreenIntent for:
 *  - Tasks with loopAlarm == true
 *  - Pomodoro / Arancia focus or break completion while locked or backgrounded.
 */
class AlarmActivity : ComponentActivity() {

    private var alarmData: ActiveAlarmData = ActiveAlarmData(
        notificationId = NotificationHelper.TIMER_ALERT_NOTIFICATION_ID,
        type = NotificationHelper.TYPE_TASK,
        title = "Alarme Citra"
    )
    private var messageText: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableLockscreenWake()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        parseIntent(intent)

        val isPomodoro = alarmData.type == NotificationHelper.TYPE_POMODORO
        setContent {
            CitraTheme(darkTheme = true) {
                AlarmScreen(
                    alarmData = alarmData,
                    clockText = "00:00",
                    subtitleText = messageText,
                    secondaryActionLabel = if (isPomodoro) "Abrir Pausa" else "Adiar 10 minutos",
                    secondaryActionIcon = if (isPomodoro) Icons.Default.PlayArrow else Icons.Default.Snooze,
                    onDismiss = {
                        dismissAlarm()
                    },
                    onSnooze = {
                        if (isPomodoro) {
                            openTimerInMainApp()
                        } else {
                            snoozeTaskAlarm()
                        }
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parseIntent(intent)
    }

    private fun parseIntent(intent: Intent?) {
        val active = ActiveAlarmManager.activeAlarm.value
        val nid = intent?.getIntExtra(NotificationHelper.EXTRA_ID, -1)?.takeIf { it != -1 }
            ?: active?.notificationId
            ?: NotificationHelper.TIMER_ALERT_NOTIFICATION_ID
        val type = intent?.getStringExtra(NotificationHelper.EXTRA_TYPE)
            ?: active?.type
            ?: NotificationHelper.TYPE_TASK
        val title = intent?.getStringExtra(NotificationHelper.EXTRA_TITLE)
            ?: active?.title
            ?: "Alarme Citra"
        val taskId = intent?.getLongExtra(NotificationHelper.EXTRA_TASK_ID, -1L)
            ?: active?.taskId
            ?: -1L
        val habitId = intent?.getLongExtra(NotificationHelper.EXTRA_HABIT_ID, -1L)
            ?: active?.habitId
            ?: -1L
        val soundUri = intent?.getStringExtra(NotificationHelper.EXTRA_SOUND_URI)
            ?: active?.soundUri
            ?: ""

        messageText = intent?.getStringExtra(NotificationHelper.EXTRA_MESSAGE)
        alarmData = ActiveAlarmData(
            notificationId = nid,
            type = type,
            title = title,
            taskId = taskId,
            habitId = habitId,
            soundUri = soundUri,
            loopAlarm = true
        )
    }

    private fun enableLockscreenWake() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun dismissAlarm() {
        stopRingAndClearNotification()
        finish()
    }

    private fun snoozeTaskAlarm() {
        val triggerAt = System.currentTimeMillis() + 10L * 60L * 1000L
        NotificationHelper.scheduleTaskSnooze(
            context = this,
            taskId = alarmData.taskId,
            title = alarmData.title.ifBlank { "Tarefa" },
            triggerTime = triggerAt,
            customSoundUri = alarmData.soundUri
        )
        stopRingAndClearNotification()
        finish()
    }

    private fun openTimerInMainApp() {
        stopRingAndClearNotification()
        val launchMain = {
            try {
                val mainIntent = Intent(this, MainActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                    putExtra(NotificationHelper.EXTRA_OPEN_TIMER_TAB, true)
                }
                startActivity(mainIntent)
            } catch (_: Exception) {}
            finish()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            if (km?.isKeyguardLocked == true) {
                km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() {
                        launchMain()
                    }

                    override fun onDismissCancelled() {
                        launchMain()
                    }

                    override fun onDismissError() {
                        launchMain()
                    }
                })
                return
            }
        }
        launchMain()
    }

    private fun stopRingAndClearNotification() {
        AlarmRingService.stopAlarm(this)
        ActiveAlarmManager.dismissAlarm(this)
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(alarmData.notificationId)
        } catch (_: Exception) {}
    }
}
