import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

public struct Tokens: Equatable, Codable {
    public var access: String
    public var refresh: String
    public var userId: String
    public init(access: String, refresh: String, userId: String) { self.access = access; self.refresh = refresh; self.userId = userId }
}

/// Where the sign-in lives between launches. The app stores it in the Keychain; tests use memory.
public protocol TokenStore: AnyObject {
    func load() -> Tokens?
    func save(_ tokens: Tokens?)
}

public final class MemoryTokenStore: TokenStore {
    private var value: Tokens?
    public init(_ value: Tokens? = nil) { self.value = value }
    public func load() -> Tokens? { value }
    public func save(_ tokens: Tokens?) { value = tokens }
}

public enum APIError: Error, Equatable, LocalizedError {
    case offline
    case server(status: Int, code: String?, message: String)
    case decoding(String)

    public var code: String? { if case .server(_, let c, _) = self { return c }; return nil }
    public var status: Int? { if case .server(let s, _, _) = self { return s }; return nil }

    public var errorDescription: String? {
        switch self {
        case .offline: return "You seem to be offline. Check your connection and try again."
        case .server(_, _, let message): return message
        case .decoding: return "The server answered in a way this version of the app does not understand."
        }
    }
}

private struct AnyEncodable: Encodable {
    let value: Encodable
    func encode(to encoder: Encoder) throws { try value.encode(to: encoder) }
}

/// One refresh at a time, however many requests got a 401 together.
private actor RefreshGate {
    private var inflight: Task<String?, Never>?
    func run(_ work: @escaping @Sendable () async -> String?) async -> String? {
        if let t = inflight { return await t.value }
        let t = Task { await work() }
        inflight = t
        let result = await t.value
        inflight = nil
        return result
    }
}

/// The backend's REST API. Adds the access token, refreshes it once on a 401 and retries, and turns errors into `APIError`.
public final class APIClient: @unchecked Sendable {
    public let baseURL: URL
    private let session: URLSession
    private let store: TokenStore
    private let gate = RefreshGate()
    /// Called when the refresh token itself is rejected: the session is over and the app returns to sign-in.
    public var onSignedOut: (@Sendable (String) -> Void)?
    /// Called with the new access token after a refresh, so the live connection can re-authenticate.
    public var onRefreshed: (@Sendable (String) -> Void)?

    public init(baseURL: URL, store: TokenStore, session: URLSession = .shared) {
        self.baseURL = baseURL
        self.store = store
        self.session = session
    }

    public var currentAccessToken: String? { store.load()?.access }

    // MARK: requests

    public func send<T: Decodable>(_ method: String, _ path: String, query: [(String, String?)] = [], body: Encodable? = nil, auth: Bool = true, headers: [String: String] = [:], as type: T.Type = T.self) async throws -> T {
        let data = try await raw(method, path, query: query, body: body, auth: auth, headers: headers)
        do { return try JSONDecoder().decode(T.self, from: data) }
        catch { throw APIError.decoding(String(describing: error)) }
    }

    public func sendVoid(_ method: String, _ path: String, query: [(String, String?)] = [], body: Encodable? = nil, auth: Bool = true, headers: [String: String] = [:]) async throws {
        _ = try await raw(method, path, query: query, body: body, auth: auth, headers: headers)
    }

    private func makeRequest(_ method: String, _ path: String, query: [(String, String?)], body: Encodable?, headers: [String: String], token: String?) throws -> URLRequest {
        var comps = URLComponents(url: baseURL.appendingPathComponent(path), resolvingAgainstBaseURL: false)!
        let items = query.compactMap { k, v in v.map { URLQueryItem(name: k, value: $0) } }
        if !items.isEmpty { comps.queryItems = items }
        var req = URLRequest(url: comps.url!)
        req.httpMethod = method
        req.timeoutInterval = 30
        req.setValue("application/json", forHTTPHeaderField: "Accept")
        if let body {
            req.httpBody = try JSONEncoder().encode(AnyEncodable(value: body))
            req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        } else if method != "GET" && method != "DELETE" {
            req.httpBody = Data("{}".utf8)
            req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        }
        for (k, v) in headers { req.setValue(v, forHTTPHeaderField: k) }
        if let token, !token.isEmpty { req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        return req
    }

    private func raw(_ method: String, _ path: String, query: [(String, String?)], body: Encodable?, auth: Bool, headers: [String: String]) async throws -> Data {
        var token = auth ? store.load()?.access : nil
        for attempt in 0..<2 {
            let req = try makeRequest(method, path, query: query, body: body, headers: headers, token: token)
            let data: Data, response: URLResponse
            do { (data, response) = try await session.data(for: req) }
            catch let e as URLError { throw APIClient.map(e) }
            guard let http = response as? HTTPURLResponse else { throw APIError.decoding("not an HTTP response") }
            if (200..<300).contains(http.statusCode) { return data }
            if http.statusCode == 401 && auth && attempt == 0, let fresh = await refresh(stale: token) {
                token = fresh
                continue
            }
            let env = try? JSONDecoder().decode(ErrorEnvelope.self, from: data)
            throw APIError.server(status: http.statusCode, code: env?.error.code, message: env?.error.message ?? "The server answered \(http.statusCode).")
        }
        throw APIError.server(status: 401, code: "UNAUTHORIZED", message: "Your session ended. Sign in again to continue.")
    }

    private static func map(_ e: URLError) -> APIError {
        switch e.code {
        case .notConnectedToInternet, .networkConnectionLost, .timedOut, .cannotFindHost, .cannotConnectToHost, .dnsLookupFailed, .dataNotAllowed:
            return .offline
        default:
            return .server(status: 0, code: nil, message: e.localizedDescription)
        }
    }

    // MARK: refreshing

    /// Returns a usable access token: another request may already have refreshed it. Offline keeps the session.
    @discardableResult
    public func refresh(stale: String?) async -> String? {
        await gate.run { [self] in
            guard let current = store.load() else { return nil }
            if let stale, current.access != stale, !current.access.isEmpty { return current.access }
            do {
                let pair: TokenPair = try await send("POST", "v1/auth/refresh", body: RefreshRequest(refreshToken: current.refresh), auth: false)
                store.save(Tokens(access: pair.accessToken, refresh: pair.refreshToken, userId: current.userId))
                onRefreshed?(pair.accessToken)
                return pair.accessToken
            } catch let e as APIError {
                if e.status == 401 {
                    store.save(nil)
                    onSignedOut?("Your session ended. Sign in again to continue.")
                }
                return nil
            } catch { return nil }
        }
    }

    public func saveSignIn(_ r: AuthResponse) {
        store.save(Tokens(access: r.tokens.accessToken, refresh: r.tokens.refreshToken, userId: r.user.id))
    }

    public func clearSignIn() { store.save(nil) }
    public var isSignedIn: Bool { store.load() != nil }
}
