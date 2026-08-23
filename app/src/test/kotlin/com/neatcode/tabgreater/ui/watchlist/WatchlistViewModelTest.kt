package com.neatcode.tabgreater.ui.watchlist

import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.SortMode
import com.neatcode.tabgreater.core.model.SparkPeriod
import com.neatcode.tabgreater.core.model.Ticker
import com.neatcode.tabgreater.ui.testing.FakeAppSettings
import com.neatcode.tabgreater.ui.testing.FakeMarketDataRepository
import com.neatcode.tabgreater.ui.testing.FakeMarketRepository
import com.neatcode.tabgreater.ui.testing.FakeSparklineRepository
import com.neatcode.tabgreater.ui.testing.FakeWatchlistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * View-model behaviour that the screen depends on: per-key sparkline subscriptions, selection
 * mode, drag commits and the settings that reach the tiles.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchlistViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val watchlists = FakeWatchlistRepository()
    private val markets = FakeMarketRepository()
    private val sparklines = FakeSparklineRepository()
    private val live = FakeMarketDataRepository()
    private var settings = FakeAppSettings()

    private val btc = MarketKey("binance:BTC/USDT")
    private val eth = MarketKey("binance:ETH/USDT")
    private val sol = MarketKey("kraken:SOL/EUR")

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `adding a ticker subscribes only the new sparkline`() = runTest(dispatcher) {
        val listId = watchlists.ensureDefault()
        watchlists.addItems(listId, listOf(btc, eth))
        val vm = viewModel()
        collectState(vm)
        advanceUntilIdle()

        assertEquals(listOf(btc, eth), sparklines.starts.map { it.first })

        watchlists.addItems(listId, listOf(sol))
        advanceUntilIdle()

        assertEquals(listOf(btc, eth, sol), sparklines.starts.map { it.first })
        assertEquals(1, sparklines.startsFor(btc))
        assertEquals(1, sparklines.startsFor(eth))
    }

    @Test
    fun `removing a ticker cancels only its sparkline`() = runTest(dispatcher) {
        val listId = watchlists.ensureDefault()
        watchlists.addItems(listId, listOf(btc, eth))
        val vm = viewModel()
        collectState(vm)
        advanceUntilIdle()

        val ethItem = watchlists.items(listId).first { it.key == eth }
        watchlists.removeItems(setOf(ethItem.id))
        advanceUntilIdle()

        assertEquals(setOf(btc), sparklines.active.toSet())
        assertEquals(1, sparklines.startsFor(btc))
    }

    @Test
    fun `changing the period restarts every sparkline`() = runTest(dispatcher) {
        val listId = watchlists.ensureDefault()
        watchlists.addItems(listId, listOf(btc, eth))
        val vm = viewModel()
        collectState(vm)
        advanceUntilIdle()

        vm.setPeriod(SparkPeriod.DAYS_7)
        advanceUntilIdle()

        assertEquals(2, sparklines.startsFor(btc))
        assertEquals(2, sparklines.startsFor(eth))
        assertEquals(
            listOf(SparkPeriod.DAYS_7, SparkPeriod.DAYS_7),
            sparklines.starts.takeLast(2).map { it.second },
        )
        assertEquals(SparkPeriod.DAYS_7, vm.uiState.value.period)
    }

    @Test
    fun `selection toggles, deletes and leaves selection mode`() = runTest(dispatcher) {
        val listId = watchlists.ensureDefault()
        watchlists.addItems(listId, listOf(btc, eth))
        val vm = viewModel()
        collectState(vm)
        advanceUntilIdle()

        val ids = watchlists.items(listId).map { it.id }
        vm.toggleSelection(ids[0])
        vm.toggleSelection(ids[1])
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isSelecting)
        assertEquals(ids.toSet(), vm.uiState.value.selectedIds)

        vm.toggleSelection(ids[1])
        advanceUntilIdle()
        assertEquals(setOf(ids[0]), vm.uiState.value.selectedIds)

        vm.deleteSelected()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isSelecting)
        assertEquals(listOf(eth), watchlists.items(listId).map { it.key })
        assertEquals(listOf(eth), vm.uiState.value.tiles.map { it.key })
    }

    @Test
    fun `switching watchlist clears the selection`() = runTest(dispatcher) {
        val first = watchlists.ensureDefault()
        val second = watchlists.createWatchlist("Second")
        watchlists.addItems(first, listOf(btc))
        val vm = viewModel()
        collectState(vm)
        advanceUntilIdle()

        vm.toggleSelection(watchlists.items(first).first().id)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isSelecting)

        vm.selectWatchlist(second)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isSelecting)
        assertEquals(second, vm.uiState.value.selectedId)
    }

    @Test
    fun `colouring the selection applies to every ticked item`() = runTest(dispatcher) {
        val listId = watchlists.ensureDefault()
        watchlists.addItems(listId, listOf(btc, eth))
        val vm = viewModel()
        collectState(vm)
        advanceUntilIdle()

        watchlists.items(listId).forEach { vm.toggleSelection(it.id) }
        advanceUntilIdle()
        vm.colourSelected(0xFF51C872L)
        advanceUntilIdle()

        assertEquals(listOf(0xFF51C872L, 0xFF51C872L), watchlists.items(listId).map { it.accentColor })
        assertFalse(vm.uiState.value.isSelecting)
    }

    @Test
    fun `moving the selection to another watchlist empties the source`() = runTest(dispatcher) {
        val first = watchlists.ensureDefault()
        val second = watchlists.createWatchlist("Second")
        watchlists.addItems(first, listOf(btc, eth))
        val vm = viewModel()
        collectState(vm)
        advanceUntilIdle()

        vm.toggleSelection(watchlists.items(first).first { it.key == btc }.id)
        advanceUntilIdle()
        vm.moveSelectedTo(second)
        advanceUntilIdle()

        assertEquals(listOf(eth), watchlists.items(first).map { it.key })
        assertEquals(listOf(btc), watchlists.items(second).map { it.key })
        assertFalse(vm.uiState.value.isSelecting)
    }

    @Test
    fun `dropping a dragged tile persists the order and switches to Custom`() = runTest(dispatcher) {
        val listId = watchlists.ensureDefault()
        watchlists.addItems(listId, listOf(btc, eth, sol))
        watchlists.setSort(listId, SortMode.PAIR_EXCHANGE)
        val vm = viewModel()
        collectState(vm)
        advanceUntilIdle()

        val displayed = vm.uiState.value.tiles.map { it.itemId }
        vm.moveTile(fromIndex = 0, toIndex = 2)
        advanceUntilIdle()

        val expected = moveItem(displayed, 0, 2)
        // The optimistic order shows straight away, and the chip already reads "Custom".
        assertEquals(expected, vm.uiState.value.tiles.map { it.itemId })
        assertEquals(SortMode.CUSTOM, vm.uiState.value.sort)

        vm.commitOrder()
        advanceUntilIdle()

        assertEquals(SortMode.CUSTOM, watchlists.watchlist(listId)?.sort)
        assertEquals(expected, watchlists.items(listId).map { it.id })
        assertEquals(expected, vm.uiState.value.tiles.map { it.itemId })
    }

    @Test
    fun `shrink zeros comes from the app settings`() = runTest(dispatcher) {
        settings = FakeAppSettings(shrinkZeros = false)
        val listId = watchlists.ensureDefault()
        watchlists.addItems(listId, listOf(btc))
        val vm = viewModel()
        collectState(vm)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.shrinkZeros)

        settings.setShrinkZeros(true)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.shrinkZeros)
    }

    @Test
    fun `the refresh rate caps how often the tiles redraw`() = runTest(dispatcher) {
        val listId = watchlists.ensureDefault()
        watchlists.addItems(listId, listOf(btc))
        val vm = viewModel()
        val prices = ArrayList<String?>()
        backgroundScope.launch { vm.uiState.collect { prices += it.tiles.firstOrNull()?.priceText } }
        advanceUntilIdle()

        // The first quote is never held back: an empty tile is worse than a slightly stale one.
        live.tickers.value = mapOf(btc to ticker(100.0))
        advanceUntilIdle()
        assertEquals("100.00", vm.uiState.value.tiles.single().priceText)

        // That first quote opens a 5 s window; three more ticks inside it collapse into one redraw.
        live.tickers.value = mapOf(btc to ticker(101.0))
        runCurrent()
        val redrawsBefore = prices.size
        live.tickers.value = mapOf(btc to ticker(102.0))
        advanceTimeBy(300)
        live.tickers.value = mapOf(btc to ticker(103.0))
        advanceTimeBy(300)
        live.tickers.value = mapOf(btc to ticker(104.0))
        advanceTimeBy(400)
        assertEquals("nothing may reach the grid inside the window", redrawsBefore, prices.size)

        advanceUntilIdle()
        assertEquals("104.00", vm.uiState.value.tiles.single().priceText)
        assertEquals(redrawsBefore + 1, prices.size)
    }

    @Test
    fun `the fastest rate lets every tick through`() = runTest(dispatcher) {
        settings = FakeAppSettings(watchlistRefreshMs = 1_000L)
        val listId = watchlists.ensureDefault()
        watchlists.addItems(listId, listOf(btc))
        val vm = viewModel()
        collectState(vm)
        advanceUntilIdle()

        live.tickers.value = mapOf(btc to ticker(100.0))
        advanceUntilIdle()
        assertEquals("100.00", vm.uiState.value.tiles.single().priceText)

        live.tickers.value = mapOf(btc to ticker(101.0))
        advanceTimeBy(1_100)
        assertEquals("101.00", vm.uiState.value.tiles.single().priceText)
    }

    @Test
    fun `switching watchlist mid-window paints the new tiles with their mini-charts`() = runTest(dispatcher) {
        val first = watchlists.ensureDefault()
        val second = watchlists.createWatchlist("Second")
        watchlists.addItems(first, listOf(btc))
        watchlists.addItems(second, listOf(sol))
        sparklines.setPoints(btc, 1f, 2f)
        sparklines.setPoints(sol, 3f, 4f)
        live.tickers.value = mapOf(btc to ticker(100.0), sol to ticker(20.0, sol))
        val vm = viewModel()
        collectState(vm)
        advanceUntilIdle()
        assertEquals(listOf(btc), vm.uiState.value.tiles.map { it.key })

        // A candle update opens a fresh 5 s window on the sparkline stream; the tab is switched
        // 1 s into it, which is where the throttle used to hold the new watchlist's mini-charts.
        sparklines.setPoints(btc, 1f, 3f)
        advanceTimeBy(1_000)
        vm.selectWatchlist(second)
        runCurrent()

        val tile = vm.uiState.value.tiles.single()
        assertEquals(sol, tile.key)
        assertEquals("20.00", tile.priceText)
        assertNotNull("the mini-chart must arrive with the price, not a window later", tile.spark)
    }

    private fun ticker(last: Double, key: MarketKey = btc) =
        Ticker(key = key, last = last, changePct24h = 1.0, timestamp = 0L)

    private fun viewModel() = WatchlistViewModel(
        watchlistRepository = watchlists,
        marketRepository = markets,
        sparklineRepository = sparklines,
        marketDataRepository = live,
        settingsStore = settings,
    )

    /** `uiState` is `WhileSubscribed`, so every test needs a collector that outlives the body. */
    private fun TestScope.collectState(vm: WatchlistViewModel) {
        backgroundScope.launch { vm.uiState.collect { } }
    }
}
