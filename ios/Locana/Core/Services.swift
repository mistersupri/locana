import Foundation
import CoreLocation
import CoreMotion
import UIKit
import UserNotifications
import LocanaKit

enum AppConfig {
    static var apiURL: URL {
        let raw = (Bundle.main.object(forInfoDictionaryKey: "LocanaAPIURL") as? String) ?? "http://localhost:3000/"
        return URL(string: raw.hasSuffix("/") ? raw : raw + "/") ?? URL(string: "http://localhost:3000/")!
    }
    static var wsURL: URL {
        var s = apiURL.absoluteString
        s = s.replacingOccurrences(of: "https://", with: "wss://").replacingOccurrences(of: "http://", with: "ws://")
        return URL(string: s + "ws")!
    }
    static var version: String { (Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String) ?? "0" }
}

// MARK: photos

/// Profile photos need the access token, so they are fetched here and kept in memory by (person, version).
@MainActor
final class PhotoStore: ObservableObject {
    private let cache = NSCache<NSString, UIImage>()
    private var inflight: [String: Task<UIImage?, Never>] = [:]
    var api: APIClient?

    func image(userId: String, version: Int64) async -> UIImage? {
        let key = "\(userId)-\(version)"
        if let hit = cache.object(forKey: key as NSString) { return hit }
        if let t = inflight[key] { return await t.value }
        guard let api else { return nil }
        let t = Task<UIImage?, Never> {
            guard let data = try? await api.photo(userId: userId), let img = UIImage(data: data) else { return nil }
            return img
        }
        inflight[key] = t
        let img = await t.value
        inflight[key] = nil
        if let img { cache.setObject(img, forKey: key as NSString) }
        return img
    }

    func clear() { cache.removeAllObjects() }
}

// MARK: motion (activity, crash)

/// What the phone itself thinks the person is doing, and a hard-impact detector while they seem to be in a vehicle.
@MainActor
final class MotionService: ObservableObject {
    @Published private(set) var activity: String?
    private let activityManager = CMMotionActivityManager()
    private let motion = CMMotionManager()
    private var lastActivityAt = Date.distantPast
    private let crash = CrashDetector()
    private var listeningForImpact = false
    var onCrash: (() -> Void)?
    var lastSpeedMps: Double?
    var lastFixAt = Date.distantPast
    var crashEnabled = true

    func start() {
        guard CMMotionActivityManager.isActivityAvailable() else { return }
        activityManager.startActivityUpdates(to: .main) { [weak self] a in
            guard let self, let a else { return }
            Task { @MainActor in
                self.activity = Self.name(a)
                self.lastActivityAt = Date()
                self.updateImpactSensor()
            }
        }
    }

    func stop() {
        activityManager.stopActivityUpdates()
        stopImpact()
        activity = nil
    }

    /// The activity to stamp on a fix: recent and reasonably sure, or nil.
    var currentActivity: String? {
        guard Date().timeIntervalSince(lastActivityAt) < 180 else { return nil }
        return activity
    }

    private static func name(_ a: CMMotionActivity) -> String? {
        guard a.confidence != .low else { return nil }
        if a.automotive { return "vehicle" }
        if a.cycling { return "cycling" }
        if a.running { return "running" }
        if a.walking { return "walking" }
        if a.stationary { return "still" }
        return nil
    }

    var inVehicle: Bool {
        CrashDetector.inVehicle(activity: currentActivity, lastSpeedMps: lastSpeedMps, lastFixAgeMs: Int64(Date().timeIntervalSince(lastFixAt) * 1000))
    }

    /// The accelerometer is only read while the person seems to be driving, so it costs nothing the rest of the day.
    func updateImpactSensor() {
        let want = crashEnabled && inVehicle
        if want && !listeningForImpact, motion.isAccelerometerAvailable {
            motion.accelerometerUpdateInterval = 1.0 / 25
            motion.startAccelerometerUpdates(to: .main) { [weak self] data, _ in
                guard let self, let a = data?.acceleration else { return }
                let g = CrashDetector.gravity
                // CoreMotion reports g; the detector works in m/s².
                if self.crash.onSample(x: a.x * g, y: a.y * g, z: a.z * g, nowMs: Int64(Date().timeIntervalSince1970 * 1000), inVehicle: self.inVehicle) { self.onCrash?() }
            }
            listeningForImpact = true
        } else if !want && listeningForImpact {
            stopImpact()
        }
    }

    private func stopImpact() {
        if listeningForImpact { motion.stopAccelerometerUpdates(); listeningForImpact = false }
    }
}

// MARK: realtime

/// One WebSocket to /ws while the app is in use. Reconnects with backoff, re-subscribes to circles, and hands events to `onEvent`.
@MainActor
final class RealtimeClient {
    private var task: URLSessionWebSocketTask?
    private var wanted = false
    private var circles = Set<String>()
    private var attempt = 0
    private var reconnect: Task<Void, Never>?
    var tokenProvider: () -> String? = { nil }
    var refresh: () async -> String? = { nil }
    var onEvent: (String, JSONValue) -> Void = { _, _ in }

    var isConnected: Bool { task != nil }

    func start() {
        guard !wanted else { return }
        wanted = true
        connect()
    }

    func stop() {
        wanted = false
        reconnect?.cancel()
        task?.cancel(with: .normalClosure, reason: nil)
        task = nil
    }

    func subscribe(_ ids: [String]) {
        let new = Set(ids)
        for id in circles.subtracting(new) { send(["type": "unsubscribe", "circleId": id]) }
        for id in new.subtracting(circles) { send(["type": "subscribe", "circleId": id]) }
        circles = new
    }

    func reauthenticate(_ token: String) { send(["type": "auth", "token": token]) }

    private func send(_ obj: [String: String]) {
        guard let task, let data = try? JSONSerialization.data(withJSONObject: obj), let text = String(data: data, encoding: .utf8) else { return }
        task.send(.string(text)) { _ in }
    }

    private func connect() {
        guard wanted, let token = tokenProvider() else { return }
        var req = URLRequest(url: AppConfig.wsURL)
        req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        let t = URLSession.shared.webSocketTask(with: req)
        task = t
        t.resume()
        listen(t)
    }

    private func listen(_ t: URLSessionWebSocketTask) {
        t.receive { [weak self] result in
            Task { @MainActor in
                guard let self, self.task === t else { return }
                switch result {
                case .success(let msg):
                    var text: String?
                    switch msg { case .string(let s): text = s; case .data(let d): text = String(data: d, encoding: .utf8); @unknown default: break }
                    if let text, let obj = try? JSONDecoder().decode(JSONValue.self, from: Data(text.utf8)), let type = obj["type"]?.string {
                        if type == "ready" {
                            self.attempt = 0
                            for id in self.circles { self.send(["type": "subscribe", "circleId": id]) }
                        }
                        self.onEvent(type, obj["data"] ?? .null)
                    }
                    self.listen(t)
                case .failure:
                    self.task = nil
                    self.scheduleReconnect()
                }
            }
        }
    }

    private func scheduleReconnect() {
        guard wanted else { return }
        attempt += 1
        let wait = min(60.0, pow(2.0, Double(min(attempt, 6)))) + Double.random(in: 0...1)
        reconnect?.cancel()
        reconnect = Task { [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(wait * 1_000_000_000))
            guard !Task.isCancelled else { return }
            await MainActor.run {
                guard let self else { return }
                Task { @MainActor in
                    // A token that expired while the socket was down is refreshed before trying again.
                    _ = await self.refresh()
                    self.connect()
                }
            }
        }
    }
}

// MARK: local notifications

enum LocalNotifier {
    static func requestPermission() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { _, _ in }
    }

    /// Shows a notification now; the same id replaces an earlier one instead of stacking.
    static func post(id: String, title: String, body: String, route: String? = nil) {
        let c = UNMutableNotificationContent()
        c.title = title
        c.body = body
        c.sound = .default
        if let route { c.userInfo = ["route": route] }
        UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: id, content: c, trigger: nil)) { _ in }
    }

    static func cancel(id: String) { UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: [id]); UNUserNotificationCenter.current().removeDeliveredNotifications(withIdentifiers: [id]) }
}
