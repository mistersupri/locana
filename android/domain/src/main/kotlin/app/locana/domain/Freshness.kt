package app.locana.domain

/** How a member's position should be presented. Never treat a cached position as live. */
enum class Presence { LIVE, STALE, UNAVAILABLE, HIDDEN }

object Freshness {
    const val STALE_AFTER_MS = 10 * 60_000L

    /** The server status can age on the device: a LIVE fix becomes STALE as time passes. */
    fun presence(serverStatus: Presence, recordedAtMs: Long?, nowMs: Long): Presence = when {
        serverStatus == Presence.HIDDEN || serverStatus == Presence.UNAVAILABLE -> serverStatus
        recordedAtMs == null -> Presence.UNAVAILABLE
        nowMs - recordedAtMs > STALE_AFTER_MS -> Presence.STALE
        else -> Presence.LIVE
    }

    /** Short, honest age label: "just now", "4 min ago", "2 h ago", "3 d ago". */
    fun ageLabel(recordedAtMs: Long, nowMs: Long): String {
        val s = ((nowMs - recordedAtMs) / 1000).coerceAtLeast(0)
        return when {
            s < 45 -> "just now"
            s < 3600 -> "${(s + 30) / 60} min ago"
            s < 86_400 -> "${s / 3600} h ago"
            else -> "${s / 86_400} d ago"
        }
    }
}
