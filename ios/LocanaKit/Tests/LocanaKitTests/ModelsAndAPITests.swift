import XCTest
@testable import LocanaKit

final class ModelsTests: XCTestCase {
    private func decode<T: Decodable>(_ json: String, as type: T.Type = T.self) throws -> T { try JSONDecoder().decode(T.self, from: Data(json.utf8)) }

    func testMeWithoutTheNewestPrivacyField() throws {
        let me: MeDto = try decode("""
        {"id":"u1","email":"a@b.c","username":"rani","hasPassword":true,"googleLinked":false,"createdAt":"2026-11-08T00:00:00.000Z",
         "profile":{"displayName":"Rani","avatarKey":"explorer_teal","onboardingDone":true},
         "progress":{"level":5,"totalXp":900,"lifetimeXp":900,"levelStartXp":800,"nextLevelXp":1100,"levelProgress":0.33,"currentStreak":1,"longestStreak":3,"tilesDiscovered":197,"questsCompleted":4,"totalDistanceM":12000},
         "privacy":{"sharingPaused":false,"historyEnabled":true,"historyRetentionDays":30,"explorationEnabled":true,"showInLeaderboards":true,"shareExplorationWithCircles":false,"notifyArrivals":true,"notifyQuests":true,"notifyAchievements":true,"notifyCircle":true}}
        """)
        XCTAssertEqual(me.profile.displayName, "Rani")
        XCTAssertNil(me.privacy.notifyTrips)
        XCTAssertEqual(me.progress.tilesDiscovered, 197)
    }

    func testCircleDetailAndMembersWithMissingOptionals() throws {
        let c: CircleDetailDto = try decode("""
        {"id":"c1","name":"Family","avatarKey":"circle_compass","createdAt":"x","myRole":"OWNER","muted":false,
         "members":[{"userId":"u1","username":"rani","displayName":"Rani","role":"OWNER","joinedAt":"x"}]}
        """)
        XCTAssertEqual(c.hiddenFromViewerIds, [])
        XCTAssertEqual(c.members[0].level, 1)
        XCTAssertFalse(c.members[0].blockedByMe)
    }

    func testNotificationPayloadKeepsItsShape() throws {
        let n: NotificationDto = try decode("""
        {"id":"n1","kind":"QUICK_MESSAGE","title":"Rani: Love ya! ❤️","body":"","read":false,"createdAt":"2026-11-08T00:00:00.000Z",
         "data":{"code":"LOVE","fromUserId":"u2","fromName":"Rani","emoji":"❤️"}}
        """)
        XCTAssertEqual(n.data?["code"]?.string, "LOVE")
        XCTAssertEqual(n.data?["fromUserId"]?.string, "u2")
    }

    func testUploadResponseWithoutOptionalLists() throws {
        let r: UploadResponse = try decode(#"{"accepted":3,"sharingActive":true}"#)
        XCTAssertEqual(r.accepted, 3)
        XCTAssertEqual(r.rejected, [])
        XCTAssertNil(r.game)
    }

    func testTimelineSegmentOldAndNewShapes() throws {
        let old: TimelineSegmentDto = try decode(#"{"type":"TRIP","startedAt":"a","endedAt":"b","durationSec":1,"path":[[1,2]]}"#)
        XCTAssertEqual(old.pathAt, [])
        XCTAssertNil(old.eventCounts)
        let new: TimelineSegmentDto = try decode(#"{"type":"TRIP","startedAt":"a","endedAt":"b","durationSec":1,"path":[[1,2],[3,4]],"pathAt":[10,20],"events":[{"kind":"HARD_BRAKE","at":15,"latitude":2,"longitude":3}],"eventCounts":{"hardBrake":1,"rapidAccel":0,"phoneUse":0}}"#)
        XCTAssertEqual(new.pathAt, [10, 20])
        XCTAssertEqual(new.events.first?.kind, "HARD_BRAKE")
        XCTAssertEqual(new.eventCounts?.hardBrake, 1)
    }

    func testBattleResultDefaults() throws {
        let r: AttackResultDto = try decode("""
        {"outcome":"CONTINUE","hit":{"damage":9},"counter":{"damage":4,"crit":false},"coins":10,
         "encounter":{"id":"e","status":"ACTIVE","monster":{"code":"slime","name":"Road Slime","emoji":"🟢"},"hp":20,"maxHp":30,"playerHp":96,"maxPlayerHp":100,"rewardCoins":10,"rewardXp":15,"expiresAt":"x"}}
        """)
        XCTAssertFalse(r.hit.crit)
        XCTAssertEqual(r.counter?.damage, 4)
        XCTAssertNil(r.rewards)
    }

    func testEmergencyContactEncodesWithoutServerIdWhenNew() throws {
        let data = try JSONEncoder().encode(SetContactsRequest(contacts: [EmergencyContactDto(name: "Ibu", phone: "0811")]))
        let text = String(decoding: data, as: UTF8.self)
        XCTAssertTrue(text.contains("\"name\":\"Ibu\""))
        XCTAssertFalse(text.contains("\"id\""))
    }
}

final class StubProtocol: URLProtocol {
    static var handler: ((URLRequest) throws -> (Int, Data))?
    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        do {
            let (status, data) = try StubProtocol.handler!(request)
            let resp = HTTPURLResponse(url: request.url!, statusCode: status, httpVersion: nil, headerFields: ["Content-Type": "application/json"])!
            client?.urlProtocol(self, didReceive: resp, cacheStoragePolicy: .notAllowed)
            client?.urlProtocol(self, didLoad: data)
            client?.urlProtocolDidFinishLoading(self)
        } catch {
            client?.urlProtocol(self, didFailWithError: error)
        }
    }
    override func stopLoading() {}
}

final class APIClientTests: XCTestCase {
    private func makeClient(store: TokenStore) -> APIClient {
        let cfg = URLSessionConfiguration.ephemeral
        cfg.protocolClasses = [StubProtocol.self]
        return APIClient(baseURL: URL(string: "https://api.example.test/")!, store: store, session: URLSession(configuration: cfg))
    }

    private let meJSON = """
    {"id":"u1","email":"a@b.c","username":"rani","hasPassword":true,"googleLinked":false,"createdAt":"x",
     "profile":{"displayName":"Rani","avatarKey":"explorer_teal","onboardingDone":true},
     "progress":{"level":1,"totalXp":0,"lifetimeXp":0,"levelStartXp":0,"levelProgress":0,"currentStreak":0,"longestStreak":0,"tilesDiscovered":0,"questsCompleted":0,"totalDistanceM":0},
     "privacy":{"sharingPaused":false,"historyEnabled":true,"historyRetentionDays":30,"explorationEnabled":true,"showInLeaderboards":true,"shareExplorationWithCircles":false,"notifyArrivals":true,"notifyQuests":true,"notifyAchievements":true,"notifyCircle":true}}
    """

    func testAddsTheBearerToken() async throws {
        let store = MemoryTokenStore(Tokens(access: "tok", refresh: "r", userId: "u1"))
        var seen: String?
        StubProtocol.handler = { req in
            seen = req.value(forHTTPHeaderField: "Authorization")
            XCTAssertEqual(req.url?.path, "/v1/me")
            return (200, Data(self.meJSON.utf8))
        }
        let me = try await makeClient(store: store).me()
        XCTAssertEqual(me.username, "rani")
        XCTAssertEqual(seen, "Bearer tok")
    }

    func testRefreshesOnceAfter401AndRetries() async throws {
        let store = MemoryTokenStore(Tokens(access: "old", refresh: "r1", userId: "u1"))
        var meCalls = 0, refreshCalls = 0
        StubProtocol.handler = { req in
            if req.url?.path == "/v1/auth/refresh" {
                refreshCalls += 1
                return (200, Data(#"{"accessToken":"new","accessTokenExpiresIn":900,"refreshToken":"r2","sessionId":"s"}"#.utf8))
            }
            meCalls += 1
            if req.value(forHTTPHeaderField: "Authorization") == "Bearer old" { return (401, Data(#"{"error":{"code":"UNAUTHORIZED","message":"expired"}}"#.utf8)) }
            return (200, Data(self.meJSON.utf8))
        }
        let me = try await makeClient(store: store).me()
        XCTAssertEqual(me.id, "u1")
        XCTAssertEqual(refreshCalls, 1)
        XCTAssertEqual(meCalls, 2)
        XCTAssertEqual(store.load(), Tokens(access: "new", refresh: "r2", userId: "u1"))
    }

    func testRejectedRefreshEndsTheSession() async {
        let store = MemoryTokenStore(Tokens(access: "old", refresh: "dead", userId: "u1"))
        StubProtocol.handler = { req in (401, Data(#"{"error":{"code":"UNAUTHORIZED","message":"no"}}"#.utf8)) }
        let client = makeClient(store: store)
        var signedOut: String?
        client.onSignedOut = { signedOut = $0 }
        do { _ = try await client.me(); XCTFail("should throw") }
        catch let e as APIError { XCTAssertEqual(e.status, 401) }
        catch { XCTFail("wrong error \(error)") }
        XCTAssertNil(store.load())
        XCTAssertNotNil(signedOut)
    }

    func testServerErrorsCarryTheirCode() async {
        let store = MemoryTokenStore(Tokens(access: "t", refresh: "r", userId: "u"))
        StubProtocol.handler = { _ in (409, Data(#"{"error":{"code":"NOT_ENOUGH_COINS","message":"You need 40 coins"}}"#.utf8)) }
        do { _ = try await makeClient(store: store).buy("wooden_sword"); XCTFail("should throw") }
        catch let e as APIError {
            XCTAssertEqual(e.code, "NOT_ENOUGH_COINS")
            XCTAssertEqual(e.errorDescription, "You need 40 coins")
        } catch { XCTFail("wrong error \(error)") }
    }

    func testOfflineIsItsOwnError() async {
        let store = MemoryTokenStore(Tokens(access: "t", refresh: "r", userId: "u"))
        StubProtocol.handler = { _ in throw URLError(.notConnectedToInternet) }
        do { _ = try await makeClient(store: store).me(); XCTFail("should throw") }
        catch let e as APIError { XCTAssertEqual(e, .offline) }
        catch { XCTFail("wrong error \(error)") }
    }

    func testSignInIsSavedAndCleared() throws {
        let store = MemoryTokenStore()
        let client = makeClient(store: store)
        let auth = try JSONDecoder().decode(AuthResponse.self, from: Data("""
        {"tokens":{"accessToken":"a","accessTokenExpiresIn":900,"refreshToken":"r","sessionId":"s"},"user":\(meJSON)}
        """.utf8))
        client.saveSignIn(auth)
        XCTAssertEqual(store.load(), Tokens(access: "a", refresh: "r", userId: "u1"))
        client.clearSignIn()
        XCTAssertFalse(client.isSignedIn)
    }
}
