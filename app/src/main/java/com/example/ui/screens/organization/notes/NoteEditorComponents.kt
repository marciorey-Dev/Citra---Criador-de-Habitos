package com.example.ui.screens.organization.notes

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.ui.theme.GreenAccent
import com.example.util.CodeHighlighter
import com.example.util.InlineMarkdownHelper
import com.example.util.MathBeautifier
import com.example.util.NoteAudioPlaybackManager

/**
 * Visual renderer for individual Markdown blocks with smooth inline formatting,
 * dynamic tables, Prism.js code highlight, real-time math beautification,
 * audio player, image resizer, full-screen viewing, and list/checklist navigation loops.
 */
@Composable
fun VisualBlockItem(
    block: VisualBlock,
    isDarkBg: Boolean,
    showPlaceholder: Boolean,
    focusRequester: FocusRequester?,
    onFocusGained: () -> Unit,
    onEnterPressed: () -> Unit,
    onBackspaceOnEmpty: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onLinkClick: (String) -> Unit,
    onLongPressChecklist: ((ChecklistBlock) -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onUpdate: (VisualBlock) -> Unit,
    onDelete: () -> Unit
) {
    val textColor = if (isDarkBg) Color.White else MaterialTheme.colorScheme.onSurface
    var showLangPicker by remember { mutableStateOf(false) }
    var isImageFullscreen by remember { mutableStateOf(false) }

    when (block) {
        is HeadingBlock -> {
            val fontSize = when (block.level) {
                1 -> 24.sp
                2 -> 20.sp
                3 -> 17.sp
                4 -> 15.sp
                else -> 13.sp
            }
            var isEditing by remember { mutableStateOf(block.text.isEmpty()) }
            var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
            val annotated = remember(block.text, isDarkBg) {
                InlineMarkdownHelper.parseInlineFormatting(block.text, isDark = isDarkBg)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (it.isFocused) onFocusGained() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isEditing || block.text.isEmpty()) {
                    OutlinedTextField(
                        value = block.text,
                        onValueChange = { input ->
                            if (input.isEmpty() && block.text.isEmpty()) {
                                onBackspaceOnEmpty()
                            } else if (input.contains("\n")) {
                                val clean = input.replace("\n", "")
                                onUpdate(block.copy(text = clean))
                                onEnterPressed()
                            } else {
                                onUpdate(block.copy(text = input))
                            }
                        },
                        placeholder = {
                            Text(
                                "Título H${block.level}...",
                                fontSize = fontSize,
                                fontWeight = FontWeight.Bold,
                                color = textColor.copy(alpha = 0.4f)
                            )
                        },
                        textStyle = MaterialTheme.typography.titleLarge.copy(
                            fontSize = fontSize,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                            .onFocusChanged {
                                if (!it.isFocused && block.text.isNotBlank()) isEditing = false
                            }
                    )
                } else {
                    Text(
                        text = annotated,
                        fontSize = fontSize,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        onTextLayout = { layoutResult = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .pointerInput(block.text) {
                                detectTapGestures { pos ->
                                    val offset = layoutResult?.getOffsetForPosition(pos) ?: -1
                                    if (offset != -1) {
                                        val links = annotated.getStringAnnotations("LINK", offset, offset)
                                        if (links.isNotEmpty()) {
                                            onLinkClick(links.first().item)
                                            return@detectTapGestures
                                        }
                                    }
                                    isEditing = true
                                    onFocusGained()
                                }
                            }
                    )
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Remover bloco", tint = Color.Gray, modifier = Modifier.size(15.dp))
                }
            }
        }

        is TextBlock -> {
            var isEditing by remember { mutableStateOf(block.text.isEmpty()) }
            var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
            val annotated = remember(block.text, isDarkBg) {
                InlineMarkdownHelper.parseInlineFormatting(block.text, isDark = isDarkBg)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged {
                        if (it.isFocused) {
                            onFocusGained()
                        }
                    },
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier.weight(1f)
                ) {
                    if (isEditing || block.text.isEmpty()) {
                        OutlinedTextField(
                            value = block.text,
                            onValueChange = { input ->
                                if (input.isEmpty() && block.text.isEmpty()) {
                                    onBackspaceOnEmpty()
                                } else if (input.contains("\n")) {
                                    val parts = input.split("\n", limit = 2)
                                    onUpdate(block.copy(text = parts[0]))
                                    onEnterPressed()
                                } else {
                                    onUpdate(block.copy(text = input))
                                }
                            },
                            placeholder = {
                                if (showPlaceholder) {
                                    Text("Escreva algo (suporta **negrito**, *itálico*, links)...", color = textColor.copy(alpha = 0.45f))
                                }
                            },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = textColor),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                                .onFocusChanged {
                                    if (!it.isFocused && block.text.isNotBlank()) {
                                        isEditing = false
                                    }
                                }
                        )
                    } else {
                        // Smooth visual display: hides symbols and makes links clickable
                        Text(
                            text = annotated,
                            style = MaterialTheme.typography.bodyMedium.copy(color = textColor),
                            onTextLayout = { layoutResult = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .pointerInput(block.text) {
                                    detectTapGestures(
                                        onTap = { pos ->
                                            val offset = layoutResult?.getOffsetForPosition(pos) ?: -1
                                            if (offset != -1) {
                                                val links = annotated.getStringAnnotations("LINK", offset, offset)
                                                if (links.isNotEmpty()) {
                                                    onLinkClick(links.first().item)
                                                    return@detectTapGestures
                                                }
                                            }
                                            isEditing = true
                                            onFocusGained()
                                        }
                                    )
                                }
                        )
                    }
                }

                if (block.text.isNotBlank()) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Remover", tint = Color.Gray.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        is ChecklistBlock -> {
            var isEditing by remember { mutableStateOf(block.text.isEmpty()) }
            var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
            val annotated = remember(block.text, isDarkBg) {
                InlineMarkdownHelper.parseInlineFormatting(block.text, isDark = isDarkBg)
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isDarkBg) Color.Black.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, top = 2.dp, bottom = 2.dp)
                    .onFocusChanged { if (it.isFocused) onFocusGained() }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = {
                                onLongPressChecklist?.invoke(block)
                            }
                        )
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = block.isChecked,
                        onCheckedChange = { onUpdate(block.copy(isChecked = it)) },
                        colors = CheckboxDefaults.colors(checkedColor = GreenAccent),
                        modifier = Modifier.size(36.dp)
                    )

                    if (isEditing || block.text.isEmpty()) {
                        OutlinedTextField(
                            value = block.text,
                            onValueChange = { input ->
                                if (input.isEmpty() && block.text.isEmpty()) {
                                    onBackspaceOnEmpty()
                                } else if (input.contains("\n")) {
                                    val parts = input.split("\n", limit = 2)
                                    onUpdate(block.copy(text = parts[0]))
                                    onEnterPressed()
                                } else {
                                    onUpdate(block.copy(text = input))
                                }
                            },
                            placeholder = { Text("Item de verificação...", color = textColor.copy(alpha = 0.4f)) },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = if (block.isChecked) textColor.copy(alpha = 0.45f) else textColor,
                                textDecoration = if (block.isChecked) TextDecoration.LineThrough else TextDecoration.None
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                                .onFocusChanged {
                                    if (!it.isFocused && block.text.isNotBlank()) isEditing = false
                                }
                        )
                    } else {
                        Text(
                            text = annotated,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = if (block.isChecked) textColor.copy(alpha = 0.45f) else textColor,
                                textDecoration = if (block.isChecked) TextDecoration.LineThrough else TextDecoration.None
                            ),
                            onTextLayout = { layoutResult = it },
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 8.dp)
                                .pointerInput(block.text) {
                                    detectTapGestures { pos ->
                                        val offset = layoutResult?.getOffsetForPosition(pos) ?: -1
                                        if (offset != -1) {
                                            val links = annotated.getStringAnnotations("LINK", offset, offset)
                                            if (links.isNotEmpty()) {
                                                onLinkClick(links.first().item)
                                                return@detectTapGestures
                                            }
                                        }
                                        isEditing = true
                                        onFocusGained()
                                    }
                                }
                        )
                    }

                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Remover", tint = Color.Gray, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        is BulletBlock -> {
            var isEditing by remember { mutableStateOf(block.text.isEmpty()) }
            var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
            val annotated = remember(block.text, isDarkBg) {
                InlineMarkdownHelper.parseInlineFormatting(block.text, isDark = isDarkBg)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 2.dp, bottom = 2.dp)
                    .onFocusChanged { if (it.isFocused) onFocusGained() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("•", fontSize = 20.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
                Spacer(modifier = Modifier.width(8.dp))

                if (isEditing || block.text.isEmpty()) {
                    OutlinedTextField(
                        value = block.text,
                        onValueChange = { input ->
                            if (input.isEmpty() && block.text.isEmpty()) {
                                onBackspaceOnEmpty()
                            } else if (input.contains("\n")) {
                                val parts = input.split("\n", limit = 2)
                                onUpdate(block.copy(text = parts[0]))
                                onEnterPressed()
                            } else {
                                onUpdate(block.copy(text = input))
                            }
                        },
                        placeholder = { Text("Tópico...", color = textColor.copy(alpha = 0.4f)) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = textColor),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                            .onFocusChanged {
                                if (!it.isFocused && block.text.isNotBlank()) isEditing = false
                            }
                    )
                } else {
                    Text(
                        text = annotated,
                        style = MaterialTheme.typography.bodyMedium.copy(color = textColor),
                        onTextLayout = { layoutResult = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp)
                            .pointerInput(block.text) {
                                detectTapGestures { pos ->
                                    val offset = layoutResult?.getOffsetForPosition(pos) ?: -1
                                    if (offset != -1) {
                                        val links = annotated.getStringAnnotations("LINK", offset, offset)
                                        if (links.isNotEmpty()) {
                                            onLinkClick(links.first().item)
                                            return@detectTapGestures
                                        }
                                    }
                                    isEditing = true
                                    onFocusGained()
                                }
                            }
                    )
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Remover", tint = Color.Gray, modifier = Modifier.size(14.dp))
                }
            }
        }

        is QuoteBlock -> {
            var isEditing by remember { mutableStateOf(block.text.isEmpty()) }
            var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
            val annotated = remember(block.text, isDarkBg) {
                InlineMarkdownHelper.parseInlineFormatting(block.text, isDark = isDarkBg)
            }

            Surface(
                shape = RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp),
                color = if (isDarkBg) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp)
                    )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(36.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    if (isEditing || block.text.isEmpty()) {
                        OutlinedTextField(
                            value = block.text,
                            onValueChange = { input ->
                                if (input.isEmpty() && block.text.isEmpty()) {
                                    onBackspaceOnEmpty()
                                } else if (input.contains("\n")) {
                                    val parts = input.split("\n", limit = 2)
                                    onUpdate(block.copy(text = parts[0]))
                                    onEnterPressed()
                                } else {
                                    onUpdate(block.copy(text = input))
                                }
                            },
                            placeholder = { Text("Citação em destaque...", fontStyle = FontStyle.Italic, color = textColor.copy(alpha = 0.4f)) },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic, color = textColor),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                                .onFocusChanged {
                                    if (!it.isFocused && block.text.isNotBlank()) isEditing = false
                                }
                        )
                    } else {
                        Text(
                            text = annotated,
                            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic, color = textColor),
                            onTextLayout = { layoutResult = it },
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 8.dp)
                                .pointerInput(block.text) {
                                    detectTapGestures { pos ->
                                        val offset = layoutResult?.getOffsetForPosition(pos) ?: -1
                                        if (offset != -1) {
                                            val links = annotated.getStringAnnotations("LINK", offset, offset)
                                            if (links.isNotEmpty()) {
                                                onLinkClick(links.first().item)
                                                return@detectTapGestures
                                            }
                                        }
                                        isEditing = true
                                        onFocusGained()
                                    }
                                }
                        )
                    }

                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Remover", tint = Color.Gray, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        is CodeBlock -> {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF0F172A),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF1E293B),
                            modifier = Modifier.clickable { showLangPicker = true }
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(block.language.uppercase(), color = Color(0xFF38BDF8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(3.dp))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = onToggleFullscreen, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Fullscreen, contentDescription = "Tela Cheia", tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Remover", tint = Color.LightGray, modifier = Modifier.size(14.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    BasicTextField(
                        value = block.code,
                        onValueChange = { input ->
                            if (input.isEmpty() && block.code.isEmpty()) {
                                onBackspaceOnEmpty()
                            } else {
                                onUpdate(block.copy(code = input))
                            }
                        },
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFF8FAFC)
                        ),
                        cursorBrush = SolidColor(Color(0xFF38BDF8)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                    )

                    // Exit code button
                    TextButton(
                        onClick = onEnterPressed,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Sair do bloco de código ↵", fontSize = 10.sp, color = Color(0xFF38BDF8))
                    }
                }
            }
        }

        is MathBlock -> {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isDarkBg) Color.Black.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📐 Equação Matemática", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = onToggleFullscreen, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Fullscreen, contentDescription = "Tela Cheia", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Remover", tint = Color.Gray, modifier = Modifier.size(14.dp))
                            }
                        }
                    }

                    OutlinedTextField(
                        value = block.formula,
                        onValueChange = { input ->
                            if (input.isEmpty() && block.formula.isEmpty()) {
                                onBackspaceOnEmpty()
                            } else if (input.contains("\n")) {
                                onUpdate(block.copy(formula = input.replace("\n", "")))
                                onEnterPressed()
                            } else {
                                onUpdate(block.copy(formula = input))
                            }
                        },
                        placeholder = { Text("Ex: E = mc^2 ou \\frac{a}{b}") },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        is TableBlock -> {
            ExcelTableEditor(
                block = block,
                isDarkBg = isDarkBg,
                onUpdate = onUpdate,
                onDelete = onDelete
            )
        }

        is DividerBlock -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                IconButton(onClick = onDelete, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Remover", tint = Color.Gray, modifier = Modifier.size(12.dp))
                }
            }
        }

        is ImageBlock -> {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Imagem", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            // Fullscreen View button
                            if (block.url.isNotBlank()) {
                                IconButton(
                                    onClick = { isImageFullscreen = true },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Fullscreen, contentDescription = "Tela Cheia", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                }
                            }

                            // Alignment toggles (Left, Center, Right)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (block.alignment == "left") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.clickable { onUpdate(block.copy(alignment = "left")) }
                            ) {
                                Text("Esq", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (block.alignment == "left") MaterialTheme.colorScheme.primary else Color.Gray, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (block.alignment == "center") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.clickable { onUpdate(block.copy(alignment = "center")) }
                            ) {
                                Text("Centro", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (block.alignment == "center") MaterialTheme.colorScheme.primary else Color.Gray, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (block.alignment == "right") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.clickable { onUpdate(block.copy(alignment = "right")) }
                            ) {
                                Text("Dir", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (block.alignment == "right") MaterialTheme.colorScheme.primary else Color.Gray, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }

                            IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Remover", tint = Color.Gray, modifier = Modifier.size(14.dp))
                            }
                        }
                    }

                    // Width percent selector (25%, 50%, 75%, 100%)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(25, 50, 75, 100).forEach { pct ->
                            FilterChip(
                                selected = block.widthPercent == pct,
                                onClick = { onUpdate(block.copy(widthPercent = pct)) },
                                label = { Text("$pct%", fontSize = 10.sp) }
                            )
                        }
                    }

                    // Side-by-side or Centered image preview
                    if (block.url.isNotBlank()) {
                        val hasSideText = block.widthPercent < 100 && (block.alignment == "left" || block.alignment == "right")
                        val imageFraction = (block.widthPercent / 100f).coerceIn(0.25f, 0.75f)

                        if (hasSideText) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                if (block.alignment == "left") {
                                    Box(modifier = Modifier.weight(imageFraction)) {
                                        AsyncImage(
                                            model = block.url,
                                            contentDescription = "Imagem",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 220.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { isImageFullscreen = true }
                                        )
                                    }
                                    OutlinedTextField(
                                        value = block.sideText,
                                        onValueChange = { onUpdate(block.copy(sideText = it)) },
                                        placeholder = { Text("Texto ao lado da imagem...", fontSize = 12.sp) },
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                        modifier = Modifier.weight(1f - imageFraction)
                                    )
                                } else {
                                    OutlinedTextField(
                                        value = block.sideText,
                                        onValueChange = { onUpdate(block.copy(sideText = it)) },
                                        placeholder = { Text("Texto ao lado da imagem...", fontSize = 12.sp) },
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                        modifier = Modifier.weight(1f - imageFraction)
                                    )
                                    Box(modifier = Modifier.weight(imageFraction)) {
                                        AsyncImage(
                                            model = block.url,
                                            contentDescription = "Imagem",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 220.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { isImageFullscreen = true }
                                        )
                                    }
                                }
                            }
                        } else {
                            val alignArrangement = when (block.alignment) {
                                "left" -> Arrangement.Start
                                "right" -> Arrangement.End
                                else -> Arrangement.Center
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = alignArrangement) {
                                AsyncImage(
                                    model = block.url,
                                    contentDescription = "Imagem",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth(block.widthPercent / 100f)
                                        .heightIn(max = 260.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { isImageFullscreen = true }
                                )
                            }
                        }
                    }
                }
            }

            // Fullscreen dialog for Image
            if (isImageFullscreen) {
                Dialog(
                    onDismissRequest = { isImageFullscreen = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.95f)),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = block.url,
                            contentDescription = "Imagem em tela cheia",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(16.dp)
                        )

                        IconButton(
                            onClick = { isImageFullscreen = false },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(24.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Fechar tela cheia", tint = Color.White)
                        }
                    }
                }
            }
        }

        is AudioBlock -> {
            AudioBlockPlayer(
                block = block,
                onUpdate = onUpdate,
                onDelete = onDelete
            )
        }

        is CalloutBlock -> {
            val (bgColor, borderColor, icon, titleText) = when (block.type) {
                "tip" -> Quadruple(Color(0xFFECFDF5), Color(0xFF10B981), Icons.Default.Lightbulb, "DICA")
                "warning" -> Quadruple(Color(0xFFFFFBEB), Color(0xFFF59E0B), Icons.Default.Warning, "AVISO")
                "important" -> Quadruple(Color(0xFFFEF2F2), Color(0xFFEF4444), Icons.Default.Error, "IMPORTANTE")
                else -> Quadruple(Color(0xFFEFF6FF), Color(0xFF3B82F6), Icons.Default.Info, "INFO")
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = bgColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, borderColor, RoundedCornerShape(10.dp))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(icon, contentDescription = null, tint = borderColor, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(titleText, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = borderColor)
                        }

                        IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Remover", tint = Color.Gray, modifier = Modifier.size(14.dp))
                        }
                    }

                    OutlinedTextField(
                        value = block.text,
                        onValueChange = { input ->
                            if (input.isEmpty() && block.text.isEmpty()) {
                                onBackspaceOnEmpty()
                            } else if (input.contains("\n")) {
                                onUpdate(block.copy(text = input.replace("\n", "")))
                                onEnterPressed()
                            } else {
                                onUpdate(block.copy(text = input))
                            }
                        },
                        placeholder = { Text("Texto do aviso...") },
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF1E293B)),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        is ToggleBlock -> {
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onUpdate(block.copy(isExpanded = !block.isExpanded)) }
                        ) {
                            Icon(
                                if (block.isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            OutlinedTextField(
                                value = block.title,
                                onValueChange = { onUpdate(block.copy(title = it)) },
                                placeholder = { Text("Título recolhível...") },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = textColor),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Remover", tint = Color.Gray, modifier = Modifier.size(14.dp))
                        }
                    }

                    AnimatedVisibility(visible = block.isExpanded) {
                        Column {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            OutlinedTextField(
                                value = block.content,
                                onValueChange = { onUpdate(block.copy(content = it)) },
                                placeholder = { Text("Conteúdo recolhível...") },
                                textStyle = MaterialTheme.typography.bodySmall.copy(color = textColor),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

/**
 * WhatsApp-style Audio block player with progress waveform bar, scrubbing,
 * inline title editing, elapsed/total time, and volume/loop toggles.
 */
@Composable
private fun AudioBlockPlayer(
    block: AudioBlock,
    onUpdate: (AudioBlock) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val playbackState by NoteAudioPlaybackManager.playbackState.collectAsState()
    val isCurrentAudio = NoteAudioPlaybackManager.isCurrentAudio(block.id, block.url)
    val isPlayingThis = isCurrentAudio && playbackState.isPlaying

    var isEditingTitle by remember { mutableStateOf(false) }
    var showVolumeSlider by remember { mutableStateOf(false) }
    var localVolume by remember(block.volume) { mutableFloatStateOf(block.volume) }

    val currentPosition = if (isCurrentAudio) playbackState.currentPositionMs else 0
    val duration = if (isCurrentAudio) playbackState.durationMs else 0

    fun formatTime(ms: Int): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val mins = totalSec / 60
        val secs = totalSec % 60
        return "%02d:%02d".format(mins, secs)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header: Inline editable title and remove button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.Audiotrack,
                        contentDescription = null,
                        tint = if (isPlayingThis) GreenAccent else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))

                    if (isEditingTitle) {
                        BasicTextField(
                            value = block.title,
                            onValueChange = { onUpdate(block.copy(title = it)) },
                            textStyle = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { isEditingTitle = false },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Concluir", tint = GreenAccent, modifier = Modifier.size(14.dp))
                        }
                    } else {
                        Text(
                            text = if (block.title.isNotBlank()) block.title else "Áudio importado",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (isPlayingThis) GreenAccent else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { isEditingTitle = true }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Editar título",
                            tint = Color.Gray.copy(alpha = 0.6f),
                            modifier = Modifier.size(12.dp).clickable { isEditingTitle = true }
                        )
                    }
                }

                IconButton(
                    onClick = {
                        if (isCurrentAudio) {
                            NoteAudioPlaybackManager.stop()
                        }
                        onDelete()
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Remover", tint = Color.Gray, modifier = Modifier.size(14.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // WhatsApp-style Voice Note Scrubber Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Circular Play / Pause button
                IconButton(
                    onClick = {
                        NoteAudioPlaybackManager.playOrPause(
                            context = context,
                            blockId = block.id,
                            url = block.url,
                            volume = block.volume,
                            isLoop = block.isLoop
                        )
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            if (isPlayingThis) GreenAccent else MaterialTheme.colorScheme.primary,
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (isPlayingThis) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlayingThis) "Pausar" else "Reproduzir",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Interactive waveform progress bar + elapsed / total duration
                Column(modifier = Modifier.weight(1f)) {
                    val progressRatio = if (duration > 0) (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

                    Slider(
                        value = progressRatio,
                        onValueChange = { ratio ->
                            if (duration > 0) {
                                val targetMs = (ratio * duration).toInt()
                                NoteAudioPlaybackManager.seekTo(targetMs)
                            }
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = if (isPlayingThis) GreenAccent else MaterialTheme.colorScheme.primary,
                            activeTrackColor = if (isPlayingThis) GreenAccent else MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.height(24.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatTime(currentPosition), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (duration > 0) formatTime(duration) else "--:--", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Volume Toggle Button
                IconButton(
                    onClick = { showVolumeSlider = !showVolumeSlider },
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            if (showVolumeSlider) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Ajustar volume",
                        tint = if (showVolumeSlider) MaterialTheme.colorScheme.primary else Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Loop Toggle Button
                IconButton(
                    onClick = {
                        val newLoop = !block.isLoop
                        onUpdate(block.copy(isLoop = newLoop))
                        if (isCurrentAudio) {
                            NoteAudioPlaybackManager.setLoop(newLoop)
                        }
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            if (block.isLoop) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = "Repetir em loop",
                        tint = if (block.isLoop) MaterialTheme.colorScheme.primary else Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Volume Adjustment Slider Row
            if (showVolumeSlider) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, start = 8.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Slider(
                        value = localVolume,
                        onValueChange = { newVol ->
                            localVolume = newVol
                            NoteAudioPlaybackManager.setVolume(newVol)
                        },
                        onValueChangeFinished = {
                            onUpdate(block.copy(volume = localVolume))
                        },
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f).height(20.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Text(
                        text = "${(localVolume * 100).toInt()}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * Excel-style spreadsheet table with clean sharp grid lines, bold darker header row,
 * in-place editable cells, real-time sync with Markdown, and horizontal scrolling for wide tables.
 */
@Composable
fun ExcelTableEditor(
    block: TableBlock,
    isDarkBg: Boolean,
    onUpdate: (TableBlock) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val gridBorderColor = if (isDarkBg) Color(0xFF475569) else Color(0xFFCBD5E1)
    val headerBgColor = if (isDarkBg) Color(0xFF334155) else Color(0xFFE2E8F0)
    val headerTextColor = if (isDarkBg) Color(0xFFF1F5F9) else Color(0xFF0F172A)
    val cellTextColor = if (isDarkBg) Color.White else Color(0xFF1E293B)
    val colWidth = 130.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // Excel Top Action Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.TableChart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Planilha (${block.headers.size} cols × ${block.rows.size} lins)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Add Column
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.clickable {
                        val nextColNum = block.headers.size + 1
                        val newHeaders = block.headers + "Coluna $nextColNum"
                        val newRows = block.rows.map { it + "" }
                        onUpdate(block.copy(headers = newHeaders, rows = newRows))
                    }
                ) {
                    Text("+ Col", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }

                // Add Row
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.clickable {
                        val newRow = List(block.headers.size) { "" }
                        onUpdate(block.copy(rows = block.rows + listOf(newRow)))
                    }
                ) {
                    Text("+ Linha", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }

                // Remove Col (if > 1)
                if (block.headers.size > 1) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.clickable {
                            val newHeaders = block.headers.dropLast(1)
                            val newRows = block.rows.map { it.dropLast(1) }
                            onUpdate(block.copy(headers = newHeaders, rows = newRows))
                        }
                    ) {
                        Text("- Col", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }

                // Remove Row (if > 1)
                if (block.rows.size > 1) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.clickable {
                            val newRows = block.rows.dropLast(1)
                            onUpdate(block.copy(rows = newRows))
                        }
                    ) {
                        Text("- Linha", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }

                if (onDelete != null) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Remover Tabela", tint = Color.Gray, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        // Excel Spreadsheet Grid with Horizontal Scroll and Sharp Borders (No rounded bubbles!)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, gridBorderColor)
                .horizontalScroll(rememberScrollState())
        ) {
            Column {
                // Header Row (Primeira linha mais carregada, estilo cabeçalho do Excel)
                Row(
                    modifier = Modifier
                        .background(headerBgColor)
                        .border(width = 0.5.dp, color = gridBorderColor)
                ) {
                    block.headers.forEachIndexed { colIdx, header ->
                        Box(
                            modifier = Modifier
                                .width(colWidth)
                                .height(38.dp)
                                .border(width = 0.5.dp, color = gridBorderColor)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            BasicTextField(
                                value = header,
                                onValueChange = { newH ->
                                    val newHeaders = block.headers.toMutableList().apply { set(colIdx, newH) }
                                    onUpdate(block.copy(headers = newHeaders))
                                },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = headerTextColor
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Data Rows (Células no estilo planilha Excel com edição direta)
                block.rows.forEachIndexed { rowIdx, row ->
                    val rowBg = if (rowIdx % 2 == 0) {
                        if (isDarkBg) Color(0xFF1E293B) else Color.White
                    } else {
                        if (isDarkBg) Color(0xFF0F172A) else Color(0xFFF8FAFC)
                    }

                    Row(
                        modifier = Modifier
                            .background(rowBg)
                            .border(width = 0.5.dp, color = gridBorderColor)
                    ) {
                        // Ensure row has same count of cells as headers
                        val safeCells = if (row.size < block.headers.size) {
                            row + List(block.headers.size - row.size) { "" }
                        } else row

                        safeCells.take(block.headers.size).forEachIndexed { colIdx, cell ->
                            Box(
                                modifier = Modifier
                                    .width(colWidth)
                                    .height(38.dp)
                                    .border(width = 0.5.dp, color = gridBorderColor)
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                BasicTextField(
                                    value = cell,
                                    onValueChange = { newCell ->
                                        val newRows = block.rows.map { it.toMutableList() }.toMutableList()
                                        while (newRows.size <= rowIdx) {
                                            newRows.add(MutableList(block.headers.size) { "" })
                                        }
                                        while (newRows[rowIdx].size <= colIdx) {
                                            newRows[rowIdx].add("")
                                        }
                                        newRows[rowIdx][colIdx] = newCell
                                        onUpdate(block.copy(rows = newRows))
                                    },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.sp,
                                        color = cellTextColor
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

