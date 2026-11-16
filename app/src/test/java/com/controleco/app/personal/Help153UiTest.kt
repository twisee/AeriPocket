package com.controleco.app.personal

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import com.controleco.app.AccBookApp
import com.controleco.app.ui.eco.*
import com.controleco.app.ui.settings.SettingsHelpScreen
import com.controleco.app.ui.theme.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], application=AccBookApp::class, qualifiers="w390dp-h844dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class Help153UiTest {
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    private fun shot(name:String)=ui.runOnIdle {
        val view=ui.activity.window.decorView
        val image=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        view.draw(Canvas(image))
        val file=File("build/release153-qa/help-$name.png").apply { parentFile!!.mkdirs() }
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun updatedNativeHelpCanReadEntryMerchantBudgetCsvAndCaptureInBothThemes() {
        var dark by mutableStateOf(false)
        ui.setContent { AccBookTheme(darkTheme=dark,accent=EcoAccent.White) {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalAnimationsEnabled provides false, LocalDensity provides Density(density.density,1.25f)) {
                Box(Modifier.fillMaxSize()) { EcoBackdrop();SettingsHelpScreen({}) }
            }
        } }
        ui.onNodeWithText("新增与补记").assertIsDisplayed()
        ui.onNodeWithContentDescription("首页预算环：内圆环剩余天数，外圆环剩余预算").assertDoesNotExist()
        shot("entry")
        for(section in listOf("商户与分类","预算与概览","账单导入与导出","自动记账","漏记、重记与兼容性")) {
            ui.onNodeWithText(section).performScrollTo().assertIsDisplayed()
        }
        ui.onNodeWithText("导出通用CSV").performScrollTo().assertIsDisplayed()
        shot("csv")
        ui.runOnIdle { dark=true }
        ui.onNodeWithText("沿用规则与本次例外").performScrollTo().assertIsDisplayed()
        shot("merchant-dark")
        ui.onNodeWithText("银行应用与升级").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("如需反馈",substring=true).performScrollTo().assertIsDisplayed()
        shot("bottom-dark")
    }
}
