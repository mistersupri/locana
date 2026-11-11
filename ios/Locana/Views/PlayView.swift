import SwiftUI
import LocanaKit

struct PlayView: View {
    @EnvironmentObject private var model: AppModel
    @State private var battle = false
    @State private var achievements: [AchievementDto] = []

    var body: some View {
        ScreenScaffold(title: "Play") {
            if let me = model.me { hero(me) }
            SectionTitle(text: "More")
            row("Monster hunt", model.encounter.map { "A wild \($0.monster.name) is waiting" } ?? "Fight monsters met on the road", "pawprint.fill", Palette.coralSoft, Palette.onCoral) { BattleView() }
            row("Weapon shop", "Spend your coins on stronger weapons", "bag.fill", Palette.yellowSoft, Palette.onAmber) { ShopView() }
            row("Achievements", "Milestones for exploring and playing together", "medal.fill", Palette.yellowSoft, Palette.onAmber) { AchievementsView() }
            row("Collection", "Frames, titles, markers and landmark stamps", "diamond.fill", Palette.primarySoft, Palette.primary) { CollectionView() }
        }
        .navigationDestination(isPresented: $battle) { BattleView() }
        .onChange(of: model.showBattle) { _, v in if v { battle = true; model.showBattle = false } }
        .task { await model.refreshMe(); await model.refreshBattle() }
        .refreshable { await model.refreshMe(); await model.refreshBattle() }
    }

    private func hero(_ me: MeDto) -> some View {
        let p = me.progress
        return ZStack(alignment: .topTrailing) {
            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 10) {
                    AvatarView(name: me.profile.displayName, avatarKey: me.profile.avatarKey, userId: me.id, photoVersion: me.profile.avatarPhotoVersion, frameKey: me.profile.avatarFrameKey, size: 44)
                    VStack(alignment: .leading, spacing: 0) {
                        Text(me.profile.displayName).font(.display(18)).foregroundStyle(.white)
                        Text("@\(me.username)").font(.body(12)).foregroundStyle(.white.opacity(0.85))
                    }
                }
                Text("Level").font(.body(13, weight: .semibold)).foregroundStyle(.white.opacity(0.85))
                Text("\(p.level)").font(.system(size: 60, weight: .heavy, design: .rounded)).foregroundStyle(.white)
                Text("\(p.totalXp) XP").font(.display(20)).foregroundStyle(Palette.yellow)
                ZStack(alignment: .leading) {
                    Capsule().fill(.white.opacity(0.3))
                    GeometryReader { g in Capsule().fill(.white).frame(width: max(10, g.size.width * p.levelProgress)) }
                }.frame(height: 12)
                if let next = p.nextLevelXp { Text("\(max(0, next - p.totalXp)) to level \(p.level + 1)").font(.body(12, weight: .semibold)).foregroundStyle(.white.opacity(0.9)) }
                HStack(spacing: 8) {
                    Pill(text: "\(p.tilesDiscovered) tiles", bg: .white.opacity(0.22), fg: .white, icon: "square.grid.3x3.fill")
                    Pill(text: "\(model.coins) coins", bg: .white.opacity(0.22), fg: .white, icon: "bitcoinsign.circle.fill")
                    if p.currentStreak > 0 { Pill(text: "\(p.currentStreak) day streak", bg: .white.opacity(0.22), fg: .white, icon: "flame.fill") }
                }
            }
            .padding(20).frame(maxWidth: .infinity, alignment: .leading)
            Illustration(name: "trophy", size: 84).padding(14)
        }
        .background(RoundedRectangle(cornerRadius: 28, style: .continuous).fill(LinearGradient(colors: [Color(hex: 0x6B3BF5), Color(hex: 0x8D5BFF)], startPoint: .topLeading, endPoint: .bottomTrailing)))
    }

    private func row<D: View>(_ title: String, _ sub: String, _ icon: String, _ tile: Color, _ tint: Color, @ViewBuilder destination: () -> D) -> some View {
        NavigationLink { destination() } label: {
            HStack(spacing: 12) {
                Image(systemName: icon).font(.system(size: 18, weight: .bold)).foregroundStyle(tint).frame(width: 46, height: 46).background(RoundedRectangle(cornerRadius: 14).fill(tile))
                VStack(alignment: .leading, spacing: 2) { Text(title).font(.body(16, weight: .heavy)).foregroundStyle(Palette.ink); Text(sub).font(.body(12)).foregroundStyle(Palette.ink2).multilineTextAlignment(.leading) }
                Spacer()
                Image(systemName: "chevron.right").font(.system(size: 13, weight: .bold)).foregroundStyle(Palette.ink3)
            }
            .padding(12).card(radius: 20)
        }
        .buttonStyle(.plain)
    }
}

struct AchievementsView: View {
    @EnvironmentObject private var model: AppModel
    @State private var items: [AchievementDto] = []
    @State private var loading = true
    @State private var error: String?

    var body: some View {
        ScreenScaffold(title: "Achievements") {
            if loading { LoadingView() }
            else if let error { ErrorView(message: error) { Task { await load() } } }
            else {
                let done = items.filter { $0.unlockedAt != nil }
                Text("\(done.count) of \(items.count) unlocked").font(.display(22)).foregroundStyle(Palette.ink)
                ForEach(items.sorted { ($0.unlockedAt != nil ? 0 : 1, $0.target - $0.progress) < ($1.unlockedAt != nil ? 0 : 1, $1.target - $1.progress) }) { a in
                    let unlocked = a.unlockedAt != nil
                    VStack(alignment: .leading, spacing: 8) {
                        HStack(spacing: 12) {
                            Image(systemName: unlocked ? "medal.fill" : "lock.fill").foregroundStyle(unlocked ? Palette.onAmber : Palette.primary).frame(width: 44, height: 44).background(RoundedRectangle(cornerRadius: 14).fill(unlocked ? Palette.yellowSoft : Palette.primarySoft))
                            VStack(alignment: .leading, spacing: 2) { Text(a.name).font(.body(16, weight: .heavy)).foregroundStyle(Palette.ink); Text(a.description).font(.body(13)).foregroundStyle(Palette.ink2) }
                            Spacer(minLength: 4)
                            Pill(text: a.rarity.capitalized, bg: Palette.primarySoft, fg: Palette.primary)
                        }
                        if !unlocked { QuestBar(fraction: Double(a.progress) / Double(max(1, a.target))); Text("\(a.progress) of \(a.target)").font(.body(12, weight: .semibold)).foregroundStyle(Palette.ink2) }
                    }
                    .padding(14).card(radius: 20).opacity(unlocked ? 1 : 0.95)
                }
            }
        }
        .task { await load() }
    }

    private func load() async {
        do { items = try await model.api.achievements(); error = nil } catch { self.error = (error as? LocalizedError)?.errorDescription ?? "Couldn't load achievements" }
        loading = false
    }
}

struct CollectionView: View {
    @EnvironmentObject private var model: AppModel
    @State private var items: [CollectibleDto] = []
    @State private var loading = true
    @State private var error: String?

    private let groups: [(String, String)] = [("AVATAR_FRAME", "Avatar frames"), ("TITLE", "Titles"), ("MAP_MARKER", "Map markers"), ("QUICK_MESSAGE", "Quick messages"), ("BADGE", "Badges"), ("LANDMARK_STAMP", "Landmark stamps")]

    var body: some View {
        ScreenScaffold(title: "Collection") {
            if loading { LoadingView() }
            else if let error { ErrorView(message: error) { Task { await load() } } }
            else {
                ForEach(groups, id: \.0) { kind, title in
                    let list = items.filter { $0.kind == kind }
                    if !list.isEmpty {
                        HStack { Text(title).font(.display(20)).foregroundStyle(Palette.ink); Spacer(); Pill(text: "\(list.filter { $0.acquiredAt != nil }.count)/\(list.count)", bg: Palette.card, fg: Palette.ink2) }.padding(.top, 10)
                        LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 10) {
                            ForEach(list) { c in card(c) }
                        }
                    }
                }
            }
        }
        .task { await load() }
    }

    private func equipped(_ c: CollectibleDto) -> Bool {
        let p = model.me?.profile
        return c.code == p?.avatarFrameKey || c.code == p?.titleKey || c.code == p?.markerKey
    }

    private func card(_ c: CollectibleDto) -> some View {
        let owned = c.acquiredAt != nil
        let equippable = owned && ["AVATAR_FRAME", "TITLE", "MAP_MARKER"].contains(c.kind)
        return Button { if equippable { Task { await toggle(c) } } } label: {
            VStack(alignment: .leading, spacing: 6) {
                HStack(alignment: .top) {
                    switch c.kind {
                    case "AVATAR_FRAME": AvatarView(name: "Lo", avatarKey: "explorer_teal", frameKey: c.code, size: 52).opacity(owned ? 1 : 0.55)
                    case "QUICK_MESSAGE": Text(c.name.split(separator: " ").last.map(String.init) ?? "💬").font(.system(size: 22)).frame(width: 44, height: 44).background(RoundedRectangle(cornerRadius: 14).fill(Palette.skySoft)).opacity(owned ? 1 : 0.6)
                    default: Image(systemName: c.kind == "BADGE" ? "rosette" : c.kind == "TITLE" ? "textformat" : "mappin.circle.fill").foregroundStyle(Palette.onSky).frame(width: 44, height: 44).background(RoundedRectangle(cornerRadius: 14).fill(Palette.skySoft)).opacity(owned ? 1 : 0.7)
                    }
                    Spacer()
                    if !owned { Image(systemName: "lock.fill").font(.system(size: 13)).foregroundStyle(Palette.ink3) }
                }
                Text(c.name.replacingOccurrences(of: "Message: ", with: "")).font(.body(14, weight: .heavy)).foregroundStyle(Palette.ink).multilineTextAlignment(.leading)
                Text(c.description).font(.body(12)).foregroundStyle(Palette.ink2).multilineTextAlignment(.leading).fixedSize(horizontal: false, vertical: true)
                if equipped(c) { Pill(text: "Equipped", bg: Palette.primary, fg: Palette.onPrimary) }
                else if equippable { Pill(text: "Equip") }
                else { Pill(text: c.rarity.capitalized, bg: Palette.primarySoft, fg: Palette.primary) }
            }
            .padding(14).frame(maxWidth: .infinity, alignment: .leading).card(radius: 20)
        }
        .buttonStyle(.plain)
    }

    private func toggle(_ c: CollectibleDto) async {
        guard let p = model.me?.profile else { return }
        var frame = p.avatarFrameKey, title = p.titleKey, marker = p.markerKey
        switch c.kind {
        case "AVATAR_FRAME": frame = frame == c.code ? nil : c.code
        case "TITLE": title = title == c.code ? nil : c.code
        default: marker = marker == c.code ? nil : c.code
        }
        if let me = try? await model.api.setCosmetics(frame: frame, title: title, marker: marker) { model.me = me }
    }

    private func load() async {
        do { items = try await model.api.collectibles(); error = nil } catch { self.error = (error as? LocalizedError)?.errorDescription ?? "Couldn't load the collection" }
        loading = false
    }
}

// MARK: monsters

@MainActor
final class BattleModel: ObservableObject {
    @Published var state: BattleStateDto?
    @Published var loading = true
    @Published var error: String?
    @Published var log: String?
    @Published var busy = false
    @Published var weapon: String?
    @Published var result: AttackResultDto?
    @Published var hits = 0

    func load(_ model: AppModel) async {
        do { state = try await model.api.battleActive(); model.encounter = state?.encounter; model.coins = state?.coins ?? 0; error = nil }
        catch { self.error = (error as? LocalizedError)?.errorDescription ?? "Couldn't load the fight" }
        loading = false
    }

    func attack(_ model: AppModel) async {
        guard let s = state, let e = s.encounter, !busy else { return }
        busy = true
        let code = weapon ?? s.weapons.max { $0.power < $1.power }?.code
        do {
            let r = try await model.api.attack(e.id, weaponCode: code)
            hits += 1
            log = "You hit for \(r.hit.damage)" + (r.hit.crit ? " (critical!)" : "") + (r.counter.map { " · \(r.encounter.monster.name) hit you for \($0.damage)" } ?? "")
            UIImpactFeedbackGenerator(style: r.hit.crit ? .heavy : .medium).impactOccurred()
            model.coins = r.coins
            if r.outcome == "CONTINUE" { state = BattleStateDto(encounter: r.encounter, coins: r.coins, weapons: s.weapons); model.encounter = r.encounter }
            else { state = BattleStateDto(encounter: nil, coins: r.coins, weapons: s.weapons); model.encounter = nil; result = r; if r.outcome == "WON" { await model.refreshMe() } }
        } catch { log = (error as? LocalizedError)?.errorDescription ?? "That did not work"; await load(model) }
        busy = false
    }

    func flee(_ model: AppModel) async {
        guard let e = state?.encounter else { return }
        try? await model.api.flee(e.id)
        await load(model)
    }
}

struct BarView: View {
    var fraction: Double
    var color: Color
    var label: String
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label).font(.body(12, weight: .semibold)).foregroundStyle(Palette.ink2)
            GeometryReader { g in
                ZStack(alignment: .leading) { Capsule().fill(Palette.line); Capsule().fill(color).frame(width: max(6, g.size.width * min(1, max(0, fraction)))) }
            }.frame(height: 14).animation(.easeOut(duration: 0.4), value: fraction)
        }
    }
}

struct BattleView: View {
    @EnvironmentObject private var model: AppModel
    @StateObject private var vm = BattleModel()
    @State private var shake: CGFloat = 0

    var body: some View {
        ScreenScaffold(title: "Monster") {
            if vm.loading { LoadingView() }
            else if let e = vm.error { ErrorView(message: e) { Task { await vm.load(model) } } }
            else if let s = vm.state, let enc = s.encounter {
                VStack(spacing: 10) {
                    Text(enc.monster.emoji).font(.system(size: 96)).offset(x: shake).rotationEffect(.degrees(Double(shake) / 3)).accessibilityLabel(enc.monster.name)
                    Text(enc.monster.name).font(.display(24)).foregroundStyle(Palette.ink)
                    BarView(fraction: Double(enc.hp) / Double(enc.maxHp), color: Palette.coral, label: "Monster \(enc.hp)/\(enc.maxHp)")
                    BarView(fraction: Double(enc.playerHp) / Double(enc.maxPlayerHp), color: Palette.mint, label: "You \(enc.playerHp)/\(enc.maxPlayerHp)")
                    Text("Win: +\(enc.rewardCoins) coins, +\(enc.rewardXp) XP").font(.body(14, weight: .heavy)).foregroundStyle(Palette.onAmber)
                    if let log = vm.log { Text(log).font(.body(14)).foregroundStyle(Palette.ink).multilineTextAlignment(.center) }
                }
                .padding(20).card(radius: 28)
                Text("Weapon").font(.display(18)).foregroundStyle(Palette.ink).padding(.top, 6)
                let chosen = vm.weapon ?? s.weapons.max { $0.power < $1.power }?.code
                ForEach(s.weapons.sorted { $0.power < $1.power }) { w in
                    Button { vm.weapon = w.code } label: {
                        HStack { Text(w.emoji).font(.system(size: 26)); Text(w.name).font(.body(16, weight: .heavy)).foregroundStyle(Palette.ink); Spacer(); Text("Power \(w.power)").font(.body(13, weight: .semibold)).foregroundStyle(Palette.ink2) }
                            .padding(.horizontal, 14).padding(.vertical, 12)
                            .background(RoundedRectangle(cornerRadius: 18).fill(chosen == w.code ? Palette.primarySoft : Palette.card))
                            .overlay(RoundedRectangle(cornerRadius: 18).stroke(chosen == w.code ? Palette.primary : Palette.line, lineWidth: 2))
                    }.buttonStyle(.plain)
                }
                HStack(spacing: 10) {
                    Button(vm.busy ? "…" : "Attack") { Task { await vm.attack(model) } }.buttonStyle(.chunky()).disabled(vm.busy)
                    Button("Flee") { Task { await vm.flee(model) } }.buttonStyle(.chunky(.secondary))
                }
                NavigationLink { ShopView() } label: { Text("Weapon shop · \(s.coins) coins").font(.body(15, weight: .heavy)) }.buttonStyle(.chunky(.yellow))
            } else {
                EmptyStateView(title: "No monster right now", message: "Monsters step onto the road while you travel, at most one every few minutes. Keep exploring, then come back.")
                NavigationLink { ShopView() } label: { Text("Weapon shop") }.buttonStyle(.chunky(.yellow))
            }
        }
        .task { await vm.load(model) }
        .onChange(of: vm.hits) { _, _ in
            withAnimation(.interpolatingSpring(stiffness: 600, damping: 6)) { shake = 24 }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.08) { withAnimation(.interpolatingSpring(stiffness: 300, damping: 8)) { shake = 0 } }
        }
        .alert(vm.result?.outcome == "WON" ? "Victory! 🎉" : "You were defeated", isPresented: Binding(get: { vm.result != nil }, set: { if !$0 { vm.result = nil } })) {
            Button("OK", role: .cancel) { vm.result = nil }
        } message: {
            if let r = vm.result {
                Text(r.outcome == "WON" ? "+\(r.rewards?.coins ?? 0) coins and +\(r.rewards?.xp ?? 0) XP." + (r.rewards?.levelUps.last.map { " Level \($0.to)!" } ?? "")
                     : "\(r.encounter.monster.name) got away. Nothing is lost: there will be another monster on the road. A stronger weapon from the shop helps.")
            }
        }
    }
}

struct ShopView: View {
    @EnvironmentObject private var model: AppModel
    @State private var shop: ShopDto?
    @State private var error: String?
    @State private var message: String?
    @State private var buying: String?

    var body: some View {
        ScreenScaffold(title: "Weapon shop") {
            if let shop {
                VStack(alignment: .leading, spacing: 4) {
                    Text("💰 \(shop.coins) coins").font(.display(24)).foregroundStyle(Palette.ink)
                    Text("Coins come from winning fights and unlocking landmarks. Level \(shop.level): stronger weapons open as you level up.").font(.body(13)).foregroundStyle(Palette.ink2)
                }.padding(16).frame(maxWidth: .infinity, alignment: .leading).card()
                if let message { Text(message).font(.body(14, weight: .heavy)).foregroundStyle(Palette.onMint) }
                ForEach(shop.weapons) { w in
                    HStack(spacing: 12) {
                        Text(w.emoji).font(.system(size: 30))
                        VStack(alignment: .leading, spacing: 2) { Text(w.name).font(.body(16, weight: .heavy)).foregroundStyle(Palette.ink); Text("Power \(w.power)" + (w.minLevel > 1 ? " · level \(w.minLevel)" : "")).font(.body(12)).foregroundStyle(Palette.ink2) }
                        Spacer()
                        if w.costCoins == 0 { Text("Always yours").font(.body(12, weight: .semibold)).foregroundStyle(Palette.ink2) }
                        else if w.owned { Text("Owned").font(.body(14, weight: .heavy)).foregroundStyle(Palette.onMint) }
                        else if !w.unlocked { Text("Level \(w.minLevel)").font(.body(14, weight: .heavy)).foregroundStyle(Palette.ink3) }
                        else { Button(buying == w.code ? "…" : "💰 \(w.costCoins)") { Task { await buy(w) } }.buttonStyle(.chunky(compact: true, enabled: w.affordable)).disabled(!w.affordable || buying != nil) }
                    }
                    .padding(14).card(radius: 20)
                }
            } else if let error { ErrorView(message: error) { Task { await load() } } }
            else { LoadingView() }
        }
        .task { await load() }
    }

    private func load() async {
        do { shop = try await model.api.shop(); model.coins = shop?.coins ?? 0; error = nil } catch { self.error = (error as? LocalizedError)?.errorDescription ?? "Couldn't open the shop" }
    }

    private func buy(_ w: ShopWeaponDto) async {
        buying = w.code
        do { shop = try await model.api.buy(w.code); model.coins = shop?.coins ?? 0; message = "You bought the \(w.name)!" }
        catch { message = (error as? LocalizedError)?.errorDescription ?? "Couldn't buy it" }
        buying = nil
    }
}
