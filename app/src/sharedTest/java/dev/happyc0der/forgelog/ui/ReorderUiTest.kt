package dev.happyc0der.forgelog.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.happyc0der.forgelog.ui.components.DragHandle
import dev.happyc0der.forgelog.ui.components.ReorderableColumn
import dev.happyc0der.forgelog.ui.theme.ForgeLogTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Dragging rows into a new order, more than once without leaving the screen.
 *
 * The second drag used to do nothing, or move the wrong row: the gesture detector is set up once per
 * row and is deliberately not restarted mid-drag, so it went on reading the list as it was the first
 * time it ran. After any reorder that list was stale, and every index it worked out from it was
 * wrong until the screen was left and opened again.
 */
@RunWith(AndroidJUnit4::class)
class ReorderUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var rowHeightPx = 0f

    private fun setContent(onOrderChanged: () -> Unit = {}, order: () -> List<String>, move: (Int, Int) -> Unit) {
        composeRule.setContent {
            ForgeLogTheme {
                ReorderableColumn(
                    items = order(),
                    key = { it },
                    onMove = { from, to -> move(from, to) },
                    onDragEnd = onOrderChanged,
                ) { item, dragModifier ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ROW_HEIGHT)
                            .onSizeChanged { rowHeightPx = it.height.toFloat() },
                    ) {
                        DragHandle(dragModifier = dragModifier, testTag = "handle-$item") {
                            Text(text = item, modifier = Modifier.testTag("label-$item"))
                        }
                    }
                }
            }
        }
    }

    /** A long press, then a drag of [dy] pixels, then a lift: what a reorder gesture is. */
    private fun drag(handle: String, dy: Float) {
        composeRule.onNodeWithTag(handle).performTouchInput {
            down(center)
            advanceEventTime(LONG_PRESS_MS)
            moveBy(Offset(0f, dy / 2f))
            advanceEventTime(16)
            moveBy(Offset(0f, dy / 2f))
            advanceEventTime(16)
            up()
        }
        composeRule.waitForIdle()
    }

    @Test
    fun twoDragsInARowEachMoveTheRowThatWasGrabbed() {
        var order by mutableStateOf(listOf("A", "B", "C"))
        var ends = 0
        setContent(
            onOrderChanged = { ends++ },
            order = { order },
            move = { from, to -> order = order.toMutableList().apply { add(to, removeAt(from)) } },
        )
        composeRule.waitForIdle()
        val step = rowHeightPx * 0.75f

        drag("handle-A", step)
        assertEquals(listOf("B", "A", "C"), order)

        // The same row, dragged back. This is the one that used to do nothing.
        drag("handle-A", -step)
        assertEquals(listOf("A", "B", "C"), order)
        assertEquals(2, ends)
    }

    private companion object {
        val ROW_HEIGHT = 64.dp

        /** Comfortably past the platform's long-press timeout. */
        const val LONG_PRESS_MS = 1_000L
    }
}
