package com.neatcode.tabgreater.core.live

import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.Ticker
import com.neatcode.tabgreater.core.model.ExchangeId
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
        now - confirmedAtElapsedMs >= STALE_AFTER_MS - tradeAgeAtReceipt() -> PriceFreshness.OLD
        else -> PriceFreshness.CURRENT
    })

    // Hyperliquid returns actual executions. Fetching yesterday's last trade again does not
    // make its price fresh. Other adapters can supply the time of the snapshot or a quote.
    private fun tradeAgeAtReceipt(): Long {
        if (ticker == null || ticker.key.exchange != ExchangeId.HYPERLIQUID || ticker.timestamp <= 0L) return 0L
        val receivedAt = confirmedAtEpochMs ?: return 0L
        return (receivedAt - ticker.timestamp).coerceIn(0L, STALE_AFTER_MS)
    }

    companion object { const val STALE_AFTER_MS = 10 * 60_000L }
}

data class RefreshResult(
    val successful: Set<MarketKey> = emptySet(),
    val missing: Set<MarketKey> = emptySet(),
    val failures: Map<MarketKey, Throwable> = emptyMap(),
) {
    val complete: Boolean get() = failures.isEmpty() && missing.isEmpty()
}
