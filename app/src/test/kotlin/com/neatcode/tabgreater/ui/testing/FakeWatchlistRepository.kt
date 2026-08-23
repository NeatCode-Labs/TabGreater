package com.neatcode.tabgreater.ui.testing

import com.neatcode.tabgreater.core.data.repo.WatchlistRepository
import com.neatcode.tabgreater.core.model.ImportMode
import com.neatcode.tabgreater.core.model.ImportResult
import com.neatcode.tabgreater.core.model.Limits
import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.SortMode
import com.neatcode.tabgreater.core.model.SparkPeriod
import com.neatcode.tabgreater.core.model.TileSize
import com.neatcode.tabgreater.core.model.Watchlist
import com.neatcode.tabgreater.core.model.WatchlistItem
import com.neatcode.tabgreater.core.model.WatchlistSnapshot
import com.neatcode.tabgreater.core.model.backup.WatchlistBackup
import com.neatcode.tabgreater.core.model.backup.WatchlistBackupCodec
import com.neatcode.tabgreater.core.model.backup.WatchlistBackupEntry
import com.neatcode.tabgreater.core.model.backup.WatchlistBackupItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Complete in-memory [WatchlistRepository] for JVM tests, following the KDoc contract of the real
 * Room implementation (caps, ordering, duplicate handling, import modes).
 */
class FakeWatchlistRepository : WatchlistRepository {

    private val lists = MutableStateFlow<List<Watchlist>>(emptyList())
    private val items = MutableStateFlow<List<WatchlistItem>>(emptyList())
    private var nextWatchlistId = 1L
    private var nextItemId = 1L

    /** Current state, for assertions. */
    val watchlists: List<Watchlist> get() = lists.value.sortedBy { it.position }
    fun itemsOf(watchlistId: Long): List<WatchlistItem> =
        items.value.filter { it.watchlistId == watchlistId }.sortedBy { it.position }

    fun watchlist(id: Long): Watchlist? = lists.value.firstOrNull { it.id == id }
    fun items(watchlistId: Long): List<WatchlistItem> = itemsOf(watchlistId)

    /** Seeds a list with its tickers without going through the public API. */
    fun seed(
        name: String,
        keys: List<String> = emptyList(),
        period: SparkPeriod = SparkPeriod.HOURS_24,
        tileSize: TileSize = TileSize.SMALL,
        sort: SortMode = SortMode.CUSTOM,
    ): Long {
        val id = nextWatchlistId++
        lists.value = lists.value + Watchlist(id, name, lists.value.size, period, tileSize, sort)
        keys.forEachIndexed { index, key ->
            items.value = items.value +
                WatchlistItem(nextItemId++, id, MarketKey(key), index)
        }
        return id
    }

    override fun observeWatchlists(): Flow<List<Watchlist>> = lists.map { it.sortedBy { w -> w.position } }

    override fun observeWatchlist(id: Long): Flow<Watchlist?> = lists.map { all -> all.firstOrNull { it.id == id } }

    override fun observeItems(watchlistId: Long): Flow<List<WatchlistItem>> =
        items.map { all -> all.filter { it.watchlistId == watchlistId }.sortedBy { it.position } }

    override fun observeAllKeys(): Flow<Set<MarketKey>> =
        items.map { all -> all.mapTo(LinkedHashSet()) { it.key } }

    override suspend fun ensureDefault(): Long =
        watchlists.firstOrNull()?.id ?: seed(DEFAULT_NAME)

    override fun observeItemCounts(): Flow<Map<Long, Int>> =
        combine(lists, items) { all, rows ->
            all.associate { list -> list.id to rows.count { it.watchlistId == list.id } }
        }

    override suspend fun createWatchlist(name: String): Long {
        val existing = watchlists
        if (existing.size >= Limits.MAX_WATCHLISTS) return existing.last().id
        return seed(name)
    }

    override suspend fun renameWatchlist(id: Long, name: String) {
        lists.value = lists.value.map { if (it.id == id) it.copy(name = name) else it }
    }

    override suspend fun deleteWatchlist(id: Long) {
        lists.value = lists.value.filterNot { it.id == id }
        items.value = items.value.filterNot { it.watchlistId == id }
        renumber()
    }

    override suspend fun copyWatchlist(id: Long, name: String): Long? {
        val source = lists.value.firstOrNull { it.id == id } ?: return null
        if (lists.value.size >= Limits.MAX_WATCHLISTS) return null
        val newId = nextWatchlistId++
        lists.value = lists.value + source.copy(id = newId, name = name, position = lists.value.size)
        items.value = items.value + itemsOf(id).map {
            it.copy(id = nextItemId++, watchlistId = newId)
        }
        return newId
    }

    override suspend fun reorderWatchlists(orderedIds: List<Long>) {
        val ranked = orderedIds.withIndex().associate { (index, id) -> id to index }
        val rest = watchlists.filter { it.id !in ranked }
        val ordered = orderedIds.mapNotNull { id -> lists.value.firstOrNull { it.id == id } } + rest
        lists.value = ordered.mapIndexed { index, watchlist -> watchlist.copy(position = index) }
    }

    override suspend fun snapshotWatchlist(id: Long): WatchlistSnapshot? {
        val watchlist = lists.value.firstOrNull { it.id == id } ?: return null
        return WatchlistSnapshot(watchlist, itemsOf(id))
    }

    override suspend fun restoreWatchlist(snapshot: WatchlistSnapshot): Long? {
        if (lists.value.size >= Limits.MAX_WATCHLISTS) return null
        val newId = nextWatchlistId++
        val position = snapshot.watchlist.position.coerceIn(0, lists.value.size)
        lists.value = lists.value.map {
            if (it.position >= position) it.copy(position = it.position + 1) else it
        } + snapshot.watchlist.copy(id = newId, position = position)
        items.value = items.value + snapshot.items.map { it.copy(id = nextItemId++, watchlistId = newId) }
        renumber()
        return newId
    }

    override suspend fun addItems(watchlistId: Long, keys: List<MarketKey>) {
        val existing = itemsOf(watchlistId)
        val room = Limits.MAX_ITEMS_PER_WATCHLIST - existing.size
        if (room <= 0) return
        val present = existing.mapTo(HashSet()) { it.key }
        var position = (existing.maxOfOrNull { it.position } ?: -1) + 1
        val fresh = keys.distinct().filterNot { it in present }.take(room)
        items.value = items.value + fresh.map { key ->
            WatchlistItem(nextItemId++, watchlistId, key, position++)
        }
    }

    override suspend fun removeItem(itemId: Long) = removeItems(listOf(itemId))

    override suspend fun removeItems(itemIds: Collection<Long>) {
        items.value = items.value.filterNot { it.id in itemIds }
    }

    override suspend fun reorderItems(watchlistId: Long, orderedItemIds: List<Long>) {
        val ranked = orderedItemIds.withIndex().associate { (index, id) -> id to index }
        items.value = items.value.map { item ->
            ranked[item.id]?.let { item.copy(position = it) } ?: item
        }
    }

    override suspend fun moveItemsToTop(itemIds: Collection<Long>) {
        val moving = items.value.filter { it.id in itemIds }.sortedBy { it.position }
        val byList = moving.groupBy { it.watchlistId }
        byList.forEach { (listId, rows) ->
            val rest = itemsOf(listId).filterNot { it.id in itemIds }
            val ordered = rows + rest
            items.value = items.value.map { item ->
                val index = ordered.indexOfFirst { it.id == item.id }
                if (item.watchlistId == listId && index >= 0) item.copy(position = index) else item
            }
        }
    }

    /** Mirrors the Room implementation: items that do not fit the cap stay in their source list. */
    override suspend fun moveItemsToWatchlist(itemIds: Collection<Long>, targetWatchlistId: Long): Int {
        val moving = items.value
            .filter { it.id in itemIds && it.watchlistId != targetWatchlistId }
            .sortedBy { it.position }
        val target = itemsOf(targetWatchlistId)
        val present = target.mapTo(HashSet()) { it.key }
        var position = (target.maxOfOrNull { it.position } ?: -1) + 1
        var room = Limits.MAX_ITEMS_PER_WATCHLIST - target.size
        val moved = HashMap<Long, WatchlistItem>()
        for (item in moving) {
            // Duplicate first: the market is already in the target, so only the source row goes.
            if (item.key in present) {
                moved[item.id] = item.copy(watchlistId = REMOVED)
                continue
            }
            // Then room: without it the item is left untouched in its source list.
            if (room <= 0) continue
            moved[item.id] = item.copy(watchlistId = targetWatchlistId, position = position++)
            present += item.key
            room--
        }
        items.value = items.value.mapNotNull { item ->
            val replacement = moved[item.id] ?: return@mapNotNull item
            replacement.takeIf { it.watchlistId != REMOVED }
        }
        return moved.size
    }

    override suspend fun setAccentColor(itemId: Long, argb: Long?) = setAccentColor(listOf(itemId), argb)

    override suspend fun setAccentColor(itemIds: Collection<Long>, argb: Long?) {
        items.value = items.value.map { if (it.id in itemIds) it.copy(accentColor = argb) else it }
    }

    override suspend fun setPeriod(watchlistId: Long, period: SparkPeriod) =
        edit(watchlistId) { it.copy(period = period) }

    override suspend fun setTileSize(watchlistId: Long, size: TileSize) =
        edit(watchlistId) { it.copy(tileSize = size) }

    override suspend fun setSort(watchlistId: Long, sort: SortMode) =
        edit(watchlistId) { it.copy(sort = sort) }

    override suspend fun exportBackup(exportedAt: Long): WatchlistBackup = WatchlistBackup(
        exportedAt = exportedAt,
        watchlists = watchlists.map { watchlist ->
            WatchlistBackupEntry(
                name = watchlist.name,
                period = watchlist.period.id,
                tileSize = watchlist.tileSize.id,
                sort = watchlist.sort.id,
                items = itemsOf(watchlist.id).map { item ->
                    WatchlistBackupItem(
                        key = item.key.value,
                        accentColor = item.accentColor?.let(WatchlistBackupCodec::formatArgb),
                    )
                },
            )
        },
    )

    override suspend fun importBackup(backup: WatchlistBackup, mode: ImportMode): ImportResult {
        if (mode == ImportMode.REPLACE) {
            lists.value = emptyList()
            items.value = emptyList()
        }
        var added = 0
        var merged = 0
        var itemsAdded = 0
        var itemsSkipped = 0
        var watchlistsSkipped = 0

        for (entry in backup.watchlists) {
            val name = entry.name.trim().take(Limits.MAX_WATCHLIST_NAME_LENGTH).ifEmpty { IMPORTED_NAME }
            val valid = entry.items.mapNotNull { it.marketKey?.let { key -> key to it.accentArgb } }
            itemsSkipped += entry.items.size - valid.size

            val existing = if (mode == ImportMode.MERGE) {
                watchlists.firstOrNull { it.name.equals(name, ignoreCase = true) }
            } else {
                null
            }
            val targetId = when {
                existing != null -> existing.id.also { merged++ }
                lists.value.size >= Limits.MAX_WATCHLISTS -> {
                    watchlistsSkipped++
                    itemsSkipped += valid.size
                    continue
                }

                else -> seed(
                    name = name,
                    period = SparkPeriod.fromId(entry.period),
                    tileSize = TileSize.fromId(entry.tileSize),
                    sort = SortMode.fromId(entry.sort),
                ).also { added++ }
            }

            val present = itemsOf(targetId).mapTo(HashSet()) { it.key }
            var position = (itemsOf(targetId).maxOfOrNull { it.position } ?: -1) + 1
            for ((key, accent) in valid) {
                if (key in present || position >= Limits.MAX_ITEMS_PER_WATCHLIST) {
                    itemsSkipped++
                    continue
                }
                items.value = items.value + WatchlistItem(nextItemId++, targetId, key, position++, accent)
                present += key
                itemsAdded++
            }
        }
        return ImportResult(added, merged, itemsAdded, itemsSkipped, watchlistsSkipped)
    }

    private fun edit(id: Long, block: (Watchlist) -> Watchlist) {
        lists.value = lists.value.map { if (it.id == id) block(it) else it }
    }

    private fun renumber() {
        lists.value = lists.value.sortedBy { it.position }
            .mapIndexed { index, watchlist -> watchlist.copy(position = index) }
    }

    private companion object {
        const val DEFAULT_NAME = "Main"
        const val IMPORTED_NAME = "Imported"

        /** Sentinel watchlist id marking an item that a move has to drop instead of relocate. */
        const val REMOVED = -1L
    }
}
