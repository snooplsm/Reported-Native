import Foundation
import CryptoKit
import ImageIO
import Photos
import SharedCore
import UIKit
import UniformTypeIdentifiers
import UserNotifications

func detectedInfractionId(plate: String, state: String, occurredAtIso: String) -> String {
    let raw = "\(plate)-\(state)-\(occurredAtIso)"
    let allowed = CharacterSet.alphanumerics.union(CharacterSet(charactersIn: "-_"))
    return String(raw.unicodeScalars.map { allowed.contains($0) ? Character($0) : "-" })
}

func scannerIsoStringUTC(from date: Date) -> String {
    let formatter = ISO8601DateFormatter()
    formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    formatter.timeZone = TimeZone(secondsFromGMT: 0)
    return formatter.string(from: date)
}

func notificationComplaintLabel(for complaintId: String) -> String {
    switch complaintId {
    case "Z8vjWz8uYr": return "Blocked bike lane"
    case "GzRxlMN1vl": return "Blocked crosswalk"
    default: return "Infraction"
    }
}

let autoReportComplaintIds: Set<String> = ["Z8vjWz8uYr", "GzRxlMN1vl"]

var autoReportConfidenceThresholdSummary: String {
    "Thresholds: plate \(scannerConfidencePercent(IOSMediaScannerSettings.autoReportPlateConfidenceThreshold))+, state \(scannerConfidencePercent(IOSMediaScannerSettings.autoReportStateConfidenceThreshold))+, infraction \(scannerConfidencePercent(IOSMediaScannerSettings.autoReportComplaintConfidenceThreshold))+."
}

func ifLet<A, B, T>(_ a: A?, _ b: B?, transform: (A, B) async -> T?) async -> T? {
    guard let a, let b else { return nil }
    return await transform(a, b)
}

extension Array where Element == ComposerState.PlateCandidate {
    func bestMediaScannerCandidate() -> ComposerState.PlateCandidate? {
        filter { candidate in
            candidate.confidence >= candidate.mediaScannerPlateThreshold &&
                (candidate.stateConfidence ?? 0) >= IOSMediaScannerSettings.autoReportStateConfidenceThreshold &&
                candidate.state?.isEmpty == false
        }
        .max { lhs, rhs in
            lhs.mediaScannerScore < rhs.mediaScannerScore
        }
    }

    func bestObservedMediaScannerCandidate() -> ComposerState.PlateCandidate? {
        self.max { lhs, rhs in
            lhs.mediaScannerScore < rhs.mediaScannerScore
        }
    }
}

extension ComposerState.PlateCandidate {
    var hasPostInferredNyForHirePlate: Bool {
        state == "NY" && (plateType == "TAXI" || plateType == "TLC")
    }

    var mediaScannerPlateThreshold: Double {
        hasPostInferredNyForHirePlate ? IOSMediaScannerSettings.autoReportPostInferencePlateConfidenceThreshold : IOSMediaScannerSettings.autoReportPlateConfidenceThreshold
    }

    var mediaScannerScore: Double {
        confidence + (stateConfidence ?? 0)
    }

    var mediaScannerConfidenceSummary: String {
        "\(plate) \(state ?? "") plate \(scannerConfidencePercent(confidence)) (needs \(scannerConfidencePercent(mediaScannerPlateThreshold))), state \(scannerConfidencePercent(stateConfidence ?? 0)) (needs \(scannerConfidencePercent(IOSMediaScannerSettings.autoReportStateConfidenceThreshold)))"
    }
}

extension Optional where Wrapped == ComplaintInferenceResult {
    var acceptedAutoReportComplaint: Bool {
        guard let result = self else { return false }
        return Double(result.confidence) >= IOSMediaScannerSettings.autoReportComplaintConfidenceThreshold &&
            autoReportComplaintIds.contains(result.complaintId)
    }

    func autoReportComplaintConfidenceSummary(matched: Bool) -> String {
        let required = scannerConfidencePercent(IOSMediaScannerSettings.autoReportComplaintConfidenceThreshold)
        guard let result = self else {
            return "Reported infraction model returned no blocked bike lane/crosswalk detection score (needs \(required))."
        }
        let label = notificationComplaintLabel(for: result.complaintId)
        let confidence = scannerConfidencePercent(Double(result.confidence))
        if matched {
            return "Reported infraction model: \(label) \(confidence) (needs \(required)), queued this photo for bulk review."
        }
        return "Reported infraction model top score: \(label) \(confidence) (needs \(required)), below Auto-Report threshold."
    }
}

func scannerConfidencePercent(_ value: Double) -> String {
    let bounded = min(0.999, max(0, value))
    if bounded > 0, bounded < 0.01 {
        return "<1%"
    }
    return "\(Int(bounded * 100))%"
}

extension String {
    var detectedNotificationTime: String {
        let isoFormatter = ISO8601DateFormatter()
        isoFormatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let fallbackIsoFormatter = ISO8601DateFormatter()
        fallbackIsoFormatter.formatOptions = [.withInternetDateTime]
        guard let date = isoFormatter.date(from: self) ?? fallbackIsoFormatter.date(from: self) else {
            return self
        }
        return date.formatted(date: .abbreviated, time: .shortened)
    }
}
