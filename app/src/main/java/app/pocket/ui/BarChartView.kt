package app.pocket.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.ceil

/** Simple bar chart: one bar per labelled value, labels thinned out to fit. */
class BarChartView(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    var bars: List<Pair<String, Float>> = emptyList()
        set(v) { field = v; invalidate() }
    var barColor: Int = 0xFFEF5350.toInt()
        set(v) { field = v; invalidate() }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val rect = RectF()

    override fun onDraw(c: Canvas) {
        if (bars.isEmpty()) return
        val max = bars.maxOf { it.second }.coerceAtLeast(0.0001f)
        val labelH = 36f
        val chartH = height - labelH - 8f
        val slot = width.toFloat() / bars.size
        val bw = slot * 0.68f
        text.color = context.attrColor(app.pocket.R.attr.pkDisabled)
        text.textSize = (slot * 0.5f).coerceIn(20f, 28f)
        val every = ceil(bars.size / 8f).toInt().coerceAtLeast(1)
        fill.color = barColor
        bars.forEachIndexed { i, (label, v) ->
            val x = i * slot + (slot - bw) / 2
            val h = if (v <= 0f) 3f else (v / max * chartH).coerceAtLeast(6f)
            rect.set(x, chartH - h, x + bw, chartH)
            c.drawRoundRect(rect, 6f, 6f, fill)
            if (i % every == 0) c.drawText(label, i * slot + slot / 2, height - 6f, text)
        }
    }
}
