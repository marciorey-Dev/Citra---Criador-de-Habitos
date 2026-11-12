package com.example.data.inbox

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.R
import com.example.data.model.InboxMessage
import com.example.data.model.RecipientInfo
import com.example.data.model.RecipientLookupState
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class InboxRepository(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("citra_inbox_prefs", Context.MODE_PRIVATE)

    companion object {
        val DEVELOPER_EMAILS = setOf(
            "obitoinfamous@gmail.com",
            "obitoinfamous5@gmail.com",
            "marcioafonsorey@gmail.com",
            "marcioreyakm@gmail.com",
            "marcioreyakm@gmail.co"
        )

        private val dynamicPromotedEmails = mutableSetOf<String>()
        private val dynamicPromotedUids = mutableSetOf<String>()

        fun isDeveloper(email: String?, uid: String? = null): Boolean {
            if (!email.isNullOrBlank()) {
                val clean = email.trim().lowercase()
                if (DEVELOPER_EMAILS.any { it.equals(clean, ignoreCase = true) } || dynamicPromotedEmails.contains(clean)) {
                    return true
                }
            }
            if (!uid.isNullOrBlank() && dynamicPromotedUids.contains(uid)) {
                return true
            }
            return false
        }

        fun promoteDeveloper(email: String?, uid: String?) {
            if (!email.isNullOrBlank()) dynamicPromotedEmails.add(email.trim().lowercase())
            if (!uid.isNullOrBlank()) dynamicPromotedUids.add(uid)
        }

        fun demoteDeveloper(email: String?, uid: String?) {
            if (!email.isNullOrBlank()) dynamicPromotedEmails.remove(email.trim().lowercase())
            if (!uid.isNullOrBlank()) dynamicPromotedUids.remove(uid)
        }

        @Volatile
        private var INSTANCE: InboxRepository? = null

        fun getInstance(context: Context): InboxRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: InboxRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val firestore: FirebaseFirestore?
        get() {
            return try {
                val app = com.example.data.auth.AuthRepository.ensureFirebaseInitialized(context) ?: return null
                val dbId = try {
                    val str = context.getString(R.string.firestore_database_id)
                    if (str.isNotBlank() && str != "firestore_database_id") str else "ai-studio-android-citracri-aef1e0cf-8dac-4828-a367-9197571c1d74"
                } catch (_: Exception) {
                    "ai-studio-android-citracri-aef1e0cf-8dac-4828-a367-9197571c1d74"
                }

                if (dbId.isNotBlank() && dbId != "default" && dbId != "(default)") {
                    try {
                        FirebaseFirestore.getInstance(app, dbId)
                    } catch (e: Exception) {
                        FirebaseFirestore.getInstance(app)
                    }
                } else {
                    FirebaseFirestore.getInstance(app)
                }
            } catch (e: Exception) {
                Log.e("InboxRepository", "Firebase Firestore could not be initialized", e)
                null
            }
        }

    suspend fun searchRecipient(
        query: String,
        preferUsernameLookup: Boolean = false
    ): RecipientLookupState = withContext(Dispatchers.IO) {
        val raw = query.trim()
        val clean = raw.removePrefix("@").lowercase()
        if (clean.isBlank()) return@withContext RecipientLookupState.Idle

        val db = firestore ?: return@withContext RecipientLookupState.Error("Banco de dados não disponível")
        try {
            fun docToRecipient(doc: com.google.firebase.firestore.DocumentSnapshot): RecipientInfo {
                return RecipientInfo(
                    uid = doc.id,
                    displayName = doc.getString("displayName") ?: clean,
                    username = (doc.getString("username") ?: clean).removePrefix("@"),
                    photoUrl = doc.getString("photoUrl"),
                    email = doc.getString("email")
                )
            }

            if (preferUsernameLookup || raw.startsWith("@")) {
                val byUsername = db.collection("users")
                    .whereEqualTo("username", clean)
                    .limit(1)
                    .get()
                    .await()
                if (!byUsername.isEmpty) {
                    return@withContext RecipientLookupState.Found(docToRecipient(byUsername.documents.first()))
                }
            }

            // Primary lookup: exact email or display name
            if (raw.contains("@") && raw.contains(".") && !raw.startsWith("@")) {
                val byEmail = db.collection("users")
                    .whereEqualTo("email", raw.lowercase())
                    .limit(1)
                    .get()
                    .await()
                if (!byEmail.isEmpty) {
                    return@withContext RecipientLookupState.Found(docToRecipient(byEmail.documents.first()))
                }
            }

            val byDisplay = db.collection("users")
                .whereEqualTo("displayName", raw)
                .limit(1)
                .get()
                .await()
            if (!byDisplay.isEmpty) {
                return@withContext RecipientLookupState.Found(docToRecipient(byDisplay.documents.first()))
            }

            // Secondary fallback lookup: @username without requiring leading @
            val byUsernameFallback = db.collection("users")
                .whereEqualTo("username", clean)
                .limit(1)
                .get()
                .await()
            if (!byUsernameFallback.isEmpty) {
                return@withContext RecipientLookupState.Found(docToRecipient(byUsernameFallback.documents.first()))
            }

            RecipientLookupState.NotFound
        } catch (e: Exception) {
            Log.e("InboxRepository", "Error looking up recipient $clean", e)
            RecipientLookupState.Error(e.localizedMessage ?: "Erro ao buscar destinatário")
        }
    }

    suspend fun sendDirectMessage(
        recipientUid: String,
        message: InboxMessage,
        recipientInfo: RecipientInfo? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext Result.failure(IllegalStateException("Banco de dados indisponível"))
        try {
            val messageWithRecipient = message.copy(
                recipientUid = recipientUid,
                recipientDisplayName = recipientInfo?.displayName ?: message.recipientDisplayName,
                recipientUsername = recipientInfo?.username ?: message.recipientUsername,
                recipientPhotoUrl = recipientInfo?.photoUrl ?: message.recipientPhotoUrl
            )
            db.collection("users")
                .document(recipientUid)
                .collection("inbox")
                .document(messageWithRecipient.id)
                .set(messageWithRecipient.toFirestoreMap())
                .await()

            if (messageWithRecipient.senderUid.isNotBlank()) {
                try {
                    db.collection("users")
                        .document(messageWithRecipient.senderUid)
                        .collection("sent")
                        .document(messageWithRecipient.id)
                        .set(messageWithRecipient.copy(isRead = true).toFirestoreMap())
                        .await()
                } catch (e: Exception) {
                    Log.w("InboxRepository", "Could not save sent message copy", e)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("InboxRepository", "Failed to send message to $recipientUid", e)
            Result.failure(e)
        }
    }

    suspend fun sendBroadcastMessage(message: InboxMessage): Result<Unit> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext Result.failure(IllegalStateException("Banco de dados indisponível"))
        try {
            val broadcastMsg = message.copy(
                recipientDisplayName = "Todos os usuários",
                recipientUsername = "citra_oficial"
            )
            db.collection("broadcasts")
                .document(broadcastMsg.id)
                .set(broadcastMsg.toFirestoreMap())
                .await()

            if (broadcastMsg.senderUid.isNotBlank()) {
                try {
                    db.collection("users")
                        .document(broadcastMsg.senderUid)
                        .collection("sent")
                        .document(broadcastMsg.id)
                        .set(broadcastMsg.copy(isRead = true).toFirestoreMap())
                        .await()
                } catch (e: Exception) {
                    Log.w("InboxRepository", "Could not save sent broadcast copy", e)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("InboxRepository", "Failed to send broadcast", e)
            Result.failure(e)
        }
    }

    suspend fun sendBugReport(
        reporterUid: String,
        reporterDisplayName: String,
        reporterUsername: String,
        reporterPhotoUrl: String?,
        reporterEmail: String?,
        title: String,
        description: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext Result.failure(IllegalStateException("Banco de dados indisponível"))
        try {
            val reportId = java.util.UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val cleanTitle = title.trim().ifBlank { "Relato de Bug" }
            val cleanDesc = description.trim()
            val appVersion = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "N/A"
            } catch (_: Exception) {
                "N/A"
            }

            // 1. Write to global bugReports collection
            val reportData = mapOf(
                "id" to reportId,
                "reporterUid" to reporterUid,
                "reporterDisplayName" to reporterDisplayName,
                "reporterUsername" to reporterUsername,
                "reporterEmail" to (reporterEmail ?: ""),
                "title" to cleanTitle,
                "description" to cleanDesc,
                "appVersion" to appVersion,
                "deviceModel" to "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE})",
                "createdAtMillis" to now,
                "status" to "OPEN"
            )
            db.collection("bugReports")
                .document(reportId)
                .set(reportData)
                .await()

            // 2. Deliver directly to developer inboxes so devs see it in Caixa de Entrada immediately
            val formattedMarkdown = buildString {
                appendLine("## 🐞 Relato de Bug")
                appendLine("**Usuário:** $reporterDisplayName (@$reporterUsername)")
                if (!reporterEmail.isNullOrBlank()) {
                    appendLine("**E-mail:** $reporterEmail")
                }
                appendLine("**Versão do App:** $appVersion | **Dispositivo:** ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                appendLine()
                appendLine("---")
                appendLine()
                appendLine(cleanDesc)
            }

            val devMessage = InboxMessage(
                id = reportId,
                senderUid = reporterUid,
                senderDisplayName = reporterDisplayName,
                senderUsername = reporterUsername,
                senderPhotoUrl = reporterPhotoUrl,
                senderEmail = reporterEmail,
                recipientUid = "devs",
                recipientDisplayName = "Equipe Citra (Devs)",
                recipientUsername = "suporte",
                isOfficialDev = false,
                title = "🐞 Bug: $cleanTitle",
                contentMarkdown = formattedMarkdown,
                category = "Relato de Bug",
                sentAtMillis = now,
                isRead = false,
                isDownloaded = false,
                isBroadcast = false,
                isBugReport = true
            )

            val devUids = mutableSetOf<String>()
            try {
                val devFlagDocs = db.collection("users").whereEqualTo("isDev", true).get().await()
                devFlagDocs.documents.forEach { devUids.add(it.id) }
            } catch (_: Exception) {}
            try {
                val devEmailDocs = db.collection("users")
                    .whereIn("email", DEVELOPER_EMAILS.toList())
                    .get()
                    .await()
                devEmailDocs.documents.forEach { devUids.add(it.id) }
            } catch (_: Exception) {}

            for (devUid in devUids) {
                try {
                    db.collection("users")
                        .document(devUid)
                        .collection("inbox")
                        .document(reportId)
                        .set(devMessage.copy(recipientUid = devUid).toFirestoreMap())
                        .await()
                } catch (_: Exception) {}
            }

            // 3. Save copy to reporter's Sent box
            try {
                db.collection("users")
                    .document(reporterUid)
                    .collection("sent")
                    .document(reportId)
                    .set(devMessage.copy(isRead = true).toFirestoreMap())
                    .await()
            } catch (e: Exception) {
                Log.w("InboxRepository", "Could not save bug report in sent box", e)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("InboxRepository", "Failed to send bug report", e)
            Result.failure(e)
        }
    }

    fun fetchInboxMessages(userUid: String): Flow<List<InboxMessage>> = flow {
        val db = firestore
        if (db == null || userUid.isBlank()) {
            emit(emptyList())
            return@flow
        }

        try {
            val dismissedBroadcasts = prefs.getStringSet("dismissed_broadcasts", emptySet()) ?: emptySet()
            val readBroadcasts = prefs.getStringSet("read_broadcasts", emptySet()) ?: emptySet()

            // 1. Fetch personal inbox
            val personalDocs = db.collection("users")
                .document(userUid)
                .collection("inbox")
                .orderBy("sentAtMillis", Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .await()

            val personalMessages = personalDocs.documents.mapNotNull { doc ->
                doc.data?.let { InboxMessage.fromFirestoreMap(doc.id, it) }
            }

            // 2. Fetch public broadcasts (developer announcements)
            val broadcastDocs = try {
                db.collection("broadcasts")
                    .orderBy("sentAtMillis", Query.Direction.DESCENDING)
                    .limit(20)
                    .get()
                    .await()
            } catch (e: Exception) {
                null
            }

            val broadcastMessages = broadcastDocs?.documents?.mapNotNull { doc ->
                if (dismissedBroadcasts.contains(doc.id)) return@mapNotNull null
                doc.data?.let { data ->
                    val msg = InboxMessage.fromFirestoreMap(doc.id, data)
                    msg.copy(
                        isBroadcast = true,
                        isOfficialDev = true,
                        isRead = readBroadcasts.contains(doc.id) || msg.isRead
                    )
                }
            } ?: emptyList()

            // Combine and sort by sentAtMillis descending
            val combined = (personalMessages + broadcastMessages).sortedByDescending { it.sentAtMillis }
            emit(combined)
        } catch (e: Exception) {
            Log.e("InboxRepository", "Error fetching inbox for $userUid", e)
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    fun fetchSentMessages(userUid: String): Flow<List<InboxMessage>> = flow {
        val db = firestore
        if (db == null || userUid.isBlank()) {
            emit(emptyList())
            return@flow
        }

        try {
            val sentDocs = db.collection("users")
                .document(userUid)
                .collection("sent")
                .orderBy("sentAtMillis", Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .await()

            val sentMessages = sentDocs.documents.mapNotNull { doc ->
                doc.data?.let { InboxMessage.fromFirestoreMap(doc.id, it) }
            }
            emit(sentMessages)
        } catch (e: Exception) {
            Log.e("InboxRepository", "Error fetching sent messages for $userUid", e)
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    suspend fun markAsRead(userUid: String, message: InboxMessage) = withContext(Dispatchers.IO) {
        if (message.isBroadcast) {
            val current = prefs.getStringSet("read_broadcasts", emptySet()) ?: emptySet()
            prefs.edit().putStringSet("read_broadcasts", current + message.id).apply()
            return@withContext
        }
        val db = firestore ?: return@withContext
        try {
            db.collection("users")
                .document(userUid)
                .collection("inbox")
                .document(message.id)
                .update("isRead", true)
                .await()
        } catch (e: Exception) {
            Log.w("InboxRepository", "Error marking message as read", e)
        }
    }

    suspend fun markAsDownloaded(userUid: String, message: InboxMessage) = withContext(Dispatchers.IO) {
        if (message.isBroadcast) return@withContext
        val db = firestore ?: return@withContext
        try {
            db.collection("users")
                .document(userUid)
                .collection("inbox")
                .document(message.id)
                .update("isDownloaded", true, "isRead", true)
                .await()
        } catch (e: Exception) {
            Log.w("InboxRepository", "Error marking message as downloaded", e)
        }
    }

    suspend fun deleteMessage(userUid: String, message: InboxMessage) = withContext(Dispatchers.IO) {
        if (message.isBroadcast) {
            val current = prefs.getStringSet("dismissed_broadcasts", emptySet()) ?: emptySet()
            prefs.edit().putStringSet("dismissed_broadcasts", current + message.id).apply()
            return@withContext
        }
        val db = firestore ?: return@withContext
        try {
            db.collection("users")
                .document(userUid)
                .collection("inbox")
                .document(message.id)
                .delete()
                .await()
        } catch (e: Exception) {
            Log.w("InboxRepository", "Error deleting message", e)
        }
    }

    suspend fun deleteMessages(userUid: String, messages: List<InboxMessage>) = withContext(Dispatchers.IO) {
        val broadcastIds = messages.filter { it.isBroadcast }.map { it.id }.toSet()
        if (broadcastIds.isNotEmpty()) {
            val current = prefs.getStringSet("dismissed_broadcasts", emptySet()) ?: emptySet()
            prefs.edit().putStringSet("dismissed_broadcasts", current + broadcastIds).apply()
        }
        val db = firestore ?: return@withContext
        for (msg in messages.filter { !it.isBroadcast }) {
            try {
                db.collection("users")
                    .document(userUid)
                    .collection("inbox")
                    .document(msg.id)
                    .delete()
                    .await()
            } catch (_: Exception) {}
        }
    }

    suspend fun clearInbox(userUid: String, messages: List<InboxMessage>) = withContext(Dispatchers.IO) {
        deleteMessages(userUid, messages)
    }

    suspend fun deleteSentMessage(userUid: String, messageId: String) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        try {
            db.collection("users")
                .document(userUid)
                .collection("sent")
                .document(messageId)
                .delete()
                .await()
        } catch (e: Exception) {
            Log.w("InboxRepository", "Error deleting sent message", e)
        }
    }

    suspend fun deleteSentMessages(userUid: String, messageIds: List<String>) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        for (id in messageIds) {
            try {
                db.collection("users")
                    .document(userUid)
                    .collection("sent")
                    .document(id)
                    .delete()
                    .await()
            } catch (_: Exception) {}
        }
    }
}
