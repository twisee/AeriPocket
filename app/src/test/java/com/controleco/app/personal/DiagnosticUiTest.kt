package com.controleco.app.personal

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.controleco.app.AccBookApp
import com.controleco.app.capture.CaptureCenter
import com.controleco.app.capture.NotificationRules
import com.controleco.app.capture.page.PageNode
import com.controleco.app.capture.page.PageSnapshot
import com.controleco.app.data.LedgerViewModel
import com.controleco.app.diagnostics.CaptureDiagnostics
import com.controleco.app.ui.AccBookNav
import com.controleco.app.ui.eco.LocalAnimationsEnabled
import com.controleco.app.ui.theme.AccBookTheme
import com.controleco.app.ui.theme.EcoAccent
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import java.io.File
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = AccBookApp::class, qualifiers = "w390dp-h844dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class DiagnosticUiTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private fun waitFor(condition: () -> Boolean) {
        ui.waitUntil(15000) { Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); condition() }
    }
    private fun waitText(value: String) = waitFor { ui.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty() }
    private fun shot(name: String) = ui.runOnIdle {
        val view = ui.activity.window.decorView
        val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(image))
        val output = File("build/diagnostic-qa/$name.png").apply { parentFile!!.mkdirs() }
        output.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun developerEntryRecordsUnmatchedPageExportsAndStopsWithoutChangingEntryModes() {
        val app = RuntimeEnvironment.getApplication() as AccBookApp
        waitFor { runBlocking { app.db.categoryDao().countAll() > 0 } }
        runBlocking { CaptureDiagnostics.clear() }
        fun entrySettings() = listOf(CaptureCenter.isAutoEntryEnabled(), CaptureCenter.isOverlayConfirmEnabled(), CaptureCenter.isCaptureNotifyEnabled(),
            CaptureCenter.isCaptureEnabled(), CaptureCenter.isA11yCaptureEnabled(), CaptureCenter.isDebugCaptureAllPages(false))
        val before = entrySettings()
        val vm = LedgerViewModel(app)
        ui.setContent { AccBookTheme(accent = EcoAccent.Sunshine) {
            CompositionLocalProvider(LocalAnimationsEnabled provides false) { AccBookNav(vm) }
        } }
        waitText("个人")
        ui.onNodeWithText("个人").performClick(); waitText("我的")
        ui.onNodeWithText("设置").performClick(); waitText("自动记账")
        ui.onNodeWithText("开发者功能").performScrollTo().performClick(); waitText("记账诊断")
        ui.onNodeWithText("记账诊断").performScrollTo().performClick(); waitText("开启记账诊断")
        ui.onNodeWithText("开启记账诊断").performClick()
        waitFor { CaptureDiagnostics.enabled() }
        assertEquals(before, entrySettings())
        CaptureCenter.setA11yCaptureEnabled(true)
        val now = System.currentTimeMillis()
        val snapshot = PageSnapshot(packageName = NotificationRules.ALIPAY, captureTime = now,
            nodes = listOf("松开刷新", "扫一扫", "消息盒子", "付款成功 ¥15.00", "手机号13812345678").map { PageNode(text = it) })
        CaptureCenter.onPageCaptured(snapshot, isDebugBuild = false)
        waitFor { CaptureDiagnostics.state.value.summary.recent.any { it.kind == "page" && it.status == "未匹配" } }
        CaptureCenter.setAutoEntryEnabled(false)
        CaptureCenter.setOverlayConfirmEnabled(false)
        CaptureCenter.setCaptureNotifyEnabled(true)
        val success = PageSnapshot(packageName = NotificationRules.ALIPAY, captureTime = now + 1,
            nodes = listOf("支付成功", "￥", "15.00", "演示面馆", "付款方式", "支付宝余额").map { PageNode(text = it) })
        CaptureCenter.onPageCaptured(success, isDebugBuild = false)
        waitFor { CaptureDiagnostics.state.value.summary.recent.any { it.kind == "outcome" && it.status == "已入账" } }
        assertTrue(runBlocking { app.db.transactionDao().getAll().any { it.amountCents == 1500L } })
        shot("diagnostic-recording")
        ui.onNodeWithText("导出诊断日志").performScrollTo().performClick()
        waitText("导出并分享诊断包")
        shot("diagnostic-export")
        val exported = runBlocking { CaptureDiagnostics.export() }
        // Android FileProvider uses '/' for canonical roots; Windows-hosted Robolectric uses '\\'.
        // Verify packaged grant configuration here, and exercise native URI opening on Unix hosts.
        if (File.separatorChar == '/') {
            val uri = androidx.core.content.FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", exported)
            assertEquals("content", uri.scheme)
            app.contentResolver.openInputStream(uri)!!.use { assertTrue(it.read() >= 0) }
        } else {
            val provider = app.packageManager.resolveContentProvider("${app.packageName}.fileprovider", 0)!!
            assertFalse(provider.exported); assertTrue(provider.grantUriPermissions)
            val xml = app.resources.getXml(com.controleco.app.R.xml.file_paths)
            var allowed = false
            while (xml.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (xml.eventType == org.xmlpull.v1.XmlPullParser.START_TAG && xml.name == "cache-path" &&
                    xml.getAttributeValue(null, "path") == "diagnostic-exports/") allowed = true
                xml.next()
            }
            xml.close(); assertTrue(allowed)
        }
        ZipFile(exported).use { zip ->
            val lines = zip.entries().asSequence().filter { it.name.endsWith(".jsonl") }
                .flatMap { zip.getInputStream(it).reader().readText().lineSequence().filter(String::isNotBlank).toList().asSequence() }.toList()
            val records = lines.map(::JSONObject)
            val page = records.first { it.getString("kind") == "page" && it.getString("status") == "未匹配" }
            assertTrue(page.getString("detail").contains("前两个有效文字节点"))
            assertTrue(page.getJSONObject("fields").getString("text").contains("15.00"))
            assertFalse(lines.joinToString().contains("13812345678"))
            val parsed = records.first { it.getString("kind") == "page" && it.getString("status") == "已解析" }
            val entered = records.first { it.getString("kind") == "outcome" && it.getString("status") == "已入账" }
            assertEquals(parsed.getJSONObject("fields").getString("identity"), entered.getJSONObject("fields").getString("identity"))
            assertEquals(7, JSONObject(zip.getInputStream(zip.getEntry("manifest.json")).reader().readText()).getInt("retentionDays"))
        }
        ui.onNodeWithText("保存到文件").performScrollTo().performClick()
        var request: org.robolectric.shadows.ShadowActivity.IntentForResult? = null
        waitFor {
            request = request ?: Shadows.shadowOf(ui.activity).nextStartedActivityForResult
            request?.intent?.action == android.content.Intent.ACTION_CREATE_DOCUMENT
        }
        assertEquals("application/zip", request!!.intent.type)
        val saved = File(app.cacheDir, "saved-diagnostics.zip")
        Shadows.shadowOf(ui.activity).receiveResult(request!!.intent, android.app.Activity.RESULT_OK,
            android.content.Intent().setData(android.net.Uri.fromFile(saved)))
        waitFor { saved.exists() && saved.length() > 0 }
        ZipFile(saved).use { assertNotNull(it.getEntry("manifest.json")) }
        ui.runOnIdle { ui.activity.onBackPressedDispatcher.onBackPressed() }
        waitText("开启记账诊断")
        ui.runOnIdle { ui.activity.onBackPressedDispatcher.onBackPressed() }
        waitText("捕获日志")
        ui.onNodeWithText("捕获日志").performScrollTo().performClick()
        waitText("仅捕获页面")
        ui.onAllNodesWithText("已识别").assertCountEquals(0)
        shot("capture-status")
        CaptureDiagnostics.setEnabled(false)
        runBlocking { CaptureDiagnostics.refresh() }
        val count = CaptureDiagnostics.state.value.summary.count
        CaptureDiagnostics.record("page", "支付宝", "未匹配", "关闭后不应写入的内容")
        runBlocking { CaptureDiagnostics.refresh() }
        assertEquals(count, CaptureDiagnostics.state.value.summary.count)
        runBlocking { CaptureDiagnostics.clear() }
        assertEquals(0L, CaptureDiagnostics.state.value.summary.count)
        assertFalse(exported.exists())
    }
}
