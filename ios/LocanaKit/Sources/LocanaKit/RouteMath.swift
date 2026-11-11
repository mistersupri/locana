import Foundation

public struct RoutePoint: Equatable {
    public let lat: Double, lon: Double
    public let index: Int
    public let metresFromStart: Double
}

public enum RouteMath {
    /// Where on the route the person was at time `t`, by interpolating between recorded points. Without per-point times
    /// the route is spread evenly over the trip. Times outside the trip clamp to its two ends.
    public static func routeAt(path: [LatLon], pathAt: [Int64], startMs: Int64, endMs: Int64, t: Int64) -> RoutePoint? {
        if path.isEmpty { return nil }
        if path.count == 1 { return RoutePoint(lat: path[0].lat, lon: path[0].lon, index: 0, metresFromStart: 0) }
        let times: [Int64] = pathAt.count == path.count
            ? pathAt
            : (0..<path.count).map { i in startMs + (endMs - startMs) * Int64(i) / Int64(path.count - 1) }
        let tt = min(max(t, times[0]), times[times.count - 1])
        var i = 1
        while i < times.count - 1 && times[i] < tt { i += 1 }
        let t0 = times[i - 1], t1 = times[i]
        let f = t1 > t0 ? min(max(Double(tt - t0) / Double(t1 - t0), 0), 1) : 0
        let a = path[i - 1], b = path[i]
        var metres = 0.0
        if i > 1 { for k in 1..<i { metres += Geo.distanceM(path[k - 1], path[k]) } }
        metres += Geo.distanceM(a, b) * f
        return RoutePoint(lat: a.lat + (b.lat - a.lat) * f, lon: a.lon + (b.lon - a.lon) * f, index: i - 1, metresFromStart: metres)
    }
}

public struct EdgeMark: Equatable {
    public let x: Double, y: Double
    /// Degrees, 0 = up, clockwise.
    public let angleDeg: Double
}

public enum EdgeMarks {
    /// Clamps a point outside the rectangle to its edge along the line from the centre, so the mark sits on the side
    /// where the person really is. Nil when the point is inside.
    public static func mark(px: Double, py: Double, left: Double, top: Double, right: Double, bottom: Double, inset: Double) -> EdgeMark? {
        if px >= left && px <= right && py >= top && py <= bottom { return nil }
        let cx = (left + right) / 2, cy = (top + bottom) / 2
        let dx = px - cx, dy = py - cy
        let halfW = (right - left) / 2 - inset
        let halfH = (bottom - top) / 2 - inset
        let sx = dx != 0 ? halfW / abs(dx) : Double.greatestFiniteMagnitude
        let sy = dy != 0 ? halfH / abs(dy) : Double.greatestFiniteMagnitude
        let scale = min(sx, sy)
        let angle = atan2(dx, -dy) * 180 / .pi
        return EdgeMark(x: cx + dx * scale, y: cy + dy * scale, angleDeg: angle)
    }
}
