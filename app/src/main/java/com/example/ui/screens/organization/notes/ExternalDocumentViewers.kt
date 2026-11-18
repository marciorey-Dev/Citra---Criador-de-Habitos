package com.example.ui.screens.organization.notes

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import android.widget.Toast
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.Category
import com.example.data.model.Note
import com.example.data.model.NoteDocumentType
import com.example.ui.theme.isAppDarkTheme
import com.example.util.DocxBlock
import com.example.util.DocxDocumentEngine
import com.example.util.DocxParsedDocument
import com.example.util.DocxRun
import com.example.util.ExternalDocumentUriHelper
import com.example.util.LatexFormulaRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Composable
fun NoteCategoryPickerDialog(
    currentCategory: String,
    categories: List<Category>,
    onSelectCategory: (String) -> Unit,
    onCreateCategory: ((String, String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    var newCategoryName by remember { mutableStateOf("") }
    var showCreateField by remember { mutableStateOf(false) }

    val filteredCategories = remember(categories, search) {
        val base = if (search.isBlank()) categories else {
            categories.filter { it.name.contains(search.trim(), ignoreCase = true) }
        }
        base
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .testTag("note_category_picker_dialog")
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
                            imageVector = Icons.Default.Label,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Organizar Categoria",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                if (categories.size > 5) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        placeholder = { Text("Filtrar categorias...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(filteredCategories, key = { _, cat -> cat.name }) { _, cat ->
                        val isSelected = cat.name.equals(currentCategory, ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    onSelectCategory(cat.name)
                                    onDismiss()
                                }
                                .testTag("category_picker_item_${cat.name}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(text = cat.emoji.ifBlank { "📁" }, fontSize = 16.sp)
                                    Text(
                                        text = cat.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selecionada",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (onCreateCategory != null) {
                    if (!showCreateField) {
                        OutlinedButton(
                            onClick = { showCreateField = true },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Nova Categoria")
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = newCategoryName,
                                onValueChange = { newCategoryName = it },
                                placeholder = { Text("Nome da pasta") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                enabled = newCategoryName.trim().isNotEmpty(),
                                onClick = {
                                    val clean = newCategoryName.trim()
                                    if (clean.isNotEmpty()) {
                                        onCreateCategory(clean, "📁")
                                        onSelectCategory(clean)
                                        onDismiss()
                                    }
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Criar")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RenameExternalDocumentDialog(
    currentTitle: String,
    docTypeLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var nameInput by remember(currentTitle) { mutableStateOf(currentTitle) }
    val cleanName = nameInput.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DriveFileRenameOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("Renomear $docTypeLabel", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "O novo nome será aplicado no Citra e, quando o armazenamento permitir, também no arquivo do aparelho.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Nome do arquivo") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rename_external_doc_input")
                )
            }
        },
        confirmButton = {
            Button(
                enabled = cleanName.isNotEmpty(),
                onClick = { onConfirm(cleanName) },
                modifier = Modifier.testTag("rename_external_doc_confirm")
            ) {
                Text("Renomear")
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
 * Shared compact document top bar for PDF and .docx viewers with 3-dots actions menu
 * and optional Edit/Read-Only eye toggle button (for .docx).
 */
@Composable
private fun ExternalDocumentReaderHeader(
    title: String,
    badgeLabel: String,
    badgeColor: Color,
    category: String,
    isConverting: Boolean,
    showEditToggle: Boolean = false,
    isEditingDocx: Boolean = false,
    onToggleEditMode: () -> Unit = {},
    onClose: () -> Unit,
    onCategoryClick: () -> Unit,
    onConvertToNote: () -> Unit,
    onRenameClick: () -> Unit,
    onShareClick: () -> Unit,
    onPrintClick: () -> Unit,
    onRelinkClick: () -> Unit,
    closeTestTag: String,
    moreMenuTestTag: String,
    editToggleTestTag: String = "docx_toggle_edit_btn",
    categoryTestTag: String,
    badgeTestTag: String,
    secondaryControls: @Composable RowScope.() -> Unit
) {
    var showMoreMenu by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = onClose,
                    enabled = !isConverting,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag(closeTestTag)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Voltar"
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = title.ifBlank { "Documento" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = badgeColor.copy(alpha = 0.14f),
                            modifier = Modifier.testTag(badgeTestTag)
                        ) {
                            Text(
                                text = badgeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = badgeColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }

                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(onClick = onCategoryClick)
                                .testTag(categoryTestTag)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Label,
                                    contentDescription = "Alterar categoria",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = category.ifBlank { "Geral" },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                if (showEditToggle) {
                    FilledTonalIconButton(
                        onClick = onToggleEditMode,
                        enabled = !isConverting,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (isEditingDocx) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
                            },
                            contentColor = if (isEditingDocx) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        ),
                        modifier = Modifier
                            .size(44.dp)
                            .testTag(editToggleTestTag)
                    ) {
                        Icon(
                            imageVector = if (isEditingDocx) Icons.Default.Visibility else Icons.Default.Edit,
                            contentDescription = if (isEditingDocx) "Modo leitura" else "Editar texto",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Box {
                    IconButton(
                        onClick = { showMoreMenu = true },
                        enabled = !isConverting,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag(moreMenuTestTag)
                    ) {
                        if (isConverting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Mais opções"
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false },
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        DropdownMenuItem(
                            text = { Text("Converter para nota") },
                            leadingIcon = { Icon(Icons.Default.EditNote, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                onConvertToNote()
                            }
                        )
                        if (showEditToggle) {
                            DropdownMenuItem(
                                text = {
                                    Text(if (isEditingDocx) "Modo leitura (só leitura)" else "Editar texto (.docx)")
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isEditingDocx) Icons.Default.Visibility else Icons.Default.Edit,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    onToggleEditMode()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Renomear arquivo") },
                            leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                onRenameClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Enviar para...") },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                onShareClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Imprimir") },
                            leadingIcon = { Icon(Icons.Default.Print, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                onPrintClick()
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                        DropdownMenuItem(
                            text = { Text("Carregar outro arquivo") },
                            leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                onRelinkClick()
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                secondaryControls()
            }
        }
    }
}

/**
 * In-app read-only PDF viewer backed by Android PdfRenderer (no internal file duplication).
 */
@Composable
fun ExternalPdfReaderScreen(
    note: Note,
    categories: List<Category>,
    onClose: () -> Unit,
    onChangeCategory: (String) -> Unit,
    onCreateCategory: (String, String) -> Unit,
    onRenameDocument: (String) -> Unit,
    onRelinkClick: () -> Unit,
    onRemoveLink: () -> Unit,
    onConvertToNote: () -> Unit,
    isConverting: Boolean = false
) {
    BackHandler(enabled = !isConverting) { onClose() }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showChrome by remember { mutableStateOf(true) }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    val animatedScale by animateFloatAsState(
        targetValue = zoomScale,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pdf_zoom_scale"
    )
    val animatedPan by animateOffsetAsState(
        targetValue = panOffset,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pdf_zoom_pan"
    )

    var loadError by remember { mutableStateOf<String?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }

    var activePfd by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var activeRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    val renderMutex = remember { Mutex() }

    val bitmapCache = remember(note.externalUri) {
        val maxKb = (Runtime.getRuntime().maxMemory() / 1024L).toInt()
        val cacheSizeKb = (maxKb / 10).coerceIn(8 * 1024, 48 * 1024)
        object : LruCache<String, Bitmap>(cacheSizeKb) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
        }
    }

    DisposableEffect(note.externalUri) {
        val pfd = ExternalDocumentUriHelper.openParcelFileDescriptor(context, note.externalUri)
        if (pfd == null) {
            loadError = "Arquivo PDF inacessível neste caminho. Se o arquivo foi movido ou a permissão expirou, toque em Carregar outro PDF."
            activePfd = null
            activeRenderer = null
            pageCount = 0
        } else {
            try {
                val renderer = PdfRenderer(pfd)
                if (renderer.pageCount <= 0) {
                    loadError = "Este PDF não possui páginas legíveis."
                    renderer.close()
                    pfd.close()
                    activePfd = null
                    activeRenderer = null
                    pageCount = 0
                } else {
                    loadError = null
                    activePfd = pfd
                    activeRenderer = renderer
                    pageCount = renderer.pageCount
                }
            } catch (e: SecurityException) {
                loadError = "Este PDF está protegido por senha e requer desbloqueio antes da visualização."
                try { pfd.close() } catch (_: Exception) {}
                activePfd = null
                activeRenderer = null
                pageCount = 0
            } catch (e: Exception) {
                loadError = "Não foi possível abrir o PDF: ${e.message ?: "formato inválido"}."
                try { pfd.close() } catch (_: Exception) {}
                activePfd = null
                activeRenderer = null
                pageCount = 0
            }
        }

        onDispose {
            bitmapCache.evictAll()
            try { activeRenderer?.close() } catch (_: Exception) {}
            try { activePfd?.close() } catch (_: Exception) {}
            activeRenderer = null
            activePfd = null
        }
    }

    val listState = rememberLazyListState()
    val currentPageIndex by remember {
        derivedStateOf { (listState.firstVisibleItemIndex + 1).coerceAtLeast(1) }
    }

    fun applyDoubleTapZoomAt(tapOffset: Offset) {
        val current = zoomScale
        val nextScale = when {
            current < 1.45f -> 1.75f
            current < 2.35f -> 2.55f
            current < 3.25f -> 3.5f
            else -> 1f
        }
        if (nextScale <= 1.01f || viewportSize.width <= 0 || viewportSize.height <= 0) {
            zoomScale = 1f
            panOffset = Offset.Zero
        } else {
            val center = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
            val factor = nextScale / current
            val rawPan = tapOffset - center - (tapOffset - center - panOffset) * factor
            val maxX = (viewportSize.width * (nextScale - 1f)) / 2f
            val maxY = (viewportSize.height * (nextScale - 1f)) / 2f
            zoomScale = nextScale
            panOffset = Offset(
                x = rawPan.x.coerceIn(-maxX, maxX),
                y = rawPan.y.coerceIn(-maxY, maxY)
            )
        }
    }

    val topSpacer by animateDpAsState(targetValue = if (showChrome) 118.dp else 16.dp, label = "pdf_top_pad")
    val bottomSpacer = 280.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clipToBounds()
    ) {
        // Main PDF Reader Surface (supports single-tap chrome toggle + area double-tap zoom + 2-finger pinch + 2D pan)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewportSize = it }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { showChrome = !showChrome },
                        onDoubleTap = { tapOffset -> applyDoubleTapZoomAt(tapOffset) }
                    )
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.count { it.pressed } >= 2) {
                                val zoomFactor = event.calculateZoom()
                                val panDelta = event.calculatePan()
                                val updated = (zoomScale * zoomFactor).coerceIn(0.85f, 3.5f)
                                if (kotlin.math.abs(zoomFactor - 1f) > 0.005f || panDelta != Offset.Zero) {
                                    zoomScale = updated
                                    if (updated <= 1.02f) {
                                        panOffset = Offset.Zero
                                    } else if (viewportSize.width > 0 && viewportSize.height > 0) {
                                        val maxX = (viewportSize.width * (updated - 1f)) / 2f
                                        val maxY = (viewportSize.height * (updated - 1f)) / 2f
                                        panOffset = Offset(
                                            x = (panOffset.x + panDelta.x).coerceIn(-maxX, maxX),
                                            y = (panOffset.y + panDelta.y).coerceIn(-maxY, maxY)
                                        )
                                    }
                                }
                                event.changes.forEach { it.consume() }
                            }
                            if (event.changes.none { it.pressed }) break
                        }
                    }
                }
                .pointerInput(zoomScale > 1.02f, viewportSize) {
                    if (zoomScale > 1.02f) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val maxX = (viewportSize.width * (zoomScale - 1f)) / 2f
                            val maxY = (viewportSize.height * (zoomScale - 1f)) / 2f
                            val nextX = (panOffset.x + dragAmount.x).coerceIn(-maxX, maxX)
                            val desiredY = panOffset.y + dragAmount.y
                            val nextY = desiredY.coerceIn(-maxY, maxY)
                            panOffset = Offset(x = nextX, y = nextY)
                            val overflowY = desiredY - nextY
                            if (kotlin.math.abs(overflowY) > 0.5f) {
                                coroutineScope.launch {
                                    listState.scrollBy(-overflowY)
                                }
                            }
                        }
                    }
                }
        ) {
            when {
                loadError != null -> {
                    ExternalDocumentMissingState(
                        message = loadError!!,
                        relinkLabel = "Carregar outro PDF",
                        onRelink = onRelinkClick,
                        onRemoveLink = onRemoveLink,
                        onClose = onClose,
                        relinkTestTag = "pdf_reader_relink_btn"
                    )
                }
                activeRenderer != null && pageCount > 0 -> {
                    val renderer = activeRenderer!!
                    val density = LocalDensity.current
                    val zoomPercent = (zoomScale * 100).toInt()
                    val renderBucket = when {
                        zoomPercent <= 115 -> 100
                        zoomPercent <= 185 -> 150
                        zoomPercent <= 275 -> 225
                        else -> 300
                    }

                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val baseWidthPx = with(density) { (maxWidth - 24.dp).toPx().toInt().coerceAtLeast(480) }
                        val targetWidthPx = (baseWidthPx * (renderBucket / 100f)).toInt().coerceAtMost(2600)
                        val displayWidthDp = (maxWidth - 24.dp)

                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = animatedScale,
                                    scaleY = animatedScale,
                                    translationX = animatedPan.x,
                                    translationY = animatedPan.y
                                ),
                            contentPadding = PaddingValues(
                                start = 12.dp,
                                end = 12.dp,
                                top = topSpacer,
                                bottom = bottomSpacer
                            ),
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            items(count = pageCount, key = { it }) { pageIdx ->
                                PdfPageCard(
                                    renderer = renderer,
                                    renderMutex = renderMutex,
                                    bitmapCache = bitmapCache,
                                    pageIndex = pageIdx,
                                    pageCount = pageCount,
                                    targetWidthPx = targetWidthPx,
                                    renderBucket = renderBucket,
                                    displayWidthDp = displayWidthDp
                                )
                            }
                        }
                    }
                }
                else -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }

        // Top Bar Overlay (Single-tap hides/restores)
        AnimatedVisibility(
            visible = showChrome || loadError != null,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            ExternalDocumentReaderHeader(
                title = note.title,
                badgeLabel = "PDF · Somente leitura",
                badgeColor = Color(0xFFDC2626),
                category = note.category,
                isConverting = isConverting,
                showEditToggle = false,
                onClose = onClose,
                onCategoryClick = { showCategoryPicker = true },
                onConvertToNote = onConvertToNote,
                onRenameClick = { showRenameDialog = true },
                onShareClick = {
                    val ok = ExternalDocumentUriHelper.shareExternalDocument(
                        context = context,
                        title = note.title,
                        pathOrUri = note.externalUri,
                        docType = NoteDocumentType.PDF
                    )
                    if (!ok) {
                        Toast.makeText(context, "Não foi possível compartilhar este PDF.", Toast.LENGTH_SHORT).show()
                    }
                },
                onPrintClick = {
                    val ok = ExternalDocumentUriHelper.printExternalDocument(
                        context = context,
                        title = note.title,
                        pathOrUri = note.externalUri,
                        docType = NoteDocumentType.PDF
                    )
                    if (!ok) {
                        Toast.makeText(context, "Impressão indisponível para este PDF.", Toast.LENGTH_SHORT).show()
                    }
                },
                onRelinkClick = onRelinkClick,
                closeTestTag = "pdf_reader_close_btn",
                moreMenuTestTag = "pdf_reader_more_menu_btn",
                categoryTestTag = "pdf_reader_category_chip",
                badgeTestTag = "pdf_reader_badge"
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                ) {
                    Text(
                        text = if (pageCount > 0) "Pág. $currentPageIndex de $pageCount" else "PDF",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = {
                            val next = (zoomScale - 0.25f).coerceAtLeast(0.75f)
                            zoomScale = next
                            if (next <= 1.02f) panOffset = Offset.Zero
                        },
                        enabled = zoomScale > 0.76f,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.ZoomOut, contentDescription = "Diminuir zoom", modifier = Modifier.size(18.dp))
                    }
                    Text(
                        text = "${(zoomScale * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = { zoomScale = (zoomScale + 0.25f).coerceAtMost(3.5f) },
                        enabled = zoomScale < 3.49f,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.ZoomIn, contentDescription = "Aumentar zoom", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }

    if (showCategoryPicker) {
        NoteCategoryPickerDialog(
            currentCategory = note.category,
            categories = categories,
            onSelectCategory = onChangeCategory,
            onCreateCategory = onCreateCategory,
            onDismiss = { showCategoryPicker = false }
        )
    }

    if (showRenameDialog) {
        RenameExternalDocumentDialog(
            currentTitle = note.title,
            docTypeLabel = "PDF",
            onDismiss = { showRenameDialog = false },
            onConfirm = { newTitle ->
                showRenameDialog = false
                onRenameDocument(newTitle)
            }
        )
    }
}

@Composable
private fun PdfPageCard(
    renderer: PdfRenderer,
    renderMutex: Mutex,
    bitmapCache: LruCache<String, Bitmap>,
    pageIndex: Int,
    pageCount: Int,
    targetWidthPx: Int,
    renderBucket: Int,
    displayWidthDp: androidx.compose.ui.unit.Dp
) {
    val cacheKey = "${pageIndex}_$renderBucket"
    var renderedBitmap by remember(cacheKey) { mutableStateOf(bitmapCache.get(cacheKey)) }

    LaunchedEffect(renderer, cacheKey, targetWidthPx) {
        val cached = bitmapCache.get(cacheKey)
        if (cached != null && !cached.isRecycled) {
            renderedBitmap = cached
            return@LaunchedEffect
        }
        val bmp = withContext(Dispatchers.IO) {
            renderMutex.withLock {
                bitmapCache.get(cacheKey)?.takeIf { !it.isRecycled } ?: try {
                    renderer.openPage(pageIndex).use { page ->
                        val ratio = page.height.toFloat() / page.width.coerceAtLeast(1).toFloat()
                        val w = targetWidthPx.coerceAtLeast(360)
                        val h = (w * ratio).toInt().coerceIn(360, 3200)
                        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        out.eraseColor(AndroidColor.WHITE)
                        page.render(out, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmapCache.put(cacheKey, out)
                        out
                    }
                } catch (_: Throwable) {
                    null
                }
            }
        }
        if (bmp != null) {
            renderedBitmap = bmp
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        shadowElevation = 4.dp,
        tonalElevation = 1.dp,
        modifier = Modifier
            .width(displayWidthDp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
    ) {
        Column {
            val bmp = renderedBitmap
            if (bmp != null && !bmp.isRecycled) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Página ${pageIndex + 1} de $pageCount",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = "Página ${pageIndex + 1} / $pageCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * In-app Word (.docx) reader and inline text-only editor backed by DocxDocumentEngine.
 * Allows reading, 350% zooming + free 2D panning, printing, sharing, renaming, and editing existing paragraph text.
 */
@Composable
fun ExternalDocxReaderScreen(
    note: Note,
    categories: List<Category>,
    onClose: () -> Unit,
    onChangeCategory: (String) -> Unit,
    onCreateCategory: (String, String) -> Unit,
    onRenameDocument: (String) -> Unit,
    onSaveDocxEdits: (Map<Int, String>, () -> Unit, (String) -> Unit) -> Unit,
    onRelinkClick: () -> Unit,
    onRemoveLink: () -> Unit,
    onConvertToNote: () -> Unit,
    isConverting: Boolean = false
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showChrome by remember { mutableStateOf(true) }
    var textScale by remember { mutableFloatStateOf(1.0f) }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    var isEditingDocx by remember { mutableStateOf(false) }
    var isSavingDocx by remember { mutableStateOf(false) }
    var reloadTrigger by remember { mutableIntStateOf(0) }
    val editedTexts = remember(note.externalUri, reloadTrigger) { mutableStateMapOf<Int, String>() }

    BackHandler(enabled = !isConverting && !isSavingDocx) {
        if (isEditingDocx) {
            editedTexts.clear()
            isEditingDocx = false
        } else {
            onClose()
        }
    }

    val animatedScale by animateFloatAsState(
        targetValue = zoomScale,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "docx_zoom_scale"
    )
    val animatedPan by animateOffsetAsState(
        targetValue = panOffset,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "docx_zoom_pan"
    )

    var parsedDoc by remember { mutableStateOf<DocxParsedDocument?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(note.externalUri, reloadTrigger) {
        isLoading = true
        loadError = null
        parsedDoc = null
        val result = withContext(Dispatchers.IO) {
            val stream = ExternalDocumentUriHelper.openInputStream(context, note.externalUri)
            if (stream == null) {
                null to "Arquivo Word (.docx) não encontrado ou permissão revogada. Toque em Carregar outro Word."
            } else {
                try {
                    stream.use { input ->
                        DocxDocumentEngine.parseDocx(input) to null
                    }
                } catch (e: Exception) {
                    null to "Não foi possível ler este Word (.docx): ${e.message ?: "arquivo corrompido ou incompatível"}."
                }
            }
        }
        parsedDoc = result.first
        loadError = result.second
        isLoading = false
    }

    fun applyDoubleTapZoomAt(tapOffset: Offset) {
        val current = zoomScale
        val nextScale = when {
            current < 1.45f -> 1.75f
            current < 2.35f -> 2.55f
            current < 3.25f -> 3.5f
            else -> 1f
        }
        if (nextScale <= 1.01f || viewportSize.width <= 0 || viewportSize.height <= 0) {
            zoomScale = 1f
            panOffset = Offset.Zero
        } else {
            val center = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
            val factor = nextScale / current
            val rawPan = tapOffset - center - (tapOffset - center - panOffset) * factor
            val maxX = (viewportSize.width * (nextScale - 1f)) / 2f
            val maxY = (viewportSize.height * (nextScale - 1f)) / 2f
            zoomScale = nextScale
            panOffset = Offset(
                x = rawPan.x.coerceIn(-maxX, maxX),
                y = rawPan.y.coerceIn(-maxY, maxY)
            )
        }
    }

    val topSpacer by animateDpAsState(targetValue = if (showChrome || isEditingDocx) 118.dp else 16.dp, label = "docx_top_pad")
    val bottomSpacer = 280.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clipToBounds()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewportSize = it }
                .pointerInput(isEditingDocx) {
                    if (!isEditingDocx) {
                        detectTapGestures(
                            onTap = { showChrome = !showChrome },
                            onDoubleTap = { tapOffset -> applyDoubleTapZoomAt(tapOffset) }
                        )
                    }
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.count { it.pressed } >= 2) {
                                val zoomFactor = event.calculateZoom()
                                val panDelta = event.calculatePan()
                                val updated = (zoomScale * zoomFactor).coerceIn(0.85f, 3.5f)
                                if (kotlin.math.abs(zoomFactor - 1f) > 0.005f || panDelta != Offset.Zero) {
                                    zoomScale = updated
                                    if (updated <= 1.02f) {
                                        panOffset = Offset.Zero
                                    } else if (viewportSize.width > 0 && viewportSize.height > 0) {
                                        val maxX = (viewportSize.width * (updated - 1f)) / 2f
                                        val maxY = (viewportSize.height * (updated - 1f)) / 2f
                                        panOffset = Offset(
                                            x = (panOffset.x + panDelta.x).coerceIn(-maxX, maxX),
                                            y = (panOffset.y + panDelta.y).coerceIn(-maxY, maxY)
                                        )
                                    }
                                }
                                event.changes.forEach { it.consume() }
                            }
                            if (event.changes.none { it.pressed }) break
                        }
                    }
                }
                .pointerInput(zoomScale > 1.02f, viewportSize, isEditingDocx) {
                    if (zoomScale > 1.02f && !isEditingDocx) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val maxX = (viewportSize.width * (zoomScale - 1f)) / 2f
                            val maxY = (viewportSize.height * (zoomScale - 1f)) / 2f
                            val nextX = (panOffset.x + dragAmount.x).coerceIn(-maxX, maxX)
                            val desiredY = panOffset.y + dragAmount.y
                            val nextY = desiredY.coerceIn(-maxY, maxY)
                            panOffset = Offset(x = nextX, y = nextY)
                            val overflowY = desiredY - nextY
                            if (kotlin.math.abs(overflowY) > 0.5f) {
                                coroutineScope.launch {
                                    listState.scrollBy(-overflowY)
                                }
                            }
                        }
                    }
                }
        ) {
            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = "Carregando Word (.docx)...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                loadError != null -> {
                    ExternalDocumentMissingState(
                        message = loadError!!,
                        relinkLabel = "Carregar outro Word",
                        onRelink = onRelinkClick,
                        onRemoveLink = onRemoveLink,
                        onClose = onClose,
                        relinkTestTag = "docx_reader_relink_btn"
                    )
                }
                parsedDoc != null -> {
                    val blocks = parsedDoc!!.blocks
                    if (blocks.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Documento Word (.docx) sem conteúdo visível.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 760.dp)
                                .align(Alignment.TopCenter)
                                .graphicsLayer(
                                    scaleX = animatedScale,
                                    scaleY = animatedScale,
                                    translationX = animatedPan.x,
                                    translationY = animatedPan.y
                                ),
                            contentPadding = PaddingValues(
                                start = 20.dp,
                                end = 20.dp,
                                top = topSpacer,
                                bottom = bottomSpacer
                            ),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(blocks) { _, block ->
                                if (isEditingDocx) {
                                    DocxEditableBlockItem(
                                        block = block,
                                        fontScale = textScale,
                                        editedTexts = editedTexts
                                    )
                                } else {
                                    DocxReadOnlyBlockItem(block = block, fontScale = textScale)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Top Bar Overlay (Single-tap hides/restores; stays visible in edit mode so Eye icon is reachable)
        AnimatedVisibility(
            visible = showChrome || loadError != null || isEditingDocx,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            ExternalDocumentReaderHeader(
                title = note.title,
                badgeLabel = if (isEditingDocx) "Word (.docx) · Edição de texto" else "Word (.docx) · Somente leitura",
                badgeColor = if (isEditingDocx) MaterialTheme.colorScheme.primary else Color(0xFF2563EB),
                category = note.category,
                isConverting = isConverting || isSavingDocx,
                showEditToggle = true,
                isEditingDocx = isEditingDocx,
                onToggleEditMode = {
                    if (isEditingDocx) {
                        editedTexts.clear()
                        isEditingDocx = false
                    } else {
                        isEditingDocx = true
                        showChrome = true
                    }
                },
                onClose = {
                    if (isEditingDocx) {
                        editedTexts.clear()
                        isEditingDocx = false
                    } else {
                        onClose()
                    }
                },
                onCategoryClick = { showCategoryPicker = true },
                onConvertToNote = onConvertToNote,
                onRenameClick = { showRenameDialog = true },
                onShareClick = {
                    val ok = ExternalDocumentUriHelper.shareExternalDocument(
                        context = context,
                        title = note.title,
                        pathOrUri = note.externalUri,
                        docType = NoteDocumentType.DOCX
                    )
                    if (!ok) {
                        Toast.makeText(context, "Não foi possível compartilhar este arquivo .docx.", Toast.LENGTH_SHORT).show()
                    }
                },
                onPrintClick = {
                    val ok = ExternalDocumentUriHelper.printExternalDocument(
                        context = context,
                        title = note.title,
                        pathOrUri = note.externalUri,
                        docType = NoteDocumentType.DOCX,
                        docxParsed = parsedDoc
                    )
                    if (!ok) {
                        Toast.makeText(context, "Impressão indisponível para este arquivo .docx.", Toast.LENGTH_SHORT).show()
                    }
                },
                onRelinkClick = onRelinkClick,
                closeTestTag = "docx_reader_close_btn",
                moreMenuTestTag = "docx_reader_more_menu_btn",
                editToggleTestTag = "docx_toggle_edit_btn",
                categoryTestTag = "docx_reader_category_chip",
                badgeTestTag = "docx_reader_badge"
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = { textScale = (textScale - 0.1f).coerceAtLeast(0.8f) },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Text("A-", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Text(
                        text = "${(textScale * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = { textScale = (textScale + 0.1f).coerceAtMost(1.5f) },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Text("A+", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = {
                            val next = (zoomScale - 0.25f).coerceAtLeast(0.75f)
                            zoomScale = next
                            if (next <= 1.02f) panOffset = Offset.Zero
                        },
                        enabled = zoomScale > 0.76f,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.ZoomOut, contentDescription = "Diminuir zoom", modifier = Modifier.size(18.dp))
                    }
                    Text(
                        text = "${(zoomScale * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = { zoomScale = (zoomScale + 0.25f).coerceAtMost(3.5f) },
                        enabled = zoomScale < 3.49f,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.ZoomIn, contentDescription = "Aumentar zoom", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        // Bottom floating save bar when editing .docx text
        AnimatedVisibility(
            visible = isEditingDocx,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 8.dp,
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (editedTexts.isEmpty()) {
                            "Toque em qualquer parágrafo para editar ou apagar texto"
                        } else {
                            "${editedTexts.size} parágrafo(s) alterado(s)"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    TextButton(
                        enabled = !isSavingDocx,
                        onClick = {
                            editedTexts.clear()
                            isEditingDocx = false
                        }
                    ) {
                        Text("Cancelar")
                    }

                    Button(
                        enabled = editedTexts.isNotEmpty() && !isSavingDocx,
                        onClick = {
                            isSavingDocx = true
                            onSaveDocxEdits(
                                editedTexts.toMap(),
                                {
                                    isSavingDocx = false
                                    editedTexts.clear()
                                    isEditingDocx = false
                                    reloadTrigger++
                                },
                                {
                                    isSavingDocx = false
                                }
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("docx_save_edits_btn")
                    ) {
                        if (isSavingDocx) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text("Salvar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showCategoryPicker) {
        NoteCategoryPickerDialog(
            currentCategory = note.category,
            categories = categories,
            onSelectCategory = onChangeCategory,
            onCreateCategory = onCreateCategory,
            onDismiss = { showCategoryPicker = false }
        )
    }

    if (showRenameDialog) {
        RenameExternalDocumentDialog(
            currentTitle = note.title,
            docTypeLabel = "Word (.docx)",
            onDismiss = { showRenameDialog = false },
            onConfirm = { newTitle ->
                showRenameDialog = false
                onRenameDocument(newTitle)
            }
        )
    }
}

private fun extractDocxBlockPlainText(block: DocxBlock): Pair<Int, String>? {
    return when (block) {
        is DocxBlock.Heading -> if (block.sourceParagraphIndex >= 0) block.sourceParagraphIndex to block.runs.joinToString("") { it.text } else null
        is DocxBlock.Paragraph -> if (block.sourceParagraphIndex >= 0) block.sourceParagraphIndex to block.runs.joinToString("") { it.text } else null
        is DocxBlock.ListItem -> if (block.sourceParagraphIndex >= 0) block.sourceParagraphIndex to block.runs.joinToString("") { it.text } else null
        is DocxBlock.ChecklistItem -> if (block.sourceParagraphIndex >= 0) block.sourceParagraphIndex to block.runs.joinToString("") { it.text } else null
        is DocxBlock.Quote -> if (block.sourceParagraphIndex >= 0) block.sourceParagraphIndex to block.runs.joinToString("") { it.text } else null
        is DocxBlock.Callout -> if (block.sourceParagraphIndex >= 0) block.sourceParagraphIndex to block.runs.joinToString("") { it.text } else null
        else -> null
    }
}

@Composable
private fun DocxEditableBlockItem(
    block: DocxBlock,
    fontScale: Float,
    editedTexts: MutableMap<Int, String>
) {
    val extracted = extractDocxBlockPlainText(block)
    if (extracted == null) {
        DocxReadOnlyBlockItem(block = block, fontScale = fontScale)
        return
    }
    val (pIndex, originalText) = extracted
    val currentText = editedTexts[pIndex] ?: originalText
    val textColor = MaterialTheme.colorScheme.onBackground
    val primaryColor = MaterialTheme.colorScheme.primary
    val borderColor = if (editedTexts.containsKey(pIndex)) {
        primaryColor
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    }

    val (fontSizeSp, fontWeight, prefixLabel) = when (block) {
        is DocxBlock.Heading -> Triple(
            when (block.level) {
                1 -> 22f
                2 -> 19f
                3 -> 17f
                else -> 15.5f
            } * fontScale,
            FontWeight.Bold,
            "H${block.level}"
        )
        is DocxBlock.ListItem -> Triple(
            15f * fontScale,
            FontWeight.Normal,
            if (block.ordered) "${block.indexNumber}." else "•"
        )
        is DocxBlock.ChecklistItem -> Triple(
            15f * fontScale,
            FontWeight.Normal,
            if (block.checked) "☑" else "☐"
        )
        is DocxBlock.Quote -> Triple(14.5f * fontScale, FontWeight.Normal, "❝")
        is DocxBlock.Callout -> Triple(14.5f * fontScale, FontWeight.Normal, "ℹ")
        else -> Triple(15f * fontScale, FontWeight.Normal, null)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.7f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (prefixLabel != null) {
            Text(
                text = prefixLabel,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = primaryColor,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        BasicTextField(
            value = currentText,
            onValueChange = { newText ->
                val sanitized = newText.replace("\n", " ")
                if (sanitized == originalText) {
                    editedTexts.remove(pIndex)
                } else {
                    editedTexts[pIndex] = sanitized
                }
            },
            textStyle = TextStyle(
                color = textColor,
                fontSize = fontSizeSp.sp,
                lineHeight = (fontSizeSp * 1.4f).sp,
                fontWeight = fontWeight
            ),
            modifier = Modifier.weight(1f),
            decorationBox = { innerTextField ->
                if (currentText.isEmpty()) {
                    Text(
                        text = "(Parágrafo vazio — texto removido)",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    )
                }
                innerTextField()
            }
        )
    }
}

@Composable
private fun ExternalDocumentMissingState(
    message: String,
    relinkLabel: String,
    onRelink: () -> Unit,
    onRemoveLink: () -> Unit,
    onClose: () -> Unit,
    relinkTestTag: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.FolderOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(34.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Arquivo indisponível",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onRelink,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.testTag(relinkTestTag)
        ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(relinkLabel)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onRemoveLink, shape = RoundedCornerShape(14.dp)) {
                Text("Remover da Lista")
            }
            TextButton(onClick = onClose) {
                Text("Voltar")
            }
        }
    }
}

@Composable
private fun DocxReadOnlyBlockItem(
    block: DocxBlock,
    fontScale: Float
) {
    val isDark = isAppDarkTheme()
    val primaryColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onBackground
    val secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant
    val codeBg = if (isDark) Color(0xFF2B303B) else Color(0xFFF1F5F9)
    val highlightBg = if (isDark) Color(0xFF713F12) else Color(0xFFFEF08A)

    when (block) {
        is DocxBlock.Heading -> {
            val sizeSp = when (block.level) {
                1 -> 24f
                2 -> 20f
                3 -> 18f
                4 -> 16f
                else -> 15f
            } * fontScale
            val annotated = remember(block.runs, isDark) {
                buildDocxAnnotatedString(block.runs, primaryColor, codeBg, highlightBg, forceBold = true)
            }
            Text(
                text = annotated,
                fontSize = sizeSp.sp,
                lineHeight = (sizeSp * 1.3f).sp,
                fontWeight = FontWeight.ExtraBold,
                color = textColor,
                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
            )
        }

        is DocxBlock.Paragraph -> {
            val annotated = remember(block.runs, isDark) {
                buildDocxAnnotatedString(block.runs, primaryColor, codeBg, highlightBg)
            }
            val align = when (block.alignment.lowercase()) {
                "center" -> TextAlign.Center
                "right", "end" -> TextAlign.End
                "both", "justify" -> TextAlign.Justify
                else -> TextAlign.Start
            }
            if (annotated.text.isEmpty()) {
                Spacer(modifier = Modifier.height(18.dp))
            } else {
                Text(
                    text = annotated,
                    fontSize = (15f * fontScale).sp,
                    lineHeight = (22f * fontScale).sp,
                    color = textColor,
                    textAlign = align,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        is DocxBlock.ListItem -> {
            val annotated = remember(block.runs, isDark) {
                buildDocxAnnotatedString(block.runs, primaryColor, codeBg, highlightBg)
            }
            val indentDp = (block.indentLevel.coerceIn(0, 4) * 18).dp
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = indentDp + 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = if (block.ordered) "${block.indexNumber}." else "•",
                    fontSize = (15f * fontScale).sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryColor,
                    modifier = Modifier.width(if (block.ordered) 26.dp else 18.dp)
                )
                Text(
                    text = annotated,
                    fontSize = (15f * fontScale).sp,
                    lineHeight = (22f * fontScale).sp,
                    color = textColor,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        is DocxBlock.ChecklistItem -> {
            val annotated = remember(block.runs, isDark) {
                buildDocxAnnotatedString(block.runs, primaryColor, codeBg, highlightBg)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (block.checked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                    contentDescription = if (block.checked) "Concluído" else "Pendente",
                    tint = if (block.checked) Color(0xFF10B981) else MaterialTheme.colorScheme.outline,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(18.dp)
                )
                Text(
                    text = annotated,
                    fontSize = (15f * fontScale).sp,
                    lineHeight = (22f * fontScale).sp,
                    color = if (block.checked) secondaryColor else textColor,
                    textDecoration = if (block.checked) TextDecoration.LineThrough else null,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        is DocxBlock.Quote -> {
            val annotated = remember(block.runs, isDark) {
                buildDocxAnnotatedString(block.runs, primaryColor, codeBg, highlightBg)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        RoundedCornerShape(10.dp)
                    )
                    .padding(vertical = 8.dp, horizontal = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(22.dp)
                        .background(primaryColor, CircleShape)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = annotated,
                    fontSize = (14.5f * fontScale).sp,
                    lineHeight = (21f * fontScale).sp,
                    fontStyle = FontStyle.Italic,
                    color = secondaryColor,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        is DocxBlock.Callout -> {
            val annotated = remember(block.runs, isDark) {
                buildDocxAnnotatedString(block.runs, primaryColor, codeBg, highlightBg)
            }
            val (accent, icon, label) = when (block.type.lowercase()) {
                "tip" -> Triple(Color(0xFF10B981), Icons.Default.Lightbulb, "Dica")
                "warning" -> Triple(Color(0xFFF59E0B), Icons.Default.Warning, "Aviso")
                "important", "danger" -> Triple(Color(0xFFEF4444), Icons.Default.PriorityHigh, "Importante")
                else -> Triple(Color(0xFF3B82F6), Icons.Default.Info, "Nota")
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accent.copy(alpha = if (isDark) 0.16f else 0.1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                        Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = accent)
                    }
                    Text(
                        text = annotated,
                        fontSize = (14.5f * fontScale).sp,
                        lineHeight = (21f * fontScale).sp,
                        color = textColor
                    )
                }
            }
        }

        is DocxBlock.Code -> {
            val clipboard = LocalClipboardManager.current
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1E293B),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = block.language.ifBlank { "código" }.uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8)
                        )
                        IconButton(
                            onClick = { clipboard.setText(AnnotatedString(block.code)) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copiar código",
                                tint = Color(0xFFCBD5E1),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        Text(
                            text = block.code,
                            fontFamily = FontFamily.Monospace,
                            fontSize = (13f * fontScale).sp,
                            lineHeight = (19f * fontScale).sp,
                            color = Color(0xFFE2E8F0)
                        )
                    }
                }
            }
        }

        is DocxBlock.Math -> {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isDark) Color(0xFF1E3A5F).copy(alpha = 0.45f) else Color(0xFFEFF6FF),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF3B82F6).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Functions,
                        contentDescription = "Fórmula matemática",
                        tint = Color(0xFF3B82F6),
                        modifier = Modifier.size(18.dp)
                    )
                    LatexFormulaRenderer.LatexFormulaBlockView(
                        formula = block.formula,
                        textColor = textColor,
                        fontSize = (15f * fontScale).sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        is DocxBlock.Table -> {
            if (block.headers.isNotEmpty()) {
                val borderCol = MaterialTheme.colorScheme.outlineVariant
                val headerBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, borderCol, RoundedCornerShape(10.dp))
                    ) {
                        Row(modifier = Modifier.background(headerBg)) {
                            block.headers.forEach { cellRuns ->
                                val cellText = remember(cellRuns, isDark) {
                                    buildDocxAnnotatedString(cellRuns, primaryColor, codeBg, highlightBg, forceBold = true)
                                }
                                Text(
                                    text = cellText,
                                    fontSize = (13f * fontScale).sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor,
                                    modifier = Modifier
                                        .widthIn(min = 96.dp, max = 240.dp)
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                )
                            }
                        }
                        HorizontalDivider(color = borderCol)
                        block.rows.forEachIndexed { rIdx, row ->
                            Row {
                                block.headers.indices.forEach { colIdx ->
                                    val cellRuns = row.getOrNull(colIdx).orEmpty()
                                    val cellText = remember(cellRuns, isDark) {
                                        buildDocxAnnotatedString(cellRuns, primaryColor, codeBg, highlightBg)
                                    }
                                    Text(
                                        text = cellText,
                                        fontSize = (13f * fontScale).sp,
                                        color = textColor,
                                        modifier = Modifier
                                            .widthIn(min = 96.dp, max = 240.dp)
                                            .padding(horizontal = 10.dp, vertical = 7.dp)
                                    )
                                }
                            }
                            if (rIdx < block.rows.lastIndex) {
                                HorizontalDivider(color = borderCol.copy(alpha = 0.55f))
                            }
                        }
                    }
                }
            }
        }

        is DocxBlock.Divider -> {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }

        is DocxBlock.PageBreak -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    text = "Quebra de página",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
            }
        }

        is DocxBlock.Image -> {
            val decodedBitmap: ImageBitmap? = remember(block.mediaPath, block.bytes?.size) {
                block.bytes?.let { bytes ->
                    try {
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                        var sample = 1
                        while ((bounds.outWidth / sample) > 1440 || (bounds.outHeight / sample) > 1440) {
                            sample *= 2
                        }
                        val opts = BitmapFactory.Options().apply {
                            inSampleSize = sample
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
                    } catch (_: Throwable) {
                        null
                    }
                }
            }

            if (block.sideText.isNotBlank() && decodedBitmap != null) {
                val imgFraction = (block.widthPercent / 100f).coerceIn(0.3f, 0.55f)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    if (block.alignment != "right") {
                        Image(
                            bitmap = decodedBitmap,
                            contentDescription = block.alt.ifBlank { "Imagem do documento" },
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .weight(imgFraction)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Text(
                            text = block.sideText,
                            fontSize = (14.5f * fontScale).sp,
                            lineHeight = (21f * fontScale).sp,
                            color = textColor,
                            modifier = Modifier.weight(1f - imgFraction)
                        )
                    } else {
                        Text(
                            text = block.sideText,
                            fontSize = (14.5f * fontScale).sp,
                            lineHeight = (21f * fontScale).sp,
                            color = textColor,
                            modifier = Modifier.weight(1f - imgFraction)
                        )
                        Image(
                            bitmap = decodedBitmap,
                            contentDescription = block.alt.ifBlank { "Imagem do documento" },
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .weight(imgFraction)
                                .clip(RoundedCornerShape(10.dp))
                        )
                    }
                }
            } else if (decodedBitmap != null) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = when (block.alignment.lowercase()) {
                        "left" -> Alignment.CenterStart
                        "right" -> Alignment.CenterEnd
                        else -> Alignment.Center
                    }
                ) {
                    Image(
                        bitmap = decodedBitmap,
                        contentDescription = block.alt.ifBlank { "Imagem do documento" },
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth((block.widthPercent / 100f).coerceIn(0.35f, 1f))
                            .heightIn(max = 360.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                }
            }
        }
    }
}

private fun buildDocxAnnotatedString(
    runs: List<DocxRun>,
    linkColor: Color,
    codeBg: Color,
    highlightBg: Color,
    forceBold: Boolean = false
): AnnotatedString {
    return buildAnnotatedString {
        for (run in runs) {
            if (run.text.isEmpty()) continue
            val decorations = buildList {
                if (run.isUnderline || !run.linkUrl.isNullOrBlank()) add(TextDecoration.Underline)
                if (run.isStrikethrough) add(TextDecoration.LineThrough)
            }
            val parsedColor = run.colorHex
                ?.takeIf { it.length == 6 && !it.equals("000000", true) && !it.equals("FFFFFF", true) }
                ?.let { hex ->
                    try {
                        Color(android.graphics.Color.parseColor("#$hex"))
                    } catch (_: Exception) {
                        Color.Unspecified
                    }
                } ?: Color.Unspecified

            val span = SpanStyle(
                fontWeight = if (run.isBold || forceBold) FontWeight.Bold else null,
                fontStyle = if (run.isItalic) FontStyle.Italic else null,
                fontFamily = if (run.isCode) FontFamily.Monospace else null,
                textDecoration = if (decorations.isNotEmpty()) TextDecoration.combine(decorations) else null,
                background = when {
                    run.isHighlight -> highlightBg
                    run.isCode -> codeBg
                    else -> Color.Unspecified
                },
                color = if (!run.linkUrl.isNullOrBlank()) linkColor else parsedColor
            )

            val url = run.linkUrl?.takeIf { it.isNotBlank() }
            if (url != null) {
                withLink(
                    LinkAnnotation.Url(
                        url = url,
                        styles = TextLinkStyles(style = span)
                    )
                ) {
                    append(run.text)
                }
            } else {
                withStyle(span) {
                    append(run.text)
                }
            }
        }
    }
}
