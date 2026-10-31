package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.NotificationSettings
import com.example.notifications.NotificationHelper
import com.example.util.SoundEffectsManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsDialog(
    currentSettings: NotificationSettings,
    onSave: (NotificationSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var taskSound by remember { mutableStateOf(currentSettings.taskSound) }
    var taskSoundUri by remember { mutableStateOf(currentSettings.taskSoundUri) }
    var habitSound by remember { mutableStateOf(currentSettings.habitSound) }
    var habitSoundUri by remember { mutableStateOf(currentSettings.habitSoundUri) }
    var pomodoroSound by remember { mutableStateOf(currentSettings.pomodoroSound) }
    var pomodoroSoundUri by remember { mutableStateOf(currentSettings.pomodoroSoundUri) }
    var vibrate by remember { mutableStateOf(currentSettings.vibrate) }
    var headsUp by remember { mutableStateOf(currentSettings.headsUpBanner) }
    var notifyAchievements by remember { mutableStateOf(currentSettings.notifyAchievements) }
    var notifyMissedHabits by remember { mutableStateOf(currentSettings.notifyMissedHabits) }

    val isIgnoringBattery = remember { NotificationHelper.isIgnoringBatteryOptimizations(context) }
    val canExactAlarms = remember { NotificationHelper.canScheduleExactAlarms(context) }

    Dialog(
        onDismissRequest = {
            SoundEffectsManager.stopPreview()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Notificações & Alarmes",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = {
                        SoundEffectsManager.stopPreview()
                        onDismiss()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Reliability Card
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AlarmOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Garantia de Disparo na Tela Bloqueada", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Text(
                                "O Citra usa AlarmClock e WakeLock com permissão de tela cheia para furar a economia de bateria e tocar pontualmente.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Button(
                                onClick = { NotificationHelper.requestIgnoreBatteryOptimizations(context) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isIgnoringBattery) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary,
                                    contentColor = if (isIgnoringBattery) MaterialTheme.colorScheme.primary else Color.White
                                )
                            ) {
                                Icon(if (isIgnoringBattery) Icons.Default.CheckCircle else Icons.Default.BatteryChargingFull, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isIgnoringBattery) "Isenção de Bateria Concedida" else "Ignorar Otimização de Bateria", fontSize = 12.sp)
                            }

                            if (!canExactAlarms) {
                                OutlinedButton(
                                    onClick = { NotificationHelper.requestExactAlarmPermission(context) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Autorizar Alarmes Exatos", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // Sound Selectors
                    Text("Toques do Aplicativo", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)

                    DialogSoundRow(
                        label = "Som de Tarefas",
                        currentSound = taskSound,
                        customUri = taskSoundUri,
                        onSoundSelect = { taskSound = it; taskSoundUri = "" },
                        onCustomAudio = { taskSoundUri = it; taskSound = "Personalizado" },
                        onClearCustom = { taskSoundUri = ""; taskSound = SoundEffectsManager.DEFAULT_TASK_SOUND_ID }
                    )

                    DialogSoundRow(
                        label = "Som de Hábitos",
                        currentSound = habitSound,
                        customUri = habitSoundUri,
                        onSoundSelect = { habitSound = it; habitSoundUri = "" },
                        onCustomAudio = { habitSoundUri = it; habitSound = "Personalizado" },
                        onClearCustom = { habitSoundUri = ""; habitSound = SoundEffectsManager.DEFAULT_HABIT_SOUND_ID }
                    )

                    DialogSoundRow(
                        label = "Som do Pomodoro",
                        currentSound = pomodoroSound,
                        customUri = pomodoroSoundUri,
                        onSoundSelect = { pomodoroSound = it; pomodoroSoundUri = "" },
                        onCustomAudio = { pomodoroSoundUri = it; pomodoroSound = "Personalizado" },
                        onClearCustom = { pomodoroSoundUri = ""; pomodoroSound = SoundEffectsManager.DEFAULT_POMODORO_SOUND_ID }
                    )

                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Toggles
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Vibração ao Notificar", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Vibra o aparelho junto ao toque sonoro", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = vibrate, onCheckedChange = { vibrate = it })
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Alertas em Tela (Banner)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Exibe banner heads-up flutuante de alta prioridade", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = headsUp, onCheckedChange = { headsUp = it })
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Avisar Conquistas", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Notificação ao desbloquear novos marcos", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = notifyAchievements, onCheckedChange = { notifyAchievements = it })
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Avisar Hábitos Não Cumpridos", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Lembrete 5 min após o prazo se o hábito não foi feito", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = notifyMissedHabits, onCheckedChange = { notifyMissedHabits = it })
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        SoundEffectsManager.stopPreview()
                        onSave(
                            currentSettings.copy(
                                taskSound = taskSound,
                                taskSoundUri = taskSoundUri,
                                habitSound = habitSound,
                                habitSoundUri = habitSoundUri,
                                pomodoroSound = pomodoroSound,
                                pomodoroSoundUri = pomodoroSoundUri,
                                vibrate = vibrate,
                                headsUpBanner = headsUp,
                                notifyAchievements = notifyAchievements,
                                notifyMissedHabits = notifyMissedHabits
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Salvar Configurações", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun DialogSoundRow(
    label: String,
    currentSound: String,
    customUri: String,
    onSoundSelect: (String) -> Unit,
    onCustomAudio: (String) -> Unit,
    onClearCustom: () -> Unit
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            onCustomAudio(uri.toString())
        }
    }

    val displaySoundName = remember(currentSound, customUri) {
        if (customUri.isNotBlank()) "Música do Aparelho"
        else SoundEffectsManager.getSoundName(currentSound)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
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
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Text(text = "🎶", fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Áudio Local Selecionado", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { SoundEffectsManager.playPreview(context, customUri) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = "Ouvir", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        }
                        IconButton(
                            onClick = { SoundEffectsManager.stopPreview(); onClearCustom() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Remover", modifier = Modifier.size(16.dp))
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
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "🎵 $displaySoundName",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    val matched = SoundEffectsManager.allSounds.firstOrNull { it.id == currentSound || it.name == currentSound }
                                        ?: SoundEffectsManager.allSounds.first()
                                    SoundEffectsManager.playPreview(context, matched.id)
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.VolumeUp, contentDescription = "Ouvir", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            }
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                    }
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.heightIn(max = 280.dp)
                ) {
                    SoundEffectsManager.allSounds.forEach { soundItem ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(soundItem.name, fontSize = 12.sp, maxLines = 1, modifier = Modifier.weight(1f))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                }
                            },
                            onClick = {
                                onSoundSelect(soundItem.id)
                                SoundEffectsManager.playPreview(context, soundItem.id)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(
                onClick = {
                    SoundEffectsManager.stopPreview()
                    audioPicker.launch("audio/*")
                },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Icon(Icons.Default.AudioFile, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Escolher áudio do aparelho", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
