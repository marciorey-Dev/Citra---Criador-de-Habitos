package com.example.ui.screens.organization.notes

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Note
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
private fun categoryIconFor(category: String): ImageVector {
    return when (category.lowercase().trim()) {
        "criatividade", "ideias" -> Icons.Default.Palette
        "estudos", "study" -> Icons.Default.MenuBook
        "logística", "finanças", "trabalho", "work" -> Icons.Default.Payments
        "saúde", "fitness" -> Icons.Default.Eco
        "geral" -> Icons.Default.FolderSpecial
        else -> Icons.Default.Label
    }
}

/**
 * Category Note Stack Component.
 * In collapsed mode, notes form an organic pile rotated slightly like real papers on a desk
 * with the category name serving as cover. In expanded mode, notes slide out in a fluid staircase.
 */
@Composable
fun CategoryNoteStack(
    categoryName: String,
    notes: List<Note>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onNoteClick: (Note) -> Unit,
    onNoteLongClick: ((Note) -> Unit)? = null,
    onTogglePin: (Note) -> Unit,
    onDeleteNote: (Note) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val categoryIcon = categoryIconFor(categoryName)
    val displayPileNotes = remember(notes) { notes.take(5) }
    val pileCount = displayPileNotes.size

    // Rotations & offsets for up to 5 notes in the pile (organic millimeter offsets)
    val stackRotations = listOf(0f, 3.2f, -2.6f, 4.1f, -4.5f)
    val stackOffsetsX = listOf(0.dp, 4.dp, (-3).dp, 5.dp, (-5).dp)
    val stackOffsetsY = listOf(0.dp, 2.dp, 3.dp, (-3).dp, (-5).dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("category_stack_$categoryName"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!isExpanded) {
            // Collapsed Pile ("Amontoado de Notas")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .padding(horizontal = 6.dp, vertical = 6.dp)
                    .clickable { onToggleExpand() }
                    .testTag("category_pile_box_$categoryName"),
                contentAlignment = Alignment.Center
            ) {
                // Background cards of the pile (bottom cards drawn first)
                if (pileCount > 1) {
                    for (i in (pileCount - 1) downTo 1) {
                        val note = displayPileNotes[i]
                        val rot = stackRotations.getOrElse(i) { (i * 2f - 3f) }
                        val offX = stackOffsetsX.getOrElse(i) { 0.dp }
                        val offY = stackOffsetsY.getOrElse(i) { 0.dp }
                        val paperColors = PaperNotePalette.resolvePaperColors(
                            noteColor = note.noteColor,
                            isDark = isDark,
                            isPinned = note.isPinned
                        )

                        PaperNoteSurface(
                            cornerRadius = 16.dp,
                            foldSize = 20.dp,
                            paperColors = paperColors,
                            isPinned = false,
                            modifier = Modifier
                                .fillMaxWidth(0.96f)
                                .height(155.dp)
                                .offset(x = offX, y = offY)
                                .rotate(rot)
                        ) {
                            // Subtle background card decoration showing title hint
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                contentAlignment = Alignment.BottomStart
                            ) {
                                Text(
                                    text = note.title.ifBlank { "Sem título" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = paperColors.onBackgroundColor.copy(alpha = 0.5f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Top Cover Card ("Capa da Categoria")
                val coverNote = displayPileNotes.firstOrNull()
                val coverColors = PaperNotePalette.resolvePaperColors(
                    noteColor = coverNote?.noteColor,
                    isDark = isDark,
                    isPinned = coverNote?.isPinned == true
                )

                PaperNoteSurface(
                    cornerRadius = 16.dp,
                    foldSize = 24.dp,
                    paperColors = coverColors,
                    isPinned = coverNote?.isPinned == true,
                    modifier = Modifier
                        .fillMaxWidth(0.96f)
                        .height(155.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 14.dp, start = 16.dp, end = 28.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Header with Category Icon and Badge
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .background(coverColors.badgeBg, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = categoryIcon,
                                        contentDescription = null,
                                        tint = coverColors.badgeFg,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }

                                Text(
                                    text = categoryName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = coverColors.onBackgroundColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Note count pill
                            Surface(
                                shape = CircleShape,
                                color = coverColors.badgeBg
                            ) {
                                Text(
                                    text = "${notes.size} ${if (notes.size == 1) "nota" else "notas"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = coverColors.badgeFg,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        // Preview of the latest note in this category
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            if (coverNote != null) {
                                Text(
                                    text = coverNote.title.ifBlank { "Sem título" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = coverColors.onBackgroundColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val excerpt = coverNote.content
                                    .lines()
                                    .firstOrNull { it.isNotBlank() && !it.startsWith("#") }
                                    ?.take(70) ?: "Toque para abrir e ver notas..."
                                Text(
                                    text = excerpt,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = coverColors.onBackgroundColor.copy(alpha = 0.65f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Bottom Tap to expand affordance
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Layers,
                                    contentDescription = null,
                                    tint = coverColors.accentColor,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Pilha compacta (${notes.size})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = coverColors.accentColor,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "Desdobrar",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = coverColors.accentColor
                                )
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = coverColors.accentColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Expanded Staircase View ("Notas se deslocam pra fora em forma de escada")
            // Category Header Bar
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = categoryIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = categoryName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "${notes.size}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Recolher pilha",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Recolher pilha",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Staircase notes cascade with fluid animation
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + fadeIn(),
                exit = shrinkVertically(
                    animationSpec = tween(180)
                ) + fadeOut()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    notes.forEachIndexed { index, note ->
                        // Staircase offset progression
                        val stairIndent = (index * 8).dp.coerceAtMost(24.dp)
                        val paperColors = PaperNotePalette.resolvePaperColors(
                            noteColor = note.noteColor,
                            isDark = isDark,
                            isPinned = note.isPinned
                        )
                        val dateText = remember(note.updatedAt) {
                            SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(note.updatedAt))
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = stairIndent)
                        ) {
                            PaperNoteSurface(
                                cornerRadius = 14.dp,
                                foldSize = 20.dp,
                                paperColors = paperColors,
                                isPinned = note.isPinned,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNoteClick(note) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp, start = 14.dp, end = 26.dp, bottom = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            if (note.isPinned) {
                                                Icon(
                                                    imageVector = Icons.Default.PushPin,
                                                    contentDescription = "Fixada",
                                                    tint = paperColors.accentColor,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                            Text(
                                                text = note.title.ifBlank { "Sem título" },
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = paperColors.onBackgroundColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        val cleanExcerpt = note.content
                                            .lines()
                                            .firstOrNull { it.isNotBlank() && !it.startsWith("#") }
                                            ?.take(90) ?: ""
                                        if (cleanExcerpt.isNotBlank()) {
                                            Text(
                                                text = cleanExcerpt,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = paperColors.onBackgroundColor.copy(alpha = 0.7f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Text(
                                            text = dateText,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = paperColors.onBackgroundColor.copy(alpha = 0.5f)
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        IconButton(
                                            onClick = { onTogglePin(note) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (note.isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                                                contentDescription = if (note.isPinned) "Desafixar" else "Fixar",
                                                tint = if (note.isPinned) paperColors.accentColor else paperColors.onBackgroundColor.copy(alpha = 0.4f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = { onDeleteNote(note) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Excluir",
                                                tint = paperColors.onBackgroundColor.copy(alpha = 0.4f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
