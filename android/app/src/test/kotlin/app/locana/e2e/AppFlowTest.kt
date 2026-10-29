package app.locana.e2e

import android.Manifest
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
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

    private fun click(text: String, substring: Boolean = false) {
        waitFor(text, substring)
        compose.onNode(hasText(text, substring = substring) and hasClickAction()).scrollIfNeeded().performClick()
        compose.waitForIdle()
    }

    /** Not every screen scrolls; only scroll when the node sits in a scrollable parent. */
    private fun SemanticsNodeInteraction.scrollIfNeeded(): SemanticsNodeInteraction = apply { runCatching { performScrollTo() } }

    private fun type(label: String, value: String) {
        compose.onNode(hasText(label) and hasSetTextAction()).scrollIfNeeded().performTextInput(value)
    }

    private fun tab(name: String) {
        // Bottom bar items are the only clickable nodes whose whole text is the tab name.
        compose.onAllNodes(hasText(name) and hasClickAction()).fetchSemanticsNodes().let { check(it.isNotEmpty()) { "no tab $name" } }
        compose.onAllNodes(hasText(name) and hasClickAction()).let { it[it.fetchSemanticsNodes().size - 1] }.performClick()
        compose.waitForIdle()
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
        waitFor("Nothing is shared until you start it")
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
        waitFor("Location, only when you share")
        shot("04-permission-location")
        click("Not now")
        waitFor("Notifications for what matters")
        click("Not now")
        waitFor("You're set")
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
        waitFor("New code")
        shot("07-invite-code")
        back()

        // Map with a circle: share needs location permission, then a duration
        waitFor("You're not sharing with Family")
        shot("08-map-not-sharing")
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        tab("Explore")
        tab("Map")
        click("Share")
        waitFor("For 1 hour")
        shot("09-share-sheet")
        click("For 1 hour")
        waitFor("Sharing until", substring = true)
        shot("10-map-sharing")

        // Explore: quests, quest detail, landmarks
        tab("Explore")
        waitFor("Today")
        shot("11-explore")
        click("Fresh ground")
        waitFor("Progress counts only", substring = true)
        shot("12-quest-detail")
        back()
        click("Find landmarks")
        waitFor("Monumen Nasional")
        // No GPS fix exists in this test, so no distance may be shown.
        waitFor("distances are hidden", substring = true)
        check(compose.onAllNodesWithText(" away", substring = true).fetchSemanticsNodes().isEmpty()) { "distance shown without a fix" }
        shot("13-landmarks")
        back()

        // Play: level, achievements, collection, leaderboard
        tab("Play")
        waitFor("Level 1")
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

        // Circle: feed and members
        tab("Circle")
        waitFor("Rani joined the circle")
        shot("18-circle")
        click("Members")
        waitFor("@$user", substring = true)
        back()

        // Profile: sharing controls, privacy, theme, sign out, sign back in
        tab("Profile")
        waitFor("Location sharing")
        shot("19-profile")
        click("Location sharing")
        waitFor("Sharing with 1 circle")
        shot("20-sharing")
        click("Stop sharing everywhere")
        waitFor("You're not sharing with anyone")
        back()
        click("Privacy")
        waitFor("Keep my location history")
        click("Keep my location history")
        waitFor("Keep for 7 days")
        shot("21-privacy")
        back()
        click("Dark")
        // Theme is stored in DataStore and applied on the next emission; wait for the dark surface.
        compose.waitUntil(5_000) { compose.activityRule.scenario.let { sc -> var dark = false; sc.onActivity { a -> dark = isDark(a) }; dark } }
        shot("22-profile-dark")
        tab("Explore")
        waitFor("Today")
        shot("23-explore-dark")
        tab("Profile")
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
        waitFor("You're not sharing with Family")
    }
}
