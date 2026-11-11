import SwiftUI
import LocanaKit

struct RootView: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        ZStack {
            Palette.bg.ignoresSafeArea()
            switch model.session {
            case .loading: ProgressView()
            case .signedOut: AuthFlow()
            case .signedIn: MainTabs()
            }
            if model.session == .signedIn {
                QuickMessageOverlay()
                if let toast = model.gameToast {
                    VStack { Spacer(); Pill(text: toast, bg: Palette.yellow, fg: Color(hex: 0x3A2800), icon: "star.fill").padding(.bottom, 110) }
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                }
            }
        }
        .animation(.easeOut(duration: 0.25), value: model.session)
        .animation(.spring(response: 0.4, dampingFraction: 0.8), value: model.gameToast)
        .fullScreenCover(isPresented: Binding(get: { model.crashSecondsLeft != nil }, set: { _ in })) { CrashAlertView() }
        .alert(sosTitle, isPresented: Binding(get: { model.sos != .idle }, set: { if !$0 { model.dismissSOS() } })) {
            if case .failed = model.sos { Button("Try again") { Task { await model.triggerSOS(reason: "MANUAL") } } }
            Button(model.sos == .sending ? "Wait" : "OK", role: .cancel) { model.dismissSOS() }
        } message: { Text(sosMessage) }
    }

    private var sosTitle: String {
        switch model.sos { case .sending: return "Sending your SOS…"; case .sent: return "SOS sent"; case .failed: return "SOS not sent"; case .idle: return "" }
    }

    private var sosMessage: String {
        switch model.sos {
        case .idle: return ""
        case .sending: return "Finding where you are and telling your circles."
        case .sent(let n, let e, let calling):
            var s = "\(n) people in your circles were told"
            if e > 0 { s += " and \(e) e-mails went out" }
            s += "."
            s += calling.map { " Calling \($0)." } ?? " You have no emergency contact yet: add one under Profile → Safety."
            return s
        case .failed(let m): return m
        }
    }
}

struct MainTabs: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        TabView(selection: $model.selectedTab) {
            MapScreen().tabItem { Label("Map", systemImage: "map.fill") }.tag(AppTab.map)
            NavigationStack { ExploreView() }.tabItem { Label("Explore", systemImage: "binoculars.fill") }.tag(AppTab.explore)
            NavigationStack { PlayView() }.tabItem { Label("Play", systemImage: "trophy.fill") }.tag(AppTab.play)
            NavigationStack { CircleView() }.tabItem { Label("Circle", systemImage: "person.3.fill") }.tag(AppTab.circle)
        }
    }
}
