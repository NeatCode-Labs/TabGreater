package com.neatcode.tabgreater.core.data.repo

import com.neatcode.tabgreater.core.model.ExchangeId
import com.neatcode.tabgreater.core.model.ImportMode
import com.neatcode.tabgreater.core.model.ImportResult
import com.neatcode.tabgreater.core.model.Market
import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.SortMode
import com.neatcode.tabgreater.core.model.SparkPeriod
import com.neatcode.tabgreater.core.model.TileSize
import com.neatcode.tabgreater.core.model.Watchlist
import com.neatcode.tabgreater.core.model.WatchlistItem
import com.neatcode.tabgreater.core.model.WatchlistSnapshot
import com.neatcode.tabgreater.core.model.backup.WatchlistBackup
import kotlinx.coroutines.flow.Flow

/** Watchlists and their items (Room-backed). All flows are hot from the database. */
interface WatchlistRepository {
    fun observeWatchlists(): Flow<List<Watchlist>>
    fun observeWatchlist(id: Long): Flow<Watchlist?>
    fun observeItems(watchlistId: Long): Flow<List<WatchlistItem>>

    /** Every market key referenced by any watchlist (for cache pruning / live subscriptions). */
    fun observeAllKeys(): Flow<Set<MarketKey>>

    /** Creates the default "Main" watchlist if none exist; returns the id of the first watchlist. */
    suspend fun ensureDefault(): Long

    /** Item count per watchlist id (for the Watchlist Manager's `45/100`); lists without items map to 0. */
    fun observeItemCounts(): Flow<Map<Long, Int>>

    suspend fun createWatchlist(name: String): Long
    suspend fun renameWatchlist(id: Long, name: String)
    suspend fun deleteWatchlist(id: Long)

    /**
     * Duplicates watchlist [id] (settings, items, order, accent colours) as a new last list
     * named [name]. Returns the new id, or `null` when [id] is unknown or the 20-list cap is reached.
     */
    suspend fun copyWatchlist(id: Long, name: String): Long?

    /** Persists the tab order from the Watchlist Manager's drag handle; ids not listed keep their relative order after the listed ones. */
    suspend fun reorderWatchlists(orderedIds: List<Long>)

    /** Captures a watchlist and its items so a delete can be undone with [restoreWatchlist]. `null` when unknown. */
    suspend fun snapshotWatchlist(id: Long): WatchlistSnapshot?

    /**
     * Re-creates a deleted watchlist at its old [Watchlist.position] (later lists shift down),
     * with its items, order and colours. Returns the **new** id, or `null` when the cap is reached.
     */
    suspend fun restoreWatchlist(snapshot: WatchlistSnapshot): Long?

    /** Appends keys not already present; silently ignores duplicates and the 100-item cap overflow. */
    suspend fun addItems(watchlistId: Long, keys: List<MarketKey>)
    suspend fun removeItem(itemId: Long)
    suspend fun removeItems(itemIds: Collection<Long>)
    suspend fun reorderItems(watchlistId: Long, orderedItemIds: List<Long>)

    /** Moves the given items (in their current relative order) in front of every other item of their list. */
    suspend fun moveItemsToTop(itemIds: Collection<Long>)

    /**
     * Moves items to [targetWatchlistId] (appended in their current relative order, accent
     * colours kept). An item whose market the target already holds is only removed from the
     * source — the market survives, so that is a genuine move. An item that does not fit under
     * the 100-item cap is **left in the source list**: it is never deleted. Items that are
     * already in the target list are ignored.
     *
     * @return how many items were removed from their source list (inserted plus deduplicated
     *   away); `itemIds.size` minus this is the number that did not fit.
     */
    suspend fun moveItemsToWatchlist(itemIds: Collection<Long>, targetWatchlistId: Long): Int

    suspend fun setAccentColor(itemId: Long, argb: Long?)
    suspend fun setAccentColor(itemIds: Collection<Long>, argb: Long?)

    suspend fun setPeriod(watchlistId: Long, period: SparkPeriod)
    suspend fun setTileSize(watchlistId: Long, size: TileSize)
    suspend fun setSort(watchlistId: Long, sort: SortMode)

    /** Every watchlist with its items, in display order, as a portable backup. */
    suspend fun exportBackup(exportedAt: Long): WatchlistBackup

    /**
     * Applies [backup] according to [mode]. Invalid market keys (unknown exchange, malformed)
     * are skipped and counted; names are trimmed and clamped to [com.neatcode.tabgreater.core.model.Limits.MAX_WATCHLIST_NAME_LENGTH]; an empty name becomes "Imported".
     * Runs in one transaction where the DAO allows it.
     */
    suspend fun importBackup(backup: WatchlistBackup, mode: ImportMode): ImportResult
}

/** Exchange instrument lists, cached in Room and refreshed at most once per [MAX_AGE_MS] unless forced. */
interface MarketRepository {
    /** Fetches the instrument list from the exchange and upserts it. No-op if fresh and not [force]. */
    suspend fun refreshMarkets(exchange: ExchangeId, force: Boolean = false): Result<Unit>

    /** Refreshes every supported exchange, swallowing per-exchange failures. */
    suspend fun refreshAll(force: Boolean = false)

    suspend fun getMarket(key: MarketKey): Market?
    suspend fun getMarkets(keys: Collection<MarketKey>): Map<MarketKey, Market>

    /**
     * Case-insensitive search over base, quote and "BASE/QUOTE"; e.g. "btc", "eur", "btc/eur", "btceur".
     * Without a `/` every part is prefix-matched ("eth" -> ETH/USDT, ETHFI/USDT, ...); with a `/`
     * the base is exact and only the quote is a prefix ("eth/usd" -> ETH/USD, ETH/USDT, ETH/USDC).
     * Results ordered by exchange, then base, then quote.
     */
    suspend fun search(query: String, limit: Int = 300): List<Market>

    companion object {
        const val MAX_AGE_MS: Long = 24L * 60 * 60 * 1000
    }
}

/**
 * Downsampled sparkline for one market and period.
 * @property points closes in chronological order, already downsampled to <= [MAX_POINTS].
 * @property firstClose close of the first candle in the window (for period % change when not 24 h).
 * @property lastClose close of the most recent candle.
 * @property high highest candle high in the window (Compact/Medium/Large "High" when the period is not 24 h).
 * @property low lowest candle low in the window.
 * @property volume sum of base volume over the window.
 * @property updatedAt epoch millis of the last refresh; `0` when only cached data is available.
 */
data class Sparkline(
    val points: FloatArray,
    val firstClose: Double?,
    val lastClose: Double?,
    val high: Double?,
    val low: Double?,
    val volume: Double?,
    val updatedAt: Long,
) {
    val isEmpty: Boolean get() = points.size < 2

    override fun equals(other: Any?): Boolean =
        other is Sparkline && points.contentEquals(other.points) && firstClose == other.firstClose &&
            lastClose == other.lastClose && high == other.high && low == other.low &&
            volume == other.volume && updatedAt == other.updatedAt

    override fun hashCode(): Int = points.contentHashCode() * 31 + updatedAt.hashCode()

    companion object {
        const val MAX_POINTS = 96
        val EMPTY = Sparkline(FloatArray(0), null, null, null, null, null, 0)
    }
}

/**
 * Candle history for tiles: fetched once per market/period, cached in Room, extended from live
 * klines, refreshed via REST every [REFRESH_INTERVAL_MS].
 */
interface SparklineRepository {
    /**
     * Emits the cached sparkline immediately (or [Sparkline.EMPTY]), then every update.
     * Subscribing triggers a REST fetch when the cache is missing or older than [REFRESH_INTERVAL_MS]
     * and subscribes to the exchange kline stream while collected.
     */
    fun observeSparkline(key: MarketKey, period: SparkPeriod): Flow<Sparkline>

    /** Forces a REST refresh for the given markets (pull-to-refresh, app foreground). */
    suspend fun refresh(keys: Collection<MarketKey>, period: SparkPeriod)

    /**
     * The sparkline already in the candle cache — **no** network request and no stream
     * subscription. The home-screen widget renders from it because a widget refresh may run every
     * few seconds and must never block on REST; an empty cache simply yields [Sparkline.EMPTY].
     */
    suspend fun cached(key: MarketKey, period: SparkPeriod): Sparkline

    companion object {
        const val REFRESH_INTERVAL_MS: Long = 15L * 60 * 1000
    }
}
