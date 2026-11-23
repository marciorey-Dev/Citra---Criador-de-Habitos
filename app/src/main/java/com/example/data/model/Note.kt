package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class NoteDocumentType(val id: String, val label: String) {
    MARKDOWN("MARKDOWN", "Nota"),
    PDF("PDF", "PDF"),
    DOCX("DOCX", "Word (.docx)"),
    EPUB("EPUB", "EPUB"),
    MOBI("MOBI", "MOBI"),
    CBZ("CBZ", "CBZ"),
    FB2("FB2", "FB2");

    val isEbookOrComic: Boolean
        get() = this == EPUB || this == MOBI || this == CBZ || this == FB2

    val fileExtension: String
        get() = when (this) {
            MARKDOWN -> "md"
            PDF -> "pdf"
            DOCX -> "docx"
            EPUB -> "epub"
            MOBI -> "mobi"
            CBZ -> "cbz"
            FB2 -> "fb2"
        }

    val mimeType: String
        get() = when (this) {
            MARKDOWN -> "text/markdown"
            PDF -> "application/pdf"
            DOCX -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            EPUB -> "application/epub+zip"
            MOBI -> "application/x-mobipocket-ebook"
            CBZ -> "application/vnd.comicbook+zip"
            FB2 -> "application/x-fictionbook+xml"
        }

    companion object {
        fun fromId(id: String?): NoteDocumentType {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: MARKDOWN
        }

        val EBOOK_COMIC_TYPES: Set<NoteDocumentType>
            get() = setOf(EPUB, MOBI, CBZ, FB2)
    }
}

@Entity(
    tableName = "notes",
    indices = [
        Index(value = ["isDeleted"]),
        Index(value = ["isArchived"]),
        Index(value = ["uuid"]),
        Index(value = ["category"]),
        Index(value = ["associatedDate"]),
        Index(value = ["updatedAt"])
    ]
)
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

val Note.isEbookOrComicDocument: Boolean
    get() = resolvedDocumentType.isEbookOrComic

private val noteIsoDateFormat = object : ThreadLocal<java.text.SimpleDateFormat>() {
    override fun initialValue(): java.text.SimpleDateFormat =
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
}

fun Note.isReadyForMainList(now: Long = System.currentTimeMillis()): Boolean {
    val dateStr = associatedDate ?: return true
    if (dateStr.isBlank()) return true

    val maxLeadTimeMs = 72L * 60L * 60L * 1000L
    val targetTs = try {
        noteIsoDateFormat.get()?.parse(dateStr)?.time
    } catch (_: Exception) { null } ?: return true

    val diff = targetTs - now
    return diff <= maxLeadTimeMs
}
