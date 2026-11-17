package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.notifications.AlarmHealthChecker
import com.example.notifications.AlarmHealthReport

@Composable
fun rememberAlarmHealthReport(): AlarmHealthReport {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var report by remember(context) { mutableStateOf(AlarmHealthChecker.inspect(context)) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                report = AlarmHealthChecker.inspect(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    return report
}

/**
 * Compact actionable warning banner displayed when Exact Alarms, Lockscreen Full-Screen Intent
 * (Android 14+), Battery Optimization exemption, or Notifications are disabled.
 */
@Composable
fun AlarmHealthWarningBanner(
    modifier: Modifier = Modifier,
    requireFullScreenCheck: Boolean = true,
    onOpenFullDiagnostics: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val report = rememberAlarmHealthReport()

    val missingFullScreen = requireFullScreenCheck && !report.canUseFullScreenIntent
    val hasIssue = !report.areNotificationsEnabled ||
        !report.canScheduleExactAlarms ||
        missingFullScreen ||
        !report.isIgnoringBatteryOptimizations

    if (!hasIssue) return

    val issueDescription = when {
        !report.areNotificationsEnabled -> "Notificações bloqueadas — o alarme não aparecerá."
        !report.canScheduleExactAlarms -> "Alarmes exatos desativados — horários podem atrasar."
        missingFullScreen -> "Despertador na tela de bloqueio desativado."
        else -> "Economia de bateria pode adormecer alarmes em segundo plano."
    }

    val actionLabel = when {
        !report.areNotificationsEnabled -> "Ativar"
        !report.canScheduleExactAlarms -> "Permitir"
        missingFullScreen -> "Liberar"
        else -> "Remover Restrição"
    }

    val fixAction: () -> Unit = {
        when {
            !report.areNotificationsEnabled -> AlarmHealthChecker.openNotificationSettings(context)
            !report.canScheduleExactAlarms -> AlarmHealthChecker.openExactAlarmSettings(context)
            missingFullScreen -> AlarmHealthChecker.openFullScreenIntentSettings(context)
            else -> AlarmHealthChecker.openBatteryOptimizationSettings(context)
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("alarm_health_warning_banner")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (!report.allCriticalOk) Icons.Default.WarningAmber else Icons.Default.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Proteção do Alarme",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = issueDescription,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                )
            }
            TextButton(
                onClick = {
                    if (onOpenFullDiagnostics != null) {
                        onOpenFullDiagnostics()
                    } else {
                        fixAction()
                    }
                },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = actionLabel,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * Full "Modo Relógio Absoluto" diagnostic & OEM manufacturer guide card with live ON_RESUME refresh.
 */
@Composable
fun AbsoluteClockDiagnosticsCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val report = rememberAlarmHealthReport()
    val oemTips = remember(report.oemFamily) { AlarmHealthChecker.getOemTips(report.oemFamily) }
    var showOemGuide by remember { mutableStateOf(!report.allAbsoluteClockOk) }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (report.allAbsoluteClockOk) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
            }
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("absolute_clock_diagnostics_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (report.allAbsoluteClockOk) Icons.Default.NotificationsActive else Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = if (report.allAbsoluteClockOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Modo Relógio Absoluto",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (report.allAbsoluteClockOk) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    } else {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
                    }
                ) {
                    Text(
                        text = if (report.allAbsoluteClockOk) "Protegido" else "${report.missingCount} pendência(s)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (report.allAbsoluteClockOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = "O Citra usa AlarmClock, WakeLock e serviços dedicados de timer para tocar pontualmente mesmo com a tela bloqueada ou após reinicialização.",
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AbsoluteDiagnosticRow(
                title = "Alarmes Exatos",
                subtitle = if (report.canScheduleExactAlarms) "Permitido no horário exato" else "Sem permissão — alarmes podem atrasar",
                ok = report.canScheduleExactAlarms,
                actionLabel = "Autorizar",
                onAction = { AlarmHealthChecker.openExactAlarmSettings(context) }
            )

            AbsoluteDiagnosticRow(
                title = "Despertador na Tela de Bloqueio",
                subtitle = if (report.canUseFullScreenIntent) "Tela cheia liberada (Android 14+)" else "Bloqueado pelo sistema para tela cheia",
                ok = report.canUseFullScreenIntent,
                actionLabel = "Liberar",
                onAction = { AlarmHealthChecker.openFullScreenIntentSettings(context) }
            )

            AbsoluteDiagnosticRow(
                title = "Bateria Sem Restrições",
                subtitle = if (report.isIgnoringBatteryOptimizations) "Isento de suspensão em segundo plano" else "Economia de bateria pode adormecer alarmes",
                ok = report.isIgnoringBatteryOptimizations,
                actionLabel = "Ajustar",
                onAction = { AlarmHealthChecker.openBatteryOptimizationSettings(context) }
            )

            AbsoluteDiagnosticRow(
                title = "Notificações do Citra",
                subtitle = if (report.areNotificationsEnabled) "Canal de alertas ativo" else "Notificações desativadas no Android",
                ok = report.areNotificationsEnabled,
                actionLabel = "Ativar",
                onAction = { AlarmHealthChecker.openNotificationSettings(context) }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Guia do aparelho: ${report.oemFamily.displayName}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { showOemGuide = !showOemGuide },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (showOemGuide) "Ocultar" else "Ver dicas",
                            fontSize = 11.sp
                        )
                    }
                }

                if (showOemGuide) {
                    oemTips.forEach { tip ->
                        Text(
                            text = tip,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = { AlarmHealthChecker.openOemBackgroundSettings(context) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Abrir Configurações do Sistema",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AbsoluteDiagnosticRow(
    title: String,
    subtitle: String,
    ok: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = if (ok) "✅" else "⚠️",
            fontSize = 14.sp
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!ok) {
            FilledTonalButton(
                onClick = onAction,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = actionLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

