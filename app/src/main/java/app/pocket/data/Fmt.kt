package app.pocket.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object Fmt {
    fun money(v: BigDecimal): String {
        val d = Prefs.decimals
        val f = DecimalFormat("#,##0" + if (d > 0) "." + "0".repeat(d) else "", DecimalFormatSymbols(Locale.US))
        f.roundingMode = RoundingMode.HALF_UP
        val s = f.format(v.abs())
        val body = when (Prefs.currencyPosition) {
            "end" -> "$s${Prefs.currencySymbol}"
            "none" -> s
            else -> "${Prefs.currencySymbol}$s"
        }
        return if (v.signum() < 0) "-$body" else body
    }

    /** Amount without currency or grouping, for editing. */
    fun plain(v: BigDecimal): String = v.stripTrailingZeros().toPlainString()
}
