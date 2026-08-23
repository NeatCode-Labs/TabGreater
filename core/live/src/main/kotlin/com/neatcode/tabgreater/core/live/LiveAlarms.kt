package com.neatcode.tabgreater.core.live

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log

/** Watchdog spacing: if the service dies, this alarm restarts it within five minutes. */
const val HEARTBEAT_INTERVAL_MS: Long = 5 * 60_000L

/**
 * A while-idle alarm requested more often than this is wasted: Doze caps them at 7 per hour
 * (~8.5 min) unless the app is on the battery allowlist.
 *
 * The budget is **per app**, not per alarm, so the heartbeat and the sleep tick draw on the same
 * quota. That is why [LiveTickerService] cancels the heartbeat while it is in [TickerMode.SLEEP]:
 * a heartbeat pending in front of the tick would spend the granted dispatch on an environment
 * read, and the tick — which proves the service is alive just as well — would slip a full cycle.
 */
const val MIN_SLEEP_TICK_MS: Long = 60_000L

/** One scheduled alarm: when it should fire (elapsed-realtime millis) and whether it may be exact. */
data class AlarmPlan(val triggerAtElapsed: Long, val exact: Boolean)

/**
 * Pure alarm arithmetic, so the watchdog rules can be unit tested without an `AlarmManager`.
 *
 * Both alarms use `ELAPSED_REALTIME_WAKEUP` (immune to clock changes) and the
 * `…AndAllowWhileIdle` variants — plain exact alarms simply do not fire in Doze, allowlist or not.
 */
object AlarmPlanner {

    /** The watchdog heartbeat, always [HEARTBEAT_INTERVAL_MS] out from now. */
    fun heartbeat(
        nowElapsed: Long,
        canScheduleExact: Boolean,
        intervalMs: Long = HEARTBEAT_INTERVAL_MS,
    ): AlarmPlan = AlarmPlan(nowElapsed + intervalMs.coerceAtLeast(MIN_SLEEP_TICK_MS), canScheduleExact)

    /** The [TickerMode.SLEEP] REST tick; the requested interval is floored at [MIN_SLEEP_TICK_MS]. */
    fun sleepTick(
        nowElapsed: Long,
        canScheduleExact: Boolean,
        intervalMs: Long,
    ): AlarmPlan = AlarmPlan(nowElapsed + intervalMs.coerceAtLeast(MIN_SLEEP_TICK_MS), canScheduleExact)

    /**
     * Whether an already scheduled alarm has to be replaced.
     *
     * Re-arming on every tick would be pointless churn, so an alarm is left alone while it is
     * still in the future and no further out than the interval it was requested for. That covers
     * both "the service was restarted and does not know what is pending" ([scheduledAtElapsed]
     * `null`) and "the user shortened the interval" (scheduled too far out).
     */
    fun needsRearm(scheduledAtElapsed: Long?, nowElapsed: Long, intervalMs: Long): Boolean {
        if (scheduledAtElapsed == null) return true
        if (scheduledAtElapsed <= nowElapsed) return true
        return scheduledAtElapsed - nowElapsed > intervalMs.coerceAtLeast(MIN_SLEEP_TICK_MS)
    }
}

/**
 * Applies [AlarmPlanner]'s decisions to the real `AlarmManager`.
 *
 * `USE_EXACT_ALARM` is declared so `canScheduleExactAlarms()` is
 * true in practice; the inexact `setAndAllowWhileIdle` path is kept for the case where a future
 * Android revokes it.
 */
internal class LiveAlarmScheduler(private val context: Context) {

    private val alarmManager: AlarmManager? = context.getSystemService(AlarmManager::class.java)

    private var heartbeatAt: Long? = null
    private var sleepTickAt: Long? = null

    /** True when exact alarms are available; surfaced in [LiveDiagnosticsState]. */
    val canScheduleExact: Boolean
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager?.canScheduleExactAlarms() == true
        } else {
            true
        }

    /** Next heartbeat as elapsed-realtime millis, or `null` when none is armed. */
    val nextHeartbeatAtElapsed: Long? get() = heartbeatAt

    /** Arms the watchdog heartbeat unless a suitable one is already pending. */
    fun armHeartbeat(force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && !AlarmPlanner.needsRearm(heartbeatAt, now, HEARTBEAT_INTERVAL_MS)) return
        val plan = AlarmPlanner.heartbeat(now, canScheduleExact)
        if (schedule(ACTION_HEARTBEAT, REQUEST_HEARTBEAT, plan)) heartbeatAt = plan.triggerAtElapsed
    }

    /** Arms the SLEEP REST tick, replacing a pending one whose spacing no longer matches. */
    fun armSleepTick(intervalMs: Long, force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && !AlarmPlanner.needsRearm(sleepTickAt, now, intervalMs)) return
        val plan = AlarmPlanner.sleepTick(now, canScheduleExact, intervalMs)
        if (schedule(ACTION_SLEEP_TICK, REQUEST_SLEEP_TICK, plan)) sleepTickAt = plan.triggerAtElapsed
    }

    fun cancelSleepTick() {
        cancel(ACTION_SLEEP_TICK, REQUEST_SLEEP_TICK)
        sleepTickAt = null
    }

    /** Drops the watchdog; used in [TickerMode.SLEEP], where the tick is the watchdog. */
    fun cancelHeartbeat() {
        cancel(ACTION_HEARTBEAT, REQUEST_HEARTBEAT)
        heartbeatAt = null
    }

    fun cancelAll() {
        cancelHeartbeat()
        cancelSleepTick()
    }

    private fun schedule(action: String, requestCode: Int, plan: AlarmPlan): Boolean {
        val manager = alarmManager ?: return false
        val pending = pendingIntent(action, requestCode, PendingIntent.FLAG_UPDATE_CURRENT) ?: return false
        return try {
            if (plan.exact) {
                manager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    plan.triggerAtElapsed,
                    pending,
                )
            } else {
                manager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    plan.triggerAtElapsed,
                    pending,
                )
            }
            true
        } catch (e: SecurityException) {
            // Exact-alarm access revoked between the check and the call.
            Log.w(TAG, "alarm $action refused", e)
            false
        }
    }

    private fun cancel(action: String, requestCode: Int) {
        val existing = pendingIntent(action, requestCode, PendingIntent.FLAG_NO_CREATE) ?: return
        alarmManager?.cancel(existing)
        existing.cancel()
    }

    private fun pendingIntent(action: String, requestCode: Int, extraFlags: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, TickAlarmReceiver::class.java).setAction(action),
            extraFlags or PendingIntent.FLAG_IMMUTABLE,
        )

    private companion object {
        const val TAG = "LiveAlarms"
        const val REQUEST_HEARTBEAT = 1
        const val REQUEST_SLEEP_TICK = 2
    }
}
