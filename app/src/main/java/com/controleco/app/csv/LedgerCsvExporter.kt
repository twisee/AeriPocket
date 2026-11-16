package com.controleco.app.csv

import com.controleco.app.data.db.*
import java.io.Writer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class LedgerExportSnapshot(val transactions: List<TransactionEntity>, val categories: Map<Long, String>)

/** Generic CSV preserves original and refunded/net amounts separately; never exports raw refund events as income. */
object LedgerCsvExporter {
    val headers = listOf("日期时间", "收支", "分类", "金额", "商户", "备注", "来源", "已退款金额", "实付金额", "退款状态", "AA人数", "是否计入预算")

    fun write(snapshot: LedgerExportSnapshot, writer: Writer, zone: ZoneId = ZoneId.systemDefault()) {
        writer.write("\uFEFF")
        fun row(values: List<String>) {
            writer.write(values.joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" })
            writer.write("\r\n")
        }
        row(headers)
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        snapshot.transactions.filter { it.refundMark != RefundMark.REFUND_INCOME }.forEach { tx ->
            val refund = if(tx.type != TxType.EXPENSE) 0L else if(tx.refundMark == RefundMark.REFUNDED_EXPENSE)
                tx.amountCents else tx.refundedCents.coerceIn(0, tx.amountCents)
            val net = if(tx.type == TxType.EXPENSE) (tx.amountCents - refund).coerceAtLeast(0) else tx.amountCents
            row(listOf(Instant.ofEpochMilli(tx.timestamp).atZone(zone).format(formatter),
                if(tx.type == TxType.EXPENSE) "支出" else "收入", snapshot.categories[tx.categoryId].orEmpty(),
                money(tx.amountCents), tx.counterparty, tx.note, when(tx.source) {
                    TxSource.MANUAL -> "手动记账"; TxSource.WECHAT -> "微信"; TxSource.ALIPAY -> "支付宝"
                    TxSource.NOTIFICATION -> "自动记账"; TxSource.BANK -> "银行"
                }, money(refund), money(net), when {
                    refund >= tx.amountCents && tx.type == TxType.EXPENSE -> "已全额退款"
                    refund > 0 -> "已部分退款"; else -> "无退款"
                }, if(tx.aaShares >= 2) tx.aaShares.toString() else "", if(tx.budgetExcluded) "否" else "是"))
        }
    }

    private fun money(cents: Long): String = java.math.BigDecimal.valueOf(cents, 2).toPlainString()
}
