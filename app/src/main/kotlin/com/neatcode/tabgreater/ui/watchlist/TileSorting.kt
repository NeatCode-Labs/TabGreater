package com.neatcode.tabgreater.ui.watchlist

import com.neatcode.tabgreater.core.model.SortMode
import com.neatcode.tabgreater.core.model.WatchlistItem

/**
 * Pure ordering rules of the tile grid: the comparators behind the sort chip and the two list
 * operations a drag needs. Everything here is side-effect free so it can be unit tested without
 * a view model, a database or Compose.
 */

/** A tile plus the raw numbers the price/change comparators need. */
internal class TileRow(
    val item: WatchlistItem,
    val price: Double?,
    val changePct: Double?,
    val tile: TileUiState,
)

/** Unknown prices and changes sort last, and ties keep the user's custom order. */
internal fun comparator(sort: SortMode): Comparator<TileRow> = when (sort) {
    SortMode.CUSTOM -> compareBy<TileRow>({ it.item.position }, { it.item.id })

    SortMode.EXCHANGE_PAIR -> compareBy<TileRow>(
        { it.item.key.exchange.displayName },
        { it.item.key.base },
        { it.item.key.quote },
    )

    SortMode.PAIR_EXCHANGE -> compareBy<TileRow>(
        { it.item.key.base },
        { it.item.key.quote },
        { it.item.key.exchange.displayName },
    )

    SortMode.PRICE -> compareByDescending<TileRow> { it.price ?: Double.NEGATIVE_INFINITY }
        .thenBy { it.item.position }

    SortMode.CHANGE -> compareByDescending<TileRow> { it.changePct ?: Double.NEGATIVE_INFINITY }
        .thenBy { it.item.position }
}

/**
 * Moves the element at [from] to index [to], shifting everything in between — the list operation
 * behind one step of a drag. Out-of-range indices and `from == to` return the receiver unchanged,
 * so a drag that reaches the edge of the grid is simply a no-op.
 */
internal fun <T> moveItem(items: List<T>, from: Int, to: Int): List<T> {
    if (from == to) return items
    if (from !in items.indices || to !in items.indices) return items
    return items.toMutableList().apply { add(to, removeAt(from)) }
}

/**
 * Re-sorts [items] into the sequence given by [order] (item ids) and renumbers
 * [WatchlistItem.position] accordingly, so the Custom comparator reproduces exactly that order.
 * Items missing from [order] keep their relative order and follow the listed ones.
 */
internal fun orderItems(items: List<WatchlistItem>, order: List<Long>): List<WatchlistItem> {
    val rank = order.withIndex().associate { (index, id) -> id to index }
    return items
        .sortedWith(compareBy({ rank[it.id] ?: Int.MAX_VALUE }, { it.position }, { it.id }))
        .mapIndexed { index, item -> if (item.position == index) item else item.copy(position = index) }
}
