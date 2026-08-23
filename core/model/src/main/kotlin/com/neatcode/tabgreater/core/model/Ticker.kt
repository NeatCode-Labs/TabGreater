package com.neatcode.tabgreater.core.model

import kotlinx.serialization.Serializable

/**
 * Latest snapshot of a market. All fields other than [last] are optional because
 * exchange streams deliver them at different granularities (e.g. Binance miniTicker
 * has no bid/ask; MEXC polling has everything).
 *
 * @property changePct24h signed percentage, e.g. `+6.52` for +6.52 %.
 * @property timestamp epoch millis when the exchange produced the update.
 */
@Serializable
data class Ticker(
    val key: MarketKey,
    val last: Double,
    val open24h: Double? = null,
    val high24h: Double? = null,
    val low24h: Double? = null,
    val volumeBase24h: Double? = null,
    val volumeQuote24h: Double? = null,
    val changePct24h: Double? = null,
    val bid: Double? = null,
    val ask: Double? = null,
    val timestamp: Long,
)

/** One OHLCV bar. [openTime] is epoch millis (UTC) of the bar start. */
@Serializable
data class Candle(
    val openTime: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
    /** `false` while the bar is still forming (live kline stream). */
    val closed: Boolean = true,
)

/** Chart / candle timeframes. [seconds] is the bar duration; months are approximated for sorting only. */
@Serializable
enum class Timeframe(val id: String, val seconds: Long, val label: String) {
    M1("1m", 60, "1m"),
    M5("5m", 5 * 60, "5m"),
    M15("15m", 15 * 60, "15m"),
    M30("30m", 30 * 60, "30m"),
    H1("1h", 60 * 60, "1H"),
    H4("4h", 4 * 60 * 60, "4H"),
    D1("1d", 24 * 60 * 60, "1D"),
    W1("1w", 7 * 24 * 60 * 60, "1W"),
    MN1("1M", 30L * 24 * 60 * 60, "1M");

    val millis: Long get() = seconds * 1000

    companion object {
        fun fromId(id: String): Timeframe = entries.first { it.id == id }
    }
}

/**
 * Sparkline look-back period, as picked in the "Tickers Timeframe" sheet.
 * Drives the sparkline window and the % change shown on tiles.
 *
 * Candle resolution is chosen so the 61 dp sparkline gets the ~60-96 vertices it needs to read
 * as a curve from a single request per market, using only timeframes that all
 * four exchanges serve natively (1m, 15m, 1h, 4h); 7d/30d are downsampled to [candles] / 2.
 */
@Serializable
enum class SparkPeriod(val id: String, val label: String, val timeframe: Timeframe, val candles: Int) {
    HOUR_1("1h", "1 hour", Timeframe.M1, 60),
    HOURS_24("24h", "24 hours", Timeframe.M15, 96),
    DAYS_7("7d", "7 days", Timeframe.H1, 168),
    DAYS_30("30d", "30 days", Timeframe.H4, 180);

    companion object {
        fun fromId(id: String): SparkPeriod = entries.firstOrNull { it.id == id } ?: HOURS_24
    }
}
