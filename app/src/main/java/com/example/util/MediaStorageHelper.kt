package com.example.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object MediaStorageHelper {
    private const val TAG = "MediaStorageHelper"

    /**
     * Copies an image from a content Uri or stream into internal private storage
     * so it never disappears on app relaunch or device restart.
     */
    fun saveProfileImage(context: Context, sourceUri: Uri): String? {
        return try {
            val dir = File(context.filesDir, "profile")
            if (!dir.exists()) dir.mkdirs()
            val destFile = File(dir, "profile_avatar.jpg")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            destFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save profile image", e)
            null
        }
    }

    fun saveProfileBitmap(context: Context, bitmap: Bitmap): String? {
        return try {
            val dir = File(context.filesDir, "profile")
            if (!dir.exists()) dir.mkdirs()
            val destFile = File(dir, "profile_avatar.jpg")
            FileOutputStream(destFile).use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
            }
            destFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save profile bitmap", e)
            null
        }
    }

    /**
     * Copies an imported note image to permanent internal app storage.
     */
    fun saveNoteImage(context: Context, sourceUri: Uri): String? {
        return try {
            val dir = File(context.filesDir, "note_images")
            if (!dir.exists()) dir.mkdirs()
            val extension = getExtensionFromUri(context, sourceUri) ?: "jpg"
            val destFile = File(dir, "img_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.$extension")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            destFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save note image", e)
            null
        }
    }

    /**
     * Saves an imported note audio file:
     * - If <= 10MB: saved in internal notes_audios/
     * - If > 10MB: saved in a dedicated app folder for easy tracking.
     */
    fun saveNoteAudio(context: Context, sourceUri: Uri): String? {
        return try {
            val size = getFileSize(context, sourceUri)
            val extension = getExtensionFromUri(context, sourceUri) ?: "m4a"
            val isLarge = size > (10 * 1024 * 1024) // > 10MB

            val targetDir = if (isLarge) {
                // Dedicated folder for easy tracking
                File(context.filesDir, "large_audios").apply { if (!exists()) mkdirs() }
            } else {
                File(context.filesDir, "note_audios").apply { if (!exists()) mkdirs() }
            }

            val prefix = if (isLarge) "audio_large" else "audio"
            val destFile = File(targetDir, "${prefix}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.$extension")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            destFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save note audio", e)
            null
        }
    }

    /**
     * Prepares an audio recording output file in Environment.DIRECTORY_MUSIC/CitraRecord.
     */
    fun createRecordingFile(context: Context): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "CitraRec_$timeStamp.m4a"

        return try {
            val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val citraRecordDir = File(musicDir, "CitraRecord")
            if (!citraRecordDir.exists()) {
                citraRecordDir.mkdirs()
            }
            File(citraRecordDir, fileName)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create recording file in Music/CitraRecord, falling back to internal", e)
            val fallbackDir = File(context.filesDir, "CitraRecord").apply { if (!exists()) mkdirs() }
            File(fallbackDir, fileName)
        }
    }

    /**
     * Saves exported content (.pdf, .txt, .html, etc.) into Android's Downloads directory.
     */
    fun saveExportToDownloads(context: Context, filename: String, mimeType: String, contentBytes: ByteArray): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Citra")
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(contentBytes)
                    }
                }
                uri
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val citraDir = File(downloadsDir, "Citra").apply { if (!exists()) mkdirs() }
                val targetFile = File(citraDir, filename)
                FileOutputStream(targetFile).use { output ->
                    output.write(contentBytes)
                }
                Uri.fromFile(targetFile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save to downloads", e)
            null
        }
    }

    private fun getFileSize(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1 && cursor.moveToFirst()) {
                    cursor.getLong(sizeIndex)
                } else 0L
            } ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    private fun getExtensionFromUri(context: Context, uri: Uri): String? {
        return try {
            val type = context.contentResolver.getType(uri)
            if (type != null) {
                android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(type)
            } else {
                uri.path?.substringAfterLast('.', "")?.takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) {
            null
        }
    }
}
