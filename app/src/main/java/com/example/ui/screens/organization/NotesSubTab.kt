package com.example.ui.screens.organization

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.model.Note
import com.example.data.model.NoteDocumentType
import com.example.data.model.NoteVersion
import com.example.data.model.TaskPriority
import com.example.data.model.isDocxDocument
import com.example.data.model.isExternalDocument
import com.example.data.model.isPdfDocument
import com.example.data.model.isReadyForMainList
import com.example.data.model.resolvedDocumentType
import com.example.ui.MainViewModel
import com.example.ui.components.AddCategoryDialog
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.screens.organization.notes.CategoryNoteStack
import com.example.ui.screens.organization.notes.ExternalDocxReaderScreen
import com.example.ui.screens.organization.notes.ExternalPdfReaderScreen
import com.example.ui.screens.organization.notes.FullScreenNoteEditor
import com.example.ui.screens.organization.notes.NoteCategoryPickerDialog
import com.example.ui.screens.organization.notes.NoteDocumentFilter
import com.example.ui.screens.organization.notes.NoteEditorMode
import com.example.ui.screens.organization.notes.NoteLayoutMode
import com.example.ui.screens.organization.notes.NoteReadingPreview
import com.example.ui.screens.organization.notes.NoteSortOption
import com.example.ui.screens.organization.notes.PaperNoteColors
import com.example.ui.screens.organization.notes.PaperNotePalette
import com.example.ui.screens.organization.notes.PaperNoteSurface
import com.example.ui.screens.organization.notes.SmoothMarkdownConverter
import com.example.ui.screens.organization.notes.filterNotesByDocumentType
import com.example.ui.screens.organization.notes.groupNotesByCategory
import com.example.ui.screens.organization.notes.sortNotes
import com.example.ui.theme.*
import com.example.ui.tutorial.tutorialTarget
import com.example.util.ExternalDocumentUriHelper
import com.example.util.NoteExportHelper
import java.text.SimpleDateFormat
import java.util.*

@Composable
private fun categoryIconFor(category: String): ImageVector {
    return when (category.lowercase().trim()) {
        "criatividade", "ideias" -> Icons.Default.Palette
        "estudos", "study" -> Icons.Default.MenuBook
        "logística", "finanças", "trabalho", "work" -> Icons.Default.Payments
        "saúde", "fitness" -> Icons.Default.Eco
        "geral" -> Icons.Default.FolderSpecial
        else -> Icons.Default.Label
    }
}

@Composable
private fun extractFirstImageUri(note: Note): String? {
    return remember(note.content) {
        val match = Regex("!\\[(?!audio:)(.*?)\\]\\((.*?)\\)").find(note.content)
        match?.groupValues?.getOrNull(2)?.takeIf { it.isNotBlank() }
    }
}

@Composable
fun NotesSubTab(
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val notes by viewModel.notes.collectAsState()
    val trashNotes by viewModel.trashNotes.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val selectedCategoryFilter by viewModel.selectedNoteCategoryFilter.collectAsState()
    val selectedDocumentFilter by viewModel.selectedNoteDocumentFilter.collectAsState()
    val openCreateTrigger by viewModel.openCreateNoteTrigger.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var activeNoteToEdit by remember { mutableStateOf<Note?>(null) }
    var activePdfToRead by remember { mutableStateOf<Note?>(null) }
    var activeDocxToRead by remember { mutableStateOf<Note?>(null) }
    var isConvertingExternalDoc by remember { mutableStateOf(false) }
    var noteForCategoryPicker by remember { mutableStateOf<Note?>(null) }
    var noteAwaitingRelink by remember { mutableStateOf<Note?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }
    var noteToDelete by remember { mutableStateOf<Note?>(null) }
    var noteForSubmenu by remember { mutableStateOf<Note?>(null) }
    var showTrashDialog by remember { mutableStateOf(false) }
    var showAddFolderDialog by remember { mutableStateOf(false) }
    val notesSortOptionName by viewModel.notesSortOption.collectAsState()
    val isSortAscending by viewModel.notesSortAscending.collectAsState()
    val notesLayoutModeName by viewModel.notesLayoutMode.collectAsState()
    val currentSort = remember(notesSortOptionName) { NoteSortOption.fromName(notesSortOptionName) }
    val currentLayout = remember(notesLayoutModeName) { NoteLayoutMode.fromName(notesLayoutModeName) }
    var showSortMenu by remember { mutableStateOf(false) }
    val expandedCategoryStacks = remember { mutableStateMapOf<String, Boolean>() }

    val openNoteItem: (Note) -> Unit = { target ->
        when (target.resolvedDocumentType) {
            NoteDocumentType.PDF -> {
                activeNoteToEdit = null
                isCreatingNew = false
                activeDocxToRead = null
                activePdfToRead = target
            }
            NoteDocumentType.DOCX -> {
                activeNoteToEdit = null
                isCreatingNew = false
                activePdfToRead = null
                activeDocxToRead = target
            }
            NoteDocumentType.MARKDOWN -> {
                activePdfToRead = null
                activeDocxToRead = null
                activeNoteToEdit = target
                isCreatingNew = false
            }
        }
    }

    val importMultipleNotesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.importMultipleMarkdownFiles(
                uris = uris,
                context = context,
                preferredCategory = selectedCategoryFilter
            )
        }
    }

    val linkExternalDocsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.importMultipleMarkdownFiles(
                uris = uris,
                context = context,
                preferredCategory = selectedCategoryFilter
            )
        }
    }

    val relinkExternalDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val target = noteAwaitingRelink
        noteAwaitingRelink = null
        if (uri != null && target != null) {
            viewModel.relinkExternalDocumentUri(target, uri, context) { updated ->
                if (activePdfToRead?.id == updated.id) activePdfToRead = updated
                if (activeDocxToRead?.id == updated.id) activeDocxToRead = updated
            }
        }
    }

    val noteToOpen by viewModel.noteToOpen.collectAsState()

    // Respond to Home screen "Nova Nota" shortcut trigger
    LaunchedEffect(openCreateTrigger) {
        if (openCreateTrigger) {
            activePdfToRead = null
            activeDocxToRead = null
            isCreatingNew = true
            viewModel.resetCreateNoteTrigger()
        }
    }

    // Respond to notification / social click to open specific note or linked document
    LaunchedEffect(noteToOpen) {
        noteToOpen?.let { note ->
            openNoteItem(note)
            viewModel.clearNoteToOpen()
        }
    }

    // Filter notes by search query AND category AND 72h rule
    val categoryAndSearchNotes = remember(notes, searchQuery, selectedCategoryFilter) {
        notes.filter { note ->
            val isReady = note.isReadyForMainList()
            val matchesCategory = (selectedCategoryFilter == "Todas" || note.category.equals(selectedCategoryFilter, ignoreCase = true))
            val matchesSearch = if (searchQuery.isBlank()) true else {
                note.title.contains(searchQuery, ignoreCase = true) ||
                    note.content.contains(searchQuery, ignoreCase = true) ||
                    note.category.contains(searchQuery, ignoreCase = true) ||
                    (note.externalUri?.contains(searchQuery, ignoreCase = true) == true)
            }
            isReady && matchesCategory && matchesSearch
        }
    }

    // Apply Document Type filter (ALL, NOTES, PDF, DOCX)
    val filteredNotes = remember(categoryAndSearchNotes, selectedDocumentFilter) {
        filterNotesByDocumentType(categoryAndSearchNotes, selectedDocumentFilter)
    }

    // Sorted notes: Pinned notes ALWAYS stay at top, combined with primary sort + secondary asc/desc
    val sortedNotes = remember(filteredNotes, currentSort, isSortAscending) {
        sortNotes(
            notes = filteredNotes,
            sortOption = currentSort,
            ascending = isSortAscending
        )
    }

    val groupedCategoryNotes = remember(sortedNotes, currentSort, isSortAscending, categories) {
        groupNotesByCategory(
            sortedNotes = sortedNotes,
            sortOption = currentSort,
            ascending = isSortAscending,
            knownCategories = categories.map { it.name }
        )
    }

    val pomodoroState by viewModel.pomodoroState.collectAsState()
    val isEditingNote = isCreatingNew || activeNoteToEdit != null
    val isFullScreenOverlayOpen = isEditingNote || activePdfToRead != null || activeDocxToRead != null

    LaunchedEffect(isFullScreenOverlayOpen) {
        viewModel.setNoteEditorOpen(isFullScreenOverlayOpen)
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.setNoteEditorOpen(false)
        }
    }

    // Full-screen Read-Only PDF Reader
    activePdfToRead?.let { pdfNote ->
        ExternalPdfReaderScreen(
            note = pdfNote,
            categories = categories,
            isConverting = isConvertingExternalDoc,
            onClose = {
                activePdfToRead = null
                viewModel.setNoteEditorOpen(false)
            },
            onChangeCategory = { newCat ->
                viewModel.updateNoteCategory(pdfNote, newCat) { updated ->
                    activePdfToRead = updated
                }
            },
            onCreateCategory = { name, emoji -> viewModel.addCustomCategory(name, emoji) },
            onRelinkClick = {
                noteAwaitingRelink = pdfNote
                relinkExternalDocLauncher.launch(arrayOf("application/pdf"))
            },
            onRemoveLink = {
                activePdfToRead = null
                noteToDelete = pdfNote
            },
            onConvertToNote = {
                if (!isConvertingExternalDoc) {
                    isConvertingExternalDoc = true
                    viewModel.convertExternalDocumentToEditableNote(
                        docNote = pdfNote,
                        context = context,
                        onConverted = { convertedNote ->
                            isConvertingExternalDoc = false
                            activePdfToRead = null
                            activeNoteToEdit = convertedNote
                            isCreatingNew = false
                        },
                        onError = {
                            isConvertingExternalDoc = false
                        }
                    )
                }
            }
        )
        return
    }

    // Full-screen Read-Only .docx Reader
    activeDocxToRead?.let { docxNote ->
        ExternalDocxReaderScreen(
            note = docxNote,
            categories = categories,
            isConverting = isConvertingExternalDoc,
            onClose = {
                activeDocxToRead = null
                viewModel.setNoteEditorOpen(false)
            },
            onChangeCategory = { newCat ->
                viewModel.updateNoteCategory(docxNote, newCat) { updated ->
                    activeDocxToRead = updated
                }
            },
            onCreateCategory = { name, emoji -> viewModel.addCustomCategory(name, emoji) },
            onRelinkClick = {
                noteAwaitingRelink = docxNote
                relinkExternalDocLauncher.launch(
                    arrayOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                )
            },
            onRemoveLink = {
                activeDocxToRead = null
                noteToDelete = docxNote
            },
            onConvertToNote = {
                if (!isConvertingExternalDoc) {
                    isConvertingExternalDoc = true
                    viewModel.convertExternalDocumentToEditableNote(
                        docNote = docxNote,
                        context = context,
                        onConverted = { convertedNote ->
                            isConvertingExternalDoc = false
                            activeDocxToRead = null
                            activeNoteToEdit = convertedNote
                            isCreatingNew = false
                        },
                        onError = {
                            isConvertingExternalDoc = false
                        }
                    )
                }
            }
        )
        return
    }

    val allTasks by viewModel.tasks.collectAsState()
    val allHabits by viewModel.habits.collectAsState()
    val currentNoteVersions by if (activeNoteToEdit != null) {
        viewModel.getVersionsForNote(activeNoteToEdit!!.id).collectAsState(initial = emptyList())
    } else {
        remember { mutableStateOf<List<NoteVersion>>(emptyList()) }
    }

    var showTemplatesMenu by remember { mutableStateOf(false) }

    if (showTemplatesMenu) {
        com.example.ui.screens.organization.notes.NoteTemplatesFullScreenDialog(
            onDismiss = { showTemplatesMenu = false },
            onSelectTemplate = { template ->
                val unsavedNote = com.example.ui.screens.organization.notes.NoteTemplatesCatalog
                    .buildUnsavedNoteFromTemplate(template)
                activeNoteToEdit = unsavedNote
                isCreatingNew = true
                showTemplatesMenu = false
            }
        )
    }

    val financesEnabled by viewModel.financesEnabled.collectAsState()
    var showInsertFinanceBlockDialog by remember { mutableStateOf(false) }

    DisposableEffect(isEditingNote, activeNoteToEdit?.uuid) {
        viewModel.setActiveEditorNote(if (isEditingNote) activeNoteToEdit else null)
        onDispose {
            viewModel.setActiveEditorNote(null)
        }
    }

    // Full screen note editor overlay when editing or creating
    if (isEditingNote) {
        key(activeNoteToEdit?.id ?: if (isCreatingNew) -1L else 0L) {
            FullScreenNoteEditor(
                initialNote = activeNoteToEdit,
                categories = categories,
                allNotes = notes,
                tasks = allTasks,
                habits = allHabits,
                pomodoroState = pomodoroState,
                onNavigateToTimer = null,
                noteVersions = currentNoteVersions,
                onRestoreVersion = { version ->
                    activeNoteToEdit?.let { note ->
                        viewModel.restoreNoteVersion(note, version)
                        activeNoteToEdit = note.copy(
                            title = version.title,
                            content = version.content
                        )
                    }
                },
                onCreateLocalCopy = { version ->
                    viewModel.createLocalCopyOfVersion(version)
                },
                financesEnabled = financesEnabled,
                onInsertFinanceBlock = {
                    showInsertFinanceBlockDialog = true
                },
                viewModel = viewModel,
                onClose = {
                    isCreatingNew = false
                    activeNoteToEdit = null
                    viewModel.setNoteEditorOpen(false)
                },
                onSaveAndClose = { title, content, category, isPinned, bgUri, blur, brightness, noteColor ->
                    viewModel.saveNote(
                        id = activeNoteToEdit?.id ?: 0L,
                        title = title,
                        content = content,
                        category = category,
                        iconEmoji = "📝",
                        associatedDate = activeNoteToEdit?.associatedDate,
                        isPinned = isPinned,
                        backgroundImageUri = bgUri,
                        backgroundBlur = blur,
                        backgroundBrightness = brightness,
                        uuid = activeNoteToEdit?.uuid,
                        oldTitle = activeNoteToEdit?.title,
                        noteColor = noteColor
                    )
                    isCreatingNew = false
                    activeNoteToEdit = null
                    viewModel.setNoteEditorOpen(false)
                },
                onSaveOnly = { title, content, category, isPinned, bgUri, blur, brightness, noteColor ->
                    val currentUuid = activeNoteToEdit?.uuid ?: UUID.randomUUID().toString()
                    viewModel.saveNote(
                        id = activeNoteToEdit?.id ?: 0L,
                        title = title,
                        content = content,
                        category = category,
                        iconEmoji = "📝",
                        associatedDate = activeNoteToEdit?.associatedDate,
                        isPinned = isPinned,
                        backgroundImageUri = bgUri,
                        backgroundBlur = blur,
                        backgroundBrightness = brightness,
                        uuid = currentUuid,
                        oldTitle = activeNoteToEdit?.title,
                        noteColor = noteColor,
                        onSaved = { savedId ->
                            activeNoteToEdit = Note(
                                id = savedId,
                                uuid = currentUuid,
                                title = title,
                                content = content,
                                category = category,
                                iconEmoji = "📝",
                                isPinned = isPinned,
                                backgroundImageUri = bgUri,
                                backgroundBlur = blur,
                                backgroundBrightness = brightness,
                                noteColor = noteColor
                            )
                        }
                    )
                },
                onDelete = if (activeNoteToEdit != null) {
                    {
                        noteToDelete = activeNoteToEdit
                        isCreatingNew = false
                        activeNoteToEdit = null
                        viewModel.setNoteEditorOpen(false)
                    }
                } else null,
                onOpenLinkedNote = { note ->
                    openNoteItem(note)
                },
                initialMode = NoteEditorMode.LIVE_PREVIEW,
                onModeChanged = null,
                onToggleChecklistTaskSync = { checklistText, isChecked ->
                    viewModel.syncChecklistToTask(checklistText, isChecked)
                },
                onConvertChecklistToDirectTask = { taskTitle, taskCategory, taskPriority, dueDate, dueTimestamp, reminderMinutes ->
                    val priorityEnum = when (taskPriority) {
                        "ALTA" -> TaskPriority.ALTA
                        "BAIXA" -> TaskPriority.BAIXA
                        else -> TaskPriority.MEDIA
                    }
                    viewModel.addTask(
                        title = taskTitle,
                        category = taskCategory,
                        priority = priorityEnum,
                        dueDateText = dueDate,
                        dueTimestamp = dueTimestamp,
                        reminderMinutesBefore = reminderMinutes
                    )
                },
                onConvertChecklistToTask = { taskTitle, dueDate ->
                    viewModel.addTask(
                        title = taskTitle,
                        category = "Geral",
                        priority = TaskPriority.MEDIA,
                        dueDateText = dueDate,
                        dueTimestamp = null,
                        reminderMinutesBefore = 0
                    )
                }
            )
        }

        if (showInsertFinanceBlockDialog && financesEnabled) {
            val finCategories by viewModel.allFinanceCategories.collectAsState()
            val finEntries by viewModel.financeEntries.collectAsState()
            val baseBal by viewModel.financeBaseBalanceMinor.collectAsState()
            val currCode by viewModel.financeDefaultCurrency.collectAsState()
            val activeCurr = remember(currCode) { com.example.data.model.FinanceCurrency.fromCode(currCode) }
            val currentBal = remember(baseBal, finEntries) {
                baseBal + finEntries.sumOf {
                    if (it.type == com.example.data.model.FinanceEntryType.INCOME.id) it.totalAmountMinor else -it.totalAmountMinor
                }
            }
            val targetNote = activeNoteToEdit ?: Note(title = "Finanças", content = "")
            com.example.ui.screens.finances.FinanceNoteLinkCreatorDialog(
                notes = notes,
                categories = finCategories,
                entries = finEntries,
                currentBalanceMinor = currentBal,
                defaultCurrency = activeCurr,
                preselectedNote = targetNote,
                onDismiss = { showInsertFinanceBlockDialog = false },
                onConfirmCreateLink = { link, _, _, template ->
                    showInsertFinanceBlockDialog = false
                    if (targetNote.id == 0L) {
                        viewModel.saveNote(
                            id = 0L,
                            title = targetNote.title.ifBlank { "Finanças" },
                            content = targetNote.content,
                            category = targetNote.category,
                            iconEmoji = targetNote.iconEmoji,
                            associatedDate = targetNote.associatedDate,
                            isPinned = targetNote.isPinned,
                            backgroundImageUri = targetNote.backgroundImageUri,
                            backgroundBlur = targetNote.backgroundBlur,
                            backgroundBrightness = targetNote.backgroundBrightness,
                            uuid = targetNote.uuid
                        )
                    }
                    viewModel.createAndRunFinanceNoteLink(
                        link = link.copy(noteId = targetNote.id, noteUuid = targetNote.uuid),
                        createNewNote = false,
                        newNoteTitle = targetNote.title,
                        template = template
                    )
                }
            )
        }
        return
    }

    val surfaceContainerLow = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    val surfaceContainerHigh = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)

    val topBarHeaderItem = @Composable {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Pill Search Bar & Utility Quick Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Pill Search Input
                Surface(
                    shape = CircleShape,
                    color = surfaceContainerLow,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .tutorialTarget("notes_search_input")
                        .testTag("notes_search_input")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Pesquisar",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Pesquisar notas ou ideias...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outline,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Limpar pesquisa",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Create New Folder Circular Button
                Surface(
                    shape = CircleShape,
                    color = surfaceContainerLow,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable { showAddFolderDialog = true }
                        .testTag("notes_create_folder_btn")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.CreateNewFolder,
                            contentDescription = "Criar nova pasta",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }

                // Import Notes & Link PDF/.docx Circular Button
                Surface(
                    shape = CircleShape,
                    color = surfaceContainerLow,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable {
                            importMultipleNotesLauncher.launch(ExternalDocumentUriHelper.ALL_IMPORT_AND_LINK_MIME_TYPES)
                        }
                        .testTag("notes_import_multiple_btn")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.FileOpen,
                            contentDescription = "Importar notas ou vincular PDF / .docx",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Trash Circular Button with Badge
                Box(modifier = Modifier.size(48.dp)) {
                    Surface(
                        shape = CircleShape,
                        color = if (trashNotes.isNotEmpty()) {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                        } else {
                            surfaceContainerLow
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .clickable { showTrashDialog = true }
                            .testTag("notes_trash_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Lixeira (30 dias)",
                                tint = if (trashNotes.isNotEmpty()) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }
                    if (trashNotes.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = (-2).dp, y = 2.dp)
                                .size(18.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${trashNotes.size}",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }

            // 2. Scrollable Folder Tabs (Pill Chips)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .tutorialTarget("notes_category_bar")
            ) {
                // "Todas" Pill
                item {
                    val isSelected = selectedCategoryFilter == "Todas"
                    FolderPillChip(
                        label = "Todas",
                        count = notes.size,
                        icon = Icons.Default.Folder,
                        selected = isSelected,
                        iconTint = if (isSelected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.primary,
                        onClick = { viewModel.setSelectedNoteCategoryFilter("Todas") }
                    )
                }

                // Category Pills
                items(categories, key = { it.name }) { cat ->
                    val count = notes.count { it.category.equals(cat.name, ignoreCase = true) }
                    val isSelected = selectedCategoryFilter.equals(cat.name, ignoreCase = true)
                    val defaultTint = when (cat.name.lowercase().trim()) {
                        "criatividade", "ideias" -> MaterialTheme.colorScheme.primary
                        "estudos", "study" -> MaterialTheme.colorScheme.tertiary
                        "geral" -> MaterialTheme.colorScheme.outline
                        else -> MaterialTheme.colorScheme.secondary
                    }
                    FolderPillChip(
                        label = cat.name,
                        count = count,
                        icon = categoryIconFor(cat.name),
                        selected = isSelected,
                        iconTint = if (isSelected) MaterialTheme.colorScheme.onSecondary else defaultTint,
                        onClick = { viewModel.setSelectedNoteCategoryFilter(cat.name) }
                    )
                }

                // "+ Pasta" Pill
                item {
                    Surface(
                        shape = CircleShape,
                        color = surfaceContainerHigh,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { showAddFolderDialog = true }
                            .testTag("notes_add_folder_chip")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Pasta",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 2b. Document Type Filter Strip (Todos, Notas, PDFs, .docx) + Quick Link Button
            val allDocCount = categoryAndSearchNotes.size
            val mdDocCount = categoryAndSearchNotes.count { it.resolvedDocumentType == NoteDocumentType.MARKDOWN }
            val pdfDocCount = categoryAndSearchNotes.count { it.resolvedDocumentType == NoteDocumentType.PDF }
            val docxDocCount = categoryAndSearchNotes.count { it.resolvedDocumentType == NoteDocumentType.DOCX }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("notes_doc_filter_bar")
            ) {
                items(NoteDocumentFilter.entries) { filter ->
                    val count = when (filter) {
                        NoteDocumentFilter.ALL -> allDocCount
                        NoteDocumentFilter.NOTES -> mdDocCount
                        NoteDocumentFilter.PDF -> pdfDocCount
                        NoteDocumentFilter.DOCX -> docxDocCount
                    }
                    val isSelected = selectedDocumentFilter == filter
                    val chipColor = when {
                        isSelected && filter == NoteDocumentFilter.PDF -> Color(0xFFDC2626)
                        isSelected && filter == NoteDocumentFilter.DOCX -> Color(0xFF2563EB)
                        isSelected -> MaterialTheme.colorScheme.primary
                        else -> surfaceContainerLow
                    }
                    val contentCol = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    val chipIcon = when (filter) {
                        NoteDocumentFilter.ALL -> Icons.Default.Apps
                        NoteDocumentFilter.NOTES -> Icons.Default.Description
                        NoteDocumentFilter.PDF -> Icons.Default.PictureAsPdf
                        NoteDocumentFilter.DOCX -> Icons.Default.Article
                    }
                    Surface(
                        shape = CircleShape,
                        color = chipColor,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { viewModel.setSelectedNoteDocumentFilter(filter) }
                            .testTag("notes_doc_filter_${filter.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = chipIcon,
                                contentDescription = null,
                                tint = contentCol,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = filter.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                color = contentCol
                            )
                            Text(
                                text = "$count",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = contentCol.copy(alpha = if (isSelected) 0.9f else 0.65f)
                            )
                        }
                    }
                }

                item {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                linkExternalDocsLauncher.launch(ExternalDocumentUriHelper.DOCUMENT_PICKER_MIME_TYPES)
                            }
                            .testTag("notes_link_external_doc_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AttachFile,
                                contentDescription = "Vincular PDF ou .docx",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "+ PDF / .docx",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // 3. View & Sort Controls Row (Primary Sort + Secondary Asc/Desc + Layout Modes incl. Stacked Folders)
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Primary Sort Dropdown Pill
                    Box {
                        Surface(
                            shape = CircleShape,
                            color = surfaceContainerLow,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { showSortMenu = true }
                                .testTag("notes_sort_menu_btn")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = "Ordenar notas",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = currentSort.shortLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(
                                    imageVector = Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            Text(
                                text = "Ordenar por",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                            NoteSortOption.entries.forEach { opt ->
                                val selected = opt == currentSort
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = opt.label,
                                            fontSize = 12.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            when (opt) {
                                                NoteSortOption.LAST_UPDATED -> Icons.Default.Update
                                                NoteSortOption.CREATION_DATE -> Icons.Default.CalendarToday
                                                NoteSortOption.ALPHABETICAL -> Icons.Default.SortByAlpha
                                                NoteSortOption.CATEGORY -> Icons.Default.Label
                                                NoteSortOption.SIZE -> Icons.Default.FormatSize
                                            },
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    trailingIcon = if (selected) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    } else null,
                                    onClick = {
                                        viewModel.setNotesSortOption(opt.name)
                                        showSortMenu = false
                                    }
                                )
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            Text(
                                text = "Ordem secundária",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Ascendente",
                                        fontSize = 12.sp,
                                        fontWeight = if (isSortAscending) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.ArrowUpward,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (isSortAscending) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                trailingIcon = if (isSortAscending) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                } else null,
                                onClick = {
                                    viewModel.setNotesSortAscending(true)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Decrescente",
                                        fontSize = 12.sp,
                                        fontWeight = if (!isSortAscending) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDownward,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (!isSortAscending) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                trailingIcon = if (!isSortAscending) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                } else null,
                                onClick = {
                                    viewModel.setNotesSortAscending(false)
                                    showSortMenu = false
                                }
                            )
                        }
                    }

                    // Secondary 1-Tap Ascendente / Decrescente Pill
                    Surface(
                        shape = CircleShape,
                        color = if (isSortAscending) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            surfaceContainerLow
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { viewModel.toggleNotesSortAscending() }
                            .testTag("notes_sort_direction_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (isSortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                contentDescription = if (isSortAscending) "Ordem Ascendente" else "Ordem Decrescente",
                                modifier = Modifier.size(15.dp),
                                tint = if (isSortAscending) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                            Text(
                                text = if (isSortAscending) "Asc" else "Desc",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSortAscending) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 1
                            )
                        }
                    }
                }

                // Layout Toggle Pills inside Pill Container (Grid 2, Grid 3, List, Stacked Folders)
                Surface(
                    shape = CircleShape,
                    color = surfaceContainerLow
                ) {
                    Row(
                        modifier = Modifier.padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NoteLayoutMode.entries.forEach { mode ->
                            val isSelected = currentLayout == mode
                            val layoutTag = when (mode) {
                                NoteLayoutMode.GRID_2 -> "notes_layout_grid2_btn"
                                NoteLayoutMode.GRID_3 -> "notes_layout_grid3_btn"
                                NoteLayoutMode.LIST -> "notes_layout_list_btn"
                                NoteLayoutMode.STACKED_CATEGORIES -> "notes_layout_stacked_btn"
                            }
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        CircleShape
                                    )
                                    .clickable { viewModel.setNotesLayoutMode(mode.name) }
                                    .testTag(layoutTag),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when (mode) {
                                        NoteLayoutMode.GRID_2 -> Icons.Default.GridView
                                        NoteLayoutMode.GRID_3 -> Icons.Default.ViewComfy
                                        NoteLayoutMode.LIST -> Icons.Default.ViewList
                                        NoteLayoutMode.STACKED_CATEGORIES -> Icons.Default.Layers
                                    },
                                    contentDescription = mode.label,
                                    modifier = Modifier.size(17.dp),
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val emptyNotesItem = @Composable {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 40.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.NoteAlt,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = if (searchQuery.isNotEmpty()) "Nenhuma anotação nesta busca." else "Nenhuma anotação nesta pasta.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Toque no '+' ou em Rascunho Rápido para começar.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (currentLayout) {
            NoteLayoutMode.GRID_2 -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 92.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("notes_grid")
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        topBarHeaderItem()
                    }
                    if (sortedNotes.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            emptyNotesItem()
                        }
                    } else {
                        items(sortedNotes, key = { it.id }) { note ->
                            SquareNoteCard(
                                note = note,
                                onClick = { openNoteItem(note) },
                                onLongClick = { noteForSubmenu = note },
                                onTogglePin = { viewModel.togglePinNote(note) },
                                onDelete = { noteToDelete = note }
                            )
                        }
                    }
                    // Quick Draft Tile (Card 6 from HTML)
                    item {
                        QuickDraftTile(
                            onClick = { showTemplatesMenu = true }
                        )
                    }
                }
            }
            NoteLayoutMode.GRID_3 -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 92.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("notes_grid_small")
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        topBarHeaderItem()
                    }
                    if (sortedNotes.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            emptyNotesItem()
                        }
                    } else {
                        items(sortedNotes, key = { it.id }) { note ->
                            SmallSquareNoteCard(
                                note = note,
                                onClick = { openNoteItem(note) },
                                onLongClick = { noteForSubmenu = note },
                                onTogglePin = { viewModel.togglePinNote(note) },
                                onDelete = { noteToDelete = note }
                            )
                        }
                    }
                    item {
                        QuickDraftTile(
                            onClick = { showTemplatesMenu = true }
                        )
                    }
                }
            }
            NoteLayoutMode.LIST -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 92.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("notes_simple_list")
                ) {
                    item {
                        topBarHeaderItem()
                    }
                    if (sortedNotes.isEmpty()) {
                        item {
                            emptyNotesItem()
                        }
                    } else {
                        items(sortedNotes, key = { it.id }) { note ->
                            ListNoteCard(
                                note = note,
                                onClick = { openNoteItem(note) },
                                onLongClick = { noteForSubmenu = note },
                                onTogglePin = { viewModel.togglePinNote(note) },
                                onDelete = { noteToDelete = note }
                            )
                        }
                    }
                    item {
                        QuickDraftTile(
                            onClick = { showTemplatesMenu = true }
                        )
                    }
                }
            }
            NoteLayoutMode.STACKED_CATEGORIES -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 92.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("notes_stacked_list")
                ) {
                    item {
                        topBarHeaderItem()
                    }
                    if (sortedNotes.isEmpty()) {
                        item {
                            emptyNotesItem()
                        }
                    } else {
                        item(key = "stacks_summary_bar") {
                            val allExpanded = groupedCategoryNotes.isNotEmpty() &&
                                groupedCategoryNotes.all { expandedCategoryStacks[it.categoryName.lowercase()] == true }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderCopy,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "${groupedCategoryNotes.size} ${if (groupedCategoryNotes.size == 1) "pasta" else "pastas"} • ${sortedNotes.size} ${if (sortedNotes.size == 1) "nota" else "notas"}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (groupedCategoryNotes.size > 1) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .clickable {
                                                val targetState = !allExpanded
                                                groupedCategoryNotes.forEach { group ->
                                                    expandedCategoryStacks[group.categoryName.lowercase()] = targetState
                                                }
                                            }
                                            .testTag("notes_stacks_toggle_all_btn")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (allExpanded) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = if (allExpanded) "Recolher todas" else "Expandir todas",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        items(groupedCategoryNotes, key = { it.categoryName.lowercase() }) { group ->
                            val stackKey = group.categoryName.lowercase()
                            CategoryNoteStack(
                                categoryName = group.categoryName,
                                notes = group.notes,
                                isExpanded = expandedCategoryStacks[stackKey] == true,
                                onToggleExpand = {
                                    expandedCategoryStacks[stackKey] = !(expandedCategoryStacks[stackKey] ?: false)
                                },
                                onNoteClick = { openNoteItem(it) },
                                onNoteLongClick = { noteForSubmenu = it },
                                onTogglePin = { viewModel.togglePinNote(it) },
                                onDeleteNote = { noteToDelete = it }
                            )
                        }
                    }
                    item {
                        QuickDraftTile(
                            onClick = { showTemplatesMenu = true }
                        )
                    }
                }
            }
        }

        // Circular Citrus Glow FAB
        FloatingActionButton(
            onClick = { isCreatingNew = true },
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(56.dp)
                .tutorialTarget("notes_add_button")
                .testTag("add_note_fab")
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Nova Anotação",
                modifier = Modifier.size(28.dp)
            )
        }
    }

    // Add Folder / Category Dialog
    if (showAddFolderDialog) {
        AddCategoryDialog(
            onDismiss = { showAddFolderDialog = false },
            onConfirm = { name, emoji ->
                viewModel.addCustomCategory(name, emoji)
                showAddFolderDialog = false
            }
        )
    }

    // Category Picker Dialog for notes, PDFs, and .docx documents
    noteForCategoryPicker?.let { target ->
        NoteCategoryPickerDialog(
            currentCategory = target.category,
            categories = categories,
            onSelectCategory = { newCategory ->
                viewModel.updateNoteCategory(target, newCategory)
            },
            onCreateCategory = { name, emoji ->
                viewModel.addCustomCategory(name, emoji)
            },
            onDismiss = { noteForCategoryPicker = null }
        )
    }

    // Confirmation dialog before deleting note or unlinking external document
    if (noteToDelete != null) {
        val target = noteToDelete!!
        val isExternal = target.isExternalDocument
        ConfirmDeleteDialog(
            title = if (isExternal) "Remover Vínculo" else "Mover para a Lixeira",
            message = if (isExternal) {
                "Deseja remover o vínculo com '${target.title}' da lista? O arquivo original no seu aparelho não será apagado."
            } else {
                "Deseja mover a anotação '${target.title}' para a lixeira? Ela ficará disponível por 30 dias para restauração."
            },
            confirmButtonText = if (isExternal) "Mover Vínculo" else "Mover para Lixeira",
            onConfirm = {
                viewModel.moveNoteToTrash(target)
                noteToDelete = null
            },
            onDismiss = { noteToDelete = null }
        )
    }

    // Mini Submenu on Note Long-Press or 3-dots click
    noteForSubmenu?.let { note ->
        NoteSubmenuDialog(
            note = note,
            onDismiss = { noteForSubmenu = null },
            onTogglePin = { viewModel.togglePinNote(note) },
            onDelete = { noteToDelete = note },
            onChangeCategory = { noteForCategoryPicker = note },
            onOpenReadOnly = if (note.isExternalDocument) {
                { openNoteItem(note) }
            } else null,
            onConvertToNote = if (note.isExternalDocument) {
                {
                    viewModel.convertExternalDocumentToEditableNote(
                        docNote = note,
                        context = context,
                        onConverted = { converted ->
                            activeNoteToEdit = converted
                            isCreatingNew = false
                        }
                    )
                }
            } else null,
            onRelinkExternal = if (note.isExternalDocument) {
                {
                    noteAwaitingRelink = note
                    val mime = if (note.isPdfDocument) {
                        arrayOf("application/pdf")
                    } else {
                        arrayOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                    }
                    relinkExternalDocLauncher.launch(mime)
                }
            } else null,
            onDuplicate = {
                viewModel.saveNote(
                    id = 0L,
                    title = "${note.title} (Cópia)",
                    content = note.content,
                    category = note.category,
                    iconEmoji = note.iconEmoji,
                    isPinned = false,
                    backgroundImageUri = note.backgroundImageUri,
                    backgroundBlur = note.backgroundBlur,
                    backgroundBrightness = note.backgroundBrightness,
                    noteColor = note.noteColor
                )
            }
        )
    }

    // Trash Dialog
    if (showTrashDialog) {
        NotesTrashDialog(
            trashNotes = trashNotes,
            onRestore = { viewModel.restoreNoteFromTrash(it) },
            onRestoreAll = { viewModel.restoreAllTrashedNotes() },
            onPermanentDelete = { viewModel.permanentlyDeleteNote(it, context) },
            onEmptyTrash = { viewModel.emptyNoteTrash(context) },
            onDismiss = { showTrashDialog = false }
        )
    }
}

@Composable
private fun FolderPillChip(
    label: String,
    count: Int,
    icon: ImageVector,
    selected: Boolean,
    iconTint: Color,
    onClick: () -> Unit
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onSecondary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val badgeBg = if (selected) {
        MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.2f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val badgeText = if (selected) {
        MaterialTheme.colorScheme.onSecondary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = CircleShape,
        color = containerColor,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(17.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Box(
                modifier = Modifier
                    .background(badgeBg, CircleShape)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$count",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = badgeText
                )
            }
        }
    }
}

@Composable
private fun QuickDraftTile(
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 175.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag("notes_quick_draft_tile")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.EditNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Rascunho Rápido",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Toque para escrever",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Warm Tactile 2-Column Card for Notes with Dog-Ear Paper Flap and 260.dp Height
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SquareNoteCard(
    note: Note,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    val isDark = isAppDarkTheme()
    val paperColors = remember(note.noteColor, isDark, note.isPinned) {
        PaperNotePalette.resolvePaperColors(note.noteColor, isDark, note.isPinned)
    }
    val dateText = remember(note.updatedAt) {
        SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(note.updatedAt))
    }
    val hasAudio = remember(note.content) {
        note.content.contains("![audio:") || note.content.contains("audio:")
    }
    val previewImageUri = extractFirstImageUri(note)
    val hasImage = previewImageUri != null

    val badgeBg = when (note.category.lowercase().trim()) {
        "criatividade", "ideias" -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
        "estudos", "study" -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f)
        "logística", "finanças", "trabalho", "work" -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val badgeFg = when (note.category.lowercase().trim()) {
        "criatividade", "ideias" -> MaterialTheme.colorScheme.onPrimaryContainer
        "estudos", "study" -> MaterialTheme.colorScheme.onSecondaryContainer
        "logística", "finanças", "trabalho", "work" -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    PaperNoteSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .combinedClickable(
                onClick = { onClick() },
                onLongClick = { onLongClick?.invoke() }
            )
            .testTag("note_square_card_${note.id}"),
        cornerRadius = 20.dp,
        foldSize = 26.dp,
        paperColors = paperColors,
        isPinned = note.isPinned
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Top row: Category Pill + Pin + More (padding end 22.dp so buttons don't collide with flap)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        shape = CircleShape,
                        color = badgeBg,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = categoryIconFor(note.category),
                                contentDescription = null,
                                tint = badgeFg,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = note.category,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = badgeFg,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        IconButton(
                            onClick = onTogglePin,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = if (note.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                contentDescription = if (note.isPinned) "Desafixar" else "Fixar",
                                tint = if (note.isPinned) paperColors.accentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        IconButton(
                            onClick = { onLongClick?.invoke() ?: onDelete() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Opções",
                                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Embedded Image Preview (86.dp height) for regular notes
                if (previewImageUri != null && !note.isExternalDocument) {
                    AsyncImage(
                        model = previewImageUri,
                        contentDescription = note.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(86.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }

                // External PDF / DOCX hero pill badge inside SquareNoteCard
                if (note.isExternalDocument) {
                    val isPdf = note.isPdfDocument
                    val docAccent = if (isPdf) Color(0xFFDC2626) else Color(0xFF2563EB)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = docAccent.copy(alpha = 0.12f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, docAccent.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isPdf) Icons.Default.PictureAsPdf else Icons.Default.Article,
                                contentDescription = if (isPdf) "Documento PDF" else "Documento Word",
                                tint = docAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isPdf) "PDF · Somente leitura" else ".DOCX · Somente leitura",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = docAccent
                                )
                                Text(
                                    text = ExternalDocumentUriHelper.formatReadablePath(note.externalUri),
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Note Title - high contrast according to app theme
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Note Reading-Mode Preview (styled headings, lists, checklists, quotes, inline formatting)
                if (!note.isExternalDocument && note.content.isNotBlank()) {
                    NoteReadingPreview(
                        content = note.content,
                        maxElements = if (previewImageUri != null) 2 else 4,
                        maxTotalLines = if (previewImageUri != null) 3 else 5,
                        compact = true,
                        textColor = MaterialTheme.colorScheme.onSurface,
                        secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        accentColor = paperColors.accentColor,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (note.isExternalDocument) {
                    Text(
                        text = "Toque para ler ou converter para nota editável",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Bottom row: Schedule Date + Media Badges / Pinned Dot
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = dateText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasImage) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = "Contém Imagem",
                            tint = Color(0xFFE53935),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    if (hasAudio) {
                        Icon(
                            imageVector = Icons.Default.Audiotrack,
                            contentDescription = "Contém Áudio",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    if (note.noteColor != null && note.noteColor != "DEFAULT") {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .background(paperColors.accentColor, CircleShape)
                        )
                    } else if (note.isPinned) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(paperColors.accentColor, CircleShape)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Small 3-Column Card for Notes
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SmallSquareNoteCard(
    note: Note,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    val cardContainerColor = if (note.isPinned) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardContainerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (note.isPinned) 2.5.dp else 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.9f)
            .border(
                width = 1.dp,
                color = if (note.isPinned) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp)
            )
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = { onClick() },
                onLongClick = { onLongClick?.invoke() }
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = note.category.take(6),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    if (note.isExternalDocument) {
                        val isPdf = note.isPdfDocument
                        val docColor = if (isPdf) Color(0xFFDC2626) else Color(0xFF2563EB)
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = docColor.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = if (isPdf) "PDF" else "DOCX",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = docColor,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    if (note.isPinned) {
                        Icon(
                            imageVector = Icons.Filled.PushPin,
                            contentDescription = "Fixada",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }

            Text(
                text = note.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 14.sp
            )

            Text(
                text = SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(note.updatedAt)),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

/**
 * Details Layout Card (Title, Category, Excerpt, and Full Actions)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DetailNoteCard(
    note: Note,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    val cardContainerColor = if (note.isPinned) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardContainerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (note.isPinned) 2.5.dp else 1.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (note.isPinned) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(20.dp)
            )
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(
                onClick = { onClick() },
                onLongClick = { onLongClick?.invoke() }
            )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = categoryIconFor(note.category),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = note.category,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(note.updatedAt)),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(onClick = onTogglePin, modifier = Modifier.size(26.dp)) {
                        Icon(
                            imageVector = if (note.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = if (note.isPinned) "Desafixar" else "Fixar",
                            tint = if (note.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = { onLongClick?.invoke() ?: onDelete() },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Opções",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = note.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (note.content.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                NoteReadingPreview(
                    content = note.content,
                    maxElements = 3,
                    maxTotalLines = 4,
                    compact = false,
                    textColor = MaterialTheme.colorScheme.onSurface,
                    secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * List Layout Card (Compact Warm Pill Row)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListNoteCard(
    note: Note,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    val cardContainerColor = if (note.isPinned) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cardContainerColor,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (note.isPinned) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp)
            )
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = { onClick() },
                onLongClick = { onLongClick?.invoke() }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = note.category.take(8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }

                if (note.isExternalDocument) {
                    val isPdf = note.isPdfDocument
                    val docColor = if (isPdf) Color(0xFFDC2626) else Color(0xFF2563EB)
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = docColor.copy(alpha = 0.16f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = if (isPdf) Icons.Default.PictureAsPdf else Icons.Default.Article,
                                contentDescription = null,
                                tint = docColor,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = if (isPdf) "PDF" else "DOCX",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = docColor
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = note.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (note.isExternalDocument) {
                        Text(
                            text = "Somente leitura · ${ExternalDocumentUriHelper.formatReadablePath(note.externalUri)}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onTogglePin, modifier = Modifier.size(26.dp)) {
                    Icon(
                        imageVector = if (note.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = if (note.isPinned) "Desafixar" else "Fixar",
                        tint = if (note.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = { onLongClick?.invoke() ?: onDelete() },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Opções",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Trash dialog for restoring or permanently deleting notes
 */
@Composable
fun NotesTrashDialog(
    trashNotes: List<Note>,
    onRestore: (Note) -> Unit,
    onRestoreAll: () -> Unit,
    onPermanentDelete: (Note) -> Unit,
    onEmptyTrash: () -> Unit,
    onDismiss: () -> Unit
) {
    var noteToPermanentlyDelete by remember { mutableStateOf<Note?>(null) }
    var showEmptyTrashConfirm by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Lixeira (${trashNotes.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                Text(
                    text = "Anotações excluídas ficam disponíveis por 30 dias para recuperação antes da exclusão permanente.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (trashNotes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "A lixeira está vazia.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onRestoreAll,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("trash_restore_all_btn")
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Restaurar todas", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Button(
                            onClick = { showEmptyTrashConfirm = true },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("trash_empty_btn")
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Esvaziar lixeira", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (showEmptyTrashConfirm) {
                        AlertDialog(
                            onDismissRequest = { showEmptyTrashConfirm = false },
                            title = { Text("Esvaziar lixeira?", fontWeight = FontWeight.Bold) },
                            text = { Text("Todas as ${trashNotes.size} anotações na lixeira serão excluídas permanentemente. Esta ação não pode ser desfeita.") },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        showEmptyTrashConfirm = false
                                        onEmptyTrash()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Esvaziar")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showEmptyTrashConfirm = false }) {
                                    Text("Cancelar")
                                }
                            }
                        )
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                    ) {
                        trashNotes.forEach { note ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = note.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = note.category,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Row {
                                        IconButton(
                                            onClick = { onRestore(note) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Restore,
                                                contentDescription = "Restaurar",
                                                tint = MaterialTheme.colorScheme.secondary
                                            )
                                        }

                                        IconButton(
                                            onClick = { noteToPermanentlyDelete = note },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteForever,
                                                contentDescription = "Excluir Definitivamente",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Concluir")
                }
            }
        }
    }

    if (noteToPermanentlyDelete != null) {
        ConfirmDeleteDialog(
            title = "Excluir Definitivamente",
            message = "Tem certeza de que deseja apagar '${noteToPermanentlyDelete!!.title}' para sempre? Esta ação não pode ser desfeita.",
            confirmButtonText = "Excluir Para Sempre",
            onConfirm = {
                onPermanentDelete(noteToPermanentlyDelete!!)
                noteToPermanentlyDelete = null
            },
            onDismiss = { noteToPermanentlyDelete = null }
        )
    }
}

/**
 * Mini Submenu Dialog displayed when long-pressing a note card or tapping the 3-dots button.
 */
@Composable
fun NoteSubmenuDialog(
    note: Note,
    onDismiss: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onChangeCategory: (() -> Unit)? = null,
    onOpenReadOnly: (() -> Unit)? = null,
    onConvertToNote: (() -> Unit)? = null,
    onRelinkExternal: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val isExternal = note.isExternalDocument

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when {
                        note.isPdfDocument -> Icons.Default.PictureAsPdf
                        note.isDocxDocument -> Icons.Default.Article
                        else -> Icons.Default.Description
                    },
                    contentDescription = null,
                    tint = when {
                        note.isPdfDocument -> Color(0xFFDC2626)
                        note.isDocxDocument -> Color(0xFF2563EB)
                        else -> primaryColor
                    },
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = note.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isExternal && onOpenReadOnly != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismiss()
                                onOpenReadOnly()
                            }
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = null,
                                tint = primaryColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Abrir em Modo Leitura", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                if (isExternal && onConvertToNote != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = primaryColor.copy(alpha = 0.08f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismiss()
                                onConvertToNote()
                            }
                            .padding(vertical = 8.dp, horizontal = 6.dp)
                            .testTag("submenu_convert_to_note")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.EditNote,
                                contentDescription = null,
                                tint = primaryColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Converter para Nota Editável (.md)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = primaryColor
                            )
                        }
                    }
                }

                // Change Category (works for Markdown notes, PDFs, and .docx documents)
                if (onChangeCategory != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismiss()
                                onChangeCategory()
                            }
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                            .testTag("submenu_change_category")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Label,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Alterar Categoria (${note.category})", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                // Pin / Unpin
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onTogglePin()
                            onDismiss()
                        }
                        .padding(vertical = 8.dp, horizontal = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = null,
                            tint = if (note.isPinned) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(if (note.isPinned) "Desafixar do topo" else "Fixar no topo", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                if (isExternal && onRelinkExternal != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismiss()
                                onRelinkExternal()
                            }
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Atualizar Caminho do Arquivo", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                if (!isExternal) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                    // Export Word
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismiss()
                                val blocks = SmoothMarkdownConverter.markdownToBlocks(note.content)
                                val uri = NoteExportHelper.exportToDocx(context, note.title, note.category, blocks, primaryColor.toArgb())
                                NoteExportHelper.shareUri(context, uri, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "Exportar Nota em Word (.docx)")
                            }
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Article, contentDescription = null, tint = primaryColor, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Exportar em Word (.docx)", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    // Export PDF
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismiss()
                                val blocks = SmoothMarkdownConverter.markdownToBlocks(note.content)
                                val uri = NoteExportHelper.exportToPdf(context, note.title, note.category, blocks, primaryColor.toArgb())
                                NoteExportHelper.shareUri(context, uri, "application/pdf", "Exportar Nota em PDF")
                            }
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Exportar em PDF (.pdf)", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    // Export Markdown
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismiss()
                                val blocks = SmoothMarkdownConverter.markdownToBlocks(note.content)
                                val uri = NoteExportHelper.exportToMdFile(context, note.title, blocks)
                                NoteExportHelper.shareUri(context, uri, "text/markdown", "Exportar Nota (.md)")
                            }
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Exportar em Markdown (.md)", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    // Export TXT
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismiss()
                                val blocks = SmoothMarkdownConverter.markdownToBlocks(note.content)
                                val uri = NoteExportHelper.exportToTxtFile(context, note.title, note.category, blocks)
                                NoteExportHelper.shareUri(context, uri, "text/plain", "Exportar Nota (.txt)")
                            }
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Exportar em Texto Puro (.txt)", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    // Export HTML
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismiss()
                                val blocks = SmoothMarkdownConverter.markdownToBlocks(note.content)
                                val uri = NoteExportHelper.exportToHtmlFile(context, note.title, note.category, blocks, primaryColor.toArgb())
                                NoteExportHelper.shareUri(context, uri, "text/html", "Exportar Página Web (.html)")
                            }
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Html, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Exportar Página Web (.html)", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    // Compartilhar como Imagem
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismiss()
                                val blocks = SmoothMarkdownConverter.markdownToBlocks(note.content)
                                val uri = NoteExportHelper.shareAsImage(context, note.title, note.category, blocks, primaryColor.toArgb())
                                NoteExportHelper.shareUri(context, uri, "image/png", "Compartilhar Imagem da Nota")
                            }
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Photo, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Compartilhar como Imagem (Card)", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                if (!isExternal) {
                    // Duplicate note
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDuplicate()
                                onDismiss()
                            }
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Duplicar Nota", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                // Move to trash (or remove link)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDelete()
                            onDismiss()
                        }
                        .padding(vertical = 8.dp, horizontal = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (isExternal) "Remover Vínculo (Lixeira)" else "Mover para a Lixeira",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar")
            }
        }
    )
}
