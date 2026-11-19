package com.neatcode.tabgreater.ui.watchlist

import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.exchange.ExchangeFailureKind

enum class PriceRefreshStatus { EMPTY, CURRENT, REFRESHING, STALE, ERROR }

enum class PriceIssueKind { OLD, UNCONFIRMED, ERROR }

data class PriceRefreshIssue(
    val key: MarketKey,
    val kind: PriceIssueKind,
    /** Friendly instrument or outcome label; falls back to the canonical pair for old callers. */
    val marketLabel: String? = null,
    /** Exchange trade time, not the time the app received or cached that trade. */
    val lastTradeAtEpochMs: Long? = null,
    val lastTradePrice: String? = null,
    val lastPriceReceivedAtEpochMs: Long? = null,
    val failure: ExchangeFailureKind? = null,
    val hasDisplayedPrice: Boolean = false,
)
