package com.controleco.app.personal

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.controleco.app.AccBookApp
import com.controleco.app.data.*
import com.controleco.app.data.db.*
import com.controleco.app.ui.AccBookNav
import com.controleco.app.ui.common.AliceBudgetCard
import com.controleco.app.ui.eco.LocalAnimationsEnabled
import com.controleco.app.ui.personal.PersonalHome
import com.controleco.app.ui.theme.*
import com.controleco.app.ui.transactions.BalanceCard
import com.controleco.app.ui.transactions.CoverStyleStore
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = AccBookApp::class, qualifiers = "w390dp-h844dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class HomePolishUiTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val app get() = RuntimeEnvironment.getApplication() as AccBookApp
    private fun waitTag(tag: String) = ui.waitUntil(15000) {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
    private fun waitText(text: String) = ui.waitUntil(15000) {
        ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
    private fun waitActor() = ui.waitUntil(15000) {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        ui.onAllNodesWithContentDescription("爱丽丝，预算使用阶段 50%").fetchSemanticsNodes().isNotEmpty()
    }
    private fun shot(name: String): Bitmap {
        lateinit var bitmap: Bitmap
        ui.runOnIdle {
            val view = ui.activity.window.decorView
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("build/home-polish/$name.png").apply { parentFile!!.mkdirs() }
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        return bitmap
    }
    @Before fun seedReady() {
        ui.waitUntil(10000) { runBlocking { app.db.categoryDao().countAll() > 0 } }
    }
    @Test fun actualFourPagesShowApprovedCardsInLightAndDark() {
        runBlocking {
            app.db.transactionDao().deleteAll()
            val account = app.db.accountDao().getAll().first().id
            val category = app.db.categoryDao().getAll().first { it.name == "购物" }.id
            app.db.transactionDao().insert(TransactionEntity(type = TxType.EXPENSE, amountCents = 100000,
                accountId = account, categoryId = category, timestamp = System.currentTimeMillis(), note = "合成示例：卡片验收"))
        }
        CoverStyleStore.set(app, CoverStyleStore.NIGHT)
        val vm = LedgerViewModel(app)
        ui.runOnIdle { vm.setMonthlyBudget(200000) }
        var dark by mutableStateOf(false)
        ui.setContent { AccBookTheme(darkTheme = dark, accent = EcoAccent.Sunshine) {
            CompositionLocalProvider(LocalAnimationsEnabled provides false) { AccBookNav(vm) }
        } }
        for (night in listOf(false, true)) {
            ui.runOnIdle { dark = night }
            ui.onAllNodesWithText("首页").onLast().performClick()
            waitText("50%")
            waitActor()
            ui.onNodeWithText("本月预算").assertDoesNotExist()
            waitTag("homeSpentAmount")
            shot(if (night) "home_dark" else "home_light")
            ui.onAllNodesWithText("流水").onLast().performClick()
            waitTag("aliceBalanceCard"); waitTag("aliceFloralArtwork")
            ui.onNodeWithContentDescription("更换结余卡背景").assertExists()
            shot(if (night) "ledger_dark" else "ledger_light")
            ui.onNodeWithText("年").performClick()
            waitText("本年结余")
            ui.onNodeWithText("月").performClick()
            waitText("本月结余")
            ui.onAllNodesWithText("概览").onLast().performClick()
            waitTag("aliceBalanceCard"); waitTag("aliceFloralArtwork")
            ui.onNodeWithContentDescription("更换结余卡背景").assertDoesNotExist()
            shot(if (night) "overview_dark" else "overview_light")
            ui.onAllNodesWithText("个人").onLast().performClick()
            waitTag("aliceBudgetCard"); waitTag("aliceFloralArtwork")
            shot(if (night) "profile_dark" else "profile_light")
            ui.onNodeWithContentDescription("设置本月预算").performClick()
            waitText("月预算")
            ui.activity.onBackPressedDispatcher.onBackPressed()
        }
    }
    @Test fun budgetActionsAndCustomBackgroundPriorityAreRetained() {
        var currencyClicks = 0; var accountClicks = 0; var budgetClicks = 0
        var custom by mutableStateOf(false)
        val image = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLUE) }
        ui.setContent { AccBookTheme(accent = EcoAccent.White) {
            CompositionLocalProvider(LocalAnimationsEnabled provides false) {
                Column {
                    AliceBudgetCard(200000, 100000, .5f, "¥", { currencyClicks++ }, { accountClicks++ }, { budgetClicks++ })
                    BalanceCard(PeriodMode.MONTH, 400000, 500000, 100000, if (custom) "synthetic-image" else null,
                        if (custom) CardImage(image, null, android.graphics.Color.BLUE, .1f) else null,
                        onModeChange = {}, onOpenImageSettings = {})
                }
            }
        } }
        waitTag("aliceBalanceCard"); waitTag("aliceFloralArtwork")
        ui.onNodeWithText("¥").performClick()
        ui.onNodeWithText("账户").performClick()
        ui.onNodeWithContentDescription("设置本月预算").performClick()
        ui.runOnIdle { assertEquals(1, currencyClicks); assertEquals(1, accountClicks); assertEquals(1, budgetClicks); custom = true }
        ui.onNodeWithTag("aliceBalanceCard").assertDoesNotExist()
        ui.onNodeWithTag("aliceBudgetCard").assertExists()
        ui.onNodeWithContentDescription("更换结余卡背景").assertExists()
    }
    @Test fun homeMoneyEndsOnExactCentsUsesAccentAndDarkBackgroundHasNoStripes() {
        var spent by mutableLongStateOf(20000000)
        var dark by mutableStateOf(true)
        var accent by mutableStateOf(EcoAccent.Sunshine)
        ui.setContent { AccBookTheme(darkTheme = dark, accent = accent) {
            CompositionLocalProvider(LocalAnimationsEnabled provides true) { PersonalHome(spent, 40000000) {} }
        } }
        waitText("50%")
        waitActor()
        waitText("¥200000.00  /  ¥400000.00")
        ui.waitForIdle()
        ui.mainClock.autoAdvance = false
        ui.runOnIdle { spent = 20000001 }
        ui.mainClock.advanceTimeBy(200)
        ui.onNodeWithText("¥200000.01  /  ¥400000.00").assertDoesNotExist()
        ui.mainClock.advanceTimeBy(1300)
        ui.onNodeWithText("¥200000.01  /  ¥400000.00").assertExists()
        ui.mainClock.autoAdvance = true
        for ((night, theme) in listOf(true to EcoAccent.Sunshine, false to EcoAccent.Coral)) {
            ui.runOnIdle { dark = night; accent = theme }
            ui.waitForIdle()
            val bounds = ui.onNodeWithTag("homeSpentAmount").fetchSemanticsNode().boundsInRoot
            val rendered = shot("amount_${theme.id}_$night")
            val expected = ecoColors(night, theme).blue.toArgb()
            var accentPixels = 0
            for (y in bounds.top.toInt() until bounds.bottom.toInt()) for (x in bounds.left.toInt() until bounds.right.toInt()) {
                val pixel = rendered.getPixel(x, y)
                val distance = kotlin.math.abs(android.graphics.Color.red(pixel) - android.graphics.Color.red(expected)) +
                    kotlin.math.abs(android.graphics.Color.green(pixel) - android.graphics.Color.green(expected)) +
                    kotlin.math.abs(android.graphics.Color.blue(pixel) - android.graphics.Color.blue(expected))
                if (distance < 12) accentPixels++
            }
            assertTrue("Spent money actually renders in the current accent: $theme dark=$night", accentPixels > 10)
        }
        ui.runOnIdle { dark = true; accent = EcoAccent.Sunshine }
        ui.mainClock.advanceTimeBy(250)
        val bitmap = shot("dark_stripe_check")
        for (y in 621..754) {
            val a = bitmap.getPixel(389, y - 1); val b = bitmap.getPixel(389, y)
            assertTrue("No bright horizontal floor lines at y=$y", kotlin.math.abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)) < 10)
        }
        ui.mainClock.autoAdvance = true
    }
}
