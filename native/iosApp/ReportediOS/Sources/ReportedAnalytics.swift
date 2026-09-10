import Foundation
import FirebaseAnalytics
import FirebaseCrashlytics
import ImageIO
import SharedCore
import UIKit
import UniformTypeIdentifiers

enum ReportAddressProvider {
    case newYorkCity
    case philadelphia

    var id: String {
        switch self {
        case .newYorkCity:
            "nyc_geosearch"
        case .philadelphia:
            "philadelphia_ais"
        }
    }

    var searchingLabel: String {
        switch self {
        case .newYorkCity:
            "Searching NYC addresses"
        case .philadelphia:
            "Searching Philadelphia addresses"
        }
    }

    var noMatchesLabel: String {
        switch self {
        case .newYorkCity:
            "No NYC address matches found."
        case .philadelphia:
            "No Philadelphia address matches found."
        }
    }

    var reverseLookupLabel: String {
        switch self {
        case .newYorkCity:
            "Finding NYC address"
        case .philadelphia:
            "Finding Philadelphia address"
        }
    }
}

extension String {
    var nilIfBlank: String? {
        let trimmed = trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}

enum ReportedAnalytics {
    static func setUser(_ session: UserSession?) {
        let userId = userId(from: session)
        let email = email(from: session)
        let emailDomain = emailDomain(from: session)
        Analytics.setUserID(userId)
        Analytics.setUserProperty(userId, forName: "reported_user_id")
        Analytics.setUserProperty(emailDomain, forName: "email_domain")

        let crashlytics = Crashlytics.crashlytics()
        crashlytics.setUserID(userId ?? "")
        crashlytics.setCustomValue(userId ?? "", forKey: "reported_user_id")
        crashlytics.setCustomValue(email ?? "", forKey: "reported_user_email")
        crashlytics.setCustomValue(emailDomain ?? "", forKey: "email_domain")
    }

    static func logLogin(method: String, session: UserSession?) {
        setUser(session)
        Analytics.logEvent(AnalyticsEventLogin, parameters: [
            AnalyticsParameterMethod: method
        ])
    }

    static func logLogout() {
        Analytics.logEvent("logout", parameters: nil)
        setUser(nil)
    }

    static func logReportsList() {
        Analytics.logEvent("reports_list", parameters: nil)
    }

    static func logReportsSearch(hasLicense: Bool, hasStartDate: Bool, hasEndDate: Bool) {
        Analytics.logEvent("reports_search", parameters: [
            "has_license": hasLicense ? 1 : 0,
            "has_start_date": hasStartDate ? 1 : 0,
            "has_end_date": hasEndDate ? 1 : 0
        ])
    }

    static func logSubmitReport(
        county: String,
        plateRegion: String,
        complaintCount: Int,
        mediaCount: Int,
        hasVideo: Bool,
        mediaToSubmitMillis: Int64? = nil
    ) {
        var parameters: [String: Any] = [
            "county": county,
            "plate_region": plateRegion,
            "complaint_count": complaintCount,
            "media_count": mediaCount,
            "has_video": hasVideo ? 1 : 0
        ]
        if let mediaToSubmitMillis {
            parameters["media_to_submit_ms"] = mediaToSubmitMillis
            parameters["media_to_submit_sec"] = Double(mediaToSubmitMillis) / 1000.0
        }
        Analytics.logEvent("submit_report", parameters: parameters)
    }

    static func logVehicleEnrichmentEndpoint(
        provider: String,
        success: Bool,
        reason: String,
        durationMillis: Int64,
        operatingSystem: String
    ) {
        Analytics.logEvent("vehicle_enrichment_endpoint", parameters: [
            "provider": firebaseSafeAnalyticsValue(provider),
            "success": success ? 1 : 0,
            "reason": firebaseSafeAnalyticsValue(reason),
            "duration_ms": durationMillis,
            "operating_system": firebaseSafeAnalyticsValue(operatingSystem)
        ])
    }

    static func logVehicleClassificationResult(
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
        Analytics.logEvent("vehicle_classification_result", parameters: [
            "surface": firebaseSafeAnalyticsValue(surface),
            "stage": firebaseSafeAnalyticsValue(stage),
            "success": success ? 1 : 0,
            "reason": firebaseSafeAnalyticsValue(reason),
            "duration_ms": durationMillis,
            "plate_prefix": firebaseSafeAnalyticsValue(platePrefix),
            "has_vin": hasVin ? 1 : 0,
            "has_decoded_vin": hasDecodedVin ? 1 : 0,
            "operating_system": firebaseSafeAnalyticsValue(operatingSystem)
        ])
    }

    static func logSubmitReportFailed(
        surface: String,
        stage: String,
        error: Error,
        plateRegion: String,
        complaintCount: Int,
        mediaCount: Int,
        hasVideo: Bool,
        reportCount: Int = 1,
        session: UserSession? = nil,
        report: [String: Any]? = nil
    ) {
        Analytics.logEvent("submit_report_failed", parameters: [
            "surface": firebaseSafeAnalyticsValue(surface),
            "stage": firebaseSafeAnalyticsValue(stage),
            "error_type": firebaseSafeAnalyticsValue(String(describing: type(of: error))),
            "error_message": firebaseSafeAnalyticsValue(String(describing: error)),
            "plate_region": firebaseSafeAnalyticsValue(plateRegion),
            "complaint_count": complaintCount,
            "media_count": mediaCount,
            "has_video": hasVideo ? 1 : 0,
            "report_count": reportCount
        ])
        ReportSubmissionFailureLogger.log(
            surface: surface,
            stage: stage,
            error: error,
            plateRegion: plateRegion,
            complaintCount: complaintCount,
            mediaCount: mediaCount,
            hasVideo: hasVideo,
            reportCount: reportCount,
            session: session,
            report: report
        )
    }

    static func userFacingSubmitFailureMessage(
        for error: Error,
        fallback: String = "There was an error submitting your report. Please try again."
    ) -> String {
        let candidates = [String(describing: error), error.localizedDescription]
        for candidate in candidates {
            if let message = duplicateSubmitMessage(from: candidate) {
                return message
            }
        }
        return fallback
    }

    private static func duplicateSubmitMessage(from value: String) -> String? {
        guard value.range(of: "already been submitted", options: .caseInsensitive) != nil else {
            return nil
        }
        if let start = value.range(of: "A report for", options: .caseInsensitive)?.lowerBound {
            var message = String(value[start...])
            if let end = message.firstIndex(of: ".") {
                message = String(message[...end])
            }
            return message.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        return "A report for this plate and state has already been submitted."
    }

    static func logSubmitReportTapped(stage: String, isAuthorized: Bool, mediaToSubmitMillis: Int64? = nil) {
        var parameters: [String: Any] = [
            "stage": stage,
            "is_authorized": isAuthorized ? 1 : 0
        ]
        if let mediaToSubmitMillis {
            parameters["media_to_submit_ms"] = mediaToSubmitMillis
            parameters["media_to_submit_sec"] = Double(mediaToSubmitMillis) / 1000.0
        }
        Analytics.logEvent("submit_report_tap", parameters: parameters)
    }

    static func logReportMediaAdded(surface: String, mediaCount: Int, hasVideo: Bool) {
        Analytics.logEvent("report_media_added", parameters: [
            "surface": firebaseSafeAnalyticsValue(surface),
            "media_count": mediaCount,
            "has_video": hasVideo ? 1 : 0
        ])
    }

    static func logAiSparkleTapped(surface: String) {
        Analytics.logEvent("ai_sparkle_tap", parameters: [
            "surface": surface
        ])
    }

    static func logReportedAiBulkSubmit(
        surface: String,
        reportCount: Int,
        mediaCount: Int,
        complaintCount: Int,
        scanToSubmitMillis: Int64? = nil
    ) {
        var parameters: [String: Any] = [
            "surface": surface,
            "report_count": reportCount,
            "media_count": mediaCount,
            "complaint_count": complaintCount
        ]
        if let scanToSubmitMillis {
            parameters["scan_to_submit_ms"] = scanToSubmitMillis
            parameters["scan_to_submit_sec"] = Double(scanToSubmitMillis) / 1000.0
        }
        Analytics.logEvent("reported_ai_bulk_submit", parameters: parameters)
    }

    static func logAutoReportScanStarted(scanWindow: String) {
        Analytics.logEvent("auto_report_scan_start", parameters: [
            "scan_window": firebaseSafeAnalyticsValue(scanWindow)
        ])
    }

    static func logAutoReportSummary(
        count: Int,
        keptCount: Int,
        discardedCount: Int,
        invalidCount: Int,
        mediaCount: Int,
        processedPhotoCount: Int
    ) {
        Analytics.logEvent("auto_report_summary", parameters: [
            "count": count,
            "report_count": count,
            "kept_count": keptCount,
            "discarded_count": discardedCount,
            "invalid_count": invalidCount,
            "media_count": mediaCount,
            "processed_photo_count": processedPhotoCount
        ])
    }

    static func logAutoReportDecision(
        keep: Bool,
        reportIndex: Int,
        reportCount: Int,
        keptCount: Int,
        mediaCount: Int
    ) {
        Analytics.logEvent(keep ? "auto_report_keep" : "auto_report_discard", parameters: [
            "report_index": reportIndex,
            "report_count": reportCount,
            "kept_count": keptCount,
            "media_count": mediaCount
        ])
    }

    static func logAutoReportSummarySubmitTapped(
        reportCount: Int,
        mediaCount: Int,
        complaintCount: Int,
        invalidCount: Int,
        isAuthorized: Bool,
        scanToSubmitMillis: Int64? = nil
    ) {
        var parameters: [String: Any] = [
            "count": reportCount,
            "report_count": reportCount,
            "media_count": mediaCount,
            "complaint_count": complaintCount,
            "invalid_count": invalidCount,
            "is_authorized": isAuthorized ? 1 : 0
        ]
        if let scanToSubmitMillis {
            parameters["scan_to_submit_ms"] = scanToSubmitMillis
            parameters["scan_to_submit_sec"] = Double(scanToSubmitMillis) / 1000.0
        }
        Analytics.logEvent("auto_report_summary_submit_tap", parameters: parameters)
    }

    static func logPlateChooserTapped(candidateCount: Int, hasPlate: Bool) {
        Analytics.logEvent("plate_chooser_tap", parameters: [
            "candidate_count": candidateCount,
            "has_plate": hasPlate ? 1 : 0
        ])
    }

    static func logStateChooserTapped(currentRegion: String) {
        Analytics.logEvent("state_chooser_tap", parameters: [
            "current_state": currentRegion
        ])
    }

    static func logStateSelected(region: String) {
        Analytics.logEvent("state_selected", parameters: [
            "plate_region": region
        ])
    }

    static func logComplaintChooserTapped(surface: String, selectedComplaintId: String?) {
        var parameters: [String: Any] = [
            "surface": surface
        ]
        if let selectedComplaintId, !selectedComplaintId.isEmpty {
            parameters["complaint_id"] = selectedComplaintId
        }
        Analytics.logEvent("complaint_chooser_tap", parameters: parameters)
    }

    static func logComplaintSelected(complaintId: String, surface: String) {
        Analytics.logEvent("complaint_selected", parameters: [
            "complaint_id": complaintId,
            "surface": surface
        ])
    }

    static func logSettingsTapped(surface: String) {
        Analytics.logEvent("settings_tap", parameters: [
            "surface": surface
        ])
    }

    static func logBuyMeCoffeeTapped(surface: String) {
        Analytics.logEvent("buy_me_coffee_tap", parameters: [
            "surface": firebaseSafeAnalyticsValue(surface)
        ])
    }

    static func logBuyMeCoffeeInfoTapped(surface: String) {
        Analytics.logEvent("buy_me_coffee_info_tap", parameters: [
            "surface": firebaseSafeAnalyticsValue(surface)
        ])
    }

    static func logBuyMeCoffeeOpen(surface: String, source: String) {
        Analytics.logEvent("buy_me_coffee_open", parameters: [
            "surface": firebaseSafeAnalyticsValue(surface),
            "source": firebaseSafeAnalyticsValue(source)
        ])
    }

    static func logAlprResultSelected(
        plateRegion: String?,
        candidateCount: Int,
        selectedRank: Int,
        confidence: Double?,
        wasCorrected: Bool
    ) {
        var parameters: [String: Any] = [
            "plate_region": plateRegion ?? "unknown",
            "candidate_count": candidateCount,
            "selected_rank": selectedRank,
            "was_corrected": wasCorrected ? 1 : 0
        ]
        if let confidence {
            parameters["confidence"] = confidence
        }
        Analytics.logEvent("alpr_result_selected", parameters: parameters)
    }

    static func county(from address: String) -> String {
        let normalized = address.lowercased()
        if normalized.contains("manhattan") ||
            normalized.contains("new york, ny") ||
            normalized.contains("new york ny") {
            return "New York County"
        }
        if normalized.contains("brooklyn") ||
            normalized.contains("kings county") {
            return "Kings County"
        }
        if normalized.contains("queens") {
            return "Queens County"
        }
        if normalized.contains("bronx") {
            return "Bronx County"
        }
        if normalized.contains("staten island") ||
            normalized.contains("richmond county") {
            return "Richmond County"
        }
        if normalized.contains("philadelphia") ||
            normalized.contains("philly") {
            return "Philadelphia County"
        }
        return "unknown"
    }

    private static func userId(from session: UserSession?) -> String? {
        guard let session else { return nil }
        let objectId = session.objectId.trimmingCharacters(in: .whitespacesAndNewlines)
        if !objectId.isEmpty {
            return objectId
        }
        return session.id > 0 ? "\(session.id)" : nil
    }

    private static func emailDomain(from session: UserSession?) -> String? {
        guard let email = email(from: session),
              let atIndex = email.lastIndex(of: "@") else { return nil }
        let domain = email[email.index(after: atIndex)...]
            .lowercased()
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return domain.isEmpty ? nil : firebaseSafeAnalyticsValue(domain)
    }

    private static func email(from session: UserSession?) -> String? {
        guard let session else { return nil }
        let email = session.email.trimmingCharacters(in: .whitespacesAndNewlines)
        return email.isEmpty ? nil : firebaseSafeAnalyticsValue(email.lowercased())
    }

    private static func firebaseSafeAnalyticsValue(_ value: String) -> String {
        let trimmed = value
            .replacingOccurrences(of: "\n", with: " ")
            .replacingOccurrences(of: "\r", with: " ")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.count <= 96 {
            return trimmed
        }
        return String(trimmed.prefix(96))
    }
}
