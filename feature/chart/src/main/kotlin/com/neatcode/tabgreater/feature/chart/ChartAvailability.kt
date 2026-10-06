package com.neatcode.tabgreater.feature.chart

import com.neatcode.tabgreater.core.exchange.ExchangeFailureKind
import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.Timeframe

/** Market and interval currently owned by the renderer. */
data class ChartTarget(val market: MarketKey, val timeframe: Timeframe)

/** User-visible state of the chart renderer and its first data request. */
sealed interface ChartAvailability {
    data class Loading(val target: ChartTarget?) : ChartAvailability
    data class Ready(val target: ChartTarget) : ChartAvailability
    data class Unsupported(val target: ChartTarget) : ChartAvailability
    data class Unavailable(
        val target: ChartTarget,
        val message: String,
        val retryable: Boolean,
        val retryAfterMs: Long = 0L,
        val failureKind: ExchangeFailureKind? = null,
    ) : ChartAvailability
}

/** One automatic renderer recovery is allowed before the user must request another attempt. */
internal class RendererRecoveryPolicy {
    private var automaticRetryUsed = false

    @Synchronized
    fun onRendererFailure(): RecoveryDecision = if (automaticRetryUsed) {
        RecoveryDecision.MANUAL_RETRY_REQUIRED
    } else {
        automaticRetryUsed = true
        RecoveryDecision.RETRY_AUTOMATICALLY
    }

    /** A successfully loaded first data window closes the current failure episode. */
    @Synchronized
    fun onHealthyChart() {
        automaticRetryUsed = false
    }

    @Synchronized
    fun onManualRetry() {
        automaticRetryUsed = false
    }
}

internal enum class RecoveryDecision { RETRY_AUTOMATICALLY, MANUAL_RETRY_REQUIRED }
