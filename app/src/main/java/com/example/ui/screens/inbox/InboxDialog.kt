package com.example.ui.screens.inbox

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.model.InboxMessage
import com.example.ui.MainViewModel
import com.example.ui.components.MarkdownViewer
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxDialog(
    viewModel: MainViewModel,
    onOpenAuthGate: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val currentUser by viewModel.authRepository.currentUser.collectAsState()
    val isUserLoggedIn = currentUser != null && currentUser?.isGuest == false

    val receivedMessages by viewModel.inboxMessages.collectAsState()
    val sentMessages by viewModel.sentMessages.collectAsState()
    val isRefreshing by viewModel.isInboxRefreshing.collectAsState()
    val unreadCount by viewModel.unreadInboxCount.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Recebidas, 1 = Enviadas
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedMessageIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    var showSendDialog by remember { mutableStateOf(false) }
    var showBugReportDialog by remember { mutableStateOf(false) }
    var readingPair by remember { mutableStateOf<Pair<InboxMessage, Boolean>?>(null) } // message to isSentView
    var showClearConfirmation by remember { mutableStateOf(false) }

    val activeMessages = if (selectedTab == 0) receivedMessages else sentMessages

    LaunchedEffect(selectedTab) {
        isSelectionMode = false
        selectedMessageIds = emptySet()
    }

    LaunchedEffect(isUserLoggedIn) {
        if (isUserLoggedIn) {
            viewModel.refreshInbox()
        }
    }

    val containerBg = if (isUserLoggedIn) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surfaceVariant
    val headerBg = if (isUserLoggedIn) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = containerBg,
            tonalElevation = if (isUserLoggedIn) 6.dp else 0.dp,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Header Bar
                Surface(
                    color = headerBg,
                    tonalElevation = if (isUserLoggedIn) 2.dp else 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (isUserLoggedIn) MaterialTheme.colorScheme.primaryContainer else Color.Gray.copy(alpha = 0.22f),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isUserLoggedIn) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                                        contentDescription = null,
                                        tint = if (isUserLoggedIn) MaterialTheme.colorScheme.primary else Color.Gray,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Caixa de Entrada",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = if (isUserLoggedIn) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (isUserLoggedIn && unreadCount > 0) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.primary
                                        ) {
                                            Text(
                                                text = "$unreadCount nova${if (unreadCount > 1) "s" else ""}",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = if (isUserLoggedIn) "Notas recebidas, enviadas e suporte" else "Disponível apenas para contas conectadas",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val infiniteTransition = rememberInfiniteTransition(label = "refresh_rot")
                            val angle by infiniteTransition.animateFloat(
                                initialValue = 0f,
                                targetValue = 360f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(800, easing = LinearEasing),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "angle"
                            )
                            IconButton(
                                onClick = { viewModel.refreshInbox() },
                                enabled = isUserLoggedIn && !isRefreshing
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Atualizar",
                                    tint = if (isUserLoggedIn) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f),
                                    modifier = if (isRefreshing && isUserLoggedIn) Modifier.rotate(angle) else Modifier
                                )
                            }

                            if (isUserLoggedIn && activeMessages.isNotEmpty()) {
                                IconButton(
                                    onClick = { showClearConfirmation = true },
                                    modifier = Modifier.testTag("inbox_empty_box_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteSweep,
                                        contentDescription = "Esvaziar caixa",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Fechar",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Segmented Tabs: Recebidas / Enviadas
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isUserLoggedIn) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f) else Color.Gray.copy(alpha = 0.16f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val tabs = listOf(
                            0 to "Recebidas (${if (isUserLoggedIn) receivedMessages.size else 0})",
                            1 to "Enviadas (${if (isUserLoggedIn) sentMessages.size else 0})"
                        )
                        tabs.forEach { (idx, label) ->
                            val isSelected = selectedTab == idx
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected && isUserLoggedIn) MaterialTheme.colorScheme.surface else if (isSelected) MaterialTheme.colorScheme.surface.copy(alpha = 0.5f) else Color.Transparent,
                                shadowElevation = if (isSelected && isUserLoggedIn) 2.dp else 0.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable(enabled = isUserLoggedIn) { selectedTab = idx }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 12.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (!isUserLoggedIn) {
                                            Color.Gray
                                        } else if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Action Bar: Send Note, Bug Report, Multi-Select Controls
                if (!isUserLoggedIn) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .alpha(0.55f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {},
                            enabled = false,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Relatar Bug", fontSize = 12.sp)
                        }
                        Button(
                            onClick = {},
                            enabled = false,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Enviar Nota", fontSize = 12.sp)
                        }
                    }

                    // Gray Locked State for Unauthenticated / Guest Users
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                            border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Gray.copy(alpha = 0.2f),
                                    modifier = Modifier.size(70.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = Color.Gray,
                                            modifier = Modifier.size(34.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Caixa de Entrada Bloqueada",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Usuários sem conta logada não podem receber, visualizar ou enviar notas e nem enviar relatos pela Caixa de Entrada.",
                                    fontSize = 13.sp,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 18.sp
                                )
                                Button(
                                    onClick = onOpenAuthGate,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Entrar com Conta Google", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    // Authenticated Actions Bar
                    if (isSelectionMode) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(
                                        onClick = {
                                            selectedMessageIds = if (selectedMessageIds.size == activeMessages.size) {
                                                emptySet()
                                            } else {
                                                activeMessages.map { it.id }.toSet()
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Text(
                                            text = if (selectedMessageIds.size == activeMessages.size && activeMessages.isNotEmpty()) "Desmarcar" else "Selecionar tudo",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = "${selectedMessageIds.size} selec.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    TextButton(
                                        onClick = {
                                            isSelectionMode = false
                                            selectedMessageIds = emptySet()
                                        }
                                    ) {
                                        Text("Cancelar", fontSize = 12.sp)
                                    }
                                    Button(
                                        onClick = {
                                            val toDelete = activeMessages.filter { it.id in selectedMessageIds }
                                            if (selectedTab == 0) {
                                                viewModel.deleteSelectedInboxMessages(toDelete)
                                            } else {
                                                viewModel.deleteSelectedSentMessages(toDelete)
                                            }
                                            isSelectionMode = false
                                            selectedMessageIds = emptySet()
                                        },
                                        enabled = selectedMessageIds.isNotEmpty(),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Apagar (${selectedMessageIds.size})", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (activeMessages.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = { isSelectionMode = true },
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("inbox_select_mode_btn")
                                ) {
                                    Icon(Icons.Default.Checklist, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Selecionar", fontSize = 12.sp)
                                }
                            }

                            OutlinedButton(
                                onClick = { showBugReportDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("inbox_report_bug_btn")
                            ) {
                                Icon(Icons.Default.BugReport, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Relatar Bug", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }

                            Button(
                                onClick = { showSendDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("inbox_send_note_btn")
                            ) {
                                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Enviar Nota", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }

                    // Messages List or Empty State
                    if (activeMessages.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.size(80.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(if (selectedTab == 0) "📭" else "📤", fontSize = 38.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = if (selectedTab == 0) "Sua caixa está limpa" else "Nenhuma mensagem enviada",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (selectedTab == 0) {
                                        "Nenhuma nota ou comunicado recebido no momento.\nToque abaixo para enviar uma nota ou relatar um bug!"
                                    } else {
                                        "As notas enviadas por você e seus relatos de bug aparecerão aqui."
                                    },
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedButton(
                                        onClick = { showBugReportDialog = true },
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Icon(Icons.Default.BugReport, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Relatar Bug")
                                    }
                                    FilledTonalButton(
                                        onClick = { showSendDialog = true },
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Enviar Nota")
                                    }
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(activeMessages, key = { "${selectedTab}_${it.id}" }) { message ->
                                val isSentTab = selectedTab == 1
                                val isChecked = message.id in selectedMessageIds
                                InboxItemCard(
                                    message = message,
                                    isSentView = isSentTab,
                                    isSelectionMode = isSelectionMode,
                                    isSelected = isChecked,
                                    onToggleSelect = {
                                        selectedMessageIds = if (isChecked) {
                                            selectedMessageIds - message.id
                                        } else {
                                            selectedMessageIds + message.id
                                        }
                                    },
                                    onRead = {
                                        if (isSelectionMode) {
                                            selectedMessageIds = if (isChecked) {
                                                selectedMessageIds - message.id
                                            } else {
                                                selectedMessageIds + message.id
                                            }
                                        } else {
                                            if (!isSentTab) viewModel.markInboxMessageRead(message)
                                            readingPair = message to isSentTab
                                        }
                                    },
                                    onDownload = {
                                        viewModel.downloadInboxNote(message)
                                    },
                                    onDelete = {
                                        if (isSentTab) {
                                            viewModel.deleteSentMessage(message)
                                        } else {
                                            viewModel.deleteInboxMessage(message)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Clear Inbox / Sent Confirmation Dialog
    if (showClearConfirmation && isUserLoggedIn) {
        val isSentTab = selectedTab == 1
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(if (isSentTab) "Esvaziar Enviadas?" else "Esvaziar Caixa de Entrada?") },
            text = {
                Text(
                    if (isSentTab) "Deseja apagar todo o histórico de mensagens enviadas? Esta ação não pode ser desfeita."
                    else "Deseja apagar todas as mensagens da sua caixa de entrada? Esta ação não pode ser desfeita."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isSentTab) viewModel.clearSentMessages() else viewModel.clearInbox()
                        showClearConfirmation = false
                    }
                ) {
                    Text("Esvaziar Tudo")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Send Note Dialog
    if (showSendDialog && isUserLoggedIn) {
        SendNoteDialog(
            viewModel = viewModel,
            onDismiss = { showSendDialog = false }
        )
    }

    // Bug Report Dialog
    if (showBugReportDialog && isUserLoggedIn) {
        BugReportDialog(
            viewModel = viewModel,
            onDismiss = { showBugReportDialog = false }
        )
    }

    // Markdown Reader Dialog
    readingPair?.let { (msg, isSentView) ->
        InboxNoteReaderDialog(
            message = msg,
            isSentView = isSentView,
            onDismiss = { readingPair = null },
            onDownload = {
                viewModel.downloadInboxNote(msg) {
                    readingPair = null
                }
            }
        )
    }
}

@Composable
fun BugReportDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    var bugTitle by remember { mutableStateOf("") }
    var bugDescription by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        icon = {
            Icon(
                imageVector = Icons.Default.BugReport,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text("Relatar Bug aos Devs", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Encontrou algum problema? Envie os detalhes diretamente para a equipe de desenvolvedores do Citra.",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = bugTitle,
                    onValueChange = {
                        bugTitle = it
                        errorMsg = null
                    },
                    label = { Text("Título / Onde ocorreu") },
                    placeholder = { Text("Ex: Erro ao abrir calendário") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = bugDescription,
                    onValueChange = {
                        bugDescription = it
                        errorMsg = null
                    },
                    label = { Text("Descreva o bug") },
                    placeholder = { Text("Conte o que aconteceu e como reproduzir...") },
                    minLines = 4,
                    maxLines = 8,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (bugDescription.isBlank()) {
                        errorMsg = "Descreva o problema antes de enviar."
                        return@Button
                    }
                    isSending = true
                    errorMsg = null
                    viewModel.sendBugReport(
                        title = bugTitle,
                        description = bugDescription,
                        onSuccess = {
                            isSending = false
                            onDismiss()
                        },
                        onError = { err ->
                            isSending = false
                            errorMsg = err
                        }
                    )
                },
                enabled = !isSending
            ) {
                if (isSending) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text("Enviar Relato")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSending
            ) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun InboxItemCard(
    message: InboxMessage,
    isSentView: Boolean = false,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onRead: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    val formattedDate = remember(message.sentAtMillis) { dateFormat.format(Date(message.sentAtMillis)) }

    val primaryPersonName = if (isSentView) {
        message.recipientDisplayName.ifBlank { "Destinatário Citra" }
    } else {
        message.senderDisplayName
    }
    val primaryUsername = if (isSentView) {
        message.recipientUsername
    } else {
        message.senderUsername
    }
    val primaryPhoto = if (isSentView) message.recipientPhotoUrl else message.senderPhotoUrl

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                !isSentView && !message.isRead -> MaterialTheme.colorScheme.surfaceContainerHighest
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = when {
                isSelected -> MaterialTheme.colorScheme.primary
                message.isBugReport -> MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                message.isOfficialDev || message.isBroadcast -> Color(0xFFFFB703)
                else -> MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onRead() }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Top Row: Sender/Recipient Info & Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    if (isSelectionMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onToggleSelect() },
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }

                    if (primaryPhoto != null) {
                        AsyncImage(
                            model = primaryPhoto,
                            contentDescription = null,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(
                                    when {
                                        message.isBugReport -> MaterialTheme.colorScheme.error
                                        message.isOfficialDev && !isSentView -> Color(0xFFFF8B1F)
                                        else -> MaterialTheme.colorScheme.primary
                                    },
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when {
                                    message.isBugReport -> "🐞"
                                    message.isOfficialDev && !isSentView -> "🍊"
                                    else -> primaryPersonName.take(1).uppercase()
                                },
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isSentView) "Para: $primaryPersonName" else primaryPersonName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (primaryUsername.isNotBlank()) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "@$primaryUsername",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Text(
                            text = formattedDate,
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                when {
                    message.isBugReport -> {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = "🐞 Bug",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                    message.isOfficialDev || message.isBroadcast -> OfficialCitraBadge()
                    !isSentView && !message.isRead -> {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(Color(0xFFFF8B1F), CircleShape)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Note Title & Snippet
            Text(
                text = message.title,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = message.contentMarkdown.replace("#", "").replace("*", "").take(140),
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom Actions & Category Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = message.category,
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (!isSentView && message.isDownloaded) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFECFDF5)
                        ) {
                            Text(
                                text = "✓ Baixada",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF059669),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                if (!isSelectionMode) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = onRead,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Ler", fontSize = 11.5.sp)
                        }

                        if (!isSentView && !message.isOfficialDev && !message.isBroadcast && !message.isBugReport) {
                            FilledTonalButton(
                                onClick = onDownload,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (message.isDownloaded) "Baixar +" else "Baixar", fontSize = 11.5.sp)
                            }
                        }

                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Apagar",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OfficialCitraBadge() {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFFFFBEB),
        border = BorderStroke(1.2.dp, Color(0xFFFFB703))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🍊", fontSize = 12.sp)
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = "Oficial Citra",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFFB45309)
            )
        }
    }
}

@Composable
fun InboxNoteReaderDialog(
    message: InboxMessage,
    isSentView: Boolean = false,
    onDismiss: () -> Unit,
    onDownload: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = message.title,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 19.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isSentView) {
                                    "Enviada para ${message.recipientDisplayName.ifBlank { "Usuário Citra" }}"
                                } else {
                                    "Enviada por ${message.senderDisplayName}"
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (message.isOfficialDev || message.isBroadcast) {
                                Spacer(modifier = Modifier.width(8.dp))
                                OfficialCitraBadge()
                            }
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    MarkdownViewer(markdownText = message.contentMarkdown)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Fechar")
                    }

                    if (!isSentView) {
                        if (message.isOfficialDev || message.isBroadcast) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFFFFBEB),
                                border = BorderStroke(1.dp, Color(0xFFFFB703)),
                                modifier = Modifier.weight(1.3f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFB45309), modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Leitura Oficial Protegida",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFB45309)
                                    )
                                }
                            }
                        } else if (!message.isBugReport) {
                            Button(
                                onClick = onDownload,
                                modifier = Modifier.weight(1.3f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (message.isDownloaded) "Baixar Novamente" else "Baixar no Meu Bloco", fontSize = 12.5.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
