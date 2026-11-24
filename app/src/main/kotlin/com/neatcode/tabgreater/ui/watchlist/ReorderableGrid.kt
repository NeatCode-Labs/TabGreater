package com.neatcode.tabgreater.ui.watchlist

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.neatcode.tabgreater.core.model.TGDimens
import kotlinx.coroutines.flow.collect

/** Elevation of the tile that is following the finger. */
private const val DRAG_ELEVATION_DP = 8f

/** The lifted tile grows by this much while it follows the finger, and animates back on drop. */
private const val DRAG_SCALE = 1.03f
private const val REORDER_ANIMATION_DURATION_MS = 180L
private const val REORDER_ANIMATION_DURATION_NS = REORDER_ANIMATION_DURATION_MS * 1_000_000L
private const val PENDING_LAYOUT_TIMEOUT_NS = 120_000_000L

/** Auto-scroll starts once the dragged tile comes this close to an edge (fraction of its height). */
private const val EDGE_MARGIN_FRACTION = 0.6f

/** Pixels per frame at the steepest, so a long list stays controllable. */
private const val MAX_AUTO_SCROLL_PX = 24f
private const val AUTO_SCROLL_FACTOR = 0.25f

/** Stable-keyed layout position used to animate sibling slots without relying on system scale. */
internal data class ReorderItemPosition(
    val itemId: Long,
    val index: Int,
    val offset: Offset,
)

/**
 * How a finished gesture is read: it was a **drag** (commit the new order) as soon as it did
 * anything a long press cannot do, and a plain long press only when it did none of them.
 *
 * The net offset alone is not enough — a finger that walks a tile to the far end of the list and
 * back lands within a pixel of where it started, yet dozens of moves have already been reported
 * and dropping them would leave the grid in an order that is shown but never persisted.
 *
 * @param reportedMove at least one reorder step was reported during the gesture.
 * @param exceededSlop the finger left the [touchSlop] circle at some point, even if it came back.
 * @param netDistance distance between the finger's last position and where the drag started.
 */
internal fun endedAsDrag(
    reportedMove: Boolean,
    exceededSlop: Boolean,
    netDistance: Float,
    touchSlop: Float,
): Boolean = reportedMove || exceededSlop || netDistance >= touchSlop

/**
 * Drag-to-reorder for a [androidx.compose.foundation.lazy.grid.LazyVerticalGrid] whose items are
 * keyed by item id, plus the long-press that opens selection mode.
 *
 * One gesture pipeline serves both: `detectDragGesturesAfterLongPress` reports the long press, and
 * on release [endedAsDrag] decides whether it was a drag (commit the new order) or a plain long
 * press (tick the tile). [consumeLongPress] lets the tap handler on the same tile ignore the
 * release that ends such a gesture — `combinedClickable` cannot be used here because its own long
 * press would swallow the drag.
 *
 * Until the finger has left the touch-slop circle the gesture is still only a press: nothing
 * scrolls and nothing is reordered, so holding a tile still in the top or bottom row cannot walk
 * it through the list.
 */
@Stable
class ReorderState internal constructor(
    internal val gridState: LazyGridState,
    private val haptics: HapticFeedback,
    private val onMove: (from: Int, to: Int) -> Unit,
    private val onDragEnd: () -> Unit,
    private val onLongPress: (itemId: Long) -> Unit,
) {
    /** Id of the tile under the finger; `null` when nothing is being dragged. */
    var draggingItemId: Long? by mutableStateOf(null)
        private set

    private var settlingItemId: Long? by mutableStateOf(null)

    /** Suppress scaled LazyGrid placement while this state animates reorder positions itself. */
    internal val ownsPlacementAnimation: Boolean
        get() = draggingItemId != null || settlingItemId != null ||
            placementStartOffsets.isNotEmpty() || pendingPositions != null

    internal val animationActive: Boolean
        get() = draggingItemId != null || settlingItemId != null ||
            placementStartOffsets.isNotEmpty() || pendingPositions != null

    internal val hasPendingMove: Boolean
        get() = pendingPositions != null

    /** Distance travelled since the long press fired. */
    private var dragOffset by mutableStateOf(Offset.Zero)

    /** Where the dragged tile sat when the drag started (viewport coordinates). */
    private var startOffset = IntOffset.Zero

    /** Absolute viewport position under the finger at release, before the target slot settles. */
    private var settleStartOffset = Offset.Zero
    private var settleStartNanos: Long? = null
    private var settleProgress by mutableFloatStateOf(1f)
    private var settleStartScale = 1f

    private var liftStartNanos: Long? = null
    private var liftProgress by mutableFloatStateOf(1f)
    private var liftStartScale = 1f

    /** Start displacement per stable item key for the latest sibling placement transition. */
    private var placementStartOffsets by mutableStateOf(emptyMap<Long, Offset>())
    private var placementStartNanos: Long? = null
    private var placementProgress by mutableFloatStateOf(1f)

    private var pendingPositions: Map<Long, ReorderItemPosition>? by mutableStateOf(null)
    private var pendingDraggedItemId: Long? = null
    private var pendingTargetIndex: Int? = null
    private var pendingLayoutStartNanos: Long? = null

    /** Captured from the pointer input scope when the gesture starts. */
    private var touchSlop = 0f

    /** `true` once the finger has left the slop circle — the press has become a real drag. */
    private var exceededSlop = false

    /** `true` once [checkForMove] has actually reported a reorder step. */
    private var reportedMove = false

    /** Set when a long press fires, cleared by the tap handler or by the next finger-down. */
    private var longPressActive = false

    /**
     * `true` when the release being handled ends a long press (or a drag) instead of a tap, and
     * clears the flag. The tap handler consumes it rather than only reading it, because the tile
     * that receives the next tap may have its drag detector disabled — in selection mode — and
     * would otherwise never clear the flag.
     */
    fun consumeLongPress(): Boolean {
        val wasLongPress = longPressActive
        longPressActive = false
        return wasLongPress
    }

    internal fun onPointerDown() {
        longPressActive = false
    }

    internal fun onDragStart(itemId: Long, touchSlop: Float) {
        val info = itemInfo(itemId) ?: return
        val currentScale = scaleFor(itemId)
        longPressActive = true
        draggingItemId = itemId
        val currentPosition = Offset(info.offset.x.toFloat(), info.offset.y.toFloat()) + placementOffsetFor(itemId)
        startOffset = IntOffset(currentPosition.x.toInt(), currentPosition.y.toInt())
        if (settlingItemId == itemId) {
            settlingItemId = null
            settleStartNanos = null
            settleProgress = 1f
        }
        liftStartScale = currentScale
        liftProgress = 0f
        liftStartNanos = null
        dragOffset = Offset.Zero
        this.touchSlop = touchSlop
        exceededSlop = false
        reportedMove = false
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    internal fun onDrag(delta: Offset) {
        if (draggingItemId == null) return
        dragOffset += delta
        if (!exceededSlop && dragOffset.getDistance() >= touchSlop) exceededSlop = true
        checkForMove()
    }

    /**
     * End of the gesture: a finger that never became a drag was a long press (enter selection
     * mode), anything else is a drag and its order is committed — see [endedAsDrag].
     */
    internal fun onDragStop(itemId: Long) {
        val wasDragging = draggingItemId != null
        val wasDrag = endedAsDrag(reportedMove, exceededSlop, dragOffset.getDistance(), touchSlop)
        if (wasDragging) {
            settleStartScale = scaleFor(itemId)
            settlingItemId = itemId
            settleStartOffset = Offset(startOffset.x.toFloat(), startOffset.y.toFloat()) + dragOffset
            settleStartNanos = null
            settleProgress = 0f
        }
        draggingItemId = null
        dragOffset = Offset.Zero
        exceededSlop = false
        reportedMove = false
        if (!wasDragging) return
        if (wasDrag) onDragEnd() else onLongPress(itemId)
    }

    /**
     * Where the dragged tile has to be drawn: the difference between the position the finger has
     * put it in and the slot the grid currently lays it out in. Because it is a difference, a
     * reorder or an auto-scroll underneath the tile does not make it jump.
     */
    internal fun translationFor(itemId: Long): Offset {
        if (itemId != draggingItemId && itemId != settlingItemId &&
            placementStartOffsets[itemId] == null && pendingPositions?.containsKey(itemId) != true
        ) {
            return Offset.Zero
        }
        val info = itemInfo(itemId) ?: return Offset.Zero
        val layoutOffset = Offset(info.offset.x.toFloat(), info.offset.y.toFloat())
        if (itemId == draggingItemId) {
            return Offset(startOffset.x.toFloat() + dragOffset.x, startOffset.y.toFloat() + dragOffset.y) - layoutOffset
        }
        if (itemId == settlingItemId) {
            val remaining = 1f - FastOutSlowInEasing.transform(settleProgress)
            return (settleStartOffset - layoutOffset) * remaining
        }
        return placementOffsetFor(itemId)
    }

    internal fun scaleFor(itemId: Long): Float = when {
        itemId == draggingItemId -> {
            val progress = FastOutSlowInEasing.transform(liftProgress)
            liftStartScale + (DRAG_SCALE - liftStartScale) * progress
        }
        itemId == settlingItemId -> {
            val progress = FastOutSlowInEasing.transform(settleProgress)
            settleStartScale + (1f - settleStartScale) * progress
        }
        else -> 1f
    }

    internal fun shadowFor(itemId: Long): Float = when {
        itemId == draggingItemId -> 1f
        itemId == settlingItemId -> 1f - FastOutSlowInEasing.transform(settleProgress)
        else -> 0f
    }

    internal fun isRaised(itemId: Long): Boolean = itemId == draggingItemId || itemId == settlingItemId

    /** Advance the short reorder motion independently from Android's animator duration scale. */
    internal fun advanceAnimations(frameTimeNanos: Long) {
        if (draggingItemId != null && liftProgress < 1f) {
            val start = liftStartNanos ?: frameTimeNanos.also { liftStartNanos = it }
            liftProgress = ((frameTimeNanos - start).toFloat() / REORDER_ANIMATION_DURATION_NS)
                .coerceIn(0f, 1f)
            if (liftProgress >= 1f) liftStartNanos = null
        }
        if (settlingItemId != null) {
            val start = settleStartNanos ?: frameTimeNanos.also { settleStartNanos = it }
            settleProgress = ((frameTimeNanos - start).toFloat() / REORDER_ANIMATION_DURATION_NS)
                .coerceIn(0f, 1f)
            if (settleProgress >= 1f) {
                settlingItemId = null
                settleStartNanos = null
            }
        }
        if (placementStartOffsets.isNotEmpty()) {
            val start = placementStartNanos ?: frameTimeNanos.also { placementStartNanos = it }
            placementProgress = ((frameTimeNanos - start).toFloat() / REORDER_ANIMATION_DURATION_NS)
                .coerceIn(0f, 1f)
            if (placementProgress >= 1f) {
                placementStartOffsets = emptyMap()
                placementStartNanos = null
            }
        }
        if (pendingPositions != null) {
            val start = pendingLayoutStartNanos ?: frameTimeNanos.also { pendingLayoutStartNanos = it }
            if (frameTimeNanos - start >= PENDING_LAYOUT_TIMEOUT_NS) clearPendingMove()
        }
    }

    /** Consumes the new keyed layout after a requested item move is measured. */
    internal fun onLayoutChanged(items: List<ReorderItemPosition>) {
        val current = items.associateBy(ReorderItemPosition::itemId)
        val before = pendingPositions
        val draggedId = pendingDraggedItemId
        val targetIndex = pendingTargetIndex
        if (before != null && draggedId != null && targetIndex != null && current[draggedId]?.index == targetIndex) {
            val offsets = current.mapNotNull { (id, after) ->
                if (id == draggedId) return@mapNotNull null
                val old = before[id] ?: return@mapNotNull null
                if (old.index == after.index) return@mapNotNull null
                val delta = old.offset - after.offset
                if (delta.getDistance() <= 0.5f) null else id to delta
            }.toMap()
            placementStartOffsets = offsets
            placementProgress = 0f
            placementStartNanos = null
            clearPendingMove()
        }
    }

    /** Scroll step for this frame, positive = towards the end of the list, `0` = no auto-scroll. */
    internal fun autoScrollAmount(): Float {
        // A finger that has not moved is still a long press: it must not scroll the grid.
        if (!exceededSlop) return 0f
        val id = draggingItemId ?: return 0f
        val info = itemInfo(id) ?: return 0f
        val layout = gridState.layoutInfo
        val top = startOffset.y + dragOffset.y
        val bottom = top + info.size.height
        val margin = info.size.height * EDGE_MARGIN_FRACTION
        val startEdge = layout.viewportStartOffset + margin
        val endEdge = layout.viewportEndOffset - margin
        return when {
            top < startEdge -> ((top - startEdge) * AUTO_SCROLL_FACTOR).coerceAtLeast(-MAX_AUTO_SCROLL_PX)
            bottom > endEdge -> ((bottom - endEdge) * AUTO_SCROLL_FACTOR).coerceAtMost(MAX_AUTO_SCROLL_PX)
            else -> 0f
        }
    }

    /** Reports a move as soon as the dragged tile's centre enters another tile's cell. */
    internal fun checkForMove() {
        if (!exceededSlop) return
        val id = draggingItemId ?: return
        val info = itemInfo(id) ?: return
        val centreX = startOffset.x + dragOffset.x + info.size.width / 2f
        val centreY = startOffset.y + dragOffset.y + info.size.height / 2f
        val target = gridState.layoutInfo.visibleItemsInfo.firstOrNull { other ->
            if (other.key !is Long) return@firstOrNull false
            val otherOffset = Offset(other.offset.x.toFloat(), other.offset.y.toFloat())
            other.key != id &&
                centreX >= otherOffset.x && centreX < otherOffset.x + other.size.width &&
                centreY >= otherOffset.y && centreY < otherOffset.y + other.size.height
        } ?: return
        if (pendingPositions == null) {
            pendingPositions = gridState.layoutInfo.visibleItemsInfo.mapNotNull { visible ->
                val visibleId = visible.key as? Long ?: return@mapNotNull null
                visibleId to ReorderItemPosition(
                    itemId = visibleId,
                    index = visible.index,
                    offset = Offset(visible.offset.x.toFloat(), visible.offset.y.toFloat()) + placementOffsetFor(visibleId),
                )
            }.toMap()
            pendingLayoutStartNanos = null
        }
        pendingDraggedItemId = id
        pendingTargetIndex = target.index
        reportedMove = true
        onMove(info.index, target.index)
    }

    private fun placementOffsetFor(itemId: Long): Offset {
        val pendingPosition = pendingPositions?.get(itemId)
        val start = placementStartOffsets[itemId]
        if (pendingPosition == null && start == null) return Offset.Zero
        val info = itemInfo(itemId)
        if (pendingPosition != null && info != null && pendingPosition.index != info.index) {
            return pendingPosition.offset - Offset(info.offset.x.toFloat(), info.offset.y.toFloat())
        }
        if (start == null) return Offset.Zero
        val progress = FastOutSlowInEasing.transform(placementProgress)
        return start * (1f - progress)
    }

    private fun clearPendingMove() {
        pendingPositions = null
        pendingDraggedItemId = null
        pendingTargetIndex = null
        pendingLayoutStartNanos = null
    }

    private fun itemInfo(itemId: Long): LazyGridItemInfo? =
        gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == itemId }
}

/**
 * Creates the [ReorderState] for [gridState] and keeps the grid scrolling while the dragged tile
 * sits near an edge.
 *
 * @param onMove one step of the drag, as indices into the currently displayed tile list.
 * @param onDragEnd the finger was lifted after a real drag: persist the order.
 * @param onLongPress the finger was lifted without moving: enter selection mode for that tile.
 */
@Composable
fun rememberReorderState(
    gridState: LazyGridState,
    onMove: (from: Int, to: Int) -> Unit,
    onDragEnd: () -> Unit,
    onLongPress: (itemId: Long) -> Unit,
): ReorderState {
    val haptics = LocalHapticFeedback.current
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val state = remember(gridState, haptics) {
        ReorderState(
            gridState = gridState,
            haptics = haptics,
            onMove = { from, to -> currentOnMove(from, to) },
            onDragEnd = { currentOnDragEnd() },
            onLongPress = { currentOnLongPress(it) },
        )
    }

    LaunchedEffect(state, gridState, state.hasPendingMove) {
        if (!state.hasPendingMove) return@LaunchedEffect
        snapshotFlow {
            gridState.layoutInfo.visibleItemsInfo.mapNotNull { info ->
                val id = info.key as? Long ?: return@mapNotNull null
                ReorderItemPosition(
                    itemId = id,
                    index = info.index,
                    offset = Offset(info.offset.x.toFloat(), info.offset.y.toFloat()),
                )
            }
        }.collect(state::onLayoutChanged)
    }

    LaunchedEffect(state, state.animationActive) {
        if (!state.animationActive) return@LaunchedEffect
        while (state.animationActive) {
            val frameTime = withFrameNanos { it }
            state.advanceAnimations(frameTime)
            if (state.draggingItemId != null) {
                val amount = state.autoScrollAmount()
                if (amount != 0f) {
                    gridState.scrollBy(amount)
                    // The list moved under the finger, so the hovered cell may have changed.
                    state.checkForMove()
                }
            }
        }
    }
    return state
}

/**
 * Makes one grid cell draggable. [itemId] must be the same value the grid uses as the item key,
 * because the state locates tiles by key in `LazyGridState.layoutInfo`.
 *
 * The tile under the finger is lifted: it gets a shadow and grows by [DRAG_SCALE], and shrinks
 * back with an animation when it is dropped.
 */
@Composable
fun Modifier.reorderable(state: ReorderState, itemId: Long, enabled: Boolean = true): Modifier {
    val dragging = state.draggingItemId == itemId
    return this
        .zIndex(if (state.isRaised(itemId)) 1f else 0f)
        .graphicsLayer {
            val translation = state.translationFor(itemId)
            translationX = translation.x
            translationY = translation.y
            scaleX = state.scaleFor(itemId)
            scaleY = state.scaleFor(itemId)
            shape = RoundedCornerShape(TGDimens.TILE_CORNER_DP.dp)
            shadowElevation = DRAG_ELEVATION_DP.dp.toPx() * state.shadowFor(itemId)
        }
        .pointerInput(itemId, enabled) {
            if (!enabled) return@pointerInput
            // Clearing the long-press flag on every finger-down happens ~500 ms before the long
            // press itself, so the tap handler can never see a flag left over from the previous
            // gesture.
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                state.onPointerDown()
            }
        }
        .pointerInput(itemId, enabled) {
            if (!enabled) return@pointerInput
            detectDragGesturesAfterLongPress(
                onDragStart = { state.onDragStart(itemId, viewConfiguration.touchSlop) },
                onDrag = { _, delta -> state.onDrag(delta) },
                onDragEnd = { state.onDragStop(itemId) },
                onDragCancel = { state.onDragStop(itemId) },
            )
        }
}
