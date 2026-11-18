package com.controleco.app.ui.profile

import com.controleco.app.data.budget.MonthPeriod
import com.controleco.app.data.db.TransactionRow
import com.controleco.app.data.db.TxType
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

internal data class SpendingTrendPeriods(
    val cumulative: Boolean,
    val current: MonthPeriod,
    val previous: MonthPeriod,
    val comparisonDays: Int?,
    val axisDays: Int,
)

internal const val TREND_WEEK_DAYS = 7

internal fun weeklyTrendPeriods(endDay: LocalDate): SpendingTrendPeriods {
    val end = endDay.plusDays(1)
    val start = end.minusDays(TREND_WEEK_DAYS.toLong())
    return SpendingTrendPeriods(
        cumulative = false,
        current = MonthPeriod(start, end),
        previous = MonthPeriod(start.minusDays(TREND_WEEK_DAYS.toLong()), start),
        comparisonDays = null,
        axisDays = TREND_WEEK_DAYS,
    )
}

internal fun monthlyTrendPeriods(month: YearMonth, today: LocalDate, startDay: Int): SpendingTrendPeriods {
    val selected = MonthPeriod.of(month, startDay)
    val previous = MonthPeriod.of(month.minusMonths(1), startDay)
    val current = selected.copy(endExclusive = minOf(selected.endExclusive, today.plusDays(1)))
    return SpendingTrendPeriods(
        cumulative = true,
        current = current,
        previous = previous,
        comparisonDays = if (selected.contains(today)) minOf(current.days, previous.days) else null,
        axisDays = maxOf(selected.days, previous.days),
    )
}

internal data class SpendingCategoryChange(val name: String, val cents: Long)

internal data class SpendingTrendData(
    val current: List<Long>,
    val previous: List<Long>,
    val comparableCurrent: Long,
    val comparablePrevious: Long,
    val categoryChanges: List<SpendingCategoryChange>,
) {
    val change: Long get() = comparableCurrent - comparablePrevious
}

private data class TrendBucketTotals(
    val cents: List<Long>,
    val categories: Map<Long, SpendingCategoryChange>,
)

internal fun spendingTrendData(
    rows: List<TransactionRow>,
    periods: SpendingTrendPeriods,
    zone: ZoneId = ZoneId.systemDefault(),
): SpendingTrendData {
    val current = accumulateSpending(rows, periods.current, zone)
    val previous = accumulateSpending(rows, periods.previous, zone)
    val comparableCurrent = comparisonTotals(rows, periods.current, current, periods.comparisonDays, zone)
    val comparablePrevious = comparisonTotals(rows, periods.previous, previous, periods.comparisonDays, zone)
    val changes = (comparableCurrent.categories.keys + comparablePrevious.categories.keys).mapNotNull { id ->
        val now = comparableCurrent.categories[id]
        val before = comparablePrevious.categories[id]
        val change = (now?.cents ?: 0L) - (before?.cents ?: 0L)
        if (change == 0L) null else SpendingCategoryChange((now ?: requireNotNull(before)).name, change)
    }.sortedWith(compareByDescending<SpendingCategoryChange> { kotlin.math.abs(it.cents) }.thenBy { it.name })
    return SpendingTrendData(current.cents, previous.cents, comparableCurrent.cents.sum(), comparablePrevious.cents.sum(), changes)
}

internal fun spendSeries(
    rows: List<TransactionRow>,
    period: MonthPeriod,
    zone: ZoneId = ZoneId.systemDefault(),
): List<Long> = accumulateSpending(rows, period, zone).cents

private fun comparisonTotals(
    rows: List<TransactionRow>,
    period: MonthPeriod,
    totals: TrendBucketTotals,
    comparisonDays: Int?,
    zone: ZoneId,
): TrendBucketTotals =
    if (comparisonDays == null || comparisonDays == period.days) totals
    else accumulateSpending(rows, period.copy(endExclusive = period.start.plusDays(comparisonDays.toLong())), zone)

private fun accumulateSpending(
    rows: List<TransactionRow>,
    period: MonthPeriod,
    zone: ZoneId,
): TrendBucketTotals {
    val buckets = LongArray(period.days)
    val categories = mutableMapOf<Long, SpendingCategoryChange>()
    // 「本期支出」使用原付款金额，退款入账和预算分摊不冲减该金额。
    for (row in rows) {
        if (row.type != TxType.EXPENSE || row.amountCents <= 0) continue
        val date = Instant.ofEpochMilli(row.timestamp).atZone(zone).toLocalDate()
        if (!period.contains(date)) continue
        buckets[ChronoUnit.DAYS.between(period.start, date).toInt()] += row.amountCents
        val total = (categories[row.categoryId]?.cents ?: 0L) + row.amountCents
        categories[row.categoryId] = SpendingCategoryChange(row.categoryName.ifBlank { "未分类" }, total)
    }
    return TrendBucketTotals(buckets.toList(), categories)
}
