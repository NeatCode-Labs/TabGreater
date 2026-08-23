package com.neatcode.tabgreater.core.live

import com.neatcode.tabgreater.core.data.db.TickerSnapshotEntity
import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.Ticker

/**
 * `ticker_snapshots` <-> [Ticker]. The table has no bid/ask columns (streams deliver them at very
 * different rates), so those stay `null` when a snapshot is read back from Room.
 */

internal fun TickerSnapshotEntity.toTicker(key: MarketKey): Ticker = Ticker(
    key = key,
    last = last,
    open24h = open24h,
    high24h = high24h,
    low24h = low24h,
    volumeBase24h = volumeBase24h,
    volumeQuote24h = volumeQuote24h,
    changePct24h = changePct24h,
    timestamp = timestamp,
)

internal fun Ticker.toSnapshotEntity(): TickerSnapshotEntity = TickerSnapshotEntity(
    marketKey = key.value,
    last = last,
    open24h = open24h,
    high24h = high24h,
    low24h = low24h,
    volumeBase24h = volumeBase24h,
    volumeQuote24h = volumeQuote24h,
    changePct24h = changePct24h,
    timestamp = timestamp,
)
