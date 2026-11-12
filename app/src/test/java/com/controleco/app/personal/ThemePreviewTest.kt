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
import com.controleco.app.ui.eco.LocalAnimationsEnabled
import com.controleco.app.ui.theme.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*
import java.io.File

/** Real ECO screens with synthetic records; palette review only, no private bill input. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=AccBookApp::class,qualifiers="w390dp-h844dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class ThemePreviewTest {
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    private fun waitText(text:String) {
        ui.waitUntil(15000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun shot(name:String) {
        val folder=File("build/theme-preview");folder.mkdirs()
        ui.runOnIdle {
            val view=ui.activity.window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File(folder,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        }
    }
    @Test fun renderBlackWhiteAndWarmYellowOnTheSameEcoPages() {
        val app=RuntimeEnvironment.getApplication() as AccBookApp
        ui.waitUntil(10000) { runBlocking { app.db.categoryDao().countAll()>0 } }
        val db=app.db
        runBlocking {
            db.transactionDao().deleteAll()
            val account=db.accountDao().getAll().first().id
            val category=db.categoryDao().getAll().first { it.name=="购物" }.id
            val now=System.currentTimeMillis()
            db.transactionDao().insert(TransactionEntity(type=TxType.EXPENSE,amountCents=60000,categoryId=category,
                accountId=account,timestamp=now,counterparty="京东",note="演示账单  不含真实消费"))
            db.transactionDao().insert(TransactionEntity(type=TxType.EXPENSE,amountCents=2500,categoryId=category,
                accountId=account,timestamp=now-1000,counterparty="淘宝",note="演示账单  全额退款",
                refundMark=RefundMark.REFUNDED_EXPENSE,refundedCents=2500))
        }
        val vm=LedgerViewModel(app)
        var accent by mutableStateOf(EcoAccent.White)
        ui.runOnIdle { vm.setMonthlyBudget(80000) }
        ui.setContent {
            AccBookTheme(darkTheme=false,accent=accent) {
                CompositionLocalProvider(LocalAnimationsEnabled provides false) { AccBookNav(vm=vm) }
            }
        }
        for(theme in listOf(EcoAccent.White,EcoAccent.Sunshine)) {
            ui.runOnIdle { accent=theme }
            ui.onAllNodesWithText("首页").onLast().performClick()
            waitText("75%")
            ui.waitUntil(15000) { ui.onAllNodesWithContentDescription("爱丽丝，预算使用阶段 75%").fetchSemanticsNodes().isNotEmpty() }
            shot("${theme.id}_home")
            ui.onAllNodesWithText("流水").onLast().performClick()
            waitText("演示账单  不含真实消费");shot("${theme.id}_ledger")
            ui.onAllNodesWithText("个人").onLast().performClick()
            waitText("我的");shot("${theme.id}_profile")
            // Settings is a nested destination, not a fifth or renamed main tab.
            ui.onNodeWithText("设置").performClick();waitText("自动记账");shot("${theme.id}_settings")
            ui.activity.onBackPressedDispatcher.onBackPressed();waitText("我的")
        }
    }
}
