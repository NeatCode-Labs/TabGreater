package com.neatcode.tabgreater.feature.chart

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.runtime.mutableIntStateOf
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.neatcode.tabgreater.core.model.TGColors

/** Origin the asset loader serves the chart from; also the web-message listener's allow-list. */
const val CHART_ORIGIN: String = "https://appassets.androidplatform.net"

/** The page the WebView loads. */
const val CHART_URL: String = "$CHART_ORIGIN/assets/chart/index.html"

/** Name of the injected bridge object; `chart.js` talks to `window.Native`. */
private const val BRIDGE_OBJECT = "Native"

/**
 * Builds the chart WebView: local assets only, no navigation, no cache, no zoom controls, and the
 * `Native` message listener installed **before** `loadUrl` so it exists at document start.
 *
 * `WebSettingsCompat.setForceDark` is deliberately not called — it is deprecated in webkit 1.17
 * and our CSS owns the theme anyway.
 */
@SuppressLint("SetJavaScriptEnabled")
fun createChartWebView(context: Context, bridge: ChartBridge, debuggable: Boolean): WebView {
    val loader = WebViewAssetLoader.Builder()
        .setDomain("appassets.androidplatform.net")
        .setHttpAllowed(false)
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
        .build()

    return WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        setBackgroundColor(TGColors.BACKGROUND.toInt()) // paint before the first frame: no white flash
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        overScrollMode = View.OVER_SCROLL_NEVER
        isScrollbarFadingEnabled = false
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        isNestedScrollingEnabled = false // WebView implements NestedScrollingChild
        isFocusableInTouchMode = true

        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = false
            // `databaseEnabled` is deprecated (Web SQL is gone) — nothing to switch off.
            allowFileAccess = false
            allowContentAccess = false
            mediaPlaybackRequiresUserGesture = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            textZoom = 100 // the canvas is laid out in CSS pixels: ignore system font scaling
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
        }
        if (debuggable) WebView.setWebContentsDebuggingEnabled(true)

        webViewClient = object : WebViewClientCompat() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                Log.e(CHART_LOG_TAG, "render process gone, crashed=${detail.didCrash()}")
                // Detach and destroy so the OOM-killed renderer cannot take the app down with it.
                (view.parent as? ViewGroup)?.removeView(view)
                ChartWebViewCache.onRenderProcessGone(view)
                view.destroy()
                return true
            }
        }

        check(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            "WebView older than M88 — update Android System WebView"
        }
        WebViewCompat.addWebMessageListener(this, BRIDGE_OBJECT, setOf(CHART_ORIGIN)) { _, message, sourceOrigin, isMainFrame, replyProxy ->
            if (!isMainFrame) return@addWebMessageListener
            if (sourceOrigin.toString() != CHART_ORIGIN) return@addWebMessageListener
            message.data?.let { bridge.handle(it, replyProxy) }
        }

        bridge.webView = this
        bridge.onPageStarted()
        loadUrl(CHART_URL)
    }
}

/**
 * One WebView per process, kept alive across chart-screen entries (the provider init plus the
 * 234 KB bundle parse costs a few hundred milliseconds on a cold open).
 *
 * [trim] is called from `Application.onTrimMemory` at `TRIM_MEMORY_MODERATE` and above — never on
 * `UI_HIDDEN`, or the warm-start benefit is thrown away every time the user leaves the app.
 */
object ChartWebViewCache {

    private var cached: WebView? = null
    private var owner: ChartBridge? = null
    private var hostVisible = false
    private val _generation = mutableIntStateOf(0)

    /**
     * Bumped every time the cached instance dies. `ChartView` reads it as Compose state and keys
     * its whole WebView subtree on it, so a destroyed WebView is rebuilt on the next frame instead
     * of leaving the open chart a blank box (findings 1 / 11 / 23).
     */
    val generation: Int get() = _generation.intValue

    /** The cached WebView, created (and re-created after a renderer crash) on demand. */
    fun obtain(context: Context, bridge: ChartBridge, debuggable: Boolean): WebView {
        owner = bridge
        hostVisible = true
        val existing = cached
        if (existing != null) {
            bridge.webView = existing
            (existing.parent as? ViewGroup)?.removeView(existing)
            return existing
        }
        // Application context: the WebView outlives the activity that first showed it.
        return createChartWebView(context.applicationContext, bridge, debuggable).also { cached = it }
    }

    /**
     * Whether a chart screen is on screen right now: `true` from [obtain] and from the host's
     * `ON_START`, `false` on its `ON_STOP` and when the host is disposed. It is the only thing
     * [trim] consults — a backgrounded chart screen may lose its WebView (the generation counter
     * rebuilds it on return), a visible one may not.
     */
    internal fun setHostVisible(visible: Boolean) {
        hostVisible = visible
    }

    /**
     * Destroys the cached instance so the next [obtain] builds a fresh one. Must run on the main
     * thread ([WebView.destroy] does); the bridge is detached first so nothing can evaluate JS in
     * a destroyed WebView afterwards.
     *
     * A no-op while a chart screen is showing the WebView: `TRIM_MEMORY_RUNNING_CRITICAL` is a
     * *foreground* level, and throwing the canvas away under the user's finger is worse than
     * holding the memory for as long as the chart is on screen (finding 23).
     */
    fun trim() {
        if (hostVisible) {
            Log.i(CHART_LOG_TAG, "trim ignored: the chart screen is showing the WebView")
            return
        }
        destroyCached()
    }

    /** Forgets a WebView whose renderer died; it has already been detached and destroyed. */
    internal fun onRenderProcessGone(view: WebView) {
        if (cached !== view) return
        detachBridge()
        cached = null
        _generation.intValue++
    }

    private fun destroyCached() {
        detachBridge()
        cached?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
            view.destroy()
        }
        cached = null
        _generation.intValue++
    }

    private fun detachBridge() {
        owner?.let { bridge ->
            bridge.close()
            bridge.webView = null
        }
        owner = null
    }
}
