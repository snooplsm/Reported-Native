import AVFoundation
import CoreGraphics
import FirebaseAnalytics
import ImageIO
import Lottie
import MapKit
import PhotosUI
import SharedCore
import SwiftUI
import UniformTypeIdentifiers
import UIKit
import WebKit

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
    @State private var detectedDraftOpenRequest: UUID?

    var body: some View {
        Group {
            if sessionViewModel.state.session?.isAuthorized == true || sessionViewModel.state.isGuest {
                MainShellView(
                    sessionViewModel: sessionViewModel,
                    themeViewModel: themeViewModel,
                    onRequireLogin: { authRoute = .login },
                    sharedMediaImportId: $sharedMediaImportId,
                    detectedDraftOpenRequest: $detectedDraftOpenRequest
                )
            } else {
                AuthFlowView(sessionViewModel: sessionViewModel)
            }
        }
        .task {
            await ReportedRemoteConfigService.shared.configureAndFetch()
            sessionViewModel.load()
            themeViewModel.load()
        }
        .onOpenURL { url in
            if NativeSocialAuth.handle(url: url) {
                return
            }
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
        .onReceive(NotificationCenter.default.publisher(for: .reportedDetectedInfractionEditRequested)) { _ in
            detectedDraftOpenRequest = UUID()
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
            ZStack {
                SplashVideoBackground()
                    .ignoresSafeArea()
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
                    }
                    if allowSkip {
                        Button {
                            sessionViewModel.continueAsGuest()
                        } label: {
                            Text("Skip")
                                .font(.body.weight(.semibold))
                                .foregroundStyle(.white)
                                .shadow(color: .black.opacity(0.35), radius: 4, x: 0, y: 1)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 10)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding()
            }
            .toolbarBackground(.hidden, for: .navigationBar)
            .navigationDestination(for: AuthRoute.self) { route in
                switch route {
                case .login:
                    LoginScreen {
                        sessionViewModel.didAuthenticate()
                        onDismiss?()
                    } onRegister: {
                        path.append(.register)
                    } onBack: {
                        handleAuthBack()
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
                        handleAuthBack()
                    } onDismiss: {
                        onDismiss?()
                    }
                case .splash:
                    EmptyView()
                }
            }
            .navigationBarBackButtonHidden(true)
        }
    }

    private func handleAuthBack() {
        if path.count <= 1, let onDismiss {
            onDismiss()
        } else if !path.isEmpty {
            path.removeLast()
        }
    }
}

private struct SplashVideoBackground: UIViewRepresentable {
    func makeUIView(context: Context) -> PlayerContainerView {
        let view = PlayerContainerView()
        view.playerLayer.videoGravity = .resizeAspectFill

        guard let url = Bundle.main.url(forResource: "splash", withExtension: "mp4") else {
            view.backgroundColor = UIColor.systemBackground
            return view
        }

        let player = AVQueuePlayer()
        player.isMuted = true
        player.actionAtItemEnd = .none

        let item = AVPlayerItem(url: url)
        let looper = AVPlayerLooper(player: player, templateItem: item)

        view.playerLayer.player = player
        context.coordinator.player = player
        context.coordinator.looper = looper
        player.play()

        return view
    }

    func updateUIView(_ view: PlayerContainerView, context: Context) {
        context.coordinator.player?.play()
    }

    static func dismantleUIView(_ view: PlayerContainerView, coordinator: Coordinator) {
        coordinator.player?.pause()
        coordinator.player = nil
        coordinator.looper = nil
    }

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    final class Coordinator {
        var player: AVQueuePlayer?
        var looper: AVPlayerLooper?
    }
}

enum MainShellDestination: Int, CaseIterable {
    case report
    case reports
    case profile
    case settings

    var title: String {
        switch self {
        case .report: return "New Report"
        case .reports: return "My Reports"
        case .profile: return "Profile"
        case .settings: return "Settings"
        }
    }

    var systemImage: String {
        switch self {
        case .report: return "plus.square"
        case .reports: return "list.bullet.rectangle"
        case .profile: return "person.crop.circle"
        case .settings: return "gearshape"
        }
    }
}

private enum ReportTutorialSettings {
    private static let seenKey = "reported.newReportTutorial.seen"

    static var hasSeen: Bool {
        UserDefaults.standard.bool(forKey: seenKey)
    }

    static func markSeen() {
        UserDefaults.standard.set(true, forKey: seenKey)
    }
}

struct MainShellView: View {
    private let drawerWidth: CGFloat = 116
    @ObservedObject var sessionViewModel: SessionViewModel
    @ObservedObject var themeViewModel: ThemeViewModel
    let onRequireLogin: () -> Void
    @Binding var sharedMediaImportId: String?
    @Binding var detectedDraftOpenRequest: UUID?
    @State private var selection: MainShellDestination = .report
    @State private var isNavigationOpen = false
    @State private var reportHasDraftContent = false
    @State private var reportClearRequest = 0
    @State private var reportSubmitBarVisible = false
    @State private var submittedSnackbarObjectId: String?
    @State private var pendingOpenReportObjectId: String?
    @GestureState private var navigationDragTranslation: CGFloat = 0

    var body: some View {
        GeometryReader { proxy in
            let isLandscape = proxy.size.width > proxy.size.height
            let reportOwnsToolbar = selection == .report && isLandscape
            let baseOffset = isNavigationOpen ? drawerWidth : 0
            let currentOffset = min(max(baseOffset + navigationDragTranslation, 0), drawerWidth)

            HStack(spacing: 0) {
                LeftGliderNavView(selection: selection) { destination in
                    select(destination)
                }
                .frame(width: drawerWidth)

                VStack(spacing: 0) {
                    if !reportOwnsToolbar {
                        MainShellToolbar(
                            title: selection.title,
                            showClear: selection == .report && reportHasDraftContent,
                            onMenuTapped: { toggleNavigation() },
                            onClear: { reportClearRequest += 1 }
                        )
                    }
                    Group {
                        switch selection {
                        case .report:
                            ComposerScreen(
                                isAuthorized: sessionViewModel.state.session?.isAuthorized == true,
                                onRequireLogin: onRequireLogin,
                                showEmbeddedLandscapeToolbar: reportOwnsToolbar,
                                shellHasDraftContent: reportHasDraftContent,
                                onMenuTapped: { toggleNavigation() },
                                onClearTapped: { reportClearRequest += 1 },
                                sharedMediaImportId: $sharedMediaImportId,
                                clearRequest: $reportClearRequest,
                                detectedDraftOpenRequest: $detectedDraftOpenRequest,
                                onDraftContentChanged: { reportHasDraftContent = $0 },
                                onSubmitBarVisibilityChanged: { reportSubmitBarVisible = $0 },
                                onReportSubmitted: { objectId in
                                    submittedSnackbarObjectId = objectId
                                }
                            )
                        case .reports:
                            ReportsScreen(
                                isAuthorized: sessionViewModel.state.session?.isAuthorized == true,
                                onRequireLogin: onRequireLogin,
                                openReportObjectId: pendingOpenReportObjectId,
                                onOpenedReport: { pendingOpenReportObjectId = nil }
                            )
                        case .profile:
                            ProfileScreen(
                                isAuthorized: sessionViewModel.state.session?.isAuthorized == true,
                                onRequireLogin: onRequireLogin,
                                onLogout: { sessionViewModel.logout() }
                            )
                        case .settings:
                            SettingsScreen(
                                onThemeModeSelected: { themeViewModel.update($0) }
                            )
                        }
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
                .frame(width: proxy.size.width, height: proxy.size.height)
                .background(Color(.systemBackground))
            }
            .frame(width: proxy.size.width + drawerWidth, height: proxy.size.height, alignment: .leading)
            .offset(x: currentOffset - drawerWidth)
            .animation(.spring(response: 0.28, dampingFraction: 0.86), value: isNavigationOpen)
            .gesture(
                DragGesture(minimumDistance: 12)
                    .updating($navigationDragTranslation) { value, state, _ in
                        let proposedOffset = baseOffset + value.translation.width
                        if proposedOffset >= 0 && proposedOffset <= drawerWidth {
                            state = value.translation.width
                        }
                    }
                    .onEnded { value in
                        settleNavigationDrag(baseOffset: baseOffset, translation: value.predictedEndTranslation.width)
                    }
            )
            .clipped()
            .background(alignment: .leading) {
                if currentOffset > 0 {
                    Color(.secondarySystemBackground)
                        .opacity(0.7)
                        .frame(width: drawerWidth)
                        .offset(x: currentOffset - drawerWidth)
                        .ignoresSafeArea(.container, edges: [.top, .bottom])
                }
            }
            .background(Color(.systemBackground))
            .background(alignment: .bottom) {
                if selection == .report && reportSubmitBarVisible {
                    Color.reportedOrange
                        .frame(height: max(proxy.safeAreaInsets.bottom, 1))
                        .ignoresSafeArea(.container, edges: .bottom)
                }
            }
            .ignoresSafeArea(.keyboard, edges: .bottom)
            .overlay(alignment: .bottom) {
                if let objectId = submittedSnackbarObjectId {
                    SubmittedReportSnackbar(
                        onView: {
                            pendingOpenReportObjectId = objectId
                            submittedSnackbarObjectId = nil
                            selection = .reports
                            closeNavigation()
                        },
                        onDismiss: {
                            submittedSnackbarObjectId = nil
                        }
                    )
                    .padding(.horizontal, 16)
                    .padding(.bottom, 16)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                }
            }
            .animation(.spring(response: 0.28, dampingFraction: 0.86), value: submittedSnackbarObjectId)
            .task(id: submittedSnackbarObjectId) {
                guard let objectId = submittedSnackbarObjectId else { return }
                try? await Task.sleep(nanoseconds: 6_000_000_000)
                if submittedSnackbarObjectId == objectId {
                    submittedSnackbarObjectId = nil
                }
            }
        }
        .ignoresSafeArea(.container, edges: [.horizontal, .bottom])
        .onAppear {
            logScreenView(selection.title)
        }
        .onChange(of: selection) { _, destination in
            logScreenView(destination.title)
        }
        .onChange(of: sharedMediaImportId) { _, importId in
            if importId != nil {
                selection = .report
                closeNavigation()
            }
        }
        .onChange(of: detectedDraftOpenRequest) { _, request in
            if request != nil {
                selection = .report
                closeNavigation()
            }
        }
    }

    private func select(_ destination: MainShellDestination) {
        let isAuthorized = sessionViewModel.state.session?.isAuthorized == true
        if !isAuthorized && destination.requiresAuthorization {
            selection = .report
            closeNavigation()
            onRequireLogin()
            return
        }
        selection = destination
        closeNavigation()
    }

    private func toggleNavigation() {
        withAnimation(.spring(response: 0.28, dampingFraction: 0.86)) {
            isNavigationOpen.toggle()
        }
    }

    private func closeNavigation() {
        withAnimation(.spring(response: 0.28, dampingFraction: 0.86)) {
            isNavigationOpen = false
        }
    }

    private func settleNavigationDrag(baseOffset: CGFloat, translation: CGFloat) {
        let projectedOffset = min(max(baseOffset + translation, 0), drawerWidth)
        withAnimation(.spring(response: 0.28, dampingFraction: 0.86)) {
            isNavigationOpen = projectedOffset > drawerWidth * 0.4
        }
    }

    private func logScreenView(_ name: String) {
        Analytics.logEvent(AnalyticsEventScreenView, parameters: [
            AnalyticsParameterScreenName: name,
            AnalyticsParameterScreenClass: name
        ])
    }
}

private extension MainShellDestination {
    var requiresAuthorization: Bool {
        switch self {
        case .reports, .profile:
            return true
        case .report, .settings:
            return false
        }
    }
}

private struct MainShellToolbar: View {
    let title: String
    var showClear = false
    var compact = false
    let onMenuTapped: () -> Void
    let onClear: () -> Void

    var body: some View {
        HStack {
            Button(action: onMenuTapped) {
                Image(systemName: "line.3.horizontal")
                    .font(.system(size: compact ? 22 : 24, weight: .semibold))
                    .frame(width: compact ? 38 : 44, height: compact ? 38 : 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .foregroundStyle(.primary)

            Spacer()

            Text(title)
                .font(.system(size: compact ? 20 : 24, weight: .regular))
                .lineLimit(1)

            Spacer()

            if showClear {
                Button("Clear", action: onClear)
                    .font(.body.weight(.semibold))
                    .foregroundStyle(Color.reportedOrange)
                    .frame(width: compact ? 44 : 58, height: compact ? 38 : 44, alignment: .trailing)
            } else {
                Color.clear
                    .frame(width: compact ? 44 : 58, height: compact ? 38 : 44)
            }
        }
        .padding(.horizontal, compact ? 8 : 20)
        .padding(.vertical, compact ? 6 : 12)
        .background(Color(.systemBackground))
    }
}

struct LeftGliderNavView: View {
    let selection: MainShellDestination
    let onSelect: (MainShellDestination) -> Void

    var body: some View {
        let destinations = MainShellDestination.allCases
        let selectedIndex = destinations.firstIndex(of: selection) ?? 0
        let topPadding: CGFloat = 16

        ZStack(alignment: .top) {
            RoundedRectangle(cornerRadius: 18)
                .fill(Color.reportedOrange.opacity(0.12))
                .frame(width: 92, height: 72)
                .offset(y: CGFloat(selectedIndex) * 84 + topPadding)
                .animation(.spring(response: 0.28, dampingFraction: 0.84), value: selection)

            VStack(spacing: 12) {
                ForEach(destinations, id: \.self) { destination in
                    Button {
                        onSelect(destination)
                    } label: {
                        VStack(spacing: 8) {
                            Image(systemName: destination.systemImage)
                                .font(.title3)
                            Text(destination.title)
                                .font(.caption)
                                .multilineTextAlignment(.center)
                        }
                        .foregroundStyle(selection == destination ? Color.reportedOrange : .secondary)
                        .frame(width: 92, height: 72)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.top, topPadding)
        }
        .frame(width: 116)
        .frame(maxHeight: .infinity, alignment: .top)
        .background(Color.clear)
    }
}

struct LoginScreen: View {
    @StateObject private var viewModel = LoginViewModel()
    let onSuccess: () -> Void
    let onRegister: () -> Void
    var onBack: (() -> Void)? = nil
    var onDismiss: (() -> Void)? = nil

    var body: some View {
        VStack(spacing: 0) {
            AuthToolbar(title: "Login", onBack: onBack, onDismiss: onDismiss)
            ScrollView {
                ScreenCard {
                if let error = viewModel.state.error { MessageView(text: error) }
                ProviderSignInButton(
                    title: "Sign in with Google",
                    systemImage: nil,
                    iconText: "G",
                    foregroundColor: Color(red: 60 / 255, green: 64 / 255, blue: 67 / 255),
                    backgroundColor: .white,
                    borderColor: Color(.separator),
                    enabled: !viewModel.state.loading
                ) {
                    viewModel.signInWithGoogle(onSuccess: onSuccess)
                }
                ProviderSignInButton(
                    title: "Sign in with Apple",
                    systemImage: "apple.logo",
                    iconText: nil,
                    foregroundColor: .white,
                    backgroundColor: .black,
                    borderColor: .black,
                    enabled: !viewModel.state.loading
                ) {
                    viewModel.signInWithApple(onSuccess: onSuccess)
                }
                AuthDivider()
                InputField(title: "Email", text: Binding(
                    get: { viewModel.state.email },
                    set: { viewModel.update(email: $0) }
                ), keyboardType: .emailAddress, textContentType: .emailAddress, textInputAutocapitalization: .never, autocorrectionDisabled: true)
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
        .navigationBarBackButtonHidden(true)
    }
}

struct RegisterScreen: View {
    @StateObject private var viewModel = RegisterViewModel()
    let onSuccess: () -> Void
    let onLogin: () -> Void
    var onBack: (() -> Void)? = nil
    var onDismiss: (() -> Void)? = nil

    var body: some View {
        VStack(spacing: 0) {
            AuthToolbar(title: "Register", onBack: onBack, onDismiss: onDismiss)
            ScrollView {
                ScreenCard {
                if let error = viewModel.state.error { MessageView(text: error) }
                ProviderSignInButton(
                    title: "Sign in with Google",
                    systemImage: nil,
                    iconText: "G",
                    foregroundColor: Color(red: 60 / 255, green: 64 / 255, blue: 67 / 255),
                    backgroundColor: .white,
                    borderColor: Color(.separator),
                    enabled: !viewModel.state.loading
                ) {
                    viewModel.signInWithGoogle(onSuccess: onSuccess)
                }
                ProviderSignInButton(
                    title: "Sign in with Apple",
                    systemImage: "apple.logo",
                    iconText: nil,
                    foregroundColor: .white,
                    backgroundColor: .black,
                    borderColor: .black,
                    enabled: !viewModel.state.loading
                ) {
                    viewModel.signInWithApple(onSuccess: onSuccess)
                }
                AuthDivider()
                InputField(title: "First Name", text: Binding(get: { viewModel.state.firstName }, set: { viewModel.update(firstName: $0) }))
                InputField(title: "Last Name", text: Binding(get: { viewModel.state.lastName }, set: { viewModel.update(lastName: $0) }))
                InputField(title: "Phone", text: Binding(get: { viewModel.state.phone }, set: { viewModel.update(phone: $0) }))
                InputField(title: "Email", text: Binding(get: { viewModel.state.email }, set: { viewModel.update(email: $0) }), keyboardType: .emailAddress, textContentType: .emailAddress, textInputAutocapitalization: .never, autocorrectionDisabled: true)
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
        .navigationBarBackButtonHidden(true)
    }
}

private struct AuthToolbar: View {
    let title: String
    let onBack: (() -> Void)?
    let onDismiss: (() -> Void)?

    var body: some View {
        ZStack {
            Text(title)
                .font(.largeTitle)
            HStack {
                if let onBack {
                    Button(action: onBack) {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 24, weight: .semibold))
                            .foregroundStyle(.primary)
                            .frame(width: 44, height: 44)
                    }
                    .buttonStyle(.plain)
                } else {
                    Color.clear.frame(width: 44, height: 44)
                }
                Spacer()
                if let onDismiss {
                    Button("Close", action: onDismiss)
                        .font(.body.weight(.semibold))
                        .foregroundStyle(Color.reportedOrange)
                } else {
                    Color.clear.frame(width: 44, height: 44)
                }
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 8)
        .padding(.bottom, 14)
        .background(Color(.systemBackground))
    }
}

struct ComposerScreen: View {
    @StateObject private var viewModel = ComposerViewModel()
    let isAuthorized: Bool
    let onRequireLogin: () -> Void
    let showEmbeddedLandscapeToolbar: Bool
    let shellHasDraftContent: Bool
    let onMenuTapped: () -> Void
    let onClearTapped: () -> Void
    @Binding var sharedMediaImportId: String?
    @Binding var clearRequest: Int
    @Binding var detectedDraftOpenRequest: UUID?
    let onDraftContentChanged: (Bool) -> Void
    let onSubmitBarVisibilityChanged: (Bool) -> Void
    let onReportSubmitted: (String) -> Void
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
    @State private var showPlateRegionSheet = false
    @State private var showAddressMapSheet = false
    @State private var showComplaintChooser = false
    @State private var showPlateCandidatesChooser = false
    @State private var pendingPlateCandidate: ComposerState.PlateCandidate?
    @State private var pendingPlatePreviewImage: UIImage?
    @State private var showOccurredAtPicker = false
    @State private var showDiscardConfirmation = false
    @State private var handledClearRequest = 0
    @State private var detectionProgressMinimized = false
    @State private var videoScanPaused = false
    @State private var videoScanGeneration = 0
    @State private var videoPreviewScrubSeconds = 0.0
    @State private var activeAnimatedComplaintId: String?
    @State private var showReportTutorial = false
    @State private var reportTutorialChecked = false
    @State private var tutorialScannerEnabled = true
    @State private var tutorialNotificationsEnabled = true
    @State private var isLandscapeComposer = false
    @State private var composerBottomSafeArea: CGFloat = 0
    private let previewScrollId = "composer-primary-media-preview"

    private let complaintColumns = [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)]
    private func landscapeMediaColumnWidth(for width: CGFloat) -> CGFloat {
        min(max(236, width * 0.31), 276)
    }

    private var complaintOptions: [ComplaintOption] {
        complaintOptionsFor(viewModel.state.complaintCategories)
    }
    private var animatedComplaintIds: [String] {
        complaintOptions.compactMap { $0.lottieName == nil ? nil : $0.id }
    }
    private var selectedComplaintDetectionHint: String? {
        guard let selectedId = viewModel.state.selectedComplaintId else { return nil }
        let option = complaintOptions.first { $0.id == selectedId }
        return [option?.title, option?.id, selectedId]
            .compactMap { $0 }
            .joined(separator: " ")
    }

    private var hasDraftContent: Bool {
        viewModel.state.primaryMedia != nil ||
            !viewModel.state.extraMedia.isEmpty ||
            viewModel.state.selectedComplaintId != nil ||
            !viewModel.state.plate.isEmpty ||
            !viewModel.state.addressQuery.isEmpty ||
            !viewModel.state.description.isEmpty ||
            !viewModel.state.notes.isEmpty ||
            !viewModel.state.occurredAtIso.isEmpty
    }

    private var draftContentKey: String {
        [
            viewModel.state.stage == .verify ? "verify" : "pick",
            viewModel.state.primaryMedia?.fileURL.absoluteString ?? "",
            viewModel.state.extraMedia.map { $0.fileURL.absoluteString }.joined(separator: "|"),
            viewModel.state.selectedComplaintId ?? "",
            viewModel.state.plate,
            viewModel.state.plateRegion,
            viewModel.state.addressQuery,
            viewModel.state.description,
            viewModel.state.notes,
            viewModel.state.occurredAtIso
        ].joined(separator: "||")
    }

    var body: some View {
        decoratedComposerContent
    }

    private var decoratedComposerContent: AnyView {
        var view = AnyView(composerContent)
        view = AnyView(view.overlay(alignment: .bottom) { detectionProgressChip })
        view = AnyView(view.sheet(isPresented: $showOccurredAtPicker) {
            ReportedDateTimeSheet(
                isoValue: viewModel.state.occurredAtIso,
                photoIsoValue: viewModel.state.photoOccurredAtIso
            ) { iso in
                viewModel.update(occurredAtIso: iso)
            }
            .presentationDetents([.height(328)])
            .presentationDragIndicator(.hidden)
        })
        view = AnyView(view.sheet(isPresented: $showPlateRegionSheet) {
            PlateRegionSheet(
                selectedValue: viewModel.state.plateRegion,
                showAllStates: $showAllPlateRegions,
                onSelected: { value in
                    viewModel.update(plateRegion: value)
                    showPlateRegionSheet = false
                }
            )
            .presentationDetents([.height(showAllPlateRegions ? 300 : 176)])
            .presentationDragIndicator(.hidden)
        })
        view = AnyView(view.sheet(isPresented: $showAddressMapSheet) {
            AddressMapSheet(
                initialLatitude: viewModel.state.latitude,
                initialLongitude: viewModel.state.longitude,
                initialAddress: viewModel.state.addressQuery.isEmpty ? viewModel.state.address : viewModel.state.addressQuery,
                onCancel: { showAddressMapSheet = false },
                onDone: { suggestion in
                    viewModel.chooseAddress(suggestion)
                    showAddressMapSheet = false
                }
            )
            .presentationDetents([.large])
            .presentationDragIndicator(.hidden)
        })
        view = AnyView(view.sheet(isPresented: $showReportTutorial) {
            NewReportTutorialSheet(
                scannerAvailable: RemoteConfigOverrides.shared.enableMediaScanner,
                scannerEnabled: $tutorialScannerEnabled,
                notificationsEnabled: $tutorialNotificationsEnabled,
                onSkip: {
                    ReportTutorialSettings.markSeen()
                    showReportTutorial = false
                },
                onComplete: {
                    Task { await completeReportTutorialFromButton() }
                }
            )
            .presentationDetents([.fraction(0.72)])
            .presentationDragIndicator(.hidden)
            .interactiveDismissDisabled()
        })
        view = AnyView(view.overlay(alignment: .bottom) {
            if showsBottomSubmitBar {
                submitBottomBar()
            }
        })
        view = AnyView(view.onAppear {
            onSubmitBarVisibilityChanged(showsBottomSubmitBar)
        })
        view = AnyView(view.onDisappear {
            onSubmitBarVisibilityChanged(false)
        })
        view = AnyView(view.onChange(of: showsBottomSubmitBar) { _, isVisible in
            onSubmitBarVisibilityChanged(isVisible)
        })
        view = AnyView(view.overlay { detectionProgressModal })
        view = AnyView(view.alert("Clear report?", isPresented: $showDiscardConfirmation) {
            Button("Cancel", role: .cancel) {}
            Button("Clear", role: .destructive) {
                viewModel.clearDraft()
                onDraftContentChanged(false)
            }
        } message: {
            Text("This will discard the current report draft.")
        })
        view = AnyView(view.onChange(of: viewModel.state.detectingPlates) { _, _ in
            detectionProgressMinimized = false
            if !viewModel.state.detectingPlates {
                videoScanPaused = false
            }
        })
        view = AnyView(view.onChange(of: viewModel.state.detectionFrameTimeSeconds) { _, newValue in
            if !videoScanPaused {
                videoPreviewScrubSeconds = newValue
            }
        })
        view = AnyView(view.onChange(of: viewModel.state.addressQuery) { oldValue, newValue in
            handleAddressQueryChanged(oldValue, newValue)
        })
        view = AnyView(view.onAppear {
            onDraftContentChanged(hasDraftContent)
            if !reportTutorialChecked {
                reportTutorialChecked = true
                if !ReportTutorialSettings.hasSeen {
                    tutorialScannerEnabled = true
                    tutorialNotificationsEnabled = true
                    showReportTutorial = true
                }
            }
        })
        view = AnyView(view.onChange(of: draftContentKey) { _, _ in
            onDraftContentChanged(hasDraftContent)
        })
        view = AnyView(view.onChange(of: viewModel.submittedReportObjectId) { _, objectId in
            guard let objectId, !objectId.isEmpty else { return }
            onReportSubmitted(objectId)
            viewModel.consumeSubmittedReport()
        })
        view = AnyView(view.onChange(of: clearRequest) { _, token in
            guard token != handledClearRequest else { return }
            handledClearRequest = token
            if hasDraftContent {
                showDiscardConfirmation = true
            }
        })
        view = AnyView(view.task(id: animatedComplaintIds.joined(separator: "|")) {
            await cycleComplaintAnimations()
        })
        view = AnyView(view.modifier(ComposerPickerModifier(
            singlePickerPresented: $singlePickerPresented,
            multiPickerPresented: $multiPickerPresented,
            pickedItem: $pickedItem,
            pickedItems: $pickedItems,
            maxSelectionCount: max(1, viewModel.remainingMediaSlots),
            handlePickedItem: handlePickedItem,
            handleAdditionalPickedItem: handleAdditionalPickedItem
        )))
        view = AnyView(view.task(id: "\(sharedMediaImportId ?? "")-\(viewModel.state.draftLoaded)") {
            guard let importId = sharedMediaImportId else { return }
            guard viewModel.state.draftLoaded else { return }
            await handleSharedMediaImport(id: importId)
            sharedMediaImportId = nil
        })
        view = AnyView(view.onChange(of: detectedDraftOpenRequest) { _, request in
            guard request != nil else { return }
            viewModel.reloadDraft()
            detectedDraftOpenRequest = nil
        })
        view = AnyView(view.sheet(
            isPresented: Binding(
                get: { viewModel.state.complaintSheetOpen },
                set: { _ in }
            )
        ) {
            pendingComplaintSheet
        })
        view = AnyView(view.alert("Process video for license plates?", isPresented: Binding(
            get: { viewModel.state.awaitingVideoProcessingDecision },
            set: { _ in }
        )) {
            Button("Process") { startVideoScan() }
            Button("Skip", role: .cancel) {
                viewModel.onVideoProcessingDecision(false)
            }
        } message: {
            Text("We can scan every video frame and show the best candidate plates for you to choose from.")
        })
        view = AnyView(view.confirmationDialog("Change complaint", isPresented: $showComplaintChooser, titleVisibility: .visible) {
            ForEach(complaintOptions) { option in
                Button(option.title) {
                    viewModel.updateSelectedComplaint(option.id)
                }
            }
            Button("Cancel", role: .cancel) {}
        })
        view = AnyView(view.sheet(isPresented: $showPlateCandidatesChooser) {
            PlateCandidatePickerSheet(
                candidates: viewModel.state.plateCandidates,
                selectedPlate: viewModel.state.selectedPlateCandidate,
                onSelected: { candidate in
                    viewModel.choosePlateCandidate(candidate)
                    showPlateCandidatesChooser = false
                },
                onDismiss: { showPlateCandidatesChooser = false }
            )
            .presentationDetents([.medium, .large])
            .presentationDragIndicator(.visible)
        })
        view = AnyView(view.sheet(item: $pendingPlateCandidate) { candidate in
            UsePlateCandidateSheet(
                candidate: candidate,
                sourceImage: pendingPlatePreviewImage,
                onUse: {
                    viewModel.choosePlateCandidate(candidate)
                    pendingPlateCandidate = nil
                    pendingPlatePreviewImage = nil
                },
                onDismiss: {
                    pendingPlateCandidate = nil
                    pendingPlatePreviewImage = nil
                }
            )
            .presentationDetents([.height(260)])
            .presentationDragIndicator(.hidden)
        })
        view = AnyView(view.alert(
            "Fix plate format?",
            isPresented: Binding(
                get: { viewModel.state.plateCorrectionPrompt != nil },
                set: { if !$0 { viewModel.dismissPlateCorrection() } }
            ),
            presenting: viewModel.state.plateCorrectionPrompt
        ) { prompt in
            Button("Use \(prompt.suggestedPlate)") {
                viewModel.acceptPlateCorrection()
            }
            Button("Keep") {
                viewModel.keepPlateCorrection()
            }
            Button("Edit", role: .cancel) {
                viewModel.dismissPlateCorrection()
            }
        } message: { prompt in
            Text("This looks like a \(prompt.label) plate. Use \(prompt.suggestedPlate) instead of \(prompt.rawPlate)?")
        })
        return view
    }

    @MainActor
    private func completeReportTutorialFromButton() async {
        await completeReportTutorial()
    }

    @MainActor
    private func completeReportTutorial() async {
        ReportTutorialSettings.markSeen()
        showReportTutorial = false
        IOSMediaScanner.shared.disableScannerFromSettings()
    }

    private var isDetectionSheetVisible: Bool {
        viewModel.state.detectingPlates &&
        viewModel.state.primaryMedia?.isVideo == true &&
        !viewModel.state.awaitingVideoProcessingDecision &&
        !detectionProgressMinimized
    }

    private var showsBottomSubmitBar: Bool {
        viewModel.state.stage == .verify && !isDetectionSheetVisible && !isLandscapeComposer
    }

    private var composerContent: some View {
        GeometryReader { geometry in
            let isLandscape = geometry.size.width > geometry.size.height
            ScrollViewReader { proxy in
                let verticalPadding: CGFloat = viewModel.state.stage == .verify ? (isLandscape ? 20 : 0) : 16
                let horizontalPadding: CGFloat = isLandscape ? 8 : 16
                let leadingPadding = horizontalPadding
                let trailingPadding = horizontalPadding + (isLandscape ? horizontalUnsafeAreaWidth : 0)
                let landscapeLeftColumnWidth = landscapeMediaColumnWidth(for: geometry.size.width)
                let landscapeColumnSpacing: CGFloat = 14
                let bottomPadding: CGFloat = viewModel.state.stage == .verify ? (isLandscape ? 24 + geometry.safeAreaInsets.bottom : 96 + geometry.safeAreaInsets.bottom) : 8
                let content = ScrollView {
                    Group {
                        switch viewModel.state.stage {
                        case .pickMedia:
                            if isLandscape && showEmbeddedLandscapeToolbar {
                                VStack(alignment: .leading, spacing: 10) {
                                    embeddedLandscapeToolbar
                                    pickMediaContent
                                }
                            } else {
                                pickMediaContent
                            }
                        case .verify:
                            verifyContent(isLandscape: isLandscape, availableSize: geometry.size)
                        }
                    }
                    .padding(.leading, leadingPadding)
                    .padding(.trailing, trailingPadding)
                    .padding(.vertical, verticalPadding)
                    .padding(.bottom, bottomPadding)
                }
                ZStack(alignment: .bottom) {
                    content
                        .task {
                            viewModel.loadDraft()
                        }
                        .onAppear {
                            isLandscapeComposer = isLandscape
                            composerBottomSafeArea = geometry.safeAreaInsets.bottom
                        }
                        .onChange(of: geometry.size) { _, newSize in
                            isLandscapeComposer = newSize.width > newSize.height
                        }
                        .onChange(of: geometry.safeAreaInsets.bottom) { _, newValue in
                            composerBottomSafeArea = newValue
                        }
                        .onChange(of: viewModel.state.primaryMedia?.fileURL.path) { _, newValue in
                            guard viewModel.state.stage == .verify, newValue != nil, !isLandscape else { return }
                            withAnimation(.easeInOut(duration: 0.25)) {
                                proxy.scrollTo(previewScrollId, anchor: .top)
                            }
                        }
                    if viewModel.state.stage == .verify && isLandscape && !isDetectionSheetVisible {
                        HStack(alignment: .bottom, spacing: landscapeColumnSpacing) {
                            Color.clear
                                .frame(width: landscapeLeftColumnWidth)
                            landscapeFabRow
                                .frame(maxWidth: .infinity)
                        }
                        .frame(
                            width: max(0, geometry.size.width - leadingPadding - trailingPadding),
                            height: geometry.size.height,
                            alignment: .bottom
                        )
                        .padding(.leading, leadingPadding)
                        .padding(.trailing, trailingPadding)
                        .padding(.bottom, max(geometry.safeAreaInsets.bottom, 18))
                    }
                }
                .frame(width: geometry.size.width, height: geometry.size.height, alignment: .bottom)
            }
        }
    }

    private var pendingComplaintSheet: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("What kind of complaint is this?")
                        .font(.title2.bold())
                    LazyVGrid(columns: complaintColumns, spacing: 12) {
                        ForEach(complaintOptions) { option in
                            ComplaintMediaTile(
                                option: option,
                                animate: option.id == activeAnimatedComplaintId,
                                showImage: viewModel.state.showComplaintImages
                            ) {
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

    private func handleAddressQueryChanged(_ oldValue: String, _ newValue: String) {
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

    private func startVideoScan(from startTimeSeconds: Double = 0, resetProgress: Bool = true) {
        let videoMedia = viewModel.state.primaryMedia
        if resetProgress {
            viewModel.onVideoProcessingDecision(true)
        }
        videoScanTask?.cancel()
        videoScanGeneration += 1
        videoScanPaused = false
        let scanGeneration = videoScanGeneration
        videoScanTask = Task {
            guard let videoMedia else {
                viewModel.finishDetection()
                return
            }
            let candidates = await NativeAlprEngine.shared.detectLicensePlatesInVideo(
                media: videoMedia,
                startTimeSeconds: startTimeSeconds,
                expectedComplaintHint: selectedComplaintDetectionHint
            ) { progress in
                let isCurrentScan = await MainActor.run { scanGeneration == videoScanGeneration }
                guard isCurrentScan else { return }
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
                    guard scanGeneration == videoScanGeneration else { return }
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
                    let isCurrentScan = await MainActor.run { scanGeneration == videoScanGeneration }
                    if !isCurrentScan { return }
                }
            }
            let isCurrentScan = await MainActor.run { scanGeneration == videoScanGeneration }
            guard isCurrentScan else { return }
            let topCandidate = candidates.first
            viewModel.finishDetection(
                candidates: candidates,
                inferredPlate: topCandidate?.plate,
                inferredState: topCandidate?.state
            )
        }
    }

    private func cycleComplaintAnimations() async {
        let ids = animatedComplaintIds
        guard !ids.isEmpty else { return }
        var index = ids.firstIndex(of: activeAnimatedComplaintId ?? "") ?? 0
        while !Task.isCancelled {
            let nextId = ids[index]
            await MainActor.run {
                activeAnimatedComplaintId = nextId
            }
            let duration = complaintOptions.first { $0.id == nextId }?.animationDuration ?? 1.8
            let boundedDuration = min(max(duration, 0.5), 8.0)
            let nanoseconds = UInt64((boundedDuration * 1_000_000_000).rounded())
            try? await Task.sleep(nanoseconds: nanoseconds)
            index = (index + 1) % ids.count
        }
    }

    @ViewBuilder
    private var pickMediaContent: some View {
        ScreenCard {
            VStack(spacing: 12) {
                Text("Upload Photo of Complaint")
                    .font(.system(size: 30, weight: .bold))
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
                Text("Pick a complaint to preselect it, or use Upload to choose after selecting media.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
                LazyVGrid(columns: complaintColumns, spacing: 10) {
                    ForEach(complaintOptions) { option in
                        ComplaintMediaTile(
                            option: option,
                            animate: option.id == activeAnimatedComplaintId,
                            showImage: viewModel.state.showComplaintImages
                        ) {
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
        if isDetectionSheetVisible {
            ZStack(alignment: .bottom) {
                Color.black.opacity(0.18)
                    .ignoresSafeArea()
                VStack(alignment: .leading, spacing: 0) {
                HStack {
                    Text("Scanning video")
                        .font(.title2.bold())
                    Spacer()
                    Button("Cancel") {
                        cancelVideoScan()
                    }
                    .font(.body.weight(.semibold))
                    .foregroundStyle(.red)
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
                            .frame(height: 310)
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
                                if videoScanPaused {
                                    let resumeSeconds = videoPreviewScrubSeconds
                                    let shouldResumeFromScrubPosition = abs(resumeSeconds - viewModel.state.detectionFrameTimeSeconds) > 0.25
                                    videoScanPaused = false
                                    if shouldResumeFromScrubPosition {
                                        startVideoScan(from: resumeSeconds, resetProgress: false)
                                    }
                                } else {
                                    videoScanPaused = true
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
                .frame(maxWidth: .infinity)
                .frame(maxHeight: .infinity)
                .background(Color(.systemBackground))
                .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
                .padding(.top, 10)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
            .ignoresSafeArea(.container, edges: .bottom)
            .transition(.opacity)
        }
    }

    private func selectVideoCandidate(_ candidate: ComposerState.PlateCandidate) {
        videoScanGeneration += 1
        videoScanTask?.cancel()
        videoScanTask = nil
        viewModel.choosePlateCandidate(candidate)
        viewModel.finishDetection(
            candidates: viewModel.state.plateCandidates.isEmpty ? [candidate] : viewModel.state.plateCandidates,
            inferredPlate: candidate.plate,
            inferredState: candidate.state
        )
    }

    private func cancelVideoScan() {
        videoScanGeneration += 1
        videoScanTask?.cancel()
        videoScanTask = nil
        videoScanPaused = false
        detectionProgressMinimized = false
        viewModel.cancelVideoProcessing()
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
    private func verifyContent(isLandscape: Bool, availableSize: CGSize) -> some View {
        let selectedCandidate = viewModel.state.plateCandidates.first { $0.plate == viewModel.state.selectedPlateCandidate }
            ?? viewModel.state.plateCandidates.first
        if isLandscape {
            HStack(alignment: .top, spacing: 14) {
                VStack(alignment: .leading, spacing: 10) {
                    if showEmbeddedLandscapeToolbar {
                        embeddedLandscapeToolbar
                    }
                    if let error = viewModel.state.error {
                        MessageView(text: error)
                    }
                    mediaPanel(selectedCandidate: selectedCandidate, expandPreview: true)
                    landscapeMediaActions
                }
                .frame(width: landscapeMediaColumnWidth(for: availableSize.width))
                VStack(alignment: .leading, spacing: 10) {
                    verifyFormContent(isLandscape: true)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            }
        } else {
            VStack(alignment: .leading, spacing: 10) {
                if let error = viewModel.state.error {
                    MessageView(text: error)
                }
                mediaPanel(selectedCandidate: selectedCandidate, expandPreview: false)
                addMoreMediaButton
                if viewModel.state.validationErrors.media != nil {
                    ValidationMessage(text: viewModel.state.validationErrors.media)
                }
                verifyFormContent(isLandscape: false)
            }
        }
    }

    @ViewBuilder
    private func mediaPanel(selectedCandidate: ComposerState.PlateCandidate?, expandPreview: Bool) -> some View {
        if let media = viewModel.state.primaryMedia {
            PrimarySubmissionPreview(
                media: media,
                selectedCandidate: selectedCandidate,
                candidates: viewModel.state.plateCandidates,
                selectedPlate: viewModel.state.selectedPlateCandidate,
                onCandidateTapped: { candidate, image in
                    pendingPlatePreviewImage = image
                    pendingPlateCandidate = candidate
                },
                onCandidateConfirmedFromFullScreen: { candidate in
                    viewModel.choosePlateCandidate(candidate)
                },
                onRemove: { viewModel.removeMedia(media) }
            )
            .frame(maxHeight: expandPreview ? .infinity : nil)
            .id(previewScrollId)
        }
    }

    private var landscapeMediaActions: some View {
        VStack(spacing: 10) {
            addMoreMediaButton
            if viewModel.state.validationErrors.media != nil {
                ValidationMessage(text: viewModel.state.validationErrors.media)
            }
        }
    }

    private var embeddedLandscapeToolbar: some View {
        ZStack {
            Text("New Report")
                .font(.system(size: 20, weight: .regular))
                .lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .center)

            HStack {
                Button(action: onMenuTapped) {
                    Image(systemName: "line.3.horizontal")
                        .font(.system(size: 22, weight: .semibold))
                        .foregroundStyle(.primary)
                        .frame(width: 34, height: 34)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)

                Spacer(minLength: 0)
            }
        }
        .padding(.top, 6)
        .padding(.bottom, 2)
    }

    private var landscapeFabRow: some View {
        HStack {
            Button(action: onClearTapped) {
                Label("Clear", systemImage: "xmark")
                    .font(.body.weight(.semibold))
                    .padding(.horizontal, 18)
                    .frame(height: 52)
            }
            .buttonStyle(.plain)
            .foregroundStyle(Color.reportedOrange)
            .background(Color(.systemBackground))
            .clipShape(Capsule())
            .shadow(color: Color.black.opacity(0.16), radius: 10, x: 0, y: 4)
            .opacity(shellHasDraftContent ? 1 : 0)
            .disabled(!shellHasDraftContent)

            Spacer()

            Button(action: submitReport) {
                Label(viewModel.state.loading ? "Submitting" : "Submit", systemImage: "paperplane.fill")
                    .font(.body.weight(.semibold))
                    .padding(.horizontal, 18)
                    .frame(height: 52)
            }
            .buttonStyle(.plain)
            .foregroundStyle(.white)
            .background(Color.reportedOrange)
            .clipShape(Capsule())
            .shadow(color: Color.black.opacity(0.16), radius: 10, x: 0, y: 4)
            .disabled(viewModel.state.loading)
        }
    }

    private var addMoreMediaButton: some View {
        SecondaryButton(title: viewModel.state.primaryMedia == nil ? "Add photo or video" : "Add more photos or videos") {
            pendingComplaintId = viewModel.state.selectedComplaintId
            if viewModel.state.primaryMedia != nil {
                if viewModel.remainingMediaSlots > 0 {
                    multiPickerPresented = true
                } else {
                    viewModel.markMediaLimitReached()
                }
            } else {
                singlePickerPresented = true
            }
        }
    }

    private var submitInlineButton: some View {
        VStack(spacing: 8) {
            submitButton(height: 56)
            submitProgressContent
        }
    }

    private func submitBottomBar() -> some View {
        VStack(spacing: 0) {
            if viewModel.state.loading {
                submitProgressContent
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                    .background(Color(.systemBackground))
            }
            submitButton(height: 64)
            Color.reportedOrange
                .frame(height: bottomUnsafeAreaHeight)
        }
        .frame(maxWidth: .infinity)
        .background(Color.reportedOrange)
        .ignoresSafeArea(.container, edges: .bottom)
    }

    private var bottomUnsafeAreaHeight: CGFloat {
        let windowBottom = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first { $0.isKeyWindow }?
            .safeAreaInsets
            .bottom ?? 0
        return max(composerBottomSafeArea, windowBottom, 0)
    }

    private var horizontalUnsafeAreaWidth: CGFloat {
        let insets = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first { $0.isKeyWindow }?
            .safeAreaInsets ?? .zero
        return max(insets.left, insets.right, 0)
    }

    private func submitButton(height: CGFloat) -> some View {
        Button(action: submitReport) {
            Text(viewModel.state.loading ? "Submitting..." : "Submit Report")
                .font(.system(size: 20, weight: .semibold))
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .frame(height: height)
        }
        .buttonStyle(.plain)
        .background(Color.reportedOrange)
        .disabled(viewModel.state.loading)
    }

    @ViewBuilder
    private var submitProgressContent: some View {
        if viewModel.state.loading {
            VStack(spacing: 6) {
                ProgressView(value: viewModel.state.submitProgress ?? 0)
                    .progressViewStyle(.linear)
                Text(viewModel.state.submitMessage ?? "Submitting report")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, alignment: .center)
            }
        }
    }

    @ViewBuilder
    private func verifyFormContent(isLandscape: Bool) -> some View {
        HStack(alignment: .top, spacing: 12) {
            ComplaintPickerField(
                value: complaintOptions.first(where: { $0.id == viewModel.state.selectedComplaintId })?.title ?? "Select complaint",
                isError: viewModel.state.validationErrors.complaint != nil
            ) {
                showComplaintChooser = true
            }
            .frame(minWidth: isLandscape ? 160 : 130, maxWidth: isLandscape ? .infinity : 156)
            PlateInputField(
                text: Binding(get: { viewModel.state.plate }, set: { viewModel.update(plate: String($0.prefix(8))) }),
                isError: viewModel.state.validationErrors.plate != nil,
                showCandidateButton: !viewModel.state.plateCandidates.isEmpty,
                onShowCandidates: { showPlateCandidatesChooser = true }
            )
            .frame(width: isLandscape ? 150 : 134)
            PickerField(
                title: "State",
                value: viewModel.state.plateRegion,
                isError: viewModel.state.validationErrors.plateRegion != nil,
                showsChevron: false,
                alignment: .center
            ) {
                showAllPlateRegions = false
                showPlateRegionSheet = true
            }
            .frame(width: isLandscape ? 76 : 62)
        }
        FieldErrorGroup([
            viewModel.state.validationErrors.complaint,
            viewModel.state.validationErrors.plate,
            viewModel.state.validationErrors.plateRegion
        ])
        if isLandscape {
            HStack(alignment: .top, spacing: 12) {
                addressField
                occurredAtField
            }
        } else {
            addressField
        }
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
        if !isLandscape {
            occurredAtField
        }
        if !viewModel.state.extraMedia.isEmpty {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(viewModel.state.extraMedia) { media in
                        MediaAttachmentChip(media: media)
                    }
                }
            }
        }
        if isLandscape {
            HStack(alignment: .top, spacing: 12) {
                InputField(title: "Description (public facing)", text: Binding(get: { viewModel.state.description }, set: { viewModel.update(description: $0) }), fieldMinHeight: 104)
                    .onChange(of: viewModel.state.description) { _, _ in viewModel.persistCurrentDraft() }
                InputField(title: "Notes (for your records)", text: Binding(get: { viewModel.state.notes }, set: { viewModel.update(notes: $0) }), fieldMinHeight: 104)
                    .onChange(of: viewModel.state.notes) { _, _ in viewModel.persistCurrentDraft() }
            }
        } else {
            InputField(title: "Description (public facing)", text: Binding(get: { viewModel.state.description }, set: { viewModel.update(description: $0) }))
                .onChange(of: viewModel.state.description) { _, _ in viewModel.persistCurrentDraft() }
            InputField(title: "Notes (for your records)", text: Binding(get: { viewModel.state.notes }, set: { viewModel.update(notes: $0) }))
                .onChange(of: viewModel.state.notes) { _, _ in viewModel.persistCurrentDraft() }
        }
    }

    private var addressField: some View {
        InputField(
            title: "Address",
            text: Binding(
                get: { viewModel.state.addressQuery },
                set: { viewModel.updateAddressQuery($0) }
            ),
            isError: viewModel.state.validationErrors.address != nil,
            onClear: {
                viewModel.updateAddressQuery("")
            },
            trailingIconSystemName: "map",
            onTrailingIcon: {
                showAddressMapSheet = true
            }
        )
    }

    private var occurredAtField: some View {
        PickerField(
            title: "Occurred At",
            value: viewModel.state.occurredAtIso.reportDateTimeDisplay,
            isError: viewModel.state.validationErrors.occurredAt != nil
        ) {
            showOccurredAtPicker = true
        }
    }

    private func submitReport() {
        guard viewModel.prepareSubmit() else { return }
        if isAuthorized {
            viewModel.submit()
        } else {
            onRequireLogin()
        }
    }

    private func handlePickedItem(_ item: PhotosPickerItem) async {
        guard let media = await loadSubmissionMedia(from: item) else { return }
        if let complaintId = pendingComplaintId {
            viewModel.onPrimaryMediaChosen(media, complaintId: complaintId)
        } else if viewModel.state.stage == .verify, viewModel.state.selectedComplaintId != nil {
            guard viewModel.addExtraMedia(media) else {
                pendingComplaintId = nil
                return
            }
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
                photoOccurredAtIso: media.isVideo ? nil : metadata.occurredAtIso,
                latitude: metadata.latitude,
                longitude: metadata.longitude,
                inferredState: inferredState,
                inferredAddress: inferredAddress
            )
            if !media.isVideo {
                viewModel.setDetectionProgress(message: "Detecting plates", progress: 0.65)
                let candidates = await NativeAlprEngine.shared.detectLicensePlates(
                    media: media,
                    expectedComplaintHint: selectedComplaintDetectionHint
                )
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
                photoOccurredAtIso: media.isVideo ? nil : metadata.occurredAtIso,
                latitude: metadata.latitude,
                longitude: metadata.longitude,
                inferredState: inferredState,
                inferredAddress: inferredAddress
            )
            if !media.isVideo {
                viewModel.setDetectionProgress(message: "Detecting plates", progress: 0.65)
                let candidates = await NativeAlprEngine.shared.detectLicensePlates(
                    media: media,
                    expectedComplaintHint: selectedComplaintDetectionHint
                )
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

private struct ComposerPickerModifier: ViewModifier {
    @Binding var singlePickerPresented: Bool
    @Binding var multiPickerPresented: Bool
    @Binding var pickedItem: PhotosPickerItem?
    @Binding var pickedItems: [PhotosPickerItem]
    let maxSelectionCount: Int
    let handlePickedItem: (PhotosPickerItem) async -> Void
    let handleAdditionalPickedItem: (PhotosPickerItem) async -> Void

    func body(content: Content) -> some View {
        content
            .photosPicker(
                isPresented: $singlePickerPresented,
                selection: $pickedItem,
                matching: .any(of: [.images, .videos]),
                preferredItemEncoding: .current
            )
            .photosPicker(
                isPresented: $multiPickerPresented,
                selection: $pickedItems,
                maxSelectionCount: maxSelectionCount,
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
    }
}

private struct ComplaintOption: Identifiable {
    let id: String
    let title: String
    let imageName: String
    let imageExtension: String
    var lottieName: String? = nil
}

private let complaintOptionsList: [ComplaintOption] = [
    ComplaintOption(id: "blocked_bike_lane", title: "Blocked bike lane", imageName: "bikelane", imageExtension: "svg"),
    ComplaintOption(id: "blocked_crosswalk", title: "Blocked crosswalk", imageName: "crosswalk", imageExtension: "svg"),
    ComplaintOption(id: "ran_red_light", title: "Ran red light", imageName: "ranredlight", imageExtension: "jpg", lottieName: "ranredlight"),
    ComplaintOption(id: "drove_recklessly", title: "Drove recklessly", imageName: "reckless", imageExtension: "png", lottieName: "reckless"),
    ComplaintOption(id: "parked_illegally", title: "Parked illegally", imageName: "parkedillegally", imageExtension: "jpg", lottieName: "parkedillegally")
]

private func complaintOptionsFor(_ categories: [ComplaintCategory]) -> [ComplaintOption] {
    [
        complaintOptionFor(
            categories,
            fallback: ComplaintOption(id: "blocked_bike_lane", title: "Blocked bike lane", imageName: "bikelane", imageExtension: "svg"),
            keywords: ["bike lane"]
        ),
        complaintOptionFor(
            categories,
            fallback: ComplaintOption(id: "blocked_crosswalk", title: "Blocked crosswalk", imageName: "crosswalk", imageExtension: "svg"),
            keywords: ["crosswalk"]
        ),
        complaintOptionFor(
            categories,
            fallback: ComplaintOption(id: "ran_red_light", title: "Ran red light", imageName: "ranredlight", imageExtension: "jpg", lottieName: "ranredlight"),
            keywords: ["red light", "stop sign"]
        ),
        complaintOptionFor(
            categories,
            fallback: ComplaintOption(id: "drove_recklessly", title: "Drove recklessly", imageName: "reckless", imageExtension: "png", lottieName: "reckless"),
            keywords: ["reckless", "aggressive"]
        ),
        complaintOptionFor(
            categories,
            fallback: ComplaintOption(id: "parked_illegally", title: "Parked illegally", imageName: "parkedillegally", imageExtension: "jpg", lottieName: "parkedillegally"),
            keywords: ["parked"]
        )
    ]
}

private func complaintOptionFor(
    _ categories: [ComplaintCategory],
    fallback: ComplaintOption,
    keywords: [String]
) -> ComplaintOption {
    guard let category = categories.first(where: { category in
        let name = category.name.lowercased()
        return keywords.contains { name.contains($0) }
    }) else {
        return fallback
    }
    return ComplaintOption(
        id: category.id,
        title: displayComplaintTitle(category.name),
        imageName: fallback.imageName,
        imageExtension: fallback.imageExtension,
        lottieName: fallback.lottieName
    )
}

private func displayComplaintTitle(_ title: String) -> String {
    switch title.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() {
    case "blocked the bike lane", "blocked the biek lane":
        return "Blocked bike lane"
    case "blocked the crosswalk":
        return "Blocked crosswalk"
    default:
        return title
    }
}

private struct ExtractedSubmissionMetadata {
    let occurredAtIso: String?
    let latitude: Double?
    let longitude: Double?
}

private struct ComplaintMediaTile: View {
    let option: ComplaintOption
    var animate = false
    var showImage = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 8) {
                if showImage {
                    ComplaintOptionImage(option: option, animate: animate)
                        .frame(height: 96)
                        .frame(maxWidth: .infinity)
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                }
                Text(option.title)
                    .font(.subheadline.weight(.semibold))
                    .multilineTextAlignment(.center)
                    .foregroundStyle(.primary)
                    .frame(maxWidth: .infinity)
                    .lineLimit(2)
                    .minimumScaleFactor(0.82)
            }
            .padding(8)
            .frame(maxWidth: .infinity, minHeight: 150, alignment: .top)
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
    var animate = false

    var body: some View {
        if let lottieName = option.lottieName {
            ZStack {
                option.artBackground
                LottieAssetView(
                    animationName: lottieName,
                    isPlaying: animate,
                    contentMode: .scaleAspectFill
                )
                .scaleEffect(option.id == "ran_red_light" ? 1.18 : 1, anchor: .bottom)
                .clipped()
            }
        } else if option.imageExtension.lowercased() == "svg" {
            ZStack {
                option.artBackground
                SvgAssetView(name: option.imageName)
            }
        } else if let image = complaintUIImage(option: option) {
            ZStack {
                option.artBackground
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(contentMode: option.id == "drove_recklessly" || option.id == "parked_illegally" ? .fill : .fit)
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

private extension ComplaintOption {
    var artBackground: Color {
        id == "ran_red_light" ? .black : Color(.secondarySystemBackground)
    }

    var animationDuration: TimeInterval? {
        guard let lottieName else { return nil }
        if let url = Bundle.main.url(forResource: lottieName, withExtension: "json", subdirectory: "complaints"),
           let animation = LottieAnimation.filepath(url.path) {
            return animation.duration
        }
        if let url = Bundle.main.url(forResource: lottieName, withExtension: "json"),
           let animation = LottieAnimation.filepath(url.path) {
            return animation.duration
        }
        return LottieAnimation.named(lottieName)?.duration
    }
}

private struct SvgAssetView: UIViewRepresentable {
    let name: String

    func makeUIView(context: Context) -> WKWebView {
        let webView = WKWebView(frame: .zero)
        webView.isOpaque = false
        webView.backgroundColor = .clear
        webView.scrollView.backgroundColor = .clear
        webView.scrollView.isScrollEnabled = false
        webView.scrollView.contentInsetAdjustmentBehavior = .never
        webView.isUserInteractionEnabled = false
        loadSvg(in: webView)
        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        loadSvg(in: webView)
    }

    private func loadSvg(in webView: WKWebView) {
        guard let url = svgUrl, let svg = try? String(contentsOf: url) else { return }
        let html = """
        <html>
          <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
              html, body { margin: 0; width: 100%; height: 100%; overflow: hidden; background: transparent; }
              svg { width: 100%; height: 100%; display: block; }
            </style>
          </head>
          <body>\(svg)</body>
        </html>
        """
        webView.loadHTMLString(html, baseURL: url.deletingLastPathComponent())
    }

    private var svgUrl: URL? {
        Bundle.main.url(forResource: name, withExtension: "svg", subdirectory: "complaints")
            ?? Bundle.main.url(forResource: name, withExtension: "svg")
    }
}

private struct LottieAssetView: UIViewRepresentable {
    let animationName: String
    var isPlaying = true
    var contentMode: UIView.ContentMode = .scaleAspectFit

    func makeUIView(context: Context) -> UIView {
        let container = UIView()
        container.backgroundColor = .clear

        let animationView = LottieAnimationView()
        animationView.translatesAutoresizingMaskIntoConstraints = false
        animationView.contentMode = contentMode
        animationView.loopMode = .playOnce
        animationView.backgroundBehavior = .pauseAndRestore
        animationView.animation = loadAnimation()

        container.addSubview(animationView)
        NSLayoutConstraint.activate([
            animationView.leadingAnchor.constraint(equalTo: container.leadingAnchor),
            animationView.trailingAnchor.constraint(equalTo: container.trailingAnchor),
            animationView.topAnchor.constraint(equalTo: container.topAnchor),
            animationView.bottomAnchor.constraint(equalTo: container.bottomAnchor)
        ])

        context.coordinator.animationView = animationView
        updatePlayback(animationView)
        return container
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        guard let animationView = context.coordinator.animationView else { return }
        animationView.contentMode = contentMode
        updatePlayback(animationView)
    }

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    private func loadAnimation() -> LottieAnimation? {
        if let url = Bundle.main.url(forResource: animationName, withExtension: "json", subdirectory: "complaints") {
            return LottieAnimation.filepath(url.path)
        }
        if let url = Bundle.main.url(forResource: animationName, withExtension: "json") {
            return LottieAnimation.filepath(url.path)
        }
        return LottieAnimation.named(animationName)
    }

    private func updatePlayback(_ animationView: LottieAnimationView) {
        if isPlaying {
            animationView.loopMode = .playOnce
            if animationView.isAnimationPlaying != true {
                animationView.currentProgress = 0
                animationView.play()
            }
        } else {
            animationView.stop()
            animationView.currentProgress = 0
        }
    }

    final class Coordinator {
        weak var animationView: LottieAnimationView?
    }
}

private struct UploadMediaTile: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 8) {
                Spacer()
                Image(systemName: "square.and.arrow.up")
                    .font(.system(size: 30, weight: .medium))
                    .foregroundStyle(Color.reportedOrange)
                Text("Upload")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.primary)
                Spacer()
            }
            .frame(maxWidth: .infinity, minHeight: 150)
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
    let candidates: [ComposerState.PlateCandidate]
    let selectedPlate: String?
    let onCandidateTapped: (ComposerState.PlateCandidate, UIImage?) -> Void
    let onCandidateConfirmedFromFullScreen: (ComposerState.PlateCandidate) -> Void
    let onRemove: () -> Void
    @State private var showVideoPlayer = false
    @State private var showImageViewer = false

    var body: some View {
        ZStack(alignment: .topTrailing) {
            if media.isVideo {
                Button {
                    showVideoPlayer = true
                } label: {
                    ZStack(alignment: .bottomLeading) {
                        if let preview = selectedCandidate?.videoFramePreview {
                            ZStack {
                                Color.black
                                Image(uiImage: preview)
                                    .resizable()
                                    .scaledToFit()
                                StillImagePlateOverlay(
                                    imageSize: preview.size,
                                    candidates: [selectedCandidate].compactMap { $0 },
                                    selectedPlate: selectedCandidate?.plate,
                                    focalPoint: nil,
                                    contentMode: .fit
                                )
                            }
                            .frame(maxWidth: .infinity)
                            .frame(height: 220)
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
                ZStack {
                    PlateAwareImage(
                        image: image,
                        focalPoint: stillImageFocalPoint
                    )
                    StillImagePlateOverlay(
                        imageSize: image.size,
                        candidates: candidates,
                        selectedPlate: selectedPlate,
                        focalPoint: stillImageFocalPoint,
                        contentMode: .fill,
                        onCandidateTapped: { candidate in
                            onCandidateTapped(candidate, image)
                        }
                    )
                }
                .frame(maxWidth: .infinity)
                .frame(height: 220)
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .contentShape(RoundedRectangle(cornerRadius: 12))
                .onTapGesture {
                    showImageViewer = true
                }
                .fullScreenCover(isPresented: $showImageViewer) {
                    FullScreenImageViewer(
                        image: image,
                        candidates: candidates,
                        selectedPlate: selectedPlate,
                        focalPoint: stillImageFocalPoint,
                        onDismiss: { showImageViewer = false },
                        onCandidateConfirmed: onCandidateConfirmedFromFullScreen
                    )
                }
            }
            Button(action: onRemove) {
                Image(systemName: "xmark")
                    .font(.system(size: 22, weight: .semibold))
                    .foregroundStyle(.primary)
                    .frame(width: 52, height: 52)
                    .background(Color(.systemBackground).opacity(0.94))
                    .clipShape(Circle())
                    .shadow(color: .black.opacity(0.18), radius: 8, y: 3)
            }
            .buttonStyle(.plain)
            .offset(x: 12, y: 8)
        }
        .padding(.trailing, 12)
    }

    private var stillImageFocalPoint: CGPoint? {
        selectedCandidate?.normalizedFocalPoint
            ?? candidates.first { $0.plate == selectedPlate }?.normalizedFocalPoint
            ?? candidates.max(by: { $0.confidence < $1.confidence })?.normalizedFocalPoint
    }
}

private enum PlateOverlayContentMode {
    case fit
    case fill
}

private struct PlateImageLayout {
    let displayedSize: CGSize
    let offset: CGPoint

    init(
        imageSize: CGSize,
        viewSize: CGSize,
        contentMode: PlateOverlayContentMode,
        focalPoint: CGPoint? = nil
    ) {
        let safeImageWidth = max(imageSize.width, 1)
        let safeImageHeight = max(imageSize.height, 1)
        let widthScale = viewSize.width / safeImageWidth
        let heightScale = viewSize.height / safeImageHeight
        let scale: CGFloat
        switch contentMode {
        case .fit:
            scale = min(widthScale, heightScale)
        case .fill:
            scale = max(widthScale, heightScale)
        }

        displayedSize = CGSize(width: safeImageWidth * scale, height: safeImageHeight * scale)

        guard contentMode == .fill, let focalPoint else {
            offset = CGPoint(
                x: (viewSize.width - displayedSize.width) / 2,
                y: (viewSize.height - displayedSize.height) / 2
            )
            return
        }

        let desiredX = viewSize.width / 2 - min(1, max(0, focalPoint.x)) * displayedSize.width
        let desiredY = viewSize.height / 2 - min(1, max(0, focalPoint.y)) * displayedSize.height
        offset = CGPoint(
            x: Self.clampedOffset(desiredX, contentLength: displayedSize.width, viewLength: viewSize.width),
            y: Self.clampedOffset(desiredY, contentLength: displayedSize.height, viewLength: viewSize.height)
        )
    }

    private static func clampedOffset(_ value: CGFloat, contentLength: CGFloat, viewLength: CGFloat) -> CGFloat {
        guard contentLength > viewLength else {
            return (viewLength - contentLength) / 2
        }
        return min(0, max(viewLength - contentLength, value))
    }
}

private struct PlateAwareImage: View {
    let image: UIImage
    let focalPoint: CGPoint?
    var contentMode: PlateOverlayContentMode = .fill

    var body: some View {
        GeometryReader { proxy in
            let layout = PlateImageLayout(
                imageSize: image.size,
                viewSize: proxy.size,
                contentMode: contentMode,
                focalPoint: focalPoint
            )
            Image(uiImage: image)
                .resizable()
                .frame(width: layout.displayedSize.width, height: layout.displayedSize.height)
                .position(
                    x: layout.offset.x + layout.displayedSize.width / 2,
                    y: layout.offset.y + layout.displayedSize.height / 2
                )
        }
        .clipped()
    }
}

private struct StillImagePlateOverlay: View {
    let imageSize: CGSize
    let candidates: [ComposerState.PlateCandidate]
    let selectedPlate: String?
    var focalPoint: CGPoint?
    var contentMode: PlateOverlayContentMode = .fill
    var onCandidateTapped: ((ComposerState.PlateCandidate) -> Void)?

    var body: some View {
        GeometryReader { proxy in
            let viewSize = proxy.size
            let layout = PlateImageLayout(
                imageSize: imageSize,
                viewSize: viewSize,
                contentMode: contentMode,
                focalPoint: focalPoint
            )
            ZStack(alignment: .topLeading) {
                ForEach(candidates) { candidate in
                    if let bounds = candidate.bounds, bounds.width > 0, bounds.height > 0 {
                        let rect = CGRect(
                            x: layout.offset.x + bounds.minX * layout.displayedSize.width,
                            y: layout.offset.y + bounds.minY * layout.displayedSize.height,
                            width: bounds.width * layout.displayedSize.width,
                            height: bounds.height * layout.displayedSize.height
                        )
                        Rectangle()
                            .stroke(candidate.plate == selectedPlate ? Color.green : Color.reportedOrange, lineWidth: 3)
                            .frame(width: max(1, rect.width), height: max(1, rect.height))
                            .position(x: rect.midX, y: rect.midY)
                        if let onCandidateTapped {
                            Rectangle()
                                .fill(Color.black.opacity(0.001))
                                .frame(width: max(44, rect.width + 20), height: max(44, rect.height + 20))
                                .position(x: rect.midX, y: rect.midY)
                                .onTapGesture {
                                    onCandidateTapped(candidate)
                                }
                        }
                    }
                }
            }
            .frame(width: viewSize.width, height: viewSize.height)
            .clipped()
        }
        .allowsHitTesting(onCandidateTapped != nil)
    }
}

private struct FullScreenImageViewer: View {
    let image: UIImage
    let candidates: [ComposerState.PlateCandidate]
    let selectedPlate: String?
    let focalPoint: CGPoint?
    let onDismiss: () -> Void
    let onCandidateConfirmed: (ComposerState.PlateCandidate) -> Void

    @State private var scale: CGFloat = 1
    @State private var lastScale: CGFloat = 1
    @State private var offset: CGSize = .zero
    @State private var lastOffset: CGSize = .zero
    @State private var pendingCandidate: ComposerState.PlateCandidate?

    var body: some View {
        GeometryReader { proxy in
            ZStack(alignment: .topTrailing) {
                Color.black.ignoresSafeArea()
                ZStack {
                    PlateAwareImage(
                        image: image,
                        focalPoint: focalPoint,
                        contentMode: .fill
                    )
                    StillImagePlateOverlay(
                        imageSize: image.size,
                        candidates: candidates,
                        selectedPlate: selectedPlate,
                        focalPoint: focalPoint,
                        contentMode: .fill,
                        onCandidateTapped: { pendingCandidate = $0 }
                    )
                }
                .frame(width: proxy.size.width, height: proxy.size.height)
                .scaleEffect(scale)
                .offset(offset)
                .contentShape(Rectangle())
                .gesture(
                    MagnificationGesture()
                        .onChanged { value in
                            scale = min(max(lastScale * value, 1), 6)
                            if scale <= 1.01 {
                                offset = .zero
                            }
                        }
                        .onEnded { _ in
                            scale = min(max(scale, 1), 6)
                            lastScale = scale
                            if scale <= 1.01 {
                                offset = .zero
                                lastOffset = .zero
                            }
                        }
                )
                .simultaneousGesture(
                    DragGesture()
                        .onChanged { value in
                            guard scale > 1 else { return }
                            offset = CGSize(
                                width: lastOffset.width + value.translation.width,
                                height: lastOffset.height + value.translation.height
                            )
                        }
                        .onEnded { _ in
                            lastOffset = offset
                        }
                )
                .onTapGesture(count: 2) {
                    if scale > 1 {
                        scale = 1
                        lastScale = 1
                        offset = .zero
                        lastOffset = .zero
                    } else {
                        scale = 2
                        lastScale = 2
                    }
                }
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
        .sheet(item: $pendingCandidate) { candidate in
            UsePlateCandidateSheet(
                candidate: candidate,
                sourceImage: image,
                onUse: {
                    onCandidateConfirmed(candidate)
                    pendingCandidate = nil
                },
                onDismiss: {
                    pendingCandidate = nil
                }
            )
            .presentationDetents([.height(260)])
            .presentationDragIndicator(.hidden)
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
                if let correctionText = candidate.plateCorrectionText {
                    Text(correctionText)
                        .font(.caption)
                        .foregroundStyle(isSelected ? .white.opacity(0.9) : Color.reportedOrange)
                }
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

private struct UsePlateCandidateSheet: View {
    let candidate: ComposerState.PlateCandidate
    let sourceImage: UIImage?
    let onUse: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack {
                Text("Use this plate?")
                    .font(.title3.weight(.semibold))
                Spacer()
                Button(action: onDismiss) {
                    Image(systemName: "xmark")
                        .font(.body.weight(.semibold))
                        .foregroundStyle(.primary)
                        .frame(width: 34, height: 34)
                        .background(Color(.secondarySystemBackground))
                        .clipShape(Circle())
                }
                .buttonStyle(.plain)
            }

            HStack(alignment: .center, spacing: 14) {
                PlateCandidateCropPreview(candidate: candidate, sourceImage: sourceImage)
                    .frame(width: 112, height: 58)
                VStack(alignment: .leading, spacing: 4) {
                    Text(candidate.plate)
                        .font(.title2.weight(.semibold))
                    if let correctionText = candidate.plateCorrectionText {
                        Text(correctionText)
                            .font(.caption)
                            .foregroundStyle(Color.reportedOrange)
                    }
                    Text("\(Int(candidate.confidence * 100))% confidence")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    if let classifierText = candidate.stateClassifierText {
                        Text(classifierText)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    if let plateTypeText = candidate.plateTypeText {
                        Text(plateTypeText)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            }

            HStack(spacing: 12) {
                SecondaryButton(title: "No", action: onDismiss)
                PrimaryButton(title: "Yes", action: onUse)
            }
        }
        .padding(20)
        .presentationCornerRadius(24)
        .background(Color(.systemBackground))
    }
}

private struct PlateCandidatePickerSheet: View {
    let candidates: [ComposerState.PlateCandidate]
    let selectedPlate: String?
    let onSelected: (ComposerState.PlateCandidate) -> Void
    let onDismiss: () -> Void

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: 10) {
                    ForEach(candidates) { candidate in
                        PlateCandidatePickerRow(
                            candidate: candidate,
                            isSelected: candidate.plate == selectedPlate
                        ) {
                            onSelected(candidate)
                        }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            }
            .navigationTitle("Possible plates")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done", action: onDismiss)
                        .foregroundStyle(Color.reportedOrange)
                }
            }
        }
    }
}

private struct PlateCandidatePickerRow: View {
    let candidate: ComposerState.PlateCandidate
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                PlateCandidateCropPreview(candidate: candidate)
                VStack(alignment: .leading, spacing: 4) {
                    Text(candidate.plate)
                        .font(.title3.weight(.semibold))
                        .foregroundStyle(.primary)
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)
                    if let correctionText = candidate.plateCorrectionText {
                        Text(correctionText)
                            .font(.caption)
                            .foregroundStyle(Color.reportedOrange)
                    }
                    Text("\(Int(candidate.confidence * 100))% detection confidence")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    if let stateText = candidate.stateClassifierText {
                        Text(stateText)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    if let typeText = candidate.plateTypeText {
                        Text(typeText)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                if isSelected {
                    Text("Selected")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(Color.green)
                }
            }
            .padding(10)
            .background(Color(.systemBackground))
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(isSelected ? Color.green : Color(.separator), lineWidth: isSelected ? 2 : 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
    }
}

private struct PlateCandidateCropPreview: View {
    let candidate: ComposerState.PlateCandidate
    var sourceImage: UIImage? = nil

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 6)
                .fill(Color(.secondarySystemBackground))
            if let crop = candidate.plateCropPreview ?? sourceImage?.croppedPlatePreview(for: candidate) {
                Image(uiImage: crop)
                    .resizable()
                    .scaledToFit()
            } else if let frame = candidate.videoFramePreview {
                Image(uiImage: frame)
                    .resizable()
                    .scaledToFit()
            } else {
                Text("No image")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
        .frame(width: 96, height: 48)
        .clipShape(RoundedRectangle(cornerRadius: 6))
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
    let url = Bundle.main.url(
        forResource: option.imageName,
        withExtension: option.imageExtension,
        subdirectory: "complaints"
    ) ?? Bundle.main.url(
        forResource: option.imageName,
        withExtension: option.imageExtension
    )
    guard let url else {
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
    do {
        let fileURL = try PersistentMediaStore.save(
            data: data,
            preferredName: fileName,
            fileExtension: ext
        )
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

    var latitude = gps?[kCGImagePropertyGPSLatitude] as? Double
    var longitude = gps?[kCGImagePropertyGPSLongitude] as? Double

    if let latitudeRef = gps?[kCGImagePropertyGPSLatitudeRef] as? String, latitudeRef.uppercased() == "S" {
        latitude = latitude.map(-)
    }
    if let longitudeRef = gps?[kCGImagePropertyGPSLongitudeRef] as? String, longitudeRef.uppercased() == "W" {
        longitude = longitude.map(-)
    }

    return ExtractedSubmissionMetadata(
        occurredAtIso: extractExifDateTimeIso(exif: exif, tiff: tiff, gps: gps),
        latitude: latitude,
        longitude: longitude
    )
}

private func extractExifDateTimeIso(
    exif: [CFString: Any]?,
    tiff: [CFString: Any]?,
    gps: [CFString: Any]?
) -> String? {
    let candidates: [(CFString, CFString?, CFString?)] = [
        (kCGImagePropertyExifDateTimeOriginal, kCGImagePropertyExifOffsetTimeOriginal, kCGImagePropertyExifSubsecTimeOriginal),
        (kCGImagePropertyExifDateTimeDigitized, kCGImagePropertyExifOffsetTimeDigitized, kCGImagePropertyExifSubsecTimeDigitized),
        (kCGImagePropertyExifDateTimeOriginal, kCGImagePropertyExifOffsetTime, kCGImagePropertyExifSubsecTime),
    ]
    for (dateKey, offsetKey, subsecondKey) in candidates {
        let rawDate = exif?[dateKey] as? String
        let rawOffset = offsetKey.flatMap { exif?[$0] as? String }
        let rawSubsecond = subsecondKey.flatMap { exif?[$0] as? String }
        if let iso = parseExifDateTimeIso(rawDate, offset: rawOffset, subsecond: rawSubsecond) {
            return iso
        }
    }
    if let iso = parseExifDateTimeIso(
        tiff?[kCGImagePropertyTIFFDateTime] as? String,
        offset: nil,
        subsecond: nil
    ) {
        return iso
    }
    return parseExifGpsDateTimeIso(
        dateStamp: gps?[kCGImagePropertyGPSDateStamp] as? String,
        timeStamp: gps?[kCGImagePropertyGPSTimeStamp] as? String
    )
}

private func parseExifDateTimeIso(
    _ raw: String?,
    offset rawOffset: String?,
    subsecond rawSubsecond: String?
) -> String? {
    guard let raw, !raw.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }
    let formatter = DateFormatter()
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.dateFormat = "yyyy:MM:dd HH:mm:ss"
    formatter.timeZone = .current
    guard var date = formatter.date(from: raw) else { return nil }
    if let fraction = normalizedSubsecond(rawSubsecond) {
        date = date.addingTimeInterval(fraction)
    }
    if let offset = normalizedExifOffset(rawOffset) {
        let offsetFormatter = DateFormatter()
        offsetFormatter.locale = Locale(identifier: "en_US_POSIX")
        offsetFormatter.dateFormat = "yyyy:MM:dd HH:mm:ssXXXXX"
        offsetFormatter.timeZone = TimeZone(secondsFromGMT: 0)
        if let offsetDate = offsetFormatter.date(from: "\(raw)\(offset)") {
            date = offsetDate.addingTimeInterval(normalizedSubsecond(rawSubsecond) ?? 0)
        }
    }
    return isoStringUTC(from: date)
}

private func parseExifGpsDateTimeIso(dateStamp: String?, timeStamp: String?) -> String? {
    guard let dateStamp, let timeStamp else { return nil }
    let raw = "\(dateStamp.trimmingCharacters(in: .whitespacesAndNewlines)) \(timeStamp.trimmingCharacters(in: .whitespacesAndNewlines))"
    let formatter = DateFormatter()
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.dateFormat = "yyyy:MM:dd HH:mm:ss"
    formatter.timeZone = TimeZone(secondsFromGMT: 0)
    return formatter.date(from: raw).map(isoStringUTC)
}

private func normalizedExifOffset(_ value: String?) -> String? {
    guard let value else { return nil }
    let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
    if trimmed.range(of: #"^[+-]\d{2}:\d{2}$"#, options: .regularExpression) != nil {
        return trimmed
    }
    if trimmed.range(of: #"^[+-]\d{4}$"#, options: .regularExpression) != nil {
        let index = trimmed.index(trimmed.startIndex, offsetBy: 3)
        return "\(trimmed[..<index]):\(trimmed[index...])"
    }
    return nil
}

private func normalizedSubsecond(_ value: String?) -> TimeInterval? {
    guard let value else { return nil }
    let digits = value.filter(\.isNumber)
    guard !digits.isEmpty, let whole = Double(digits) else { return nil }
    return whole / pow(10, Double(digits.count))
}

private func isoStringUTC(from date: Date) -> String {
    let formatter = ISO8601DateFormatter()
    formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    formatter.timeZone = TimeZone(secondsFromGMT: 0)
    return formatter.string(from: date)
}

private extension String {
    var photoTimeDisplay: String {
        let isoFormatter = ISO8601DateFormatter()
        isoFormatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let fallbackIsoFormatter = ISO8601DateFormatter()
        fallbackIsoFormatter.formatOptions = [.withInternetDateTime]
        let date = isoFormatter.date(from: self) ?? fallbackIsoFormatter.date(from: self)
        guard let date else { return self }
        return date.formatted(date: .abbreviated, time: .shortened)
    }
}

private func reverseGeocodeNyc(latitude: Double, longitude: Double) async -> ComposerState.AddressSuggestion? {
    var components = URLComponents(string: "https://geosearch.planninglabs.nyc/v2/reverse")
    components?.queryItems = [
        URLQueryItem(name: "point.lat", value: String(latitude)),
        URLQueryItem(name: "point.lon", value: String(longitude)),
        URLQueryItem(name: "size", value: "1")
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
    let openReportObjectId: String?
    let onOpenedReport: () -> Void
    @StateObject private var viewModel = ReportsViewModel()
    @State private var expandedReports: Set<String> = []
    @State private var pendingDeleteReport: ReportSummary?

    var body: some View {
        if !isAuthorized {
            ScrollView {
                ScreenCard {
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
            }
            .onAppear {
                openSubmittedReport(openReportObjectId)
            }
            .onChange(of: openReportObjectId) { _, objectId in
                openSubmittedReport(objectId)
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

    private func openSubmittedReport(_ objectId: String?) {
        guard let objectId, !objectId.isEmpty else { return }
        expandedReports.insert(objectId)
        viewModel.openReport(objectId: objectId)
        onOpenedReport()
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
        Section {
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
                .foregroundStyle(.primary)
                .tint(.primary)
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
            if !detail.description_.isEmpty {
                Text(detail.description_).font(.subheadline)
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

    var body: some View {
        GeometryReader { geometry in
            let isLandscape = geometry.size.width > geometry.size.height
            Group {
                if !isAuthorized {
                    ScrollView {
                        ScreenCard {
                            MessageView(text: "Sign in to edit your profile and manage your account.")
                            PrimaryButton(title: "Login", action: onRequireLogin)
                        }
                        .padding(isLandscape ? 12 : 16)
                    }
                } else {
                    ScrollView {
                        ScreenCard {
                            if let error = viewModel.state.error { MessageView(text: error) }
                            profileFields(isLandscape: isLandscape)
                            Toggle("I'm willing to testify by phone if needed.", isOn: Binding(
                                get: { viewModel.state.testify },
                                set: { viewModel.update(testify: $0) }
                            ))
                            .disabled(!viewModel.state.editing)
                            if viewModel.state.editing {
                                PrimaryButton(title: viewModel.state.loading ? "Saving..." : "Save") { viewModel.save() }
                            } else {
                                PrimaryButton(title: "Edit Profile") { viewModel.toggleEditing() }
                            }
                            Button("Logout", action: onLogout)
                                .foregroundStyle(Color.reportedOrange)
                        }
                        .padding(isLandscape ? 12 : 16)
                    }
                }
            }
            .background(Color(.systemBackground))
        }
        .task {
            if isAuthorized {
                viewModel.load()
            }
        }
    }

    @ViewBuilder
    private func profileFields(isLandscape: Bool) -> some View {
        if isLandscape {
            HStack(alignment: .top, spacing: 12) {
                InputField(title: "First Name", text: Binding(get: { viewModel.state.firstName }, set: { viewModel.update(firstName: $0) }), disabled: !viewModel.state.editing)
                InputField(title: "Last Name", text: Binding(get: { viewModel.state.lastName }, set: { viewModel.update(lastName: $0) }), disabled: !viewModel.state.editing)
            }
            HStack(alignment: .top, spacing: 12) {
                InputField(title: "Phone", text: Binding(get: { viewModel.state.phone }, set: { viewModel.update(phone: $0) }), disabled: !viewModel.state.editing)
                InputField(title: "Email", text: Binding(get: { viewModel.state.email }, set: { viewModel.update(email: $0) }), disabled: !viewModel.state.editing, keyboardType: .emailAddress, textContentType: .emailAddress, textInputAutocapitalization: .never, autocorrectionDisabled: true)
            }
        } else {
            InputField(title: "First Name", text: Binding(get: { viewModel.state.firstName }, set: { viewModel.update(firstName: $0) }), disabled: !viewModel.state.editing)
            InputField(title: "Last Name", text: Binding(get: { viewModel.state.lastName }, set: { viewModel.update(lastName: $0) }), disabled: !viewModel.state.editing)
            InputField(title: "Phone", text: Binding(get: { viewModel.state.phone }, set: { viewModel.update(phone: $0) }), disabled: !viewModel.state.editing)
            InputField(title: "Email", text: Binding(get: { viewModel.state.email }, set: { viewModel.update(email: $0) }), disabled: !viewModel.state.editing, keyboardType: .emailAddress, textContentType: .emailAddress, textInputAutocapitalization: .never, autocorrectionDisabled: true)
        }
    }
}

struct SettingsScreen: View {
    @StateObject private var viewModel = ProfileViewModel()
    @State private var mediaScannerFeatureEnabled = RemoteConfigOverrides.shared.enableMediaScanner
    @State private var offlineProcessingFeatureEnabled = RemoteConfigOverrides.shared.enableOfflinePhotoProcessing
    @State private var mediaScannerEnabled = IOSMediaScannerSettings.isEnabled
    @State private var notificationsEnabled = IOSMediaScannerSettings.notificationsEnabled
    @State private var offlineProcessingEnabled = IOSMediaScannerSettings.isOfflineProcessingEnabled
    let onThemeModeSelected: (AppThemeMode) -> Void

    var body: some View {
        GeometryReader { geometry in
            let isLandscape = geometry.size.width > geometry.size.height
            ScrollView {
                ScreenCard {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Appearance")
                            .font(.headline)
                        HStack(spacing: 8) {
                            ThemeChip(title: "System", isSelected: viewModel.state.themeMode == .system) {
                                setTheme(.system)
                            }
                            ThemeChip(title: "Light", isSelected: viewModel.state.themeMode == .light) {
                                setTheme(.light)
                            }
                            ThemeChip(title: "Dark", isSelected: viewModel.state.themeMode == .dark) {
                                setTheme(.dark)
                            }
                        }
                    }
                    if mediaScannerFeatureEnabled || offlineProcessingFeatureEnabled {
                        mediaSettingsContent(isLandscape: isLandscape)
                    }
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Sources")
                            .font(.headline)
                        Text("Reported is not a government app. Reports are prepared from your submitted media and sent through the configured reporting service.")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                }
                .padding(isLandscape ? 12 : 16)
            }
            .background(Color(.systemBackground))
        }
        .task {
            viewModel.load()
            refreshMediaFlags()
        }
        .onReceive(NotificationCenter.default.publisher(for: .reportedRemoteConfigUpdated)) { _ in
            refreshMediaFlags()
        }
    }

    @ViewBuilder
    private func mediaSettingsContent(isLandscape: Bool) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Media")
                .font(.headline)
            if isLandscape {
                HStack(alignment: .top, spacing: 12) {
                    if offlineProcessingFeatureEnabled {
                        offlineProcessingToggle
                    }
                    if mediaScannerFeatureEnabled {
                        scannerNotificationsToggle
                    }
                }
                if mediaScannerFeatureEnabled {
                    mediaScannerToggle
                }
            } else {
                if offlineProcessingFeatureEnabled {
                    offlineProcessingToggle
                }
                if mediaScannerFeatureEnabled {
                    mediaScannerToggle
                    scannerNotificationsToggle
                }
            }
        }
    }

    private var offlineProcessingToggle: some View {
        SettingsToggleRow(
            title: "Allow offline photo processing",
            description: "Use on-device computer vision for media you choose or share with Reported.",
            isOn: Binding(
                get: { offlineProcessingEnabled },
                set: { isOn in
                    IOSMediaScannerSettings.isOfflineProcessingEnabled = isOn
                    offlineProcessingEnabled = IOSMediaScannerSettings.isOfflineProcessingEnabled
                }
            )
        )
    }

    private var mediaScannerToggle: some View {
        SettingsToggleRow(
            title: "Media scanner",
            description: "Background photo library scanning is feature flagged and defaults off.",
            isOn: Binding(
                get: { mediaScannerEnabled },
                set: { isOn in
                    setMediaScannerEnabled(isOn)
                }
            )
        )
    }

    private var scannerNotificationsToggle: some View {
        SettingsToggleRow(
            title: "Scanner notifications",
            description: "Show a notification when Reported finds a likely report candidate.",
            isOn: Binding(
                get: { notificationsEnabled },
                set: { isOn in
                    IOSMediaScannerSettings.notificationsEnabled = isOn
                    notificationsEnabled = isOn
                }
            )
        )
    }

    private func setTheme(_ mode: AppThemeMode) {
        viewModel.setThemeMode(mode)
        onThemeModeSelected(mode)
    }

    private func refreshMediaFlags() {
        mediaScannerFeatureEnabled = RemoteConfigOverrides.shared.enableMediaScanner
        offlineProcessingFeatureEnabled = RemoteConfigOverrides.shared.enableOfflinePhotoProcessing
        mediaScannerEnabled = IOSMediaScannerSettings.isEnabled
        notificationsEnabled = IOSMediaScannerSettings.notificationsEnabled
        offlineProcessingEnabled = IOSMediaScannerSettings.isOfflineProcessingEnabled
    }

    private func setMediaScannerEnabled(_ isOn: Bool) {
        if isOn {
            Task {
                let enabled = await IOSMediaScanner.shared.enableScannerFromSettings()
                await MainActor.run {
                    mediaScannerEnabled = enabled
                }
            }
        } else {
            IOSMediaScanner.shared.disableScannerFromSettings()
            mediaScannerEnabled = false
        }
    }
}

private struct NewReportTutorialSheet: View {
    let scannerAvailable: Bool
    @Binding var scannerEnabled: Bool
    @Binding var notificationsEnabled: Bool
    let onSkip: () -> Void
    let onComplete: () -> Void
    @State private var page = 0
    private var pageCount: Int { scannerAvailable ? 3 : 2 }
    private var lastPage: Int { pageCount - 1 }

    var body: some View {
        VStack(spacing: 14) {
            TabView(selection: $page) {
                ReportTutorialPage(
                    icon: "1",
                    title: "Report faster",
                    bodyText: "Reported can fill in plate, time, and address details from your photo or video so you spend less time typing."
                )
                .tag(0)
                ReportTutorialPage(
                    icon: "2",
                    title: "Photo time and location",
                    bodyText: "Media/location access lets us read image metadata for the location and time of incident. We use it only to prefill your report."
                )
                .tag(1)
                if scannerAvailable {
                    ReportTutorialScannerPage()
                        .tag(2)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
            .frame(height: 390)

            HStack(spacing: 8) {
                ForEach(0..<pageCount, id: \.self) { index in
                    Capsule()
                        .fill(index == page ? Color.reportedOrange : Color(.separator))
                        .frame(width: index == page ? 18 : 8, height: 8)
                        .animation(.easeInOut(duration: 0.18), value: page)
                }
            }

            HStack(spacing: 12) {
                Button("Skip", action: onSkip)
                    .font(.body.weight(.semibold))
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)

                PrimaryButton(title: page == lastPage ? "Done" : "Continue") {
                    if page < lastPage {
                        withAnimation(.easeInOut(duration: 0.22)) {
                            page += 1
                        }
                    } else {
                        onComplete()
                    }
                }
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 18)
        .padding(.bottom, 14)
        .onChange(of: scannerAvailable) { _, isAvailable in
            if !isAvailable && page > lastPage {
                page = lastPage
            }
        }
    }
}

private struct ReportTutorialPage: View {
    let icon: String
    let title: String
    let bodyText: String

    var body: some View {
        VStack(spacing: 16) {
            Text(icon)
                .font(.title.bold())
                .foregroundStyle(Color.reportedOrange)
                .frame(width: 70, height: 70)
                .background(Color.reportedOrange.opacity(0.12))
                .clipShape(Circle())
            Text(title)
                .font(.title2.bold())
                .multilineTextAlignment(.center)
            Text(bodyText)
                .font(.body)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(.horizontal, 6)
    }
}

private struct ReportTutorialScannerPage: View {
    var body: some View {
        VStack(spacing: 10) {
            Image(systemName: "photo.on.rectangle.angled")
                .font(.system(size: 26, weight: .semibold))
                .foregroundStyle(Color.reportedOrange)
                .frame(width: 58, height: 58)
                .background(Color.reportedOrange.opacity(0.12))
                .clipShape(Circle())
            Text("Private media checks")
                .font(.title2.bold())
                .multilineTextAlignment(.center)
            Text("Reported scans only the photos and videos you choose or share with the app. We do not keep broad access to your photo library.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(.horizontal, 6)
    }
}

private struct SettingsToggleRow: View {
    let title: String
    let description: String
    @Binding var isOn: Bool

    var body: some View {
        HStack(alignment: .center, spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.subheadline.weight(.semibold))
                Text(description)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 8)
            Toggle(title, isOn: $isOn)
                .labelsHidden()
                .tint(Color.reportedOrange)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
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

private struct SubmittedReportSnackbar: View {
    let onView: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Text("Report Submitted")
                .font(.callout.weight(.semibold))
                .foregroundStyle(.white)
            Spacer(minLength: 8)
            Button("View", action: onView)
                .font(.callout.weight(.bold))
                .foregroundStyle(Color.reportedOrange)
            Button(action: onDismiss) {
                Image(systemName: "xmark")
                    .font(.caption.weight(.bold))
                    .foregroundStyle(.white.opacity(0.85))
                    .padding(6)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Dismiss")
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(Color.black.opacity(0.88))
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .shadow(color: .black.opacity(0.22), radius: 12, y: 4)
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
                sourceSize: candidates.first?.videoFramePreview?.size,
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
    @State private var currentPlaybackSeconds = 0.0

    private var shouldShowDetectionOverlay: Bool {
        let detectionSeconds = candidate.videoFrameTimeSeconds ?? 0
        return abs(currentPlaybackSeconds - detectionSeconds) <= 0.75
    }

    var body: some View {
        ZStack(alignment: .topTrailing) {
            Color.black.ignoresSafeArea()
            VideoPlaybackView(
                url: media.fileURL,
                startSeconds: candidate.videoFrameTimeSeconds ?? 0,
                onTimeChanged: { currentPlaybackSeconds = $0 }
            )
            .ignoresSafeArea()
            if shouldShowDetectionOverlay {
                VideoPlateOverlay(
                    candidates: [candidate],
                    sourceSize: candidate.videoFramePreview?.size,
                    selectedPlate: candidate.plate,
                    onCandidateSelected: { _ in }
                )
                .ignoresSafeArea()
            }
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
    let onTimeChanged: (Double) -> Void

    func makeUIView(context: Context) -> PlayerContainerView {
        let view = PlayerContainerView()
        let player = AVPlayer(url: url)
        view.playerLayer.player = player
        view.playerLayer.videoGravity = .resizeAspect
        context.coordinator.url = url
        context.coordinator.onTimeChanged = onTimeChanged
        context.coordinator.attachTimeObserver(to: player)
        player.seek(to: CMTime(seconds: max(0, startSeconds), preferredTimescale: 600), toleranceBefore: .zero, toleranceAfter: .zero)
        player.play()
        return view
    }

    func updateUIView(_ view: PlayerContainerView, context: Context) {
        context.coordinator.onTimeChanged = onTimeChanged
        guard context.coordinator.url != url else { return }
        let player = AVPlayer(url: url)
        context.coordinator.url = url
        view.playerLayer.player = player
        context.coordinator.attachTimeObserver(to: player)
        player.seek(to: CMTime(seconds: max(0, startSeconds), preferredTimescale: 600), toleranceBefore: .zero, toleranceAfter: .zero)
        player.play()
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(url: url, onTimeChanged: onTimeChanged)
    }

    final class Coordinator {
        var url: URL
        var player: AVPlayer?
        var onTimeChanged: (Double) -> Void
        private var timeObserver: Any?

        init(url: URL, onTimeChanged: @escaping (Double) -> Void) {
            self.url = url
            self.onTimeChanged = onTimeChanged
        }

        func attachTimeObserver(to player: AVPlayer) {
            if let timeObserver, let oldPlayer = self.player {
                oldPlayer.removeTimeObserver(timeObserver)
                self.timeObserver = nil
            }
            self.player = player
            let interval = CMTime(seconds: 0.1, preferredTimescale: 600)
            timeObserver = player.addPeriodicTimeObserver(forInterval: interval, queue: .main) { [weak self] time in
                self?.onTimeChanged(CMTimeGetSeconds(time))
            }
        }

        deinit {
            if let timeObserver, let player {
                player.removeTimeObserver(timeObserver)
            }
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
    var sourceSize: CGSize? = nil
    let selectedPlate: String?
    let onCandidateSelected: (ComposerState.PlateCandidate) -> Void

    var body: some View {
        GeometryReader { proxy in
            let mediaRect = aspectFitRect(sourceSize: sourceSize, viewportSize: proxy.size)
            ZStack(alignment: .topLeading) {
                ForEach(candidates) { candidate in
                    if let bounds = candidate.bounds, bounds.width > 0, bounds.height > 0 {
                        let rect = CGRect(
                            x: mediaRect.minX + bounds.minX * mediaRect.width,
                            y: mediaRect.minY + bounds.minY * mediaRect.height,
                            width: bounds.width * mediaRect.width,
                            height: bounds.height * mediaRect.height
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

    private func aspectFitRect(sourceSize: CGSize?, viewportSize: CGSize) -> CGRect {
        guard
            let sourceSize,
            sourceSize.width > 0,
            sourceSize.height > 0,
            viewportSize.width > 0,
            viewportSize.height > 0
        else {
            return CGRect(origin: .zero, size: viewportSize)
        }
        let scale = min(viewportSize.width / sourceSize.width, viewportSize.height / sourceSize.height)
        let width = sourceSize.width * scale
        let height = sourceSize.height * scale
        return CGRect(
            x: (viewportSize.width - width) / 2,
            y: (viewportSize.height - height) / 2,
            width: width,
            height: height
        )
    }
}

struct InputField: View {
    let title: String
    @Binding var text: String
    var disabled = false
    var isError = false
    var fieldMinHeight: CGFloat? = nil
    var keyboardType: UIKeyboardType = .default
    var textContentType: UITextContentType? = nil
    var textInputAutocapitalization: TextInputAutocapitalization? = nil
    var autocorrectionDisabled = false
    var onClear: (() -> Void)? = nil
    var trailingIconSystemName: String? = nil
    var onTrailingIcon: (() -> Void)? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.headline)
                .foregroundStyle(isError ? Color.red : Color.reportedOrange)
                .lineLimit(1)
                .minimumScaleFactor(0.68)
                .allowsTightening(true)
            HStack(spacing: 8) {
                TextField(title, text: $text, axis: .vertical)
                    .disabled(disabled)
                    .keyboardType(keyboardType)
                    .textContentType(textContentType)
                    .textInputAutocapitalization(textInputAutocapitalization)
                    .autocorrectionDisabled(autocorrectionDisabled)
                    .foregroundStyle(.primary)
                    .tint(.primary)
                if let onClear, !text.isEmpty {
                    Button(action: onClear) {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundStyle(.secondary)
                    }
                    .buttonStyle(.plain)
                }
                if let trailingIconSystemName, let onTrailingIcon {
                    Button(action: onTrailingIcon) {
                        Image(systemName: trailingIconSystemName)
                            .font(.body.weight(.semibold))
                            .foregroundStyle(.primary)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 11)
            .frame(minHeight: fieldMinHeight, alignment: .topLeading)
            .background(isError ? Color.reportedFieldErrorBackground : Color(.systemBackground))
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(isError ? Color.red : Color(.separator), lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: 8))
        }
    }
}

private struct ComplaintPickerField: View {
    let value: String
    var isError = false
    let action: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Complaint")
                .font(.headline)
                .foregroundStyle(isError ? Color.red : Color.reportedOrange)
            Button(action: action) {
                AutoFitFieldText(value.isEmpty ? "Select complaint" : value)
                    .foregroundStyle(.primary)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 11)
                    .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
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

private struct PlateInputField: View {
    @Binding var text: String
    var isError = false
    let showCandidateButton: Bool
    let onShowCandidates: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Plate")
                .font(.headline)
                .foregroundStyle(isError ? Color.red : Color.reportedOrange)
            HStack(spacing: 6) {
                TextField("Plate", text: $text)
                    .textInputAutocapitalization(.characters)
                    .autocorrectionDisabled()
                    .font(.body)
                    .foregroundStyle(.primary)
                    .tint(.primary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.55)
                    .allowsTightening(true)
                if showCandidateButton {
                    Button(action: onShowCandidates) {
                        Text("?")
                            .font(.headline.bold())
                            .foregroundStyle(Color.reportedOrange)
                            .frame(width: 12, height: 24)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 11)
            .frame(minHeight: 44)
            .background(isError ? Color.reportedFieldErrorBackground : Color(.systemBackground))
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(isError ? Color.red : Color(.separator), lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: 8))
        }
    }
}

private struct ValidationMessage: View {
    let text: String?

    var body: some View {
        if let text, !text.isEmpty {
            Text(text)
                .font(.caption)
                .foregroundStyle(Color.red)
                .padding(.horizontal, 4)
        }
    }
}

private struct FieldErrorGroup: View {
    let errors: [String?]

    init(_ errors: [String?]) {
        self.errors = errors
    }

    var body: some View {
        let messages = errors.compactMap { $0 }.filter { !$0.isEmpty }
        if !messages.isEmpty {
            VStack(alignment: .leading, spacing: 4) {
                ForEach(messages, id: \.self) { message in
                    ValidationMessage(text: message)
                }
            }
        }
    }
}

private extension ComposerState.PlateCandidate {
    var normalizedFocalPoint: CGPoint? {
        guard let bounds else { return nil }
        return CGPoint(
            x: min(1, max(0, bounds.midX)),
            y: min(1, max(0, bounds.midY))
        )
    }

    var candidateSummary: String {
        var parts = [plate]
        if let state { parts.append(state) }
        parts.append("\(Int(confidence * 100))%")
        if let plateTypeLabel { parts.append(plateTypeLabel) }
        return parts.joined(separator: "  ")
    }

    var stateClassifierText: String? {
        guard let state else { return nil }
        if let stateConfidence {
            return "State classifier: \(state) (\(Int(stateConfidence * 100))%)"
        }
        return "State classifier: \(state)"
    }

    var plateTypeText: String? {
        guard let plateTypeLabel else { return nil }
        return "Plate type: \(plateTypeLabel)"
    }

    var plateCorrectionText: String? {
        guard wasPlateCorrected, let rawPlateText, rawPlateText != plate else { return nil }
        return "Corrected from OCR: \(rawPlateText)"
    }
}

private extension UIImage {
    func croppedPlatePreview(for candidate: ComposerState.PlateCandidate) -> UIImage? {
        guard let bounds = candidate.bounds, let cgImage else { return nil }
        let paddedBounds = bounds.insetBy(dx: -bounds.width * 0.35, dy: -bounds.height * 0.65)
        let clampedBounds = CGRect(
            x: min(1, max(0, paddedBounds.minX)),
            y: min(1, max(0, paddedBounds.minY)),
            width: min(1, max(0, paddedBounds.width)),
            height: min(1, max(0, paddedBounds.height))
        ).intersection(CGRect(x: 0, y: 0, width: 1, height: 1))
        let pixelRect = CGRect(
            x: clampedBounds.minX * CGFloat(cgImage.width),
            y: clampedBounds.minY * CGFloat(cgImage.height),
            width: clampedBounds.width * CGFloat(cgImage.width),
            height: clampedBounds.height * CGFloat(cgImage.height)
        ).integral.intersection(CGRect(x: 0, y: 0, width: cgImage.width, height: cgImage.height))
        guard pixelRect.width > 0, pixelRect.height > 0, let cropped = cgImage.cropping(to: pixelRect) else {
            return nil
        }
        return UIImage(cgImage: cropped, scale: scale, orientation: imageOrientation)
    }
}

private struct PlateRegionSheet: View {
    let selectedValue: String
    @Binding var showAllStates: Bool
    let onSelected: (String) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Choose State")
                .font(.title2.bold())
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if showAllStates {
                        FlowLayout(spacing: 8) {
                            ForEach(allPlateRegions, id: \.self) { option in
                                ThemeChip(title: option, isSelected: selectedValue == option) {
                                    onSelected(option)
                                }
                            }
                        }
                    } else {
                        FlowLayout(spacing: 8) {
                            ForEach(preferredPlateRegions.filter { $0 != "OTHER" }, id: \.self) { option in
                                ThemeChip(title: option, isSelected: selectedValue == option) {
                                    onSelected(option)
                                }
                            }
                        }
                        ThemeChip(title: "OTHER", isSelected: selectedValue == "OTHER") {
                            showAllStates = true
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 18)
        .padding(.bottom, 14)
    }
}

private struct AddressMapSheet: View {
    private static let defaultCoordinate = CLLocationCoordinate2D(latitude: 40.71305, longitude: -74.00723)
    private static let defaultSpan = MKCoordinateSpan(latitudeDelta: 0.0045, longitudeDelta: 0.0045)

    let initialLatitude: Double?
    let initialLongitude: Double?
    let initialAddress: String
    let onCancel: () -> Void
    let onDone: (ComposerState.AddressSuggestion) -> Void

    @State private var position: MapCameraPosition
    @State private var selectedCoordinate: CLLocationCoordinate2D
    @State private var resolvedAddress: String
    @State private var pendingSuggestion: ComposerState.AddressSuggestion?
    @State private var currentRegion: MKCoordinateRegion
    @State private var lookupInFlight = false
    @State private var lookupTask: Task<Void, Never>?
    @State private var addressCache: [String: ComposerState.AddressSuggestion] = [:]

    init(
        initialLatitude: Double?,
        initialLongitude: Double?,
        initialAddress: String,
        onCancel: @escaping () -> Void,
        onDone: @escaping (ComposerState.AddressSuggestion) -> Void
    ) {
        self.initialLatitude = initialLatitude
        self.initialLongitude = initialLongitude
        self.initialAddress = initialAddress
        self.onCancel = onCancel
        self.onDone = onDone
        let coordinate = Self.validCoordinate(latitude: initialLatitude, longitude: initialLongitude)
            ?? Self.defaultCoordinate
        let region = MKCoordinateRegion(center: coordinate, span: Self.defaultSpan)
        _selectedCoordinate = State(initialValue: coordinate)
        _resolvedAddress = State(initialValue: initialAddress)
        _pendingSuggestion = State(initialValue: initialAddress.isEmpty ? nil : ComposerState.AddressSuggestion(
            label: initialAddress,
            latitude: coordinate.latitude,
            longitude: coordinate.longitude
        ))
        _currentRegion = State(initialValue: region)
        _position = State(initialValue: .region(region))
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Button("Cancel", action: onCancel)
                    .font(.body.weight(.semibold))
                    .foregroundStyle(Color.reportedOrange)
                Spacer()
                Button("Done") {
                    onDone(pendingSuggestion ?? fallbackSuggestion(for: selectedCoordinate))
                }
                .font(.body.weight(.semibold))
                .foregroundStyle(Color.reportedOrange)
            }
            .padding(.horizontal, 20)
            .padding(.top, 18)

            VStack(alignment: .leading, spacing: 6) {
                Text("Pan map to change address.")
                    .font(.title2.bold())
                Text(addressStatusText)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
                    .minimumScaleFactor(0.8)
            }
            .padding(.horizontal, 20)

            ZStack {
                Map(position: $position, interactionModes: [.pan, .zoom])
                    .onMapCameraChange(frequency: .onEnd) { context in
                        currentRegion = context.region
                        selectedCoordinate = context.region.center
                        scheduleLookup(for: context.region.center)
                    }
                    .clipShape(RoundedRectangle(cornerRadius: 18))

                VStack(spacing: 0) {
                    ZStack {
                        Circle()
                            .fill(Color.reportedOrange)
                            .frame(width: 56, height: 56)
                        Image(systemName: "mappin")
                            .font(.system(size: 30, weight: .semibold))
                            .foregroundStyle(.white)
                    }
                    Rectangle()
                        .fill(Color.reportedOrange)
                        .frame(width: 3, height: 24)
                }
                .offset(y: -18)
                .shadow(color: .black.opacity(0.25), radius: 8, y: 3)
                .allowsHitTesting(false)

                VStack(spacing: 8) {
                    Button {
                        zoom(by: 0.5)
                    } label: {
                        Image(systemName: "plus")
                            .font(.body.weight(.bold))
                            .frame(width: 42, height: 42)
                    }
                    Divider()
                        .frame(width: 30)
                    Button {
                        zoom(by: 2.0)
                    } label: {
                        Image(systemName: "minus")
                            .font(.body.weight(.bold))
                            .frame(width: 42, height: 42)
                    }
                }
                .foregroundStyle(.primary)
                .background(.regularMaterial)
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .shadow(color: .black.opacity(0.16), radius: 10, y: 3)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .trailing)
                .padding(.trailing, 14)
                .padding(.top, 14)
                .frame(maxHeight: .infinity, alignment: .top)
                .buttonStyle(.plain)
            }
            .onAppear {
                if Self.validCoordinate(latitude: initialLatitude, longitude: initialLongitude) == nil {
                    let region = MKCoordinateRegion(center: Self.defaultCoordinate, span: Self.defaultSpan)
                    currentRegion = region
                    selectedCoordinate = region.center
                    position = .region(region)
                }
            }
            .padding(.horizontal, 20)
            .frame(maxHeight: .infinity)
        }
        .background(Color(.systemBackground))
        .onAppear {
            if initialAddress.isEmpty {
                scheduleLookup(for: selectedCoordinate, delayNanoseconds: 0)
            }
        }
        .onDisappear {
            lookupTask?.cancel()
        }
    }

    private func zoom(by factor: Double) {
        let span = MKCoordinateSpan(
            latitudeDelta: min(max(currentRegion.span.latitudeDelta * factor, 0.0012), 0.08),
            longitudeDelta: min(max(currentRegion.span.longitudeDelta * factor, 0.0012), 0.08)
        )
        let region = MKCoordinateRegion(center: selectedCoordinate, span: span)
        currentRegion = region
        position = .region(region)
        scheduleLookup(for: region.center)
    }

    private var addressStatusText: String {
        if lookupInFlight {
            return "Finding address..."
        }
        return resolvedAddress.isEmpty ? coordinateText(selectedCoordinate) : resolvedAddress
    }

    private func scheduleLookup(for coordinate: CLLocationCoordinate2D, delayNanoseconds: UInt64 = 700_000_000) {
        let key = addressCacheKey(for: coordinate)
        if let cached = addressCache[key] {
            selectedCoordinate = coordinate
            pendingSuggestion = cached
            resolvedAddress = cached.label
            lookupInFlight = false
            return
        }

        lookupTask?.cancel()
        lookupInFlight = true
        lookupTask = Task {
            if delayNanoseconds > 0 {
                try? await Task.sleep(nanoseconds: delayNanoseconds)
            }
            if Task.isCancelled { return }
            let suggestion = await reverseGeocodeNyc(latitude: coordinate.latitude, longitude: coordinate.longitude)
                ?? fallbackSuggestion(for: coordinate)
            if Task.isCancelled { return }
            await MainActor.run {
                addressCache[key] = suggestion
                selectedCoordinate = coordinate
                pendingSuggestion = suggestion
                resolvedAddress = suggestion.label
                lookupInFlight = false
            }
        }
    }

    private func addressCacheKey(for coordinate: CLLocationCoordinate2D) -> String {
        "\(Int((coordinate.latitude * 10_000).rounded())):\(Int((coordinate.longitude * 10_000).rounded()))"
    }

    private func fallbackSuggestion(for coordinate: CLLocationCoordinate2D) -> ComposerState.AddressSuggestion {
        ComposerState.AddressSuggestion(
            label: coordinateText(coordinate),
            latitude: coordinate.latitude,
            longitude: coordinate.longitude
        )
    }

    private func coordinateText(_ coordinate: CLLocationCoordinate2D) -> String {
        String(format: "%.5f, %.5f", coordinate.latitude, coordinate.longitude)
    }

    private static func validCoordinate(latitude: Double?, longitude: Double?) -> CLLocationCoordinate2D? {
        guard let latitude, let longitude else { return nil }
        guard latitude.isFinite, longitude.isFinite else { return nil }
        guard abs(latitude) <= 90, abs(longitude) <= 180 else { return nil }
        guard abs(latitude) > 0.000001 || abs(longitude) > 0.000001 else { return nil }
        let coordinate = CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
        return CLLocationCoordinate2DIsValid(coordinate) ? coordinate : nil
    }
}

private struct ReportedDateTimeSheet: View {
    let isoValue: String
    let photoIsoValue: String?
    let onSelected: (String) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var month: Int
    @State private var day: Int
    @State private var year: Int
    @State private var hour: Int
    @State private var minute: Int
    @State private var meridiem: String

    init(isoValue: String, photoIsoValue: String?, onSelected: @escaping (String) -> Void) {
        self.isoValue = isoValue
        self.photoIsoValue = photoIsoValue
        self.onSelected = onSelected
        let components = Self.components(from: Self.date(from: isoValue) ?? Date())
        _month = State(initialValue: components.month)
        _day = State(initialValue: components.day)
        _year = State(initialValue: components.year)
        _hour = State(initialValue: components.hour)
        _minute = State(initialValue: components.minute)
        _meridiem = State(initialValue: components.meridiem)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("Occurred At")
                    .font(.title2.bold())
                Spacer()
                Button("Done") {
                    onSelected(Self.isoString(from: selectedDate))
                    dismiss()
                }
                .font(.body.weight(.semibold))
                .foregroundStyle(Color.reportedOrange)
            }
            GeometryReader { proxy in
                let availableWidth = max(proxy.size.width, 1)
                let widths = datePickerColumnWidths(for: availableWidth)
                HStack(spacing: 0) {
                    wheelPicker("Month", selection: $month, values: Array(1...12), width: widths.month) { value in
                        Calendar.current.shortMonthSymbols[value - 1]
                    }
                    wheelPicker("Day", selection: $day, values: Array(1...daysInSelectedMonth), width: widths.day) { "\($0)" }
                    wheelPicker("Year", selection: $year, values: Array((currentYear - 1)...currentYear), width: widths.year) { "\($0)" }
                    wheelPicker("Hour", selection: $hour, values: Array(1...12), width: widths.hour) { "\($0)" }
                    wheelPicker("Minute", selection: $minute, values: Array(stride(from: 0, through: 59, by: 1)), width: widths.minute) { String(format: "%02d", $0) }
                    Picker("", selection: $meridiem) {
                        Text("AM")
                            .font(.system(size: 16))
                            .lineLimit(1)
                            .minimumScaleFactor(0.75)
                            .tag("AM")
                        Text("PM")
                            .font(.system(size: 16))
                            .lineLimit(1)
                            .minimumScaleFactor(0.75)
                            .tag("PM")
                    }
                    .pickerStyle(.wheel)
                    .frame(width: widths.meridiem, height: 126)
                    .compositingGroup()
                    .clipped()
                }
                .frame(width: availableWidth, height: 154, alignment: .center)
                .clipped()
            }
            .frame(height: 154)
            .onChange(of: month) { _, _ in clampDay() }
            .onChange(of: year) { _, _ in clampDay() }
            if let photoIsoValue, let photoDate = Self.date(from: photoIsoValue) {
                Button {
                    apply(photoDate)
                    onSelected(Self.isoString(from: photoDate))
                    dismiss()
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "camera")
                        Text("Use photo time of \(photoIsoValue.photoTimeDisplay)")
                        Spacer(minLength: 0)
                    }
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Color.reportedOrange)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 16)
    }

    private var currentYear: Int {
        Calendar.current.component(.year, from: Date())
    }

    private var daysInSelectedMonth: Int {
        var components = DateComponents()
        components.year = year
        components.month = month
        let date = Calendar.current.date(from: components) ?? Date()
        return Calendar.current.range(of: .day, in: .month, for: date)?.count ?? 31
    }

    private var selectedDate: Date {
        var components = DateComponents()
        components.year = year
        components.month = month
        components.day = min(day, daysInSelectedMonth)
        let normalizedHour = hour % 12 + (meridiem == "PM" ? 12 : 0)
        components.hour = normalizedHour
        components.minute = minute
        return min(Calendar.current.date(from: components) ?? Date(), Date())
    }

    @ViewBuilder
    private func wheelPicker<Value: Hashable>(
        _ label: String,
        selection: Binding<Value>,
        values: [Value],
        width: CGFloat,
        formatter: @escaping (Value) -> String
    ) -> some View {
        VStack(spacing: 2) {
            Text(label)
                .font(.caption2)
                .foregroundStyle(.secondary)
            Picker(label, selection: selection) {
                ForEach(values, id: \.self) { value in
                    Text(formatter(value))
                        .font(.system(size: 17))
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)
                        .tag(value)
                }
            }
            .pickerStyle(.wheel)
            .frame(width: width, height: 126)
            .compositingGroup()
            .clipped()
        }
        .frame(width: width, height: 154)
        .clipped()
    }

    private func datePickerColumnWidths(for availableWidth: CGFloat) -> (
        month: CGFloat,
        day: CGFloat,
        year: CGFloat,
        hour: CGFloat,
        minute: CGFloat,
        meridiem: CGFloat
    ) {
        let weights: [CGFloat] = [0.2, 0.14, 0.2, 0.14, 0.18, 0.14]
        let base = max(availableWidth, 280)
        return (
            month: floor(base * weights[0]),
            day: floor(base * weights[1]),
            year: floor(base * weights[2]),
            hour: floor(base * weights[3]),
            minute: floor(base * weights[4]),
            meridiem: floor(base * weights[5])
        )
    }

    private func clampDay() {
        day = min(day, daysInSelectedMonth)
    }

    private func apply(_ date: Date) {
        let components = Self.components(from: date)
        month = components.month
        day = components.day
        year = components.year
        hour = components.hour
        minute = components.minute
        meridiem = components.meridiem
    }

    private static func date(from iso: String) -> Date? {
        guard !iso.isEmpty else { return nil }
        let isoFormatter = ISO8601DateFormatter()
        isoFormatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let fallbackIsoFormatter = ISO8601DateFormatter()
        fallbackIsoFormatter.formatOptions = [.withInternetDateTime]
        return isoFormatter.date(from: iso) ?? fallbackIsoFormatter.date(from: iso)
    }

    private static func isoString(from date: Date) -> String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter.string(from: date)
    }

    private static func components(from date: Date) -> (month: Int, day: Int, year: Int, hour: Int, minute: Int, meridiem: String) {
        let calendar = Calendar.current
        let rawHour = calendar.component(.hour, from: date)
        let hour = rawHour % 12 == 0 ? 12 : rawHour % 12
        return (
            calendar.component(.month, from: date),
            calendar.component(.day, from: date),
            calendar.component(.year, from: date),
            hour,
            calendar.component(.minute, from: date),
            rawHour >= 12 ? "PM" : "AM"
        )
    }
}

struct PickerField: View {
    let title: String
    let value: String
    var isError = false
    var showsChevron = true
    var alignment: Alignment = .leading
    let action: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.headline)
                .foregroundStyle(isError ? Color.red : Color.reportedOrange)
            Button(action: action) {
                HStack(spacing: 6) {
                    AutoFitFieldText(value.isEmpty ? "Select" : value, alignment: alignment)
                        .foregroundStyle(.primary)
                    if showsChevron {
                        Spacer(minLength: 0)
                    }
                    if showsChevron {
                        Image(systemName: "chevron.down")
                            .foregroundStyle(.secondary)
                            .layoutPriority(1)
                    }
                }
                .frame(maxWidth: .infinity, alignment: alignment)
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

private struct AutoFitFieldText: View {
    let text: String
    var alignment: Alignment = .leading

    init(_ text: String, alignment: Alignment = .leading) {
        self.text = text
        self.alignment = alignment
    }

    var body: some View {
        Text(text)
            .font(.body)
            .lineLimit(1)
            .minimumScaleFactor(0.55)
            .allowsTightening(true)
            .frame(maxWidth: .infinity, alignment: alignment)
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

struct ProviderSignInButton: View {
    let title: String
    let systemImage: String?
    let iconText: String?
    let foregroundColor: Color
    let backgroundColor: Color
    let borderColor: Color
    let enabled: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                if let systemImage {
                    Image(systemName: systemImage)
                        .font(.title3.weight(.semibold))
                        .frame(width: 24, height: 24)
                } else if let iconText {
                    Text(iconText)
                        .font(.title3.weight(.bold))
                        .foregroundStyle(.blue)
                        .frame(width: 24, height: 24)
                }
                Text(title)
                    .font(.headline)
                    .frame(maxWidth: .infinity)
            }
            .foregroundStyle(foregroundColor)
            .padding(.horizontal, 16)
            .padding(.vertical, 13)
            .frame(maxWidth: .infinity)
            .background(backgroundColor)
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(borderColor, lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: 8))
            .opacity(enabled ? 1 : 0.55)
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}

struct AuthDivider: View {
    var body: some View {
        HStack(spacing: 14) {
            Rectangle()
                .fill(Color(.separator))
                .frame(height: 1)
            Text("or")
                .font(.callout)
                .foregroundStyle(.secondary)
            Rectangle()
                .fill(Color(.separator))
                .frame(height: 1)
        }
        .padding(.vertical, 2)
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
    var foregroundColor: Color = .primary.opacity(0.88)
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .foregroundStyle(foregroundColor)
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
