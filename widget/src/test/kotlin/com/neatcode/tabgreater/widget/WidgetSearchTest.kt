package com.neatcode.tabgreater.widget

import com.neatcode.tabgreater.core.model.AssetClass
import com.neatcode.tabgreater.core.model.Market
import com.neatcode.tabgreater.core.model.MarketKey
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetSearchTest {

    /** Every (query, limit, class) the search was asked for, in order. */
    private val calls = mutableListOf<Triple<String, Int, AssetClass>>()

    @Test
    fun `a scope with rows lists them and probes nothing else`() = runTest {
        val results = WidgetSearch.run("USDT", AssetClass.CRYPTO, LIMIT, ::search)

        assertEquals(AssetClass.CRYPTO, results.scope)
        assertEquals(listOf("binance:BTC/USDT"), results.markets.map { it.key.value })
        assertEquals(0, results.otherScopeMatches)
        assertEquals(listOf(Triple("USDT", LIMIT, AssetClass.CRYPTO)), calls)
    }

    @Test
    fun `an empty scope counts the other one with a capped probe`() = runTest {
        val results = WidgetSearch.run("TSLA", AssetClass.CRYPTO, LIMIT, ::search)

        assertTrue(results.markets.isEmpty())
        assertEquals(2, results.otherScopeMatches)
        assertEquals(
            listOf(
                Triple("TSLA", LIMIT, AssetClass.CRYPTO),
                Triple("TSLA", WidgetSearch.OTHER_SCOPE_MATCH_CAP + 1, AssetClass.STOCK),
            ),
            calls,
        )
    }

    @Test
    fun `the stock scope searches stock tokens only`() = runTest {
        val results = WidgetSearch.run("TSLA", AssetClass.STOCK, LIMIT, ::search)

        assertEquals(listOf("gate:TSLAX/USDT", "mexc:TSLAON/USDT"), results.markets.map { it.key.value })
        assertEquals(0, results.otherScopeMatches)
    }

    @Test
    fun `a blank query searches nothing`() = runTest {
        val results = WidgetSearch.run("  ", AssetClass.STOCK, LIMIT, ::search)

        assertEquals(ScopedResults(AssetClass.STOCK), results)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `the other scope of each scope is the remaining one`() {
        assertEquals(AssetClass.STOCK, WidgetSearch.other(AssetClass.CRYPTO))
        assertEquals(AssetClass.CRYPTO, WidgetSearch.other(AssetClass.STOCK))
    }

    @Test
    fun `counts above the cap print as the cap plus`() {
        assertEquals("1", WidgetSearch.countLabel(1))
        assertEquals("100", WidgetSearch.countLabel(WidgetSearch.OTHER_SCOPE_MATCH_CAP))
        assertEquals("100+", WidgetSearch.countLabel(WidgetSearch.OTHER_SCOPE_MATCH_CAP + 1))
    }

    /** `MarketRepository.search` over [CATALOGUE]: substring match, class filter, then the limit. */
    private fun search(query: String, limit: Int, assetClass: AssetClass): List<Market> {
        calls += Triple(query, limit, assetClass)
        return CATALOGUE
            .filter { it.key.value.contains(query, ignoreCase = true) && it.assetClass == assetClass }
            .take(limit)
    }

    private companion object {
        const val LIMIT = 120

        val CATALOGUE = listOf(
            market("binance:BTC/USDT"),
            market("gate:TSLAX/USDT", underlying = "TSLA"),
            market("mexc:TSLAON/USDT", underlying = "TSLA"),
        )

        fun market(key: String, underlying: String? = null) = Market(
            key = MarketKey(key),
            nativeSymbol = key.substringAfter(':'),
            pricePrecision = 2,
            assetClass = if (underlying != null) AssetClass.STOCK else AssetClass.CRYPTO,
            underlying = underlying,
        )
    }
}
