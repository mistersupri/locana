import SwiftUI
import MapKit
import LocanaKit

enum DrawerDetent: CGFloat { case peek = 0, mid = 1, full = 2 }

struct MapScreen: View {
    @EnvironmentObject private var model: AppModel
    @State private var position: MapCameraPosition = .userLocation(fallback: .automatic)
    @State private var focusedId: String?
    @State private var detent: DrawerDetent = .peek
    @State private var dragOffset: CGFloat = 0
    @State private var camTick = 0
    @State private var showProfile = false
    @State private var showNotifications = false
    @State private var quickMessages = defaultQuickMessages
    @State private var toast: String?
    @State private var landmarks: [LandmarkDto] = []
    @State private var claiming = false
    @State private var replay: TimelineTrip?

    private var pins: [MemberPin] { model.pins }
    private var focused: MemberPin? { pins.first { $0.userId == focusedId } }

    private func heights(_ total: CGFloat) -> [CGFloat] { [178, total * 0.52, total * 0.86] }

    var body: some View {
        GeometryReader { geo in
            let hs = heights(geo.size.height)
            let base = hs[Int(detent.rawValue)]
            let drawerH = min(max(base - dragOffset, hs[0]), hs[2])
            ZStack(alignment: .bottom) {
                mapLayer(size: geo.size, bottomInset: drawerH, safeTop: geo.safeAreaInsets.top)
                topBar.frame(maxHeight: .infinity, alignment: .top)
                floating(drawerH: drawerH)
                drawer(height: drawerH, hs: hs)
            }
            .ignoresSafeArea(edges: .top)
        }
        .sheet(isPresented: $showProfile) { NavigationStack { ProfileView() }.environmentObject(model) }
        .sheet(isPresented: $showNotifications) { NavigationStack { NotificationsView() }.environmentObject(model) }
        .fullScreenCover(item: $replay) { trip in TripReplayView(trip: trip) }
        .task(id: model.selectedCircleId) { await loadLandmarks() }
        .task(id: model.pendingFocusUserId) {
            guard let id = model.pendingFocusUserId, pins.contains(where: { $0.userId == id }) else { return }
            focus(id); model.pendingFocusUserId = nil
        }
        .onChange(of: focused?.latitude) { _, _ in followFocused() }
        .onChange(of: focused?.longitude) { _, _ in followFocused() }
        .task { if let list = try? await model.api.quickMessages(), !list.isEmpty { quickMessages = list } }
    }

    // MARK: map

    @ViewBuilder
    private func mapLayer(size: CGSize, bottomInset: CGFloat, safeTop: CGFloat) -> some View {
        MapReader { proxy in
            Map(position: $position) {
                ForEach(pins.filter { $0.coordinate != nil && $0.presence != .hidden }) { p in
                    if let c = p.coordinate {
                        Annotation("", coordinate: c, anchor: .bottom) {
                            PinView(pin: p, focused: p.userId == focusedId)
                                .onTapGesture { focus(p.userId) }
                        }
                        .annotationTitles(.hidden)
                    }
                }
                ForEach(landmarks) { l in
                    Annotation("", coordinate: CLLocationCoordinate2D(latitude: l.latitude, longitude: l.longitude), anchor: .center) {
                        Image(systemName: l.visited ? "checkmark.seal.fill" : "lock.fill")
                            .font(.system(size: 13, weight: .bold)).foregroundStyle(.white)
                            .frame(width: 30, height: 30).background(Circle().fill(l.visited ? Palette.mint : Palette.primaryStrong))
                            .shadow(radius: 2)
                    }
                    .annotationTitles(.hidden)
                }
                ForEach(model.places) { pl in
                    MapCircle(center: CLLocationCoordinate2D(latitude: pl.latitude, longitude: pl.longitude), radius: CLLocationDistance(pl.radiusM))
                        .foregroundStyle(Palette.mint.opacity(0.12)).stroke(Palette.mint.opacity(0.6), lineWidth: 1.5)
                }
            }
            .mapStyle(.standard(elevation: .flat))
            .mapControls { MapCompass() }
            .onMapCameraChange(frequency: .continuous) { _ in camTick &+= 1 }
            .overlay {
                // People who are off the screen: an avatar on the nearest edge, pointing their way.
                let _ = camTick
                ZStack {
                    ForEach(pins.filter { $0.coordinate != nil && $0.presence != .hidden && $0.userId != focusedId }) { p in
                        if let c = p.coordinate, let pt = proxy.convert(c, to: .local),
                           let mark = EdgeMarks.mark(px: Double(pt.x), py: Double(pt.y), left: 0, top: Double(safeTop) + 70, right: Double(size.width), bottom: Double(size.height - bottomInset) - 8, inset: 28) {
                            OffscreenMark(pin: p, angle: mark.angleDeg)
                                .position(x: CGFloat(mark.x), y: CGFloat(mark.y))
                                .onTapGesture { focus(p.userId) }
                        }
                    }
                }
                .allowsHitTesting(true)
            }
        }
    }

    private var topBar: some View {
        HStack(spacing: 10) {
            roundButton("gearshape.fill", label: "Profile and settings") { showProfile = true }
            Menu {
                ForEach(model.circles) { c in Button(c.name) { Task { await model.select(c.id) } } }
                Divider()
                Button("Create or join a circle") { model.selectedTab = .circle }
            } label: {
                HStack {
                    Text(model.circles.first { $0.id == model.selectedCircleId }?.name ?? "No circle").font(.display(18)).foregroundStyle(Palette.ink).lineLimit(1)
                    Spacer()
                    Image(systemName: "chevron.down").font(.system(size: 13, weight: .bold)).foregroundStyle(Palette.ink)
                }
                .padding(.horizontal, 16).frame(height: 48).background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(Palette.card)).shadow(color: .black.opacity(0.12), radius: 6, y: 2)
            }
            ZStack(alignment: .topTrailing) {
                roundButton("bell.fill", label: model.unread > 0 ? "Notifications, \(model.unread) unread" : "Notifications") { showNotifications = true }
                if model.unread > 0 { Text(model.unread > 9 ? "9+" : "\(model.unread)").font(.system(size: 11, weight: .heavy)).foregroundStyle(.white).padding(4).background(Circle().fill(Palette.coralStrong)).offset(x: 4, y: -4) }
            }
        }
        .padding(.horizontal, 14).padding(.top, 58)
    }

    private func roundButton(_ icon: String, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon).font(.system(size: 18, weight: .semibold)).foregroundStyle(Palette.ink)
                .frame(width: 48, height: 48).background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(Palette.card)).shadow(color: .black.opacity(0.12), radius: 6, y: 2)
        }
        .accessibilityLabel(label)
    }

    // MARK: floating controls

    private var claimable: LandmarkDto? {
        guard let f = model.location.latest, f.horizontalAccuracy <= 40, Date().timeIntervalSince(f.timestamp) < 120 else { return nil }
        return landmarks.filter { !$0.visited }
            .map { ($0, Geo.distanceM(LatLon(lat: f.coordinate.latitude, lon: f.coordinate.longitude), LatLon(lat: $0.latitude, lon: $0.longitude))) }
            .filter { $0.1 <= Double($0.0.radiusM) }.min { $0.1 < $1.1 }?.0
    }

    @ViewBuilder
    private func floating(drawerH: CGFloat) -> some View {
        VStack(alignment: .trailing, spacing: 10) {
            Spacer()
            if let e = model.encounter, focusedId == nil {
                HStack(spacing: 10) {
                    Text(e.monster.emoji).font(.system(size: 34))
                    VStack(alignment: .leading, spacing: 2) {
                        Text("A wild \(e.monster.name) appeared!").font(.body(14, weight: .heavy)).foregroundStyle(Palette.ink)
                        Text("+\(e.rewardCoins) coins, +\(e.rewardXp) XP if you win").font(.body(12)).foregroundStyle(Palette.ink2)
                    }
                    Spacer(minLength: 0)
                    Button("Fight") { model.selectedTab = .play; model.showBattle = true }.buttonStyle(.chunky(compact: true))
                }
                .padding(12).card(radius: 22).padding(.horizontal, 16).transition(.move(edge: .bottom).combined(with: .opacity))
            }
            HStack(alignment: .bottom) {
                VStack(alignment: .leading, spacing: 8) {
                    if let toast { Pill(text: toast, bg: Palette.card, fg: Palette.ink, icon: nil).shadow(radius: 3).transition(.opacity) }
                    if let f = focused, !f.isMe, !f.blockedByMe { QuickMessageRow(messages: quickMessages) { m in sendQuick(m, to: f) } }
                    else if focusedId == nil, let l = claimable {
                        HStack(spacing: 10) {
                            Image(systemName: "lock.open.fill").foregroundStyle(Palette.primary)
                            VStack(alignment: .leading, spacing: 1) {
                                Text(l.name).font(.body(14, weight: .heavy)).foregroundStyle(Palette.ink).lineLimit(1)
                                Text("You're here. Unlock it for +\(l.rewardXp) XP").font(.body(12)).foregroundStyle(Palette.ink2)
                            }
                            Spacer(minLength: 0)
                            Button(claiming ? "…" : "Claim") { Task { await claim(l) } }.buttonStyle(.chunky(compact: true))
                        }
                        .padding(12).card(radius: 22)
                    }
                }
                Spacer(minLength: 8)
                VStack(spacing: 10) {
                    SosButton { Task { await model.triggerSOS(reason: "MANUAL") } }
                    roundButton("location.fill", label: "Centre on me") { centreOnMe() }
                }
                .padding(.trailing, 14)
            }
            .padding(.leading, 0)
        }
        .padding(.bottom, drawerH + 10)
        .animation(.spring(response: 0.4, dampingFraction: 0.8), value: model.encounter)
        .animation(.spring(response: 0.35, dampingFraction: 0.8), value: focusedId)
    }

    // MARK: drawer

    private func drawer(height: CGFloat, hs: [CGFloat]) -> some View {
        VStack(spacing: 0) {
            Capsule().fill(Palette.line).frame(width: 42, height: 5).padding(.top, 8).padding(.bottom, 8)
                .frame(maxWidth: .infinity).contentShape(Rectangle())
                .gesture(
                    DragGesture()
                        .onChanged { dragOffset = $0.translation.height }
                        .onEnded { v in
                            let target = hs[Int(detent.rawValue)] - v.translation.height
                            let nearest = hs.indices.min { abs(hs[$0] - target) < abs(hs[$1] - target) } ?? 0
                            withAnimation(.spring(response: 0.38, dampingFraction: 0.85)) { detent = DrawerDetent(rawValue: CGFloat(nearest)) ?? .peek; dragOffset = 0 }
                        }
                )
            Group {
                if let f = focused {
                    MemberDetailView(pin: f, onBack: { unfocus() }, onOpenTrip: { replay = $0 })
                } else {
                    CircleSheet(pins: pins, onSelect: { focus($0) })
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .frame(height: height + 60, alignment: .top)
        .background(UnevenRoundedRectangle(topLeadingRadius: 30, topTrailingRadius: 30).fill(Palette.bg).shadow(color: .black.opacity(0.18), radius: 12, y: -2))
        .offset(y: 60)
    }

    // MARK: actions

    private func focus(_ id: String) {
        focusedId = id
        withAnimation(.spring(response: 0.4, dampingFraction: 0.85)) { detent = .mid }
        if let c = pins.first(where: { $0.userId == id })?.coordinate {
            withAnimation(.easeInOut(duration: 0.6)) { position = .camera(MapCamera(centerCoordinate: c, distance: 1400)) }
        }
    }

    private func unfocus() {
        focusedId = nil
        withAnimation(.spring(response: 0.4, dampingFraction: 0.85)) { detent = .peek }
    }

    private func followFocused() {
        guard let c = focused?.coordinate else { return }
        withAnimation(.easeInOut(duration: 0.7)) { position = .camera(MapCamera(centerCoordinate: c, distance: 1400)) }
    }

    private func centreOnMe() {
        if let c = model.location.latest?.coordinate { withAnimation { position = .camera(MapCamera(centerCoordinate: c, distance: 1400)) } }
        else { withAnimation { position = .userLocation(fallback: .automatic) } }
    }

    private func sendQuick(_ m: QuickMessageDefDto, to p: MemberPin) {
        guard let circle = model.selectedCircleId else { return }
        Task {
            do {
                _ = try await model.api.sendQuickMessage(circleId: circle, userId: p.userId, code: m.code)
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                flash("Sent \(m.emoji) to \(p.name)")
            } catch { flash((error as? LocalizedError)?.errorDescription ?? "Couldn't send") }
        }
    }

    private func flash(_ text: String) {
        withAnimation { toast = text }
        Task { try? await Task.sleep(nanoseconds: 2_400_000_000); withAnimation { toast = nil } }
    }

    private func loadLandmarks() async {
        let c = model.location.latest?.coordinate ?? CLLocationCoordinate2D(latitude: -6.1754, longitude: 106.8272)
        if let list = try? await model.api.landmarks(lat: c.latitude, lon: c.longitude) { landmarks = list }
    }

    private func claim(_ l: LandmarkDto) async {
        guard let f = model.location.latest else { return }
        claiming = true
        do {
            let r = try await model.api.claimLandmark(l.id, lat: f.coordinate.latitude, lon: f.coordinate.longitude, accuracyM: f.horizontalAccuracy)
            flash(r.firstVisit ? "\(r.name) unlocked! +\(r.rewardXp) XP" : "\(r.name) was already unlocked")
            await loadLandmarks(); await model.refreshMe()
        } catch { flash((error as? LocalizedError)?.errorDescription ?? "Couldn't claim") }
        claiming = false
    }
}

// MARK: pins

struct PinView: View {
    @EnvironmentObject private var model: AppModel
    var pin: MemberPin
    var focused: Bool

    private var caption: (label: String, value: String, emoji: String)? {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        if pin.presence == .stale, let at = pin.recordedAtMs { return ("Last seen", Freshness.ageLabel(recordedAtMs: at, nowMs: now), "") }
        if pin.movement == "STAYING", let since = pin.stationarySinceMs { return ("Here for", Formatting.duration(ms: now - since), "🏡") }
        if pin.movement == "MOVING" {
            let v = pin.speedMps.map { Formatting.speedKmh(mps: $0) } ?? "on the move"
            switch pin.mode ?? ((pin.speedMps ?? 0) >= 5.5 ? "DRIVING" : "WALKING") {
            case "DRIVING": return ("Driving", v, "🚗")
            case "CYCLING": return ("Cycling", v, "🚲")
            default: return ("Walking", v, "🚶")
            }
        }
        return nil
    }

    var body: some View {
        VStack(spacing: 2) {
            if let c = caption {
                HStack(spacing: 8) {
                    if !c.emoji.isEmpty { Text(c.emoji).font(.system(size: 18)).frame(width: 30, height: 30).background(RoundedRectangle(cornerRadius: 10).fill(Palette.primarySoft)) }
                    VStack(alignment: .leading, spacing: 0) {
                        Text(c.label).font(.body(10, weight: .medium)).foregroundStyle(Palette.ink2)
                        Text(c.value).font(.body(13, weight: .heavy)).foregroundStyle(Palette.ink)
                    }
                }
                .padding(.horizontal, 8).padding(.vertical, 5).background(Capsule().fill(Palette.card)).shadow(color: .black.opacity(0.18), radius: 4, y: 2)
            }
            ZStack {
                if focused { Circle().fill(Palette.primaryStrong.opacity(0.22)).frame(width: 96, height: 96).offset(y: 34) }
                VStack(spacing: -2) {
                    AvatarView(name: pin.name, avatarKey: pin.avatarKey, userId: pin.userId, photoVersion: pin.photoVersion, frameKey: pin.frameKey, size: 58, dim: pin.presence == .stale)
                        .padding(3)
                        .background(Circle().fill(focused ? Palette.primaryStrong : Color.white).shadow(color: .black.opacity(0.3), radius: 4, y: 2))
                    Image(systemName: "arrowtriangle.down.fill").font(.system(size: 12)).foregroundStyle(focused ? Palette.primaryStrong : Color.white).shadow(color: .black.opacity(0.25), radius: 2, y: 1)
                    Circle().fill(pin.presence == .stale ? Palette.ink3 : Palette.primaryStrong).frame(width: 12, height: 12).overlay(Circle().stroke(.white, lineWidth: 2.5))
                }
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(pin.name)
        .accessibilityAddTraits(.isButton)
    }
}

struct OffscreenMark: View {
    var pin: MemberPin
    var angle: Double
    var body: some View {
        ZStack {
            AvatarView(name: pin.name, avatarKey: pin.avatarKey, userId: pin.userId, photoVersion: pin.photoVersion, frameKey: pin.frameKey, size: 44)
                .padding(3).background(Circle().fill(.white).shadow(color: .black.opacity(0.3), radius: 4, y: 2))
            Image(systemName: "arrowtriangle.up.fill").font(.system(size: 14)).foregroundStyle(.white).shadow(radius: 1)
                .offset(y: -34).rotationEffect(.degrees(angle), anchor: UnitPoint(x: 0.5, y: 2.4))
        }
        .frame(width: 56, height: 56)
        .accessibilityLabel("\(pin.name) is off the screen. Tap to find them.")
        .accessibilityAddTraits(.isButton)
    }
}

// MARK: drawer content

struct CircleSheet: View {
    @EnvironmentObject private var model: AppModel
    var pins: [MemberPin]
    var onSelect: (String) -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(model.circles.first { $0.id == model.selectedCircleId }?.name ?? "No circle yet").font(.display(28)).foregroundStyle(Palette.ink)
                    Text(subtitle).font(.body(15)).foregroundStyle(Palette.ink2)
                }
                .padding(.horizontal, 20)
                if model.circles.isEmpty {
                    VStack(spacing: 10) {
                        Illustration(name: "friends", size: 110)
                        Text("Start with a circle").font(.display(22)).foregroundStyle(Palette.ink)
                        Text("A circle is the people you share with: a partner, family, friends.").font(.body(15)).foregroundStyle(Palette.ink2).multilineTextAlignment(.center)
                        Button("Create or join a circle") { model.selectedTab = .circle }.buttonStyle(.chunky())
                    }.padding(20).frame(maxWidth: .infinity)
                }
                ForEach(pins) { p in
                    Button { onSelect(p.userId) } label: {
                        HStack(spacing: 12) {
                            AvatarView(name: p.name, avatarKey: p.avatarKey, userId: p.userId, photoVersion: p.photoVersion, frameKey: p.frameKey, size: 48, dim: p.presence == .hidden)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(p.name + (p.isMe ? " (you)" : "")).font(.body(16, weight: .heavy)).foregroundStyle(Palette.ink)
                                Text(status(p)).font(.body(13)).foregroundStyle(Palette.ink2)
                            }
                            Spacer()
                            Image(systemName: "chevron.right").font(.system(size: 13, weight: .bold)).foregroundStyle(Palette.ink3)
                        }
                        .padding(12).card(radius: 20)
                    }
                    .buttonStyle(.plain).padding(.horizontal, 16)
                }
            }
            .padding(.bottom, 20)
        }
    }

    private var subtitle: String {
        let others = pins.filter { !$0.isMe && $0.presence == .live }.count
        return "\(pins.count) member\(pins.count == 1 ? "" : "s") · " + (others == 0 ? "nobody else live right now" : "\(others) live")
    }

    private func status(_ p: MemberPin) -> String {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        switch p.presence {
        case .hidden: return "Hidden from you"
        case .unavailable: return "No position yet"
        case .stale: return "Last seen " + (p.recordedAtMs.map { Freshness.ageLabel(recordedAtMs: $0, nowMs: now) } ?? "a while ago")
        case .live: return "Live · " + (p.recordedAtMs.map { Freshness.ageLabel(recordedAtMs: $0, nowMs: now) } ?? "now") + (p.batteryPct.map { " · \($0)%" } ?? "")
        }
    }
}
