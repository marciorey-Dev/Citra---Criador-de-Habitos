package com.example.data.local

import androidx.room.*
import com.example.data.model.NoteVersion
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteVersionDao {
    @Query("SELECT * FROM note_versions WHERE noteId = :noteId ORDER BY versionNumber DESC")
    fun getVersionsForNote(noteId: Long): Flow<List<NoteVersion>>

    @Query("SELECT * FROM note_versions WHERE noteId = :noteId ORDER BY versionNumber DESC")
    suspend fun getVersionsForNoteSync(noteId: Long): List<NoteVersion>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVersion(version: NoteVersion): Long

    @Query("DELETE FROM note_versions WHERE noteId = :noteId")
    suspend fun deleteVersionsForNote(noteId: Long)
}
