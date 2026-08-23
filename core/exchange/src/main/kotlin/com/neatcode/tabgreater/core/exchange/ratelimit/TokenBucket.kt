package com.neatcode.tabgreater.core.exchange.ratelimit

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Classic token bucket for client-side REST pacing.
 *
 * The bucket holds at most [capacity] tokens and refills continuously at [refillPerSecond]. A call
 * to [acquire] takes [weight] tokens, suspending until enough have accumulated; callers are served
 * strictly in arrival order, so a burst of tile refreshes cannot starve one request forever.
 *
 * Typical settings: Kraken `TokenBucket(capacity = 1.0, refillPerSecond = 1.0)` ("1 request per
 * second or less"), MEXC `TokenBucket(capacity = 500.0, refillPerSecond = 50.0)` per endpoint
 * (500 weight per 10 s window), Gate `TokenBucket(capacity = 200.0, refillPerSecond = 20.0)`.
 *
 * @param capacity maximum burst, in tokens (>= the largest single [weight]).
 * @param refillPerSecond steady-state rate.
 * @param clockNanos monotonic clock; injectable for tests.
 */
class TokenBucket(
    private val capacity: Double,
    private val refillPerSecond: Double,
    private val clockNanos: () -> Long = System::nanoTime,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    init {
        require(capacity > 0) { "capacity must be positive" }
        require(refillPerSecond > 0) { "refillPerSecond must be positive" }
    }

    private val mutex = Mutex()
    private var tokens = capacity
    private var lastRefillNanos = clockNanos()

    /** Suspends until [weight] tokens are available, then consumes them. */
    suspend fun acquire(weight: Int = 1) {
        require(weight > 0) { "weight must be positive" }
        val needed = weight.toDouble().coerceAtMost(capacity)
        // The mutex is held while waiting so that requests are served in FIFO order and the
        // accounting (refill -> deduct) is atomic per request.
        mutex.withLock {
            refill()
            if (tokens < needed) {
                val deficit = needed - tokens
                val waitNanos = (deficit / refillPerSecond * NANOS_PER_SECOND).toLong()
                sleep((waitNanos + NANOS_PER_MILLI - 1) / NANOS_PER_MILLI)
                refill()
            }
            tokens = (tokens - needed).coerceAtLeast(0.0)
        }
    }

    /** Tokens currently available (after a refill); for tests and diagnostics. */
    suspend fun available(): Double = mutex.withLock {
        refill()
        tokens
    }

    private fun refill() {
        val now = clockNanos()
        val elapsed = now - lastRefillNanos
        if (elapsed <= 0) return
        lastRefillNanos = now
        tokens = (tokens + elapsed / NANOS_PER_SECOND * refillPerSecond).coerceAtMost(capacity)
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000.0
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
