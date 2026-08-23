package com.neatcode.tabgreater.core.live

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [needsScreenOnTick] — the rule that decides whether unlocking the phone in [TickerMode.TICK] is
 * worth one REST round.
 *
 * The interesting case is a burst of sessions: `USER_PRESENT`, the connectivity callback as the
 * radio comes back and `POWER_CONNECTED` all land within a few hundred milliseconds of an unlock,
 * and `collectLatest` cancels the previous session — including the round it started — on each of
 * them. The round only counts once it has finished, so a cancelled one leaves the next session with
 * the same work to do instead of an already-spent 60 s suppression window.
 */
class ScreenOnTickTest {

    private val minute = SCREEN_ON_TICK_MAX_AGE_MS

    private fun needs(
        mode: TickerMode = TickerMode.TICK,
        screenInteractive: Boolean = true,
        hasKeys: Boolean = true,
        tickInFlight: Boolean = false,
        lastTickAtElapsed: Long = 0L,
        nowElapsed: Long = 0L,
    ) = needsScreenOnTick(mode, screenInteractive, hasKeys, tickInFlight, lastTickAtElapsed, nowElapsed)

    @Test
    fun `the first unlock of a timed session ticks`() {
        assertTrue(needs(nowElapsed = 3_600_000L))
    }

    @Test
    fun `a round that just finished suppresses the next sessions of the burst`() {
        assertFalse(needs(lastTickAtElapsed = 3_600_000L, nowElapsed = 3_600_200L))
        assertFalse(needs(lastTickAtElapsed = 3_600_000L, nowElapsed = 3_600_000L + minute - 1))
        assertTrue(needs(lastTickAtElapsed = 3_600_000L, nowElapsed = 3_600_000L + minute))
    }

    @Test
    fun `a cancelled round leaves the work for the next session`() {
        // Session A stamped nothing, because it never got past its REST call; session B, 200 ms
        // later, has to run the round the user is actually waiting for.
        assertTrue(needs(tickInFlight = false, lastTickAtElapsed = 0L, nowElapsed = 3_600_200L))
    }

    @Test
    fun `a round still running is not started a second time`() {
        assertFalse(needs(tickInFlight = true, lastTickAtElapsed = 0L, nowElapsed = 3_600_200L))
    }

    @Test
    fun `only a timed mode with a visible screen and a widget qualifies`() {
        assertFalse("sockets already deliver a fresh price", needs(mode = TickerMode.LIVE))
        assertFalse(needs(mode = TickerMode.NEAR))
        assertFalse(needs(mode = TickerMode.SLEEP))
        assertFalse("nobody is looking", needs(screenInteractive = false))
        assertFalse("nothing to paint", needs(hasKeys = false))
    }
}
