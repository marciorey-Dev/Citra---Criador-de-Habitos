package com.example.data.local

import androidx.room.*
import com.example.data.model.Note
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE isDeleted = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun getAllNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE isDeleted = 1 ORDER BY deletedAt DESC")
    fun getTrashNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE isDeleted = 0 AND (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%') ORDER BY isPinned DESC, updatedAt DESC")
    fun searchNotes(query: String): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE isDeleted = 0 AND category = :category ORDER BY isPinned DESC, updatedAt DESC")
    fun getNotesByCategory(category: String): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE isDeleted = 0 AND associatedDate = :dateStr ORDER BY isPinned DESC, updatedAt DESC")
    fun getNotesByDate(dateStr: String): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getNoteById(id: Long): Note?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: Note): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<Note>)

    @Update
    suspend fun updateNote(note: Note)

    @Query("UPDATE notes SET isDeleted = 1, deletedAt = :timestamp WHERE id = :id")
    suspend fun moveToTrash(id: Long, timestamp: Long)

    @Query("UPDATE notes SET isDeleted = 0, deletedAt = NULL WHERE id = :id")
    suspend fun restoreFromTrash(id: Long)

    @Delete
    suspend fun deleteNote(note: Note)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun permanentlyDelete(id: Long)

    @Query("DELETE FROM notes WHERE isDeleted = 1 AND deletedAt < :cutoffTimestamp")
    suspend fun cleanOldTrash(cutoffTimestamp: Long)

    @Query("SELECT * FROM notes WHERE isDeleted = 0")
    suspend fun getAllNotesSync(): List<Note>

    @Query("DELETE FROM notes")
    suspend fun deleteAllNotes()
}
