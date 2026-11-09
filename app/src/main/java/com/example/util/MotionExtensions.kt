package com.example.util

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
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
import kotlinx.coroutines.delay

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
        label = "squashBounceAnim"
    )

    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Clickable modifier combining Squash & Stretch animation with full click handling and haptics.
 */
fun Modifier.squashBounceClickable(
    pressedScale: Float = 0.93f,
    enableHaptic: Boolean = true,
    enabled: Boolean = true,
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
        label = "squashBounceClickScale"
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
 * Staggered Entrance ("Efeito Escada") modifier used on initial app startup.
 * Delays the reveal based on [index] to create a cascading entrance with fade-in and upward slide.
 */
fun Modifier.staggeredEntrance(
    index: Int,
    enabled: Boolean = true,
    delayStepMs: Int = 65,
    slideOffsetDp: Dp = 22.dp
): Modifier = composed {
    if (!enabled) return@composed this

    val alpha = remember { Animatable(0f) }
    val offsetY = remember { Animatable(slideOffsetDp.value) }

    LaunchedEffect(Unit) {
        delay((index * delayStepMs).toLong())
        // Run slide-up and fade-in concurrently
        val animSpec = tween<Float>(durationMillis = 380, easing = FastOutSlowInEasing)
        alpha.animateTo(1f, animationSpec = animSpec)
    }

    LaunchedEffect(Unit) {
        delay((index * delayStepMs).toLong())
        val animSpec = spring<Float>(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        )
        offsetY.animateTo(0f, animationSpec = animSpec)
    }

    this.graphicsLayer {
        this.alpha = alpha.value
        this.translationY = offsetY.value * density
    }
}
