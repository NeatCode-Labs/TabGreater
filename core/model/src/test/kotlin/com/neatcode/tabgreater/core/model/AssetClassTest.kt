package com.neatcode.tabgreater.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class AssetClassTest {

    @Test
    fun `fromId reads the stable ids`() {
        assertEquals(AssetClass.CRYPTO, AssetClass.fromId("crypto"))
        assertEquals(AssetClass.STOCK, AssetClass.fromId("stock"))
    }

    @Test
    fun `fromId falls back to crypto for unknown or missing ids`() {
        assertEquals(AssetClass.CRYPTO, AssetClass.fromId(null))
        assertEquals(AssetClass.CRYPTO, AssetClass.fromId(""))
        assertEquals(AssetClass.CRYPTO, AssetClass.fromId("etf"))
        // Ids are lower case; the enum names are not ids.
        assertEquals(AssetClass.CRYPTO, AssetClass.fromId("STOCK"))
    }

    @Test
    fun `a market is crypto without an underlying unless an adapter says otherwise`() {
        val market = Market(MarketKey.of(ExchangeId.BINANCE, "BTC", "EUR"), "BTCEUR", pricePrecision = 2)

        assertEquals(AssetClass.CRYPTO, market.assetClass)
        assertNull(market.underlying)
    }

    @Test
    fun `market json written before asset classes existed still decodes, and defaults stay out of new json`() {
        val json = Json { ignoreUnknownKeys = true }
        val old = """{"key":"gate:BTC/USDT","nativeSymbol":"BTC_USDT","pricePrecision":1,"tickSize":0.1}"""

        val market = json.decodeFromString<Market>(old)

        assertEquals(AssetClass.CRYPTO, market.assetClass)
        assertNull(market.underlying)
        val encoded = json.encodeToString(market)
        assertFalse(encoded, encoded.contains("assetClass") || encoded.contains("underlying"))

        val stock = market.copy(
            key = MarketKey.of(ExchangeId.GATE, "TSLAX", "USDT"),
            assetClass = AssetClass.STOCK,
            underlying = "TSLA",
        )
        assertEquals(stock, json.decodeFromString<Market>(json.encodeToString(stock)))
    }
}
