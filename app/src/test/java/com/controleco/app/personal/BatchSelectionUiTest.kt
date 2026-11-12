package com.controleco.app.personal

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.controleco.app.AccBookApp
import com.controleco.app.data.LedgerViewModel
import com.controleco.app.data.db.*
import com.controleco.app.ui.AccBookNav
import com.controleco.app.ui.eco.*
import com.controleco.app.ui.theme.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = AccBookApp::class, qualifiers = "w390dp-h844dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class BatchSelectionUiTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private lateinit var app: AccBookApp
    private lateinit var vm: LedgerViewModel
    private var expenseIds = emptySet<Long>()
    private var incomeIds = emptySet<Long>()
    private var salaryId = 0L
    private lateinit var salaryName: String
    private fun exists(text: String) = ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(block: () -> Boolean) {
        try { ui.waitUntil(15000) { Shadows.shadowOf(Looper.getMainLooper()).idle(); block() } }
        catch (e: Throwable) { println(ui.onRoot(useUnmergedTree = true).printToString()); throw e }
    }
    @Before fun seed() {
        app = RuntimeEnvironment.getApplication() as AccBookApp
        waitFor { runBlocking { app.db.categoryDao().countAll() > 0 } }
        runBlocking {
            val db = app.db
            db.transactionDao().deleteAll()
            val cats = db.categoryDao().getAll()
            val incomeCats = cats.filter { it.type == TxType.INCOME && it.name != "退款" }
            val salary = incomeCats.first { it.name == "工资" }
            val other = incomeCats.first { it.id != salary.id }
            salaryName = salary.name
            val account = db.accountDao().getAll().first().id
            val expenseCat = cats.first { it.type == TxType.EXPENSE }.id
            val base = YearMonth.now().atDay(18).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            expenseIds = (1..40).map { index -> db.transactionDao().insert(TransactionEntity(
                type = TxType.EXPENSE, amountCents = 1000, categoryId = expenseCat, accountId = account,
                timestamp = base - index * 7200000L, counterparty = "合成支出$index")) }.toSet()
            salaryId = db.transactionDao().insert(TransactionEntity(type = TxType.INCOME, amountCents = 8000,
                categoryId = salary.id, accountId = account, timestamp = base + 60000, counterparty = "合成工资"))
            val otherId = db.transactionDao().insert(TransactionEntity(type = TxType.INCOME, amountCents = 2000,
                categoryId = other.id, accountId = account, timestamp = base + 120000, counterparty = "合成其他收入"))
            incomeIds = setOf(salaryId, otherId)
        }
        vm = LedgerViewModel(app)
        vm.setMonthStartDay(1)
        EcoToast.clear()
        ui.setContent { AccBookTheme(accent = EcoAccent.White) {
            CompositionLocalProvider(LocalAnimationsEnabled provides false) { AccBookNav(vm) }
        } }
        ui.onAllNodesWithText("流水").onLast().performClick()
        waitFor { exists("合成工资") }
    }
    @After fun clean() { runBlocking { app.db.transactionDao().deleteAll() }; EcoToast.clear() }
    private fun batchAll() {
        ui.onNodeWithContentDescription("批量操作").performClick()
        ui.onNodeWithText("批量管理").performClick()
        ui.onNodeWithText("全选").performClick()
    }
    private fun deleteSelected() {
        ui.onNodeWithText("删除").performClick()
        ui.onAllNodesWithText("删除").onLast().performClick()
    }
    private fun remaining() = runBlocking { app.db.transactionDao().getAll().map { it.id }.toSet() }
    @Test fun incomeSelectAllDeletesOnlyIncomeAndPreservesEveryExpense() {
        ui.onNodeWithText("收入").performClick()
        batchAll()
        ui.onNodeWithText("已选 2").assertExists()
        deleteSelected()
        waitFor { remaining() == expenseIds }
    }
    @Test fun typeAndCategoryTogetherRestrictSelectAll() {
        ui.onNodeWithText("收入").performClick()
        ui.onNodeWithContentDescription("分类筛选").performClick()
        ui.onNodeWithText(salaryName).performClick()
        ui.onNodeWithText("应用筛选").performScrollTo().performClick()
        batchAll()
        ui.onNodeWithText("已选 1").assertExists()
        deleteSelected()
        waitFor { remaining() == expenseIds + (incomeIds - salaryId) }
    }
    @Test fun changingTypeAfterSelectingAllClearsHiddenSelections() {
        batchAll()
        ui.onNodeWithText("已选 42").assertExists()
        ui.onNodeWithText("收入").performClick()
        ui.onNodeWithText("已选 0").assertExists()
        ui.onNodeWithText("全选").performClick()
        ui.onNodeWithText("已选 2").assertExists()
        deleteSelected()
        waitFor { remaining() == expenseIds }
    }
    @Test fun changingCategoryAfterSelectingAllClearsHiddenSelections() {
        batchAll()
        ui.onNodeWithText("已选 42").assertExists()
        ui.onNodeWithContentDescription("分类筛选").performClick()
        ui.onNodeWithText(salaryName).performClick()
        ui.onNodeWithText("应用筛选").performScrollTo().performClick()
        ui.onNodeWithText("已选 0").assertExists()
        ui.onNodeWithText("全选").performClick()
        ui.onNodeWithText("已选 1").assertExists()
        deleteSelected()
        waitFor { remaining() == expenseIds + (incomeIds - salaryId) }
    }
    @Test fun confirmationKeepsItsOriginalIdsWhenANewIncomeArrives() {
        ui.onNodeWithText("收入").performClick()
        batchAll()
        ui.onNodeWithText("删除").performClick()
        ui.onNodeWithText("仅删除当前筛选结果中已勾选的 2 条记录", substring = true).assertExists()
        val newId = runBlocking {
            val source = app.db.transactionDao().getAll().first { it.id == salaryId }
            app.db.transactionDao().insert(source.copy(id = 0, counterparty = "确认期间新增的合成收入"))
        }
        ui.waitForIdle()
        ui.onAllNodesWithText("删除").onLast().performClick()
        waitFor { remaining() == expenseIds + newId }
    }
    @Test fun incomeDeletionPreservesExpenseAndItsLinkedRefundEvent() {
        val refundId = runBlocking {
            val source = app.db.transactionDao().getAll().first { it.id == salaryId }
            app.db.transactionDao().insert(source.copy(id = 0, refundMark = RefundMark.REFUND_INCOME,
                refundExpenseId = expenseIds.first(), counterparty = "合成退款原始事件"))
        }
        ui.onNodeWithText("收入").performClick()
        batchAll()
        ui.onNodeWithText("已选 2").assertExists()
        deleteSelected()
        waitFor { remaining() == expenseIds + refundId }
    }
    @Test fun expenseSelectAllIncludesFilteredRowsBeyondTheFirstPage() {
        ui.onNodeWithText("支出").performClick()
        batchAll()
        ui.onNodeWithText("已选 40").assertExists()
        deleteSelected()
        waitFor { remaining() == incomeIds }
    }
}
