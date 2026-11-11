import Foundation

public struct TimelineStay: Equatable, Identifiable {
    public let id: String
    public var title: String
    public let range: String
    public let duration: String
    public let latitude: Double?
    public let longitude: Double?
    public let ongoing: Bool
    /// No saved place matched: the app may still look the spot up and name it.
    public var unnamed: Bool
    public let isWork: Bool
}

public struct TimelineTrip: Equatable, Identifiable {
    public let id: String
    public var title: String
    public let range: String
    public let duration: String
    public let distance: String
    public let avgSpeed: String
    public let maxSpeed: String
    public let driving: Bool
    public let emoji: String
    public let path: [LatLon]
    public let pathAt: [Int64]
    public let startMs: Int64
    public let endMs: Int64
    public let mode: String?
    public let ongoing: Bool
    public var fromName: String?
    public var toName: String?
    public let events: [DriveEventDto]
    public let counts: DriveEventCountsDto?
}

public enum TimelineItem: Equatable, Identifiable {
    case stay(TimelineStay)
    case trip(TimelineTrip)

    public var id: String {
        switch self { case .stay(let s): return s.id; case .trip(let t): return t.id }
    }
}

public enum TimelineBuilder {
    /// "Home → Office" when both ends are known, "Left Home" or "On the way to Office" when one is, else the kind of trip.
    public static func tripTitle(from: String?, to: String?, mode: String?) -> String {
        if let from, let to { return "\(from) → \(to)" }
        if let to { return "On the way to \(to)" }
        if let from { return "Left \(from)" }
        switch mode {
        case "DRIVE": return "Drive"
        case "CYCLE": return "Ride"
        default: return "Walk"
        }
    }

    /// Turns the server's stays and trips into cards. A trip is named after the saved places on either side when there are any.
    public static func items(from segments: [TimelineSegmentDto], zone: TimeZone = .current, nowMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000), today: Bool = false) -> [TimelineItem] {
        let clock = DateFormatter()
        clock.dateFormat = "HH:mm"
        clock.timeZone = zone
        clock.locale = Locale(identifier: "en_US_POSIX")
        func time(_ iso: String) -> String { Iso.parse(iso).map { clock.string(from: $0) } ?? "--:--" }

        var out: [TimelineItem] = []
        for (i, s) in segments.enumerated() {
            let range = "\(time(s.startedAt)) – \(time(s.endedAt))"
            let duration = Formatting.duration(ms: Int64(s.durationSec) * 1000)
            let endMs = Iso.ms(s.endedAt) ?? 0
            // Fixes arrive every couple of minutes, so "still going" means the last one is recent.
            let ongoing = today && i == segments.count - 1 && nowMs - endMs <= 15 * 60_000
            if s.type == "STAY" {
                out.append(.stay(TimelineStay(
                    id: "stay-\(s.startedAt)", title: s.place?.name ?? "Stayed in one place", range: range, duration: duration,
                    latitude: s.latitude, longitude: s.longitude, ongoing: ongoing, unnamed: s.place?.name == nil, isWork: s.place?.type == "WORK"
                )))
            } else {
                let from = i > 0 && segments[i - 1].type == "STAY" ? segments[i - 1].place?.name : nil
                let to = i + 1 < segments.count && segments[i + 1].type == "STAY" ? segments[i + 1].place?.name : nil
                let driving = s.mode == "DRIVE"
                let cycling = s.mode == "CYCLE"
                let path: [LatLon] = s.path.compactMap { $0.count >= 2 ? LatLon(lat: $0[0], lon: $0[1]) : nil }
                out.append(.trip(TimelineTrip(
                    id: "trip-\(s.startedAt)", title: tripTitle(from: from, to: to, mode: s.mode), range: range, duration: duration,
                    distance: Formatting.distance(s.distanceM ?? 0), avgSpeed: "\(Int((s.avgSpeedKmh ?? 0).rounded())) km/h", maxSpeed: "\(s.maxSpeedKmh ?? 0) km/h",
                    driving: driving || cycling, emoji: driving ? "🚗" : (cycling ? "🚲" : "🚶"), path: path, pathAt: s.pathAt,
                    startMs: Iso.ms(s.startedAt) ?? 0, endMs: endMs, mode: s.mode, ongoing: ongoing, fromName: from, toName: to, events: s.events, counts: s.eventCounts
                )))
            }
        }
        return out
    }

    /// Fills in names learned later (stays by index, trip ends by key) and retitles the trips next to them.
    public static func retitle(_ list: [TimelineItem], stayNames: [Int: String], tripEnds: [String: String] = [:]) -> [TimelineItem] {
        var result = list
        func stayName(_ idx: Int) -> String? {
            guard idx >= 0, idx < list.count, case .stay(let s) = list[idx] else { return nil }
            return s.unnamed ? stayNames[idx] : s.title
        }
        for (idx, item) in list.enumerated() {
            switch item {
            case .stay(var s):
                if let n = stayNames[idx], s.unnamed { s.title = n; s.unnamed = false; result[idx] = .stay(s) }
            case .trip(var t):
                let from = stayName(idx - 1) ?? t.fromName ?? tripEnds["\(t.id)#from"]
                let to = stayName(idx + 1) ?? t.toName ?? tripEnds["\(t.id)#to"]
                t.fromName = from
                t.toName = to
                t.title = tripTitle(from: from, to: to, mode: t.mode)
                result[idx] = .trip(t)
            }
        }
        return result
    }
}
