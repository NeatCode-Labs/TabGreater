package com.neatcode.tabgreater.ui.watchlist

import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.SortMode
import com.neatcode.tabgreater.core.model.WatchlistItem
import org.junit.Assert.assertEquals
import org.junit.Test

/** The five sort modes behind the third chip, including how they treat ties and missing data. */
class TileSortingTest {

    @Test
    fun `custom follows position then id`() {
        val rows = listOf(
            row("binance:BTC/USDT", id = 7, position = 2),
            row("kraken:ETH/EUR", id = 3, position = 0),
            row("kraken:SOL/EUR", id = 9, position = 0),
        )
        assertEquals(listOf(3L, 9L, 7L), rows.sorted(SortMode.CUSTOM))
    }

    @Test
    fun `exchange plus pair orders by exchange name first`() {
        val rows = listOf(
            row("kraken:AAA/EUR", id = 1),
            row("binance:ZZZ/USDT", id = 2),
            row("binance:AAA/USDT", id = 3),
        )
        assertEquals(listOf(3L, 2L, 1L), rows.sorted(SortMode.EXCHANGE_PAIR))
    }

    @Test
    fun `pair plus exchange orders by base then quote then exchange`() {
        val rows = listOf(
            row("kraken:BTC/EUR", id = 1),
            row("binance:BTC/USDT", id = 2),
            row("binance:ADA/USDT", id = 3),
        )
        assertEquals(listOf(3L, 1L, 2L), rows.sorted(SortMode.PAIR_EXCHANGE))
    }

    @Test
    fun `price sorts descending and puts unknown prices last`() {
        val rows = listOf(
            row("binance:A/USDT", id = 1, position = 0, price = 10.0),
            row("binance:B/USDT", id = 2, position = 1, price = null),
            row("binance:C/USDT", id = 3, position = 2, price = 100.0),
        )
        assertEquals(listOf(3L, 1L, 2L), rows.sorted(SortMode.PRICE))
    }

    @Test
    fun `price ties keep the custom order`() {
        val rows = listOf(
            row("binance:A/USDT", id = 1, position = 5, price = 10.0),
            row("binance:B/USDT", id = 2, position = 1, price = 10.0),
        )
        assertEquals(listOf(2L, 1L), rows.sorted(SortMode.PRICE))
    }

    @Test
    fun `change sorts descending and puts unknown changes last`() {
        val rows = listOf(
            row("binance:A/USDT", id = 1, position = 0, changePct = -4.0),
            row("binance:B/USDT", id = 2, position = 1, changePct = null),
            row("binance:C/USDT", id = 3, position = 2, changePct = 2.5),
        )
        assertEquals(listOf(3L, 1L, 2L), rows.sorted(SortMode.CHANGE))
    }

    private fun List<TileRow>.sorted(sort: SortMode): List<Long> =
        sortedWith(comparator(sort)).map { it.item.id }

    private fun row(
        key: String,
        id: Long,
        position: Int = 0,
        price: Double? = null,
        changePct: Double? = null,
    ): TileRow {
        val marketKey = MarketKey(key)
        return TileRow(
            item = WatchlistItem(id = id, watchlistId = 1, key = marketKey, position = position),
            price = price,
            changePct = changePct,
            tile = TileUiState(
                itemId = id,
                key = marketKey,
                exchangeLabel = marketKey.exchange.displayName.uppercase(),
                pair = marketKey.pair,
                priceText = null,
                changeText = null,
                absChangeText = null,
                highText = null,
                lowText = null,
                volumeText = null,
                isUp = true,
                spark = null,
                accent = null,
            ),
        )
    }
}
