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
import androidx.compose.ui.draw.clip
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
    val dailyRandomColor by viewModel.dailyRandomColor.collectAsState()
    val welcomePhrasesEnabled by viewModel.welcomePhrasesEnabled.collectAsState()
    val disabledWelcomeCategories by viewModel.disabledWelcomeCategories.collectAsState()
    val defaultNoteOpenMode by viewModel.defaultNoteOpenMode.collectAsState()

    // Finance Module States
    val financesEnabled by viewModel.financesEnabled.collectAsState()
    val financesSetupCompleted by viewModel.financesSetupCompleted.collectAsState()
    val financeDefaultCurrency by viewModel.financeDefaultCurrency.collectAsState()
    val financeHideValues by viewModel.financeHideValues.collectAsState()
    val financePinCode by viewModel.financePinCode.collectAsState()
    val financeBiometricEnabled by viewModel.financeBiometricEnabled.collectAsState()

    var showFinanceWizard by remember { mutableStateOf(false) }
    var showFinanceLockDialog by remember { mutableStateOf(false) }
    var showEraseFinanceDialog by remember { mutableStateOf(false) }
    var financeCurrencyExpanded by remember { mutableStateOf(false) }

    var tempNotificationSettings by remember(notificationSettings) {
        mutableStateOf(notificationSettings)
    }

    var selectedTab by remember(defaultTab) { mutableStateOf(defaultTab) }
    var selectedAccent by remember(currentAccent) { mutableStateOf(currentAccent) }
    var screenOnState by remember(keepScreenOn) { mutableStateOf(keepScreenOn) }
    var showAllColorsDialog by remember { mutableStateOf(false) }

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
                            // First 10 theme colors
                            items(ThemeAccent.entries.take(10)) { accent ->
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

                            // '+' Button styled with the current color to open full 30-color palette
                            item {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = selectedAccent.container,
                                    modifier = Modifier
                                        .clickable { showAllColorsDialog = true }
                                        .border(
                                            width = 1.5.dp,
                                            color = selectedAccent.primary,
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Mais cores",
                                            tint = selectedAccent.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Mais Cores (30)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = selectedAccent.onContainer
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Daily Random Color Toggle (at 00h)
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setDailyRandomColor(!dailyRandomColor) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .background(selectedAccent.container, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Casino,
                                            contentDescription = null,
                                            tint = selectedAccent.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Trocar cor aleatoriamente às 00h",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Alterna a cada dia entre as 30 cores do app",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = dailyRandomColor,
                                    onCheckedChange = { viewModel.setDailyRandomColor(it) }
                                )
                            }
                        }



                        Spacer(modifier = Modifier.height(10.dp))

                        // Resetar Mascotes Citra do Timer
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Mascotes Citra no Timer",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Limpar os mascotes acumulados na tela do Timer",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                FilledTonalButton(
                                    onClick = {
                                        viewModel.clearTimerCitraBalls()
                                        android.widget.Toast.makeText(context, "Mascotes do Timer resetados!", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Resetar", fontSize = 12.sp)
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
                            text = "Temporizador Arancia",
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
                            label = "Som de Término do Arancia",
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

                                Divider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Tocar independente do estado do celular", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text("Permite tocar som mesmo com o celular em modo silencioso ou vibração. Desativado por padrão para respeitar o aparelho.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = tempNotificationSettings.overrideSilentMode,
                                        onCheckedChange = {
                                             tempNotificationSettings = tempNotificationSettings.copy(overrideSilentMode = it)
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

                    // 6. NOTAS & ANOTAÇÕES (MODO PADRÃO DE ABERTURA)
                    item {
                        Text(
                            text = "Notas & Anotações",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Modo padrão ao abrir notas",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.5.sp
                                )
                                Text(
                                    text = "Escolha como as anotações são abertas por padrão ao selecioná-las na lista ou calendário.",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val isReading = defaultNoteOpenMode == "READING"
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (!isReading) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(14.dp))
                                            .clickable { viewModel.setDefaultNoteOpenMode("EDIT") }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = null,
                                                tint = if (!isReading) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Modo Edição",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = if (!isReading) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (isReading) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(14.dp))
                                            .clickable { viewModel.setDefaultNoteOpenMode("READING") }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.MenuBook,
                                                contentDescription = null,
                                                tint = if (isReading) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Modo Leitura",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = if (isReading) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 7. TUTORIAL DO APLICATIVO
                    item {
                        Text(
                            text = "Tutorial do Aplicativo",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Rever Tutoriais do App", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(
                                    "Reinicia os tutoriais guiados em todas as áreas do Citra (Início, Tarefas, Hábitos, Notas, Dados e Menu) para que sejam exibidos novamente.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = {
                                        viewModel.resetAllTutorials()
                                        onDismiss()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("reset_tutorials_btn"),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Reativar e Rever Tutoriais")
                                }
                            }
                        }
                    }

                    // 7. FRASES DE BOAS-VINDAS DIÁRIAS
                    item {
                        Text(
                            text = "Frases de Boas-vindas",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Mensagem diária no Início", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text(
                                            "Exibe uma frase nova por dia na primeira abertura",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = welcomePhrasesEnabled,
                                        onCheckedChange = { viewModel.setWelcomePhrasesEnabled(it) }
                                    )
                                }

                                if (welcomePhrasesEnabled) {
                                    Text(
                                        text = "Categorias ativas:",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(com.example.data.local.WelcomePhraseCategory.entries) { cat ->
                                            val isEnabled = !disabledWelcomeCategories.contains(cat.id)
                                            FilterChip(
                                                selected = isEnabled,
                                                onClick = { viewModel.toggleWelcomeCategory(cat.id) },
                                                label = { Text("${cat.emoji} ${cat.label}", fontSize = 11.5.sp) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ==================== FINANÇAS (V1.2) ====================
                    item {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.AccountBalanceWallet,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Finanças",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // Interruptor Ativar Finanças
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Ativar Finanças",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = if (financesEnabled) {
                                                "Aba ativa na navegação. Desligar não apaga dados."
                                            } else {
                                                "Desligado por padrão. Nenhum dado é apagado ao desligar."
                                            },
                                            fontSize = 11.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = financesEnabled,
                                        onCheckedChange = { checked ->
                                            if (checked && !financesSetupCompleted) {
                                                showFinanceWizard = true
                                            } else {
                                                viewModel.setFinancesEnabled(checked)
                                            }
                                        },
                                        modifier = Modifier.testTag("toggle_finances_switch")
                                    )
                                }

                                if (financesEnabled) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                    // Moeda padrão
                                    val currentCurr = com.example.data.model.FinanceCurrency.fromCode(financeDefaultCurrency)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Moeda padrão", fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                                        Box {
                                            OutlinedButton(
                                                onClick = { financeCurrencyExpanded = true },
                                                shape = RoundedCornerShape(12.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text(currentCurr.displayName, fontSize = 12.5.sp)
                                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                                            }
                                            DropdownMenu(
                                                expanded = financeCurrencyExpanded,
                                                onDismissRequest = { financeCurrencyExpanded = false }
                                            ) {
                                                com.example.data.model.FinanceCurrency.entries.forEach { curr ->
                                                    DropdownMenuItem(
                                                        text = { Text(curr.displayName) },
                                                        onClick = {
                                                            viewModel.setFinanceDefaultCurrency(curr.code)
                                                            financeCurrencyExpanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Bloqueio por PIN ou biometria
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showFinanceLockDialog = true }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Bloqueio por PIN ou biometria", fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                                            Text(
                                                text = when {
                                                    !financePinCode.isNullOrBlank() && financeBiometricEnabled -> "PIN e biometria ativos"
                                                    !financePinCode.isNullOrBlank() -> "PIN ativo"
                                                    financeBiometricEnabled -> "Biometria ativa"
                                                    else -> "Desligado"
                                                },
                                                fontSize = 11.5.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                                    }

                                    // Ocultar valores na tela
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Ocultar valores na tela", fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                                        Switch(
                                            checked = financeHideValues,
                                            onCheckedChange = { viewModel.setFinanceHideValues(it) }
                                        )
                                    }

                                    // Sincronização automática na nuvem (Ativar / Desativar)
                                    val syncEnabled by viewModel.syncEnabled.collectAsState()
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Sincronização automática na nuvem", fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                                            Text(
                                                text = if (syncEnabled) "Ativada (sincroniza ao abrir e sair)" else "Desativada (apenas backup manual)",
                                                fontSize = 11.5.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = syncEnabled,
                                            onCheckedChange = { viewModel.setSyncEnabled(it) }
                                        )
                                    }

                                    // Backup na nuvem (botão manual)
                                    val lastFinSync by viewModel.lastFinanceSyncTimestamp.collectAsState()
                                    val syncDateFmt = remember { java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale("pt", "AO")) }
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Backup na nuvem", fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                                                Text(
                                                    text = if (lastFinSync > 0L) {
                                                        "Último backup: ${syncDateFmt.format(java.util.Date(lastFinSync))}"
                                                    } else {
                                                        "Sincronize manualmente quando quiser"
                                                    },
                                                    fontSize = 11.5.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            FilledTonalButton(
                                                onClick = { viewModel.backupFinancesNow(showToast = true) },
                                                shape = RoundedCornerShape(12.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Fazer backup agora", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }

                                    // Apagar dados de finanças (confirmação dupla)
                                    OutlinedButton(
                                        onClick = { showEraseFinanceDialog = true },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = com.example.ui.theme.RedAction
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Apagar dados de finanças", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
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

    if (showFinanceWizard) {
        com.example.ui.screens.finances.FinanceFirstTimeWizardDialog(
            onDismiss = { showFinanceWizard = false },
            onComplete = { currencyCode, initialBalanceMinor, pin, biometric ->
                viewModel.completeFinanceSetup(currencyCode, initialBalanceMinor, pin, biometric)
                showFinanceWizard = false
            }
        )
    }

    if (showFinanceLockDialog) {
        com.example.ui.screens.finances.FinanceLockSettingsDialog(
            currentPin = financePinCode,
            currentBiometric = financeBiometricEnabled,
            onDismiss = { showFinanceLockDialog = false },
            onSave = { pin, biometric ->
                viewModel.setFinanceLock(pin, biometric)
            }
        )
    }

    if (showEraseFinanceDialog) {
        com.example.ui.screens.finances.EraseFinanceDataDoubleConfirmDialog(
            onDismiss = { showEraseFinanceDialog = false },
            onConfirmErase = { viewModel.eraseAllFinanceData() }
        )
    }

    if (showAllColorsDialog) {
        Dialog(
            onDismissRequest = { showAllColorsDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.85f),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Paleta de Cores (30)",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Escolha sua cor preferida para todo o aplicativo",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { showAllColorsDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Fechar")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(ThemeAccent.entries) { accent ->
                            val isSelected = selectedAccent == accent
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) accent.container else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedAccent = accent
                                        viewModel.setThemeAccent(accent)
                                        showAllColorsDialog = false
                                    }
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) accent.primary else Color.Transparent,
                                        shape = RoundedCornerShape(14.dp)
                                    )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .background(accent.primary, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = accent.displayName,
                                            fontSize = 14.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) accent.onContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Selecionado",
                                            tint = accent.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { showAllColorsDialog = false },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Fechar")
                    }
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
            val audioFileName = remember(customUri) {
                try {
                    val uri = Uri.parse(customUri)
                    var name: String? = null
                    if (uri.scheme == "content") {
                        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                if (index != -1) {
                                    name = cursor.getString(index)
                                }
                            }
                        }
                    }
                    name ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Áudio Personalizado"
                } catch (_: Exception) {
                    "Áudio Personalizado"
                }
            }

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
                            text = audioFileName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                SoundEffectsManager.playPreview(context, customUri)
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.VolumeUp,
                                contentDescription = "Ouvir áudio selecionado",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
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
