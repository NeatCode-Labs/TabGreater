package com.neatcode.tabgreater.ui.watchlist

import androidx.compose.runtime.Immutable
import com.neatcode.tabgreater.core.live.LiveStatus
import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.SortMode
import com.neatcode.tabgreater.core.model.SparkPeriod
import com.neatcode.tabgreater.core.model.TileSize
import com.neatcode.tabgreater.core.model.Watchlist

/**
 * Everything one tile draws, already formatted — the composables never touch a repository,
 * a `Ticker` or a number formatter.
 *
 * @property priceText formatted with the market's own price precision; `null` while unknown.
 * @property changeText signed percentage for the watchlist's period; `null` while unknown.
 * @property isUp `true` when the change is zero or positive (drives text and sparkline colour).
 * @property spark closes in chronological order; `null` or shorter than two points draws nothing.
 * @property absChangeText absolute change plus the unsigned percentage, `+1.10 (0.04%)`, shown on
 *   Compact / Medium / Large; `null` while either half is unknown.
 * @property highText highest price of the period (24 h statistic, else the sparkline window).
 * @property lowText lowest price of the period.
 * @property volumeText base volume of the period in the compact notation (`713M`).
 * @property accent ARGB of the user's left stripe, `null` = no stripe.
 */
@Immutable
data class TileUiState(
    val itemId: Long,
    val key: MarketKey,
    val exchangeLabel: String,
    val pair: String,
    val priceText: String?,
    val changeText: String?,
    val absChangeText: String?,
    val highText: String?,
    val lowText: String?,
    val volumeText: String?,
    val isUp: Boolean,
    val spark: FloatArray?,
    val accent: Long?,
) {
    override fun equals(other: Any?): Boolean {
        if (other !is TileUiState) return false
        val sparkEquals = when {
            spark == null -> other.spark == null
            other.spark == null -> false
            else -> spark.contentEquals(other.spark)
        }
        return itemId == other.itemId &&
            key == other.key &&
            exchangeLabel == other.exchangeLabel &&
            pair == other.pair &&
            priceText == other.priceText &&
            changeText == other.changeText &&
            absChangeText == other.absChangeText &&
            highText == other.highText &&
            lowText == other.lowText &&
            volumeText == other.volumeText &&
            isUp == other.isUp &&
            accent == other.accent &&
            sparkEquals
    }

    override fun hashCode(): Int {
        var result = itemId.hashCode()
        result = 31 * result + key.hashCode()
        result = 31 * result + exchangeLabel.hashCode()
        result = 31 * result + pair.hashCode()
        result = 31 * result + (priceText?.hashCode() ?: 0)
        result = 31 * result + (changeText?.hashCode() ?: 0)
        result = 31 * result + (absChangeText?.hashCode() ?: 0)
        result = 31 * result + (highText?.hashCode() ?: 0)
        result = 31 * result + (lowText?.hashCode() ?: 0)
        result = 31 * result + (volumeText?.hashCode() ?: 0)
        result = 31 * result + isUp.hashCode()
        result = 31 * result + (accent?.hashCode() ?: 0)
        result = 31 * result + (spark?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * State of the whole Watchlists screen: the tab row, the chip row and the tile grid.
 *
 * @property shrinkZeros app setting for the `0.0₄123` price compression.
 * @property selectedIds watchlist-item ids ticked in selection mode; empty means the screen
 *   shows its normal app bar.
 * @property itemCounts number of tickers per watchlist id, for the "Move to watchlist" sheet.
 */
@Immutable
data class WatchlistUiState(
    val watchlists: List<Watchlist> = emptyList(),
    val selectedId: Long? = null,
    val period: SparkPeriod = SparkPeriod.HOURS_24,
    val tileSize: TileSize = TileSize.SMALL,
    val sort: SortMode = SortMode.CUSTOM,
    val tiles: List<TileUiState> = emptyList(),
    val liveStatus: LiveStatus = LiveStatus.CONNECTING,
    val shrinkZeros: Boolean = true,
    val selectedIds: Set<Long> = emptySet(),
    val itemCounts: Map<Long, Int> = emptyMap(),
) {
    /** `true` while the contextual action bar replaces the app bar. */
    val isSelecting: Boolean get() = selectedIds.isNotEmpty()
}
