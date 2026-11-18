package com.controleco.app.ui.profile

import com.controleco.app.data.db.RefundMark
import com.controleco.app.data.db.TransactionRow
import com.controleco.app.data.db.TxSource
import com.controleco.app.data.db.TxType
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class SpendingTrendDataTest {
    @Test fun comparesOnlySharedDaysWhenPreviousMonthIsShorter() {
        val periods = monthlyTrendPeriods(YearMonth.of(2026, 3), LocalDate.of(2026, 3, 31), 1)
        val data = spendingTrendData(
            listOf(
                row("2026-03-28", 1234, refunded = 1234),
                row("2026-03-29", 2000, categoryId = 2),
                row("2026-02-28", 1000),
                row("2026-02-28", 600, type = TxType.INCOME),
            ),
            periods,
            ZoneOffset.UTC,
        )

        assertEquals(31, data.current.size)
        assertEquals(28, data.previous.size)
        assertEquals(3234L, data.current.sum())
        assertEquals(1000L, data.previous.sum())
        assertEquals(234L, data.change)
        assertEquals(listOf(SpendingCategoryChange("餐饮", 234)), data.categoryChanges)
    }

    @Test fun pastShortMonthUsesActualDaysAndComparesBothFullMonths() {
        val periods = monthlyTrendPeriods(YearMonth.of(2026, 2), LocalDate.of(2026, 11, 18), 1)
        val data = spendingTrendData(
            listOf(
                row("2026-02-28", 1200),
                row("2026-01-31", 500),
            ),
            periods,
            ZoneOffset.UTC,
        )

        assertEquals(28, data.current.size)
        assertEquals(31, periods.axisDays)
        assertEquals(31, data.previous.size)
        assertEquals(null, data.current.getOrNull(30))
        assertEquals(1200L, data.current.last())
        assertEquals(500L, data.previous.last())
        assertEquals(700L, data.change)
    }

    @Test fun currentAccountingMonthPlotsAllOfPreviousMonthButComparesOnlyElapsedDays() {
        val periods = monthlyTrendPeriods(YearMonth.of(2025, 12), LocalDate.of(2026, 1, 3), 26)
        val data = spendingTrendData(
            listOf(
                row("2026-01-03", 1200),
                row("2026-01-04", 9000),
                row("2025-12-04", 500),
                row("2025-12-05", 8000),
            ),
            periods,
            ZoneOffset.UTC,
        )

        assertEquals(LocalDate.of(2025, 12, 26), periods.current.start)
        assertEquals(9, data.current.size)
        assertEquals(31, periods.axisDays)
        assertEquals(8500L, data.previous.sum())
        assertEquals(500L, data.comparablePrevious)
        assertEquals(700L, data.change)
    }

    @Test fun selectedSevenDaysCrossMonthAndIgnoreLaterSpending() {
        val periods = weeklyTrendPeriods(LocalDate.of(2026, 3, 3))
        val data = spendingTrendData(
            listOf(row("2026-02-24", 700), row("2026-02-25", 800), row("2026-03-03", 1200), row("2026-03-04", 9000)),
            periods,
            ZoneOffset.UTC,
        )

        assertEquals(LocalDate.of(2026, 2, 25), periods.current.start)
        assertEquals(7, periods.axisDays)
        assertEquals(listOf(800L, 0L, 0L, 0L, 0L, 0L, 1200L), data.current)
        assertEquals(700L, data.previous.last())
        assertEquals(1300L, data.change)
    }

    private fun row(
        date: String,
        cents: Long,
        refunded: Long = 0,
        categoryId: Long = 1,
        type: TxType = TxType.EXPENSE,
    ) = TransactionRow(
        id = 1,
        type = type,
        amountCents = cents,
        timestamp = LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        note = "",
        counterparty = "",
        source = TxSource.MANUAL,
        categoryId = categoryId,
        categoryName = if (categoryId == 1L) "餐饮" else "购物",
        accountId = 1,
        accountName = "",
        refundMark = if (refunded == cents) RefundMark.REFUNDED_EXPENSE else RefundMark.NONE,
        refundedCents = refunded,
    )
}
