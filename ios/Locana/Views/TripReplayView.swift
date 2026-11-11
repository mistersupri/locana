import SwiftUI
import MapKit
import LocanaKit

/// A small non-interactive map of a route, for the timeline cards.
struct RouteThumbnail: View {
    var path: [LatLon]
    var body: some View {
        let coords = path.map { CLLocationCoordinate2D(latitude: $0.lat, longitude: $0.lon) }
        Map(initialPosition: .rect(Self.rect(for: coords)), interactionModes: []) {
            MapPolyline(coordinates: coords).stroke(Palette.primaryStrong, style: StrokeStyle(lineWidth: 5, lineCap: .round, lineJoin: .round))
            if let a = coords.first { Annotation("", coordinate: a) { Circle().fill(Palette.primaryStrong).frame(width: 12, height: 12).overlay(Circle().stroke(.white, lineWidth: 2)) } }
            if let b = coords.last { Annotation("", coordinate: b) { Image(systemName: "flag.fill").font(.system(size: 14)).foregroundStyle(Palette.primaryStrong) } }
        }
        .allowsHitTesting(false)
    }

    static func rect(for coords: [CLLocationCoordinate2D]) -> MKMapRect {
        var rect = MKMapRect.null
        for c in coords { rect = rect.union(MKMapRect(origin: MKMapPoint(c), size: MKMapSize(width: 0.1, height: 0.1))) }
        if rect.isNull { return MKMapRect.world }
        return rect.insetBy(dx: -rect.size.width * 0.25 - 400, dy: -rect.size.height * 0.25 - 400)
    }
}

/// Full-screen replay of one trip: the route, and a bar whose handle is the person, moving from the time they left to the
/// time they arrived. Drag the bar to scrub, or press play. Risky driving moments appear on the route.
struct TripReplayView: View {
    var trip: TimelineTrip
    @Environment(\.dismiss) private var dismiss
    @State private var fraction: Double = 0
    @State private var playing = false
    @State private var speed: Double = 1
    @State private var follow = true
    @State private var position: MapCameraPosition = .automatic
    private let timer = Timer.publish(every: 1.0 / 30, on: .main, in: .common).autoconnect()

    private var duration: Int64 { max(1, trip.endMs - trip.startMs) }
    private var nowMs: Int64 { trip.startMs + Int64(Double(duration) * fraction) }
    private var here: RoutePoint? { RouteMath.routeAt(path: trip.path, pathAt: trip.pathAt, startMs: trip.startMs, endMs: trip.endMs, t: nowMs) }
    private var coords: [CLLocationCoordinate2D] { trip.path.map { CLLocationCoordinate2D(latitude: $0.lat, longitude: $0.lon) } }

    var body: some View {
        ZStack(alignment: .bottom) {
            if trip.path.count < 2 {
                EmptyStateView(title: "No route to replay", message: "This trip has too few recorded points.")
            } else {
                map
                VStack { header; Spacer() }
                controls
            }
        }
        .background(Palette.bg.ignoresSafeArea())
        .onReceive(timer) { _ in
            guard playing else { return }
            fraction = min(1, fraction + (1.0 / 30) * speed / 25)
            if fraction >= 1 { playing = false }
            if follow, let h = here { position = .camera(MapCamera(centerCoordinate: CLLocationCoordinate2D(latitude: h.lat, longitude: h.lon), distance: 1600)) }
        }
        .onAppear { position = .rect(RouteThumbnail.rect(for: coords)) }
    }

    private var map: some View {
        Map(position: $position) {
            MapPolyline(coordinates: coords).stroke(Palette.primaryStrong.opacity(0.28), style: StrokeStyle(lineWidth: 6, lineCap: .round, lineJoin: .round))
            if let h = here {
                let travelled = Array(coords.prefix(h.index + 1)) + [CLLocationCoordinate2D(latitude: h.lat, longitude: h.lon)]
                MapPolyline(coordinates: travelled).stroke(Palette.primaryStrong, style: StrokeStyle(lineWidth: 7, lineCap: .round, lineJoin: .round))
                Annotation("", coordinate: CLLocationCoordinate2D(latitude: h.lat, longitude: h.lon)) {
                    Text(trip.emoji).font(.system(size: 22)).frame(width: 44, height: 44).background(Circle().fill(.white).shadow(radius: 3)).overlay(Circle().stroke(Palette.primaryStrong, lineWidth: 2.5))
                }
            }
            if let a = coords.first { Annotation("", coordinate: a) { Circle().fill(Palette.primaryStrong).frame(width: 14, height: 14).overlay(Circle().stroke(.white, lineWidth: 2.5)) } }
            if let b = coords.last { Annotation("", coordinate: b) { Image(systemName: "flag.checkered").font(.system(size: 16, weight: .bold)).foregroundStyle(Palette.primaryStrong).padding(6).background(Circle().fill(.white)) } }
            ForEach(Array(trip.events.enumerated()), id: \.offset) { _, e in
                Annotation("", coordinate: CLLocationCoordinate2D(latitude: e.latitude, longitude: e.longitude)) {
                    Text(e.kind == "HARD_BRAKE" ? "🛑" : e.kind == "RAPID_ACCEL" ? "⚡" : "📵").font(.system(size: 18)).frame(width: 34, height: 34).background(Circle().fill(.white).shadow(radius: 2))
                }
            }
        }
        .ignoresSafeArea()
        .onMapCameraChange { _ in }
    }

    private var header: some View {
        HStack(spacing: 6) {
            Button { dismiss() } label: { Image(systemName: "chevron.left").font(.system(size: 18, weight: .bold)).frame(width: 44, height: 44) }.accessibilityLabel("Back")
            VStack(alignment: .leading, spacing: 1) {
                Text(trip.title).font(.display(17)).foregroundStyle(Palette.ink).lineLimit(1)
                Text("\(trip.range) · \(trip.distance) · \(trip.duration)").font(.body(12)).foregroundStyle(Palette.ink2).lineLimit(1)
            }
            Spacer()
            Button { follow.toggle() } label: {
                Image(systemName: "location.fill").font(.system(size: 16, weight: .bold)).foregroundStyle(follow ? Palette.onPrimary : Palette.ink)
                    .frame(width: 40, height: 40).background(Circle().fill(follow ? Palette.primary : Palette.card))
            }.accessibilityLabel(follow ? "Stop following the person" : "Follow the person")
        }
        .padding(.horizontal, 8).padding(.vertical, 6).card(radius: 24).padding(.horizontal, 12).padding(.top, 6)
    }

    private func clock(_ ms: Int64) -> String {
        let f = DateFormatter(); f.dateFormat = "HH:mm:ss"
        return f.string(from: Date(timeIntervalSince1970: Double(ms) / 1000))
    }

    private var controls: some View {
        VStack(spacing: 8) {
            HStack {
                Text(clock(nowMs)).font(.display(24)).foregroundStyle(Palette.ink)
                Spacer()
                Text("\(Formatting.distance(Int(here?.metresFromStart ?? 0))) of \(trip.distance)").font(.body(13)).foregroundStyle(Palette.ink2)
            }
            Slider(value: Binding(get: { fraction }, set: { fraction = $0; playing = false; if let h = here, follow { position = .camera(MapCamera(centerCoordinate: CLLocationCoordinate2D(latitude: h.lat, longitude: h.lon), distance: 1600)) } }), in: 0...1)
                .tint(Palette.primary).accessibilityLabel("Trip position").accessibilityValue(clock(nowMs))
            HStack {
                Text(clock(trip.startMs)).font(.body(12)).foregroundStyle(Palette.ink2)
                Spacer()
                Text(clock(trip.endMs)).font(.body(12)).foregroundStyle(Palette.ink2)
            }
            HStack(spacing: 10) {
                Button { if fraction >= 1 { fraction = 0 }; playing.toggle() } label: {
                    Image(systemName: playing ? "pause.fill" : "play.fill").font(.system(size: 20, weight: .bold)).foregroundStyle(Palette.onPrimary).frame(width: 52, height: 52).background(Circle().fill(Palette.primary))
                }.accessibilityLabel(playing ? "Pause" : "Play")
                ForEach([1.0, 2.0, 4.0], id: \.self) { s in
                    Button { speed = s } label: {
                        Text("\(Int(s))×").font(.body(14, weight: .heavy)).foregroundStyle(speed == s ? Palette.primary : Palette.ink2)
                            .padding(.horizontal, 14).padding(.vertical, 9)
                            .background(RoundedRectangle(cornerRadius: 14).fill(speed == s ? Palette.primarySoft : Palette.card))
                            .overlay(RoundedRectangle(cornerRadius: 14).stroke(speed == s ? Palette.primary : Palette.line, lineWidth: 1.5))
                    }
                }
                Spacer()
                if let c = trip.counts, c.hardBrake + c.rapidAccel + c.phoneUse > 0 {
                    Text([c.hardBrake > 0 ? "🛑 \(c.hardBrake)" : nil, c.rapidAccel > 0 ? "⚡ \(c.rapidAccel)" : nil, c.phoneUse > 0 ? "📵 \(c.phoneUse)" : nil].compactMap { $0 }.joined(separator: "  ")).font(.body(14, weight: .bold))
                }
            }
        }
        .padding(.horizontal, 20).padding(.top, 14).padding(.bottom, 10)
        .background(UnevenRoundedRectangle(topLeadingRadius: 28, topTrailingRadius: 28).fill(Palette.card).shadow(color: .black.opacity(0.16), radius: 12, y: -2).ignoresSafeArea(edges: .bottom))
    }
}
