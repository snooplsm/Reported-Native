import FirebaseAnalytics
import FirebaseCore
import SwiftUI

@main
struct ReportediOSApp: App {
    @Environment(\.scenePhase) private var scenePhase

    init() {
        FirebaseApp.configure()
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
