package com.neatcode.tabgreater.ui.watchlist

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import com.neatcode.tabgreater.R
import com.neatcode.tabgreater.core.model.SortMode
import com.neatcode.tabgreater.core.model.SparkPeriod
import com.neatcode.tabgreater.core.model.TileSize
import com.neatcode.tabgreater.ui.components.TGBottomSheet
import com.neatcode.tabgreater.ui.components.TGSheetOption
import com.neatcode.tabgreater.ui.icons.TGIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Which chip / action sheet is on screen; `NONE` means the grid has the screen to itself. */
enum class WatchlistSheet { NONE, PERIOD, SIZE, SORT, COLOUR, MOVE }

/** The sheet behind the first chip: it picks the sparkline window and the % reference. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodSheet(
    current: SparkPeriod,
    onPick: (SparkPeriod) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val dismiss = rememberSheetDismiss(sheetState)
    TGBottomSheet(
        onDismiss = onDismiss,
        title = stringResource(R.string.sheet_period_title),
        sheetState = sheetState,
    ) {
        SparkPeriod.entries.forEach { period ->
            TGSheetOption(
                label = period.label,
                checked = period == current,
                onClick = { dismiss { onPick(period) } },
            )
        }
    }
}

/** The sheet behind the second chip: tile layout — Small, Compact, Medium, Large. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TileSizeSheet(
    current: TileSize,
    onPick: (TileSize) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val dismiss = rememberSheetDismiss(sheetState)
    TGBottomSheet(
        onDismiss = onDismiss,
        title = stringResource(R.string.sheet_size_title),
        sheetState = sheetState,
    ) {
        TileSize.entries.forEach { size ->
            TGSheetOption(
                label = size.label,
                checked = size == current,
                onClick = { dismiss { onPick(size) } },
                leadingIcon = TGIcons.forTileSize(size),
            )
        }
    }
}

/** The sheet behind the third chip: tile order. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortSheet(
    current: SortMode,
    onPick: (SortMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val dismiss = rememberSheetDismiss(sheetState)
    TGBottomSheet(
        onDismiss = onDismiss,
        title = stringResource(R.string.sheet_sort_title),
        sheetState = sheetState,
    ) {
        SortMode.entries.forEach { sort ->
            TGSheetOption(
                label = sort.label,
                checked = sort == current,
                onClick = { dismiss { onPick(sort) } },
            )
        }
    }
}

/**
 * Returns a "slide the sheet away, *then* do this" helper. Applying the change only after the
 * hide animation has finished is what keeps the sheet from blinking out of existence, and the
 * `isVisible` guard stops the action from firing if the sheet was torn down mid-animation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun rememberSheetDismiss(sheetState: SheetState): ((() -> Unit) -> Unit) {
    val scope: CoroutineScope = rememberCoroutineScope()
    return remember(sheetState, scope) {
        { action: () -> Unit ->
            scope.launch { sheetState.hide() }.invokeOnCompletion {
                if (!sheetState.isVisible) action()
            }
        }
    }
}
