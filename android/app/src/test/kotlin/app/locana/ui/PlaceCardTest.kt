package app.locana.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.locana.data.places.NearbyKind
import app.locana.data.places.Place
import app.locana.domain.LatLon
import app.locana.ui.map.PlaceCard
import app.locana.ui.map.PoiUi
import app.locana.ui.map.renderEmojiDisc
import app.locana.ui.theme.LocanaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlaceCardTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val place = Place(
        id = "p1", name = "Warung Sate Pak Haji", lat = -6.2, lon = 106.8, type = "Indonesian restaurant", rating = 4.6, ratingCount = 1280,
        address = "Jl. Raya Pasar Minggu No. 12, Jakarta Selatan", openNow = true,
    )

    @Test
    fun `the card shows name, kind, distance, rating, status and address`() {
        var closed = false
        rule.setContent {
            LocanaTheme {
                PlaceCard(PoiUi("p1", "Warung Sate", -6.2, 106.8, place), from = LatLon(-6.21, 106.8), onClose = { closed = true })
            }
        }
        rule.onNodeWithText("Warung Sate Pak Haji").assertIsDisplayed()
        rule.onNodeWithText("Indonesian restaurant · 1.1 km from you").assertIsDisplayed()
        rule.onNodeWithText("4.6").assertIsDisplayed()
        rule.onNodeWithText("(1280)").assertIsDisplayed()
        rule.onNodeWithText("· Open now").assertIsDisplayed()
        rule.onNodeWithText("Jl. Raya Pasar Minggu No. 12, Jakarta Selatan").assertIsDisplayed()
        rule.onNodeWithText("Directions").assertIsDisplayed()
        rule.onNodeWithText("Open in Maps").assertIsDisplayed()
        rule.onNodeWithContentDescription("Close").performClick()
        assertTrue(closed)
    }

    @Test
    fun `a place with only a name still gets a usable card`() {
        rule.setContent { LocanaTheme { PlaceCard(PoiUi("p2", "Gedung Sate", -6.9, 107.6, null, loading = true, error = "Places is not switched on"), from = null, onClose = {}) } }
        rule.onNodeWithText("Gedung Sate").assertIsDisplayed()
        rule.onNodeWithText("Loading details").assertIsDisplayed()
        rule.onNodeWithText("Directions").assertIsDisplayed()
        val dir = java.io.File(System.getenv("LOCANA_SHOTS_DIR") ?: "build/shots").apply { mkdirs() }
        rule.waitForIdle()
        rule.activityRule.scenario.onActivity { a: androidx.activity.ComponentActivity ->
            val v = a.window.decorView
            val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            v.draw(android.graphics.Canvas(b))
            java.io.File(dir, "place-card.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test
    fun `every category has a marker and a Places type list`() {
        NearbyKind.entries.forEach { k ->
            assertTrue(k.types.isNotEmpty())
            val b = renderEmojiDisc(k.emoji, 3f)
            assertEquals(b.width, b.height)
        }
    }
}
