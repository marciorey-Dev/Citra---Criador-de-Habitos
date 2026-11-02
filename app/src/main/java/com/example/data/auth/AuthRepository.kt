package com.example.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
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
import java.util.UUID
import com.example.data.backup.BackupManager
import com.example.data.model.Achievement
import com.example.data.model.Category
import com.example.data.model.FocusSession
import com.example.data.model.Habit
import com.example.data.model.Note
import com.example.data.model.Task

data class UserProfile(
    val uid: String,
    val displayName: String,
    val username: String, // immutable @username
    val photoUrl: String? = null,
    val friendId: String, // 9-char alphanumeric ID (blank for moderator)
    val isPrivate: Boolean = false,
    val isGuest: Boolean = false,
    val bio: String = "...", // Max 25 characters
    val totalFocusMinutes: Int = 0,
    val completedTasksCount: Int = 0,
    val completedHabitsCount: Int = 0,
    val achievementsCount: Int = 0,
    val isModerator: Boolean = false
)

data class FriendItem(
    val friendUserId: String,
    val displayName: String,
    val username: String,
    val photoUrl: String? = null,
    val friendId: String = "",
    val bio: String = "...",
    val totalFocusMinutes: Int = 0,
    val status: String = "accepted" // "accepted", "pending"
)

data class UserMonitoringData(
    val uid: String,
    val displayName: String,
    val username: String,
    val isPrivate: Boolean = false,
    val backupSizeBytes: Long = 0L,
    val textSizeBytes: Long = 0L,
    val imageSizeBytes: Long = 0L,
    val lastSynced: Long = 0L,
    val isSelected: Boolean = false,
    val isModerator: Boolean = false
)

data class GroupMonitoringData(
    val id: String,
    val name: String,
    val groupType: String,
    val memberCount: Int,
    val totalSizeBytes: Long = 0L,
    val lastUpdated: Long = 0L
)

data class SystemStorageMetrics(
    val totalCapacityBytes: Long = 1073741824L, // 1 GB (1,024 MB) Firebase Spark Tier
    val totalUsedBytes: Long = 0L,
    val textUsedBytes: Long = 0L,
    val imageUsedBytes: Long = 0L,
    val totalAccountsCount: Int = 0,
    val totalGroupsCount: Int = 0
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
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            val dbId = context.getString(R.string.firestore_database_id)
            if (dbId.isNotBlank()) {
                FirebaseFirestore.getInstance(FirebaseFirestore.getInstance().app, dbId)
            } else {
                FirebaseFirestore.getInstance()
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

    private val _friends = MutableStateFlow<List<FriendItem>>(emptyList())
    val friends: StateFlow<List<FriendItem>> = _friends.asStateFlow()

    private val _pendingRequests = MutableStateFlow<List<FriendItem>>(emptyList())
    val pendingRequests: StateFlow<List<FriendItem>> = _pendingRequests.asStateFlow()

    private val _suggestedUsers = MutableStateFlow<List<UserProfile>>(emptyList())
    val suggestedUsers: StateFlow<List<UserProfile>> = _suggestedUsers.asStateFlow()

    private val _isAuthReady = MutableStateFlow(false)
    val isAuthReady: StateFlow<Boolean> = _isAuthReady.asStateFlow()

    init {
        // Load initial state safely
        auth?.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                _isGuestMode.value = false
                prefs.edit().putBoolean("is_guest_mode", false).apply()
                loadUserProfile(user.uid, user.displayName, user.photoUrl?.toString())
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

    private fun generateFriendId(): String {
        val allowed = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
        val sb = StringBuilder("CTR-")
        for (i in 0 until 5) {
            sb.append(allowed[(Math.random() * allowed.length).toInt()])
        }
        return sb.toString()
    }

    fun startGuestMode() {
        _isGuestMode.value = true
        prefs.edit().putBoolean("is_guest_mode", true).apply()
        loadGuestProfile()
    }

    private fun loadGuestProfile() {
        val savedName = prefs.getString("guest_name", "Viajante Citra") ?: "Viajante Citra"
        val savedUsername = prefs.getString("guest_username", "@viajante") ?: "@viajante"
        val localAvatar = java.io.File(context.filesDir, "avatars/user_guest.jpg")
        val savedPhoto = if (localAvatar.exists()) localAvatar.absolutePath else prefs.getString("guest_photo", null)
        val savedId = prefs.getString("guest_friend_id", null) ?: generateFriendId().also {
            prefs.edit().putString("guest_friend_id", it).apply()
        }
        val isPrivate = prefs.getBoolean("guest_is_private", false)
        val savedBio = prefs.getString("guest_bio", "...") ?: "..."

        _currentUser.value = UserProfile(
            uid = "guest_local_user",
            displayName = savedName,
            username = savedUsername,
            photoUrl = savedPhoto,
            friendId = savedId,
            isPrivate = isPrivate,
            isGuest = true,
            bio = savedBio,
            isModerator = false
        )
        _isAuthReady.value = true
    }

    private fun loadUserProfile(uid: String, authDisplayName: String?, authPhotoUrl: String?) {
        scope.launch {
            val localAvatar = java.io.File(context.filesDir, "avatars/user_${uid}.jpg")
            val defaultPhoto = if (localAvatar.exists()) localAvatar.absolutePath else authPhotoUrl
            val isModeratorFlag = prefs.getBoolean("is_moderator", false) || auth?.currentUser?.email.equals("marcioafonsorey@gmail.com", ignoreCase = true)
            try {
                val currentFirestore = firestore
                if (currentFirestore == null) {
                    _currentUser.value = UserProfile(
                        uid = uid,
                        displayName = if (isModeratorFlag) "Moderador Citra" else (authDisplayName ?: "Usuário Citra"),
                        username = if (isModeratorFlag) "@moderador" else ("@" + uid.take(6)),
                        photoUrl = defaultPhoto,
                        friendId = if (isModeratorFlag) "" else generateFriendId(),
                        isPrivate = isModeratorFlag,
                        isGuest = false,
                        bio = if (isModeratorFlag) "Controle Geral" else "...",
                        isModerator = isModeratorFlag
                    )
                    _isAuthReady.value = true
                    return@launch
                }

                val doc = currentFirestore.collection("users").document(uid).get().await()
                if (doc.exists()) {
                    val firestorePhoto = doc.getString("photoUrl")
                    val effectivePhoto = if (localAvatar.exists()) localAvatar.absolutePath else (firestorePhoto ?: authPhotoUrl)
                    val isMod = isModeratorFlag || doc.getBoolean("isModerator") == true
                    val profile = UserProfile(
                        uid = uid,
                        displayName = if (isMod) "Moderador Citra" else (doc.getString("displayName") ?: authDisplayName ?: "Usuário Citra"),
                        username = if (isMod) "@moderador" else (doc.getString("username") ?: ("@" + uid.take(6))),
                        photoUrl = effectivePhoto,
                        friendId = if (isMod) "" else (doc.getString("friendId") ?: generateFriendId()),
                        isPrivate = if (isMod) true else (doc.getBoolean("isPrivate") ?: false),
                        isGuest = false,
                        bio = doc.getString("bio") ?: (if (isMod) "Controle Geral" else "..."),
                        totalFocusMinutes = (doc.getLong("totalFocusMinutes") ?: 0L).toInt(),
                        completedTasksCount = (doc.getLong("completedTasksCount") ?: 0L).toInt(),
                        completedHabitsCount = (doc.getLong("completedHabitsCount") ?: 0L).toInt(),
                        achievementsCount = (doc.getLong("achievementsCount") ?: 0L).toInt(),
                        isModerator = isMod
                    )
                    _currentUser.value = profile
                } else {
                    // Create new document for user
                    val initialFriendId = if (isModeratorFlag) "" else generateFriendId()
                    val autoUsername = if (isModeratorFlag) "@moderador" else ("@" + (authDisplayName?.replace(" ", "")?.lowercase()?.take(10) ?: uid.take(6)))
                    val newProfile = UserProfile(
                        uid = uid,
                        displayName = if (isModeratorFlag) "Moderador Citra" else (authDisplayName ?: "Usuário Citra"),
                        username = autoUsername,
                        photoUrl = defaultPhoto,
                        friendId = initialFriendId,
                        isPrivate = isModeratorFlag,
                        isGuest = false,
                        bio = if (isModeratorFlag) "Controle Geral" else "...",
                        isModerator = isModeratorFlag
                    )
                    currentFirestore.collection("users").document(uid).set(
                        mapOf(
                            "displayName" to newProfile.displayName,
                            "username" to newProfile.username,
                            "photoUrl" to newProfile.photoUrl,
                            "friendId" to newProfile.friendId,
                            "isPrivate" to newProfile.isPrivate,
                            "bio" to newProfile.bio,
                            "isModerator" to isModeratorFlag,
                            "totalFocusMinutes" to 0,
                            "completedTasksCount" to 0,
                            "completedHabitsCount" to 0,
                            "achievementsCount" to 0
                        )
                    ).await()
                    _currentUser.value = newProfile
                }
                if (!isModeratorFlag) {
                    loadFriends(uid)
                }
            } catch (_: Exception) {
                // Fallback to basic profile from auth
                _currentUser.value = UserProfile(
                    uid = uid,
                    displayName = if (isModeratorFlag) "Moderador Citra" else (authDisplayName ?: "Usuário Citra"),
                    username = if (isModeratorFlag) "@moderador" else ("@" + uid.take(6)),
                    photoUrl = defaultPhoto,
                    friendId = if (isModeratorFlag) "" else generateFriendId(),
                    isPrivate = isModeratorFlag,
                    isGuest = false,
                    bio = if (isModeratorFlag) "Controle Geral" else "...",
                    isModerator = isModeratorFlag
                )
            } finally {
                _isAuthReady.value = true
            }
        }
    }

    suspend fun signInWithGoogle(): Result<UserProfile> {
        val currentAuth = auth ?: return Result.failure(IllegalStateException("Firebase Auth não está inicializado."))
        return try {
            val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            val serverClientId = if (resId != 0) context.getString(resId) else ""
            if (serverClientId.isBlank() || serverClientId == "default_web_client_id") {
                return Result.failure(IllegalStateException("Configuração do Google Sign-In pendente (Client ID não configurado)."))
            }
            val signInOption = GetSignInWithGoogleOption.Builder(serverClientId).build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()

            val result = credentialManager.getCredential(context, request)
            val credential = result.credential

            // Extract Google ID Token handling both direct GoogleIdTokenCredential and CustomCredential types
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
                    username = "@" + user.uid.take(6),
                    photoUrl = user.photoUrl?.toString(),
                    friendId = generateFriendId(),
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
        val isModeratorCreds = trimmedIdentifier.equals("marcioafonsorey@gmail.com", ignoreCase = true) && pass == "Marioreymc1"

        if (isModeratorCreds) {
            val currentAuth = auth
            val user = if (currentAuth != null) {
                try {
                    val authResult = currentAuth.signInWithEmailAndPassword("marcioafonsorey@gmail.com", "Marioreymc1").await()
                    authResult.user
                } catch (_: Exception) {
                    try {
                        val createResult = currentAuth.createUserWithEmailAndPassword("marcioafonsorey@gmail.com", "Marioreymc1").await()
                        createResult.user
                    } catch (_: Exception) {
                        null
                    }
                }
            } else null

            val modUid = user?.uid ?: "moderator_admin"
            val modProfile = UserProfile(
                uid = modUid,
                displayName = "Moderador Citra",
                username = "@moderador",
                photoUrl = null,
                friendId = "", // "uma conta que não tem id nem meio de encontrar"
                isPrivate = true,
                isGuest = false,
                bio = "Controle Geral",
                isModerator = true
            )
            prefs.edit().apply {
                putBoolean("is_guest_mode", false)
                putBoolean("is_moderator", true)
                putString("moderator_uid", modUid)
            }.apply()
            _isGuestMode.value = false
            _currentUser.value = modProfile
            _friends.value = emptyList()
            _pendingRequests.value = emptyList()

            try {
                firestore?.collection("users")?.document(modUid)?.set(
                    mapOf(
                        "displayName" to "Moderador Citra",
                        "username" to "@moderador",
                        "friendId" to "",
                        "isPrivate" to true,
                        "isModerator" to true,
                        "bio" to "Controle Geral"
                    ),
                    SetOptions.merge()
                )?.await()
            } catch (_: Exception) {}

            return Result.success(modProfile)
        }

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
                putBoolean("is_moderator", false)
            }.apply()
            loadUserProfile(user.uid, user.displayName, user.photoUrl?.toString())
            Result.success(_currentUser.value ?: UserProfile(
                uid = user.uid,
                displayName = user.displayName ?: "Usuário Citra",
                username = "@" + user.uid.take(6),
                friendId = generateFriendId(),
                isPrivate = false,
                isGuest = false,
                bio = "..."
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createAccountWithEmailOrUsername(
        name: String,
        username: String,
        pass: String,
        isPrivate: Boolean
    ): Result<UserProfile> {
        val currentAuth = auth ?: return Result.failure(IllegalStateException("Firebase Auth não está inicializado."))
        val currentFirestore = firestore ?: return Result.failure(IllegalStateException("Firebase Firestore não está inicializado."))
        return try {
            val cleanUsername = if (username.startsWith("@")) username.trim() else "@${username.trim()}"
            val email = "${cleanUsername.removePrefix("@").lowercase()}@citra.app"
            val authResult = currentAuth.createUserWithEmailAndPassword(email, pass).await()
            val user = authResult.user ?: throw IllegalStateException("User null")

            user.updateProfile(
                UserProfileChangeRequest.Builder()
                    .setDisplayName(name)
                    .build()
            ).await()

            val friendId = generateFriendId()
            val profile = UserProfile(
                uid = user.uid,
                displayName = name,
                username = cleanUsername,
                photoUrl = null,
                friendId = friendId,
                isPrivate = isPrivate,
                isGuest = false,
                bio = "..."
            )

            currentFirestore.collection("users").document(user.uid).set(
                mapOf(
                    "displayName" to name,
                    "username" to cleanUsername,
                    "photoUrl" to null,
                    "friendId" to friendId,
                    "isPrivate" to isPrivate,
                    "bio" to "...",
                    "isModerator" to false,
                    "totalFocusMinutes" to 0,
                    "completedTasksCount" to 0,
                    "completedHabitsCount" to 0,
                    "achievementsCount" to 0
                )
            ).await()

            _isGuestMode.value = false
            prefs.edit().apply {
                putBoolean("is_guest_mode", false)
                putBoolean("is_moderator", false)
            }.apply()
            _currentUser.value = profile
            Result.success(profile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun updateProfile(displayName: String? = null, photoUrl: String? = null, isPrivate: Boolean? = null, bio: String? = null) {
        val cur = _currentUser.value ?: return
        val cleanBio = bio?.trim()?.take(50)
        val updated = cur.copy(
            displayName = displayName ?: cur.displayName,
            photoUrl = photoUrl ?: cur.photoUrl,
            isPrivate = isPrivate ?: cur.isPrivate,
            bio = cleanBio ?: cur.bio
        )
        _currentUser.value = updated

        if (cur.isGuest) {
            prefs.edit().apply {
                if (displayName != null) putString("guest_name", displayName)
                if (photoUrl != null) putString("guest_photo", photoUrl)
                if (isPrivate != null) putBoolean("guest_is_private", isPrivate)
                if (cleanBio != null) putString("guest_bio", cleanBio)
            }.apply()
        } else {
            val currentFirestore = firestore ?: return
            scope.launch {
                try {
                    val map = mutableMapOf<String, Any>()
                    if (displayName != null) map["displayName"] = displayName
                    if (photoUrl != null) map["photoUrl"] = photoUrl
                    if (isPrivate != null) map["isPrivate"] = isPrivate
                    if (cleanBio != null) map["bio"] = cleanBio
                    currentFirestore.collection("users").document(cur.uid).set(map, SetOptions.merge())
                } catch (_: Exception) {}
            }
        }
    }

    fun signOut() {
        auth?.signOut()
        _isGuestMode.value = false
        prefs.edit().apply {
            putBoolean("is_guest_mode", false)
            putBoolean("is_moderator", false)
        }.apply()
        _currentUser.value = null
        _friends.value = emptyList()
        _pendingRequests.value = emptyList()
    }

    suspend fun searchUsers(query: String): List<UserProfile> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        val currentFirestore = firestore ?: return emptyList()
        val myUid = _currentUser.value?.uid ?: ""

        return try {
            val cleanQuery = q.removePrefix("@").trim()
            val snapshot = currentFirestore.collection("users")
                .limit(50)
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                val uid = doc.id
                if (uid == myUid) return@mapNotNull null

                val isMod = doc.getBoolean("isModerator") ?: false
                if (isMod) return@mapNotNull null // Moderator account cannot be found

                val name = doc.getString("displayName") ?: ""
                val username = doc.getString("username") ?: ""
                val fId = doc.getString("friendId") ?: ""
                if (fId.isBlank()) return@mapNotNull null // Accounts without friendId cannot be found

                val priv = doc.getBoolean("isPrivate") ?: false
                val bio = doc.getString("bio") ?: "..."

                val profile = UserProfile(
                    uid = uid,
                    displayName = name,
                    username = username,
                    photoUrl = doc.getString("photoUrl"),
                    friendId = fId,
                    isPrivate = priv,
                    isGuest = false,
                    bio = bio,
                    totalFocusMinutes = (doc.getLong("totalFocusMinutes") ?: 0L).toInt(),
                    achievementsCount = (doc.getLong("achievementsCount") ?: 0L).toInt()
                )

                if (priv) {
                    // Private profiles can ONLY be found by entering exact 9-character friendId
                    if (fId.equals(q, ignoreCase = true) || fId.equals(cleanQuery, ignoreCase = true)) {
                        profile
                    } else null
                } else {
                    // Public profiles can be found by name, username, or friendId
                    val cleanUsername = username.removePrefix("@")
                    if (name.contains(q, ignoreCase = true) ||
                        cleanUsername.equals(cleanQuery, ignoreCase = true) ||
                        username.contains(q, ignoreCase = true) ||
                        fId.equals(q, ignoreCase = true) ||
                        fId.equals(cleanQuery, ignoreCase = true)
                    ) {
                        profile
                    } else null
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun searchUserByHandleOrId(query: String): UserProfile? {
        val q = query.trim()
        if (q.isBlank()) return null
        val currentFirestore = firestore ?: return null
        val myUid = _currentUser.value?.uid ?: ""

        return try {
            val cleanHandle = if (q.startsWith("@")) q.lowercase() else "@${q.lowercase()}"
            val cleanId = q.uppercase()

            val snapshot = currentFirestore.collection("users").get().await()
            for (doc in snapshot.documents) {
                if (doc.id == myUid) continue
                val isMod = doc.getBoolean("isModerator") ?: false
                if (isMod) continue // Moderator is hidden

                val username = doc.getString("username") ?: ""
                val friendId = doc.getString("friendId") ?: ""
                val isPrivate = doc.getBoolean("isPrivate") ?: false

                val matchesUsername = username.equals(cleanHandle, ignoreCase = true) || username.equals(q, ignoreCase = true)
                val matchesFriendId = friendId.isNotBlank() && (friendId.equals(cleanId, ignoreCase = true) || friendId.equals(q, ignoreCase = true))

                if (isPrivate) {
                    // Private accounts can ONLY be found by exact Friend ID
                    if (matchesFriendId) {
                        return UserProfile(
                            uid = doc.id,
                            displayName = doc.getString("displayName") ?: "Usuário Citra",
                            username = username,
                            photoUrl = doc.getString("photoUrl"),
                            friendId = friendId,
                            isPrivate = true,
                            isGuest = false,
                            bio = doc.getString("bio") ?: "...",
                            totalFocusMinutes = (doc.getLong("totalFocusMinutes") ?: 0L).toInt(),
                            achievementsCount = (doc.getLong("achievementsCount") ?: 0L).toInt()
                        )
                    }
                } else {
                    // Public accounts can be found by username or friendId
                    if (matchesUsername || matchesFriendId) {
                        return UserProfile(
                            uid = doc.id,
                            displayName = doc.getString("displayName") ?: "Usuário Citra",
                            username = username,
                            photoUrl = doc.getString("photoUrl"),
                            friendId = friendId,
                            isPrivate = false,
                            isGuest = false,
                            bio = doc.getString("bio") ?: "...",
                            totalFocusMinutes = (doc.getLong("totalFocusMinutes") ?: 0L).toInt(),
                            achievementsCount = (doc.getLong("achievementsCount") ?: 0L).toInt()
                        )
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun loadPublicSuggestions(): List<UserProfile> {
        val currentFirestore = firestore ?: return emptyList()
        val myUid = _currentUser.value?.uid ?: ""
        val friendUids = _friends.value.map { it.friendUserId }.toSet()

        return try {
            val snapshot = currentFirestore.collection("users")
                .get()
                .await()

            val list = snapshot.documents.mapNotNull { doc ->
                val uid = doc.id
                if (uid == myUid || uid in friendUids) return@mapNotNull null

                val isMod = doc.getBoolean("isModerator") ?: false
                if (isMod) return@mapNotNull null // Never suggest moderator account

                val fId = doc.getString("friendId") ?: ""
                if (fId.isBlank()) return@mapNotNull null

                val priv = doc.getBoolean("isPrivate") ?: false
                if (priv) return@mapNotNull null // Only show public profiles

                UserProfile(
                    uid = uid,
                    displayName = doc.getString("displayName") ?: "Usuário Citra",
                    username = doc.getString("username") ?: "@citra",
                    photoUrl = doc.getString("photoUrl"),
                    friendId = fId,
                    isPrivate = false,
                    isGuest = false,
                    bio = doc.getString("bio") ?: "...",
                    totalFocusMinutes = (doc.getLong("totalFocusMinutes") ?: 0L).toInt(),
                    achievementsCount = (doc.getLong("achievementsCount") ?: 0L).toInt()
                )
            }
            _suggestedUsers.value = list
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun sendFriendRequest(target: UserProfile): Result<Unit> {
        val myUser = _currentUser.value ?: return Result.failure(IllegalStateException("Não conectado"))
        val currentFirestore = firestore ?: return Result.failure(IllegalStateException("Firestore não está inicializado"))
        return try {
            val reqId = "${myUser.uid}_${target.uid}"
            currentFirestore.collection("friendRequests").document(reqId).set(
                mapOf(
                    "fromUserId" to myUser.uid,
                    "fromDisplayName" to myUser.displayName,
                    "fromUsername" to myUser.username,
                    "fromPhotoUrl" to myUser.photoUrl,
                    "toUserId" to target.uid,
                    "toDisplayName" to target.displayName,
                    "toUsername" to target.username,
                    "status" to "pending",
                    "createdAt" to System.currentTimeMillis()
                )
            ).await()

            // Optimistically add to my friends as pending
            _friends.value = _friends.value + FriendItem(
                friendUserId = target.uid,
                displayName = target.displayName,
                username = target.username,
                photoUrl = target.photoUrl,
                friendId = target.friendId,
                status = "pending"
            )
            // Remove from suggested users list
            _suggestedUsers.value = _suggestedUsers.value.filter { it.uid != target.uid }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun acceptFriendRequest(request: FriendItem): Result<Unit> {
        val myUser = _currentUser.value ?: return Result.failure(IllegalStateException("Não conectado"))
        val currentFirestore = firestore ?: return Result.failure(IllegalStateException("Firestore não está inicializado"))
        return try {
            currentFirestore.collection("users").document(myUser.uid)
                .collection("friends").document(request.friendUserId).set(
                    mapOf(
                        "friendUserId" to request.friendUserId,
                        "displayName" to request.displayName,
                        "username" to request.username,
                        "photoUrl" to request.photoUrl,
                        "friendId" to request.friendId,
                        "status" to "accepted"
                    )
                ).await()

            currentFirestore.collection("users").document(request.friendUserId)
                .collection("friends").document(myUser.uid).set(
                    mapOf(
                        "friendUserId" to myUser.uid,
                        "displayName" to myUser.displayName,
                        "username" to myUser.username,
                        "photoUrl" to myUser.photoUrl,
                        "friendId" to myUser.friendId,
                        "status" to "accepted"
                    )
                ).await()

            val reqId = "${request.friendUserId}_${myUser.uid}"
            try {
                currentFirestore.collection("friendRequests").document(reqId).update("status", "accepted").await()
            } catch (_: Exception) {}

            _pendingRequests.value = _pendingRequests.value.filter { it.friendUserId != request.friendUserId }
            _friends.value = _friends.value.filter { it.friendUserId != request.friendUserId } + request.copy(status = "accepted")
            _suggestedUsers.value = _suggestedUsers.value.filter { it.uid != request.friendUserId }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun rejectFriendRequest(request: FriendItem): Result<Unit> {
        val myUser = _currentUser.value ?: return Result.failure(IllegalStateException("Não conectado"))
        val currentFirestore = firestore ?: return Result.failure(IllegalStateException("Firestore não está inicializado"))
        return try {
            val reqId = "${request.friendUserId}_${myUser.uid}"
            try {
                currentFirestore.collection("friendRequests").document(reqId).update("status", "rejected").await()
            } catch (_: Exception) {
                currentFirestore.collection("friendRequests").document(reqId).delete().await()
            }
            _pendingRequests.value = _pendingRequests.value.filter { it.friendUserId != request.friendUserId }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeFriend(friendUserId: String): Result<Unit> {
        val myUser = _currentUser.value ?: return Result.failure(IllegalStateException("Não conectado"))
        val currentFirestore = firestore
        _friends.value = _friends.value.filter { it.friendUserId != friendUserId }
        if (currentFirestore == null || myUser.isGuest) {
            return Result.success(Unit)
        }
        return try {
            currentFirestore.collection("users").document(myUser.uid)
                .collection("friends").document(friendUserId).delete().await()
            try {
                currentFirestore.collection("users").document(friendUserId)
                    .collection("friends").document(myUser.uid).delete().await()
            } catch (_: Exception) {}
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun loadFriends(uid: String) {
        val currentFirestore = firestore ?: return
        scope.launch {
            try {
                val friendsSnap = currentFirestore.collection("users").document(uid).collection("friends").get().await()
                _friends.value = friendsSnap.documents.mapNotNull { doc ->
                    FriendItem(
                        friendUserId = doc.getString("friendUserId") ?: doc.id,
                        displayName = doc.getString("displayName") ?: "Amigo",
                        username = doc.getString("username") ?: "@amigo",
                        photoUrl = doc.getString("photoUrl"),
                        friendId = doc.getString("friendId") ?: "",
                        status = doc.getString("status") ?: "accepted"
                    )
                }

                // Check pending requests to me
                val requestsSnap = currentFirestore.collection("friendRequests")
                    .whereEqualTo("toUserId", uid)
                    .whereEqualTo("status", "pending")
                    .get()
                    .await()

                _pendingRequests.value = requestsSnap.documents.mapNotNull { doc ->
                    FriendItem(
                        friendUserId = doc.getString("fromUserId") ?: "",
                        displayName = doc.getString("fromDisplayName") ?: "Usuário",
                        username = doc.getString("fromUsername") ?: "@usuario",
                        photoUrl = doc.getString("fromPhotoUrl"),
                        status = "pending"
                    )
                }

                // Also load public suggestions
                loadPublicSuggestions()
            } catch (_: Exception) {}
        }
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
        settingsMap: Map<String, Any>
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
            val notesJson = BackupManager.exportNotesJson(notes)

            userRef.collection("cloudSync").document("current").set(
                mapOf(
                    "backupJson" to backupJson,
                    "notesJson" to notesJson,
                    "settings" to settingsMap,
                    "syncedAt" to System.currentTimeMillis()
                )
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
                val settings = doc.get("settings") as? Map<String, Any>
                CloudUserData(backupJson, notesJson, settings)
            } else null
        } catch (_: Exception) {
            null
        }
    }

    // MODERATOR MONITORING & CLOUD INFRASTRUCTURE MANAGEMENT
    suspend fun fetchMonitoringData(): Triple<SystemStorageMetrics, List<UserMonitoringData>, List<GroupMonitoringData>> {
        val currentFirestore = firestore ?: return Triple(SystemStorageMetrics(), emptyList(), emptyList())
        val userMonitoringList = mutableListOf<UserMonitoringData>()
        val groupMonitoringList = mutableListOf<GroupMonitoringData>()

        var totalTextBytes = 0L
        var totalImageBytes = 0L

        try {
            val usersSnapshot = currentFirestore.collection("users").get().await()
            for (doc in usersSnapshot.documents) {
                val isMod = doc.getBoolean("isModerator") ?: false
                val username = doc.getString("username") ?: ""
                if (isMod || username == "@moderador") continue

                val uid = doc.id
                val displayName = doc.getString("displayName") ?: "Usuário Citra"
                val isPrivate = doc.getBoolean("isPrivate") ?: false

                var backupBytes = 0L
                var textBytes = 0L
                var imageBytes = 0L
                var lastSynced = 0L

                try {
                    val syncDoc = currentFirestore.collection("users").document(uid)
                        .collection("cloudSync").document("current").get().await()
                    if (syncDoc.exists()) {
                        val backupStr = syncDoc.getString("backupJson") ?: ""
                        val notesStr = syncDoc.getString("notesJson") ?: ""
                        lastSynced = syncDoc.getLong("syncedAt") ?: 0L

                        val bBytes = backupStr.toByteArray(Charsets.UTF_8).size.toLong()
                        val nBytes = notesStr.toByteArray(Charsets.UTF_8).size.toLong()
                        backupBytes = bBytes + nBytes

                        // Detect image data in notes
                        val hasMedia = notesStr.contains("image") || notesStr.contains(".jpg") || notesStr.contains(".png") || notesStr.contains("data:image")
                        if (hasMedia && nBytes > 2000L) {
                            imageBytes = (nBytes * 0.4).toLong()
                            textBytes = backupBytes - imageBytes
                        } else {
                            textBytes = backupBytes
                            imageBytes = 0L
                        }
                    }
                } catch (_: Exception) {}

                totalTextBytes += textBytes
                totalImageBytes += imageBytes

                userMonitoringList.add(
                    UserMonitoringData(
                        uid = uid,
                        displayName = displayName,
                        username = username,
                        isPrivate = isPrivate,
                        backupSizeBytes = backupBytes,
                        textSizeBytes = textBytes,
                        imageSizeBytes = imageBytes,
                        lastSynced = lastSynced,
                        isSelected = false,
                        isModerator = isMod
                    )
                )
            }

            // Also inspect groups
            try {
                val groupsSnapshot = currentFirestore.collection("groups").get().await()
                for (doc in groupsSnapshot.documents) {
                    val gName = doc.getString("name") ?: "Grupo"
                    val gType = doc.getString("groupType") ?: "LIBERAL"
                    val mCount = (doc.getLong("memberCount") ?: 1L).toInt()
                    val gSize = (doc.getLong("storageBytes") ?: (mCount * 1840L)).toLong()
                    totalTextBytes += (gSize * 0.8).toLong()
                    totalImageBytes += (gSize * 0.2).toLong()
                    groupMonitoringList.add(
                        GroupMonitoringData(
                            id = doc.id,
                            name = gName,
                            groupType = gType,
                            memberCount = mCount,
                            totalSizeBytes = gSize,
                            lastUpdated = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                        )
                    )
                }
            } catch (_: Exception) {}

        } catch (_: Exception) {}

        val totalUsedBytes = totalTextBytes + totalImageBytes
        val metrics = SystemStorageMetrics(
            totalCapacityBytes = 1073741824L, // 1 GB (1,024 MB) Firebase Spark Plan
            totalUsedBytes = totalUsedBytes,
            textUsedBytes = totalTextBytes,
            imageUsedBytes = totalImageBytes,
            totalAccountsCount = userMonitoringList.size,
            totalGroupsCount = groupMonitoringList.size
        )

        return Triple(metrics, userMonitoringList, groupMonitoringList)
    }

    suspend fun deleteUsersCloudData(uids: List<String>): Result<Unit> {
        val currentFirestore = firestore ?: return Result.failure(IllegalStateException("Firestore não está inicializado"))
        return try {
            for (uid in uids) {
                // Delete user cloudSync
                try {
                    currentFirestore.collection("users").document(uid)
                        .collection("cloudSync").document("current").delete().await()
                } catch (_: Exception) {}

                // Reset user storage counts
                try {
                    currentFirestore.collection("users").document(uid).update(
                        mapOf(
                            "totalFocusMinutes" to 0,
                            "completedTasksCount" to 0,
                            "completedHabitsCount" to 0,
                            "achievementsCount" to 0
                        )
                    ).await()
                } catch (_: Exception) {}
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteAllUsersCloudData(): Result<Unit> {
        val currentFirestore = firestore ?: return Result.failure(IllegalStateException("Firestore não está inicializado"))
        return try {
            val usersSnapshot = currentFirestore.collection("users").get().await()
            for (doc in usersSnapshot.documents) {
                val isMod = doc.getBoolean("isModerator") ?: false
                if (!isMod && doc.getString("username") != "@moderador") {
                    try {
                        currentFirestore.collection("users").document(doc.id)
                            .collection("cloudSync").document("current").delete().await()
                        currentFirestore.collection("users").document(doc.id).update(
                            mapOf(
                                "totalFocusMinutes" to 0,
                                "completedTasksCount" to 0,
                                "completedHabitsCount" to 0,
                                "achievementsCount" to 0
                            )
                        ).await()
                    } catch (_: Exception) {}
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleUserModeratorStatus(targetUid: String, makeModerator: Boolean): Result<Unit> {
        val currentMod = _currentUser.value
        if (currentMod?.isModerator != true) {
            return Result.failure(IllegalStateException("Apenas moderadores podem gerenciar privilégios."))
        }
        if (!makeModerator && targetUid == currentMod.uid) {
            return Result.failure(IllegalStateException("Você não pode revogar seu próprio privilégio de moderador."))
        }
        val currentFirestore = firestore ?: return Result.failure(IllegalStateException("Firestore não está conectado."))
        return try {
            currentFirestore.collection("users").document(targetUid).update("isModerator", makeModerator).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

data class CloudUserData(
    val backupJson: String?,
    val notesJson: String?,
    val settings: Map<String, Any>?
)
