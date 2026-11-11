import SwiftUI
import UserNotifications
import LocanaKit

extension Notification.Name { static let locanaOpen = Notification.Name("locana.open") }

final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async -> UNNotificationPresentationOptions { [.banner, .sound] }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        if let route = response.notification.request.content.userInfo["route"] as? String {
            await MainActor.run { NotificationCenter.default.post(name: .locanaOpen, object: route) }
        }
    }
}

@main
struct LocanaApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var delegate
    @StateObject private var model = AppModel()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(model)
                .tint(Palette.primary)
                .task { await model.bootstrap() }
                .onReceive(NotificationCenter.default.publisher(for: .locanaOpen)) { n in
                    if let kind = n.object as? String { model.open(kind: kind, data: nil) }
                }
        }
    }
}
