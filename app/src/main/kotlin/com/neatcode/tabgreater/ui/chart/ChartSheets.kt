package com.neatcode.tabgreater.ui.chart

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.neatcode.tabgreater.R
import com.neatcode.tabgreater.feature.chart.CandleType
import com.neatcode.tabgreater.feature.chart.IndicatorCatalogue
import com.neatcode.tabgreater.feature.chart.IndicatorSpec
import com.neatcode.tabgreater.ui.components.TGBottomSheet
import com.neatcode.tabgreater.ui.components.TGSheetOption

/** Which bottom sheet the chart toolbar has open. */
enum class ChartSheet { CANDLE_TYPE, INDICATORS }

/** The chart's two bottom sheets; nothing is drawn while [sheet] is `null`. */
@Composable
fun ChartSheets(
    sheet: ChartSheet?,
    state: ChartUiState,
    onDismiss: () -> Unit,
    onCandleType: (CandleType) -> Unit,
    onToggleIndicator: (String) -> Unit,
    immersive: Boolean = false,
) {
    when (sheet) {
        null -> Unit
        ChartSheet.CANDLE_TYPE -> CandleTypeSheet(
            selected = state.settings.candleType,
            onSelect = { type ->
                onCandleType(type)
                onDismiss()
            },
            onDismiss = onDismiss,
            immersive = immersive,
        )
        ChartSheet.INDICATORS -> IndicatorsSheet(
            selected = state.settings.indicators,
            onToggle = onToggleIndicator,
            onDismiss = onDismiss,
            immersive = immersive,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CandleTypeSheet(
    selected: CandleType,
    onSelect: (CandleType) -> Unit,
    onDismiss: () -> Unit,
    immersive: Boolean = false,
) {
    TGBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.chart_type_title), immersive = immersive) {
        CandleType.entries.forEach { type ->
            TGSheetOption(
                label = stringResource(type.labelRes),
                checked = type == selected,
                onClick = { onSelect(type) },
            )
        }
    }
}

/**
 * The 11 built-in indicators as checkable rows, each showing the parameters it is calculated
 * with (`5 · 10 · 30 · 60`). The sheet stays open so several can be toggled in one visit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IndicatorsSheet(
    selected: List<IndicatorSpec>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
    immersive: Boolean = false,
) {
    TGBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.chart_indicators_title), immersive = immersive) {
        IndicatorCatalogue.entries.forEach { spec ->
            TGSheetOption(
                label = spec.name,
                checked = selected.any { it.name == spec.name },
                onClick = { onToggle(spec.name) },
                trailingText = spec.paramsLabel,
            )
        }
    }
}

/** `5 · 10 · 30 · 60`, or `null` for an indicator without parameters. */
private val IndicatorSpec.paramsLabel: String?
    get() = calcParams.takeIf { it.isNotEmpty() }?.joinToString(" · ")

private val CandleType.labelRes: Int
    get() = when (this) {
        CandleType.CANDLE_SOLID -> R.string.chart_type_candles
        CandleType.CANDLE_STROKE -> R.string.chart_type_hollow
        CandleType.OHLC -> R.string.chart_type_ohlc
        CandleType.AREA -> R.string.chart_type_area
    }
