package com.example.ui.screens.organization.notes

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import com.example.data.model.NoteDocumentType
import com.example.data.model.isDocxDocument
import com.example.data.model.isExternalDocument
import com.example.data.model.isPdfDocument
import com.example.data.model.resolvedDocumentType
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
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoryNoteStack(
    categoryName: String,
    notes: List<Note>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onNoteClick: (Note) -> Unit,
    onNoteLongClick: ((Note) -> Unit)? = null,
    onNoteMoreClick: ((Note) -> Unit)? = null,
    onTogglePin: (Note) -> Unit,
    onDeleteNote: (Note) -> Unit,
    isSelectionMode: Boolean = false,
    selectedNoteIds: Set<Long> = emptySet(),
    modifier: Modifier = Modifier
) {
    val isDark = isAppDarkTheme()
    val categoryIcon = categoryIconFor(categoryName)
    val displayPileNotes = remember(notes) { notes.take(5) }
    val pileCount = displayPileNotes.size

    // Rotations & offsets for up to 5 notes in the pile (organic millimeter offsets)
    val stackRotations = listOf(0f, 3.0f, -2.5f, 3.8f, -4.2f)
    val stackOffsetsX = listOf(0.dp, 4.dp, (-3).dp, 5.dp, (-5).dp)
    val stackOffsetsY = listOf(0.dp, 2.dp, 3.dp, (-3).dp, (-4).dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("category_stack_$categoryName"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!isExpanded) {
            // Collapsed Pile ("Pasta Empilhada")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(162.dp)
                    .padding(horizontal = 4.dp, vertical = 4.dp)
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
                            note = note,
                            isDark = isDark
                        )

                        PaperNoteSurface(
                            cornerRadius = 16.dp,
                            foldSize = 20.dp,
                            paperColors = paperColors,
                            isPinned = false,
                            modifier = Modifier
                                .fillMaxWidth(0.96f)
                                .height(142.dp)
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

                // Top Cover Card ("Capa da Pasta")
                val coverNote = displayPileNotes.firstOrNull()
                val coverColors = if (coverNote != null) {
                    PaperNotePalette.resolvePaperColors(note = coverNote, isDark = isDark)
                } else {
                    PaperNotePalette.resolvePaperColors(noteColor = null, isDark = isDark, isPinned = false)
                }

                PaperNoteSurface(
                    cornerRadius = 16.dp,
                    foldSize = 22.dp,
                    paperColors = coverColors,
                    isPinned = coverNote?.isPinned == true,
                    modifier = Modifier
                        .fillMaxWidth(0.96f)
                        .height(142.dp)
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

                        // Preview of the latest note or external doc in this category
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            if (coverNote != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (coverNote.isExternalDocument) {
                                        val coverType = coverNote.resolvedDocumentType
                                        val docAccent = accentColorForDocType(coverType)
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = docAccent.copy(alpha = 0.16f)
                                        ) {
                                            Text(
                                                text = coverType.label,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = docAccent,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = coverNote.title.ifBlank { "Sem título" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = coverColors.onBackgroundColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                }
                                when (coverNote.resolvedDocumentType) {
                                    NoteDocumentType.PDF -> {
                                        Text(
                                            text = "Documento PDF · Somente leitura",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = PdfSheetAccentColor.copy(alpha = 0.85f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    NoteDocumentType.DOCX -> {
                                        Text(
                                            text = "Documento .docx · Toque para abrir",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = DocxSheetAccentColor.copy(alpha = 0.85f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    NoteDocumentType.EPUB, NoteDocumentType.MOBI, NoteDocumentType.CBZ, NoteDocumentType.FB2 -> {
                                        val t = coverNote.resolvedDocumentType
                                        Text(
                                            text = "${t.label} · Leitura no Citra",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = accentColorForDocType(t).copy(alpha = 0.85f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    NoteDocumentType.MARKDOWN -> {
                                        if (coverNote.content.isNotBlank()) {
                                            NoteReadingPreview(
                                                content = coverNote.content,
                                                maxElements = 1,
                                                maxTotalLines = 1,
                                                compact = true,
                                                textColor = coverColors.onBackgroundColor,
                                                secondaryTextColor = coverColors.onBackgroundColor.copy(alpha = 0.72f),
                                                accentColor = coverColors.accentColor,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        } else {
                                            Text(
                                                text = "Toque para abrir e ver notas...",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = coverColors.onBackgroundColor.copy(alpha = 0.65f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
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
                                    text = if (notes.size == 1) "Pasta com 1 nota" else "Pilha de ${notes.size} notas",
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
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onToggleExpand() }
                    .testTag("category_stack_collapse_$categoryName")
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
                        val isSelected = selectedNoteIds.contains(note.id)
                        val wobbleDeg = rememberSelectionWobbleAngle(isSelectionMode = isSelectionMode, isSelected = isSelected)
                        val paperColors = PaperNotePalette.resolvePaperColors(
                            note = note,
                            isDark = isDark
                        )
                        val dateText = remember(note.updatedAt) {
                            SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(note.updatedAt))
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = stairIndent)
                                .rotate(wobbleDeg)
                        ) {
                            PaperNoteSurface(
                                cornerRadius = 14.dp,
                                foldSize = 20.dp,
                                paperColors = paperColors,
                                isPinned = note.isPinned || isSelected,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = { onNoteClick(note) },
                                        onLongClick = onNoteLongClick?.let { handler -> { handler(note) } }
                                    )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp, start = 14.dp, end = 22.dp, bottom = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    if (isSelectionMode) {
                                        Box(
                                            modifier = Modifier
                                                .padding(end = 10.dp)
                                                .size(22.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isSelected) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                                                )
                                                .border(
                                                    width = 1.5.dp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                                    shape = CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selecionada",
                                                    tint = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }

                                    if (note.isExternalDocument) {
                                        Box(
                                            modifier = Modifier
                                                .padding(end = 10.dp)
                                                .size(width = 72.dp, height = 88.dp)
                                        ) {
                                            ExternalDocumentPageOnePreviewBox(
                                                externalUri = note.externalUri,
                                                docType = note.resolvedDocumentType,
                                                compact = true,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(bottomStart = 8.dp),
                                                color = Color.Black.copy(alpha = 0.62f),
                                                modifier = Modifier.align(Alignment.TopEnd)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(22.dp)
                                                            .clip(CircleShape)
                                                            .clickable { onTogglePin(note) },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = if (note.isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                                                            contentDescription = if (note.isPinned) "Desafixar" else "Fixar",
                                                            tint = if (note.isPinned) paperColors.accentColor else Color.White,
                                                            modifier = Modifier.size(13.dp)
                                                        )
                                                    }
                                                    Box(
                                                        modifier = Modifier
                                                            .size(22.dp)
                                                            .clip(CircleShape)
                                                            .clickable { onNoteMoreClick?.invoke(note) ?: onDeleteNote(note) },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = if (onNoteMoreClick != null) Icons.Default.MoreVert else Icons.Default.DeleteOutline,
                                                            contentDescription = if (onNoteMoreClick != null) "Opções" else "Excluir",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(13.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            if (note.isPinned && !note.isExternalDocument) {
                                                Icon(
                                                    imageVector = Icons.Default.PushPin,
                                                    contentDescription = "Fixada",
                                                    tint = paperColors.accentColor,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                            if (note.isExternalDocument) {
                                                val docType = note.resolvedDocumentType
                                                val docAccent = accentColorForDocType(docType)
                                                val docIcon = when {
                                                    docType == NoteDocumentType.PDF -> Icons.Default.PictureAsPdf
                                                    docType == NoteDocumentType.DOCX -> Icons.Default.Article
                                                    else -> Icons.Default.MenuBook
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = docAccent.copy(alpha = 0.16f)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = docIcon,
                                                            contentDescription = null,
                                                            tint = docAccent,
                                                            modifier = Modifier.size(11.dp)
                                                        )
                                                        Text(
                                                            text = docType.label,
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            color = docAccent
                                                        )
                                                    }
                                                }
                                            }
                                            Text(
                                                text = note.title.ifBlank { "Sem título" },
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = paperColors.onBackgroundColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                        }

                                        when (note.resolvedDocumentType) {
                                            NoteDocumentType.PDF -> {
                                                Text(
                                                    text = "Documento PDF · Toque para abrir",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = PdfSheetAccentColor.copy(alpha = 0.85f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            NoteDocumentType.DOCX -> {
                                                Text(
                                                    text = "Documento .docx · Toque para abrir",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = DocxSheetAccentColor.copy(alpha = 0.85f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            NoteDocumentType.EPUB, NoteDocumentType.MOBI, NoteDocumentType.CBZ, NoteDocumentType.FB2 -> {
                                                val docType = note.resolvedDocumentType
                                                Text(
                                                    text = "${docType.label} · Toque para ler",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = accentColorForDocType(docType).copy(alpha = 0.85f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            NoteDocumentType.MARKDOWN -> {
                                                if (note.content.isNotBlank()) {
                                                    NoteReadingPreview(
                                                        content = note.content,
                                                        maxElements = 1,
                                                        maxTotalLines = 1,
                                                        compact = true,
                                                        textColor = paperColors.onBackgroundColor,
                                                        secondaryTextColor = paperColors.onBackgroundColor.copy(alpha = 0.72f),
                                                        accentColor = paperColors.accentColor,
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                }
                                            }
                                        }

                                        if (!note.isExternalDocument) {
                                            Text(
                                                text = dateText,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = paperColors.metaTextColor
                                            )
                                        }
                                    }

                                    if (!note.isExternalDocument) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            IconButton(
                                                onClick = { onTogglePin(note) },
                                                modifier = Modifier.minimumInteractiveComponentSize()
                                            ) {
                                                Icon(
                                                    imageVector = if (note.isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                                                    contentDescription = if (note.isPinned) "Desafixar" else "Fixar",
                                                    tint = if (note.isPinned) paperColors.accentColor else paperColors.mutedIconColor,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }

                                            IconButton(
                                                onClick = { onNoteMoreClick?.invoke(note) ?: onDeleteNote(note) },
                                                modifier = Modifier.minimumInteractiveComponentSize()
                                            ) {
                                                Icon(
                                                    imageVector = if (onNoteMoreClick != null) Icons.Default.MoreVert else Icons.Default.DeleteOutline,
                                                    contentDescription = if (onNoteMoreClick != null) "Opções" else "Excluir",
                                                    tint = paperColors.mutedIconColor,
                                                    modifier = Modifier.size(18.dp)
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
}
