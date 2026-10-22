package app.pocket.ui

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import app.pocket.data.Period
import app.pocket.data.EXPENSE
import app.pocket.data.INCOME
import app.pocket.data.Prefs
import app.pocket.data.Store
import java.math.BigDecimal

data class Window(val period: Period, val anchor: Long)
data class Totals(val expense: BigDecimal, val income: BigDecimal)

/** Shared between MainActivity (header) and the tab fragments. */
class MainViewModel : ViewModel() {
    val window = MutableLiveData(Window(Prefs.period, System.currentTimeMillis()))
    val totals = MutableLiveData(Totals(BigDecimal.ZERO, BigDecimal.ZERO))

    /** Accounts/Categories tabs: all-time balance, expense and income. */
    val allTime = MutableLiveData(Triple(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO))

    fun refreshAllTime() {
        val recs = Store.allRecords()
        fun sum(type: Int) = recs.filter { it.type == type }.fold(BigDecimal.ZERO) { a, r -> a + r.amount }
        allTime.value = Triple(Store.accounts().fold(BigDecimal.ZERO) { a, x -> a + x.balance }, sum(EXPENSE), sum(INCOME))
    }

    /** Budgets tab: always a single month. */
    val budgetAnchor = MutableLiveData(System.currentTimeMillis())
    val budgetTotals = MutableLiveData(BigDecimal.ZERO to BigDecimal.ZERO) // limit to spent

    fun shiftBudget(dir: Int) { budgetAnchor.value = Period.MONTH.shift(budgetAnchor.value!!, dir) }

    fun shift(dir: Int) {
        val w = window.value!!
        window.value = w.copy(anchor = w.period.shift(w.anchor, dir))
    }

    fun setPeriod(p: Period) {
        Prefs.period = p
        window.value = window.value!!.copy(period = p)
    }
}
