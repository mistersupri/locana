package app.locana.data.repo

import app.locana.data.api.AchievementDto
import app.locana.data.api.ClaimResultDto
import app.locana.data.api.CollectibleDto
import app.locana.data.api.GameEventDto
import app.locana.data.api.LandmarkDto
import app.locana.data.api.LeaderboardDto
import app.locana.data.api.LocanaApi
import app.locana.data.api.QuestDto
import app.locana.data.api.QuestPageDto
import app.locana.data.api.StatsDto
import app.locana.data.api.TerritoryDetailDto
import app.locana.data.api.TerritorySummaryDto
import app.locana.data.api.TilesDto
import app.locana.data.api.apiCall
import app.locana.domain.TileBounds
import kotlinx.serialization.builtins.ListSerializer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Read-mostly game data. Every reward is decided by the server; the app only displays it. */
@Singleton
class GameRepository @Inject constructor(private val api: LocanaApi, private val cache: CacheStore) {
    private val questSer = ListSerializer(QuestDto.serializer())
    // One idempotency key per quest until the claim succeeds, so a retry after a timeout is
    // recognised by the server as the same claim.
    private val claimKeys = ConcurrentHashMap<String, String>()

    suspend fun cachedQuests() = cache.read("quests", questSer)
    suspend fun quests(): Result<List<QuestDto>> = apiCall { api.quests() }.onSuccess { cache.write("quests", questSer, it) }
    suspend fun quest(id: String): Result<QuestDto> = apiCall { api.quest(id) }
    suspend fun questHistory(cursor: String?): Result<QuestPageDto> = apiCall { api.questHistory(cursor) }

    suspend fun claim(id: String): Result<ClaimResultDto> {
        val key = claimKeys.getOrPut(id) { UUID.randomUUID().toString().replace("-", "") }
        return apiCall { api.claim(id, key) }.onSuccess { claimKeys.remove(id) }
    }

    suspend fun achievements(): Result<List<AchievementDto>> = apiCall { api.achievements() }
    suspend fun collectibles(): Result<List<CollectibleDto>> = apiCall { api.collectibles() }
    suspend fun levels() = apiCall { api.levels() }
    suspend fun stats(): Result<StatsDto> = apiCall { api.stats() }
    suspend fun events(): Result<List<GameEventDto>> = apiCall { api.events() }

    suspend fun leaderboard(scope: String, metric: String, period: String, circleId: String?, offset: Int = 0): Result<LeaderboardDto> =
        apiCall { api.leaderboard(scope, metric, period, circleId, 50, offset) }

    suspend fun tiles(b: TileBounds): Result<TilesDto> = apiCall { api.tiles(b.north, b.south, b.east, b.west) }
    suspend fun territories(b: TileBounds): Result<List<TerritorySummaryDto>> = apiCall { api.territories(b.north, b.south, b.east, b.west) }
    suspend fun territory(id: String): Result<TerritoryDetailDto> = apiCall { api.territory(id) }
    suspend fun landmarks(lat: Double, lon: Double, query: String? = null): Result<List<LandmarkDto>> =
        apiCall { api.landmarks(lat, lon, null, query?.takeIf { it.isNotBlank() }) }
}
