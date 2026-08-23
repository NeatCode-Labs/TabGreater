package com.neatcode.tabgreater.widget

import com.neatcode.tabgreater.core.data.db.TickerSnapshotDao
import com.neatcode.tabgreater.core.data.db.TickerSnapshotEntity
import com.neatcode.tabgreater.core.live.LiveStatus
import com.neatcode.tabgreater.core.live.MarketDataRepository
import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.Ticker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Findings 16 / 22: the in-memory map is written only by the WebSocket collector and is never
 * shrunk, so a widget that trusted it stayed frozen on the last socket price through every REST
 * round. The newest timestamp has to win.
 */
class TickerResolverTest {

    private val key = MarketKey("kraken:BTC/EUR")

    @Test
    fun `the fresher REST snapshot beats a stale socket value`() = runTest {
        val resolver = TickerResolver(
            marketData = FakeMarketData(mapOf(key to ticker(last = 65_000.0, timestamp = 1_000L))),
            snapshots = FakeSnapshots(row(last = 66_000.0, timestamp = 2_000L)),
        )

        val resolved = resolver.resolve(key)

        assertEquals(66_000.0, resolved?.last)
        assertEquals(2_000L, resolved?.timestamp)
    }

    @Test
    fun `a live socket value newer than Room still wins`() = runTest {
        val resolver = TickerResolver(
            marketData = FakeMarketData(mapOf(key to ticker(last = 65_000.0, timestamp = 3_000L))),
            snapshots = FakeSnapshots(row(last = 66_000.0, timestamp = 2_000L)),
        )

        assertEquals(65_000.0, resolver.resolve(key)?.last)
    }

    @Test
    fun `a tie goes to the live value, like mergeTickers`() = runTest {
        val resolver = TickerResolver(
            marketData = FakeMarketData(mapOf(key to ticker(last = 65_000.0, timestamp = 2_000L))),
            snapshots = FakeSnapshots(row(last = 66_000.0, timestamp = 2_000L)),
        )

        assertEquals(65_000.0, resolver.resolve(key)?.last)
    }

    @Test
    fun `either source alone is used, and no source yields null`() = runTest {
        val liveOnly = TickerResolver(
            marketData = FakeMarketData(mapOf(key to ticker(last = 65_000.0, timestamp = 1_000L))),
            snapshots = FakeSnapshots(null),
        )
        assertEquals(65_000.0, liveOnly.resolve(key)?.last)

        val roomOnly = TickerResolver(
            marketData = FakeMarketData(emptyMap()),
            snapshots = FakeSnapshots(row(last = 66_000.0, timestamp = 1_000L)),
        )
        assertEquals(66_000.0, roomOnly.resolve(key)?.last)

        val neither = TickerResolver(FakeMarketData(emptyMap()), FakeSnapshots(null))
        assertNull(neither.resolve(key))
    }

    @Test
    fun `a failing database read degrades to the live value instead of throwing`() = runTest {
        val resolver = TickerResolver(
            marketData = FakeMarketData(mapOf(key to ticker(last = 65_000.0, timestamp = 1_000L))),
            snapshots = FakeSnapshots(null, fail = true),
        )

        assertEquals(65_000.0, resolver.resolve(key)?.last)
    }

    @Test
    fun `the snapshot row is mapped field by field`() = runTest {
        val resolver = TickerResolver(FakeMarketData(emptyMap()), FakeSnapshots(row(66_000.0, 2_000L)))

        val resolved = resolver.resolve(key)

        assertEquals(key, resolved?.key)
        assertEquals(64_000.0, resolved?.open24h)
        assertEquals(67_000.0, resolved?.high24h)
        assertEquals(63_000.0, resolved?.low24h)
        assertEquals(12.5, resolved?.volumeBase24h)
        assertEquals(800_000.0, resolved?.volumeQuote24h)
        assertEquals(3.125, resolved?.changePct24h)
    }

    private fun ticker(last: Double, timestamp: Long) =
        Ticker(key = key, last = last, timestamp = timestamp)

    private fun row(last: Double, timestamp: Long) = TickerSnapshotEntity(
        marketKey = key.value,
        last = last,
        open24h = 64_000.0,
        high24h = 67_000.0,
        low24h = 63_000.0,
        volumeBase24h = 12.5,
        volumeQuote24h = 800_000.0,
        changePct24h = 3.125,
        timestamp = timestamp,
    )
}

private class FakeMarketData(values: Map<MarketKey, Ticker>) : MarketDataRepository {
    override val latest: StateFlow<Map<MarketKey, Ticker>> = MutableStateFlow(values)
    override val status: Flow<LiveStatus> = flowOf(LiveStatus.LIVE)
    override fun observeTickers(keys: Set<MarketKey>): Flow<Map<MarketKey, Ticker>> = latest
    override suspend fun refresh(keys: Collection<MarketKey>) = Unit
}

private class FakeSnapshots(
    private val stored: TickerSnapshotEntity?,
    private val fail: Boolean = false,
) : TickerSnapshotDao {
    override fun observeByKeys(keys: List<String>): Flow<List<TickerSnapshotEntity>> =
        flowOf(listOfNotNull(stored))

    override suspend fun get(key: String): TickerSnapshotEntity? {
        if (fail) error("database unavailable")
        return stored?.takeIf { it.marketKey == key }
    }

    override suspend fun upsert(snapshot: TickerSnapshotEntity) = Unit
    override suspend fun upsertAll(snapshots: List<TickerSnapshotEntity>) = Unit
    override suspend fun distinctKeys(): List<String> = listOfNotNull(stored?.marketKey)
    override suspend fun deleteByKeys(keys: List<String>) = Unit
}
