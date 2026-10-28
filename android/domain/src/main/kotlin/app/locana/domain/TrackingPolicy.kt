package app.locana.domain

/**
 * Decides how often to ask for a location and how eagerly to upload it.
 *
 * Nothing here guarantees "real time": Android batches, delays and drops background updates
 * (Doze, App Standby buckets, OEM battery managers). The plan is a request, and the UI shows
 * the actual age of every position instead of promising freshness.
 */
data class TrackingInputs(
    val sharingActive: Boolean,
    val explorationEnabled: Boolean,
    val appInForeground: Boolean,
    val moving: Boolean,
    val batteryPct: Int?,
    val charging: Boolean,
    val powerSaveMode: Boolean,
    val online: Boolean,
)

enum class Accuracy { HIGH, BALANCED, LOW_POWER }

data class TrackingPlan(
    val active: Boolean,
    val intervalMs: Long,
    val minIntervalMs: Long,
    val minDistanceM: Float,
    val accuracy: Accuracy,
    /** how long samples may wait before an upload; 0 = upload each one */
    val uploadBatchMs: Long,
    val reason: String,
) {
    companion object {
        val OFF = TrackingPlan(false, 0, 0, 0f, Accuracy.LOW_POWER, 0, "sharing and exploration are off")
    }
}

data class TrackingConfig(
    val foregroundMovingMs: Long = 10_000,
    val backgroundMovingMs: Long = 30_000,
    val stationaryMs: Long = 120_000,
    val lowBatteryMs: Long = 300_000,
    val lowBatteryPct: Int = 20,
    val criticalBatteryPct: Int = 10,
)

object TrackingPolicy {
    fun plan(i: TrackingInputs, c: TrackingConfig = TrackingConfig()): TrackingPlan {
        if (!i.sharingActive && !i.explorationEnabled) return TrackingPlan.OFF

        val battery = i.batteryPct
        val critical = !i.charging && battery != null && battery <= c.criticalBatteryPct
        val low = !i.charging && (i.powerSaveMode || (battery != null && battery <= c.lowBatteryPct))

        var plan = when {
            critical -> TrackingPlan(true, c.lowBatteryMs, c.lowBatteryMs / 2, 100f, Accuracy.LOW_POWER, 600_000, "battery critical")
            !i.moving -> TrackingPlan(true, c.stationaryMs, c.stationaryMs / 2, 50f, Accuracy.BALANCED, 300_000, "stationary")
            i.appInForeground -> TrackingPlan(true, c.foregroundMovingMs, c.foregroundMovingMs / 2, 10f, Accuracy.HIGH, 0, "moving, app open")
            else -> TrackingPlan(
                true,
                c.backgroundMovingMs,
                c.backgroundMovingMs / 2,
                25f,
                // Exploration rewards need fixes under 50 m, which in practice means GPS.
                if (i.explorationEnabled) Accuracy.HIGH else Accuracy.BALANCED,
                60_000,
                "moving, background",
            )
        }
        if (low && !critical) {
            plan = plan.copy(
                intervalMs = plan.intervalMs * 2,
                minIntervalMs = plan.minIntervalMs * 2,
                accuracy = if (plan.accuracy == Accuracy.HIGH) Accuracy.BALANCED else plan.accuracy,
                uploadBatchMs = maxOf(plan.uploadBatchMs, 120_000),
                reason = plan.reason + ", battery saver",
            )
        }
        // Offline: keep sampling but hold everything in the local queue.
        if (!i.online) plan = plan.copy(uploadBatchMs = Long.MAX_VALUE, reason = plan.reason + ", offline")
        return plan
    }
}

/** A sample just large enough for movement detection. */
data class Fix(val lat: Double, val lon: Double, val accuracyM: Float, val timeMs: Long, val speedMps: Float?)

object MovementDetector {
    /**
     * Moving when the reported speed is clearly above walking-still noise, or when the person
     * has left the uncertainty circle of a fix from the last few minutes.
     */
    fun isMoving(recent: List<Fix>, windowMs: Long = 180_000): Boolean {
        val last = recent.maxByOrNull { it.timeMs } ?: return true
        if ((last.speedMps ?: 0f) > 0.7f) return true
        val window = recent.filter { last.timeMs - it.timeMs in 1..windowMs }
        if (window.isEmpty()) return true
        return window.any {
            val d = Geo.distanceM(LatLon(it.lat, it.lon), LatLon(last.lat, last.lon))
            d > maxOf(30f, it.accuracyM + last.accuracyM)
        }
    }
}
