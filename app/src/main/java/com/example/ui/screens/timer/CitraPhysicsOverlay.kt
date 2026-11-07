package com.example.ui.screens.timer

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

private data class CitraPhysicsBall(
    val id: Long,
    val durationMinutes: Int,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val radius: Float,
    var angleDeg: Float = 0f,
    var omegaDeg: Float = 0f
)

private data class ExplosionParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val radius: Float,
    val color: Color,
    var alpha: Float = 1f
)

private fun durationToRadiusDp(minutes: Int): Float {
    val m = minutes.coerceIn(1, 180).toFloat()
    return when {
        m <= 15f -> 14f
        m <= 30f -> 14f + (m - 15f) / 15f * 4f
        m <= 45f -> 18f + (m - 30f) / 15f * 4f
        m <= 60f -> 22f + (m - 45f) / 15f * 4f
        m <= 90f -> 26f + (m - 60f) / 30f * 4f
        m <= 120f -> 30f + (m - 90f) / 30f * 4f
        else -> 34f
    }
}

@Composable
fun CitraPhysicsOverlay(
    ballsJson: String,
    onBallsChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val durations = remember(ballsJson) {
        try {
            val arr = JSONArray(ballsJson)
            List(arr.length()) { idx -> arr.optInt(idx, 25) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    if (durations.isEmpty()) return

    // Accelerometer gravity state (in px/s^2)
    var gravityX by remember { mutableFloatStateOf(0f) }
    var gravityY by remember { mutableFloatStateOf(1250f) }

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // event.values[0]: +X is left tilt, -X is right tilt
                // event.values[1]: +Y is upright
                val gx = -event.values[0] * 145f
                val gy = event.values[1] * 145f
                // Smooth low-pass filter
                gravityX = gravityX * 0.75f + gx * 0.25f
                gravityY = gravityY * 0.75f + gy * 0.25f
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (accelerometer != null) {
            sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        }
        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }

        val balls = remember { mutableStateListOf<CitraPhysicsBall>() }
        val particles = remember { mutableStateListOf<ExplosionParticle>() }
        var frameTick by remember { mutableLongStateOf(0L) }

        // Sync balls list with persisted durations
        LaunchedEffect(durations, widthPx, heightPx) {
            if (widthPx <= 0f || heightPx <= 0f) return@LaunchedEffect
            if (durations.size < balls.size) {
                // Reset or removed externally
                balls.clear()
            }
            while (balls.size < durations.size) {
                val idx = balls.size
                val mins = durations[idx]
                val rPx = with(density) { durationToRadiusDp(mins).dp.toPx() }
                val spawnX = (rPx + Random.nextFloat() * (widthPx - 2f * rPx).coerceAtLeast(10f))
                val spawnY = rPx + 12f + (idx % 4) * (rPx * 0.6f)
                balls.add(
                    CitraPhysicsBall(
                        id = System.nanoTime() + idx,
                        durationMinutes = mins,
                        x = spawnX,
                        y = spawnY.coerceAtMost(heightPx - rPx),
                        vx = (Random.nextFloat() - 0.5f) * 140f,
                        vy = Random.nextFloat() * 60f,
                        radius = rPx,
                        angleDeg = (Random.nextFloat() - 0.5f) * 30f
                    )
                )
            }
        }

        // 60fps physics simulation loop
        LaunchedEffect(widthPx, heightPx) {
            if (widthPx <= 0f || heightPx <= 0f) return@LaunchedEffect
            var lastNanos = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val rawDt = (now - lastNanos) / 1_000_000_000f
                lastNanos = now
                val dt = rawDt.coerceIn(0.001f, 0.033f)

                val gx = gravityX
                val gy = gravityY

                // Integrate velocities & positions
                for (i in 0 until balls.size) {
                    val b = balls[i]
                    b.vx = (b.vx + gx * dt) * 0.992f
                    b.vy = (b.vy + gy * dt) * 0.992f
                    b.x += b.vx * dt
                    b.y += b.vy * dt
                    b.angleDeg = (b.angleDeg + b.omegaDeg * dt) % 360f
                    b.omegaDeg *= 0.96f
                }

                // Wall & Ball-to-Ball collision solver (3 iterations for stable stacking)
                val restitution = 0.52f
                repeat(3) {
                    for (i in 0 until balls.size) {
                        val b = balls[i]
                        val r = b.radius
                        if (b.x < r) {
                            b.x = r
                            if (b.vx < 0f) b.vx = -b.vx * restitution
                            b.omegaDeg = b.vy * 0.25f
                        } else if (b.x > widthPx - r) {
                            b.x = widthPx - r
                            if (b.vx > 0f) b.vx = -b.vx * restitution
                            b.omegaDeg = -b.vy * 0.25f
                        }
                        if (b.y < r) {
                            b.y = r
                            if (b.vy < 0f) b.vy = -b.vy * restitution
                        } else if (b.y > heightPx - r) {
                            b.y = heightPx - r
                            if (b.vy > 0f) b.vy = -b.vy * restitution
                            b.vx *= 0.94f
                            b.omegaDeg = b.vx * 0.35f
                        }
                    }

                    for (i in 0 until balls.size) {
                        val a = balls[i]
                        for (j in i + 1 until balls.size) {
                            val b = balls[j]
                            val dx = b.x - a.x
                            val dy = b.y - a.y
                            val minDist = a.radius + b.radius
                            val distSq = dx * dx + dy * dy
                            if (distSq < minDist * minDist) {
                                val dist = hypot(dx, dy).coerceAtLeast(0.001f)
                                val nx = dx / dist
                                val ny = dy / dist
                                val overlap = (minDist - dist) * 0.5f
                                a.x -= nx * overlap
                                a.y -= ny * overlap
                                b.x += nx * overlap
                                b.y += ny * overlap

                                val rvx = b.vx - a.vx
                                val rvy = b.vy - a.vy
                                val velAlongNormal = rvx * nx + rvy * ny
                                if (velAlongNormal < 0f) {
                                    val jImpulse = -(1f + restitution) * velAlongNormal * 0.5f
                                    val ix = jImpulse * nx
                                    val iy = jImpulse * ny
                                    a.vx -= ix
                                    a.vy -= iy
                                    b.vx += ix
                                    b.vy += iy
                                }
                            }
                        }
                    }
                }

                // Update explosion particles
                var pIdx = particles.size - 1
                while (pIdx >= 0) {
                    val p = particles[pIdx]
                    p.x += p.vx * dt
                    p.y += p.vy * dt
                    p.vy += 400f * dt
                    p.alpha -= dt * 2.2f
                    if (p.alpha <= 0f) {
                        particles.removeAt(pIdx)
                    }
                    pIdx--
                }

                frameTick++
            }
        }

        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            // Read frameTick to trigger redraw at 60fps
            @Suppress("UNUSED_EXPRESSION")
            frameTick

            for (i in 0 until balls.size) {
                val b = balls[i]
                drawCitraMascotBall(
                    center = Offset(b.x, b.y),
                    radius = b.radius,
                    angleDeg = b.angleDeg,
                    durationMinutes = b.durationMinutes
                )
            }

            for (i in 0 until particles.size) {
                val p = particles[i]
                drawCircle(
                    color = p.color.copy(alpha = p.alpha.coerceIn(0f, 1f)),
                    radius = p.radius,
                    center = Offset(p.x, p.y)
                )
            }
        }
    }
}

private fun DrawScope.drawCitraMascotBall(
    center: Offset,
    radius: Float,
    angleDeg: Float,
    durationMinutes: Int
) {
    val bodyColors = when {
        durationMinutes >= 120 -> listOf(
            Color(0xFFFFF9C4),
            Color(0xFFFFD54F),
            Color(0xFFFFB300),
            Color(0xFFFF8F00)
        )
        durationMinutes >= 60 -> listOf(
            Color(0xFFFFFF8D),
            Color(0xFFFFEE58),
            Color(0xFFFDD835),
            Color(0xFFFBC02D)
        )
        else -> listOf(
            Color(0xFFFFB74D),
            Color(0xFFFF7A00),
            Color(0xFFE65100)
        )
    }
    val rimColor = when {
        durationMinutes >= 120 -> Color(0xFFFFFDE7).copy(alpha = 0.85f)
        durationMinutes >= 60 -> Color(0xFFFFFFCC).copy(alpha = 0.75f)
        else -> Color(0xFFFFCC80).copy(alpha = 0.55f)
    }

    rotate(degrees = angleDeg, pivot = center) {
        // Soft drop shadow
        drawCircle(
            color = Color.Black.copy(alpha = 0.16f),
            radius = radius * 0.98f,
            center = Offset(center.x, center.y + radius * 0.08f)
        )

        // Body gradient (Golden, Yellow, or Classic Orange)
        drawCircle(
            brush = Brush.radialGradient(
                colors = bodyColors,
                center = Offset(center.x - radius * 0.28f, center.y - radius * 0.28f),
                radius = radius * 1.35f
            ),
            radius = radius,
            center = center
        )

        // Subtle rim
        drawCircle(
            color = rimColor,
            radius = radius,
            center = center,
            style = Stroke(width = (radius * 0.06f).coerceAtLeast(1f))
        )

        // Glossy highlight
        drawCircle(
            color = Color.White.copy(alpha = 0.32f),
            radius = radius * 0.24f,
            center = Offset(center.x - radius * 0.34f, center.y - radius * 0.34f)
        )

        // Cute green leaf on top
        val leafPath = Path().apply {
            moveTo(center.x, center.y - radius * 0.88f)
            quadraticTo(
                center.x + radius * 0.38f,
                center.y - radius * 1.25f,
                center.x + radius * 0.48f,
                center.y - radius * 0.85f
            )
            quadraticTo(
                center.x + radius * 0.22f,
                center.y - radius * 0.72f,
                center.x,
                center.y - radius * 0.88f
            )
            close()
        }
        drawPath(path = leafPath, color = Color(0xFF43A047))

        // Eyes
        val eyeY = center.y - radius * 0.06f
        val eyeOffsetX = radius * 0.28f
        val eyeRadius = (radius * 0.12f).coerceAtLeast(1.8f)

        drawCircle(
            color = Color(0xFF261400),
            radius = eyeRadius,
            center = Offset(center.x - eyeOffsetX, eyeY)
        )
        drawCircle(
            color = Color(0xFF261400),
            radius = eyeRadius,
            center = Offset(center.x + eyeOffsetX, eyeY)
        )

        // Eye sparkles
        drawCircle(
            color = Color.White,
            radius = eyeRadius * 0.38f,
            center = Offset(center.x - eyeOffsetX - eyeRadius * 0.25f, eyeY - eyeRadius * 0.25f)
        )
        drawCircle(
            color = Color.White,
            radius = eyeRadius * 0.38f,
            center = Offset(center.x + eyeOffsetX - eyeRadius * 0.25f, eyeY - eyeRadius * 0.25f)
        )

        // Cute smile
        val smileWidth = radius * 0.36f
        val smileHeight = radius * 0.22f
        drawArc(
            color = Color(0xFF261400),
            startAngle = 15f,
            sweepAngle = 150f,
            useCenter = false,
            topLeft = Offset(center.x - smileWidth * 0.5f, center.y + radius * 0.04f),
            size = Size(smileWidth, smileHeight),
            style = Stroke(width = (radius * 0.08f).coerceAtLeast(1.4f), cap = StrokeCap.Round)
        )
    }
}
