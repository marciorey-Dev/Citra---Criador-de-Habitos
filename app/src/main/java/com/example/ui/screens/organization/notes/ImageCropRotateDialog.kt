package com.example.ui.screens.organization.notes

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.util.MediaStorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

@Composable
fun ImageFullScreenCropRotateDialog(
    imageUrl: String,
    onDismiss: () -> Unit,
    onSaveImage: (newPath: String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var rotationDegrees by remember { mutableIntStateOf(0) }
    var isCropMode by remember { mutableStateOf(false) }
    var selectedRatio by remember { mutableStateOf(CropAspectRatios[0]) } // Free/Original
    var isProcessing by remember { mutableStateOf(false) }

    // Normalized crop box state within the container (0f..1f range)
    var cropNormalizedLeft by remember { mutableFloatStateOf(0.1f) }
    var cropNormalizedTop by remember { mutableFloatStateOf(0.1f) }
    var cropNormalizedWidth by remember { mutableFloatStateOf(0.8f) }
    var cropNormalizedHeight by remember { mutableFloatStateOf(0.8f) }

    // Raw loaded bitmap for cropping calculations
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(imageUrl) {
        withContext(Dispatchers.IO) {
            sourceBitmap = MediaStorageHelper.loadBitmap(context, imageUrl)
        }
    }

    // Adjust crop box dimensions when aspect ratio preset changes
    fun updateCropBoxForRatio(ratio: CropAspectRatio) {
        selectedRatio = ratio
        if (ratio.ratioWidth <= 0f || ratio.ratioHeight <= 0f) {
            cropNormalizedLeft = 0.05f
            cropNormalizedTop = 0.05f
            cropNormalizedWidth = 0.90f
            cropNormalizedHeight = 0.90f
            return
        }

        val targetRatio = ratio.ratioWidth / ratio.ratioHeight
        if (targetRatio >= 1f) {
            // Wider
            cropNormalizedWidth = 0.85f
            cropNormalizedHeight = (cropNormalizedWidth / targetRatio).coerceIn(0.1f, 0.9f)
        } else {
            // Taller
            cropNormalizedHeight = 0.85f
            cropNormalizedWidth = (cropNormalizedHeight * targetRatio).coerceIn(0.1f, 0.9f)
        }
        cropNormalizedLeft = ((1f - cropNormalizedWidth) / 2f).coerceAtLeast(0f)
        cropNormalizedTop = ((1f - cropNormalizedHeight) / 2f).coerceAtLeast(0f)
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
                                rotationDegrees = (rotationDegrees + 90) % 360
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

                        // Crop Mode Toggle
                        IconButton(
                            onClick = {
                                isCropMode = !isCropMode
                                if (isCropMode && selectedRatio.id == "FREE") {
                                    updateCropBoxForRatio(CropAspectRatios[1]) // Default to 1:1 when entering crop
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

                        // Save button (active if rotated or in crop mode)
                        Button(
                            onClick = {
                                val bmp = sourceBitmap ?: return@Button
                                isProcessing = true
                                coroutineScope.launch {
                                    withContext(Dispatchers.IO) {
                                        try {
                                            // 1. Rotate if needed
                                            val rotated = if (rotationDegrees != 0) {
                                                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                                                Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
                                            } else {
                                                bmp
                                            }

                                            // 2. Crop if in crop mode
                                            val finalBitmap = if (isCropMode) {
                                                val cropX = (cropNormalizedLeft * rotated.width).roundToInt().coerceIn(0, rotated.width - 1)
                                                val cropY = (cropNormalizedTop * rotated.height).roundToInt().coerceIn(0, rotated.height - 1)
                                                val cropW = (cropNormalizedWidth * rotated.width).roundToInt().coerceIn(1, rotated.width - cropX)
                                                val cropH = (cropNormalizedHeight * rotated.height).roundToInt().coerceIn(1, rotated.height - cropY)
                                                Bitmap.createBitmap(rotated, cropX, cropY, cropW, cropH)
                                            } else {
                                                rotated
                                            }

                                            // 3. Save into Citra Pic folder
                                            val newPath = MediaStorageHelper.saveNoteImageBitmap(context, finalBitmap)
                                            withContext(Dispatchers.Main) {
                                                isProcessing = false
                                                if (newPath != null) {
                                                    onSaveImage(newPath)
                                                }
                                                onDismiss()
                                            }
                                        } catch (e: Exception) {
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

                // Main Interactive Image Area with Draggable Crop Box
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val containerWidthPx = constraints.maxWidth.toFloat()
                    val containerHeightPx = constraints.maxHeight.toFloat()

                    // Render rotated base image
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = "Visualização da Imagem",
                        modifier = Modifier
                            .fillMaxSize()
                            .rotate(rotationDegrees.toFloat()),
                        contentScale = ContentScale.Fit
                    )

                    // Interactive Draggable Crop Overlay
                    if (isCropMode) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(containerWidthPx, containerHeightPx, cropNormalizedWidth, cropNormalizedHeight) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val deltaXNorm = dragAmount.x / containerWidthPx
                                        val deltaYNorm = dragAmount.y / containerHeightPx

                                        val newLeft = (cropNormalizedLeft + deltaXNorm)
                                            .coerceIn(0f, 1f - cropNormalizedWidth)
                                        val newTop = (cropNormalizedTop + deltaYNorm)
                                            .coerceIn(0f, 1f - cropNormalizedHeight)

                                        cropNormalizedLeft = newLeft
                                        cropNormalizedTop = newTop
                                    }
                                }
                        ) {
                            val rectLeft = cropNormalizedLeft * size.width
                            val rectTop = cropNormalizedTop * size.height
                            val rectWidth = cropNormalizedWidth * size.width
                            val rectHeight = cropNormalizedHeight * size.height
                            val cropRect = Rect(Offset(rectLeft, rectTop), Size(rectWidth, rectHeight))

                            // 1. Dimmed semi-transparent dark mask outside crop area
                            // Top
                            drawRect(
                                color = Color.Black.copy(alpha = 0.65f),
                                topLeft = Offset(0f, 0f),
                                size = Size(size.width, rectTop)
                            )
                            // Bottom
                            drawRect(
                                color = Color.Black.copy(alpha = 0.65f),
                                topLeft = Offset(0f, rectTop + rectHeight),
                                size = Size(size.width, size.height - (rectTop + rectHeight))
                            )
                            // Left
                            drawRect(
                                color = Color.Black.copy(alpha = 0.65f),
                                topLeft = Offset(0f, rectTop),
                                size = Size(rectLeft, rectHeight)
                            )
                            // Right
                            drawRect(
                                color = Color.Black.copy(alpha = 0.65f),
                                topLeft = Offset(rectLeft + rectWidth, rectTop),
                                size = Size(size.width - (rectLeft + rectWidth), rectHeight)
                            )

                            // 2. Crisp White Border around crop window
                            drawRect(
                                color = Color.White,
                                topLeft = cropRect.topLeft,
                                size = cropRect.size,
                                style = Stroke(width = 2.dp.toPx())
                            )

                            // 3. Rule-of-Thirds Grid Lines
                            val oneThirdX = rectWidth / 3f
                            val oneThirdY = rectHeight / 3f
                            val gridColor = Color.White.copy(alpha = 0.35f)
                            val gridStroke = Stroke(width = 1.dp.toPx())

                            // Vertical grid lines
                            drawLine(gridColor, Offset(rectLeft + oneThirdX, rectTop), Offset(rectLeft + oneThirdX, rectTop + rectHeight), gridStroke.width)
                            drawLine(gridColor, Offset(rectLeft + oneThirdX * 2f, rectTop), Offset(rectLeft + oneThirdX * 2f, rectTop + rectHeight), gridStroke.width)
                            // Horizontal grid lines
                            drawLine(gridColor, Offset(rectLeft, rectTop + oneThirdY), Offset(rectLeft + rectWidth, rectTop + oneThirdY), gridStroke.width)
                            drawLine(gridColor, Offset(rectLeft, rectTop + oneThirdY * 2f), Offset(rectLeft + rectWidth, rectTop + oneThirdY * 2f), gridStroke.width)

                            // Corner guides for dragging feedback
                            val cornerLen = 18.dp.toPx()
                            val cornerStroke = Stroke(width = 3.5.dp.toPx())
                            val accent = Color(0xFFFF9800)

                            // Top-Left
                            drawLine(accent, Offset(rectLeft - 1.dp.toPx(), rectTop), Offset(rectLeft + cornerLen, rectTop), cornerStroke.width)
                            drawLine(accent, Offset(rectLeft, rectTop - 1.dp.toPx()), Offset(rectLeft, rectTop + cornerLen), cornerStroke.width)

                            // Top-Right
                            drawLine(accent, Offset(rectLeft + rectWidth - cornerLen, rectTop), Offset(rectLeft + rectWidth + 1.dp.toPx(), rectTop), cornerStroke.width)
                            drawLine(accent, Offset(rectLeft + rectWidth, rectTop - 1.dp.toPx()), Offset(rectLeft + rectWidth, rectTop + cornerLen), cornerStroke.width)

                            // Bottom-Left
                            drawLine(accent, Offset(rectLeft - 1.dp.toPx(), rectTop + rectHeight), Offset(rectLeft + cornerLen, rectTop + rectHeight), cornerStroke.width)
                            drawLine(accent, Offset(rectLeft, rectTop + rectHeight - cornerLen), Offset(rectLeft, rectTop + rectHeight + 1.dp.toPx()), cornerStroke.width)

                            // Bottom-Right
                            drawLine(accent, Offset(rectLeft + rectWidth - cornerLen, rectTop + rectHeight), Offset(rectLeft + rectWidth + 1.dp.toPx(), rectTop + rectHeight), cornerStroke.width)
                            drawLine(accent, Offset(rectLeft + rectWidth, rectTop + rectHeight - cornerLen), Offset(rectLeft + rectWidth, rectTop + rectHeight + 1.dp.toPx()), cornerStroke.width)
                        }
                    }
                }

                // Bottom Controls: Aspect Ratio Presets with Miniature Visual Icons
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
                                text = "Arraste a área para posicionar o corte • Escolha uma proporção:",
                                color = Color.White.copy(alpha = 0.75f),
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
                                            // Visual Miniature Box Preview representing the aspect ratio
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
