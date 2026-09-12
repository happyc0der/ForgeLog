package dev.happyc0der.forgelog.ui.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.happyc0der.forgelog.ui.components.BarChart
import dev.happyc0der.forgelog.ui.components.BarDatum
import dev.happyc0der.forgelog.ui.components.FeelingRow
import dev.happyc0der.forgelog.ui.input.DurationSecondsField
import dev.happyc0der.forgelog.domain.workout.DurationInputUnit
import dev.happyc0der.forgelog.ui.theme.ForgeLogTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Layout at the largest font size a phone offers.
 *
 * Nothing was ever checked above the default text size, and three things broke at it -- the
 * navigation labels, this chart's axis and the trend-window chips -- each found by hand on a phone.
 * These are the same checks without one.
 *
 * [GraphicsMode.Mode.NATIVE] is what makes them mean anything: Robolectric measures text as a
 * fixed-width stub otherwise, so a label that wrapped on a real device would measure as fitting
 * here. With it, the same "Programs" that is 62dp wide at normal size measures 114dp at 2x.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(fontScale = 2.0f)
class LargeFontLayoutTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val weekdays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    private fun showChart(labels: List<String>) {
        composeRule.setContent {
            ForgeLogTheme {
                // A narrow phone in portrait, which is the tightest the chart has to survive.
                Box(modifier = Modifier.width(PHONE_WIDTH)) {
                    BarChart(bars = labels.map { BarDatum(label = it, value = 100.0) })
                }
            }
        }
        composeRule.waitForIdle()
    }

    /**
     * A week of day names across a phone gives each label about 45dp, and "Mon" at twice the normal
     * size does not fit that. It used to wrap, so the axis read "Mo n" with the total pushed below
     * the card; now it is cut short instead and the row keeps its height.
     */
    @Test
    fun theChartAxisStaysOnOneLine() {
        showChart(weekdays)

        weekdays.forEach { day ->
            val bounds = composeRule.onNodeWithText(day, substring = true).getBoundsInRoot()
            val height = bounds.bottom - bounds.top
            assert(height < TWO_LINES) {
                "$day wrapped: ${height} is more than one line at this font size"
            }
        }
    }

    /** Dated labels are longer still, and are what a range of more than a week uses. */
    @Test
    fun datedAxisLabelsStayOnOneLine() {
        val dates = listOf("31 Aug", "7 Sept", "14 Sept", "21 Sept", "28 Sept")
        showChart(dates)

        dates.forEach { label ->
            val bounds = composeRule.onNodeWithText(label, substring = true).getBoundsInRoot()
            val height = bounds.bottom - bounds.top
            assert(height < TWO_LINES) { "$label wrapped: $height" }
        }
    }

    /**
     * The feeling picker is five chips and a label in a plain row. Nothing about that wraps or
     * scrolls, so at a large font on a narrow phone the row can run past the edge -- and a chip
     * past the edge cannot be tapped.
     */
    @Test
    fun everyFeelingChipIsReachable() {
        composeRule.setContent {
            ForgeLogTheme {
                Box(modifier = Modifier.width(PHONE_WIDTH)) {
                    FeelingRow(feeling = null, onChange = {})
                }
            }
        }
        composeRule.waitForIdle()

        (1..5).forEach { value ->
            val bounds = composeRule.onNodeWithText(value.toString()).getBoundsInRoot()
            assert(bounds.right <= PHONE_WIDTH) {
                "chip $value runs to ${bounds.right}, past the ${PHONE_WIDTH} edge, so it cannot be tapped"
            }
        }
    }

    /**
     * The duration field shares its row with the sec/min toggle. The field takes what is left after
     * the two chips, and at a large font the chips take more -- so what is left has to stay wide
     * enough to type a rest into.
     */
    @Test
    fun theDurationFieldKeepsRoomToTypeIn() {
        composeRule.setContent {
            ForgeLogTheme {
                Box(modifier = Modifier.width(PHONE_WIDTH)) {
                    DurationSecondsField(
                        secondsText = "150",
                        onSecondsTextChange = {},
                        label = "Rest",
                        unit = DurationInputUnit.MINUTES,
                        onUnitChange = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val field = composeRule.onNodeWithText("Rest").getBoundsInRoot()
        val width = field.right - field.left
        assert(width >= USABLE_FIELD) {
            "the duration field is only $width wide at this font size, too narrow to type a rest into"
        }
    }

    private companion object {
        val PHONE_WIDTH = 320.dp

        /**
         * Comfortably above one line and below two at 2x: the probe measured a single line at 30dp,
         * so anything at or past this has wrapped.
         */
        val TWO_LINES = 50.dp

        /** Narrower than this and a rest of "1m 30s" has nowhere to show. */
        val USABLE_FIELD = 100.dp
    }
}
