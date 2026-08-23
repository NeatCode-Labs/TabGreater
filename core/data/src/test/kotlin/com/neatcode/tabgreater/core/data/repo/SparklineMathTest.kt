package com.neatcode.tabgreater.core.data.repo

import com.neatcode.tabgreater.core.model.Candle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SparklineMathTest {

    @Test
    fun `shorter than the budget is returned unchanged`() {
        val closes = doubleArrayOf(1.0, 2.0, 3.0)
        val points = downsampleCloses(closes, 64)
        assertEquals(3, points.size)
        assertEquals(floatArrayOf(1f, 2f, 3f).toList(), points.toList())
    }

    @Test
    fun `exactly the budget is returned unchanged`() {
        val closes = DoubleArray(64) { it.toDouble() }
        assertEquals(64, downsampleCloses(closes, 64).size)
    }

    @Test
    fun `longer input is reduced to the budget`() {
        val closes = DoubleArray(500) { it.toDouble() }
        val points = downsampleCloses(closes, 64)
        assertEquals(64, points.size)
    }

    @Test
    fun `first and last close survive downsampling`() {
        val closes = DoubleArray(180) { it.toDouble() * 3 }
        val points = downsampleCloses(closes, 48)
        assertEquals(closes.first().toFloat(), points.first(), 0f)
        assertEquals(closes.last().toFloat(), points.last(), 0f)
    }

    @Test
    fun `buckets average their samples`() {
        // 10 values, budget 4 -> [v0][avg v1..v4][avg v5..v8][v9]: two equal-width interior buckets.
        val closes = doubleArrayOf(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0)
        val points = downsampleCloses(closes, 4)
        assertEquals(4, points.size)
        assertEquals(0f, points[0], 0f)
        assertEquals(9f, points[3], 0f)
        assertEquals(2.5f, points[1], 1e-4f)
        assertEquals(6.5f, points[2], 1e-4f)
    }

    @Test
    fun `monotonic input stays monotonic`() {
        val closes = DoubleArray(365) { it.toDouble() }
        val points = downsampleCloses(closes, 64)
        for (i in 1 until points.size) {
            assertTrue("points[$i] < points[${i - 1}]", points[i] >= points[i - 1])
        }
    }

    @Test
    fun `single value produces a one point array`() {
        assertEquals(1, downsampleCloses(doubleArrayOf(42.0), 64).size)
    }

    @Test
    fun `empty input produces an empty array`() {
        assertEquals(0, downsampleCloses(DoubleArray(0), 64).size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a budget below two is rejected`() {
        downsampleCloses(DoubleArray(10) { it.toDouble() }, 1)
    }

    @Test
    fun `buildSparkline reports the real first and last close`() {
        val candles = (0 until 120).map { candle(openTime = it * 60_000L, close = 100.0 + it) }
        val sparkline = buildSparkline(candles, updatedAt = 1_700_000_000_000L)
        assertEquals(100.0, sparkline.firstClose!!, 0.0)
        assertEquals(219.0, sparkline.lastClose!!, 0.0)
        assertEquals(Sparkline.MAX_POINTS, sparkline.points.size)
        assertEquals(1_700_000_000_000L, sparkline.updatedAt)
        assertFalse(sparkline.isEmpty)
    }

    @Test
    fun `buildSparkline on no candles is EMPTY`() {
        assertSame(Sparkline.EMPTY, buildSparkline(emptyList(), updatedAt = 5L))
    }

    @Test
    fun `mergeCandle replaces the forming bar instead of appending`() {
        val window = mutableListOf(candle(0, 1.0), candle(60_000, 2.0))
        window.mergeCandle(candle(60_000, 2.5), maxSize = 4)
        assertEquals(2, window.size)
        assertEquals(2.5, window.last().close, 0.0)
    }

    @Test
    fun `mergeCandle appends and trims to the window size`() {
        val window = mutableListOf(candle(0, 1.0), candle(60_000, 2.0), candle(120_000, 3.0))
        window.mergeCandle(candle(180_000, 4.0), maxSize = 3)
        assertEquals(listOf(60_000L, 120_000L, 180_000L), window.map { it.openTime })
    }

    @Test
    fun `mergeCandle keeps an out of order bar sorted`() {
        val window = mutableListOf(candle(0, 1.0), candle(120_000, 3.0))
        window.mergeCandle(candle(60_000, 2.0), maxSize = 8)
        assertEquals(listOf(0L, 60_000L, 120_000L), window.map { it.openTime })
    }

    @Test
    fun `buildSparkline aggregates high low and volume over the window`() {
        val candles = listOf(
            bar(0, high = 110.0, low = 90.0, close = 100.0, volume = 1.5),
            bar(60_000, high = 130.0, low = 95.0, close = 120.0, volume = 2.5),
            bar(120_000, high = 125.0, low = 80.0, close = 115.0, volume = 6.0),
        )

        val sparkline = buildSparkline(candles, updatedAt = 1L)

        assertEquals(130.0, sparkline.high!!, 0.0)
        assertEquals(80.0, sparkline.low!!, 0.0)
        assertEquals(10.0, sparkline.volume!!, 1e-9)
        assertEquals(100.0, sparkline.firstClose!!, 0.0)
        assertEquals(115.0, sparkline.lastClose!!, 0.0)
    }

    @Test
    fun `a single candle reports its own high low and volume`() {
        val sparkline = buildSparkline(listOf(bar(0, high = 9.0, low = 3.0, close = 5.0, volume = 2.0)), updatedAt = 0L)
        assertEquals(9.0, sparkline.high!!, 0.0)
        assertEquals(3.0, sparkline.low!!, 0.0)
        assertEquals(2.0, sparkline.volume!!, 0.0)
    }

    @Test
    fun `aggregates survive downsampling of a long window`() {
        // The spike sits in the interior, where closes are averaged away but high/low must not be.
        val candles = (0 until 400).map { i ->
            val close = 100.0 + i
            if (i == 200) bar(i * 60_000L, high = 9_999.0, low = 1.0, close = close, volume = 1.0)
            else bar(i * 60_000L, high = close, low = close, close = close, volume = 1.0)
        }

        val sparkline = buildSparkline(candles, updatedAt = 0L)

        assertEquals(9_999.0, sparkline.high!!, 0.0)
        assertEquals(1.0, sparkline.low!!, 0.0)
        assertEquals(400.0, sparkline.volume!!, 0.0)
        assertEquals(Sparkline.MAX_POINTS, sparkline.points.size)
    }

    @Test
    fun `the empty sparkline has no aggregates`() {
        assertNull(Sparkline.EMPTY.high)
        assertNull(Sparkline.EMPTY.low)
        assertNull(Sparkline.EMPTY.volume)
    }

    private fun bar(openTime: Long, high: Double, low: Double, close: Double, volume: Double) =
        Candle(openTime = openTime, open = close, high = high, low = low, close = close, volume = volume)

    private fun candle(openTime: Long, close: Double) =
        Candle(openTime = openTime, open = close, high = close, low = close, close = close, volume = 1.0)
}
