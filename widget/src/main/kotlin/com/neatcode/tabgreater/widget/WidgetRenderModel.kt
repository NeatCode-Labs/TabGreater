package com.neatcode.tabgreater.widget

import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.PriceFormat
import com.neatcode.tabgreater.core.model.ShrunkPrice
import com.neatcode.tabgreater.core.model.Ticker
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * Everything one widget draws, already formatted. It is written into the widget's Glance state as
 * JSON, so `provideGlance` is a pure read: the widget host can re-inflate instantly and the
 * rendering never touches Room or the network.
 *
 * The 24 h sparkline travels as its (already downsampled, <= 96) closes rather than as a bitmap:
 * the pixel size is only known inside the composition, and points survive process death while an
 * in-memory bitmap does not. The points are kept even when [showSparkline] is off — they are
 * also the data behind the 24 h change on exchanges whose REST ticker carries none (Kraken), and
 * the service only re-reads the candle cache on some ticks, so the model has to carry them across.
 *
 * @property price already shrink-zeroed (`0.0₄123`) and grouped en-US.
 * @property hasChange a 24 h change is known; when it is not, [change] is the em dash and the
 *   widget draws it in the neutral text colour rather than as a gain.
 * @property stale the snapshot is older than [WidgetModelFactory.STALE_AFTER_MS]; the widget then
 *   draws the price at half alpha instead of pretending it is live.
 */
@Serializable
data class WidgetRenderModel(
    val key: String,
    val exchange: String,
    val pair: String,
    val price: String,
    val change: String,
    val changeUp: Boolean,
    val hasData: Boolean,
    val stale: Boolean,
    val updatedLabel: String,
    val backgroundArgb: Int,
    val showSparkline: Boolean,
    val spark: List<Float> = emptyList(),
    val hasChange: Boolean = true,
) {
    /** The market this widget is bound to, or `null` if the stored key no longer parses. */
    val marketKey: MarketKey? get() = MarketKey.parseOrNull(key)
}

/**
 * Turns a [WidgetConfig] plus the newest [Ticker] into a [WidgetRenderModel]. Pure and
 * side-effect free (the clock and the zone are parameters) so the formatting rules — precision,
 * signed percentage, staleness — are unit tested on the JVM.
 */
internal object WidgetModelFactory {

    /** Older than this and the price is drawn dimmed: the live layer has clearly lost the market. */
    const val STALE_AFTER_MS: Long = 10L * 60 * 1000

    private const val SUBSCRIPT_ZERO = '₀'

    fun build(
        config: WidgetConfig,
        ticker: Ticker?,
        pricePrecision: Int?,
        spark: List<Float>,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): WidgetRenderModel {
        val changePct = ticker?.let { it.changePct24h ?: derivedChangePct(it) ?: windowChangePct(it, spark) }
        val precision = pricePrecision ?: fallbackPrecision(ticker?.last)
        return WidgetRenderModel(
            key = config.key.value,
            exchange = config.key.exchange.displayName.uppercase(),
            pair = config.key.pair,
            price = ticker?.let { shrink(PriceFormat.formatPrice(it.last, precision)) } ?: PriceFormat.NO_VALUE,
            change = PriceFormat.formatChangePct(changePct),
            changeUp = (changePct ?: 0.0) >= 0.0,
            hasData = ticker != null,
            stale = ticker == null || now - ticker.timestamp > STALE_AFTER_MS,
            updatedLabel = ticker?.let { clockOf(it.timestamp, zone) }.orEmpty(),
            backgroundArgb = config.blendedArgb,
            showSparkline = config.showSparkline,
            spark = spark,
            hasChange = changePct != null,
        )
    }

    /** `hh:mm:ss` in the device's zone — the "Last updated" line asks the widget to show. */
    fun clockOf(epochMillis: Long, zone: ZoneId): String {
        val time = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalTime()
        return String.format(Locale.ROOT, "%02d:%02d:%02d", time.hour, time.minute, time.second)
    }

    /**
     * The leading-zero compression rendered with Unicode subscripts (`0.0₄123`), because
     * Glance has no `AnnotatedString` and therefore no real subscript span.
     */
    fun shrink(formatted: String): String {
        val shrunk: ShrunkPrice = PriceFormat.shrinkZeros(formatted)
        val zeros = shrunk.zeroCount ?: return shrunk.prefix
        return shrunk.prefix + subscript(zeros) + shrunk.rest
    }

    /**
     * Last resort for exchanges whose REST ticker carries neither a 24 h change nor a 24 h open
     * (Kraken): measure across the 24 h sparkline window, whose first point is the real close of
     * 24 h ago (`downsampleCloses` keeps the first sample exact).
     */
    private fun windowChangePct(ticker: Ticker, spark: List<Float>): Double? {
        val first = spark.firstOrNull()?.toDouble() ?: return null
        if (first <= 0.0 || !first.isFinite()) return null
        return (ticker.last - first) / first * 100.0
    }

    /** Exchanges that only stream `open24h` still get a percentage instead of an em dash. */
    private fun derivedChangePct(ticker: Ticker): Double? {
        val open = ticker.open24h ?: return null
        if (open <= 0.0 || !open.isFinite()) return null
        return (ticker.last - open) / open * 100.0
    }

    /**
     * Used only when the instrument list has not been cached yet (a widget configured before the
     * app ever ran): two decimals above 1, eight below, which is what every exchange uses for
     * majors and for sub-cent alt coins respectively.
     */
    fun fallbackPrecision(last: Double?): Int = if (last == null || last >= 1.0) 2 else 8

    private fun subscript(count: Int): String {
        val digits = count.toString()
        val out = StringBuilder(digits.length)
        for (c in digits) out.append(SUBSCRIPT_ZERO + (c - '0'))
        return out.toString()
    }
}
