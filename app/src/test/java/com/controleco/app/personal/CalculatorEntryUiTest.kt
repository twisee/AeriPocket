package com.controleco.app.personal

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import com.controleco.app.AccBookApp
import com.controleco.app.data.LedgerViewModel
import com.controleco.app.ui.eco.EcoBackdrop
import com.controleco.app.ui.eco.LocalAnimationsEnabled
import com.controleco.app.ui.theme.AccBookTheme
import com.controleco.app.ui.theme.EcoAccent
import com.controleco.app.ui.transactions.QuickAddScreen
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = AccBookApp::class, qualifiers = "w390dp-h844dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class CalculatorEntryUiTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private fun waitFor(condition: () -> Boolean) {
        ui.waitUntil(15000) { Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); condition() }
    }
    private fun screenshot(name: String) {
        ui.runOnIdle {
            val view = ui.activity.window.decorView
            val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image))
            val file = File("build/calculator-entry/$name.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Test fun merchantAndNoteCanReturnToAmountAndSaveCalculatedResult() {
        val app = RuntimeEnvironment.getApplication() as AccBookApp
        waitFor { runBlocking { app.db.categoryDao().countAll() > 0 } }
        runBlocking { app.db.transactionDao().deleteAll() }
        val vm = LedgerViewModel(app)
        var exited = false
        var accent by mutableStateOf(EcoAccent.Tide)
        var dark by mutableStateOf(false)
        ui.setContent {
            AccBookTheme(accent = accent, darkTheme = dark) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalAnimationsEnabled provides false,
                    LocalDensity provides Density(density.density, 1.25f)) {
                    Box(Modifier.fillMaxSize()) {
                        EcoBackdrop()
                        QuickAddScreen(vm) { exited = true }
                    }
                }
            }
        }
        waitFor { ui.onAllNodesWithText("餐饮").fetchSemanticsNodes().isNotEmpty() }
        for (key in listOf("1", "8", ".", "5")) ui.onNodeWithText(key).performClick()
        ui.onNodeWithContentDescription("加").performClick()
        for (key in listOf("1", "2")) ui.onNodeWithText(key).performClick()
        ui.onNodeWithContentDescription("乘").performClick()
        ui.onNodeWithText("2").performClick()
        ui.onNodeWithText("42.50").assertExists()
        ui.onNodeWithText("先乘除 · 后加减").assertDoesNotExist()
        screenshot("tide")
        for (theme in listOf(EcoAccent.White, EcoAccent.Amber, EcoAccent.Iris)) {
            ui.runOnIdle { accent = theme }
            screenshot(theme.id)
        }
        ui.runOnIdle { dark = true; accent = EcoAccent.Tide }
        screenshot("tide-dark")
        ui.runOnIdle { dark = false }
        ui.onNodeWithText("补充信息").performScrollTo().performClick()
        val fields = ui.onAllNodes(hasSetTextAction())
        fields[0].performScrollTo().performClick().performTextInput("瑞幸咖啡")
        fields[0].assertIsFocused()
        ui.onNodeWithContentDescription("修改金额").performScrollTo().performClick()
        fields[0].assertIsNotFocused()
        ui.onNodeWithContentDescription("退格").performClick()
        ui.onNodeWithText("3").performClick()
        ui.onNodeWithText("54.50").assertExists()
        fields[1].performScrollTo().performClick().performTextInput("计算器回归验证")
        fields[1].assertIsFocused()
        ui.onNodeWithContentDescription("修改金额").performScrollTo().performClick()
        fields[1].assertIsNotFocused()
        // Save directly without '=', using the same result shown above.
        ui.onAllNodesWithText("记一笔").onLast().performClick()
        waitFor { runBlocking { app.db.transactionDao().countAll() == 1 } }
        val saved = runBlocking { app.db.transactionDao().getAll().single() }
        assertEquals(5450L, saved.amountCents)
        assertEquals("瑞幸咖啡", saved.counterparty)
        assertEquals("计算器回归验证", saved.note)
        waitFor { exited }
    }
}
