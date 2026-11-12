package com.controleco.app.personal

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.controleco.app.AccBookApp
import com.controleco.app.data.*
import com.controleco.app.data.db.*
import com.controleco.app.ui.AccBookNav
import com.controleco.app.ui.eco.LocalAnimationsEnabled
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
class LedgerNavigationUiTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val month = YearMonth.now()
    private lateinit var vm: LedgerViewModel
    private fun waitFor(block: () -> Boolean) {
        try { ui.waitUntil(15000) { Shadows.shadowOf(Looper.getMainLooper()).idle(); block() } }
        catch (e: Throwable) { println(ui.onRoot(useUnmergedTree = true).printToString()); throw e }
    }
    private fun exists(text: String) = ui.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    private fun openTab(text: String) { ui.onAllNodesWithText(text).onLast().performClick(); ui.waitForIdle() }
    @Before fun seed() {
        val app = RuntimeEnvironment.getApplication() as AccBookApp
        waitFor { runBlocking { app.db.categoryDao().countAll() > 0 } }
        runBlocking {
            val db = app.db
            db.transactionDao().deleteAll()
            val cat = db.categoryDao().getAll().first { it.name == "餐饮" }.id
            val account = db.accountDao().getAll().first().id
            for (offset in 0..2) for (day in 1..18) {
                val date = month.minusMonths(offset.toLong()).atDay(day)
                db.transactionDao().insert(TransactionEntity(type = TxType.EXPENSE, amountCents = (day + offset) * 100L,
                    categoryId = cat, accountId = account,
                    timestamp = date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    counterparty = "演示${offset}月${day}日", note = "合成导航测试"))
            }
        }
        vm = LedgerViewModel(app)
        vm.setMonthStartDay(1)
        com.controleco.app.ui.eco.EcoToast.clear()
        ui.setContent { AccBookTheme(accent = EcoAccent.White) { CompositionLocalProvider(LocalAnimationsEnabled provides false) { AccBookNav(vm) } } }
        waitFor { exists("首页") }
    }
    @After fun clearSyntheticRecords() {
        val app = RuntimeEnvironment.getApplication() as AccBookApp
        runBlocking { app.db.transactionDao().deleteAll() }
        vm.setMonthStartDay(1)
        com.controleco.app.ui.eco.EcoToast.clear()
    }
    @Test fun calendarRequiresConfirmationAndLedgerReentryPreservesPositionUntilReselected() {
        openTab("概览")
        waitFor { exists("支出日历") }
        ui.onNodeWithTag("spendingCalendar").performScrollTo()
        ui.onNodeWithTag("spendingDay-${month.atDay(2)}").performClick()
        ui.onNodeWithTag("spendingCalendar").assertExists()
        ui.onNodeWithTag("confirmSpendingDay").performScrollTo().performClick()
        waitFor { exists("演示0月2日") }
        ui.onNodeWithText("演示0月2日").assertIsDisplayed()
        assertEquals(month, vm.month.value)
        openTab("概览")
        ui.onNodeWithContentDescription("上一月").performScrollTo().performClick()
        waitFor { vm.month.value == month.minusMonths(1) }
        ui.onNodeWithTag("spendingCalendar").performScrollTo()
        ui.onNodeWithTag("spendingDay-${month.minusMonths(1).atDay(2)}").performClick()
        ui.onNodeWithTag("confirmSpendingDay").performScrollTo().performClick()
        waitFor { exists("演示1月2日") }
        ui.onNodeWithText("演示1月2日").assertIsDisplayed()
        openTab("流水")
        waitFor { vm.month.value == month && exists("演示0月18日") }
        ui.onNodeWithText("演示0月18日").assertIsDisplayed()
        openTab("概览")
        ui.onAllNodesWithText("年").onLast().performScrollTo().performClick()
        waitFor { ui.onAllNodesWithTag("spendingMonthGrid").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("spendingMonthGrid").performScrollTo()
        val target = month.minusMonths(1).takeIf { it.year == month.year } ?: month
        ui.onNodeWithTag("spendingMonth-$target").performClick()
        ui.onNodeWithTag("spendingMonthGrid").assertExists()
        assertEquals(PeriodMode.YEAR, vm.periodMode.value)
        ui.onNodeWithTag("confirmSpendingMonth").performScrollTo().performClick()
        waitFor { vm.month.value == target && vm.periodMode.value == PeriodMode.MONTH && exists("演示") }
        openTab("个人"); openTab("流水")
        waitFor { vm.month.value == target && exists("演示") }
        openTab("流水")
        waitFor { vm.month.value == month && exists("演示0月18日") }
        ui.onNodeWithText("演示0月18日").assertIsDisplayed()
    }
    @Test fun emptySelectedDayHasAnExactMarkerAndCanStillScrollIntoHistory() {
        openTab("概览")
        waitFor { exists("支出日历") }
        ui.onNodeWithTag("spendingCalendar").performScrollTo()
        ui.onNodeWithTag("spendingDay-${month.atDay(20)}").performClick()
        ui.onNodeWithTag("spendingCalendar").assertExists()
        ui.onNodeWithTag("confirmSpendingDay").performScrollTo().performClick()
        waitFor { exists("当天没有流水") }
        ui.onNodeWithText("当天没有流水").assertIsDisplayed()
        assertEquals(month, vm.month.value)
        ui.onNodeWithTag("ledgerList").performScrollToNode(hasText("演示0月18日"))
        ui.onNodeWithText("演示0月18日").assertIsDisplayed()
    }
    @Test fun emptyCurrentAndInterveningMonthsDoNotBlockHistory() {
        val app = RuntimeEnvironment.getApplication() as AccBookApp
        runBlocking {
            val dao = app.db.transactionDao()
            dao.getAll().filter {
                Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate() >= month.minusMonths(1).atDay(1)
            }.forEach { dao.deleteByIds(listOf(it.id)) }
        }
        openTab("流水")
        waitFor { exists("演示2月18日") }
        ui.onNodeWithText("演示2月18日").assertIsDisplayed()
        assertEquals(month.minusMonths(2), vm.month.value)
    }
    @Test fun detailReturnPreservesHistoricalDayAndAccountingCalendarCrossesNaturalMonths() {
        openTab("概览")
        waitFor { exists("支出日历") }
        ui.onNodeWithTag("spendingCalendar").performScrollTo()
        ui.onNodeWithTag("spendingDay-${month.atDay(5)}").performClick()
        ui.onNodeWithTag("confirmSpendingDay").performScrollTo().performClick()
        waitFor { exists("演示0月5日") }
        ui.onNodeWithText("演示0月5日").performClick()
        waitFor { exists("编辑记录") }
        ui.activity.onBackPressedDispatcher.onBackPressed()
        waitFor { exists("演示0月5日") }
        ui.onNodeWithText("演示0月5日").assertIsDisplayed()
        ui.runOnIdle { vm.setMonthStartDay(5) }
        openTab("概览")
        waitFor { exists("支出日历") }
        ui.onNodeWithTag("spendingCalendar").performScrollTo()
        val date = month.plusMonths(1).atDay(2)
        ui.onNodeWithTag("spendingDay-$date").performScrollTo().performClick()
        ui.onNodeWithText("查看 ${date.monthValue}月${date.dayOfMonth}日流水").assertExists()
        ui.onNodeWithTag("confirmSpendingDay").performScrollTo().performClick()
        waitFor { exists("当天没有流水") }
        assertEquals(month, vm.month.value)
    }
    private fun shot(name: String) {
        val folder = java.io.File("build/calendar-navigation-qa").apply { mkdirs() }
        ui.runOnIdle {
            val view = ui.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            java.io.File(folder, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Test fun renderCalendarConfirmationMonthlyGridAndFloralLedger() {
        openTab("流水")
        waitFor { exists("演示0月18日") }
        shot("floral_ledger")
        openTab("概览")
        waitFor { exists("支出日历") }
        ui.onNodeWithTag("spendingCalendar").performScrollTo()
        ui.onNodeWithTag("spendingDay-${month.atDay(2)}").performClick()
        ui.onNodeWithTag("confirmSpendingDay").performScrollTo()
        shot("day_confirmation")
        ui.onAllNodesWithText("年").onLast().performScrollTo().performClick()
        ui.onNodeWithTag("spendingMonthGrid").performScrollTo()
        ui.onNodeWithTag("spendingMonth-$month").performClick()
        ui.onNodeWithTag("confirmSpendingMonth").performScrollTo()
        shot("month_confirmation")
    }
    @Test fun scrollAppendsPreviousMonthAndDateHeaderFollowsWithoutReplacingList() {
        openTab("流水")
        waitFor { exists("演示0月18日") }
        // Real gestures exercise automatic prefetch rather than the explicit fallback button.
        repeat(10) {
            if (!exists("已加载 2 个月")) ui.onNodeWithTag("ledgerList").performTouchInput { swipeUp(durationMillis = 500) }
            ui.waitForIdle()
        }
        waitFor { exists("已加载 2 个月") }
        ui.onNodeWithTag("ledgerList").performScrollToNode(hasText("演示1月18日"))
        ui.onNodeWithTag("ledgerList").performTouchInput { swipeUp(durationMillis = 500) }
        waitFor { vm.month.value == month.minusMonths(1) }
        // Newer days remain in the same LazyColumn, and can be scrolled back into view.
        ui.onNodeWithTag("ledgerList").performScrollToIndex(0)
        waitFor { vm.month.value == month }
        ui.onNodeWithText("演示0月18日").assertIsDisplayed()
        openTab("流水")
        waitFor { exists("演示0月18日") }
        assertEquals(month, vm.month.value)
    }
}
