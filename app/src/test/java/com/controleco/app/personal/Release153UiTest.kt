package com.controleco.app.personal

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.controleco.app.AccBookApp
import com.controleco.app.data.LedgerViewModel
import com.controleco.app.data.db.*
import com.controleco.app.ui.eco.*
import com.controleco.app.ui.settings.*
import com.controleco.app.ui.stats.StatsScreen
import com.controleco.app.ui.theme.*
import com.controleco.app.ui.transactions.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import java.io.File
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], application=AccBookApp::class, qualifiers="w390dp-h844dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class Release153UiTest {
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    private val app get()=RuntimeEnvironment.getApplication() as AccBookApp
    private fun waitFor(condition:()->Boolean) { ui.waitUntil(15000) { Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); condition() } }
    private fun text(value:String)=waitFor { ui.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty() }
    private fun ready(): Triple<LedgerViewModel,Long,Long> {
        waitFor { runBlocking { app.db.categoryDao().countAll()>0 } }
        runBlocking {
            app.db.transactionDao().deleteAll(); app.db.merchantRuleDao().clearAll()
            val defaults=(DefaultData.defaultExpenseCategories+DefaultData.defaultIncomeCategories).toSet()
            app.db.categoryDao().getAll().filter { it.name !in defaults }.forEach { app.db.categoryDao().deleteById(it.id) }
        }
        val prefs=app.getSharedPreferences("settings",0)
        prefs.edit().also { edit -> prefs.all.keys.filter { it.startsWith("category_budget_") }.forEach { edit.remove(it) } }.commit()
        val cat=runBlocking { app.db.categoryDao().getAll().first { it.name=="餐饮" }.id }
        val account=runBlocking { app.db.accountDao().getAll().first().id }
        return Triple(LedgerViewModel(app),cat,account)
    }
    private fun content(block:@Composable ()->Unit) { ui.setContent { AccBookTheme(darkTheme=false,accent=EcoAccent.White) {
        CompositionLocalProvider(LocalAnimationsEnabled provides false) { Box(Modifier.fillMaxSize()) { EcoBackdrop();block() } }
    } } }
    private fun shot(name:String)=ui.runOnIdle {
        val view=ui.activity.window.decorView
        val image=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        view.draw(Canvas(image))
        val file=File("build/release153-qa/$name.png").apply { parentFile!!.mkdirs() }
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
    }

    private fun dialogShot(name:String) = ui.runOnIdle {
        // PixelCopy redraw waits are unsupported by the host; draw the real dialog decor directly.
        val cls=Class.forName("android.view.WindowManagerGlobal")
        val manager=cls.getMethod("getInstance").invoke(null)
        val field=cls.getDeclaredField("mViews").apply { isAccessible=true }
        @Suppress("UNCHECKED_CAST")
        val views=field.get(manager) as List<android.view.View>
        val view=views.last { it.isShown && it.width>0 && it.height>0 }
        val image=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        view.draw(Canvas(image))
        val file=File("build/release153-qa/$name.png").apply { parentFile!!.mkdirs() }
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
    }

    @Test fun merchantSuggestionManualExceptionAndExplicitRuleChangeAreSeparate() {
        val (vm,food,account)=ready()
        val daily=runBlocking { app.db.categoryDao().getAll().first { it.name=="日常" }.id }
        runBlocking {
            app.db.merchantRuleDao().upsert(MerchantRuleEntity(matchKey="学校食堂",type=TxType.EXPENSE,categoryId=food))
            app.db.transactionDao().insert(TransactionEntity(type=TxType.EXPENSE,amountCents=500,categoryId=food,accountId=account,timestamp=1,counterparty="学校食堂"))
        }
        var returned=false
        content { QuickAddScreen(vm) { returned=true } }
        text("餐饮")
        ui.onNodeWithContentDescription("1").performClick()
        ui.onNodeWithContentDescription("2").performClick()
        ui.onNodeWithContentDescription("输入商户").performTextInput("学校")
        text("默认分类：餐饮")
        shot("merchant-suggestion")
        ui.onNodeWithText("学校食堂").performClick()
        ui.onNodeWithText("日常").performClick()
        ui.onAllNodes(isToggleable()).onFirst().assertIsOff()
        ui.onAllNodesWithText("记一笔").onLast().performClick()
        waitFor { returned }
        val rows=runBlocking { app.db.transactionDao().getAll() }
        assertEquals(food,rows.first { it.amountCents==500L }.categoryId)
        assertEquals(daily,rows.first { it.amountCents==1200L }.categoryId)
        assertEquals(food,runBlocking { app.db.merchantRuleDao().getAll().single().categoryId })
        waitFor { vm.merchantAssociatedCounts.value.values.singleOrNull()==2 }
        var done=false
        ui.runOnIdle { vm.saveTransaction(-1,TxType.EXPENSE,100,daily,account,2,"","学校食堂",rememberMerchantCategory=true,onResult={ assertNull(it);done=true }) }
        waitFor { done }
        assertTrue(runBlocking { app.db.transactionDao().getAll().all { it.categoryId==daily } })
    }

    @Test fun compactEditPreservesExistingExceptionAndManyCategoriesStayInSheet() {
        val (vm,food,account)=ready()
        val daily=runBlocking { app.db.categoryDao().getAll().first { it.name=="日常" }.id }
        val time=LocalDate.now().atTime(9,30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val id=runBlocking {
            repeat(20) { app.db.categoryDao().insert(CategoryEntity(name="演示分类$it",type=TxType.EXPENSE,sortOrder=20+it)) }
            app.db.merchantRuleDao().upsert(MerchantRuleEntity(matchKey="学校食堂",type=TxType.EXPENSE,categoryId=food))
            app.db.transactionDao().insert(TransactionEntity(type=TxType.EXPENSE,amountCents=3500,categoryId=daily,accountId=account,timestamp=time,counterparty="学校食堂",note="合成演示"))
        }
        var returned=false
        content { EditTransactionScreen(vm,id) { returned=true } }
        text("09:30")
        ui.onNodeWithText("保存修改").assertIsDisplayed()
        ui.onNodeWithText("删除这笔记录").assertDoesNotExist()
        ui.onNodeWithText("账户").assertDoesNotExist()
        ui.onNodeWithText("退款").assertIsDisplayed()
        ui.onAllNodes(isToggleable()).onFirst().assertIsOff()
        shot("compact-edit")
        ui.onNodeWithText("更多").performClick()
        text("更多分类")
        ui.waitForIdle()
        dialogShot("category-picker")
        ui.onNodeWithContentDescription("输入").performTextInput("演示分类19")
        ui.onNode(hasText("演示分类19") and !hasSetTextAction()).performClick()
        ui.onNodeWithText("更多分类").assertDoesNotExist()
        ui.onNodeWithText("09:30").assertExists()
        ui.onNodeWithText("保存修改").performClick()
        waitFor { returned }
        val saved=runBlocking { app.db.transactionDao().getById(id)!! }
        assertEquals(time,saved.timestamp)
        assertEquals("演示分类19",runBlocking { app.db.categoryDao().getAll().first { it.id==saved.categoryId }.name })
        assertEquals(food,runBlocking { app.db.merchantRuleDao().getAll().single().categoryId })
    }

    @Test fun selectedTimeIsSavedAndEditingNeverReplacesTheOriginalTime() {
        val (vm,food,account)=ready()
        val original=LocalDate.now().atTime(9,30,42).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val id=runBlocking { app.db.transactionDao().insert(TransactionEntity(type=TxType.EXPENSE,amountCents=500,
            categoryId=food,accountId=account,timestamp=original)) }
        var returned=false
        content { EditTransactionScreen(vm,id) { returned=true } }
        text("09:30")
        ui.onNodeWithContentDescription("选择时间").performClick()
        text("选择时间")
        val fields=ui.onAllNodes(hasSetTextAction())
        val size=fields.fetchSemanticsNodes().size
        fields[size-2].performTextReplacement("08")
        fields[size-1].performTextReplacement("15")
        ui.onNodeWithText("确定").performClick()
        text("08:15")
        ui.onNodeWithText("保存修改").performClick()
        waitFor { returned }
        val saved=runBlocking { app.db.transactionDao().getById(id)!! }
        val result=Instant.ofEpochMilli(saved.timestamp).atZone(ZoneId.systemDefault())
        assertEquals(8,result.hour)
        assertEquals(15,result.minute)
        assertEquals(LocalDate.now(),result.toLocalDate())
    }

    @Test fun editingAnotherMerchantUsesItsRuleButOpeningAnExceptionDoesNot() {
        val (vm,food,account)=ready()
        val categories=runBlocking { app.db.categoryDao().getAll() }
        val daily=categories.first { it.name=="日常" }.id
        val shopping=categories.first { it.name=="购物" }.id
        val id=runBlocking {
            app.db.merchantRuleDao().upsert(MerchantRuleEntity(matchKey="学校食堂",type=TxType.EXPENSE,categoryId=food))
            app.db.merchantRuleDao().upsert(MerchantRuleEntity(matchKey="学校超市",type=TxType.EXPENSE,categoryId=shopping))
            app.db.transactionDao().insert(TransactionEntity(type=TxType.EXPENSE,amountCents=500,categoryId=daily,
                accountId=account,timestamp=System.currentTimeMillis(),counterparty="学校食堂"))
        }
        var done=false
        content { EditTransactionScreen(vm,id) { done=true } }
        text("学校食堂")
        ui.onAllNodesWithText("日常").assertCountEquals(2) // selected value and direct chip
        ui.onNodeWithContentDescription("输入商户").performTextReplacement("学校超市")
        waitFor { ui.onAllNodesWithText("购物").fetchSemanticsNodes().size==2 }
        ui.onAllNodesWithText("购物").assertCountEquals(2)
        ui.onNodeWithText("保存修改").performClick()
        waitFor { done }
        assertEquals(shopping,runBlocking { app.db.transactionDao().getById(id)!!.categoryId })
        assertEquals(food,runBlocking { app.db.merchantRuleDao().getAll().first { it.matchKey=="学校食堂" }.categoryId })
    }

    @Test fun fiveCategoryBudgetFitsAndSixCategoryBudgetPinsHeaderWithCorrectUsage() {
        val (vm,food,account)=ready()
        val now=System.currentTimeMillis()
        val fifth=runBlocking { app.db.categoryDao().insert(CategoryEntity(name="第五分类",type=TxType.EXPENSE,sortOrder=8)) }
        val ids=runBlocking {
            val extra = app.db.categoryDao().getAll()
                .filter { it.type == TxType.EXPENSE && it.id != food && it.id != fifth }
                .take(3)
                .map { it.id }
            listOf(food, fifth) + extra
        }
        runBlocking {
            app.db.transactionDao().insert(TransactionEntity(type=TxType.EXPENSE,amountCents=5000,categoryId=food,accountId=account,timestamp=now))
            app.db.transactionDao().insert(TransactionEntity(type=TxType.EXPENSE,amountCents=20000,categoryId=food,accountId=account,timestamp=now,budgetExcluded=true))
        }
        ui.runOnIdle { ids.forEach { vm.setCategoryBudget(it,10000) } }
        content { StatsScreen(vm) }
        text("概览")
        waitFor { ui.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().isNotEmpty() }
        ui.onNode(hasScrollToIndexAction()).performScrollToIndex(5)
        ui.onNodeWithContentDescription("餐饮预算已用50%").assertIsDisplayed()
        ui.onNodeWithContentDescription("第五分类预算已用0%").assertIsDisplayed()
        val before=ui.onNodeWithTag("statsBudgetHeader").fetchSemanticsNode().boundsInRoot.top
        shot("budget-five")
        val sixth=runBlocking { app.db.categoryDao().insert(CategoryEntity(name="第六分类",type=TxType.EXPENSE,sortOrder=9)) }
        ui.runOnIdle { vm.setCategoryBudget(sixth,10000) }
        waitFor { vm.categoryBudgets.value.size==6 }
        ui.onNode(hasScrollToIndexAction()).performScrollToIndex(5)
        ui.onNode(hasScrollToIndexAction()).performTouchInput { swipeUp() }
        ui.onNodeWithText("分类预算").assertIsDisplayed()
        ui.onNodeWithContentDescription("第六分类预算已用0%").assertIsDisplayed()
        val after=ui.onNodeWithTag("statsBudgetHeader").fetchSemanticsNode().boundsInRoot.top
        assertTrue("Long list keeps the chart visible at the top: $before -> $after",after <= before)
        shot("budget-six")
    }

    @Test @Config(qualifiers="w360dp-h640dp") fun smallScreenKeepsSaveReachableAndFormCanScroll() {
        val (vm,food,account)=ready()
        val id=runBlocking { app.db.transactionDao().insert(TransactionEntity(type=TxType.EXPENSE,amountCents=500,
            categoryId=food,accountId=account,timestamp=System.currentTimeMillis(),counterparty="演示商户")) }
        content { EditTransactionScreen(vm,id,{}) }
        text("演示商户")
        ui.onNodeWithText("保存修改").assertIsDisplayed()
        ui.onNodeWithText("来源").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("保存修改").assertIsDisplayed()
        shot("edit-small-screen")
    }

    @Test fun exportUsesFullDateRangeAndDoesNotExportRefundEventsAsIncome() {
        val (vm,food,account)=ready()
        val start=LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        runBlocking {
            repeat(130) { index -> app.db.transactionDao().insert(TransactionEntity(type=TxType.EXPENSE,amountCents=100,categoryId=food,accountId=account,timestamp=start+index,counterparty="演示商户")) }
            app.db.transactionDao().insert(TransactionEntity(type=TxType.EXPENSE,amountCents=900,categoryId=food,accountId=account,timestamp=start-1))
            app.db.transactionDao().insert(TransactionEntity(type=TxType.INCOME,amountCents=100,categoryId=food,accountId=account,timestamp=start,refundMark=RefundMark.REFUND_INCOME))
        }
        assertEquals(131,runBlocking { vm.csvExportSnapshot().transactions.size })
        assertEquals(130,runBlocking { vm.csvExportSnapshot(start,start+86400000).transactions.size })
        var page by mutableIntStateOf(0)
        content { if(page==0) SettingsImportScreen({}, {}, { page=1 }) else LedgerExportScreen(vm,{}) }
        ui.onNodeWithText("导出账单").performClick()
        text("131 笔")
        shot("csv-export")
        ui.onNodeWithText("导出并保存 CSV").performScrollTo().performClick()
        waitFor { Shadows.shadowOf(ui.activity).peekNextStartedActivityForResult()!=null }
        val request=Shadows.shadowOf(ui.activity).nextStartedActivityForResult
        assertEquals(android.content.Intent.ACTION_CREATE_DOCUMENT,request.intent.action)
        assertEquals("text/csv",request.intent.type)
        val file=File(app.cacheDir,"ledger-exports").listFiles()!!.single()
        val rows=com.controleco.app.csv.CsvReader.parse(file.readText(Charsets.UTF_8))
        assertEquals(132,rows.size)
        val saved=File(app.cacheDir,"chosen-ledger.csv")
        ui.runOnIdle {
            Shadows.shadowOf(ui.activity).receiveResult(request.intent,android.app.Activity.RESULT_OK,
                android.content.Intent().setData(android.net.Uri.fromFile(saved)))
        }
        waitFor { saved.exists() && saved.length()==file.length() }
        assertArrayEquals(file.readBytes(),saved.readBytes())
    }
}
