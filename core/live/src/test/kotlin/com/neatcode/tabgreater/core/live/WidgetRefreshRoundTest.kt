package com.neatcode.tabgreater.core.live

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WidgetRefreshRoundTest {
    @Test
    fun `new price is rendered while candle request is still suspended`() = runTest {
        val candles = CompletableDeferred<Boolean>()
        var price = 1
        val rendered = mutableListOf<Pair<Int, Boolean>>()
        val round = launch {
            refreshPricesBeforeSparklines(
                refreshPrices = { price = 2 },
                render = { rendered += price to it },
                refreshSparklines = { candles.await() },
            )
        }
        runCurrent()
        assertEquals(listOf(2 to false), rendered)
        assertFalse(round.isCompleted)
        candles.complete(true)
        round.join()
        assertEquals(listOf(2 to false, 2 to true), rendered)
    }

    @Test
    fun `skipped or failed candle top-up keeps the freshly rendered price and old graph`() = runTest {
        val rendered = mutableListOf<Boolean>()
        refreshPricesBeforeSparklines(
            refreshPrices = {},
            render = { rendered += it },
            // The service converts a non-cancellation candle failure to false, as it does a
            // top-up that is not due yet. Neither needs a redundant second widget render.
            refreshSparklines = { false },
        )
        assertEquals(listOf(false), rendered)
    }

    @Test
    fun `cancellation during candles preserves price render and cancels the round`() = runTest {
        val rendered = mutableListOf<Boolean>()
        val round = launch {
            refreshPricesBeforeSparklines(
                refreshPrices = {},
                render = { rendered += it },
                refreshSparklines = { awaitCancellation() },
            )
        }
        runCurrent()
        round.cancelAndJoin()
        assertEquals(listOf(false), rendered)
    }

    @Test
    fun `cancellation before a price arrives does not pretend to render a new price`() = runTest {
        val rendered = mutableListOf<Boolean>()
        val round = launch {
            refreshPricesBeforeSparklines(
                refreshPrices = { awaitCancellation() },
                render = { rendered += it },
                refreshSparklines = { true },
            )
        }
        runCurrent()
        round.cancelAndJoin()
        assertEquals(emptyList<Boolean>(), rendered)
    }
}
