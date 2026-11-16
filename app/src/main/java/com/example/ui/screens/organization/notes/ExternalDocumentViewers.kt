package com.example.ui.screens.organization.notes

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
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
import com.example.ui.theme.isAppDarkTheme
import com.example.util.DocxBlock
import com.example.util.DocxDocumentEngine
import com.example.util.DocxParsedDocument
import com.example.util.DocxRun
import com.example.util.ExternalDocumentUriHelper
import com.example.util.MathBeautifier
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

/**
 * Shared compact read-only document top bar for PDF and .docx viewers.
 */
@Composable
private fun ExternalDocumentReaderHeader(
    title: String,
    badgeLabel: String,
    badgeColor: Color,
    category: String,
    isConverting: Boolean,
    onClose: () -> Unit,
    onCategoryClick: () -> Unit,
    onConvertToNote: () -> Unit,
    closeTestTag: String,
    convertTestTag: String,
    categoryTestTag: String,
    badgeTestTag: String,
    secondaryControls: @Composable RowScope.() -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
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

                Button(
                    onClick = onConvertToNote,
                    enabled = !isConverting,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 40.dp)
                        .testTag(convertTestTag)
                ) {
                    if (isConverting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Convertendo...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(
                            imageVector = Icons.Default.EditNote,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Converter para Nota", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
    onRelinkClick: () -> Unit,
    onRemoveLink: () -> Unit,
    onConvertToNote: () -> Unit,
    isConverting: Boolean = false
) {
    BackHandler(enabled = !isConverting) { onClose() }

    val context = LocalContext.current
    var showCategoryPicker by remember { mutableStateOf(false) }
    var zoomPercent by remember { mutableIntStateOf(100) }
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
            loadError = "Arquivo PDF inacessível neste caminho. Se o arquivo foi movido ou a permissão expirou, toque em Re-vincular PDF."
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

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ExternalDocumentReaderHeader(
                title = note.title,
                badgeLabel = "PDF · Somente leitura",
                badgeColor = Color(0xFFDC2626),
                category = note.category,
                isConverting = isConverting,
                onClose = onClose,
                onCategoryClick = { showCategoryPicker = true },
                onConvertToNote = onConvertToNote,
                closeTestTag = "pdf_reader_close_btn",
                convertTestTag = "pdf_reader_convert_btn",
                categoryTestTag = "pdf_reader_category_chip",
                badgeTestTag = "pdf_reader_badge"
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                ) {
                    Text(
                        text = if (pageCount > 0) "Pág. $currentPageIndex de $pageCount" else "PDF externo",
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
                        onClick = { zoomPercent = (zoomPercent - 25).coerceAtLeast(75) },
                        enabled = zoomPercent > 75,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.ZoomOut, contentDescription = "Diminuir zoom", modifier = Modifier.size(18.dp))
                    }
                    Text(
                        text = "$zoomPercent%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = { zoomPercent = (zoomPercent + 25).coerceAtMost(225) },
                        enabled = zoomPercent < 225,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.ZoomIn, contentDescription = "Aumentar zoom", modifier = Modifier.size(18.dp))
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Caminho: ${ExternalDocumentUriHelper.formatReadablePath(note.externalUri)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        if (zoom > 1.04f) {
                            zoomPercent = (zoomPercent + 10).coerceAtMost(225)
                        } else if (zoom < 0.96f) {
                            zoomPercent = (zoomPercent - 10).coerceAtLeast(75)
                        }
                    }
                }
        ) {
            if (loadError != null) {
                ExternalDocumentMissingState(
                    message = loadError!!,
                    relinkLabel = "Re-vincular PDF",
                    onRelink = onRelinkClick,
                    onRemoveLink = onRemoveLink,
                    onClose = onClose,
                    relinkTestTag = "pdf_reader_relink_btn"
                )
            } else if (activeRenderer != null && pageCount > 0) {
                val renderer = activeRenderer!!
                val density = LocalDensity.current
                val horizontalScroll = rememberScrollState()

                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val baseWidthPx = with(density) { (maxWidth - 24.dp).toPx().toInt().coerceAtLeast(480) }
                    val renderBucket = when {
                        zoomPercent <= 100 -> 100
                        zoomPercent <= 150 -> 150
                        else -> 225
                    }
                    val targetWidthPx = (baseWidthPx * (renderBucket / 100f)).toInt().coerceAtMost(2200)
                    val displayWidthDp = (maxWidth - 24.dp) * (zoomPercent / 100f)

                    val listModifier = if (zoomPercent > 100) {
                        Modifier
                            .fillMaxSize()
                            .horizontalScroll(horizontalScroll)
                    } else {
                        Modifier.fillMaxSize()
                    }

                    LazyColumn(
                        state = listState,
                        modifier = listModifier,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
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
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
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
 * In-app read-only Word (.docx) reader backed by DocxDocumentEngine.
 * Images and content are read dynamically from the external file without saving in Citra until converted.
 */
@Composable
fun ExternalDocxReaderScreen(
    note: Note,
    categories: List<Category>,
    onClose: () -> Unit,
    onChangeCategory: (String) -> Unit,
    onCreateCategory: (String, String) -> Unit,
    onRelinkClick: () -> Unit,
    onRemoveLink: () -> Unit,
    onConvertToNote: () -> Unit,
    isConverting: Boolean = false
) {
    BackHandler(enabled = !isConverting) { onClose() }

    val context = LocalContext.current
    var showCategoryPicker by remember { mutableStateOf(false) }
    var textScale by remember { mutableFloatStateOf(1.0f) }
    var parsedDoc by remember { mutableStateOf<DocxParsedDocument?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(note.externalUri) {
        isLoading = true
        loadError = null
        parsedDoc = null
        val result = withContext(Dispatchers.IO) {
            val stream = ExternalDocumentUriHelper.openInputStream(context, note.externalUri)
            if (stream == null) {
                null to "Arquivo .docx não encontrado ou permissão revogada. Toque em Re-vincular .docx."
            } else {
                try {
                    stream.use { input ->
                        DocxDocumentEngine.parseDocx(input) to null
                    }
                } catch (e: Exception) {
                    null to "Não foi possível ler este .docx: ${e.message ?: "arquivo corrompido ou incompatível"}."
                }
            }
        }
        parsedDoc = result.first
        loadError = result.second
        isLoading = false
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ExternalDocumentReaderHeader(
                title = note.title,
                badgeLabel = ".docx · Somente leitura",
                badgeColor = Color(0xFF2563EB),
                category = note.category,
                isConverting = isConverting,
                onClose = onClose,
                onCategoryClick = { showCategoryPicker = true },
                onConvertToNote = onConvertToNote,
                closeTestTag = "docx_reader_close_btn",
                convertTestTag = "docx_reader_convert_btn",
                categoryTestTag = "docx_reader_category_chip",
                badgeTestTag = "docx_reader_badge"
            ) {
                Text(
                    text = "Modo Leitura Word (Edição bloqueada até converter)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

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
            }
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Caminho: ${ExternalDocumentUriHelper.formatReadablePath(note.externalUri)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
                                text = "Carregando documento .docx...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                loadError != null -> {
                    ExternalDocumentMissingState(
                        message = loadError!!,
                        relinkLabel = "Re-vincular .docx",
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
                                text = "Documento .docx sem conteúdo visível.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        SelectionContainer {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .widthIn(max = 760.dp)
                                    .align(Alignment.TopCenter),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                itemsIndexed(blocks) { _, block ->
                                    DocxReadOnlyBlockItem(block = block, fontScale = textScale)
                                }
                            }
                        }
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
                    imageVector = Icons.Default.LinkOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(34.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Arquivo externo indisponível",
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
                Text("Remover Vínculo")
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
                Spacer(modifier = Modifier.height(4.dp))
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
                    Text(
                        text = MathBeautifier.beautify(block.formula),
                        fontFamily = FontFamily.Serif,
                        fontSize = (15f * fontScale).sp,
                        color = textColor,
                        modifier = Modifier.horizontalScroll(rememberScrollState())
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
                        val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
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
