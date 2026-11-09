package com.example.ui.screens.organization.notes

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import com.example.ui.theme.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Toc
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.local.PreferencesManager
import com.example.data.model.Category
import com.example.data.model.Habit
import com.example.data.model.Note
import com.example.data.model.NoteVersion
import com.example.data.model.Task
import com.example.ui.screens.organization.QuickAddTaskDialog
import com.example.ui.FlexiblePomodoroState
import com.example.ui.theme.GreenAccent
import com.example.ui.theme.RedAction
import com.example.util.CitraDocsFileManager
import com.example.util.FastBlur
import com.example.util.MediaStorageHelper
import com.example.util.InlineMarkdownHelper
import com.example.util.NoteAudioPlaybackManager
import com.example.util.NoteExportHelper
import com.example.util.NotePaginationType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

enum class NoteEditorMode(val label: String) {
    LIVE_PREVIEW("Edição"),
    READING("Leitura")
}

object NoteEditorSaveBridge {
    var onSaveOpenNote: (() -> Unit)? = null
    fun requestSave() {
        onSaveOpenNote?.invoke()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenNoteEditor(
    initialNote: Note?,
    categories: List<Category>,
    allNotes: List<Note> = emptyList(),
    tasks: List<Task> = emptyList(),
    habits: List<Habit> = emptyList(),
    pomodoroState: FlexiblePomodoroState? = null,
    onNavigateToTimer: (() -> Unit)? = null,
    onSaveAndClose: (title: String, content: String, category: String, isPinned: Boolean, bgUri: String?, blur: Float, brightness: Float, noteColor: String?) -> Unit,
    onSaveOnly: ((title: String, content: String, category: String, isPinned: Boolean, bgUri: String?, blur: Float, brightness: Float, noteColor: String?) -> Unit)? = null,
    onClose: () -> Unit = {},
    onDelete: (() -> Unit)? = null,
    onOpenLinkedNote: ((Note) -> Unit)? = null,
    onConvertChecklistToTask: ((title: String, date: String) -> Unit)? = null,
    initialMode: NoteEditorMode = NoteEditorMode.LIVE_PREVIEW,
    onModeChanged: ((NoteEditorMode) -> Unit)? = null,
    onToggleChecklistTaskSync: ((checklistText: String, isChecked: Boolean) -> Unit)? = null,
    onConvertChecklistToDirectTask: ((title: String, category: String, priority: String, dueDate: String, dueTimestamp: Long?, reminderMinutesBefore: Int) -> Unit)? = null,
    noteVersions: List<NoteVersion> = emptyList(),
    onRestoreVersion: ((NoteVersion) -> Unit)? = null,
    onCreateLocalCopy: ((NoteVersion) -> Unit)? = null,
    financesEnabled: Boolean = false,
    onInsertFinanceBlock: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val noteScrollState = rememberScrollState()

    // Floating toolbar is persistent: it stays visible and accompanies the user on scroll with drag mobility
    val isToolbarVisible = true
    var savedFloatingOffsetX by rememberSaveable { mutableFloatStateOf(0f) }
    var savedFloatingOffsetY by rememberSaveable { mutableFloatStateOf(0f) }
    var isToolbarPinnedToBottom by rememberSaveable { mutableStateOf(true) }
    var toolbarOffsetX by rememberSaveable { mutableFloatStateOf(savedFloatingOffsetX) }
    var toolbarOffsetY by rememberSaveable { mutableFloatStateOf(savedFloatingOffsetY) }
    var isToolbarMinimized by remember { mutableStateOf(false) }
    val preferencesManager = remember { PreferencesManager(context) }
    var showReorderToolbarDialog by remember { mutableStateOf(false) }
    var toolbarItemsOrder by remember { mutableStateOf(preferencesManager.getToolbarOrder()) }

    var title by remember { mutableStateOf(initialNote?.title ?: "") }
    var currentSavedTitle by remember { mutableStateOf(initialNote?.title) }
    val defaultCat = remember(categories) {
        categories.find { it.name.equals("Rascunhos", ignoreCase = true) }?.name
            ?: categories.find { it.name.equals("Geral", ignoreCase = true) }?.name
            ?: categories.firstOrNull()?.name
            ?: "Rascunhos"
    }
    var category by remember {
        mutableStateOf(
            initialNote?.category?.takeIf { cat -> categories.any { it.name.equals(cat, ignoreCase = true) } } ?: defaultCat
        )
    }
    var isPinned by remember { mutableStateOf(initialNote?.isPinned ?: false) }
    var bgUri by remember { mutableStateOf(initialNote?.backgroundImageUri) }
    var bgBlur by remember { mutableStateOf(initialNote?.backgroundBlur ?: 0.5f) }
    var bgBrightness by remember { mutableStateOf(initialNote?.backgroundBrightness ?: 0.6f) }
    var noteColor by remember { mutableStateOf(initialNote?.noteColor) }
    var showColorPickerDialog by remember { mutableStateOf(false) }

    // Unified continuous Markdown document (Obsidian style)
    var editorValue by remember {
        val initialContent = com.example.data.backup.FinanceNoteMarkdownEngine.stripFinanceMarkers(initialNote?.content ?: "")
        mutableStateOf(TextFieldValue(initialContent))
    }
    var editorMode by remember {
        mutableStateOf(initialMode)
    }

    val bodyFocusRequester = remember { FocusRequester() }
    val isDark = isAppDarkTheme()
    val paperColors = remember(noteColor, isDark, isPinned) {
        PaperNotePalette.resolvePaperColors(noteColor, isDark, isPinned)
    }
    val isDarkBg = bgUri != null || isDark
    val textColor = if (bgUri != null) Color.White else if (isDark) DarkTextPrimary else Slate900
    val primaryColor = MaterialTheme.colorScheme.primary

    // Auto-scroll on typing removed: screen remains stationary while user types.

    // Loaded blurred background bitmap cache
    var processedBackgroundBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var missingMediaItems by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(bgUri, bgBlur) {
        if (!bgUri.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                try {
                    val stream = MediaStorageHelper.openInputStream(context, bgUri!!)
                    val original = stream?.use { BitmapFactory.decodeStream(it) }
                    if (original != null) {
                        val radius = (bgBlur * 24).toInt().coerceAtLeast(0)
                        processedBackgroundBitmap = if (radius > 0) FastBlur.blur(original, radius) else original
                    } else {
                        processedBackgroundBitmap = null
                    }
                } catch (_: Exception) {
                    processedBackgroundBitmap = null
                }
            }
        } else {
            processedBackgroundBitmap = null
        }
    }

    // Detect any missing media on opening note and alert user
    LaunchedEffect(editorValue.text, bgUri, initialNote?.id) {
        withContext(Dispatchers.IO) {
            val missing = mutableListOf<String>()
            if (!bgUri.isNullOrBlank() && !MediaStorageHelper.isMediaFileValid(context, bgUri)) {
                missing.add("Imagem de fundo")
            }
            val tokens = ObsidianMarkdownEngine.parseMediaTokens(editorValue.text)
            tokens.forEach { token ->
                if (token.url.isNotBlank() && !MediaStorageHelper.isMediaFileValid(context, token.url)) {
                    val name = token.titleOrAlt.ifBlank { token.url.substringAfterLast('/') }
                    val type = if (token.isAudio) "Áudio" else "Imagem"
                    missing.add("$type: $name")
                }
            }
            missingMediaItems = missing
        }
    }

    // Undo / Redo history
    val undoStack = remember { mutableStateListOf<TextFieldValue>() }
    val redoStack = remember { mutableStateListOf<TextFieldValue>() }
    var lastSnapshotValue by remember { mutableStateOf(editorValue) }
    var lastTypingTime by remember { mutableLongStateOf(0L) }
    var isHistoryApplying by remember { mutableStateOf(false) }

    // Tracking unsaved changes for Save button state (colored when dirty, neutral gray when saved)
    val isUnsavedTemplate = initialNote?.id == 0L
    var lastSavedTitle by remember { mutableStateOf(if (isUnsavedTemplate) "" else initialNote?.title ?: "") }
    var lastSavedContent by remember { mutableStateOf(if (isUnsavedTemplate) "" else initialNote?.content ?: "") }
    var lastSavedCategory by remember { mutableStateOf(initialNote?.category ?: categories.firstOrNull()?.name ?: "Geral") }
    var lastSavedIsPinned by remember { mutableStateOf(initialNote?.isPinned ?: false) }
    var lastSavedBgUri by remember { mutableStateOf(initialNote?.backgroundImageUri) }
    var lastSavedBgBlur by remember { mutableStateOf(initialNote?.backgroundBlur ?: 0.5f) }
    var lastSavedBgBrightness by remember { mutableStateOf(initialNote?.backgroundBrightness ?: 0.6f) }
    var lastSavedNoteColor by remember { mutableStateOf(initialNote?.noteColor) }

    val isDirty = title != lastSavedTitle ||
            editorValue.text != lastSavedContent ||
            category != lastSavedCategory ||
            isPinned != lastSavedIsPinned ||
            bgUri != lastSavedBgUri ||
            bgBlur != lastSavedBgBlur ||
            bgBrightness != lastSavedBgBrightness ||
            noteColor != lastSavedNoteColor

    var saveFeedbackTimestamp by remember { mutableStateOf(0L) }
    val showSaveFeedback = saveFeedbackTimestamp > 0L

    LaunchedEffect(saveFeedbackTimestamp) {
        if (saveFeedbackTimestamp > 0L) {
            kotlinx.coroutines.delay(1000)
            saveFeedbackTimestamp = 0L
        }
    }

    fun pushHistory() {
        if (undoStack.isEmpty() || undoStack.last().text != editorValue.text) {
            undoStack.add(editorValue)
            if (undoStack.size > 80) undoStack.removeAt(0)
        }
        redoStack.clear()
        lastSnapshotValue = editorValue
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            isHistoryApplying = true
            val current = editorValue
            val prev = undoStack.removeAt(undoStack.lastIndex)
            redoStack.add(current)
            editorValue = prev
            lastSnapshotValue = prev
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            isHistoryApplying = true
            val current = editorValue
            val next = redoStack.removeAt(redoStack.lastIndex)
            undoStack.add(current)
            editorValue = next
            lastSnapshotValue = next
        }
    }

    fun appendToEndOfNote(snippet: String) {
        pushHistory()
        val currentText = editorValue.text
        val newText = if (currentText.isBlank()) {
            snippet
        } else {
            currentText.trimEnd() + "\n\n" + snippet
        }
        editorValue = TextFieldValue(text = newText, selection = TextRange(newText.length))
        coroutineScope.launch {
            kotlinx.coroutines.delay(100)
            noteScrollState.animateScrollTo(noteScrollState.maxValue)
        }
    }

    LaunchedEffect(initialNote?.id) {
        if (initialNote != null) {
            title = initialNote.title
            category = initialNote.category
            isPinned = initialNote.isPinned
            bgUri = initialNote.backgroundImageUri
            bgBlur = initialNote.backgroundBlur
            bgBrightness = initialNote.backgroundBrightness
            noteColor = initialNote.noteColor
            lastSavedNoteColor = initialNote.noteColor
            val initialTfv = TextFieldValue(initialNote.content)
            editorValue = initialTfv
            lastSnapshotValue = initialTfv
            undoStack.clear()
            redoStack.clear()
        }
    }

    val readingBlocks = remember(editorValue.text) {
        SmoothMarkdownConverter.markdownToBlocks(editorValue.text)
    }

    fun findBlockCharOffset(blockIdx: Int): Int {
        if (readingBlocks.isEmpty() || blockIdx < 0) return 0
        if (blockIdx >= readingBlocks.size) return editorValue.text.length
        val targetBlock = readingBlocks[blockIdx]
        val searchSnippet = when (targetBlock) {
            is HeadingBlock -> targetBlock.text.trim().take(40)
            is TextBlock -> targetBlock.text.trim().take(40)
            is ChecklistBlock -> targetBlock.text.trim().take(40)
            is BulletBlock -> targetBlock.text.trim().take(40)
            is QuoteBlock -> targetBlock.text.trim().take(40)
            is CodeBlock -> targetBlock.code.trim().take(30)
            is MathBlock -> targetBlock.formula.trim().take(30)
            is ImageBlock -> targetBlock.url.trim()
            is AudioBlock -> targetBlock.url.trim()
            is ToggleBlock -> targetBlock.title.trim()
            is CalloutBlock -> targetBlock.text.trim().take(30)
            is TableBlock -> targetBlock.headers.firstOrNull()?.trim() ?: ""
            else -> ""
        }
        if (searchSnippet.isNotEmpty()) {
            val idx = editorValue.text.indexOf(searchSnippet)
            if (idx != -1) return idx
        }
        val ratio = blockIdx.toFloat() / readingBlocks.size.toFloat()
        return (ratio * editorValue.text.length).toInt().coerceIn(0, editorValue.text.length)
    }

    fun updateBlocksAndMarkdown(newBlocks: List<VisualBlock>) {
        pushHistory()
        val md = SmoothMarkdownConverter.blocksToMarkdown(newBlocks)
        editorValue = editorValue.copy(text = md)
    }


    DisposableEffect(Unit) {
        onDispose {
            NoteAudioPlaybackManager.stop()
        }
    }

    fun isCompletelyEmpty(): Boolean {
        return title.isBlank() && editorValue.text.isBlank()
    }

    fun saveCurrent(close: Boolean = true, silent: Boolean = false) {
        if (isCompletelyEmpty() && initialNote == null) {
            if (close) onClose()
            return
        }
        val finalTitle = title.trim().ifBlank { "Sem título" }
        val finalContent = com.example.data.backup.FinanceNoteMarkdownEngine.stripFinanceMarkers(editorValue.text)

        // Sync directly to physical .md file in Documents/Citra Docs/ with UUID and old title cleanup
        CitraDocsFileManager.saveNoteMarkdown(
            context = context,
            title = finalTitle,
            content = finalContent,
            uuid = initialNote?.uuid.orEmpty(),
            oldTitle = currentSavedTitle
        )
        currentSavedTitle = finalTitle
        lastSavedTitle = finalTitle
        lastSavedContent = finalContent
        lastSavedCategory = category
        lastSavedIsPinned = isPinned
        lastSavedBgUri = bgUri
        lastSavedBgBlur = bgBlur
        lastSavedBgBrightness = bgBrightness
        lastSavedNoteColor = noteColor

        if (close) {
            onSaveAndClose(
                finalTitle,
                finalContent,
                category,
                isPinned,
                bgUri,
                bgBlur,
                bgBrightness,
                noteColor
            )
        } else {
            if (!silent) {
                saveFeedbackTimestamp = System.currentTimeMillis()
            }
            if (onSaveOnly != null) {
                onSaveOnly(
                    finalTitle,
                    finalContent,
                    category,
                    isPinned,
                    bgUri,
                    bgBlur,
                    bgBrightness,
                    noteColor
                )
            } else {
                onSaveAndClose(
                    finalTitle,
                    finalContent,
                    category,
                    isPinned,
                    bgUri,
                    bgBlur,
                    bgBrightness,
                    noteColor
                )
            }
        }
    }

    fun switchEditorMode(newMode: NoteEditorMode) {
        if (newMode == NoteEditorMode.READING && editorMode != NoteEditorMode.READING) {
            saveCurrent(close = false, silent = true)
        }
        editorMode = newMode
        onModeChanged?.invoke(newMode)
    }

    // Auto-save and pause audio when app is put to background, user switches apps or exits
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                NoteAudioPlaybackManager.pause()
                saveCurrent(close = false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(Unit) {
        NoteEditorSaveBridge.onSaveOpenNote = {
            saveCurrent(close = false)
        }
        onDispose {
            NoteEditorSaveBridge.onSaveOpenNote = null
        }
    }

    // Auto-save on back press and close editor
    BackHandler {
        saveCurrent(close = true)
    }

    // Link navigation handler (external web links only; internal note links discarded)
    fun handleLinkClick(target: String) {
        val clean = target.trim()
        val isWeb = clean.startsWith("http://", ignoreCase = true) ||
                clean.startsWith("https://", ignoreCase = true) ||
                clean.startsWith("www.", ignoreCase = true) ||
                (clean.contains(".") && !clean.contains(" "))
                (clean.contains(".") && !clean.contains(" "))

        if (isWeb) {
            try {
                val url = if (clean.startsWith("http://", ignoreCase = true) || clean.startsWith("https://", ignoreCase = true)) {
                    clean
                } else {
                    "https://$clean"
                }
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                return
            } catch (_: Exception) {}
        }
    }

    // Media & CitraRecord Audio Recording State
    var isRecordingAudio by remember { mutableStateOf(false) }
    var currentRecorder by remember { mutableStateOf<android.media.MediaRecorder?>(null) }
    var currentRecordingFile by remember { mutableStateOf<File?>(null) }
    var recordingSeconds by remember { mutableStateOf(0) }

    LaunchedEffect(isRecordingAudio) {
        if (isRecordingAudio) {
            recordingSeconds = 0
            while (isRecordingAudio) {
                kotlinx.coroutines.delay(1000)
                recordingSeconds++
            }
        }
    }

    fun startRecording() {
        try {
            val audioFile = MediaStorageHelper.createRecordingFile(context)
            val recorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                android.media.MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                android.media.MediaRecorder()
            }
            recorder.apply {
                setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(audioFile.absolutePath)
                prepare()
                start()
            }
            currentRecorder = recorder
            currentRecordingFile = audioFile
            isRecordingAudio = true
        } catch (e: Exception) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Erro ao iniciar gravação no CitraRecord.")
            }
        }
    }

    fun stopRecording() {
        try {
            currentRecorder?.apply {
                stop()
                release()
            }
            currentRecorder = null
            isRecordingAudio = false

            currentRecordingFile?.let { file ->
                pushHistory()
                val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                val audioToken = "[audio:CitraRecord_$timeStr](${file.absolutePath})"
                editorValue = ObsidianMarkdownEngine.insertMediaToken(editorValue, audioToken)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Áudio gravado e inserido na nota!")
                }
            }
        } catch (e: Exception) {
            isRecordingAudio = false
            currentRecorder = null
        }
    }

    // Media Pickers
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val savedPath = MediaStorageHelper.saveNoteImage(context, uri)
            if (savedPath != null) {
                pushHistory()
                val imageToken = "![center|100%]($savedPath)"
                editorValue = ObsidianMarkdownEngine.insertMediaToken(editorValue, imageToken)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Imagem anexada com sucesso!")
                }
            }
        }
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val savedPath = MediaStorageHelper.saveNoteAudio(context, uri)
            if (savedPath != null) {
                pushHistory()
                val filename = uri.lastPathSegment?.substringAfterLast('/')?.take(25) ?: "Áudio"
                val audioToken = "[audio:$filename](${savedPath})"
                editorValue = ObsidianMarkdownEngine.insertMediaToken(editorValue, audioToken)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Áudio anexado com sucesso!")
                }
            }
        }
    }

    // Media Replacement State
    var tokenToReplaceForImage by remember { mutableStateOf<MediaTokenInfo?>(null) }
    var tokenToReplaceForAudio by remember { mutableStateOf<MediaTokenInfo?>(null) }
    var blockIndexToReplaceForImage by remember { mutableStateOf<Int?>(null) }
    var blockIndexToReplaceForAudio by remember { mutableStateOf<Int?>(null) }

    val replaceImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val savedPath = MediaStorageHelper.saveNoteImage(context, uri)
            if (savedPath != null) {
                pushHistory()
                val targetToken = tokenToReplaceForImage
                val targetIdx = blockIndexToReplaceForImage
                if (targetToken != null) {
                    val newToken = targetToken.rawMatch.replace(targetToken.url, savedPath)
                    val newText = editorValue.text.replace(targetToken.rawMatch, newToken)
                    editorValue = editorValue.copy(text = newText)
                } else if (targetIdx != null && targetIdx in readingBlocks.indices) {
                    val currentBlock = readingBlocks[targetIdx]
                    if (currentBlock is ImageBlock) {
                        val updated = currentBlock.copy(url = savedPath)
                        val list = readingBlocks.toMutableList().apply { set(targetIdx, updated) }
                        updateBlocksAndMarkdown(list)
                    }
                }
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Imagem substituída com sucesso!")
                }
            }
        }
        tokenToReplaceForImage = null
        blockIndexToReplaceForImage = null
    }

    val replaceAudioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val savedPath = MediaStorageHelper.saveNoteAudio(context, uri)
            if (savedPath != null) {
                pushHistory()
                val targetToken = tokenToReplaceForAudio
                val targetIdx = blockIndexToReplaceForAudio
                if (targetToken != null) {
                    val newToken = targetToken.rawMatch.replace(targetToken.url, savedPath)
                    val newText = editorValue.text.replace(targetToken.rawMatch, newToken)
                    editorValue = editorValue.copy(text = newText)
                } else if (targetIdx != null && targetIdx in readingBlocks.indices) {
                    val currentBlock = readingBlocks[targetIdx]
                    if (currentBlock is AudioBlock) {
                        val updated = currentBlock.copy(url = savedPath)
                        val list = readingBlocks.toMutableList().apply { set(targetIdx, updated) }
                        updateBlocksAndMarkdown(list)
                    }
                }
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Áudio substituído com sucesso!")
                }
            }
        }
        tokenToReplaceForAudio = null
        blockIndexToReplaceForAudio = null
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startRecording()
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Permissão de gravação de áudio negada.")
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val permanentBg = MediaStorageHelper.saveNoteBackground(context, uri)
            if (permanentBg != null) {
                bgUri = permanentBg
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Imagem de fundo salva com sucesso!")
                }
            } else {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Falha ao salvar imagem de fundo permanente.")
                }
            }
        }
    }

    // Dialog & Menu visibility states
    var showMenu by remember { mutableStateOf(false) }
    var showTitlesMenu by remember { mutableStateOf(false) }
    var showCalloutMenu by remember { mutableStateOf(false) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var showFindReplaceDialog by remember { mutableStateOf(false) }
    var showLinkToEntityDialog by remember { mutableStateOf(false) }
    var showBgSettingsSheet by remember { mutableStateOf(false) }
    var showAdvancedToolbar by remember { mutableStateOf(false) }
    var checklistItemToConvert by remember { mutableStateOf<String?>(null) }
    var fullscreenImageUrl by remember { mutableStateOf<String?>(null) }
    var fullscreenBlockIndex by remember { mutableStateOf<Int?>(null) }
    var showCreateTableDialog by remember { mutableStateOf(false) }
    var showAudioExportWarningDialog by remember { mutableStateOf(false) }
    var showVersionHistoryDialog by remember { mutableStateOf(false) }
    var showPaginationMenu by remember { mutableStateOf(false) }
    var notePaginationType by remember(initialNote?.id) {
        mutableStateOf(
            when {
                initialNote?.content?.contains("<!-- citra:pagination:roman -->") == true -> NotePaginationType.ROMAN
                initialNote?.content?.contains("<!-- citra:pagination:arabic -->") == true -> NotePaginationType.ARABIC
                else -> NotePaginationType.NONE
            }
        )
    }

    // Parse active media tokens for interactive widgets in Live Preview
    val parsedMediaTokens = remember(editorValue.text) {
        ObsidianMarkdownEngine.parseMediaTokens(editorValue.text)
    }

    val missingMediaTokens = remember(parsedMediaTokens, bgUri) {
        val missing = mutableListOf<String>()
        if (bgUri != null && !MediaStorageHelper.isMediaFileValid(context, bgUri)) {
            missing.add("Imagem de fundo não encontrada")
        }
        for (token in parsedMediaTokens) {
            if (token.url.isNotBlank() && !MediaStorageHelper.isMediaFileValid(context, token.url)) {
                val name = token.titleOrAlt.ifBlank { token.url.substringAfterLast("/") }
                val type = if (token.isAudio) "Áudio" else "Imagem"
                missing.add("$type: \"$name\"")
            }
        }
        missing
    }

    // Universal file import launcher (converts HTML, TXT, JSON, CITRADOC, TEX, MD to Markdown)
    val mdImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val filename = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1 && cursor.moveToFirst()) cursor.getString(nameIdx) else null
                } ?: (uri.lastPathSegment ?: "documento.md")

                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val rawText = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val markdownText = CitraDocsFileManager.convertContentToMarkdown(filename, rawText)
                    val persistedText = MediaStorageHelper.processAndPersistImportedNoteMedia(context, markdownText)
                    pushHistory()
                    editorValue = TextFieldValue(persistedText, selection = TextRange(persistedText.length))
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Ficheiro importado e convertido em Markdown!")
                    }
                }
            } catch (e: Exception) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Falha ao importar ficheiro.")
                }
            }
        }
    }

    Scaffold(
        containerColor = if (bgUri != null) Color.Black else MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (bgUri != null) Color.Transparent else MaterialTheme.colorScheme.background,
                    titleContentColor = textColor,
                    navigationIconContentColor = textColor,
                    actionIconContentColor = textColor
                ),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Mode selector pill (Edição & Leitura)
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    val newMode = if (editorMode == NoteEditorMode.LIVE_PREVIEW) {
                                        NoteEditorMode.READING
                                    } else {
                                        NoteEditorMode.LIVE_PREVIEW
                                    }
                                    switchEditorMode(newMode)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (editorMode == NoteEditorMode.LIVE_PREVIEW) {
                                        Icons.Default.Edit
                                    } else {
                                        Icons.AutoMirrored.Filled.MenuBook
                                    },
                                    contentDescription = editorMode.label,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = editorMode.label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { saveCurrent(close = true) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar e Salvar",
                            tint = textColor
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { undo() },
                        enabled = undoStack.isNotEmpty(),
                        modifier = Modifier.testTag("note_editor_undo_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Desfazer",
                            tint = if (undoStack.isNotEmpty()) primaryColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                        )
                    }

                    IconButton(
                        onClick = { redo() },
                        enabled = redoStack.isNotEmpty(),
                        modifier = Modifier.testTag("note_editor_redo_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "Refazer",
                            tint = if (redoStack.isNotEmpty()) primaryColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                        )
                    }

                    // Note Color Button
                    IconButton(
                        onClick = { showColorPickerDialog = true },
                        modifier = Modifier.testTag("note_editor_color_btn")
                    ) {
                        val activeColorOpt = PaperNotePalette.findColorOption(noteColor)
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = "Cor da Nota",
                                tint = textColor
                            )
                            if (noteColor != null) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .align(Alignment.BottomEnd)
                                        .background(activeColorOpt.primaryColor, CircleShape)
                                        .border(1.dp, Color.White, CircleShape)
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = { saveCurrent(close = false) },
                        modifier = Modifier.testTag("note_editor_save_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Salvar",
                            tint = if (isDirty) primaryColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                    }

                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Mais Opções",
                                tint = textColor
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (isPinned) "Desafixar nota" else "Fixar nota no topo") },
                                leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) },
                                onClick = {
                                    isPinned = !isPinned
                                    showMenu = false
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Imagem de fundo") },
                                leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    if (bgUri == null) {
                                        photoPickerLauncher.launch(
                                            androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    } else {
                                        showBgSettingsSheet = true
                                    }
                                }
                            )

                            if (financesEnabled && onInsertFinanceBlock != null) {
                                DropdownMenuItem(
                                    text = { Text("Inserir bloco de finanças") },
                                    leadingIcon = { Icon(Icons.Default.TableChart, contentDescription = null, tint = primaryColor) },
                                    onClick = {
                                        showMenu = false
                                        onInsertFinanceBlock()
                                    }
                                )
                            }

                            HorizontalDivider()

                            // EXPORT MENU OPTIONS (DOCX, PDF, MD, TXT, HTML, Image Card)
                            DropdownMenuItem(
                                text = { Text("Exportar em Word (.docx)") },
                                leadingIcon = { Icon(Icons.Default.Article, contentDescription = null, tint = primaryColor) },
                                onClick = {
                                    showMenu = false
                                    val blocks = SmoothMarkdownConverter.markdownToBlocks(editorValue.text)
                                    val uri = NoteExportHelper.exportToDocx(context, title, category, blocks, primaryColor.toArgb(), notePaginationType)
                                    NoteExportHelper.shareUri(context, uri, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "Exportar Nota em Word (.docx)")
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Exportar em PDF (.pdf)") },
                                leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = RedAction) },
                                onClick = {
                                    showMenu = false
                                    val blocks = SmoothMarkdownConverter.markdownToBlocks(editorValue.text)
                                    val uri = NoteExportHelper.exportToPdf(context, title, category, blocks, primaryColor.toArgb(), notePaginationType)
                                    NoteExportHelper.shareUri(context, uri, "application/pdf", "Exportar Nota em PDF")
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Exportar em Markdown (.md)") },
                                leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    val blocks = SmoothMarkdownConverter.markdownToBlocks(editorValue.text)
                                    val uri = NoteExportHelper.exportToMdFile(context, title, blocks)
                                    NoteExportHelper.shareUri(context, uri, "text/markdown", "Exportar Nota (.md)")
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Exportar em Texto Puro (.txt)") },
                                leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    val blocks = SmoothMarkdownConverter.markdownToBlocks(editorValue.text)
                                    val uri = NoteExportHelper.exportToTxtFile(context, title, category, blocks)
                                    NoteExportHelper.shareUri(context, uri, "text/plain", "Exportar Nota (.txt)")
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Exportar Página Web (.html)") },
                                leadingIcon = { Icon(Icons.Default.Html, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    val blocks = SmoothMarkdownConverter.markdownToBlocks(editorValue.text)
                                    val hasAudio = blocks.any { it is AudioBlock && it.url.isNotBlank() }
                                    if (hasAudio) {
                                        showAudioExportWarningDialog = true
                                    } else {
                                        val uri = NoteExportHelper.exportToHtmlFile(context, title, category, blocks, primaryColor.toArgb())
                                        NoteExportHelper.shareUri(context, uri, "text/html", "Exportar Página Web (.html)")
                                    }
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Compartilhar como Imagem (Card)") },
                                leadingIcon = { Icon(Icons.Default.Photo, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    val blocks = SmoothMarkdownConverter.markdownToBlocks(editorValue.text)
                                    val uri = NoteExportHelper.shareAsImage(context, title, category, blocks, primaryColor.toArgb())
                                    NoteExportHelper.shareUri(context, uri, "image/png", "Compartilhar Imagem da Nota")
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Converter Checklist em Tarefa ⚡") },
                                leadingIcon = { Icon(Icons.Default.Checklist, contentDescription = null, tint = primaryColor) },
                                onClick = {
                                    showMenu = false
                                    val lines = editorValue.text.lines()
                                    val checklistLine = lines.firstOrNull { it.trim().startsWith("- [ ]") || it.trim().startsWith("* [ ]") }
                                        ?: lines.firstOrNull { it.trim().startsWith("- [x]") || it.trim().startsWith("* [x]") }
                                    val extracted = if (checklistLine != null) {
                                        checklistLine.trim().substring(5).removePrefix("~~").removeSuffix("~~").trim()
                                    } else {
                                        editorValue.text.take(60).trim().ifBlank { title.ifBlank { "Minha Tarefa" } }
                                    }
                                    checklistItemToConvert = extracted
                                }
                            )

                            HorizontalDivider()

                            DropdownMenuItem(
                                text = { Text("Importar ficheiro (.md, .txt, .json, .html, .tex)") },
                                leadingIcon = { Icon(Icons.Default.FileUpload, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    mdImportLauncher.launch("*/*")
                                }
                            )

                            if (noteVersions.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Histórico de Versões (${noteVersions.size})") },
                                    leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        showVersionHistoryDialog = true
                                    }
                                )
                            }

                            if (onDelete != null) {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Mover para Lixeira", color = RedAction) },
                                    leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = RedAction) },
                                    onClick = {
                                        showMenu = false
                                        onDelete()
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Background Image layer (if enabled)
            if (processedBackgroundBitmap != null) {
                Image(
                    bitmap = processedBackgroundBitmap!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 1f - bgBrightness))
                )
            }

            // Main Note Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(noteScrollState)
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                // 1. Dynamic Meta Strip: Category Pills & Focus Session Chip
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(categories) { cat ->
                        val isSelected = category == cat.name
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                            },
                            shadowElevation = if (isSelected) 1.dp else 0.dp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { category = cat.name }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = if (isSelected) Icons.Default.Lightbulb else Icons.Default.Label,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = if (isSelected) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = cat.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }

                    if (pomodoroState != null && (pomodoroState.isRunning || pomodoroState.remainingSeconds > 0)) {
                        item {
                            val mins = (pomodoroState.remainingSeconds / 60).coerceAtLeast(1)
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Timer,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = "Foco • $mins min",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Editorial Timestamp & Reading Pulse
                val wordCount = remember(editorValue.text) {
                    editorValue.text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
                }
                val readMinutes = remember(wordCount) {
                    (wordCount / 180).coerceAtLeast(1)
                }
                val formattedTimestamp = remember(initialNote?.updatedAt) {
                    val ts = initialNote?.updatedAt ?: System.currentTimeMillis()
                    java.text.SimpleDateFormat("d 'de' MMMM, HH:mm", java.util.Locale("pt", "BR")).format(java.util.Date(ts))
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = formattedTimestamp,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDarkBg) Color.White.copy(alpha = 0.92f) else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(MaterialTheme.colorScheme.secondary, CircleShape)
                        )
                        Text(
                            text = "$wordCount palavras • $readMinutes min de leitura",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 3. Expressive Cozy Note Title Input
                OutlinedTextField(
                    value = title,
                    onValueChange = { input ->
                        if (input.contains("\n")) {
                            title = input.replace("\n", "")
                            try { bodyFocusRequester.requestFocus() } catch (_: Exception) {}
                        } else {
                            title = input
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = {
                        try { bodyFocusRequester.requestFocus() } catch (_: Exception) {}
                    }),
                    placeholder = {
                        Text(
                            "Título acolhedor...",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = textColor.copy(alpha = 0.4f)
                            )
                        )
                    },
                    textStyle = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = textColor
                    ),
                    maxLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_full_title_input")
                )

                // 3. Interactive Media Widgets Strip in Live Preview (WhatsApp Audio & Resizable Images)
                if (parsedMediaTokens.isNotEmpty() && editorMode == NoteEditorMode.LIVE_PREVIEW) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (token in parsedMediaTokens) {
                            if (token.isAudio) {
                                ObsidianWhatsAppAudioPlayer(
                                    token = token,
                                    onUpdateTitle = { newTitle ->
                                        pushHistory()
                                        val newToken = "[audio:$newTitle](${token.url})"
                                        val newText = editorValue.text.replace(token.rawMatch, newToken)
                                        editorValue = editorValue.copy(text = newText)
                                    },
                                    onReplace = {
                                        tokenToReplaceForAudio = token
                                        replaceAudioPickerLauncher.launch("audio/*")
                                    },
                                    onDelete = {
                                        pushHistory()
                                        val newText = editorValue.text.replace(token.rawMatch, "").replace("\n\n\n", "\n\n")
                                        editorValue = editorValue.copy(text = newText)
                                    }
                                )
                            } else {
                                ObsidianSideBySideImageWidget(
                                    token = token,
                                    isDarkBg = isDarkBg,
                                    onClickFullscreen = { fullscreenImageUrl = token.url },
                                    onReplace = {
                                        tokenToReplaceForImage = token
                                        replaceImagePickerLauncher.launch("image/*")
                                    },
                                    onChangeWidth = { newWidth ->
                                        pushHistory()
                                        val sidePart = if (token.sideText.isNotEmpty()) {
                                            val enc = token.sideText.replace("\n", "<br>").replace("|", "&#124;")
                                            "|side:$enc"
                                        } else ""
                                        val altPrefix = if (token.titleOrAlt.isNotEmpty()) "${token.titleOrAlt}|" else ""
                                        val newToken = "![$altPrefix${token.alignment}|$newWidth%$sidePart](${token.url})"
                                        val newText = editorValue.text.replace(token.rawMatch, newToken)
                                        editorValue = editorValue.copy(text = newText)
                                    },
                                    onChangeAlign = { newAlign ->
                                        pushHistory()
                                        val sidePart = if (token.sideText.isNotEmpty()) {
                                            val enc = token.sideText.replace("\n", "<br>").replace("|", "&#124;")
                                            "|side:$enc"
                                        } else ""
                                        val altPrefix = if (token.titleOrAlt.isNotEmpty()) "${token.titleOrAlt}|" else ""
                                        val newToken = "![$altPrefix$newAlign|${token.widthPercent}%$sidePart](${token.url})"
                                        val newText = editorValue.text.replace(token.rawMatch, newToken)
                                        editorValue = editorValue.copy(text = newText)
                                    },
                                    onChangeSideText = { newSideText ->
                                        pushHistory()
                                        val sidePart = if (newSideText.isNotEmpty()) {
                                            val enc = newSideText.replace("\n", "<br>").replace("|", "&#124;")
                                            "|side:$enc"
                                        } else ""
                                        val altPrefix = if (token.titleOrAlt.isNotEmpty()) "${token.titleOrAlt}|" else ""
                                        val newToken = "![$altPrefix${token.alignment}|${token.widthPercent}%$sidePart](${token.url})"
                                        val newText = editorValue.text.replace(token.rawMatch, newToken)
                                        editorValue = editorValue.copy(text = newText)
                                    },
                                    onDelete = {
                                        pushHistory()
                                        val newText = editorValue.text.replace(token.rawMatch, "").replace("\n\n\n", "\n\n")
                                        editorValue = editorValue.copy(text = newText)
                                    }
                                )
                            }
                        }
                    }
                }

                // 4. Content Area: Mode Live Preview OR Modo Leitura (Interactive UI Editing & Drag/Drop Reordering)
                when (editorMode) {
                    NoteEditorMode.LIVE_PREVIEW -> {
                        // OBSIDIAN LIVE PREVIEW: Unified Continuous Text with active line syntax reveal & rich styling
                        BasicTextField(
                            value = editorValue,
                            onValueChange = { newVal ->
                                if (isHistoryApplying) {
                                    isHistoryApplying = false
                                    editorValue = newVal
                                    return@BasicTextField
                                }
                                if (newVal.text != editorValue.text) {
                                    val now = System.currentTimeMillis()
                                    val textDiff = kotlin.math.abs(newVal.text.length - editorValue.text.length)
                                    val isWordBoundary = newVal.text.endsWith(" ") || newVal.text.endsWith("\n") || 
                                                           newVal.text.endsWith(".") || newVal.text.endsWith(",") ||
                                                           newVal.text.endsWith(";") || newVal.text.endsWith(":")
                                    val isTimeElapsed = (now - lastTypingTime) > 600L
                                    val isLargeChange = textDiff > 1

                                    if (undoStack.isEmpty() || isWordBoundary || isTimeElapsed || isLargeChange) {
                                        if (undoStack.isEmpty() || undoStack.last().text != editorValue.text) {
                                            undoStack.add(editorValue)
                                            if (undoStack.size > 80) undoStack.removeAt(0)
                                        }
                                        redoStack.clear()
                                        lastSnapshotValue = newVal
                                    }
                                    lastTypingTime = now

                                    // Detect checklist toggles in live preview mode
                                    val oldLines = editorValue.text.lines()
                                    val newLines = newVal.text.lines()
                                    if (oldLines.size == newLines.size) {
                                        for (i in oldLines.indices) {
                                            val o = oldLines[i].trim()
                                            val n = newLines[i].trim()
                                            if (o != n) {
                                                val oChecked = o.startsWith("- [x]", ignoreCase = true) || o.startsWith("* [x]", ignoreCase = true)
                                                val nChecked = n.startsWith("- [x]", ignoreCase = true) || n.startsWith("* [x]", ignoreCase = true)
                                                val oUnchecked = o.startsWith("- [ ]") || o.startsWith("* [ ]")
                                                val nUnchecked = n.startsWith("- [ ]") || n.startsWith("* [ ]")
                                                if (oUnchecked && nChecked) {
                                                    val text = n.substring(5).removePrefix("~~").removeSuffix("~~").trim()
                                                    onToggleChecklistTaskSync?.invoke(text, true)
                                                } else if (oChecked && nUnchecked) {
                                                    val text = n.substring(5).removePrefix("~~").removeSuffix("~~").trim()
                                                    onToggleChecklistTaskSync?.invoke(text, false)
                                                }
                                            }
                                        }
                                    }
                                }
                                editorValue = newVal
                            },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = textColor,
                                fontSize = 16.sp,
                                lineHeight = 26.sp
                            ),
                            cursorBrush = SolidColor(primaryColor),
                            visualTransformation = remember(editorValue.selection, isDarkBg, primaryColor, textColor) {
                                VisualTransformation { text ->
                                    val styled = ObsidianMarkdownEngine.buildLivePreviewAnnotatedString(
                                        text = text.text,
                                        selection = editorValue.selection,
                                        primaryColor = primaryColor,
                                        textColor = textColor,
                                        isDarkBg = isDarkBg
                                    )
                                    TransformedText(styled, OffsetMapping.Identity)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 450.dp)
                                .focusRequester(bodyFocusRequester)
                                .testTag("obsidian_live_preview_editor"),
                            decorationBox = { innerTextField ->
                                if (editorValue.text.isEmpty()) {
                                    Text(
                                        "Escreva sua nota livremente em Markdown contínuo...\n" +
                                        "# Título grande\n" +
                                        "- [ ] Checklist diário\n" +
                                        "**Negrito**, *itálico*, ==destaque== e links.",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            color = textColor.copy(alpha = 0.35f),
                                            lineHeight = 26.sp
                                        )
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }

                    NoteEditorMode.READING -> {
                        // MODO LEITURA LIMPO
                        // Permite edição/interação exclusivamente para 3 elementos:
                        // 1. Imagem: texto ao lado da imagem, controles de largura/alinhamento e tela cheia
                        // 2. Áudio: player estilo WhatsApp com controle de volume interativo
                        // 3. Bloco de código e matemática: edição e diálogo de tela cheia (com desfazer, refazer, copiar, colar)
                        fun updateBlocksAndMarkdown(newBlocks: List<VisualBlock>) {
                            pushHistory()
                            val md = SmoothMarkdownConverter.blocksToMarkdown(newBlocks)
                            editorValue = editorValue.copy(text = md)
                        }

                        fun centerBlockOnScreen(blockIndex: Int) {
                            if (readingBlocks.isNotEmpty() && noteScrollState.maxValue > 0) {
                                val ratio = blockIndex.toFloat() / readingBlocks.size.toFloat()
                                val target = ((ratio * noteScrollState.maxValue) - (noteScrollState.viewportSize / 3)).toInt()
                                    .coerceIn(0, noteScrollState.maxValue)
                                coroutineScope.launch {
                                    noteScrollState.animateScrollTo(target)
                                }
                            }
                        }

                        SelectionContainer {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 400.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                            readingBlocks.forEachIndexed { idx, block ->
                                when (block) {
                                    is HeadingBlock -> {
                                        val (fontSize, fontWeight) = when (block.level) {
                                            1 -> 24.sp to FontWeight.Bold
                                            2 -> 20.sp to FontWeight.Bold
                                            3 -> 17.sp to FontWeight.SemiBold
                                            4 -> 15.sp to FontWeight.SemiBold
                                            else -> 14.sp to FontWeight.SemiBold
                                        }
                                        val headingColor = if (block.level <= 2) {
                                            MaterialTheme.colorScheme.primary
                                        } else if (isDarkBg) {
                                            Color.White
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                        Text(
                                            text = InlineMarkdownHelper.parseInlineFormatting(block.text, isDark = isDarkBg),
                                            fontSize = fontSize,
                                            fontWeight = fontWeight,
                                            color = headingColor,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                        )
                                    }

                                    is TextBlock -> {
                                        if (block.text.isEmpty()) {
                                            Spacer(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(18.dp)
                                            )
                                        } else {
                                            var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
                                            val annotated = remember(block.text, isDarkBg) {
                                                InlineMarkdownHelper.parseInlineFormatting(block.text, isDark = isDarkBg)
                                            }
                                            val hasLinks = remember(annotated) {
                                                annotated.getStringAnnotations("LINK", 0, annotated.length).isNotEmpty()
                                            }
                                            Text(
                                                text = annotated,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    color = if (isDarkBg) Color.White else MaterialTheme.colorScheme.onSurface,
                                                    lineHeight = 22.sp
                                                ),
                                                onTextLayout = { layoutResult = it },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 2.dp)
                                                    .then(
                                                        if (hasLinks) {
                                                            Modifier.pointerInput(block.text) {
                                                                detectTapGestures(
                                                                    onTap = { pos ->
                                                                        val offset = layoutResult?.getOffsetForPosition(pos) ?: -1
                                                                        val links = if (offset != -1) annotated.getStringAnnotations("LINK", offset, offset) else emptyList()
                                                                        if (links.isNotEmpty()) {
                                                                            handleLinkClick(links.first().item)
                                                                        }
                                                                    }
                                                                )
                                                            }
                                                        } else Modifier
                                                    )
                                            )
                                        }
                                    }

                                    is ChecklistBlock -> {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = block.isChecked,
                                                onCheckedChange = { checked ->
                                                    val updated = block.copy(isChecked = checked)
                                                    val list = readingBlocks.toMutableList().apply { set(idx, updated) }
                                                    updateBlocksAndMarkdown(list)
                                                    onToggleChecklistTaskSync?.invoke(block.text, checked)
                                                },
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            val annotated = remember(block.text, isDarkBg) {
                                                InlineMarkdownHelper.parseInlineFormatting(block.text, isDark = isDarkBg)
                                            }
                                            Text(
                                                text = annotated,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    textDecoration = if (block.isChecked) TextDecoration.LineThrough else null,
                                                    color = if (block.isChecked) Color.Gray else (if (isDarkBg) Color.White else MaterialTheme.colorScheme.onSurface)
                                                ),
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }

                                    is BulletBlock -> {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 2.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Text(
                                                text = "•",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(end = 8.dp, start = 4.dp)
                                            )
                                            Text(
                                                text = InlineMarkdownHelper.parseInlineFormatting(block.text, isDark = isDarkBg),
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    color = if (isDarkBg) Color.White else MaterialTheme.colorScheme.onSurface
                                                ),
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }

                                    is QuoteBlock -> {
                                        Surface(
                                            shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp),
                                            color = if (isDarkBg) Color.White.copy(alpha = 0.06f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .width(4.dp)
                                                        .height(32.dp)
                                                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = InlineMarkdownHelper.parseInlineFormatting(block.text, isDark = isDarkBg),
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontStyle = FontStyle.Italic,
                                                        color = if (isDarkBg) Color.White else MaterialTheme.colorScheme.onSurface
                                                    )
                                                )
                                            }
                                        }
                                    }

                                    is TableBlock -> {
                                        ExcelTableEditor(
                                            block = block,
                                            isDarkBg = isDarkBg,
                                            onUpdate = { updated ->
                                                val list = readingBlocks.toMutableList().apply { set(idx, updated) }
                                                updateBlocksAndMarkdown(list)
                                            },
                                            onDelete = {
                                                val list = readingBlocks.toMutableList().apply { removeAt(idx) }
                                                updateBlocksAndMarkdown(list)
                                            }
                                        )
                                    }

                                    is CalloutBlock -> {
                                        val (bgColor, borderColor, icon, titleText) = when (block.type) {
                                            "tip" -> listOf(Color(0xFFECFDF5), Color(0xFF10B981), Icons.Default.Lightbulb, "DICA")
                                            "warning" -> listOf(Color(0xFFFFFBEB), Color(0xFFF59E0B), Icons.Default.Warning, "AVISO")
                                            "important" -> listOf(Color(0xFFFEF2F2), Color(0xFFEF4444), Icons.Default.Error, "IMPORTANTE")
                                            else -> listOf(Color(0xFFEFF6FF), Color(0xFF3B82F6), Icons.Default.Info, "INFO")
                                        }
                                        val bg = bgColor as Color
                                        val border = borderColor as Color
                                        val ic = icon as androidx.compose.ui.graphics.vector.ImageVector
                                        val label = titleText as String

                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isDarkBg) bg.copy(alpha = 0.15f) else bg,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .border(1.5.dp, border.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(ic, contentDescription = null, tint = border, modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(label, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = border)
                                                }
                                                if (block.text.isNotBlank()) {
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = InlineMarkdownHelper.parseInlineFormatting(block.text, isDark = isDarkBg),
                                                        style = MaterialTheme.typography.bodySmall.copy(
                                                            color = if (isDarkBg) Color.White else Color(0xFF1E293B)
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    is ToggleBlock -> {
                                        var isExpanded by remember { mutableStateOf(block.isExpanded) }
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isDarkBg) Color.White.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(8.dp)) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable { isExpanded = !isExpanded },
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = block.title.ifBlank { "Recolhível" },
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 13.sp,
                                                        color = if (isDarkBg) Color.White else MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                                AnimatedVisibility(visible = isExpanded) {
                                                    Column(modifier = Modifier.padding(start = 24.dp, top = 4.dp)) {
                                                        Text(
                                                            text = InlineMarkdownHelper.parseInlineFormatting(block.content, isDark = isDarkBg),
                                                            style = MaterialTheme.typography.bodySmall.copy(
                                                                color = if (isDarkBg) Color.LightGray else MaterialTheme.colorScheme.onSurface
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    is DividerBlock -> {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                        )
                                    }

                                    is PageBreakBlock -> {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                            Text(
                                                text = "Quebra de Página",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                        }
                                    }

                                    // OS 3 ELEMENTOS EDITÁVEIS/INTERATIVOS NO MODO LEITURA:
                                    // 1. Imagem: texto ao lado da imagem e controles de alinhamento/tamanho
                                    // 2. Áudio: player estilo WhatsApp com slider de volume e reprodução
                                    // 3. Código e Matemática: edição e botão de tela cheia com desfazer/refazer/copiar
                                    is ImageBlock, is AudioBlock, is CodeBlock, is MathBlock -> {
                                        VisualBlockItem(
                                            block = block,
                                            isDarkBg = isDarkBg,
                                            showPlaceholder = false,
                                            focusRequester = null,
                                            onFocusGained = {},
                                            onEnterPressed = {},
                                            onBackspaceOnEmpty = {},
                                            onToggleFullscreen = {
                                                fullscreenBlockIndex = idx
                                            },
                                            onLinkClick = { handleLinkClick(it) },
                                            onLongPressChecklist = null,
                                            onMoveUp = null,
                                            onMoveDown = null,
                                            onReplaceMedia = {
                                                if (block is ImageBlock) {
                                                    blockIndexToReplaceForImage = idx
                                                    replaceImagePickerLauncher.launch("image/*")
                                                } else if (block is AudioBlock) {
                                                    blockIndexToReplaceForAudio = idx
                                                    replaceAudioPickerLauncher.launch("audio/*")
                                                }
                                            },
                                            onUpdate = { updated ->
                                                val list = readingBlocks.toMutableList().apply { set(idx, updated) }
                                                updateBlocksAndMarkdown(list)
                                            },
                                            onDelete = {
                                                val list = readingBlocks.toMutableList().apply { removeAt(idx) }
                                                updateBlocksAndMarkdown(list)
                                            },
                                            isReadOnly = true
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                }

                // Espaço vazio ajustado para rolagem (reduzido em 6 parágrafos)
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                )
            }

            // Obsidian Formatting Toolbar (pinned to bottom by default, or floating when unpinned)
            // No modo leitura, a barra de inserção de formatação fica oculta para evitar corrupção de blocos
            AnimatedVisibility(
                visible = isToolbarVisible && editorMode != NoteEditorMode.READING,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(
                        bottom = if (isRecordingAudio) 72.dp else if (isToolbarPinnedToBottom && toolbarOffsetX == 0f && toolbarOffsetY == 0f) 0.dp else 12.dp,
                        start = if (isToolbarPinnedToBottom && toolbarOffsetX == 0f && toolbarOffsetY == 0f) 0.dp else 8.dp,
                        end = if (isToolbarPinnedToBottom && toolbarOffsetX == 0f && toolbarOffsetY == 0f) 0.dp else 8.dp
                    )
                    .offset {
                        IntOffset(toolbarOffsetX.roundToInt(), toolbarOffsetY.roundToInt())
                    }
            ) {
                val toolbarShape = if (isToolbarPinnedToBottom && toolbarOffsetX == 0f && toolbarOffsetY == 0f) {
                    RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                } else {
                    RoundedCornerShape(26.dp)
                }
                Surface(
                    shape = toolbarShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                    tonalElevation = 8.dp,
                    shadowElevation = 10.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), toolbarShape)
                ) {
                    if (isToolbarMinimized) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onDoubleTap = {
                                                toolbarOffsetX = 0f
                                                toolbarOffsetY = 0f
                                                savedFloatingOffsetX = 0f
                                                savedFloatingOffsetY = 0f
                                            }
                                        )
                                    }
                                    .pointerInput(Unit) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            toolbarOffsetX += dragAmount.x
                                            toolbarOffsetY += dragAmount.y
                                            savedFloatingOffsetX = toolbarOffsetX
                                            savedFloatingOffsetY = toolbarOffsetY
                                        }
                                    }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DragHandle,
                                    contentDescription = "Arrastar menu flutuante (duplo clique para resetar)",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = "Formatação Markdown",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { isToolbarMinimized = false }
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(
                                onClick = { isToolbarMinimized = false },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Expandir menu",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                // Mobility Drag Handle when unpinned; Lock indicator when fixed
                                if (!isToolbarPinnedToBottom) {
                                    Box(
                                        modifier = Modifier
                                            .pointerInput(Unit) {
                                                detectTapGestures(
                                                    onDoubleTap = {
                                                        toolbarOffsetX = 0f
                                                        toolbarOffsetY = 0f
                                                        savedFloatingOffsetX = 0f
                                                        savedFloatingOffsetY = 0f
                                                    }
                                                )
                                            }
                                            .pointerInput(Unit) {
                                                detectDragGestures { change, dragAmount ->
                                                    change.consume()
                                                    toolbarOffsetX += dragAmount.x
                                                    toolbarOffsetY += dragAmount.y
                                                    savedFloatingOffsetX = toolbarOffsetX
                                                    savedFloatingOffsetY = toolbarOffsetY
                                                }
                                            }
                                            .padding(horizontal = 4.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DragHandle,
                                            contentDescription = "Arrastar menu flutuante (duplo clique para resetar)",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                } else if (toolbarOffsetX != 0f || toolbarOffsetY != 0f) {
                                    Box(
                                        modifier = Modifier
                                            .clickable { isToolbarPinnedToBottom = false }
                                            .padding(horizontal = 4.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PushPin,
                                            contentDescription = "Barra fixada neste local (toque para desafixar)",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                // Botão de configurações para organizar menu flutuante (arrastar e soltar / reordenar)
                                IconButton(
                                    onClick = { showReorderToolbarDialog = true },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Organizar menu flutuante",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }

                                // Minimize toggle
                                IconButton(
                                    onClick = { isToolbarMinimized = true },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Minimizar",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                VerticalDivider(modifier = Modifier.height(20.dp).padding(horizontal = 2.dp), color = MaterialTheme.colorScheme.outlineVariant)

                                // Renderização dinâmica conforme a ordem configurada pelo usuário
                                for (toolId in toolbarItemsOrder) {
                                    when (toolId) {
                                        "bold" -> ToolbarToolButton(label = "B", icon = Icons.Default.FormatBold) {
                                            pushHistory()
                                            editorValue = ObsidianMarkdownEngine.wrapSelection(editorValue, "**", "**")
                                        }
                                        "italic" -> ToolbarToolButton(label = "I", icon = Icons.Default.FormatItalic) {
                                            pushHistory()
                                            editorValue = ObsidianMarkdownEngine.wrapSelection(editorValue, "*", "*")
                                        }
                                        "inline_code" -> ToolbarToolButton(label = "<>", icon = Icons.Default.Code) {
                                            if (editorMode == NoteEditorMode.READING) {
                                                Toast.makeText(context, "Mude para o modo de Edição para formatar texto", Toast.LENGTH_SHORT).show()
                                            } else {
                                                pushHistory()
                                                editorValue = ObsidianMarkdownEngine.wrapSelection(editorValue, "`", "`")
                                            }
                                        }
                                        "titles" -> Box {
                                            ToolbarToolButton(label = "Título", icon = Icons.Default.Title) {
                                                showTitlesMenu = true
                                            }
                                            DropdownMenu(
                                                expanded = showTitlesMenu,
                                                onDismissRequest = { showTitlesMenu = false }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("H1 - Título Principal", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                                                    onClick = {
                                                        showTitlesMenu = false
                                                        pushHistory()
                                                        editorValue = ObsidianMarkdownEngine.toggleLinePrefix(editorValue, "# ")
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("H2 - Subtítulo", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
                                                    onClick = {
                                                        showTitlesMenu = false
                                                        pushHistory()
                                                        editorValue = ObsidianMarkdownEngine.toggleLinePrefix(editorValue, "## ")
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("H3 - Seção", fontSize = 14.sp, fontWeight = FontWeight.Medium) },
                                                    onClick = {
                                                        showTitlesMenu = false
                                                        pushHistory()
                                                        editorValue = ObsidianMarkdownEngine.toggleLinePrefix(editorValue, "### ")
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("H4 - Subseção", fontSize = 13.sp) },
                                                    onClick = {
                                                        showTitlesMenu = false
                                                        pushHistory()
                                                        editorValue = ObsidianMarkdownEngine.toggleLinePrefix(editorValue, "#### ")
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("H5 - Tópico", fontSize = 12.sp) },
                                                    onClick = {
                                                        showTitlesMenu = false
                                                        pushHistory()
                                                        editorValue = ObsidianMarkdownEngine.toggleLinePrefix(editorValue, "##### ")
                                                    }
                                                )
                                            }
                                        }
                                        "checklist" -> ToolbarToolButton(label = "Checklist", icon = Icons.Default.CheckBox) {
                                            pushHistory()
                                            editorValue = ObsidianMarkdownEngine.toggleLinePrefix(editorValue, "- [ ] ")
                                        }
                                        "to_task" -> ToolbarToolButton(label = "Para Tarefa", icon = Icons.Default.AddTask) {
                                            val lines = editorValue.text.lines()
                                            val checklistLine = lines.firstOrNull { it.trim().startsWith("- [ ]") || it.trim().startsWith("* [ ]") }
                                                ?: lines.firstOrNull { it.trim().startsWith("- [x]") || it.trim().startsWith("* [x]") }
                                            val extracted = if (checklistLine != null) {
                                                checklistLine.trim().substring(5).removePrefix("~~").removeSuffix("~~").trim()
                                            } else {
                                                editorValue.text.take(60).trim().ifBlank { title.ifBlank { "Minha Tarefa" } }
                                            }
                                            checklistItemToConvert = extracted
                                        }
                                        "list" -> ToolbarToolButton(label = "Lista", icon = Icons.AutoMirrored.Filled.FormatListBulleted) {
                                            pushHistory()
                                            editorValue = ObsidianMarkdownEngine.toggleLinePrefix(editorValue, "- ")
                                        }
                                        "quote" -> ToolbarToolButton(label = "Citação", icon = Icons.Default.FormatQuote) {
                                            pushHistory()
                                            editorValue = ObsidianMarkdownEngine.toggleLinePrefix(editorValue, "> ")
                                        }
                                        "table" -> ToolbarToolButton(label = "Tabela", icon = Icons.Default.TableChart) {
                                            if (editorMode == NoteEditorMode.READING) {
                                                val defaultTable = "| Coluna 1 | Coluna 2 |\n| --- | --- |\n|  |  |"
                                                appendToEndOfNote(defaultTable)
                                            } else {
                                                showCreateTableDialog = true
                                            }
                                        }
                                        "code_block" -> ToolbarToolButton(label = "Código", icon = Icons.Default.Code) {
                                            if (editorMode == NoteEditorMode.READING) {
                                                Toast.makeText(context, "Mude para o modo de Edição para inserir código", Toast.LENGTH_SHORT).show()
                                            } else {
                                                pushHistory()
                                                editorValue = ObsidianMarkdownEngine.wrapOrInsertBlock(
                                                    editorValue,
                                                    "```kotlin\n",
                                                    "\n```",
                                                    "```kotlin\n// código\n```"
                                                )
                                            }
                                        }
                                        "math" -> ToolbarToolButton(label = "Math", icon = Icons.Default.Functions) {
                                            if (editorMode == NoteEditorMode.READING) {
                                                Toast.makeText(context, "Mude para o modo de Edição para inserir matemática", Toast.LENGTH_SHORT).show()
                                            } else {
                                                pushHistory()
                                                editorValue = ObsidianMarkdownEngine.wrapOrInsertBlock(
                                                    editorValue,
                                                    "$$\n",
                                                    "\n$$",
                                                    "$$ E = mc^2 $$"
                                                )
                                            }
                                        }
                                        "divider" -> ToolbarToolButton(label = "Divisor", icon = Icons.Default.HorizontalRule) {
                                            pushHistory()
                                            editorValue = ObsidianMarkdownEngine.insertMediaToken(editorValue, "---")
                                        }
                                        "image" -> ToolbarToolButton(label = "Imagem", icon = Icons.Default.Image) {
                                            imagePickerLauncher.launch("image/*")
                                        }
                                        "audio" -> ToolbarToolButton(label = "Áudio", icon = Icons.Default.Audiotrack) {
                                            audioPickerLauncher.launch("audio/*")
                                        }
                                        "record" -> ToolbarToolButton(
                                            label = if (isRecordingAudio) "Parar" else "Gravar",
                                            icon = if (isRecordingAudio) Icons.Default.Stop else Icons.Default.Mic
                                        ) {
                                            if (isRecordingAudio) {
                                                stopRecording()
                                            } else {
                                                recordAudioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                            }
                                        }
                                        "callout" -> Box {
                                            ToolbarToolButton(label = "Callout", icon = Icons.Default.Announcement) {
                                                showCalloutMenu = true
                                            }
                                            DropdownMenu(expanded = showCalloutMenu, onDismissRequest = { showCalloutMenu = false }) {
                                                DropdownMenuItem(text = { Text("ℹ️ Info") }, onClick = { showCalloutMenu = false; pushHistory(); editorValue = ObsidianMarkdownEngine.wrapSelectionInCallout(editorValue, "INFO", "Informação...") })
                                                DropdownMenuItem(text = { Text("💡 Dica") }, onClick = { showCalloutMenu = false; pushHistory(); editorValue = ObsidianMarkdownEngine.wrapSelectionInCallout(editorValue, "TIP", "Dica importante...") })
                                                DropdownMenuItem(text = { Text("⚠️ Aviso") }, onClick = { showCalloutMenu = false; pushHistory(); editorValue = ObsidianMarkdownEngine.wrapSelectionInCallout(editorValue, "WARNING", "Atenção...") })
                                                DropdownMenuItem(text = { Text("🚨 Importante") }, onClick = { showCalloutMenu = false; pushHistory(); editorValue = ObsidianMarkdownEngine.wrapSelectionInCallout(editorValue, "IMPORTANT", "Crítico...") })
                                            }
                                        }
                                        "details" -> ToolbarToolButton(label = "Recolhível", icon = Icons.Default.UnfoldMore) {
                                            pushHistory()
                                            editorValue = ObsidianMarkdownEngine.wrapOrInsertBlock(
                                                editorValue,
                                                "<details><summary>Resumo</summary>\n\n",
                                                "\n\n</details>",
                                                "<details><summary>Resumo</summary>\nConteúdo recolhível\n</details>"
                                            )
                                        }
                                    }
                                }

                                VerticalDivider(modifier = Modifier.height(20.dp).padding(horizontal = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)

                                // 4. Advanced expand toggle
                                IconButton(
                                    onClick = { showAdvancedToolbar = !showAdvancedToolbar },
                                    modifier = Modifier
                                        .size(34.dp)
                                        .background(
                                            if (showAdvancedToolbar) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                            CircleShape
                                        )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = "Opções Avançadas",
                                        tint = if (showAdvancedToolbar) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // Expanded secondary toolbar row
                            AnimatedVisibility(visible = showAdvancedToolbar) {
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                                    tonalElevation = 6.dp,
                                    modifier = Modifier
                                        .padding(horizontal = 12.dp, vertical = 4.dp)
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .horizontalScroll(rememberScrollState())
                                            .padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        ToolbarToolButton(label = "Tachado", icon = Icons.Default.FormatStrikethrough) {
                                            pushHistory()
                                            editorValue = ObsidianMarkdownEngine.wrapSelection(editorValue, "~~", "~~")
                                        }
                                        ToolbarToolButton(label = "Sublinhado", icon = Icons.Default.FormatUnderlined) {
                                            pushHistory()
                                            editorValue = ObsidianMarkdownEngine.wrapSelection(editorValue, "<u>", "</u>")
                                        }
                                        ToolbarToolButton(label = "Destaque", icon = Icons.Default.BorderColor) {
                                            pushHistory()
                                            editorValue = ObsidianMarkdownEngine.wrapSelection(editorValue, "==", "==")
                                        }

                                        ToolbarToolButton(label = "Quebra de página", icon = Icons.Default.HorizontalSplit) {
                                            pushHistory()
                                            editorValue = ObsidianMarkdownEngine.insertTextAtCursor(editorValue, "\n\n<!-- pagebreak -->\n\n")
                                            Toast.makeText(context, "Quebra de página inserida", Toast.LENGTH_SHORT).show()
                                        }

                                        VerticalDivider(modifier = Modifier.height(18.dp).padding(horizontal = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)

                                        // Callout dropdown
                                        Box {
                                            ToolbarToolButton(label = "Callout", icon = Icons.Default.Announcement) {
                                                showCalloutMenu = true
                                            }
                                            DropdownMenu(expanded = showCalloutMenu, onDismissRequest = { showCalloutMenu = false }) {
                                                DropdownMenuItem(text = { Text("ℹ️ Info") }, onClick = { showCalloutMenu = false; pushHistory(); editorValue = ObsidianMarkdownEngine.wrapSelectionInCallout(editorValue, "INFO", "Informação...") })
                                                DropdownMenuItem(text = { Text("💡 Dica") }, onClick = { showCalloutMenu = false; pushHistory(); editorValue = ObsidianMarkdownEngine.wrapSelectionInCallout(editorValue, "TIP", "Dica importante...") })
                                                DropdownMenuItem(text = { Text("⚠️ Aviso") }, onClick = { showCalloutMenu = false; pushHistory(); editorValue = ObsidianMarkdownEngine.wrapSelectionInCallout(editorValue, "WARNING", "Atenção...") })
                                                DropdownMenuItem(text = { Text("🚨 Importante") }, onClick = { showCalloutMenu = false; pushHistory(); editorValue = ObsidianMarkdownEngine.wrapSelectionInCallout(editorValue, "IMPORTANT", "Crítico...") })
                                            }
                                        }

                                        ToolbarToolButton(label = "Recolhível", icon = Icons.Default.UnfoldMore) {
                                            pushHistory()
                                            editorValue = ObsidianMarkdownEngine.wrapOrInsertBlock(
                                                editorValue,
                                                "<details><summary>Resumo</summary>\n\n",
                                                "\n\n</details>",
                                                "<details><summary>Resumo</summary>\nConteúdo recolhível\n</details>"
                                            )
                                        }

                                        ToolbarToolButton(label = "Buscar/Substituir", icon = Icons.Default.FindReplace) {
                                            showFindReplaceDialog = true
                                        }

                                        ToolbarToolButton(label = "Vincular", icon = Icons.Default.Share) {
                                            showLinkToEntityDialog = true
                                        }

                                        VerticalDivider(modifier = Modifier.height(18.dp).padding(horizontal = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)

                                        // Paginação dropdown (Nenhuma, 1., I.)
                                        Box {
                                            ToolbarToolButton(
                                                label = when (notePaginationType) {
                                                    NotePaginationType.NONE -> "Paginação"
                                                    NotePaginationType.ARABIC -> "Pág: 1."
                                                    NotePaginationType.ROMAN -> "Pág: I."
                                                },
                                                icon = Icons.Default.FormatListNumbered
                                            ) {
                                                showPaginationMenu = true
                                            }
                                            DropdownMenu(
                                                expanded = showPaginationMenu,
                                                onDismissRequest = { showPaginationMenu = false }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("Nenhuma (Sem numeração)") },
                                                    leadingIcon = {
                                                        if (notePaginationType == NotePaginationType.NONE) {
                                                            Icon(Icons.Default.Check, contentDescription = null, tint = primaryColor)
                                                        }
                                                    },
                                                    onClick = {
                                                        notePaginationType = NotePaginationType.NONE
                                                        showPaginationMenu = false
                                                        Toast.makeText(context, "Paginação desativada", Toast.LENGTH_SHORT).show()
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Numeração Arábica (1., 2., 3...)") },
                                                    leadingIcon = {
                                                        if (notePaginationType == NotePaginationType.ARABIC) {
                                                            Icon(Icons.Default.Check, contentDescription = null, tint = primaryColor)
                                                        }
                                                    },
                                                    onClick = {
                                                        notePaginationType = NotePaginationType.ARABIC
                                                        showPaginationMenu = false
                                                        Toast.makeText(context, "Paginação Arábica (1.) ativada para exportações", Toast.LENGTH_SHORT).show()
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Numeração Romana (I., II., III...)") },
                                                    leadingIcon = {
                                                        if (notePaginationType == NotePaginationType.ROMAN) {
                                                            Icon(Icons.Default.Check, contentDescription = null, tint = primaryColor)
                                                        }
                                                    },
                                                    onClick = {
                                                        notePaginationType = NotePaginationType.ROMAN
                                                        showPaginationMenu = false
                                                        Toast.makeText(context, "Paginação Romana (I.) ativada para exportações", Toast.LENGTH_SHORT).show()
                                                    }
                                                )
                                            }
                                        }

                                        // Índice automático
                                        ToolbarToolButton(label = "Índice", icon = Icons.AutoMirrored.Filled.Toc) {
                                            pushHistory()
                                            val generated = NoteExportHelper.generateTableOfContents(editorValue.text)
                                            if (generated == editorValue.text) {
                                                Toast.makeText(context, "Nenhum título (#) encontrado para gerar o índice.", Toast.LENGTH_SHORT).show()
                                            } else {
                                                editorValue = TextFieldValue(text = generated, selection = TextRange(0))
                                                Toast.makeText(context, "Índice gerado no início com quebra de página!", Toast.LENGTH_SHORT).show()
                                            }
                                        }

                                        VerticalDivider(modifier = Modifier.height(18.dp).padding(horizontal = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)

                                        ToolbarToolButton(
                                            label = if (isToolbarPinnedToBottom) "Desafixar" else "Fixar barra",
                                            icon = if (isToolbarPinnedToBottom) Icons.Default.OpenWith else Icons.Default.PushPin
                                        ) {
                                            if (isToolbarPinnedToBottom) {
                                                isToolbarPinnedToBottom = false
                                            } else {
                                                savedFloatingOffsetX = toolbarOffsetX
                                                savedFloatingOffsetY = toolbarOffsetY
                                                isToolbarPinnedToBottom = true
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // CitraRecord recording HUD
            if (isRecordingAudio) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(Color.Red, CircleShape)
                        )
                        val minutes = recordingSeconds / 60
                        val seconds = recordingSeconds % 60
                        Text(
                            text = String.format("%02d:%02d", minutes, seconds),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "Gravando em CitraRecord...",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Button(
                            onClick = { stopRecording() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Concluir", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Bottom-center pop-up feedback when saving note (removed per user request)
            androidx.compose.animation.AnimatedVisibility(
                visible = false,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .imePadding()
                    .padding(bottom = 76.dp)
                    .zIndex(100f)
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Nota salva",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }

    // Full screen Code or Math editor dialog with Undo, Redo, Copy, and Paste
    if (fullscreenBlockIndex != null) {
        val currentBlocks = SmoothMarkdownConverter.markdownToBlocks(editorValue.text)
        if (fullscreenBlockIndex!! in currentBlocks.indices) {
            val targetBlock = currentBlocks[fullscreenBlockIndex!!]
            FullScreenBlockDialog(
                block = targetBlock,
                onDismiss = { fullscreenBlockIndex = null },
                onUpdate = { updated ->
                    pushHistory()
                    val newBlocks = currentBlocks.toMutableList().apply { set(fullscreenBlockIndex!!, updated) }
                    val md = SmoothMarkdownConverter.blocksToMarkdown(newBlocks)
                    editorValue = editorValue.copy(text = md)
                }
            )
        }
    }

    // Full screen image preview, rotate & crop dialog
    if (fullscreenImageUrl != null) {
        val currentImg = fullscreenImageUrl!!
        ImageFullScreenCropRotateDialog(
            imageUrl = currentImg,
            onDismiss = { fullscreenImageUrl = null },
            onSaveImage = { newSavedPath ->
                pushHistory()
                val oldText = editorValue.text
                val updatedText = if (oldText.contains(currentImg)) {
                    oldText.replace(currentImg, newSavedPath)
                } else {
                    oldText
                }
                editorValue = editorValue.copy(text = updatedText)
                fullscreenImageUrl = null
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Imagem cortada/rotacionada e guardada com sucesso!")
                }
            }
        )
    }

    // Modal para criar tabela no estilo Excel (escolher linhas e colunas)
    if (showCreateTableDialog) {
        CreateTableDialog(
            onDismiss = { showCreateTableDialog = false },
            onConfirm = { rows, cols ->
                showCreateTableDialog = false
                pushHistory()
                val safeCols = cols.coerceIn(1, 20)
                val safeRows = rows.coerceIn(1, 50)
                val header = (1..safeCols).joinToString(" | ", prefix = "| ", postfix = " |") { "Coluna $it" }
                val separator = (1..safeCols).joinToString(" | ", prefix = "| ", postfix = " |") { "---" }
                val bodyLines = (1..safeRows).map { r ->
                    (1..safeCols).joinToString(" | ", prefix = "| ", postfix = " |") { "" }
                }
                val tableMarkdown = (listOf(header, separator) + bodyLines).joinToString("\n") + "\n"
                if (editorMode == NoteEditorMode.READING) {
                    appendToEndOfNote(tableMarkdown)
                } else {
                    editorValue = ObsidianMarkdownEngine.insertMediaToken(editorValue, tableMarkdown)
                }
            }
        )
    }

    // Audio Export Warning Modal per user requirement
    if (showAudioExportWarningDialog) {
        AlertDialog(
            onDismissRequest = { showAudioExportWarningDialog = false },
            icon = { Text("🎵", fontSize = 28.sp) },
            title = { Text("Exportação com Áudio", fontWeight = FontWeight.Bold) },
            text = {
                Text("O arquivo ficará bem maior por causa do áudio")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAudioExportWarningDialog = false
                        val blocks = SmoothMarkdownConverter.markdownToBlocks(editorValue.text)
                        val uri = NoteExportHelper.exportToHtmlFile(context, title, category, blocks, primaryColor.toArgb())
                        NoteExportHelper.shareUri(context, uri, "text/html", "Exportar Página Web (.html)")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                ) {
                    Text("Continuar", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAudioExportWarningDialog = false }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Link insertion/editing modal
    if (showLinkDialog) {
        LinkEditorDialog(
            allNotes = allNotes,
            onDismiss = { showLinkDialog = false },
            onConfirm = { target, label ->
                showLinkDialog = false
                pushHistory()
                val linkToken = if (label.isNotBlank()) "[$label]($target)" else "[$target]($target)"
                editorValue = ObsidianMarkdownEngine.insertMediaToken(editorValue, linkToken)
            }
        )
    }

    // Find and Replace modal
    if (showFindReplaceDialog) {
        FindReplaceDialog(
            onDismiss = { showFindReplaceDialog = false },
            onReplace = { find, replaceWith ->
                pushHistory()
                val updated = editorValue.text.replace(find, replaceWith)
                editorValue = editorValue.copy(text = updated)
            }
        )
    }

    // Convert Checklist to Task modal (Direct Task Creation using identical task creation form)
    if (checklistItemToConvert != null) {
        QuickAddTaskDialog(
            categories = categories,
            initialTitle = checklistItemToConvert!!,
            onDismiss = { checklistItemToConvert = null },
            onConfirm = { taskTitle, taskCategory, taskPriority, dueDateText, dueTimestamp, reminderMinutesBefore ->
                checklistItemToConvert = null
                if (onConvertChecklistToDirectTask != null) {
                    onConvertChecklistToDirectTask(taskTitle, taskCategory, taskPriority.name, dueDateText, dueTimestamp, reminderMinutesBefore)
                } else {
                    onConvertChecklistToTask?.invoke(taskTitle, dueDateText)
                }
                Toast.makeText(context, "Tarefa criada a partir do checklist: $taskTitle ($dueDateText) ⚡", Toast.LENGTH_SHORT).show()
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Tarefa criada com sucesso: $taskTitle ($dueDateText)")
                }
            }
        )
    }

    // Link note to Task, Habit, or Focus Session
    if (showLinkToEntityDialog) {
        LinkNoteToEntityDialog(
            tasks = tasks,
            habits = habits,
            onDismiss = { showLinkToEntityDialog = false },
            onSelectEntity = { description ->
                showLinkToEntityDialog = false
                pushHistory()
                val calloutToken = "> [!INFO] Vinculado a: $description"
                editorValue = ObsidianMarkdownEngine.insertMediaToken(editorValue, calloutToken)
            }
        )
    }

    // Note Color Picker Dialog
    if (showColorPickerDialog) {
        NoteColorPickerDialog(
            currentColorKey = noteColor,
            onDismiss = { showColorPickerDialog = false },
            onSelectColor = { selected ->
                noteColor = selected
            }
        )
    }

    // Background Image Customization Modal Bottom Sheet
    if (showBgSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBgSettingsSheet = false },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Ajustar Imagem de Fundo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Desfoque", fontSize = 13.sp)
                        Text("${(bgBlur * 100).toInt()}%", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = bgBlur,
                        onValueChange = { bgBlur = it },
                        valueRange = 0f..1f
                    )
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Luminosidade & Contraste", fontSize = 13.sp)
                        Text("${(bgBrightness * 100).toInt()}%", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = bgBrightness,
                        onValueChange = { bgBrightness = it },
                        valueRange = 0.15f..1f
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Mudar Imagem")
                    }

                    Button(
                        onClick = {
                            bgUri = null
                            processedBackgroundBitmap = null
                            showBgSettingsSheet = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RedAction),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Remover", color = Color.White)
                    }
                }
            }
        }
    }

    // Dialog for version history (Restore or Create local copy)
    if (showVersionHistoryDialog) {
        VersionHistoryDialog(
            versions = noteVersions,
            onDismiss = { showVersionHistoryDialog = false },
            onRestore = { version ->
                showVersionHistoryDialog = false
                pushHistory()
                title = version.title
                editorValue = TextFieldValue(version.content)
                onRestoreVersion?.invoke(version)
            },
            onCreateLocalCopy = { version ->
                showVersionHistoryDialog = false
                onCreateLocalCopy?.invoke(version)
            }
        )
    }

    // Modal de organização / reordenação do menu flutuante
    if (showReorderToolbarDialog) {
        ReorderToolbarDialog(
            initialOrder = toolbarItemsOrder,
            onDismiss = { showReorderToolbarDialog = false },
            onSave = { newOrder ->
                toolbarItemsOrder = newOrder
                preferencesManager.saveToolbarOrder(newOrder)
            }
        )
    }
}

@Composable
private fun VersionHistoryDialog(
    versions: List<NoteVersion>,
    onDismiss: () -> Unit,
    onRestore: (NoteVersion) -> Unit,
    onCreateLocalCopy: (NoteVersion) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Histórico de Versões", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            if (versions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Nenhuma versão anterior registrada para esta nota.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(versions.sortedByDescending { it.versionNumber }) { v ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Versão #${v.versionNumber}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = dateFormat.format(Date(v.timestamp)),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "Por: ${v.authorName}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = v.content.take(120).replace("\n", " ") + if (v.content.length > 120) "..." else "",
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = { onCreateLocalCopy(v) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Criar cópia local", fontSize = 11.sp)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = { onRestore(v) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Restaurar", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar")
            }
        }
    )
}

/**
 * WhatsApp-style Audio Player Widget for embedded [audio:Title](url) tokens
 * with interactive volume adjustment slider.
 */
@Composable
private fun ObsidianWhatsAppAudioPlayer(
    token: MediaTokenInfo,
    onUpdateTitle: (String) -> Unit,
    onReplace: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val playbackState by NoteAudioPlaybackManager.playbackState.collectAsState()
    val isCurrentAudio = NoteAudioPlaybackManager.isCurrentAudio(token.url, token.url)
    val isPlaying = isCurrentAudio && playbackState.isPlaying
    val currentPositionMs = if (isCurrentAudio) playbackState.currentPositionMs else 0
    val totalDurationMs = if (isCurrentAudio) playbackState.durationMs else 0

    var isEditingTitle by remember { mutableStateOf(false) }
    var editedTitle by remember(token.titleOrAlt) { mutableStateOf(token.titleOrAlt) }
    var volume by remember { mutableFloatStateOf(1.0f) }
    var showVolumeControl by remember { mutableStateOf(false) }

    LaunchedEffect(playbackState.volume, isCurrentAudio) {
        if (isCurrentAudio) {
            volume = playbackState.volume
        }
    }

    val isMediaValid = remember(token.url) {
        token.url.isBlank() || MediaStorageHelper.isMediaFileValid(context, token.url)
    }

    if (!isMediaValid && token.url.isNotBlank()) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Áudio não encontrado",
                    tint = MaterialTheme.colorScheme.error
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Arquivo de áudio não encontrado",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = token.titleOrAlt.ifBlank { token.url.substringAfterLast("/") },
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                    )
                }
                IconButton(onClick = onReplace, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = "Substituir áudio ausente",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remover áudio ausente",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        return
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Play / Pause WhatsApp button
                IconButton(
                    onClick = {
                        NoteAudioPlaybackManager.playOrPause(
                            context = context,
                            blockId = token.url,
                            url = token.url,
                            volume = volume,
                            isLoop = false
                        )
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFF10B981), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Reproduzir Áudio",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Audio Title and Scrubbing
                Column(modifier = Modifier.weight(1f)) {
                    if (isEditingTitle) {
                        BasicTextField(
                            value = editedTitle,
                            onValueChange = { editedTitle = it },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            singleLine = true,
                            keyboardActions = KeyboardActions(onDone = {
                                isEditingTitle = false
                                onUpdateTitle(editedTitle)
                            }),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = token.titleOrAlt.ifBlank { "Áudio Gravado" },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { isEditingTitle = true }
                        )
                    }

                    // Waveform Scrubbing Slider
                    val progress = if (totalDurationMs > 0) (currentPositionMs.toFloat() / totalDurationMs).coerceIn(0f, 1f) else 0f
                    Slider(
                        value = progress,
                        onValueChange = { fraction ->
                            if (totalDurationMs > 0) {
                                val targetMs = (fraction * totalDurationMs).toInt()
                                NoteAudioPlaybackManager.seekTo(targetMs)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val curSec = currentPositionMs / 1000
                        val totSec = totalDurationMs / 1000
                        Text(
                            text = String.format("%02d:%02d", curSec / 60, curSec % 60),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = String.format("%02d:%02d", totSec / 60, totSec % 60),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Volume button
                IconButton(
                    onClick = { showVolumeControl = !showVolumeControl },
                    modifier = Modifier
                        .size(32.dp)
                        .background(if (showVolumeControl) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Ajustar volume",
                        tint = if (showVolumeControl) MaterialTheme.colorScheme.primary else Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Replace audio file button
                IconButton(
                    onClick = onReplace,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = "Substituir áudio",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = {
                        if (isCurrentAudio) {
                            NoteAudioPlaybackManager.stop()
                        }
                        onDelete()
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Remover Áudio", tint = RedAction, modifier = Modifier.size(16.dp))
                }
            }

            // Expandable volume slider
            if (showVolumeControl) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, start = 8.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Slider(
                        value = volume,
                        onValueChange = { newV ->
                            volume = newV
                            NoteAudioPlaybackManager.setVolume(newV)
                        },
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f).height(18.dp)
                    )
                    Text(
                        text = "${(volume * 100).toInt()}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * Obsidian Resizable Image Widget with 25%, 50%, 75%, 100% sizing and alignment.
 */
@Composable
private fun ObsidianSideBySideImageWidget(
    token: MediaTokenInfo,
    isDarkBg: Boolean,
    onClickFullscreen: () -> Unit,
    onReplace: () -> Unit,
    onChangeWidth: (Int) -> Unit,
    onChangeAlign: (String) -> Unit,
    onChangeSideText: (String) -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Sizing and alignment toolbar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(25, 50, 75, 100).forEach { width ->
                        val isSelected = token.widthPercent == width
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            modifier = Modifier.clickable { onChangeWidth(width) }
                        ) {
                            Text(
                                text = "$width%",
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    listOf("left" to "Esq", "center" to "Meio", "right" to "Dir").forEach { (alignKey, label) ->
                        val isSelected = token.alignment == alignKey
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            modifier = Modifier.clickable { onChangeAlign(alignKey) }
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onReplace, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = "Substituir Imagem", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onClickFullscreen, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Default.Fullscreen, contentDescription = "Tela Cheia", modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Remover", tint = RedAction, modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Image display: side-by-side with text when width < 100% and aligned left or right
            val hasSideText = token.widthPercent < 100 && (token.alignment == "left" || token.alignment == "right")
            val fraction = (token.widthPercent / 100f).coerceIn(0.25f, 0.75f)

            if (hasSideText) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    if (token.alignment == "left") {
                        Box(modifier = Modifier.weight(fraction)) {
                            AsyncImage(
                                model = token.url,
                                contentDescription = token.titleOrAlt,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onClickFullscreen() },
                                contentScale = ContentScale.Crop
                            )
                        }
                        OutlinedTextField(
                            value = token.sideText,
                            onValueChange = onChangeSideText,
                            placeholder = { Text("Texto ao lado da imagem...", fontSize = 12.sp) },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                            singleLine = false,
                            maxLines = 15,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                            modifier = Modifier.weight(1f - fraction)
                        )
                    } else {
                        OutlinedTextField(
                            value = token.sideText,
                            onValueChange = onChangeSideText,
                            placeholder = { Text("Texto ao lado da imagem...", fontSize = 12.sp) },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                            singleLine = false,
                            maxLines = 15,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                            modifier = Modifier.weight(1f - fraction)
                        )
                        Box(modifier = Modifier.weight(fraction)) {
                            AsyncImage(
                                model = token.url,
                                contentDescription = token.titleOrAlt,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onClickFullscreen() },
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            } else {
                val alignment = when (token.alignment) {
                    "left" -> Alignment.CenterStart
                    "right" -> Alignment.CenterEnd
                    else -> Alignment.Center
                }
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = alignment
                ) {
                    AsyncImage(
                        model = token.url,
                        contentDescription = token.titleOrAlt,
                        modifier = Modifier
                            .fillMaxWidth((token.widthPercent / 100f).coerceIn(0.25f, 1f))
                            .heightIn(max = 300.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onClickFullscreen() },
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolbarToolButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
        shape = RoundedCornerShape(10.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(3.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}
