package com.neatcode.tabgreater.core.exchange.kucoin

import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.Ticker
import kotlinx.serialization.Serializable

// KuCoin wire formats. REST sends every number as a string and uses `null` for "unknown symbol";
// the WebSocket snapshot channel is the one place where numbers arrive as JSON numbers.
// Declarations are `internal` (not `private`) only because they are shared with KuCoinAdapter.kt.

@Serializable
internal data class SymbolDto(
    val symbol: String,
    val baseCurrency: String = "",
    val quoteCurrency: String = "",
    val priceIncrement: String? = null,
    val enableTrading: Boolean = false,
)

/** `/api/v1/market/stats`; an unknown symbol answers HTTP 200 with every value `null`. */
@Serializable
internal data class StatsDto(
    val time: Long = 0,
    val last: String? = null,
    val buy: String? = null,
    val sell: String? = null,
    val high: String? = null,
    val low: String? = null,
    val vol: String? = null,
    val volValue: String? = null,
    val changePrice: String? = null,
    val changeRate: String? = null,
)

@Serializable
internal data class AllTickersDto(val time: Long = 0, val ticker: List<TickerRowDto> = emptyList())

@Serializable
internal data class TickerRowDto(
    val symbol: String,
    val last: String? = null,
    val buy: String? = null,
    val sell: String? = null,
    val open: String? = null,
    val high: String? = null,
    val low: String? = null,
    val vol: String? = null,
    val volValue: String? = null,
    val changeRate: String? = null,
)

@Serializable
internal data class BulletDto(
    val token: String = "",
    val instanceServers: List<InstanceServerDto> = emptyList(),
)

@Serializable
internal data class InstanceServerDto(val endpoint: String = "", val pingInterval: Long? = null)

/** `subject = trade.ticker`: price and top of book only, no 24 h statistics. */
@Serializable
internal data class TickerFrameDto(
    val price: String? = null,
    val bestBid: String? = null,
    val bestAsk: String? = null,
    val time: Long = 0,
)

/** `subject = trade.snapshot`: the payload nests a second `data` object. */
@Serializable
internal data class SnapshotFrameDto(val data: SnapshotDataDto? = null)

@Serializable
internal data class SnapshotDataDto(
    val lastTradedPrice: Double? = null,
    val close: Double? = null,
    val open: Double? = null,
    val high: Double? = null,
    val low: Double? = null,
    val vol: Double? = null,
    val volValue: Double? = null,
    val buy: Double? = null,
    val sell: Double? = null,
    val changeRate: Double? = null,
    val datetime: Long = 0,
)

@Serializable
internal data class CandlesFrameDto(val symbol: String = "", val candles: List<String> = emptyList())

/** 24 h statistics cached from the snapshot channel so a bare ticker frame can be completed. */
internal data class SnapshotStats(
    val open: Double?,
    val high: Double?,
    val low: Double?,
    val volumeBase: Double?,
    val volumeQuote: Double?,
    val changeRate: Double?,
)

private const val PERCENT = 100.0

/**
 * Signed 24 h change. The open is preferred so REST and WebSocket agree on the number;
 * KuCoin's own `changeRate` is a fraction (`-0.001` = −0.10 %) and only fills in when no open is
 * known. A zero open means "unknown", not "−100 %".
 */
private fun changePct(last: Double, open: Double?, changeRate: Double?): Double? = when {
    open == null -> changeRate?.times(PERCENT)
    open == 0.0 -> null
    else -> (last - open) / open * PERCENT
}

internal fun StatsDto.toTicker(key: MarketKey): Ticker? {
    val lastPrice = last?.toDoubleOrNull() ?: return null
    // /market/stats has no open price, but `changePrice` is `last - open`.
    val open = changePrice?.toDoubleOrNull()?.let { lastPrice - it }
    return Ticker(
        key = key,
        last = lastPrice,
        open24h = open,
        high24h = high?.toDoubleOrNull(),
        low24h = low?.toDoubleOrNull(),
        volumeBase24h = vol?.toDoubleOrNull(),
        volumeQuote24h = volValue?.toDoubleOrNull(),
        changePct24h = changePct(lastPrice, open, changeRate?.toDoubleOrNull()),
        bid = buy?.toDoubleOrNull(),
        ask = sell?.toDoubleOrNull(),
        timestamp = if (time > 0) time else System.currentTimeMillis(),
    )
}

internal fun TickerRowDto.toTicker(key: MarketKey, time: Long): Ticker? {
    val lastPrice = last?.toDoubleOrNull() ?: return null
    val open = open?.toDoubleOrNull()
    return Ticker(
        key = key,
        last = lastPrice,
        open24h = open,
        high24h = high?.toDoubleOrNull(),
        low24h = low?.toDoubleOrNull(),
        volumeBase24h = vol?.toDoubleOrNull(),
        volumeQuote24h = volValue?.toDoubleOrNull(),
        changePct24h = changePct(lastPrice, open, changeRate?.toDoubleOrNull()),
        bid = buy?.toDoubleOrNull(),
        ask = sell?.toDoubleOrNull(),
        timestamp = if (time > 0) time else System.currentTimeMillis(),
    )
}

internal fun TickerFrameDto.toTicker(key: MarketKey, stats: SnapshotStats?): Ticker? {
    val lastPrice = price?.toDoubleOrNull() ?: return null
    return Ticker(
        key = key,
        last = lastPrice,
        open24h = stats?.open,
        high24h = stats?.high,
        low24h = stats?.low,
        volumeBase24h = stats?.volumeBase,
        volumeQuote24h = stats?.volumeQuote,
        changePct24h = changePct(lastPrice, stats?.open, stats?.changeRate),
        bid = bestBid?.toDoubleOrNull(),
        ask = bestAsk?.toDoubleOrNull(),
        timestamp = if (time > 0) time else System.currentTimeMillis(),
    )
}

internal fun SnapshotDataDto.toStats(): SnapshotStats =
    SnapshotStats(open, high, low, vol, volValue, changeRate)

internal fun SnapshotDataDto.toTicker(key: MarketKey, stats: SnapshotStats): Ticker? {
    val lastPrice = lastTradedPrice ?: close ?: return null
    return Ticker(
        key = key,
        last = lastPrice,
        open24h = stats.open,
        high24h = stats.high,
        low24h = stats.low,
        volumeBase24h = stats.volumeBase,
        volumeQuote24h = stats.volumeQuote,
        changePct24h = changePct(lastPrice, stats.open, stats.changeRate),
        bid = buy,
        ask = sell,
        timestamp = if (datetime > 0) datetime else System.currentTimeMillis(),
    )
}
