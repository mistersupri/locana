import Foundation
import CoreLocation
import UIKit
import LocanaKit

/// Follows the phone's position, keeps the fixes in a small queue on disk and uploads them in batches. How often it
/// listens and how long fixes wait before an upload follow `TrackingPolicy`, the same plan the Android app uses.
@MainActor
final class LocationService: NSObject, ObservableObject, CLLocationManagerDelegate {
    @Published private(set) var latest: CLLocation?
    @Published private(set) var authorization: CLAuthorizationStatus

    private let manager = CLLocationManager()
    private var queue: [SampleDto] = []
    private var queueSince: Date?
    private let behavior = DrivingBehavior()
    private var plan: TrackingPlan = .off
    private var lastEnqueuedMs: Int64 = 0
    private var flushing = false
    private var oneShot: [CheckedContinuation<CLLocation?, Never>] = []

    var api: APIClient?
    var motion: MotionService?
    var sharingActive = false
    var explorationEnabled = false
    var online = true
    var onGame: ((GameResultDto) -> Void)?
    var onNotNeeded: (() -> Void)?

    override init() {
        authorization = manager.authorizationStatus
        super.init()
        manager.delegate = self
        manager.activityType = .otherNavigation
        UIDevice.current.isBatteryMonitoringEnabled = true
        loadQueue()
    }

    // MARK: permission and plan

    var hasPermission: Bool { authorization == .authorizedAlways || authorization == .authorizedWhenInUse }

    func requestPermission() {
        switch manager.authorizationStatus {
        case .notDetermined: manager.requestWhenInUseAuthorization()
        case .authorizedWhenInUse: manager.requestAlwaysAuthorization()
        default: break
        }
    }

    func configure(sharing: Bool, exploration: Bool) {
        sharingActive = sharing
        explorationEnabled = exploration
        apply()
    }

    private var moving: Bool {
        guard let l = latest, l.speed >= 0.7, l.horizontalAccuracy <= 50 else { return motion?.currentActivity.map { ["walking", "running", "cycling", "vehicle"].contains($0) } ?? false }
        return true
    }

    private var driving: Bool { motion?.inVehicle ?? false }

    func apply() {
        guard hasPermission else { return }
        let level = UIDevice.current.batteryLevel
        plan = TrackingPolicy.plan(TrackingInputs(
            sharingActive: sharingActive, explorationEnabled: explorationEnabled,
            appInForeground: UIApplication.shared.applicationState == .active, moving: moving,
            batteryPct: level < 0 ? nil : Int(level * 100), charging: UIDevice.current.batteryState == .charging || UIDevice.current.batteryState == .full,
            lowPowerMode: ProcessInfo.processInfo.isLowPowerModeEnabled, online: online
        ))
        guard plan.active else { stop(); return }
        manager.desiredAccuracy = driving ? kCLLocationAccuracyBest : (plan.accuracy == .high ? kCLLocationAccuracyBest : plan.accuracy == .balanced ? kCLLocationAccuracyNearestTenMeters : kCLLocationAccuracyHundredMeters)
        manager.distanceFilter = driving ? kCLDistanceFilterNone : plan.distanceFilterM
        manager.pausesLocationUpdatesAutomatically = false
        manager.allowsBackgroundLocationUpdates = true
        manager.showsBackgroundLocationIndicator = true
        manager.startUpdatingLocation()
        manager.startMonitoringSignificantLocationChanges()
    }

    func stop() {
        manager.stopUpdatingLocation()
        manager.stopMonitoringSignificantLocationChanges()
        behavior.reset()
    }

    /// A position for something that cannot wait (SOS): the newest fix if it is fresh, else ask once.
    func currentFix() async -> CLLocation? {
        if let l = latest, Date().timeIntervalSince(l.timestamp) < 60 { return l }
        guard hasPermission else { return latest }
        manager.requestLocation()
        return await withCheckedContinuation { c in
            oneShot.append(c)
            Task { @MainActor in
                try? await Task.sleep(nanoseconds: 8_000_000_000)
                if !self.oneShot.isEmpty { let all = self.oneShot; self.oneShot = []; all.forEach { $0.resume(returning: self.latest) } }
            }
        }
    }

    // MARK: delegate

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let status = manager.authorizationStatus
        Task { @MainActor in
            self.authorization = status
            self.apply()
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        Task { @MainActor in
            for l in locations { self.onFix(l) }
            if !self.oneShot.isEmpty { let all = self.oneShot; self.oneShot = []; all.forEach { $0.resume(returning: self.latest) } }
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {}

    // MARK: fixes

    private func onFix(_ l: CLLocation) {
        guard l.horizontalAccuracy > 0 else { return }
        latest = l
        let speed: Double? = l.speed >= 0 ? l.speed : nil
        motion?.lastSpeedMps = speed
        motion?.lastFixAt = l.timestamp
        motion?.updateImpactSensor()
        let ms = Int64(l.timestamp.timeIntervalSince1970 * 1000)
        let event = behavior.onFix(timeMs: ms, speedMps: speed, accuracyM: l.horizontalAccuracy, inVehicle: driving, phoneInUse: UIApplication.shared.applicationState == .active)
        // While driving a fix arrives about every second for the braking check; only one in a normal interval (or an event) is kept.
        let cadence: Int64 = driving ? 15_000 : 5_000
        if event != nil || ms - lastEnqueuedMs >= cadence {
            lastEnqueuedMs = ms
            let level = UIDevice.current.batteryLevel
            enqueue(SampleDto(
                latitude: l.coordinate.latitude, longitude: l.coordinate.longitude, accuracyM: l.horizontalAccuracy, recordedAt: Iso.string(ms: ms), sequence: nextSequence(),
                speedMps: speed, bearingDeg: l.course >= 0 ? l.course : nil, altitudeM: l.verticalAccuracy > 0 ? l.altitude : nil,
                source: "fused", activity: motion?.currentActivity, isMock: (l.sourceInformation?.isSimulatedBySoftware ?? false) ? true : nil,
                batteryPct: level < 0 ? nil : Int(level * 100), driveEvent: event?.rawValue
            ))
        }
        apply()
        flushIfDue(force: plan.uploadBatchMs == 0 || event != nil)
    }

    // MARK: queue

    private var queueURL: URL {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir.appendingPathComponent("pending-samples.json")
    }

    private func loadQueue() {
        if let data = try? Data(contentsOf: queueURL), let list = try? JSONDecoder().decode([SampleDto].self, from: data) { queue = list; if !list.isEmpty { queueSince = Date() } }
    }

    private func saveQueue() { try? JSONEncoder().encode(queue).write(to: queueURL, options: .atomic) }

    private func nextSequence() -> Int64 {
        let n = Int64(UserDefaults.standard.integer(forKey: "locana.sequence")) + 1
        UserDefaults.standard.set(Int(n), forKey: "locana.sequence")
        return n
    }

    private func enqueue(_ s: SampleDto) {
        if queue.isEmpty { queueSince = Date() }
        queue.append(s)
        if queue.count > 5_000 { queue.removeFirst(queue.count - 5_000) }
        saveQueue()
    }

    func flushIfDue(force: Bool = false) {
        guard !queue.isEmpty, !flushing, api != nil else { return }
        let waited = Date().timeIntervalSince(queueSince ?? Date()) * 1000
        let due = force || plan.uploadBatchMs == 0 || (plan.uploadBatchMs != Int64.max && waited >= Double(plan.uploadBatchMs))
        guard due, online else { return }
        Task { await flush() }
    }

    /// Uploads queued samples oldest first, 100 at a time. Offline or failing batches stay queued.
    func flush() async {
        guard let api, !flushing else { return }
        flushing = true
        defer { flushing = false }
        // Anything older than the server accepts is not worth sending.
        let cutoff = Int64(Date().timeIntervalSince1970 * 1000) - 23 * 3_600_000
        queue.removeAll { (Iso.ms($0.recordedAt) ?? 0) < cutoff }
        while !queue.isEmpty {
            let batch = Array(queue.prefix(100))
            do {
                let r = try await api.upload(batch)
                queue.removeFirst(min(batch.count, queue.count))
                if let g = r.game, (g.xpGained ?? 0) > 0 || !g.levelUps.isEmpty || !g.achievements.isEmpty || !g.newTiles.isEmpty { onGame?(g) }
            } catch let e as APIError {
                if e == .offline { break }
                if e.code == "LOCATION_NOT_NEEDED" { queue.removeAll(); onNotNeeded?(); break }
                if e.status == 400 { queue.removeFirst(min(batch.count, queue.count)); continue }
                break
            } catch { break }
        }
        if queue.isEmpty { queueSince = nil }
        saveQueue()
    }
}
