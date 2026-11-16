package com.controleco.app.ui.stats

import com.controleco.app.data.PeriodMode
import com.controleco.app.data.db.*
import org.junit.Assert.*
import org.junit.Test

class OverviewCategoriesTest {
    private val categories = listOf(CategoryEntity(id=1, name="餐饮", type=TxType.EXPENSE),
        CategoryEntity(id=2, name="日常", type=TxType.EXPENSE), CategoryEntity(id=3, name="购物", type=TxType.EXPENSE))
    private fun sum(id: Long, amount: Long) = CategorySumRow(id, categories.first { it.id == id }.name, amount)

    @Test fun budgetAndSpendingUseDifferentConsistentTotalsAndShares() {
        val data = overviewCategories(mapOf(1L to 10000, 2L to 10000, 3L to 30000), categories,
            listOf(sum(1,18000), sum(2,8000), sum(3,4000)),
            listOf(sum(1,12000), sum(2,4000), sum(3,2000)), PeriodMode.MONTH)
        assertEquals(18000L, data.budgetUsedCents)
        assertEquals(30000L, data.spendingCents)
        assertEquals(12000L, data.excludedCents)
        assertEquals(50000L, data.budgetCents)
        assertEquals(6000L, data.excludedByName["餐饮"])
        assertEquals("120", categoryPercent(data.budgetRows.first().actualCents, data.budgetRows.first().budgetCents))
        assertEquals("60", categoryPercent(data.spendingRows.first().actualCents, data.spendingCents))
    }

    @Test fun excludedOnlyCategoryStaysInSpendingAndDoesNotConsumeUnconfiguredBudget() {
        val data = overviewCategories(mapOf(1L to 10000), categories,
            listOf(sum(2,5000)), emptyList(), PeriodMode.MONTH)
        assertEquals(listOf("餐饮"), data.budgetRows.map { it.name })
        assertEquals(0L, data.budgetUsedCents)
        assertEquals(5000L, data.excludedCents)
        assertEquals(listOf("日常"), data.spendingRows.map { it.name })
        assertEquals("100", categoryPercent(5000, data.spendingCents))
    }

    @Test fun spendingWithoutBudgetHasNoInventedBudgetAndYearScalesOnlyLimits() {
        val data = overviewCategories(mapOf(1L to 10000), categories,
            listOf(sum(2,5000)), listOf(sum(2,5000)), PeriodMode.YEAR)
        assertEquals(120000L, data.budgetCents)
        assertEquals(5000L, data.budgetUsedCents)
        assertEquals(0L, data.budgetRows.first { it.name == "日常" }.budgetCents)
        assertEquals(0L, data.excludedCents)
    }

    @Test fun percentageKeepsOverageAndFractionAndHandlesEmptyTotals() {
        assertEquals("120", categoryPercent(12000,10000))
        assertEquals("100", categoryPercent(10000,10000))
        assertEquals("6.7", categoryPercent(2000,30000))
        assertEquals("0", categoryPercent(0,0))
        assertEquals("0", categoryPercent(100,0))
    }
}
