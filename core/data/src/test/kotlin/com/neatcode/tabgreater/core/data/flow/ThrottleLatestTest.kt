package com.neatcode.tabgreater.core.data.flow

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

/**
 * [throttleLatest] under `runTest`'s virtual clock: every `delay` below is instantaneous in real
 * time, so the assertions are about the cadence the watchlist grid actually redraws at.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ThrottleLatestTest {

    private val period = 5_000L

    @Test
    fun `the first value goes through immediately`() = runTest {
        val upstream = MutableSharedFlow<Int>()
        upstream.throttleLatest { period }.test {
            runCurrent()
            upstream.emit(1)
            assertEquals(1, awaitItem())
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `three ticks inside one period produce one emission`() = runTest {
        val upstream = MutableSharedFlow<Int>()
        upstream.throttleLatest { period }.test {
            runCurrent()
            upstream.emit(1)
            assertEquals(1, awaitItem())

            // 3 more ticks spread over the first second of a 5 s window.
            upstream.emit(2)
            delay(300)
            upstream.emit(3)
            delay(300)
            upstream.emit(4)
            delay(400)
            expectNoEvents()

            // Only the newest of them survives, and only once the window is over.
            delay(period)
            assertEquals(4, awaitItem())
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a value that arrives after a quiet window is not delayed`() = runTest {
        val upstream = MutableSharedFlow<Int>()
        upstream.throttleLatest { period }.test {
            runCurrent()
            upstream.emit(1)
            assertEquals(1, awaitItem())

            delay(60_000)
            upstream.emit(2)
            assertEquals(2, awaitItem())
            assertEquals(60_000L, this@runTest.testScheduler.currentTime)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a period change takes effect on the next emission`() = runTest {
        var current = period
        val upstream = MutableSharedFlow<Int>()
        upstream.throttleLatest { current }.test {
            runCurrent()
            upstream.emit(1)
            assertEquals(1, awaitItem())

            // The 5 s window opened by the first emission still runs to its end.
            current = 1_000L
            upstream.emit(2)
            delay(1_000)
            expectNoEvents()
            delay(4_000)
            assertEquals(2, awaitItem())

            // From here on the new period governs.
            upstream.emit(3)
            delay(1_500)
            assertEquals(3, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a period of zero lets everything through`() = runTest {
        val upstream = MutableSharedFlow<Int>()
        upstream.throttleLatest { 0L }.test {
            runCurrent()
            upstream.emit(1)
            assertEquals(1, awaitItem())
            upstream.emit(2)
            assertEquals(2, awaitItem())
            assertEquals(0L, this@runTest.testScheduler.currentTime)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a pass-through value does not wait for the window to end`() = runTest {
        val upstream = MutableSharedFlow<String>()
        // "a different thing, not another sample of the same one": here, a change of the first char.
        upstream.throttleLatest(
            passThrough = { previous, next -> previous?.first() != next.first() },
        ) { period }.test {
            runCurrent()
            upstream.emit("a1")
            assertEquals("a1", awaitItem())

            // Same series: held back for the rest of the window.
            upstream.emit("a2")
            delay(1_000)
            expectNoEvents()

            // A different one: on screen at once, 1 s into a 5 s window — and it replaces "a2",
            // which is stale by definition.
            upstream.emit("b1")
            assertEquals("b1", awaitItem())
            assertEquals(1_000L, this@runTest.testScheduler.currentTime)

            // The pass-through opened a fresh window of its own.
            upstream.emit("b2")
            delay(1_000)
            expectNoEvents()
            delay(4_000)
            assertEquals("b2", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a value the predicate rejects still waits out the original window`() = runTest {
        val upstream = MutableSharedFlow<String>()
        upstream.throttleLatest(
            passThrough = { previous, next -> previous?.first() != next.first() },
        ) { period }.test {
            runCurrent()
            upstream.emit("a1")
            assertEquals("a1", awaitItem())

            // Two ordinary samples inside the window: the window is not restarted by them, so the
            // newest lands exactly one period after the first value, not one period after itself.
            delay(2_000)
            upstream.emit("a2")
            delay(2_000)
            upstream.emit("a3")
            expectNoEvents()
            delay(1_000)
            assertEquals("a3", awaitItem())
            assertEquals(period, this@runTest.testScheduler.currentTime)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `upstream completion completes the throttled flow`() = runTest {
        flowOf(1).throttleLatest { period }.test {
            assertEquals(1, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `upstream failures are forwarded`() = runTest {
        val boom = IOException("socket died")
        val upstream = flow<Int> {
            emit(1)
            throw boom
        }
        upstream.throttleLatest { period }.test {
            assertEquals(1, awaitItem())
            // Stack-trace recovery hands the collector a copy, so compare what identifies it.
            val error = awaitError()
            assertEquals(IOException::class.java, error.javaClass)
            assertEquals(boom.message, error.message)
        }
    }
}
