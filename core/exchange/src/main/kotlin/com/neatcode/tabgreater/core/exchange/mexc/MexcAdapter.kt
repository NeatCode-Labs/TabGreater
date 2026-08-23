package com.neatcode.tabgreater.core.exchange.mexc

import com.neatcode.tabgreater.core.exchange.ExchangeAdapter
import com.neatcode.tabgreater.core.exchange.ExchangeHttpException
import com.neatcode.tabgreater.core.exchange.ExchangeUnavailableException
import com.neatcode.tabgreater.core.exchange.ratelimit.TokenBucket
import com.neatcode.tabgreater.core.exchange.ws.SubscriptionBook
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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

/**
 * MEXC spot adapter over the public `api.mexc.com` REST API (Binance-shaped, no key required).
 *
 * MEXC's push API speaks protobuf, which would drag a code-generation step into this pure-JVM
 * module for a single exchange, so there is **no WebSocket here**: live data is REST
 * polling. All `watchTickers` collectors share one loop that polls the union of their markets;
 * every `watchKlines` flow runs its own small loop. Each endpoint has its own 500-weight / 10 s
 * budget, so each path gets its own [TokenBucket].
 *
 * @param tickerPollMs spacing between two ticker polls of the shared loop.
 * @param klinePollMs spacing between two kline polls of one chart/sparkline flow.
 * @param klineStartJitterMs upper bound of the random delay before a kline loop's first poll, so
 *   the tiles of a whole watchlist do not fire at the same instant; `0` polls immediately (tests).
 * @param bucketFactory one bucket per endpoint path; injectable for tests.
 */
class MexcAdapter(
    private val client: OkHttpClient,
    private val scope: CoroutineScope,
    private val restBase: String = DEFAULT_REST_BASE,
    private val tickerPollMs: Long = DEFAULT_TICKER_POLL_MS,
    private val klinePollMs: Long = DEFAULT_KLINE_POLL_MS,
    private val logger: (String) -> Unit = {},
    private val klineStartJitterMs: Long = DEFAULT_KLINE_START_JITTER_MS,
    private val bucketFactory: (String) -> TokenBucket = {
        TokenBucket(BUCKET_CAPACITY, BUCKET_REFILL_PER_SECOND)
    },
) : ExchangeAdapter {

    override val id: ExchangeId = ExchangeId.MEXC

    /** Every timeframe the app uses is served natively (one hour is `60m`, not `1h`). */
    override val nativeTimeframes: Set<Timeframe> = Timeframe.entries.toSet()

    private val json = Json { ignoreUnknownKeys = true }

    private val buckets = ConcurrentHashMap<String, TokenBucket>()

    // ------------------------------------------------------------- polling state
    private val loopLock = Any()

    /** Ref-counting only: there is no socket and nothing to send, so the flush hook is a no-op. */
    private val tickerBook = SubscriptionBook(scope) { _, _ -> }
    private val marketsBySymbol = ConcurrentHashMap<String, Market>()
    private var tickerLoopJob: Job? = null

    /**
     * One element per poll tick: the whole snapshot the shared loop just fetched for the union of
     * the subscribed markets, which every [watchTickers] flow filters down to its own keys.
     *
     * Publishing the round as one list instead of one element per ticker is what makes the buffer
     * size mean anything: a per-ticker feed needs as many slots as the widest watchlist to hold a
     * single tick, so any fixed capacity either over-allocates or tears a round in half under
     * `DROP_OLDEST`. One tick per slot keeps a round atomic, and a collector that falls behind
     * loses a whole stale snapshot and resumes at the newest prices, which is exactly what a
     * ticker feed should do.
     */
    private val tickerUpdates = MutableSharedFlow<List<Ticker>>(
        replay = 0,
        extraBufferCapacity = TICKER_TICK_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    // ---------------------------------------------------------------- REST

    // Request *and* JSON decode run on Dispatchers.IO: callers may be on the main thread and
    // exchangeInfo alone is about 2 MB.
    override suspend fun listMarkets(): List<Market> = withContext(Dispatchers.IO) {
        val info = json.decodeFromString<ExchangeInfoDto>(
            get(PATH_EXCHANGE_INFO, weight = WEIGHT_EXCHANGE_INFO),
        )
        info.symbols.asSequence()
            .filter { it.status == STATUS_ENABLED && it.isSpotTradingAllowed }
            .filter { SYMBOL_PART.matches(it.baseAsset) && SYMBOL_PART.matches(it.quoteAsset) }
            .map { dto ->
                Market(
                    key = MarketKey.of(ExchangeId.MEXC, dto.baseAsset, dto.quoteAsset),
                    nativeSymbol = dto.symbol,
                    // MEXC publishes no PRICE_FILTER, so quotePrecision is the only precision hint
                    // and there is no tick size to report.
                    pricePrecision = dto.quotePrecision,
                    tickSize = null,
                )
            }
            .toList()
    }

    /**
     * MEXC ignores a `symbols=` list (it silently answers with the whole market), so a small
     * watchlist is cheaper as one weight-1 request per symbol and a large one as the single
     * weight-40 call for everything, filtered locally.
     *
     * In the per-symbol branch a single bad symbol must not take the whole round down: a market
     * delisted since the last `listMarkets` stays in the local database and answers HTTP 400
     * `invalid symbol` forever, which would otherwise freeze the shared poll loop for every healthy
     * market next to it. Failures are therefore dropped per symbol; only a round in which *every*
     * symbol failed (a real outage) is reported to the caller.
     *
     * A throttle or a regional block is the opposite case: swallowing it would hand the caller a
     * partial list, and the poll loop above would read that as a healthy tick and keep hammering
     * MEXC at full rate instead of backing off. Those are rethrown at once.
     */
    override suspend fun fetchTickers(markets: List<Market>): List<Ticker> = withContext(Dispatchers.IO) {
        if (markets.isEmpty()) return@withContext emptyList()
        if (markets.size <= ALL_TICKERS_THRESHOLD) {
            val tickers = ArrayList<Ticker>(markets.size)
            var firstFailure: Exception? = null
            var failures = 0
            for (market in markets) {
                try {
                    val body = get(
                        path = PATH_TICKER_24H,
                        query = listOf("symbol" to market.nativeSymbol),
                        weight = WEIGHT_TICKER_SYMBOL,
                    )
                    decodeTickerFor(body, market.nativeSymbol)
                        ?.toTicker(market.key)
                        ?.let { tickers += it }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (!isPerSymbolFailure(e)) throw e
                    failures++
                    if (firstFailure == null) firstFailure = e
                    logger("mexc: ticker for ${market.nativeSymbol} failed: ${e.message}")
                }
            }
            val failure = firstFailure
            if (failure != null && failures == markets.size) throw failure
            tickers
        } else {
            val byNativeSymbol = markets.associateBy { it.nativeSymbol }
            val body = get(PATH_TICKER_24H, weight = WEIGHT_TICKER_ALL)
            // ~400 KB for the whole market: decode it straight into DTOs. Parsing it to a
            // JsonElement tree first and decoding every element out of that would build the same
            // 2 000-row payload twice on every poll tick.
            json.decodeFromString<List<TickerDto>>(body).mapNotNull { dto ->
                byNativeSymbol[dto.symbol]?.let { dto.toTicker(it.key) }
            }
        }
    }

    override suspend fun fetchOHLCV(
        market: Market,
        timeframe: Timeframe,
        endTime: Long?,
        limit: Int,
    ): List<Candle> = withContext(Dispatchers.IO) {
        val clamped = limit.coerceIn(1, MAX_KLINES)
        val query = mutableListOf(
            "symbol" to market.nativeSymbol,
            "interval" to intervalOf(timeframe),
            "limit" to clamped.toString(),
        )
        if (endTime != null) {
            // api.mexc.com ignores a lone endTime and answers with the newest window, so paging
            // older history needs an explicit range. The bar starting exactly at endTime still
            // comes back, and `ExchangeAdapter.fetchOHLCV` documents endTime as exclusive — hence
            // the filter, which keeps the seam bar out of KLineChart's un-deduplicated `forward`.
            query += "startTime" to (endTime - clamped * timeframe.millis).toString()
            query += "endTime" to endTime.toString()
        }
        val bars = parseKlines(get(PATH_KLINES, query, WEIGHT_KLINES))
        if (endTime == null) bars else bars.filter { it.openTime < endTime }
    }

    /** Rows are `[openTime, open, high, low, close, volume, closeTime, quoteVolume]`, oldest first. */
    private fun parseKlines(body: String): List<Candle> {
        val now = System.currentTimeMillis()
        return json.parseToJsonElement(body).jsonArray.mapNotNull { row ->
            val cells = row.jsonArray
            if (cells.size <= IDX_CLOSE_TIME) return@mapNotNull null
            // MEXC's closeTime is the exact bar end (openTime + interval), not end - 1 ms.
            val closeTime = cells[IDX_CLOSE_TIME].jsonPrimitive.long
            Candle(
                openTime = cells[IDX_OPEN_TIME].jsonPrimitive.long,
                open = cells[IDX_OPEN].jsonPrimitive.content.toDouble(),
                high = cells[IDX_HIGH].jsonPrimitive.content.toDouble(),
                low = cells[IDX_LOW].jsonPrimitive.content.toDouble(),
                close = cells[IDX_CLOSE].jsonPrimitive.content.toDouble(),
                volume = cells[IDX_VOLUME].jsonPrimitive.content.toDouble(),
                closed = closeTime <= now,
            )
        }
    }

    /**
     * Reads the one ticker a `?symbol=` request asked for. The documented answer is a single
     * object, but a cache or proxy could serve the whole-market array instead, so the shape is
     * inspected before decoding and the row is matched by symbol: a foreign price must never be
     * labelled with [nativeSymbol]'s key. The tree costs nothing here — this body is one row.
     */
    private fun decodeTickerFor(body: String, nativeSymbol: String): TickerDto? {
        val root = json.parseToJsonElement(body)
        return if (root is JsonArray) {
            root.asSequence()
                .map { json.decodeFromJsonElement<TickerDto>(it) }
                .firstOrNull { it.symbol == nativeSymbol }
        } else {
            json.decodeFromJsonElement<TickerDto>(root).takeIf { it.symbol == nativeSymbol }
        }
    }

    /**
     * Whether [e] says something about one symbol only (a delisted market's HTTP 400 `invalid
     * symbol`, a dropped connection, a garbled body) rather than about the client as a whole. A
     * regional block or a throttle answers for every symbol of the round and must reach the caller
     * so that the poll loop backs off.
     */
    private fun isPerSymbolFailure(e: Exception): Boolean = when (e) {
        is ExchangeUnavailableException -> false
        is ExchangeHttpException -> e.code == HTTP_BAD_REQUEST
        else -> true
    }

    private suspend fun get(
        path: String,
        query: List<Pair<String, String>> = emptyList(),
        weight: Int,
    ): String = withContext(Dispatchers.IO) {
        bucketFor(path).acquire(weight)
        val url = (restBase.trimEnd('/') + path).toHttpUrl().newBuilder()
        for ((name, value) in query) url.addQueryParameter(name, value)
        val request = Request.Builder().url(url.build()).get().build()
        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) throw errorFor(response, body)
            body
        }
    }

    /** Every endpoint has its own 500-weight / 10 s window, so buckets are per path. */
    private fun bucketFor(path: String): TokenBucket =
        buckets.computeIfAbsent(path) { bucketFactory(it) }

    private fun errorFor(response: Response, body: String): Exception {
        val code = response.code
        val detail = body.take(ERROR_BODY_CHARS)
        // The geo block is documented by its body, and MEXC's edge does not always dress it as 451
        // (a 403 from the WAF carries the same message), so the body decides before the status.
        if (code == HTTP_UNAVAILABLE_FOR_LEGAL_REASONS ||
            detail.contains(RESTRICTED_LOCATION_MARKER, ignoreCase = true)
        ) {
            return ExchangeUnavailableException(id, "MEXC is unavailable in this region (HTTP $code): $detail")
        }
        return when (code) {
            HTTP_TOO_MANY_REQUESTS, HTTP_IM_A_TEAPOT -> {
                val retryAfter = response.header("Retry-After")
                val suffix = if (retryAfter != null) ", retry after $retryAfter s" else ""
                ExchangeHttpException(id, code, "MEXC rate limit hit (HTTP $code$suffix): $detail")
            }

            // 403 is an abuse/WAF refusal, not a throttle: do not describe it as a rate limit.
            HTTP_FORBIDDEN -> ExchangeHttpException(id, code, "MEXC refused the request (HTTP $code): $detail")

            else -> ExchangeHttpException(id, code, "MEXC request failed (HTTP $code): $detail")
        }
    }

    // --------------------------------------------------------------- polling

    override fun watchTickers(markets: List<Market>): Flow<Ticker> = channelFlow {
        val wanted = LinkedHashMap<String, Market>()
        for (market in markets) wanted[market.nativeSymbol] = market
        // Nothing to poll, but completing would look like a dropped feed to the live repository.
        if (wanted.isEmpty()) awaitCancellation()
        val wantedKeys = markets.mapTo(HashSet()) { it.key }
        val bookWasEmpty = acquireTickers(wanted)
        try {
            tickerUpdates
                // Start the loop only once this collector is attached, so it never misses the
                // immediate first poll.
                .onSubscription { startTickerLoop(bookWasEmpty) }
                // The tick covers the union of every collector's markets; this one keeps its own.
                .collect { tick ->
                    for (ticker in tick) {
                        if (ticker.key in wantedKeys) this@channelFlow.send(ticker)
                    }
                }
        } finally {
            releaseTickers(wanted.keys)
        }
    }

    override fun watchKlines(market: Market, timeframe: Timeframe): Flow<Candle> = channelFlow {
        // Spread the loops of a whole screen of tiles over a few seconds instead of one burst.
        if (klineStartJitterMs > 0) delay(Random.nextLong(klineStartJitterMs + 1))
        var failures = 0
        while (isActive) {
            failures = try {
                // Two bars: the previous one may have closed since the last poll.
                for (candle in fetchOHLCV(market, timeframe, null, KLINE_POLL_LIMIT)) {
                    this@channelFlow.send(candle)
                }
                0
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger("mexc: kline poll for ${market.nativeSymbol} failed: ${e.message}")
                failures + 1
            }
            delay(pollDelayMs(klinePollMs, failures))
        }
    }

    /** Registers [wanted] and adds a reference per symbol; returns whether the book was empty. */
    private fun acquireTickers(wanted: Map<String, Market>): Boolean = synchronized(loopLock) {
        marketsBySymbol.putAll(wanted)
        tickerBook.acquire(wanted.keys).wasEmpty
    }

    /** [bookWasEmpty] only explains the log line; the job itself is the guard against duplicates. */
    private fun startTickerLoop(bookWasEmpty: Boolean) {
        synchronized(loopLock) {
            if (tickerLoopJob?.isActive == true) return
            logger("mexc: starting ticker poll loop (book was empty: $bookWasEmpty)")
            tickerLoopJob = scope.launch { runTickerLoop() }
        }
    }

    private fun releaseTickers(keys: Collection<String>) {
        synchronized(loopLock) {
            val released = tickerBook.release(keys)
            for (key in released.gone) marketsBySymbol.remove(key)
            if (!released.isEmpty) return
            logger("mexc: last ticker collector left, stopping the poll loop")
            tickerLoopJob?.cancel()
            tickerLoopJob = null
        }
    }

    /**
     * One shared loop for every ticker collector: it polls the union of the subscribed markets and
     * publishes the round as a single snapshot into [tickerUpdates], where each flow keeps its own
     * keys. A failing tick is logged and retried later with a growing delay — the loop never dies
     * on its own.
     */
    private suspend fun runTickerLoop() {
        var failures = 0
        while (coroutineContext.isActive) {
            val active = activeTickerMarkets()
            // Never self-terminate on an empty union: only releaseTickers knows whether the book is
            // really empty, and it cancels this job when it is. A loop that ended here on a
            // transient empty read would leave a live collector attached to a dead feed forever.
            if (active.isEmpty()) {
                delay(tickerPollMs)
                continue
            }
            failures = try {
                val tick = fetchTickers(active)
                if (tick.isNotEmpty()) tickerUpdates.tryEmit(tick)
                0
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger("mexc: ticker poll of ${active.size} market(s) failed: ${e.message}")
                failures + 1
            }
            delay(pollDelayMs(tickerPollMs, failures))
        }
    }

    /**
     * The key set and its market map are mutated together by [acquireTickers] / [releaseTickers],
     * so the union is read under the same lock: outside it the loop could observe a key whose
     * market has already been removed and conclude that nobody is subscribed any more. Neither
     * holder of [loopLock] suspends inside it, so this cannot deadlock the poll loop.
     */
    private fun activeTickerMarkets(): List<Market> = synchronized(loopLock) {
        tickerBook.active.mapNotNull(marketsBySymbol::get)
    }

    /** Normal spacing while healthy, doubling per consecutive failure up to [MAX_POLL_BACKOFF_MS]. */
    private fun pollDelayMs(baseMs: Long, failures: Int): Long {
        if (failures <= 0) return baseMs
        val shift = (failures - 1).coerceIn(0, MAX_BACKOFF_SHIFT)
        return (baseMs shl shift).coerceIn(baseMs, MAX_POLL_BACKOFF_MS.coerceAtLeast(baseMs))
    }

    private fun intervalOf(timeframe: Timeframe): String = when (timeframe) {
        Timeframe.M1 -> "1m"
        Timeframe.M5 -> "5m"
        Timeframe.M15 -> "15m"
        Timeframe.M30 -> "30m"
        // MEXC answers "Invalid interval." for the Binance-style "1h".
        Timeframe.H1 -> "60m"
        Timeframe.H4 -> "4h"
        Timeframe.D1 -> "1d"
        Timeframe.W1 -> "1W"
        Timeframe.MN1 -> "1M"
    }

    companion object {
        const val DEFAULT_REST_BASE: String = "https://api.mexc.com"

        const val DEFAULT_TICKER_POLL_MS: Long = 15_000
        const val DEFAULT_KLINE_POLL_MS: Long = 60_000
        const val DEFAULT_KLINE_START_JITTER_MS: Long = 3_000

        /** Above this many markets the single whole-market call is cheaper than one call each. */
        const val ALL_TICKERS_THRESHOLD: Int = 20

        private const val PATH_EXCHANGE_INFO = "/api/v3/exchangeInfo"
        private const val PATH_TICKER_24H = "/api/v3/ticker/24hr"
        private const val PATH_KLINES = "/api/v3/klines"

        private const val WEIGHT_EXCHANGE_INFO = 10
        private const val WEIGHT_TICKER_SYMBOL = 1
        private const val WEIGHT_TICKER_ALL = 40
        private const val WEIGHT_KLINES = 1

        /** 500 weight per 10 s and per endpoint. */
        private const val BUCKET_CAPACITY = 500.0
        private const val BUCKET_REFILL_PER_SECOND = 50.0

        private const val MAX_KLINES = 1000
        private const val KLINE_POLL_LIMIT = 2

        /** Poll ticks (whole snapshots, not single tickers) buffered for a lagging collector. */
        private const val TICKER_TICK_BUFFER = 1

        private const val MAX_POLL_BACKOFF_MS = 60_000L
        private const val MAX_BACKOFF_SHIFT = 16

        /** MEXC symbol states: 1 = ENABLED, 2 = PAUSED, 3 = OFFLINE. */
        private const val STATUS_ENABLED = "1"
        private val SYMBOL_PART = Regex("[A-Za-z0-9]+")

        private const val ERROR_BODY_CHARS = 200

        /** MEXC's documented regional-block body: `{"msg":"Service unavailable from a restricted location.","code":0}`. */
        private const val RESTRICTED_LOCATION_MARKER = "restricted location"

        /** MEXC answers `{"msg":"invalid symbol","code":-1121}` with this status. */
        private const val HTTP_BAD_REQUEST = 400
        private const val HTTP_FORBIDDEN = 403
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
    val quotePrecision: Int = DEFAULT_QUOTE_PRECISION,
    val isSpotTradingAllowed: Boolean = false,
)

/**
 * `priceChangePercent` is deliberately absent: MEXC reports it as a fraction (`-0.0013` for
 * −0.13 %), so the change is recomputed from `openPrice` instead.
 */
@Serializable
private data class TickerDto(
    val symbol: String,
    val lastPrice: String? = null,
    val openPrice: String? = null,
    val highPrice: String? = null,
    val lowPrice: String? = null,
    val volume: String? = null,
    val quoteVolume: String? = null,
    val bidPrice: String? = null,
    val askPrice: String? = null,
    val closeTime: Long? = null,
)

private const val DEFAULT_QUOTE_PRECISION = 8
private const val PERCENT = 100.0

private fun TickerDto.toTicker(key: MarketKey): Ticker? {
    val last = lastPrice?.toDoubleOrNull() ?: return null
    val open = openPrice?.toDoubleOrNull()
    return Ticker(
        key = key,
        last = last,
        open24h = open,
        high24h = highPrice?.toDoubleOrNull(),
        low24h = lowPrice?.toDoubleOrNull(),
        volumeBase24h = volume?.toDoubleOrNull(),
        volumeQuote24h = quoteVolume?.toDoubleOrNull(),
        changePct24h = open?.takeIf { it != 0.0 }?.let { (last - it) / it * PERCENT },
        bid = bidPrice?.toDoubleOrNull(),
        ask = askPrice?.toDoubleOrNull(),
        timestamp = closeTime?.takeIf { it > 0 } ?: System.currentTimeMillis(),
    )
}
