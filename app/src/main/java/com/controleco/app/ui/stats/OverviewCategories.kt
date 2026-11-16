package com.controleco.app.ui.stats

import com.controleco.app.data.PeriodMode
import com.controleco.app.data.db.CategoryEntity
import com.controleco.app.data.db.CategorySumRow
import java.util.Locale

internal data class OverviewCategories(
    val budgetRows: List<BudgetRatioRow>,
    val spendingRows: List<BudgetRatioRow>,
    val budgetUsedCents: Long,
    val spendingCents: Long,
    val excludedCents: Long,
    val budgetCents: Long,
    val excludedByName: Map<String, Long>,
)

/** Keep each view's amounts, totals and chart on the same accounting basis. */
internal fun overviewCategories(
    budgets: Map<Long, Long>,
    categories: List<CategoryEntity>,
    spending: List<CategorySumRow>,
    budgetSpending: List<CategorySumRow>,
    period: PeriodMode,
): OverviewCategories {
    val budgetRows = buildRatioRows(budgets, categories, budgetSpending, period)
    val spendingRows = buildRatioRows(emptyMap(), categories, spending, period)
    val used = budgetRows.sumOf { it.actualCents }
    val total = spendingRows.sumOf { it.actualCents }
    val usedByName = budgetRows.associate { it.name to it.actualCents }
    val excludedByName = spendingRows.associate { it.name to (it.actualCents - (usedByName[it.name] ?: 0)).coerceAtLeast(0) }
    return OverviewCategories(budgetRows, spendingRows, used, total, (total - used).coerceAtLeast(0),
        budgetRows.sumOf { it.budgetCents }, excludedByName)
}

/** Percentages keep one decimal where needed; over-budget values remain above 100. */
internal fun categoryPercent(part: Long, total: Long): String =
    if (total <= 0L) "0" else String.format(Locale.ROOT, "%.1f", part.toDouble() / total * 100).removeSuffix(".0")
