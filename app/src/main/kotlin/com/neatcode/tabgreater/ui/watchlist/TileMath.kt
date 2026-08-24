package com.neatcode.tabgreater.ui.watchlist

import com.neatcode.tabgreater.core.data.repo.Sparkline
import com.neatcode.tabgreater.core.model.PriceFormat
import com.neatcode.tabgreater.core.model.SparkPeriod
import com.neatcode.tabgreater.core.model.Ticker
import kotlin.math.abs

/**
 * The raw numbers one tile shows, before formatting. Pure Kotlin so the whole per-tile maths is
 * unit-testable without a view model (`TileMathTest`).
 *
 * @property price last traded price; `null` while neither the ticker nor the history has arrived.
 * @property changePct signed percentage over the watchlist's period.
 * @property absChange the same change in quote units (`last - open`).
 * @property high highest price of the period, [low] the lowest, [volume] the base volume.
 */
internal data class TileNumbers(
    val price: Double? = null,
    val changePct: Double? = null,
    val absChange: Double? = null,
    val high: Double? = null,
    val low: Double? = null,
    val volume: Double? = null,
)

/**
 * Assembles everything a tile needs from the live ticker and the cached sparkline window.
 *
 * The "Sparkline period" sheet drives the sparkline, the % change **and** the
 * high/low/volume together, so only the 24 h period may fall back to the exchange's own
 * rolling statistic; every other period is measured across the window itself.
 */
internal fun tileNumbers(period: SparkPeriod, ticker: Ticker?, spark: Sparkline?): TileNumbers {
    val price = ticker?.last ?: spark?.lastClose
    val changePct = changePct(period, ticker, spark)
    val daily = period == SparkPeriod.HOURS_24
    return TileNumbers(
        price = price,
        changePct = changePct,
        absChange = absChange(daily, price, ticker, spark),
        high = if (daily) ticker?.high24h ?: spark?.high else spark?.high,
        low = if (daily) ticker?.low24h ?: spark?.low else spark?.low,
        volume = if (daily) ticker?.volumeBase24h ?: spark?.volume else spark?.volume,
    )
}

/**
 * `+1.10 (0.04%)` — the signed absolute change followed by the **unsigned** percentage with two
 * decimals. `null` when either half is unknown.
 */
internal fun absChangeText(absChange: Double?, changePct: Double?, precision: Int): String? {
    if (absChange == null || changePct == null || !changePct.isFinite()) return null
    val signed = PriceFormat.formatSignedAbs(absChange, precision)
    if (signed == PriceFormat.NO_VALUE) return null
    return "$signed (${PriceFormat.formatPrice(abs(changePct), CHANGE_DECIMALS)}%)"
}

/**
 * 24 h uses the exchange's own statistic (falling back to the open price, then to the window);
 * every other period is measured across the sparkline window itself.
 */
internal fun changePct(period: SparkPeriod, ticker: Ticker?, spark: Sparkline?): Double? =
    if (period == SparkPeriod.HOURS_24) {
        ticker?.changePct24h ?: openChange(ticker) ?: windowChange(spark)
    } else {
        windowChange(spark)
    }

/**
 * The change in quote units. Over 24 h the open price is preferred; when the exchange only
 * publishes the percentage the open is reconstructed from it (`last / (1 + pct/100)`), and as a
 * last resort the sparkline window's first close is used — the same order as [changePct], so the
 * two lines on a tile always describe the same move.
 */
private fun absChange(daily: Boolean, price: Double?, ticker: Ticker?, spark: Sparkline?): Double? {
    val last = price ?: return null
    if (daily) {
        val open = ticker?.open24h
        if (open != null) return last - open
        val pct = ticker?.changePct24h
        if (pct != null) {
            val factor = 1.0 + pct / 100.0
            if (factor != 0.0) {
                val reconstructed = last - last / factor
                if (reconstructed.isFinite()) return reconstructed
            }
        }
    }
    val first = spark?.firstClose ?: return null
    return last - first
}

private fun openChange(ticker: Ticker?): Double? {
    val open = ticker?.open24h ?: return null
    if (open == 0.0) return null
    return (ticker.last - open) / open * 100.0
}

private fun windowChange(spark: Sparkline?): Double? {
    val first = spark?.firstClose ?: return null
    val last = spark.lastClose ?: return null
    if (first == 0.0) return null
    return (last - first) / first * 100.0
}

/** Percentages always print with two decimals, whatever the market's precision is. */
private const val CHANGE_DECIMALS = 2
