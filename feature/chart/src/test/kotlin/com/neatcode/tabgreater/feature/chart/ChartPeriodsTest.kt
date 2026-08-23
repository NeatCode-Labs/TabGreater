package com.neatcode.tabgreater.feature.chart

import com.neatcode.tabgreater.core.model.Timeframe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChartPeriodsTest {

    @Test
    fun `every timeframe maps to a klinecharts period`() {
        val expected = mapOf(
            Timeframe.M1 to ChartPeriod(1, "minute"),
            Timeframe.M5 to ChartPeriod(5, "minute"),
            Timeframe.M15 to ChartPeriod(15, "minute"),
            Timeframe.M30 to ChartPeriod(30, "minute"),
            Timeframe.H1 to ChartPeriod(1, "hour"),
            Timeframe.H4 to ChartPeriod(4, "hour"),
            Timeframe.D1 to ChartPeriod(1, "day"),
            Timeframe.W1 to ChartPeriod(1, "week"),
            Timeframe.MN1 to ChartPeriod(1, "month"),
        )
        assertEquals(Timeframe.entries.size, expected.size)
        expected.forEach { (timeframe, period) -> assertEquals(period, ChartPeriods.of(timeframe)) }
    }

    @Test
    fun `mapping round trips both ways`() {
        Timeframe.entries.forEach { timeframe ->
            val period = ChartPeriods.of(timeframe)
            assertEquals(timeframe, ChartPeriods.toTimeframe(period))
            assertEquals(timeframe, ChartPeriods.toTimeframe(period.span, period.unit))
        }
    }

    @Test
    fun `periods we do not serve resolve to null`() {
        assertNull(ChartPeriods.toTimeframe(1, "second"))
        assertNull(ChartPeriods.toTimeframe(1, "year"))
        assertNull(ChartPeriods.toTimeframe(2, "hour"))
        assertNull(ChartPeriods.toTimeframe(15, "hour"))
        assertNull(ChartPeriods.toTimeframe(1, "MINUTE"))
    }

    @Test
    fun `toolbar shows the nine timeframes shortest first`() {
        assertEquals(
            listOf("1m", "5m", "15m", "30m", "1H", "4H", "1D", "1W", "1M"),
            ChartPeriods.toolbarOrder.map { it.label },
        )
    }
}
