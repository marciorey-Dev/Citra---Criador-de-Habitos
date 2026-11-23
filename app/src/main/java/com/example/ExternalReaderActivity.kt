package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.local.PreferencesManager
import com.example.data.model.Note
import com.example.data.model.NoteDocumentType
import com.example.data.model.resolvedDocumentType
import com.example.ui.MainViewModel
import com.example.ui.screens.organization.notes.ExternalDocxReaderScreen
import com.example.ui.screens.organization.notes.ExternalEbookReaderScreen
import com.example.ui.screens.organization.notes.ExternalPdfReaderScreen
import com.example.ui.theme.CitraTheme
import com.example.util.ExternalDocumentUriHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Ultra-lightweight, isolated Activity for opening external PDF and DOCX files
 * directly from Android "Open with Citra" intents.
 *
 * Avoids initializing MainActivity, Room AppDatabase, alarms, or tabs — saving RAM and startup time,
 * and exits cleanly via finish() when closed unless the user explicitly hands off to save or convert.
 */
class ExternalReaderActivity : ComponentActivity() {

    private var targetUri by mutableStateOf<Uri?>(null)
    private var targetMimeType by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        parseIntent(intent)

        val prefsManager = PreferencesManager(applicationContext)

        setContent {
            val isDarkSetting by prefsManager.themeMode.collectAsState()
            val themeAccent by prefsManager.themeAccent.collectAsState()
            val effectiveDark = isDarkSetting ?: isSystemInDarkTheme()

            CitraTheme(darkTheme = effectiveDark, accent = themeAccent) {
                IsolatedExternalReaderHost(
                    uri = targetUri,
                    mimeHint = targetMimeType,
                    onClose = { finish() },
                    onHandoffToMain = { uri, mime, action ->
                        handoffToMainActivity(uri, mime, action)
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parseIntent(intent)
    }

    private fun parseIntent(intent: Intent?) {
        val dataUri = intent?.data ?: intent?.clipData?.getItemAt(0)?.uri
        targetUri = dataUri
        targetMimeType = intent?.type
    }

    private fun handoffToMainActivity(uri: Uri, mimeType: String?, action: String) {
        val handoffIntent = Intent(this, MainActivity::class.java).apply {
            this.action = ACTION_HANDOFF_EXTERNAL_DOC
            data = uri
            putExtra(EXTRA_HANDOFF_MIME_TYPE, mimeType)
            putExtra(EXTRA_HANDOFF_ACTION, action)
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
        }
        startActivity(handoffIntent)
        finish()
    }

    companion object {
        const val ACTION_HANDOFF_EXTERNAL_DOC = "com.example.citra.HANDOFF_EXTERNAL_DOC"
        const val EXTRA_HANDOFF_MIME_TYPE = "extra_handoff_mime_type"
        const val EXTRA_HANDOFF_ACTION = "extra_handoff_action"
    }
}

@Composable
private fun IsolatedExternalReaderHost(
    uri: Uri?,
    mimeHint: String?,
    onClose: () -> Unit,
    onHandoffToMain: (Uri, String?, String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var resolvedNote by remember(uri) { mutableStateOf<Note?>(null) }
    var resolutionDone by remember(uri) { mutableStateOf(false) }

    LaunchedEffect(uri, mimeHint) {
        resolutionDone = false
        if (uri == null) {
            resolvedNote = null
            resolutionDone = true
            return@LaunchedEffect
        }
        val docNote = withContext(Dispatchers.IO) {
            ExternalDocumentUriHelper.takePersistableReadPermission(context, uri)
            val filename = ExternalDocumentUriHelper.resolveDisplayName(context, uri)
                ?: "Documento"
            val detected = ExternalDocumentUriHelper.detectExternalDocumentType(
                context = context,
                uri = uri,
                displayName = filename,
                mimeTypeHint = mimeHint
            ) ?: return@withContext null
            val rawTitle = filename.substringBeforeLast(".").ifBlank { filename }.trim()
            val emoji = when {
                detected == NoteDocumentType.PDF -> "📄"
                detected == NoteDocumentType.DOCX -> "📘"
                detected == NoteDocumentType.CBZ -> "🖼️"
                else -> "📚"
            }
            Note(
                id = 0L,
                uuid = "",
                title = rawTitle,
                content = "",
                category = "Geral",
                iconEmoji = emoji,
                documentType = detected.id,
                externalUri = uri.toString(),
                updatedAt = System.currentTimeMillis()
            )
        }
        resolvedNote = docNote
        resolutionDone = true
    }

    val note = resolvedNote
    when {
        !resolutionDone -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        note == null || uri == null -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Não foi possível abrir o arquivo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Formato não suportado ou acesso expirado pelo sistema.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(onClick = onClose) {
                    Text("Fechar leitor")
                }
            }
        }
        note.resolvedDocumentType == NoteDocumentType.PDF -> {
            ExternalPdfReaderScreen(
                note = note,
                categories = emptyList(),
                isTransientMode = true,
                onSaveToVault = {
                    onHandoffToMain(uri, mimeHint, MainViewModel.HANDOFF_ACTION_SAVE_TO_VAULT)
                },
                onConvertToNote = {
                    onHandoffToMain(uri, mimeHint, MainViewModel.HANDOFF_ACTION_CONVERT_TO_NOTE)
                },
                onClose = onClose,
                onChangeCategory = {},
                onCreateCategory = { _, _ -> },
                onRenameDocument = {},
                onRelinkClick = {},
                onRemoveLink = onClose
            )
        }
        note.resolvedDocumentType.isEbookOrComic -> {
            ExternalEbookReaderScreen(
                note = note,
                categories = emptyList(),
                isTransientMode = true,
                onSaveToVault = {
                    onHandoffToMain(uri, mimeHint, MainViewModel.HANDOFF_ACTION_SAVE_TO_VAULT)
                },
                onConvertToNote = {
                    onHandoffToMain(uri, mimeHint, MainViewModel.HANDOFF_ACTION_CONVERT_TO_NOTE)
                },
                onClose = onClose,
                onChangeCategory = {},
                onCreateCategory = { _, _ -> },
                onRenameDocument = {},
                onRelinkClick = {},
                onRemoveLink = onClose
            )
        }
        else -> {
            ExternalDocxReaderScreen(
                note = note,
                categories = emptyList(),
                isTransientMode = true,
                onSaveToVault = {
                    onHandoffToMain(uri, mimeHint, MainViewModel.HANDOFF_ACTION_SAVE_TO_VAULT)
                },
                onConvertToNote = {
                    onHandoffToMain(uri, mimeHint, MainViewModel.HANDOFF_ACTION_CONVERT_TO_NOTE)
                },
                onClose = onClose,
                onChangeCategory = {},
                onCreateCategory = { _, _ -> },
                onRenameDocument = {},
                onSaveDocxEdits = { _, _, _ -> },
                onRelinkClick = {},
                onRemoveLink = onClose
            )
        }
    }
}
