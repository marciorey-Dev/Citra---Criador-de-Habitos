package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class NoteDocumentType(val id: String, val label: String) {
    MARKDOWN("MARKDOWN", "Nota"),
    PDF("PDF", "PDF"),
    DOCX("DOCX", "Word (.docx)");

    companion object {
        fun fromId(id: String?): NoteDocumentType {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: MARKDOWN
        }
    }
}

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uuid: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val content: String,
    val category: String = "Geral",
    val iconEmoji: String = "📝",
    val associatedDate: String? = null, // "YYYY-MM-DD"
    val isPinned: Boolean = false,
    val backgroundImageUri: String? = null,
    val backgroundBlur: Float = 0.5f,
    val backgroundBrightness: Float = 0.5f,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val isArchived: Boolean = false,
    val noteColor: String? = null,
    val documentType: String = NoteDocumentType.MARKDOWN.id,
    val externalUri: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

val Note.resolvedDocumentType: NoteDocumentType
    get() = NoteDocumentType.fromId(documentType)

val Note.isExternalDocument: Boolean
    get() = resolvedDocumentType != NoteDocumentType.MARKDOWN

val Note.isPdfDocument: Boolean
    get() = resolvedDocumentType == NoteDocumentType.PDF

val Note.isDocxDocument: Boolean
    get() = resolvedDocumentType == NoteDocumentType.DOCX

fun Note.isReadyForMainList(now: Long = System.currentTimeMillis()): Boolean {
    val dateStr = associatedDate ?: return true
    if (dateStr.isBlank()) return true

    val maxLeadTimeMs = 72L * 60L * 60L * 1000L
    val targetTs = try {
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).parse(dateStr)?.time
    } catch (_: Exception) { null } ?: return true

    val diff = targetTs - now
    return diff <= maxLeadTimeMs
}
