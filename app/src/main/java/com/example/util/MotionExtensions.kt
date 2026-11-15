package com.example.util

import android.os.SystemClock
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
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

val LocalScreenEntranceKey = compositionLocalOf { 0 }
val LocalScreenEnteredAtMillis = compositionLocalOf { 0L }

/**
 * Brief, fluid Staggered Entrance ("Efeito Escada") modifier triggered on screen transitions.
 * Displays each block sequentially with a soft fade-in and subtle upward slide.
 */
fun Modifier.staggeredEntrance(
    index: Int,
    enabled: Boolean = true,
    delayStepMs: Int = 38,
    slideOffsetDp: Dp = 14.dp
): Modifier = composed {
    if (!enabled) return@composed this

    val screenKey = LocalScreenEntranceKey.current
    val enteredAt = LocalScreenEnteredAtMillis.current
    val clampedIndex = index.coerceIn(0, 6)

    val alpha = remember { Animatable(0f) }
    val offsetY = remember { Animatable(slideOffsetDp.value) }
    val lastAnimatedKey = remember { mutableIntStateOf(-1) }

    LaunchedEffect(screenKey) {
        if (lastAnimatedKey.intValue == screenKey) return@LaunchedEffect
        lastAnimatedKey.intValue = screenKey

        val elapsed = if (enteredAt > 0L) (SystemClock.uptimeMillis() - enteredAt) else 0L
        if (elapsed > 600L) {
            alpha.snapTo(1f)
            offsetY.snapTo(0f)
            return@LaunchedEffect
        }

        alpha.snapTo(0f)
        offsetY.snapTo(slideOffsetDp.value)
        delay((clampedIndex * delayStepMs).toLong())
        val fadeSpec = tween<Float>(durationMillis = 220, easing = FastOutSlowInEasing)
        val slideSpec = tween<Float>(durationMillis = 240, easing = FastOutSlowInEasing)
        // Animate alpha & offset smoothly together
        coroutineScope {
            launch { alpha.animateTo(1f, animationSpec = fadeSpec) }
            launch { offsetY.animateTo(0f, animationSpec = slideSpec) }
        }
    }

    this.graphicsLayer {
        this.alpha = alpha.value
        this.translationY = offsetY.value * density
    }
}
