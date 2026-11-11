import XCTest
@testable import Locana

final class SmokeTests: XCTestCase {
    func testApiURLEndsWithASlashAndHasAWebSocketTwin() {
        XCTAssertTrue(AppConfig.apiURL.absoluteString.hasSuffix("/"))
        XCTAssertTrue(AppConfig.wsURL.absoluteString.hasSuffix("/ws"))
        XCTAssertTrue(AppConfig.wsURL.scheme == "ws" || AppConfig.wsURL.scheme == "wss")
    }

    func testFrameStylesKnowTheCollectionFrames() {
        for key in ["frame_neon", "frame_sakura", "frame_ice", "frame_flame", "frame_aurora", "frame_gold", "frame_trailhead", "frame_summit"] {
            XCTAssertNotNil(FrameStyles.colors(key), key)
        }
        XCTAssertNil(FrameStyles.colors("something_else"))
    }

    func testConfettiHasEmojiForEveryMessage() {
        for code in ["LOVE", "SLOW_DOWN", "CALL_ME", "WHERE_ARE_YOU", "ON_MY_WAY", "BE_CAREFUL", "THANK_YOU", "HAHA", "GOAT", "BOO"] {
            XCTAssertGreaterThan(confettiEmojis(code: code, fallback: "x").count, 1, code)
        }
        XCTAssertEqual(confettiEmojis(code: "UNKNOWN", fallback: "💬"), ["💬"])
    }
}
