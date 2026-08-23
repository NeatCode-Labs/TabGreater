package com.neatcode.tabgreater.core.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

/**
 * The error-visibility rules of the Settings "Status" line.
 *
 * `LiveDiagnostics` itself needs a `Context` (battery allowlist), so the state transitions it
 * delegates to are tested here directly — they are where the whole rule lives.
 */
class LiveDiagnosticsStateTest {

    private val boom = IOException("timeout")

    @Test
    fun `an error names the operation that produced it`() {
        val state = LiveDiagnosticsState().withError(WHAT_REST_ROUND, boom)
        assertEquals("REST round: IOException timeout", state.lastError)
        assertEquals(WHAT_REST_ROUND, state.lastErrorWhat)
    }

    @Test
    fun `a widget render never clears a REST failure`() {
        val failed = LiveDiagnosticsState().withError(WHAT_REST_ROUND, boom)
        // LIVE mode renders every 2 s; before the fix the first of those wiped the message and the
        // stalled REST round had no visible symptom at all.
        val after = failed.withWidgetRefresh(atEpochMs = 1_000L, painted = 3)
        assertEquals("REST round: IOException timeout", after.lastError)
        assertEquals(1_000L, after.lastWidgetRefreshAt)
    }

    @Test
    fun `a REST round clears its own failure`() {
        val recovered = LiveDiagnosticsState()
            .withError(WHAT_REST_ROUND, boom)
            .withRestRound(atEpochMs = 2_000L)
        assertNull(recovered.lastError)
        assertNull(recovered.lastErrorWhat)
        assertEquals(2_000L, recovered.lastRestRoundAt)
    }

    @Test
    fun `a widget failure is only cleared by a render that painted something`() {
        val failed = LiveDiagnosticsState().withError(WHAT_WIDGET_REFRESH, boom)

        val unchangedMarket = failed.withWidgetRefresh(atEpochMs = 1_000L, painted = 0)
        assertEquals("widget refresh: IOException timeout", unchangedMarket.lastError)

        val repainted = unchangedMarket.withWidgetRefresh(atEpochMs = 2_000L, painted = 1)
        assertNull(repainted.lastError)
        assertNull(repainted.lastErrorWhat)
    }

    @Test
    fun `the timestamp advances even when nothing was repainted`() {
        // A flat market repaints nothing; "Last update" must still move, or the Settings screen
        // would report a stalled service that is in fact perfectly healthy.
        val state = LiveDiagnosticsState().withWidgetRefresh(atEpochMs = 5_000L, painted = 0)
        assertEquals(5_000L, state.lastWidgetRefreshAt)
        assertNull(state.lastError)
    }
}
