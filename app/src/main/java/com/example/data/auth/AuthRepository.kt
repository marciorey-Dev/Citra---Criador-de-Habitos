package com.example.data.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.example.data.backup.BackupManager
import com.example.data.model.Achievement
import com.example.data.model.Category
import com.example.data.model.FocusSession
import com.example.data.model.Habit
import com.example.data.inbox.InboxRepository
import com.example.data.model.Note
import com.example.data.model.Task

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

class AuthRepository(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("citra_prefs", Context.MODE_PRIVATE)

    private val auth: FirebaseAuth? by lazy {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "Firebase Auth could not be initialized", e)
            null
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
                    android.util.Log.w("AuthRepository", "Fallback to default db after error with $dbId", e)
                    FirebaseFirestore.getInstance(app)
                }
            } else {
                FirebaseFirestore.getInstance(app)
            }
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "Firebase Firestore could not be initialized", e)
            null
        }
    }

    private val credentialManager = CredentialManager.create(context)
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _currentUser = MutableStateFlow<UserProfile?>(null)
    val currentUser: StateFlow<UserProfile?> = _currentUser.asStateFlow()

    private val _isGuestMode = MutableStateFlow(prefs.getBoolean("is_guest_mode", false))
    val isGuestMode: StateFlow<Boolean> = _isGuestMode.asStateFlow()

    private val _isAuthReady = MutableStateFlow(false)
    val isAuthReady: StateFlow<Boolean> = _isAuthReady.asStateFlow()

    init {
        auth?.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                _isGuestMode.value = false
                prefs.edit().putBoolean("is_guest_mode", false).apply()
                val email = user.email
                loadUserProfile(user.uid, user.displayName, user.photoUrl?.toString(), email)
            } else if (_isGuestMode.value) {
                loadGuestProfile()
            } else {
                _currentUser.value = null
                _isAuthReady.value = true
            }
        }
        if (auth == null) {
            if (_isGuestMode.value) {
                loadGuestProfile()
            } else {
                _isAuthReady.value = true
            }
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
        val isDev = prefs.getBoolean("guest_is_dev", false) || InboxRepository.isDeveloper(null)
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

    private fun loadUserProfile(uid: String, authDisplayName: String?, authPhotoUrl: String?, authEmail: String? = null) {
        scope.launch {
            val localAvatar = java.io.File(context.filesDir, "avatars/user_${uid}.jpg")
            val defaultPhoto = if (localAvatar.exists()) localAvatar.absolutePath else authPhotoUrl
            val isDevInitial = InboxRepository.isDeveloper(authEmail, uid)
            try {
                val currentFirestore = firestore
                if (currentFirestore == null) {
                    _currentUser.value = UserProfile(
                        uid = uid,
                        displayName = authDisplayName ?: "Usuário Citra",
                        username = uid.take(6),
                        photoUrl = defaultPhoto,
                        email = authEmail,
                        isPrivate = false,
                        isGuest = false,
                        isDev = isDevInitial,
                        isBanned = false
                    )
                    _isAuthReady.value = true
                    return@launch
                }

                val doc = currentFirestore.collection("users").document(uid).get().await()
                if (doc.exists()) {
                    val firestorePhoto = doc.getString("photoUrl")
                    val effectivePhoto = if (localAvatar.exists()) localAvatar.absolutePath else (firestorePhoto ?: authPhotoUrl)
                    val isDev = doc.getBoolean("isDev") ?: isDevInitial
                    val isBanned = doc.getBoolean("isBanned") ?: false
                    val mail = doc.getString("email") ?: authEmail
                    val profile = UserProfile(
                        uid = uid,
                        displayName = doc.getString("displayName") ?: authDisplayName ?: "Usuário Citra",
                        username = (doc.getString("username") ?: uid.take(6)).removePrefix("@"),
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
                    _currentUser.value = profile
                } else {
                    val autoUsername = (authDisplayName?.replace(" ", "")?.lowercase()?.take(10) ?: uid.take(6))
                    val newProfile = UserProfile(
                        uid = uid,
                        displayName = authDisplayName ?: "Usuário Citra",
                        username = autoUsername,
                        photoUrl = defaultPhoto,
                        email = authEmail,
                        isPrivate = false,
                        isGuest = false,
                        isDev = isDevInitial,
                        isBanned = false
                    )
                    currentFirestore.collection("users").document(uid).set(
                        mapOf(
                            "displayName" to newProfile.displayName,
                            "username" to newProfile.username,
                            "email" to (authEmail ?: ""),
                            "photoUrl" to newProfile.photoUrl,
                            "isPrivate" to newProfile.isPrivate,
                            "isDev" to newProfile.isDev,
                            "isBanned" to newProfile.isBanned,
                            "totalFocusMinutes" to 0,
                            "completedTasksCount" to 0,
                            "completedHabitsCount" to 0,
                            "achievementsCount" to 0
                        )
                    ).await()
                    _currentUser.value = newProfile
                }
            } catch (_: Exception) {
                _currentUser.value = UserProfile(
                    uid = uid,
                    displayName = authDisplayName ?: "Usuário Citra",
                    username = uid.take(6),
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
        }
    }

    suspend fun signInWithGoogle(callerContext: Context? = null): Result<UserProfile> {
        val currentAuth = auth ?: return Result.failure(IllegalStateException("Firebase Auth não está inicializado."))
        return try {
            val resolvedContext: Context = callerContext ?: context
            var activity: Activity? = null
            var tempContext: Context? = resolvedContext
            while (tempContext is ContextWrapper) {
                if (tempContext is Activity) {
                    activity = tempContext
                    break
                }
                tempContext = tempContext.baseContext
            }

            val targetContext: Context = activity ?: resolvedContext
            val serverClientId = try {
                val resId = targetContext.resources.getIdentifier("default_web_client_id", "string", targetContext.packageName)
                if (resId != 0) {
                    val str = targetContext.getString(resId)
                    if (str.isNotBlank() && str != "default_web_client_id") str else "281497817953-kq1higalts6knpb831l7ffrlmmfs9qeu.apps.googleusercontent.com"
                } else {
                    "281497817953-kq1higalts6knpb831l7ffrlmmfs9qeu.apps.googleusercontent.com"
                }
            } catch (_: Exception) {
                "281497817953-kq1higalts6knpb831l7ffrlmmfs9qeu.apps.googleusercontent.com"
            }
            if (serverClientId.isBlank()) {
                return Result.failure(IllegalStateException("Configuração do Google Sign-In pendente (Client ID não configurado)."))
            }
            val signInOption = GetSignInWithGoogleOption.Builder(serverClientId).build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()

            val cm = CredentialManager.create(targetContext)
            val result = cm.getCredential(targetContext, request)
            val credential = result.credential

            val idToken: String = when {
                credential is GoogleIdTokenCredential -> {
                    credential.idToken
                }
                credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL -> {
                    try {
                        GoogleIdTokenCredential.createFrom(credential.data).idToken
                    } catch (e: GoogleIdTokenParsingException) {
                        return Result.failure(IllegalArgumentException("Falha ao processar credencial do Google: ${e.message}", e))
                    } catch (e: Exception) {
                        return Result.failure(IllegalArgumentException("Falha ao analisar credencial do Google: ${e.message}", e))
                    }
                }
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL -> {
                    if (credential is CustomCredential) {
                        try {
                            GoogleIdTokenCredential.createFrom(credential.data).idToken
                        } catch (e: Exception) {
                            return Result.failure(IllegalArgumentException("Falha ao analisar Google ID Token: ${e.message}", e))
                        }
                    } else {
                        return Result.failure(IllegalArgumentException("Tipo de credencial incompatível: ${credential.type}"))
                    }
                }
                else -> {
                    return Result.failure(IllegalArgumentException("Unexpected credential type: ${credential.type}"))
                }
            }

            if (idToken.isBlank()) {
                return Result.failure(IllegalStateException("ID Token retornado pelo Google está vazio."))
            }

            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = currentAuth.signInWithCredential(firebaseCredential).await()
            val user = authResult.user ?: throw IllegalStateException("Firebase user was null")

            _isGuestMode.value = false
            prefs.edit().putBoolean("is_guest_mode", false).apply()

            loadUserProfile(user.uid, user.displayName, user.photoUrl?.toString())
            Result.success(
                _currentUser.value ?: UserProfile(
                    uid = user.uid,
                    displayName = user.displayName ?: "Usuário Citra",
                    username = user.uid.take(6),
                    photoUrl = user.photoUrl?.toString(),
                    isPrivate = false,
                    isGuest = false
                )
            )
        } catch (e: GetCredentialCancellationException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signInWithEmailOrUsername(identifier: String, pass: String): Result<UserProfile> {
        val trimmedIdentifier = identifier.trim()
        val currentAuth = auth ?: return Result.failure(IllegalStateException("Firebase Auth não está inicializado."))
        return try {
            val email = if (trimmedIdentifier.contains("@") && trimmedIdentifier.contains(".")) {
                trimmedIdentifier
            } else {
                "${trimmedIdentifier.removePrefix("@").lowercase()}@citra.app"
            }
            val authResult = currentAuth.signInWithEmailAndPassword(email, pass).await()
            val user = authResult.user ?: throw IllegalStateException("User null")
            _isGuestMode.value = false
            prefs.edit().apply {
                putBoolean("is_guest_mode", false)
            }.apply()
            loadUserProfile(user.uid, user.displayName, user.photoUrl?.toString())
            Result.success(_currentUser.value ?: UserProfile(
                uid = user.uid,
                displayName = user.displayName ?: "Usuário Citra",
                username = user.uid.take(6),
                isPrivate = false,
                isGuest = false
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createAccountWithEmailOrUsername(
        name: String,
        username: String,
        pass: String,
        isPrivate: Boolean = false
    ): Result<UserProfile> {
        val currentAuth = auth ?: return Result.failure(IllegalStateException("Firebase Auth não está inicializado."))
        return try {
            val raw = username.trim()
            val isEmail = raw.contains("@") && raw.contains(".")
            val email = if (isEmail) raw else "${raw.removePrefix("@").lowercase()}@citra.app"
            val cleanUsername = if (isEmail) raw.substringBefore("@") else raw.removePrefix("@")
            val authResult = currentAuth.createUserWithEmailAndPassword(email, pass).await()
            val user = authResult.user ?: throw IllegalStateException("User null")

            try {
                user.updateProfile(
                    UserProfileChangeRequest.Builder()
                        .setDisplayName(name)
                        .build()
                ).await()
            } catch (_: Exception) {}

            val profile = UserProfile(
                uid = user.uid,
                displayName = name,
                username = cleanUsername,
                photoUrl = null,
                isPrivate = isPrivate,
                isGuest = false
            )

            val currentFirestore = firestore
            if (currentFirestore != null) {
                try {
                    currentFirestore.collection("users").document(user.uid).set(
                        mapOf(
                            "displayName" to name,
                            "username" to cleanUsername,
                            "photoUrl" to null,
                            "isPrivate" to isPrivate,
                            "totalFocusMinutes" to 0,
                            "completedTasksCount" to 0,
                            "completedHabitsCount" to 0,
                            "achievementsCount" to 0
                        )
                    ).await()
                } catch (e: Exception) {
                    android.util.Log.w("AuthRepository", "Failed saving user document to Firestore", e)
                }
            }

            _isGuestMode.value = false
            prefs.edit().apply {
                putBoolean("is_guest_mode", false)
            }.apply()
            _currentUser.value = profile
            Result.success(profile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun updateProfile(displayName: String? = null, photoUrl: String? = null, isPrivate: Boolean? = null) {
        val cur = _currentUser.value ?: return
        val updated = cur.copy(
            displayName = displayName ?: cur.displayName,
            photoUrl = photoUrl ?: cur.photoUrl,
            isPrivate = isPrivate ?: cur.isPrivate
        )
        _currentUser.value = updated

        if (cur.isGuest) {
            prefs.edit().apply {
                if (displayName != null) putString("guest_name", displayName)
                if (photoUrl != null) putString("guest_photo", photoUrl)
                if (isPrivate != null) putBoolean("guest_is_private", isPrivate)
            }.apply()
        } else {
            val currentFirestore = firestore ?: return
            scope.launch {
                try {
                    val map = mutableMapOf<String, Any>()
                    if (displayName != null) map["displayName"] = displayName
                    if (photoUrl != null) map["photoUrl"] = photoUrl
                    if (isPrivate != null) map["isPrivate"] = isPrivate
                    currentFirestore.collection("users").document(cur.uid).set(map, SetOptions.merge())
                } catch (_: Exception) {}
            }
        }
    }

    fun signOut() {
        auth?.signOut()
        scope.launch {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (_: Exception) {}
        }
        _isGuestMode.value = false
        prefs.edit().apply {
            putBoolean("is_guest_mode", false)
        }.apply()
        _currentUser.value = null
    }

    suspend fun syncLocalStats(totalMinutes: Int, tasksCount: Int, habitsCount: Int, achievementsCount: Int) {
        val user = _currentUser.value ?: return
        if (user.isGuest) return
        val currentFirestore = firestore ?: return
        try {
            currentFirestore.collection("users").document(user.uid).set(
                mapOf(
                    "totalFocusMinutes" to totalMinutes,
                    "completedTasksCount" to tasksCount,
                    "completedHabitsCount" to habitsCount,
                    "achievementsCount" to achievementsCount
                ),
                SetOptions.merge()
            ).await()
        } catch (_: Exception) {}
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
    ) {
        val user = _currentUser.value ?: return
        if (user.isGuest) return
        val currentFirestore = firestore ?: return
        try {
            val totalFocusMins = focusSessions.sumOf { it.durationMinutes }
            val completedTasks = tasks.count { it.isCompleted }
            val completedHabits = habits.sumOf { it.currentStreak }
            val unlockedAch = achievements.count { it.isUnlocked }

            val userRef = currentFirestore.collection("users").document(user.uid)
            userRef.set(
                mapOf(
                    "totalFocusMinutes" to totalFocusMins,
                    "completedTasksCount" to completedTasks,
                    "completedHabitsCount" to completedHabits,
                    "achievementsCount" to unlockedAch
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
                "backupJson" to backupJson,
                "notesJson" to notesJson,
                "settings" to settingsMap,
                "syncedAt" to System.currentTimeMillis()
            )
            if (!financesJson.isNullOrBlank()) {
                cloudPayload["financesJson"] = financesJson
            }

            userRef.collection("cloudSync").document("current").set(
                cloudPayload,
                SetOptions.merge()
            ).await()
        } catch (_: Exception) {}
    }

    suspend fun fetchCloudUserData(): CloudUserData? {
        val user = _currentUser.value ?: return null
        if (user.isGuest) return null
        val currentFirestore = firestore ?: return null
        return try {
            val doc = currentFirestore.collection("users").document(user.uid)
                .collection("cloudSync").document("current").get().await()
            if (doc.exists()) {
                val backupJson = doc.getString("backupJson")
                val notesJson = doc.getString("notesJson")
                val financesJson = doc.getString("financesJson")
                @Suppress("UNCHECKED_CAST")
                val settings = doc.get("settings") as? Map<String, Any>
                CloudUserData(backupJson, notesJson, settings, financesJson)
            } else null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun deleteCloudBackup(): Boolean {
        val user = _currentUser.value ?: return false
        if (user.isGuest) return false
        val currentFirestore = firestore ?: return false
        return try {
            currentFirestore.collection("users").document(user.uid)
                .collection("cloudSync").document("current").delete().await()
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun updateCloudNotes(notes: List<Note>): Boolean {
        val user = _currentUser.value ?: return false
        if (user.isGuest) return false
        val currentFirestore = firestore ?: return false
        return try {
            val notesJson = BackupManager.exportNotesJson(notes, context)
            currentFirestore.collection("users").document(user.uid)
                .collection("cloudSync").document("current")
                .set(mapOf("notesJson" to notesJson), com.google.firebase.firestore.SetOptions.merge()).await()
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun updateCloudTasksAndHabits(tasks: List<Task>, habits: List<Habit>): Boolean {
        val user = _currentUser.value ?: return false
        if (user.isGuest) return false
        val currentFirestore = firestore ?: return false
        return try {
            val backupJson = BackupManager.createBackupJson(
                tasks = tasks,
                habits = habits,
                focusSessions = emptyList(),
                categories = emptyList(),
                achievements = emptyList()
            )
            currentFirestore.collection("users").document(user.uid)
                .collection("cloudSync").document("current")
                .set(mapOf("backupJson" to backupJson), com.google.firebase.firestore.SetOptions.merge()).await()
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun syncFinancesToCloud(financesJson: String): Boolean {
        val user = _currentUser.value ?: return false
        if (user.isGuest) return false
        val currentFirestore = firestore ?: return false
        return try {
            currentFirestore.collection("users").document(user.uid)
                .collection("cloudSync").document("finances")
                .set(
                    mapOf(
                        "financesJson" to financesJson,
                        "syncedAt" to System.currentTimeMillis()
                    )
                ).await()
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun fetchCloudFinances(): String? {
        val user = _currentUser.value ?: return null
        if (user.isGuest) return null
        val currentFirestore = firestore ?: return null
        return try {
            val doc = currentFirestore.collection("users").document(user.uid)
                .collection("cloudSync").document("finances").get().await()
            if (doc.exists()) doc.getString("financesJson") else null
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: AuthRepository? = null

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
