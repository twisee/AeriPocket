package com.controleco.app.personal

import android.app.Application
import androidx.room.Room
import com.controleco.app.data.RefundActions
import com.controleco.app.data.budget.BudgetShare
import com.controleco.app.data.db.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class ExperienceDatabaseTest {
    @Test fun experienceDailyExcludedAndRefundsUseIndependentTotals() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AccBookDatabase::class.java).build()
        try {
            DefaultData.seedIfNeeded(db)
            val dao = db.transactionDao()
            val account = db.accountDao().getAll().first().id
            val category = db.categoryDao().getAll().first { it.type == TxType.EXPENSE }.id
            fun expense(amount: Long, experience: Boolean = false, excluded: Boolean = false, shares: Int = 0) =
                TransactionEntity(type = TxType.EXPENSE, amountCents = amount, timestamp = 1000,
                    accountId = account, categoryId = category, experienceBudget = experience, budgetExcluded = excluded, aaShares = shares)
            dao.insert(expense(2000))
            val id = dao.insert(expense(10000, experience = true, shares = 2))
            dao.insert(expense(3000, excluded = true))
            assertEquals(2000L, dao.observeBudgetSpendingTotal(0, 2000).first())
            assertEquals(10000L, dao.observeSpendingTotal(0, 2000).first())
            assertEquals(2000L, dao.observeBudgetCategorySums(0, 2000).first().sumOf { it.totalCents })
            assertTrue(dao.observeRows(0, 2000).first().first { it.id == id }.experienceBudget)
            RefundActions(db).refundExpense(id, 2000)
            assertEquals(9000L, dao.observeSpendingTotal(0, 2000).first())
            val row = dao.observeExperienceExpenses().first().single()
            assertEquals(4000L, BudgetShare.aaSplitCents(row.amountCents - row.refundedCents, row.aaShares))
            assertEquals(2000L, dao.observeBudgetSpendingTotal(0, 2000).first())
            assertEquals(2000L, dao.refundsFor(id).single().amountCents)
            try { RefundActions(db).refundExpense(id, 9000); fail("over-refund must fail") } catch (_: IllegalStateException) { }
            assertEquals(2000L, requireNotNull(dao.getById(id)).refundedCents)
            RefundActions(db).refundExpense(id, 8000)
            assertEquals(RefundMark.REFUNDED_EXPENSE, requireNotNull(dao.getById(id)).refundMark)
            RefundActions(db).undoRefund(id)
            assertEquals(0L, requireNotNull(dao.getById(id)).refundedCents)
            assertTrue(requireNotNull(dao.getById(id)).experienceBudget)
            assertTrue(dao.refundsFor(id).isEmpty())
        } finally { db.close() }
    }
}
