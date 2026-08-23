package com.neatcode.tabgreater.feature.chart

import com.neatcode.tabgreater.core.model.Candle
import com.neatcode.tabgreater.core.model.ExchangeId
import com.neatcode.tabgreater.core.model.Market
import com.neatcode.tabgreater.core.model.MarketKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * One bar in KLineChart's own shape — the field names are `KLineData`'s, so the JSON the bridge
 * emits can be handed to the data-loader callback untouched.
 */
@Serializable
data class ChartBar(
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
)

/** `Candle` (epoch-millis `openTime`) as KLineChart's `KLineData`. */
fun Candle.toChartBar(): ChartBar =
    ChartBar(timestamp = openTime, open = open, high = high, low = low, close = close, volume = volume)

/** Envelope of every JS → Kotlin message. [id] is absent for fire-and-forget messages. */
@Serializable
data class Req(
    val id: String? = null,
    val action: String,
    val payload: JsonObject = JsonObject(emptyMap()),
)

/**
 * `getBars` request. [type] is KLineChart's `DataLoadType`; [timestamp] is the open time of the
 * oldest loaded bar for `forward` (page older) and of the newest for `backward`, `null` on `init`.
 */
@Serializable
data class GetBarsReq(
    val exchange: String,
    val ticker: String,
    val instId: String = "",
    val type: String,
    val timestamp: Long? = null,
    val span: Int,
    val unit: String,
    val limit: Int,
)

/** `getBars` reply. [hasMoreOlder] drives KLineChart's left-edge paging. */
@Serializable
data class GetBarsRes(
    val bars: List<ChartBar>,
    val hasMoreOlder: Boolean,
)

/** `subscribeBar` request: which market and period the live bar stream should follow. */
@Serializable
data class SubscribeBarReq(
    val exchange: String,
    val ticker: String,
    val instId: String = "",
    val span: Int,
    val unit: String,
)

/** `log` payload; `chart.js` also routes `window.onerror` through it. */
@Serializable
data class LogPayload(val kind: String = "info", val text: String = "")

/** The `tg.setMarket` symbol argument. */
@Serializable
data class ChartSymbol(
    val exchange: String,
    val ticker: String,
    val instId: String,
    val pricePrecision: Int,
    val volumePrecision: Int,
)

/** [Market] as the symbol object `chart.js` passes back with every data-loader call. */
fun Market.toChartSymbol(volumePrecision: Int = DEFAULT_VOLUME_PRECISION): ChartSymbol =
    ChartSymbol(
        exchange = key.exchange.id,
        ticker = key.pair,
        instId = nativeSymbol,
        pricePrecision = pricePrecision,
        volumePrecision = volumePrecision,
    )

/** Base volumes are shown with 2 decimals; no exchange reports a base-volume precision. */
const val DEFAULT_VOLUME_PRECISION: Int = 2

/**
 * Wire format between `assets/chart/chart.js` and [ChartBridge] — pure Kotlin so it can be
 * exercised without a WebView.
 */
object ChartProtocol {

    const val ACTION_GET_BARS = "getBars"
    const val ACTION_SUBSCRIBE_BAR = "subscribeBar"
    const val ACTION_UNSUBSCRIBE_BAR = "unsubscribeBar"
    const val ACTION_LOG = "log"
    const val ACTION_READY = "ready"

    /** Page older history (KLineChart prepends the result). */
    const val TYPE_FORWARD = "forward"

    val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Parses one JS message; `null` when it is not a request we understand. */
    fun parseRequest(raw: String): Req? = runCatching { json.decodeFromString<Req>(raw) }.getOrNull()

    /**
     * Whether the venue can serve history older than what it just returned.
     *
     * Kraken has no paging parameter at all — its OHLC endpoint always answers with the newest
     * ~720 bars and nothing older is retrievable — so it must report `false` or KLineChart keeps
     * asking for the same window every time the user drags to the left edge.
     */
    fun canPageHistory(exchange: ExchangeId): Boolean = exchange != ExchangeId.KRAKEN

    /**
     * `hasMoreOlder` for a reply of [barCount] bars to a request for [limit].
     *
     * Half the requested window is the cut-off: exchanges routinely return fewer bars than asked
     * for (thin markets, aggregated timeframes), and a strict `== limit` rule would stop paging
     * on the first short page while a `> 0` rule would loop forever at the start of history.
     */
    fun hasMoreOlder(exchange: ExchangeId, barCount: Int, limit: Int): Boolean =
        canPageHistory(exchange) && limit > 0 && barCount >= limit / 2

    /**
     * The exclusive upper bound to pass to `ExchangeAdapter.fetchOHLCV`: the oldest loaded bar
     * when paging older, `null` (= now) for `init` and for `backward`, which we never page.
     */
    fun endTimeFor(type: String, timestamp: Long?): Long? = if (type == TYPE_FORWARD) timestamp else null

    /** The canonical key a `{exchange, ticker}` pair names, or `null` if either is malformed. */
    fun marketKeyOf(exchange: String, ticker: String): MarketKey? =
        MarketKey.parseOrNull("$exchange:$ticker")
}
