import Foundation

// MARK: crash

/// Decides whether an accelerometer reading looks like a crash. A reading counts only when the person is in a vehicle,
/// and after one alert there is a quiet period, so one impact asks once.
public final class CrashDetector {
    public static let gravity = 9.80665
    private let thresholdG: Double
    private let quietMs: Int64
    private var lastAlertMs: Int64 = Int64.min / 2

    public init(thresholdG: Double = 5.0, quietMs: Int64 = 2 * 60_000) { self.thresholdG = thresholdG; self.quietMs = quietMs }

    /// Acceleration per axis in m/s², gravity included.
    public func onSample(x: Double, y: Double, z: Double, nowMs: Int64, inVehicle: Bool) -> Bool {
        if !inVehicle { return false }
        let g = (x * x + y * y + z * z).squareRoot() / CrashDetector.gravity
        if g < thresholdG { return false }
        if nowMs - lastAlertMs < quietMs { return false }
        lastAlertMs = nowMs
        return true
    }

    public static func inVehicle(activity: String?, lastSpeedMps: Double?, lastFixAgeMs: Int64) -> Bool {
        if activity == "vehicle" { return true }
        if let v = lastSpeedMps, v >= 6, lastFixAgeMs <= 60_000 { return true }
        return false
    }
}

// MARK: driving behaviour

public enum DriveEventKind: String { case hardBrake = "HARD_BRAKE", rapidAccel = "RAPID_ACCEL", phoneUse = "PHONE_USE" }

/// Notices risky moments while driving, from the speed between consecutive fixes and from whether the phone is in use.
public final class DrivingBehavior {
    private let hardBrakeMps2: Double, rapidAccelMps2: Double
    private let phoneUseAfterMs: Int64, quietMs: Int64, phoneQuietMs: Int64
    private var prevTimeMs: Int64 = -1
    private var prevSpeed: Double = 0
    private var phoneSinceMs: Int64 = -1
    private var lastAt: [DriveEventKind: Int64] = [:]

    public init(hardBrakeMps2: Double = 3.5, rapidAccelMps2: Double = 3.0, phoneUseAfterMs: Int64 = 12_000, quietMs: Int64 = 10_000, phoneQuietMs: Int64 = 60_000) {
        self.hardBrakeMps2 = hardBrakeMps2; self.rapidAccelMps2 = rapidAccelMps2
        self.phoneUseAfterMs = phoneUseAfterMs; self.quietMs = quietMs; self.phoneQuietMs = phoneQuietMs
    }

    public func reset() { prevTimeMs = -1; phoneSinceMs = -1 }

    public func onFix(timeMs: Int64, speedMps: Double?, accuracyM: Double, inVehicle: Bool, phoneInUse: Bool) -> DriveEventKind? {
        guard inVehicle, let speed = speedMps, accuracyM <= 40 else { reset(); return nil }
        var found: DriveEventKind?
        if prevTimeMs >= 0 {
            let dt = Double(timeMs - prevTimeMs) / 1000
            if dt >= 0.5 && dt <= 6 {
                let a = (speed - prevSpeed) / dt
                if a <= -hardBrakeMps2 && prevSpeed >= 6 { found = .hardBrake }
                else if a >= rapidAccelMps2 && speed >= 5 { found = .rapidAccel }
            } else if dt > 6 { phoneSinceMs = -1 }
        }
        prevTimeMs = timeMs
        prevSpeed = speed

        if found == nil {
            if phoneInUse && speed >= 5 {
                if phoneSinceMs < 0 { phoneSinceMs = timeMs }
                else if timeMs - phoneSinceMs >= phoneUseAfterMs { found = .phoneUse }
            } else { phoneSinceMs = -1 }
        }
        guard let kind = found else { return nil }
        let gap = kind == .phoneUse ? phoneQuietMs : quietMs
        if timeMs - (lastAt[kind] ?? Int64.min / 2) < gap { return nil }
        lastAt[kind] = timeMs
        return kind
    }
}

// MARK: tracking policy

public struct TrackingInputs {
    public var sharingActive: Bool, explorationEnabled: Bool, appInForeground: Bool, moving: Bool
    public var batteryPct: Int?
    public var charging: Bool, lowPowerMode: Bool, online: Bool
    public init(sharingActive: Bool, explorationEnabled: Bool, appInForeground: Bool, moving: Bool, batteryPct: Int?, charging: Bool, lowPowerMode: Bool, online: Bool) {
        self.sharingActive = sharingActive; self.explorationEnabled = explorationEnabled; self.appInForeground = appInForeground; self.moving = moving
        self.batteryPct = batteryPct; self.charging = charging; self.lowPowerMode = lowPowerMode; self.online = online
    }
}

public enum TrackingAccuracy { case high, balanced, lowPower }

/// What to ask of the location hardware. iOS has no "every N seconds" request, so the plan is an accuracy, a distance
/// filter and how long fixes may wait before an upload; the service turns it into CLLocationManager settings.
public struct TrackingPlan: Equatable {
    public var active: Bool
    public var accuracy: TrackingAccuracy
    public var distanceFilterM: Double
    /// How long samples may wait before an upload; 0 uploads each one, `.max` holds everything (offline).
    public var uploadBatchMs: Int64
    public var reason: String
    public static let off = TrackingPlan(active: false, accuracy: .lowPower, distanceFilterM: 0, uploadBatchMs: 0, reason: "sharing and exploration are off")
}

public enum TrackingPolicy {
    public static func plan(_ i: TrackingInputs) -> TrackingPlan {
        if !i.sharingActive && !i.explorationEnabled { return .off }
        let critical = !i.charging && (i.batteryPct.map { $0 <= 10 } ?? false)
        let low = !i.charging && (i.lowPowerMode || (i.batteryPct.map { $0 <= 20 } ?? false))
        var plan: TrackingPlan
        if critical { plan = TrackingPlan(active: true, accuracy: .lowPower, distanceFilterM: 100, uploadBatchMs: 600_000, reason: "battery critical") }
        else if !i.moving { plan = TrackingPlan(active: true, accuracy: .balanced, distanceFilterM: 25, uploadBatchMs: 300_000, reason: "stationary") }
        else if i.appInForeground { plan = TrackingPlan(active: true, accuracy: .high, distanceFilterM: 10, uploadBatchMs: 0, reason: "moving, app open") }
        else { plan = TrackingPlan(active: true, accuracy: i.explorationEnabled ? .high : .balanced, distanceFilterM: 25, uploadBatchMs: 60_000, reason: "moving, background") }
        if low && !critical {
            if plan.accuracy == .high { plan.accuracy = .balanced }
            plan.uploadBatchMs = max(plan.uploadBatchMs, 120_000)
            plan.reason += ", battery saver"
        }
        if !i.online { plan.uploadBatchMs = Int64.max; plan.reason += ", offline" }
        return plan
    }
}
