package com.neatcode.tabgreater.ui.navigation

import com.neatcode.tabgreater.core.model.MarketKey
import java.net.URLDecoder

/**
 * The market behind a `tabgreater://chart/{key}` link, or `null` for anything else.
 *
 * The link is parsed here instead of being declared on `ChartRoute` as a `navDeepLink`: a declared
 * deep link makes the graph build the synthetic back stack `[Watchlists, Chart]`, which flashes the
 * grid on the way in and drops the user into the app on the way out — the opposite of what a tap on
 * a home-screen widget should do.
 *
 * [uri] is the link in string form (`intent.data?.toString()`), so the whole rule — scheme, host,
 * exactly one percent-encoded path segment, a parsable market key — stays a pure function the JVM
 * tests can exercise without an Android `Uri`.
 */
fun chartDeepLinkKey(uri: String?): MarketKey? {
    val prefix = "$CHART_DEEP_LINK_BASE/"
    if (uri == null || !uri.startsWith(prefix)) return null
    val segment = uri.substring(prefix.length)
    // The key is encoded whole (`binance%3ABTC%2FEUR`), so a bare '/', '?' or '#' after the host
    // can only come from a link this app never sends.
    if (segment.isEmpty() || segment.any { it == '/' || it == '?' || it == '#' }) return null
    val decoded = try {
        // The charset-name overload on purpose: `decode(String, Charset)` needs API 33, minSdk is 26.
        URLDecoder.decode(segment, "UTF-8")
    } catch (_: IllegalArgumentException) {
        return null // a truncated escape ("%E"); nothing to open
    }
    return MarketKey.parseOrNull(decoded)
}
