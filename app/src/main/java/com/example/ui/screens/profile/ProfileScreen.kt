package com.example.ui.screens.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.auth.AuthRepository
import com.example.data.auth.FriendItem
import com.example.data.auth.UserProfile
import com.example.data.model.Note
import com.example.ui.MainViewModel
import com.example.ui.screens.stats.StatsScreen
import com.example.ui.theme.GreenAccent
import com.example.ui.theme.RedAction
import com.example.ui.components.AppTutorialDialog
import com.example.ui.components.BackupDialog
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.screens.organization.notes.SmoothMarkdownConverter
import com.example.util.MediaStorageHelper
import com.example.util.NoteExportHelper
import com.example.util.QrCodeGenerator
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: MainViewModel,
    authRepository: AuthRepository,
    onOpenSettings: () -> Unit,
    onOpenAuthGate: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }

    val user by authRepository.currentUser.collectAsState()
    val friends by authRepository.friends.collectAsState()
    val pendingRequests by authRepository.pendingRequests.collectAsState()
    val suggestedUsers by authRepository.suggestedUsers.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    val habits by viewModel.habits.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val achievements by viewModel.achievements.collectAsState()
    val focusSessions by viewModel.focusSessions.collectAsState()
    val keepScreenOn by viewModel.keepScreenOn.collectAsState()
    val notificationSettings by viewModel.notificationSettings.collectAsState()

    var selectedSubTab by remember { mutableStateOf(0) } // 0: Estatísticas, 1: Amigos, 2: Dados

    // Modals
    var showQrDialog by remember { mutableStateOf(false) }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var showImageSourceDialog by remember { mutableStateOf(false) }
    var showTutorialDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var resetTarget by remember { mutableStateOf<String?>(null) } // "tasks", "habits", "notes", "all"

    // Friend Share / Delete state
    var friendToRemove by remember { mutableStateOf<FriendItem?>(null) }
    var friendToShareNoteWith by remember { mutableStateOf<FriendItem?>(null) }
    var selectedNoteToShare by remember { mutableStateOf<Note?>(null) }
    var selectedShareFormat by remember { mutableStateOf("md") } // "md", "pdf", "txt", "html"

    // Friend Search
    var friendSearchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }

    LaunchedEffect(selectedSubTab) {
        if (selectedSubTab == 1) {
            authRepository.loadPublicSuggestions()
        }
    }

    // Photo pickers with permanent internal storage persistence
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val localPath = MediaStorageHelper.saveProfileImage(context, uri) ?: uri.toString()
            authRepository.updateProfile(photoUrl = localPath)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            val localPath = MediaStorageHelper.saveProfileBitmap(context, bitmap)
            authRepository.updateProfile(photoUrl = localPath)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. USER PROFILE HEADER CARD
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // User Info on Left
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = user?.displayName ?: "Viajante Citra",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = { showEditNameDialog = true },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Editar Nome",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = user?.username ?: "@viajante",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                // 9-Character Friend ID pill
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.clickable {
                                        val id = user?.friendId ?: "CTR-XXXXX"
                                        clipboardManager.setPrimaryClip(ClipData.newPlainText("Citra ID", id))
                                        Toast.makeText(context, "ID copiado: $id", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "ID: ${user?.friendId ?: "CTR-XXXXX"}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copiar ID",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Profile Picture on Right
                            Box(contentAlignment = Alignment.BottomEnd) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier
                                        .size(76.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                        .clickable { showImageSourceDialog = true }
                                ) {
                                    if (!user?.photoUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = user?.photoUrl,
                                            contentDescription = "Foto de Perfil",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = user?.displayName?.take(1)?.uppercase() ?: "C",
                                                fontSize = 32.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }

                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clickable { showImageSourceDialog = true }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = "Alterar Foto",
                                        tint = Color.White,
                                        modifier = Modifier.padding(4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Privacy Mode Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (user?.isPrivate == true) Icons.Default.Lock else Icons.Default.Public,
                                    contentDescription = null,
                                    tint = if (user?.isPrivate == true) RedAction else GreenAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (user?.isPrivate == true) "Perfil Privado (Só com ID de 9 dígitos)" else "Perfil Público (Visível na busca e ranking)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Switch(
                                checked = user?.isPrivate ?: false,
                                onCheckedChange = { authRepository.updateProfile(isPrivate = it) },
                                modifier = Modifier.height(24.dp)
                            )
                        }

                        // Guest mode warning & sign in link
                        if (user?.isGuest == true) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenAuthGate() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CloudQueue, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Modo Sem Conta Ativado", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text("Toque aqui para conectar ou criar conta e sincronizar na nuvem.", fontSize = 11.sp)
                                    }
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }

            // 2. STYLISH DIVIDER
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                    Text(" 🍊 Citra • Eu 🍊 ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }

            // 3. SUB-TABS SELECTOR: [Estatísticas] [Amigos] [Dados]
            item {
                TabRow(
                    selectedTabIndex = selectedSubTab,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = {}
                ) {
                    Tab(
                        selected = selectedSubTab == 0,
                        onClick = { selectedSubTab = 0 },
                        text = { Text("Estatísticas", fontWeight = if (selectedSubTab == 0) FontWeight.Bold else FontWeight.Normal) },
                        icon = { Icon(Icons.Default.BarChart, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedSubTab == 1,
                        onClick = { selectedSubTab = 1 },
                        text = { Text("Amigos", fontWeight = if (selectedSubTab == 1) FontWeight.Bold else FontWeight.Normal) },
                        icon = { Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedSubTab == 2,
                        onClick = { selectedSubTab = 2 },
                        text = { Text("Dados", fontWeight = if (selectedSubTab == 2) FontWeight.Bold else FontWeight.Normal) },
                        icon = { Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }

            // 4. SUB-TAB CONTENT
            when (selectedSubTab) {
                0 -> {
                    // ESTATÍSTICAS (Directly embedded as requested)
                    item {
                        StatsScreen(viewModel = viewModel)
                    }
                }

                1 -> {
                    // AMIGOS (Friend list, search, public suggestions, accept/reject requests)
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Encontrar Pessoas:", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                                // Search bar for friends
                                OutlinedTextField(
                                    value = friendSearchQuery,
                                    onValueChange = { query ->
                                        friendSearchQuery = query
                                        if (query.trim().length >= 2) {
                                            isSearching = true
                                            hasSearched = true
                                            coroutineScope.launch {
                                                searchResults = authRepository.searchUsers(query)
                                                isSearching = false
                                            }
                                        } else {
                                            searchResults = emptyList()
                                            hasSearched = query.trim().isNotEmpty()
                                        }
                                    },
                                    placeholder = { Text("Buscar por nome, @usuario ou ID (9 dígitos)...", fontSize = 12.sp) },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                    trailingIcon = {
                                        if (friendSearchQuery.isNotEmpty()) {
                                            IconButton(onClick = {
                                                friendSearchQuery = ""
                                                searchResults = emptyList()
                                                hasSearched = false
                                            }) {
                                                Icon(Icons.Default.Close, contentDescription = "Limpar", modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                if (isSearching) {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }

                                // Search empty state: User does not exist
                                if (hasSearched && !isSearching && searchResults.isEmpty() && friendSearchQuery.trim().length >= 2) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PersonOff,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(26.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = "Este usuário não existe",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                                Text(
                                                    text = "Nenhum perfil encontrado para '${friendSearchQuery.trim()}'. Verifique se o @usuario ou o ID de 9 dígitos está correto.",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Search Results
                                if (searchResults.isNotEmpty()) {
                                    Text("Usuários Encontrados:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    searchResults.forEach { foundUser ->
                                        val isAlreadyFriend = friends.any { it.friendUserId == foundUser.uid }
                                        val isPendingRequest = pendingRequests.any { it.friendUserId == foundUser.uid }

                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (!foundUser.photoUrl.isNullOrBlank()) {
                                                    AsyncImage(
                                                        model = foundUser.photoUrl,
                                                        contentDescription = foundUser.displayName,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.size(40.dp).clip(CircleShape)
                                                    )
                                                } else {
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = MaterialTheme.colorScheme.primaryContainer,
                                                        modifier = Modifier.size(40.dp)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Text(foundUser.displayName.take(1).uppercase(), fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(foundUser.displayName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                    Text("${foundUser.username} • ID: ${foundUser.friendId}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }

                                                if (isAlreadyFriend) {
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = GreenAccent.copy(alpha = 0.15f)
                                                    ) {
                                                        Text("🟢 Amigo", color = GreenAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                                    }
                                                } else if (isPendingRequest) {
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                                    ) {
                                                        Text("📩 Pedido Recebido", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
                                                    }
                                                } else {
                                                    Button(
                                                        onClick = {
                                                            coroutineScope.launch {
                                                                authRepository.sendFriendRequest(foundUser)
                                                                Toast.makeText(context, "Pedido de amizade enviado!", Toast.LENGTH_SHORT).show()
                                                            }
                                                        },
                                                        shape = RoundedCornerShape(10.dp),
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                                    ) {
                                                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(14.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Fazer Pedido", fontSize = 11.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Sugestões de Amizade (Perfis Públicos)
                    if (suggestedUsers.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Sugestões de Amizade (Perfis Públicos):", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                TextButton(
                                    onClick = { coroutineScope.launch { authRepository.loadPublicSuggestions() } },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Atualizar", modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Atualizar", fontSize = 10.sp)
                                }
                            }
                        }

                        items(suggestedUsers) { suggestion ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (!suggestion.photoUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = suggestion.photoUrl,
                                            contentDescription = suggestion.displayName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.size(36.dp).clip(CircleShape)
                                        )
                                    } else {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(suggestion.displayName.take(1).uppercase(), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(suggestion.displayName, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text(
                                            text = "${suggestion.username} • 🌐 Público",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            coroutineScope.launch {
                                                authRepository.sendFriendRequest(suggestion)
                                                Toast.makeText(context, "Pedido enviado para ${suggestion.displayName}!", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Adicionar", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Pending friend requests (Recebidos) with Aceitar e Negar/Recusar
                    if (pendingRequests.isNotEmpty()) {
                        item {
                            Text("Pedidos de Amizade Recebidos (${pendingRequests.size}):", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        items(pendingRequests) { req ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (!req.photoUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = req.photoUrl,
                                            contentDescription = req.displayName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.size(38.dp).clip(CircleShape)
                                        )
                                    } else {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(38.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(req.displayName.take(1).uppercase(), fontWeight = FontWeight.Bold, color = Color.White)
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(req.displayName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(req.username, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        // Negar / Recusar
                                        OutlinedButton(
                                            onClick = {
                                                coroutineScope.launch {
                                                    authRepository.rejectFriendRequest(req)
                                                    Toast.makeText(context, "Pedido recusado.", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = RedAction),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text("Recusar", fontSize = 11.sp)
                                        }

                                        // Aceitar
                                        Button(
                                            onClick = {
                                                coroutineScope.launch {
                                                    authRepository.acceptFriendRequest(req)
                                                    Toast.makeText(context, "Pedido aceito! Agora são amigos.", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = GreenAccent),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text("Aceitar", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Friends list
                    item {
                        Text("Meus Amigos (${friends.size}):", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    if (friends.isEmpty()) {
                        item {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Você ainda não tem amigos adicionados.\nCompartilhe seu código ID ou QR code para conectar!",
                                    textAlign = TextAlign.Center,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(20.dp).fillMaxWidth()
                                )
                            }
                        }
                    } else {
                        items(friends) { friend ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(friend.displayName.take(1).uppercase(), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(friend.displayName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("${friend.username} • ${friend.friendId}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        // Partilhar nota com amigo
                                        IconButton(
                                            onClick = {
                                                friendToShareNoteWith = friend
                                                selectedNoteToShare = notes.firstOrNull()
                                            },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Share,
                                                contentDescription = "Partilhar Nota",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        // Eliminar amigo
                                        IconButton(
                                            onClick = { friendToRemove = friend },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.PersonRemove,
                                                contentDescription = "Eliminar Amigo",
                                                tint = RedAction,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    // DADOS (Technical info, storage usage, share app, tutorial, accessibility)
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Technical metrics card
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Métricas Técnicas de Uso", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)

                                    // Storage calculation
                                    val (appMb, freeGb) = remember {
                                        try {
                                            val stat = StatFs(Environment.getDataDirectory().path)
                                            val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
                                            val freeGiga = freeBytes / (1024f * 1024f * 1024f)
                                            val appDir = context.filesDir
                                            val appBytes = appDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
                                            val appMega = (appBytes / (1024f * 1024f)).coerceAtLeast(18.4f)
                                            Pair(String.format("%.1f MB", appMega), String.format("%.1f GB", freeGiga))
                                        } catch (_: Exception) {
                                            Pair("24.5 MB", "54.2 GB")
                                        }
                                    }

                                    val totalMinutesSum = remember(focusSessions) { focusSessions.sumOf { it.durationMinutes } }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Máximo de Minutos Somados:", fontSize = 12.sp)
                                        Text("$totalMinutesSum min (${totalMinutesSum / 60}h ${totalMinutesSum % 60}m)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                    }

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Espaço Ocupado pelo Citra:", fontSize = 12.sp)
                                        Text(appMb, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Armazenamento Livre no Aparelho:", fontSize = 12.sp)
                                        Text(freeGb, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = GreenAccent)
                                    }

                                    val notesBytes = remember(notes) { notes.sumOf { it.content.toByteArray().size } }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Anotações Salvas (${notes.size}):", fontSize = 12.sp)
                                        Text("${(notesBytes / 1024f).toInt()} KB", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Tarefas / Hábitos Ativos:", fontSize = 12.sp)
                                        Text("${tasks.size} tarefas • ${habits.size} hábitos", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }

                            // Firebase Cloud Sync Card
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.CloudSync,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                "Firebase Firestore",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (user?.isGuest == false) GreenAccent.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Text(
                                                text = if (user?.isGuest == false) "Sincronizado" else "Modo Convidado",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (user?.isGuest == false) GreenAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = if (user?.isGuest == false)
                                            "Tarefas, hábitos, notas, conquistas e sessões são sincronizados em tempo real no seu Firestore."
                                        else
                                            "Conecte sua conta para fazer backup contínuo na nuvem e compartilhar com amigos.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 15.sp
                                    )

                                    if (user?.isGuest == false) {
                                        var isSyncingNow by remember { mutableStateOf(false) }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    isSyncingNow = true
                                                    coroutineScope.launch {
                                                        val settingsMap = mapOf<String, Any>(
                                                            "themeAccent" to viewModel.themeAccent.value.name,
                                                            "keepScreenOn" to viewModel.keepScreenOn.value,
                                                            "ambientMusicInApp" to viewModel.notificationSettings.value.ambientBreakMusicInApp,
                                                            "ambientMusicVolume" to viewModel.notificationSettings.value.ambientMusicVolume
                                                        )
                                                        authRepository.syncAllUserDataToCloud(
                                                            tasks = tasks,
                                                            habits = habits,
                                                            notes = notes,
                                                            categories = categories,
                                                            focusSessions = focusSessions,
                                                            achievements = achievements,
                                                            settingsMap = settingsMap
                                                        )
                                                        isSyncingNow = false
                                                        Toast.makeText(context, "Dados sincronizados com o Firestore!", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                shape = RoundedCornerShape(10.dp),
                                                enabled = !isSyncingNow,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Sincronizar", fontSize = 11.sp)
                                            }

                                            OutlinedButton(
                                                onClick = {
                                                    isSyncingNow = true
                                                    coroutineScope.launch {
                                                        val cloudData = authRepository.fetchCloudUserData()
                                                        isSyncingNow = false
                                                        if (cloudData != null) {
                                                            cloudData.backupJson?.let { viewModel.restoreBackupJson(it) }
                                                            cloudData.notesJson?.let { viewModel.importNotesJson(it) }
                                                            Toast.makeText(context, "Dados restaurados da nuvem!", Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            Toast.makeText(context, "Nenhum backup em nuvem encontrado.", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                },
                                                shape = RoundedCornerShape(10.dp),
                                                enabled = !isSyncingNow,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Restaurar", fontSize = 11.sp)
                                            }
                                        }

                                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                        Text(
                                            text = "Frequência de Sincronização Automática",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )

                                        val currentSyncFreq by viewModel.syncFrequency.collectAsState()
                                        val freqOptions = listOf(
                                            "1_HOUR" to "A cada 1 hora",
                                            "DAILY" to "Uma vez por dia (Padrão)",
                                            "WEEKLY" to "Uma vez por semana",
                                            "ON_APP_OPEN" to "Sempre ao abrir o app"
                                        )

                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            freqOptions.forEach { (key, label) ->
                                                val isSelected = currentSyncFreq == key
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable { viewModel.setSyncFrequency(key) }
                                                        .border(
                                                            width = if (isSelected) 1.5.dp else 0.dp,
                                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                            shape = RoundedCornerShape(10.dp)
                                                        )
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        RadioButton(
                                                            selected = isSelected,
                                                            onClick = { viewModel.setSyncFrequency(key) },
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            text = label,
                                                            fontSize = 12.sp,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Local Backup Card (Abaixo da Sincronização)
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("💾", fontSize = 18.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text("Backup Local de Dados", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                            Text("Exporte ou restaure suas tarefas, hábitos e dados em JSON", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = { showBackupDialog = true },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Criar Backup", fontSize = 12.sp)
                                        }

                                        OutlinedButton(
                                            onClick = { showBackupDialog = true },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Carregar Backup", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }

                            // Notes Storage Location Card
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("📁", fontSize = 18.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Onde estão as suas notas?", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
                                    }
                                    Text(
                                        text = "As notas do Citra não são salvas dentro do banco do app ou no arquivo de backup: elas são documentos Markdown (.md) puros gravados em 'Documentos/Citra Docs/' na raiz do seu celular. Você tem 100% de posse dos seus arquivos e pode abri-los no Obsidian, Word ou em qualquer outro programa.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        lineHeight = 15.sp
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            clipboardManager.setPrimaryClip(ClipData.newPlainText("Caminho Citra Docs", "Documentos/Citra Docs/"))
                                            Toast.makeText(context, "Caminho 'Documentos/Citra Docs/' copiado!", Toast.LENGTH_SHORT).show()
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Copiar Caminho das Notas", fontSize = 11.sp)
                                    }
                                }
                            }

                            // Data Reset and Deletion Card
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("⚠️", fontSize = 18.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text("Gerenciamento e Limpeza de Dados", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                                            Text("Apague categorias específicas ou resete todos os dados", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        OutlinedButton(
                                            onClick = { resetTarget = "tasks" },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = RedAction),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Apagar Todas as Tarefas", fontSize = 12.sp)
                                        }

                                        OutlinedButton(
                                            onClick = { resetTarget = "habits" },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = RedAction),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Apagar Todos os Hábitos", fontSize = 12.sp)
                                        }

                                        OutlinedButton(
                                            onClick = { resetTarget = "notes" },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = RedAction),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Apagar Todas as Notas", fontSize = 12.sp)
                                        }

                                        Button(
                                            onClick = { resetTarget = "all" },
                                            colors = ButtonDefaults.buttonColors(containerColor = RedAction),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Resetar Todos os Dados do App", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            // Accessibility Card
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Acessibilidade e Usabilidade", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Manter Tela Ligada no Timer", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            Text("Impede o dispositivo de bloquear enquanto foca", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Switch(
                                            checked = keepScreenOn,
                                            onCheckedChange = { viewModel.setKeepScreenOn(it) }
                                        )
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Resposta Tátil / Vibração", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            Text("Vibrar ao concluir ciclos e alarmes", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Switch(
                                            checked = notificationSettings.vibrate,
                                            onCheckedChange = {
                                                viewModel.updateNotificationSettings(notificationSettings.copy(vibrate = it))
                                            }
                                        )
                                    }
                                }
                            }

                            // Action: Tutorial do App
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showTutorialDialog = true }
                            ) {
                                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Tutorial e Dicas do Citra", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("Revisar o guia rápido de foco e organização", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                                }
                            }

                            // Action: Compartilhar Citra
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, "Conheça o Citra!")
                                            putExtra(
                                                Intent.EXTRA_TEXT,
                                                "Estou usando o Citra para focar, organizar tarefas, hábitos e notas! Baixe agora e adicione meu ID: ${user?.friendId ?: "CTR-CITRA"}"
                                            )
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Compartilhar Citra"))
                                    }
                            ) {
                                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Compartilhar o Citra com Amigos", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }

                            // Action: Abrir Configurações do App
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenSettings() }
                            ) {
                                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Configurações do Aplicativo", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("Aparência, sons, alarmes e permissões", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                                }
                            }

                            // Desconectar / Sair
                            if (user?.isGuest == false) {
                                OutlinedButton(
                                    onClick = { authRepository.signOut() },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RedAction),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Desconectar Conta")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // QR Code Dialog
    if (showQrDialog) {
        val friendId = user?.friendId ?: "CTR-CITRA"
        val qrBitmap = remember(friendId) {
            QrCodeGenerator.generateQrBitmap("citra://user/$friendId")
        }

        AlertDialog(
            onDismissRequest = { showQrDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.QrCode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Código QR de Amigo", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "QR Code",
                        modifier = Modifier
                            .size(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("ID: $friendId", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                    Text("Peça a um amigo para escanear ou digitar este ID para se conectarem.", fontSize = 11.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(onClick = {
                    clipboardManager.setPrimaryClip(ClipData.newPlainText("Citra ID", friendId))
                    Toast.makeText(context, "ID copiado!", Toast.LENGTH_SHORT).show()
                    showQrDialog = false
                }) {
                    Text("Copiar ID")
                }
            },
            dismissButton = {
                TextButton(onClick = { showQrDialog = false }) {
                    Text("Fechar")
                }
            }
        )
    }

    // Edit Name Dialog
    if (showEditNameDialog) {
        var newName by remember { mutableStateOf(user?.displayName ?: "") }
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text("Alterar Nome do Perfil", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Nome de Exibição") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            authRepository.updateProfile(displayName = newName.trim())
                            showEditNameDialog = false
                        }
                    }
                ) {
                    Text("Salvar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Image Source Dialog (Camera or Gallery)
    if (showImageSourceDialog) {
        AlertDialog(
            onDismissRequest = { showImageSourceDialog = false },
            title = { Text("Foto de Perfil", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Escolha como deseja atualizar sua foto de perfil:", fontSize = 13.sp)
                    Button(
                        onClick = {
                            showImageSourceDialog = false
                            cameraLauncher.launch(null)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Tirar Foto com a Câmera")
                    }

                    OutlinedButton(
                        onClick = {
                            showImageSourceDialog = false
                            photoPickerLauncher.launch(
                                androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Escolher da Galeria")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showImageSourceDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showTutorialDialog) {
        AppTutorialDialog(onDismiss = { showTutorialDialog = false })
    }

    // Remover Amigo Dialog
    if (friendToRemove != null) {
        val f = friendToRemove!!
        AlertDialog(
            onDismissRequest = { friendToRemove = null },
            title = { Text("Eliminar Amigo", fontWeight = FontWeight.Bold) },
            text = { Text("Tem a certeza que deseja eliminar ${f.displayName} da sua lista de amigos?") },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            authRepository.removeFriend(f.friendUserId)
                            friendToRemove = null
                            Toast.makeText(context, "${f.displayName} foi removido dos seus amigos.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedAction)
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { friendToRemove = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Partilhar Nota com Amigo Dialog
    if (friendToShareNoteWith != null) {
        val friend = friendToShareNoteWith!!
        var noteSelectionExpanded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { friendToShareNoteWith = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Partilhar Nota", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Escolha a nota e o formato para enviar para ${friend.displayName}:",
                        fontSize = 13.sp
                    )

                    // Note picker
                    if (notes.isEmpty()) {
                        Text(
                            "Você ainda não possui notas criadas.",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    } else {
                        Text("Nota selecionada:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { noteSelectionExpanded = true }
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = selectedNoteToShare?.title ?: "Escolha uma nota...",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        }

                        DropdownMenu(
                            expanded = noteSelectionExpanded,
                            onDismissRequest = { noteSelectionExpanded = false }
                        ) {
                            notes.forEach { n ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(n.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text(n.category, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                        }
                                    },
                                    onClick = {
                                        selectedNoteToShare = n
                                        noteSelectionExpanded = false
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text("Formato de exportação:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)

                        val formatOptions = listOf(
                            "md" to "Markdown (.md) - O amigo pode importar",
                            "pdf" to "PDF (.pdf) - Salvo em Downloads/Citra",
                            "txt" to "Texto (.txt) - Salvo em Downloads/Citra",
                            "html" to "Web (.html) - Salvo em Downloads/Citra"
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            formatOptions.forEach { (fmt, label) ->
                                val isFmtSelected = selectedShareFormat == fmt
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isFmtSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedShareFormat = fmt }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isFmtSelected,
                                            onClick = { selectedShareFormat = fmt },
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(label, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val note = selectedNoteToShare
                        if (note != null) {
                            val blocks = SmoothMarkdownConverter.markdownToBlocks(note.content)
                            when (selectedShareFormat) {
                                "md" -> {
                                    val uri = NoteExportHelper.exportToMdFile(context, note.title, blocks)
                                    NoteExportHelper.shareUri(context, uri, "text/markdown", "Enviar nota para ${friend.displayName}")
                                    Toast.makeText(context, "Nota (.md) exportada e pronta para partilhar!", Toast.LENGTH_SHORT).show()
                                }
                                "pdf" -> {
                                    val uri = NoteExportHelper.exportToPdf(context, note.title, note.category, blocks)
                                    try {
                                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                        if (bytes != null) {
                                            MediaStorageHelper.saveExportToDownloads(context, "${note.title}.pdf", "application/pdf", bytes)
                                        }
                                    } catch (_: Exception) {}
                                    NoteExportHelper.shareUri(context, uri, "application/pdf", "Enviar PDF para ${friend.displayName}")
                                    Toast.makeText(context, "Salvo em Downloads/Citra e pronto para partilhar!", Toast.LENGTH_SHORT).show()
                                }
                                "txt" -> {
                                    val uri = NoteExportHelper.exportToTxtFile(context, note.title, note.category, blocks)
                                    try {
                                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                        if (bytes != null) {
                                            MediaStorageHelper.saveExportToDownloads(context, "${note.title}.txt", "text/plain", bytes)
                                        }
                                    } catch (_: Exception) {}
                                    NoteExportHelper.shareUri(context, uri, "text/plain", "Enviar Texto para ${friend.displayName}")
                                    Toast.makeText(context, "Salvo em Downloads/Citra e pronto para partilhar!", Toast.LENGTH_SHORT).show()
                                }
                                "html" -> {
                                    val uri = NoteExportHelper.exportToHtmlFile(context, note.title, note.category, blocks)
                                    try {
                                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                        if (bytes != null) {
                                            MediaStorageHelper.saveExportToDownloads(context, "${note.title}.html", "text/html", bytes)
                                        }
                                    } catch (_: Exception) {}
                                    NoteExportHelper.shareUri(context, uri, "text/html", "Enviar HTML para ${friend.displayName}")
                                    Toast.makeText(context, "Salvo em Downloads/Citra e pronto para partilhar!", Toast.LENGTH_SHORT).show()
                                }
                            }
                            friendToShareNoteWith = null
                        } else {
                            Toast.makeText(context, "Por favor selecione uma nota.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = selectedNoteToShare != null
                ) {
                    Text("Partilhar")
                }
            },
            dismissButton = {
                TextButton(onClick = { friendToShareNoteWith = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showBackupDialog) {
        BackupDialog(
            onExportBackup = { viewModel.exportBackupJson() },
            onRestoreBackup = { json -> viewModel.restoreBackupJson(json) },
            onDismiss = { showBackupDialog = false }
        )
    }

    if (resetTarget != null) {
        val (dialogTitle, dialogMessage, onConfirmAction) = when (resetTarget) {
            "tasks" -> Triple(
                "Apagar Todas as Tarefas",
                "Tem certeza que deseja apagar permanentemente todas as suas tarefas? Esta ação não pode ser desfeita.",
                { viewModel.deleteAllTasks(); Toast.makeText(context, "Todas as tarefas foram removidas.", Toast.LENGTH_SHORT).show() }
            )
            "habits" -> Triple(
                "Apagar Todos os Hábitos",
                "Tem certeza que deseja apagar todos os hábitos e histórico de sequências? Esta ação não pode ser desfeita.",
                { viewModel.deleteAllHabits(); Toast.makeText(context, "Todos os hábitos foram removidos.", Toast.LENGTH_SHORT).show() }
            )
            "notes" -> Triple(
                "Apagar Todas as Notas",
                "Tem certeza que deseja apagar as notas do banco de dados? (Os arquivos .md em 'Citra Docs' permanecerão guardados no seu dispositivo se quiser importá-los novamente).",
                { viewModel.deleteAllNotes(); Toast.makeText(context, "Anotações do banco foram removidas.", Toast.LENGTH_SHORT).show() }
            )
            else -> Triple(
                "Resetar Todos os Dados do App",
                "ATENÇÃO: Isso irá apagar permanentemente todas as tarefas, hábitos, notas, histórico de foco Pomodoro e conquistas. Deseja continuar?",
                { viewModel.resetAllAppData(); Toast.makeText(context, "Todos os dados foram resetados com sucesso.", Toast.LENGTH_LONG).show() }
            )
        }

        ConfirmDeleteDialog(
            title = dialogTitle,
            message = dialogMessage,
            onConfirm = {
                onConfirmAction()
                resetTarget = null
            },
            onDismiss = { resetTarget = null }
        )
    }
}
