package app.locana.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingPolicyTest {
    private val base = TrackingInputs(
        sharingActive = true, explorationEnabled = false, appInForeground = true, moving = true,
        batteryPct = 80, charging = false, powerSaveMode = false, online = true,
    )

    @Test
    fun `nothing enabled means no tracking at all`() {
        assertFalse(TrackingPolicy.plan(base.copy(sharingActive = false)).active)
    }

    @Test
    fun `foreground movement is frequent and uploads right away`() {
        val p = TrackingPolicy.plan(base)
        assertEquals(10_000, p.intervalMs)
        assertEquals(0, p.uploadBatchMs)
        assertEquals(Accuracy.HIGH, p.accuracy)
    }

    @Test
    fun `stationary slows down and batches`() {
        val p = TrackingPolicy.plan(base.copy(moving = false))
        assertEquals(120_000, p.intervalMs)
        assertTrue(p.uploadBatchMs >= 300_000)
    }

    @Test
    fun `background sharing uses balanced accuracy unless exploring`() {
        assertEquals(Accuracy.BALANCED, TrackingPolicy.plan(base.copy(appInForeground = false)).accuracy)
        assertEquals(Accuracy.HIGH, TrackingPolicy.plan(base.copy(appInForeground = false, explorationEnabled = true)).accuracy)
    }

    @Test
    fun `low battery doubles intervals, critical battery goes low power`() {
        assertEquals(20_000, TrackingPolicy.plan(base.copy(batteryPct = 15)).intervalMs)
        assertEquals(20_000, TrackingPolicy.plan(base.copy(powerSaveMode = true)).intervalMs)
        assertEquals(Accuracy.LOW_POWER, TrackingPolicy.plan(base.copy(batteryPct = 5)).accuracy)
        assertEquals(10_000, TrackingPolicy.plan(base.copy(batteryPct = 5, charging = true)).intervalMs)
    }

    @Test
    fun `offline keeps sampling and holds uploads`() {
        val p = TrackingPolicy.plan(base.copy(online = false))
        assertTrue(p.active)
        assertEquals(Long.MAX_VALUE, p.uploadBatchMs)
    }

    @Test
    fun `movement detection ignores jitter inside the accuracy circle`() {
        val t = 1_000_000L
        val still = listOf(Fix(-6.2, 106.8, 15f, t - 120_000, 0f), Fix(-6.20005, 106.8, 15f, t, 0.1f))
        assertFalse(MovementDetector.isMoving(still))
        val walked = listOf(Fix(-6.2, 106.8, 10f, t - 120_000, null), Fix(-6.2009, 106.8, 10f, t, null))
        assertTrue(MovementDetector.isMoving(walked))
    }
}
