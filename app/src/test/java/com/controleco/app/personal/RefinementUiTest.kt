package com.controleco.app.personal

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.controleco.app.AccBookApp
import com.controleco.app.data.LedgerViewModel
import com.controleco.app.data.db.*
import com.controleco.app.ui.AccBookNav
import com.controleco.app.ui.common.AnimationPrefs
import com.controleco.app.ui.common.HelpOnboarding
import com.controleco.app.ui.profile.ProfileScreen
import com.controleco.app.ui.theme.AccBookTheme
import com.controleco.app.ui.theme.AccentThemeStore
import com.controleco.app.ui.theme.EcoAccent
import com.controleco.app.ui.transactions.QuickAddScreen
import com.controleco.app.ui.transactions.TransactionListScreen
import com.controleco.app.util.Money
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = AccBookApp::class, qualifiers = "w390dp-h844dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class RefinementUiTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()

    @Test fun themeLabelsStayInPlaceWhenPickingAColorInTheOtherRow() {
        val app = RuntimeEnvironment.getApplication() as AccBookApp
        val vm = LedgerViewModel(app)
        val previous = AccentThemeStore.current
        try {
            ui.runOnIdle { AccentThemeStore.set(app, EcoAccent.White) }
            ui.setContent {
                AccBookTheme(darkTheme = false, accent = AccentThemeStore.current) {
                    ProfileScreen(vm, {}, {}, {}, {}, {}, {})
                }
            }
            ui.waitUntil(15000) { ui.onAllNodesWithContentDescription("主题色").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithContentDescription("主题色").performClick()
            val labels = EcoAccent.entries.map { ui.onNodeWithText(it.label) }
            val originalBounds = labels.map { it.getUnclippedBoundsInRoot() }
            ui.onNodeWithText(EcoAccent.Meadow.label).performClick()
            assertEquals(EcoAccent.Meadow, AccentThemeStore.current)
            assertEquals(originalBounds, labels.map { it.getUnclippedBoundsInRoot() })
            ui.onNodeWithText(EcoAccent.White.label).performClick()
            assertEquals(originalBounds, labels.map { it.getUnclippedBoundsInRoot() })
        } finally {
            ui.runOnIdle { AccentThemeStore.set(app, previous) }
        }
    }

    @Test fun ledgerStopsAtTheCurrentAccountingMonth() {
        val app = RuntimeEnvironment.getApplication() as AccBookApp
        val db = app.db
        ui.waitUntil(15000) { runBlocking { db.categoryDao().countAll() > 0 } }
        val vm = LedgerViewModel(app)
        runBlocking {
            db.transactionDao().deleteAll()
            val category = db.categoryDao().getAll().first { it.type == TxType.EXPENSE }
            val account = db.accountDao().getAll().first()
            db.transactionDao().insert(TransactionEntity(
                type = TxType.EXPENSE,
                amountCents = 2600,
                categoryId = category.id,
                accountId = account.id,
                timestamp = System.currentTimeMillis(),
                counterparty = "上期测试商户",
            ))
        }
        ui.setContent { AccBookTheme(darkTheme = false) { TransactionListScreen(vm, {}, {}, {}) } }
        ui.waitUntil(15000) { ui.onAllNodesWithText("上期测试商户").fetchSemanticsNodes().isNotEmpty() }

        val current = vm.month.value
        val next = ui.onNodeWithContentDescription("下一周期")
        next.assertIsNotEnabled().performTouchInput { click() }
        assertEquals(current, vm.month.value)
        ui.onNodeWithText("上期测试商户").assertIsDisplayed()

        ui.onNodeWithContentDescription("上一周期").performClick()
        ui.waitUntil(5000) { vm.month.value == current.minusMonths(1) }
        next.assertIsEnabled().performClick()
        ui.waitUntil(5000) { vm.month.value == current }
        next.assertIsNotEnabled()
        ui.runOnIdle {
            assertFalse(vm.setMonth(current.plusMonths(1)))
            vm.setPeriodMode(com.controleco.app.data.PeriodMode.YEAR)
            vm.nextPeriod()
            assertEquals(current, vm.month.value)
        }
    }

    @Test fun approvedCategoriesCirclesAndSpendingCoverUseRealData() {
        val app = RuntimeEnvironment.getApplication() as AccBookApp
        ui.waitUntil(15000) { runBlocking { app.db.categoryDao().getAll().count { it.type == TxType.EXPENSE } >= 19 } }
        val vm = LedgerViewModel(app)
        ui.runOnIdle { AnimationPrefs.set(app, false); HelpOnboarding.markViewed(app); vm.setMonthlyBudget(200000); assertNull(vm.configureExperienceVault(true, 30000)) }
        runBlocking {
            val db = app.db
            db.transactionDao().deleteAll()
            val cat = db.categoryDao().getAll().first { it.name == "餐饮" && it.type == TxType.EXPENSE }.id
            val account = db.accountDao().getAll().first().id
            fun row(amount: Long, exp: Boolean = false, excluded: Boolean = false) = TransactionEntity(type = TxType.EXPENSE,
                amountCents = amount, categoryId = cat, accountId = account, timestamp = System.currentTimeMillis(),
                experienceBudget = exp, budgetExcluded = excluded)
            db.transactionDao().insert(row(5000).copy(refundedCents = 1000))
            db.transactionDao().insert(row(3000, exp = true))
            db.transactionDao().insert(row(2000, excluded = true))
        }
        var page by mutableIntStateOf(0)
        ui.setContent { AccBookTheme(darkTheme = false) {
            when (page) {
                0 -> QuickAddScreen(vm, {})
                1 -> ProfileScreen(vm, {}, {}, {}, {}, {}, {})
                2 -> TransactionListScreen(vm, {}, {}, {})
                else -> AccBookNav(vm)
            }
        } }
        ui.waitUntil(15000) { ui.onAllNodesWithText("更多").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("更多").performClick()
        ui.onNodeWithText("更多分类").assertExists()
        ui.onNodeWithText("交通").assertExists()
        ui.onNodeWithText("通讯").assertExists()
        shot("more-categories-native")
        // This is a modal panel: the first three are only on its underlying entry page.
        ui.onNodeWithText("交通").performClick()
        ui.onNodeWithContentDescription("展开补充信息").performClick()
        ui.onNodeWithContentDescription("计入体验预算").performScrollTo().performClick().assertIsOn()
        ui.onNodeWithContentDescription("不计入预算").performClick().assertIsOn()
        ui.onNodeWithContentDescription("计入体验预算").assertIsOff()
        shot("budget-circles-native")
        ui.runOnIdle { page = 1 }
        ui.waitUntil(15000) { ui.onAllNodesWithText("日常预算").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("¥").assertDoesNotExist()
        ui.onNodeWithText("预算管理").assertDoesNotExist()
        ui.onNodeWithText("日预算").assertDoesNotExist()
        ui.onNodeWithContentDescription("最近7天支出趋势").assertIsDisplayed()
        ui.onNodeWithText("年").assertDoesNotExist()
        shot("profile-budget-native")
        ui.onNodeWithText("日常预算").performClick()
        ui.onNodeWithTag("experienceBudgetBack", useUnmergedTree = true).assertExists()
        ui.waitUntil(15000) { ui.onAllNodesWithContentDescription("月夜庭院体验小金库").fetchSemanticsNodes().isNotEmpty() }
        shot("vault-native")
        ui.runOnIdle { page = 2 }
        ui.waitUntil(15000) { ui.onAllNodesWithText("本月花费").fetchSemanticsNodes().isNotEmpty() }
        ui.waitUntil(15000) { ui.onAllNodesWithText("¥90.00").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("本月结余").assertDoesNotExist()
        ui.waitUntil(15000) { ui.onAllNodesWithContentDescription("爱丽丝在集市接小票").fetchSemanticsNodes().isNotEmpty() }
        shot("ledger-spending-native")
        ui.onNodeWithText("年", useUnmergedTree = true).performClick()
        ui.onNodeWithText("本年花费").assertExists()
        ui.waitUntil(15000) { ui.onAllNodesWithText("¥90.00").fetchSemanticsNodes().isNotEmpty() }
        ui.runOnIdle { page = 3 }
        ui.waitUntil(15000) { ui.onAllNodesWithTag("homeCharacter").fetchSemanticsNodes().isNotEmpty() }
        val daily = (170_000L - 4_000L) / vm.currentPeriod.value.daysLeft(vm.today.value)
        ui.onNode(
            hasText("剩余日均 ${Money.formatYuanGroupedFixed(daily)}") and
                hasAnyAncestor(hasTestTag("homeRemainingDaily")),
        ).assertIsDisplayed()
        shot("home-remaining-daily")
        ui.onNodeWithTag("homeOverview").assertDoesNotExist()
        ui.onNodeWithTag("mainPagePager").performTouchInput { swipeRight() }
        ui.onNodeWithTag("homeCharacter").assertExists()
        ui.onNodeWithTag("mainPagePager").performTouchInput { swipeLeft() }
        ui.waitUntil(15000) { ui.onAllNodesWithText("本年花费").fetchSemanticsNodes().isNotEmpty() }
        val cover = ui.onNodeWithTag("aliceBalanceCard", useUnmergedTree = true)
        val ledgerCover = cover.getUnclippedBoundsInRoot()
        ui.onNodeWithText("概览").performClick()
        ui.waitUntil(15000) { ui.onAllNodesWithText("本年结余").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(ledgerCover, cover.getUnclippedBoundsInRoot())
        ui.waitUntil(15000) { ui.onAllNodesWithContentDescription("爱丽丝在书桌翻阅账本").fetchSemanticsNodes().isNotEmpty() }
        shot("overview-balance-native")
    }

    @Test fun profileChartOpensDetailAndCanScrubDailyAndCumulativeSpending() {
        val app = RuntimeEnvironment.getApplication() as AccBookApp
        val vm = LedgerViewModel(app)
        val db = app.db
        ui.waitUntil(15000) { runBlocking { db.categoryDao().countAll() > 0 } }
        val today = LocalDate.now()
        val weekStart = today.minusDays(6)
        val shortMonth = YearMonth.of(today.year - 1, 2)
        ui.runOnIdle {
            AnimationPrefs.set(app, false)
            HelpOnboarding.markViewed(app)
            vm.setMonthStartDay(1)
        }
        runBlocking {
            db.transactionDao().deleteAll()
            val account = db.accountDao().getAll().first()
            val categories = db.categoryDao().getAll().filter { it.type == TxType.EXPENSE }
            val category = categories.first()
            fun purchase(date: LocalDate, amount: Long, refunded: Long = 0, categoryId: Long = category.id) = TransactionEntity(
                type = TxType.EXPENSE,
                amountCents = amount,
                refundedCents = refunded,
                categoryId = categoryId,
                accountId = account.id,
                timestamp = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            )
            db.transactionDao().insert(purchase(weekStart, 2500, 1000))
            db.transactionDao().insert(purchase(today, 4000))
            db.transactionDao().insert(purchase(weekStart.minusDays(7), 2000))
            db.transactionDao().insert(purchase(shortMonth.atEndOfMonth(), 7700))
            db.transactionDao().insert(purchase(shortMonth.minusMonths(1).atEndOfMonth(), 5000, categoryId = categories[1].id))
        }
        ui.setContent { AccBookTheme(darkTheme = false) { AccBookNav(vm) } }
        ui.onNodeWithText("个人").performClick()
        ui.waitUntil(15000) { ui.onAllNodesWithContentDescription("最近7天支出趋势").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithContentDescription("最近7天支出趋势").performClick()
        ui.waitUntil(15000) { ui.onAllNodesWithText("本期支出").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("¥65.00").assertExists()
        ui.onNodeWithText("分类支出变化").assertExists()
        ui.onNodeWithText("按日明细").assertDoesNotExist()
        val plot = ui.onNodeWithTag("spendingTrendChart")
        val selection = ui.onNodeWithTag("trendSelectionDate")
        fun label(date: LocalDate) = "${date.monthValue}月${date.dayOfMonth}日"
        selection.assertTextEquals(label(today))
        plot.performTouchInput { swipeRight() }
        plot.performTouchInput { swipeLeft() }
        selection.assertTextEquals(label(weekStart))
        ui.onNodeWithText("本期 ¥25.00").assertExists()
        shot("spending-trend-detail")

        ui.onNodeWithTag("trendDatePicker").performClick()
        if (today.dayOfMonth > 1) {
            val earlier = today.minusDays(1)
            val locale = Locale.getDefault()
            val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "yMMMMEEEEd")
            val dateDescription = earlier.format(DateTimeFormatter.ofPattern(pattern, locale))
            ui.onNodeWithText(dateDescription).performClick()
            ui.onNodeWithText("确定").performClick()
            ui.waitUntil(15000) { ui.onAllNodesWithText(label(earlier)).fetchSemanticsNodes().isNotEmpty() }
            selection.assertTextEquals(label(earlier))
            ui.onNodeWithText("¥25.00").assertExists()
        } else {
            ui.onNodeWithText("取消").performClick()
        }

        ui.onNodeWithText("累计").performClick()
        ui.onNodeWithTag("trendDatePicker").performClick()
        ui.onNodeWithContentDescription("上一年").performClick()
        ui.onNodeWithText("2 月").performClick()
        ui.waitUntil(15000) { ui.onAllNodesWithText("¥77.00").fetchSemanticsNodes().isNotEmpty() }
        selection.assertTextEquals("${shortMonth.lengthOfMonth()}日累计")
        plot.performTouchInput { click(androidx.compose.ui.geometry.Offset(right - 1f, centerY)) }
        selection.assertTextEquals("31日累计")
        ui.onNodeWithText("2月 —").assertExists()
        ui.onNodeWithText("1月 ¥50.00").assertExists()
        assertTrue(ui.onNodeWithText("-¥50.00").getUnclippedBoundsInRoot().right.value < 195f)
        assertTrue(ui.onNodeWithText("+¥77.00").getUnclippedBoundsInRoot().left.value > 195f)
        shot("spending-trend-short-month")
    }

    private fun shot(name: String) = ui.runOnIdle {
        val view = ui.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView?.draw(canvas)
        val file = File("build/refinement-native/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
