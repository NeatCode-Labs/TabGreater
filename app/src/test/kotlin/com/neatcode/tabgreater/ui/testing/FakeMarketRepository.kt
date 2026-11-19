package com.neatcode.tabgreater.ui.testing

import com.neatcode.tabgreater.core.data.repo.MarketRepository
import com.neatcode.tabgreater.core.model.AssetClass
import com.neatcode.tabgreater.core.model.ExchangeId
import com.neatcode.tabgreater.core.model.InstrumentKind
import com.neatcode.tabgreater.core.model.Market
import com.neatcode.tabgreater.core.model.MarketKey

data class SearchPageCall(
    val query: String,
    val limit: Int,
    val offset: Int,
    val instrumentKind: InstrumentKind?,
    val exchange: ExchangeId?,
)

/** Instrument metadata for view-model tests: everything in [markets], no network. */
class FakeMarketRepository(
    private val markets: MutableMap<MarketKey, Market> = mutableMapOf(),
) : MarketRepository {

    val refreshRequests = mutableListOf<Pair<ExchangeId, Boolean>>()
    val getMarketsRequests = mutableListOf<Set<MarketKey>>()
    val nextOutcomeRequests = mutableListOf<Pair<MarketKey, Long>>()
    val searchPageRequests = mutableListOf<SearchPageCall>()
    var onRefreshMarket: suspend (ExchangeId, Boolean) -> Unit = { _, _ -> }
    var onNextDailyOutcomeMarket: suspend (Market, Long) -> Market? = { current, now ->
        val side = current.outcomeSide?.trim()?.lowercase()
        val candidates = markets.values.filter { candidate ->
            candidate.key != current.key && candidate.exchange == current.exchange && candidate.quote == current.quote &&
                candidate.rolloverSeriesId == current.rolloverSeriesId && candidate.instrumentKind == InstrumentKind.OUTCOME &&
                candidate.active && candidate.outcomeSide?.trim()?.lowercase() == side &&
                candidate.expiresAt?.let { it > now && it > (current.expiresAt ?: Long.MAX_VALUE) } == true &&
                !candidate.outcomeId.isNullOrBlank()
        }
        val expiry = candidates.minOfOrNull { it.expiresAt ?: Long.MAX_VALUE }
        candidates.filter { it.expiresAt == expiry }.distinctBy { it.outcomeId }.singleOrNull()
    }

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

    fun put(market: Market) {
        markets[market.key] = market
    }

    override suspend fun refreshMarkets(exchange: ExchangeId, force: Boolean): Result<Unit> {
        refreshRequests += exchange to force
        onRefreshMarket(exchange, force)
        return Result.success(Unit)
    }

    /** Runs inside [refreshAll]; a test can suspend it to hold the catalogue refresh open. */
    var onRefreshAll: suspend () -> Unit = {}

    override suspend fun refreshAll(force: Boolean) = onRefreshAll()

    override suspend fun getMarket(key: MarketKey): Market? = markets[key]

    override suspend fun getMarkets(keys: Collection<MarketKey>): Map<MarketKey, Market> {
        getMarketsRequests += keys.toSet()
        return keys.mapNotNull { key -> markets[key]?.let { key to it } }.toMap()
    }

    override suspend fun nextDailyOutcomeMarket(current: Market, nowEpochMs: Long): Market? {
        nextOutcomeRequests += current.key to nowEpochMs
        return onNextDailyOutcomeMarket(current, nowEpochMs)
    }

    /** Runs before every page; a test can suspend it to hold that request open. */
    var onSearchPage: suspend (SearchPageCall) -> Unit = {}

    override suspend fun searchPage(
        query: String,
        limit: Int,
        offset: Int,
        instrumentKind: InstrumentKind?,
        exchange: ExchangeId?,
    ): List<Market> {
        val call = SearchPageCall(query, limit, offset, instrumentKind, exchange)
        searchPageRequests += call
        onSearchPage(call)
        if (limit <= 0 || offset < 0) return emptyList()
        val matching = markets.values
            .filter { it.active }
            .filter {
                query.isBlank() || listOfNotNull(
                    it.key.value,
                    it.displayName,
                    it.description,
                    it.nativeSymbol,
                    it.venue,
                    it.unit,
                    it.underlying,
                    it.outcomeSide,
                    it.eventId,
                    it.outcomeId,
                ).any { value -> value.contains(query, ignoreCase = true) }
            }
            .filter { instrumentKind == null || it.instrumentKind == instrumentKind }
            .filter { exchange == null || it.exchange == exchange }
            .sortedBy { it.key.value }
        return matching.drop(offset).take(limit)
    }
}
