package com.neatcode.tabgreater.core.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The pair of while-idle alarms `LiveAlarmScheduler` keeps, with its arithmetic and none of its
 * `AlarmManager`: `null` means nothing is pending, a number is the elapsed-realtime trigger.
 */
private class Alarms {
    var heartbeat: Long? = null
    var sleepTick: Long? = null

    fun armHeartbeat(now: Long, force: Boolean) {
        if (!force && !AlarmPlanner.needsRearm(heartbeat, now, HEARTBEAT_INTERVAL_MS)) return
        heartbeat = AlarmPlanner.heartbeat(now, canScheduleExact = true).triggerAtElapsed
    }

    fun armSleepTick(now: Long, intervalMs: Long, force: Boolean) {
        if (!force && !AlarmPlanner.needsRearm(sleepTick, now, intervalMs)) return
        sleepTick = AlarmPlanner.sleepTick(now, canScheduleExact = true, intervalMs = intervalMs)
            .triggerAtElapsed
    }

    fun cancelHeartbeat() {
        heartbeat = null
    }
}

/**
 * Regression for a bug found on the emulator: while the service is in a tick mode
 * ([TickerMode.SLEEP] or [TickerMode.TICK]), every environment change (screen on/off, network
 * flap) re-runs the session. Arming the tick unconditionally each time pushed it a full interval
 * into the future, so it never fired.
 *
 * This walks a sequence of session re-runs through [AlarmPlanner.needsRearm] the way
 * `LiveAlarmScheduler.armSleepTick` does and asserts the trigger time stays put.
 */
class SleepTickRearmTest {

    /** Mirrors `LiveAlarmScheduler.armSleepTick(intervalMs, force = false)`. */
    private fun arm(pending: Long?, now: Long, intervalMs: Long): Long =
        if (AlarmPlanner.needsRearm(pending, now, intervalMs)) {
            AlarmPlanner.sleepTick(now, canScheduleExact = true, intervalMs = intervalMs).triggerAtElapsed
        } else {
            checkNotNull(pending)
        }

    @Test
    fun `repeated session re-runs do not push the tick out`() {
        val interval = LIVE_SCREEN_OFF_TICK_MS
        var now = 0L
        var pending = arm(null, now, interval)
        assertEquals(300_000L, pending)

        // Screen on, screen off, network flap — one re-run each, 20 s apart.
        repeat(5) {
            now += 20_000L
            pending = arm(pending, now, interval)
        }
        assertEquals("the tick must still fire at its original time", 300_000L, pending)
    }

    @Test
    fun `a fired tick is scheduled again from now`() {
        val interval = LIVE_SCREEN_OFF_TICK_MS
        val pending = arm(null, 0L, interval)
        // The alarm fired at 300 s; the next session re-run is at 301 s.
        assertEquals(601_000L, arm(pending, 301_000L, interval))
    }

    @Test
    fun `shortening the widget cadence brings the tick forward`() {
        val pending = arm(null, 0L, WidgetRefresh.MIN_15.intervalMs)
        assertEquals(900_000L, pending)
        // The user picked "Every minute" while the 15-minute alarm was pending.
        assertEquals(10_000L + 60_000L, arm(pending, 10_000L, WidgetRefresh.MIN_1.intervalMs))
    }

    /**
     * The heartbeat must not survive a tick dispatch. Both alarms are `…AndAllowWhileIdle` and
     * share one per-app Doze budget, so a heartbeat pending in front of the tick spends the granted
     * dispatch on a no-op and pushes the cadence the user picked out by a full cycle — and, because
     * a heartbeat dispatch arms the next heartbeat, it would keep doing so forever.
     *
     * Mirrors what one dispatch does in `LiveTickerService`: `onStartCommand` (which consults
     * [rearmsHeartbeat]) followed by `sleepTick`.
     */
    private fun Alarms.dispatch(action: String, now: Long, intervalMs: Long, mode: TickerMode?) {
        if (rearmsHeartbeat(action, mode)) armHeartbeat(now, force = true)
        if (action == ACTION_SLEEP_TICK) {
            armSleepTick(now, intervalMs, force = true)
            cancelHeartbeat()
        }
    }

    @Test
    fun `a tick dispatch leaves no heartbeat behind`() {
        val interval = WidgetRefresh.MIN_15.intervalMs
        val alarms = Alarms()

        // Cold start: the mode is not known yet, so the watchdog is armed; the first session then
        // enters TICK, arms the tick and drops it again.
        alarms.dispatch(ACTION_START, now = 0L, intervalMs = interval, mode = null)
        assertEquals(HEARTBEAT_INTERVAL_MS, alarms.heartbeat)
        alarms.armSleepTick(0L, interval, force = false)
        alarms.cancelHeartbeat()

        // Screen off, hours of Doze: three tick dispatches, each 15 minutes apart.
        var now = 0L
        repeat(3) {
            now += interval
            alarms.dispatch(ACTION_SLEEP_TICK, now, interval, TickerMode.TICK)
            assertNull("a tick dispatch must not arm the watchdog", alarms.heartbeat)
            assertEquals(now + interval, alarms.sleepTick)
        }
    }

    @Test
    fun `a widget start in a tick mode does not add a second alarm`() {
        val interval = WidgetRefresh.MIN_5.intervalMs
        val alarms = Alarms()
        alarms.armSleepTick(0L, interval, force = true)

        // Placing or refreshing a widget sends ACTION_START to an already running service, long
        // after the session that would have cancelled a heartbeat; nothing else would take it down.
        for (mode in listOf(TickerMode.TICK, TickerMode.SLEEP)) {
            alarms.dispatch(ACTION_START, now = 30_000L, intervalMs = interval, mode = mode)
            assertNull(mode.name, alarms.heartbeat)
        }
        assertEquals(interval, alarms.sleepTick)
    }

    @Test
    fun `a heartbeat dispatch keeps the watchdog chain going in a socket mode`() {
        val alarms = Alarms()
        // In LIVE and NEAR nothing else re-arms it, so this dispatch has to.
        alarms.dispatch(ACTION_HEARTBEAT, now = 300_000L, intervalMs = 0L, mode = TickerMode.LIVE)
        assertEquals(300_000L + HEARTBEAT_INTERVAL_MS, alarms.heartbeat)
        assertNull(alarms.sleepTick)
    }

    @Test
    fun `a stray heartbeat dispatch in a tick mode ends the chain`() {
        val interval = WidgetRefresh.MIN_5.intervalMs
        val alarms = Alarms()
        alarms.armSleepTick(0L, interval, force = true)
        // The heartbeat this answers was armed before the mode was known and fires alongside the
        // tick; re-arming here would keep both alarms alive against each other for ever.
        alarms.dispatch(ACTION_HEARTBEAT, now = interval, intervalMs = interval, mode = TickerMode.TICK)
        assertNull(alarms.heartbeat)
    }

    @Test
    fun `every offered cadence clears the doze quota floor`() {
        for (refresh in WidgetRefresh.entries - WidgetRefresh.LIVE) {
            assertEquals(
                refresh.name,
                refresh.intervalMs,
                AlarmPlanner.sleepTick(0L, canScheduleExact = true, intervalMs = refresh.intervalMs)
                    .triggerAtElapsed,
            )
        }
    }
}
