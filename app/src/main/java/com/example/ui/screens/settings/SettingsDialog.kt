package com.example.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.NotificationSettings
import com.example.ui.MainViewModel
import com.example.ui.theme.ThemeAccent
import com.example.notifications.NotificationHelper
import com.example.util.AmbientBreakMusicManager
import com.example.util.SoundEffectsManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currentAccent by viewModel.themeAccent.collectAsState()
    val defaultTab by viewModel.defaultStartTab.collectAsState()
    val keepScreenOn by viewModel.keepScreenOn.collectAsState()
    val notificationSettings by viewModel.notificationSettings.collectAsState()
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()

    var tempNotificationSettings by remember(notificationSettings) {
        mutableStateOf(notificationSettings)
    }

    var selectedTab by remember(defaultTab) { mutableStateOf(defaultTab) }
    var selectedAccent by remember(currentAccent) { mutableStateOf(currentAccent) }
    var screenOnState by remember(keepScreenOn) { mutableStateOf(keepScreenOn) }

    DisposableEffect(Unit) {
        onDispose {
            SoundEffectsManager.stopPreview()
        }
    }

    val soundOptions = remember { SoundEffectsManager.allSounds.map { it.name } }

    val startTabOptions = listOf(
        "HOME" to "Início",
        "TIMER" to "Timer",
        "ORGANIZATION" to "Tarefas & Hábitos",
        "NOTES" to "Notas & Anotações",
        "CALENDAR" to "Calendário",
        "STATS" to "Estatísticas"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
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
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Configurações",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable content
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // 1. APARÊNCIA & CORES TEMA
                    item {
                        Text(
                            text = "Aparência & Cores do Tema",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Theme Mode Selector
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = isDarkTheme == false,
                                onClick = { viewModel.setThemeMode(false) },
                                label = { Text("Claro") },
                                leadingIcon = { Icon(Icons.Default.LightMode, null, modifier = Modifier.size(16.dp)) }
                            )
                            FilterChip(
                                selected = isDarkTheme == true,
                                onClick = { viewModel.setThemeMode(true) },
                                label = { Text("Escuro") },
                                leadingIcon = { Icon(Icons.Default.DarkMode, null, modifier = Modifier.size(16.dp)) }
                            )
                            FilterChip(
                                selected = isDarkTheme == null,
                                onClick = { viewModel.setThemeMode(null) },
                                label = { Text("Sistema") },
                                leadingIcon = { Icon(Icons.Default.BrightnessAuto, null, modifier = Modifier.size(16.dp)) }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Cor predominante do aplicativo:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(ThemeAccent.entries) { accent ->
                                val isSelected = selectedAccent == accent
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSelected) accent.container else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .clickable {
                                            selectedAccent = accent
                                            viewModel.setThemeAccent(accent)
                                        }
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) accent.primary else Color.Transparent,
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(18.dp)
                                                .background(accent.primary, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = accent.displayName,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) accent.onContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2. ABA INICIAL PADRÃO
                    item {
                        Text(
                            text = "Aba Inicial ao Abrir",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Escolha qual tela o aplicativo abre por padrão:",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(startTabOptions) { (key, label) ->
                                val isSel = selectedTab == key
                                FilterChip(
                                    selected = isSel,
                                    onClick = {
                                        selectedTab = key
                                        viewModel.setDefaultStartTab(key)
                                    },
                                    label = { Text(label, fontSize = 12.sp) }
                                )
                            }
                        }
                    }

                    // 3. TELA DO TIMER & COMPORTAMENTO
                    item {
                        Text(
                            text = "Temporizador de Pomodoro",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Manter tela ligada no Timer",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Impede que o dispositivo apague a tela durante a contagem de foco",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = screenOnState,
                                    onCheckedChange = {
                                        screenOnState = it
                                        viewModel.setKeepScreenOn(it)
                                    }
                                )
                            }
                        }
                    }

                    // 4. CENTRAL DE NOTIFICAÇÕES & SONS
                    item {
                        Text(
                            text = "Central de Notificações & Sons",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Sound Pickers (Predefinidos ou Escolha de Áudio do Aparelho)
                        NotificationSoundSelector(
                            label = "Som da Notificação de Tarefas",
                            currentSound = tempNotificationSettings.taskSound,
                            customUri = tempNotificationSettings.taskSoundUri,
                            soundOptions = soundOptions,
                            onSoundSelected = {
                                val matched = SoundEffectsManager.allSounds.firstOrNull { s -> s.name == it || s.id == it }
                                val id = matched?.id ?: it
                                tempNotificationSettings = tempNotificationSettings.copy(taskSound = id, taskSoundUri = "")
                            },
                            onCustomAudioSelected = {
                                tempNotificationSettings = tempNotificationSettings.copy(taskSoundUri = it, taskSound = "Personalizado")
                            },
                            onClearCustomAudio = {
                                tempNotificationSettings = tempNotificationSettings.copy(taskSoundUri = "", taskSound = SoundEffectsManager.DEFAULT_TASK_SOUND_ID)
                            }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        NotificationSoundSelector(
                            label = "Som da Notificação de Hábitos",
                            currentSound = tempNotificationSettings.habitSound,
                            customUri = tempNotificationSettings.habitSoundUri,
                            soundOptions = soundOptions,
                            onSoundSelected = {
                                val matched = SoundEffectsManager.allSounds.firstOrNull { s -> s.name == it || s.id == it }
                                val id = matched?.id ?: it
                                tempNotificationSettings = tempNotificationSettings.copy(habitSound = id, habitSoundUri = "")
                            },
                            onCustomAudioSelected = {
                                tempNotificationSettings = tempNotificationSettings.copy(habitSoundUri = it, habitSound = "Personalizado")
                            },
                            onClearCustomAudio = {
                                tempNotificationSettings = tempNotificationSettings.copy(habitSoundUri = "", habitSound = SoundEffectsManager.DEFAULT_HABIT_SOUND_ID)
                            }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        NotificationSoundSelector(
                            label = "Som de Término do Pomodoro",
                            currentSound = tempNotificationSettings.pomodoroSound,
                            customUri = tempNotificationSettings.pomodoroSoundUri,
                            soundOptions = soundOptions,
                            onSoundSelected = {
                                val matched = SoundEffectsManager.allSounds.firstOrNull { s -> s.name == it || s.id == it }
                                val id = matched?.id ?: it
                                tempNotificationSettings = tempNotificationSettings.copy(pomodoroSound = id, pomodoroSoundUri = "")
                            },
                            onCustomAudioSelected = {
                                tempNotificationSettings = tempNotificationSettings.copy(pomodoroSoundUri = it, pomodoroSound = "Personalizado")
                            },
                            onClearCustomAudio = {
                                tempNotificationSettings = tempNotificationSettings.copy(pomodoroSoundUri = "", pomodoroSound = SoundEffectsManager.DEFAULT_POMODORO_SOUND_ID)
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Música Ambiente de Pausa e Fundo
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Música Ambiente de Pausa e Fundo 🎵", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(
                                            "Trilha analógica suave para momentos de descanso ou foco contínuo",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Tocar contínuo em segundo plano", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text("Mantém o áudio ambiente ativo enquanto usa o aplicativo", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = tempNotificationSettings.ambientBreakMusicInApp,
                                        onCheckedChange = { enabled ->
                                            tempNotificationSettings = tempNotificationSettings.copy(ambientBreakMusicInApp = enabled)
                                            AmbientBreakMusicManager.setAlwaysPlayingInApp(context, enabled)
                                        }
                                    )
                                }

                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Volume do Áudio Ambiente", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                        Text("${(tempNotificationSettings.ambientMusicVolume * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Slider(
                                        value = tempNotificationSettings.ambientMusicVolume,
                                        onValueChange = { vol ->
                                            tempNotificationSettings = tempNotificationSettings.copy(ambientMusicVolume = vol)
                                            AmbientBreakMusicManager.setVolume(vol)
                                        },
                                        valueRange = 0f..1f,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Toggles
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Vibração ao Notificar", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text("Vibra o aparelho junto aos alertas sonoros", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = tempNotificationSettings.vibrate,
                                        onCheckedChange = {
                                            tempNotificationSettings = tempNotificationSettings.copy(vibrate = it)
                                        }
                                    )
                                }

                                Divider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Alertas na Tela (Banner)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text("Exibe banner flutuante heads-up com alta prioridade", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = tempNotificationSettings.headsUpBanner,
                                        onCheckedChange = {
                                            tempNotificationSettings = tempNotificationSettings.copy(headsUpBanner = it)
                                        }
                                    )
                                }

                                Divider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Avisar ao Desbloquear Conquistas", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text("Receba notificação celebrando novos marcos alcançados", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = tempNotificationSettings.notifyAchievements,
                                        onCheckedChange = {
                                            tempNotificationSettings = tempNotificationSettings.copy(notifyAchievements = it)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 5. PERSISTÊNCIA & PRIORIDADE DE ALARMES
                    item {
                        Text(
                            text = "Persistência & Prioridade de Alarmes",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val isIgnoringBattery = remember { NotificationHelper.isIgnoringBatteryOptimizations(context) }
                        val canExactAlarms = remember { NotificationHelper.canScheduleExactAlarms(context) }

                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.AlarmOn,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Garantia de Disparo Contínuo",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                                Text(
                                    text = "O Android desativa tarefas em segundo plano e bloqueia alarmes comuns após longas horas de repouso (Doze Mode). O Citra utiliza AlarmClock e WakeLock de alta precisão para garantir que tarefas e hábitos toquem pontualmente.",
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Button(
                                    onClick = { NotificationHelper.requestIgnoreBatteryOptimizations(context) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isIgnoringBattery) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary,
                                        contentColor = if (isIgnoringBattery) MaterialTheme.colorScheme.primary else Color.White
                                    )
                                ) {
                                    Icon(
                                        if (isIgnoringBattery) Icons.Default.CheckCircle else Icons.Default.BatteryChargingFull,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        if (isIgnoringBattery) "Isenção de Bateria Concedida" else "Ignorar Otimização de Bateria"
                                    )
                                }

                                if (!canExactAlarms) {
                                    OutlinedButton(
                                        onClick = { NotificationHelper.requestExactAlarmPermission(context) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Autorizar Alarmes Exatos")
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom Save Button
                Button(
                    onClick = {
                        viewModel.updateNotificationSettings(tempNotificationSettings)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("save_settings_btn"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Salvar Configurações", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
fun NotificationSoundSelector(
    label: String,
    currentSound: String,
    customUri: String,
    soundOptions: List<String>,
    onSoundSelected: (String) -> Unit,
    onCustomAudioSelected: (String) -> Unit,
    onClearCustomAudio: () -> Unit
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            SoundEffectsManager.stopPreview()
            onCustomAudioSelected(uri.toString())
        }
    }

    val displaySoundName = remember(currentSound) {
        if (currentSound == "Personalizado") "Personalizado"
        else SoundEffectsManager.getSoundName(currentSound)
    }

    Column {
        Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(4.dp))

        if (customUri.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = "🎶", fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Áudio Local Selecionado",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    IconButton(
                        onClick = {
                            SoundEffectsManager.stopPreview()
                            onClearCustomAudio()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remover áudio customizado",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        } else {
            Box {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "🎵 $displaySoundName",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    val matched = SoundEffectsManager.allSounds.firstOrNull { it.id == currentSound || it.name == currentSound }
                                        ?: SoundEffectsManager.allSounds.first()
                                    SoundEffectsManager.playPreview(context, matched.id)
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.VolumeUp,
                                    contentDescription = "Ouvir som atual",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                    }
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.heightIn(max = 320.dp)
                ) {
                    soundOptions.forEach { sound ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        sound,
                                        fontSize = 12.sp,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = "Ouvir som",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            },
                            onClick = {
                                onSoundSelected(sound)
                                val matched = SoundEffectsManager.allSounds.firstOrNull { it.name == sound || it.id == sound }
                                if (matched != null) {
                                    SoundEffectsManager.playPreview(context, matched.id)
                                }
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = {
                    SoundEffectsManager.stopPreview()
                    audioPicker.launch("audio/*")
                },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Icon(Icons.Default.AudioFile, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Escolher áudio/música do aparelho", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
