package app.locana.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.locana.data.db.CacheEntry
import app.locana.data.db.LocanaDatabase
import app.locana.data.db.PendingSample
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PendingSampleDaoTest {
    private lateinit var db: LocanaDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), LocanaDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun s(t: Long) = PendingSample(0, 1.0, 2.0, 5f, t, t, null, null, null, "gps", null, false, null)

    @Test
    fun `returns samples in time order and trims old ones`() = runTest {
        listOf(300L, 100L, 200L).forEach { db.pending().insert(s(it)) }
        assertEquals(listOf(100L, 200L, 300L), db.pending().oldest(10).map { it.recordedAtMs })
        assertEquals(1, db.pending().dropOlderThan(150))
        assertEquals(2, db.pending().count().first())
        assertEquals(200L, db.pending().oldestTime())
    }

    @Test
    fun `cache entries replace by key`() = runTest {
        db.cache().put(CacheEntry("k", "1", 1))
        db.cache().put(CacheEntry("k", "2", 2))
        assertEquals("2", db.cache().get("k")?.json)
    }
}
