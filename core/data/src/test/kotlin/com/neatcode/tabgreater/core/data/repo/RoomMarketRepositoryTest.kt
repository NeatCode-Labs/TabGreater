package com.neatcode.tabgreater.core.data.repo

import com.neatcode.tabgreater.core.exchange.ExchangeRegistry
import com.neatcode.tabgreater.core.model.AssetClass
import com.neatcode.tabgreater.core.model.ExchangeId
import com.neatcode.tabgreater.core.model.InstrumentKind
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
import java.time.Instant

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

    private fun dailyOutcome(key: String, id: String, side: String, expiry: String): Market {
        val expiresAt = Instant.parse(expiry).toEpochMilli()
        val slot = Math.floorMod(expiresAt, 24L * 60 * 60 * 1_000)
        return Market(
            key = MarketKey(key),
            nativeSymbol = "#${id}0",
            pricePrecision = 4,
            instrumentKind = InstrumentKind.OUTCOME,
            outcomeId = id,
            outcomeSide = side,
            expiresAt = expiresAt,
            rolloverSeriesId = "priceBinary:BTC:1d:$slot",
        )
    }

    private fun List<Market>.keys(): List<String> = map { it.key.value }

    @Test
    fun `search matches a base prefix`() = runTest {
        val results = repository(adapters = binance(FakeExchangeAdapter(ExchangeId.KRAKEN))).searchPage("bt")
        assertEquals(
            listOf("binance:BTC/EUR", "binance:BTC/USDT", "kraken:BTC/EUR"),
            results.map { it.key.value },
        )
    }

    @Test
    fun `daily outcome rollover accepts a unique same slot contract after several missed days`() = runTest {
        val old = dailyOutcome("hyperliquid:OUTCOME_95_SIDE_0/USDC", "95", "Yes", "2026-11-20T06:00:00Z")
        val next = dailyOutcome("hyperliquid:OUTCOME_105_SIDE_0/USDC", "105", "Yes", "2026-11-24T06:00:00Z")
        val otherSlot = dailyOutcome("hyperliquid:OUTCOME_106_SIDE_0/USDC", "106", "Yes", "2026-11-23T12:00:00Z")
            .copy(rolloverSeriesId = "priceBinary:BTC:1d:43200000")
        val dao = FakeMarketDao(listOf(old, next, otherSlot).map { it.toEntity(1L) })
        val repository = repository(dao, *exchanges(ExchangeId.HYPERLIQUID))

        val confirmed = repository.nextDailyOutcomeMarket(
            old,
            Instant.parse("2026-11-23T07:00:00Z").toEpochMilli(),
        )

        assertEquals(next.key, confirmed?.key)
        assertEquals(next.expiresAt, confirmed?.expiresAt)
    }

    @Test
    fun `daily outcome rollover refuses same deadline ambiguity`() = runTest {
        val old = dailyOutcome("hyperliquid:OUTCOME_95_SIDE_0/USDC", "95", "Yes", "2026-11-20T06:00:00Z")
        val first = dailyOutcome("hyperliquid:OUTCOME_105_SIDE_0/USDC", "105", "Yes", "2026-11-24T06:00:00Z")
        val second = dailyOutcome("hyperliquid:OUTCOME_107_SIDE_0/USDC", "107", "Yes", "2026-11-24T06:00:00Z")
        val repository = repository(
            FakeMarketDao(listOf(old, first, second).map { it.toEntity(1L) }),
            *exchanges(ExchangeId.HYPERLIQUID),
        )

        assertNull(repository.nextDailyOutcomeMarket(old, Instant.parse("2026-11-23T07:00:00Z").toEpochMilli()))
    }

    @Test
    fun `legacy daily outcome description recovers family and deadline after its listing disappears`() {
        val expiry = Instant.parse("2026-11-20T06:00:00Z").toEpochMilli()
        val legacy = marketEntity("hyperliquid:OUTCOME_95_SIDE_0/USDC", active = false).copy(
            instrumentKind = InstrumentKind.OUTCOME.id,
            outcomeId = "95",
            outcomeSide = "Yes",
            description = "class:priceBinary|underlying:BTC|expiry:20261120-0600|targetPrice:63330|period:1d",
            expiresAt = null,
            rolloverSeriesId = null,
        )

        val restored = legacy.toModelOrNull()

        assertEquals("priceBinary:BTC:1d:21600000", restored?.rolloverSeriesId)
        assertEquals(expiry, restored?.expiresAt)
    }

    @Test
    fun `legacy fallback accepts only an exact Hyperliquid outcome class field`() {
        val descriptor = "class:priceBinary|underlying:BTC|expiry:20261120-0600|targetPrice:63330|period:1d"
        val legacyOutcome = marketEntity("hyperliquid:OUTCOME_95_SIDE_0/USDC").copy(
            instrumentKind = InstrumentKind.OUTCOME.id,
            outcomeId = "95",
            outcomeSide = "Yes",
        )

        val similarlyNamedClass = legacyOutcome.copy(description = descriptor.replace("class:priceBinary|", "class:priceBinaryOther|"))
        val proseMention = legacyOutcome.copy(description = "Official listing mentions $descriptor")
        val spot = legacyOutcome.copy(
            exchange = ExchangeId.HYPERLIQUID.id,
            instrumentKind = InstrumentKind.SPOT.id,
            description = descriptor,
        )

        assertNull(similarlyNamedClass.toModelOrNull()?.rolloverSeriesId)
        assertNull(similarlyNamedClass.toModelOrNull()?.expiresAt)
        assertNull(proseMention.toModelOrNull()?.rolloverSeriesId)
        assertNull(spot.toModelOrNull()?.rolloverSeriesId)
        assertNull(spot.toModelOrNull()?.expiresAt)
    }

    @Test
    fun `search matches a quote prefix`() = runTest {
        val results = repository(adapters = binance()).searchPage("usdt")
        assertTrue(results.map { it.key.value }.containsAll(listOf("binance:BTC/USDT", "binance:EURI/USDT")))
    }

    @Test
    fun `search splits on a slash`() = runTest {
        val results = repository(adapters = binance(FakeExchangeAdapter(ExchangeId.KRAKEN))).searchPage(" btc / eu ")
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
            repository.searchPage("eth/usd").map { it.key.value },
        )
        // Without the divider the base is still a prefix, so ETHFI shows up too.
        assertEquals(
            listOf("binance:ETH/USD", "binance:ETH/USDC", "binance:ETH/USDT", "binance:ETHFI/USD", "binance:ETHFI/USDT"),
            repository.searchPage("eth").map { it.key.value },
        )
        // A leading slash still means "any base".
        assertEquals(5, repository.searchPage("/usd").size)
    }

    @Test
    fun `search understands the concatenated form`() = runTest {
        val results = repository(adapters = binance()).searchPage("btceur")
        assertEquals(listOf("binance:BTC/EUR"), results.map { it.key.value })
    }

    @Test
    fun `search hides exchanges without an adapter`() = runTest {
        val results = repository(adapters = binance()).searchPage("btc")
        assertTrue(results.all { it.key.exchange == ExchangeId.BINANCE })
        assertFalse(results.any { it.key.exchange == ExchangeId.MEXC })
    }

    @Test
    fun `blank query browses the active catalogue`() = runTest {
        assertTrue(repository(adapters = binance()).searchPage("").isNotEmpty())
    }

    @Test
    fun `blank query with instrument and source filters browses active markets`() = runTest {
        val event = marketEntity("hyperliquid:OUTCOME_123/YES").copy(
            instrumentKind = InstrumentKind.OUTCOME.id,
            displayName = "Bitcoin above \$100,000 by Dec 31?",
            description = "Will Bitcoin trade above \$100,000 by 2026-12-31?",
            unit = "USD per BTC",
            venue = "HIP-4",
            eventId = "question-123",
            outcomeId = "yes-123",
            outcomeSide = "Yes",
            expiresAt = 1_800_000_000_000L,
        )
        val ordinary = marketEntity("hyperliquid:BTC/USDC")
        val repo = repository(
            FakeMarketDao(listOf(event, ordinary)),
            *exchanges(ExchangeId.HYPERLIQUID),
        )

        assertEquals(listOf("hyperliquid:OUTCOME_123/YES"), repo.searchPage("", instrumentKind = InstrumentKind.OUTCOME).keys())
        assertEquals(
            listOf("hyperliquid:OUTCOME_123/YES"),
            repo.searchPage("", instrumentKind = InstrumentKind.OUTCOME, exchange = ExchangeId.HYPERLIQUID).keys(),
        )
        assertEquals(listOf("hyperliquid:OUTCOME_123/YES"), repo.searchPage("bitcoin", instrumentKind = InstrumentKind.OUTCOME).keys())
        assertEquals(listOf("hyperliquid:OUTCOME_123/YES"), repo.searchPage("\$100,000", instrumentKind = InstrumentKind.OUTCOME).keys())
        assertEquals(emptyList<String>(), repo.searchPage("", instrumentKind = InstrumentKind.OUTCOME, exchange = ExchangeId.BINANCE).keys())
    }

    @Test
    fun `slash pair searches keep exact bases while matching display and native aliases`() = runTest {
        val syntheticSpot = marketEntity("hyperliquid:SPOT_107/USDC").copy(
            nativeSymbol = "@107",
            displayName = "HYPE/USDC",
        )
        val similarBase = marketEntity("hyperliquid:KHYPE/USDC").copy(displayName = "KHYPE/USDC")
        val namespacedPerpetual = marketEntity("hyperliquid:HIP3_XYZ_ANT/USD").copy(
            nativeSymbol = "xyz:ANT",
            displayName = "xyz:ANT/USD",
            assetClass = AssetClass.STOCK.id,
            underlying = "ANT",
            instrumentKind = InstrumentKind.PERPETUAL.id,
        )
        val seasonEvent = marketEntity("hyperliquid:EVENT_2026_27/YES").copy(
            displayName = "2026/2027 NBA winner · Yes",
            description = "Basketball season winner · 勝者 · 5%",
            instrumentKind = InstrumentKind.OUTCOME.id,
            eventId = "nba-2026-27",
            outcomeSide = "Yes",
        )
        val repo = repository(
            FakeMarketDao(listOf(syntheticSpot, similarBase, namespacedPerpetual, seasonEvent)),
            *exchanges(ExchangeId.HYPERLIQUID),
        )

        assertEquals(listOf("hyperliquid:SPOT_107/USDC"), repo.searchPage("HYPE/USDC", exchange = ExchangeId.HYPERLIQUID).keys())
        assertEquals(listOf("hyperliquid:SPOT_107/USDC"), repo.searchPage("HYPE/US", exchange = ExchangeId.HYPERLIQUID).keys())
        assertEquals(listOf("hyperliquid:SPOT_107/USDC"), repo.searchPage("@107", exchange = ExchangeId.HYPERLIQUID).keys())
        assertEquals(listOf("hyperliquid:HIP3_XYZ_ANT/USD"), repo.searchPage("xyz:ANT/USD", exchange = ExchangeId.HYPERLIQUID).keys())
        assertEquals(listOf("hyperliquid:HIP3_XYZ_ANT/USD"), repo.searchPage("ANT/USD", exchange = ExchangeId.HYPERLIQUID).keys())
        assertEquals(listOf("hyperliquid:EVENT_2026_27/YES"), repo.searchPage("2026/2027 NBA", instrumentKind = InstrumentKind.OUTCOME).keys())
        assertEquals(listOf("hyperliquid:EVENT_2026_27/YES"), repo.searchPage("basketball", instrumentKind = InstrumentKind.OUTCOME).keys())
        assertEquals(listOf("hyperliquid:EVENT_2026_27/YES"), repo.searchPage("勝者").keys())
        assertEquals(listOf("hyperliquid:EVENT_2026_27/YES"), repo.searchPage("%").keys())
        assertEquals(listOf("hyperliquid:SPOT_107/USDC"), repo.searchPage("@").keys())
        assertEquals(listOf("hyperliquid:SPOT_107/USDC"), repo.searchPage("SPOT_107/USDC").keys())
        assertTrue(repo.searchPage("勝者/USDC").isEmpty())
    }

    @Test
    fun `search honours the limit`() = runTest {
        assertEquals(1, repository(adapters = binance()).searchPage("bt", limit = 1).size)
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
    fun `Hyperliquid refresh uses a ten minute catalogue lifetime`() = runTest {
        val now = 10_000_000L
        val freshDao = FakeMarketDao(listOf(marketEntity("hyperliquid:BTC/USDC", updatedAt = now - 9 * 60 * 1000L)))
        val freshAdapter = FakeExchangeAdapter(ExchangeId.HYPERLIQUID, listOf(market("hyperliquid:BTC/USDC")))
        assertTrue(repository(freshDao, freshAdapter, now = { now }).refreshMarkets(ExchangeId.HYPERLIQUID).isSuccess)
        assertEquals(0, freshAdapter.listMarketsCalls)

        val staleDao = FakeMarketDao(listOf(marketEntity("hyperliquid:BTC/USDC", updatedAt = now - 11 * 60 * 1000L)))
        val staleAdapter = FakeExchangeAdapter(ExchangeId.HYPERLIQUID, listOf(market("hyperliquid:BTC/USDC")))
        assertTrue(repository(staleDao, staleAdapter, now = { now }).refreshMarkets(ExchangeId.HYPERLIQUID).isSuccess)
        assertEquals(1, staleAdapter.listMarketsCalls)
    }

    @Test
    fun `Hyperliquid refresh archives missing listings and retains their watched labels`() = runTest {
        val now = 20_000_000L
        val oldEvent = marketEntity("hyperliquid:OUTCOME_123/YES", updatedAt = now).copy(
            instrumentKind = InstrumentKind.OUTCOME.id,
            displayName = "Bitcoin above \$100,000?",
            eventId = "question-123",
            outcomeSide = "Yes",
        )
        val dao = FakeMarketDao(listOf(oldEvent))
        val adapter = FakeExchangeAdapter(ExchangeId.HYPERLIQUID, listOf(market("hyperliquid:BTC/USDC")))
        val repo = repository(dao, adapter, now = { now })

        assertTrue(repo.refreshMarkets(ExchangeId.HYPERLIQUID, force = true).isSuccess)
        assertEquals(1, dao.deactivateStaleCalls)
        assertEquals(0, dao.deleteStaleCalls)
        val archived = repo.getMarket(MarketKey("hyperliquid:OUTCOME_123/YES"))
        assertEquals("Bitcoin above \$100,000?", archived?.displayName)
        assertEquals(false, archived?.active)
        assertEquals(emptyList<String>(), repo.searchPage("bitcoin", exchange = ExchangeId.HYPERLIQUID).keys())
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
    fun `refreshMarkets replaces delisted markets`() = runTest {
        val dao = FakeMarketDao(seed.map { it.copy(updatedAt = 900_000L) })
        val adapter = FakeExchangeAdapter(
            ExchangeId.BINANCE,
            listOf(market("binance:BTC/EUR"), market("binance:SOL/EUR")),
        )
        val repo = repository(dao, adapter, now = { 1_000_000L })

        assertTrue(repo.refreshMarkets(ExchangeId.BINANCE, force = true).isSuccess)
        assertEquals(1, adapter.listMarketsCalls)
        assertEquals(1, dao.deactivateStaleCalls)

        val binanceKeys = dao.all.filter { it.exchange == "binance" && it.active }.map { it.marketKey }.sorted()
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
    fun `an empty complete catalogue is a failure and preserves cached listings`() = runTest {
        val existing = marketEntity("hyperliquid:OUTCOME_95_SIDE_0/USDC", updatedAt = 1L)
        val dao = FakeMarketDao(listOf(existing))
        val adapter = FakeExchangeAdapter(ExchangeId.HYPERLIQUID, emptyList())
        val repo = repository(dao, adapter)

        val result = repo.refreshMarkets(ExchangeId.HYPERLIQUID, force = true)

        assertTrue(result.isFailure)
        assertEquals(0, dao.deactivateStaleCalls)
        assertEquals(true, repo.getMarket(MarketKey(existing.marketKey))?.active)
    }

    @Test
    fun `a partial venue response retains previously active markets for the next refresh`() = runTest {
        val dao = FakeMarketDao(seed)
        val adapter = FakeExchangeAdapter(ExchangeId.BINANCE, listOf(market("binance:NEW/EUR")), complete = false)
        val repo = repository(dao, adapter)
        assertTrue(repo.refreshMarkets(ExchangeId.BINANCE, force = true).isSuccess)
        assertEquals(0, dao.deactivateStaleCalls)
        assertEquals(true, repo.getMarket(MarketKey("binance:BTC/EUR"))?.active)
        assertEquals(true, repo.getMarket(MarketKey("binance:NEW/EUR"))?.active)
    }

    @Test
    fun `a partial Kraken snapshot keeps absent tokenized rows active`() = runTest {
        val tokenized = stockEntity("kraken:AAPLX/USD", underlying = "AAPL").copy(updatedAt = 900_000L)
        val dao = FakeMarketDao(seed + tokenized)
        val adapter = FakeExchangeAdapter(
            ExchangeId.KRAKEN,
            listOf(market("kraken:BTC/EUR")),
            complete = false,
        )
        val repo = repository(dao, adapter)

        assertTrue(repo.refreshMarkets(ExchangeId.KRAKEN, force = true).isSuccess)

        assertEquals(0, dao.deactivateStaleCalls)
        assertEquals(true, repo.getMarket(MarketKey("kraken:AAPLX/USD"))?.active)
        assertEquals(true, repo.getMarket(MarketKey("kraken:BTC/EUR"))?.active)
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
    fun `typed and unfiltered pages cover every matching market beyond 300 without overlap`() = runTest {
        val rows = (1..723).map { marketEntity("binance:COIN${it.toString().padStart(4, '0')}/USDT") }
        val repo = repository(FakeMarketDao(rows.reversed()), *binance())
        val expected = rows.map { it.marketKey }
        for (query in listOf("", "coin")) {
            val pages = (0..780 step 60).flatMap { offset ->
                repo.searchPage(query, limit = 60, offset = offset, instrumentKind = InstrumentKind.SPOT).keys()
            }
            assertEquals(expected, pages)
        }
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
        assertEquals(listOf(token), repo.searchPage("tsla"))
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
