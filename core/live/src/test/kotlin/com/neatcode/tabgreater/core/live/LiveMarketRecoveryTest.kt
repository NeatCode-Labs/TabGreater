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
        var calls = 0
        override suspend fun listMarkets() = emptyList<Market>()
        override suspend fun fetchTickers(markets: List<Market>): List<Ticker> { calls++; return response(markets) }
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
}
