import FirebaseRemoteConfig
import Foundation
import SharedCore

extension Notification.Name {
    static let reportedRemoteConfigUpdated = Notification.Name("reportedRemoteConfigUpdated")
    static let reportedDetectedInfractionEditRequested = Notification.Name("reportedDetectedInfractionEditRequested")
}

@MainActor
final class ReportedRemoteConfigService {
    static let shared = ReportedRemoteConfigService()

    private let remoteConfig = RemoteConfig.remoteConfig()
    private var configured = false

    private init() {}

    func configureAndFetch() async {
        configureIfNeeded()
        applyCurrentValues()
        do {
            _ = try await remoteConfig.fetchAndActivate()
            applyCurrentValues()
        } catch {
            applyCurrentValues()
        }
    }

    private func configureIfNeeded() {
        guard !configured else { return }
        let settings = RemoteConfigSettings()
        #if DEBUG
        settings.minimumFetchInterval = 0
        #else
        settings.minimumFetchInterval = 3600
        #endif
        remoteConfig.configSettings = settings
        remoteConfig.setDefaults(defaults().mapValues { $0 as! NSObject })
        configured = true
    }

    private func applyCurrentValues() {
        RemoteConfigOverrides.shared.apply(
            apiBaseUrl: stringValue("reported_api_base_url"),
            parseServerUrl: stringValue("reported_parse_server_url"),
            complaintCategoriesJson: stringValue("reported_complaint_categories"),
            reportStatusesJson: stringValue("reported_report_statuses"),
            showComplaintImages: remoteConfig.configValue(forKey: "reported_show_complaint_images").boolValue.description,
            enableLive: remoteConfig.configValue(forKey: "reported_enable_live").boolValue.description,
            enableMediaScanner: remoteConfig.configValue(forKey: "reported_enable_media_scanner").boolValue.description,
            enableOfflinePhotoProcessing: remoteConfig.configValue(forKey: "reported_enable_offline_photo_processing").boolValue.description
        )
        NotificationCenter.default.post(name: .reportedRemoteConfigUpdated, object: nil)
    }

    private func stringValue(_ key: String) -> String {
        remoteConfig.configValue(forKey: key).stringValue ?? ""
    }

    private func defaults() -> [String: Any] {
        [
            "reported_api_base_url": ProcessInfo.processInfo.environment["REPORTED_API_BASE_URL"] ?? "https://reported-stats.herokuapp.com/prod/",
            "reported_parse_server_url": ProcessInfo.processInfo.environment["REPORTED_PARSE_SERVER_URL"] ?? "https://parseapi.back4app.com",
            "reported_complaint_categories": RemoteConfigDefaults.shared.complaintCategoriesJson,
            "reported_report_statuses": RemoteConfigDefaults.shared.reportStatusesJson,
            "reported_show_complaint_images": true,
            "reported_enable_live": false,
            "reported_enable_media_scanner": false,
            "reported_enable_offline_photo_processing": false
        ]
    }
}
