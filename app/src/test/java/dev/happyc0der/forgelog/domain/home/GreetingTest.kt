package dev.happyc0der.forgelog.domain.home

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class GreetingTest {

    private val kolkata = ZoneId.of("Asia/Kolkata")

    private fun at(hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(2026, 3, 10, hour, minute).atZone(kolkata).toInstant().toEpochMilli()

    @Test
    fun `each bucket covers its documented hours`() {
        assertEquals(DayPart.MORNING, Greeting.dayPart(at(5), kolkata))
        assertEquals(DayPart.MORNING, Greeting.dayPart(at(11, 59), kolkata))
        assertEquals(DayPart.AFTERNOON, Greeting.dayPart(at(12), kolkata))
        assertEquals(DayPart.AFTERNOON, Greeting.dayPart(at(16, 59), kolkata))
        assertEquals(DayPart.EVENING, Greeting.dayPart(at(17), kolkata))
        assertEquals(DayPart.EVENING, Greeting.dayPart(at(21, 59), kolkata))
        assertEquals(DayPart.NIGHT, Greeting.dayPart(at(22), kolkata))
    }

    @Test
    fun `night wraps past midnight`() {
        assertEquals(DayPart.NIGHT, Greeting.dayPart(at(23, 30), kolkata))
        assertEquals(DayPart.NIGHT, Greeting.dayPart(at(0), kolkata))
        assertEquals(DayPart.NIGHT, Greeting.dayPart(at(4, 59), kolkata))
    }

    @Test
    fun `the same instant greets differently in different zones`() {
        // 09:00 in Kolkata is 03:30 UTC — morning there, still night in London.
        val instant = at(9)
        assertEquals(DayPart.MORNING, Greeting.dayPart(instant, kolkata))
        assertEquals(DayPart.NIGHT, Greeting.dayPart(instant, ZoneId.of("Europe/London")))
    }
}
