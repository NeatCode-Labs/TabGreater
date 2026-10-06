package com.neatcode.tabgreater.core.data.repo

import android.os.SystemClock
import android.util.Log
import com.neatcode.tabgreater.core.data.db.CandleDao
import com.neatcode.tabgreater.core.exchange.*
import com.neatcode.tabgreater.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.CoroutineContext

/** One candle session per market/period; disk changes and network results reach existing observers. */
class RoomSparklineRepository(
    private val candleDao: CandleDao,
    private val markets: MarketRepository,
    private val registry: ExchangeRegistry,
    private val elapsed: () -> Long = SystemClock::elapsedRealtime,
    private val epoch: () -> Long = System::currentTimeMillis,
) : SparklineRepository {
    private data class Key(val market: MarketKey, val period: SparkPeriod)
    private class Session(val key: Key, initial: List<Candle>, context: CoroutineContext) {
        val owner = SupervisorJob()
        val scope = CoroutineScope(context.minusKey(Job) + owner)
        val lock = Mutex()
        val flights = SingleFlight<Unit, Boolean>()
        val window = initial.toMutableList()
        val revisions = mutableMapOf<Long, Long>()
        var version = 0L
        var refs = 0
        var started = false
        var recovery: Job? = null
        var nextRecoveryAt = 0L
        var refreshedAt = 0L
        var history = HistoryState.CACHED
        val state = MutableStateFlow(buildSparkline(initial, 0).copy(history = HistoryState.CACHED))
    }
    private val entriesLock = Mutex()
    private val entries = mutableMapOf<Key, Session>()
    private data class Retained(val bars: List<Candle>, val refreshedAt: Long, val history: HistoryState)
    // Data only: released sessions retain no jobs or HTTP owners. Also covers a failed Room write.
    private val retained = linkedMapOf<Key, Retained>()

    private suspend fun acquire(key: Key): Session {
        val context = currentCoroutineContext()
        return entriesLock.withLock {
            entries.getOrPut(key) {
                val saved = retained.remove(key)
                val bars = (load(key) + saved?.bars.orEmpty()).associateBy { it.openTime }.values.sortedBy { it.openTime }
                Session(key, bars, context).also {
                    if (saved != null) { it.refreshedAt = saved.refreshedAt; it.history = saved.history }
                    publish(it)
                }
            }.also { it.refs++ }
        }
    }
    private suspend fun release(session: Session) = withContext(NonCancellable) {
        entriesLock.withLock {
            session.refs--
            if (session.refs == 0) {
                if (entries[session.key] === session) entries.remove(session.key)
                session.owner.cancel()
                session.lock.withLock {
                    retained[session.key] = Retained(session.window.toList(), session.refreshedAt, session.history)
                    while (retained.size > 128) retained.remove(retained.keys.first())
                }
            }
        }
    }

    override fun observeSparkline(key: MarketKey, period: SparkPeriod): Flow<Sparkline> = flow {
        val session = acquire(Key(key, period))
        try {
            entriesLock.withLock {
                if (!session.started) {
                    session.started = true
                    start(session)
                }
            }
            emitAll(session.state)
        } finally { release(session) }
    }.distinctUntilChanged()

    private fun start(session: Session) {
        val key = session.key
        session.scope.launch {
            // Quiet markets still advance their time window and stop treating a closed bar as forming.
            while (isActive) { delay(5_000); session.lock.withLock { publish(session) } }
        }
        session.scope.launch {
            // Room also changes during an explicit refresh and during cache maintenance.
            while (isActive) {
                try {
                    candleDao.observeLatest(key.market.value, key.period.timeframe.id, key.period.candles * 2)
                        .distinctUntilChanged().collect { rows ->
                            session.lock.withLock {
                                for (bar in rows.asReversed().map { it.toModel() }) {
                                    // A disk replay has no authority over a value accepted in this session.
                                    if (bar.openTime !in session.revisions) session.window.mergeCandle(bar, key.period.candles * 2)
                                }
                                publish(session)
                            }
                        }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { Log.w(TAG, "candle cache read failed", e); delay(RETRY_MS) }
            }
        }
        val adapter = registry.getOrNull(key.market.exchange) ?: return
        session.scope.launch {
            var resolved: Market? = null
            while (isActive && resolved == null) {
                resolved = resolve(key.market)
                if (resolved == null) delay(RETRY_MS)
            }
            val market = resolved ?: return@launch
            val newest = session.window.lastOrNull()?.openTime
            if (session.history != HistoryState.VERIFIED || newest == null || epoch() - newest >= 2 * key.period.timeframe.millis)
                requestRecovery(session, adapter, market)
            launch {
                while (isActive) {
                    delay(SparklineRepository.REFRESH_INTERVAL_MS)
                    requestRecovery(session, adapter, market)
                }
            }
            var attempts = 0
            while (isActive) {
                try {
                    adapter.watchKlines(market, key.period.timeframe).collect { candle ->
                        ensureActive()
                        if (!valid(candle)) return@collect
                        var gap = false
                        session.lock.withLock {
                            val previous = session.window.lastOrNull()?.openTime
                            gap = previous != null && candle.openTime - previous > key.period.timeframe.millis
                            session.version++
                            session.revisions[candle.openTime] = session.version
                            session.window.mergeCandle(candle, key.period.candles * 2)
                            if (gap) session.history = HistoryState.INCOMPLETE
                            publish(session)
                            persist(session, listOf(candle))
                        }
                        if (gap) requestRecovery(session, adapter, market)
                        attempts = 0
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { Log.w(TAG, "candle stream failed", e) }
                delay(retryDelay(attempts++))
            }
        }
    }

    private fun requestRecovery(session: Session, adapter: ExchangeAdapter, market: Market) {
        synchronized(session) {
            if (session.recovery?.isActive == true || elapsed() < session.nextRecoveryAt) return
            session.recovery = session.scope.launch { recover(session, adapter, market) }
        }
    }

    private suspend fun recover(session: Session, adapter: ExchangeAdapter, market: Market) {
        var attempt = 0
        while (currentCoroutineContext().isActive) {
            try { refreshSession(session, adapter, market); return }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (!e.canRetryAutomatically() || attempt >= 5) {
                    session.nextRecoveryAt = if (e.canRetryAutomatically()) elapsed() + SparklineRepository.REFRESH_INTERVAL_MS else Long.MAX_VALUE
                    return
                }
                val pause = retryDelay(attempt++)
                delay(maxOf(pause + kotlin.random.Random.nextLong(pause / 5 + 1), e.retryDelayMs()))
            }
        }
    }

    override suspend fun refresh(keys: Collection<MarketKey>, period: SparkPeriod) {
        val failures = mutableListOf<Throwable>()
        for (key in keys.distinct()) {
            val adapter = registry.getOrNull(key.exchange) ?: continue
            val session = acquire(Key(key, period))
            try {
                val market = markets.getMarket(key) ?: run {
                    markets.refreshMarkets(key.exchange).getOrThrow()
                    markets.getMarket(key)
                } ?: throw ExchangeHttpException(key.exchange, 404, "Market is no longer available")
                refreshSession(session, adapter, market)
            }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                session.lock.withLock { session.history = HistoryState.UNAVAILABLE; publish(session) }
                failures += e
            }
            finally { release(session) }
        }
        if (failures.isNotEmpty()) throw failures.first()
    }

    private suspend fun refreshSession(session: Session, adapter: ExchangeAdapter, market: Market): Boolean =
        session.flights.run(Unit) {
            val key = session.key
            val started = session.lock.withLock { session.version }
            try {
                val fetched = adapter.fetchOHLCV(market, key.period.timeframe, null, key.period.candles + 1)
                    .filter(::valid).distinctBy { it.openTime }.sortedBy { it.openTime }
                session.lock.withLock {
                    val accepted = fetched.filter { (session.revisions[it.openTime] ?: 0) <= started }
                    for (bar in accepted) {
                        session.version++
                        session.revisions[bar.openTime] = session.version
                        session.window.mergeCandle(bar, key.period.candles * 2)
                    }
                    session.refreshedAt = epoch()
                    session.nextRecoveryAt = 0L
                    session.history = if (fetched.isEmpty()) HistoryState.INCOMPLETE else HistoryState.VERIFIED
                    publish(session)
                    persist(session, accepted)
                    fetched.isNotEmpty()
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                session.lock.withLock { session.history = HistoryState.UNAVAILABLE; publish(session) }
                throw e
            }
        }

    private fun publish(session: Session) {
        session.state.value = snapshot(session.key, session.window, session.refreshedAt, session.history)
        val retained = session.window.mapTo(hashSetOf()) { it.openTime }
        session.revisions.keys.retainAll(retained)
    }

    private fun snapshot(key: Key, window: List<Candle>, refreshedAt: Long, history: HistoryState): Sparkline {
        val step = key.period.timeframe.millis
        val end = (epoch() / step) * step
        val start = end - key.period.candles * step
        val bars = window.filter { it.openTime >= start && it.openTime < end + step }
        val anchor = bars.firstOrNull()?.takeIf { it.openTime == start }?.open
            ?: window.lastOrNull { it.openTime < start }?.close
        return buildSparkline(bars, refreshedAt).copy(
            firstClose = anchor,
            lastClose = bars.lastOrNull()?.close ?: window.lastOrNull { it.openTime <= end }?.close,
            history = history,
            lastOpenTime = bars.lastOrNull()?.openTime,
            forming = bars.lastOrNull()?.let { it.openTime >= end && it.openTime < end + step } == true,
        )
    }

    private suspend fun persist(session: Session, bars: List<Candle>) {
        try {
            candleDao.upsertAll(bars.map { it.toEntity(session.key.market, session.key.period.timeframe) })
            val keep = candleDao.latest(session.key.market.value, session.key.period.timeframe.id, session.key.period.candles * 2)
            if (keep.size >= session.key.period.candles * 2) candleDao.prune(session.key.market.value, session.key.period.timeframe.id, keep.last().openTime)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Log.w(TAG, "candle cache write failed", e) }
    }

    override suspend fun cached(key: MarketKey, period: SparkPeriod): Sparkline {
        entriesLock.withLock {
            entries[Key(key, period)]?.let { return it.state.value }
            retained[Key(key, period)]?.let { return snapshot(Key(key, period), it.bars, it.refreshedAt, it.history) }
        }
        return snapshot(Key(key, period), load(Key(key, period)), 0, HistoryState.CACHED)
    }
    private suspend fun load(key: Key): List<Candle> = try {
        candleDao.latest(key.market.value, key.period.timeframe.id, key.period.candles * 2).asReversed().map { it.toModel() }
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) { Log.w(TAG, "candle cache unavailable", e); emptyList() }

    private suspend fun resolve(key: MarketKey): Market? = try {
        markets.getMarket(key) ?: run { markets.refreshMarkets(key.exchange).getOrThrow(); markets.getMarket(key) }
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) { Log.w(TAG, "market lookup failed", e); null }

    private fun valid(c: Candle) = c.openTime >= 0 && listOf(c.open, c.high, c.low, c.close, c.volume).all { it.isFinite() } &&
        c.open > 0 && c.close > 0 && c.low > 0 && c.high >= maxOf(c.open, c.close, c.low) && c.low <= minOf(c.open, c.close) && c.volume >= 0
    private fun retryDelay(attempt: Int): Long = when (attempt) { 0 -> 5_000; 1 -> 10_000; 2 -> 20_000; 3 -> 40_000; 4 -> 60_000; else -> 900_000 }
    private companion object { const val TAG = "Sparkline"; const val RETRY_MS = 60_000L }
}
