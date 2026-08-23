package com.neatcode.tabgreater.core.live

import com.neatcode.tabgreater.core.model.ExchangeId
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveStatusCalculatorTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `nothing subscribed is offline`() {
        assertEquals(LiveStatus.OFFLINE, computeLiveStatus(emptyMap(), emptyMap(), now))
    }

    @Test
    fun `a connecting stream is connecting`() {
        assertEquals(
            LiveStatus.CONNECTING,
            computeLiveStatus(mapOf(ExchangeId.BINANCE to StreamState.CONNECTING), emptyMap(), now),
        )
    }

    @Test
    fun `a fresh message makes the layer live`() {
        assertEquals(
            LiveStatus.LIVE,
            computeLiveStatus(
                mapOf(ExchangeId.BINANCE to StreamState.ACTIVE),
                mapOf(ExchangeId.BINANCE to now - 1_000L),
                now,
            ),
        )
    }

    @Test
    fun `a connected but silent stream falls back to connecting`() {
        assertEquals(
            LiveStatus.CONNECTING,
            computeLiveStatus(
                mapOf(ExchangeId.BINANCE to StreamState.ACTIVE),
                mapOf(ExchangeId.BINANCE to now - LIVE_FRESHNESS_MS - 1L),
                now,
            ),
        )
    }

    @Test
    fun `a message exactly at the freshness bound still counts`() {
        assertEquals(
            LiveStatus.LIVE,
            computeLiveStatus(
                mapOf(ExchangeId.BINANCE to StreamState.ACTIVE),
                mapOf(ExchangeId.BINANCE to now - LIVE_FRESHNESS_MS),
                now,
            ),
        )
    }

    @Test
    fun `one live exchange outweighs a failed one`() {
        assertEquals(
            LiveStatus.LIVE,
            computeLiveStatus(
                mapOf(ExchangeId.BINANCE to StreamState.FAILED, ExchangeId.KRAKEN to StreamState.ACTIVE),
                mapOf(ExchangeId.KRAKEN to now - 500L),
                now,
            ),
        )
    }

    @Test
    fun `only failures is offline`() {
        assertEquals(
            LiveStatus.OFFLINE,
            computeLiveStatus(
                mapOf(ExchangeId.BINANCE to StreamState.FAILED, ExchangeId.KRAKEN to StreamState.FAILED),
                mapOf(ExchangeId.BINANCE to now - 500L),
                now,
            ),
        )
    }

    @Test
    fun `an active stream that never sent anything is connecting`() {
        assertEquals(
            LiveStatus.CONNECTING,
            computeLiveStatus(mapOf(ExchangeId.BINANCE to StreamState.ACTIVE), emptyMap(), now),
        )
    }
}
