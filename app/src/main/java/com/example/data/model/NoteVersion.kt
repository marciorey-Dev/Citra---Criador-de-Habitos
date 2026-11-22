package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "note_versions",
    indices = [Index(value = ["noteId"])]
)
data class NoteVersion(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val noteId: Long,
    val versionNumber: Int,
    val title: String,
    val content: String,
    val authorName: String,
    val timestamp: Long = System.currentTimeMillis()
)
