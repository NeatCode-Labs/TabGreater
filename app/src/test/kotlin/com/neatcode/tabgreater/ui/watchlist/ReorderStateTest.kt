package com.neatcode.tabgreater.ui.watchlist

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule that decides what a finished grid gesture was. Getting it wrong either scrambles the
 * order on a plain long press or throws a real drag away, so it is worth pinning down on its own.
 */
class ReorderStateTest {

    private val slop = 8f

    @Test
    fun `a press that never moved is a long press`() {
        assertFalse(
            endedAsDrag(reportedMove = false, exceededSlop = false, netDistance = 0f, touchSlop = slop),
        )
    }

    @Test
    fun `a tremor inside the slop circle is still a long press`() {
        assertFalse(
            endedAsDrag(reportedMove = false, exceededSlop = false, netDistance = 3f, touchSlop = slop),
        )
    }

    @Test
    fun `a drag that ends back at its origin is still a drag`() {
        // Scenario 4: dragged to the bottom edge, auto-scrolled, then walked back to the start.
        assertTrue(
            endedAsDrag(reportedMove = true, exceededSlop = true, netDistance = 0.5f, touchSlop = slop),
        )
    }

    @Test
    fun `leaving the slop circle makes it a drag even when nothing was reordered`() {
        // A one-item watchlist, or a drag that stayed inside its own cell: no onMove ever fires,
        // but the finger clearly moved, so the gesture must not tick the tile.
        assertTrue(
            endedAsDrag(reportedMove = false, exceededSlop = true, netDistance = 1f, touchSlop = slop),
        )
    }

    @Test
    fun `a reported move alone is enough`() {
        assertTrue(
            endedAsDrag(reportedMove = true, exceededSlop = false, netDistance = 0f, touchSlop = slop),
        )
    }

    @Test
    fun `a net offset past the slop is a drag`() {
        assertTrue(
            endedAsDrag(reportedMove = false, exceededSlop = false, netDistance = 8f, touchSlop = slop),
        )
    }
}
