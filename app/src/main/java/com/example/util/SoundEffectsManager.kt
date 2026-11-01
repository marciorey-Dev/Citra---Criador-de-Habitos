package com.example.util

import android.content.ContentResolver
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import com.example.R

data class SoundItem(
    val id: String,
    val name: String,
    val resId: Int,
    val isDefaultTag: String? = null
)

object SoundEffectsManager {
    private var previewPlayer: MediaPlayer? = null
    private var activeAlarmPlayer: MediaPlayer? = null

    const val DEFAULT_TASK_SOUND_ID = "sound_tarefa_concluida_default"
    const val DEFAULT_HABIT_SOUND_ID = "sound_checkin_habito_default"
    const val DEFAULT_ACHIEVEMENT_SOUND_ID = "sound_conquistas_default"
    const val DEFAULT_POMODORO_SOUND_ID = "sound_deep_warm_1"
    const val DEFAULT_NOTIFICATION_SOUND_ID = "sound_light_chime_1"

    val allSounds = listOf(
        SoundItem("sound_tarefa_concluida_default", "Digital Satisfatório (Padrão Tarefa)", R.raw.sound_tarefa_concluida_default, "tarefa"),
        SoundItem("sound_checkin_habito_default", "Digital Suave (Padrão Hábito)", R.raw.sound_checkin_habito_default, "habito"),
        SoundItem("sound_conquistas_default", "Brilho Festivo (Padrão Conquistas)", R.raw.sound_conquistas_default, "conquistas"),
        SoundItem("sound_deep_warm_1", "Ressonante Profundo 1 (Padrão Pomodoro)", R.raw.sound_deep_warm_1, "pomodoro"),
        SoundItem("sound_light_chime_1", "Sino Leve e Arejado 1 (Padrão Alertas)", R.raw.sound_light_chime_1, "alerta"),

        SoundItem("sound_fundo_dyven_clade", "Música de Fundo Citra (Dyven Clade)", R.raw.sound_fundo_dyven_clade, "fundo"),
        SoundItem("sound_analog_warm_1", "Analógico Suave 1", R.raw.sound_analog_warm_1),
        SoundItem("sound_analog_warm_2", "Analógico Suave 2", R.raw.sound_analog_warm_2),
        SoundItem("sound_analog_warm_3", "Analógico Suave 3", R.raw.sound_analog_warm_3),
        SoundItem("sound_analog_warm_4", "Analógico Suave 4", R.raw.sound_analog_warm_4),

        SoundItem("sound_celebration_1", "Celebração Radiante 1", R.raw.sound_celebration_1),
        SoundItem("sound_celebration_2", "Celebração Radiante 2", R.raw.sound_celebration_2),
        SoundItem("sound_celebration_3", "Celebração Radiante 3", R.raw.sound_celebration_3),

        SoundItem("sound_deep_warm_2", "Ressonante Profundo 2", R.raw.sound_deep_warm_2),
        SoundItem("sound_deep_warm_3", "Ressonante Profundo 3", R.raw.sound_deep_warm_3),
        SoundItem("sound_deep_warm_4", "Ressonante Profundo 4", R.raw.sound_deep_warm_4),

        SoundItem("sound_light_chime_2", "Sino Leve e Arejado 2", R.raw.sound_light_chime_2),
        SoundItem("sound_light_chime_3", "Sino Leve e Arejado 3", R.raw.sound_light_chime_3),
        SoundItem("sound_light_chime_4", "Sino Leve e Arejado 4", R.raw.sound_light_chime_4),

        SoundItem("sound_upbeat_1", "Recompensa Animada 1", R.raw.sound_upbeat_1),
        SoundItem("sound_upbeat_2", "Recompensa Animada 2", R.raw.sound_upbeat_2),
        SoundItem("sound_upbeat_3", "Recompensa Animada 3", R.raw.sound_upbeat_3),
        SoundItem("sound_upbeat_4", "Recompensa Animada 4", R.raw.sound_upbeat_4)
    )

    fun getSoundById(id: String): SoundItem {
        return allSounds.firstOrNull { it.id == id }
            ?: allSounds.firstOrNull { it.name.equals(id, ignoreCase = true) }
            ?: allSounds.first()
    }

    fun getSoundName(id: String): String {
        if (id.startsWith("content://") || id.startsWith("file://")) {
            return "Música Personalizada do Aparelho"
        }
        return getSoundById(id).name
    }

    fun getSoundResId(id: String): Int {
        return getSoundById(id).resId
    }

    fun getSoundUri(context: Context, soundIdOrUri: String): Uri {
        if (soundIdOrUri.isBlank()) {
            val resId = getSoundResId(DEFAULT_NOTIFICATION_SOUND_ID)
            return Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/$resId")
        }
        if (soundIdOrUri.startsWith("content://") || soundIdOrUri.startsWith("file://")) {
            return try {
                Uri.parse(soundIdOrUri)
            } catch (_: Exception) {
                val resId = getSoundResId(DEFAULT_NOTIFICATION_SOUND_ID)
                Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/$resId")
            }
        }
        val resId = getSoundResId(soundIdOrUri)
        return Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/$resId")
    }

    /**
     * Plays sound immediately (for in-app events like checking in habit or finishing task).
     */
    fun playSound(context: Context, resId: Int) {
        try {
            val player = MediaPlayer.create(context, resId)
            player?.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            player?.setOnCompletionListener { mp ->
                try { mp.release() } catch (_: Exception) {}
            }
            player?.start()
        } catch (_: Exception) {}
    }

    fun playSoundById(context: Context, id: String) {
        val sound = getSoundById(id)
        playSound(context, sound.resId)
    }

    /**
     * Dedicated alarm playback engine using USAGE_ALARM.
     * Guarantees audible playback on the Alarm audio stream even in silent / DND modes.
     */
    @Synchronized
    fun playAlarmAudio(context: Context, soundIdOrUri: String, loop: Boolean = false) {
        try {
            stopAlarmAudio()
            val uri = getSoundUri(context, soundIdOrUri)
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                        .build()
                )
                setDataSource(context, uri)
                isLooping = loop
                prepare()
                if (!loop) {
                    setOnCompletionListener { mp ->
                        try { mp.release() } catch (_: Exception) {}
                        if (activeAlarmPlayer == mp) activeAlarmPlayer = null
                    }
                }
                start()
            }
            activeAlarmPlayer = player
        } catch (_: Exception) {
            // Fallback to bundled sound if custom URI failed
            try {
                val fallbackRes = getSoundResId(DEFAULT_NOTIFICATION_SOUND_ID)
                val player = MediaPlayer.create(context, fallbackRes).apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    isLooping = loop
                    if (!loop) {
                        setOnCompletionListener { mp ->
                            try { mp.release() } catch (_: Exception) {}
                            if (activeAlarmPlayer == mp) activeAlarmPlayer = null
                        }
                    }
                    start()
                }
                activeAlarmPlayer = player
            } catch (_: Exception) {}
        }
    }

    @Synchronized
    fun stopAlarmAudio() {
        try {
            activeAlarmPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (_: Exception) {}
        activeAlarmPlayer = null
    }

    @Synchronized
    fun isAlarmPlaying(): Boolean {
        return try {
            activeAlarmPlayer?.isPlaying == true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Plays sound preview in settings dialog, stopping previous preview if running.
     */
    @Synchronized
    fun playPreview(context: Context, soundIdOrUri: String) {
        try {
            stopPreview()
            val uri = getSoundUri(context, soundIdOrUri)
            previewPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(context, uri)
                prepare()
                setOnCompletionListener { mp ->
                    try { mp.release() } catch (_: Exception) {}
                    if (previewPlayer == mp) previewPlayer = null
                }
                start()
            }
        } catch (_: Exception) {
            // Fallback for bundled IDs
            try {
                val resId = getSoundResId(soundIdOrUri)
                previewPlayer = MediaPlayer.create(context, resId)?.apply {
                    setOnCompletionListener { mp ->
                        try { mp.release() } catch (_: Exception) {}
                        if (previewPlayer == mp) previewPlayer = null
                    }
                    start()
                }
            } catch (_: Exception) {}
        }
    }

    @Synchronized
    fun stopPreview() {
        try {
            previewPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (_: Exception) {}
        previewPlayer = null
    }
}
