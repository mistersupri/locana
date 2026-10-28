package app.locana.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.locana.data.db.LocanaDatabase
import app.locana.data.db.PendingSample
import app.locana.data.repo.FlushResult
import app.locana.data.repo.LocationRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Offline queue behaviour: nothing is lost while offline, nothing is retried forever. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class LocationRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var db: LocanaDatabase
    private lateinit var repo: LocationRepository

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val g = TestGraph(server)
        db = g.db
        repo = g.location
    }

    @After
    fun tearDown() {
        db.close()
        server.shutdown()
    }

    private fun sample(msAgo: Long) = PendingSample(
        latitude = -6.2, longitude = 106.8, accuracyM = 8f, recordedAtMs = System.currentTimeMillis() - msAgo, sequence = 0,
        speedMps = null, bearingDeg = null, altitudeM = null, source = "fused", activity = null, isMock = false, batteryPct = 80,
    )

    @Test
    fun `uploads oldest first and clears the queue on success`() = runTest {
        repo.enqueue(sample(5_000))
        repo.enqueue(sample(60_000))
        server.enqueue(MockResponse().setBody("""{"accepted":2,"rejected":[],"sharingActive":true}"""))
        val r = repo.flush()
        assertEquals(FlushResult.Uploaded(2), r)
        assertEquals(0, db.pending().countNow())
        val sent = testJson.decodeFromString(app.locana.data.api.UploadRequest.serializer(), server.takeRequest().body.readUtf8()).samples
        // Oldest fix first; it was enqueued second, so it carries the higher sequence number.
        assertTrue(sent[0].recordedAt < sent[1].recordedAt)
        assertTrue(sent[0].sequence > sent[1].sequence)
    }

    @Test
    fun `keeps samples while offline`() = runTest {
        repo.enqueue(sample(1_000))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        assertEquals(FlushResult.Offline, repo.flush())
        assertEquals(1, db.pending().countNow())
    }

    @Test
    fun `stops and clears when the server no longer needs location`() = runTest {
        repo.enqueue(sample(1_000))
        server.enqueue(MockResponse().setResponseCode(409).setBody("""{"error":{"code":"LOCATION_NOT_NEEDED","message":"off"}}"""))
        server.enqueue(MockResponse().setBody("""{"globallyPaused":false,"explorationEnabled":false,"collectLocation":false,"sessions":[]}"""))
        assertEquals(FlushResult.NotNeeded, repo.flush())
        assertEquals(0, db.pending().countNow())
        assertEquals(false, repo.sharing.first()?.collectLocation)
    }

    @Test
    fun `drops a batch the server rejects as malformed instead of retrying forever`() = runTest {
        repo.enqueue(sample(1_000))
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":{"code":"VALIDATION_FAILED","message":"bad"}}"""))
        assertEquals(FlushResult.Empty, repo.flush())
        assertEquals(0, db.pending().countNow())
    }

    @Test
    fun `discards samples older than the server accepts`() = runTest {
        repo.enqueue(sample(25 * 3_600_000L))
        assertEquals(FlushResult.Empty, repo.flush())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `server error keeps the queue for a later retry`() = runTest {
        repo.enqueue(sample(1_000))
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"error":{"code":"INTERNAL","message":"x"}}"""))
        assertTrue(repo.flush() is FlushResult.Failed)
        assertEquals(1, db.pending().countNow())
    }

    @Test
    fun `hidden availability event removes coordinates from the cache`() = runTest {
        db.members().upsert(listOf(app.locana.data.db.CachedMemberLocation("c1", "u1", "LIVE", -6.2, 106.8, 10.0, 1L, 50, 1L)))
        repo.applyRealtime(app.locana.data.realtime.RealtimeEvent("member.availability", testJson.parseToJsonElement("""{"circleId":"c1","userId":"u1","status":"HIDDEN"}""").let { it as kotlinx.serialization.json.JsonObject }))
        val row = repo.memberLocations("c1").first().single()
        assertEquals("HIDDEN", row.status)
        assertEquals(null, row.latitude)
    }
}
