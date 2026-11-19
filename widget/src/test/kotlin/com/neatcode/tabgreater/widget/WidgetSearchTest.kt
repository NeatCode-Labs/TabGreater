package com.neatcode.tabgreater.widget

import com.neatcode.tabgreater.core.model.ExchangeId
import com.neatcode.tabgreater.core.model.InstrumentKind
import com.neatcode.tabgreater.core.model.Market
import com.neatcode.tabgreater.core.model.MarketKey
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetSearchTest {
    private data class Call(
        val query: String,
        val limit: Int,
        val offset: Int,
        val instrumentKind: InstrumentKind?,
        val source: ExchangeId?,
    )

    private val calls = mutableListOf<Call>()

    @Test
    fun `blank query and all filters request the first catalogue page`() = runTest {
        val results = WidgetSearch.page("  ", WidgetSearchFilters(), limit = 2, offset = 0, ::searchPage)

        assertEquals(listOf("binance:BTC/USDT", "hyperliquid:OUTCOME_37275_YES/USDC"),
            results.markets.map { it.key.value })
        assertEquals(2, results.nextOffset)
        assertEquals(Call("", 2, 0, null, null), calls.single())
    }

    @Test
    fun `empty cached page refreshes in place and leaves the next offset ready for scroll`() = runTest {
        val filters = WidgetSearchFilters()
        val cache = WidgetSearch.page("", filters, limit = 2, offset = 0) { query, limit, offset, kind, source ->
            calls += Call(query, limit, offset, kind, source)
            emptyList()
        }
        assertEquals(emptyList<Market>(), cache.markets)

        val refreshed = WidgetSearch.refreshVisible(
            text = "",
            filters = filters,
            visibleCount = cache.markets.size,
            firstPageSize = 2,
        ) { query, limit, offset, kind, source ->
            calls += Call(query, limit, offset, kind, source)
            CATALOGUE.drop(offset).take(limit)
        }
        val next = WidgetSearch.page("", filters, limit = 2, offset = refreshed.results.nextOffset, ::searchPage)

        assertEquals(listOf("binance:BTC/USDT", "hyperliquid:OUTCOME_37275_YES/USDC"),
            refreshed.results.markets.map { it.key.value })
        assertEquals(2, refreshed.results.nextOffset)
        assertEquals(true, refreshed.hasMore)
        assertEquals("hyperliquid:OUTCOME_37275_NO/USDC", next.markets.first().key.value)
        assertEquals(listOf(0, 0, 2), calls.map { it.offset })
    }

    @Test
    fun `successive offsets append distinct rows without a fixed catalogue cap`() = runTest {
        val filters = WidgetSearchFilters()
        val first = WidgetSearch.page("", filters, limit = 2, offset = 0, ::searchPage)
        val second = WidgetSearch.page("", filters, limit = 2, offset = first.nextOffset, ::searchPage)

        assertEquals(listOf("binance:BTC/USDT", "hyperliquid:OUTCOME_37275_YES/USDC"),
            first.markets.map { it.key.value })
        assertEquals(listOf("hyperliquid:OUTCOME_37275_NO/USDC", "hyperliquid:TSLA/USD"),
            second.markets.map { it.key.value })
        assertEquals(listOf(0, 2), calls.map { it.offset })
    }

    @Test
    fun `instrument type and source filters are passed together`() = runTest {
        val filters = WidgetSearchFilters(
            instrumentKind = InstrumentKind.PERPETUAL,
            source = ExchangeId.HYPERLIQUID,
        )

        val results = WidgetSearch.page(" TSLA ", filters, limit = 120, offset = 0, ::searchPage)

        assertEquals(listOf("hyperliquid:TSLA/USD"), results.markets.map { it.key.value })
        assertEquals(Call("TSLA", 120, 0, InstrumentKind.PERPETUAL, ExchangeId.HYPERLIQUID), calls.single())
    }

    @Test
    fun `outcome pagination preserves yes and no market identities`() = runTest {
        val filters = WidgetSearchFilters(instrumentKind = InstrumentKind.OUTCOME)

        val yes = WidgetSearch.page("", filters, limit = 1, offset = 0, ::searchPage)
        val no = WidgetSearch.page("", filters, limit = 1, offset = yes.nextOffset, ::searchPage)

        assertEquals("hyperliquid:OUTCOME_37275_YES/USDC", yes.markets.single().key.value)
        assertEquals("hyperliquid:OUTCOME_37275_NO/USDC", no.markets.single().key.value)
        assertEquals(listOf(0, 1), calls.map { it.offset })
    }

    @Test
    fun `failed continuation can retry from the same offset`() = runTest {
        val filters = WidgetSearchFilters()
        val first = WidgetSearch.page("", filters, limit = 2, offset = 0, ::searchPage)
        var failOnce = true
        val continuation: suspend (String, Int, Int, InstrumentKind?, ExchangeId?) -> List<Market> =
            { query, limit, offset, kind, source ->
                calls += Call(query, limit, offset, kind, source)
                if (offset == first.nextOffset && failOnce) {
                    failOnce = false
                    error("temporary read failure")
                }
                CATALOGUE.drop(offset).take(limit)
            }

        try {
            WidgetSearch.page("", filters, limit = 2, offset = first.nextOffset, continuation)
        } catch (_: IllegalStateException) {
            // A failed read must not advance the caller's offset.
        }
        val retry = WidgetSearch.page("", filters, limit = 2, offset = first.nextOffset, continuation)

        assertEquals(first.nextOffset, 2)
        assertEquals("hyperliquid:OUTCOME_37275_NO/USDC", retry.markets.first().key.value)
        assertEquals(listOf(0, 2, 2), calls.map { it.offset })
    }

    private fun searchPage(
        query: String,
        limit: Int,
        offset: Int,
        instrumentKind: InstrumentKind?,
        source: ExchangeId?,
    ): List<Market> {
        calls += Call(query, limit, offset, instrumentKind, source)
        return CATALOGUE
            .filter { market -> query.isBlank() || listOfNotNull(market.key.value, market.displayName, market.description)
                .any { it.contains(query, ignoreCase = true) } }
            .filter { instrumentKind == null || it.instrumentKind == instrumentKind }
            .filter { source == null || it.exchange == source }
            .drop(offset)
            .take(limit)
    }

    private companion object {
        val CATALOGUE = listOf(
            Market(
                key = MarketKey("binance:BTC/USDT"),
                nativeSymbol = "BTCUSDT",
                pricePrecision = 2,
            ),
            Market(
                key = MarketKey("hyperliquid:OUTCOME_37275_YES/USDC"),
                nativeSymbol = "#372750",
                pricePrecision = 3,
                instrumentKind = InstrumentKind.OUTCOME,
                displayName = "Will Team A win? · Yes",
                description = "Will Team A win?",
                outcomeSide = "Yes",
            ),
            Market(
                key = MarketKey("hyperliquid:OUTCOME_37275_NO/USDC"),
                nativeSymbol = "#372751",
                pricePrecision = 3,
                instrumentKind = InstrumentKind.OUTCOME,
                displayName = "Will Team A win? · No",
                description = "Will Team A win?",
                outcomeSide = "No",
            ),
            Market(
                key = MarketKey("hyperliquid:TSLA/USD"),
                nativeSymbol = "xyz:TSLA",
                pricePrecision = 2,
                instrumentKind = InstrumentKind.PERPETUAL,
                displayName = "TSLA",
            ),
        )
    }
}
