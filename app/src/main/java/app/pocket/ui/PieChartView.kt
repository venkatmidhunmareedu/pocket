package app.pocket.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/** Donut chart with a centre label. */
class PieChartView(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    data class Slice(val value: Float, val color: Int)

    var slices: List<Slice> = emptyList()
        set(v) { field = v; invalidate() }
    var centerText: String = ""
        set(v) { field = v; invalidate() }

    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val rect = RectF()

    override fun onDraw(c: Canvas) {
        val total = slices.sumOf { it.value.toDouble() }.toFloat()
        if (total <= 0f) return
        val size = min(width, height) * 0.88f
        val stroke = size * 0.2f
        arc.strokeWidth = stroke
        val cx = width / 2f
        val cy = height / 2f
        val r = size / 2f - stroke / 2f
        rect.set(cx - r, cy - r, cx + r, cy + r)
        var start = -90f
        val gap = if (slices.size > 1) 1.2f else 0f
        for (s in slices) {
            val sweep = s.value / total * 360f
            arc.color = s.color
            c.drawArc(rect, start, (sweep - gap).coerceAtLeast(0.5f), false, arc)
            start += sweep
        }
        label.color = context.attrColor(com.google.android.material.R.attr.colorOnSurface)
        label.textSize = size * 0.095f
        c.drawText(centerText, cx, cy + label.textSize / 3f, label)
    }

    companion object {
        val PALETTE = intArrayOf(
            0xFFEF5350.toInt(), 0xFFFFB74D.toInt(), 0xFFFFF176.toInt(), 0xFF81C784.toInt(),
            0xFF4DB6AC.toInt(), 0xFF4FC3F7.toInt(), 0xFF7986CB.toInt(), 0xFFBA68C8.toInt(),
            0xFFF06292.toInt(), 0xFFA1887F.toInt(), 0xFF90A4AE.toInt(), 0xFFAED581.toInt(),
        )
    }
}
