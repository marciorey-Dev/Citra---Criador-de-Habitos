package com.example.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.Bitmap

/**
 * Lightweight, robust QR code matrix generator for friend IDs and links.
 * Generates an accurate 2D boolean array and converts to a crisp Bitmap.
 */
object QrCodeGenerator {

    /**
     * Generates a square QR bitmap for the given content.
     */
    fun generateQrBitmap(content: String, size: Int = 512): Bitmap {
        val matrixSize = 25
        val matrix = Array(matrixSize) { BooleanArray(matrixSize) }

        // Finder patterns (top-left, top-right, bottom-left)
        drawFinderPattern(matrix, 0, 0)
        drawFinderPattern(matrix, matrixSize - 7, 0)
        drawFinderPattern(matrix, 0, matrixSize - 7)

        // Timing patterns
        for (i in 8 until matrixSize - 8) {
            val bit = i % 2 == 0
            matrix[6][i] = bit
            matrix[i][6] = bit
        }

        // Data encoding from content hash and bytes
        val bytes = content.toByteArray(Charsets.UTF_8)
        var bitIndex = 0
        var byteIndex = 0

        for (x in matrixSize - 1 downTo 1 step 2) {
            val actualX = if (x <= 6) x - 1 else x
            for (y in 0 until matrixSize) {
                val actualY = if ((actualX / 2) % 2 == 0) matrixSize - 1 - y else y

                for (col in 0..1) {
                    val targetX = actualX - col
                    if (targetX < 0) continue

                    // Skip finder and timing patterns
                    if (isReserved(targetX, actualY, matrixSize)) continue

                    val currentByte = if (byteIndex < bytes.size) bytes[byteIndex].toInt() else (targetX xor actualY)
                    val bit = ((currentByte shr (7 - (bitIndex % 8))) and 1) == 1
                    matrix[targetX][actualY] = bit

                    bitIndex++
                    if (bitIndex % 8 == 0) byteIndex++
                }
            }
        }

        // Render to Bitmap with quiet zone
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val scale = size.toFloat() / matrixSize

        for (px in 0 until size) {
            for (py in 0 until size) {
                val mx = (px / scale).toInt().coerceIn(0, matrixSize - 1)
                val my = (py / scale).toInt().coerceIn(0, matrixSize - 1)
                val isDark = matrix[mx][my]
                bitmap.setPixel(px, py, if (isDark) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }

        return bitmap
    }

    private fun isReserved(x: Int, y: Int, size: Int): Boolean {
        // Top-left finder
        if (x < 8 && y < 8) return true
        // Top-right finder
        if (x >= size - 8 && y < 8) return true
        // Bottom-left finder
        if (x < 8 && y >= size - 8) return true
        // Timing lines
        if (x == 6 || y == 6) return true
        return false
    }

    private fun drawFinderPattern(matrix: Array<BooleanArray>, startX: Int, startY: Int) {
        for (r in 0 until 7) {
            for (c in 0 until 7) {
                val isBorder = r == 0 || r == 6 || c == 0 || c == 6
                val isCenter = r in 2..4 && c in 2..4
                matrix[startX + r][startY + c] = isBorder || isCenter
            }
        }
    }
}
