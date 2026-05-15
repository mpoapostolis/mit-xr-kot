package gr.impatron.xr.ar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import java.io.ByteArrayOutputStream

/**
 * Renders a string into a PNG byte array — used as a texture for the runtime
 * glTF quad that anchors text events above the card.
 */
object TextPng {
    data class Result(val bytes: ByteArray, val aspect: Float)

    fun render(
        text: String,
        textColor: Int = Color.parseColor("#FFC9A86A"),
        background: Int = Color.parseColor("#CC0A0A0A"),
        fontSize: Float = 64f,
    ): Result {
        val padding = 48f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            this.textSize = fontSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val maxWidth = 1024f
        val (lines, totalWidth, totalHeight) = layoutLines(text, paint, maxWidth - padding * 2)

        val w = (totalWidth + padding * 2).coerceAtLeast(256f)
        val h = (totalHeight + padding * 2).coerceAtLeast(128f)
        val bmp = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = background }
        canvas.drawRoundRect(RectF(0f, 0f, w, h), 28f, 28f, bgPaint)

        var y = (h - totalHeight) / 2 - paint.ascent()
        for (line in lines) {
            val lw = paint.measureText(line)
            canvas.drawText(line, (w - lw) / 2, y, paint)
            y += paint.descent() - paint.ascent()
        }

        val bos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
        bmp.recycle()
        return Result(bos.toByteArray(), w / h)
    }

    private data class LayoutOut(
        val lines: List<String>,
        val width: Float,
        val height: Float,
    )

    private operator fun LayoutOut.component1() = lines
    private operator fun LayoutOut.component2() = width
    private operator fun LayoutOut.component3() = height

    private fun layoutLines(text: String, paint: Paint, maxWidth: Float): LayoutOut {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) > maxWidth && current.isNotEmpty()) {
                lines.add(current.toString())
                current = StringBuilder(word)
            } else {
                current = StringBuilder(candidate)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        val lineH = paint.descent() - paint.ascent()
        val maxW = lines.maxOf { paint.measureText(it) }
        return LayoutOut(lines, maxW, lineH * lines.size)
    }
}
