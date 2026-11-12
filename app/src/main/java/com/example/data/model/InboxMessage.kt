package com.example.data.model

import java.util.UUID

data class InboxMessage(
    val id: String = UUID.randomUUID().toString(),
    val senderUid: String = "",
    val senderDisplayName: String = "",
    val senderUsername: String = "",
    val senderPhotoUrl: String? = null,
    val senderEmail: String? = null,
    val recipientUid: String = "",
    val recipientDisplayName: String = "",
    val recipientUsername: String = "",
    val recipientPhotoUrl: String? = null,
    val isOfficialDev: Boolean = false,
    val title: String = "",
    val contentMarkdown: String = "",
    val category: String = "Geral",
    val sentAtMillis: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val isDownloaded: Boolean = false,
    val isBroadcast: Boolean = false,
    val isBugReport: Boolean = false
) {
    fun toFirestoreMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "senderUid" to senderUid,
            "senderDisplayName" to senderDisplayName,
            "senderUsername" to senderUsername,
            "senderPhotoUrl" to senderPhotoUrl,
            "senderEmail" to senderEmail,
            "recipientUid" to recipientUid,
            "recipientDisplayName" to recipientDisplayName,
            "recipientUsername" to recipientUsername,
            "recipientPhotoUrl" to recipientPhotoUrl,
            "isOfficialDev" to isOfficialDev,
            "title" to title,
            "contentMarkdown" to contentMarkdown,
            "category" to category,
            "sentAtMillis" to sentAtMillis,
            "isRead" to isRead,
            "isDownloaded" to isDownloaded,
            "isBroadcast" to isBroadcast,
            "isBugReport" to isBugReport
        )
    }

    companion object {
        fun fromFirestoreMap(id: String, map: Map<String, Any?>): InboxMessage {
            return InboxMessage(
                id = (map["id"] as? String) ?: id,
                senderUid = (map["senderUid"] as? String).orEmpty(),
                senderDisplayName = (map["senderDisplayName"] as? String) ?: "Usuário Citra",
                senderUsername = (map["senderUsername"] as? String).orEmpty(),
                senderPhotoUrl = map["senderPhotoUrl"] as? String,
                senderEmail = map["senderEmail"] as? String,
                recipientUid = (map["recipientUid"] as? String).orEmpty(),
                recipientDisplayName = (map["recipientDisplayName"] as? String).orEmpty(),
                recipientUsername = (map["recipientUsername"] as? String).orEmpty(),
                recipientPhotoUrl = map["recipientPhotoUrl"] as? String,
                isOfficialDev = (map["isOfficialDev"] as? Boolean) ?: false,
                title = (map["title"] as? String) ?: "Nota sem título",
                contentMarkdown = (map["contentMarkdown"] as? String).orEmpty(),
                category = (map["category"] as? String) ?: "Geral",
                sentAtMillis = (map["sentAtMillis"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                isRead = (map["isRead"] as? Boolean) ?: false,
                isDownloaded = (map["isDownloaded"] as? Boolean) ?: false,
                isBroadcast = (map["isBroadcast"] as? Boolean) ?: false,
                isBugReport = (map["isBugReport"] as? Boolean) ?: false
            )
        }
    }
}

data class RecipientInfo(
    val uid: String,
    val displayName: String,
    val username: String,
    val photoUrl: String? = null,
    val email: String? = null
)

sealed class RecipientLookupState {
    object Idle : RecipientLookupState()
    object Loading : RecipientLookupState()
    data class Found(val recipient: RecipientInfo) : RecipientLookupState()
    object NotFound : RecipientLookupState()
    data class Error(val message: String) : RecipientLookupState()
}
