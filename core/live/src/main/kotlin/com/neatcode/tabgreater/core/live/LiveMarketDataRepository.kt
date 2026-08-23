package com.neatcode.tabgreater.core.live

import android.util.Log
import com.neatcode.tabgreater.core.data.db.TickerSnapshotDao
import com.neatcode.tabgreater.core.data.db.TickerSnapshotEntity
import com.neatcode.tabgreater.core.data.repo.MarketRepository
import com.neatcode.tabgreater.core.exchange.ExchangeAdapter
import com.neatcode.tabgreater.core.exchange.ExchangeRegistry
import com.neatcode.tabgreater.core.model.ExchangeId
import com.neatcode.tabgreater.core.model.Market
import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.Ticker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Merges three sources of prices into one map per collector:
 *
 * 1. the persisted snapshot in `ticker_snapshots` (so the grid renders instantly on cold start),
 * 2. a REST round fired on subscribe,
 * 3. the exchange WebSocket streams while the flow is collected.
 *
 * Streams are reference counted per market ([SubscriptionTracker]): one stream per exchange,
 * restarted 300 ms after the subscribed set changes, torn down when nothing is left. Live updates
 * are written back to Room at most once per 5 s per exchange, batched.
 */
class LiveMarketDataRepository(
    private val snapshotDao: TickerSnapshotDao,
    private val markets: MarketRepository,
    private val registry: ExchangeRegistry,
    private val scope: CoroutineScope,
) : MarketDataRepository {

    private val tracker = SubscriptionTracker()
    private val live = MutableStateFlow<Map<MarketKey, Ticker>>(emptyMap())

    override val latest: StateFlow<Map<MarketKey, Ticker>> get() = live

    private val jobsMutex = Mutex()
    private val streamJobs = mutableMapOf<ExchangeId, Job>()

    /** What each running stream is subscribed to, so an unchanged set is never re-handshaked. */
    private val streamKeys = mutableMapOf<ExchangeId, Set<MarketKey>>()
    private val restartJobs = ConcurrentHashMap<ExchangeId, Job>()

    private val streamStates = MutableStateFlow<Map<ExchangeId, StreamState>>(emptyMap())
    private val lastMessageAt = ConcurrentHashMap<ExchangeId, Long>()
    private val lastRestAt = ConcurrentHashMap<MarketKey, Long>()

    @OptIn(ExperimentalCoroutinesApi::class)
    override val status: StateFlow<LiveStatus> = streamStates
        .flatMapLatest { states ->
            if (states.isEmpty()) {
                flowOf(LiveStatus.OFFLINE)
            } else {
                // The "seen a message in the last 60 s" part of the rule is time based, so the
                // status has to be re-evaluated while nothing else changes.
                flow {
                    while (true) {
                        emit(computeLiveStatus(states, lastMessageAt, System.currentTimeMillis()))
                        delay(STATUS_TICK_MS)
                    }
                }
            }
        }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.WhileSubscribed(STATUS_STOP_TIMEOUT_MS), LiveStatus.OFFLINE)

    override fun observeTickers(keys: Set<MarketKey>): Flow<Map<MarketKey, Ticker>> = channelFlow {
        val wanted = keys.filterTo(LinkedHashSet()) { registry.getOrNull(it.exchange) != null }
        if (wanted.isEmpty()) {
            send(emptyMap())
            return@channelFlow
        }

        scheduleRestart(tracker.acquire(wanted))
        // Only markets without a recent REST answer are fetched: the watchlist re-subscribes on
        // every tab switch and on every added ticker, and a full round per event (KuCoin: one
        // request per market for small sets) is exactly the kind of burst that gets an IP
        // rate-limited.
        launch { refresh(wanted.filter { needsRestSnapshot(it) }) }

        try {
            combine(
                snapshotDao.observeByKeys(wanted.map { it.value }),
                live,
            ) { rows, liveTickers -> mergeTickers(rows, liveTickers, wanted) }
                .collect { send(it) }
        } finally {
            scheduleRestart(tracker.release(wanted))
        }
    }.conflate()

    // Exchanges refresh concurrently: Kraken paces itself at 1 request/s and KuCoin needs one
    // call per market for small sets, so a sequential round would hold back the others' snapshots.
    override suspend fun refresh(keys: Collection<MarketKey>) {
        val byExchange = keys.filter { registry.getOrNull(it.exchange) != null }.groupBy { it.exchange }
        if (byExchange.isEmpty()) return
        coroutineScope {
            for ((exchange, exchangeKeys) in byExchange) {
                val adapter = registry.getOrNull(exchange) ?: continue
                launch {
                    try {
                        val resolved = resolveMarkets(exchange, exchangeKeys.toSet())
                        if (resolved.isNotEmpty()) {
                            val fetched = adapter.fetchTickers(resolved)
                            val now = System.currentTimeMillis()
                            for (ticker in fetched) lastRestAt[ticker.key] = now
                            persist(fetched)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "REST refresh failed for ${exchange.id}", e)
                    }
                }
            }
        }
    }

    /**
     * A market whose REST snapshot (or live tick) is younger than [REST_COOLDOWN_MS] does not
     * need another request on re-subscribe — the socket is already keeping it current.
     */
    private fun needsRestSnapshot(key: MarketKey): Boolean {
        val now = System.currentTimeMillis()
        val rest = lastRestAt[key]
        if (rest != null && now - rest < REST_COOLDOWN_MS) return false
        val liveAt = live.value[key]?.timestamp
        return liveAt == null || now - liveAt >= REST_COOLDOWN_MS
    }

    /** Room snapshot first, live update wins when its timestamp is at least as new. */
    private fun mergeTickers(
        rows: List<TickerSnapshotEntity>,
        liveTickers: Map<MarketKey, Ticker>,
        wanted: Set<MarketKey>,
    ): Map<MarketKey, Ticker> {
        val merged = LinkedHashMap<MarketKey, Ticker>(wanted.size)
        for (row in rows) {
            val key = MarketKey.parseOrNull(row.marketKey) ?: continue
            if (key in wanted) merged[key] = row.toTicker(key)
        }
        for (key in wanted) {
            val update = liveTickers[key] ?: continue
            val current = merged[key]
            if (current == null || update.timestamp >= current.timestamp) merged[key] = update
        }
        return merged
    }

    /** Debounced restart, so a screen that subscribes tile by tile opens a single socket. */
    private fun scheduleRestart(exchanges: Set<ExchangeId>) {
        for (exchange in exchanges) {
            restartJobs.remove(exchange)?.cancel()
            restartJobs[exchange] = scope.launch {
                delay(RESTART_DEBOUNCE_MS)
                withContext(NonCancellable) { restartExchange(exchange) }
            }
        }
    }

    private suspend fun restartExchange(exchange: ExchangeId) {
        jobsMutex.withLock {
            val keys = tracker.keysOf(exchange)
            // A subscriber that leaves and comes straight back (a tab switch, or the live service
            // re-running its session because the charger was plugged in) drops the refcount to 0
            // and takes it back to 1, which marks the exchange "changed". Restarting a healthy
            // stream on the same key set would cost a full TLS handshake plus the market lookup —
            // and KuCoin another bullet-public round-trip — for no change at all.
            val running = streamJobs[exchange]
            if (running?.isActive == true && keys.isNotEmpty() && streamKeys[exchange] == keys) {
                return@withLock
            }
            streamJobs.remove(exchange)?.cancelAndJoin()
            val adapter = registry.getOrNull(exchange)
            if (keys.isEmpty() || adapter == null) {
                streamKeys.remove(exchange)
                clearState(exchange)
                return@withLock
            }
            setState(exchange, StreamState.CONNECTING)
            streamKeys[exchange] = keys
            streamJobs[exchange] = scope.launch { runStream(adapter, keys) }
        }
    }

    private suspend fun runStream(adapter: ExchangeAdapter, keys: Set<MarketKey>) {
        val exchange = adapter.id
        var backoff = RETRY_MIN_MS
        while (currentCoroutineContext().isActive) {
            val pending = LinkedHashMap<MarketKey, Ticker>()
            try {
                val resolved = resolveMarkets(exchange, keys)
                if (resolved.isEmpty()) {
                    Log.w(TAG, "no known markets for ${exchange.id}, not subscribing")
                    setState(exchange, StreamState.FAILED)
                } else {
                    setState(exchange, StreamState.CONNECTING)
                    var lastPersist = 0L
                    adapter.watchTickers(resolved).collect { ticker ->
                        backoff = RETRY_MIN_MS
                        val now = System.currentTimeMillis()
                        lastMessageAt[exchange] = now
                        setState(exchange, StreamState.ACTIVE)
                        live.update { it + (ticker.key to ticker) }
                        pending[ticker.key] = ticker
                        if (now - lastPersist >= PERSIST_INTERVAL_MS) {
                            lastPersist = now
                            persist(pending.values.toList())
                            pending.clear()
                        }
                    }
                    // The adapter closed the stream on its own; treat it like a drop.
                    setState(exchange, StreamState.FAILED)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "ticker stream failed for ${exchange.id}", e)
                setState(exchange, StreamState.FAILED)
            } finally {
                if (pending.isNotEmpty()) {
                    withContext(NonCancellable) { persist(pending.values.toList()) }
                }
            }
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(RETRY_MAX_MS)
        }
    }

    private suspend fun resolveMarkets(exchange: ExchangeId, keys: Set<MarketKey>): List<Market> {
        var found = markets.getMarkets(keys)
        if (found.size < keys.size) {
            markets.refreshMarkets(exchange)
            found = markets.getMarkets(keys)
        }
        return keys.mapNotNull { found[it] }
    }

    private suspend fun persist(tickers: List<Ticker>) {
        if (tickers.isEmpty()) return
        try {
            snapshotDao.upsertAll(tickers.map { it.toSnapshotEntity() })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "snapshot persist failed", e)
        }
    }

    private fun setState(exchange: ExchangeId, state: StreamState) {
        streamStates.update { current ->
            if (current[exchange] == state) current else current + (exchange to state)
        }
    }

    private fun clearState(exchange: ExchangeId) {
        lastMessageAt.remove(exchange)
        streamStates.update { current -> if (exchange in current) current - exchange else current }
    }

    private companion object {
        const val TAG = "LiveData"

        /** Coalesces the burst of subscriptions a screen fires while it is being composed. */
        const val RESTART_DEBOUNCE_MS = 300L

        /** Re-subscribing within this window (tab switch, added ticker) skips the REST round for markets that are already fresh. */
        const val REST_COOLDOWN_MS = 60_000L

        /** Live updates hit Room at most this often, per exchange, batched. */
        const val PERSIST_INTERVAL_MS = 5_000L

        const val RETRY_MIN_MS = 5_000L
        const val RETRY_MAX_MS = 60_000L

        const val STATUS_TICK_MS = 5_000L
        const val STATUS_STOP_TIMEOUT_MS = 5_000L
    }
}
