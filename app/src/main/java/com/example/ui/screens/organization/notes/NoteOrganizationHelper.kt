package com.example.ui.screens.organization.notes

import com.example.data.model.Note
import com.example.data.model.NoteDocumentType
import com.example.data.model.resolvedDocumentType

enum class NoteDocumentFilter(val label: String) {
    ALL("Todos"),
    NOTES("Notas"),
    PDF("PDFs"),
    DOCX(".docx");

    companion object {
        fun fromName(name: String?): NoteDocumentFilter {
            return entries.firstOrNull { it.name == name } ?: ALL
        }
    }
}

fun filterNotesByDocumentType(
    notes: List<Note>,
    filter: NoteDocumentFilter
): List<Note> {
    return when (filter) {
        NoteDocumentFilter.ALL -> notes
        NoteDocumentFilter.NOTES -> notes.filter { it.resolvedDocumentType == NoteDocumentType.MARKDOWN }
        NoteDocumentFilter.PDF -> notes.filter { it.resolvedDocumentType == NoteDocumentType.PDF }
        NoteDocumentFilter.DOCX -> notes.filter { it.resolvedDocumentType == NoteDocumentType.DOCX }
    }
}

enum class NoteSortOption(
    val label: String,
    val shortLabel: String
) {
    LAST_UPDATED("Última Atualização", "Atualização"),
    CREATION_DATE("Data de Criação", "Criação"),
    ALPHABETICAL("Alfabética", "Alfabética"),
    CATEGORY("Categoria", "Categoria"),
    SIZE("Tamanho", "Tamanho");

    companion object {
        fun fromName(name: String?): NoteSortOption {
            return entries.firstOrNull { it.name == name } ?: LAST_UPDATED
        }
    }
}

enum class NoteLayoutMode(val label: String) {
    GRID_2("Grade"),
    GRID_3("Grade Pequena"),
    LIST("Lista"),
    STACKED_CATEGORIES("Pastas Empilhadas");

    companion object {
        fun fromName(name: String?): NoteLayoutMode {
            return entries.firstOrNull { it.name == name } ?: GRID_2
        }
    }
}

data class NoteCategoryGroup(
    val categoryName: String,
    val notes: List<Note>
)

/**
 * Sorts notes with pinned notes always prioritized at the top, followed by the primary
 * [sortOption] combined with the secondary [ascending] direction.
 *
 * Ascending semantics per criterion:
 * - LAST_UPDATED: oldest update -> newest update
 * - CREATION_DATE: oldest creation (lower id) -> newest creation (higher id)
 * - ALPHABETICAL: A -> Z
 * - CATEGORY: A -> Z (tie-broken by title A -> Z)
 * - SIZE: smallest content length -> largest content length
 *
 * Descending ([ascending] = false) reverses the primary criterion cleanly across all options.
 */
fun sortNotes(
    notes: List<Note>,
    sortOption: NoteSortOption,
    ascending: Boolean
): List<Note> {
    return notes.sortedWith(
        Comparator { a, b ->
            if (a.isPinned != b.isPinned) {
                return@Comparator if (a.isPinned) -1 else 1
            }

            val baseComparison = when (sortOption) {
                NoteSortOption.LAST_UPDATED -> {
                    val timeCmp = a.updatedAt.compareTo(b.updatedAt)
                    if (timeCmp != 0) timeCmp else a.id.compareTo(b.id)
                }
                NoteSortOption.CREATION_DATE -> a.id.compareTo(b.id)
                NoteSortOption.ALPHABETICAL -> {
                    val titleA = a.title.trim()
                    val titleB = b.title.trim()
                    val titleCmp = titleA.compareTo(titleB, ignoreCase = true)
                    if (titleCmp != 0) titleCmp else a.id.compareTo(b.id)
                }
                NoteSortOption.CATEGORY -> {
                    val catA = a.category.trim().ifBlank { "Geral" }
                    val catB = b.category.trim().ifBlank { "Geral" }
                    val catCmp = catA.compareTo(catB, ignoreCase = true)
                    if (catCmp != 0) {
                        catCmp
                    } else {
                        val titleCmp = a.title.trim().compareTo(b.title.trim(), ignoreCase = true)
                        if (titleCmp != 0) titleCmp else a.id.compareTo(b.id)
                    }
                }
                NoteSortOption.SIZE -> {
                    val sizeCmp = a.content.length.compareTo(b.content.length)
                    if (sizeCmp != 0) sizeCmp else a.id.compareTo(b.id)
                }
            }

            if (ascending) baseComparison else -baseComparison
        }
    )
}

/**
 * Groups already-sorted notes into compact category folders.
 * Intra-folder note order preserves [sortedNotes].
 * Folder order respects [NoteSortOption.CATEGORY] + [ascending] when sorting by category,
 * otherwise preserves insertion order determined by each folder's highest-priority note.
 */
fun groupNotesByCategory(
    sortedNotes: List<Note>,
    sortOption: NoteSortOption,
    ascending: Boolean,
    knownCategories: List<String> = emptyList()
): List<NoteCategoryGroup> {
    if (sortedNotes.isEmpty()) return emptyList()

    val canonicalByKey = knownCategories
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .associateBy { it.lowercase() }

    val buckets = linkedMapOf<String, MutableList<Note>>()
    val displayNames = mutableMapOf<String, String>()

    for (note in sortedNotes) {
        val rawCategory = note.category.trim().ifBlank { "Geral" }
        val key = rawCategory.lowercase()
        if (!displayNames.containsKey(key)) {
            displayNames[key] = canonicalByKey[key] ?: rawCategory
        }
        buckets.getOrPut(key) { mutableListOf() }.add(note)
    }

    val groups = buckets.entries.map { (key, groupNotes) ->
        NoteCategoryGroup(
            categoryName = displayNames[key] ?: "Geral",
            notes = groupNotes.toList()
        )
    }

    return if (sortOption == NoteSortOption.CATEGORY) {
        groups.sortedWith(
            Comparator { a, b ->
                val cmp = a.categoryName.compareTo(b.categoryName, ignoreCase = true)
                if (ascending) cmp else -cmp
            }
        )
    } else {
        groups
    }
}
