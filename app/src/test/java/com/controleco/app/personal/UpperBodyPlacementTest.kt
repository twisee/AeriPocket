package com.controleco.app.personal

import android.app.Application
import com.controleco.app.ui.personal.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UpperBodyPlacementTest {
    @Test fun upperBodyKeepsVisibleArtWithinNarrowAndWideScreens() {
        val atlas = ActorAtlas.load(RuntimeEnvironment.getApplication(), "characters/aerith/pose_50_upper_body.png", true)
        val bounds = requireNotNull(atlas.visibleBounds)
        for (density in listOf(1f, 2.75f)) for (widthDp in listOf(320f, 390f, 480f)) {
            val width = widthDp * density
            val height = 566f * density
            val placement = atlas.placement(width, height, 50, false, 12f * density, 8f * density)
            val left = placement.left + bounds.first.toFloat() * placement.side / atlas.frameWidth
            val right = placement.left + (bounds.last + 1f) * placement.side / atlas.frameWidth
            assertTrue("Notebook/braid left margin", left >= 12f * density - 2)
            assertTrue("Notebook/braid right margin", right <= width - 12f * density + 2)
            assertTrue("Head remains inside scene", placement.top >= 0)
            assertEquals(height - 8f * density, (placement.top + placement.side).toFloat(), 1f)
        }
    }
    @Test fun blinkingKeepsTheBodyAnchorAndScaleStable() {
        val atlas = ActorAtlas.load(RuntimeEnvironment.getApplication(), "characters/aerith/pose_50_upper_body.png", true)
        val open = atlas.placement(390f, 566f, 50, false, 12f, 8f)
        val closed = atlas.placement(390f, 566f, 50, true, 12f, 8f)
        assertEquals(open.side, closed.side)
        assertEquals(open.top, closed.top)
        val openAnchor = open.left + atlas.openAnchor * open.side / atlas.frameWidth
        val closedAnchor = closed.left + atlas.closedAnchor * closed.side / atlas.frameWidth
        assertEquals(openAnchor, closedAnchor, 1f)
    }
    @Test fun otherStagesKeepTheirExistingPlacement() {
        for (stage in listOf(0, 25, 75, 100)) {
            val atlas = ActorAtlas.load(RuntimeEnvironment.getApplication(), "characters/aerith/pose_$stage.png")
            assertNull(atlas.visibleBounds)
            for (closed in listOf(false, true)) {
                val expected = actorPlacement(390f, 566f, 0f, stage, atlas.frameWidth,
                    atlas.openAnchor, atlas.closedAnchor, closed, 0f, 0f)
                assertEquals(expected, atlas.placement(390f, 566f, stage, closed, 12f, 8f))
            }
        }
    }
}
