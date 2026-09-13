import Foundation
import FirebaseAnalytics
import FirebaseCrashlytics
import ImageIO
import SharedCore
import UIKit
import UniformTypeIdentifiers

private let maxLicensePlateInputLength = 10

final class ComposerViewModel: ObservableObject, UdfStore {
    @Published private(set) var state = ComposerState()
    @Published private(set) var submittedReportObjectId: String?
    private lazy var vehicleLookupCoordinator = ComposerVehicleLookupCoordinator(
        currentState: { [weak self] in self?.state ?? ComposerState() },
        updateState: { [weak self] mutation in
            guard let self else { return }
            mutation(&self.state)
        },
        onPersistDraft: { [weak self] in self?.persistDraft() }
    )
    private var remoteConfigObserver: NSObjectProtocol?
    private let maxSubmissionMediaCount = 3
    private let maxSubmissionVideoCount = 1
    private let philadelphiaSubmissionMediaCount = 2
    private let maxSubmissionMediaMessage = "You can attach up to 3 photos or videos."
    private let maxSubmissionVideoMessage = "You can attach no more than 1 video."
    private let philadelphiaSubmissionMediaMessage = "Philadelphia Parking Authority reports can include up to 2 photos and no videos."
    private let philadelphiaSubmissionVideoMessage = "Philadelphia Parking Authority reports do not accept videos. Add up to 2 JPG or PNG photos instead."
    private let duplicateSubmissionMediaMessage = "That photo or video is already attached."

    var remainingMediaSlots: Int {
        max(0, (state.isPhiladelphiaSubmission ? philadelphiaSubmissionMediaCount : maxSubmissionMediaCount) - mediaItems.count)
    }

    init() {
        remoteConfigObserver = NotificationCenter.default.addObserver(
            forName: .reportedRemoteConfigUpdated,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor [weak self] in
                self?.refreshRemoteConfigValues()
            }
        }
    }

    deinit {
        if let remoteConfigObserver {
            NotificationCenter.default.removeObserver(remoteConfigObserver)
        }
    }

    func refreshRemoteConfigValues() {
        state.complaintCategories = Array(Catalogs.shared.complaintCategories)
        state.showComplaintImages = RemoteConfigOverrides.shared.showComplaintImages
    }

    @discardableResult
    func onAction(_ action: ComposerAction) -> Bool {
        switch action {
        case .loadDraft:
            loadDraft()
        case .reloadDraft:
            reloadDraft()
        case .submitValidationRequested:
            return prepareSubmit()
        case .submitPressed:
            submit()
        case .clearDraft:
            clearDraft()
        case .submittedReportConsumed:
            consumeSubmittedReport()
        case .mediaLimitReached:
            markMediaLimitReached()
        case .draftPersistenceRequested:
            persistCurrentDraft()
        case .complaintTileChosen(let complaintId):
            onComplaintTileChosen(complaintId)
        case .selectedComplaintChanged(let complaintId):
            updateSelectedComplaint(complaintId)
        case .uploadMediaChosen(let media):
            onUploadMediaChosen(media)
        case .primaryMediaChosen(let media, let complaintId):
            onPrimaryMediaChosen(media, complaintId: complaintId)
        case .pendingComplaintConfirmed(let complaintId):
            confirmPendingComplaint(complaintId)
        case .extraMediaAdded(let media):
            return addExtraMedia(media)
        case .mediaRemoved(let media):
            removeMedia(media)
        case .mediaRejected(let media, let message):
            rejectMedia(media, message: message)
        case .videoProcessingDecision(let process):
            onVideoProcessingDecision(process)
        case .videoProcessingCancelled:
            cancelVideoProcessing()
        case .detectionProgressChanged(let message, let progress, let framePreview, let frameTimeSeconds, let videoDurationSeconds, let frameCandidates, let allCandidates):
            setDetectionProgress(
                message: message,
                progress: progress,
                framePreview: framePreview,
                frameTimeSeconds: frameTimeSeconds,
                videoDurationSeconds: videoDurationSeconds,
                frameCandidates: frameCandidates,
                allCandidates: allCandidates
            )
        case .detectionFinished(let candidates, let inferredPlate, let inferredState):
            finishDetection(candidates: candidates, inferredPlate: inferredPlate, inferredState: inferredState)
        case .plateCandidateChosen(let candidate):
            choosePlateCandidate(candidate)
        case .addressQueryChanged(let value):
            updateAddressQuery(value)
        case .addressSuggestionsChanged(let suggestions, let loading):
            setAddressSuggestions(suggestions, loading: loading)
        case .addressLookupLoadingChanged(let loading):
            setAddressLookupLoading(loading)
        case .addressChosen(let suggestion):
            chooseAddress(suggestion)
        case .metadataApplied(let occurredAtIso, let photoOccurredAtIso, let latitude, let longitude, let inferredState, let inferredAddress, let addressSuggestion):
            applyDetectedMetadata(
                occurredAtIso: occurredAtIso,
                photoOccurredAtIso: photoOccurredAtIso,
                latitude: latitude,
                longitude: longitude,
                inferredState: inferredState,
                inferredAddress: inferredAddress,
                addressSuggestion: addressSuggestion
            )
        case .fieldsChanged(let plate, let plateRegion, let address, let description, let notes, let occurredAtIso):
            update(
                plate: plate,
                plateRegion: plateRegion,
                address: address,
                description: description,
                notes: notes,
                occurredAtIso: occurredAtIso
            )
        case .philadelphiaMobilityAccessChanged(let blockNumber, let streetName, let zipCode, let vehicleMake, let vehicleModel, let bodyStyle, let vehicleColor, let violationObserved, let frequency):
            updatePhiladelphiaMobilityAccess(
                blockNumber: blockNumber,
                streetName: streetName,
                zipCode: zipCode,
                vehicleMake: vehicleMake,
                vehicleModel: vehicleModel,
                bodyStyle: bodyStyle,
                vehicleColor: vehicleColor,
                violationObserved: violationObserved,
                frequency: frequency
            )
        case .plateCorrectionAccepted:
            acceptPlateCorrection()
        case .plateCorrectionKept:
            keepPlateCorrection()
        case .plateCorrectionDismissed:
            dismissPlateCorrection()
        }
        return true
    }

    func loadDraft() {
        guard !state.draftLoaded else { return }
        Task {
            do {
                let draft = try await SharedBridge.shared.container.loadDraftUseCase.execute()
                if let draft {
                    state.plate = draft.plate
                    state.plateRegion = draft.plateRegion.isEmpty ? "NY" : draft.plateRegion
                    state.address = draft.address
                    state.addressQuery = draft.address
                    state.description = cleanComposerDraftText(draft.description_)
                    state.notes = cleanComposerDraftText(draft.notes)
                    state.philadelphiaMobilityAccessDetails = draft.philadelphiaMobilityAccessDetails ?? Self.emptyPhiladelphiaMobilityAccessDetails()
                    state.occurredAtIso = draft.occurredAtIso
                    state.selectedComplaintIds = draft.complaintIds
                    state.selectedComplaintId = draft.selectedComplaintId ?? draft.complaintIds.first
                    state.primaryMedia = draft.primaryMedia.flatMap { Self.restoreSubmissionMedia(from: $0) }
                    state.extraMedia = draft.extraMedia.map {
                        Self.restoreSubmissionMedia(from: $0)
                    }
                    .compactMap { $0 }
                    let draftLatitude = draft.latitude?.doubleValue
                    let draftLongitude = draft.longitude?.doubleValue
                    if Self.isUsableCoordinate(latitude: draftLatitude, longitude: draftLongitude) {
                        state.latitude = draftLatitude
                        state.longitude = draftLongitude
                    } else {
                        state.latitude = nil
                        state.longitude = nil
                    }
                    state.plateCandidates = draft.plateCandidates.map {
                        let bounds: CGRect?
                        if let left = $0.boundsLeft?.doubleValue,
                           let top = $0.boundsTop?.doubleValue,
                           let right = $0.boundsRight?.doubleValue,
                           let bottom = $0.boundsBottom?.doubleValue {
                            bounds = CGRect(x: left, y: top, width: right - left, height: bottom - top)
                        } else {
                            bounds = nil
                        }
                        let videoFrameTimeSeconds = $0.videoFrameTimeMs.map { Double(truncating: $0) / 1000.0 }
                        let videoFramePreviewURL = Self.restoreURL(from: $0.videoFramePreviewUri)
                        let cornerPoints = Self.restoreCornerPoints(from: $0.cornerPoints)
                        return ComposerState.PlateCandidate(
                            plate: $0.plate,
                            confidence: Double($0.confidence),
                            rawPlateText: $0.rawPlateText,
                            wasPlateCorrected: $0.wasPlateCorrected,
                            ownOcrText: nil,
                            ownOcrConfidence: nil,
                            state: $0.state,
                            stateConfidence: $0.stateConfidence?.doubleValue,
                            plateType: $0.plateType,
                            plateTypeLabel: $0.plateTypeLabel,
                            bounds: bounds,
                            cornerPoints: cornerPoints,
                            plateCropPreview: nil,
                            videoFramePreview: videoFramePreviewURL.flatMap { UIImage(contentsOfFile: $0.path) },
                            videoFramePreviewURL: videoFramePreviewURL,
                            videoFrameTimeSeconds: videoFrameTimeSeconds
                        )
                    }
                    state.selectedPlateCandidate = draft.selectedPlateCandidate
                    if draft.stage == "VERIFY" || draft.primaryMedia != nil {
                        state.stage = .verify
                    }
                }
                state.draftLoaded = true
                vehicleLookupCoordinator.refresh()
            } catch {
                state.draftLoaded = true
                state.error = error.localizedDescription
            }
        }
    }

    func reloadDraft() {
        state = ComposerState()
        refreshRemoteConfigValues()
        loadDraft()
    }

    func onComplaintTileChosen(_ complaintId: String) {
        state.selectedComplaintId = complaintId
        state.selectedComplaintIds = [complaintId]
        state.validationErrors.complaint = nil
        persistDraft()
    }

    func updateSelectedComplaint(_ complaintId: String) {
        state.selectedComplaintId = complaintId
        state.selectedComplaintIds = [complaintId]
        state.validationErrors.complaint = nil
        persistDraft()
    }

    func onPrimaryMediaChosen(_ media: ComposerState.SubmissionMedia, complaintId: String) {
        if let mediaError = mediaLimitError(for: media, replacingPrimary: true), state.primaryMedia?.fileURL != media.fileURL {
            state.validationErrors.media = mediaError
            return
        }
        let hadMedia = !mediaItems.isEmpty
        state.selectedComplaintId = complaintId
        state.selectedComplaintIds = [complaintId]
        state.primaryMedia = media
        state.stage = .verify
        state.awaitingVideoProcessingDecision = media.isVideo
        state.validationErrors.media = nil
        state.validationErrors.complaint = nil
        markFirstMediaAddedIfNeeded(hadMedia: hadMedia)
        persistDraft()
    }

    func onUploadMediaChosen(_ media: ComposerState.SubmissionMedia) {
        if let mediaError = mediaLimitError(for: media, replacingPrimary: true) {
            state.validationErrors.media = mediaError
            return
        }
        let hadMedia = !mediaItems.isEmpty
        state.pendingMediaSelection = media
        state.primaryMedia = media
        state.stage = .verify
        state.plateCandidates = []
        state.selectedPlateCandidate = nil
        state.plate = ""
        state.detectionResultMessage = nil
        state.validationErrors.media = nil
        state.complaintSheetOpen = true
        markFirstMediaAddedIfNeeded(hadMedia: hadMedia)
        persistDraft()
        vehicleLookupCoordinator.refresh()
    }

    func confirmPendingComplaint(_ complaintId: String) {
        guard let pending = state.pendingMediaSelection ?? state.primaryMedia else { return }
        if let mediaError = mediaLimitError(for: pending, replacingPrimary: true) {
            state.pendingMediaSelection = nil
            state.complaintSheetOpen = false
            state.validationErrors.media = mediaError
            return
        }
        state.selectedComplaintId = complaintId
        state.selectedComplaintIds = [complaintId]
        state.primaryMedia = pending
        state.pendingMediaSelection = nil
        state.complaintSheetOpen = false
        state.stage = .verify
        state.awaitingVideoProcessingDecision = pending.isVideo
        state.validationErrors.media = nil
        state.validationErrors.complaint = nil
        markFirstMediaAddedIfNeeded(hadMedia: false)
        persistDraft()
    }

    @discardableResult
    func addExtraMedia(_ media: ComposerState.SubmissionMedia) -> Bool {
        if let mediaError = mediaLimitError(for: media, replacingPrimary: false) {
            state.validationErrors.media = mediaError
            return false
        }
        let hadMedia = !mediaItems.isEmpty
        state.extraMedia.append(media)
        state.validationErrors.media = nil
        markFirstMediaAddedIfNeeded(hadMedia: hadMedia)
        persistDraft()
        return true
    }

    func markMediaLimitReached() {
        state.validationErrors.media = state.isPhiladelphiaSubmission ? philadelphiaSubmissionMediaMessage : maxSubmissionMediaMessage
    }

    func removeMedia(_ media: ComposerState.SubmissionMedia) {
        if state.primaryMedia?.fileURL == media.fileURL {
            let nextPrimary = state.extraMedia.first
            state.primaryMedia = nextPrimary
            state.extraMedia = nextPrimary == nil ? [] : Array(state.extraMedia.dropFirst())
            state.plateCandidates = []
            state.selectedPlateCandidate = nil
            state.plate = ""
            state.detectionResultMessage = nil
            if nextPrimary == nil && state.selectedComplaintId == nil {
                state.stage = .pickMedia
            }
        } else {
            state.extraMedia.removeAll { $0.fileURL == media.fileURL }
        }
        if mediaItems.isEmpty {
            state.firstMediaAddedAt = nil
        }
        persistDraft()
        vehicleLookupCoordinator.refresh()
    }

    func rejectMedia(_ media: ComposerState.SubmissionMedia, message: String) {
        removeMedia(media)
        state.awaitingVideoProcessingDecision = false
        state.detectingPlates = false
        state.detectionMessage = nil
        state.detectionResultMessage = nil
        state.detectionProgress = 0
        state.detectionFramePreview = nil
        state.validationErrors.media = message
        persistDraft()
    }

    private func markFirstMediaAddedIfNeeded(hadMedia: Bool) {
        guard !hadMedia, state.firstMediaAddedAt == nil, let primaryMedia = state.primaryMedia else { return }
        state.firstMediaAddedAt = Date()
        ReportedAnalytics.logReportMediaAdded(
            surface: "new_report",
            mediaCount: mediaItems.count,
            hasVideo: primaryMedia.isVideo || state.extraMedia.contains { $0.isVideo }
        )
    }

    func onVideoProcessingDecision(_ process: Bool) {
        state.awaitingVideoProcessingDecision = false
        state.detectingPlates = process
        state.detectionMessage = process ? "Detecting plates" : nil
        state.detectionResultMessage = process ? nil : "Video plate detection skipped."
        state.detectionProgress = process ? 0.05 : 0
        state.detectionFramePreview = nil
        state.detectionFrameTimeSeconds = 0
        state.detectionVideoDurationSeconds = 0
        state.detectionFrameCandidates = []
    }

    func cancelVideoProcessing() {
        state.awaitingVideoProcessingDecision = false
        state.detectingPlates = false
        state.detectionMessage = nil
        state.detectionResultMessage = nil
        state.detectionProgress = 0
        state.detectionFramePreview = nil
        state.detectionFrameTimeSeconds = 0
        state.detectionVideoDurationSeconds = 0
        state.detectionFrameCandidates = []
    }

    func setDetectionProgress(
        message: String,
        progress: Double,
        framePreview: UIImage? = nil,
        frameTimeSeconds: Double = 0,
        videoDurationSeconds: Double = 0,
        frameCandidates: [ComposerState.PlateCandidate] = [],
        allCandidates: [ComposerState.PlateCandidate] = []
    ) {
        state.detectingPlates = true
        state.detectionMessage = message
        state.detectionResultMessage = nil
        state.detectionProgress = max(0, min(progress, 1))
        state.detectionFrameTimeSeconds = frameTimeSeconds
        if videoDurationSeconds > 0 {
            state.detectionVideoDurationSeconds = videoDurationSeconds
        }
        if let framePreview {
            state.detectionFramePreview = framePreview
        }
        state.detectionFrameCandidates = frameCandidates
        if !allCandidates.isEmpty {
            state.plateCandidates = allCandidates
        }
    }

    func finishDetection(candidates: [ComposerState.PlateCandidate] = [], inferredPlate: String? = nil, inferredState: String? = nil) {
        let existingSelectedPlate = state.selectedPlateCandidate
        let existingPlate = state.plate.trimmingCharacters(in: .whitespacesAndNewlines)
        let selectedCandidate = existingSelectedPlate.flatMap { selectedPlate in
            candidates.first { $0.plate == selectedPlate }
        }
        let inferredCandidate = inferredPlate.flatMap { plate in
            candidates.first { $0.plate == plate }
        }
        let shouldApplyInferredPlate = selectedCandidate == nil && existingPlate.isEmpty
        let appliedCandidate = selectedCandidate ?? (shouldApplyInferredPlate ? inferredCandidate : nil)
        let detectedState = appliedCandidate?.state ?? inferredCandidate?.state ?? candidates.first?.state
        state.detectingPlates = false
        state.detectionMessage = nil
        state.detectionResultMessage = nil
        state.detectionProgress = 0
        state.detectionFramePreview = nil
        state.detectionFrameTimeSeconds = 0
        state.detectionVideoDurationSeconds = 0
        state.detectionFrameCandidates = []
        state.plateCandidates = candidates
        if let appliedCandidate {
            state.selectedPlateCandidate = appliedCandidate.plate
            state.plate = appliedCandidate.plate
        } else if shouldApplyInferredPlate, let inferredPlate {
            state.selectedPlateCandidate = inferredPlate
            state.plate = inferredPlate
        }
        if let inferredState = inferredState ?? detectedState {
            state.plateRegion = inferredState
        }
        persistDraft()
        vehicleLookupCoordinator.refresh()
    }

    func choosePlateCandidate(_ candidate: ComposerState.PlateCandidate) {
        let selectedRank = state.plateCandidates.firstIndex { $0.plate == candidate.plate }.map { $0 + 1 } ?? 0
        ReportedAnalytics.logAlprResultSelected(
            plateRegion: candidate.state,
            candidateCount: state.plateCandidates.count,
            selectedRank: selectedRank,
            confidence: candidate.confidence,
            wasCorrected: candidate.wasPlateCorrected
        )
        state.selectedPlateCandidate = candidate.plate
        state.plate = candidate.plate
        if let detectedState = candidate.state {
            state.plateRegion = detectedState
        }
        state.validationErrors.plate = nil
        if candidate.state != nil {
            state.validationErrors.plateRegion = nil
        }
        persistDraft()
        vehicleLookupCoordinator.refresh()
    }

    func updateAddressQuery(_ value: String) {
        let trimmedValue = value.trimmingCharacters(in: .whitespacesAndNewlines)
        let matchesChosenAddress = value == state.address
        state.addressQuery = value
        if trimmedValue.isEmpty {
            state.address = ""
        }
        if !matchesChosenAddress {
            state.latitude = nil
            state.longitude = nil
        }
        if !trimmedValue.isEmpty {
            state.validationErrors.address = nil
        }
        persistDraft()
    }

    func setAddressSuggestions(_ suggestions: [ComposerState.AddressSuggestion], loading: Bool = false) {
        state.addressSuggestions = suggestions
        state.lookupInFlight = loading
    }

    func setAddressLookupLoading(_ loading: Bool) {
        state.lookupInFlight = loading
    }

    func chooseAddress(_ suggestion: ComposerState.AddressSuggestion) {
        let previousLookupKey = vehicleLookupCoordinator.currentLookupKey
        state.address = suggestion.label
        state.addressQuery = suggestion.label
        state.latitude = suggestion.latitude
        state.longitude = suggestion.longitude
        if let region = suggestion.region {
            state.plateRegion = region
        }
        applyPhiladelphiaAddressParts(from: suggestion)
        state.addressSuggestions = []
        state.lookupInFlight = false
        state.validationErrors.address = nil
        if suggestion.region != nil {
            state.validationErrors.plateRegion = nil
        }
        persistDraft()
        if vehicleLookupCoordinator.currentLookupKey != previousLookupKey {
            vehicleLookupCoordinator.refresh()
        }
    }

    func applyDetectedMetadata(
        occurredAtIso: String? = nil,
        photoOccurredAtIso: String? = nil,
        latitude: Double? = nil,
        longitude: Double? = nil,
        inferredState: String? = nil,
        inferredAddress: String? = nil,
        addressSuggestion: ComposerState.AddressSuggestion? = nil
    ) {
        if let occurredAtIso { state.occurredAtIso = occurredAtIso }
        if let photoOccurredAtIso { state.photoOccurredAtIso = photoOccurredAtIso }
        if Self.isUsableCoordinate(latitude: latitude, longitude: longitude) {
            state.latitude = latitude
            state.longitude = longitude
        }
        let suggestionState = addressSuggestion?.region
        if let inferredState = inferredState ?? suggestionState { state.plateRegion = inferredState }
        if let suggestion = addressSuggestion {
            state.address = suggestion.label
            state.addressQuery = suggestion.label
            state.latitude = suggestion.latitude
            state.longitude = suggestion.longitude
            applyPhiladelphiaAddressParts(from: suggestion)
        } else if let inferredAddress {
            state.address = inferredAddress
            state.addressQuery = inferredAddress
        }
        if occurredAtIso != nil { state.validationErrors.occurredAt = nil }
        if inferredState != nil || suggestionState != nil { state.validationErrors.plateRegion = nil }
        if inferredAddress != nil || addressSuggestion != nil { state.validationErrors.address = nil }
        applyPhiladelphiaMediaValidation()
        persistDraft()
    }

    func update(
        plate: String? = nil,
        plateRegion: String? = nil,
        address: String? = nil,
        description: String? = nil,
        notes: String? = nil,
        occurredAtIso: String? = nil
    ) {
        let previousLookupKey = vehicleLookupCoordinator.currentLookupKey
        if let plate {
            let normalizedPlate = Self.normalizedPlateInput(plate)
            if normalizedPlate != state.plate {
                state.keptPlateCorrectionRaw = nil
            }
            state.plate = normalizedPlate
            state.plateCorrectionPrompt = nil
        }
        if let plateRegion { state.plateRegion = plateRegion.uppercased() }
        if let address {
            state.address = address
            state.addressQuery = address
        }
        if let description { state.description = description }
        if let notes { state.notes = notes }
        if let occurredAtIso { state.occurredAtIso = occurredAtIso }
        if let plate, !plate.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { state.validationErrors.plate = nil }
        if let plateRegion, !plateRegion.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { state.validationErrors.plateRegion = nil }
        if let address, !address.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { state.validationErrors.address = nil }
        if let occurredAtIso, !occurredAtIso.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { state.validationErrors.occurredAt = nil }
        persistDraft()
        if vehicleLookupCoordinator.currentLookupKey != previousLookupKey {
            vehicleLookupCoordinator.refresh()
        }
    }

    func updatePhiladelphiaMobilityAccess(
        blockNumber: String? = nil,
        streetName: String? = nil,
        zipCode: String? = nil,
        vehicleMake: String? = nil,
        vehicleModel: String? = nil,
        bodyStyle: String? = nil,
        vehicleColor: String? = nil,
        violationObserved: String? = nil,
        frequency: String? = nil
    ) {
        let current = state.philadelphiaMobilityAccessDetails
        state.philadelphiaMobilityAccessDetails = PhiladelphiaMobilityAccessDetails(
            blockNumber: blockNumber ?? current.blockNumber,
            streetName: streetName ?? current.streetName,
            zipCode: zipCode ?? current.zipCode,
            vehicleMake: vehicleMake ?? current.vehicleMake,
            vehicleModel: vehicleModel ?? current.vehicleModel,
            bodyStyle: bodyStyle ?? current.bodyStyle,
            vehicleColor: vehicleColor ?? current.vehicleColor,
            violationObserved: violationObserved ?? current.violationObserved,
            frequency: frequency ?? current.frequency
        )
        persistDraft()
    }

    private func applyPhiladelphiaAddressParts(from suggestion: ComposerState.AddressSuggestion) {
        guard suggestion.providerId == ReportAddressProvider.philadelphia.id else { return }
        let current = state.philadelphiaMobilityAccessDetails
        state.philadelphiaMobilityAccessDetails = PhiladelphiaMobilityAccessDetails(
            blockNumber: suggestion.blockNumber ?? current.blockNumber,
            streetName: suggestion.streetName ?? current.streetName,
            zipCode: suggestion.zipCode ?? current.zipCode,
            vehicleMake: current.vehicleMake,
            vehicleModel: current.vehicleModel,
            bodyStyle: current.bodyStyle,
            vehicleColor: current.vehicleColor,
            violationObserved: current.violationObserved,
            frequency: current.frequency
        )
    }

    func acceptPlateCorrection() {
        guard let prompt = state.plateCorrectionPrompt else { return }
        state.plate = prompt.suggestedPlate
        state.plateRegion = prompt.state
        state.plateCorrectionPrompt = nil
        state.keptPlateCorrectionRaw = nil
        state.validationErrors.plate = nil
        state.validationErrors.plateRegion = nil
        persistDraft()
        vehicleLookupCoordinator.refresh()
    }

    func keepPlateCorrection() {
        guard let prompt = state.plateCorrectionPrompt else { return }
        state.plateCorrectionPrompt = nil
        state.keptPlateCorrectionRaw = prompt.rawPlate
        state.validationErrors.plate = nil
        state.validationErrors.plateRegion = nil
    }

    func dismissPlateCorrection() {
        state.plateCorrectionPrompt = nil
        state.validationErrors.plate = "Review the plate format before submitting."
    }

    func toggleComplaint(_ complaintId: String) {
        if state.selectedComplaintIds.contains(complaintId) {
            state.selectedComplaintIds.removeAll { $0 == complaintId }
        } else {
            state.selectedComplaintIds.append(complaintId)
        }
        persistDraft()
    }

    func persistCurrentDraft() {
        persistDraft()
    }

    func clearDraft() {
        let draftMediaURLs = composerDraftMediaURLs(from: state)
        Task {
            try? await SharedBridge.shared.container.clearDraftUseCase.execute()
        }
        PersistentMediaStore.deleteStoredMedia(draftMediaURLs)
        state = ComposerState(draftLoaded: true)
    }

    private func persistDraft() {
        let draft = makeComposerReportDraft(from: state)
        Task {
            try? await SharedBridge.shared.container.saveDraftUseCase.execute(draft: draft)
        }
    }

    private static func restoreSubmissionMedia(from media: DraftMedia) -> ComposerState.SubmissionMedia? {
        guard let restoredURL = restoreURL(from: media.uri) else {
            return nil
        }
        return ComposerState.SubmissionMedia(
            fileURL: restoredURL,
            displayName: media.displayName,
            isVideo: media.isVideo
        )
    }

    private static func restoreURL(from value: String?) -> URL? {
        guard let value, !value.isEmpty else { return nil }
        let url = URL(string: value) ?? URL(fileURLWithPath: value)
        return PersistentMediaStore.restoredURL(from: url)
    }

    private static func restoreCornerPoints(from values: [KotlinFloat]) -> [CGPoint] {
        guard values.count >= 8 else { return [] }
        return stride(from: 0, to: min(values.count, 8), by: 2).map { index in
            CGPoint(
                x: min(1, max(0, CGFloat(values[index].doubleValue))),
                y: min(1, max(0, CGFloat(values[index + 1].doubleValue)))
            )
        }
    }

    private static func isUsableCoordinate(latitude: Double?, longitude: Double?) -> Bool {
        guard let latitude, let longitude else { return false }
        guard latitude.isFinite, longitude.isFinite else { return false }
        guard abs(latitude) <= 90, abs(longitude) <= 180 else { return false }
        return abs(latitude) > 0.000001 || abs(longitude) > 0.000001
    }

    static func emptyPhiladelphiaMobilityAccessDetails() -> PhiladelphiaMobilityAccessDetails {
        PhiladelphiaMobilityAccessDetails(
            blockNumber: "",
            streetName: "",
            zipCode: "",
            vehicleMake: "",
            vehicleModel: "",
            bodyStyle: "",
            vehicleColor: "",
            violationObserved: "",
            frequency: ""
        )
    }

    func submit() {
        print("ReportedSubmit: submit requested; validating")
        guard prepareSubmit() else { return }
        state.loading = true
        state.submitProgress = 0
        state.submitMessage = "Preparing report"
        state.validationErrors = ComposerState.ValidationErrors()
        let submittedMedia = [state.primaryMedia].compactMap { $0 } + state.extraMedia
        let submittedMediaURLs = submittedMedia.map(\.fileURL) + state.plateCandidates.compactMap(persistedVideoFramePreviewURL)
        let submitAddress = state.addressQuery.isEmpty ? state.address : state.addressQuery
        let submitCounty = ReportedAnalytics.county(from: submitAddress)
        let submitPlateRegion = state.plateRegion
        let submitComplaintCount = state.selectedComplaintIds.count
        let submitMediaCount = submittedMedia.count
        let submitHasVideo = submittedMedia.contains { $0.isVideo }
        let philadelphiaDetails = submittablePhiladelphiaMobilityAccessDetails(for: state)
        let baseFailureReport = ReportSubmissionFailureLogger.reportPayload(
            plate: state.plate,
            plateRegion: state.plateRegion,
            address: submitAddress,
            complaintIds: state.selectedComplaintIds,
            timeOfIncidentIso: state.occurredAtIso.isEmpty ? nil : state.occurredAtIso,
            latitude: state.latitude,
            longitude: state.longitude,
            description: state.description,
            notes: state.notes,
            mediaFileCount: 0
        )
        print("ReportedSubmit: validated report; media=\(submittedMedia.count) plate=\(state.plate)/\(state.plateRegion) complaintIds=\(state.selectedComplaintIds)")
        Task {
            var failureReport = baseFailureReport
            do {
                await MainActor.run {
                    state.submitProgress = 0.02
                    state.submitMessage = "Uploading media"
                }
                let mediaFiles = try await uploadComposerMediaForSubmission(
                    submittedMedia,
                    isPhiladelphiaSubmission: philadelphiaDetails != nil,
                    philadelphiaMediaCount: philadelphiaSubmissionMediaCount,
                    videoMessage: philadelphiaSubmissionVideoMessage,
                    mediaMessage: philadelphiaSubmissionMediaMessage
                ) { progress, message in
                    await MainActor.run {
                        self.state.submitProgress = progress
                        self.state.submitMessage = message
                    }
                }
                print("ReportedSubmit: media upload complete; parseFiles=\(mediaFiles.count)")
                failureReport = ReportSubmissionFailureLogger.reportPayload(
                    plate: state.plate,
                    plateRegion: state.plateRegion,
                    address: submitAddress,
                    complaintIds: state.selectedComplaintIds,
                    timeOfIncidentIso: state.occurredAtIso.isEmpty ? nil : state.occurredAtIso,
                    latitude: state.latitude,
                    longitude: state.longitude,
                    description: state.description,
                    notes: state.notes,
                    mediaFileCount: mediaFiles.count
                )
                let command = SubmitReportCommand(
                    plate: state.plate,
                    plateRegion: state.plateRegion,
                    description: state.description,
                    notes: state.notes,
                    address: submitAddress,
                    complaintIds: state.selectedComplaintIds,
                    timeOfIncidentIso: state.occurredAtIso.isEmpty ? nil : state.occurredAtIso,
                    latitude: state.latitude.map { KotlinDouble(double: $0) },
                    longitude: state.longitude.map { KotlinDouble(double: $0) },
                    vehicleImageDescription: nil,
                    vehicleColor: philadelphiaDetails?.vehicleColor.nilIfBlank,
                    vehicleMake: philadelphiaDetails?.vehicleMake.nilIfBlank
                        ?? state.vehicleLookupDetails?.vehicleMake?.nilIfBlank,
                    vehicleModel: philadelphiaDetails?.vehicleModel.nilIfBlank
                        ?? state.vehicleLookupDetails?.vehicleModel?.nilIfBlank,
                    mediaUrls: [],
                    mediaFiles: mediaFiles,
                    vehicleVin: nil,
                    vehicleYear: state.vehicleLookupDetails?.vehicleYear?.nilIfBlank,
                    vehicleBodyClass: philadelphiaDetails?.bodyStyle.nilIfBlank
                        ?? state.vehicleLookupDetails?.vehicleBody?.nilIfBlank,
                    philadelphiaMobilityAccessDetails: philadelphiaDetails
                )
                await MainActor.run {
                    state.submitProgress = 0.88
                    state.submitMessage = "Submitting report"
                }
                let submittedObjectId = try await SharedBridge.shared.container.submitReportUseCase.execute(command: command)
                print("ReportedSubmit: Parse submission complete; cleaning local media")
                await MainActor.run {
                    state.submitProgress = 0.96
                    state.submitMessage = "Cleaning up"
                }
                try? await SharedBridge.shared.container.clearDraftUseCase.execute()
                IOSMediaScannerSettings.markSubmittedAutoReportMediaURLs(submittedMedia.map(\.fileURL))
                PersistentMediaStore.deleteStoredMedia(submittedMediaURLs)
                ReportedAnalytics.logSubmitReport(
                    county: submitCounty,
                    plateRegion: submitPlateRegion,
                    complaintCount: submitComplaintCount,
                    mediaCount: submitMediaCount,
                    hasVideo: submitHasVideo,
                    mediaToSubmitMillis: state.mediaToSubmitMillis
                )
                print("ReportedSubmit: submit flow finished successfully")
                state = ComposerState()
                submittedReportObjectId = submittedObjectId
            } catch {
                print("ReportedSubmit: submit flow failed \(error)")
                let failedSession = try? await SharedBridge.shared.container.loadSessionUseCase.execute()
                ReportedAnalytics.logSubmitReportFailed(
                    surface: "new_report",
                    stage: state.stage == .verify ? "verify" : "pick_media",
                    error: error,
                    plateRegion: submitPlateRegion,
                    complaintCount: submitComplaintCount,
                    mediaCount: submitMediaCount,
                    hasVideo: submitHasVideo,
                    session: failedSession,
                    report: failureReport
                )
                state.loading = false
                state.submitProgress = nil
                state.submitMessage = nil
                state.error = ReportedAnalytics.userFacingSubmitFailureMessage(for: error)
            }
        }
    }

    func consumeSubmittedReport() {
        submittedReportObjectId = nil
    }

    func prepareSubmit() -> Bool {
        state.error = nil
        if let suggestion = PlatePatternClassifier.shared.suggestedCorrection(rawPlate: state.plate),
           state.keptPlateCorrectionRaw != state.plate {
            state.loading = false
            state.plateCorrectionPrompt = ComposerState.PlateCorrectionPrompt(
                rawPlate: state.plate,
                suggestedPlate: suggestion.normalizedPlate,
                state: suggestion.state,
                label: suggestion.label
            )
            state.validationErrors.plate = nil
            state.validationErrors.plateRegion = nil
            return false
        }
        let validationErrors = validateSubmission()
        if validationErrors.hasErrors {
            state.loading = false
            state.validationErrors = validationErrors
            state.stage = .verify
            return false
        }
        state.validationErrors = ComposerState.ValidationErrors()
        return true
    }

    private func validateSubmission() -> ComposerState.ValidationErrors {
        ComposerState.ValidationErrors(
            media: {
                if state.primaryMedia == nil {
                    return "Add at least one photo or video."
                }
                if state.isPhiladelphiaSubmission && mediaItems.contains(where: { $0.isVideo }) {
                    return philadelphiaSubmissionVideoMessage
                }
                if state.isPhiladelphiaSubmission && mediaItems.count > philadelphiaSubmissionMediaCount {
                    return philadelphiaSubmissionMediaMessage
                }
                if mediaItems.count > maxSubmissionMediaCount {
                    return maxSubmissionMediaMessage
                }
                if mediaItems.filter(\.isVideo).count > maxSubmissionVideoCount {
                    return maxSubmissionVideoMessage
                }
                return nil
            }(),
            complaint: state.selectedComplaintId == nil ? "Choose a complaint type." : nil,
            plate: {
                if state.plate.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    return "Enter the license plate."
                }
                if state.plate.count > maxLicensePlateInputLength {
                    return "License plate must be \(maxLicensePlateInputLength) characters or fewer."
                }
                return nil
            }(),
            plateRegion: state.plateRegion.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Choose a state." : nil,
            address: state.addressQuery.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && state.address.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Enter or choose an address." : nil,
            occurredAt: state.occurredAtIso.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Choose when this happened." : nil
        )
    }

    static func normalizedPlateInput(_ value: String) -> String {
        let normalized = value
            .uppercased()
            .filter { $0.isLetter || $0.isNumber }
        return String(normalized.prefix(maxLicensePlateInputLength))
    }

    private var mediaItems: [ComposerState.SubmissionMedia] {
        var items: [ComposerState.SubmissionMedia] = []
        if let primaryMedia = state.primaryMedia {
            items.append(primaryMedia)
        }
        items.append(contentsOf: state.extraMedia)
        return items
    }

    private func mediaLimitError(
        for media: ComposerState.SubmissionMedia,
        replacingPrimary: Bool
    ) -> String? {
        let existingMedia = replacingPrimary ? state.extraMedia : mediaItems
        if existingMedia.contains(where: { $0.fileURL == media.fileURL }) {
            return duplicateSubmissionMediaMessage
        }
        if state.isPhiladelphiaSubmission && media.isVideo {
            return philadelphiaSubmissionVideoMessage
        }
        if state.isPhiladelphiaSubmission && existingMedia.contains(where: { $0.isVideo }) {
            return philadelphiaSubmissionVideoMessage
        }
        if state.isPhiladelphiaSubmission && existingMedia.count + 1 > philadelphiaSubmissionMediaCount {
            return philadelphiaSubmissionMediaMessage
        }
        if existingMedia.count + 1 > maxSubmissionMediaCount {
            return maxSubmissionMediaMessage
        }
        if media.isVideo && existingMedia.filter(\.isVideo).count >= maxSubmissionVideoCount {
            return maxSubmissionVideoMessage
        }
        return nil
    }

    private func applyPhiladelphiaMediaValidation() {
        guard state.isPhiladelphiaSubmission else { return }
        if mediaItems.contains(where: { $0.isVideo }) {
            state.validationErrors.media = philadelphiaSubmissionVideoMessage
        } else if mediaItems.count > philadelphiaSubmissionMediaCount {
            state.validationErrors.media = philadelphiaSubmissionMediaMessage
        }
    }
}
