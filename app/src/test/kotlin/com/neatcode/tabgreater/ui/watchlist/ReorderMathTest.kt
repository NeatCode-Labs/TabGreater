package com.neatcode.tabgreater.ui.watchlist

import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.WatchlistItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** The list arithmetic behind one drag step and behind the optimistic order the grid shows. */
class ReorderMathTest {

    private val list = listOf(1L, 2L, 3L, 4L)

    @Test
    fun `moves an item forwards`() {
        assertEquals(listOf(2L, 3L, 1L, 4L), moveItem(list, from = 0, to = 2))
    }

    @Test
    fun `moves an item backwards`() {
        assertEquals(listOf(1L, 4L, 2L, 3L), moveItem(list, from = 3, to = 1))
    }

    @Test
    fun `moving onto itself changes nothing`() {
        assertSame(list, moveItem(list, from = 2, to = 2))
    }

    @Test
    fun `out of range indices change nothing`() {
        assertSame(list, moveItem(list, from = 0, to = 4))
        assertSame(list, moveItem(list, from = -1, to = 0))
        assertSame(list, moveItem(list, from = 9, to = 1))
    }

    @Test
    fun `an empty list has nothing to move`() {
        val empty = emptyList<Long>()
        assertSame(empty, moveItem(empty, from = 0, to = 0))
    }

    @Test
    fun `orderItems follows the dragged order and renumbers positions`() {
        val items = listOf(item(1, 0), item(2, 1), item(3, 2))
        val ordered = orderItems(items, listOf(3L, 1L, 2L))
        assertEquals(listOf(3L, 1L, 2L), ordered.map { it.id })
        assertEquals(listOf(0, 1, 2), ordered.map { it.position })
    }

    @Test
    fun `orderItems appends items the drag did not mention`() {
        val items = listOf(item(1, 0), item(2, 1), item(3, 2))
        val ordered = orderItems(items, listOf(3L))
        assertEquals(listOf(3L, 1L, 2L), ordered.map { it.id })
        assertEquals(listOf(0, 1, 2), ordered.map { it.position })
    }

    private fun item(id: Long, position: Int) = WatchlistItem(
        id = id,
        watchlistId = 1,
        key = MarketKey("binance:BTC/USDT"),
        position = position,
    )
}
