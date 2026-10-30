package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class NoteAudioState(
    val playingBlockId: String? = null,
    val isPlaying: Boolean = false,
    val isLooping: Boolean = false,
    val volume: Float = 1.0f,
    val currentUrl: String = ""
)

/**
 * Persistent audio playback manager for notes.
 * Keeps audio playing continuously even when note items scroll out of view in LazyColumn.
 */
object NoteAudioPlaybackManager {
    private const val TAG = "NoteAudioPlayback"
    private var mediaPlayer: MediaPlayer? = null

    private val _playbackState = MutableStateFlow(NoteAudioState())
    val playbackState: StateFlow<NoteAudioState> = _playbackState.asStateFlow()

    fun playOrPause(context: Context, blockId: String, url: String, volume: Float, isLoop: Boolean) {
        val currentState = _playbackState.value

        // If clicking the same block that is currently playing -> pause it
        if (currentState.playingBlockId == blockId && currentState.isPlaying) {
            pause()
            return
        }

        // If clicking the same block that is paused -> resume it
        if (currentState.playingBlockId == blockId && mediaPlayer != null) {
            try {
                mediaPlayer?.start()
                _playbackState.update { it.copy(isPlaying = true) }
                return
            } catch (e: Exception) {
                Log.e(TAG, "Error resuming audio", e)
            }
        }

        // New audio block or restart
        stop()

        if (url.isBlank()) return

        try {
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(context, Uri.parse(url))
                this.isLooping = isLoop
                val safeVol = volume.coerceIn(0f, 1f)
                setVolume(safeVol, safeVol)
                prepare()
                setOnCompletionListener {
                    if (!isLoop) {
                        _playbackState.update { it.copy(isPlaying = false) }
                    }
                }
                start()
            }
            mediaPlayer = mp
            _playbackState.value = NoteAudioState(
                playingBlockId = blockId,
                isPlaying = true,
                isLooping = isLoop,
                volume = volume,
                currentUrl = url
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio block: $url", e)
            stop()
        }
    }

    fun pause() {
        try {
            mediaPlayer?.pause()
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing audio", e)
        }
        _playbackState.update { it.copy(isPlaying = false) }
    }

    fun setVolume(volume: Float) {
        val safeVol = volume.coerceIn(0f, 1f)
        _playbackState.update { it.copy(volume = safeVol) }
        try {
            mediaPlayer?.setVolume(safeVol, safeVol)
        } catch (_: Exception) {}
    }

    fun setLoop(isLoop: Boolean) {
        _playbackState.update { it.copy(isLooping = isLoop) }
        try {
            mediaPlayer?.isLooping = isLoop
        } catch (_: Exception) {}
    }

    fun stop() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        _playbackState.value = NoteAudioState()
    }
}
