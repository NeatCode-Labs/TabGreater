package com.neatcode.tabgreater.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class MarketKeyTest {

    @Test
    fun `of builds the canonical form and upper cases the pair`() {
        val key = MarketKey.of(ExchangeId.KRAKEN, "btc", "eur")
        assertEquals("kraken:BTC/EUR", key.value)
        assertEquals(ExchangeId.KRAKEN, key.exchange)
        assertEquals("BTC", key.base)
        assertEquals("EUR", key.quote)
        assertEquals("BTC/EUR", key.pair)
    }

    @Test
    fun `parseOrNull rejects malformed keys`() {
        assertNull(MarketKey.parseOrNull(""))
        assertNull(MarketKey.parseOrNull("binance"))
        assertNull(MarketKey.parseOrNull("binance:BTCEUR"))
        assertNull(MarketKey.parseOrNull("binance:/EUR"))
        assertNull(MarketKey.parseOrNull("binance:BTC/"))
        assertNull(MarketKey.parseOrNull(":BTC/EUR"))
        assertNotNull(MarketKey.parseOrNull("binance:BTC/EUR"))
    }

    /**
     * Keys of exchanges that were dropped from the build stay in the database and in exported
     * backups. They must parse to `null` so the rows are skipped instead of crashing a flow.
     */
    @Test
    fun `parseOrNull rejects exchanges that are no longer supported`() {
        assertNull(ExchangeId.fromIdOrNull("coinbase"))
        assertNull(MarketKey.parseOrNull("coinbase:BTC/USD"))
        assertNull(MarketKey.parseOrNull("bitstamp:BTC/EUR"))
        assertFalse(ExchangeId.entries.any { it.id == "coinbase" })
    }

    @Test
    fun `the constructor rejects what parseOrNull rejects`() {
        assertThrows(IllegalArgumentException::class.java) { MarketKey("coinbase:BTC/USD") }
        assertThrows(IllegalArgumentException::class.java) { MarketKey("binance:BTCEUR") }
    }

    @Test
    fun `fromId throws for an unknown exchange id`() {
        assertThrows(IllegalArgumentException::class.java) { ExchangeId.fromId("coinbase") }
        assertEquals(ExchangeId.GATE, ExchangeId.fromId("gate"))
    }
}
