package com.controleco.app.personal

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.controleco.app.AccBookApp
import com.controleco.app.csv.BillParsers
import com.controleco.app.data.LedgerViewModel
import com.controleco.app.data.db.*
import com.controleco.app.data.model.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=AccBookApp::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class PersonalFlowTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    @Test fun realImportDedupAndMerchantLearningUseNativeViewModel() {
        val app=RuntimeEnvironment.getApplication() as AccBookApp
        ui.waitUntil(10000) { runBlocking { app.db.categoryDao().countAll() > 0 } }
        val vm=LedgerViewModel(app)
        ui.setContent { androidx.compose.material3.Text("Flow verification") }
        val db=app.db
        val account=runBlocking { db.accountDao().getAll().first().id }
        runBlocking { db.transactionDao().deleteAll();db.merchantRuleDao().clearAll() }
        fun commit(bill:ParsedBill) {
            ui.runOnIdle { vm.clearImportState(); vm.commitImport(bill,account) }
            ui.waitUntil(15000) {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                vm.importDone.value!=null || vm.importError.value!=null
            }
            assertNull(vm.importError.value)
        }
        System.getProperty("wallet.localBill")?.let { path ->
            val bill=requireNotNull(BillParsers.parse(File(path).readBytes()))
            commit(bill)
            assertEquals(333,vm.importDone.value!!.inserted)
            commit(bill)
            assertEquals(0,vm.importDone.value!!.inserted)
            assertEquals(333,vm.importDone.value!!.duplicated)
            val all=runBlocking { db.transactionDao().getAll() }
            assertEquals(6,all.count { it.refundMark==RefundMark.REFUND_INCOME && it.refundExpenseId>0 })
            runBlocking { db.transactionDao().deleteAll() }
        }
        val row=ImportedBillRow(type=TxType.EXPENSE,amountCents=1800,timestamp=1000,counterparty="江西理工大学",
            goods="合成演示午餐",note="合成演示",payMethod="余额",externalId="SYNTHETIC-SCHOOL-1",guessedCategoryName="购物")
        commit(ParsedBill(BillSource.ALIPAY,listOf(row),0,0,0))
        val old=runBlocking { db.transactionDao().getAll().single() }
        val dining=runBlocking { db.categoryDao().getAll().first { it.name=="餐饮" }.id }
        var saved=false;var message:String?=null
        ui.runOnIdle { vm.saveTransaction(old.id,old.type,old.amountCents,dining,old.accountId,old.timestamp,old.note,old.counterparty,
            onResult={ message=it;saved=true }) }
        ui.waitUntil(10000) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();saved };assertNull(message)
        commit(ParsedBill(BillSource.ALIPAY,listOf(row.copy(externalId="SYNTHETIC-SCHOOL-2",timestamp=2000)),0,0,0))
        val future=runBlocking { db.transactionDao().getAll().first { it.externalId=="SYNTHETIC-SCHOOL-2" } }
        assertEquals(dining,future.categoryId)
        // A later export carries the refund event; the previously imported original is updated.
        val refund=row.copy(type=TxType.INCOME,amountCents=500,timestamp=9000,
            externalId="SYNTHETIC-SCHOOL-2_REFUND-1",refundMark=RefundMark.REFUND_INCOME)
        commit(ParsedBill(BillSource.ALIPAY,listOf(refund),0,0,1))
        commit(ParsedBill(BillSource.ALIPAY,listOf(refund),0,0,1))
        val updated=runBlocking { db.transactionDao().getById(future.id)!! }
        assertEquals(500L,updated.refundedCents)
        assertEquals(dining,updated.categoryId)
        assertEquals(3,runBlocking { db.transactionDao().countAll() })
    }
}
