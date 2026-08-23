package com.neatcode.tabgreater.core.exchange.ratelimit

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenBucketTest {

    private var nowNanos = 0L
    private val sleeps = ArrayList<Long>()

    private fun bucket(capacity: Double, refillPerSecond: Double) = TokenBucket(
        capacity = capacity,
        refillPerSecond = refillPerSecond,
        clockNanos = { nowNanos },
        sleep = { ms ->
            sleeps += ms
            nowNanos += ms * 1_000_000
        },
    )

    @Test
    fun `a full bucket serves a burst up to capacity without sleeping`() = runTest {
        val bucket = bucket(capacity = 3.0, refillPerSecond = 1.0)

        repeat(3) { bucket.acquire() }

        assertTrue(sleeps.isEmpty())
        assertEquals(0.0, bucket.available(), 1e-9)
    }

    @Test
    fun `kraken style bucket waits one second between requests`() = runTest {
        val bucket = bucket(capacity = 1.0, refillPerSecond = 1.0)

        bucket.acquire()
        bucket.acquire()
        bucket.acquire()

        assertEquals(listOf(1000L, 1000L), sleeps)
    }

    @Test
    fun `weighted requests deduct their weight and wait for the deficit only`() = runTest {
        val bucket = bucket(capacity = 500.0, refillPerSecond = 50.0)

        bucket.acquire(weight = 400)
        assertEquals(100.0, bucket.available(), 1e-9)
        bucket.acquire(weight = 150)

        // 50 tokens short at 50 tokens/s -> exactly one second.
        assertEquals(listOf(1000L), sleeps)
        assertEquals(0.0, bucket.available(), 1e-6)
    }

    @Test
    fun `tokens refill with elapsed time and never exceed capacity`() = runTest {
        val bucket = bucket(capacity = 2.0, refillPerSecond = 4.0)

        bucket.acquire(2)
        nowNanos += 250_000_000 // 0.25 s -> 1 token
        assertEquals(1.0, bucket.available(), 1e-9)
        nowNanos += 10_000_000_000
        assertEquals(2.0, bucket.available(), 1e-9)
    }

    @Test
    fun `a weight above capacity is clamped so it can still be served`() = runTest {
        val bucket = bucket(capacity = 2.0, refillPerSecond = 1.0)

        bucket.acquire(weight = 10)

        assertTrue(sleeps.isEmpty())
        assertEquals(0.0, bucket.available(), 1e-9)
    }
}
