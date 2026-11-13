package com.example.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.base.FirestoreEmulatorTestBase
import com.example.data.auth.AuthRepository
import com.example.data.inbox.InboxRepository
import com.example.data.model.Achievement
import com.example.data.model.Category
import com.example.data.model.FocusSession
import com.example.data.model.Habit
import com.example.data.model.InboxMessage
import com.example.data.model.Note
import com.example.data.model.RecipientLookupState
import com.example.data.model.Task
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class AuthAndInboxRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private fun newAuthRepo(): AuthRepository {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return AuthRepository(
            context = context,
            overrideFirestore = firestore,
            overrideAuth = auth,
            attachAuthListener = false
        )
    }

    private fun newInboxRepo(): InboxRepository {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return InboxRepository(
            context = context,
            overrideFirestore = firestore,
            overrideAuth = auth
        )
    }

    @Test
    fun ensureAndLoadUserProfile_andCloudSync_roundtripWorks() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val firebaseUser = checkNotNull(auth.currentUser)
        val authRepo = newAuthRepo()

        val profile = withTimeout(DEFAULT_TIMEOUT_MS) {
            authRepo.ensureAndLoadUserProfile(firebaseUser)
        }
        assertEquals(uid, profile.uid)
        assertTrue(profile.username.isNotBlank())

        val syncResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            authRepo.syncAllUserDataToCloud(
                tasks = listOf(Task(id = 1L, title = "Estudar Kotlin")),
                habits = listOf(Habit(id = 1L, title = "Ler 20 min", currentStreak = 3)),
                notes = listOf(Note(id = 1L, title = "Resumo", content = "Conteúdo")),
                categories = listOf(Category(name = "Estudos", emoji = "📚")),
                focusSessions = listOf(FocusSession(id = 1L, category = "Estudos", durationMinutes = 25)),
                achievements = listOf(Achievement(id = "a1", title = "Primeiro", description = "OK", iconEmoji = "🔥", category = "Foco", isUnlocked = true)),
                settingsMap = mapOf("themeAccent" to "ORANGE"),
                financesJson = "{\"schemaVersion\":1}"
            )
        }
        assertTrue("Expected syncAllUserDataToCloud to succeed: ${syncResult.exceptionOrNull()}", syncResult.isSuccess)

        val fetched = withTimeout(DEFAULT_TIMEOUT_MS) {
            authRepo.fetchCloudUserData()
        }
        assertNotNull(fetched)
        assertTrue(fetched!!.backupJson?.contains("Estudar Kotlin") == true)
        assertEquals("{\"schemaVersion\":1}", fetched.financesJson)

        val financeSyncOk = withTimeout(DEFAULT_TIMEOUT_MS) {
            authRepo.syncFinancesToCloud("{\"schemaVersion\":1,\"entries\":[]}")
        }
        assertTrue(financeSyncOk)
        val fetchedFinances = withTimeout(DEFAULT_TIMEOUT_MS) {
            authRepo.fetchCloudFinances()
        }
        assertEquals("{\"schemaVersion\":1,\"entries\":[]}", fetchedFinances)
    }

    @Test
    fun inboxRepository_recipientLookup_sendAndFetch_worksAcrossAccounts() = runBlocking {
        val uniqueRun = UUID.randomUUID().toString().take(6)
        val aliceEmail = "alice.$uniqueRun@test.com"
        val bobEmail = "bob.$uniqueRun@test.com"

        val aliceUid = signInTestUser(aliceEmail)
        val aliceRepo = newAuthRepo()
        val aliceProfile = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.ensureAndLoadUserProfile(checkNotNull(auth.currentUser))
        }

        val bobUid = signInTestUser(bobEmail)
        val bobRepo = newAuthRepo()
        val bobProfile = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.ensureAndLoadUserProfile(checkNotNull(auth.currentUser))
        }

        // Bob searches Alice via hashed usernameLookup and hashed emailLookup & sends a note
        val bobInbox = newInboxRepo()
        val lookupState = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobInbox.searchRecipient("@${aliceProfile.username}", preferUsernameLookup = true)
        }
        assertTrue("Expected Found via username, got $lookupState", lookupState is RecipientLookupState.Found)
        val foundAlice = (lookupState as RecipientLookupState.Found).recipient
        assertEquals(aliceUid, foundAlice.uid)

        val emailLookupState = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobInbox.searchRecipient(aliceEmail, preferUsernameLookup = false)
        }
        assertTrue("Expected Found via email, got $emailLookupState", emailLookupState is RecipientLookupState.Found)
        assertEquals(aliceUid, (emailLookupState as RecipientLookupState.Found).recipient.uid)

        val messageId = UUID.randomUUID().toString()
        val msg = InboxMessage(
            id = messageId,
            senderUid = bobUid,
            senderDisplayName = bobProfile.displayName,
            senderUsername = bobProfile.username,
            recipientUid = aliceUid,
            recipientDisplayName = aliceProfile.displayName,
            recipientUsername = aliceProfile.username,
            title = "Nota Compartilhada",
            contentMarkdown = "Olá Alice!",
            category = "Geral",
            sentAtMillis = System.currentTimeMillis()
        )
        val sendResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobInbox.sendDirectMessage(aliceUid, msg, foundAlice)
        }
        assertTrue("sendDirectMessage failed: ${sendResult.exceptionOrNull()}", sendResult.isSuccess)

        // Alice signs back in and fetches her inbox
        signInTestUser(aliceEmail)
        val aliceInbox = newInboxRepo()
        val received = withTimeout(FLOW_TIMEOUT_MS) {
            aliceInbox.fetchInboxMessages(aliceUid).first { list -> list.any { it.id == messageId } }
        }
        assertTrue(received.any { it.id == messageId && it.title == "Nota Compartilhada" })

        // Alice sends a bug report (create allowed)
        val bugRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceInbox.sendBugReport(
                reporterUid = aliceUid,
                reporterDisplayName = aliceProfile.displayName,
                reporterUsername = aliceProfile.username,
                reporterPhotoUrl = null,
                reporterEmail = aliceEmail,
                title = "Teste Bug",
                description = "Descrição de teste"
            )
        }
        assertTrue("sendBugReport failed: ${bugRes.exceptionOrNull()}", bugRes.isSuccess)
    }

    @Test
    fun crossUserAndUnauthenticated_accessDeniedWithPermissionDenied() = runBlocking {
        val uniqueRun = UUID.randomUUID().toString().take(6)
        val aliceUid = signInTestUser("alice.neg.$uniqueRun@test.com")
        val aliceRepo = newAuthRepo()
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.ensureAndLoadUserProfile(checkNotNull(auth.currentUser))
        }

        // Switch to Bob and verify cross-user reads fail
        signInTestUser("bob.neg.$uniqueRun@test.com")
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid).get().await()
            }
            fail("Expected PERMISSION_DENIED when Bob reads Alice's profile")
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException() ?: throw e
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx.code)
        }

        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid)
                    .collection("cloudSync").document("current").get().await()
            }
            fail("Expected PERMISSION_DENIED when Bob reads Alice's cloudSync")
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException() ?: throw e
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx.code)
        }

        // Sign out completely and verify unauthenticated reads fail
        auth.signOut()
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid).get().await()
            }
            fail("Expected PERMISSION_DENIED when unauthenticated")
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException() ?: throw e
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx.code)
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice.citra@test.com"
        const val BOB_EMAIL = "bob.citra@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
