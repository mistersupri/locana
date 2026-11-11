import Foundation

public struct LatLon: Equatable, Hashable {
    public var lat: Double
    public var lon: Double
    public init(lat: Double, lon: Double) { self.lat = lat; self.lon = lon }
}

public enum Geo {
    public static let earthRadiusM = 6_371_008.8

    /// Great-circle distance in metres (haversine), the same formula the backend and the Android app use.
    public static func distanceM(_ a: LatLon, _ b: LatLon) -> Double {
        let dLat = (b.lat - a.lat) * .pi / 180
        let dLon = (b.lon - a.lon) * .pi / 180
        let h = pow(sin(dLat / 2), 2) + cos(a.lat * .pi / 180) * cos(b.lat * .pi / 180) * pow(sin(dLon / 2), 2)
        return 2 * earthRadiusM * asin(min(1, h.squareRoot()))
    }
}
