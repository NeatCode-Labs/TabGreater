package com.neatcode.tabgreater.core.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketSearchTest {

    @Test
    fun `normalisation trims uppercases and drops spaces`() {
        assertEquals("BTCEUR", normaliseSearchQuery("  btc eur "))
        assertEquals("BTC/EUR", normaliseSearchQuery(" btc / eur "))
        assertEquals("BTC", normaliseSearchQuery("BtC"))
    }

    @Test
    fun `normalisation drops LIKE wildcards and punctuation`() {
        assertEquals("BTCEUR", normaliseSearchQuery("btc%eur"))
        assertEquals("BTCEUR", normaliseSearchQuery("btc_eur"))
        assertEquals("BTCEUR", normaliseSearchQuery("btc-eur"))
        assertEquals("1INCHUSDT", normaliseSearchQuery("1inch usdt"))
        assertEquals("", normaliseSearchQuery("   "))
    }

    @Test
    fun `a query without a slash has no quote part`() {
        val parsed = parseSearchQuery("BTC")
        assertEquals("BTC", parsed.base)
        assertNull(parsed.quote)
        assertFalse(parsed.isBlank)
    }

    @Test
    fun `a slash splits base from quote`() {
        val parsed = parseSearchQuery("BTC/EUR")
        assertEquals("BTC", parsed.base)
        assertEquals("EUR", parsed.quote)
    }

    @Test
    fun `a trailing slash means any quote`() {
        val parsed = parseSearchQuery("BTC/")
        assertEquals("BTC", parsed.base)
        assertEquals("", parsed.quote)
        assertFalse(parsed.isBlank)
    }

    @Test
    fun `a leading slash means any base`() {
        val parsed = parseSearchQuery("/EUR")
        assertEquals("", parsed.base)
        assertEquals("EUR", parsed.quote)
        assertFalse(parsed.isBlank)
    }

    @Test
    fun `extra slashes are folded into the quote prefix`() {
        assertEquals(MarketQuery("BTC", "EUR"), parseSearchQuery("BTC/EU/R"))
    }

    @Test
    fun `an empty query is blank`() {
        assertTrue(parseSearchQuery("").isBlank)
        assertTrue(parseSearchQuery(normaliseSearchQuery("///")).isBlank)
    }

    @Test
    fun `concatenated matching accepts BTCEUR for BTC EUR`() {
        assertTrue(matchesConcatenated("BTC", "EUR", "BTCEUR"))
        assertTrue(matchesConcatenated("BTC", "EUR", "BTCE"))
    }

    @Test
    fun `concatenated matching rejects a plain base prefix`() {
        // "BTC" is already covered by the base-prefix query; the concatenated pass must not
        // duplicate it, otherwise every quote of every BTC market outranks the real matches.
        assertFalse(matchesConcatenated("BTC", "EUR", "BTC"))
        assertFalse(matchesConcatenated("BTC", "EUR", "BT"))
    }

    @Test
    fun `concatenated matching rejects a different quote`() {
        assertFalse(matchesConcatenated("BTC", "USDT", "BTCEUR"))
        assertFalse(matchesConcatenated("ETH", "EUR", "BTCEUR"))
    }
}
