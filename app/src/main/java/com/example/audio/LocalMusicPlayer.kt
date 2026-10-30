package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.random.Random

data class AudioTrackItem(
    val id: String,
    val title: String,
    val uriString: String
)

data class PlayerState(
    val isPlaying: Boolean = false,
    val currentTrackIndex: Int = -1,
    val currentTrackTitle: String = "Silêncio",
    val playlist: List<AudioTrackItem> = emptyList(),
    val volume: Float = 0.7f
)

class LocalMusicPlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var audioTrack: AudioTrack? = null
    private var audioJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    fun setPlaylist(tracks: List<AudioTrackItem>) {
        _playerState.update { it.copy(playlist = tracks) }
    }

    fun playBuiltInAmbient(type: String) {
        stop()

        val sampleRate = 22050
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        try {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()

            _playerState.update {
                it.copy(
                    isPlaying = true,
                    currentTrackTitle = type
                )
            }

            audioJob = scope.launch {
                val buffer = ShortArray(bufferSize / 2)
                var lastVal = 0.0
                while (isActive && _playerState.value.isPlaying) {
                    val vol = _playerState.value.volume
                    for (i in buffer.indices) {
                        val white = Random.nextDouble(-1.0, 1.0)
                        val sample = when (type) {
                            "Chuva" -> {
                                // Filtered pinkish/brownish noise for rain
                                lastVal = (lastVal + (0.04 * white)) / 1.04
                                lastVal * 0.45 * vol
                            }
                            "Riacho" -> {
                                // Gentle murmur with soft oscillating frequency
                                lastVal = (lastVal + (0.02 * white)) / 1.02
                                lastVal * 0.35 * vol
                            }
                            "Café" -> {
                                // Warm rumble
                                lastVal = (lastVal + (0.06 * white)) / 1.06
                                lastVal * 0.4 * vol
                            }
                            "Pássaros" -> {
                                // Soft forest breeze
                                lastVal = (lastVal + (0.03 * white)) / 1.03
                                lastVal * 0.3 * vol
                            }
                            else -> {
                                // Soft white noise
                                white * 0.25 * vol
                            }
                        }
                        buffer[i] = (sample.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
                    }
                    audioTrack?.write(buffer, 0, buffer.size)
                }
            }
        } catch (e: Exception) {
            Log.e("LocalMusicPlayer", "Failed to start ambient audio", e)
        }
    }

    fun playTrack(index: Int) {
        val list = _playerState.value.playlist
        if (index !in list.indices) return

        val track = list[index]
        try {
            stop()

            val uri = Uri.parse(track.uriString)
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(context, uri)
                val vol = _playerState.value.volume
                setVolume(vol, vol)
                setOnPreparedListener { mp ->
                    mp.start()
                    _playerState.update {
                        it.copy(
                            isPlaying = true,
                            currentTrackIndex = index,
                            currentTrackTitle = track.title
                        )
                    }
                }
                setOnCompletionListener {
                    stop()
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e("LocalMusicPlayer", "Failed to play track: ${track.title}", e)
        }
    }

    fun togglePlayPause() {
        if (_playerState.value.isPlaying) {
            stop()
        } else {
            playBuiltInAmbient("Chuva")
        }
    }

    fun setVolume(vol: Float) {
        val safeVol = vol.coerceIn(0f, 1f)
        _playerState.update { it.copy(volume = safeVol) }
        try {
            mediaPlayer?.setVolume(safeVol, safeVol)
            audioTrack?.setVolume(safeVol)
        } catch (_: Exception) {}
    }

    fun stop() {
        audioJob?.cancel()
        audioJob = null
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null

        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null

        _playerState.update { it.copy(isPlaying = false, currentTrackTitle = "Silêncio") }
    }

    fun release() {
        stop()
        scope.cancel()
    }
}
