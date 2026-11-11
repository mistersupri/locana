import SwiftUI
import LocanaKit

struct CircleView: View {
    @EnvironmentObject private var model: AppModel
    @State private var newName = ""
    @State private var joinCode = ""
    @State private var message: String?
    @State private var invite: InvitationDto?
    @State private var busy = false
    @State private var confirmLeave = false

    private var current: CircleSummaryDto? { model.circles.first { $0.id == model.selectedCircleId } }

    var body: some View {
        ScreenScaffold(title: "Circle") {
            if let message { Pill(text: message, bg: Palette.mintSoft, fg: Palette.onMint) }
            if model.circles.count > 1 {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack { ForEach(model.circles) { c in
                        Button(c.name) { Task { await model.select(c.id) } }
                            .font(.body(14, weight: .heavy)).foregroundStyle(c.id == model.selectedCircleId ? Palette.onPrimary : Palette.ink)
                            .padding(.horizontal, 16).padding(.vertical, 9).background(Capsule().fill(c.id == model.selectedCircleId ? Palette.primary : Palette.card))
                    } }
                }
            }
            if let c = current, let d = model.detail {
                VStack(alignment: .leading, spacing: 6) {
                    Text(c.name).font(.display(30)).foregroundStyle(Palette.ink)
                    Text("\(d.members.count) member\(d.members.count == 1 ? "" : "s") · you are \(c.myRole.lowercased())").font(.body(14)).foregroundStyle(Palette.ink2)
                    Text("Belonging to a circle means sharing your position with it. You can pause or stop sharing in Profile.").font(.body(12)).foregroundStyle(Palette.ink3)
                }
                .padding(18).frame(maxWidth: .infinity, alignment: .leading)
                .background(RoundedRectangle(cornerRadius: 28, style: .continuous).fill(LinearGradient(colors: [Palette.primarySoft, Palette.card], startPoint: .top, endPoint: .bottom)))
                SectionTitle(text: "Who's here")
                ForEach(d.members) { m in
                    HStack(spacing: 12) {
                        AvatarView(name: m.displayName, avatarKey: m.avatarKey, userId: m.userId, photoVersion: m.avatarPhotoVersion, frameKey: m.avatarFrameKey, size: 48)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(m.displayName + (m.userId == model.me?.id ? " (you)" : "")).font(.body(16, weight: .heavy)).foregroundStyle(Palette.ink)
                            Text("@\(m.username) · level \(m.level)").font(.body(12)).foregroundStyle(Palette.ink2)
                        }
                        Spacer()
                        if m.role != "MEMBER" { Pill(text: m.role.capitalized) }
                    }
                    .padding(12).card(radius: 20)
                }
                SectionTitle(text: "Invite someone")
                VStack(alignment: .leading, spacing: 10) {
                    if let code = invite?.code {
                        Text("NEW CODE").font(.body(11, weight: .heavy)).foregroundStyle(Palette.ink2)
                        Text(code).font(.system(size: 28, weight: .heavy, design: .monospaced)).foregroundStyle(Palette.ink).textSelection(.enabled)
                        ShareLink(item: invite?.deepLink ?? code, message: Text("Join my circle on Locana with this code: \(code)")) { Label("Share", systemImage: "square.and.arrow.up").font(.body(15, weight: .heavy)) }
                    } else { Text("Make a code and send it to the person. It works for a few days and a few people.").font(.body(14)).foregroundStyle(Palette.ink2) }
                    Button(busy ? "…" : "Create code") { Task { await makeCode(c.id) } }.buttonStyle(.chunky(.secondary, compact: true))
                }
                .padding(16).frame(maxWidth: .infinity, alignment: .leading).card()
                Button("Leave this circle") { confirmLeave = true }.font(.body(14, weight: .heavy)).foregroundStyle(Palette.onCoral).padding(.top, 10)
            } else if model.circles.isEmpty {
                EmptyStateView(title: "No circle yet", message: "Create one for the people you share with, or join one with a code.", illustration: "friends")
            }
            SectionTitle(text: "Create a circle")
            VStack(alignment: .leading, spacing: 10) {
                LField(label: "Circle name", text: $newName, capitalize: .words)
                Button("Create circle") { Task { await create() } }.buttonStyle(.chunky(enabled: newName.trimmingCharacters(in: .whitespaces).count >= 2 && !busy)).disabled(newName.trimmingCharacters(in: .whitespaces).count < 2 || busy)
            }.padding(16).card()
            SectionTitle(text: "Join with a code")
            VStack(alignment: .leading, spacing: 10) {
                LField(label: "Invitation code", text: $joinCode, capitalize: .characters)
                Button("Join") { Task { await join() } }.buttonStyle(.chunky(.secondary, enabled: joinCode.count >= 6 && !busy)).disabled(joinCode.count < 6 || busy)
            }.padding(16).card()
        }
        .confirmationDialog("Leave this circle?", isPresented: $confirmLeave, titleVisibility: .visible) {
            Button("Leave", role: .destructive) { Task { await leave() } }
            Button("Cancel", role: .cancel) {}
        } message: { Text("You stop sharing with it and it disappears from your list.") }
        .task { await model.refreshCircles() }
        .refreshable { await model.refreshCircles() }
    }

    private func create() async {
        busy = true
        do { let d = try await model.api.createCircle(newName.trimmingCharacters(in: .whitespaces)); newName = ""; message = "Created \(d.name)"; await model.refreshCircles(); await model.select(d.id); await model.refreshSharing() }
        catch { message = (error as? LocalizedError)?.errorDescription ?? "Couldn't create the circle" }
        busy = false
    }

    private func join() async {
        busy = true
        do { let d = try await model.api.joinCircle(code: joinCode.trimmingCharacters(in: .whitespaces).uppercased()); joinCode = ""; message = "Joined \(d.name)"; await model.refreshCircles(); await model.select(d.id); await model.refreshSharing() }
        catch { message = (error as? LocalizedError)?.errorDescription ?? "That code did not work" }
        busy = false
    }

    private func makeCode(_ id: String) async {
        busy = true
        do { invite = try await model.api.createInvitation(id) } catch { message = (error as? LocalizedError)?.errorDescription ?? "Couldn't make a code" }
        busy = false
    }

    private func leave() async {
        guard let id = model.selectedCircleId else { return }
        do { try await model.api.leaveCircle(id); invite = nil; await model.refreshCircles() } catch { message = (error as? LocalizedError)?.errorDescription ?? "Couldn't leave" }
    }
}

struct NotificationsView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var items: [NotificationDto] = []
    @State private var loading = true
    @State private var error: String?

    var body: some View {
        ScreenScaffold(title: "Notifications") {
            if loading { LoadingView() }
            else if let error { ErrorView(message: error) { Task { await load() } } }
            else if items.isEmpty { EmptyStateView(title: "You're all caught up", message: "Arrivals, quest rewards and circle news will show up here.", illustration: "mailbox") }
            else {
                ForEach(items) { n in
                    Button { Task { await open(n) } } label: {
                        HStack(spacing: 12) {
                            Image(systemName: icon(n.kind)).foregroundStyle(n.kind == "SOS" ? Palette.onCoral : Palette.onAmber).frame(width: 44, height: 44).background(RoundedRectangle(cornerRadius: 14).fill(n.kind == "SOS" ? Palette.coralSoft : Palette.yellowSoft))
                            VStack(alignment: .leading, spacing: 2) {
                                Text(n.title).font(.body(15, weight: .heavy)).foregroundStyle(Palette.ink).multilineTextAlignment(.leading)
                                Text([n.body, Iso.ms(n.createdAt).map { Freshness.ageLabel(recordedAtMs: $0, nowMs: Int64(Date().timeIntervalSince1970 * 1000)) }].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " · ")).font(.body(12)).foregroundStyle(Palette.ink2).multilineTextAlignment(.leading)
                            }
                            Spacer()
                            if !n.read { Circle().fill(Palette.coralStrong).frame(width: 10, height: 10) }
                        }
                        .padding(12).card(radius: 20)
                    }.buttonStyle(.plain)
                }
            }
        }
        .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("Mark all read") { Task { try? await model.api.markRead(); model.unread = 0; await load() } } } }
        .task { await load() }
    }

    private func icon(_ kind: String) -> String {
        switch kind {
        case "SOS": return "cross.case.fill"
        case "QUICK_MESSAGE": return "bubble.left.fill"
        case "TRIP": return "car.fill"
        case "ARRIVAL": return "mappin.and.ellipse"
        case "QUEST", "ACHIEVEMENT", "LEVEL_UP": return "trophy.fill"
        case "MONSTER": return "pawprint.fill"
        default: return "bell.fill"
        }
    }

    private func load() async {
        do { let page = try await model.api.notifications(); items = page.items; model.unread = page.unread; error = nil } catch { self.error = (error as? LocalizedError)?.errorDescription ?? "Couldn't load notifications" }
        loading = false
    }

    private func open(_ n: NotificationDto) async {
        if !n.read { try? await model.api.markRead(ids: [n.id]); model.unread = max(0, model.unread - 1) }
        model.open(kind: n.kind, data: n.data)
        dismiss()
    }
}

struct ProfileView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var confirmOut = false

    var body: some View {
        ScreenScaffold(title: "Profile") {
            if let me = model.me {
                VStack(alignment: .leading, spacing: 12) {
                    HStack(spacing: 14) {
                        AvatarView(name: me.profile.displayName, avatarKey: me.profile.avatarKey, userId: me.id, photoVersion: me.profile.avatarPhotoVersion, frameKey: me.profile.avatarFrameKey, size: 72)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(me.profile.displayName).font(.display(24)).foregroundStyle(.white).lineLimit(2)
                            Text("@\(me.username)").font(.body(14)).foregroundStyle(.white.opacity(0.85))
                        }
                    }
                    HStack(spacing: 8) {
                        Pill(text: "Level \(me.progress.level)", bg: Palette.yellow, fg: Color(hex: 0x3A2800), icon: "star")
                        Pill(text: "\(me.progress.tilesDiscovered) tiles", bg: .white.opacity(0.22), fg: .white, icon: "square.grid.3x3.fill")
                    }
                    NavigationLink { EditProfileView() } label: { Text("Edit profile") }.buttonStyle(.chunky(.secondary, compact: true))
                }
                .padding(20).frame(maxWidth: .infinity, alignment: .leading)
                .background(RoundedRectangle(cornerRadius: 28, style: .continuous).fill(LinearGradient(colors: [Color(hex: 0x6B3BF5), Color(hex: 0x8D5BFF)], startPoint: .topLeading, endPoint: .bottomTrailing)))
                link("Safety", "SOS, emergency contacts, crash detection", "cross.case.fill", Palette.coralSoft, Palette.onCoral) { SafetyScreen() }
                link("Privacy and notifications", "History, exploration, rankings, alerts", "eye.slash.fill", Palette.mintSoft, Palette.onMint) { PrivacyView() }
                Button { Task { await sharingToggle(me) } } label: {
                    HStack(spacing: 12) {
                        Image(systemName: (model.sharing?.globallyPaused ?? false) ? "play.fill" : "pause.fill").foregroundStyle(Palette.onSky).frame(width: 46, height: 46).background(RoundedRectangle(cornerRadius: 14).fill(Palette.skySoft))
                        VStack(alignment: .leading, spacing: 2) {
                            Text((model.sharing?.globallyPaused ?? false) ? "Resume sharing" : "Pause sharing").font(.body(16, weight: .heavy)).foregroundStyle(Palette.ink)
                            Text((model.sharing?.globallyPaused ?? false) ? "Your circles see your position again." : "Your circles see you as not live until you resume.").font(.body(12)).foregroundStyle(Palette.ink2).multilineTextAlignment(.leading)
                        }
                        Spacer()
                    }.padding(12).card(radius: 20)
                }.buttonStyle(.plain)
                Button("Sign out") { confirmOut = true }.font(.body(15, weight: .heavy)).foregroundStyle(Palette.onCoral).padding(.top, 12)
                Text("Version \(AppConfig.version)").font(.body(12)).foregroundStyle(Palette.ink3)
            }
        }
        .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("Done") { dismiss() } } }
        .confirmationDialog("Sign out?", isPresented: $confirmOut, titleVisibility: .visible) {
            Button("Sign out", role: .destructive) { Task { dismiss(); await model.signOut() } }
            Button("Cancel", role: .cancel) {}
        } message: { Text("Location sharing from this phone stops. Your circles will see your last position as not live.") }
    }

    private func sharingToggle(_ me: MeDto) async {
        let paused = model.sharing?.globallyPaused ?? false
        if let s = try? await model.api.pauseAll(!paused) { model.sharing = s; await model.refreshSharing() }
    }

    private func link<D: View>(_ title: String, _ sub: String, _ icon: String, _ tile: Color, _ tint: Color, @ViewBuilder destination: () -> D) -> some View {
        NavigationLink { destination() } label: {
            HStack(spacing: 12) {
                Image(systemName: icon).foregroundStyle(tint).frame(width: 46, height: 46).background(RoundedRectangle(cornerRadius: 14).fill(tile))
                VStack(alignment: .leading, spacing: 2) { Text(title).font(.body(16, weight: .heavy)).foregroundStyle(Palette.ink); Text(sub).font(.body(12)).foregroundStyle(Palette.ink2).multilineTextAlignment(.leading) }
                Spacer(); Image(systemName: "chevron.right").font(.system(size: 13, weight: .bold)).foregroundStyle(Palette.ink3)
            }.padding(12).card(radius: 20)
        }.buttonStyle(.plain)
    }
}

struct EditProfileView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var bio = ""
    @State private var color = "explorer_teal"
    @State private var busy = false
    @State private var error: String?
    private let colors = ["explorer_teal", "explorer_amber", "explorer_slate", "explorer_coral", "explorer_moss", "explorer_plum"]

    var body: some View {
        ScreenScaffold(title: "Edit profile") {
            LField(label: "Display name", text: $name, capitalize: .words)
            LField(label: "About you (optional)", text: $bio)
            Text("Pin colour").font(.body(13, weight: .bold)).foregroundStyle(Palette.ink2)
            HStack(spacing: 12) {
                ForEach(colors, id: \.self) { k in
                    Button { color = k } label: {
                        AvatarView(name: name.isEmpty ? "You" : name, avatarKey: k, size: 46).overlay(Circle().stroke(Palette.primary, lineWidth: color == k ? 3 : 0).padding(-3))
                    }.buttonStyle(.plain)
                }
            }
            if let error { Text(error).font(.body(13, weight: .semibold)).foregroundStyle(Palette.onCoral) }
            Button(busy ? "Saving…" : "Save") { Task { await save() } }.buttonStyle(.chunky(enabled: !busy && !name.trimmingCharacters(in: .whitespaces).isEmpty)).disabled(busy || name.trimmingCharacters(in: .whitespaces).isEmpty)
        }
        .onAppear { name = model.me?.profile.displayName ?? ""; bio = model.me?.profile.bio ?? ""; color = model.me?.profile.avatarKey ?? "explorer_teal" }
    }

    private func save() async {
        busy = true; error = nil
        do { model.me = try await model.api.updateMe(UpdateProfileRequest(displayName: name.trimmingCharacters(in: .whitespaces), avatarKey: color, bio: bio)); await model.loadSelected(); dismiss() }
        catch { self.error = (error as? LocalizedError)?.errorDescription ?? "Couldn't save" }
        busy = false
    }
}

struct PrivacyView: View {
    @EnvironmentObject private var model: AppModel

    private func toggle(_ title: String, _ sub: String?, _ value: Bool, _ patch: @escaping (Bool) -> PrivacyPatch) -> some View {
        Toggle(isOn: Binding(get: { value }, set: { on in Task { _ = try? await model.api.updatePrivacy(patch(on)); await model.refreshMe(); await model.refreshSharing() } })) {
            VStack(alignment: .leading, spacing: 2) { Text(title).font(.body(16, weight: .heavy)).foregroundStyle(Palette.ink); if let sub { Text(sub).font(.body(12)).foregroundStyle(Palette.ink2) } }
        }.padding(14).card(radius: 18)
    }

    var body: some View {
        ScreenScaffold(title: "Privacy") {
            if let p = model.me?.privacy {
                toggle("Keep my location history", "Places and trips in your timeline. Turning it off deletes it.", p.historyEnabled) { PrivacyPatch(historyEnabled: $0) }
                toggle("Exploration", "Walks reveal tiles and count for quests. Tiles stay private.", p.explorationEnabled) { PrivacyPatch(explorationEnabled: $0) }
                toggle("Show me in leaderboards", nil, p.showInLeaderboards) { PrivacyPatch(showInLeaderboards: $0) }
                SectionTitle(text: "Notifications")
                toggle("Arrivals and departures", nil, p.notifyArrivals) { PrivacyPatch(notifyArrivals: $0) }
                toggle("Quests", nil, p.notifyQuests) { PrivacyPatch(notifyQuests: $0) }
                toggle("Levels and achievements", nil, p.notifyAchievements) { PrivacyPatch(notifyAchievements: $0) }
                toggle("Circle activity and invitations", nil, p.notifyCircle) { PrivacyPatch(notifyCircle: $0) }
                toggle("Long trips", "When someone in your circles is on a long drive or ride", p.notifyTrips ?? true) { PrivacyPatch(notifyTrips: $0) }
                Text("Security alerts and SOS always arrive: they are about your safety.").font(.body(12)).foregroundStyle(Palette.ink2).padding(.top, 8)
            }
        }
    }
}
