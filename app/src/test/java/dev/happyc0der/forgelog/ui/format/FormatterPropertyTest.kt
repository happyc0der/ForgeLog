package dev.happyc0der.forgelog.ui.format

import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.workout.DurationInput
import dev.happyc0der.forgelog.domain.workout.DurationInputUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Nothing a number can be makes a screen fall over.
 *
 * These run on every screen in the app, on values that reach them from the database rather than
 * from a field, so the guard against a bad one cannot be at the point of entry. BigDecimal in
 * particular throws on a non-finite value rather than returning something, and a crash in a
 * formatter takes down whichever screen was drawing.
 */
class FormatterPropertyTest {

    private val units = listOf(
        ExerciseUnit.LB,
        ExerciseUnit.KG,
        ExerciseUnit.BODYWEIGHT,
        ExerciseUnit.SECONDS,
        ExerciseUnit.METERS,
    )

    private fun awkwardDoubles(): List<Double> {
        val random = Random(7777)
        val generated = List(2_000) {
            when (random.nextInt(5)) {
                0 -> random.nextDouble(0.0, 1_000.0)
                1 -> random.nextDouble(0.0, 1_000_000.0)
                2 -> random.nextDouble(-1_000.0, 1_000.0)
                3 -> random.nextDouble() * Double.MAX_VALUE
                else -> random.nextDouble(0.0, 1.0)
            }
        }
        return generated + listOf(
            0.0, -0.0, 1.0, -1.0, 0.005, 0.0049, 99.995, 999_999.99,
            1e-300, 1e300, Double.MAX_VALUE, Double.MIN_VALUE,
            -Double.MAX_VALUE, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
        )
    }

    @Test
    fun noValueMakesAFormatterThrow() {
        for (value in awkwardDoubles()) {
            for (unit in units) {
                listOf<Pair<String, () -> String>>(
                    "volume" to { Formatters.volume(value, unit) },
                    "load" to { Formatters.load(value, unit) },
                    "weight" to { Formatters.weight(value, unit) },
                    "plainNumber" to { Formatters.plainNumber(value) },
                    "distanceMeters" to { Formatters.distanceMeters(value) },
                ).forEach { (name, format) ->
                    val rendered = try {
                        format()
                    } catch (throwable: Throwable) {
                        throw AssertionError(
                            "$name($value, $unit) threw ${throwable::class.simpleName}",
                            throwable,
                        )
                    }
                    assertTrue("$name($value, $unit) rendered nothing", rendered.isNotEmpty())
                }
            }
        }
    }

    @Test
    fun noSecondCountMakesADurationThrow() {
        val random = Random(8888)
        val values = List(2_000) { random.nextInt() } +
            listOf(0, 1, -1, 59, 60, 61, 3_599, 3_600, Int.MAX_VALUE, Int.MIN_VALUE)
        for (value in values) {
            listOf<Pair<String, () -> String?>>(
                "seconds" to { Formatters.seconds(value) },
                "timedSeconds" to { Formatters.timedSeconds(value) },
                "compactDuration" to { Formatters.compactDuration(value.toLong() * 1000L) },
            ).forEach { (name, format) ->
                try {
                    format()
                } catch (throwable: Throwable) {
                    throw AssertionError("$name($value) threw ${throwable::class.simpleName}", throwable)
                }
            }
            DurationInputUnit.entries.forEach { unit ->
                val shown = DurationInput.toDisplay(value, unit)
                try {
                    DurationInput.parseSeconds(shown, unit)
                } catch (throwable: Throwable) {
                    throw AssertionError(
                        "parsing back \"$shown\" ($unit) threw ${throwable::class.simpleName}",
                        throwable,
                    )
                }
            }
        }
    }

    /** Whatever a duration field shows, reading it back gives the seconds it was shown for. */
    @Test
    fun aDurationReadsBackAsTheSecondsItShows() {
        val random = Random(9999)
        val values = List(3_000) { random.nextInt(0, 500_000) } + listOf(0, 1, 59, 60, 61, 90, 3_599, 3_600)
        for (seconds in values) {
            assertEquals(
                "a duration in seconds did not read back",
                seconds,
                DurationInput.parseSeconds(
                    DurationInput.toDisplay(seconds, DurationInputUnit.SECONDS),
                    DurationInputUnit.SECONDS,
                ),
            )
            // Minutes are shown to two decimals, so a second inside that rounding is the most it
            // can lose -- 0.01 of a minute is 0.6 s, which rounds to within a second either way.
            val viaMinutes = DurationInput.parseSeconds(
                DurationInput.toDisplay(seconds, DurationInputUnit.MINUTES),
                DurationInputUnit.MINUTES,
            )
            assertTrue("a duration in minutes did not read back at all", viaMinutes != null)
            assertTrue(
                "$seconds s shown in minutes read back as $viaMinutes s",
                kotlin.math.abs(viaMinutes!! - seconds) <= 1,
            )
        }
    }

    /** Text pasted into a duration field is answered, never thrown from. */
    @Test
    fun anythingPastedIntoADurationFieldIsAnswered() {
        val random = Random(1357)
        val alphabet = "0123456789.,-+eE NaNInfinity".toCharArray()
        val texts = List(4_000) {
            String(CharArray(random.nextInt(0, 9)) { alphabet[random.nextInt(alphabet.size)] })
        } + listOf("NaN", "Infinity", "-Infinity", "1e400", "1e-400", "", " ", ".", "-", "1/2")
        for (text in texts) {
            DurationInputUnit.entries.forEach { unit ->
                try {
                    DurationInput.parseSeconds(text, unit)
                    DurationInput.isParseable(text, unit)
                } catch (throwable: Throwable) {
                    throw AssertionError(
                        "\"$text\" ($unit) threw ${throwable::class.simpleName}",
                        throwable,
                    )
                }
            }
        }
    }
}
