package com.controleco.app.personal

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import com.controleco.app.AccBookApp
import com.controleco.app.data.LedgerViewModel
import com.controleco.app.data.db.*
import com.controleco.app.ui.eco.*
import com.controleco.app.ui.stats.StatsScreen
import com.controleco.app.ui.theme.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], application=AccBookApp::class, qualifiers="w390dp-h844dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class Overview154UiTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val app get() = RuntimeEnvironment.getApplication() as AccBookApp
    private fun waitFor(condition: () -> Boolean) = ui.waitUntil(15000) {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); condition()
    }
    private fun text(value: String) = waitFor { ui.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty() }
    private fun ready(): Pair<LedgerViewModel, Map<String, Long>> {
        waitFor { runBlocking { app.db.categoryDao().countAll() > 0 } }
        runBlocking { app.db.transactionDao().deleteAll() }
        val prefs = app.getSharedPreferences("settings", 0)
        prefs.edit().also { edit -> prefs.all.keys.filter { it.startsWith("category_budget_") }.forEach { edit.remove(it) } }.commit()
        val ids = runBlocking { app.db.categoryDao().getAll().associate { it.name to it.id } }
        return LedgerViewModel(app) to ids
    }
    private fun insert(category: Long, amount: Long, excluded: Boolean = false): Long = runBlocking {
        app.db.transactionDao().insert(TransactionEntity(type=TxType.EXPENSE, amountCents=amount,
            categoryId=category, accountId=app.db.accountDao().getAll().first().id,
            timestamp=System.currentTimeMillis(), budgetExcluded=excluded))
    }
    private fun content(vm: LedgerViewModel, dark: Boolean=false, scale: Float=1f) {
        ui.setContent { AccBookTheme(darkTheme=dark, accent=EcoAccent.White) {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalAnimationsEnabled provides false,
                LocalDensity provides Density(density.density, scale)) {
                Box(Modifier.fillMaxSize()) { EcoBackdrop(); StatsScreen(vm) }
            }
        } }
        text("概览")
        waitFor { ui.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().isNotEmpty() }
        ui.onNode(hasScrollToIndexAction()).performScrollToIndex(5)
    }
    private fun image(): Bitmap {
        val view = ui.activity.window.decorView
        return Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }
    private fun shot(name: String) = ui.runOnIdle {
        val file=File("build/release154-qa/$name.png").apply { parentFile!!.mkdirs() }
        file.outputStream().use { image().compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    private fun assertCategoryColorMatchesProgress(name: String) {
        val marker = ui.onNodeWithTag("statsCategoryMarker:$name",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot.center
        val fill = ui.onNodeWithTag("statsCategoryProgressFill:$name",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot.center
        ui.runOnIdle {
            val bitmap=image()
            val markerColor=bitmap.getPixel(marker.x.toInt(),marker.y.toInt())
            assertEquals("Category marker and progress fill keep the same rendered color",markerColor,
                bitmap.getPixel(fill.x.toInt(),fill.y.toInt()))
        }
    }

    @Test fun budgetExclusionUpdatesAmountsAndProgressWhileAllSpendingKeepsItsShare() {
        val (vm, ids)=ready()
        val food=ids.getValue("餐饮")
        val regular=insert(food,12000); insert(food,6000,true)
        insert(ids.getValue("日常"),4000); insert(ids.getValue("日常"),4000,true)
        insert(ids.getValue("购物"),2000); insert(ids.getValue("购物"),2000,true)
        ui.runOnIdle {
            vm.setCategoryBudget(food,10000); vm.setCategoryBudget(ids.getValue("日常"),10000)
            vm.setCategoryBudget(ids.getValue("购物"),30000)
        }
        content(vm)
        text("¥180 / ¥500")
        ui.onNodeWithText(java.time.YearMonth.now().let { "${it.year} / ${it.monthValue.toString().padStart(2, '0')}" }).assertDoesNotExist()
        ui.onNodeWithTag("statsCategoryAmount:餐饮",useUnmergedTree=true).assertTextEquals("¥120")
        ui.onNodeWithText("已用 120%").assertIsDisplayed()
        ui.onNodeWithText("超出 ¥20").assertIsDisplayed()
        assertEquals(1f, ui.onNodeWithTag("statsCategoryProgress:餐饮",useUnmergedTree=true).fetchSemanticsNode()
            .config[SemanticsProperties.ProgressBarRangeInfo].current)
        assertCategoryColorMatchesProgress("餐饮")
        assertCategoryColorMatchesProgress("日常")
        shot("budget-usage")
        ui.onNodeWithText("全部消费").performClick()
        text("全部消费分布")
        ui.onNodeWithTag("statsCategoryAmount:餐饮",useUnmergedTree=true).assertTextEquals("¥180")
        ui.onNodeWithText("占全部消费 60%").assertIsDisplayed()
        ui.onNodeWithTag("statsCategoryProgress:餐饮",useUnmergedTree=true).assertDoesNotExist()
        ui.onNodeWithText("体验及不计预算").assertIsDisplayed()
        shot("all-spending")
        ui.onNodeWithTag("statsCategoryRow:餐饮").performClick()
        ui.onNode(hasText("¥60.00") and hasAnyAncestor(hasTestTag("statsDonutRightAmount")),useUnmergedTree=true).assertExists()
        ui.onNodeWithTag("statsCategoryRow:餐饮").performClick()
        runBlocking {
            val row=app.db.transactionDao().getById(regular)!!
            app.db.transactionDao().update(row.copy(budgetExcluded=true))
        }
        ui.onNodeWithText("预算使用").performClick()
        text("¥60 / ¥500")
        ui.onNodeWithTag("statsCategoryAmount:餐饮",useUnmergedTree=true).assertTextEquals("¥0")
        ui.onNodeWithText("已用 120%").assertDoesNotExist()
        ui.onNodeWithContentDescription("餐饮预算已用0%").assertIsDisplayed()
        ui.onNodeWithText("全部消费").performClick()
        text("全部消费分布")
        ui.onNodeWithTag("statsCategoryAmount:餐饮",useUnmergedTree=true).assertTextEquals("¥180")
        ui.onNodeWithText("占全部消费 60%").assertIsDisplayed()
    }

    @Test @Config(qualifiers="w360dp-h640dp")
    fun smallDarkViewShowsUnbudgetedSpendingAndKeepsSwitchReachable() {
        val (vm,ids)=ready()
        insert(ids.getValue("餐饮"),10000)
        insert(ids.getValue("日常"),5000,true)
        content(vm, dark=true, scale=1.25f)
        ui.onNodeWithText("未设预算").assertIsDisplayed()
        ui.onNodeWithTag("statsCategoryProgress:餐饮",useUnmergedTree=true).assertDoesNotExist()
        ui.onNodeWithText("全部消费").performClick()
        text("全部消费分布")
        ui.onNodeWithTag("statsCategoryAmount:日常",useUnmergedTree=true).assertTextEquals("¥50")
        ui.onNodeWithText("占全部消费 33.3%").assertIsDisplayed()
        shot("all-spending-small-dark")
    }

    @Test fun exactlyFullBudgetShows100WithoutOverageAndKeepsCategoryColor() {
        val (vm,ids)=ready()
        insert(ids.getValue("餐饮"),10000)
        ui.runOnIdle { vm.setCategoryBudget(ids.getValue("餐饮"),10000) }
        content(vm)
        text("已用 100%")
        ui.onNodeWithText("超出",substring=true).assertDoesNotExist()
        assertCategoryColorMatchesProgress("餐饮")
        shot("budget-full")
    }

    @Test fun sixCategoriesKeepChartAndViewSwitchVisibleInBothViews() {
        val (vm,_) = ready()
        val ids = runBlocking { (1..6).map { app.db.categoryDao().insert(
            CategoryEntity(name="滚动分类$it",type=TxType.EXPENSE,sortOrder=30+it)) } }
        ids.forEachIndexed { index,id -> insert(id,(6-index)*1000L) }
        ui.runOnIdle { ids.forEach { vm.setCategoryBudget(it,10000) } }
        content(vm)
        ui.onNode(hasScrollToIndexAction()).performTouchInput { swipeUp() }
        ui.onNodeWithText("分类预算").assertIsDisplayed()
        ui.onNodeWithContentDescription("滚动分类6预算已用10%").assertIsDisplayed()
        val before=ui.onNodeWithTag("statsBudgetHeader").fetchSemanticsNode().boundsInRoot.top
        shot("six-budget")
        ui.onNodeWithText("全部消费").performClick()
        text("全部消费分布")
        ui.onNode(hasScrollToIndexAction()).performTouchInput { swipeUp() }
        ui.onNodeWithText("消费占比").assertIsDisplayed()
        ui.onNodeWithText("占全部消费 4.8%").assertIsDisplayed()
        val after=ui.onNodeWithTag("statsBudgetHeader").fetchSemanticsNode().boundsInRoot.top
        assertEquals(before,after,1f)
        shot("six-spending")
    }

    @Test fun emptySpendingCanSwitchToBudgetWithoutInventingPercentage() {
        val (vm,_) = ready()
        content(vm)
        ui.onNodeWithText("暂无分类预算或预算支出").assertExists()
        ui.onNodeWithText("全部消费").performClick()
        text("全部消费分布")
        ui.onNodeWithText("暂无消费记录").assertExists()
        ui.onNodeWithText("已用",substring=true).assertDoesNotExist()
        shot("empty-spending")
    }
}
