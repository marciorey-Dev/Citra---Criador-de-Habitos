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
     * Opens an InputStream from any media source representation:
     * - content:// URI
     * - file:// URI
     * - Absolute path (/data/... or /storage/...)
     * - Relative path in Documents/Citra Docs/ or downloads
     */
    fun openInputStream(context: Context, source: String): InputStream? {
        if (source.isBlank()) return null
        return try {
            when {
                source.startsWith("content://") -> {
                    context.contentResolver.openInputStream(Uri.parse(source))
                }
                source.startsWith("file://") -> {
                    val path = Uri.parse(source).path ?: source.removePrefix("file://")
                    val f = File(path)
                    if (f.exists() && f.canRead()) f.inputStream() else null
                }
                source.startsWith("/") -> {
                    val f = File(source)
                    if (f.exists() && f.canRead()) f.inputStream() else null
                }
                else -> {
                    // Check Documents/Citra Docs/ or fallback app dirs
                    val inDocs = File(CitraDocsFileManager.getDocsFolder(context), source)
                    if (inDocs.exists() && inDocs.canRead()) {
                        inDocs.inputStream()
                    } else {
                        val inFiles = File(context.filesDir, source)
                        if (inFiles.exists() && inFiles.canRead()) inFiles.inputStream() else null
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to open input stream for $source", e)
            null
        }
    }

    /**
     * Copies a background image to permanent internal storage so it survives reboots and cache clearing.
     */
    fun saveNoteBackground(context: Context, source: String): String? {
        return try {
            val dir = File(context.filesDir, "note_backgrounds").apply { if (!exists()) mkdirs() }
            val extension = getExtension(context, source) ?: "jpg"
            val destFile = File(dir, "bg_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.$extension")
            openInputStream(context, source)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            destFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save note background", e)
            null
        }
    }

    fun saveNoteBackground(context: Context, sourceUri: Uri): String? {
        return saveNoteBackground(context, sourceUri.toString())
    }

    /**
     * Copies an imported note image to permanent internal app storage.
     */
    fun saveNoteImage(context: Context, source: String): String? {
        return try {
            val dir = File(context.filesDir, "note_images").apply { if (!exists()) mkdirs() }
            val extension = getExtension(context, source) ?: "jpg"
            val destFile = File(dir, "img_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.$extension")
            openInputStream(context, source)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            destFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save note image", e)
            null
        }
    }

    fun saveNoteImage(context: Context, sourceUri: Uri): String? {
        return saveNoteImage(context, sourceUri.toString())
    }

    /**
     * Saves an imported note audio file into permanent internal app storage:
     * - If <= 10MB: saved in internal note_audios/
     * - If > 10MB: saved in internal large_audios/
     */
    fun saveNoteAudio(context: Context, source: String): String? {
        return try {
            val size = if (source.startsWith("content://")) getFileSize(context, Uri.parse(source)) else {
                val f = File(source.removePrefix("file://"))
                if (f.exists()) f.length() else 0L
            }
            val extension = getExtension(context, source) ?: "m4a"
            val isLarge = size > (10 * 1024 * 1024) // > 10MB

            val targetDir = if (isLarge) {
                File(context.filesDir, "large_audios").apply { if (!exists()) mkdirs() }
            } else {
                File(context.filesDir, "note_audios").apply { if (!exists()) mkdirs() }
            }

            val prefix = if (isLarge) "audio_large" else "audio"
            val destFile = File(targetDir, "${prefix}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.$extension")
            openInputStream(context, source)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            destFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save note audio", e)
            null
        }
    }

    fun saveNoteAudio(context: Context, sourceUri: Uri): String? {
        return saveNoteAudio(context, sourceUri.toString())
    }

    /**
     * Prepares an audio recording output file in app's permanent private storage
     * (File(context.filesDir, "CitraRecord")), ensuring it survives device reboot,
     * app cache clearing, and never depends on temporary media permissions.
     */
    fun createRecordingFile(context: Context): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "CitraRec_$timeStamp.m4a"
        val dir = File(context.filesDir, "CitraRecord").apply { if (!exists()) mkdirs() }
        return File(dir, fileName)
    }

    /**
     * Verifies if a given media path or URI is accessible, non-empty and exists.
     * Prevents silent failures by providing a clear indication when media is missing.
     */
    fun isMediaFileValid(context: Context, pathOrUri: String?): Boolean {
        if (pathOrUri.isNullOrBlank()) return false
        return try {
            if (pathOrUri.startsWith("content://")) {
                val uri = Uri.parse(pathOrUri)
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    pfd.statSize > 0
                } ?: false
            } else {
                val cleanPath = pathOrUri.removePrefix("file://")
                val f = File(cleanPath)
                if (f.exists() && f.length() > 0) {
                    true
                } else {
                    openInputStream(context, pathOrUri)?.use { true } ?: false
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Scans an imported Markdown string for media tokens (images and audios).
     * If any token points to an external file, temporary content URI or relative path,
     * it copies the media into the app's persistent internal storage and updates the link.
     */
    fun processAndPersistImportedNoteMedia(context: Context, markdownContent: String): String {
        var processed = markdownContent
        val permanentStoragePrefix = context.filesDir.absolutePath

        // 1. Process images: ![alt|...](url)
        val imgRegex = Regex("!\\[(.*?)\\]\\((.*?)\\)")
        imgRegex.findAll(markdownContent).forEach { match ->
            val altAndMeta = match.groupValues[1]
            val url = match.groupValues[2].trim()

            if (url.isNotBlank() && !url.contains(permanentStoragePrefix)) {
                val persistentPath = saveNoteImage(context, url)
                if (persistentPath != null) {
                    val oldToken = match.value
                    val newToken = "![$altAndMeta]($persistentPath)"
                    processed = processed.replace(oldToken, newToken)
                }
            }
        }

        // 2. Process audios: [audio:title](url) or ![audio:title](url)
        val audioRegex = Regex("(!?\\[audio:(.*?)\\]\\((.*?)\\))")
        audioRegex.findAll(markdownContent).forEach { match ->
            val isExclamation = match.value.startsWith("!")
            val titleAndMeta = match.groupValues[2]
            val url = match.groupValues[3].trim()

            if (url.isNotBlank() && !url.contains(permanentStoragePrefix)) {
                val persistentPath = saveNoteAudio(context, url)
                if (persistentPath != null) {
                    val oldToken = match.value
                    val prefix = if (isExclamation) "!" else ""
                    val newToken = "$prefix[audio:$titleAndMeta]($persistentPath)"
                    processed = processed.replace(oldToken, newToken)
                }
            }
        }

        return processed
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

    /**
     * Streams an exported file directly to Downloads without loading entire byte array into memory.
     */
    fun copyExportToDownloads(context: Context, filename: String, mimeType: String, sourceUri: Uri): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Citra")
                }
                val destUri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (destUri != null) {
                    context.contentResolver.openInputStream(sourceUri)?.use { input ->
                        context.contentResolver.openOutputStream(destUri)?.use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                destUri
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val citraDir = File(downloadsDir, "Citra").apply { if (!exists()) mkdirs() }
                val targetFile = File(citraDir, filename)
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Uri.fromFile(targetFile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy export to downloads", e)
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

    private fun getExtension(context: Context, source: String): String? {
        return try {
            if (source.startsWith("content://")) {
                getExtensionFromUri(context, Uri.parse(source))
            } else {
                val clean = source.substringBefore('?').substringBefore('#')
                val ext = clean.substringAfterLast('.', "").takeIf { it.isNotBlank() && it.length <= 5 }
                ext ?: if (source.contains("audio", ignoreCase = true)) "m4a" else "jpg"
            }
        } catch (_: Exception) {
            null
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
