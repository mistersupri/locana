package app.locana.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashDetectorTest {
    private val hard = 60f // about 6 g along one axis

    @Test fun `a hard impact in a vehicle is a crash`() {
        assertTrue(CrashDetector().onSample(hard, 0f, 0f, 1_000, inVehicle = true))
    }

    @Test fun `the same impact outside a vehicle is ignored`() {
        assertFalse(CrashDetector().onSample(hard, 0f, 0f, 1_000, inVehicle = false))
    }

    @Test fun `ordinary driving and a bump are ignored`() {
        val d = CrashDetector()
        assertFalse(d.onSample(0f, 0f, 9.8f, 0, true))
        assertFalse(d.onSample(20f, 5f, 12f, 100, true)) // a pothole, about 2.5 g
    }

    @Test fun `one impact asks once, then stays quiet for two minutes`() {
        val d = CrashDetector()
        assertTrue(d.onSample(hard, 0f, 0f, 0, true))
        assertFalse(d.onSample(hard, 0f, 0f, 500, true))
        assertFalse(d.onSample(hard, 0f, 0f, 119_000, true))
        assertTrue(d.onSample(hard, 0f, 0f, 121_000, true))
    }

    @Test fun `vehicle context comes from activity or a fast recent fix`() {
        assertTrue(CrashDetector.inVehicle("vehicle", null, 0))
        assertTrue(CrashDetector.inVehicle(null, 12f, 10_000))
        assertFalse(CrashDetector.inVehicle("walking", 1.4f, 5_000))
        assertFalse(CrashDetector.inVehicle(null, 12f, 120_000))
        assertEquals(false, CrashDetector.inVehicle("still", null, 0))
    }
}
