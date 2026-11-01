package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class NoteAudioState(
    val playingBlockId: String? = null,
    val isPlaying: Boolean = false,
    val isLooping: Boolean = false,
    val volume: Float = 1.0f,
    val currentUrl: String = "",
    val currentPositionMs: Int = 0,
    val durationMs: Int = 0
)

/**
 * Persistent audio playback manager for notes with progress tracking and seek support.
 * Keeps audio playing continuously even when note items scroll out of view in LazyColumn.
 */
object NoteAudioPlaybackManager {
    private const val TAG = "NoteAudioPlayback"
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _playbackState = MutableStateFlow(NoteAudioState())
    val playbackState: StateFlow<NoteAudioState> = _playbackState.asStateFlow()

    private fun normalizeUrl(url: String): String {
        return url.trim()
            .removePrefix("file://")
            .removeSuffix("/")
    }

    fun isCurrentAudio(blockId: String, url: String): Boolean {
        val s = _playbackState.value
        if (s.playingBlockId == blockId) return true
        val cleanUrl = normalizeUrl(url)
        val cleanCurrentUrl = normalizeUrl(s.currentUrl)
        return cleanCurrentUrl.isNotBlank() && (cleanCurrentUrl == cleanUrl || cleanCurrentUrl.equals(cleanUrl, ignoreCase = true))
    }

    fun isPlayingAudio(blockId: String, url: String): Boolean {
        return isCurrentAudio(blockId, url) && _playbackState.value.isPlaying
    }

    fun playOrPause(context: Context, blockId: String, url: String, volume: Float, isLoop: Boolean) {
        val currentState = _playbackState.value
        val cleanUrl = normalizeUrl(url)
        val cleanCurrentUrl = normalizeUrl(currentState.currentUrl)
        val isSameAudio = currentState.playingBlockId == blockId || (cleanCurrentUrl.isNotBlank() && (cleanCurrentUrl == cleanUrl || cleanCurrentUrl.equals(cleanUrl, ignoreCase = true)))

        // If clicking the same block that is currently playing -> pause it
        if (isSameAudio && currentState.isPlaying) {
            pause()
            return
        }

        // If clicking the same block that is paused -> resume it
        if (isSameAudio && mediaPlayer != null) {
            try {
                mediaPlayer?.start()
                _playbackState.update { it.copy(isPlaying = true, playingBlockId = blockId) }
                startProgressTracker()
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
                if (url.startsWith("content://")) {
                    val uri = Uri.parse(url)
                    try {
                        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                            setDataSource(pfd.fileDescriptor)
                        } ?: setDataSource(context, uri)
                    } catch (_: Exception) {
                        setDataSource(context, uri)
                    }
                } else {
                    val cleanPath = url.removePrefix("file://")
                    setDataSource(cleanPath)
                }
                this.isLooping = isLoop
                val safeVol = volume.coerceIn(0f, 1f)
                setVolume(safeVol, safeVol)
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                    true // Prevents erroneous completion trigger
                }
                prepare()
                setOnCompletionListener {
                    if (!isLoop) {
                        _playbackState.update { it.copy(isPlaying = false, currentPositionMs = 0) }
                        stopProgressTracker()
                    }
                }
                start()
            }
            mediaPlayer = mp
            val dur = try { mp.duration.coerceAtLeast(0) } catch (_: Exception) { 0 }
            _playbackState.value = NoteAudioState(
                playingBlockId = blockId,
                isPlaying = true,
                isLooping = isLoop,
                volume = volume,
                currentUrl = url,
                currentPositionMs = 0,
                durationMs = dur
            )
            startProgressTracker()
        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio block: $url", e)
            stop()
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val mp = mediaPlayer
                if (mp != null && _playbackState.value.isPlaying) {
                    try {
                        val currentPos = mp.currentPosition
                        val dur = mp.duration.coerceAtLeast(0)
                        _playbackState.update {
                            it.copy(currentPositionMs = currentPos, durationMs = dur)
                        }
                    } catch (_: Exception) {}
                }
                delay(100)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    fun seekTo(positionMs: Int) {
        try {
            val safePos = positionMs.coerceAtLeast(0)
            mediaPlayer?.seekTo(safePos)
            _playbackState.update { it.copy(currentPositionMs = safePos) }
        } catch (e: Exception) {
            Log.e(TAG, "Error seeking audio", e)
        }
    }

    fun pause() {
        try {
            mediaPlayer?.pause()
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing audio", e)
        }
        stopProgressTracker()
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
        stopProgressTracker()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        _playbackState.value = NoteAudioState()
    }
}
