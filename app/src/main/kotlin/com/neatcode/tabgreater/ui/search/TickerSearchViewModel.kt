package com.neatcode.tabgreater.ui.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neatcode.tabgreater.core.data.popular.DEFAULT_POPULAR_PAIRS
import com.neatcode.tabgreater.core.data.popular.PopularPairsRepository
import com.neatcode.tabgreater.core.data.repo.MarketRepository
import com.neatcode.tabgreater.core.data.repo.WatchlistRepository
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
)

/**
 * Market search for one watchlist. Refreshes every exchange's instrument list on entry (that is
 * the only place the app needs a complete catalogue) and searches the Room cache with a 200 ms
 * debounce, so typing does not run a query per keystroke.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class TickerSearchViewModel(
    private val watchlistId: Long,
    private val marketRepository: MarketRepository,
    private val watchlistRepository: WatchlistRepository,
    private val popularPairsRepository: PopularPairsRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val selected = MutableStateFlow<Set<MarketKey>>(emptySet())
    private val progress = MutableStateFlow(Progress(loading = true, finished = false))

    /** Starts on the built-in list so the chip row never flashes empty. */
    private val popularPairs = MutableStateFlow(DEFAULT_POPULAR_PAIRS)

    /** Bumped when the instrument lists have been refreshed, so an early query re-runs. */
    private val catalogue = MutableStateFlow(0)

    private val results: Flow<List<Market>> = query
        .debounce(DEBOUNCE_MS)
        .map { it.trim() }
        .distinctUntilChanged()
        .combine(catalogue) { text, _ -> text }
        .flatMapLatest { text ->
            flow { emit(if (text.isEmpty()) emptyList() else marketRepository.search(text)) }
        }

    val uiState: StateFlow<TickerSearchUiState> =
        combine(query, results, selected, progress, popularPairs) { text, markets, picked, state, popular ->
            TickerSearchUiState(
                query = text,
                results = markets,
                selected = picked,
                loading = state.loading,
                finished = state.finished,
                popularPairs = popular,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TickerSearchUiState())

    init {
        viewModelScope.launch {
            marketRepository.refreshAll()
            catalogue.value += 1
            progress.update { it.copy(loading = false) }
        }
        // Opening this screen is the only thing that may refresh the ranking, and the repository
        // still lets at most one call per 24 h through to CoinGecko.
        viewModelScope.launch { popularPairs.value = popularPairsRepository.pairs() }
    }

    fun onQueryChange(text: String) {
        query.value = text
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

    private data class Progress(val loading: Boolean, val finished: Boolean)

    private companion object {
        const val DEBOUNCE_MS = 200L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
