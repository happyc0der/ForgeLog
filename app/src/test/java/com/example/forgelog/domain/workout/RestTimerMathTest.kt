package com.example.forgelog.domain.workout

import org.junit.Assert.assertEquals
import org.junit.Test

class RestTimerMathTest {
    @Test
    fun formatElapsedIncludesHoursWhenNeeded() {
        assertEquals("0:05", formatElapsed(5_000L))
        assertEquals("1:01", formatElapsed(61_000L))
        assertEquals("1:01:01", formatElapsed(3_661_000L))
        assertEquals("1:30", formatSeconds(90))
    }
}
