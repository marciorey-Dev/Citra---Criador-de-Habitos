package com.example.util

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * High-performance Squash & Stretch (Compress & Elastic Bounce) modifier with tactile haptic feedback.
 * Compresses slightly (to [pressedScale]) when the finger touches down,
 * and bounces back with authentic spring physics when released.
 */
fun Modifier.squashBounce(
    pressedScale: Float = 0.93f,
    enableHaptic: Boolean = true
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val hapticFeedback = LocalHapticFeedback.current

    LaunchedEffect(isPressed) {
        if (isPressed && enableHaptic) {
            try {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            } catch (_: Throwable) {}
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "squashScale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
}

/**
 * Clickable wrapper that also triggers the squash bounce effect.
 */
fun Modifier.squashBounceClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.93f,
    enableHaptic: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val hapticFeedback = LocalHapticFeedback.current

    LaunchedEffect(isPressed) {
        if (isPressed && enableHaptic && enabled) {
            try {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            } catch (_: Throwable) {}
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "squashClickableScale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}

/**
 * Screen entrance animations were removed to preserve fast navigation responsiveness.
 */
@Suppress("UNUSED_PARAMETER")
fun Modifier.staggeredEntrance(
    index: Int,
    enabled: Boolean = true,
    delayStepMs: Int = 38,
    slideOffsetDp: Dp = 14.dp
): Modifier = this
