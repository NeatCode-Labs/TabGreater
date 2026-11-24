package com.neatcode.tabgreater.ui.watchlist

import androidx.compose.animation.core.snap
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Uses Pager's normal fling behavior, restoring the watchlist's short-swipe threshold. At a system
 * motion scale of zero, make the final settle itself a zero-duration snap as well.
 */
@Composable
internal fun rememberWatchlistPagerFlingBehavior(
    state: PagerState,
    pageWidth: Dp,
    motionScaleFactor: Float,
): TargetedFlingBehavior {
    val threshold = remember(pageWidth) {
        if (pageWidth <= 0.dp || !pageWidth.value.isFinite()) {
            0.2f
        } else {
            ((pageWidth * 0.2f).coerceIn(56.dp, 120.dp) / pageWidth).coerceIn(0f, 1f)
        }
    }

    return if (motionScaleFactor <= 0f) {
        PagerDefaults.flingBehavior(
            state = state,
            snapAnimationSpec = snap(),
            snapPositionalThreshold = threshold,
        )
    } else {
        PagerDefaults.flingBehavior(
            state = state,
            snapPositionalThreshold = threshold,
        )
    }
}
