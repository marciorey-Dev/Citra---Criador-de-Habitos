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
    ),
    AZUL_CELESTE(
        displayName = "Azul Celeste",
        primary = Color(0xFF0288D1),
        light = Color(0xFF4FC3F7),
        dark = Color(0xFF01579B),
        container = Color(0xFFE1F5FE),
        onContainer = Color(0xFF013660)
    ),
    VERDE_LIMA(
        displayName = "Verde Lima",
        primary = Color(0xFF689F38),
        light = Color(0xFF9CCC65),
        dark = Color(0xFF33691E),
        container = Color(0xFFF1F8E9),
        onContainer = Color(0xFF1B3D0F)
    ),
    AMARELO_SOLAR(
        displayName = "Amarelo Solar",
        primary = Color(0xFFF59E0B),
        light = Color(0xFFFCD34D),
        dark = Color(0xFFB45309),
        container = Color(0xFFFEF3C7),
        onContainer = Color(0xFF78350F)
    ),
    ROXO_LAVANDA(
        displayName = "Roxo Lavanda",
        primary = Color(0xFF9575CD),
        light = Color(0xFFD1C4E9),
        dark = Color(0xFF5E35B1),
        container = Color(0xFFEDE7F6),
        onContainer = Color(0xFF311B92)
    ),
    TURQUESA_TROPICAL(
        displayName = "Turquesa Tropical",
        primary = Color(0xFF00ACC1),
        light = Color(0xFF4DD0E1),
        dark = Color(0xFF006064),
        container = Color(0xFFE0F7FA),
        onContainer = Color(0xFF00363A)
    ),
    TERRACOTA_RUSTICO(
        displayName = "Terracota Rústico",
        primary = Color(0xFFBF360C),
        light = Color(0xFFFF8A65),
        dark = Color(0xFF870000),
        container = Color(0xFFFBE9E7),
        onContainer = Color(0xFF4E1500)
    ),
    MAGENTA_RADIANTE(
        displayName = "Magenta Radiante",
        primary = Color(0xFFC2185B),
        light = Color(0xFFF06292),
        dark = Color(0xFF880E4F),
        container = Color(0xFFFCE4EC),
        onContainer = Color(0xFF4A0027)
    ),
    AZUL_PETROLEO(
        displayName = "Azul Petróleo",
        primary = Color(0xFF00695C),
        light = Color(0xFF26A69A),
        dark = Color(0xFF003D33),
        container = Color(0xFFE0F2F1),
        onContainer = Color(0xFF00251E)
    ),
    VERDE_OLIVA(
        displayName = "Verde Oliva",
        primary = Color(0xFF558B2F),
        light = Color(0xFF8BC34A),
        dark = Color(0xFF2E5B10),
        container = Color(0xFFEDF7E7),
        onContainer = Color(0xFF1B3B05)
    ),
    CORAL_FLAMINGO(
        displayName = "Coral Flamingo",
        primary = Color(0xFFFF5722),
        light = Color(0xFFFF8A80),
        dark = Color(0xFFD84315),
        container = Color(0xFFFBE9E7),
        onContainer = Color(0xFF4E1500)
    ),
    FRAMBOESA_DOCE(
        displayName = "Framboesa Doce",
        primary = Color(0xFFAD1457),
        light = Color(0xFFEC407A),
        dark = Color(0xFF6A1B9A),
        container = Color(0xFFFCE4EC),
        onContainer = Color(0xFF4A0025)
    ),
    COBRE_AQUECIDO(
        displayName = "Cobre Aquecido",
        primary = Color(0xFFA15C38),
        light = Color(0xFFD49A7E),
        dark = Color(0xFF6F3212),
        container = Color(0xFFFBECE6),
        onContainer = Color(0xFF401805)
    ),
    BRONZE_NOBRE(
        displayName = "Bronze Nobre",
        primary = Color(0xFF795548),
        light = Color(0xFFA1887F),
        dark = Color(0xFF4E342E),
        container = Color(0xFFEFEBE9),
        onContainer = Color(0xFF2D1E1A)
    ),
    AZUL_SAFIRA(
        displayName = "Azul Safira",
        primary = Color(0xFF1565C0),
        light = Color(0xFF42A5F5),
        dark = Color(0xFF0D47A1),
        container = Color(0xFFE3F2FD),
        onContainer = Color(0xFF082855)
    ),
    VERDE_JADE(
        displayName = "Verde Jade",
        primary = Color(0xFF00796B),
        light = Color(0xFF48A999),
        dark = Color(0xFF004C40),
        container = Color(0xFFE0F2F1),
        onContainer = Color(0xFF002820)
    ),
    AMETISTA_MISTICA(
        displayName = "Ametista Mística",
        primary = Color(0xFF6A1B9A),
        light = Color(0xFFAB47BC),
        dark = Color(0xFF4A148C),
        container = Color(0xFFF3E5F5),
        onContainer = Color(0xFF2E0854)
    ),
    PESSEGO_PASTEL(
        displayName = "Pêssego Pastel",
        primary = Color(0xFFE65100),
        light = Color(0xFFFFAB91),
        dark = Color(0xFFBF360C),
        container = Color(0xFFFFF3E0),
        onContainer = Color(0xFF5D1D00)
    ),
    VERDE_FLORESTA(
        displayName = "Verde Floresta",
        primary = Color(0xFF1B5E20),
        light = Color(0xFF4CAF50),
        dark = Color(0xFF0A3D0E),
        container = Color(0xFFE8F5E9),
        onContainer = Color(0xFF052408)
    ),
    ORQUIDEA_SELVAGEM(
        displayName = "Orquídea Selvagem",
        primary = Color(0xFF8E24AA),
        light = Color(0xFFBA68C8),
        dark = Color(0xFF4A148C),
        container = Color(0xFFF3E5F5),
        onContainer = Color(0xFF38004A)
    ),
    DOURADO_CHAMPAGNE(
        displayName = "Dourado Champagne",
        primary = Color(0xFFB78103),
        light = Color(0xFFF6C344),
        dark = Color(0xFF7A5400),
        container = Color(0xFFFEF7E6),
        onContainer = Color(0xFF473100)
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

val BlueAccent = Color(0xFF1E88E5)
val PurpleAccent = Color(0xFF7E57C2)

val CreamBackground = Color(0xFFF3ECE1)
val CreamSurface = Color(0xFFFFFFFF)
val CreamSurfaceVariant = Color(0xFFE7DDD0)
val CreamSurfaceContainerLow = Color(0xFFFAF6F0)
val CreamSurfaceContainer = Color(0xFFEFE6D8)
val CreamSurfaceContainerHigh = Color(0xFFE4D8C7)
val CreamSurfaceContainerHighest = Color(0xFFD9CBB8)
val BorderOutline = Color(0xFFBBA992)
val BorderOutlineVariant = Color(0xFFD5C6B3)

// High-contrast text colors for light mode
val Slate900 = Color(0xFF0F172A)           // Very crisp primary text
val Slate700 = Color(0xFF334155)           // Darkened secondary text
val Slate600 = Color(0xFF475569)           // Readable helper text
val Slate500 = Color(0xFF64748B)
val Slate400 = Color(0xFF94A3B8)

// Dark theme counterparts with clear elevation & container separation
val DarkBackground = Color(0xFF100F0D)
val DarkSurface = Color(0xFF1E1C19)
val DarkSurfaceVariant = Color(0xFF2E2A24)
val DarkSurfaceContainerLow = Color(0xFF171513)
val DarkSurfaceContainer = Color(0xFF24211D)
val DarkSurfaceContainerHigh = Color(0xFF2E2A24)
val DarkSurfaceContainerHighest = Color(0xFF39342D)
val DarkBorder = Color(0xFF524A40)
val DarkBorderVariant = Color(0xFF3A342D)
val DarkTextPrimary = Color(0xFFF8FAFC)
val DarkTextSecondary = Color(0xFFCBD5E1)
