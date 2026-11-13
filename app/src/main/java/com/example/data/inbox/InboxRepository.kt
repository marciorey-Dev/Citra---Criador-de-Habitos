package com.example.data.inbox

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.data.firebase.CitraFirebaseProvider
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.InboxMessage
import com.example.data.model.RecipientInfo
import com.example.data.model.RecipientLookupState
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class InboxRepository(
    private val context: Context,
    private val overrideFirestore: FirebaseFirestore? = null,
    private val overrideAuth: FirebaseAuth? = null
) {

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

    private val auth: FirebaseAuth?
        get() = overrideAuth ?: CitraFirebaseProvider.getAuth(context)

    private val firestore: FirebaseFirestore?
        get() = overrideFirestore ?: CitraFirebaseProvider.getFirestore(context)

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
                    uid = doc.getString("uid") ?: doc.id,
                    displayName = doc.getString("displayName") ?: clean,
                    username = (doc.getString("username") ?: clean).removePrefix("@"),
                    photoUrl = doc.getString("photoUrl"),
                    email = null
                )
            }

            if (preferUsernameLookup || raw.startsWith("@")) {
                val usernameKey = CitraFirebaseProvider.sha256Hex(clean)
                val byUsername = db.collection("usernameLookup")
                    .document(usernameKey)
                    .get()
                    .await()
                if (byUsername.exists()) {
                    return@withContext RecipientLookupState.Found(docToRecipient(byUsername))
                }
            }

            if (raw.contains("@") && raw.contains(".") && !raw.startsWith("@")) {
                val emailKey = CitraFirebaseProvider.sha256Hex(raw.lowercase())
                val byEmail = db.collection("emailLookup")
                    .document(emailKey)
                    .get()
                    .await()
                if (byEmail.exists()) {
                    return@withContext RecipientLookupState.Found(docToRecipient(byEmail))
                }
            }

            val displayKey = CitraFirebaseProvider.sha256Hex(raw)
            val byDisplay = db.collection("displayNameLookup")
                .document(displayKey)
                .get()
                .await()
            if (byDisplay.exists()) {
                return@withContext RecipientLookupState.Found(docToRecipient(byDisplay))
            }

            val usernameFallbackKey = CitraFirebaseProvider.sha256Hex(clean)
            val byUsernameFallback = db.collection("usernameLookup")
                .document(usernameFallbackKey)
                .get()
                .await()
            if (byUsernameFallback.exists()) {
                return@withContext RecipientLookupState.Found(docToRecipient(byUsernameFallback))
            }

            RecipientLookupState.NotFound
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "recipientLookup", auth)
            RecipientLookupState.Error(e.localizedMessage ?: "Erro ao buscar destinatário")
        }
    }

    suspend fun sendDirectMessage(
        recipientUid: String,
        message: InboxMessage,
        recipientInfo: RecipientInfo? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext Result.failure(IllegalStateException("Banco de dados indisponível"))
        val inboxPath = "users/$recipientUid/inbox/${message.id}"
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
                val sentPath = "users/${messageWithRecipient.senderUid}/sent/${messageWithRecipient.id}"
                try {
                    db.collection("users")
                        .document(messageWithRecipient.senderUid)
                        .collection("sent")
                        .document(messageWithRecipient.id)
                        .set(messageWithRecipient.copy(isRead = true).toFirestoreMap())
                        .await()
                } catch (e: Exception) {
                    handleFirestoreError(e, OperationType.CREATE, sentPath, auth)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, inboxPath, auth)
            Result.failure(e)
        }
    }

    suspend fun sendBroadcastMessage(message: InboxMessage): Result<Unit> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext Result.failure(IllegalStateException("Banco de dados indisponível"))
        val broadcastPath = "broadcasts/${message.id}"
        try {
            val broadcastMsg = message.copy(
                isBroadcast = true,
                isOfficialDev = true,
                recipientDisplayName = "Todos os usuários",
                recipientUsername = "citra_oficial"
            )
            db.collection("broadcasts")
                .document(broadcastMsg.id)
                .set(broadcastMsg.toFirestoreMap())
                .await()

            if (broadcastMsg.senderUid.isNotBlank()) {
                val sentPath = "users/${broadcastMsg.senderUid}/sent/${broadcastMsg.id}"
                try {
                    db.collection("users")
                        .document(broadcastMsg.senderUid)
                        .collection("sent")
                        .document(broadcastMsg.id)
                        .set(broadcastMsg.copy(isRead = true).toFirestoreMap())
                        .await()
                } catch (e: Exception) {
                    handleFirestoreError(e, OperationType.CREATE, sentPath, auth)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, broadcastPath, auth)
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
        val reportId = java.util.UUID.randomUUID().toString()
        val reportPath = "bugReports/$reportId"
        try {
            val now = System.currentTimeMillis()
            val cleanTitle = title.trim().ifBlank { "Relato de Bug" }
            val cleanDesc = description.trim().ifBlank { "Sem descrição" }
            val appVersion = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "N/A"
            } catch (_: Exception) {
                "N/A"
            }

            val reportData = mutableMapOf<String, Any>(
                "id" to reportId,
                "reporterUid" to reporterUid,
                "reporterDisplayName" to reporterDisplayName.take(100),
                "reporterUsername" to reporterUsername.take(60),
                "title" to cleanTitle.take(200),
                "description" to cleanDesc.take(10000),
                "appVersion" to appVersion.take(40),
                "deviceModel" to "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE})".take(120),
                "createdAtMillis" to now,
                "status" to "OPEN"
            )
            if (!reporterEmail.isNullOrBlank()) {
                reportData["reporterEmail"] = reporterEmail.take(200)
            }

            db.collection("bugReports")
                .document(reportId)
                .set(reportData)
                .await()

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

            val sentCopy = InboxMessage(
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
                isRead = true,
                isDownloaded = false,
                isBroadcast = false,
                isBugReport = true
            )

            val sentPath = "users/$reporterUid/sent/$reportId"
            try {
                db.collection("users")
                    .document(reporterUid)
                    .collection("sent")
                    .document(reportId)
                    .set(sentCopy.toFirestoreMap())
                    .await()
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, sentPath, auth)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, reportPath, auth)
            Result.failure(e)
        }
    }

    fun fetchInboxMessages(userUid: String): Flow<List<InboxMessage>> = flow {
        val db = firestore
        if (db == null || userUid.isBlank()) {
            emit(emptyList())
            return@flow
        }

        val inboxPath = "users/$userUid/inbox"
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

            // 2. Fetch public broadcasts (aligned with security rule existing().isBroadcast == true)
            val broadcastDocs = try {
                db.collection("broadcasts")
                    .whereEqualTo("isBroadcast", true)
                    .limit(20)
                    .get()
                    .await()
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.LIST, "broadcasts", auth)
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

            // 3. For developer accounts, also surface bugReports from /bugReports
            val currentEmail = auth?.currentUser?.email
            val bugMessages = if (isDeveloper(currentEmail, userUid)) {
                try {
                    val bugDocs = db.collection("bugReports")
                        .limit(20)
                        .get()
                        .await()
                    bugDocs.documents.mapNotNull { doc ->
                        val data = doc.data ?: return@mapNotNull null
                        val rId = doc.id
                        if (dismissedBroadcasts.contains(rId)) return@mapNotNull null
                        val rUid = (data["reporterUid"] as? String).orEmpty()
                        val rName = (data["reporterDisplayName"] as? String) ?: "Usuário Citra"
                        val rUsername = (data["reporterUsername"] as? String).orEmpty()
                        val rEmail = data["reporterEmail"] as? String
                        val rTitle = (data["title"] as? String) ?: "Relato de Bug"
                        val rDesc = (data["description"] as? String).orEmpty()
                        val rVersion = (data["appVersion"] as? String) ?: "N/A"
                        val rDevice = (data["deviceModel"] as? String).orEmpty()
                        val createdAt = (data["createdAtMillis"] as? Number)?.toLong() ?: 0L
                        InboxMessage(
                            id = rId,
                            senderUid = rUid,
                            senderDisplayName = rName,
                            senderUsername = rUsername,
                            senderEmail = rEmail,
                            recipientUid = userUid,
                            recipientDisplayName = "Equipe Citra (Devs)",
                            recipientUsername = "suporte",
                            isOfficialDev = false,
                            title = "🐞 Bug: $rTitle",
                            contentMarkdown = "**Versão:** $rVersion | **Dispositivo:** $rDevice\n\n$rDesc",
                            category = "Relato de Bug",
                            sentAtMillis = createdAt,
                            isRead = readBroadcasts.contains(rId),
                            isDownloaded = false,
                            isBroadcast = false,
                            isBugReport = true
                        )
                    }
                } catch (e: Exception) {
                    handleFirestoreError(e, OperationType.LIST, "bugReports", auth)
                    emptyList()
                }
            } else {
                emptyList()
            }

            val combined = (personalMessages + broadcastMessages + bugMessages)
                .distinctBy { it.id }
                .sortedByDescending { it.sentAtMillis }
            emit(combined)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, inboxPath, auth)
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    fun fetchSentMessages(userUid: String): Flow<List<InboxMessage>> = flow {
        val db = firestore
        if (db == null || userUid.isBlank()) {
            emit(emptyList())
            return@flow
        }

        val sentPath = "users/$userUid/sent"
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
            handleFirestoreError(e, OperationType.LIST, sentPath, auth)
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    suspend fun markAsRead(userUid: String, message: InboxMessage) = withContext(Dispatchers.IO) {
        if (message.isBroadcast || message.isBugReport) {
            val current = prefs.getStringSet("read_broadcasts", emptySet()) ?: emptySet()
            prefs.edit().putStringSet("read_broadcasts", current + message.id).apply()
            return@withContext
        }
        val db = firestore ?: return@withContext
        val path = "users/$userUid/inbox/${message.id}"
        try {
            db.collection("users")
                .document(userUid)
                .collection("inbox")
                .document(message.id)
                .update("isRead", true)
                .await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path, auth)
        }
    }

    suspend fun markAsDownloaded(userUid: String, message: InboxMessage) = withContext(Dispatchers.IO) {
        if (message.isBroadcast) return@withContext
        val db = firestore ?: return@withContext
        val path = "users/$userUid/inbox/${message.id}"
        try {
            db.collection("users")
                .document(userUid)
                .collection("inbox")
                .document(message.id)
                .update("isDownloaded", true, "isRead", true)
                .await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path, auth)
        }
    }

    suspend fun deleteMessage(userUid: String, message: InboxMessage) = withContext(Dispatchers.IO) {
        if (message.isBroadcast || message.isBugReport) {
            val current = prefs.getStringSet("dismissed_broadcasts", emptySet()) ?: emptySet()
            prefs.edit().putStringSet("dismissed_broadcasts", current + message.id).apply()
            return@withContext
        }
        val db = firestore ?: return@withContext
        val path = "users/$userUid/inbox/${message.id}"
        try {
            db.collection("users")
                .document(userUid)
                .collection("inbox")
                .document(message.id)
                .delete()
                .await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path, auth)
        }
    }

    suspend fun deleteMessages(userUid: String, messages: List<InboxMessage>) = withContext(Dispatchers.IO) {
        val localDismissIds = messages.filter { it.isBroadcast || it.isBugReport }.map { it.id }.toSet()
        if (localDismissIds.isNotEmpty()) {
            val current = prefs.getStringSet("dismissed_broadcasts", emptySet()) ?: emptySet()
            prefs.edit().putStringSet("dismissed_broadcasts", current + localDismissIds).apply()
        }
        val db = firestore ?: return@withContext
        for (msg in messages.filter { !it.isBroadcast && !it.isBugReport }) {
            val path = "users/$userUid/inbox/${msg.id}"
            try {
                db.collection("users")
                    .document(userUid)
                    .collection("inbox")
                    .document(msg.id)
                    .delete()
                    .await()
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.DELETE, path, auth)
            }
        }
    }

    suspend fun clearInbox(userUid: String, messages: List<InboxMessage>) = withContext(Dispatchers.IO) {
        deleteMessages(userUid, messages)
    }

    suspend fun deleteSentMessage(userUid: String, messageId: String) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        val path = "users/$userUid/sent/$messageId"
        try {
            db.collection("users")
                .document(userUid)
                .collection("sent")
                .document(messageId)
                .delete()
                .await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path, auth)
        }
    }

    suspend fun deleteSentMessages(userUid: String, messageIds: List<String>) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        for (id in messageIds) {
            val path = "users/$userUid/sent/$id"
            try {
                db.collection("users")
                    .document(userUid)
                    .collection("sent")
                    .document(id)
                    .delete()
                    .await()
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.DELETE, path, auth)
            }
        }
    }
}
