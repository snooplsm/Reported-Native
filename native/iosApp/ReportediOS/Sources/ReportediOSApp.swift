import FirebaseAnalytics
import FirebaseCore
import SwiftUI

@main
struct ReportediOSApp: App {
    init() {
        FirebaseApp.configure()
        Analytics.logEvent(AnalyticsEventAppOpen, parameters: nil)
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
