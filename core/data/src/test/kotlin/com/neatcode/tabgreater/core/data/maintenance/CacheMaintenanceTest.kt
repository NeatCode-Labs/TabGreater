package com.neatcode.tabgreater.core.data.maintenance

import com.neatcode.tabgreater.core.data.repo.FakeCandleDao
import com.neatcode.tabgreater.core.data.repo.FakeTickerSnapshotDao
import com.neatcode.tabgreater.core.data.repo.FakeWatchlistDaos
import com.neatcode.tabgreater.core.data.repo.RoomWatchlistRepository
import com.neatcode.tabgreater.core.data.repo.candleEntity
import com.neatcode.tabgreater.core.data.repo.snapshotEntity
import com.neatcode.tabgreater.core.model.MarketKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CacheMaintenanceTest {

    private val daos = FakeWatchlistDaos()
    private val repository = RoomWatchlistRepository(daos.watchlists, daos.items)
    private val candleDao = FakeCandleDao()
    private val snapshotDao = FakeTickerSnapshotDao()

    @Test
    fun `the first run drops caches nothing references any more`() = runTest {
        val main = repository.ensureDefault()
        repository.addItems(main, listOf(key(BTC)))
        seedCache(BTC, ORPHAN)

        maintenance(backgroundScope).start()
        settle()

        assertEquals(listOf(BTC), candleDao.distinctKeys())
        assertEquals(listOf(BTC), snapshotDao.distinctKeys())
    }

    @Test
    fun `adding a ticker never deletes anything`() = runTest {
        val main = repository.ensureDefault()
        repository.addItems(main, listOf(key(BTC)))
        seedCache(BTC)

        maintenance(backgroundScope).start()
        settle()

        repository.addItems(main, listOf(key(ETH)))
        settle()

        assertEquals(listOf(BTC), candleDao.distinctKeys())
        assertEquals(listOf(BTC), snapshotDao.distinctKeys())
    }

    @Test
    fun `removing a ticker drops its candles and snapshot after the debounce`() = runTest {
        val main = repository.ensureDefault()
        repository.addItems(main, listOf(key(BTC), key(ETH)))
        seedCache(BTC, ETH)

        maintenance(backgroundScope).start()
        settle()
        assertEquals(listOf(BTC, ETH), candleDao.distinctKeys())

        val eth = repository.observeItems(main).first { it.isNotEmpty() }.first { it.key.value == ETH }
        repository.removeItems(listOf(eth.id))
        settle()

        assertEquals(listOf(BTC), candleDao.distinctKeys())
        assertEquals(listOf(BTC), snapshotDao.distinctKeys())
    }

    @Test
    fun `undo inside the debounce window deletes nothing`() = runTest {
        val main = repository.ensureDefault()
        repository.addItems(main, listOf(key(BTC), key(ETH)))
        seedCache(BTC, ETH)

        maintenance(backgroundScope).start()
        settle()

        val eth = repository.observeItems(main).first { it.isNotEmpty() }.first { it.key.value == ETH }
        repository.removeItems(listOf(eth.id))
        advanceTimeBy(DEBOUNCE / 3)
        repository.addItems(main, listOf(key(ETH)))
        settle()

        assertEquals(listOf(BTC, ETH), candleDao.distinctKeys())
        assertEquals(listOf(BTC, ETH), snapshotDao.distinctKeys())
    }

    @Test
    fun `an empty database is left alone until the first watchlist exists`() = runTest {
        seedCache(BTC)

        maintenance(backgroundScope).start()
        settle()

        assertEquals(listOf(BTC), candleDao.distinctKeys())
    }

    @Test
    fun `a watchlist without items prunes everything`() = runTest {
        repository.ensureDefault()
        seedCache(BTC, ETH)

        maintenance(backgroundScope).pruneOnce()

        assertEquals(emptyList<String>(), candleDao.distinctKeys())
        assertEquals(emptyList<String>(), snapshotDao.distinctKeys())
    }

    @Test
    fun `a widget-only pair survives the prune`() = runTest {
        val main = repository.ensureDefault()
        repository.addItems(main, listOf(key(BTC)))
        // WIDGET is on the home screen but in no watchlist. Pruning it deletes the candles its
        // sparkline is drawn from and the snapshot it renders from on a cold start — and nothing
        // in the widget path ever fetches them back.
        seedCache(BTC, WIDGET, ORPHAN)

        maintenance(backgroundScope, pinned(WIDGET)).start()
        settle()

        assertEquals(listOf(BTC, WIDGET), candleDao.distinctKeys().sorted())
        assertEquals(listOf(BTC, WIDGET), snapshotDao.distinctKeys().sorted())
    }

    @Test
    fun `pruneOnce unions the pinned keys too`() = runTest {
        repository.ensureDefault()
        seedCache(WIDGET, ORPHAN)

        maintenance(backgroundScope, pinned(WIDGET)).pruneOnce()

        assertEquals(listOf(WIDGET), candleDao.distinctKeys())
        assertEquals(listOf(WIDGET), snapshotDao.distinctKeys())
    }

    @Test
    fun `removing the widget lets its cache go`() = runTest {
        val main = repository.ensureDefault()
        repository.addItems(main, listOf(key(BTC)))
        seedCache(BTC, WIDGET)
        val widgets = MutableStateFlow(setOf(key(WIDGET)))

        maintenance(backgroundScope, widgets).start()
        settle()
        assertEquals(listOf(BTC, WIDGET), candleDao.distinctKeys().sorted())

        widgets.value = emptySet()
        settle()

        assertEquals(listOf(BTC), candleDao.distinctKeys())
    }

    private fun maintenance(
        scope: CoroutineScope,
        extraKeys: Flow<Set<MarketKey>> = flowOf(emptySet()),
    ) = CacheMaintenance(repository, candleDao, snapshotDao, scope, DEBOUNCE, extraKeys)

    private fun pinned(vararg keys: String): Flow<Set<MarketKey>> =
        flowOf(keys.mapTo(LinkedHashSet()) { key(it) })

    private suspend fun seedCache(vararg keys: String) {
        for (k in keys) {
            candleDao.upsertAll(listOf(candleEntity(k, openTime = 0L), candleEntity(k, openTime = 3_600_000L)))
            snapshotDao.upsert(snapshotEntity(k))
        }
    }

    private fun TestScope.settle() {
        advanceTimeBy(DEBOUNCE * 2)
        advanceUntilIdle()
    }

    private fun key(value: String) = MarketKey(value)

    private companion object {
        const val DEBOUNCE = 30_000L
        const val BTC = "binance:BTC/EUR"
        const val ETH = "binance:ETH/EUR"
        const val ORPHAN = "kraken:DOGE/EUR"

        /** On a home-screen widget, in no watchlist. */
        const val WIDGET = "kraken:ETH/EUR"
    }
}
