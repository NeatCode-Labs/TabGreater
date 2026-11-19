package com.neatcode.tabgreater.core.live

import com.neatcode.tabgreater.core.model.*
import org.junit.Assert.*
import org.junit.Test

class MarketStateTest {
    private val ticker = Ticker(MarketKey("binance:BTC/EUR"), 100.0, timestamp = Long.MAX_VALUE)
    @Test fun `disk timestamps never prove freshness even when in the future`() {
        assertEquals(PriceFreshness.CACHED, MarketState(ticker).aged(0).freshness)
    }
    @Test fun `freshness expires at ten minutes using receipt rather than exchange time`() {
        val state = MarketState(ticker, confirmedAtElapsedMs = 5_000)
        assertEquals(PriceFreshness.CURRENT, state.aged(604_999).freshness)
        assertEquals(PriceFreshness.OLD, state.aged(605_000).freshness)
    }
    @Test fun `missing data remains missing`() {
        assertEquals(PriceFreshness.MISSING, MarketState().aged(0).freshness)
    }

    @Test fun `reading an old Hyperliquid execution does not make its price fresh`() {
        val oldTrade = Ticker(MarketKey("hyperliquid:PERP_BTC/USD"), 75.0, timestamp = 1_000_000L)
        val received = MarketState(oldTrade, confirmedAtElapsedMs = 8_000L, confirmedAtEpochMs = 1_590_000L)
        assertEquals(PriceFreshness.CURRENT, received.aged(8_000L).freshness)
        assertEquals(PriceFreshness.OLD, received.aged(18_000L).freshness)
        assertEquals(PriceFreshness.OLD, received.copy(confirmedAtEpochMs = 1_700_000L).aged(8_000L).freshness)
    }
}
