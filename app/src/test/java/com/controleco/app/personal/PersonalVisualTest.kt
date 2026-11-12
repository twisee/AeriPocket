package com.controleco.app.personal

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.runtime.CompositionLocalProvider
import com.controleco.app.AccBookApp
import com.controleco.app.data.LedgerViewModel
import com.controleco.app.data.db.*
import com.controleco.app.ui.AccBookNav
import com.controleco.app.ui.eco.LocalAnimationsEnabled
import com.controleco.app.ui.personal.PersonalHome
import com.controleco.app.ui.theme.AccBookTheme
import com.controleco.app.ui.theme.EcoAccent
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], application=AccBookApp::class, qualifiers="w390dp-h844dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class PersonalVisualTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private fun shot(name: String) {
        val directory = File("build/personal-visual-qa");directory.mkdirs()
        ui.runOnIdle {
            val view=ui.activity.window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File(directory,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        }
    }
    private fun waitText(text: String) {
        try { ui.waitUntil(15000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
        catch(e:Throwable) { println(ui.onRoot(useUnmergedTree=true).printToString());throw e }
    }
    @Test fun renderHomeAndNativeBookkeepingPages() {
        val app=(RuntimeEnvironment.getApplication() as AccBookApp)
        // Wait for the native application's one-time seed before inserting synthetic UI examples.
        ui.waitUntil(10000) { runBlocking { app.db.categoryDao().countAll() > 0 } }
        val vm=LedgerViewModel(app)
        var homeOnly by mutableStateOf(true)
        var stage by mutableStateOf<Long?>(null)
        ui.setContent {
            AccBookTheme(darkTheme=false,accent=EcoAccent.White) {
                CompositionLocalProvider(LocalAnimationsEnabled provides false) {
                    if(homeOnly) PersonalHome(stage?.times(1000),100000) {} else AccBookNav(vm=vm)
                }
            }
        }
        // Initial loading has no 0%-stage character. The first loaded pose is 75%.
        ui.onNodeWithTag("homeCharacter").assertIsNotEnabled()
        ui.runOnIdle { stage=75 }
        ui.waitUntil(15000) { ui.onAllNodesWithContentDescription("爱丽丝，预算使用阶段 75%").fetchSemanticsNodes().isNotEmpty() }
        ui.waitUntil(15000) { ui.onNodeWithTag("homeCharacter").fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsActions.OnClick) }
        for(n in listOf(0L,25L,50L,75L,100L)) {
            ui.runOnIdle { stage=n }
            waitText("$n%")
            // Let the asynchronous atlas load complete before capturing.
            ui.waitUntil(15000) { ui.onAllNodesWithContentDescription("爱丽丝，预算使用阶段 $n%").fetchSemanticsNodes().isNotEmpty() }
            ui.waitForIdle()
            shot("home_$n")
        }
        val ids=runBlocking {
            val db=app.db
            // The screenshot's fixed 75% budget assumes only these two synthetic rows.
            db.transactionDao().deleteAll()
            val account=db.accountDao().getAll().first().id
            val category=db.categoryDao().getAll().first { it.name=="购物" }.id
            val now=System.currentTimeMillis()
            db.transactionDao().insert(TransactionEntity(type=TxType.EXPENSE,amountCents=2500,categoryId=category,
                accountId=account,timestamp=now-1000,counterparty="淘宝",note="合成演示：全额退款",refundMark=RefundMark.REFUNDED_EXPENSE,refundedCents=2500))
            db.transactionDao().insert(TransactionEntity(type=TxType.EXPENSE,amountCents=8000,categoryId=category,
                accountId=account,timestamp=now,counterparty="京东",note="合成演示：部分退款",refundedCents=2000))
        }
        ui.runOnIdle { homeOnly=false; vm.setMonthlyBudget(8000) }
        waitText("首页")
        waitText("75%")
        ui.waitUntil(15000) { ui.onAllNodesWithContentDescription("爱丽丝，预算使用阶段 75%").fetchSemanticsNodes().isNotEmpty() }
        shot("app_home")
        ui.onAllNodesWithText("流水").onLast().performClick();waitText("合成演示：部分退款");shot("ledger")
        ui.onNodeWithText("合成演示：部分退款").performClick();waitText("编辑记录");shot("detail")
        ui.activity.onBackPressedDispatcher.onBackPressed();waitText("流水")
        ui.onNodeWithContentDescription("记一笔").performClick();waitText("记一笔");shot("manual_entry")
        ui.activity.onBackPressedDispatcher.onBackPressed();waitText("流水")
        ui.onAllNodesWithText("概览").onLast().performClick();ui.waitForIdle();shot("overview")
        ui.onAllNodesWithText("个人").onLast().performClick();ui.waitForIdle();shot("settings")
    }
}
