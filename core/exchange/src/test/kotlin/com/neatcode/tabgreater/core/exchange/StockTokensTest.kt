package com.neatcode.tabgreater.core.exchange

import com.neatcode.tabgreater.core.model.AssetClass
import com.neatcode.tabgreater.core.model.ExchangeId
import com.neatcode.tabgreater.core.model.Market
import com.neatcode.tabgreater.core.model.MarketKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class StockTokensTest {

    @Test
    fun `strip removes a present suffix and never leaves an empty ticker`() {
        assertEquals("TSLA", StockTokens.strip("TSLAX", "X"))
        assertEquals("AAPL", StockTokens.strip("AAPLON", "ON"))
        assertEquals("SPCX", StockTokens.strip("SPCX", "ON"))
        assertEquals("X", StockTokens.strip("X", "X"))
        assertEquals("ON", StockTokens.strip("ON", "ON"))
    }

    @Test
    fun `binance needs the stock group and the B suffix together`() {
        assertEquals("TSLA", StockTokens.binance("TSLAB", inStockGroup = true))
        assertEquals("MU", StockTokens.binance("MUB", inStockGroup = true))
        // The suffix alone is no marker: these are coins.
        assertNull(StockTokens.binance("BNB", inStockGroup = false))
        assertNull(StockTokens.binance("BOB", inStockGroup = false))
        // A group member without the suffix, or nothing left after it, stays crypto as well.
        assertNull(StockTokens.binance("TSLA", inStockGroup = true))
        assertNull(StockTokens.binance("B", inStockGroup = true))
    }

    @Test
    fun `gate reads the category tags and strips the issuer suffix`() {
        assertEquals("TSLA", StockTokens.gate("TSLAX", listOf("stocks", "xstocks")))
        assertEquals("AAPL", StockTokens.gate("AAPLON", listOf("stocks", "ondo-stocks")))
        assertEquals("SLV", StockTokens.gate("SLVON", listOf("metals", "ondo-stocks", "stocks")))
        assertEquals("AAPL", StockTokens.gate("AAPLG", listOf("gstocks", "stocks")))
        // Pre-IPO shares carry only "stocks" and are their own underlying.
        assertEquals("SPCX", StockTokens.gate("SPCX", listOf("stocks")))
        assertEquals("OPENAI", StockTokens.gate("OPENAI", listOf("stocks")))
    }

    @Test
    fun `gate keeps coins crypto, the ONDO coin included`() {
        // ONDO carries the Ondo tag but not "stocks".
        assertNull(StockTokens.gate("ONDO", listOf("ondo-stocks")))
        assertNull(StockTokens.gate("BTC", emptyList()))
        assertNull(StockTokens.gate("AVAX", emptyList()))
        assertNull(StockTokens.gate("NEON", emptyList()))
        assertNull(StockTokens.gate("DOG", emptyList()))
        assertNull(StockTokens.gate("XAU", listOf("metals")))
    }

    @Test
    fun `gate by name only knows xStocks and Ondo tokens`() {
        assertEquals("TSLA", StockTokens.gateByName("TSLAX", "Tesla xStock"))
        assertEquals("AAPL", StockTokens.gateByName("AAPLON", "Apple Ondo Tokenized"))
        assertNull(StockTokens.gateByName("AAPLG", "Apple"))
        assertNull(StockTokens.gateByName("ONDO", "Ondo Finance"))
        assertNull(StockTokens.gateByName("AVAX", "Avalanche"))
    }

    @Test
    fun `gate leveraged tokens follow their root`() {
        val roots = setOf("TSLA", "MU", "SPCX")

        assertEquals("TSLA", StockTokens.gateLeveraged("TSLA3L", roots))
        assertEquals("TSLA", StockTokens.gateLeveraged("TSLA3S", roots))
        assertEquals("MU", StockTokens.gateLeveraged("MU5L", roots))
        assertEquals("SPCX", StockTokens.gateLeveraged("SPCX5S", roots))
        assertNull(StockTokens.gateLeveraged("BTC3L", roots))
        assertNull(StockTokens.gateLeveraged("TSLA", roots))
        assertNull(StockTokens.gateLeveraged("TSLA2L", roots))
    }

    @Test
    fun `kucoin uses the Stocks market plus its stray xStocks`() {
        assertEquals("TSLA", StockTokens.kucoin("TSLAX", "Stocks"))
        assertEquals("SPCX", StockTokens.kucoin("SPCXX", "USDS"))
        assertNull(StockTokens.kucoin("AVAX", "USDS"))
        assertNull(StockTokens.kucoin("TRX", "USDS"))
        assertNull(StockTokens.kucoin("WMTX", "USDS"))
        assertNull(StockTokens.kucoin("BTC", null))
    }

    @Test
    fun `mexc takes the plate or the name and strips by issuer`() {
        val plates = listOf("Innovation", "Tokenized Stocks")
        assertEquals("TSLA", StockTokens.mexc("TSLAX", "Tesla xStock", XSTOCK_MINT, plates))
        assertEquals("AAPL", StockTokens.mexc("AAPLON", "Apple (Ondo)", ONDO_CONTRACT, plates))
        // SOXLON has no plate; the Ondo name gives it away.
        assertEquals("SOXL", StockTokens.mexc("SOXLON", "SOXLON(Ondo)", ONDO_CONTRACT, listOf("Innovation")))
        // An xStock mint decides the suffix even when the name does not say xStock.
        assertEquals("NVDA", StockTokens.mexc("NVDAX", "NVIDIA", XSTOCK_MINT, listOf("Tokenized Stocks")))
        // A plate without a known suffix keeps the base as it is.
        assertEquals("SPY", StockTokens.mexc("SPY", "SPDR S&P 500", ONDO_CONTRACT, listOf("Tokenized Stocks")))
    }

    @Test
    fun `mexc keeps meme and pre-IPO plates and suffix lookalikes crypto`() {
        assertNull(StockTokens.mexc("STONKS", "Stonks", "", listOf("Stock Meme Tokens")))
        assertNull(StockTokens.mexc("SPACEX", "SpaceX", "", listOf("Pre-IPO")))
        assertNull(StockTokens.mexc("NEON", "Neon EVM", "", listOf("Innovation")))
        assertNull(StockTokens.mexc("ELON", "Dogelon Mars", "", listOf("MEME")))
        assertNull(StockTokens.mexc("MAX", "Giggle Mascot", ONDO_CONTRACT, listOf("MEME")))
    }

    @Test
    fun `kraken strips only the lowercase x of its tokenized catalogue`() {
        assertEquals("AAPL", StockTokens.kraken("AAPLx"))
        assertEquals("SPCX", StockTokens.kraken("SPCXx"))
        // The upper-case X is part of the ticker, not Kraken's suffix.
        assertEquals("TONX", StockTokens.kraken("TONXx"))
        assertEquals("TONX", StockTokens.kraken("TONX"))
        assertEquals("x", StockTokens.kraken("x"))
    }

    @Test
    fun `classified tags stock rows and leaves crypto rows untouched`() {
        val crypto = market("BTC")
        assertSame(crypto, crypto.classified(null))
        assertEquals(AssetClass.CRYPTO, crypto.assetClass)

        val stock = market("TSLAX").classified("tsla")
        assertEquals(AssetClass.STOCK, stock.assetClass)
        assertEquals("TSLA", stock.underlying)
        assertEquals(MarketKey.of(ExchangeId.GATE, "TSLAX", "USDT"), stock.key)
    }

    private fun market(base: String) = Market(
        key = MarketKey.of(ExchangeId.GATE, base, "USDT"),
        nativeSymbol = "${base}_USDT",
        pricePrecision = 2,
    )

    private companion object {
        const val XSTOCK_MINT = "XsDoVfqeBukxuZHWhdvWHBhgEHjGNst4MLodqsJHzoB"
        const val ONDO_CONTRACT = "0x14c3abF95Cb9C93a8b82C1CdCB76D72Cb87b2d4c"
    }
}
