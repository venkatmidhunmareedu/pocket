package app.pocket.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Calendar.DAY_OF_MONTH
import java.util.Calendar.DAY_OF_WEEK
import java.util.Calendar.DAY_OF_YEAR
import java.util.Calendar.HOUR_OF_DAY
import java.util.Calendar.MILLISECOND
import java.util.Calendar.MINUTE
import java.util.Calendar.SECOND
import java.util.Locale

/** View window for Records/Analysis: a [start, end) range around an anchor timestamp. */
enum class Period(val title: String, val emptyMessage: String, val analysisEmpty: String) {
    DAY("Daily", "No record on this day.\nTap + to add new expense or income.", "No analysis on this day"),
    WEEK("Weekly", "No record in this week. Tap + to add new expense or income.", "No analysis in this week"),
    MONTH("Monthly", "No record in this month. Tap + to add new expense or income.", "No analysis for this month"),
    MONTHS3("3 Months", "No record during these months. Tap + to add new expense or income.", "No analysis in this time period"),
    MONTHS6("6 Months", "No record during these months. Tap + to add new expense or income.", "No analysis in this time period"),
    YEAR("Yearly", "No record in this year. Tap + to add new expense or income.", "No analysis for this year");

    private fun cal(t: Long) = Calendar.getInstance().apply { timeInMillis = t }

    fun start(anchor: Long): Long {
        val c = cal(anchor)
        c.set(HOUR_OF_DAY, 0); c.set(MINUTE, 0); c.set(SECOND, 0); c.set(MILLISECOND, 0)
        when (this) {
            DAY -> {}
            WEEK -> while (c.get(DAY_OF_WEEK) != c.firstDayOfWeek) c.add(DAY_OF_YEAR, -1)
            MONTH -> c.set(DAY_OF_MONTH, 1)
            MONTHS3 -> { c.set(DAY_OF_MONTH, 1); c.add(Calendar.MONTH, -2) }
            MONTHS6 -> { c.set(DAY_OF_MONTH, 1); c.add(Calendar.MONTH, -5) }
            YEAR -> c.set(DAY_OF_YEAR, 1)
        }
        return c.timeInMillis
    }

    fun end(anchor: Long): Long {
        val c = cal(start(anchor))
        when (this) {
            DAY -> c.add(DAY_OF_YEAR, 1)
            WEEK -> c.add(DAY_OF_YEAR, 7)
            MONTH -> c.add(Calendar.MONTH, 1)
            MONTHS3 -> c.add(Calendar.MONTH, 3)
            MONTHS6 -> c.add(Calendar.MONTH, 6)
            YEAR -> c.add(Calendar.YEAR, 1)
        }
        return c.timeInMillis
    }

    fun shift(anchor: Long, dir: Int): Long {
        val c = cal(anchor)
        when (this) {
            DAY -> c.add(DAY_OF_YEAR, dir)
            WEEK -> c.add(DAY_OF_YEAR, 7 * dir)
            MONTH -> c.add(Calendar.MONTH, dir)
            MONTHS3 -> c.add(Calendar.MONTH, 3 * dir)
            MONTHS6 -> c.add(Calendar.MONTH, 6 * dir)
            YEAR -> c.add(Calendar.YEAR, dir)
        }
        return c.timeInMillis
    }

    fun label(anchor: Long): String {
        val s = start(anchor)
        val last = end(anchor) - 1
        fun f(p: String, t: Long) = SimpleDateFormat(p, Locale.getDefault()).format(t)
        val sameYear = f("yyyy", s) == f("yyyy", last)
        return when (this) {
            DAY -> f("EEE, d MMM yyyy", s)
            WEEK -> if (sameYear) "${f("d MMM", s)} – ${f("d MMM yyyy", last)}" else "${f("d MMM yyyy", s)} – ${f("d MMM yyyy", last)}"
            MONTH -> f("MMMM yyyy", s)
            MONTHS3, MONTHS6 -> if (sameYear) "${f("MMM", s)} – ${f("MMM yyyy", last)}" else "${f("MMM yyyy", s)} – ${f("MMM yyyy", last)}"
            YEAR -> f("yyyy", s)
        }
    }
}
