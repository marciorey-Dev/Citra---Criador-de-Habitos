package com.example.ui.screens.inbox

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.inbox.InboxRepository
import com.example.data.model.Note
import com.example.data.model.RecipientInfo
import com.example.data.model.RecipientLookupState
import com.example.ui.MainViewModel
import com.google.firebase.auth.FirebaseAuth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendNoteDialog(
    viewModel: MainViewModel,
    initialNote: Note? = null,
    onDismiss: () -> Unit
) {
    val notes by viewModel.notes.collectAsState()
    val recipientState by viewModel.recipientLookupState.collectAsState()

    val currentAuthUser = FirebaseAuth.getInstance().currentUser
    val isDeveloper = remember(currentAuthUser) {
        InboxRepository.isDeveloper(currentAuthUser?.email)
    }

    var isBroadcast by remember { mutableStateOf(false) }
    var usernameInput by remember { mutableStateOf("") }
    var selectedRecipient by remember { mutableStateOf<RecipientInfo?>(null) }

    var useExistingNote by remember { mutableStateOf(initialNote != null) }
    var selectedExistingNote by remember { mutableStateOf(initialNote) }
    var noteDropdownExpanded by remember { mutableStateOf(false) }

    var customTitle by remember { mutableStateOf(initialNote?.title ?: "") }
    var customContent by remember { mutableStateOf(initialNote?.content ?: "") }
    var customCategory by remember { mutableStateOf(initialNote?.category ?: "Geral") }

    var isSending by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Synchronize recipient search with input
    LaunchedEffect(usernameInput) {
        if (!isBroadcast) {
            viewModel.searchRecipient(usernameInput)
        }
    }

    LaunchedEffect(recipientState) {
        if (recipientState is RecipientLookupState.Found) {
            selectedRecipient = (recipientState as RecipientLookupState.Found).recipient
        } else if (recipientState !is RecipientLookupState.Loading) {
            selectedRecipient = null
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
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF2563EB).copy(alpha = 0.12f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = null,
                                    tint = Color(0xFF2563EB),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Compartilhar Nota",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Envio direto via Citra",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

                // Scrollable Form
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Developer Broadcast Switch (Only for authorized dev accounts)
                    if (isDeveloper) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFFFF7ED),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFFFB703)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("🍊", fontSize = 16.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Comunicado Oficial Citra",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Color(0xFF9A3412)
                                        )
                                    }
                                    Text(
                                        text = "Enviar para a caixa de todos os usuários como comunicado oficial",
                                        fontSize = 11.5.sp,
                                        color = Color(0xFFB45309)
                                    )
                                }
                                Switch(
                                    checked = isBroadcast,
                                    onCheckedChange = { isBroadcast = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFFFF8B1F),
                                        checkedTrackColor = Color(0xFFFFD166)
                                    )
                                )
                            }
                        }
                    }

                    // Recipient Section (Hidden if broadcast)
                    AnimatedVisibility(visible = !isBroadcast) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Nome de Usuário de Destino",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            OutlinedTextField(
                                value = usernameInput,
                                onValueChange = {
                                    usernameInput = it
                                    errorMessage = null
                                },
                                placeholder = { Text("@exemplo ou nome do usuário") },
                                leadingIcon = {
                                    Icon(Icons.Default.AlternateEmail, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                },
                                trailingIcon = {
                                    when (recipientState) {
                                        is RecipientLookupState.Loading -> {
                                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        }
                                        is RecipientLookupState.Found -> {
                                            Icon(Icons.Default.CheckCircle, contentDescription = "Encontrado", tint = Color(0xFF10B981))
                                        }
                                        is RecipientLookupState.NotFound -> {
                                            Icon(Icons.Default.Cancel, contentDescription = "Não encontrado", tint = MaterialTheme.colorScheme.error)
                                        }
                                        else -> {}
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Visual Instant Verification Card
                            when (val state = recipientState) {
                                is RecipientLookupState.Found -> {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFFECFDF5),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (state.recipient.photoUrl != null) {
                                                AsyncImage(
                                                    model = state.recipient.photoUrl,
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .clip(CircleShape)
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .background(Color(0xFF10B981), CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = state.recipient.displayName.take(1).uppercase(),
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = state.recipient.displayName,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = Color(0xFF065F46)
                                                )
                                                Text(
                                                    text = "@${state.recipient.username}",
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF047857)
                                                )
                                            }
                                            Spacer(modifier = Modifier.weight(1f))
                                            Text(
                                                text = "✓ Confirmado",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = Color(0xFF059669)
                                            )
                                        }
                                    }
                                }
                                is RecipientLookupState.NotFound -> {
                                    Text(
                                        text = "✕ Usuário não encontrado no banco de dados",
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                is RecipientLookupState.Error -> {
                                    Text(
                                        text = state.message,
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                else -> {}
                            }
                        }
                    }

                    // Source Mode Switch: Existing note or new markdown
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = useExistingNote,
                            onClick = { useExistingNote = true },
                            label = { Text("Nota Existente") },
                            leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                        FilterChip(
                            selected = !useExistingNote,
                            onClick = { useExistingNote = false },
                            label = { Text("Escrever Agora") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                    }

                    if (useExistingNote) {
                        // Note selector dropdown
                        Box {
                            OutlinedCard(
                                onClick = { noteDropdownExpanded = true },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = selectedExistingNote?.title ?: "Toque para escolher uma nota",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.5.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (selectedExistingNote != null) {
                                            Text(
                                                text = "Categoria: ${selectedExistingNote?.category}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            }

                            DropdownMenu(
                                expanded = noteDropdownExpanded,
                                onDismissRequest = { noteDropdownExpanded = false }
                            ) {
                                val activeNotes = notes.filter { !it.isDeleted }
                                if (activeNotes.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("Nenhuma nota salva") },
                                        onClick = { noteDropdownExpanded = false }
                                    )
                                } else {
                                    activeNotes.forEach { note ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(note.title.ifBlank { "Sem título" }, fontWeight = FontWeight.SemiBold)
                                                    Text(note.category, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            },
                                            onClick = {
                                                selectedExistingNote = note
                                                customTitle = note.title
                                                customContent = note.content
                                                customCategory = note.category
                                                noteDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Compose new note fields
                        OutlinedTextField(
                            value = customTitle,
                            onValueChange = { customTitle = it },
                            label = { Text("Título da Nota") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = customCategory,
                            onValueChange = { customCategory = it },
                            label = { Text("Categoria") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = customContent,
                            onValueChange = { customContent = it },
                            label = { Text("Conteúdo em Markdown (.md)") },
                            minLines = 5,
                            maxLines = 10,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancelar")
                    }

                    val canSend = (isBroadcast || selectedRecipient != null) &&
                            ((useExistingNote && selectedExistingNote != null) || (!useExistingNote && customContent.isNotBlank()))

                    Button(
                        onClick = {
                            val finalTitle = if (useExistingNote) selectedExistingNote?.title.orEmpty() else customTitle
                            val finalContent = if (useExistingNote) selectedExistingNote?.content.orEmpty() else customContent
                            val finalCategory = if (useExistingNote) selectedExistingNote?.category.orEmpty() else customCategory

                            if (finalContent.isBlank()) {
                                errorMessage = "O conteúdo da nota não pode estar vazio"
                                return@Button
                            }

                            isSending = true
                            errorMessage = null
                            viewModel.sendNoteMessage(
                                recipientUid = if (isBroadcast) null else selectedRecipient?.uid,
                                isBroadcast = isBroadcast,
                                title = finalTitle,
                                content = finalContent,
                                category = finalCategory,
                                onSuccess = {
                                    isSending = false
                                    onDismiss()
                                },
                                onError = { err ->
                                    isSending = false
                                    errorMessage = err
                                }
                            )
                        },
                        enabled = canSend && !isSending,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isBroadcast) "Transmitir" else "Enviar")
                            }
                        }
                    }
                }
            }
        }
    }
}
