import Foundation
import FirebaseAnalytics
import ImageIO
import SharedCore
import UIKit
import UniformTypeIdentifiers

private enum ReportedAnalytics {
    static func logLogin(method: String) {
        Analytics.logEvent(AnalyticsEventLogin, parameters: [
            AnalyticsParameterMethod: method
        ])
    }

    static func logLogout() {
        Analytics.logEvent("logout", parameters: nil)
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
        hasVideo: Bool
    ) {
        Analytics.logEvent("submit_report", parameters: [
            "county": county,
            "plate_region": plateRegion,
            "complaint_count": complaintCount,
            "media_count": mediaCount,
            "has_video": hasVideo ? 1 : 0
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
}

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
        let state: String?
        let stateConfidence: Double?
        let plateType: String?
        let plateTypeLabel: String?
        let bounds: CGRect?
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
        inferredAddress: String? = nil
    )
    case fieldsChanged(
        plate: String? = nil,
        plateRegion: String? = nil,
        address: String? = nil,
        description: String? = nil,
        notes: String? = nil,
        occurredAtIso: String? = nil
    )

    case plateCorrectionAccepted
    case plateCorrectionKept
    case plateCorrectionDismissed
}

@MainActor
final class SessionViewModel: ObservableObject {
    @Published private(set) var state = SessionState()

    func onAction(_ action: SessionAction) {
        switch action {
        case .load:
            load()
        case .authenticated:
            didAuthenticate()
        case .continueAsGuest:
            continueAsGuest()
        case .logout:
            logout()
        }
    }

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
            ReportedAnalytics.logLogout()
            state = SessionState(loading: false, session: nil, isGuest: false)
        }
    }
}

@MainActor
final class LoginViewModel: ObservableObject {
    @Published private(set) var state = LoginState()

    func onAction(_ action: LoginAction) {
        switch action {
        case .emailChanged(let value):
            update(email: value)
        case .passwordChanged(let value):
            update(password: value)
        case .loginPressed(let onSuccess):
            login(onSuccess: onSuccess)
        case .googleSignInPressed(let onSuccess):
            signInWithGoogle(onSuccess: onSuccess)
        case .appleSignInPressed(let onSuccess):
            signInWithApple(onSuccess: onSuccess)
        case .forgotPasswordPressed:
            forgotPassword()
        case .passwordResetMessageDismissed:
            dismissPasswordResetMessage()
        }
    }

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
                ReportedAnalytics.logLogin(method: "password")
                onSuccess()
            } catch {
                print("ReportedAuth: login failed \(error)")
                state.loading = false
                state.error = "There was an error signing in. Please try again."
            }
        }
    }

    func signInWithGoogle(onSuccess: @escaping () -> Void) {
        state.loading = true
        state.error = nil
        Task {
            do {
                let profile = try await NativeSocialAuth.signInWithGoogle()
                completeSocialSignIn(profile, onSuccess: onSuccess)
            } catch {
                state.loading = false
                if !NativeSocialAuth.isCancellation(error) {
                    print("ReportedAuth: Google sign-in failed \(error)")
                    state.error = "There was an error signing in. Please try again."
                }
            }
        }
    }

    func signInWithApple(onSuccess: @escaping () -> Void) {
        state.loading = true
        state.error = nil
        Task {
            do {
                let profile = try await NativeSocialAuth.signInWithApple()
                completeSocialSignIn(profile, onSuccess: onSuccess)
            } catch {
                state.loading = false
                if !NativeSocialAuth.isCancellation(error) {
                    print("ReportedAuth: Apple sign-in failed \(error)")
                    state.error = "There was an error signing in. Please try again."
                }
            }
        }
    }

    func completeSocialSignIn(_ profile: SocialAuthProfile, onSuccess: @escaping () -> Void) {
        state.loading = true
        state.error = nil
        Task {
            do {
                _ = try await SharedBridge.shared.container.socialLoginUseCase.execute(
                    provider: profile.provider,
                    providerUserId: profile.providerUserId,
                    idToken: profile.idToken,
                    email: profile.email,
                    firstName: profile.firstName,
                    lastName: profile.lastName,
                    phone: "",
                    testify: false
                )
                state.loading = false
                ReportedAnalytics.logLogin(method: profile.provider)
                onSuccess()
            } catch {
                print("ReportedAuth: social login failed \(error)")
                state.loading = false
                state.error = "There was an error signing in. Please try again."
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
                state.error = nil
                state.passwordResetMessage = "We sent password reset instructions to \(state.email)."
            } catch {
                state.error = "Couldn't send reset email."
            }
        }
    }

    func dismissPasswordResetMessage() {
        state.passwordResetMessage = nil
    }
}

@MainActor
final class RegisterViewModel: ObservableObject {
    @Published private(set) var state = RegisterState()

    func onAction(_ action: RegisterAction) {
        switch action {
        case .fieldsChanged(let firstName, let lastName, let phone, let email, let password, let testify):
            update(
                firstName: firstName,
                lastName: lastName,
                phone: phone,
                email: email,
                password: password,
                testify: testify
            )
        case .registerPressed(let onSuccess):
            register(onSuccess: onSuccess)
        case .googleSignInPressed(let onSuccess):
            signInWithGoogle(onSuccess: onSuccess)
        case .appleSignInPressed(let onSuccess):
            signInWithApple(onSuccess: onSuccess)
        }
    }

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
                ReportedAnalytics.logLogin(method: "password")
                onSuccess()
            } catch {
                print("ReportedAuth: registration failed \(error)")
                state.loading = false
                state.error = "There was an error creating your account. Please try again."
            }
        }
    }

    func signInWithGoogle(onSuccess: @escaping () -> Void) {
        state.loading = true
        state.error = nil
        Task {
            do {
                let profile = try await NativeSocialAuth.signInWithGoogle()
                completeSocialSignIn(profile, onSuccess: onSuccess)
            } catch {
                state.loading = false
                if !NativeSocialAuth.isCancellation(error) {
                    print("ReportedAuth: Google registration sign-in failed \(error)")
                    state.error = "There was an error signing in. Please try again."
                }
            }
        }
    }

    func signInWithApple(onSuccess: @escaping () -> Void) {
        state.loading = true
        state.error = nil
        Task {
            do {
                let profile = try await NativeSocialAuth.signInWithApple()
                completeSocialSignIn(profile, onSuccess: onSuccess)
            } catch {
                state.loading = false
                if !NativeSocialAuth.isCancellation(error) {
                    print("ReportedAuth: Apple registration sign-in failed \(error)")
                    state.error = "There was an error signing in. Please try again."
                }
            }
        }
    }

    func completeSocialSignIn(_ profile: SocialAuthProfile, onSuccess: @escaping () -> Void) {
        let fallbackFirstName = state.firstName
        let fallbackLastName = state.lastName
        state.loading = true
        state.error = nil
        Task {
            do {
                _ = try await SharedBridge.shared.container.socialLoginUseCase.execute(
                    provider: profile.provider,
                    providerUserId: profile.providerUserId,
                    idToken: profile.idToken,
                    email: profile.email,
                    firstName: profile.firstName.isEmpty ? fallbackFirstName : profile.firstName,
                    lastName: profile.lastName.isEmpty ? fallbackLastName : profile.lastName,
                    phone: state.phone,
                    testify: state.testify
                )
                state.loading = false
                ReportedAnalytics.logLogin(method: profile.provider)
                onSuccess()
            } catch {
                print("ReportedAuth: social registration failed \(error)")
                state.loading = false
                state.error = "There was an error signing in. Please try again."
            }
        }
    }
}

@MainActor
final class ReportsViewModel: ObservableObject {
    @Published private(set) var state = ReportsState()
    private var activeFilter: ReportFilter?

    var reports: [ReportSummary] { state.reports }
    var reportDetails: [String: ReportSummary] { state.reportDetails }
    var detailLoadingIds: Set<String> { state.detailLoadingIds }
    var loading: Bool { state.loading }
    var loadingMore: Bool { state.loadingMore }
    var hasMore: Bool { state.hasMore }
    var deletingReportKeys: Set<String> { state.deletingReportKeys }
    var error: String? { state.error }
    var mode: ReportsMode? { state.mode }
    var licenseQuery: String { state.licenseQuery }
    var startDate: Date { state.startDate }
    var endDate: Date { state.endDate }
    var usesStartDate: Bool { state.usesStartDate }
    var usesEndDate: Bool { state.usesEndDate }

    func onAction(_ action: ReportsAction) {
        switch action {
        case .listChosen:
            chooseList()
        case .searchChosen:
            chooseSearch()
        case .searchPressed:
            search()
        case .nextPageRequested(let report):
            loadNextPageIfNeeded(current: report)
        case .detailRequested(let report):
            loadDetail(for: report)
        case .reportOpened(let objectId):
            openReport(objectId: objectId)
        case .reportDeleted(let report):
            delete(report: report)
        case .licenseChanged(let value):
            state.licenseQuery = value
        case .startDateChanged(let value):
            state.startDate = value
        case .endDateChanged(let value):
            state.endDate = value
        case .usesStartDateChanged(let value):
            state.usesStartDate = value
        case .usesEndDateChanged(let value):
            state.usesEndDate = value
        }
    }

    func chooseList() {
        ReportedAnalytics.logReportsList()
        state.mode = .list
        state.reports = []
        state.reportDetails = [:]
        state.hasMore = false
        state.nextSkip = 0
        state.error = nil
        activeFilter = nil
        load(filter: nil, append: false)
    }

    func chooseSearch() {
        state.mode = .search
        state.reports = []
        state.reportDetails = [:]
        state.hasMore = false
        state.nextSkip = 0
        activeFilter = nil
        state.error = nil
    }

    func search() {
        let filter = ReportFilter(
            keywords: "",
            srid: "",
            complaints: [],
            whenDescription: "",
            locationDescription: "",
            license: state.licenseQuery.trimmingCharacters(in: .whitespacesAndNewlines),
            startDateIso: state.usesStartDate ? state.startDate.ISO8601Format() : "",
            endDateIso: state.usesEndDate ? state.endDate.ISO8601Format() : ""
        )
        ReportedAnalytics.logReportsSearch(
            hasLicense: !filter.license.isEmpty,
            hasStartDate: !filter.startDateIso.isEmpty,
            hasEndDate: !filter.endDateIso.isEmpty
        )
        activeFilter = filter
        load(filter: filter, append: false)
    }

    func loadNextPageIfNeeded(current report: ReportSummary) {
        guard state.hasMore, !state.loading, !state.loadingMore else { return }
        guard state.reports.suffix(5).contains(where: { reportKey($0) == reportKey(report) }) else { return }
        load(filter: activeFilter, append: true)
    }

    private func load(filter: ReportFilter?, append: Bool) {
        if append {
            state.loadingMore = true
        } else {
            state.loading = true
        }
        state.error = nil
        let skip = append ? state.nextSkip : 0
        Task {
            do {
                let page = try await SharedBridge.shared.container.fetchReportsUseCase.execute(filter: filter, skip: skip, forCurrentUser: true)
                if append {
                    let existingKeys = Set(state.reports.map(reportKey))
                    state.reports.append(contentsOf: page.reports.filter { !existingKeys.contains(reportKey($0)) })
                } else {
                    state.reports = page.reports
                    state.reportDetails = [:]
                    state.detailLoadingIds = []
                }
                state.hasMore = page.hasMore
                state.nextSkip = Int32(state.reports.count)
                state.loading = false
                state.loadingMore = false
            } catch {
                state.error = error.localizedDescription
                state.loading = false
                state.loadingMore = false
            }
        }
    }

    private func reportKey(_ report: ReportSummary) -> String {
        report.objectId.isEmpty ? "\(report.id)" : report.objectId
    }

    func loadDetail(for report: ReportSummary) {
        let objectId = report.objectId
        guard !objectId.isEmpty, state.reportDetails[objectId] == nil, !state.detailLoadingIds.contains(objectId) else { return }
        state.detailLoadingIds.insert(objectId)
        Task {
            do {
                let detail = try await SharedBridge.shared.container.fetchReportDetailUseCase.execute(objectId: objectId)
                state.reportDetails[objectId] = detail
                state.detailLoadingIds.remove(objectId)
            } catch {
                state.error = error.localizedDescription
                state.detailLoadingIds.remove(objectId)
            }
        }
    }

    func openReport(objectId: String) {
        guard !objectId.isEmpty else { return }
        state.mode = .list
        state.loading = true
        state.loadingMore = false
        state.error = nil
        state.detailLoadingIds.insert(objectId)
        Task {
            do {
                let detail = try await SharedBridge.shared.container.fetchReportDetailUseCase.execute(objectId: objectId)
                let existingKeys = Set(state.reports.map(reportKey))
                if existingKeys.contains(reportKey(detail)) {
                    state.reports = state.reports.map { reportKey($0) == reportKey(detail) ? detail : $0 }
                } else {
                    state.reports.insert(detail, at: 0)
                }
                state.reportDetails[objectId] = detail
                state.detailLoadingIds.remove(objectId)
                state.nextSkip = Int32(state.reports.count)
                state.loading = false
            } catch {
                state.error = error.localizedDescription
                state.detailLoadingIds.remove(objectId)
                state.loading = false
            }
        }
    }

    func delete(report: ReportSummary) {
        let key = reportKey(report)
        guard report.canDelete, !state.deletingReportKeys.contains(key) else { return }
        state.deletingReportKeys.insert(key)
        state.error = nil
        Task {
            do {
                try await SharedBridge.shared.container.deleteReportUseCase.execute(report: report)
                state.reports.removeAll { reportKey($0) == key }
                state.reportDetails.removeValue(forKey: report.objectId)
                state.detailLoadingIds.remove(report.objectId)
                state.deletingReportKeys.remove(key)
            } catch {
                state.error = error.localizedDescription
                state.deletingReportKeys.remove(key)
            }
        }
    }
}

@MainActor
final class ProfileViewModel: ObservableObject {
    @Published private(set) var state = ProfileState()

    func onAction(_ action: ProfileAction) {
        switch action {
        case .load:
            load()
        case .toggleEditing:
            toggleEditing()
        case .save:
            save()
        case .themeModeChanged(let mode):
            setThemeMode(mode)
        case .fieldsChanged(let firstName, let lastName, let phone, let email, let testify):
            update(
                firstName: firstName,
                lastName: lastName,
                phone: phone,
                email: email,
                testify: testify
            )
        }
    }

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
                state.testify = session.testify
            }
        }
    }

    func update(
        firstName: String? = nil,
        lastName: String? = nil,
        phone: String? = nil,
        email: String? = nil,
        testify: Bool? = nil
    ) {
        if let firstName { state.firstName = firstName }
        if let lastName { state.lastName = lastName }
        if let phone { state.phone = phone }
        if let email { state.email = email }
        if let testify { state.testify = testify }
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
                    lastName: state.lastName,
                    testify: state.testify
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

    func onAction(_ action: ThemeAction) {
        switch action {
        case .load:
            load()
        case .modeChanged(let mode):
            update(mode)
        }
    }

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
    @Published private(set) var submittedReportObjectId: String?
    private var remoteConfigObserver: NSObjectProtocol?
    private let maxSubmissionMediaCount = 3
    private let maxSubmissionVideoCount = 1
    private let maxSubmissionMediaMessage = "You can attach up to 3 photos or videos."
    private let maxSubmissionVideoMessage = "You can attach no more than 1 video."
    private let duplicateSubmissionMediaMessage = "That photo or video is already attached."

    var remainingMediaSlots: Int {
        max(0, maxSubmissionMediaCount - mediaItems.count)
    }

    init() {
        remoteConfigObserver = NotificationCenter.default.addObserver(
            forName: .reportedRemoteConfigUpdated,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            self?.refreshRemoteConfigValues()
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
        case .metadataApplied(let occurredAtIso, let photoOccurredAtIso, let latitude, let longitude, let inferredState, let inferredAddress):
            applyDetectedMetadata(
                occurredAtIso: occurredAtIso,
                photoOccurredAtIso: photoOccurredAtIso,
                latitude: latitude,
                longitude: longitude,
                inferredState: inferredState,
                inferredAddress: inferredAddress
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
                    state.description = Self.cleanDraftText(draft.description_)
                    state.notes = Self.cleanDraftText(draft.notes)
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
                        return ComposerState.PlateCandidate(
                            plate: $0.plate,
                            confidence: Double($0.confidence),
                            rawPlateText: $0.rawPlateText,
                            wasPlateCorrected: $0.wasPlateCorrected,
                            state: $0.state,
                            stateConfidence: $0.stateConfidence?.doubleValue,
                            plateType: $0.plateType,
                            plateTypeLabel: $0.plateTypeLabel,
                            bounds: bounds,
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
        if let mediaError = mediaLimitError(for: media, replacingPrimary: true) {
            state.validationErrors.media = mediaError
            return
        }
        state.pendingMediaSelection = media
        state.complaintSheetOpen = true
    }

    func confirmPendingComplaint(_ complaintId: String) {
        guard let pending = state.pendingMediaSelection else { return }
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
        persistDraft()
    }

    @discardableResult
    func addExtraMedia(_ media: ComposerState.SubmissionMedia) -> Bool {
        if let mediaError = mediaLimitError(for: media, replacingPrimary: false) {
            state.validationErrors.media = mediaError
            return false
        }
        state.extraMedia.append(media)
        state.validationErrors.media = nil
        persistDraft()
        return true
    }

    func markMediaLimitReached() {
        state.validationErrors.media = maxSubmissionMediaMessage
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
        let detectedState = candidates.first { $0.plate == inferredPlate }?.state ?? candidates.first?.state
        state.detectingPlates = false
        state.detectionMessage = nil
        state.detectionResultMessage = nil
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
        photoOccurredAtIso: String? = nil,
        latitude: Double? = nil,
        longitude: Double? = nil,
        inferredState: String? = nil,
        inferredAddress: String? = nil
    ) {
        if let occurredAtIso { state.occurredAtIso = occurredAtIso }
        if let photoOccurredAtIso { state.photoOccurredAtIso = photoOccurredAtIso }
        if Self.isUsableCoordinate(latitude: latitude, longitude: longitude) {
            state.latitude = latitude
            state.longitude = longitude
        }
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
        let draftMediaURLs = currentDraftMediaURLs()
        Task {
            try? await SharedBridge.shared.container.clearDraftUseCase.execute()
        }
        PersistentMediaStore.deleteStoredMedia(draftMediaURLs)
        state = ComposerState(draftLoaded: true)
    }

    private func persistDraft() {
        let draft = ReportDraft(
            plate: state.plate,
            plateRegion: state.plateRegion,
            address: state.addressQuery.isEmpty ? state.address : state.addressQuery,
            description: Self.cleanDraftText(state.description),
            notes: Self.cleanDraftText(state.notes),
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
                let videoFramePreviewURL = Self.persistedVideoFramePreviewURL(for: $0)
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
                    cornerPoints: [],
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
            vehicleModel: nil
        )
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

    private static func persistedVideoFramePreviewURL(for candidate: ComposerState.PlateCandidate) -> URL? {
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

    private static func cleanDraftText(_ value: String) -> String {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.hasPrefix("ReportDraft(") || trimmed.contains("primaryMedia=DraftMedia(") {
            return ""
        }
        return value
    }

    private static func isUsableCoordinate(latitude: Double?, longitude: Double?) -> Bool {
        guard let latitude, let longitude else { return false }
        guard latitude.isFinite, longitude.isFinite else { return false }
        guard abs(latitude) <= 90, abs(longitude) <= 180 else { return false }
        return abs(latitude) > 0.000001 || abs(longitude) > 0.000001
    }

    func submit() {
        print("ReportedSubmit: submit requested; validating")
        guard prepareSubmit() else { return }
        state.loading = true
        state.submitProgress = 0
        state.submitMessage = "Preparing report"
        state.validationErrors = ComposerState.ValidationErrors()
        let submittedMedia = [state.primaryMedia].compactMap { $0 } + state.extraMedia
        let submittedMediaURLs = submittedMedia.map(\.fileURL) + state.plateCandidates.compactMap(Self.persistedVideoFramePreviewURL)
        let submitAddress = state.addressQuery.isEmpty ? state.address : state.addressQuery
        let submitCounty = ReportedAnalytics.county(from: submitAddress)
        let submitPlateRegion = state.plateRegion
        let submitComplaintCount = state.selectedComplaintIds.count
        let submitMediaCount = submittedMedia.count
        let submitHasVideo = submittedMedia.contains { $0.isVideo }
        print("ReportedSubmit: validated report; media=\(submittedMedia.count) plate=\(state.plate)/\(state.plateRegion) complaintIds=\(state.selectedComplaintIds)")
        Task {
            do {
                await MainActor.run {
                    state.submitProgress = 0.02
                    state.submitMessage = "Uploading media"
                }
                let mediaFiles = try await ParseMediaUploader.uploadAll(submittedMedia) { progress in
                    await MainActor.run {
                        self.state.submitProgress = min(max(progress.overallFraction * 0.82, 0), 0.82)
                        self.state.submitMessage = "\(progress.message) (\(Int(progress.fileFraction * 100))%)"
                    }
                }
                print("ReportedSubmit: media upload complete; parseFiles=\(mediaFiles.count)")
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
                    vehicleColor: nil,
                    vehicleMake: nil,
                    vehicleModel: nil,
                    mediaUrls: [],
                    mediaFiles: mediaFiles
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
                PersistentMediaStore.deleteStoredMedia(submittedMediaURLs)
                ReportedAnalytics.logSubmitReport(
                    county: submitCounty,
                    plateRegion: submitPlateRegion,
                    complaintCount: submitComplaintCount,
                    mediaCount: submitMediaCount,
                    hasVideo: submitHasVideo
                )
                print("ReportedSubmit: submit flow finished successfully")
                state = ComposerState()
                submittedReportObjectId = submittedObjectId
            } catch {
                print("ReportedSubmit: submit flow failed \(error)")
                state.loading = false
                state.submitProgress = nil
                state.submitMessage = nil
                state.error = "There was an error submitting your report. Please try again."
            }
        }
    }

    func consumeSubmittedReport() {
        submittedReportObjectId = nil
    }

    private func currentDraftMediaURLs() -> [URL] {
        let mediaURLs = [state.primaryMedia].compactMap { $0?.fileURL } + state.extraMedia.map(\.fileURL)
        let framePreviewURLs = state.plateCandidates.compactMap(Self.persistedVideoFramePreviewURL)
        return mediaURLs + framePreviewURLs
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
                if state.plate.count > 8 {
                    return "License plate must be 8 characters or fewer."
                }
                return nil
            }(),
            plateRegion: state.plateRegion.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Choose a state." : nil,
            address: state.addressQuery.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && state.address.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Enter or choose an address." : nil,
            occurredAt: state.occurredAtIso.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Choose when this happened." : nil
        )
    }

    private static func normalizedPlateInput(_ value: String) -> String {
        let normalized = value
            .uppercased()
            .filter { $0.isLetter || $0.isNumber }
        return String(normalized.prefix(8))
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
        if existingMedia.count + 1 > maxSubmissionMediaCount {
            return maxSubmissionMediaMessage
        }
        if media.isVideo && existingMedia.filter(\.isVideo).count >= maxSubmissionVideoCount {
            return maxSubmissionVideoMessage
        }
        return nil
    }
}

enum ParseMediaUploader {
    struct UploadProgress {
        let completedBytes: Int64
        let totalBytes: Int64
        let currentFileIndex: Int
        let totalFiles: Int
        let message: String

        var fileFraction: Double {
            guard totalBytes > 0 else { return 0 }
            return min(max(Double(completedBytes) / Double(totalBytes), 0), 1)
        }

        var overallFraction: Double {
            let count = max(totalFiles, 1)
            return min(max((Double(currentFileIndex) + fileFraction) / Double(count), 0), 1)
        }
    }

    static func uploadAll(
        _ media: [ComposerState.SubmissionMedia],
        onProgress: @escaping (UploadProgress) async -> Void = { _ in }
    ) async throws -> [SubmitReportMediaFile] {
        print("ReportedSubmit: media upload batch starting; count=\(media.count)")
        var uploaded: [SubmitReportMediaFile] = []
        for (index, item) in media.enumerated() {
            if let file = try await upload(item, index: index, totalFiles: media.count, onProgress: onProgress) {
                uploaded.append(file)
            }
        }
        print("ReportedSubmit: media upload batch finished; uploaded=\(uploaded.count)/\(media.count)")
        return uploaded
    }

    private static func upload(
        _ media: ComposerState.SubmissionMedia,
        index: Int,
        totalFiles: Int,
        onProgress: @escaping (UploadProgress) async -> Void
    ) async throws -> SubmitReportMediaFile? {
        guard let url = try await uploadUrl(media, index: index, totalFiles: totalFiles, onProgress: onProgress) else { return nil }
        return SubmitReportMediaFile(url: url, isVideo: media.isVideo)
    }

    private static func uploadUrl(
        _ media: ComposerState.SubmissionMedia,
        index: Int,
        totalFiles: Int,
        onProgress: @escaping (UploadProgress) async -> Void
    ) async throws -> String? {
        let payload = try await makePayload(for: media)
        let filename = sanitizeFilename(payload.filename)
        let encodedName = filename.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? filename
        guard let uploadURL = URL(string: "\(parseBaseUrl())/files/\(encodedName)") else {
            throw NSError(
                domain: "Reported.ParseMediaUploader",
                code: 1,
                userInfo: [NSLocalizedDescriptionKey: "Invalid Parse upload URL."]
            )
        }

        var request = URLRequest(url: uploadURL)
        request.httpMethod = "POST"
        request.timeoutInterval = 60
        request.setValue(parseApplicationId(), forHTTPHeaderField: "X-Parse-Application-Id")
        request.setValue(parseJavascriptKey(), forHTTPHeaderField: "X-Parse-JavaScript-Key")
        request.setValue(payload.mimeType, forHTTPHeaderField: "Content-Type")

        print("ReportedSubmit: uploading media \(index + 1)/\(totalFiles) name=\(filename) type=\(payload.mimeType) bytes=\(payload.data.count) compressed=\(payload.compressed)")
        let (responseData, response) = try await upload(
            request: request,
            data: payload.data,
            index: index,
            totalFiles: totalFiles,
            onProgress: onProgress
        )
        guard let httpResponse = response as? HTTPURLResponse else {
            throw NSError(
                domain: "Reported.ParseMediaUploader",
                code: 2,
                userInfo: [NSLocalizedDescriptionKey: "Parse media upload returned an invalid response."]
            )
        }
        print("ReportedSubmit: media upload response \(httpResponse.statusCode); name=\(filename)")
        guard (200...299).contains(httpResponse.statusCode) else {
            let body = String(data: responseData, encoding: .utf8) ?? ""
            throw NSError(
                domain: "Reported.ParseMediaUploader",
                code: httpResponse.statusCode,
                userInfo: [NSLocalizedDescriptionKey: "Media upload failed: \(httpResponse.statusCode) \(body)"]
            )
        }
        let json = try JSONSerialization.jsonObject(with: responseData) as? [String: Any]
        return json?["url"] as? String
    }

    private static func upload(
        request: URLRequest,
        data: Data,
        index: Int,
        totalFiles: Int,
        onProgress: @escaping (UploadProgress) async -> Void
    ) async throws -> (Data, URLResponse) {
        let delegate = UploadProgressDelegate { sent, total in
            Task {
                await onProgress(
                    UploadProgress(
                        completedBytes: sent,
                        totalBytes: total > 0 ? total : Int64(data.count),
                        currentFileIndex: index,
                        totalFiles: totalFiles,
                        message: "Uploading media \(index + 1) of \(totalFiles)"
                    )
                )
            }
        }
        let session = URLSession(configuration: .default, delegate: delegate, delegateQueue: nil)
        defer { session.invalidateAndCancel() }
        return try await withCheckedThrowingContinuation { continuation in
            let task = session.uploadTask(with: request, from: data) { responseData, response, error in
                if let error {
                    continuation.resume(throwing: error)
                    return
                }
                guard let responseData, let response else {
                    continuation.resume(
                        throwing: NSError(
                            domain: "Reported.ParseMediaUploader",
                            code: 3,
                            userInfo: [NSLocalizedDescriptionKey: "Parse media upload returned no response."]
                        )
                    )
                    return
                }
                continuation.resume(returning: (responseData, response))
            }
            task.resume()
        }
    }

    private static func makePayload(for media: ComposerState.SubmissionMedia) async throws -> UploadPayload {
        let original = try await Task.detached(priority: .utility) {
            try Data(contentsOf: media.fileURL)
        }.value
        let rawName = media.displayName.isEmpty ? defaultFilename(for: media) : media.displayName
        guard !media.isVideo else {
            return UploadPayload(
                data: original,
                filename: rawName,
                mimeType: mimeType(for: media),
                compressed: false
            )
        }
        return makeJpegPayload(original: original, rawName: rawName) ?? UploadPayload(
            data: original,
            filename: rawName,
            mimeType: mimeType(for: media),
            compressed: false
        )
    }

    private static func makeJpegPayload(
        original: Data,
        rawName: String
    ) -> UploadPayload? {
        guard
            let source = CGImageSourceCreateWithData(original as CFData, nil),
            let image = UIImage(data: original)?.cgImage
        else {
            return nil
        }
        let output = NSMutableData()
        guard let destination = CGImageDestinationCreateWithData(
            output as CFMutableData,
            UTType.jpeg.identifier as CFString,
            1,
            nil
        ) else {
            return nil
        }
        var properties = (CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any]) ?? [:]
        properties[kCGImageDestinationLossyCompressionQuality] = 0.84
        CGImageDestinationAddImage(destination, image, properties as CFDictionary)
        guard CGImageDestinationFinalize(destination) else {
            return nil
        }
        let baseName = rawName.replacingOccurrences(
            of: "\\.[^.]+$",
            with: "",
            options: .regularExpression
        )
        print("ReportedSubmit: JPEG image prepared; original=\(original.count) jpeg=\(output.length)")
        return UploadPayload(
            data: output as Data,
            filename: "\(baseName).jpg",
            mimeType: "image/jpeg",
            compressed: true
        )
    }

    private static func defaultFilename(for media: ComposerState.SubmissionMedia) -> String {
        let lastPath = media.fileURL.lastPathComponent
        if !lastPath.isEmpty { return lastPath }
        return media.isVideo ? "video.mov" : "photo.jpg"
    }

    private static func sanitizeFilename(_ value: String) -> String {
        let fallback = value.isEmpty ? "media" : value
        return fallback.replacingOccurrences(
            of: "[^A-Za-z0-9._-]",
            with: "_",
            options: .regularExpression
        )
    }

    private static func mimeType(for media: ComposerState.SubmissionMedia) -> String {
        if let type = UTType(filenameExtension: media.fileURL.pathExtension),
           let mimeType = type.preferredMIMEType {
            return mimeType
        }
        return media.isVideo ? "video/quicktime" : "image/jpeg"
    }

    private static func parseBaseUrl() -> String {
        let fallback = ProcessInfo.processInfo.environment["REPORTED_PARSE_SERVER_URL"] ?? "https://parseapi.back4app.com"
        let raw = RemoteConfigOverrides.shared.parseServerUrl(fallback: fallback)
        let trimmed = raw.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        if trimmed.hasSuffix("/parse") || trimmed.contains("parseapi.back4app.com") {
            return trimmed
        }
        return "\(trimmed)/parse"
    }

    private static func parseApplicationId() -> String {
        ProcessInfo.processInfo.environment["REPORTED_PARSE_APPLICATION_ID"] ?? "jkAZF8ojV4vOGnhSBjdwiMWBKpWML5tM4SWGKgOV"
    }

    private static func parseJavascriptKey() -> String {
        ProcessInfo.processInfo.environment["REPORTED_PARSE_JAVASCRIPT_KEY"] ?? "LeBKOerWTXGBGRLE0yvg2bXa5RRv4e8PuC6INEFA"
    }

    private struct UploadPayload {
        let data: Data
        let filename: String
        let mimeType: String
        let compressed: Bool
    }

    private final class UploadProgressDelegate: NSObject, URLSessionTaskDelegate {
        private let onProgress: (Int64, Int64) -> Void

        init(onProgress: @escaping (Int64, Int64) -> Void) {
            self.onProgress = onProgress
        }

        func urlSession(
            _ session: URLSession,
            task: URLSessionTask,
            didSendBodyData bytesSent: Int64,
            totalBytesSent: Int64,
            totalBytesExpectedToSend: Int64
        ) {
            onProgress(totalBytesSent, totalBytesExpectedToSend)
        }
    }
}
