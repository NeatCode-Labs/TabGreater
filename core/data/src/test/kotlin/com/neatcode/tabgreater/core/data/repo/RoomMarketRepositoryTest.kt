package com.neatcode.tabgreater.core.data.repo

import com.neatcode.tabgreater.core.exchange.ExchangeRegistry
import com.neatcode.tabgreater.core.model.AssetClass
import com.neatcode.tabgreater.core.model.ExchangeId
import com.neatcode.tabgreater.core.model.Market
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

    private fun exchanges(vararg ids: ExchangeId) = ids.map { FakeExchangeAdapter(it) }.toTypedArray()

    /** Coins and stock tokens side by side, as the adapters classify them. `T` is a coin as well as a share. */
    private val mixed = listOf(
        marketEntity("binance:TRX/USDT"),
        stockEntity("binance:TSLAB/USDT", underlying = "TSLA"),
        marketEntity("gate:TRX/USDT"),
        stockEntity("gate:TSLAX/USDT", underlying = "TSLA"),
        stockEntity("gate:TX/USDT", underlying = "T"),
        marketEntity("kucoin:T/USDT"),
        stockEntity("kucoin:TX/USDT", underlying = "T"),
    )

    private fun mixedRepository() =
        repository(FakeMarketDao(mixed), *exchanges(ExchangeId.BINANCE, ExchangeId.GATE, ExchangeId.KUCOIN))

    private fun List<Market>.keys(): List<String> = map { it.key.value }

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
    fun `a fresh cache written before the app was updated is refreshed anyway`() = runTest {
        val dao = FakeMarketDao(seed.map { it.copy(updatedAt = 900_000L) })
        val adapter = FakeExchangeAdapter(ExchangeId.BINANCE, listOf(market("binance:BTC/EUR")))
        val updated = RoomMarketRepository(
            dao, ExchangeRegistry(listOf(adapter)), now = { 1_000_000L }, catalogueValidSince = { 950_000L },
        )

        assertTrue(updated.refreshMarkets(ExchangeId.BINANCE).isSuccess)
        assertEquals(1, adapter.listMarketsCalls)
        // The rows now carry a stamp after the update, so the gate holds again.
        assertTrue(updated.refreshMarkets(ExchangeId.BINANCE).isSuccess)
        assertEquals(1, adapter.listMarketsCalls)
    }

    @Test
    fun `an install stamp from the future does not defeat the freshness gate`() = runTest {
        val dao = FakeMarketDao(seed.map { it.copy(updatedAt = 900_000L) })
        val adapter = FakeExchangeAdapter(ExchangeId.BINANCE, listOf(market("binance:BTC/EUR")))
        val skewed = RoomMarketRepository(
            dao, ExchangeRegistry(listOf(adapter)), now = { 1_000_000L }, catalogueValidSince = { 2_000_000L },
        )

        assertTrue(skewed.refreshMarkets(ExchangeId.BINANCE).isSuccess)
        assertEquals(0, adapter.listMarketsCalls)
    }

    @Test
    fun `stock search keeps the typed ticker's own tokens when candidates overflow the limit`() = runTest {
        // "U" is also a quote prefix, so every USDT row is a candidate and the limit cuts by exchange order.
        val crowd = (1..12).map { stockEntity("binance:S${it}B/USDT", underlying = "S$it") }
        val own = stockEntity("kucoin:UX/USDT", underlying = "U")
        val repo = repository(FakeMarketDao(crowd + own), *exchanges(ExchangeId.BINANCE, ExchangeId.KUCOIN))

        val results = repo.search("u", limit = 2, assetClass = AssetClass.STOCK).keys()
        assertEquals("kucoin:UX/USDT", results.first())
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

    @Test
    fun `search without a class returns every class in the catalogue order`() = runTest {
        val repo = mixedRepository()
        val everything = listOf(
            "binance:TRX/USDT", "binance:TSLAB/USDT", "gate:TRX/USDT", "gate:TSLAX/USDT", "gate:TX/USDT",
            "kucoin:T/USDT", "kucoin:TX/USDT",
        )
        assertEquals(everything, repo.search("t").keys())
        assertEquals(everything, repo.search("t", assetClass = null).keys())
        assertEquals(listOf("binance:TSLAB/USDT", "gate:TSLAX/USDT"), repo.search("tsla").keys())
    }

    @Test
    fun `a crypto search leaves the stock tokens out and keeps the order`() = runTest {
        val repo = mixedRepository()
        val crypto = repo.search("t", assetClass = AssetClass.CRYPTO)
        assertEquals(listOf("binance:TRX/USDT", "gate:TRX/USDT", "kucoin:T/USDT"), crypto.keys())
        assertTrue(crypto.all { it.assetClass == AssetClass.CRYPTO && it.underlying == null })
        assertEquals(emptyList<String>(), repo.search("tsla", assetClass = AssetClass.CRYPTO).keys())
    }

    @Test
    fun `a stock search lists the typed underlying first`() = runTest {
        val repo = mixedRepository()
        // The tokens of share T come before TSLAB and TSLAX although binance sorts first.
        val stocks = repo.search("t", assetClass = AssetClass.STOCK)
        assertEquals(listOf("gate:TX/USDT", "kucoin:TX/USDT", "binance:TSLAB/USDT", "gate:TSLAX/USDT"), stocks.keys())
        assertEquals(listOf("T", "T", "TSLA", "TSLA"), stocks.map { it.underlying })
        assertTrue(stocks.all { it.assetClass == AssetClass.STOCK })
        assertEquals(
            listOf("binance:TSLAB/USDT", "gate:TSLAX/USDT"),
            repo.search("tsla", assetClass = AssetClass.STOCK).keys(),
        )
    }

    @Test
    fun `slash and concatenated queries respect the class`() = runTest {
        val repo = mixedRepository()
        assertEquals(listOf("gate:TX/USDT", "kucoin:TX/USDT"), repo.search("tx/usdt", assetClass = AssetClass.STOCK).keys())
        assertEquals(emptyList<String>(), repo.search("tx/usdt", assetClass = AssetClass.CRYPTO).keys())
        assertEquals(listOf("kucoin:T/USDT"), repo.search("t/usdt", assetClass = AssetClass.CRYPTO).keys())
        // In Stocks the pinned base may name the share: T/USDT lists the tokens of share T.
        assertEquals(
            listOf("gate:TX/USDT", "kucoin:TX/USDT"),
            repo.search("t/usdt", assetClass = AssetClass.STOCK).keys(),
        )
        assertEquals(
            listOf("binance:TRX/USDT", "gate:TRX/USDT", "kucoin:T/USDT"),
            repo.search("/usdt", assetClass = AssetClass.CRYPTO).keys(),
        )

        assertEquals(listOf("gate:TSLAX/USDT"), repo.search("tslaxusdt", assetClass = AssetClass.STOCK).keys())
        assertEquals(emptyList<String>(), repo.search("tslaxusdt", assetClass = AssetClass.CRYPTO).keys())
        assertEquals(
            listOf("binance:TRX/USDT", "gate:TRX/USDT"),
            repo.search("trxusdt", assetClass = AssetClass.CRYPTO).keys(),
        )
        assertEquals(emptyList<String>(), repo.search("trxusdt", assetClass = AssetClass.STOCK).keys())
    }

    @Test
    fun `a slash query names the share in Stocks and changes nothing elsewhere`() = runTest {
        val repo = mixedRepository()
        assertEquals(
            listOf("binance:TSLAB/USDT", "gate:TSLAX/USDT"),
            repo.search("tsla/usdt", assetClass = AssetClass.STOCK).keys(),
        )
        assertEquals(
            listOf("binance:TSLAB/USDT", "gate:TSLAX/USDT"),
            repo.search("tsla/us", assetClass = AssetClass.STOCK).keys(),
        )
        assertEquals(emptyList<String>(), repo.search("tsla/eur", assetClass = AssetClass.STOCK).keys())
        // No token's base is TSLA, so every other class still finds nothing.
        assertEquals(emptyList<String>(), repo.search("tsla/usdt").keys())
        assertEquals(emptyList<String>(), repo.search("tsla/usdt", assetClass = AssetClass.CRYPTO).keys())
        assertEquals(listOf("kucoin:T/USDT"), repo.search("t/usdt").keys())
    }

    @Test
    fun `leveraged tokens follow the plain tokens in a stock search`() = runTest {
        val dao = FakeMarketDao(
            listOf(
                stockEntity("binance:TSLAB/USDT", underlying = "TSLA"),
                stockEntity("gate:TSLA3L/USDT", underlying = "TSLA"),
                stockEntity("gate:TSLA3S/USDT", underlying = "TSLA"),
                stockEntity("gate:TSLAX/USDT", underlying = "TSLA"),
                stockEntity("gate:TSM3L/USDT", underlying = "TSM"),
                stockEntity("gate:TSMX/USDT", underlying = "TSM"),
                stockEntity("kucoin:TSLAX/USDT", underlying = "TSLA"),
            ),
        )
        val repo = repository(dao, *exchanges(ExchangeId.BINANCE, ExchangeId.GATE, ExchangeId.KUCOIN))
        assertEquals(
            listOf(
                "binance:TSLAB/USDT", "gate:TSLAX/USDT", "kucoin:TSLAX/USDT", "gate:TSLA3L/USDT", "gate:TSLA3S/USDT",
            ),
            repo.search("tsla", assetClass = AssetClass.STOCK).keys(),
        )
        // Likewise when no share is typed exactly; every other scope keeps the catalogue order.
        assertEquals(
            listOf(
                "binance:TSLAB/USDT", "gate:TSLAX/USDT", "gate:TSMX/USDT", "kucoin:TSLAX/USDT", "gate:TSLA3L/USDT",
                "gate:TSLA3S/USDT", "gate:TSM3L/USDT",
            ),
            repo.search("ts", assetClass = AssetClass.STOCK).keys(),
        )
        assertEquals(
            listOf("binance:TSLAB/USDT", "gate:TSLA3L/USDT", "gate:TSLA3S/USDT", "gate:TSLAX/USDT", "kucoin:TSLAX/USDT"),
            repo.search("tsla").keys(),
        )
    }

    @Test
    fun `the class filter runs before the candidate limit`() = runTest {
        // limit = 1 asks the DAO for 4 candidates, and the first four rows are all stock tokens:
        // filtering after the limit would leave a crypto search with nothing.
        val dao = FakeMarketDao(
            listOf(
                stockEntity("binance:AAPLB/USDT", underlying = "AAPL"),
                stockEntity("binance:MSTRB/USDT", underlying = "MSTR"),
                stockEntity("binance:NVDAB/USDT", underlying = "NVDA"),
                stockEntity("binance:TSLAB/USDT", underlying = "TSLA"),
                stockEntity("gate:TSLAX/USDT", underlying = "TSLA"),
                marketEntity("kucoin:BTC/USDT"),
            ),
        )
        val repo = repository(dao, *exchanges(ExchangeId.BINANCE, ExchangeId.GATE, ExchangeId.KUCOIN))
        assertEquals(listOf("kucoin:BTC/USDT"), repo.search("usdt", limit = 1, assetClass = AssetClass.CRYPTO).keys())
        assertEquals(listOf("kucoin:BTC/USDT"), repo.search("/usdt", limit = 1, assetClass = AssetClass.CRYPTO).keys())
        assertEquals(listOf("binance:AAPLB/USDT"), repo.search("usdt", limit = 1, assetClass = AssetClass.STOCK).keys())
    }

    @Test
    fun `popularStockRoots ranks by exchanges, then markets, then name`() = runTest {
        val dao = FakeMarketDao(
            listOf(
                stockEntity("binance:TSLAB/USDT", underlying = "TSLA"),
                stockEntity("gate:TSLAX/USDT", underlying = "TSLA"),
                stockEntity("gate:TSLAG/USDT", underlying = "TSLA"),
                stockEntity("kucoin:TSLAX/USDT", underlying = "TSLA"),
                stockEntity("binance:AAPLB/USDT", underlying = "AAPL"),
                stockEntity("gate:AAPLX/USDT", underlying = "AAPL"),
                stockEntity("mexc:AAPLX/USDT", underlying = "AAPL"),
                stockEntity("gate:NVDAX/USDT", underlying = "NVDA"),
                stockEntity("mexc:NVDAX/USDT", underlying = "NVDA"),
                stockEntity("mexc:NVDAON/USDT", underlying = "NVDA"),
                stockEntity("gate:COINX/USDT", underlying = "COIN"),
                stockEntity("mexc:COINX/USDT", underlying = "COIN"),
                stockEntity("gate:AMDX/USDT", underlying = "AMD"),
                stockEntity("mexc:AMDX/USDT", underlying = "AMD"),
                marketEntity("binance:BTC/USDT"),
                marketEntity("gate:BTC/USDT"),
                marketEntity("kucoin:BTC/USDT"),
                marketEntity("mexc:BTC/USDT"),
            ),
        )
        val repo = repository(dao, *exchanges(ExchangeId.BINANCE, ExchangeId.GATE, ExchangeId.KUCOIN, ExchangeId.MEXC))
        // TSLA and AAPL are on 3 exchanges (4 and 3 markets); NVDA, AMD and COIN on 2 (3, 2 and 2).
        assertEquals(listOf("TSLA", "AAPL", "NVDA", "AMD", "COIN"), repo.popularStockRoots())
        assertEquals(listOf("TSLA", "AAPL"), repo.popularStockRoots(limit = 2))
        assertEquals(emptyList<String>(), repo.popularStockRoots(limit = 0))
    }

    @Test
    fun `popularStockRoots ignores inactive markets and exchanges without an adapter`() = runTest {
        val dao = FakeMarketDao(
            listOf(
                stockEntity("binance:AAPLB/USDT", underlying = "AAPL"),
                stockEntity("binance:HOODB/USDT", underlying = "HOOD", active = false),
                stockEntity("gate:HOODX/USDT", underlying = "HOOD", active = false),
                stockEntity("mexc:MSTRX/USDT", underlying = "MSTR"),
                stockEntity("mexc:MSTRON/USDT", underlying = "MSTR"),
            ),
        )
        assertEquals(listOf("AAPL"), repository(dao, *exchanges(ExchangeId.BINANCE, ExchangeId.GATE)).popularStockRoots())
    }

    @Test
    fun `popularStockRoots is empty while the catalogue holds no stock token`() = runTest {
        val coinsOnly = repository(adapters = binance(FakeExchangeAdapter(ExchangeId.KRAKEN)))
        assertEquals(emptyList<String>(), coinsOnly.popularStockRoots())
        assertEquals(emptyList<String>(), repository(FakeMarketDao()).popularStockRoots())
    }

    @Test
    fun `refreshMarkets stores the asset class and the underlying`() = runTest {
        val token = market("binance:TSLAB/USDT", underlying = "TSLA")
        val coin = market("binance:BTC/USDT")
        val dao = FakeMarketDao()
        val repo = repository(dao, FakeExchangeAdapter(ExchangeId.BINANCE, listOf(token, coin)))

        assertTrue(repo.refreshMarkets(ExchangeId.BINANCE, force = true).isSuccess)
        val stored = dao.all.associateBy { it.marketKey }
        assertEquals("stock" to "TSLA", stored.getValue("binance:TSLAB/USDT").let { it.assetClass to it.underlying })
        assertEquals("crypto" to null, stored.getValue("binance:BTC/USDT").let { it.assetClass to it.underlying })

        assertEquals(token, repo.getMarket(token.key))
        assertEquals(coin, repo.getMarket(coin.key))
        assertEquals(mapOf(token.key to token, coin.key to coin), repo.getMarkets(listOf(token.key, coin.key)))
        assertEquals(listOf(token), repo.search("tsla", assetClass = AssetClass.STOCK))
    }

    @Test
    fun `a catalogue stamped by the 2 to 3 migration is refreshed without force`() = runTest {
        // MIGRATION_2_3 leaves every row unclassified and stamped 0, so the freshness gate opens.
        val now = 1_790_000_000_000L
        val migrated = FakeMarketDao(listOf(marketEntity("binance:TSLAB/USDT", updatedAt = 0L)))
        val adapter = FakeExchangeAdapter(ExchangeId.BINANCE, listOf(market("binance:TSLAB/USDT", underlying = "TSLA")))

        assertTrue(repository(migrated, adapter, now = { now }).refreshMarkets(ExchangeId.BINANCE).isSuccess)
        assertEquals(1, adapter.listMarketsCalls)
        assertEquals(AssetClass.STOCK.id, migrated.all.single().assetClass)
        assertEquals("TSLA", migrated.all.single().underlying)

        // The same catalogue refreshed an hour earlier is still skipped.
        val fresh = FakeMarketDao(listOf(marketEntity("binance:TSLAB/USDT", updatedAt = now - 3_600_000L)))
        val idle = FakeExchangeAdapter(ExchangeId.BINANCE, listOf(market("binance:TSLAB/USDT", underlying = "TSLA")))
        assertTrue(repository(fresh, idle, now = { now }).refreshMarkets(ExchangeId.BINANCE).isSuccess)
        assertEquals(0, idle.listMarketsCalls)
    }

    @Test
    fun `an unknown stored asset class reads as crypto`() = runTest {
        val dao = FakeMarketDao(listOf(marketEntity("binance:BTC/EUR").copy(assetClass = "bond")))
        assertEquals(AssetClass.CRYPTO, repository(dao, *binance()).getMarket(MarketKey("binance:BTC/EUR"))?.assetClass)
    }
}
