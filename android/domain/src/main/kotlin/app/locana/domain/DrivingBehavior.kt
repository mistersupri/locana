package app.locana.domain

enum class DriveEventKind { HARD_BRAKE, RAPID_ACCEL, PHONE_USE }

/**
 * Notices risky moments while someone is driving, from the speed between consecutive fixes (a few seconds apart)
 * and from whether the phone is in use. Pure, so it is tested without a phone.
 *
 * Braking and acceleration are measured as the change in speed per second; the thresholds sit around what a
 * careful driver rarely exceeds (a little over a third of a g to brake, 0.3 g to accelerate). Phone use needs
 * the screen on and unlocked for a stretch while moving, so a glance at a notification does not count. A
 * passenger holding the phone looks the same: the label says "phone use", not "distracted driver".
 */
class DrivingBehavior(
    private val hardBrakeMps2: Double = 3.5,
    private val rapidAccelMps2: Double = 3.0,
    private val phoneUseAfterMs: Long = 12_000,
    private val quietMs: Long = 10_000,
    private val phoneQuietMs: Long = 60_000,
) {
    private var prevTimeMs = -1L
    private var prevSpeed = 0f
    private var phoneSinceMs = -1L
    private val lastAt = mutableMapOf<DriveEventKind, Long>()

    fun reset() { prevTimeMs = -1; phoneSinceMs = -1 }

    fun onFix(timeMs: Long, speedMps: Float?, accuracyM: Float, inVehicle: Boolean, phoneInUse: Boolean): DriveEventKind? {
        if (!inVehicle || speedMps == null || accuracyM > 40f) { reset(); return null }
        var found: DriveEventKind? = null
        if (prevTimeMs >= 0) {
            val dt = (timeMs - prevTimeMs) / 1000.0
            if (dt in 0.5..6.0) {
                val a = (speedMps - prevSpeed) / dt
                if (a <= -hardBrakeMps2 && prevSpeed >= 6f) found = DriveEventKind.HARD_BRAKE
                else if (a >= rapidAccelMps2 && speedMps >= 5f) found = DriveEventKind.RAPID_ACCEL
            } else if (dt > 6.0) phoneSinceMs = -1
        }
        prevTimeMs = timeMs
        prevSpeed = speedMps

        if (found == null) {
            if (phoneInUse && speedMps >= 5f) {
                if (phoneSinceMs < 0) phoneSinceMs = timeMs
                else if (timeMs - phoneSinceMs >= phoneUseAfterMs) found = DriveEventKind.PHONE_USE
            } else phoneSinceMs = -1
        }
        val kind = found ?: return null
        val gap = if (kind == DriveEventKind.PHONE_USE) phoneQuietMs else quietMs
        if (timeMs - (lastAt[kind] ?: Long.MIN_VALUE / 2) < gap) return null
        lastAt[kind] = timeMs
        return kind
    }
}
