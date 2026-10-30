package com.example.ui.screens.organization.notes

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.model.Category
import com.example.data.model.Habit
import com.example.data.model.Note
import com.example.data.model.Task
import com.example.ui.FlexiblePomodoroState
import com.example.ui.theme.GreenAccent
import com.example.ui.theme.RedAction
import com.example.util.CitraDocsFileManager
import com.example.util.FastBlur
import com.example.util.MediaStorageHelper
import com.example.util.InlineMarkdownHelper
import com.example.util.NoteAudioPlaybackManager
import com.example.util.NoteExportHelper
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

enum class NoteEditorMode(val label: String) {
    LIVE_PREVIEW("Live Preview"),
    READING("Modo Leitura")
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
    onSaveAndClose: (title: String, content: String, category: String, isPinned: Boolean, bgUri: String?, blur: Float, brightness: Float) -> Unit,
    onClose: () -> Unit = {},
    onDelete: (() -> Unit)? = null,
    onOpenLinkedNote: ((Note) -> Unit)? = null,
    onConvertChecklistToTask: ((title: String, date: String) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var title by remember { mutableStateOf(initialNote?.title ?: "") }
    var category by remember { mutableStateOf(initialNote?.category ?: categories.firstOrNull()?.name ?: "Geral") }
    var isPinned by remember { mutableStateOf(initialNote?.isPinned ?: false) }
    var bgUri by remember { mutableStateOf(initialNote?.backgroundImageUri) }
    var bgBlur by remember { mutableStateOf(initialNote?.backgroundBlur ?: 0.5f) }
    var bgBrightness by remember { mutableStateOf(initialNote?.backgroundBrightness ?: 0.6f) }

    // Unified continuous Markdown document (Obsidian style)
    var editorValue by remember {
        mutableStateOf(TextFieldValue(initialNote?.content ?: ""))
    }
    // Only Live Preview and Modo Leitura as requested
    var editorMode by remember { mutableStateOf(NoteEditorMode.LIVE_PREVIEW) }

    val bodyFocusRequester = remember { FocusRequester() }
    val isDarkBg = bgUri != null
    val textColor = if (isDarkBg) Color.White else MaterialTheme.colorScheme.onSurface
    val primaryColor = MaterialTheme.colorScheme.primary

    // Loaded blurred background bitmap cache
    var processedBackgroundBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(bgUri, bgBlur) {
        if (!bgUri.isNullOrBlank()) {
            try {
                val uri = Uri.parse(bgUri)
                val stream = context.contentResolver.openInputStream(uri)
                val original = BitmapFactory.decodeStream(stream)
                if (original != null) {
                    val radius = (bgBlur * 24).toInt().coerceAtLeast(0)
                    processedBackgroundBitmap = if (radius > 0) FastBlur.blur(original, radius) else original
                }
            } catch (_: Exception) {
                processedBackgroundBitmap = null
            }
        } else {
            processedBackgroundBitmap = null
        }
    }

    // Undo / Redo history
    val undoStack = remember { mutableStateListOf<TextFieldValue>() }
    val redoStack = remember { mutableStateListOf<TextFieldValue>() }

    fun pushHistory() {
        undoStack.add(editorValue)
        if (undoStack.size > 40) undoStack.removeAt(0)
        redoStack.clear()
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val prev = undoStack.removeAt(undoStack.lastIndex)
            redoStack.add(editorValue)
            editorValue = prev
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val next = redoStack.removeAt(redoStack.lastIndex)
            undoStack.add(editorValue)
            editorValue = next
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            NoteAudioPlaybackManager.stop()
        }
    }

    fun isCompletelyEmpty(): Boolean {
        return title.isBlank() && editorValue.text.isBlank()
    }

    fun saveCurrent() {
        if (isCompletelyEmpty() && initialNote == null) {
            onClose()
            return
        }
        val finalTitle = title.trim().ifBlank { "Sem título" }
        val finalContent = editorValue.text

        // Sync directly to physical .md file in Documents/Citra Docs/
        CitraDocsFileManager.saveNoteMarkdown(context, finalTitle, finalContent)

        onSaveAndClose(
            finalTitle,
            finalContent,
            category,
            isPinned,
            bgUri,
            bgBlur,
            bgBrightness
        )
    }

    // Auto-save on back press
    BackHandler {
        saveCurrent()
    }

    // Link navigation handler
    fun handleLinkClick(target: String) {
        val clean = target.trim()
        val matchedNote = allNotes.firstOrNull { it.title.equals(clean, ignoreCase = true) }
        if (matchedNote != null) {
            onOpenLinkedNote?.invoke(matchedNote)
            return
        }
        if (clean.startsWith("http://") || clean.startsWith("https://") || clean.startsWith("www.")) {
            try {
                val url = if (clean.startsWith("www.")) "https://$clean" else clean
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                return
            } catch (_: Exception) {}
        }
        coroutineScope.launch {
            snackbarHostState.showSnackbar(
                message = "Link '$clean' acionado.",
                duration = SnackbarDuration.Short
            )
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
            bgUri = uri.toString()
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

    // Parse active media tokens for interactive widgets in Live Preview
    val parsedMediaTokens = remember(editorValue.text) {
        ObsidianMarkdownEngine.parseMediaTokens(editorValue.text)
    }

    // Markdown file import launcher
    val mdImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val importedText = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    pushHistory()
                    editorValue = TextFieldValue(importedText, selection = TextRange(importedText.length))
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Nota Markdown importada com sucesso!")
                    }
                }
            } catch (e: Exception) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Falha ao importar ficheiro Markdown.")
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (title.isBlank()) "Nova Nota" else title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        // Mode selector pill (Toggle strictly between Live Preview & Modo Leitura)
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                            modifier = Modifier.clickable {
                                editorMode = if (editorMode == NoteEditorMode.LIVE_PREVIEW) {
                                    NoteEditorMode.READING
                                } else {
                                    NoteEditorMode.LIVE_PREVIEW
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (editorMode == NoteEditorMode.LIVE_PREVIEW) {
                                        Icons.Default.AutoAwesome
                                    } else {
                                        Icons.AutoMirrored.Filled.MenuBook
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = editorMode.label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { saveCurrent() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar e Salvar",
                            tint = textColor
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { undo() }, enabled = undoStack.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Desfazer",
                            tint = if (undoStack.isNotEmpty()) primaryColor else Color.Gray.copy(alpha = 0.35f)
                        )
                    }

                    IconButton(onClick = { redo() }, enabled = redoStack.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "Refazer",
                            tint = if (redoStack.isNotEmpty()) primaryColor else Color.Gray.copy(alpha = 0.35f)
                        )
                    }

                    IconButton(onClick = { saveCurrent() }) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Salvar",
                            tint = GreenAccent
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

                            HorizontalDivider()

                            // EXPORT MENU OPTIONS (PDF, MD, TXT, HTML, Image Card)
                            DropdownMenuItem(
                                text = { Text("Exportar em PDF (.pdf)") },
                                leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = RedAction) },
                                onClick = {
                                    showMenu = false
                                    val blocks = SmoothMarkdownConverter.markdownToBlocks(editorValue.text)
                                    val uri = NoteExportHelper.exportToPdf(context, title, category, blocks)
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
                                    val uri = NoteExportHelper.exportToHtmlFile(context, title, category, blocks)
                                    NoteExportHelper.shareUri(context, uri, "text/html", "Exportar Página Web (.html)")
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Compartilhar como Imagem (Card)") },
                                leadingIcon = { Icon(Icons.Default.Photo, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    val blocks = SmoothMarkdownConverter.markdownToBlocks(editorValue.text)
                                    val uri = NoteExportHelper.shareAsImage(context, title, category, blocks)
                                    NoteExportHelper.shareUri(context, uri, "image/png", "Compartilhar Imagem da Nota")
                                }
                            )

                            HorizontalDivider()

                            DropdownMenuItem(
                                text = { Text("Importar ficheiro (.md)") },
                                leadingIcon = { Icon(Icons.Default.FileUpload, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    mdImportLauncher.launch("*/*")
                                }
                            )

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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (processedBackgroundBitmap == null) MaterialTheme.colorScheme.surface else Color.Transparent
                )
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

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 10.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Categories above Title
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 2.dp)
                ) {
                    items(categories) { cat ->
                        val isSelected = category == cat.name
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .clickable { category = cat.name }
                                .border(
                                    width = if (isSelected) 1.5.dp else 0.dp,
                                    color = if (isSelected) primaryColor else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Label, contentDescription = null, modifier = Modifier.size(13.dp), tint = primaryColor)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = cat.name,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // 2. Note Title Input
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
                            "Título da nota...",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = textColor.copy(alpha = 0.45f)
                            )
                        )
                    },
                    textStyle = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    ),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_full_title_input")
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

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
                                    onChangeWidth = { newWidth ->
                                        pushHistory()
                                        val newToken = "![${token.alignment}|$newWidth%](${token.url})"
                                        val newText = editorValue.text.replace(token.rawMatch, newToken)
                                        editorValue = editorValue.copy(text = newText)
                                    },
                                    onChangeAlign = { newAlign ->
                                        pushHistory()
                                        val newToken = "![$newAlign|${token.widthPercent}%](${token.url})"
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
                        val readingBlocks = remember(editorValue.text) {
                            SmoothMarkdownConverter.markdownToBlocks(editorValue.text)
                        }

                        fun updateBlocksAndMarkdown(newBlocks: List<VisualBlock>) {
                            pushHistory()
                            val md = SmoothMarkdownConverter.blocksToMarkdown(newBlocks)
                            editorValue = editorValue.copy(text = md)
                        }

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
                                        var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
                                        val annotated = remember(block.text, isDarkBg) {
                                            InlineMarkdownHelper.parseInlineFormatting(block.text, isDark = isDarkBg)
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
                                                .pointerInput(block.text) {
                                                    detectTapGestures(
                                                        onTap = { pos ->
                                                            val offset = layoutResult?.getOffsetForPosition(pos) ?: -1
                                                            if (offset != -1) {
                                                                val links = annotated.getStringAnnotations("LINK", offset, offset)
                                                                if (links.isNotEmpty()) {
                                                                    handleLinkClick(links.first().item)
                                                                }
                                                            }
                                                        }
                                                    )
                                                }
                                        )
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
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .pointerInput(block.text) {
                                                        detectTapGestures(
                                                            onLongPress = {
                                                                checklistItemToConvert = block.text
                                                            }
                                                        )
                                                    }
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
                                }
                            }
                        }
                    }
                }
            }

            // Floating Obsidian Formatting Toolbar (Docked at top/center of screen)
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp, start = 12.dp, end = 12.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(22.dp))
            ) {
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
                        // 1. Inline formatters
                        ToolbarToolButton(label = "B", icon = Icons.Default.FormatBold) {
                            pushHistory()
                            editorValue = ObsidianMarkdownEngine.wrapSelection(editorValue, "**", "**")
                        }
                        ToolbarToolButton(label = "I", icon = Icons.Default.FormatItalic) {
                            pushHistory()
                            editorValue = ObsidianMarkdownEngine.wrapSelection(editorValue, "*", "*")
                        }
                        ToolbarToolButton(label = "<>", icon = Icons.Default.Code) {
                            pushHistory()
                            editorValue = ObsidianMarkdownEngine.wrapSelection(editorValue, "`", "`")
                        }
                        ToolbarToolButton(label = "Link", icon = Icons.Default.Link) {
                            showLinkDialog = true
                        }

                        VerticalDivider(modifier = Modifier.height(20.dp).padding(horizontal = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)

                        // 2. Line Prefix Tools (Headers, Checklists, Lists, Tables)
                        Box {
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

                        ToolbarToolButton(label = "Checklist", icon = Icons.Default.CheckBox) {
                            pushHistory()
                            editorValue = ObsidianMarkdownEngine.toggleLinePrefix(editorValue, "- [ ] ")
                        }

                        ToolbarToolButton(label = "Lista", icon = Icons.AutoMirrored.Filled.FormatListBulleted) {
                            pushHistory()
                            editorValue = ObsidianMarkdownEngine.toggleLinePrefix(editorValue, "- ")
                        }

                        ToolbarToolButton(label = "Citação", icon = Icons.Default.FormatQuote) {
                            pushHistory()
                            editorValue = ObsidianMarkdownEngine.toggleLinePrefix(editorValue, "> ")
                        }

                        ToolbarToolButton(label = "Tabela", icon = Icons.Default.TableChart) {
                            showCreateTableDialog = true
                        }

                        ToolbarToolButton(label = "Divisor", icon = Icons.Default.HorizontalRule) {
                            pushHistory()
                            editorValue = ObsidianMarkdownEngine.insertMediaToken(editorValue, "---")
                        }

                        VerticalDivider(modifier = Modifier.height(20.dp).padding(horizontal = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)

                        // 3. Media buttons (Images & Audios)
                        ToolbarToolButton(label = "Imagem", icon = Icons.Default.Image) {
                            imagePickerLauncher.launch("image/*")
                        }

                        ToolbarToolButton(label = "Áudio", icon = Icons.Default.Audiotrack) {
                            audioPickerLauncher.launch("audio/*")
                        }

                        ToolbarToolButton(
                            label = if (isRecordingAudio) "Parar" else "Gravar",
                            icon = if (isRecordingAudio) Icons.Default.Stop else Icons.Default.Mic
                        ) {
                            if (isRecordingAudio) {
                                stopRecording()
                            } else {
                                recordAudioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
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
                                ToolbarToolButton(label = "Bloco Código", icon = Icons.Default.Code) {
                                    pushHistory()
                                    editorValue = ObsidianMarkdownEngine.insertMediaToken(editorValue, "```kotlin\n// código\n```")
                                }
                                ToolbarToolButton(label = "Math", icon = Icons.Default.Functions) {
                                    pushHistory()
                                    editorValue = ObsidianMarkdownEngine.insertMediaToken(editorValue, "$$ E = mc^2 $$")
                                }

                                VerticalDivider(modifier = Modifier.height(18.dp).padding(horizontal = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)

                                // Callout dropdown
                                Box {
                                    ToolbarToolButton(label = "Callout", icon = Icons.Default.Announcement) {
                                        showCalloutMenu = true
                                    }
                                    DropdownMenu(expanded = showCalloutMenu, onDismissRequest = { showCalloutMenu = false }) {
                                        DropdownMenuItem(text = { Text("ℹ️ Info") }, onClick = { showCalloutMenu = false; pushHistory(); editorValue = ObsidianMarkdownEngine.insertMediaToken(editorValue, "> [!INFO] Informação...") })
                                        DropdownMenuItem(text = { Text("💡 Dica") }, onClick = { showCalloutMenu = false; pushHistory(); editorValue = ObsidianMarkdownEngine.insertMediaToken(editorValue, "> [!TIP] Dica importante...") })
                                        DropdownMenuItem(text = { Text("⚠️ Aviso") }, onClick = { showCalloutMenu = false; pushHistory(); editorValue = ObsidianMarkdownEngine.insertMediaToken(editorValue, "> [!WARNING] Atenção...") })
                                        DropdownMenuItem(text = { Text("🚨 Importante") }, onClick = { showCalloutMenu = false; pushHistory(); editorValue = ObsidianMarkdownEngine.insertMediaToken(editorValue, "> [!IMPORTANT] Crítico...") })
                                    }
                                }

                                ToolbarToolButton(label = "Recolhível", icon = Icons.Default.UnfoldMore) {
                                    pushHistory()
                                    editorValue = ObsidianMarkdownEngine.insertMediaToken(editorValue, "<details><summary>Resumo</summary>\nConteúdo recolhível\n</details>")
                                }

                                ToolbarToolButton(label = "Buscar/Substituir", icon = Icons.Default.FindReplace) {
                                    showFindReplaceDialog = true
                                }

                                ToolbarToolButton(label = "Vincular", icon = Icons.Default.Share) {
                                    showLinkToEntityDialog = true
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

    // Full screen image preview dialog
    if (fullscreenImageUrl != null) {
        Dialog(
            onDismissRequest = { fullscreenImageUrl = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = fullscreenImageUrl,
                    contentDescription = "Visualização da Imagem",
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { fullscreenImageUrl = null },
                    contentScale = ContentScale.Fit
                )
                IconButton(
                    onClick = { fullscreenImageUrl = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.White)
                }
            }
        }
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
                val tableMarkdown = (listOf(header, separator) + bodyLines).joinToString("\n")
                editorValue = ObsidianMarkdownEngine.insertMediaToken(editorValue, tableMarkdown)
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

    // Convert Checklist to Task modal
    if (checklistItemToConvert != null) {
        ConvertChecklistToTaskDialog(
            initialTitle = checklistItemToConvert!!,
            onDismiss = { checklistItemToConvert = null },
            onConfirm = { taskTitle, dueDate ->
                checklistItemToConvert = null
                onConvertChecklistToTask?.invoke(taskTitle, dueDate)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Tarefa criada com sucesso: $taskTitle ($dueDate)")
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
                        Text("Desfoque Gaussiano (Blur)", fontSize = 13.sp)
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
}

/**
 * WhatsApp-style Audio Player Widget for embedded [audio:Title](url) tokens
 * with interactive volume adjustment slider.
 */
@Composable
private fun ObsidianWhatsAppAudioPlayer(
    token: MediaTokenInfo,
    onUpdateTitle: (String) -> Unit,
    onDelete: () -> Unit
) {
    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableStateOf(0) }
    var totalDurationMs by remember { mutableStateOf(0) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isEditingTitle by remember { mutableStateOf(false) }
    var editedTitle by remember(token.titleOrAlt) { mutableStateOf(token.titleOrAlt) }
    var volume by remember { mutableStateOf(1.0f) }
    var showVolumeControl by remember { mutableStateOf(false) }

    DisposableEffect(token.url) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
            isPlaying = false
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            mediaPlayer?.let {
                currentPositionMs = it.currentPosition
                totalDurationMs = it.duration.coerceAtLeast(0)
            }
            kotlinx.coroutines.delay(200)
        }
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
                        if (isPlaying) {
                            mediaPlayer?.pause()
                            isPlaying = false
                        } else {
                            try {
                                if (mediaPlayer == null) {
                                    val mp = MediaPlayer().apply {
                                        setDataSource(token.url)
                                        setVolume(volume, volume)
                                        prepare()
                                        setOnCompletionListener {
                                            isPlaying = false
                                            currentPositionMs = 0
                                        }
                                    }
                                    mediaPlayer = mp
                                    totalDurationMs = mp.duration.coerceAtLeast(0)
                                }
                                mediaPlayer?.setVolume(volume, volume)
                                mediaPlayer?.start()
                                isPlaying = true
                            } catch (_: Exception) {}
                        }
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
                            val targetMs = (fraction * totalDurationMs).toInt()
                            currentPositionMs = targetMs
                            mediaPlayer?.seekTo(targetMs)
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

                IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
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
                            mediaPlayer?.setVolume(newV, newV)
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
    onChangeWidth: (Int) -> Unit,
    onChangeAlign: (String) -> Unit,
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
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClickFullscreen, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Default.Fullscreen, contentDescription = "Tela Cheia", modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Remover", tint = RedAction, modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Image display aligned according to width and alignment
            val fraction = (token.widthPercent / 100f).coerceIn(0.25f, 1f)
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
                        .fillMaxWidth(fraction)
                        .heightIn(max = 300.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onClickFullscreen() },
                    contentScale = ContentScale.Crop
                )
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
