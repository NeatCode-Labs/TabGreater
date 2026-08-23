package com.neatcode.tabgreater.widget

import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.PriceFormat
import com.neatcode.tabgreater.core.model.TGColors
import com.neatcode.tabgreater.core.model.Ticker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class WidgetRenderModelTest {

    @Test
    fun `price uses the market precision and en-US grouping`() {
        val model = build(ticker(last = 65_609.7031), precision = 2)

        assertEquals("65,609.70", model.price)
        assertTrue(model.hasData)
    }

    @Test
    fun `eight decimals survive for sub-cent markets`() {
        val model = build(ticker(last = 0.00012412), precision = 8)

        // Shrink zeros: 0.00012412 has three leading zeros -> 0.0₃12412.
        assertEquals("0.0₃12412", model.price)
    }

    @Test
    fun `a missing instrument list falls back to two decimals above one and eight below`() {
        assertEquals(2, WidgetModelFactory.fallbackPrecision(65_609.70))
        assertEquals(8, WidgetModelFactory.fallbackPrecision(0.00012412))
        assertEquals(2, WidgetModelFactory.fallbackPrecision(null))
        assertEquals("0.0₃12412", build(ticker(last = 0.00012412), precision = null).price)
    }

    @Test
    fun `change is signed with two decimals and drives the colour`() {
        assertEquals("+6.52%", build(ticker(changePct = 6.52)).change)
        assertTrue(build(ticker(changePct = 6.52)).changeUp)
        assertEquals("-3.47%", build(ticker(changePct = -3.47)).change)
        assertFalse(build(ticker(changePct = -3.47)).changeUp)
    }

    @Test
    fun `change is derived from the 24h open when the exchange sends no percentage`() {
        val model = build(ticker(last = 110.0, changePct = null, open = 100.0))

        assertEquals("+10.00%", model.change)
    }

    @Test
    fun `no ticker at all renders the em dash and counts as stale`() {
        val model = WidgetModelFactory.build(CONFIG, null, 2, emptyList(), NOW)

        assertEquals(PriceFormat.NO_VALUE, model.price)
        assertEquals(PriceFormat.NO_VALUE, model.change)
        assertFalse(model.hasData)
        assertTrue(model.stale)
        assertEquals("", model.updatedLabel)
    }

    @Test
    fun `a snapshot older than ten minutes is stale`() {
        assertFalse(build(ticker(timestamp = NOW - 9 * 60_000)).stale)
        assertFalse(build(ticker(timestamp = NOW - WidgetModelFactory.STALE_AFTER_MS)).stale)
        assertTrue(build(ticker(timestamp = NOW - WidgetModelFactory.STALE_AFTER_MS - 1)).stale)
    }

    @Test
    fun `updated label is hh mm ss in the given zone`() {
        // 2026-08-22T14:05:09Z
        val model = WidgetModelFactory.build(
            config = CONFIG,
            ticker = ticker(timestamp = 1_787_407_509_000),
            pricePrecision = 2,
            spark = emptyList(),
            now = 1_787_407_509_000,
            zone = ZoneId.of("UTC"),
        )

        assertEquals("14:05:09", model.updatedLabel)
    }

    @Test
    fun `exchange and pair come from the canonical key`() {
        val model = build(ticker())

        assertEquals("KRAKEN", model.exchange)
        assertEquals("BTC/EUR", model.pair)
        assertEquals("kraken:BTC/EUR", model.key)
        assertEquals(MarketKey("kraken:BTC/EUR"), model.marketKey)
    }

    @Test
    fun `change falls back to the 24h window when the ticker carries neither percentage nor open`() {
        // Kraken over REST: `o` is today's open, so the adapter sends no 24 h figures at all.
        val model = build(ticker(last = 110.0, changePct = null, open = null), spark = listOf(100f, 104f, 108f))

        assertEquals("+10.00%", model.change)
        assertTrue(model.changeUp)
        assertTrue(model.hasChange)
    }

    @Test
    fun `the window fallback survives switching the sparkline off`() {
        val off = CONFIG.copy(showSparkline = false)
        val model = WidgetModelFactory.build(off, ticker(last = 95.0, changePct = null), 2, listOf(100f, 97f), NOW)

        assertEquals("-5.00%", model.change)
        assertFalse(model.changeUp)
        assertFalse(model.showSparkline)
        // The points stay in the state — drawing is gated by `showSparkline`, the change is not.
        assertEquals(listOf(100f, 97f), model.spark)
    }

    @Test
    fun `an unknown change is the em dash and is not painted as a gain`() {
        val model = build(ticker(changePct = null, open = null), spark = emptyList())

        assertEquals(PriceFormat.NO_VALUE, model.change)
        assertFalse(model.hasChange)
        assertTrue(build(ticker(changePct = 0.0)).hasChange)
        // A zero or non-finite first close cannot anchor a percentage either.
        assertFalse(build(ticker(changePct = null), spark = listOf(0f, 1f)).hasChange)
    }

    @Test
    fun `the exchange percentage wins over the open and the window`() {
        val model = build(ticker(last = 110.0, changePct = 1.5, open = 100.0), spark = listOf(50f, 110f))

        assertEquals("+1.50%", model.change)
    }

    @Test
    fun `a state written before hasChange existed still decodes`() {
        val legacy = """{"key":"kraken:BTC/EUR","exchange":"KRAKEN","pair":"BTC/EUR","price":"1.00",""" +
            """"change":"+1.00%","changeUp":true,"hasData":true,"stale":false,"updatedLabel":"",""" +
            """"backgroundArgb":0,"showSparkline":true}"""

        assertTrue(WidgetJson.format.decodeFromString<WidgetRenderModel>(legacy).hasChange)
    }

    @Test
    fun `background carries the configured colour and transparency`() {
        val config = CONFIG.copy(backgroundArgb = 0xFF000000L, alpha = 0f)

        assertEquals(0x00000000, WidgetModelFactory.build(config, ticker(), 2, emptyList(), NOW).backgroundArgb)
    }

    @Test
    fun `render model survives a json round trip`() {
        val model = build(ticker(), spark = listOf(1f, 2f, 3f))
        val json = WidgetJson.format.encodeToString(model)

        assertEquals(model, WidgetJson.format.decodeFromString<WidgetRenderModel>(json))
    }

    // ---- helpers -------------------------------------------------------------------------

    private fun build(ticker: Ticker?, precision: Int? = 2, spark: List<Float> = emptyList()) =
        WidgetModelFactory.build(CONFIG, ticker, precision, spark, NOW, ZoneId.of("UTC"))

    private fun ticker(
        last: Double = 65_609.70,
        changePct: Double? = 6.52,
        open: Double? = null,
        timestamp: Long = NOW,
    ) = Ticker(
        key = CONFIG.key,
        last = last,
        open24h = open,
        changePct24h = changePct,
        timestamp = timestamp,
    )

    private companion object {
        const val NOW = 1_787_407_509_000L
        val CONFIG = WidgetConfig(MarketKey("kraken:BTC/EUR"), TGColors.SURFACE, 1f, true)
    }
}
