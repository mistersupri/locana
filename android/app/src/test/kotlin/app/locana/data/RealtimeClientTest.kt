package app.locana.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.locana.data.api.TokenPair
import app.locana.data.auth.SessionManager
import app.locana.data.auth.TokenRefresher
import app.locana.data.auth.TokenStore
import app.locana.data.realtime.ConnectionState
import app.locana.data.realtime.RealtimeClient
import app.locana.domain.Backoff
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/** Reconnects after the server drops the socket and re-subscribes to the same circles. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RealtimeClientTest {
    private val server = MockWebServer()
    private val received = CopyOnWriteArrayList<String>()
    private val sockets = CopyOnWriteArrayList<WebSocket>()
    private val connections = CountDownLatch(2)

    @Before
    fun setUp() {
        repeat(3) {
            server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    sockets += webSocket
                    webSocket.send("""{"type":"ready","data":{"userId":"u1"}}""")
                    connections.countDown()
                }
                override fun onMessage(webSocket: WebSocket, text: String) { received += text }
            }))
        }
        server.start()
    }

    @After
    fun tearDown() {
        sockets.forEach { it.close(1000, null) }
        server.shutdown()
    }

    @Test
    fun `reconnects with backoff and resubscribes`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val tokens = TokenStore(ctx, PlainCipher()).also { it.save("u1", TokenPair("access-1", 900, "refresh-1", "s1")) }
        val refresher = TokenRefresher(tokens, SessionManager(tokens), OkHttpClient(), server.url("/").toString())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val client = RealtimeClient(OkHttpClient(), server.url("/ws").toString().replace("http", "ws"), tokens, refresher, scope, Backoff(baseMs = 50, maxMs = 200, random = Random(1)))
        client.subscribe(listOf("c1"))
        client.start()
        withTimeout(5_000) { client.state.first { it == ConnectionState.CONNECTED } }
        // Server drops the connection.
        sockets.first().close(1011, "restart")
        assertTrue(connections.await(5, TimeUnit.SECONDS))
        withTimeout(5_000) { client.state.first { it == ConnectionState.CONNECTED } }
        Thread.sleep(200)
        assertEquals(2, received.count { it.contains("\"subscribe\"") && it.contains("c1") })
        // The upgrade request carried the token as a header, never in the URL.
        val req = server.takeRequest()
        assertEquals("Bearer access-1", req.getHeader("Authorization"))
        assertTrue(req.path!!.endsWith("/ws"))
        client.stop()
    }
}
