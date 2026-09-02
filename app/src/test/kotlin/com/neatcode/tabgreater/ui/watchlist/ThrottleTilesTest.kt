package com.neatcode.tabgreater.ui.watchlist

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [throttleTiles] under `runTest`'s virtual clock: the assertions are about *when* a tile stops
 * showing the value the grid came back with, which is what the unlock delay was made of.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ThrottleTilesTest {

    private val period = 5_000L

    /** A value with a drawn half and one the tile ignores, the way `Ticker` carries its timestamp. */
    private data class Quote(val price: Int, val stamp: Int = 0)

    private fun Flow<Map<String, Quote>>.tiles(): Flow<Map<String, Quote>> =
        throttleTiles({ shown, next -> shown.price != next.price }) { period }

    @Test
    fun `the value that first redraws a tile is not held`() = runTest {
        val upstream = MutableSharedFlow<Map<String, Quote>>()
        upstream.tiles().test {
            runCurrent()
            upstream.emit(mapOf("btc" to Quote(100)))
            assertEquals(mapOf("btc" to Quote(100)), awaitItem())

            // The answer from the network, 1 s into the window the stale snapshot opened.
            delay(1_000)
            upstream.emit(mapOf("btc" to Quote(101)))
            assertEquals(mapOf("btc" to Quote(101)), awaitItem())
            assertEquals(1_000L, testScheduler.currentTime)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the next sample of the same tile waits out the window`() = runTest {
        val upstream = MutableSharedFlow<Map<String, Quote>>()
        upstream.tiles().test {
            runCurrent()
            upstream.emit(mapOf("btc" to Quote(100)))
            assertEquals(mapOf("btc" to Quote(100)), awaitItem())

            delay(1_000)
            upstream.emit(mapOf("btc" to Quote(101)))
            assertEquals(mapOf("btc" to Quote(101)), awaitItem())

            // The free frame is spent; from here the refresh rate governs again, in the window
            // the pass through opened.
            upstream.emit(mapOf("btc" to Quote(102)))
            delay(1_000)
            expectNoEvents()
            delay(4_000)
            assertEquals(mapOf("btc" to Quote(102)), awaitItem())
            assertEquals(6_000L, testScheduler.currentTime)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a change the tile does not draw leaves the free frame armed`() = runTest {
        val upstream = MutableSharedFlow<Map<String, Quote>>()
        upstream.tiles().test {
            runCurrent()
            upstream.emit(mapOf("btc" to Quote(100)))
            assertEquals(mapOf("btc" to Quote(100)), awaitItem())

            // A tick with a new timestamp and the same price: nothing on the tile moves, so it
            // must not cost the market the frame the fresh price is waiting for.
            delay(400)
            upstream.emit(mapOf("btc" to Quote(100, stamp = 1)))
            delay(400)
            expectNoEvents()

            upstream.emit(mapOf("btc" to Quote(101)))
            assertEquals(mapOf("btc" to Quote(101)), awaitItem())
            assertEquals(800L, testScheduler.currentTime)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `every market gets its own free frame`() = runTest {
        val upstream = MutableSharedFlow<Map<String, Quote>>()
        upstream.tiles().test {
            runCurrent()
            upstream.emit(mapOf("a" to Quote(1), "b" to Quote(1)))
            assertEquals(mapOf("a" to Quote(1), "b" to Quote(1)), awaitItem())

            delay(500)
            upstream.emit(mapOf("a" to Quote(2), "b" to Quote(1)))
            assertEquals(mapOf("a" to Quote(2), "b" to Quote(1)), awaitItem())
            assertEquals(500L, testScheduler.currentTime)

            // b's own frame: one market's answer must not spend the frame of the market next to it.
            delay(500)
            upstream.emit(mapOf("a" to Quote(2), "b" to Quote(2)))
            assertEquals(mapOf("a" to Quote(2), "b" to Quote(2)), awaitItem())
            assertEquals(1_000L, testScheduler.currentTime)

            // Both frames are spent, so this is an ordinary sample again.
            delay(500)
            upstream.emit(mapOf("a" to Quote(3), "b" to Quote(2)))
            expectNoEvents()
            delay(4_500)
            assertEquals(mapOf("a" to Quote(3), "b" to Quote(2)), awaitItem())
            assertEquals(6_000L, testScheduler.currentTime)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a key that only just appeared does not spend its free frame`() = runTest {
        val upstream = MutableSharedFlow<Map<String, Quote>>()
        upstream.tiles().test {
            runCurrent()
            upstream.emit(emptyMap())
            assertEquals(emptyMap<String, Quote>(), awaitItem())

            // How `observeEach` fills the map: the key appears carrying its cached window ...
            delay(100)
            upstream.emit(mapOf("a" to Quote(1)))
            assertEquals(mapOf("a" to Quote(1)), awaitItem())

            // ... and the first live candle replacing it still finds the frame unspent.
            delay(100)
            upstream.emit(mapOf("a" to Quote(2)))
            assertEquals(mapOf("a" to Quote(2)), awaitItem())
            assertEquals(200L, testScheduler.currentTime)

            delay(100)
            upstream.emit(mapOf("a" to Quote(3)))
            expectNoEvents()
            delay(4_900)
            assertEquals(mapOf("a" to Quote(3)), awaitItem())
            assertEquals(5_200L, testScheduler.currentTime)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a market that leaves the map is armed again when it comes back`() = runTest {
        val upstream = MutableSharedFlow<Map<String, Quote>>()
        upstream.tiles().test {
            runCurrent()
            upstream.emit(mapOf("a" to Quote(1)))
            assertEquals(mapOf("a" to Quote(1)), awaitItem())
            delay(1_000)
            upstream.emit(mapOf("a" to Quote(2)))
            assertEquals(mapOf("a" to Quote(2)), awaitItem())

            // Candle updates are rare, so a watchlist is edited with no window running: the
            // removal and the re-add are both emitted without the gate being asked anything.
            delay(period + 1)
            upstream.emit(emptyMap())
            assertEquals(emptyMap<String, Quote>(), awaitItem())
            upstream.emit(mapOf("a" to Quote(10)))
            assertEquals(mapOf("a" to Quote(10)), awaitItem())

            // The re-added market is a new tile, so its first fresh value is not another sample.
            upstream.emit(mapOf("a" to Quote(11)))
            assertEquals(mapOf("a" to Quote(11)), awaitItem())
            assertEquals(6_001L, testScheduler.currentTime)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a growing key set still paints at once`() = runTest {
        val upstream = MutableSharedFlow<Map<String, Quote>>()
        upstream.tiles().test {
            runCurrent()
            upstream.emit(mapOf("a" to Quote(1)))
            assertEquals(mapOf("a" to Quote(1)), awaitItem())

            upstream.emit(mapOf("a" to Quote(1), "b" to Quote(1)))
            assertEquals(mapOf("a" to Quote(1), "b" to Quote(1)), awaitItem())

            upstream.emit(mapOf("a" to Quote(1), "b" to Quote(1), "c" to Quote(1)))
            assertEquals(mapOf("a" to Quote(1), "b" to Quote(1), "c" to Quote(1)), awaitItem())
            assertEquals(0L, testScheduler.currentTime)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the free frames are re-armed on the next collection`() = runTest {
        val upstream = MutableSharedFlow<Map<String, Quote>>()
        // The same flow collected twice, because that is what the grid does with one pipeline.
        val tiles = upstream.tiles()
        tiles.test {
            runCurrent()
            upstream.emit(mapOf("a" to Quote(1)))
            assertEquals(mapOf("a" to Quote(1)), awaitItem())
            upstream.emit(mapOf("a" to Quote(2)))
            assertEquals(mapOf("a" to Quote(2)), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        // Every unlock resubscribes the grid, and that is exactly when the frames must come back.
        val resubscribed = testScheduler.currentTime
        tiles.test {
            runCurrent()
            upstream.emit(mapOf("a" to Quote(10)))
            assertEquals(mapOf("a" to Quote(10)), awaitItem())
            upstream.emit(mapOf("a" to Quote(11)))
            assertEquals(mapOf("a" to Quote(11)), awaitItem())
            assertEquals(resubscribed, testScheduler.currentTime)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
