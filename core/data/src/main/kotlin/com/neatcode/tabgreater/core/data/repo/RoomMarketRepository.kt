package com.neatcode.tabgreater.core.data.repo

import com.neatcode.tabgreater.core.data.db.MarketDao
import com.neatcode.tabgreater.core.data.db.MarketEntity
import com.neatcode.tabgreater.core.exchange.SingleFlight
import com.neatcode.tabgreater.core.exchange.ExchangeRegistry
import com.neatcode.tabgreater.core.model.ExchangeId
import com.neatcode.tabgreater.core.model.Market
import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.InstrumentKind
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Room-backed [MarketRepository]. Instrument lists use a source-specific freshness window unless
 * forced; every network failure is turned into a
 * [Result.failure] instead of an exception.
 *
 * @param catalogueValidSince epoch millis before which a stored list counts as stale whatever its
 *   age. The app passes the time it was installed or last updated: rows written by an older build
 *   may carry earlier instrument metadata, so the first refresh after an
 *   update must not be skipped by the freshness gate.
 */
class RoomMarketRepository(
    private val marketDao: MarketDao,
    private val registry: ExchangeRegistry,
    private val now: () -> Long = marketClock,
    private val catalogueValidSince: () -> Long = { 0L },
) : MarketRepository {

    private val refreshes = SingleFlight<ExchangeId, Result<Unit>>()

    override suspend fun refreshMarkets(exchange: ExchangeId, force: Boolean): Result<Unit> =
        refreshes.run(exchange) { refreshMarketList(exchange, force) }

    private suspend fun refreshMarketList(exchange: ExchangeId, force: Boolean): Result<Unit> {
        val adapter = registry.getOrNull(exchange) ?: return Result.success(Unit)
        return try {
            val startedAt = now()
            val lastUpdated = marketDao.lastUpdated(exchange.id) ?: 0L
            if (!force) {
                val fresh = startedAt - lastUpdated < maxAgeMs(exchange)
                // A cutoff in the future (the clock was ahead when the app was installed) could
                // never be met and would refetch every list on every call, so it is ignored.
                val validSince = catalogueValidSince().takeIf { it <= startedAt } ?: 0L
                if (fresh && lastUpdated >= validSince) return Result.success(Unit)
            }
            val snapshot = adapter.listMarketSnapshot()
            val markets = snapshot.markets
            if (markets.isEmpty()) {
                return if (snapshot.isComplete) {
                    Result.failure(IllegalStateException("${exchange.id} returned an empty complete market catalogue"))
                } else {
                    Result.success(Unit)
                }
            }
            val fetchedAt = now()
            // Every old row must sort strictly before this refresh, even when a forced refresh
            // happens in the same millisecond as its previous stamp.
            val refreshedAt = if (lastUpdated >= fetchedAt && lastUpdated < Long.MAX_VALUE) lastUpdated + 1 else fetchedAt
            marketDao.syncCatalogue(
                exchange = exchange.id,
                markets = markets.map { it.toEntity(if (snapshot.isComplete) refreshedAt else lastUpdated) },
                refreshedAt = refreshedAt,
                archiveMissing = snapshot.isComplete,
            )
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

    override suspend fun nextDailyOutcomeMarket(current: Market, nowEpochMs: Long): Market? {
        if (!current.isExpiredDailyPriceBinary(nowEpochMs)) return null
        val seriesId = current.rolloverSeriesId ?: return null
        val side = current.binarySide() ?: return null
        val candidates = marketDao.getUpcomingOutcomeSeries(
            exchange = current.exchange.id,
            seriesId = seriesId,
            side = side,
            quote = current.quote,
            afterEpochMs = nowEpochMs,
        ).mapNotNull(MarketEntity::toModelOrNull)
        return nextConfirmedDailyOutcome(current, candidates, nowEpochMs)
    }

    override suspend fun searchPage(
        query: String,
        limit: Int,
        offset: Int,
        instrumentKind: InstrumentKind?,
        exchange: ExchangeId?,
    ): List<Market> {
        if (limit <= 0 || offset < 0) return emptyList()
        val compact = normaliseSearchQuery(query)
        val parsed = parseSearchQuery(compact)
        val searchText = normaliseSearchText(query)
        val pairDisplayPrefix = normalisePairDisplayPrefix(query).orEmpty()
        val supportedIds = registry.supported.map { it.id }
        val searchExchangeIds = when {
            exchange == null -> supportedIds
            exchange.id in supportedIds -> listOf(exchange.id)
            else -> emptyList()
        }
        if (searchExchangeIds.isEmpty()) return emptyList()
        return marketDao.searchCatalog(
            searchText = searchText,
            pairSearch = pairDisplayPrefix.isNotEmpty(),
            pairDisplayPrefix = pairDisplayPrefix,
            compactPrefix = if (parsed.quote == null) compact else parsed.base,
            compactQuery = compact.replace("/", ""),
            hasSlash = parsed.quote != null,
            exactBase = pairDisplayPrefix.substringBefore('/'),
            quotePrefix = pairDisplayPrefix.substringAfter('/', ""),
            instrumentKind = instrumentKind?.id,
            exchange = exchange?.id,
            exchanges = searchExchangeIds,
            limit = limit,
            offset = offset,
        ).mapNotNull(MarketEntity::toModelOrNull)
    }

    private companion object {
        /** SQLite refuses more bound variables than this in a single statement. */
        const val SQL_VARIABLE_LIMIT = 900
        const val HYPERLIQUID_CATALOGUE_MAX_AGE_MS: Long = 10L * 60 * 1000
    }

    private fun maxAgeMs(exchange: ExchangeId): Long =
        if (exchange == ExchangeId.HYPERLIQUID) HYPERLIQUID_CATALOGUE_MAX_AGE_MS else MarketRepository.MAX_AGE_MS
}

/** Picks only an unambiguous, already catalogued contract at the nearest future deadline. */
internal fun nextConfirmedDailyOutcome(
    current: Market,
    candidates: List<Market>,
    nowEpochMs: Long,
): Market? {
    val seriesId = current.rolloverSeriesId ?: return null
    val currentExpiry = current.expiresAt ?: return null
    if (currentExpiry > nowEpochMs || !current.isDailyOutcome() || current.binarySide() == null) return null

    val eligible = candidates.filter { candidate ->
        candidate.key != current.key &&
            candidate.exchange == current.exchange &&
            candidate.quote == current.quote &&
            candidate.rolloverSeriesId == seriesId &&
            candidate.instrumentKind == InstrumentKind.OUTCOME &&
            candidate.active &&
            candidate.expiresAt?.let { it > nowEpochMs && it > currentExpiry } == true &&
            candidate.binarySide() == current.binarySide() &&
            !candidate.outcomeId.isNullOrBlank()
    }
    val nearestExpiry = eligible.minOfOrNull { it.expiresAt ?: Long.MAX_VALUE } ?: return null
    val nearest = eligible.filter { it.expiresAt == nearestExpiry }.distinctBy { it.outcomeId }
    return nearest.singleOrNull()
}

private val marketClock: () -> Long = System::currentTimeMillis

private fun Market.isExpiredDailyPriceBinary(nowEpochMs: Long): Boolean =
    isDailyOutcome() && expiresAt?.let { it <= nowEpochMs } == true && !outcomeId.isNullOrBlank() && binarySide() != null

private fun Market.isDailyOutcome(): Boolean =
    exchange == ExchangeId.HYPERLIQUID && instrumentKind == InstrumentKind.OUTCOME && !rolloverSeriesId.isNullOrBlank()

private fun Market.binarySide(): String? = when (outcomeSide?.trim()?.lowercase(Locale.ROOT)) {
    "yes" -> "yes"
    "no" -> "no"
    else -> null
}
