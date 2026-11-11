import SwiftUI
import UIKit
import LocanaKit

// The design language of the Android app, in SwiftUI: a warm cream page, white cards, one saturated violet for what
// you can act on; yellow marks rewards, mint "good", coral danger, sky information. Every colour has a dark pair.

extension UIColor {
    convenience init(hex: UInt32) {
        self.init(red: CGFloat((hex >> 16) & 0xFF) / 255, green: CGFloat((hex >> 8) & 0xFF) / 255, blue: CGFloat(hex & 0xFF) / 255, alpha: 1)
    }
}

extension Color {
    init(hex: UInt32) { self.init(uiColor: UIColor(hex: hex)) }
    static func dynamic(_ light: UInt32, _ dark: UInt32) -> Color {
        Color(uiColor: UIColor { $0.userInterfaceStyle == .dark ? UIColor(hex: dark) : UIColor(hex: light) })
    }
}

enum Palette {
    static let bg = Color.dynamic(0xFBF6EE, 0x100B22)
    static let card = Color.dynamic(0xFFFFFF, 0x181133)
    static let ink = Color.dynamic(0x1F1645, 0xEDE8FB)
    static let ink2 = Color.dynamic(0x5F587F, 0xB3ABD1)
    static let ink3 = Color.dynamic(0x9C97B6, 0x8A82B0)
    static let line = Color.dynamic(0xE9E3F4, 0x332A5E)
    static let primary = Color.dynamic(0x6B3BF5, 0xB9A2FF)
    static let primaryStrong = Color(hex: 0x6B3BF5)
    static let primaryEdge = Color.dynamic(0x4620C4, 0x7B5FD6)
    static let onPrimary = Color.dynamic(0xFFFFFF, 0x1A0B45)
    static let primarySoft = Color.dynamic(0xECE4FF, 0x3A2A7A)
    static let yellow = Color(hex: 0xFFC93C)
    static let yellowEdge = Color(hex: 0xD99A0B)
    static let yellowSoft = Color.dynamic(0xFFF1C9, 0x4A3500)
    static let onAmber = Color.dynamic(0xA86F00, 0xFFD27A)
    static let mint = Color.dynamic(0x22C489, 0x3DD9A0)
    static let mintSoft = Color.dynamic(0xD8F6E9, 0x0F3A2D)
    static let onMint = Color.dynamic(0x0E8A5D, 0x6FE3B8)
    static let coral = Color.dynamic(0xFF6550, 0xFF7A66)
    static let coralStrong = Color(hex: 0xFF6550)
    static let coralEdge = Color(hex: 0xD13E2B)
    static let coralSoft = Color.dynamic(0xFFE2DC, 0x4A1E18)
    static let onCoral = Color.dynamic(0xD13E2B, 0xFF8A7A)
    static let skySoft = Color.dynamic(0xDCF0FF, 0x12344D)
    static let onSky = Color.dynamic(0x1E6FB0, 0x8CCBFF)
    static let edge = Color.dynamic(0xE1D6FA, 0x3A2F6B)
}

extension Font {
    /// Headings and numbers: heavy rounded, the closest system face to the Android app's Bricolage Grotesque.
    static func display(_ size: CGFloat) -> Font { .system(size: size, weight: .heavy, design: .rounded) }
    static func body(_ size: CGFloat = 16, weight: Font.Weight = .medium) -> Font { .system(size: size, weight: weight, design: .rounded) }
}

// MARK: buttons

struct ChunkyButtonStyle: ButtonStyle {
    enum Kind { case primary, secondary, yellow, coral }
    var kind: Kind = .primary
    var compact = false
    var enabled = true

    private var face: Color { switch kind { case .primary: Palette.primaryStrong; case .secondary: Palette.card; case .yellow: Palette.yellow; case .coral: Palette.coralStrong } }
    private var edge: Color { switch kind { case .primary: Color(hex: 0x4620C4); case .secondary: Palette.edge; case .yellow: Palette.yellowEdge; case .coral: Palette.coralEdge } }
    private var fg: Color { switch kind { case .primary: .white; case .secondary: Palette.primary; case .yellow: Color(hex: 0x3A2800); case .coral: .white } }

    func makeBody(configuration: Configuration) -> some View {
        let pressed = configuration.isPressed
        configuration.label
            .font(.system(size: compact ? 14 : 16, weight: .heavy, design: .rounded))
            .foregroundStyle(fg)
            .padding(.vertical, compact ? 9 : 15)
            .padding(.horizontal, compact ? 16 : 20)
            .frame(maxWidth: compact ? nil : .infinity)
            .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(face))
            .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(edge).offset(y: pressed ? 1 : 4))
            .offset(y: pressed ? 3 : 0)
            .padding(.bottom, 4)
            .opacity(enabled ? 1 : 0.45)
            .animation(.easeOut(duration: 0.09), value: pressed)
    }
}

extension ButtonStyle where Self == ChunkyButtonStyle {
    static func chunky(_ kind: ChunkyButtonStyle.Kind = .primary, compact: Bool = false, enabled: Bool = true) -> ChunkyButtonStyle { ChunkyButtonStyle(kind: kind, compact: compact, enabled: enabled) }
}

// MARK: surfaces

struct CardModifier: ViewModifier {
    var radius: CGFloat = 22
    var fill: Color = Palette.card
    func body(content: Content) -> some View {
        content
            .background(RoundedRectangle(cornerRadius: radius, style: .continuous).fill(fill))
            .shadow(color: Color.black.opacity(0.07), radius: 8, x: 0, y: 3)
    }
}

extension View {
    func card(radius: CGFloat = 22, fill: Color = Palette.card) -> some View { modifier(CardModifier(radius: radius, fill: fill)) }
    /// Fades in while rising a little, the first time a card or row appears.
    func enterRise(delay: Double = 0) -> some View { modifier(EnterRise(delay: delay)) }
}

struct EnterRise: ViewModifier {
    var delay: Double
    @State private var shown = false
    func body(content: Content) -> some View {
        content
            .opacity(shown ? 1 : 0)
            .offset(y: shown ? 0 : 14)
            .onAppear { withAnimation(.easeOut(duration: 0.36).delay(delay)) { shown = true } }
    }
}

struct Pill: View {
    var text: String
    var bg: Color = Palette.primarySoft
    var fg: Color = Palette.primary
    var icon: String? = nil
    var body: some View {
        HStack(spacing: 5) {
            if let icon { Image(systemName: icon).font(.system(size: 11, weight: .bold)) }
            Text(text).font(.system(size: 12, weight: .heavy, design: .rounded)).lineLimit(1)
        }
        .foregroundStyle(fg)
        .padding(.horizontal, 10).padding(.vertical, 5)
        .background(Capsule().fill(bg))
    }
}

struct SectionTitle: View {
    var text: String
    var action: (title: String, run: () -> Void)? = nil
    var body: some View {
        HStack {
            Text(text).font(.display(20)).foregroundStyle(Palette.ink)
            Spacer()
            if let action { Button(action.title, action: action.run).font(.system(size: 14, weight: .heavy, design: .rounded)).foregroundStyle(Palette.primary) }
        }
        .padding(.top, 18).padding(.bottom, 6)
    }
}

struct Illustration: View {
    var name: String
    var size: CGFloat = 96
    var body: some View {
        Image("ill_\(name)").resizable().scaledToFit().frame(width: size, height: size)
    }
}

struct LoadingView: View {
    var body: some View { ProgressView().frame(maxWidth: .infinity, minHeight: 160) }
}

struct ErrorView: View {
    var message: String
    var retry: (() -> Void)?
    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: "exclamationmark.triangle").font(.system(size: 28)).foregroundStyle(Palette.onCoral)
            Text(message).font(.body(15)).foregroundStyle(Palette.ink2).multilineTextAlignment(.center)
            if let retry { Button("Try again", action: retry).buttonStyle(.chunky(.secondary, compact: true)) }
        }
        .padding(24).frame(maxWidth: .infinity)
    }
}

struct EmptyStateView: View {
    var title: String
    var message: String
    var illustration: String = "compass"
    var body: some View {
        VStack(spacing: 10) {
            Illustration(name: illustration, size: 120)
            Text(title).font(.display(22)).foregroundStyle(Palette.ink).multilineTextAlignment(.center)
            Text(message).font(.body(15)).foregroundStyle(Palette.ink2).multilineTextAlignment(.center)
        }
        .padding(24).frame(maxWidth: .infinity)
    }
}

/// A page with the app's cream background and a bold title, like the Android tabs.
struct ScreenScaffold<Content: View>: View {
    var title: String
    @ViewBuilder var content: () -> Content
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) { content() }
                .padding(.horizontal, 20).padding(.bottom, 28)
        }
        .background(Palette.bg.ignoresSafeArea())
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.large)
    }
}

// MARK: avatars and frames

/// The colours swept around a pin or avatar for each collection frame; unknown keys fall back to plain gold.
enum FrameStyles {
    static func colors(_ key: String?) -> [Color]? {
        switch key {
        case "frame_trailhead": return [Color(hex: 0x2E7D4F), Color(hex: 0x7BD389), Color(hex: 0x2E7D4F)]
        case "frame_summit": return [Color(hex: 0xFFC93C), Color(hex: 0xFFF1C9), Color(hex: 0xFFC93C)]
        case "frame_neon": return [Color(hex: 0x00E5FF), Color(hex: 0xFF2BD6), Color(hex: 0x00E5FF)]
        case "frame_sakura": return [Color(hex: 0xFFB7C5), Color(hex: 0xFF7AA2), Color(hex: 0xFFE0E8), Color(hex: 0xFFB7C5)]
        case "frame_ice": return [Color(hex: 0xBDEBFF), Color(hex: 0x5AB8FF), .white, Color(hex: 0xBDEBFF)]
        case "frame_flame": return [Color(hex: 0xFF3D00), Color(hex: 0xFFB300), Color(hex: 0xFF3D00)]
        case "frame_aurora": return [Color(hex: 0x22C489), Color(hex: 0x45AFFF), Color(hex: 0x9B6BFF), Color(hex: 0x22C489)]
        case "frame_gold": return [Color(hex: 0xFFD54A), Color(hex: 0xB8860B), Color(hex: 0xFFF3B0), Color(hex: 0xFFD54A)]
        default: return nil
        }
    }
    static func glows(_ key: String?) -> Bool { ["frame_neon", "frame_flame", "frame_aurora", "frame_gold", "frame_ice"].contains(key ?? "") }
}

enum AvatarColors {
    static func pair(_ key: String?) -> (bg: Color, fg: Color) {
        switch key {
        case "explorer_teal": return (Color(hex: 0x6B3BF5), .white)
        case "explorer_amber": return (Color(hex: 0xD9680F), .white)
        case "explorer_coral": return (Color(hex: 0xD13E2B), .white)
        case "explorer_moss": return (Color(hex: 0x2E7D4F), .white)
        case "explorer_plum": return (Color(hex: 0x8E4FA8), .white)
        default: return (Color(hex: 0x4A5568), .white)
        }
    }
}

struct AvatarView: View {
    @EnvironmentObject private var model: AppModel
    var name: String
    var avatarKey: String?
    var userId: String? = nil
    var photoVersion: Int64? = nil
    var frameKey: String? = nil
    var size: CGFloat = 44
    var dim = false
    @State private var photo: UIImage?

    var body: some View {
        let colors = AvatarColors.pair(avatarKey)
        let ring = FrameStyles.colors(frameKey)
        ZStack {
            if let ring {
                if FrameStyles.glows(frameKey) { Circle().stroke(ring[0].opacity(0.28), lineWidth: size * 0.12).blur(radius: 1) }
                Circle().strokeBorder(AngularGradient(colors: ring, center: .center), lineWidth: max(2.5, size * 0.07))
            } else if frameKey != nil {
                Circle().strokeBorder(Palette.yellow, lineWidth: max(2.5, size * 0.06))
            }
            Group {
                if let photo {
                    Image(uiImage: photo).resizable().scaledToFill()
                } else {
                    ZStack {
                        colors.bg
                        Text(Formatting.initials(name)).font(.system(size: size * 0.36, weight: .heavy, design: .rounded)).foregroundStyle(colors.fg)
                    }
                }
            }
            .frame(width: size * (frameKey != nil ? 0.82 : 1), height: size * (frameKey != nil ? 0.82 : 1))
            .clipShape(Circle())
            .opacity(dim ? 0.45 : 1)
        }
        .frame(width: size, height: size)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(name)
        .task(id: "\(userId ?? "")-\(photoVersion ?? 0)") {
            guard let userId, let photoVersion else { photo = nil; return }
            photo = await model.photos.image(userId: userId, version: photoVersion)
        }
    }
}
