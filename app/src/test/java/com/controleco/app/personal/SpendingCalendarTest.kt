package com.controleco.app.personal

import com.controleco.app.ui.stats.*
import com.controleco.app.data.db.*
import java.time.*
import org.junit.Test
import org.junit.Assert.*

class SpendingCalendarTest {
    private val zone=ZoneId.of("Asia/Shanghai")
    private fun row(id:Long,date:String,amount:Long,refund:Long=0,aa:Int=0)=TransactionRow(id=id,type=TxType.EXPENSE,
        amountCents=amount,timestamp=LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli(),note="",counterparty="合成商户",
        source=TxSource.MANUAL,categoryId=1,categoryName="购物",accountId=1,accountName="现金",refundMark=RefundMark.NONE,refundedCents=refund,aaShares=aa)
    @Test fun leapMonthAndRefundAaTotalsMatchExistingChart() {
        val rows=listOf(row(1,"2028-02-01",10000,2000),row(2,"2028-02-01",2000,aa=2),row(3,"2028-02-29",5000,5000),row(4,"2028-03-01",6000))
        val days=calendarDays(rows,YearMonth.of(2028,2),1,zone)
        assertEquals(29,days.size);assertEquals(LocalDate.parse("2028-02-01"),days.first().date)
        assertEquals(9000L,days.first().cents);assertEquals(0L,days.last().cents)
        assertEquals(ChartMath.monthBars(rows,YearMonth.of(2028,2),1,zone).sumOf { it.cents },days.sumOf { it.cents })
    }
    @Test fun accountingMonthStillIncludesItsCrossMonthDays() {
        val days=calendarDays(listOf(row(1,"2026-12-02",1800)),YearMonth.of(2026,11),5,zone)
        assertEquals(LocalDate.parse("2026-11-05"),days.first().date)
        assertEquals(LocalDate.parse("2026-12-04"),days.last().date)
        assertEquals(1800L,days.sumOf { it.cents })
    }
    @Test fun darkerCellsRepresentLargerPositiveAmounts() {
        assertEquals(0f,calendarHeat(0,10000),0f)
        assertTrue(calendarHeat(1000,10000)<calendarHeat(5000,10000))
        assertEquals(1f,calendarHeat(10000,10000),0f)
    }
}
