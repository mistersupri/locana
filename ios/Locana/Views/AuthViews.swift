import SwiftUI
import LocanaKit

/// A text field in the app's style: the label above a white rounded box; an error turns the box coral.
struct LField: View {
    var label: String
    @Binding var text: String
    var error: String? = nil
    var secure = false
    var keyboard: UIKeyboardType = .default
    var capitalize: TextInputAutocapitalization = .never

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label).font(.body(13, weight: .bold)).foregroundStyle(error == nil ? Palette.ink2 : Palette.onCoral)
            Group {
                if secure { SecureField("", text: $text) } else { TextField("", text: $text) }
            }
            .keyboardType(keyboard).textInputAutocapitalization(capitalize).autocorrectionDisabled()
            .font(.body(17, weight: .semibold)).foregroundStyle(Palette.ink)
            .padding(.horizontal, 14).frame(minHeight: 52)
            .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(Palette.card))
            .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(error == nil ? Palette.line : Palette.coral, lineWidth: 2))
            .accessibilityLabel(label)
            if let error { Text(error).font(.body(12, weight: .semibold)).foregroundStyle(Palette.onCoral) }
        }
    }
}

struct AuthFlow: View {
    @EnvironmentObject private var model: AppModel
    @State private var path: [String] = []

    var body: some View {
        NavigationStack(path: $path) {
            VStack(spacing: 18) {
                Spacer(minLength: 20)
                Image("mascot_pin").resizable().scaledToFit().frame(height: 190).enterRise()
                Text("Locana").font(.display(40)).foregroundStyle(Palette.ink)
                Text("Share where you are with the people you trust, and turn everyday walks into a map you reveal together.")
                    .font(.body(16)).foregroundStyle(Palette.ink2).multilineTextAlignment(.center).padding(.horizontal, 12)
                VStack(alignment: .leading, spacing: 10) {
                    promise("lock.shield.fill", "You stay in control", "Pause or stop sharing any time. Circles only see what you choose.")
                    promise("map.fill", "Walks become a map", "Every new street you walk lights a tile. Only you see your tiles.")
                }.padding(.top, 6)
                Spacer()
                if let notice = model.notice { Text(notice).font(.body(14, weight: .semibold)).foregroundStyle(Palette.onCoral).multilineTextAlignment(.center) }
                Button("Create an account") { path.append("register") }.buttonStyle(.chunky())
                Button("Sign in") { path.append("login") }.buttonStyle(.chunky(.secondary))
            }
            .padding(.horizontal, 24).padding(.bottom, 12)
            .background(Palette.bg.ignoresSafeArea())
            .navigationDestination(for: String.self) { route in
                if route == "login" { LoginView() } else { RegisterView() }
            }
        }
    }

    private func promise(_ icon: String, _ title: String, _ text: String) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: icon).font(.system(size: 18, weight: .bold)).foregroundStyle(Palette.primary).frame(width: 40, height: 40).background(RoundedRectangle(cornerRadius: 12).fill(Palette.primarySoft))
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.body(15, weight: .heavy)).foregroundStyle(Palette.ink)
                Text(text).font(.body(13)).foregroundStyle(Palette.ink2)
            }
        }
    }
}

struct LoginView: View {
    @EnvironmentObject private var model: AppModel
    @State private var identifier = ""
    @State private var password = ""
    @State private var error: String?
    @State private var busy = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text("Welcome back").font(.display(30)).foregroundStyle(Palette.ink)
                Text("Sign in to see your circles.").font(.body(16)).foregroundStyle(Palette.ink2)
                LField(label: "Email or username", text: $identifier, keyboard: .emailAddress)
                LField(label: "Password", text: $password, secure: true)
                if let error { Text(error).font(.body(14, weight: .semibold)).foregroundStyle(Palette.onCoral) }
                Button(busy ? "Signing in…" : "Sign in") { Task { await submit() } }
                    .buttonStyle(.chunky(enabled: !busy && !identifier.isEmpty && !password.isEmpty))
                    .disabled(busy || identifier.isEmpty || password.isEmpty).padding(.top, 8)
            }
            .padding(24)
        }
        .background(Palette.bg.ignoresSafeArea())
        .navigationBarTitleDisplayMode(.inline)
    }

    private func submit() async {
        busy = true; error = nil
        do { try await model.signIn(identifier: identifier.trimmingCharacters(in: .whitespaces), password: password) }
        catch { self.error = (error as? LocalizedError)?.errorDescription ?? "Couldn't sign in." }
        busy = false
    }
}

struct RegisterView: View {
    @EnvironmentObject private var model: AppModel
    @State private var name = ""
    @State private var username = ""
    @State private var email = ""
    @State private var password = ""
    @State private var showErrors = false
    @State private var error: String?
    @State private var busy = false

    private var nameError: String? { showErrors && !(1...40).contains(name.trimmingCharacters(in: .whitespaces).count) ? "Use 1-40 characters" : nil }
    private var userError: String? { showErrors && username.range(of: "^[a-z0-9_.]{3,24}$", options: .regularExpression) == nil ? "3-24 letters (lowercase), digits, _ or ." : nil }
    private var emailError: String? { showErrors && !email.contains("@") ? "Enter a valid e-mail" : nil }
    private var passError: String? { showErrors && password.count < 10 ? "Use at least 10 characters" : nil }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text("Create your account").font(.display(30)).foregroundStyle(Palette.ink)
                LField(label: "Your name", text: $name, error: nameError, capitalize: .words)
                LField(label: "Username", text: $username, error: userError)
                LField(label: "Email", text: $email, error: emailError, keyboard: .emailAddress)
                LField(label: "Password (10+ characters)", text: $password, error: passError, secure: true)
                if let error { Text(error).font(.body(14, weight: .semibold)).foregroundStyle(Palette.onCoral) }
                Button(busy ? "Creating…" : "Create account") { Task { await submit() } }.buttonStyle(.chunky(enabled: !busy)).disabled(busy).padding(.top, 8)
            }
            .padding(24)
        }
        .background(Palette.bg.ignoresSafeArea())
        .navigationBarTitleDisplayMode(.inline)
    }

    private func submit() async {
        showErrors = true
        guard nameError == nil, userError == nil, emailError == nil, passError == nil else { return }
        busy = true; error = nil
        do { try await model.register(email: email.trimmingCharacters(in: .whitespaces), password: password, username: username, name: name.trimmingCharacters(in: .whitespaces)) }
        catch { self.error = (error as? LocalizedError)?.errorDescription ?? "Couldn't create the account." }
        busy = false
    }
}
