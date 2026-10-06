package com.neatcode.tabgreater.feature.chart

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import com.neatcode.tabgreater.core.data.repo.ChartDrawingRepository
import com.neatcode.tabgreater.core.data.repo.MarketRepository
import com.neatcode.tabgreater.core.exchange.ExchangeFailureKind
import com.neatcode.tabgreater.core.exchange.ExchangeRegistry
import com.neatcode.tabgreater.core.exchange.canRetryAutomatically
import com.neatcode.tabgreater.core.exchange.exchangeFailureKind
import com.neatcode.tabgreater.core.exchange.retryDelayMs
import com.neatcode.tabgreater.core.model.Candle
import com.neatcode.tabgreater.core.model.Market
import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.Timeframe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.ConcurrentHashMap

/** Logcat tag of every chart diagnostic, native and JS alike (`adb logcat -s chart`). */
const val CHART_LOG_TAG: String = "chart"

/**
 * The Kotlin half of the WebView bridge: it answers `chart.js`'s `getBars` /
 * `subscribeBar` / `unsubscribeBar` RPCs, forwards its `log` / `ready` notices to logcat, persists
 * the user's drawings (`drawingsChanged`) and mirrors the drawing layer's state (`drawingState`).
 *
 * One instance per process (it owns the live subscription of the single cached WebView).
 * Adapters come from [ExchangeRegistry] and the market — with its native symbol and price
 * precision — from [MarketRepository], so the JS side only ever names `exchange` + `BASE/QUOTE`.
 */
class ChartBridge(
    private val scope: CoroutineScope,
    private val registry: ExchangeRegistry,
    private val markets: MarketRepository,
    private val drawings: ChartDrawingRepository,
) {

    /** Set by [ChartWebViewCache] when the WebView is created; live bars are pushed into it. */
    @Volatile
    var webView: WebView? = null

    private var liveJob: Job? = null

    /** The request [resumeLive] replays; kept across a [pauseLive] so no `getBars` round is needed. */
    private var liveRequest: SubscribeBarReq? = null
    private var livePaused = false

    /**
     * Replies and live pushes go through the main looper rather than `WebView.post`: a detached
     * WebView queues posted runnables until it is attached again, which would strand every RPC
     * while the screen is being swapped for another market.
     */
    private val main = Handler(Looper.getMainLooper())

    /** Incremented per [ChartView] that mounts the shared WebView; see [attachHost]. */
    @Volatile
    private var hostGeneration = 0L

    @Volatile
    private var previousHostGeneration = -1L

    @Volatile
    private var targetGeneration = 0L

    @Volatile
    private var previousTargetGeneration = -1L

    @Volatile
    private var pageGeneration = 0L

    internal val activePageToken: Long get() = pageGeneration

    @Volatile
    private var currentTarget: ChartTarget? = null

    @Volatile
    private var previousTarget: ChartTarget? = null

    private val recoveryPolicy = RendererRecoveryPolicy()

    /**
     * `true` once `chart.js` has reported that KLineChart booted; reset by [onPageStarted].
     *
     * A flow rather than a single listener slot: two chart screens overlap for the length of a
     * navigation transition, and each of them must be resumed independently — cancelling one
     * waiter must never drop another's.
     */
    private val readyState = MutableStateFlow(false)

    private val availabilityState = MutableStateFlow<ChartAvailability>(ChartAvailability.Loading(null))

    /** Public UI state; `Ready` means the renderer booted and its first history request succeeded. */
    val availability: StateFlow<ChartAvailability> = availabilityState.asStateFlow()

    /** `true` once `chart.js` has reported that KLineChart booted. */
    val isReady: Boolean get() = readyState.value

    private val drawingStateFlow = MutableStateFlow(DrawingState.IDLE)

    /**
     * What the drawing layer is doing (placing a tool, a selection, the drawing count). Back to
     * [DrawingState.IDLE] when the page reloads and when the chart switches market or timeframe.
     */
    val drawingState: StateFlow<DrawingState> = drawingStateFlow.asStateFlow()

    /**
     * The newest drawing set per market reported in this process, ahead of Room: a save is still
     * in flight when a timeframe swap asks for the same market's drawings a few milliseconds
     * after the user finished one, and restoring the older Room copy would silently undo it.
     */
    private val latestDrawings = ConcurrentHashMap<MarketKey, String>()

    /** Saves in the order the page reported them; one consumer, so a later set never loses to an earlier one. */
    private val saves = Channel<Pair<MarketKey, String>>(Channel.UNLIMITED)

    init {
        scope.launch(Dispatchers.IO) {
            for ((key, json) in saves) {
                try {
                    drawings.save(key, json)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(CHART_LOG_TAG, "saving drawings for $key failed", e)
                }
            }
        }
    }

    /** Suspends until `chart.js` reports that KLineChart booted; returns at once when it already has. */
    suspend fun awaitReady() {
        readyState.first { it }
    }

    /** Called before creating a WebView when this device's provider lacks the required bridge. */
    fun reportUnsupported(target: ChartTarget) {
        currentTarget = target
        availabilityState.value = ChartAvailability.Unsupported(target)
        readyState.value = false
    }

    /** Surface a provider or WebView construction failure without letting it escape Compose. */
    fun reportUnavailable(
        target: ChartTarget,
        message: String = "The chart renderer could not be started.",
        retryable: Boolean = true,
    ) {
        currentTarget = target
        readyState.value = false
        availabilityState.value = ChartAvailability.Unavailable(
            target = target,
            message = message,
            retryable = retryable,
        )
    }

    /** Called by the WebView factory before `loadUrl`, so a reload starts from a clean state. */
    fun onPageStarted(): Long {
        pageGeneration++
        readyState.value = false
        drawingStateFlow.value = DrawingState.IDLE
        currentTarget?.let { availabilityState.value = ChartAvailability.Loading(it) }
        close()
        return pageGeneration
    }

    /** Begins a market or timeframe transition and invalidates every older asynchronous reply. */
    fun onMarketChanged(target: ChartTarget, hostToken: Long): Long {
        if (!isCurrentHost(hostToken)) return targetGeneration
        previousTarget = currentTarget
        previousTargetGeneration = targetGeneration
        currentTarget = target
        targetGeneration++
        drawingStateFlow.value = DrawingState.IDLE
        availabilityState.value = ChartAvailability.Loading(target)
        close()
        return targetGeneration
    }

    /**
     * Called right before `tg.setMarket`: the page drops its selection and any overlay being placed
     * with the old series, so the state it reported no longer holds.
     */
    fun onMarketChanged() {
        drawingStateFlow.value = DrawingState.IDLE
    }

    /** Returns whether a dead renderer gets its single automatic reconstruction. */
    fun onRendererFailure(
        pageToken: Long,
        message: String = "The chart renderer stopped unexpectedly.",
    ): Boolean {
        if (pageToken != pageGeneration) return false
        pageGeneration++
        readyState.value = false
        close()
        val target = currentTarget
        return when (recoveryPolicy.onRendererFailure()) {
            RecoveryDecision.RETRY_AUTOMATICALLY -> {
                if (target != null) availabilityState.value = ChartAvailability.Loading(target)
                true
            }
            RecoveryDecision.MANUAL_RETRY_REQUIRED -> {
                if (target != null) {
                    availabilityState.value = ChartAvailability.Unavailable(
                        target = target,
                        message = message,
                        retryable = true,
                    )
                }
                false
            }
        }
    }

    /** Re-enables one automatic renderer recovery and marks a user-requested retry as in progress. */
    fun onManualRetry() {
        recoveryPolicy.onManualRetry()
        readyState.value = false
        close()
        currentTarget?.let { availabilityState.value = ChartAvailability.Loading(it) }
    }

    private fun markDataReady(req: Req, pageToken: Long) {
        if (!isCurrentRequest(req, pageToken)) return
        val target = currentTarget ?: return
        availabilityState.value = ChartAvailability.Ready(target)
        recoveryPolicy.onHealthyChart()
    }

    private fun markDataUnavailable(req: Req, pageToken: Long, error: ChartRpcFailure) {
        if (!isCurrentRequest(req, pageToken)) return
        val target = currentTarget ?: return
        availabilityState.value = ChartAvailability.Unavailable(
            target = target,
            message = error.error,
            retryable = error.retryable,
            retryAfterMs = error.retryAfterMs,
            failureKind = runCatching { ExchangeFailureKind.valueOf(error.failureKind) }.getOrNull(),
        )
    }

    /**
     * The `tg.setDrawings` argument for [market]: its drawings from this process's newest report
     * or from Room, sanitised, `"drawings":[]` when there are none or they cannot be read.
     *
     * @param magnet when given, every drawing is restored with this mode: the magnet setting
     *   overrides all drawings, and a switch of it alone is not a drawing change the page saves.
     */
    suspend fun drawingsPayloadFor(market: Market, magnet: MagnetMode? = null): String {
        val key = market.key
        val stored = latestDrawings[key] ?: withContext(Dispatchers.IO) {
            try {
                drawings.load(key)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(CHART_LOG_TAG, "loading drawings for $key failed", e)
                null
            }
        }
        val restored = DrawingsCodec.decode(stored).let { list ->
            if (magnet == null) list else list.map { it.copy(mode = DrawingsCodec.overlayMode(magnet)) }
        }
        return DrawingsCodec.encodePayload(
            DrawingsPayload(exchange = key.exchange.id, ticker = key.pair, drawings = restored),
        )
    }

    /** Handles one message from `chart.js`. Always called on the UI thread by the web listener. */
    fun handle(raw: String, reply: JavaScriptReplyProxy, pageToken: Long = pageGeneration) {
        if (pageToken != pageGeneration) return
        val req = ChartProtocol.parseRequest(raw) ?: return
        when (req.action) {
            ChartProtocol.ACTION_LOG -> log(req)
            ChartProtocol.ACTION_READY -> {
                Log.i(CHART_LOG_TAG, "klinecharts booted")
                readyState.value = true
            }
            ChartProtocol.ACTION_GET_BARS -> {
                if (!isCurrentRequest(req, pageToken)) return staleReply(reply, req)
                getBars(req, reply, pageToken)
            }
            ChartProtocol.ACTION_SUBSCRIBE_BAR -> {
                if (!isCurrentRequest(req, pageToken)) return staleReply(reply, req)
                subscribeBar(req, reply, pageToken)
            }
            ChartProtocol.ACTION_UNSUBSCRIBE_BAR -> {
                if (!isCurrentRequest(req, pageToken)) return staleReply(reply, req)
                close()
                replyOk(reply, req.id, JsonNull)
            }
            ChartProtocol.ACTION_DRAWINGS_CHANGED -> {
                if (isCurrentDrawingNotice(req, pageToken)) drawingsChanged(req)
            }
            ChartProtocol.ACTION_DRAWING_STATE -> {
                if (!isCurrentRequest(req, pageToken)) return
                decode(req, DrawingState.serializer())?.let { drawingStateFlow.value = it }
            }
            ChartProtocol.ACTION_DATA_STATE -> {
                if (!isCurrentRequest(req, pageToken)) return
                decode(req, ChartDataState.serializer())?.let { state ->
                    if (state.ready) {
                        markDataReady(req, pageToken)
                    } else {
                        markDataUnavailable(
                            req,
                            pageToken,
                            ChartRpcFailure(
                                error = state.error ?: "Chart data is temporarily unavailable.",
                                failureKind = state.failureKind ?: ExchangeFailureKind.TRANSIENT.name,
                                retryable = state.retryable,
                                retryAfterMs = state.retryAfterMs,
                            ),
                        )
                    }
                }
            }
            else -> Log.w(CHART_LOG_TAG, "unknown action ${req.action}")
        }
    }

    private fun staleReply(reply: JavaScriptReplyProxy, req: Req) {
        replyErr(
            reply,
            req.id,
            "This chart request was superseded.",
            ExchangeFailureKind.DEFERRED,
            retryable = true,
        )
    }

    /**
     * Claims the shared WebView for one chart screen. The returned token makes [detachHost] a
     * no-op for a screen that was already replaced — navigating from one market to another
     * composes the new screen before the old one is disposed, and the old one must not tear down
     * the live subscription the new one has just started.
     */
    fun attachHost(): Long {
        previousHostGeneration = hostGeneration
        previousTargetGeneration = targetGeneration
        hostGeneration++
        return hostGeneration
    }

    /** `true` while [token] is still the newest host — a replaced screen must not touch the WebView. */
    fun isCurrentHost(token: Long): Boolean = token == hostGeneration

    /** `true` only while this host still owns the active market/timeframe and loaded page. */
    fun isCurrentChart(hostToken: Long, targetToken: Long, pageToken: Long): Boolean =
        isCurrentHost(hostToken) && targetToken == targetGeneration && pageToken == pageGeneration

    /** Releases the claim [attachHost] took; only the current host actually stops the stream. */
    fun detachHost(token: Long) {
        if (isCurrentHost(token)) close()
    }

    private fun isCurrentRequest(req: Req, pageToken: Long): Boolean =
        pageToken == pageGeneration &&
            (req.hostGeneration == null || req.hostGeneration == hostGeneration) &&
            (req.targetGeneration == null || req.targetGeneration == targetGeneration)

    private fun isCurrentDrawingNotice(req: Req, pageToken: Long): Boolean {
        if (pageToken != pageGeneration) return false
        if (req.hostGeneration == null && req.targetGeneration == null) return true
        if (req.hostGeneration == null || req.targetGeneration == null) return false
        if (req.hostGeneration == hostGeneration && req.targetGeneration == targetGeneration) return true
        val payload = DrawingsCodec.decodePayload(req.payload) ?: return false
        val key = ChartProtocol.marketKeyOf(payload.exchange, payload.ticker) ?: return false
        // setMarket flushes the outgoing market's drawings before it updates JS's generation tags.
        return req.hostGeneration == previousHostGeneration &&
            req.targetGeneration == previousTargetGeneration &&
            key == previousTarget?.market
    }

    /** Stops the live stream (the chart is being disposed). */
    fun close() {
        liveJob?.cancel()
        liveJob = null
        liveRequest = null
        livePaused = false
    }

    /**
     * Stops the live bar stream while the host activity is stopped, remembering what to replay.
     * Unlike [close] this costs no `getBars('init')` on the way back — [resumeLive] re-opens the
     * same kline subscription and KLineChart keeps every bar it already holds.
     */
    fun pauseLive() {
        if (livePaused) return
        livePaused = true
        liveJob?.cancel()
        liveJob = null
    }

    /** Undoes [pauseLive]; a no-op when nothing was paused or the screen has since been closed. */
    fun resumeLive() {
        if (!livePaused) return
        livePaused = false
        liveRequest?.let {
            startLive(it, reply = null, id = null, pageToken = pageGeneration, hostTag = hostGeneration, targetTag = targetGeneration)
        }
    }

    // ------------------------------------------------------------------ actions

    private fun log(req: Req) {
        val payload = runCatching { ChartProtocol.json.decodeFromJsonElement(LogPayload.serializer(), req.payload) }
            .getOrElse { LogPayload(text = req.payload.toString()) }
        when (payload.kind) {
            "error" -> Log.e(CHART_LOG_TAG, payload.text)
            "warn" -> Log.w(CHART_LOG_TAG, payload.text)
            else -> Log.d(CHART_LOG_TAG, payload.text)
        }
    }

    private fun drawingsChanged(req: Req) {
        val payload = DrawingsCodec.decodePayload(req.payload)
        val key = payload?.let { ChartProtocol.marketKeyOf(it.exchange, it.ticker) }
        if (payload == null || key == null) {
            Log.w(CHART_LOG_TAG, "drawingsChanged without a valid market")
            return
        }
        val json = DrawingsCodec.encode(payload.drawings)
        latestDrawings[key] = json
        saves.trySend(key to json)
    }

    private fun getBars(req: Req, reply: JavaScriptReplyProxy, pageToken: Long) {
        val current = { isCurrentRequest(req, pageToken) }
        val p = decode(req, GetBarsReq.serializer()) ?: return replyErr(
            reply, req.id, "bad getBars payload", ExchangeFailureKind.INVALID_RESPONSE, retryable = false, guard = current,
        )
        scope.launch(Dispatchers.IO) {
            val resolved = resolve(p.exchange, p.ticker, p.span, p.unit)
            if (resolved == null) {
                val failure = ChartRpcFailure(
                    error = "unknown market ${p.exchange}:${p.ticker} ${p.span}${p.unit}",
                    failureKind = ExchangeFailureKind.INVALID_MARKET.name,
                    retryable = false,
                )
                replyErr(reply, req.id, failure.error, ExchangeFailureKind.INVALID_MARKET, retryable = false, guard = current)
                return@launch
            }
            val (market, timeframe) = resolved
            if (!isCurrentTarget(market, timeframe)) {
                replyErr(
                    reply, req.id, "chart request does not match the active market", ExchangeFailureKind.INVALID_MARKET,
                    retryable = false, guard = current,
                )
                return@launch
            }
            val adapter = registry.getOrNull(market.key.exchange)
                ?: run {
                    return@launch replyErr(
                        reply, req.id, "no adapter for ${p.exchange}", ExchangeFailureKind.INVALID_MARKET,
                        retryable = false, guard = current,
                    )
                }
            runCatching {
                val endTime = ChartProtocol.endTimeFor(p.type, p.timestamp)
                // KLineChart's `forward` branch is a bare `newBars.concat(dataList)` with no
                // de-duplication, so a venue that treats endTime as inclusive would draw the seam
                // bar twice. Adapters honour the exclusive contract; this is the backstop.
                val bars = adapter.fetchOHLCV(market, timeframe, endTime, p.limit)
                    .filter { endTime == null || it.openTime < endTime }
                GetBarsRes(
                    bars = bars.map { it.toChartBar() },
                    hasMoreOlder = ChartProtocol.hasMoreOlder(market.key.exchange, bars.size, p.limit),
                )
            }.onSuccess { res ->
                Log.d(CHART_LOG_TAG, "getBars ${p.type} ${market.key} ${timeframe.id} -> ${res.bars.size}")
                // Receiving bars is not the same as displaying them. The JS dataState notice
                // marks readiness only after its current-generation callback replaces the series.
                replyOk(reply, req.id, ChartProtocol.json.encodeToJsonElement(GetBarsRes.serializer(), res), current)
            }.onFailure { e ->
                if (e is CancellationException) throw e
                Log.w(CHART_LOG_TAG, "getBars failed for ${market.key}", e)
                val failure = ChartRpcFailure(
                    error = e.message ?: "fetch failed",
                    failureKind = e.exchangeFailureKind().name,
                    retryable = e.canRetryAutomatically(),
                    retryAfterMs = e.retryDelayMs(),
                )
                replyErr(
                    reply, req.id, failure.error, e.exchangeFailureKind(), failure.retryable,
                    failure.retryAfterMs, current,
                )
            }
        }
    }

    private fun subscribeBar(req: Req, reply: JavaScriptReplyProxy, pageToken: Long) {
        val p = decode(req, SubscribeBarReq.serializer())
            ?: return replyErr(
                reply, req.id, "bad subscribeBar payload", ExchangeFailureKind.INVALID_RESPONSE, retryable = false,
            )
        livePaused = false
        liveRequest = p
        startLive(p, reply, req.id, pageToken, req.hostGeneration ?: hostGeneration, req.targetGeneration ?: targetGeneration)
    }

    private fun startLive(
        p: SubscribeBarReq,
        reply: JavaScriptReplyProxy?,
        id: String?,
        pageToken: Long,
        hostTag: Long,
        targetTag: Long,
    ) {
        val current = { pageToken == pageGeneration && hostTag == hostGeneration && targetTag == targetGeneration }
        liveJob?.cancel()
        liveJob = scope.launch(Dispatchers.IO) {
            val resolved = resolve(p.exchange, p.ticker, p.span, p.unit)
            if (resolved == null) {
                if (reply != null) replyErr(
                    reply, id, "unknown market ${p.exchange}:${p.ticker}", ExchangeFailureKind.INVALID_MARKET,
                    retryable = false, guard = current,
                )
                return@launch
            }
            val (market, timeframe) = resolved
            if (!isCurrentTarget(market, timeframe)) {
                if (reply != null) replyErr(
                    reply, id, "chart request does not match the active market", ExchangeFailureKind.INVALID_MARKET,
                    retryable = false, guard = current,
                )
                return@launch
            }
            val adapter = registry.getOrNull(market.key.exchange)
            if (adapter == null) {
                if (reply != null) replyErr(
                    reply, id, "no adapter for ${p.exchange}", ExchangeFailureKind.INVALID_MARKET,
                    retryable = false, guard = current,
                )
                return@launch
            }
            if (reply != null) replyOk(reply, id, JsonNull, current)
            var lastPushAt = 0L
            var lastOpenTime = Long.MIN_VALUE
            adapter.watchKlines(market, timeframe)
                .catch { e -> Log.w(CHART_LOG_TAG, "live bars stopped for ${market.key}: ${e.message}") }
                .collect { bar ->
                    if (!current()) return@collect
                    // KLineChart redraws the whole canvas per push: 5 Hz for the forming bar, but a
                    // closed bar or a new bucket always goes through so the series never loses one.
                    val now = SystemClock.uptimeMillis()
                    val forced = bar.closed || bar.openTime != lastOpenTime
                    if (!forced && now - lastPushAt < LIVE_PUSH_INTERVAL_MS) return@collect
                    lastPushAt = now
                    lastOpenTime = bar.openTime
                    pushBar(bar, current)
                }
        }
    }

    private fun pushBar(bar: Candle, current: () -> Boolean) {
        val payload = ChartProtocol.json.encodeToString(ChartBar.serializer(), bar.toChartBar())
        evaluate("window.tg&&tg.onBar($payload)", current)
    }

    // ------------------------------------------------------------------ plumbing

    /** Market + timeframe named by one request, or `null` when either is unknown to the app. */
    private suspend fun resolve(exchange: String, ticker: String, span: Int, unit: String): Pair<Market, Timeframe>? {
        val key = ChartProtocol.marketKeyOf(exchange, ticker) ?: return null
        val timeframe = ChartPeriods.toTimeframe(span, unit) ?: return null
        val market = markets.getMarket(key) ?: return null
        return market to timeframe
    }

    private fun isCurrentTarget(market: Market, timeframe: Timeframe): Boolean =
        currentTarget?.let { it.market == market.key && it.timeframe == timeframe } == true

    private fun <T> decode(req: Req, serializer: KSerializer<T>): T? =
        runCatching { ChartProtocol.json.decodeFromJsonElement(serializer, req.payload) }
            .onFailure { Log.w(CHART_LOG_TAG, "bad ${req.action} payload: ${it.message}") }
            .getOrNull()

    private fun replyOk(
        reply: JavaScriptReplyProxy,
        id: String?,
        result: JsonElement,
        guard: (() -> Boolean)? = null,
    ) {
        if (id == null) return
        post(reply, buildJsonObject { put("id", id); put("result", result) }.toString(), guard)
    }

    private fun replyErr(
        reply: JavaScriptReplyProxy,
        id: String?,
        message: String,
        failureKind: ExchangeFailureKind = ExchangeFailureKind.INVALID_RESPONSE,
        retryable: Boolean = false,
        retryAfterMs: Long = 0L,
        guard: (() -> Boolean)? = null,
    ) {
        if (id == null) return
        post(
            reply,
            buildJsonObject {
                put("id", id)
                put("error", message)
                put("failureKind", failureKind.name)
                put("retryable", retryable)
                put("retryAfterMs", retryAfterMs)
            }.toString(),
            guard,
        )
    }

    /** `JavaScriptReplyProxy` and `WebView` are both UI-thread bound. */
    private fun post(reply: JavaScriptReplyProxy, body: String, guard: (() -> Boolean)? = null) {
        main.post { if (guard == null || guard()) reply.postMessage(body) }
    }

    private fun evaluate(js: String, guard: (() -> Boolean)? = null) {
        val view = webView ?: return
        main.post { if (guard == null || guard()) view.evaluateJavascript(js, null) }
    }

    private companion object {
        const val LIVE_PUSH_INTERVAL_MS = 200L
    }
}
