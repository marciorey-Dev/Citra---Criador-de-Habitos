package com.example.ui.tutorial

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

object TutorialTargetRegistry {
    val boundsMap = mutableStateMapOf<String, Rect>()

    fun setBounds(key: String, bounds: Rect) {
        boundsMap[key] = bounds
    }

    fun removeBounds(key: String) {
        boundsMap.remove(key)
    }

    fun getBounds(key: String): Rect? {
        return boundsMap[key]
    }
}

fun Modifier.tutorialTarget(key: String): Modifier = this.then(
    Modifier.onGloballyPositioned { coordinates ->
        if (coordinates.isAttached) {
            val rootBounds = coordinates.boundsInRoot()
            if (rootBounds.width > 0 && rootBounds.height > 0) {
                TutorialTargetRegistry.setBounds(key, rootBounds)
            }
        }
    }
)
