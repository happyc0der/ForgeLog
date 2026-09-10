package dev.happyc0der.forgelog.domain.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryCopyNamesTest {
    @Test
    fun programCopyAppendsCopy() {
        assertEquals("Push (copy)", LibraryCopyNames.programCopy("Push"))
    }

    @Test
    fun dayCopyAppendsB() {
        assertEquals("Push Day B", LibraryCopyNames.dayCopy("Push Day"))
    }

    @Test
    fun dayCopyAvoidsDoubleB() {
        assertEquals("Push Day B copy", LibraryCopyNames.dayCopy("Push Day B"))
    }
}

class HowToUrlTest {
    @Test
    fun blankBecomesNull() {
        assertNull(HowToUrl.normalize("  ").getOrThrow())
    }

    @Test
    fun addsHttpsWhenMissing() {
        assertEquals("https://example.com/bench", HowToUrl.normalize("example.com/bench").getOrThrow())
    }

    @Test
    fun rejectsInvalidUrl() {
        assertTrue(HowToUrl.normalize("not a url").isFailure)
    }

    @Test
    fun acceptsHttps() {
        assertEquals(
            "https://exrx.net/WeightExercises/PectoralSternal/BBBenchPress",
            HowToUrl.normalize("https://exrx.net/WeightExercises/PectoralSternal/BBBenchPress").getOrThrow(),
        )
    }
}

class ExerciseDeletePolicyTest {
    @Test
    fun cannotHardDeleteWhenSessionHistoryExists() {
        assertFalse(ExerciseDeletePolicy.canHardDelete(hasSessionHistory = true))
        assertTrue(ExerciseDeletePolicy.canHardDelete(hasSessionHistory = false))
    }
}
