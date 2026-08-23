package com.neatcode.tabgreater.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "watchlists")
data class WatchlistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val position: Int,
    /** [com.neatcode.tabgreater.core.model.SparkPeriod.id] */
    val period: String = "24h",
    /** [com.neatcode.tabgreater.core.model.TileSize.id] */
    @ColumnInfo(name = "tile_size") val tileSize: String = "small",
    /** [com.neatcode.tabgreater.core.model.SortMode.id] */
    val sort: String = "custom",
)

@Entity(
    tableName = "watchlist_items",
    foreignKeys = [
        ForeignKey(
            entity = WatchlistEntity::class,
            parentColumns = ["id"],
            childColumns = ["watchlist_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("watchlist_id"),
        Index(value = ["watchlist_id", "market_key"], unique = true),
    ],
)
data class WatchlistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "watchlist_id") val watchlistId: Long,
    /** Canonical key `"exchange:BASE/QUOTE"`. */
    @ColumnInfo(name = "market_key") val marketKey: String,
    val position: Int,
    @ColumnInfo(name = "accent_color") val accentColor: Long? = null,
)

/** Cached instrument list per exchange (refreshed daily / on demand). */
@Entity(
    tableName = "markets",
    indices = [Index("exchange"), Index("base"), Index("quote")],
)
data class MarketEntity(
    @PrimaryKey @ColumnInfo(name = "market_key") val marketKey: String,
    val exchange: String,
    val base: String,
    val quote: String,
    @ColumnInfo(name = "native_symbol") val nativeSymbol: String,
    @ColumnInfo(name = "price_precision") val pricePrecision: Int,
    @ColumnInfo(name = "tick_size") val tickSize: Double?,
    val active: Boolean,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** Candle cache so tiles and charts are never empty on cold start. */
@Entity(
    tableName = "candles",
    primaryKeys = ["market_key", "timeframe", "open_time"],
    indices = [Index(value = ["market_key", "timeframe", "open_time"])],
)
data class CandleEntity(
    @ColumnInfo(name = "market_key") val marketKey: String,
    /** [com.neatcode.tabgreater.core.model.Timeframe.id] */
    val timeframe: String,
    @ColumnInfo(name = "open_time") val openTime: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
)

/** Last known ticker per market, so the grid renders instantly before the socket connects. */
@Entity(tableName = "ticker_snapshots")
data class TickerSnapshotEntity(
    @PrimaryKey @ColumnInfo(name = "market_key") val marketKey: String,
    val last: Double,
    @ColumnInfo(name = "open_24h") val open24h: Double?,
    @ColumnInfo(name = "high_24h") val high24h: Double?,
    @ColumnInfo(name = "low_24h") val low24h: Double?,
    @ColumnInfo(name = "volume_base_24h") val volumeBase24h: Double?,
    @ColumnInfo(name = "volume_quote_24h") val volumeQuote24h: Double?,
    @ColumnInfo(name = "change_pct_24h") val changePct24h: Double?,
    val timestamp: Long,
)
