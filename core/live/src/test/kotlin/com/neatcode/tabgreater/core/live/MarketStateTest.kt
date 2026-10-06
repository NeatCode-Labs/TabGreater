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
}
