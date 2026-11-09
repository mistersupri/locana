package app.locana.domain

import kotlin.math.roundToInt

object Formatting {
    fun distance(m: Int): String = if (m < 1000) "$m m" else "${((m / 100.0).roundToInt() / 10.0)} km"

    fun progress(value: Int, target: Int, unit: String): String = when (unit) {
        "meters" -> "${distance(value)} of ${distance(target)}"
        else -> "$value of $target $unit"
    }

    fun initials(name: String): String {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when {
            parts.isEmpty() -> "?"
            parts.size == 1 -> parts[0].take(2).uppercase()
            else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
        }
    }

    /** "42 mins", "3 hrs, 52 mins", "2 days, 4 hrs": how long someone has been in one place or on a trip. */
    fun duration(ms: Long): String {
        val minutes = (ms / 60_000).coerceAtLeast(0)
        fun unit(n: Long, one: String, many: String) = if (n == 1L) "$n $one" else "$n $many"
        return when {
            minutes < 1 -> "under a minute"
            minutes < 60 -> unit(minutes, "min", "mins")
            minutes < 24 * 60 -> {
                val h = minutes / 60
                val m = minutes % 60
                if (m == 0L) unit(h, "hr", "hrs") else "${unit(h, "hr", "hrs")}, ${unit(m, "min", "mins")}"
            }
            else -> {
                val d = minutes / (24 * 60)
                val h = (minutes % (24 * 60)) / 60
                if (h == 0L) unit(d, "day", "days") else "${unit(d, "day", "days")}, ${unit(h, "hr", "hrs")}"
            }
        }
    }

    fun speedKmh(mps: Double): String = "${(mps * 3.6).roundToInt()} km/h"
}
