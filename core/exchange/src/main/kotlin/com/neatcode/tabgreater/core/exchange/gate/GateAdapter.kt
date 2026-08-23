package com.neatcode.tabgreater.core.exchange.gate

import com.neatcode.tabgreater.core.exchange.ExchangeAdapter
import com.neatcode.tabgreater.core.exchange.ExchangeHttpException
import com.neatcode.tabgreater.core.exchange.ExchangeUnavailableException
import com.neatcode.tabgreater.core.exchange.ratelimit.TokenBucket
import com.neatcode.tabgreater.core.exchange.ws.ExchangeSocket
import com.neatcode.tabgreater.core.exchange.ws.SocketState
import com.neatcode.tabgreater.core.exchange.ws.SubscriptionBook
import com.neatcode.tabgreater.core.model.Candle
import com.neatcode.tabgreater.core.model.ExchangeId
import com.neatcode.tabgreater.core.model.Market
import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.Ticker
import com.neatcode.tabgreater.core.model.Timeframe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap

/**
 * Gate.io spot adapter over the public v4 API (`/api/v4/spot/...`), which needs no API key.
 *
 * REST runs on [Dispatchers.IO] and is paced by one [TokenBucket] per endpoint path, because Gate
 * counts its 200 requests / 10 s public limit per endpoint. Live data goes through a single
 * [ExchangeSocket] shared by every collector of this adapter and reference-counted per stream key
 * by a [SubscriptionBook], so the socket exists only while something is collecting.
 */
class GateAdapter(
    private val client: OkHttpClient,
    private val scope: CoroutineScope,
    private val restBase: String = DEFAULT_REST_BASE,
    private val wsBase: String = DEFAULT_WS_BASE,
    private val logger: (String) -> Unit = {},
    private val pingIntervalMs: Long = PING_INTERVAL_MS,
    private val bucketFactory: () -> TokenBucket = { TokenBucket(REST_CAPACITY, REST_REFILL_PER_SECOND) },
) : ExchangeAdapter {

    override val id: ExchangeId = ExchangeId.GATE

    /**
     * Gate serves every timeframe the app shows natively: `7d` bars are Monday-aligned and `30d`
     * bars open on the 1st of each month, so no client-side aggregation is needed.
     */
    override val nativeTimeframes: Set<Timeframe> = Timeframe.entries.toSet()

    private val json = Json { ignoreUnknownKeys = true }

    private val buckets = ConcurrentHashMap<String, TokenBucket>()

    private val socketLock = Any()
    private var socket: ExchangeSocket? = null
    private var keepaliveJob: Job? = null

    private val book = SubscriptionBook(scope = scope, onFlush = ::flushCommands)

    // ---------------------------------------------------------------- REST

    // Request and JSON decode both run on Dispatchers.IO: callers may be on the main thread and the
    // pair list alone is ~2 200 entries.
    override suspend fun listMarkets(): List<Market> = withContext(Dispatchers.IO) {
        val pairs = json.decodeFromString<List<CurrencyPairDto>>(get(PATH_CURRENCY_PAIRS))
        pairs.asSequence()
            .filter { it.tradeStatus == STATUS_TRADABLE }
            // Gate lists pairs with non-ASCII bases (e.g. "龙虾_USDT") that no other layer can key.
            .filter { SYMBOL_PART.matches(it.base) && SYMBOL_PART.matches(it.quote) }
            .map { dto ->
                Market(
                    key = MarketKey.of(ExchangeId.GATE, dto.base, dto.quote),
                    nativeSymbol = dto.id,
                    pricePrecision = dto.precision.coerceAtLeast(0),
                    tickSize = tickSizeOf(dto.precision),
                )
            }
            .toList()
    }

    /**
     * Gate has no multi-symbol ticker query: either one request per pair or one big request for all
     * ~2 200 tickers (~540 KB). [ALL_TICKERS_THRESHOLD] is where the single big response gets cheaper.
     */
    override suspend fun fetchTickers(markets: List<Market>): List<Ticker> = withContext(Dispatchers.IO) {
        if (markets.isEmpty()) return@withContext emptyList()
        val byNativeSymbol = markets.associateBy { it.nativeSymbol }
        val dtos = if (markets.size <= ALL_TICKERS_THRESHOLD) {
            markets.flatMap { market ->
                decodeTickers(get(PATH_TICKERS, listOf(PARAM_CURRENCY_PAIR to market.nativeSymbol)))
            }
        } else {
            decodeTickers(get(PATH_TICKERS))
        }
        val now = System.currentTimeMillis()
        dtos.mapNotNull { dto -> byNativeSymbol[dto.currencyPair]?.let { dto.toTicker(it.key, now) } }
    }

    override suspend fun fetchOHLCV(
        market: Market,
        timeframe: Timeframe,
        endTime: Long?,
        limit: Int,
    ): List<Candle> = withContext(Dispatchers.IO) {
        val query = mutableListOf(
            PARAM_CURRENCY_PAIR to market.nativeSymbol,
            PARAM_INTERVAL to intervalOf(timeframe),
            PARAM_LIMIT to limit.coerceIn(1, MAX_CANDLES).toString(),
        )
        // Gate's "to" is an inclusive upper bound in seconds; one second less makes it exclusive.
        if (endTime != null) query += PARAM_TO to (endTime / MILLIS_PER_SECOND - 1).toString()
        val body = get(PATH_CANDLESTICKS, query)
        json.parseToJsonElement(body).jsonArray.mapNotNull { row -> rowToCandle(row.jsonArray) }
    }

    private fun decodeTickers(body: String): List<GateTickerDto> = json.decodeFromString(body)

    private suspend fun get(path: String, query: List<Pair<String, String>> = emptyList()): String =
        withContext(Dispatchers.IO) {
            buckets.computeIfAbsent(path) { bucketFactory() }.acquire()
            val url = (restBase.trimEnd('/') + path).toHttpUrl().newBuilder()
            for ((name, value) in query) url.addQueryParameter(name, value)
            val request = Request.Builder().url(url.build()).get().build()
            client.newCall(request).execute().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) throw errorFor(response, body)
                body
            }
        }

    /** Gate reports failures as `{"label":"INVALID_CURRENCY","message":"..."}` with a 4xx status. */
    private fun errorFor(response: Response, body: String): Exception {
        val code = response.code
        val detail = body.take(ERROR_BODY_CHARS)
        val label = labelOf(body)
        val labelSuffix = if (label != null) ", $label" else ""
        return when {
            code == HTTP_UNAVAILABLE_FOR_LEGAL_REASONS ->
                ExchangeUnavailableException(id, "Gate is unavailable in this region (HTTP $code): $detail")

            // Gate answers geo-blocked IPs with 403 and a FORBIDDEN/IP_FORBIDDEN label.
            code == HTTP_FORBIDDEN && label?.contains(LABEL_FORBIDDEN) == true ->
                ExchangeUnavailableException(id, "Gate is unavailable in this region (HTTP $code$labelSuffix): $detail")

            code == HTTP_TOO_MANY_REQUESTS -> {
                val retryAfter = response.header(HEADER_RETRY_AFTER)
                val retrySuffix = if (retryAfter != null) ", retry after $retryAfter s" else ""
                ExchangeHttpException(id, code, "Gate rate limit hit (HTTP $code$labelSuffix$retrySuffix): $detail")
            }

            else -> ExchangeHttpException(id, code, "Gate request failed (HTTP $code$labelSuffix): $detail")
        }
    }

    private fun labelOf(body: String): String? {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
        return stringOf(root, FIELD_LABEL)
    }

    // ------------------------------------------------------------ WebSocket

    override fun watchTickers(markets: List<Market>): Flow<Ticker> = channelFlow {
        val marketsByPair = LinkedHashMap<String, Market>()
        for (market in markets) marketsByPair[market.nativeSymbol] = market
        // The live repository reads a completed flow as a dropped feed and restarts it with backoff,
        // so an empty market set has to idle until the collector leaves instead of completing.
        if (marketsByPair.isEmpty()) awaitCancellation()
        val keys = marketsByPair.keys.map(::tickerKey)
        val (activeSocket, fresh) = acquire(keys)
        try {
            activeSocket.messages
                .onSubscription { book.queueSubscribe(fresh) }
                .collect { text ->
                    val (root, result) = update(text, CHANNEL_TICKERS) ?: return@collect
                    val dto = decodeOrNull<GateTickerDto>(result) ?: return@collect
                    val market = marketsByPair[dto.currencyPair] ?: return@collect
                    val ticker = dto.toTicker(market.key, eventTime(root)) ?: return@collect
                    this@channelFlow.send(ticker)
                }
        } finally {
            release(keys)
        }
    }

    override fun watchKlines(market: Market, timeframe: Timeframe): Flow<Candle> = channelFlow {
        val stream = candleStream(intervalOf(timeframe), market.nativeSymbol)
        val key = candleKey(stream)
        val (activeSocket, fresh) = acquire(listOf(key))
        try {
            activeSocket.messages
                .onSubscription { book.queueSubscribe(fresh) }
                .collect { text ->
                    val (_, result) = update(text, CHANNEL_CANDLES) ?: return@collect
                    if (stringOf(result, FIELD_NAME) != stream) return@collect
                    val candle = resultToCandle(result) ?: return@collect
                    this@channelFlow.send(candle)
                }
        } finally {
            release(listOf(key))
        }
    }

    /** Adds a reference to every key and returns the shared socket plus the keys new to it. */
    private fun acquire(keys: Collection<String>): Pair<ExchangeSocket, List<String>> =
        synchronized(socketLock) {
            val acquired = book.acquire(keys)
            val current = socket ?: createSocket().also {
                socket = it
                it.connect()
                keepaliveJob = launchKeepalive(it)
            }
            current to acquired.fresh
        }

    /** Drops a reference from every key, unsubscribing and finally closing the socket. */
    private fun release(keys: Collection<String>) {
        var closing: ExchangeSocket? = null
        // Dropping the references and deciding about the socket must be atomic against [acquire],
        // or a collector arriving right now would be handed a socket this call then closes.
        val released = synchronized(socketLock) {
            val dropped = book.release(keys)
            if (dropped.isEmpty) {
                // Nothing else is listening: drop the socket instead of unsubscribing key by key.
                closing = socket
                socket = null
                keepaliveJob?.cancel()
                keepaliveJob = null
                // Clearing the queue belongs under this lock too: the next collector takes it, builds
                // a fresh socket and queues its subscribe, which this teardown must not wipe.
                book.clearPending()
            }
            dropped
        }
        val target = closing
        logger("${id.id}: release ${keys.size} key(s), ${book.active.size} left, closeSocket=${target != null}")
        // Closing blocks on the socket's own lock, so it stays outside [socketLock].
        if (target != null) {
            target.close()
        } else if (released.gone.isNotEmpty()) {
            book.queueUnsubscribe(released.gone)
        }
    }

    private fun createSocket(): ExchangeSocket {
        val created = ExchangeSocket(
            client = client,
            url = wsBase,
            name = id.id,
            scope = scope,
            // Gate keeps a connection alive indefinitely as long as it is pinged, and it answers our
            // application-level ping, so neither proactive recycling nor OkHttp ping frames apply.
            maxLifetimeMs = 0,
            pingIntervalMs = null,
            minSendGapMs = MIN_SEND_GAP_MS,
            logger = logger,
        )
        created.onReconnected = {
            // A discarded socket whose handshake completed late must not touch the live book.
            if (synchronized(socketLock) { socket === created }) book.resubscribeAll()
        }
        return created
    }

    /** Gate closes connections that stay idle, so ping for exactly as long as the socket lives. */
    private fun launchKeepalive(target: ExchangeSocket): Job = scope.launch {
        while (isActive) {
            delay(pingIntervalMs)
            // Pings queued while the socket is down would carry a stale time and, worse, push the
            // post-reconnect resubscribe behind a backlog of them in the paced sender.
            if (target.state.value != SocketState.OPEN) continue
            target.send(
                buildJsonObject {
                    put(FIELD_TIME, System.currentTimeMillis() / MILLIS_PER_SECOND)
                    put(FIELD_CHANNEL, CHANNEL_PING)
                }.toString(),
            )
        }
    }

    /**
     * Turns the net command lists into Gate frames: every ticker pair fits in one `payload` list
     * (chunked at [MAX_PAIRS_PER_FRAME]), while candles take exactly one `[interval, pair]` frame each.
     */
    private fun flushCommands(subscribe: List<String>, unsubscribe: List<String>) {
        val target = synchronized(socketLock) { socket } ?: return
        send(target, unsubscribe, EVENT_UNSUBSCRIBE)
        send(target, subscribe, EVENT_SUBSCRIBE)
    }

    private fun send(target: ExchangeSocket, keys: List<String>, event: String) {
        val pairs = keys.filter { it.startsWith(KEY_TICKERS) }.map { it.removePrefix(KEY_TICKERS) }
        for (batch in pairs.chunked(MAX_PAIRS_PER_FRAME)) {
            target.send(commandFrame(CHANNEL_TICKERS, event, batch))
        }
        for (key in keys.filter { it.startsWith(KEY_CANDLES) }) {
            val stream = key.removePrefix(KEY_CANDLES)
            target.send(
                commandFrame(CHANNEL_CANDLES, event, listOf(stream.substringBefore('_'), stream.substringAfter('_'))),
            )
        }
    }

    private fun commandFrame(channel: String, event: String, payload: List<String>): String = buildJsonObject {
        put(FIELD_TIME, System.currentTimeMillis() / MILLIS_PER_SECOND)
        put(FIELD_CHANNEL, channel)
        put(FIELD_EVENT, event)
        putJsonArray(FIELD_PAYLOAD) { for (item in payload) add(item) }
    }.toString()

    /**
     * Root + `result` of an `update` frame on [channel], or `null` for anything else: acks (logged
     * when they carry an error), pongs, frames of another channel and garbage never throw.
     */
    private fun update(text: String, channel: String): Pair<JsonObject, JsonObject>? {
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return null
        if (stringOf(root, FIELD_CHANNEL) != channel) return null
        if (stringOf(root, FIELD_EVENT) != EVENT_UPDATE) {
            val error = root[FIELD_ERROR] as? JsonObject
            if (error != null) logger("${id.id}: $channel ${stringOf(root, FIELD_EVENT)} rejected: $error")
            return null
        }
        val result = root[FIELD_RESULT] as? JsonObject ?: return null
        return root to result
    }

    private fun eventTime(root: JsonObject): Long {
        (root[FIELD_TIME_MS] as? JsonPrimitive)?.longOrNull?.let { return it }
        (root[FIELD_TIME] as? JsonPrimitive)?.longOrNull?.let { return it * MILLIS_PER_SECOND }
        return System.currentTimeMillis()
    }

    private inline fun <reified T> decodeOrNull(element: JsonObject): T? =
        runCatching { json.decodeFromJsonElement<T>(element) }.getOrNull()

    /**
     * A REST candlestick row is `[time_sec, quote_volume, close, high, low, open, base_volume, closed]`,
     * all strings — note the c/h/l/o order, which is not the usual o/h/l/c.
     */
    private fun rowToCandle(cells: JsonArray): Candle? {
        if (cells.size <= IDX_BASE_VOLUME) return null
        val seconds = contentOf(cells, IDX_TIME)?.toLongOrNull() ?: return null
        return Candle(
            openTime = seconds * MILLIS_PER_SECOND,
            open = contentOf(cells, IDX_OPEN)?.toDoubleOrNull() ?: return null,
            high = contentOf(cells, IDX_HIGH)?.toDoubleOrNull() ?: return null,
            low = contentOf(cells, IDX_LOW)?.toDoubleOrNull() ?: return null,
            close = contentOf(cells, IDX_CLOSE)?.toDoubleOrNull() ?: return null,
            volume = contentOf(cells, IDX_BASE_VOLUME)?.toDoubleOrNull() ?: return null,
            // Historical rows always carry the flag; treat a missing one as a finished bar.
            closed = contentOf(cells, IDX_WINDOW_CLOSED)?.toBooleanStrictOrNull() ?: true,
        )
    }

    /** Live bar: `a` is the base-asset amount (`v` is quote volume) and `w` the window-closed flag. */
    private fun resultToCandle(result: JsonObject): Candle? {
        val seconds = contentOf(result, FIELD_BAR_TIME)?.toLongOrNull() ?: return null
        return Candle(
            openTime = seconds * MILLIS_PER_SECOND,
            open = contentOf(result, FIELD_BAR_OPEN)?.toDoubleOrNull() ?: return null,
            high = contentOf(result, FIELD_BAR_HIGH)?.toDoubleOrNull() ?: return null,
            low = contentOf(result, FIELD_BAR_LOW)?.toDoubleOrNull() ?: return null,
            close = contentOf(result, FIELD_BAR_CLOSE)?.toDoubleOrNull() ?: return null,
            volume = contentOf(result, FIELD_BAR_AMOUNT)?.toDoubleOrNull() ?: return null,
            closed = contentOf(result, FIELD_BAR_CLOSED)?.toBooleanStrictOrNull() ?: false,
        )
    }

    private fun stringOf(obj: JsonObject, name: String): String? =
        (obj[name] as? JsonPrimitive)?.takeIf { it.isString }?.content

    /** Raw text of a primitive, quoted or not — Gate sends numbers as strings but booleans bare. */
    private fun contentOf(obj: JsonObject, name: String): String? = (obj[name] as? JsonPrimitive)?.content

    private fun contentOf(cells: JsonArray, index: Int): String? =
        (cells.getOrNull(index) as? JsonPrimitive)?.content

    private fun tickerKey(pair: String): String = KEY_TICKERS + pair

    private fun candleKey(stream: String): String = KEY_CANDLES + stream

    private fun candleStream(interval: String, pair: String): String = "${interval}_$pair"

    private fun intervalOf(timeframe: Timeframe): String = when (timeframe) {
        Timeframe.M1 -> "1m"
        Timeframe.M5 -> "5m"
        Timeframe.M15 -> "15m"
        Timeframe.M30 -> "30m"
        Timeframe.H1 -> "1h"
        Timeframe.H4 -> "4h"
        Timeframe.D1 -> "1d"
        // Gate rejects "1M" and treats "1w" as a rolling week: "7d" is Monday-aligned, "30d" calendar-monthly.
        Timeframe.W1 -> "7d"
        Timeframe.MN1 -> "30d"
    }

    /** Gate publishes decimals, not an increment: `precision = 1` means a tick of `0.1`. */
    private fun tickSizeOf(precision: Int): Double =
        BigDecimal.ONE.movePointLeft(precision.coerceAtLeast(0)).toDouble()

    companion object {
        const val DEFAULT_REST_BASE: String = "https://api.gateio.ws"
        const val DEFAULT_WS_BASE: String = "wss://api.gateio.ws/ws/v4/"

        /** Public limit: 200 requests / 10 s per endpoint per IP. */
        const val REST_CAPACITY: Double = 200.0
        const val REST_REFILL_PER_SECOND: Double = 20.0

        /** Gate hangs up on connections that send nothing for a while. */
        const val PING_INTERVAL_MS: Long = 15_000

        /** Above this many markets the single all-tickers response beats one request per pair. */
        const val ALL_TICKERS_THRESHOLD: Int = 25

        private const val MAX_CANDLES = 1000
        private const val MAX_PAIRS_PER_FRAME = 50
        private const val MIN_SEND_GAP_MS = 100L
        private const val MILLIS_PER_SECOND = 1000L

        private const val PATH_CURRENCY_PAIRS = "/api/v4/spot/currency_pairs"
        private const val PATH_TICKERS = "/api/v4/spot/tickers"
        private const val PATH_CANDLESTICKS = "/api/v4/spot/candlesticks"
        private const val PARAM_CURRENCY_PAIR = "currency_pair"
        private const val PARAM_INTERVAL = "interval"
        private const val PARAM_LIMIT = "limit"
        private const val PARAM_TO = "to"

        private const val STATUS_TRADABLE = "tradable"
        private val SYMBOL_PART = Regex("[A-Za-z0-9]+")

        private const val ERROR_BODY_CHARS = 200
        private const val HEADER_RETRY_AFTER = "Retry-After"
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val HTTP_UNAVAILABLE_FOR_LEGAL_REASONS = 451
        private const val LABEL_FORBIDDEN = "FORBIDDEN"

        private const val CHANNEL_TICKERS = "spot.tickers"
        private const val CHANNEL_CANDLES = "spot.candlesticks"
        private const val CHANNEL_PING = "spot.ping"
        private const val EVENT_SUBSCRIBE = "subscribe"
        private const val EVENT_UNSUBSCRIBE = "unsubscribe"
        private const val EVENT_UPDATE = "update"
        private const val KEY_TICKERS = "tickers:"
        private const val KEY_CANDLES = "candles:"

        private const val FIELD_TIME = "time"
        private const val FIELD_TIME_MS = "time_ms"
        private const val FIELD_CHANNEL = "channel"
        private const val FIELD_EVENT = "event"
        private const val FIELD_PAYLOAD = "payload"
        private const val FIELD_RESULT = "result"
        private const val FIELD_ERROR = "error"
        private const val FIELD_LABEL = "label"
        private const val FIELD_NAME = "n"
        private const val FIELD_BAR_TIME = "t"
        private const val FIELD_BAR_OPEN = "o"
        private const val FIELD_BAR_HIGH = "h"
        private const val FIELD_BAR_LOW = "l"
        private const val FIELD_BAR_CLOSE = "c"
        private const val FIELD_BAR_AMOUNT = "a"
        private const val FIELD_BAR_CLOSED = "w"

        private const val IDX_TIME = 0
        private const val IDX_CLOSE = 2
        private const val IDX_HIGH = 3
        private const val IDX_LOW = 4
        private const val IDX_OPEN = 5
        private const val IDX_BASE_VOLUME = 6
        private const val IDX_WINDOW_CLOSED = 7
    }
}

// ------------------------------------------------------------------- DTOs

@Serializable
private data class CurrencyPairDto(
    val id: String,
    val base: String = "",
    val quote: String = "",
    /** Price decimals, e.g. `1` for BTC_USDT and `9` for PEPE_USDT. */
    val precision: Int = 0,
    @SerialName("trade_status") val tradeStatus: String = "",
)

/** Shared by `GET /spot/tickers` and the `spot.tickers` stream — Gate sends the same fields on both. */
@Serializable
private data class GateTickerDto(
    @SerialName("currency_pair") val currencyPair: String = "",
    val last: String = "",
    @SerialName("lowest_ask") val lowestAsk: String? = null,
    @SerialName("highest_bid") val highestBid: String? = null,
    @SerialName("change_percentage") val changePercentage: String? = null,
    @SerialName("base_volume") val baseVolume: String? = null,
    @SerialName("quote_volume") val quoteVolume: String? = null,
    @SerialName("high_24h") val high24h: String? = null,
    @SerialName("low_24h") val low24h: String? = null,
)

private const val PERCENT = 100.0

private fun GateTickerDto.toTicker(key: MarketKey, timestamp: Long): Ticker? {
    val last = last.toDoubleOrNull() ?: return null
    val changePct = changePercentage?.toDoubleOrNull()
    // The open is derived first: when it cannot be derived the change it came from is unusable too,
    // so both stay null rather than showing a percentage no open price can back.
    val open = openFromChange(last, changePct)
    return Ticker(
        key = key,
        last = last,
        open24h = open,
        high24h = high24h?.toDoubleOrNull(),
        low24h = low24h?.toDoubleOrNull(),
        volumeBase24h = baseVolume?.toDoubleOrNull(),
        volumeQuote24h = quoteVolume?.toDoubleOrNull(),
        changePct24h = open?.let { changePct },
        bid = highestBid?.toDoubleOrNull(),
        ask = lowestAsk?.toDoubleOrNull(),
        timestamp = timestamp,
    )
}

/**
 * Gate publishes the signed 24 h change but no open price. Deriving the open from it keeps the
 * tiles and the live path consistent instead of mixing two sources of truth. A change of exactly
 * −100 % (or a zero last price, as on never-traded pairs) leaves no usable open, which the app
 * treats as unknown together with the percentage.
 */
private fun openFromChange(last: Double, changePct: Double?): Double? {
    if (changePct == null || last == 0.0) return null
    val factor = 1 + changePct / PERCENT
    return if (factor <= 0.0) null else last / factor
}
