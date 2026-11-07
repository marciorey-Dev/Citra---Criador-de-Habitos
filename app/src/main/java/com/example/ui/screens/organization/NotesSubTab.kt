package com.example.ui.screens.organization

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import com.example.data.model.NoteVersion
import com.example.data.model.TaskPriority
import com.example.ui.MainViewModel
import com.example.ui.components.AddCategoryDialog
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.screens.organization.notes.*
import com.example.ui.theme.*
import com.example.ui.tutorial.tutorialTarget
import com.example.util.NoteExportHelper
import java.text.SimpleDateFormat
import java.util.*

enum class NoteSortOption(val label: String) {
    LAST_UPDATED("Última Atualização"),
    CREATION_DATE("Data de Criação"),
    ALPHABETICAL("Ordem A-Z"),
    CATEGORY("Empilhar por Categoria"),
    COLOR("Por Cor"),
    SIZE("Tamanho")
}

enum class NoteLayoutMode(val label: String) {
    GRID_2("Grade"),
    GRID_3("Grade Pequena"),
    DETAILS("Detalhes"),
    LIST("Lista"),
    STACKS("Pilhas")
}

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
    return remember(note.backgroundImageUri, note.content) {
        if (!note.backgroundImageUri.isNullOrBlank()) {
            note.backgroundImageUri
        } else {
            val match = Regex("!\\[(?!audio:)(.*?)\\]\\((.*?)\\)").find(note.content)
            match?.groupValues?.getOrNull(2)?.takeIf { it.isNotBlank() }
        }
    }
}

@Composable
private fun extractCleanExcerpt(content: String, maxChars: Int = 120): String {
    return remember(content, maxChars) {
        content
            .lines()
            .map { it.trim() }
            .filter { line ->
                line.isNotBlank() &&
                    !line.startsWith("---") &&
                    !line.startsWith("|") &&
                    !line.startsWith("```") &&
                    !line.startsWith("$$") &&
                    !line.startsWith("![audio:") &&
                    !line.startsWith("![")
            }
            .joinToString(" ") { line ->
                line.replace(Regex("!\\[audio:.*?\\]\\(.*?\\)"), "")
                    .replace(Regex("!\\[.*?\\]\\(.*?\\)"), "")
                    .replace(Regex("<.*?>"), "")
                    .replace(Regex("[#*`~=_]+"), "")
                    .replace(Regex("\\[(x| )\\]"), "")
            }
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(maxChars)
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
    val openCreateTrigger by viewModel.openCreateNoteTrigger.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var activeNoteToEdit by remember { mutableStateOf<Note?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }
    var noteToDelete by remember { mutableStateOf<Note?>(null) }
    var noteForSubmenu by remember { mutableStateOf<Note?>(null) }
    var showTrashDialog by remember { mutableStateOf(false) }
    var showAddFolderDialog by remember { mutableStateOf(false) }
    var currentSort by remember { mutableStateOf(NoteSortOption.LAST_UPDATED) }
    var currentLayout by remember { mutableStateOf(NoteLayoutMode.GRID_2) }
    var isGridStackedByCategory by rememberSaveable { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var expandedCategories by remember { mutableStateOf(setOf<String>()) }

    val importMultipleNotesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.importMultipleMarkdownFiles(uris, context)
        }
    }

    val noteToOpen by viewModel.noteToOpen.collectAsState()

    // Respond to Home screen "Nova Nota" shortcut trigger
    LaunchedEffect(openCreateTrigger) {
        if (openCreateTrigger) {
            isCreatingNew = true
            viewModel.resetCreateNoteTrigger()
        }
    }

    // Respond to notification / social click to open specific note
    LaunchedEffect(noteToOpen) {
        noteToOpen?.let { note ->
            activeNoteToEdit = note
            isCreatingNew = false
            viewModel.clearNoteToOpen()
        }
    }

    // Filter notes by search query AND category
    val filteredNotes = remember(notes, searchQuery, selectedCategoryFilter) {
        notes.filter { note ->
            val matchesCategory = (selectedCategoryFilter == "Todas" || note.category.equals(selectedCategoryFilter, ignoreCase = true))
            val matchesSearch = if (searchQuery.isBlank()) true else {
                note.title.contains(searchQuery, ignoreCase = true) ||
                    note.content.contains(searchQuery, ignoreCase = true) ||
                    note.category.contains(searchQuery, ignoreCase = true)
            }
            matchesCategory && matchesSearch
        }
    }

    // Sorted notes: Pinned notes ALWAYS stay at top unless unpinned
    val sortedNotes = remember(filteredNotes, currentSort) {
        filteredNotes.sortedWith(
            Comparator { a, b ->
                if (a.isPinned != b.isPinned) {
                    return@Comparator if (a.isPinned) -1 else 1
                }
                when (currentSort) {
                    NoteSortOption.LAST_UPDATED -> b.updatedAt.compareTo(a.updatedAt)
                    NoteSortOption.CREATION_DATE -> b.id.compareTo(a.id)
                    NoteSortOption.ALPHABETICAL -> a.title.compareTo(b.title, ignoreCase = true)
                    NoteSortOption.CATEGORY -> a.category.compareTo(b.category, ignoreCase = true)
                    NoteSortOption.COLOR -> (a.noteColor ?: "ZZZ").compareTo(b.noteColor ?: "ZZZ")
                    NoteSortOption.SIZE -> b.content.length.compareTo(a.content.length)
                }
            }
        )
    }

    val pomodoroState by viewModel.pomodoroState.collectAsState()
    val isEditingNote = isCreatingNew || activeNoteToEdit != null

    LaunchedEffect(isEditingNote) {
        viewModel.setNoteEditorOpen(isEditingNote)
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.setNoteEditorOpen(false)
        }
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
                onClose = {
                    isCreatingNew = false
                    activeNoteToEdit = null
                    viewModel.setNoteEditorOpen(false)
                },
                onSaveAndClose = { title, content, category, isPinned, bgUri, blur, brightness, nColor ->
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
                        noteColor = nColor
                    )
                    isCreatingNew = false
                    activeNoteToEdit = null
                    viewModel.setNoteEditorOpen(false)
                },
                onSaveOnly = { title, content, category, isPinned, bgUri, blur, brightness, nColor ->
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
                        noteColor = nColor,
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
                                noteColor = nColor
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
                    activeNoteToEdit = note
                    isCreatingNew = false
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
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
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
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium
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

                // Import Multiple Notes Circular Button
                Surface(
                    shape = CircleShape,
                    color = surfaceContainerLow,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable { importMultipleNotesLauncher.launch("*/*") }
                        .testTag("notes_import_multiple_btn")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.FileOpen,
                            contentDescription = "Importar Ficheiros Suportados (.md, .txt, .json, .html, .tex)",
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

            // 3. View & Sort Controls Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Sort Dropdown Pill
                Box {
                    Surface(
                        shape = CircleShape,
                        color = surfaceContainerLow,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { showSortMenu = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = currentSort.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Icon(
                                imageVector = Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        NoteSortOption.values().forEach { opt ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = opt.label,
                                        fontSize = 12.sp,
                                        fontWeight = if (opt == currentSort) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        when (opt) {
                                            NoteSortOption.LAST_UPDATED -> Icons.Default.Update
                                            NoteSortOption.CREATION_DATE -> Icons.Default.CalendarToday
                                            NoteSortOption.ALPHABETICAL -> Icons.Default.SortByAlpha
                                            NoteSortOption.CATEGORY -> Icons.Default.Layers
                                            NoteSortOption.COLOR -> Icons.Default.Palette
                                            NoteSortOption.SIZE -> Icons.Default.FormatSize
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (opt == currentSort) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    currentSort = opt
                                    if (opt == NoteSortOption.CATEGORY) {
                                        currentLayout = NoteLayoutMode.STACKS
                                    }
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }

                // Layout Toggle Pills inside Pill Container
                Surface(
                    shape = CircleShape,
                    color = surfaceContainerLow
                ) {
                    Row(
                        modifier = Modifier.padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NoteLayoutMode.values().forEach { mode ->
                            val isSelected = currentLayout == mode
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        CircleShape
                                    )
                                    .clickable { currentLayout = mode },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when (mode) {
                                        NoteLayoutMode.GRID_2 -> Icons.Default.GridView
                                        NoteLayoutMode.GRID_3 -> Icons.Default.ViewComfy
                                        NoteLayoutMode.DETAILS -> Icons.Default.ViewAgenda
                                        NoteLayoutMode.LIST -> Icons.Default.ViewList
                                        NoteLayoutMode.STACKS -> Icons.Default.Layers
                                    },
                                    contentDescription = mode.label,
                                    modifier = Modifier.size(17.dp),
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }

                if (currentLayout == NoteLayoutMode.GRID_2) {
                    Surface(
                        shape = CircleShape,
                        color = if (isGridStackedByCategory) MaterialTheme.colorScheme.primaryContainer else surfaceContainerLow,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { isGridStackedByCategory = !isGridStackedByCategory }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = "Empilhar categorias no modo grade",
                                modifier = Modifier.size(15.dp),
                                tint = if (isGridStackedByCategory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = "Empilhar",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isGridStackedByCategory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 92.dp),
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
                    } else if (isGridStackedByCategory) {
                        val categoriesWithNotes = sortedNotes.groupBy { it.category }
                        items(categoriesWithNotes.keys.toList(), key = { "grid_stack_$it" }, span = { GridItemSpan(maxLineSpan) }) { catName ->
                            val catNotes = categoriesWithNotes[catName] ?: emptyList()
                            CategoryNoteStack(
                                categoryName = catName,
                                notes = catNotes,
                                isExpanded = expandedCategories.contains(catName),
                                onToggleExpand = {
                                    expandedCategories = if (expandedCategories.contains(catName)) {
                                        expandedCategories - catName
                                    } else {
                                        expandedCategories + catName
                                    }
                                },
                                onNoteClick = { activeNoteToEdit = it },
                                onNoteLongClick = { noteForSubmenu = it },
                                onTogglePin = { viewModel.togglePinNote(it) },
                                onDeleteNote = { noteToDelete = it }
                            )
                        }
                    } else {
                        items(sortedNotes, key = { it.id }) { note ->
                            SquareNoteCard(
                                note = note,
                                onClick = { activeNoteToEdit = note },
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 92.dp),
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
                                onClick = { activeNoteToEdit = note },
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
            NoteLayoutMode.DETAILS -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 92.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("notes_details_list")
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
                            DetailNoteCard(
                                note = note,
                                onClick = { activeNoteToEdit = note },
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
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 92.dp),
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
                                onClick = { activeNoteToEdit = note },
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
            NoteLayoutMode.STACKS -> {
                val categoriesWithNotes = remember(sortedNotes) {
                    sortedNotes.groupBy { it.category }
                }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 92.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("notes_stacks_list")
                ) {
                    item {
                        topBarHeaderItem()
                    }
                    if (sortedNotes.isEmpty()) {
                        item {
                            emptyNotesItem()
                        }
                    } else {
                        items(categoriesWithNotes.keys.toList(), key = { it }) { catName ->
                            val catNotes = categoriesWithNotes[catName] ?: emptyList()
                            CategoryNoteStack(
                                categoryName = catName,
                                notes = catNotes,
                                isExpanded = expandedCategories.contains(catName),
                                onToggleExpand = {
                                    expandedCategories = if (expandedCategories.contains(catName)) {
                                        expandedCategories - catName
                                    } else {
                                        expandedCategories + catName
                                    }
                                },
                                onNoteClick = { activeNoteToEdit = it },
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

    // Confirmation dialog before deleting note
    if (noteToDelete != null) {
        ConfirmDeleteDialog(
            title = "Mover para a Lixeira",
            message = "Deseja mover a anotação '${noteToDelete!!.title}' para a lixeira? Ela ficará disponível por 30 dias para restauração.",
            confirmButtonText = "Mover para Lixeira",
            onConfirm = {
                viewModel.moveNoteToTrash(noteToDelete!!)
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
                    backgroundBrightness = note.backgroundBrightness
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
 * Warm Tactile 2-Column Card for Notes
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
    val isDark = isSystemInDarkTheme()
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
    val cleanExcerpt = extractCleanExcerpt(note.content, maxChars = 200)

    val badgeBg = if (note.noteColor != null) {
        paperColors.badgeBg
    } else when (note.category.lowercase().trim()) {
        "criatividade", "ideias" -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
        "estudos", "study" -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f)
        "logística", "finanças", "trabalho", "work" -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val badgeFg = if (note.noteColor != null) {
        paperColors.badgeFg
    } else when (note.category.lowercase().trim()) {
        "criatividade", "ideias" -> MaterialTheme.colorScheme.onPrimaryContainer
        "estudos", "study" -> MaterialTheme.colorScheme.onSecondaryContainer
        "logística", "finanças", "trabalho", "work" -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    PaperNoteSurface(
        cornerRadius = 16.dp,
        foldSize = 22.dp,
        paperColors = paperColors,
        isPinned = note.isPinned,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 250.dp)
            .combinedClickable(
                onClick = { onClick() },
                onLongClick = { onLongClick?.invoke() }
            )
            .testTag("note_square_card_${note.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 10.dp, start = 12.dp, end = 24.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Top row: Category Pill + Pin + More
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
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

                // Embedded Image Preview (like Card 5 in HTML)
                if (previewImageUri != null) {
                    AsyncImage(
                        model = previewImageUri,
                        contentDescription = note.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(88.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }

                // Note Title
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = paperColors.onBackgroundColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Note Excerpt
                if (cleanExcerpt.isNotBlank()) {
                    Text(
                        text = cleanExcerpt,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = paperColors.onBackgroundColor.copy(alpha = 0.88f),
                        maxLines = if (previewImageUri != null) 4 else 6,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Bottom row: Schedule Date + Media Badges / Pinned Dot
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
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
                        tint = paperColors.onBackgroundColor.copy(alpha = 0.85f),
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = dateText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = paperColors.onBackgroundColor.copy(alpha = 0.95f)
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
                            tint = MaterialTheme.colorScheme.tertiary,
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
                    if (note.isPinned) {
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
    val isDark = isSystemInDarkTheme()
    val paperColors = remember(note.noteColor, isDark, note.isPinned) {
        PaperNotePalette.resolvePaperColors(note.noteColor, isDark, note.isPinned)
    }

    PaperNoteSurface(
        cornerRadius = 14.dp,
        foldSize = 18.dp,
        paperColors = paperColors,
        isPinned = note.isPinned,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.9f)
            .combinedClickable(
                onClick = { onClick() },
                onLongClick = { onLongClick?.invoke() }
            )
            .testTag("note_small_card_${note.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp, start = 8.dp, end = 20.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = CircleShape,
                    color = paperColors.badgeBg
                ) {
                    Text(
                        text = note.category.take(6),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = paperColors.badgeFg,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                if (note.isPinned) {
                    Icon(
                        imageVector = Icons.Filled.PushPin,
                        contentDescription = "Fixada",
                        tint = paperColors.accentColor,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            Text(
                text = note.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = paperColors.onBackgroundColor,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 14.sp
            )

            Text(
                text = SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(note.updatedAt)),
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = paperColors.onBackgroundColor.copy(alpha = 0.95f)
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
    val cleanExcerpt = extractCleanExcerpt(note.content, maxChars = 180)
    val isDark = isSystemInDarkTheme()
    val paperColors = remember(note.noteColor, isDark, note.isPinned) {
        PaperNotePalette.resolvePaperColors(note.noteColor, isDark, note.isPinned)
    }

    PaperNoteSurface(
        cornerRadius = 16.dp,
        foldSize = 22.dp,
        paperColors = paperColors,
        isPinned = note.isPinned,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onClick() },
                onLongClick = { onLongClick?.invoke() }
            )
            .testTag("note_detail_card_${note.id}")
    ) {
        Column(modifier = Modifier.padding(top = 10.dp, start = 14.dp, end = 24.dp, bottom = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(note.updatedAt)),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = paperColors.onBackgroundColor.copy(alpha = 0.95f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(onClick = onTogglePin, modifier = Modifier.size(26.dp)) {
                        Icon(
                            imageVector = if (note.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = if (note.isPinned) "Desafixar" else "Fixar",
                            tint = if (note.isPinned) paperColors.accentColor else MaterialTheme.colorScheme.outline,
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
                color = paperColors.onBackgroundColor
            )

            if (cleanExcerpt.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = cleanExcerpt,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
                    color = paperColors.onBackgroundColor.copy(alpha = 0.75f),
                    lineHeight = 17.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
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
    val isDark = isSystemInDarkTheme()
    val paperColors = remember(note.noteColor, isDark, note.isPinned) {
        PaperNotePalette.resolvePaperColors(note.noteColor, isDark, note.isPinned)
    }

    PaperNoteSurface(
        cornerRadius = 14.dp,
        foldSize = 16.dp,
        paperColors = paperColors,
        isPinned = note.isPinned,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onClick() },
                onLongClick = { onLongClick?.invoke() }
            )
            .testTag("note_list_card_${note.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
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

                Text(
                    text = note.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = paperColors.onBackgroundColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onTogglePin, modifier = Modifier.size(26.dp)) {
                    Icon(
                        imageVector = if (note.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = if (note.isPinned) "Desafixar" else "Fixar",
                        tint = if (note.isPinned) paperColors.accentColor else MaterialTheme.colorScheme.outline,
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
    onDuplicate: () -> Unit
) {
    val context = LocalContext.current
    val primaryColor = MaterialTheme.colorScheme.primary

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = primaryColor,
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
                        Text(if (note.isPinned) "Desafixar nota" else "Fixar nota no topo", style = MaterialTheme.typography.bodyMedium)
                    }
                }

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

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

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
                        Text("Mover para a Lixeira", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
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
