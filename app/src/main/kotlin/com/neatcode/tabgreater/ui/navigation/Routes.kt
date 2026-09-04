package com.neatcode.tabgreater.ui.navigation

import kotlinx.serialization.Serializable

/** The tile grid — the start destination of an ordinary launch (a widget tap starts on the chart). */
@Serializable
data object WatchlistsRoute

/** Settings, reachable from the bottom navigation bar. */
@Serializable
data object SettingsRoute

/** About · data sources · disclaimer · third-party licences (Settings → ABOUT). */
@Serializable
data object AboutRoute

/** "+ Ticker" market search that appends the picked markets to [watchlistId]. */
@Serializable
data class SearchRoute(val watchlistId: Long)

/**
 * The chart screen for one market. [key] is the canonical `exchange:BASE/QUOTE` string.
 *
 * The route is also reachable from outside the app as `tabgreater://chart/{key}` with the key
 * URL-encoded (`binance%3ABTC%2FEUR`) — the link the home-screen widgets fire. That link is not
 * declared on the route: it is parsed by [chartDeepLinkKey] and turned into this route by hand, so
 * the chart ends up as the only back-stack entry (see `TabGreaterRoot`).
 */
@Serializable
data class ChartRoute(val key: String)

/** Scheme + host of the chart link; the single path segment is the encoded market key. */
const val CHART_DEEP_LINK_BASE: String = "tabgreater://chart"
