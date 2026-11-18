package com.controleco.app.personal

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.controleco.app.ui.common.AnimationPrefs
import com.controleco.app.ui.common.FlippableBudgetCard
import com.controleco.app.ui.theme.AccBookTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w390dp-h844dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExperienceCardTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()

    @Test fun flipFooterPlacementEditAndDisabling() {
        var enabled by mutableStateOf(true)
        var dark by mutableStateOf(false)
        var edits = 0
        AnimationPrefs.set(ui.activity, false)
        ui.setContent {
            AccBookTheme(darkTheme = dark) {
                Column(Modifier.fillMaxWidth().padding(22.dp)) {
                    FlippableBudgetCard(70000, 12000, "¥", {}, {}, { edits++ }, enabled, 128000, 30000, 12000)
                }
            }
        }
        ui.onNodeWithText("日常预算").assertExists()
        ui.onNodeWithText("17%").assertDoesNotExist()
        ui.onNodeWithContentDescription("设置本月预算").performClick()
        ui.runOnIdle { assertEquals(1, edits) }
        ui.onNodeWithText("日常预算").performClick()
        ui.onNodeWithTag("experienceBudgetBack", useUnmergedTree = true).assertExists()
        ui.onNodeWithText("体验小金库").assertExists()
        ui.waitUntil(15000) { ui.onAllNodesWithContentDescription("月夜庭院体验小金库").fetchSemanticsNodes().isNotEmpty() }
        val left = ui.onNodeWithText("本月新增", substring = true, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val right = ui.onNodeWithText("本月已用", substring = true, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(left.right <= right.left)
        assertEquals(left.top, right.top, 1f)
        shot("experience-light")
        ui.runOnIdle { dark = true }
        ui.waitForIdle(); shot("experience-dark")
        ui.runOnIdle { enabled = false }
        ui.onNodeWithTag("experienceBudgetBack", useUnmergedTree = true).assertDoesNotExist()
        ui.onNodeWithText("本月预算").assertExists()
        ui.onNodeWithText("本月预算").performClick()
        ui.onNodeWithTag("experienceBudgetBack", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun renderSingleSurfaceFlipAtIntermediateFrames() {
        AnimationPrefs.set(ui.activity, true)
        ui.setContent {
            AccBookTheme(darkTheme = false) {
                Column(Modifier.fillMaxWidth().padding(22.dp)) {
                    FlippableBudgetCard(170000, 4000, "¥", {}, {}, {}, true, 27000, 30000, 3000)
                }
            }
        }
        ui.waitUntil(15000) { ui.onAllNodesWithContentDescription("爱丽丝花卉卡片").fetchSemanticsNodes().isNotEmpty() }
        ui.waitForIdle()
        ui.mainClock.autoAdvance = false
        ui.onNodeWithTag("flippableBudgetCard").performClick()
        var elapsed = 0L
        for (point in listOf(80L, 180L, 300L, 400L, 560L)) {
            ui.mainClock.advanceTimeBy(point - elapsed)
            ui.waitForIdle()
            shot("flip-$point")
            elapsed = point
        }
        ui.mainClock.autoAdvance = true
        ui.onNodeWithTag("experienceBudgetBack", useUnmergedTree = true).assertExists()
    }

    private fun shot(name: String) = ui.runOnIdle {
        val view = ui.activity.window.decorView
        val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(image))
        val file = File("build/experience-preview/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
