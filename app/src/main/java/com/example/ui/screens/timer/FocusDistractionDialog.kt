package com.example.ui.screens.timer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.PreferencesManager
import com.example.service.FocusDistractionService
import com.example.ui.theme.OrangePrimary
import com.example.util.FocusPermissionHelper
import com.example.util.InstalledAppItem
import kotlinx.coroutines.launch

@Composable
fun FocusDistractionDialog(
    preferencesManager: PreferencesManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val isEnabled by preferencesManager.focusDistractionFreeEnabled.collectAsState()
    val allowedPackages by preferencesManager.focusAllowedPackages.collectAsState()

    var hasOverlayPerm by remember { mutableStateOf(FocusPermissionHelper.hasOverlayPermission(context)) }
    var hasUsagePerm by remember { mutableStateOf(FocusPermissionHelper.hasUsageStatsPermission(context)) }

    var installedApps by remember { mutableStateOf<List<InstalledAppItem>>(emptyList()) }
    var isLoadingApps by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }

    var showExceptionsView by remember { mutableStateOf(false) }

    // Recheck permissions on resume or when screen updates
    LaunchedEffect(Unit) {
        hasOverlayPerm = FocusPermissionHelper.hasOverlayPermission(context)
        hasUsagePerm = FocusPermissionHelper.hasUsageStatsPermission(context)
    }

    // Lazy-load apps only when opening the exceptions view
    LaunchedEffect(showExceptionsView) {
        if (showExceptionsView && installedApps.isEmpty()) {
            isLoadingApps = true
            val apps = FocusPermissionHelper.getInstalledLauncherApps(context)
            installedApps = apps
            isLoadingApps = false
        }
    }

    val filteredApps = remember(installedApps, searchQuery) {
        if (searchQuery.isBlank()) installedApps
        else installedApps.filter {
            it.appName.contains(searchQuery, ignoreCase = true) ||
            it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    Dialog(
        onDismissRequest = {
            if (showExceptionsView) {
                showExceptionsView = false
            } else {
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                if (!showExceptionsView) {
                    // INITIAL VIEW: Only Toggle, Permissions (if needed), and Add Exceptions button
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = OrangePrimary.copy(alpha = 0.15f),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("🛡️", fontSize = 22.sp)
                                }
                            }
                            Column {
                                Text(
                                    text = "Não Perturbe",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Foco Cítrico Sem Distrações",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OrangePrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Fechar")
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Master Toggle Card
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isEnabled) OrangePrimary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isEnabled) OrangePrimary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val nextState = !isEnabled
                                    preferencesManager.setFocusDistractionFreeEnabled(nextState)
                                }
                                .padding(18.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Ligar Modo Não Perturbe",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isEnabled)
                                        "Ativado! Bloqueia a abertura de outros apps durante o timer."
                                    else
                                        "Toque para ligar a proteção contra distrações durante o timer.",
                                    fontSize = 12.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 16.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Switch(
                                checked = isEnabled,
                                onCheckedChange = { preferencesManager.setFocusDistractionFreeEnabled(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = OrangePrimary
                                ),
                                modifier = Modifier.testTag("focus_distraction_switch")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Permissions Section (shown if first launch or permissions missing)
                    if (!hasOverlayPerm || !hasUsagePerm) {
                        Text(
                            text = "PERMISSÕES NECESSÁRIAS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (!hasOverlayPerm) {
                            PermissionWarningCard(
                                title = "Permissão de sobreposição de tela",
                                description = "Necessária para exibir a tela do Citra sobre apps distratores.",
                                onGrantClick = { FocusPermissionHelper.openOverlaySettings(context) }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        if (!hasUsagePerm) {
                            PermissionWarningCard(
                                title = "Acesso a dados de uso",
                                description = "Permite detectar quando um app que não é permitido foi aberto.",
                                onGrantClick = { FocusPermissionHelper.openUsageAccessSettings(context) }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Status and Info Card
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = OrangePrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = if (allowedPackages.isEmpty())
                                    "Nenhuma exceção cadastrada. Todos os outros apps serão bloqueados."
                                else
                                    "${allowedPackages.size} app(s) permitido(s) como exceção durante o foco.",
                                fontSize = 12.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Test Overlay Button (if permission is granted)
                    if (hasOverlayPerm) {
                        OutlinedButton(
                            onClick = { FocusDistractionService.testOverlay(context) },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().height(42.dp)
                        ) {
                            Text("Testar Tela de Bloqueio 🍊", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Button: Adicionar Exceções
                    OutlinedButton(
                        onClick = { showExceptionsView = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OrangePrimary),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Adicionar Exceções (${allowedPackages.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Conclude Button
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text("Concluir", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                } else {
                    // EXCEPTIONS VIEW: Search bar and Installed apps list
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = { showExceptionsView = false },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                            }
                            Column {
                                Text(
                                    text = "Adicionar Exceções",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${allowedPackages.size} aplicativo(s) selecionados",
                                    fontSize = 11.5.sp,
                                    color = OrangePrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        TextButton(onClick = { showExceptionsView = false }) {
                            Text("Pronto", fontWeight = FontWeight.Bold, color = OrangePrimary)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Search field
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Buscar aplicativo...", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Installed Apps List
                    if (isLoadingApps) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(color = OrangePrimary, strokeWidth = 2.dp)
                                Text("Carregando aplicativos instalados...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else if (filteredApps.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (searchQuery.isNotBlank()) "Nenhum aplicativo encontrado." else "Nenhum app listado.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(filteredApps, key = { it.packageName }) { appItem ->
                                val isAllowed = allowedPackages.contains(appItem.packageName)
                                AppWhitelistRow(
                                    appItem = appItem,
                                    isAllowed = isAllowed,
                                    onToggle = {
                                        preferencesManager.toggleFocusAllowedPackage(appItem.packageName)
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { showExceptionsView = false },
                        colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Salvar Exceções", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionWarningCard(
    title: String,
    description: String,
    onGrantClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Default.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = description,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                    lineHeight = 14.sp
                )
            }

            Button(
                onClick = onGrantClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text("Conceder", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AppWhitelistRow(
    appItem: InstalledAppItem,
    isAllowed: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isAllowed) OrangePrimary.copy(alpha = 0.08f) else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val bitmap = remember(appItem.icon) {
                drawableToBitmap(appItem.icon)
            }

            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(appItem.appName.take(1).uppercase(), fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appItem.appName,
                    fontWeight = if (isAllowed) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isAllowed) "Liberado durante o foco" else appItem.packageName,
                    fontSize = 10.5.sp,
                    color = if (isAllowed) OrangePrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Checkbox(
                checked = isAllowed,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = OrangePrimary,
                    checkmarkColor = Color.White
                )
            )
        }
    }
}

private fun drawableToBitmap(drawable: Drawable?): Bitmap? {
    if (drawable == null) return null
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap
    }
    val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
    val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}
