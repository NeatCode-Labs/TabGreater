package com.neatcode.tabgreater.core.data.repo

import com.neatcode.tabgreater.core.model.Candle

/**
 * Reduces [closes] to at most [maxPoints] values by averaging equal-width buckets over the
 * interior, keeping the first and the last close untouched so the tile's polyline still starts and
 * ends on the real prices. Pure and side-effect free so it can be unit tested on the JVM.
 */
internal fun downsampleCloses(closes: DoubleArray, maxPoints: Int): FloatArray {
    require(maxPoints >= 2) { "maxPoints must be >= 2, was $maxPoints" }
    if (closes.size <= maxPoints) return FloatArray(closes.size) { closes[it].toFloat() }

    val out = FloatArray(maxPoints)
    out[0] = closes.first().toFloat()
    out[maxPoints - 1] = closes.last().toFloat()

    // Interior indices 1 .. size-2; there are strictly more of them than buckets, so every
    // bucket receives at least one sample.
    val interiorFrom = 1
    val interiorCount = closes.size - 2
    val buckets = maxPoints - 2
    for (bucket in 0 until buckets) {
        val start = interiorFrom + (bucket.toLong() * interiorCount / buckets).toInt()
        val end = interiorFrom + ((bucket + 1).toLong() * interiorCount / buckets).toInt()
        var sum = 0.0
        for (i in start until end) sum += closes[i]
        out[bucket + 1] = (sum / (end - start)).toFloat()
    }
    return out
}

/**
 * Builds a [Sparkline] from chronologically ordered [candles]. Returns [Sparkline.EMPTY] for an
 * empty window so callers never have to special-case it.
 */
internal fun buildSparkline(candles: List<Candle>, updatedAt: Long): Sparkline {
    if (candles.isEmpty()) return Sparkline.EMPTY
    val closes = DoubleArray(candles.size) { candles[it].close }
    var high = Double.NEGATIVE_INFINITY
    var low = Double.POSITIVE_INFINITY
    var volume = 0.0
    for (candle in candles) {
        if (candle.high > high) high = candle.high
        if (candle.low < low) low = candle.low
        volume += candle.volume
    }
    return Sparkline(
        points = downsampleCloses(closes, Sparkline.MAX_POINTS),
        firstClose = closes.first(),
        lastClose = closes.last(),
        high = high.takeIf { it.isFinite() },
        low = low.takeIf { it.isFinite() },
        volume = volume.takeIf { it.isFinite() },
        updatedAt = updatedAt,
    )
}

/**
 * Inserts [candle] into a chronologically ordered window, replacing the bar with the same
 * `openTime` (live klines re-send the forming bar), and trims the window to [maxSize] bars.
 */
internal fun MutableList<Candle>.mergeCandle(candle: Candle, maxSize: Int) {
    val existing = indexOfFirst { it.openTime == candle.openTime }
    when {
        existing >= 0 -> this[existing] = candle
        isEmpty() || candle.openTime > last().openTime -> add(candle)
        else -> {
            val insertAt = indexOfFirst { it.openTime > candle.openTime }
            if (insertAt < 0) add(candle) else add(insertAt, candle)
        }
    }
    while (size > maxSize) removeAt(0)
}
