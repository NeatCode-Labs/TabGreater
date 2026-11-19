package com.neatcode.tabgreater.widget

import com.neatcode.tabgreater.core.model.ExchangeId
import com.neatcode.tabgreater.core.model.InstrumentKind
import com.neatcode.tabgreater.core.model.Market

/** Independent instrument-kind and market-data source filters. */
internal data class WidgetSearchFilters(
    val instrumentKind: InstrumentKind? = null,
    val source: ExchangeId? = null,
) {
    val isFiltered: Boolean get() = instrumentKind != null || source != null
}

/** One page of a specific query and filter selection. [nextOffset] counts fetched rows. */
internal data class ScopedResults(
    val query: String = "",
    val filters: WidgetSearchFilters = WidgetSearchFilters(),
    val markets: List<Market> = emptyList(),
    val nextOffset: Int = 0,
)

/** Rows refreshed without discarding the current scroll position. */
internal data class RefreshedResults(
    val results: ScopedResults,
    val hasMore: Boolean,
)

/** Query normalization and paging rules shared by the widget picker. */
internal object WidgetSearch {
    suspend fun page(
        text: String,
        filters: WidgetSearchFilters,
        limit: Int,
        offset: Int,
        searchPage: suspend (
            query: String,
            limit: Int,
            offset: Int,
            instrumentKind: InstrumentKind?,
            source: ExchangeId?,
        ) -> List<Market>,
    ): ScopedResults {
        val query = text.trim()
        val markets = searchPage(query, limit, offset, filters.instrumentKind, filters.source)
        return ScopedResults(
            query = query,
            filters = filters,
            markets = markets,
            nextOffset = offset + markets.size,
        )
    }

    /**
     * Re-reads the currently visible prefix with one extra row as a continuation probe. The
     * caller can replace rows by stable market keys while retaining the list's scroll state.
     */
    suspend fun refreshVisible(
        text: String,
        filters: WidgetSearchFilters,
        visibleCount: Int,
        firstPageSize: Int,
        searchPage: suspend (
            query: String,
            limit: Int,
            offset: Int,
            instrumentKind: InstrumentKind?,
            source: ExchangeId?,
        ) -> List<Market>,
    ): RefreshedResults {
        val desiredCount = maxOf(visibleCount, firstPageSize)
        val page = page(text, filters, limit = desiredCount + 1, offset = 0, searchPage = searchPage)
        val visible = page.markets.take(desiredCount)
        return RefreshedResults(
            results = page.copy(markets = visible, nextOffset = visible.size),
            hasMore = page.markets.size > visible.size,
        )
    }
}
