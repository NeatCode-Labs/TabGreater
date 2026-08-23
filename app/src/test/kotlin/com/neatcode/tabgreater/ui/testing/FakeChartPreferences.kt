package com.neatcode.tabgreater.ui.testing

import com.neatcode.tabgreater.core.model.Timeframe
import com.neatcode.tabgreater.feature.chart.CandleType
import com.neatcode.tabgreater.feature.chart.ChartPreferences
import com.neatcode.tabgreater.feature.chart.ChartSettings
import com.neatcode.tabgreater.feature.chart.IndicatorCatalogue
import com.neatcode.tabgreater.feature.chart.IndicatorSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [ChartPreferences] so the chart view model can be exercised without DataStore. */
class FakeChartPreferences(initial: ChartSettings = ChartSettings.DEFAULT) : ChartPreferences {

    private val state = MutableStateFlow(initial)

    override val settings: Flow<ChartSettings> = state

    /** Current value, for assertions. */
    val value: ChartSettings get() = state.value

    override suspend fun setTimeframe(timeframe: Timeframe) {
        state.value = state.value.copy(timeframe = timeframe)
    }

    override suspend fun setCandleType(type: CandleType) {
        state.value = state.value.copy(candleType = type)
    }

    override suspend fun setLogScale(enabled: Boolean) {
        state.value = state.value.copy(logScale = enabled)
    }

    override suspend fun setIndicators(indicators: List<IndicatorSpec>) {
        state.value = state.value.copy(indicators = IndicatorCatalogue.sanitize(indicators))
    }
}
