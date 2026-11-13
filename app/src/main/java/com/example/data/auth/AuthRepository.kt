package com.example.data.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.example.R
import com.example.data.firebase.CitraFirebaseProvider
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.inbox.InboxRepository
import com.example.data.model.Achievement
import com.example.data.model.Category
import com.example.data.model.FocusSession
import com.example.data.model.Habit
import com.example.data.model.Note
import com.example.data.model.Task
import com.example.data.backup.BackupManager
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class UserProfile(
    val uid: String,
    val displayName: String,
    val username: String, // @username
    val photoUrl: String? = null,
    val email: String? = null,
    val isPrivate: Boolean = false,
    val isGuest: Boolean = false,
    val isDev: Boolean = false,
    val isBanned: Boolean = false,
    val totalFocusMinutes: Int = 0,
    val completedTasksCount: Int = 0,
    val completedHabitsCount: Int = 0,
    val achievementsCount: Int = 0
)

class AuthRepository(
    private val context: Context,
    private val overrideFirestore: FirebaseFirestore? = null,
    private val overrideAuth: FirebaseAuth? = null,
    attachAuthListener: Boolean = true
) {

    private val prefs: SharedPreferences = context.getSharedPreferences("citra_prefs", Context.MODE_PRIVATE)
    private val credentialManager = CredentialManager.create(context)
    private val scope = CoroutineScope(Dispatchers.IO)
    private val profileMutex = Mutex()

    val auth: FirebaseAuth?
        get() = overrideAuth ?: CitraFirebaseProvider.getAuth(context)

    val firestore: FirebaseFirestore?
        get() = overrideFirestore ?: CitraFirebaseProvider.getFirestore(context)

    private val _currentUser = MutableStateFlow<UserProfile?>(null)
    val currentUser: StateFlow<UserProfile?> = _currentUser.asStateFlow()

    private val _isGuestMode = MutableStateFlow(prefs.getBoolean("is_guest_mode", false))
    val isGuestMode: StateFlow<Boolean> = _isGuestMode.asStateFlow()

    private val _isAuthReady = MutableStateFlow(false)
    val isAuthReady: StateFlow<Boolean> = _isAuthReady.asStateFlow()

    init {
        CitraFirebaseProvider.ensureFirebaseInitialized(context)
        if (attachAuthListener) {
            val firebaseAuth = auth
            if (firebaseAuth != null) {
                firebaseAuth.addAuthStateListener { fa ->
                    val user = fa.currentUser
                    if (user != null) {
                        _isGuestMode.value = false
                        prefs.edit()
                            .putBoolean("is_guest_mode", false)
                            .putBoolean("explicitly_signed_out", false)
                            .apply()
                        scope.launch {
                            ensureAndLoadUserProfile(user)
                        }
                    } else if (_isGuestMode.value) {
                        loadGuestProfile()
                    } else {
                        _currentUser.value = null
                        _isAuthReady.value = true
                    }
                }
            }
            if (firebaseAuth?.currentUser == null) {
                if (_isGuestMode.value) {
                    loadGuestProfile()
                } else {
                    _isAuthReady.value = true
                }
            }
        } else {
            _isAuthReady.value = true
        }
    }

    fun startGuestMode() {
        _isGuestMode.value = true
        prefs.edit().putBoolean("is_guest_mode", true).apply()
        loadGuestProfile()
    }

    private fun loadGuestProfile() {
        val savedName = prefs.getString("guest_name", "Viajante Citra") ?: "Viajante Citra"
        val savedUsername = (prefs.getString("guest_username", "viajante") ?: "viajante").removePrefix("@")
        val localAvatar = java.io.File(context.filesDir, "avatars/user_guest.jpg")
        val savedPhoto = if (localAvatar.exists()) localAvatar.absolutePath else prefs.getString("guest_photo", null)
        val isPrivate = prefs.getBoolean("guest_is_private", false)
        val isDev = prefs.getBoolean("guest_is_dev", false)
        val isBanned = prefs.getBoolean("guest_is_banned", false)

        _currentUser.value = UserProfile(
            uid = "guest_local_user",
            displayName = savedName,
            username = savedUsername,
            photoUrl = savedPhoto,
            email = null,
            isPrivate = isPrivate,
            isGuest = true,
            isDev = isDev,
            isBanned = isBanned
        )
        _isAuthReady.value = true
    }

    suspend fun ensureAndLoadUserProfile(firebaseUser: FirebaseUser): UserProfile = profileMutex.withLock {
        val uid = firebaseUser.uid
        val authDisplayName = firebaseUser.displayName?.trim()?.takeIf { it.isNotBlank() } ?: "Usuário Citra"
        val authPhotoUrl = firebaseUser.photoUrl?.toString()?.takeIf { it.isNotBlank() }
        val authEmail = firebaseUser.email?.trim()?.takeIf { it.isNotBlank() }
        val localAvatar = java.io.File(context.filesDir, "avatars/user_${uid}.jpg")
        val defaultPhoto = if (localAvatar.exists()) localAvatar.absolutePath else authPhotoUrl
        val isDevInitial = InboxRepository.isDeveloper(authEmail, uid)
        val db = firestore

        if (db == null) {
            val offlineProfile = UserProfile(
                uid = uid,
                displayName = authDisplayName,
                username = sanitizeUsername(authDisplayName, uid),
                photoUrl = defaultPhoto,
                email = authEmail,
                isPrivate = false,
                isGuest = false,
                isDev = isDevInitial,
                isBanned = false
            )
            _currentUser.value = offlineProfile
            _isAuthReady.value = true
            return@withLock offlineProfile
        }

        val userDocPath = "users/$uid"
        val userRef = db.collection("users").document(uid)
        val loadedProfile = try {
            val doc = userRef.get().await()
            if (doc.exists()) {
                val firestorePhoto = doc.getString("photoUrl")
                val effectivePhoto = if (localAvatar.exists()) localAvatar.absolutePath else (firestorePhoto ?: authPhotoUrl)
                val isDev = doc.getBoolean("isDev") ?: isDevInitial
                val isBanned = doc.getBoolean("isBanned") ?: false
                val mail = doc.getString("email")?.takeIf { it.isNotBlank() } ?: authEmail
                val existingName = doc.getString("displayName")?.takeIf { it.isNotBlank() } ?: authDisplayName
                val existingUsername = (doc.getString("username")?.takeIf { it.isNotBlank() } ?: sanitizeUsername(existingName, uid)).removePrefix("@")

                // Backfill missing email/photoUrl/userId without overwriting user edits
                val backfill = mutableMapOf<String, Any>()
                if (doc.getString("userId").isNullOrBlank()) backfill["userId"] = uid
                if (doc.getString("email").isNullOrBlank() && !authEmail.isNullOrBlank()) backfill["email"] = authEmail
                if (firestorePhoto.isNullOrBlank() && !authPhotoUrl.isNullOrBlank()) backfill["photoUrl"] = authPhotoUrl
                if (backfill.isNotEmpty()) {
                    backfill["updatedAt"] = FieldValue.serverTimestamp()
                    try {
                        userRef.set(backfill, SetOptions.merge()).await()
                    } catch (e: Exception) {
                        handleFirestoreError(e, OperationType.UPDATE, userDocPath, auth)
                    }
                }

                UserProfile(
                    uid = uid,
                    displayName = existingName,
                    username = existingUsername,
                    photoUrl = effectivePhoto,
                    email = mail,
                    isPrivate = doc.getBoolean("isPrivate") ?: false,
                    isGuest = false,
                    isDev = isDev,
                    isBanned = isBanned,
                    totalFocusMinutes = (doc.getLong("totalFocusMinutes") ?: 0L).toInt(),
                    completedTasksCount = (doc.getLong("completedTasksCount") ?: 0L).toInt(),
                    completedHabitsCount = (doc.getLong("completedHabitsCount") ?: 0L).toInt(),
                    achievementsCount = (doc.getLong("achievementsCount") ?: 0L).toInt()
                )
            } else {
                val autoUsername = sanitizeUsername(authDisplayName, uid)
                val newProfile = UserProfile(
                    uid = uid,
                    displayName = authDisplayName,
                    username = autoUsername,
                    photoUrl = defaultPhoto,
                    email = authEmail,
                    isPrivate = false,
                    isGuest = false,
                    isDev = isDevInitial,
                    isBanned = false
                )
                val createPayload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "displayName" to newProfile.displayName,
                    "username" to newProfile.username,
                    "isPrivate" to false,
                    "isDev" to (isDevInitial && firebaseUser.isEmailVerified),
                    "isBanned" to false,
                    "totalFocusMinutes" to 0,
                    "completedTasksCount" to 0,
                    "completedHabitsCount" to 0,
                    "achievementsCount" to 0,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (!authEmail.isNullOrBlank()) createPayload["email"] = authEmail
                if (!authPhotoUrl.isNullOrBlank()) createPayload["photoUrl"] = authPhotoUrl

                userRef.set(createPayload).await()
                newProfile
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, userDocPath, auth)
            UserProfile(
                uid = uid,
                displayName = authDisplayName,
                username = sanitizeUsername(authDisplayName, uid),
                photoUrl = defaultPhoto,
                email = authEmail,
                isPrivate = false,
                isGuest = false,
                isDev = isDevInitial,
                isBanned = false
            )
        } finally {
            _isAuthReady.value = true
        }

        _currentUser.value = loadedProfile
        publishRecipientLookupEntries(db, loadedProfile)
        return@withLock loadedProfile
    }

    private suspend fun publishRecipientLookupEntries(db: FirebaseFirestore, profile: UserProfile) {
        if (profile.isGuest) return
        val basePayload = mutableMapOf<String, Any>(
            "uid" to profile.uid,
            "displayName" to profile.displayName,
            "username" to profile.username,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        val remotePhoto = profile.photoUrl?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        if (!remotePhoto.isNullOrBlank()) {
            basePayload["photoUrl"] = remotePhoto
        }

        val usernameKey = CitraFirebaseProvider.sha256Hex(profile.username.removePrefix("@"))
        val displayKey = CitraFirebaseProvider.sha256Hex(profile.displayName)
        try {
            db.collection("usernameLookup").document(usernameKey).set(basePayload).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "usernameLookup/$usernameKey", auth)
        }
        try {
            db.collection("displayNameLookup").document(displayKey).set(basePayload).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "displayNameLookup/$displayKey", auth)
        }
        val cleanEmail = profile.email?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        if (cleanEmail != null) {
            val emailKey = CitraFirebaseProvider.sha256Hex(cleanEmail)
            try {
                db.collection("emailLookup").document(emailKey).set(basePayload).await()
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.WRITE, "emailLookup/$emailKey", auth)
            }
        }
    }

    private fun sanitizeUsername(displayName: String, uid: String): String {
        val cleaned = displayName.lowercase()
            .replace(Regex("[^a-z0-9._]"), "")
            .take(14)
        val suffix = uid.take(4).lowercase().ifBlank { "0000" }
        return if (cleaned.length >= 3) "${cleaned}_$suffix" else "citra_${uid.take(6).lowercase()}"
    }

    private fun resolveActivity(callerContext: Context?): Activity? {
        var tempContext: Context? = callerContext ?: context
        while (tempContext is ContextWrapper) {
            if (tempContext is Activity) return tempContext
            tempContext = tempContext.baseContext
        }
        return null
    }

    /**
     * Silent auto sign-in using GetGoogleIdOption on app startup.
     * Kept in a separate GetCredentialRequest from interactive GetSignInWithGoogleOption.
     */
    suspend fun attemptAutoSignIn(callerContext: Context? = null): Boolean {
        val currentAuth = auth ?: return false
        if (currentAuth.currentUser != null) return true
        if (_isGuestMode.value || prefs.getBoolean("explicitly_signed_out", false)) return false

        val resolvedContext = callerContext ?: context
        val targetContext = resolveActivity(resolvedContext) ?: resolvedContext
        val clientId = try {
            targetContext.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            return false
        }
        if (clientId.isBlank() || clientId == "default_web_client_id") return false

        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(true)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            val cm = CredentialManager.create(targetContext)
            val result = cm.getCredential(targetContext, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                if (googleIdToken.isNotBlank()) {
                    val firebaseCred = GoogleAuthProvider.getCredential(googleIdToken, null)
                    val authResult = currentAuth.signInWithCredential(firebaseCred).await()
                    val user = authResult.user
                    if (user != null) {
                        _isGuestMode.value = false
                        prefs.edit().putBoolean("is_guest_mode", false).apply()
                        ensureAndLoadUserProfile(user)
                        return true
                    }
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Interactive Google Sign-In via Jetpack Credential Manager (GetSignInWithGoogleOption exclusively).
     */
    suspend fun signInWithGoogle(callerContext: Context? = null): Result<UserProfile> {
        val currentAuth = auth ?: return Result.failure(IllegalStateException("Firebase Auth não está inicializado."))
        val resolvedContext: Context = callerContext ?: context
        val activity = resolveActivity(resolvedContext)
        val targetContext: Context = activity ?: resolvedContext

        return try {
            val serverClientId = try {
                targetContext.getString(R.string.default_web_client_id)
            } catch (e: Exception) {
                Log.e("AuthRepository", "Falha ao obter default_web_client_id", e)
                ""
            }
            if (serverClientId.isBlank() || serverClientId == "default_web_client_id") {
                return Result.failure(IllegalStateException("Configuração do Google Sign-In pendente (default_web_client_id não encontrado)."))
            }

            val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = serverClientId).build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()

            val cm = CredentialManager.create(targetContext)
            val result = cm.getCredential(targetContext, request)
            val credential = result.credential

            val idToken: String = when {
                credential is GoogleIdTokenCredential -> credential.idToken
                credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL -> {
                    try {
                        GoogleIdTokenCredential.createFrom(credential.data).idToken
                    } catch (e: GoogleIdTokenParsingException) {
                        return Result.failure(IllegalArgumentException("Falha ao processar credencial do Google: ${e.message}", e))
                    } catch (e: Exception) {
                        return Result.failure(IllegalArgumentException("Falha ao analisar credencial do Google: ${e.message}", e))
                    }
                }
                else -> {
                    return Result.failure(IllegalArgumentException("Tipo de credencial inesperado: ${credential.type}"))
                }
            }

            if (idToken.isBlank()) {
                return Result.failure(IllegalStateException("ID Token retornado pelo Google está vazio."))
            }

            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = currentAuth.signInWithCredential(firebaseCredential).await()
            val user = authResult.user ?: throw IllegalStateException("Usuário retornado pelo Firebase foi nulo.")

            _isGuestMode.value = false
            prefs.edit()
                .putBoolean("is_guest_mode", false)
                .putBoolean("explicitly_signed_out", false)
                .apply()

            val profile = ensureAndLoadUserProfile(user)
            Result.success(profile)
        } catch (e: GetCredentialCancellationException) {
            Log.w("AuthRepository", "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            Result.failure(e)
        } catch (e: GetCredentialException) {
            Log.e("AuthRepository", "CredentialManager failed (${e.javaClass.simpleName}): ${e.message}", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e("AuthRepository", "Erro no login Google", e)
            Result.failure(e)
        }
    }

    fun updateProfile(
        displayName: String? = null,
        username: String? = null,
        photoUrl: String? = null,
        isPrivate: Boolean? = null
    ) {
        val cur = _currentUser.value ?: return
        val cleanUsername = username?.trim()?.removePrefix("@")?.lowercase()?.replace(Regex("[^a-z0-9._]"), "")
            ?.takeIf { it.isNotBlank() }
        val updated = cur.copy(
            displayName = displayName?.trim()?.takeIf { it.isNotBlank() } ?: cur.displayName,
            username = cleanUsername ?: cur.username,
            photoUrl = photoUrl ?: cur.photoUrl,
            isPrivate = isPrivate ?: cur.isPrivate
        )
        _currentUser.value = updated

        if (cur.isGuest) {
            prefs.edit().apply {
                if (displayName != null) putString("guest_name", updated.displayName)
                if (cleanUsername != null) putString("guest_username", updated.username)
                if (photoUrl != null) putString("guest_photo", updated.photoUrl)
                if (isPrivate != null) putBoolean("guest_is_private", updated.isPrivate)
            }.apply()
        } else {
            val db = firestore ?: return
            val path = "users/${cur.uid}"
            scope.launch {
                try {
                    val map = mutableMapOf<String, Any>(
                        "userId" to cur.uid,
                        "displayName" to updated.displayName,
                        "username" to updated.username,
                        "isPrivate" to updated.isPrivate,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    if (!photoUrl.isNullOrBlank() && (photoUrl.startsWith("http://") || photoUrl.startsWith("https://"))) {
                        map["photoUrl"] = photoUrl
                    }
                    db.collection("users").document(cur.uid).set(map, SetOptions.merge()).await()
                    publishRecipientLookupEntries(db, updated)
                } catch (e: Exception) {
                    handleFirestoreError(e, OperationType.UPDATE, path, auth)
                }
            }
        }
    }

    fun signOut() {
        _isGuestMode.value = false
        prefs.edit()
            .putBoolean("is_guest_mode", false)
            .putBoolean("explicitly_signed_out", true)
            .apply()
        _currentUser.value = null

        scope.launch {
            withContext(NonCancellable) {
                try {
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: Exception) {
                    Log.w("AuthRepository", "Failed to clear credential state: ${e.message}")
                } finally {
                    auth?.signOut()
                }
            }
        }
    }

    suspend fun syncLocalStats(totalMinutes: Int, tasksCount: Int, habitsCount: Int, achievementsCount: Int): Result<Unit> {
        val user = _currentUser.value ?: return Result.failure(IllegalStateException("Usuário não autenticado"))
        if (user.isGuest) return Result.failure(IllegalStateException("Modo convidado não sincroniza na nuvem"))
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore não disponível"))
        val path = "users/${user.uid}"
        return try {
            db.collection("users").document(user.uid).set(
                mapOf(
                    "userId" to user.uid,
                    "totalFocusMinutes" to totalMinutes.coerceAtLeast(0),
                    "completedTasksCount" to tasksCount.coerceAtLeast(0),
                    "completedHabitsCount" to habitsCount.coerceAtLeast(0),
                    "achievementsCount" to achievementsCount.coerceAtLeast(0),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path, auth)
            Result.failure(e)
        }
    }

    suspend fun syncAllUserDataToCloud(
        tasks: List<Task>,
        habits: List<Habit>,
        notes: List<Note>,
        categories: List<Category>,
        focusSessions: List<FocusSession>,
        achievements: List<Achievement>,
        settingsMap: Map<String, Any>,
        financesJson: String? = null
    ): Result<Unit> {
        val user = _currentUser.value ?: return Result.failure(IllegalStateException("Usuário não autenticado"))
        if (user.isGuest) return Result.failure(IllegalStateException("Modo convidado não sincroniza na nuvem"))
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore não disponível"))

        val userPath = "users/${user.uid}"
        val syncPath = "users/${user.uid}/cloudSync/current"
        return try {
            val totalFocusMins = focusSessions.sumOf { it.durationMinutes }.coerceAtLeast(0)
            val completedTasks = tasks.count { it.isCompleted }.coerceAtLeast(0)
            val completedHabits = habits.sumOf { it.currentStreak }.coerceAtLeast(0)
            val unlockedAch = achievements.count { it.isUnlocked }.coerceAtLeast(0)

            val userRef = db.collection("users").document(user.uid)
            userRef.set(
                mapOf(
                    "userId" to user.uid,
                    "displayName" to user.displayName,
                    "username" to user.username,
                    "totalFocusMinutes" to totalFocusMins,
                    "completedTasksCount" to completedTasks,
                    "completedHabitsCount" to completedHabits,
                    "achievementsCount" to unlockedAch,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()

            val backupJson = BackupManager.createBackupJson(
                tasks = tasks,
                habits = habits,
                focusSessions = focusSessions,
                categories = categories,
                achievements = achievements
            )
            val notesJson = BackupManager.exportNotesJson(notes, context)

            val cloudPayload = mutableMapOf<String, Any>(
                "userId" to user.uid,
                "backupJson" to backupJson,
                "notesJson" to notesJson,
                "settings" to settingsMap,
                "syncedAt" to System.currentTimeMillis(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!financesJson.isNullOrBlank()) {
                cloudPayload["financesJson"] = financesJson
            }

            userRef.collection("cloudSync").document("current").set(
                cloudPayload,
                SetOptions.merge()
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, syncPath, auth)
            Result.failure(e)
        }
    }

    suspend fun fetchCloudUserData(): CloudUserData? {
        val user = _currentUser.value ?: return null
        if (user.isGuest) return null
        val db = firestore ?: return null
        val path = "users/${user.uid}/cloudSync/current"
        return try {
            val doc = db.collection("users").document(user.uid)
                .collection("cloudSync").document("current").get().await()
            if (doc.exists()) {
                val backupJson = doc.getString("backupJson")
                val notesJson = doc.getString("notesJson")
                val financesJson = doc.getString("financesJson")
                @Suppress("UNCHECKED_CAST")
                val settings = doc.get("settings") as? Map<String, Any>
                CloudUserData(backupJson, notesJson, settings, financesJson)
            } else null
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path, auth)
            null
        }
    }

    suspend fun deleteCloudBackup(): Boolean {
        val user = _currentUser.value ?: return false
        if (user.isGuest) return false
        val db = firestore ?: return false
        val path = "users/${user.uid}/cloudSync/current"
        return try {
            db.collection("users").document(user.uid)
                .collection("cloudSync").document("current").delete().await()
            true
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path, auth)
            false
        }
    }

    suspend fun updateCloudNotes(notes: List<Note>): Boolean {
        val user = _currentUser.value ?: return false
        if (user.isGuest) return false
        val db = firestore ?: return false
        val path = "users/${user.uid}/cloudSync/current"
        return try {
            val notesJson = BackupManager.exportNotesJson(notes, context)
            db.collection("users").document(user.uid)
                .collection("cloudSync").document("current")
                .set(
                    mapOf(
                        "userId" to user.uid,
                        "notesJson" to notesJson,
                        "syncedAt" to System.currentTimeMillis(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                ).await()
            true
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path, auth)
            false
        }
    }

    suspend fun updateCloudTasksAndHabits(tasks: List<Task>, habits: List<Habit>): Boolean {
        val user = _currentUser.value ?: return false
        if (user.isGuest) return false
        val db = firestore ?: return false
        val path = "users/${user.uid}/cloudSync/current"
        return try {
            val backupJson = BackupManager.createBackupJson(
                tasks = tasks,
                habits = habits,
                focusSessions = emptyList(),
                categories = emptyList(),
                achievements = emptyList()
            )
            db.collection("users").document(user.uid)
                .collection("cloudSync").document("current")
                .set(
                    mapOf(
                        "userId" to user.uid,
                        "backupJson" to backupJson,
                        "syncedAt" to System.currentTimeMillis(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                ).await()
            true
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path, auth)
            false
        }
    }

    suspend fun syncFinancesToCloud(financesJson: String): Boolean {
        val user = _currentUser.value ?: return false
        if (user.isGuest) return false
        val db = firestore ?: return false
        val path = "users/${user.uid}/cloudSync/finances"
        return try {
            db.collection("users").document(user.uid)
                .collection("cloudSync").document("finances")
                .set(
                    mapOf(
                        "userId" to user.uid,
                        "financesJson" to financesJson,
                        "syncedAt" to System.currentTimeMillis(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            true
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path, auth)
            false
        }
    }

    suspend fun fetchCloudFinances(): String? {
        val user = _currentUser.value ?: return null
        if (user.isGuest) return null
        val db = firestore ?: return null
        val path = "users/${user.uid}/cloudSync/finances"
        return try {
            val doc = db.collection("users").document(user.uid)
                .collection("cloudSync").document("finances").get().await()
            if (doc.exists()) doc.getString("financesJson") else null
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path, auth)
            null
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: AuthRepository? = null

        fun ensureFirebaseInitialized(context: Context): FirebaseApp? =
            CitraFirebaseProvider.ensureFirebaseInitialized(context)

        fun getInstance(context: Context): AuthRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AuthRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}

data class CloudUserData(
    val backupJson: String?,
    val notesJson: String?,
    val settings: Map<String, Any>?,
    val financesJson: String? = null
)
