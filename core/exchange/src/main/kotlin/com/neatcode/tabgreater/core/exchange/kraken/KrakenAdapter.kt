package com.neatcode.tabgreater.core.exchange.kraken

import com.neatcode.tabgreater.core.exchange.ExchangeAdapter
import com.neatcode.tabgreater.core.exchange.ExchangeHttpException
import com.neatcode.tabgreater.core.exchange.ExchangeUnavailableException
import com.neatcode.tabgreater.core.exchange.ohlc.CandleAggregator
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

/**
 * Kraken spot adapter: public REST v0 (`/0/public/...`) plus the v2 WebSocket.
 *
 * Kraken uses three different names for the same market, and all three are handled here so that
 * only [MarketKey] leaves the adapter:
 *  - the REST pair id (`XXBTZEUR`) — stored as [Market.nativeSymbol] and used in every REST call;
 *  - `wsname` (`XBT/EUR`) — the v1 name, used only to derive base/quote (with the `XBT`/`XDG`
 *    aliases applied);
 *  - the v2 WebSocket symbol (`BTC/EUR`) — identical to our canonical `BASE/QUOTE`, so it is
 *    rebuilt from the key; subscribing with `XBT/EUR` is rejected by the server.
 *
 * REST work runs on [Dispatchers.IO] and is paced by a single [TokenBucket] shared by all calls
 * (Kraken tolerates roughly one public request per second before it throttles the IP). Live data
 * goes through one [ExchangeSocket] shared by every collector of this adapter and reference-counted
 * per subscription key, so the socket exists only while something is collecting.
 */
class KrakenAdapter(
    private val client: OkHttpClient,
    private val scope: CoroutineScope,
    private val restBase: String = DEFAULT_REST_BASE,
    private val wsBase: String = DEFAULT_WS_BASE,
    private val logger: (String) -> Unit = {},
    private val restBucket: TokenBucket = TokenBucket(REST_CAPACITY, REST_REFILL_PER_SECOND),
    private val pingIntervalMs: Long = PING_INTERVAL_MS,
) : ExchangeAdapter {

    override val id: ExchangeId = ExchangeId.KRAKEN

    /** Kraken's coarsest native bar is 2 weeks (21600 min); `1M` is aggregated from `1d`. */
    override val nativeTimeframes: Set<Timeframe> = INTERVAL_MINUTES.keys

    private val json = Json { ignoreUnknownKeys = true }

    private val socketLock = Any()
    private var socket: ExchangeSocket? = null
    private var socketJobs: Job? = null
    private val requestId = AtomicInteger(0)

    private val book = SubscriptionBook(scope) { subscribe, unsubscribe -> flush(subscribe, unsubscribe) }

    // ---------------------------------------------------------------- REST

    override suspend fun listMarkets(): List<Market> = withContext(Dispatchers.IO) {
        val result = getResult(PATH_ASSET_PAIRS)
        json.decodeFromJsonElement<Map<String, AssetPairDto>>(result)
            .mapNotNull { (nativeSymbol, dto) -> dto.toMarket(nativeSymbol) }
    }

    override suspend fun fetchTickers(markets: List<Market>): List<Ticker> = withContext(Dispatchers.IO) {
        if (markets.isEmpty()) return@withContext emptyList()
        val byNativeSymbol = markets.associateBy { it.nativeSymbol }
        val tickers = ArrayList<Ticker>(markets.size)
        for (chunk in markets.chunked(TICKER_CHUNK)) {
            val pairs = chunk.joinToString(separator = ",") { it.nativeSymbol }
            val result = getResult(PATH_TICKER, listOf(PARAM_PAIR to pairs))
            for ((nativeSymbol, dto) in json.decodeFromJsonElement<Map<String, KrakenTickerDto>>(result)) {
                val market = byNativeSymbol[nativeSymbol] ?: continue
                tickers += dto.toTicker(market.key) ?: continue
            }
        }
        tickers
    }

    override suspend fun fetchOHLCV(
        market: Market,
        timeframe: Timeframe,
        endTime: Long?,
        limit: Int,
    ): List<Candle> = withContext(Dispatchers.IO) {
        val take = limit.coerceIn(1, MAX_CANDLES)
        if (timeframe in nativeTimeframes) return@withContext fetchBars(market, timeframe, endTime, take)
        // 1M is the only non-native timeframe; CandleAggregator picks 1d as its finest exact divisor.
        val source = CandleAggregator.sourceFor(timeframe, nativeTimeframes)
            ?: throw IllegalArgumentException("Kraken cannot build ${timeframe.id} bars")
        val bars = fetchBars(market, source, endTime, MAX_CANDLES)
        CandleAggregator.aggregate(bars, source, timeframe).takeLast(take)
    }

    /**
     * One OHLC request. Kraken has neither a `limit` nor a paging parameter: it always answers with
     * the newest ~720 bars (older history is simply not retrievable), and `since` only trims the
     * head, so the window is applied client-side.
     */
    private suspend fun fetchBars(
        market: Market,
        timeframe: Timeframe,
        endTime: Long?,
        take: Int,
    ): List<Candle> {
        val minutes = INTERVAL_MINUTES.getValue(timeframe)
        val result = getResult(
            PATH_OHLC,
            listOf(PARAM_PAIR to market.nativeSymbol, PARAM_INTERVAL to minutes.toString()),
        )
        val rows = (result[market.nativeSymbol] ?: result.entries.firstOrNull { it.key != KEY_LAST }?.value)
            as? JsonArray ?: return emptyList()
        // `last` is the open time of the newest *committed* bar; everything after it is still forming.
        val lastCommitted = (result[KEY_LAST] as? JsonPrimitive)?.longOrNull ?: newestForming(rows)
        val candles = rows.mapNotNull { row -> row.toCandle(lastCommitted) }
        val bounded = if (endTime == null) candles else candles.filter { it.openTime < endTime }
        return bounded.takeLast(take)
    }

    /**
     * Fallback for a response whose `last` is missing or not a number: the newest row is the bar
     * Kraken is still building, so everything before it counts as committed. Guessing the other way
     * would hand out a half-built bar as final, and a closed bar is cached and never refetched.
     * Bar times are whole seconds, so "one second before the newest row" excludes exactly that row.
     */
    private fun newestForming(rows: JsonArray): Long {
        val newest = rows.mapNotNull { it.rowTimeSeconds() }.maxOrNull() ?: return Long.MIN_VALUE
        return newest - 1
    }

    /** Open time of an OHLC row in seconds, or `null` when the row is not shaped like one. */
    private fun JsonElement.rowTimeSeconds(): Long? =
        ((this as? JsonArray)?.getOrNull(IDX_TIME) as? JsonPrimitive)?.longOrNull

    /** Row layout: `[time_sec, open, high, low, close, vwap, volume, count]`. */
    private fun JsonElement.toCandle(lastCommittedSeconds: Long): Candle? {
        val cells = this as? JsonArray ?: return null
        if (cells.size <= IDX_VOLUME) return null
        val timeSeconds = rowTimeSeconds() ?: return null
        return Candle(
            openTime = timeSeconds * MILLIS_PER_SECOND,
            open = cells[IDX_OPEN].jsonPrimitive.content.toDoubleOrNull() ?: return null,
            high = cells[IDX_HIGH].jsonPrimitive.content.toDoubleOrNull() ?: return null,
            low = cells[IDX_LOW].jsonPrimitive.content.toDoubleOrNull() ?: return null,
            close = cells[IDX_CLOSE].jsonPrimitive.content.toDoubleOrNull() ?: return null,
            volume = cells[IDX_VOLUME].jsonPrimitive.content.toDoubleOrNull() ?: return null,
            closed = timeSeconds <= lastCommittedSeconds,
        )
    }

    private fun AssetPairDto.toMarket(nativeSymbol: String): Market? {
        if (status != STATUS_ONLINE) return null
        val name = wsname ?: return null
        val slash = name.indexOf('/')
        if (slash <= 0 || slash == name.lastIndex) return null
        val base = alias(name.substring(0, slash))
        val quote = alias(name.substring(slash + 1))
        if (!SYMBOL_PART.matches(base) || !SYMBOL_PART.matches(quote)) return null
        return Market(
            key = MarketKey.of(ExchangeId.KRAKEN, base, quote),
            nativeSymbol = nativeSymbol,
            pricePrecision = pairDecimals,
            tickSize = tickSize?.toDoubleOrNull(),
        )
    }

    private fun alias(asset: String): String = ASSET_ALIASES[asset] ?: asset

    /**
     * `o` is Kraken's *today* open (since 00:00 UTC), the only open REST exposes — there is no
     * rolling 24 h open, so neither `open24h` nor `changePct24h` is filled from REST: a "24 h"
     * change measured since midnight reads `0.00%` right after the day rolls over, and the widget
     * service in a timed mode never gets the v2 stream's rolling `change_pct` to correct it.
     * Consumers fall back to the 24 h candle window instead (tiles: `windowChange`; widget:
     * `WidgetModelFactory`), and the socket fills the rolling figure in when it is connected.
     */
    private fun KrakenTickerDto.toTicker(key: MarketKey): Ticker? {
        val last = lastTrade.firstOrNull()?.toDoubleOrNull() ?: return null
        val volume = volume24h.getOrNull(IDX_ROLLING_24H)?.toDoubleOrNull()
        val vwap = vwap24h.getOrNull(IDX_ROLLING_24H)?.toDoubleOrNull()
        return Ticker(
            key = key,
            last = last,
            open24h = null,
            high24h = high24h.getOrNull(IDX_ROLLING_24H)?.toDoubleOrNull(),
            low24h = low24h.getOrNull(IDX_ROLLING_24H)?.toDoubleOrNull(),
            volumeBase24h = volume,
            volumeQuote24h = if (volume != null && vwap != null) volume * vwap else null,
            changePct24h = null,
            bid = bid.firstOrNull()?.toDoubleOrNull(),
            ask = ask.firstOrNull()?.toDoubleOrNull(),
            timestamp = System.currentTimeMillis(),
        )
    }

    private fun changePct(last: Double, open: Double): Double? =
        if (open == 0.0) null else (last - open) / open * PERCENT

    /**
     * Kraken always answers `{"error":[...],"result":{...}}`, including for failures that arrive
     * with HTTP 200. Messages starting with `E` are hard errors, `W` ones are warnings that come
     * with a usable result.
     */
    private suspend fun getResult(path: String, query: List<Pair<String, String>> = emptyList()): JsonObject {
        val (code, body) = get(path, query)
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject
            ?: throw ExchangeHttpException(id, code, "Kraken sent a malformed body: ${body.take(ERROR_BODY_CHARS)}")
        val messages = (root[KEY_ERROR] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            .orEmpty()
        val (fatal, warnings) = messages.partition { it.startsWith(ERROR_MARKER) }
        if (warnings.isNotEmpty()) logger("kraken: $path warned ${warnings.joinToString()}")
        val result = root[KEY_RESULT] as? JsonObject
        if (fatal.isEmpty() && result != null) return result
        throw krakenError(code, fatal.ifEmpty { messages }.joinToString().ifEmpty { NO_RESULT })
    }

    /**
     * `EGeneral:Too many requests` is Kraken's throttle response, and it arrives inside an HTTP 200
     * body. The exception keeps the status the server actually sent — [ExchangeHttpException.code]
     * is always the real HTTP code — so only the message tells callers this was a rate limit.
     */
    private fun krakenError(code: Int, detail: String): Exception {
        val text = detail.take(ERROR_BODY_CHARS)
        return if (text.contains(RATE_LIMIT_MARKER, ignoreCase = true)) {
            ExchangeHttpException(id, code, "Kraken rate limit hit ($text)")
        } else {
            ExchangeHttpException(id, code, "Kraken error: $text")
        }
    }

    /** Status plus body of a 2xx response — Kraken reports its own failures inside a successful one. */
    private suspend fun get(path: String, query: List<Pair<String, String>>): Pair<Int, String> =
        withContext(Dispatchers.IO) {
            restBucket.acquire()
            val url = (restBase.trimEnd('/') + path).toHttpUrl().newBuilder()
            for ((name, value) in query) url.addQueryParameter(name, value)
            val request = Request.Builder().url(url.build()).get().build()
            client.newCall(request).execute().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) throw errorFor(response, body)
                response.code to body
            }
        }

    private fun errorFor(response: Response, body: String): Exception {
        val code = response.code
        val detail = body.take(ERROR_BODY_CHARS)
        return when (code) {
            HTTP_UNAVAILABLE_FOR_LEGAL_REASONS ->
                ExchangeUnavailableException(id, "Kraken is unavailable in this region (HTTP $code): $detail")

            HTTP_TOO_MANY_REQUESTS -> {
                val retryAfter = response.header(HEADER_RETRY_AFTER)
                val suffix = if (retryAfter != null) ", retry after $retryAfter s" else ""
                ExchangeHttpException(id, code, "Kraken rate limit hit (HTTP $code$suffix): $detail")
            }

            else -> ExchangeHttpException(id, code, "Kraken request failed (HTTP $code): $detail")
        }
    }

    // ------------------------------------------------------------ WebSocket

    override fun watchTickers(markets: List<Market>): Flow<Ticker> = channelFlow {
        val bySymbol = LinkedHashMap<String, Market>()
        for (market in markets) bySymbol[wsSymbol(market)] = market
        // An empty subscription parks instead of completing: the live layer treats completion as a drop.
        if (bySymbol.isEmpty()) awaitCancellation()
        val keys = bySymbol.keys.map(::tickerKey)
        val (activeSocket, fresh) = acquire(keys)
        try {
            activeSocket.messages
                .onSubscription { book.queueSubscribe(fresh) }
                .collect { text ->
                    for (dto in channelData<KrakenWsTickerDto>(text, CHANNEL_TICKER)) {
                        val market = bySymbol[dto.symbol] ?: continue
                        this@channelFlow.send(dto.toTicker(market.key) ?: continue)
                    }
                }
        } finally {
            release(keys)
        }
    }

    override fun watchKlines(market: Market, timeframe: Timeframe): Flow<Candle> =
        if (timeframe in nativeTimeframes) nativeKlines(market, timeframe) else aggregatedKlines(market, timeframe)

    /**
     * Kraken never flags a bar as finished: every frame is the current state of the bucket named by
     * `interval_begin`. A bar is therefore emitted as forming until a frame for a later bucket
     * arrives, at which point the previous one is re-emitted as closed.
     */
    private fun nativeKlines(market: Market, timeframe: Timeframe): Flow<Candle> = channelFlow {
        val symbol = wsSymbol(market)
        val minutes = INTERVAL_MINUTES.getValue(timeframe)
        val keys = listOf(ohlcKey(minutes, symbol))
        val (activeSocket, fresh) = acquire(keys)
        var forming: Candle? = null
        try {
            activeSocket.messages
                .onSubscription { book.queueSubscribe(fresh) }
                .collect { text ->
                    for (dto in channelData<KrakenWsOhlcDto>(text, CHANNEL_OHLC)) {
                        if (dto.symbol != symbol || dto.interval != minutes) continue
                        val candle = dto.toCandle() ?: continue
                        val previous = forming
                        if (previous != null && candle.openTime > previous.openTime) {
                            this@channelFlow.send(previous.copy(closed = true))
                        }
                        forming = candle
                        this@channelFlow.send(candle)
                    }
                }
        } finally {
            release(keys)
        }
    }

    /**
     * Kraken has no `1M` channel, so the finest exact divisor (`1d`) is streamed instead and the
     * target bar is re-aggregated on every update. The bucket is seeded over REST once, otherwise
     * the first emitted bar would only cover the days seen since subscribing.
     */
    private fun aggregatedKlines(market: Market, timeframe: Timeframe): Flow<Candle> = channelFlow {
        val source = CandleAggregator.sourceFor(timeframe, nativeTimeframes)
            ?: error("Kraken cannot build ${timeframe.id} bars")
        val symbol = wsSymbol(market)
        val minutes = INTERVAL_MINUTES.getValue(source)
        val keys = listOf(ohlcKey(minutes, symbol))
        val sources = LinkedHashMap<Long, Candle>()
        var bucket = Long.MIN_VALUE
        for (candle in seedBars(market, source, timeframe)) {
            val start = CandleAggregator.bucketStart(candle.openTime, timeframe)
            if (start > bucket) {
                bucket = start
                sources.clear()
            }
            if (start == bucket) sources[candle.openTime] = candle
        }
        val (activeSocket, fresh) = acquire(keys)
        try {
            activeSocket.messages
                .onSubscription { book.queueSubscribe(fresh) }
                .collect { text ->
                    for (dto in channelData<KrakenWsOhlcDto>(text, CHANNEL_OHLC)) {
                        if (dto.symbol != symbol || dto.interval != minutes) continue
                        val candle = dto.toCandle() ?: continue
                        val start = CandleAggregator.bucketStart(candle.openTime, timeframe)
                        if (start < bucket) continue
                        if (start > bucket) {
                            merge(sources, source, timeframe, closed = true)?.let { this@channelFlow.send(it) }
                            sources.clear()
                            bucket = start
                        }
                        sources[candle.openTime] = candle
                        merge(sources, source, timeframe, closed = false)?.let { this@channelFlow.send(it) }
                    }
                }
        } finally {
            release(keys)
        }
    }

    /** Source bars of the bucket that is currently forming; a failure only costs accuracy, not the stream. */
    private suspend fun seedBars(market: Market, source: Timeframe, target: Timeframe): List<Candle> = try {
        fetchOHLCV(market, source, null, (target.millis / source.millis).toInt() + 1)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logger("kraken: ${target.id} seed failed ${e::class.java.simpleName}: ${e.message}")
        emptyList()
    }

    private fun merge(sources: Map<Long, Candle>, from: Timeframe, to: Timeframe, closed: Boolean): Candle? {
        if (sources.isEmpty()) return null
        val ordered = sources.values.sortedBy { it.openTime }
        val merged = CandleAggregator.aggregate(ordered, from, to).lastOrNull() ?: return null
        return if (merged.closed == closed) merged else merged.copy(closed = closed)
    }

    /** Adds a reference to every key and returns the shared socket plus the keys new to it. */
    private fun acquire(keys: Collection<String>): Pair<ExchangeSocket, List<String>> =
        synchronized(socketLock) {
            val acquired = book.acquire(keys)
            val current = socket ?: createSocket().also {
                socket = it
                socketJobs = startSocketJobs(it)
                it.connect()
            }
            logger("kraken: acquire ${keys.size} key(s), ${acquired.fresh.size} new, socketWasIdle=${acquired.wasEmpty}")
            current to acquired.fresh
        }

    /** Drops a reference from every key, unsubscribing and finally closing the socket. */
    private fun release(keys: Collection<String>) {
        val released: SubscriptionBook.Released
        val target: ExchangeSocket?
        val jobs: Job?
        synchronized(socketLock) {
            released = book.release(keys)
            target = socket
            if (released.isEmpty) {
                jobs = socketJobs
                socket = null
                socketJobs = null
                // Under the lock: a collector arriving in parallel takes the same lock, so it either
                // still holds a reference here (and the socket survives) or queues its subscribe
                // after this clear — never before it, which would drop the subscribe silently.
                book.clearPending()
            } else {
                jobs = null
            }
        }
        val current = target ?: return
        if (released.isEmpty) {
            // Nothing else is listening: drop the socket instead of unsubscribing key by key.
            jobs?.cancel()
            current.close()
            logger("kraken: last collector left, socket closed")
        } else if (released.gone.isNotEmpty()) {
            book.queueUnsubscribe(released.gone)
        }
    }

    private fun createSocket(): ExchangeSocket {
        val created = ExchangeSocket(
            client = client,
            url = wsBase.trimEnd('/'),
            name = ExchangeId.KRAKEN.id,
            scope = scope,
            maxLifetimeMs = NO_RECYCLE,
            pingIntervalMs = null,
            minSendGapMs = MIN_SEND_GAP_MS,
            logger = logger,
        )
        created.onReconnected = {
            // A discarded socket whose handshake completed late must not touch the live socket's queue.
            if (synchronized(socketLock) { socket === created }) book.resubscribeAll()
        }
        return created
    }

    /**
     * Kraken hangs up on connections idle for about a minute and its heartbeats only flow while
     * something is subscribed, so the adapter pings itself. The same job logs rejected commands
     * (`{"error":"Currency pair not supported ..."}`), which are never fatal.
     */
    private fun startSocketJobs(target: ExchangeSocket): Job = scope.launch {
        launch {
            while (isActive) {
                delay(pingIntervalMs)
                // Only meaningful on a live connection; queueing pings through an outage would push
                // them ahead of the resubscribe frames the reconnect issues.
                if (target.state.value == SocketState.OPEN) target.send(pingFrame())
            }
        }
        launch {
            target.messages.collect { text -> logRejection(text) }
        }
    }

    private fun logRejection(text: String) {
        // Heartbeats arrive every second and ticker frames per update; only rejections are parsed.
        if (!text.contains(ERROR_FIELD_TOKEN)) return
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return
        val error = (root[FIELD_ERROR] as? JsonPrimitive)?.contentOrNull ?: return
        val method = (root[FIELD_METHOD] as? JsonPrimitive)?.contentOrNull
        logger("kraken: $method rejected: $error")
    }

    private fun flush(subscribe: List<String>, unsubscribe: List<String>) {
        val target = synchronized(socketLock) { socket } ?: return
        for (frame in frames(METHOD_UNSUBSCRIBE, unsubscribe)) target.send(frame)
        for (frame in frames(METHOD_SUBSCRIBE, subscribe)) target.send(frame)
    }

    /**
     * Turns subscription keys (`ticker:BTC/EUR`, `ohlc:15:BTC/EUR`) into as few frames as possible:
     * one per channel and OHLC interval, with the symbols batched [SYMBOLS_PER_FRAME] at a time.
     */
    private fun frames(method: String, keys: List<String>): List<String> {
        if (keys.isEmpty()) return emptyList()
        val groups = LinkedHashMap<Pair<String, Int?>, MutableList<String>>()
        for (key in keys) {
            val parts = key.split(KEY_SEPARATOR)
            when {
                parts.size == OHLC_KEY_PARTS && parts[0] == CHANNEL_OHLC ->
                    groups.getOrPut(CHANNEL_OHLC to parts[1].toIntOrNull()) { ArrayList() } += parts[2]

                parts.size == TICKER_KEY_PARTS ->
                    groups.getOrPut(parts[0] to null) { ArrayList() } += parts[1]

                else -> logger("kraken: ignoring malformed subscription key $key")
            }
        }
        val out = ArrayList<String>(groups.size)
        for ((group, symbols) in groups) {
            val (channel, interval) = group
            for (batch in symbols.chunked(SYMBOLS_PER_FRAME)) out += commandFrame(method, channel, interval, batch)
        }
        return out
    }

    private fun commandFrame(method: String, channel: String, interval: Int?, symbols: List<String>): String =
        buildJsonObject {
            put(FIELD_METHOD, method)
            putJsonObject(FIELD_PARAMS) {
                put(FIELD_CHANNEL, channel)
                putJsonArray(FIELD_SYMBOL) { for (symbol in symbols) add(symbol) }
                if (interval != null) put(FIELD_INTERVAL, interval)
                // An OHLC snapshot would push hundreds of bars per pair; REST already seeded them.
                if (method == METHOD_SUBSCRIBE) put(FIELD_SNAPSHOT, channel == CHANNEL_TICKER)
            }
            put(FIELD_REQ_ID, requestId.incrementAndGet())
        }.toString()

    private fun pingFrame(): String = buildJsonObject {
        put(FIELD_METHOD, METHOD_PING)
        put(FIELD_REQ_ID, requestId.incrementAndGet())
    }.toString()

    /** Payload of a `{"channel":...,"data":[...]}` frame, or empty for anything else (acks, heartbeats, junk). */
    private inline fun <reified T> channelData(text: String, channel: String): List<T> {
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return emptyList()
        if ((root[FIELD_CHANNEL] as? JsonPrimitive)?.contentOrNull != channel) return emptyList()
        val data = root[FIELD_DATA] as? JsonArray ?: return emptyList()
        return runCatching { json.decodeFromJsonElement<List<T>>(data) }.getOrNull().orEmpty()
    }

    /**
     * `change` is the rolling 24 h move, which is exactly the open REST cannot give us. A frame
     * without `last` carries no price at all, so it is dropped rather than reported as 0.
     */
    private fun KrakenWsTickerDto.toTicker(key: MarketKey): Ticker? {
        val last = last ?: return null
        val open = change?.let { last - it }
        return Ticker(
            key = key,
            last = last,
            open24h = open,
            high24h = high,
            low24h = low,
            volumeBase24h = volume,
            volumeQuote24h = if (volume != null && vwap != null) volume * vwap else null,
            changePct24h = changePct ?: open?.let { changePct(last, it) },
            bid = bid,
            ask = ask,
            timestamp = epochMillisOf(timestamp),
        )
    }

    /** A frame missing any price column is not a usable bar; zeros would break the chart's scale. */
    private fun KrakenWsOhlcDto.toCandle(): Candle? = Candle(
        openTime = runCatching { Instant.parse(intervalBegin).toEpochMilli() }.getOrNull() ?: return null,
        open = open ?: return null,
        high = high ?: return null,
        low = low ?: return null,
        close = close ?: return null,
        volume = volume ?: return null,
        closed = false,
    )

    /** Kraken timestamps are ISO-8601 with nanoseconds; some ticker updates omit the field entirely. */
    private fun epochMillisOf(timestamp: String?): Long =
        timestamp?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: System.currentTimeMillis()

    private fun wsSymbol(market: Market): String = "${market.key.base}/${market.key.quote}"

    private fun tickerKey(symbol: String): String = "$CHANNEL_TICKER$KEY_SEPARATOR$symbol"

    private fun ohlcKey(minutes: Int, symbol: String): String =
        "$CHANNEL_OHLC$KEY_SEPARATOR$minutes$KEY_SEPARATOR$symbol"

    companion object {
        const val DEFAULT_REST_BASE: String = "https://api.kraken.com"
        const val DEFAULT_WS_BASE: String = "wss://ws.kraken.com/v2"

        /** "1 request per second or less" — exceeding it locks the IP out for several seconds. */
        private const val REST_CAPACITY = 1.0
        private const val REST_REFILL_PER_SECOND = 1.0

        /** Kraken closes idle sockets after roughly a minute. */
        private const val PING_INTERVAL_MS = 30_000L

        /** No documented 24 h connection recycle, so sessions are kept until they drop. */
        private const val NO_RECYCLE = 0L
        private const val MIN_SEND_GAP_MS = 100L
        private const val SYMBOLS_PER_FRAME = 50

        private const val PATH_ASSET_PAIRS = "/0/public/AssetPairs"
        private const val PATH_TICKER = "/0/public/Ticker"
        private const val PATH_OHLC = "/0/public/OHLC"
        private const val PARAM_PAIR = "pair"
        private const val PARAM_INTERVAL = "interval"

        /** One `Ticker` call handles every pair we ask for; chunked anyway to keep the URL sane. */
        private const val TICKER_CHUNK = 100

        /** `OHLC` returns at most 720 bars and offers no paging. */
        private const val MAX_CANDLES = 720
        private const val MILLIS_PER_SECOND = 1000L
        private const val PERCENT = 100.0

        private const val STATUS_ONLINE = "online"
        private val SYMBOL_PART = Regex("[A-Za-z0-9]+")

        /** Kraken's own names for two assets everybody else spells differently. */
        private val ASSET_ALIASES = mapOf("XBT" to "BTC", "XDG" to "DOGE")

        private val INTERVAL_MINUTES = mapOf(
            Timeframe.M1 to 1,
            Timeframe.M5 to 5,
            Timeframe.M15 to 15,
            Timeframe.M30 to 30,
            Timeframe.H1 to 60,
            Timeframe.H4 to 240,
            Timeframe.D1 to 1440,
            Timeframe.W1 to 10080,
        )

        private const val KEY_ERROR = "error"
        private const val KEY_RESULT = "result"
        private const val KEY_LAST = "last"
        private const val KEY_SEPARATOR = ":"
        private const val TICKER_KEY_PARTS = 2
        private const val OHLC_KEY_PARTS = 3

        private const val CHANNEL_TICKER = "ticker"
        private const val CHANNEL_OHLC = "ohlc"
        private const val METHOD_SUBSCRIBE = "subscribe"
        private const val METHOD_UNSUBSCRIBE = "unsubscribe"
        private const val METHOD_PING = "ping"

        private const val FIELD_METHOD = "method"
        private const val FIELD_PARAMS = "params"
        private const val FIELD_CHANNEL = "channel"
        private const val FIELD_SYMBOL = "symbol"
        private const val FIELD_INTERVAL = "interval"
        private const val FIELD_SNAPSHOT = "snapshot"
        private const val FIELD_REQ_ID = "req_id"
        private const val FIELD_DATA = "data"
        private const val FIELD_ERROR = "error"

        /** Cheap prefilter: only command rejections carry an `error` field. */
        private const val ERROR_FIELD_TOKEN = "\"error\""

        private const val ERROR_MARKER = "E"
        private const val RATE_LIMIT_MARKER = "Too many requests"
        private const val NO_RESULT = "response without a result"
        private const val ERROR_BODY_CHARS = 200
        private const val HEADER_RETRY_AFTER = "Retry-After"
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val HTTP_UNAVAILABLE_FOR_LEGAL_REASONS = 451

        /** Columns of an OHLC row and of the `[today, last 24 h]` ticker arrays. */
        private const val IDX_TIME = 0
        private const val IDX_OPEN = 1
        private const val IDX_HIGH = 2
        private const val IDX_LOW = 3
        private const val IDX_CLOSE = 4
        private const val IDX_VOLUME = 6
        private const val IDX_ROLLING_24H = 1
    }
}

// ------------------------------------------------------------------- DTOs

@Serializable
private data class AssetPairDto(
    /** v1 WebSocket name (`XBT/EUR`); the only field that carries base and quote separately. */
    val wsname: String? = null,
    @SerialName("pair_decimals") val pairDecimals: Int = 0,
    @SerialName("tick_size") val tickSize: String? = null,
    val status: String = "",
)

/** REST ticker: every array is `[today, last 24 h]`, `o` is today's open. */
@Serializable
private data class KrakenTickerDto(
    @SerialName("a") val ask: List<String> = emptyList(),
    @SerialName("b") val bid: List<String> = emptyList(),
    @SerialName("c") val lastTrade: List<String> = emptyList(),
    @SerialName("v") val volume24h: List<String> = emptyList(),
    @SerialName("p") val vwap24h: List<String> = emptyList(),
    @SerialName("h") val high24h: List<String> = emptyList(),
    @SerialName("l") val low24h: List<String> = emptyList(),
    @SerialName("o") val open24h: String = "0",
)

/**
 * v2 `ticker` channel payload; unlike REST these are JSON numbers. Every price is nullable: a frame
 * that omits one must be skipped, never defaulted to `0.0` (which would read as a real price).
 */
@Serializable
private data class KrakenWsTickerDto(
    val symbol: String = "",
    val last: Double? = null,
    val bid: Double? = null,
    val ask: Double? = null,
    val high: Double? = null,
    val low: Double? = null,
    val volume: Double? = null,
    val vwap: Double? = null,
    val change: Double? = null,
    @SerialName("change_pct") val changePct: Double? = null,
    val timestamp: String? = null,
)

/**
 * v2 `ohlc` channel payload; `interval_begin` names the bucket, `interval` its length in minutes.
 * The price columns are nullable for the same reason as in [KrakenWsTickerDto].
 */
@Serializable
private data class KrakenWsOhlcDto(
    val symbol: String = "",
    val open: Double? = null,
    val high: Double? = null,
    val low: Double? = null,
    val close: Double? = null,
    val volume: Double? = null,
    @SerialName("interval_begin") val intervalBegin: String = "",
    val interval: Int = 0,
)
