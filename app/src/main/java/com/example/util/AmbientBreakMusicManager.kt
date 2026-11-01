package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.example.R

/**
 * Ambient background music manager for Citrus Calm / breaks and continuous background audio.
 */
object AmbientBreakMusicManager {

    private var mediaPlayer: MediaPlayer? = null
    private var isBreakActive = false
    private var isAlwaysPlayingInApp = false
    private var currentVolume = 0.5f

    fun init(context: Context, alwaysPlaying: Boolean, volume: Float) {
        isAlwaysPlayingInApp = alwaysPlaying
        currentVolume = volume
        if (isAlwaysPlayingInApp) {
            startPlaying(context)
        }
    }

    fun onBreakStateChanged(context: Context, isBreak: Boolean) {
        isBreakActive = isBreak
        if (isAlwaysPlayingInApp) {
            // Already playing
            return
        }
        if (isBreak) {
            startPlaying(context)
        } else {
            stopPlaying()
        }
    }

    private var wasPlayingBeforeBackground = false
    private var isAppInBackground = false

    fun onAppBackgrounded() {
        if (!isAppInBackground) {
            isAppInBackground = true
            wasPlayingBeforeBackground = mediaPlayer?.isPlaying == true || isAlwaysPlayingInApp
        }
        try {
            mediaPlayer?.pause()
        } catch (_: Exception) {}
    }

    fun onAppForegrounded(context: Context) {
        isAppInBackground = false
        val shouldPlay = isAlwaysPlayingInApp || (isBreakActive && wasPlayingBeforeBackground)
        if (shouldPlay) {
            if (mediaPlayer != null) {
                try {
                    mediaPlayer?.start()
                    wasPlayingBeforeBackground = false
                    return
                } catch (_: Exception) {}
            }
            startPlaying(context)
        }
        wasPlayingBeforeBackground = false
    }

    fun setAlwaysPlayingInApp(context: Context, enabled: Boolean) {
        isAlwaysPlayingInApp = enabled
        if (enabled) {
            startPlaying(context)
        } else if (!isBreakActive) {
            stopPlaying()
        }
    }

    fun setVolume(volume: Float) {
        currentVolume = volume.coerceIn(0f, 1f)
        try {
            mediaPlayer?.setVolume(currentVolume, currentVolume)
        } catch (_: Exception) {}
    }

    private fun startPlaying(context: Context) {
        if (mediaPlayer?.isPlaying == true) return
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(context, R.raw.sound_fundo_dyven_clade)?.apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                isLooping = true
                setVolume(currentVolume, currentVolume)
                start()
            }
        } catch (_: Exception) {}
    }

    private fun stopPlaying() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (_: Exception) {}
    }
}
