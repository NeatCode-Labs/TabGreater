package com.neatcode.tabgreater.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        WatchlistEntity::class,
        WatchlistItemEntity::class,
        MarketEntity::class,
        CandleEntity::class,
        TickerSnapshotEntity::class,
        ChartDrawingEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class TabGreaterDatabase : RoomDatabase() {
    abstract fun watchlistDao(): WatchlistDao
    abstract fun watchlistItemDao(): WatchlistItemDao
    abstract fun marketDao(): MarketDao
    abstract fun candleDao(): CandleDao
    abstract fun tickerSnapshotDao(): TickerSnapshotDao
    abstract fun chartDrawingDao(): ChartDrawingDao

    companion object {
        const val NAME = "tabgreater.db"

        fun build(context: Context): TabGreaterDatabase =
            Room.databaseBuilder(context.applicationContext, TabGreaterDatabase::class.java, NAME)
                // Upgrades are always explicit: the watchlists and the drawings are user data, so
                // a missing migration must fail loudly in testing rather than wipe them.
                .addMigrations(*DatabaseMigrations.ALL)
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                .build()
    }
}

/** Every schema step, in order. Each one must match the exported `schemas/<version>.json`. */
object DatabaseMigrations {

    /**
     * `CREATE TABLE` of [ChartDrawingEntity], byte for byte what Room generates for it (the
     * `createSql` of `schemas/…/2.json`); a unit test compares the two.
     */
    const val CREATE_CHART_DRAWINGS: String =
        "CREATE TABLE IF NOT EXISTS `chart_drawings` (`market_key` TEXT NOT NULL, " +
            "`drawings` TEXT NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`market_key`))"

    /** 1 → 2: chart drawings per market. Adds a table; every existing row is left untouched. */
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(CREATE_CHART_DRAWINGS)
        }
    }

    /**
     * Columns [MIGRATION_2_3] appends to `markets`, byte for byte as they appear in the
     * `createSql` of `schemas/…/3.json` (a unit test compares the two), so an upgraded table has
     * the same definition, default included, as one a fresh install creates.
     */
    const val MARKETS_ASSET_CLASS_COLUMN: String = "`asset_class` TEXT NOT NULL DEFAULT 'crypto'"
    const val MARKETS_UNDERLYING_COLUMN: String = "`underlying` TEXT"

    /**
     * 2 → 3: stock tokens. `markets` gains the asset class (every existing row reads as crypto)
     * and the underlying share ticker. The cached catalogue is then stamped as never refreshed,
     * otherwise the 24 h freshness gate of
     * [com.neatcode.tabgreater.core.data.repo.RoomMarketRepository.refreshMarkets] would keep the
     * rows unclassified for up to a day. A refresh rewrites or deletes every row of its exchange
     * anyway, and an exchange that cannot be reached keeps its rows as before.
     */
    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE markets ADD COLUMN $MARKETS_ASSET_CLASS_COLUMN")
            db.execSQL("ALTER TABLE markets ADD COLUMN $MARKETS_UNDERLYING_COLUMN")
            db.execSQL("UPDATE markets SET updated_at = 0")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
