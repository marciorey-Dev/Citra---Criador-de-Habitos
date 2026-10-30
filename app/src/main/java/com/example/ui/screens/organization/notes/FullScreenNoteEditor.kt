package com.example.ui.screens.organization.notes

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Category
import com.example.data.model.Habit
import com.example.data.model.Note
import com.example.data.model.Task
import com.example.ui.FlexiblePomodoroState
import com.example.ui.theme.GreenAccent
import com.example.ui.theme.RedAction
import com.example.util.FastBlur
import com.example.util.InlineMarkdownHelper
import com.example.util.NoteExportHelper
import kotlinx.coroutines.launch

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

    // Visual Blocks representing the Smooth Markdown Document
    val initialBlocks = remember(initialNote) {
        SmoothMarkdownConverter.markdownToBlocks(initialNote?.content ?: "")
    }
    var blocks by remember { mutableStateOf(initialBlocks) }
    var focusedBlockIndex by remember { mutableStateOf(-1) }
    var isTitleFocused by remember { mutableStateOf(false) }

    // Dynamic Focus Requesters
    val blockFocusRequesters = remember { mutableStateMapOf<String, FocusRequester>() }
    val firstBlockFocusRequester = remember { FocusRequester() }

    fun focusBlock(blockId: String) {
        coroutineScope.launch {
            kotlinx.coroutines.delay(60)
            try {
                blockFocusRequesters[blockId]?.requestFocus()
            } catch (_: Exception) {}
        }
    }

    // Full screen focused environment for Code or Math block
    var fullScreenBlockIndex by remember { mutableStateOf<Int?>(null) }

    // Undo / Redo history
    val undoStack = remember { mutableStateListOf<List<VisualBlock>>() }
    val redoStack = remember { mutableStateListOf<List<VisualBlock>>() }

    fun pushHistory() {
        undoStack.add(blocks)
        if (undoStack.size > 30) undoStack.removeAt(0)
        redoStack.clear()
    }

    DisposableEffect(Unit) {
        onDispose {
            com.example.util.NoteAudioPlaybackManager.stop()
        }
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val prev = undoStack.removeAt(undoStack.size - 1)
            redoStack.add(blocks)
            blocks = prev
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val next = redoStack.removeAt(redoStack.size - 1)
            undoStack.add(blocks)
            blocks = next
        }
    }

    fun isCompletelyEmpty(): Boolean {
        val noTitle = title.isBlank()
        val noBlocks = blocks.isEmpty() || blocks.all {
            when (it) {
                is TextBlock -> it.text.isBlank()
                is HeadingBlock -> it.text.isBlank()
                is ChecklistBlock -> it.text.isBlank()
                is BulletBlock -> it.text.isBlank()
                is QuoteBlock -> it.text.isBlank()
                is CodeBlock -> it.code.isBlank()
                is MathBlock -> it.formula.isBlank()
                is CalloutBlock -> it.text.isBlank()
                is ToggleBlock -> it.content.isBlank() && it.title.isBlank()
                is AudioBlock -> it.url.isBlank()
                is ImageBlock -> it.url.isBlank()
                is TableBlock -> it.rows.all { r -> r.all { c -> c.isBlank() } }
                is DividerBlock -> false
            }
        }
        return noTitle && noBlocks
    }

    fun saveCurrent() {
        // User rule: If opened and left without writing anything, do NOT save
        if (isCompletelyEmpty() && initialNote == null) {
            onClose()
            return
        }
        val markdownContent = SmoothMarkdownConverter.blocksToMarkdown(blocks)
        onSaveAndClose(
            title.trim().ifBlank { "Sem título" },
            markdownContent,
            category,
            isPinned,
            bgUri,
            bgBlur,
            bgBrightness
        )
    }

    // Auto-save on back
    BackHandler {
        saveCurrent()
    }

    fun insertBlock(newBlock: VisualBlock, isDivider: Boolean = false) {
        pushHistory()
        val currentIdx = focusedBlockIndex
        if (currentIdx in blocks.indices) {
            val insertAt = if (isDivider) currentIdx + 1 else currentIdx
            blocks = blocks.toMutableList().apply { add(insertAt.coerceIn(0, size), newBlock) }
            focusedBlockIndex = insertAt
        } else {
            blocks = blocks + newBlock
            focusedBlockIndex = blocks.size - 1
        }
    }

    // Inline formatting applicator
    fun applyInlineFormatting(prefix: String, suffix: String) {
        pushHistory()
        if (isTitleFocused) {
            val (newT, _) = InlineMarkdownHelper.wrapSelection(title, 0, title.length, prefix, suffix)
            title = newT
            return
        }
        val currentIdx = focusedBlockIndex
        if (currentIdx in blocks.indices) {
            val block = blocks[currentIdx]
            when (block) {
                is TextBlock -> {
                    val (newT, _) = InlineMarkdownHelper.wrapSelection(block.text, 0, block.text.length, prefix, suffix)
                    blocks = blocks.toMutableList().apply { set(currentIdx, block.copy(text = newT)) }
                }
                is HeadingBlock -> {
                    val (newT, _) = InlineMarkdownHelper.wrapSelection(block.text, 0, block.text.length, prefix, suffix)
                    blocks = blocks.toMutableList().apply { set(currentIdx, block.copy(text = newT)) }
                }
                is QuoteBlock -> {
                    val (newT, _) = InlineMarkdownHelper.wrapSelection(block.text, 0, block.text.length, prefix, suffix)
                    blocks = blocks.toMutableList().apply { set(currentIdx, block.copy(text = newT)) }
                }
                is ChecklistBlock -> {
                    val (newT, _) = InlineMarkdownHelper.wrapSelection(block.text, 0, block.text.length, prefix, suffix)
                    blocks = blocks.toMutableList().apply { set(currentIdx, block.copy(text = newT)) }
                }
                is BulletBlock -> {
                    val (newT, _) = InlineMarkdownHelper.wrapSelection(block.text, 0, block.text.length, prefix, suffix)
                    blocks = blocks.toMutableList().apply { set(currentIdx, block.copy(text = newT)) }
                }
                else -> {}
            }
        }
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
                message = "Aviso: O link '$clean' não existe ou não é compatível.",
                duration = SnackbarDuration.Short
            )
        }
    }

    // Pickers
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
            bgUri = uri.toString()
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            insertBlock(ImageBlock(url = uri.toString(), alt = "Imagem", widthPercent = 100, alignment = "center"))
        }
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            insertBlock(AudioBlock(url = uri.toString(), title = "Áudio importado", volume = 1f, isLoop = false))
        }
    }

    val mdImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val text = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                if (text.isNotBlank()) {
                    pushHistory()
                    blocks = SmoothMarkdownConverter.markdownToBlocks(text)
                }
            } catch (_: Exception) {}
        }
    }

    // Top menu and modal dialog states
    var showMenu by remember { mutableStateOf(false) }
    var showTitlesMenu by remember { mutableStateOf(false) }
    var showBgSettingsSheet by remember { mutableStateOf(false) }
    var showAdvancedOptions by remember { mutableStateOf(false) }
    var showCalloutMenu by remember { mutableStateOf(false) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var showFindReplaceDialog by remember { mutableStateOf(false) }
    var showLinkToEntityDialog by remember { mutableStateOf(false) }
    var checklistItemToConvert by remember { mutableStateOf<ChecklistBlock?>(null) }

    // Scroll state for floating toolbar auto-hide
    val lazyListState = rememberLazyListState()
    var isToolbarVisible by remember { mutableStateOf(true) }
    var previousScrollIndex by remember { mutableStateOf(0) }
    var previousScrollOffset by remember { mutableStateOf(0) }

    LaunchedEffect(lazyListState.firstVisibleItemIndex, lazyListState.firstVisibleItemScrollOffset) {
        val currentIndex = lazyListState.firstVisibleItemIndex
        val currentOffset = lazyListState.firstVisibleItemScrollOffset
        if (currentIndex > previousScrollIndex || (currentIndex == previousScrollIndex && currentOffset > previousScrollOffset + 20)) {
            isToolbarVisible = false
        } else if (currentIndex < previousScrollIndex || (currentIndex == previousScrollIndex && currentOffset < previousScrollOffset - 20)) {
            isToolbarVisible = true
        }
        previousScrollIndex = currentIndex
        previousScrollOffset = currentOffset
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        if (processedBackgroundBitmap != null) {
            Image(
                bitmap = processedBackgroundBitmap!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = (1f - bgBrightness).coerceIn(0.1f, 0.88f)))
            )
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = if (processedBackgroundBitmap == null) MaterialTheme.colorScheme.surface else Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isPinned) {
                                Icon(
                                    imageVector = Icons.Default.PushPin,
                                    contentDescription = "Nota Fixada",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = if (title.isBlank()) "Anotação" else title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (processedBackgroundBitmap != null) Color.White else MaterialTheme.colorScheme.onSurface
                            )

                            if (pomodoroState != null && (pomodoroState.isRunning || pomodoroState.isPaused)) {
                                val mins = pomodoroState.remainingSeconds / 60
                                val secs = pomodoroState.remainingSeconds % 60
                                val timeFormatted = String.format("%02d:%02d", mins, secs)

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (pomodoroState.isRunning) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .clickable { onNavigateToTimer?.invoke() }
                                        .padding(start = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = "🍊", fontSize = 11.sp)
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = timeFormatted,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (pomodoroState.isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { saveCurrent() }, modifier = Modifier.testTag("note_back_save_btn")) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Voltar e Salvar",
                                tint = if (processedBackgroundBitmap != null) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { undo() }, enabled = undoStack.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "Desfazer",
                                tint = if (undoStack.isNotEmpty()) (if (processedBackgroundBitmap != null) Color.White else MaterialTheme.colorScheme.primary) else Color.Gray.copy(alpha = 0.35f)
                            )
                        }

                        IconButton(onClick = { redo() }, enabled = redoStack.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Redo,
                                contentDescription = "Refazer",
                                tint = if (redoStack.isNotEmpty()) (if (processedBackgroundBitmap != null) Color.White else MaterialTheme.colorScheme.primary) else Color.Gray.copy(alpha = 0.35f)
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
                                    tint = if (processedBackgroundBitmap != null) Color.White else MaterialTheme.colorScheme.onSurface
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
                                        val uri = NoteExportHelper.exportToPdf(context, title, category, blocks)
                                        NoteExportHelper.shareUri(context, uri, "application/pdf", "Exportar Nota em PDF")
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Exportar em Markdown (.md)") },
                                    leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        val uri = NoteExportHelper.exportToMdFile(context, title, blocks)
                                        NoteExportHelper.shareUri(context, uri, "text/markdown", "Exportar Nota (.md)")
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Exportar em Texto Puro (.txt)") },
                                    leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        val uri = NoteExportHelper.exportToTxtFile(context, title, category, blocks)
                                        NoteExportHelper.shareUri(context, uri, "text/plain", "Exportar Nota (.txt)")
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Exportar Página Web (.html)") },
                                    leadingIcon = { Icon(Icons.Default.Html, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        val uri = NoteExportHelper.exportToHtmlFile(context, title, category, blocks)
                                        NoteExportHelper.shareUri(context, uri, "text/html", "Exportar Página Web (.html)")
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Compartilhar como Imagem (Card)") },
                                    leadingIcon = { Icon(Icons.Default.Photo, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
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
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    contentPadding = PaddingValues(top = 74.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. Categories ABOVE Title
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(bottom = 4.dp)
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
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Label, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
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
                    }

                    // 2. Title Input (Enter moves to first block)
                    item {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { input ->
                                if (input.contains("\n")) {
                                    title = input.replace("\n", "")
                                    if (blocks.isEmpty()) {
                                        blocks = listOf(TextBlock(text = ""))
                                    }
                                    try { firstBlockFocusRequester.requestFocus() } catch (_: Exception) {}
                                } else {
                                    title = input
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = {
                                if (blocks.isEmpty()) {
                                    blocks = listOf(TextBlock(text = ""))
                                }
                                try { firstBlockFocusRequester.requestFocus() } catch (_: Exception) {}
                            }),
                            placeholder = {
                                Text(
                                    "Título da nota...",
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (processedBackgroundBitmap != null) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline
                                    )
                                )
                            },
                            textStyle = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (processedBackgroundBitmap != null) Color.White else MaterialTheme.colorScheme.onSurface
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
                    }

                    // 3. Visual Blocks (Smooth Markdown)
                    itemsIndexed(blocks, key = { _, block -> block.id }) { index, block ->
                        val isFirst = index == 0
                        val isOnlyEmptyBlock = blocks.size == 1 && block is TextBlock && block.text.isEmpty()
                        val blockRequester = blockFocusRequesters.getOrPut(block.id) { FocusRequester() }

                        VisualBlockItem(
                            block = block,
                            isDarkBg = processedBackgroundBitmap != null,
                            showPlaceholder = isOnlyEmptyBlock,
                            focusRequester = if (isFirst) firstBlockFocusRequester else blockRequester,
                            onFocusGained = {
                                focusedBlockIndex = index
                                isTitleFocused = false
                            },
                            onEnterPressed = {
                                pushHistory()
                                val newBlock = TextBlock(text = "")
                                val newBlockId = newBlock.id
                                blockFocusRequesters[newBlockId] = FocusRequester()
                                blocks = blocks.toMutableList().apply { add(index + 1, newBlock) }
                                focusedBlockIndex = index + 1
                                focusBlock(newBlockId)
                            },
                            onBackspaceOnEmpty = {
                                pushHistory()
                                blocks = blocks.toMutableList().apply { removeAt(index) }
                                focusedBlockIndex = (index - 1).coerceAtLeast(0)
                            },
                            onToggleFullscreen = {
                                fullScreenBlockIndex = index
                            },
                            onLinkClick = { handleLinkClick(it) },
                            onLongPressChecklist = { chk ->
                                checklistItemToConvert = chk
                            },
                            onUpdate = { updated ->
                                pushHistory()
                                blocks = blocks.toMutableList().apply { set(index, updated) }
                            },
                            onDelete = {
                                pushHistory()
                                blocks = blocks.toMutableList().apply { removeAt(index) }
                            }
                        )
                    }
                }

                // Floating Toolbar
                AnimatedVisibility(
                    visible = isToolbarVisible,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 8.dp,
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(22.dp))
                        ) {
                            Row(
                                modifier = Modifier
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                // 1. INLINE FORMATTING BUTTONS (at start)
                                ToolbarToolButton(label = "B", icon = Icons.Default.FormatBold) {
                                    applyInlineFormatting("**", "**")
                                }
                                ToolbarToolButton(label = "I", icon = Icons.Default.FormatItalic) {
                                    applyInlineFormatting("*", "*")
                                }
                                ToolbarToolButton(label = "<>", icon = Icons.Default.Code) {
                                    applyInlineFormatting("`", "`")
                                }
                                ToolbarToolButton(label = "Link", icon = Icons.Default.Link) {
                                    showLinkDialog = true
                                }

                                VerticalDivider(modifier = Modifier.height(20.dp).padding(horizontal = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)

                                // 2. BLOCK CREATION BUTTONS
                                ToolbarToolButton(label = "+ Texto", icon = Icons.Default.TextFields) {
                                    insertBlock(TextBlock(text = ""))
                                }

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
                                                insertBlock(HeadingBlock(level = 1, text = ""))
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("H2 - Subtítulo", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
                                            onClick = {
                                                showTitlesMenu = false
                                                insertBlock(HeadingBlock(level = 2, text = ""))
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("H3 - Seção", fontSize = 14.sp, fontWeight = FontWeight.Medium) },
                                            onClick = {
                                                showTitlesMenu = false
                                                insertBlock(HeadingBlock(level = 3, text = ""))
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("H4 - Subseção", fontSize = 13.sp) },
                                            onClick = {
                                                showTitlesMenu = false
                                                insertBlock(HeadingBlock(level = 4, text = ""))
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("H5 - Tópico Pequeno", fontSize = 12.sp) },
                                            onClick = {
                                                showTitlesMenu = false
                                                insertBlock(HeadingBlock(level = 5, text = ""))
                                            }
                                        )
                                    }
                                }

                                ToolbarToolButton(label = "Checklist", icon = Icons.Default.CheckBox) {
                                    insertBlock(ChecklistBlock(isChecked = false, text = ""))
                                }

                                ToolbarToolButton(label = "Lista", icon = Icons.Default.FormatListBulleted) {
                                    insertBlock(BulletBlock(text = ""))
                                }

                                ToolbarToolButton(label = "Tabela", icon = Icons.Default.TableChart) {
                                    insertBlock(TableBlock(headers = listOf("Coluna 1", "Coluna 2"), rows = listOf(listOf("", ""))))
                                }

                                ToolbarToolButton(label = "Citação", icon = Icons.Default.FormatQuote) {
                                    insertBlock(QuoteBlock(text = ""))
                                }

                                ToolbarToolButton(label = "Código", icon = Icons.Default.Code) {
                                    insertBlock(CodeBlock(code = "", language = "kotlin"))
                                }

                                ToolbarToolButton(label = "Math", icon = Icons.Default.Functions) {
                                    insertBlock(MathBlock(formula = "2^3"))
                                }

                                ToolbarToolButton(label = "Divisor", icon = Icons.Default.HorizontalRule) {
                                    insertBlock(DividerBlock(), isDivider = true)
                                }

                                VerticalDivider(modifier = Modifier.height(20.dp).padding(horizontal = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)

                                // 3. MEDIA BUTTONS (Last buttons on main bar)
                                ToolbarToolButton(label = "Imagem", icon = Icons.Default.Image) {
                                    imagePickerLauncher.launch("image/*")
                                }

                                ToolbarToolButton(label = "Áudio", icon = Icons.Default.Audiotrack) {
                                    audioPickerLauncher.launch("audio/*")
                                }

                                VerticalDivider(modifier = Modifier.height(20.dp).padding(horizontal = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)

                                // 4. ADVANCED TOGGLE BUTTON
                                IconButton(
                                    onClick = { showAdvancedOptions = !showAdvancedOptions },
                                    modifier = Modifier
                                        .size(34.dp)
                                        .background(
                                            if (showAdvancedOptions) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                            CircleShape
                                        )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = "Opções Avançadas",
                                        tint = if (showAdvancedOptions) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Expanded Advanced Toolbar row
                        AnimatedVisibility(visible = showAdvancedOptions) {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                                tonalElevation = 6.dp,
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
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
                                        applyInlineFormatting("~~", "~~")
                                    }
                                    ToolbarToolButton(label = "Sublinhado", icon = Icons.Default.FormatUnderlined) {
                                        applyInlineFormatting("<u>", "</u>")
                                    }
                                    ToolbarToolButton(label = "Destaque", icon = Icons.Default.BorderColor) {
                                        applyInlineFormatting("==", "==")
                                    }

                                    VerticalDivider(modifier = Modifier.height(18.dp).padding(horizontal = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)

                                    // Callout dropdown
                                    Box {
                                        ToolbarToolButton(label = "Callout", icon = Icons.Default.Announcement) {
                                            showCalloutMenu = true
                                        }
                                        DropdownMenu(expanded = showCalloutMenu, onDismissRequest = { showCalloutMenu = false }) {
                                            DropdownMenuItem(text = { Text("ℹ️ Info") }, onClick = { showCalloutMenu = false; insertBlock(CalloutBlock(type = "info", text = "")) })
                                            DropdownMenuItem(text = { Text("💡 Dica") }, onClick = { showCalloutMenu = false; insertBlock(CalloutBlock(type = "tip", text = "")) })
                                            DropdownMenuItem(text = { Text("⚠️ Aviso") }, onClick = { showCalloutMenu = false; insertBlock(CalloutBlock(type = "warning", text = "")) })
                                            DropdownMenuItem(text = { Text("🚨 Importante") }, onClick = { showCalloutMenu = false; insertBlock(CalloutBlock(type = "important", text = "")) })
                                        }
                                    }

                                    ToolbarToolButton(label = "Recolhível", icon = Icons.Default.UnfoldMore) {
                                        insertBlock(ToggleBlock(title = "Resumo", content = ""))
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
            }
        }
    }

    // Full Screen Focus Environment for Code or Math Block
    if (fullScreenBlockIndex != null && fullScreenBlockIndex!! in blocks.indices) {
        val targetBlock = blocks[fullScreenBlockIndex!!]
        FullScreenBlockDialog(
            block = targetBlock,
            onDismiss = { fullScreenBlockIndex = null },
            onUpdate = { updated ->
                pushHistory()
                blocks = blocks.toMutableList().apply { set(fullScreenBlockIndex!!, updated) }
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
                val formatted = if (label.isNotBlank()) "$target|$label" else target
                applyInlineFormatting("", formatted)
            }
        )
    }

    // Find and Replace modal
    if (showFindReplaceDialog) {
        FindReplaceDialog(
            onDismiss = { showFindReplaceDialog = false },
            onReplace = { find, replaceWith ->
                pushHistory()
                blocks = blocks.map { block ->
                    when (block) {
                        is TextBlock -> block.copy(text = block.text.replace(find, replaceWith))
                        is HeadingBlock -> block.copy(text = block.text.replace(find, replaceWith))
                        is ChecklistBlock -> block.copy(text = block.text.replace(find, replaceWith))
                        is BulletBlock -> block.copy(text = block.text.replace(find, replaceWith))
                        is QuoteBlock -> block.copy(text = block.text.replace(find, replaceWith))
                        is CodeBlock -> block.copy(code = block.code.replace(find, replaceWith))
                        is MathBlock -> block.copy(formula = block.formula.replace(find, replaceWith))
                        is CalloutBlock -> block.copy(text = block.text.replace(find, replaceWith))
                        is ToggleBlock -> block.copy(title = block.title.replace(find, replaceWith), content = block.content.replace(find, replaceWith))
                        else -> block
                    }
                }
            }
        )
    }

    // Convert Checklist to Task modal
    if (checklistItemToConvert != null) {
        ConvertChecklistToTaskDialog(
            initialTitle = checklistItemToConvert!!.text,
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
                insertBlock(CalloutBlock(type = "info", text = "Vinculado a: $description"))
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
