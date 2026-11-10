package app.locana.domain

import kotlin.math.sqrt

/**
 * Decides whether an accelerometer reading looks like a crash. Pure, so it can be tested without a phone.
 *
 * A reading counts only when the person is in a vehicle (the phone's activity guess, or a recent GPS speed
 * of a vehicle): a dropped phone or a jolt while walking reaches high g too, and must not frighten anyone.
 * After one alert there is a quiet period, so a single impact (which shakes for a second or two) asks once.
 */
class CrashDetector(
    private val thresholdG: Double = 5.0,
    private val quietMs: Long = 2 * 60_000,
) {
    private var lastAlertMs = Long.MIN_VALUE / 2

    /** Acceleration in m/s² per axis, gravity included (what the accelerometer sensor reports). */
    fun onSample(x: Float, y: Float, z: Float, nowMs: Long, inVehicle: Boolean): Boolean {
        if (!inVehicle) return false
        val g = sqrt((x * x + y * y + z * z).toDouble()) / GRAVITY
        if (g < thresholdG) return false
        if (nowMs - lastAlertMs < quietMs) return false
        lastAlertMs = nowMs
        return true
    }

    companion object {
        const val GRAVITY = 9.80665

        /** In a vehicle when the phone says so, or a recent fix was fast enough that only a vehicle explains it. */
        fun inVehicle(activity: String?, lastSpeedMps: Float?, lastFixAgeMs: Long): Boolean =
            activity == "vehicle" || (lastSpeedMps != null && lastSpeedMps >= 6f && lastFixAgeMs <= 60_000)
    }
}
