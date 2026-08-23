package com.neatcode.tabgreater.ui.chart

import androidx.compose.runtime.Immutable
import com.neatcode.tabgreater.core.model.Market
import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.PriceFormat
import com.neatcode.tabgreater.feature.chart.ChartSettings

/**
 * Everything the chart screen draws, already formatted — the composables never see a `Ticker`,
 * a repository or a number formatter.
 *
 * @property market `null` until the instrument list has been consulted; the canvas only mounts
 *   once it is known (its native symbol and price precision drive every request).
 * @property unavailable the exchange does not list this pair (any more): the canvas stays empty
 *   and the header shows placeholders instead of spinning forever.
 * @property isUp direction of the 24 h change; drives the caret and the percentage colour.
 * @property hasTrend `false` while the change is unknown, so no caret is drawn.
 * @property starred the market is in the currently selected watchlist.
 * @property canStar the ★ can actually change something: there is a watchlist, and either the
 *   market is already in it (so the tap un-stars) or the list is still below its item cap.
 * @property shrinkZeros the global "shrink zeros" setting; every price in the header goes through
 *   `shrunkPrice`, so `0.00001234` reads `0.0₄1234` exactly like the tiles.
 */
@Immutable
data class ChartUiState(
    val key: MarketKey,
    val market: Market? = null,
    val unavailable: Boolean = false,
    val exchangeLabel: String = key.exchange.displayName.uppercase(),
    val pair: String = key.pair,
    val priceText: String = PriceFormat.NO_VALUE,
    val changeText: String = PriceFormat.NO_VALUE,
    val isUp: Boolean = true,
    val hasTrend: Boolean = false,
    val askText: String = PriceFormat.NO_VALUE,
    val bidText: String = PriceFormat.NO_VALUE,
    val highText: String = PriceFormat.NO_VALUE,
    val lowText: String = PriceFormat.NO_VALUE,
    val volumeText: String = PriceFormat.NO_VALUE,
    val starred: Boolean = false,
    val canStar: Boolean = false,
    val shrinkZeros: Boolean = true,
    val settings: ChartSettings = ChartSettings.DEFAULT,
)

/** One-shot messages the chart screen shows in its snackbar; see `ChartViewModel.toggleStar`. */
sealed interface ChartEvent {

    /** The ★ could not add the market: the selected watchlist already holds [limit] items. */
    data class WatchlistFull(val limit: Int) : ChartEvent

    /** The ★ could not add the market: there is no watchlist to add it to. */
    data object NoWatchlist : ChartEvent
}
