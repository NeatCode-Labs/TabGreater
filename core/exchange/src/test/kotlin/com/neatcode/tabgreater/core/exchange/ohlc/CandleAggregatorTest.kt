package com.neatcode.tabgreater.core.exchange.ohlc

import com.neatcode.tabgreater.core.model.Candle
import com.neatcode.tabgreater.core.model.Timeframe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CandleAggregatorTest {

    // 2026-08-17 (Monday) 00:00 UTC.
    private val monday = 1786924800000L
    private val hour = 3_600_000L
    private val day = 24 * hour

    @Test
    fun `intraday buckets are multiples of the bar length since the epoch`() {
        val t = monday + 5 * hour + 17 * 60_000
        assertEquals(monday + 4 * hour, CandleAggregator.bucketStart(t, Timeframe.H4))
        assertEquals(monday + 5 * hour, CandleAggregator.bucketStart(t, Timeframe.H1))
        assertEquals(monday + 5 * hour + 15 * 60_000, CandleAggregator.bucketStart(t, Timeframe.M15))
        assertEquals(monday, CandleAggregator.bucketStart(t, Timeframe.D1))
    }

    @Test
    fun `weekly buckets start on monday and monthly buckets on the first`() {
        val sunday = monday - 1
        assertEquals(monday - 7 * day, CandleAggregator.bucketStart(sunday, Timeframe.W1))
        assertEquals(monday, CandleAggregator.bucketStart(monday + 6 * day + 23 * hour, Timeframe.W1))
        // 2026-08-01 00:00 UTC.
        val augustFirst = 1785542400000L
        assertEquals(augustFirst, CandleAggregator.bucketStart(monday + 3 * day, Timeframe.MN1))
        // 2026-09-01 00:00 UTC.
        assertEquals(1788220800000L, CandleAggregator.nextBucketStart(augustFirst, Timeframe.MN1))
    }

    @Test
    fun `hourly bars are merged into four hour bars with ohlc and volume semantics`() {
        val source = (0 until 8).map { i ->
            Candle(
                openTime = monday + i * hour,
                open = 100.0 + i,
                high = 110.0 + i,
                low = 90.0 - i,
                close = 101.0 + i,
                volume = 1.0,
                closed = true,
            )
        }

        val merged = CandleAggregator.aggregate(source, Timeframe.H1, Timeframe.H4)

        assertEquals(2, merged.size)
        val first = merged[0]
        assertEquals(monday, first.openTime)
        assertEquals(100.0, first.open, 0.0)
        assertEquals(113.0, first.high, 0.0)
        assertEquals(87.0, first.low, 0.0)
        assertEquals(104.0, first.close, 0.0)
        assertEquals(4.0, first.volume, 0.0)
        assertTrue(first.closed)
        assertEquals(monday + 4 * hour, merged[1].openTime)
        assertEquals(108.0, merged[1].close, 0.0)
        assertTrue(merged[1].closed)
    }

    @Test
    fun `an incomplete trailing bucket and a forming source bar are reported as forming`() {
        val source = listOf(
            Candle(monday, 1.0, 2.0, 0.5, 1.5, 1.0, closed = true),
            Candle(monday + hour, 1.5, 3.0, 1.0, 2.0, 1.0, closed = true),
            Candle(monday + 4 * hour, 2.0, 2.5, 1.5, 2.2, 1.0, closed = true),
            Candle(monday + 5 * hour, 2.2, 2.6, 2.0, 2.4, 1.0, closed = true),
            Candle(monday + 6 * hour, 2.4, 2.7, 2.3, 2.5, 1.0, closed = true),
            Candle(monday + 7 * hour, 2.5, 2.8, 2.4, 2.6, 1.0, closed = false),
        )

        val merged = CandleAggregator.aggregate(source, Timeframe.H1, Timeframe.H4)

        assertEquals(2, merged.size)
        assertTrue("missing tail hours -> forming", !merged[0].closed)
        assertTrue("last source bar forming -> forming", !merged[1].closed)
    }

    @Test
    fun `daily bars roll into calendar weeks and months`() {
        val source = (0 until 10).map { i ->
            Candle(monday - 2 * day + i * day, 1.0, 1.0, 1.0, 1.0 + i, 1.0, closed = true)
        }

        val weeks = CandleAggregator.aggregate(source, Timeframe.D1, Timeframe.W1)
        assertEquals(listOf(monday - 7 * day, monday, monday + 7 * day), weeks.map { it.openTime })
        assertEquals(2.0, weeks[0].close, 0.0)
        assertEquals(9.0, weeks[1].close, 0.0)
        assertTrue(!weeks[0].closed)
        assertTrue(weeks[1].closed)
        assertTrue(!weeks[2].closed)

        val months = CandleAggregator.aggregate(source, Timeframe.D1, Timeframe.MN1)
        assertEquals(1, months.size)
        assertEquals(1785542400000L, months[0].openTime)
        assertTrue(!months[0].closed)
    }

    @Test
    fun `sourceFor picks the coarsest native divisor`() {
        // An exchange serving only these five bars: everything else has to be aggregated.
        val native = setOf(Timeframe.M1, Timeframe.M5, Timeframe.M15, Timeframe.H1, Timeframe.D1)
        assertEquals(Timeframe.H1, CandleAggregator.sourceFor(Timeframe.H4, native))
        assertEquals(Timeframe.M15, CandleAggregator.sourceFor(Timeframe.M30, native))
        assertEquals(Timeframe.D1, CandleAggregator.sourceFor(Timeframe.W1, native))
        assertEquals(Timeframe.D1, CandleAggregator.sourceFor(Timeframe.MN1, native))
        assertNull(CandleAggregator.sourceFor(Timeframe.M1, native))
        // A week does not divide a month.
        assertEquals(Timeframe.D1, CandleAggregator.sourceFor(Timeframe.MN1, setOf(Timeframe.D1, Timeframe.W1)))
    }
}
