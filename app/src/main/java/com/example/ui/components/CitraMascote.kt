package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.min

enum class MascotHumor {
    NEUTRO,
    FELIZ,
    DISCRETO,
    NAO,
    SONOLENTO,
    SURPRESO
}

private val LaranjaCitra = Color(0xFFFF8B1F)
private val Pessego = Color(0xFFFFB866)
private val FolhaVerde = Color(0xFF3FA34D)
private val FolhaVeia = Color(0xFF2F8A3E)
private val BrancoOlho = Color(0xFFFFFFFF)
private val Tinta = Color(0xFF2B1A12)
private val Bochecha = Color(0xFFE8451F)

@Composable
fun CitraMascote(
    humor: MascotHumor,
    olharX: Float,
    olharY: Float,
    modifier: Modifier = Modifier,
    tamanho: Dp = 120.dp,
    folha: Boolean = true,
    piscar: Boolean = true,
    nodTrigger: Int = 0,
    shakeTrigger: Int = 0,
    jumpTrigger: Int = 0
) {
    val density = LocalDensity.current

    // Smooth gaze interpolation (140ms)
    val smoothOlharX by animateFloatAsState(
        targetValue = olharX.coerceIn(-1f, 1f),
        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
        label = "mascotOlharX"
    )
    val smoothOlharY by animateFloatAsState(
        targetValue = olharY.coerceIn(-1f, 1f),
        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
        label = "mascotOlharY"
    )

    // Continuous 3.2s double-blink cycle & leaf sway
    val infiniteTransition = rememberInfiniteTransition(label = "mascotIdle")
    val blinkScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 3200
                1f at 0 using LinearEasing
                1f at 1216 using LinearEasing       // 38%
                0.08f at 1312 using FastOutSlowInEasing // 41%
                1f at 1408 using FastOutSlowInEasing    // 44%
                0.08f at 1536 using FastOutSlowInEasing // 48%
                1f at 1664 using FastOutSlowInEasing    // 52%
                1f at 3200 using LinearEasing
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "mascotBlink"
    )

    val leafSway by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mascotLeafSway"
    )

    // Micro-animations
    val nodOffsetYPx = remember { Animatable(0f) }
    val nodRotation = remember { Animatable(0f) }
    val nodScaleY = remember { Animatable(1f) }

    val shakeOffsetXPx = remember { Animatable(0f) }
    val shakeRotation = remember { Animatable(0f) }

    val jumpOffsetYPx = remember { Animatable(0f) }
    val jumpScaleX = remember { Animatable(1f) }
    val jumpScaleY = remember { Animatable(1f) }

    // Nod reaction (420ms)
    LaunchedEffect(nodTrigger) {
        if (nodTrigger > 0) {
            val dropPx = with(density) { 7.dp.toPx() }
            launch {
                nodOffsetYPx.animateTo(
                    targetValue = 0f,
                    animationSpec = keyframes {
                        durationMillis = 420
                        0f at 0
                        dropPx at 160 using FastOutSlowInEasing
                        0f at 420 using FastOutSlowInEasing
                    }
                )
            }
            launch {
                nodRotation.animateTo(
                    targetValue = 0f,
                    animationSpec = keyframes {
                        durationMillis = 420
                        0f at 0
                        3f at 160 using FastOutSlowInEasing
                        0f at 420 using FastOutSlowInEasing
                    }
                )
            }
            launch {
                nodScaleY.animateTo(
                    targetValue = 1f,
                    animationSpec = keyframes {
                        durationMillis = 420
                        1f at 0
                        0.96f at 160 using FastOutSlowInEasing
                        1f at 420 using FastOutSlowInEasing
                    }
                )
            }
        }
    }

    // Shake reaction (550ms)
    LaunchedEffect(shakeTrigger) {
        if (shakeTrigger > 0) {
            val maxShiftPx = with(density) { 9.dp.toPx() }
            launch {
                shakeOffsetXPx.animateTo(
                    targetValue = 0f,
                    animationSpec = keyframes {
                        durationMillis = 550
                        0f at 0
                        -maxShiftPx at 85 using FastOutSlowInEasing
                        maxShiftPx at 180 using FastOutSlowInEasing
                        -maxShiftPx * 0.7f at 275 using FastOutSlowInEasing
                        maxShiftPx * 0.6f at 370 using FastOutSlowInEasing
                        -maxShiftPx * 0.3f at 460 using FastOutSlowInEasing
                        0f at 550 using FastOutSlowInEasing
                    }
                )
            }
            launch {
                shakeRotation.animateTo(
                    targetValue = 0f,
                    animationSpec = keyframes {
                        durationMillis = 550
                        0f at 0
                        -6f at 85 using FastOutSlowInEasing
                        6f at 180 using FastOutSlowInEasing
                        -4.5f at 275 using FastOutSlowInEasing
                        3.5f at 370 using FastOutSlowInEasing
                        -1.5f at 460 using FastOutSlowInEasing
                        0f at 550 using FastOutSlowInEasing
                    }
                )
            }
        }
    }

    // Jump reaction (450ms)
    LaunchedEffect(jumpTrigger) {
        if (jumpTrigger > 0) {
            val jumpHeightPx = with(density) { 26.dp.toPx() }
            launch {
                jumpOffsetYPx.animateTo(
                    targetValue = 0f,
                    animationSpec = keyframes {
                        durationMillis = 450
                        0f at 0
                        0f at 70
                        -jumpHeightPx at 220 using FastOutSlowInEasing
                        0f at 380 using FastOutSlowInEasing
                        0f at 450
                    }
                )
            }
            launch {
                jumpScaleX.animateTo(
                    targetValue = 1f,
                    animationSpec = keyframes {
                        durationMillis = 450
                        1f at 0
                        1.08f at 70 using FastOutSlowInEasing
                        0.94f at 220 using FastOutSlowInEasing
                        1.05f at 380 using FastOutSlowInEasing
                        1f at 450 using FastOutSlowInEasing
                    }
                )
            }
            launch {
                jumpScaleY.animateTo(
                    targetValue = 1f,
                    animationSpec = keyframes {
                        durationMillis = 450
                        1f at 0
                        0.88f at 70 using FastOutSlowInEasing
                        1.08f at 220 using FastOutSlowInEasing
                        0.94f at 380 using FastOutSlowInEasing
                        1f at 450 using FastOutSlowInEasing
                    }
                )
            }
        }
    }

    // Pre-allocated vector paths (zero per-frame allocations)
    val leafStemPath = remember {
        Path().apply {
            moveTo(100f, 24f)
            quadraticTo(101f, 12f, 108f, 4f)
        }
    }
    val leafBladePath = remember {
        Path().apply {
            moveTo(103f, 16f)
            cubicTo(102f, -2f, 126f, -8f, 134f, 4f)
            cubicTo(126f, 18f, 108f, 20f, 103f, 16f)
            close()
        }
    }
    val leafVeinPath = remember {
        Path().apply {
            moveTo(104f, 15f)
            quadraticTo(116f, 7f, 128f, 4f)
        }
    }
    val happyLeftEyePath = remember {
        Path().apply {
            moveTo(58f, 104f)
            quadraticTo(74f, 84f, 90f, 104f)
        }
    }
    val happyRightEyePath = remember {
        Path().apply {
            moveTo(110f, 104f)
            quadraticTo(126f, 84f, 142f, 104f)
        }
    }

    val happyStroke = remember {
        Stroke(width = 6f, cap = StrokeCap.Round)
    }
    val stemStroke = remember {
        Stroke(width = 3.5f, cap = StrokeCap.Round)
    }
    val veinStroke = remember {
        Stroke(width = 1.8f, cap = StrokeCap.Round)
    }

    val heightDp = tamanho * (212f / 200f)

    Canvas(
        modifier = modifier
            .size(width = tamanho, height = heightDp)
            .semantics {
                contentDescription = "Citra, o mascote do aplicativo"
            }
            .graphicsLayer {
                translationX = shakeOffsetXPx.value
                translationY = nodOffsetYPx.value + jumpOffsetYPx.value
                rotationZ = nodRotation.value + shakeRotation.value
                scaleX = jumpScaleX.value
                scaleY = nodScaleY.value * jumpScaleY.value
            }
    ) {
        val scaleFactor = min(size.width / 200f, size.height / 212f)
        val offsetX = (size.width - 200f * scaleFactor) / 2f
        val offsetY = (size.height - 212f * scaleFactor) / 2f

        translate(left = offsetX, top = offsetY) {
            scale(scaleX = scaleFactor, scaleY = scaleFactor, pivot = Offset.Zero) {
                // Map viewBox="0 -12 200 212" into 0..200 x 0..212
                translate(left = 0f, top = 12f) {
                    // 1. Body
                    drawCircle(
                        color = LaranjaCitra,
                        radius = 80f,
                        center = Offset(100f, 100f)
                    )

                    // 2. Highlight
                    rotate(degrees = -35f, pivot = Offset(64f, 52f)) {
                        drawOval(
                            color = Pessego.copy(alpha = 0.75f),
                            topLeft = Offset(64f - 17f, 52f - 8f),
                            size = Size(34f, 16f)
                        )
                    }

                    // 3. Leaf
                    if (folha) {
                        val currentLeafAngle = if (humor == MascotHumor.FELIZ) leafSway else 0f
                        rotate(degrees = currentLeafAngle, pivot = Offset(100f, 22f)) {
                            drawPath(
                                path = leafStemPath,
                                color = FolhaVeia,
                                style = stemStroke
                            )
                            drawPath(
                                path = leafBladePath,
                                color = FolhaVerde
                            )
                            drawPath(
                                path = leafVeinPath,
                                color = FolhaVeia,
                                style = veinStroke
                            )
                        }
                    }

                    // 4. Eyes & Expressions
                    val eyeGroupOffsetX = smoothOlharX * 5f
                    val eyeGroupOffsetY = smoothOlharY * 4f

                    translate(left = eyeGroupOffsetX, top = eyeGroupOffsetY) {
                        if (humor == MascotHumor.FELIZ) {
                            // Rosy cheeks
                            drawOval(
                                color = Bochecha.copy(alpha = 0.45f),
                                topLeft = Offset(48f - 10f, 120f - 6f),
                                size = Size(20f, 12f)
                            )
                            drawOval(
                                color = Bochecha.copy(alpha = 0.45f),
                                topLeft = Offset(152f - 10f, 120f - 6f),
                                size = Size(20f, 12f)
                            )

                            // Happy curved arcs
                            drawPath(
                                path = happyLeftEyePath,
                                color = Tinta,
                                style = happyStroke
                            )
                            drawPath(
                                path = happyRightEyePath,
                                color = Tinta,
                                style = happyStroke
                            )
                        } else {
                            val baseEyelidScale = when (humor) {
                                MascotHumor.NEUTRO -> 1.0f
                                MascotHumor.DISCRETO -> 0.6f
                                MascotHumor.NAO -> 0.5f
                                MascotHumor.SONOLENTO -> 0.2f
                                MascotHumor.SURPRESO -> 1.14f
                                MascotHumor.FELIZ -> 1.0f
                            }

                            val activeBlink = if (piscar && humor != MascotHumor.SONOLENTO) {
                                blinkScale
                            } else {
                                1f
                            }
                            val effectiveEyelidScale = baseEyelidScale * activeBlink
                            val pupilScale = if (humor == MascotHumor.SURPRESO) 0.75f else 1.0f

                            val pupilDx = smoothOlharX * 6f
                            val pupilDy = smoothOlharY * 7f

                            // Left eye (74, 98) and Right eye (126, 98)
                            drawMascotEye(
                                cx = 74f,
                                cy = 98f,
                                eyelidScaleY = effectiveEyelidScale,
                                pupilDx = pupilDx,
                                pupilDy = pupilDy,
                                pupilScale = pupilScale
                            )
                            drawMascotEye(
                                cx = 126f,
                                cy = 98f,
                                eyelidScaleY = effectiveEyelidScale,
                                pupilDx = pupilDx,
                                pupilDy = pupilDy,
                                pupilScale = pupilScale
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMascotEye(
    cx: Float,
    cy: Float,
    eyelidScaleY: Float,
    pupilDx: Float,
    pupilDy: Float,
    pupilScale: Float
) {
    scale(scaleX = 1f, scaleY = eyelidScaleY, pivot = Offset(cx, cy)) {
        // Sclera
        drawOval(
            color = BrancoOlho,
            topLeft = Offset(cx - 17f, cy - 21f),
            size = Size(34f, 42f)
        )
        // Pupil
        val pupilCenter = Offset(cx + pupilDx, cy + pupilDy)
        drawCircle(
            color = Tinta,
            radius = 9f * pupilScale,
            center = pupilCenter
        )
        // Specular highlight dot
        drawCircle(
            color = BrancoOlho,
            radius = 3f * pupilScale,
            center = Offset(
                pupilCenter.x + 3.5f * pupilScale,
                pupilCenter.y - 4.5f * pupilScale
            )
        )
    }
}
