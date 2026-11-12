package com.controleco.app.personal

import android.app.Application
import androidx.room.Room
import com.controleco.app.data.RefundActions
import com.controleco.app.data.db.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], application=Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class PersonalDatabaseTest {
    @Test fun nativeV14DatabaseMigratesWithoutLosingTransactions() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "migration-personal-test.db"
        context.deleteDatabase(name)
        val schema = org.json.JSONObject(java.io.File("schemas/com.controleco.app.data.db.AccBookDatabase/14.json").readText()).getJSONObject("database")
        val helper = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(14) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        val entities = schema.getJSONArray("entities")
                        for(i in 0 until entities.length()) {
                            val e = entities.getJSONObject(i)
                            db.execSQL(e.getString("createSql").replace("${'$'}{TABLE_NAME}",e.getString("tableName")))
                            val indexes = e.getJSONArray("indices")
                            for(j in 0 until indexes.length()) db.execSQL(indexes.getJSONObject(j).getString("createSql").replace("${'$'}{TABLE_NAME}",e.getString("tableName")))
                        }
                        val setup = schema.getJSONArray("setupQueries")
                        for(i in 0 until setup.length()) db.execSQL(setup.getString(i))
                        db.execSQL("INSERT INTO accounts VALUES (1,'合成账户',0,'',1)")
                        db.execSQL("INSERT INTO categories VALUES (1,'购物','EXPENSE',0,'')")
                        db.execSQL("INSERT INTO transactions (id,type,amountCents,categoryId,accountId,timestamp,note,counterparty,source,externalId,refundMark,refundPending,flowId,budgetExcluded,aaShares,createdAt) VALUES (1,'EXPENSE',2500,1,1,1000,'','','MANUAL','synthetic','REFUNDED_EXPENSE',0,'',0,0,1000)")
                    }
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase,old:Int,new:Int) {}
                }).build())
        helper.writableDatabase;helper.close()
        val migrated = Room.databaseBuilder(context, AccBookDatabase::class.java,name).addMigrations(AccBookDatabase.MIGRATION_14_15).build()
        try {
            val row = requireNotNull(migrated.transactionDao().getById(1))
            assertEquals(2500L,row.refundedCents)
            assertEquals(0L,row.refundExpenseId)
            assertEquals(0L,migrated.transactionDao().observeBudgetSpendingTotal(0,2000).first())
        } finally { migrated.close();context.deleteDatabase(name) }
    }
    @Test fun originalMonthNetTotalsPartialCaptureUndoAndDeletion() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AccBookDatabase::class.java).build()
        try {
            DefaultData.seedIfNeeded(db)
            assertEquals(listOf("餐饮","购物","娱乐","日常"), DefaultData.defaultExpenseCategories)
            val cats = db.categoryDao().getAll()
            val category = cats.first { it.name=="购物" }.id
            val income = cats.first { it.type==TxType.INCOME && it.name=="其他" }.id
            val account = db.accountDao().getAll().first().id
            val dao = db.transactionDao()
            val original = dao.insert(TransactionEntity(type=TxType.EXPENSE, amountCents=10000, categoryId=category, accountId=account, timestamp=1000))
            dao.insert(TransactionEntity(type=TxType.INCOME,amountCents=9000,categoryId=income,accountId=account,timestamp=1100))
            val pendingId = db.pendingDao().insert(PendingTransactionEntity(type=TxType.INCOME,amountCents=3000,timestamp=9000,
                sourcePackage="com.eg.android.AlipayGphone",ruleName="synthetic-refund",dedupKey="synthetic-r1",refundMark=RefundMark.REFUND_INCOME))
            RefundActions(db).refundCaptured(pendingId, original)
            assertEquals(7000L, dao.observeSpendingTotal(0,2000).first())
            assertEquals(7000L, dao.observeBudgetSpendingTotal(0,2000).first())
            assertEquals(7000L, dao.observeSpendingCategorySums(0,2000).first().single().totalCents)
            assertEquals(9000L, dao.observeTypeTotals(0,10000).first().first { it.type==TxType.INCOME }.totalCents)
            assertEquals(2, dao.observeRows(0,10000).first().size)
            assertEquals(2000L, dao.observeBalances().first().first { it.accountId==account }.signedSumCents)
            // A second same-amount expense must never have its refund removed by undo.
            val other = dao.insert(TransactionEntity(type=TxType.EXPENSE,amountCents=10000,categoryId=category,accountId=account,timestamp=1500))
            RefundActions(db).refundExpense(other)
            RefundActions(db).undoRefund(original)
            assertEquals(0L, requireNotNull(dao.getById(original)).refundedCents)
            assertEquals(1, dao.refundsFor(other).size)
            dao.deleteById(other)
            assertTrue(dao.refundsFor(other).isEmpty())
        } finally { db.close() }
    }
    @Test fun importedRefundSnapshotsAreNotSubtractedTwiceWhenLinked() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),AccBookDatabase::class.java).build()
        try {
            DefaultData.seedIfNeeded(db)
            val cats=db.categoryDao().getAll()
            val account=db.accountDao().getAll().first().id
            val shopping=cats.first { it.name=="购物" }.id
            val refundCategory=cats.first { it.name=="退款" }.id
            val dao=db.transactionDao()
            val partial=dao.insert(TransactionEntity(type=TxType.EXPENSE,amountCents=10000,categoryId=shopping,accountId=account,
                timestamp=1000,source=TxSource.WECHAT,externalId="SYNTHETIC-PARTIAL",refundedCents=2000))
            val partialEvent=dao.insert(TransactionEntity(type=TxType.INCOME,amountCents=2000,categoryId=refundCategory,accountId=account,
                timestamp=3000,source=TxSource.WECHAT,externalId="SYNTHETIC-PARTIAL-REFUND",refundMark=RefundMark.REFUND_INCOME,refundPending=true))
            RefundActions(db).handleLegacyRefund(partialEvent,partial)
            assertEquals(2000L,dao.getById(partial)!!.refundedCents)
            assertEquals(8000L,dao.observeSpendingTotal(0,10000).first())
            val full=dao.insert(TransactionEntity(type=TxType.EXPENSE,amountCents=10000,categoryId=shopping,accountId=account,
                timestamp=1000,source=TxSource.WECHAT,externalId="SYNTHETIC-FULL",refundMark=RefundMark.REFUNDED_EXPENSE,refundedCents=10000))
            val fullEvent=dao.insert(TransactionEntity(type=TxType.INCOME,amountCents=10000,categoryId=refundCategory,accountId=account,
                timestamp=4000,source=TxSource.WECHAT,externalId="SYNTHETIC-FULL-REFUND",refundMark=RefundMark.REFUND_INCOME,refundPending=true))
            assertTrue(dao.refundCandidates(10000,true).any { it.id==full })
            assertTrue(dao.refundCandidates(10000,false).none { it.id==full })
            RefundActions(db).handleLegacyRefund(fullEvent,full)
            assertEquals(10000L,dao.getById(full)!!.refundedCents)
            assertEquals(full,dao.getById(fullEvent)!!.refundExpenseId)
            assertEquals(0,dao.observePendingRefundCount().first())
            assertEquals(8000L,dao.observeBudgetSpendingTotal(0,10000).first())
            assertTrue(dao.refundCandidates(10000,true).none { it.id==full })
        } finally { db.close() }
    }

    @Test fun rangeDeletionOnlyDeletesVisibleRowsAndTheirExactRefunds() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),AccBookDatabase::class.java).build()
        try {
            DefaultData.seedIfNeeded(db)
            val cats=db.categoryDao().getAll();val account=db.accountDao().getAll().first().id
            val shopping=cats.first { it.name=="购物" }.id
            val refundCategory=cats.first { it.name=="退款" }.id
            val dao=db.transactionDao()
            val original=dao.insert(TransactionEntity(type=TxType.EXPENSE,amountCents=10000,categoryId=shopping,accountId=account,timestamp=1000,refundedCents=3000))
            dao.insert(TransactionEntity(type=TxType.INCOME,amountCents=3000,categoryId=refundCategory,accountId=account,timestamp=9000,
                refundMark=RefundMark.REFUND_INCOME,refundExpenseId=original))
            assertEquals(0,dao.countByRange(8000,10000))
            dao.deleteByRange(8000,10000)
            assertEquals(2,dao.countAll())
            assertEquals(7000L,dao.observeBudgetSpendingTotal(0,2000).first())
            assertEquals(1,dao.countByRange(0,2000))
            dao.deleteByRange(0,2000)
            assertEquals(0,dao.countAll())
        } finally { db.close() }
    }

    @Test fun concurrentColdStartSeedsOnlyOneSetOfDefaults() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),AccBookDatabase::class.java).build()
        try {
            kotlinx.coroutines.coroutineScope {
                val jobs=(1..16).map { async(kotlinx.coroutines.Dispatchers.IO) { DefaultData.seedIfNeeded(db) } }
                jobs.forEach { it.await() }
            }
            assertEquals(4,db.accountDao().countAll())
            assertEquals(9,db.categoryDao().countAll())
            assertEquals(9,db.categoryDao().getAll().map { it.type to it.name }.toSet().size)
        } finally { db.close() }
    }

}
