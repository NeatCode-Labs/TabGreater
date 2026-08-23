package com.neatcode.tabgreater.core.exchange.ohlc

import com.neatcode.tabgreater.core.model.Candle
import com.neatcode.tabgreater.core.model.Timeframe
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters

/**
 * Client-side bar aggregation for timeframes an exchange does not serve natively
 * (of the supported exchanges only Kraken is missing one: it has no 1M bars).
 *
 * Bucket boundaries follow the convention every supported exchange uses for its native bars:
 * intraday and daily buckets are multiples of the bar length since the Unix epoch (UTC), weekly
 * buckets start on Monday 00:00 UTC and monthly buckets on the 1st 00:00 UTC.
 */
object CandleAggregator {

    /** Start (epoch millis, UTC) of the [timeframe] bucket that contains [timeMillis]. */
    fun bucketStart(timeMillis: Long, timeframe: Timeframe): Long = when (timeframe) {
        Timeframe.W1 -> {
            val day = LocalDate.ofInstant(Instant.ofEpochMilli(timeMillis), ZoneOffset.UTC)
            day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochMillis()
        }

        Timeframe.MN1 -> LocalDate.ofInstant(Instant.ofEpochMilli(timeMillis), ZoneOffset.UTC)
            .withDayOfMonth(1)
            .toEpochMillis()

        else -> timeMillis - Math.floorMod(timeMillis, timeframe.millis)
    }

    /** Start of the bucket that follows the one beginning at [bucketStart]. */
    fun nextBucketStart(bucketStart: Long, timeframe: Timeframe): Long = when (timeframe) {
        Timeframe.W1 -> bucketStart + timeframe.millis
        Timeframe.MN1 -> LocalDate.ofInstant(Instant.ofEpochMilli(bucketStart), ZoneOffset.UTC)
            .plusMonths(1)
            .toEpochMillis()

        else -> bucketStart + timeframe.millis
    }

    /**
     * Merges chronologically ordered [source] bars of timeframe [from] into bars of the coarser
     * timeframe [to]. Bars are grouped by [bucketStart]; the open is the first source open, the
     * close the last source close, high/low the extremes and volume the sum.
     *
     * A merged bar is `closed` only when every source bar in it is closed **and** the source bars
     * span the whole bucket (first one starts at the bucket start, last one ends at the bucket
     * end) — a bucket whose head or tail is missing (history that starts mid-week, a forming bar)
     * is reported as forming so callers refresh it later. Gaps inside a bucket (no trades) are
     * indistinguishable from complete data and are not detected.
     */
    fun aggregate(source: List<Candle>, from: Timeframe, to: Timeframe): List<Candle> {
        require(to.seconds > from.seconds) { "target ${to.id} must be coarser than source ${from.id}" }
        if (source.isEmpty()) return emptyList()

        val out = ArrayList<Candle>()
        var bucket = bucketStart(source.first().openTime, to)
        var bucketEnd = nextBucketStart(bucket, to)
        var open = source.first().open
        var high = source.first().high
        var low = source.first().low
        var close = source.first().close
        var volume = 0.0
        var allClosed = true
        var firstOpenTime = source.first().openTime
        var lastOpenTime = source.first().openTime
        var first = true

        fun emit() {
            val complete = allClosed && firstOpenTime == bucket && lastOpenTime + from.millis >= bucketEnd
            out += Candle(bucket, open, high, low, close, volume, closed = complete)
        }

        for (candle in source) {
            val start = bucketStart(candle.openTime, to)
            if (!first && start != bucket) {
                emit()
                bucket = start
                bucketEnd = nextBucketStart(bucket, to)
                open = candle.open
                high = candle.high
                low = candle.low
                volume = 0.0
                allClosed = true
                firstOpenTime = candle.openTime
            }
            first = false
            if (candle.high > high) high = candle.high
            if (candle.low < low) low = candle.low
            close = candle.close
            volume += candle.volume
            allClosed = allClosed && candle.closed
            lastOpenTime = candle.openTime
        }
        emit()
        return out
    }

    /**
     * The finest native timeframe of [native] from which [target] can be built exactly, or `null`
     * when none divides it. Prefers the coarsest candidate so the fewest source bars are needed.
     */
    fun sourceFor(target: Timeframe, native: Set<Timeframe>): Timeframe? {
        val candidates = native.filter { it.seconds < target.seconds && divides(it, target) }
        return candidates.maxByOrNull { it.seconds }
    }

    private fun divides(source: Timeframe, target: Timeframe): Boolean = when (target) {
        // A calendar month is a whole number of days but not of weeks.
        Timeframe.MN1 -> source != Timeframe.W1 && Timeframe.D1.seconds % source.seconds == 0L
        Timeframe.W1 -> Timeframe.W1.seconds % source.seconds == 0L && source != Timeframe.MN1
        else -> target.seconds % source.seconds == 0L
    }

    private fun LocalDate.toEpochMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}
