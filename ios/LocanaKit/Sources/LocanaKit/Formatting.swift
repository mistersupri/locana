import Foundation

/// Text for numbers, identical in wording to the Android app so a circle sees the same thing on both phones.
public enum Formatting {
    public static func distance(_ m: Int) -> String {
        if m < 1000 { return "\(m) m" }
        let km = (Double(m) / 100).rounded() / 10
        return "\(km) km"
    }

    public static func progress(value: Int, target: Int, unit: String) -> String {
        unit == "meters" ? "\(distance(value)) of \(distance(target))" : "\(value) of \(target) \(unit)"
    }

    public static func initials(_ name: String) -> String {
        let parts = name.split(whereSeparator: { $0.isWhitespace }).map(String.init)
        switch parts.count {
        case 0: return "?"
        case 1: return String(parts[0].prefix(2)).uppercased()
        default: return (String(parts[0].prefix(1)) + String(parts[1].prefix(1))).uppercased()
        }
    }

    /// "42 mins", "3 hrs, 52 mins", "2 days, 4 hrs".
    public static func duration(ms: Int64) -> String {
        let minutes = max(0, ms / 60_000)
        func unit(_ n: Int64, _ one: String, _ many: String) -> String { n == 1 ? "\(n) \(one)" : "\(n) \(many)" }
        if minutes < 1 { return "under a minute" }
        if minutes < 60 { return unit(minutes, "min", "mins") }
        if minutes < 24 * 60 {
            let h = minutes / 60, m = minutes % 60
            return m == 0 ? unit(h, "hr", "hrs") : "\(unit(h, "hr", "hrs")), \(unit(m, "min", "mins"))"
        }
        let d = minutes / (24 * 60), h = (minutes % (24 * 60)) / 60
        return h == 0 ? unit(d, "day", "days") : "\(unit(d, "day", "days")), \(unit(h, "hr", "hrs"))"
    }

    public static func speedKmh(mps: Double) -> String { "\(Int((mps * 3.6).rounded())) km/h" }
}

public enum Presence: String { case live, stale, unavailable, hidden }

public enum Freshness {
    public static let staleAfterMs: Int64 = 10 * 60_000

    /// A LIVE fix becomes STALE as time passes on the device; a cached position is never shown as live.
    public static func presence(server: Presence, recordedAtMs: Int64?, nowMs: Int64) -> Presence {
        if server == .hidden || server == .unavailable { return server }
        guard let at = recordedAtMs else { return .unavailable }
        return nowMs - at > staleAfterMs ? .stale : .live
    }

    /// "just now", "4 min ago", "2 h ago", "3 d ago".
    public static func ageLabel(recordedAtMs: Int64, nowMs: Int64) -> String {
        let s = max(0, (nowMs - recordedAtMs) / 1000)
        if s < 45 { return "just now" }
        if s < 3600 { return "\((s + 30) / 60) min ago" }
        if s < 86_400 { return "\(s / 3600) h ago" }
        return "\(s / 86_400) d ago"
    }
}

public enum Iso {
    private static let withFraction: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f
    }()
    private static let plain = ISO8601DateFormatter()

    public static func parse(_ s: String) -> Date? { withFraction.date(from: s) ?? plain.date(from: s) }
    public static func ms(_ s: String) -> Int64? { parse(s).map { Int64($0.timeIntervalSince1970 * 1000) } }
    public static func string(ms: Int64) -> String { withFraction.string(from: Date(timeIntervalSince1970: Double(ms) / 1000)) }
}
