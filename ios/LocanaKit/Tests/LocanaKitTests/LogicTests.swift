import XCTest
@testable import LocanaKit

final class FormattingTests: XCTestCase {
    func testDistance() {
        XCTAssertEqual(Formatting.distance(700), "700 m")
        XCTAssertEqual(Formatting.distance(9_800), "9.8 km")
        XCTAssertEqual(Formatting.distance(1_000), "1.0 km")
    }

    func testInitials() {
        XCTAssertEqual(Formatting.initials("Ahmad Supriyanto"), "AS")
        XCTAssertEqual(Formatting.initials("rani"), "RA")
        XCTAssertEqual(Formatting.initials("   "), "?")
    }

    func testDuration() {
        XCTAssertEqual(Formatting.duration(ms: 30_000), "under a minute")
        XCTAssertEqual(Formatting.duration(ms: 42 * 60_000), "42 mins")
        XCTAssertEqual(Formatting.duration(ms: 60 * 60_000), "1 hr")
        XCTAssertEqual(Formatting.duration(ms: (3 * 60 + 52) * 60_000), "3 hrs, 52 mins")
        XCTAssertEqual(Formatting.duration(ms: (2 * 24 * 60 + 4 * 60) * 60_000), "2 days, 4 hrs")
    }

    func testProgress() {
        XCTAssertEqual(Formatting.progress(value: 1500, target: 3000, unit: "meters"), "1.5 km of 3.0 km")
        XCTAssertEqual(Formatting.progress(value: 2, target: 3, unit: "tiles"), "2 of 3 tiles")
    }

    func testSpeed() { XCTAssertEqual(Formatting.speedKmh(mps: 10), "36 km/h") }
}

final class FreshnessTests: XCTestCase {
    func testPresenceAgesWithTime() {
        XCTAssertEqual(Freshness.presence(server: .live, recordedAtMs: 0, nowMs: 5 * 60_000), .live)
        XCTAssertEqual(Freshness.presence(server: .live, recordedAtMs: 0, nowMs: 11 * 60_000), .stale)
        XCTAssertEqual(Freshness.presence(server: .hidden, recordedAtMs: 0, nowMs: 0), .hidden)
        XCTAssertEqual(Freshness.presence(server: .live, recordedAtMs: nil, nowMs: 0), .unavailable)
    }

    func testAgeLabel() {
        XCTAssertEqual(Freshness.ageLabel(recordedAtMs: 0, nowMs: 10_000), "just now")
        XCTAssertEqual(Freshness.ageLabel(recordedAtMs: 0, nowMs: 4 * 60_000), "4 min ago")
        XCTAssertEqual(Freshness.ageLabel(recordedAtMs: 0, nowMs: 2 * 3_600_000), "2 h ago")
        XCTAssertEqual(Freshness.ageLabel(recordedAtMs: 0, nowMs: 3 * 86_400_000), "3 d ago")
    }
}

final class GeoTests: XCTestCase {
    func testDistanceMatchesTheBackendFormula() {
        // About 111 m per 0.001 degree of latitude.
        let d = Geo.distanceM(LatLon(lat: -6.2, lon: 106.8), LatLon(lat: -6.199, lon: 106.8))
        XCTAssertEqual(d, 111.2, accuracy: 0.5)
        XCTAssertEqual(Geo.distanceM(LatLon(lat: 1, lon: 1), LatLon(lat: 1, lon: 1)), 0, accuracy: 1e-9)
    }
}

final class IsoTests: XCTestCase {
    func testParsesWithAndWithoutFraction() {
        XCTAssertEqual(Iso.ms("2026-11-08T01:00:00Z"), 1_794_099_600_000)
        XCTAssertEqual(Iso.ms("2026-11-08T01:00:00.500Z"), 1_794_099_600_500)
        XCTAssertNil(Iso.ms("not a date"))
        XCTAssertEqual(Iso.ms(Iso.string(ms: 1_794_099_600_123)), 1_794_099_600_123)
    }
}

final class RouteMathTests: XCTestCase {
    private let path = [LatLon(lat: -6.2, lon: 106.8), LatLon(lat: -6.2, lon: 106.81), LatLon(lat: -6.21, lon: 106.81)]
    private let times: [Int64] = [1_000, 2_000, 4_000]

    func testNoRouteNoPoint() { XCTAssertNil(RouteMath.routeAt(path: [], pathAt: [], startMs: 0, endMs: 1, t: 0)) }

    func testHalfwayInTimeIsHalfwayInSpace() {
        let p = RouteMath.routeAt(path: path, pathAt: times, startMs: 1_000, endMs: 4_000, t: 1_500)!
        XCTAssertEqual(p.lat, -6.2, accuracy: 1e-9)
        XCTAssertEqual(p.lon, 106.805, accuracy: 1e-9)
        XCTAssertEqual(p.index, 0)
    }

    func testSlowerStretchMovesProportionally() {
        let p = RouteMath.routeAt(path: path, pathAt: times, startMs: 1_000, endMs: 4_000, t: 3_000)!
        XCTAssertEqual(p.lat, -6.205, accuracy: 1e-9)
        XCTAssertEqual(p.index, 1)
    }

    func testClampsOutsideTheTrip() {
        XCTAssertEqual(RouteMath.routeAt(path: path, pathAt: times, startMs: 1_000, endMs: 4_000, t: -50)!.lat, -6.2, accuracy: 1e-9)
        XCTAssertEqual(RouteMath.routeAt(path: path, pathAt: times, startMs: 1_000, endMs: 4_000, t: 99_000)!.lat, -6.21, accuracy: 1e-9)
    }

    func testEvenSpreadWithoutTimes() {
        let p = RouteMath.routeAt(path: path, pathAt: [], startMs: 0, endMs: 2_000, t: 1_000)!
        XCTAssertEqual(p.lon, 106.81, accuracy: 1e-9)
    }

    func testMetresGrowAlongTheRoute() {
        let early = RouteMath.routeAt(path: path, pathAt: times, startMs: 1_000, endMs: 4_000, t: 1_500)!.metresFromStart
        let late = RouteMath.routeAt(path: path, pathAt: times, startMs: 1_000, endMs: 4_000, t: 3_500)!.metresFromStart
        XCTAssertGreaterThan(late, early)
        XCTAssertGreaterThan(early, 0)
    }
}

final class EdgeMarkTests: XCTestCase {
    private func mark(_ x: Double, _ y: Double) -> EdgeMark? { EdgeMarks.mark(px: x, py: y, left: 0, top: 200, right: 1000, bottom: 1500, inset: 50) }

    func testInsideNeedsNoMark() { XCTAssertNil(mark(500, 800)) }

    func testLeftPointsLeft() {
        let m = mark(-900, 850)!
        XCTAssertEqual(m.x, 50, accuracy: 0.5)
        XCTAssertEqual(m.y, 850, accuracy: 1)
        XCTAssertEqual(m.angleDeg, -90, accuracy: 1)
    }

    func testAbovePointsUp() {
        let m = mark(500, -2000)!
        XCTAssertEqual(m.angleDeg, 0, accuracy: 1)
        XCTAssertEqual(m.y, 250, accuracy: 1)
    }

    func testLowerRightStaysInsideTheBand() {
        let m = mark(3000, 3000)!
        XCTAssertTrue((0...1000).contains(m.x) && (200...1500).contains(m.y))
        XCTAssertTrue((90...180).contains(m.angleDeg))
    }
}

final class DetectorTests: XCTestCase {
    func testHardImpactInVehicleIsACrash() {
        XCTAssertTrue(CrashDetector().onSample(x: 60, y: 0, z: 0, nowMs: 1_000, inVehicle: true))
    }

    func testSameImpactOutsideAVehicleIsIgnored() {
        XCTAssertFalse(CrashDetector().onSample(x: 60, y: 0, z: 0, nowMs: 1_000, inVehicle: false))
    }

    func testBumpsAreIgnoredAndOneImpactAsksOnce() {
        let d = CrashDetector()
        XCTAssertFalse(d.onSample(x: 20, y: 5, z: 12, nowMs: 0, inVehicle: true))
        XCTAssertTrue(d.onSample(x: 60, y: 0, z: 0, nowMs: 0, inVehicle: true))
        XCTAssertFalse(d.onSample(x: 60, y: 0, z: 0, nowMs: 500, inVehicle: true))
        XCTAssertTrue(d.onSample(x: 60, y: 0, z: 0, nowMs: 121_000, inVehicle: true))
    }

    func testVehicleContext() {
        XCTAssertTrue(CrashDetector.inVehicle(activity: "vehicle", lastSpeedMps: nil, lastFixAgeMs: 0))
        XCTAssertTrue(CrashDetector.inVehicle(activity: nil, lastSpeedMps: 12, lastFixAgeMs: 10_000))
        XCTAssertFalse(CrashDetector.inVehicle(activity: "walking", lastSpeedMps: 1.4, lastFixAgeMs: 5_000))
        XCTAssertFalse(CrashDetector.inVehicle(activity: nil, lastSpeedMps: 12, lastFixAgeMs: 120_000))
    }

    func testHardBrakingAndRapidAcceleration() {
        let d = DrivingBehavior()
        XCTAssertNil(d.onFix(timeMs: 0, speedMps: 14, accuracyM: 8, inVehicle: true, phoneInUse: false))
        XCTAssertEqual(d.onFix(timeMs: 2_000, speedMps: 5, accuracyM: 8, inVehicle: true, phoneInUse: false), .hardBrake)
        let e = DrivingBehavior()
        _ = e.onFix(timeMs: 0, speedMps: 2, accuracyM: 8, inVehicle: true, phoneInUse: false)
        XCTAssertEqual(e.onFix(timeMs: 2_000, speedMps: 9, accuracyM: 8, inVehicle: true, phoneInUse: false), .rapidAccel)
    }

    func testGentleSlowdownAndOutsideVehicleAreFine() {
        let d = DrivingBehavior()
        _ = d.onFix(timeMs: 0, speedMps: 14, accuracyM: 8, inVehicle: true, phoneInUse: false)
        XCTAssertNil(d.onFix(timeMs: 3_000, speedMps: 10, accuracyM: 8, inVehicle: true, phoneInUse: false))
        let o = DrivingBehavior()
        _ = o.onFix(timeMs: 0, speedMps: 14, accuracyM: 8, inVehicle: false, phoneInUse: false)
        XCTAssertNil(o.onFix(timeMs: 2_000, speedMps: 2, accuracyM: 8, inVehicle: false, phoneInUse: false))
    }

    func testPhoneUseNeedsAStretchAndRepeatsOnlyOncePerMinute() {
        let d = DrivingBehavior()
        for t in [0, 5_000, 10_000] as [Int64] { XCTAssertNil(d.onFix(timeMs: t, speedMps: 12, accuracyM: 8, inVehicle: true, phoneInUse: true)) }
        XCTAssertEqual(d.onFix(timeMs: 15_000, speedMps: 12, accuracyM: 8, inVehicle: true, phoneInUse: true), .phoneUse)
        XCTAssertNil(d.onFix(timeMs: 20_000, speedMps: 12, accuracyM: 8, inVehicle: true, phoneInUse: true))
    }

    func testTrackingPolicy() {
        func inputs(moving: Bool, fg: Bool = false, battery: Int? = 80, online: Bool = true) -> TrackingInputs {
            TrackingInputs(sharingActive: true, explorationEnabled: true, appInForeground: fg, moving: moving, batteryPct: battery, charging: false, lowPowerMode: false, online: online)
        }
        XCTAssertFalse(TrackingPolicy.plan(TrackingInputs(sharingActive: false, explorationEnabled: false, appInForeground: true, moving: true, batteryPct: 50, charging: false, lowPowerMode: false, online: true)).active)
        XCTAssertEqual(TrackingPolicy.plan(inputs(moving: false)).uploadBatchMs, 300_000)
        XCTAssertEqual(TrackingPolicy.plan(inputs(moving: true, fg: true)).uploadBatchMs, 0)
        XCTAssertEqual(TrackingPolicy.plan(inputs(moving: true)).accuracy, .high)
        XCTAssertEqual(TrackingPolicy.plan(inputs(moving: true, battery: 15)).accuracy, .balanced)
        XCTAssertEqual(TrackingPolicy.plan(inputs(moving: true, battery: 5)).accuracy, .lowPower)
        XCTAssertEqual(TrackingPolicy.plan(inputs(moving: true, online: false)).uploadBatchMs, Int64.max)
    }
}

final class TimelineTests: XCTestCase {
    private func stay(_ start: String, _ end: String, place: TimelinePlaceDto? = nil) -> TimelineSegmentDto {
        let json = """
        {"type":"STAY","startedAt":"\(start)","endedAt":"\(end)","durationSec":7200,"latitude":-6.2,"longitude":106.8\(place.map { ",\"place\":{\"id\":\"p\",\"name\":\"\($0.name)\",\"type\":\"\($0.type)\"}" } ?? "")}
        """
        return try! JSONDecoder().decode(TimelineSegmentDto.self, from: Data(json.utf8))
    }

    private func trip(_ start: String, _ end: String, mode: String = "DRIVE") -> TimelineSegmentDto {
        let json = """
        {"type":"TRIP","startedAt":"\(start)","endedAt":"\(end)","durationSec":600,"distanceM":9800,"avgSpeedKmh":40,"maxSpeedKmh":60,"mode":"\(mode)","path":[[-6.2,106.8],[-6.1,106.9]],"pathAt":[1,2],"eventCounts":{"hardBrake":2}}
        """
        return try! JSONDecoder().decode(TimelineSegmentDto.self, from: Data(json.utf8))
    }

    func testTripsAreNamedAfterTheStaysOnEitherSide() {
        let segs = [
            stay("2026-11-08T01:00:00Z", "2026-11-08T03:00:00Z", place: TimelinePlaceDto(id: "p", name: "Home", type: "HOME")),
            trip("2026-11-08T03:00:00Z", "2026-11-08T03:10:00Z"),
            stay("2026-11-08T03:10:00Z", "2026-11-08T05:10:00Z", place: TimelinePlaceDto(id: "q", name: "Office", type: "WORK")),
            trip("2026-11-08T05:10:00Z", "2026-11-08T05:25:00Z", mode: "WALK"),
        ]
        let items = TimelineBuilder.items(from: segs, zone: TimeZone(identifier: "UTC")!, nowMs: 0, today: false)
        guard case .stay(let home) = items[0], case .trip(let drive) = items[1], case .trip(let walk) = items[3] else { return XCTFail("shape") }
        XCTAssertEqual(home.title, "Home")
        XCTAssertEqual(home.range, "01:00 – 03:00")
        XCTAssertEqual(drive.title, "Home → Office")
        XCTAssertEqual(drive.distance, "9.8 km")
        XCTAssertTrue(drive.driving)
        XCTAssertEqual(drive.counts, DriveEventCountsDto(hardBrake: 2, rapidAccel: 0, phoneUse: 0))
        XCTAssertEqual(walk.title, "Left Office")
    }

    func testOnlyTheNewestRecentEntryOfTodayIsHappeningNow() {
        let segs = [stay("2026-11-08T01:00:00Z", "2026-11-08T02:00:00Z"), stay("2026-11-08T02:00:00Z", "2026-11-08T03:00:00Z")]
        let end = Iso.ms("2026-11-08T03:00:00Z")!
        let items = TimelineBuilder.items(from: segs, zone: TimeZone(identifier: "UTC")!, nowMs: end + 5 * 60_000, today: true)
        XCTAssertEqual(items.map { if case .stay(let s) = $0 { return s.ongoing } else { return false } }, [false, true])
    }

    func testRetitleLearnsNamesLater() {
        let segs = [stay("2026-11-08T01:00:00Z", "2026-11-08T03:00:00Z"), trip("2026-11-08T03:00:00Z", "2026-11-08T03:10:00Z"), stay("2026-11-08T03:10:00Z", "2026-11-08T05:10:00Z")]
        let items = TimelineBuilder.items(from: segs, zone: TimeZone(identifier: "UTC")!, nowMs: 0, today: false)
        let named = TimelineBuilder.retitle(items, stayNames: [0: "Cafe", 2: "Mall"])
        guard case .stay(let first) = named[0], case .trip(let t) = named[1] else { return XCTFail("shape") }
        XCTAssertEqual(first.title, "Cafe")
        XCTAssertFalse(first.unnamed)
        XCTAssertEqual(t.title, "Cafe → Mall")
    }

    func testTripTitleFallbacks() {
        XCTAssertEqual(TimelineBuilder.tripTitle(from: nil, to: nil, mode: "DRIVE"), "Drive")
        XCTAssertEqual(TimelineBuilder.tripTitle(from: nil, to: nil, mode: "CYCLE"), "Ride")
        XCTAssertEqual(TimelineBuilder.tripTitle(from: nil, to: nil, mode: "WALK"), "Walk")
        XCTAssertEqual(TimelineBuilder.tripTitle(from: nil, to: "Mall", mode: "WALK"), "On the way to Mall")
    }
}
