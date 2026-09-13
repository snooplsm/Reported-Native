import Foundation
import FirebaseAnalytics
import FirebaseCrashlytics
import ImageIO
import SharedCore
import UIKit
import UniformTypeIdentifiers

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
    var passwordResetMessage: String?
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
    var testify = false
    var themeMode: AppThemeMode = .system
    var editing = false
    var loading = false
    var error: String?
}

struct ThemeState {
    var mode: AppThemeMode = .system
}

enum ReportsMode: Equatable {
    case search
    case list
}

struct ReportsState {
    var reports: [ReportSummary] = []
    var reportDetails: [String: ReportSummary] = [:]
    var detailLoadingIds: Set<String> = []
    var loading = false
    var loadingMore = false
    var hasMore = false
    var nextSkip: Int32 = 0
    var deletingReportKeys: Set<String> = []
    var error: String?
    var mode: ReportsMode?
    var licenseQuery = ""
    var startDate = Date()
    var endDate = Date()
    var usesStartDate = false
    var usesEndDate = false
}

enum SessionAction {
    case load
    case authenticated
    case continueAsGuest
    case logout
}

enum LoginAction {
    case emailChanged(String)
    case passwordChanged(String)
    case loginPressed(onSuccess: () -> Void)
    case googleSignInPressed(onSuccess: () -> Void)
    case appleSignInPressed(onSuccess: () -> Void)
    case forgotPasswordPressed
    case passwordResetMessageDismissed
}

enum RegisterAction {
    case fieldsChanged(
        firstName: String? = nil,
        lastName: String? = nil,
        phone: String? = nil,
        email: String? = nil,
        password: String? = nil,
        testify: Bool? = nil
    )
    case registerPressed(onSuccess: () -> Void)
    case googleSignInPressed(onSuccess: () -> Void)
    case appleSignInPressed(onSuccess: () -> Void)
}

enum ReportsAction {
    case listChosen
    case searchChosen
    case searchPressed
    case nextPageRequested(current: ReportSummary)
    case detailRequested(ReportSummary)
    case reportOpened(objectId: String)
    case reportDeleted(ReportSummary)
    case licenseChanged(String)
    case startDateChanged(Date)
    case endDateChanged(Date)
    case usesStartDateChanged(Bool)
    case usesEndDateChanged(Bool)
}

enum ProfileAction {
    case load
    case toggleEditing
    case save
    case themeModeChanged(AppThemeMode)
    case fieldsChanged(
        firstName: String? = nil,
        lastName: String? = nil,
        phone: String? = nil,
        email: String? = nil,
        testify: Bool? = nil
    )
}

enum ThemeAction {
    case load
    case modeChanged(AppThemeMode)
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
        let rawPlateText: String?
        let wasPlateCorrected: Bool
        let ownOcrText: String?
        let ownOcrConfidence: Double?
        let state: String?
        let stateConfidence: Double?
        let plateType: String?
        let plateTypeLabel: String?
        let bounds: CGRect?
        let cornerPoints: [CGPoint]
        let plateCropPreview: UIImage?
        let videoFramePreview: UIImage?
        let videoFramePreviewURL: URL?
        let videoFrameTimeSeconds: Double?
    }

    struct AddressSuggestion: Identifiable {
        let id = UUID()
        let label: String
        let latitude: Double
        let longitude: Double
        let region: String?
        let providerId: String?
        let blockNumber: String?
        let streetName: String?
        let zipCode: String?

        init(
            label: String,
            latitude: Double,
            longitude: Double,
            region: String? = nil,
            providerId: String? = nil,
            blockNumber: String? = nil,
            streetName: String? = nil,
            zipCode: String? = nil
        ) {
            self.label = label
            self.latitude = latitude
            self.longitude = longitude
            self.region = region
            self.providerId = providerId
            self.blockNumber = blockNumber
            self.streetName = streetName
            self.zipCode = zipCode
        }
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

    struct PlateCorrectionPrompt {
        let rawPlate: String
        let suggestedPlate: String
        let state: String
        let label: String
    }

    var stage: SubmissionStage = .pickMedia
    var selectedComplaintId: String?
    var complaintSheetOpen = false
    var primaryMedia: SubmissionMedia?
    var extraMedia: [SubmissionMedia] = []
    var pendingMediaSelection: SubmissionMedia?
    var firstMediaAddedAt: Date?
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
    var vehicleLookupDetails: VehicleLookupDetails?
    var vehicleLookupInFlight = false
    var vehicleLookupMessage: String?
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
    var philadelphiaMobilityAccessDetails = PhiladelphiaMobilityAccessDetails(
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
    var occurredAtIso = ""
    var photoOccurredAtIso: String?
    var complaintCategories: [ComplaintCategory] = Array(Catalogs.shared.complaintCategories)
    var showComplaintImages = RemoteConfigOverrides.shared.showComplaintImages
    var selectedComplaintIds: [String] = []
    var draftLoaded = false
    var loading = false
    var submitProgress: Double?
    var submitMessage: String?
    var error: String?
    var plateCorrectionPrompt: PlateCorrectionPrompt?
    var keptPlateCorrectionRaw: String?
    var validationErrors = ValidationErrors()
    let info = "Media, location, uploads, and notifications are the next native migration slice. This Swift app already shares the live API, use cases, and draft state with the KMP core."

    var mediaToSubmitMillis: Int64? {
        guard let firstMediaAddedAt else { return nil }
        return max(0, Int64(Date().timeIntervalSince(firstMediaAddedAt) * 1000))
    }
}

enum ComposerAction {
    case loadDraft
    case reloadDraft
    case submitValidationRequested
    case submitPressed
    case clearDraft
    case submittedReportConsumed
    case mediaLimitReached
    case draftPersistenceRequested

    case complaintTileChosen(String)
    case selectedComplaintChanged(String)
    case uploadMediaChosen(ComposerState.SubmissionMedia)
    case primaryMediaChosen(ComposerState.SubmissionMedia, complaintId: String)
    case pendingComplaintConfirmed(String)
    case extraMediaAdded(ComposerState.SubmissionMedia)
    case mediaRemoved(ComposerState.SubmissionMedia)
    case mediaRejected(ComposerState.SubmissionMedia, message: String)

    case videoProcessingDecision(Bool)
    case videoProcessingCancelled
    case detectionProgressChanged(
        message: String,
        progress: Double,
        framePreview: UIImage? = nil,
        frameTimeSeconds: Double = 0,
        videoDurationSeconds: Double = 0,
        frameCandidates: [ComposerState.PlateCandidate] = [],
        allCandidates: [ComposerState.PlateCandidate] = []
    )
    case detectionFinished(
        candidates: [ComposerState.PlateCandidate] = [],
        inferredPlate: String? = nil,
        inferredState: String? = nil
    )
    case plateCandidateChosen(ComposerState.PlateCandidate)

    case addressQueryChanged(String)
    case addressSuggestionsChanged([ComposerState.AddressSuggestion], loading: Bool = false)
    case addressLookupLoadingChanged(Bool)
    case addressChosen(ComposerState.AddressSuggestion)
    case metadataApplied(
        occurredAtIso: String? = nil,
        photoOccurredAtIso: String? = nil,
        latitude: Double? = nil,
        longitude: Double? = nil,
        inferredState: String? = nil,
        inferredAddress: String? = nil,
        addressSuggestion: ComposerState.AddressSuggestion? = nil
    )
    case fieldsChanged(
        plate: String? = nil,
        plateRegion: String? = nil,
        address: String? = nil,
        description: String? = nil,
        notes: String? = nil,
        occurredAtIso: String? = nil
    )
    case philadelphiaMobilityAccessChanged(
        blockNumber: String? = nil,
        streetName: String? = nil,
        zipCode: String? = nil,
        vehicleMake: String? = nil,
        vehicleModel: String? = nil,
        bodyStyle: String? = nil,
        vehicleColor: String? = nil,
        violationObserved: String? = nil,
        frequency: String? = nil
    )

    case plateCorrectionAccepted
    case plateCorrectionKept
    case plateCorrectionDismissed
}
