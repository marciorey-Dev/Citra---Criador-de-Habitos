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

    private val firestore: FirebaseFirestore? by lazy {
        try {
            val app = if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context) ?: FirebaseApp.getInstance()
            } else {
                FirebaseApp.getInstance()
            }
            val dbId = try {
                context.getString(R.string.firestore_database_id)
            } catch (_: Exception) { "" }

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

    suspend fun searchRecipient(username: String): RecipientLookupState = withContext(Dispatchers.IO) {
        val clean = username.trim().removePrefix("@").lowercase()
        if (clean.isBlank()) return@withContext RecipientLookupState.Idle

        val db = firestore ?: return@withContext RecipientLookupState.Error("Banco de dados não disponível")
        try {
            val querySnapshot = db.collection("users")
                .whereEqualTo("username", clean)
                .limit(1)
                .get()
                .await()

            if (querySnapshot.isEmpty) {
                // Also attempt lowercase fallback or matching displayName if username was not formatted with @
                val byDisplay = db.collection("users")
                    .whereEqualTo("displayName", username.trim())
                    .limit(1)
                    .get()
                    .await()
                if (!byDisplay.isEmpty) {
                    val doc = byDisplay.documents.first()
                    return@withContext RecipientLookupState.Found(
                        RecipientInfo(
                            uid = doc.id,
                            displayName = doc.getString("displayName") ?: username,
                            username = doc.getString("username") ?: clean,
                            photoUrl = doc.getString("photoUrl")
                        )
                    )
                }
                return@withContext RecipientLookupState.NotFound
            }

            val doc = querySnapshot.documents.first()
            RecipientLookupState.Found(
                RecipientInfo(
                    uid = doc.id,
                    displayName = doc.getString("displayName") ?: clean,
                    username = doc.getString("username") ?: clean,
                    photoUrl = doc.getString("photoUrl")
                )
            )
        } catch (e: Exception) {
            Log.e("InboxRepository", "Error looking up recipient @$clean", e)
            RecipientLookupState.Error(e.localizedMessage ?: "Erro ao buscar destinatário")
        }
    }

    suspend fun sendDirectMessage(recipientUid: String, message: InboxMessage): Result<Unit> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext Result.failure(IllegalStateException("Banco de dados indisponível"))
        try {
            db.collection("users")
                .document(recipientUid)
                .collection("inbox")
                .document(message.id)
                .set(message.toFirestoreMap())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("InboxRepository", "Failed to send message to $recipientUid", e)
            Result.failure(e)
        }
    }

    suspend fun sendBroadcastMessage(message: InboxMessage): Result<Unit> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext Result.failure(IllegalStateException("Banco de dados indisponível"))
        try {
            db.collection("broadcasts")
                .document(message.id)
                .set(message.toFirestoreMap())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("InboxRepository", "Failed to send broadcast", e)
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

    suspend fun clearInbox(userUid: String, messages: List<InboxMessage>) = withContext(Dispatchers.IO) {
        val broadcastIds = messages.filter { it.isBroadcast }.map { it.id }.toSet()
        if (broadcastIds.isNotEmpty()) {
            val current = prefs.getStringSet("dismissed_broadcasts", emptySet()) ?: emptySet()
            prefs.edit().putStringSet("dismissed_broadcasts", current + broadcastIds).apply()
        }

        val db = firestore ?: return@withContext
        val personal = messages.filter { !it.isBroadcast }
        for (msg in personal) {
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
}
