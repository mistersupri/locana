import SwiftUI
import MapKit
import LocanaKit

@MainActor
final class DetailModel: ObservableObject {
    enum Load { case loading, failed(String), loaded([TimelineItem], points: Int) }
    @Published var load: Load = .loading
    @Published var day = Calendar.current.startOfDay(for: Date())
    private var names: [Int: String] = [:]
    private var tripEnds: [String: String] = [:]

    var isToday: Bool { Calendar.current.isDateInToday(day) }

    private var dayString: String {
        let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"; f.locale = Locale(identifier: "en_US_POSIX"); f.timeZone = .current
        return f.string(from: day)
    }

    func shift(_ days: Int, api: APIClient, circleId: String, userId: String) async {
        guard let next = Calendar.current.date(byAdding: .day, value: days, to: day), next <= Date() else { return }
        day = next
        await reload(api: api, circleId: circleId, userId: userId)
    }

    func reload(api: APIClient, circleId: String, userId: String) async {
        names = [:]; tripEnds = [:]
        do {
            let t = try await api.timeline(circleId: circleId, userId: userId, day: dayString, tzOffsetMinutes: TimeZone.current.secondsFromGMT() / 60)
            let items = TimelineBuilder.items(from: t.segments, zone: .current, today: isToday)
            load = .loaded(items, points: t.points ?? 0)
            await name(items)
        } catch { load = .failed((error as? LocalizedError)?.errorDescription ?? "Couldn't load places and trips") }
    }

    /// Names unnamed stays by the nearest place Apple Maps knows, then trips by their two ends.
    private func name(_ items: [TimelineItem]) async {
        for (idx, item) in items.enumerated() {
            guard case .stay(let s) = item, s.unnamed, let la = s.latitude, let lo = s.longitude, let n = await Self.placeName(la, lo) else { continue }
            names[idx] = n
            apply(items)
        }
        for (idx, item) in items.enumerated() {
            guard case .trip(let t) = item, let first = t.path.first, let last = t.path.last else { continue }
            let beforeIsStay: Bool = { if idx > 0, case .stay = items[idx - 1] { return true }; return false }()
            let afterIsStay: Bool = { if idx + 1 < items.count, case .stay = items[idx + 1] { return true }; return false }()
            if !beforeIsStay, t.fromName == nil, let n = await Self.placeName(first.lat, first.lon) { tripEnds["\(t.id)#from"] = n }
            if !afterIsStay, !t.ongoing, t.toName == nil, let n = await Self.placeName(last.lat, last.lon) { tripEnds["\(t.id)#to"] = n }
            apply(items)
        }
    }

    private func apply(_ items: [TimelineItem]) {
        if case .loaded(_, let p) = load { load = .loaded(TimelineBuilder.retitle(items, stayNames: names, tripEnds: tripEnds), points: p) }
    }

    nonisolated static func placeName(_ lat: Double, _ lon: Double) async -> String? {
        let center = CLLocationCoordinate2D(latitude: lat, longitude: lon)
        let request = MKLocalPointsOfInterestRequest(center: center, radius: 60)
        guard let response = try? await MKLocalSearch(request: request).start() else { return nil }
        let here = CLLocation(latitude: lat, longitude: lon)
        return response.mapItems.min { ($0.placemark.location?.distance(from: here) ?? .infinity) < ($1.placemark.location?.distance(from: here) ?? .infinity) }?.name
    }
}

struct MemberDetailView: View {
    @EnvironmentObject private var model: AppModel
    var pin: MemberPin
    var onBack: () -> Void
    var onOpenTrip: (TimelineTrip) -> Void
    @StateObject private var detail = DetailModel()

    private var circleId: String { model.selectedCircleId ?? "" }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    Button { onBack() } label: { Label("Back", systemImage: "chevron.left").font(.body(15, weight: .heavy)) }.accessibilityLabel("Back to the circle")
                    Spacer()
                    if let b = pin.batteryPct, pin.presence != .unavailable { Pill(text: "\(b)%", bg: Palette.card, fg: b <= 20 ? Palette.onCoral : Palette.onMint, icon: "battery.75percent") }
                }
                .padding(.horizontal, 20)
                hero.padding(.horizontal, 16)
                dayBar.padding(.horizontal, 16)
                switch detail.load {
                case .loading: LoadingView()
                case .failed(let m): ErrorView(message: m) { Task { await detail.reload(api: model.api, circleId: circleId, userId: pin.userId) } }
                case .loaded(let items, let points):
                    if items.isEmpty {
                        EmptyStateView(
                            title: points > 0 ? "Collecting today" : "Nothing recorded",
                            message: points > 0 ? "\(points) positions so far. A place shows up after 10 minutes in one spot, a trip once about 30 m have been covered." : "A place is recorded after 10 minutes in one spot and a trip while travelling, once location is on and Locana has been running for a few minutes.",
                            illustration: "house"
                        )
                    } else {
                        ForEach(Array(items.enumerated()), id: \.element.id) { i, item in
                            Group {
                                switch item {
                                case .stay(let s): StayCard(stay: s)
                                case .trip(let t): TripCard(trip: t).onTapGesture { onOpenTrip(t) }
                                }
                            }
                            .padding(.horizontal, 16).enterRise(delay: Double(min(i, 6)) * 0.04)
                        }
                    }
                }
            }
            .padding(.bottom, 30)
        }
        .task(id: pin.userId) { await detail.reload(api: model.api, circleId: circleId, userId: pin.userId) }
    }

    private var status: (title: String, subtitle: String, art: String) {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let who = pin.isMe ? "You · " : ""
        switch pin.presence {
        case .hidden: return (who + "Hidden", "\(pin.name) is not sharing their position with you.", "shield")
        case .unavailable: return (who + "No position yet", "Nothing received from this phone.", "compass")
        default: break
        }
        if pin.presence == .stale, let at = pin.recordedAtMs { return (who + "Last seen", Freshness.ageLabel(recordedAtMs: at, nowMs: now), "compass") }
        if pin.movement == "STAYING" {
            let since = pin.stationarySinceMs
            return (who + "Staying", since.map { "Here for \(Formatting.duration(ms: now - $0))" } ?? "In one place", "house")
        }
        if pin.movement == "MOVING" {
            let v = pin.speedMps.map { Formatting.speedKmh(mps: $0) } ?? "on the move"
            switch pin.mode ?? "WALKING" { case "DRIVING": return (who + "Driving", v, "car"); case "CYCLING": return (who + "Cycling", v, "sneaker"); default: return (who + "Walking", v, "sneaker") }
        }
        return (who + "Live", pin.recordedAtMs.map { "Updated " + Freshness.ageLabel(recordedAtMs: $0, nowMs: now) } ?? "", "compass")
    }

    private var hero: some View {
        HStack(spacing: 8) {
            VStack(alignment: .leading, spacing: 6) {
                Text(status.title).font(.display(26)).foregroundStyle(Palette.ink).lineLimit(2).minimumScaleFactor(0.8)
                Text(status.subtitle).font(.body(14)).foregroundStyle(Palette.ink2).lineLimit(3)
            }
            Spacer(minLength: 4)
            Illustration(name: status.art, size: 92)
                .frame(width: 112, height: 112).background(RoundedRectangle(cornerRadius: 30, style: .continuous).stroke(.white, lineWidth: 4))
        }
        .padding(20)
        .background(RoundedRectangle(cornerRadius: 28, style: .continuous).fill(LinearGradient(colors: [Palette.primarySoft, Palette.card], startPoint: .topLeading, endPoint: .bottomTrailing)))
    }

    private var dayBar: some View {
        HStack {
            Button { Task { await detail.shift(-1, api: model.api, circleId: circleId, userId: pin.userId) } } label: { Image(systemName: "chevron.left").frame(width: 44, height: 40).background(RoundedRectangle(cornerRadius: 12).fill(Palette.primarySoft)) }.accessibilityLabel("Previous day")
            Spacer()
            Text(dayLabel).font(.display(18)).foregroundStyle(Palette.ink)
            Spacer()
            Button { Task { await detail.shift(1, api: model.api, circleId: circleId, userId: pin.userId) } } label: { Image(systemName: "chevron.right").frame(width: 44, height: 40).background(RoundedRectangle(cornerRadius: 12).fill(Palette.primarySoft)) }.disabled(detail.isToday).opacity(detail.isToday ? 0.4 : 1).accessibilityLabel("Next day")
        }
        .padding(8).card(radius: 20)
    }

    private var dayLabel: String {
        if detail.isToday { return "Today" }
        if Calendar.current.isDateInYesterday(detail.day) { return "Yesterday" }
        return detail.day.formatted(.dateTime.weekday(.abbreviated).day().month(.abbreviated))
    }
}

struct StayCard: View {
    var stay: TimelineStay
    var body: some View {
        HStack(spacing: 14) {
            Illustration(name: stay.isWork ? "office" : "house", size: 44).frame(width: 56, height: 56).background(RoundedRectangle(cornerRadius: 18).fill(Palette.coralSoft))
            VStack(alignment: .leading, spacing: 6) {
                Text(stay.title).font(.body(17, weight: .heavy)).foregroundStyle(Palette.ink)
                Text(stay.range).font(.body(13)).foregroundStyle(Palette.ink2)
                FlowPills {
                    Pill(text: "Stayed \(stay.duration)", bg: Palette.coralSoft, fg: Palette.onCoral, icon: "hourglass")
                    if stay.ongoing { Pill(text: "Happening now") }
                }
            }
            Spacer(minLength: 0)
        }
        .padding(16).frame(maxWidth: .infinity, alignment: .leading).card()
        .accessibilityElement(children: .combine)
    }
}

struct TripCard: View {
    var trip: TimelineTrip
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(trip.title).font(.body(17, weight: .heavy)).foregroundStyle(Palette.ink)
                    Text("\(trip.range) · \(trip.distance)").font(.body(13)).foregroundStyle(Palette.ink2)
                }
                Spacer()
                Illustration(name: trip.driving && trip.mode == "DRIVE" ? "car" : "sneaker", size: 42).frame(width: 52, height: 52).background(RoundedRectangle(cornerRadius: 16).fill(Palette.skySoft))
            }
            if trip.path.count > 1 {
                RouteThumbnail(path: trip.path).frame(height: 120).clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            }
            FlowPills {
                Pill(text: "Took \(trip.duration)", bg: Palette.skySoft, fg: Palette.onSky, icon: "timer")
                Pill(text: "avg \(trip.avgSpeed)", bg: Palette.primarySoft, fg: Palette.ink)
                if trip.driving { Pill(text: "top \(trip.maxSpeed)", bg: Palette.yellowSoft, fg: Palette.onAmber, icon: "bolt.fill") }
                if let c = trip.counts {
                    if c.hardBrake > 0 { Pill(text: "Hard braking ×\(c.hardBrake)", bg: Palette.coralSoft, fg: Palette.onCoral) }
                    if c.rapidAccel > 0 { Pill(text: "Rapid acceleration ×\(c.rapidAccel)", bg: Palette.yellowSoft, fg: Palette.onAmber) }
                    if c.phoneUse > 0 { Pill(text: "Phone use ×\(c.phoneUse)", bg: Palette.skySoft, fg: Palette.onSky) }
                }
                if trip.ongoing { Pill(text: "Now") }
            }
            Text("Tap to replay this trip").font(.body(12, weight: .semibold)).foregroundStyle(Palette.ink3)
        }
        .padding(14).frame(maxWidth: .infinity, alignment: .leading).card()
        .accessibilityElement(children: .combine)
        .accessibilityHint("Opens the route replay")
        .accessibilityAddTraits(.isButton)
    }
}

/// Lays children out left to right and wraps onto a new line when the row is full.
struct FlowLayout: Layout {
    var spacing: CGFloat = 6

    private func arrange(width: CGFloat, subviews: Subviews) -> (positions: [CGPoint], size: CGSize) {
        var x: CGFloat = 0, y: CGFloat = 0, rowH: CGFloat = 0, maxW: CGFloat = 0
        var positions: [CGPoint] = []
        for s in subviews {
            let size = s.sizeThatFits(.unspecified)
            if x > 0 && x + size.width > width { x = 0; y += rowH + spacing; rowH = 0 }
            positions.append(CGPoint(x: x, y: y))
            x += size.width + spacing
            rowH = max(rowH, size.height)
            maxW = max(maxW, x - spacing)
        }
        return (positions, CGSize(width: maxW, height: y + rowH))
    }

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        arrange(width: proposal.width ?? 10_000, subviews: subviews).size
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let laid = arrange(width: bounds.width, subviews: subviews)
        for (i, s) in subviews.enumerated() {
            s.place(at: CGPoint(x: bounds.minX + laid.positions[i].x, y: bounds.minY + laid.positions[i].y), proposal: .unspecified)
        }
    }
}

/// Pills that wrap onto more lines instead of squeezing letter by letter.
struct FlowPills<Content: View>: View {
    private let content: Content
    init(@ViewBuilder content: () -> Content) { self.content = content() }
    var body: some View { FlowLayout { content }.frame(maxWidth: .infinity, alignment: .leading) }
}
