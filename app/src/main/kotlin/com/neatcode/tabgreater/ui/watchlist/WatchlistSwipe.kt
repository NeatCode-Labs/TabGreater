package com.neatcode.tabgreater.ui.watchlist

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.neatcode.tabgreater.core.model.Watchlist
import kotlin.math.abs

private const val SWIPE_WIDTH_FRACTION = 0.20f
private val MIN_SWIPE_DISTANCE = 56.dp
private val MAX_SWIPE_DISTANCE = 120.dp

/**
 * Adds horizontal navigation over the watchlist content. The grid keeps ownership of vertical
 * scrolling and each tile keeps its tap and long-press reorder gestures; this detector only wins
 * when a horizontal drag crosses touch slop. Selection mode disables it so bulk actions and tile
 * dragging stay predictable.
 */
@Composable
internal fun Modifier.watchlistSwipe(
    watchlists: List<Watchlist>,
    selectedId: Long?,
    enabled: Boolean,
    onSelect: (Long) -> Unit,
): Modifier {
    if (!enabled || selectedId == null || watchlists.size < 2 || watchlists.none { it.id == selectedId }) {
        return this
    }

    val watchlistIds = remember(watchlists) { watchlists.map(Watchlist::id) }
    val currentOnSelect by rememberUpdatedState(onSelect)
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val minDistancePx = with(density) { MIN_SWIPE_DISTANCE.toPx() }
    val maxDistancePx = with(density) { MAX_SWIPE_DISTANCE.toPx() }

    return pointerInput(watchlistIds, selectedId, layoutDirection, minDistancePx, maxDistancePx) {
        var horizontalDistance = 0f
        detectHorizontalDragGestures(
            onHorizontalDrag = { change, dragAmount ->
                horizontalDistance += dragAmount
                change.consume()
            },
            onDragEnd = {
                val threshold = (size.width * SWIPE_WIDTH_FRACTION).coerceIn(minDistancePx, maxDistancePx)
                adjacentWatchlistId(
                    watchlistIds = watchlistIds,
                    selectedId = selectedId,
                    horizontalDistance = horizontalDistance,
                    threshold = threshold,
                    layoutDirection = layoutDirection,
                )?.let(currentOnSelect)
                horizontalDistance = 0f
            },
            onDragCancel = { horizontalDistance = 0f },
        )
    }
}

/** Returns the immediately adjacent list for a completed swipe, or `null` when it is a no-op. */
internal fun adjacentWatchlistId(
    watchlistIds: List<Long>,
    selectedId: Long?,
    horizontalDistance: Float,
    threshold: Float,
    layoutDirection: LayoutDirection = LayoutDirection.Ltr,
): Long? {
    if (watchlistIds.size < 2 || selectedId == null || threshold <= 0f) return null
    if (abs(horizontalDistance) < threshold) return null

    val selectedIndex = watchlistIds.indexOf(selectedId)
    if (selectedIndex < 0) return null

    // Follow the visual order of the tab row in both layout directions.
    val towardsNext = if (layoutDirection == LayoutDirection.Ltr) horizontalDistance < 0f else horizontalDistance > 0f
    val nextIndex = selectedIndex + if (towardsNext) 1 else -1
    return watchlistIds.getOrNull(nextIndex)
}
