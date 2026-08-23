package com.neatcode.tabgreater.feature.chart

import com.neatcode.tabgreater.core.model.Timeframe
import org.junit.Assert.assertEquals
import org.junit.Test

class ChartSettingsTest {

    @Test
    fun `settings survive a JSON round trip`() {
        val settings = ChartSettings(
            timeframe = Timeframe.M15,
            candleType = CandleType.AREA,
            logScale = true,
            indicators = listOf(
                IndicatorCatalogue.find("MA")!!,
                IndicatorCatalogue.find("VOL")!!,
                IndicatorCatalogue.find("MACD")!!,
            ),
        )
        assertEquals(settings, ChartSettingsCodec.decode(ChartSettingsCodec.encode(settings)))
    }

    @Test
    fun `defaults survive a JSON round trip`() {
        assertEquals(
            ChartSettings.DEFAULT,
            ChartSettingsCodec.decode(ChartSettingsCodec.encode(ChartSettings.DEFAULT)),
        )
    }

    @Test
    fun `the default chart is 1H solid candles with volume and a linear axis`() {
        assertEquals(Timeframe.H1, ChartSettings.DEFAULT.timeframe)
        assertEquals(CandleType.CANDLE_SOLID, ChartSettings.DEFAULT.candleType)
        assertEquals(false, ChartSettings.DEFAULT.logScale)
        assertEquals(listOf(IndicatorCatalogue.VOL), ChartSettings.DEFAULT.indicators.map { it.name })
    }

    @Test
    fun `indicators survive a JSON round trip`() {
        val indicators = listOf(
            IndicatorCatalogue.find("BOLL")!!,
            IndicatorCatalogue.find("VOL")!!,
            IndicatorCatalogue.find("RSI")!!,
        )
        val decoded = ChartSettingsCodec.decodeIndicators(ChartSettingsCodec.encodeIndicators(indicators))
        // sanitize() re-imposes catalogue order, so compare as sets of names plus the params.
        assertEquals(indicators.map { it.name }.toSet(), decoded.map { it.name }.toSet())
        decoded.forEach { assertEquals(IndicatorCatalogue.find(it.name), it) }
    }

    @Test
    fun `an empty indicator list round trips as empty`() {
        assertEquals(emptyList<IndicatorSpec>(), ChartSettingsCodec.decodeIndicators("[]"))
    }

    @Test
    fun `missing or unreadable settings degrade to the defaults`() {
        assertEquals(ChartSettings.DEFAULT, ChartSettingsCodec.decode(null))
        assertEquals(ChartSettings.DEFAULT, ChartSettingsCodec.decode(""))
        assertEquals(ChartSettings.DEFAULT, ChartSettingsCodec.decode("{"))
        assertEquals(IndicatorCatalogue.defaults, ChartSettingsCodec.decodeIndicators(null))
        assertEquals(IndicatorCatalogue.defaults, ChartSettingsCodec.decodeIndicators("nonsense"))
    }

    @Test
    fun `unknown keys and unknown indicators from another build are dropped`() {
        val raw = """
            {"timeframe":"D1","candleType":"OHLC","logScale":false,"future":1,
             "indicators":[{"name":"ATR","calcParams":[14],"pane":"SUB"},
                           {"name":"VOL","calcParams":[5,10,20],"pane":"SUB"}]}
        """.trimIndent()
        val decoded = ChartSettingsCodec.decode(raw)
        assertEquals(Timeframe.D1, decoded.timeframe)
        assertEquals(CandleType.OHLC, decoded.candleType)
        assertEquals(listOf(IndicatorCatalogue.VOL), decoded.indicators.map { it.name })
    }

    @Test
    fun `a stored indicator with tampered params is restored from the catalogue`() {
        val raw = """[{"name":"MA","calcParams":[999],"pane":"SUB"}]"""
        val decoded = ChartSettingsCodec.decodeIndicators(raw)
        assertEquals(listOf(IndicatorCatalogue.find("MA")), decoded)
    }

    @Test
    fun `candle types map to the klinecharts names`() {
        assertEquals("candle_solid", CandleType.CANDLE_SOLID.jsValue)
        assertEquals("candle_stroke", CandleType.CANDLE_STROKE.jsValue)
        assertEquals("ohlc", CandleType.OHLC.jsValue)
        assertEquals("area", CandleType.AREA.jsValue)
    }

    @Test
    fun `an unknown persisted candle type falls back to solid candles`() {
        assertEquals(CandleType.CANDLE_SOLID, CandleType.fromNameOrDefault(null))
        assertEquals(CandleType.CANDLE_SOLID, CandleType.fromNameOrDefault("HEIKIN_ASHI"))
        assertEquals(CandleType.OHLC, CandleType.fromNameOrDefault("OHLC"))
    }
}
