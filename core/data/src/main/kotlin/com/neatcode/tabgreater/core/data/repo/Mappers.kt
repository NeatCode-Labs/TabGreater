package com.neatcode.tabgreater.core.data.repo

import com.neatcode.tabgreater.core.data.db.CandleEntity
import com.neatcode.tabgreater.core.data.db.MarketEntity
import com.neatcode.tabgreater.core.data.db.WatchlistEntity
import com.neatcode.tabgreater.core.data.db.WatchlistItemEntity
import com.neatcode.tabgreater.core.model.Candle
import com.neatcode.tabgreater.core.model.Market
import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.SortMode
import com.neatcode.tabgreater.core.model.SparkPeriod
import com.neatcode.tabgreater.core.model.TileSize
import com.neatcode.tabgreater.core.model.Timeframe
import com.neatcode.tabgreater.core.model.Watchlist
import com.neatcode.tabgreater.core.model.WatchlistItem

/**
 * Entity <-> model conversions. Rows whose `market_key` no longer parses (e.g. after an exchange
 * is dropped from the build) are skipped by the callers instead of crashing the flow.
 */

internal fun WatchlistEntity.toModel(): Watchlist = Watchlist(
    id = id,
    name = name,
    position = position,
    period = SparkPeriod.fromId(period),
    tileSize = TileSize.fromId(tileSize),
    sort = SortMode.fromId(sort),
)

internal fun WatchlistItemEntity.toModelOrNull(): WatchlistItem? {
    val key = MarketKey.parseOrNull(marketKey) ?: return null
    return WatchlistItem(
        id = id,
        watchlistId = watchlistId,
        key = key,
        position = position,
        accentColor = accentColor,
    )
}

internal fun MarketEntity.toModelOrNull(): Market? {
    val key = MarketKey.parseOrNull(marketKey) ?: return null
    return Market(
        key = key,
        nativeSymbol = nativeSymbol,
        pricePrecision = pricePrecision,
        tickSize = tickSize,
        active = active,
    )
}

internal fun Market.toEntity(updatedAt: Long): MarketEntity = MarketEntity(
    marketKey = key.value,
    exchange = key.exchange.id,
    base = key.base,
    quote = key.quote,
    nativeSymbol = nativeSymbol,
    pricePrecision = pricePrecision,
    tickSize = tickSize,
    active = active,
    updatedAt = updatedAt,
)

internal fun CandleEntity.toModel(): Candle = Candle(
    openTime = openTime,
    open = open,
    high = high,
    low = low,
    close = close,
    volume = volume,
)

internal fun Candle.toEntity(key: MarketKey, timeframe: Timeframe): CandleEntity = CandleEntity(
    marketKey = key.value,
    timeframe = timeframe.id,
    openTime = openTime,
    open = open,
    high = high,
    low = low,
    close = close,
    volume = volume,
)
