import SwiftUI
import CoreLocation
import LocanaKit

struct QuestBar: View {
    var fraction: Double
    var body: some View {
        GeometryReader { g in
            ZStack(alignment: .leading) {
                Capsule().fill(Palette.line)
                Capsule().fill(Palette.primaryStrong).frame(width: max(8, g.size.width * min(1, max(0, fraction))))
            }
        }
        .frame(height: 10)
        .animation(.easeOut(duration: 0.4), value: fraction)
    }
}

struct QuestCard: View {
    var quest: QuestDto
    var busy: Bool
    var onClaim: () -> Void

    private var completed: Bool { quest.status == "COMPLETED" }
    private var claimed: Bool { quest.status == "CLAIMED" }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: icon).font(.system(size: 18, weight: .bold)).foregroundStyle(completed ? Palette.onPrimary : Palette.primary)
                    .frame(width: 42, height: 42).background(RoundedRectangle(cornerRadius: 14).fill(completed ? Palette.primary : Palette.primarySoft)).opacity(claimed ? 0.6 : 1)
                VStack(alignment: .leading, spacing: 2) {
                    Text(quest.name).font(.body(16, weight: .heavy)).foregroundStyle(Palette.ink)
                    Text(quest.description).font(.body(13)).foregroundStyle(Palette.ink2)
                }
                Spacer(minLength: 6)
                Pill(text: claimed ? "Claimed" : "+\(quest.rewardXp) XP", bg: claimed ? Palette.primarySoft : Palette.yellowSoft, fg: claimed ? Palette.primary : Palette.onAmber, icon: claimed ? nil : "star.fill")
            }
            QuestBar(fraction: Double(quest.progress) / Double(max(1, quest.target)))
            HStack {
                Text(Formatting.progress(value: quest.progress, target: quest.target, unit: quest.unit)).font(.body(12, weight: .semibold)).foregroundStyle(Palette.ink2)
                Spacer()
                if completed { Button(busy ? "…" : "Claim") { onClaim() }.buttonStyle(.chunky(.primary, compact: true)).disabled(busy) }
                else if claimed { Text("Done").font(.body(12, weight: .semibold)).foregroundStyle(Palette.ink2) }
                else { Text(endsIn).font(.body(12, weight: .semibold)).foregroundStyle(Palette.ink2) }
            }
        }
        .padding(14).card(radius: 22)
        .accessibilityElement(children: .combine)
    }

    private var icon: String {
        switch quest.condition {
        case "DISCOVER_TILES", "CIRCLE_TILES": return "square.grid.3x3.fill"
        case "VISIT_LANDMARKS", "COLLECT_LANDMARK_SET": return "building.columns.fill"
        case "WALK_DISTANCE": return "figure.walk"
        case "ACTIVE_DAYS": return "calendar"
        default: return "flag.fill"
        }
    }

    private var endsIn: String {
        guard let end = Iso.ms(quest.expiresAt) else { return "" }
        let left = end - Int64(Date().timeIntervalSince1970 * 1000)
        if left <= 0 { return "Ended" }
        return "Ends in " + Formatting.duration(ms: left)
    }
}

@MainActor
final class ExploreModel: ObservableObject {
    @Published var quests: [QuestDto] = []
    @Published var stats: StatsDto?
    @Published var events: [GameEventDto] = []
    @Published var error: String?
    @Published var loading = true
    @Published var claiming: String?
    @Published var message: String?

    func load(_ api: APIClient) async {
        do {
            async let q = api.quests()
            async let s = try? api.stats()
            async let e = try? api.events()
            let (qs, st, ev) = try await (q, s, e)
            quests = qs; stats = st; events = ev ?? []; error = nil
        } catch { self.error = (error as? LocalizedError)?.errorDescription ?? "Couldn't load quests" }
        loading = false
    }

    func claim(_ q: QuestDto, model: AppModel) async {
        claiming = q.id
        do {
            let r = try await model.api.claimQuest(q.id, key: UUID().uuidString.replacingOccurrences(of: "-", with: ""))
            message = r.alreadyClaimed ? "Already claimed" : "+\(r.xpAwarded) XP" + (r.levelUps.last.map { " · Level \($0.to)!" } ?? "")
            UINotificationFeedbackGenerator().notificationOccurred(.success)
            await load(model.api); await model.refreshMe()
        } catch { message = (error as? LocalizedError)?.errorDescription ?? "Couldn't claim" }
        claiming = nil
    }
}

struct ExploreView: View {
    @EnvironmentObject private var model: AppModel
    @StateObject private var vm = ExploreModel()
    @State private var exploring = true

    var body: some View {
        ScreenScaffold(title: "Explore") {
            if vm.loading { LoadingView() }
            else if let e = vm.error { ErrorView(message: e) { Task { await vm.load(model.api) } } }
            else {
                hero
                HStack(spacing: 10) {
                    NavigationLink { LandmarksView() } label: { shortcut("building.columns.fill", "Landmarks") }
                    NavigationLink { StatsView(stats: vm.stats) } label: { shortcut("chart.bar.fill", "Stats") }
                }
                if let m = vm.message { Pill(text: m, bg: Palette.mintSoft, fg: Palette.onMint) }
                if let ev = vm.events.first(where: { $0.live }) {
                    HStack { Illustration(name: "monas", size: 70); VStack(alignment: .leading) { Text(ev.name).font(.display(18)); Text(ev.description).font(.body(13)) }.foregroundStyle(Color(hex: 0x1F1645)); Spacer(minLength: 0) }
                        .padding(16).background(RoundedRectangle(cornerRadius: 24).fill(LinearGradient(colors: [Color(hex: 0xFFD45C), Color(hex: 0xFFB23C)], startPoint: .topLeading, endPoint: .bottomTrailing)))
                }
                section("Today", vm.quests.filter { $0.type == "DAILY" })
                section("This week", vm.quests.filter { $0.type == "WEEKLY" })
                section("Events", vm.quests.filter { $0.type == "SPECIAL" })
            }
        }
        .task { await vm.load(model.api); exploring = model.me?.privacy.explorationEnabled ?? true }
        .refreshable { await vm.load(model.api) }
    }

    private func shortcut(_ icon: String, _ title: String) -> some View {
        HStack(spacing: 8) { Image(systemName: icon).foregroundStyle(Palette.primary); Text(title).font(.body(15, weight: .heavy)).foregroundStyle(Palette.ink) }
            .frame(maxWidth: .infinity).padding(.vertical, 14).card(radius: 18)
    }

    private var hero: some View {
        let daily = vm.quests.filter { $0.type == "DAILY" }
        let done = daily.filter { $0.status != "ACTIVE" }.count
        return VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Today").font(.body(14, weight: .semibold)).foregroundStyle(Palette.ink2)
                    HStack(alignment: .firstTextBaseline, spacing: 4) {
                        Text("\(done)").font(.system(size: 56, weight: .heavy, design: .rounded)).foregroundStyle(Palette.ink)
                        Text("/ \(daily.count) quests").font(.body(16, weight: .heavy)).foregroundStyle(Palette.ink2)
                    }
                }
                Spacer()
                Illustration(name: "compass", size: 96)
            }
            Text(vm.stats.map { $0.tilesDiscovered == 0 ? "Your map is still dark. Walk somewhere new to light the first tile." : "\($0.tilesDiscovered) tiles revealed so far" } ?? "")
                .font(.body(14)).foregroundStyle(Palette.ink2)
            Toggle(isOn: $exploring) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(exploring ? "Exploration on" : "Exploration off").font(.body(15, weight: .heavy)).foregroundStyle(Palette.ink)
                    Text(exploring ? "Walks reveal tiles and count for quests. Circles never see where you went." : "Turn on so walks count. Separate from sharing your location.").font(.body(12)).foregroundStyle(Palette.ink2)
                }
            }
            .padding(12).background(RoundedRectangle(cornerRadius: 18).fill(Palette.card))
            .onChange(of: exploring) { _, on in Task { _ = try? await model.api.updatePrivacy(PrivacyPatch(explorationEnabled: on)); await model.refreshMe(); await model.refreshSharing() } }
        }
        .padding(18).background(RoundedRectangle(cornerRadius: 28, style: .continuous).fill(LinearGradient(colors: [Palette.card, Palette.primarySoft], startPoint: .top, endPoint: .bottom)))
        .shadow(color: .black.opacity(0.06), radius: 8, y: 3)
    }

    @ViewBuilder
    private func section(_ title: String, _ list: [QuestDto]) -> some View {
        if !list.isEmpty {
            SectionTitle(text: title)
            ForEach(Array(list.enumerated()), id: \.element.id) { i, q in
                QuestCard(quest: q, busy: vm.claiming == q.id) { Task { await vm.claim(q, model: model) } }.enterRise(delay: Double(min(i, 5)) * 0.04)
            }
        }
    }
}

struct StatsView: View {
    var stats: StatsDto?
    var body: some View {
        ScreenScaffold(title: "Stats") {
            if let s = stats {
                tile("Tiles revealed", "\(s.tilesDiscovered)", "square.grid.3x3.fill")
                tile("Landmarks unlocked", "\(s.landmarksVisited)", "building.columns.fill")
                tile("Distance walked", Formatting.distance(s.totalDistanceM), "figure.walk")
                tile("Current streak", "\(s.currentStreak) days", "flame.fill")
                tile("Longest streak", "\(s.longestStreak) days", "trophy.fill")
                tile("Quests completed", "\(s.questsCompleted)", "checkmark.seal.fill")
            } else { EmptyStateView(title: "No stats yet", message: "Play for a day and they appear here.") }
        }
    }
    private func tile(_ title: String, _ value: String, _ icon: String) -> some View {
        HStack { Image(systemName: icon).foregroundStyle(Palette.primary).frame(width: 36); Text(title).font(.body(16, weight: .semibold)).foregroundStyle(Palette.ink); Spacer(); Text(value).font(.display(20)).foregroundStyle(Palette.ink) }
            .padding(16).card(radius: 18)
    }
}

@MainActor
final class LandmarksModel: ObservableObject {
    @Published var items: [LandmarkDto] = []
    @Published var loading = true
    @Published var error: String?
    @Published var message: String?
    @Published var claiming: String?
    var query = ""

    func load(model: AppModel) async {
        let c = model.location.latest?.coordinate ?? CLLocationCoordinate2D(latitude: -6.1754, longitude: 106.8272)
        do { items = try await model.api.landmarks(lat: c.latitude, lon: c.longitude, query: query.isEmpty ? nil : query); error = nil }
        catch { self.error = (error as? LocalizedError)?.errorDescription ?? "Couldn't load landmarks" }
        loading = false
    }

    func claim(_ l: LandmarkDto, model: AppModel) async {
        guard let f = model.location.latest else { message = "No position yet"; return }
        claiming = l.id
        do {
            let r = try await model.api.claimLandmark(l.id, lat: f.coordinate.latitude, lon: f.coordinate.longitude, accuracyM: f.horizontalAccuracy)
            message = r.firstVisit ? "\(r.name) unlocked! +\(r.rewardXp) XP" : "\(r.name) was unlocked before"
            await load(model: model); await model.refreshMe()
        } catch { message = (error as? LocalizedError)?.errorDescription ?? "Couldn't claim" }
        claiming = nil
    }
}

struct LandmarksView: View {
    @EnvironmentObject private var model: AppModel
    @StateObject private var vm = LandmarksModel()
    @State private var query = ""

    var body: some View {
        ScreenScaffold(title: "Landmarks") {
            HStack { Image(systemName: "magnifyingglass").foregroundStyle(Palette.ink3); TextField("Search public places", text: $query).font(.body(16)).onSubmit { vm.query = query; Task { await vm.load(model: model) } } }
                .padding(.horizontal, 14).frame(minHeight: 50).background(RoundedRectangle(cornerRadius: 16).fill(Palette.card)).overlay(RoundedRectangle(cornerRadius: 16).stroke(Palette.line, lineWidth: 2))
            if model.location.latest == nil { Text("We don't know where you are right now, so distances are hidden.").font(.body(13)).foregroundStyle(Palette.ink2) }
            if let m = vm.message { Pill(text: m, bg: Palette.mintSoft, fg: Palette.onMint) }
            if vm.loading { LoadingView() }
            else if let e = vm.error { ErrorView(message: e) { Task { await vm.load(model: model) } } }
            else if vm.items.isEmpty { EmptyStateView(title: "No landmarks found", message: query.isEmpty ? "No public landmarks are mapped near you yet." : "Try another name.", illustration: "city") }
            else {
                ForEach(Array(vm.items.enumerated()), id: \.element.id) { i, l in row(l).enterRise(delay: Double(min(i, 6)) * 0.03) }
                Text("Only public places are listed. Respect opening hours and local rules; never enter restricted areas for the game.").font(.body(12)).foregroundStyle(Palette.ink2).padding(.top, 8)
            }
        }
        .task { await vm.load(model: model) }
    }

    private func row(_ l: LandmarkDto) -> some View {
        let located = model.location.latest != nil
        let inRange = located && !l.visited && l.distanceM <= l.radiusM
        let sub: String = l.visited ? "Unlocked" : inRange ? "You're here: claim it" : located ? "Locked · \(Formatting.distance(l.distanceM)) away, get within \(Formatting.distance(l.radiusM))" : "Locked"
        return HStack(spacing: 12) {
            Image(systemName: l.visited ? "checkmark.seal.fill" : "lock.fill").foregroundStyle(l.visited ? Palette.onMint : Palette.ink3)
                .frame(width: 46, height: 46).background(RoundedRectangle(cornerRadius: 14).fill(l.visited ? Palette.mintSoft : Palette.skySoft))
            VStack(alignment: .leading, spacing: 2) {
                Text(l.name).font(.body(16, weight: .heavy)).foregroundStyle(Palette.ink)
                Text("\(l.category.capitalized) · \(sub)").font(.body(12)).foregroundStyle(Palette.ink2)
                Text(l.description).font(.body(12)).foregroundStyle(Palette.ink3).lineLimit(2)
            }
            Spacer(minLength: 4)
            if l.visited { Pill(text: "Unlocked", bg: Palette.primarySoft, fg: Palette.primary) }
            else if inRange { Button(vm.claiming == l.id ? "…" : "Claim") { Task { await vm.claim(l, model: model) } }.buttonStyle(.chunky(compact: true)) }
            else { Pill(text: "+\(l.rewardXp) XP", bg: Palette.yellowSoft, fg: Palette.onAmber, icon: "star.fill") }
        }
        .padding(12).card(radius: 20).opacity(l.visited || inRange ? 1 : 0.85)
        .accessibilityElement(children: .combine)
    }
}
