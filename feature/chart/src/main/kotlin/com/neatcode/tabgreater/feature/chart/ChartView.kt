package com.neatcode.tabgreater.feature.chart

import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.neatcode.tabgreater.core.model.Market
import com.neatcode.tabgreater.core.model.Timeframe
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/**
 * The KLineChart canvas: the process-wide cached WebView, driven entirely through the `tg.*` API.
 *
 * The screen around it must **not** scroll — a `verticalScroll`/`LazyColumn` ancestor would win the
 * vertical drag through nested scrolling and the y-axis rescale would feel dead
 *.
 *
 * @param autoScaleTick bump this to run the "auto" action (undo a manual y-axis drag).
 * @param debuggable enables WebView contents debugging; pass `BuildConfig.DEBUG`.
 */
@Composable
fun ChartView(
    market: Market,
    timeframe: Timeframe,
    indicators: List<IndicatorSpec>,
    candleType: CandleType,
    logScale: Boolean,
    autoScaleTick: Int,
    bridge: ChartBridge,
    modifier: Modifier = Modifier,
    debuggable: Boolean = false,
) {
    // A renderer crash (or a trim while the screen was away) destroys the cached WebView; the
    // cache bumps its generation and the whole subtree below is rebuilt around a fresh instance.
    // `key` rather than `remember(generation)`: AndroidView's factory only runs for a new node.
    key(ChartWebViewCache.generation) {
        ChartCanvas(
            market = market,
            timeframe = timeframe,
            indicators = indicators,
            candleType = candleType,
            logScale = logScale,
            autoScaleTick = autoScaleTick,
            bridge = bridge,
            modifier = modifier,
            debuggable = debuggable,
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ChartCanvas(
    market: Market,
    timeframe: Timeframe,
    indicators: List<IndicatorSpec>,
    candleType: CandleType,
    logScale: Boolean,
    autoScaleTick: Int,
    bridge: ChartBridge,
    modifier: Modifier,
    debuggable: Boolean,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val webView = remember { ChartWebViewCache.obtain(context, bridge, debuggable) }
    val hostToken = remember { bridge.attachHost() }
    var booted by remember { mutableStateOf(false) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    AndroidView(
        factory = { webView },
        modifier = modifier
            // Hand every gesture to the WebView: no Compose ancestor may steal it.
            .pointerInteropFilter { false }
            .onSizeChanged { size = it },
        // Deliberately NOT detaching here: the WebView is shared, and navigating from one market
        // to another disposes this screen *after* the next one has already re-parented it, so an
        // unconditional detach would rip the canvas out of the incoming screen. The token-guarded
        // `DisposableEffect` below is what detaches when this screen really is the last owner.
        onRelease = { },
    )

    // One atomic swap per market/timeframe change, so only one getBars('init') goes out.
    LaunchedEffect(market, timeframe) {
        bridge.awaitReady()
        val symbol = ChartProtocol.json.encodeToString(ChartSymbol.serializer(), market.toChartSymbol())
        val period = ChartProtocol.json.encodeToString(ChartPeriod.serializer(), ChartPeriods.of(timeframe))
        // KLineChart renders `{span:1,type:'minute'}` as a bare "1"; the legend gets our own label.
        val label = ChartProtocol.json.encodeToString(String.serializer(), timeframe.label)
        webView.eval("tg.setMarket($symbol,$period,$label)")
        booted = true
    }
    LaunchedEffect(indicators, booted) {
        if (!booted) return@LaunchedEffect
        val payload = ChartProtocol.json.encodeToString(JsIndicatorSpec.listSerializer, indicators.map(::JsIndicatorSpec))
        webView.eval("tg.setIndicators($payload)")
    }
    LaunchedEffect(candleType, booted) {
        if (booted) webView.eval("tg.setCandleType('${candleType.jsValue}')")
    }
    LaunchedEffect(logScale, booted) {
        if (booted) webView.eval("tg.setScale('${if (logScale) "log" else "normal"}')")
    }
    LaunchedEffect(autoScaleTick) {
        if (booted && autoScaleTick > 0) webView.eval("tg.resetAutoScale()")
    }
    // KLineChart resizes itself through a ResizeObserver; this is the belt-and-braces call for
    // the fullscreen/rotation swap, where the canvas would otherwise keep the old bounds.
    LaunchedEffect(size, booted) {
        if (booted && size != IntSize.Zero) webView.eval("tg.resize()")
    }

    // The live kline stream and the canvas repaint it drives stop with the host activity: leaving
    // the app must not keep a WebSocket and a 5 Hz redraw alive for a window nobody can see.
    // `pauseTimers()`/`resumeTimers()` are process-global on WebView and are deliberately not used.
    DisposableEffect(lifecycleOwner, webView) {
        val observer = LifecycleEventObserver { _, event ->
            if (!bridge.isCurrentHost(hostToken)) return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    ChartWebViewCache.setHostVisible(false)
                    bridge.pauseLive()
                    webView.onPause()
                }
                Lifecycle.Event.ON_START -> {
                    ChartWebViewCache.setHostVisible(true)
                    webView.onResume()
                    bridge.resumeLive()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(hostToken) {
        onDispose {
            // Only the last owner detaches: when a newer chart screen has already re-parented the
            // shared WebView through `obtain`, this holder no longer has it as a child anyway, and
            // removing it would blank the incoming screen.
            if (bridge.isCurrentHost(hostToken)) {
                (webView.parent as? ViewGroup)?.removeView(webView)
                ChartWebViewCache.setHostVisible(false)
            }
            bridge.detachHost(hostToken)
        }
    }
}

/** [IndicatorSpec] in the exact shape `chart.js` expects (`pane` as `"main"` / `"sub"`). */
@Serializable
private data class JsIndicatorSpec(
    val name: String,
    val calcParams: List<Int>,
    val pane: String,
) {
    constructor(spec: IndicatorSpec) : this(spec.name, spec.calcParams, spec.pane.jsValue)

    companion object {
        val listSerializer = ListSerializer(serializer())
    }
}

private fun WebView.eval(js: String) = post { evaluateJavascript(js, null) }
