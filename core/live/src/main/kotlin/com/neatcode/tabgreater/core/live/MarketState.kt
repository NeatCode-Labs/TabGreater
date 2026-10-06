package com.neatcode.tabgreater.core.live

import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.Ticker
import com.neatcode.tabgreater.core.exchange.ExchangeFailureKind

enum class PriceFreshness { MISSING, CACHED, CURRENT, OLD }

data class MarketState(
    val ticker: Ticker? = null,
    val confirmedAtElapsedMs: Long? = null,
    val confirmedAtEpochMs: Long? = null,
    val freshness: PriceFreshness = if (ticker == null) PriceFreshness.MISSING else PriceFreshness.CACHED,
    val failure: ExchangeFailureKind? = null,
    val retryAfterMs: Long = 0,
) {
    fun aged(now: Long): MarketState = copy(freshness = when {
        ticker == null -> PriceFreshness.MISSING
        confirmedAtElapsedMs == null -> PriceFreshness.CACHED
        now - confirmedAtElapsedMs >= STALE_AFTER_MS -> PriceFreshness.OLD
        else -> PriceFreshness.CURRENT
    })

    companion object { const val STALE_AFTER_MS = 10 * 60_000L }
}

data class RefreshResult(
    val successful: Set<MarketKey> = emptySet(),
    val missing: Set<MarketKey> = emptySet(),
    val failures: Map<MarketKey, Throwable> = emptyMap(),
) {
    val complete: Boolean get() = failures.isEmpty() && missing.isEmpty()
}
