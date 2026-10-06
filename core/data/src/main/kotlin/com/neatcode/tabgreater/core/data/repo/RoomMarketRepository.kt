package com.neatcode.tabgreater.core.data.repo

import com.neatcode.tabgreater.core.data.db.MarketDao
import com.neatcode.tabgreater.core.data.db.MarketEntity
import com.neatcode.tabgreater.core.exchange.SingleFlight
import com.neatcode.tabgreater.core.exchange.ExchangeRegistry
import com.neatcode.tabgreater.core.model.AssetClass
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
 *
 * @param catalogueValidSince epoch millis before which a stored list counts as stale whatever its
 *   age. The app passes the time it was installed or last updated: rows written by an older build
 *   were classified by that build's rules (asset class, underlying), so the first refresh after an
 *   update must not be skipped by the freshness gate.
 */
class RoomMarketRepository(
    private val marketDao: MarketDao,
    private val registry: ExchangeRegistry,
    private val now: () -> Long = System::currentTimeMillis,
    private val catalogueValidSince: () -> Long = { 0L },
) : MarketRepository {

    private val refreshes = SingleFlight<ExchangeId, Result<Unit>>()

    override suspend fun refreshMarkets(exchange: ExchangeId, force: Boolean): Result<Unit> =
        refreshes.run(exchange) { refreshMarketList(exchange, force) }

    private suspend fun refreshMarketList(exchange: ExchangeId, force: Boolean): Result<Unit> {
        val adapter = registry.getOrNull(exchange) ?: return Result.success(Unit)
        return try {
            val startedAt = now()
            if (!force) {
                val lastUpdated = marketDao.lastUpdated(exchange.id) ?: 0L
                val fresh = startedAt - lastUpdated < MarketRepository.MAX_AGE_MS
                // A cutoff in the future (the clock was ahead when the app was installed) could
                // never be met and would refetch every list on every call, so it is ignored.
                val validSince = catalogueValidSince().takeIf { it <= startedAt } ?: 0L
                if (fresh && lastUpdated >= validSince) return Result.success(Unit)
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

    override suspend fun search(query: String, limit: Int, assetClass: AssetClass?): List<Market> {
        if (limit <= 0) return emptyList()
        val normalised = normaliseSearchQuery(query)
        val parsed = parseSearchQuery(normalised)
        if (parsed.isBlank) return emptyList()

        // The class filter runs in SQL, before the candidate limit.
        val classId = assetClass?.id
        val rows = LinkedHashMap<String, MarketEntity>()
        val quote = parsed.quote
        if (quote != null) {
            for (row in marketDao.searchPair(parsed.base, quote, classId, limit * CANDIDATE_FACTOR)) {
                rows[row.marketKey] = row
            }
        } else {
            val prefix = parsed.base
            // The tokens of the typed ticker itself are fetched on their own: a short ticker that
            // is also a quote prefix ("U") matches more rows than the candidate limit lets through.
            if (assetClass == AssetClass.STOCK) {
                for (row in marketDao.searchPair(prefix, "", classId, limit * CANDIDATE_FACTOR)) {
                    rows[row.marketKey] = row
                }
            }
            for (row in marketDao.search(prefix, "$prefix%", classId, limit * CANDIDATE_FACTOR)) {
                rows[row.marketKey] = row
            }
            // "BTCEUR" -> BTC/EUR: SQLite gives us the rows whose base is a prefix of the query,
            // the concatenated match itself is cheap to check in memory.
            for (row in marketDao.searchConcatCandidates(prefix, classId, limit * CANDIDATE_FACTOR)) {
                if (matchesConcatenated(row.base, row.quote, prefix)) rows[row.marketKey] = row
            }
        }

        // "t" in Stocks: the tokens of share T on every exchange come before TSLAX, TSMX, ...
        // Within either group the leveraged tokens (TSLA3L) follow the plain ones.
        val order = if (assetClass == AssetClass.STOCK && parsed.base.isNotEmpty()) {
            compareBy<MarketEntity>({ it.underlying != parsed.base }, { it.isLeveragedToken() }).then(CATALOGUE_ORDER)
        } else {
            CATALOGUE_ORDER
        }
        val supportedIds = registry.supported.mapTo(HashSet()) { it.id }
        return rows.values.asSequence()
            .filter { it.exchange in supportedIds }
            .sortedWith(order)
            .mapNotNull { it.toModelOrNull() }
            .take(limit)
            .toList()
    }

    override suspend fun popularStockRoots(limit: Int): List<String> {
        if (limit <= 0) return emptyList()
        val supportedIds = registry.supported.map { it.id }
        if (supportedIds.isEmpty()) return emptyList()
        return marketDao.popularUnderlyings(AssetClass.STOCK.id, supportedIds, limit)
    }

    private companion object {
        /** SQLite refuses more bound variables than this in a single statement. */
        const val SQL_VARIABLE_LIMIT = 900

        /** Over-fetch factor so the in-memory filter/sort still has [limit] rows to work with. */
        const val CANDIDATE_FACTOR = 4

        /** The order of every search result: exchange, then base, then quote. */
        val CATALOGUE_ORDER: Comparator<MarketEntity> = compareBy({ it.exchange }, { it.base }, { it.quote })

        /** What follows the underlying in a leveraged token's base: `3L`, `3S`, `5L`, `5S`. */
        val LEVERAGED_SUFFIX = Regex("[35][LS]")

        /** A 3x/5x token on its own underlying (`TSLA3L` on `TSLA`), as only Gate lists them. */
        fun MarketEntity.isLeveragedToken(): Boolean {
            val root = underlying ?: return false
            return base.startsWith(root) && LEVERAGED_SUFFIX.matches(base.substring(root.length))
        }
    }
}
