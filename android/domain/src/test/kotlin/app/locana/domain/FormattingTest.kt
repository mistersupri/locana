package app.locana.domain

import org.junit.Test
import org.junit.Assert.assertEquals

class FormattingTest {
    private fun min(m: Long) = m * 60_000

    @Test fun `short durations`() {
        assertEquals("under a minute", Formatting.duration(20_000))
        assertEquals("1 min", Formatting.duration(min(1)))
        assertEquals("42 mins", Formatting.duration(min(42)))
    }

    @Test fun `hours and minutes`() {
        assertEquals("1 hr", Formatting.duration(min(60)))
        assertEquals("3 hrs, 52 mins", Formatting.duration(min(232)))
        assertEquals("13 hrs, 20 mins", Formatting.duration(min(800)))
        assertEquals("2 hrs, 1 min", Formatting.duration(min(121)))
    }

    @Test fun `days`() {
        assertEquals("1 day", Formatting.duration(min(24 * 60)))
        assertEquals("2 days, 4 hrs", Formatting.duration(min(2 * 24 * 60 + 4 * 60 + 10)))
    }

    @Test fun `speed`() {
        assertEquals("36 km/h", Formatting.speedKmh(10.0))
    }
}
