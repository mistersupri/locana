package app.locana.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FreshnessAndValidationTest {
    @Test
    fun `a live fix turns stale as it ages on the device`() {
        val now = 10_000_000L
        assertEquals(Presence.LIVE, Freshness.presence(Presence.LIVE, now - 60_000, now))
        assertEquals(Presence.STALE, Freshness.presence(Presence.LIVE, now - 11 * 60_000, now))
        assertEquals(Presence.HIDDEN, Freshness.presence(Presence.HIDDEN, now, now))
        assertEquals(Presence.UNAVAILABLE, Freshness.presence(Presence.LIVE, null, now))
    }

    @Test
    fun `age labels are short and honest`() {
        val now = 10_000_000L
        assertEquals("just now", Freshness.ageLabel(now - 10_000, now))
        assertEquals("4 min ago", Freshness.ageLabel(now - 4 * 60_000, now))
        assertEquals("2 h ago", Freshness.ageLabel(now - 2 * 3_600_000, now))
    }

    @Test
    fun `form rules mirror the backend`() {
        assertNull(Validation.email("rani@example.com"))
        assertNotNull(Validation.email("rani@"))
        assertNotNull(Validation.password("short"))
        assertNull(Validation.username("rani.walks"))
        assertNotNull(Validation.username("Rani Walks"))
    }

    @Test
    fun `invite codes are normalized from loose typing`() {
        assertEquals("K7QH-M3XP-2R", Validation.normalizeInviteCode("k7qh m3xp 2r"))
        assertNull(Validation.normalizeInviteCode("K7QH"))
    }

    @Test
    fun `formatting helpers`() {
        assertEquals("850 m", Formatting.distance(850))
        assertEquals("1.5 km", Formatting.distance(1500))
        assertEquals("RA", Formatting.initials("Rani"))
        assertEquals("RA", Formatting.initials("Rani  Ayu"))
    }
}
