package com.neatcode.tabgreater.ui.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neatcode.tabgreater.core.data.popular.DEFAULT_POPULAR_PAIRS
import com.neatcode.tabgreater.core.data.popular.PopularPairsRepository
import com.neatcode.tabgreater.core.data.repo.MarketRepository
import com.neatcode.tabgreater.core.data.repo.WatchlistRepository
import com.neatcode.tabgreater.core.model.ExchangeId
import com.neatcode.tabgreater.core.model.InstrumentKind
import com.neatcode.tabgreater.core.model.Market
import com.neatcode.tabgreater.core.model.MarketKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
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
    val popularPairs: List<String> = DEFAULT_POPULAR_PAIRS,
    val instrumentKind: InstrumentKind? = null,
    val source: ExchangeId? = null,
    /** A new query or filter is being applied, so stale rows stay hidden. */
    val pending: Boolean = false,
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
    val pageError: Boolean = false,
)

/**
 * Searches every active market and pages the stable catalogue as the user scrolls. Type and
 * source are independent filters; the query itself covers symbols, names, event descriptions,
 * outcome sides and venue metadata through [MarketRepository.searchPage].
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class TickerSearchViewModel(
    private val watchlistId: Long,
    private val marketRepository: MarketRepository,
    private val watchlistRepository: WatchlistRepository,
    private val popularPairsRepository: PopularPairsRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val instrumentKind = MutableStateFlow<InstrumentKind?>(null)
    private val source = MutableStateFlow<ExchangeId?>(null)
    private val selected = MutableStateFlow<Set<MarketKey>>(emptySet())
    private val progress = MutableStateFlow(Progress(loading = true, finished = false))
    private val popularPairs = MutableStateFlow(DEFAULT_POPULAR_PAIRS)
    private val catalogueRevision = MutableStateFlow(0)
    private val retryRevision = MutableStateFlow(0)
    private val filters = combine(instrumentKind, source, ::Filters)

    private val requests: Flow<Request> = query
        .debounce { text -> if (text.isBlank()) 0L else DEBOUNCE_MS }
        .map { it.trim() }
        .distinctUntilChanged()
        .combine(filters) { text, currentFilters -> Request(text, currentFilters) }
        .distinctUntilChanged()
    private val requestTriggers: Flow<Triple<Request, Int, Int>> = requests
        .combine(catalogueRevision) { request, revision -> request to revision }
        .combine(retryRevision) { (request, catalogRevision), retry -> Triple(request, catalogRevision, retry) }
        .distinctUntilChanged()

    private val found = MutableStateFlow(Found(Request("", Filters(null, null)), pending = true))
    private var activeGeneration = 0L
    private var loadMoreJob: Job? = null

    val uiState: StateFlow<TickerSearchUiState> = combine(
        combine(query, filters) { text, currentFilters -> text to currentFilters },
        found,
        selected,
        progress,
        popularPairs,
    ) { (text, currentFilters), result, picked, progress, popular ->
        val current = result.request.query == text.trim() && result.request.filters == currentFilters
        TickerSearchUiState(
            query = text,
            results = if (current) result.markets else emptyList(),
            selected = picked,
            loading = progress.loading,
            finished = progress.finished,
            popularPairs = popular,
            instrumentKind = currentFilters.kind,
            source = currentFilters.source,
            pending = !current || result.pending,
            hasMore = current && !result.pending && result.hasMore,
            loadingMore = current && result.loadingMore,
            pageError = current && result.pageError,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TickerSearchUiState())

    init {
        viewModelScope.launch {
            requestTriggers.flatMapLatest { (request, _, _) -> searchRequest(request) }
                .collect { result -> found.value = result }
        }
        viewModelScope.launch {
            try {
                marketRepository.refreshAll()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A cached catalogue can still answer search when a venue is offline.
            }
            progress.update { it.copy(loading = false) }
            catalogueRevision.value += 1
        }
        viewModelScope.launch { popularPairs.value = popularPairsRepository.pairs() }
    }

    fun onQueryChange(text: String) {
        query.value = text
    }

    fun onInstrumentKindChange(value: InstrumentKind?) {
        instrumentKind.value = value
    }

    fun onSourceChange(value: ExchangeId?) {
        source.value = value
    }

    fun toggle(key: MarketKey) {
        selected.update { current -> if (key in current) current - key else current + key }
    }

    /** Load the next stable page; repeated scroll callbacks cannot request the same page twice. */
    fun loadNextPage() {
        val current = found.value
        if (current.pending || current.loadingMore || !current.hasMore) return

        val generation = activeGeneration
        val request = current.request
        val offset = current.nextOffset
        found.value = current.copy(loadingMore = true, pageError = false)
        loadMoreJob = viewModelScope.launch {
            try {
                val page = marketRepository.searchPage(
                    query = request.query,
                    limit = PAGE_SIZE + 1,
                    offset = offset,
                    instrumentKind = request.filters.kind,
                    exchange = request.filters.source,
                )
                val latest = found.value
                if (generation != activeGeneration || latest.request != request) return@launch

                val known = latest.markets.mapTo(HashSet()) { it.key }
                val appended = page.take(PAGE_SIZE).filter { known.add(it.key) }
                found.value = latest.copy(
                    markets = latest.markets + appended,
                    nextOffset = offset + PAGE_SIZE,
                    hasMore = page.size > PAGE_SIZE,
                    loadingMore = false,
                    pageError = false,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                val latest = found.value
                if (generation == activeGeneration && latest.request == request) {
                    found.value = latest.copy(loadingMore = false, pageError = true)
                }
            }
        }
    }

    /** Retries either the initial query or the failed next page, whichever is currently visible. */
    fun retrySearch() {
        val current = found.value
        if (!current.pageError || current.pending || current.loadingMore) return
        if (current.hasMore) {
            loadNextPage()
        } else {
            retryRevision.value += 1
        }
    }

    /** Appends the picked markets to the watchlist and flags the screen as done. */
    fun addSelected() {
        val keys = selected.value.toList()
        viewModelScope.launch {
            if (keys.isNotEmpty()) watchlistRepository.addItems(watchlistId, keys)
            progress.update { it.copy(finished = true) }
        }
    }

    private fun searchRequest(request: Request): Flow<Found> = flow {
        val generation = ++activeGeneration
        loadMoreJob?.cancel()
        val previous = found.value
        val retainExisting = previous.request == request && previous.markets.isNotEmpty()
        val retainedCount = previous.markets.size.takeIf { retainExisting } ?: PAGE_SIZE
        found.value = if (retainExisting) {
            previous.copy(pending = true, loadingMore = false, pageError = false)
        } else {
            Found(request, pending = true)
        }
        try {
            val page = marketRepository.searchPage(
                query = request.query,
                limit = retainedCount + 1,
                offset = 0,
                instrumentKind = request.filters.kind,
                exchange = request.filters.source,
            )
            if (generation == activeGeneration) {
                emit(
                    Found(
                        request = request,
                        markets = page.take(retainedCount).distinctBy { it.key },
                        nextOffset = retainedCount,
                        hasMore = page.size > retainedCount,
                    ),
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (generation == activeGeneration) {
                emit(
                    if (retainExisting) previous.copy(pending = false, loadingMore = false)
                    else Found(request, pageError = true),
                )
            }
        }
    }

    private data class Filters(val kind: InstrumentKind?, val source: ExchangeId?)
    private data class Request(val query: String, val filters: Filters)
    private data class Found(
        val request: Request,
        val markets: List<Market> = emptyList(),
        val nextOffset: Int = 0,
        val hasMore: Boolean = false,
        val pending: Boolean = false,
        val loadingMore: Boolean = false,
        val pageError: Boolean = false,
    )
    private data class Progress(val loading: Boolean, val finished: Boolean)

    private companion object {
        const val DEBOUNCE_MS = 200L
        const val STOP_TIMEOUT_MS = 5_000L
        const val PAGE_SIZE = 50
    }
}
