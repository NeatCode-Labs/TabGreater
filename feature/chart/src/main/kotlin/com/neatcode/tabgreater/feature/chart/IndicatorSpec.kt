package com.neatcode.tabgreater.feature.chart

import kotlinx.serialization.Serializable

/** Which pane an indicator is drawn in; the JSON value is what `chart.js` expects. */
@Serializable
enum class IndicatorPane {
    /** Overlaid on the candles (`candle_pane`). */
    MAIN,

    /** Its own stacked pane under the candles (`tg_sub_<name>`). */
    SUB,
    ;

    /** `"main"` / `"sub"` — the string `applyIndicators` in `chart.js` switches on. */
    val jsValue: String get() = if (this == MAIN) "main" else "sub"
}

/**
 * One indicator as `chart.js` wants it: `{ name, calcParams, pane }`.
 *
 * [name] is the KLineChart registered indicator name (`MA`, `BOLL`, `MACD`, …) — never a label.
 * [calcParams] are the periods the indicator is calculated with; an empty list means "the
 * library default".
 */
@Serializable
data class IndicatorSpec(
    val name: String,
    val calcParams: List<Int> = emptyList(),
    val pane: IndicatorPane = IndicatorPane.SUB,
)

/**
 * The 11 built-in indicators the app exposes, with the exact default
 * `calcParams` KLineChart 10.0.2 ships.
 *
 * `ATR` and Heikin-Ashi do not exist in 10.0.2; `SMA` is a weighted average, not a simple one,
 * so `MA` is the entry that carries the "moving average" role.
 */
object IndicatorCatalogue {

    const val VOL = "VOL"

    val entries: List<IndicatorSpec> = listOf(
        IndicatorSpec("MA", listOf(5, 10, 30, 60), IndicatorPane.MAIN),
        IndicatorSpec("EMA", listOf(6, 12, 20), IndicatorPane.MAIN),
        IndicatorSpec("BOLL", listOf(20, 2), IndicatorPane.MAIN),
        IndicatorSpec("SAR", listOf(2, 2, 20), IndicatorPane.MAIN),
        IndicatorSpec(VOL, listOf(5, 10, 20), IndicatorPane.SUB),
        IndicatorSpec("MACD", listOf(12, 26, 9), IndicatorPane.SUB),
        IndicatorSpec("RSI", listOf(6, 12, 24), IndicatorPane.SUB),
        IndicatorSpec("KDJ", listOf(9, 3, 3), IndicatorPane.SUB),
        IndicatorSpec("CCI", listOf(20), IndicatorPane.SUB),
        IndicatorSpec("DMI", listOf(14, 6), IndicatorPane.SUB),
        IndicatorSpec("OBV", listOf(30), IndicatorPane.SUB),
    )

    private val byName: Map<String, IndicatorSpec> = entries.associateBy { it.name }

    /** The catalogue entry for [name], or `null` for an indicator we do not expose. */
    fun find(name: String): IndicatorSpec? = byName[name]

    /** Volume only: what a freshly opened chart shows. */
    val defaults: List<IndicatorSpec> = listOf(byName.getValue(VOL))

    /**
     * Drops entries that are not in the catalogue and restores each kept entry's pane and
     * parameters, so a settings file written by an older build can never create a pane
     * `chart.js` cannot address.
     */
    fun sanitize(specs: List<IndicatorSpec>): List<IndicatorSpec> =
        entries.filter { catalogue -> specs.any { it.name == catalogue.name } }
}
