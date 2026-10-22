package app.pocket.ui

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import app.pocket.data.EXPENSE
import app.pocket.data.Fmt
import app.pocket.data.INCOME
import app.pocket.data.Record
import app.pocket.data.Store
import app.pocket.data.TRANSFER
import java.io.File
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A4 report: summary, expense/income by category, then every transaction. Plain black-on-white for printing. */
object PdfReport {
    private const val W = 595
    private const val H = 842
    private const val M = 36f

    private val ink = Color.rgb(33, 33, 33)
    private val faint = Color.rgb(120, 120, 120)
    private val red = Color.rgb(198, 40, 40)
    private val green = Color.rgb(46, 125, 50)
    private val line = Color.rgb(210, 210, 210)

    private fun paint(size: Float, bold: Boolean = false, color: Int = ink, right: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size; this.color = color
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        if (right) textAlign = Paint.Align.RIGHT
    }

    private fun fit(text: String, p: Paint, max: Float): String {
        if (p.measureText(text) <= max) return text
        var t = text
        while (t.isNotEmpty() && p.measureText("$t…") > max) t = t.dropLast(1)
        return "$t…"
    }

    private class Writer(val doc: PdfDocument) {
        var page: PdfDocument.Page? = null
        var y = 0f
        var n = 0
        val canvas get() = page!!.canvas

        fun newPage() {
            page?.let { doc.finishPage(it) }
            n++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, n).create())
            y = M
            val foot = paint(8f, color = faint)
            canvas.drawText("Pocket  ·  page $n", M, H - 18f, foot)
        }

        fun need(h: Float) { if (page == null || y + h > H - 40f) newPage() }
        fun rule() { canvas.drawLine(M, y, W - M, y, Paint().apply { color = line; strokeWidth = 0.8f }) }
        fun finish() { page?.let { doc.finishPage(it) } }
    }

    fun write(file: File, range: String, recs: List<Record>) {
        val cats = Store.categories().associateBy { it.id }
        val accs = Store.accounts().associateBy { it.id }
        val doc = PdfDocument()
        val w = Writer(doc)
        w.newPage()
        val c = w.canvas

        c.drawText("Pocket", M, w.y + 22f, paint(24f, true))
        c.drawText("Report", M + 92f, w.y + 22f, paint(24f, false, faint))
        w.y += 34f
        c.drawText(range, M, w.y + 10f, paint(11f, color = faint))
        c.drawText("Generated " + SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date()), W - M, w.y + 10f, paint(9f, color = faint, right = true))
        w.y += 24f

        fun sum(l: List<Record>) = l.fold(BigDecimal.ZERO) { a, r -> a + r.amount }
        val exp = recs.filter { it.type == EXPENSE }
        val inc = recs.filter { it.type == INCOME }
        val boxW = (W - 2 * M - 20f) / 3
        listOf(
            Triple("EXPENSE", sum(exp), red), Triple("INCOME", sum(inc), green),
            Triple("TOTAL", sum(inc) - sum(exp), if (sum(inc) >= sum(exp)) green else red),
        ).forEachIndexed { i, (label, v, col) ->
            val x = M + i * (boxW + 10f)
            c.drawRoundRect(x, w.y, x + boxW, w.y + 54f, 8f, 8f, Paint().apply { color = Color.rgb(245, 245, 245) })
            c.drawText(label, x + 10f, w.y + 18f, paint(8.5f, true, faint))
            c.drawText(Fmt.money(v), x + 10f, w.y + 40f, paint(15f, true, col))
        }
        w.y += 74f

        fun section(title: String, list: List<Record>, col: Int) {
            if (list.isEmpty()) return
            val total = sum(list)
            val rows = list.groupBy { it.catId }.map { (id, l) -> (cats[id]?.name ?: "Uncategorized") to sum(l) }.sortedByDescending { it.second }
            w.need(34f + 18f)
            w.canvas.drawText(title, M, w.y + 12f, paint(12f, true))
            w.y += 20f
            for ((name, v) in rows) {
                w.need(18f)
                val pct = v.multiply(BigDecimal(100)).divide(total, 1, java.math.RoundingMode.HALF_UP)
                val barMax = 150f
                w.canvas.drawText(fit(name, paint(10f), 160f), M, w.y + 11f, paint(10f))
                w.canvas.drawRect(M + 180f, w.y + 3f, M + 180f + barMax, w.y + 11f, Paint().apply { color = Color.rgb(235, 235, 235) })
                w.canvas.drawRect(M + 180f, w.y + 3f, M + 180f + barMax * pct.toFloat() / 100f, w.y + 11f, Paint().apply { color = col })
                w.canvas.drawText("$pct%", M + 345f, w.y + 11f, paint(9f, color = faint))
                w.canvas.drawText(Fmt.money(v), W - M, w.y + 11f, paint(10f, true, right = true))
                w.y += 17f
            }
            w.y += 12f
        }
        section("Expense by category", exp, red)
        section("Income by category", inc, green)

        w.need(40f)
        w.canvas.drawText("Transactions (${recs.size})", M, w.y + 12f, paint(12f, true))
        w.y += 22f
        val head = paint(8.5f, true, faint)
        fun header() {
            w.canvas.drawText("DATE", M, w.y + 9f, head)
            w.canvas.drawText("CATEGORY / ACCOUNT", M + 72f, w.y + 9f, head)
            w.canvas.drawText("NOTE", M + 250f, w.y + 9f, head)
            w.canvas.drawText("AMOUNT", W - M, w.y + 9f, paint(8.5f, true, faint, right = true))
            w.y += 14f
            w.rule(); w.y += 4f
        }
        header()
        val df = SimpleDateFormat("d MMM yy", Locale.getDefault())
        val tf = SimpleDateFormat("h:mm a", Locale.getDefault())
        for (r in recs) {
            if (w.y + 26f > H - 40f) { w.newPage(); header() }
            val col = when (r.type) { EXPENSE -> red; INCOME -> green; else -> Color.rgb(21, 101, 192) }
            val title = if (r.type == TRANSFER) "Transfer" else cats[r.catId]?.name ?: "Uncategorized"
            val sub = if (r.type == TRANSFER) "${accs[r.fromId]?.name ?: "?"} › ${accs[r.toId]?.name ?: "?"}" else accs[r.accId]?.name ?: "?"
            val cv = w.canvas
            cv.drawText(df.format(Date(r.time)), M, w.y + 10f, paint(9.5f))
            cv.drawText(tf.format(Date(r.time)), M, w.y + 21f, paint(7.5f, color = faint))
            cv.drawText(fit(title, paint(10f, true), 170f), M + 72f, w.y + 10f, paint(10f, true))
            cv.drawText(fit(sub, paint(8.5f), 170f), M + 72f, w.y + 21f, paint(8.5f, color = faint))
            cv.drawText(fit(r.note.replace('\n', ' '), paint(9f), W - M - 100f - (M + 250f)), M + 250f, w.y + 10f, paint(9f))
            val sign = if (r.type == EXPENSE) "-" else if (r.type == INCOME) "+" else ""
            cv.drawText(sign + Fmt.money(r.amount), W - M, w.y + 10f, paint(10f, true, col, right = true))
            w.y += 28f
            w.rule(); w.y += 4f
        }
        w.finish()
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
    }
}
