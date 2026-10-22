package app.pocket.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import kotlin.math.min

/**
 * Coloured circle with a darker lower-right half and a light glyph on top, the look of the category/account icons.
 * Draws at whatever bounds it is given.
 */
class BadgeDrawable(ctx: Context, glyphRes: Int, private val base: Int) : Drawable() {
    private val glyph = ContextCompat.getDrawable(ctx, glyphRes)!!.mutate().also { it.setTint(0xFFEEEEEE.toInt()) }
    private val dark = darken(base)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clip = Path()
    private val half = Path()

    private fun darken(c: Int): Int {
        val f = 0.78f
        return (c and 0xFF000000.toInt()) or
            ((((c shr 16) and 0xFF) * f).toInt() shl 16) or ((((c shr 8) and 0xFF) * f).toInt() shl 8) or ((c and 0xFF) * f).toInt()
    }

    override fun draw(c: Canvas) {
        val b = bounds
        val r = min(b.width(), b.height()) / 2f
        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        clip.reset(); clip.addCircle(cx, cy, r, Path.Direction.CW)
        half.reset()
        half.moveTo(cx + r, cy - r); half.lineTo(cx + r, cy + r); half.lineTo(cx - r, cy + r); half.close()
        c.save()
        c.clipPath(clip)
        paint.color = base; c.drawCircle(cx, cy, r, paint)
        paint.color = dark; c.drawPath(half, paint)
        c.restore()
        val inset = (r * 0.52f).toInt()
        glyph.setBounds((cx - inset).toInt(), (cy - inset).toInt(), (cx + inset).toInt(), (cy + inset).toInt())
        glyph.draw(c)
    }

    override fun getIntrinsicWidth() = 96
    override fun getIntrinsicHeight() = 96
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; glyph.alpha = alpha }
    override fun setColorFilter(cf: ColorFilter?) { paint.colorFilter = cf }
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
