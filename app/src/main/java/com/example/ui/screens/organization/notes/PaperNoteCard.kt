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
            lightBg = Color(0xFFFCFBF8),
            lightFlap = Color(0xFFE8E2D8),
            darkBg = Color(0xFF201D1A),
            darkFlap = Color(0xFF332F2A)
        ),
        NoteColorOption(
            key = "TANGERINA",
            displayName = "Laranja Cítrico",
            primaryColor = Color(0xFFFF6F00),
            lightBg = Color(0xFFFFF7ED),
            lightFlap = Color(0xFFFFE0B2),
            darkBg = Color(0xFF2C1C12),
            darkFlap = Color(0xFF482D1D)
        ),
        NoteColorOption(
            key = "AZUL_OCEANO",
            displayName = "Azul Oceano",
            primaryColor = Color(0xFF1E88E5),
            lightBg = Color(0xFFF0F7FF),
            lightFlap = Color(0xFFBBDEFB),
            darkBg = Color(0xFF132233),
            darkFlap = Color(0xFF1F3752)
        ),
        NoteColorOption(
            key = "VERDE_ESMERALDA",
            displayName = "Verde Esmeralda",
            primaryColor = Color(0xFF2E7D32),
            lightBg = Color(0xFFF1FDF3),
            lightFlap = Color(0xFFC8E6C9),
            darkBg = Color(0xFF142718),
            darkFlap = Color(0xFF223E28)
        ),
        NoteColorOption(
            key = "ROXO_COSMICO",
            displayName = "Roxo Cósmico",
            primaryColor = Color(0xFF7E57C2),
            lightBg = Color(0xFFFBF7FF),
            lightFlap = Color(0xFFE1BEE7),
            darkBg = Color(0xFF251833),
            darkFlap = Color(0xFF3B2750)
        ),
        NoteColorOption(
            key = "ROSA_CORAL",
            displayName = "Rosa Coral",
            primaryColor = Color(0xFFE91E63),
            lightBg = Color(0xFFFFF1F4),
            lightFlap = Color(0xFFF8BBD0),
            darkBg = Color(0xFF301621),
            darkFlap = Color(0xFF4C2435)
        ),
        NoteColorOption(
            key = "AMBAR_DOURADO",
            displayName = "Âmbar Dourado",
            primaryColor = Color(0xFFD97706),
            lightBg = Color(0xFFFFFBEB),
            lightFlap = Color(0xFFFFECB3),
            darkBg = Color(0xFF2B2110),
            darkFlap = Color(0xFF433418)
        ),
        NoteColorOption(
            key = "VERDE_MENTA",
            displayName = "Verde Menta",
            primaryColor = Color(0xFF00897B),
            lightBg = Color(0xFFF0FDF9),
            lightFlap = Color(0xFFB2DFDB),
            darkBg = Color(0xFF102623),
            darkFlap = Color(0xFF1C3F3A)
        ),
        NoteColorOption(
            key = "CARAMELO_AVELA",
            displayName = "Kraft Avelã",
            primaryColor = Color(0xFF8D6E63),
            lightBg = Color(0xFFFBF8F5),
            lightFlap = Color(0xFFD7CCC8),
            darkBg = Color(0xFF251F1C),
            darkFlap = Color(0xFF3A312D)
        ),
        NoteColorOption(
            key = "AZUL_CELESTE",
            displayName = "Azul Celeste",
            primaryColor = Color(0xFF0288D1),
            lightBg = Color(0xFFF0F9FF),
            lightFlap = Color(0xFFB3E5FC),
            darkBg = Color(0xFF112330),
            darkFlap = Color(0xFF1B374C)
        ),
        NoteColorOption(
            key = "VERDE_LIMA",
            displayName = "Verde Lima",
            primaryColor = Color(0xFF689F38),
            lightBg = Color(0xFFF7FEE7),
            lightFlap = Color(0xFFDCEDC8),
            darkBg = Color(0xFF1D2610),
            darkFlap = Color(0xFF2D3C19)
        ),
        NoteColorOption(
            key = "FRAMBOESA_DOCE",
            displayName = "Framboesa Doce",
            primaryColor = Color(0xFFAD1457),
            lightBg = Color(0xFFFDF2F8),
            lightFlap = Color(0xFFF48FB1),
            darkBg = Color(0xFF2B1321),
            darkFlap = Color(0xFF441F35)
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
        val crease = opt.primaryColor.copy(alpha = if (isDark) 0.45f else 0.35f)
        val border = if (isPinned) {
            opt.primaryColor.copy(alpha = 0.6f)
        } else {
            if (isDark) opt.primaryColor.copy(alpha = 0.22f) else opt.primaryColor.copy(alpha = 0.28f)
        }
        val onBg = if (isDark) DarkTextPrimary else Slate900
        val badgeBg = opt.primaryColor.copy(alpha = if (isDark) 0.22f else 0.15f)
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
    val isDark = isSystemInDarkTheme()

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

                // Color Swatches Grid (3 columns)
                val chunkedOptions = remember { PaperNotePalette.options.chunked(3) }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    chunkedOptions.forEach { rowOptions ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowOptions.forEach { opt ->
                                val isSelected = if (opt.key == "DEFAULT") {
                                    currentColorKey == null || currentColorKey == "DEFAULT"
                                } else {
                                    currentColorKey.equals(opt.key, ignoreCase = true)
                                }
                                val optBg = if (isDark) opt.darkBg else opt.lightBg

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = optBg,
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) opt.primaryColor else opt.primaryColor.copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(72.dp)
                                        .clickable {
                                            onSelectColor(if (opt.key == "DEFAULT") null else opt.key)
                                            onDismiss()
                                        }
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(22.dp)
                                                .background(opt.primaryColor, CircleShape)
                                                .border(1.5.dp, Color.White, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = opt.displayName,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isDark) DarkTextPrimary else Slate900,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
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
