package com.example.util

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets

/**
 * Manages transparent synchronization of Citra notes with physical .md files
 * located in the user-accessible folder: Documents/Citra Docs/
 *
 * This allows notes to be opened, edited, or synced directly with Obsidian,
 * computer file managers, and other external Markdown editors.
 */
object CitraDocsFileManager {

    private const val TAG = "CitraDocsFileManager"
    private const val FOLDER_NAME = "Citra Docs"

    /**
     * Obtains the primary Documents/Citra Docs directory with reliable fallbacks.
     */
    fun getDocsFolder(context: Context): File {
        // 1. Try public Documents directory
        try {
            val publicDocs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val citraDir = File(publicDocs, FOLDER_NAME)
            if (citraDir.exists() || citraDir.mkdirs()) {
                return citraDir
            }
        } catch (e: Exception) {
            Log.w(TAG, "Public Documents directory inaccessible, trying external app docs", e)
        }

        // 2. Fallback to external files Documents folder
        try {
            val extDocs = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            if (extDocs != null) {
                val citraDir = File(extDocs, FOLDER_NAME)
                if (citraDir.exists() || citraDir.mkdirs()) {
                    return citraDir
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "External app documents directory failed, falling back to internal storage", e)
        }

        // 3. Fallback to app private filesDir
        val internalDir = File(context.filesDir, FOLDER_NAME)
        if (!internalDir.exists()) {
            internalDir.mkdirs()
        }
        return internalDir
    }

    /**
     * Sanitizes title into a safe filename.
     */
    fun sanitizeFilename(title: String): String {
        val sanitized = title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .take(80)
        return if (sanitized.isBlank()) "Nota_Sem_Titulo" else sanitized
    }

    /**
     * Writes or updates a .md file directly in Documents/Citra Docs/
     */
    fun saveNoteMarkdown(context: Context, title: String, content: String): File? {
        return try {
            val folder = getDocsFolder(context)
            val filename = "${sanitizeFilename(title)}.md"
            val file = File(folder, filename)

            FileOutputStream(file).use { fos ->
                fos.write(content.toByteArray(StandardCharsets.UTF_8))
            }

            // Notify Android media scanner so the file is visible over USB MTP and file managers
            try {
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(file.absolutePath),
                    arrayOf("text/markdown", "text/plain"),
                    null
                )
            } catch (_: Exception) {}

            file
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save note markdown file for: $title", e)
            null
        }
    }

    /**
     * Reads a .md file content as a UTF-8 string.
     */
    fun readNoteMarkdown(file: File): String? {
        return try {
            file.readText(StandardCharsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read markdown file: ${file.name}", e)
            null
        }
    }

    /**
     * Deletes a .md file from Documents/Citra Docs/
     */
    fun deleteNoteMarkdown(context: Context, title: String): Boolean {
        return try {
            val folder = getDocsFolder(context)
            val file = File(folder, "${sanitizeFilename(title)}.md")
            if (file.exists()) {
                val deleted = file.delete()
                try {
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(file.absolutePath),
                        null,
                        null
                    )
                } catch (_: Exception) {}
                deleted
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete markdown file for: $title", e)
            false
        }
    }

    /**
     * Lists all .md files present in Documents/Citra Docs/
     */
    fun listAllMarkdownNotes(context: Context): List<File> {
        return try {
            val folder = getDocsFolder(context)
            folder.listFiles { file -> file.isFile && (file.name.endsWith(".md", ignoreCase = true) || file.name.endsWith(".markdown", ignoreCase = true)) }
                ?.toList()
                ?.sortedByDescending { it.lastModified() }
                ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list markdown files", e)
            emptyList()
        }
    }
}
