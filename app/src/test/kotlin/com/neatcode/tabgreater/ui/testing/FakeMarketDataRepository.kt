package com.neatcode.tabgreater.ui.testing

import com.neatcode.tabgreater.core.live.LiveStatus
import com.neatcode.tabgreater.core.live.MarketDataRepository
import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.Ticker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

/** Live prices for view-model tests: whatever is put into [tickers], no sockets. */
class FakeMarketDataRepository : MarketDataRepository {

    val tickers = MutableStateFlow<Map<MarketKey, Ticker>>(emptyMap())

    override val status = MutableStateFlow(LiveStatus.LIVE)

    override val latest: StateFlow<Map<MarketKey, Ticker>> get() = tickers

    /** Keys passed to the last [refresh] call. */
    var refreshed: List<MarketKey> = emptyList()
        private set

    override fun observeTickers(keys: Set<MarketKey>): Flow<Map<MarketKey, Ticker>> =
        tickers.map { all -> all.filterKeys { it in keys } }

    override suspend fun refresh(keys: Collection<MarketKey>) {
        refreshed = keys.toList()
    }
}
