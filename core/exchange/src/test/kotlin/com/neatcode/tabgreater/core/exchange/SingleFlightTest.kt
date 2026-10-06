package com.neatcode.tabgreater.core.exchange

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SingleFlightTest {
    @Test fun `leaving one waiter does not cancel another and only one request runs`() = runTest {
        val flight = SingleFlight<String, Int>()
        val finish = CompletableDeferred<Int>()
        var calls = 0
        val first = async { flight.run("same") { calls++; finish.await() } }
        runCurrent()
        val second = async { flight.run("same") { calls++; 99 } }
        runCurrent()
        first.cancelAndJoin()
        assertTrue(second.isActive)
        finish.complete(42)
        assertEquals(42, second.await())
        assertEquals(1, calls)
    }
    @Test fun `last waiter cancels work and a new caller can retry`() = runTest {
        val flight = SingleFlight<String, Int>()
        var cancelled = false
        val first = async { flight.run("same") { try { awaitCancellation() } finally { cancelled = true } } }
        runCurrent()
        first.cancelAndJoin()
        runCurrent()
        assertTrue(cancelled)
        assertEquals(7, flight.run("same") { 7 })
    }
    @Test fun `different history pages are not coalesced`() = runTest {
        val flight = SingleFlight<String, Int>()
        val a = async { flight.run("1h:end=100:limit=60") { delay(10); 1 } }
        val b = async { flight.run("1h:end=50:limit=60") { delay(10); 2 } }
        assertEquals(listOf(1, 2), awaitAll(a, b))
    }
    @Test fun `retry after accepts dates and seconds but not invalid negative values`() {
        assertEquals(17_000L, parseRetryAfter("17", 0))
        assertEquals(5_000L, parseRetryAfter("Thu, 01 Jan 1970 00:00:05 GMT", 0))
        assertEquals(0L, parseRetryAfter("Thu, 01 Jan 1970 00:00:05 GMT", 10_000))
        assertNull(parseRetryAfter("-1", 0))
        assertNull(parseRetryAfter("later", 0))
    }
}
