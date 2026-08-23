package com.neatcode.tabgreater.core.data.repo

import com.neatcode.tabgreater.core.data.db.WatchlistItemEntity
import com.neatcode.tabgreater.core.model.ImportMode
import com.neatcode.tabgreater.core.model.Limits
import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.SortMode
import com.neatcode.tabgreater.core.model.SparkPeriod
import com.neatcode.tabgreater.core.model.TileSize
import com.neatcode.tabgreater.core.model.backup.WatchlistBackup
import com.neatcode.tabgreater.core.model.backup.WatchlistBackupEntry
import com.neatcode.tabgreater.core.model.backup.WatchlistBackupItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomWatchlistRepositoryTest {

    private val daos = FakeWatchlistDaos()
    private val repository = RoomWatchlistRepository(daos.watchlists, daos.items)

    @Test
    fun `ensureDefault creates Main once`() = runTest {
        val first = repository.ensureDefault()
        val second = repository.ensureDefault()
        assertEquals(first, second)

        val lists = repository.observeWatchlists().first()
        assertEquals(1, lists.size)
        assertEquals("Main", lists.single().name)
        assertEquals(0, lists.single().position)
    }

    @Test
    fun `addItems appends in order and ignores duplicates`() = runTest {
        val id = repository.ensureDefault()
        repository.addItems(id, listOf(key("binance:BTC/EUR"), key("binance:ETH/EUR")))
        repository.addItems(id, listOf(key("binance:BTC/EUR"), key("binance:SOL/EUR")))

        val items = repository.observeItems(id).first()
        assertEquals(
            listOf("binance:BTC/EUR", "binance:ETH/EUR", "binance:SOL/EUR"),
            items.map { it.key.value },
        )
        assertEquals(listOf(0, 1, 2), items.map { it.position })
    }

    @Test
    fun `addItems drops duplicates inside one call`() = runTest {
        val id = repository.ensureDefault()
        repository.addItems(id, listOf(key("binance:BTC/EUR"), key("binance:BTC/EUR")))
        assertEquals(1, repository.observeItems(id).first().size)
    }

    @Test
    fun `addItems respects the per watchlist cap`() = runTest {
        val id = repository.ensureDefault()
        val many = (0 until Limits.MAX_ITEMS_PER_WATCHLIST + 20).map { key("binance:C$it/EUR") }
        repository.addItems(id, many)
        assertEquals(Limits.MAX_ITEMS_PER_WATCHLIST, repository.observeItems(id).first().size)

        repository.addItems(id, listOf(key("binance:ZZZ/EUR")))
        assertEquals(Limits.MAX_ITEMS_PER_WATCHLIST, repository.observeItems(id).first().size)
    }

    @Test
    fun `reorderItems rewrites positions`() = runTest {
        val id = repository.ensureDefault()
        repository.addItems(id, listOf(key("binance:BTC/EUR"), key("binance:ETH/EUR"), key("binance:SOL/EUR")))
        val items = repository.observeItems(id).first()

        repository.reorderItems(id, listOf(items[2].id, items[0].id, items[1].id))

        assertEquals(
            listOf("binance:SOL/EUR", "binance:BTC/EUR", "binance:ETH/EUR"),
            repository.observeItems(id).first().map { it.key.value },
        )
    }

    @Test
    fun `observeAllKeys reports every referenced market`() = runTest {
        val main = repository.ensureDefault()
        val other = repository.createWatchlist("Alts")
        repository.addItems(main, listOf(key("binance:BTC/EUR")))
        repository.addItems(other, listOf(key("binance:BTC/EUR"), key("kraken:ETH/EUR")))

        assertEquals(
            setOf(key("binance:BTC/EUR"), key("kraken:ETH/EUR")),
            repository.observeAllKeys().first(),
        )
    }

    @Test
    fun `createWatchlist appends and stops at the cap`() = runTest {
        repository.ensureDefault()
        repeat(Limits.MAX_WATCHLISTS - 1) { repository.createWatchlist("List $it") }
        assertEquals(Limits.MAX_WATCHLISTS, repository.observeWatchlists().first().size)

        val overflow = repository.createWatchlist("Too many")
        assertEquals(Limits.MAX_WATCHLISTS, repository.observeWatchlists().first().size)
        assertEquals(repository.observeWatchlists().first().last().id, overflow)
    }

    @Test
    fun `rename and per list settings are persisted`() = runTest {
        val id = repository.ensureDefault()
        repository.renameWatchlist(id, "Majors")
        repository.setPeriod(id, SparkPeriod.DAYS_7)

        val list = repository.observeWatchlist(id).first()
        assertNotNull(list)
        assertEquals("Majors", list?.name)
        assertEquals(SparkPeriod.DAYS_7, list?.period)
    }

    @Test
    fun `deleting a watchlist removes it`() = runTest {
        val id = repository.ensureDefault()
        repository.deleteWatchlist(id)
        assertEquals(emptyList<Long>(), repository.observeWatchlists().first().map { it.id })
    }

    // ---- F3: watchlist manager operations ----

    @Test
    fun `observeItemCounts reports zero for empty lists`() = runTest {
        val main = repository.ensureDefault()
        val alts = repository.createWatchlist("Alts")
        repository.addItems(main, listOf(key("binance:BTC/EUR"), key("binance:ETH/EUR")))

        assertEquals(mapOf(main to 2, alts to 0), repository.observeItemCounts().first())
    }

    @Test
    fun `observeItemCounts follows added and removed items`() = runTest {
        val main = repository.ensureDefault()
        repository.addItems(main, listOf(key("binance:BTC/EUR")))
        assertEquals(mapOf(main to 1), repository.observeItemCounts().first())

        val item = repository.observeItems(main).first().single()
        repository.removeItems(listOf(item.id))
        assertEquals(mapOf(main to 0), repository.observeItemCounts().first())
    }

    @Test
    fun `copyWatchlist duplicates settings items and accents`() = runTest {
        val main = repository.ensureDefault()
        repository.setPeriod(main, SparkPeriod.DAYS_30)
        repository.setTileSize(main, TileSize.LARGE)
        repository.setSort(main, SortMode.CHANGE)
        repository.addItems(main, listOf(key("binance:BTC/EUR"), key("kraken:ETH/EUR")))
        val source = repository.observeItems(main).first()
        repository.setAccentColor(source.first().id, 0xFFFFBF66L)

        val copyId = repository.copyWatchlist(main, "Copy")
        assertNotNull(copyId)

        val copy = repository.observeWatchlist(copyId!!).first()!!
        assertEquals("Copy", copy.name)
        assertEquals(1, copy.position)
        assertEquals(SparkPeriod.DAYS_30, copy.period)
        assertEquals(TileSize.LARGE, copy.tileSize)
        assertEquals(SortMode.CHANGE, copy.sort)

        val copied = repository.observeItems(copyId).first()
        assertEquals(listOf("binance:BTC/EUR", "kraken:ETH/EUR"), copied.map { it.key.value })
        assertEquals(listOf(0, 1), copied.map { it.position })
        assertEquals(listOf(0xFFFFBF66L, null), copied.map { it.accentColor })
        // The source is untouched.
        assertEquals(2, repository.observeItems(main).first().size)
    }

    @Test
    fun `copyWatchlist falls back to the source name and clamps it`() = runTest {
        val main = repository.ensureDefault()
        repository.renameWatchlist(main, "A".repeat(Limits.MAX_WATCHLIST_NAME_LENGTH))

        val copyId = repository.copyWatchlist(main, "   ")!!
        val name = repository.observeWatchlist(copyId).first()!!.name
        assertEquals(Limits.MAX_WATCHLIST_NAME_LENGTH, name.length)
        assertTrue(name.startsWith("AAA"))
    }

    @Test
    fun `copyWatchlist is null for an unknown id and at the cap`() = runTest {
        repository.ensureDefault()
        assertNull(repository.copyWatchlist(999L, "Nope"))

        repeat(Limits.MAX_WATCHLISTS - 1) { repository.createWatchlist("List $it") }
        val first = repository.observeWatchlists().first().first().id
        assertNull(repository.copyWatchlist(first, "Overflow"))
    }

    @Test
    fun `reorderWatchlists puts the listed ids first and keeps the rest in order`() = runTest {
        val a = repository.ensureDefault()
        val b = repository.createWatchlist("B")
        val c = repository.createWatchlist("C")
        val d = repository.createWatchlist("D")

        repository.reorderWatchlists(listOf(c, 12345L, a))

        val lists = repository.observeWatchlists().first()
        assertEquals(listOf(c, a, b, d), lists.map { it.id })
        assertEquals(listOf(0, 1, 2, 3), lists.map { it.position })
    }

    @Test
    fun `snapshot and restore put a deleted watchlist back where it was`() = runTest {
        val a = repository.ensureDefault()
        val b = repository.createWatchlist("B")
        val c = repository.createWatchlist("C")
        repository.setPeriod(b, SparkPeriod.HOUR_1)
        repository.setTileSize(b, TileSize.COMPACT)
        repository.addItems(b, listOf(key("binance:BTC/EUR"), key("kraken:ETH/EUR")))
        repository.setAccentColor(repository.observeItems(b).first().last().id, 0xFF66BB6AL)

        val snapshot = repository.snapshotWatchlist(b)!!
        assertEquals("B", snapshot.watchlist.name)
        assertEquals(2, snapshot.items.size)

        repository.deleteWatchlist(b)
        assertEquals(listOf(a, c), repository.observeWatchlists().first().map { it.id })

        val restored = repository.restoreWatchlist(snapshot)!!
        val lists = repository.observeWatchlists().first()
        assertEquals(listOf(a, restored, c), lists.map { it.id })
        assertEquals(listOf(0, 1, 2), lists.map { it.position })

        val list = lists[1]
        assertEquals("B", list.name)
        assertEquals(SparkPeriod.HOUR_1, list.period)
        assertEquals(TileSize.COMPACT, list.tileSize)
        val items = repository.observeItems(restored).first()
        assertEquals(listOf("binance:BTC/EUR", "kraken:ETH/EUR"), items.map { it.key.value })
        assertEquals(listOf(null, 0xFF66BB6AL), items.map { it.accentColor })
    }

    @Test
    fun `restoreWatchlist works next to a list with the same name`() = runTest {
        repository.ensureDefault()
        val b = repository.createWatchlist("Twins")
        repository.addItems(b, listOf(key("binance:BTC/EUR")))
        val snapshot = repository.snapshotWatchlist(b)!!
        repository.deleteWatchlist(b)
        repository.createWatchlist("Twins")

        val restored = repository.restoreWatchlist(snapshot)
        assertNotNull(restored)
        assertEquals(2, repository.observeWatchlists().first().count { it.name == "Twins" })
    }

    @Test
    fun `restoreWatchlist is null at the cap`() = runTest {
        val main = repository.ensureDefault()
        val snapshot = repository.snapshotWatchlist(main)!!
        repeat(Limits.MAX_WATCHLISTS - 1) { repository.createWatchlist("List $it") }
        assertNull(repository.restoreWatchlist(snapshot))
    }

    @Test
    fun `removeItems deletes the whole selection`() = runTest {
        val main = repository.ensureDefault()
        repository.addItems(main, listOf(key("binance:BTC/EUR"), key("binance:ETH/EUR"), key("binance:SOL/EUR")))
        val items = repository.observeItems(main).first()

        repository.removeItems(listOf(items[0].id, items[2].id))

        assertEquals(listOf("binance:ETH/EUR"), repository.observeItems(main).first().map { it.key.value })
    }

    @Test
    fun `moveItemsToTop keeps the relative order of the selection`() = runTest {
        val main = repository.ensureDefault()
        repository.addItems(
            main,
            listOf(key("binance:A/EUR"), key("binance:B/EUR"), key("binance:C/EUR"), key("binance:D/EUR")),
        )
        val items = repository.observeItems(main).first()

        repository.moveItemsToTop(listOf(items[3].id, items[1].id))

        val after = repository.observeItems(main).first()
        assertEquals(
            listOf("binance:B/EUR", "binance:D/EUR", "binance:A/EUR", "binance:C/EUR"),
            after.map { it.key.value },
        )
        assertEquals(listOf(0, 1, 2, 3), after.map { it.position })
    }

    @Test
    fun `moveItemsToTop handles a selection spanning two watchlists`() = runTest {
        val main = repository.ensureDefault()
        val alts = repository.createWatchlist("Alts")
        repository.addItems(main, listOf(key("binance:A/EUR"), key("binance:B/EUR")))
        repository.addItems(alts, listOf(key("kraken:C/EUR"), key("kraken:D/EUR")))
        val mainItems = repository.observeItems(main).first()
        val altItems = repository.observeItems(alts).first()

        repository.moveItemsToTop(listOf(mainItems[1].id, altItems[1].id))

        assertEquals(
            listOf("binance:B/EUR", "binance:A/EUR"),
            repository.observeItems(main).first().map { it.key.value },
        )
        assertEquals(
            listOf("kraken:D/EUR", "kraken:C/EUR"),
            repository.observeItems(alts).first().map { it.key.value },
        )
    }

    @Test
    fun `moveItemsToWatchlist appends and drops duplicates`() = runTest {
        val main = repository.ensureDefault()
        val alts = repository.createWatchlist("Alts")
        repository.addItems(main, listOf(key("binance:BTC/EUR"), key("binance:ETH/EUR"), key("binance:SOL/EUR")))
        repository.addItems(alts, listOf(key("binance:ETH/EUR")))
        val items = repository.observeItems(main).first()
        repository.setAccentColor(items[2].id, 0xFF42A5F5L)

        val movedCount = repository.moveItemsToWatchlist(listOf(items[2].id, items[1].id), alts)

        assertEquals(2, movedCount)
        assertEquals(listOf("binance:BTC/EUR"), repository.observeItems(main).first().map { it.key.value })
        val moved = repository.observeItems(alts).first()
        // ETH already existed in the target, so it is only dropped from the source.
        assertEquals(listOf("binance:ETH/EUR", "binance:SOL/EUR"), moved.map { it.key.value })
        assertEquals(listOf(null, 0xFF42A5F5L), moved.map { it.accentColor })
    }

    @Test
    fun `moveItemsToWatchlist keeps the overflow in the source when the target is full`() = runTest {
        val main = repository.ensureDefault()
        val alts = repository.createWatchlist("Alts")
        repository.addItems(alts, (0 until Limits.MAX_ITEMS_PER_WATCHLIST).map { key("binance:F$it/EUR") })
        repository.addItems(main, listOf(key("kraken:BTC/EUR"), key("kraken:ETH/EUR")))
        val items = repository.observeItems(main).first()

        val movedCount = repository.moveItemsToWatchlist(items.map { it.id }, alts)

        assertEquals(0, movedCount)
        // Nothing fitted, so nothing was destroyed: both tickers survive, in their order.
        assertEquals(
            listOf("kraken:BTC/EUR", "kraken:ETH/EUR"),
            repository.observeItems(main).first().map { it.key.value },
        )
        assertEquals(Limits.MAX_ITEMS_PER_WATCHLIST, repository.observeItems(alts).first().size)
    }

    @Test
    fun `moveItemsToWatchlist moves what fits and leaves the rest in the source`() = runTest {
        val main = repository.ensureDefault()
        val alts = repository.createWatchlist("Alts")
        repository.addItems(alts, (0 until Limits.MAX_ITEMS_PER_WATCHLIST - 1).map { key("binance:F$it/EUR") })
        repository.addItems(
            main,
            listOf(key("kraken:BTC/EUR"), key("kraken:ETH/EUR"), key("kraken:SOL/EUR")),
        )
        val items = repository.observeItems(main).first()

        val movedCount = repository.moveItemsToWatchlist(items.map { it.id }, alts)

        assertEquals(1, movedCount)
        assertEquals(
            listOf("kraken:ETH/EUR", "kraken:SOL/EUR"),
            repository.observeItems(main).first().map { it.key.value },
        )
        val target = repository.observeItems(alts).first()
        assertEquals(Limits.MAX_ITEMS_PER_WATCHLIST, target.size)
        assertEquals("kraken:BTC/EUR", target.last().key.value)
    }

    @Test
    fun `moveItemsToWatchlist still absorbs a duplicate when the target is full`() = runTest {
        val main = repository.ensureDefault()
        val alts = repository.createWatchlist("Alts")
        repository.addItems(alts, (0 until Limits.MAX_ITEMS_PER_WATCHLIST).map { key("binance:F$it/EUR") })
        repository.addItems(main, listOf(key("binance:F0/EUR"), key("kraken:BTC/EUR")))
        val items = repository.observeItems(main).first()

        val movedCount = repository.moveItemsToWatchlist(items.map { it.id }, alts)

        // The duplicate is checked before the cap: its market lives on in the target, so the
        // source row goes; the one that needs a free slot stays.
        assertEquals(1, movedCount)
        assertEquals(listOf("kraken:BTC/EUR"), repository.observeItems(main).first().map { it.key.value })
        assertEquals(Limits.MAX_ITEMS_PER_WATCHLIST, repository.observeItems(alts).first().size)
    }

    @Test
    fun `moveItemsToWatchlist leaves items already in the target alone`() = runTest {
        val main = repository.ensureDefault()
        repository.addItems(main, listOf(key("binance:BTC/EUR")))
        val items = repository.observeItems(main).first()

        repository.moveItemsToWatchlist(items.map { it.id }, main)

        assertEquals(listOf("binance:BTC/EUR"), repository.observeItems(main).first().map { it.key.value })
    }

    @Test
    fun `setAccentColor paints and clears a whole selection`() = runTest {
        val main = repository.ensureDefault()
        repository.addItems(main, listOf(key("binance:BTC/EUR"), key("binance:ETH/EUR")))
        val ids = repository.observeItems(main).first().map { it.id }

        repository.setAccentColor(ids, 0xFFEF5350L)
        assertEquals(listOf(0xFFEF5350L, 0xFFEF5350L), repository.observeItems(main).first().map { it.accentColor })

        repository.setAccentColor(ids, null)
        assertEquals(listOf(null, null), repository.observeItems(main).first().map { it.accentColor })
    }

    // ---- F3: backup ----

    @Test
    fun `exportBackup keeps lists and items in display order`() = runTest {
        val main = repository.ensureDefault()
        val alts = repository.createWatchlist("Alts")
        repository.setPeriod(alts, SparkPeriod.DAYS_7)
        repository.addItems(main, listOf(key("binance:BTC/EUR"), key("kraken:ETH/EUR")))
        repository.setAccentColor(repository.observeItems(main).first().first().id, 0xFFFFBF66L)

        val backup = repository.exportBackup(exportedAt = 42L)

        assertEquals(WatchlistBackup.FORMAT, backup.format)
        assertEquals(42L, backup.exportedAt)
        assertEquals(listOf("Main", "Alts"), backup.watchlists.map { it.name })
        assertEquals("7d", backup.watchlists[1].period)
        assertEquals(
            listOf("binance:BTC/EUR", "kraken:ETH/EUR"),
            backup.watchlists[0].items.map { it.key },
        )
        assertEquals("#FFFFBF66", backup.watchlists[0].items[0].accentColor)
        assertNull(backup.watchlists[0].items[1].accentColor)
    }

    @Test
    fun `importBackup REPLACE swaps every list`() = runTest {
        val main = repository.ensureDefault()
        repository.addItems(main, listOf(key("binance:OLD/EUR")))

        val result = repository.importBackup(backupOf(entry("Fresh", "binance:BTC/EUR", "kraken:ETH/EUR")), ImportMode.REPLACE)

        assertEquals(1, result.watchlistsAdded)
        assertEquals(2, result.itemsAdded)
        assertEquals(0, result.itemsSkipped)
        val lists = repository.observeWatchlists().first()
        assertEquals(listOf("Fresh"), lists.map { it.name })
        assertEquals(
            listOf("binance:BTC/EUR", "kraken:ETH/EUR"),
            repository.observeItems(lists.single().id).first().map { it.key.value },
        )
    }

    @Test
    fun `importBackup REPLACE always leaves a default list behind`() = runTest {
        val main = repository.ensureDefault()
        repository.addItems(main, listOf(key("binance:OLD/EUR")))

        val result = repository.importBackup(backupOf(), ImportMode.REPLACE)

        assertEquals(0, result.watchlistsAdded)
        val lists = repository.observeWatchlists().first()
        assertEquals(listOf("Main"), lists.map { it.name })
        assertEquals(emptyList<String>(), repository.observeItems(lists.single().id).first().map { it.key.value })
    }

    @Test
    fun `importBackup REPLACE counts lists past the cap`() = runTest {
        repository.ensureDefault()
        val entries = (0 until Limits.MAX_WATCHLISTS + 3).map { entry("List $it", "binance:BTC/EUR") }

        val result = repository.importBackup(backupOf(*entries.toTypedArray()), ImportMode.REPLACE)

        assertEquals(Limits.MAX_WATCHLISTS, result.watchlistsAdded)
        assertEquals(3, result.watchlistsSkipped)
        assertEquals(Limits.MAX_WATCHLISTS, repository.observeWatchlists().first().size)
    }

    @Test
    fun `importBackup MERGE adds missing tickers to a list with the same name`() = runTest {
        val main = repository.ensureDefault()
        repository.addItems(main, listOf(key("binance:BTC/EUR")))

        val result = repository.importBackup(
            backupOf(
                entry("  main  ", "binance:BTC/EUR", "kraken:ETH/EUR"),
                entry("Alts", "binance:SOL/EUR"),
            ),
            ImportMode.MERGE,
        )

        assertEquals(1, result.watchlistsMerged)
        assertEquals(1, result.watchlistsAdded)
        assertEquals(2, result.itemsAdded)
        assertEquals(1, result.itemsSkipped)
        assertEquals(
            listOf("binance:BTC/EUR", "kraken:ETH/EUR"),
            repository.observeItems(main).first().map { it.key.value },
        )
        val alts = repository.observeWatchlists().first().last()
        assertEquals("Alts", alts.name)
        assertEquals(1, alts.position)
    }

    @Test
    fun `importBackup skips invalid keys and duplicates inside one list`() = runTest {
        repository.ensureDefault()

        val result = repository.importBackup(
            backupOf(
                WatchlistBackupEntry(
                    name = "  ",
                    items = listOf(
                        WatchlistBackupItem("binance:BTC/EUR", "#FFFFBF66"),
                        WatchlistBackupItem("binance:BTC/EUR"),
                        WatchlistBackupItem("foo:BAR/EUR"),
                        WatchlistBackupItem("binance:BTCEUR"),
                    ),
                ),
            ),
            ImportMode.REPLACE,
        )

        assertEquals(1, result.itemsAdded)
        assertEquals(3, result.itemsSkipped)
        val list = repository.observeWatchlists().first().single()
        assertEquals("Imported", list.name)
        val items = repository.observeItems(list.id).first()
        assertEquals(listOf(0xFFFFBF66L), items.map { it.accentColor })
    }

    /**
     * Rows written by an older build whose exchange has since been dropped still sit in the
     * database. They must disappear from the models instead of crashing the flow, which also lets
     * `CacheMaintenance` prune their candles and snapshots (they are no longer in the key set).
     */
    @Test
    fun `items of an unsupported exchange are skipped instead of crashing`() = runTest {
        val id = repository.ensureDefault()
        repository.addItems(id, listOf(key("binance:BTC/EUR")))
        daos.items.insert(WatchlistItemEntity(watchlistId = id, marketKey = "coinbase:BTC/USD", position = 1))

        assertEquals(listOf("binance:BTC/EUR"), repository.observeItems(id).first().map { it.key.value })
        assertEquals(setOf(key("binance:BTC/EUR")), repository.observeAllKeys().first())
    }

    @Test
    fun `importBackup falls back to defaults for unknown enum ids`() = runTest {
        repository.ensureDefault()

        repository.importBackup(
            backupOf(WatchlistBackupEntry(name = "Weird", period = "9y", tileSize = "huge", sort = "chaos")),
            ImportMode.REPLACE,
        )

        val list = repository.observeWatchlists().first().single()
        assertEquals(SparkPeriod.HOURS_24, list.period)
        assertEquals(TileSize.SMALL, list.tileSize)
        assertEquals(SortMode.CUSTOM, list.sort)
    }

    private fun key(value: String) = MarketKey(value)

    private fun entry(name: String, vararg keys: String) =
        WatchlistBackupEntry(name = name, items = keys.map { WatchlistBackupItem(it) })

    private fun backupOf(vararg entries: WatchlistBackupEntry) =
        WatchlistBackup(exportedAt = 0L, watchlists = entries.toList())
}
