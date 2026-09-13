package dev.happyc0der.forgelog.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.zIndex
import dev.happyc0der.forgelog.R
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Minimum size for anything draggable, so a handle is actually grabbable one-handed. */
val DragHandleSize = 48.dp

/**
 * A vertical list whose rows can be dragged into a new order.
 *
 * Three things this gets right that the previous implementation did not:
 *
 * 1. **Per-row heights.** It used one shared height for every row, taken from whichever row measured
 *    last. Rows here differ enormously — an exercise card with six previous sets against one with
 *    none — so a single height made the drag target the wrong index.
 * 2. **Persistence on drop.** [onMove] is for reordering a transient list only; [onDragEnd] fires
 *    once, when the finger lifts. Persisting inside `onMove` meant one drag across five positions
 *    issued five database transactions, each computed from a snapshot that the previous write had
 *    already invalidated.
 * 3. **Reachable without dragging.** Each row exposes move-up and move-down accessibility actions,
 *    so reordering works with a screen reader and can be tested without synthesising gestures.
 *
 * [scrollState], when given, auto-scrolls the parent as a drag approaches the viewport edge, which is
 * what makes moving a row past the fold possible in one gesture.
 */
@Composable
fun <T> ReorderableColumn(
    items: List<T>,
    key: (T) -> Any,
    onMove: (from: Int, to: Int) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState? = null,
    moveUpLabel: String = stringResource(R.string.action_move_up),
    moveDownLabel: String = stringResource(R.string.action_move_down),
    itemContent: @Composable ColumnScope.(item: T, dragHandleModifier: Modifier) -> Unit,
) {
    // Height per row key, so the drag maths uses each row's real size.
    val heights = remember { mutableStateMapOf<Any, Int>() }
    var draggingKey by remember { mutableStateOf<Any?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()

    /*
     * The gesture detector below is set up once per row and deliberately not restarted while a drag
     * is under way -- restarting it would cancel the drag. So it must not close over the list it saw
     * at the time: every move, including its own, leaves that copy stale, and every index worked out
     * from it is then wrong. It read one that never changed, so a second drag on the same screen did
     * nothing at all, or moved a row the user had not grabbed, until the screen was left and opened
     * again.
     */
    val currentItems by rememberUpdatedState(items)
    val currentKey by rememberUpdatedState(key)
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)

    /*
     * The order the drag itself has reached, which is not always the order the caller has got round
     * to showing.
     *
     * Holding the list through [rememberUpdatedState] was not enough on its own, because that is
     * only refreshed when composition runs. Pointer events are not: several arrive within one frame
     * whenever a finger moves quickly, and every one after the first in that frame read the order
     * from before the frame's moves. Each then reported a move from an index that had already
     * changed hands, so a quick drag did not merely land in the wrong place -- it moved rows the
     * finger had never touched, and [onDragEnd] wrote that down.
     *
     * Keys rather than items, because heights are kept per key and the index is all a move needs.
     * It is seeded when the finger goes down, where composition has settled, and every move the
     * gesture reports is applied to it first -- so it always matches what the caller was last told,
     * which is what the next index has to be relative to. The callers all apply a move the same
     * way, and only ever decline one whose indices are out of bounds, which these never are.
     */
    val dragOrder = remember { mutableListOf<Any>() }

    Column(modifier = modifier.fillMaxWidth()) {
        items.forEachIndexed { index, item ->
            val itemKey = key(item)
            val isDragging = draggingKey == itemKey
            key(itemKey) {
                Column(
                    modifier = Modifier
                        .zIndex(if (isDragging) 1f else 0f)
                        .offset {
                            IntOffset(x = 0, y = if (isDragging) dragOffset.roundToInt() else 0)
                        }
                        .onSizeChanged { size ->
                            if (size.height > 0) heights[itemKey] = size.height
                        }
                        .semantics {
                            customActions = buildList {
                                if (index > 0) {
                                    add(
                                        CustomAccessibilityAction(moveUpLabel) {
                                            onMove(index, index - 1)
                                            onDragEnd()
                                            true
                                        },
                                    )
                                }
                                if (index < items.lastIndex) {
                                    add(
                                        CustomAccessibilityAction(moveDownLabel) {
                                            onMove(index, index + 1)
                                            onDragEnd()
                                            true
                                        },
                                    )
                                }
                            }
                        },
                ) {
                    itemContent(
                        item,
                        Modifier.pointerInput(itemKey, items.size) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingKey = itemKey
                                    dragOffset = 0f
                                    dragOrder.clear()
                                    currentItems.forEach { dragOrder += currentKey(it) }
                                },
                                onDragCancel = {
                                    draggingKey = null
                                    dragOffset = 0f
                                    dragOrder.clear()
                                    currentOnDragEnd()
                                },
                                onDragEnd = {
                                    draggingKey = null
                                    dragOffset = 0f
                                    dragOrder.clear()
                                    // The single persistence point for a whole drag.
                                    currentOnDragEnd()
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffset += dragAmount.y
                                    val currentIndex = dragOrder.indexOf(draggingKey)
                                    if (currentIndex < 0) return@detectDragGesturesAfterLongPress

                                    scrollState?.let { state ->
                                        scope.launch { state.autoScroll(dragAmount.y) }
                                    }

                                    val target = targetIndex(
                                        currentIndex = currentIndex,
                                        offset = dragOffset,
                                        order = dragOrder,
                                        heights = heights,
                                    )
                                    if (target != currentIndex) {
                                        // Offset shrinks by the distance actually travelled, so the row
                                        // stays under the finger whatever the neighbours' heights are.
                                        // Measured before the move, which is the order it describes.
                                        dragOffset -= travelled(
                                            from = currentIndex,
                                            to = target,
                                            order = dragOrder,
                                            heights = heights,
                                        )
                                        dragOrder.add(target, dragOrder.removeAt(currentIndex))
                                        currentOnMove(currentIndex, target)
                                    }
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}

/**
 * The index the dragged row should occupy, found by walking neighbours and accumulating their real
 * heights until the drag offset is used up.
 */
private fun targetIndex(
    currentIndex: Int,
    offset: Float,
    order: List<Any>,
    heights: Map<Any, Int>,
): Int {
    if (offset == 0f) return currentIndex
    var target = currentIndex
    var remaining = kotlin.math.abs(offset)
    val step = if (offset > 0) 1 else -1

    while (true) {
        val neighbour = target + step
        if (neighbour !in order.indices) break
        val neighbourHeight = heights[order[neighbour]] ?: break
        // Swap once the row has travelled past half of the neighbour it is passing.
        if (remaining < neighbourHeight / 2f) break
        remaining -= neighbourHeight
        target = neighbour
    }
    return target
}

/** Total pixel distance between two positions, using each intervening row's own height. */
private fun travelled(
    from: Int,
    to: Int,
    order: List<Any>,
    heights: Map<Any, Int>,
): Float {
    val range = if (to > from) (from + 1)..to else to until from
    val distance = range.sumOf { index ->
        heights[order[index]] ?: 0
    }
    return if (to > from) distance.toFloat() else -distance.toFloat()
}

/** Nudges the parent scroll when a drag nears the top or bottom of the viewport. */
private suspend fun ScrollState.autoScroll(dragAmount: Float) {
    val threshold = 12f
    if (kotlin.math.abs(dragAmount) < threshold) return
    scrollBy(dragAmount.coerceIn(-AUTO_SCROLL_STEP, AUTO_SCROLL_STEP))
}

private const val AUTO_SCROLL_STEP = 24f

/**
 * A drag handle big enough to hit. The visual icon stays small; the touch target does not — a bare
 * 24dp icon, which is what the screens used before, is under the 48dp the design system requires and
 * genuinely hard to grab one-handed mid-workout.
 */
@Composable
fun DragHandle(
    dragModifier: Modifier,
    testTag: String? = null,
    icon: @Composable () -> Unit,
) {
    Box(
        modifier = dragModifier
            .size(DragHandleSize)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        icon()
    }
}
