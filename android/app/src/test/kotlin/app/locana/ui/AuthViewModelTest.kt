package app.locana.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.locana.data.TestGraph
import app.locana.data.auth.SessionState
import app.locana.ui.auth.AuthViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AuthViewModelTest {
    private val server = MockWebServer()
    private lateinit var g: TestGraph
    private lateinit var vm: AuthViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server.start()
        g = TestGraph(server)
        vm = AuthViewModel(g.auth, g.settings)
    }

    @After
    fun tearDown() {
        g.close()
        server.shutdown()
        Dispatchers.resetMain()
    }

    @Test
    fun `register reports every invalid field without calling the server`() {
        vm.edit { it.copy(displayName = "", username = "Bad Name", email = "nope", password = "short") }
        vm.register()
        assertEquals(setOf("displayName", "username", "email", "password"), vm.form.value.fieldErrors.keys)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `login asks for both fields first, and editing clears the error`() {
        vm.login()
        assertEquals("Enter your email or username and password", vm.form.value.error)
        vm.edit { it.copy(identifier = "rani") }
        assertNull(vm.form.value.error)
    }

    @Test
    fun `wrong password shows the server message and keeps the user signed out`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"code":"INVALID_CREDENTIALS","message":"Email or password is incorrect"}}"""))
        vm.edit { it.copy(identifier = "rani", password = "wrong password!") }
        vm.login()
        withTimeout(3_000) { while (vm.form.value.busy || vm.form.value.error == null) kotlinx.coroutines.delay(10) }
        assertEquals("Email or password is incorrect", vm.form.value.error)
        g.session.restore()
        assertEquals(SessionState.SignedOut, g.session.state.value)
    }

    @Test
    fun `successful login stores tokens and signs in`() = runBlocking {
        server.enqueue(MockResponse().setBody(AUTH_OK))
        vm.edit { it.copy(identifier = "rani", password = "correct horse battery") }
        vm.login()
        val s = withTimeout(3_000) { g.session.state.first { it is SessionState.SignedIn } }
        assertEquals(SessionState.SignedIn("u1"), s)
        assertEquals("r-token-0123456789012345", g.tokens.current()?.refresh)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"identifier\":\"rani\""))
    }

    companion object {
        val AUTH_OK = """
            {"tokens":{"accessToken":"a","accessTokenExpiresIn":900,"refreshToken":"r-token-0123456789012345","sessionId":"s1"},
             "user":{"id":"u1","email":"rani@example.com","username":"rani","hasPassword":true,"googleLinked":false,"createdAt":"2026-10-28T00:00:00Z",
               "profile":{"displayName":"Rani","avatarKey":"explorer_teal","onboardingDone":true},
               "progress":{"level":1,"totalXp":0,"lifetimeXp":0,"levelStartXp":0,"nextLevelXp":100,"levelProgress":0.0,"currentStreak":0,"longestStreak":0,"tilesDiscovered":0,"questsCompleted":0,"totalDistanceM":0},
               "privacy":{"sharingPaused":false,"historyEnabled":false,"historyRetentionDays":7,"explorationEnabled":false,"showInLeaderboards":true,"shareExplorationWithCircles":true,"notifyArrivals":true,"notifyQuests":true,"notifyAchievements":true,"notifyCircle":true}}}
        """.trimIndent()
    }
}
