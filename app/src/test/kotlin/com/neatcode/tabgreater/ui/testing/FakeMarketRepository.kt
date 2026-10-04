package com.neatcode.tabgreater.ui.testing

import com.neatcode.tabgreater.core.data.repo.MarketRepository
import com.neatcode.tabgreater.core.model.AssetClass
import com.neatcode.tabgreater.core.model.ExchangeId
import com.neatcode.tabgreater.core.model.Market
import com.neatcode.tabgreater.core.model.MarketKey

/** Instrument metadata for view-model tests: everything in [markets], no network. */
class FakeMarketRepository(
    private val markets: MutableMap<MarketKey, Market> = mutableMapOf(),
) : MarketRepository {

    /** Registers a market with the given price precision; a non-null [underlying] makes it a stock token. */
    fun put(key: MarketKey, pricePrecision: Int = 2, underlying: String? = null) {
        markets[key] = Market(
            key = key,
            nativeSymbol = key.pair,
            pricePrecision = pricePrecision,
            assetClass = if (underlying != null) AssetClass.STOCK else AssetClass.CRYPTO,
            underlying = underlying,
        )
    }

    override suspend fun refreshMarkets(exchange: ExchangeId, force: Boolean): Result<Unit> = Result.success(Unit)

    /** Runs inside [refreshAll]; a test can suspend it to hold the catalogue refresh open. */
    var onRefreshAll: suspend () -> Unit = {}

    override suspend fun refreshAll(force: Boolean) = onRefreshAll()

    override suspend fun getMarket(key: MarketKey): Market? = markets[key]

    override suspend fun getMarkets(keys: Collection<MarketKey>): Map<MarketKey, Market> =
        keys.mapNotNull { key -> markets[key]?.let { key to it } }.toMap()

    /** Runs before every [search]; a test can suspend it to hold a search open. */
    var onSearch: suspend () -> Unit = {}

    override suspend fun search(query: String, limit: Int, assetClass: AssetClass?): List<Market> {
        onSearch()
        return markets.values
            .filter { it.key.value.contains(query, ignoreCase = true) }
            .filter { assetClass == null || it.assetClass == assetClass }
            .take(limit)
    }

    /** Same ranking as the Room repository: exchanges listing the underlying, then markets, then name. */
    override suspend fun popularStockRoots(limit: Int): List<String> =
        markets.values
            .filter { it.assetClass == AssetClass.STOCK && it.active }
            .mapNotNull { market -> market.underlying?.let { it to market.exchange } }
            .groupBy({ it.first }, { it.second })
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<String, List<ExchangeId>>> { it.value.distinct().size }
                    .thenByDescending { it.value.size }
                    .thenBy { it.key },
            )
            .take(limit)
            .map { it.key }
}
