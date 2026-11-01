package com.neatcode.tabgreater.core.live

/**
 * Show the latest price with the existing graph before waiting for candle requests. A slow or
 * failed candle top-up must not hold an already fetched price off the home screen. Only rebuild
 * the graph when the rate-limited top-up ran; cancellation still belongs to the calling session.
 */
internal suspend fun refreshPricesBeforeSparklines(
    refreshPrices: suspend () -> Unit,
    render: suspend (includeSparklines: Boolean) -> Unit,
    refreshSparklines: suspend () -> Boolean,
) {
    refreshPrices()
    render(false)
    if (refreshSparklines()) render(true)
}
