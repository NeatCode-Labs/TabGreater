package com.neatcode.tabgreater.core.data.repo

import com.neatcode.tabgreater.core.exchange.ExchangeRegistry
import com.neatcode.tabgreater.core.model.ExchangeId
import com.neatcode.tabgreater.core.model.MarketKey
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class RoomMarketRepositoryTest {

    private val seed = listOf(
        marketEntity("binance:BTC/EUR"),
        marketEntity("binance:BTC/USDT"),
        marketEntity("binance:ETH/EUR"),
        marketEntity("binance:EURI/USDT"),
        marketEntity("kraken:BTC/EUR"),
        marketEntity("mexc:BTC/USDT"),
    )

    private fun repository(
        dao: FakeMarketDao = FakeMarketDao(seed),
        vararg adapters: FakeExchangeAdapter,
        now: () -> Long = { 1_000_000L },
    ) = RoomMarketRepository(dao, ExchangeRegistry(adapters.toList()), now)

    private fun binance(vararg extra: FakeExchangeAdapter) =
        arrayOf(FakeExchangeAdapter(ExchangeId.BINANCE), *extra)

    @Test
    fun `search matches a base prefix`() = runTest {
        val results = repository(adapters = binance(FakeExchangeAdapter(ExchangeId.KRAKEN))).search("bt")
        assertEquals(
            listOf("binance:BTC/EUR", "binance:BTC/USDT", "kraken:BTC/EUR"),
            results.map { it.key.value },
        )
    }

    @Test
    fun `search matches a quote prefix`() = runTest {
        val results = repository(adapters = binance()).search("usdt")
        assertTrue(results.map { it.key.value }.containsAll(listOf("binance:BTC/USDT", "binance:EURI/USDT")))
    }

    @Test
    fun `search splits on a slash`() = runTest {
        val results = repository(adapters = binance(FakeExchangeAdapter(ExchangeId.KRAKEN))).search(" btc / eu ")
        assertEquals(listOf("binance:BTC/EUR", "kraken:BTC/EUR"), results.map { it.key.value })
    }

    @Test
    fun `a slash pins the base exactly but keeps the quote a prefix`() = runTest {
        val dao = FakeMarketDao(
            listOf(
                marketEntity("binance:ETH/USD"),
                marketEntity("binance:ETH/USDT"),
                marketEntity("binance:ETH/USDC"),
                marketEntity("binance:ETHFI/USD"),
                marketEntity("binance:ETHFI/USDT"),
            ),
        )
        val repository = repository(dao = dao, adapters = binance())
        assertEquals(
            listOf("binance:ETH/USD", "binance:ETH/USDC", "binance:ETH/USDT"),
            repository.search("eth/usd").map { it.key.value },
        )
        // Without the divider the base is still a prefix, so ETHFI shows up too.
        assertEquals(
            listOf("binance:ETH/USD", "binance:ETH/USDC", "binance:ETH/USDT", "binance:ETHFI/USD", "binance:ETHFI/USDT"),
            repository.search("eth").map { it.key.value },
        )
        // A leading slash still means "any base".
        assertEquals(5, repository.search("/usd").size)
    }

    @Test
    fun `search understands the concatenated form`() = runTest {
        val results = repository(adapters = binance()).search("btceur")
        assertEquals(listOf("binance:BTC/EUR"), results.map { it.key.value })
    }

    @Test
    fun `search hides exchanges without an adapter`() = runTest {
        val results = repository(adapters = binance()).search("btc")
        assertTrue(results.all { it.key.exchange == ExchangeId.BINANCE })
        assertFalse(results.any { it.key.exchange == ExchangeId.MEXC })
    }

    @Test
    fun `search on a blank query returns nothing`() = runTest {
        assertEquals(emptyList<String>(), repository(adapters = binance()).search("  //  ").map { it.key.value })
    }

    @Test
    fun `search honours the limit`() = runTest {
        assertEquals(1, repository(adapters = binance()).search("bt", limit = 1).size)
    }

    @Test
    fun `getMarket ignores unsupported exchanges`() = runTest {
        val repo = repository(adapters = binance())
        assertNotNull(repo.getMarket(MarketKey("binance:BTC/EUR")))
        assertNull(repo.getMarket(MarketKey("mexc:BTC/USDT")))
    }

    @Test
    fun `getMarkets returns only known supported keys`() = runTest {
        val found = repository(adapters = binance()).getMarkets(
            listOf(MarketKey("binance:BTC/EUR"), MarketKey("mexc:BTC/USDT"), MarketKey("binance:DOGE/EUR")),
        )
        assertEquals(setOf(MarketKey("binance:BTC/EUR")), found.keys)
    }

    @Test
    fun `refreshMarkets is skipped while the cache is fresh`() = runTest {
        val dao = FakeMarketDao(seed.map { it.copy(updatedAt = 900_000L) })
        val adapter = FakeExchangeAdapter(ExchangeId.BINANCE, listOf(market("binance:BTC/EUR")))
        val repo = repository(dao, adapter, now = { 1_000_000L })

        assertTrue(repo.refreshMarkets(ExchangeId.BINANCE).isSuccess)
        assertEquals(0, adapter.listMarketsCalls)
    }

    @Test
    fun `refreshMarkets replaces delisted markets`() = runTest {
        val dao = FakeMarketDao(seed.map { it.copy(updatedAt = 900_000L) })
        val adapter = FakeExchangeAdapter(
            ExchangeId.BINANCE,
            listOf(market("binance:BTC/EUR"), market("binance:SOL/EUR")),
        )
        val repo = repository(dao, adapter, now = { 1_000_000L })

        assertTrue(repo.refreshMarkets(ExchangeId.BINANCE, force = true).isSuccess)
        assertEquals(1, adapter.listMarketsCalls)
        assertEquals(1, dao.deleteStaleCalls)

        val binanceKeys = dao.all.filter { it.exchange == "binance" }.map { it.marketKey }.sorted()
        assertEquals(listOf("binance:BTC/EUR", "binance:SOL/EUR"), binanceKeys)
        // Other exchanges are untouched.
        assertTrue(dao.all.any { it.marketKey == "kraken:BTC/EUR" })
    }

    @Test
    fun `refreshMarkets reports failures instead of throwing`() = runTest {
        val adapter = FakeExchangeAdapter(ExchangeId.BINANCE, failure = IOException("offline"))
        val result = repository(FakeMarketDao(), adapter).refreshMarkets(ExchangeId.BINANCE, force = true)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
    }

    @Test
    fun `refreshMarkets on an exchange without an adapter is a no-op success`() = runTest {
        val repo = repository(adapters = binance())
        assertTrue(repo.refreshMarkets(ExchangeId.MEXC, force = true).isSuccess)
    }

    @Test
    fun `refreshAll covers every supported exchange`() = runTest {
        val binanceAdapter = FakeExchangeAdapter(ExchangeId.BINANCE, listOf(market("binance:BTC/EUR")))
        val krakenAdapter = FakeExchangeAdapter(ExchangeId.KRAKEN, listOf(market("kraken:BTC/EUR")))
        repository(FakeMarketDao(), binanceAdapter, krakenAdapter).refreshAll(force = true)
        assertEquals(1, binanceAdapter.listMarketsCalls)
        assertEquals(1, krakenAdapter.listMarketsCalls)
    }
}
