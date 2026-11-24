package com.neatcode.tabgreater.ui.watchlist

import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Keep the short-swipe threshold and use Pager's normal settling spring at every motion scale. */
@Composable
internal fun rememberWatchlistPagerFlingBehavior(
    state: PagerState,
    pageWidth: Dp,
): TargetedFlingBehavior {
    val threshold = remember(pageWidth) {
        if (pageWidth <= 0.dp || !pageWidth.value.isFinite()) {
            0.2f
        } else {
            ((pageWidth * 0.2f).coerceIn(56.dp, 120.dp) / pageWidth).coerceIn(0f, 1f)
        }
    }

    return PagerDefaults.flingBehavior(state = state, snapPositionalThreshold = threshold)
}
