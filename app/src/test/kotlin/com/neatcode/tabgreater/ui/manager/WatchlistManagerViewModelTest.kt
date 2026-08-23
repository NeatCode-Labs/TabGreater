package com.neatcode.tabgreater.ui.manager

import com.neatcode.tabgreater.core.model.Limits
import com.neatcode.tabgreater.core.model.SortMode
import com.neatcode.tabgreater.core.model.SparkPeriod
import com.neatcode.tabgreater.core.model.TileSize
import com.neatcode.tabgreater.core.model.Watchlist
import com.neatcode.tabgreater.ui.testing.FakeAppSettings
import com.neatcode.tabgreater.ui.testing.FakeWatchlistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WatchlistManagerViewModelTest {

    private val repository = FakeWatchlistRepository()
    private val settings = FakeAppSettings()

    /** Unconfined everywhere: the view models' `stateIn` must be hot before every assertion. */
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `create trims the name and appends the list`() = managerTest { viewModel ->
        repository.seed("Main")

        viewModel.create("  Solana  ")

        assertEquals(listOf("Main", "Solana"), repository.watchlists.map { it.name })
    }

    @Test
    fun `create ignores a blank name`() = managerTest { viewModel ->
        repository.seed("Main")

        viewModel.create("   ")

        assertEquals(1, repository.watchlists.size)
    }

    @Test
    fun `create clamps the name to the persisted length`() = managerTest { viewModel ->
        repository.seed("Main")

        viewModel.create("x".repeat(80))

        assertEquals(Limits.MAX_WATCHLIST_NAME_LENGTH, repository.watchlists.last().name.length)
    }

    @Test
    fun `create at the cap reports the limit instead of adding`() = managerTest { viewModel ->
        repeat(Limits.MAX_WATCHLISTS) { index -> repository.seed("List $index") }

        viewModel.create("One too many")

        assertEquals(Limits.MAX_WATCHLISTS, repository.watchlists.size)
        assertEquals(ManagerMessageKind.LIMIT_REACHED, viewModel.uiState.value.message?.kind)
        assertEquals(false, viewModel.uiState.value.canAdd)
    }

    @Test
    fun `rename replaces the name`() = managerTest { viewModel ->
        val id = repository.seed("Main")

        viewModel.rename(id, "  Majors  ")

        assertEquals("Majors", repository.watchlists.single().name)
    }

    @Test
    fun `copy duplicates the list and its items under a copy name`() = managerTest { viewModel ->
        val id = repository.seed("Main", listOf("binance:BTC/EUR", "kraken:ETH/EUR"))

        viewModel.copy(id)

        val copy = repository.watchlists.last()
        assertEquals("Main copy", copy.name)
        assertEquals(
            listOf("binance:BTC/EUR", "kraken:ETH/EUR"),
            repository.itemsOf(copy.id).map { it.key.value },
        )
    }

    @Test
    fun `copy at the cap reports the limit`() = managerTest { viewModel ->
        val id = repository.seed("Main")
        repeat(Limits.MAX_WATCHLISTS - 1) { index -> repository.seed("List $index") }

        viewModel.copy(id)

        assertEquals(Limits.MAX_WATCHLISTS, repository.watchlists.size)
        assertEquals(ManagerMessageKind.LIMIT_REACHED, viewModel.uiState.value.message?.kind)
    }

    @Test
    fun `delete then undo restores name position and items`() = managerTest { viewModel ->
        repository.seed("First")
        val middle = repository.seed("Middle", listOf("binance:BTC/EUR", "kraken:ETH/EUR"))
        repository.seed("Last")

        viewModel.delete(middle)

        assertEquals(listOf("First", "Last"), repository.watchlists.map { it.name })
        assertEquals(ManagerMessageKind.DELETED, viewModel.uiState.value.message?.kind)
        assertTrue(viewModel.uiState.value.pendingUndo)

        viewModel.undoDelete()

        assertEquals(listOf("First", "Middle", "Last"), repository.watchlists.map { it.name })
        val restored = repository.watchlists[1]
        assertEquals(
            listOf("binance:BTC/EUR", "kraken:ETH/EUR"),
            repository.itemsOf(restored.id).map { it.key.value },
        )
        assertEquals(false, viewModel.uiState.value.pendingUndo)
    }

    @Test
    fun `the undo window closes after five seconds even with nobody watching`() = managerTest { viewModel ->
        repository.seed("First")
        val middle = repository.seed("Middle")
        repository.seed("Last")

        viewModel.delete(middle)
        advanceTimeBy(WatchlistManagerViewModel.UNDO_WINDOW_MS + 1)

        assertEquals(false, viewModel.uiState.value.pendingUndo)
        assertNull(viewModel.uiState.value.message)

        viewModel.undoDelete()

        assertEquals(listOf("First", "Last"), repository.watchlists.map { it.name })
    }

    @Test
    fun `dismissing the sheet drops the undo offer and its snackbar`() = managerTest { viewModel ->
        repository.seed("First")
        val middle = repository.seed("Middle")

        viewModel.delete(middle)
        viewModel.onSheetDismissed()

        assertNull(viewModel.uiState.value.message)
        assertEquals(false, viewModel.uiState.value.pendingUndo)

        // Reopening the sheet must not replay the snackbar, and a late UNDO restores nothing.
        advanceTimeBy(WatchlistManagerViewModel.UNDO_WINDOW_MS + 1)
        viewModel.undoDelete()

        assertNull(viewModel.uiState.value.message)
        assertEquals(listOf("First"), repository.watchlists.map { it.name })
    }

    @Test
    fun `a second delete inside the window finalises the first one`() = managerTest { viewModel ->
        val first = repository.seed("First")
        val second = repository.seed("Second")
        repository.seed("Third")

        viewModel.delete(first)
        advanceTimeBy(WatchlistManagerViewModel.UNDO_WINDOW_MS / 2)
        viewModel.delete(second)
        viewModel.undoDelete()

        assertEquals(listOf("Second", "Third"), repository.watchlists.map { it.name })
        assertEquals(false, viewModel.uiState.value.pendingUndo)
    }

    @Test
    fun `deleting the only watchlist is refused`() = managerTest { viewModel ->
        val id = repository.seed("Main")

        viewModel.delete(id)

        assertEquals(1, repository.watchlists.size)
        assertEquals(ManagerMessageKind.KEEP_AT_LEAST_ONE, viewModel.uiState.value.message?.kind)
    }

    @Test
    fun `reorder persists the dropped order`() = managerTest { viewModel ->
        val a = repository.seed("A")
        val b = repository.seed("B")
        val c = repository.seed("C")

        viewModel.reorder(listOf(c, a, b))

        assertEquals(listOf("C", "A", "B"), repository.watchlists.map { it.name })
        assertEquals(listOf("C", "A", "B"), viewModel.uiState.value.watchlists.map { it.name })
    }

    @Test
    fun `rows carry the item count, the settings summary and the selected tab`() = managerTest { viewModel ->
        val id = repository.seed(
            name = "Majors",
            keys = listOf("binance:BTC/EUR", "kraken:ETH/EUR"),
            period = SparkPeriod.DAYS_7,
            tileSize = TileSize.COMPACT,
            sort = SortMode.PRICE,
        )
        settings.setSelectedWatchlistId(id)

        val row = viewModel.uiState.value.watchlists.single()
        assertEquals("Majors", row.name)
        assertEquals("7 days · Compact · Price · 2/${Limits.MAX_ITEMS_PER_WATCHLIST}", row.subtitle)
        assertTrue(row.selected)
        assertEquals(1, viewModel.uiState.value.count)
    }

    @Test
    fun `consumeMessage clears only the message it was given`() = managerTest { viewModel ->
        val id = repository.seed("Main")

        viewModel.delete(id)
        val message = viewModel.uiState.value.message!!
        viewModel.consumeMessage(message.id + 1)
        assertEquals(message, viewModel.uiState.value.message)

        viewModel.consumeMessage(message.id)
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun `subtitle joins the per-list settings with the item count`() {
        val watchlist = Watchlist(1, "Main", 0, SparkPeriod.HOURS_24, TileSize.SMALL, SortMode.CUSTOM)

        assertEquals("24 hours · Small · Custom · 45/${Limits.MAX_ITEMS_PER_WATCHLIST}", subtitleOf(watchlist, 45))
    }

    @Test
    fun `copy name keeps the suffix when the original is already at the length limit`() {
        val name = "x".repeat(Limits.MAX_WATCHLIST_NAME_LENGTH)

        val copy = copyName(name)

        assertTrue(copy.endsWith(" copy"))
        assertTrue(copy.length <= Limits.MAX_WATCHLIST_NAME_LENGTH)
    }

    @Test
    fun `a stale drag order is ignored once the set of ids changed`() {
        val lists = listOf(watchlist(1, 0), watchlist(2, 1))

        assertEquals(listOf(2L, 1L), applyOrder(lists, listOf(2L, 1L)).map { it.id })
        assertEquals(listOf(1L, 2L), applyOrder(lists, listOf(3L, 4L)).map { it.id })
        assertEquals(listOf(1L, 2L), applyOrder(lists, listOf(2L)).map { it.id })
    }

    private fun watchlist(id: Long, position: Int) = Watchlist(id, "L$id", position)

    /** Runs [block] with the state flow collected, so `WhileSubscribed` keeps [uiState] fresh. */
    private fun managerTest(block: suspend TestScope.(WatchlistManagerViewModel) -> Unit) = runTest(dispatcher) {
        val viewModel = WatchlistManagerViewModel(repository, settings)
        backgroundScope.launch { viewModel.uiState.collect { } }
        block(viewModel)
    }
}
