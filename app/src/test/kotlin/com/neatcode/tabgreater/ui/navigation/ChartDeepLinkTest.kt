package com.neatcode.tabgreater.ui.navigation

import com.neatcode.tabgreater.core.model.MarketKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChartDeepLinkTest {

    @Test
    fun `the widget's encoded link names its market`() {
        // Exactly what TickerWidget builds: Uri.encode() over the canonical key.
        assertEquals(
            MarketKey("binance:BTC/EUR"),
            chartDeepLinkKey("tabgreater://chart/binance%3ABTC%2FEUR"),
        )
    }

    @Test
    fun `lower-case escapes decode just as well`() {
        assertEquals(
            MarketKey("kraken:BTC/EUR"),
            chartDeepLinkKey("tabgreater://chart/kraken%3aBTC%2fEUR"),
        )
    }

    @Test
    fun `an unencoded key is a link too, as long as it is one segment`() {
        // Nothing sends this today, but a colon needs no escaping in a path segment.
        assertEquals(MarketKey("mexc:ETH/USDT"), chartDeepLinkKey("tabgreater://chart/mexc:ETH%2FUSDT"))
    }

    @Test
    fun `another scheme or host is not a chart link`() {
        assertNull(chartDeepLinkKey("https://chart/binance%3ABTC%2FEUR"))
        assertNull(chartDeepLinkKey("tabgreater://widget/binance%3ABTC%2FEUR"))
        assertNull(chartDeepLinkKey("tabgreater://charts/binance%3ABTC%2FEUR"))
    }

    @Test
    fun `no key and no link at all are ignored`() {
        assertNull(chartDeepLinkKey(null))
        assertNull(chartDeepLinkKey("tabgreater://chart"))
        assertNull(chartDeepLinkKey("tabgreater://chart/"))
    }

    @Test
    fun `a second path segment or a query is not the link this app sends`() {
        assertNull(chartDeepLinkKey("tabgreater://chart/binance/BTC%2FEUR"))
        assertNull(chartDeepLinkKey("tabgreater://chart/binance%3ABTC%2FEUR/extra"))
        assertNull(chartDeepLinkKey("tabgreater://chart/binance%3ABTC%2FEUR?open=now"))
        assertNull(chartDeepLinkKey("tabgreater://chart/binance%3ABTC%2FEUR#top"))
    }

    @Test
    fun `a key that does not parse yields null instead of a route`() {
        assertNull(chartDeepLinkKey("tabgreater://chart/nasdaq%3ABTC%2FEUR")) // unknown exchange
        assertNull(chartDeepLinkKey("tabgreater://chart/binance%3ABTC")) // no quote
        assertNull(chartDeepLinkKey("tabgreater://chart/%3ABTC%2FEUR")) // no exchange
        assertNull(chartDeepLinkKey("tabgreater://chart/binance%3ABTC%2F")) // empty quote
    }

    @Test
    fun `a truncated escape is not an error, just no market`() {
        assertNull(chartDeepLinkKey("tabgreater://chart/binance%3ABTC%2"))
    }
}
