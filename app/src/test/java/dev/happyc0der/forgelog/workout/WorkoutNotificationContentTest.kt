package dev.happyc0der.forgelog.workout

import dev.happyc0der.forgelog.domain.workout.RestTimer
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutNotificationContentTest {

    private val started = 1_000_000L

    @Test
    fun `with no rest it counts the workout up`() {
        val content = WorkoutNotificationContents.of(started, rest = null)
        assertEquals(WorkoutNotificationContent(WorkoutNotificationLine.Default, started, countDown = false), content)
    }

    @Test
    fun `a running rest counts down to its end`() {
        val rest = RestTimer.start(targetSeconds = 90, anchorEpochMs = started + 600_000L)
        val content = WorkoutNotificationContents.of(started, rest)
        val endsAt = started + 600_000L + 90_000L
        assertEquals(WorkoutNotificationContent(WorkoutNotificationLine.Resting(endsAt), endsAt, countDown = true), content)
    }

    @Test
    fun `an extended rest counts down to its new end`() {
        val rest = RestTimer.adjust(RestTimer.start(targetSeconds = 90, anchorEpochMs = started), 15)
        assertEquals(started + 105_000L, WorkoutNotificationContents.of(started, rest).chronometerBase)
    }

    @Test
    fun `a paused rest says what is left and the workout clock carries on`() {
        val rest = RestTimer.pause(RestTimer.start(targetSeconds = 90, anchorEpochMs = started), started + 10_000L)
        val content = WorkoutNotificationContents.of(started, rest)
        assertEquals(
            WorkoutNotificationContent(WorkoutNotificationLine.RestPaused(80), started, countDown = false),
            content,
        )
    }

    @Test
    fun `a skipped rest is no rest`() {
        val rest = RestTimer.dismiss(RestTimer.start(targetSeconds = 90, anchorEpochMs = started))
        assertEquals(WorkoutNotificationLine.Default, WorkoutNotificationContents.of(started, rest).line)
    }
}
