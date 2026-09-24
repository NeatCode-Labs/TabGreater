package com.neatcode.tabgreater.core.data.repo

import com.neatcode.tabgreater.core.data.db.ChartDrawingDao
import com.neatcode.tabgreater.core.data.db.ChartDrawingEntity
import com.neatcode.tabgreater.core.model.MarketKey
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** One row per market; an empty array is a delete, never a row holding `[]`. */
class RoomChartDrawingRepositoryTest {

    private val dao = FakeChartDrawingDao()
    private val repository = RoomChartDrawingRepository(dao)
    private val btc = MarketKey("kraken:BTC/EUR")
    private val eth = MarketKey("binance:ETH/USDT")

    @Test
    fun `save then load returns the same JSON for that market only`() = runTest {
        repository.save(btc, DRAWINGS, now = 42L)

        assertEquals(DRAWINGS, repository.load(btc))
        assertNull(repository.load(eth))
        assertEquals(ChartDrawingEntity(btc.value, DRAWINGS, 42L), dao.rows[btc.value])
    }

    @Test
    fun `a second save replaces the row`() = runTest {
        repository.save(btc, DRAWINGS, now = 1L)
        repository.save(btc, OTHER, now = 2L)

        assertEquals(OTHER, repository.load(btc))
        assertEquals(1, dao.count())
    }

    @Test
    fun `saving an empty array deletes the row`() = runTest {
        repository.save(btc, DRAWINGS)
        repository.save(btc, " [ ] ")

        assertNull(repository.load(btc))
        assertEquals(0, dao.count())
    }

    @Test
    fun `saving blank input deletes the row and never stores it`() = runTest {
        repository.save(btc, DRAWINGS)
        repository.save(btc, "")

        assertEquals(0, dao.count())
    }

    @Test
    fun `clear removes one market and leaves the others`() = runTest {
        repository.save(btc, DRAWINGS)
        repository.save(eth, OTHER)

        repository.clear(btc)

        assertNull(repository.load(btc))
        assertEquals(OTHER, repository.load(eth))
    }

    @Test
    fun `empty-array detection`() {
        assertTrue(RoomChartDrawingRepository.isEmptyArray("[]"))
        assertTrue(RoomChartDrawingRepository.isEmptyArray("\n[\n]\n"))
        assertTrue(RoomChartDrawingRepository.isEmptyArray("   "))
        assertFalse(RoomChartDrawingRepository.isEmptyArray(DRAWINGS))
        // Not an array at all: stored as given, the chart layer decides what it means.
        assertFalse(RoomChartDrawingRepository.isEmptyArray("{}"))
        assertFalse(RoomChartDrawingRepository.isEmptyArray("[not json"))
    }

    private class FakeChartDrawingDao : ChartDrawingDao {
        val rows = LinkedHashMap<String, ChartDrawingEntity>()

        override suspend fun get(key: String): ChartDrawingEntity? = rows[key]
        override suspend fun upsert(entity: ChartDrawingEntity) {
            rows[entity.marketKey] = entity
        }

        override suspend fun delete(key: String) {
            rows.remove(key)
        }

        override suspend fun deleteAll() = rows.clear()
        override suspend fun count(): Int = rows.size
    }

    private companion object {
        const val DRAWINGS =
            """[{"name":"segment","points":[{"timestamp":1727100000000,"value":63120.5},""" +
                """{"timestamp":1727200000000,"value":64000.0}],"lock":false,"mode":"weak_magnet","text":null}]"""
        const val OTHER =
            """[{"name":"priceLine","points":[{"timestamp":1727100000000,"value":2500.0}],""" +
                """"lock":true,"mode":"normal","text":null}]"""
    }
}
