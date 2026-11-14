package com.controleco.app.ui.transactions

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/** Decimal calculator for entry amounts. Only the final result is rounded to cents. */
internal data class AmountCalculator(
    val input: String = "",
    val completedExpression: String? = null,
    val isResult: Boolean = false,
) {
    val result: AmountCalculation get() = evaluate(input)
    val formula: String?
        get() = (completedExpression ?: input.takeIf { it.any(::isOperator) })
            ?.map { if (isOperator(it)) " $it " else it.toString() }?.joinToString("")
    val displayAmount: String
        get() {
            if (completedExpression != null || input.none(::isOperator)) return input
            // An unfinished operator keeps the previous result visible, but cannot be saved.
            val display = if (input.lastOrNull()?.let(::isOperator) == true) evaluate(input.dropLast(1)) else result
            return display.cents?.let(::formatCents) ?: ""
        }

    fun press(key: String): CalculatorEdit {
        if (key == "C") return CalculatorEdit(AmountCalculator())
        if (key == "⌫") return CalculatorEdit(copy(input = input.dropLast(1), completedExpression = null, isResult = false))
        if (key == "=") {
            val calculated = result
            return if (calculated.cents != null && calculated.cents > 0) {
                CalculatorEdit(AmountCalculator(formatCents(calculated.cents), completedExpression ?: input.takeIf { it.any(::isOperator) }, isResult = true))
            } else CalculatorEdit(this, calculated.error ?: "金额须大于零")
        }
        if (key.length == 1 && isOperator(key[0])) {
            if (input.isEmpty()) return CalculatorEdit(this)
            val next = if (isOperator(input.last())) input.dropLast(1) + key else input + key
            return bounded(next)
        }
        if (key != "." && (key.length != 1 || key[0] !in '0'..'9')) return CalculatorEdit(this)
        val base = if (isResult) "" else input
        val split = base.indexOfLast(::isOperator) + 1
        val prefix = base.take(split)
        val operand = base.substring(split)
        val nextOperand = when {
            key == "." && '.' in operand -> return CalculatorEdit(this)
            key == "." && operand.isEmpty() -> "0."
            operand == "0" && key != "." -> key
            else -> operand + key
        }
        if (nextOperand.substringBefore('.').length > 9) return CalculatorEdit(this, "金额最多 9 位整数")
        if (nextOperand.substringAfter('.', "").length > 2) return CalculatorEdit(this, "最多两位小数")
        return bounded(prefix + nextOperand)
    }

    private fun bounded(next: String): CalculatorEdit =
        if (next.length > 120) CalculatorEdit(this, "算式过长，请先计算")
        else CalculatorEdit(AmountCalculator(next))

    companion object {
        private fun isOperator(c: Char) = c in "+−×÷"
        private val maximum = BigDecimal("999999999.99")
        private val operandPattern = Regex("[0-9]{1,9}(\\.[0-9]{0,2})?")

        fun evaluate(expression: String): AmountCalculation {
            if (expression.isEmpty()) return AmountCalculation(error = "请输入有效金额")
            if (expression.length > 120) return AmountCalculation(error = "算式过长，请先计算")
            val operands = expression.split('+', '−', '×', '÷')
            if (operands.any { !operandPattern.matches(it) }) return AmountCalculation(error = "请补全算式")
            val operators = expression.filter(::isOperator)
            try {
                var sum = Fraction(BigInteger.ZERO)
                var term = Fraction.from(operands.first())
                var sign = 1
                operators.forEachIndexed { index, op ->
                    val next = Fraction.from(operands[index + 1])
                    when (op) {
                        '×' -> term = term * next
                        '÷' -> {
                            if (next.numerator.signum() == 0) return AmountCalculation(error = "不能除以零")
                            term = term / next
                        }
                        else -> {
                            sum += if (sign > 0) term else -term
                            term = next
                            sign = if (op == '+') 1 else -1
                        }
                    }
                }
                val exact = sum + if (sign > 0) term else -term
                val total = BigDecimal(exact.numerator).divide(BigDecimal(exact.denominator), 2, RoundingMode.HALF_UP)
                if (total.signum() < 0) return AmountCalculation(error = "金额不能为负数")
                if (total > maximum) return AmountCalculation(error = "金额最多 9 位整数")
                return AmountCalculation(cents = total.movePointRight(2).longValueExact())
            } catch (_: ArithmeticException) {
                return AmountCalculation(error = "请输入有效算式")
            }
        }

        private fun formatCents(cents: Long): String = BigDecimal.valueOf(cents, 2).toPlainString()
    }
}

internal data class AmountCalculation(val cents: Long? = null, val error: String? = null)
internal data class CalculatorEdit(val state: AmountCalculator, val error: String? = null)

/** Exact intermediate fractions prevent division/cancellation from changing half-cent rounding. */
private data class Fraction(val numerator: BigInteger, val denominator: BigInteger = BigInteger.ONE) {
    operator fun plus(other: Fraction) = Fraction(numerator * other.denominator + other.numerator * denominator, denominator * other.denominator)
    operator fun times(other: Fraction) = Fraction(numerator * other.numerator, denominator * other.denominator)
    operator fun div(other: Fraction) = Fraction(numerator * other.denominator, denominator * other.numerator)
    operator fun unaryMinus() = Fraction(-numerator, denominator)

    companion object {
        fun from(text: String): Fraction {
            val decimal = BigDecimal(text)
            return Fraction(decimal.unscaledValue(), BigInteger.TEN.pow(decimal.scale()))
        }
    }
}
