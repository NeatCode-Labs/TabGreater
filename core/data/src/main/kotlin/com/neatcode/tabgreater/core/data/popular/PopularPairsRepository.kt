package com.neatcode.tabgreater.core.data.popular

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The quick-add ranking behind the chips under the "+ Ticker" search field.
 *
 * The whole point is to touch the network as little as CoinGecko's public tier asks for: the
 * ranking is refreshed **at most once per 24 h**, and only when [pairs] is called — which the
 * search screen does once, on open. Everything else is served from [cache]. A failed or throttled
 * fetch never retries in a loop and never clears anything: it falls back to the cached list and,
 * when there is no cache at all, to [DEFAULT_POPULAR_PAIRS], so the row is never empty.
 *
 * @param clock wall clock in millis, injectable so the 24 h gate is unit testable.
 */
class PopularPairsRepository(
    private val cache: PopularPairsCache,
    private val source: PopularPairsSource,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val mutex = Mutex()

    /** When the last attempt failed; nothing tries again before [RETRY_AFTER_FAILURE_MS]. */
    private var failedAtMs: Long? = null

    /**
     * The ranked pairs to show. Never empty, never throws. The mutex means two screens opening at
     * once share one refresh instead of racing into two requests.
     */
    suspend fun pairs(): List<String> = mutex.withLock {
        val cached = cache.read()
        val now = clock()
        val fallback = cached?.pairs ?: DEFAULT_POPULAR_PAIRS
        when {
            cached != null && isFresh(cached, now) -> cached.pairs
            !mayAttempt(now) -> fallback
            else -> {
                val fetched = source.fetch()
                if (fetched.isNullOrEmpty()) {
                    failedAtMs = now
                    fallback
                } else {
                    failedAtMs = null
                    cache.write(fetched, now)
                    fetched
                }
            }
        }
    }

    /**
     * A cache written in the future (the clock moved backwards, or a time zone/NTP jump) counts as
     * stale rather than fresh forever.
     */
    private fun isFresh(cached: CachedPopularPairs, now: Long): Boolean =
        (now - cached.fetchedAtMs) in 0 until REFRESH_INTERVAL_MS

    /**
     * Keeps a stale cache (or no cache at all) from turning "open the search screen" into a
     * request per open while CoinGecko is down or throttling. Process-local on purpose: it must
     * not survive as state on disk, and a relaunch may legitimately try again.
     */
    private fun mayAttempt(now: Long): Boolean {
        val failed = failedAtMs ?: return true
        return (now - failed) !in 0 until RETRY_AFTER_FAILURE_MS
    }

    companion object {
        /** CoinGecko's public tier expects callers to cache; one refresh per day is plenty. */
        const val REFRESH_INTERVAL_MS: Long = 24L * 60 * 60 * 1000

        /** How long a failed or throttled attempt stops the next one. */
        const val RETRY_AFTER_FAILURE_MS: Long = 15L * 60 * 1000
    }
}
