package com.example.notifications

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

enum class DeviceOemFamily(val displayName: String) {
    XIAOMI("Xiaomi / Redmi / POCO (HyperOS / MIUI)"),
    SAMSUNG("Samsung (One UI)"),
    MOTOROLA("Motorola"),
    REALME_OPPO("Realme / Oppo / OnePlus"),
    GENERIC("Android Padrão")
}

data class AlarmHealthReport(
    val canScheduleExactAlarms: Boolean,
    val canUseFullScreenIntent: Boolean,
    val isIgnoringBatteryOptimizations: Boolean,
    val areNotificationsEnabled: Boolean,
    val oemFamily: DeviceOemFamily
) {
    val allCriticalOk: Boolean
        get() = canScheduleExactAlarms && areNotificationsEnabled

    val allAbsoluteClockOk: Boolean
        get() = canScheduleExactAlarms &&
            canUseFullScreenIntent &&
            isIgnoringBatteryOptimizations &&
            areNotificationsEnabled

    val missingCount: Int
        get() = listOf(
            canScheduleExactAlarms,
            canUseFullScreenIntent,
            isIgnoringBatteryOptimizations,
            areNotificationsEnabled
        ).count { !it }
}

object AlarmHealthChecker {

    fun inspect(context: Context): AlarmHealthReport {
        val exactAlarms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                (context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)
                    ?.canScheduleExactAlarms() ?: true
            } catch (_: Exception) {
                true
            }
        } else {
            true
        }

        val fullScreen = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
                    ?.canUseFullScreenIntent() ?: true
            } catch (_: Exception) {
                true
            }
        } else {
            true
        }

        val batteryUnrestricted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
                    ?.isIgnoringBatteryOptimizations(context.packageName) ?: true
            } catch (_: Exception) {
                true
            }
        } else {
            true
        }

        val notificationsEnabled = try {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        } catch (_: Exception) {
            true
        }

        return AlarmHealthReport(
            canScheduleExactAlarms = exactAlarms,
            canUseFullScreenIntent = fullScreen,
            isIgnoringBatteryOptimizations = batteryUnrestricted,
            areNotificationsEnabled = notificationsEnabled,
            oemFamily = detectOemFamily()
        )
    }

    fun detectOemFamily(): DeviceOemFamily {
        val mfg = Build.MANUFACTURER.orEmpty().lowercase()
        val brand = Build.BRAND.orEmpty().lowercase()
        val combined = "$mfg $brand"
        return when {
            combined.contains("xiaomi") || combined.contains("redmi") || combined.contains("poco") ->
                DeviceOemFamily.XIAOMI
            combined.contains("samsung") ->
                DeviceOemFamily.SAMSUNG
            combined.contains("motorola") || combined.contains("moto") ->
                DeviceOemFamily.MOTOROLA
            combined.contains("realme") || combined.contains("oppo") || combined.contains("oneplus") ->
                DeviceOemFamily.REALME_OPPO
            else -> DeviceOemFamily.GENERIC
        }
    }

    fun getOemTips(oem: DeviceOemFamily): List<String> = when (oem) {
        DeviceOemFamily.XIAOMI -> listOf(
            "1. Informações do App → Economia de bateria → Sem restrições.",
            "2. Ative 'Início automático' (AutoStart) para o Citra.",
            "3. Em 'Outras permissões', ative 'Exibir na tela de bloqueio' e 'Abrir novas janelas em segundo plano'."
        )
        DeviceOemFamily.SAMSUNG -> listOf(
            "1. Informações do App → Bateria → Selecione 'Não restrito'.",
            "2. Assistência do aparelho → Bateria → Limites de uso em segundo plano → Aplicativos nunca suspensos: adicione o Citra."
        )
        DeviceOemFamily.MOTOROLA -> listOf(
            "1. Informações do App → Bateria do app → Gerenciar uso da bateria → Sem restrições.",
            "2. Certifique-se de que 'Alarme e lembretes' está permitido."
        )
        DeviceOemFamily.REALME_OPPO -> listOf(
            "1. Bateria → Configurações avançadas → Otimização de espera em suspensão desativada.",
            "2. Informações do App → Uso de bateria → Permitir atividade em segundo plano e Início automático."
        )
        DeviceOemFamily.GENERIC -> listOf(
            "1. Bateria → Sem restrições para que o sistema nunca congele o timer.",
            "2. Permita Alarmes Exatos e Notificações em Tela Cheia para despertar na tela de bloqueio."
        )
    }

    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return
            } catch (_: Exception) {}
        }
        openAppDetailsSettings(context)
    }

    fun openFullScreenIntentSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return
            } catch (_: Exception) {}
        }
        openNotificationSettings(context)
    }

    fun openBatteryOptimizationSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return
            } catch (_: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    return
                } catch (_: Exception) {}
            }
        }
        openAppDetailsSettings(context)
    }

    fun openNotificationSettings(context: Context) {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {
            openAppDetailsSettings(context)
        }
    }

    fun openOemBackgroundSettings(context: Context) {
        val candidates = when (detectOemFamily()) {
            DeviceOemFamily.XIAOMI -> listOf(
                Intent().setComponent(
                    ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                    )
                ),
                Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                    setClassName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.permissions.PermissionsEditorActivity"
                    )
                    putExtra("extra_pkgname", context.packageName)
                }
            )
            DeviceOemFamily.REALME_OPPO -> listOf(
                Intent().setComponent(
                    ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                    )
                ),
                Intent().setComponent(
                    ComponentName(
                        "com.oplus.safecenter",
                        "com.oplus.safecenter.permission.startup.StartupAppListActivity"
                    )
                )
            )
            else -> emptyList()
        }

        for (candidate in candidates) {
            try {
                candidate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(candidate)
                return
            } catch (_: Exception) {}
        }
        openAppDetailsSettings(context)
    }

    fun openAppDetailsSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}
