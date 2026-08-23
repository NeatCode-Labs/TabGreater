package com.neatcode.tabgreater.core.data.popular

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PopularPairsRepositoryTest {

    private val fetched = listOf("BTC/USDT", "ETH/USDT", "SOL/USDT")
    private val stored = listOf("BTC/USDT", "ETH/USDT", "DOGE/USDT")

    @Test
    fun `an empty cache fetches and stores the ranking`() = runTest {
        val cache = FakeCache()
        val source = FakeSource(fetched)
        val repository = PopularPairsRepository(cache, source, clock = { NOW })

        assertEquals(fetched, repository.pairs())
        assertEquals(1, source.calls)
        assertEquals(CachedPopularPairs(fetched, NOW), cache.entry)
    }

    @Test
    fun `a cache younger than 24 h is served without touching the network`() = runTest {
        val cache = FakeCache(CachedPopularPairs(stored, NOW - DAY_MS + 1))
        val source = FakeSource(fetched)
        val repository = PopularPairsRepository(cache, source, clock = { NOW })

        assertEquals(stored, repository.pairs())
        assertEquals(0, source.calls)
    }

    @Test
    fun `a cache exactly 24 h old is refreshed`() = runTest {
        val cache = FakeCache(CachedPopularPairs(stored, NOW - DAY_MS))
        val source = FakeSource(fetched)
        val repository = PopularPairsRepository(cache, source, clock = { NOW })

        assertEquals(fetched, repository.pairs())
        assertEquals(1, source.calls)
        assertEquals(NOW, cache.entry?.fetchedAtMs)
    }

    @Test
    fun `a cache written in the future counts as stale`() = runTest {
        val cache = FakeCache(CachedPopularPairs(stored, NOW + DAY_MS))
        val source = FakeSource(fetched)
        val repository = PopularPairsRepository(cache, source, clock = { NOW })

        assertEquals(fetched, repository.pairs())
        assertEquals(1, source.calls)
    }

    @Test
    fun `a second call inside the window does not fetch again`() = runTest {
        val cache = FakeCache()
        val source = FakeSource(fetched)
        var now = NOW
        val repository = PopularPairsRepository(cache, source, clock = { now })

        assertEquals(fetched, repository.pairs())
        now += DAY_MS - 1
        assertEquals(fetched, repository.pairs())
        assertEquals(1, source.calls)

        now += 1
        assertEquals(fetched, repository.pairs())
        assertEquals(2, source.calls)
    }

    @Test
    fun `a failed fetch keeps the stale cache`() = runTest {
        val cache = FakeCache(CachedPopularPairs(stored, NOW - 2 * DAY_MS))
        val source = FakeSource(null)
        val repository = PopularPairsRepository(cache, source, clock = { NOW })

        assertEquals(stored, repository.pairs())
        assertEquals(1, source.calls)
        // The stale entry is left exactly as it was, timestamp included.
        assertEquals(NOW - 2 * DAY_MS, cache.entry?.fetchedAtMs)
    }

    @Test
    fun `an empty answer is treated as a failure`() = runTest {
        val cache = FakeCache(CachedPopularPairs(stored, NOW - 2 * DAY_MS))
        val repository = PopularPairsRepository(cache, FakeSource(emptyList()), clock = { NOW })

        assertEquals(stored, repository.pairs())
        assertEquals(NOW - 2 * DAY_MS, cache.entry?.fetchedAtMs)
    }

    @Test
    fun `no cache and no network falls back to the built-in list`() = runTest {
        val cache = FakeCache()
        val repository = PopularPairsRepository(cache, FakeSource(null), clock = { NOW })

        assertEquals(DEFAULT_POPULAR_PAIRS, repository.pairs())
        assertEquals(null, cache.entry)
    }

    @Test
    fun `a failed fetch is not retried on every call`() = runTest {
        val cache = FakeCache()
        val source = FakeSource(null)
        var now = NOW
        val repository = PopularPairsRepository(cache, source, clock = { now })

        assertEquals(DEFAULT_POPULAR_PAIRS, repository.pairs())
        now += PopularPairsRepository.RETRY_AFTER_FAILURE_MS - 1
        assertEquals(DEFAULT_POPULAR_PAIRS, repository.pairs())
        assertEquals(1, source.calls)

        now += 1
        assertEquals(DEFAULT_POPULAR_PAIRS, repository.pairs())
        assertEquals(2, source.calls)
    }

    @Test
    fun `a success clears the failure backoff`() = runTest {
        val cache = FakeCache()
        var answer: List<String>? = null
        var now = NOW
        val source = object : PopularPairsSource {
            var calls = 0
                private set

            override suspend fun fetch(): List<String>? {
                calls++
                return answer
            }
        }
        val repository = PopularPairsRepository(cache, source, clock = { now })

        assertEquals(DEFAULT_POPULAR_PAIRS, repository.pairs())
        now += PopularPairsRepository.RETRY_AFTER_FAILURE_MS
        answer = fetched
        assertEquals(fetched, repository.pairs())

        // A day later the ranking refreshes again, with no leftover backoff in the way.
        now += DAY_MS
        answer = stored
        assertEquals(stored, repository.pairs())
        assertEquals(3, source.calls)
    }

    private class FakeCache(var entry: CachedPopularPairs? = null) : PopularPairsCache {
        override suspend fun read(): CachedPopularPairs? = entry
        override suspend fun write(pairs: List<String>, fetchedAtMs: Long) {
            entry = CachedPopularPairs(pairs, fetchedAtMs)
        }
    }

    private class FakeSource(private val answer: List<String>?) : PopularPairsSource {
        var calls = 0
            private set

        override suspend fun fetch(): List<String>? {
            calls++
            return answer
        }
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val DAY_MS = PopularPairsRepository.REFRESH_INTERVAL_MS
    }
}
