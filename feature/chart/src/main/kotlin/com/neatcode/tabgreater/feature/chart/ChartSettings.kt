package com.neatcode.tabgreater.feature.chart

import com.neatcode.tabgreater.core.model.Timeframe
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** How the price series is drawn. [jsValue] is KLineChart's `CandleType`. */
@Serializable
enum class CandleType(val jsValue: String) {
    CANDLE_SOLID("candle_solid"),
    CANDLE_STROKE("candle_stroke"),
    OHLC("ohlc"),
    AREA("area"),
    ;

    companion object {
        /** Parses a persisted name, falling back to [CANDLE_SOLID] for anything unknown. */
        fun fromNameOrDefault(name: String?): CandleType =
            entries.firstOrNull { it.name == name } ?: CANDLE_SOLID
    }
}

/**
 * The chart's user preferences. Global rather than per market: opening any
 * pair keeps the timeframe, the candle type, the y-axis scale and the indicator set you left.
 */
@Serializable
data class ChartSettings(
    val timeframe: Timeframe = Timeframe.H1,
    val candleType: CandleType = CandleType.CANDLE_SOLID,
    val logScale: Boolean = false,
    val indicators: List<IndicatorSpec> = IndicatorCatalogue.defaults,
) {
    companion object {
        val DEFAULT = ChartSettings()
    }
}

/**
 * JSON codec for the parts of [ChartSettings] that are not primitives. Unknown keys are ignored
 * and unparsable input degrades to the defaults, so a settings file from another build can never
 * stop the chart from opening.
 */
object ChartSettingsCodec {

    val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encodeIndicators(indicators: List<IndicatorSpec>): String =
        json.encodeToString(indicators)

    fun decodeIndicators(raw: String?): List<IndicatorSpec> {
        if (raw.isNullOrBlank()) return IndicatorCatalogue.defaults
        val parsed = runCatching { json.decodeFromString<List<IndicatorSpec>>(raw) }.getOrNull()
            ?: return IndicatorCatalogue.defaults
        return IndicatorCatalogue.sanitize(parsed)
    }

    fun encode(settings: ChartSettings): String = json.encodeToString(settings)

    fun decode(raw: String?): ChartSettings {
        if (raw.isNullOrBlank()) return ChartSettings.DEFAULT
        return runCatching { json.decodeFromString<ChartSettings>(raw) }
            .map { it.copy(indicators = IndicatorCatalogue.sanitize(it.indicators)) }
            .getOrDefault(ChartSettings.DEFAULT)
    }
}
