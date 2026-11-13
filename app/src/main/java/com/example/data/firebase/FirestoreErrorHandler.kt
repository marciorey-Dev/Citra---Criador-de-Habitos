package com.example.data.firebase

import android.content.Context
import android.util.Log
import com.example.R
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun handleFirestoreError(
    exception: Throwable,
    operationType: OperationType,
    path: String?,
    auth: FirebaseAuth? = null
): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val resolvedAuth = auth ?: try {
        FirebaseAuth.getInstance()
    } catch (_: Exception) {
        null
    }
    val currentUser = resolvedAuth?.currentUser

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", rootError.message ?: rootError.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

object CitraFirebaseProvider {
    fun ensureFirebaseInitialized(context: Context): FirebaseApp? {
        val appContext = context.applicationContext ?: context
        return try {
            if (FirebaseApp.getApps(appContext).isNotEmpty()) {
                FirebaseApp.getInstance()
            } else {
                FirebaseApp.initializeApp(appContext)
            }
        } catch (e: Exception) {
            Log.e("CitraFirebase", "FirebaseApp initialization failed", e)
            null
        }
    }

    fun getDatabaseId(context: Context): String {
        val appContext = context.applicationContext ?: context
        return appContext.getString(R.string.firestore_database_id)
    }

    fun getFirestore(context: Context): FirebaseFirestore? {
        val appContext = context.applicationContext ?: context
        val app = ensureFirebaseInitialized(appContext) ?: return null
        return try {
            val dbId = getDatabaseId(appContext)
            require(dbId.isNotBlank() && dbId != "firestore_database_id") {
                "firestore_database_id resource is missing or unconfigured"
            }
            FirebaseFirestore.getInstance(app, dbId)
        } catch (e: Exception) {
            Log.e("CitraFirebase", "Failed to obtain FirebaseFirestore for named database", e)
            null
        }
    }

    fun getAuth(context: Context): FirebaseAuth? {
        val appContext = context.applicationContext ?: context
        val app = ensureFirebaseInitialized(appContext) ?: return null
        return try {
            FirebaseAuth.getInstance(app)
        } catch (e: Exception) {
            Log.e("CitraFirebase", "Failed to obtain FirebaseAuth", e)
            null
        }
    }

    fun sha256Hex(raw: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(raw.trim().lowercase().toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
