package com.neatcode.tabgreater.core.data.flow

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ObserveEachTest {

    /** One [MutableStateFlow] per key, recording how often `source(key)` was called. */
    private class Sources {
        private val flows = HashMap<String, MutableStateFlow<Int>>()
        val started = ArrayList<String>()

        fun flow(key: String): MutableStateFlow<Int> = flows.getOrPut(key) { MutableStateFlow(0) }

        operator fun invoke(key: String): Flow<Int> {
            started += key
            return flow(key)
        }

        suspend fun emit(key: String, value: Int) = flow(key).emit(value)
    }

    @Test
    fun `an empty key set emits an empty map`() = runTest {
        val sources = Sources()
        MutableStateFlow(emptySet<String>()).observeEach(sources::invoke).test {
            assertEquals(emptyMap<String, Int>(), awaitItem())
            assertEquals(emptyList<String>(), sources.started)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `only newly added keys are subscribed`() = runTest {
        val sources = Sources()
        val keys = MutableStateFlow(setOf("a"))

        keys.observeEach(sources::invoke).test {
            awaitUntil { it.keys == setOf("a") }
            assertEquals(listOf("a"), sources.started)

            keys.value = setOf("a", "b")
            awaitUntil { it.keys == setOf("a", "b") }
            assertEquals(listOf("a", "b"), sources.started)

            // "a" is still in the set, so it must not be restarted.
            keys.value = setOf("a", "b", "c")
            awaitUntil { it.keys == setOf("a", "b", "c") }
            assertEquals(listOf("a", "b", "c"), sources.started)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `removing a key drops it from the map and cancels its collector`() = runTest {
        val sources = Sources()
        val keys = MutableStateFlow(setOf("a", "b"))

        keys.observeEach(sources::invoke).test {
            awaitUntil { it.keys == setOf("a", "b") }
            assertEquals(1, sources.flow("b").subscriptionCount.value)

            keys.value = setOf("a")
            assertFalse("b" in awaitUntil { it.keys == setOf("a") })
            // Suspends until the collector is really gone; a leaked one would time the test out.
            assertEquals(0, sources.flow("b").subscriptionCount.first { it == 0 })

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a surviving key keeps emitting after the key set changed`() = runTest {
        val sources = Sources()
        val keys = MutableStateFlow(setOf("a", "b"))

        keys.observeEach(sources::invoke).test {
            awaitUntil { it.keys == setOf("a", "b") }

            keys.value = setOf("a")
            awaitUntil { it.keys == setOf("a") }

            sources.emit("a", 7)
            assertEquals(mapOf("a" to 7), awaitUntil { it["a"] == 7 })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a late value for a removed key never reappears`() = runTest {
        val sources = Sources()
        val keys = MutableStateFlow(setOf("a", "b"))

        keys.observeEach(sources::invoke).test {
            awaitUntil { it.keys == setOf("a", "b") }

            keys.value = setOf("a")
            awaitUntil { it.keys == setOf("a") }

            sources.emit("b", 99)
            sources.emit("a", 1)

            assertEquals(mapOf("a" to 1), awaitUntil { it["a"] == 1 })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a source that completes is re-subscribed on the next key-set emission`() = runTest {
        val started = ArrayList<String>()
        val keys = MutableStateFlow(setOf("a"))
        keys.observeEach { key ->
            started += key
            // The first subscription completes immediately after one value; later ones never do.
            if (started.count { it == key } == 1) flowOf(1) else MutableStateFlow(2)
        }.test {
            // Emissions are conflated, so wait for the map that carries the first value.
            while (awaitItem()["a"] != 1) { /* keep waiting */ }
            assertEquals(1, started.count { it == "a" })
            // A key-set change that still contains "a" restarts its (completed) source.
            keys.value = setOf("a", "b")
            while (awaitItem()["a"] != 2) { /* keep waiting */ }
            assertEquals(2, started.count { it == "a" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `values of several keys end up in one map`() = runTest {
        val sources = Sources()
        val keys = MutableStateFlow(setOf("a", "b"))

        keys.observeEach(sources::invoke).test {
            sources.emit("a", 1)
            sources.emit("b", 2)
            assertEquals(mapOf("a" to 1, "b" to 2), awaitUntil { it["a"] == 1 && it["b"] == 2 })
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * `observeEach` conflates, so intermediate maps may be dropped; tests wait for the first map
     * that satisfies [predicate] instead of asserting on an exact emission sequence.
     */
    private suspend fun ReceiveTurbine<Map<String, Int>>.awaitUntil(
        predicate: (Map<String, Int>) -> Boolean,
    ): Map<String, Int> {
        repeat(MAX_EMISSIONS) {
            val map = awaitItem()
            if (predicate(map)) return map
        }
        throw AssertionError("no emission matched after $MAX_EMISSIONS items")
    }

    private companion object {
        const val MAX_EMISSIONS = 20
    }
}
