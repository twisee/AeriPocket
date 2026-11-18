package com.controleco.app.data.budget

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ExperienceVaultTest {
    private val nov = YearMonth.of(2026, 11)

    @Test fun activationDoesNotBackfillAndOnlyGrantsOnce() {
        val active = ExperienceVault().configure(nov, true, 30000)
        assertEquals(mapOf(nov to 30000L), active.grants)
        assertEquals(active, active.advance(nov))
        assertEquals(30000L, active.configure(nov, false, 30000).configure(nov, true, 30000).available(0))
    }

    @Test fun unopenedMonthsAccumulateAndEditsStartNextPeriod() {
        val active = ExperienceVault().configure(nov, true, 30000).configure(nov, true, 50000)
        assertEquals(30000L, active.grants[nov])
        assertEquals(50000L, active.nextMonthlyCents)
        val feb = active.advance(nov.plusMonths(3))
        assertEquals(180000L, feb.available(0))
        assertEquals(50000L, feb.monthlyCents)
        assertNull(feb.nextMonthlyCents)
        assertEquals(feb, feb.advance(nov.plusMonths(3)))
    }

    @Test fun disabledMonthsDoNotAccumulateAndOldGrantsSurvive() {
        val off = ExperienceVault().configure(nov, true, 30000).configure(nov, false, 30000)
        val resumed = off.configure(nov.plusMonths(3), true, 40000)
        assertEquals(mapOf(nov to 30000L, nov.plusMonths(3) to 40000L), resumed.grants)
        assertEquals(70000L, resumed.available(0))
    }

    @Test fun clearKeepsHistoryAndRefundsAndNewGrantsStillWork() {
        val active = ExperienceVault().configure(nov, true, 30000)
        val clear = active.clear(12000)
        assertEquals(active.grants, clear.grants)
        assertEquals(0L, clear.available(12000))
        assertEquals(2000L, clear.available(10000))
        assertEquals(30000L, clear.advance(nov.plusMonths(1)).available(12000))
        assertEquals(clear, clear.clear(12000))
    }

    @Test fun negativeBalancesCarryAndCanAlsoBeCleared() {
        val active = ExperienceVault().configure(nov, true, 30000)
        assertEquals(-10000L, active.available(40000))
        assertEquals(20000L, active.advance(nov.plusMonths(1)).available(40000))
        assertEquals(0L, active.clear(40000).available(40000))
    }

    @Test fun accountingMonthUsesConfiguredFirstDayAndCannotGrantTwiceAfterClockRollback() {
        val period = MonthPeriod.of(LocalDate.of(2026, 12, 5), 10)
        assertEquals(nov, YearMonth.from(period.start))
        val active = ExperienceVault().configure(nov, true, 30000).advance(nov.plusMonths(1))
        assertEquals(active, active.advance(nov))
        assertEquals(active, active.advance(nov.plusMonths(1)))
    }
}
