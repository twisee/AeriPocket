package com.controleco.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.controleco.app.ui.eco.LocalAnimationsEnabled

/** Leftward finger movement advances pages. Only the main shell owns this gesture. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MainPagePager(state: PagerState, content: @Composable (Int) -> Unit) {
    val motion = LocalAnimationsEnabled.current
    val maxEdge = with(LocalDensity.current) { 36.dp.toPx() }
    var edge by remember { mutableFloatStateOf(0f) }
    val release = remember { Animatable(0f) }
    val connection = remember(state, motion, maxEdge) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && edge != 0f && available.x * edge < 0) {
                    val next = edge + available.x * .24f
                    edge = if (next * edge < 0) 0f else next
                    return Offset(available.x, 0f)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val boundary = (state.currentPage == 0 && available.x > 0) ||
                    (state.currentPage == state.pageCount - 1 && available.x < 0)
                if (source == NestedScrollSource.UserInput && boundary) {
                    if (motion) edge = (edge + available.x * .24f).coerceIn(-maxEdge, maxEdge)
                    return Offset(available.x, 0f)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (edge != 0f) {
                    release.snapTo(edge)
                    edge = 0f
                    release.animateTo(0f, tween(if (motion) 220 else 0))
                }
                return Velocity.Zero
            }
        }
    }
    Box(Modifier.fillMaxSize().clipToBounds().nestedScroll(connection).testTag("mainPagePager")) {
        CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
            HorizontalPager(state = state, reverseLayout = false, beyondViewportPageCount = 0,
                modifier = Modifier.fillMaxSize().graphicsLayer { translationX = edge + release.value },
                key = { it }) { page ->
                // The home character can draw beyond its canvas while a neighboring page is visible.
                Box(Modifier.fillMaxSize().clipToBounds()) {
                    content(page)
                }
            }
        }
    }
}
