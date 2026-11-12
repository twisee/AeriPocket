package com.controleco.app.personal

import com.controleco.app.csv.BillParsers
import com.controleco.app.data.RefundReconciler
import com.controleco.app.data.db.*
import java.io.File
import java.nio.charset.Charset
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class PersonalImportTest {
    private val header = "交易时间,交易分类,交易对方,对方账号,商品说明,收/支,金额,收/付款方式,交易状态,交易订单号,商家订单号,备注"
    private fun parse(vararg lines: String) = requireNotNull(BillParsers.parse((header + "\n" + lines.joinToString("\n")).toByteArray(Charset.forName("GB18030"))))
    @Test fun closedOriginalNeedsExactSuccessfulRefundEvidence() {
        val bill = parse(
            "2026-10-01 12:00:00,购物,合成淘宝,,物品,支出,25.00,余额,交易关闭,ORDER1,,",
            "2026-10-02 12:00:00,退款,合成淘宝,,退款,不计收支,25.00,余额,退款成功,ORDER1_EVENT1,,",
            "2026-10-01 12:00:00,购物,未付款,,物品,支出,25.00,余额,交易关闭,ORDER2,,")
        assertEquals(2, bill.rows.size)
        assertEquals(1, bill.skippedStatus)
        assertEquals(2500L, bill.rows.first().refundedCents)
    }
    @Test fun earningsAndExplicitReceiptsAreIncomeButPrincipalIsNot() {
        val bill = parse(
            "2026-10-01 12:00:00,投资理财,收益,,余额宝收益发放,不计收支,0.02,余额,交易成功,YIELD,,",
            "2026-10-02 12:00:00,转账,朋友,,转账收款,不计收支,100.00,余额,交易成功,RECEIPT,,",
            "2026-10-02 12:00:00,投资理财,余额宝,,转入余额宝,不计收支,100.00,余额,交易成功,PRINCIPAL,,")
        assertEquals(2, bill.rows.size)
        assertTrue(bill.rows.all { it.type == TxType.INCOME })
        assertEquals("理财", bill.rows.first().guessedCategoryName)
        assertEquals(1, bill.skippedOtherType)
    }
    @Test fun partialRefundsAccumulateWithoutGuessingAndAreIdempotent() {
        val original = TransactionEntity(id=1, type=TxType.EXPENSE, amountCents=10000, categoryId=1, accountId=1,
            timestamp=1, source=TxSource.ALIPAY, externalId="ORDER")
        val unrelated = original.copy(id=2, externalId="OTHER")
        val refund = original.copy(id=3, type=TxType.INCOME, amountCents=2000, externalId="ORDER_R1", refundMark=RefundMark.REFUND_INCOME)
        val second = refund.copy(id=4, amountCents=3000, externalId="ORDER_R2")
        var all = listOf(original, unrelated, refund, second)
        val updates = RefundReconciler.updates(all).associateBy { it.id }
        all = all.map { updates[it.id] ?: it }
        assertEquals(5000L, all.first().refundedCents)
        assertEquals(0L, all[1].refundedCents)
        assertEquals(1L, all[2].refundExpenseId)
        assertTrue(RefundReconciler.updates(all).isEmpty())
    }
    @Test fun privateBillStaysOutsideRepositoryAndEveryRefundHasOriginal() {
        val path = System.getProperty("wallet.localBill")
        assumeTrue(path != null)
        val bill = requireNotNull(BillParsers.parse(File(requireNotNull(path)).readBytes()))
        val rows = bill.rows.mapIndexed { index, row -> TransactionEntity(id=index+1L, type=row.type,
            amountCents=row.amountCents, categoryId=1, accountId=1, timestamp=row.timestamp,
            source=TxSource.ALIPAY, externalId=row.externalId, refundMark=row.refundMark, refundedCents=row.refundedCents) }
        val updates = RefundReconciler.updates(rows).associateBy { it.id }
        val all = rows.map { updates[it.id] ?: it }
        assertEquals(333, all.size)
        assertEquals(138, all.count { it.type == TxType.INCOME && it.refundMark != RefundMark.REFUND_INCOME })
        val refunds = all.filter { it.refundMark == RefundMark.REFUND_INCOME }
        assertEquals(6, refunds.size)
        refunds.forEachIndexed { i, r ->
            val match = all.firstOrNull { it.type == TxType.EXPENSE && it.externalId == r.externalId?.substringBefore('_') }
            println("Refund audit ${i+1}: linked=${r.refundExpenseId>0}, exactOriginal=${match!=null}, amountWithin=${match!=null && r.amountCents<=match.amountCents}")
        }
        assertTrue(refunds.all { it.refundExpenseId > 0 })
        assertEquals(6, all.count { it.refundMark == RefundMark.REFUNDED_EXPENSE })
        assertEquals(130, bill.rows.count { (it.goods.contains("余额宝") && it.goods.contains("收益发放")) && it.type == TxType.INCOME })
        assertTrue(bill.rows.any { it.goods.contains("转账收款") && it.type == TxType.INCOME })
        println("Private bill: rows=${all.size}, refunds=${refunds.size}, exact links=${refunds.count { it.refundExpenseId > 0 }}; no transaction details logged")
    }
}
