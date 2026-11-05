package com.example.ui.tutorial

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

private val OrangeBadgeColor = Color(0xFFFF9800)
private val DarkScrimColor = Color(0xBF000000)

@Composable
fun CitraSpotlightOverlay(
    activeTutorial: ActiveTutorialState,
    onNextStep: () -> Unit,
    onSkipArea: () -> Unit,
    onSkipAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentStep = activeTutorial.currentStep ?: return
    val density = LocalDensity.current

    // Handle back button to skip current area tutorial
    BackHandler {
        onSkipArea()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .zIndex(999f)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // Consume all outside clicks so user interacts strictly through the tutorial buttons
            }
    ) {
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }

        // Look up target bounds or provide intelligent fallback based on screen center
        val measuredBounds = TutorialTargetRegistry.getBounds(currentStep.targetKey)
        val targetRect = measuredBounds ?: Rect(
            left = screenWidthPx * 0.1f,
            top = screenHeightPx * 0.35f,
            right = screenWidthPx * 0.9f,
            bottom = screenHeightPx * 0.48f
        )

        val paddingPx = with(density) { 8.dp.toPx() }
        val animSpec = tween<Float>(durationMillis = 350, easing = FastOutSlowInEasing)

        // Smooth gliding animation for the illuminated spotlight cutout
        val animLeft by animateFloatAsState(targetValue = targetRect.left - paddingPx, animationSpec = animSpec, label = "spot_left")
        val animTop by animateFloatAsState(targetValue = targetRect.top - paddingPx, animationSpec = animSpec, label = "spot_top")
        val animRight by animateFloatAsState(targetValue = targetRect.right + paddingPx, animationSpec = animSpec, label = "spot_right")
        val animBottom by animateFloatAsState(targetValue = targetRect.bottom + paddingPx, animationSpec = animSpec, label = "spot_bottom")

        val spotWidth = (animRight - animLeft).coerceAtLeast(10f)
        val spotHeight = (animBottom - animTop).coerceAtLeast(10f)

        // 1. Fullscreen Darkened Scrim with Rounded Rectangle Spotlight Cutout
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        ) {
            // Draw dark background
            drawRect(DarkScrimColor)

            val cornerRadiusPx = 16.dp.toPx()

            // Punch hole for the highlighted element
            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(animLeft, animTop),
                size = Size(spotWidth, spotHeight),
                cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                blendMode = BlendMode.Clear
            )

            // Radiant orange outline around the highlighted hole
            drawRoundRect(
                color = OrangeBadgeColor,
                topLeft = Offset(animLeft, animTop),
                size = Size(spotWidth, spotHeight),
                cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                style = Stroke(width = 2.5.dp.toPx())
            )
        }

        // Determine if dialog should be positioned above or below the cutout
        val isTargetInBottomHalf = animTop > (screenHeightPx * 0.45f)
        val balloonVerticalOffset = if (isTargetInBottomHalf) {
            // Position above the spotlight cutout
            (animTop - with(density) { 240.dp.toPx() }).coerceAtLeast(with(density) { 56.dp.toPx() })
        } else {
            // Position below the spotlight cutout
            (animBottom + with(density) { 16.dp.toPx() }).coerceAtMost(screenHeightPx - with(density) { 240.dp.toPx() })
        }

        val animBalloonY by animateFloatAsState(
            targetValue = balloonVerticalOffset,
            animationSpec = animSpec,
            label = "balloon_y"
        )

        // 2. The Citra Thematic Dialogue Balloon
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .offset(y = with(density) { animBalloonY.toDp() })
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 14.dp,
                border = BorderStroke(1.5.dp, OrangeBadgeColor.copy(alpha = 0.65f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp)
                ) {
                    // Header: Orange Badge + Counter + Area Tag
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = OrangeBadgeColor.copy(alpha = 0.16f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🍊 Citra Guia",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = OrangeBadgeColor
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "• ${activeTutorial.area.title}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Step counter badge (e.g. 1 de 4)
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "${activeTutorial.stepIndex + 1} de ${activeTutorial.totalSteps}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Title of the current step
                    Text(
                        text = currentStep.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Explanation (unbounded length, clear step-by-step)
                    Text(
                        text = currentStep.text,
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 13.5.sp,
                        lineHeight = 19.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Bottom Action Row: [Pular Tudo] - [Pular] - [Próximo / Concluir]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Pular Tudo (dismisses all tutorials permanently)
                        TextButton(
                            onClick = onSkipAll,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Pular Tudo",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // 2. Pular (skips only this area)
                        OutlinedButton(
                            onClick = onSkipArea,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Pular",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // 3. Próximo (or Concluir on last step)
                        Button(
                            onClick = onNextStep,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = OrangeBadgeColor),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (activeTutorial.isLastStep) "Concluir" else "Próximo",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = if (activeTutorial.isLastStep) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
