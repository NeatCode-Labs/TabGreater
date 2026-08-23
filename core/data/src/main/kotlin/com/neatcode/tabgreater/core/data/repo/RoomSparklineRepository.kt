package com.neatcode.tabgreater.core.data.repo

import android.util.Log
import com.neatcode.tabgreater.core.data.db.CandleDao
import com.neatcode.tabgreater.core.exchange.ExchangeAdapter
import com.neatcode.tabgreater.core.exchange.ExchangeRegistry
import com.neatcode.tabgreater.core.model.Candle
import com.neatcode.tabgreater.core.model.Market
import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.SparkPeriod
import com.neatcode.tabgreater.core.model.Timeframe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Room-backed [SparklineRepository].
 *
 * Per `(market, period)` it keeps the last `period.candles` bars of `period.timeframe` in the
 * `candles` table, refreshes them over REST at most every [SparklineRepository.REFRESH_INTERVAL_MS]
 * and extends them from the exchange kline stream while a collector is present.
 *
 * `observeSparkline` never completes on its own — the collector (`observeEach`) subscribes a key
 * once and would not re-subscribe a finished flow, so a tile would silently lose its sparkline
 * (and, on periods other than 24 h, its %, High, Low and Volume) for the rest of the session.
 */
class RoomSparklineRepository(
    private val candleDao: CandleDao,
    private val markets: MarketRepository,
    private val registry: ExchangeRegistry,
) : SparklineRepository {

    private data class CacheKey(val key: MarketKey, val period: SparkPeriod)

    private val mutexes = ConcurrentHashMap<CacheKey, Mutex>()
    private val lastRefresh = ConcurrentHashMap<CacheKey, Long>()

    override fun observeSparkline(key: MarketKey, period: SparkPeriod): Flow<Sparkline> = flow {
        val timeframe = period.timeframe
        val size = period.candles
        val cacheKey = CacheKey(key, period)

        val window = loadCached(key, timeframe, size).toMutableList()
        emit(buildSparkline(window, lastRefresh[cacheKey] ?: 0L))

        val adapter = registry.getOrNull(key.exchange)
        if (adapter == null) {
            // The exchange is not in this build — nothing to retry, so just hold the cache.
            awaitCancellation()
        }

        // Unknown market (delisted, or offline on first use): keep showing the cached window and
        // retry the instrument list every RETRY_MS until the market resolves.
        var resolved = resolveMarket(key)
        while (resolved == null) {
            delay(RETRY_MS)
            val cached = loadCached(key, timeframe, size)
            if (cached != window) {
                window.clear()
                window += cached
                emit(buildSparkline(window, lastRefresh[cacheKey] ?: 0L))
            }
            resolved = resolveMarket(key)
        }
        val market = resolved

        val newestCached = window.lastOrNull()?.openTime
        if (refreshIfStale(cacheKey, adapter, market, timeframe, size, force = window.isEmpty(), newestCached = newestCached)) {
            window.clear()
            window += loadCached(key, timeframe, size)
            emit(buildSparkline(window, lastRefresh[cacheKey] ?: 0L))
        }

        adapter.watchKlines(market, timeframe)
            .catch { e -> Log.w(TAG, "kline stream failed for ${key.value} ${timeframe.id}", e) }
            .collect { candle ->
                try {
                    candleDao.upsertAll(listOf(candle.toEntity(key, timeframe)))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "candle cache write failed for ${key.value}", e)
                }
                window.mergeCandle(candle, size)
                emit(buildSparkline(window, lastRefresh[cacheKey] ?: 0L))
            }
        // `catch` above turns a failing stream into a normal completion; hold the last window
        // instead, so the tile keeps its sparkline and the key stays subscribed.
        awaitCancellation()
    }

    override suspend fun refresh(keys: Collection<MarketKey>, period: SparkPeriod) {
        val timeframe = period.timeframe
        val size = period.candles
        for (key in keys.distinct()) {
            val adapter = registry.getOrNull(key.exchange) ?: continue
            val market = resolveMarket(key) ?: continue
            refreshIfStale(CacheKey(key, period), adapter, market, timeframe, size, force = true)
        }
    }

    override suspend fun cached(key: MarketKey, period: SparkPeriod): Sparkline {
        val window = loadCached(key, period.timeframe, period.candles)
        return buildSparkline(window, lastRefresh[CacheKey(key, period)] ?: 0L)
    }

    /**
     * Fetches `size + 1` bars over REST when the cache is empty, stale or [force]d, prunes the
     * history down to `2 * size` bars and returns `true` when the cache actually changed.
     *
     * @param newestCached open time of the newest cached bar, used to judge freshness when this
     *   process has not refreshed the window yet (cold start).
     */
    private suspend fun refreshIfStale(
        cacheKey: CacheKey,
        adapter: ExchangeAdapter,
        market: Market,
        timeframe: Timeframe,
        size: Int,
        force: Boolean,
        newestCached: Long? = null,
    ): Boolean {
        if (!force && !isStale(cacheKey, timeframe, newestCached)) return false
        return mutexes.getOrPut(cacheKey) { Mutex() }.withLock {
            if (!force && !isStale(cacheKey, timeframe, newestCached)) return@withLock false
            try {
                val fetched = adapter.fetchOHLCV(market, timeframe, null, size + 1)
                if (fetched.isEmpty()) return@withLock false
                candleDao.upsertAll(fetched.map { it.toEntity(cacheKey.key, timeframe) })
                prune(cacheKey.key, timeframe, size)
                lastRefresh[cacheKey] = System.currentTimeMillis()
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "OHLCV refresh failed for ${cacheKey.key.value} ${timeframe.id}", e)
                false
            }
        }
    }

    /**
     * Stale = no REST refresh in the last [SparklineRepository.REFRESH_INTERVAL_MS]. Before the
     * first refresh of this process the cache itself is the evidence: a window whose newest bar
     * is at most one bar behind real time was kept current by the kline stream (or the app was
     * only just closed), so a cold start does not fire one OHLCV request per tile — that burst,
     * multiplied by tab switches, is what gets an IP rate-limited. Anything older is a gap that
     * only REST can fill.
     */
    private fun isStale(cacheKey: CacheKey, timeframe: Timeframe, newestCached: Long?): Boolean {
        val now = System.currentTimeMillis()
        val last = lastRefresh[cacheKey]
        if (last != null) return now - last >= SparklineRepository.REFRESH_INTERVAL_MS
        if (newestCached == null) return true
        return now - newestCached >= 2 * timeframe.millis
    }

    /** Keeps twice the visible window so a period switch does not always hit the network. */
    private suspend fun prune(key: MarketKey, timeframe: Timeframe, size: Int) {
        val keep = candleDao.latest(key.value, timeframe.id, size * 2)
        if (keep.size < size * 2) return
        candleDao.prune(key.value, timeframe.id, keep.last().openTime)
    }

    private suspend fun loadCached(key: MarketKey, timeframe: Timeframe, size: Int): List<Candle> =
        candleDao.latest(key.value, timeframe.id, size).asReversed().map { it.toModel() }

    /** Falls back to one instrument-list refresh when the market is unknown locally. */
    private suspend fun resolveMarket(key: MarketKey): Market? {
        markets.getMarket(key)?.let { return it }
        markets.refreshMarkets(key.exchange)
        return markets.getMarket(key)
    }

    private companion object {
        const val TAG = "Sparkline"

        /** How long to wait before asking the instrument list again for an unknown market. */
        const val RETRY_MS = 60_000L
    }
}
