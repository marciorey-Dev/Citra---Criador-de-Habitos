package com.example.ui.screens.profile

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.auth.UserProfile
import com.example.data.devtools.DevStorageOverview
import com.example.data.devtools.DevToolsRepository
import com.example.data.devtools.DevUserAccount
import com.example.data.devtools.formatStorageBytes
import com.example.ui.theme.*
import com.example.util.squashBounceClickable
import kotlinx.coroutines.launch

// Storage Category Colors
private val ColorNotesStorage = Color(0xFFFF9E40) // Tangerina notes
private val ColorSettingsStorage = Color(0xFF1E88E5) // Blue config
private val ColorTasksStorage = Color(0xFF2E7D32) // Emerald tasks
private val ColorHabitsStorage = Color(0xFFE53935) // Red habits
private val ColorOtherStorage = Color(0xFF7E57C2) // Purple other/finances

@Composable
fun DevToolsPanel(
    currentUser: UserProfile?,
    devToolsRepository: DevToolsRepository
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val accounts by devToolsRepository.accounts.collectAsState()
    val overview by devToolsRepository.overview.collectAsState()
    val isLoading by devToolsRepository.isLoading.collectAsState()

    var searchQuery by remember { mutableStateOf("") }

    // Dialog States
    var userToMessage by remember { mutableStateOf<DevUserAccount?>(null) }
    var userToClearData by remember { mutableStateOf<DevUserAccount?>(null) }
    var userToDelete by remember { mutableStateOf<DevUserAccount?>(null) }
    var userToToggleBan by remember { mutableStateOf<DevUserAccount?>(null) }
    var userToToggleDev by remember { mutableStateOf<DevUserAccount?>(null) }

    LaunchedEffect(currentUser) {
        devToolsRepository.refreshData(currentUser)
    }

    val filteredAccounts = remember(accounts, searchQuery) {
        if (searchQuery.isBlank()) accounts
        else accounts.filter {
            it.displayName.contains(searchQuery, ignoreCase = true) ||
                it.username.contains(searchQuery, ignoreCase = true) ||
                (it.email?.contains(searchQuery, ignoreCase = true) == true)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. DevTools Banner Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.5.dp, OrangePrimary.copy(alpha = 0.5f)),
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = OrangePrimary.copy(alpha = 0.15f),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AdminPanelSettings,
                                    contentDescription = null,
                                    tint = OrangePrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "DevTools & Moderação",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFFFB703).copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "DEV",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFD97706),
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Monitoramento do backend Firebase & Gestão de Contas",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            try { haptic.performHapticFeedback(HapticFeedbackType.LongPress) } catch (_: Throwable) {}
                            coroutineScope.launch {
                                devToolsRepository.refreshData(currentUser)
                                Toast.makeText(context, "Dados do DevTools atualizados!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .squashBounceClickable {
                                coroutineScope.launch { devToolsRepository.refreshData(currentUser) }
                            }
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = OrangePrimary)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Atualizar", tint = OrangePrimary)
                        }
                    }
                }

                // Quick stats summary row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    DevMiniStatBadge(
                        label = "Total Contas",
                        value = "${overview.totalAccountsCount}",
                        icon = Icons.Default.People,
                        color = MaterialTheme.colorScheme.primary
                    )
                    DevMiniStatBadge(
                        label = "Ativos",
                        value = "${overview.activeAccountsCount}",
                        icon = Icons.Default.CheckCircle,
                        color = ColorTasksStorage
                    )
                    DevMiniStatBadge(
                        label = "Devs",
                        value = "${overview.devAccountsCount}",
                        icon = Icons.Default.Engineering,
                        color = Color(0xFFD97706)
                    )
                    DevMiniStatBadge(
                        label = "Banidos",
                        value = "${overview.bannedAccountsCount}",
                        icon = Icons.Default.Block,
                        color = RedAction
                    )
                }
            }
        }

        // 2. Global Cloud Storage Multi-Color Progress Card
        DevCloudStorageCard(overview = overview)

        // 3. User Accounts & Moderation Section
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Contas de Usuários (${filteredAccounts.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "10 MB cota / conta",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // Search text field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Pesquisar por nome, @username ou e-mail...", fontSize = 12.sp) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Limpar", modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = OrangePrimary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Accounts List
            if (filteredAccounts.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isBlank()) "Nenhuma conta encontrada." else "Nenhuma conta corresponde à busca.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    filteredAccounts.forEach { account ->
                        DevUserAccountCard(
                            account = account,
                            isCurrentUser = currentUser?.uid == account.uid,
                            onSendMessage = { userToMessage = account },
                            onClearData = { userToClearData = account },
                            onDeleteAccount = { userToDelete = account },
                            onToggleBan = { userToToggleBan = account },
                            onToggleDev = { userToToggleDev = account }
                        )
                    }
                }
            }
        }
    }

    // --- Action Dialogs ---

    // 1. Direct Message Dialog
    if (userToMessage != null) {
        val target = userToMessage!!
        var msgTitle by remember { mutableStateOf("Aviso da Equipe Citra") }
        var msgBody by remember { mutableStateOf("") }
        var isSending by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isSending) userToMessage = null },
            icon = { Icon(Icons.Default.MailOutline, contentDescription = null, tint = OrangePrimary) },
            title = {
                Text(
                    text = "Mensagem para @${target.username}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Esta mensagem será entregue diretamente na caixa de entrada oficial do perfil ${target.displayName}.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = msgTitle,
                        onValueChange = { msgTitle = it },
                        label = { Text("Título da Notificação") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = msgBody,
                        onValueChange = { msgBody = it },
                        label = { Text("Mensagem") },
                        placeholder = { Text("Ex: Atualização sobre seu limite de armazenamento ou nota oficial...") },
                        minLines = 3,
                        maxLines = 5,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Quick Template Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = OrangePrimary.copy(alpha = 0.12f),
                            modifier = Modifier
                                .clickable {
                                    msgTitle = "Aviso de Manutenção Programada"
                                    msgBody = "Olá! O servidor da nuvem passará por uma rápida manutenção nas próximas horas. Suas notas locais continuam seguras."
                                }
                        ) {
                            Text("Manutenção", fontSize = 10.5.sp, color = OrangePrimary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }

                        Surface(
                            shape = CircleShape,
                            color = ColorTasksStorage.copy(alpha = 0.12f),
                            modifier = Modifier
                                .clickable {
                                    msgTitle = "Parabéns pelo Progresso!"
                                    msgBody = "A equipe Citra notou seu ritmo incrível com hábitos diários. Continue assim florescendo a sua produtividade!"
                                }
                        ) {
                            Text("Elogio", fontSize = 10.5.sp, color = ColorTasksStorage, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isSending = true
                        coroutineScope.launch {
                            val res = devToolsRepository.sendMessageToInbox(
                                targetUid = target.uid,
                                targetDisplayName = target.displayName,
                                title = msgTitle,
                                body = msgBody.ifBlank { "Mensagem da moderação do app." },
                                sender = currentUser
                            )
                            isSending = false
                            userToMessage = null
                            if (res.isSuccess) {
                                Toast.makeText(context, "Mensagem enviada para a caixa de @${target.username}!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Erro ao enviar mensagem: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = !isSending && (msgBody.isNotBlank() || msgTitle.isNotBlank()),
                    colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary)
                ) {
                    if (isSending) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Enviar Mensagem")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { userToMessage = null }, enabled = !isSending) {
                    Text("Cancelar")
                }
            }
        )
    }

    // 2. Clear Data Confirmation Dialog
    if (userToClearData != null) {
        val target = userToClearData!!
        AlertDialog(
            onDismissRequest = { userToClearData = null },
            icon = { Icon(Icons.Default.CleaningServices, contentDescription = null, tint = RedAction) },
            title = { Text("Limpar Dados da Nuvem?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Deseja remover todos os backups e notas salvos na nuvem da conta @${target.username} (${target.displayName})? A conta continuará existindo, mas seu consumo de espaço na nuvem será zerado.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            devToolsRepository.clearUserData(target.uid)
                            Toast.makeText(context, "Dados da nuvem de @${target.username} foram limpos com sucesso!", Toast.LENGTH_SHORT).show()
                            userToClearData = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedAction)
                ) {
                    Text("Limpar Dados")
                }
            },
            dismissButton = {
                TextButton(onClick = { userToClearData = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // 3. Delete Account Dialog
    if (userToDelete != null) {
        val target = userToDelete!!
        AlertDialog(
            onDismissRequest = { userToDelete = null },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = RedAction) },
            title = { Text("Deletar Conta Definitivamente?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "ATENÇÃO: A conta @${target.username} (${target.displayName}) será permanentemente removida do banco de dados do app junto com todos os seus dados.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            devToolsRepository.deleteUserAccount(target.uid)
                            Toast.makeText(context, "Conta @${target.username} deletada com sucesso.", Toast.LENGTH_SHORT).show()
                            userToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedAction)
                ) {
                    Text("Excluir Conta")
                }
            },
            dismissButton = {
                TextButton(onClick = { userToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // 4. Ban / Unban Dialog
    if (userToToggleBan != null) {
        val target = userToToggleBan!!
        val actionBan = !target.isBanned
        AlertDialog(
            onDismissRequest = { userToToggleBan = null },
            icon = { Icon(if (actionBan) Icons.Default.Block else Icons.Default.CheckCircle, contentDescription = null, tint = if (actionBan) RedAction else ColorTasksStorage) },
            title = { Text(if (actionBan) "Banir Conta do App?" else "Remover Banimento?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = if (actionBan) {
                        "A conta @${target.username} será bloqueada de acessar as funcionalidades online e de sincronização do Citra."
                    } else {
                        "A conta @${target.username} terá o acesso restabelecido ao Citra."
                    },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            devToolsRepository.toggleBanUser(target.uid, actionBan)
                            Toast.makeText(context, if (actionBan) "Conta @${target.username} banida." else "Banimento removido.", Toast.LENGTH_SHORT).show()
                            userToToggleBan = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (actionBan) RedAction else ColorTasksStorage)
                ) {
                    Text(if (actionBan) "Confirmar Banimento" else "Desbanir Conta")
                }
            },
            dismissButton = {
                TextButton(onClick = { userToToggleBan = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // 5. Promote / Demote Dev Dialog
    if (userToToggleDev != null) {
        val target = userToToggleDev!!
        val actionPromote = !target.isDev
        AlertDialog(
            onDismissRequest = { userToToggleDev = null },
            icon = { Icon(Icons.Default.Engineering, contentDescription = null, tint = Color(0xFFD97706)) },
            title = { Text(if (actionPromote) "Promover a Desenvolvedor?" else "Rebaixar Permissão de Dev?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = if (actionPromote) {
                        "Ao promover @${target.username} a Desenvolvedor, este perfil terá acesso completo a este painel DevTools, monitoramento do Base, ferramentas de moderação e envio de avisos oficiais."
                    } else {
                        "Deseja revogar as permissões de desenvolvedor da conta @${target.username}?"
                    },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            devToolsRepository.toggleDevRole(target.uid, target.email, actionPromote)
                            Toast.makeText(context, if (actionPromote) "@${target.username} agora é um Desenvolvedor!" else "Permissão de Dev revogada.", Toast.LENGTH_SHORT).show()
                            userToToggleDev = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
                ) {
                    Text(if (actionPromote) "Promover a Dev" else "Revogar Dev")
                }
            },
            dismissButton = {
                TextButton(onClick = { userToToggleDev = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

/**
 * Multi-colored stacked progress bar showing cloud storage consumption on the Firebase base.
 */
@Composable
private fun DevCloudStorageCard(overview: DevStorageOverview) {
    val usedFormatted = formatStorageBytes(overview.totalUsedBytes)
    val quotaFormatted = formatStorageBytes(overview.totalQuotaBytes)
    val freeFormatted = formatStorageBytes(overview.freeBytes)
    val percentUsed = (overview.usedRatio * 100).toInt()

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        shadowElevation = 1.5.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
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
                        imageVector = Icons.Default.CloudQueue,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Armazenamento em Nuvem do Base",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = "$percentUsed% ocupado",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Text(
                text = "$usedFormatted de $quotaFormatted consumidos • $freeFormatted livres no Base",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Multi-segment horizontal progress bar
            val total = overview.totalQuotaBytes.toFloat().coerceAtLeast(1f)
            val wNotes = (overview.notesBytes.toFloat() / total).coerceIn(0f, 1f)
            val wConfigs = (overview.settingsBytes.toFloat() / total).coerceIn(0f, 1f)
            val wTasks = (overview.tasksBytes.toFloat() / total).coerceIn(0f, 1f)
            val wHabits = (overview.habitsBytes.toFloat() / total).coerceIn(0f, 1f)
            val wOther = (overview.otherBytes.toFloat() / total).coerceIn(0f, 1f)
            val wFree = (overview.freeBytes.toFloat() / total).coerceIn(0f, 1f)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    if (wNotes > 0.005f) {
                        Box(
                            modifier = Modifier
                                .weight(wNotes)
                                .fillMaxHeight()
                                .background(ColorNotesStorage)
                        )
                    }
                    if (wConfigs > 0.005f) {
                        Box(
                            modifier = Modifier
                                .weight(wConfigs)
                                .fillMaxHeight()
                                .background(ColorSettingsStorage)
                        )
                    }
                    if (wTasks > 0.005f) {
                        Box(
                            modifier = Modifier
                                .weight(wTasks)
                                .fillMaxHeight()
                                .background(ColorTasksStorage)
                        )
                    }
                    if (wHabits > 0.005f) {
                        Box(
                            modifier = Modifier
                                .weight(wHabits)
                                .fillMaxHeight()
                                .background(ColorHabitsStorage)
                        )
                    }
                    if (wOther > 0.005f) {
                        Box(
                            modifier = Modifier
                                .weight(wOther)
                                .fillMaxHeight()
                                .background(ColorOtherStorage)
                        )
                    }
                    if (wFree > 0.01f) {
                        Box(
                            modifier = Modifier
                                .weight(wFree)
                                .fillMaxHeight()
                                .background(Color.Transparent)
                        )
                    }
                }
            }

            // Legend Grid
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    DevStorageLegendItem(
                        label = "Backup de Notas",
                        sizeStr = formatStorageBytes(overview.notesBytes),
                        color = ColorNotesStorage
                    )
                    DevStorageLegendItem(
                        label = "Configurações",
                        sizeStr = formatStorageBytes(overview.settingsBytes),
                        color = ColorSettingsStorage
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    DevStorageLegendItem(
                        label = "Tarefas",
                        sizeStr = formatStorageBytes(overview.tasksBytes),
                        color = ColorTasksStorage
                    )
                    DevStorageLegendItem(
                        label = "Hábitos",
                        sizeStr = formatStorageBytes(overview.habitsBytes),
                        color = ColorHabitsStorage
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    DevStorageLegendItem(
                        label = "Outros (Finanças)",
                        sizeStr = formatStorageBytes(overview.otherBytes),
                        color = ColorOtherStorage
                    )
                    DevStorageLegendItem(
                        label = "Espaço Livre Restante",
                        sizeStr = freeFormatted,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DevStorageLegendItem(label: String, sizeStr: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape)
        )
        Text(
            text = "$label: ",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = sizeStr,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Card representing an individual user account with its individual consumption progress bar
 * and action buttons (Mensagem, Limpar Dados, Deletar, Banir, Promover a Dev).
 */
@Composable
private fun DevUserAccountCard(
    account: DevUserAccount,
    isCurrentUser: Boolean,
    onSendMessage: () -> Unit,
    onClearData: () -> Unit,
    onDeleteAccount: () -> Unit,
    onToggleBan: () -> Unit,
    onToggleDev: () -> Unit
) {
    val usedFormatted = formatStorageBytes(account.totalStorageBytes)
    val quotaFormatted = formatStorageBytes(account.quotaBytes)
    val remainingFormatted = formatStorageBytes(account.remainingBytes)
    val pct = (account.usageRatio * 100).toInt()

    val cardBorderColor = when {
        account.isBanned -> RedAction.copy(alpha = 0.6f)
        account.isDev -> Color(0xFFFFB703).copy(alpha = 0.7f)
        isCurrentUser -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (account.isDev || account.isBanned) 1.5.dp else 1.dp, cardBorderColor),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top: User info + Status badges
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
                    // Avatar
                    if (!account.photoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = account.photoUrl,
                            contentDescription = account.displayName,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Surface(
                            shape = CircleShape,
                            color = if (account.isDev) Color(0xFFFFE0B2) else MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (account.isDev) "🍊" else account.displayName.take(1).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = if (account.isDev) Color(0xFFE65100) else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = account.displayName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (isCurrentUser) {
                                Surface(shape = CircleShape, color = ColorTasksStorage.copy(alpha = 0.15f)) {
                                    Text("Você", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = ColorTasksStorage, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                                }
                            }
                        }

                        Text(
                            text = "@${account.username}${if (!account.email.isNullOrBlank()) " • ${account.email}" else ""}",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Badges
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (account.isDev) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFFFB703).copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, Color(0xFFD97706).copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text("🛠️", fontSize = 10.sp)
                                Text("Dev", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                            }
                        }
                    }

                    if (account.isBanned) {
                        Surface(
                            shape = CircleShape,
                            color = RedAction.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, RedAction.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text("🚫", fontSize = 10.sp)
                                Text("Banido", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = RedAction)
                            }
                        }
                    }
                }
            }

            // Individual Storage Consumption Progress Bar
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Consumo: $usedFormatted de $quotaFormatted ($pct%)",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$remainingFormatted restantes",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                LinearProgressIndicator(
                    progress = { account.usageRatio },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = when {
                        account.usageRatio > 0.85f -> RedAction
                        account.usageRatio > 0.60f -> OrangePrimary
                        else -> ColorTasksStorage
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )

                // Sub-category distribution chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Notas: ${formatStorageBytes(account.notesStorageBytes)}",
                        fontSize = 10.sp,
                        color = ColorNotesStorage
                    )
                    Text(
                        text = "Tarefas: ${formatStorageBytes(account.tasksStorageBytes)}",
                        fontSize = 10.sp,
                        color = ColorTasksStorage
                    )
                    Text(
                        text = "Hábitos: ${formatStorageBytes(account.habitsStorageBytes)}",
                        fontSize = 10.sp,
                        color = ColorHabitsStorage
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 2.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )

            // Moderation Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Button 1: Mensagem para a Caixa de Entrada
                FilledTonalIconButton(
                    onClick = onSendMessage,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.MailOutline, contentDescription = "Enviar Mensagem", modifier = Modifier.size(18.dp))
                }

                // Button 2: Limpar Dados da Conta
                FilledTonalIconButton(
                    onClick = onClearData,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.CleaningServices, contentDescription = "Limpar Dados", modifier = Modifier.size(18.dp))
                }

                // Button 3: Promover como Dev (ou Revogar)
                FilledTonalIconButton(
                    onClick = onToggleDev,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (account.isDev) Color(0xFFFFE0B2) else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (account.isDev) Icons.Default.Stars else Icons.Default.Engineering,
                        contentDescription = "Promover/Revogar Dev",
                        tint = if (account.isDev) Color(0xFFD97706) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Button 4: Banir do App
                FilledTonalIconButton(
                    onClick = onToggleBan,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (account.isBanned) RedAction.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (account.isBanned) Icons.Default.LockOpen else Icons.Default.Block,
                        contentDescription = if (account.isBanned) "Desbanir" else "Banir",
                        tint = if (account.isBanned) RedAction else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Button 5: Deletar Conta
                FilledTonalIconButton(
                    onClick = onDeleteAccount,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = RedAction.copy(alpha = 0.12f),
                        contentColor = RedAction
                    ),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Deletar Conta", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun DevMiniStatBadge(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
                Text(text = value, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = color)
            }
            Text(text = label, fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
