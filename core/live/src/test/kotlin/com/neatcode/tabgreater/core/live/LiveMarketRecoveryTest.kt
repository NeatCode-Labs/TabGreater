package com.neatcode.tabgreater.core.live

import com.neatcode.tabgreater.core.data.db.*
import com.neatcode.tabgreater.core.data.repo.MarketRepository
import com.neatcode.tabgreater.core.exchange.*
import com.neatcode.tabgreater.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LiveMarketRecoveryTest {
    private val key = MarketKey("binance:BTC/EUR")
    private val other = MarketKey("binance:ETH/EUR")
    private val instrument = Market(key, "BTCEUR", 2)
    private fun price(value: Double, time: Long = 100) = Ticker(key, value, timestamp = time)
    private class Dao : TickerSnapshotDao {
        val rows = MutableStateFlow<Map<String, TickerSnapshotEntity>>(emptyMap())
        var failWrites = false
        override fun observeByKeys(keys: List<String>) = rows.map { it.filterKeys { key -> key in keys }.values.toList() }
        override suspend fun get(key: String) = rows.value[key]
        override suspend fun upsert(snapshot: TickerSnapshotEntity) { upsertAll(listOf(snapshot)) }
        override suspend fun upsertAll(snapshots: List<TickerSnapshotEntity>) {
            if (failWrites) error("disk full")
            rows.value = rows.value + snapshots.associateBy { it.marketKey }
        }
        override suspend fun distinctKeys() = rows.value.keys.toList()
        override suspend fun deleteByKeys(keys: List<String>) { rows.value -= keys.toSet() }
    }
    private class Adapter : ExchangeAdapter {
        override val id = ExchangeId.BINANCE
        override val nativeTimeframes = Timeframe.entries.toSet()
        val stream = MutableSharedFlow<Ticker>(extraBufferCapacity = 8)
        var response: suspend (List<Market>) -> List<Ticker> = { emptyList() }
        var batchResponse: (suspend (List<Market>) -> TickerBatch)? = null
        var calls = 0
        override suspend fun listMarkets() = emptyList<Market>()
        override suspend fun fetchTickers(markets: List<Market>): List<Ticker> { calls++; return response(markets) }
        override suspend fun fetchTickerBatch(markets: List<Market>): TickerBatch {
            val batch = batchResponse ?: return super.fetchTickerBatch(markets)
            calls++
            return batch(markets)
        }
        override suspend fun fetchOHLCV(market: Market, timeframe: Timeframe, endTime: Long?, limit: Int) = emptyList<Candle>()
        override fun watchTickers(markets: List<Market>) = stream.filter { tick -> markets.any { it.key == tick.key } }
        override fun watchKlines(market: Market, timeframe: Timeframe) = emptyFlow<Candle>()
    }
    private fun TestScope.repo(dao: Dao, adapter: Adapter): LiveMarketDataRepository {
        val catalogue = listOf(instrument, Market(other, "ETHEUR", 2))
        val markets = object : MarketRepository {
            override suspend fun refreshMarkets(exchange: ExchangeId, force: Boolean) = Result.success(Unit)
            override suspend fun refreshAll(force: Boolean) = Unit
            override suspend fun getMarket(key: MarketKey) = catalogue.find { it.key == key }
            override suspend fun getMarkets(keys: Collection<MarketKey>) = catalogue.filter { it.key in keys }.associateBy { it.key }
            override suspend fun search(query: String, limit: Int, assetClass: AssetClass?) = catalogue
            override suspend fun popularStockRoots(limit: Int) = emptyList<String>()
        }
        return LiveMarketDataRepository(dao, markets, ExchangeRegistry(listOf(adapter)), backgroundScope,
            elapsed = { testScheduler.currentTime }, epoch = { 1_800_000_000_000L + testScheduler.currentTime })
    }
    @Test fun `REST accepted without an observer supersedes a future-dated disk snapshot`() = runTest {
        val dao = Dao()
        dao.upsert(price(1.0, Long.MAX_VALUE).toSnapshotEntity())
        val adapter = Adapter().apply { response = { listOf(price(2.0, 1)) } }
        val repo = repo(dao, adapter)
        assertTrue(repo.refreshResult(listOf(key)).complete)
        assertEquals(2.0, repo.latest.value.getValue(key).last, 0.0)
        assertEquals(2.0, dao.get(key.value)!!.last, 0.0)
        assertEquals(PriceFreshness.CURRENT, repo.currentState(key)!!.freshness)
    }
    @Test fun `late REST cannot overwrite a newer stream value in memory or Room`() = runTest {
        val dao = Dao()
        val adapter = Adapter().apply { response = { listOf(price(10.0)) } }
        val repo = repo(dao, adapter)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repo.observeTickers(setOf(key)).collect() }
        runCurrent(); advanceTimeBy(301); runCurrent()
        val release = CompletableDeferred<Unit>()
        adapter.response = { release.await(); listOf(price(20.0, 500)) }
        val request = async { repo.refreshResult(listOf(key)) }
        runCurrent()
        adapter.stream.emit(price(30.0, 200)); runCurrent()
        release.complete(Unit); request.await(); runCurrent()
        assertEquals(30.0, repo.latest.value.getValue(key).last, 0.0)
        assertEquals(30.0, dao.get(key.value)!!.last, 0.0)
        job.cancelAndJoin()
    }
    @Test fun `quote-only traffic does not renew an old price but same-price snapshot does`() = runTest {
        val adapter = Adapter().apply { response = { listOf(price(10.0)) } }
        val repo = repo(Dao(), adapter)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repo.observeTickers(setOf(key)).collect() }
        runCurrent(); advanceTimeBy(600_001); runCurrent()
        adapter.stream.emit(price(999.0).copy(bid = 11.0, confirmsPrice = false)); runCurrent()
        assertEquals(10.0, repo.currentState(key)!!.ticker!!.last, 0.0)
        assertEquals(PriceFreshness.OLD, repo.currentState(key)!!.freshness)
        adapter.stream.emit(price(10.0, 101)); runCurrent()
        assertEquals(PriceFreshness.CURRENT, repo.currentState(key)!!.freshness)
        job.cancelAndJoin()
    }
    @Test fun `cache failure does not hide accepted data and an unrelated pair does not become fresh`() = runTest {
        val dao = Dao().apply { failWrites = true }
        val adapter = Adapter().apply { response = { listOf(price(10.0)) } }
        val repo = repo(dao, adapter)
        val result = repo.refreshResult(listOf(key, other))
        assertEquals(setOf(key), result.successful)
        assertEquals(setOf(other), result.missing)
        assertEquals(PriceFreshness.CURRENT, repo.currentState(key)!!.freshness)
        assertEquals(PriceFreshness.MISSING, repo.currentState(other)!!.freshness)
    }

    @Test fun `cancelled request retries at cooldown while observed and confirms only actual response`() = runTest {
        val adapter = Adapter()
        val gate = CompletableDeferred<Unit>()
        adapter.response = { gate.await(); listOf(price(10.0)) }
        val repo = repo(Dao(), adapter)
        var observer = backgroundScope.launch { repo.observeTickers(setOf(key)).collect() }
        runCurrent()
        assertEquals(1, adapter.calls)
        observer.cancelAndJoin()
        adapter.response = { listOf(price(20.0)) }
        observer = backgroundScope.launch { repo.observeTickers(setOf(key)).collect() }
        runCurrent()
        advanceTimeBy(59_999); runCurrent()
        assertEquals(1, adapter.calls)
        assertNull(repo.currentState(key))
        advanceTimeBy(1); runCurrent()
        assertEquals(2, adapter.calls)
        assertEquals(20.0, repo.currentState(key)!!.ticker!!.last, 0.0)
        assertEquals(PriceFreshness.CURRENT, repo.currentState(key)!!.freshness)
        observer.cancelAndJoin()
    }

    @Test fun `cancelled retry delay is resumed after cooldown`() = runTest {
        val adapter = Adapter().apply { response = { error("temporary") } }
        val repo = repo(Dao(), adapter)
        var observer = backgroundScope.launch { repo.observeTickers(setOf(key)).collect() }
        runCurrent()
        assertEquals(ExchangeFailureKind.TRANSIENT, repo.currentState(key)!!.failure)
        observer.cancelAndJoin()
        adapter.response = { listOf(price(11.0)) }
        observer = backgroundScope.launch { repo.observeTickers(setOf(key)).collect() }
        runCurrent()
        advanceTimeBy(59_999); runCurrent()
        assertEquals(1, adapter.calls)
        advanceTimeBy(1); runCurrent()
        assertEquals(2, adapter.calls)
        assertNull(repo.currentState(key)!!.failure)
        assertEquals(PriceFreshness.CURRENT, repo.currentState(key)!!.freshness)
        observer.cancelAndJoin()
    }

    @Test fun `another cancelled refresh extends pending cooldown without losing recovery`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val adapter = Adapter().apply { response = { gate.await(); listOf(price(10.0)) } }
        val repo = repo(Dao(), adapter)
        var observer = backgroundScope.launch { repo.observeTickers(setOf(key)).collect() }
        runCurrent()
        observer.cancelAndJoin()
        observer = backgroundScope.launch { repo.observeTickers(setOf(key)).collect() }
        runCurrent()
        advanceTimeBy(59_000); runCurrent()
        val external = backgroundScope.launch { repo.refreshResult(listOf(key)) }
        runCurrent()
        assertEquals(2, adapter.calls)
        external.cancelAndJoin()
        adapter.response = { listOf(price(20.0)) }
        advanceTimeBy(1_000); runCurrent()
        advanceTimeBy(58_999); runCurrent()
        assertEquals(2, adapter.calls)
        advanceTimeBy(1); runCurrent()
        assertEquals(3, adapter.calls)
        assertEquals(PriceFreshness.CURRENT, repo.currentState(key)!!.freshness)
        observer.cancelAndJoin()
    }

    @Test fun `resubscribe retains server retry deadline even beyond cooldown`() = runTest {
        val adapter = Adapter().apply {
            response = { throw ExchangeHttpException(id, 429, "limited", retryAfterMs = 120_000) }
        }
        val repo = repo(Dao(), adapter)
        var observer = backgroundScope.launch { repo.observeTickers(setOf(key)).collect() }
        runCurrent()
        observer.cancelAndJoin()
        adapter.response = { listOf(price(12.0)) }
        observer = backgroundScope.launch { repo.observeTickers(setOf(key)).collect() }
        runCurrent()
        advanceTimeBy(119_999); runCurrent()
        assertEquals(1, adapter.calls)
        assertEquals(ExchangeFailureKind.RATE_LIMITED, repo.currentState(key)!!.failure)
        advanceTimeBy(1); runCurrent()
        assertEquals(2, adapter.calls)
        assertEquals(PriceFreshness.CURRENT, repo.currentState(key)!!.freshness)
        observer.cancelAndJoin()
    }

    @Test fun `successful snapshot suppresses repeat at cooldown`() = runTest {
        val adapter = Adapter().apply { response = { listOf(price(10.0)) } }
        val repo = repo(Dao(), adapter)
        var observer = backgroundScope.launch { repo.observeTickers(setOf(key)).collect() }
        runCurrent()
        observer.cancelAndJoin()
        observer = backgroundScope.launch { repo.observeTickers(setOf(key)).collect() }
        runCurrent()
        advanceTimeBy(60_001); runCurrent()
        assertEquals(1, adapter.calls)
        assertEquals(PriceFreshness.CURRENT, repo.currentState(key)!!.freshness)
        observer.cancelAndJoin()
    }

    @Test fun `stream confirmation during cancelled request cooldown avoids redundant REST`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val adapter = Adapter().apply { response = { gate.await(); listOf(price(10.0)) } }
        val repo = repo(Dao(), adapter)
        var observer = backgroundScope.launch { repo.observeTickers(setOf(key)).collect() }
        runCurrent()
        observer.cancelAndJoin()
        observer = backgroundScope.launch { repo.observeTickers(setOf(key)).collect() }
        runCurrent()
        advanceTimeBy(301); runCurrent()
        adapter.stream.emit(price(15.0)); runCurrent()
        advanceTimeBy(59_699); runCurrent()
        assertEquals(1, adapter.calls)
        assertEquals(15.0, repo.currentState(key)!!.ticker!!.last, 0.0)
        assertEquals(PriceFreshness.CURRENT, repo.currentState(key)!!.freshness)
        observer.cancelAndJoin()
    }

    @Test fun `overlapping observers share in flight request and one cancellation keeps it alive`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val adapter = Adapter().apply { response = { gate.await(); listOf(price(10.0)) } }
        val repo = repo(Dao(), adapter)
        val first = backgroundScope.launch { repo.observeTickers(setOf(key)).collect() }
        val second = backgroundScope.launch { repo.refreshResult(listOf(key)) }
        runCurrent()
        assertEquals(1, adapter.calls)
        first.cancelAndJoin()
        gate.complete(Unit)
        second.join()
        assertEquals(1, adapter.calls)
        assertEquals(PriceFreshness.CURRENT, repo.currentState(key)!!.freshness)
    }

    @Test fun `partial batch retries failed pair without re-fetching successful pair`() = runTest {
        val adapter = Adapter().apply {
            batchResponse = { markets ->
                if (calls == 1) TickerBatch(mapOf(key to price(10.0)), mapOf(other to IllegalStateException("temporary")))
                else TickerBatch(markets.associate { it.key to Ticker(it.key, 20.0, timestamp = 100) }, emptyMap())
            }
        }
        val repo = repo(Dao(), adapter)
        val observer = backgroundScope.launch { repo.observeTickers(setOf(key, other)).collect() }
        runCurrent()
        assertEquals(1, adapter.calls)
        assertEquals(PriceFreshness.CURRENT, repo.currentState(key)!!.freshness)
        assertEquals(ExchangeFailureKind.TRANSIENT, repo.currentState(other)!!.failure)
        advanceTimeBy(6_001); runCurrent()
        assertEquals(2, adapter.calls)
        assertEquals(10.0, repo.currentState(key)!!.ticker!!.last, 0.0)
        assertEquals(20.0, repo.currentState(other)!!.ticker!!.last, 0.0)
        observer.cancelAndJoin()
    }
}
