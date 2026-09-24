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
    version = 2,
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

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
