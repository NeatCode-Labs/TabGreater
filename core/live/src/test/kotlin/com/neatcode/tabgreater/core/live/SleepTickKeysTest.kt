package com.neatcode.tabgreater.core.live

import com.neatcode.tabgreater.core.model.MarketKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression for the wasted Doze dispatch: the SLEEP alarm restarts a killed process, and
 * `onStartCommand` launches the tick immediately — long before the mode machine's combined flow
 * (two DataStore reads) has filled the key cache. Reading the cache alone skipped the whole tick,
 * so the REST round the while-idle quota had just paid for never happened.
 */
class SleepTickKeysTest {

    private class SlowWidgets(private val keys: Set<MarketKey>) : WidgetRefresher {
        var reads = 0
            private set

        override fun observeWidgetKeys(): Flow<Set<MarketKey>> = flow {
            reads++
            // The real store is a Preferences DataStore: the first read is file I/O.
            kotlinx.coroutines.delay(500L)
            emit(keys)
        }

        override suspend fun refreshAll(includeSparklines: Boolean): Int = 0
    }

    @Test
    fun `a cold service reads the widget store instead of ticking empty`() = runTest {
        val widgets = SlowWidgets(KEYS)
        assertEquals(KEYS, sleepTickKeys(cached = emptySet(), widgets = widgets))
        assertEquals(1, widgets.reads)
    }

    @Test
    fun `a warm service pays no I O at all`() = runTest {
        val widgets = SlowWidgets(KEYS)
        assertEquals(KEYS, sleepTickKeys(cached = KEYS, widgets = widgets))
        assertEquals(0, widgets.reads)
    }

    @Test
    fun `no widgets stays empty`() = runTest {
        assertEquals(emptySet<MarketKey>(), sleepTickKeys(cached = emptySet(), widgets = NoWidgets))
    }

    private companion object {
        val KEYS = setOf(MarketKey("kraken:BTC/EUR"))
    }
}
