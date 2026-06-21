import Foundation
import FirebaseFirestore
import SharedCore

enum ReportSubmissionFailureLogger {
    private static let collectionName = "report_submission_errors"
    private static let maxErrorMessageLength = 1_000
    private static let maxStackTraceLength = 8_000

    static func log(
        surface: String,
        stage: String,
        error: Error,
        plateRegion: String,
        complaintCount: Int,
        mediaCount: Int,
        hasVideo: Bool,
        reportCount: Int,
        session: UserSession? = nil,
        report: [String: Any]? = nil
    ) {
        var payload: [String: Any] = [
            "createdAt": FieldValue.serverTimestamp(),
            "clientCreatedAtMs": Int(Date().timeIntervalSince1970 * 1000),
            "platform": "ios",
            "appVersionName": Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "unknown",
            "appVersionCode": Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "unknown",
            "surface": clean(surface, maxLength: 96),
            "stage": clean(stage, maxLength: 96),
            "error": [
                "type": clean(String(describing: type(of: error)), maxLength: 200),
                "message": clean(String(describing: error), maxLength: maxErrorMessageLength),
                "localizedDescription": clean(error.localizedDescription, maxLength: maxErrorMessageLength),
                "stackTrace": clean(Thread.callStackSymbols.joined(separator: "\n"), maxLength: maxStackTraceLength)
            ],
            "summary": [
                "plateRegion": clean(plateRegion, maxLength: 16),
                "complaintCount": complaintCount,
                "mediaCount": mediaCount,
                "hasVideo": hasVideo,
                "reportCount": reportCount
            ]
        ]
        if let userId = userId(from: session) {
            payload["userId"] = userId
        }
        if let userEmail = userEmail(from: session) {
            payload["userEmail"] = userEmail
        }
        if let report {
            payload["report"] = report
        }

        Firestore.firestore()
            .collection(collectionName)
            .addDocument(data: payload) { firestoreError in
                if let firestoreError {
                    print("ReportedSubmitFailure: failed saving submit failure to Firestore \(firestoreError)")
                }
            }
    }

    static func reportPayload(
        plate: String,
        plateRegion: String,
        address: String,
        complaintIds: [String],
        timeOfIncidentIso: String?,
        latitude: Double?,
        longitude: Double?,
        description: String,
        notes: String,
        mediaUrlCount: Int = 0,
        mediaFileCount: Int = 0,
        hasVehicleImageDescription: Bool = false,
        vehicleColor: String? = nil,
        vehicleMake: String? = nil,
        vehicleModel: String? = nil
    ) -> [String: Any] {
        var payload: [String: Any] = [
            "plate": clean(plate, maxLength: 32),
            "plateRegion": clean(plateRegion, maxLength: 16),
            "address": clean(address, maxLength: 500),
            "complaintIds": complaintIds.map { clean($0, maxLength: 100) },
            "descriptionLength": description.count,
            "notesLength": notes.count,
            "mediaUrlCount": mediaUrlCount,
            "mediaFileCount": mediaFileCount,
            "hasVehicleImageDescription": hasVehicleImageDescription
        ]
        if let timeOfIncidentIso {
            payload["timeOfIncidentIso"] = clean(timeOfIncidentIso, maxLength: 64)
        }
        if let latitude {
            payload["latitude"] = latitude
        }
        if let longitude {
            payload["longitude"] = longitude
        }
        if let vehicleColor {
            payload["vehicleColor"] = clean(vehicleColor, maxLength: 64)
        }
        if let vehicleMake {
            payload["vehicleMake"] = clean(vehicleMake, maxLength: 64)
        }
        if let vehicleModel {
            payload["vehicleModel"] = clean(vehicleModel, maxLength: 64)
        }
        return payload
    }

    private static func userId(from session: UserSession?) -> String? {
        guard let session else { return nil }
        let objectId = session.objectId.trimmingCharacters(in: .whitespacesAndNewlines)
        if !objectId.isEmpty {
            return objectId
        }
        return session.id > 0 ? "\(session.id)" : nil
    }

    private static func userEmail(from session: UserSession?) -> String? {
        guard let session else { return nil }
        let email = session.email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        return email.isEmpty ? nil : clean(email, maxLength: 200)
    }

    private static func clean(_ value: String, maxLength: Int) -> String {
        let cleaned = value
            .replacingOccurrences(of: "\n", with: " ")
            .replacingOccurrences(of: "\r", with: " ")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        if cleaned.count <= maxLength {
            return cleaned
        }
        return String(cleaned.prefix(maxLength))
    }
}
