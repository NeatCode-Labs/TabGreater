package com.neatcode.tabgreater.core.exchange.kucoin

import com.neatcode.tabgreater.core.exchange.ExchangeAdapter
import com.neatcode.tabgreater.core.exchange.ExchangeHttpException
import com.neatcode.tabgreater.core.exchange.ExchangeUnavailableException
import com.neatcode.tabgreater.core.exchange.ohlc.CandleAggregator
import com.neatcode.tabgreater.core.exchange.ratelimit.TokenBucket
import com.neatcode.tabgreater.core.exchange.ws.ExchangeSocket
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.math.BigDecimal
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * KuCoin spot adapter.
 *
 * Two KuCoin quirks shape this class:
 * 1. every REST response is wrapped in `{"code":"200000","data":...}` and a *failed* call still
 *    answers HTTP 200 with a different `code`, so the envelope is checked before decoding;
 * 2. there is no static WebSocket URL — the endpoint and a 24 h token come from
 *    `POST /api/v1/bullet-public`, which is why the socket is built with [ExchangeSocket]'s
 *    `urlProvider` constructor and gets a fresh token before every (re)connect.
 *
 * Live prices need two topics per market: `/market/ticker` is fast but carries only the trade
 * price, `/market/snapshot` arrives every ~2 s with the 24 h statistics. The snapshot values are
 * cached per symbol so a ticker frame can be emitted as a complete [Ticker].
 */
class KuCoinAdapter(
    private val client: OkHttpClient,
    private val scope: CoroutineScope,
    private val restBase: String = DEFAULT_REST_BASE,
    private val logger: (String) -> Unit = {},
    private val bucket: TokenBucket = TokenBucket(REST_CAPACITY, REST_REFILL_PER_SECOND),
    /** Overrides the interval KuCoin negotiates in `bullet-public`; only tests pass it. */
    private val pingIntervalOverrideMs: Long? = null,
) : ExchangeAdapter {

    override val id: ExchangeId = ExchangeId.KUCOIN

    /** KuCoin serves all nine timeframes, so [CandleAggregator] is only used for its bucket maths. */
    override val nativeTimeframes: Set<Timeframe> = Timeframe.entries.toSet()

    private val json = Json { ignoreUnknownKeys = true }

    private val socketLock = Any()
    private var socket: ExchangeSocket? = null
    private var pingJob: Job? = null
    private val commandId = AtomicInteger(0)

    /** Ping interval reported by the last `bullet-public` response. */
    @Volatile
    private var negotiatedPingIntervalMs: Long = DEFAULT_PING_INTERVAL_MS

    /** Last 24 h statistics per native symbol, filled by `/market/snapshot` frames. */
    private val snapshotStats = ConcurrentHashMap<String, SnapshotStats>()

    private val book = SubscriptionBook(scope) { subscribe, unsubscribe -> flush(subscribe, unsubscribe) }

    // ---------------------------------------------------------------- REST

    // Request and JSON decode both run on Dispatchers.IO: callers may be on the main thread and
    // /api/v1/market/allTickers is ~300 KB for 1 000 symbols.
    override suspend fun listMarkets(): List<Market> = withContext(Dispatchers.IO) {
        val symbols: List<SymbolDto> = decodeData(get(SYMBOLS_PATH, weight = WEIGHT_SYMBOLS))
        symbols.asSequence()
            .filter { it.enableTrading }
            .filter { SYMBOL_PART.matches(it.baseCurrency) && SYMBOL_PART.matches(it.quoteCurrency) }
            .map { dto ->
                Market(
                    key = MarketKey.of(ExchangeId.KUCOIN, dto.baseCurrency, dto.quoteCurrency),
                    nativeSymbol = dto.symbol,
                    pricePrecision = dto.priceIncrement?.let(::decimalsOf) ?: DEFAULT_PRICE_PRECISION,
                    tickSize = dto.priceIncrement?.toDoubleOrNull(),
                )
            }
            .toList()
    }

    /**
     * Small sets go through `/market/stats` (one request per market, but a tiny response); larger
     * ones through the single `/market/allTickers` dump, which is cheaper than a dozen round trips.
     */
    override suspend fun fetchTickers(markets: List<Market>): List<Ticker> = withContext(Dispatchers.IO) {
        if (markets.isEmpty()) return@withContext emptyList()
        if (markets.size <= STATS_THRESHOLD) fetchStats(markets) else fetchAllTickers(markets)
    }

    private suspend fun fetchStats(markets: List<Market>): List<Ticker> {
        val tickers = ArrayList<Ticker>(markets.size)
        for (market in markets) {
            val query = listOf(PARAM_SYMBOL to market.nativeSymbol)
            val dto: StatsDto = decodeData(get(STATS_PATH, query, WEIGHT_STATS))
            // An unknown symbol answers HTTP 200 with every field null instead of an error.
            tickers += dto.toTicker(market.key) ?: continue
        }
        return tickers
    }

    private suspend fun fetchAllTickers(markets: List<Market>): List<Ticker> {
        val byNativeSymbol = markets.associateBy { it.nativeSymbol }
        val dto: AllTickersDto = decodeData(get(ALL_TICKERS_PATH, weight = WEIGHT_ALL_TICKERS))
        val tickers = ArrayList<Ticker>(markets.size)
        for (row in dto.ticker) {
            val market = byNativeSymbol[row.symbol] ?: continue
            tickers += row.toTicker(market.key, dto.time) ?: continue
        }
        return tickers
    }

    override suspend fun fetchOHLCV(
        market: Market,
        timeframe: Timeframe,
        endTime: Long?,
        limit: Int,
    ): List<Candle> = withContext(Dispatchers.IO) {
        val wanted = limit.coerceIn(1, MAX_CANDLES)
        // KuCoin keeps every bar whose start is <= endAt, while [endTime] is an exclusive bound:
        // step back one second so a bar starting exactly at [endTime] stays out of the page.
        val endSeconds = if (endTime != null) {
            (endTime - 1) / MILLIS_PER_SECOND
        } else {
            System.currentTimeMillis() / MILLIS_PER_SECOND
        }
        // Without startAt KuCoin caps the answer at 100 rows; with it, up to 1 500 come back.
        // 1 500 monthly bars reach back well past 1970, and KuCoin rejects a negative startAt.
        val startSeconds = (endSeconds - lookbackSeconds(timeframe, wanted + 1)).coerceAtLeast(0)
        val query = listOf(
            PARAM_SYMBOL to market.nativeSymbol,
            PARAM_TYPE to candleTypeOf(timeframe),
            PARAM_START_AT to startSeconds.toString(),
            PARAM_END_AT to endSeconds.toString(),
        )
        val rows: List<List<String>> = decodeData(get(CANDLES_PATH, query, WEIGHT_CANDLES))
        val now = System.currentTimeMillis()
        // KuCoin answers newest first; the app wants oldest first.
        rows.asReversed().mapNotNull { candleOf(it, timeframe, now) }.takeLast(wanted)
    }

    private suspend fun get(
        path: String,
        query: List<Pair<String, String>> = emptyList(),
        weight: Int,
    ): String = withContext(Dispatchers.IO) {
        bucket.acquire(weight)
        val url = (restBase.trimEnd('/') + path).toHttpUrl().newBuilder()
        for ((name, value) in query) url.addQueryParameter(name, value)
        execute(Request.Builder().url(url.build()).get().build())
    }

    private suspend fun post(path: String, weight: Int): String = withContext(Dispatchers.IO) {
        bucket.acquire(weight)
        val url = (restBase.trimEnd('/') + path).toHttpUrl()
        execute(Request.Builder().url(url).post(EMPTY_BODY.toRequestBody()).build())
    }

    private fun execute(request: Request): String =
        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) throw errorFor(response, body)
            body
        }

    private fun errorFor(response: Response, body: String): Exception {
        val code = response.code
        val detail = body.take(ERROR_BODY_CHARS)
        return when (code) {
            HTTP_UNAVAILABLE_FOR_LEGAL_REASONS ->
                ExchangeUnavailableException(id, "KuCoin is unavailable in this region (HTTP $code): $detail")

            HTTP_TOO_MANY_REQUESTS -> {
                val retryAfter = response.header(HEADER_RETRY_AFTER)
                val suffix = if (retryAfter != null) ", retry after $retryAfter s" else ""
                ExchangeHttpException(id, code, "KuCoin rate limit hit (HTTP $code$suffix): $detail")
            }

            else -> ExchangeHttpException(id, code, "KuCoin request failed (HTTP $code): $detail")
        }
    }

    /** Unwraps `{"code":"200000","data":...}`; any other code is an error even though HTTP said 200. */
    private fun payloadOf(body: String): JsonElement {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject
            ?: throw ExchangeHttpException(id, HTTP_OK, "KuCoin sent a non-JSON body: ${body.take(ERROR_BODY_CHARS)}")
        val code = root.string(FIELD_CODE)
        if (code != CODE_OK) {
            val message = root.string(FIELD_MSG).orEmpty()
            throw ExchangeHttpException(id, HTTP_OK, "KuCoin error $code: $message")
        }
        return root[FIELD_DATA] ?: JsonNull
    }

    private inline fun <reified T> decodeData(body: String): T = json.decodeFromJsonElement<T>(payloadOf(body))

    // ------------------------------------------------------------ WebSocket

    override fun watchTickers(markets: List<Market>): Flow<Ticker> = channelFlow {
        val byNativeSymbol = LinkedHashMap<String, Market>()
        for (market in markets) byNativeSymbol[market.nativeSymbol] = market
        // Never complete on our own: the live repository reads completion as a dropped stream.
        if (byNativeSymbol.isEmpty()) awaitCancellation()
        val keys = byNativeSymbol.keys.flatMap { listOf(KEY_TICKER + it, KEY_SNAPSHOT + it) }
        val (activeSocket, fresh) = acquire(keys)
        try {
            activeSocket.messages
                .onSubscription { book.queueSubscribe(fresh) }
                .collect { text ->
                    val frame = messageFrame(text) ?: return@collect
                    val market = byNativeSymbol[frame.symbol] ?: return@collect
                    val ticker = when (frame.subject) {
                        SUBJECT_SNAPSHOT -> snapshotTicker(frame, market.key)
                        SUBJECT_TICKER -> decodeOrNull<TickerFrameDto>(frame.data)
                            ?.toTicker(market.key, snapshotStats[frame.symbol])

                        else -> null
                    } ?: return@collect
                    send(ticker)
                }
        } finally {
            release(keys)
        }
    }

    private fun snapshotTicker(frame: MessageFrame, key: MarketKey): Ticker? {
        val data = decodeOrNull<SnapshotFrameDto>(frame.data)?.data ?: return null
        val stats = data.toStats()
        snapshotStats[frame.symbol] = stats
        return data.toTicker(key, stats)
    }

    override fun watchKlines(market: Market, timeframe: Timeframe): Flow<Candle> = channelFlow {
        val topicSymbol = "${market.nativeSymbol}$CANDLES_SYMBOL_SEPARATOR${candleTypeOf(timeframe)}"
        val key = KEY_CANDLES + topicSymbol
        val (activeSocket, fresh) = acquire(listOf(key))
        // KuCoin never marks a bar closed; the "add" of the next bar is the signal that the
        // previous one is final, so the last forming bar of this stream is kept around.
        var forming: Candle? = null
        try {
            activeSocket.messages
                .onSubscription { book.queueSubscribe(fresh) }
                .collect { text ->
                    val frame = messageFrame(text) ?: return@collect
                    if (frame.symbol != topicSymbol) return@collect
                    if (frame.subject != SUBJECT_CANDLES_UPDATE && frame.subject != SUBJECT_CANDLES_ADD) {
                        return@collect
                    }
                    val row = decodeOrNull<CandlesFrameDto>(frame.data)?.candles ?: return@collect
                    val candle = candleOf(row, timeframe, closedOverride = false) ?: return@collect
                    val previous = forming
                    if (frame.subject == SUBJECT_CANDLES_ADD && previous != null &&
                        previous.openTime < candle.openTime
                    ) {
                        send(previous.copy(closed = true))
                    }
                    forming = candle
                    send(candle)
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
                pingJob = startPing(it)
            }
            logger("kucoin: acquire ${keys.size} key(s), ${acquired.fresh.size} fresh, fresh socket=${acquired.wasEmpty}")
            current to acquired.fresh
        }

    /** Drops a reference from every key, unsubscribing what is gone and closing an idle socket. */
    private fun release(keys: Collection<String>) {
        val gone: List<String>
        val target: ExchangeSocket?
        val closing: Boolean
        synchronized(socketLock) {
            val released = book.release(keys)
            gone = released.gone
            target = socket
            closing = released.isEmpty && target != null
            if (closing) {
                socket = null
                pingJob?.cancel()
                pingJob = null
                // Still holding socketLock: a collector arriving right now blocks in acquire(), so
                // its queued subscribe cannot be wiped by this teardown. Nesting is safe because
                // socketLock -> SubscriptionBook lock is the only order in this class and
                // SubscriptionBook calls back into flush() after releasing its own lock.
                book.clearPending()
                snapshotStats.clear()
            }
        }
        val current = target ?: return
        logger("kucoin: release ${keys.size} key(s), ${gone.size} gone, closeSocket=$closing")
        if (closing) {
            // Nothing is listening any more: drop the socket instead of unsubscribing topic by topic.
            current.close()
        } else if (gone.isNotEmpty()) {
            book.queueUnsubscribe(gone)
        }
    }

    private fun createSocket(): ExchangeSocket {
        val created = ExchangeSocket(
            client = client,
            urlProvider = { connectUrl() },
            name = ExchangeId.KUCOIN.id,
            scope = scope,
            // A bullet token lives 24 h; the default 23 h recycle fetches a fresh one in time.
            maxLifetimeMs = ExchangeSocket.DEFAULT_MAX_LIFETIME_MS,
            minSendGapMs = MIN_SEND_GAP_MS,
            logger = logger,
        )
        created.onReconnected = {
            // A discarded socket whose handshake completed late must not touch the live queue.
            if (synchronized(socketLock) { socket === created }) book.resubscribeAll()
        }
        return created
    }

    /** Resolves a one-shot socket URL: `bullet-public` hands out the endpoint plus a fresh token. */
    private suspend fun connectUrl(): String {
        val bullet: BulletDto = decodeData(post(BULLET_PATH, WEIGHT_BULLET))
        val server = bullet.instanceServers.firstOrNull()
            ?: throw ExchangeHttpException(id, HTTP_OK, "KuCoin bullet-public returned no instance server")
        negotiatedPingIntervalMs = server.pingInterval ?: DEFAULT_PING_INTERVAL_MS
        val separator = if (server.endpoint.contains('?')) '&' else '?'
        val token = URLEncoder.encode(bullet.token, StandardCharsets.UTF_8)
        return "${server.endpoint}$separator$PARAM_TOKEN=$token&$PARAM_CONNECT_ID=${UUID.randomUUID()}"
    }

    /** Application-level keepalive; KuCoin closes a socket that stops pinging. Dies with the socket. */
    private fun startPing(target: ExchangeSocket): Job = scope.launch {
        while (isActive) {
            delay(pingIntervalOverrideMs ?: negotiatedPingIntervalMs)
            target.send(
                buildJsonObject {
                    put(FIELD_ID, nextCommandId())
                    put(FIELD_TYPE, TYPE_PING)
                }.toString(),
            )
        }
    }

    /** Turns the net subscribe/unsubscribe lists of [book] into KuCoin topic frames. */
    private fun flush(subscribe: List<String>, unsubscribe: List<String>) {
        val target = synchronized(socketLock) { socket } ?: return
        for (frame in framesFor(unsubscribe, TYPE_UNSUBSCRIBE)) target.send(frame)
        for (frame in framesFor(subscribe, TYPE_SUBSCRIBE)) target.send(frame)
    }

    /**
     * `/market/ticker` accepts a comma separated list (up to [MAX_SYMBOLS_PER_TOPIC] symbols);
     * `/market/snapshot` and `/market/candles` take exactly one symbol per frame.
     */
    private fun framesFor(keys: List<String>, type: String): List<String> {
        if (keys.isEmpty()) return emptyList()
        val frames = ArrayList<String>()
        val tickerSymbols = keys.filter { it.startsWith(KEY_TICKER) }.map { it.symbolPart() }
        for (batch in tickerSymbols.chunked(MAX_SYMBOLS_PER_TOPIC)) {
            frames += topicFrame(type, TOPIC_TICKER + batch.joinToString(SYMBOL_LIST_SEPARATOR))
        }
        for (key in keys) {
            when {
                key.startsWith(KEY_SNAPSHOT) -> frames += topicFrame(type, TOPIC_SNAPSHOT + key.symbolPart())
                key.startsWith(KEY_CANDLES) -> frames += topicFrame(type, TOPIC_CANDLES + key.symbolPart())
            }
        }
        return frames
    }

    private fun topicFrame(type: String, topic: String): String = buildJsonObject {
        put(FIELD_ID, nextCommandId())
        put(FIELD_TYPE, type)
        put(FIELD_TOPIC, topic)
        put(FIELD_PRIVATE_CHANNEL, false)
        put(FIELD_RESPONSE, true)
    }.toString()

    private fun nextCommandId(): String = commandId.incrementAndGet().toString()

    /** Splits a `message` frame into topic symbol, subject and payload; anything else yields `null`. */
    private fun messageFrame(text: String): MessageFrame? {
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return null
        val type = root.string(FIELD_TYPE)
        if (type != TYPE_MESSAGE) {
            if (type == TYPE_ERROR) logger("kucoin: error frame ${text.take(ERROR_BODY_CHARS)}")
            return null
        }
        val topic = root.string(FIELD_TOPIC) ?: return null
        val subject = root.string(FIELD_SUBJECT) ?: return null
        val data = root[FIELD_DATA] ?: return null
        return MessageFrame(topic.substringAfter(TOPIC_SEPARATOR, ""), subject, data)
    }

    private inline fun <reified T> decodeOrNull(element: JsonElement): T? =
        runCatching { json.decodeFromJsonElement<T>(element) }.getOrNull()

    private fun JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun String.symbolPart(): String = substringAfter(KEY_SEPARATOR)

    /**
     * `[time_sec, open, close, high, low, volume, turnover]` — KuCoin puts the close *before*
     * high/low. [closedOverride] is `false` for live frames, which are always the forming bar.
     * The bar end comes from [CandleAggregator.nextBucketStart] so weeks and calendar months are
     * measured properly instead of by [Timeframe.millis]' flat 30 day month.
     */
    private fun candleOf(
        row: List<String>,
        timeframe: Timeframe,
        now: Long = 0,
        closedOverride: Boolean? = null,
    ): Candle? {
        if (row.size < CANDLE_CELLS) return null
        val openTime = (row[IDX_TIME].toLongOrNull() ?: return null) * MILLIS_PER_SECOND
        return Candle(
            openTime = openTime,
            open = row[IDX_OPEN].toDoubleOrNull() ?: return null,
            high = row[IDX_HIGH].toDoubleOrNull() ?: return null,
            low = row[IDX_LOW].toDoubleOrNull() ?: return null,
            close = row[IDX_CLOSE].toDoubleOrNull() ?: return null,
            volume = row[IDX_VOLUME].toDoubleOrNull() ?: return null,
            closed = closedOverride ?: (CandleAggregator.nextBucketStart(openTime, timeframe) <= now),
        )
    }

    /**
     * Seconds covered by [count] bars. [Timeframe.MN1] is 30 days in the model, which would ask for
     * too short a window on 31 day months, so months use a safe upper bound (extra bars are dropped
     * by the caller's `takeLast`).
     */
    private fun lookbackSeconds(timeframe: Timeframe, count: Int): Long = when (timeframe) {
        Timeframe.MN1 -> count * MAX_MONTH_SECONDS
        else -> count * timeframe.seconds
    }

    private fun candleTypeOf(timeframe: Timeframe): String = when (timeframe) {
        Timeframe.M1 -> "1min"
        Timeframe.M5 -> "5min"
        Timeframe.M15 -> "15min"
        Timeframe.M30 -> "30min"
        Timeframe.H1 -> "1hour"
        Timeframe.H4 -> "4hour"
        Timeframe.D1 -> "1day"
        Timeframe.W1 -> "1week"
        Timeframe.MN1 -> "1month"
    }

    /**
     * Decimals of a KuCoin price increment after trailing zeros are stripped: `"0.1"` -> `1`,
     * `"0.000000001"` -> `9`, `"1"` -> `0`. Scientific notation goes through [BigDecimal].
     */
    private fun decimalsOf(increment: String): Int {
        val text = increment.trim()
        if (text.contains('e') || text.contains('E')) {
            val scale = runCatching { BigDecimal(text).stripTrailingZeros().scale() }.getOrNull()
            return (scale ?: 0).coerceAtLeast(0)
        }
        val dot = text.indexOf('.')
        if (dot < 0) return 0
        var end = text.length
        while (end > dot + 1 && text[end - 1] == '0') end--
        return end - dot - 1
    }

    /** One decoded `type=message` frame: the symbol part of the topic, its subject and payload. */
    private data class MessageFrame(val symbol: String, val subject: String, val data: JsonElement)

    companion object {
        const val DEFAULT_REST_BASE: String = "https://api.kucoin.com"

        /** Public REST pool: 2 000 weight per 30 s per IP. */
        const val REST_CAPACITY: Double = 2000.0
        const val REST_REFILL_PER_SECOND: Double = 2000.0 / 30

        private const val SYMBOLS_PATH = "/api/v2/symbols"
        private const val STATS_PATH = "/api/v1/market/stats"
        private const val ALL_TICKERS_PATH = "/api/v1/market/allTickers"
        private const val CANDLES_PATH = "/api/v1/market/candles"
        private const val BULLET_PATH = "/api/v1/bullet-public"

        private const val WEIGHT_SYMBOLS = 4
        private const val WEIGHT_STATS = 15
        private const val WEIGHT_ALL_TICKERS = 15
        private const val WEIGHT_CANDLES = 3
        private const val WEIGHT_BULLET = 10

        /** Above this many markets the single allTickers dump beats one stats call per market. */
        private const val STATS_THRESHOLD = 8

        /** `startAt` lifts the answer from 100 rows to at most 1 500. */
        private const val MAX_CANDLES = 1500
        private const val MILLIS_PER_SECOND = 1000L
        private const val MAX_MONTH_SECONDS = 31L * 24 * 60 * 60
        private const val DEFAULT_PRICE_PRECISION = 8

        private const val PARAM_SYMBOL = "symbol"
        private const val PARAM_TYPE = "type"
        private const val PARAM_START_AT = "startAt"
        private const val PARAM_END_AT = "endAt"
        private const val PARAM_TOKEN = "token"
        private const val PARAM_CONNECT_ID = "connectId"

        private const val EMPTY_BODY = ""
        private const val CODE_OK = "200000"
        private const val FIELD_CODE = "code"
        private const val FIELD_MSG = "msg"
        private const val FIELD_DATA = "data"
        private const val FIELD_ID = "id"
        private const val FIELD_TYPE = "type"
        private const val FIELD_TOPIC = "topic"
        private const val FIELD_SUBJECT = "subject"
        private const val FIELD_PRIVATE_CHANNEL = "privateChannel"
        private const val FIELD_RESPONSE = "response"

        private const val TYPE_MESSAGE = "message"
        private const val TYPE_SUBSCRIBE = "subscribe"
        private const val TYPE_UNSUBSCRIBE = "unsubscribe"
        private const val TYPE_PING = "ping"
        private const val TYPE_ERROR = "error"

        private const val SUBJECT_TICKER = "trade.ticker"
        private const val SUBJECT_SNAPSHOT = "trade.snapshot"
        private const val SUBJECT_CANDLES_UPDATE = "trade.candles.update"
        private const val SUBJECT_CANDLES_ADD = "trade.candles.add"

        private const val TOPIC_TICKER = "/market/ticker:"
        private const val TOPIC_SNAPSHOT = "/market/snapshot:"
        private const val TOPIC_CANDLES = "/market/candles:"
        private const val TOPIC_SEPARATOR = ':'

        private const val KEY_TICKER = "ticker:"
        private const val KEY_SNAPSHOT = "snapshot:"
        private const val KEY_CANDLES = "candles:"
        private const val KEY_SEPARATOR = ":"
        private const val SYMBOL_LIST_SEPARATOR = ","
        private const val CANDLES_SYMBOL_SEPARATOR = "_"

        /** One topic string carries at most 100 symbols. */
        private const val MAX_SYMBOLS_PER_TOPIC = 100

        /** Outbound limit is 100 messages per 10 s. */
        private const val MIN_SEND_GAP_MS = 100L

        /** Fallback when `bullet-public` does not report one. */
        private const val DEFAULT_PING_INTERVAL_MS = 18_000L

        private val SYMBOL_PART = Regex("[A-Za-z0-9]+")

        private const val ERROR_BODY_CHARS = 200
        private const val HTTP_OK = 200
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val HTTP_UNAVAILABLE_FOR_LEGAL_REASONS = 451
        private const val HEADER_RETRY_AFTER = "Retry-After"

        private const val CANDLE_CELLS = 6
        private const val IDX_TIME = 0
        private const val IDX_OPEN = 1
        private const val IDX_CLOSE = 2
        private const val IDX_HIGH = 3
        private const val IDX_LOW = 4
        private const val IDX_VOLUME = 5
    }
}
