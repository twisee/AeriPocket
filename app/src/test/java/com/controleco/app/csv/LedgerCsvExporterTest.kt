package com.controleco.app.csv

import com.controleco.app.data.db.*
import org.junit.Assert.*
import org.junit.Test
import java.io.StringWriter
import java.time.ZoneId

class LedgerCsvExporterTest {
    private fun tx(amount: Long = 3500, type: TxType = TxType.EXPENSE) =
        TransactionEntity(type = type, amountCents = amount, categoryId = 1, accountId = 1, timestamp = 0,
            counterparty = "商户,分店\"A\"", note = "第一行\n第二行")
    private fun export(vararg rows: TransactionEntity): Pair<String, List<List<String>>> {
        val out = StringWriter()
        LedgerCsvExporter.write(LedgerExportSnapshot(rows.toList(), mapOf(1L to "餐饮")), out, ZoneId.of("Asia/Shanghai"))
        return out.toString() to CsvReader.parse(out.toString())
    }
    @Test fun `unicode quoting multiline and exact decimals round trip`() {
        val (text, rows) = export(tx())
        assertTrue(text.startsWith("\uFEFF"))
        assertEquals(LedgerCsvExporter.headers, rows.first())
        assertEquals("商户,分店\"A\"", rows[1][4])
        assertEquals("第一行\n第二行", rows[1][5])
        assertEquals("1970-01-01 08:00:00", rows[1][0])
        assertEquals("35.00", rows[1][3])
        assertEquals("餐饮", rows[1][2])
        assertEquals(12, rows[1].size)
    }
    @Test fun `refund events do not double count and original gross net remain distinct`() {
        val original = tx(10000).copy(refundedCents = 2500, aaShares = 2, budgetExcluded = true)
        val event = tx(2500, TxType.INCOME).copy(refundMark = RefundMark.REFUND_INCOME)
        val rows = export(original, event).second
        assertEquals(2, rows.size)
        assertEquals("100.00", rows[1][3])
        assertEquals("25.00", rows[1][7])
        assertEquals("75.00", rows[1][8])
        assertEquals("已部分退款", rows[1][9])
        assertEquals("2", rows[1][10])
        assertEquals("否", rows[1][11])
    }
    @Test fun `full refund is not ordinary income and no rounding loss`() {
        val rows = export(tx(1).copy(refundedCents = 1, refundMark = RefundMark.REFUNDED_EXPENSE), tx(123456789, TxType.INCOME)).second
        assertEquals("0.00", rows[1][8])
        assertEquals("已全额退款", rows[1][9])
        assertEquals("收入", rows[2][1])
        assertEquals("1234567.89", rows[2][8])
    }
    @Test fun `legacy full refund flag exports zero net even without amount backfill`() {
        val rows = export(tx(3500).copy(refundMark = RefundMark.REFUNDED_EXPENSE)).second
        assertEquals("35.00", rows[1][7])
        assertEquals("0.00", rows[1][8])
    }
}
