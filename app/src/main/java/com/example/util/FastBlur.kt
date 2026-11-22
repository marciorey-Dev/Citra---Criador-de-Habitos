package com.example.util

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Fast Gaussian StackBlur algorithm in pure Kotlin.
 * Works uniformly across all Android API levels (API 24 to 36+)
 * without requiring RenderScript or RenderEffect.
 */
object FastBlur {
    private const val MAX_BLUR_DIMENSION = 720

    fun blur(sentBitmap: Bitmap, radius: Int): Bitmap? {
        if (radius < 1) return sentBitmap

        val w = sentBitmap.width.coerceAtLeast(1)
        val h = sentBitmap.height.coerceAtLeast(1)

        // Downsample moderately while capping max dimension to prevent OOM on large images
        val baseScale = if (radius > 15) 0.5f else 0.75f
        val longestSide = max(w, h).toFloat()
        val capScale = if (longestSide * baseScale > MAX_BLUR_DIMENSION) {
            MAX_BLUR_DIMENSION / longestSide
        } else {
            baseScale
        }
        val scaledW = max(1, (w * capScale).toInt())
        val scaledH = max(1, (h * capScale).toInt())
        val scaledBitmap = try {
            Bitmap.createScaledBitmap(sentBitmap, scaledW, scaledH, true)
        } catch (_: OutOfMemoryError) {
            return null
        }

        val sw = scaledBitmap.width
        val sh = scaledBitmap.height

        val pix = try {
            IntArray(sw * sh)
        } catch (_: OutOfMemoryError) {
            if (scaledBitmap !== sentBitmap && !scaledBitmap.isRecycled) scaledBitmap.recycle()
            return null
        }
        scaledBitmap.getPixels(pix, 0, sw, 0, 0, sw, sh)
        if (scaledBitmap !== sentBitmap && !scaledBitmap.isRecycled) {
            scaledBitmap.recycle()
        }

        val wm = sw - 1
        val hm = sh - 1
        val wh = sw * sh
        val div = radius + radius + 1

        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)
        var rsum: Int
        var gsum: Int
        var bsum: Int
        var x: Int
        var y: Int
        var i: Int
        var p: Int
        var yp: Int
        var yi: Int
        var yw: Int
        val vmin = IntArray(max(sw, sh))

        var divsum = (div + 1) shr 1
        divsum *= divsum
        val dv = IntArray(256 * divsum)
        for (idx in 0 until 256 * divsum) {
            dv[idx] = idx / divsum
        }

        yi = 0
        yw = 0

        val stack = Array(div) { IntArray(3) }
        var stackpointer: Int
        var stackstart: Int
        var rbs: Int
        val routsum: Int
        val goutsum: Int
        val boutsum: Int
        val rinsum: Int
        val ginsum: Int
        val binsum: Int

        for (yIdx in 0 until sh) {
            var rinsumVal = 0
            var ginsumVal = 0
            var binsumVal = 0
            var routsumVal = 0
            var goutsumVal = 0
            var boutsumVal = 0
            rsum = 0
            gsum = 0
            bsum = 0
            for (idx in -radius..radius) {
                p = pix[yi + min(wm, max(idx, 0))]
                val sir = stack[idx + radius]
                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = (p and 0x0000ff)
                val rbsVal = radius + 1 - abs(idx)
                rsum += sir[0] * rbsVal
                gsum += sir[1] * rbsVal
                bsum += sir[2] * rbsVal
                if (idx > 0) {
                    rinsumVal += sir[0]
                    ginsumVal += sir[1]
                    binsumVal += sir[2]
                } else {
                    routsumVal += sir[0]
                    goutsumVal += sir[1]
                    boutsumVal += sir[2]
                }
            }
            stackpointer = radius

            for (xIdx in 0 until sw) {
                r[yi] = dv[rsum]
                g[yi] = dv[gsum]
                b[yi] = dv[bsum]

                rsum -= routsumVal
                gsum -= goutsumVal
                bsum -= boutsumVal

                stackstart = stackpointer - radius + div
                val sir = stack[stackstart % div]

                routsumVal -= sir[0]
                goutsumVal -= sir[1]
                boutsumVal -= sir[2]

                if (yIdx == 0) {
                    vmin[xIdx] = min(xIdx + radius + 1, wm)
                }
                p = pix[yw + vmin[xIdx]]

                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = (p and 0x0000ff)

                rinsumVal += sir[0]
                ginsumVal += sir[1]
                binsumVal += sir[2]

                rsum += rinsumVal
                gsum += ginsumVal
                bsum += binsumVal

                stackpointer = (stackpointer + 1) % div
                val nextSir = stack[stackpointer % div]

                routsumVal += nextSir[0]
                goutsumVal += nextSir[1]
                boutsumVal += nextSir[2]

                rinsumVal -= nextSir[0]
                ginsumVal -= nextSir[1]
                binsumVal -= nextSir[2]

                yi++
            }
            yw += sw
        }

        for (xIdx in 0 until sw) {
            var rinsumVal = 0
            var ginsumVal = 0
            var binsumVal = 0
            var routsumVal = 0
            var goutsumVal = 0
            var boutsumVal = 0
            rsum = 0
            gsum = 0
            bsum = 0
            yp = -radius * sw
            for (idx in -radius..radius) {
                yi = max(0, yp) + xIdx
                val sir = stack[idx + radius]
                sir[0] = r[yi]
                sir[1] = g[yi]
                sir[2] = b[yi]
                val rbsVal = radius + 1 - abs(idx)
                rsum += r[yi] * rbsVal
                gsum += g[yi] * rbsVal
                bsum += b[yi] * rbsVal
                if (idx > 0) {
                    rinsumVal += sir[0]
                    ginsumVal += sir[1]
                    binsumVal += sir[2]
                } else {
                    routsumVal += sir[0]
                    goutsumVal += sir[1]
                    boutsumVal += sir[2]
                }
                if (idx < hm) {
                    yp += sw
                }
            }
            yi = xIdx
            stackpointer = radius
            for (yIdx in 0 until sh) {
                pix[yi] = (-0x1000000 and pix[yi]) or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]

                rsum -= routsumVal
                gsum -= goutsumVal
                bsum -= boutsumVal

                stackstart = stackpointer - radius + div
                val sir = stack[stackstart % div]

                routsumVal -= sir[0]
                goutsumVal -= sir[1]
                boutsumVal -= sir[2]

                if (xIdx == 0) {
                    vmin[yIdx] = min(yIdx + radius + 1, hm) * sw
                }
                p = xIdx + vmin[yIdx]

                sir[0] = r[p]
                sir[1] = g[p]
                sir[2] = b[p]

                rinsumVal += sir[0]
                ginsumVal += sir[1]
                binsumVal += sir[2]

                rsum += rinsumVal
                gsum += ginsumVal
                bsum += binsumVal

                stackpointer = (stackpointer + 1) % div
                val nextSir = stack[stackpointer]

                routsumVal += nextSir[0]
                goutsumVal += nextSir[1]
                boutsumVal += nextSir[2]

                rinsumVal -= nextSir[0]
                ginsumVal -= nextSir[1]
                binsumVal -= nextSir[2]

                yi += sw
            }
        }

        return try {
            val resultBitmap = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
            resultBitmap.setPixels(pix, 0, sw, 0, 0, sw, sh)
            resultBitmap
        } catch (_: OutOfMemoryError) {
            null
        }
    }
}
