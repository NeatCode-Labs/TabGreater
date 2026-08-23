package com.neatcode.tabgreater.feature.chart

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The part of [ChartWebViewCache] that needs no WebView: the visibility guard that keeps
 * `Application.onTrimMemory` from destroying the canvas the user is looking at (finding 23), and
 * the generation counter `ChartView` keys its subtree on (findings 1 / 11).
 */
class ChartWebViewCacheTest {

    @Test
    fun `trim is ignored while a chart screen is showing the WebView`() {
        ChartWebViewCache.setHostVisible(true)
        val before = ChartWebViewCache.generation

        ChartWebViewCache.trim()

        assertEquals("a visible chart must survive a foreground trim", before, ChartWebViewCache.generation)
    }

    @Test
    fun `trim drops the WebView and bumps the generation once the host is gone`() {
        ChartWebViewCache.setHostVisible(false)
        val before = ChartWebViewCache.generation

        ChartWebViewCache.trim()

        // The bump is what makes ChartView rebuild its subtree around a fresh WebView.
        assertEquals(before + 1, ChartWebViewCache.generation)
    }
}
