package com.neatcode.tabgreater.ui.watchlist

import org.junit.Assert.assertEquals
import org.junit.Test
import com.neatcode.tabgreater.R
import com.neatcode.tabgreater.core.exchange.ExchangeFailureKind
import com.neatcode.tabgreater.core.live.MarketState
import com.neatcode.tabgreater.core.model.MarketKey

class PriceStatusFormattingTest {

    @Test
    fun `a recent reply carrying an earlier trade is distinct from losing price updates`() {
        val now = 420_000_000L
        val issue = PriceRefreshIssue(
            key = MarketKey("hyperliquid:AAPL/USDC"),
            kind = PriceIssueKind.OLD,
            lastTradeAtEpochMs = now - 4 * 86_400_000L,
            lastTradePrice = "355.76 USDC",
            lastPriceReceivedAtEpochMs = now - 1_000L,
        )
        assertEquals(null, issue.explanationRes(now))
        assertEquals(R.string.price_status_issue_old, issue.explanationRes(now + MarketState.STALE_AFTER_MS))
        assertEquals(R.string.price_status_issue_old, issue.copy(lastPriceReceivedAtEpochMs = null).explanationRes(now))
        val error = issue.copy(kind = PriceIssueKind.ERROR)
        assertEquals(R.string.price_status_issue_error, error.explanationRes(now))
        assertEquals(R.string.price_status_issue_rate_limit, error.copy(failure = ExchangeFailureKind.RATE_LIMITED).explanationRes(now))
    }

    @Test
    fun `trade age is calculated from the exchange timestamp`() {
        val now = 10_000_000L

        assertEquals("12s", formatTradeAge(now, now - 12_000L))
        assertEquals("2m 5s", formatTradeAge(now, now - 125_000L))
        assertEquals("3h 4m", formatTradeAge(now, now - (3 * 60 + 4) * 60_000L))
        assertEquals("2d 1h", formatTradeAge(now, now - (49 * 60) * 60_000L))
        assertEquals("0s", formatTradeAge(now, now + 1_000L))
    }
}
