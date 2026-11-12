package com.controleco.app.data

import androidx.room.withTransaction
import com.controleco.app.capture.CaptureDefaults
import com.controleco.app.capture.CaptureResolution
import com.controleco.app.capture.LogicalTransactions
import com.controleco.app.data.db.*

/** 保存精确退款关联。操作在原项目的 Room 事务内完成，重复点击不会重复退款。 */
internal class RefundActions(private val db: AccBookDatabase) {
    private val transactions = db.transactionDao()

    private suspend fun refundCategoryId(): Long {
        val categories = db.categoryDao().getAll()
        return categories.firstOrNull { CategoryRules.isRefundIncome(it.type, it.name) }?.id
            ?: db.categoryDao().insert(CategoryEntity(name = RefundCategory.NAME, type = TxType.INCOME, sortOrder = 3))
    }

    private suspend fun createRefund(expense: TransactionEntity, capture: PendingTransactionEntity? = null) {
        transactions.insert(
            TransactionEntity(
                type = TxType.INCOME,
                amountCents = capture?.amountCents?.takeIf { it > 0 } ?: (expense.amountCents - expense.refundedCents),
                categoryId = refundCategoryId(),
                accountId = expense.accountId,
                // 保证生成的收入在原支出之后；普通捕获仍保留退款业务时间。
                timestamp = maxOf(capture?.timestamp ?: System.currentTimeMillis(), expense.timestamp + 1),
                counterparty = expense.counterparty,
                note = capture?.note ?: "",
                source = capture?.let { CaptureDefaults.txSource(it.sourcePackage) } ?: TxSource.MANUAL,
                externalId = capture?.let { "NTF:${it.dedupKey}" },
                refundMark = RefundMark.REFUND_INCOME,
                refundPending = false,
                refundExpenseId = expense.id,
                flowId = capture?.flowId.orEmpty(),
            ),
        )
        val amount = capture?.amountCents?.takeIf { it > 0 } ?: (expense.amountCents - expense.refundedCents)
        val total = expense.refundedCents + amount
        transactions.update(expense.copy(refundedCents = total,
            refundMark = if (total >= expense.amountCents) RefundMark.REFUNDED_EXPENSE else RefundMark.NONE))
    }

    suspend fun refundExpense(expenseId: Long) = db.withTransaction {
        val expense = transactions.getById(expenseId)
        RefundRules.expenseError(expense)?.let { error(it) }
        createRefund(requireNotNull(expense))
    }

    suspend fun refundCaptured(pendingId: Long, expenseId: Long) {
        var resolved: PendingTransactionEntity? = null
        try {
            db.withTransaction {
                val capture = db.pendingDao().getById(pendingId) ?: error("该退款已处理")
                check(capture.status == PendingStatus.PENDING && capture.type == TxType.INCOME &&
                    capture.refundMark == RefundMark.REFUND_INCOME) { "该记录不是待处理退款" }
                val expense = transactions.getById(expenseId)
                RefundRules.expenseError(expense, capture.amountCents)?.let { error(it) }
                resolved = capture
                LogicalTransactions.markResolved(capture, CaptureResolution.HANDLED)
                createRefund(requireNotNull(expense), capture)
                db.pendingDao().deleteById(pendingId)
            }
        } catch (e: Exception) {
            resolved?.let { LogicalTransactions.forget(it.dedupKey) }
            throw e
        }
    }

    /** 兼容升级前已经入账、尚待用户指认支出的退款收入，不重复生成收入。 */
    suspend fun handleLegacyRefund(refundId: Long, expenseId: Long) = db.withTransaction {
        val refund = transactions.getById(refundId) ?: error("退款记录不存在")
        check(refund.type == TxType.INCOME && refund.refundMark == RefundMark.REFUND_INCOME && refund.refundPending) {
            "该退款已处理"
        }
        val expense = transactions.getById(expenseId)
        val alreadyLinked = transactions.refundsFor(expenseId).sumOf { it.amountCents }
        RefundRules.legacyError(expense, refund.amountCents, alreadyLinked)?.let { error(it) }
        val original = requireNotNull(expense)
        val total = maxOf(original.refundedCents, alreadyLinked + refund.amountCents)
        transactions.update(original.copy(refundedCents = total,
            refundMark = if (total >= original.amountCents) RefundMark.REFUNDED_EXPENSE else RefundMark.NONE))
        transactions.update(refund.copy(refundExpenseId = expenseId, refundPending = false))
    }

    suspend fun undoRefund(expenseId: Long) = db.withTransaction {
        val expense = transactions.getById(expenseId) ?: error("原支出记录不存在")
        check(expense.type == TxType.EXPENSE && expense.refundedCents > 0) { "该账单未退款" }
        transactions.refundsFor(expenseId).forEach { refund ->
            refund.externalId?.takeIf { it.startsWith("NTF:") }?.removePrefix("NTF:")?.let { key ->
                db.pendingDao().findByDedupKey(key)?.let { LogicalTransactions.markResolved(it, CaptureResolution.REJECTED) }
                db.pendingDao().deleteByDedupKey(key)
            }
            transactions.deleteById(refund.id)
        }
        transactions.clearExpenseRefund(expenseId)
    }
}
