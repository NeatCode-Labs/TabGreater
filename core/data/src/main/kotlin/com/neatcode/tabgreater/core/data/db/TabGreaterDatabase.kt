package com.neatcode.tabgreater.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        WatchlistEntity::class,
        WatchlistItemEntity::class,
        MarketEntity::class,
        CandleEntity::class,
        TickerSnapshotEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class TabGreaterDatabase : RoomDatabase() {
    abstract fun watchlistDao(): WatchlistDao
    abstract fun watchlistItemDao(): WatchlistItemDao
    abstract fun marketDao(): MarketDao
    abstract fun candleDao(): CandleDao
    abstract fun tickerSnapshotDao(): TickerSnapshotDao

    companion object {
        const val NAME = "tabgreater.db"

        fun build(context: Context): TabGreaterDatabase =
            Room.databaseBuilder(context.applicationContext, TabGreaterDatabase::class.java, NAME)
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                .build()
    }
}
