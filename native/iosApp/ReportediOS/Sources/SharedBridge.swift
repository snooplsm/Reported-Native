import Foundation
import SharedCore

@MainActor
final class SharedBridge {
    static let shared = SharedBridge()
    #if DEBUG
    let container = ReportedShared(
        config: ReportedConfig(
            environment: .development,
            apiBaseUrl: ProcessInfo.processInfo.environment["REPORTED_API_BASE_URL"] ?? "https://reported-stats.herokuapp.com/prod/",
            parse: ParseConfig(
                serverUrl: ProcessInfo.processInfo.environment["REPORTED_PARSE_SERVER_URL"] ?? "https://parseapi.back4app.com",
                applicationId: ProcessInfo.processInfo.environment["REPORTED_PARSE_APPLICATION_ID"] ?? "jkAZF8ojV4vOGnhSBjdwiMWBKpWML5tM4SWGKgOV",
                javascriptKey: ProcessInfo.processInfo.environment["REPORTED_PARSE_JAVASCRIPT_KEY"] ?? "LeBKOerWTXGBGRLE0yvg2bXa5RRv4e8PuC6INEFA"
            ),
            operatingSystem: "native-ios"
        )
    )
    #else
    let container = ReportedShared(
        config: ReportedConfig(
            environment: .production,
            apiBaseUrl: ProcessInfo.processInfo.environment["REPORTED_API_BASE_URL"] ?? "https://reported-stats.herokuapp.com/prod/",
            parse: ParseConfig(
                serverUrl: ProcessInfo.processInfo.environment["REPORTED_PARSE_SERVER_URL"] ?? "https://parseapi.back4app.com",
                applicationId: ProcessInfo.processInfo.environment["REPORTED_PARSE_APPLICATION_ID"] ?? "jkAZF8ojV4vOGnhSBjdwiMWBKpWML5tM4SWGKgOV",
                javascriptKey: ProcessInfo.processInfo.environment["REPORTED_PARSE_JAVASCRIPT_KEY"] ?? "LeBKOerWTXGBGRLE0yvg2bXa5RRv4e8PuC6INEFA"
            ),
            operatingSystem: "native-ios"
        )
    )
    #endif

    private init() {}
}
