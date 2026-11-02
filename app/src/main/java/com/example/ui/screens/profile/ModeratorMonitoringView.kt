package com.example.ui.screens.profile

import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.auth.AuthRepository
import com.example.data.auth.GroupMonitoringData
import com.example.data.auth.SystemStorageMetrics
import com.example.data.auth.UserMonitoringData
import com.example.ui.MainViewModel
import com.example.ui.theme.RedAction
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ModeratorMonitoringView(
    viewModel: MainViewModel,
    authRepository: AuthRepository
) {
    val coroutineScope = rememberCoroutineScope()

    var metrics by remember { mutableStateOf(SystemStorageMetrics()) }
    var users by remember { mutableStateOf<List<UserMonitoringData>>(emptyList()) }
    var groups by remember { mutableStateOf<List<GroupMonitoringData>>(emptyList()) }
    var selectedUids by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    var showDeleteSelectedDialog by remember { mutableStateOf(false) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var userToToggleModerator by remember { mutableStateOf<UserMonitoringData?>(null) }

    fun refreshData() {
        isLoading = true
        statusMessage = null
        coroutineScope.launch {
            val (fetchedMetrics, fetchedUsers, fetchedGroups) = authRepository.fetchMonitoringData()
            metrics = fetchedMetrics
            users = fetchedUsers
            groups = fetchedGroups
            selectedUids = emptySet()
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshData()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. MODERATOR HEADER CARD
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Security, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Monitoramento do Sistema", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                            Text("Painel de Controle e Nuvem Firebase", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    IconButton(
                        onClick = { refreshData() },
                        enabled = !isLoading,
                        modifier = Modifier.size(36.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Atualizar", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF2E7D32).copy(alpha = 0.15f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF2E7D32)))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Firebase Firestore Ativo", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                        }
                    }

                    Text(
                        text = "Acesso de Administrador",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // 2. STATUS FEEDBACK NOTIFICATION
        statusMessage?.let { msg ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(msg, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
        }

        // 3. FIREBASE STORAGE GAUGE / MEDIDOR DE ESPAÇO TOTAL
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudQueue, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Armazenamento Total do Firebase", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }

                    Text(
                        text = "Plano Spark (1.00 GB)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Storage visual bar
                val totalCap = metrics.totalCapacityBytes.toDouble()
                val textFraction = (metrics.textUsedBytes.toDouble() / totalCap).coerceIn(0.0, 1.0).toFloat()
                val imageFraction = (metrics.imageUsedBytes.toDouble() / totalCap).coerceIn(0.0, 1.0).toFloat()
                val freeFraction = (1f - textFraction - imageFraction).coerceAtLeast(0f)

                // Visual segmented progress bar
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(18.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        if (textFraction > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(textFraction.coerceAtLeast(0.01f))
                                    .background(Color(0xFF1E88E5)) // Blue for Text
                            )
                        }
                        if (imageFraction > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(imageFraction.coerceAtLeast(0.01f))
                                    .background(Color(0xFF8E24AA)) // Purple for Images
                            )
                        }
                        if (freeFraction > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(freeFraction)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${formatBytes(metrics.totalUsedBytes)} Usados",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val percentUsed = (metrics.totalUsedBytes.toDouble() / metrics.totalCapacityBytes) * 100.0
                        Text(
                            text = "${String.format(Locale.US, "%.3f%%", percentUsed)} da cota",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${formatBytes(metrics.totalCapacityBytes - metrics.totalUsedBytes)} Livres",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Breakdown indicators (Text vs Image vs Free)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StorageBadgeItem(
                        dotColor = Color(0xFF1E88E5),
                        label = "Texto / JSON",
                        value = formatBytes(metrics.textUsedBytes)
                    )
                    StorageBadgeItem(
                        dotColor = Color(0xFF8E24AA),
                        label = "Imagens / Mídia",
                        value = formatBytes(metrics.imageUsedBytes)
                    )
                    StorageBadgeItem(
                        dotColor = Color(0xFF757575),
                        label = "Espaço Disponível",
                        value = formatBytes(metrics.totalCapacityBytes - metrics.totalUsedBytes)
                    )
                }
            }
        }

        // 4. DATA CONTROLS ROW (Delete selected / Delete all)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    selectedUids = if (selectedUids.size == users.size) emptySet() else users.map { it.uid }.toSet()
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (selectedUids.size == users.size && users.isNotEmpty()) "Desmarcar" else "Sel. Todas",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = { showDeleteSelectedDialog = true },
                enabled = selectedUids.isNotEmpty(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RedAction),
                modifier = Modifier.weight(1.3f)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Excluir (${selectedUids.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            FilledTonalButton(
                onClick = { showDeleteAllDialog = true },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = RedAction.copy(alpha = 0.12f), contentColor = RedAction),
                modifier = Modifier.weight(1.3f)
            ) {
                Text(
                    text = "Limpar Banco",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 5. REGISTERED ACCOUNTS LIST
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Contas Registradas no App (${users.size})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Backup na Nuvem",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (users.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.PersonOff, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Nenhuma outra conta cadastrada no Firestore", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                users.forEach { userItem ->
                    val isSelected = selectedUids.contains(userItem.uid)
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedUids = if (isSelected) selectedUids - userItem.uid else selectedUids + userItem.uid
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    selectedUids = if (checked) selectedUids + userItem.uid else selectedUids - userItem.uid
                                }
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = userItem.displayName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = userItem.username,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (userItem.isModerator) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFFD32F2F).copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = "🛡️ Moderador",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFD32F2F),
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    if (userItem.isPrivate) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(11.dp))
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF1E88E5).copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = "Texto: ${formatBytes(userItem.textSizeBytes)}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1E88E5),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF8E24AA).copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = "Mídia: ${formatBytes(userItem.imageSizeBytes)}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF8E24AA),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }

                                    Text(
                                        text = "Total: ${formatBytes(userItem.backupSizeBytes)}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                if (userItem.lastSynced > 0) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    val formattedTime = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(userItem.lastSynced))
                                    Text(
                                        text = "Último backup: $formattedTime",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Toggle moderator button
                            IconButton(
                                onClick = { userToToggleModerator = userItem }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AdminPanelSettings,
                                    contentDescription = if (userItem.isModerator) "Revogar Moderador" else "Definir como Moderador",
                                    tint = if (userItem.isModerator) Color(0xFFD32F2F) else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Single account delete icon
                            IconButton(
                                onClick = {
                                    selectedUids = setOf(userItem.uid)
                                    showDeleteSelectedDialog = true
                                }
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Excluir dados", tint = RedAction, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }

        // 6. GROUPS & MEMBER OCCUPATION LIST
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Grupos Existentes (${groups.size})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Ocupação dos Membros",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (groups.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.GroupOff, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Nenhum grupo ativo no banco", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                groups.forEach { groupItem ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = groupItem.name.take(2).uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = groupItem.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (groupItem.groupType == "UNILATERAL") MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = if (groupItem.groupType == "UNILATERAL") "Unilateral" else "Liberal",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (groupItem.groupType == "UNILATERAL") MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(3.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${groupItem.memberCount} membros",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Espaço ocupado: ${formatBytes(groupItem.totalSizeBytes)}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 7. CONFIRM DELETE SELECTED ACCOUNTS DIALOG
    if (showDeleteSelectedDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteSelectedDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = RedAction, modifier = Modifier.size(32.dp)) },
            title = { Text("Excluir Dados Selecionados?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Esta ação excluirá os backups em nuvem de ${selectedUids.size} conta(s) selecionada(s) no Firestore. Os dados não poderão ser recuperados. Deseja continuar?",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteSelectedDialog = false
                        isLoading = true
                        coroutineScope.launch {
                            val res = authRepository.deleteUsersCloudData(selectedUids.toList())
                            isLoading = false
                            if (res.isSuccess) {
                                statusMessage = "Dados de ${selectedUids.size} conta(s) excluídos com sucesso."
                                refreshData()
                            } else {
                                statusMessage = "Erro ao excluir: ${res.exceptionOrNull()?.localizedMessage}"
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedAction)
                ) {
                    Text("Excluir Definitivamente")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // 8. CONFIRM DELETE ALL ACCOUNTS CLOUD DATA DIALOG
    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = RedAction, modifier = Modifier.size(36.dp)) },
            title = { Text("Limpar Dados de TODAS as Contas?", fontWeight = FontWeight.Bold, color = RedAction) },
            text = {
                Text(
                    "ATENÇÃO MÁXIMA: Esta ação apagará todos os dados e backups de todas as contas no banco de dados do Firebase Firestore. Esta ação é segura para a infraestrutura, mas irreversível para os usuários.",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAllDialog = false
                        isLoading = true
                        coroutineScope.launch {
                            val res = authRepository.deleteAllUsersCloudData()
                            isLoading = false
                            if (res.isSuccess) {
                                statusMessage = "Todos os dados do banco foram limpos com segurança."
                                refreshData()
                            } else {
                                statusMessage = "Erro ao limpar banco: ${res.exceptionOrNull()?.localizedMessage}"
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedAction)
                ) {
                    Text("Confirmar e Limpar Tudo")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Dialog: Definir ou revogar moderador
    if (userToToggleModerator != null) {
        val target = userToToggleModerator!!
        val isTargetMod = target.isModerator
        AlertDialog(
            onDismissRequest = { userToToggleModerator = null },
            title = {
                Text(
                    text = if (isTargetMod) "Revogar Privilégio de Moderador" else "Definir como Moderador",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (isTargetMod) {
                        "Deseja revogar os privilégios de moderador de ${target.displayName} (${target.username})? O usuário voltará a ser uma conta padrão."
                    } else {
                        "Deseja conceder privilégios de moderador para ${target.displayName} (${target.username})? Este usuário passará a ter acesso ao painel de monitoramento do sistema."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uid = target.uid
                        val newStatus = !isTargetMod
                        userToToggleModerator = null
                        coroutineScope.launch {
                            val res = authRepository.toggleUserModeratorStatus(uid, newStatus)
                            if (res.isSuccess) {
                                statusMessage = if (newStatus) "Usuário promovido a Moderador com sucesso!" else "Privilégio de moderador revogado com sucesso."
                                refreshData()
                            } else {
                                statusMessage = "Erro: ${res.exceptionOrNull()?.message}"
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isTargetMod) RedAction else MaterialTheme.colorScheme.primary)
                ) {
                    Text(if (isTargetMod) "Revogar Privilégio" else "Definir Moderador")
                }
            },
            dismissButton = {
                TextButton(onClick = { userToToggleModerator = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun StorageBadgeItem(
    dotColor: Color,
    label: String,
    value: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(dotColor))
            Spacer(modifier = Modifier.width(4.dp))
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 * 1024 -> String.format(Locale.US, "%.2f GB", bytes.toDouble() / (1024 * 1024 * 1024))
        bytes >= 1024 * 1024 -> String.format(Locale.US, "%.2f MB", bytes.toDouble() / (1024 * 1024))
        bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes.toDouble() / 1024)
        else -> "$bytes B"
    }
}
