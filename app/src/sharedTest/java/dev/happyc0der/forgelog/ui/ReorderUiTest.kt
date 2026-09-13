package dev.happyc0der.forgelog.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.happyc0der.forgelog.ui.components.DragHandle
import dev.happyc0der.forgelog.ui.components.ReorderableColumn
import dev.happyc0der.forgelog.ui.theme.ForgeLogTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    /** Measured heights per row, for the uneven case where one number will not do. */
    private val heightsPx = mutableMapOf<String, Float>()

    private fun setContent(
        onOrderChanged: () -> Unit = {},
        order: () -> List<String>,
        move: (Int, Int) -> Unit,
        heightOf: (String) -> Dp = { ROW_HEIGHT },
    ) {
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
                            .height(heightOf(item))
                            .onSizeChanged {
                                rowHeightPx = it.height.toFloat()
                                heightsPx[item] = it.height.toFloat()
                            },
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


    /*
     * Rows of different heights, which is the whole reason this component was rewritten: the one it
     * replaced used a single shared height taken from whichever row measured last, so the drag maths
     * targeted the wrong index. An exercise card with six previous sets beside one with none is the
     * real case. Every test above uses rows of one height, which a shared-height bug would pass.
     *
     * The documented rule is "swap once the row has travelled past half of the neighbour it is
     * passing", so each expectation below is stated in terms of the *neighbour's* height, never the
     * dragged row's.
     */

    @Test
    fun aShortRowCrossesATallOneAtHalfTheTallOnesHeight() {
        var order by mutableStateOf(listOf("short", "tall", "last"))
        setContent(
            order = { order },
            move = { from, to -> order = order.toMutableList().apply { add(to, removeAt(from)) } },
            heightOf = { item -> if (item == "tall") TALL_ROW else ROW_HEIGHT },
        )
        composeRule.waitForIdle()
        val tall = heightsPx.getValue("tall")

        // Not yet: a short row dragged by its own height has not passed half of a much taller one.
        drag("handle-short", heightsPx.getValue("short") * 1.5f)
        assertEquals(listOf("short", "tall", "last"), order)

        drag("handle-short", tall * 0.6f)
        assertEquals(listOf("tall", "short", "last"), order)
    }

    @Test
    fun aTallRowCrossesAShortOneAtHalfTheShortOnesHeight() {
        var order by mutableStateOf(listOf("short", "tall", "last"))
        setContent(
            order = { order },
            move = { from, to -> order = order.toMutableList().apply { add(to, removeAt(from)) } },
            heightOf = { item -> if (item == "tall") TALL_ROW else ROW_HEIGHT },
        )
        composeRule.waitForIdle()

        // Upwards, past a row far shorter than itself: the short row's height is what counts, so a
        // small drag is enough. Measuring against the dragged row would need four times as much.
        drag("handle-tall", -heightsPx.getValue("short") * 0.75f)
        assertEquals(listOf("tall", "short", "last"), order)
    }

    @Test
    fun oneGestureCrossesTwoRowsOfDifferentHeights() {
        var order by mutableStateOf(listOf("short", "tall", "last"))
        var ends = 0
        setContent(
            onOrderChanged = { ends++ },
            order = { order },
            move = { from, to -> order = order.toMutableList().apply { add(to, removeAt(from)) } },
            heightOf = { item -> if (item == "tall") TALL_ROW else ROW_HEIGHT },
        )
        composeRule.waitForIdle()
        val past = heightsPx.getValue("tall") + heightsPx.getValue("last") * 0.75f

        drag("handle-short", past)

        assertEquals(listOf("tall", "last", "short"), order)
        // Two positions crossed, but one drag: the database is written once, not per swap.
        assertEquals(1, ends)
    }

    @Test
    fun aDragPastTheEndStopsAtTheEnd() {
        var order by mutableStateOf(listOf("short", "tall", "last"))
        setContent(
            order = { order },
            move = { from, to -> order = order.toMutableList().apply { add(to, removeAt(from)) } },
            heightOf = { item -> if (item == "tall") TALL_ROW else ROW_HEIGHT },
        )
        composeRule.waitForIdle()
        val wayPastTheBottom = heightsPx.values.sum() * 4f

        drag("handle-short", wayPastTheBottom)

        assertEquals(listOf("tall", "last", "short"), order)
    }

    /**
     * The row has to stay where the finger put it, and that is a separate question from where it
     * lands in the list: the offset is reduced by the distance actually travelled, which is the sum
     * of the heights of the rows passed over. Get that sum from the order *after* the move and it is
     * the dragged row's own height that gets counted instead of the row it passed, so the card jumps
     * away from the finger by the difference — invisible to any assertion about the final order, and
     * glaring to anyone holding the phone.
     */
    @Test
    fun theDraggedRowStaysUnderTheFingerWhenItPassesATallerOne() {
        var order by mutableStateOf(listOf("short", "tall", "last"))
        setContent(
            order = { order },
            move = { from, to -> order = order.toMutableList().apply { add(to, removeAt(from)) } },
            heightOf = { item -> if (item == "tall") TALL_ROW else ROW_HEIGHT },
        )
        composeRule.waitForIdle()
        val startedAt = topOf("handle-short")
        val dy = heightsPx.getValue("tall") * 0.6f

        // Held down, not lifted: the question is where the card is mid-gesture.
        composeRule.onNodeWithTag("handle-short").performTouchInput {
            down(center)
            advanceEventTime(LONG_PRESS_MS)
            moveBy(Offset(0f, dy))
            advanceEventTime(16)
        }
        composeRule.waitForIdle()

        assertEquals(listOf("tall", "short", "last"), order)
        assertEquals(
            "the card left the finger behind",
            dy.toDouble(),
            (topOf("handle-short") - startedAt).toDouble(),
            2.0,
        )

        composeRule.onNodeWithTag("handle-short").performTouchInput { up() }
        composeRule.waitForIdle()
    }

    /**
     * The same question on the one screen that scrolls.
     *
     * A drag near the fold nudges the parent scroll, which is what makes moving a row past it
     * possible in one gesture. But the scroll moves the content the drag offset is measured in, and
     * nothing put that back: the card slid out from under the finger by however far the list had
     * scrolled, and — worse, because it is saved — the offset then understated how far the finger had
     * actually travelled through the list, so the row came to rest above where it was dropped.
     */
    @Test
    fun aDragThatScrollsTheParentKeepsTheRowUnderTheFinger() {
        val rows = listOf("a", "b", "c", "d", "e", "f", "g", "h")
        var order by mutableStateOf(rows)
        lateinit var scroll: ScrollState
        composeRule.setContent {
            ForgeLogTheme {
                scroll = rememberScrollState()
                Box(modifier = Modifier.height(VIEWPORT).verticalScroll(scroll)) {
                    ReorderableColumn(
                        items = order,
                        key = { it },
                        onMove = { from, to ->
                            order = order.toMutableList().apply { add(to, removeAt(from)) }
                        },
                        onDragEnd = {},
                        scrollState = scroll,
                    ) { item, dragModifier ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(ROW_HEIGHT)
                                .onSizeChanged { heightsPx[item] = it.height.toFloat() },
                        ) {
                            DragHandle(dragModifier = dragModifier, testTag = "handle-$item") {
                                Text(text = item, modifier = Modifier.testTag("label-$item"))
                            }
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        val rowHeight = heightsPx.getValue("a")
        val startedAt = topOf("handle-a")

        // Fast enough to nudge the scroll: several of these in a row, each past the threshold.
        composeRule.onNodeWithTag("handle-a").performTouchInput {
            down(center)
            advanceEventTime(LONG_PRESS_MS)
            repeat(4) {
                moveBy(Offset(0f, rowHeight * 0.75f))
                advanceEventTime(16)
            }
        }
        composeRule.waitForIdle()

        val travelled = rowHeight * 3f
        assertTrue("the parent never scrolled, so this proves nothing", scroll.value > 0)
        assertEquals(
            "the card slid out from under the finger by what the list had scrolled",
            travelled.toDouble(),
            (topOf("handle-a") - startedAt).toDouble(),
            rowHeight / 2.0,
        )

        composeRule.onNodeWithTag("handle-a").performTouchInput { up() }
        composeRule.waitForIdle()
    }

    private fun topOf(tag: String): Float =
        composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top

    private companion object {
        val ROW_HEIGHT = 64.dp

        /** Short enough that eight rows do not fit, so the parent has somewhere to scroll. */
        val VIEWPORT = 200.dp

        /** Four times the others, the way a card with six logged sets stands beside an empty one. */
        val TALL_ROW = 256.dp

        /** Comfortably past the platform's long-press timeout. */
        const val LONG_PRESS_MS = 1_000L
    }
}
