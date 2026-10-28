package app.locana.data.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// Wire models for the /v1 API. Timestamps stay ISO-8601 strings and are parsed at the edges.

@Serializable data class ErrorEnvelope(val error: ErrorBody)
@Serializable data class ErrorBody(val code: String, val message: String)

// ── auth ──
@Serializable data class RegisterRequest(val email: String, val password: String, val username: String, val displayName: String, val deviceName: String?)
@Serializable data class LoginRequest(val identifier: String, val password: String, val deviceName: String?)
@Serializable data class GoogleRequest(val idToken: String, val deviceName: String?)
@Serializable data class RefreshRequest(val refreshToken: String)
@Serializable data class ForgotRequest(val email: String)
@Serializable data class ResetRequest(val email: String, val code: String, val newPassword: String)
@Serializable data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)
@Serializable data class TokenPair(val accessToken: String, val accessTokenExpiresIn: Int, val refreshToken: String, val sessionId: String)
@Serializable data class AuthResponse(val tokens: TokenPair, val user: MeDto)
@Serializable data class SessionDto(val id: String, val deviceName: String? = null, val userAgent: String? = null, val createdAt: String, val lastUsedAt: String, val current: Boolean)

// ── me ──
@Serializable
data class MeDto(
    val id: String,
    val email: String,
    val username: String,
    val hasPassword: Boolean,
    val googleLinked: Boolean,
    val createdAt: String,
    val profile: ProfileDto,
    val progress: ProgressDto,
    val privacy: PrivacyDto,
)

@Serializable
data class ProfileDto(
    val displayName: String,
    val avatarKey: String,
    val avatarFrameKey: String? = null,
    val titleKey: String? = null,
    val markerKey: String? = null,
    val bio: String? = null,
    val onboardingDone: Boolean,
    val teamCircleId: String? = null,
)

@Serializable
data class ProgressDto(
    val level: Int,
    val totalXp: Int,
    val lifetimeXp: Int,
    val levelStartXp: Int,
    val nextLevelXp: Int? = null,
    val levelProgress: Double,
    val currentStreak: Int,
    val longestStreak: Int,
    val tilesDiscovered: Int,
    val questsCompleted: Int,
    val totalDistanceM: Int,
)

@Serializable
data class PrivacyDto(
    val sharingPaused: Boolean,
    val historyEnabled: Boolean,
    val historyRetentionDays: Int,
    val explorationEnabled: Boolean,
    val showInLeaderboards: Boolean,
    val leaderboardAlias: String? = null,
    val shareExplorationWithCircles: Boolean,
    val notifyArrivals: Boolean,
    val notifyQuests: Boolean,
    val notifyAchievements: Boolean,
    val notifyCircle: Boolean,
)

@Serializable
data class UpdateProfileRequest(
    val displayName: String? = null,
    val username: String? = null,
    val avatarKey: String? = null,
    val bio: String? = null,
    val onboardingDone: Boolean? = null,
    val teamCircleId: String? = null,
)

/** Sent with explicit nulls, so "unequip" is possible. */
@Serializable data class CosmeticsRequest(val avatarFrameKey: String?, val titleKey: String?, val markerKey: String?)

@Serializable
data class PrivacyPatch(
    val historyEnabled: Boolean? = null,
    val historyRetentionDays: Int? = null,
    val explorationEnabled: Boolean? = null,
    val showInLeaderboards: Boolean? = null,
    val leaderboardAlias: String? = null,
    val shareExplorationWithCircles: Boolean? = null,
    val notifyArrivals: Boolean? = null,
    val notifyQuests: Boolean? = null,
    val notifyAchievements: Boolean? = null,
    val notifyCircle: Boolean? = null,
)

@Serializable data class DeleteAccountRequest(val confirm: String = "DELETE", val password: String?)
@Serializable data class DeviceRequest(val installId: String, val model: String?, val appVersion: String?)
@Serializable data class BlockDto(val userId: String, val username: String, val displayName: String? = null, val since: String)
@Serializable data class ReportRequest(val reason: String, val details: String? = null, val circleId: String? = null)
@Serializable data class ReportResponse(val id: String, val status: String)

// ── circles ──
@Serializable data class CircleSummaryDto(val id: String, val name: String, val avatarKey: String, val memberCount: Int, val myRole: String, val muted: Boolean, val iAmSharing: Boolean)

@Serializable
data class CircleDetailDto(
    val id: String,
    val name: String,
    val avatarKey: String,
    val createdAt: String,
    val myRole: String,
    val muted: Boolean,
    val hiddenFromViewerIds: List<String> = emptyList(),
    val members: List<MemberDto>,
)

@Serializable
data class MemberDto(
    val userId: String,
    val username: String,
    val displayName: String,
    val avatarKey: String? = null,
    val avatarFrameKey: String? = null,
    val titleKey: String? = null,
    val level: Int = 1,
    val role: String,
    val joinedAt: String,
    val sharingWithMe: Boolean? = null,
    val blockedByMe: Boolean = false,
)

@Serializable data class CreateCircleRequest(val name: String, val avatarKey: String? = null)
@Serializable data class UpdateCircleRequest(val name: String? = null, val avatarKey: String? = null)
@Serializable data class CreateInvitationRequest(val expiresInHours: Int, val maxUses: Int)
@Serializable data class InvitationDto(val id: String, val codeHint: String, val maxUses: Int, val uses: Int, val expiresAt: String, val createdAt: String, val status: String, val code: String? = null, val deepLink: String? = null)
@Serializable data class JoinRequest(val code: String)
@Serializable data class RoleRequest(val role: String)
@Serializable data class MuteRequest(val muted: Boolean)
@Serializable data class VisibilityRequest(val viewerId: String, val hidden: Boolean)
@Serializable data class ReactionRequest(val emoji: String?)

@Serializable data class FeedActorDto(val id: String, val username: String, val displayName: String, val avatarKey: String? = null)
@Serializable data class ReactionCountDto(val emoji: String, val count: Int)
@Serializable data class FeedItemDto(val id: String, val kind: String, val payload: JsonObject = JsonObject(emptyMap()), val createdAt: String, val actor: FeedActorDto, val reactions: List<ReactionCountDto> = emptyList(), val myReaction: String? = null)
@Serializable data class FeedPageDto(val items: List<FeedItemDto>, val nextCursor: String? = null)

@Serializable data class ChallengeDto(val id: String, val circleId: String, val title: String, val metric: String, val target: Int, val progress: Int, val startsAt: String, val endsAt: String, val completedAt: String? = null, val status: String)
@Serializable data class ChallengeRequest(val title: String, val metric: String, val target: Int, val days: Int)

// ── location ──
@Serializable data class LocationDto(val latitude: Double, val longitude: Double, val accuracyM: Double, val speedMps: Double? = null, val bearingDeg: Double? = null, val recordedAt: String, val batteryPct: Int? = null, val activity: String? = null)
@Serializable data class MemberLocationDto(val userId: String, val status: String, val location: LocationDto? = null)

@Serializable
data class SampleDto(
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Double,
    val recordedAt: String,
    val sequence: Long,
    val speedMps: Double? = null,
    val bearingDeg: Double? = null,
    val altitudeM: Double? = null,
    val source: String,
    val activity: String? = null,
    val isMock: Boolean? = null,
    val batteryPct: Int? = null,
)

@Serializable data class UploadRequest(val samples: List<SampleDto>)
@Serializable data class RejectedDto(val index: Int, val reason: String)
@Serializable data class LandmarkHitDto(val id: String, val name: String, val firstVisit: Boolean)
@Serializable data class LevelUpDto(val from: Int, val to: Int)
@Serializable data class AchievementUnlockDto(val code: String, val name: String, val rarity: String, val rewardXp: Int)
@Serializable data class QuestProgressDto(val userId: String, val userQuestId: String, val questName: String, val progress: Int, val target: Int, val completed: Boolean)

@Serializable
data class GameResultDto(
    val xpGained: Int,
    val newTiles: List<String> = emptyList(),
    val landmarks: List<LandmarkHitDto> = emptyList(),
    val levelUps: List<LevelUpDto> = emptyList(),
    val achievements: List<AchievementUnlockDto> = emptyList(),
    val questUpdates: List<QuestProgressDto> = emptyList(),
)

@Serializable data class UploadResponse(val accepted: Int, val rejected: List<RejectedDto> = emptyList(), val latestRecordedAt: String? = null, val sharingActive: Boolean, val game: GameResultDto? = null)

@Serializable data class SharingSessionDto(val id: String, val circleId: String, val circleName: String? = null, val status: String, val startedAt: String, val expiresAt: String? = null, val pausedAt: String? = null, val endedAt: String? = null, val endReason: String? = null)
@Serializable data class SharingStateDto(val globallyPaused: Boolean, val explorationEnabled: Boolean, val collectLocation: Boolean, val sessions: List<SharingSessionDto>)
@Serializable data class StartSharingRequest(val circleId: String, val durationMinutes: Int? = null)
@Serializable data class PauseAllRequest(val paused: Boolean)
@Serializable data class HistoryPointDto(val latitude: Double, val longitude: Double, val accuracyM: Double, val recordedAt: String)
@Serializable data class DeletedDto(val deleted: Int)

// ── places ──
@Serializable data class PlaceDto(val id: String, val circleId: String, val name: String, val type: String, val latitude: Double, val longitude: Double, val radiusM: Int, val notifyArrive: Boolean, val notifyLeave: Boolean, val notifyUserIds: List<String> = emptyList(), val createdById: String, val updatedAt: String)
@Serializable data class PlaceRequest(val name: String, val type: String, val latitude: Double, val longitude: Double, val radiusM: Int, val notifyArrive: Boolean, val notifyLeave: Boolean, val notifyUserIds: List<String>)

// ── game ──
@Serializable data class QuestLogDto(val delta: Int, val reason: String, val at: String)

@Serializable
data class QuestDto(
    val id: String,
    val questCode: String,
    val name: String,
    val description: String,
    val type: String,
    val condition: String,
    val unit: String,
    val target: Int,
    val progress: Int,
    val status: String,
    val rewardXp: Int,
    val rewardCollectibleCode: String? = null,
    val circleId: String? = null,
    val startedAt: String,
    val expiresAt: String,
    val completedAt: String? = null,
    val claimedAt: String? = null,
    val recentProgress: List<QuestLogDto> = emptyList(),
)

@Serializable data class QuestPageDto(val items: List<QuestDto>, val nextCursor: String? = null)
@Serializable data class CollectibleRefDto(val code: String, val name: String)

@Serializable
data class ClaimResultDto(
    val quest: QuestDto,
    val xpAwarded: Int,
    val alreadyClaimed: Boolean,
    val totalXp: Int,
    val level: Int,
    val levelUps: List<LevelUpDto> = emptyList(),
    val achievements: List<AchievementUnlockDto> = emptyList(),
    val collectibles: List<CollectibleRefDto> = emptyList(),
)

@Serializable data class AchievementDto(val code: String, val name: String, val description: String, val rarity: String, val iconKey: String, val rewardXp: Int, val unlockedAt: String? = null, val progress: Int, val target: Int)
@Serializable data class CollectibleDto(val code: String, val name: String, val description: String, val kind: String, val rarity: String, val setCode: String? = null, val iconKey: String, val acquiredAt: String? = null)
@Serializable data class LevelDto(val level: Int, val totalXp: Int, val rewardCollectibleCode: String? = null)

@Serializable data class BoardEntryDto(val rank: Int, val userId: String, val name: String, val avatarKey: String? = null, val level: Int, val score: Int, val isMe: Boolean, val hiddenFromOthers: Boolean = false)
@Serializable data class BoardMeDto(val rank: Int? = null, val score: Int, val optedOut: Boolean)
@Serializable data class LeaderboardDto(val scope: String, val metric: String, val period: String, val periodKey: String, val total: Int, val entries: List<BoardEntryDto>, val me: BoardMeDto)

@Serializable data class TileDto(val id: String, val north: Double, val south: Double, val east: Double, val west: Double, val discoveredAt: String, val visits: Int)
@Serializable data class TilesDto(val tooLarge: Boolean, val tiles: List<TileDto>)
@Serializable data class DayStatDto(val day: String, val distanceM: Int, val tiles: Int, val landmarks: Int, val xp: Int)
@Serializable data class StatsDto(val tilesDiscovered: Int, val landmarksVisited: Int, val totalDistanceM: Int, val currentStreak: Int, val longestStreak: Int, val questsCompleted: Int, val last7Days: List<DayStatDto>)
@Serializable data class CircleExplorerDto(val userId: String, val displayName: String, val avatarKey: String? = null, val tilesDiscovered: Int, val level: Int)
@Serializable data class CircleExplorationDto(val totalTiles: Int, val members: List<CircleExplorerDto>, val hiddenMembers: Int)

@Serializable data class LandmarkDto(val id: String, val code: String, val name: String, val description: String, val category: String, val latitude: Double, val longitude: Double, val radiusM: Int, val rewardXp: Int, val setCode: String? = null, val visited: Boolean, val territoryId: String? = null, val distanceM: Int)
@Serializable data class TerritorySummaryDto(val id: String, val name: String, val landmarkId: String, val latitude: Double, val longitude: Double, val radiusM: Int, val ownerCircleId: String? = null, val ownerCircleName: String? = null, val ownedByMyCircle: Boolean, val expiresAt: String? = null)
@Serializable data class TerritoryLandmarkDto(val id: String, val name: String, val category: String, val latitude: Double, val longitude: Double, val radiusM: Int)
@Serializable data class TerritoryRulesDto(val claimMinScore: Int, val holdDays: Int)
@Serializable data class TerritoryRankDto(val rank: Int, val circleId: String, val circleName: String, val score: Int, val isMine: Boolean)
@Serializable data class TerritoryHistoryDto(val type: String, val circleName: String? = null, val day: String)

@Serializable
data class TerritoryDetailDto(
    val id: String,
    val name: String,
    val landmark: TerritoryLandmarkDto,
    val ownerCircleId: String? = null,
    val ownerCircleName: String? = null,
    val ownerScore: Int,
    val claimedAt: String? = null,
    val expiresAt: String? = null,
    val periodKey: String,
    val rules: TerritoryRulesDto,
    val myTeamCircleId: String? = null,
    val leaderboard: List<TerritoryRankDto>,
    val history: List<TerritoryHistoryDto>,
)

@Serializable data class GameEventDto(val id: String, val code: String, val name: String, val description: String, val kind: String, val startsAt: String, val endsAt: String, val live: Boolean)

// ── notifications ──
@Serializable data class NotificationDto(val id: String, val kind: String, val title: String, val body: String, val data: JsonObject = JsonObject(emptyMap()), val read: Boolean, val createdAt: String)
@Serializable data class NotificationPageDto(val items: List<NotificationDto>, val nextCursor: String? = null, val unread: Int)
@Serializable data class MarkReadRequest(val ids: List<String>? = null)
