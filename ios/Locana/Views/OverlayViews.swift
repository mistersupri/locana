import SwiftUI
import LocanaKit

/// The emoji that fall for each quick message, so "Love ya!" rains hearts and "Haha" rains laughter.
func confettiEmojis(code: String, fallback: String) -> [String] {
    switch code {
    case "LOVE": return ["❤️", "💖", "💕", "😍"]
    case "SLOW_DOWN": return ["😳", "💦", "🐢"]
    case "CALL_ME": return ["📞", "📱", "☎️"]
    case "WHERE_ARE_YOU": return ["📍", "🔎", "🗺️"]
    case "ON_MY_WAY": return ["🚗", "💨", "🛣️"]
    case "BE_CAREFUL": return ["🙏", "🛡️", "⚠️"]
    case "THANK_YOU": return ["🙌", "✨", "🙏"]
    case "HAHA": return ["😂", "🤣", "😆"]
    case "NOTED_CAPTAIN": return ["🎖️", "⭐", "👍"]
    case "SNACK_TIME": return ["🍜", "🍕", "🍔", "🍟"]
    case "BEEP_BEEP": return ["🚙", "💨", "🚦"]
    case "TOO_SLOW": return ["🐌", "💤", "⏳"]
    case "GOAT": return ["🐐", "🏆", "👑"]
    case "SPILL_TEA": return ["☕", "🍵", "👀"]
    case "LETS_PARTY": return ["🎉", "🎊", "🥳"]
    case "BOO": return ["👻", "🎃", "💀"]
    default: return [fallback]
    }
}

/// About thirty emoji falling and swaying once from the top to the bottom of the screen.
struct EmojiConfetti: View {
    private struct Piece { let emoji: String; let x: Double; let size: CGFloat; let start: Double; let speed: Double; let sway: Double; let spin: Double }
    private let pieces: [Piece]
    private let began = Date()

    init(emojis: [String]) {
        var seed: UInt64 = UInt64(abs(emojis.joined().hashValue % 100_000) + 7)
        func next() -> Double { seed = seed &* 6364136223846793005 &+ 1442695040888963407; return Double((seed >> 33) % 10_000) / 10_000 }
        pieces = (0..<30).map { i in Piece(emoji: emojis[i % emojis.count], x: next(), size: 22 + CGFloat(next()) * 20, start: next() * 0.4, speed: 0.75 + next() * 0.5, sway: 10 + next() * 30, spin: (next() - 0.5) * 240) }
    }

    var body: some View {
        TimelineView(.animation) { ctx in
            Canvas { gc, size in
                let p = min(1, ctx.date.timeIntervalSince(began) / 3.0)
                for piece in pieces {
                    let local = max(0, min(1, (p - piece.start) / (1 - piece.start))) * piece.speed
                    if local <= 0 || local > 1.15 { continue }
                    let x = piece.x * size.width + sin(local * 9) * piece.sway
                    let y = -60 + local * (size.height + 140)
                    var g = gc
                    g.translateBy(x: x, y: y)
                    g.rotate(by: .degrees(piece.spin * local))
                    g.opacity = max(0, min(1, 1.15 - local))
                    g.draw(Text(piece.emoji).font(.system(size: piece.size)), at: .zero)
                }
            }
        }
        .allowsHitTesting(false)
        .ignoresSafeArea()
    }
}

/// A quick message from someone in a circle: a banner at the top and emoji falling over the whole screen for a few seconds.
struct QuickMessageOverlay: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ZStack(alignment: .top) {
            if let m = model.incoming {
                if !reduceMotion { EmojiConfetti(emojis: confettiEmojis(code: m.code, fallback: m.emoji)).id(m.id) }
                HStack(spacing: 12) {
                    Text(m.emoji).font(.system(size: 30))
                    VStack(alignment: .leading, spacing: 1) {
                        Text(m.fromName).font(.body(13, weight: .semibold)).foregroundStyle(Palette.ink2)
                        Text(m.text).font(.display(18)).foregroundStyle(Palette.ink)
                    }
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, 16).padding(.vertical, 12)
                .card(radius: 24)
                .padding(.horizontal, 16).padding(.top, 8)
                .transition(.move(edge: .top).combined(with: .opacity))
                .accessibilityElement(children: .combine)
                .accessibilityLabel("\(m.fromName) says: \(m.text)")
            }
        }
        .animation(.spring(response: 0.45, dampingFraction: 0.75), value: model.incoming)
        .task(id: model.incoming?.id) {
            guard model.incoming != nil else { return }
            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
            try? await Task.sleep(nanoseconds: 3_800_000_000)
            model.incoming = nil
        }
        .allowsHitTesting(false)
    }
}

/// "Are you okay?" after a hard impact: a big countdown and one button to say you are fine.
struct CrashAlertView: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        ZStack {
            Color(hex: 0xD13E2B).ignoresSafeArea()
            VStack(spacing: 22) {
                Text("Are you okay?").font(.display(34)).foregroundStyle(.white)
                Text("\(model.crashSecondsLeft ?? 0)").font(.system(size: 120, weight: .heavy, design: .rounded)).foregroundStyle(.white)
                    .accessibilityLabel("\(model.crashSecondsLeft ?? 0) seconds left")
                Text("A hard impact was detected. If you do not answer, an SOS goes to your circles and emergency contacts.")
                    .font(.body(17)).foregroundStyle(.white).multilineTextAlignment(.center)
                Button("I'm OK") { model.imOk() }.buttonStyle(.chunky())
                Button("Send SOS now") { model.sendSOSNowFromCrash() }.buttonStyle(.chunky(.yellow))
            }
            .padding(28)
        }
    }
}
