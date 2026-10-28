package app.locana.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A location fix waiting for upload. Kept only while offline or batching; never synced elsewhere. */
@Entity(tableName = "pending_samples")
data class PendingSample(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Float,
    val recordedAtMs: Long,
    val sequence: Long,
    val speedMps: Float?,
    val bearingDeg: Float?,
    val altitudeM: Double?,
    val source: String,
    val activity: String?,
    val isMock: Boolean,
    val batteryPct: Int?,
)

/** Last member positions seen on this device. Shown with their age, never as "live" on their own. */
@Entity(tableName = "member_locations", primaryKeys = ["circleId", "userId"])
data class CachedMemberLocation(
    val circleId: String,
    val userId: String,
    val status: String,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyM: Double?,
    val recordedAtMs: Long?,
    val batteryPct: Int?,
    val updatedAtMs: Long,
)

/** JSON snapshots of server responses, so screens open instantly and still work offline. */
@Entity(tableName = "cache")
data class CacheEntry(@PrimaryKey val key: String, val json: String, val updatedAtMs: Long)

@Dao
interface PendingSampleDao {
    @Insert suspend fun insert(s: PendingSample): Long
    @Query("SELECT * FROM pending_samples ORDER BY recordedAtMs ASC LIMIT :limit") suspend fun oldest(limit: Int): List<PendingSample>
    @Query("DELETE FROM pending_samples WHERE id IN (:ids)") suspend fun delete(ids: List<Long>)
    @Query("SELECT COUNT(*) FROM pending_samples") fun count(): Flow<Int>
    @Query("SELECT COUNT(*) FROM pending_samples") suspend fun countNow(): Int
    @Query("SELECT MIN(recordedAtMs) FROM pending_samples") suspend fun oldestTime(): Long?
    /** The server refuses fixes older than 24 h, so there is no point keeping them. */
    @Query("DELETE FROM pending_samples WHERE recordedAtMs < :cutoffMs") suspend fun dropOlderThan(cutoffMs: Long): Int
    @Query("DELETE FROM pending_samples") suspend fun clear()
}

@Dao
interface MemberLocationDao {
    @Upsert suspend fun upsert(rows: List<CachedMemberLocation>)
    @Query("SELECT * FROM member_locations WHERE circleId = :circleId") fun observe(circleId: String): Flow<List<CachedMemberLocation>>
    @Query("DELETE FROM member_locations WHERE circleId = :circleId") suspend fun clearCircle(circleId: String)
    @Query("DELETE FROM member_locations WHERE circleId = :circleId AND userId = :userId") suspend fun remove(circleId: String, userId: String)
    @Query("DELETE FROM member_locations") suspend fun clear()
}

@Dao
interface CacheDao {
    @Upsert suspend fun put(e: CacheEntry)
    @Query("SELECT * FROM cache WHERE `key` = :key") suspend fun get(key: String): CacheEntry?
    @Query("SELECT * FROM cache WHERE `key` = :key") fun observe(key: String): Flow<CacheEntry?>
    @Query("DELETE FROM cache") suspend fun clear()
}

@Database(entities = [PendingSample::class, CachedMemberLocation::class, CacheEntry::class], version = 1, exportSchema = true)
abstract class LocanaDatabase : RoomDatabase() {
    abstract fun pending(): PendingSampleDao
    abstract fun members(): MemberLocationDao
    abstract fun cache(): CacheDao
}
