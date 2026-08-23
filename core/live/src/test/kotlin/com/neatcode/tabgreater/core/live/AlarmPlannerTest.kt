package com.neatcode.tabgreater.core.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The watchdog / sleep-tick arithmetic, extracted from `AlarmManager` so it can be tested. */
class AlarmPlannerTest {

    private val now = 1_000_000L

    @Test
    fun `heartbeat is five minutes out`() {
        val plan = AlarmPlanner.heartbeat(now, canScheduleExact = true)
        assertEquals(now + HEARTBEAT_INTERVAL_MS, plan.triggerAtElapsed)
        assertTrue(plan.exact)
    }

    @Test
    fun `heartbeat falls back to an inexact alarm when exact access is gone`() {
        assertFalse(AlarmPlanner.heartbeat(now, canScheduleExact = false).exact)
    }

    @Test
    fun `sleep tick honours the configured interval`() {
        val plan = AlarmPlanner.sleepTick(now, canScheduleExact = true, intervalMs = 300_000L)
        assertEquals(now + 300_000L, plan.triggerAtElapsed)
    }

    @Test
    fun `sleep tick is floored at the doze-quota minimum`() {
        val plan = AlarmPlanner.sleepTick(now, canScheduleExact = true, intervalMs = 5_000L)
        assertEquals(now + MIN_SLEEP_TICK_MS, plan.triggerAtElapsed)
    }

    @Test
    fun `nothing scheduled always needs arming`() {
        assertTrue(AlarmPlanner.needsRearm(null, now, 120_000L))
    }

    @Test
    fun `an alarm in the past needs re-arming`() {
        assertTrue(AlarmPlanner.needsRearm(now - 1, now, 120_000L))
        assertTrue(AlarmPlanner.needsRearm(now, now, 120_000L))
    }

    @Test
    fun `a pending alarm inside the interval is left alone`() {
        assertFalse(AlarmPlanner.needsRearm(now + 60_000L, now, 120_000L))
        assertFalse(AlarmPlanner.needsRearm(now + 120_000L, now, 120_000L))
    }

    @Test
    fun `an alarm further out than the interval is replaced`() {
        // The user shortened the sleep interval from 5 minutes to 2.
        assertTrue(AlarmPlanner.needsRearm(now + 300_000L, now, 120_000L))
    }

    @Test
    fun `the interval floor also governs re-arming`() {
        // 5 s was clamped to 60 s when armed, so a 60 s-out alarm must not be considered stale.
        assertFalse(AlarmPlanner.needsRearm(now + MIN_SLEEP_TICK_MS, now, 5_000L))
    }
}
