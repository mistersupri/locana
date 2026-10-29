package app.locana.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.locana.data.api.QuestDto
import app.locana.ui.auth.OnboardingScreen
import app.locana.ui.components.EmptyState
import app.locana.ui.components.ErrorState
import app.locana.ui.components.TileTrack
import app.locana.ui.explore.QuestCard
import app.locana.ui.theme.LocanaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ComponentsTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `error state retry is a working button`() {
        var retried = 0
        rule.setContent { LocanaTheme { ErrorState("No connection", onRetry = { retried++ }) } }
        rule.onNodeWithText("No connection").assertIsDisplayed()
        rule.onNodeWithText("Try again").performClick()
        assertEquals(1, retried)
    }

    @Test
    fun `empty state action is a working button`() {
        var clicked = false
        rule.setContent { LocanaTheme { EmptyState("No circles yet", "Make one", actionLabel = "Create a circle", onAction = { clicked = true }) } }
        rule.onNodeWithText("Create a circle").performClick()
        assertTrue(clicked)
    }

    @Test
    fun `tile track exposes progress to accessibility services`() {
        rule.setContent { LocanaTheme { TileTrack(0.5f) } }
        rule.onNode(SemanticsMatcher("has progress 0.5") { n ->
            n.config.getOrElseNullable(SemanticsProperties.ProgressBarRangeInfo) { null }?.current == 0.5f
        }).assertExists()
    }

    @Test
    fun `quest card shows progress and claim readiness`() {
        val q = QuestDto(
            id = "q1", questCode = "daily_tiles", name = "Fresh ground", description = "Reveal 3 tiles", type = "DAILY",
            condition = "DISCOVER_TILES", unit = "tiles", target = 3, progress = 3, status = "COMPLETED", rewardXp = 30,
            startedAt = Instant.now().toString(), expiresAt = Instant.now().plusSeconds(3600).toString(),
        )
        var opened = false
        rule.setContent { LocanaTheme { QuestCard(q) { opened = true } } }
        rule.onNodeWithText("3 of 3 tiles").assertIsDisplayed()
        rule.onNodeWithText("Ready to claim").assertIsDisplayed()
        rule.onNodeWithText("+30 XP").assertIsDisplayed()
        rule.onNodeWithText("Fresh ground").performClick()
        assertTrue(opened)
    }

    @Test
    fun `onboarding offers both paths`() {
        var created = false
        var signedIn = false
        rule.setContent { LocanaTheme { OnboardingScreen(onCreate = { created = true }, onSignIn = { signedIn = true }) } }
        rule.onNodeWithText("Nothing is shared until you start it").assertIsDisplayed()
        rule.onNodeWithText("Create an account").performScrollTo().performClick()
        rule.onNodeWithText("I already have one").performScrollTo().performClick()
        assertTrue(created && signedIn)
    }
}
