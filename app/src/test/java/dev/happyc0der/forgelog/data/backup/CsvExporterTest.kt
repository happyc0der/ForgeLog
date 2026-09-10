package dev.happyc0der.forgelog.data.backup

import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.SessionExerciseWithSets
import dev.happyc0der.forgelog.domain.model.SetType
import dev.happyc0der.forgelog.domain.workout.sessionDetail
import dev.happyc0der.forgelog.domain.workout.sessionExercise
import dev.happyc0der.forgelog.domain.workout.setLog
import dev.happyc0der.forgelog.domain.workout.workoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class CsvExporterTest {

    private val zone = ZoneId.of("Asia/Kolkata")

    private fun exportOf(
        exerciseName: String = "Bench Press",
        notes: String? = null,
        sets: List<dev.happyc0der.forgelog.domain.model.SetLog> = listOf(setLog(notes = notes)),
    ): String = CsvExporter.export(
        listOf(
            sessionDetail(
                workoutSession(id = 1L, startedAt = 1_700_000_000_000L, completedAt = 1_700_003_600_000L),
                SessionExerciseWithSets(
                    exercise = sessionExercise(
                        id = 1L,
                        sessionId = 1L,
                        exerciseId = 1L,
                        displayName = exerciseName,
                    ),
                    sets = sets,
                ),
            ),
        ),
        zone,
    )

    private fun rows(csv: String): List<String> =
        csv.split("\r\n").filter { it.isNotBlank() }

    @Test
    fun `the header names every column and rows match its width`() {
        val csv = exportOf()
        val header = rows(csv).first().split(",")
        assertEquals(23, header.size)
        assertEquals("session_id", header.first())
        assertEquals("session_notes", header.last())
        assertEquals(header.size, rows(csv)[1].split(",").size)
    }

    @Test
    fun `rows use CRLF line endings`() {
        assertTrue(exportOf().endsWith("\r\n"))
    }

    @Test
    fun `a comma in a name is quoted rather than splitting the row`() {
        val csv = exportOf(exerciseName = "Row, Barbell")
        val dataRow = rows(csv)[1]
        assertTrue(dataRow.contains("\"Row, Barbell\""))
        // 23 columns plus the one comma inside the quoted field.
        assertEquals(23, dataRow.split(",").size - 1)
    }

    @Test
    fun `an embedded quote is doubled`() {
        val csv = exportOf(notes = """felt "easy" today""")
        assertTrue(rows(csv)[1].contains("\"felt \"\"easy\"\" today\""))
    }

    @Test
    fun `an embedded newline stays inside one quoted field`() {
        val csv = exportOf(notes = "line one\nline two")
        assertTrue(csv.contains("\"line one\nline two\""))
        // The newline must not create a new record: header + one data row.
        assertEquals(2, csv.split("\r\n").filter { it.isNotBlank() }.size)
    }

    @Test
    fun `fields that need no quoting are left bare`() {
        val dataRow = rows(exportOf())[1]
        assertTrue(dataRow.startsWith("1,Session 1,completed,"))
    }

    @Test
    fun `a null field is an empty cell, not the word null`() {
        val csv = exportOf(
            sets = listOf(setLog(reps = null, weight = null, rpe = null, rir = null, notes = null)),
        )
        assertTrue(!csv.contains("null"))
    }

    @Test
    fun `timestamps are rendered in the given zone`() {
        // 1_700_000_000_000 is 2023-11-15T03:43:20 in Asia/Kolkata (UTC+5:30), not 22:13 UTC.
        val dataRow = rows(exportOf())[1]
        assertTrue(dataRow, dataRow.contains("2023-11-15 03:43:20"))
        assertTrue(dataRow, dataRow.contains("2023-11-15 04:43:20"))
    }

    @Test
    fun `an exercise with no sets still produces a row`() {
        val csv = exportOf(sets = emptyList())
        val dataRow = rows(csv)[1]
        assertEquals(23, dataRow.split(",").size)
        assertTrue(dataRow.contains("Bench Press"))
    }

    @Test
    fun `incomplete sets are exported and flagged rather than dropped`() {
        val csv = exportOf(sets = listOf(setLog(completed = false)))
        assertTrue(rows(csv)[1].contains("false"))
    }

    @Test
    fun `warmup sets are exported with their type`() {
        val csv = exportOf(sets = listOf(setLog(setType = SetType.WARMUP)))
        assertTrue(rows(csv)[1].contains("warmup"))
    }

    @Test
    fun `kilogram sets keep their own unit rather than being converted`() {
        val csv = exportOf(sets = listOf(setLog(weight = 60.0, weightUnit = ExerciseUnit.KG)))
        val dataRow = rows(csv)[1]
        // 60, not 132: the unit travels with the number rather than the number being converted.
        assertTrue("expected 60 in: $dataRow", dataRow.contains(",60,"))
        assertTrue(dataRow.contains("kg"))
    }

    @Test
    fun `whole weights are written without a trailing decimal`() {
        val csv = exportOf(sets = listOf(setLog(weight = 225.0)))
        val dataRow = rows(csv)[1]
        assertTrue("expected 225 in: $dataRow", dataRow.contains(",225,"))
        assertFalse("225.0 imports as text in some locales: $dataRow", dataRow.contains("225.0"))
    }

    @Test
    fun `fractional weights keep their decimal`() {
        val csv = exportOf(sets = listOf(setLog(weight = 62.5)))
        assertTrue(rows(csv)[1].contains("62.5"))
    }

    @Test
    fun `a large distance is not written in scientific notation`() {
        val csv = exportOf(sets = listOf(setLog(distanceMeters = 12_000_000.0)))
        val dataRow = rows(csv)[1]
        assertTrue("expected plain digits in: $dataRow", dataRow.contains("12000000"))
        assertFalse("a spreadsheet reads 1.2E7 as text: $dataRow", dataRow.contains("E7"))
    }

    /*
     * A note is free text, and the file is meant to be opened in a spreadsheet and shared. A value
     * beginning = + - or @ is executed as a formula on open, so it is quoted and prefixed.
     */

    @Test
    fun `a note that looks like a formula is neutralised`() {
        val csv = exportOf(sets = listOf(setLog(notes = "=HYPERLINK(\"http://example.com\",\"x\")")))
        val dataRow = rows(csv).drop(1).joinToString("\n")
        assertFalse("must not start a field with =: $dataRow", dataRow.contains(",=HYPERLINK"))
        assertTrue(dataRow.contains("'=HYPERLINK"))
    }

    @Test
    fun `a leading minus is neutralised too`() {
        val csv = exportOf(sets = listOf(setLog(notes = "-2+3")))
        assertTrue(rows(csv).drop(1).joinToString("\n").contains("'-2+3"))
    }

    @Test
    fun `ordinary notes are left exactly as written`() {
        val csv = exportOf(sets = listOf(setLog(notes = "felt easy")))
        val dataRow = rows(csv).drop(1).joinToString("\n")
        assertTrue(dataRow.contains("felt easy"))
        assertFalse(dataRow.contains("'felt easy"))
    }

    @Test
    fun `an empty export is just the header`() {
        val csv = CsvExporter.export(emptyList(), zone)
        assertEquals(1, rows(csv).size)
    }

    @Test
    fun `sessions come out oldest first`() {
        val csv = CsvExporter.export(
            listOf(
                sessionDetail(
                    workoutSession(id = 2L, startedAt = 9_000L, completedAt = 10_000L),
                    SessionExerciseWithSets(
                        exercise = sessionExercise(id = 2L, sessionId = 2L, exerciseId = 1L, displayName = "Later"),
                        sets = listOf(setLog(id = 2L, sessionExerciseId = 2L)),
                    ),
                ),
                sessionDetail(
                    workoutSession(id = 1L, startedAt = 1_000L, completedAt = 2_000L),
                    SessionExerciseWithSets(
                        exercise = sessionExercise(id = 1L, sessionId = 1L, exerciseId = 1L, displayName = "Earlier"),
                        sets = listOf(setLog(id = 1L, sessionExerciseId = 1L)),
                    ),
                ),
            ),
            zone,
        )
        val dataRows = rows(csv).drop(1)
        assertTrue(dataRows.first().contains("Earlier"))
        assertTrue(dataRows.last().contains("Later"))
    }
}

/** Sessions and exercises with nothing logged must still appear, with the right column count. */
class CsvExporterEmptyRowsTest {

    private val zone = ZoneId.of("Asia/Kolkata")

    @Test
    fun `a session with no exercises still produces one full-width row`() {
        val csv = CsvExporter.export(
            listOf(sessionDetail(workoutSession(id = 1L, startedAt = 1_000L, completedAt = 2_000L))),
            zone,
        )
        val rows = csv.split("\r\n").filter { it.isNotBlank() }
        assertEquals(2, rows.size)
        assertEquals(23, rows[1].split(",").size)
        assertTrue(rows[1].startsWith("1,Session 1,completed,"))
    }

    @Test
    fun `a session note still reaches the row when nothing was logged`() {
        val csv = CsvExporter.export(
            listOf(
                sessionDetail(
                    workoutSession(id = 1L, startedAt = 1_000L, completedAt = 2_000L)
                        .copy(overallFeeling = 3, overallNotes = "cut it short"),
                ),
            ),
            zone,
        )
        val dataRow = csv.split("\r\n").filter { it.isNotBlank() }[1]
        assertTrue(dataRow.endsWith("3,cut it short"))
    }
}
