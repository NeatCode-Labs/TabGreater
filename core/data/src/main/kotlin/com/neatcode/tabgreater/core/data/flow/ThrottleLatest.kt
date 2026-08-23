package com.neatcode.tabgreater.core.data.flow

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select

/**
 * Samples this flow: the first value goes through immediately, after that at most one value — the
 * newest one — per [periodMs] window. Values that arrive inside a window are not queued, they
 * replace each other, so the collector never falls behind the producer.
 *
 * This is what separates *how fast the data arrives* from *how fast the screen redraws*. The
 * exchange WebSockets stay connected and keep pushing at full speed; only the UI's frame rate is
 * capped, which is the whole point of the "Watchlist refresh rate" setting.
 *
 * The period is read as a lambda, once per emission, so changing the setting takes effect on the
 * next value instead of restarting the upstream flow (and with it every socket subscription).
 * A period of `0` or less disables the throttle.
 *
 * Timing uses [delay] only — no clock reads — so `runTest`'s virtual time drives it exactly.
 *
 * @param passThrough decides, per value, whether holding it back would be wrong: it ends the
 *   running window at once and emits. It exists for values that are not another sample of the same
 *   thing but a *different* thing — a map whose key set has changed, say, where waiting out the
 *   window would paint rows that no longer exist next to nothing at all. The previously emitted
 *   value is passed alongside (`null` before the first emission). It comes first, before the
 *   parameter every caller passes, so that [periodMs] stays the trailing lambda.
 */
fun <T> Flow<T>.throttleLatest(
    passThrough: (previous: T?, next: T) -> Boolean = { _, _ -> false },
    periodMs: () -> Long,
): Flow<T> = channelFlow {
    // CONFLATED: `send` never suspends and only the newest pending value survives a window.
    val latest = Channel<T>(Channel.CONFLATED)
    launch {
        try {
            collect { latest.send(it) }
            latest.close()
        } catch (e: CancellationException) {
            latest.close()
            throw e
        } catch (e: Throwable) {
            // Fail the downstream with the upstream's exception rather than completing quietly.
            latest.close(e)
        }
    }

    var previous: T? = null
    /** Taken out of the channel while the previous window was running; emitted next. */
    var carried: ChannelResult<T>? = null
    while (true) {
        val result = carried ?: latest.receiveCatching()
        carried = null
        if (result.isClosed) {
            result.exceptionOrNull()?.let { throw it }
            break
        }
        val value = result.getOrThrow()
        send(value)
        previous = value
        val period = periodMs()
        if (period <= 0L) continue
        carried = waitOutWindow(latest, period, previous, passThrough)
    }
}

/**
 * Waits out one window of [period] millis, watching what arrives inside it.
 *
 * @return the value to emit next — the newest one seen inside the window, or the channel's closed
 *   result — and `null` when the window ran out with nothing new to show for it.
 */
private suspend fun <T> CoroutineScope.waitOutWindow(
    latest: ReceiveChannel<T>,
    period: Long,
    previous: T?,
    passThrough: (previous: T?, next: T) -> Boolean,
): ChannelResult<T>? {
    // A job rather than `delay(period)` so the wait can be cut short without losing track of it.
    val timer = launch { delay(period) }
    var pending: ChannelResult<T>? = null
    while (timer.isActive) {
        val received = select<ChannelResult<T>?> {
            timer.onJoin { null }
            latest.onReceiveCatching { it }
        } ?: break
        // Conflation, done by hand: only the newest value survives the window.
        pending = received
        if (received.isClosed) {
            timer.cancel()
            break
        }
        if (passThrough(previous, received.getOrThrow())) {
            timer.cancel()
            break
        }
    }
    return pending
}
