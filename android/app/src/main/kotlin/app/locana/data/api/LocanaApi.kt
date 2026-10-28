package app.locana.data.api

import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** Retrofit description of backend /v1. See docs/api.md for semantics and error codes. */
interface LocanaApi {
    // auth
    @POST("v1/auth/register") suspend fun register(@Body body: RegisterRequest): AuthResponse
    @POST("v1/auth/login") suspend fun login(@Body body: LoginRequest): AuthResponse
    @POST("v1/auth/google") suspend fun google(@Body body: GoogleRequest): AuthResponse
    @POST("v1/auth/password/forgot") suspend fun forgotPassword(@Body body: ForgotRequest): Response<Unit>
    @POST("v1/auth/password/reset") suspend fun resetPassword(@Body body: ResetRequest): Response<Unit>
    @POST("v1/auth/password/change") suspend fun changePassword(@Body body: ChangePasswordRequest): Response<Unit>
    @POST("v1/auth/logout") suspend fun logout(): Response<Unit>
    @POST("v1/auth/logout-others") suspend fun logoutOthers(): Response<Unit>
    @GET("v1/auth/sessions") suspend fun sessions(): List<SessionDto>
    @POST("v1/auth/sessions/{id}/revoke") suspend fun revokeSession(@Path("id") id: String): Response<Unit>

    // me
    @GET("v1/me") suspend fun me(): MeDto
    @PATCH("v1/me") suspend fun updateMe(@Body body: UpdateProfileRequest): MeDto
    @PUT("v1/me/cosmetics") suspend fun cosmetics(@Body body: CosmeticsRequest): MeDto
    @PATCH("v1/me/privacy") suspend fun privacy(@Body body: PrivacyPatch): PrivacyDto
    @POST("v1/me/devices") suspend fun registerDevice(@Body body: DeviceRequest): Response<Unit>
    @GET("v1/me/export") suspend fun export(): JsonObject
    @POST("v1/me/delete") suspend fun deleteAccount(@Body body: DeleteAccountRequest): Response<Unit>
    @GET("v1/me/blocks") suspend fun blocks(): List<BlockDto>
    @PUT("v1/users/{id}/block") suspend fun block(@Path("id") id: String): Response<Unit>
    @DELETE("v1/users/{id}/block") suspend fun unblock(@Path("id") id: String): Response<Unit>
    @POST("v1/users/{id}/report") suspend fun report(@Path("id") id: String, @Body body: ReportRequest): ReportResponse
    @GET("v1/me/location-history") suspend fun history(@Query("from") from: String?, @Query("to") to: String?): List<HistoryPointDto>
    @DELETE("v1/me/location-history") suspend fun deleteHistory(): DeletedDto

    // circles
    @GET("v1/circles") suspend fun circles(): List<CircleSummaryDto>
    @POST("v1/circles") suspend fun createCircle(@Body body: CreateCircleRequest): CircleDetailDto
    @GET("v1/circles/{id}") suspend fun circle(@Path("id") id: String): CircleDetailDto
    @PATCH("v1/circles/{id}") suspend fun updateCircle(@Path("id") id: String, @Body body: UpdateCircleRequest): CircleDetailDto
    @DELETE("v1/circles/{id}") suspend fun deleteCircle(@Path("id") id: String): Response<Unit>
    @POST("v1/circles/{id}/leave") suspend fun leaveCircle(@Path("id") id: String): Response<Unit>
    @POST("v1/circles/join") suspend fun join(@Body body: JoinRequest): CircleDetailDto
    @GET("v1/circles/{id}/invitations") suspend fun invitations(@Path("id") id: String): List<InvitationDto>
    @POST("v1/circles/{id}/invitations") suspend fun createInvitation(@Path("id") id: String, @Body body: CreateInvitationRequest): InvitationDto
    @DELETE("v1/circles/{id}/invitations/{inv}") suspend fun revokeInvitation(@Path("id") id: String, @Path("inv") inv: String): Response<Unit>
    @PUT("v1/circles/{id}/members/{userId}/role") suspend fun setRole(@Path("id") id: String, @Path("userId") userId: String, @Body body: RoleRequest): CircleDetailDto
    @DELETE("v1/circles/{id}/members/{userId}") suspend fun removeMember(@Path("id") id: String, @Path("userId") userId: String): Response<Unit>
    @PATCH("v1/circles/{id}/me") suspend fun mute(@Path("id") id: String, @Body body: MuteRequest): Response<Unit>
    @PUT("v1/circles/{id}/visibility") suspend fun visibility(@Path("id") id: String, @Body body: VisibilityRequest): Response<Unit>
    @GET("v1/circles/{id}/feed") suspend fun feed(@Path("id") id: String, @Query("cursor") cursor: String?): FeedPageDto
    @PUT("v1/circles/feed/{itemId}/reaction") suspend fun react(@Path("itemId") itemId: String, @Body body: ReactionRequest): Response<Unit>
    @GET("v1/circles/{id}/challenges") suspend fun challenges(@Path("id") id: String): List<ChallengeDto>
    @POST("v1/circles/{id}/challenges") suspend fun createChallenge(@Path("id") id: String, @Body body: ChallengeRequest): ChallengeDto
    @GET("v1/circles/{id}/exploration") suspend fun circleExploration(@Path("id") id: String): CircleExplorationDto

    // location and sharing
    @POST("v1/location") suspend fun upload(@Body body: UploadRequest): UploadResponse
    @GET("v1/circles/{id}/locations") suspend fun locations(@Path("id") id: String): List<MemberLocationDto>
    @GET("v1/sharing") suspend fun sharing(): SharingStateDto
    @POST("v1/sharing") suspend fun startSharing(@Body body: StartSharingRequest): SharingStateDto
    @POST("v1/sharing/{id}/pause") suspend fun pauseSharing(@Path("id") id: String): SharingStateDto
    @POST("v1/sharing/{id}/resume") suspend fun resumeSharing(@Path("id") id: String): SharingStateDto
    @POST("v1/sharing/{id}/stop") suspend fun stopSharing(@Path("id") id: String): SharingStateDto
    @PUT("v1/sharing/pause-all") suspend fun pauseAll(@Body body: PauseAllRequest): SharingStateDto
    @POST("v1/sharing/stop-all") suspend fun stopAll(): SharingStateDto

    // places
    @GET("v1/circles/{id}/places") suspend fun places(@Path("id") circleId: String): List<PlaceDto>
    @POST("v1/circles/{id}/places") suspend fun createPlace(@Path("id") circleId: String, @Body body: PlaceRequest): PlaceDto
    @PATCH("v1/places/{id}") suspend fun updatePlace(@Path("id") id: String, @Body body: PlaceRequest): PlaceDto
    @DELETE("v1/places/{id}") suspend fun deletePlace(@Path("id") id: String): Response<Unit>

    // game
    @GET("v1/quests") suspend fun quests(): List<QuestDto>
    @GET("v1/quests/history") suspend fun questHistory(@Query("cursor") cursor: String?): QuestPageDto
    @GET("v1/quests/{id}") suspend fun quest(@Path("id") id: String): QuestDto
    @POST("v1/quests/{id}/claim") suspend fun claim(@Path("id") id: String, @Header("Idempotency-Key") key: String): ClaimResultDto
    @GET("v1/achievements") suspend fun achievements(): List<AchievementDto>
    @GET("v1/collectibles") suspend fun collectibles(): List<CollectibleDto>
    @GET("v1/levels") suspend fun levels(): List<LevelDto>
    @GET("v1/leaderboards") suspend fun leaderboard(
        @Query("scope") scope: String,
        @Query("metric") metric: String,
        @Query("period") period: String,
        @Query("circleId") circleId: String?,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
    ): LeaderboardDto
    @GET("v1/exploration/tiles") suspend fun tiles(@Query("north") north: Double, @Query("south") south: Double, @Query("east") east: Double, @Query("west") west: Double): TilesDto
    @GET("v1/exploration/stats") suspend fun stats(): StatsDto
    @GET("v1/landmarks") suspend fun landmarks(@Query("lat") lat: Double, @Query("lon") lon: Double, @Query("radiusM") radiusM: Int?, @Query("q") query: String?): List<LandmarkDto>
    @GET("v1/territories") suspend fun territories(@Query("north") north: Double, @Query("south") south: Double, @Query("east") east: Double, @Query("west") west: Double): List<TerritorySummaryDto>
    @GET("v1/territories/{id}") suspend fun territory(@Path("id") id: String): TerritoryDetailDto
    @GET("v1/events") suspend fun events(): List<GameEventDto>

    // notifications
    @GET("v1/notifications") suspend fun notifications(@Query("cursor") cursor: String?, @Query("limit") limit: Int?): NotificationPageDto
    @HTTP(method = "POST", path = "v1/notifications/read", hasBody = true) suspend fun markRead(@Body body: MarkReadRequest): Response<Unit>
}
