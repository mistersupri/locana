import Foundation

/// One method per endpoint the iOS app uses. Paths and bodies match docs/api.md and the Android app's LocanaApi.
extension APIClient {
    // auth
    public func register(_ b: RegisterRequest) async throws -> AuthResponse { try await send("POST", "v1/auth/register", body: b, auth: false) }
    public func login(_ b: LoginRequest) async throws -> AuthResponse { try await send("POST", "v1/auth/login", body: b, auth: false) }
    public func forgotPassword(_ email: String) async throws { try await sendVoid("POST", "v1/auth/password/forgot", body: ForgotRequest(email: email), auth: false) }
    public func resetPassword(_ b: ResetRequest) async throws { try await sendVoid("POST", "v1/auth/password/reset", body: b, auth: false) }
    public func changePassword(_ b: ChangePasswordRequest) async throws { try await sendVoid("POST", "v1/auth/password/change", body: b) }
    public func logout() async throws { try await sendVoid("POST", "v1/auth/logout") }

    // me
    public func me() async throws -> MeDto { try await send("GET", "v1/me") }
    public func updateMe(_ b: UpdateProfileRequest) async throws -> MeDto { try await send("PATCH", "v1/me", body: b) }
    public func updatePrivacy(_ b: PrivacyPatch) async throws -> PrivacyDto { try await send("PATCH", "v1/me/privacy", body: b) }
    public func setCosmetics(frame: String?, title: String?, marker: String?) async throws -> MeDto { try await send("PUT", "v1/me/cosmetics", body: CosmeticsRequest(avatarFrameKey: frame, titleKey: title, markerKey: marker)) }
    public func registerDevice(_ b: DeviceRequest) async throws { try await sendVoid("POST", "v1/me/devices", body: b) }

    /// A person's photo bytes (JPEG), or an error if they have none.
    public func photo(userId: String) async throws -> Data { try await raw("GET", "v1/users/\(userId)/avatar-photo", query: [], body: nil, auth: true, headers: [:]) }

    // circles
    public func circles() async throws -> [CircleSummaryDto] { try await send("GET", "v1/circles") }
    public func circle(_ id: String) async throws -> CircleDetailDto { try await send("GET", "v1/circles/\(id)") }
    public func createCircle(_ name: String) async throws -> CircleDetailDto { try await send("POST", "v1/circles", body: CreateCircleRequest(name: name)) }
    public func joinCircle(code: String) async throws -> CircleDetailDto { try await send("POST", "v1/circles/join", body: JoinRequest(code: code)) }
    public func leaveCircle(_ id: String) async throws { try await sendVoid("POST", "v1/circles/\(id)/leave") }
    public func invitations(_ circleId: String) async throws -> [InvitationDto] { try await send("GET", "v1/circles/\(circleId)/invitations") }
    public func createInvitation(_ circleId: String) async throws -> InvitationDto {
        try await send("POST", "v1/circles/\(circleId)/invitations", body: CreateInvitationRequest(expiresInHours: 72, maxUses: 5))
    }

    // location and sharing
    public func upload(_ samples: [SampleDto]) async throws -> UploadResponse { try await send("POST", "v1/location", body: UploadRequest(samples: samples)) }
    public func locations(circleId: String) async throws -> [MemberLocationDto] { try await send("GET", "v1/circles/\(circleId)/locations") }
    public func timeline(circleId: String, userId: String, day: String, tzOffsetMinutes: Int) async throws -> TimelineDto {
        try await send("GET", "v1/circles/\(circleId)/members/\(userId)/timeline", query: [("day", day), ("tzOffsetMinutes", String(tzOffsetMinutes))])
    }
    public func sharing() async throws -> SharingStateDto { try await send("GET", "v1/sharing") }
    public func pauseAll(_ paused: Bool) async throws -> SharingStateDto { try await send("PUT", "v1/sharing/pause-all", body: PauseAllRequest(paused: paused)) }
    public func places(circleId: String) async throws -> [PlaceDto] { try await send("GET", "v1/circles/\(circleId)/places") }

    // game
    public func quests() async throws -> [QuestDto] { try await send("GET", "v1/quests") }
    public func claimQuest(_ id: String, key: String) async throws -> ClaimResultDto { try await send("POST", "v1/quests/\(id)/claim", headers: ["Idempotency-Key": key]) }
    public func achievements() async throws -> [AchievementDto] { try await send("GET", "v1/achievements") }
    public func collectibles() async throws -> [CollectibleDto] { try await send("GET", "v1/collectibles") }
    public func stats() async throws -> StatsDto { try await send("GET", "v1/exploration/stats") }
    public func events() async throws -> [GameEventDto] { try await send("GET", "v1/events") }
    public func landmarks(lat: Double, lon: Double, query: String? = nil) async throws -> [LandmarkDto] {
        try await send("GET", "v1/landmarks", query: [("lat", String(lat)), ("lon", String(lon)), ("q", query)])
    }
    public func claimLandmark(_ id: String, lat: Double, lon: Double, accuracyM: Double) async throws -> ClaimLandmarkResultDto {
        try await send("POST", "v1/landmarks/\(id)/claim", body: ClaimLandmarkBody(latitude: lat, longitude: lon, accuracyM: accuracyM))
    }

    // notifications
    public func notifications(cursor: String? = nil) async throws -> NotificationPageDto {
        try await send("GET", "v1/notifications", query: [("cursor", cursor), ("limit", "30")])
    }
    public func markRead(ids: [String]? = nil) async throws { try await sendVoid("POST", "v1/notifications/read", body: MarkReadRequest(ids: ids)) }

    // safety
    public func emergencyContacts() async throws -> [EmergencyContactDto] { try await send("GET", "v1/emergency-contacts") }
    public func setEmergencyContacts(_ list: [EmergencyContactDto]) async throws -> [EmergencyContactDto] {
        try await send("PUT", "v1/emergency-contacts", body: SetContactsRequest(contacts: list))
    }
    public func sos(_ b: SosRequest) async throws -> SosResultDto { try await send("POST", "v1/sos", body: b) }
    public func quickMessages() async throws -> [QuickMessageDefDto] { try await send("GET", "v1/quick-messages") }
    public func sendQuickMessage(circleId: String, userId: String, code: String) async throws -> QuickMessageResultDto {
        try await send("POST", "v1/circles/\(circleId)/members/\(userId)/quick-message", body: QuickMessageRequest(code: code))
    }

    // monsters and shop
    public func battleActive() async throws -> BattleStateDto { try await send("GET", "v1/battle/active") }
    public func attack(_ id: String, weaponCode: String?) async throws -> AttackResultDto { try await send("POST", "v1/battle/\(id)/attack", body: AttackRequest(weaponCode: weaponCode)) }
    public func flee(_ id: String) async throws { try await sendVoid("POST", "v1/battle/\(id)/flee") }
    public func shop() async throws -> ShopDto { try await send("GET", "v1/shop") }
    public func buy(_ code: String) async throws -> ShopDto { try await send("POST", "v1/shop/buy", body: BuyRequest(code: code)) }
}
