package com.neatcode.tabgreater.core.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.DeleteColumn
import androidx.room.migration.AutoMigrationSpec
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

@Database(
    entities = [
        WatchlistEntity::class,
        WatchlistItemEntity::class,
        MarketEntity::class,
        CandleEntity::class,
        TickerSnapshotEntity::class,
        ChartDrawingEntity::class,
    ],
    version = 6,
    autoMigrations = [AutoMigration(from = 5, to = 6, spec = DropMarketCategory::class)],
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

@DeleteColumn(tableName = "markets", columnName = "category")
class DropMarketCategory : AutoMigrationSpec {
    override fun onPostMigrate(connection: SQLiteConnection) {
        // Older builds included the category in generated perp descriptions, even for offline or delisted rows.
        connection.execSQL(
            """
            UPDATE markets SET description =
                CASE WHEN unit IS NULL OR unit = 'USD per contract unit' THEN '' ELSE unit || ' · ' END
                || 'Perpetual contract'
                || CASE WHEN venue IS NULL OR venue IN ('', 'HyperCore') THEN '' ELSE ' · Builder venue ' || venue END
            WHERE exchange = 'hyperliquid' AND instrument_kind = 'perpetual' AND description LIKE '% perpetual contract%'
            """.trimIndent(),
        )
    }
}

/** Every explicit schema step, in order. Each one must match the exported `schemas/<version>.json`. */
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

    const val MARKETS_CATEGORY_COLUMN: String = "`category` TEXT NOT NULL DEFAULT 'crypto'"
    const val MARKETS_INSTRUMENT_KIND_COLUMN: String = "`instrument_kind` TEXT NOT NULL DEFAULT 'spot'"
    const val MARKETS_DISPLAY_NAME_COLUMN: String = "`display_name` TEXT"
    const val MARKETS_DESCRIPTION_COLUMN: String = "`description` TEXT"
    const val MARKETS_UNIT_COLUMN: String = "`unit` TEXT"
    const val MARKETS_VENUE_COLUMN: String = "`venue` TEXT"
    const val MARKETS_EVENT_ID_COLUMN: String = "`event_id` TEXT"
    const val MARKETS_OUTCOME_ID_COLUMN: String = "`outcome_id` TEXT"
    const val MARKETS_OUTCOME_SIDE_COLUMN: String = "`outcome_side` TEXT"
    const val MARKETS_EXPIRES_AT_COLUMN: String = "`expires_at` INTEGER"

    /** 3 → 4: stores every market category and the descriptive metadata required by outcome markets. */
    val MIGRATION_3_4: Migration = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            listOf(
                MARKETS_CATEGORY_COLUMN,
                MARKETS_INSTRUMENT_KIND_COLUMN,
                MARKETS_DISPLAY_NAME_COLUMN,
                MARKETS_DESCRIPTION_COLUMN,
                MARKETS_UNIT_COLUMN,
                MARKETS_VENUE_COLUMN,
                MARKETS_EVENT_ID_COLUMN,
                MARKETS_OUTCOME_ID_COLUMN,
                MARKETS_OUTCOME_SIDE_COLUMN,
                MARKETS_EXPIRES_AT_COLUMN,
            ).forEach { column -> db.execSQL("ALTER TABLE markets ADD COLUMN $column") }
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_markets_category` ON `markets` (`category`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_markets_instrument_kind` ON `markets` (`instrument_kind`)")
            // Old routing flags do not distinguish stocks from ETFs or crypto from fiat.
            db.execSQL("UPDATE markets SET category = 'unclassified', updated_at = 0")
        }
    }

    const val MARKETS_ROLLOVER_SERIES_ID_COLUMN: String = "`rollover_series_id` TEXT"

    /** 4 → 5: cache the stable family identity used to find a confirmed next daily outcome. */
    val MIGRATION_4_5: Migration = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE markets ADD COLUMN $MARKETS_ROLLOVER_SERIES_ID_COLUMN")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_markets_rollover_series_id_outcome_side_expires_at` " +
                    "ON `markets` (`rollover_series_id`, `outcome_side`, `expires_at`)",
            )
            // Existing rows lack the new series id; re-fetch the catalogue after an update.
            db.execSQL("UPDATE markets SET updated_at = 0")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
}
