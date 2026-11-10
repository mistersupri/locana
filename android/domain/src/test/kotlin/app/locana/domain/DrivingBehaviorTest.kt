package app.locana.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DrivingBehaviorTest {
    private fun fix(d: DrivingBehavior, t: Long, v: Float, vehicle: Boolean = true, phone: Boolean = false, acc: Float = 8f) = d.onFix(t, v, acc, vehicle, phone)

    @Test fun `a sudden stop from speed is hard braking`() {
        val d = DrivingBehavior()
        assertNull(fix(d, 0, 14f))
        assertEquals(DriveEventKind.HARD_BRAKE, fix(d, 2_000, 5f)) // minus 4.5 m/s per second
    }

    @Test fun `a gentle slowdown is fine`() {
        val d = DrivingBehavior()
        fix(d, 0, 14f)
        assertNull(fix(d, 3_000, 10f)) // minus 1.3 m/s per second
    }

    @Test fun `a hard launch is rapid acceleration`() {
        val d = DrivingBehavior()
        fix(d, 0, 2f)
        assertEquals(DriveEventKind.RAPID_ACCEL, fix(d, 2_000, 9f)) // plus 3.5 m/s per second
    }

    @Test fun `nothing is judged outside a vehicle or with a poor fix or a long gap`() {
        val d = DrivingBehavior()
        fix(d, 0, 14f, vehicle = false)
        assertNull(fix(d, 2_000, 2f, vehicle = false))
        fix(d, 4_000, 14f)
        assertNull(d.onFix(6_000, 2f, 90f, true, false)) // accuracy too poor
        fix(d, 8_000, 14f)
        assertNull(fix(d, 20_000, 2f)) // twelve seconds apart: not a single braking
    }

    @Test fun `the same kind is not repeated within ten seconds`() {
        val d = DrivingBehavior()
        fix(d, 0, 20f)
        assertEquals(DriveEventKind.HARD_BRAKE, fix(d, 2_000, 10f))
        assertNull(fix(d, 4_000, 2f)) // still the same stop
    }

    @Test fun `using the phone while moving counts after a while, once a minute`() {
        val d = DrivingBehavior()
        assertNull(fix(d, 0, 12f, phone = true))
        assertNull(fix(d, 5_000, 12f, phone = true))
        assertNull(fix(d, 10_000, 12f, phone = true))
        assertEquals(DriveEventKind.PHONE_USE, fix(d, 15_000, 12f, phone = true))
        assertNull(fix(d, 20_000, 12f, phone = true))
        assertNull(fix(d, 25_000, 12f, phone = true)) // under a minute since the last one
    }

    @Test fun `a glance that stops the streak resets the clock, and standing still does not count`() {
        val d = DrivingBehavior()
        fix(d, 0, 12f, phone = true)
        fix(d, 5_000, 12f, phone = false)
        assertNull(fix(d, 10_000, 12f, phone = true))
        assertNull(fix(d, 15_000, 12f, phone = true)) // only five seconds since the glance ended
        assertNull(fix(d, 30_000, 0.5f, phone = true))
    }
}
