package com.neatcode.tabgreater.core.live

import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.Ticker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Connection state of the live layer, for a subtle "stale" indication in the UI. */
enum class LiveStatus { CONNECTING, LIVE, OFFLINE }

/**
 * Single source of truth for current prices. Merges the persisted snapshot (Room), a REST
 * snapshot on subscribe, and the exchange WebSocket streams while collected.
 *
 * Subscriptions are reference-counted per market across all collectors (watchlist screen,
 * chart header, widgets): one socket per exchange, closed when nothing is subscribed.
 */
interface MarketDataRepository {
    /**
     * Emits the latest known ticker for each requested key as a map (keys missing until first data).
     * Emits immediately with cached snapshots, then on every live update (conflated).
     */
    fun observeTickers(keys: Set<MarketKey>): Flow<Map<MarketKey, Ticker>>

    /** Aggregate status across the exchanges currently subscribed. */
    val status: Flow<LiveStatus>

    /**
     * The newest live ticker per market received in this process (no Room round trip), for
     * callers that render on a timer rather than per update — the home-screen widgets.
     */
    val latest: StateFlow<Map<MarketKey, Ticker>>

    /** One REST round for the given keys (used when sockets are down or on foreground). */
    suspend fun refresh(keys: Collection<MarketKey>)
}
