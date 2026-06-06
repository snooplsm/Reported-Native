import FirebaseAnalytics
import FirebaseCore
import FirebaseCrashlytics
import SwiftUI

@main
struct ReportediOSApp: App {
    @Environment(\.scenePhase) private var scenePhase

    init() {
        FirebaseApp.configure()
        Crashlytics.crashlytics().setCustomValue(Bundle.main.bundleIdentifier ?? "unknown", forKey: "bundle_id")
        Analytics.logEvent(AnalyticsEventAppOpen, parameters: nil)
        IOSMediaScanner.shared.start()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .onChange(of: scenePhase) { _, phase in
                    if phase == .background {
                        IOSMediaScanner.shared.scanWhileBackgrounded()
                    }
                }
        }
    }
}
