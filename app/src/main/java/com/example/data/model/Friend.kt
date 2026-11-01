package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "friends")
data class Friend(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val username: String, // e.g. "@eik"
    val avatarUri: String? = null,
    val avatarColorHex: String? = null,
    val status: String = "FRIEND", // "FRIEND", "PENDING_INCOMING", "PENDING_OUTGOING", "BLOCKED", "SUGGESTION"
    val subtitleTag: String = "unknown", // e.g. "unknown", "<Enviado via GDD>", "<Sugerido por Citra>", "<Vibe Code>", "<Prism.js>", "<Markdown>"
    val mutualFriendsCount: Int = 0,
    val totalFocusMinutes: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
