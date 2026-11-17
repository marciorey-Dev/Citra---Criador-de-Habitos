package com.example.notifications

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import java.util.concurrent.atomic.AtomicInteger

/**
 * Lightweight process tracker knowing whether Citra's MainActivity is currently in foreground
 * and the device screen is unlocked, allowing completion alerts to decide between in-screen
 * live celebration vs Lockscreen FullScreenIntent + AlarmActivity.
 */
object AppForegroundTracker {
    private val startedCount = AtomicInteger(0)

    fun onActivityStarted() {
        startedCount.incrementAndGet()
    }

    fun onActivityStopped() {
        val current = startedCount.get()
        if (current > 0) {
            startedCount.decrementAndGet()
        }
    }

    fun resetForTest() {
        startedCount.set(0)
    }

    fun isAppVisibleAndUnlocked(context: Context): Boolean {
        if (startedCount.get() <= 0) return false
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (pm != null && !pm.isInteractive) return false
            val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            if (km != null && km.isKeyguardLocked) return false
            true
        } catch (_: Exception) {
            startedCount.get() > 0
        }
    }
}
