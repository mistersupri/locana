package app.locana.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.locana.ui.nav.Routes
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RoutesTest {
    @Test
    fun `route builders fill the patterns`() {
        assertEquals("circle/abc/invite", Routes.circleInvite("abc"))
        assertEquals("circle/abc/place?placeId=", Routes.placeEdit("abc"))
        assertEquals("circle/join?code=K7QH-M3XP-2R", Routes.circleJoin("K7QH-M3XP-2R"))
        assertEquals("play/leaderboard?scope=circle", Routes.leaderboard("circle"))
        assertEquals(5, Routes.tabs.size)
    }
}
