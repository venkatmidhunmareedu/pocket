package app.pocket.ui

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Keypad expression buffer. Internal operators are '+', '-', '*', '/'; * and / bind tighter than + and -.
 * evaluate() returns null for an empty/invalid expression or division by zero.
 */
class Calc {
    private val sb = StringBuilder()
    val text: String get() = sb.toString()
    val hasOperator: Boolean get() = sb.any { it in OPS }

    private fun currentNumber(): String {
        val i = sb.indexOfLast { it in OPS }
        return sb.substring(i + 1)
    }

    fun digit(c: Char) {
        if (currentNumber() == "0") sb.setLength(sb.length - 1)
        sb.append(c)
    }

    fun dot() {
        val cur = currentNumber()
        if ('.' in cur) return
        if (cur.isEmpty()) sb.append('0')
        sb.append('.')
    }

    fun op(c: Char) {
        if (sb.isEmpty()) return
        if (sb.last() in OPS) sb.setLength(sb.length - 1)
        else if (sb.last() == '.') sb.setLength(sb.length - 1)
        sb.append(c)
    }

    fun backspace() { if (sb.isNotEmpty()) sb.setLength(sb.length - 1) }
    fun clear() { sb.setLength(0) }

    fun set(v: String) { sb.setLength(0); sb.append(v) }

    /** Collapses the expression to its result, if valid. */
    fun equals() { evaluate()?.let { set(it.stripTrailingZeros().toPlainString()) } }

    fun evaluate(): BigDecimal? {
        var s = sb.toString()
        if (s.isNotEmpty() && s.last() in OPS) s = s.dropLast(1)
        if (s.isEmpty()) return null
        val nums = ArrayList<BigDecimal>()
        val ops = ArrayList<Char>()
        var cur = StringBuilder()
        for (ch in s) {
            if (ch in OPS) {
                nums += cur.toString().toBigDecimalOrNull() ?: return null
                cur = StringBuilder()
                ops += ch
            } else cur.append(ch)
        }
        nums += cur.toString().toBigDecimalOrNull() ?: return null

        val n2 = arrayListOf(nums[0])
        val o2 = ArrayList<Char>()
        for (i in ops.indices) {
            val rhs = nums[i + 1]
            when (ops[i]) {
                '*' -> n2[n2.lastIndex] = n2.last() * rhs
                '/' -> {
                    if (rhs.signum() == 0) return null
                    n2[n2.lastIndex] = n2.last().divide(rhs, 10, RoundingMode.HALF_UP)
                }
                else -> { o2 += ops[i]; n2 += rhs }
            }
        }
        var r = n2[0]
        for (i in o2.indices) r = if (o2[i] == '+') r + n2[i + 1] else r - n2[i + 1]
        return r
    }

    companion object { private const val OPS = "+-*/" }
}
