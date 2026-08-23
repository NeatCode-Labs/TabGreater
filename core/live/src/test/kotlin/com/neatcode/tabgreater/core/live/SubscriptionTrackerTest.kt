package com.neatcode.tabgreater.core.live

import com.neatcode.tabgreater.core.model.ExchangeId
import com.neatcode.tabgreater.core.model.MarketKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionTrackerTest {

    private val tracker = SubscriptionTracker()

    private val btc = MarketKey("binance:BTC/EUR")
    private val eth = MarketKey("binance:ETH/EUR")
    private val krakenBtc = MarketKey("kraken:BTC/EUR")

    @Test
    fun `the first acquire of a key restarts its exchange`() {
        assertEquals(setOf(ExchangeId.BINANCE), tracker.acquire(listOf(btc)))
        assertEquals(setOf(btc), tracker.keysOf(ExchangeId.BINANCE))
    }

    @Test
    fun `a second collector for the same key does not restart anything`() {
        tracker.acquire(listOf(btc))
        assertTrue(tracker.acquire(listOf(btc)).isEmpty())
        assertEquals(2, tracker.countOf(btc))
    }

    @Test
    fun `duplicates inside one acquire count once`() {
        tracker.acquire(listOf(btc, btc, btc))
        assertEquals(1, tracker.countOf(btc))
    }

    @Test
    fun `releasing one of two collectors keeps the stream`() {
        tracker.acquire(listOf(btc))
        tracker.acquire(listOf(btc))

        assertTrue(tracker.release(listOf(btc)).isEmpty())
        assertEquals(setOf(btc), tracker.keysOf(ExchangeId.BINANCE))

        assertEquals(setOf(ExchangeId.BINANCE), tracker.release(listOf(btc)))
        assertTrue(tracker.keysOf(ExchangeId.BINANCE).isEmpty())
    }

    @Test
    fun `adding a key to an exchange that is already streaming restarts it`() {
        tracker.acquire(listOf(btc))
        assertEquals(setOf(ExchangeId.BINANCE), tracker.acquire(listOf(btc, eth)))
        assertEquals(setOf(btc, eth), tracker.keysOf(ExchangeId.BINANCE))
    }

    @Test
    fun `keys are grouped per exchange`() {
        tracker.acquire(listOf(btc, eth, krakenBtc))
        assertEquals(setOf(btc, eth), tracker.keysOf(ExchangeId.BINANCE))
        assertEquals(setOf(krakenBtc), tracker.keysOf(ExchangeId.KRAKEN))
        assertEquals(setOf(ExchangeId.BINANCE, ExchangeId.KRAKEN), tracker.exchanges())
    }

    @Test
    fun `releasing a key that was never acquired changes nothing`() {
        assertTrue(tracker.release(listOf(btc)).isEmpty())
        assertEquals(0, tracker.countOf(btc))
        assertTrue(tracker.exchanges().isEmpty())
    }

    @Test
    fun `overlapping collectors leave exactly the shared keys subscribed`() {
        tracker.acquire(listOf(btc, eth))
        tracker.acquire(listOf(eth, krakenBtc))

        assertEquals(setOf(ExchangeId.BINANCE), tracker.release(listOf(btc, eth)))
        assertEquals(setOf(eth), tracker.keysOf(ExchangeId.BINANCE))
        assertEquals(setOf(krakenBtc), tracker.keysOf(ExchangeId.KRAKEN))
    }
}
