import Foundation
import Security
import LocanaKit

/// The sign-in lives in the Keychain, readable after the first unlock so background uploads keep working.
final class KeychainTokenStore: TokenStore {
    private let service = "app.locana.ios.session"
    private let account = "tokens"
    private let lock = NSLock()

    private var query: [String: Any] {
        [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service, kSecAttrAccount as String: account]
    }

    func load() -> Tokens? {
        lock.lock(); defer { lock.unlock() }
        var q = query
        q[kSecReturnData as String] = true
        q[kSecMatchLimit as String] = kSecMatchLimitOne
        var out: AnyObject?
        guard SecItemCopyMatching(q as CFDictionary, &out) == errSecSuccess, let data = out as? Data else { return nil }
        return try? JSONDecoder().decode(Tokens.self, from: data)
    }

    func save(_ tokens: Tokens?) {
        lock.lock(); defer { lock.unlock() }
        SecItemDelete(query as CFDictionary)
        guard let tokens, let data = try? JSONEncoder().encode(tokens) else { return }
        var q = query
        q[kSecValueData as String] = data
        q[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
        SecItemAdd(q as CFDictionary, nil)
    }
}
