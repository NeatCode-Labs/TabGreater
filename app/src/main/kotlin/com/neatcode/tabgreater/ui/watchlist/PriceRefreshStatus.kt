package com.neatcode.tabgreater.ui.watchlist

import com.neatcode.tabgreater.core.model.MarketKey

enum class PriceRefreshStatus { EMPTY, CURRENT, REFRESHING, STALE, ERROR }

enum class PriceIssueKind { OLD, UNCONFIRMED, ERROR }

data class PriceRefreshIssue(val key: MarketKey, val kind: PriceIssueKind)
