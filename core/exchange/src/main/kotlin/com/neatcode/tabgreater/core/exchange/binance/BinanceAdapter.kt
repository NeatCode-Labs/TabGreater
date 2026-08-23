package com.neatcode.tabgreater.core.exchange.binance

import com.neatcode.tabgreater.core.exchange.ExchangeAdapter
import com.neatcode.tabgreater.core.exchange.ExchangeHttpException
import com.neatcode.tabgreater.core.exchange.ExchangeUnavailableException
import com.neatcode.tabgreater.core.exchange.ws.ExchangeSocket
import com.neatcode.tabgreater.core.model.Candle
import com.neatcode.tabgreater.core.model.ExchangeId
import com.neatcode.tabgreater.core.model.Market
import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.Ticker
import com.neatcode.tabgreater.core.model.Timeframe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.atomic.AtomicInteger

/**
 * Binance spot adapter over the public market-data mirrors (`data-api` / `data-stream`), which need
 * no API key and are the ones that still answer from the EU.
 *
 * REST goes through [OkHttpClient] on [Dispatchers.IO]; live data goes through a single combined
 * stream socket shared by all subscribers of this adapter and reference-counted per stream, so the
 * socket exists only while something is collecting.
 */
class BinanceAdapter(
    private val client: OkHttpClient,
    private val scope: CoroutineScope,
    private val restBase: String = DEFAULT_REST_BASE,
    private val wsBase: String = DEFAULT_WS_BASE,
    private val logger: (String) -> Unit = {},
) : ExchangeAdapter {

    override val id: ExchangeId = ExchangeId.BINANCE

    override val nativeTimeframes: Set<Timeframe> = Timeframe.entries.toSet()

    private val json = Json { ignoreUnknownKeys = true }

    private val socketLock = Any()
    private var socket: ExchangeSocket? = null
    private val streamRefs = LinkedHashMap<String, Int>()
    private val commandId = AtomicInteger(0)

    // Pending stream commands, coalesced into as few frames as possible (see [queueCommand]).
    private val pendingLock = Any()
    private val pendingSubscribe = LinkedHashSet<String>()
    private val pendingUnsubscribe = LinkedHashSet<String>()
    private var flushJob: Job? = null

    // ---------------------------------------------------------------- REST

    // Every REST method runs the request *and* the JSON decode on Dispatchers.IO: callers may be on
    // the main thread, and exchangeInfo alone is several megabytes.
    override suspend fun listMarkets(): List<Market> = withContext(Dispatchers.IO) {
        val info = json.decodeFromString<ExchangeInfoDto>(get("/api/v3/exchangeInfo"))
        info.symbols.asSequence()
            .filter { it.status == STATUS_TRADING && it.isSpotTradingAllowed }
            .filter { SYMBOL_PART.matches(it.baseAsset) && SYMBOL_PART.matches(it.quoteAsset) }
            .map { dto ->
                val tickSize = dto.filters.firstOrNull { it.filterType == PRICE_FILTER }?.tickSize
                Market(
                    key = MarketKey.of(ExchangeId.BINANCE, dto.baseAsset, dto.quoteAsset),
                    nativeSymbol = dto.symbol,
                    pricePrecision = tickSize?.let(::decimalsOf) ?: dto.quotePrecision,
                    tickSize = tickSize?.toDoubleOrNull(),
                )
            }
            .toList()
    }

    override suspend fun fetchTickers(markets: List<Market>): List<Ticker> = withContext(Dispatchers.IO) {
        if (markets.isEmpty()) return@withContext emptyList()
        val byNativeSymbol = markets.associateBy { it.nativeSymbol }
        val tickers = ArrayList<Ticker>(markets.size)
        for (chunk in markets.chunked(TICKER_CHUNK)) {
            val symbols = chunk.joinToString(separator = ",", prefix = "[", postfix = "]") {
                "\"${it.nativeSymbol}\""
            }
            val body = get(PATH_TICKER_24H, listOf("symbols" to symbols, "type" to "MINI"))
            // Neither `24hr?type=MINI` nor `@miniTicker` carries a quote, so the best bid/ask comes
            // from one extra request per chunk (weight 4 for more than one symbol).
            val quotes = bookTickers(symbols)
            for (dto in json.decodeFromString<List<MiniTickerDto>>(body)) {
                val market = byNativeSymbol[dto.symbol] ?: continue
                tickers += dto.toTicker(market.key).withQuote(quotes[dto.symbol])
            }
        }
        tickers
    }

    /** Best bid/ask per native symbol; a failing book must not cost the whole ticker round. */
    private suspend fun bookTickers(symbols: String): Map<String, BookTickerDto> = try {
        json.decodeFromString<List<BookTickerDto>>(get(PATH_BOOK_TICKER, listOf("symbols" to symbols)))
            .associateBy { it.symbol }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logger("binance: bookTicker failed: ${e.message}")
        emptyMap()
    }

    override suspend fun fetchOHLCV(
        market: Market,
        timeframe: Timeframe,
        endTime: Long?,
        limit: Int,
    ): List<Candle> = withContext(Dispatchers.IO) {
        val query = mutableListOf(
            "symbol" to market.nativeSymbol,
            "interval" to intervalOf(timeframe),
            "limit" to limit.coerceIn(1, MAX_KLINES).toString(),
        )
        // `ExchangeAdapter.fetchOHLCV` documents endTime as exclusive while Binance filters
        // `openTime <= endTime`, so one millisecond less keeps the seam bar out of the next page
        // (Gate and KuCoin step back the same way).
        if (endTime != null) query += "endTime" to (endTime - 1).toString()
        val body = get(PATH_KLINES, query)
        val now = System.currentTimeMillis()
        json.parseToJsonElement(body).jsonArray.map { row ->
            val cells = row.jsonArray
            Candle(
                openTime = cells[IDX_OPEN_TIME].jsonPrimitive.long,
                open = cells[IDX_OPEN].jsonPrimitive.content.toDouble(),
                high = cells[IDX_HIGH].jsonPrimitive.content.toDouble(),
                low = cells[IDX_LOW].jsonPrimitive.content.toDouble(),
                close = cells[IDX_CLOSE].jsonPrimitive.content.toDouble(),
                volume = cells[IDX_VOLUME].jsonPrimitive.content.toDouble(),
                closed = cells[IDX_CLOSE_TIME].jsonPrimitive.long < now,
            )
        }
    }

    private suspend fun get(path: String, query: List<Pair<String, String>> = emptyList()): String =
        withContext(Dispatchers.IO) {
            val url = (restBase.trimEnd('/') + path).toHttpUrl().newBuilder()
            for ((name, value) in query) url.addQueryParameter(name, value)
            val request = Request.Builder().url(url.build()).get().build()
            client.newCall(request).execute().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) throw errorFor(response, body)
                body
            }
        }

    private fun errorFor(response: Response, body: String): Exception {
        val code = response.code
        val detail = body.take(ERROR_BODY_CHARS)
        return when (code) {
            HTTP_UNAVAILABLE_FOR_LEGAL_REASONS ->
                ExchangeUnavailableException(id, "Binance is unavailable in this region (HTTP $code): $detail")

            HTTP_TOO_MANY_REQUESTS, HTTP_IM_A_TEAPOT -> {
                val retryAfter = response.header("Retry-After")
                val suffix = if (retryAfter != null) ", retry after $retryAfter s" else ""
                ExchangeHttpException(id, code, "Binance rate limit hit (HTTP $code$suffix): $detail")
            }

            else -> ExchangeHttpException(id, code, "Binance request failed (HTTP $code): $detail")
        }
    }

    // ------------------------------------------------------------ WebSocket

    /**
     * `@miniTicker` (once a second, no quote) merged with `@bookTicker` (best bid/ask, ~10 frames a
     * second). The two are combined **here** because the live layer replaces the whole [Ticker] per
     * key, so a quote-only object would wipe last/high/low/volume; and quote frames are sampled down
     * to [QUOTE_PUSH_INTERVAL_NS] per market, before they are even decoded, so the extra stream
     * costs the repository at most one extra update per second per market.
     */
    override fun watchTickers(markets: List<Market>): Flow<Ticker> = channelFlow {
        val marketsByStream = LinkedHashMap<String, Market>()
        for (market in markets) {
            marketsByStream[tickerStream(market)] = market
            marketsByStream[bookStream(market)] = market
        }
        // An empty subscription parks instead of completing: the live layer treats completion as a drop.
        if (marketsByStream.isEmpty()) awaitCancellation()
        val (activeSocket, fresh) = acquire(marketsByStream.keys)
        // Both maps are only ever touched by this collector.
        val latest = HashMap<MarketKey, Ticker>()
        val lastQuoteAt = HashMap<MarketKey, Long>()
        try {
            activeSocket.messages
                .onSubscription { queueCommand(METHOD_SUBSCRIBE, fresh) }
                .collect { text ->
                    val frame = combinedFrame(text) ?: return@collect
                    val market = marketsByStream[frame.first] ?: return@collect
                    val ticker = if (frame.first.endsWith(BOOK_TICKER_SUFFIX)) {
                        // A quote alone renders nothing: wait for the first miniTicker of this market.
                        val base = latest[market.key] ?: return@collect
                        val now = System.nanoTime()
                        val previous = lastQuoteAt[market.key]
                        if (previous != null && now - previous < QUOTE_PUSH_INTERVAL_NS) return@collect
                        val event = decodeOrNull<BookTickerEvent>(frame.second) ?: return@collect
                        lastQuoteAt[market.key] = now
                        base.copy(bid = event.bidPrice.toDoubleOrNull(), ask = event.askPrice.toDoubleOrNull())
                    } else {
                        val event = decodeOrNull<MiniTickerEvent>(frame.second) ?: return@collect
                        val previous = latest[market.key]
                        event.toTicker(market.key, bid = previous?.bid, ask = previous?.ask) ?: return@collect
                    }
                    latest[market.key] = ticker
                    this@channelFlow.send(ticker)
                }
        } finally {
            release(marketsByStream.keys)
        }
    }

    override fun watchKlines(market: Market, timeframe: Timeframe): Flow<Candle> = channelFlow {
        val stream = klineStream(market, timeframe)
        val (activeSocket, fresh) = acquire(listOf(stream))
        try {
            activeSocket.messages
                .onSubscription { queueCommand(METHOD_SUBSCRIBE, fresh) }
                .collect { text ->
                    val frame = combinedFrame(text) ?: return@collect
                    if (frame.first != stream) return@collect
                    val event = decodeOrNull<KlineEvent>(frame.second) ?: return@collect
                    val candle = event.kline.toCandle() ?: return@collect
                    this@channelFlow.send(candle)
                }
        } finally {
            release(listOf(stream))
        }
    }

    /** Adds a reference to every stream and returns the shared socket plus the streams new to it. */
    private fun acquire(streams: Collection<String>): Pair<ExchangeSocket, List<String>> =
        synchronized(socketLock) {
            val fresh = ArrayList<String>()
            for (stream in streams) {
                val refs = streamRefs[stream] ?: 0
                if (refs == 0) fresh += stream
                streamRefs[stream] = refs + 1
            }
            val current = socket ?: createSocket().also {
                socket = it
                it.connect()
            }
            current to fresh
        }

    /** Drops a reference from every stream, unsubscribing and finally closing the socket. */
    private fun release(streams: Collection<String>) {
        val gone = ArrayList<String>()
        var current: ExchangeSocket? = null
        var closeSocket = false
        synchronized(socketLock) {
            for (stream in streams) {
                val refs = (streamRefs[stream] ?: 0) - 1
                if (refs <= 0) {
                    streamRefs.remove(stream)
                    gone += stream
                } else {
                    streamRefs[stream] = refs
                }
            }
            current = socket
            if (streamRefs.isEmpty() && current != null) {
                closeSocket = true
                socket = null
                // Cleared while still holding socketLock: a collector that acquires right after
                // this block must find an empty queue, otherwise its subscribe could be wiped here.
                synchronized(pendingLock) {
                    pendingSubscribe.clear()
                    pendingUnsubscribe.clear()
                    flushJob?.cancel()
                    flushJob = null
                }
            }
        }
        val target = current ?: return
        logger("binance: release ${streams.size} stream(s), ${streamRefs.size} left, closeSocket=$closeSocket")
        if (closeSocket) {
            // Nothing else is listening: drop the socket instead of unsubscribing stream by stream.
            target.close()
        } else if (gone.isNotEmpty()) {
            queueCommand(METHOD_UNSUBSCRIBE, gone)
        }
    }

    private fun createSocket(): ExchangeSocket {
        val created = ExchangeSocket(
            client = client,
            url = wsBase.trimEnd('/') + STREAM_PATH,
            name = ExchangeId.BINANCE.id,
            scope = scope,
            minSendGapMs = MIN_SEND_GAP_MS,
            logger = logger,
        )
        created.onReconnected = { resubscribeAll(created) }
        return created
    }

    /** After a reconnect the server has forgotten every stream: re-subscribe the whole ref-counted set. */
    private fun resubscribeAll(target: ExchangeSocket) {
        val streams = synchronized(socketLock) {
            // A discarded socket whose handshake completed late must not touch the live socket's queue.
            if (socket !== target) return
            streamRefs.keys.toList()
        }
        synchronized(pendingLock) {
            pendingUnsubscribe.clear()
            pendingSubscribe.clear()
            pendingSubscribe += streams
        }
        scheduleFlush()
    }

    /**
     * Queues a SUBSCRIBE/UNSUBSCRIBE for [streams]. Commands arriving within [COALESCE_WINDOW_MS]
     * (e.g. one `watchKlines` per tile on screen entry) are merged into a single frame per method,
     * and a stream that is subscribed and unsubscribed within the window cancels out. Frames are
     * then paced by the socket itself (Binance allows 5 incoming messages per second).
     */
    private fun queueCommand(method: String, streams: Collection<String>) {
        if (streams.isEmpty()) return
        synchronized(pendingLock) {
            val add = if (method == METHOD_SUBSCRIBE) pendingSubscribe else pendingUnsubscribe
            val cancel = if (method == METHOD_SUBSCRIBE) pendingUnsubscribe else pendingSubscribe
            for (stream in streams) if (!cancel.remove(stream)) add += stream
        }
        scheduleFlush()
    }

    private fun scheduleFlush() {
        synchronized(pendingLock) {
            if (flushJob?.isActive == true) return
            flushJob = scope.launch {
                delay(COALESCE_WINDOW_MS)
                flushPending()
            }
        }
    }

    private fun flushPending() {
        val subscribe: List<String>
        val unsubscribe: List<String>
        synchronized(pendingLock) {
            subscribe = pendingSubscribe.toList()
            unsubscribe = pendingUnsubscribe.toList()
            pendingSubscribe.clear()
            pendingUnsubscribe.clear()
            flushJob = null
        }
        val target = synchronized(socketLock) { socket } ?: return
        for (batch in unsubscribe.chunked(MAX_PARAMS_PER_MESSAGE)) target.send(commandFrame(METHOD_UNSUBSCRIBE, batch))
        for (batch in subscribe.chunked(MAX_PARAMS_PER_MESSAGE)) target.send(commandFrame(METHOD_SUBSCRIBE, batch))
    }

    private fun commandFrame(method: String, streams: List<String>): String = buildJsonObject {
        put("method", method)
        putJsonArray("params") { for (stream in streams) add(stream) }
        put("id", commandId.incrementAndGet())
    }.toString()

    private fun combinedFrame(text: String): Pair<String, JsonObject>? {
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return null
        val stream = (root["stream"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
        val data = root["data"] as? JsonObject ?: return null
        return stream to data
    }

    private inline fun <reified T> decodeOrNull(element: JsonObject): T? =
        runCatching { json.decodeFromJsonElement<T>(element) }.getOrNull()

    private fun tickerStream(market: Market): String =
        "${market.nativeSymbol.lowercase()}@$MINI_TICKER_STREAM"

    private fun bookStream(market: Market): String =
        "${market.nativeSymbol.lowercase()}$BOOK_TICKER_SUFFIX"

    private fun klineStream(market: Market, timeframe: Timeframe): String =
        "${market.nativeSymbol.lowercase()}@$KLINE_STREAM_PREFIX${intervalOf(timeframe)}"

    private fun intervalOf(timeframe: Timeframe): String = when (timeframe) {
        Timeframe.M1 -> "1m"
        Timeframe.M5 -> "5m"
        Timeframe.M15 -> "15m"
        Timeframe.M30 -> "30m"
        Timeframe.H1 -> "1h"
        Timeframe.H4 -> "4h"
        Timeframe.D1 -> "1d"
        Timeframe.W1 -> "1w"
        Timeframe.MN1 -> "1M"
    }

    /**
     * Decimals of a Binance tick size after trailing zeros are stripped:
     * `"0.01000000"` -> `2`, `"1.00000000"` -> `0`.
     */
    private fun decimalsOf(tickSize: String): Int {
        val dot = tickSize.indexOf('.')
        if (dot < 0) return 0
        var end = tickSize.length
        while (end > dot + 1 && tickSize[end - 1] == '0') end--
        return end - dot - 1
    }

    companion object {
        const val DEFAULT_REST_BASE: String = "https://data-api.binance.vision"
        const val DEFAULT_WS_BASE: String = "wss://data-stream.binance.vision"

        /** `/api/v3/ticker/24hr` costs weight 2 per chunk; Binance itself caps a call at 100 symbols. */
        private const val TICKER_CHUNK = 20
        private const val MAX_KLINES = 1000
        private const val MAX_PARAMS_PER_MESSAGE = 200

        /** Binance closes sockets that receive more than 5 messages per second. */
        private const val MIN_SEND_GAP_MS = 250L

        /** Stream commands issued within this window are merged into one frame. */
        private const val COALESCE_WINDOW_MS = 100L

        private const val STREAM_PATH = "/stream"
        private const val MINI_TICKER_STREAM = "miniTicker"
        private const val BOOK_TICKER_SUFFIX = "@bookTicker"
        private const val KLINE_STREAM_PREFIX = "kline_"

        /** `@bookTicker` pushes ~10 frames a second per symbol; the repository sees at most one. */
        private const val QUOTE_PUSH_INTERVAL_NS = 1_000_000_000L

        private const val PATH_TICKER_24H = "/api/v3/ticker/24hr"
        private const val PATH_BOOK_TICKER = "/api/v3/ticker/bookTicker"
        private const val PATH_KLINES = "/api/v3/klines"
        private const val METHOD_SUBSCRIBE = "SUBSCRIBE"
        private const val METHOD_UNSUBSCRIBE = "UNSUBSCRIBE"

        private const val STATUS_TRADING = "TRADING"
        private const val PRICE_FILTER = "PRICE_FILTER"
        private val SYMBOL_PART = Regex("[A-Za-z0-9]+")

        private const val ERROR_BODY_CHARS = 200
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val HTTP_IM_A_TEAPOT = 418
        private const val HTTP_UNAVAILABLE_FOR_LEGAL_REASONS = 451

        private const val IDX_OPEN_TIME = 0
        private const val IDX_OPEN = 1
        private const val IDX_HIGH = 2
        private const val IDX_LOW = 3
        private const val IDX_CLOSE = 4
        private const val IDX_VOLUME = 5
        private const val IDX_CLOSE_TIME = 6
    }
}

// ------------------------------------------------------------------- DTOs

@Serializable
private data class ExchangeInfoDto(val symbols: List<SymbolDto> = emptyList())

@Serializable
private data class SymbolDto(
    val symbol: String,
    val status: String = "",
    val baseAsset: String = "",
    val quoteAsset: String = "",
    val quotePrecision: Int = 8,
    val isSpotTradingAllowed: Boolean = false,
    val filters: List<FilterDto> = emptyList(),
)

@Serializable
private data class FilterDto(val filterType: String = "", val tickSize: String? = null)

@Serializable
private data class MiniTickerDto(
    val symbol: String,
    val lastPrice: String = "0",
    val openPrice: String = "0",
    val highPrice: String = "0",
    val lowPrice: String = "0",
    val volume: String = "0",
    val quoteVolume: String = "0",
    val closeTime: Long = 0,
)

/** `/api/v3/ticker/bookTicker` row: the top of the book for one symbol. */
@Serializable
private data class BookTickerDto(
    val symbol: String,
    val bidPrice: String = "0",
    val askPrice: String = "0",
)

/** `<symbol>@bookTicker` frame: `b`/`a` are the best bid and ask prices. */
@Serializable
private data class BookTickerEvent(
    @SerialName("s") val symbol: String = "",
    @SerialName("b") val bidPrice: String = "0",
    @SerialName("a") val askPrice: String = "0",
)

@Serializable
private data class MiniTickerEvent(
    @SerialName("s") val symbol: String = "",
    @SerialName("c") val closePrice: String = "0",
    @SerialName("o") val openPrice: String = "0",
    @SerialName("h") val highPrice: String = "0",
    @SerialName("l") val lowPrice: String = "0",
    @SerialName("v") val baseVolume: String = "0",
    @SerialName("q") val quoteVolume: String = "0",
    @SerialName("E") val eventTime: Long = 0,
)

@Serializable
private data class KlineEvent(@SerialName("k") val kline: KlineDto)

@Serializable
private data class KlineDto(
    @SerialName("t") val openTime: Long = 0,
    @SerialName("o") val openPrice: String = "0",
    @SerialName("h") val highPrice: String = "0",
    @SerialName("l") val lowPrice: String = "0",
    @SerialName("c") val closePrice: String = "0",
    @SerialName("v") val baseVolume: String = "0",
    @SerialName("x") val isClosed: Boolean = false,
)

private const val PERCENT = 100.0

private fun changePct(last: Double, open: Double): Double? =
    if (open == 0.0) null else (last - open) / open * PERCENT

private fun MiniTickerDto.toTicker(key: MarketKey): Ticker {
    val last = lastPrice.toDouble()
    val open = openPrice.toDouble()
    return Ticker(
        key = key,
        last = last,
        open24h = open,
        high24h = highPrice.toDouble(),
        low24h = lowPrice.toDouble(),
        volumeBase24h = volume.toDouble(),
        volumeQuote24h = quoteVolume.toDouble(),
        changePct24h = changePct(last, open),
        timestamp = closeTime,
    )
}

/** [bid]/[ask] carry over the last `@bookTicker` quote, which `@miniTicker` frames never contain. */
private fun MiniTickerEvent.toTicker(key: MarketKey, bid: Double?, ask: Double?): Ticker? {
    val last = closePrice.toDoubleOrNull() ?: return null
    val open = openPrice.toDoubleOrNull()
    return Ticker(
        key = key,
        last = last,
        open24h = open,
        high24h = highPrice.toDoubleOrNull(),
        low24h = lowPrice.toDoubleOrNull(),
        volumeBase24h = baseVolume.toDoubleOrNull(),
        volumeQuote24h = quoteVolume.toDoubleOrNull(),
        changePct24h = open?.let { changePct(last, it) },
        bid = bid,
        ask = ask,
        timestamp = eventTime,
    )
}

private fun Ticker.withQuote(quote: BookTickerDto?): Ticker =
    if (quote == null) this else copy(bid = quote.bidPrice.toDoubleOrNull(), ask = quote.askPrice.toDoubleOrNull())

private fun KlineDto.toCandle(): Candle? = Candle(
    openTime = openTime,
    open = openPrice.toDoubleOrNull() ?: return null,
    high = highPrice.toDoubleOrNull() ?: return null,
    low = lowPrice.toDoubleOrNull() ?: return null,
    close = closePrice.toDoubleOrNull() ?: return null,
    volume = baseVolume.toDoubleOrNull() ?: return null,
    closed = isClosed,
)
