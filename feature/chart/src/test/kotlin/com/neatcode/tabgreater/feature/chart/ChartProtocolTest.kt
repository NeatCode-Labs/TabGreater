package com.neatcode.tabgreater.feature.chart

import com.neatcode.tabgreater.core.model.Candle
import com.neatcode.tabgreater.core.model.ExchangeId
import com.neatcode.tabgreater.core.model.MarketKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartProtocolTest {

    private val json = ChartProtocol.json

    @Test
    fun `parses a getBars request, ignoring keys the JS side added`() {
        val raw = """
            {"id":"r7","action":"getBars","payload":{
              "exchange":"kraken","ticker":"BTC/EUR","instId":"XXBTZEUR",
              "type":"forward","timestamp":1750000000000,"span":1,"unit":"hour","limit":300,
              "somethingNew":true}}
        """.trimIndent()

        val req = ChartProtocol.parseRequest(raw)!!
        assertEquals("r7", req.id)
        assertEquals(ChartProtocol.ACTION_GET_BARS, req.action)

        val payload = json.decodeFromJsonElement(GetBarsReq.serializer(), req.payload)
        assertEquals("kraken", payload.exchange)
        assertEquals("BTC/EUR", payload.ticker)
        assertEquals("XXBTZEUR", payload.instId)
        assertEquals("forward", payload.type)
        assertEquals(1_750_000_000_000L, payload.timestamp)
        assertEquals(1, payload.span)
        assertEquals("hour", payload.unit)
        assertEquals(300, payload.limit)
    }

    @Test
    fun `an init request has a null timestamp and no id-less field is required`() {
        val raw = """
            {"id":"r1","action":"getBars","payload":{
              "exchange":"binance","ticker":"BTC/USDT","type":"init","timestamp":null,
              "span":1,"unit":"day","limit":500}}
        """.trimIndent()
        val payload = json.decodeFromJsonElement(GetBarsReq.serializer(), ChartProtocol.parseRequest(raw)!!.payload)
        assertNull(payload.timestamp)
        assertEquals("", payload.instId)
    }

    @Test
    fun `a message without an id parses as fire-and-forget`() {
        val req = ChartProtocol.parseRequest("""{"action":"ready","payload":{}}""")!!
        assertNull(req.id)
        assertEquals(ChartProtocol.ACTION_READY, req.action)
    }

    @Test
    fun `a log message carries kind and text`() {
        val req = ChartProtocol.parseRequest("""{"action":"log","payload":{"kind":"warn","text":"boom","x":1}}""")!!
        val payload = json.decodeFromJsonElement(LogPayload.serializer(), req.payload)
        assertEquals("warn", payload.kind)
        assertEquals("boom", payload.text)
    }

    @Test
    fun `garbage and unexpected shapes parse to null instead of throwing`() {
        assertNull(ChartProtocol.parseRequest("not json"))
        assertNull(ChartProtocol.parseRequest("[]"))
        assertNull(ChartProtocol.parseRequest("""{"payload":{}}"""))
    }

    @Test
    fun `a reply serialises with klinecharts field names`() {
        val bar = Candle(openTime = 1_700_000_000_000L, open = 1.0, high = 2.0, low = 0.5, close = 1.5, volume = 42.0)
        val encoded = json.encodeToString(GetBarsRes.serializer(), GetBarsRes(listOf(bar.toChartBar()), true))
        assertEquals(
            """{"bars":[{"timestamp":1700000000000,"open":1.0,"high":2.0,"low":0.5,"close":1.5,"volume":42.0}],""" +
                """"hasMoreOlder":true}""",
            encoded,
        )
    }

    @Test
    fun `hasMoreOlder needs half a page and an exchange that can page`() {
        // Kraken has no paging parameter at all: always false, however full the page is.
        assertFalse(ChartProtocol.canPageHistory(ExchangeId.KRAKEN))
        assertFalse(ChartProtocol.hasMoreOlder(ExchangeId.KRAKEN, barCount = 720, limit = 500))
        assertFalse(ChartProtocol.hasMoreOlder(ExchangeId.KRAKEN, barCount = 500, limit = 500))

        (ExchangeId.entries - ExchangeId.KRAKEN).forEach { exchange ->
            assertTrue(exchange.id, ChartProtocol.canPageHistory(exchange))
            assertTrue(exchange.id, ChartProtocol.hasMoreOlder(exchange, barCount = 500, limit = 500))
            assertTrue(exchange.id, ChartProtocol.hasMoreOlder(exchange, barCount = 250, limit = 500))
            assertFalse(exchange.id, ChartProtocol.hasMoreOlder(exchange, barCount = 249, limit = 500))
            assertFalse(exchange.id, ChartProtocol.hasMoreOlder(exchange, barCount = 0, limit = 500))
        }
    }

    @Test
    fun `a zero limit never claims more history`() {
        assertFalse(ChartProtocol.hasMoreOlder(ExchangeId.BINANCE, barCount = 0, limit = 0))
    }

    @Test
    fun `only forward paging bounds the request`() {
        assertEquals(123L, ChartProtocol.endTimeFor("forward", 123L))
        assertNull(ChartProtocol.endTimeFor("init", null))
        assertNull(ChartProtocol.endTimeFor("backward", 123L))
        assertNull(ChartProtocol.endTimeFor("update", 123L))
    }

    @Test
    fun `exchange and ticker rebuild the canonical key`() {
        assertEquals(MarketKey("kraken:BTC/EUR"), ChartProtocol.marketKeyOf("kraken", "BTC/EUR"))
        assertNull(ChartProtocol.marketKeyOf("nasdaq", "BTC/EUR"))
        assertNull(ChartProtocol.marketKeyOf("kraken", "BTC"))
        assertNull(ChartProtocol.marketKeyOf("", ""))
    }
}
