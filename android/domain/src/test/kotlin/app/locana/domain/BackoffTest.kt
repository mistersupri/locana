package app.locana.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BackoffTest {
    @Test
    fun `delays grow, stay inside the window and cap at max`() {
        val b = Backoff(baseMs = 1_000, maxMs = 30_000, random = Random(42))
        val delays = List(10) { b.nextDelayMs() }
        assertTrue(delays[0] in 500..1_000)
        assertTrue(delays[1] in 1_000..2_000)
        assertTrue(delays[2] in 2_000..4_000)
        assertTrue(delays.drop(5).all { it in 15_000..30_000 })
    }

    @Test
    fun `reset starts over`() {
        val b = Backoff(random = Random(1))
        repeat(5) { b.nextDelayMs() }
        b.reset()
        assertEquals(0, b.attempt)
        assertTrue(b.nextDelayMs() <= 1_000)
    }
}
