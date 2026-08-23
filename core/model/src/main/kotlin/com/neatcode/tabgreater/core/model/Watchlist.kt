package com.neatcode.tabgreater.core.model

import kotlinx.serialization.Serializable

/** Tile layout, as picked in the "Tickers Appearance" sheet. */
@Serializable
enum class TileSize(val id: String, val label: String, val columns: Int) {
    SMALL("small", "Small", 2),
    COMPACT("compact", "Compact", 2),
    MEDIUM("medium", "Medium", 1),
    LARGE("large", "Large", 1);

    companion object {
        fun fromId(id: String): TileSize = entries.firstOrNull { it.id == id } ?: SMALL
    }
}

/** Sort order of tiles inside one watchlist. */
@Serializable
enum class SortMode(val id: String, val label: String) {
    CUSTOM("custom", "Custom"),
    EXCHANGE_PAIR("exchange_pair", "Exchange + Pair"),
    PAIR_EXCHANGE("pair_exchange", "Pair + Exchange"),
    PRICE("price", "Price"),
    CHANGE("change", "Change");

    companion object {
        fun fromId(id: String): SortMode = entries.firstOrNull { it.id == id } ?: CUSTOM
    }
}

/** A named watchlist (one tab). Period, size and sort are stored per list. */
@Serializable
data class Watchlist(
    val id: Long,
    val name: String,
    val position: Int,
    val period: SparkPeriod = SparkPeriod.HOURS_24,
    val tileSize: TileSize = TileSize.SMALL,
    val sort: SortMode = SortMode.CUSTOM,
)

/**
 * One ticker inside a watchlist.
 * @property accentColor optional ARGB colour of the left stripe (user-assigned), `null` = none.
 */
@Serializable
data class WatchlistItem(
    val id: Long,
    val watchlistId: Long,
    val key: MarketKey,
    val position: Int,
    val accentColor: Long? = null,
)

/**
 * A watchlist together with its items, as captured right before a delete so the 5 s "Undo"
 * can put it back ([WatchlistItem.id]s and [Watchlist.id] are the *old* ids and are not reused).
 */
data class WatchlistSnapshot(
    val watchlist: Watchlist,
    val items: List<WatchlistItem>,
)

/** How an imported backup is applied to the existing watchlists. */
enum class ImportMode {
    /** Delete every existing watchlist first, then create the imported ones. */
    REPLACE,

    /**
     * Keep existing watchlists; imported lists with the same name (case-insensitive) receive
     * the missing tickers, other lists are appended (until [Limits.MAX_WATCHLISTS]).
     */
    MERGE,
}

/**
 * Outcome of an import.
 * @property itemsSkipped tickers dropped because their key was invalid, a duplicate, or the
 *   list was full; [watchlistsSkipped] lists dropped because the 20-list cap was reached.
 */
data class ImportResult(
    val watchlistsAdded: Int,
    val watchlistsMerged: Int,
    val itemsAdded: Int,
    val itemsSkipped: Int,
    val watchlistsSkipped: Int,
)

object Limits {
    const val MAX_WATCHLISTS = 25
    const val MAX_ITEMS_PER_WATCHLIST = 120
    const val MAX_WATCHLIST_NAME_LENGTH = 30
}
