package com.example.ui.screens.timer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun VaultDialSelector(
    label: String,
    selectedValue: Int,
    values: List<Int>,
    unitLabel: String = "min",
    accentColor: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
    onValueChange: (Int) -> Unit
) {
    val currentIndex = remember(selectedValue, values) {
        val idx = values.indexOf(selectedValue)
        if (idx >= 0) idx else 0
    }

    var dragOffset by remember { mutableStateOf(0f) }
    val stepPx = 40f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // Label & Selected Value Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = accentColor.copy(alpha = 0.15f)
            ) {
                Text(
                    text = "$selectedValue $unitLabel",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Vault Rotary Dial Roller
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF1E293B),
                            Color(0xFF334155),
                            Color(0xFF1E293B)
                        )
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
                .pointerInput(enabled, values, currentIndex) {
                    if (!enabled) return@pointerInput
                    detectDragGestures(
                        onDragEnd = {
                            dragOffset = 0f
                        },
                        onDragCancel = {
                            dragOffset = 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragOffset += dragAmount.x
                            val stepsChanged = (dragOffset / stepPx).toInt()
                            if (stepsChanged != 0) {
                                val newIndex = (currentIndex - stepsChanged).coerceIn(0, values.size - 1)
                                if (newIndex != currentIndex) {
                                    onValueChange(values[newIndex])
                                }
                                dragOffset %= stepPx
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val centerX = w / 2f
                val pinSpacing = 28.dp.toPx()

                // Draw rotating tick pins to left and right of center
                val visibleRange = 10
                for (offset in -visibleRange..visibleRange) {
                    val targetIdx = currentIndex + offset
                    val pinX = centerX + (offset * pinSpacing) + dragOffset

                    if (pinX in 0f..w) {
                        // Distance from center determines alpha and height (3D cylinder vault effect)
                        val distFromCenter = kotlin.math.abs(pinX - centerX) / (w / 2f)
                        val alpha = (1f - distFromCenter).coerceIn(0.1f, 1f)

                        val isMajor = targetIdx in values.indices
                        val pinHeight = if (isMajor) (h * 0.48f * alpha) else (h * 0.28f * alpha)
                        val strokeWidth = if (isMajor) 2.5.dp.toPx() else 1.5.dp.toPx()
                        val pinColor = if (isMajor) Color(0xFFF8FAFC).copy(alpha = alpha) else Color(0xFF94A3B8).copy(alpha = alpha * 0.7f)

                        val startY = (h - pinHeight) / 2f
                        val endY = startY + pinHeight

                        drawLine(
                            color = pinColor,
                            start = Offset(pinX, startY),
                            end = Offset(pinX, endY),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round
                        )
                    }
                }

                // Fixed Central Precision Needle (Agulha de Cofre)
                val needleColor = accentColor
                drawLine(
                    color = needleColor,
                    start = Offset(centerX, 6f),
                    end = Offset(centerX, h - 6f),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Top & Bottom Indicator Triangles
                drawCircle(
                    color = needleColor,
                    radius = 3.5.dp.toPx(),
                    center = Offset(centerX, 6f)
                )
                drawCircle(
                    color = needleColor,
                    radius = 3.5.dp.toPx(),
                    center = Offset(centerX, h - 6f)
                )
            }
        }
    }
}
