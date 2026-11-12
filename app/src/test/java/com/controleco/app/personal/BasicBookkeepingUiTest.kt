package com.controleco.app.personal

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.controleco.app.AccBookApp
import com.controleco.app.data.LedgerViewModel
import com.controleco.app.data.db.*
import com.controleco.app.ui.AccBookNav
import com.controleco.app.ui.eco.LocalAnimationsEnabled
import com.controleco.app.ui.theme.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=AccBookApp::class,qualifiers="w390dp-h844dp")
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class BasicBookkeepingUiTest {
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    private fun waitFor(condition:()->Boolean) {
        ui.waitUntil(15000) { Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();condition() }
    }
    private fun waitText(text:String) {
        try { waitFor { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
        catch(e:Throwable) { println(ui.onRoot(useUnmergedTree=true).printToString());throw e }
    }
    @Test fun manualExpenseIncomeEditDeleteAndRecurringBudget() {
        val app=RuntimeEnvironment.getApplication() as AccBookApp
        waitFor { runBlocking { app.db.categoryDao().countAll()>0 } }
        val dao=app.db.transactionDao()
        runBlocking { dao.deleteAll() }
        val vm=LedgerViewModel(app)
        ui.runOnIdle { vm.setMonthlyBudget(100000) }
        ui.setContent { AccBookTheme(accent=AccentThemeStore.current) {
            CompositionLocalProvider(LocalAnimationsEnabled provides false) { AccBookNav(vm=vm) }
        } }
        waitText("首页")
        ui.onAllNodesWithContentDescription("记一笔").assertCountEquals(0)
        ui.onNodeWithText("流水").performClick()
        ui.onNodeWithContentDescription("记一笔").performClick()
        waitText("记一笔")
        for(key in listOf("1","2",".","5")) ui.onNodeWithText(key).performClick()
        ui.onAllNodesWithText("记一笔").onLast().performClick()
        waitFor { runBlocking { dao.countAll()==1 } }
        waitText("流水")
        val expense=runBlocking { dao.getAll().single() }
        assertEquals(TxType.EXPENSE,expense.type);assertEquals(1250L,expense.amountCents)
        // Open the native editor from the real list and change amount + merchant.
        ui.onNodeWithText("餐饮").performClick();waitText("编辑记录")
        ui.onAllNodes(hasSetTextAction()).onFirst().performTextReplacement("17.80")
        ui.onAllNodes(hasSetTextAction())[1].performTextReplacement("手动演示午餐")
        ui.onNodeWithText("保存修改").performScrollTo().performClick()
        waitFor { runBlocking { dao.getById(expense.id)?.amountCents==1780L } }
        waitText("手动演示午餐")
        ui.onNodeWithContentDescription("记一笔").performClick();waitText("记一笔")
        ui.onNodeWithText("收入").performClick()
        for(key in listOf("2","5")) ui.onNodeWithText(key).performClick()
        ui.onAllNodesWithText("记一笔").onLast().performClick()
        waitFor { runBlocking { dao.getAll().any { it.type==TxType.INCOME && it.amountCents==2500L } } }
        waitText("手动演示午餐")
        ui.onNodeWithText("手动演示午餐").performClick();waitText("编辑记录")
        ui.onNodeWithText("删除").performClick();waitText("删除记录")
        ui.onAllNodesWithText("删除").onLast().performClick()
        waitFor { runBlocking { dao.getById(expense.id)==null } }
        waitText("流水")
        assertEquals(TxType.INCOME,runBlocking { dao.getAll().single().type })
        // Changing the viewed month and recreating the VM must not reset the fixed monthly allowance.
        ui.runOnIdle { vm.setMonth(YearMonth.now().minusMonths(1)) }
        assertEquals(100000L,runBlocking { vm.monthlyBudgetCents.first() })
        val reopened=LedgerViewModel(app)
        assertEquals(100000L,runBlocking { reopened.monthlyBudgetCents.first() })
        ui.onNodeWithText("个人").performClick();waitText("我的")
        ui.onNodeWithText("设置").performClick();waitText("自动记账")
    }
}
