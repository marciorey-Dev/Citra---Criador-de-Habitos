package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
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
    val updatedAt: Long = System.currentTimeMillis(),
    val isShared: Boolean = false,
    val sharedGroupId: Long? = null,
    val sharedGroupName: String? = null,
    val sharedOwnerId: String? = "me",
    val sharedOwnerName: String? = null,
    val sharedGroupType: String = "LIBERAL", // "UNILATERAL" or "LIBERAL"
    val hasUnsyncedChanges: Boolean = false,
    val syncNotificationPending: Boolean = false,
    val autoAcceptSync: Boolean = false
)
