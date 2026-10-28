package app.locana.domain

import kotlin.random.Random

/**
 * Exponential backoff with "equal jitter": half the window is fixed, half is random, so retries
 * spread out without ever collapsing to an immediate retry.
 */
class Backoff(
    private val baseMs: Long = 1_000,
    private val maxMs: Long = 60_000,
    private val random: Random = Random.Default,
) {
    var attempt = 0
        private set

    fun nextDelayMs(): Long {
        val cap = minOf(maxMs, baseMs shl minOf(attempt, 16))
        attempt++
        val half = cap / 2
        return half + random.nextLong(half + 1)
    }

    fun reset() {
        attempt = 0
    }
}
