package com.example.ui.screens.organization.notes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.NoteDocumentType
import com.example.util.DocxBlock
import com.example.util.DocxDocumentEngine
import com.example.util.DocxRun
import com.example.util.EbookComicEngine
import com.example.util.ExternalDocumentUriHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

val PdfSheetAccentColor = Color(0xFFDC2626)
val DocxSheetAccentColor = Color(0xFF2563EB)
val EpubSheetAccentColor = Color(0xFF059669)
val MobiSheetAccentColor = Color(0xFFD97706)
val CbzSheetAccentColor = Color(0xFF7C3AED)
val Fb2SheetAccentColor = Color(0xFF0891B2)

fun accentColorForDocType(docType: NoteDocumentType): Color = when (docType) {
    NoteDocumentType.PDF -> PdfSheetAccentColor
    NoteDocumentType.DOCX -> DocxSheetAccentColor
    NoteDocumentType.EPUB -> EpubSheetAccentColor
    NoteDocumentType.MOBI -> MobiSheetAccentColor
    NoteDocumentType.CBZ -> CbzSheetAccentColor
    NoteDocumentType.FB2 -> Fb2SheetAccentColor
    NoteDocumentType.MARKDOWN -> PdfSheetAccentColor
}

data class DocxPreviewEntry(
    val lines: List<DocxPreviewItem>
)

sealed class DocxPreviewItem {
    data class Heading(val text: String, val level: Int) : DocxPreviewItem()
    data class Paragraph(val styled: AnnotatedString) : DocxPreviewItem()
    data class ListRow(val prefix: String, val styled: AnnotatedString) : DocxPreviewItem()
    data class TableHint(val headerPreview: String, val rowCount: Int) : DocxPreviewItem()
    data class ImageHint(val alt: String) : DocxPreviewItem()
}

object DocumentCardPreviewCache {
    private val previewSemaphore = Semaphore(2)

    private val pdfThumbCache: LruCache<String, Bitmap> by lazy {
        val maxKb = (Runtime.getRuntime().maxMemory() / 1024L).toInt().coerceAtLeast(16 * 1024)
        val budgetKb = (maxKb / 10).coerceIn(6 * 1024, 16 * 1024)
        object : LruCache<String, Bitmap>(budgetKb) {
            override fun sizeOf(key: String, value: Bitmap): Int =
                (value.byteCount / 1024).coerceAtLeast(1)
        }
    }
    private val docxPreviewCache = LruCache<String, DocxPreviewEntry>(48)

    fun getPdfThumbnail(uri: String): Bitmap? =
        synchronized(pdfThumbCache) {
            pdfThumbCache.get(uri)?.takeIf { !it.isRecycled }
        }

    fun putPdfThumbnail(uri: String, bitmap: Bitmap) {
        synchronized(pdfThumbCache) {
            pdfThumbCache.put(uri, bitmap)
        }
    }

    fun getDocxPreview(uri: String): DocxPreviewEntry? =
        synchronized(docxPreviewCache) {
            docxPreviewCache.get(uri)
        }

    fun putDocxPreview(uri: String, entry: DocxPreviewEntry) {
        synchronized(docxPreviewCache) {
            docxPreviewCache.put(uri, entry)
        }
    }

    fun getEbookThumbnail(uri: String, docType: NoteDocumentType): Bitmap? =
        synchronized(pdfThumbCache) {
            pdfThumbCache.get("${docType.id}|$uri")?.takeIf { !it.isRecycled }
        }

    fun putEbookThumbnail(uri: String, docType: NoteDocumentType, bitmap: Bitmap) {
        synchronized(pdfThumbCache) {
            pdfThumbCache.put("${docType.id}|$uri", bitmap)
        }
    }

    fun invalidatePdfThumbnail(uri: String) {
        synchronized(pdfThumbCache) {
            pdfThumbCache.remove(uri)
            NoteDocumentType.EBOOK_COMIC_TYPES.forEach { t ->
                pdfThumbCache.remove("${t.id}|$uri")
            }
        }
    }

    fun invalidateDocxPreview(uri: String) {
        synchronized(docxPreviewCache) {
            docxPreviewCache.remove(uri)
        }
    }

    fun invalidate(uri: String) {
        invalidatePdfThumbnail(uri)
        invalidateDocxPreview(uri)
    }

    suspend fun getOrRenderPdfThumbnail(context: Context, uriStr: String): Bitmap? {
        getPdfThumbnail(uriStr)?.let { return it }
        val rendered = withContext(Dispatchers.IO) {
            previewSemaphore.withPermit {
                getPdfThumbnail(uriStr)?.let { return@withPermit it }
                try {
                    ExternalDocumentUriHelper.openParcelFileDescriptor(context, uriStr)?.use { pfd ->
                        PdfRenderer(pfd).use { renderer ->
                            if (renderer.pageCount <= 0) return@use null
                            renderer.openPage(0).use { page ->
                                val targetW = 300
                                val ratio = page.height.toFloat() / page.width.coerceAtLeast(1).toFloat()
                                val targetH = (targetW * ratio).toInt().coerceIn(200, 440)
                                val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(AndroidColor.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                bmp
                            }
                        }
                    }
                } catch (_: Throwable) {
                    null
                }
            }
        }
        if (rendered != null) {
            putPdfThumbnail(uriStr, rendered)
        }
        return rendered
    }

    suspend fun getOrParseDocxPreview(context: Context, uriStr: String): DocxPreviewEntry? {
        getDocxPreview(uriStr)?.let { return it }
        val parsed = withContext(Dispatchers.IO) {
            previewSemaphore.withPermit {
                getDocxPreview(uriStr)?.let { return@withPermit it }
                try {
                    val stream = ExternalDocumentUriHelper.openInputStream(context, uriStr) ?: return@withPermit null
                    stream.use { inp ->
                        val doc = DocxDocumentEngine.parseDocx(inp)
                        val items = mutableListOf<DocxPreviewItem>()
                        for (block in doc.blocks) {
                            if (block is DocxBlock.PageBreak) break
                            if (items.size >= 7) break
                            when (block) {
                                is DocxBlock.Heading -> {
                                    val text = block.runs.joinToString("") { it.text }.trim()
                                    if (text.isNotBlank()) items.add(DocxPreviewItem.Heading(text, block.level))
                                }
                                is DocxBlock.Paragraph -> {
                                    val raw = block.runs.joinToString("") { it.text }.trim()
                                    if (raw.isNotBlank()) items.add(DocxPreviewItem.Paragraph(buildPreviewAnnotated(block.runs)))
                                }
                                is DocxBlock.ListItem -> {
                                    val raw = block.runs.joinToString("") { it.text }.trim()
                                    if (raw.isNotBlank()) {
                                        val prefix = if (block.ordered) "${block.indexNumber}." else "•"
                                        items.add(DocxPreviewItem.ListRow(prefix, buildPreviewAnnotated(block.runs)))
                                    }
                                }
                                is DocxBlock.ChecklistItem -> {
                                    val raw = block.runs.joinToString("") { it.text }.trim()
                                    if (raw.isNotBlank()) {
                                        val prefix = if (block.checked) "☑" else "☐"
                                        items.add(DocxPreviewItem.ListRow(prefix, buildPreviewAnnotated(block.runs)))
                                    }
                                }
                                is DocxBlock.Quote -> {
                                    val raw = block.runs.joinToString("") { it.text }.trim()
                                    if (raw.isNotBlank()) items.add(DocxPreviewItem.ListRow("┃", buildPreviewAnnotated(block.runs)))
                                }
                                is DocxBlock.Callout -> {
                                    val raw = block.runs.joinToString("") { it.text }.trim()
                                    if (raw.isNotBlank()) items.add(DocxPreviewItem.ListRow("!", buildPreviewAnnotated(block.runs)))
                                }
                                is DocxBlock.Table -> {
                                    val headerRow = block.headers.joinToString(" | ") { cell ->
                                        cell.joinToString("") { it.text }.trim()
                                    }.trim()
                                    val firstBodyRow = block.rows.firstOrNull()?.joinToString(" | ") { cell ->
                                        cell.joinToString("") { it.text }.trim()
                                    }?.trim().orEmpty()
                                    val summaryRow = headerRow.ifBlank { firstBodyRow }
                                    if (summaryRow.isNotBlank()) {
                                        items.add(DocxPreviewItem.TableHint(summaryRow, block.rows.size + if (headerRow.isNotBlank()) 1 else 0))
                                    }
                                }
                                is DocxBlock.Image -> {
                                    items.add(DocxPreviewItem.ImageHint(block.alt.ifBlank { "Imagem" }))
                                }
                                is DocxBlock.Code -> {
                                    val firstLine = block.code.lineSequence().firstOrNull()?.trim().orEmpty()
                                    if (firstLine.isNotBlank()) {
                                        items.add(DocxPreviewItem.Paragraph(AnnotatedString(firstLine)))
                                    }
                                }
                                is DocxBlock.Math -> {
                                    if (block.formula.isNotBlank()) {
                                        items.add(DocxPreviewItem.Paragraph(AnnotatedString(block.formula)))
                                    }
                                }
                                else -> Unit
                            }
                        }
                        DocxPreviewEntry(items)
                    }
                } catch (_: Throwable) {
                    null
                }
            }
        }
        if (parsed != null) {
            putDocxPreview(uriStr, parsed)
        }
        return parsed
    }

    suspend fun getOrLoadEbookThumbnail(
        context: Context,
        uriStr: String,
        docType: NoteDocumentType
    ): Bitmap? {
        getEbookThumbnail(uriStr, docType)?.let { return it }
        val rendered = withContext(Dispatchers.IO) {
            previewSemaphore.withPermit {
                getEbookThumbnail(uriStr, docType)?.let { return@withPermit it }
                try {
                    EbookComicEngine.renderCoverOrPageOneThumbnail(
                        context = context,
                        pathOrUri = uriStr,
                        docType = docType
                    )
                } catch (_: Throwable) {
                    null
                }
            }
        }
        if (rendered != null) {
            putEbookThumbnail(uriStr, docType, rendered)
        }
        return rendered
    }

    suspend fun prewarmDocumentPreview(context: Context, uriStr: String, isPdf: Boolean) {
        prewarmDocumentPreview(
            context = context,
            uriStr = uriStr,
            docType = if (isPdf) NoteDocumentType.PDF else NoteDocumentType.DOCX
        )
    }

    suspend fun prewarmDocumentPreview(context: Context, uriStr: String, docType: NoteDocumentType) {
        if (uriStr.isBlank()) return
        when {
            docType == NoteDocumentType.PDF -> getOrRenderPdfThumbnail(context, uriStr)
            docType == NoteDocumentType.DOCX -> getOrParseDocxPreview(context, uriStr)
            docType.isEbookOrComic -> getOrLoadEbookThumbnail(context, uriStr, docType)
        }
    }
}

private fun buildPreviewAnnotated(runs: List<DocxRun>): AnnotatedString {
    return buildAnnotatedString {
        for (run in runs) {
            val weight = if (run.isBold) FontWeight.Bold else FontWeight.Normal
            val style = if (run.isItalic) FontStyle.Italic else FontStyle.Normal
            val dec = when {
                run.isUnderline && run.isStrikethrough -> TextDecoration.combine(
                    listOf(TextDecoration.Underline, TextDecoration.LineThrough)
                )
                run.isUnderline -> TextDecoration.Underline
                run.isStrikethrough -> TextDecoration.LineThrough
                else -> TextDecoration.None
            }
            withStyle(
                SpanStyle(
                    fontWeight = weight,
                    fontStyle = style,
                    textDecoration = dec
                )
            ) {
                append(run.text)
            }
        }
    }
}

@Composable
fun PdfPageOnePreviewBox(
    externalUri: String?,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var previewBitmap by remember(externalUri) {
        mutableStateOf(externalUri?.let { DocumentCardPreviewCache.getPdfThumbnail(it) })
    }
    var loadFailed by remember(externalUri) { mutableStateOf(false) }

    LaunchedEffect(externalUri) {
        val uriStr = externalUri?.trim()
        if (uriStr.isNullOrBlank()) {
            loadFailed = true
            return@LaunchedEffect
        }
        val rendered = DocumentCardPreviewCache.getOrRenderPdfThumbnail(context, uriStr)
        if (rendered != null) {
            previewBitmap = rendered
            loadFailed = false
        } else {
            loadFailed = true
        }
    }

    val sheetShape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .clip(sheetShape)
            .background(Color.White)
            .border(1.dp, PdfSheetAccentColor.copy(alpha = 0.35f), sheetShape)
    ) {
        val bmp = previewBitmap
        if (bmp != null && !bmp.isRecycled) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Prévia da página 1 do PDF",
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.PictureAsPdf,
                    contentDescription = "Documento PDF",
                    tint = PdfSheetAccentColor.copy(alpha = 0.8f),
                    modifier = Modifier.size(if (compact) 20.dp else 26.dp)
                )
                if (!compact) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = if (loadFailed) "PDF · Página 1" else "Carregando pág. 1...",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF475569),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Vivid red PDF sigla badge directly on the sheet
        Surface(
            shape = RoundedCornerShape(bottomEnd = 8.dp),
            color = PdfSheetAccentColor,
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Text(
                text = "PDF",
                fontSize = if (compact) 8.sp else 9.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                modifier = Modifier.padding(
                    horizontal = if (compact) 5.dp else 7.dp,
                    vertical = if (compact) 1.5.dp else 2.5.dp
                )
            )
        }
    }
}

@Composable
fun DocxPageOnePreviewBox(
    externalUri: String?,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var previewEntry by remember(externalUri) {
        mutableStateOf(externalUri?.let { DocumentCardPreviewCache.getDocxPreview(it) })
    }
    var loadFailed by remember(externalUri) { mutableStateOf(false) }

    LaunchedEffect(externalUri) {
        val uriStr = externalUri?.trim()
        if (uriStr.isNullOrBlank()) {
            loadFailed = true
            return@LaunchedEffect
        }
        val parsed = DocumentCardPreviewCache.getOrParseDocxPreview(context, uriStr)
        if (parsed != null) {
            previewEntry = parsed
            loadFailed = false
        } else {
            loadFailed = true
        }
    }

    val sheetShape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .clip(sheetShape)
            .background(Color.White)
            .border(1.dp, DocxSheetAccentColor.copy(alpha = 0.35f), sheetShape)
    ) {
        val entry = previewEntry
        if (entry != null && entry.lines.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = if (compact) 6.dp else 8.dp,
                        end = if (compact) 6.dp else 8.dp,
                        top = if (compact) 18.dp else 22.dp,
                        bottom = 5.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(if (compact) 1.5.dp else 2.5.dp)
            ) {
                val visibleItems = entry.lines.take(if (compact) 3 else 5)
                for (item in visibleItems) {
                    when (item) {
                        is DocxPreviewItem.Heading -> {
                            Text(
                                text = item.text,
                                fontSize = if (compact) 8.5.sp else 9.5.sp,
                                lineHeight = if (compact) 10.sp else 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF0F172A),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        is DocxPreviewItem.Paragraph -> {
                            Text(
                                text = item.styled,
                                fontSize = if (compact) 7.5.sp else 8.5.sp,
                                lineHeight = if (compact) 9.5.sp else 10.5.sp,
                                color = Color(0xFF334155),
                                maxLines = if (compact) 1 else 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        is DocxPreviewItem.ListRow -> {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = item.prefix,
                                    fontSize = if (compact) 7.5.sp else 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DocxSheetAccentColor
                                )
                                Text(
                                    text = item.styled,
                                    fontSize = if (compact) 7.5.sp else 8.5.sp,
                                    lineHeight = if (compact) 9.5.sp else 10.5.sp,
                                    color = Color(0xFF334155),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        is DocxPreviewItem.TableHint -> {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TableChart,
                                    contentDescription = null,
                                    tint = DocxSheetAccentColor,
                                    modifier = Modifier.size(9.dp)
                                )
                                Text(
                                    text = item.headerPreview,
                                    fontSize = 7.5.sp,
                                    color = Color(0xFF334155),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        is DocxPreviewItem.ImageHint -> {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = null,
                                    tint = DocxSheetAccentColor,
                                    modifier = Modifier.size(9.dp)
                                )
                                Text(
                                    text = item.alt,
                                    fontSize = 7.5.sp,
                                    color = Color(0xFF475569),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Article,
                    contentDescription = "Documento Word",
                    tint = DocxSheetAccentColor.copy(alpha = 0.8f),
                    modifier = Modifier.size(if (compact) 18.dp else 24.dp)
                )
                if (!compact) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = if (loadFailed) "Word (.docx) · Pág. 1" else "Lendo conteúdo...",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF475569),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Vivid blue DOCX sigla badge directly on the sheet
        Surface(
            shape = RoundedCornerShape(bottomEnd = 8.dp),
            color = DocxSheetAccentColor,
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Text(
                text = "DOCX",
                fontSize = if (compact) 7.5.sp else 9.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                modifier = Modifier.padding(
                    horizontal = if (compact) 5.dp else 7.dp,
                    vertical = if (compact) 1.5.dp else 2.5.dp
                )
            )
        }
    }
}

@Composable
fun EbookPageOnePreviewBox(
    externalUri: String?,
    docType: NoteDocumentType,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var previewBitmap by remember(externalUri, docType) {
        mutableStateOf(externalUri?.let { DocumentCardPreviewCache.getEbookThumbnail(it, docType) })
    }
    var loadFailed by remember(externalUri, docType) { mutableStateOf(false) }

    LaunchedEffect(externalUri, docType) {
        val uriStr = externalUri?.trim()
        if (uriStr.isNullOrBlank()) {
            loadFailed = true
            return@LaunchedEffect
        }
        val rendered = DocumentCardPreviewCache.getOrLoadEbookThumbnail(context, uriStr, docType)
        if (rendered != null) {
            previewBitmap = rendered
            loadFailed = false
        } else {
            loadFailed = true
        }
    }

    val accent = accentColorForDocType(docType)
    val sheetShape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .clip(sheetShape)
            .background(Color.White)
            .border(1.dp, accent.copy(alpha = 0.35f), sheetShape)
    ) {
        val bmp = previewBitmap
        if (bmp != null && !bmp.isRecycled) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Prévia da capa ou página 1 (${docType.label})",
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = if (docType == NoteDocumentType.CBZ) Icons.Default.AutoStories else Icons.Default.MenuBook,
                    contentDescription = "Documento ${docType.label}",
                    tint = accent.copy(alpha = 0.85f),
                    modifier = Modifier.size(if (compact) 20.dp else 26.dp)
                )
                if (!compact) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = if (loadFailed) "${docType.label} · Página 1" else "Carregando capa...",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF475569),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(bottomEnd = 8.dp),
            color = accent,
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Text(
                text = docType.label,
                fontSize = if (compact) 7.5.sp else 9.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                modifier = Modifier.padding(
                    horizontal = if (compact) 5.dp else 7.dp,
                    vertical = if (compact) 1.5.dp else 2.5.dp
                )
            )
        }
    }
}

@Composable
fun ExternalDocumentPageOnePreviewBox(
    externalUri: String?,
    docType: NoteDocumentType,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    when {
        docType == NoteDocumentType.PDF -> PdfPageOnePreviewBox(
            externalUri = externalUri,
            modifier = modifier,
            compact = compact
        )
        docType == NoteDocumentType.DOCX -> DocxPageOnePreviewBox(
            externalUri = externalUri,
            modifier = modifier,
            compact = compact
        )
        else -> EbookPageOnePreviewBox(
            externalUri = externalUri,
            docType = docType,
            modifier = modifier,
            compact = compact
        )
    }
}
