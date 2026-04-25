import AVFoundation
import CoreGraphics
import FirebaseAnalytics
import ImageIO
import PhotosUI
import SharedCore
import SwiftUI
import UniformTypeIdentifiers
import UIKit

private let preferredPlateRegions = ["NY", "NJ", "CT", "PA", "FL", "OTHER"]
private let allPlateRegions = [
    "AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "FL", "GA",
    "HI", "ID", "IL", "IN", "IA", "KS", "KY", "LA", "ME", "MD",
    "MA", "MI", "MN", "MS", "MO", "MT", "NE", "NV", "NH", "NJ",
    "NM", "NY", "NC", "ND", "OH", "OK", "OR", "PA", "RI", "SC",
    "SD", "TN", "TX", "UT", "VT", "VA", "WA", "WV", "WI", "WY"
]

struct ContentView: View {
    @StateObject private var sessionViewModel = SessionViewModel()
    @StateObject private var themeViewModel = ThemeViewModel()
    @State private var authRoute: AuthRoute?
    @State private var sharedMediaImportId: String?

    var body: some View {
        Group {
            if sessionViewModel.state.session?.isAuthorized == true || sessionViewModel.state.isGuest {
                MainShellView(
                    sessionViewModel: sessionViewModel,
                    themeViewModel: themeViewModel,
                    onRequireLogin: { authRoute = .login },
                    sharedMediaImportId: $sharedMediaImportId
                )
            } else {
                AuthFlowView(sessionViewModel: sessionViewModel)
            }
        }
        .task {
            sessionViewModel.load()
            themeViewModel.load()
        }
        .onOpenURL { url in
            guard url.scheme == "reported",
                  url.host == "share",
                  let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
                  let importId = components.queryItems?.first(where: { $0.name == "import" })?.value,
                  !importId.isEmpty else {
                return
            }
            sharedMediaImportId = importId
            if sessionViewModel.state.session?.isAuthorized != true && !sessionViewModel.state.isGuest {
                sessionViewModel.continueAsGuest()
            }
        }
        .preferredColorScheme(appPreferredColorScheme(for: themeViewModel.mode))
        .sheet(item: $authRoute) { route in
            AuthFlowView(
                sessionViewModel: sessionViewModel,
                initialRoute: route,
                allowSkip: false,
                onDismiss: { authRoute = nil }
            )
        }
    }
}

enum AuthRoute: String, Identifiable {
    case splash
    case login
    case register

    var id: String { rawValue }
}

struct AuthFlowView: View {
    @ObservedObject var sessionViewModel: SessionViewModel
    @State private var path: [AuthRoute]
    let allowSkip: Bool
    let onDismiss: (() -> Void)?

    init(
        sessionViewModel: SessionViewModel,
        initialRoute: AuthRoute = .splash,
        allowSkip: Bool = true,
        onDismiss: (() -> Void)? = nil
    ) {
        self.sessionViewModel = sessionViewModel
        self.allowSkip = allowSkip
        self.onDismiss = onDismiss
        _path = State(initialValue: initialRoute == .splash ? [] : [initialRoute])
    }

    var body: some View {
        NavigationStack(path: $path) {
            VStack(spacing: 16) {
                Spacer()
                ScreenCard {
                    if let onDismiss {
                        HStack {
                            Spacer()
                            TertiaryButton(title: "Close", action: onDismiss)
                                .frame(maxWidth: .none)
                        }
                    }
                    PrimaryButton(title: "Login") { path.append(.login) }
                    SecondaryButton(title: "Register") { path.append(.register) }
                    if allowSkip {
                        TertiaryButton(title: "Skip") { sessionViewModel.continueAsGuest() }
                    }
                }
            }
            .padding()
            .navigationDestination(for: AuthRoute.self) { route in
                switch route {
                case .login:
                    LoginScreen {
                        sessionViewModel.didAuthenticate()
                        onDismiss?()
                    } onRegister: {
                        path.append(.register)
                    } onBack: {
                        if !path.isEmpty {
                            path.removeLast()
                        }
                    } onDismiss: {
                        onDismiss?()
                    }
                case .register:
                    RegisterScreen {
                        sessionViewModel.didAuthenticate()
                        onDismiss?()
                    } onLogin: {
                        path.append(.login)
                    } onBack: {
                        if !path.isEmpty {
                            path.removeLast()
                        }
                    } onDismiss: {
                        onDismiss?()
                    }
                case .splash:
                    EmptyView()
                }
            }
        }
    }
}

enum MainShellDestination: Int, CaseIterable {
    case report
    case reports
    case profile

    var title: String {
        switch self {
        case .report: return "New Report"
        case .reports: return "My Reports"
        case .profile: return "Profile"
        }
    }

    var systemImage: String {
        switch self {
        case .report: return "plus.square"
        case .reports: return "list.bullet.rectangle"
        case .profile: return "person.crop.circle"
        }
    }
}

struct MainShellView: View {
    @ObservedObject var sessionViewModel: SessionViewModel
    @ObservedObject var themeViewModel: ThemeViewModel
    let onRequireLogin: () -> Void
    @Binding var sharedMediaImportId: String?
    @State private var selection: MainShellDestination = .report

    var body: some View {
        HStack(spacing: 0) {
            LeftGliderNavView(selection: $selection) {
                let isAuthorized = sessionViewModel.state.session?.isAuthorized == true
                if !isAuthorized && selection != .report {
                    selection = .report
                    onRequireLogin()
                }
            }
            Group {
                switch selection {
                case .report:
                    ComposerScreen(
                        isAuthorized: sessionViewModel.state.session?.isAuthorized == true,
                        onRequireLogin: onRequireLogin,
                        sharedMediaImportId: $sharedMediaImportId
                    )
                case .reports:
                    ReportsScreen(
                        isAuthorized: sessionViewModel.state.session?.isAuthorized == true,
                        onRequireLogin: onRequireLogin
                    )
                case .profile:
                    ProfileScreen(
                        isAuthorized: sessionViewModel.state.session?.isAuthorized == true,
                        onRequireLogin: onRequireLogin,
                        onLogout: { sessionViewModel.logout() },
                        onThemeModeSelected: { themeViewModel.update($0) }
                    )
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .onAppear {
            logScreenView(selection.title)
        }
        .onChange(of: selection) { _, destination in
            logScreenView(destination.title)
        }
        .onChange(of: sharedMediaImportId) { _, importId in
            if importId != nil {
                selection = .report
            }
        }
    }

    private func logScreenView(_ name: String) {
        Analytics.logEvent(AnalyticsEventScreenView, parameters: [
            AnalyticsParameterScreenName: name,
            AnalyticsParameterScreenClass: name
        ])
    }
}

struct LeftGliderNavView: View {
    @Binding var selection: MainShellDestination
    let onSelectionAttempt: () -> Void

    var body: some View {
        let destinations = MainShellDestination.allCases
        let selectedIndex = destinations.firstIndex(of: selection) ?? 0

        ZStack(alignment: .top) {
            RoundedRectangle(cornerRadius: 18)
                .fill(Color.reportedOrange.opacity(0.12))
                .frame(width: 96, height: 72)
                .offset(y: CGFloat(selectedIndex) * 84 + 16)
                .animation(.spring(response: 0.28, dampingFraction: 0.84), value: selection)

            VStack(spacing: 12) {
                ForEach(destinations, id: \.self) { destination in
                    Button {
                        selection = destination
                        onSelectionAttempt()
                    } label: {
                        VStack(spacing: 8) {
                            Image(systemName: destination.systemImage)
                                .font(.title3)
                            Text(destination.title)
                                .font(.caption)
                                .multilineTextAlignment(.center)
                        }
                        .foregroundStyle(selection == destination ? Color.reportedOrange : .secondary)
                        .frame(width: 96, height: 72)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.top, 16)
        }
        .frame(width: 120)
        .frame(maxHeight: .infinity, alignment: .top)
        .padding(.horizontal, 12)
        .background(Color(.secondarySystemBackground).opacity(0.7))
    }
}

struct LoginScreen: View {
    @StateObject private var viewModel = LoginViewModel()
    let onSuccess: () -> Void
    let onRegister: () -> Void
    var onBack: (() -> Void)? = nil
    var onDismiss: (() -> Void)? = nil

    var body: some View {
        ScrollView {
            ScreenCard(title: "Login") {
                if onBack != nil || onDismiss != nil {
                    HStack {
                        if let onBack {
                            TertiaryButton(title: "Back", action: onBack)
                                .frame(maxWidth: .none)
                        }
                        Spacer()
                        if let onDismiss {
                            TertiaryButton(title: "Close", action: onDismiss)
                                .frame(maxWidth: .none)
                        }
                    }
                }
                if let error = viewModel.state.error { MessageView(text: error) }
                InputField(title: "Email", text: Binding(
                    get: { viewModel.state.email },
                    set: { viewModel.update(email: $0) }
                ))
                InputField(title: "Password", text: Binding(
                    get: { viewModel.state.password },
                    set: { viewModel.update(password: $0) }
                ))
                PrimaryButton(title: viewModel.state.loading ? "Logging In..." : "Login") {
                    viewModel.login(onSuccess: onSuccess)
                }
                Button("Forgot Password?", action: viewModel.forgotPassword)
                    .foregroundStyle(Color.reportedOrange)
                Button("Need an account? Register", action: onRegister)
                    .foregroundStyle(Color.reportedOrange)
            }
            .padding()
        }
    }
}

struct RegisterScreen: View {
    @StateObject private var viewModel = RegisterViewModel()
    let onSuccess: () -> Void
    let onLogin: () -> Void
    var onBack: (() -> Void)? = nil
    var onDismiss: (() -> Void)? = nil

    var body: some View {
        ScrollView {
            ScreenCard(title: "Register") {
                if onBack != nil || onDismiss != nil {
                    HStack {
                        if let onBack {
                            TertiaryButton(title: "Back", action: onBack)
                                .frame(maxWidth: .none)
                        }
                        Spacer()
                        if let onDismiss {
                            TertiaryButton(title: "Close", action: onDismiss)
                                .frame(maxWidth: .none)
                        }
                    }
                }
                if let error = viewModel.state.error { MessageView(text: error) }
                InputField(title: "First Name", text: Binding(get: { viewModel.state.firstName }, set: { viewModel.update(firstName: $0) }))
                InputField(title: "Last Name", text: Binding(get: { viewModel.state.lastName }, set: { viewModel.update(lastName: $0) }))
                InputField(title: "Phone", text: Binding(get: { viewModel.state.phone }, set: { viewModel.update(phone: $0) }))
                InputField(title: "Email", text: Binding(get: { viewModel.state.email }, set: { viewModel.update(email: $0) }))
                InputField(title: "Password", text: Binding(get: { viewModel.state.password }, set: { viewModel.update(password: $0) }))
                Toggle("I'm willing to testify by phone if needed.", isOn: Binding(
                    get: { viewModel.state.testify },
                    set: { viewModel.update(testify: $0) }
                ))
                PrimaryButton(title: viewModel.state.loading ? "Creating..." : "Create Account") {
                    viewModel.register(onSuccess: onSuccess)
                }
                Button("Already registered? Login", action: onLogin)
                    .foregroundStyle(Color.reportedOrange)
            }
            .padding()
        }
    }
}

struct ComposerScreen: View {
    @StateObject private var viewModel = ComposerViewModel()
    let isAuthorized: Bool
    let onRequireLogin: () -> Void
    @Binding var sharedMediaImportId: String?
    @State private var singlePickerPresented = false
    @State private var multiPickerPresented = false
    @State private var pickedItem: PhotosPickerItem?
    @State private var pickedItems: [PhotosPickerItem] = []
    @State private var pendingComplaintId: String?
    @State private var metadataTask: Task<Void, Never>?
    @State private var videoScanTask: Task<Void, Never>?
    @State private var addressSearchTask: Task<Void, Never>?
    @State private var showPreferredPlateRegions = false
    @State private var showAllPlateRegions = false
    @State private var showComplaintChooser = false
    @State private var detectionProgressMinimized = false
    @State private var videoScanPaused = false
    @State private var videoPreviewScrubSeconds = 0.0
    private let previewScrollId = "composer-primary-media-preview"

    private let complaintColumns = [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)]
    private let complaintOptions = complaintOptionsList

    var body: some View {
        ScrollViewReader { proxy in
            let content = ScrollView {
                Group {
                    switch viewModel.state.stage {
                    case .pickMedia:
                        pickMediaContent
                    case .verify:
                        verifyContent
                    }
                }
                .padding()
            }
            content
                .task {
                    viewModel.loadDraft()
                }
                .onChange(of: viewModel.state.primaryMedia?.fileURL.path) { _, newValue in
                    guard viewModel.state.stage == .verify, newValue != nil else { return }
                    withAnimation(.easeInOut(duration: 0.25)) {
                        proxy.scrollTo(previewScrollId, anchor: .top)
                    }
                }
        }
        .overlay(alignment: .bottom) {
            detectionProgressChip
        }
        .overlay {
            detectionProgressModal
        }
        .onChange(of: viewModel.state.detectingPlates) { _, _ in
            detectionProgressMinimized = false
            if !viewModel.state.detectingPlates {
                videoScanPaused = false
            }
        }
        .onChange(of: viewModel.state.detectionFrameTimeSeconds) { _, newValue in
            if !videoScanPaused {
                videoPreviewScrubSeconds = newValue
            }
        }
        .photosPicker(
            isPresented: $singlePickerPresented,
            selection: $pickedItem,
            matching: .any(of: [.images, .videos]),
            preferredItemEncoding: .current
        )
        .photosPicker(
            isPresented: $multiPickerPresented,
            selection: $pickedItems,
            maxSelectionCount: 10,
            matching: .any(of: [.images, .videos]),
            preferredItemEncoding: .current
        )
        .onChange(of: pickedItem) { _, newItem in
            guard let newItem else { return }
            Task {
                await handlePickedItem(newItem)
                pickedItem = nil
            }
        }
        .onChange(of: pickedItems) { _, newItems in
            guard !newItems.isEmpty else { return }
            Task {
                for item in newItems {
                    await handleAdditionalPickedItem(item)
                }
                pickedItems = []
            }
        }
        .task(id: "\(sharedMediaImportId ?? "")-\(viewModel.state.draftLoaded)") {
            guard let importId = sharedMediaImportId else { return }
            guard viewModel.state.draftLoaded else { return }
            await handleSharedMediaImport(id: importId)
            sharedMediaImportId = nil
        }
        .onChange(of: viewModel.state.addressQuery) { _, newValue in
            guard viewModel.state.stage == .verify,
                  !newValue.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                  newValue != viewModel.state.address else {
                addressSearchTask?.cancel()
                viewModel.setAddressSuggestions([])
                return
            }
            addressSearchTask?.cancel()
            addressSearchTask = Task {
                viewModel.setAddressLookupLoading(true)
                try? await Task.sleep(for: .milliseconds(250))
                if Task.isCancelled { return }
                let suggestions = await searchNycAddresses(query: newValue)
                if Task.isCancelled { return }
                viewModel.setAddressSuggestions(suggestions)
            }
        }
        .sheet(
            isPresented: Binding(
                get: { viewModel.state.complaintSheetOpen },
                set: { _ in }
            )
        ) {
            NavigationStack {
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        Text("What kind of complaint is this?")
                            .font(.title2.bold())
                        LazyVGrid(columns: complaintColumns, spacing: 12) {
                            ForEach(complaintOptions) { option in
                                ComplaintMediaTile(option: option) {
                                    viewModel.confirmPendingComplaint(option.id)
                                }
                            }
                        }
                    }
                    .padding()
                }
                .interactiveDismissDisabled()
            }
            .presentationDetents([.medium, .large])
            .presentationDragIndicator(.hidden)
        }
        .alert("Process video for license plates?", isPresented: Binding(
            get: { viewModel.state.awaitingVideoProcessingDecision },
            set: { _ in }
        )) {
            Button("Process") {
                let videoMedia = viewModel.state.primaryMedia
                videoScanPaused = false
                viewModel.onVideoProcessingDecision(true)
                videoScanTask?.cancel()
                videoScanTask = Task {
                    guard let videoMedia else {
                        viewModel.finishDetection()
                        return
                    }
                    let candidates = await NativeAlprEngine.shared.detectLicensePlatesInVideo(media: videoMedia) { progress in
                        let totalFrames = max(progress.totalFrames, 1)
                        let percent = Double(progress.processedFrames) / Double(totalFrames)
                        let foundText: String
                        if progress.candidatesFound == 0 {
                            foundText = "no plates yet"
                        } else if progress.candidatesFound == 1 {
                            foundText = "1 possible plate"
                        } else {
                            foundText = "\(progress.candidatesFound) possible plates"
                        }
                        await MainActor.run {
                            viewModel.setDetectionProgress(
                                message: "Scanning frame \(progress.processedFrames)/\(totalFrames), \(foundText)",
                                progress: percent,
                                framePreview: progress.framePreview,
                                frameTimeSeconds: progress.frameTimeSeconds,
                                videoDurationSeconds: progress.durationSeconds,
                                frameCandidates: progress.frameCandidates,
                                allCandidates: progress.allCandidates
                            )
                        }
                        while await MainActor.run(body: { videoScanPaused }) {
                            try? await Task.sleep(for: .milliseconds(80))
                            if Task.isCancelled { return }
                        }
                    }
                    let topCandidate = candidates.first
                    viewModel.finishDetection(
                        candidates: candidates,
                        inferredPlate: topCandidate?.plate,
                        inferredState: topCandidate?.state
                    )
                }
            }
            Button("Skip", role: .cancel) {
                viewModel.onVideoProcessingDecision(false)
            }
        } message: {
            Text("We can scan every video frame and show the best candidate plates for you to choose from.")
        }
        .confirmationDialog("Change complaint", isPresented: $showComplaintChooser, titleVisibility: .visible) {
            ForEach(complaintOptions) { option in
                Button(option.title) {
                    viewModel.updateSelectedComplaint(option.id)
                }
            }
            Button("Cancel", role: .cancel) {}
        }
    }

    @ViewBuilder
    private var pickMediaContent: some View {
        ScreenCard {
            VStack(spacing: 24) {
                Text("Upload Photo of Complaint")
                    .font(.largeTitle.bold())
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
                Text("Pick a complaint first to preselect it, or use Upload if you want to choose the complaint after selecting media.")
                    .font(.body)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
                LazyVGrid(columns: complaintColumns, spacing: 12) {
                    ForEach(complaintOptions) { option in
                        ComplaintMediaTile(option: option) {
                            pendingComplaintId = option.id
                            singlePickerPresented = true
                        }
                    }
                    UploadMediaTile {
                        pendingComplaintId = nil
                        singlePickerPresented = true
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var detectionProgressModal: some View {
        if viewModel.state.detectingPlates && !viewModel.state.awaitingVideoProcessingDecision && !detectionProgressMinimized {
            VStack(alignment: .leading, spacing: 0) {
                HStack {
                    Text("Scanning video")
                        .font(.title2.bold())
                    Spacer()
                    Button("Minimize") {
                        detectionProgressMinimized = true
                    }
                    .font(.body.weight(.semibold))
                    .foregroundStyle(Color.reportedOrange)
                }
                .padding(.horizontal, 20)
                .padding(.top, 18)
                .padding(.bottom, 14)

                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        Text(viewModel.state.detectionMessage ?? "Detecting plates")
                            .font(.body)
                            .foregroundStyle(.secondary)
                        if let media = viewModel.state.primaryMedia {
                            VideoFrameScrubberPreview(
                                url: media.fileURL,
                                timeSeconds: videoScanPaused ? videoPreviewScrubSeconds : viewModel.state.detectionFrameTimeSeconds,
                                candidates: viewModel.state.detectionFrameCandidates,
                                selectedPlate: viewModel.state.selectedPlateCandidate,
                                onCandidateSelected: selectVideoCandidate
                            )
                            .frame(maxWidth: .infinity)
                            .frame(height: 360)
                            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                        } else if let framePreview = viewModel.state.detectionFramePreview {
                            Image(uiImage: framePreview)
                                .resizable()
                                .scaledToFit()
                                .frame(maxWidth: .infinity)
                                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                        }
                        Text("Current frame")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(.secondary)
                        HStack(spacing: 10) {
                            Button(videoScanPaused ? "Resume" : "Pause") {
                                videoScanPaused.toggle()
                                if videoScanPaused {
                                    videoPreviewScrubSeconds = viewModel.state.detectionFrameTimeSeconds
                                }
                            }
                            .font(.callout.weight(.semibold))
                            .foregroundStyle(Color.reportedOrange)
                            Text(formatVideoDuration(videoScanPaused ? videoPreviewScrubSeconds : viewModel.state.detectionFrameTimeSeconds))
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(.secondary)
                            Slider(
                                value: Binding(
                                    get: { videoScanPaused ? videoPreviewScrubSeconds : viewModel.state.detectionFrameTimeSeconds },
                                    set: { videoPreviewScrubSeconds = $0 }
                                ),
                                in: 0...max(viewModel.state.detectionVideoDurationSeconds, 1)
                            )
                            .disabled(!videoScanPaused)
                            Text(formatVideoDuration(viewModel.state.detectionVideoDurationSeconds))
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(.secondary)
                        }
                        ZStack(alignment: .leading) {
                            if !viewModel.state.detectionFrameCandidates.isEmpty {
                                ScrollView(.horizontal, showsIndicators: false) {
                                    HStack(spacing: 8) {
                                        ForEach(viewModel.state.detectionFrameCandidates) { candidate in
                                            DetectionCandidatePill(
                                                candidate: candidate,
                                                isSelected: viewModel.state.selectedPlateCandidate == candidate.plate
                                            ) {
                                                selectVideoCandidate(candidate)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        .frame(height: 44)
                        if !viewModel.state.plateCandidates.isEmpty {
                            Text("Possible plates")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(.secondary)
                            ScrollView(.vertical, showsIndicators: true) {
                                LazyVGrid(
                                    columns: [GridItem(.adaptive(minimum: 116), spacing: 8)],
                                    alignment: .leading,
                                    spacing: 8
                                ) {
                                    ForEach(viewModel.state.plateCandidates) { candidate in
                                        DetectionCandidatePill(
                                            candidate: candidate,
                                            isSelected: viewModel.state.selectedPlateCandidate == candidate.plate
                                        ) {
                                            selectVideoCandidate(candidate)
                                        }
                                    }
                                }
                                .padding(.trailing, 4)
                            }
                            .frame(maxHeight: 132)
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.bottom, 18)
                }

                VStack(alignment: .leading, spacing: 8) {
                    ProgressView(value: viewModel.state.detectionProgress)
                        .tint(Color.reportedOrange)
                    HStack {
                        Text("\(Int(max(0, min(viewModel.state.detectionProgress, 1)) * 100))%")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(.secondary)
                        Spacer()
                        Text("Tap a plate to use it")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 12)
                .padding(.bottom, 18)
                .background(Color(.systemBackground).shadow(.drop(radius: 8)))
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color(.systemBackground))
            .ignoresSafeArea(.container, edges: .bottom)
            .transition(.opacity)
        }
    }

    private func selectVideoCandidate(_ candidate: ComposerState.PlateCandidate) {
        videoScanTask?.cancel()
        viewModel.choosePlateCandidate(candidate)
        viewModel.finishDetection(
            candidates: viewModel.state.plateCandidates.isEmpty ? [candidate] : viewModel.state.plateCandidates,
            inferredPlate: candidate.plate,
            inferredState: candidate.state
        )
    }

    @ViewBuilder
    private var detectionProgressChip: some View {
        if viewModel.state.detectingPlates && !viewModel.state.awaitingVideoProcessingDecision && detectionProgressMinimized {
            Button {
                detectionProgressMinimized = false
            } label: {
                VStack(alignment: .leading, spacing: 8) {
                    HStack(spacing: 10) {
                        ProgressView(value: viewModel.state.detectionProgress)
                            .progressViewStyle(.circular)
                            .tint(Color.reportedOrange)
                            .frame(width: 22, height: 22)
                        Text(viewModel.state.detectionMessage ?? "Detecting plates")
                            .font(.callout.weight(.semibold))
                            .foregroundStyle(.primary)
                            .lineLimit(2)
                        Spacer()
                        Text("Open")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(Color.reportedOrange)
                    }
                    ProgressView(value: viewModel.state.detectionProgress)
                        .tint(Color.reportedOrange)
                }
                .padding(14)
                .background(Color(.systemBackground))
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                .shadow(radius: 10)
                .padding(.horizontal, 16)
                .padding(.bottom, 12)
            }
            .buttonStyle(.plain)
        }
    }

    @ViewBuilder
    private var verifyContent: some View {
        ScreenCard(title: "New Report") {
            let selectedCandidate = viewModel.state.plateCandidates.first { $0.plate == viewModel.state.selectedPlateCandidate }
                ?? viewModel.state.plateCandidates.first
            if let error = viewModel.state.error {
                MessageView(text: error)
            }
            if let media = viewModel.state.primaryMedia {
                PrimarySubmissionPreview(media: media, selectedCandidate: selectedCandidate)
                    .id(previewScrollId)
            }
            if let detectionResultMessage = viewModel.state.detectionResultMessage {
                MessageView(text: detectionResultMessage)
            }
            if !viewModel.state.plateCandidates.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 10) {
                        ForEach(viewModel.state.plateCandidates) { candidate in
                            PlateCandidateChip(
                                candidate: candidate,
                                isSelected: viewModel.state.selectedPlateCandidate == candidate.plate
                            ) {
                                viewModel.choosePlateCandidate(candidate)
                            }
                        }
                    }
                }
            }
            verifyFormContent
        }
    }

    @ViewBuilder
    private var verifyFormContent: some View {
        HStack(alignment: .top, spacing: 12) {
            InputField(
                title: "Plate",
                text: Binding(get: { viewModel.state.plate }, set: { viewModel.update(plate: $0) }),
                isError: viewModel.state.validationErrors.plate != nil
            )
            PickerField(
                title: "State",
                value: viewModel.state.plateRegion,
                isError: viewModel.state.validationErrors.plateRegion != nil
            ) {
                showPreferredPlateRegions = true
            }
            .overlay(alignment: .topLeading) {
                if showPreferredPlateRegions || showAllPlateRegions {
                    let options = showAllPlateRegions ? allPlateRegions : preferredPlateRegions
                    PlateRegionPopup(options: options, selectedValue: viewModel.state.plateRegion) { option in
                        if !showAllPlateRegions && option == "OTHER" {
                            showPreferredPlateRegions = false
                            showAllPlateRegions = true
                        } else {
                            viewModel.update(plateRegion: option)
                            showPreferredPlateRegions = false
                            showAllPlateRegions = false
                        }
                    } onDismiss: {
                        showPreferredPlateRegions = false
                        showAllPlateRegions = false
                    }
                    .offset(y: -170)
                }
            }
            .frame(maxWidth: 96)
        }
        InputField(
            title: "Address",
            text: Binding(
                get: { viewModel.state.addressQuery },
                set: { viewModel.updateAddressQuery($0) }
            ),
            isError: viewModel.state.validationErrors.address != nil,
            onClear: {
                viewModel.updateAddressQuery("")
            }
        )
        if viewModel.state.lookupInFlight {
            HStack(spacing: 8) {
                ProgressView()
                    .controlSize(.small)
                Text("Searching NYC addresses")
                    .foregroundStyle(.secondary)
            }
        }
        ForEach(viewModel.state.addressSuggestions) { suggestion in
            Button {
                viewModel.chooseAddress(suggestion)
            } label: {
                HStack(spacing: 10) {
                    Image(systemName: "location")
                    Text(suggestion.label)
                        .multilineTextAlignment(.leading)
                    Spacer(minLength: 0)
                }
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color(.secondarySystemBackground))
                .clipShape(RoundedRectangle(cornerRadius: 8))
            }
            .buttonStyle(.plain)
        }
        InputField(
            title: "Occurred At (ISO)",
            text: Binding(get: { viewModel.state.occurredAtIso }, set: { viewModel.update(occurredAtIso: $0) }),
            isError: viewModel.state.validationErrors.occurredAt != nil
        )
        Button {
            showComplaintChooser = true
        } label: {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Complaint")
                        .font(.headline)
                        .foregroundStyle(.primary)
                    Text(
                        complaintOptions.first(where: { $0.id == viewModel.state.selectedComplaintId })?.title ?? "Select complaint"
                    )
                    .foregroundStyle(.primary)
                }
                Spacer()
                Image(systemName: "chevron.down")
                    .foregroundStyle(.secondary)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 11)
            .background(viewModel.state.validationErrors.complaint == nil ? Color(.systemBackground) : Color.reportedFieldErrorBackground)
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(viewModel.state.validationErrors.complaint == nil ? Color(.separator) : Color.red, lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: 8))
        }
        .buttonStyle(.plain)
        if !viewModel.state.extraMedia.isEmpty {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(viewModel.state.extraMedia) { media in
                        MediaAttachmentChip(media: media)
                    }
                }
            }
        }
        SecondaryButton(title: "Add more photos or videos") {
            pendingComplaintId = viewModel.state.selectedComplaintId
            multiPickerPresented = true
        }
        InputField(title: "Description", text: Binding(get: { viewModel.state.description }, set: { viewModel.update(description: $0) }))
        InputField(title: "Notes", text: Binding(get: { viewModel.state.notes }, set: { viewModel.update(notes: $0) }))
        PrimaryButton(title: viewModel.state.loading ? "Submitting..." : "Submit Report") {
            if isAuthorized {
                viewModel.submit()
            } else {
                onRequireLogin()
            }
        }
    }

    private func handlePickedItem(_ item: PhotosPickerItem) async {
        guard let media = await loadSubmissionMedia(from: item) else { return }
        if let complaintId = pendingComplaintId {
            viewModel.onPrimaryMediaChosen(media, complaintId: complaintId)
        } else if viewModel.state.stage == .verify, viewModel.state.selectedComplaintId != nil {
            viewModel.addExtraMedia(media)
        } else {
            viewModel.onUploadMediaChosen(media)
        }
        pendingComplaintId = nil
        metadataTask?.cancel()
        metadataTask = Task {
            viewModel.setDetectionProgress(message: "Reading media metadata", progress: 0.1)
            let metadata = await extractSubmissionMetadata(from: media)
            let inferredState = metadata.latitude != nil && metadata.longitude != nil ? "NY" : nil
            let inferredAddress: String?
            if let latitude = metadata.latitude, let longitude = metadata.longitude {
                viewModel.setDetectionProgress(message: "Finding NYC address", progress: 0.35)
                inferredAddress = await reverseGeocodeNyc(latitude: latitude, longitude: longitude)?.label
            } else {
                inferredAddress = nil
            }
            if Task.isCancelled { return }
            viewModel.applyDetectedMetadata(
                occurredAtIso: metadata.occurredAtIso,
                latitude: metadata.latitude,
                longitude: metadata.longitude,
                inferredState: inferredState,
                inferredAddress: inferredAddress
            )
            if !media.isVideo {
                viewModel.setDetectionProgress(message: "Detecting plates", progress: 0.65)
                let candidates = await NativeAlprEngine.shared.detectLicensePlates(media: media)
                if !Task.isCancelled {
                    viewModel.finishDetection(
                        candidates: candidates,
                        inferredPlate: candidates.first?.plate,
                        inferredState: inferredState
                    )
                }
            }
        }
    }

    private func handleSharedMediaImport(id: String) async {
        let mediaItems = SharedMediaImportStore.consumeImport(id: id)
        guard !mediaItems.isEmpty else { return }

        if viewModel.state.primaryMedia != nil {
            mediaItems.forEach { viewModel.addExtraMedia($0) }
            return
        }

        let primary = mediaItems[0]
        if let complaintId = viewModel.state.selectedComplaintId {
            viewModel.onPrimaryMediaChosen(primary, complaintId: complaintId)
        } else {
            viewModel.onUploadMediaChosen(primary)
        }
        mediaItems.dropFirst().forEach { viewModel.addExtraMedia($0) }
        await processMetadataAndDetection(for: primary)
    }

    private func processMetadataAndDetection(for media: ComposerState.SubmissionMedia) async {
        metadataTask?.cancel()
        metadataTask = Task {
            viewModel.setDetectionProgress(message: "Reading media metadata", progress: 0.1)
            let metadata = await extractSubmissionMetadata(from: media)
            let inferredState = metadata.latitude != nil && metadata.longitude != nil ? "NY" : nil
            let inferredAddress: String?
            if let latitude = metadata.latitude, let longitude = metadata.longitude {
                viewModel.setDetectionProgress(message: "Finding NYC address", progress: 0.35)
                inferredAddress = await reverseGeocodeNyc(latitude: latitude, longitude: longitude)?.label
            } else {
                inferredAddress = nil
            }
            if Task.isCancelled { return }
            viewModel.applyDetectedMetadata(
                occurredAtIso: metadata.occurredAtIso,
                latitude: metadata.latitude,
                longitude: metadata.longitude,
                inferredState: inferredState,
                inferredAddress: inferredAddress
            )
            if !media.isVideo {
                viewModel.setDetectionProgress(message: "Detecting plates", progress: 0.65)
                let candidates = await NativeAlprEngine.shared.detectLicensePlates(media: media)
                if !Task.isCancelled {
                    viewModel.finishDetection(
                        candidates: candidates,
                        inferredPlate: candidates.first?.plate,
                        inferredState: inferredState
                    )
                }
            }
        }
    }

    private func handleAdditionalPickedItem(_ item: PhotosPickerItem) async {
        guard let media = await loadSubmissionMedia(from: item) else { return }
        viewModel.addExtraMedia(media)
    }
}

private struct ComplaintOption: Identifiable {
    let id: String
    let title: String
    let imageName: String
    let imageExtension: String
}

private let complaintOptionsList: [ComplaintOption] = [
    ComplaintOption(id: "blocked_bike_lane", title: "Blocked bike lane", imageName: "blockedbikelane", imageExtension: "jpeg"),
    ComplaintOption(id: "blocked_crosswalk", title: "Blocked crosswalk", imageName: "blockedcrosswalk", imageExtension: "jpg"),
    ComplaintOption(id: "ran_red_light", title: "Ran red light", imageName: "ranredlight", imageExtension: "jpg"),
    ComplaintOption(id: "drove_recklessly", title: "Drove recklessly", imageName: "reckless", imageExtension: "png"),
    ComplaintOption(id: "parked_illegally", title: "Parked illegally", imageName: "parkedillegally", imageExtension: "jpg")
]

private struct ExtractedSubmissionMetadata {
    let occurredAtIso: String?
    let latitude: Double?
    let longitude: Double?
}

private struct ComplaintMediaTile: View {
    let option: ComplaintOption
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 10) {
                ComplaintOptionImage(option: option)
                    .frame(height: 132)
                    .frame(maxWidth: .infinity)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                Text(option.title)
                    .font(.headline)
                    .multilineTextAlignment(.center)
                    .foregroundStyle(.primary)
                    .frame(maxWidth: .infinity)
            }
            .padding(10)
            .frame(maxWidth: .infinity, minHeight: 208, alignment: .top)
            .background(Color(.secondarySystemBackground))
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(Color(.separator), lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
    }
}

private struct ComplaintOptionImage: View {
    let option: ComplaintOption

    var body: some View {
        if let image = complaintUIImage(option: option) {
            ZStack {
                if option.id == "ran_red_light" {
                    Color.black
                }
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .clipped()
            }
        } else {
            ZStack {
                LinearGradient(
                    colors: [Color.reportedOrange.opacity(0.95), Color.red.opacity(0.7)],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
                Image(systemName: "photo")
                    .font(.system(size: 34, weight: .medium))
                    .foregroundStyle(.white)
            }
        }
    }
}

private struct UploadMediaTile: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 12) {
                Spacer()
                Image(systemName: "square.and.arrow.up")
                    .font(.system(size: 34, weight: .medium))
                    .foregroundStyle(Color.reportedOrange)
                Text("Upload")
                    .font(.headline)
                    .foregroundStyle(.primary)
                Spacer()
            }
            .frame(maxWidth: .infinity, minHeight: 208)
            .background(Color(.secondarySystemBackground))
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(Color.reportedOrange, lineWidth: 1.5)
            )
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
    }
}

private struct PrimarySubmissionPreview: View {
    let media: ComposerState.SubmissionMedia
    let selectedCandidate: ComposerState.PlateCandidate?
    @State private var showVideoPlayer = false

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if media.isVideo {
                Button {
                    showVideoPlayer = true
                } label: {
                    ZStack(alignment: .bottomLeading) {
                        if let preview = selectedCandidate?.videoFramePreview {
                            Image(uiImage: preview)
                                .resizable()
                                .scaledToFill()
                                .frame(maxWidth: .infinity)
                                .frame(height: 240)
                        } else {
                            RoundedRectangle(cornerRadius: 12)
                                .fill(Color(.secondarySystemBackground))
                                .frame(height: 220)
                                .overlay(
                                    VStack(spacing: 10) {
                                        Image(systemName: "video")
                                            .font(.system(size: 36))
                                            .foregroundStyle(Color.reportedOrange)
                                        Text(media.displayName)
                                            .foregroundStyle(.primary)
                                    }
                                )
                        }
                        if let seconds = selectedCandidate?.videoFrameTimeSeconds {
                            Text("Video frame \(formatVideoDuration(seconds))")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(.white)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .background(Color.black.opacity(0.72))
                                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                                .padding(10)
                        }
                    }
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(.plain)
                .fullScreenCover(isPresented: $showVideoPlayer) {
                    if let candidate = selectedCandidate {
                        VideoPlaybackCandidateView(
                            media: media,
                            candidate: candidate,
                            onDismiss: { showVideoPlayer = false }
                        )
                    }
                }
            } else if let image = UIImage(contentsOfFile: media.fileURL.path) {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
                    .frame(maxWidth: .infinity)
                    .frame(height: 240)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
            }
        }
    }
}

private struct PlateCandidateChip: View {
    let candidate: ComposerState.PlateCandidate
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 4) {
                Text(candidate.plate)
                    .font(.headline)
                Text("\(Int(candidate.confidence * 100))% confidence")
                    .font(.caption)
                if let detectedState = candidate.state {
                    if let confidence = candidate.stateConfidence {
                        Text("State classifier: \(detectedState) (\(Int(confidence * 100))%)")
                            .font(.caption)
                    } else {
                        Text("State classifier: \(detectedState)")
                            .font(.caption)
                    }
                }
                if let plateTypeLabel = candidate.plateTypeLabel {
                    Text("Plate type: \(plateTypeLabel)")
                        .font(.caption)
                }
            }
            .foregroundStyle(isSelected ? .white : .primary)
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(isSelected ? Color.reportedOrange : Color(.secondarySystemBackground))
            .overlay(
                RoundedRectangle(cornerRadius: 10)
                    .stroke(isSelected ? Color.reportedOrange : Color(.separator), lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: 10))
        }
        .buttonStyle(.plain)
    }
}

private struct MediaAttachmentChip: View {
    let media: ComposerState.SubmissionMedia

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: media.isVideo ? "video" : "photo")
            Text(media.displayName)
                .lineLimit(1)
        }
        .font(.subheadline)
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 10))
    }
}

private func complaintUIImage(option: ComplaintOption) -> UIImage? {
    guard let url = Bundle.main.url(
        forResource: option.imageName,
        withExtension: option.imageExtension,
        subdirectory: "complaints"
    ) else {
        return nil
    }
    return UIImage(contentsOfFile: url.path)
}

private func formatVideoDuration(_ seconds: Double) -> String {
    let totalSeconds = max(0, Int(seconds.rounded(.down)))
    return "\(totalSeconds / 60):\(String(format: "%02d", totalSeconds % 60))"
}

private func loadSubmissionMedia(from item: PhotosPickerItem) async -> ComposerState.SubmissionMedia? {
    guard let data = try? await item.loadTransferable(type: Data.self) else {
        return nil
    }
    let contentType = item.supportedContentTypes.first
    let isVideo = contentType?.conforms(to: .movie) == true
    let ext = contentType?.preferredFilenameExtension ?? (isVideo ? "mov" : "jpg")
    let fileName = item.itemIdentifier.map { "picked-\($0)" } ?? UUID().uuidString
    let fileURL = FileManager.default.temporaryDirectory.appendingPathComponent(fileName).appendingPathExtension(ext)
    do {
        if FileManager.default.fileExists(atPath: fileURL.path) {
            try FileManager.default.removeItem(at: fileURL)
        }
        try data.write(to: fileURL, options: .atomic)
        return ComposerState.SubmissionMedia(
            fileURL: fileURL,
            displayName: fileURL.lastPathComponent,
            isVideo: isVideo
        )
    } catch {
        return nil
    }
}

private func extractSubmissionMetadata(from media: ComposerState.SubmissionMedia) async -> ExtractedSubmissionMetadata {
    if media.isVideo {
        let asset = AVURLAsset(url: media.fileURL)
        let creationDate = try? await asset.load(.creationDate)
        return ExtractedSubmissionMetadata(
            occurredAtIso: creationDate?.dateValue?.ISO8601Format(),
            latitude: nil,
            longitude: nil
        )
    }

    guard let source = CGImageSourceCreateWithURL(media.fileURL as CFURL, nil),
          let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any] else {
        return ExtractedSubmissionMetadata(occurredAtIso: nil, latitude: nil, longitude: nil)
    }

    let exif = properties[kCGImagePropertyExifDictionary] as? [CFString: Any]
    let tiff = properties[kCGImagePropertyTIFFDictionary] as? [CFString: Any]
    let gps = properties[kCGImagePropertyGPSDictionary] as? [CFString: Any]

    let rawDate = exif?[kCGImagePropertyExifDateTimeOriginal] as? String
        ?? tiff?[kCGImagePropertyTIFFDateTime] as? String
    let formatter = DateFormatter()
    formatter.dateFormat = "yyyy:MM:dd HH:mm:ss"
    formatter.timeZone = .current

    var latitude = gps?[kCGImagePropertyGPSLatitude] as? Double
    var longitude = gps?[kCGImagePropertyGPSLongitude] as? Double

    if let latitudeRef = gps?[kCGImagePropertyGPSLatitudeRef] as? String, latitudeRef.uppercased() == "S" {
        latitude = latitude.map(-)
    }
    if let longitudeRef = gps?[kCGImagePropertyGPSLongitudeRef] as? String, longitudeRef.uppercased() == "W" {
        longitude = longitude.map(-)
    }

    return ExtractedSubmissionMetadata(
        occurredAtIso: rawDate.flatMap { formatter.date(from: $0)?.ISO8601Format() },
        latitude: latitude,
        longitude: longitude
    )
}

private func reverseGeocodeNyc(latitude: Double, longitude: Double) async -> ComposerState.AddressSuggestion? {
    var components = URLComponents(string: "https://geosearch.planninglabs.nyc/v2/reverse")
    components?.queryItems = [
        URLQueryItem(name: "lat", value: String(latitude)),
        URLQueryItem(name: "lon", value: String(longitude))
    ]
    guard let url = components?.url,
          let (data, _) = try? await URLSession.shared.data(from: url),
          let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
          let features = json["features"] as? [[String: Any]],
          let first = features.first,
          let properties = first["properties"] as? [String: Any] else {
        return nil
    }
    let label = (properties["label"] as? String)
        ?? (properties["name"] as? String)
        ?? (properties["address"] as? String)
    guard let label else { return nil }
    return ComposerState.AddressSuggestion(label: label, latitude: latitude, longitude: longitude)
}

private func searchNycAddresses(query: String) async -> [ComposerState.AddressSuggestion] {
    var components = URLComponents(string: "https://geosearch.planninglabs.nyc/v2/search")
    components?.queryItems = [
        URLQueryItem(name: "text", value: query),
        URLQueryItem(name: "size", value: "5")
    ]
    guard let url = components?.url,
          let (data, _) = try? await URLSession.shared.data(from: url),
          let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
          let features = json["features"] as? [[String: Any]] else {
        return []
    }

    return features.compactMap { feature in
        guard let properties = feature["properties"] as? [String: Any],
              let geometry = feature["geometry"] as? [String: Any],
              let coordinates = geometry["coordinates"] as? [Double],
              coordinates.count >= 2 else {
            return nil
        }
        let label = (properties["label"] as? String)
            ?? (properties["name"] as? String)
            ?? (properties["address"] as? String)
        guard let label else { return nil }
        return ComposerState.AddressSuggestion(
            label: label,
            latitude: coordinates[1],
            longitude: coordinates[0]
        )
    }
}

struct ReportsScreen: View {
    let isAuthorized: Bool
    let onRequireLogin: () -> Void
    @StateObject private var viewModel = ReportsViewModel()
    @State private var expandedReports: Set<String> = []
    @State private var pendingDeleteReport: ReportSummary?

    var body: some View {
        if !isAuthorized {
            ScrollView {
                ScreenCard(title: "My Reports") {
                    MessageView(text: "Sign in to see your history and keep track of the reports you've submitted.")
                    PrimaryButton(title: "Login", action: onRequireLogin)
                }
                .padding()
            }
        } else {
            NavigationStack {
                ReportsListContent(
                    viewModel: viewModel,
                    expandedReports: $expandedReports,
                    pendingDeleteReport: $pendingDeleteReport
                )
                    .navigationTitle("My Reports")
            }
            .confirmationDialog(
                "Delete report?",
                isPresented: Binding(
                    get: { pendingDeleteReport != nil },
                    set: { if !$0 { pendingDeleteReport = nil } }
                ),
                presenting: pendingDeleteReport
            ) { report in
                Button("Delete report", role: .destructive) {
                    viewModel.delete(report: report)
                    pendingDeleteReport = nil
                }
                Button("Cancel", role: .cancel) {
                    pendingDeleteReport = nil
                }
            } message: { report in
                Text("This pending report for \([report.plateRegion, report.plate].filter { !$0.isEmpty }.joined(separator: " ")) will be removed.")
            }
        }
    }
}

private struct ReportsListContent: View {
    @ObservedObject var viewModel: ReportsViewModel
    @Binding var expandedReports: Set<String>
    @Binding var pendingDeleteReport: ReportSummary?

    var body: some View {
        List {
            ReportsModeSection(viewModel: viewModel)
            if viewModel.mode == .search {
                ReportsSearchSection(viewModel: viewModel)
            }
            if let error = viewModel.error {
                MessageView(text: error)
            }
            ReportsResultsSection(viewModel: viewModel)
            ForEach(viewModel.reports, id: \.objectId) { report in
                ReportSummaryRow(
                    report: report,
                    detail: viewModel.reportDetails[report.objectId] ?? report,
                    isExpanded: expandedReports.contains(report.reportKey),
                    isLoadingDetail: viewModel.detailLoadingIds.contains(report.objectId),
                    isDeleting: viewModel.deletingReportKeys.contains(report.reportKey),
                    onToggle: {
                        if expandedReports.contains(report.reportKey) {
                            expandedReports.remove(report.reportKey)
                        } else {
                            expandedReports.insert(report.reportKey)
                            viewModel.loadDetail(for: report)
                        }
                    },
                    onDelete: {
                        pendingDeleteReport = report
                    }
                )
                .onAppear {
                    viewModel.loadNextPageIfNeeded(current: report)
                }
            }
            if viewModel.loadingMore {
                HStack {
                    Spacer()
                    ProgressView()
                    Spacer()
                }
            }
        }
    }
}

private struct ReportsModeSection: View {
    @ObservedObject var viewModel: ReportsViewModel

    var body: some View {
        Section("My Reports") {
            Text("Choose how you want to pull your reports. We will not load the list until you ask.")
                .foregroundStyle(.secondary)
            HStack(spacing: 12) {
                SecondaryButton(title: "Search", action: viewModel.chooseSearch)
                PrimaryButton(title: "List", action: viewModel.chooseList)
            }
        }
    }
}

private struct ReportsSearchSection: View {
    @ObservedObject var viewModel: ReportsViewModel

    var body: some View {
        Section("Search Filters") {
            TextField("License plate", text: $viewModel.licenseQuery)
                .textInputAutocapitalization(.characters)
            Toggle("Use start date", isOn: $viewModel.usesStartDate)
            if viewModel.usesStartDate {
                DatePicker(
                    "Start Date",
                    selection: $viewModel.startDate,
                    in: ...Date(),
                    displayedComponents: [.date, .hourAndMinute]
                )
            }
            Toggle("Use end date", isOn: $viewModel.usesEndDate)
            if viewModel.usesEndDate {
                DatePicker(
                    "End Date",
                    selection: $viewModel.endDate,
                    in: ...Date(),
                    displayedComponents: [.date, .hourAndMinute]
                )
            }
            PrimaryButton(title: "Search reports", action: viewModel.search)
        }
    }
}

private struct ReportsResultsSection: View {
    @ObservedObject var viewModel: ReportsViewModel

    var body: some View {
        if viewModel.loading {
            ProgressView()
        } else if viewModel.mode != nil {
            Section("Results") {
                Text(viewModel.reports.isEmpty ? "No reports found." : "\(viewModel.reports.count) reports")
            }
        }
    }
}

private struct ReportSummaryRow: View {
    let report: ReportSummary
    let detail: ReportSummary
    let isExpanded: Bool
    let isLoadingDetail: Bool
    let isDeleting: Bool
    let onToggle: () -> Void
    let onDelete: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Button(action: onToggle) {
                reportHeader
            }
            .buttonStyle(.plain)
            if isExpanded {
                reportDetail
                if report.canDelete {
                    Button(isDeleting ? "Deleting..." : "Delete report", role: .destructive, action: onDelete)
                        .disabled(isDeleting)
                }
            }
        }
        .padding(.vertical, 8)
    }

    private var reportHeader: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(report.street.isEmpty ? "Unknown address" : report.street)
                        .font(.headline)
                    Text([report.plate, report.plateRegion].filter { !$0.isEmpty }.joined(separator: " - "))
                        .foregroundStyle(.secondary)
                }
                Spacer()
                Text(report.status.isEmpty ? "Pending" : report.status)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(Color.reportedOrange)
            }
            if !report.incidentAt.isEmpty {
                Text(report.incidentAt.reportDateTimeDisplay).font(.caption)
            }
            if !report.complaint.isEmpty {
                Text(report.complaint).font(.subheadline)
            }
        }
    }

    private var reportDetail: some View {
        VStack(alignment: .leading, spacing: 8) {
            if isLoadingDetail {
                ProgressView()
            }
            if !detail.description.isEmpty {
                Text(detail.description).font(.subheadline)
            }
            if !detail.notes.isEmpty {
                Text("Notes: \(detail.notes)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            ReportMediaStrip(mediaUrls: detail.mediaUrls, videoUrls: detail.videoUrls)
            if detail.mediaUrls.isEmpty && detail.videoUrls.isEmpty && !isLoadingDetail {
                Text("No media attached.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }
}

private struct ReportMediaStrip: View {
    let mediaUrls: [String]
    let videoUrls: [String]

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if !mediaUrls.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 10) {
                        ForEach(mediaUrls, id: \.self) { url in
                            AsyncImage(url: URL(string: url)) { image in
                                image.resizable().scaledToFill()
                            } placeholder: {
                                Color(.secondarySystemBackground)
                            }
                            .frame(width: 112, height: 112)
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                        }
                    }
                }
            }
            ForEach(Array(videoUrls.enumerated()), id: \.offset) { index, _ in
                Text("Video \(index + 1)")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(Color.reportedOrange)
            }
        }
    }
}

private extension ReportSummary {
    var reportKey: String {
        objectId.isEmpty ? "\(id)" : objectId
    }
}

private extension String {
    var reportDateTimeDisplay: String {
        guard !isEmpty else { return "" }
        let isoFormatter = ISO8601DateFormatter()
        isoFormatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let fallbackIsoFormatter = ISO8601DateFormatter()
        fallbackIsoFormatter.formatOptions = [.withInternetDateTime]
        let date = isoFormatter.date(from: self) ?? fallbackIsoFormatter.date(from: self)
        guard let date else { return self }
        return date.formatted(
            .dateTime
                .month(.abbreviated)
                .day()
                .year()
                .hour(.defaultDigits(amPM: .abbreviated))
                .minute()
        )
    }
}

struct ProfileScreen: View {
    let isAuthorized: Bool
    let onRequireLogin: () -> Void
    @StateObject private var viewModel = ProfileViewModel()
    let onLogout: () -> Void
    let onThemeModeSelected: (AppThemeMode) -> Void

    var body: some View {
        Group {
            if !isAuthorized {
                ScrollView {
                    ScreenCard(title: "Profile") {
                        MessageView(text: "Sign in to edit your profile, sync your settings, and manage your account.")
                        PrimaryButton(title: "Login", action: onRequireLogin)
                    }
                    .padding()
                }
            } else {
                ScrollView {
                    ScreenCard(title: "Profile") {
                        if let error = viewModel.state.error { MessageView(text: error) }
                        InputField(title: "First Name", text: Binding(get: { viewModel.state.firstName }, set: { viewModel.update(firstName: $0) }), disabled: !viewModel.state.editing)
                        InputField(title: "Last Name", text: Binding(get: { viewModel.state.lastName }, set: { viewModel.update(lastName: $0) }), disabled: !viewModel.state.editing)
                        InputField(title: "Phone", text: Binding(get: { viewModel.state.phone }, set: { viewModel.update(phone: $0) }), disabled: !viewModel.state.editing)
                        InputField(title: "Email", text: Binding(get: { viewModel.state.email }, set: { viewModel.update(email: $0) }), disabled: !viewModel.state.editing)
                        VStack(alignment: .leading, spacing: 10) {
                            Text("Theme")
                                .font(.headline)
                            HStack(spacing: 8) {
                                ThemeChip(title: "System", isSelected: viewModel.state.themeMode == .system) {
                                    viewModel.setThemeMode(.system)
                                    onThemeModeSelected(.system)
                                }
                                ThemeChip(title: "Light", isSelected: viewModel.state.themeMode == .light) {
                                    viewModel.setThemeMode(.light)
                                    onThemeModeSelected(.light)
                                }
                                ThemeChip(title: "Dark", isSelected: viewModel.state.themeMode == .dark) {
                                    viewModel.setThemeMode(.dark)
                                    onThemeModeSelected(.dark)
                                }
                            }
                        }
                        if viewModel.state.editing {
                            PrimaryButton(title: viewModel.state.loading ? "Saving..." : "Save") { viewModel.save() }
                        } else {
                            PrimaryButton(title: "Edit Profile") { viewModel.toggleEditing() }
                        }
                        Button("Logout", action: onLogout)
                            .foregroundStyle(Color.reportedOrange)
                    }
                    .padding()
                }
            }
        }
        .task {
            if isAuthorized {
                viewModel.load()
            }
        }
    }
}

struct ScreenCard<Content: View>: View {
    let title: String?
    @ViewBuilder let content: Content

    init(title: String? = nil, @ViewBuilder content: () -> Content) {
        self.title = title
        self.content = content()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            if let title {
                Text(title)
                    .font(.largeTitle.bold())
            }
            content
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

struct MessageView: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.callout)
            .padding()
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color(.secondarySystemBackground))
            .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}

private struct DetectionCandidatePill: View {
    let candidate: ComposerState.PlateCandidate
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 3) {
                Text(candidate.plate)
                    .font(.callout.weight(.bold))
                Text([candidate.state, "\(Int(candidate.confidence * 100))%"].compactMap { $0 }.joined(separator: "  "))
                    .font(.caption)
            }
            .foregroundStyle(isSelected ? .white : .primary)
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(isSelected ? Color.reportedOrange : Color(.secondarySystemBackground))
            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}

private struct VideoFrameScrubberPreview: View {
    let url: URL
    let timeSeconds: Double
    let candidates: [ComposerState.PlateCandidate]
    let selectedPlate: String?
    let onCandidateSelected: (ComposerState.PlateCandidate) -> Void

    var body: some View {
        ZStack {
            VideoSeekPreview(url: url, timeSeconds: timeSeconds)
            VideoPlateOverlay(
                candidates: candidates,
                selectedPlate: selectedPlate,
                onCandidateSelected: onCandidateSelected
            )
        }
        .background(Color.black)
    }
}

private struct VideoPlaybackCandidateView: View {
    let media: ComposerState.SubmissionMedia
    let candidate: ComposerState.PlateCandidate
    let onDismiss: () -> Void

    var body: some View {
        ZStack(alignment: .topTrailing) {
            Color.black.ignoresSafeArea()
            VideoPlaybackView(
                url: media.fileURL,
                startSeconds: candidate.videoFrameTimeSeconds ?? 0
            )
            .ignoresSafeArea()
            VideoPlateOverlay(
                candidates: [candidate],
                selectedPlate: candidate.plate,
                onCandidateSelected: { _ in }
            )
            .ignoresSafeArea()
            Button(action: onDismiss) {
                Image(systemName: "xmark")
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(width: 44, height: 44)
                    .background(Color.black.opacity(0.62))
                    .clipShape(Circle())
            }
            .padding(16)
        }
    }
}

private struct VideoPlaybackView: UIViewRepresentable {
    let url: URL
    let startSeconds: Double

    func makeUIView(context: Context) -> PlayerContainerView {
        let view = PlayerContainerView()
        let player = AVPlayer(url: url)
        view.playerLayer.player = player
        view.playerLayer.videoGravity = .resizeAspect
        context.coordinator.player = player
        context.coordinator.url = url
        player.seek(to: CMTime(seconds: max(0, startSeconds), preferredTimescale: 600), toleranceBefore: .zero, toleranceAfter: .zero)
        player.play()
        return view
    }

    func updateUIView(_ view: PlayerContainerView, context: Context) {
        guard context.coordinator.url != url else { return }
        let player = AVPlayer(url: url)
        context.coordinator.player = player
        context.coordinator.url = url
        view.playerLayer.player = player
        player.seek(to: CMTime(seconds: max(0, startSeconds), preferredTimescale: 600), toleranceBefore: .zero, toleranceAfter: .zero)
        player.play()
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(url: url)
    }

    final class Coordinator {
        var url: URL
        var player: AVPlayer?

        init(url: URL) {
            self.url = url
        }
    }
}

private struct VideoSeekPreview: UIViewRepresentable {
    let url: URL
    let timeSeconds: Double

    func makeUIView(context: Context) -> PlayerContainerView {
        let view = PlayerContainerView()
        let player = AVPlayer(url: url)
        player.isMuted = true
        player.pause()
        view.playerLayer.player = player
        view.playerLayer.videoGravity = .resizeAspect
        context.coordinator.player = player
        return view
    }

    func updateUIView(_ view: PlayerContainerView, context: Context) {
        if context.coordinator.url != url {
            let player = AVPlayer(url: url)
            player.isMuted = true
            player.pause()
            context.coordinator.player = player
            context.coordinator.url = url
            view.playerLayer.player = player
        }
        let target = CMTime(seconds: max(0, timeSeconds), preferredTimescale: 600)
        context.coordinator.player?.pause()
        context.coordinator.player?.seek(to: target, toleranceBefore: .zero, toleranceAfter: .zero)
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(url: url)
    }

    final class Coordinator {
        var url: URL
        var player: AVPlayer?

        init(url: URL) {
            self.url = url
        }
    }
}

private final class PlayerContainerView: UIView {
    override static var layerClass: AnyClass { AVPlayerLayer.self }
    var playerLayer: AVPlayerLayer { layer as! AVPlayerLayer }
}

private struct VideoPlateOverlay: View {
    let candidates: [ComposerState.PlateCandidate]
    let selectedPlate: String?
    let onCandidateSelected: (ComposerState.PlateCandidate) -> Void

    var body: some View {
        GeometryReader { proxy in
            ZStack(alignment: .topLeading) {
                ForEach(candidates) { candidate in
                    if let bounds = candidate.bounds, bounds.width > 0, bounds.height > 0 {
                        let rect = CGRect(
                            x: bounds.minX * proxy.size.width,
                            y: bounds.minY * proxy.size.height,
                            width: bounds.width * proxy.size.width,
                            height: bounds.height * proxy.size.height
                        )
                        Rectangle()
                            .stroke(candidate.plate == selectedPlate ? Color.green : Color.reportedOrange, lineWidth: 3)
                            .frame(width: max(1, rect.width), height: max(1, rect.height))
                            .position(x: rect.midX, y: rect.midY)
                        Button {
                            onCandidateSelected(candidate)
                        } label: {
                            Text([candidate.plate, candidate.state, "\(Int(candidate.confidence * 100))%"].compactMap { $0 }.joined(separator: "  "))
                                .font(.caption.weight(.bold))
                                .foregroundStyle(.white)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .background(Color.black.opacity(0.78))
                                .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
                        }
                        .buttonStyle(.plain)
                        .position(x: min(proxy.size.width - 70, max(70, rect.midX)), y: max(18, rect.minY - 16))
                    }
                }
            }
        }
    }
}

struct InputField: View {
    let title: String
    @Binding var text: String
    var disabled = false
    var isError = false
    var onClear: (() -> Void)? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.headline)
            HStack(spacing: 8) {
                TextField(title, text: $text, axis: .vertical)
                    .disabled(disabled)
                if let onClear, !text.isEmpty {
                    Button(action: onClear) {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundStyle(.secondary)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 11)
            .background(isError ? Color.reportedFieldErrorBackground : Color(.systemBackground))
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(isError ? Color.red : Color(.separator), lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: 8))
        }
    }
}

struct PickerField: View {
    let title: String
    let value: String
    var isError = false
    let action: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.headline)
            Button(action: action) {
                HStack {
                    Text(value.isEmpty ? "Select" : value)
                        .foregroundStyle(.primary)
                    Spacer()
                    Image(systemName: "chevron.down")
                        .foregroundStyle(.secondary)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 11)
                .background(isError ? Color.reportedFieldErrorBackground : Color(.systemBackground))
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(isError ? Color.red : Color(.separator), lineWidth: 1)
                )
                .clipShape(RoundedRectangle(cornerRadius: 8))
            }
            .buttonStyle(.plain)
        }
    }
}

struct PlateRegionPopup: View {
    let options: [String]
    let selectedValue: String
    let onSelected: (String) -> Void
    let onDismiss: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(options.count > 6 ? "All State Abbreviations" : "Choose State")
                    .font(.headline)
                Spacer()
                Button("Close", action: onDismiss)
                    .font(.caption)
                    .foregroundStyle(Color.reportedOrange)
            }
            FlowLayout(spacing: 8) {
                ForEach(options, id: \.self) { option in
                    ThemeChip(title: option, isSelected: selectedValue == option) {
                        onSelected(option)
                    }
                }
            }
        }
        .padding(12)
        .frame(width: 260)
        .background(Color(.systemBackground))
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(Color(.separator), lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .shadow(color: .black.opacity(0.12), radius: 12, y: 6)
    }
}

struct FlowLayout<Content: View>: View {
    let spacing: CGFloat
    @ViewBuilder let content: Content

    init(spacing: CGFloat = 8, @ViewBuilder content: () -> Content) {
        self.spacing = spacing
        self.content = content()
    }

    var body: some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 52), spacing: spacing)], spacing: spacing) {
            content
        }
    }
}

struct PrimaryButton: View {
    let title: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
        }
        .buttonStyle(.borderedProminent)
        .tint(Color.reportedOrange)
    }
}

struct SecondaryButton: View {
    let title: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .foregroundStyle(Color.reportedOrange)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
        }
        .buttonStyle(.bordered)
        .tint(Color.reportedOrange)
    }
}

struct TertiaryButton: View {
    let title: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .foregroundStyle(.primary.opacity(0.88))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 10)
        }
        .buttonStyle(.plain)
    }
}

struct ThemeChip: View {
    let title: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.subheadline)
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
        }
        .buttonStyle(.plain)
        .foregroundStyle(isSelected ? .white : .primary)
        .background(isSelected ? Color.reportedOrange : Color(.secondarySystemBackground))
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(isSelected ? Color.reportedOrange : Color(.separator), lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}

private func appPreferredColorScheme(for mode: AppThemeMode) -> ColorScheme? {
    switch mode {
    case .light:
        return .light
    case .dark:
        return .dark
    default:
        return nil
    }
}

struct ComplaintChip: View {
    let title: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.subheadline)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
        }
        .buttonStyle(.plain)
        .foregroundStyle(isSelected ? .white : .primary)
        .background(isSelected ? Color.reportedOrange : Color(.secondarySystemBackground))
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(isSelected ? Color.reportedOrange : Color(.separator), lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}

extension Color {
    static let reportedOrange = Color(red: 236 / 255, green: 104 / 255, blue: 44 / 255)
    static let reportedFieldErrorBackground = Color(red: 1, green: 241 / 255, blue: 241 / 255)
}
