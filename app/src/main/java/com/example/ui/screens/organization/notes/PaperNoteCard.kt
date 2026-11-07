package com.example.ui.screens.organization.notes

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.Note
import com.example.ui.theme.*

/**
 * Palette options for colored paper notes based on Citra's theme colors.
 */
data class NoteColorOption(
    val key: String,
    val displayName: String,
    val primaryColor: Color,
    val lightBg: Color,
    val lightFlap: Color,
    val darkBg: Color,
    val darkFlap: Color
)

object PaperNotePalette {
    val options = listOf(
        NoteColorOption(
            key = "DEFAULT",
            displayName = "Padrão",
            primaryColor = OrangePrimary,
            lightBg = Color(0xFFFBF7F0),
            lightFlap = Color(0xFFE8DFCE),
            darkBg = Color(0xFF38342D),
            darkFlap = Color(0xFF4C453C)
        ),
        NoteColorOption(
            key = "TANGERINA",
            displayName = "Laranja Cítrico",
            primaryColor = Color(0xFFFF6F00),
            lightBg = Color(0xFFFFF6EB),
            lightFlap = Color(0xFFFFDFC0),
            darkBg = Color(0xFF4C3322),
            darkFlap = Color(0xFF6B4831)
        ),
        NoteColorOption(
            key = "AZUL_OCEANO",
            displayName = "Azul Oceano",
            primaryColor = Color(0xFF1E88E5),
            lightBg = Color(0xFFEEF5FF),
            lightFlap = Color(0xFFD3E5FD),
            darkBg = Color(0xFF2C4360),
            darkFlap = Color(0xFF3E5F88)
        ),
        NoteColorOption(
            key = "VERDE_ESMERALDA",
            displayName = "Verde Esmeralda",
            primaryColor = Color(0xFF2E7D32),
            lightBg = Color(0xFFEFF8F0),
            lightFlap = Color(0xFFD4EED7),
            darkBg = Color(0xFF2A4D33),
            darkFlap = Color(0xFF3C6D48)
        ),
        NoteColorOption(
            key = "ROXO_COSMICO",
            displayName = "Roxo Cósmico",
            primaryColor = Color(0xFF7E57C2),
            lightBg = Color(0xFFF6F0FD),
            lightFlap = Color(0xFFE5D5F9),
            darkBg = Color(0xFF45335E),
            darkFlap = Color(0xFF634986)
        ),
        NoteColorOption(
            key = "ROSA_CORAL",
            displayName = "Rosa Coral",
            primaryColor = Color(0xFFE91E63),
            lightBg = Color(0xFFFFF0F4),
            lightFlap = Color(0xFFFFD4E1),
            darkBg = Color(0xFF552D40),
            darkFlap = Color(0xFF79405C)
        ),
        NoteColorOption(
            key = "AMBAR_DOURADO",
            displayName = "Âmbar Dourado",
            primaryColor = Color(0xFFD97706),
            lightBg = Color(0xFFFFF8E6),
            lightFlap = Color(0xFFFFE8B0),
            darkBg = Color(0xFF4F3E22),
            darkFlap = Color(0xFF715931)
        ),
        NoteColorOption(
            key = "VERDE_MENTA",
            displayName = "Verde Menta",
            primaryColor = Color(0xFF00897B),
            lightBg = Color(0xFFEDFAF6),
            lightFlap = Color(0xFFC7F1E7),
            darkBg = Color(0xFF2A4E47),
            darkFlap = Color(0xFF3D6E64)
        ),
        NoteColorOption(
            key = "CARAMELO_AVELA",
            displayName = "Kraft Avelã",
            primaryColor = Color(0xFF8D6E63),
            lightBg = Color(0xFFF5EDE5),
            lightFlap = Color(0xFFE2D3C6),
            darkBg = Color(0xFF4A3E37),
            darkFlap = Color(0xFF6A594F)
        ),
        NoteColorOption(
            key = "AZUL_CELESTE",
            displayName = "Azul Celeste",
            primaryColor = Color(0xFF0288D1),
            lightBg = Color(0xFFEEF8FE),
            lightFlap = Color(0xFFCDECFB),
            darkBg = Color(0xFF29465D),
            darkFlap = Color(0xFF3B6485)
        ),
        NoteColorOption(
            key = "VERDE_LIMA",
            displayName = "Verde Lima",
            primaryColor = Color(0xFF689F38),
            lightBg = Color(0xFFF5FBE6),
            lightFlap = Color(0xFFE0F4B8),
            darkBg = Color(0xFF3D4D29),
            darkFlap = Color(0xFF576E3A)
        ),
        NoteColorOption(
            key = "FRAMBOESA_DOCE",
            displayName = "Framboesa Doce",
            primaryColor = Color(0xFFAD1457),
            lightBg = Color(0xFFFEECF4),
            lightFlap = Color(0xFFFAC9DF),
            darkBg = Color(0xFF542C43),
            darkFlap = Color(0xFF783E60)
        )
    )

    fun findColorOption(key: String?): NoteColorOption {
        if (key.isNullOrBlank()) return options[0]
        return options.firstOrNull { it.key.equals(key, ignoreCase = true) }
            ?: options.firstOrNull { it.displayName.equals(key, ignoreCase = true) }
            ?: options[0]
    }

    fun resolvePaperColors(noteColor: String?, isDark: Boolean, isPinned: Boolean): PaperNoteColors {
        val opt = findColorOption(noteColor)
        val bg = if (isDark) opt.darkBg else opt.lightBg
        val flap = if (isDark) opt.darkFlap else opt.lightFlap
        val crease = if (isDark && opt.key == "DEFAULT") Color(0xFF564E43) else opt.primaryColor.copy(alpha = if (isDark) 0.45f else 0.35f)
        val border = if (isPinned) {
            opt.primaryColor.copy(alpha = 0.65f)
        } else {
            if (isDark) {
                if (opt.key == "DEFAULT") Color(0xFF4E453A) else opt.primaryColor.copy(alpha = 0.25f)
            } else Color(0x18000000).compositeOver(opt.primaryColor.copy(alpha = 0.22f))
        }
        val onBg = if (isDark) DarkTextPrimary else Slate900
        val badgeBg = opt.primaryColor.copy(alpha = if (isDark) 0.25f else 0.15f)
        val badgeFg = if (isDark) opt.primaryColor.copy(alpha = 0.95f) else opt.primaryColor

        return PaperNoteColors(
            background = bg,
            flapColor = flap,
            creaseColor = crease,
            borderColor = border,
            accentColor = opt.primaryColor,
            onBackgroundColor = onBg,
            badgeBg = badgeBg,
            badgeFg = badgeFg
        )
    }

    @Composable
    @ReadOnlyComposable
    fun resolvePaperColors(noteColor: String?, isPinned: Boolean): PaperNoteColors {
        return resolvePaperColors(noteColor, isAppDarkTheme(), isPinned)
    }
}

data class PaperNoteColors(
    val background: Color,
    val flapColor: Color,
    val creaseColor: Color,
    val borderColor: Color,
    val accentColor: Color,
    val onBackgroundColor: Color,
    val badgeBg: Color,
    val badgeFg: Color
)

/**
 * Shape that cuts the top-right corner diagonally to create a realistic folded paper edge.
 */
class FoldedCornerShape(
    private val cornerRadius: Dp = 14.dp,
    private val foldSize: Dp = 22.dp
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val r = with(density) { cornerRadius.toPx() }.coerceAtMost(size.minDimension / 4f)
        val f = with(density) { foldSize.toPx() }.coerceAtMost(size.minDimension / 2.5f)
        val w = size.width
        val h = size.height

        val path = Path().apply {
            moveTo(r, 0f)
            lineTo(w - f, 0f)
            lineTo(w, f)
            lineTo(w, h - r)
            quadraticTo(w, h, w - r, h)
            lineTo(r, h)
            quadraticTo(0f, h, 0f, h - r)
            lineTo(0f, r)
            quadraticTo(0f, 0f, r, 0f)
            close()
        }
        return Outline.Generic(path)
    }
}

/**
 * Realistic paper surface with a folded top-right corner, 3D shading, crease, and subtle texture.
 */
@Composable
fun PaperNoteSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 14.dp,
    foldSize: Dp = 22.dp,
    paperColors: PaperNoteColors,
    isPinned: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = remember(cornerRadius, foldSize) { FoldedCornerShape(cornerRadius, foldSize) }

    Box(
        modifier = modifier
            .shadow(
                elevation = if (isPinned) 3.5.dp else 1.5.dp,
                shape = shape,
                clip = false
            )
            .border(
                width = if (isPinned) 1.5.dp else 1.dp,
                color = paperColors.borderColor,
                shape = shape
            )
            .background(color = paperColors.background, shape = shape)
            .clip(shape)
    ) {
        // Draw the folded paper flap in the top-right corner with 3D drop shadow
        Canvas(modifier = Modifier.matchParentSize()) {
            val f = foldSize.toPx()
            val w = size.width

            // Shadow under the flap for realistic depth
            val shadowPath = Path().apply {
                moveTo(w - f, 0f)
                lineTo(w, f)
                lineTo(w - f - 3f, f + 3f)
                close()
            }
            drawPath(shadowPath, color = Color.Black.copy(alpha = 0.08f))

            // The folded triangle flap
            val flapPath = Path().apply {
                moveTo(w - f, 0f)
                lineTo(w, f)
                lineTo(w - f, f)
                close()
            }
            drawPath(flapPath, color = paperColors.flapColor)

            // Crease line along diagonal
            drawLine(
                color = paperColors.creaseColor,
                start = Offset(w - f, 0f),
                end = Offset(w, f),
                strokeWidth = 1.8f
            )

            // Flap borders for crisp definition
            val flapBorderColor = Color.Black.copy(alpha = 0.09f)
            drawLine(
                color = flapBorderColor,
                start = Offset(w - f, 0f),
                end = Offset(w - f, f),
                strokeWidth = 1f
            )
            drawLine(
                color = flapBorderColor,
                start = Offset(w - f, f),
                end = Offset(w, f),
                strokeWidth = 1f
            )
        }

        // Card Content
        content()
    }
}

/**
 * Dialog for selecting note paper color inside FullScreenNoteEditor.
 */
@Composable
fun NoteColorPickerDialog(
    currentColorKey: String?,
    onDismiss: () -> Unit,
    onSelectColor: (String?) -> Unit
) {
    val isDark = isAppDarkTheme()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Cor da Nota",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar", modifier = Modifier.size(18.dp))
                    }
                }

                Text(
                    text = "Escolha uma cor tema para a nota parecer uma folha colorida e facilitar a organização.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Color Swatches Grid (Pure colored circles, no text names)
                val chunkedOptions = remember { PaperNotePalette.options.chunked(4) }
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    chunkedOptions.forEach { rowOptions ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            rowOptions.forEach { opt ->
                                val isSelected = if (opt.key == "DEFAULT") {
                                    currentColorKey == null || currentColorKey == "DEFAULT"
                                } else {
                                    currentColorKey.equals(opt.key, ignoreCase = true)
                                }
                                val optBg = if (isDark) opt.darkBg else opt.lightBg

                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(optBg)
                                        .border(
                                            width = if (isSelected) 2.5.dp else 1.dp,
                                            color = if (isSelected) opt.primaryColor else opt.primaryColor.copy(alpha = 0.35f),
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            onSelectColor(if (opt.key == "DEFAULT") null else opt.key)
                                            onDismiss()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .background(opt.primaryColor, CircleShape)
                                            .border(1.5.dp, Color.White.copy(alpha = 0.9f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Restore Default Action
                if (currentColorKey != null && currentColorKey != "DEFAULT") {
                    TextButton(
                        onClick = {
                            onSelectColor(null)
                            onDismiss()
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Restaurar Cor Padrão")
                    }
                }
            }
        }
    }
}
