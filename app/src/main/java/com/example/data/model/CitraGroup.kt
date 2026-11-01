package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "groups")
data class CitraGroup(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val imageUri: String? = null,
    val avatarInitials: String = "GP",
    val avatarColorHex: String? = null,
    val ownerId: String = "me", // "me" or member ID
    val groupType: String = "LIBERAL", // "UNILATERAL" (read-only for members) or "LIBERAL" (live edit)
    val memberCount: Int = 1,
    val status: String = "ACTIVE", // "ACTIVE", "INVITED"
    val createdAt: Long = System.currentTimeMillis()
)
