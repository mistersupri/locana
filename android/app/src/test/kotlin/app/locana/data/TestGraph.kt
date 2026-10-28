package app.locana.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.locana.data.auth.SessionManager
import app.locana.data.auth.TokenCipher
import app.locana.data.auth.TokenStore
import app.locana.data.db.LocanaDatabase
import app.locana.data.location.TrackingController
import app.locana.data.prefs.SettingsStore
import app.locana.data.repo.AuthRepository
import app.locana.data.repo.CacheStore
import app.locana.data.repo.CircleRepository
import app.locana.data.repo.LocationRepository
import app.locana.data.repo.MeRepository
import okhttp3.mockwebserver.MockWebServer

/** Robolectric has no AndroidKeyStore; tests store tokens with a reversible stand-in. */
class PlainCipher : TokenCipher {
    override fun encrypt(plain: String) = "plain:$plain"
    override fun decrypt(encoded: String) = encoded.removePrefix("plain:")
}

/** Real repositories wired to a MockWebServer and an in-memory database. */
class TestGraph(val server: MockWebServer) {
    val context: Context = ApplicationProvider.getApplicationContext()
    val db: LocanaDatabase = Room.inMemoryDatabaseBuilder(context, LocanaDatabase::class.java).allowMainThreadQueries().build()
    val api = server.api()
    val settings = SettingsStore(context)
    val tokens = TokenStore(context, PlainCipher())
    val session = SessionManager(tokens)
    val cache = CacheStore(db.cache(), testJson)
    val tracking = TrackingController(context)
    val me = MeRepository(api, cache, settings)
    val circles = CircleRepository(api, cache)
    val location = LocationRepository(api, db, settings)
    val auth = AuthRepository(api, tokens, session, db, settings, cache, tracking, me, circles)

    fun close() = db.close()
}
