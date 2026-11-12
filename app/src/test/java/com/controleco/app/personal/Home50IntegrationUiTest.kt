package com.controleco.app.personal

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.controleco.app.AccBookApp
import com.controleco.app.ui.eco.*
import com.controleco.app.ui.personal.PersonalHome
import com.controleco.app.ui.theme.*
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
class Home50IntegrationUiTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private fun waitForStage(stage: Int) {
        ui.waitUntil(15000) {
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            ui.onAllNodesWithContentDescription("爱丽丝，预算使用阶段 $stage%").fetchSemanticsNodes().isNotEmpty()
        }
    }
    private fun shot(name: String) {
        val folder = File("build/home-50-integration").apply { mkdirs() }
        ui.runOnIdle {
            val view = ui.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Before fun waitForAppSeed() {
        val app = RuntimeEnvironment.getApplication() as AccBookApp
        ui.waitUntil(10000) { runBlocking { app.db.categoryDao().countAll() > 0 } }
    }
    @Test fun realHomepageShowsNew50OnlyAtTheApprovedBudgetRange() {
        var spent by mutableStateOf<Long?>(null)
        var overviewOpens = 0
        ui.setContent { AccBookTheme(accent = EcoAccent.White) {
            CompositionLocalProvider(LocalAnimationsEnabled provides false) {
                Box(Modifier.fillMaxSize()) {
                    PersonalHome(spent, 200000) { overviewOpens++ }
                    EcoBottomNav(0, {}, Modifier.align(Alignment.BottomCenter))
                }
            }
        } }
        ui.onNodeWithTag("homeCharacter").assertIsNotEnabled()
        for ((percent, stage) in listOf(19 to 0, 20 to 25, 39 to 25, 40 to 50, 59 to 50, 60 to 75, 79 to 75, 80 to 100, 100 to 100, 50 to 50)) {
            ui.runOnIdle { spent = percent * 2000L }
            waitForStage(stage)
            ui.waitForIdle()
            shot("home_$percent")
        }
        ui.onNodeWithTag("homeOverview").performClick()
        ui.runOnIdle { assertEquals(1, overviewOpens) }
    }
    @Test fun enabledAnimationsKeepClickBlinkAndReturnToOpenEyes() {
        var motion by mutableStateOf(true)
        ui.setContent { AccBookTheme(accent = EcoAccent.White) {
            CompositionLocalProvider(LocalAnimationsEnabled provides motion) {
                Box(Modifier.fillMaxSize()) {
                    PersonalHome(100000, 200000) {}
                    EcoBottomNav(0, {}, Modifier.align(Alignment.BottomCenter))
                }
            }
        } }
        waitForStage(50)
        ui.waitUntil(15000) { ui.onAllNodesWithText("50%").fetchSemanticsNodes().isNotEmpty() }
        ui.waitForIdle()
        ui.mainClock.autoAdvance = false
        shot("open_eyes")
        ui.onNodeWithTag("homeCharacter").performClick()
        ui.mainClock.advanceTimeBy(200)
        shot("click_closed_eyes")
        ui.mainClock.advanceTimeBy(1200)
        shot("click_finished")
        val folder = File("build/home-50-integration")
        assertFalse("Click has a visible response", File(folder, "open_eyes.png").readBytes().contentEquals(File(folder, "click_closed_eyes.png").readBytes()))
        assertTrue("Click finishes on the original open-eye frame", File(folder, "open_eyes.png").readBytes().contentEquals(File(folder, "click_finished.png").readBytes()))
        ui.mainClock.autoAdvance = true
        ui.runOnIdle { motion = false }
    }
    @Test fun heldPressReboundsAndDraggingStillOpensOverview() {
        var overviewOpens = 0
        ui.setContent { AccBookTheme(accent = EcoAccent.White) {
            CompositionLocalProvider(LocalAnimationsEnabled provides true) {
                PersonalHome(100000, 200000) { overviewOpens++ }
            }
        } }
        waitForStage(50)
        ui.waitUntil(15000) { ui.onAllNodesWithText("50%").fetchSemanticsNodes().isNotEmpty() }
        ui.waitForIdle()
        ui.mainClock.autoAdvance = false
        shot("pointer_original")
        ui.onNodeWithTag("homeCharacter").performTouchInput { down(androidx.compose.ui.geometry.Offset(30f, height * .8f)) }
        ui.mainClock.advanceTimeBy(160)
        shot("pointer_held")
        ui.onNodeWithTag("homeCharacter").performTouchInput { up() }
        ui.mainClock.advanceTimeBy(350)
        shot("pointer_released_closed")
        // A second tap must not restart or extend the active response.
        ui.onNodeWithTag("homeCharacter").performTouchInput { click(androidx.compose.ui.geometry.Offset(30f, height * .8f)) }
        ui.mainClock.advanceTimeBy(400)
        shot("pointer_repeat_open")
        ui.mainClock.advanceTimeBy(1000)
        shot("pointer_finished")
        val folder = File("build/home-50-integration")
        fun pixels(name: String) = BitmapFactory.decodeFile(File(folder, "$name.png").absolutePath)
        val original = pixels("pointer_original")
        assertFalse("Holding visibly compresses the whole character", original.sameAs(pixels("pointer_held")))
        assertTrue("Release returns exactly to the original artwork and scale", original.sameAs(pixels("pointer_finished")))
        // Float text stays on the left, so the face reveals whether the second tap restarted the blink.
        val face = android.graphics.Rect(155, 330, 260, 420)
        fun facePixels(bitmap: Bitmap) = Bitmap.createBitmap(bitmap, face.left, face.top, face.width(), face.height())
        assertFalse("Released response really closes the eyes", facePixels(original).sameAs(facePixels(pixels("pointer_released_closed"))))
        assertTrue("Repeated tap does not prolong closed eyes", facePixels(original).sameAs(facePixels(pixels("pointer_repeat_open"))))
        ui.onNodeWithText("¥1000.00  /  ¥2000.00").assertExists()
        ui.onNodeWithTag("homeCharacter").performTouchInput { swipeUp() }
        ui.runOnIdle { assertEquals("Character press must yield to the overview swipe", 1, overviewOpens) }
        ui.mainClock.autoAdvance = true
    }
}
