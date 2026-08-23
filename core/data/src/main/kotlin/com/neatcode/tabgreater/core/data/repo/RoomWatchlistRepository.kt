package com.neatcode.tabgreater.core.data.repo

import com.neatcode.tabgreater.core.data.db.TransactionRunner
import com.neatcode.tabgreater.core.data.db.WatchlistDao
import com.neatcode.tabgreater.core.data.db.WatchlistEntity
import com.neatcode.tabgreater.core.data.db.WatchlistItemDao
import com.neatcode.tabgreater.core.data.db.WatchlistItemEntity
import com.neatcode.tabgreater.core.model.ImportMode
import com.neatcode.tabgreater.core.model.ImportResult
import com.neatcode.tabgreater.core.model.Limits
import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.WatchlistSnapshot
import com.neatcode.tabgreater.core.model.backup.WatchlistBackup
import com.neatcode.tabgreater.core.model.backup.WatchlistBackupCodec
import com.neatcode.tabgreater.core.model.backup.WatchlistBackupEntry
import com.neatcode.tabgreater.core.model.backup.WatchlistBackupItem
import com.neatcode.tabgreater.core.model.SortMode
import com.neatcode.tabgreater.core.model.SparkPeriod
import com.neatcode.tabgreater.core.model.TileSize
import com.neatcode.tabgreater.core.model.Watchlist
import com.neatcode.tabgreater.core.model.WatchlistItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Room-backed [WatchlistRepository].
 *
 * Multi-step operations are plain Kotlin over the two DAOs and are made atomic by
 * [TransactionRunner]; unit tests pass [TransactionRunner.Direct] together with fake DAOs.
 */
class RoomWatchlistRepository(
    private val watchlistDao: WatchlistDao,
    private val itemDao: WatchlistItemDao,
    private val transaction: TransactionRunner = TransactionRunner.Direct,
) : WatchlistRepository {

    override fun observeWatchlists(): Flow<List<Watchlist>> =
        watchlistDao.observeAll().map { rows -> rows.map { it.toModel() } }

    override fun observeWatchlist(id: Long): Flow<Watchlist?> =
        watchlistDao.observeAll()
            .map { rows -> rows.firstOrNull { it.id == id }?.toModel() }
            .distinctUntilChanged()

    override fun observeItems(watchlistId: Long): Flow<List<WatchlistItem>> =
        itemDao.observeByWatchlist(watchlistId).map { rows -> rows.mapNotNull { it.toModelOrNull() } }

    override fun observeAllKeys(): Flow<Set<MarketKey>> =
        itemDao.observeAllMarketKeys()
            .map { keys -> keys.mapNotNullTo(LinkedHashSet()) { MarketKey.parseOrNull(it) } }
            .distinctUntilChanged()

    override suspend fun ensureDefault(): Long {
        if (watchlistDao.count() == 0) {
            return watchlistDao.insert(WatchlistEntity(name = DEFAULT_NAME, position = 0))
        }
        return watchlistDao.getAll().first().id
    }

    /** Empty lists are reported as `0` (the DAO's left join keeps them), so `counts[id]` is never null for an existing list. */
    override fun observeItemCounts(): Flow<Map<Long, Int>> =
        itemDao.observeItemCounts()
            .map { rows -> rows.associate { it.watchlistId to it.count } }
            .distinctUntilChanged()

    /**
     * Appends a new watchlist. When [Limits.MAX_WATCHLISTS] is already reached nothing is created
     * and the id of the last existing watchlist is returned, so callers can still select something.
     */
    override suspend fun createWatchlist(name: String): Long {
        val existing = watchlistDao.getAll()
        if (existing.size >= Limits.MAX_WATCHLISTS) return existing.last().id
        val position = (existing.maxOfOrNull { it.position } ?: -1) + 1
        return watchlistDao.insert(WatchlistEntity(name = name, position = position))
    }

    override suspend fun renameWatchlist(id: Long, name: String) {
        val current = watchlistDao.getById(id) ?: return
        watchlistDao.update(current.copy(name = name))
    }

    override suspend fun deleteWatchlist(id: Long) {
        watchlistDao.delete(id)
    }

    override suspend fun copyWatchlist(id: Long, name: String): Long? = transaction { doCopyWatchlist(id, name) }

    override suspend fun reorderWatchlists(orderedIds: List<Long>) {
        if (orderedIds.isEmpty()) return
        watchlistDao.reorder(orderedIds)
    }

    override suspend fun snapshotWatchlist(id: Long): WatchlistSnapshot? {
        val row = watchlistDao.getById(id) ?: return null
        val items = itemDao.getByWatchlist(id).mapNotNull { it.toModelOrNull() }
        return WatchlistSnapshot(row.toModel(), items)
    }

    override suspend fun restoreWatchlist(snapshot: WatchlistSnapshot): Long? =
        transaction { doRestoreWatchlist(snapshot) }

    override suspend fun addItems(watchlistId: Long, keys: List<MarketKey>) {
        if (keys.isEmpty()) return
        val existing = itemDao.getByWatchlist(watchlistId)
        val room = Limits.MAX_ITEMS_PER_WATCHLIST - existing.size
        if (room <= 0) return
        val present = existing.mapTo(HashSet()) { it.marketKey }
        val fresh = LinkedHashSet<String>()
        for (key in keys) {
            if (key.value !in present) fresh += key.value
            if (fresh.size == room) break
        }
        if (fresh.isEmpty()) return
        var position = itemDao.nextPosition(watchlistId)
        itemDao.insertAll(
            fresh.map { marketKey ->
                WatchlistItemEntity(watchlistId = watchlistId, marketKey = marketKey, position = position++)
            },
        )
    }

    override suspend fun removeItem(itemId: Long) {
        itemDao.delete(itemId)
    }

    override suspend fun removeItems(itemIds: Collection<Long>) {
        val ids = itemIds.distinct()
        if (ids.isEmpty()) return
        for (chunk in ids.chunked(SQL_CHUNK)) itemDao.deleteByIds(chunk)
    }

    override suspend fun reorderItems(watchlistId: Long, orderedItemIds: List<Long>) {
        if (orderedItemIds.isEmpty()) return
        itemDao.reorder(watchlistId, orderedItemIds)
    }

    override suspend fun moveItemsToTop(itemIds: Collection<Long>) {
        if (itemIds.isEmpty()) return
        transaction { doMoveItemsToTop(itemIds) }
    }

    override suspend fun moveItemsToWatchlist(itemIds: Collection<Long>, targetWatchlistId: Long): Int {
        if (itemIds.isEmpty()) return 0
        return transaction { doMoveItemsToWatchlist(itemIds, targetWatchlistId) }
    }

    override suspend fun setAccentColor(itemId: Long, argb: Long?) {
        itemDao.setAccentColor(itemId, argb)
    }

    override suspend fun setAccentColor(itemIds: Collection<Long>, argb: Long?) {
        val ids = itemIds.distinct()
        if (ids.isEmpty()) return
        for (chunk in ids.chunked(SQL_CHUNK)) itemDao.setAccentColors(chunk, argb)
    }

    override suspend fun setPeriod(watchlistId: Long, period: SparkPeriod) {
        watchlistDao.setPeriod(watchlistId, period.id)
    }

    override suspend fun setTileSize(watchlistId: Long, size: TileSize) {
        watchlistDao.setTileSize(watchlistId, size.id)
    }

    override suspend fun setSort(watchlistId: Long, sort: SortMode) {
        watchlistDao.setSort(watchlistId, sort.id)
    }

    override suspend fun exportBackup(exportedAt: Long): WatchlistBackup {
        val lists = watchlistDao.getAll()
        val itemsByList = itemDao.getAllItems().groupBy { it.watchlistId }
        return WatchlistBackup(
            exportedAt = exportedAt,
            watchlists = lists.map { row ->
                WatchlistBackupEntry(
                    name = row.name,
                    period = row.period,
                    tileSize = row.tileSize,
                    sort = row.sort,
                    items = itemsByList[row.id].orEmpty().sortedBy { it.position }.map { item ->
                        WatchlistBackupItem(
                            key = item.marketKey,
                            accentColor = item.accentColor?.let(WatchlistBackupCodec::formatArgb),
                        )
                    },
                )
            },
        )
    }

    override suspend fun importBackup(backup: WatchlistBackup, mode: ImportMode): ImportResult = transaction {
        when (mode) {
            ImportMode.REPLACE -> doReplace(backup)
            ImportMode.MERGE -> doMerge(backup)
        }
    }

    // ---- multi-step operations, wrapped in one transaction by the callers above ----

    private suspend fun doCopyWatchlist(id: Long, name: String): Long? {
        val source = watchlistDao.getById(id) ?: return null
        val existing = watchlistDao.getAll()
        if (existing.size >= Limits.MAX_WATCHLISTS) return null
        val position = (existing.maxOfOrNull { it.position } ?: -1) + 1
        val newId = watchlistDao.insert(
            WatchlistEntity(
                name = cleanName(name, "${source.name} $COPY_SUFFIX"),
                position = position,
                period = source.period,
                tileSize = source.tileSize,
                sort = source.sort,
            ),
        )
        val items = itemDao.getByWatchlist(id)
        if (items.isNotEmpty()) {
            itemDao.insertAll(
                items.mapIndexed { index, item ->
                    item.copy(id = 0, watchlistId = newId, position = index)
                },
            )
        }
        return newId
    }

    private suspend fun doRestoreWatchlist(snapshot: WatchlistSnapshot): Long? {
        if (watchlistDao.getAll().size >= Limits.MAX_WATCHLISTS) return null
        val position = snapshot.watchlist.position.coerceAtLeast(0)
        watchlistDao.shiftPositionsFrom(position)
        val id = watchlistDao.insert(
            WatchlistEntity(
                name = cleanName(snapshot.watchlist.name, DEFAULT_NAME),
                position = position,
                period = snapshot.watchlist.period.id,
                tileSize = snapshot.watchlist.tileSize.id,
                sort = snapshot.watchlist.sort.id,
            ),
        )
        watchlistDao.normalisePositions()
        val items = snapshot.items.sortedBy { it.position }.take(Limits.MAX_ITEMS_PER_WATCHLIST)
        if (items.isNotEmpty()) {
            itemDao.insertAll(
                items.map { item ->
                    WatchlistItemEntity(
                        watchlistId = id,
                        marketKey = item.key.value,
                        position = item.position,
                        accentColor = item.accentColor,
                    )
                },
            )
        }
        return id
    }

    private suspend fun doMoveItemsToTop(itemIds: Collection<Long>) {
        val selected = loadItems(itemIds)
        if (selected.isEmpty()) return
        val ids = selected.mapTo(HashSet()) { it.id }
        for (watchlistId in selected.map { it.watchlistId }.distinct()) {
            val all = itemDao.getByWatchlist(watchlistId)
            val (top, rest) = all.partition { it.id in ids }
            itemDao.updateAll((top + rest).mapIndexed { index, item -> item.copy(position = index) })
        }
    }

    /**
     * A source row is deleted only when the market really ends up in the target: either it was
     * inserted, or the target already holds that market (a genuine move onto an existing tile).
     * An item that does not fit under the 100-item cap stays in the source list — deleting it
     * would destroy a ticker the app never placed anywhere.
     *
     * @return how many rows were removed from their source list.
     */
    private suspend fun doMoveItemsToWatchlist(itemIds: Collection<Long>, targetWatchlistId: Long): Int {
        if (watchlistDao.getById(targetWatchlistId) == null) return 0
        val moving = loadItems(itemIds).filter { it.watchlistId != targetWatchlistId }
        if (moving.isEmpty()) return 0

        val targetItems = itemDao.getByWatchlist(targetWatchlistId)
        val present = targetItems.mapTo(HashSet()) { it.marketKey }
        var room = Limits.MAX_ITEMS_PER_WATCHLIST - targetItems.size
        var position = itemDao.nextPosition(targetWatchlistId)
        val inserts = ArrayList<WatchlistItemEntity>(moving.size)
        val removable = ArrayList<Long>(moving.size)
        for (item in moving) {
            // Duplicate first: the market is already in the target, so the source row goes.
            if (item.marketKey in present) {
                removable += item.id
                continue
            }
            // Then room: without it the item is left untouched in its source list.
            if (room <= 0) continue
            present += item.marketKey
            room--
            removable += item.id
            inserts += item.copy(id = 0, watchlistId = targetWatchlistId, position = position++)
        }
        for (chunk in removable.chunked(SQL_CHUNK)) itemDao.deleteByIds(chunk)
        if (inserts.isNotEmpty()) itemDao.insertAll(inserts)
        return removable.size
    }

    private suspend fun doReplace(backup: WatchlistBackup): ImportResult {
        watchlistDao.deleteAll()
        var added = 0
        var skipped = 0
        var itemsAdded = 0
        var itemsSkipped = 0
        for (entry in backup.watchlists) {
            if (added >= Limits.MAX_WATCHLISTS) {
                skipped++
                continue
            }
            val id = watchlistDao.insert(entryEntity(entry, added))
            added++
            val outcome = insertBackupItems(id, entry.items, HashSet())
            itemsAdded += outcome.added
            itemsSkipped += outcome.skipped
        }
        // A backup without a single usable list must still leave the app with a watchlist to show.
        if (added == 0) watchlistDao.insert(WatchlistEntity(name = DEFAULT_NAME, position = 0))
        return ImportResult(added, 0, itemsAdded, itemsSkipped, skipped)
    }

    private suspend fun doMerge(backup: WatchlistBackup): ImportResult {
        var added = 0
        var merged = 0
        var skipped = 0
        var itemsAdded = 0
        var itemsSkipped = 0
        val existing = watchlistDao.getAll().toMutableList()
        var position = (existing.maxOfOrNull { it.position } ?: -1) + 1
        for (entry in backup.watchlists) {
            val name = cleanName(entry.name, IMPORTED_NAME)
            val match = existing.firstOrNull { it.name.trim().equals(name, ignoreCase = true) }
            val outcome = if (match != null) {
                merged++
                insertBackupItems(match.id, entry.items, itemDao.getByWatchlist(match.id).mapTo(HashSet()) { it.marketKey })
            } else if (existing.size >= Limits.MAX_WATCHLISTS) {
                skipped++
                continue
            } else {
                val row = entryEntity(entry, position++, name)
                val id = watchlistDao.insert(row)
                existing += row.copy(id = id)
                added++
                insertBackupItems(id, entry.items, HashSet())
            }
            itemsAdded += outcome.added
            itemsSkipped += outcome.skipped
        }
        return ImportResult(added, merged, itemsAdded, itemsSkipped, skipped)
    }

    /** How many of [items] made it into [watchlistId]; [present] carries the keys already there. */
    private suspend fun insertBackupItems(
        watchlistId: Long,
        items: List<WatchlistBackupItem>,
        present: MutableSet<String>,
    ): ItemOutcome {
        var count = present.size
        var position = itemDao.nextPosition(watchlistId)
        var skipped = 0
        val rows = ArrayList<WatchlistItemEntity>(items.size)
        for (item in items) {
            val key = item.marketKey
            if (key == null || count >= Limits.MAX_ITEMS_PER_WATCHLIST || !present.add(key.value)) {
                skipped++
                continue
            }
            count++
            rows += WatchlistItemEntity(
                watchlistId = watchlistId,
                marketKey = key.value,
                position = position++,
                accentColor = item.accentArgb,
            )
        }
        if (rows.isNotEmpty()) itemDao.insertAll(rows)
        return ItemOutcome(rows.size, skipped)
    }

    /** Reads the selected items in `(watchlist, position)` order, chunked around SQLite's variable limit. */
    private suspend fun loadItems(itemIds: Collection<Long>): List<WatchlistItemEntity> =
        itemIds.distinct()
            .chunked(SQL_CHUNK)
            .flatMap { itemDao.getByIds(it) }
            .sortedWith(compareBy({ it.watchlistId }, { it.position }))

    private fun entryEntity(
        entry: WatchlistBackupEntry,
        position: Int,
        name: String = cleanName(entry.name, IMPORTED_NAME),
    ): WatchlistEntity = WatchlistEntity(
        name = name,
        position = position,
        period = SparkPeriod.fromId(entry.period).id,
        tileSize = TileSize.fromId(entry.tileSize).id,
        sort = SortMode.fromId(entry.sort).id,
    )

    private fun cleanName(raw: String, fallback: String): String {
        val trimmed = raw.trim().take(Limits.MAX_WATCHLIST_NAME_LENGTH)
        return trimmed.ifBlank { fallback.trim().take(Limits.MAX_WATCHLIST_NAME_LENGTH) }
    }

    private data class ItemOutcome(val added: Int, val skipped: Int)

    private companion object {
        const val DEFAULT_NAME = "Main"
        const val IMPORTED_NAME = "Imported"
        const val COPY_SUFFIX = "copy"

        /** Well under SQLite's 999 bound variables per statement. */
        const val SQL_CHUNK = 500
    }
}
