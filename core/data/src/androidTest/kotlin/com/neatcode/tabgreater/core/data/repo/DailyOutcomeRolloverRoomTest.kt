package com.neatcode.tabgreater.core.data.repo

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.neatcode.tabgreater.core.data.db.DatabaseMigrations
import com.neatcode.tabgreater.core.data.db.RoomTransactionRunner
import com.neatcode.tabgreater.core.data.db.TabGreaterDatabase
import com.neatcode.tabgreater.core.exchange.ExchangeAdapter
import com.neatcode.tabgreater.core.exchange.ExchangeRegistry
import com.neatcode.tabgreater.core.model.Candle
import com.neatcode.tabgreater.core.model.ExchangeId
import com.neatcode.tabgreater.core.model.Market
import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.MarketSeriesId
import com.neatcode.tabgreater.core.model.InstrumentKind
import com.neatcode.tabgreater.core.model.Ticker
import com.neatcode.tabgreater.core.model.Timeframe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DailyOutcomeRolloverRoomTest {

    @Test
    fun v4LegacyDailyOutcomesRollAfterConfirmation() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val databaseName = "rollover-${UUID.randomUUID()}.db"
        context.deleteDatabase(databaseName)
        var database: TabGreaterDatabase? = null
        try {
            val expiry = Instant.parse("2026-11-20T06:00:00Z").toEpochMilli()
            val nextExpiry = expiry + DAY_MS
            val oldBtcKey = MarketKey("hyperliquid:OUTCOME_95_SIDE_0/USDC")
            val oldSolKey = MarketKey("hyperliquid:OUTCOME_96_SIDE_1/USDC")
            val nextBtcKey = MarketKey("hyperliquid:OUTCOME_105_SIDE_0/USDC")
            val nextSolKey = MarketKey("hyperliquid:OUTCOME_106_SIDE_1/USDC")
            val btcSeries = MarketSeriesId.dailyPriceBinary("BTC", "1d", "63330", expiry)!!
            val solSeries = MarketSeriesId.dailyPriceBinary("SOL", "1d", "145", expiry)!!

            createAndUpgradeLegacyDatabase(
                instrumentation = instrumentation,
                databaseName = databaseName,
                oldBtcKey = oldBtcKey,
                oldSolKey = oldSolKey,
                expiry = expiry,
            )

            val unrelated = Market(
                key = MarketKey("hyperliquid:PERP_HYPE/USD"),
                nativeSymbol = "HYPE",
                pricePrecision = 2,
                instrumentKind = InstrumentKind.PERPETUAL,
            )
            val nextBtc = dailyOutcome(nextBtcKey, "105", "Yes", nextExpiry, btcSeries, "64000")
            val nextSol = dailyOutcome(nextSolKey, "106", "No", nextExpiry, solSeries, "150")
            val adapter = SequenceHyperliquidAdapter(
                snapshots = listOf(
                    listOf(unrelated), // First post-expiry fetch: neither next market is listed.
                    listOf(unrelated, nextBtc, nextSol), // Retry confirms the actual next contracts.
                ),
            )
            var now = expiry
            val opened = Room.databaseBuilder(context, TabGreaterDatabase::class.java, databaseName)
                .addMigrations(*DatabaseMigrations.ALL)
                .allowMainThreadQueries()
                .build()
            database = opened
            val marketRepository = RoomMarketRepository(
                marketDao = opened.marketDao(),
                registry = ExchangeRegistry(listOf(adapter)),
                now = { now },
            )
            val watchlists = RoomWatchlistRepository(
                opened.watchlistDao(),
                opened.watchlistItemDao(),
                RoomTransactionRunner(opened),
            )

            val btcBefore = watchlists.observeItems(1L).first()
            val solBefore = watchlists.observeItems(2L).first()
            assertEquals(oldBtcKey, btcBefore.single().key)
            assertEquals(oldSolKey, solBefore.single().key)
            assertEquals(btcSeries, marketRepository.getMarket(oldBtcKey)?.rolloverSeriesId)
            assertEquals(solSeries, marketRepository.getMarket(oldSolKey)?.rolloverSeriesId)

            assertTrue(marketRepository.refreshMarkets(ExchangeId.HYPERLIQUID, force = true).isSuccess)
            assertEquals(1, adapter.listMarketsCalls)
            assertNull(marketRepository.nextDailyOutcomeMarket(marketRepository.getMarket(oldBtcKey)!!, now))
            assertNull(marketRepository.nextDailyOutcomeMarket(marketRepository.getMarket(oldSolKey)!!, now))
            assertEquals(oldBtcKey, watchlists.observeItems(1L).first().single().key)
            assertEquals(oldSolKey, watchlists.observeItems(2L).first().single().key)

            now += RETRY_GAP_MS
            assertTrue(marketRepository.refreshMarkets(ExchangeId.HYPERLIQUID, force = true).isSuccess)
            assertEquals(2, adapter.listMarketsCalls)
            val btcCurrent = marketRepository.getMarket(oldBtcKey)!!
            val solCurrent = marketRepository.getMarket(oldSolKey)!!
            val btcNext = marketRepository.nextDailyOutcomeMarket(btcCurrent, now)!!
            val solNext = marketRepository.nextDailyOutcomeMarket(solCurrent, now)!!
            assertEquals(nextBtcKey, btcNext.key)
            assertEquals(nextSolKey, solNext.key)
            assertEquals(nextExpiry, btcNext.expiresAt)
            assertEquals(nextExpiry, solNext.expiresAt)
            assertEquals("Yes", btcNext.outcomeSide)
            assertEquals("No", solNext.outcomeSide)

            assertTrue(watchlists.rolloverItem(btcBefore.single().id, oldBtcKey, btcNext.key))
            assertTrue(watchlists.rolloverItem(solBefore.single().id, oldSolKey, solNext.key))

            val btcAfter = watchlists.observeItems(1L).first().single()
            val solAfter = watchlists.observeItems(2L).first().single()
            assertEquals(btcBefore.single().id, btcAfter.id)
            assertEquals(btcBefore.single().position, btcAfter.position)
            assertEquals(btcBefore.single().accentColor, btcAfter.accentColor)
            assertEquals(solBefore.single().id, solAfter.id)
            assertEquals(solBefore.single().position, solAfter.position)
            assertEquals(solBefore.single().accentColor, solAfter.accentColor)
            assertNotEquals(oldBtcKey, btcAfter.key)
            assertNotEquals(oldSolKey, solAfter.key)
            assertEquals(nextBtcKey, btcAfter.key)
            assertEquals(nextSolKey, solAfter.key)
            assertEquals("Yes", marketRepository.getMarket(btcAfter.key)?.outcomeSide)
            assertEquals("No", marketRepository.getMarket(solAfter.key)?.outcomeSide)

            val snapshots = opened.tickerSnapshotDao()
            assertEquals(63_330.0, snapshots.get(oldBtcKey.value)!!.last, 0.0)
            assertNull(snapshots.get(btcAfter.key.value))
            val oldCandles = opened.candleDao().latest(oldBtcKey.value, Timeframe.M1.id, 10)
            assertEquals(1, oldCandles.size)
            assertTrue(opened.candleDao().latest(btcAfter.key.value, Timeframe.M1.id, 10).isEmpty())
            assertFalse(adapter.snapshots.last().any { it.key == oldBtcKey || it.key == oldSolKey })
        } finally {
            database?.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun createAndUpgradeLegacyDatabase(
        instrumentation: android.app.Instrumentation,
        databaseName: String,
        oldBtcKey: MarketKey,
        oldSolKey: MarketKey,
        expiry: Long,
    ) {
        val helper = MigrationTestHelper(
            instrumentation,
            TabGreaterDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )
        helper.createDatabase(databaseName, 4).use { v4 ->
            v4.execSQL(
                "INSERT INTO `watchlists` (`id`,`name`,`position`,`period`,`tile_size`,`sort`) VALUES (1,'Main',0,'24h','small','custom')",
            )
            v4.execSQL(
                "INSERT INTO `watchlists` (`id`,`name`,`position`,`period`,`tile_size`,`sort`) VALUES (2,'Alts',1,'24h','small','custom')",
            )
            v4.execSQL(
                "INSERT INTO `watchlist_items` (`id`,`watchlist_id`,`market_key`,`position`,`accent_color`) VALUES (501,1,?,0,?)",
                arrayOf<Any?>(oldBtcKey.value, 0xFF38A4D8L),
            )
            v4.execSQL(
                "INSERT INTO `watchlist_items` (`id`,`watchlist_id`,`market_key`,`position`,`accent_color`) VALUES (601,2,?,0,?)",
                arrayOf<Any?>(oldSolKey.value, 0xFFB877D8L),
            )
            insertLegacyMarket(v4, oldBtcKey, "95", "Yes", "BTC", "63330", expiry)
            insertLegacyMarket(v4, oldSolKey, "96", "No", "SOL", "145", expiry)
            v4.execSQL(
                "INSERT INTO `ticker_snapshots` (`market_key`,`last`,`open_24h`,`high_24h`,`low_24h`,`volume_base_24h`,`volume_quote_24h`,`change_pct_24h`,`timestamp`) VALUES (?,63330,62000,64000,61000,1,63330,2,?)",
                arrayOf<Any?>(oldBtcKey.value, expiry - 1_000L),
            )
            v4.execSQL(
                "INSERT INTO `candles` (`market_key`,`timeframe`,`open_time`,`open`,`high`,`low`,`close`,`volume`) VALUES (?,'1m',?,63200,63400,63100,63330,0.2)",
                arrayOf<Any?>(oldBtcKey.value, expiry - 60_000L),
            )
        }
        helper.runMigrationsAndValidate(databaseName, 5, true, DatabaseMigrations.MIGRATION_4_5).close()
    }

    private fun insertLegacyMarket(
        db: SupportSQLiteDatabase,
        key: MarketKey,
        outcomeId: String,
        side: String,
        underlying: String,
        targetPrice: String,
        expiry: Long,
    ) {
        val rawDescription =
            "class:priceBinary|underlying:$underlying|expiry:20261120-0600|targetPrice:$targetPrice|period:1d"
        db.execSQL(
            """
            INSERT INTO `markets` (`market_key`,`exchange`,`base`,`quote`,`native_symbol`,`price_precision`,
                `tick_size`,`active`,`updated_at`,`asset_class`,`underlying`,`category`,`instrument_kind`,
                `display_name`,`description`,`unit`,`venue`,`event_id`,`outcome_id`,`outcome_side`,`expires_at`)
            VALUES (?,?,?,?,?,4,NULL,1,?,'crypto',?,'events','outcome',?,?,?,'HyperCore outcomes',NULL,?,?,?)
            """.trimIndent(),
            arrayOf<Any?>(
                key.value,
                ExchangeId.HYPERLIQUID.id,
                key.base,
                key.quote,
                "#${outcomeId}${if (side == "No") 1 else 0}",
                expiry - 60_000L,
                underlying,
                "$underlying price binary · $side",
                rawDescription,
                "USDC per outcome token",
                outcomeId,
                side,
                expiry,
            ),
        )
    }

    private fun dailyOutcome(
        key: MarketKey,
        outcomeId: String,
        side: String,
        expiry: Long,
        seriesId: String,
        targetPrice: String,
    ) = Market(
        key = key,
        nativeSymbol = "#${outcomeId}${if (side == "No") 1 else 0}",
        pricePrecision = 4,
        instrumentKind = InstrumentKind.OUTCOME,
        displayName = "Daily priceBinary · $side",
        description = "class:priceBinary|underlying:${key.base}|expiry:${Instant.ofEpochMilli(expiry)}|targetPrice:$targetPrice|period:1d",
        unit = "USDC per outcome token",
        venue = "HyperCore outcomes",
        outcomeId = outcomeId,
        outcomeSide = side,
        expiresAt = expiry,
        rolloverSeriesId = seriesId,
    )

    private class SequenceHyperliquidAdapter(val snapshots: List<List<Market>>) : ExchangeAdapter {
        override val id = ExchangeId.HYPERLIQUID
        override val nativeTimeframes = setOf(Timeframe.M1)
        var listMarketsCalls = 0
            private set

        override suspend fun listMarkets(): List<Market> {
            val index = listMarketsCalls++.coerceAtMost(snapshots.lastIndex)
            return snapshots[index]
        }

        override suspend fun fetchTickers(markets: List<Market>): List<Ticker> = emptyList()
        override suspend fun fetchOHLCV(market: Market, timeframe: Timeframe, endTime: Long?, limit: Int): List<Candle> = emptyList()
        override fun watchTickers(markets: List<Market>): Flow<Ticker> = emptyFlow()
        override fun watchKlines(market: Market, timeframe: Timeframe): Flow<Candle> = emptyFlow()
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1_000
        const val RETRY_GAP_MS = 5L * 60 * 1_000
    }
}
