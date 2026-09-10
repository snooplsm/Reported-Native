import Foundation
import SharedCore
import UIKit

func makeComposerReportDraft(from state: ComposerState) -> ReportDraft {
    return ReportDraft(
        plate: state.plate,
        plateRegion: state.plateRegion,
        address: state.addressQuery.isEmpty ? state.address : state.addressQuery,
        description: cleanComposerDraftText(state.description),
        notes: cleanComposerDraftText(state.notes),
        complaintIds: state.selectedComplaintId.map { [$0] } ?? state.selectedComplaintIds,
        occurredAtIso: state.occurredAtIso,
        selectedComplaintId: state.selectedComplaintId,
        stage: state.stage == .verify ? "VERIFY" : "PICK_MEDIA",
        primaryMedia: state.primaryMedia.map { media in
            let fileURL = PersistentMediaStore.persistableURL(
                for: media.fileURL,
                displayName: media.displayName
            )
            return DraftMedia(
                uri: fileURL.absoluteString,
                displayName: media.displayName,
                mimeType: "",
                isVideo: media.isVideo
            )
        },
        extraMedia: state.extraMedia.map {
            let fileURL = PersistentMediaStore.persistableURL(
                for: $0.fileURL,
                displayName: $0.displayName
            )
            return DraftMedia(
                uri: fileURL.absoluteString,
                displayName: $0.displayName,
                mimeType: "",
                isVideo: $0.isVideo
            )
        },
        latitude: state.latitude.map { KotlinDouble(double: $0) },
        longitude: state.longitude.map { KotlinDouble(double: $0) },
        plateCandidates: state.plateCandidates.map {
            let videoFramePreviewURL = persistedVideoFramePreviewURL(for: $0)
            return DraftPlateCandidate(
                plate: $0.plate,
                confidence: Float($0.confidence),
                rawPlateText: $0.rawPlateText,
                wasPlateCorrected: $0.wasPlateCorrected,
                state: $0.state,
                stateConfidence: $0.stateConfidence.map { KotlinFloat(float: Float($0)) },
                plateType: $0.plateType,
                plateTypeLabel: $0.plateTypeLabel,
                focalPointX: nil,
                focalPointY: nil,
                boundsLeft: $0.bounds.map { KotlinFloat(float: Float($0.minX)) },
                boundsTop: $0.bounds.map { KotlinFloat(float: Float($0.minY)) },
                boundsRight: $0.bounds.map { KotlinFloat(float: Float($0.maxX)) },
                boundsBottom: $0.bounds.map { KotlinFloat(float: Float($0.maxY)) },
                rotationDegrees: 0,
                cornerPoints: $0.cornerPointFloats,
                sourceImageWidth: nil,
                sourceImageHeight: nil,
                thumbnailUri: nil,
                videoFramePreviewUri: videoFramePreviewURL?.absoluteString,
                videoFrameTimeMs: $0.videoFrameTimeSeconds.map { KotlinLong(longLong: Int64($0 * 1000)) }
            )
        },
        selectedPlateCandidate: state.selectedPlateCandidate,
        vehicleImageDescription: nil,
        vehicleColor: nil,
        vehicleMake: nil,
        vehicleModel: nil,
        philadelphiaMobilityAccessDetails: state.philadelphiaMobilityAccessDetails
    )
}

func cleanComposerDraftText(_ value: String) -> String {
    let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
    if trimmed.hasPrefix("ReportDraft(") || trimmed.contains("primaryMedia=DraftMedia(") {
        return ""
    }
    return value
}

func persistedVideoFramePreviewURL(for candidate: ComposerState.PlateCandidate) -> URL? {
    if let url = candidate.videoFramePreviewURL,
       FileManager.default.fileExists(atPath: url.path) {
        return url
    }
    guard let data = candidate.videoFramePreview?.jpegData(compressionQuality: 0.82) else {
        return nil
    }
    let timeMs = Int((candidate.videoFrameTimeSeconds ?? 0) * 1000)
    let name = "video-frame-\(candidate.plate)-\(timeMs)"
    return try? PersistentMediaStore.saveReplacing(data: data, preferredName: name, fileExtension: "jpg")
}

func composerDraftMediaURLs(from state: ComposerState) -> [URL] {
    let mediaURLs = [state.primaryMedia].compactMap { $0?.fileURL } + state.extraMedia.map(\.fileURL)
    let framePreviewURLs = state.plateCandidates.compactMap { persistedVideoFramePreviewURL(for: $0) }
    return mediaURLs + framePreviewURLs
}

func submittablePhiladelphiaMobilityAccessDetails(
    for state: ComposerState
) -> PhiladelphiaMobilityAccessDetails? {
    let submitAddress = state.addressQuery.isEmpty ? state.address : state.addressQuery
    let isPhiladelphia = reportAddressProvider(
        latitude: state.latitude,
        longitude: state.longitude,
        address: submitAddress
    ) == .philadelphia
    return isPhiladelphia ? state.philadelphiaMobilityAccessDetails : nil
}

func uploadComposerMediaForSubmission(
    _ submittedMedia: [ComposerState.SubmissionMedia],
    isPhiladelphiaSubmission: Bool,
    philadelphiaMediaCount: Int,
    videoMessage: String,
    mediaMessage: String,
    onProgress: @escaping (Double, String) async -> Void
) async throws -> [SubmitReportMediaFile] {
    if isPhiladelphiaSubmission {
        if submittedMedia.contains(where: { $0.isVideo }) {
            throw ReportedSubmissionValidationError(message: videoMessage)
        }
        if submittedMedia.count > philadelphiaMediaCount {
            throw ReportedSubmissionValidationError(message: mediaMessage)
        }
        return try await uploadComposerParseMedia(
            submittedMedia,
            messagePrefix: "Uploading Philadelphia photos",
            onProgress: onProgress
        )
    }
    return try await uploadComposerParseMedia(
        submittedMedia,
        messagePrefix: "Uploading media",
        onProgress: onProgress
    )
}

private func uploadComposerParseMedia(
    _ submittedMedia: [ComposerState.SubmissionMedia],
    messagePrefix: String,
    onProgress: @escaping (Double, String) async -> Void
) async throws -> [SubmitReportMediaFile] {
    try await ParseMediaUploader.uploadAll(submittedMedia) { progress in
        await onProgress(
            min(max(progress.overallFraction * 0.82, 0), 0.82),
            "\(messagePrefix) (\(Int(progress.fileFraction * 100))%)"
        )
    }
}
