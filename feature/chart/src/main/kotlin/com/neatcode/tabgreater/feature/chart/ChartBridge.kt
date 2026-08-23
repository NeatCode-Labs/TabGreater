package com.neatcode.tabgreater.feature.chart

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import com.neatcode.tabgreater.core.data.repo.MarketRepository
import com.neatcode.tabgreater.core.exchange.ExchangeRegistry
import com.neatcode.tabgreater.core.model.Candle
import com.neatcode.tabgreater.core.model.Market
import com.neatcode.tabgreater.core.model.Timeframe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Logcat tag of every chart diagnostic, native and JS alike (`adb logcat -s chart`). */
const val CHART_LOG_TAG: String = "chart"

/**
 * The Kotlin half of the WebView bridge: it answers `chart.js`'s `getBars` /
 * `subscribeBar` / `unsubscribeBar` RPCs and forwards its `log` / `ready` notices to logcat.
 *
 * One instance per process (it owns the live subscription of the single cached WebView).
 * Adapters come from [ExchangeRegistry] and the market — with its native symbol and price
 * precision — from [MarketRepository], so the JS side only ever names `exchange` + `BASE/QUOTE`.
 */
class ChartBridge(
    private val scope: CoroutineScope,
    private val registry: ExchangeRegistry,
    private val markets: MarketRepository,
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
    private var hostGeneration = 0

    /**
     * `true` once `chart.js` has reported that KLineChart booted; reset by [onPageStarted].
     *
     * A flow rather than a single listener slot: two chart screens overlap for the length of a
     * navigation transition, and each of them must be resumed independently — cancelling one
     * waiter must never drop another's.
     */
    private val readyState = MutableStateFlow(false)

    /** `true` once `chart.js` has reported that KLineChart booted. */
    val isReady: Boolean get() = readyState.value

    /** Suspends until `chart.js` reports that KLineChart booted; returns at once when it already has. */
    suspend fun awaitReady() {
        readyState.first { it }
    }

    /** Called by the WebView factory before `loadUrl`, so a reload starts from a clean state. */
    fun onPageStarted() {
        readyState.value = false
        close()
    }

    /** Handles one message from `chart.js`. Always called on the UI thread by the web listener. */
    fun handle(raw: String, reply: JavaScriptReplyProxy) {
        val req = ChartProtocol.parseRequest(raw) ?: return
        when (req.action) {
            ChartProtocol.ACTION_LOG -> log(req)
            ChartProtocol.ACTION_READY -> {
                Log.i(CHART_LOG_TAG, "klinecharts booted")
                readyState.value = true
            }
            ChartProtocol.ACTION_GET_BARS -> getBars(req, reply)
            ChartProtocol.ACTION_SUBSCRIBE_BAR -> subscribeBar(req, reply)
            ChartProtocol.ACTION_UNSUBSCRIBE_BAR -> {
                close()
                replyOk(reply, req.id, JsonNull)
            }
            else -> Log.w(CHART_LOG_TAG, "unknown action ${req.action}")
        }
    }

    /**
     * Claims the shared WebView for one chart screen. The returned token makes [detachHost] a
     * no-op for a screen that was already replaced — navigating from one market to another
     * composes the new screen before the old one is disposed, and the old one must not tear down
     * the live subscription the new one has just started.
     */
    fun attachHost(): Int = ++hostGeneration

    /** `true` while [token] is still the newest host — a replaced screen must not touch the WebView. */
    fun isCurrentHost(token: Int): Boolean = token == hostGeneration

    /** Releases the claim [attachHost] took; only the current host actually stops the stream. */
    fun detachHost(token: Int) {
        if (isCurrentHost(token)) close()
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
        liveRequest?.let { startLive(it, reply = null, id = null) }
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

    private fun getBars(req: Req, reply: JavaScriptReplyProxy) {
        val p = decode(req, GetBarsReq.serializer()) ?: return replyErr(reply, req.id, "bad getBars payload")
        scope.launch(Dispatchers.IO) {
            val resolved = resolve(p.exchange, p.ticker, p.span, p.unit)
            if (resolved == null) {
                replyErr(reply, req.id, "unknown market ${p.exchange}:${p.ticker} ${p.span}${p.unit}")
                return@launch
            }
            val (market, timeframe) = resolved
            val adapter = registry.getOrNull(market.key.exchange)
                ?: return@launch replyErr(reply, req.id, "no adapter for ${p.exchange}")
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
                replyOk(reply, req.id, ChartProtocol.json.encodeToJsonElement(GetBarsRes.serializer(), res))
            }.onFailure { e ->
                if (e is CancellationException) throw e
                Log.w(CHART_LOG_TAG, "getBars failed for ${market.key}", e)
                replyErr(reply, req.id, e.message ?: "fetch failed")
            }
        }
    }

    private fun subscribeBar(req: Req, reply: JavaScriptReplyProxy) {
        val p = decode(req, SubscribeBarReq.serializer())
            ?: return replyErr(reply, req.id, "bad subscribeBar payload")
        livePaused = false
        liveRequest = p
        startLive(p, reply, req.id)
    }

    private fun startLive(p: SubscribeBarReq, reply: JavaScriptReplyProxy?, id: String?) {
        liveJob?.cancel()
        liveJob = scope.launch(Dispatchers.IO) {
            val resolved = resolve(p.exchange, p.ticker, p.span, p.unit)
            if (resolved == null) {
                if (reply != null) replyErr(reply, id, "unknown market ${p.exchange}:${p.ticker}")
                return@launch
            }
            val (market, timeframe) = resolved
            val adapter = registry.getOrNull(market.key.exchange)
            if (adapter == null) {
                if (reply != null) replyErr(reply, id, "no adapter for ${p.exchange}")
                return@launch
            }
            if (reply != null) replyOk(reply, id, JsonNull)
            var lastPushAt = 0L
            var lastOpenTime = Long.MIN_VALUE
            adapter.watchKlines(market, timeframe)
                .catch { e -> Log.w(CHART_LOG_TAG, "live bars stopped for ${market.key}: ${e.message}") }
                .collect { bar ->
                    // KLineChart redraws the whole canvas per push: 5 Hz for the forming bar, but a
                    // closed bar or a new bucket always goes through so the series never loses one.
                    val now = SystemClock.uptimeMillis()
                    val forced = bar.closed || bar.openTime != lastOpenTime
                    if (!forced && now - lastPushAt < LIVE_PUSH_INTERVAL_MS) return@collect
                    lastPushAt = now
                    lastOpenTime = bar.openTime
                    pushBar(bar)
                }
        }
    }

    private fun pushBar(bar: Candle) {
        val payload = ChartProtocol.json.encodeToString(ChartBar.serializer(), bar.toChartBar())
        evaluate("window.tg&&tg.onBar($payload)")
    }

    // ------------------------------------------------------------------ plumbing

    /** Market + timeframe named by one request, or `null` when either is unknown to the app. */
    private suspend fun resolve(exchange: String, ticker: String, span: Int, unit: String): Pair<Market, Timeframe>? {
        val key = ChartProtocol.marketKeyOf(exchange, ticker) ?: return null
        val timeframe = ChartPeriods.toTimeframe(span, unit) ?: return null
        val market = markets.getMarket(key) ?: return null
        return market to timeframe
    }

    private fun <T> decode(req: Req, serializer: KSerializer<T>): T? =
        runCatching { ChartProtocol.json.decodeFromJsonElement(serializer, req.payload) }
            .onFailure { Log.w(CHART_LOG_TAG, "bad ${req.action} payload: ${it.message}") }
            .getOrNull()

    private fun replyOk(reply: JavaScriptReplyProxy, id: String?, result: JsonElement) {
        if (id == null) return
        post(reply, buildJsonObject { put("id", id); put("result", result) }.toString())
    }

    private fun replyErr(reply: JavaScriptReplyProxy, id: String?, message: String) {
        if (id == null) return
        post(reply, buildJsonObject { put("id", id); put("error", message) }.toString())
    }

    /** `JavaScriptReplyProxy` and `WebView` are both UI-thread bound. */
    private fun post(reply: JavaScriptReplyProxy, body: String) {
        main.post { reply.postMessage(body) }
    }

    private fun evaluate(js: String) {
        val view = webView ?: return
        main.post { view.evaluateJavascript(js, null) }
    }

    private companion object {
        const val LIVE_PUSH_INTERVAL_MS = 200L
    }
}
