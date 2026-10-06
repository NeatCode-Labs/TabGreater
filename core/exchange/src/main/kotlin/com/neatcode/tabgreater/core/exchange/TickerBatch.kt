package com.neatcode.tabgreater.core.exchange

import com.neatcode.tabgreater.core.model.*
import kotlinx.coroutines.CancellationException

/** Every requested market has a value, an explicit failure, or a no-data result. */
data class TickerBatch(
    val values: Map<MarketKey, Ticker> = emptyMap(),
    val failures: Map<MarketKey, Throwable> = emptyMap(),
    val missing: Set<MarketKey> = emptySet(),
)

suspend fun tickerBatch(markets: List<Market>, fetch: suspend () -> List<Ticker>): TickerBatch = try {
    val wanted = markets.mapTo(linkedSetOf()) { it.key }
    val values = fetch().filter { it.key in wanted && it.last.isFinite() && it.last > 0 }.associateBy { it.key }
    TickerBatch(values, missing = wanted - values.keys)
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    TickerBatch(failures = markets.associate { it.key to e })
}

/** Preserve successful chunks; a blocked request defers remaining chunks without extra traffic. */
suspend fun tickerBatches(groups: List<List<Market>>, fetch: suspend (List<Market>) -> List<Ticker>): TickerBatch {
    val values = linkedMapOf<MarketKey, Ticker>()
    val failures = linkedMapOf<MarketKey, Throwable>()
    val missing = linkedSetOf<MarketKey>()
    var blocked: Throwable? = null
    for (group in groups) {
        val stopped = blocked
        val result = if (stopped != null) TickerBatch(failures = group.associate { it.key to stopped })
            else tickerBatch(group) { fetch(group) }
        values.putAll(result.values)
        failures.putAll(result.failures)
        missing.addAll(result.missing)
        blocked = result.failures.values.firstOrNull { it.exchangeFailureKind() in setOf(
            ExchangeFailureKind.RATE_LIMITED, ExchangeFailureKind.DEFERRED,
            ExchangeFailureKind.FORBIDDEN, ExchangeFailureKind.REGION_RESTRICTED,
        ) }
    }
    return TickerBatch(values, failures, missing)
}
