package com.example.ui.screens.organization.notes

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.util.MediaStorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

data class CropAspectRatio(
    val id: String,
    val label: String,
    val ratioWidth: Float,
    val ratioHeight: Float,
    val iconWidthDp: Int,
    val iconHeightDp: Int
)

val CropAspectRatios = listOf(
    CropAspectRatio("FREE", "Livre", 0f, 0f, 20, 20),
    CropAspectRatio("1_1", "1:1", 1f, 1f, 18, 18),
    CropAspectRatio("2_3", "2:3", 2f, 3f, 14, 21),
    CropAspectRatio("3_4", "3:4", 3f, 4f, 15, 20),
    CropAspectRatio("4_3", "4:3", 4f, 3f, 20, 15),
    CropAspectRatio("9_16", "9:16", 9f, 16f, 13, 23),
    CropAspectRatio("16_9", "16:9", 16f, 9f, 23, 13)
)

private enum class CropDragHandle {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    TOP,
    BOTTOM,
    LEFT,
    RIGHT,
    MOVE
}

@Composable
fun ImageFullScreenCropRotateDialog(
    imageUrl: String,
    onDismiss: () -> Unit,
    onSaveImage: (newPath: String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    var rotationDegrees by remember { mutableIntStateOf(0) }
    var isCropMode by remember { mutableStateOf(false) }
    var selectedRatio by remember { mutableStateOf(CropAspectRatios[0]) } // Free by default
    var isProcessing by remember { mutableStateOf(false) }

    // Normalized crop box state within the actual displayed image bounds (0f..1f range)
    var cropLeft by remember { mutableFloatStateOf(0.05f) }
    var cropTop by remember { mutableFloatStateOf(0.05f) }
    var cropRight by remember { mutableFloatStateOf(0.95f) }
    var cropBottom by remember { mutableFloatStateOf(0.95f) }
    var activeHandle by remember { mutableStateOf<CropDragHandle?>(null) }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(isCropMode, rotationDegrees) {
        if (isCropMode) {
            zoomScale = 1f
            panOffset = Offset.Zero
        } else {
            panOffset = Offset.Zero
        }
    }

    // Raw loaded bitmap and its rotated display version
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var rotatedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(imageUrl) {
        withContext(Dispatchers.IO) {
            sourceBitmap = MediaStorageHelper.loadBitmap(context, imageUrl)
        }
    }

    LaunchedEffect(sourceBitmap, rotationDegrees) {
        val bmp = sourceBitmap
        rotatedBitmap = when {
            bmp == null -> null
            rotationDegrees % 360 == 0 -> bmp
            else -> withContext(Dispatchers.Default) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
            }
        }
    }

    // Current rotated intrinsic aspect ratio (width / height)
    val currentImageAspect = remember(sourceBitmap, rotatedBitmap, rotationDegrees) {
        val r = rotatedBitmap
        if (r != null && r.height > 0) {
            r.width.toFloat() / r.height.toFloat()
        } else {
            val s = sourceBitmap
            if (s != null && s.width > 0 && s.height > 0) {
                if (rotationDegrees % 180 == 0) s.width.toFloat() / s.height.toFloat()
                else s.height.toFloat() / s.width.toFloat()
            } else {
                1f
            }
        }
    }

    fun updateCropBoxForRatio(ratio: CropAspectRatio, imgAspect: Float = currentImageAspect) {
        selectedRatio = ratio
        if (ratio.ratioWidth <= 0f || ratio.ratioHeight <= 0f) {
            cropLeft = 0.05f
            cropTop = 0.05f
            cropRight = 0.95f
            cropBottom = 0.95f
            return
        }

        // targetPixelRatio = (normW * imgW) / (normH * imgH) = (normW / normH) * imgAspect
        val targetPixelRatio = ratio.ratioWidth / ratio.ratioHeight
        val normRatio = (targetPixelRatio / imgAspect.coerceAtLeast(0.01f)).coerceAtLeast(0.01f)
        val maxSpan = 0.88f
        val normW: Float
        val normH: Float
        if (normRatio >= 1f) {
            normW = maxSpan
            normH = (maxSpan / normRatio).coerceIn(0.08f, maxSpan)
        } else {
            normH = maxSpan
            normW = (maxSpan * normRatio).coerceIn(0.08f, maxSpan)
        }
        val left = ((1f - normW) / 2f).coerceIn(0f, 1f - normW)
        val top = ((1f - normH) / 2f).coerceIn(0f, 1f - normH)
        cropLeft = left
        cropTop = top
        cropRight = (left + normW).coerceAtMost(1f)
        cropBottom = (top + normH).coerceAtMost(1f)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("full_screen_image_crop_dialog"),
            color = Color.Black
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.White)
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Rotate 90° button
                        IconButton(
                            onClick = {
                                val nextRot = (rotationDegrees + 90) % 360
                                rotationDegrees = nextRot
                                if (isCropMode) {
                                    if (selectedRatio.ratioWidth > 0f && selectedRatio.ratioHeight > 0f) {
                                        val nextAspect = 1f / currentImageAspect.coerceAtLeast(0.01f)
                                        updateCropBoxForRatio(selectedRatio, nextAspect)
                                    } else {
                                        // Rotate freeform crop box 90° CW in normalized space
                                        val oldL = cropLeft
                                        val oldT = cropTop
                                        val oldR = cropRight
                                        val oldB = cropBottom
                                        cropLeft = (1f - oldB).coerceIn(0f, 0.9f)
                                        cropTop = oldL.coerceIn(0f, 0.9f)
                                        cropRight = (1f - oldT).coerceIn(cropLeft + 0.05f, 1f)
                                        cropBottom = oldR.coerceIn(cropTop + 0.05f, 1f)
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .testTag("image_rotate_btn")
                        ) {
                            Icon(
                                Icons.Default.RotateRight,
                                contentDescription = "Rotacionar 90°",
                                tint = Color.White
                            )
                        }

                        // Crop Mode Toggle (preserves FREE mode so corners are immediately resizable)
                        IconButton(
                            onClick = {
                                isCropMode = !isCropMode
                                if (isCropMode && selectedRatio.id == "FREE" &&
                                    (cropRight - cropLeft < 0.08f || cropBottom - cropTop < 0.08f)
                                ) {
                                    updateCropBoxForRatio(CropAspectRatios[0])
                                }
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    if (isCropMode) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.5f),
                                    CircleShape
                                )
                                .testTag("image_crop_mode_btn")
                        ) {
                            Icon(
                                Icons.Default.Crop,
                                contentDescription = "Modo Cortar",
                                tint = if (isCropMode) Color.White else Color.White.copy(alpha = 0.85f)
                            )
                        }

                        // Save button
                        Button(
                            onClick = {
                                if (rotationDegrees == 0 && !isCropMode) {
                                    onDismiss()
                                    return@Button
                                }
                                val bmp = sourceBitmap ?: return@Button
                                isProcessing = true
                                coroutineScope.launch {
                                    withContext(Dispatchers.IO) {
                                        try {
                                            val rotated = if (rotationDegrees % 360 != 0) {
                                                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                                                Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
                                            } else {
                                                bmp
                                            }

                                            val finalBitmap = if (isCropMode) {
                                                val cropX = (cropLeft * rotated.width).roundToInt().coerceIn(0, rotated.width - 1)
                                                val cropY = (cropTop * rotated.height).roundToInt().coerceIn(0, rotated.height - 1)
                                                val cropW = ((cropRight - cropLeft) * rotated.width).roundToInt().coerceIn(1, rotated.width - cropX)
                                                val cropH = ((cropBottom - cropTop) * rotated.height).roundToInt().coerceIn(1, rotated.height - cropY)
                                                Bitmap.createBitmap(rotated, cropX, cropY, cropW, cropH)
                                            } else {
                                                rotated
                                            }

                                            val newPath = MediaStorageHelper.saveNoteImageBitmap(context, finalBitmap)
                                            withContext(Dispatchers.Main) {
                                                isProcessing = false
                                                if (newPath != null) {
                                                    onSaveImage(newPath)
                                                }
                                                onDismiss()
                                            }
                                        } catch (_: Exception) {
                                            withContext(Dispatchers.Main) {
                                                isProcessing = false
                                                onDismiss()
                                            }
                                        }
                                    }
                                }
                            },
                            enabled = !isProcessing,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("image_save_crop_btn")
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Guardar", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }

                // Main Interactive Image Area with Dynamic Corner/Edge Resizable Crop Box
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val containerWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                    val containerHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                    val containerAspect = containerWidthPx / containerHeightPx
                    val fittedWidthPx: Float
                    val fittedHeightPx: Float
                    if (currentImageAspect > containerAspect) {
                        fittedWidthPx = containerWidthPx
                        fittedHeightPx = (containerWidthPx / currentImageAspect).coerceAtLeast(1f)
                    } else {
                        fittedHeightPx = containerHeightPx
                        fittedWidthPx = (containerHeightPx * currentImageAspect).coerceAtLeast(1f)
                    }

                    val fittedWidthDp = with(density) { fittedWidthPx.toDp() }
                    val fittedHeightDp = with(density) { fittedHeightPx.toDp() }

                    val currentLeft by rememberUpdatedState(cropLeft)
                    val currentTop by rememberUpdatedState(cropTop)
                    val currentRight by rememberUpdatedState(cropRight)
                    val currentBottom by rememberUpdatedState(cropBottom)
                    val currentRatio by rememberUpdatedState(selectedRatio)
                    val latestWidthPx by rememberUpdatedState(fittedWidthPx)
                    val latestHeightPx by rememberUpdatedState(fittedHeightPx)

                    // Box matched 1:1 to the displayed rotated image bounds so crop coordinates never drift
                    Box(
                        modifier = Modifier
                            .size(width = fittedWidthDp, height = fittedHeightDp)
                            .clipToBounds()
                            .then(
                                if (!isCropMode) {
                                    Modifier
                                        .pointerInput(fittedWidthPx, fittedHeightPx) {
                                            detectTapGestures(
                                                onDoubleTap = { tapOffset ->
                                                    if (zoomScale > 1.15f) {
                                                        zoomScale = 1f
                                                        panOffset = Offset.Zero
                                                    } else {
                                                        val target = 2.5f
                                                        val center = Offset(fittedWidthPx / 2f, fittedHeightPx / 2f)
                                                        val maxX = (fittedWidthPx * (target - 1f)) / 2f
                                                        val maxY = (fittedHeightPx * (target - 1f)) / 2f
                                                        val rawPan = (center - tapOffset) * (target - 1f)
                                                        zoomScale = target
                                                        panOffset = Offset(
                                                            x = rawPan.x.coerceIn(-maxX, maxX),
                                                            y = rawPan.y.coerceIn(-maxY, maxY)
                                                        )
                                                    }
                                                }
                                            )
                                        }
                                        .pointerInput(fittedWidthPx, fittedHeightPx) {
                                            detectTransformGestures { _, pan, zoom, _ ->
                                                val updated = (zoomScale * zoom).coerceIn(1f, 4f)
                                                zoomScale = updated
                                                if (updated <= 1.02f) {
                                                    panOffset = Offset.Zero
                                                } else {
                                                    val maxX = (fittedWidthPx * (updated - 1f)) / 2f
                                                    val maxY = (fittedHeightPx * (updated - 1f)) / 2f
                                                    panOffset = Offset(
                                                        x = (panOffset.x + pan.x).coerceIn(-maxX, maxX),
                                                        y = (panOffset.y + pan.y).coerceIn(-maxY, maxY)
                                                    )
                                                }
                                            }
                                        }
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        val previewBmp = rotatedBitmap
                        val imageTransformModifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (!isCropMode) {
                                    Modifier.graphicsLayer(
                                        scaleX = zoomScale,
                                        scaleY = zoomScale,
                                        translationX = panOffset.x,
                                        translationY = panOffset.y
                                    )
                                } else Modifier
                            )
                        if (previewBmp != null) {
                            Image(
                                bitmap = previewBmp.asImageBitmap(),
                                contentDescription = "Visualização da Imagem",
                                modifier = imageTransformModifier,
                                contentScale = ContentScale.FillBounds
                            )
                        } else {
                            AsyncImage(
                                model = imageUrl,
                                contentDescription = "Visualização da Imagem",
                                modifier = imageTransformModifier,
                                contentScale = ContentScale.Fit
                            )
                        }

                        if (isCropMode) {
                            Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(isCropMode) {
                                        detectDragGestures(
                                            onDragStart = { startOffset ->
                                                val w = latestWidthPx.coerceAtLeast(1f)
                                                val h = latestHeightPx.coerceAtLeast(1f)
                                                val l = currentLeft * w
                                                val t = currentTop * h
                                                val r = currentRight * w
                                                val b = currentBottom * h

                                                val cornerRadiusPx = 36.dp.toPx()
                                                val edgeTolPx = 22.dp.toPx()

                                                val corners = listOf(
                                                    CropDragHandle.TOP_LEFT to Offset(l, t),
                                                    CropDragHandle.TOP_RIGHT to Offset(r, t),
                                                    CropDragHandle.BOTTOM_LEFT to Offset(l, b),
                                                    CropDragHandle.BOTTOM_RIGHT to Offset(r, b)
                                                )
                                                val closestCorner = corners.minByOrNull { (_, pos) ->
                                                    hypot(startOffset.x - pos.x, startOffset.y - pos.y)
                                                }
                                                val closestCornerDist = closestCorner?.let { (_, pos) ->
                                                    hypot(startOffset.x - pos.x, startOffset.y - pos.y)
                                                } ?: Float.MAX_VALUE

                                                activeHandle = when {
                                                    closestCorner != null && closestCornerDist <= cornerRadiusPx -> closestCorner.first
                                                    abs(startOffset.x - l) <= edgeTolPx && startOffset.y in (t - edgeTolPx)..(b + edgeTolPx) -> CropDragHandle.LEFT
                                                    abs(startOffset.x - r) <= edgeTolPx && startOffset.y in (t - edgeTolPx)..(b + edgeTolPx) -> CropDragHandle.RIGHT
                                                    abs(startOffset.y - t) <= edgeTolPx && startOffset.x in (l - edgeTolPx)..(r + edgeTolPx) -> CropDragHandle.TOP
                                                    abs(startOffset.y - b) <= edgeTolPx && startOffset.x in (l - edgeTolPx)..(r + edgeTolPx) -> CropDragHandle.BOTTOM
                                                    startOffset.x in l..r && startOffset.y in t..b -> CropDragHandle.MOVE
                                                    closestCorner != null && closestCornerDist <= 56.dp.toPx() -> closestCorner.first
                                                    else -> null
                                                }
                                            },
                                            onDragEnd = { activeHandle = null },
                                            onDragCancel = { activeHandle = null },
                                            onDrag = { change, dragAmount ->
                                                val handle = activeHandle ?: return@detectDragGestures
                                                change.consume()

                                                val w = latestWidthPx.coerceAtLeast(1f)
                                                val h = latestHeightPx.coerceAtLeast(1f)
                                                val minSpanPx = 44.dp.toPx().coerceAtMost(minOf(w, h) * 0.45f)
                                                val minNormW = (minSpanPx / w).coerceIn(0.05f, 0.45f)
                                                val minNormH = (minSpanPx / h).coerceIn(0.05f, 0.45f)

                                                val dx = dragAmount.x / w
                                                val dy = dragAmount.y / h
                                                val ratio = currentRatio

                                                if (handle == CropDragHandle.MOVE) {
                                                    val boxW = currentRight - currentLeft
                                                    val boxH = currentBottom - currentTop
                                                    val nextL = (currentLeft + dx).coerceIn(0f, (1f - boxW).coerceAtLeast(0f))
                                                    val nextT = (currentTop + dy).coerceIn(0f, (1f - boxH).coerceAtLeast(0f))
                                                    cropLeft = nextL
                                                    cropTop = nextT
                                                    cropRight = (nextL + boxW).coerceAtMost(1f)
                                                    cropBottom = (nextT + boxH).coerceAtMost(1f)
                                                } else if (ratio.ratioWidth <= 0f || ratio.ratioHeight <= 0f) {
                                                    // Freeform crop: every corner and edge moves independently
                                                    when (handle) {
                                                        CropDragHandle.TOP_LEFT -> {
                                                            cropLeft = (currentLeft + dx).coerceIn(0f, currentRight - minNormW)
                                                            cropTop = (currentTop + dy).coerceIn(0f, currentBottom - minNormH)
                                                        }
                                                        CropDragHandle.TOP_RIGHT -> {
                                                            cropRight = (currentRight + dx).coerceIn(currentLeft + minNormW, 1f)
                                                            cropTop = (currentTop + dy).coerceIn(0f, currentBottom - minNormH)
                                                        }
                                                        CropDragHandle.BOTTOM_LEFT -> {
                                                            cropLeft = (currentLeft + dx).coerceIn(0f, currentRight - minNormW)
                                                            cropBottom = (currentBottom + dy).coerceIn(currentTop + minNormH, 1f)
                                                        }
                                                        CropDragHandle.BOTTOM_RIGHT -> {
                                                            cropRight = (currentRight + dx).coerceIn(currentLeft + minNormW, 1f)
                                                            cropBottom = (currentBottom + dy).coerceIn(currentTop + minNormH, 1f)
                                                        }
                                                        CropDragHandle.LEFT -> {
                                                            cropLeft = (currentLeft + dx).coerceIn(0f, currentRight - minNormW)
                                                        }
                                                        CropDragHandle.RIGHT -> {
                                                            cropRight = (currentRight + dx).coerceIn(currentLeft + minNormW, 1f)
                                                        }
                                                        CropDragHandle.TOP -> {
                                                            cropTop = (currentTop + dy).coerceIn(0f, currentBottom - minNormH)
                                                        }
                                                        CropDragHandle.BOTTOM -> {
                                                            cropBottom = (currentBottom + dy).coerceIn(currentTop + minNormH, 1f)
                                                        }
                                                        CropDragHandle.MOVE -> Unit
                                                    }
                                                } else {
                                                    // Locked aspect ratio resizing anchored on the opposite corner/center
                                                    val targetRatio = ratio.ratioWidth / ratio.ratioHeight
                                                    val minW = maxOf(minSpanPx, minSpanPx * targetRatio)
                                                    val lPx = currentLeft * w
                                                    val tPx = currentTop * h
                                                    val rPx = currentRight * w
                                                    val bPx = currentBottom * h

                                                    when (handle) {
                                                        CropDragHandle.TOP_LEFT -> {
                                                            val maxW = minOf(rPx, bPx * targetRatio).coerceAtLeast(minW)
                                                            val candW = rPx - (lPx + dragAmount.x)
                                                            val candH = bPx - (tPx + dragAmount.y)
                                                            val desiredW = if (abs(dragAmount.x) >= abs(dragAmount.y) * targetRatio) candW else candH * targetRatio
                                                            val newW = desiredW.coerceIn(minW, maxW)
                                                            val newH = newW / targetRatio
                                                            cropLeft = ((rPx - newW) / w).coerceIn(0f, 1f)
                                                            cropTop = ((bPx - newH) / h).coerceIn(0f, 1f)
                                                        }
                                                        CropDragHandle.TOP_RIGHT -> {
                                                            val maxW = minOf(w - lPx, bPx * targetRatio).coerceAtLeast(minW)
                                                            val candW = (rPx + dragAmount.x) - lPx
                                                            val candH = bPx - (tPx + dragAmount.y)
                                                            val desiredW = if (abs(dragAmount.x) >= abs(dragAmount.y) * targetRatio) candW else candH * targetRatio
                                                            val newW = desiredW.coerceIn(minW, maxW)
                                                            val newH = newW / targetRatio
                                                            cropRight = ((lPx + newW) / w).coerceIn(0f, 1f)
                                                            cropTop = ((bPx - newH) / h).coerceIn(0f, 1f)
                                                        }
                                                        CropDragHandle.BOTTOM_LEFT -> {
                                                            val maxW = minOf(rPx, (h - tPx) * targetRatio).coerceAtLeast(minW)
                                                            val candW = rPx - (lPx + dragAmount.x)
                                                            val candH = (bPx + dragAmount.y) - tPx
                                                            val desiredW = if (abs(dragAmount.x) >= abs(dragAmount.y) * targetRatio) candW else candH * targetRatio
                                                            val newW = desiredW.coerceIn(minW, maxW)
                                                            val newH = newW / targetRatio
                                                            cropLeft = ((rPx - newW) / w).coerceIn(0f, 1f)
                                                            cropBottom = ((tPx + newH) / h).coerceIn(0f, 1f)
                                                        }
                                                        CropDragHandle.BOTTOM_RIGHT -> {
                                                            val maxW = minOf(w - lPx, (h - tPx) * targetRatio).coerceAtLeast(minW)
                                                            val candW = (rPx + dragAmount.x) - lPx
                                                            val candH = (bPx + dragAmount.y) - tPx
                                                            val desiredW = if (abs(dragAmount.x) >= abs(dragAmount.y) * targetRatio) candW else candH * targetRatio
                                                            val newW = desiredW.coerceIn(minW, maxW)
                                                            val newH = newW / targetRatio
                                                            cropRight = ((lPx + newW) / w).coerceIn(0f, 1f)
                                                            cropBottom = ((tPx + newH) / h).coerceIn(0f, 1f)
                                                        }
                                                        CropDragHandle.LEFT -> {
                                                            val centerY = (tPx + bPx) / 2f
                                                            val maxAvailH = minOf(centerY, h - centerY) * 2f
                                                            val maxW = minOf(rPx, maxAvailH * targetRatio).coerceAtLeast(minW)
                                                            val newW = (rPx - (lPx + dragAmount.x)).coerceIn(minW, maxW)
                                                            val newH = newW / targetRatio
                                                            cropLeft = ((rPx - newW) / w).coerceIn(0f, 1f)
                                                            cropTop = ((centerY - newH / 2f) / h).coerceIn(0f, 1f)
                                                            cropBottom = ((centerY + newH / 2f) / h).coerceIn(0f, 1f)
                                                        }
                                                        CropDragHandle.RIGHT -> {
                                                            val centerY = (tPx + bPx) / 2f
                                                            val maxAvailH = minOf(centerY, h - centerY) * 2f
                                                            val maxW = minOf(w - lPx, maxAvailH * targetRatio).coerceAtLeast(minW)
                                                            val newW = ((rPx + dragAmount.x) - lPx).coerceIn(minW, maxW)
                                                            val newH = newW / targetRatio
                                                            cropRight = ((lPx + newW) / w).coerceIn(0f, 1f)
                                                            cropTop = ((centerY - newH / 2f) / h).coerceIn(0f, 1f)
                                                            cropBottom = ((centerY + newH / 2f) / h).coerceIn(0f, 1f)
                                                        }
                                                        CropDragHandle.TOP -> {
                                                            val centerX = (lPx + rPx) / 2f
                                                            val maxAvailW = minOf(centerX, w - centerX) * 2f
                                                            val maxW = minOf(maxAvailW, bPx * targetRatio).coerceAtLeast(minW)
                                                            val candW = (bPx - (tPx + dragAmount.y)) * targetRatio
                                                            val newW = candW.coerceIn(minW, maxW)
                                                            val newH = newW / targetRatio
                                                            cropTop = ((bPx - newH) / h).coerceIn(0f, 1f)
                                                            cropLeft = ((centerX - newW / 2f) / w).coerceIn(0f, 1f)
                                                            cropRight = ((centerX + newW / 2f) / w).coerceIn(0f, 1f)
                                                        }
                                                        CropDragHandle.BOTTOM -> {
                                                            val centerX = (lPx + rPx) / 2f
                                                            val maxAvailW = minOf(centerX, w - centerX) * 2f
                                                            val maxW = minOf(maxAvailW, (h - tPx) * targetRatio).coerceAtLeast(minW)
                                                            val candW = ((bPx + dragAmount.y) - tPx) * targetRatio
                                                            val newW = candW.coerceIn(minW, maxW)
                                                            val newH = newW / targetRatio
                                                            cropBottom = ((tPx + newH) / h).coerceIn(0f, 1f)
                                                            cropLeft = ((centerX - newW / 2f) / w).coerceIn(0f, 1f)
                                                            cropRight = ((centerX + newW / 2f) / w).coerceIn(0f, 1f)
                                                        }
                                                        CropDragHandle.MOVE -> Unit
                                                    }
                                                }
                                            }
                                        )
                                    }
                            ) {
                                val rectLeft = (cropLeft * size.width).coerceIn(0f, size.width)
                                val rectTop = (cropTop * size.height).coerceIn(0f, size.height)
                                val rectRight = (cropRight * size.width).coerceIn(rectLeft + 1f, size.width)
                                val rectBottom = (cropBottom * size.height).coerceIn(rectTop + 1f, size.height)
                                val rectWidth = (rectRight - rectLeft).coerceAtLeast(1f)
                                val rectHeight = (rectBottom - rectTop).coerceAtLeast(1f)
                                val cropRect = Rect(Offset(rectLeft, rectTop), Size(rectWidth, rectHeight))

                                // 1. Dimmed mask outside crop area
                                if (rectTop > 0f) {
                                    drawRect(
                                        color = Color.Black.copy(alpha = 0.65f),
                                        topLeft = Offset(0f, 0f),
                                        size = Size(size.width, rectTop)
                                    )
                                }
                                if (rectBottom < size.height) {
                                    drawRect(
                                        color = Color.Black.copy(alpha = 0.65f),
                                        topLeft = Offset(0f, rectBottom),
                                        size = Size(size.width, size.height - rectBottom)
                                    )
                                }
                                if (rectLeft > 0f) {
                                    drawRect(
                                        color = Color.Black.copy(alpha = 0.65f),
                                        topLeft = Offset(0f, rectTop),
                                        size = Size(rectLeft, rectHeight)
                                    )
                                }
                                if (rectRight < size.width) {
                                    drawRect(
                                        color = Color.Black.copy(alpha = 0.65f),
                                        topLeft = Offset(rectRight, rectTop),
                                        size = Size(size.width - rectRight, rectHeight)
                                    )
                                }

                                // 2. Crisp White Border
                                drawRect(
                                    color = Color.White,
                                    topLeft = cropRect.topLeft,
                                    size = cropRect.size,
                                    style = Stroke(width = 1.8.dp.toPx())
                                )

                                // 3. Rule-of-Thirds Grid Lines
                                val oneThirdX = rectWidth / 3f
                                val oneThirdY = rectHeight / 3f
                                val gridColor = Color.White.copy(alpha = 0.35f)
                                val gridStrokeWidth = 1.dp.toPx()

                                drawLine(gridColor, Offset(rectLeft + oneThirdX, rectTop), Offset(rectLeft + oneThirdX, rectBottom), gridStrokeWidth)
                                drawLine(gridColor, Offset(rectLeft + oneThirdX * 2f, rectTop), Offset(rectLeft + oneThirdX * 2f, rectBottom), gridStrokeWidth)
                                drawLine(gridColor, Offset(rectLeft, rectTop + oneThirdY), Offset(rectRight, rectTop + oneThirdY), gridStrokeWidth)
                                drawLine(gridColor, Offset(rectLeft, rectTop + oneThirdY * 2f), Offset(rectRight, rectTop + oneThirdY * 2f), gridStrokeWidth)

                                // 4. Dynamic Corner L-Brackets + Touch Handle Knobs
                                val cornerLen = minOf(22.dp.toPx(), rectWidth * 0.35f, rectHeight * 0.35f)
                                val cornerStrokeWidth = 4.dp.toPx()
                                val accent = Color(0xFFFF9800)

                                // Top-Left
                                drawLine(accent, Offset(rectLeft - 1.dp.toPx(), rectTop), Offset(rectLeft + cornerLen, rectTop), cornerStrokeWidth)
                                drawLine(accent, Offset(rectLeft, rectTop - 1.dp.toPx()), Offset(rectLeft, rectTop + cornerLen), cornerStrokeWidth)
                                // Top-Right
                                drawLine(accent, Offset(rectRight - cornerLen, rectTop), Offset(rectRight + 1.dp.toPx(), rectTop), cornerStrokeWidth)
                                drawLine(accent, Offset(rectRight, rectTop - 1.dp.toPx()), Offset(rectRight, rectTop + cornerLen), cornerStrokeWidth)
                                // Bottom-Left
                                drawLine(accent, Offset(rectLeft - 1.dp.toPx(), rectBottom), Offset(rectLeft + cornerLen, rectBottom), cornerStrokeWidth)
                                drawLine(accent, Offset(rectLeft, rectBottom - cornerLen), Offset(rectLeft, rectBottom + 1.dp.toPx()), cornerStrokeWidth)
                                // Bottom-Right
                                drawLine(accent, Offset(rectRight - cornerLen, rectBottom), Offset(rectRight + 1.dp.toPx(), rectBottom), cornerStrokeWidth)
                                drawLine(accent, Offset(rectRight, rectBottom - cornerLen), Offset(rectRight, rectBottom + 1.dp.toPx()), cornerStrokeWidth)

                                val cornerPositions = listOf(
                                    CropDragHandle.TOP_LEFT to Offset(rectLeft, rectTop),
                                    CropDragHandle.TOP_RIGHT to Offset(rectRight, rectTop),
                                    CropDragHandle.BOTTOM_LEFT to Offset(rectLeft, rectBottom),
                                    CropDragHandle.BOTTOM_RIGHT to Offset(rectRight, rectBottom)
                                )
                                cornerPositions.forEach { (hnd, center) ->
                                    val isDraggingThis = activeHandle == hnd
                                    drawCircle(
                                        color = if (isDraggingThis) Color.White else accent,
                                        radius = if (isDraggingThis) 8.dp.toPx() else 6.dp.toPx(),
                                        center = center
                                    )
                                    drawCircle(
                                        color = Color.Black.copy(alpha = 0.6f),
                                        radius = if (isDraggingThis) 8.dp.toPx() else 6.dp.toPx(),
                                        center = center,
                                        style = Stroke(width = 1.5.dp.toPx())
                                    )
                                }

                                // Mid-edge grab bars
                                val midBarLen = minOf(18.dp.toPx(), rectWidth * 0.25f, rectHeight * 0.25f)
                                val centerX = (rectLeft + rectRight) / 2f
                                val centerY = (rectTop + rectBottom) / 2f
                                drawLine(Color.White, Offset(centerX - midBarLen / 2f, rectTop), Offset(centerX + midBarLen / 2f, rectTop), 3.dp.toPx())
                                drawLine(Color.White, Offset(centerX - midBarLen / 2f, rectBottom), Offset(centerX + midBarLen / 2f, rectBottom), 3.dp.toPx())
                                drawLine(Color.White, Offset(rectLeft, centerY - midBarLen / 2f), Offset(rectLeft, centerY + midBarLen / 2f), 3.dp.toPx())
                                drawLine(Color.White, Offset(rectRight, centerY - midBarLen / 2f), Offset(rectRight, centerY + midBarLen / 2f), 3.dp.toPx())
                            }
                        }
                    }
                }

                // Bottom Controls: Aspect Ratio Presets
                AnimatedVisibility(visible = isCropMode) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding(),
                        color = Color(0xFF141416)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 8.dp)
                        ) {
                            Text(
                                text = "Arraste qualquer canto, borda ou o centro para moldar o corte:",
                                color = Color.White.copy(alpha = 0.78f),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CropAspectRatios.forEach { ratio ->
                                    val isSelected = selectedRatio.id == ratio.id

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color(0xFF222226),
                                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { updateCropBoxForRatio(ratio) }
                                            .testTag("crop_ratio_${ratio.id}")
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(width = ratio.iconWidthDp.dp, height = ratio.iconHeightDp.dp)
                                                    .border(
                                                        1.5.dp,
                                                        if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.65f),
                                                        RoundedCornerShape(3.dp)
                                                    )
                                                    .background(
                                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else Color.Transparent
                                                    )
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = ratio.label,
                                                fontSize = 11.5.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White
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
