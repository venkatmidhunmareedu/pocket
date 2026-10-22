package app.pocket.ui

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CalcTest {
    private fun calc(vararg keys: Char) = Calc().apply {
        for (k in keys) when (k) {
            in '0'..'9' -> digit(k)
            '.' -> dot()
            '<' -> backspace()
            else -> op(k)
        }
    }

    @Test fun precedence() = assertEquals(BigDecimal("14"), calc('2', '+', '3', '*', '4').evaluate())
    @Test fun division() = assertEquals(BigDecimal("2.5000000000"), calc('1', '0', '/', '4').evaluate())
    @Test fun divideByZero() = assertNull(calc('1', '/', '0').evaluate())
    @Test fun trailingOperatorIgnored() = assertEquals(BigDecimal("7"), calc('7', '+').evaluate())
    @Test fun empty() = assertNull(Calc().evaluate())
    @Test fun leadingZeroCollapses() = assertEquals("5", calc('0', '5').text)
    @Test fun dotOnlyOncePerNumber() = assertEquals("1.5+0.2", calc('1', '.', '5', '.', '+', '.', '2').text)
    @Test fun operatorReplaced() = assertEquals("3*", calc('3', '+', '*').text)
    @Test fun backspace() = assertEquals("12", calc('1', '2', '3', '<').text)
    @Test fun equalsCollapses() = Calc().run { set("2+2"); equals(); assertEquals("4", text) }
}
