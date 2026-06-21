import Foundation
import Network
import SharedCore

final class IOSVehicleEnrichmentTracker: NSObject, VehicleEnrichmentTracker {
    func endpointCompleted(
        provider: String,
        success: Bool,
        reason: String,
        durationMillis: Int64,
        operatingSystem: String
    ) {
        ReportedAnalytics.logVehicleEnrichmentEndpoint(
            provider: provider,
            success: success,
            reason: reason,
            durationMillis: durationMillis,
            operatingSystem: operatingSystem
        )
    }

    func classificationCompleted(
        surface: String,
        stage: String,
        success: Bool,
        reason: String,
        durationMillis: Int64,
        platePrefix: String,
        hasVin: Bool,
        hasDecodedVin: Bool,
        operatingSystem: String
    ) {
        ReportedAnalytics.logVehicleClassificationResult(
            surface: surface,
            stage: stage,
            success: success,
            reason: reason,
            durationMillis: durationMillis,
            platePrefix: platePrefix,
            hasVin: hasVin,
            hasDecodedVin: hasDecodedVin,
            operatingSystem: operatingSystem
        )
    }
}

final class IOSVehicleEnrichmentPolicy: NSObject, VehicleEnrichmentPolicy {
    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "reported.vehicle-enrichment-network")
    private let lock = NSLock()
    private var latestPath: NWPath?

    override init() {
        super.init()
        monitor.pathUpdateHandler = { [weak self] path in
            guard let self else { return }
            lock.lock()
            latestPath = path
            lock.unlock()
        }
        monitor.start(queue: queue)
    }

    func canAttemptVehicleEnrichment() -> Bool {
        lock.lock()
        let path = latestPath
        lock.unlock()
        let resolvedPath = path ?? monitor.currentPath
        return resolvedPath.status == .satisfied && resolvedPath.usesInterfaceType(.wifi)
    }

    func includeDebugSummaryInNotes() -> Bool {
        #if DEBUG
        return true
        #else
        return false
        #endif
    }

    deinit {
        monitor.cancel()
    }
}

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
        ),
        vehicleEnrichmentTracker: IOSVehicleEnrichmentTracker(),
        vehicleEnrichmentPolicy: IOSVehicleEnrichmentPolicy()
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
        ),
        vehicleEnrichmentTracker: IOSVehicleEnrichmentTracker(),
        vehicleEnrichmentPolicy: IOSVehicleEnrichmentPolicy()
    )
    #endif

    private init() {}
}
