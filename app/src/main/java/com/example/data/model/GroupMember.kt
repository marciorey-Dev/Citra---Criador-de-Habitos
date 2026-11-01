package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "group_members")
data class GroupMember(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val groupId: Long,
    val friendId: Long, // 0 for local user
    val name: String,
    val username: String,
    val avatarUri: String? = null,
    val isAdmin: Boolean = false,
    val status: String = "ACCEPTED" // "ACCEPTED", "INVITED"
)
