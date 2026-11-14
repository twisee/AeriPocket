package com.controleco.app.ui.transactions

import org.junit.Assert.*
import org.junit.Test

class AmountCalculatorTest {
    private fun enter(vararg keys: String): AmountCalculator = keys.fold(AmountCalculator()) { state, key -> state.press(key).state }

    @Test fun precedenceAndDecimalAdditionHaveNoBinaryRounding() {
        assertEquals(4250L, AmountCalculator.evaluate("18.5+12×2").cents)
        assertEquals(30L, AmountCalculator.evaluate("0.1+0.2").cents)
        assertEquals(1400L, AmountCalculator.evaluate("20−9÷3×2").cents)
        assertEquals(300L, AmountCalculator.evaluate("1−2+4").cents)
    }

    @Test fun roundOnlyFinalResultToCents() {
        assertEquals(100L, AmountCalculator.evaluate("1÷3×3").cents)
        assertEquals(17L, AmountCalculator.evaluate("1÷6").cents)
        assertEquals(1L, AmountCalculator.evaluate("1÷200").cents)
        assertEquals(101L, AmountCalculator.evaluate("2.01÷2").cents)
    }

    @Test fun invalidAndOutOfRangeResultsCannotBecomeSavedAmounts() {
        for (expression in listOf("1÷0", "1−2", "12+", "", "999999999.99+0.01", "1.234", "1000000000")) {
            assertNull(expression, AmountCalculator.evaluate(expression).cents)
        }
        assertEquals("不能除以零", AmountCalculator.evaluate("2÷0").error)
        assertEquals(99999999999L, AmountCalculator.evaluate("999999999.99").cents)
        assertEquals(0L, AmountCalculator.evaluate("1−1").cents)
        assertEquals("金额须大于零", enter("1", "−", "1").press("=").error)
    }

    @Test fun operatorReplacementAndDecimalEntry() {
        val state = enter(".", "5", "+", "×", ".", "2")
        assertEquals("0.5×0.2", state.input)
        assertEquals(10L, state.result.cents)
        assertEquals("0.5×0.2", state.press(".").state.input)
        assertEquals("最多两位小数", enter("1", ".", "2", "3").press("4").error)
        assertEquals("金额最多 9 位整数", enter(*Array(9) { "9" }).press("9").error)
    }

    @Test fun equalsRetainsFormulaAndStartsFreshNumberOrContinuesCalculation() {
        val done = enter("1", "8", ".", "5", "+", "1", "2", "×", "2", "=")
        assertEquals("42.50", done.displayAmount)
        assertEquals("18.5 + 12 × 2", done.formula)
        assertEquals("7", done.press("7").state.input)
        val continued = done.press("+").state.press("1").state
        assertEquals(4350L, continued.result.cents)
        assertEquals("42.5", done.press("⌫").state.input)
        assertEquals("7", enter("2", "=").press("7").state.input)
        assertEquals(AmountCalculator(), done.press("C").state)
    }

    @Test fun unfinishedOperatorDisplaysPreviousResultButCannotBeSaved() {
        val pending = enter("1", "+", "2", "×")
        assertEquals("3.00", pending.displayAmount)
        assertNull(pending.result.cents)
        assertEquals("请补全算式", pending.press("=").error)
        assertEquals(300L, pending.press("⌫").state.result.cents)
        assertEquals(1200L, enter("1", "2", ".").result.cents)
    }
}
