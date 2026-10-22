package app.pocket.data

import java.math.BigDecimal

/** Budget maths. With carry over on, a month's unspent limit (including what it carried in) is added to the next month's. */
object Budgets {
    private fun spent(cat: Long, month: Long): BigDecimal =
        Store.records(month, Period.MONTH.end(month)).filter { it.type == EXPENSE && it.catId == cat }
            .fold(BigDecimal.ZERO) { a, r -> a + r.amount }

    /** Surplus carried into [month] for [cat]; zero when carry over is off or the previous month had no budget. */
    fun carry(cat: Long, month: Long, depth: Int = 0): BigDecimal {
        if (!Prefs.carryOver || depth > 24) return BigDecimal.ZERO
        val prev = Period.MONTH.start(Period.MONTH.shift(month, -1))
        val b = Store.budgetsOf(prev).firstOrNull { it.catId == cat } ?: return BigDecimal.ZERO
        return (b.limit + carry(cat, prev, depth + 1) - spent(cat, prev)).max(BigDecimal.ZERO)
    }
}
