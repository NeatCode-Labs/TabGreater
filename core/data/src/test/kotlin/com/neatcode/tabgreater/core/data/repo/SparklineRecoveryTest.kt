package com.neatcode.tabgreater.core.data.repo

import com.neatcode.tabgreater.core.exchange.*
import com.neatcode.tabgreater.core.model.*
import com.neatcode.tabgreater.core.data.db.CandleDao
import com.neatcode.tabgreater.core.data.db.CandleEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SparklineRecoveryTest {
    private val key = MarketKey("binance:BTC/EUR")
    private val period = SparkPeriod.HOUR_1
    private val now = 1_800_000_000_000L
    private val step = period.timeframe.millis
    private fun bar(at: Long, price: Double) = Candle(at, price, price, price, price, 1.0, false)
    private class Adapter(val instrument: Market) : ExchangeAdapter by FakeExchangeAdapter(ExchangeId.BINANCE, listOf(instrument)) {
        val stream = MutableSharedFlow<Candle>(extraBufferCapacity = 8)
        var calls = 0
        var subscriptions = 0
        var response: suspend () -> List<Candle> = { emptyList() }
        override suspend fun fetchOHLCV(market: Market, timeframe: Timeframe, endTime: Long?, limit: Int): List<Candle> { calls++; return response() }
        override fun watchKlines(market: Market, timeframe: Timeframe): Flow<Candle> = flow {
            subscriptions++
            try { emitAll(stream) } finally { subscriptions-- }
        }
    }
    private fun TestScope.repo(dao: CandleDao, adapter: Adapter): RoomSparklineRepository {
        val registry = ExchangeRegistry(listOf(adapter))
        return RoomSparklineRepository(dao, RoomMarketRepository(FakeMarketDao(), registry), registry,
            elapsed = { testScheduler.currentTime }, epoch = { now })
    }
    @Test fun `forced refresh reaches existing observer without a new stream event`() = runTest {
        val dao = FakeCandleDao()
        val adapter = Adapter(market(key.value))
        adapter.response = { listOf(bar(now - step, 100.0), bar(now, 101.0)) }
        val repo = repo(dao, adapter)
        val values = mutableListOf<Sparkline>()
        val observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repo.observeSparkline(key, period).toList(values) }
        runCurrent()
        adapter.response = { listOf(bar(now - step, 200.0), bar(now, 201.0)) }
        repo.refresh(listOf(key), period)
        runCurrent()
        assertEquals(201.0, values.last().lastClose!!, 0.0)
        assertEquals(HistoryState.VERIFIED, values.last().history)
        observer.cancelAndJoin()
    }
    @Test fun `gap repairs history while a later stream candle wins over delayed REST`() = runTest {
        val dao = FakeCandleDao()
        val adapter = Adapter(market(key.value))
        adapter.response = { listOf(bar(now - 4 * step, 1.0), bar(now - 3 * step, 2.0)) }
        val repo = repo(dao, adapter)
        val values = mutableListOf<Sparkline>()
        val observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repo.observeSparkline(key, period).toList(values) }
        runCurrent()
        val release = CompletableDeferred<Unit>()
        adapter.response = { release.await(); listOf(bar(now - 2 * step, 3.0), bar(now - step, 4.0), bar(now, 5.0)) }
        adapter.stream.emit(bar(now, 50.0))
        runCurrent()
        adapter.stream.emit(bar(now, 99.0))
        runCurrent()
        release.complete(Unit)
        runCurrent()
        assertEquals(99.0, values.last().lastClose!!, 0.0)
        assertTrue(values.last().points.any { it == 3f })
        assertEquals(99.0, dao.latest(key.value, period.timeframe.id, 1).single().close, 0.0)
        assertEquals(2, adapter.calls)
        observer.cancelAndJoin()
    }
    @Test fun `two observers share one stream and last cancellation releases it`() = runTest {
        val adapter = Adapter(market(key.value))
        adapter.response = { listOf(bar(now, 10.0)) }
        val repo = repo(FakeCandleDao(), adapter)
        val first = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repo.observeSparkline(key, period).collect() }
        val second = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repo.observeSparkline(key, period).collect() }
        runCurrent()
        assertEquals(1, adapter.subscriptions)
        assertEquals(1, adapter.calls)
        first.cancelAndJoin()
        assertEquals(1, adapter.subscriptions)
        second.cancelAndJoin()
        runCurrent()
        assertEquals(0, adapter.subscriptions)
    }
    @Test fun `one shot refresh works without observers and sparse history does not invent candles`() = runTest {
        val dao = FakeCandleDao()
        val adapter = Adapter(market(key.value))
        adapter.response = { listOf(bar(now - 100 * step, 1.0), bar(now - step, 2.0), bar(now, 3.0)) }
        val repo = repo(dao, adapter)
        repo.refresh(listOf(key), period)
        assertEquals(0, adapter.subscriptions)
        assertEquals(3, dao.all.size)
        assertEquals(3.0, repo.cached(key, period).lastClose!!, 0.0)
    }

    @Test fun `one shot accepted history survives a failed disk write without retaining a stream`() = runTest {
        val dao = object : CandleDao by FakeCandleDao() {
            override suspend fun upsertAll(candles: List<CandleEntity>) { error("disk full") }
        }
        val adapter = Adapter(market(key.value)).apply { response = { listOf(bar(now - step, 10.0), bar(now, 11.0)) } }
        val repo = repo(dao, adapter)
        repo.refresh(listOf(key), period)
        assertEquals(0, adapter.subscriptions)
        assertEquals(11.0, repo.cached(key, period).lastClose!!, 0.0)
        assertEquals(HistoryState.VERIFIED, repo.cached(key, period).history)
    }
}
