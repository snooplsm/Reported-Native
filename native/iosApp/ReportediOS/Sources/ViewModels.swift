import Foundation
import SharedCore
import UIKit

struct SessionState {
    var loading = true
    var session: UserSession?
    var isGuest = false
}

struct LoginState {
    var email = ""
    var password = ""
    var loading = false
    var error: String?
}

struct RegisterState {
    var firstName = ""
    var lastName = ""
    var phone = ""
    var email = ""
    var password = ""
    var testify = false
    var loading = false
    var error: String?
}

struct ProfileState {
    var firstName = ""
    var lastName = ""
    var phone = ""
    var email = ""
    var themeMode: AppThemeMode = .system
    var editing = false
    var loading = false
    var error: String?
}

struct ComposerState {
    enum SubmissionStage {
        case pickMedia
        case verify
    }

    struct SubmissionMedia: Identifiable {
        let id = UUID()
        let fileURL: URL
        let displayName: String
        let isVideo: Bool
    }

    struct PlateCandidate: Identifiable {
        let id = UUID()
        let plate: String
        let confidence: Double
        let state: String?
        let stateConfidence: Double?
        let plateType: String?
        let plateTypeLabel: String?
        let bounds: CGRect?
        let videoFramePreview: UIImage?
        let videoFrameTimeSeconds: Double?
    }

    struct AddressSuggestion: Identifiable {
        let id = UUID()
        let label: String
        let latitude: Double
        let longitude: Double
    }

    struct ValidationErrors {
        var media: String?
        var complaint: String?
        var plate: String?
        var plateRegion: String?
        var address: String?
        var occurredAt: String?

        var hasErrors: Bool {
            media != nil || complaint != nil || plate != nil || plateRegion != nil || address != nil || occurredAt != nil
        }
    }

    var stage: SubmissionStage = .pickMedia
    var selectedComplaintId: String?
    var complaintSheetOpen = false
    var primaryMedia: SubmissionMedia?
    var extraMedia: [SubmissionMedia] = []
    var pendingMediaSelection: SubmissionMedia?
    var awaitingVideoProcessingDecision = false
    var detectingPlates = false
    var detectionMessage: String?
    var detectionResultMessage: String?
    var detectionProgress = 0.0
    var detectionFramePreview: UIImage?
    var detectionFrameTimeSeconds = 0.0
    var detectionVideoDurationSeconds = 0.0
    var detectionFrameCandidates: [PlateCandidate] = []
    var plateCandidates: [PlateCandidate] = []
    var selectedPlateCandidate: String?
    var latitude: Double?
    var longitude: Double?
    var addressQuery = ""
    var addressSuggestions: [AddressSuggestion] = []
    var lookupInFlight = false
    var plate = ""
    var plateRegion = "NY"
    var address = ""
    var description = ""
    var notes = ""
    var occurredAtIso = ""
    var complaintCategories: [ComplaintCategory] = Array(Catalogs.shared.complaintCategories)
    var selectedComplaintIds: [String] = []
    var draftLoaded = false
    var loading = false
    var error: String?
    var validationErrors = ValidationErrors()
    let info = "Media, location, uploads, and notifications are the next native migration slice. This Swift app already shares the live API, use cases, and draft state with the KMP core."
}

@MainActor
final class SessionViewModel: ObservableObject {
    @Published private(set) var state = SessionState()

    func load() {
        Task {
            do {
                let session = try await SharedBridge.shared.container.loadSessionUseCase.execute()
                let isGuest = try await SharedBridge.shared.container.loadGuestModeUseCase.execute()
                state = SessionState(loading: false, session: session, isGuest: session == nil ? isGuest.boolValue : false)
            } catch {
                let isGuest = (try? await SharedBridge.shared.container.loadGuestModeUseCase.execute())?.boolValue ?? false
                state = SessionState(loading: false, session: nil, isGuest: isGuest)
            }
        }
    }

    func didAuthenticate() {
        Task {
            try? await SharedBridge.shared.container.saveGuestModeUseCase.execute(enabled: false)
            load()
        }
    }

    func continueAsGuest() {
        Task {
            try? await SharedBridge.shared.container.saveGuestModeUseCase.execute(enabled: true)
            state = SessionState(loading: false, session: nil, isGuest: true)
        }
    }

    func logout() {
        Task {
            try? await SharedBridge.shared.container.logoutUseCase.execute()
            try? await SharedBridge.shared.container.saveGuestModeUseCase.execute(enabled: false)
            state = SessionState(loading: false, session: nil, isGuest: false)
        }
    }
}

@MainActor
final class LoginViewModel: ObservableObject {
    @Published private(set) var state = LoginState()

    func update(email: String? = nil, password: String? = nil) {
        if let email { state.email = email }
        if let password { state.password = password }
    }

    func login(onSuccess: @escaping () -> Void) {
        state.loading = true
        state.error = nil
        Task {
            do {
                _ = try await SharedBridge.shared.container.loginUseCase.execute(
                    email: state.email,
                    password: state.password
                )
                state.loading = false
                onSuccess()
            } catch {
                state.loading = false
                state.error = error.localizedDescription
            }
        }
    }

    func forgotPassword() {
        guard !state.email.isEmpty else {
            state.error = "Enter your email first."
            return
        }
        Task {
            do {
                try await SharedBridge.shared.container.forgotPasswordUseCase.execute(email: state.email)
            } catch {
                state.error = "Couldn't send reset email."
            }
        }
    }
}

@MainActor
final class RegisterViewModel: ObservableObject {
    @Published private(set) var state = RegisterState()

    func update(
        firstName: String? = nil,
        lastName: String? = nil,
        phone: String? = nil,
        email: String? = nil,
        password: String? = nil,
        testify: Bool? = nil
    ) {
        if let firstName { state.firstName = firstName }
        if let lastName { state.lastName = lastName }
        if let phone { state.phone = phone }
        if let email { state.email = email }
        if let password { state.password = password }
        if let testify { state.testify = testify }
    }

    func register(onSuccess: @escaping () -> Void) {
        state.loading = true
        state.error = nil
        Task {
            do {
                _ = try await SharedBridge.shared.container.registerUseCase.execute(
                    firstName: state.firstName,
                    lastName: state.lastName,
                    phone: state.phone,
                    testify: state.testify,
                    email: state.email,
                    password: state.password
                )
                state.loading = false
                onSuccess()
            } catch {
                state.loading = false
                state.error = error.localizedDescription
            }
        }
    }
}

@MainActor
final class ReportsViewModel: ObservableObject {
    enum Mode: Equatable {
        case search
        case list
    }

    @Published private(set) var reports: [ReportSummary] = []
    @Published private(set) var reportDetails: [String: ReportSummary] = [:]
    @Published private(set) var detailLoadingIds: Set<String> = []
    @Published private(set) var loading = false
    @Published private(set) var loadingMore = false
    @Published private(set) var hasMore = false
    @Published private(set) var nextSkip: Int32 = 0
    @Published private(set) var deletingReportKeys: Set<String> = []
    @Published private(set) var error: String?
    @Published private(set) var mode: Mode?
    @Published var licenseQuery = ""
    @Published var startDate = Date()
    @Published var endDate = Date()
    @Published var usesStartDate = false
    @Published var usesEndDate = false
    private var activeFilter: ReportFilter?

    func chooseList() {
        mode = .list
        reports = []
        reportDetails = [:]
        hasMore = false
        nextSkip = 0
        activeFilter = nil
        load(filter: nil, append: false)
    }

    func chooseSearch() {
        mode = .search
        reports = []
        reportDetails = [:]
        hasMore = false
        nextSkip = 0
        activeFilter = nil
        error = nil
    }

    func search() {
        let filter = ReportFilter(
            keywords: "",
            srid: "",
            complaints: [],
            whenDescription: "",
            locationDescription: "",
            license: licenseQuery.trimmingCharacters(in: .whitespacesAndNewlines),
            startDateIso: usesStartDate ? startDate.ISO8601Format() : "",
            endDateIso: usesEndDate ? endDate.ISO8601Format() : ""
        )
        activeFilter = filter
        load(filter: filter, append: false)
    }

    func loadNextPageIfNeeded(current report: ReportSummary) {
        guard hasMore, !loading, !loadingMore else { return }
        guard reports.suffix(5).contains(where: { reportKey($0) == reportKey(report) }) else { return }
        load(filter: activeFilter, append: true)
    }

    private func load(filter: ReportFilter?, append: Bool) {
        if append {
            loadingMore = true
        } else {
            loading = true
        }
        error = nil
        let skip = append ? nextSkip : 0
        Task {
            do {
                let page = try await SharedBridge.shared.container.fetchReportsUseCase.execute(filter: filter, skip: skip, forCurrentUser: true)
                if append {
                    let existingKeys = Set(reports.map(reportKey))
                    reports.append(contentsOf: page.reports.filter { !existingKeys.contains(reportKey($0)) })
                } else {
                    reports = page.reports
                    reportDetails = [:]
                    detailLoadingIds = []
                }
                hasMore = page.hasMore
                nextSkip = Int32(reports.count)
                loading = false
                loadingMore = false
            } catch {
                self.error = error.localizedDescription
                loading = false
                loadingMore = false
            }
        }
    }

    private func reportKey(_ report: ReportSummary) -> String {
        report.objectId.isEmpty ? "\(report.id)" : report.objectId
    }

    func loadDetail(for report: ReportSummary) {
        let objectId = report.objectId
        guard !objectId.isEmpty, reportDetails[objectId] == nil, !detailLoadingIds.contains(objectId) else { return }
        detailLoadingIds.insert(objectId)
        Task {
            do {
                let detail = try await SharedBridge.shared.container.fetchReportDetailUseCase.execute(objectId: objectId)
                reportDetails[objectId] = detail
                detailLoadingIds.remove(objectId)
            } catch {
                self.error = error.localizedDescription
                detailLoadingIds.remove(objectId)
            }
        }
    }

    func delete(report: ReportSummary) {
        let key = reportKey(report)
        guard report.canDelete, !deletingReportKeys.contains(key) else { return }
        deletingReportKeys.insert(key)
        error = nil
        Task {
            do {
                try await SharedBridge.shared.container.deleteReportUseCase.execute(report: report)
                reports.removeAll { reportKey($0) == key }
                reportDetails.removeValue(forKey: report.objectId)
                detailLoadingIds.remove(report.objectId)
                deletingReportKeys.remove(key)
            } catch {
                self.error = error.localizedDescription
                deletingReportKeys.remove(key)
            }
        }
    }
}

@MainActor
final class ProfileViewModel: ObservableObject {
    @Published private(set) var state = ProfileState()

    func load() {
        Task {
            if let themeMode = try? await SharedBridge.shared.container.loadAppThemeModeUseCase.execute() {
                state.themeMode = themeMode
            }
            if let session = try? await SharedBridge.shared.container.loadSessionUseCase.execute() {
                state.firstName = session.firstName
                state.lastName = session.lastName
                state.phone = session.phone
                state.email = session.email
            }
        }
    }

    func update(
        firstName: String? = nil,
        lastName: String? = nil,
        phone: String? = nil,
        email: String? = nil
    ) {
        if let firstName { state.firstName = firstName }
        if let lastName { state.lastName = lastName }
        if let phone { state.phone = phone }
        if let email { state.email = email }
    }

    func toggleEditing() {
        state.editing.toggle()
    }

    func setThemeMode(_ mode: AppThemeMode) {
        state.themeMode = mode
        Task {
            try? await SharedBridge.shared.container.saveAppThemeModeUseCase.execute(mode: mode)
        }
    }

    func save() {
        state.loading = true
        state.error = nil
        Task {
            do {
                _ = try await SharedBridge.shared.container.updateProfileUseCase.execute(
                    email: state.email,
                    phone: state.phone,
                    firstName: state.firstName,
                    lastName: state.lastName
                )
                state.loading = false
                state.editing = false
            } catch {
                state.loading = false
                state.error = error.localizedDescription
            }
        }
    }
}

@MainActor
final class ThemeViewModel: ObservableObject {
    @Published private(set) var mode: AppThemeMode = .system

    func load() {
        Task {
            if let loadedMode = try? await SharedBridge.shared.container.loadAppThemeModeUseCase.execute() {
                mode = loadedMode
            }
        }
    }

    func update(_ mode: AppThemeMode) {
        self.mode = mode
        Task {
            try? await SharedBridge.shared.container.saveAppThemeModeUseCase.execute(mode: mode)
        }
    }
}

@MainActor
final class ComposerViewModel: ObservableObject {
    @Published private(set) var state = ComposerState()

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
                    state.description = draft.description
                    state.notes = draft.notes
                    state.occurredAtIso = draft.occurredAtIso
                    state.selectedComplaintIds = draft.complaintIds
                    state.selectedComplaintId = draft.selectedComplaintId ?? draft.complaintIds.first
                    state.primaryMedia = draft.primaryMedia.flatMap { Self.restoreSubmissionMedia(from: $0) }
                    state.extraMedia = draft.extraMedia.map {
                        Self.restoreSubmissionMedia(from: $0)
                    }
                    .compactMap { $0 }
                    state.latitude = draft.latitude?.doubleValue
                    state.longitude = draft.longitude?.doubleValue
                    state.plateCandidates = draft.plateCandidates.map {
                        ComposerState.PlateCandidate(
                            plate: $0.plate,
                            confidence: Double($0.confidence),
                            state: $0.state,
                            stateConfidence: $0.stateConfidence?.doubleValue,
                            plateType: $0.plateType,
                            plateTypeLabel: $0.plateTypeLabel,
                            bounds: nil,
                            videoFramePreview: nil,
                            videoFrameTimeSeconds: nil
                        )
                    }
                    state.selectedPlateCandidate = draft.selectedPlateCandidate
                    if draft.stage == "VERIFY" || draft.primaryMedia != nil {
                        state.stage = .verify
                    }
                }
                state.draftLoaded = true
            } catch {
                state.draftLoaded = true
                state.error = error.localizedDescription
            }
        }
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
        state.selectedComplaintId = complaintId
        state.selectedComplaintIds = [complaintId]
        state.primaryMedia = media
        state.stage = .verify
        state.awaitingVideoProcessingDecision = media.isVideo
        state.validationErrors.media = nil
        state.validationErrors.complaint = nil
        persistDraft()
    }

    func onUploadMediaChosen(_ media: ComposerState.SubmissionMedia) {
        state.pendingMediaSelection = media
        state.complaintSheetOpen = true
    }

    func confirmPendingComplaint(_ complaintId: String) {
        guard let pending = state.pendingMediaSelection else { return }
        state.selectedComplaintId = complaintId
        state.selectedComplaintIds = [complaintId]
        state.primaryMedia = pending
        state.pendingMediaSelection = nil
        state.complaintSheetOpen = false
        state.stage = .verify
        state.awaitingVideoProcessingDecision = pending.isVideo
        state.validationErrors.media = nil
        state.validationErrors.complaint = nil
        persistDraft()
    }

    func addExtraMedia(_ media: ComposerState.SubmissionMedia) {
        state.extraMedia.append(media)
        state.validationErrors.media = nil
        persistDraft()
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
        let detectedState = candidates.first { $0.plate == inferredPlate }?.state ?? candidates.first?.state
        state.detectingPlates = false
        state.detectionMessage = nil
        state.detectionResultMessage = candidates.isEmpty ? "Scan complete. No license plates were detected." : nil
        state.detectionProgress = 0
        state.detectionFramePreview = nil
        state.detectionFrameTimeSeconds = 0
        state.detectionVideoDurationSeconds = 0
        state.detectionFrameCandidates = []
        state.plateCandidates = candidates
        if let inferredPlate {
            state.selectedPlateCandidate = inferredPlate
            state.plate = inferredPlate
        }
        if let inferredState = inferredState ?? detectedState {
            state.plateRegion = inferredState
        }
        persistDraft()
    }

    func choosePlateCandidate(_ candidate: ComposerState.PlateCandidate) {
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
    }

    func updateAddressQuery(_ value: String) {
        state.addressQuery = value
        if !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            state.validationErrors.address = nil
        }
    }

    func setAddressSuggestions(_ suggestions: [ComposerState.AddressSuggestion], loading: Bool = false) {
        state.addressSuggestions = suggestions
        state.lookupInFlight = loading
    }

    func setAddressLookupLoading(_ loading: Bool) {
        state.lookupInFlight = loading
    }

    func chooseAddress(_ suggestion: ComposerState.AddressSuggestion) {
        state.address = suggestion.label
        state.addressQuery = suggestion.label
        state.latitude = suggestion.latitude
        state.longitude = suggestion.longitude
        state.addressSuggestions = []
        state.lookupInFlight = false
        state.validationErrors.address = nil
        persistDraft()
    }

    func applyDetectedMetadata(
        occurredAtIso: String? = nil,
        latitude: Double? = nil,
        longitude: Double? = nil,
        inferredState: String? = nil,
        inferredAddress: String? = nil
    ) {
        if let occurredAtIso { state.occurredAtIso = occurredAtIso }
        if let latitude { state.latitude = latitude }
        if let longitude { state.longitude = longitude }
        if let inferredState { state.plateRegion = inferredState }
        if let inferredAddress {
            state.address = inferredAddress
            state.addressQuery = inferredAddress
        }
        if occurredAtIso != nil { state.validationErrors.occurredAt = nil }
        if inferredState != nil { state.validationErrors.plateRegion = nil }
        if inferredAddress != nil { state.validationErrors.address = nil }
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
        if let plate { state.plate = plate }
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
    }

    func toggleComplaint(_ complaintId: String) {
        if state.selectedComplaintIds.contains(complaintId) {
            state.selectedComplaintIds.removeAll { $0 == complaintId }
        } else {
            state.selectedComplaintIds.append(complaintId)
        }
        persistDraft()
    }

    private func persistDraft() {
        let draft = ReportDraft(
            plate: state.plate,
            plateRegion: state.plateRegion,
            address: state.address,
            description: state.description,
            notes: state.notes,
            complaintIds: state.selectedComplaintId.map { [$0] } ?? state.selectedComplaintIds,
            occurredAtIso: state.occurredAtIso,
            selectedComplaintId: state.selectedComplaintId,
            stage: state.stage == .verify ? "VERIFY" : "PICK_MEDIA",
            primaryMedia: state.primaryMedia.map { media in
                DraftMedia(
                    uri: media.fileURL.absoluteString,
                    displayName: media.displayName,
                    mimeType: "",
                    isVideo: media.isVideo
                )
            },
            extraMedia: state.extraMedia.map {
                DraftMedia(
                    uri: $0.fileURL.absoluteString,
                    displayName: $0.displayName,
                    mimeType: "",
                    isVideo: $0.isVideo
                )
            },
            latitude: state.latitude.map { KotlinDouble(double: $0) },
            longitude: state.longitude.map { KotlinDouble(double: $0) },
            plateCandidates: state.plateCandidates.map {
                DraftPlateCandidate(
                    plate: $0.plate,
                    confidence: Float($0.confidence),
                    state: $0.state,
                    stateConfidence: $0.stateConfidence.map { KotlinFloat(float: Float($0)) },
                    plateType: $0.plateType,
                    plateTypeLabel: $0.plateTypeLabel,
                    focalPointX: nil,
                    focalPointY: nil,
                    boundsLeft: nil,
                    boundsTop: nil,
                    boundsRight: nil,
                    boundsBottom: nil,
                    rotationDegrees: 0,
                    cornerPoints: [],
                    thumbnailUri: nil,
                    videoFramePreviewUri: nil,
                    videoFrameTimeMs: nil
                )
            },
            selectedPlateCandidate: state.selectedPlateCandidate
        )
        Task {
            try? await SharedBridge.shared.container.saveDraftUseCase.execute(draft: draft)
        }
    }

    private static func restoreSubmissionMedia(from media: DraftMedia) -> ComposerState.SubmissionMedia? {
        let url = URL(string: media.uri) ?? URL(fileURLWithPath: media.uri)
        return ComposerState.SubmissionMedia(
            fileURL: url,
            displayName: media.displayName,
            isVideo: media.isVideo
        )
    }

    func submit() {
        state.error = nil
        let validationErrors = validateSubmission()
        if validationErrors.hasErrors {
            state.loading = false
            state.validationErrors = validationErrors
            state.stage = .verify
            return
        }
        state.loading = true
        state.validationErrors = ComposerState.ValidationErrors()
        Task {
            do {
                let command = SubmitReportCommand(
                    plate: state.plate,
                    plateRegion: state.plateRegion,
                    description: state.description,
                    notes: state.notes,
                    address: state.address,
                    complaintIds: state.selectedComplaintIds,
                    timeOfIncidentIso: state.occurredAtIso.isEmpty ? nil : state.occurredAtIso,
                    latitude: state.latitude.map { KotlinDouble(double: $0) },
                    longitude: state.longitude.map { KotlinDouble(double: $0) },
                    mediaUrls: []
                )
                try await SharedBridge.shared.container.submitReportUseCase.execute(command: command)
                try? await SharedBridge.shared.container.clearDraftUseCase.execute()
                state = ComposerState()
            } catch {
                state.loading = false
                state.error = error.localizedDescription
            }
        }
    }

    private func validateSubmission() -> ComposerState.ValidationErrors {
        ComposerState.ValidationErrors(
            media: state.primaryMedia == nil ? "Add at least one photo or video." : nil,
            complaint: state.selectedComplaintId == nil ? "Choose a complaint type." : nil,
            plate: state.plate.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Enter the license plate." : nil,
            plateRegion: state.plateRegion.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Choose a state." : nil,
            address: state.addressQuery.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && state.address.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Enter or choose an address." : nil,
            occurredAt: state.occurredAtIso.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Choose when this happened." : nil
        )
    }
}
