package com.neatcode.tabgreater.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class MarketSeriesIdTest {
    @Test
    fun `daily price binary family keeps the UTC expiry slot and ignores the changing target`() {
        val morning = Instant.parse("2026-11-20T06:00:00Z").toEpochMilli()
        val nextMorning = Instant.parse("2026-11-21T06:00:00Z").toEpochMilli()
        val afternoon = Instant.parse("2026-11-21T12:00:00Z").toEpochMilli()

        val original = MarketSeriesId.dailyPriceBinary("btc", "1d", "63330", morning)

        assertEquals("priceBinary:BTC:1d:21600000", original)
        assertEquals(original, MarketSeriesId.dailyPriceBinary("BTC", "1d", "64000", nextMorning))
        assertNotEquals(original, MarketSeriesId.dailyPriceBinary("BTC", "1d", "64000", afternoon))
    }

    @Test
    fun `only a complete daily price binary descriptor receives a series identity`() {
        val expiry = Instant.parse("2026-11-20T06:00:00Z").toEpochMilli()
        assertNull(MarketSeriesId.dailyPriceBinary("BTC", "3m", "63330", expiry))
        assertNull(MarketSeriesId.dailyPriceBinary("BTC", "1d", "0", expiry))
        assertNull(MarketSeriesId.dailyPriceBinary("BTC", "1d", "63330", null))
        assertNull(MarketSeriesId.dailyPriceBinary(null, "1d", "63330", expiry))
    }
}
