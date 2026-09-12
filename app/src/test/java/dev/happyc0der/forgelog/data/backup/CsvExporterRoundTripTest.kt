package dev.happyc0der.forgelog.data.backup

import dev.happyc0der.forgelog.domain.model.SessionExerciseWithSets
import dev.happyc0der.forgelog.domain.workout.sessionDetail
import dev.happyc0der.forgelog.domain.workout.sessionExercise
import dev.happyc0der.forgelog.domain.workout.setLog
import dev.happyc0der.forgelog.domain.workout.workoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import kotlin.random.Random

/**
 * Whatever a user types into a note or a lift's name, the CSV parses back to it.
 *
 * The export exists to be opened somewhere else, so the test of it is a reader, not a substring
 * match: a quote, a comma or a newline in a note has to come back as that note and not split the
 * row or swallow the rest of the file. The individual cases are covered next door; this throws
 * everything at it at once, because the awkward text is whatever a real user happened to type.
 *
 * One thing is deliberately not identity. A value starting with = + - @ is prefixed with an
 * apostrophe, so a note reading "=SUM(A1)" opens as text rather than running as a formula.
 */
class CsvExporterRoundTripTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val formulaLeads = setOf('=', '+', '-', '@')

    private fun csvOf(exerciseName: String, note: String, sessionName: String): String =
        CsvExporter.export(
            listOf(
                sessionDetail(
                    workoutSession(
                        id = 1L,
                        startedAt = 1_700_000_000_000L,
                        completedAt = 1_700_003_600_000L,
                    ).copy(sessionName = sessionName),
                    SessionExerciseWithSets(
                        exercise = sessionExercise(
                            id = 1L,
                            sessionId = 1L,
                            exerciseId = 1L,
                            displayName = exerciseName,
                        ),
                        sets = listOf(setLog(notes = note)),
                    ),
                ),
            ),
            zone,
        )

    /** A reader of the format the file claims to be, rather than a split on commas. */
    private fun parse(csv: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var index = 0
        while (index < csv.length) {
            val char = csv[index]
            when {
                quoted && char == '"' && csv.getOrNull(index + 1) == '"' -> {
                    field.append('"'); index++
                }
                char == '"' -> quoted = !quoted
                !quoted && char == ',' -> { row.add(field.toString()); field.clear() }
                !quoted && char == '\r' && csv.getOrNull(index + 1) == '\n' -> {
                    row.add(field.toString()); field.clear()
                    rows.add(row); row = mutableListOf()
                    index++
                }
                else -> field.append(char)
            }
            index++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString()); rows.add(row)
        }
        return rows
    }

    /** What the exporter should have written for [value], apostrophe rule included. */
    private fun expected(value: String): String =
        if (value.firstOrNull() in formulaLeads) "'" + value else value

    private fun awkwardStrings(): List<String> {
        val random = Random(1234)
        val alphabet = "abc ,\"'\r\n=+-@\t;|\\{}[]💪₹—é0123456789".toCharArray()
        val generated = List(500) {
            val length = random.nextInt(1, 14)
            String(CharArray(length) { alphabet[random.nextInt(alphabet.size)] })
        }
        return generated + listOf(
            "plain",
            "with, comma",
            "with \"quote\"",
            "with\nnewline",
            "with\r\nboth",
            "=SUM(A1:A9)",
            "+1 rep",
            "-5 lb",
            "@everyone",
            "\"",
            "\"\"",
            ",",
            "'",
            "₹1,200 — heavy 💪",
        )
    }

    @Test
    fun everyAwkwardNoteComesBackAsItself() {
        val header = parse(csvOf("Bench Press", "", "Push")).first()
        val notesColumn = header.indexOf("set_notes")
        assertTrue("the export has a set_notes column", notesColumn >= 0)

        awkwardStrings().forEach { note ->
            val rows = parse(csvOf("Bench Press", note, "Push"))
            assertEquals("\"$note\" did not produce one row of data", 2, rows.size)
            assertEquals(
                "\"$note\" came back changed",
                expected(note),
                rows[1][notesColumn],
            )
            assertEquals(
                "\"$note\" changed the row's width",
                header.size,
                rows[1].size,
            )
        }
    }

    @Test
    fun everyAwkwardLiftNameAndSessionNameComeBackAsThemselves() {
        val header = parse(csvOf("Bench Press", "", "Push")).first()
        val nameColumn = header.indexOf("exercise")
        val sessionColumn = header.indexOf("session_name")
        assertTrue("the export names its columns", nameColumn >= 0 && sessionColumn >= 0)

        awkwardStrings().forEach { text ->
            val rows = parse(csvOf(text, "note", text))
            assertEquals("\"$text\" did not produce one row of data", 2, rows.size)
            assertEquals("\"$text\" came back changed as a lift", expected(text), rows[1][nameColumn])
            assertEquals("\"$text\" came back changed as a session", expected(text), rows[1][sessionColumn])
            assertEquals("\"$text\" changed the row's width", header.size, rows[1].size)
        }
    }

    @Test
    fun nothingATextCanContainRunsAsAFormula() {
        awkwardStrings().filter { it.firstOrNull() in formulaLeads }.forEach { note ->
            val rows = parse(csvOf("Bench Press", note, "Push"))
            val header = rows.first()
            val value = rows[1][header.indexOf("set_notes")]
            assertTrue(
                "\"$note\" would open as a formula",
                value.firstOrNull() !in formulaLeads,
            )
        }
    }
}
