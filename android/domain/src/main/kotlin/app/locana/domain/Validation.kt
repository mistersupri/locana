package app.locana.domain

/** Form rules mirrored from the backend DTOs so users see problems before a round trip. */
object Validation {
    private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$")
    private val USERNAME = Regex("^[a-z0-9_.]{3,24}$")

    fun email(v: String): String? = if (EMAIL.matches(v.trim())) null else "Enter an email like name@example.com"

    fun password(v: String): String? = when {
        v.length < 10 -> "Use at least 10 characters"
        v.length > 128 -> "Use at most 128 characters"
        else -> null
    }

    fun username(v: String): String? =
        if (USERNAME.matches(v.trim().lowercase())) null else "3-24 characters: lowercase letters, digits, _ or ."

    fun displayName(v: String): String? = if (v.trim().length in 1..40) null else "Use 1-40 characters"

    fun circleName(v: String): String? = if (v.trim().length in 1..40) null else "Use 1-40 characters"

    /** Accepts codes typed with spaces, dashes or lowercase; returns the canonical XXXX-XXXX-XX form. */
    fun normalizeInviteCode(v: String): String? {
        val raw = v.uppercase().filter { it.isLetterOrDigit() }
        if (raw.length != 10) return null
        return "${raw.substring(0, 4)}-${raw.substring(4, 8)}-${raw.substring(8)}"
    }
}
