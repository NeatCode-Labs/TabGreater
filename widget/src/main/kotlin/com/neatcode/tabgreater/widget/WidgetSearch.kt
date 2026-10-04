package com.neatcode.tabgreater.widget

import com.neatcode.tabgreater.core.model.AssetClass
import com.neatcode.tabgreater.core.model.Market

/** One search of the widget picker, with the [scope] it was run in. */
internal data class ScopedResults(
    val scope: AssetClass,
    val markets: List<Market> = emptyList(),
    /**
     * How many markets the other scope has for the query when [scope] has none; 0 otherwise.
     * Counted up to [WidgetSearch.OTHER_SCOPE_MATCH_CAP] + 1.
     */
    val otherScopeMatches: Int = 0,
)

/**
 * The scope rules of the widget picker's "Crypto | Stocks" search, the same ones the app's
 * "+ Add pair" screen follows. Kept out of the composable so they are unit testable.
 */
internal object WidgetSearch {

    /** The largest count the cross-scope hint spells out; anything above it shows as "100+". */
    const val OTHER_SCOPE_MATCH_CAP = 100

    /**
     * Searches [text] in [scope] through [search] (`MarketRepository.search`). Only when that finds
     * nothing is the other scope probed, with a capped limit, for the "N matches in …" hint.
     */
    suspend fun run(
        text: String,
        scope: AssetClass,
        limit: Int,
        search: suspend (query: String, limit: Int, assetClass: AssetClass) -> List<Market>,
    ): ScopedResults {
        if (text.isBlank()) return ScopedResults(scope)
        val markets = search(text, limit, scope)
        val elsewhere = if (markets.isEmpty()) {
            search(text, OTHER_SCOPE_MATCH_CAP + 1, other(scope)).size
        } else {
            0
        }
        return ScopedResults(scope, markets, elsewhere)
    }

    /** The scope the hint switches to: there are exactly two. */
    fun other(scope: AssetClass): AssetClass =
        if (scope == AssetClass.STOCK) AssetClass.CRYPTO else AssetClass.STOCK

    /** The count as the hint prints it: `7`, or `100+` above the cap. */
    fun countLabel(count: Int): String =
        if (count > OTHER_SCOPE_MATCH_CAP) "$OTHER_SCOPE_MATCH_CAP+" else count.toString()
}
