package com.neatcode.tabgreater.core.live

import com.neatcode.tabgreater.core.model.ExchangeId

/** State of one exchange's ticker stream. */
internal enum class StreamState { CONNECTING, ACTIVE, FAILED }

/** A stream that has not delivered anything for this long is no longer reported as [LiveStatus.LIVE]. */
internal const val LIVE_FRESHNESS_MS: Long = 60_000L

/**
 * Aggregates the per-exchange stream states into the single [LiveStatus] the UI shows.
 *
 * * [LiveStatus.LIVE] — at least one stream is connected and delivered a message within
 *   [freshnessMs].
 * * [LiveStatus.CONNECTING] — a stream is connecting, or is connected but has gone quiet.
 * * [LiveStatus.OFFLINE] — nothing is subscribed, or every stream failed.
 *
 * Pure function so it can be unit tested without a dispatcher or Android APIs.
 */
internal fun computeLiveStatus(
    states: Map<ExchangeId, StreamState>,
    lastMessageAt: Map<ExchangeId, Long>,
    now: Long,
    freshnessMs: Long = LIVE_FRESHNESS_MS,
): LiveStatus {
    if (states.isEmpty()) return LiveStatus.OFFLINE
    var connecting = false
    for ((exchange, state) in states) {
        when (state) {
            StreamState.ACTIVE -> {
                val last = lastMessageAt[exchange] ?: 0L
                if (last > 0L && now - last <= freshnessMs) return LiveStatus.LIVE
                connecting = true
            }
            StreamState.CONNECTING -> connecting = true
            StreamState.FAILED -> Unit
        }
    }
    return if (connecting) LiveStatus.CONNECTING else LiveStatus.OFFLINE
}
