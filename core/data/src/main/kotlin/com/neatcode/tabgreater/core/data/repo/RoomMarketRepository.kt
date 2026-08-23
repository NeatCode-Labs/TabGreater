package com.neatcode.tabgreater.core.data.repo

import com.neatcode.tabgreater.core.data.db.MarketDao
import com.neatcode.tabgreater.core.data.db.MarketEntity
import com.neatcode.tabgreater.core.exchange.ExchangeRegistry
import com.neatcode.tabgreater.core.model.ExchangeId
import com.neatcode.tabgreater.core.model.Market
import com.neatcode.tabgreater.core.model.MarketKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Room-backed [MarketRepository]. Instrument lists are refreshed at most once per
 * [MarketRepository.MAX_AGE_MS] unless forced; every network failure is turned into a
 * [Result.failure] instead of an exception.
 */
class RoomMarketRepository(
    private val marketDao: MarketDao,
    private val registry: ExchangeRegistry,
    private val now: () -> Long = System::currentTimeMillis,
) : MarketRepository {

    override suspend fun refreshMarkets(exchange: ExchangeId, force: Boolean): Result<Unit> {
        val adapter = registry.getOrNull(exchange) ?: return Result.success(Unit)
        return try {
            val startedAt = now()
            if (!force) {
                val lastUpdated = marketDao.lastUpdated(exchange.id) ?: 0L
                if (startedAt - lastUpdated < MarketRepository.MAX_AGE_MS) return Result.success(Unit)
            }
            val markets = adapter.listMarkets()
            if (markets.isEmpty()) return Result.success(Unit)
            val refreshedAt = now()
            marketDao.upsertAll(markets.map { it.toEntity(refreshedAt) })
            // Everything the exchange no longer lists still carries an older stamp.
            marketDao.deleteStale(exchange.id, refreshedAt)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Six instrument lists add up to several megabytes; fetching them concurrently keeps the
    // "+ Ticker" screen's first open to the slowest exchange instead of the sum of all of them.
    override suspend fun refreshAll(force: Boolean) {
        coroutineScope {
            registry.supported.map { exchange -> async { refreshMarkets(exchange, force) } }.awaitAll()
        }
    }

    override suspend fun getMarket(key: MarketKey): Market? {
        if (key.exchange !in registry.supported) return null
        return marketDao.getByKey(key.value)?.toModelOrNull()
    }

    override suspend fun getMarkets(keys: Collection<MarketKey>): Map<MarketKey, Market> {
        if (keys.isEmpty()) return emptyMap()
        val supported = keys.filter { it.exchange in registry.supported }
        if (supported.isEmpty()) return emptyMap()
        val out = LinkedHashMap<MarketKey, Market>(supported.size)
        for (chunk in supported.map { it.value }.distinct().chunked(SQL_VARIABLE_LIMIT)) {
            for (row in marketDao.getByKeys(chunk)) {
                val market = row.toModelOrNull() ?: continue
                out[market.key] = market
            }
        }
        return out
    }

    override suspend fun search(query: String, limit: Int): List<Market> {
        if (limit <= 0) return emptyList()
        val normalised = normaliseSearchQuery(query)
        val parsed = parseSearchQuery(normalised)
        if (parsed.isBlank) return emptyList()

        val rows = LinkedHashMap<String, MarketEntity>()
        val quote = parsed.quote
        if (quote != null) {
            for (row in marketDao.searchPair(parsed.base, quote, limit * CANDIDATE_FACTOR)) {
                rows[row.marketKey] = row
            }
        } else {
            val prefix = parsed.base
            for (row in marketDao.search(prefix, "$prefix%", limit * CANDIDATE_FACTOR)) {
                rows[row.marketKey] = row
            }
            // "BTCEUR" -> BTC/EUR: SQLite gives us the rows whose base is a prefix of the query,
            // the concatenated match itself is cheap to check in memory.
            for (row in marketDao.searchConcatCandidates(prefix, limit * CANDIDATE_FACTOR)) {
                if (matchesConcatenated(row.base, row.quote, prefix)) rows[row.marketKey] = row
            }
        }

        val supportedIds = registry.supported.mapTo(HashSet()) { it.id }
        return rows.values.asSequence()
            .filter { it.exchange in supportedIds }
            .sortedWith(compareBy({ it.exchange }, { it.base }, { it.quote }))
            .mapNotNull { it.toModelOrNull() }
            .take(limit)
            .toList()
    }

    private companion object {
        /** SQLite refuses more bound variables than this in a single statement. */
        const val SQL_VARIABLE_LIMIT = 900

        /** Over-fetch factor so the in-memory filter/sort still has [limit] rows to work with. */
        const val CANDIDATE_FACTOR = 4
    }
}
