package com.neatcode.tabgreater.ui.watchlist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WatchlistPagerFlingBehaviorTest {
    @get:Rule val compose = createComposeRule()

    @Test fun slowGesturesTrackPagerOffsetAndCrossLegacyThresholdAtZeroMotionScale() {
        val pagerState = mutableStateOf<PagerState?>(null)

        compose.setContent {
            val state = rememberPagerState(pageCount = { 2 })
            SideEffect { pagerState.value = state }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = state,
                    modifier = Modifier.fillMaxSize().testTag("watchlistPager"),
                    flingBehavior = rememberWatchlistPagerFlingBehavior(
                        state = state,
                        pageWidth = maxWidth,
                        motionScaleFactor = 0f,
                    ),
                ) { page ->
                    Box(Modifier.fillMaxSize().testTag("watchlistPage$page"))
                }
            }
        }

        compose.waitForIdle()
        dragSlowlyByFraction(-0.3f, pagerState, initialPage = 0, targetPage = 1)
        dragSlowlyByFraction(0.3f, pagerState, initialPage = 1, targetPage = 0)
    }

    private fun dragSlowlyByFraction(
        fraction: Float,
        pagerState: State<PagerState?>,
        initialPage: Int,
        targetPage: Int,
    ) {
        val pager = compose.onNodeWithTag("watchlistPager")
        pager.performTouchInput {
            val start = center
            down(start)
            for (step in 1..10) {
                val point = Offset(
                    x = start.x + width.toFloat() * fraction * step / 10f,
                    y = start.y,
                )
                moveTo(point, delayMillis = 100)
            }
        }
        compose.waitForIdle()

        compose.runOnIdle {
            val state = requireNotNull(pagerState.value)
            assertTrue(
                "Pager must follow the held finger, offset=${state.currentPageOffsetFraction}",
                kotlin.math.abs(state.currentPageOffsetFraction) > 0.2f,
            )
            assertEquals(initialPage, state.settledPage)
        }

        pager.performTouchInput { up() }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(targetPage, pagerState.value?.settledPage) }
    }
}
