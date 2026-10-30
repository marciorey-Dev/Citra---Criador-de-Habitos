package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Theme Accent Options
enum class ThemeAccent(
    val displayName: String,
    val primary: Color,
    val light: Color,
    val dark: Color,
    val container: Color,
    val onContainer: Color
) {
    TANGERINA(
        displayName = "Laranja Tangerina",
        primary = Color(0xFFFF6F00),
        light = Color(0xFFFF9E40),
        dark = Color(0xFFC43E00),
        container = Color(0xFFFFF0E0),
        onContainer = Color(0xFF5A1E00)
    ),
    AZUL_OCEANO(
        displayName = "Azul Oceano",
        primary = Color(0xFF1E88E5),
        light = Color(0xFF64B5F6),
        dark = Color(0xFF1565C0),
        container = Color(0xFFE3F2FD),
        onContainer = Color(0xFF0D47A1)
    ),
    VERDE_ESMERALDA(
        displayName = "Verde Esmeralda",
        primary = Color(0xFF2E7D32),
        light = Color(0xFF81C784),
        dark = Color(0xFF1B5E20),
        container = Color(0xFFE8F5E9),
        onContainer = Color(0xFF00390B)
    ),
    ROXO_COSMICO(
        displayName = "Roxo Cósmico",
        primary = Color(0xFF7E57C2),
        light = Color(0xFFB39DDB),
        dark = Color(0xFF512DA8),
        container = Color(0xFFEDE7F6),
        onContainer = Color(0xFF311B92)
    ),
    ROSA_CORAL(
        displayName = "Rosa Coral",
        primary = Color(0xFFE91E63),
        light = Color(0xFFF48FB1),
        dark = Color(0xFFC2185B),
        container = Color(0xFFFCE4EC),
        onContainer = Color(0xFF880E4F)
    ),
    VERMELHO_RUBI(
        displayName = "Vermelho Rubi",
        primary = Color(0xFFD32F2F),
        light = Color(0xFFEF9A9A),
        dark = Color(0xFFB71C1C),
        container = Color(0xFFFFEBEE),
        onContainer = Color(0xFF5C0003)
    ),
    INDIGO_PROFUNDO(
        displayName = "Índigo Profundo",
        primary = Color(0xFF3F51B5),
        light = Color(0xFF7986CB),
        dark = Color(0xFF283593),
        container = Color(0xFFE8EAF6),
        onContainer = Color(0xFF1A237E)
    ),
    AMBAR_DOURADO(
        displayName = "Âmbar Dourado",
        primary = Color(0xFFD97706),
        light = Color(0xFFFBBF24),
        dark = Color(0xFFB45309),
        container = Color(0xFFFEF3C7),
        onContainer = Color(0xFF78350F)
    ),
    VERDE_MENTA(
        displayName = "Verde Menta",
        primary = Color(0xFF00897B),
        light = Color(0xFF4DB6AC),
        dark = Color(0xFF004D40),
        container = Color(0xFFE0F2F1),
        onContainer = Color(0xFF00251A)
    ),
    CARAMELO_AVELA(
        displayName = "Caramelo Avelã",
        primary = Color(0xFF8D6E63),
        light = Color(0xFFBCAAA4),
        dark = Color(0xFF4E342E),
        container = Color(0xFFEFEBE9),
        onContainer = Color(0xFF3E2723)
    )
}

// Default accent references (fallback)
val OrangePrimary = Color(0xFFFF6F00)
val OrangeLight = Color(0xFFFF9E40)
val OrangeDark = Color(0xFFC43E00)
val OrangeContainer = Color(0xFFFFF0E0)
val OnOrangeContainer = Color(0xFF5A1E00)

val RedAction = Color(0xFFE53935)
val RedContainer = Color(0xFFFFEBEE)
val OnRedContainer = Color(0xFF5C0003)

val GreenAccent = Color(0xFF2E7D32)
val GreenLight = Color(0xFF4CAF50)
val GreenContainer = Color(0xFFE8F5E9)
val OnGreenContainer = Color(0xFF00390B)

val CreamBackground = Color(0xFFFCFAF7)
val CreamSurface = Color(0xFFFFFFFF)
val CreamSurfaceVariant = Color(0xFFF4EFEA)
val BorderOutline = Color(0xFFCBD5E1)

// High-contrast text colors for light mode (darkened per user feedback)
val Slate900 = Color(0xFF0F172A)           // Very crisp primary text
val Slate700 = Color(0xFF334155)           // Darkened secondary text
val Slate600 = Color(0xFF475569)           // Readable helper text
val Slate500 = Color(0xFF64748B)
val Slate400 = Color(0xFF94A3B8)

// Dark theme counterparts
val DarkBackground = Color(0xFF141311)
val DarkSurface = Color(0xFF1E1C1A)
val DarkSurfaceVariant = Color(0xFF2B2824)
val DarkBorder = Color(0xFF3B3732)
val DarkTextPrimary = Color(0xFFF8FAFC)
val DarkTextSecondary = Color(0xFFCBD5E1)
