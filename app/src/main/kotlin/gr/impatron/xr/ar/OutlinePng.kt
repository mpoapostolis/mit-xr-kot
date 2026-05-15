package gr.impatron.xr.ar

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import java.io.ByteArrayOutputStream

/**
 * Renders a 1024×1024 transparent PNG with glowing gold corner brackets — used
 * as the texture for a 3D quad placed exactly on the detected card pose so
 * the user can visually confirm what ARCore tracked.
 */
object OutlinePng {
    fun render(color: Int = Color.parseColor("#FFC9A86A")): ByteArray {
        val size = 1024
        val cornerLen = 220f
        val stroke = 24f

        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = (color and 0x00FFFFFF) or (0x5A shl 24)
            style = Paint.Style.STROKE
            strokeWidth = stroke * 2f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            maskFilter = BlurMaskFilter(16f, BlurMaskFilter.Blur.NORMAL)
        }
        val solid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        val s = size.toFloat()
        // 4 corners — each L-shape
        val corners = listOf(
            floatArrayOf(0f, cornerLen, 0f, 0f, cornerLen, 0f),
            floatArrayOf(s - cornerLen, 0f, s, 0f, s, cornerLen),
            floatArrayOf(s, s - cornerLen, s, s, s - cornerLen, s),
            floatArrayOf(cornerLen, s, 0f, s, 0f, s - cornerLen),
        )
        for (c in corners) {
            val path = Path().apply {
                moveTo(c[0], c[1])
                lineTo(c[2], c[3])
                lineTo(c[4], c[5])
            }
            canvas.drawPath(path, glow)
            canvas.drawPath(path, solid)
        }

        val bos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
        bmp.recycle()
        return bos.toByteArray()
    }
}
