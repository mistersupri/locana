package app.locana.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.locana.data.PhotoPrep
import app.locana.data.api.PlaceDto
import app.locana.domain.Presence
import app.locana.ui.components.UiState
import app.locana.ui.map.FocusUi
import app.locana.ui.map.MemberDetail
import app.locana.ui.map.MemberPin
import app.locana.ui.map.statusFor
import app.locana.ui.theme.LocanaTheme
import app.locana.ui.timeline.TimelineItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class MemberDetailTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val now = 1_800_000_000_000L
    private fun pin(movement: String?, since: Long? = null, speed: Double? = null, presence: Presence = Presence.LIVE, isMe: Boolean = false, lat: Double? = -6.2) = MemberPin(
        userId = if (isMe) "me" else "u", name = "Lina", avatarKey = null, framed = false, presence = presence, lat = lat, lon = 106.8, accuracyM = 8.0,
        recordedAtMs = now - 60_000, batteryPct = 65, speedMps = speed, movement = movement, stationarySinceMs = since, isMe = isMe, hiddenFromThem = false, blockedByMe = false,
    )

    @Test
    fun `status says where and since when`() {
        val s = statusFor(pin("STAYING", now - 54 * 60_000L), null, null, now, ZoneOffset.UTC)
        assertEquals("At this place", s.title)
        assertTrue(s.subtitle.contains("Here for 54 mins"))
        assertTrue(s.subtitle.startsWith("Since "))
    }

    @Test
    fun `status shows the distance from you`() {
        val me = pin(null, isMe = true, lat = -6.2)
        val s = statusFor(pin("MOVING", speed = 9.0).copy(lat = -6.3), null, me, now)
        assertEquals("Driving", s.title)
        assertTrue(s.subtitle.contains("km from you"))
    }

    @Test
    fun `hidden people are not described`() {
        assertEquals("Not sharing with you", statusFor(pin(null, presence = Presence.HIDDEN), null, null, now).title)
    }

    @Test
    fun `detail panel shows the person, battery, status and their day`() {
        val place = PlaceDto("p", "c", "Lina Home", "HOME", -6.2, 106.8, 100, true, true, createdById = "u", updatedAt = "2026-11-01T00:00:00Z")
        val items = listOf(
            TimelineItem.Stay("Lina Home", "05:31 – 12:46", "7 hrs, 15 mins", "🏠", -6.2, 106.8),
            TimelineItem.Trip("Lina Home → Office", "13:00 – 13:25", "25 mins", "9.8 km", "24 km/h", "58 km/h", true, "🚗", emptyList()),
        )
        rule.setContent {
            LocanaTheme {
                MemberDetail(
                    m = pin("STAYING", now - 54 * 60_000L), me = null, place = place,
                    focus = FocusUi("u", LocalDate.now(), UiState.Data(items)),
                    onPrevDay = {}, onNextDay = {}, onRetry = {}, onHide = {}, onManage = {},
                )
            }
        }
        rule.onNodeWithText("Lina").assertIsDisplayed()
        rule.onNodeWithText("65%").assertIsDisplayed()
        rule.onNodeWithText("At Lina Home").assertIsDisplayed()
        rule.onNodeWithText("Today").assertIsDisplayed()
        rule.onNodeWithText("Stayed 7 hrs, 15 mins").assertIsDisplayed()
        val dir = java.io.File(System.getenv("LOCANA_SHOTS_DIR") ?: "build/shots").apply { mkdirs() }
        rule.waitForIdle()
        rule.activityRule.scenario.onActivity { a: androidx.activity.ComponentActivity ->
            val v = a.window.decorView
            val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            v.draw(android.graphics.Canvas(b))
            java.io.File(dir, "member-detail.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test
    fun `photos are cropped to a square before upload`() {
        val wide = Bitmap.createBitmap(800, 400, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val sq = PhotoPrep.square(wide)
        assertEquals(PhotoPrep.SIZE, sq.width)
        assertEquals(PhotoPrep.SIZE, sq.height)
    }
}
