import Foundation
import CoreLocation
import UIKit
import SwiftUI
import LocanaKit

struct MemberPin: Identifiable, Equatable {
    var id: String { userId }
    let userId: String
    let name: String
    let avatarKey: String?
    let photoVersion: Int64?
    let frameKey: String?
    let latitude: Double?
    let longitude: Double?
    let accuracyM: Double?
    let presence: Presence
    let movement: String        // STAYING, MOVING, UNKNOWN
    let mode: String?           // DRIVING, CYCLING, WALKING
    let speedMps: Double?
    let stationarySinceMs: Int64?
    let recordedAtMs: Int64?
    let batteryPct: Int?
    let isMe: Bool
    let blockedByMe: Bool
    var coordinate: CLLocationCoordinate2D? { latitude.flatMap { la in longitude.map { CLLocationCoordinate2D(latitude: la, longitude: $0) } } }
}

struct IncomingQuickMessage: Identifiable, Equatable {
    let id = UUID()
    let fromName: String
    let text: String
    let emoji: String
    let code: String
}

enum SosUi: Equatable {
    case idle
    case sending
    case sent(notified: Int, emailed: Int, calling: String?)
    case failed(String)
}

enum AppTab: Hashable { case map, explore, play, circle }

@MainActor
final class AppModel: ObservableObject {
    enum Session { case loading, signedOut, signedIn }

    // session
    @Published var session: Session = .loading
    @Published var notice: String?
    @Published var me: MeDto?
    // circles and the map
    @Published var circles: [CircleSummaryDto] = []
    @Published var selectedCircleId: String?
    @Published var detail: CircleDetailDto?
    @Published var locations: [String: MemberLocationDto] = [:]
    @Published var places: [PlaceDto] = []
    @Published var sharing: SharingStateDto?
    @Published var tick = Date()
    // inbox and game
    @Published var unread = 0
    @Published var encounter: EncounterDto?
    @Published var coins = 0
    @Published var incoming: IncomingQuickMessage?
    @Published var gameToast: String?
    // safety
    @Published var crashSecondsLeft: Int?
    @Published var sos: SosUi = .idle
    // navigation
    @Published var selectedTab: AppTab = .map
    @Published var pendingFocusUserId: String?
    @Published var showBattle = false

    let tokens = KeychainTokenStore()
    let api: APIClient
    let photos = PhotoStore()
    let location = LocationService()
    let motion = MotionService()
    private let realtime = RealtimeClient()
    private var movingUntil: [String: Date] = [:]
    private var crashTask: Task<Void, Never>?
    private var clock: Timer?

    init() {
        api = APIClient(baseURL: AppConfig.apiURL, store: tokens)
        photos.api = api
        location.api = api
        location.motion = motion
        api.onSignedOut = { [weak self] message in Task { @MainActor in self?.endSession(message) } }
        api.onRefreshed = { [weak self] token in Task { @MainActor in self?.realtime.reauthenticate(token) } }
        realtime.tokenProvider = { [weak self] in self?.api.currentAccessToken }
        realtime.refresh = { [weak self] in
            guard let self else { return nil }
            return await self.api.refresh(stale: self.api.currentAccessToken)
        }
        realtime.onEvent = { [weak self] type, data in self?.handle(type: type, data: data) }
        motion.onCrash = { [weak self] in self?.beginCrashCountdown() }
        location.onNotNeeded = { [weak self] in Task { await self?.refreshSharing() } }
        location.onGame = { [weak self] g in self?.showGame(g) }
    }

    // MARK: session

    func bootstrap() async {
        guard api.isSignedIn else { session = .signedOut; return }
        if let cached = Cache.load(MeDto.self, "me") { me = cached; session = .signedIn }
        do {
            let fresh = try await api.me()
            me = fresh
            Cache.save(fresh, "me")
            if session != .signedIn { session = .signedIn }
            await afterSignIn()
        } catch let e as APIError {
            if e == .offline, me != nil { await afterSignIn() }
            else if session != .signedOut { /* signed out by the refresh failure already */ if !api.isSignedIn { session = .signedOut } }
        } catch {}
    }

    func signIn(identifier: String, password: String) async throws {
        let r = try await api.login(LoginRequest(identifier: identifier, password: password, deviceName: UIDevice.current.name))
        finishSignIn(r)
    }

    func register(email: String, password: String, username: String, name: String) async throws {
        let r = try await api.register(RegisterRequest(email: email, password: password, username: username, displayName: name, deviceName: UIDevice.current.name))
        finishSignIn(r)
    }

    private func finishSignIn(_ r: AuthResponse) {
        api.saveSignIn(r)
        me = r.user
        Cache.save(r.user, "me")
        notice = nil
        session = .signedIn
        Task { await afterSignIn() }
    }

    func signOut() async {
        try? await api.logout()
        endSession(nil)
    }

    private func endSession(_ message: String?) {
        api.clearSignIn()
        realtime.stop()
        location.stop()
        motion.stop()
        clock?.invalidate()
        photos.clear()
        Cache.clear()
        me = nil; circles = []; selectedCircleId = nil; detail = nil; locations = [:]; places = []; sharing = nil
        unread = 0; encounter = nil; coins = 0
        notice = message
        session = .signedOut
    }

    private func afterSignIn() async {
        LocalNotifier.requestPermission()
        motion.start()
        realtime.start()
        clock?.invalidate()
        clock = Timer.scheduledTimer(withTimeInterval: 15, repeats: true) { [weak self] _ in
            Task { @MainActor in
                guard let self else { return }
                self.tick = Date()
                self.location.flushIfDue()
            }
        }
        try? await api.registerDevice(DeviceRequest(installId: Self.installId, model: UIDevice.current.model, appVersion: AppConfig.version))
        await refreshCircles()
        await refreshSharing()
        await refreshBattle()
        if let page = try? await api.notifications() { unread = page.unread }
    }

    private static var installId: String {
        if let id = UserDefaults.standard.string(forKey: "locana.install") { return id }
        let id = UUID().uuidString.replacingOccurrences(of: "-", with: "").lowercased()
        UserDefaults.standard.set(id, forKey: "locana.install")
        return id
    }

    func refreshMe() async {
        if let fresh = try? await api.me() { me = fresh; Cache.save(fresh, "me") }
    }

    // MARK: circles

    func refreshCircles() async {
        guard let list = try? await api.circles() else { return }
        circles = list
        realtime.subscribe(list.map(\.id))
        if selectedCircleId == nil || !list.contains(where: { $0.id == selectedCircleId }) { selectedCircleId = list.first?.id }
        await loadSelected()
    }

    func select(_ id: String) async {
        selectedCircleId = id
        await loadSelected()
    }

    func loadSelected() async {
        guard let id = selectedCircleId else { detail = nil; locations = [:]; places = []; return }
        async let d = try? api.circle(id)
        async let l = try? api.locations(circleId: id)
        async let p = try? api.places(circleId: id)
        let (detailNow, locs, pl) = await (d, l, p)
        if selectedCircleId != id { return }
        detail = detailNow
        if let locs { locations = Dictionary(uniqueKeysWithValues: locs.map { ($0.userId, $0) }) }
        places = pl ?? []
    }

    func refreshLocations() async {
        guard let id = selectedCircleId, let locs = try? await api.locations(circleId: id) else { return }
        locations = Dictionary(uniqueKeysWithValues: locs.map { ($0.userId, $0) })
    }

    func refreshSharing() async {
        guard let s = try? await api.sharing() else { return }
        sharing = s
        location.configure(sharing: !s.globallyPaused && s.sessions.contains { $0.status == "ACTIVE" }, exploration: s.explorationEnabled)
        if s.collectLocation { location.requestPermission() }
        motion.crashEnabled = UserDefaults.standard.object(forKey: "locana.crash") as? Bool ?? true
    }

    // MARK: pins

    /// The circle's members as pins: the server's positions, with this phone's own fix and motion for you.
    var pins: [MemberPin] {
        guard let detail else { return [] }
        let now = Int64(tick.timeIntervalSince1970 * 1000)
        let myId = me?.id
        return detail.members.map { m in
            let isMe = m.userId == myId
            let row = locations[m.userId]
            let loc = row?.location
            var lat = loc?.latitude, lon = loc?.longitude, acc = loc?.accuracyM
            var recorded = loc.flatMap { Iso.ms($0.recordedAt) }
            var movement = loc?.movement ?? "UNKNOWN"
            var mode = loc?.mode
            var speed = loc?.speedMps
            var battery = loc?.batteryPct
            var serverStatus = Presence(rawValue: (row?.status ?? "UNAVAILABLE").lowercased()) ?? .unavailable
            if isMe, let own = location.latest, now - Int64(own.timestamp.timeIntervalSince1970 * 1000) < 10 * 60_000, Int64(own.timestamp.timeIntervalSince1970 * 1000) >= (recorded ?? 0) {
                lat = own.coordinate.latitude; lon = own.coordinate.longitude; acc = own.horizontalAccuracy
                recorded = Int64(own.timestamp.timeIntervalSince1970 * 1000)
                serverStatus = .live
                let level = UIDevice.current.batteryLevel
                if level >= 0 { battery = Int(level * 100) }
                // Your own pin does not wait for the server: a clear speed or the phone's activity is enough, held a moment so it does not flicker.
                let s = own.speed >= 0 && own.horizontalAccuracy <= 50 ? own.speed : 0
                let act = motion.currentActivity
                let activeKind = act.flatMap { ["vehicle": "DRIVING", "cycling": "CYCLING", "walking": "WALKING", "running": "WALKING"][$0] }
                if activeKind != nil || s >= 0.7 { movingUntil[m.userId] = Date().addingTimeInterval(25) }
                if act != "still" && (movingUntil[m.userId] ?? .distantPast) > Date() {
                    movement = "MOVING"
                    mode = activeKind ?? (s >= 5.5 ? "DRIVING" : "WALKING")
                    speed = own.speed >= 0 ? own.speed : nil
                }
            }
            let hidden = serverStatus == .hidden
            return MemberPin(
                userId: m.userId,
                name: isMe ? (me?.profile.displayName ?? m.displayName) : m.displayName,
                avatarKey: isMe ? me?.profile.avatarKey : m.avatarKey,
                photoVersion: isMe ? me?.profile.avatarPhotoVersion : m.avatarPhotoVersion,
                frameKey: isMe ? me?.profile.avatarFrameKey : m.avatarFrameKey,
                latitude: hidden ? nil : lat, longitude: hidden ? nil : lon, accuracyM: acc,
                presence: Freshness.presence(server: serverStatus, recordedAtMs: recorded, nowMs: now),
                movement: movement, mode: mode, speedMps: movement == "MOVING" ? speed : nil,
                stationarySinceMs: movement == "STAYING" ? loc?.stationarySince.flatMap { Iso.ms($0) } : nil,
                recordedAtMs: recorded, batteryPct: battery, isMe: isMe, blockedByMe: m.blockedByMe
            )
        }
    }

    // MARK: realtime

    private func handle(type: String, data: JSONValue) {
        switch type {
        case "location.update":
            guard let userId = data["userId"]?.string, data["circleId"]?.string == selectedCircleId else { return }
            let wrapped: JSONValue = .object(["userId": .string(userId), "status": .string("LIVE"), "location": data])
            if let bytes = try? JSONEncoder().encode(wrapped), let row = try? JSONDecoder().decode(MemberLocationDto.self, from: bytes) { locations[userId] = row }
        case "member.availability", "circle.membership":
            Task { await refreshLocations() }
        case "sharing.status":
            Task { await refreshSharing() }
        case "notification":
            guard let bytes = try? JSONEncoder().encode(data), let n = try? JSONDecoder().decode(NotificationDto.self, from: bytes) else { return }
            unread += 1
            onNotification(n)
        default: break
        }
    }

    private func onNotification(_ n: NotificationDto) {
        let foreground = UIApplication.shared.applicationState == .active
        if n.kind == "MONSTER" { Task { await refreshBattle() } }
        if n.kind == "QUICK_MESSAGE", foreground, let d = n.data {
            incoming = IncomingQuickMessage(fromName: d["fromName"]?.string ?? "Someone", text: d["text"]?.string ?? n.title, emoji: d["emoji"]?.string ?? "💬", code: d["code"]?.string ?? "")
            return
        }
        if foreground && n.kind != "SOS" { return }
        LocalNotifier.post(id: n.id, title: n.title, body: n.body, route: n.kind)
    }

    /// A notification (or a tap on one) asked for something: switch to the right place.
    func open(kind: String, data: JSONValue?) {
        switch kind {
        case "SOS", "TRIP", "ARRIVAL", "QUICK_MESSAGE":
            selectedTab = .map
            pendingFocusUserId = data?["userId"]?.string ?? data?["fromUserId"]?.string
        case "QUEST": selectedTab = .explore
        case "LEVEL_UP", "ACHIEVEMENT": selectedTab = .play
        case "MONSTER": selectedTab = .play; showBattle = true
        case "INVITATION", "CIRCLE", "CHALLENGE": selectedTab = .circle
        default: break
        }
    }

    // MARK: game results

    private func showGame(_ g: GameResultDto) {
        if let up = g.levelUps.last { gameToast = "Level \(up.to)! 🎉" }
        else if let a = g.achievements.last { gameToast = "Achievement: \(a.name)" }
        else if (g.xpGained ?? 0) > 0 { gameToast = "+\(g.xpGained ?? 0) XP" }
        Task { await refreshMe(); try? await Task.sleep(nanoseconds: 3_000_000_000); gameToast = nil }
    }

    // MARK: monsters

    func refreshBattle() async {
        guard let s = try? await api.battleActive() else { return }
        encounter = s.encounter
        coins = s.coins
    }

    // MARK: SOS and crash

    func triggerSOS(reason: String) async {
        if sos == .sending { return }
        sos = .sending
        guard let fix = await location.currentFix() else {
            sos = .failed("No position yet. Go outside or turn on location, then try again.")
            return
        }
        do {
            let r = try await api.sos(SosRequest(reason: reason, latitude: fix.coordinate.latitude, longitude: fix.coordinate.longitude, accuracyM: fix.horizontalAccuracy))
            let first = r.contacts.first
            if let first {
                let digits = first.phone.filter { $0.isNumber || $0 == "+" }
                if !digits.isEmpty, let url = URL(string: "tel://\(digits)") { await UIApplication.shared.open(url) }
            }
            sos = .sent(notified: r.notified, emailed: r.emailed, calling: first?.name)
        } catch {
            sos = .failed((error as? LocalizedError)?.errorDescription ?? "Couldn't send the SOS. Check your connection and try again.")
        }
    }

    func dismissSOS() { if sos != .sending { sos = .idle } }

    /// "Are you okay?" after a hard impact: fifteen seconds to answer, then the SOS goes out by itself.
    func beginCrashCountdown() {
        guard crashSecondsLeft == nil else { return }
        crashSecondsLeft = 15
        LocalNotifier.post(id: "crash-alert", title: "Are you okay?", body: "A hard impact was detected. Open Locana within 15 seconds or an SOS is sent.")
        crashTask = Task { [weak self] in
            for left in stride(from: 15, through: 1, by: -1) {
                await MainActor.run { self?.crashSecondsLeft = left }
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                if Task.isCancelled { return }
            }
            await MainActor.run { self?.crashSecondsLeft = nil }
            LocalNotifier.cancel(id: "crash-alert")
            await self?.triggerSOS(reason: "CRASH")
        }
    }

    func imOk() {
        crashTask?.cancel()
        crashTask = nil
        crashSecondsLeft = nil
        LocalNotifier.cancel(id: "crash-alert")
    }

    func sendSOSNowFromCrash() {
        crashTask?.cancel()
        crashTask = nil
        crashSecondsLeft = nil
        LocalNotifier.cancel(id: "crash-alert")
        Task { await triggerSOS(reason: "CRASH") }
    }
}

/// Small JSON files in Caches: the last profile, so the app opens offline.
enum Cache {
    private static func url(_ name: String) -> URL {
        let dir = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
        return dir.appendingPathComponent("locana-\(name).json")
    }
    static func save<T: Encodable>(_ value: T, _ name: String) { try? JSONEncoder().encode(value).write(to: url(name), options: .atomic) }
    static func load<T: Decodable>(_ type: T.Type, _ name: String) -> T? { (try? Data(contentsOf: url(name))).flatMap { try? JSONDecoder().decode(T.self, from: $0) } }
    static func clear() { try? FileManager.default.removeItem(at: url("me")) }
}
