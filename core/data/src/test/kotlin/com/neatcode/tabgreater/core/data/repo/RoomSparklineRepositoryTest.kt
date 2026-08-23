package com.neatcode.tabgreater.core.data.repo

import com.neatcode.tabgreater.core.exchange.ExchangeAdapter
import com.neatcode.tabgreater.core.exchange.ExchangeRegistry
import com.neatcode.tabgreater.core.model.Candle
import com.neatcode.tabgreater.core.model.ExchangeId
import com.neatcode.tabgreater.core.model.Market
import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.SparkPeriod
import com.neatcode.tabgreater.core.model.Timeframe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `observeSparkline` must never complete: `observeEach` subscribes one flow per market key and
 * does not re-subscribe a finished one, so a completion silently kills the tile's sparkline for
 * the rest of the session (review finding 2).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RoomSparklineRepositoryTest {

    private val key = MarketKey("binance:BTC/EUR")
    private val candleDao = FakeCandleDao()
    private val markets = FakeMarketRepository()

    @Test
    fun `emits the cached window before touching the network`() = runTest {
        val now = System.currentTimeMillis()
        candleDao.upsertAll(
            (0 until 4).map { candleEntity(key.value, now - (3L - it) * PERIOD.timeframe.millis, PERIOD.timeframe.id) },
        )
        markets.publish(market(key.value))
        val adapter = RecordingAdapter(ExchangeId.BINANCE, ohlcv = listOf(candle(now, 9.0)))
        val repository = repository(adapter)

        val emissions = mutableListOf<Sparkline>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeSparkline(key, PERIOD).toList(emissions)
        }
        runCurrent()

        assertEquals(1, emissions.size)
        assertEquals(4, emissions.single().points.size)
        // The cache is younger than two bars, so a cold start does not fire an OHLCV request.
        assertEquals(0, adapter.ohlcvCalls)
    }

    @Test
    fun `an unknown market keeps the cache, retries and recovers`() = runTest {
        val adapter = RecordingAdapter(
            ExchangeId.BINANCE,
            ohlcv = listOf(candle(1L, 1.0), candle(2L, 2.0), candle(3L, 3.0)),
        )
        val repository = repository(adapter)

        val emissions = mutableListOf<Sparkline>()
        var completed = false
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeSparkline(key, PERIOD).onCompletion { completed = true }.toList(emissions)
        }
        runCurrent()

        // Offline first use: the market cannot be resolved, but the flow stays alive on the cache.
        assertEquals(1, emissions.size)
        assertTrue(emissions.single().isEmpty)
        assertEquals(1, markets.refreshCalls)
        assertEquals(0, adapter.ohlcvCalls)
        assertTrue(job.isActive)
        assertFalse(completed)

        // Nothing changes while the market stays unknown, but the retry keeps asking.
        advanceTimeBy(RETRY_MS + 1)
        runCurrent()
        assertEquals(1, emissions.size)
        assertEquals(2, markets.refreshCalls)

        // The instrument list finally arrives: the same subscription picks the market up.
        markets.publish(market(key.value))
        advanceTimeBy(RETRY_MS + 1)
        runCurrent()

        assertEquals(2, emissions.size)
        assertEquals(3, emissions.last().points.size)
        assertEquals(1, adapter.ohlcvCalls)
        assertTrue(job.isActive)
        assertFalse(completed)
    }

    @Test
    fun `a missing adapter emits the cache and stays subscribed`() = runTest {
        candleDao.upsertAll(listOf(candleEntity(key.value, 1L, PERIOD.timeframe.id)))
        val repository = repository(RecordingAdapter(ExchangeId.KRAKEN))

        val emissions = mutableListOf<Sparkline>()
        var completed = false
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeSparkline(key, PERIOD).onCompletion { completed = true }.toList(emissions)
        }
        runCurrent()
        advanceTimeBy(10 * RETRY_MS)
        runCurrent()

        assertEquals(1, emissions.size)
        assertEquals(0, markets.refreshCalls)
        assertTrue(job.isActive)
        assertFalse(completed)
    }

    private fun repository(vararg adapters: ExchangeAdapter) =
        RoomSparklineRepository(candleDao, markets, ExchangeRegistry(adapters.toList()))

    private fun candle(openTime: Long, close: Double) =
        Candle(openTime = openTime, open = close, high = close, low = close, close = close, volume = 1.0)

    private companion object {
        val PERIOD = SparkPeriod.HOURS_24

        /** Mirrors `RoomSparklineRepository.RETRY_MS`. */
        const val RETRY_MS = 60_000L
    }
}

/** [MarketRepository] whose instrument list can be published mid-test, counting refresh calls. */
private class FakeMarketRepository : MarketRepository {
    private val known = LinkedHashMap<MarketKey, Market>()

    var refreshCalls: Int = 0
        private set

    fun publish(market: Market) {
        known[market.key] = market
    }

    override suspend fun refreshMarkets(exchange: ExchangeId, force: Boolean): Result<Unit> {
        refreshCalls++
        return Result.success(Unit)
    }

    override suspend fun refreshAll(force: Boolean) = Unit

    override suspend fun getMarket(key: MarketKey): Market? = known[key]

    override suspend fun getMarkets(keys: Collection<MarketKey>): Map<MarketKey, Market> =
        keys.mapNotNull { key -> known[key]?.let { key to it } }.toMap()

    override suspend fun search(query: String, limit: Int): List<Market> = emptyList()
}

/** [FakeExchangeAdapter] with a canned OHLCV response and kline stream. */
private class RecordingAdapter(
    id: ExchangeId,
    private val ohlcv: List<Candle> = emptyList(),
    private val klines: Flow<Candle> = emptyFlow(),
) : ExchangeAdapter by FakeExchangeAdapter(id) {

    var ohlcvCalls: Int = 0
        private set

    override suspend fun fetchOHLCV(
        market: Market,
        timeframe: Timeframe,
        endTime: Long?,
        limit: Int,
    ): List<Candle> {
        ohlcvCalls++
        return ohlcv
    }

    override fun watchKlines(market: Market, timeframe: Timeframe): Flow<Candle> = klines
}
