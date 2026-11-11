import SwiftUI
import LocanaKit

let defaultQuickMessages: [QuickMessageDefDto] = [
    QuickMessageDefDto(code: "LOVE", text: "Love ya!", emoji: "❤️"),
    QuickMessageDefDto(code: "SLOW_DOWN", text: "Slow down", emoji: "😳"),
    QuickMessageDefDto(code: "CALL_ME", text: "Call me", emoji: "📞"),
    QuickMessageDefDto(code: "WHERE_ARE_YOU", text: "Where are you?", emoji: "📍"),
    QuickMessageDefDto(code: "ON_MY_WAY", text: "On my way", emoji: "🚗"),
    QuickMessageDefDto(code: "BE_CAREFUL", text: "Be careful", emoji: "🙏"),
    QuickMessageDefDto(code: "THANK_YOU", text: "Thank you", emoji: "🙌"),
    QuickMessageDefDto(code: "HAHA", text: "Haha", emoji: "😂"),
]

/// Hold to send an SOS. A ring fills while the finger stays down; letting go early cancels, so a pocket touch never raises the alarm.
struct SosButton: View {
    var onTriggered: () -> Void
    @State private var progress: CGFloat = 0
    @State private var pressing = false
    @State private var hint = false
    @State private var fired = false

    var body: some View {
        ZStack {
            Circle().fill(Palette.coralStrong)
            Circle().trim(from: 0, to: progress).stroke(Color.white, style: StrokeStyle(lineWidth: 5, lineCap: .round)).rotationEffect(.degrees(-90)).padding(3)
            Text("SOS").font(.display(14)).foregroundStyle(.white)
        }
        .frame(width: 54, height: 54)
        .shadow(color: .black.opacity(0.2), radius: 6, y: 3)
        .overlay(alignment: .leading) {
            if hint {
                Text("Hold to send SOS").font(.body(12, weight: .bold)).foregroundStyle(Palette.ink)
                    .padding(.horizontal, 10).padding(.vertical, 6).background(Capsule().fill(Palette.card)).fixedSize()
                    .offset(x: -130).transition(.opacity)
            }
        }
        .scaleEffect(pressing ? 0.94 : 1)
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { _ in
                    guard !pressing else { return }
                    pressing = true; fired = false
                    withAnimation(.linear(duration: 1.5)) { progress = 1 }
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                        if pressing && !fired {
                            fired = true
                            UINotificationFeedbackGenerator().notificationOccurred(.warning)
                            onTriggered()
                            reset()
                        }
                    }
                }
                .onEnded { _ in
                    if pressing && !fired { showHint() }
                    reset()
                }
        )
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("SOS. Press and hold to send an alert to your circles and emergency contacts.")
        .accessibilityAddTraits(.isButton)
        .accessibilityAction(named: "Send SOS") { onTriggered() }
    }

    private func reset() {
        pressing = false
        withAnimation(.easeOut(duration: 0.15)) { progress = 0 }
    }

    private func showHint() {
        withAnimation { hint = true }
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.2) { withAnimation { hint = false } }
    }
}

/// Chips of quick messages floating above the drawer while someone else's pin is in focus.
struct QuickMessageRow: View {
    var messages: [QuickMessageDefDto]
    var onSend: (QuickMessageDefDto) -> Void
    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 10) {
                ForEach(messages) { m in
                    Button { onSend(m) } label: {
                        HStack(spacing: 8) {
                            Text(m.emoji).font(.system(size: 20))
                            Text(m.text).font(.display(15)).foregroundStyle(Palette.primary)
                        }
                        .padding(.horizontal, 16).padding(.vertical, 11)
                        .background(Capsule().fill(Palette.card))
                        .shadow(color: .black.opacity(0.12), radius: 5, y: 2)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Send \(m.text) to this person")
                }
            }
            .padding(.horizontal, 16).padding(.vertical, 6)
        }
    }
}

/// Emergency contacts, SOS explanation and crash detection.
struct SafetyScreen: View {
    @EnvironmentObject private var model: AppModel
    @State private var contacts: [EmergencyContactDto] = []
    @State private var name = ""
    @State private var phone = ""
    @State private var email = ""
    @State private var error: String?
    @State private var saving = false
    @AppStorage("locana.crash") private var crash = true

    var body: some View {
        ScreenScaffold(title: "Safety") {
            VStack(alignment: .leading, spacing: 6) {
                Text("SOS").font(.display(22)).foregroundStyle(Palette.ink)
                Text("Hold the red SOS button on the map for 1.5 seconds. Everyone in your circles is notified with your position, they and your emergency contacts get an e-mail, and your phone offers to call the first emergency contact.")
                    .font(.body(15)).foregroundStyle(Palette.ink2)
            }
            .padding(16).frame(maxWidth: .infinity, alignment: .leading).card()

            SectionTitle(text: "Emergency contacts")
            if contacts.isEmpty { Text("No one yet. Add up to five people to call and e-mail when you send an SOS.").font(.body(15)).foregroundStyle(Palette.ink2) }
            ForEach(contacts) { c in
                HStack {
                    Image(systemName: "phone.fill").foregroundStyle(Palette.onCoral).frame(width: 40, height: 40).background(RoundedRectangle(cornerRadius: 12).fill(Palette.coralSoft))
                    VStack(alignment: .leading, spacing: 2) {
                        Text(c.name).font(.body(16, weight: .heavy)).foregroundStyle(Palette.ink)
                        Text([c.phone, c.email].compactMap { $0 }.joined(separator: " · ")).font(.body(13)).foregroundStyle(Palette.ink2)
                    }
                    Spacer()
                    Button("Remove") { Task { await save(contacts.filter { $0 != c }) } }.font(.body(14, weight: .heavy)).foregroundStyle(Palette.onCoral)
                }
                .padding(12).card(radius: 18)
            }
            if contacts.count < 5 {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Add a contact").font(.body(16, weight: .heavy)).foregroundStyle(Palette.ink)
                    LField(label: "Name", text: $name, capitalize: .words)
                    LField(label: "Phone number", text: $phone, keyboard: .phonePad)
                    LField(label: "E-mail (optional)", text: $email, keyboard: .emailAddress)
                    if let error { Text(error).font(.body(13, weight: .semibold)).foregroundStyle(Palette.onCoral) }
                    Button(saving ? "Saving…" : "Add contact") { Task { await add() } }
                        .buttonStyle(.chunky(enabled: !name.isEmpty && phone.count >= 6 && !saving))
                        .disabled(name.isEmpty || phone.count < 6 || saving)
                }
                .padding(16).card()
            }

            SectionTitle(text: "Crash detection")
            Toggle(isOn: $crash) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Detect crashes").font(.body(16, weight: .heavy)).foregroundStyle(Palette.ink)
                    Text("After a hard impact while you are in a vehicle, Locana asks \"Are you okay?\". With no answer in 15 seconds it sends an SOS.")
                        .font(.body(13)).foregroundStyle(Palette.ink2)
                }
            }
            .onChange(of: crash) { _, on in model.motion.crashEnabled = on; model.motion.updateImpactSensor() }
            .padding(16).card()
            Text("The motion sensor is read only while you seem to be driving, and nothing is recorded. It can be wrong: you can always answer \"I'm OK\".")
                .font(.body(13)).foregroundStyle(Palette.ink2)
        }
        .task { if let list = try? await model.api.emergencyContacts() { contacts = list } }
    }

    private func add() async {
        let c = EmergencyContactDto(name: name.trimmingCharacters(in: .whitespaces), phone: phone.trimmingCharacters(in: .whitespaces), email: email.isEmpty ? nil : email)
        await save(contacts + [c])
        if error == nil { name = ""; phone = ""; email = "" }
    }

    private func save(_ list: [EmergencyContactDto]) async {
        saving = true; error = nil
        do { contacts = try await model.api.setEmergencyContacts(list) }
        catch { self.error = (error as? LocalizedError)?.errorDescription ?? "Couldn't save." }
        saving = false
    }
}
