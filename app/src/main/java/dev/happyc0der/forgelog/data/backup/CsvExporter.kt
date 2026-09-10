package dev.happyc0der.forgelog.data.backup

import dev.happyc0der.forgelog.domain.model.SessionDetail
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One row per logged set, for opening in a spreadsheet.
 *
 * RFC 4180 quoting is not optional here: exercise names and set notes routinely contain commas, and
 * a note can contain a newline or a quote. Getting that wrong produces a file that opens but is
 * subtly misaligned, which is worse than one that fails loudly.
 */
object CsvExporter {

    private val HEADERS = listOf(
        "session_id",
        "session_name",
        "status",
        "started_at",
        "completed_at",
        "exercise",
        "exercise_order",
        "set_number",
        "set_type",
        "reps",
        "weight",
        "weight_unit",
        "duration_seconds",
        "distance_meters",
        "rest_after_seconds",
        "rpe",
        "rir",
        "completed",
        "set_notes",
        "exercise_notes",
        "exercise_feeling",
        "session_feeling",
        "session_notes",
    )

    fun export(sessions: List<SessionDetail>, zone: ZoneId): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)
        val builder = StringBuilder()
        builder.appendRow(HEADERS)

        sessions
            .sortedBy { it.session.startedAt }
            .forEach { detail ->
                val session = detail.session
                // A session with nothing logged still earns a row. Dropping it would make the export
                // disagree with the app's own history screen about which sessions exist.
                if (detail.exercises.isEmpty()) {
                    builder.appendRow(
                        sessionColumns(session, formatter, zone) +
                            // Everything from exercise through exercise_feeling is blank: 16 columns.
                            List(16) { "" } +
                            listOf(
                                session.overallFeeling?.toString().orEmpty(),
                                session.overallNotes.orEmpty(),
                            ),
                    )
                    return@forEach
                }
                detail.exercises
                    .sortedBy { it.exercise.exerciseOrder }
                    .forEach { logged ->
                        // An exercise with no sets still earns a row, otherwise "I opened it and did
                        // nothing" silently disappears from the export.
                        if (logged.sets.isEmpty()) {
                            builder.appendRow(
                                sessionColumns(session, formatter, zone) +
                                    listOf(
                                        logged.exercise.displayNameSnapshot,
                                        logged.exercise.exerciseOrder.toString(),
                                    ) +
                                    // The eleven set columns plus set_notes, all blank.
                                    List(12) { "" } +
                                    listOf(
                                        logged.exercise.exerciseNotes.orEmpty(),
                                        logged.exercise.feeling?.toString().orEmpty(),
                                        session.overallFeeling?.toString().orEmpty(),
                                        session.overallNotes.orEmpty(),
                                    ),
                            )
                            return@forEach
                        }
                        logged.sets.sortedBy { it.setNumber }.forEach { set ->
                            builder.appendRow(
                                sessionColumns(session, formatter, zone) + listOf(
                                    logged.exercise.displayNameSnapshot,
                                    logged.exercise.exerciseOrder.toString(),
                                    set.setNumber.toString(),
                                    set.setType.storageValue,
                                    set.reps?.toString().orEmpty(),
                                    set.weight?.toString().orEmpty(),
                                    set.weightUnit.storageValue,
                                    set.durationSeconds?.toString().orEmpty(),
                                    set.distanceMeters?.toString().orEmpty(),
                                    set.restAfterSetSeconds?.toString().orEmpty(),
                                    set.rpe?.toString().orEmpty(),
                                    set.rir?.toString().orEmpty(),
                                    set.completed.toString(),
                                    set.notes.orEmpty(),
                                    logged.exercise.exerciseNotes.orEmpty(),
                                    logged.exercise.feeling?.toString().orEmpty(),
                                    session.overallFeeling?.toString().orEmpty(),
                                    session.overallNotes.orEmpty(),
                                ),
                            )
                        }
                    }
            }
        return builder.toString()
    }

    private fun sessionColumns(
        session: dev.happyc0der.forgelog.domain.model.WorkoutSession,
        formatter: DateTimeFormatter,
        zone: ZoneId,
    ): List<String> = listOf(
        session.id.toString(),
        session.sessionName,
        session.status.storageValue,
        formatter.format(Instant.ofEpochMilli(session.startedAt).atZone(zone)),
        session.completedAt?.let { formatter.format(Instant.ofEpochMilli(it).atZone(zone)) }.orEmpty(),
    )

    private fun StringBuilder.appendRow(values: List<String>) {
        append(values.joinToString(",") { escape(it) })
        append("\r\n")
    }

    /** Quoted only when it has to be, and embedded quotes are doubled, per RFC 4180. */
    private fun escape(value: String): String {
        val needsQuoting = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!needsQuoting) return value
        return "\"" + value.replace("\"", "\"\"") + "\""
    }
}
