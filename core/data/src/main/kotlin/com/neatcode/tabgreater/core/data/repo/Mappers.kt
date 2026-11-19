package com.neatcode.tabgreater.core.data.repo

import com.neatcode.tabgreater.core.data.db.CandleEntity
import com.neatcode.tabgreater.core.data.db.MarketEntity
import com.neatcode.tabgreater.core.data.db.WatchlistEntity
import com.neatcode.tabgreater.core.data.db.WatchlistItemEntity
import com.neatcode.tabgreater.core.model.AssetClass
import com.neatcode.tabgreater.core.model.Candle
import com.neatcode.tabgreater.core.model.ExchangeId
import com.neatcode.tabgreater.core.model.Market
import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.MarketSeriesId
import com.neatcode.tabgreater.core.model.InstrumentKind
import com.neatcode.tabgreater.core.model.SortMode
import com.neatcode.tabgreater.core.model.SparkPeriod
import com.neatcode.tabgreater.core.model.TileSize
import com.neatcode.tabgreater.core.model.Timeframe
import com.neatcode.tabgreater.core.model.Watchlist
import com.neatcode.tabgreater.core.model.WatchlistItem
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

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
    val classifiedAsset = AssetClass.fromId(assetClass)
    val storedInstrumentKind = InstrumentKind.fromId(instrumentKind)
    val legacyFields = if (rolloverSeriesId == null) legacyDailyPriceBinaryFields() else null
    val legacyExpiry = expiresAt ?: legacyFields?.get("expiry")?.let(::parseLegacyOutcomeExpiry)
    val fallbackSeriesId = legacyFields?.let { fields ->
        MarketSeriesId.dailyPriceBinary(
            underlying = fields["underlying"],
            period = fields["period"],
            targetPrice = fields["targetPrice"],
            expiresAtEpochMs = legacyExpiry,
        )
    }
    return Market(
        key = key,
        nativeSymbol = nativeSymbol,
        pricePrecision = pricePrecision,
        tickSize = tickSize,
        active = active,
        assetClass = classifiedAsset,
        underlying = underlying,
        instrumentKind = storedInstrumentKind,
        displayName = displayName,
        description = description,
        unit = unit,
        venue = venue,
        eventId = eventId,
        outcomeId = outcomeId,
        outcomeSide = outcomeSide,
        expiresAt = legacyExpiry,
        rolloverSeriesId = rolloverSeriesId ?: fallbackSeriesId,
    )
}

/** Old outcome rows can outlive the API listing; accept only a whole protocol class field. */
private fun MarketEntity.legacyDailyPriceBinaryFields(): Map<String, String>? {
    if (exchange != ExchangeId.HYPERLIQUID.id || instrumentKind != InstrumentKind.OUTCOME.id) return null
    return description.orEmpty().split(" · ").asSequence().mapNotNull { segment ->
        val fields = segment.split('|').mapNotNull { field ->
            val divider = field.indexOf(':')
            if (divider <= 0) null else field.substring(0, divider).trim() to field.substring(divider + 1).trim()
        }.toMap()
        fields.takeIf { it["class"]?.equals("priceBinary", ignoreCase = true) == true }
    }.firstOrNull()
}

private fun parseLegacyOutcomeExpiry(raw: String): Long? = runCatching {
    LocalDateTime.parse(raw, LEGACY_OUTCOME_EXPIRY).toInstant(ZoneOffset.UTC).toEpochMilli()
}.getOrNull()

private val LEGACY_OUTCOME_EXPIRY = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")

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
    assetClass = assetClass.id,
    underlying = underlying,
    instrumentKind = instrumentKind.id,
    displayName = displayName,
    description = description,
    unit = unit,
    venue = venue,
    eventId = eventId,
    outcomeId = outcomeId,
    outcomeSide = outcomeSide,
    expiresAt = expiresAt,
    rolloverSeriesId = rolloverSeriesId,
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
