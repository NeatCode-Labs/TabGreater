package com.neatcode.tabgreater.core.exchange.hyperliquid

import com.neatcode.tabgreater.core.exchange.ExchangeAdapter
import com.neatcode.tabgreater.core.exchange.ExchangeFailureKind
import com.neatcode.tabgreater.core.exchange.ExchangeHttpException
import com.neatcode.tabgreater.core.exchange.ExchangeRequests
import com.neatcode.tabgreater.core.exchange.TickerBatch
import com.neatcode.tabgreater.core.exchange.httpFailureKind
import com.neatcode.tabgreater.core.exchange.parseRetryAfter
import com.neatcode.tabgreater.core.exchange.ratelimit.TokenBucket
import com.neatcode.tabgreater.core.exchange.ws.ExchangeSocket
import com.neatcode.tabgreater.core.exchange.ws.SubscriptionBook
import com.neatcode.tabgreater.core.model.Candle
import com.neatcode.tabgreater.core.model.ExchangeId
import com.neatcode.tabgreater.core.model.InstrumentKind
import com.neatcode.tabgreater.core.model.Market
import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.MarketSeriesId
import com.neatcode.tabgreater.core.model.Ticker
import com.neatcode.tabgreater.core.model.Timeframe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Semaphore

/**
 * Read-only adapter for Hyperliquid HyperCore.
 *
 * The public info endpoint provides spot pairs, core and builder-deployed perpetuals, and HIP-4
 * outcomes through distinct metadata formats. [Market.nativeSymbol] always retains the exact API
 * coin identifier. User-facing names and persistence keys are generated independently so a spot
 * token, an equity perpetual, and a Yes/No outcome cannot collide.
 */
class HyperliquidAdapter(
    private val client: OkHttpClient,
    private val scope: CoroutineScope,
    private val restBase: String = DEFAULT_REST_BASE,
    private val wsBase: String = DEFAULT_WS_BASE,
    private val logger: (String) -> Unit = {},
    private val restBucket: TokenBucket = TokenBucket(REST_WEIGHT_CAPACITY, REST_WEIGHT_REFILL_PER_SECOND),
) : ExchangeAdapter {

    override val id: ExchangeId = ExchangeId.HYPERLIQUID

    override val nativeTimeframes: Set<Timeframe> = Timeframe.entries.toSet()

    private val json = Json { ignoreUnknownKeys = true }
    private val requestSlots = Semaphore(MAX_PARALLEL_REQUESTS)
    private val socketLock = Any()
    private var socket: ExchangeSocket? = null
    private var pingJob: Job? = null
    private val subscriptionBook = SubscriptionBook(scope) { subscribe, unsubscribe ->
        flushSubscriptions(subscribe, unsubscribe)
    }
    private val socketSubscriptions = ConcurrentHashMap<String, Subscription>()
    private val latestTicker = ConcurrentHashMap<String, Ticker>()

    @Volatile
    private var contextsByCoin: Map<String, MarketContext> = emptyMap()

    @Volatile
    private var marketByCoin: Map<String, Market> = emptyMap()

    /** Loads every currently listed HyperCore instrument. Stale and delisted HIP-3 markets are skipped. */
    override suspend fun listMarkets(): List<Market> = withContext(Dispatchers.IO) {
        val spotDeferred = async { info("spotMetaAndAssetCtxs") }
        val dexDeferred = async { info("perpDexs") }
        val outcomeDeferred = async { info("outcomeMeta") }
        val templatesDeferred = async { info("outcomeTemplates") }
        val quoteHints = async { loadPerpQuoteHints() }

        val spotEnvelope = decodePair(spotDeferred.await())
        val spotMeta = json.decodeFromJsonElement<SpotMetaDto>(spotEnvelope.first)
        val spotContexts = decodeSpotContexts(spotEnvelope.second)
        // The first perpDexs row is null on mainnet; the core dex is represented by name "".
        val dexes = listOf("") + json.decodeFromJsonElement<List<PerpDexDto?>>(dexDeferred.await().jsonArray)
            .mapNotNull { it?.name }
            .filter(String::isNotBlank)
            .distinct()

        val perpMetadata = loadPerpMetadata(dexes)
        val outcomeMetadata = outcomeDeferred.await()
        val outcomeTemplates = templatesDeferred.await()

        val allMarkets = buildList {
            addAll(buildSpotMarkets(spotMeta))
            addAll(buildPerpMarkets(perpMetadata, quoteHints.await()))
            addAll(buildOutcomeMarkets(outcomeMetadata, outcomeTemplates))
        }.distinctBy { it.key }

        val contexts = buildMap {
            putAll(spotContexts)
            perpMetadata.forEach { metadata ->
                putAll(metadata.contexts)
            }
        }
        contextsByCoin = contexts
        marketByCoin = allMarkets.associateBy(Market::nativeSymbol)
        latestTicker.keys.retainAll(marketByCoin.keys)
        allMarkets
    }

    override suspend fun fetchTickers(markets: List<Market>): List<Ticker> =
        fetchTickerBatch(markets).values.values.toList()

    /** A request per selected coin is paced and isolated so one stale market does not lose its peers. */
    override suspend fun fetchTickerBatch(markets: List<Market>) = withContext(Dispatchers.IO) {
        if (markets.isEmpty()) return@withContext TickerBatch()

        val values = linkedMapOf<MarketKey, Ticker>()
        val failures = linkedMapOf<MarketKey, Throwable>()
        val missing = linkedSetOf<MarketKey>()
        val chunks = markets.chunked(MAX_PARALLEL_REQUESTS)
        for ((chunkIndex, chunk) in chunks.withIndex()) {
            val results = supervisorScope {
                chunk.map { market ->
                    async {
                        val result: Result<Ticker?> = try {
                            Result.success(fetchLatestTrade(market))
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            Result.failure(failure)
                        }
                        market to result
                    }
                }.map { it.await() }
            }
            for ((market, result) in results) {
                result.fold(
                    onSuccess = { ticker -> if (ticker == null) missing += market.key else values[market.key] = ticker },
                    onFailure = { failure -> failures[market.key] = failure },
                )
            }
            val blocked = failures.values.firstOrNull {
                it is ExchangeHttpException && it.kind in setOf(
                    ExchangeFailureKind.RATE_LIMITED,
                    ExchangeFailureKind.DEFERRED,
                    ExchangeFailureKind.FORBIDDEN,
                    ExchangeFailureKind.REGION_RESTRICTED,
                )
            }
            if (blocked != null) {
                val remaining = markets.drop((chunkIndex + 1) * MAX_PARALLEL_REQUESTS)
                remaining.forEach { market ->
                    failures.putIfAbsent(
                        market.key,
                        ExchangeHttpException(
                            id,
                            429,
                            "Hyperliquid refresh paused after the API rate limit",
                            ExchangeFailureKind.DEFERRED,
                            (blocked as? ExchangeHttpException)?.retryAfterMs,
                        ),
                    )
                }
                break
            }
        }
        TickerBatch(values, failures, missing)
    }

    override suspend fun fetchOHLCV(
        market: Market,
        timeframe: Timeframe,
        endTime: Long?,
        limit: Int,
    ): List<Candle> = withContext(Dispatchers.IO) {
        val wanted = limit.coerceIn(1, MAX_CANDLES)
        val endInclusive = ((endTime ?: System.currentTimeMillis()) - 1).coerceAtLeast(0)
        val intervalMillis = timeframe.millis.coerceAtLeast(MINUTE_MS)
        val startTime = (endInclusive - intervalMillis * wanted).coerceAtLeast(0)
        val request = info(
            "candleSnapshot",
            "req" to buildJsonObject {
                put("coin", market.nativeSymbol)
                put("interval", timeframe.apiInterval())
                put("startTime", startTime)
                put("endTime", endInclusive)
            },
        )
        val now = System.currentTimeMillis()
        request.jsonArray.mapNotNull { element ->
            runCatching { json.decodeFromJsonElement<CandleDto>(element).toCandle(now) }.getOrNull()
        }.sortedBy(Candle::openTime).takeLast(wanted)
    }

    override fun watchTickers(markets: List<Market>): Flow<Ticker> = channelFlow {
        if (markets.isEmpty()) return@channelFlow

        val byCoin = markets.associateBy(Market::nativeSymbol)
        val subscriptions = byCoin.keys.flatMap { coin -> listOf(tradeSubscription(coin), bboSubscription(coin)) }
        val keys = subscriptions.map(Subscription::key)
        val acquired = subscriptionBook.acquire(keys)
        val target = obtainSocket()
        val reader = launch {
            target.messages.collect { frame ->
                decodeFrame(frame)?.let { parsed ->
                    when (parsed.channel) {
                        "trades" -> parsed.data.asElements().forEach { tradeElement ->
                            val ticker = runCatching {
                                json.decodeFromJsonElement<TradeDto>(tradeElement)
                            }.getOrNull()?.let { trade ->
                                val market = byCoin[trade.coin] ?: marketByCoin[trade.coin]
                                market?.let { trade.toTicker(it, contextsByCoin[trade.nativeKey()]) }
                            }
                            if (ticker != null) {
                                latestTicker[ticker.key.value] = ticker
                                send(ticker)
                            }
                        }

                        "bbo" -> {
                            val quote = runCatching {
                                json.decodeFromJsonElement<BboDto>(parsed.data)
                            }.getOrNull()
                            val market = quote?.let { byCoin[it.coin] ?: marketByCoin[it.coin] }
                            val current = market?.let { latestTicker[it.key.value] }
                            if (market != null && current != null) {
                                val book = quote.bbo
                                val ticker = current.copy(
                                    bid = book.getOrNull(0)?.price()?.takeIf { it > 0.0 },
                                    ask = book.getOrNull(1)?.price()?.takeIf { it > 0.0 },
                                    timestamp = maxOf(current.timestamp, quote.time),
                                    confirmsPrice = false,
                                )
                                latestTicker[market.key.value] = ticker
                                send(ticker)
                            }
                        }
                    }
                }
            }
        }
        try {
            target.connect()
            subscriptionBook.queueSubscribe(acquired.fresh)
            awaitCancellation()
        } finally {
            reader.cancelAndJoin()
            val released = subscriptionBook.release(keys)
            subscriptionBook.queueUnsubscribe(released.gone)
            if (released.isEmpty) closeSocketWhenIdle()
        }
    }

    override fun watchKlines(market: Market, timeframe: Timeframe): Flow<Candle> = channelFlow {
        val subscription = candleSubscription(market.nativeSymbol, timeframe)
        val acquired = subscriptionBook.acquire(listOf(subscription.key))
        val target = obtainSocket()
        val reader = launch {
            target.messages.collect { frame ->
                val parsed = decodeFrame(frame) ?: return@collect
                if (parsed.channel != "candle") return@collect
                parsed.data.asElements().forEach { candleElement ->
                    val candle = runCatching {
                        json.decodeFromJsonElement<CandleDto>(candleElement).toCandle(System.currentTimeMillis())
                    }.getOrNull()
                    if (candle != null && candleElement.jsonObject.string("s") == market.nativeSymbol) send(candle)
                }
            }
        }
        try {
            target.connect()
            subscriptionBook.queueSubscribe(acquired.fresh)
            awaitCancellation()
        } finally {
            reader.cancelAndJoin()
            val released = subscriptionBook.release(listOf(subscription.key))
            subscriptionBook.queueUnsubscribe(released.gone)
            if (released.isEmpty) closeSocketWhenIdle()
        }
    }

    private suspend fun loadPerpMetadata(dexes: List<String>): List<PerpDexMetadata> = supervisorScope {
        dexes.map { dex ->
            async {
                val pair = decodePair(info("metaAndAssetCtxs", "dex" to JsonPrimitive(dex)))
                val meta = json.decodeFromJsonElement<PerpMetaDto>(pair.first)
                // Perp ctx rows omit `coin`; Hyperliquid returns them aligned to that dex's universe.
                val contexts = decodePerpContexts(pair.second, meta.universe)
                PerpDexMetadata(dex, meta.universe, contexts)
            }
        }.map { it.await() }
    }

    private fun buildSpotMarkets(meta: SpotMetaDto): List<Market> {
        val tokens = meta.tokens.associateBy(SpotTokenDto::index)
        return meta.universe.mapNotNull { market ->
            val base = tokens[market.tokens.getOrNull(0)] ?: return@mapNotNull null
            val quote = tokens[market.tokens.getOrNull(1)] ?: return@mapNotNull null
            val coin = market.name
            Market(
                key = MarketKey.of(ExchangeId.HYPERLIQUID, "SPOT_${market.index}", quote.name),
                nativeSymbol = coin,
                pricePrecision = pricePrecision(base.szDecimals, SPOT_MAX_PRICE_DECIMALS),
                active = true,
                instrumentKind = InstrumentKind.SPOT,
                displayName = "${base.name}/${quote.name}",
                description = listOfNotNull(base.fullName, "Spot market on Hyperliquid").joinToString(" · "),
                unit = quote.name,
                venue = "HyperCore spot",
            )
        }
    }

    private fun buildPerpMarkets(
        dexes: List<PerpDexMetadata>,
        quoteHints: Map<String, String>,
    ): List<Market> = dexes.flatMap { dex ->
        dex.markets.mapNotNull { perp ->
            if (perp.isDelisted) return@mapNotNull null
            val coin = perp.name
            val venue = if (dex.dex.isBlank()) "HyperCore" else dex.dex
            val keyBase = if (dex.dex.isBlank()) {
                "PERP_${coin.keyPart()}"
            } else {
                "HIP3_${dex.dex.keyPart()}_${coin.substringAfter(':').keyPart()}"
            }
            val hint = quoteHints[coin]
            val unit = hyperliquidUnit(coin, hint)
            Market(
                key = MarketKey.of(ExchangeId.HYPERLIQUID, keyBase, "USD"),
                nativeSymbol = coin,
                pricePrecision = pricePrecision(perp.szDecimals, PERP_MAX_PRICE_DECIMALS),
                instrumentKind = InstrumentKind.PERPETUAL,
                displayName = if (unit == "USD billions" || hint in PAIR_OR_INDEX_QUOTE_HINTS) coin else "$coin/USD",
                description = listOfNotNull(
                    unit,
                    "Perpetual contract",
                    if (dex.dex.isBlank()) null else "Builder venue ${dex.dex}",
                ).joinToString(" · "),
                unit = unit ?: "USD per contract unit",
                venue = venue,
            )
        }
    }

    private fun buildOutcomeMarkets(
        element: JsonElement,
        templateElement: JsonElement,
    ): List<Market> {
        val metadata = json.decodeFromJsonElement<OutcomeMetaDto>(element)
        val templates = json.decodeFromJsonElement<List<OutcomeTemplateDto>>(templateElement.jsonArray)
            .associateBy(OutcomeTemplateDto::id)
        val questions = metadata.questions.flatMap { question ->
            (question.namedOutcomes + question.settledNamedOutcomes + question.fallbackOutcome)
                .distinct()
                .map { outcomeId -> outcomeId to question }
        }.toMap()

        return metadata.outcomes.flatMap { outcome ->
            val question = questions[outcome.outcome]
            val fields = linkedMapOf<String, String>().apply {
                putAll(question?.description.toFields())
                putAll(outcome.description.toFields())
            }
            val templateId = outcome.name.removePrefix("template:").substringBefore(':')
            val outcomeTemplate = templates[templateId]
            val isFallbackTemplate = question?.fallbackOutcome == outcome.outcome &&
                outcome.name.trim().equals("template fallback", ignoreCase = true)
            val prompt = if (isFallbackTemplate) {
                fallbackOutcomeLabel(question)
            } else {
                renderOutcomeName(outcome.name, outcomeTemplate, fields)
            }
            val parentPrompt = question?.let { parent ->
                renderOutcomeName(
                    parent.name,
                    templates[parent.name.removePrefix("template:").substringBefore(':')],
                    fields,
                )
            }.orEmpty()
            val recurring = question?.name?.removePrefix("template:")?.equals("Recurring", ignoreCase = true) == true ||
                fields["class"].equals("priceBinary", ignoreCase = true) ||
                fields["class"].equals("priceBucket", ignoreCase = true) ||
                templateId.equals("priceBinary", ignoreCase = true) ||
                templateId.equals("priceBucket", ignoreCase = true) ||
                outcome.name.startsWith("Recurring", ignoreCase = true)
            val promptWithQuestion = when {
                recurring -> recurringPrompt(fields, outcome.description.orEmpty())
                parentPrompt.isBlank() -> prompt
                prompt.isBlank() || prompt.equals("binary", ignoreCase = true) || prompt.equals("yes no", ignoreCase = true) -> parentPrompt
                prompt.equals(parentPrompt, ignoreCase = true) -> parentPrompt
                prompt.contains(parentPrompt, ignoreCase = true) -> prompt
                else -> "$parentPrompt · $prompt"
            }.ifBlank { "Outcome #${outcome.outcome}" }
            val sides = outcome.sideSpecs.ifEmpty { listOf(OutcomeSideDto("Yes"), OutcomeSideDto("No")) }
            sides.mapIndexed { sideIndex, side ->
                val label = outcomeSideLabel(side.name, fields, sideIndex, templates)
                val coin = "#${outcome.outcome * 10L + sideIndex}"
                val quote = outcome.quoteToken.ifBlank { "USDC" }
                val expiry = outcomeExpiry(fields) ?: outcomeExpiry(question?.description.toFields())
                // Keep the key tied to the protocol side index; a template label can change.
                val baseKey = "OUTCOME_${outcome.outcome}_SIDE_${sideIndex}"
                val descriptor = listOfNotNull(
                    outcomeTemplate?.description?.let { renderTemplate(it.substringBefore("metadata="), fields) },
                    outcome.description.orEmpty().takeIf(String::isNotBlank),
                    question?.description.orEmpty().takeIf(String::isNotBlank),
                ).joinToString(" · ").take(MAX_DESCRIPTION_CHARS)
                Market(
                    key = MarketKey.of(ExchangeId.HYPERLIQUID, baseKey, quote),
                    nativeSymbol = coin,
                    pricePrecision = OUTCOME_PRICE_PRECISION,
                    active = outcome.outcome !in question?.settledNamedOutcomes.orEmpty(),
                    instrumentKind = InstrumentKind.OUTCOME,
                    displayName = "$promptWithQuestion · $label",
                    description = descriptor,
                    unit = "$quote per outcome token",
                    venue = outcome.venue.ifBlank { "HyperCore outcomes" },
                    eventId = question?.question?.toString(),
                    outcomeId = outcome.outcome.toString(),
                    outcomeSide = label,
                    expiresAt = expiry,
                    rolloverSeriesId = if (fields["class"].equals("priceBinary", ignoreCase = true)) {
                        MarketSeriesId.dailyPriceBinary(
                            underlying = fields["underlying"],
                            period = fields["period"],
                            targetPrice = fields["targetPrice"],
                            expiresAtEpochMs = expiry,
                        )
                    } else {
                        null
                    },
                )
            }
        }
    }

    private suspend fun fetchLatestTrade(market: Market): Ticker? {
        val trades = info("recentTrades", "coin" to JsonPrimitive(market.nativeSymbol)).jsonArray
            .mapNotNull { element -> runCatching { json.decodeFromJsonElement<TradeDto>(element) }.getOrNull() }
        val ticker = trades.asSequence()
            .filter { it.coin == market.nativeSymbol }
            .sortedByDescending(TradeDto::time)
            .mapNotNull { it.toTicker(market, contextsByCoin[market.nativeSymbol]) }
            .firstOrNull() ?: return null
        latestTicker[market.key.value] = ticker
        return ticker
    }

    private suspend fun info(type: String, vararg parameters: Pair<String, JsonElement>): JsonElement {
        val requestBody = buildJsonObject {
            put("type", type)
            parameters.forEach { (key, value) -> put(key, value) }
        }
        return postInfo(requestBody)
    }

    private suspend fun postInfo(body: JsonObject): JsonElement = withContext(Dispatchers.IO) {
        restBucket.acquire(requestWeight(body))
        requestSlots.acquire()
        try {
            val request = Request.Builder()
                .url(restBase.trimEnd('/') + INFO_PATH)
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            val response = ExchangeRequests.execute(
                exchange = id,
                client = client,
                request = request,
                publicPayloadKey = body.toString(),
                errorFor = ::httpException,
            )
            json.parseToJsonElement(response.body)
        } finally {
            requestSlots.release()
        }
    }

    private fun requestWeight(body: JsonObject): Int = when (body["type"]?.jsonPrimitive?.contentOrNull) {
        "candleSnapshot" -> {
            val request = body["req"]?.jsonObject
            val start = request?.get("startTime")?.jsonPrimitive?.longOrNull
            val end = request?.get("endTime")?.jsonPrimitive?.longOrNull
            val interval = request?.get("interval")?.jsonPrimitive?.contentOrNull?.let(::intervalMillis)
            val bars = if (start != null && end != null && interval != null && interval > 0L) {
                ((end - start).coerceAtLeast(0L) / interval + 1L).coerceIn(1L, MAX_CANDLES.toLong()).toInt()
            } else {
                MAX_CANDLES
            }
            BASE_INFO_WEIGHT + (bars + CANDLE_WEIGHT_DIVISOR - 1) / CANDLE_WEIGHT_DIVISOR
        }
        "recentTrades" -> RECENT_TRADES_WEIGHT
        else -> BASE_INFO_WEIGHT
    }

    private fun intervalMillis(interval: String): Long? = when (interval) {
        "1m" -> 60_000L
        "5m" -> 5 * 60_000L
        "15m" -> 15 * 60_000L
        "30m" -> 30 * 60_000L
        "1h" -> 60 * 60_000L
        "4h" -> 4 * 60 * 60_000L
        "1d" -> 24 * 60 * 60_000L
        "1w" -> 7 * 24 * 60 * 60_000L
        "1M" -> 30L * 24 * 60 * 60_000L
        else -> null
    }

    private fun httpException(response: Response, body: String): ExchangeHttpException {
        val code = response.code
        val retryAfter = parseRetryAfter(response.header("Retry-After"), System.currentTimeMillis())
        val kind = httpFailureKind(code)
        val detail = body.take(ERROR_BODY_CHARS)
        return ExchangeHttpException(
            id,
            code,
            "Hyperliquid info request failed (HTTP $code): $detail",
            kind,
            if (kind == ExchangeFailureKind.RATE_LIMITED) retryAfter ?: DEFAULT_RETRY_MS else null,
        )
    }

    private fun flushSubscriptions(subscribe: List<String>, unsubscribe: List<String>) {
        val target = synchronized(socketLock) { socket } ?: return
        unsubscribe.forEach { key ->
            val subscription = socketSubscriptions[key] ?: return@forEach
            target.send(subscriptionFrame("unsubscribe", subscription))
        }
        subscribe.forEach { key ->
            val subscription = socketSubscriptions[key] ?: return@forEach
            target.send(subscriptionFrame("subscribe", subscription))
        }
    }

    private fun obtainSocket(): ExchangeSocket = synchronized(socketLock) {
        socket?.let { return@synchronized it }
        ExchangeSocket(
            client = client,
            url = wsBase,
            name = id.id,
            scope = scope,
            logger = logger,
        ).also { created ->
            created.onReconnected = { subscriptionBook.resubscribeAll() }
            socket = created
            created.connect()
            pingJob = scope.launch {
                while (isActive) {
                    delay(WS_PING_INTERVAL_MS)
                    created.send(PING_FRAME)
                }
            }
        }
    }

    private fun closeSocketWhenIdle() {
        synchronized(socketLock) {
            if (subscriptionBook.active.isNotEmpty()) return
            subscriptionBook.clearPending()
            pingJob?.cancel()
            pingJob = null
            socket?.close()
            socket = null
        }
    }

    private fun tradeSubscription(coin: String): Subscription = Subscription("trades", coin).also {
        socketSubscriptions[it.key] = it
    }

    private fun bboSubscription(coin: String): Subscription = Subscription("bbo", coin).also {
        socketSubscriptions[it.key] = it
    }

    private fun candleSubscription(coin: String, timeframe: Timeframe): Subscription =
        Subscription("candle", coin, timeframe.apiInterval()).also { socketSubscriptions[it.key] = it }

    private fun subscriptionFrame(method: String, subscription: Subscription): String = buildJsonObject {
        put("method", method)
        putJsonObject("subscription") {
            put("type", subscription.type)
            put("coin", subscription.coin)
            subscription.interval?.let { put("interval", it) }
        }
    }.toString()

    private fun decodeFrame(text: String): SocketFrame? = runCatching {
        val root = json.parseToJsonElement(text).jsonObject
        val channel = root.string("channel") ?: return null
        val data = root["data"] ?: return null
        SocketFrame(channel, data)
    }.getOrNull()

    private fun decodePair(element: JsonElement): Pair<JsonElement, JsonElement> {
        val array = element.jsonArray
        require(array.size >= 2) { "Hyperliquid returned incomplete market metadata" }
        return array[0] to array[1]
    }

    /** Spot ctx includes coin and may append outcome rows, so only exact coin joins are safe. */
    private fun decodeSpotContexts(element: JsonElement): Map<String, MarketContext> =
        element.jsonArray.mapNotNull { row ->
            val context = runCatching { json.decodeFromJsonElement<ContextDto>(row) }.getOrNull()
                ?: return@mapNotNull null
            context.coin?.takeIf(String::isNotBlank)?.let { coin -> coin to context.toMarketContext() }
        }.toMap()

    /** Perp ctx omits coin; its documented order matches the same dex's full universe. */
    private fun decodePerpContexts(
        element: JsonElement,
        markets: List<PerpMarketDto>,
    ): Map<String, MarketContext> = element.jsonArray.mapIndexedNotNull { index, row ->
        val market = markets.getOrNull(index) ?: return@mapIndexedNotNull null
        val context = runCatching { json.decodeFromJsonElement<ContextDto>(row) }.getOrNull()
            ?: return@mapIndexedNotNull null
        market.name.takeIf(String::isNotBlank)?.let { coin -> coin to context.toMarketContext() }
    }.toMap()

    private fun JsonElement.asElements(): List<JsonElement> = when (this) {
        is JsonArray -> this
        is JsonObject -> listOf(this)
        else -> emptyList()
    }

    /** The venue supplies quote conventions for rates, FX and indices separately from its market list. */
    private suspend fun loadPerpQuoteHints(): Map<String, String> = try {
        info("perpCategories").jsonArray.mapNotNull { entry ->
            val row = entry as? JsonArray ?: return@mapNotNull null
            val symbol = (row.getOrNull(0) as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val hint = (row.getOrNull(1) as? JsonPrimitive)?.contentOrNull?.lowercase(Locale.ROOT)
            if (hint != null && hint in PAIR_OR_INDEX_QUOTE_HINTS) symbol to hint else null
        }.toMap()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyMap()
    }

    private fun JsonElement?.toFields(): Map<String, String> {
        val text = (this as? JsonPrimitive)?.contentOrNull ?: return emptyMap()
        return text.split('|').mapNotNull { field ->
            val delimiter = field.indexOf(':')
            if (delimiter <= 0) null else field.substring(0, delimiter).trim() to field.substring(delimiter + 1).trim()
        }.toMap()
    }

    private fun renderOutcomeName(
        rawName: String,
        template: OutcomeTemplateDto?,
        fields: Map<String, String>,
    ): String {
        val raw = rawName.removePrefix("template:")
        val delimiter = raw.indexOf(':')
        val templateId = if (delimiter > 0) raw.substring(0, delimiter) else null
        val rawValue = if (delimiter > 0) raw.substring(delimiter + 1).trim() else null
        val inlineValue = rawValue?.takeIf { template != null || templateId in INLINE_OUTCOME_PREFIXES }
        val inlineField = template?.name?.let { PLACEHOLDER.find(it)?.groupValues?.getOrNull(1) }
            ?: templateId
        val mergedFields = inlineField?.takeIf { it.isNotBlank() }?.let { key ->
            inlineValue?.takeIf { it.isNotBlank() }?.let { value -> fields + (key to value) }
        } ?: fields
        val source = template?.name ?: inlineValue ?: raw.replace('_', ' ').replace(CAMEL_CASE_BOUNDARY, " ")
        return renderTemplate(source, mergedFields)
    }

    private fun renderTemplate(template: String, fields: Map<String, String>): String =
        PLACEHOLDER.replace(template) { match -> fields[match.groupValues[1]] ?: "" }
            .replace(MULTIPLE_WHITESPACE, " ")
            .trim(' ', '-', ':', '|', '?')

    private fun recurringPrompt(fields: Map<String, String>, outcomeDescription: String): String {
        val underlying = fields["underlying"] ?: fields["asset"] ?: "Asset"
        val target = fields["targetPrice"] ?: fields["target"]
        val index = fields["index"]?.toIntOrNull()
        val bucket = when {
            outcomeDescription.equals("other", ignoreCase = true) -> "in other bucket"
            index != null -> recurringBucketLabel(fields, index)
            fields["priceThresholds"].isNullOrBlank() -> null
            else -> "in other bucket"
        }
        val wording = when {
            target != null -> "$underlying at least ${formatUsdPrice(target)}"
            bucket != null -> "$underlying price $bucket"
            else -> "Recurring outcome on $underlying"
        }
        val expiry = outcomeExpiry(fields)?.let { READABLE_OUTCOME_FORMAT.format(Instant.ofEpochMilli(it)) }
        val period = fields["period"]?.takeIf(String::isNotBlank)?.let { "$it period" }
        return listOfNotNull(wording, expiry, period).joinToString(" · ")
    }

    private fun fallbackOutcomeLabel(question: OutcomeQuestionDto?): String =
        if (question?.namedOutcomes.orEmpty().size > 1) "Other outcome" else "None of the listed outcomes"

    private fun outcomeSideLabel(
        sideName: String,
        fields: Map<String, String>,
        index: Int,
        templates: Map<String, OutcomeTemplateDto>,
    ): String {
        val templateId = sideName.removePrefix("template:").substringBefore(':')
        val label = renderOutcomeName(sideName, templates[templateId], fields)
        if (label.isNotBlank()) return label
        return fields["index"]?.toIntOrNull()?.let { recurringBucketLabel(fields, it) }
            ?: "Side ${index + 1}"
    }

    private fun recurringBucketLabel(fields: Map<String, String>, index: Int): String {
        val thresholds = fields["priceThresholds"]?.split(',')?.map { it.trim() }?.filter(String::isNotBlank)
            ?: return "in other bucket"
        if (thresholds.isEmpty()) return "in other bucket"
        return when (index) {
            0 -> "below ${formatUsdPrice(thresholds.first())}"
            in 1 until thresholds.size -> "from ${formatUsdPrice(thresholds[index - 1])} to below ${formatUsdPrice(thresholds[index])}"
            thresholds.size -> "at least ${formatUsdPrice(thresholds.last())}"
            else -> "in other bucket"
        }
    }

    private fun formatUsdPrice(value: String): String {
        val trimmed = value.trim()
        if (trimmed.startsWith("\$")) return trimmed
        val number = trimmed.toBigDecimalOrNull() ?: return "\$$trimmed"
        val formatter = NumberFormat.getNumberInstance(Locale.US).apply {
            isGroupingUsed = true
            maximumFractionDigits = number.scale().coerceAtLeast(0)
        }
        return "\$${formatter.format(number)}"
    }

    private fun outcomeExpiry(fields: Map<String, String>): Long? = fields.entries.asSequence()
        .filter { (key, _) -> expiryPriority(key) < Int.MAX_VALUE }
        .sortedBy { (key, _) -> expiryPriority(key) }
        .mapNotNull { (_, value) -> parseOutcomeTime(value) }
        .firstOrNull()

    private fun parseOutcomeTime(value: String): Long? {
        val trimmed = value.trim()
        trimmed.toLongOrNull()?.let { numeric ->
            if (numeric <= 0L) return null
            return if (numeric < EPOCH_MILLISECONDS_THRESHOLD) numeric * MILLIS_PER_SECOND else numeric
        }
        return runCatching { Instant.parse(trimmed).toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(trimmed).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(trimmed, OUTCOME_TIME_FORMAT).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(trimmed, OUTCOME_TIME_SECONDS_FORMAT).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(trimmed).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(trimmed, OUTCOME_DATE_FORMAT).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
    }

    private fun expiryPriority(key: String): Int = when (key.lowercase()) {
        "expiry" -> 0
        "expiresat", "expiration", "resolutiondeadline", "decisiondeadline", "listingdeadline", "deadline" -> 1
        "settlementtime", "resolutiontime", "endtime", "closetime", "time", "datetime" -> 2
        else -> Int.MAX_VALUE
    }

    private fun String?.toFields(): Map<String, String> =
        JsonPrimitive(this ?: "").toFields()

    private fun ContextDto.toMarketContext() = MarketContext(
        previousPrice = prevDayPx?.toDoubleOrNull()?.takeIf(Double::isFinite),
        volumeBase = dayBaseVlm?.toDoubleOrNull()?.takeIf(Double::isFinite),
        volumeQuote = dayNtlVlm?.toDoubleOrNull()?.takeIf(Double::isFinite),
    )

    private fun TradeDto.nativeKey(): String = coin

    private fun TradeDto.toTicker(market: Market, context: MarketContext?): Ticker? {
        val price = px.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        if (time <= 0L) return null
        val previous = context?.previousPrice?.takeIf { it > 0.0 }
        return Ticker(
            key = market.key,
            last = price,
            open24h = previous,
            volumeBase24h = context?.volumeBase,
            volumeQuote24h = context?.volumeQuote,
            changePct24h = previous?.let { (price - it) / it * PERCENT },
            timestamp = time,
        )
    }

    private fun CandleDto.toCandle(now: Long) = Candle(
        openTime = openTime,
        open = o.toDouble(),
        high = h.toDouble(),
        low = l.toDouble(),
        close = c.toDouble(),
        volume = v.toDouble(),
        closed = closeTime < now,
    )

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    private fun BookLevelDto.price(): Double? = px?.toDoubleOrNull()

    private fun String.keyPart(): String =
        uppercase().replace(NON_KEY_CHARACTER, "_").trim('_').ifBlank { "UNKNOWN" }

    private fun pricePrecision(sizeDecimals: Int, maxDecimals: Int): Int =
        (maxDecimals - sizeDecimals).coerceIn(0, maxDecimals)

    private fun Timeframe.apiInterval(): String = when (this) {
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

    private data class PerpDexMetadata(val dex: String, val markets: List<PerpMarketDto>, val contexts: Map<String, MarketContext>)
    private data class MarketContext(val previousPrice: Double?, val volumeBase: Double?, val volumeQuote: Double?)
    private data class SocketFrame(val channel: String, val data: JsonElement)
    private data class Subscription(val type: String, val coin: String, val interval: String? = null) {
        val key: String = listOf(type, coin, interval.orEmpty()).joinToString("\u001f")
    }

    private companion object {
        const val DEFAULT_REST_BASE = "https://api.hyperliquid.xyz"
        const val DEFAULT_WS_BASE = "wss://api.hyperliquid.xyz/ws"
        const val INFO_PATH = "/info"
        const val MAX_PARALLEL_REQUESTS = 8
        const val REST_WEIGHT_CAPACITY = 200.0
        const val REST_WEIGHT_REFILL_PER_SECOND = 15.0
        const val BASE_INFO_WEIGHT = 20
        const val RECENT_TRADES_WEIGHT = 30
        const val CANDLE_WEIGHT_DIVISOR = 60
        const val MAX_CANDLES = 5_000
        const val MINUTE_MS = 60_000L
        const val OUTCOME_PRICE_PRECISION = 4
        const val SPOT_MAX_PRICE_DECIMALS = 8
        const val PERP_MAX_PRICE_DECIMALS = 6
        const val PERCENT = 100.0
        const val DEFAULT_RETRY_MS = 15 * 60_000L
        const val WS_PING_INTERVAL_MS = 45_000L
        const val MAX_DESCRIPTION_CHARS = 1_000
        const val ERROR_BODY_CHARS = 200
        const val PING_FRAME = "{\"method\":\"ping\"}"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val NON_KEY_CHARACTER = Regex("[^A-Z0-9]+")
        val PLACEHOLDER = Regex("""\{([^{}]+)\}""")
        val MULTIPLE_WHITESPACE = Regex("\\s+")
        val CAMEL_CASE_BOUNDARY = Regex("(?<=[a-z])(?=[A-Z])")
        val OUTCOME_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")
        val OUTCOME_TIME_SECONDS_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        val OUTCOME_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        val READABLE_OUTCOME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm 'UTC'", Locale.US)
            .withZone(ZoneOffset.UTC)
        val INLINE_OUTCOME_PREFIXES = setOf("participant", "team", "player", "fallback", "tie", "yesno")
        const val EPOCH_MILLISECONDS_THRESHOLD = 100_000_000_000L
        const val MILLIS_PER_SECOND = 1_000L
    }
}

@Serializable
private data class SpotMetaDto(
    val universe: List<SpotMarketDto> = emptyList(),
    val tokens: List<SpotTokenDto> = emptyList(),
)

@Serializable
private data class SpotMarketDto(
    val tokens: List<Int> = emptyList(),
    val name: String = "",
    val index: Int = 0,
    val isCanonical: Boolean = false,
)

@Serializable
private data class SpotTokenDto(
    val name: String = "",
    val index: Int = 0,
    val szDecimals: Int = 0,
        val fullName: String? = null,
    val deployerLabel: String? = null,
)

@Serializable
private data class PerpDexDto(val name: String? = null)

@Serializable
private data class PerpMetaDto(val universe: List<PerpMarketDto> = emptyList())

@Serializable
private data class PerpMarketDto(
    val name: String = "",
    val szDecimals: Int = 0,
    val isDelisted: Boolean = false,
)

@Serializable
private data class ContextDto(
    val coin: String? = null,
    val prevDayPx: String? = null,
    val dayNtlVlm: String? = null,
    val dayBaseVlm: String? = null,
)

@Serializable
private data class OutcomeMetaDto(
    val outcomes: List<OutcomeDto> = emptyList(),
    val questions: List<OutcomeQuestionDto> = emptyList(),
)

@Serializable
private data class OutcomeDto(
    val outcome: Long = 0,
    val name: String = "",
    val description: String? = null,
    val sideSpecs: List<OutcomeSideDto> = emptyList(),
    val quoteToken: String = "USDC",
    val venue: String = "",
)

@Serializable
private data class OutcomeSideDto(val name: String = "")

@Serializable
private data class OutcomeQuestionDto(
    val question: Long = 0,
    val name: String = "",
    val description: String? = null,
    val fallbackOutcome: Long = 0,
    val namedOutcomes: List<Long> = emptyList(),
    val settledNamedOutcomes: List<Long> = emptyList(),
)

@Serializable
private data class OutcomeTemplateDto(
    val id: String = "",
    val name: String = "",
    val description: String = "",
)

@Serializable
private data class TradeDto(
    val coin: String = "",
    val px: String = "0",
    val time: Long = 0,
)

@Serializable
private data class BookLevelDto(val px: String? = null)

@Serializable
private data class BboDto(
    val coin: String = "",
    val time: Long = 0,
    val bbo: List<BookLevelDto?> = emptyList(),
)

@Serializable
private data class CandleDto(
    @SerialName("t") val openTime: Long = 0,
    @SerialName("T") val closeTime: Long = 0,
    val s: String = "",
    val i: String = "",
    val o: String = "0",
    val h: String = "0",
    val l: String = "0",
    val c: String = "0",
    val v: String = "0",
)
