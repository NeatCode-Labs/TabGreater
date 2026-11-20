package com.neatcode.tabgreater.ui.watchlist

import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatchlistSwipeTest {

    private val watchlists = listOf(10L, 20L, 30L)

    @Test
    fun `swiping left selects the next watchlist`() {
        assertEquals(20L, adjacentWatchlistId(watchlists, 10L, horizontalDistance = -40f, threshold = 32f))
    }

    @Test
    fun `swiping right selects the previous watchlist`() {
        assertEquals(20L, adjacentWatchlistId(watchlists, 30L, horizontalDistance = 40f, threshold = 32f))
    }

    @Test
    fun `a swipe shorter than the threshold does not change selection`() {
        assertNull(adjacentWatchlistId(watchlists, 10L, horizontalDistance = -31f, threshold = 32f))
    }

    @Test
    fun `empty and single watchlists cannot be swiped`() {
        assertNull(adjacentWatchlistId(emptyList(), 10L, horizontalDistance = -100f, threshold = 32f))
        assertNull(adjacentWatchlistId(listOf(10L), 10L, horizontalDistance = -100f, threshold = 32f))
    }

    @Test
    fun `swiping past either end does not wrap`() {
        assertNull(adjacentWatchlistId(watchlists, 10L, horizontalDistance = 40f, threshold = 32f))
        assertNull(adjacentWatchlistId(watchlists, 30L, horizontalDistance = -40f, threshold = 32f))
    }

    @Test
    fun `an unknown selection has no adjacent watchlist`() {
        assertNull(adjacentWatchlistId(watchlists, 99L, horizontalDistance = -40f, threshold = 32f))
    }

    @Test
    fun `swipes follow the mirrored tab order in right to left layouts`() {
        assertEquals(30L, adjacentWatchlistId(watchlists, 20L, 40f, 32f, LayoutDirection.Rtl))
        assertEquals(10L, adjacentWatchlistId(watchlists, 20L, -40f, 32f, LayoutDirection.Rtl))
    }
}
