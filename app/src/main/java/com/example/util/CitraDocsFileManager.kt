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
     * Embeds a clean, standard identifier comment in markdown content.
     */
    fun buildContentWithFrontmatter(uuid: String, content: String): String {
        val clean = stripFrontmatter(content)
        return "<!-- citra_id: $uuid -->\n$clean"
    }

    /**
     * Extracts UUID from markdown comments or YAML frontmatter.
     */
    fun extractUuidFromMarkdown(content: String): String? {
        val commentRegex = Regex("<!--\\s*citra_id:\\s*([a-zA-Z0-9_-]+)\\s*-->")
        val match = commentRegex.find(content)
        if (match != null) return match.groupValues[1].trim()

        val yamlRegex = Regex("(?s)^---\\s*\\n.*?citra_id:\\s*([a-zA-Z0-9_-]+).*?\\n---\\s*\\n?")
        val yamlMatch = yamlRegex.find(content)
        if (yamlMatch != null) return yamlMatch.groupValues[1].trim()

        return null
    }

    /**
     * Strips frontmatter/citra_id tags so text remains clean for UI editors.
     */
    fun stripFrontmatter(content: String): String {
        var result = content.replace(Regex("<!--\\s*citra_id:\\s*[a-zA-Z0-9_-]+\\s*-->\\s*\\n?"), "")
        result = result.replace(Regex("(?s)^---\\s*\\n.*?citra_id:\\s*[a-zA-Z0-9_-]+.*?\\n---\\s*\\n?"), "")
        return result.trimStart()
    }

    /**
     * Writes or updates a .md file directly in Documents/Citra Docs/.
     * If oldTitle is provided and different, safely removes the old file to prevent duplicates.
     */
    fun saveNoteMarkdown(
        context: Context,
        title: String,
        content: String,
        uuid: String? = null,
        oldTitle: String? = null
    ): File? {
        return try {
            val folder = getDocsFolder(context)

            // If title was renamed, remove old file so it won't be re-imported as a duplicate
            if (oldTitle != null && oldTitle.isNotBlank() && !oldTitle.equals(title, ignoreCase = true)) {
                deleteNoteMarkdown(context, oldTitle)
            }

            val filename = "${sanitizeFilename(title)}.md"
            val file = File(folder, filename)

            val textToWrite = if (uuid != null && uuid.isNotBlank()) {
                buildContentWithFrontmatter(uuid, content)
            } else {
                content
            }

            val tempFile = File(folder, ".${sanitizeFilename(title)}_${System.currentTimeMillis()}.tmp")
            FileOutputStream(tempFile).use { fos ->
                fos.write(textToWrite.toByteArray(StandardCharsets.UTF_8))
                fos.flush()
            }
            if (!tempFile.renameTo(file)) {
                tempFile.copyTo(file, overwrite = true)
                tempFile.delete()
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
     * Returns all existing Citra Docs directories across public, external, and internal app storage.
     */
    fun getAllDocsFolders(context: Context): List<File> {
        val list = mutableListOf<File>()
        try {
            val pub = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), FOLDER_NAME)
            list.add(pub)
        } catch (_: Exception) {}
        try {
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)?.let { ext ->
                list.add(File(ext, FOLDER_NAME))
            }
        } catch (_: Exception) {}
        try {
            list.add(File(context.filesDir, FOLDER_NAME))
        } catch (_: Exception) {}
        return list.distinctBy { it.absolutePath }
    }

    /**
     * Deletes a .md file from Documents/Citra Docs/ across all storage targets and MediaStore.
     */
    fun deleteNoteMarkdown(context: Context, title: String, uuid: String? = null): Boolean {
        var deletedAny = false
        val targetFilenames = setOf(
            "${sanitizeFilename(title)}.md".lowercase(),
            "$title.md".lowercase(),
            "${title.trim()}.md".lowercase()
        )

        // 1. Direct File Deletion across all candidate directories
        val folders = getAllDocsFolders(context)
        for (folder in folders) {
            if (!folder.exists()) continue
            val allFiles = folder.listFiles { f -> f.isFile && f.name.endsWith(".md", ignoreCase = true) } ?: continue
            for (f in allFiles) {
                var shouldDelete = targetFilenames.contains(f.name.lowercase())
                if (!shouldDelete && uuid != null && uuid.isNotBlank()) {
                    try {
                        val header = f.bufferedReader().use { reader ->
                            val sb = StringBuilder()
                            var line = reader.readLine()
                            var linesRead = 0
                            while (line != null && linesRead < 15) {
                                sb.append(line).append("\n")
                                line = reader.readLine()
                                linesRead++
                            }
                            sb.toString()
                        }
                        if (header.contains(uuid)) {
                            shouldDelete = true
                        }
                    } catch (_: Exception) {}
                }

                if (shouldDelete) {
                    val filePath = f.absolutePath
                    val ok = f.delete()
                    if (ok || !f.exists()) {
                        deletedAny = true
                        try {
                            MediaScannerConnection.scanFile(
                                context,
                                arrayOf(filePath),
                                null,
                                null
                            )
                        } catch (_: Exception) {}
                    }
                }
            }
        }

        // 2. Android MediaStore ContentResolver Deletion (Scoped Storage on API 29+)
        try {
            val externalUri = android.provider.MediaStore.Files.getContentUri("external")
            val selection = "${android.provider.MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? OR ${android.provider.MediaStore.MediaColumns.DATA} LIKE ?"
            val args = arrayOf("%.md", "%Citra Docs%")
            val projection = arrayOf(
                android.provider.MediaStore.MediaColumns._ID,
                android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
                android.provider.MediaStore.MediaColumns.DATA
            )
            context.contentResolver.query(externalUri, projection, selection, args, null)?.use { cursor ->
                val idCol = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DATA)
                while (cursor.moveToNext()) {
                    val id = if (idCol >= 0) cursor.getLong(idCol) else -1L
                    val name = if (nameCol >= 0) cursor.getString(nameCol) else null
                    val data = if (dataCol >= 0) cursor.getString(dataCol) else null

                    var match = false
                    if (name != null && targetFilenames.contains(name.lowercase())) {
                        match = true
                    }
                    if (!match && data != null && uuid != null && uuid.isNotBlank()) {
                        try {
                            val f = File(data)
                            if (f.exists() && f.canRead()) {
                                val firstLine = f.bufferedReader().use { it.readLine() ?: "" }
                                if (firstLine.contains(uuid)) match = true
                            }
                        } catch (_: Exception) {}
                    }

                    if (match && id != -1L) {
                        try {
                            val deleteUri = android.content.ContentUris.withAppendedId(externalUri, id)
                            context.contentResolver.delete(deleteUri, null, null)
                            deletedAny = true
                            if (data != null) {
                                File(data).delete()
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaStore deletion error (non-fatal)", e)
        }

        return deletedAny
    }

    /**
     * Lists all .md files present in Documents/Citra Docs/
     */
    fun listAllMarkdownNotes(context: Context): List<File> {
        return try {
            val folder = getDocsFolder(context)
            folder.listFiles { file ->
                file.isFile && file.name.endsWith(".md", ignoreCase = true)
            }?.toList() ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list markdown notes", e)
            emptyList()
        }
    }

    /**
     * Converts various supported file formats (HTML, TXT, JSON, CITRADOC, TEX, MD) to clean Markdown.
     */
    fun convertContentToMarkdown(filename: String, rawText: String): String {
        val lower = filename.lowercase()
        return when {
            lower.endsWith(".html") || lower.endsWith(".htm") || rawText.trim().startsWith("<!DOCTYPE html", ignoreCase = true) || rawText.trim().startsWith("<html", ignoreCase = true) -> {
                convertHtmlToMarkdown(rawText)
            }
            lower.endsWith(".csv") -> {
                convertCsvToMarkdown(rawText)
            }
            lower.endsWith(".json") || lower.endsWith(".citradoc") -> {
                convertJsonToMarkdown(rawText)
            }
            lower.endsWith(".tex") -> {
                convertLatexToMarkdown(rawText)
            }
            lower.endsWith(".xml") -> {
                "```xml\n$rawText\n```"
            }
            else -> {
                // .md, .markdown, .txt, or other text formats
                rawText
            }
        }
    }

    private fun convertHtmlToMarkdown(html: String): String {
        var text = html
        // Remove scripts and styles
        text = text.replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
        text = text.replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")

        // Headers
        text = text.replace(Regex("<h1[^>]*>([\\s\\S]*?)</h1>", RegexOption.IGNORE_CASE)) { "# ${it.groupValues[1].trim()}\n\n" }
        text = text.replace(Regex("<h2[^>]*>([\\s\\S]*?)</h2>", RegexOption.IGNORE_CASE)) { "## ${it.groupValues[1].trim()}\n\n" }
        text = text.replace(Regex("<h3[^>]*>([\\s\\S]*?)</h3>", RegexOption.IGNORE_CASE)) { "### ${it.groupValues[1].trim()}\n\n" }
        text = text.replace(Regex("<h4[^>]*>([\\s\\S]*?)</h4>", RegexOption.IGNORE_CASE)) { "#### ${it.groupValues[1].trim()}\n\n" }

        // Bold & Italic
        text = text.replace(Regex("<(strong|b)[^>]*>([\\s\\S]*?)</\\1>", RegexOption.IGNORE_CASE)) { "**${it.groupValues[2]}**" }
        text = text.replace(Regex("<(em|i)[^>]*>([\\s\\S]*?)</\\1>", RegexOption.IGNORE_CASE)) { "*${it.groupValues[2]}*" }

        // Code
        text = text.replace(Regex("<pre[^>]*><code[^>]*>([\\s\\S]*?)</code></pre>", RegexOption.IGNORE_CASE)) { "\n```\n${it.groupValues[1].trim()}\n```\n" }
        text = text.replace(Regex("<code[^>]*>([\\s\\S]*?)</code>", RegexOption.IGNORE_CASE)) { "`${it.groupValues[1]}`" }

        // Lists
        text = text.replace(Regex("<li[^>]*>([\\s\\S]*?)</li>", RegexOption.IGNORE_CASE)) { "- ${it.groupValues[1].trim()}\n" }

        // Paragraphs & Line breaks
        text = text.replace(Regex("<p[^>]*>([\\s\\S]*?)</p>", RegexOption.IGNORE_CASE)) { "${it.groupValues[1].trim()}\n\n" }
        text = text.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        text = text.replace(Regex("<hr\\s*/?>", RegexOption.IGNORE_CASE), "\n---\n")

        // Blockquotes
        text = text.replace(Regex("<blockquote[^>]*>([\\s\\S]*?)</blockquote>", RegexOption.IGNORE_CASE)) { "> ${it.groupValues[1].trim()}\n\n" }

        // Strip remaining HTML tags
        text = text.replace(Regex("<[^>]+>"), "")

        // Unescape standard HTML entities
        text = text.replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")

        return text.trim()
    }

    private fun convertCsvToMarkdown(csv: String): String {
        val lines = csv.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return csv
        val sb = StringBuilder()
        val firstLine = lines.first()
        val delimiter = if (firstLine.contains(";")) ";" else ","
        val headers = firstLine.split(delimiter).map { it.trim().removeSurrounding("\"") }
        sb.append("| ${headers.joinToString(" | ")} |\n")
        sb.append("| ${headers.joinToString(" | ") { "---" }} |\n")
        for (i in 1 until lines.size) {
            val cols = lines[i].split(delimiter).map { it.trim().removeSurrounding("\"") }
            sb.append("| ${cols.joinToString(" | ")} |\n")
        }
        return sb.toString().trim()
    }

    private fun convertJsonToMarkdown(json: String): String {
        val trimmed = json.trim()
        if (trimmed.startsWith("[")) {
            return try {
                val array = org.json.JSONArray(trimmed)
                val sb = StringBuilder()
                for (i in 0 until array.length()) {
                    val item = array.opt(i)
                    if (item is org.json.JSONObject) {
                        val title = item.optString("title", "Item ${i + 1}")
                        val content = item.optString("content", item.optString("body", item.toString(2)))
                        sb.append("### $title\n\n$content\n\n---\n\n")
                    } else {
                        sb.append("- $item\n")
                    }
                }
                sb.toString().trim()
            } catch (_: Exception) {
                "```json\n$json\n```"
            }
        }
        return try {
            val jsonObject = org.json.JSONObject(json)
            if (jsonObject.has("content")) {
                jsonObject.getString("content")
            } else if (jsonObject.has("body")) {
                jsonObject.getString("body")
            } else if (jsonObject.has("text")) {
                jsonObject.getString("text")
            } else {
                val sb = StringBuilder()
                val keys = jsonObject.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = jsonObject.opt(key)
                    sb.append("**$key:** $value\n\n")
                }
                sb.toString().trim()
            }
        } catch (_: Exception) {
            "```json\n$json\n```"
        }
    }

    private fun convertLatexToMarkdown(tex: String): String {
        var text = tex
        text = text.replace(Regex("\\\\section\\{([^}]+)\\}")) { "# ${it.groupValues[1]}\n\n" }
        text = text.replace(Regex("\\\\subsection\\{([^}]+)\\}")) { "## ${it.groupValues[1]}\n\n" }
        text = text.replace(Regex("\\\\subsubsection\\{([^}]+)\\}")) { "### ${it.groupValues[1]}\n\n" }
        text = text.replace(Regex("\\\\textbf\\{([^}]+)\\}")) { "**${it.groupValues[1]}**" }
        text = text.replace(Regex("\\\\textit\\{([^}]+)\\}")) { "*${it.groupValues[1]}*" }
        text = text.replace(Regex("\\\\item\\s+")) { "- " }
        text = text.replace(Regex("\\\\begin\\{itemize\\}|\\\\end\\{itemize\\}|\\\\begin\\{enumerate\\}|\\\\end\\{enumerate\\}"), "")
        return text.trim()
    }
}
