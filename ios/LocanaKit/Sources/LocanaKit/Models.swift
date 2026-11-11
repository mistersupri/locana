import Foundation

// Wire models for the backend's /v1 API (docs/api.md). Timestamps stay ISO-8601 strings and are parsed with `Iso`.
// Fields the server may leave out are optional, or arrays marked @Arr that read as empty when missing.

/// An array that reads as empty when the server leaves the key out.
@propertyWrapper
public struct Arr<T: Codable & Equatable>: Codable, Equatable {
    public var wrappedValue: [T]
    public init(wrappedValue: [T] = []) { self.wrappedValue = wrappedValue }
    public init(from decoder: Decoder) throws {
        let c = try decoder.singleValueContainer()
        wrappedValue = (try? c.decode([T].self)) ?? []
    }
    public func encode(to encoder: Encoder) throws {
        var c = encoder.singleValueContainer()
        try c.encode(wrappedValue)
    }
}

extension KeyedDecodingContainer {
    public func decode<T>(_ type: Arr<T>.Type, forKey key: Key) throws -> Arr<T> {
        try decodeIfPresent(type, forKey: key) ?? Arr(wrappedValue: [])
    }
}

public struct ErrorEnvelope: Codable { public struct Body: Codable { public let code: String; public let message: String }; public let error: Body }

// MARK: auth
public struct RegisterRequest: Codable { public var email, password, username, displayName: String; public var deviceName: String?
    public init(email: String, password: String, username: String, displayName: String, deviceName: String?) { self.email = email; self.password = password; self.username = username; self.displayName = displayName; self.deviceName = deviceName } }
public struct LoginRequest: Codable { public var identifier, password: String; public var deviceName: String?
    public init(identifier: String, password: String, deviceName: String?) { self.identifier = identifier; self.password = password; self.deviceName = deviceName } }
public struct RefreshRequest: Codable { public var refreshToken: String; public init(refreshToken: String) { self.refreshToken = refreshToken } }
public struct ForgotRequest: Codable { public var email: String; public init(email: String) { self.email = email } }
public struct ResetRequest: Codable { public var email, code, newPassword: String; public init(email: String, code: String, newPassword: String) { self.email = email; self.code = code; self.newPassword = newPassword } }
public struct ChangePasswordRequest: Codable { public var currentPassword, newPassword: String; public init(currentPassword: String, newPassword: String) { self.currentPassword = currentPassword; self.newPassword = newPassword } }
public struct TokenPair: Codable { public let accessToken: String; public let accessTokenExpiresIn: Int; public let refreshToken: String; public let sessionId: String }
public struct AuthResponse: Codable { public let tokens: TokenPair; public let user: MeDto }

// MARK: me
public struct MeDto: Codable, Equatable {
    public let id: String, email: String, username: String
    public let hasPassword: Bool, googleLinked: Bool
    public let createdAt: String
    public var profile: ProfileDto
    public var progress: ProgressDto
    public var privacy: PrivacyDto
}

public struct ProfileDto: Codable, Equatable {
    public var displayName: String
    public var avatarKey: String
    public var avatarPhotoVersion: Int64?
    public var avatarFrameKey: String?
    public var titleKey: String?
    public var markerKey: String?
    public var bio: String?
    public var onboardingDone: Bool
    public var teamCircleId: String?
}

public struct ProgressDto: Codable, Equatable {
    public let level: Int, totalXp: Int, lifetimeXp: Int, levelStartXp: Int
    public let nextLevelXp: Int?
    public let levelProgress: Double
    public let currentStreak: Int, longestStreak: Int, tilesDiscovered: Int, questsCompleted: Int, totalDistanceM: Int
}

public struct PrivacyDto: Codable, Equatable {
    public var sharingPaused: Bool
    public var historyEnabled: Bool
    public var historyRetentionDays: Int
    public var explorationEnabled: Bool
    public var showInLeaderboards: Bool
    public var leaderboardAlias: String?
    public var shareExplorationWithCircles: Bool
    public var notifyArrivals: Bool
    public var notifyQuests: Bool
    public var notifyAchievements: Bool
    public var notifyCircle: Bool
    public var notifyTrips: Bool?
}

public struct UpdateProfileRequest: Codable {
    public var displayName: String?, username: String?, avatarKey: String?, bio: String?
    public var onboardingDone: Bool?
    public var teamCircleId: String?
    public init(displayName: String? = nil, username: String? = nil, avatarKey: String? = nil, bio: String? = nil, onboardingDone: Bool? = nil, teamCircleId: String? = nil) {
        self.displayName = displayName; self.username = username; self.avatarKey = avatarKey; self.bio = bio; self.onboardingDone = onboardingDone; self.teamCircleId = teamCircleId
    }
}

public struct PrivacyPatch: Codable {
    public var historyEnabled: Bool?, explorationEnabled: Bool?, showInLeaderboards: Bool?
    public var notifyArrivals: Bool?, notifyQuests: Bool?, notifyAchievements: Bool?, notifyCircle: Bool?, notifyTrips: Bool?
    public init(historyEnabled: Bool? = nil, explorationEnabled: Bool? = nil, showInLeaderboards: Bool? = nil, notifyArrivals: Bool? = nil, notifyQuests: Bool? = nil, notifyAchievements: Bool? = nil, notifyCircle: Bool? = nil, notifyTrips: Bool? = nil) {
        self.historyEnabled = historyEnabled; self.explorationEnabled = explorationEnabled; self.showInLeaderboards = showInLeaderboards
        self.notifyArrivals = notifyArrivals; self.notifyQuests = notifyQuests; self.notifyAchievements = notifyAchievements; self.notifyCircle = notifyCircle; self.notifyTrips = notifyTrips
    }
}

/// Sent with explicit nulls, so unequipping is possible.
public struct CosmeticsRequest: Encodable {
    public var avatarFrameKey: String?, titleKey: String?, markerKey: String?
    public init(avatarFrameKey: String?, titleKey: String?, markerKey: String?) { self.avatarFrameKey = avatarFrameKey; self.titleKey = titleKey; self.markerKey = markerKey }
    enum CodingKeys: String, CodingKey { case avatarFrameKey, titleKey, markerKey }
    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(avatarFrameKey, forKey: .avatarFrameKey)
        try c.encode(titleKey, forKey: .titleKey)
        try c.encode(markerKey, forKey: .markerKey)
    }
}

public struct DeviceRequest: Codable { public var installId: String; public var model: String?; public var appVersion: String?
    public init(installId: String, model: String?, appVersion: String?) { self.installId = installId; self.model = model; self.appVersion = appVersion } }

// MARK: circles
public struct CircleSummaryDto: Codable, Equatable, Identifiable {
    public let id: String, name: String, avatarKey: String
    public let memberCount: Int
    public let myRole: String
    public let muted: Bool, iAmSharing: Bool
}

public struct CircleDetailDto: Codable, Equatable {
    public let id: String, name: String, avatarKey: String, createdAt: String, myRole: String
    public let muted: Bool
    @Arr public var hiddenFromViewerIds: [String]
    public let members: [MemberDto]
}

public struct MemberDto: Codable, Equatable, Identifiable {
    public var id: String { userId }
    public let userId: String, username: String, displayName: String
    public let avatarKey: String?
    public let avatarPhotoVersion: Int64?
    public let avatarFrameKey: String?
    public let titleKey: String?
    public let level: Int
    public let role: String, joinedAt: String
    public let sharingWithMe: Bool?
    public let blockedByMe: Bool

    enum CodingKeys: String, CodingKey { case userId, username, displayName, avatarKey, avatarPhotoVersion, avatarFrameKey, titleKey, level, role, joinedAt, sharingWithMe, blockedByMe }
    public init(from d: Decoder) throws {
        let c = try d.container(keyedBy: CodingKeys.self)
        userId = try c.decode(String.self, forKey: .userId)
        username = try c.decode(String.self, forKey: .username)
        displayName = try c.decode(String.self, forKey: .displayName)
        avatarKey = try c.decodeIfPresent(String.self, forKey: .avatarKey)
        avatarPhotoVersion = try c.decodeIfPresent(Int64.self, forKey: .avatarPhotoVersion)
        avatarFrameKey = try c.decodeIfPresent(String.self, forKey: .avatarFrameKey)
        titleKey = try c.decodeIfPresent(String.self, forKey: .titleKey)
        level = try c.decodeIfPresent(Int.self, forKey: .level) ?? 1
        role = try c.decode(String.self, forKey: .role)
        joinedAt = try c.decode(String.self, forKey: .joinedAt)
        sharingWithMe = try c.decodeIfPresent(Bool.self, forKey: .sharingWithMe)
        blockedByMe = try c.decodeIfPresent(Bool.self, forKey: .blockedByMe) ?? false
    }
}

public struct CreateCircleRequest: Codable { public var name: String; public var avatarKey: String?
    public init(name: String, avatarKey: String? = nil) { self.name = name; self.avatarKey = avatarKey } }
public struct CreateInvitationRequest: Codable { public var expiresInHours: Int; public var maxUses: Int
    public init(expiresInHours: Int, maxUses: Int) { self.expiresInHours = expiresInHours; self.maxUses = maxUses } }
public struct InvitationDto: Codable, Equatable, Identifiable {
    public let id: String, codeHint: String
    public let maxUses: Int, uses: Int
    public let expiresAt: String, createdAt: String, status: String
    public let code: String?, deepLink: String?
}
public struct JoinRequest: Codable { public var code: String; public init(code: String) { self.code = code } }

// MARK: location
public struct LocationDto: Codable, Equatable {
    public let latitude: Double, longitude: Double, accuracyM: Double
    public let speedMps: Double?, bearingDeg: Double?
    public let recordedAt: String
    public let batteryPct: Int?
    public let activity: String?, movement: String?, mode: String?, stationarySince: String?
}
public struct MemberLocationDto: Codable, Equatable { public let userId: String, status: String; public let location: LocationDto? }

public struct SampleDto: Codable {
    public var latitude: Double, longitude: Double, accuracyM: Double
    public var recordedAt: String
    public var sequence: Int64
    public var speedMps: Double?, bearingDeg: Double?, altitudeM: Double?
    public var source: String
    public var activity: String?
    public var isMock: Bool?
    public var batteryPct: Int?
    public var driveEvent: String?
    public init(latitude: Double, longitude: Double, accuracyM: Double, recordedAt: String, sequence: Int64, speedMps: Double? = nil, bearingDeg: Double? = nil, altitudeM: Double? = nil, source: String, activity: String? = nil, isMock: Bool? = nil, batteryPct: Int? = nil, driveEvent: String? = nil) {
        self.latitude = latitude; self.longitude = longitude; self.accuracyM = accuracyM; self.recordedAt = recordedAt; self.sequence = sequence
        self.speedMps = speedMps; self.bearingDeg = bearingDeg; self.altitudeM = altitudeM; self.source = source; self.activity = activity
        self.isMock = isMock; self.batteryPct = batteryPct; self.driveEvent = driveEvent
    }
}
public struct UploadRequest: Codable { public var samples: [SampleDto]; public init(samples: [SampleDto]) { self.samples = samples } }
public struct RejectedDto: Codable, Equatable { public let index: Int; public let reason: String }
public struct LevelUpDto: Codable, Equatable { public let from: Int, to: Int }
public struct AchievementUnlockDto: Codable, Equatable { public let code: String, name: String, rarity: String; public let rewardXp: Int }
public struct GameResultDto: Codable, Equatable {
    public let xpGained: Int?
    @Arr public var newTiles: [String]
    @Arr public var levelUps: [LevelUpDto]
    @Arr public var achievements: [AchievementUnlockDto]
}
public struct UploadResponse: Codable {
    public let accepted: Int
    @Arr public var rejected: [RejectedDto]
    public let latestRecordedAt: String?
    public let sharingActive: Bool
    public let game: GameResultDto?
}

public struct SharingSessionDto: Codable, Equatable, Identifiable {
    public let id: String, circleId: String
    public let circleName: String?
    public let status: String, startedAt: String
    public let expiresAt: String?, pausedAt: String?, endedAt: String?, endReason: String?
}
public struct SharingStateDto: Codable, Equatable {
    public let globallyPaused: Bool, explorationEnabled: Bool, collectLocation: Bool
    public let sessions: [SharingSessionDto]
}
public struct PauseAllRequest: Codable { public var paused: Bool; public init(paused: Bool) { self.paused = paused } }

// MARK: timeline
public struct TimelinePlaceDto: Codable, Equatable { public let id: String, name: String, type: String }
public struct DriveEventDto: Codable, Equatable { public let kind: String; public let at: Int64; public let latitude: Double, longitude: Double }
public struct DriveEventCountsDto: Codable, Equatable {
    public let hardBrake: Int, rapidAccel: Int, phoneUse: Int
    enum CodingKeys: String, CodingKey { case hardBrake, rapidAccel, phoneUse }
    public init(from d: Decoder) throws {
        let c = try d.container(keyedBy: CodingKeys.self)
        hardBrake = try c.decodeIfPresent(Int.self, forKey: .hardBrake) ?? 0
        rapidAccel = try c.decodeIfPresent(Int.self, forKey: .rapidAccel) ?? 0
        phoneUse = try c.decodeIfPresent(Int.self, forKey: .phoneUse) ?? 0
    }
    public init(hardBrake: Int = 0, rapidAccel: Int = 0, phoneUse: Int = 0) { self.hardBrake = hardBrake; self.rapidAccel = rapidAccel; self.phoneUse = phoneUse }
}
public struct TimelineSegmentDto: Codable, Equatable {
    public let type: String, startedAt: String, endedAt: String
    public let durationSec: Int
    public let distanceM: Int?
    public let avgSpeedKmh: Double?
    public let maxSpeedKmh: Int?
    public let mode: String?
    @Arr public var path: [[Double]]
    public let latitude: Double?, longitude: Double?
    public let place: TimelinePlaceDto?
    @Arr public var pathAt: [Int64]
    @Arr public var events: [DriveEventDto]
    public let eventCounts: DriveEventCountsDto?
}
public struct TimelineDto: Codable, Equatable { public let day: String; public let points: Int?; public let segments: [TimelineSegmentDto] }

// MARK: places
public struct PlaceDto: Codable, Equatable, Identifiable {
    public let id: String, circleId: String, name: String, type: String
    public let latitude: Double, longitude: Double
    public let radiusM: Int
}

// MARK: game
public struct QuestDto: Codable, Equatable, Identifiable {
    public let id: String, questCode: String, name: String, description: String, type: String, condition: String, unit: String
    public let target: Int, progress: Int
    public let status: String
    public let rewardXp: Int
    public let rewardCollectibleCode: String?
    public let circleId: String?
    public let startedAt: String, expiresAt: String
    public let completedAt: String?, claimedAt: String?
}
public struct CollectibleRefDto: Codable, Equatable { public let code: String, name: String }
public struct ClaimResultDto: Codable, Equatable {
    public let quest: QuestDto
    public let xpAwarded: Int
    public let alreadyClaimed: Bool
    public let totalXp: Int, level: Int
    @Arr public var levelUps: [LevelUpDto]
    @Arr public var achievements: [AchievementUnlockDto]
    @Arr public var collectibles: [CollectibleRefDto]
}
public struct AchievementDto: Codable, Equatable, Identifiable {
    public var id: String { code }
    public let code: String, name: String, description: String, rarity: String, iconKey: String
    public let rewardXp: Int
    public let unlockedAt: String?
    public let progress: Int, target: Int
}
public struct CollectibleDto: Codable, Equatable, Identifiable {
    public var id: String { code }
    public let code: String, name: String, description: String, kind: String, rarity: String
    public let setCode: String?
    public let iconKey: String
    public let acquiredAt: String?
}
public struct StatsDto: Codable, Equatable {
    public let tilesDiscovered: Int, landmarksVisited: Int, totalDistanceM: Int, currentStreak: Int, longestStreak: Int, questsCompleted: Int
}
public struct LandmarkDto: Codable, Equatable, Identifiable {
    public let id: String, code: String, name: String, description: String, category: String
    public let latitude: Double, longitude: Double
    public let radiusM: Int, rewardXp: Int
    public let setCode: String?
    public let visited: Bool
    public let territoryId: String?
    public let distanceM: Int
}
public struct ClaimLandmarkBody: Codable { public var latitude: Double, longitude: Double, accuracyM: Double
    public init(latitude: Double, longitude: Double, accuracyM: Double) { self.latitude = latitude; self.longitude = longitude; self.accuracyM = accuracyM } }
public struct ClaimLandmarkResultDto: Codable, Equatable { public let id: String, name: String; public let firstVisit: Bool; public let rewardXp: Int }
public struct GameEventDto: Codable, Equatable, Identifiable { public let id: String, code: String, name: String, description: String, kind: String, startsAt: String, endsAt: String; public let live: Bool }

// MARK: notifications
public struct NotificationDto: Codable, Equatable, Identifiable {
    public let id: String, kind: String, title: String, body: String
    public let data: JSONValue?
    public let read: Bool
    public let createdAt: String
}
public struct NotificationPageDto: Codable { public let items: [NotificationDto]; public let nextCursor: String?; public let unread: Int }
public struct MarkReadRequest: Codable { public var ids: [String]?; public init(ids: [String]? = nil) { self.ids = ids } }

// MARK: safety
public struct EmergencyContactDto: Codable, Equatable, Identifiable {
    public var id: String { serverId ?? (name + phone) }
    public var serverId: String?
    public var name: String, phone: String
    public var email: String?
    enum CodingKeys: String, CodingKey { case serverId = "id", name, phone, email }
    public init(serverId: String? = nil, name: String, phone: String, email: String? = nil) { self.serverId = serverId; self.name = name; self.phone = phone; self.email = email }
}
public struct SetContactsRequest: Codable { public var contacts: [EmergencyContactDto]; public init(contacts: [EmergencyContactDto]) { self.contacts = contacts } }
public struct SosRequest: Codable { public var reason: String, latitude: Double, longitude: Double; public var accuracyM: Double?
    public init(reason: String, latitude: Double, longitude: Double, accuracyM: Double?) { self.reason = reason; self.latitude = latitude; self.longitude = longitude; self.accuracyM = accuracyM } }
public struct SosCallDto: Codable, Equatable { public let name: String, phone: String }
public struct SosResultDto: Codable, Equatable { public let id: String; public let notified: Int, emailed: Int; @Arr public var contacts: [SosCallDto] }
public struct QuickMessageDefDto: Codable, Equatable, Identifiable { public var id: String { code }; public let code: String, text: String, emoji: String
    public init(code: String, text: String, emoji: String) { self.code = code; self.text = text; self.emoji = emoji } }
public struct QuickMessageRequest: Codable { public var code: String; public init(code: String) { self.code = code } }
public struct QuickMessageResultDto: Codable, Equatable { public let sent: Bool, delivered: Bool; public let code: String, text: String, emoji: String }

// MARK: monsters, weapons, shop
public struct MonsterDto: Codable, Equatable { public let code: String, name: String, emoji: String }
public struct EncounterDto: Codable, Equatable, Identifiable {
    public let id: String, status: String
    public let monster: MonsterDto
    public let hp: Int, maxHp: Int, playerHp: Int, maxPlayerHp: Int, rewardCoins: Int, rewardXp: Int
    public let expiresAt: String
}
public struct OwnedWeaponDto: Codable, Equatable, Identifiable { public var id: String { code }; public let code: String, name: String, emoji: String; public let power: Int }
public struct BattleStateDto: Codable, Equatable {
    public let encounter: EncounterDto?
    public let coins: Int
    @Arr public var weapons: [OwnedWeaponDto]
    public init(encounter: EncounterDto?, coins: Int, weapons: [OwnedWeaponDto]) { self.encounter = encounter; self.coins = coins; self.weapons = weapons }
}
public struct AttackRequest: Codable { public var weaponCode: String?; public init(weaponCode: String?) { self.weaponCode = weaponCode } }
public struct HitDto: Codable, Equatable {
    public let damage: Int, crit: Bool
    enum CodingKeys: String, CodingKey { case damage, crit }
    public init(from d: Decoder) throws {
        let c = try d.container(keyedBy: CodingKeys.self)
        damage = try c.decode(Int.self, forKey: .damage)
        crit = try c.decodeIfPresent(Bool.self, forKey: .crit) ?? false
    }
}
public struct BattleRewardsDto: Codable, Equatable { public let coins: Int, xp: Int; @Arr public var levelUps: [LevelUpDto] }
public struct AttackResultDto: Codable, Equatable {
    public let outcome: String
    public let hit: HitDto
    public let counter: HitDto?
    public let encounter: EncounterDto
    public let coins: Int
    public let rewards: BattleRewardsDto?
}
public struct FleeDto: Codable { public let fled: Bool? }
public struct ShopWeaponDto: Codable, Equatable, Identifiable {
    public var id: String { code }
    public let code: String, name: String, emoji: String
    public let power: Int, costCoins: Int, minLevel: Int
    public let owned: Bool, affordable: Bool, unlocked: Bool
}
public struct ShopDto: Codable, Equatable { public let coins: Int, level: Int; public let weapons: [ShopWeaponDto] }
public struct BuyRequest: Codable { public var code: String; public init(code: String) { self.code = code } }
