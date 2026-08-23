package com.neatcode.tabgreater.core.data.popular

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class CoinGeckoPopularPairsSourceTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient()
    }

    @After
    fun tearDown() {
        server.close()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
    }

    @Test
    fun `a ranking answer becomes pairs`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(RANKING).build())

        assertEquals(listOf("BTC/USDT", "ETH/USDT"), source().fetch())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a rate limit answers null and is not retried`() = runTest {
        server.enqueue(MockResponse.Builder().code(429).body("rate limited").build())

        assertNull(source().fetch())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a server error answers null`() = runTest {
        server.enqueue(MockResponse.Builder().code(500).body("boom").build())

        assertNull(source().fetch())
    }

    @Test
    fun `a body with nothing chartable answers null rather than an empty row`() = runTest {
        server.enqueue(
            MockResponse.Builder().code(200)
                .body("""[{"id":"tether","symbol":"usdt","name":"Tether","market_cap":1}]""")
                .build(),
        )

        assertNull(source().fetch())
    }

    @Test
    fun `an unreachable endpoint answers null`() = runTest {
        val url = server.url("/markets").toString()
        server.close()

        assertNull(CoinGeckoPopularPairsSource(client, endpoint = url).fetch())
    }

    private fun source() =
        CoinGeckoPopularPairsSource(client, endpoint = server.url("/markets").toString())

    private companion object {
        val RANKING = """
            [
              {"id":"bitcoin","symbol":"btc","name":"Bitcoin","market_cap":1300000000000},
              {"id":"tether","symbol":"usdt","name":"Tether","market_cap":120000000000},
              {"id":"ethereum","symbol":"eth","name":"Ethereum","market_cap":400000000000}
            ]
        """.trimIndent()
    }
}
