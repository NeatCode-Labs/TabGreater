package com.neatcode.tabgreater.ui.manager

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex

/**
 * Drag-to-reorder for a [androidx.compose.foundation.lazy.LazyColumn] where the drag starts on a
 * handle rather than on the whole row (the rows themselves stay clickable and swipeable).
 *
 * The dragged row is translated with `graphicsLayer` while the rows it passes swap places through
 * [onMove], so the caller only keeps a mutable list of keys; [onDrop] then persists the result.
 */
@Stable
class ReorderState internal constructor(
    private val listState: LazyListState,
    private val onMove: (from: Int, to: Int) -> Unit,
    private val onDrop: () -> Unit,
) {
    /** Key of the row under the finger, `null` when nothing is being dragged. */
    var draggedKey: Any? by mutableStateOf(null)
        private set

    private var draggedIndex by mutableIntStateOf(NO_INDEX)
    private var offset by mutableFloatStateOf(0f)

    val isDragging: Boolean get() = draggedKey != null

    /** Vertical displacement in pixels for the row identified by [key]. */
    fun translationFor(key: Any): Float = if (key == draggedKey) offset else 0f

    fun start(key: Any) {
        val info = itemInfo(key) ?: return
        draggedKey = key
        draggedIndex = info.index
        offset = 0f
    }

    /**
     * Moves the dragged row by [deltaY] and swaps it with the neighbour whose bounds now contain
     * its centre. The translation is corrected by the swap distance so the row stays under the
     * finger once the list has re-laid out.
     */
    fun drag(deltaY: Float) {
        val key = draggedKey ?: return
        offset += deltaY
        val current = itemInfo(key) ?: return
        val centre = current.offset + current.size / 2f + offset
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { other ->
            other.key != key && centre >= other.offset && centre <= other.offset + other.size
        } ?: return
        onMove(current.index, target.index)
        draggedIndex = target.index
        offset -= (target.offset - current.offset)
    }

    fun end() {
        val dropped = draggedIndex != NO_INDEX
        reset()
        if (dropped) onDrop()
    }

    fun cancel() = reset()

    /**
     * Pixels the list should scroll so a row dragged past a viewport edge keeps moving,
     * capped so a fast drag cannot fling the list.
     */
    fun autoScrollDelta(): Float {
        val key = draggedKey ?: return 0f
        val current = itemInfo(key) ?: return 0f
        val layout = listState.layoutInfo
        val top = current.offset + offset
        val bottom = top + current.size
        return when {
            top < layout.viewportStartOffset ->
                (top - layout.viewportStartOffset).coerceAtLeast(-MAX_AUTO_SCROLL_PX)

            bottom > layout.viewportEndOffset ->
                (bottom - layout.viewportEndOffset).coerceAtMost(MAX_AUTO_SCROLL_PX)

            else -> 0f
        }
    }

    /** Keeps the dragged row under the finger after the list auto-scrolled by [consumed] pixels. */
    fun onAutoScrolled(consumed: Float) {
        offset += consumed
    }

    private fun reset() {
        draggedKey = null
        draggedIndex = NO_INDEX
        offset = 0f
    }

    private fun itemInfo(key: Any): LazyListItemInfo? =
        listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }

    private companion object {
        const val NO_INDEX = -1
        const val MAX_AUTO_SCROLL_PX = 24f
    }
}

/**
 * @param onMove swap two rows in the caller's own list while the drag is running.
 * @param onDrop persist the order the drag ended on.
 */
@Composable
fun rememberReorderState(
    listState: LazyListState,
    onMove: (from: Int, to: Int) -> Unit,
    onDrop: () -> Unit,
): ReorderState {
    val move by rememberUpdatedState(onMove)
    val drop by rememberUpdatedState(onDrop)
    return remember(listState) {
        ReorderState(listState, { from, to -> move(from, to) }, { drop() })
    }
}

/** Put this on the drag handle only — dragging anywhere else must stay a scroll. */
fun Modifier.reorderHandle(state: ReorderState, key: Any): Modifier = pointerInput(state, key) {
    detectDragGestures(
        onDragStart = { state.start(key) },
        onDragEnd = { state.end() },
        onDragCancel = { state.cancel() },
        onDrag = { change, amount ->
            change.consume()
            state.drag(amount.y)
        },
    )
}

/** Put this on the row itself: it lifts the dragged row above its neighbours and moves it. */
fun Modifier.reorderableItem(state: ReorderState, key: Any): Modifier = this
    .zIndex(if (state.draggedKey == key) 1f else 0f)
    .graphicsLayer { translationY = state.translationFor(key) }
