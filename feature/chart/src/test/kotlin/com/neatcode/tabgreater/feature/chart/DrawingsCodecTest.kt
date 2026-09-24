package com.neatcode.tabgreater.feature.chart

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The persisted drawing list: round trip, and what sanitising keeps out of Room and the page. */
class DrawingsCodecTest {

    private val segment = Drawing(
        name = "segment",
        points = listOf(DrawingPoint(1_727_100_000_000, 63_120.5), DrawingPoint(1_727_200_000_000, 64_000.0)),
        lock = false,
        mode = "weak_magnet",
    )
    private val tag = Drawing(
        name = "simpleTag",
        points = listOf(DrawingPoint(1_727_100_000_000, 0.000_012_34)),
        lock = true,
        mode = "strong_magnet",
        text = "breakout ✓",
    )

    @Test
    fun `drawings survive a JSON round trip`() {
        val drawings = listOf(segment, tag, segment.copy(name = "fibonacciLine", mode = "normal"))
        assertEquals(drawings, DrawingsCodec.decode(DrawingsCodec.encode(drawings)))
    }

    @Test
    fun `the encoded shape is the one the page expects`() {
        val encoded = DrawingsCodec.encode(listOf(segment))
        assertEquals(
            """[{"name":"segment","points":[{"timestamp":1727100000000,"value":63120.5},""" +
                """{"timestamp":1727200000000,"value":64000.0}],"lock":false,"mode":"weak_magnet","text":null}]""",
            encoded,
        )
    }

    @Test
    fun `missing fields decode to the defaults`() {
        val decoded = DrawingsCodec.decode("""[{"name":"priceLine","points":[{"timestamp":5,"value":1.5}]}]""")
        assertEquals(listOf(Drawing(name = "priceLine", points = listOf(DrawingPoint(5, 1.5)))), decoded)
        assertEquals(false, decoded.single().lock)
        assertEquals("weak_magnet", decoded.single().mode)
        assertNull(decoded.single().text)
    }

    @Test
    fun `unknown names, empty points and missing coordinates are dropped`() {
        val raw = "[" + listOf(
            """{"name":"spaceship","points":[{"timestamp":1,"value":2}]}""",
            """{"name":"segment","points":[]}""",
            """{"name":"segment","points":[{"timestamp":1,"value":null},{"timestamp":2,"value":3}]}""",
            """{"name":"segment","points":[{"value":3},{"timestamp":2,"value":3}]}""",
            """{"name":"rect","points":[{"timestamp":1,"value":2},{"timestamp":3,"value":4}]}""",
        ).joinToString(",") + "]"

        assertEquals(listOf("rect"), DrawingsCodec.decode(raw).map { it.name })
    }

    @Test
    fun `non-finite prices are dropped`() {
        val drawings = listOf(
            segment.copy(points = listOf(DrawingPoint(1, Double.NaN))),
            segment.copy(points = listOf(DrawingPoint(1, Double.POSITIVE_INFINITY))),
            segment,
        )
        assertEquals(listOf(segment), DrawingsCodec.sanitize(drawings))
    }

    @Test
    fun `one malformed drawing costs only that drawing`() {
        val raw = """[{"name":"segment","points":"nope"},{"name":7},""" + DrawingsCodec.encode(listOf(segment)).drop(1)
        assertEquals(listOf(segment), DrawingsCodec.decode(raw))
    }

    @Test
    fun `an unknown mode becomes weak magnet`() {
        val decoded = DrawingsCodec.sanitize(listOf(segment.copy(mode = "super_magnet")))
        assertEquals("weak_magnet", decoded.single().mode)
    }

    @Test
    fun `annotations need text and other tools lose theirs`() {
        val sanitized = DrawingsCodec.sanitize(
            listOf(
                tag.copy(text = null),
                tag.copy(text = "   "),
                tag,
                segment.copy(text = "stray"),
                tag.copy(name = "simpleAnnotation", text = "x".repeat(DrawingsCodec.MAX_TEXT_LENGTH + 20)),
            ),
        )
        assertEquals(listOf("simpleTag", "segment", "simpleAnnotation"), sanitized.map { it.name })
        assertNull(sanitized[1].text)
        assertEquals(DrawingsCodec.MAX_TEXT_LENGTH, sanitized[2].text!!.length)
    }

    @Test
    fun `blank, unreadable or non-array input is an empty list`() {
        assertTrue(DrawingsCodec.decode(null).isEmpty())
        assertTrue(DrawingsCodec.decode("").isEmpty())
        assertTrue(DrawingsCodec.decode("[oops").isEmpty())
        assertTrue(DrawingsCodec.decode("""{"name":"segment"}""").isEmpty())
    }

    @Test
    fun `a drawingsChanged payload names its market and carries sanitised drawings`() {
        val payload = Json.parseToJsonElement(
            """{"exchange":"kraken","ticker":"BTC/EUR","drawings":[""" +
                DrawingsCodec.encode(listOf(segment)).drop(1).dropLast(1) +
                """,{"name":"spaceship","points":[{"timestamp":1,"value":2}]}]}""",
        ).jsonObject

        assertEquals(DrawingsPayload("kraken", "BTC/EUR", listOf(segment)), DrawingsCodec.decodePayload(payload))
    }

    @Test
    fun `a payload without a market is rejected and one without drawings is empty`() {
        fun parse(raw: String) = DrawingsCodec.decodePayload(Json.parseToJsonElement(raw).jsonObject)

        assertNull(parse("""{"ticker":"BTC/EUR","drawings":[]}"""))
        assertNull(parse("""{"exchange":"kraken","ticker":7,"drawings":[]}"""))
        assertEquals(DrawingsPayload("kraken", "BTC/EUR"), parse("""{"exchange":"kraken","ticker":"BTC/EUR"}"""))
    }

    @Test
    fun `the setDrawings argument round trips`() {
        val payload = DrawingsPayload("binance", "ETH/USDT", listOf(segment, tag))
        assertEquals(
            payload,
            ChartProtocol.json.decodeFromString(DrawingsPayload.serializer(), DrawingsCodec.encodePayload(payload)),
        )
    }

    @Test
    fun `magnet modes map onto KLineChart overlay modes`() {
        assertEquals("normal", DrawingsCodec.overlayMode(MagnetMode.NONE))
        assertEquals("weak_magnet", DrawingsCodec.overlayMode(MagnetMode.WEAK))
        assertEquals("strong_magnet", DrawingsCodec.overlayMode(MagnetMode.STRONG))
    }
}
