package com.neatcode.tabgreater.core.data.popular

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PopularPairParserTest {

    private val parser = PopularPairParser()

    @Test
    fun `a market cap ranking becomes the top five chartable pairs`() {
        assertEquals(
            listOf("BTC/USDT", "ETH/USDT", "BNB/USDT", "SOL/USDT", "XRP/USDT"),
            parser.parse(RANKING),
        )
    }

    @Test
    fun `unknown keys and a missing market cap do not break the decode`() {
        val body = """
            [
              {"id":"bitcoin","symbol":"btc","name":"Bitcoin","image":"x","current_price":65609.7,
               "market_cap":1300000000000,"roi":null,"sparkline_in_7d":{"price":[1.0,2.0]}},
              {"id":"ethereum","symbol":"eth","name":"Ethereum","current_price":3000.0}
            ]
        """.trimIndent()
        assertEquals(listOf("BTC/USDT", "ETH/USDT"), parser.parse(body))
    }

    @Test
    fun `rows are ranked by market cap even when the feed is unordered`() {
        val body = """
            [
              {"id":"solana","symbol":"sol","name":"Solana","market_cap":80000000000},
              {"id":"bitcoin","symbol":"btc","name":"Bitcoin","market_cap":1300000000000},
              {"id":"ethereum","symbol":"eth","name":"Ethereum","market_cap":400000000000}
            ]
        """.trimIndent()
        assertEquals(listOf("BTC/USDT", "ETH/USDT", "SOL/USDT"), parser.parse(body))
    }

    @Test
    fun `the limit is honoured`() {
        assertEquals(listOf("BTC/USDT", "ETH/USDT"), parser.parse(RANKING, limit = 2))
        assertEquals(emptyList<String>(), parser.parse(RANKING, limit = 0))
        assertEquals(
            listOf(
                "BTC/USDT", "ETH/USDT", "BNB/USDT", "SOL/USDT", "XRP/USDT",
                "DOGE/USDT", "ADA/USDT", "TRX/USDT", "AVAX/USDT", "LEO/USDT",
            ),
            parser.parse(RANKING, limit = 50),
        )
    }

    @Test
    fun `a duplicated ticker is listed once`() {
        val body = """
            [
              {"id":"bitcoin","symbol":"btc","name":"Bitcoin","market_cap":1300000000000},
              {"id":"bitcoin-clone","symbol":"BTC","name":"Bitcoin Clone","market_cap":5}
            ]
        """.trimIndent()
        assertEquals(listOf("BTC/USDT"), parser.parse(body))
    }

    @Test
    fun `junk tickers are skipped`() {
        val body = """
            [
              {"id":"weird","symbol":"","name":"No ticker","market_cap":9},
              {"id":"weirder","symbol":"a b c","name":"Spaces","market_cap":8},
              {"id":"bitcoin","symbol":"btc","name":"Bitcoin","market_cap":7}
            ]
        """.trimIndent()
        assertEquals(listOf("BTC/USDT"), parser.parse(body))
    }

    @Test
    fun `a malformed body yields an empty list`() {
        assertTrue(parser.parse("").isEmpty())
        assertTrue(parser.parse("not json").isEmpty())
        assertTrue(parser.parse("{\"error\":\"rate limited\"}").isEmpty())
        assertTrue(parser.parse("[]").isEmpty())
        assertTrue(parser.parse("[{\"id\":42}]").isEmpty())
    }

    @Test
    fun `the quote asset is configurable`() {
        assertEquals(
            listOf("BTC/EUR", "ETH/EUR"),
            PopularPairParser(quote = "EUR").parse(RANKING, limit = 2),
        )
    }

    private companion object {
        /**
         * Trimmed CoinGecko `/coins/markets` answer: the real top of the ranking, with every kind
         * of row the filter has to drop — stablecoins, a wrapped BTC, a liquid-staking receipt and
         * a bridged token.
         */
        val RANKING = """
            [
              {"id":"bitcoin","symbol":"btc","name":"Bitcoin","market_cap":1300000000000},
              {"id":"ethereum","symbol":"eth","name":"Ethereum","market_cap":400000000000},
              {"id":"tether","symbol":"usdt","name":"Tether","market_cap":120000000000},
              {"id":"binancecoin","symbol":"bnb","name":"BNB","market_cap":90000000000},
              {"id":"solana","symbol":"sol","name":"Solana","market_cap":80000000000},
              {"id":"usd-coin","symbol":"usdc","name":"USDC","market_cap":60000000000},
              {"id":"ripple","symbol":"xrp","name":"XRP","market_cap":55000000000},
              {"id":"staked-ether","symbol":"steth","name":"Lido Staked Ether","market_cap":40000000000},
              {"id":"dogecoin","symbol":"doge","name":"Dogecoin","market_cap":30000000000},
              {"id":"wrapped-bitcoin","symbol":"wbtc","name":"Wrapped Bitcoin","market_cap":20000000000},
              {"id":"cardano","symbol":"ada","name":"Cardano","market_cap":18000000000},
              {"id":"tron","symbol":"trx","name":"TRON","market_cap":17000000000},
              {"id":"binance-bridged-usdt-bnb-smart-chain","symbol":"busdt","name":"Binance Bridged USDT","market_cap":16000000000},
              {"id":"avalanche-2","symbol":"avax","name":"Avalanche","market_cap":15000000000},
              {"id":"leo-token","symbol":"leo","name":"LEO Token","market_cap":9000000000}
            ]
        """.trimIndent()
    }
}
