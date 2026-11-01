package com.example.ui.screens.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.model.CitraGroup
import com.example.data.model.Friend
import com.example.data.model.GroupMember
import com.example.data.model.Note
import com.example.ui.MainViewModel
import com.example.ui.theme.RedAction
import com.example.ui.theme.RedContainer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialView(
    viewModel: MainViewModel,
    onOpenNote: (Note) -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val friends by viewModel.allFriends.collectAsState()
    val pendingRequests by viewModel.pendingFriendRequests.collectAsState()
    val suggestedFriends by viewModel.suggestedFriends.collectAsState()
    val blockedFriends by viewModel.blockedFriends.collectAsState()
    val pendingCount by viewModel.pendingRequestsCount.collectAsState()
    val groups by viewModel.allGroups.collectAsState()
    val pendingGroupInvites by viewModel.pendingGroupInvites.collectAsState()
    val notes by viewModel.notes.collectAsState()

    var isGroupsTab by remember { mutableStateOf(false) }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSortAscending by remember { mutableStateOf(true) }

    // Dialogs state
    var showRequestsAndSuggestionsDialog by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showAddFriendDialog by remember { mutableStateOf(false) }
    var selectedGroupForManagement by remember { mutableStateOf<CitraGroup?>(null) }
    var showBlockedFriendsDialog by remember { mutableStateOf(false) }

    val totalBadgeCount = pendingCount + pendingGroupInvites.size

    val filteredFriends = remember(friends, searchQuery, isSortAscending) {
        val list = if (searchQuery.isBlank()) friends else {
            friends.filter { it.name.contains(searchQuery, ignoreCase = true) || it.username.contains(searchQuery, ignoreCase = true) }
        }
        if (isSortAscending) list.sortedBy { it.name.lowercase() } else list.sortedByDescending { it.name.lowercase() }
    }

    val filteredGroups = remember(groups, searchQuery, isSortAscending) {
        val list = if (searchQuery.isBlank()) groups else {
            groups.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
        if (isSortAscending) list.sortedBy { it.name.lowercase() } else list.sortedByDescending { it.name.lowercase() }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // TOP SOCIAL HEADER (matches Mockups 1 & 2: "--", Bell with Badge, Search Icon)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "--",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Bell button with badge
                BadgedBox(
                    badge = {
                        if (totalBadgeCount > 0) {
                            Badge(
                                containerColor = Color(0xFFD32F2F),
                                contentColor = Color.White
                            ) {
                                Text("$totalBadgeCount", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                ) {
                    IconButton(
                        onClick = { showRequestsAndSuggestionsDialog = true },
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("social_bell_notifications_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Pedidos e Notificações",
                            tint = if (totalBadgeCount > 0) Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Search button
                IconButton(
                    onClick = {
                        isSearchExpanded = !isSearchExpanded
                        if (!isSearchExpanded) searchQuery = ""
                    },
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("social_search_toggle_btn")
                ) {
                    Icon(
                        imageVector = if (isSearchExpanded) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = "Buscar",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Inline Search Bar
        AnimatedVisibility(visible = isSearchExpanded) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(if (isGroupsTab) "Filtrar grupos..." else "Filtrar amigos...") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .testTag("social_inline_search_field")
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // COUNT AND SORT ROW (matches Mockup: "0 Amigos" / "X Grupos", Sort icon, List icon)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (isGroupsTab) "${filteredGroups.size} Grupos" else "${filteredFriends.size} Amigos",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { isSortAscending = !isSortAscending },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapVert,
                        contentDescription = "Ordenar",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (!isGroupsTab && blockedFriends.isNotEmpty()) {
                    IconButton(
                        onClick = { showBlockedFriendsDialog = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = "Ver Bloqueados",
                            tint = RedAction,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // SEGMENTED PILL SWITCHER: [👤 Amigos] [👥 Grupos] (matches Mockups 1 & 2)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Amigos Pill
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = if (!isGroupsTab) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.5.dp,
                    color = if (!isGroupsTab) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clickable { isGroupsTab = false }
                    .testTag("social_pill_amigos")
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (!isGroupsTab) Icons.Filled.Person else Icons.Outlined.Person,
                        contentDescription = null,
                        tint = if (!isGroupsTab) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Amigos",
                        fontSize = 15.sp,
                        fontWeight = if (!isGroupsTab) FontWeight.Bold else FontWeight.Medium,
                        color = if (!isGroupsTab) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Grupos Pill
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = if (isGroupsTab) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.5.dp,
                    color = if (isGroupsTab) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clickable { isGroupsTab = true }
                    .testTag("social_pill_grupos")
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isGroupsTab) Icons.Filled.Group else Icons.Outlined.Group,
                        contentDescription = null,
                        tint = if (isGroupsTab) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Grupos",
                        fontSize = 15.sp,
                        fontWeight = if (isGroupsTab) FontWeight.Bold else FontWeight.Medium,
                        color = if (isGroupsTab) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // TAB CONTENT
        if (!isGroupsTab) {
            // --- AMIGOS LIST (Mockup 1) ---
            if (filteredFriends.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Nenhum amigo adicionado ainda", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Toque no sininho acima para ver pedidos de amizade e sugestões da comunidade!",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                filteredFriends.forEach { friend ->
                    var showFriendMenu by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Avatar (circular)
                        if (!friend.avatarUri.isNullOrBlank()) {
                            AsyncImage(
                                model = friend.avatarUri,
                                contentDescription = friend.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(54.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = friend.name.take(1).uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = friend.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = friend.subtitleTag,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }

                        // 3-Dots Button with Block, Unfriend options
                        Box {
                            IconButton(
                                onClick = { showFriendMenu = true },
                                modifier = Modifier.testTag("friend_menu_${friend.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Mais opções",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            DropdownMenu(
                                expanded = showFriendMenu,
                                onDismissRequest = { showFriendMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Bloquear Amigo", color = RedAction) },
                                    leadingIcon = { Icon(Icons.Default.Block, contentDescription = null, tint = RedAction) },
                                    onClick = {
                                        showFriendMenu = false
                                        viewModel.blockFriend(friend)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Remover Amigo") },
                                    leadingIcon = { Icon(Icons.Default.PersonRemove, contentDescription = null) },
                                    onClick = {
                                        showFriendMenu = false
                                        viewModel.removeFriend(friend)
                                    }
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 8.dp))
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action row to add friend manually
            FilledTonalButton(
                onClick = { showAddFriendDialog = true },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_friend_manual_btn")
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Adicionar Amigo por ID / @Usuário", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            // --- GRUPOS LIST (Mockup 2) ---
            if (pendingGroupInvites.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1E88E5).copy(alpha = 0.08f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E88E5).copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Mail, contentDescription = null, tint = Color(0xFF1E88E5), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Convites de Grupos Pendentes (${pendingGroupInvites.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF1E88E5)
                            )
                        }
                        pendingGroupInvites.forEach { inviteGroup ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(inviteGroup.name.take(2).uppercase(), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(inviteGroup.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(
                                        if (inviteGroup.groupType == "UNILATERAL") "Unilateral (Modo Leitura)" else "Liberal (Live Preview)",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFFC75B5B),
                                        modifier = Modifier.clickable { viewModel.declineGroupInvite(inviteGroup) }
                                    ) {
                                        Text("Recusar", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp))
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.clickable { viewModel.acceptGroupInvite(inviteGroup) }
                                    ) {
                                        Text("Aceitar", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (filteredGroups.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Nenhum grupo ativo", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Crie grupos de estudos, trabalho ou projetos e compartilhe notas sincronizadas!",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                filteredGroups.forEach { group ->
                    var showGroupMenu by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedGroupForManagement = group }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Group Avatar (image or monogram)
                        if (!group.imageUri.isNullOrBlank()) {
                            AsyncImage(
                                model = group.imageUri,
                                contentDescription = group.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.size(54.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = group.avatarInitials,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = group.name,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (group.groupType == "UNILATERAL") MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = if (group.groupType == "UNILATERAL") "Unilateral" else "Liberal",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (group.groupType == "UNILATERAL") MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "<${group.memberCount} Membros>",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }

                        // 3-Dots Button on Group
                        Box {
                            IconButton(
                                onClick = { showGroupMenu = true },
                                modifier = Modifier.testTag("group_menu_${group.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Mais opções",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            DropdownMenu(
                                expanded = showGroupMenu,
                                onDismissRequest = { showGroupMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Ver Notas e Membros") },
                                    leadingIcon = { Icon(Icons.Default.FolderShared, contentDescription = null) },
                                    onClick = {
                                        showGroupMenu = false
                                        selectedGroupForManagement = group
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Sair do Grupo") },
                                    leadingIcon = { Icon(Icons.Default.ExitToApp, contentDescription = null) },
                                    onClick = {
                                        showGroupMenu = false
                                        viewModel.leaveGroup(group.id)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Excluir Grupo", color = RedAction) },
                                    leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = RedAction) },
                                    onClick = {
                                        showGroupMenu = false
                                        viewModel.deleteGroup(group)
                                    }
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 8.dp))
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Create Group Button
            Button(
                onClick = { showCreateGroupDialog = true },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("create_group_btn")
            ) {
                Icon(Icons.Default.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Criar Novo Grupo", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    // --- DIALOG: PEDIDOS E SUGESTÕES DE AMIZADE (Mockup 3) ---
    if (showRequestsAndSuggestionsDialog) {
        Dialog(
            onDismissRequest = { showRequestsAndSuggestionsDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                color = MaterialTheme.colorScheme.background
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 90.dp)
                    ) {
                        // Top Navigation Row
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { showRequestsAndSuggestionsDialog = false }) {
                                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                                    }
                                    Text(
                                        text = "--",
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    BadgedBox(
                                        badge = {
                                            if (pendingRequests.isNotEmpty()) {
                                                Badge(containerColor = Color(0xFFD32F2F)) {
                                                    Text("${pendingRequests.size}", fontSize = 10.sp)
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Notifications,
                                            contentDescription = null,
                                            tint = Color(0xFFD32F2F),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }

                        // Subtitle row: "0 Amigos" and sort/list
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${friends.size} Amigos",
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row {
                                    Icon(Icons.Default.SwapVert, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(Icons.Default.FormatListBulleted, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        // SECTION 0: CONVITES DE GRUPO
                        if (pendingGroupInvites.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Convites de Grupos",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E88E5),
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }

                            items(pendingGroupInvites) { group ->
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = Color(0xFF1E88E5).copy(alpha = 0.08f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E88E5).copy(alpha = 0.35f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 5.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.size(46.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = group.name.take(2).uppercase(),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = group.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = if (group.groupType == "UNILATERAL") "Unilateral (Modo Leitura)" else "Liberal (Live Preview)",
                                                fontSize = 11.sp,
                                                color = Color(0xFF1E88E5)
                                            )
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color(0xFFC75B5B),
                                                modifier = Modifier.clickable { viewModel.declineGroupInvite(group) }
                                            ) {
                                                Text(
                                                    text = "Recusar",
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp)
                                                )
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.clickable { viewModel.acceptGroupInvite(group) }
                                            ) {
                                                Text(
                                                    text = "Aceitar",
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            item {
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                        }

                        // SECTION 1: PEDIDOS DE AMIZADE (Mockup 3)
                        item {
                            Text(
                                text = "Pedidos de Amizade",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        if (pendingRequests.isEmpty()) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "Nenhum pedido pendente no momento.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }
                        } else {
                            items(pendingRequests) { request ->
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 5.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.size(46.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = request.name.take(1).uppercase(),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 18.sp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = request.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = request.subtitleTag,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        // 3 Action Buttons matching Mockup 3: [Aceitar] [Remover] [Bloquear]
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color(0xFFC75B5B),
                                                modifier = Modifier.clickable { viewModel.acceptFriendRequest(request) }
                                            ) {
                                                Text(
                                                    text = "Aceitar",
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp)
                                                )
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color(0xFFC75B5B),
                                                modifier = Modifier.clickable { viewModel.removeFriendRequest(request) }
                                            ) {
                                                Text(
                                                    text = "Remover",
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp)
                                                )
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color(0xFFC75B5B),
                                                modifier = Modifier.clickable { viewModel.blockFriend(request) }
                                            ) {
                                                Text(
                                                    text = "Bloquear",
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // SECTION 2: SUGESTÕES DE AMIZADE (Mockup 3)
                        item {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Sugestões de Amizade",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        items(suggestedFriends) { suggestion ->
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        modifier = Modifier.size(46.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = suggestion.name.take(2).uppercase(),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = suggestion.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = suggestion.subtitleTag,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    // Action Button: [Enviar Pedido]
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFFC75B5B),
                                        modifier = Modifier.clickable { viewModel.sendFriendRequest(suggestion) }
                                    ) {
                                        Text(
                                            text = "Enviar Pedido",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Floating Action Button "+" in Coral / Reddish tint (Mockup 3)
                    FloatingActionButton(
                        onClick = { showAddFriendDialog = true },
                        containerColor = Color(0xFFD34848),
                        contentColor = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(20.dp)
                            .testTag("requests_fab_add_friend")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Adicionar amigo")
                    }
                }
            }
        }
    }

    // --- DIALOG: CRIAR NOVO GRUPO ---
    if (showCreateGroupDialog) {
        var groupName by remember { mutableStateOf("") }
        var groupImageUri by remember { mutableStateOf<String?>(null) }
        var selectedType by remember { mutableStateOf("LIBERAL") } // "UNILATERAL" or "LIBERAL"
        val selectedFriends = remember { mutableStateListOf<Friend>() }

        val groupPhotoPicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            groupImageUri = uri?.toString()
        }

        AlertDialog(
            onDismissRequest = { showCreateGroupDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.GroupAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Criar Novo Grupo", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Group Image Avatar Picker
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .size(64.dp)
                                .clickable {
                                    groupPhotoPicker.launch(
                                        androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (!groupImageUri.isNullOrBlank()) {
                                    AsyncImage(
                                        model = groupImageUri,
                                        contentDescription = "Foto do Grupo",
                                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Default.AddAPhoto,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Text("Foto", fontSize = 9.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { groupName = it },
                        label = { Text("Nome do Grupo") },
                        placeholder = { Text("Ex: Faculdade, Equipe, Projeto") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Tipo do Grupo:", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                    // Unilateral Option
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selectedType == "UNILATERAL") MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = if (selectedType == "UNILATERAL") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedType = "UNILATERAL" }
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = selectedType == "UNILATERAL", onClick = { selectedType = "UNILATERAL" })
                                Text("Unilateral", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Text(
                                text = "Membros enviam notas que ficam em modo leitura para os outros (só interagem com checklists e elementos permitidos).",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 32.dp)
                            )
                        }
                    }

                    // Liberal Option
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selectedType == "LIBERAL") MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = if (selectedType == "LIBERAL") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedType = "LIBERAL" }
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = selectedType == "LIBERAL", onClick = { selectedType = "LIBERAL" })
                                Text("Liberal (Colaborativo)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Text(
                                text = "Todos os membros podem editar livremente em Live Preview e sincronizar com o grupo.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 32.dp)
                            )
                        }
                    }

                    if (friends.isNotEmpty()) {
                        Text("Convidar Amigos:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Column(modifier = Modifier.fillMaxWidth()) {
                            friends.forEach { friend ->
                                val isChecked = selectedFriends.contains(friend)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (isChecked) selectedFriends.remove(friend) else selectedFriends.add(friend)
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { check ->
                                            if (check) selectedFriends.add(friend) else selectedFriends.remove(friend)
                                        }
                                    )
                                    Text(friend.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (groupName.isNotBlank()) {
                            viewModel.createGroup(
                                name = groupName,
                                imageUri = groupImageUri,
                                groupType = selectedType,
                                invitedFriends = selectedFriends.toList()
                            )
                            showCreateGroupDialog = false
                        }
                    },
                    enabled = groupName.isNotBlank()
                ) {
                    Text("Criar Grupo")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateGroupDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // --- DIALOG: ADICIONAR AMIGO POR ID / @USUÁRIO ---
    if (showAddFriendDialog) {
        var friendName by remember { mutableStateOf("") }
        var friendUsername by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddFriendDialog = false },
            title = { Text("Adicionar Novo Amigo", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = friendName,
                        onValueChange = { friendName = it },
                        label = { Text("Nome") },
                        placeholder = { Text("Ex: Lucas") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = friendUsername,
                        onValueChange = { friendUsername = it },
                        label = { Text("@Usuário ou ID (9 dígitos)") },
                        placeholder = { Text("@lucas ou CTR-98721") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (friendName.isNotBlank()) {
                            viewModel.addCustomFriend(friendName, friendUsername)
                            showAddFriendDialog = false
                        }
                    },
                    enabled = friendName.isNotBlank()
                ) {
                    Text("Adicionar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddFriendDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // --- DIALOG: GERENCIAR GRUPO E NOTAS COMPARTILHADAS ---
    selectedGroupForManagement?.let { group ->
        val groupMembers by viewModel.getGroupMembers(group.id).collectAsState(initial = emptyList())
        val groupNotes by viewModel.getNotesForGroup(group.id).collectAsState(initial = emptyList())

        AlertDialog(
            onDismissRequest = { selectedGroupForManagement = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(group.avatarInitials, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(group.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            text = if (group.groupType == "UNILATERAL") "Grupo Unilateral (Leitura para membros)" else "Grupo Liberal (Edição aberta)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Group Notes section
                    Text("Notas Compartilhadas no Grupo (${groupNotes.size}):", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                    if (groupNotes.isEmpty()) {
                        Text(
                            "Nenhuma nota compartilhada neste grupo ainda. Abra uma nota e selecione 'Compartilhar' no menu.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        groupNotes.forEach { note ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedGroupForManagement = null
                                        onOpenNote(note)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Public, contentDescription = null, tint = Color(0xFF1E88E5), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(note.title, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text("Autor: ${note.sharedOwnerName ?: "Você"}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(
                                        onClick = { viewModel.unshareNoteFromGroup(note) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.LinkOff, contentDescription = "Remover do grupo", tint = RedAction, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Members Section
                    Text("Membros do Grupo (${groupMembers.size}):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    groupMembers.forEach { member ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(member.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                if (member.isAdmin) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("(Admin)", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (!member.isAdmin) {
                                TextButton(
                                    onClick = { viewModel.removeGroupMember(group.id, member.id, member.friendId) },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                ) {
                                    Text("Remover", color = RedAction, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedGroupForManagement = null }) {
                    Text("Fechar")
                }
            }
        )
    }

    // --- DIALOG: AMIGOS BLOQUEADOS ---
    if (showBlockedFriendsDialog) {
        AlertDialog(
            onDismissRequest = { showBlockedFriendsDialog = false },
            title = { Text("Amigos Bloqueados", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (blockedFriends.isEmpty()) {
                        Text("Nenhum usuário bloqueado.", fontSize = 12.sp)
                    } else {
                        blockedFriends.forEach { blocked ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(blocked.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(blocked.username, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Button(
                                    onClick = { viewModel.unblockFriend(blocked) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("Desbloquear", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBlockedFriendsDialog = false }) {
                    Text("Fechar")
                }
            }
        )
    }
}
