package com.neatcode.tabgreater.feature.chart

import com.neatcode.tabgreater.core.model.Timeframe
import kotlinx.serialization.Serializable

/**
 * A KLineChart `Period` (`{span, type}`) — the only shape the JS side understands.
 *
 * [unit] is KLineChart's `PeriodType`: `second | minute | hour | day | week | month | year`;
 * TabGreater only ever uses `minute`, `hour`, `day`, `week` and `month`.
 */
@Serializable
data class ChartPeriod(val span: Int, val unit: String)

/** Bidirectional mapping between [Timeframe] and KLineChart's `Period`. */
object ChartPeriods {

    const val UNIT_MINUTE = "minute"
    const val UNIT_HOUR = "hour"
    const val UNIT_DAY = "day"
    const val UNIT_WEEK = "week"
    const val UNIT_MONTH = "month"

    private val byTimeframe: Map<Timeframe, ChartPeriod> = mapOf(
        Timeframe.M1 to ChartPeriod(1, UNIT_MINUTE),
        Timeframe.M5 to ChartPeriod(5, UNIT_MINUTE),
        Timeframe.M15 to ChartPeriod(15, UNIT_MINUTE),
        Timeframe.M30 to ChartPeriod(30, UNIT_MINUTE),
        Timeframe.H1 to ChartPeriod(1, UNIT_HOUR),
        Timeframe.H4 to ChartPeriod(4, UNIT_HOUR),
        Timeframe.D1 to ChartPeriod(1, UNIT_DAY),
        Timeframe.W1 to ChartPeriod(1, UNIT_WEEK),
        Timeframe.MN1 to ChartPeriod(1, UNIT_MONTH),
    )

    private val byPeriod: Map<ChartPeriod, Timeframe> = byTimeframe.entries.associate { it.value to it.key }

    /** The KLineChart period for [timeframe]; total, every timeframe maps. */
    fun of(timeframe: Timeframe): ChartPeriod = byTimeframe.getValue(timeframe)

    /** The timeframe a `getBars`/`subscribeBar` request names, or `null` for an unknown period. */
    fun toTimeframe(span: Int, unit: String): Timeframe? = byPeriod[ChartPeriod(span, unit)]

    /** The timeframe [period] names, or `null` when the JS side sent something we do not serve. */
    fun toTimeframe(period: ChartPeriod): Timeframe? = byPeriod[period]

    /** The nine timeframes the chart toolbar offers, shortest first. */
    val toolbarOrder: List<Timeframe> = listOf(
        Timeframe.M1, Timeframe.M5, Timeframe.M15, Timeframe.M30,
        Timeframe.H1, Timeframe.H4, Timeframe.D1, Timeframe.W1, Timeframe.MN1,
    )
}
