package com.neatcode.tabgreater.core.exchange.ws

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionBookTest {

    private val flushes = ArrayList<Pair<List<String>, List<String>>>()

    private fun TestScope.book(window: Long = 100) = SubscriptionBook(
        scope = this,
        coalesceWindowMs = window,
        onFlush = { sub, unsub -> flushes += sub to unsub },
    )

    @Test
    fun `acquire reports fresh keys once and release reports gone keys once`() = runTest {
        val book = book()

        val first = book.acquire(listOf("a", "b"))
        assertEquals(listOf("a", "b"), first.fresh)
        assertTrue(first.wasEmpty)

        val second = book.acquire(listOf("b", "c"))
        assertEquals(listOf("c"), second.fresh)
        assertTrue(!second.wasEmpty)
        assertEquals(listOf("a", "b", "c"), book.active)

        val released = book.release(listOf("a", "b"))
        assertEquals(listOf("a"), released.gone)
        assertTrue(!released.isEmpty)

        val last = book.release(listOf("b", "c", "never-acquired"))
        assertEquals(listOf("b", "c"), last.gone)
        assertTrue(last.isEmpty)
        assertTrue(book.active.isEmpty())
    }

    @Test
    fun `commands inside the window are merged into one flush`() = runTest {
        val book = book(window = 100)

        book.queueSubscribe(listOf("a"))
        advanceTimeBy(50)
        book.queueSubscribe(listOf("b"))
        book.queueUnsubscribe(listOf("z"))
        advanceTimeBy(100)
        runCurrent()

        assertEquals(listOf(listOf("a", "b") to listOf("z")), flushes)
    }

    @Test
    fun `a subscribe followed by an unsubscribe of the same key cancels out`() = runTest {
        val book = book()

        book.queueSubscribe(listOf("a", "b"))
        book.queueUnsubscribe(listOf("a"))
        advanceTimeBy(200)
        runCurrent()

        assertEquals(listOf(listOf("b") to emptyList<String>()), flushes)
    }

    @Test
    fun `nothing is flushed when everything cancelled out`() = runTest {
        val book = book()

        book.queueSubscribe(listOf("a"))
        book.queueUnsubscribe(listOf("a"))
        advanceTimeBy(200)
        runCurrent()

        assertTrue(flushes.isEmpty())
    }

    @Test
    fun `resubscribeAll drops pending commands and queues every active key`() = runTest {
        val book = book()
        book.acquire(listOf("a", "b"))
        book.queueUnsubscribe(listOf("b"))

        book.resubscribeAll()
        advanceTimeBy(200)
        runCurrent()

        assertEquals(listOf(listOf("a", "b") to emptyList<String>()), flushes)
    }

    @Test
    fun `clearPending cancels the timer`() = runTest {
        val book = book()

        book.queueSubscribe(listOf("a"))
        book.clearPending()
        advanceTimeBy(200)
        runCurrent()

        assertTrue(flushes.isEmpty())
    }

    @Test
    fun `commands after a flush start a new window`() = runTest(StandardTestDispatcher()) {
        val book = book()

        book.queueSubscribe(listOf("a"))
        advanceTimeBy(150)
        runCurrent()
        book.queueUnsubscribe(listOf("a"))
        advanceTimeBy(150)
        runCurrent()

        assertEquals(
            listOf(listOf("a") to emptyList(), emptyList<String>() to listOf("a")),
            flushes,
        )
    }
}
