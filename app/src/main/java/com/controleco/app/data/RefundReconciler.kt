package com.controleco.app.data

import com.controleco.app.data.db.*

/** 仅按来源与完整原交易号关联；不按金额、商户或相近时间猜测。 */
internal object RefundReconciler {
    fun updates(all: List<TransactionEntity>): List<TransactionEntity> {
        val expenses = all.filter { it.type == TxType.EXPENSE }
            .filter { !it.externalId.isNullOrBlank() }.associateBy { it.source to it.externalId }
        val linked = all.filter { it.refundMark == RefundMark.REFUND_INCOME }.map { refund ->
            val original = if (refund.source == TxSource.ALIPAY && refund.externalId?.contains('_') == true)
                expenses[refund.source to refund.externalId.substringBefore('_')] else null
            if (original != null && refund.amountCents >= 0 && refund.amountCents <= original.amountCents)
                refund.copy(refundExpenseId = original.id, refundPending = false)
            else refund.copy(refundPending = refund.refundExpenseId == 0L)
        }
        val byExpense = linked.filter { it.refundExpenseId != 0L }.groupBy { it.refundExpenseId }
        val expenseUpdates = expenses.values.map { expense ->
            val sum = byExpense[expense.id].orEmpty().sumOf { it.amountCents }
            val refunded = maxOf(expense.refundedCents, sum).coerceIn(0, expense.amountCents)
            expense.copy(refundedCents = refunded,
                refundMark = if (refunded == expense.amountCents && (refunded > 0 || byExpense[expense.id].orEmpty().isNotEmpty())) RefundMark.REFUNDED_EXPENSE else RefundMark.NONE)
        }
        val previous = all.associateBy { it.id }
        return (linked + expenseUpdates).filter { it != previous[it.id] }
    }
}
