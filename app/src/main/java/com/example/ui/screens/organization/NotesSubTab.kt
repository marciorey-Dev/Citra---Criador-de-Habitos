package com.example.ui.screens.organization

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.Category
import com.example.data.model.Note
import com.example.data.model.TaskPriority
import com.example.ui.MainViewModel
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.screens.organization.notes.FullScreenNoteEditor
import com.example.ui.screens.organization.notes.NoteEditorMode
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

enum class NoteSortOption(val label: String) {
    LAST_UPDATED("Última Atualização"),
    CREATION_DATE("Data de Criação"),
    ALPHABETICAL("Ordem A-Z"),
    CATEGORY("Categoria"),
    SIZE("Tamanho")
}

enum class NoteLayoutMode(val label: String) {
    GRID_2("Grade"),
    GRID_3("Grade Pequena"),
    DETAILS("Detalhes"),
    LIST("Lista")
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
    var showTrashDialog by remember { mutableStateOf(false) }
    var currentSort by remember { mutableStateOf(NoteSortOption.LAST_UPDATED) }
    var currentLayout by remember { mutableStateOf(NoteLayoutMode.GRID_2) }
    var showSortMenu by remember { mutableStateOf(false) }

    // Respond to Home screen "Nova Nota" shortcut trigger
    LaunchedEffect(openCreateTrigger) {
        if (openCreateTrigger) {
            isCreatingNew = true
            viewModel.resetCreateNoteTrigger()
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

    // Sorted notes: Pinned notes ALWAYS stay at top unless unpinned!
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
            onClose = {
                isCreatingNew = false
                activeNoteToEdit = null
                viewModel.setNoteEditorOpen(false)
            },
            onSaveAndClose = { title, content, category, isPinned, bgUri, blur, brightness ->
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
                    backgroundBrightness = brightness
                )
                isCreatingNew = false
                activeNoteToEdit = null
                viewModel.setNoteEditorOpen(false)
            },
            onSaveOnly = { title, content, category, isPinned, bgUri, blur, brightness ->
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
                    onSaved = { savedId ->
                        if (activeNoteToEdit == null || activeNoteToEdit?.id == 0L) {
                            activeNoteToEdit = Note(
                                id = savedId,
                                title = title,
                                content = content,
                                category = category,
                                iconEmoji = "📝",
                                isPinned = isPinned,
                                backgroundImageUri = bgUri,
                                backgroundBlur = blur,
                                backgroundBrightness = brightness
                            )
                        }
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
            initialMode = if (viewModel.getLastNoteEditorMode() == "READING") NoteEditorMode.READING else NoteEditorMode.LIVE_PREVIEW,
            onModeChanged = { mode ->
                viewModel.setLastNoteEditorMode(mode.name)
            },
            onToggleChecklistTaskSync = { checklistText, isChecked ->
                viewModel.syncChecklistToTask(checklistText, isChecked)
            },
            onConvertChecklistToDirectTask = { taskTitle, taskCategory, taskPriority, dueDate ->
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
                    dueTimestamp = null,
                    reminderMinutesBefore = 0
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
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Search Bar + Trash Shortcut
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Pesquisar anotações...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Pesquisar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Limpar")
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("notes_search_input")
                )

                Spacer(modifier = Modifier.width(8.dp))

                BadgedBox(
                    badge = {
                        if (trashNotes.isNotEmpty()) {
                            Badge(containerColor = RedAction) { Text("${trashNotes.size}") }
                        }
                    }
                ) {
                    IconButton(
                        onClick = { showTrashDialog = true },
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                if (trashNotes.isNotEmpty()) RedContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(14.dp)
                            )
                            .testTag("notes_trash_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Lixeira (30 dias)",
                            tint = if (trashNotes.isNotEmpty()) RedAction else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Category Filter Chips with minimalist icons
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = selectedCategoryFilter == "Todas",
                        onClick = { viewModel.setSelectedNoteCategoryFilter("Todas") },
                        label = { Text("Todas (${notes.size})", fontSize = 11.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    )
                }
                items(categories) { cat ->
                    val count = notes.count { it.category.equals(cat.name, ignoreCase = true) }
                    FilterChip(
                        selected = selectedCategoryFilter == cat.name,
                        onClick = { viewModel.setSelectedNoteCategoryFilter(cat.name) },
                        label = { Text("${cat.name} ($count)", fontSize = 11.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Label, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sort and Layout Selection Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Sort selector
                Box {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.clickable { showSortMenu = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(currentSort.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        NoteSortOption.values().forEach { opt ->
                            DropdownMenuItem(
                                text = { Text(opt.label, fontSize = 12.sp, fontWeight = if (opt == currentSort) FontWeight.Bold else FontWeight.Normal) },
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
                                        tint = if (opt == currentSort) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    currentSort = opt
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }

                // Layout switcher
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NoteLayoutMode.values().forEach { mode ->
                        val isSelected = currentLayout == mode
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier
                                .size(32.dp)
                                .clickable { currentLayout = mode }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = when (mode) {
                                        NoteLayoutMode.GRID_2 -> Icons.Default.GridView
                                        NoteLayoutMode.GRID_3 -> Icons.Default.ViewComfy
                                        NoteLayoutMode.DETAILS -> Icons.Default.ViewAgenda
                                        NoteLayoutMode.LIST -> Icons.Default.ViewList
                                    },
                                    contentDescription = mode.label,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (sortedNotes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
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
                            text = if (searchQuery.isNotEmpty()) "Nenhuma anotação nesta busca." else "Nenhuma anotação nesta categoria.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Toque no '+' para criar sua primeira nota com Markdown Suave.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                when (currentLayout) {
                    NoteLayoutMode.GRID_2 -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 88.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .testTag("notes_grid")
                        ) {
                            items(sortedNotes, key = { it.id }) { note ->
                                SquareNoteCard(
                                    note = note,
                                    onClick = { activeNoteToEdit = note },
                                    onTogglePin = { viewModel.togglePinNote(note) },
                                    onDelete = { noteToDelete = note }
                                )
                            }
                        }
                    }
                    NoteLayoutMode.GRID_3 -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 88.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .testTag("notes_grid_small")
                        ) {
                            items(sortedNotes, key = { it.id }) { note ->
                                SmallSquareNoteCard(
                                    note = note,
                                    onClick = { activeNoteToEdit = note },
                                    onTogglePin = { viewModel.togglePinNote(note) },
                                    onDelete = { noteToDelete = note }
                                )
                            }
                        }
                    }
                    NoteLayoutMode.DETAILS -> {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 88.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .testTag("notes_details_list")
                        ) {
                            items(sortedNotes, key = { it.id }) { note ->
                                DetailNoteCard(
                                    note = note,
                                    onClick = { activeNoteToEdit = note },
                                    onTogglePin = { viewModel.togglePinNote(note) },
                                    onDelete = { noteToDelete = note }
                                )
                            }
                        }
                    }
                    NoteLayoutMode.LIST -> {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(bottom = 88.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .testTag("notes_simple_list")
                        ) {
                            items(sortedNotes, key = { it.id }) { note ->
                                ListNoteCard(
                                    note = note,
                                    onClick = { activeNoteToEdit = note },
                                    onTogglePin = { viewModel.togglePinNote(note) },
                                    onDelete = { noteToDelete = note }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Floating Action Button to create new note
        FloatingActionButton(
            onClick = { isCreatingNew = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("add_note_fab")
        ) {
            Icon(Icons.Default.Add, contentDescription = "Nova Anotação")
        }
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

    // Trash Dialog
    if (showTrashDialog) {
        NotesTrashDialog(
            trashNotes = trashNotes,
            onRestore = { viewModel.restoreNoteFromTrash(it) },
            onPermanentDelete = { viewModel.permanentlyDeleteNote(it) },
            onDismiss = { showTrashDialog = false }
        )
    }
}

/**
 * Square 2-Column Card for Notes with Minimalist Icons
 */
@Composable
fun SquareNoteCard(
    note: Note,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    val dateText = remember(note.updatedAt) {
        SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(note.updatedAt))
    }

    val hasAudio = remember(note.content) {
        note.content.contains("![audio:") || note.content.contains("audio:")
    }
    val hasImage = remember(note.content, note.backgroundImageUri) {
        note.backgroundImageUri != null || Regex("!\\[(.*?)\\]\\(.*?\\)").containsMatchIn(note.content)
    }

    // Strip raw markdown symbols for a clean excerpt without exposing distorted image or audio raw markdown
    val cleanExcerpt = remember(note.content) {
        note.content
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
            .take(120)
    }

    val categoryColor = when (note.category.lowercase()) {
        "trabalho", "work" -> OrangePrimary
        "estudos", "study" -> BlueAccent
        "saúde", "fitness" -> GreenAccent
        "ideias", "criatividade" -> PurpleAccent
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (note.isPinned) categoryColor.copy(alpha = 0.08f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (note.isPinned) 3.dp else 1.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.95f)
            .border(
                width = if (note.isPinned) 2.5.dp else 1.dp,
                color = if (note.isPinned) categoryColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable { onClick() }
            .testTag("note_square_card_${note.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top row: Minimalist Category tag + Pin / Menu button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (note.isPinned) categoryColor.copy(alpha = 0.16f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Label,
                            contentDescription = null,
                            tint = if (note.isPinned) categoryColor else MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = note.category,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (note.isPinned) categoryColor else MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onTogglePin,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = if (note.isPinned) "Desafixar" else "Fixar",
                            tint = if (note.isPinned) categoryColor else MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Excluir",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Center: Note Title and Excerpt
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.Top
            ) {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (cleanExcerpt.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = cleanExcerpt,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 15.sp
                    )
                }
            }

            // Bottom row: Date & Media Badges (Image / Audio)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = dateText,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasImage) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = "Contém Imagem",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    if (hasAudio) {
                        Icon(
                            imageVector = Icons.Default.Audiotrack,
                            contentDescription = "Contém Áudio",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(14.dp)
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
@Composable
fun SmallSquareNoteCard(
    note: Note,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    val categoryColor = when (note.category.lowercase()) {
        "trabalho", "work" -> OrangePrimary
        "estudos", "study" -> BlueAccent
        "saúde", "fitness" -> GreenAccent
        "ideias", "criatividade" -> PurpleAccent
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (note.isPinned) categoryColor.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (note.isPinned) 2.5.dp else 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.9f)
            .border(
                width = if (note.isPinned) 2.dp else 1.dp,
                color = if (note.isPinned) categoryColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
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
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (note.isPinned) categoryColor.copy(alpha = 0.16f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = note.category.take(6),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (note.isPinned) categoryColor else MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }

                if (note.isPinned) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "Fixada",
                        tint = categoryColor,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            Text(
                text = note.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 14.sp
            )

            Text(
                text = SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(note.updatedAt)),
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

/**
 * Details Layout Card (Title, Category, First Paragraph Clean Excerpt, and Full Actions)
 */
@Composable
fun DetailNoteCard(
    note: Note,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    val categoryColor = when (note.category.lowercase()) {
        "trabalho", "work" -> OrangePrimary
        "estudos", "study" -> BlueAccent
        "saúde", "fitness" -> GreenAccent
        "ideias", "criatividade" -> PurpleAccent
        else -> MaterialTheme.colorScheme.primary
    }

    val cleanExcerpt = remember(note.content) {
        note.content
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
            .take(180)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (note.isPinned) categoryColor.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (note.isPinned) 2.5.dp else 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (note.isPinned) 2.5.dp else 1.dp,
                color = if (note.isPinned) categoryColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (note.isPinned) categoryColor.copy(alpha = 0.16f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = note.category,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (note.isPinned) categoryColor else MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(note.updatedAt)),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(onClick = onTogglePin, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = if (note.isPinned) "Desafixar" else "Fixar",
                            tint = if (note.isPinned) categoryColor else MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Excluir",
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

            if (cleanExcerpt.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = cleanExcerpt,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 17.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * List Layout Card (Titles Only - compact and efficient)
 */
@Composable
fun ListNoteCard(
    note: Note,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    val categoryColor = when (note.category.lowercase()) {
        "trabalho", "work" -> OrangePrimary
        "estudos", "study" -> BlueAccent
        "saúde", "fitness" -> GreenAccent
        "ideias", "criatividade" -> PurpleAccent
        else -> MaterialTheme.colorScheme.primary
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (note.isPinned) categoryColor.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (note.isPinned) 2.dp else 1.dp,
                color = if (note.isPinned) categoryColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (note.isPinned) categoryColor.copy(alpha = 0.16f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = note.category.take(8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (note.isPinned) categoryColor else MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = note.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onTogglePin, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = if (note.isPinned) "Desafixar" else "Fixar",
                        tint = if (note.isPinned) categoryColor else MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Excluir",
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
    onPermanentDelete: (Note) -> Unit,
    onDismiss: () -> Unit
) {
    var noteToPermanentlyDelete by remember { mutableStateOf<Note?>(null) }

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
                            tint = RedAction,
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
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
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
                                                tint = GreenAccent
                                            )
                                        }

                                        IconButton(
                                            onClick = { noteToPermanentlyDelete = note },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteForever,
                                                contentDescription = "Excluir Definitivamente",
                                                tint = RedAction
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
