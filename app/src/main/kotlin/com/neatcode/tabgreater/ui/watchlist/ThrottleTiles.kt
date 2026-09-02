package com.neatcode.tabgreater.ui.watchlist

import com.neatcode.tabgreater.core.data.flow.throttleLatest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * [throttleLatest] for the per-market maps behind the grid, with the rule the tiles need: a window
 * may hold back another *sample* of a tile, never the value that first redraws it.
 *
 * Without it the first fresh price after every unlock costs a whole window. The screen comes back,
 * `WhileSubscribed` rebuilds the pipeline, `observeTickers` emits the persisted snapshot — the
 * prices the grid is already showing — the throttle spends its free first emission on those, and
 * the answer from the network lands inside the window that stale value opened (5 s by default,
 * 10 s at the slowest setting). Mini-charts pay it twice: once for the cached candle window, once
 * for the first live candle.
 *
 * Two things open the gate:
 * - the key set changed — a map for *different* markets is not another sample of the same tiles
 *   (a tab switch, an added or removed ticker). This is the rule the sparkline flow had before.
 * - a market whose value would redraw its tile, **once** per market. A key that has only just
 *   appeared does not count: while `observeEach` fills the map key by key, counting it would spend
 *   that market's one free frame on its cached mini-chart and the first live candle would find the
 *   gate already shut. A market that leaves the map is armed again when it comes back.
 *
 * The gate is decided on every upstream value rather than inside `passThrough`, which
 * [throttleLatest] consults only while a window is running: a market whose free frame is spent
 * with no window running must still be marked, and a market that disappears during such a quiet
 * stretch must still be re-armed. Each value is therefore compared with the one before it rather
 * than with the one the tile is showing — the same thing for as long as it matters, because a
 * value that redraws a tile ends the window and is emitted at once, so the two can only drift
 * apart after that market's frame has been spent.
 *
 * The cost is one extra frame per market per collection; everything after that is sampled at the
 * "Watchlist refresh rate" exactly as before. The state is per **collection** — `uiState` is
 * `WhileSubscribed`, so the grid resubscribes on every unlock and that is precisely when the free
 * frames have to be armed again — and it is touched from one coroutine only, so it needs no
 * synchronisation.
 *
 * @param changed whether two values of the same market would draw a different tile. Value equality
 *   cannot stand in for it: [com.neatcode.tabgreater.core.model.Ticker] equality counts the
 *   exchange timestamp and a ticker stream pushes an update every second whether or not a trade
 *   happened, so a tick that moves nothing on the screen would spend the market's free frame —
 *   and buy an emission the grid never even sees, because the tiles built from it compare equal
 *   to the ones already there.
 */
internal fun <K, V> Flow<Map<K, V>>.throttleTiles(
    changed: (shown: V, next: V) -> Boolean,
    periodMs: () -> Long,
): Flow<Map<K, V>> {
    val upstream = this
    return flow {
        val served = HashSet<K>()
        var previous: Map<K, V>? = null
        emitAll(
            upstream
                .map { next ->
                    var free = previous?.keys != next.keys
                    served.retainAll(next.keys)
                    for ((key, value) in next) {
                        val shown = previous?.get(key) ?: continue
                        if (!changed(shown, value) || key in served) continue
                        served += key
                        free = true
                    }
                    previous = next
                    Sample(next, free)
                }
                .throttleLatest(passThrough = { _, next -> next.free }, periodMs = periodMs)
                .map { it.markets }
        )
    }
}

/** One upstream map carrying the gate decision [throttleTiles] already took for it. */
private class Sample<K, V>(val markets: Map<K, V>, val free: Boolean)
