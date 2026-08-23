package com.neatcode.tabgreater.core.data.repo

import com.neatcode.tabgreater.core.data.db.CandleDao
import com.neatcode.tabgreater.core.data.db.CandleEntity
import com.neatcode.tabgreater.core.data.db.MarketDao
import com.neatcode.tabgreater.core.data.db.MarketEntity
import com.neatcode.tabgreater.core.data.db.TickerSnapshotDao
import com.neatcode.tabgreater.core.data.db.TickerSnapshotEntity
import com.neatcode.tabgreater.core.data.db.WatchlistDao
import com.neatcode.tabgreater.core.data.db.WatchlistEntity
import com.neatcode.tabgreater.core.data.db.WatchlistItemCount
import com.neatcode.tabgreater.core.data.db.WatchlistItemDao
import com.neatcode.tabgreater.core.data.db.WatchlistItemEntity
import com.neatcode.tabgreater.core.exchange.ExchangeAdapter
import com.neatcode.tabgreater.core.model.Candle
import com.neatcode.tabgreater.core.model.ExchangeId
import com.neatcode.tabgreater.core.model.Market
import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.Ticker
import com.neatcode.tabgreater.core.model.Timeframe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** In-memory stand-ins for the Room DAOs so the repositories can be tested on the JVM. */

/** The two watchlist DAOs, wired to each other so cascades and the count join behave like Room. */
internal class FakeWatchlistDaos {
    val watchlists = FakeWatchlistDao()
    val items = FakeWatchlistItemDao()

    init {
        watchlists.items = items
        items.watchlists = watchlists
    }
}

internal class FakeWatchlistDao : WatchlistDao {
    private val rows = MutableStateFlow<List<WatchlistEntity>>(emptyList())
    private var nextId = 1L

    /** Set to mirror the `ON DELETE CASCADE` from `watchlists` to `watchlist_items`. */
    var items: FakeWatchlistItemDao? = null

    override fun observeAll(): Flow<List<WatchlistEntity>> = rows.map { list -> list.sortedBy { it.position } }
    override suspend fun getAll(): List<WatchlistEntity> = rows.value.sortedBy { it.position }
    override suspend fun getById(id: Long): WatchlistEntity? = rows.value.firstOrNull { it.id == id }
    override suspend fun count(): Int = rows.value.size

    override suspend fun insert(watchlist: WatchlistEntity): Long {
        val id = nextId++
        rows.value = rows.value + watchlist.copy(id = id)
        return id
    }

    override suspend fun update(watchlist: WatchlistEntity) {
        rows.value = rows.value.map { if (it.id == watchlist.id) watchlist else it }
    }

    override suspend fun delete(id: Long) {
        rows.value = rows.value.filterNot { it.id == id }
        items?.deleteByWatchlist(id)
    }

    override suspend fun deleteAll() {
        rows.value = emptyList()
        items?.deleteAllItems()
    }

    override suspend fun setPosition(id: Long, position: Int) = edit(id) { it.copy(position = position) }

    override suspend fun shiftPositionsFrom(from: Int) {
        rows.value = rows.value.map { if (it.position >= from) it.copy(position = it.position + 1) else it }
    }

    override suspend fun setPeriod(id: Long, period: String) = edit(id) { it.copy(period = period) }
    override suspend fun setTileSize(id: Long, tileSize: String) = edit(id) { it.copy(tileSize = tileSize) }
    override suspend fun setSort(id: Long, sort: String) = edit(id) { it.copy(sort = sort) }

    private fun edit(id: Long, block: (WatchlistEntity) -> WatchlistEntity) {
        rows.value = rows.value.map { if (it.id == id) block(it) else it }
    }
}

internal class FakeWatchlistItemDao : WatchlistItemDao {
    private val rows = MutableStateFlow<List<WatchlistItemEntity>>(emptyList())
    private var nextId = 1L

    /** Set so [observeItemCounts] can report `0` for lists without items, like the SQL left join. */
    var watchlists: FakeWatchlistDao? = null

    val all: List<WatchlistItemEntity> get() = rows.value.sortedBy { it.position }

    override fun observeByWatchlist(watchlistId: Long): Flow<List<WatchlistItemEntity>> =
        rows.map { list -> list.filter { it.watchlistId == watchlistId }.sortedBy { it.position } }

    override suspend fun getByWatchlist(watchlistId: Long): List<WatchlistItemEntity> =
        rows.value.filter { it.watchlistId == watchlistId }.sortedBy { it.position }

    override suspend fun getAllItems(): List<WatchlistItemEntity> =
        rows.value.sortedWith(compareBy({ it.watchlistId }, { it.position }))

    override suspend fun getByIds(ids: List<Long>): List<WatchlistItemEntity> =
        rows.value.filter { it.id in ids }.sortedWith(compareBy({ it.watchlistId }, { it.position }))

    override fun observeAllMarketKeys(): Flow<List<String>> =
        rows.map { list -> list.map { it.marketKey }.distinct() }

    override fun observeItemCounts(): Flow<List<WatchlistItemCount>> =
        combine(watchlists?.observeAll() ?: flowOf(emptyList()), rows) { lists, items ->
            lists.map { list -> WatchlistItemCount(list.id, items.count { it.watchlistId == list.id }) }
        }

    override suspend fun count(watchlistId: Long): Int = getByWatchlist(watchlistId).size

    override suspend fun nextPosition(watchlistId: Long): Int =
        (getByWatchlist(watchlistId).maxOfOrNull { it.position } ?: -1) + 1

    override suspend fun insert(item: WatchlistItemEntity): Long {
        if (conflicts(item)) return -1
        val id = nextId++
        rows.value = rows.value + item.copy(id = id)
        return id
    }

    override suspend fun insertAll(items: List<WatchlistItemEntity>) {
        for (item in items) insert(item)
    }

    override suspend fun update(item: WatchlistItemEntity) {
        rows.value = rows.value.map { if (it.id == item.id) item else it }
    }

    override suspend fun updateAll(items: List<WatchlistItemEntity>) {
        for (item in items) update(item)
    }

    override suspend fun delete(id: Long) {
        rows.value = rows.value.filterNot { it.id == id }
    }

    override suspend fun deleteByIds(ids: List<Long>) {
        rows.value = rows.value.filterNot { it.id in ids }
    }

    override suspend fun setAccentColor(id: Long, color: Long?) {
        rows.value = rows.value.map { if (it.id == id) it.copy(accentColor = color) else it }
    }

    override suspend fun setAccentColors(ids: List<Long>, color: Long?) {
        rows.value = rows.value.map { if (it.id in ids) it.copy(accentColor = color) else it }
    }

    fun deleteByWatchlist(watchlistId: Long) {
        rows.value = rows.value.filterNot { it.watchlistId == watchlistId }
    }

    fun deleteAllItems() {
        rows.value = emptyList()
    }

    /** Mirrors the unique index on `(watchlist_id, market_key)` that makes `insert` a no-op. */
    private fun conflicts(item: WatchlistItemEntity): Boolean =
        rows.value.any { it.watchlistId == item.watchlistId && it.marketKey == item.marketKey }
}

internal class FakeCandleDao : CandleDao {
    private val rows = MutableStateFlow<List<CandleEntity>>(emptyList())

    val all: List<CandleEntity> get() = rows.value

    override suspend fun latest(key: String, timeframe: String, limit: Int): List<CandleEntity> =
        rows.value.filter { it.marketKey == key && it.timeframe == timeframe }
            .sortedByDescending { it.openTime }
            .take(limit)

    override fun observeLatest(key: String, timeframe: String, limit: Int): Flow<List<CandleEntity>> =
        rows.map { list ->
            list.filter { it.marketKey == key && it.timeframe == timeframe }
                .sortedByDescending { it.openTime }
                .take(limit)
        }

    override suspend fun range(key: String, timeframe: String, from: Long, to: Long): List<CandleEntity> =
        rows.value.filter { it.marketKey == key && it.timeframe == timeframe && it.openTime >= from && it.openTime < to }
            .sortedBy { it.openTime }

    override suspend fun upsertAll(candles: List<CandleEntity>) {
        val merged = rows.value.toMutableList()
        for (candle in candles) {
            val index = merged.indexOfFirst {
                it.marketKey == candle.marketKey && it.timeframe == candle.timeframe && it.openTime == candle.openTime
            }
            if (index >= 0) merged[index] = candle else merged += candle
        }
        rows.value = merged
    }

    override suspend fun prune(key: String, timeframe: String, before: Long) {
        rows.value = rows.value.filterNot { it.marketKey == key && it.timeframe == timeframe && it.openTime < before }
    }

    override suspend fun distinctKeys(): List<String> = rows.value.map { it.marketKey }.distinct()

    override suspend fun deleteByKeys(keys: List<String>) {
        rows.value = rows.value.filterNot { it.marketKey in keys }
    }
}

internal class FakeTickerSnapshotDao : TickerSnapshotDao {
    private val rows = MutableStateFlow<List<TickerSnapshotEntity>>(emptyList())

    val all: List<TickerSnapshotEntity> get() = rows.value

    override fun observeByKeys(keys: List<String>): Flow<List<TickerSnapshotEntity>> =
        rows.map { list -> list.filter { it.marketKey in keys } }

    override suspend fun get(key: String): TickerSnapshotEntity? = rows.value.firstOrNull { it.marketKey == key }

    override suspend fun upsert(snapshot: TickerSnapshotEntity) = upsertAll(listOf(snapshot))

    override suspend fun upsertAll(snapshots: List<TickerSnapshotEntity>) {
        val merged = rows.value.toMutableList()
        for (snapshot in snapshots) {
            val index = merged.indexOfFirst { it.marketKey == snapshot.marketKey }
            if (index >= 0) merged[index] = snapshot else merged += snapshot
        }
        rows.value = merged
    }

    override suspend fun distinctKeys(): List<String> = rows.value.map { it.marketKey }.distinct()

    override suspend fun deleteByKeys(keys: List<String>) {
        rows.value = rows.value.filterNot { it.marketKey in keys }
    }
}

internal fun candleEntity(key: String, openTime: Long, timeframe: String = "1h"): CandleEntity =
    CandleEntity(
        marketKey = key,
        timeframe = timeframe,
        openTime = openTime,
        open = 1.0,
        high = 2.0,
        low = 0.5,
        close = 1.5,
        volume = 10.0,
    )

internal fun snapshotEntity(key: String): TickerSnapshotEntity =
    TickerSnapshotEntity(
        marketKey = key,
        last = 1.0,
        open24h = 1.0,
        high24h = 2.0,
        low24h = 0.5,
        volumeBase24h = 10.0,
        volumeQuote24h = 12.0,
        changePct24h = 1.0,
        timestamp = 0L,
    )

internal class FakeMarketDao(seed: List<MarketEntity> = emptyList()) : MarketDao {
    private val rows = LinkedHashMap<String, MarketEntity>()
    var deleteStaleCalls: Int = 0
        private set

    init {
        seed.forEach { rows[it.marketKey] = it }
    }

    val all: List<MarketEntity> get() = rows.values.toList()

    override suspend fun getByExchange(exchange: String): List<MarketEntity> =
        rows.values.filter { it.exchange == exchange && it.active }.sortedWith(order)

    override suspend fun getByKeys(keys: List<String>): List<MarketEntity> = keys.mapNotNull { rows[it] }

    override suspend fun getByKey(key: String): MarketEntity? = rows[key]

    override suspend fun count(exchange: String): Int = rows.values.count { it.exchange == exchange }

    override suspend fun lastUpdated(exchange: String): Long? =
        rows.values.filter { it.exchange == exchange }.maxOfOrNull { it.updatedAt }

    override suspend fun search(prefix: String, pattern: String, limit: Int): List<MarketEntity> =
        rows.values.asSequence()
            .filter { it.active }
            .filter { it.base.startsWith(prefix) || it.quote.startsWith(prefix) || like("${it.base}/${it.quote}", pattern) }
            .sortedWith(order)
            .take(limit)
            .toList()

    override suspend fun searchPair(base: String, quotePrefix: String, limit: Int): List<MarketEntity> =
        rows.values.asSequence()
            .filter { it.active && (base.isEmpty() || it.base == base) && it.quote.startsWith(quotePrefix) }
            .sortedWith(order)
            .take(limit)
            .toList()

    override suspend fun searchConcatCandidates(query: String, limit: Int): List<MarketEntity> =
        rows.values.asSequence()
            .filter { it.active && query.startsWith(it.base) }
            .sortedWith(order)
            .take(limit)
            .toList()

    override suspend fun upsertAll(markets: List<MarketEntity>) {
        markets.forEach { rows[it.marketKey] = it }
    }

    override suspend fun deleteMissing(exchange: String, keepKeys: List<String>) {
        rows.values.removeAll { it.exchange == exchange && it.marketKey !in keepKeys }
    }

    override suspend fun deleteStale(exchange: String, refreshedAt: Long) {
        deleteStaleCalls++
        rows.values.removeAll { it.exchange == exchange && it.updatedAt < refreshedAt }
    }

    /** Only the `prefix || '%'` shape is used by the repository. */
    private fun like(value: String, pattern: String): Boolean =
        value.startsWith(pattern.removeSuffix("%"))

    private companion object {
        val order = compareBy<MarketEntity>({ it.exchange }, { it.base }, { it.quote })
    }
}

/** Minimal [ExchangeAdapter] so an [com.neatcode.tabgreater.core.exchange.ExchangeRegistry] can be built. */
internal class FakeExchangeAdapter(
    override val id: ExchangeId,
    private val instruments: List<Market> = emptyList(),
    private val failure: Exception? = null,
) : ExchangeAdapter {

    var listMarketsCalls: Int = 0
        private set

    override val nativeTimeframes: Set<Timeframe> = Timeframe.entries.toSet()

    override suspend fun listMarkets(): List<Market> {
        listMarketsCalls++
        failure?.let { throw it }
        return instruments
    }

    override suspend fun fetchTickers(markets: List<Market>): List<Ticker> = emptyList()

    override suspend fun fetchOHLCV(
        market: Market,
        timeframe: Timeframe,
        endTime: Long?,
        limit: Int,
    ): List<Candle> = emptyList()

    override fun watchTickers(markets: List<Market>): Flow<Ticker> = emptyFlow()

    override fun watchKlines(market: Market, timeframe: Timeframe): Flow<Candle> = emptyFlow()
}

internal fun marketEntity(
    key: String,
    nativeSymbol: String = key.substringAfter(':').replace("/", ""),
    updatedAt: Long = 0L,
    active: Boolean = true,
): MarketEntity {
    val parsed = MarketKey(key)
    return MarketEntity(
        marketKey = key,
        exchange = parsed.exchange.id,
        base = parsed.base,
        quote = parsed.quote,
        nativeSymbol = nativeSymbol,
        pricePrecision = 2,
        tickSize = 0.01,
        active = active,
        updatedAt = updatedAt,
    )
}

internal fun market(key: String): Market =
    Market(key = MarketKey(key), nativeSymbol = key.substringAfter(':').replace("/", ""), pricePrecision = 2)
