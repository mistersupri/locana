package app.locana.ui.timeline

import app.locana.data.api.TimelineSegmentDto
import app.locana.domain.Formatting
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** A timeline entry ready to draw. Built from the server's stays and trips. */
sealed interface TimelineItem {
    val range: String
    val duration: String
    /** The newest entry of today, still going on. */
    val ongoing: Boolean

    data class Stay(
        val title: String,
        override val range: String,
        override val duration: String,
        val emoji: String,
        val latitude: Double?,
        val longitude: Double?,
        override val ongoing: Boolean = false,
    ) : TimelineItem

    data class Trip(
        val title: String,
        override val range: String,
        override val duration: String,
        val distance: String,
        val avgSpeed: String,
        val maxSpeed: String,
        val driving: Boolean,
        val emoji: String,
        val path: List<Pair<Double, Double>>,
        override val ongoing: Boolean = false,
    ) : TimelineItem
}

private val clock = DateTimeFormatter.ofPattern("HH:mm")

private fun placeEmoji(type: String?) = when (type) {
    "HOME" -> "🏠"
    "WORK" -> "💼"
    "SCHOOL" -> "🎒"
    "CUSTOM" -> "📍"
    else -> "🏡"
}

/** Turns segments into cards. A trip is named after the saved places on either side when there are any. */
fun buildItems(
    segments: List<TimelineSegmentDto>,
    zone: ZoneId = ZoneId.systemDefault(),
    nowMs: Long = System.currentTimeMillis(),
    today: Boolean = false,
): List<TimelineItem> {
    fun time(iso: String) = clock.format(Instant.parse(iso).atZone(zone))
    return segments.mapIndexed { i, s ->
        val range = "${time(s.startedAt)} – ${time(s.endedAt)}"
        val duration = Formatting.duration(s.durationSec * 1000L)
        // Fixes arrive every couple of minutes, so "still going" means the last one is recent.
        val ongoing = today && i == segments.lastIndex && nowMs - Instant.parse(s.endedAt).toEpochMilli() <= 15 * 60_000
        if (s.type == "STAY") {
            TimelineItem.Stay(
                title = s.place?.name ?: "Stayed in one place",
                range = range,
                duration = duration,
                emoji = placeEmoji(s.place?.type),
                latitude = s.latitude,
                longitude = s.longitude,
                ongoing = ongoing,
            )
        } else {
            val from = segments.getOrNull(i - 1)?.takeIf { it.type == "STAY" }?.place?.name
            val to = segments.getOrNull(i + 1)?.takeIf { it.type == "STAY" }?.place?.name
            val driving = s.mode == "DRIVE"
            val cycling = s.mode == "CYCLE"
            TimelineItem.Trip(
                title = when {
                    from != null && to != null -> "$from → $to"
                    to != null -> "On the way to $to"
                    from != null -> "Left $from"
                    else -> if (driving) "Drive" else if (cycling) "Ride" else "Walk"
                },
                range = range,
                duration = duration,
                distance = Formatting.distance(s.distanceM ?: 0),
                avgSpeed = "${Math.round(s.avgSpeedKmh ?: 0.0)} km/h",
                maxSpeed = "${s.maxSpeedKmh ?: 0} km/h",
                driving = driving || cycling,
                emoji = if (driving) "🚗" else if (cycling) "🚲" else "🚶",
                path = s.path.mapNotNull { if (it.size >= 2) it[0] to it[1] else null },
                ongoing = ongoing,
            )
        }
    }
}
