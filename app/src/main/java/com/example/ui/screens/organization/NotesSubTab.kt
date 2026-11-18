package com.example.ui.screens.organization

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
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
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.local.RecentExternalDocEntry
import com.example.data.model.Note
import com.example.data.model.NoteDocumentType
import com.example.data.model.NoteVersion
import com.example.data.model.TaskPriority
import com.example.data.model.isDocxDocument
import com.example.data.model.isExternalDocument
import com.example.data.model.isPdfDocument
import com.example.data.model.isReadyForMainList
import com.example.data.model.resolvedDocumentType
import com.example.ui.BatchExportUiState
import com.example.ui.MainViewModel
import com.example.ui.components.AddCategoryDialog
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.screens.organization.notes.CategoryNoteStack
import com.example.ui.screens.organization.notes.DocxPageOnePreviewBox
import com.example.ui.screens.organization.notes.DocxSheetAccentColor
import com.example.ui.screens.organization.notes.ExternalDocxReaderScreen
import com.example.ui.screens.organization.notes.ExternalPdfReaderScreen
import com.example.ui.screens.organization.notes.FullScreenNoteEditor
import com.example.ui.screens.organization.notes.NoteCategoryPickerDialog
import com.example.ui.screens.organization.notes.NoteColorPickerDialog
import com.example.ui.screens.organization.notes.RenameExternalDocumentDialog
import com.example.ui.screens.organization.notes.NoteDocumentFilter
import com.example.ui.screens.organization.notes.NoteEditorMode
import com.example.ui.screens.organization.notes.NoteLayoutMode
import com.example.ui.screens.organization.notes.NoteReadingPreview
import com.example.ui.screens.organization.notes.NoteSortOption
import com.example.ui.screens.organization.notes.PaperNoteColors
import com.example.ui.screens.organization.notes.PaperNotePalette
import com.example.ui.screens.organization.notes.PaperNoteSurface
import com.example.ui.screens.organization.notes.PdfPageOnePreviewBox
import com.example.ui.screens.organization.notes.PdfSheetAccentColor
import com.example.ui.screens.organization.notes.SmoothMarkdownConverter
import com.example.ui.screens.organization.notes.filterNotesByDocumentType
import com.example.ui.screens.organization.notes.groupNotesByCategory
import com.example.ui.screens.organization.notes.rememberSelectionWobbleAngle
import com.example.ui.screens.organization.notes.sortNotes
import com.example.ui.theme.*
import com.example.ui.tutorial.tutorialTarget
import com.example.util.BatchExportFormat
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
        match?.groupValues?.getOrNull(2)?.trim()?.takeIf { url ->
            url.isNotBlank() &&
                !url.startsWith("http://", ignoreCase = true) &&
                !url.startsWith("https://", ignoreCase = true)
        }
    }
}

@Composable
fun NotesSubTab(
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val notes by viewModel.notes.collectAsState()
    val archivedNotes by viewModel.archivedNotes.collectAsState()
    val trashNotes by viewModel.trashNotes.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val selectedCategoryFilter by viewModel.selectedNoteCategoryFilter.collectAsState()
    val selectedDocFilter by viewModel.selectedNoteDocumentFilter.collectAsState()
    val defaultNoteOpenMode by viewModel.defaultNoteOpenMode.collectAsState()
    val recentExternalDocs by viewModel.recentExternalDocs.collectAsState()
    val batchExportState by viewModel.batchExportState.collectAsState()
    val openCreateTrigger by viewModel.openCreateNoteTrigger.collectAsState()
    val openTrashTrigger by viewModel.openNotesTrashTrigger.collectAsState()
    val openArchivedTrigger by viewModel.openArchivedNotesTrigger.collectAsState()
    val pendingOpenNoteFromFinances by viewModel.pendingOpenNoteFromFinances.collectAsState()

    val searchQuery by viewModel.notesSearchQuery.collectAsState()
    var activeNoteToEdit by remember { mutableStateOf<Note?>(null) }
    var activePdfToRead by remember { mutableStateOf<Note?>(null) }
    var activeDocxToRead by remember { mutableStateOf<Note?>(null) }
    var isConvertingExternalDoc by remember { mutableStateOf(false) }
    var noteForCategoryPicker by remember { mutableStateOf<Note?>(null) }
    var noteAwaitingRelink by remember { mutableStateOf<Note?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }
    var isCreateMenuExpanded by remember { mutableStateOf(false) }
    var showSpeedDialFinanceDialog by remember { mutableStateOf(false) }
    var recentPickerDocType by remember { mutableStateOf<NoteDocumentType?>(null) }
    var noteToDelete by remember { mutableStateOf<Note?>(null) }
    var noteForSubmenu by remember { mutableStateOf<Note?>(null) }
    var showTrashDialog by remember { mutableStateOf(false) }
    var showArchivedDialog by remember { mutableStateOf(false) }

    // Multi-selection state
    var selectedNoteIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val isSelectionMode = selectedNoteIds.isNotEmpty()
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    var showBatchColorDialog by remember { mutableStateOf(false) }
    var showBatchCategoryDialog by remember { mutableStateOf(false) }
    var showBatchExportDialog by remember { mutableStateOf(false) }

    val notesSortOptionName by viewModel.notesSortOption.collectAsState()
    val isSortAscending by viewModel.notesSortAscending.collectAsState()
    val notesLayoutModeName by viewModel.notesLayoutMode.collectAsState()
    val currentSort = remember(notesSortOptionName) { NoteSortOption.fromName(notesSortOptionName) }
    val currentLayout = remember(notesLayoutModeName) { NoteLayoutMode.fromName(notesLayoutModeName) }
    var showSortMenu by remember { mutableStateOf(false) }
    val expandedCategoryStacks = remember { mutableStateMapOf<String, Boolean>() }

    val toggleNoteSelection: (Note) -> Unit = { target ->
        isCreateMenuExpanded = false
        selectedNoteIds = if (target.id in selectedNoteIds) {
            selectedNoteIds - target.id
        } else {
            selectedNoteIds + target.id
        }
    }

    LaunchedEffect(notes) {
        val activeIds = notes.filter { it.isReadyForMainList() }.map { it.id }.toSet()
        if (selectedNoteIds.any { it !in activeIds }) {
            selectedNoteIds = selectedNoteIds.intersect(activeIds)
        }
    }

    BackHandler(enabled = isSelectionMode) {
        selectedNoteIds = emptySet()
    }

    val openNoteItem: (Note) -> Unit = { target ->
        when (target.resolvedDocumentType) {
            NoteDocumentType.PDF -> {
                activeNoteToEdit = null
                isCreatingNew = false
                isCreateMenuExpanded = false
                activeDocxToRead = null
                activePdfToRead = target
            }
            NoteDocumentType.DOCX -> {
                activeNoteToEdit = null
                isCreatingNew = false
                isCreateMenuExpanded = false
                activePdfToRead = null
                activeDocxToRead = target
            }
            NoteDocumentType.MARKDOWN -> {
                activePdfToRead = null
                activeDocxToRead = null
                activeNoteToEdit = target
                isCreatingNew = false
                isCreateMenuExpanded = false
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

    val pickPdfDocsLauncher = rememberLauncherForActivityResult(
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

    val pickDocxDocsLauncher = rememberLauncherForActivityResult(
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

    val importOtherFilesLauncher = rememberLauncherForActivityResult(
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
            isCreateMenuExpanded = false
            isCreatingNew = true
            viewModel.resetCreateNoteTrigger()
        }
    }

    // Respond to top 3-dots menu Trash / Archived triggers
    LaunchedEffect(openTrashTrigger) {
        if (openTrashTrigger) {
            showTrashDialog = true
            viewModel.resetOpenNotesTrashTrigger()
        }
    }

    LaunchedEffect(openArchivedTrigger) {
        if (openArchivedTrigger) {
            showArchivedDialog = true
            viewModel.resetOpenArchivedNotesTrigger()
        }
    }

    LaunchedEffect(pendingOpenNoteFromFinances) {
        pendingOpenNoteFromFinances?.let { created ->
            openNoteItem(created)
            viewModel.clearPendingOpenNoteFromFinances()
        }
    }

    // Respond to notification / social / intent click to open specific note, PDF, or Word document
    LaunchedEffect(noteToOpen) {
        noteToOpen?.let { note ->
            openNoteItem(note)
            viewModel.clearNoteToOpen()
        }
    }

    // Filter notes by ready state, document type (PDF/DOCX/All), category, and search query
    val filteredNotes = remember(notes, searchQuery, selectedCategoryFilter, selectedDocFilter) {
        val readyAndCategory = notes.filter { note ->
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
        filterNotesByDocumentType(readyAndCategory, selectedDocFilter)
    }

    val selectedNotes = remember(notes, selectedNoteIds) {
        if (selectedNoteIds.isEmpty()) emptyList()
        else notes.filter { it.id in selectedNoteIds }
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
            onRenameDocument = { newTitle ->
                viewModel.renameExternalDocument(pdfNote, newTitle, context) { updated ->
                    activePdfToRead = updated
                }
            },
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

    // Full-screen Word (.docx) Reader & Inline Text Editor
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
            onRenameDocument = { newTitle ->
                viewModel.renameExternalDocument(docxNote, newTitle, context) { updated ->
                    activeDocxToRead = updated
                }
            },
            onSaveDocxEdits = { editsByParagraphIndex, onDone, onErr ->
                viewModel.saveEditedDocxDocument(
                    note = docxNote,
                    editsByParagraphIndex = editsByParagraphIndex,
                    context = context,
                    onSuccess = onDone,
                    onError = onErr
                )
            },
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
                initialMode = if (isCreatingNew) {
                    NoteEditorMode.LIVE_PREVIEW
                } else if (defaultNoteOpenMode == "READING") {
                    NoteEditorMode.READING
                } else {
                    NoteEditorMode.LIVE_PREVIEW
                },
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Scrollable Category & Document Type Tabs (Pill Chips)
            val readyNotes = remember(notes) { notes.filter { it.isReadyForMainList() } }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .tutorialTarget("notes_category_bar")
            ) {
                // "Todas" Pill
                item(key = "filter_all") {
                    val isSelected = selectedCategoryFilter == "Todas" && selectedDocFilter == NoteDocumentFilter.ALL
                    FolderPillChip(
                        label = "Todas",
                        count = readyNotes.size,
                        icon = Icons.Default.Folder,
                        selected = isSelected,
                        iconTint = if (isSelected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.primary,
                        onClick = {
                            viewModel.setSelectedNoteCategoryFilter("Todas")
                            viewModel.setSelectedNoteDocumentFilter(NoteDocumentFilter.ALL)
                        }
                    )
                }

                // Document-type Filter Pills: PDFs and .docx
                item(key = "filter_pdfs") {
                    val pdfCount = readyNotes.count { it.isPdfDocument }
                    val isSelected = selectedDocFilter == NoteDocumentFilter.PDF
                    FolderPillChip(
                        label = "PDFs",
                        count = pdfCount,
                        icon = Icons.Default.PictureAsPdf,
                        selected = isSelected,
                        iconTint = if (isSelected) MaterialTheme.colorScheme.onSecondary else PdfSheetAccentColor,
                        onClick = {
                            viewModel.setSelectedNoteDocumentFilter(
                                if (isSelected) NoteDocumentFilter.ALL else NoteDocumentFilter.PDF
                            )
                        }
                    )
                }

                item(key = "filter_docx") {
                    val docxCount = readyNotes.count { it.isDocxDocument }
                    val isSelected = selectedDocFilter == NoteDocumentFilter.DOCX
                    FolderPillChip(
                        label = ".docx",
                        count = docxCount,
                        icon = Icons.Default.Article,
                        selected = isSelected,
                        iconTint = if (isSelected) MaterialTheme.colorScheme.onSecondary else DocxSheetAccentColor,
                        onClick = {
                            viewModel.setSelectedNoteDocumentFilter(
                                if (isSelected) NoteDocumentFilter.ALL else NoteDocumentFilter.DOCX
                            )
                        }
                    )
                }

                // Category Pills
                items(categories, key = { it.name }) { cat ->
                    val count = readyNotes.count { it.category.equals(cat.name, ignoreCase = true) }
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
            }

            // 2. View & Sort Controls Row (Primary Sort + Secondary Asc/Desc inside menu + Layout Modes incl. Stacked Folders)
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Primary Sort Dropdown Pill (includes Ascendente / Decrescente secondary order inside menu)
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
                                imageVector = if (isSortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                imageVector = Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
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
        val listBottomPadding = if (isSelectionMode) 118.dp else 92.dp
        val listTopPadding = if (isSelectionMode) 68.dp else 8.dp

        when (currentLayout) {
            NoteLayoutMode.GRID_2 -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = listTopPadding, bottom = listBottomPadding),
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
                            val selected = note.id in selectedNoteIds
                            SquareNoteCard(
                                note = note,
                                isSelectionMode = isSelectionMode,
                                isSelected = selected,
                                onClick = {
                                    if (isSelectionMode) toggleNoteSelection(note) else openNoteItem(note)
                                },
                                onLongClick = { toggleNoteSelection(note) },
                                onMoreClick = { noteForSubmenu = note },
                                onTogglePin = { viewModel.togglePinNote(note) },
                                onDelete = { noteToDelete = note }
                            )
                        }
                    }
                    // Quick Draft Tile (Card 6 from HTML)
                    if (!isSelectionMode) {
                        item {
                            QuickDraftTile(
                                onClick = { showTemplatesMenu = true }
                            )
                        }
                    }
                }
            }
            NoteLayoutMode.GRID_3 -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = listTopPadding, bottom = listBottomPadding),
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
                            val selected = note.id in selectedNoteIds
                            SmallSquareNoteCard(
                                note = note,
                                isSelectionMode = isSelectionMode,
                                isSelected = selected,
                                onClick = {
                                    if (isSelectionMode) toggleNoteSelection(note) else openNoteItem(note)
                                },
                                onLongClick = { toggleNoteSelection(note) },
                                onMoreClick = { noteForSubmenu = note },
                                onTogglePin = { viewModel.togglePinNote(note) },
                                onDelete = { noteToDelete = note }
                            )
                        }
                    }
                    if (!isSelectionMode) {
                        item {
                            QuickDraftTile(
                                onClick = { showTemplatesMenu = true }
                            )
                        }
                    }
                }
            }
            NoteLayoutMode.LIST -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = listTopPadding, bottom = listBottomPadding),
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
                            val selected = note.id in selectedNoteIds
                            ListNoteCard(
                                note = note,
                                isSelectionMode = isSelectionMode,
                                isSelected = selected,
                                onClick = {
                                    if (isSelectionMode) toggleNoteSelection(note) else openNoteItem(note)
                                },
                                onLongClick = { toggleNoteSelection(note) },
                                onMoreClick = { noteForSubmenu = note },
                                onTogglePin = { viewModel.togglePinNote(note) },
                                onDelete = { noteToDelete = note }
                            )
                        }
                    }
                    if (!isSelectionMode) {
                        item {
                            QuickDraftTile(
                                onClick = { showTemplatesMenu = true }
                            )
                        }
                    }
                }
            }
            NoteLayoutMode.STACKED_CATEGORIES -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = listTopPadding, bottom = listBottomPadding),
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
                                onNoteClick = { note ->
                                    if (isSelectionMode) toggleNoteSelection(note) else openNoteItem(note)
                                },
                                onNoteLongClick = { note -> toggleNoteSelection(note) },
                                onNoteMoreClick = { note -> noteForSubmenu = note },
                                onTogglePin = { viewModel.togglePinNote(it) },
                                onDeleteNote = { noteToDelete = it },
                                isSelectionMode = isSelectionMode,
                                selectedNoteIds = selectedNoteIds
                            )
                        }
                    }
                    if (!isSelectionMode) {
                        item {
                            QuickDraftTile(
                                onClick = { showTemplatesMenu = true }
                            )
                        }
                    }
                }
            }
        }

        // Top Multi-Selection Header Bar
        AnimatedVisibility(
            visible = isSelectionMode,
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            val allVisibleSelected = sortedNotes.isNotEmpty() && sortedNotes.all { it.id in selectedNoteIds }
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("notes_selection_top_bar")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { selectedNoteIds = emptySet() },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("selection_close_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Sair da seleção"
                            )
                        }
                        Text(
                            text = "${selectedNoteIds.size} selecionada(s)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    TextButton(
                        onClick = {
                            selectedNoteIds = if (allVisibleSelected) {
                                emptySet()
                            } else {
                                sortedNotes.map { it.id }.toSet()
                            }
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("selection_select_all_btn")
                    ) {
                        Text(
                            text = if (allVisibleSelected) "Desmarcar todas" else "Selecionar todas",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Momentary Bottom Batch Action Bar in Multi-Selection Mode
        AnimatedVisibility(
            visible = isSelectionMode,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("notes_selection_action_bar")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BatchSelectionBarAction(
                        label = "Apagar",
                        icon = Icons.Default.DeleteOutline,
                        tint = MaterialTheme.colorScheme.error,
                        testTag = "batch_action_delete",
                        onClick = { showBatchDeleteConfirm = true }
                    )
                    BatchSelectionBarAction(
                        label = "Arquivar",
                        icon = Icons.Default.Archive,
                        tint = MaterialTheme.colorScheme.secondary,
                        testTag = "batch_action_archive",
                        onClick = {
                            viewModel.setNotesArchived(selectedNotes, true)
                            selectedNoteIds = emptySet()
                        }
                    )
                    BatchSelectionBarAction(
                        label = "Exportar",
                        icon = Icons.Default.IosShare,
                        tint = MaterialTheme.colorScheme.primary,
                        testTag = "batch_action_export",
                        onClick = { showBatchExportDialog = true }
                    )
                    BatchSelectionBarAction(
                        label = "Colorir",
                        icon = Icons.Default.Palette,
                        tint = MaterialTheme.colorScheme.tertiary,
                        testTag = "batch_action_color",
                        onClick = { showBatchColorDialog = true }
                    )
                    BatchSelectionBarAction(
                        label = "Categoria",
                        icon = Icons.Default.Label,
                        tint = MaterialTheme.colorScheme.onSurface,
                        testTag = "batch_action_category",
                        onClick = { showBatchCategoryDialog = true }
                    )
                }
            }
        }

        // Dimmed backdrop when FAB speed-dial is open
        AnimatedVisibility(
            visible = isCreateMenuExpanded && !isSelectionMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (isAppDarkTheme()) 0.58f else 0.44f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { isCreateMenuExpanded = false }
                    .testTag("notes_speed_dial_scrim")
            )
        }

        BackHandler(enabled = isCreateMenuExpanded) {
            isCreateMenuExpanded = false
        }

        // Speed-Dial circular action buttons stacked cleanly ABOVE the FAB
        AnimatedVisibility(
            visible = isCreateMenuExpanded && !isSelectionMode,
            enter = fadeIn() + slideInVertically { it / 3 } + scaleIn(initialScale = 0.9f),
            exit = fadeOut() + slideOutVertically { it / 3 } + scaleOut(targetScale = 0.9f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 84.dp)
        ) {
            val dialColor = MaterialTheme.colorScheme.primary
            val dialIconTint = if (dialColor.luminance() > 0.45f) Color(0xFF141218) else Color.White

            Column(
                modifier = Modifier
                    .widthIn(max = 310.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                NotesSpeedDialItem(
                    label = "Outro",
                    subtitle = ExternalDocumentUriHelper.OTHER_SUPPORTED_EXTENSIONS_LABEL,
                    icon = Icons.Default.SnippetFolder,
                    buttonColor = dialColor,
                    iconTint = dialIconTint,
                    testTag = "speed_dial_other",
                    onClick = {
                        isCreateMenuExpanded = false
                        importOtherFilesLauncher.launch(ExternalDocumentUriHelper.OTHER_IMPORT_MIME_TYPES)
                    }
                )
                if (financesEnabled) {
                    NotesSpeedDialItem(
                        label = "Bloco de Finanças",
                        icon = Icons.Default.AccountBalanceWallet,
                        buttonColor = dialColor,
                        iconTint = dialIconTint,
                        testTag = "speed_dial_finance_block",
                        onClick = {
                            isCreateMenuExpanded = false
                            showSpeedDialFinanceDialog = true
                        }
                    )
                }
                NotesSpeedDialItem(
                    label = "Rascunhos",
                    icon = Icons.Default.DashboardCustomize,
                    buttonColor = dialColor,
                    iconTint = dialIconTint,
                    testTag = "speed_dial_drafts",
                    onClick = {
                        isCreateMenuExpanded = false
                        showTemplatesMenu = true
                    }
                )
                NotesSpeedDialItem(
                    label = "Word (.docx)",
                    icon = Icons.Default.Article,
                    buttonColor = dialColor,
                    iconTint = dialIconTint,
                    testTag = "speed_dial_docx",
                    onClick = {
                        isCreateMenuExpanded = false
                        recentPickerDocType = NoteDocumentType.DOCX
                    }
                )
                NotesSpeedDialItem(
                    label = "PDF",
                    icon = Icons.Default.PictureAsPdf,
                    buttonColor = dialColor,
                    iconTint = dialIconTint,
                    testTag = "speed_dial_pdf",
                    onClick = {
                        isCreateMenuExpanded = false
                        recentPickerDocType = NoteDocumentType.PDF
                    }
                )
                NotesSpeedDialItem(
                    label = "Importar notas",
                    icon = Icons.Default.FileOpen,
                    buttonColor = dialColor,
                    iconTint = dialIconTint,
                    testTag = "speed_dial_import_notes",
                    onClick = {
                        isCreateMenuExpanded = false
                        importMultipleNotesLauncher.launch(ExternalDocumentUriHelper.MARKDOWN_IMPORT_MIME_TYPES)
                    }
                )
                NotesSpeedDialItem(
                    label = "Criar notas",
                    icon = Icons.Default.EditNote,
                    buttonColor = dialColor,
                    iconTint = dialIconTint,
                    testTag = "speed_dial_create_note",
                    onClick = {
                        isCreateMenuExpanded = false
                        isCreatingNew = true
                    }
                )
            }
        }

        // Circular Citrus Glow FAB (hidden while multi-selection action bar is active)
        if (!isSelectionMode) {
            val fabTint = if (MaterialTheme.colorScheme.primary.luminance() > 0.45f) Color(0xFF141218) else Color.White
            FloatingActionButton(
                onClick = { isCreateMenuExpanded = !isCreateMenuExpanded },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = fabTint,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .size(56.dp)
                    .tutorialTarget("notes_add_button")
                    .testTag("add_note_fab")
            ) {
                Icon(
                    imageVector = if (isCreateMenuExpanded) Icons.Default.Close else Icons.Default.Add,
                    contentDescription = if (isCreateMenuExpanded) "Fechar menu de criação" else "Opções de criação",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }

    // Recent PDF / DOCX picker modal with search bar and multi-file import from device
    recentPickerDocType?.let { targetType ->
        val allTypeEntries = remember(recentExternalDocs, notes, archivedNotes, targetType) {
            val merged = linkedMapOf<String, RecentExternalDocEntry>()
            recentExternalDocs
                .filter { NoteDocumentType.fromId(it.docType) == targetType }
                .forEach { merged[it.uri.trim()] = it }
            (notes + archivedNotes)
                .filter { it.resolvedDocumentType == targetType && !it.externalUri.isNullOrBlank() }
                .forEach { existing ->
                    val uriKey = existing.externalUri!!.trim()
                    if (!merged.containsKey(uriKey)) {
                        merged[uriKey] = RecentExternalDocEntry(
                            uri = uriKey,
                            title = existing.title.ifBlank { "Documento" },
                            docType = targetType.id,
                            addedAt = existing.updatedAt
                        )
                    }
                }
            merged.values.sortedByDescending { it.addedAt }
        }

        RecentExternalDocsPickerDialog(
            docType = targetType,
            recentDocs = allTypeEntries,
            onImportFromDevice = {
                recentPickerDocType = null
                if (targetType == NoteDocumentType.PDF) {
                    pickPdfDocsLauncher.launch(ExternalDocumentUriHelper.PDF_PICKER_MIME_TYPES)
                } else {
                    pickDocxDocsLauncher.launch(ExternalDocumentUriHelper.DOCX_PICKER_MIME_TYPES)
                }
            },
            onLoadEntries = { entries ->
                recentPickerDocType = null
                viewModel.openOrAddRecentExternalDocs(
                    entries = entries,
                    preferredCategory = selectedCategoryFilter,
                    openIfSingle = true
                )
            },
            onRemoveRecent = { entry ->
                viewModel.removeRecentExternalDoc(entry.uri)
            },
            onDismiss = { recentPickerDocType = null }
        )
    }

    // Batch Selection Dialogs
    if (showBatchDeleteConfirm && selectedNotes.isNotEmpty()) {
        ConfirmDeleteDialog(
            title = "Mover ${selectedNotes.size} para a Lixeira?",
            message = "Os itens selecionados serão movidos para a lixeira. Documentos PDF e Word (.docx) originais no aparelho nunca são apagados.",
            confirmButtonText = "Mover para Lixeira",
            onConfirm = {
                viewModel.moveNotesToTrash(selectedNotes)
                selectedNoteIds = emptySet()
                showBatchDeleteConfirm = false
            },
            onDismiss = { showBatchDeleteConfirm = false }
        )
    }

    if (showBatchColorDialog && selectedNotes.isNotEmpty()) {
        NoteColorPickerDialog(
            currentColorKey = null,
            onDismiss = { showBatchColorDialog = false },
            onSelectColor = { chosenColor ->
                viewModel.updateNotesColor(selectedNotes, chosenColor)
                selectedNoteIds = emptySet()
                showBatchColorDialog = false
            }
        )
    }

    if (showBatchCategoryDialog && selectedNotes.isNotEmpty()) {
        NoteCategoryPickerDialog(
            currentCategory = selectedNotes.firstOrNull()?.category ?: "Geral",
            categories = categories,
            onSelectCategory = { newCat ->
                viewModel.updateNotesCategory(selectedNotes, newCat)
                selectedNoteIds = emptySet()
                showBatchCategoryDialog = false
            },
            onCreateCategory = { name, emoji ->
                viewModel.addCustomCategory(name, emoji)
            },
            onDismiss = { showBatchCategoryDialog = false }
        )
    }

    if (showBatchExportDialog && selectedNotes.isNotEmpty()) {
        val exportThemeColor = MaterialTheme.colorScheme.primary.toArgb()
        BatchExportFormatDialog(
            selectedCount = selectedNotes.size,
            onSelectFormat = { chosenFormat ->
                val toExport = selectedNotes
                showBatchExportDialog = false
                selectedNoteIds = emptySet()
                viewModel.startBatchExport(
                    context = context,
                    targetNotes = toExport,
                    format = chosenFormat,
                    themeColor = exportThemeColor
                )
            },
            onDismiss = { showBatchExportDialog = false }
        )
    }

    if (batchExportState !is BatchExportUiState.Idle) {
        BatchExportProgressDialog(
            state = batchExportState,
            onCancel = { viewModel.cancelBatchExport() },
            onShare = { output ->
                NoteExportHelper.shareUri(
                    context = context,
                    uri = output.uri,
                    mimeType = output.mimeType,
                    title = "Compartilhar exportação Citra"
                )
            },
            onDismiss = { viewModel.clearBatchExportState() }
        )
    }

    // Finance Note Link Creator Dialog called directly from Notes Speed-Dial (identical menu to Finances)
    if (showSpeedDialFinanceDialog && financesEnabled) {
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
        com.example.ui.screens.finances.FinanceNoteLinkCreatorDialog(
            notes = notes,
            categories = finCategories,
            entries = finEntries,
            currentBalanceMinor = currentBal,
            defaultCurrency = activeCurr,
            preselectedNote = null,
            onDismiss = { showSpeedDialFinanceDialog = false },
            onConfirmCreateLink = { link, createNewNote, newNoteTitle, template ->
                viewModel.createAndRunFinanceNoteLink(link, createNewNote, newNoteTitle, template)
                showSpeedDialFinanceDialog = false
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

    // Confirmation dialog before deleting note or removing PDF / Word from list
    if (noteToDelete != null) {
        val target = noteToDelete!!
        ConfirmDeleteDialog(
            title = when (target.resolvedDocumentType) {
                NoteDocumentType.PDF -> "Remover PDF"
                NoteDocumentType.DOCX -> "Remover Word (.docx)"
                NoteDocumentType.MARKDOWN -> "Mover para a Lixeira"
            },
            message = when (target.resolvedDocumentType) {
                NoteDocumentType.PDF -> "Deseja mover este PDF ('${target.title}') para a lixeira? O arquivo original no seu aparelho não será apagado."
                NoteDocumentType.DOCX -> "Deseja mover este Word (.docx) ('${target.title}') para a lixeira? O arquivo original no seu aparelho não será apagado."
                NoteDocumentType.MARKDOWN -> "Deseja mover a anotação '${target.title}' para a lixeira? Ela ficará disponível por 30 dias para restauração."
            },
            confirmButtonText = "Mover para Lixeira",
            onConfirm = {
                viewModel.moveNoteToTrash(target)
                noteToDelete = null
            },
            onDismiss = { noteToDelete = null }
        )
    }

    var noteToRenameExternal by remember { mutableStateOf<Note?>(null) }
    noteToRenameExternal?.let { target ->
        RenameExternalDocumentDialog(
            currentTitle = target.title,
            docTypeLabel = if (target.isPdfDocument) "PDF" else "Word (.docx)",
            onDismiss = { noteToRenameExternal = null },
            onConfirm = { newTitle ->
                viewModel.renameExternalDocument(target, newTitle, context)
                noteToRenameExternal = null
            }
        )
    }

    // Mini Submenu on Note Long-Press or 3-dots click
    noteForSubmenu?.let { note ->
        NoteSubmenuDialog(
            note = note,
            onDismiss = { noteForSubmenu = null },
            onTogglePin = { viewModel.togglePinNote(note) },
            onToggleArchive = { viewModel.setNoteArchived(note, !note.isArchived) },
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
            onRenameExternal = if (note.isExternalDocument) {
                { noteToRenameExternal = note }
            } else null,
            onRelinkExternal = if (note.isExternalDocument) {
                {
                    noteAwaitingRelink = note
                    val mime = if (note.isPdfDocument) {
                        ExternalDocumentUriHelper.PDF_PICKER_MIME_TYPES
                    } else {
                        ExternalDocumentUriHelper.DOCX_PICKER_MIME_TYPES
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

    // Archived Notes Dialog
    if (showArchivedDialog) {
        ArchivedNotesDialog(
            archivedNotes = archivedNotes,
            onOpenNote = { note ->
                showArchivedDialog = false
                openNoteItem(note)
            },
            onUnarchive = { viewModel.setNoteArchived(it, false) },
            onMoveToTrash = { noteToDelete = it },
            onDismiss = { showArchivedDialog = false }
        )
    }
}

@Composable
private fun NotesSpeedDialItem(
    label: String,
    subtitle: String? = null,
    icon: ImageVector,
    buttonColor: Color,
    iconTint: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .testTag(testTag)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 4.dp,
            shadowElevation = 4.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Surface(
            shape = CircleShape,
            color = buttonColor,
            shadowElevation = 5.dp,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
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
 * Warm Tactile 2-Column Card for Notes with Dog-Ear Paper Flap, Selection Wobble & PDF/DOCX Page 1 Previews
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SquareNoteCard(
    note: Note,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onMoreClick: (() -> Unit)? = null,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    val isDark = isAppDarkTheme()
    val paperColors = remember(note.noteColor, note.documentType, isDark, note.isPinned) {
        PaperNotePalette.resolvePaperColors(note, isDark)
    }
    val wobbleDeg = rememberSelectionWobbleAngle(isSelectionMode = isSelectionMode, isSelected = isSelected)
    val dateText = remember(note.updatedAt) {
        SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(note.updatedAt))
    }
    val hasAudio = remember(note.content) {
        note.content.contains("![audio:") || note.content.contains("audio:")
    }
    val previewImageUri = extractFirstImageUri(note)
    val hasImage = previewImageUri != null

    val badgeBg = paperColors.badgeBg
    val badgeFg = paperColors.badgeFg

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .rotate(wobbleDeg)
    ) {
        PaperNoteSurface(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .then(
                    if (isSelected) {
                        Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))
                    } else Modifier
                )
                .combinedClickable(
                    onClick = { onClick() },
                    onLongClick = { onLongClick?.invoke() }
                )
                .testTag("note_square_card_${note.id}"),
            cornerRadius = 20.dp,
            foldSize = 26.dp,
            paperColors = paperColors,
            isPinned = note.isPinned || isSelected
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
                    // Top row: Selection Check or Category Pill + Pin + 3-dots
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 22.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            if (isSelectionMode) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
                                        )
                                        .border(
                                            width = 1.5.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selecionada",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }

                            Surface(
                                shape = CircleShape,
                                color = badgeBg
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
                                    tint = if (note.isPinned) paperColors.accentColor else paperColors.mutedIconColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            IconButton(
                                onClick = { onMoreClick?.invoke() ?: onLongClick?.invoke() ?: onDelete() },
                                modifier = Modifier
                                    .size(24.dp)
                                    .testTag("note_more_btn_${note.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Opções",
                                    tint = paperColors.mutedIconColor,
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

                    // External PDF / DOCX Page 1 mini-sheet preview inside SquareNoteCard
                    if (note.isExternalDocument) {
                        if (note.isPdfDocument) {
                            PdfPageOnePreviewBox(
                                externalUri = note.externalUri,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(104.dp)
                            )
                        } else {
                            DocxPageOnePreviewBox(
                                externalUri = note.externalUri,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(104.dp)
                            )
                        }
                    }

                    // Note Title - high contrast according to app theme
                    Text(
                        text = note.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontSize = 15.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = paperColors.onBackgroundColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Note Reading-Mode Preview for Markdown notes
                    if (!note.isExternalDocument && note.content.isNotBlank()) {
                        NoteReadingPreview(
                            content = note.content,
                            maxElements = if (previewImageUri != null) 2 else 4,
                            maxTotalLines = if (previewImageUri != null) 3 else 5,
                            compact = true,
                            textColor = paperColors.onBackgroundColor,
                            secondaryTextColor = paperColors.onBackgroundColor.copy(alpha = 0.72f),
                            accentColor = paperColors.accentColor,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Bottom row: Schedule Date + Media Badges / Document Type Tag
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
                            tint = paperColors.metaTextColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = dateText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = paperColors.metaTextColor
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (note.isExternalDocument) {
                            Text(
                                text = if (note.isPdfDocument) "PDF" else "DOCX",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (note.isPdfDocument) PdfSheetAccentColor else DocxSheetAccentColor
                            )
                        }
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
}

/**
 * Small 3-Column Card for Notes
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SmallSquareNoteCard(
    note: Note,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onMoreClick: (() -> Unit)? = null,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    val isDark = isAppDarkTheme()
    val paperColors = remember(note.noteColor, note.documentType, isDark, note.isPinned) {
        PaperNotePalette.resolvePaperColors(note, isDark)
    }
    val wobbleDeg = rememberSelectionWobbleAngle(isSelectionMode = isSelectionMode, isSelected = isSelected)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = paperColors.background),
        elevation = CardDefaults.cardElevation(defaultElevation = if (note.isPinned || isSelected) 2.5.dp else 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.86f)
            .rotate(wobbleDeg)
            .border(
                width = if (isSelected) 2.5.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else paperColors.borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = { onClick() },
                onLongClick = { onLongClick?.invoke() }
            )
            .testTag("note_small_card_${note.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (isSelectionMode) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
                            )
                            .border(
                                width = 1.5.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selecionada",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = CircleShape,
                        color = paperColors.badgeBg
                    ) {
                        Text(
                            text = note.category.take(6),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = paperColors.badgeFg,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (note.isPinned) {
                        Icon(
                            imageVector = Icons.Filled.PushPin,
                            contentDescription = "Fixada",
                            tint = paperColors.accentColor,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    IconButton(
                        onClick = { onMoreClick?.invoke() ?: onLongClick?.invoke() ?: onDelete() },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Opções",
                            tint = paperColors.mutedIconColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            if (note.isExternalDocument) {
                if (note.isPdfDocument) {
                    PdfPageOnePreviewBox(
                        externalUri = note.externalUri,
                        compact = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    )
                } else {
                    DocxPageOnePreviewBox(
                        externalUri = note.externalUri,
                        compact = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    )
                }
            }

            Text(
                text = note.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = paperColors.onBackgroundColor,
                maxLines = if (note.isExternalDocument) 2 else 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 13.5.sp
            )

            Text(
                text = SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(note.updatedAt)),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = paperColors.metaTextColor
            )
        }
    }
}

/**
 * Details Layout Card (Title, Category, Excerpt/Preview, and Full Actions)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DetailNoteCard(
    note: Note,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onMoreClick: (() -> Unit)? = null,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    val isDark = isAppDarkTheme()
    val paperColors = remember(note.noteColor, note.documentType, isDark, note.isPinned) {
        PaperNotePalette.resolvePaperColors(note, isDark)
    }
    val wobbleDeg = rememberSelectionWobbleAngle(isSelectionMode = isSelectionMode, isSelected = isSelected)

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = paperColors.background),
        elevation = CardDefaults.cardElevation(defaultElevation = if (note.isPinned || isSelected) 2.5.dp else 1.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .rotate(wobbleDeg)
            .border(
                width = if (isSelected) 2.5.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else paperColors.borderColor,
                shape = RoundedCornerShape(20.dp)
            )
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(
                onClick = { onClick() },
                onLongClick = { onLongClick?.invoke() }
            )
            .testTag("note_detail_card_${note.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isSelectionMode) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
                                )
                                .border(
                                    width = 1.5.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selecionada",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = paperColors.badgeBg
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = categoryIconFor(note.category),
                                contentDescription = null,
                                tint = paperColors.badgeFg,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = note.category,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = paperColors.badgeFg
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(note.updatedAt)),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = paperColors.metaTextColor
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(onClick = onTogglePin, modifier = Modifier.size(26.dp)) {
                        Icon(
                            imageVector = if (note.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = if (note.isPinned) "Desafixar" else "Fixar",
                            tint = if (note.isPinned) paperColors.accentColor else paperColors.mutedIconColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = { onMoreClick?.invoke() ?: onLongClick?.invoke() ?: onDelete() },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Opções",
                            tint = paperColors.mutedIconColor,
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
                color = paperColors.onBackgroundColor
            )

            if (note.isExternalDocument) {
                Spacer(modifier = Modifier.height(8.dp))
                if (note.isPdfDocument) {
                    PdfPageOnePreviewBox(
                        externalUri = note.externalUri,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                    )
                } else {
                    DocxPageOnePreviewBox(
                        externalUri = note.externalUri,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                    )
                }
            } else if (note.content.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                NoteReadingPreview(
                    content = note.content,
                    maxElements = 3,
                    maxTotalLines = 4,
                    compact = false,
                    textColor = paperColors.onBackgroundColor,
                    secondaryTextColor = paperColors.onBackgroundColor.copy(alpha = 0.72f),
                    accentColor = paperColors.accentColor,
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
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onMoreClick: (() -> Unit)? = null,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    val isDark = isAppDarkTheme()
    val paperColors = remember(note.noteColor, note.documentType, isDark, note.isPinned) {
        PaperNotePalette.resolvePaperColors(note, isDark)
    }
    val wobbleDeg = rememberSelectionWobbleAngle(isSelectionMode = isSelectionMode, isSelected = isSelected)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = paperColors.background,
        modifier = Modifier
            .fillMaxWidth()
            .rotate(wobbleDeg)
            .border(
                width = if (isSelected) 2.5.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else paperColors.borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = { onClick() },
                onLongClick = { onLongClick?.invoke() }
            )
            .testTag("note_list_card_${note.id}")
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
                if (isSelectionMode) {
                    Box(
                        modifier = Modifier
                            .padding(end = 10.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
                            )
                            .border(
                                width = 1.5.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selecionada",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                if (note.isExternalDocument) {
                    Box(
                        modifier = Modifier
                            .padding(end = 10.dp)
                            .size(width = 46.dp, height = 58.dp)
                    ) {
                        if (note.isPdfDocument) {
                            PdfPageOnePreviewBox(
                                externalUri = note.externalUri,
                                compact = true,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            DocxPageOnePreviewBox(
                                externalUri = note.externalUri,
                                compact = true,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = CircleShape,
                        color = paperColors.badgeBg
                    ) {
                        Text(
                            text = note.category.take(8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = paperColors.badgeFg,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = note.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = paperColors.onBackgroundColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (note.isExternalDocument) {
                        Text(
                            text = "${if (note.isPdfDocument) "PDF" else "Word (.docx)"} · ${note.category}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (note.isPdfDocument) PdfSheetAccentColor else DocxSheetAccentColor,
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
                        tint = if (note.isPinned) paperColors.accentColor else paperColors.mutedIconColor,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = { onMoreClick?.invoke() ?: onLongClick?.invoke() ?: onDelete() },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Opções",
                        tint = paperColors.mutedIconColor,
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
                            text = {
                                Text(
                                    "Todas as ${trashNotes.size} anotações na lixeira, seus ficheiros .md e mídias exclusivas (imagens/áudios) serão excluídos definitivamente do armazenamento do aparelho. Esta ação não pode ser desfeita."
                                )
                            },
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
            message = "Tem certeza de que deseja apagar '${noteToPermanentlyDelete!!.title}'? O ficheiro .md da nota e suas mídias exclusivas também serão removidos definitivamente do armazenamento do aparelho.",
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
    onToggleArchive: (() -> Unit)? = null,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onChangeCategory: (() -> Unit)? = null,
    onOpenReadOnly: (() -> Unit)? = null,
    onConvertToNote: (() -> Unit)? = null,
    onRenameExternal: (() -> Unit)? = null,
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

                if (isExternal && onRenameExternal != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismiss()
                                onRenameExternal()
                            }
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                            .testTag("submenu_rename_external")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.DriveFileRenameOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Renomear arquivo", style = MaterialTheme.typography.bodyMedium)
                        }
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
                            Text("Carregar outro arquivo", style = MaterialTheme.typography.bodyMedium)
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

                // Archive / Unarchive
                if (onToggleArchive != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onToggleArchive()
                                onDismiss()
                            }
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                            .testTag("submenu_archive_note")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (note.isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = if (note.isArchived) "Desarquivar" else "Arquivar",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                // Move to trash
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
                            text = when (note.resolvedDocumentType) {
                                NoteDocumentType.PDF -> "Mover PDF para a Lixeira"
                                NoteDocumentType.DOCX -> "Mover Word para a Lixeira"
                                NoteDocumentType.MARKDOWN -> "Mover para a Lixeira"
                            },
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

/**
 * Dialog showing Archived Notes (regular notes, PDFs, and Word docs).
 * Clicking any archived note opens it normally; users can also unarchive or move to trash.
 */
@Composable
fun ArchivedNotesDialog(
    archivedNotes: List<Note>,
    onOpenNote: (Note) -> Unit,
    onUnarchive: (Note) -> Unit,
    onMoveToTrash: (Note) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .testTag("archived_notes_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Archive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Notas Arquivadas",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Suas notas arquivadas não aparecem na tela inicial, mas abrem normalmente por aqui.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))

                if (archivedNotes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Archive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = "Nenhuma nota arquivada",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(archivedNotes, key = { it.id }) { note ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onOpenNote(note) }
                                    .testTag("archived_note_item_${note.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
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
                                                else -> MaterialTheme.colorScheme.primary
                                            },
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = note.title.ifBlank { "Sem título" },
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = note.category.ifBlank { "Geral" },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { onUnarchive(note) },
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Unarchive,
                                                contentDescription = "Desarquivar",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { onMoveToTrash(note) },
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Mover para a Lixeira",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Fechar")
                }
            }
        }
    }
}

@Composable
private fun BatchSelectionBarAction(
    label: String,
    icon: ImageVector,
    tint: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(21.dp)
        )
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

@Composable
private fun BatchExportFormatDialog(
    selectedCount: Int,
    onSelectFormat: (BatchExportFormat) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .testTag("batch_export_format_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.IosShare,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Exportar $selectedCount item(ns)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                Text(
                    text = if (selectedCount > 1) {
                        "Os arquivos serão empacotados em um único pacote .zip de forma segura."
                    } else {
                        "Escolha o formato desejado para exportar a nota selecionada."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(BatchExportFormat.entries) { format ->
                        val icon = when (format) {
                            BatchExportFormat.DOCX -> Icons.Default.Article
                            BatchExportFormat.PDF -> Icons.Default.PictureAsPdf
                            BatchExportFormat.MARKDOWN -> Icons.Default.Description
                            BatchExportFormat.HTML -> Icons.Default.Html
                            BatchExportFormat.TXT -> Icons.Default.TextSnippet
                            BatchExportFormat.JSON_BACKUP -> Icons.Default.DataObject
                        }
                        val tint = when (format) {
                            BatchExportFormat.DOCX -> DocxSheetAccentColor
                            BatchExportFormat.PDF -> PdfSheetAccentColor
                            else -> MaterialTheme.colorScheme.primary
                        }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onSelectFormat(format) }
                                .testTag("batch_export_format_${format.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = tint,
                                    modifier = Modifier.size(22.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = format.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = format.subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchExportProgressDialog(
    state: BatchExportUiState,
    onCancel: () -> Unit,
    onShare: (NoteExportHelper.BatchExportOutput) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = {
            if (state !is BatchExportUiState.Exporting) onDismiss()
        }
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .testTag("batch_export_progress_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (state) {
                    is BatchExportUiState.Exporting -> {
                        val total = state.total.coerceAtLeast(1)
                        val progress = (state.exported.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                        Text(
                            text = "Exportando em ${state.format.label}...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        LinearProgressIndicator(
                            progress = { progress },
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape)
                                .testTag("batch_export_progress_bar")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = state.currentTitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${state.exported} de $total",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        TextButton(
                            onClick = onCancel,
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Cancelar")
                        }
                    }

                    is BatchExportUiState.Completed -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Exportação concluída!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = buildString {
                                append("${state.output.exportedCount} item(ns) exportado(s) para ${state.output.fileName}.")
                                if (state.output.skippedCount > 0) {
                                    append("\n${state.output.skippedCount} arquivo(s) externo(s) inacessível(is) foram ignorados.")
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Fechar")
                            }
                            Button(
                                onClick = {
                                    onShare(state.output)
                                    onDismiss()
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Compartilhar")
                            }
                        }
                    }

                    is BatchExportUiState.Error -> {
                        Text(
                            text = "Falha na exportação",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.align(Alignment.End),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Fechar")
                        }
                    }

                    BatchExportUiState.Idle -> Unit
                }
            }
        }
    }
}

@Composable
private fun RecentExternalDocsPickerDialog(
    docType: NoteDocumentType,
    recentDocs: List<RecentExternalDocEntry>,
    onImportFromDevice: () -> Unit,
    onLoadEntries: (List<RecentExternalDocEntry>) -> Unit,
    onRemoveRecent: (RecentExternalDocEntry) -> Unit,
    onDismiss: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    var selectedUris by remember { mutableStateOf<Set<String>>(emptySet()) }
    val isPdf = docType == NoteDocumentType.PDF
    val accentColor = if (isPdf) PdfSheetAccentColor else DocxSheetAccentColor
    val titleText = if (isPdf) "Abrir ou Importar PDF" else "Abrir ou Importar Word (.docx)"

    val filteredDocs = remember(recentDocs, search) {
        val q = search.trim()
        if (q.isBlank()) recentDocs else {
            recentDocs.filter {
                it.title.contains(q, ignoreCase = true) ||
                    ExternalDocumentUriHelper.formatReadablePath(it.uri).contains(q, ignoreCase = true)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
                .padding(horizontal = 14.dp, vertical = 18.dp)
                .testTag("recent_external_docs_picker_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(accentColor.copy(alpha = 0.14f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPdf) Icons.Default.PictureAsPdf else Icons.Default.Article,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                // Top Long Button for System Multi-File Device Import
                Button(
                    onClick = onImportFromDevice,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .testTag("recent_picker_import_device_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isPdf) {
                            "Importar do celular (selecionar PDFs)"
                        } else {
                            "Importar do celular (selecionar .docx)"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }

                // Search bar for recent documents
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = {
                        Text(
                            text = if (isPdf) "Buscar PDFs recentes..." else "Buscar documentos Word recentes..."
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = if (search.isNotBlank()) {
                        {
                            IconButton(onClick = { search = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Limpar busca")
                            }
                        }
                    } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("recent_picker_search_input")
                )

                Text(
                    text = "Recentes (${filteredDocs.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (filteredDocs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isPdf) Icons.Default.PictureAsPdf else Icons.Default.Article,
                                contentDescription = null,
                                tint = accentColor.copy(alpha = 0.4f),
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = if (search.isNotBlank()) {
                                    "Nenhum arquivo encontrado para \"$search\"."
                                } else {
                                    "Nenhum arquivo recente deste tipo ainda.\nToque no botão acima para selecionar do celular."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredDocs, key = { it.uri }) { entry ->
                            val isChecked = entry.uri in selectedUris
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isChecked) {
                                    accentColor.copy(alpha = 0.12f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                },
                                border = if (isChecked) {
                                    androidx.compose.foundation.BorderStroke(1.5.dp, accentColor)
                                } else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        if (selectedUris.isNotEmpty()) {
                                            selectedUris = if (isChecked) selectedUris - entry.uri else selectedUris + entry.uri
                                        } else {
                                            onLoadEntries(listOf(entry))
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            selectedUris = if (checked) selectedUris + entry.uri else selectedUris - entry.uri
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = accentColor)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = entry.title.ifBlank { "Documento" },
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = ExternalDocumentUriHelper.formatReadablePath(entry.uri),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(
                                        onClick = { onRemoveRecent(entry) },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Remover dos recentes",
                                            tint = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (selectedUris.isNotEmpty()) {
                    val toLoad = filteredDocs.filter { it.uri in selectedUris }
                    Button(
                        onClick = { onLoadEntries(toLoad) },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("recent_picker_load_selected_btn")
                    ) {
                        Text(
                            text = "Carregar selecionado(s) (${toLoad.size})",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
