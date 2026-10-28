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
}
