package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.auth.AuthRepository
import com.example.data.backup.BackupManager
import com.example.data.backup.BackupParsedData
import com.example.data.model.Habit
import com.example.data.model.Note
import com.example.data.model.Task
import com.example.ui.theme.GreenAccent
import com.example.ui.theme.OrangeContainer
import com.example.ui.theme.RedAction
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudSyncedDataDialog(
    authRepository: AuthRepository,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(true) }
    var notesList by remember { mutableStateOf<List<Note>>(emptyList()) }
    var tasksList by remember { mutableStateOf<List<Task>>(emptyList()) }
    var habitsList by remember { mutableStateOf<List<Habit>>(emptyList()) }
    var hasCloudBackup by remember { mutableStateOf(false) }

    var selectedTab by remember { mutableIntStateOf(0) }
    var noteToViewReadOnly by remember { mutableStateOf<Note?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }

    fun loadData() {
        isLoading = true
        coroutineScope.launch {
            val cloudData = authRepository.fetchCloudUserData()
            if (cloudData != null && (cloudData.backupJson != null || cloudData.notesJson != null)) {
                hasCloudBackup = true
                notesList = cloudData.notesJson?.let {
                    try {
                        BackupManager.parseNotesJson(it)
                    } catch (_: Exception) {
                        emptyList()
                    }
                } ?: emptyList()

                val backupParsed: BackupParsedData? = cloudData.backupJson?.let {
                    try {
                        BackupManager.parseBackupJson(it)
                    } catch (_: Exception) {
                        null
                    }
                }
                tasksList = backupParsed?.tasks ?: emptyList()
                habitsList = backupParsed?.habits ?: emptyList()
            } else {
                hasCloudBackup = false
                notesList = emptyList()
                tasksList = emptyList()
                habitsList = emptyList()
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadData()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CloudQueue,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Dados Salvos na Nuvem",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                Text(
                    text = "Consulte os itens armazenados no Firestore e veja suas notas no modo somente leitura.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    }
                } else if (!hasCloudBackup) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("☁️", fontSize = 42.sp)
                            Text(
                                text = "Nenhum backup em nuvem encontrado.",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Faça login com sua conta e toque em 'Sincronizar' para salvar suas notas, tarefas e hábitos.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    }
                } else {
                    // Action button to delete cloud backup
                    OutlinedButton(
                        onClick = { showDeleteConfirmDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RedAction),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Apagar Backup da Nuvem", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Tab bar
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)),
                        divider = {}
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Notas (${notesList.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Tarefas (${tasksList.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            text = { Text("Hábitos (${habitsList.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Content list based on tab
                    Box(modifier = Modifier.weight(1f)) {
                        when (selectedTab) {
                            0 -> {
                                if (notesList.isEmpty()) {
                                    EmptyTabMessage("Nenhuma anotação gravada no backup da nuvem.")
                                } else {
                                    LazyColumn(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        items(notesList) { note ->
                                            Card(
                                                shape = RoundedCornerShape(14.dp),
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { noteToViewReadOnly = note }
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Text(note.iconEmoji.ifBlank { "📝" }, fontSize = 20.sp)
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                        Column {
                                                            Text(
                                                                text = note.title.ifBlank { "Sem título" },
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 13.sp,
                                                                maxLines = 1
                                                            )
                                                            Surface(
                                                                shape = RoundedCornerShape(6.dp),
                                                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                                                modifier = Modifier.padding(top = 4.dp)
                                                            ) {
                                                                Text(
                                                                    text = note.category,
                                                                    fontSize = 10.sp,
                                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                )
                                                            }
                                                        }
                                                    }

                                                    FilledTonalButton(
                                                        onClick = { noteToViewReadOnly = note },
                                                        shape = RoundedCornerShape(10.dp),
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                                    ) {
                                                        Icon(Icons.AutoMirrored.Filled.Article, contentDescription = null, modifier = Modifier.size(14.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Ler", fontSize = 11.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            1 -> {
                                if (tasksList.isEmpty()) {
                                    EmptyTabMessage("Nenhuma tarefa gravada no backup da nuvem.")
                                } else {
                                    LazyColumn(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        items(tasksList) { task ->
                                            Card(
                                                shape = RoundedCornerShape(14.dp),
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = if (task.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                                        contentDescription = null,
                                                        tint = if (task.isCompleted) GreenAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = task.title,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 13.sp,
                                                            color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                                        )
                                                        Row(
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                            modifier = Modifier.padding(top = 4.dp)
                                                        ) {
                                                            Surface(
                                                                shape = RoundedCornerShape(6.dp),
                                                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                                                            ) {
                                                                Text(
                                                                    text = task.category,
                                                                    fontSize = 10.sp,
                                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                )
                                                            }
                                                            Surface(
                                                                shape = RoundedCornerShape(6.dp),
                                                                color = MaterialTheme.colorScheme.surfaceVariant
                                                            ) {
                                                                Text(
                                                                    text = task.priority.name,
                                                                    fontSize = 10.sp,
                                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
                            2 -> {
                                if (habitsList.isEmpty()) {
                                    EmptyTabMessage("Nenhum hábito gravado no backup da nuvem.")
                                } else {
                                    LazyColumn(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        items(habitsList) { habit ->
                                            Card(
                                                shape = RoundedCornerShape(14.dp),
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(36.dp)
                                                            .background(OrangeContainer, CircleShape),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(habit.iconEmoji, fontSize = 18.sp)
                                                    }
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(habit.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Text(
                                                            text = "Categoria: ${habit.category} • Horário: ${habit.reminderTime} • Sequência: ${habit.currentStreak} dias",
                                                            fontSize = 11.sp,
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
                }
            }
        }
    }

    // Read-only note preview dialog
    if (noteToViewReadOnly != null) {
        val note = noteToViewReadOnly!!
        Dialog(
            onDismissRequest = { noteToViewReadOnly = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(note.iconEmoji.ifBlank { "📝" }, fontSize = 24.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = note.title.ifBlank { "Sem título" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "📖 Modo Leitura (Nuvem) • Categoria: ${note.category}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        IconButton(onClick = { noteToViewReadOnly = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Fechar")
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (note.content.isBlank()) {
                            Text(
                                text = "Esta anotação está vazia.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            MarkdownViewer(markdownText = note.content)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { noteToViewReadOnly = null },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Fechar Leitura")
                    }
                }
            }
        }
    }

    // Confirmation dialog before deleting cloud backup
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteConfirmDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WarningAmber, contentDescription = null, tint = RedAction)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Apagar Backup da Nuvem?", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    text = "Aviso de Confirmação:\n\nEsta ação vai apagar permanentemente todas as anotações, tarefas e hábitos salvos no banco de dados na nuvem (Firestore).\n\nOs seus dados locais neste celular não serão excluídos.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        isDeleting = true
                        coroutineScope.launch {
                            val success = authRepository.deleteCloudBackup()
                            isDeleting = false
                            showDeleteConfirmDialog = false
                            if (success) {
                                Toast.makeText(context, "Backup da nuvem apagado com sucesso!", Toast.LENGTH_SHORT).show()
                                loadData()
                            } else {
                                Toast.makeText(context, "Falha ao apagar o backup da nuvem.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedAction),
                    enabled = !isDeleting,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(if (isDeleting) "Apagando..." else "Sim, Apagar Backup da Nuvem")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeleteConfirmDialog = false },
                    enabled = !isDeleting,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Cancelar")
                }
            },
            shape = RoundedCornerShape(18.dp)
        )
    }
}

@Composable
private fun EmptyTabMessage(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
