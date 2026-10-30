package com.example.ui.screens.organization.notes

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.Habit
import com.example.data.model.Note
import com.example.data.model.Task
import com.example.util.CodeHighlighter
import com.example.util.MathBeautifier
import java.text.SimpleDateFormat
import java.util.*

/**
 * Fullscreen focused environment for Code or Math blocks with Undo, Redo, Copy, and Paste.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenBlockDialog(
    block: VisualBlock,
    onDismiss: () -> Unit,
    onUpdate: (VisualBlock) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = remember {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    var textState by remember(block) {
        mutableStateOf(
            when (block) {
                is CodeBlock -> block.code
                is MathBlock -> block.formula
                else -> ""
            }
        )
    }

    var selectedLang by remember(block) {
        mutableStateOf(if (block is CodeBlock) block.language else "kotlin")
    }
    var showLangPicker by remember { mutableStateOf(false) }

    // Undo / Redo history within fullscreen mode
    val historyUndo = remember { mutableStateListOf<String>() }
    val historyRedo = remember { mutableStateListOf<String>() }

    fun updateContent(newVal: String) {
        historyUndo.add(textState)
        if (historyUndo.size > 30) historyUndo.removeAt(0)
        historyRedo.clear()
        textState = newVal
        if (block is CodeBlock) {
            onUpdate(block.copy(code = newVal, language = selectedLang))
        } else if (block is MathBlock) {
            onUpdate(block.copy(formula = MathBeautifier.beautify(newVal)))
        }
    }

    fun undo() {
        if (historyUndo.isNotEmpty()) {
            val prev = historyUndo.removeAt(historyUndo.size - 1)
            historyRedo.add(textState)
            textState = prev
            if (block is CodeBlock) onUpdate(block.copy(code = prev))
            else if (block is MathBlock) onUpdate(block.copy(formula = prev))
        }
    }

    fun redo() {
        if (historyRedo.isNotEmpty()) {
            val next = historyRedo.removeAt(historyRedo.size - 1)
            historyUndo.add(textState)
            textState = next
            if (block is CodeBlock) onUpdate(block.copy(code = next))
            else if (block is MathBlock) onUpdate(block.copy(formula = next))
        }
    }

    fun copyToClipboard() {
        val clip = ClipData.newPlainText("Citra Block", textState)
        clipboardManager.setPrimaryClip(clip)
        Toast.makeText(context, "Copiado para a área de transferência!", Toast.LENGTH_SHORT).show()
    }

    fun pasteFromClipboard() {
        val clip = clipboardManager.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val pasteText = clip.getItemAt(0).text?.toString() ?: ""
            if (pasteText.isNotEmpty()) {
                updateContent(textState + pasteText)
                Toast.makeText(context, "Texto colado!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val supportedLanguages = listOf(
        "kotlin", "java", "python", "javascript", "typescript",
        "html", "css", "sql", "json", "c++", "c", "c#", "rust", "swift", "go", "bash", "markdown"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = if (block is CodeBlock) Color(0xFF0F172A) else MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Top Action Bar: Title, Undo, Redo, Copy, Paste, Exit
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (block is CodeBlock) Icons.Default.Code else Icons.Default.Functions,
                            contentDescription = null,
                            tint = if (block is CodeBlock) Color(0xFF38BDF8) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        if (block is CodeBlock) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF1E293B),
                                modifier = Modifier.clickable { showLangPicker = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = selectedLang.uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color(0xFF38BDF8)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
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
                                            selectedLang = lang
                                            showLangPicker = false
                                            onUpdate(block.copy(language = lang))
                                        }
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "Editor Matemático Focado",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Action buttons: Undo, Redo, Copy, Paste, Close
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { undo() },
                            enabled = historyUndo.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "Desfazer",
                                tint = if (historyUndo.isNotEmpty()) Color.White else Color.Gray.copy(alpha = 0.4f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = { redo() },
                            enabled = historyRedo.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Redo,
                                contentDescription = "Refazer",
                                tint = if (historyRedo.isNotEmpty()) Color.White else Color.Gray.copy(alpha = 0.4f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(onClick = { copyToClipboard() }) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copiar",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(onClick = { pasteFromClipboard() }) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = "Colar",
                                tint = Color(0xFF4ADE80),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FullscreenExit,
                                contentDescription = "Sair da Tela Cheia",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (block is CodeBlock) {
                    OutlinedTextField(
                        value = textState,
                        onValueChange = { updateContent(it) },
                        placeholder = { Text("// Digite ou cole seu código...", color = Color.Gray) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFF8FAFC),
                            lineHeight = 22.sp
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF334155),
                            unfocusedBorderColor = Color(0xFF1E293B)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                } else if (block is MathBlock) {
                    Column(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = textState,
                            onValueChange = { updateContent(it) },
                            placeholder = { Text("Ex: 2^3 + 3/5 = x, \\int, \\sqrt...") },
                            textStyle = MaterialTheme.typography.headlineSmall.copy(
                                fontFamily = FontFamily.Serif,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        // Real-time formatted preview card
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Pré-visualização Matemática Formatada:", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = MathBeautifier.beautify(textState).ifBlank { "Nenhuma fórmula digitada" },
                                    fontSize = 18.sp,
                                    fontFamily = FontFamily.Serif,
                                    fontStyle = FontStyle.Italic,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dialog to insert or edit links with format `url|label` or link to another note.
 */
@Composable
fun LinkEditorDialog(
    initialTarget: String = "",
    initialLabel: String = "",
    allNotes: List<Note>,
    onDismiss: () -> Unit,
    onConfirm: (target: String, label: String) -> Unit
) {
    var target by remember { mutableStateOf(initialTarget) }
    var label by remember { mutableStateOf(initialLabel) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Inserir ou Editar Link", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Vincule a um site da web ou ao título de outra nota existente.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = { Text("URL ou Nome da Nota") },
                    placeholder = { Text("ex: https://meusite.com ou Nota de Estudo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Texto de Exibição (Opcional |)") },
                    placeholder = { Text("ex: Artigo Interessante") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick note selector chips
                if (allNotes.isNotEmpty()) {
                    Text("Notas Disponíveis:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    LazyColumn(modifier = Modifier.heightIn(max = 120.dp)) {
                        items(allNotes.take(6)) { note ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clickable {
                                        target = note.title
                                        if (label.isBlank()) label = note.title
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Note, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(note.title, fontSize = 12.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (target.isNotBlank()) {
                        onConfirm(target.trim(), label.trim())
                    }
                },
                enabled = target.isNotBlank()
            ) {
                Text("Aplicar Link")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

/**
 * In-note Find & Replace Dialog
 */
@Composable
fun FindReplaceDialog(
    onDismiss: () -> Unit,
    onReplace: (find: String, replaceWith: String) -> Unit
) {
    var findText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FindReplace, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Localizar e Substituir", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = findText,
                    onValueChange = { findText = it },
                    label = { Text("Localizar texto") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = replaceText,
                    onValueChange = { replaceText = it },
                    label = { Text("Substituir por") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (findText.isNotEmpty()) {
                        onReplace(findText, replaceText)
                        onDismiss()
                    }
                },
                enabled = findText.isNotEmpty()
            ) {
                Text("Substituir Tudo")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

/**
 * Dialog to convert a Checklist item into a Task with date.
 */
@Composable
fun ConvertChecklistToTaskDialog(
    initialTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (title: String, dueDate: String) -> Unit
) {
    var taskTitle by remember { mutableStateOf(initialTitle) }
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
    var dueDate by remember { mutableStateOf(today) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.TaskAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Converter em Tarefa", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Transforma este item da nota em uma Tarefa com prazo na sua lista principal.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = taskTitle,
                    onValueChange = { taskTitle = it },
                    label = { Text("Título da Tarefa") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = dueDate,
                    onValueChange = { dueDate = it },
                    label = { Text("Data de Prazo (AAAA-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (taskTitle.isNotBlank()) {
                        onConfirm(taskTitle.trim(), dueDate.trim())
                    }
                },
                enabled = taskTitle.isNotBlank()
            ) {
                Text("Criar Tarefa")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

/**
 * Dialog to link note to Task, Habit, or Focus Session.
 */
@Composable
fun LinkNoteToEntityDialog(
    tasks: List<Task>,
    habits: List<Habit>,
    onDismiss: () -> Unit,
    onSelectEntity: (description: String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Vincular Nota", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Escolha a que vincular esta anotação:", fontSize = 12.sp)

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelectEntity("Sessão de Foco Pomodoro")
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🍊", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Vincular à Sessão de Foco Atual", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                if (tasks.isNotEmpty()) {
                    Text("Vincular a uma Tarefa:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    LazyColumn(modifier = Modifier.heightIn(max = 140.dp)) {
                        items(tasks.take(5)) { task ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clickable { onSelectEntity("Tarefa: ${task.title}") }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.TaskAlt, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(task.title, fontSize = 12.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }

                if (habits.isNotEmpty()) {
                    Text("Vincular a um Hábito:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    LazyColumn(modifier = Modifier.heightIn(max = 140.dp)) {
                        items(habits.take(5)) { habit ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clickable { onSelectEntity("Hábito: ${habit.title}") }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Repeat, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(habit.title, fontSize = 12.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar")
            }
        }
    )
}

/**
 * Dialog to configure and automatically generate a Markdown table (rows x columns).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTableDialog(
    onDismiss: () -> Unit,
    onConfirm: (rows: Int, cols: Int) -> Unit
) {
    var cols by remember { mutableIntStateOf(3) }
    var rows by remember { mutableIntStateOf(3) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.TableChart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Criar Tabela (Estilo Excel)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "Escolha a quantidade de colunas e linhas para gerar a tabela automaticamente:",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Columns Picker
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Colunas:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { if (cols > 1) cols-- },
                                    enabled = cols > 1,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Diminuir colunas", modifier = Modifier.size(16.dp))
                                }
                                Text(
                                    "$cols",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                                IconButton(
                                    onClick = { if (cols < 12) cols++ },
                                    enabled = cols < 12,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Aumentar colunas", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            listOf(2, 3, 4, 5).forEach { c ->
                                FilterChip(
                                    selected = cols == c,
                                    onClick = { cols = c },
                                    label = { Text("$c", fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                }

                // Rows Picker
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Linhas de dados:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { if (rows > 1) rows-- },
                                    enabled = rows > 1,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Diminuir linhas", modifier = Modifier.size(16.dp))
                                }
                                Text(
                                    "$rows",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                                IconButton(
                                    onClick = { if (rows < 25) rows++ },
                                    enabled = rows < 25,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Aumentar linhas", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            listOf(2, 3, 5, 10).forEach { r ->
                                FilterChip(
                                    selected = rows == r,
                                    onClick = { rows = r },
                                    label = { Text("$r", fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                }

                // Dimensions preview badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Grade gerada: $cols colunas × $rows linhas (rolagem lateral Excel)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(rows, cols) }) {
                Text("Criar Tabela")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

