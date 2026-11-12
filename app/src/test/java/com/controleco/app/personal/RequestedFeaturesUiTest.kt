package com.controleco.app.personal

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.controleco.app.AccBookApp
import com.controleco.app.data.LedgerViewModel
import com.controleco.app.data.db.*
import com.controleco.app.data.model.*
import com.controleco.app.capture.MerchantRules
import com.controleco.app.ui.AccBookNav
import com.controleco.app.ui.eco.LocalAnimationsEnabled
import com.controleco.app.ui.theme.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import java.io.File
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=AccBookApp::class,qualifiers="w390dp-h844dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class RequestedFeaturesUiTest {
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    private fun waitFor(block:()->Boolean) { ui.waitUntil(15000) { Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();block() } }
    private fun waitText(text:String) {
        try { waitFor { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
        catch(e:Throwable) { println(ui.onRoot(useUnmergedTree=true).printToString());throw e }
    }
    private fun shot(name:String) {
        val dir=File("build/requested-feature-qa");dir.mkdirs()
        ui.runOnIdle {
            val activityView=ui.activity.window.decorView
            val dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }
            val view=dialog?.window?.decorView ?: activityView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            val canvas=Canvas(bitmap)
            if(view!==activityView) {
                activityView.draw(canvas)
                val amount=dialog?.window?.attributes?.dimAmount ?: .32f
                canvas.drawColor(android.graphics.Color.argb((amount*255).toInt(),0,0,0))
            }
            view.draw(canvas)
            File(dir,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        }
    }
    @Test fun calendarMerchantHistoryAndAccessibilityAreReachable() {
        val app=RuntimeEnvironment.getApplication() as AccBookApp
        waitFor { runBlocking { app.db.categoryDao().countAll()>0 } }
        val db=app.db;val dao=db.transactionDao();val month=YearMonth.now();val zone=ZoneId.systemDefault()
        val account=runBlocking { db.accountDao().getAll().first().id }
        val cats=runBlocking { db.categoryDao().getAll() };val shopping=cats.first { it.name=="购物" }.id
        val dining=cats.first { it.name=="餐饮" }.id;val salary=cats.first { it.name=="工资" }.id
        runBlocking {
            dao.deleteAll();db.merchantRuleDao().clearAll()
            for(day in 1..minOf(18,month.lengthOfMonth())) dao.insert(TransactionEntity(type=TxType.EXPENSE,
                amountCents=(day%5+1)*1500L,categoryId=shopping,accountId=account,
                timestamp=month.atDay(day).atTime(12,0).atZone(zone).toInstant().toEpochMilli(),
                counterparty=if(day==1 || day==2) "江西理工大学" else "演示商户$day",note="合成演示记录"))
            dao.insert(TransactionEntity(type=TxType.INCOME,amountCents=10000,categoryId=salary,accountId=account,
                timestamp=month.atDay(1).atTime(8,0).atZone(zone).toInstant().toEpochMilli(),counterparty="江西理工大学"))
        }
        val vm=LedgerViewModel(app)
        ui.runOnIdle { vm.setMonthlyBudget(200000) }
        ui.setContent { AccBookTheme(accent=EcoAccent.White) { CompositionLocalProvider(LocalAnimationsEnabled provides false) { AccBookNav(vm=vm) } } }
        waitText("首页");ui.onNodeWithText("概览").performClick();waitText("支出日历")
        ui.onNodeWithTag("spendingCalendar").performScrollTo()
        ui.onNodeWithContentDescription("${month.atDay(1)} 支出 ¥30.00").assertExists()
        ui.onRoot().performTouchInput { swipeUp(startY=650f,endY=590f,durationMillis=1000) }
        shot("spending_calendar")
        ui.onAllNodesWithText("流水").onLast().performClick();waitText("流水")
        ui.onNodeWithText("个人").performClick();waitText("我的")
        ui.onNodeWithText("设置").performClick();waitText("无障碍页面记账");shot("settings_accessibility")
        ui.onNodeWithText("商户归类").performScrollTo().performClick();waitText("新增规则")
        ui.onNodeWithText("新增规则").performClick();waitText("新增商户规则")
        ui.onAllNodes(hasSetTextAction()).onFirst().performTextInput("江西理工大学")
        ui.onAllNodesWithText("餐饮").onLast().performClick();shot("merchant_editor")
        ui.onNodeWithText("保存规则").performClick()
        waitFor { runBlocking { dao.getAll().filter { it.counterparty=="江西理工大学" && it.type==TxType.EXPENSE }.all { it.categoryId==dining } } }
        assertEquals(salary,runBlocking { dao.getAll().first { it.type==TxType.INCOME }.categoryId })
        assertTrue(runBlocking { dao.getAll().filter { it.counterparty.startsWith("演示商户") }.all { it.categoryId==shopping } })
        // Editing the explicit rule must update the same historical merchant again.
        ui.onNodeWithText("修改").performClick();waitText("修改商户规则")
        ui.onAllNodesWithText("日常").onLast().performClick();ui.onNodeWithText("保存规则").performClick()
        val daily=cats.first { it.name=="日常" }.id
        waitFor { runBlocking { dao.getAll().filter { it.counterparty=="江西理工大学" && it.type==TxType.EXPENSE }.all { it.categoryId==daily } } }
        val future=ImportedBillRow(TxType.EXPENSE,1200,System.currentTimeMillis(),"江西理工大学","合成后续消费","合成记录","余额","SYNTHETIC-NEXT","购物")
        ui.runOnIdle { vm.commitImport(ParsedBill(BillSource.ALIPAY,listOf(future),0,0,0),account) }
        waitFor { vm.importDone.value!=null || vm.importError.value!=null };assertNull(vm.importError.value)
        assertEquals(daily,runBlocking { dao.getAll().first { it.externalId=="SYNTHETIC-NEXT" }.categoryId })
        shot("merchant_rules")
        // Explicit user rules may include platforms; automatic learning still rejects generic platform words.
        assertNull(MerchantRules.keyOf("淘宝",""));assertEquals("淘宝",MerchantRules.explicitKeyOf("淘宝"))
        ui.activity.onBackPressedDispatcher.onBackPressed();waitText("无障碍页面记账")
        ui.onNode(hasAnyAncestor(hasTestTag("a11yCaptureToggleRow")) and hasClickAction()).performClick()
        waitFor { vm.a11yCaptureEnabled.value }
        ui.onNodeWithText("无障碍授权与教程").performClick();waitText("无障碍页面采集")
        ui.onNodeWithText("自动记录支付页面").performScrollTo().assertIsDisplayed();shot("accessibility_permissions")
        val service=app.packageManager.getPackageInfo(app.packageName,android.content.pm.PackageManager.GET_SERVICES).services.orEmpty()
        assertTrue(service.any { it.permission=="android.permission.BIND_ACCESSIBILITY_SERVICE" })
    }
}
