package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun CitraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: ThemeAccent = ThemeAccent.TANGERINA,
    content: @Composable () -> Unit
) {
    val lightColors = lightColorScheme(
        primary = accent.primary,
        onPrimary = CreamSurface,
        primaryContainer = accent.container,
        onPrimaryContainer = accent.onContainer,
        secondary = GreenAccent,
        onSecondary = CreamSurface,
        secondaryContainer = GreenContainer,
        onSecondaryContainer = OnGreenContainer,
        tertiary = RedAction,
        onTertiary = CreamSurface,
        tertiaryContainer = RedContainer,
        onTertiaryContainer = OnRedContainer,
        background = CreamBackground,
        onBackground = Slate900,
        surface = CreamSurface,
        onSurface = Slate900,
        surfaceVariant = CreamSurfaceVariant,
        onSurfaceVariant = Slate700,
        surfaceContainerLowest = CreamSurface,
        surfaceContainerLow = CreamSurfaceContainerLow,
        surfaceContainer = CreamSurfaceContainer,
        surfaceContainerHigh = CreamSurfaceContainerHigh,
        surfaceContainerHighest = CreamSurfaceContainerHighest,
        outline = BorderOutline,
        outlineVariant = BorderOutlineVariant
    )

    val darkColors = darkColorScheme(
        primary = accent.light,
        onPrimary = DarkBackground,
        primaryContainer = accent.dark,
        onPrimaryContainer = CreamSurface,
        secondary = GreenLight,
        onSecondary = DarkBackground,
        secondaryContainer = GreenAccent,
        onSecondaryContainer = CreamSurface,
        tertiary = RedAction,
        onTertiary = CreamSurface,
        tertiaryContainer = OnRedContainer,
        onTertiaryContainer = RedContainer,
        background = DarkBackground,
        onBackground = DarkTextPrimary,
        surface = DarkSurface,
        onSurface = DarkTextPrimary,
        surfaceVariant = DarkSurfaceVariant,
        onSurfaceVariant = DarkTextSecondary,
        surfaceContainerLowest = DarkBackground,
        surfaceContainerLow = DarkSurfaceContainerLow,
        surfaceContainer = DarkSurfaceContainer,
        surfaceContainerHigh = DarkSurfaceContainerHigh,
        surfaceContainerHighest = DarkSurfaceContainerHighest,
        outline = DarkBorder,
        outlineVariant = DarkBorderVariant
    )

    val colorScheme = if (darkTheme) darkColors else lightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
