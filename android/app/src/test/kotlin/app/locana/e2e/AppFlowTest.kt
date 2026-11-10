package app.locana.e2e

import android.Manifest
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import app.locana.MainActivity
import app.locana.data.PlainCipher
import app.locana.data.auth.TokenCipher
import app.locana.di.EnvironmentModule
import dagger.Module
import dagger.Provides
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import javax.inject.Named
import javax.inject.Singleton

private val API: String? = System.getenv("LOCANA_E2E_API")

/** Points the real app at a locally running backend and avoids the Keystore (absent in Robolectric). */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [EnvironmentModule::class])
object TestEnvironmentModule {
    @Provides @Named("baseUrl") fun baseUrl(): String = API ?: "http://127.0.0.1:3000/"
    @Provides @Named("wsUrl") fun wsUrl(@Named("baseUrl") b: String): String = b.replaceFirst("http://", "ws://") + "ws"
    @Provides @Singleton fun cipher(): TokenCipher = PlainCipher()
}

/**
 * Click-through of the whole app (real Activity, Hilt graph, ViewModels, repositories,
 * Room, DataStore, OkHttp and WebSocket) against a real backend. Skipped unless
 * LOCANA_E2E_API is set, e.g. LOCANA_E2E_API=http://127.0.0.1:3000/.
 * Screenshots go to LOCANA_SHOTS_DIR (or build/shots).
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppFlowTest {
    private val hilt = HiltAndroidRule(this)
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(hilt).around(compose)

    private val shots = File(System.getenv("LOCANA_SHOTS_DIR") ?: "build/shots").apply { mkdirs() }
    private val user = "rani" + System.currentTimeMillis() % 1_000_000
    private val password = "correct horse battery"

    @Before
    fun setUp() {
        assumeTrue("set LOCANA_E2E_API to run the full-app flow", API != null)
        WorkManagerTestInitHelper.initializeTestWorkManager(ApplicationProvider.getApplicationContext())
        hilt.inject()
    }

    private fun waitFor(text: String, substring: Boolean = false, ms: Long = 15_000) {
        compose.waitUntil(ms) { compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun node(text: String, substring: Boolean = false): SemanticsNodeInteraction =
        compose.onAllNodesWithText(text, substring = substring)[0]

    /** Lazy lists only compose what is near the screen: scroll every scrollable until the text is composed. */
    private fun scrollTo(text: String, substring: Boolean) {
        if (compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()) return
        val count = compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().size
        for (i in 0 until count) {
            runCatching { compose.onAllNodes(hasScrollAction())[i].performScrollToNode(hasText(text, substring = substring)) }
            if (compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()) return
        }
    }

    private fun click(text: String, substring: Boolean = false) {
        scrollTo(text, substring)
        waitFor(text, substring)
        compose.onNode(hasText(text, substring = substring) and hasClickAction()).scrollIfNeeded().performClick()
        compose.waitForIdle()
    }

    /** Not every screen scrolls; only scroll when the node sits in a scrollable parent. */
    private fun SemanticsNodeInteraction.scrollIfNeeded(): SemanticsNodeInteraction = apply { runCatching { performScrollTo() } }

    private fun type(label: String, value: String) {
        compose.onNode((hasText(label) or hasContentDescription(label)) and hasSetTextAction()).scrollIfNeeded().performTextInput(value)
    }

    private fun tab(name: String) {
        // Bottom bar items are the only clickable nodes whose whole text is the tab name.
        compose.onAllNodes(hasText(name) and hasClickAction()).fetchSemanticsNodes().let { check(it.isNotEmpty()) { "no tab $name" } }
        compose.onAllNodes(hasText(name) and hasClickAction()).let { it[it.fetchSemanticsNodes().size - 1] }.performClick()
        compose.waitForIdle()
    }

    /** Controls without text (the gear) are found by their content description. */
    private fun clickDescription(label: String) {
        compose.waitUntil(15_000) { compose.onAllNodes(hasContentDescription(label) and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasContentDescription(label) and hasClickAction())[0].performClick()
        compose.waitForIdle()
    }

    /** Opens a screen from a button or row, lets it load, takes its picture and returns. */
    private fun visit(label: String, shotName: String) {
        click(label)
        compose.waitForIdle()
        Thread.sleep(700)
        compose.waitForIdle()
        shot(shotName)
        back()
    }

    private fun back() {
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    /** Draws the real view tree into a bitmap (Robolectric native graphics renders Compose). */
    private fun isDark(a: android.app.Activity): Boolean {
        val v = a.window.decorView
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        val px = bmp.getPixel(v.width / 2, v.height / 2)
        return android.graphics.Color.luminance(px) < 0.2f
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        compose.activityRule.scenario.onActivity { a ->
            val v = a.window.decorView
            val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            v.draw(Canvas(bmp))
            File(shots, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test
    fun fullFlow() {
        try {
            flow()
        } catch (e: Throwable) {
            runCatching { shot("zz-failure") }
            throw e
        }
    }

    private fun flow() {
        // Onboarding and registration
        waitFor("You stay in control")
        shot("01-onboarding")
        click("Create an account")
        waitFor("Create your account")
        click("Create account")
        waitFor("Use 1-40 characters")
        shot("02-register-validation")
        type("Your name", "Rani")
        type("Username", user)
        type("Email", "$user@example.test")
        type("Password (10+ characters)", password)
        click("Create account")

        // Profile setup and permission education (every permission declined)
        waitFor("How should your circle see you?")
        shot("03-profile-setup")
        click("Continue")
        waitFor("Location for your circles")
        shot("04-permission-location")
        click("Not now")
        waitFor("Notifications for what matters")
        click("Not now")
        waitFor("Optional: tell driving from walking")
        shot("04b-permission-motion")
        click("Not now")
        waitFor("You're set", substring = true)
        shot("05-permission-summary")
        click("Go to the map")

        // Map without circles, create a circle, invitation code
        waitFor("Start with a circle")
        shot("06-map-no-circle")
        click("Create a circle")
        waitFor("New circle")
        type("Circle name", "Family")
        click("Create circle")
        waitFor("Make a code")
        click("Create code")
        waitFor("NEW CODE")
        shot("07-invite-code")
        back()

        // Map with a circle: belonging to it means sharing, so there is no Share button, only Invite
        waitFor("1 member", substring = true)
        shot("08-map-in-circle")
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        tab("Explore")
        tab("Map")
        waitFor("Invite")
        waitFor("Rani", substring = true)
        shot("09-map-members")

        // Focus a person from the drawer, then Back must unfocus (not leave the app).
        click("Rani", substring = true)
        waitFor("Nothing recorded", substring = true)
        shot("09a-map-focused")
        back()
        waitFor("Invite")
        compose.activityRule.scenario.onActivity { check(!it.isFinishing) { "Back left the app instead of unfocusing" } }
        check(compose.onAllNodesWithText("Nothing recorded", substring = true).fetchSemanticsNodes().isEmpty()) { "still focused after Back" }

        // Explore: quests, quest detail, landmarks
        tab("Explore")
        waitFor("Today")
        shot("11-explore")
        click("Fresh ground")
        waitFor("Progress counts only", substring = true)
        shot("12-quest-detail")
        back()
        click("Landmarks")
        waitFor("Monumen Nasional")
        // No GPS fix exists in this test, so no distance may be shown.
        waitFor("distances are hidden", substring = true)
        check(compose.onAllNodesWithText(" away", substring = true).fetchSemanticsNodes().isEmpty()) { "distance shown without a fix" }
        shot("13-landmarks")
        back()
        visit("Explored map", "13a-explored-map")
        visit("History", "13b-quest-history")

        // Play: level, achievements, collection, leaderboard
        tab("Play")
        waitFor("to level 2", substring = true)
        shot("14-play")
        click("Achievements")
        waitFor("First Discovery")
        shot("15-achievements")
        back()
        click("Collection")
        waitFor("Avatar frames", substring = true)
        shot("16-collection")
        back()
        click("Friends leaderboard")
        waitFor("#1")
        shot("17-leaderboard")
        back()
        visit("Circle leaderboard", "17a-circle-leaderboard")
        visit("Level path", "17b-level-path")
        visit("Monster hunt", "17c-monster-hunt")
        visit("Weapon shop", "17d-weapon-shop")

        // Circle: feed and members
        tab("Circle")
        waitFor("Rani joined the circle")
        shot("18-circle")
        click("Members")
        waitFor("@$user", substring = true)
        shot("18a-members")
        back()
        visit("Places", "18b-places")

        // Profile (the map's gear): photo, privacy, theme, sign out, sign back in. Location sharing is automatic, so no sharing page.
        tab("Map")
        clickDescription("Profile and settings")
        waitFor("Add photo")
        check(compose.onAllNodesWithText("Location sharing").fetchSemanticsNodes().isEmpty()) { "profile still offers a sharing page" }
        shot("19-profile")
        visit("Edit profile", "19a-edit-profile")
        visit("Safety", "19f-safety")
        visit("Notifications", "19b-notifications")
        visit("Account and security", "19c-account")
        visit("Blocked people", "19d-blocked")
        visit("Permissions", "19e-permissions")
        click("Privacy")
        waitFor("Keep my location history")
        click("Keep my location history")
        waitFor("7 days")
        shot("21-privacy")
        back()
        click("Dark")
        // Theme is stored in DataStore and applied on the next emission; wait for the dark surface.
        compose.waitUntil(5_000) { compose.activityRule.scenario.let { sc -> var dark = false; sc.onActivity { a -> dark = isDark(a) }; dark } }
        shot("22-profile-dark")
        back()
        tab("Explore")
        waitFor("Today")
        shot("23-explore-dark")
        // Dark tour: the screens with the most coloured tiles and text.
        visit("Fresh ground", "23a-quest-dark")
        visit("Landmarks", "23b-landmarks-dark")
        tab("Play")
        waitFor("to level 2", substring = true)
        shot("23c-play-dark")
        visit("Achievements", "23d-achievements-dark")
        visit("Collection", "23e-collection-dark")
        tab("Circle")
        waitFor("Rani joined the circle")
        shot("23f-circle-dark")
        tab("Map")
        waitFor("Invite")
        shot("23g-map-dark")
        tab("Map")
        clickDescription("Profile and settings")
        click("Light")
        click("Sign out")
        waitFor("Sign out?")
        node("Sign out", substring = false)
        compose.onAllNodes(hasText("Sign out") and hasClickAction()).let { it[it.fetchSemanticsNodes().size - 1] }.performClick()
        waitFor("Sign in to see your circles.")
        shot("24-login")
        type("Email or username", user)
        type("Password", password)
        click("Sign in")
        waitFor("Family")
    }
}
