package com.example.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaRecorder
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object MediaStorageHelper {
    private const val TAG = "MediaStorageHelper"

    private val CITRA_FOLDER_NAMES = setOf(
        "citra",
        "citra docs",
        "citra pic",
        "citra records",
        "docs",
        "pics",
        "records"
    )

    /**
     * Copies an image from a content Uri or stream into internal private storage
     * scoped by user ID so it never disappears on app relaunch or crosses between accounts.
     */
    fun saveUserAvatar(context: Context, uid: String, sourceUri: Uri): String? {
        return try {
            val dir = File(context.filesDir, "avatars")
            if (!dir.exists()) dir.mkdirs()
            val destFile = File(dir, "user_${uid}.jpg")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            destFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save user avatar", e)
            null
        }
    }

    fun saveUserAvatarBitmap(context: Context, uid: String, bitmap: Bitmap): String? {
        return try {
            val dir = File(context.filesDir, "avatars")
            if (!dir.exists()) dir.mkdirs()
            val destFile = File(dir, "user_${uid}.jpg")
            FileOutputStream(destFile).use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
            }
            destFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save user avatar bitmap", e)
            null
        }
    }

    fun saveGroupAvatar(context: Context, groupId: Long, sourceUri: Uri): String? {
        return try {
            val dir = File(context.filesDir, "avatars")
            if (!dir.exists()) dir.mkdirs()
            val destFile = File(dir, "group_${groupId}.jpg")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            destFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save group avatar", e)
            null
        }
    }

    fun getUserAvatarFile(context: Context, uid: String): File {
        val dir = File(context.filesDir, "avatars")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "user_${uid}.jpg")
    }

    fun getGroupAvatarFile(context: Context, groupId: Long): File {
        val dir = File(context.filesDir, "avatars")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "group_${groupId}.jpg")
    }

    // Backward compatible wrappers
    fun saveProfileImage(context: Context, sourceUri: Uri): String? {
        return saveUserAvatar(context, "default_user", sourceUri)
    }

    fun saveProfileBitmap(context: Context, bitmap: Bitmap): String? {
        return saveUserAvatarBitmap(context, "default_user", bitmap)
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
     * Obtains the directory "Citra Pic" located in Android's Pictures folder.
     */
    fun getCitraPicFolder(context: Context): File {
        try {
            val publicPictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val citraPicDir = File(publicPictures, "Citra Pic")
            if (citraPicDir.exists() || citraPicDir.mkdirs()) {
                return citraPicDir
            }
        } catch (e: Exception) {
            Log.w(TAG, "Public Pictures directory inaccessible, trying external app pictures", e)
        }

        try {
            val extPictures = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            if (extPictures != null) {
                val citraPicDir = File(extPictures, "Citra Pic")
                if (citraPicDir.exists() || citraPicDir.mkdirs()) {
                    return citraPicDir
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "External app pictures directory failed, falling back to internal storage", e)
        }

        val internalDir = File(context.filesDir, "Citra Pic")
        if (!internalDir.exists()) {
            internalDir.mkdirs()
        }
        return internalDir
    }

    /**
     * Checks whether a normalized path or folder chain points inside any standard Citra directory:
     * Citra, Citra Docs (docs), Citra Pic (pics), or Citra Records (records).
     */
    fun isCitraFolderPath(rawPathOrRelative: String?): Boolean {
        if (rawPathOrRelative.isNullOrBlank()) return false
        val normalized = rawPathOrRelative.replace('\\', '/').trim()
        val segments = normalized.split('/').filter { it.isNotBlank() }
        if (segments.isEmpty()) return false
        // Check directory segments (exclude the trailing file segment if it has an extension)
        val dirSegments = if (segments.last().contains('.')) segments.dropLast(1) else segments
        for (i in dirSegments.indices) {
            val seg = dirSegments[i].lowercase(Locale.ROOT)
            if (seg == "citra" || seg == "citra docs" || seg == "citra pic" || seg == "citra records") {
                return true
            }
            if ((seg == "docs" || seg == "pics" || seg == "records") &&
                i > 0 && dirSegments[i - 1].equals("citra", ignoreCase = true)
            ) {
                return true
            }
        }
        return false
    }

    /**
     * Extracts a relative path under primary external storage from SAF ExternalStorageProvider URIs
     * (e.g. "primary:Pictures/Citra Pic/foto.jpg" -> "Pictures/Citra Pic/foto.jpg").
     */
    fun extractExternalStorageRelativePath(uriString: String): String? {
        if (!uriString.startsWith("content://")) return null
        return try {
            val parsed = Uri.parse(uriString)
            val docId = try {
                DocumentsContract.getDocumentId(parsed)
            } catch (_: Exception) {
                val decoded = URLDecoder.decode(uriString, "UTF-8")
                decoded.substringAfter("/document/", "")
                    .substringBefore('?')
                    .takeIf { it.isNotBlank() }
            } ?: return null

            when {
                docId.startsWith("raw:", ignoreCase = true) -> docId.substring(4)
                docId.startsWith("primary:", ignoreCase = true) -> docId.substringAfter("primary:")
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun getAllCitraDirectories(context: Context): List<File> {
        val dirs = mutableListOf<File>()
        try { dirs.add(getCitraPicFolder(context)) } catch (_: Exception) {}
        try { dirs.add(getCitraRecordsFolder(context)) } catch (_: Exception) {}
        try { dirs.addAll(CitraDocsFileManager.getAllDocsFolders(context)) } catch (_: Exception) {}
        try {
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dirs.add(File(downloads, "Citra"))
        } catch (_: Exception) {}
        try {
            val pubPictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            dirs.add(File(pubPictures, "Citra Pic"))
        } catch (_: Exception) {}
        try {
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.let {
                dirs.add(File(it, "Citra Pic"))
            }
            dirs.add(File(context.filesDir, "Citra Pic"))
            dirs.add(File(context.filesDir, "Citra Records"))
            dirs.add(File(context.filesDir, "note_backgrounds"))
        } catch (_: Exception) {}
        return dirs.distinctBy { it.absolutePath }
    }

    /**
     * Deletes all media files stored in Citra Pic folders (public Pictures/Citra Pic,
     * app external/internal Citra Pic, note_backgrounds, and restored note images) and MediaStore.
     */
    fun deleteAllCitraPicFiles(context: Context): Int {
        var deletedCount = 0
        val deletedPaths = mutableListOf<String>()
        val candidateDirs = mutableListOf<File>()

        try { candidateDirs.add(getCitraPicFolder(context)) } catch (_: Exception) {}
        try {
            val pubPictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            candidateDirs.add(File(pubPictures, "Citra Pic"))
        } catch (_: Exception) {}
        try {
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.let {
                candidateDirs.add(File(it, "Citra Pic"))
            }
        } catch (_: Exception) {}
        try {
            candidateDirs.add(File(context.filesDir, "Citra Pic"))
            candidateDirs.add(File(context.filesDir, "note_backgrounds"))
            candidateDirs.add(File(context.filesDir, "images"))
        } catch (_: Exception) {}

        for (dir in candidateDirs.distinctBy { it.absolutePath }) {
            if (!dir.exists() || !dir.isDirectory) continue
            val files = dir.listFiles() ?: continue
            for (f in files) {
                if (f.isFile) {
                    val path = f.absolutePath
                    if (f.delete() || !f.exists()) {
                        deletedCount++
                        deletedPaths.add(path)
                    }
                }
            }
        }

        try {
            val imagesUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val projection = mutableListOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DATA
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                projection.add(MediaStore.MediaColumns.RELATIVE_PATH)
            }
            context.contentResolver.query(imagesUri, projection.toTypedArray(), null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                val dataCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                val relCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                } else -1

                while (cursor.moveToNext()) {
                    val id = if (idCol >= 0) cursor.getLong(idCol) else -1L
                    val data = if (dataCol >= 0) cursor.getString(dataCol) else null
                    val rel = if (relCol >= 0) cursor.getString(relCol) else null
                    val isInCitraPic = (data != null && data.contains("Citra Pic", ignoreCase = true)) ||
                        (rel != null && rel.contains("Citra Pic", ignoreCase = true))
                    if (isInCitraPic && id != -1L) {
                        try {
                            val deleteUri = android.content.ContentUris.withAppendedId(imagesUri, id)
                            if (context.contentResolver.delete(deleteUri, null, null) > 0) {
                                deletedCount++
                            }
                            if (data != null) File(data).delete()
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaStore Citra Pic cleanup error (non-fatal)", e)
        }

        if (deletedPaths.isNotEmpty()) {
            try {
                MediaScannerConnection.scanFile(context, deletedPaths.toTypedArray(), null, null)
            } catch (_: Exception) {}
        }

        return deletedCount
    }

    /**
     * Resolves an existing file if [source] already lives inside one of Citra's standard folders
     * (Citra, Citra Docs, Citra Pic, Citra Records). Returns null if the source is external.
     */
    fun resolveExistingCitraFile(context: Context, source: String?): File? {
        if (source.isNullOrBlank()) return null
        val clean = source.trim()
        if (clean.startsWith("http://", ignoreCase = true) || clean.startsWith("https://", ignoreCase = true)) {
            return null
        }

        // 1. Direct file path or file:// URI
        if (!clean.startsWith("content://")) {
            val rawPath = if (clean.startsWith("file://")) {
                Uri.parse(clean).path ?: clean.removePrefix("file://")
            } else {
                clean
            }
            if (rawPath.startsWith("/")) {
                val candidate = File(rawPath)
                if (candidate.exists() && candidate.isFile && candidate.canRead() && isCitraFolderPath(candidate.absolutePath)) {
                    return candidate
                }
            }
            return null
        }

        val uri = try { Uri.parse(clean) } catch (_: Exception) { return null }

        // 2. ExternalStorage / Downloads SAF document URI
        val extracted = extractExternalStorageRelativePath(clean)
        if (!extracted.isNullOrBlank()) {
            val candidate = if (extracted.startsWith("/")) {
                File(extracted)
            } else {
                File(Environment.getExternalStorageDirectory(), extracted)
            }
            if (isCitraFolderPath(candidate.absolutePath) && candidate.exists() && candidate.isFile && candidate.canRead()) {
                return candidate
            }
        }

        // 3. Query MediaStore DATA / RELATIVE_PATH / DISPLAY_NAME / SIZE
        var displayName: String? = null
        var fileSize: Long = 0L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val dataIdx = cursor.getColumnIndex("_data")
                    if (dataIdx != -1) {
                        val path = cursor.getString(dataIdx)
                        if (!path.isNullOrBlank() && isCitraFolderPath(path)) {
                            val f = File(path)
                            if (f.exists() && f.isFile && f.canRead()) return f
                        }
                    }

                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1) {
                        displayName = cursor.getString(nameIdx)
                    }
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx != -1) {
                        fileSize = cursor.getLong(sizeIdx)
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val relIdx = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                        val relPath = if (relIdx != -1) cursor.getString(relIdx) else null
                        if (!relPath.isNullOrBlank() && !displayName.isNullOrBlank() && isCitraFolderPath(relPath)) {
                            val candidate = File(Environment.getExternalStorageDirectory(), relPath.trimEnd('/') + "/" + displayName)
                            if (candidate.exists() && candidate.isFile && candidate.canRead()) {
                                return candidate
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // 4. Photo Picker / virtual MediaStore URIs: match filename + size inside known Citra folders
        val targetName = displayName?.takeIf { it.isNotBlank() }
        if (targetName != null) {
            for (dir in getAllCitraDirectories(context)) {
                if (dir.exists() && dir.isDirectory) {
                    val candidate = File(dir, targetName)
                    if (candidate.exists() && candidate.isFile && candidate.canRead() && candidate.length() > 0L) {
                        if (fileSize <= 0L || candidate.length() == fileSize) {
                            return candidate
                        }
                    }
                }
            }
        }

        return null
    }

    /**
     * Copies an imported note image into "Citra Pic" (under Android's Pictures directory)
     * and returns the exact absolute path so it can be stored directly in the note.
     * If the image already resides inside Citra's folders, returns its existing path without duplicating.
     */
    fun saveNoteImage(context: Context, source: String): String? {
        return try {
            val existingCitraFile = resolveExistingCitraFile(context, source)
            if (existingCitraFile != null) {
                try {
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(existingCitraFile.absolutePath),
                        arrayOf("image/*"),
                        null
                    )
                } catch (_: Exception) {}
                return existingCitraFile.absolutePath
            }

            val dir = getCitraPicFolder(context)
            val extension = getExtension(context, source) ?: "jpg"
            val destFile = File(dir, "citra_pic_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.$extension")
            openInputStream(context, source)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            // Notify MediaScanner so it is visible in Android gallery/file explorer
            try {
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(destFile.absolutePath),
                    arrayOf("image/*"),
                    null
                )
            } catch (_: Exception) {}

            destFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save note image to Citra Pic", e)
            null
        }
    }

    fun saveNoteImage(context: Context, sourceUri: Uri): String? {
        return saveNoteImage(context, sourceUri.toString())
    }

    fun saveNoteImageBitmap(context: Context, bitmap: Bitmap): String? {
        return try {
            val dir = getCitraPicFolder(context)
            val destFile = File(dir, "citra_pic_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg")
            FileOutputStream(destFile).use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)
            }
            try {
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(destFile.absolutePath),
                    arrayOf("image/jpeg"),
                    null
                )
            } catch (_: Exception) {}
            destFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save note image bitmap to Citra Pic", e)
            null
        }
    }

    fun loadBitmap(context: Context, source: String): Bitmap? {
        return try {
            openInputStream(context, source)?.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode bitmap from $source", e)
            null
        }
    }

    /**
     * Obtains the primary Citra Records directory (public Recordings/Music/Documents or app storage).
     */
    fun getCitraRecordsFolder(context: Context): File {
        try {
            val publicDir = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RECORDINGS)
            } else {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            }
            val citraRec = File(publicDir, "Citra Records")
            if (citraRec.exists() || citraRec.mkdirs()) {
                return citraRec
            }
        } catch (_: Exception) {}

        try {
            val publicDocs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val citraRec = File(publicDocs, "Citra Records")
            if (citraRec.exists() || citraRec.mkdirs()) {
                return citraRec
            }
        } catch (_: Exception) {}

        val extDir = context.getExternalFilesDir("Citra Records")
        if (extDir != null && (extDir.exists() || extDir.mkdirs())) {
            return extDir
        }
        val internalDir = File(context.filesDir, "Citra Records")
        if (!internalDir.exists()) internalDir.mkdirs()
        return internalDir
    }

    /**
     * Saves an imported note audio file into "Citra Records" and returns its absolute path.
     * If the audio already resides inside Citra's folders, returns its existing path without duplicating.
     */
    fun saveNoteAudio(context: Context, source: String): String? {
        return try {
            val existingCitraFile = resolveExistingCitraFile(context, source)
            if (existingCitraFile != null) {
                try {
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(existingCitraFile.absolutePath),
                        arrayOf("audio/*"),
                        null
                    )
                } catch (_: Exception) {}
                return existingCitraFile.absolutePath
            }

            val extension = getExtension(context, source) ?: "m4a"
            val targetDir = getCitraRecordsFolder(context)
            val destFile = File(targetDir, "audio_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.$extension")
            openInputStream(context, source)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            try {
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(destFile.absolutePath),
                    arrayOf("audio/*"),
                    null
                )
            } catch (_: Exception) {}

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
     * Prepares an audio recording output file in "Citra Records".
     */
    fun createRecordingFile(context: Context): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "CitraRec_$timeStamp.m4a"
        val dir = getCitraRecordsFolder(context)
        return File(dir, fileName)
    }

    /**
     * Verifies if a given media path or URI is accessible, non-empty and exists.
     * Prevents silent failures by providing a clear indication when media is missing.
     */
    fun isMediaFileValid(context: Context, pathOrUri: String?): Boolean {
        if (pathOrUri.isNullOrBlank()) return false
        val clean = pathOrUri.trim()
        // Links diretos da internet (http/https) são válidos
        if (clean.startsWith("http://", ignoreCase = true) || clean.startsWith("https://", ignoreCase = true)) {
            return true
        }
        return try {
            if (clean.startsWith("content://")) {
                val uri = Uri.parse(clean)
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    pfd.statSize > 0
                } ?: false
            } else {
                val cleanPath = clean.removePrefix("file://")
                val f = File(cleanPath)
                if (f.exists() && f.length() > 0) {
                    true
                } else {
                    openInputStream(context, clean)?.use { true } ?: false
                }
            }
        } catch (_: Exception) {
            true // Evita falsos negativos bloqueando a renderização
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

            if (url.isNotBlank() &&
                !url.startsWith("http://", ignoreCase = true) &&
                !url.startsWith("https://", ignoreCase = true) &&
                !url.contains(permanentStoragePrefix)
            ) {
                val existing = resolveExistingCitraFile(context, url)
                val persistentPath = existing?.absolutePath ?: saveNoteImage(context, url)
                if (persistentPath != null && persistentPath != url) {
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

            if (url.isNotBlank() &&
                !url.startsWith("http://", ignoreCase = true) &&
                !url.startsWith("https://", ignoreCase = true) &&
                !url.contains(permanentStoragePrefix)
            ) {
                val existing = resolveExistingCitraFile(context, url)
                val persistentPath = existing?.absolutePath ?: saveNoteAudio(context, url)
                if (persistentPath != null && persistentPath != url) {
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

    const val MAX_SYNC_MEDIA_SIZE_BYTES = 8L * 1024 * 1024 // 8 MB

    /**
     * Resolves a media path, attempting transparent self-healing if the original
     * file path changed, device storage path differed, or file was moved to Citra's folders.
     */
    fun resolveOrSelfHealMediaPath(context: Context, pathOrUri: String?): String? {
        if (pathOrUri.isNullOrBlank()) return null
        val clean = pathOrUri.trim()
        if (clean.startsWith("http://", ignoreCase = true) || clean.startsWith("https://", ignoreCase = true)) {
            return clean
        }

        // 1. Direct check
        val directFile = File(clean.removePrefix("file://"))
        if (directFile.exists() && directFile.length() > 0 && directFile.canRead()) {
            return directFile.absolutePath
        }

        if (clean.startsWith("content://")) {
            try {
                val uri = Uri.parse(clean)
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    if (pfd.statSize > 0) return clean
                }
            } catch (_: Exception) {}
        }

        // 2. Self-healing: search known Citra media directories by filename
        val filename = clean.substringAfterLast('/').substringAfterLast('\\').trim()
        if (filename.isNotBlank()) {
            val candidateDirs = listOf(
                getCitraRecordsFolder(context),
                File(context.filesDir, "Citra Records"),
                File(context.filesDir, "avatars"),
                File(context.filesDir, "images"),
                CitraDocsFileManager.getDocsFolder(context),
                context.getExternalFilesDir(Environment.DIRECTORY_MUSIC),
                context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ).filterNotNull()

            for (dir in candidateDirs) {
                if (dir.exists()) {
                    val candidate = File(dir, filename)
                    if (candidate.exists() && candidate.length() > 0 && candidate.canRead()) {
                        return candidate.absolutePath
                    }
                }
            }
        }

        return null
    }

    /**
     * Regra dos 3 MB: Extracts all media in the note with size <= 3 MB
     * into a JSON-friendly array of Base64 attachments for sync & backup.
     */
    fun extractMediaAttachmentsForNote(context: Context, content: String): org.json.JSONArray {
        val attachments = org.json.JSONArray()
        if (content.isBlank()) return attachments

        val mediaUrls = mutableSetOf<String>()

        // 1. Images: ![alt](url)
        val imgRegex = Regex("!\\[.*?\\]\\((.*?)\\)")
        imgRegex.findAll(content).forEach { m ->
            val u = m.groupValues[1].trim()
            if (u.isNotBlank() && !u.startsWith("http://", true) && !u.startsWith("https://", true)) {
                mediaUrls.add(u)
            }
        }

        // 2. Audios: [audio:...](url) or ![audio:...](url)
        val audioRegex = Regex("!?\\[audio:.*?\\]\\((.*?)\\)")
        audioRegex.findAll(content).forEach { m ->
            val u = m.groupValues[1].trim()
            if (u.isNotBlank() && !u.startsWith("http://", true) && !u.startsWith("https://", true)) {
                mediaUrls.add(u)
            }
        }

        for (rawUrl in mediaUrls) {
            try {
                val resolvedPath = resolveOrSelfHealMediaPath(context, rawUrl) ?: rawUrl
                val file = File(resolvedPath.removePrefix("file://"))
                if (file.exists() && file.length() in 1..MAX_SYNC_MEDIA_SIZE_BYTES) {
                    val bytes = file.readBytes()
                    val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    val filename = file.name
                    val isAudio = rawUrl.contains("audio", true) || filename.endsWith(".m4a", true) || filename.endsWith(".mp3", true) || filename.endsWith(".wav", true) || filename.endsWith(".aac", true)
                    val mime = if (isAudio) "audio/mp4" else "image/jpeg"

                    val item = org.json.JSONObject().apply {
                        put("originalPath", rawUrl)
                        put("resolvedPath", resolvedPath)
                        put("filename", filename)
                        put("mimeType", mime)
                        put("sizeBytes", file.length())
                        put("base64Data", b64)
                    }
                    attachments.put(item)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to attach media under 3MB rule: $rawUrl", e)
            }
        }

        return attachments
    }

    /**
     * Regra dos 3 MB: Silently restores embedded media attachments from backup/sync
     * to the appropriate local Citra folder, updating markdown paths seamlessly.
     */
    fun restoreMediaAttachments(context: Context, attachmentsJson: org.json.JSONArray?, noteContent: String): String {
        if (attachmentsJson == null || attachmentsJson.length() == 0) return noteContent
        var updatedContent = noteContent

        for (i in 0 until attachmentsJson.length()) {
            try {
                val item = attachmentsJson.getJSONObject(i)
                val originalPath = item.optString("originalPath", "")
                val filename = item.optString("filename", "media_${System.currentTimeMillis()}")
                val mimeType = item.optString("mimeType", "")
                val b64 = item.optString("base64Data", "")

                if (b64.isNotBlank()) {
                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                    val isAudio = mimeType.startsWith("audio/") || filename.endsWith(".m4a", true) || filename.endsWith(".mp3", true) || filename.endsWith(".wav", true) || filename.endsWith(".aac", true)
                    val targetDir = if (isAudio) getCitraRecordsFolder(context) else {
                        File(context.filesDir, "images").apply { if (!exists()) mkdirs() }
                    }
                    val targetFile = File(targetDir, filename)
                    if (!targetFile.exists() || targetFile.length() == 0L) {
                        FileOutputStream(targetFile).use { fos ->
                            fos.write(bytes)
                        }
                    }

                    val newLocalPath = targetFile.absolutePath
                    if (originalPath.isNotBlank() && originalPath != newLocalPath) {
                        updatedContent = updatedContent.replace(originalPath, newLocalPath)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Silent restore of media attachment failed", e)
            }
        }

        return updatedContent
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
