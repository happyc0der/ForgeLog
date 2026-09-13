package dev.happyc0der.forgelog.data.backup

import dev.happyc0der.forgelog.domain.backup.BackupCheck
import dev.happyc0der.forgelog.domain.model.StoredNumbers
import dev.happyc0der.forgelog.domain.workout.DurationInput
import dev.happyc0der.forgelog.domain.workout.DurationInputUnit
import dev.happyc0der.forgelog.ui.components.FEELING_RANGE
import dev.happyc0der.forgelog.ui.input.NumericInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Whatever the app lets you enter has to be something the app can read back.
 *
 * The importer is the only thing standing between a backup file and the database, so it is strict --
 * and every bound it enforces is also a bound the app has to respect on the way out, or it writes
 * files it will not accept. That fails in the worst direction: the entry saves, the export writes it
 * out, and the *restore* refuses, rejecting the whole file. One bad value quietly makes every backup
 * from then on unusable, with nothing to say which row is at fault.
 *
 * So this is a seam test, per bound, from what the app accepts through to a restore.
 *
 * Durations and rests are typed in either seconds or minutes, and a minutes entry is multiplied by
 * sixty before it is stored. The field's own ceiling is six whole digits, which is the importer's
 * ceiling too — in seconds. In minutes those six digits become a number sixty times larger, which
 * the importer refuses.
 *
 * That is worse than it sounds, because of which direction it fails in. The set saves. The export
 * writes it out. It is the *restore* that refuses, and it refuses the whole file, so the one set
 * with an absurd rest in it quietly makes every backup the user takes from then on unusable — with
 * nothing to say which set is the problem. Entering it at all takes a stray digit, which is the very
 * thing [DurationInput] says it clamps rather than discards.
 */
class EnteredValuesSurviveABackupTest {

    /** The most a duration field will accept, and some ordinary entries for comparison. */
    private val accepted = listOf("1", "90", "2.5", "16666.65", "99999", "999999", "999999.99")

    private fun secondsFromMinutes(typed: String): Int {
        assertEquals("the field refuses $typed, so this is not a real entry", typed, NumericInput.accept(typed, decimal = true))
        val seconds = DurationInput.parseSeconds(typed, DurationInputUnit.MINUTES)
        assertNotNull("$typed did not parse as minutes", seconds)
        return seconds!!
    }

    private fun envelopeWith(
        setDuration: Int? = null,
        setRest: Int? = null,
        targetDuration: Int? = null,
        targetRest: Int? = null,
        feeling: Int? = null,
        rpe: Int? = null,
        rir: Int? = null,
    ) = BackupEnvelope(
        appVersion = "1.0",
        databaseVersion = 3,
        exportedAtEpochMs = 1_700_000_000_000L,
        exercises = listOf(ExerciseBackup(id = 1, name = "Plank", category = "core", defaultUnit = "seconds")),
        programs = listOf(ProgramBackup(id = 1, name = "Core", color = "#A855F7")),
        programDays = listOf(ProgramDayBackup(id = 1, programId = 1, name = "Day 1")),
        programExercises = listOf(
            ProgramExerciseBackup(
                id = 1,
                programDayId = 1,
                exerciseId = 1,
                targetDurationSeconds = targetDuration,
                targetRestSeconds = targetRest,
            ),
        ),
        sessions = listOf(
            SessionBackup(
                id = 1,
                programId = 1,
                programDayId = 1,
                sessionName = "Core · Day 1",
                startedAt = 1_000,
                completedAt = 5_000,
                status = "completed",
                overallFeeling = feeling,
            ),
        ),
        sessionExercises = listOf(
            SessionExerciseBackup(
                id = 1,
                sessionId = 1,
                exerciseId = 1,
                displayNameSnapshot = "Plank",
                feeling = feeling,
                targetDurationSeconds = targetDuration,
                targetRestSeconds = targetRest,
            ),
        ),
        setLogs = listOf(
            SetLogBackup(
                id = 1,
                sessionExerciseId = 1,
                setNumber = 1,
                setType = "working",
                weightUnit = "seconds",
                durationSeconds = setDuration,
                restAfterSetSeconds = setRest,
                rpe = rpe,
                rir = rir,
                completed = true,
                completedAt = 2_000,
            ),
        ),
    )

    private fun assertRestorable(what: String, envelope: BackupEnvelope) {
        val reread = BackupSerializer.decode(BackupSerializer.encode(envelope))
        assertTrue(
            "a backup holding $what cannot be restored: $reread",
            reread is BackupCheck.Valid,
        )
    }

    @Test
    fun `a set duration typed in minutes can be restored`() {
        accepted.forEach { typed ->
            val seconds = secondsFromMinutes(typed)
            assertRestorable("a $typed minute set ($seconds seconds)", envelopeWith(setDuration = seconds))
        }
    }

    @Test
    fun `a rest typed in minutes can be restored`() {
        accepted.forEach { typed ->
            val seconds = secondsFromMinutes(typed)
            assertRestorable("a $typed minute rest ($seconds seconds)", envelopeWith(setRest = seconds))
        }
    }

    @Test
    fun `a planned duration and rest typed in minutes can be restored`() {
        accepted.forEach { typed ->
            val seconds = secondsFromMinutes(typed)
            assertRestorable(
                "a $typed minute target ($seconds seconds)",
                envelopeWith(targetDuration = seconds, targetRest = seconds),
            )
        }
    }

    /**
     * And the seconds field, which is the easy half: its six digits are already the limit, so this
     * only pins that the two ceilings are the same one and not a coincidence.
     */
    @Test
    fun `a duration typed in seconds can be restored`() {
        listOf("1", "3600", "999999").forEach { typed ->
            assertEquals(typed, NumericInput.accept(typed, decimal = false))
            val seconds = DurationInput.parseSeconds(typed, DurationInputUnit.SECONDS)
            assertNotNull(seconds)
            assertRestorable("a $typed second set", envelopeWith(setDuration = seconds))
        }
    }

    // --- and the rating scales, which were written down twice the same way -------------------------

    private fun assertRefused(what: String, envelope: BackupEnvelope) {
        val reread = BackupSerializer.decode(BackupSerializer.encode(envelope))
        assertTrue("a backup holding $what was accepted: $reread", reread is BackupCheck.Invalid)
    }

    /** Every rating the feeling control offers can be restored; one outside the scale cannot. */
    @Test
    fun `every feeling the control offers can be restored`() {
        FEELING_RANGE.forEach { feeling ->
            assertRestorable("a feeling of $feeling", envelopeWith(feeling = feeling))
        }
        assertRefused("a feeling of ${FEELING_RANGE.last + 1}", envelopeWith(feeling = FEELING_RANGE.last + 1))
        assertRefused("a feeling of ${FEELING_RANGE.first - 1}", envelopeWith(feeling = FEELING_RANGE.first - 1))
    }

    @Test
    fun `every rpe the set editor accepts can be restored`() {
        StoredNumbers.RPE_RANGE.forEach { rpe ->
            assertRestorable("an rpe of $rpe", envelopeWith(rpe = rpe))
        }
        assertRefused("an rpe of 11", envelopeWith(rpe = StoredNumbers.RPE_RANGE.last + 1))
    }

    @Test
    fun `every rir the set editor accepts can be restored, zero included`() {
        StoredNumbers.RIR_RANGE.forEach { rir ->
            assertRestorable("a rir of $rir", envelopeWith(rir = rir))
        }
        assertRefused("a rir of 11", envelopeWith(rir = StoredNumbers.RIR_RANGE.last + 1))
    }

    /**
     * And the scale the control offers is the scale the importer checks.
     *
     * Tautological now that there is one definition, which is the point: it fails the moment someone
     * gives either side its own copy again.
     */
    @Test
    fun `the control and the importer use one scale`() {
        assertEquals(StoredNumbers.FEELING_RANGE, FEELING_RANGE)
    }
}
