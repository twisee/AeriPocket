package com.controleco.app.personal

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.controleco.app.ui.MainPagePager
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w390dp-h844dp")
class MainPagePagerTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()

    @Test fun leftwardDragAdvancesRightwardReturnsAndEdgesCannotNavigate() {
        var page = -1
        ui.setContent {
            val state = rememberPagerState(pageCount = { 4 })
            val settled = state.settledPage
            SideEffect { page = settled }
            MainPagePager(state) { Text("page $it", modifier = Modifier.fillMaxSize()) }
        }
        ui.onNodeWithTag("mainPagePager").performTouchInput { swipeRight() }
        ui.runOnIdle { assertEquals(0, page) }
        repeat(3) { expected ->
            ui.onNodeWithTag("mainPagePager").performTouchInput { swipeLeft() }
            ui.runOnIdle { assertEquals(expected + 1, page) }
        }
        ui.onNodeWithTag("mainPagePager").performTouchInput { swipeLeft() }
        ui.runOnIdle { assertEquals(3, page) }
        repeat(3) { expected ->
            ui.onNodeWithTag("mainPagePager").performTouchInput { swipeRight() }
            ui.runOnIdle { assertEquals(2 - expected, page) }
        }
        ui.onNodeWithTag("mainPagePager").performTouchInput { swipeUp() }
        ui.runOnIdle { assertEquals(0, page) }
    }

    @Test fun homeEdgeFollowsFingerThenReturnsToItsOriginalPosition() {
        ui.setContent {
            val state = rememberPagerState(pageCount = { 4 })
            MainPagePager(state) { Text("edge page $it", modifier = Modifier.fillMaxSize()) }
        }
        ui.onNodeWithTag("mainPagePager").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(120f, 0f), delayMillis = 240)
        }
        val dragged = ui.onNodeWithText("edge page 0").getUnclippedBoundsInRoot().left.value
        assertTrue("Outward dragging must move the home page slightly", dragged > 0f)
        ui.onNodeWithTag("mainPagePager").performTouchInput { up() }
        ui.waitForIdle()
        assertEquals(0f, ui.onNodeWithText("edge page 0").getUnclippedBoundsInRoot().left.value, .5f)
    }

    @Test fun horizontalChildGetsPriorityOverPageSwitching() {
        var page = -1
        var scroll: ScrollState? = null
        ui.setContent {
            val state = rememberPagerState(pageCount = { 4 })
            val settled = state.settledPage
            SideEffect { page = settled }
            MainPagePager(state) {
                val child = rememberScrollState(1000)
                SideEffect { scroll = child }
                Row(Modifier.fillMaxWidth().height(160.dp).horizontalScroll(child).testTag("childChart")) {
                    Box(Modifier.width(2000.dp).height(160.dp))
                }
            }
        }
        ui.onNodeWithTag("childChart").performTouchInput { swipeLeft() }
        ui.runOnIdle { assertEquals(0, page); assertTrue(requireNotNull(scroll).value > 1000) }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun oversizedHomeDrawingDoesNotCoverLedgerDuringDrag() {
        ui.setContent {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                val state = rememberPagerState(pageCount = { 2 })
                MainPagePager(state) { page ->
                    if (page == 0) {
                        Canvas(Modifier.fillMaxSize()) { drawRect(Color.Red, size = Size(size.width * 2f, size.height)) }
                    } else {
                        Box(Modifier.fillMaxSize().testTag("ledgerPage"))
                    }
                }
            }
        }
        ui.onNodeWithTag("mainPagePager").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(-width * .4f, 0f), delayMillis = 240)
        }
        val ledger = ui.onNodeWithTag("ledgerPage").getUnclippedBoundsInRoot()
        ui.runOnIdle {
            val view = ui.activity.window.decorView
            val frame = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(frame))
            val split = with(view.resources.displayMetrics) { (ledger.left.value * density).toInt() }
            assertTrue(split in 16 until frame.width - 16)
            assertEquals(android.graphics.Color.RED, frame.getPixel(split - 12, frame.height / 2))
            assertEquals(android.graphics.Color.WHITE, frame.getPixel(split + 12, frame.height / 2))
        }
        ui.onNodeWithTag("mainPagePager").performTouchInput { up() }
    }
}
