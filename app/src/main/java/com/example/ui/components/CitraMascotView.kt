package com.example.ui.components

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

/**
 * Native Android View that renders the animated Citra Mascot (matching CitraMascote Composable)
 * with periodic double-blinking eyes, gentle pulse breathing, and leaf sway.
 */
class CitraMascotView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFF8B1F") // Citra Orange
        style = Paint.Style.FILL
    }

    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B3FFB866") // Peach highlight
        style = Paint.Style.FILL
    }

    private val stemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF2F8A3E") // Leaf stem
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
    }

    private val leafBladePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF3FA34D") // Leaf green
        style = Paint.Style.FILL
    }

    private val leafVeinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF2F8A3E") // Leaf vein
        style = Paint.Style.STROKE
        strokeWidth = 2.2f
        strokeCap = Paint.Cap.ROUND
    }

    private val cheekPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#73E8451F") // Rosy cheeks
        style = Paint.Style.FILL
    }

    private val eyeWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val pupilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF2B1A12") // Dark ink pupil
        style = Paint.Style.FILL
    }

    private val eyeHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    // Leaf paths
    private val stemPath = Path().apply {
        moveTo(100f, 24f)
        quadTo(101f, 12f, 108f, 4f)
    }

    private val leafPath = Path().apply {
        moveTo(103f, 16f)
        cubicTo(102f, -2f, 126f, -8f, 134f, 4f)
        cubicTo(126f, 18f, 108f, 20f, 103f, 16f)
        close()
    }

    private val veinPath = Path().apply {
        moveTo(104f, 15f)
        quadTo(116f, 7f, 128f, 4f)
    }

    // Animation properties
    var eyelidScaleY: Float = 1.0f
        set(value) {
            field = value
            invalidate()
        }

    var pulseScale: Float = 1.0f
        set(value) {
            field = value
            invalidate()
        }

    var leafAngle: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isAnimating = false

    private val blinkRunnable = object : Runnable {
        override fun run() {
            triggerDoubleBlink()
            mainHandler.postDelayed(this, 3000L)
        }
    }

    init {
        startIdleAnimations()
    }

    fun startIdleAnimations() {
        if (isAnimating) return
        isAnimating = true

        // Continuous subtle breathing pulse (0.97f to 1.03f)
        val pulseAnimator = ValueAnimator.ofFloat(0.97f, 1.03f).apply {
            duration = 1800L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                pulseScale = it.animatedValue as Float
            }
        }
        pulseAnimator.start()

        // Subtle leaf sway
        val leafAnimator = ValueAnimator.ofFloat(-4f, 4f).apply {
            duration = 1200L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                leafAngle = it.animatedValue as Float
            }
        }
        leafAnimator.start()

        // Periodic double-blink cycle
        mainHandler.postDelayed(blinkRunnable, 1500L)
    }

    fun stopAnimations() {
        isAnimating = false
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun triggerDoubleBlink() {
        val blink1Down = ValueAnimator.ofFloat(1f, 0.08f).setDuration(90)
        val blink1Up = ValueAnimator.ofFloat(0.08f, 1f).setDuration(90)
        val blink2Down = ValueAnimator.ofFloat(1f, 0.08f).setDuration(90)
        val blink2Up = ValueAnimator.ofFloat(0.08f, 1f).setDuration(90)

        blink1Down.addUpdateListener { eyelidScaleY = it.animatedValue as Float }
        blink1Up.addUpdateListener { eyelidScaleY = it.animatedValue as Float }
        blink2Down.addUpdateListener { eyelidScaleY = it.animatedValue as Float }
        blink2Up.addUpdateListener { eyelidScaleY = it.animatedValue as Float }

        AnimatorSet().apply {
            playSequentially(blink1Down, blink1Up, blink2Down, blink2Up)
            start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimations()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val mascotBaseW = 200f
        val mascotBaseH = 200f
        val baseScale = Math.min(w / mascotBaseW, h / mascotBaseH) * 0.88f
        val currentScale = baseScale * pulseScale

        canvas.save()
        // Center exactly in the View
        canvas.translate(w / 2f, h / 2f)
        canvas.scale(currentScale, currentScale)
        // Center of mascot (100f, 95f) mapped to (0, 0)
        canvas.translate(-100f, -95f)

        // 1. Body
        canvas.drawCircle(100f, 100f, 80f, bodyPaint)

        // 2. Highlight (peach oval rotated -35 deg at (64, 52))
        canvas.save()
        canvas.rotate(-35f, 64f, 52f)
        val highlightRect = RectF(64f - 17f, 52f - 8f, 64f + 17f, 52f + 8f)
        canvas.drawOval(highlightRect, highlightPaint)
        canvas.restore()

        // 3. Leaf with subtle sway
        canvas.save()
        canvas.rotate(leafAngle, 100f, 22f)
        canvas.drawPath(stemPath, stemPaint)
        canvas.drawPath(leafPath, leafBladePaint)
        canvas.drawPath(veinPath, leafVeinPaint)
        canvas.restore()

        // 4. Rosy Cheeks
        canvas.drawOval(RectF(48f - 10f, 120f - 6f, 48f + 10f, 120f + 6f), cheekPaint)
        canvas.drawOval(RectF(152f - 10f, 120f - 6f, 152f + 10f, 120f + 6f), cheekPaint)

        // 5. Left Eye (74, 98) & Right Eye (126, 98) with eyelidScaleY
        drawEye(canvas, 74f, 98f, eyelidScaleY)
        drawEye(canvas, 126f, 98f, eyelidScaleY)

        canvas.restore()
    }

    private fun drawEye(canvas: Canvas, cx: Float, cy: Float, eyeScaleY: Float) {
        canvas.save()
        canvas.scale(1f, eyeScaleY, cx, cy)

        // Sclera (white oval 34x42)
        val scleraRect = RectF(cx - 17f, cy - 21f, cx + 17f, cy + 21f)
        canvas.drawOval(scleraRect, eyeWhitePaint)

        // Pupil (dark brown circle radius 9)
        canvas.drawCircle(cx, cy, 9f, pupilPaint)

        // Specular highlight dot (white circle radius 3)
        canvas.drawCircle(cx + 3.5f, cy - 4.5f, 3f, eyeHighlightPaint)

        canvas.restore()
    }
}
