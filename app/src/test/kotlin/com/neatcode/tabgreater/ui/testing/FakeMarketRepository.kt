package com.neatcode.tabgreater.ui.testing

import com.neatcode.tabgreater.core.data.repo.MarketRepository
import com.neatcode.tabgreater.core.model.ExchangeId
import com.neatcode.tabgreater.core.model.Market
import com.neatcode.tabgreater.core.model.MarketKey

/** Instrument metadata for view-model tests: everything in [markets], no network. */
class FakeMarketRepository(
    private val markets: MutableMap<MarketKey, Market> = mutableMapOf(),
) : MarketRepository {

    /** Registers a market with the given price precision. */
    fun put(key: MarketKey, pricePrecision: Int = 2) {
        markets[key] = Market(key = key, nativeSymbol = key.pair, pricePrecision = pricePrecision)
    }

    override suspend fun refreshMarkets(exchange: ExchangeId, force: Boolean): Result<Unit> = Result.success(Unit)

    override suspend fun refreshAll(force: Boolean) = Unit

    override suspend fun getMarket(key: MarketKey): Market? = markets[key]

    override suspend fun getMarkets(keys: Collection<MarketKey>): Map<MarketKey, Market> =
        keys.mapNotNull { key -> markets[key]?.let { key to it } }.toMap()

    override suspend fun search(query: String, limit: Int): List<Market> =
        markets.values.filter { it.key.value.contains(query, ignoreCase = true) }.take(limit)
}
