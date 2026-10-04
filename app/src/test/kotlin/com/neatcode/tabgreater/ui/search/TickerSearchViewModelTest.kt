package com.neatcode.tabgreater.ui.search

import com.neatcode.tabgreater.core.data.popular.CachedPopularPairs
import com.neatcode.tabgreater.core.data.popular.DEFAULT_POPULAR_PAIRS
import com.neatcode.tabgreater.core.data.popular.DEFAULT_POPULAR_STOCKS
import com.neatcode.tabgreater.core.data.popular.PopularPairsCache
import com.neatcode.tabgreater.core.data.popular.PopularPairsRepository
import com.neatcode.tabgreater.core.model.AssetClass
import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.ui.testing.FakeMarketRepository
import com.neatcode.tabgreater.ui.testing.FakeWatchlistRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

/** The "+ Add pair" search: the Crypto | Stocks scope, its chips and the cross-scope hint. */
@OptIn(ExperimentalCoroutinesApi::class)
class TickerSearchViewModelTest {

    private val markets = FakeMarketRepository()
    private val watchlists = FakeWatchlistRepository()

    /** No cache and a failing source: the crypto chips stay on the built-in list, offline. */
    private val popularPairs = PopularPairsRepository(cache = EmptyCache, source = { null })

    /** Unconfined everywhere: the view model's `stateIn` must be hot before every assertion. */
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
    fun `the scope starts on Crypto and lists coins only`() = searchTest { viewModel ->
        seedCatalogue()

        type(viewModel, "USDT")

        val state = viewModel.uiState.value
        assertEquals(AssetClass.CRYPTO, state.scope)
        assertEquals(listOf("binance:BTC/USDT", "binance:ETH/USDT"), state.results.map { it.key.value })
    }

    @Test
    fun `switching the scope re-runs the query in that class at once`() = searchTest { viewModel ->
        seedCatalogue()
        type(viewModel, "USDT")

        viewModel.onScopeChange(AssetClass.STOCK)
        // No debounce for a tab tap: the new class is searched without waiting.
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(AssetClass.STOCK, state.scope)
        assertEquals(
            listOf("binance:TSLAB/USDT", "gate:TSLAX/USDT", "mexc:TSLAON/USDT", "kucoin:NVDAX/USDT"),
            state.results.map { it.key.value },
        )
        assertTrue(state.results.all { it.assetClass == AssetClass.STOCK })

        viewModel.onScopeChange(AssetClass.CRYPTO)
        runCurrent()

        assertEquals(
            listOf("binance:BTC/USDT", "binance:ETH/USDT"),
            viewModel.uiState.value.results.map { it.key.value },
        )
    }

    @Test
    fun `the selection survives a scope switch and one Add adds both classes`() = searchTest { viewModel ->
        seedCatalogue()
        type(viewModel, "USDT")
        viewModel.toggle(MarketKey("binance:BTC/USDT"))

        viewModel.onScopeChange(AssetClass.STOCK)
        runCurrent()
        viewModel.toggle(MarketKey("gate:TSLAX/USDT"))

        assertEquals(
            setOf(MarketKey("binance:BTC/USDT"), MarketKey("gate:TSLAX/USDT")),
            viewModel.uiState.value.selected,
        )

        viewModel.addSelected()

        assertEquals(
            setOf("binance:BTC/USDT", "gate:TSLAX/USDT"),
            watchlists.itemsOf(listId).map { it.key.value }.toSet(),
        )
        assertTrue(viewModel.uiState.value.finished)
    }

    @Test
    fun `a query only one scope has points to that scope`() = searchTest { viewModel ->
        seedCatalogue()

        type(viewModel, "TSLA")

        var state = viewModel.uiState.value
        assertTrue(state.results.isEmpty())
        assertEquals(3, state.otherScopeMatches)
        assertEquals(AssetClass.STOCK, state.otherScope)

        viewModel.onScopeChange(state.otherScope)
        runCurrent()

        state = viewModel.uiState.value
        assertEquals(3, state.results.size)
        assertEquals(0, state.otherScopeMatches)

        type(viewModel, "BTC")

        state = viewModel.uiState.value
        assertTrue(state.results.isEmpty())
        assertEquals(1, state.otherScopeMatches)
        assertEquals(AssetClass.CRYPTO, state.otherScope)
    }

    @Test
    fun `a tapped scope shows neither the old rows nor no-match until its search answers`() = searchTest { viewModel ->
        seedCatalogue()
        type(viewModel, "USDT")
        assertEquals(2, viewModel.uiState.value.results.size)
        assertFalse(viewModel.uiState.value.pending)

        val answer = CompletableDeferred<Unit>()
        markets.onSearch = { answer.await() }
        viewModel.onScopeChange(AssetClass.STOCK)
        runCurrent()

        var state = viewModel.uiState.value
        assertEquals(AssetClass.STOCK, state.scope)
        assertTrue(state.pending)
        // Not the two coins under the Stocks tab, and no count either.
        assertTrue(state.results.isEmpty())
        assertEquals(0, state.otherScopeMatches)

        answer.complete(Unit)
        runCurrent()

        state = viewModel.uiState.value
        assertFalse(state.pending)
        assertEquals(4, state.results.size)
    }

    @Test
    fun `no other-scope count while this scope has rows, nothing matches or the query is blank`() =
        searchTest { viewModel ->
            seedCatalogue()

            type(viewModel, "USDT")
            assertEquals(0, viewModel.uiState.value.otherScopeMatches)

            type(viewModel, "ZZZ")
            assertTrue(viewModel.uiState.value.results.isEmpty())
            assertEquals(0, viewModel.uiState.value.otherScopeMatches)

            type(viewModel, "TSLA")
            assertEquals(3, viewModel.uiState.value.otherScopeMatches)

            type(viewModel, "")
            assertEquals(0, viewModel.uiState.value.otherScopeMatches)
        }

    @Test
    fun `the other-scope probe stops one past the cap`() = searchTest { viewModel ->
        repeat(150) { index -> markets.put(MarketKey("kraken:C$index/EUR")) }
        viewModel.onScopeChange(AssetClass.STOCK)

        type(viewModel, "EUR")

        assertEquals(TickerSearchUiState.OTHER_SCOPE_MATCH_CAP + 1, viewModel.uiState.value.otherScopeMatches)
    }

    @Test
    fun `stock chips stay on the built-in list until the refresh ranks the catalogue`() {
        val refresh = CompletableDeferred<Unit>()
        markets.onRefreshAll = { refresh.await() }

        searchTest { viewModel ->
            assertEquals(DEFAULT_POPULAR_STOCKS, viewModel.uiState.value.popularStocks)
            assertTrue(viewModel.uiState.value.loading)

            // What the refresh brings in: NVDA is listed on two exchanges, TSLA on three.
            seedCatalogue()
            markets.put(MarketKey("mexc:NVDAON/USDT"), underlying = "NVDA")
            refresh.complete(Unit)
            runCurrent()

            assertFalse(viewModel.uiState.value.loading)
            assertEquals(listOf("TSLA", "NVDA"), viewModel.uiState.value.popularStocks)
        }
    }

    @Test
    fun `a reopened screen ranks the cached catalogue before the refresh lands`() {
        seedCatalogue()
        markets.onRefreshAll = { CompletableDeferred<Unit>().await() }

        searchTest { viewModel ->
            assertTrue(viewModel.uiState.value.loading)
            assertEquals(listOf("TSLA", "NVDA"), viewModel.uiState.value.popularStocks)
        }
    }

    @Test
    fun `a catalogue without stock tokens keeps the built-in stock chips`() = searchTest { viewModel ->
        markets.put(MarketKey("binance:BTC/USDT"))

        assertFalse(viewModel.uiState.value.loading)
        assertEquals(DEFAULT_POPULAR_STOCKS, viewModel.uiState.value.popularStocks)
        assertEquals(DEFAULT_POPULAR_PAIRS, viewModel.uiState.value.popularPairs)
    }

    @Test
    fun `an early stock query re-runs once the catalogue refresh lands`() {
        val refresh = CompletableDeferred<Unit>()
        markets.onRefreshAll = { refresh.await() }

        searchTest { viewModel ->
            viewModel.onScopeChange(AssetClass.STOCK)
            type(viewModel, "TSLA")
            assertTrue(viewModel.uiState.value.results.isEmpty())

            seedCatalogue()
            refresh.complete(Unit)
            runCurrent()

            assertEquals(
                listOf("binance:TSLAB/USDT", "gate:TSLAX/USDT", "mexc:TSLAON/USDT"),
                viewModel.uiState.value.results.map { it.key.value },
            )
        }
    }

    /** Two coins and four stock tokens: three of TSLA on three exchanges, one of NVDA. */
    private fun seedCatalogue() {
        markets.put(MarketKey("binance:BTC/USDT"))
        markets.put(MarketKey("binance:ETH/USDT"))
        markets.put(MarketKey("binance:TSLAB/USDT"), underlying = "TSLA")
        markets.put(MarketKey("gate:TSLAX/USDT"), underlying = "TSLA")
        markets.put(MarketKey("mexc:TSLAON/USDT"), underlying = "TSLA")
        markets.put(MarketKey("kucoin:NVDAX/USDT"), underlying = "NVDA")
    }

    /** Types [text] and lets the 200 ms debounce run out. */
    private fun TestScope.type(viewModel: TickerSearchViewModel, text: String) {
        viewModel.onQueryChange(text)
        advanceTimeBy(DEBOUNCE_SETTLE_MS)
    }

    private var listId = 0L

    /** Runs [block] with the state flow collected, so `WhileSubscribed` keeps [uiState] fresh. */
    private fun searchTest(block: suspend TestScope.(TickerSearchViewModel) -> Unit) = runTest(dispatcher) {
        listId = watchlists.seed("Main")
        val viewModel = TickerSearchViewModel(listId, markets, watchlists, popularPairs)
        backgroundScope.launch { viewModel.uiState.collect { } }
        block(viewModel)
    }

    private object EmptyCache : PopularPairsCache {
        override suspend fun read(): CachedPopularPairs? = null
        override suspend fun write(pairs: List<String>, fetchedAtMs: Long) = Unit
    }

    private companion object {
        /** Past the view model's 200 ms debounce. */
        const val DEBOUNCE_SETTLE_MS = 250L
    }
}
