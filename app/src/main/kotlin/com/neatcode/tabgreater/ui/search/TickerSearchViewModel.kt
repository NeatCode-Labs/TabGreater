package com.neatcode.tabgreater.ui.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neatcode.tabgreater.core.data.popular.DEFAULT_POPULAR_PAIRS
import com.neatcode.tabgreater.core.data.popular.DEFAULT_POPULAR_STOCKS
import com.neatcode.tabgreater.core.data.popular.PopularPairsRepository
import com.neatcode.tabgreater.core.data.repo.MarketRepository
import com.neatcode.tabgreater.core.data.repo.WatchlistRepository
import com.neatcode.tabgreater.core.model.AssetClass
import com.neatcode.tabgreater.core.model.Market
import com.neatcode.tabgreater.core.model.MarketKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** State of the "+ Ticker" market search. */
@Immutable
data class TickerSearchUiState(
    val query: String = "",
    val results: List<Market> = emptyList(),
    val selected: Set<MarketKey> = emptySet(),
    val loading: Boolean = true,
    /** Set once the picked markets have been written; the screen then navigates back. */
    val finished: Boolean = false,
    /** Quick-add chips (`BTC/USDT`, …), shown only while [query] is empty. */
    val popularPairs: List<String> = DEFAULT_POPULAR_PAIRS,
    /** Which kind of market [results] lists: coins or stock tokens. */
    val scope: AssetClass = AssetClass.CRYPTO,
    /** The Stocks scope's quick-add chips (`TSLA`, …), shown only while [query] is empty. */
    val popularStocks: List<String> = DEFAULT_POPULAR_STOCKS,
    /**
     * How many markets [otherScope] has for [query] when [scope] has none; 0 otherwise. Counted up
     * to [OTHER_SCOPE_MATCH_CAP] + 1, so a value above the cap reads as "more than the cap".
     */
    val otherScopeMatches: Int = 0,
    /**
     * Set between a tab tap and the new scope's first result. [results] is empty meanwhile rather
     * than the previous scope's rows, and the screen says nothing rather than "No markets match".
     */
    val pending: Boolean = false,
) {
    /** The scope the "N matches in …" hint switches to. */
    val otherScope: AssetClass get() = scope.other()

    companion object {
        /** The largest count the cross-scope hint spells out; anything above it shows as "100+". */
        const val OTHER_SCOPE_MATCH_CAP = 100
    }
}

/**
 * Market search for one watchlist. Refreshes every exchange's instrument list on entry (that is
 * the only place the app needs a complete catalogue) and searches the Room cache with a 200 ms
 * debounce, so typing does not run a query per keystroke.
 *
 * The search runs in one [AssetClass] scope at a time, Crypto on every open. Switching the scope
 * re-runs the current query at once; the selection is kept across the switch, so one "Add N" can
 * add coins and stock tokens together.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class TickerSearchViewModel(
    private val watchlistId: Long,
    private val marketRepository: MarketRepository,
    private val watchlistRepository: WatchlistRepository,
    private val popularPairsRepository: PopularPairsRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val scope = MutableStateFlow(AssetClass.CRYPTO)
    private val selected = MutableStateFlow<Set<MarketKey>>(emptySet())
    private val progress = MutableStateFlow(Progress(loading = true, finished = false))

    /** Starts on the built-in list so the chip row never flashes empty. */
    private val popularPairs = MutableStateFlow(DEFAULT_POPULAR_PAIRS)

    /** Likewise for the Stocks scope, until the catalogue has ranked its own stock tokens. */
    private val popularStocks = MutableStateFlow(DEFAULT_POPULAR_STOCKS)

    /** Bumped when the instrument lists have been refreshed, so an early query re-runs. */
    private val catalogue = MutableStateFlow(0)

    private val found: Flow<Found> = query
        // A blank query has nothing to search, so it is not held back: the screen's first state
        // and a cleared field land at once.
        .debounce { text -> if (text.isBlank()) 0L else DEBOUNCE_MS }
        .map { it.trim() }
        .distinctUntilChanged()
        // The scope joins after the debounce: a tab tap re-runs the query without waiting.
        .combine(scope) { text, scope -> text to scope }
        .combine(catalogue) { request, _ -> request }
        .flatMapLatest { (text, scope) -> flow { emit(find(text, scope)) } }

    val uiState: StateFlow<TickerSearchUiState> =
        combine(
            combine(query, scope, ::Pair),
            found,
            selected,
            progress,
            combine(popularPairs, popularStocks, ::Pair),
        ) { (text, scope), found, picked, state, (pairs, stocks) ->
            // Rows or a count found for the other tab must not show under this one for the moment
            // between a tab tap and its new result.
            val current = found.scope == scope
            TickerSearchUiState(
                query = text,
                results = if (current) found.markets else emptyList(),
                selected = picked,
                loading = state.loading,
                finished = state.finished,
                popularPairs = pairs,
                scope = scope,
                popularStocks = stocks,
                otherScopeMatches = if (current) found.otherScopeMatches else 0,
                pending = !current,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TickerSearchUiState())

    init {
        viewModelScope.launch {
            // The cached catalogue first, so a reopened screen shows its own ranking at once
            // instead of swapping the chips under the user's finger when the refresh lands.
            popularStocks.value = stockRoots()
            marketRepository.refreshAll()
            catalogue.value += 1
            progress.update { it.copy(loading = false) }
            popularStocks.value = stockRoots()
        }
        // Opening this screen is the only thing that may refresh the ranking, and the repository
        // still lets at most one call per 24 h through to CoinGecko.
        viewModelScope.launch { popularPairs.value = popularPairsRepository.pairs() }
    }

    fun onQueryChange(text: String) {
        query.value = text
    }

    fun onScopeChange(scope: AssetClass) {
        this.scope.value = scope
    }

    fun toggle(key: MarketKey) {
        selected.update { current -> if (key in current) current - key else current + key }
    }

    /** Appends the picked markets to the watchlist and flags the screen as done. */
    fun addSelected() {
        val keys = selected.value.toList()
        viewModelScope.launch {
            if (keys.isNotEmpty()) watchlistRepository.addItems(watchlistId, keys)
            progress.update { it.copy(finished = true) }
        }
    }

    /**
     * One search in [scope]. Only when it finds nothing is the other scope probed, with a capped
     * limit, for the "N matches in …" hint: a typed `TSLA` under Crypto then points to Stocks.
     */
    private suspend fun find(text: String, scope: AssetClass): Found {
        if (text.isEmpty()) return Found(scope)
        val markets = marketRepository.search(text, assetClass = scope)
        val elsewhere = if (markets.isEmpty()) {
            marketRepository.search(text, OTHER_SCOPE_PROBE_LIMIT, assetClass = scope.other()).size
        } else {
            0
        }
        return Found(scope, markets, elsewhere)
    }

    private suspend fun stockRoots(): List<String> =
        marketRepository.popularStockRoots().ifEmpty { DEFAULT_POPULAR_STOCKS }

    private data class Progress(val loading: Boolean, val finished: Boolean)

    /** A search result together with the scope it was run in. */
    private data class Found(
        val scope: AssetClass,
        val markets: List<Market> = emptyList(),
        val otherScopeMatches: Int = 0,
    )

    private companion object {
        const val DEBOUNCE_MS = 200L
        const val STOP_TIMEOUT_MS = 5_000L

        /** One more than the hint spells out, so "more than the cap" is detectable. */
        const val OTHER_SCOPE_PROBE_LIMIT = TickerSearchUiState.OTHER_SCOPE_MATCH_CAP + 1
    }
}

/** The search's other scope: there are exactly two. */
internal fun AssetClass.other(): AssetClass =
    if (this == AssetClass.STOCK) AssetClass.CRYPTO else AssetClass.STOCK
