package com.example.forgelog.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

@Composable
fun <T> ReorderableColumn(
    items: List<T>,
    key: (T) -> Any,
    onMove: (from: Int, to: Int) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
    itemContent: @Composable ColumnScope.(item: T, dragHandleModifier: Modifier) -> Unit,
) {
    var itemHeightPx by remember { mutableIntStateOf(1) }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    Column(modifier = modifier.fillMaxWidth()) {
        items.forEachIndexed { index, item ->
            val isDragging = draggingIndex == index
            key(key(item)) {
            Column(
                modifier = Modifier
                    .zIndex(if (isDragging) 1f else 0f)
                    .offset {
                        IntOffset(
                            x = 0,
                            y = if (isDragging) dragOffset.roundToInt() else 0,
                        )
                    }
                    .onSizeChanged { size ->
                        if (size.height > 0) itemHeightPx = size.height
                    },
            ) {
                itemContent(
                    item,
                    Modifier.pointerInput(items.size, index, itemHeightPx) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingIndex = index
                                dragOffset = 0f
                            },
                            onDragCancel = {
                                draggingIndex = null
                                dragOffset = 0f
                                onDragEnd()
                            },
                            onDragEnd = {
                                draggingIndex = null
                                dragOffset = 0f
                                onDragEnd()
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val from = draggingIndex ?: return@detectDragGesturesAfterLongPress
                                dragOffset += dragAmount.y
                                val shift = (dragOffset / itemHeightPx.coerceAtLeast(1)).toInt()
                                if (shift != 0) {
                                    val to = (from + shift).coerceIn(0, items.lastIndex)
                                    if (to != from) {
                                        onMove(from, to)
                                        draggingIndex = to
                                        dragOffset -= shift * itemHeightPx
                                    }
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
