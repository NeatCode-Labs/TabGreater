package com.neatcode.tabgreater.ui.navigation

import kotlinx.serialization.Serializable

/** The tile grid — the app's start destination. */
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
 * URL-encoded (`binance%3ABTC%2FEUR`) — that is the deep link the home-screen widgets fire.
 */
@Serializable
data class ChartRoute(val key: String)

/** Scheme + host of [ChartRoute]'s deep link; the single path segment is the encoded market key. */
const val CHART_DEEP_LINK_BASE: String = "tabgreater://chart"
