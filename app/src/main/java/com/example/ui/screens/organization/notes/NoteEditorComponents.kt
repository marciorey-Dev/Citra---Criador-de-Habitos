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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.GreenAccent
import com.example.util.CodeHighlighter
import com.example.util.InlineMarkdownHelper
import com.example.util.MathBeautifier

/**
 * Visual renderer for individual Markdown blocks with smooth inline formatting,
 * dynamic tables, Prism.js code highlight, real-time math beautification,
 * audio player, image resizer, and list/checklist navigation loops.
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
    onUpdate: (VisualBlock) -> Unit,
    onDelete: () -> Unit
) {
    val textColor = if (isDarkBg) Color.White else MaterialTheme.colorScheme.onSurface
    var showLangPicker by remember { mutableStateOf(false) }

    when (block) {
        is HeadingBlock -> {
            val fontSize = when (block.level) {
                1 -> 24.sp
                2 -> 20.sp
                3 -> 17.sp
                4 -> 15.sp
                else -> 13.sp
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (it.isFocused) onFocusGained() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = block.text,
                    onValueChange = { input ->
                        if (input.contains("\n")) {
                            onUpdate(block.copy(text = input.replace("\n", "")))
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
                )

                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Remover bloco", tint = Color.Gray, modifier = Modifier.size(15.dp))
                }
            }
        }

        is TextBlock -> {
            var isEditing by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged {
                        if (it.isFocused) {
                            onFocusGained()
                            isEditing = true
                        }
                    },
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .pointerInput(block.text) {
                            detectTapGestures(
                                onTap = {
                                    isEditing = true
                                },
                                onLongPress = {
                                    isEditing = true
                                }
                            )
                        }
                ) {
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
                    )
                }

                if (block.text.isNotBlank()) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Remover", tint = Color.Gray.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        is ChecklistBlock -> {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isDarkBg) Color.Black.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, top = 2.dp, bottom = 2.dp) // 24dp tab indentation + half vertical spacing
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
                    OutlinedTextField(
                        value = block.text,
                        onValueChange = { input ->
                            if (input.contains("\n")) {
                                if (block.text.isBlank()) {
                                    // Empty checklist item with Enter -> exits loop! Converts to TextBlock
                                    onUpdate(TextBlock(text = ""))
                                } else {
                                    // Enter inside item -> creates next checklist item below
                                    onUpdate(block.copy(text = input.replace("\n", "")))
                                    onEnterPressed()
                                }
                            } else {
                                onUpdate(block.copy(text = input))
                            }
                        },
                        placeholder = { Text("Item de checklist...", fontSize = 13.sp) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            textDecoration = if (block.isChecked) TextDecoration.LineThrough else TextDecoration.None,
                            color = if (block.isChecked) textColor.copy(alpha = 0.55f) else textColor
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                    )

                    IconButton(
                        onClick = { onLongPressChecklist?.invoke(block) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.TaskAlt, contentDescription = "Converter em Tarefa", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                    }

                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Remover", tint = Color.Gray, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        is BulletBlock -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, top = 2.dp, bottom = 2.dp) // 24dp tab indentation + half vertical spacing
                    .onFocusChanged { if (it.isFocused) onFocusGained() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "•",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )

                OutlinedTextField(
                    value = block.text,
                    onValueChange = { input ->
                        if (input.contains("\n")) {
                            if (block.text.isBlank()) {
                                // Enter on empty item -> exits loop! Converts to TextBlock
                                onUpdate(TextBlock(text = ""))
                            } else {
                                // Enter inside item -> creates next bullet item below
                                onUpdate(block.copy(text = input.replace("\n", "")))
                                onEnterPressed()
                            }
                        } else {
                            onUpdate(block.copy(text = input))
                        }
                    },
                    placeholder = { Text("Item da lista...", fontSize = 13.sp) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = textColor),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                )

                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Remover", tint = Color.Gray, modifier = Modifier.size(15.dp))
                }
            }
        }

        is TableBlock -> {
            val colCount = block.headers.size.coerceIn(1, 5)
            val dynamicFontSize = when (colCount) {
                1 -> 13.sp
                2 -> 12.sp
                3 -> 11.sp
                4 -> 10.sp
                else -> 9.sp
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    .onFocusChanged { if (it.isFocused) onFocusGained() }
            ) {
                // Table Toolbar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tabela (${colCount} colunas)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = {
                                val newRows = block.rows + listOf(block.headers.map { "" })
                                onUpdate(block.copy(rows = newRows))
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("+ Linha", fontSize = 10.sp)
                        }

                        if (block.rows.size > 1) {
                            TextButton(
                                onClick = {
                                    val newRows = block.rows.dropLast(1)
                                    onUpdate(block.copy(rows = newRows))
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("- Linha", fontSize = 10.sp)
                            }
                        }

                        if (colCount < 5) {
                            TextButton(
                                onClick = {
                                    val newHeaders = block.headers + "Col ${colCount + 1}"
                                    val newRows = block.rows.map { it + "" }
                                    onUpdate(block.copy(headers = newHeaders, rows = newRows))
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("+ Col", fontSize = 10.sp)
                            }
                        }

                        IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Remover", tint = Color.Gray, modifier = Modifier.size(14.dp))
                        }
                    }
                }

                // Table Horizontal Scroll to prevent text clipping
                Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    // Header Row
                    Row(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        block.headers.forEachIndexed { hIdx, headerText ->
                            Box(
                                modifier = Modifier
                                    .widthIn(min = 90.dp, max = 220.dp)
                                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                    .padding(horizontal = 6.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                OutlinedTextField(
                                    value = headerText,
                                    onValueChange = { newH ->
                                        val newHeaders = block.headers.toMutableList().apply { set(hIdx, newH) }
                                        onUpdate(block.copy(headers = newHeaders))
                                    },
                                    textStyle = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = dynamicFontSize,
                                        textAlign = TextAlign.Center
                                    ),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color.Transparent,
                                        unfocusedBorderColor = Color.Transparent
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    // Data Rows
                    block.rows.forEachIndexed { rIdx, rowList ->
                        Row(modifier = Modifier.border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)) {
                            rowList.forEachIndexed { cIdx, cellText ->
                                Box(
                                    modifier = Modifier
                                        .widthIn(min = 90.dp, max = 220.dp)
                                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    OutlinedTextField(
                                        value = cellText,
                                        onValueChange = { newCell ->
                                            if (newCell.contains("\n")) {
                                                // Enter key behavior in table:
                                                if (rIdx < block.rows.size - 1) {
                                                    // Moves cursor to next row
                                                    val cleaned = newCell.replace("\n", "")
                                                    val newRows = block.rows.toMutableList().apply {
                                                        val updated = rowList.toMutableList().apply { set(cIdx, cleaned) }
                                                        set(rIdx, updated)
                                                    }
                                                    onUpdate(block.copy(rows = newRows))
                                                } else {
                                                    // At the bottom row, Enter exits table and creates paragraph below
                                                    val cleaned = newCell.replace("\n", "")
                                                    val newRows = block.rows.toMutableList().apply {
                                                        val updated = rowList.toMutableList().apply { set(cIdx, cleaned) }
                                                        set(rIdx, updated)
                                                    }
                                                    onUpdate(block.copy(rows = newRows))
                                                    onEnterPressed()
                                                }
                                            } else {
                                                val newRows = block.rows.toMutableList().apply {
                                                    val updated = rowList.toMutableList().apply { set(cIdx, newCell) }
                                                    set(rIdx, updated)
                                                }
                                                onUpdate(block.copy(rows = newRows))
                                            }
                                        },
                                        textStyle = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = dynamicFontSize,
                                            textAlign = TextAlign.Center
                                        ),
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

        is QuoteBlock -> {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    .onFocusChanged { if (it.isFocused) onFocusGained() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.FormatQuote, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    OutlinedTextField(
                        value = block.text,
                        onValueChange = { input ->
                            if (input.contains("\n")) {
                                onUpdate(block.copy(text = input.replace("\n", "")))
                                onEnterPressed()
                            } else {
                                onUpdate(block.copy(text = input))
                            }
                        },
                        placeholder = { Text("Citação inspiradora...") },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic, color = textColor),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Remover", tint = Color.Gray, modifier = Modifier.size(15.dp))
                    }
                }
            }
        }

        is CodeBlock -> {
            val supportedLanguages = listOf(
                "kotlin", "java", "python", "javascript", "typescript",
                "html", "css", "sql", "json", "c++", "c#", "rust", "swift", "go", "bash"
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1E293B),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (it.isFocused) onFocusGained() }
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Language Badge (Tappable to select or change language)
                        Box {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF0F172A),
                                modifier = Modifier.clickable { showLangPicker = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = block.language.uppercase(),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF38BDF8)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                                }
                            }

                            DropdownMenu(
                                expanded = showLangPicker,
                                onDismissRequest = { showLangPicker = false }
                            ) {
                                supportedLanguages.forEach { lang ->
                                    DropdownMenuItem(
                                        text = { Text(lang.uppercase()) },
                                        onClick = {
                                            showLangPicker = false
                                            onUpdate(block.copy(language = lang))
                                        }
                                    )
                                }
                            }
                        }

                        Row {
                            IconButton(onClick = onToggleFullscreen, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Fullscreen, contentDescription = "Tela Cheia", tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Remover", tint = Color.Gray, modifier = Modifier.size(14.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = block.code,
                        onValueChange = { onUpdate(block.copy(code = it)) },
                        placeholder = { Text("// Digite o código...", color = Color.Gray) },
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFF1F5F9),
                            lineHeight = 18.sp
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        is MathBlock -> {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (it.isFocused) onFocusGained() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Functions, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))

                    OutlinedTextField(
                        value = block.formula,
                        onValueChange = { input ->
                            // Automatically beautifies math syntax (2^3 -> 2³, 3/5 -> ⅗)
                            val beautified = MathBeautifier.beautify(input)
                            onUpdate(block.copy(formula = beautified))
                        },
                        placeholder = { Text("Fórmula (ex: 2^3 + 3/5 = x)") },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic,
                            color = textColor
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(onClick = onToggleFullscreen, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Fullscreen, contentDescription = "Tela Cheia", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Remover", tint = Color.Gray, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        is DividerBlock -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Remover divisor", tint = Color.Gray, modifier = Modifier.size(14.dp))
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

                        // Alignment toggles (Left, Center, Right)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = { onUpdate(block.copy(alignment = "left")) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.FormatAlignLeft, contentDescription = "Esquerda", tint = if (block.alignment == "left") MaterialTheme.colorScheme.primary else Color.Gray, modifier = Modifier.size(14.dp))
                            }
                            IconButton(
                                onClick = { onUpdate(block.copy(alignment = "center")) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.FormatAlignCenter, contentDescription = "Centro", tint = if (block.alignment == "center") MaterialTheme.colorScheme.primary else Color.Gray, modifier = Modifier.size(14.dp))
                            }
                            IconButton(
                                onClick = { onUpdate(block.copy(alignment = "right")) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.FormatAlignRight, contentDescription = "Direita", tint = if (block.alignment == "right") MaterialTheme.colorScheme.primary else Color.Gray, modifier = Modifier.size(14.dp))
                            }

                            IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Remover", tint = Color.Gray, modifier = Modifier.size(14.dp))
                            }
                        }
                    }

                    // Width percent selector (25%, 50%, 75%, 100%)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
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

                    // Image preview
                    if (block.url.isNotBlank()) {
                        val alignArrangement = when (block.alignment) {
                            "left" -> Arrangement.Start
                            "right" -> Arrangement.End
                            else -> Arrangement.Center
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = alignArrangement) {
                            AsyncImage(
                                model = block.url,
                                contentDescription = block.alt,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth(block.widthPercent / 100f)
                                    .heightIn(max = 240.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    OutlinedTextField(
                        value = block.url,
                        onValueChange = { onUpdate(block.copy(url = it)) },
                        placeholder = { Text("URL ou caminho da imagem...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = block.alt,
                        onValueChange = { onUpdate(block.copy(alt = it)) },
                        placeholder = { Text("Legenda descritiva...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
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
                        onValueChange = { onUpdate(block.copy(text = it)) },
                        placeholder = { Text("Nota de destaque...", color = Color.Gray) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF0F172A)),
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
 * Audio block player with play/pause, volume slider, loop toggle, and title input.
 */
@Composable
private fun AudioBlockPlayer(
    block: AudioBlock,
    onUpdate: (AudioBlock) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(block.url) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Audiotrack, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (block.title.isNotBlank()) block.title else "Áudio",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Remover", tint = Color.Gray, modifier = Modifier.size(14.dp))
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Play / Pause + Volume + Loop Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = {
                        if (isPlaying) {
                            mediaPlayer?.pause()
                            isPlaying = false
                        } else {
                            if (mediaPlayer == null && block.url.isNotBlank()) {
                                try {
                                    val mp = MediaPlayer().apply {
                                        setAudioAttributes(
                                            AudioAttributes.Builder()
                                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                                .build()
                                        )
                                        setDataSource(context, Uri.parse(block.url))
                                        isLooping = block.isLoop
                                        setVolume(block.volume, block.volume)
                                        prepare()
                                        setOnCompletionListener {
                                            if (!block.isLoop) isPlaying = false
                                        }
                                    }
                                    mediaPlayer = mp
                                } catch (_: Exception) {}
                            }
                            mediaPlayer?.start()
                            isPlaying = true
                        }
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pausar" else "Reproduzir",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Volume Slider
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Volume", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${(block.volume * 100).toInt()}%", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = block.volume,
                        onValueChange = { vol ->
                            onUpdate(block.copy(volume = vol))
                            mediaPlayer?.setVolume(vol, vol)
                        },
                        valueRange = 0f..1f,
                        modifier = Modifier.height(20.dp)
                    )
                }

                // Loop Toggle Button
                IconButton(
                    onClick = {
                        val newLoop = !block.isLoop
                        onUpdate(block.copy(isLoop = newLoop))
                        mediaPlayer?.isLooping = newLoop
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

            Spacer(modifier = Modifier.height(4.dp))

            OutlinedTextField(
                value = block.title,
                onValueChange = { onUpdate(block.copy(title = it)) },
                label = { Text("Nome do áudio") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
