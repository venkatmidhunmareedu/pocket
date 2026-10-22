package app.pocket.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import app.pocket.R
import app.pocket.data.EXPENSE
import app.pocket.data.Fmt
import app.pocket.data.INCOME
import app.pocket.data.Icons
import app.pocket.data.Period
import app.pocket.data.Record
import app.pocket.data.Store
import app.pocket.data.TRANSFER
import app.pocket.databinding.FragmentAnalysisBinding
import app.pocket.databinding.ItemAccountAnalysisBinding
import app.pocket.databinding.ItemAnalysisBinding
import app.pocket.databinding.ItemFlowBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Category overview, flow over time, and per-account analysis for the header's period. */
class AnalysisFragment : Fragment() {
    private enum class Mode(val title: String, val type: Int) {
        EXPENSE_OVERVIEW("Expense overview", EXPENSE), INCOME_OVERVIEW("Income overview", INCOME),
        EXPENSE_FLOW("Expense flow", EXPENSE), INCOME_FLOW("Income flow", INCOME), ACCOUNTS("Account analysis", 0),
    }

    private val vm: MainViewModel by activityViewModels()
    private var _b: FragmentAnalysisBinding? = null
    private val b get() = _b!!
    private var mode = Mode.EXPENSE_OVERVIEW

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentAnalysisBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        b.btnChart.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setItems(Mode.entries.map { it.title }.toTypedArray()) { _, which -> mode = Mode.entries[which]; load() }.show()
        }
        vm.window.observe(viewLifecycleOwner) { load() }
    }

    override fun onResume() { super.onResume(); load() }

    private fun load() {
        val w = vm.window.value ?: return
        val recs = Store.records(w.period.start(w.anchor), w.period.end(w.anchor))
        vm.totals.value = Totals(
            recs.filter { it.type == EXPENSE }.fold(BigDecimal.ZERO) { a, r -> a + r.amount },
            recs.filter { it.type == INCOME }.fold(BigDecimal.ZERO) { a, r -> a + r.amount },
        )
        b.btnChart.text = mode.title
        b.list.removeAllViews()
        b.pie.visibility = View.GONE
        b.bar.visibility = View.GONE
        val shown = when (mode) {
            Mode.EXPENSE_OVERVIEW, Mode.INCOME_OVERVIEW -> overview(recs)
            Mode.EXPENSE_FLOW, Mode.INCOME_FLOW -> flow(w, recs)
            Mode.ACCOUNTS -> accounts(w, recs)
        }
        b.empty.visibility = if (shown) View.GONE else View.VISIBLE
        b.scroll.visibility = if (shown) View.VISIBLE else View.GONE
        b.emptyText.text = w.period.analysisEmpty
    }

    private fun overview(recs: List<Record>): Boolean {
        val ctx = requireContext()
        val cats = Store.categories().associateBy { it.id }
        val sums = recs.filter { it.type == mode.type }.groupBy { it.catId }
            .mapValues { (_, l) -> l.fold(BigDecimal.ZERO) { a, r -> a + r.amount } }
            .toList().sortedByDescending { it.second }
        if (sums.isEmpty()) return false
        val total = sums.fold(BigDecimal.ZERO) { a, p -> a + p.second }
        val colors = sums.map { (catId, _) -> Icons.color(cats[catId]?.icon ?: 0) }
        b.pie.visibility = View.VISIBLE
        b.pie.slices = sums.mapIndexed { i, p -> PieChartView.Slice(p.second.toFloat(), colors[i]) }
        b.pie.centerText = Fmt.money(total)
        sums.forEachIndexed { i, (catId, sum) ->
            val cat = cats[catId]
            val pct = sum.multiply(BigDecimal(100)).divide(total, 1, RoundingMode.HALF_UP)
            val row = ItemAnalysisBinding.inflate(layoutInflater, b.list, false)
            row.ivIcon.setImageDrawable(Icons.badge(ctx, cat?.icon ?: 0))
            row.tvTitle.text = cat?.name ?: "Uncategorized"
            row.tvAmount.text = Fmt.money(sum)
            row.tvPct.text = "$pct%"
            row.progress.setProgress((pct.toFloat() * 10).toInt(), false)
            row.progress.setIndicatorColor(colors[i])
            row.root.setOnClickListener {
                if (cat != null) startActivity(
                    Intent(ctx, DetailActivity::class.java)
                        .putExtra(DetailActivity.EXTRA_KIND, DetailActivity.KIND_CATEGORY).putExtra(DetailActivity.EXTRA_ID, cat.id)
                )
            }
            b.list.addView(row.root)
        }
        return true
    }

    /** Total per hour (day view), per day (week/month) or per month (3/6 months, year). */
    private fun flow(w: Window, recs: List<Record>): Boolean {
        val typed = recs.filter { it.type == mode.type }
        if (typed.isEmpty()) return false
        val start = w.period.start(w.anchor)
        val end = w.period.end(w.anchor)
        val (step, pattern) = when (w.period) {
            Period.DAY -> Calendar.HOUR_OF_DAY to "HH:00"
            Period.WEEK, Period.MONTH -> Calendar.DAY_OF_YEAR to "d MMM"
            else -> Calendar.MONTH to "MMM yy"
        }
        val short = when (w.period) { Period.DAY -> "HH"; Period.WEEK, Period.MONTH -> "d"; else -> "MMM" }
        val label = SimpleDateFormat(pattern, Locale.getDefault())
        val shortLabel = SimpleDateFormat(short, Locale.getDefault())
        val buckets = ArrayList<Triple<String, String, BigDecimal>>()
        val c = Calendar.getInstance().apply { timeInMillis = start }
        while (c.timeInMillis < end) {
            val from = c.timeInMillis
            c.add(step, 1)
            val sum = typed.filter { it.time >= from && it.time < c.timeInMillis }.fold(BigDecimal.ZERO) { a, r -> a + r.amount }
            buckets += Triple(shortLabel.format(Date(from)), label.format(Date(from)), sum)
        }
        val color = requireContext().attrColor(if (mode.type == EXPENSE) R.attr.pkNegative else R.attr.pkPositive)
        b.bar.visibility = View.VISIBLE
        b.bar.barColor = color
        b.bar.bars = buckets.map { it.first to it.third.toFloat() }
        val total = buckets.fold(BigDecimal.ZERO) { a, t -> a + t.third }
        addFlowRow("Total", Fmt.money(total), bold = true)
        buckets.filter { it.third.signum() > 0 }.forEach { addFlowRow(it.second, Fmt.money(it.third)) }
        return true
    }

    private fun addFlowRow(label: String, amount: String, bold: Boolean = false) {
        val row = ItemFlowBinding.inflate(layoutInflater, b.list, false)
        row.tvLabel.text = label
        row.tvAmount.text = amount
        if (bold) row.tvLabel.setTypeface(row.tvLabel.typeface, android.graphics.Typeface.BOLD)
        b.list.addView(row.root)
    }

    private fun accounts(w: Window, recs: List<Record>): Boolean {
        val ctx = requireContext()
        val accs = Store.accounts()
        if (accs.isEmpty()) return false
        val start = w.period.start(w.anchor)
        val before = Store.allRecords().filter { it.time < start }
        fun delta(id: Long, list: List<Record>): BigDecimal {
            var d = BigDecimal.ZERO
            for (r in list) when (r.type) {
                INCOME -> if (r.accId == id) d += r.amount
                EXPENSE -> if (r.accId == id) d -= r.amount
                TRANSFER -> { if (r.fromId == id) d -= r.amount; if (r.toId == id) d += r.amount }
            }
            return d
        }
        accs.forEach { a ->
            val startBal = a.initial + delta(a.id, before)
            val inc = recs.filter { it.type == INCOME && it.accId == a.id }.fold(BigDecimal.ZERO) { x, r -> x + r.amount }
            val exp = recs.filter { it.type == EXPENSE && it.accId == a.id }.fold(BigDecimal.ZERO) { x, r -> x + r.amount }
            val tin = recs.filter { it.type == TRANSFER && it.toId == a.id }.fold(BigDecimal.ZERO) { x, r -> x + r.amount }
            val tout = recs.filter { it.type == TRANSFER && it.fromId == a.id }.fold(BigDecimal.ZERO) { x, r -> x + r.amount }
            val row = ItemAccountAnalysisBinding.inflate(layoutInflater, b.list, false)
            row.ivIcon.setImageDrawable(Icons.badge(ctx, a.icon))
            row.tvTitle.text = a.name
            row.tvBody.text = listOf(
                "Starting balance" to startBal, "Income" to inc, "Expense" to exp,
                "Transfer in" to tin, "Transfer out" to tout, "Ending balance" to (startBal + inc - exp + tin - tout),
            ).joinToString("\n") { "${it.first}: ${Fmt.money(it.second)}" }
            row.root.setOnClickListener {
                startActivity(
                    Intent(ctx, DetailActivity::class.java)
                        .putExtra(DetailActivity.EXTRA_KIND, DetailActivity.KIND_ACCOUNT).putExtra(DetailActivity.EXTRA_ID, a.id)
                )
            }
            b.list.addView(row.root)
        }
        return true
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
