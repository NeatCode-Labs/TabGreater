package com.neatcode.tabgreater.widget

import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.TGColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetConfigTest {

    @Test
    fun `json round trip keeps every field`() {
        val config = WidgetConfig(
            key = MarketKey("kraken:BTC/EUR"),
            backgroundArgb = 0xFF0F1A24L,
            alpha = 0.65f,
            showSparkline = false,
        )

        val json = WidgetJson.format.encodeToString(config)
        assertEquals(config, WidgetJson.format.decodeFromString<WidgetConfig>(json))
    }

    @Test
    fun `market key serialises as the canonical string`() {
        val json = WidgetJson.format.encodeToString(WidgetConfig(MarketKey("binance:ETH/USDT")))
        assertTrue(json, json.contains("\"binance:ETH/USDT\""))
    }

    @Test
    fun `unknown fields from an older build do not break decoding`() {
        val json = """{"key":"gate:BTC/USDT","backgroundArgb":4280295713,"alpha":1.0,"showSparkline":true,"period":"24h"}"""
        val config = WidgetJson.format.decodeFromString<WidgetConfig>(json)
        assertEquals(MarketKey("gate:BTC/USDT"), config.key)
    }

    @Test
    fun `blended argb folds the alpha slider into the alpha channel`() {
        assertEquals(0xFF202121.toInt(), WidgetConfig(KEY, TGColors.SURFACE, 1f).blendedArgb)
        assertEquals(0x00202121, WidgetConfig(KEY, TGColors.SURFACE, 0f).blendedArgb)
        assertEquals(0x80202121.toInt(), WidgetConfig(KEY, TGColors.SURFACE, 0.502f).blendedArgb)
    }

    @Test
    fun `background palette starts with the tile surface and includes the accent colours`() {
        assertEquals(TGColors.SURFACE, WidgetConfig.BACKGROUNDS.first())
        assertTrue(WidgetConfig.BACKGROUNDS.containsAll(TGColors.ACCENT_PALETTE))
        assertNotNull(WidgetConfig.BACKGROUNDS.firstOrNull { it == 0xFF000000L })
    }

    private companion object {
        val KEY = MarketKey("kraken:BTC/EUR")
    }
}
