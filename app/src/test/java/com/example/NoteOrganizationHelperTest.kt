package com.example

import com.example.data.model.Note
import com.example.ui.screens.organization.notes.NoteLayoutMode
import com.example.ui.screens.organization.notes.NoteSortOption
import com.example.ui.screens.organization.notes.groupNotesByCategory
import com.example.ui.screens.organization.notes.sortNotes
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteOrganizationHelperTest {

    private val sampleNotes = listOf(
        Note(id = 1L, title = "Beta", content = "short", category = "Estudos", updatedAt = 100L, isPinned = false),
        Note(id = 2L, title = "Alpha", content = "a very long content body", category = "Trabalho", updatedAt = 300L, isPinned = false),
        Note(id = 3L, title = "Gamma", content = "medium len", category = "Criatividade", updatedAt = 200L, isPinned = false),
        Note(id = 4L, title = "Pinned Z", content = "pin", category = "Trabalho", updatedAt = 50L, isPinned = true)
    )

    @Test
    fun `pinned notes stay first regardless of primary sort or secondary direction`() {
        NoteSortOption.entries.forEach { option ->
            listOf(true, false).forEach { asc ->
                val sorted = sortNotes(sampleNotes, option, asc)
                assertEquals(
                    "Pinned note must be first for $option asc=$asc",
                    4L,
                    sorted.first().id
                )
            }
        }
    }

    @Test
    fun `last updated combines cleanly with ascending and descending`() {
        val descIds = sortNotes(sampleNotes, NoteSortOption.LAST_UPDATED, ascending = false).map { it.id }
        val ascIds = sortNotes(sampleNotes, NoteSortOption.LAST_UPDATED, ascending = true).map { it.id }

        assertEquals(listOf(4L, 2L, 3L, 1L), descIds)
        assertEquals(listOf(4L, 1L, 3L, 2L), ascIds)
    }

    @Test
    fun `creation date combines cleanly with ascending and descending`() {
        val descIds = sortNotes(sampleNotes, NoteSortOption.CREATION_DATE, ascending = false).map { it.id }
        val ascIds = sortNotes(sampleNotes, NoteSortOption.CREATION_DATE, ascending = true).map { it.id }

        assertEquals(listOf(4L, 3L, 2L, 1L), descIds)
        assertEquals(listOf(4L, 1L, 2L, 3L), ascIds)
    }

    @Test
    fun `alphabetical combines cleanly with ascending and descending`() {
        val ascTitles = sortNotes(sampleNotes, NoteSortOption.ALPHABETICAL, ascending = true).map { it.title }
        val descTitles = sortNotes(sampleNotes, NoteSortOption.ALPHABETICAL, ascending = false).map { it.title }

        assertEquals(listOf("Pinned Z", "Alpha", "Beta", "Gamma"), ascTitles)
        assertEquals(listOf("Pinned Z", "Gamma", "Beta", "Alpha"), descTitles)
    }

    @Test
    fun `category sort combines cleanly with ascending and descending`() {
        val ascCats = sortNotes(sampleNotes, NoteSortOption.CATEGORY, ascending = true).map { it.category }
        val descCats = sortNotes(sampleNotes, NoteSortOption.CATEGORY, ascending = false).map { it.category }

        assertEquals(listOf("Trabalho", "Criatividade", "Estudos", "Trabalho"), ascCats)
        assertEquals(listOf("Trabalho", "Trabalho", "Estudos", "Criatividade"), descCats)
    }

    @Test
    fun `size sort combines cleanly with ascending and descending`() {
        val ascIds = sortNotes(sampleNotes, NoteSortOption.SIZE, ascending = true).map { it.id }
        val descIds = sortNotes(sampleNotes, NoteSortOption.SIZE, ascending = false).map { it.id }

        assertEquals(listOf(4L, 1L, 3L, 2L), ascIds)
        assertEquals(listOf(4L, 2L, 3L, 1L), descIds)
    }

    @Test
    fun `groupNotesByCategory stacks notes cleanly by folder and respects category sort direction`() {
        val notes = listOf(
            Note(id = 1L, title = "N1", content = "c", category = "estudos", updatedAt = 100L),
            Note(id = 2L, title = "N2", content = "c", category = "Estudos", updatedAt = 200L),
            Note(id = 3L, title = "N3", content = "c", category = "", updatedAt = 300L),
            Note(id = 4L, title = "N4", content = "c", category = "Criatividade", updatedAt = 150L)
        )

        val sortedAscCat = sortNotes(notes, NoteSortOption.CATEGORY, ascending = true)
        val groupsAsc = groupNotesByCategory(
            sortedNotes = sortedAscCat,
            sortOption = NoteSortOption.CATEGORY,
            ascending = true,
            knownCategories = listOf("Estudos", "Criatividade", "Geral")
        )

        assertEquals(listOf("Criatividade", "Estudos", "Geral"), groupsAsc.map { it.categoryName })
        assertEquals(listOf(1L, 2L), groupsAsc[1].notes.map { it.id })

        val sortedDescCat = sortNotes(notes, NoteSortOption.CATEGORY, ascending = false)
        val groupsDesc = groupNotesByCategory(
            sortedNotes = sortedDescCat,
            sortOption = NoteSortOption.CATEGORY,
            ascending = false,
            knownCategories = listOf("Estudos", "Criatividade", "Geral")
        )
        assertEquals(listOf("Geral", "Estudos", "Criatividade"), groupsDesc.map { it.categoryName })
    }

    @Test
    fun `enum lookup helpers fall back safely`() {
        assertEquals(NoteSortOption.LAST_UPDATED, NoteSortOption.fromName("UNKNOWN"))
        assertEquals(NoteLayoutMode.STACKED_CATEGORIES, NoteLayoutMode.fromName("STACKED_CATEGORIES"))
        assertEquals(NoteLayoutMode.GRID_2, NoteLayoutMode.fromName(null))
    }
}
