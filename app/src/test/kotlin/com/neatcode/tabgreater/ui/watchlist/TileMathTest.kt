package com.neatcode.tabgreater.ui.watchlist

import com.neatcode.tabgreater.core.data.repo.Sparkline
import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.SparkPeriod
import com.neatcode.tabgreater.core.model.Ticker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TileMathTest {

    private val key = MarketKey("binance:BTC/USDT")

    private fun ticker(
        last: Double = 100.0,
        open24h: Double? = null,
        high24h: Double? = null,
        low24h: Double? = null,
        volumeBase24h: Double? = null,
        changePct24h: Double? = null,
    ) = Ticker(
        key = key,
        last = last,
        open24h = open24h,
        high24h = high24h,
        low24h = low24h,
        volumeBase24h = volumeBase24h,
        changePct24h = changePct24h,
        timestamp = 0L,
    )

    private fun spark(
        first: Double? = 90.0,
        last: Double? = 110.0,
        high: Double? = 120.0,
        low: Double? = 80.0,
        volume: Double? = 5_000.0,
    ) = Sparkline(
        points = floatArrayOf(90f, 100f, 110f),
        firstClose = first,
        lastClose = last,
        high = high,
        low = low,
        volume = volume,
        updatedAt = 0L,
    )

    // ── price and percentage ─────────────────────────────────────────────────

    @Test
    fun `price prefers the live ticker over the sparkline`() {
        assertEquals(100.0, tileNumbers(SparkPeriod.HOURS_24, ticker(), spark()).price)
        assertEquals(110.0, tileNumbers(SparkPeriod.HOURS_24, null, spark()).price)
        assertNull(tileNumbers(SparkPeriod.HOURS_24, null, null).price)
    }

    @Test
    fun `24h change prefers the exchange statistic, then the open, then the window`() {
        val exchange = tileNumbers(SparkPeriod.HOURS_24, ticker(changePct24h = 6.52), spark())
        assertEquals(6.52, exchange.changePct!!, 1e-9)

        val open = tileNumbers(SparkPeriod.HOURS_24, ticker(last = 110.0, open24h = 100.0), spark())
        assertEquals(10.0, open.changePct!!, 1e-9)

        val window = tileNumbers(SparkPeriod.HOURS_24, ticker(), spark(first = 100.0, last = 105.0))
        assertEquals(5.0, window.changePct!!, 1e-9)
    }

    @Test
    fun `other periods always measure the window`() {
        val numbers = tileNumbers(
            SparkPeriod.DAYS_7,
            ticker(changePct24h = 6.52, open24h = 100.0),
            spark(first = 100.0, last = 90.0),
        )
        assertEquals(-10.0, numbers.changePct!!, 1e-9)
    }

    @Test
    fun `a zero base never divides`() {
        assertNull(tileNumbers(SparkPeriod.HOURS_24, ticker(open24h = 0.0), null).changePct)
        assertNull(tileNumbers(SparkPeriod.DAYS_30, null, spark(first = 0.0)).changePct)
    }

    // ── absolute change ──────────────────────────────────────────────────────

    @Test
    fun `24h absolute change uses the open price`() {
        val numbers = tileNumbers(SparkPeriod.HOURS_24, ticker(last = 2785.05, open24h = 2783.95), null)
        assertEquals(1.1, numbers.absChange!!, 1e-9)
    }

    @Test
    fun `24h absolute change reconstructs the open from the percentage`() {
        val numbers = tileNumbers(SparkPeriod.HOURS_24, ticker(last = 106.0, changePct24h = 6.0), null)
        assertEquals(6.0, numbers.absChange!!, 1e-9)
    }

    @Test
    fun `24h absolute change falls back to the window`() {
        val numbers = tileNumbers(SparkPeriod.HOURS_24, ticker(last = 110.0), spark(first = 100.0))
        assertEquals(10.0, numbers.absChange!!, 1e-9)
    }

    @Test
    fun `other periods take the absolute change from the window`() {
        val numbers = tileNumbers(
            SparkPeriod.HOUR_1,
            ticker(last = 110.0, open24h = 50.0),
            spark(first = 100.0),
        )
        assertEquals(10.0, numbers.absChange!!, 1e-9)
    }

    @Test
    fun `absolute change is unknown without a price or a reference`() {
        assertNull(tileNumbers(SparkPeriod.HOURS_24, null, null).absChange)
        assertNull(tileNumbers(SparkPeriod.HOURS_24, ticker(), spark(first = null)).absChange)
        // -100 % would reconstruct an infinite open price.
        assertNull(tileNumbers(SparkPeriod.HOURS_24, ticker(changePct24h = -100.0), null).absChange)
    }

    // ── high / low / volume ──────────────────────────────────────────────────

    @Test
    fun `24h high low volume prefer the exchange statistic`() {
        val numbers = tileNumbers(
            SparkPeriod.HOURS_24,
            ticker(high24h = 39_323.5, low24h = 38_186.0, volumeBase24h = 713e6),
            spark(),
        )
        assertEquals(39_323.5, numbers.high!!, 1e-9)
        assertEquals(38_186.0, numbers.low!!, 1e-9)
        assertEquals(713e6, numbers.volume!!, 1e-9)
    }

    @Test
    fun `24h high low volume fall back to the window aggregates`() {
        val numbers = tileNumbers(SparkPeriod.HOURS_24, ticker(), spark())
        assertEquals(120.0, numbers.high!!, 1e-9)
        assertEquals(80.0, numbers.low!!, 1e-9)
        assertEquals(5_000.0, numbers.volume!!, 1e-9)
    }

    @Test
    fun `other periods ignore the 24h statistic`() {
        val numbers = tileNumbers(
            SparkPeriod.DAYS_7,
            ticker(high24h = 39_323.5, low24h = 38_186.0, volumeBase24h = 713e6),
            spark(),
        )
        assertEquals(120.0, numbers.high!!, 1e-9)
        assertEquals(80.0, numbers.low!!, 1e-9)
        assertEquals(5_000.0, numbers.volume!!, 1e-9)
    }

    // ── the absolute-change line ─────────────────────────────────────────────

    @Test
    fun `renders the absolute change line`() {
        assertEquals("+1.10 (0.04%)", absChangeText(1.1, 0.04, 2))
        assertEquals("-159.0 (0.41%)", absChangeText(-159.0, -0.41, 1))
        assertEquals("-132.87 (0.35%)", absChangeText(-132.87, -0.345, 2))
        assertEquals("+0.000301 (0.42%)", absChangeText(0.000301, 0.42, 6))
    }

    @Test
    fun `the percentage inside the brackets is always unsigned`() {
        assertEquals("+2.920 (2.73%)", absChangeText(2.92, 2.73, 3))
        assertEquals("-2.920 (2.73%)", absChangeText(-2.92, -2.73, 3))
        assertEquals("0.00 (0.00%)", absChangeText(0.0, 0.0, 2))
    }

    @Test
    fun `the absolute change line is null when a half is missing`() {
        assertNull(absChangeText(null, 1.0, 2))
        assertNull(absChangeText(1.0, null, 2))
        assertNull(absChangeText(1.0, Double.NaN, 2))
        assertNull(absChangeText(Double.NaN, 1.0, 2))
    }
}
