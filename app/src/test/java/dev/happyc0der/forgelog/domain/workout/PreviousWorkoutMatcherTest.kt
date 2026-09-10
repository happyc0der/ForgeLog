package dev.happyc0der.forgelog.domain.workout

import dev.happyc0der.forgelog.domain.model.SessionExerciseWithSets
import dev.happyc0der.forgelog.domain.model.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreviousWorkoutMatcherTest {

    private val current = workoutSession(id = 99L, programId = 1L, programDayId = 10L)
    private val currentExercise = sessionExercise(
        id = 990L,
        sessionId = 99L,
        exerciseId = 5L,
        displayName = "Bench Press",
        exerciseOrder = 1,
    )

    @Test
    fun matchesPreviousCompletedExerciseById() {
        val olderSameExercise = sessionDetail(
            workoutSession(id = 1L, completedAt = 1_000L),
            SessionExerciseWithSets(
                exercise = sessionExercise(
                    id = 11L,
                    sessionId = 1L,
                    exerciseId = 5L,
                    displayName = "Old Bench Name",
                    exerciseOrder = 1,
                ),
                sets = listOf(setLog(id = 111L, sessionExerciseId = 11L, weight = 135.0)),
            ),
        )

        val match = PreviousWorkoutMatcher.findPreviousExercise(
            currentSession = current,
            currentExercise = currentExercise,
            history = listOf(olderSameExercise),
        )

        assertEquals(11L, match?.exercise?.id)
        assertEquals(135.0, match?.sets?.single()?.weight)
    }

    @Test
    fun fallsBackToDisplayNameWhenExerciseIdIsMissing() {
        val unnamedCurrent = currentExercise.copy(exerciseId = null, displayNameSnapshot = "  bench press ")
        val history = sessionDetail(
            workoutSession(id = 2L, completedAt = 3_000L),
            SessionExerciseWithSets(
                exercise = sessionExercise(
                    id = 22L,
                    sessionId = 2L,
                    exerciseId = 8L,
                    displayName = "Bench Press",
                    exerciseOrder = 0,
                ),
                sets = listOf(setLog(id = 221L, sessionExerciseId = 22L)),
            ),
        )

        val match = PreviousWorkoutMatcher.findPreviousExercise(
            currentSession = current,
            currentExercise = unnamedCurrent,
            history = listOf(history),
        )

        assertEquals(22L, match?.exercise?.id)
    }

    @Test
    fun doesNotUseNameFallbackWhenCurrentHasExerciseId() {
        val history = sessionDetail(
            workoutSession(id = 2L, completedAt = 3_000L),
            SessionExerciseWithSets(
                exercise = sessionExercise(
                    id = 22L,
                    sessionId = 2L,
                    exerciseId = 8L,
                    displayName = "Bench Press",
                ),
                sets = emptyList(),
            ),
        )

        val match = PreviousWorkoutMatcher.findPreviousExercise(
            currentSession = current,
            currentExercise = currentExercise,
            history = listOf(history),
        )

        assertNull(match)
    }

    @Test
    fun ignoresInProgressAbandonedAndCurrentSession() {
        val inProgress = sessionDetail(
            workoutSession(id = 3L, status = SessionStatus.IN_PROGRESS, completedAt = null),
            SessionExerciseWithSets(
                exercise = sessionExercise(id = 31L, sessionId = 3L, exerciseId = 5L, displayName = "Bench Press"),
                sets = emptyList(),
            ),
        )
        val abandoned = sessionDetail(
            workoutSession(id = 4L, status = SessionStatus.ABANDONED, completedAt = 4_000L),
            SessionExerciseWithSets(
                exercise = sessionExercise(id = 41L, sessionId = 4L, exerciseId = 5L, displayName = "Bench Press"),
                sets = emptyList(),
            ),
        )
        val currentAsHistory = sessionDetail(
            current.copy(status = SessionStatus.COMPLETED),
            SessionExerciseWithSets(
                exercise = currentExercise,
                sets = emptyList(),
            ),
        )

        val match = PreviousWorkoutMatcher.findPreviousExercise(
            currentSession = current,
            currentExercise = currentExercise,
            history = listOf(inProgress, abandoned, currentAsHistory),
        )

        assertNull(match)
    }

    @Test
    fun prefersSameProgramDayOverAMoreRecentDifferentDay() {
        val sameDayOlder = sessionDetail(
            workoutSession(id = 5L, programId = 1L, programDayId = 10L, completedAt = 5_000L),
            SessionExerciseWithSets(
                exercise = sessionExercise(id = 51L, sessionId = 5L, exerciseId = 5L, displayName = "Bench Press"),
                sets = listOf(setLog(id = 511L, sessionExerciseId = 51L, weight = 185.0)),
            ),
        )
        val differentDayNewer = sessionDetail(
            workoutSession(id = 6L, programId = 1L, programDayId = 11L, completedAt = 9_000L),
            SessionExerciseWithSets(
                exercise = sessionExercise(id = 61L, sessionId = 6L, exerciseId = 5L, displayName = "Bench Press"),
                sets = listOf(setLog(id = 611L, sessionExerciseId = 61L, weight = 225.0)),
            ),
        )

        val match = PreviousWorkoutMatcher.findPreviousExercise(
            currentSession = current,
            currentExercise = currentExercise,
            history = listOf(differentDayNewer, sameDayOlder),
        )

        assertEquals(51L, match?.exercise?.id)
        assertEquals(185.0, match?.sets?.single()?.weight)
    }

    @Test
    fun prefersSameProgramWhenProgramDayDoesNotMatch() {
        val sameProgram = sessionDetail(
            workoutSession(id = 7L, programId = 1L, programDayId = 12L, completedAt = 2_000L),
            SessionExerciseWithSets(
                exercise = sessionExercise(id = 71L, sessionId = 7L, exerciseId = 5L, displayName = "Bench Press"),
                sets = emptyList(),
            ),
        )
        val otherProgramNewer = sessionDetail(
            workoutSession(id = 8L, programId = 2L, programDayId = 20L, completedAt = 8_000L),
            SessionExerciseWithSets(
                exercise = sessionExercise(id = 81L, sessionId = 8L, exerciseId = 5L, displayName = "Bench Press"),
                sets = emptyList(),
            ),
        )

        val match = PreviousWorkoutMatcher.findPreviousExercise(
            currentSession = current,
            currentExercise = currentExercise,
            history = listOf(otherProgramNewer, sameProgram),
        )

        assertEquals(71L, match?.exercise?.id)
    }

    @Test
    fun usesMostRecentCompletedSessionWhenNoProgramOverlap() {
        val older = sessionDetail(
            workoutSession(id = 9L, programId = 3L, programDayId = 30L, completedAt = 1_000L),
            SessionExerciseWithSets(
                exercise = sessionExercise(id = 91L, sessionId = 9L, exerciseId = 5L, displayName = "Bench Press"),
                sets = emptyList(),
            ),
        )
        val newer = sessionDetail(
            workoutSession(id = 10L, programId = 4L, programDayId = 40L, completedAt = 4_000L),
            SessionExerciseWithSets(
                exercise = sessionExercise(id = 101L, sessionId = 10L, exerciseId = 5L, displayName = "Bench Press"),
                sets = emptyList(),
            ),
        )

        val match = PreviousWorkoutMatcher.findPreviousExercise(
            currentSession = current.copy(programId = null, programDayId = null),
            currentExercise = currentExercise,
            history = listOf(older, newer),
        )

        assertEquals(101L, match?.exercise?.id)
    }
}

/**
 * The matcher used to return only the exercise, throwing away the session it found it in — which is
 * why the planner could never show a date and the user could not tell last Tuesday from last March.
 */
class PreviousPerformanceTest {

    @Test
    fun `the matched session is carried, not discarded`() {
        val pastSession = workoutSession(id = 1L, startedAt = 1_000L, completedAt = 5_000L)
        val history = listOf(
            sessionDetail(
                pastSession,
                SessionExerciseWithSets(
                    exercise = sessionExercise(id = 10L, sessionId = 1L, exerciseId = 7L, displayName = "Bench"),
                    sets = listOf(setLog(id = 1L, sessionExerciseId = 10L)),
                ),
            ),
        )

        val performance = PreviousWorkoutMatcher.findPrevious(
            currentSession = workoutSession(id = 2L, startedAt = 9_000L, completedAt = null),
            currentExercise = sessionExercise(id = 20L, sessionId = 2L, exerciseId = 7L, displayName = "Bench"),
            history = history,
        )

        assertEquals(1L, performance?.session?.id)
        assertEquals(5_000L, performance?.session?.completedAt)
        assertEquals("Bench", performance?.exercise?.exercise?.displayNameSnapshot)
    }

    @Test
    fun `completedSets excludes rows that were never ticked`() {
        val history = listOf(
            sessionDetail(
                workoutSession(id = 1L),
                SessionExerciseWithSets(
                    exercise = sessionExercise(id = 10L, sessionId = 1L, exerciseId = 7L, displayName = "Bench"),
                    sets = listOf(
                        setLog(id = 1L, sessionExerciseId = 10L, setNumber = 1, completed = true),
                        setLog(id = 2L, sessionExerciseId = 10L, setNumber = 2, completed = false),
                        setLog(id = 3L, sessionExerciseId = 10L, setNumber = 3, completed = true),
                    ),
                ),
            ),
        )

        val performance = PreviousWorkoutMatcher.findPrevious(
            currentSession = workoutSession(id = 2L, completedAt = null),
            currentExercise = sessionExercise(id = 20L, sessionId = 2L, exerciseId = 7L, displayName = "Bench"),
            history = history,
        )

        assertEquals(3, performance?.exercise?.sets?.size)
        // A set added and never completed did not happen, so history must not show it.
        assertEquals(listOf(1, 3), performance?.completedSets?.map { it.setNumber })
    }

    @Test
    fun `the older findPreviousExercise entry point still behaves the same`() {
        val history = listOf(
            sessionDetail(
                workoutSession(id = 1L),
                SessionExerciseWithSets(
                    exercise = sessionExercise(id = 10L, sessionId = 1L, exerciseId = 7L, displayName = "Bench"),
                    sets = listOf(setLog(id = 1L, sessionExerciseId = 10L)),
                ),
            ),
        )
        val current = sessionExercise(id = 20L, sessionId = 2L, exerciseId = 7L, displayName = "Bench")
        val session = workoutSession(id = 2L, completedAt = null)

        assertEquals(
            PreviousWorkoutMatcher.findPrevious(session, current, history)?.exercise,
            PreviousWorkoutMatcher.findPreviousExercise(session, current, history),
        )
    }

    /*
     * Recency outranks context.
     *
     * Ranking by program day first meant a six-week-old session from the matching day beat
     * yesterday's, so "last time" showed weights the user had moved past — and SetPrefill filled
     * today's first set from it.
     */

    @Test
    fun `yesterday beats a much older session from the matching program day`() {
        val current = workoutSession(id = 100L, startedAt = day(40), programId = 1L, programDayId = 7L)
        val exercise = sessionExercise(id = 1000L, sessionId = 100L, exerciseId = 5L, displayName = "Bench")

        val history = listOf(
            // Same program day, six weeks ago.
            sessionDetail(
                workoutSession(id = 1L, startedAt = day(1), completedAt = day(1), programId = 1L, programDayId = 7L),
                SessionExerciseWithSets(
                    exercise = sessionExercise(id = 11L, sessionId = 1L, exerciseId = 5L, displayName = "Bench"),
                    sets = listOf(setLog(id = 111L, weight = 135.0)),
                ),
            ),
            // Ad-hoc, yesterday.
            sessionDetail(
                workoutSession(id = 2L, startedAt = day(39), completedAt = day(39)),
                SessionExerciseWithSets(
                    exercise = sessionExercise(id = 22L, sessionId = 2L, exerciseId = 5L, displayName = "Bench"),
                    sets = listOf(setLog(id = 222L, weight = 205.0)),
                ),
            ),
        )

        val match = PreviousWorkoutMatcher.findPrevious(current, exercise, history)
        assertEquals(2L, match?.session?.id)
    }

    @Test
    fun `among sessions on the same day the matching program day wins`() {
        val current = workoutSession(id = 100L, startedAt = day(40), programId = 1L, programDayId = 7L)
        val exercise = sessionExercise(id = 1000L, sessionId = 100L, exerciseId = 5L, displayName = "Bench")

        val sameDay = day(39)
        val history = listOf(
            sessionDetail(
                workoutSession(id = 3L, startedAt = sameDay, completedAt = sameDay),
                SessionExerciseWithSets(
                    exercise = sessionExercise(id = 33L, sessionId = 3L, exerciseId = 5L, displayName = "Bench"),
                    sets = listOf(setLog(id = 333L, weight = 155.0)),
                ),
            ),
            sessionDetail(
                workoutSession(
                    id = 4L,
                    startedAt = sameDay + 3_600_000L,
                    completedAt = sameDay + 3_600_000L,
                    programId = 1L,
                    programDayId = 7L,
                ),
                SessionExerciseWithSets(
                    exercise = sessionExercise(id = 44L, sessionId = 4L, exerciseId = 5L, displayName = "Bench"),
                    sets = listOf(setLog(id = 444L, weight = 185.0)),
                ),
            ),
        )

        val match = PreviousWorkoutMatcher.findPrevious(current, exercise, history)
        assertEquals(4L, match?.session?.id)
    }

    private fun day(n: Long): Long = n * 24L * 60L * 60L * 1000L
}
