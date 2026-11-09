package app.locana.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.locana.data.api.TimelinePlaceDto
import app.locana.data.api.TimelineSegmentDto
import app.locana.domain.Presence
import app.locana.ui.map.MemberPin
import app.locana.ui.map.PinSpec
import app.locana.ui.map.captionFor
import app.locana.ui.map.renderDot
import app.locana.ui.map.renderHeadingCone
import app.locana.ui.map.renderPin
import app.locana.ui.timeline.TimelineItem
import app.locana.ui.timeline.buildItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapMarkersTest {
    private val now = 1_800_000_000_000L
    private fun pin(
        movement: String? = null,
        since: Long? = null,
        speed: Double? = null,
        presence: Presence = Presence.LIVE,
        recordedAt: Long? = now,
    ) = MemberPin(
        userId = "u", name = "Ahmad Supriyanto", avatarKey = null, framed = false, presence = presence, lat = -6.2, lon = 106.8, accuracyM = 10.0,
        recordedAtMs = recordedAt, batteryPct = 80, speedMps = speed, movement = movement, stationarySinceMs = since, isMe = false, hiddenFromThem = false, blockedByMe = false,
    )

    @Test
    fun `staying says how long, with a house badge`() {
        val c = captionFor(pin("STAYING", now - (3 * 60 + 52) * 60_000L), null, now)
        assertEquals("Here for", c.label)
        assertEquals("3 hrs, 52 mins", c.value)
        assertNotNull(c.emoji)
    }

    @Test
    fun `moving shows speed and the right vehicle`() {
        val walk = captionFor(pin("MOVING", speed = 1.6), null, now)
        assertEquals("Walking", walk.label)
        assertEquals("6 km/h", walk.value)
        val drive = captionFor(pin("MOVING", speed = 12.0), null, now)
        assertEquals("Driving", drive.label)
        assertEquals("43 km/h", drive.value)
        assertTrue(walk.emoji != drive.emoji)
    }

    @Test
    fun `a car crawling in traffic is still driving`() {
        val c = captionFor(pin("MOVING", speed = 0.5).copy(mode = "DRIVING"), null, now)
        assertEquals("Driving", c.label)
        assertEquals("2 km/h", c.value)
        assertEquals("Cycling", captionFor(pin("MOVING", speed = 4.0).copy(mode = "CYCLING"), null, now).label)
        // The mode wins over the speed: a fast reading with the mode walking is still walking.
        assertEquals("Walking", captionFor(pin("MOVING", speed = 9.0).copy(mode = "WALKING"), null, now).label)
    }

    @Test
    fun `activity recognition types map to what the server accepts`() {
        assertEquals("vehicle", app.locana.data.system.ActivityMonitor.kindOf(com.google.android.gms.location.DetectedActivity.IN_VEHICLE))
        assertEquals("cycling", app.locana.data.system.ActivityMonitor.kindOf(com.google.android.gms.location.DetectedActivity.ON_BICYCLE))
        assertEquals("walking", app.locana.data.system.ActivityMonitor.kindOf(com.google.android.gms.location.DetectedActivity.ON_FOOT))
        assertEquals("still", app.locana.data.system.ActivityMonitor.kindOf(com.google.android.gms.location.DetectedActivity.STILL))
        assertEquals("unknown", app.locana.data.system.ActivityMonitor.kindOf(com.google.android.gms.location.DetectedActivity.TILTING))
    }

    @Test
    fun `a speed reading alone is not walking`() {
        // The phone is lying still but reported 10 km/h once: nothing agreed it was moving, so no badge.
        assertNull(captionFor(pin("UNKNOWN", speed = 2.8), null, now).value)
        assertNull(captionFor(pin(null, speed = 2.8), null, now).value)
    }

    @Test
    fun `an old position is labelled with its age, not a stay`() {
        val c = captionFor(pin("STAYING", now - 3_600_000, presence = Presence.STALE, recordedAt = now - 2 * 3_600_000), null, now)
        assertEquals("Last seen", c.label)
        assertEquals("2 h ago", c.value)
        assertNull(c.emoji)
    }

    @Test
    fun `nothing to say while the state is unknown`() {
        assertNull(captionFor(pin("UNKNOWN"), null, now).value)
    }

    private fun spec(bubble: Boolean, stale: Boolean = false) = PinSpec(
        initials = "AS", avatarColor = 0xFF5B2DD0.toInt(), avatarTextColor = Color.WHITE, accent = 0xFF5B2DD0.toInt(), framed = false, frameColor = 0xFFB85C00.toInt(),
        stale = stale, bubbleLabel = if (bubble) "Here for" else null, bubbleValue = if (bubble) "3 hrs, 52 mins" else null, emoji = if (bubble) "🏠" else null,
    )

    @Test
    fun `the anchor is the dot at the bottom of the pin and its pixels are crisp`() {
        val r = renderPin(spec(bubble = true), density = 3f)
        val bmp = r.bitmap
        assertTrue(r.anchorY in 0.6f..0.98f)
        val ay = (r.anchorY * bmp.height).toInt()
        // The dot is opaque violet at the anchor and the bitmap centre line.
        assertEquals(0xFF5B2DD0.toInt(), bmp.getPixel(bmp.width / 2, ay))
        // Fully transparent outside the artwork (corner), fully opaque inside: no soft blur halo around the dot.
        assertEquals(0, Color.alpha(bmp.getPixel(0, bmp.height - 1)))
        save("pin-staying", bmp)
        save("pin-plain", renderPin(spec(bubble = false), 3f).bitmap)
        save("pin-stale", renderPin(spec(bubble = true, stale = true), 3f).bitmap)
    }

    @Test
    fun `heading cone points up from its bottom centre`() {
        val b = renderHeadingCone(0xFF5B2DD0.toInt(), 3f)
        assertTrue(Color.alpha(b.getPixel(b.width / 2, b.height - 4)) > 100)
        assertEquals(0, Color.alpha(b.getPixel(2, b.height - 2)))
        save("cone", b)
        save("dot", renderDot(0xFF5B2DD0.toInt(), 3f, flag = true))
    }

    private fun save(name: String, bmp: Bitmap) {
        val dir = File(System.getenv("LOCANA_SHOTS_DIR") ?: "build/shots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun `timeline cards name trips after the places around them`() {
        fun stay(start: String, end: String, secs: Int, place: TimelinePlaceDto?) = TimelineSegmentDto("STAY", start, end, secs, latitude = -6.2, longitude = 106.8, place = place)
        val items = buildItems(
            listOf(
                stay("2026-11-08T01:00:00Z", "2026-11-08T03:00:00Z", 7200, TimelinePlaceDto("1", "Home", "HOME")),
                TimelineSegmentDto("TRIP", "2026-11-08T03:00:00Z", "2026-11-08T03:25:00Z", 1500, distanceM = 9800, avgSpeedKmh = 23.5, maxSpeedKmh = 58, mode = "DRIVE", path = listOf(listOf(-6.2, 106.8), listOf(-6.25, 106.85))),
                stay("2026-11-08T03:25:00Z", "2026-11-08T08:00:00Z", 16500, TimelinePlaceDto("2", "Office", "WORK")),
                TimelineSegmentDto("TRIP", "2026-11-08T08:00:00Z", "2026-11-08T08:10:00Z", 600, distanceM = 700, avgSpeedKmh = 4.2, maxSpeedKmh = 6, mode = "WALK", path = emptyList()),
            ),
            ZoneOffset.UTC,
        )
        assertTrue(items.none { it.ongoing })
        val home = items[0] as TimelineItem.Stay
        assertEquals("Home", home.title)
        assertEquals("01:00 – 03:00", home.range)
        assertEquals("2 hrs", home.duration)
        val drive = items[1] as TimelineItem.Trip
        assertEquals("Home → Office", drive.title)
        assertEquals("9.8 km", drive.distance)
        assertTrue(drive.driving)
        assertEquals(2, drive.path.size)
        val walk = items[3] as TimelineItem.Trip
        assertEquals("Left Office", walk.title)
        assertEquals("700 m", walk.distance)
    }
}

class TimelineOngoingTest {
    private fun seg(end: String) = TimelineSegmentDto("STAY", "2026-11-08T01:00:00Z", end, 600, latitude = 0.0, longitude = 0.0)

    @Test
    fun `only the newest entry of today that is still recent is happening now`() {
        val end = java.time.Instant.parse("2026-11-08T03:00:00Z")
        val now = end.plusSeconds(5 * 60).toEpochMilli()
        val items = buildItems(listOf(seg("2026-11-08T02:00:00Z"), seg("2026-11-08T03:00:00Z")), ZoneOffset.UTC, now, today = true)
        assertEquals(listOf(false, true), items.map { it.ongoing })
        assertEquals(listOf(false, false), buildItems(listOf(seg("2026-11-08T02:00:00Z"), seg("2026-11-08T03:00:00Z")), ZoneOffset.UTC, now, today = false).map { it.ongoing })
        assertEquals(false, buildItems(listOf(seg("2026-11-08T03:00:00Z")), ZoneOffset.UTC, end.plusSeconds(3600).toEpochMilli(), today = true).single().ongoing)
    }
}
