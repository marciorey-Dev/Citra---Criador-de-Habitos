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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
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
import coil.compose.AsyncImage
import com.example.data.auth.AuthRepository
import com.example.ui.MainViewModel
import com.example.ui.screens.stats.StatsScreen
import com.example.ui.theme.GreenAccent
import com.example.ui.theme.RedAction
import com.example.ui.components.AppTutorialDialog
import com.example.ui.components.BackupDialog
import com.example.ui.components.CloudSyncedDataDialog
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.LatexManualDialog
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: MainViewModel,
    authRepository: AuthRepository,
    onOpenSettings: () -> Unit,
    onOpenAuthGate: () -> Unit,
    onNavigateToNotes: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }

    val user by authRepository.currentUser.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    val habits by viewModel.habits.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val achievements by viewModel.achievements.collectAsState()
    val focusSessions by viewModel.focusSessions.collectAsState()
    val keepScreenOn by viewModel.keepScreenOn.collectAsState()
    val notificationSettings by viewModel.notificationSettings.collectAsState()

    var selectedSubTab by remember { mutableStateOf(0) } // 0: Estatísticas, 1: Dados

    // Modals
    var showEditNameDialog by remember { mutableStateOf(false) }
    var showImageSourceDialog by remember { mutableStateOf(false) }
    var showLatexManualDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var showCloudDataDialog by remember { mutableStateOf(false) }
    var resetTarget by remember { mutableStateOf<String?>(null) } // "tasks", "habits", "notes", "all"

    // Photo picker launcher (Gallery)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val uid = user?.uid ?: "guest"
                    val avatarDir = File(context.filesDir, "avatars").apply { mkdirs() }
                    val targetFile = File(avatarDir, "user_${uid}.jpg")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    authRepository.updateProfile(photoUrl = targetFile.absolutePath)
                    Toast.makeText(context, "Foto de perfil atualizada!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Erro ao salvar imagem local.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Camera capture launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            coroutineScope.launch {
                try {
                    val uid = user?.uid ?: "guest"
                    val avatarDir = File(context.filesDir, "avatars").apply { mkdirs() }
                    val targetFile = File(avatarDir, "user_${uid}.jpg")
                    targetFile.outputStream().use { out ->
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                    }
                    authRepository.updateProfile(photoUrl = targetFile.absolutePath)
                    Toast.makeText(context, "Foto capturada com sucesso!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Erro ao salvar foto da câmera.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. TOP PROFILE HEADER CARD
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                            RoundedCornerShape(22.dp)
                        )
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // User Info on Left
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = user?.displayName ?: "Viajante Citra",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    IconButton(
                                        onClick = { showEditNameDialog = true },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Editar Nome",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(17.dp)
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
                                    shadowElevation = 3.dp,
                                    modifier = Modifier
                                        .size(78.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), CircleShape)
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
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }

                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    shadowElevation = 2.dp,
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clickable { showImageSourceDialog = true }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = "Alterar Foto",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(5.dp)
                                    )
                                }
                            }
                        }

                        // Cozy Citrus Divider
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                modifier = Modifier.padding(horizontal = 10.dp)
                            ) {
                                Text(
                                    text = "🍊 Citra • Eu 🍊",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }

                        // Guest mode warning & sign in link
                        if (user?.isGuest == true) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenAuthGate() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
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

            // 2. SEGMENTED SUB-TABS SELECTOR: [Estatísticas] [Dados]
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            0 to Pair("Estatísticas", Icons.Default.BarChart),
                            1 to Pair("Dados", Icons.Default.Storage)
                        ).forEach { (index, pair) ->
                            val isSelected = selectedSubTab == index
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                                shadowElevation = if (isSelected) 2.dp else 0.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { selectedSubTab = index }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = pair.second,
                                        contentDescription = null,
                                        tint = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = pair.first,
                                        fontSize = 13.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                        color = if (isSelected) {
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
            }

            // 4. SUB-TAB CONTENT
            when (selectedSubTab) {
                0 -> {
                    // ESTATÍSTICAS
                    item {
                        StatsScreen(viewModel = viewModel)
                    }
                }

                1 -> {
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
                                            "Conecte sua conta para fazer backup contínuo na nuvem.",
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
                                                        val settingsMap = viewModel.getSettingsMapForCloud()
                                                        val financesJson = viewModel.exportFinancesJson()
                                                        authRepository.syncAllUserDataToCloud(
                                                            tasks = tasks,
                                                            habits = habits,
                                                            notes = notes,
                                                            categories = categories,
                                                            focusSessions = focusSessions,
                                                            achievements = achievements,
                                                            settingsMap = settingsMap,
                                                            financesJson = financesJson
                                                        )
                                                        isSyncingNow = false
                                                        Toast.makeText(context, "Dados, estatísticas e finanças sincronizados!", Toast.LENGTH_SHORT).show()
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
                                                            viewModel.applyCloudSettings(cloudData.settings)
                                                            cloudData.backupJson?.let { viewModel.restoreBackupJson(it) }
                                                            cloudData.notesJson?.let { viewModel.importNotesJson(it) }
                                                            cloudData.financesJson?.let { viewModel.restoreFinancesJson(it) }
                                                            Toast.makeText(context, "Dados, estatísticas e finanças restaurados da nuvem!", Toast.LENGTH_SHORT).show()
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

                                        FilledTonalButton(
                                            onClick = { showCloudDataDialog = true },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.CloudQueue, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Monitoramento do Armazenamento em Nuvem", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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

                            // Local Backup Card
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
                                        text = "As notas do Citra são documentos Markdown (.md) puros gravados em 'Documentos/Citra Docs/' na raiz do seu celular. Você tem 100% de posse dos seus arquivos e pode abri-los no Obsidian, Word ou em qualquer outro programa.",
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
                                    .clickable {
                                        viewModel.resetAllTutorials()
                                        viewModel.startTutorial(com.example.ui.tutorial.CitraTutorialArea.STATS)
                                        Toast.makeText(context, "Tutoriais reativados! Navegue pelas abas para ver cada guia.", Toast.LENGTH_SHORT).show()
                                    }
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

                            // Action: Manual de Fórmulas LaTeX
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("profile_latex_manual_btn")
                                    .clickable { showLatexManualDialog = true }
                            ) {
                                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Functions, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Manual de Fórmulas e Cálculos LaTeX", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("Regras de cálculo, sintaxe, todos os comandos e fórmulas prontas para copiar", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                                "Estou usando o Citra para focar, organizar tarefas, hábitos e notas! Baixe agora!"
                                            )
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Compartilhar Citra"))
                                    }
                            ) {
                                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Compartilhar o Citra", fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
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

    if (showLatexManualDialog) {
        LatexManualDialog(onDismiss = { showLatexManualDialog = false })
    }

    if (showBackupDialog) {
        BackupDialog(
            onExportBackup = { viewModel.exportBackupJson() },
            onRestoreBackup = { json -> viewModel.restoreBackupJson(json) },
            onDismiss = { showBackupDialog = false }
        )
    }

    if (showCloudDataDialog) {
        CloudSyncedDataDialog(
            authRepository = authRepository,
            viewModel = viewModel,
            onDismiss = { showCloudDataDialog = false }
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
                "ATENÇÃO: Isso irá apagar permanentemente todas as tarefas, hábitos, notas, histórico de foco Arancia e conquistas. Deseja continuar?",
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
