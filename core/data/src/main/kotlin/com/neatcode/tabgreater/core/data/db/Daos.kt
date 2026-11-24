package com.neatcode.tabgreater.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchlistDao {
    @Query("SELECT * FROM watchlists ORDER BY position ASC")
    fun observeAll(): Flow<List<WatchlistEntity>>

    @Query("SELECT * FROM watchlists ORDER BY position ASC")
    suspend fun getAll(): List<WatchlistEntity>

    @Query("SELECT * FROM watchlists WHERE id = :id")
    suspend fun getById(id: Long): WatchlistEntity?

    @Query("SELECT COUNT(*) FROM watchlists")
    suspend fun count(): Int

    @Insert
    suspend fun insert(watchlist: WatchlistEntity): Long

    @Update
    suspend fun update(watchlist: WatchlistEntity)

    @Query("DELETE FROM watchlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE watchlists SET period = :period WHERE id = :id")
    suspend fun setPeriod(id: Long, period: String)

    @Query("UPDATE watchlists SET tile_size = :tileSize WHERE id = :id")
    suspend fun setTileSize(id: Long, tileSize: String)

    @Query("UPDATE watchlists SET sort = :sort WHERE id = :id")
    suspend fun setSort(id: Long, sort: String)

    /** Removes every watchlist; the foreign key cascade takes the items with it (import REPLACE). */
    @Query("DELETE FROM watchlists")
    suspend fun deleteAll()

    @Query("UPDATE watchlists SET position = :position WHERE id = :id")
    suspend fun setPosition(id: Long, position: Int)

    /** Makes room for a restored watchlist at [from] by pushing every later list one slot down. */
    @Query("UPDATE watchlists SET position = position + 1 WHERE position >= :from")
    suspend fun shiftPositionsFrom(from: Int)

    /** Rewrites the positions to `0..n-1` in the current order, closing gaps left by a delete. */
    @Transaction
    suspend fun normalisePositions() {
        getAll().forEachIndexed { index, row -> if (row.position != index) setPosition(row.id, index) }
    }

    /**
     * Persists a tab order: the listed ids first (unknown ones ignored), then every other
     * watchlist in its current relative order.
     */
    @Transaction
    suspend fun reorder(orderedIds: List<Long>) {
        val current = getAll()
        val known = current.mapTo(HashSet()) { it.id }
        val order = LinkedHashSet<Long>()
        orderedIds.filterTo(order) { it in known }
        current.mapTo(order) { it.id }
        order.forEachIndexed { index, id -> setPosition(id, index) }
    }
}

/** One row of [WatchlistItemDao.observeItemCounts]: how many tickers a watchlist holds. */
data class WatchlistItemCount(val watchlistId: Long, val count: Int)

@Dao
interface WatchlistItemDao {
    @Query("SELECT * FROM watchlist_items WHERE watchlist_id = :watchlistId ORDER BY position ASC")
    fun observeByWatchlist(watchlistId: Long): Flow<List<WatchlistItemEntity>>

    @Query("SELECT * FROM watchlist_items WHERE watchlist_id = :watchlistId ORDER BY position ASC")
    suspend fun getByWatchlist(watchlistId: Long): List<WatchlistItemEntity>

    @Query("SELECT * FROM watchlist_items ORDER BY watchlist_id ASC, position ASC")
    suspend fun getAllItems(): List<WatchlistItemEntity>

    @Query("SELECT * FROM watchlist_items WHERE id IN (:ids) ORDER BY watchlist_id ASC, position ASC")
    suspend fun getByIds(ids: List<Long>): List<WatchlistItemEntity>

    @Query("SELECT DISTINCT market_key FROM watchlist_items")
    fun observeAllMarketKeys(): Flow<List<String>>

    /**
     * Item count per watchlist, `0` for empty lists (the left join keeps them in the result).
     * Emits again whenever either table changes.
     */
    @Query(
        """
        SELECT w.id AS watchlistId, COUNT(i.id) AS count FROM watchlists w
        LEFT JOIN watchlist_items i ON i.watchlist_id = w.id
        GROUP BY w.id
        """,
    )
    fun observeItemCounts(): Flow<List<WatchlistItemCount>>

    @Query("SELECT COUNT(*) FROM watchlist_items WHERE watchlist_id = :watchlistId")
    suspend fun count(watchlistId: Long): Int

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM watchlist_items WHERE watchlist_id = :watchlistId")
    suspend fun nextPosition(watchlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: WatchlistItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<WatchlistItemEntity>)

    @Update
    suspend fun update(item: WatchlistItemEntity)

    @Query("UPDATE watchlist_items SET market_key = :marketKey WHERE id = :id AND market_key = :expectedMarketKey")
    suspend fun replaceMarketKey(id: Long, expectedMarketKey: String, marketKey: String): Int

    @Update
    suspend fun updateAll(items: List<WatchlistItemEntity>)

    @Query("DELETE FROM watchlist_items WHERE id = :id")
    suspend fun delete(id: Long)

    /** Caller chunks [ids] to stay under SQLite's 999 bound variables. */
    @Query("DELETE FROM watchlist_items WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("UPDATE watchlist_items SET accent_color = :color WHERE id = :id")
    suspend fun setAccentColor(id: Long, color: Long?)

    /** Caller chunks [ids] to stay under SQLite's 999 bound variables. */
    @Query("UPDATE watchlist_items SET accent_color = :color WHERE id IN (:ids)")
    suspend fun setAccentColors(ids: List<Long>, color: Long?)

    @Transaction
    suspend fun reorder(watchlistId: Long, orderedIds: List<Long>) {
        val items = getByWatchlist(watchlistId).associateBy { it.id }
        val updated = orderedIds.mapIndexedNotNull { index, id -> items[id]?.copy(position = index) }
        updateAll(updated)
    }
}

@Dao
interface MarketDao {
    @Query("SELECT * FROM markets WHERE exchange = :exchange AND active = 1 ORDER BY base, quote")
    suspend fun getByExchange(exchange: String): List<MarketEntity>

    @Query("SELECT * FROM markets WHERE market_key IN (:keys)")
    suspend fun getByKeys(keys: List<String>): List<MarketEntity>

    @Query("SELECT * FROM markets WHERE market_key = :key")
    suspend fun getByKey(key: String): MarketEntity?

    @Query(
        "SELECT * FROM markets WHERE exchange = :exchange AND rollover_series_id = :seriesId " +
            "AND LOWER(outcome_side) = :side AND quote = :quote AND active = 1 " +
            "AND expires_at > :afterEpochMs ORDER BY expires_at ASC",
    )
    suspend fun getUpcomingOutcomeSeries(
        exchange: String,
        seriesId: String,
        side: String,
        quote: String,
        afterEpochMs: Long,
    ): List<MarketEntity>

    @Query("SELECT COUNT(*) FROM markets WHERE exchange = :exchange")
    suspend fun count(exchange: String): Int

    @Query("SELECT MAX(updated_at) FROM markets WHERE exchange = :exchange")
    suspend fun lastUpdated(exchange: String): Long?

    /** Search symbols and market/event metadata while applying filters before pagination. */
    @Query(
        """
        SELECT * FROM markets
        WHERE active = 1
          AND exchange IN (:exchanges)
          AND (:instrumentKind IS NULL OR instrument_kind = :instrumentKind)
          AND (:exchange IS NULL OR exchange = :exchange)
          AND (
            :searchText = ''
            OR (
              :pairSearch = 1 AND (
                instr(upper(display_name), :pairDisplayPrefix) = 1
                OR (
                  (:exactBase = '' OR base = :exactBase OR underlying = :exactBase OR upper(native_symbol) = :exactBase)
                  AND instr(quote, :quotePrefix) = 1
                )
              )
            )
            OR (
              :hasSlash = 0 AND :compactPrefix != ''
              AND (base LIKE :compactPrefix || '%' OR quote LIKE :compactPrefix || '%'
                OR underlying LIKE :compactPrefix || '%' OR (base || quote) LIKE :compactQuery || '%')
            )
            OR (
              :pairSearch = 0 AND (
                instr(upper(display_name), upper(:searchText)) > 0
                OR instr(upper(description), upper(:searchText)) > 0
                OR instr(upper(native_symbol), upper(:searchText)) > 0
                OR instr(upper(venue), upper(:searchText)) > 0
                OR instr(upper(unit), upper(:searchText)) > 0
                OR instr(upper(underlying), upper(:searchText)) > 0
                OR instr(upper(outcome_side), upper(:searchText)) > 0
                OR instr(upper(event_id), upper(:searchText)) > 0
                OR instr(upper(outcome_id), upper(:searchText)) > 0
              )
            )
          )
        ORDER BY CASE
                   WHEN underlying = :compactPrefix THEN 0
                   WHEN base = :compactPrefix THEN 1
                   ELSE 2
                 END,
                 exchange, base, quote, market_key
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun searchCatalog(
        searchText: String,
        pairSearch: Boolean,
        pairDisplayPrefix: String,
        compactPrefix: String,
        compactQuery: String,
        hasSlash: Boolean,
        exactBase: String,
        quotePrefix: String,
        instrumentKind: String?,
        exchange: String?,
        exchanges: List<String>,
        limit: Int,
        offset: Int,
    ): List<MarketEntity>

    @Upsert
    suspend fun upsertAll(markets: List<MarketEntity>)

    /**
     * Retain labels for watchlists. Only a complete catalogue can withdraw missing markets.
     */
    @Transaction
    suspend fun syncCatalogue(exchange: String, markets: List<MarketEntity>, refreshedAt: Long, archiveMissing: Boolean) {
        upsertAll(markets)
        if (archiveMissing) deactivateStale(exchange, refreshedAt)
    }

    @Query("UPDATE markets SET active = 0 WHERE exchange = :exchange AND updated_at < :refreshedAt")
    suspend fun deactivateStale(exchange: String, refreshedAt: Long)

    @Query("DELETE FROM markets WHERE exchange = :exchange AND market_key NOT IN (:keepKeys)")
    suspend fun deleteMissing(exchange: String, keepKeys: List<String>)

    /**
     * Variable-count-safe equivalent of [deleteMissing]: a refresh stamps every surviving row with
     * the same `updated_at`, so anything older was not in the exchange's new instrument list.
     * Binance alone returns ~3000 markets, well past SQLite's bound-variable limit.
     */
    @Query("DELETE FROM markets WHERE exchange = :exchange AND updated_at < :refreshedAt")
    suspend fun deleteStale(exchange: String, refreshedAt: Long)
}

@Dao
interface CandleDao {
    @Query(
        """
        SELECT * FROM candles WHERE market_key = :key AND timeframe = :timeframe
        ORDER BY open_time DESC LIMIT :limit
        """,
    )
    suspend fun latest(key: String, timeframe: String, limit: Int): List<CandleEntity>

    @Query(
        """
        SELECT * FROM candles WHERE market_key = :key AND timeframe = :timeframe
        ORDER BY open_time DESC LIMIT :limit
        """,
    )
    fun observeLatest(key: String, timeframe: String, limit: Int): Flow<List<CandleEntity>>

    @Query(
        """
        SELECT * FROM candles WHERE market_key = :key AND timeframe = :timeframe
        AND open_time >= :from AND open_time < :to ORDER BY open_time ASC
        """,
    )
    suspend fun range(key: String, timeframe: String, from: Long, to: Long): List<CandleEntity>

    @Upsert
    suspend fun upsertAll(candles: List<CandleEntity>)

    @Query("DELETE FROM candles WHERE market_key = :key AND timeframe = :timeframe AND open_time < :before")
    suspend fun prune(key: String, timeframe: String, before: Long)

    /** Every market that has cached candles, so stale ones can be diffed in Kotlin. */
    @Query("SELECT DISTINCT market_key FROM candles")
    suspend fun distinctKeys(): List<String>

    /** Caller chunks [keys] to stay under SQLite's 999 bound variables. */
    @Query("DELETE FROM candles WHERE market_key IN (:keys)")
    suspend fun deleteByKeys(keys: List<String>)
}

@Dao
interface TickerSnapshotDao {
    @Query("SELECT * FROM ticker_snapshots WHERE market_key IN (:keys)")
    fun observeByKeys(keys: List<String>): Flow<List<TickerSnapshotEntity>>

    @Query("SELECT * FROM ticker_snapshots WHERE market_key = :key")
    suspend fun get(key: String): TickerSnapshotEntity?

    @Query("SELECT * FROM ticker_snapshots WHERE market_key IN (:keys)")
    suspend fun getByKeys(keys: List<String>): List<TickerSnapshotEntity>

    @Upsert
    suspend fun upsert(snapshot: TickerSnapshotEntity)

    @Upsert
    suspend fun upsertAll(snapshots: List<TickerSnapshotEntity>)

    /** Every market that has a cached snapshot, so stale ones can be diffed in Kotlin. */
    @Query("SELECT DISTINCT market_key FROM ticker_snapshots")
    suspend fun distinctKeys(): List<String>

    /** Caller chunks [keys] to stay under SQLite's 999 bound variables. */
    @Query("DELETE FROM ticker_snapshots WHERE market_key IN (:keys)")
    suspend fun deleteByKeys(keys: List<String>)
}

@Dao
interface ChartDrawingDao {
    @Query("SELECT * FROM chart_drawings WHERE market_key = :key")
    suspend fun get(key: String): ChartDrawingEntity?

    @Upsert
    suspend fun upsert(entity: ChartDrawingEntity)

    @Query("DELETE FROM chart_drawings WHERE market_key = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM chart_drawings")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM chart_drawings")
    suspend fun count(): Int
}
