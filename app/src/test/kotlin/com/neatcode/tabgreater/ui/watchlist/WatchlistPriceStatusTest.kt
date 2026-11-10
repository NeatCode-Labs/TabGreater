package com.neatcode.tabgreater.ui.watchlist

import com.neatcode.tabgreater.core.exchange.ExchangeFailureKind
import com.neatcode.tabgreater.core.live.MarketDataRepository
import com.neatcode.tabgreater.core.live.MarketState
import com.neatcode.tabgreater.core.live.PriceFreshness
import com.neatcode.tabgreater.core.live.RefreshResult
import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.Ticker
import com.neatcode.tabgreater.ui.testing.FakeAppSettings
import com.neatcode.tabgreater.ui.testing.FakeMarketDataRepository
import com.neatcode.tabgreater.ui.testing.FakeMarketRepository
import com.neatcode.tabgreater.ui.testing.FakeSparklineRepository
import com.neatcode.tabgreater.ui.testing.FakeWatchlistRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WatchlistPriceStatusTest {

    private val dispatcher = StandardTestDispatcher()
    private val watchlists = FakeWatchlistRepository()
    private val markets = FakeMarketRepository()
    private val sparklines = FakeSparklineRepository()
    private val live = StatusMarketDataRepository()
    private val settings = FakeAppSettings()

    private val btc = MarketKey("binance:BTC/USDT")
    private val eth = MarketKey("binance:ETH/USDT")

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `old prices refresh to current after same-price confirmation`() = runTest(dispatcher) {
        val listId = watchlists.ensureDefault()
        watchlists.addItems(listId, listOf(btc))
        live.states.value = mapOf(btc to marketState(btc, 100.0, PriceFreshness.OLD))

        val vm = viewModel()
        collectState(vm)
        vm.onForeground()
        runCurrent()

        assertEquals(PriceRefreshStatus.REFRESHING, vm.uiState.value.priceRefreshStatus)
        assertTrue("foreground recovery uses the existing observation", live.refreshRequests.isEmpty())

        // Confirmation refreshes age/freshness even though the displayed price is unchanged.
        live.states.value = mapOf(btc to marketState(btc, 100.0, PriceFreshness.CURRENT))
        runCurrent()

        assertEquals(PriceRefreshStatus.CURRENT, vm.uiState.value.priceRefreshStatus)
        assertTrue(vm.uiState.value.priceIssues.isEmpty())
    }

    @Test
    fun `unconfirmed prices become stale when foreground grace expires`() = runTest(dispatcher) {
        val listId = watchlists.ensureDefault()
        watchlists.addItems(listId, listOf(btc))
        live.states.value = mapOf(btc to marketState(btc, 100.0, PriceFreshness.OLD))

        val vm = viewModel()
        collectState(vm)
        vm.onForeground()
        runCurrent()

        assertEquals(PriceRefreshStatus.REFRESHING, vm.uiState.value.priceRefreshStatus)
        advanceTimeBy(4_999)
        runCurrent()
        assertEquals(PriceRefreshStatus.REFRESHING, vm.uiState.value.priceRefreshStatus)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(PriceRefreshStatus.STALE, vm.uiState.value.priceRefreshStatus)
        assertEquals(PriceIssueKind.OLD, vm.uiState.value.priceIssues.single().kind)
    }

    @Test
    fun `known market failure is error immediately during grace`() = runTest(dispatcher) {
        val listId = watchlists.ensureDefault()
        watchlists.addItems(listId, listOf(btc))
        live.states.value = mapOf(
            btc to marketState(btc, 100.0, PriceFreshness.CURRENT, ExchangeFailureKind.TRANSIENT),
        )

        val vm = viewModel()
        collectState(vm)
        vm.onForeground()
        runCurrent()

        assertEquals(PriceRefreshStatus.ERROR, vm.uiState.value.priceRefreshStatus)
        assertEquals(PriceIssueKind.ERROR, vm.uiState.value.priceIssues.single().kind)
    }

    @Test
    fun `mixed current and absent state reports only the unconfirmed market`() = runTest(dispatcher) {
        val listId = watchlists.ensureDefault()
        watchlists.addItems(listId, listOf(btc, eth))
        live.states.value = mapOf(btc to marketState(btc, 100.0, PriceFreshness.CURRENT))

        val vm = viewModel()
        collectState(vm)
        vm.onForeground()
        runCurrent()

        assertEquals(PriceRefreshStatus.REFRESHING, vm.uiState.value.priceRefreshStatus)
        assertEquals(listOf(eth), vm.uiState.value.priceIssues.map { it.key })
        assertEquals(PriceIssueKind.UNCONFIRMED, vm.uiState.value.priceIssues.single().kind)

        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(PriceRefreshStatus.STALE, vm.uiState.value.priceRefreshStatus)
        assertEquals(listOf(eth), vm.uiState.value.priceIssues.map { it.key })
    }

    @Test
    fun `empty watchlist remains empty without a foreground spinner`() = runTest(dispatcher) {
        watchlists.ensureDefault()
        val vm = viewModel()
        collectState(vm)
        vm.onForeground()
        runCurrent()

        assertEquals(PriceRefreshStatus.EMPTY, vm.uiState.value.priceRefreshStatus)
        assertTrue(vm.uiState.value.priceIssues.isEmpty())
        assertFalse(vm.uiState.value.refreshInProgress)
    }

    @Test
    fun `returning to foreground starts a fresh grace period`() = runTest(dispatcher) {
        val listId = watchlists.ensureDefault()
        watchlists.addItems(listId, listOf(btc))
        live.states.value = mapOf(btc to marketState(btc, 100.0, PriceFreshness.OLD))

        val vm = viewModel()
        collectState(vm)
        vm.onForeground()
        runCurrent()
        advanceTimeBy(4_000)
        runCurrent()

        vm.onBackground()
        runCurrent()
        vm.onForeground()
        runCurrent()
        assertEquals(PriceRefreshStatus.REFRESHING, vm.uiState.value.priceRefreshStatus)

        advanceTimeBy(4_999)
        runCurrent()
        assertEquals(PriceRefreshStatus.REFRESHING, vm.uiState.value.priceRefreshStatus)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(PriceRefreshStatus.STALE, vm.uiState.value.priceRefreshStatus)
    }

    @Test
    fun `watchlist change resets the grace period for its keys`() = runTest(dispatcher) {
        val first = watchlists.ensureDefault()
        val second = watchlists.createWatchlist("Second")
        watchlists.addItems(first, listOf(btc))
        watchlists.addItems(second, listOf(eth))
        live.states.value = mapOf(btc to marketState(btc, 100.0, PriceFreshness.OLD))

        val vm = viewModel()
        collectState(vm)
        vm.onForeground()
        runCurrent()
        advanceTimeBy(4_000)
        vm.selectWatchlist(second)
        runCurrent()

        assertEquals(PriceRefreshStatus.REFRESHING, vm.uiState.value.priceRefreshStatus)
        assertEquals(listOf(eth), vm.uiState.value.priceIssues.map { it.key })
        advanceTimeBy(4_999)
        runCurrent()
        assertEquals(PriceRefreshStatus.REFRESHING, vm.uiState.value.priceRefreshStatus)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(PriceRefreshStatus.STALE, vm.uiState.value.priceRefreshStatus)
    }

    @Test
    fun `retry is single flight and clears progress after result`() = runTest(dispatcher) {
        val listId = watchlists.ensureDefault()
        watchlists.addItems(listId, listOf(btc))
        live.states.value = mapOf(btc to marketState(btc, 100.0, PriceFreshness.OLD))
        val result = CompletableDeferred<RefreshResult>()
        live.refreshResultHandler = { result.await() }

        val vm = viewModel()
        collectState(vm)
        vm.onForeground()
        runCurrent()
        vm.refresh()
        vm.refresh()
        runCurrent()

        assertEquals(1, live.refreshRequests.size)
        assertTrue(vm.uiState.value.refreshInProgress)
        assertEquals(PriceRefreshStatus.REFRESHING, vm.uiState.value.priceRefreshStatus)

        live.states.value = mapOf(btc to marketState(btc, 100.0, PriceFreshness.CURRENT))
        result.complete(RefreshResult(successful = setOf(btc)))
        runCurrent()

        assertFalse(vm.uiState.value.refreshInProgress)
        assertEquals(PriceRefreshStatus.CURRENT, vm.uiState.value.priceRefreshStatus)
    }

    @Test
    fun `refresh result failures and thrown exceptions remain visible after cleanup`() = runTest(dispatcher) {
        val listId = watchlists.ensureDefault()
        watchlists.addItems(listId, listOf(btc))
        val vm = viewModel()
        collectState(vm)
        vm.onForeground()
        runCurrent()

        live.refreshResultHandler = { keys ->
            RefreshResult(failures = mapOf(keys.single() to IllegalStateException("REST failed")))
        }
        vm.refresh()
        runCurrent()
        assertFalse(vm.uiState.value.refreshInProgress)
        assertEquals(PriceRefreshStatus.ERROR, vm.uiState.value.priceRefreshStatus)
        assertEquals(PriceIssueKind.ERROR, vm.uiState.value.priceIssues.single().kind)

        live.refreshResultHandler = { keys -> RefreshResult(missing = keys.toSet()) }
        vm.refresh()
        runCurrent()
        assertFalse(vm.uiState.value.refreshInProgress)
        assertEquals(PriceRefreshStatus.ERROR, vm.uiState.value.priceRefreshStatus)
        assertEquals(PriceIssueKind.ERROR, vm.uiState.value.priceIssues.single().kind)

        live.refreshResultHandler = { throw IllegalStateException("refresh threw") }
        vm.refresh()
        runCurrent()
        assertFalse(vm.uiState.value.refreshInProgress)
        assertEquals(PriceRefreshStatus.ERROR, vm.uiState.value.priceRefreshStatus)
        assertEquals(PriceIssueKind.ERROR, vm.uiState.value.priceIssues.single().kind)
    }

    @Test
    fun `watchlist switch cancels an in-flight retry and clears progress`() = runTest(dispatcher) {
        val first = watchlists.ensureDefault()
        val second = watchlists.createWatchlist("Second")
        watchlists.addItems(first, listOf(btc))
        watchlists.addItems(second, listOf(eth))
        val pending = CompletableDeferred<RefreshResult>()
        live.refreshResultHandler = { pending.await() }

        val vm = viewModel()
        collectState(vm)
        vm.onForeground()
        runCurrent()
        vm.refresh()
        runCurrent()
        assertTrue(vm.uiState.value.refreshInProgress)

        vm.selectWatchlist(second)
        runCurrent()

        assertFalse(vm.uiState.value.refreshInProgress)
        assertEquals(listOf(eth), vm.uiState.value.tiles.map { it.key })
    }

    @Test
    fun `failed retry clears immediately on a new confirmation at the same price`() = runTest(dispatcher) {
        val id = watchlists.ensureDefault()
        watchlists.addItems(id, listOf(btc))
        val confirmed = marketState(btc, 100.0, PriceFreshness.CURRENT)
        live.states.value = mapOf(btc to confirmed)
        live.refreshResultHandler = { RefreshResult(failures = mapOf(btc to IllegalStateException("failed"))) }
        val vm = viewModel()
        collectState(vm)
        vm.onForeground()
        runCurrent()
        vm.refresh()
        runCurrent()
        assertEquals(PriceRefreshStatus.ERROR, vm.uiState.value.priceRefreshStatus)
        live.states.value = mapOf(btc to confirmed.copy(confirmedAtElapsedMs = 1))
        runCurrent()
        assertEquals(PriceRefreshStatus.CURRENT, vm.uiState.value.priceRefreshStatus)
        assertTrue(vm.uiState.value.priceIssues.isEmpty())
    }

    @Test
    fun `switching watchlists with identical keys also starts a bounded grace period`() = runTest(dispatcher) {
        val first = watchlists.ensureDefault()
        val second = watchlists.createWatchlist("Same pairs")
        watchlists.addItems(first, listOf(btc))
        watchlists.addItems(second, listOf(btc))
        live.states.value = mapOf(btc to marketState(btc, 100.0, PriceFreshness.OLD))
        val vm = viewModel()
        collectState(vm)
        vm.onForeground()
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(PriceRefreshStatus.STALE, vm.uiState.value.priceRefreshStatus)
        vm.selectWatchlist(second)
        runCurrent()
        assertEquals(PriceRefreshStatus.REFRESHING, vm.uiState.value.priceRefreshStatus)
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(PriceRefreshStatus.STALE, vm.uiState.value.priceRefreshStatus)
    }

    @Test
    fun `slow retry stops showing a spinner at the deadline but remains single flight`() = runTest(dispatcher) {
        val id = watchlists.ensureDefault()
        watchlists.addItems(id, listOf(btc))
        val pending = CompletableDeferred<RefreshResult>()
        live.refreshResultHandler = { pending.await() }
        val vm = viewModel()
        collectState(vm)
        vm.onForeground()
        runCurrent()
        vm.refresh()
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(PriceRefreshStatus.STALE, vm.uiState.value.priceRefreshStatus)
        assertTrue(vm.uiState.value.refreshInProgress)
        vm.refresh()
        runCurrent()
        assertEquals(1, live.refreshRequests.size)
        pending.complete(RefreshResult(missing = setOf(btc)))
        runCurrent()
        assertEquals(PriceRefreshStatus.ERROR, vm.uiState.value.priceRefreshStatus)
        assertFalse(vm.uiState.value.refreshInProgress)
    }

    @Test
    fun `cancelled retry cannot clear or fail a replacement retry`() = runTest(dispatcher) {
        val first = watchlists.ensureDefault()
        val second = watchlists.createWatchlist("Same pair")
        watchlists.addItems(first, listOf(btc))
        watchlists.addItems(second, listOf(btc))
        live.states.value = mapOf(btc to marketState(btc, 100.0, PriceFreshness.CURRENT))

        val cancelledResult = CompletableDeferred<RefreshResult>()
        val replacementResult = CompletableDeferred<RefreshResult>()
        live.refreshResultHandler = {
            when (live.refreshRequests.size) {
                1 -> withContext(NonCancellable) { cancelledResult.await() }
                else -> replacementResult.await()
            }
        }

        val vm = viewModel()
        collectState(vm)
        vm.onForeground()
        runCurrent()

        vm.refresh()
        runCurrent()
        assertTrue(vm.uiState.value.refreshInProgress)

        vm.selectWatchlist(second)
        runCurrent()
        assertEquals(second, vm.uiState.value.selectedId)

        // Hold the cancelled request past the watchlist switch so its finalizer competes with a
        // replacement request for the same market key.
        vm.refresh()
        runCurrent()
        assertEquals(2, live.refreshRequests.size)
        assertTrue(vm.uiState.value.refreshInProgress)

        cancelledResult.complete(RefreshResult(failures = mapOf(btc to IllegalStateException("late failure"))))
        runCurrent()

        assertTrue("the old finally must not clear the replacement request", vm.uiState.value.refreshInProgress)
        assertEquals("the cancelled result must not be recorded against the replacement", PriceRefreshStatus.REFRESHING, vm.uiState.value.priceRefreshStatus)
        assertTrue(vm.uiState.value.priceIssues.isEmpty())

        replacementResult.complete(RefreshResult(successful = setOf(btc)))
        runCurrent()
        assertFalse(vm.uiState.value.refreshInProgress)
        assertEquals(PriceRefreshStatus.CURRENT, vm.uiState.value.priceRefreshStatus)
    }

    private fun marketState(
        key: MarketKey,
        price: Double,
        freshness: PriceFreshness,
        failure: ExchangeFailureKind? = null,
    ) = MarketState(
        ticker = Ticker(key = key, last = price, changePct24h = 1.0, timestamp = 0L),
        confirmedAtElapsedMs = 0L.takeIf { freshness == PriceFreshness.CURRENT || freshness == PriceFreshness.OLD },
        confirmedAtEpochMs = 1_000L.takeIf { freshness == PriceFreshness.CURRENT || freshness == PriceFreshness.OLD },
        freshness = freshness,
        failure = failure,
    )

    private fun viewModel() = WatchlistViewModel(
        watchlistRepository = watchlists,
        marketRepository = markets,
        sparklineRepository = sparklines,
        marketDataRepository = live,
        settingsStore = settings,
    )

    private fun TestScope.collectState(vm: WatchlistViewModel) {
        backgroundScope.launch { vm.uiState.collect { } }
    }

    /** Keeps tile repositories fake while providing deterministic confirmed-price transitions. */
    private class StatusMarketDataRepository : MarketDataRepository by FakeMarketDataRepository() {
        val states = MutableStateFlow<Map<MarketKey, MarketState>>(emptyMap())
        val refreshRequests = mutableListOf<List<MarketKey>>()
        var refreshResultHandler: suspend (List<MarketKey>) -> RefreshResult = { keys ->
            RefreshResult(successful = keys.toSet())
        }

        override fun observeMarketState(keys: Set<MarketKey>): Flow<Map<MarketKey, MarketState>> =
            states.map { current -> keys.associateWith { key -> current[key] ?: MarketState() } }

        override fun currentState(key: MarketKey): MarketState? = states.value[key]

        override suspend fun refresh(keys: Collection<MarketKey>) = Unit

        override suspend fun refreshResult(keys: Collection<MarketKey>): RefreshResult {
            val requested = keys.toList()
            refreshRequests += requested
            return refreshResultHandler(requested)
        }
    }
}
