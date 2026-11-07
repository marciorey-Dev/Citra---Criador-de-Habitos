package com.example.ui.screens.finances

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.backup.FinanceNoteMarkdownEngine
import com.example.data.model.*
import com.example.ui.theme.GreenAccent
import com.example.ui.theme.RedAction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FinanceNoteLinkCreatorDialog(
    notes: List<Note>,
    categories: List<FinanceCategory>,
    entries: List<FinanceEntry>,
    currentBalanceMinor: Long,
    defaultCurrency: FinanceCurrency,
    preselectedNote: Note? = null,
    onDismiss: () -> Unit,
    onConfirmCreateLink: (
        link: FinanceNoteLink,
        createNewNote: Boolean,
        newNoteTitle: String,
        selectedTemplate: FinanceNoteTemplate?
    ) -> Unit
) {
    val activeNotes = remember(notes) { notes.filter { !it.isDeleted } }

    var createNewNote by remember { mutableStateOf(preselectedNote == null) }
    var selectedTemplate by remember { mutableStateOf<FinanceNoteTemplate?>(FinanceNoteTemplate.MONTHLY_EXPENSES) }
    var newNoteTitle by remember { mutableStateOf("Despesas Mensais") }
    var selectedExistingNote by remember {
        mutableStateOf(preselectedNote ?: activeNotes.firstOrNull())
    }

    var sourceType by remember { mutableStateOf(FinanceLinkSource.EXPENSE) }
    var frequency by remember { mutableStateOf(FinanceLinkFrequency.MONTHLY) }
    var writeMode by remember { mutableStateOf(FinanceLinkWriteMode.REPLACE_BLOCK) }
    var newPeriodNoteTemplate by remember { mutableStateOf("Despesas Mensais · {mês} {ano}") }
    var groupBy by remember { mutableStateOf(FinanceLinkGroupBy.PRIORITY) }
    var mergeSameItems by remember { mutableStateOf(false) }

    var colCategory by remember { mutableStateOf(false) }
    var colDate by remember { mutableStateOf(false) }
    var colComment by remember { mutableStateOf(false) }

    var includeBalanceSummary by remember { mutableStateOf(true) }
    var includeAutoSummary by remember { mutableStateOf(false) }
    var includeMermaidChart by remember { mutableStateOf(false) }
    var notifyOnUpdate by remember { mutableStateOf(false) }

    var showFilters by remember { mutableStateOf(false) }
    var selectedCategoryIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var filterPriority by remember { mutableStateOf("") }
    var filterWallet by remember { mutableStateOf("") }
    var filterTags by remember { mutableStateOf("") }

    var showPreview by remember { mutableStateOf(false) }

    fun applyTemplate(tpl: FinanceNoteTemplate) {
        selectedTemplate = tpl
        newNoteTitle = tpl.title
        sourceType = tpl.defaultSource
        frequency = tpl.defaultFrequency
        groupBy = tpl.defaultGroupBy
        includeAutoSummary = tpl.defaultAutoSummary
    }

    val extraColsCsv = remember(colCategory, colDate, colComment) {
        buildList {
            if (colCategory) add("CATEGORY")
            if (colDate) add("DATE")
            if (colComment) add("COMMENT")
        }.joinToString(",")
    }

    val draftLink = remember(
        selectedExistingNote,
        sourceType,
        frequency,
        writeMode,
        newPeriodNoteTemplate,
        groupBy,
        mergeSameItems,
        extraColsCsv,
        includeBalanceSummary,
        includeAutoSummary,
        includeMermaidChart,
        selectedCategoryIds,
        filterPriority,
        filterWallet,
        filterTags,
        notifyOnUpdate
    ) {
        val blockId = "fin_${UUID.randomUUID().toString().take(6)}"
        FinanceNoteLink(
            noteId = selectedExistingNote?.id ?: 0L,
            noteUuid = selectedExistingNote?.uuid ?: "",
            blockId = blockId,
            sourceType = sourceType.id,
            frequency = frequency.id,
            writeMode = writeMode.id,
            newNoteTitleTemplate = newPeriodNoteTemplate,
            groupBy = groupBy.id,
            mergeSameItems = mergeSameItems,
            extraColumnsCsv = extraColsCsv,
            includeBalanceSummary = includeBalanceSummary,
            includeAutoSummary = includeAutoSummary,
            includeMermaidChart = includeMermaidChart,
            filterCategoriesCsv = selectedCategoryIds.joinToString(","),
            filterPriority = filterPriority,
            filterWallet = filterWallet.trim(),
            filterTagsCsv = filterTags.trim(),
            notifyOnUpdate = notifyOnUpdate
        )
    }

    val previewMarkdown = remember(draftLink, entries, categories, currentBalanceMinor, defaultCurrency) {
        FinanceNoteMarkdownEngine.generateBlockInnerMarkdown(
            link = draftLink,
            allEntries = entries,
            categories = categories,
            currentBalanceMinor = currentBalanceMinor,
            defaultCurrency = defaultCurrency
        ).first
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Link,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Vincular Finanças a Nota",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                HorizontalDivider()

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Destination selector (unless preselected from inside Note Editor)
                    if (preselectedNote == null) {
                        Text("Destino", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = createNewNote,
                                onClick = { createNewNote = true },
                                label = { Text("Nova nota") },
                                leadingIcon = { Icon(Icons.Default.NoteAdd, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = !createNewNote,
                                onClick = { createNewNote = false },
                                label = { Text("Nota existente") },
                                leadingIcon = { Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(preselectedNote.iconEmoji, fontSize = 18.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Inserir bloco na nota:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(preselectedNote.title.ifBlank { "Nota sem título" }, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }
                    }

                    if (createNewNote && preselectedNote == null) {
                        Text("Modelos prontos", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            FinanceNoteTemplate.entries.forEach { tpl ->
                                val isSel = selectedTemplate == tpl
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSel) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                    },
                                    border = if (isSel) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { applyTemplate(tpl) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(tpl.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Text(
                                                tpl.description,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (isSel) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = newNoteTitle,
                            onValueChange = { newNoteTitle = it },
                            label = { Text("Título da nova nota") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    } else if (!createNewNote && preselectedNote == null) {
                        Text("Selecionar nota existente", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        if (activeNotes.isEmpty()) {
                            Text(
                                "Nenhuma nota existente encontrada. Escolha 'Nova nota'.",
                                fontSize = 12.5.sp,
                                color = RedAction
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                activeNotes.take(12).forEach { note ->
                                    val isSel = selectedExistingNote?.uuid == note.uuid
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSel) {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                        },
                                        border = if (isSel) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { selectedExistingNote = note }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(note.iconEmoji, fontSize = 16.sp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = note.title.ifBlank { "Nota sem título" },
                                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 13.5.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (isSel) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    // Source Type
                    Text("O que extrair", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FinanceLinkSource.entries.forEach { src ->
                            FilterChip(
                                selected = sourceType == src,
                                onClick = { sourceType = src },
                                label = { Text(src.label, fontSize = 12.5.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Frequency
                    Text("Frequência de atualização", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FinanceLinkFrequency.entries.forEach { freq ->
                            FilterChip(
                                selected = frequency == freq,
                                onClick = { frequency = freq },
                                label = { Text(freq.label, fontSize = 12.5.sp) }
                            )
                        }
                    }

                    // Write Mode
                    Text("Como escreve na nota", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FinanceLinkWriteMode.entries.forEach { mode ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { writeMode = mode }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = writeMode == mode,
                                    onClick = { writeMode = mode }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(mode.label, fontSize = 13.5.sp)
                            }
                        }
                    }

                    if (writeMode == FinanceLinkWriteMode.NEW_NOTE_PER_PERIOD) {
                        OutlinedTextField(
                            value = newPeriodNoteTemplate,
                            onValueChange = { newPeriodNoteTemplate = it },
                            label = { Text("Modelo de título ({mês}, {ano}, {dia})") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    // Group By
                    Text("Agrupar por", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FinanceLinkGroupBy.entries.forEach { gb ->
                            FilterChip(
                                selected = groupBy == gb,
                                onClick = { groupBy = gb },
                                label = { Text(gb.label, fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Extra columns
                    Text("Colunas extra", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = colCategory,
                            onClick = { colCategory = !colCategory },
                            label = { Text("Categoria") }
                        )
                        FilterChip(
                            selected = colDate,
                            onClick = { colDate = !colDate },
                            label = { Text("Data") }
                        )
                        FilterChip(
                            selected = colComment,
                            onClick = { colComment = !colComment },
                            label = { Text("Comentário") }
                        )
                    }

                    // Toggles
                    LinkSwitchRow("Juntar itens iguais", mergeSameItems) { mergeSameItems = it }
                    LinkSwitchRow("Resumo de saldo e simulação", includeBalanceSummary) { includeBalanceSummary = it }
                    LinkSwitchRow("Resumo automático", includeAutoSummary) { includeAutoSummary = it }
                    LinkSwitchRow("Notificar ao atualizar", notifyOnUpdate) { notifyOnUpdate = it }

                    // Optional Filters
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showFilters = !showFilters },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Filtros opcionais", fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                                Icon(
                                    if (showFilters) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null
                                )
                            }
                            AnimatedVisibility(visible = showFilters) {
                                Column(
                                    modifier = Modifier.padding(top = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text("Categorias:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        categories.forEach { cat ->
                                            val sel = selectedCategoryIds.contains(cat.id)
                                            FilterChip(
                                                selected = sel,
                                                onClick = {
                                                    selectedCategoryIds = if (sel) {
                                                        selectedCategoryIds - cat.id
                                                    } else {
                                                        selectedCategoryIds + cat.id
                                                    }
                                                },
                                                label = { Text(cat.name, fontSize = 11.5.sp) }
                                            )
                                        }
                                    }

                                    Text("Prioridade:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf("" to "Todas", "MANDATORY" to "Obrigatória", "OPTIONAL" to "Opcional").forEach { (id, lbl) ->
                                            FilterChip(
                                                selected = filterPriority == id,
                                                onClick = { filterPriority = id },
                                                label = { Text(lbl, fontSize = 12.sp) }
                                            )
                                        }
                                    }

                                    OutlinedTextField(
                                        value = filterWallet,
                                        onValueChange = { filterWallet = it },
                                        label = { Text("Filtrar por carteira (opcional)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    )

                                    OutlinedTextField(
                                        value = filterTags,
                                        onValueChange = { filterTags = it },
                                        label = { Text("Etiquetas separadas por vírgula") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Preview Toggle & Box
                    OutlinedButton(
                        onClick = { showPreview = !showPreview },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            if (showPreview) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (showPreview) "Ocultar pré-visualização" else "Pré-visualizar bloco Markdown")
                    }

                    AnimatedVisibility(visible = showPreview) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = previewMarkdown,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                HorizontalDivider()

                // Bottom Confirm Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Cancelar")
                    }
                    Button(
                        onClick = {
                            val titleToUse = newNoteTitle.trim().ifBlank { "Finanças" }
                            onConfirmCreateLink(
                                draftLink,
                                createNewNote && preselectedNote == null,
                                titleToUse,
                                selectedTemplate
                            )
                        },
                        enabled = (createNewNote && preselectedNote == null) || selectedExistingNote != null,
                        modifier = Modifier
                            .weight(1.4f)
                            .testTag("confirm_create_finance_link_btn"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Vincular e atualizar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun FinanceNoteLinksManagerDialog(
    links: List<FinanceNoteLink>,
    notes: List<Note>,
    onDismiss: () -> Unit,
    onCreateNewLink: () -> Unit,
    onRunNow: (FinanceNoteLink) -> Unit,
    onUndoLast: (FinanceNoteLink) -> Unit,
    onTogglePause: (FinanceNoteLink) -> Unit,
    onRecreateMarkers: (FinanceNoteLink) -> Unit,
    onRecreateNote: (FinanceNoteLink) -> Unit,
    onDeleteLink: (FinanceNoteLink) -> Unit
) {
    val noteMap = remember(notes) { notes.associateBy { it.uuid } }
    val dateFmt = remember { SimpleDateFormat("dd/MM HH:mm", Locale("pt", "AO")) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SyncAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Vínculos com Notas", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Row {
                        IconButton(onClick = onCreateNewLink) {
                            Icon(Icons.Default.Add, contentDescription = "Novo vínculo", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Fechar")
                        }
                    }
                }

                HorizontalDivider()

                if (links.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.NoteAdd,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Nenhum vínculo criado ainda",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Ligue suas despesas ou rendas a uma nota Markdown para gerar tabelas automáticas.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = onCreateNewLink, shape = RoundedCornerShape(14.dp)) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Criar primeiro vínculo")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(links, key = { it.id }) { link ->
                            val targetNote = noteMap[link.noteUuid]
                            val isNoteMissing = targetNote == null || targetNote.isDeleted
                            val areMarkersMissing = !isNoteMissing &&
                                FinanceLinkWriteMode.fromId(link.writeMode) == FinanceLinkWriteMode.REPLACE_BLOCK &&
                                !FinanceNoteMarkdownEngine.hasManagedBlock(targetNote!!.content, link.blockId)
                            val isPaused = link.status != "ACTIVE"

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = targetNote?.title ?: link.newNoteTitleTemplate,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${FinanceLinkSource.fromId(link.sourceType).label} · ${FinanceLinkFrequency.fromId(link.frequency).label}",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isPaused) RedAction.copy(alpha = 0.15f) else GreenAccent.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = if (isPaused) "Pausado" else "Ativo",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isPaused) RedAction else GreenAccent,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    val lastRunText = if (link.lastExecutedAt != null) {
                                        "Última extração: ${dateFmt.format(Date(link.lastExecutedAt))}"
                                    } else {
                                        "Ainda não executado"
                                    }
                                    Text(
                                        text = lastRunText,
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    if (isNoteMissing) {
                                        OutlinedButton(
                                            onClick = { onRecreateNote(link) },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.NoteAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Nota apagada · Criar nova nota", fontSize = 12.sp)
                                        }
                                    } else if (areMarkersMissing) {
                                        OutlinedButton(
                                            onClick = { onRecreateMarkers(link) },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Marcadores apagados · Recriar no fim da nota", fontSize = 12.sp)
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FilledTonalButton(
                                            onClick = { onRunNow(link) },
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Atualizar agora", fontSize = 12.sp)
                                        }
                                        OutlinedButton(
                                            onClick = { onUndoLast(link) },
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Desfazer", fontSize = 12.sp)
                                        }
                                        IconButton(onClick = { onTogglePause(link) }) {
                                            Icon(
                                                if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                                contentDescription = if (isPaused) "Retomar" else "Pausar"
                                            )
                                        }
                                        IconButton(onClick = { onDeleteLink(link) }) {
                                            Icon(
                                                Icons.Default.DeleteOutline,
                                                contentDescription = "Apagar vínculo",
                                                tint = RedAction
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
