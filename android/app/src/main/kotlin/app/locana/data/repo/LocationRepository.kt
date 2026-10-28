package app.locana.data.repo

import app.locana.data.api.AppError
import app.locana.data.api.GameResultDto
import app.locana.data.api.LocanaApi
import app.locana.data.api.PauseAllRequest
import app.locana.data.api.SampleDto
import app.locana.data.api.SharingStateDto
import app.locana.data.api.StartSharingRequest
import app.locana.data.api.UploadRequest
import app.locana.data.api.apiCall
import app.locana.data.db.CachedMemberLocation
import app.locana.data.db.LocanaDatabase
import app.locana.data.db.PendingSample
import app.locana.data.prefs.SettingsStore
import app.locana.data.realtime.RealtimeEvent
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

sealed interface FlushResult {
    data object Empty : FlushResult
    data class Uploaded(val count: Int) : FlushResult
    data object Offline : FlushResult
    /** Server says nothing needs location now (sharing and exploration off): stop tracking. */
    data object NotNeeded : FlushResult
    data class Failed(val error: AppError) : FlushResult
}

@Singleton
class LocationRepository @Inject constructor(
    private val api: LocanaApi,
    private val db: LocanaDatabase,
    private val settings: SettingsStore,
) {
    private val _sharing = MutableStateFlow<SharingStateDto?>(null)
    val sharing: StateFlow<SharingStateDto?> = _sharing.asStateFlow()

    private val _game = MutableSharedFlow<GameResultDto>(extraBufferCapacity = 8)
    /** Rewards decided by the server for uploaded samples, for in-app celebration. */
    val gameResults: SharedFlow<GameResultDto> = _game.asSharedFlow()

    val pendingCount: Flow<Int> = db.pending().count()
    private val flushLock = Mutex()

    private suspend fun accept(s: SharingStateDto): SharingStateDto {
        _sharing.value = s
        settings.setLastKnownCollect(s.collectLocation)
        return s
    }

    suspend fun refreshSharing(): Result<SharingStateDto> = apiCall { accept(api.sharing()) }
    suspend fun start(circleId: String, minutes: Int?) = apiCall { accept(api.startSharing(StartSharingRequest(circleId, minutes))) }
    suspend fun pause(sessionId: String) = apiCall { accept(api.pauseSharing(sessionId)) }
    suspend fun resume(sessionId: String) = apiCall { accept(api.resumeSharing(sessionId)) }
    suspend fun stop(sessionId: String) = apiCall { accept(api.stopSharing(sessionId)) }
    suspend fun pauseAll(paused: Boolean) = apiCall { accept(api.pauseAll(PauseAllRequest(paused))) }
    suspend fun stopAll() = apiCall { accept(api.stopAll()) }

    /** Exploration changes the "collect location" answer too. */
    fun setExplorationLocally(enabled: Boolean) {
        _sharing.value = _sharing.value?.let { s ->
            s.copy(explorationEnabled = enabled, collectLocation = enabled || (!s.globallyPaused && s.sessions.any { it.status == "ACTIVE" }))
        }
    }

    // ── member locations ──

    fun memberLocations(circleId: String): Flow<List<CachedMemberLocation>> = db.members().observe(circleId)

    suspend fun refreshLocations(circleId: String): Result<Unit> = apiCall {
        val rows = api.locations(circleId)
        val now = System.currentTimeMillis()
        db.withTransaction {
            db.members().clearCircle(circleId)
            db.members().upsert(rows.map { r ->
                CachedMemberLocation(
                    circleId, r.userId, r.status, r.location?.latitude, r.location?.longitude, r.location?.accuracyM,
                    r.location?.recordedAt?.let { Instant.parse(it).toEpochMilli() }, r.location?.batteryPct, now,
                )
            })
        }
    }

    /** Applies realtime events to the local cache. Hidden members lose their coordinates at once. */
    suspend fun applyRealtime(e: RealtimeEvent) {
        fun JsonObject.s(k: String) = this[k]?.jsonPrimitive?.contentOrNull
        fun JsonObject.d(k: String) = this[k]?.jsonPrimitive?.doubleOrNull
        when (e.type) {
            "location.update" -> {
                val circleId = e.data.s("circleId") ?: return
                val userId = e.data.s("userId") ?: return
                val at = e.data.s("recordedAt")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                db.members().upsert(listOf(CachedMemberLocation(circleId, userId, "LIVE", e.data.d("latitude"), e.data.d("longitude"), e.data.d("accuracyM"), at, e.data["batteryPct"]?.jsonPrimitive?.intOrNull, System.currentTimeMillis())))
            }
            "member.availability" -> {
                val circleId = e.data.s("circleId") ?: return
                val userId = e.data.s("userId") ?: return
                if (e.data.s("status") == "HIDDEN") {
                    db.members().upsert(listOf(CachedMemberLocation(circleId, userId, "HIDDEN", null, null, null, null, null, System.currentTimeMillis())))
                } else {
                    refreshLocations(circleId)
                }
            }
            "circle.membership" -> e.data.s("circleId")?.let { refreshLocations(it) }
            "sharing.status" -> refreshSharing()
        }
    }

    // ── upload queue ──

    suspend fun enqueue(s: PendingSample) {
        db.pending().insert(s.copy(sequence = settings.nextSequence()))
    }

    suspend fun oldestPendingMs(): Long? = db.pending().oldestTime()

    /** Uploads queued samples oldest first, in batches of 100. Safe to call from several places. */
    suspend fun flush(): FlushResult = flushLock.withLock {
        db.pending().dropOlderThan(System.currentTimeMillis() - 23 * 3_600_000L)
        var uploaded = 0
        while (true) {
            val batch = db.pending().oldest(100)
            if (batch.isEmpty()) return if (uploaded == 0) FlushResult.Empty else FlushResult.Uploaded(uploaded)
            val result = apiCall { api.upload(UploadRequest(batch.map { it.toDto() })) }
            result.onSuccess { r ->
                db.pending().delete(batch.map { it.id })
                uploaded += batch.size
                r.game?.takeIf { it.xpGained > 0 || it.levelUps.isNotEmpty() || it.achievements.isNotEmpty() || it.questUpdates.isNotEmpty() || it.newTiles.isNotEmpty() }
                    ?.let { _game.tryEmit(it) }
            }
            val err = result.exceptionOrNull() as? AppError ?: continue
            return when {
                err is AppError.Offline -> FlushResult.Offline
                err.codeOrNull == "LOCATION_NOT_NEEDED" -> {
                    db.pending().clear()
                    refreshSharing()
                    FlushResult.NotNeeded
                }
                err is AppError.Server && err.status == 400 -> {
                    // Malformed batch can never succeed; drop it rather than retry forever.
                    db.pending().delete(batch.map { it.id })
                    continue
                }
                else -> FlushResult.Failed(err)
            }
        }
        @Suppress("UNREACHABLE_CODE") FlushResult.Empty
    }

    suspend fun clearLocal() {
        db.pending().clear()
        db.members().clear()
        _sharing.value = null
    }
}

private fun PendingSample.toDto() = SampleDto(
    latitude = latitude,
    longitude = longitude,
    accuracyM = accuracyM.toDouble(),
    recordedAt = Instant.ofEpochMilli(recordedAtMs).toString(),
    sequence = sequence,
    speedMps = speedMps?.toDouble(),
    bearingDeg = bearingDeg?.toDouble(),
    altitudeM = altitudeM,
    source = source,
    activity = activity,
    isMock = isMock.takeIf { it },
    batteryPct = batteryPct,
)
