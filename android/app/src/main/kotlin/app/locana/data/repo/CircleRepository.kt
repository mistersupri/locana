package app.locana.data.repo

import app.locana.data.api.ChallengeDto
import app.locana.data.api.ChallengeRequest
import app.locana.data.api.CircleDetailDto
import app.locana.data.api.CircleExplorationDto
import app.locana.data.api.CircleSummaryDto
import app.locana.data.api.CreateCircleRequest
import app.locana.data.api.CreateInvitationRequest
import app.locana.data.api.FeedPageDto
import app.locana.data.api.InvitationDto
import app.locana.data.api.JoinRequest
import app.locana.data.api.LocanaApi
import app.locana.data.api.MuteRequest
import app.locana.data.api.PlaceDto
import app.locana.data.api.PlaceRequest
import app.locana.data.api.ReactionRequest
import app.locana.data.api.RoleRequest
import app.locana.data.api.UpdateCircleRequest
import app.locana.data.api.VisibilityRequest
import app.locana.data.api.apiCall
import app.locana.data.api.apiUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CircleRepository @Inject constructor(private val api: LocanaApi, private val cache: CacheStore) {
    private val listSer = ListSerializer(CircleSummaryDto.serializer())
    private val _circles = MutableStateFlow<Cached<List<CircleSummaryDto>>?>(null)
    val circles: StateFlow<Cached<List<CircleSummaryDto>>?> = _circles.asStateFlow()

    suspend fun loadCached() {
        if (_circles.value == null) _circles.value = cache.read("circles", listSer)
    }

    suspend fun refresh(): Result<List<CircleSummaryDto>> = apiCall { api.circles() }.onSuccess {
        cache.write("circles", listSer, it)
        _circles.value = Cached(it, System.currentTimeMillis())
    }

    suspend fun detail(id: String): Result<CircleDetailDto> = apiCall { api.circle(id) }.onSuccess { cache.write("circle:$id", CircleDetailDto.serializer(), it) }
    suspend fun cachedDetail(id: String) = cache.read("circle:$id", CircleDetailDto.serializer())

    suspend fun create(name: String, avatarKey: String?) = apiCall { api.createCircle(CreateCircleRequest(name.trim(), avatarKey)) }.also { refresh() }
    suspend fun update(id: String, name: String?, avatarKey: String?) = apiCall { api.updateCircle(id, UpdateCircleRequest(name?.trim(), avatarKey)) }.also { refresh() }
    suspend fun delete(id: String) = apiUnit { api.deleteCircle(id) }.also { refresh() }
    suspend fun leave(id: String) = apiUnit { api.leaveCircle(id) }.also { refresh() }
    suspend fun join(code: String) = apiCall { api.join(JoinRequest(code)) }.also { refresh() }

    suspend fun invitations(id: String): Result<List<InvitationDto>> = apiCall { api.invitations(id) }
    suspend fun createInvitation(id: String, hours: Int, maxUses: Int) = apiCall { api.createInvitation(id, CreateInvitationRequest(hours, maxUses)) }
    suspend fun revokeInvitation(id: String, inv: String) = apiUnit { api.revokeInvitation(id, inv) }

    suspend fun setRole(id: String, userId: String, role: String) = apiCall { api.setRole(id, userId, RoleRequest(role)) }
    suspend fun removeMember(id: String, userId: String) = apiUnit { api.removeMember(id, userId) }
    suspend fun mute(id: String, muted: Boolean) = apiUnit { api.mute(id, MuteRequest(muted)) }
    suspend fun hideFrom(id: String, viewerId: String, hidden: Boolean) = apiUnit { api.visibility(id, VisibilityRequest(viewerId, hidden)) }

    suspend fun feed(id: String, cursor: String?): Result<FeedPageDto> = apiCall { api.feed(id, cursor) }
    suspend fun react(itemId: String, emoji: String?) = apiUnit { api.react(itemId, ReactionRequest(emoji)) }

    suspend fun challenges(id: String): Result<List<ChallengeDto>> = apiCall { api.challenges(id) }
    suspend fun createChallenge(id: String, title: String, metric: String, target: Int, days: Int) =
        apiCall { api.createChallenge(id, ChallengeRequest(title.trim(), metric, target, days)) }
    suspend fun exploration(id: String): Result<CircleExplorationDto> = apiCall { api.circleExploration(id) }

    suspend fun places(circleId: String): Result<List<PlaceDto>> = apiCall { api.places(circleId) }
    suspend fun createPlace(circleId: String, r: PlaceRequest) = apiCall { api.createPlace(circleId, r) }
    suspend fun updatePlace(id: String, r: PlaceRequest) = apiCall { api.updatePlace(id, r) }
    suspend fun deletePlace(id: String) = apiUnit { api.deletePlace(id) }

    fun clear() {
        _circles.value = null
    }
}
