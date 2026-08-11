import AVFoundation
import CoreGraphics
import Darwin
import FirebaseAnalytics
import ImageIO
import LiteRTLM
import Lottie
import MapKit
import PhotosUI
import SharedCore
import SwiftUI
import UniformTypeIdentifiers
import UIKit
import WebKit

@MainActor
private func dismissActiveKeyboard() {
    UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
}

@MainActor
private func currentInterfaceIsLandscape(fallbackSize: CGSize) -> Bool {
    let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
    let foregroundScene = scenes.first { $0.activationState == .foregroundActive }
        ?? scenes.first { $0.activationState == .foregroundInactive }
        ?? scenes.first
    if let orientation = foregroundScene?.interfaceOrientation, orientation != .unknown {
        return orientation.isLandscape
    }
    return fallbackSize.width > fallbackSize.height
}

private let preferredPlateRegions = ["NY", "NJ", "CT", "PA", "FL", "OTHER"]
private let dismissedSystemNoticeKey = "reported.dismissed_system_notice"
private let buyMeACoffeeURL = URL(string: "https://www.buymeacoffee.com/reported")!
private let reportedRoboflowProjectURL = URL(string: "https://app.roboflow.com/reported/reported/13")!
private let philadelphiaSubmissionVideoMessage = "Philadelphia Parking Authority reports do not accept videos. Add up to 2 JPG or PNG photos instead."
private let reportedAiFabDiameter: CGFloat = 58
private let reportedAiFabBottomPadding: CGFloat = 24
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
            if sessionViewModel.state.loading {
                StartupLoadingView()
            } else if sessionViewModel.state.session?.isAuthorized == true || sessionViewModel.state.isGuest {
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
            sessionViewModel.onAction(.load)
            themeViewModel.onAction(.load)
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
                sessionViewModel.onAction(.continueAsGuest)
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: .reportedDetectedInfractionEditRequested)) { _ in
            detectedDraftOpenRequest = UUID()
            if sessionViewModel.state.session?.isAuthorized != true && !sessionViewModel.state.isGuest {
                sessionViewModel.onAction(.continueAsGuest)
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

private struct StartupLoadingView: View {
    var body: some View {
        ZStack {
            Color(.systemBackground)
                .ignoresSafeArea()
            ProgressView()
                .controlSize(.regular)
                .tint(Color.reportedOrange)
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
                        PrimaryButton(title: "Login", size: .compact) { path.append(.login) }
                        SecondaryButton(title: "Register", size: .compact) { path.append(.register) }
                    }
                    if allowSkip {
                        Button {
                            sessionViewModel.onAction(.continueAsGuest)
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
                        sessionViewModel.onAction(.authenticated)
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
                        sessionViewModel.onAction(.authenticated)
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
    case autoReport
    case reports
    case profile
    case settings

    var title: String {
        switch self {
        case .report: return "Report"
        case .autoReport: return "Auto-Report"
        case .reports: return "My Reports"
        case .profile: return "Profile"
        case .settings: return "Settings"
        }
    }

    var systemImage: String {
        switch self {
        case .report: return "plus.square"
        case .autoReport: return "sparkles"
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
    @State private var reportVoiceAssistRequest = 0
    @State private var reportSubmitBarVisible = false
    @State private var submittedSnackbarObjectId: String?
    @State private var pendingOpenReportObjectId: String?
    @State private var systemNotice = RemoteConfigOverrides.shared.systemNotice
    @State private var dismissedSystemNotice = UserDefaults.standard.string(forKey: dismissedSystemNoticeKey) ?? ""
    @GestureState private var navigationDragTranslation: CGFloat = 0

    var body: some View {
        GeometryReader { proxy in
            shellContent(proxy: proxy)
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
        .onReceive(NotificationCenter.default.publisher(for: .reportedRemoteConfigUpdated)) { _ in
            refreshSystemNotice()
        }
    }

    private func shellContent(proxy: GeometryProxy) -> some View {
        let isLandscape = currentInterfaceIsLandscape(fallbackSize: proxy.size)
        let reportOwnsToolbar = selection == .report && isLandscape
        let usesTabletToolbarActions = UIDevice.current.userInterfaceIdiom == .pad || min(proxy.size.width, proxy.size.height) >= 700
        let profileLogoutUsesToolbar = selection == .profile && usesTabletToolbarActions && sessionViewModel.state.session?.isAuthorized == true
        let baseOffset = isNavigationOpen ? drawerWidth : 0
        let currentOffset = min(max(baseOffset + navigationDragTranslation, 0), drawerWidth)

        return HStack(spacing: 0) {
            LeftGliderNavView(selection: selection) { destination in
                select(destination)
            }
            .frame(width: drawerWidth)

            mainPanel(
                size: proxy.size,
                reportOwnsToolbar: reportOwnsToolbar,
                profileLogoutUsesToolbar: profileLogoutUsesToolbar
            )
        }
        .frame(width: proxy.size.width + drawerWidth, height: proxy.size.height, alignment: .leading)
        .offset(x: currentOffset - drawerWidth)
        .animation(.spring(response: 0.28, dampingFraction: 0.86), value: isNavigationOpen)
        .simultaneousGesture(navigationEdgeDragGesture(baseOffset: baseOffset))
        .clipped()
        .background(alignment: .leading) {
            navigationBackdrop(currentOffset: currentOffset)
        }
        .background(Color(.systemBackground))
        .background(alignment: .bottom) {
            submitBarSafeAreaFill(proxy: proxy)
        }
        .overlay(alignment: .bottom) {
            submittedSnackbarOverlay
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

    private func mainPanel(
        size: CGSize,
        reportOwnsToolbar: Bool,
        profileLogoutUsesToolbar: Bool
    ) -> some View {
        VStack(spacing: 0) {
            shellToolbar(
                reportOwnsToolbar: reportOwnsToolbar,
                profileLogoutUsesToolbar: profileLogoutUsesToolbar
            )
            if let notice = visibleSystemNotice {
                SystemNoticeBanner(message: notice) {
                    dismissSystemNotice(notice)
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 8)
                .transition(.move(edge: .top).combined(with: .opacity))
            }
            destinationContent(
                reportOwnsToolbar: reportOwnsToolbar,
                profileLogoutUsesToolbar: profileLogoutUsesToolbar
            )
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .frame(width: size.width, height: size.height)
        .background(Color(.systemBackground))
        .animation(.easeInOut(duration: 0.2), value: visibleSystemNotice)
    }

    private var visibleSystemNotice: String? {
        let notice = systemNotice.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !notice.isEmpty, notice != dismissedSystemNotice else {
            return nil
        }
        return notice
    }

    private func refreshSystemNotice() {
        systemNotice = RemoteConfigOverrides.shared.systemNotice
    }

    private func dismissSystemNotice(_ notice: String) {
        dismissedSystemNotice = notice
        UserDefaults.standard.set(notice, forKey: dismissedSystemNoticeKey)
    }

    @ViewBuilder
    private func shellToolbar(reportOwnsToolbar: Bool, profileLogoutUsesToolbar: Bool) -> some View {
        if !reportOwnsToolbar {
            MainShellToolbar(
                title: selection.title,
                showClear: selection == .report && reportHasDraftContent,
                trailingActionTitle: profileLogoutUsesToolbar ? "Logout" : nil,
                trailingAction: profileLogoutUsesToolbar ? { sessionViewModel.onAction(.logout) } : nil,
                onMenuTapped: { toggleNavigation() },
                onClear: { reportClearRequest += 1 }
            )
        }
    }

    @ViewBuilder
    private func destinationContent(
        reportOwnsToolbar: Bool,
        profileLogoutUsesToolbar: Bool
    ) -> some View {
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
                voiceAssistRequest: reportVoiceAssistRequest,
                detectedDraftOpenRequest: $detectedDraftOpenRequest,
                onDraftContentChanged: { reportHasDraftContent = $0 },
                onSubmitBarVisibilityChanged: { reportSubmitBarVisible = $0 },
                onReportSubmitted: { objectId in
                    submittedSnackbarObjectId = objectId
                }
            )
        case .autoReport:
            AutoReportScreen(
                isAuthorized: sessionViewModel.state.session?.isAuthorized == true,
                onRequireLogin: onRequireLogin,
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
                showsLogoutInToolbar: profileLogoutUsesToolbar,
                onLogout: { sessionViewModel.onAction(.logout) }
            )
        case .settings:
            SettingsScreen(
                onThemeModeSelected: { themeViewModel.onAction(.modeChanged($0)) }
            )
        }
    }

    @ViewBuilder
    private func navigationBackdrop(currentOffset: CGFloat) -> some View {
        if currentOffset > 0 {
            Color(.secondarySystemBackground)
                .opacity(0.7)
                .frame(width: drawerWidth)
                .offset(x: currentOffset - drawerWidth)
                .ignoresSafeArea(.container, edges: [.top, .bottom])
        }
    }

    @ViewBuilder
    private func submitBarSafeAreaFill(proxy: GeometryProxy) -> some View {
        if selection == .report && reportSubmitBarVisible {
            Color(.systemBackground)
                .frame(height: max(proxy.safeAreaInsets.bottom, 1))
                .ignoresSafeArea(.container, edges: .bottom)
        }
    }

    @ViewBuilder
    private var submittedSnackbarOverlay: some View {
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

    private func select(_ destination: MainShellDestination) {
        let isAuthorized = sessionViewModel.state.session?.isAuthorized == true
        if !isAuthorized && destination.requiresAuthorization {
            selection = .report
            closeNavigation()
            onRequireLogin()
            return
        }
        if destination == .settings {
            ReportedAnalytics.logSettingsTapped(surface: "navigation")
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

    private func navigationEdgeDragGesture(baseOffset: CGFloat) -> some Gesture {
        DragGesture(minimumDistance: 12, coordinateSpace: .local)
            .updating($navigationDragTranslation) { value, state, _ in
                guard shouldHandleNavigationDrag(startX: value.startLocation.x) else { return }
                let proposedOffset = min(max(baseOffset + value.translation.width, 0), drawerWidth)
                state = proposedOffset - baseOffset
            }
            .onEnded { value in
                guard shouldHandleNavigationDrag(startX: value.startLocation.x) else { return }
                settleNavigationDrag(baseOffset: baseOffset, translation: value.predictedEndTranslation.width)
            }
    }

    private func shouldHandleNavigationDrag(startX: CGFloat) -> Bool {
        if isNavigationOpen {
            return startX <= drawerWidth + 28
        }
        return startX <= 28
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
        case .report, .autoReport, .settings:
            return false
        }
    }
}

private struct MainShellToolbar: View {
    let title: String
    var showClear = false
    var compact = false
    var onVoiceAssist: (() -> Void)? = nil
    var trailingActionTitle: String? = nil
    var trailingAction: (() -> Void)? = nil
    let onMenuTapped: () -> Void
    let onClear: () -> Void

    var body: some View {
        let buttonHeight: CGFloat = compact ? 38 : 44
        let sideSlotWidth: CGFloat = compact ? 68 : 96

        HStack {
            Button(action: onMenuTapped) {
                Image(systemName: "line.3.horizontal")
                    .font(.system(size: compact ? 22 : 24, weight: .semibold))
                    .frame(width: buttonHeight, height: buttonHeight)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .foregroundStyle(.primary)
            .frame(width: sideSlotWidth, height: buttonHeight, alignment: .leading)

            Spacer()

            Text(title)
                .font(.system(size: compact ? 20 : 24, weight: .regular))
                .lineLimit(1)
                .overlay(alignment: .trailing) {
                    if let onVoiceAssist {
                        Button(action: onVoiceAssist) {
                            AnimatedSparkleIcon(size: compact ? 17 : 19)
                                .frame(width: compact ? 32 : 36, height: compact ? 32 : 36)
                                .background(Color.reportedOrange.opacity(0.14))
                                .overlay(
                                    Circle()
                                        .stroke(Color.reportedOrange.opacity(0.35), lineWidth: 1)
                                )
                                .clipShape(Circle())
                        }
                        .buttonStyle(.plain)
                        .foregroundStyle(Color.reportedOrange)
                        .accessibilityLabel("Reported AI")
                        .offset(x: compact ? 32 : 37)
                    }
                }

            Spacer()

            if showClear {
                Button("Clear", action: onClear)
                    .font(.body.weight(.semibold))
                    .foregroundStyle(Color.reportedOrange)
                    .frame(width: sideSlotWidth, height: buttonHeight, alignment: .trailing)
            } else if let trailingActionTitle, let trailingAction {
                Button(trailingActionTitle, action: trailingAction)
                    .font(.body.weight(.semibold))
                    .foregroundStyle(Color.reportedOrange)
                    .frame(width: sideSlotWidth, height: buttonHeight, alignment: .trailing)
            } else {
                Color.clear
                    .frame(width: sideSlotWidth, height: buttonHeight)
            }
        }
        .padding(.horizontal, compact ? 8 : 20)
        .padding(.vertical, compact ? 6 : 12)
        .background(Color(.systemBackground))
    }
}

private struct SystemNoticeBanner: View {
    let message: String
    let onDismiss: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "exclamationmark.circle.fill")
                .font(.body.weight(.semibold))
                .foregroundStyle(Color.reportedOrange)
                .padding(.top, 1)

            Text(message)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.primary)
                .fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: .infinity, alignment: .leading)

            Button(action: onDismiss) {
                Image(systemName: "xmark")
                    .font(.caption.weight(.bold))
                    .frame(width: 28, height: 28)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .foregroundStyle(.secondary)
            .accessibilityLabel("Dismiss system notice")
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(Color.reportedOrange.opacity(0.12))
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(Color.reportedOrange.opacity(0.35), lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

private struct AnimatedSparkleIcon: View {
    let size: CGFloat
    var color: Color = .reportedOrange
    @State private var isAnimating = false

    var body: some View {
        Image(systemName: "sparkles")
            .font(.system(size: size, weight: .semibold))
            .foregroundStyle(color)
            .scaleEffect(isAnimating ? 1.12 : 0.92)
            .rotationEffect(.degrees(isAnimating ? 7 : -7))
            .opacity(isAnimating ? 1 : 0.72)
            .animation(.easeInOut(duration: 0.82).repeatForever(autoreverses: true), value: isAnimating)
            .onAppear {
                isAnimating = true
            }
    }
}

struct LeftGliderNavView: View {
    let selection: MainShellDestination
    let onSelect: (MainShellDestination) -> Void
    @Environment(\.openURL) private var openURL
    @State private var showCoffeeInfo = false

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

            VStack(spacing: 0) {
                Spacer()
                ZStack(alignment: .topTrailing) {
                    Button {
                        ReportedAnalytics.logBuyMeCoffeeTapped(surface: "left_nav")
                        ReportedAnalytics.logBuyMeCoffeeOpen(surface: "left_nav", source: "primary")
                        openURL(buyMeACoffeeURL)
                    } label: {
                        VStack(spacing: 7) {
                            Image("BuyMeACoffeeLogo")
                                .resizable()
                                .scaledToFit()
                                .frame(width: 28, height: 28)
                            Text("Buy us\ncoffee!!")
                                .font(.caption2.weight(.semibold))
                                .multilineTextAlignment(.center)
                                .lineLimit(2)
                        }
                        .foregroundStyle(Color.reportedOrange)
                        .frame(width: 92)
                        .frame(minHeight: 70)
                        .background(Color.reportedOrange.opacity(0.10))
                        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                        .contentShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Buy us coffee")

                    Button {
                        ReportedAnalytics.logBuyMeCoffeeInfoTapped(surface: "left_nav")
                        showCoffeeInfo = true
                    } label: {
                        Image(systemName: "info.circle.fill")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(Color.reportedOrange)
                            .frame(width: 36, height: 36)
                            .contentShape(Circle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Why support Reported")
                    .zIndex(1)
                }
                .padding(.bottom, 18)
            }
            .frame(maxHeight: .infinity)
        }
        .frame(width: 116)
        .frame(maxHeight: .infinity, alignment: .top)
        .background(Color.clear)
        .alert("Buy us coffee!!", isPresented: $showCoffeeInfo) {
            Button("OK", role: .cancel) {}
            Button("Open Buy Me a Coffee") {
                ReportedAnalytics.logBuyMeCoffeeOpen(surface: "left_nav", source: "info_dialog")
                openURL(buyMeACoffeeURL)
            }
        } message: {
            Text("Reported is free to use, but we do have infrastructure costs.")
        }
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
                    viewModel.onAction(.googleSignInPressed(onSuccess: onSuccess))
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
                    viewModel.onAction(.appleSignInPressed(onSuccess: onSuccess))
                }
                AuthDivider()
                InputField(title: "Email", text: Binding(
                    get: { viewModel.state.email },
                    set: { viewModel.onAction(.emailChanged($0)) }
                ), keyboardType: .emailAddress, textContentType: .emailAddress, autocapitalizationType: .none, autocorrectionDisabled: true)
                PasswordInputField(title: "Password", text: Binding(
                    get: { viewModel.state.password },
                    set: { viewModel.onAction(.passwordChanged($0)) }
                ))
                PrimaryButton(title: viewModel.state.loading ? "Logging In..." : "Login", size: .compact) {
                    viewModel.onAction(.loginPressed(onSuccess: onSuccess))
                }
                Button("Forgot Password?") {
                    viewModel.onAction(.forgotPasswordPressed)
                }
                    .foregroundStyle(Color.reportedOrange)
                Button("Need an account? Register", action: onRegister)
                    .foregroundStyle(Color.reportedOrange)
                }
                .padding()
            }
        }
        .navigationBarBackButtonHidden(true)
        .alert(
            "Check your email",
            isPresented: Binding(
                get: { viewModel.state.passwordResetMessage != nil },
                set: { if !$0 { viewModel.onAction(.passwordResetMessageDismissed) } }
            )
        ) {
            Button("OK", role: .cancel) {
                viewModel.onAction(.passwordResetMessageDismissed)
            }
        } message: {
            Text(viewModel.state.passwordResetMessage ?? "")
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
                    viewModel.onAction(.googleSignInPressed(onSuccess: onSuccess))
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
                    viewModel.onAction(.appleSignInPressed(onSuccess: onSuccess))
                }
                AuthDivider()
                InputField(title: "First Name", text: Binding(get: { viewModel.state.firstName }, set: { viewModel.onAction(.fieldsChanged(firstName: $0)) }))
                InputField(title: "Last Name", text: Binding(get: { viewModel.state.lastName }, set: { viewModel.onAction(.fieldsChanged(lastName: $0)) }))
                InputField(title: "Phone", text: Binding(get: { viewModel.state.phone }, set: { viewModel.onAction(.fieldsChanged(phone: $0)) }))
                InputField(title: "Email", text: Binding(get: { viewModel.state.email }, set: { viewModel.onAction(.fieldsChanged(email: $0)) }), keyboardType: .emailAddress, textContentType: .emailAddress, autocapitalizationType: .none, autocorrectionDisabled: true)
                PasswordInputField(title: "Password", text: Binding(get: { viewModel.state.password }, set: { viewModel.onAction(.fieldsChanged(password: $0)) }))
                Toggle("I'm willing to testify by phone if needed.", isOn: Binding(
                    get: { viewModel.state.testify },
                    set: { viewModel.onAction(.fieldsChanged(testify: $0)) }
                ))
                PrimaryButton(title: viewModel.state.loading ? "Creating..." : "Create Account", size: .compact) {
                    viewModel.onAction(.registerPressed(onSuccess: onSuccess))
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
                .font(.title2.weight(.semibold))
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
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    let isAuthorized: Bool
    let onRequireLogin: () -> Void
    let showEmbeddedLandscapeToolbar: Bool
    let shellHasDraftContent: Bool
    let onMenuTapped: () -> Void
    let onClearTapped: () -> Void
    @Binding var sharedMediaImportId: String?
    @Binding var clearRequest: Int
    var voiceAssistRequest: Int = 0
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
    @State private var imageTimeRefreshTask: Task<Void, Never>?
    @State private var refreshedPhotoOccurredAtIso: String?
    @State private var imageTimeRefreshInFlight = false
    @State private var imageAddressRefreshTask: Task<Void, Never>?
    @State private var refreshedPhotoAddressSuggestion: ComposerState.AddressSuggestion?
    @State private var imageAddressRefreshInFlight = false
    @State private var videoScanTask: Task<Void, Never>?
    @State private var addressSearchTask: Task<Void, Never>?
    @State private var showPreferredPlateRegions = false
    @State private var showAllPlateRegions = false
    @State private var showPlateRegionSheet = false
    @State private var showAddressMapSheet = false
    @State private var showAddressSearchScreen = false
    @State private var showPlateEntryScreen = false
    @State private var showVoiceAssistSheet = false
    @State private var voiceAssistSheetDetent: PresentationDetent = .height(voiceAssistantCompactSheetHeight)
    @State private var showComplaintChooser = false
    @StateObject private var voiceAudio = VoiceReportAudioController()
    @State private var voiceTranscript = ""
    @State private var voiceDraft: VoiceReportDraft?
    @State private var voiceError: String?
    @State private var voiceProcessing = false
    @State private var voiceModelInstalled = OnDeviceGemmaVoiceDraftEngine.isModelInstalled()
    @State private var voiceModelDownloading = false
    @State private var voiceModelDownloadProgress: Double?
    @State private var voiceImageContext: String?
    @State private var voiceImageContextTask: Task<Void, Never>?
    @State private var voiceProcessingTask: Task<Void, Never>?
    @State private var voiceProcessingID: UUID?
    @State private var pendingPlateCandidate: ComposerState.PlateCandidate?
    @State private var pendingPlatePreviewImage: UIImage?
    @State private var showOccurredAtPicker = false
    @State private var showDiscardConfirmation = false
    @State private var handledClearRequest = 0
    @State private var handledVoiceAssistRequest = 0
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
    @State private var isKeyboardVisible = false
    @State private var fullScreenTextEditorField: ReportLongTextField?
#if DEBUG
    @State private var debugMediaImportConsumed = false
#endif
    private let previewScrollId = "composer-primary-media-preview"

    private func complaintPickerColumns(for width: CGFloat) -> [GridItem] {
        let spacing: CGFloat = width >= 700 ? 18 : 10
        let columnCount = 2
        return Array(repeating: GridItem(.flexible(), spacing: spacing), count: columnCount)
    }

    private func complaintPickerMaxWidth(for width: CGFloat) -> CGFloat {
        width >= 700 ? min(width, 980) : width
    }

    private func complaintTileMetrics(for width: CGFloat) -> (imageHeight: CGFloat, minHeight: CGFloat) {
        width >= 700 ? (224, 320) : (96, 150)
    }
    private func primaryPreviewHeight(for availableSize: CGSize) -> CGFloat {
        availableSize.width >= 700 ? 440 : 220
    }

    private func landscapeMediaColumnWidth(for width: CGFloat) -> CGFloat {
        min(max(236, width * 0.31), 276)
    }

    private var complaintOptions: [ComplaintOption] {
        complaintOptionsFor(viewModel.state.complaintCategories)
    }
    private var animatedComplaintIds: [String] {
        complaintOptions.compactMap { $0.lottieName == nil ? nil : $0.id }
    }
    private var primaryPlateSourceImage: UIImage? {
        guard let media = viewModel.state.primaryMedia, !media.isVideo else { return nil }
        return UIImage(contentsOfFile: media.fileURL.path)
    }
    private var hasImageAddressSource: Bool {
        viewModel.state.primaryMedia?.isVideo == false
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
        let photoIsoValue = viewModel.state.photoOccurredAtIso ?? refreshedPhotoOccurredAtIso
        let hasImageTimeSource = viewModel.state.primaryMedia?.isVideo == false
        var view = AnyView(composerContent)
        view = AnyView(view.overlay(alignment: .bottom) { detectionProgressChip })
        view = AnyView(view.overlay(alignment: .bottomTrailing) {
            reportedAiFloatingButton
        })
        view = AnyView(view.sheet(isPresented: $showOccurredAtPicker) {
            ReportedDateTimeSheet(
                isoValue: viewModel.state.occurredAtIso,
                photoIsoValue: photoIsoValue,
                hasImageTimeSource: hasImageTimeSource,
                isRefreshingImageTime: imageTimeRefreshInFlight
            ) { iso in
                viewModel.onAction(.fieldsChanged(occurredAtIso: iso))
            }
            .presentationDetents([.height(360)])
            .presentationDragIndicator(.hidden)
            .presentationBackground(Color(.systemBackground))
        })
        view = AnyView(view.sheet(isPresented: $showPlateRegionSheet) {
            PlateRegionSheet(
                selectedValue: viewModel.state.plateRegion,
                showAllStates: $showAllPlateRegions,
                onSelected: { value in
                    ReportedAnalytics.logStateSelected(region: value)
                    viewModel.onAction(.fieldsChanged(plateRegion: value))
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
                imageAddressSuggestion: refreshedPhotoAddressSuggestion,
                hasImageAddressSource: hasImageAddressSource,
                isRefreshingImageAddress: imageAddressRefreshInFlight,
                onCancel: { showAddressMapSheet = false },
                onDone: { suggestion in
                    viewModel.onAction(.addressChosen(suggestion))
                    showAddressMapSheet = false
                }
            )
            .presentationDetents([.large])
            .presentationDragIndicator(.hidden)
        })
        view = AnyView(view.fullScreenCover(isPresented: $showAddressSearchScreen) {
            AddressSearchScreen(
                query: Binding(
                    get: { viewModel.state.addressQuery },
                    set: { viewModel.onAction(.addressQueryChanged($0)) }
                ),
                suggestions: viewModel.state.addressSuggestions,
                addressProvider: reportAddressProvider(
                    latitude: viewModel.state.latitude,
                    longitude: viewModel.state.longitude,
                    address: viewModel.state.addressQuery.isEmpty ? viewModel.state.address : viewModel.state.addressQuery
                ),
                isLoading: viewModel.state.lookupInFlight,
                isError: viewModel.state.validationErrors.address != nil,
                imageAddressSuggestion: refreshedPhotoAddressSuggestion,
                hasImageAddressSource: hasImageAddressSource,
                isRefreshingImageAddress: imageAddressRefreshInFlight,
                onCancel: {
                    showAddressSearchScreen = false
                },
                onClear: {
                    viewModel.onAction(.addressQueryChanged(""))
                },
                onOpenMap: {
                    showAddressSearchScreen = false
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) {
                        openAddressMapSheet()
                    }
                },
                onSelect: { suggestion in
                    viewModel.onAction(.addressChosen(suggestion))
                    showAddressSearchScreen = false
                },
                onUseImageAddress: { suggestion in
                    viewModel.onAction(.addressChosen(suggestion))
                    showAddressSearchScreen = false
                }
            )
        })
        view = AnyView(view.fullScreenCover(isPresented: $showPlateEntryScreen) {
            PlateEntryScreen(
                plate: Binding(
                    get: { viewModel.state.plate },
                    set: { viewModel.onAction(.fieldsChanged(plate: $0)) }
                ),
                candidates: viewModel.state.plateCandidates,
                selectedPlate: viewModel.state.selectedPlateCandidate,
                sourceImage: primaryPlateSourceImage,
                isError: viewModel.state.validationErrors.plate != nil,
                onCancel: {
                    showPlateEntryScreen = false
                },
                onClear: {
                    viewModel.onAction(.fieldsChanged(plate: ""))
                },
                onCandidateSelected: { candidate in
                    viewModel.onAction(.plateCandidateChosen(candidate))
                    showPlateEntryScreen = false
                }
            )
        })
        view = AnyView(view.fullScreenCover(item: $fullScreenTextEditorField) { field in
            FullScreenReportTextEditor(
                title: field.title,
                placeholder: field.placeholder,
                text: reportTextBinding(for: field),
                onDone: {
                    fullScreenTextEditorField = nil
                    viewModel.onAction(.draftPersistenceRequested)
                }
            )
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
        view = AnyView(view.safeAreaInset(edge: .bottom, spacing: 0) {
            if showsBottomSubmitBar {
                submitBottomBar()
            }
        })
        view = AnyView(view.overlay(alignment: .bottom) {
            voiceAssistantFloatingOverlay
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
        view = AnyView(view.onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillShowNotification)) { _ in
            isKeyboardVisible = true
        })
        view = AnyView(view.onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillHideNotification)) { _ in
            isKeyboardVisible = false
        })
        view = AnyView(view.overlay { detectionProgressModal })
        view = AnyView(view.alert("Clear report?", isPresented: $showDiscardConfirmation) {
            Button("Cancel", role: .cancel) {}
            Button("Clear", role: .destructive) {
                viewModel.onAction(.clearDraft)
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
            viewModel.onAction(.submittedReportConsumed)
        })
        view = AnyView(view.onChange(of: viewModel.state.primaryMedia?.fileURL.path) { _, _ in
            imageTimeRefreshTask?.cancel()
            refreshedPhotoOccurredAtIso = nil
            imageTimeRefreshInFlight = false
            imageAddressRefreshTask?.cancel()
            refreshedPhotoAddressSuggestion = nil
            imageAddressRefreshInFlight = false
        })
        view = AnyView(view.onChange(of: clearRequest) { _, token in
            guard token != handledClearRequest else { return }
            handledClearRequest = token
            if hasDraftContent {
                showDiscardConfirmation = true
            }
        })
        view = AnyView(view.onChange(of: voiceAssistRequest) { _, token in
            guard token != handledVoiceAssistRequest else { return }
            handledVoiceAssistRequest = token
            openVoiceAssistant()
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
            allowsVideos: !viewModel.state.isPhiladelphiaSubmission,
            handlePickedItem: handlePickedItem,
            handlePickedItems: handlePickedItems
        )))
        view = AnyView(view.task(id: "\(sharedMediaImportId ?? "")-\(viewModel.state.draftLoaded)") {
            guard let importId = sharedMediaImportId else { return }
            guard viewModel.state.draftLoaded else { return }
            await handleSharedMediaImport(id: importId)
            sharedMediaImportId = nil
        })
#if DEBUG
        view = AnyView(view.task(id: viewModel.state.draftLoaded) {
            await handleDebugMediaImportIfNeeded()
        })
#endif
        view = AnyView(view.onChange(of: detectedDraftOpenRequest) { _, request in
            guard request != nil else { return }
            viewModel.onAction(.reloadDraft)
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
                viewModel.onAction(.videoProcessingDecision(false))
            }
        } message: {
            Text("We can scan every video frame and show the best candidate plates for you to choose from.")
        })
        view = AnyView(view.sheet(isPresented: $showComplaintChooser) {
            ComplaintChooserSheet(
                title: "What kind of complaint is this?",
                options: complaintOptions,
                selectedComplaintId: viewModel.state.selectedComplaintId,
                activeAnimatedComplaintId: activeAnimatedComplaintId
            ) { option in
                ReportedAnalytics.logComplaintSelected(complaintId: option.id, surface: "verify")
                viewModel.onAction(.selectedComplaintChanged(option.id))
                showComplaintChooser = false
            }
            .presentationDetents([.height(complaintChooserSheetDetentHeight(optionCount: complaintOptions.count))])
            .presentationDragIndicator(.visible)
            .presentationBackground(Color(.systemBackground))
        })
        view = AnyView(view.sheet(item: $pendingPlateCandidate) { candidate in
            UsePlateCandidateSheet(
                candidate: candidate,
                sourceImage: pendingPlatePreviewImage,
                onUse: {
                    viewModel.onAction(.plateCandidateChosen(candidate))
                    pendingPlateCandidate = nil
                    pendingPlatePreviewImage = nil
                },
                onDismiss: {
                    pendingPlateCandidate = nil
                    pendingPlatePreviewImage = nil
                }
            )
            .presentationDetents([.height(plateCandidateSheetDetentHeight)])
            .presentationDragIndicator(.hidden)
            .presentationBackground(Color(.systemBackground))
        })
        view = AnyView(view.alert(
            "Fix plate format?",
            isPresented: Binding(
                get: { viewModel.state.plateCorrectionPrompt != nil },
                set: { if !$0 { viewModel.onAction(.plateCorrectionDismissed) } }
            ),
            presenting: viewModel.state.plateCorrectionPrompt
        ) { prompt in
            Button("Use \(prompt.suggestedPlate)") {
                viewModel.onAction(.plateCorrectionAccepted)
            }
            Button("Keep") {
                viewModel.onAction(.plateCorrectionKept)
            }
            Button("Edit", role: .cancel) {
                viewModel.onAction(.plateCorrectionDismissed)
            }
        } message: { prompt in
            Text("This looks like a \(prompt.label) plate. Use \(prompt.suggestedPlate) instead of \(prompt.rawPlate)?")
        })
        return view
    }

    private func openVoiceAssistant() {
        ReportedAnalytics.logAiSparkleTapped(surface: "report_composer")
        voiceError = nil
        voiceImageContext = nil
        voiceImageContextTask?.cancel()
        voiceImageContextTask = nil
        voiceAudio.refreshPermissionState()
        voiceModelInstalled = OnDeviceGemmaVoiceDraftEngine.isModelInstalled()
        voiceAssistSheetDetent = .height(voiceAssistantCompactSheetHeight)
        showVoiceAssistSheet = true
        startVoiceImageContextWarmup()
    }

    private func dismissVoiceAssistant() {
        cancelVoiceProcessing()
        _ = voiceAudio.stopRecording()
        showVoiceAssistSheet = false
    }

    private var isVoiceAssistSheetMinimized: Bool {
        voiceAudio.isRecording && voiceAssistSheetDetent == .height(voiceAssistantMinimizedSheetHeight)
    }

    @MainActor
    private func startVoiceAssistantCapture() async {
        voiceModelInstalled = OnDeviceGemmaVoiceDraftEngine.isModelInstalled()
        guard voiceModelInstalled else {
            voiceError = nil
            return
        }
        voiceError = nil
        voiceTranscript = ""
        voiceDraft = nil
        voiceProcessing = false
        if voiceImageContext == nil && voiceImageContextTask == nil {
            startVoiceImageContextWarmup()
        }
        let started = await voiceAudio.startRecording()
        if !started {
            voiceImageContextTask?.cancel()
            voiceImageContextTask = nil
            voiceError = voiceAudio.errorMessage ?? "Microphone recording could not start."
        }
    }

    @MainActor
    private func beginStoppingVoiceAssistantCapture() {
        guard voiceProcessingTask == nil else { return }
        let processingID = UUID()
        voiceProcessingID = processingID
        voiceProcessingTask = Task { @MainActor in
            await stopVoiceAssistantCapture(processingID: processingID)
        }
    }

    @MainActor
    private func stopVoiceAssistantCapture(processingID: UUID) async {
        guard let audioURL = voiceAudio.stopRecording() else {
            if voiceProcessingID == processingID {
                voiceError = voiceAudio.errorMessage ?? "I couldn't capture enough audio to process."
                finishVoiceProcessing(processingID: processingID)
            }
            return
        }
        await processVoiceAudio(audioURL, processingID: processingID)
    }

    @MainActor
    private func processVoiceAudio(_ audioURL: URL, processingID: UUID) async {
        voiceModelInstalled = OnDeviceGemmaVoiceDraftEngine.isModelInstalled()
        guard voiceModelInstalled else {
            if voiceProcessingID == processingID {
                voiceError = "Install REPORTED AI before using Talk."
                finishVoiceProcessing(processingID: processingID)
            }
            try? FileManager.default.removeItem(at: audioURL)
            return
        }
        voiceError = nil
        voiceDraft = nil
        voiceProcessing = true
        defer {
            finishVoiceProcessing(processingID: processingID)
            try? FileManager.default.removeItem(at: audioURL)
        }
        do {
            await voiceImageContextTask?.value
            try Task.checkCancellation()
            voiceImageContextTask = nil
            let voiceContext = await VoiceReportContext.build(
                state: viewModel.state,
                complaintOptions: complaintOptions,
                imageAddressSuggestion: refreshedPhotoAddressSuggestion,
                imageVisualContext: voiceImageContext
            )
            try Task.checkCancellation()
            let result = try await OnDeviceGemmaVoiceDraftEngine.generateDraft(
                audioURL: audioURL,
                complaintOptions: complaintOptions,
                voiceContext: voiceContext
            )
            try Task.checkCancellation()
            guard voiceProcessingID == processingID else { return }
            voiceTranscript = result.transcript ?? ""
            voiceDraft = result.draft
            withAnimation(.snappy) {
                voiceAssistSheetDetent = .large
            }
        } catch is CancellationError {
            return
        } catch {
            if voiceProcessingID == processingID {
                voiceError = error.localizedDescription
            }
        }
    }

    @MainActor
    private func cancelVoiceProcessing() {
        voiceProcessingID = nil
        voiceProcessingTask?.cancel()
        voiceProcessingTask = nil
        voiceImageContextTask?.cancel()
        voiceImageContextTask = nil
        voiceProcessing = false
    }

    @MainActor
    private func finishVoiceProcessing(processingID: UUID) {
        guard voiceProcessingID == processingID else { return }
        voiceProcessing = false
        voiceProcessingTask = nil
        voiceProcessingID = nil
    }

    @MainActor
    private func startVoiceImageContextWarmup() {
        voiceImageContextTask?.cancel()
        voiceImageContextTask = nil
        guard let imageURL = viewModel.state.primaryMedia?.fileURL,
              viewModel.state.primaryMedia?.isVideo == false else {
            return
        }
        voiceImageContextTask = Task {
            let context = await OnDeviceGemmaVoiceDraftEngine.generateImageContext(imageURL: imageURL)
            guard !Task.isCancelled else { return }
            await MainActor.run {
                voiceImageContext = context
            }
        }
    }

    @MainActor
    private func downloadVoiceModel() async {
        guard !voiceModelDownloading else { return }
        voiceError = nil
        voiceModelDownloading = true
        voiceModelDownloadProgress = nil
        do {
            try await OnDeviceGemmaVoiceDraftEngine.downloadModel { downloadedBytes, totalBytes in
                Task { @MainActor in
                    if totalBytes > 0 {
                        voiceModelDownloadProgress = min(1, max(0, Double(downloadedBytes) / Double(totalBytes)))
                    } else {
                        voiceModelDownloadProgress = nil
                    }
                }
            }
            voiceModelInstalled = true
            voiceModelDownloadProgress = 1
            voiceError = nil
        } catch {
            voiceModelInstalled = OnDeviceGemmaVoiceDraftEngine.isModelInstalled()
            voiceError = error.localizedDescription
        }
        voiceModelDownloading = false
    }

    private func resetVoiceAssistant() {
        cancelVoiceProcessing()
        voiceImageContext = nil
        voiceAudio.reset()
        voiceAudio.refreshPermissionState()
        voiceTranscript = ""
        voiceDraft = nil
        voiceError = nil
        voiceProcessing = false
        voiceModelInstalled = OnDeviceGemmaVoiceDraftEngine.isModelInstalled()
    }

    private func applyVoiceDraft(_ draft: VoiceReportDraft) {
        voiceImageContextTask?.cancel()
        voiceImageContextTask = nil
        if let complaintId = draft.complaintId {
            viewModel.onAction(.selectedComplaintChanged(complaintId))
        }
        viewModel.onAction(.fieldsChanged(
            plate: draft.plate,
            plateRegion: draft.plateRegion,
            address: draft.address,
            description: draft.description,
            notes: draft.notes,
            occurredAtIso: draft.occurredAtIso
        ))
        _ = voiceAudio.stopRecording()
        showVoiceAssistSheet = false
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
        !viewModel.state.awaitingVideoProcessingDecision &&
        !detectionProgressMinimized
    }

    private var showsBottomSubmitBar: Bool {
        viewModel.state.stage == .verify && !isDetectionSheetVisible && !isLandscapeComposer && !isKeyboardVisible
    }

    private var isReportedAiFabVisible: Bool {
        viewModel.state.primaryMedia != nil && !isKeyboardVisible
    }

    @ViewBuilder
    private var reportedAiFloatingButton: some View {
        if isReportedAiFabVisible {
            Button(action: openVoiceAssistant) {
                AnimatedSparkleIcon(size: 21, color: .white)
                    .frame(width: reportedAiFabDiameter, height: reportedAiFabDiameter)
                    .background(Color.reportedOrange)
                    .clipShape(Circle())
                    .shadow(color: Color.black.opacity(0.22), radius: 12, x: 0, y: 6)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Reported AI")
            .padding(.trailing, isLandscapeComposer ? 24 : 20)
            .padding(.bottom, reportedAiFabBottomPadding)
        }
    }

    @ViewBuilder
    private var voiceAssistantFloatingOverlay: some View {
        if showVoiceAssistSheet {
            GeometryReader { geometry in
                VStack(spacing: 0) {
                    Spacer(minLength: 0)
                    voiceAssistantSheetContent
                        .frame(height: voiceAssistantOverlayHeight(for: geometry.size.height))
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            .transition(.move(edge: .bottom).combined(with: .opacity))
            .animation(.snappy, value: showVoiceAssistSheet)
            .animation(.snappy, value: voiceAssistSheetDetent)
        }
    }

    private var voiceAssistantSheetContent: some View {
        VoiceReportAssistantSheet(
            audio: voiceAudio,
            transcript: voiceTranscript,
            draft: voiceDraft,
            changeRows: voiceDraft?.changeRows(currentState: viewModel.state, complaintOptions: complaintOptions) ?? [],
            isProcessing: voiceProcessing,
            isMinimized: isVoiceAssistSheetMinimized,
            modelInstalled: voiceModelInstalled,
            modelDownloading: voiceModelDownloading,
            modelDownloadProgress: voiceModelDownloadProgress,
            modelDownloadSizeLabel: OnDeviceGemmaVoiceDraftEngine.modelDownloadSizeLabel,
            accelerationMessage: OnDeviceGemmaVoiceDraftEngine.accelerationMessage,
            error: voiceError,
            onRequestPermission: {
                Task {
                    let granted = await voiceAudio.requestPermissions()
                    if !granted {
                        voiceError = voiceAudio.errorMessage ?? "Microphone access is needed to talk through report fields."
                    } else {
                        voiceError = nil
                    }
                }
            },
            onDownloadModel: {
                Task {
                    await downloadVoiceModel()
                }
            },
            onTalk: {
                Task {
                    await startVoiceAssistantCapture()
                }
            },
            onStop: {
                beginStoppingVoiceAssistantCapture()
            },
            onCancelProcessing: {
                cancelVoiceProcessing()
            },
            onClear: {
                resetVoiceAssistant()
            },
            onApply: { draft in
                applyVoiceDraft(draft)
            },
            onDismiss: dismissVoiceAssistant
        )
    }

    private func voiceAssistantOverlayHeight(for availableHeight: CGFloat) -> CGFloat {
        let desiredHeight: CGFloat
        if voiceAssistSheetDetent == .large {
            desiredHeight = availableHeight - 18
        } else if isVoiceAssistSheetMinimized {
            desiredHeight = voiceAssistantMinimizedSheetHeight
        } else {
            desiredHeight = voiceAssistantCompactSheetHeight
        }
        let maximumHeight = max(voiceAssistantMinimizedSheetHeight, availableHeight - 6)
        return min(max(desiredHeight, voiceAssistantMinimizedSheetHeight), maximumHeight)
    }

    private var composerContent: some View {
        GeometryReader { geometry in
            let isLandscape = currentInterfaceIsLandscape(fallbackSize: geometry.size)
            ScrollViewReader { proxy in
                let verticalPadding: CGFloat = viewModel.state.stage == .verify ? (isLandscape ? 20 : 0) : 16
                let horizontalPadding: CGFloat = isLandscape ? 8 : 16
                let leadingPadding = horizontalPadding
                let trailingPadding = horizontalPadding + (isLandscape ? horizontalUnsafeAreaWidth : 0)
                let landscapeLeftColumnWidth = landscapeMediaColumnWidth(for: geometry.size.width)
                let landscapeColumnSpacing: CGFloat = 14
                let fabSpacerHeight = isReportedAiFabVisible ? reportedAiFabDiameter : 0
                let bottomPadding: CGFloat = (viewModel.state.stage == .verify ? (isLandscape ? 24 + geometry.safeAreaInsets.bottom : 16) : 8) + fabSpacerHeight
                let content = ScrollView {
                    Group {
                        switch viewModel.state.stage {
                        case .pickMedia:
                            if isLandscape && showEmbeddedLandscapeToolbar {
                                VStack(alignment: .leading, spacing: 10) {
                                    embeddedLandscapeToolbar
                                    pickMediaContent(availableWidth: geometry.size.width - leadingPadding - trailingPadding)
                                }
                            } else {
                                pickMediaContent(availableWidth: geometry.size.width - leadingPadding - trailingPadding)
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
                .scrollDismissesKeyboard(.interactively)
                ZStack(alignment: .bottom) {
                    content
                        .task {
                            viewModel.onAction(.loadDraft)
                        }
                        .onAppear {
                            isLandscapeComposer = isLandscape
                        }
                        .onChange(of: geometry.size) { _, newSize in
                            isLandscapeComposer = currentInterfaceIsLandscape(fallbackSize: newSize)
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
        ComplaintChooserSheet(
            title: "What kind of complaint is this?",
            options: complaintOptions,
            selectedComplaintId: viewModel.state.selectedComplaintId,
            activeAnimatedComplaintId: activeAnimatedComplaintId
        ) { option in
            ReportedAnalytics.logComplaintSelected(complaintId: option.id, surface: "pending_media")
            viewModel.onAction(.pendingComplaintConfirmed(option.id))
        }
        .interactiveDismissDisabled()
        .presentationDetents([.height(complaintChooserSheetDetentHeight(optionCount: complaintOptions.count))])
        .presentationDragIndicator(.hidden)
        .presentationBackground(Color(.systemBackground))
    }

    private func handleAddressQueryChanged(_ oldValue: String, _ newValue: String) {
        guard viewModel.state.stage == .verify,
              !newValue.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              newValue != viewModel.state.address else {
            addressSearchTask?.cancel()
            viewModel.onAction(.addressSuggestionsChanged([]))
            return
        }
        addressSearchTask?.cancel()
        addressSearchTask = Task {
            viewModel.onAction(.addressLookupLoadingChanged(true))
            try? await Task.sleep(for: .milliseconds(250))
            if Task.isCancelled { return }
            let suggestions = await searchReportAddresses(
                query: newValue,
                latitude: viewModel.state.latitude,
                longitude: viewModel.state.longitude,
                address: viewModel.state.address
            )
            if Task.isCancelled { return }
            viewModel.onAction(.addressSuggestionsChanged(suggestions))
        }
    }

    private func startVideoScan(from startTimeSeconds: Double = 0, resetProgress: Bool = true) {
        let videoMedia = viewModel.state.primaryMedia
        if resetProgress {
            viewModel.onAction(.videoProcessingDecision(true))
        }
        videoScanTask?.cancel()
        videoScanGeneration += 1
        videoScanPaused = false
        let scanGeneration = videoScanGeneration
        videoScanTask = Task {
            guard let videoMedia else {
                viewModel.onAction(.detectionFinished())
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
                    viewModel.onAction(.detectionProgressChanged(
                        message: "Scanning frame \(progress.processedFrames)/\(totalFrames), \(foundText)",
                        progress: percent,
                        framePreview: progress.framePreview,
                        frameTimeSeconds: progress.frameTimeSeconds,
                        videoDurationSeconds: progress.durationSeconds,
                        frameCandidates: progress.frameCandidates,
                        allCandidates: progress.allCandidates
                    ))
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
            viewModel.onAction(.detectionFinished(
                candidates: candidates,
                inferredPlate: topCandidate?.plate,
                inferredState: topCandidate?.state
            ))
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
    private func pickMediaContent(availableWidth: CGFloat) -> some View {
        let pickerMaxWidth = complaintPickerMaxWidth(for: availableWidth)
        let tileMetrics = complaintTileMetrics(for: availableWidth)
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
                LazyVGrid(columns: complaintPickerColumns(for: availableWidth), spacing: availableWidth >= 700 ? 18 : 10) {
                    ForEach(complaintOptions) { option in
                        ComplaintMediaTile(
                            option: option,
                            animate: option.id == activeAnimatedComplaintId,
                            showImage: viewModel.state.showComplaintImages,
                            imageHeight: tileMetrics.imageHeight,
                            minHeight: tileMetrics.minHeight,
                            titleFont: availableWidth >= 700 ? .title2.weight(.semibold) : .subheadline.weight(.semibold)
                        ) {
                            ReportedAnalytics.logComplaintSelected(complaintId: option.id, surface: "pick_media")
                            pendingComplaintId = option.id
                            presentMediaPicker()
                        }
                    }
                    UploadMediaTile(
                        minHeight: tileMetrics.minHeight,
                        iconSize: availableWidth >= 700 ? 54 : 30,
                        titleFont: availableWidth >= 700 ? .title2.weight(.semibold) : .subheadline.weight(.semibold)
                    ) {
                        pendingComplaintId = nil
                        presentMediaPicker()
                    }
                }
            }
            .frame(maxWidth: pickerMaxWidth)
            .frame(maxWidth: .infinity, alignment: .center)
        }
    }

    @ViewBuilder
    private var detectionProgressModal: some View {
        if isDetectionSheetVisible {
            let isVideoScan = viewModel.state.primaryMedia?.isVideo == true
            let hasCurrentFrameContent = isVideoScan ||
                viewModel.state.detectionFramePreview != nil ||
                !viewModel.state.detectionFrameCandidates.isEmpty
            ZStack(alignment: .bottom) {
                Color.black.opacity(0.18)
                    .ignoresSafeArea()
                VStack(alignment: .leading, spacing: 0) {
                HStack {
                    Text(isVideoScan ? "Scanning video" : "Scanning photo")
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
                        if let media = viewModel.state.primaryMedia, media.isVideo {
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
                        if hasCurrentFrameContent {
                            Text(isVideoScan ? "Current frame" : "Scan preview")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(.secondary)
                        }
                        if isVideoScan {
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
                        }
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
                            .frame(height: 44)
                        }
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
                .frame(maxHeight: isVideoScan ? nil : 190)

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
                .frame(maxHeight: isVideoScan ? CGFloat.infinity : nil, alignment: .bottom)
                .fixedSize(horizontal: false, vertical: !isVideoScan)
                .background(Color(.systemBackground))
                .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
                .padding(.horizontal, isVideoScan ? 0 : 12)
                .padding(.top, isVideoScan ? 10 : 0)
                .padding(.bottom, isVideoScan ? 0 : 12)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
            .ignoresSafeArea(.container, edges: .bottom)
            .transition(.opacity)
        }
    }

    private func selectVideoCandidate(_ candidate: ComposerState.PlateCandidate) {
        metadataTask?.cancel()
        videoScanGeneration += 1
        videoScanTask?.cancel()
        videoScanTask = nil
        viewModel.onAction(.plateCandidateChosen(candidate))
        viewModel.onAction(.detectionFinished(
            candidates: viewModel.state.plateCandidates.isEmpty ? [candidate] : viewModel.state.plateCandidates,
            inferredPlate: candidate.plate,
            inferredState: candidate.state
        ))
    }

    private func cancelVideoScan() {
        videoScanGeneration += 1
        videoScanTask?.cancel()
        videoScanTask = nil
        videoScanPaused = false
        detectionProgressMinimized = false
        viewModel.onAction(.videoProcessingCancelled)
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
                    mediaPanel(
                        selectedCandidate: selectedCandidate,
                        expandPreview: true,
                        previewHeight: primaryPreviewHeight(for: availableSize)
                    )
                    landscapeMediaActions
                }
                .frame(width: landscapeMediaColumnWidth(for: availableSize.width))
                VStack(alignment: .leading, spacing: 10) {
                    verifyFormContent(isLandscape: true, isTablet: availableSize.width >= 700)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            }
        } else {
            VStack(alignment: .leading, spacing: 10) {
                if let error = viewModel.state.error {
                    MessageView(text: error)
                }
                mediaPanel(
                    selectedCandidate: selectedCandidate,
                    expandPreview: false,
                    previewHeight: primaryPreviewHeight(for: availableSize)
                )
                addMoreMediaButton
                if viewModel.state.validationErrors.media != nil {
                    ValidationMessage(text: viewModel.state.validationErrors.media)
                }
                verifyFormContent(isLandscape: false, isTablet: availableSize.width >= 700)
            }
        }
    }

    @ViewBuilder
    private func mediaPanel(
        selectedCandidate: ComposerState.PlateCandidate?,
        expandPreview: Bool,
        previewHeight: CGFloat
    ) -> some View {
        if let media = viewModel.state.primaryMedia {
            PrimarySubmissionPreview(
                mediaItems: [media] + viewModel.state.extraMedia,
                selectedCandidate: selectedCandidate,
                candidates: viewModel.state.plateCandidates,
                selectedPlate: viewModel.state.selectedPlateCandidate,
                previewHeight: previewHeight,
                onCandidateTapped: { candidate, image in
                    pendingPlatePreviewImage = image
                    pendingPlateCandidate = candidate
                },
                onCandidateConfirmedFromFullScreen: { candidate in
                    viewModel.onAction(.plateCandidateChosen(candidate))
                },
                onRemove: { media in viewModel.onAction(.mediaRemoved(media)) }
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
            Text("Report")
                .font(.system(size: 20, weight: .regular))
                .lineLimit(1)
                .overlay(alignment: .trailing) {
                    Button {
                        openVoiceAssistant()
                    } label: {
                        AnimatedSparkleIcon(size: 17)
                            .frame(width: 30, height: 30)
                    }
                    .buttonStyle(.plain)
                    .foregroundStyle(Color.reportedOrange)
                    .accessibilityLabel("Reported AI")
                    .offset(x: 31)
                }
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
                    .frame(minWidth: 108)
            }
            .buttonStyle(.bordered)
            .buttonBorderShape(.capsule)
            .controlSize(.large)
            .tint(Color.reportedOrange)
            .shadow(color: Color.black.opacity(0.16), radius: 10, x: 0, y: 4)
            .opacity(shellHasDraftContent ? 1 : 0)
            .disabled(!shellHasDraftContent)

            Spacer()

            Button(action: submitReport) {
                Label(viewModel.state.loading ? "Submitting" : "Submit", systemImage: "paperplane.fill")
                    .frame(minWidth: 132)
            }
            .buttonStyle(.borderedProminent)
            .buttonBorderShape(.capsule)
            .controlSize(.large)
            .tint(Color.reportedOrange)
            .shadow(color: Color.black.opacity(0.16), radius: 10, x: 0, y: 4)
            .disabled(viewModel.state.loading)
        }
    }

    private var addMoreMediaButton: some View {
        SecondaryButton(title: addMediaButtonTitle) {
            pendingComplaintId = viewModel.state.selectedComplaintId
            presentMediaPicker()
        }
    }

    private var addMediaButtonTitle: String {
        if viewModel.state.isPhiladelphiaSubmission {
            return viewModel.state.primaryMedia == nil ? "Add photo" : "Add another photo"
        }
        return viewModel.state.primaryMedia == nil ? "Add photo or video" : "Add more photos or videos"
    }

    private var submitInlineButton: some View {
        VStack(spacing: 8) {
            submitButton()
            submitProgressContent
        }
    }

    private func submitBottomBar() -> some View {
        VStack(spacing: 10) {
            if viewModel.state.loading {
                submitProgressContent
            }
            submitButton()
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, usesTabletSubmitMetrics ? 16 : 20)
        .padding(.top, usesTabletSubmitMetrics ? 12 : 8)
        .padding(.bottom, usesTabletSubmitMetrics ? 16 : 20)
        .background(.regularMaterial)
        .overlay(alignment: .top) {
            Divider()
        }
    }

    private var horizontalUnsafeAreaWidth: CGFloat {
        let insets = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first { $0.isKeyWindow }?
            .safeAreaInsets ?? .zero
        return max(insets.left, insets.right, 0)
    }

    private var usesTabletSubmitMetrics: Bool {
        UIDevice.current.userInterfaceIdiom == .pad || horizontalSizeClass == .regular
    }

    private func submitButton() -> some View {
        let buttonHeight: CGFloat = usesTabletSubmitMetrics ? 68 : 50
        let cornerRadius: CGFloat = usesTabletSubmitMetrics ? 18 : 12

        return Button(action: submitReport) {
            Text(viewModel.state.loading ? "Submitting..." : "Submit Report")
                .font(usesTabletSubmitMetrics ? .title2.weight(.semibold) : .headline.weight(.semibold))
                .multilineTextAlignment(.center)
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .frame(height: buttonHeight)
                .background(
                    RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                        .fill(Color.reportedOrange)
                )
                .contentShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
        }
        .buttonStyle(.plain)
        .disabled(viewModel.state.loading)
        .opacity(viewModel.state.loading ? 0.72 : 1)
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
    private func verifyFormContent(isLandscape: Bool, isTablet: Bool) -> some View {
        ReportVerifyFields(
            complaintValue: complaintOptions.first(where: { $0.id == viewModel.state.selectedComplaintId })?.title ?? "Select complaint",
            plateValue: viewModel.state.plate,
            plateCandidateCount: viewModel.state.plateCandidates.count,
            plateRegionValue: viewModel.state.plateRegion,
            vehicleLookupDetails: viewModel.state.vehicleLookupDetails,
            vehicleLookupInFlight: viewModel.state.vehicleLookupInFlight,
            vehicleLookupMessage: viewModel.state.vehicleLookupMessage,
            addressValue: viewModel.state.addressQuery,
            occurredAtValue: viewModel.state.occurredAtIso.reportDateTimeDisplay,
            validationErrors: viewModel.state.validationErrors,
            descriptionText: Binding(get: { viewModel.state.description }, set: { viewModel.onAction(.fieldsChanged(description: $0)) }),
            notesText: Binding(get: { viewModel.state.notes }, set: { viewModel.onAction(.fieldsChanged(notes: $0)) }),
            philadelphiaDetails: viewModel.state.isPhiladelphiaSubmission ? viewModel.state.philadelphiaMobilityAccessDetails : nil,
            isLandscape: isLandscape,
            isTablet: isTablet,
            onComplaintTapped: {
                ReportedAnalytics.logComplaintChooserTapped(
                    surface: "verify",
                    selectedComplaintId: viewModel.state.selectedComplaintId
                )
                showComplaintChooser = true
            },
            onPlateTapped: {
                ReportedAnalytics.logPlateChooserTapped(
                    candidateCount: viewModel.state.plateCandidates.count,
                    hasPlate: !viewModel.state.plate.isEmpty
                )
                showPlateEntryScreen = true
            },
            onStateTapped: {
                ReportedAnalytics.logStateChooserTapped(currentRegion: viewModel.state.plateRegion)
                showAllPlateRegions = false
                showPlateRegionSheet = true
            },
            onAddressTapped: {
                    openAddressSearchScreen()
            },
            onAddressClear: {
                viewModel.onAction(.addressQueryChanged(""))
            },
            onAddressMapTapped: {
                openAddressMapSheet()
            },
            onOccurredAtTapped: {
            refreshOccurredAtImageTime()
            showOccurredAtPicker = true
            },
            onDescriptionTapped: {
                fullScreenTextEditorField = .description
            },
            onNotesTapped: {
                fullScreenTextEditorField = .notes
            },
            onPhiladelphiaMobilityAccessChanged: { details in
                viewModel.onAction(.philadelphiaMobilityAccessChanged(
                    blockNumber: details.blockNumber,
                    streetName: details.streetName,
                    zipCode: details.zipCode,
                    vehicleMake: details.vehicleMake,
                    vehicleModel: details.vehicleModel,
                    bodyStyle: details.bodyStyle,
                    vehicleColor: details.vehicleColor,
                    violationObserved: details.violationObserved,
                    frequency: details.frequency
                ))
            },
            onDescriptionChanged: {
                viewModel.onAction(.draftPersistenceRequested)
            },
            onNotesChanged: {
                viewModel.onAction(.draftPersistenceRequested)
            }
        )
    }

    private func reportTextBinding(for field: ReportLongTextField) -> Binding<String> {
        switch field {
        case .description:
            Binding(
                get: { viewModel.state.description },
                set: { viewModel.onAction(.fieldsChanged(description: $0)) }
            )
        case .notes:
            Binding(
                get: { viewModel.state.notes },
                set: { viewModel.onAction(.fieldsChanged(notes: $0)) }
            )
        }
    }

    private func openAddressSearchScreen() {
        refreshAddressImageLocation()
        showAddressSearchScreen = true
    }

    private func openAddressMapSheet() {
        refreshAddressImageLocation()
        showAddressMapSheet = true
    }

    private func refreshAddressImageLocation() {
        guard let media = viewModel.state.primaryMedia, !media.isVideo else {
            refreshedPhotoAddressSuggestion = nil
            imageAddressRefreshInFlight = false
            imageAddressRefreshTask?.cancel()
            return
        }
        imageAddressRefreshTask?.cancel()
        imageAddressRefreshInFlight = true
        imageAddressRefreshTask = Task {
            let metadata = await extractSubmissionMetadata(from: media)
            let suggestion = await addressSuggestionFromImageMetadata(metadata)
            if Task.isCancelled { return }
            await MainActor.run {
                imageAddressRefreshInFlight = false
                refreshedPhotoAddressSuggestion = suggestion
            }
        }
    }

    private func refreshOccurredAtImageTime() {
        guard let media = viewModel.state.primaryMedia, !media.isVideo else {
            refreshedPhotoOccurredAtIso = nil
            imageTimeRefreshInFlight = false
            imageTimeRefreshTask?.cancel()
            return
        }
        refreshedPhotoOccurredAtIso = viewModel.state.photoOccurredAtIso
        imageTimeRefreshTask?.cancel()
        imageTimeRefreshInFlight = true
        imageTimeRefreshTask = Task {
            let metadata = await extractSubmissionMetadata(from: media)
            if Task.isCancelled { return }
            await MainActor.run {
                imageTimeRefreshInFlight = false
                refreshedPhotoOccurredAtIso = metadata.occurredAtIso
                if let occurredAtIso = metadata.occurredAtIso, !occurredAtIso.isEmpty {
                    viewModel.onAction(.metadataApplied(photoOccurredAtIso: occurredAtIso))
                }
            }
        }
    }

    private func submitReport() {
        ReportedAnalytics.logSubmitReportTapped(
            stage: viewModel.state.stage == .verify ? "verify" : "pick_media",
            isAuthorized: isAuthorized,
            mediaToSubmitMillis: viewModel.state.mediaToSubmitMillis
        )
        guard viewModel.onAction(.submitValidationRequested) else { return }
        if isAuthorized {
            viewModel.onAction(.submitPressed)
        } else {
            onRequireLogin()
        }
    }

    private func presentMediaPicker() {
        guard viewModel.remainingMediaSlots > 0 else {
            viewModel.onAction(.mediaLimitReached)
            return
        }
        multiPickerPresented = true
    }

    private func handlePickedItem(_ item: PhotosPickerItem) async {
        await handlePickedItems([item])
    }

    private func handlePickedItems(_ items: [PhotosPickerItem]) async {
        guard !items.isEmpty else { return }
        var mediaItems: [ComposerState.SubmissionMedia] = []
        for item in items {
            if let media = await loadSubmissionMedia(from: item) {
                mediaItems.append(media)
            }
        }
        let complaintId = pendingComplaintId
        pendingComplaintId = nil
        await applyPickedMedia(mediaItems, preferredComplaintId: complaintId)
    }

    private func applyPickedMedia(_ mediaItems: [ComposerState.SubmissionMedia], preferredComplaintId: String?) async {
        guard let primary = mediaItems.first else { return }

        if viewModel.state.primaryMedia != nil {
            for media in mediaItems {
                guard viewModel.onAction(.extraMediaAdded(media)) else { break }
                await inspectExtraMediaForPhiladelphiaVideo(media)
            }
            return
        }

        if let complaintId = preferredComplaintId {
            viewModel.onAction(.primaryMediaChosen(primary, complaintId: complaintId))
        } else {
            viewModel.onAction(.uploadMediaChosen(primary))
        }
        for media in mediaItems.dropFirst() {
            guard viewModel.onAction(.extraMediaAdded(media)) else { break }
            await inspectExtraMediaForPhiladelphiaVideo(media)
        }
        await processMetadataAndDetection(for: primary)
    }

    private func inspectExtraMediaForPhiladelphiaVideo(_ media: ComposerState.SubmissionMedia) async {
        guard media.isVideo else { return }
        let metadata = await extractSubmissionMetadata(from: media)
        guard let latitude = metadata.latitude,
              let longitude = metadata.longitude,
              reportAddressProvider(latitude: latitude, longitude: longitude, address: "") == .philadelphia else {
            return
        }
        await MainActor.run {
            _ = viewModel.onAction(.mediaRejected(media, message: philadelphiaSubmissionVideoMessage))
        }
    }

    private func handleSharedMediaImport(id: String) async {
        let mediaItems = SharedMediaImportStore.consumeImport(id: id)
        guard !mediaItems.isEmpty else { return }
        await applyPickedMedia(mediaItems, preferredComplaintId: viewModel.state.selectedComplaintId)
    }

#if DEBUG
    private func handleDebugMediaImportIfNeeded() async {
        guard !debugMediaImportConsumed else { return }
        guard viewModel.state.draftLoaded else { return }
        let sourcePaths = debugMediaImportPaths()
        guard !sourcePaths.isEmpty else { return }

        debugMediaImportConsumed = true
        let mediaItems = sourcePaths.compactMap { sourcePath -> ComposerState.SubmissionMedia? in
            let sourceURL = URL(fileURLWithPath: sourcePath)
            guard FileManager.default.fileExists(atPath: sourceURL.path) else {
                print("ReportedDebugImport: missing media at \(sourceURL.path)")
                return nil
            }

            do {
                let data = try Data(contentsOf: sourceURL)
                let fileURL = try PersistentMediaStore.save(
                    data: data,
                    preferredName: sourceURL.deletingPathExtension().lastPathComponent,
                    fileExtension: sourceURL.pathExtension.isEmpty ? "jpg" : sourceURL.pathExtension
                )
                print("ReportedDebugImport: importing \(fileURL.path)")
                return ComposerState.SubmissionMedia(
                    fileURL: fileURL,
                    displayName: fileURL.lastPathComponent,
                    isVideo: false
                )
            } catch {
                print("ReportedDebugImport: failed to import \(sourceURL.path): \(error)")
                return nil
            }
        }
        guard let primary = mediaItems.first else { return }
        viewModel.onAction(.uploadMediaChosen(primary))
        mediaItems.dropFirst().forEach { viewModel.onAction(.extraMediaAdded($0)) }
        await processMetadataAndDetection(for: primary)
    }

    private func debugMediaImportPaths() -> [String] {
        let arguments = ProcessInfo.processInfo.arguments
        var paths: [String] = []
        var index = arguments.startIndex
        while index < arguments.endIndex {
            if arguments[index] == "--reported-debug-import-media" {
                let nextIndex = arguments.index(after: index)
                if nextIndex < arguments.endIndex {
                    paths.append(arguments[nextIndex])
                    index = nextIndex
                }
            }
            index = arguments.index(after: index)
        }
        if let environmentValue = ProcessInfo.processInfo.environment["REPORTED_DEBUG_IMPORT_MEDIA"] {
            paths.append(contentsOf: environmentValue.split(separator: "|").map(String.init))
        }
        return paths.filter { !$0.isEmpty }
    }
#endif

    private func processMetadataAndDetection(for media: ComposerState.SubmissionMedia) async {
        metadataTask?.cancel()
        metadataTask = Task {
            viewModel.onAction(.detectionProgressChanged(message: "Reading media metadata", progress: 0.1))
            let metadata = await extractSubmissionMetadata(from: media)
            let addressSuggestion: ComposerState.AddressSuggestion?
            if let latitude = metadata.latitude, let longitude = metadata.longitude {
                let provider = reportAddressProvider(latitude: latitude, longitude: longitude, address: "")
                viewModel.onAction(.detectionProgressChanged(message: provider.reverseLookupLabel, progress: 0.35))
                addressSuggestion = await reverseGeocodeAddress(latitude: latitude, longitude: longitude)
            } else {
                addressSuggestion = nil
            }
            if Task.isCancelled { return }
            if media.isVideo,
               let latitude = metadata.latitude,
               let longitude = metadata.longitude,
               reportAddressProvider(latitude: latitude, longitude: longitude, address: "") == .philadelphia {
                viewModel.onAction(.mediaRejected(media, message: philadelphiaSubmissionVideoMessage))
                return
            }
            viewModel.onAction(.metadataApplied(
                occurredAtIso: metadata.occurredAtIso,
                photoOccurredAtIso: media.isVideo ? nil : metadata.occurredAtIso,
                latitude: metadata.latitude,
                longitude: metadata.longitude,
                inferredState: addressSuggestion?.region,
                inferredAddress: addressSuggestion?.label,
                addressSuggestion: addressSuggestion
            ))
            if !media.isVideo {
                viewModel.onAction(.detectionProgressChanged(message: "Detecting plates", progress: 0.65))
                let candidates = await NativeAlprEngine.shared.detectLicensePlates(
                    media: media,
                    expectedComplaintHint: selectedComplaintDetectionHint
                )
#if DEBUG
                let summary = candidates.map { candidate in
                    "\(candidate.plate)/\(candidate.state ?? "-") conf=\(candidate.confidence) raw=\(candidate.rawPlateText ?? "-")"
                }.joined(separator: ", ")
                print("ReportedALPR: candidates=\(candidates.count) [\(summary)]")
#endif
                if !Task.isCancelled {
                    viewModel.onAction(.detectionFinished(
                        candidates: candidates,
                        inferredPlate: candidates.first?.plate,
                        inferredState: addressSuggestion?.region
                    ))
                }
            }
        }
    }

}

private struct ComposerPickerModifier: ViewModifier {
    @Binding var singlePickerPresented: Bool
    @Binding var multiPickerPresented: Bool
    @Binding var pickedItem: PhotosPickerItem?
    @Binding var pickedItems: [PhotosPickerItem]
    let maxSelectionCount: Int
    let allowsVideos: Bool
    let handlePickedItem: (PhotosPickerItem) async -> Void
    let handlePickedItems: ([PhotosPickerItem]) async -> Void

    func body(content: Self.Content) -> some View {
        content
            .photosPicker(
                isPresented: $singlePickerPresented,
                selection: $pickedItem,
                matching: mediaFilter,
                preferredItemEncoding: .current
            )
            .photosPicker(
                isPresented: $multiPickerPresented,
                selection: $pickedItems,
                maxSelectionCount: maxSelectionCount,
                matching: mediaFilter,
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
                    await handlePickedItems(newItems)
                    pickedItems = []
                }
            }
    }

    private var mediaFilter: PHPickerFilter {
        allowsVideos ? .any(of: [.images, .videos]) : .images
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
        let searchableText = "\(category.name) \(category.key)".lowercased()
        return keywords.contains { searchableText.contains($0) }
    }) else {
        return fallback
    }
    return ComplaintOption(
        id: category.id,
        title: category.name,
        imageName: fallback.imageName,
        imageExtension: fallback.imageExtension,
        lottieName: fallback.lottieName
    )
}

struct ExtractedSubmissionMetadata {
    let occurredAtIso: String?
    let latitude: Double?
    let longitude: Double?
}

private struct ComplaintChooserSheet: View {
    let title: String
    let options: [ComplaintOption]
    let selectedComplaintId: String?
    let activeAnimatedComplaintId: String?
    let onSelected: (ComplaintOption) -> Void
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

    var body: some View {
        GeometryReader { geometry in
            ScrollView {
                VStack(alignment: .leading, spacing: isTabletLayout ? 20 : 16) {
                    Text(title)
                        .font(isTabletLayout ? .largeTitle.bold() : .title2.bold())
                    LazyVGrid(columns: columns(for: geometry.size.width), spacing: isTabletLayout ? 16 : 12) {
                        ForEach(options) { option in
                            ZStack(alignment: .topTrailing) {
                                ComplaintMediaTile(
                                    option: option,
                                    animate: option.id == activeAnimatedComplaintId,
                                    showImage: true,
                                    imageHeight: tileMetrics(for: geometry.size.width).imageHeight,
                                    minHeight: tileMetrics(for: geometry.size.width).minHeight,
                                    titleFont: isTabletLayout ? .title3.weight(.semibold) : .subheadline.weight(.semibold)
                                ) {
                                    onSelected(option)
                                }
                                if option.id == selectedComplaintId {
                                    Image(systemName: "checkmark.circle.fill")
                                        .font(.title3.weight(.semibold))
                                        .foregroundStyle(Color.green)
                                        .background(Color(.systemBackground), in: Circle())
                                        .padding(10)
                                }
                            }
                        }
                    }
                }
                .frame(maxWidth: maxContentWidth(for: geometry.size.width), alignment: .leading)
                .frame(maxWidth: .infinity)
                .padding(.horizontal, 16)
                .padding(.top, 16)
                .padding(.bottom, 8)
            }
        }
    }

    private func columns(for width: CGFloat) -> [GridItem] {
        let columnCount = width >= 700 ? 3 : 2
        return Array(repeating: GridItem(.flexible(), spacing: isTabletLayout ? 16 : 12), count: columnCount)
    }

    private func maxContentWidth(for width: CGFloat) -> CGFloat {
        width >= 700 ? min(width, 780) : width
    }

    private func tileMetrics(for width: CGFloat) -> (imageHeight: CGFloat, minHeight: CGFloat) {
        width >= 700 ? (136, 218) : (96, 150)
    }

    private var isTabletLayout: Bool {
        UIDevice.current.userInterfaceIdiom == .pad || horizontalSizeClass == .regular
    }
}

private struct ComplaintMediaTile: View {
    let option: ComplaintOption
    var animate = false
    var showImage = true
    var imageHeight: CGFloat = 96
    var minHeight: CGFloat = 150
    var titleFont: Font = .subheadline.weight(.semibold)
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 8) {
                if showImage {
                    ComplaintOptionImage(option: option, animate: animate)
                        .frame(height: imageHeight)
                        .frame(maxWidth: .infinity)
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                }
                Text(option.title)
                    .font(titleFont)
                    .multilineTextAlignment(.center)
                    .foregroundStyle(.primary)
                    .frame(maxWidth: .infinity)
                    .lineLimit(2)
                    .minimumScaleFactor(0.82)
            }
            .padding(8)
            .frame(maxWidth: .infinity, minHeight: minHeight, alignment: .top)
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
                    contentMode: .scaleAspectFit
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
                    .aspectRatio(contentMode: .fit)
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
    var minHeight: CGFloat = 150
    var iconSize: CGFloat = 30
    var titleFont: Font = .subheadline.weight(.semibold)
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 8) {
                Spacer()
                Image(systemName: "square.and.arrow.up")
                    .font(.system(size: iconSize, weight: .medium))
                    .foregroundStyle(Color.reportedOrange)
                Text("Upload")
                    .font(titleFont)
                    .foregroundStyle(.primary)
                Spacer()
            }
            .frame(maxWidth: .infinity, minHeight: minHeight)
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
    let mediaItems: [ComposerState.SubmissionMedia]
    let selectedCandidate: ComposerState.PlateCandidate?
    let candidates: [ComposerState.PlateCandidate]
    let selectedPlate: String?
    let previewHeight: CGFloat
    let onCandidateTapped: (ComposerState.PlateCandidate, UIImage?) -> Void
    let onCandidateConfirmedFromFullScreen: (ComposerState.PlateCandidate) -> Void
    let onRemove: (ComposerState.SubmissionMedia) -> Void
    @State private var selectedMediaURL: URL?
    @State private var videoPlayerMedia: ComposerState.SubmissionMedia?
    @State private var imageViewerMedia: ComposerState.SubmissionMedia?
    @GestureState private var carouselDragOffset: CGFloat = 0

    private var selectedIndex: Int {
        if let selectedMediaURL,
           let index = mediaItems.firstIndex(where: { $0.fileURL == selectedMediaURL }) {
            return index
        }
        return 0
    }

    private var selectedMedia: ComposerState.SubmissionMedia? {
        guard !mediaItems.isEmpty else { return nil }
        return mediaItems[min(selectedIndex, mediaItems.count - 1)]
    }

    var body: some View {
        ZStack(alignment: .topTrailing) {
            ZStack(alignment: .bottom) {
                GeometryReader { proxy in
                    HStack(spacing: 0) {
                        ForEach(mediaItems.indices, id: \.self) { index in
                            mediaPreviewPage(media: mediaItems[index], isPrimary: index == 0)
                                .frame(width: proxy.size.width, height: previewHeight)
                        }
                    }
                    .frame(
                        width: proxy.size.width * CGFloat(max(mediaItems.count, 1)),
                        height: previewHeight,
                        alignment: .leading
                    )
                    .offset(x: -CGFloat(selectedIndex) * proxy.size.width + carouselDragOffset)
                    .animation(.interactiveSpring(response: 0.28, dampingFraction: 0.86), value: selectedMediaURL)
                    .animation(.interactiveSpring(response: 0.28, dampingFraction: 0.86), value: mediaItems.map(\.fileURL))
                    .contentShape(Rectangle())
                }
                carouselControls
            }
            .frame(maxWidth: .infinity)
            .frame(height: previewHeight)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .contentShape(RoundedRectangle(cornerRadius: 12))
            .simultaneousGesture(carouselDragGesture)
            .onAppear {
                if selectedMediaURL == nil {
                    selectedMediaURL = mediaItems.first?.fileURL
                }
            }
            .onChange(of: mediaItems.map(\.fileURL)) { _, urls in
                if let selectedMediaURL, urls.contains(selectedMediaURL) {
                    return
                }
                selectedMediaURL = urls.first
            }

            if let selectedMedia {
                Button(action: { onRemove(selectedMedia) }) {
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
        }
        .padding(.trailing, 12)
        .fullScreenCover(item: $imageViewerMedia) { media in
            if let image = UIImage(contentsOfFile: media.fileURL.path) {
                let isPrimary = media.fileURL == mediaItems.first?.fileURL
                FullScreenImageViewer(
                    image: image,
                    candidates: isPrimary ? candidates : [],
                    selectedPlate: isPrimary ? selectedPlate : nil,
                    focalPoint: isPrimary ? stillImageFocalPoint : nil,
                    onDismiss: { imageViewerMedia = nil },
                    onCandidateConfirmed: onCandidateConfirmedFromFullScreen
                )
            }
        }
        .fullScreenCover(item: $videoPlayerMedia) { media in
            if media.fileURL == mediaItems.first?.fileURL, let candidate = selectedCandidate {
                VideoPlaybackCandidateView(
                    media: media,
                    candidate: candidate,
                    onDismiss: { videoPlayerMedia = nil }
                )
            } else {
                BasicVideoPlaybackView(
                    media: media,
                    onDismiss: { videoPlayerMedia = nil }
                )
            }
        }
    }

    @ViewBuilder
    private var carouselControls: some View {
        if mediaItems.count > 1 {
            ZStack {
                HStack {
                    mediaPageDots
                    Spacer(minLength: 0)
                }
                .padding(.top, 10)
                .padding(.leading, 10)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)

                HStack {
                    carouselControlButton(
                        systemImage: "chevron.left",
                        accessibilityLabel: "Previous media",
                        isEnabled: selectedIndex > 0
                    ) {
                        selectMedia(at: selectedIndex - 1)
                    }

                    Spacer(minLength: 0)

                    carouselControlButton(
                        systemImage: "chevron.right",
                        accessibilityLabel: "Next media",
                        isEnabled: selectedIndex < mediaItems.count - 1
                    ) {
                        selectMedia(at: selectedIndex + 1)
                    }
                }
                .padding(.horizontal, 10)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    private var mediaPageDots: some View {
        HStack(spacing: 6) {
            ForEach(mediaItems.indices, id: \.self) { index in
                Circle()
                    .fill(index == selectedIndex ? Color.white : Color.white.opacity(0.48))
                    .frame(width: index == selectedIndex ? 8 : 6, height: index == selectedIndex ? 8 : 6)
                    .overlay(
                        Circle()
                            .stroke(Color.black.opacity(0.24), lineWidth: 0.5)
                    )
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 7)
        .background(Color.black.opacity(0.34))
        .clipShape(Capsule())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Media \(selectedIndex + 1) of \(mediaItems.count)")
    }

    private var carouselDragGesture: some Gesture {
        DragGesture(minimumDistance: 12, coordinateSpace: .local)
            .updating($carouselDragOffset) { value, state, _ in
                guard mediaItems.count > 1 else { return }
                guard abs(value.translation.width) > abs(value.translation.height) else { return }
                state = boundedCarouselDragOffset(value.translation.width)
            }
            .onEnded { value in
                guard mediaItems.count > 1 else { return }
                guard abs(value.translation.width) > abs(value.translation.height),
                      abs(value.translation.width) > 42 || abs(value.predictedEndTranslation.width) > 72 else { return }
                let horizontalTravel = abs(value.predictedEndTranslation.width) > abs(value.translation.width)
                    ? value.predictedEndTranslation.width
                    : value.translation.width
                if horizontalTravel < 0 {
                    selectMedia(at: selectedIndex + 1)
                } else {
                    selectMedia(at: selectedIndex - 1)
                }
            }
    }

    private func carouselControlButton(
        systemImage: String,
        accessibilityLabel: String,
        isEnabled: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.system(size: 17, weight: .bold))
                .foregroundStyle(.white)
                .frame(width: 36, height: 36)
                .background(Color.black.opacity(0.46))
                .clipShape(Circle())
        }
        .buttonStyle(.plain)
        .disabled(!isEnabled)
        .opacity(isEnabled ? 1 : 0.5)
        .accessibilityLabel(accessibilityLabel)
    }

    private func boundedCarouselDragOffset(_ translation: CGFloat) -> CGFloat {
        let draggingPastFirst = selectedIndex == 0 && translation > 0
        let draggingPastLast = selectedIndex == mediaItems.count - 1 && translation < 0
        return draggingPastFirst || draggingPastLast ? translation * 0.28 : translation
    }

    private func selectMedia(at index: Int) {
        guard !mediaItems.isEmpty else { return }
        let clampedIndex = min(max(index, 0), mediaItems.count - 1)
        guard selectedIndex != clampedIndex else { return }
        withAnimation(.easeInOut(duration: 0.18)) {
            selectedMediaURL = mediaItems[clampedIndex].fileURL
        }
    }

    @ViewBuilder
    private func mediaPreviewPage(media: ComposerState.SubmissionMedia, isPrimary: Bool) -> some View {
        if media.isVideo {
            Button {
                videoPlayerMedia = media
            } label: {
                videoPreview(media: media, isPrimary: isPrimary)
            }
            .buttonStyle(.plain)
        } else if let image = UIImage(contentsOfFile: media.fileURL.path) {
            ZStack {
                PlateAwareImage(
                    image: image,
                    focalPoint: isPrimary ? stillImageFocalPoint : nil
                )
                .contentShape(Rectangle())
                .onTapGesture {
                    imageViewerMedia = media
                }
                if isPrimary {
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
            }
            .frame(maxWidth: .infinity)
            .frame(height: previewHeight)
        } else {
            missingMediaPreview(media: media)
        }
    }

    @ViewBuilder
    private func videoPreview(media: ComposerState.SubmissionMedia, isPrimary: Bool) -> some View {
        ZStack(alignment: .bottomLeading) {
            if isPrimary, let preview = selectedCandidate?.videoFramePreview {
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
                .frame(height: previewHeight)
            } else {
                missingMediaPreview(media: media, systemImage: "video")
            }
            if isPrimary, let seconds = selectedCandidate?.videoFrameTimeSeconds {
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
    }

    private func missingMediaPreview(media: ComposerState.SubmissionMedia, systemImage: String = "photo") -> some View {
        RoundedRectangle(cornerRadius: 12)
            .fill(Color(.secondarySystemBackground))
            .frame(height: previewHeight)
            .overlay(
                VStack(spacing: 10) {
                    Image(systemName: systemImage)
                        .font(.system(size: 36))
                        .foregroundStyle(Color.reportedOrange)
                    Text(media.displayName)
                        .foregroundStyle(.primary)
                        .lineLimit(1)
                }
                .padding(.horizontal, 16)
            )
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
                    if let overlay = candidate.overlayShape(in: layout) {
                        overlay.path
                            .stroke(candidate.plate == selectedPlate ? Color.green : Color.reportedOrange, lineWidth: 3)
                        if let onCandidateTapped {
                            Rectangle()
                                .fill(Color.black.opacity(0.001))
                                .frame(width: max(44, overlay.bounds.width + 20), height: max(44, overlay.bounds.height + 20))
                                .position(x: overlay.bounds.midX, y: overlay.bounds.midY)
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
            .presentationDetents([.height(plateCandidateSheetDetentHeight)])
            .presentationDragIndicator(.hidden)
            .presentationBackground(Color(.systemBackground))
        }
    }
}

private var plateCandidateSheetDetentHeight: CGFloat {
    UIDevice.current.userInterfaceIdiom == .pad ? 470 : 320
}

@MainActor
private func complaintChooserSheetDetentHeight(optionCount: Int) -> CGFloat {
    let screenBounds = UIScreen.main.bounds
    let shortSide = min(screenBounds.width, screenBounds.height)
    let longSide = max(screenBounds.width, screenBounds.height)
    let isTabletLayout = UIDevice.current.userInterfaceIdiom == .pad || shortSide >= 700
    let columnCount = shortSide >= 700 ? 3 : 2
    let rowCount = max(1, Int(ceil(Double(max(optionCount, 1)) / Double(columnCount))))
    let tileHeight: CGFloat = isTabletLayout ? 218 : 150
    let gridSpacing: CGFloat = isTabletLayout ? 16 : 12
    let titleHeight: CGFloat = isTabletLayout ? 44 : 32
    let titleGridSpacing: CGFloat = isTabletLayout ? 20 : 16
    let outerVerticalPadding: CGFloat = 24
    let gridHeight = CGFloat(rowCount) * tileHeight + CGFloat(max(rowCount - 1, 0)) * gridSpacing
    let contentHeight = titleHeight + titleGridSpacing + gridHeight + outerVerticalPadding
    let bottomSafeArea = currentBottomSafeAreaInset(fallbackLongSide: longSide)
    let requestedHeight = contentHeight - bottomSafeArea
    let maximumHeight = (longSide * (isTabletLayout ? 0.72 : 0.86)) - bottomSafeArea
    return min(max(requestedHeight, 220), maximumHeight)
}

@MainActor
private func currentBottomSafeAreaInset(fallbackLongSide: CGFloat) -> CGFloat {
    let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
    let foregroundScene = scenes.first { $0.activationState == .foregroundActive }
        ?? scenes.first { $0.activationState == .foregroundInactive }
        ?? scenes.first
    if let bottomInset = foregroundScene?.windows.first(where: \.isKeyWindow)?.safeAreaInsets.bottom {
        return bottomInset
    }
    return fallbackLongSide >= 780 ? 34 : 0
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
                ForEach(candidate.ocrSourceTexts, id: \.self) { sourceText in
                    Text(sourceText)
                        .font(.caption)
                        .foregroundStyle(isSelected ? .white.opacity(0.85) : .secondary)
                }
                Text(candidate.detectorConfidenceText)
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
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

    var body: some View {
        VStack(alignment: .leading, spacing: isTabletLayout ? 24 : 18) {
            HStack {
                Text("Use this plate?")
                    .font(isTabletLayout ? .title2.weight(.semibold) : .title3.weight(.semibold))
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

            HStack(alignment: .center, spacing: isTabletLayout ? 22 : 14) {
                PlateCandidateCropPreview(
                    candidate: candidate,
                    sourceImage: sourceImage,
                    size: isTabletLayout ? CGSize(width: 224, height: 116) : CGSize(width: 112, height: 58),
                    cornerRadius: isTabletLayout ? 10 : 6
                )
                VStack(alignment: .leading, spacing: 4) {
                    Text(candidate.plate)
                        .font(isTabletLayout ? .largeTitle.weight(.semibold) : .title2.weight(.semibold))
                    if let correctionText = candidate.plateCorrectionText {
                        Text(correctionText)
                            .font(isTabletLayout ? .callout : .caption)
                            .foregroundStyle(Color.reportedOrange)
                    }
                    ForEach(candidate.ocrSourceTexts, id: \.self) { sourceText in
                        Text(sourceText)
                            .font(isTabletLayout ? .callout : .caption)
                            .foregroundStyle(.secondary)
                    }
                    Text(candidate.detectorConfidenceText)
                        .font(isTabletLayout ? .body : .subheadline)
                        .foregroundStyle(.secondary)
                    if let classifierText = candidate.stateClassifierText {
                        Text(classifierText)
                            .font(isTabletLayout ? .callout : .caption)
                            .foregroundStyle(.secondary)
                    }
                    if let plateTypeText = candidate.plateTypeText {
                        Text(plateTypeText)
                            .font(isTabletLayout ? .callout : .caption)
                            .foregroundStyle(.secondary)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }

            HStack(spacing: 12) {
                SecondaryButton(title: "No", action: onDismiss)
                PrimaryButton(title: "Yes", action: onUse)
            }
        }
        .padding(isTabletLayout ? 28 : 20)
        .presentationCornerRadius(24)
        .background(Color(.systemBackground))
    }

    private var isTabletLayout: Bool {
        UIDevice.current.userInterfaceIdiom == .pad || horizontalSizeClass == .regular
    }
}

private struct PlateCandidatePickerSheet: View {
    let candidates: [ComposerState.PlateCandidate]
    let selectedPlate: String?
    let onSelected: (ComposerState.PlateCandidate) -> Void
    let onDismiss: () -> Void
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: isTabletLayout ? 14 : 10) {
                    ForEach(candidates) { candidate in
                        PlateCandidatePickerRow(
                            candidate: candidate,
                            isSelected: candidate.plate == selectedPlate
                        ) {
                            onSelected(candidate)
                        }
                    }
                }
                .padding(.horizontal, isTabletLayout ? 24 : 16)
                .padding(.vertical, isTabletLayout ? 18 : 12)
            }
            .background(Color(.systemBackground))
            .navigationTitle("Possible plates")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done", action: onDismiss)
                        .foregroundStyle(Color.reportedOrange)
                }
            }
        }
        .background(Color(.systemBackground))
    }

    private var isTabletLayout: Bool {
        UIDevice.current.userInterfaceIdiom == .pad || horizontalSizeClass == .regular
    }
}

private struct PlateCandidatePickerRow: View {
    let candidate: ComposerState.PlateCandidate
    var sourceImage: UIImage? = nil
    let isSelected: Bool
    let action: () -> Void
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

    var body: some View {
        Button(action: action) {
            HStack(spacing: isTabletLayout ? 16 : 12) {
                PlateCandidateCropPreview(
                    candidate: candidate,
                    sourceImage: sourceImage,
                    size: isTabletLayout ? CGSize(width: 160, height: 82) : CGSize(width: 96, height: 48),
                    cornerRadius: isTabletLayout ? 8 : 6
                )
                VStack(alignment: .leading, spacing: 4) {
                    Text(candidate.plate)
                        .font(isTabletLayout ? .title2.weight(.semibold) : .title3.weight(.semibold))
                        .foregroundStyle(.primary)
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)
                    if let correctionText = candidate.plateCorrectionText {
                        Text(correctionText)
                            .font(isTabletLayout ? .callout : .caption)
                            .foregroundStyle(Color.reportedOrange)
                    }
                    ForEach(candidate.ocrSourceTexts, id: \.self) { sourceText in
                        Text(sourceText)
                            .font(isTabletLayout ? .callout : .caption)
                            .foregroundStyle(.secondary)
                    }
                    Text(candidate.detectorConfidenceText)
                        .font(isTabletLayout ? .callout : .caption)
                        .foregroundStyle(.secondary)
                    if let stateText = candidate.stateClassifierText {
                        Text(stateText)
                            .font(isTabletLayout ? .callout : .caption)
                            .foregroundStyle(.secondary)
                    }
                    if let typeText = candidate.plateTypeText {
                        Text(typeText)
                            .font(isTabletLayout ? .callout : .caption)
                            .foregroundStyle(.secondary)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                if isSelected {
                    Text("Selected")
                        .font(isTabletLayout ? .callout.weight(.semibold) : .caption.weight(.semibold))
                        .foregroundStyle(Color.green)
                }
            }
            .padding(isTabletLayout ? 14 : 10)
            .background(Color(.systemBackground))
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(isSelected ? Color.green : Color(.separator), lineWidth: isSelected ? 2 : 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
    }

    private var isTabletLayout: Bool {
        UIDevice.current.userInterfaceIdiom == .pad || horizontalSizeClass == .regular
    }
}

private struct PlateCandidateCropPreview: View {
    let candidate: ComposerState.PlateCandidate
    var sourceImage: UIImage? = nil
    var size = CGSize(width: 96, height: 48)
    var cornerRadius: CGFloat = 6

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: cornerRadius)
                .fill(Color(.secondarySystemBackground))
            if let crop = candidate.plateCropPreview ?? sourceImage?.croppedPlatePreview(for: candidate) {
                Image(uiImage: crop)
                    .resizable()
                    .scaledToFill()
            } else if let frame = candidate.videoFramePreview {
                Image(uiImage: frame)
                    .resizable()
                    .scaledToFill()
            } else {
                Text("No image")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
        .frame(width: size.width, height: size.height)
        .clipShape(RoundedRectangle(cornerRadius: cornerRadius))
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

func extractSubmissionMetadata(from media: ComposerState.SubmissionMedia) async -> ExtractedSubmissionMetadata {
    if media.isVideo {
        let asset = AVURLAsset(url: media.fileURL)
        let creationDateMetadata = try? await asset.load(.creationDate)
        let creationDate: Date?
        if let creationDateMetadata {
            creationDate = try? await creationDateMetadata.load(.dateValue)
        } else {
            creationDate = nil
        }
        let metadataItems = (try? await asset.load(.metadata)) ?? []
        let locationItem = AVMetadataItem
            .metadataItems(from: metadataItems, filteredByIdentifier: .commonIdentifierLocation)
            .first
        let rawLocation: String?
        if let locationItem {
            rawLocation = try? await locationItem.load(.stringValue)
        } else {
            rawLocation = nil
        }
        let coordinate = parseIso6709Location(rawLocation)
        return ExtractedSubmissionMetadata(
            occurredAtIso: creationDate?.ISO8601Format(),
            latitude: coordinate?.latitude,
            longitude: coordinate?.longitude
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

private func parseIso6709Location(_ raw: String?) -> (latitude: Double, longitude: Double)? {
    guard let raw, !raw.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }
    let pattern = #"([+-]\d+(?:\.\d+)?)([+-]\d+(?:\.\d+)?)"#
    guard let regex = try? NSRegularExpression(pattern: pattern) else { return nil }
    let nsRange = NSRange(raw.startIndex..<raw.endIndex, in: raw)
    guard let match = regex.firstMatch(in: raw, range: nsRange), match.numberOfRanges >= 3 else { return nil }

    func group(_ index: Int) -> String? {
        let range = match.range(at: index)
        guard let swiftRange = Range(range, in: raw) else { return nil }
        return String(raw[swiftRange])
    }

    guard let latitude = group(1).flatMap(Double.init),
          let longitude = group(2).flatMap(Double.init),
          isValidReportCoordinate(latitude: latitude, longitude: longitude) else {
        return nil
    }
    return (latitude, longitude)
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

func reportAddressProvider(latitude: Double?, longitude: Double?, address: String) -> ReportAddressProvider {
    if let latitude, let longitude, isValidReportCoordinate(latitude: latitude, longitude: longitude) {
        if CityBoundaryIndex.shared.contains(cityId: "philadelphia", latitude: latitude, longitude: longitude) {
            return .philadelphia
        }
        if CityBoundaryIndex.shared.contains(cityId: "nyc", latitude: latitude, longitude: longitude) {
            return .newYorkCity
        }
        return .newYorkCity
    }
    return isPhiladelphiaAddressText(address) ? .philadelphia : .newYorkCity
}

private func isValidReportCoordinate(latitude: Double, longitude: Double) -> Bool {
    latitude.isFinite &&
        longitude.isFinite &&
        abs(latitude) <= 90 &&
        abs(longitude) <= 180 &&
        (abs(latitude) > 0.000001 || abs(longitude) > 0.000001)
}

private func isPhiladelphiaAddressText(_ address: String) -> Bool {
    let value = address.lowercased()
    if value.contains("philadelphia") || value.contains("phila") || value.contains("philly") {
        return true
    }
    return philadelphiaZipCodes.contains { value.contains($0) }
}

private let philadelphiaZipCodes: Set<String> = [
    "19102", "19103", "19104", "19106", "19107", "19111", "19112", "19113",
    "19114", "19115", "19116", "19118", "19119", "19120", "19121", "19122",
    "19123", "19124", "19125", "19126", "19127", "19128", "19129", "19130",
    "19131", "19132", "19133", "19134", "19135", "19136", "19137", "19138",
    "19139", "19140", "19141", "19142", "19143", "19144", "19145", "19146",
    "19147", "19148", "19149", "19150", "19151", "19152", "19153", "19154"
]

private func reverseGeocodeAddress(latitude: Double, longitude: Double) async -> ComposerState.AddressSuggestion? {
    let provider = reportAddressProvider(latitude: latitude, longitude: longitude, address: "")
    switch provider {
    case .philadelphia:
        if let suggestion = await reverseGeocodePhiladelphiaAis(latitude: latitude, longitude: longitude) {
            return suggestion
        }
        if let suggestion = await reverseGeocodeNyc(latitude: latitude, longitude: longitude) {
            return suggestion
        }
        return await reverseGeocodePlatform(latitude: latitude, longitude: longitude)
    case .newYorkCity:
        if let suggestion = await reverseGeocodeNyc(latitude: latitude, longitude: longitude) {
            return suggestion
        }
        return await reverseGeocodePlatform(latitude: latitude, longitude: longitude)
    }
}

private func searchReportAddresses(
    query: String,
    latitude: Double?,
    longitude: Double?,
    address: String
) async -> [ComposerState.AddressSuggestion] {
    switch reportAddressProvider(latitude: latitude, longitude: longitude, address: address.isEmpty ? query : address) {
    case .philadelphia:
        let suggestions = await searchPhiladelphiaAisAddresses(query: query)
        return suggestions.isEmpty ? await searchNycAddresses(query: query) : suggestions
    case .newYorkCity:
        return await searchNycAddresses(query: query)
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
    return ComposerState.AddressSuggestion(
        label: label,
        latitude: latitude,
        longitude: longitude,
        region: "NY",
        providerId: ReportAddressProvider.newYorkCity.id
    )
}

private func addressSuggestionFromImageMetadata(_ metadata: ExtractedSubmissionMetadata) async -> ComposerState.AddressSuggestion? {
    guard let latitude = metadata.latitude, let longitude = metadata.longitude else { return nil }
    guard latitude.isFinite, longitude.isFinite else { return nil }
    guard abs(latitude) <= 90, abs(longitude) <= 180 else { return nil }
    guard abs(latitude) > 0.000001 || abs(longitude) > 0.000001 else { return nil }
    let coordinate = CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
    guard CLLocationCoordinate2DIsValid(coordinate) else { return nil }
    return await reverseGeocodeAddress(latitude: latitude, longitude: longitude)
        ?? coordinateAddressSuggestion(latitude: latitude, longitude: longitude)
}

private func coordinateAddressSuggestion(latitude: Double, longitude: Double) -> ComposerState.AddressSuggestion {
    ComposerState.AddressSuggestion(
        label: formattedCoordinateText(latitude: latitude, longitude: longitude),
        latitude: latitude,
        longitude: longitude
    )
}

private func formattedCoordinateText(latitude: Double, longitude: Double) -> String {
    String(format: "%.5f, %.5f", latitude, longitude)
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
            longitude: coordinates[0],
            region: "NY",
            providerId: ReportAddressProvider.newYorkCity.id
        )
    }
}

private func searchPhiladelphiaAisAddresses(query: String) async -> [ComposerState.AddressSuggestion] {
    let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !trimmed.isEmpty,
          let encoded = trimmed.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed),
          let json = await fetchPhiladelphiaAisJson("https://api.phila.gov/ais/v1/search/\(encoded)"),
          let features = json["features"] as? [[String: Any]] else {
        return []
    }
    return features.compactMap { philadelphiaAisFeatureToSuggestion($0) }
}

private func reverseGeocodePhiladelphiaAis(latitude: Double, longitude: Double) async -> ComposerState.AddressSuggestion? {
    guard let json = await fetchPhiladelphiaAisJson(
        "https://api.phila.gov/ais/v1/reverse_geocode/\(longitude),\(latitude)?srid=4326&search_radius=500"
    ),
          let features = json["features"] as? [[String: Any]],
          let first = features.first else {
        return nil
    }
    return philadelphiaAisFeatureToSuggestion(first, fallbackLatitude: latitude, fallbackLongitude: longitude)
}

private func fetchPhiladelphiaAisJson(_ urlString: String) async -> [String: Any]? {
    guard let url = URL(string: urlString) else { return nil }
    var request = URLRequest(url: url)
    request.timeoutInterval = 5
    if let key = philadelphiaAisGatekeeperKey() {
        request.setValue("Gatekeeper-Key \(key)", forHTTPHeaderField: "Authorization")
    }
    guard let (data, response) = try? await URLSession.shared.data(for: request),
          let httpResponse = response as? HTTPURLResponse,
          (200...299).contains(httpResponse.statusCode) else {
        return nil
    }
    return try? JSONSerialization.jsonObject(with: data) as? [String: Any]
}

private func philadelphiaAisGatekeeperKey() -> String? {
    let environmentValue = ProcessInfo.processInfo.environment["REPORTED_PHILADELPHIA_AIS_GATEKEEPER_KEY"]
    let plistValue = Bundle.main.object(forInfoDictionaryKey: "REPORTED_PHILADELPHIA_AIS_GATEKEEPER_KEY") as? String
    return (environmentValue ?? plistValue)?.nilIfBlank
}

private func philadelphiaAisFeatureToSuggestion(
    _ feature: [String: Any],
    fallbackLatitude: Double? = nil,
    fallbackLongitude: Double? = nil
) -> ComposerState.AddressSuggestion? {
    guard let properties = feature["properties"] as? [String: Any] else { return nil }
    let coordinates = (feature["geometry"] as? [String: Any])?["coordinates"] as? [Any]
    let longitude = doubleValue(coordinates?.first) ?? fallbackLongitude
    let latitude = doubleValue((coordinates?.count ?? 0) > 1 ? coordinates?[1] : nil) ?? fallbackLatitude
    guard let latitude, let longitude else { return nil }
    let streetAddress = stringValue(properties["street_address"])
        ?? stringValue(properties["address"])
        ?? stringValue(properties["opa_address"])
    let zipCode = stringValue(properties["zip_code"]) ?? stringValue(properties["zip"])
    var label = streetAddress ?? "Philadelphia address"
    if !label.localizedCaseInsensitiveContains("Philadelphia") {
        label += ", Philadelphia"
    }
    if !label.localizedCaseInsensitiveContains(", PA") {
        label += ", PA"
    }
    if let zipCode, !label.contains(zipCode) {
        label += " \(zipCode)"
    }
    return ComposerState.AddressSuggestion(
        label: label,
        latitude: latitude,
        longitude: longitude,
        region: "PA",
        providerId: ReportAddressProvider.philadelphia.id,
        blockNumber: stringValue(properties["address_low"]) ?? streetAddress?.firstStreetNumber,
        streetName: stringValue(properties["street_full"]),
        zipCode: zipCode
    )
}

private func reverseGeocodePlatform(latitude: Double, longitude: Double) async -> ComposerState.AddressSuggestion? {
    let location = CLLocation(latitude: latitude, longitude: longitude)
    guard let placemark = try? await CLGeocoder().reverseGeocodeLocation(location).first else {
        return nil
    }
    let label = [
        placemark.name,
        placemark.locality,
        placemark.administrativeArea,
        placemark.postalCode
    ]
        .compactMap { $0?.nilIfBlank }
        .joined(separator: ", ")
    guard !label.isEmpty else { return nil }
    return ComposerState.AddressSuggestion(
        label: label,
        latitude: latitude,
        longitude: longitude,
        region: placemark.administrativeArea?.uppercased()
    )
}

private final class CityBoundaryIndex {
    static let shared = CityBoundaryIndex()

    private let lock = NSLock()
    private var cache: [String: CityBoundary] = [:]
    private var missing: Set<String> = []

    func contains(cityId: String, latitude: Double, longitude: Double) -> Bool {
        guard isValidReportCoordinate(latitude: latitude, longitude: longitude),
              let boundary = boundary(for: cityId) else {
            return false
        }
        return boundary.contains(longitude: longitude, latitude: latitude)
    }

    private func boundary(for cityId: String) -> CityBoundary? {
        lock.lock()
        if let cached = cache[cityId] {
            lock.unlock()
            return cached
        }
        if missing.contains(cityId) {
            lock.unlock()
            return nil
        }
        lock.unlock()

        let loaded = load(cityId: cityId)

        lock.lock()
        if let loaded {
            cache[cityId] = loaded
        } else {
            missing.insert(cityId)
        }
        lock.unlock()
        return loaded
    }

    private func load(cityId: String) -> CityBoundary? {
        let fileName: String
        switch cityId {
        case "philadelphia":
            fileName = "philadelphia"
        case "nyc":
            fileName = "nyc"
        default:
            return nil
        }
        let url = Bundle.main.url(forResource: fileName, withExtension: "geojson", subdirectory: "CityBoundaries")
            ?? Bundle.main.url(forResource: fileName, withExtension: "geojson")
        guard let url,
              let data = try? Data(contentsOf: url),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let features = json["features"] as? [[String: Any]] else {
            return nil
        }
        let bbox = ((json["properties"] as? [String: Any])?["bbox"] as? [Any])?
            .compactMap(doubleValue)
        let polygons = features.flatMap { feature -> [[[BoundaryPoint]]] in
            guard let geometry = feature["geometry"] as? [String: Any] else { return [] }
            return parseBoundaryGeometry(geometry)
        }
        return CityBoundary(bbox: bbox?.count == 4 ? bbox : nil, polygons: polygons)
    }
}

private struct CityBoundary {
    let bbox: [Double]?
    let polygons: [[[BoundaryPoint]]]

    func contains(longitude: Double, latitude: Double) -> Bool {
        if let bbox,
           longitude < bbox[0] || latitude < bbox[1] || longitude > bbox[2] || latitude > bbox[3] {
            return false
        }
        return polygons.contains { polygonContains($0, longitude: longitude, latitude: latitude) }
    }
}

private struct BoundaryPoint {
    let longitude: Double
    let latitude: Double
}

private func parseBoundaryGeometry(_ geometry: [String: Any]) -> [[[BoundaryPoint]]] {
    guard let type = geometry["type"] as? String else { return [] }
    let coordinates = geometry["coordinates"]
    switch type {
    case "Polygon":
        return parseBoundaryPolygon(coordinates).map { [$0] } ?? []
    case "MultiPolygon":
        guard let polygons = coordinates as? [Any] else { return [] }
        return polygons.compactMap(parseBoundaryPolygon)
    default:
        return []
    }
}

private func parseBoundaryPolygon(_ value: Any?) -> [[BoundaryPoint]]? {
    guard let rings = value as? [Any] else { return nil }
    let parsed = rings.compactMap { ringValue -> [BoundaryPoint]? in
        guard let points = ringValue as? [Any] else { return nil }
        let ring = points.compactMap { pointValue -> BoundaryPoint? in
            guard let point = pointValue as? [Any],
                  point.count >= 2,
                  let longitude = doubleValue(point[0]),
                  let latitude = doubleValue(point[1]) else {
                return nil
            }
            return BoundaryPoint(longitude: longitude, latitude: latitude)
        }
        return ring.isEmpty ? nil : ring
    }
    return parsed.isEmpty ? nil : parsed
}

private func polygonContains(_ polygon: [[BoundaryPoint]], longitude: Double, latitude: Double) -> Bool {
    guard let outer = polygon.first, ringContains(outer, longitude: longitude, latitude: latitude) else {
        return false
    }
    return polygon.dropFirst().allSatisfy { !ringContains($0, longitude: longitude, latitude: latitude) }
}

private func ringContains(_ ring: [BoundaryPoint], longitude: Double, latitude: Double) -> Bool {
    guard ring.count >= 3 else { return false }
    var inside = false
    var previous = ring[ring.count - 1]
    for current in ring {
        let intersects = (current.latitude > latitude) != (previous.latitude > latitude) &&
            longitude < (previous.longitude - current.longitude) *
            (latitude - current.latitude) /
            (previous.latitude - current.latitude) +
            current.longitude
        if intersects {
            inside.toggle()
        }
        previous = current
    }
    return inside
}

private func doubleValue(_ value: Any?) -> Double? {
    if let value = value as? Double { return value }
    if let value = value as? NSNumber { return value.doubleValue }
    if let value = value as? String { return Double(value) }
    return nil
}

private func stringValue(_ value: Any?) -> String? {
    if let value = value as? String {
        return value.nilIfBlank
    }
    if let value = value as? NSNumber {
        return value.stringValue.nilIfBlank
    }
    return nil
}

private extension String {
    var firstStreetNumber: String? {
        let pattern = #"^\s*(\d+[A-Za-z]?)"#
        guard let range = range(of: pattern, options: .regularExpression) else { return nil }
        return String(self[range]).trimmingCharacters(in: .whitespacesAndNewlines)
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
                    viewModel.onAction(.reportDeleted(report))
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
        viewModel.onAction(.reportOpened(objectId: objectId))
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
                            viewModel.onAction(.detailRequested(report))
                        }
                    },
                    onDelete: {
                        pendingDeleteReport = report
                    }
                )
                .onAppear {
                    viewModel.onAction(.nextPageRequested(current: report))
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
                SecondaryButton(title: "Search") { viewModel.onAction(.searchChosen) }
                PrimaryButton(title: "List") { viewModel.onAction(.listChosen) }
            }
        }
    }
}

private struct ReportsSearchSection: View {
    @ObservedObject var viewModel: ReportsViewModel

    var body: some View {
        Section("Search Filters") {
            TextField(
                "License plate",
                text: Binding(
                    get: { viewModel.licenseQuery },
                    set: { viewModel.onAction(.licenseChanged($0)) }
                )
            )
            .textInputAutocapitalization(.characters)
            .autocorrectionDisabled(true)
            .textFieldStyle(.plain)
            .submitLabel(.done)
            .onSubmit { dismissActiveKeyboard() }
            .frame(height: 24)
            Toggle("Use start date", isOn: Binding(
                get: { viewModel.usesStartDate },
                set: { viewModel.onAction(.usesStartDateChanged($0)) }
            ))
            if viewModel.usesStartDate {
                DatePicker(
                    "Start Date",
                    selection: Binding(
                        get: { viewModel.startDate },
                        set: { viewModel.onAction(.startDateChanged($0)) }
                    ),
                    in: ...Date(),
                    displayedComponents: [.date, .hourAndMinute]
                )
            }
            Toggle("Use end date", isOn: Binding(
                get: { viewModel.usesEndDate },
                set: { viewModel.onAction(.usesEndDateChanged($0)) }
            ))
            if viewModel.usesEndDate {
                DatePicker(
                    "End Date",
                    selection: Binding(
                        get: { viewModel.endDate },
                        set: { viewModel.onAction(.endDateChanged($0)) }
                    ),
                    in: ...Date(),
                    displayedComponents: [.date, .hourAndMinute]
                )
            }
            PrimaryButton(title: "Search reports") { viewModel.onAction(.searchPressed) }
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

private struct RemoteReportImageSelection: Identifiable {
    let url: URL
    var id: String { url.absoluteString }
}

private struct ReportMediaStrip: View {
    let mediaUrls: [String]
    let videoUrls: [String]
    @State private var selectedImage: RemoteReportImageSelection?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if !mediaUrls.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 10) {
                        ForEach(mediaUrls, id: \.self) { url in
                            if let imageURL = URL(string: url) {
                                Button {
                                    selectedImage = RemoteReportImageSelection(url: imageURL)
                                } label: {
                                    AsyncImage(url: imageURL) { image in
                                        image.resizable().scaledToFill()
                                    } placeholder: {
                                        Color(.secondarySystemBackground)
                                    }
                                    .frame(width: 112, height: 112)
                                    .clipShape(RoundedRectangle(cornerRadius: 8))
                                    .contentShape(RoundedRectangle(cornerRadius: 8))
                                }
                                .buttonStyle(.plain)
                                .accessibilityLabel("Open report image")
                            }
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
        .fullScreenCover(item: $selectedImage) { selection in
            RemoteReportImageViewer(url: selection.url) {
                selectedImage = nil
            }
        }
    }
}

private struct RemoteReportImageViewer: View {
    let url: URL
    let onDismiss: () -> Void
    @State private var scale: CGFloat = 1
    @State private var lastScale: CGFloat = 1
    @State private var offset: CGSize = .zero
    @State private var lastOffset: CGSize = .zero

    var body: some View {
        GeometryReader { proxy in
            ZStack(alignment: .topTrailing) {
                Color.black.ignoresSafeArea()
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .empty:
                        ProgressView()
                            .tint(.white)
                    case .success(let image):
                        image
                            .resizable()
                            .scaledToFit()
                    case .failure:
                        VStack(spacing: 10) {
                            Image(systemName: "photo")
                                .font(.system(size: 36, weight: .medium))
                            Text("Image unavailable")
                                .font(.headline)
                        }
                        .foregroundStyle(.white)
                    @unknown default:
                        EmptyView()
                    }
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
    var showsLogoutInToolbar = false
    @StateObject private var viewModel = ProfileViewModel()
    let onLogout: () -> Void

    var body: some View {
        GeometryReader { geometry in
            let isLandscape = currentInterfaceIsLandscape(fallbackSize: geometry.size)
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
                                set: { viewModel.onAction(.fieldsChanged(testify: $0)) }
                            ))
                            .disabled(!viewModel.state.editing)
                            if viewModel.state.editing {
                                PrimaryButton(title: viewModel.state.loading ? "Saving..." : "Save") { viewModel.onAction(.save) }
                            } else {
                                PrimaryButton(title: "Edit Profile") { viewModel.onAction(.toggleEditing) }
                            }
                            if !showsLogoutInToolbar {
                                Button("Logout", action: onLogout)
                                    .foregroundStyle(Color.reportedOrange)
                            }
                        }
                        .padding(isLandscape ? 12 : 16)
                    }
                }
            }
            .background(Color(.systemBackground))
        }
        .task {
            if isAuthorized {
                viewModel.onAction(.load)
            }
        }
    }

    @ViewBuilder
    private func profileFields(isLandscape: Bool) -> some View {
        if isLandscape {
            HStack(alignment: .top, spacing: 12) {
                InputField(title: "First Name", text: Binding(get: { viewModel.state.firstName }, set: { viewModel.onAction(.fieldsChanged(firstName: $0)) }), disabled: !viewModel.state.editing)
                InputField(title: "Last Name", text: Binding(get: { viewModel.state.lastName }, set: { viewModel.onAction(.fieldsChanged(lastName: $0)) }), disabled: !viewModel.state.editing)
            }
            HStack(alignment: .top, spacing: 12) {
                InputField(title: "Phone", text: Binding(get: { viewModel.state.phone }, set: { viewModel.onAction(.fieldsChanged(phone: $0)) }), disabled: !viewModel.state.editing)
                InputField(title: "Email", text: Binding(get: { viewModel.state.email }, set: { viewModel.onAction(.fieldsChanged(email: $0)) }), disabled: !viewModel.state.editing, keyboardType: .emailAddress, textContentType: .emailAddress, autocapitalizationType: .none, autocorrectionDisabled: true)
            }
        } else {
            InputField(title: "First Name", text: Binding(get: { viewModel.state.firstName }, set: { viewModel.onAction(.fieldsChanged(firstName: $0)) }), disabled: !viewModel.state.editing)
            InputField(title: "Last Name", text: Binding(get: { viewModel.state.lastName }, set: { viewModel.onAction(.fieldsChanged(lastName: $0)) }), disabled: !viewModel.state.editing)
            InputField(title: "Phone", text: Binding(get: { viewModel.state.phone }, set: { viewModel.onAction(.fieldsChanged(phone: $0)) }), disabled: !viewModel.state.editing)
            InputField(title: "Email", text: Binding(get: { viewModel.state.email }, set: { viewModel.onAction(.fieldsChanged(email: $0)) }), disabled: !viewModel.state.editing, keyboardType: .emailAddress, textContentType: .emailAddress, autocapitalizationType: .none, autocorrectionDisabled: true)
        }
    }
}

private struct AutoReportIncident: Identifiable {
    let id: String
    var plate: String
    var plateRegion: String
    var complaintId: String
    var occurredAtIso: String
    var address: String
    var description = ""
    var notes = ""
    var latitude: Double?
    var longitude: Double?
    let infractions: [IOSDetectedInfraction]
    var selected = true
    var good = true

    var complaintTitle: String {
        autoReportComplaintTitle(for: complaintId)
    }

    var previewURL: URL? {
        infractions.first?.mediaURL
    }

    var media: [ComposerState.SubmissionMedia] {
        infractions.map(\.media)
    }

    var aiSummary: String {
        let photoText = "\(infractions.count) photo\(infractions.count == 1 ? "" : "s")"
        let plateScore = infractions.map(\.plateConfidence).max().map(autoReportPercent) ?? "unknown"
        let stateScore = infractions.map(\.stateConfidence).max().map(autoReportPercent) ?? "unknown"
        let complaintScore = infractions.compactMap(\.complaintConfidence).max().map(autoReportPercent) ?? "unknown"
        return "Reported AI grouped \(photoText) that appear to show \(complaintTitle.lowercased()) involving plate \(plate) in \(plateRegion). Plate confidence \(plateScore), state confidence \(stateScore), infraction confidence \(complaintScore)."
    }

    var validationErrors: ComposerState.ValidationErrors {
        ComposerState.ValidationErrors(
            media: media.isEmpty ? "Add at least one photo or video." : nil,
            complaint: complaintId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Choose a complaint type." : nil,
            plate: {
                let trimmed = plate.trimmingCharacters(in: .whitespacesAndNewlines)
                if trimmed.isEmpty {
                    return "Enter the license plate."
                }
                if trimmed.count > autoReportMaxLicensePlateLength {
                    return "License plate must be \(autoReportMaxLicensePlateLength) characters or fewer."
                }
                return nil
            }(),
            plateRegion: plateRegion.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Choose a state." : nil,
            address: address.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Enter or choose an address." : nil,
            occurredAt: occurredAtIso.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Choose when this happened." : nil
        )
    }

    var isSubmittable: Bool {
        !validationErrors.hasErrors
    }
}

private let autoReportMaxLicensePlateLength = 10

private struct AutoReportScanWindow: Identifiable, Hashable {
    let id: String
    let label: String
    let buttonLabel: String
    let sentenceLabel: String
    let lookback: TimeInterval

    static let oneHour = AutoReportScanWindow(
        id: "oneHour",
        label: "1 hr",
        buttonLabel: "Last 1 Hour",
        sentenceLabel: "the last hour",
        lookback: 60 * 60
    )
    static let sevenDays = AutoReportScanWindow(
        id: "sevenDays",
        label: "7 days",
        buttonLabel: "Last 7 Days",
        sentenceLabel: "the last 7 days",
        lookback: 7 * 24 * 60 * 60
    )
    static let options = [
        oneHour,
        AutoReportScanWindow(id: "eightHours", label: "8 hr", buttonLabel: "Last 8 Hours", sentenceLabel: "the last 8 hours", lookback: 8 * 60 * 60),
        AutoReportScanWindow(id: "oneDay", label: "1 day", buttonLabel: "Last 1 Day", sentenceLabel: "the last day", lookback: 24 * 60 * 60),
        AutoReportScanWindow(id: "threeDays", label: "3 days", buttonLabel: "Last 3 Days", sentenceLabel: "the last 3 days", lookback: 3 * 24 * 60 * 60),
        sevenDays,
        AutoReportScanWindow(id: "fourteenDays", label: "14 days", buttonLabel: "Last 14 Days", sentenceLabel: "the last 14 days", lookback: 14 * 24 * 60 * 60),
        AutoReportScanWindow(id: "thirtyOneDays", label: "31 days", buttonLabel: "Last 31 Days", sentenceLabel: "the last 31 days", lookback: 31 * 24 * 60 * 60)
    ]
}

struct AutoReportScreen: View {
    let isAuthorized: Bool
    let onRequireLogin: () -> Void
    let onReportSubmitted: (String) -> Void
    @State private var scanning = false
    @State private var submitting = false
    @State private var processed = 0
    @State private var total = 0
    @State private var incidents: [AutoReportIncident] = []
    @State private var processedPhotos: [IOSAutoReportProcessedPhoto] = []
    @State private var scanWindow = AutoReportScanWindow.sevenDays
    @State private var message: String?
    @State private var scanTask: Task<Void, Never>?
    @State private var showingReviewSheet = false
    @State private var scanStartedAt: Date?

    var body: some View {
        ScrollView {
            ScreenCard {
                VStack(alignment: .leading, spacing: 14) {
                    Text("Scan photos into a bulk review")
                        .font(.title3.weight(.semibold))
                    Text("Reported will scan \(scanWindow.sentenceLabel) of photos on this device and use on-device AI to determine if they are reported worthy. We look for vehicle photos where the infraction model shows a blocked bike lane or blocked crosswalk, then ask you before anything is submitted.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                    HStack(spacing: 10) {
                        Picker("Scan window", selection: $scanWindow) {
                            ForEach(AutoReportScanWindow.options) { option in
                                Text(option.label).tag(option)
                            }
                        }
                        .pickerStyle(.menu)
                        .tint(Color.reportedOrange)
                        .disabled(scanning || submitting)
                        .frame(minWidth: 92, alignment: .leading)

                        Button {
                            startScan()
                        } label: {
                            HStack {
                                if scanning {
                                    ProgressView()
                                        .tint(.white)
                                }
                                Text(scanning ? "Scanning..." : "Scan Photos")
                                    .font(.body.weight(.semibold))
                            }
                            .foregroundStyle(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(Color.reportedOrange)
                        .disabled(scanning || submitting)
                    }
                }

                AutoReportSummaryView(incidents: incidents)

                if let message {
                    MessageView(text: message)
                }

                if !incidents.isEmpty || !processedPhotos.isEmpty {
                    Button {
                        showingReviewSheet = true
                    } label: {
                        Text("Review Results")
                            .font(.body.weight(.semibold))
                            .foregroundStyle(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(Color.reportedOrange)
                    .disabled(scanning || submitting)
                }
            }
            .padding(16)
        }
        .background(Color(.systemBackground))
        .sheet(isPresented: $scanning) {
            AutoReportScanProgressSheet(
                processed: processed,
                total: total,
                onCancel: cancelScan
            )
            .presentationDetents([.height(220)])
            .presentationDragIndicator(.visible)
            .interactiveDismissDisabled(true)
        }
        .sheet(isPresented: $showingReviewSheet) {
            AutoReportReviewSheet(
                incidents: $incidents,
                processedPhotos: processedPhotos,
                submitting: submitting,
                onSubmit: submitSelected
            )
            .presentationDetents([.large])
            .presentationDragIndicator(.visible)
            .presentationBackground(Color(.systemBackground))
        }
    }

    private func startScan() {
        scanTask?.cancel()
        scanStartedAt = Date()
        ReportedAnalytics.logAutoReportScanStarted(scanWindow: scanWindow.id)
        scanning = true
        showingReviewSheet = false
        message = nil
        processed = 0
        total = 0
        incidents = []
        processedPhotos = []
        let selectedWindow = scanWindow
        scanTask = Task {
            let summary = await IOSMediaScanner.shared.runAutoReportScan(lookback: selectedWindow.lookback) { processed, total in
                Task { @MainActor in
                    self.processed = processed
                    self.total = total
                }
            }
            guard !Task.isCancelled else {
                return
            }
            await MainActor.run {
                scanTask = nil
                scanning = false
                processed = summary.scannedCount
                total = summary.scannedCount
                incidents = buildAutoReportIncidents(from: summary.matches)
                processedPhotos = summary.processedPhotos
                if !summary.photoAccessGranted {
                    message = "Photo library permission is needed to scan \(selectedWindow.sentenceLabel) without opening the gallery."
                } else if summary.scannedCount == 0 {
                    message = "No new photos from \(selectedWindow.sentenceLabel) were available to scan."
                } else if incidents.isEmpty {
                    message = "Scanned \(summary.scannedCount) photo\(summary.scannedCount == 1 ? "" : "s") and did not find a blocked bike lane or blocked crosswalk report to review."
                } else {
                    message = nil
                }
                if summary.photoAccessGranted && summary.scannedCount > 0 {
                    DispatchQueue.main.async {
                        showingReviewSheet = true
                    }
                }
            }
        }
    }

    private func cancelScan() {
        scanTask?.cancel()
        scanTask = nil
        scanning = false
        scanStartedAt = nil
        processed = 0
        total = 0
        message = "Photo scan cancelled."
    }

    private func submitSelected() {
        let selectedIncidents = incidents.filter { $0.selected && $0.good }
        guard !selectedIncidents.isEmpty else { return }
        let selectedMediaCount = selectedIncidents.reduce(0) { $0 + $1.media.count }
        let selectedComplaintCount = selectedIncidents.reduce(0) { $0 + ($1.complaintId.isEmpty ? 0 : 1) }
        let invalidCount = selectedIncidents.filter { !$0.isSubmittable }.count
        let scanToSubmitMillis = scanStartedAt.map { max(0, Int64(Date().timeIntervalSince($0) * 1000)) }
        ReportedAnalytics.logAutoReportSummarySubmitTapped(
            reportCount: selectedIncidents.count,
            mediaCount: selectedMediaCount,
            complaintCount: selectedComplaintCount,
            invalidCount: invalidCount,
            isAuthorized: isAuthorized,
            scanToSubmitMillis: scanToSubmitMillis
        )
        guard invalidCount == 0 else {
            message = "\(invalidCount) kept auto-report\(invalidCount == 1 ? "" : "s") need edits before submission."
            showingReviewSheet = true
            return
        }
        guard isAuthorized else {
            onRequireLogin()
            return
        }
        ReportedAnalytics.logReportedAiBulkSubmit(
            surface: "auto_report_review",
            reportCount: selectedIncidents.count,
            mediaCount: selectedMediaCount,
            complaintCount: selectedComplaintCount,
            scanToSubmitMillis: scanToSubmitMillis
        )
        submitting = true
        message = nil
        Task {
            var failureReports = selectedIncidents.map { incident in
                ReportSubmissionFailureLogger.reportPayload(
                    plate: incident.plate,
                    plateRegion: incident.plateRegion,
                    address: incident.address,
                    complaintIds: [incident.complaintId],
                    timeOfIncidentIso: incident.occurredAtIso,
                    latitude: incident.latitude,
                    longitude: incident.longitude,
                    description: incident.description,
                    notes: incident.notes,
                    mediaFileCount: 0
                )
            }
            do {
                var submittedObjectId: String?
                for (index, incident) in selectedIncidents.enumerated() {
                    let media = incident.media
                    let mediaFiles = try await ParseMediaUploader.uploadAll(media)
                    failureReports[index] = ReportSubmissionFailureLogger.reportPayload(
                        plate: incident.plate,
                        plateRegion: incident.plateRegion,
                        address: incident.address,
                        complaintIds: [incident.complaintId],
                        timeOfIncidentIso: incident.occurredAtIso,
                        latitude: incident.latitude,
                        longitude: incident.longitude,
                        description: incident.description,
                        notes: incident.notes,
                        mediaFileCount: mediaFiles.count
                    )
                    let objectId = try await SharedBridge.shared.container.submitReportUseCase.execute(command: SubmitReportCommand(
                        plate: incident.plate,
                        plateRegion: incident.plateRegion,
                        description: incident.description,
                        notes: incident.notes,
                        address: incident.address,
                        complaintIds: [incident.complaintId],
                        timeOfIncidentIso: incident.occurredAtIso,
                        latitude: incident.latitude.map { KotlinDouble(double: $0) },
                        longitude: incident.longitude.map { KotlinDouble(double: $0) },
                        vehicleImageDescription: nil,
                        vehicleColor: nil,
                        vehicleMake: nil,
                        vehicleModel: nil,
                        mediaUrls: [],
                        mediaFiles: mediaFiles,
                        vehicleVin: nil,
                        vehicleYear: nil,
                        vehicleBodyClass: nil,
                        philadelphiaMobilityAccessDetails: nil
                    ))
                    submittedObjectId = objectId
                    IOSMediaScannerSettings.markSubmittedAutoReportContentHashes(incident.infractions.compactMap(\.contentHash))
                    PersistentMediaStore.deleteStoredMedia(media.map(\.fileURL))
                    incident.infractions.forEach { IOSDetectedInfractionStore.remove(id: $0.id) }
                }
                await MainActor.run {
                    incidents.removeAll { incident in
                        selectedIncidents.contains { $0.id == incident.id }
                    }
                    submitting = false
                    showingReviewSheet = false
                    if let submittedObjectId {
                        onReportSubmitted(submittedObjectId)
                    }
                    scanStartedAt = nil
                    message = "Submitted \(selectedIncidents.count) auto-report\(selectedIncidents.count == 1 ? "" : "s")."
                }
            } catch {
                let failedSession = try? await SharedBridge.shared.container.loadSessionUseCase.execute()
                await MainActor.run {
                    ReportedAnalytics.logSubmitReportFailed(
                        surface: "auto_report",
                        stage: "bulk_review",
                        error: error,
                        plateRegion: selectedIncidents.first?.plateRegion ?? "unknown",
                        complaintCount: selectedIncidents.reduce(0) { $0 + ($1.complaintId.isEmpty ? 0 : 1) },
                        mediaCount: selectedIncidents.reduce(0) { $0 + $1.media.count },
                        hasVideo: false,
                        reportCount: selectedIncidents.count,
                        session: failedSession,
                        report: ["reports": failureReports]
                    )
                }
                await MainActor.run {
                    submitting = false
                    message = ReportedAnalytics.userFacingSubmitFailureMessage(
                        for: error,
                        fallback: "Auto-Report submission failed. Review the selected reports and try again."
                    )
                }
            }
        }
    }
}

private struct AutoReportSummaryView: View {
    let incidents: [AutoReportIncident]

    var body: some View {
        let counts = Dictionary(grouping: incidents, by: \.complaintTitle).mapValues(\.count)
        VStack(alignment: .leading, spacing: 6) {
            Text("\(incidents.count) possible report\(incidents.count == 1 ? "" : "s")")
                .font(.headline)
            if counts.isEmpty {
                Text("Scan your recent photos to build a bulk review.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            } else {
                ForEach(counts.keys.sorted(), id: \.self) { title in
                    Text("\(title): \(counts[title] ?? 0)")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

private struct AutoReportProcessedPhotosView: View {
    let photos: [IOSAutoReportProcessedPhoto]

    var body: some View {
        if !photos.isEmpty {
            VStack(alignment: .leading, spacing: 10) {
                Text("Processed photos")
                    .font(.headline)
                ForEach(photos) { photo in
                    AutoReportProcessedPhotoRow(photo: photo)
                }
            }
        }
    }
}

private struct AutoReportProcessedPhotoRow: View {
    let photo: IOSAutoReportProcessedPhoto

    var body: some View {
        HStack(spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(Color(.secondarySystemBackground))
                if let mediaURL = photo.mediaURL {
                    AsyncImage(url: mediaURL) { image in
                        image
                            .resizable()
                            .scaledToFill()
                    } placeholder: {
                        ProgressView()
                    }
                }
            }
            .frame(width: 74, height: 56)
            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))

            VStack(alignment: .leading, spacing: 4) {
                Text(photo.resultTitle)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(photo.matched ? Color.reportedOrange : .primary)
                    .lineLimit(2)
                Text(photo.resultDetail)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
        }
        .padding(12)
        .background(photo.matched ? Color.reportedOrange.opacity(0.10) : Color(.tertiarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

private struct AutoReportScanProgressSheet: View {
    let processed: Int
    let total: Int
    let onCancel: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Scanning Photos")
                .font(.headline)
            VStack(alignment: .leading, spacing: 8) {
                ProgressView(value: total == 0 ? 0 : Double(processed) / Double(total))
                    .tint(Color.reportedOrange)
                Text(total == 0 ? "Preparing photo scan" : "Checking photo \(processed) of \(total)")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            Button(role: .cancel, action: onCancel) {
                Text("Cancel")
                    .font(.body.weight(.semibold))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
            }
            .buttonStyle(.bordered)
        }
        .padding(20)
    }
}

private struct AutoReportReviewSheet: View {
    @Binding var incidents: [AutoReportIncident]
    let processedPhotos: [IOSAutoReportProcessedPhoto]
    let submitting: Bool
    let onSubmit: () -> Void
    @State private var selectedIndex: Int? = 0
    @State private var loggedSummarySignature: String?

    private let pageCardInset: CGFloat = 14

    private var keptIncidents: [AutoReportIncident] {
        incidents.filter { $0.selected && $0.good }
    }

    private var submitCount: Int {
        keptIncidents.count
    }

    private var invalidKeptCount: Int {
        keptIncidents.filter { !$0.isSubmittable }.count
    }

    private var pageCount: Int {
        incidents.count + 1
    }

    private var currentIndex: Int {
        min(max(selectedIndex ?? 0, 0), max(pageCount - 1, 0))
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Review Auto-Reports")
                        .font(.headline)
                    Text(reviewSubtitle)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
            }

            GeometryReader { proxy in
                let pageWidth = max(proxy.size.width, 1)
                ScrollView(.horizontal) {
                    LazyHStack(spacing: 0) {
                        ForEach(incidents.indices, id: \.self) { index in
                            AutoReportReviewCarouselPage(width: pageWidth, cardInset: pageCardInset) {
                                AutoReportIncidentCard(incident: $incidents[index])
                            }
                            .id(index)
                        }
                        AutoReportReviewCarouselPage(width: pageWidth, cardInset: pageCardInset) {
                            AutoReportSubmissionSummaryCard(
                                incidents: incidents,
                                processedPhotos: processedPhotos
                            )
                        }
                        .id(incidents.count)
                    }
                    .scrollTargetLayout()
                }
                .scrollIndicators(.hidden)
                .scrollTargetBehavior(.viewAligned)
                .scrollPosition(id: $selectedIndex)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)

            reviewActionControls
            reviewPageIndicator
        }
        .padding(.top, 16)
        .padding(.horizontal, 16)
        .padding(.bottom, 24)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(Color(.systemBackground))
        .onChange(of: incidents.count) { _, count in
            selectedIndex = min(currentIndex, count)
        }
        .onChange(of: currentIndex) { _, _ in
            logSummaryIfNeeded()
        }
        .onAppear {
            logSummaryIfNeeded()
        }
    }

    private var reviewSubtitle: String {
        let discarded = max(0, incidents.count - submitCount)
        if invalidKeptCount > 0 {
            return "\(submitCount) kept, \(discarded) discarded, \(invalidKeptCount) needs edits"
        }
        return "\(submitCount) kept, \(discarded) discarded"
    }

    private var reviewPageIndicator: some View {
        HStack(spacing: 12) {
            HStack(spacing: 7) {
                ForEach(0..<pageCount, id: \.self) { index in
                    Circle()
                        .fill(index == currentIndex ? Color.reportedOrange : Color.reportedOrange.opacity(0.28))
                        .frame(width: index == currentIndex ? 8 : 6, height: index == currentIndex ? 8 : 6)
                }
            }

            Button {
                withAnimation(.easeInOut(duration: 0.18)) {
                    selectedIndex = incidents.count
                }
            } label: {
                Text("Summary")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(currentIndex == incidents.count ? .white : Color.reportedOrange)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(
                        Capsule()
                            .fill(currentIndex == incidents.count ? Color.reportedOrange : Color.reportedOrange.opacity(0.12))
                    )
            }
            .buttonStyle(.plain)
            .disabled(currentIndex == incidents.count)
        }
        .frame(maxWidth: .infinity, alignment: .center)
        .accessibilityLabel(reviewPageAccessibilityLabel)
    }

    private var reviewPageAccessibilityLabel: String {
        if currentIndex == incidents.count {
            return "Submission summary page"
        }
        return "Auto-report \(currentIndex + 1) of \(incidents.count)"
    }

    @ViewBuilder
    private var reviewActionControls: some View {
        if incidents.indices.contains(currentIndex) {
            let isKept = incidents[currentIndex].good && incidents[currentIndex].selected
            HStack(spacing: 10) {
                Button {
                    setCurrentIncidentKept(true)
                } label: {
                    Text("Keep")
                        .font(.caption.weight(.semibold))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                }
                .buttonStyle(.borderedProminent)
                .tint(isKept ? Color.reportedOrange : Color(.tertiarySystemFill))

                Button {
                    setCurrentIncidentKept(false)
                } label: {
                    Text("Discard")
                        .font(.caption.weight(.semibold))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                }
                .buttonStyle(.bordered)
                .tint(isKept ? Color.secondary : Color.red)
            }
            .padding(.horizontal, pageCardInset)
            .transition(.opacity)
        } else if currentIndex == incidents.count {
            Button {
                onSubmit()
            } label: {
                HStack {
                    if submitting {
                        ProgressView()
                            .tint(.white)
                    }
                    Text(submitting ? "Submitting..." : "Submit \(keptIncidents.count) Report\(keptIncidents.count == 1 ? "" : "s")")
                        .font(.body.weight(.semibold))
                }
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
            }
            .buttonStyle(.borderedProminent)
            .tint(Color.reportedOrange)
            .disabled(keptIncidents.isEmpty || invalidKeptCount > 0 || submitting)
            .padding(.horizontal, pageCardInset)
        } else {
            Color.clear
                .frame(height: 42)
        }
    }

    private func logSummaryIfNeeded() {
        guard currentIndex == incidents.count, !incidents.isEmpty else { return }
        let signature = incidents.map(\.id).joined(separator: "|")
        guard loggedSummarySignature != signature else { return }
        loggedSummarySignature = signature
        ReportedAnalytics.logAutoReportSummary(
            count: incidents.count,
            keptCount: submitCount,
            discardedCount: max(0, incidents.count - submitCount),
            invalidCount: invalidKeptCount,
            mediaCount: incidents.reduce(0) { $0 + $1.media.count },
            processedPhotoCount: processedPhotos.count
        )
    }

    private func setCurrentIncidentKept(_ keep: Bool) {
        guard incidents.indices.contains(currentIndex) else { return }
        incidents[currentIndex].good = keep
        incidents[currentIndex].selected = keep
        ReportedAnalytics.logAutoReportDecision(
            keep: keep,
            reportIndex: currentIndex + 1,
            reportCount: incidents.count,
            keptCount: keptIncidents.count,
            mediaCount: incidents[currentIndex].media.count
        )
    }
}

private struct AutoReportReviewCarouselPage<Content: View>: View {
    let width: CGFloat
    let cardInset: CGFloat
    @ViewBuilder let content: () -> Content
    private let verticalInset: CGFloat = 6

    var body: some View {
        let innerWidth = max(width - (cardInset * 2), 1)
        GeometryReader { proxy in
            let innerHeight = max(proxy.size.height - (verticalInset * 2), 1)
            let cardShape = RoundedRectangle(cornerRadius: 14, style: .continuous)
            content()
                .frame(width: innerWidth, height: innerHeight)
                .clipShape(cardShape)
                .overlay(
                    cardShape
                        .strokeBorder(Color.reportedOrange.opacity(0.5), lineWidth: 1.8)
                        .allowsHitTesting(false)
                )
                .position(x: proxy.size.width / 2, y: proxy.size.height / 2)
        }
        .frame(width: width)
        .frame(maxHeight: .infinity)
        .clipped()
    }
}

private struct AutoReportIncidentCard: View {
    @Binding var incident: AutoReportIncident
    @State private var showComplaintChooser = false
    @State private var showPlateEntryScreen = false
    @State private var showPlateRegionSheet = false
    @State private var showAllPlateRegions = false
    @State private var showAddressSearchScreen = false
    @State private var showAddressMapSheet = false
    @State private var showOccurredAtPicker = false
    @State private var addressQuery = ""
    @State private var addressSuggestions: [ComposerState.AddressSuggestion] = []
    @State private var addressLookupInFlight = false
    @State private var addressSearchTask: Task<Void, Never>?
    @State private var selectedPhotoIndex = 0
    @State private var fullScreenTextEditorField: ReportLongTextField?

    private var complaintOptions: [ComplaintOption] {
        complaintOptionsFor(Array(Catalogs.shared.complaintCategories))
    }

    private var validationErrors: ComposerState.ValidationErrors {
        incident.validationErrors
    }

    private var plateCandidates: [ComposerState.PlateCandidate] {
        incident.infractions.flatMap { infraction in
            infraction.composerPlateCandidates(loadCropPreviews: true)
        }
    }

    private var primaryPlateSourceImage: UIImage? {
        guard let previewURL = incident.previewURL else { return nil }
        return UIImage(contentsOfFile: previewURL.path)
    }

    private var imageAddressSuggestion: ComposerState.AddressSuggestion? {
        guard let latitude = incident.latitude,
              let longitude = incident.longitude,
              !incident.address.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return nil
        }
        return ComposerState.AddressSuggestion(
            label: incident.address,
            latitude: latitude,
            longitude: longitude
        )
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                AutoReportGroupedPhotoCarousel(
                    infractions: incident.infractions,
                    selectedIndex: $selectedPhotoIndex,
                    selectedPlate: incident.plate,
                    onCandidateConfirmed: { candidate in
                        incident.plate = autoReportNormalizedPlateInput(candidate.plate)
                        if let state = candidate.state, !state.isEmpty {
                            incident.plateRegion = state
                        }
                    }
                )

                ReportVerifyFields(
                    complaintValue: complaintOptions.first(where: { $0.id == incident.complaintId })?.title ?? incident.complaintTitle,
                    plateValue: incident.plate,
                    plateCandidateCount: incident.infractions.reduce(0) { $0 + $1.candidates.count },
                    plateRegionValue: incident.plateRegion,
                    addressValue: incident.address,
                    occurredAtValue: autoReportDisplayTime(incident.occurredAtIso),
                    validationErrors: validationErrors,
                    descriptionText: $incident.description,
                    notesText: $incident.notes,
                    philadelphiaDetails: nil,
                    isLandscape: false,
                    isTablet: false,
                    usesCompactFieldRow: true,
                    onComplaintTapped: {
                        showComplaintChooser = true
                    },
                    onPlateTapped: {
                        showPlateEntryScreen = true
                    },
                    onStateTapped: {
                        showAllPlateRegions = false
                        showPlateRegionSheet = true
                    },
                    onAddressTapped: {
                        addressQuery = incident.address
                        addressSuggestions = []
                        addressLookupInFlight = false
                        showAddressSearchScreen = true
                    },
                    onAddressClear: {
                        incident.address = ""
                        incident.latitude = nil
                        incident.longitude = nil
                    },
                    onAddressMapTapped: nil,
                    onOccurredAtTapped: {
                        showOccurredAtPicker = true
                    },
                    onDescriptionTapped: {
                        fullScreenTextEditorField = .description
                    },
                    onNotesTapped: {
                        fullScreenTextEditorField = .notes
                    },
                    onPhiladelphiaMobilityAccessChanged: { _ in }
                )

                AutoReportGroupedPhotoStrip(infractions: incident.infractions)

                AutoReportReviewField(
                    title: "AI Summary",
                    value: incident.aiSummary,
                    valueFont: .callout
                )
            }
            .padding(12)
            .padding(.bottom, 16)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background((incident.good && incident.selected) ? Color.reportedOrange.opacity(0.06) : Color(.tertiarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        .sheet(isPresented: $showComplaintChooser) {
            ComplaintChooserSheet(
                title: "Choose Complaint",
                options: complaintOptions,
                selectedComplaintId: incident.complaintId,
                activeAnimatedComplaintId: nil
            ) { option in
                incident.complaintId = option.id
                showComplaintChooser = false
            }
            .presentationDetents([.medium, .large])
        }
        .fullScreenCover(isPresented: $showPlateEntryScreen) {
            PlateEntryScreen(
                plate: Binding(
                    get: { incident.plate },
                    set: { incident.plate = autoReportNormalizedPlateInput($0) }
                ),
                candidates: plateCandidates,
                selectedPlate: incident.plate,
                sourceImage: primaryPlateSourceImage,
                isError: validationErrors.plate != nil,
                onCancel: {
                    showPlateEntryScreen = false
                },
                onClear: {
                    incident.plate = ""
                },
                onCandidateSelected: { candidate in
                    incident.plate = autoReportNormalizedPlateInput(candidate.plate)
                    if let state = candidate.state, !state.isEmpty {
                        incident.plateRegion = state
                    }
                    showPlateEntryScreen = false
                }
            )
        }
        .fullScreenCover(item: $fullScreenTextEditorField) { field in
            FullScreenReportTextEditor(
                title: field.title,
                placeholder: field.placeholder,
                text: autoReportTextBinding(for: field),
                onDone: {
                    fullScreenTextEditorField = nil
                }
            )
        }
        .sheet(isPresented: $showPlateRegionSheet) {
            PlateRegionSheet(
                selectedValue: incident.plateRegion,
                showAllStates: $showAllPlateRegions,
                onSelected: { value in
                    incident.plateRegion = value
                    showPlateRegionSheet = false
                }
            )
            .presentationDetents([.height(showAllPlateRegions ? 300 : 176)])
            .presentationDragIndicator(.hidden)
            .presentationBackground(Color(.systemBackground))
        }
        .fullScreenCover(isPresented: $showAddressSearchScreen) {
            AddressSearchScreen(
                query: Binding(
                    get: { addressQuery },
                    set: { value in
                        addressQuery = value
                        handleAddressQueryChanged(value)
                    }
                ),
                suggestions: addressSuggestions,
                addressProvider: reportAddressProvider(
                    latitude: incident.latitude,
                    longitude: incident.longitude,
                    address: addressQuery.isEmpty ? incident.address : addressQuery
                ),
                isLoading: addressLookupInFlight,
                isError: validationErrors.address != nil,
                imageAddressSuggestion: imageAddressSuggestion,
                hasImageAddressSource: true,
                isRefreshingImageAddress: false,
                onCancel: {
                    showAddressSearchScreen = false
                },
                onClear: {
                    addressQuery = ""
                    addressSuggestions = []
                    addressSearchTask?.cancel()
                    addressLookupInFlight = false
                },
                onOpenMap: {
                    showAddressSearchScreen = false
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) {
                        showAddressMapSheet = true
                    }
                },
                onSelect: selectAddress,
                onUseImageAddress: selectAddress
            )
        }
        .sheet(isPresented: $showAddressMapSheet) {
            AddressMapSheet(
                initialLatitude: incident.latitude,
                initialLongitude: incident.longitude,
                initialAddress: incident.address,
                imageAddressSuggestion: imageAddressSuggestion,
                hasImageAddressSource: true,
                isRefreshingImageAddress: false,
                onCancel: {
                    showAddressMapSheet = false
                },
                onDone: { suggestion in
                    selectAddress(suggestion)
                    showAddressMapSheet = false
                }
            )
            .presentationDetents([.large])
            .presentationDragIndicator(.hidden)
        }
        .sheet(isPresented: $showOccurredAtPicker) {
            ReportedDateTimeSheet(
                isoValue: incident.occurredAtIso,
                photoIsoValue: incident.occurredAtIso,
                hasImageTimeSource: true,
                isRefreshingImageTime: false
            ) { iso in
                incident.occurredAtIso = iso
            }
            .presentationDetents([.height(360)])
            .presentationDragIndicator(.hidden)
            .presentationBackground(Color(.systemBackground))
        }
    }

    private func handleAddressQueryChanged(_ newValue: String) {
        let trimmed = newValue.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty,
              trimmed != incident.address else {
            addressSearchTask?.cancel()
            addressSuggestions = []
            addressLookupInFlight = false
            return
        }
        addressSearchTask?.cancel()
        addressSearchTask = Task {
            await MainActor.run {
                addressLookupInFlight = true
            }
            try? await Task.sleep(for: .milliseconds(250))
            if Task.isCancelled { return }
            let suggestions = await searchReportAddresses(
                query: trimmed,
                latitude: incident.latitude,
                longitude: incident.longitude,
                address: incident.address
            )
            if Task.isCancelled { return }
            await MainActor.run {
                addressLookupInFlight = false
                addressSuggestions = suggestions
            }
        }
    }

    private func selectAddress(_ suggestion: ComposerState.AddressSuggestion) {
        incident.address = suggestion.label
        incident.latitude = suggestion.latitude
        incident.longitude = suggestion.longitude
        addressQuery = suggestion.label
        addressSuggestions = []
        addressLookupInFlight = false
        addressSearchTask?.cancel()
    }

    private func autoReportTextBinding(for field: ReportLongTextField) -> Binding<String> {
        switch field {
        case .description:
            Binding(get: { incident.description }, set: { incident.description = $0 })
        case .notes:
            Binding(get: { incident.notes }, set: { incident.notes = $0 })
        }
    }
}

private struct AutoReportGroupedPhotoCarousel: View {
    let infractions: [IOSDetectedInfraction]
    @Binding var selectedIndex: Int
    let selectedPlate: String?
    let onCandidateConfirmed: (ComposerState.PlateCandidate) -> Void
    @State private var imageViewerInfraction: IOSDetectedInfraction?

    var body: some View {
        VStack(spacing: 8) {
            ZStack {
                if let infraction = selectedInfraction {
                    AutoReportGroupedPhotoPage(
                        infraction: infraction,
                        selectedPlate: selectedPlate
                    ) {
                        imageViewerInfraction = infraction
                    }
                }
            }
            .frame(height: 190)
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            .gesture(photoDragGesture)

            if infractions.count > 1 {
                pageIndicator
            }
        }
        .onChange(of: infractions.map(\.id)) { _, ids in
            selectedIndex = min(selectedIndex, max(0, ids.count - 1))
        }
        .fullScreenCover(
            isPresented: Binding(
                get: { imageViewerInfraction != nil },
                set: { isPresented in
                    if !isPresented {
                        imageViewerInfraction = nil
                    }
                }
            )
        ) {
            if let infraction = imageViewerInfraction,
               let image = UIImage(contentsOfFile: infraction.mediaURL.path) {
                FullScreenImageViewer(
                    image: image,
                    candidates: infraction.composerPlateCandidates(),
                    selectedPlate: selectedPlate,
                    focalPoint: infraction.plateFocalPoint,
                    onDismiss: { imageViewerInfraction = nil },
                    onCandidateConfirmed: { candidate in
                        onCandidateConfirmed(candidate)
                        imageViewerInfraction = nil
                    }
                )
            }
        }
    }

    private var selectedInfraction: IOSDetectedInfraction? {
        guard !infractions.isEmpty else { return nil }
        return infractions[min(selectedIndex, infractions.count - 1)]
    }

    private var pageIndicator: some View {
        HStack(spacing: 10) {
            photoControlButton(systemImage: "chevron.left", isEnabled: selectedIndex > 0) {
                selectPhoto(at: selectedIndex - 1)
            }
            HStack(spacing: 7) {
                ForEach(infractions.indices, id: \.self) { index in
                    Circle()
                        .fill(index == selectedIndex ? Color.reportedOrange : Color.reportedOrange.opacity(0.32))
                        .frame(width: index == selectedIndex ? 8 : 6, height: index == selectedIndex ? 8 : 6)
                }
                Text("\(selectedIndex + 1)/\(infractions.count)")
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(Color.reportedOrange)
                    .padding(.leading, 2)
            }
            photoControlButton(systemImage: "chevron.right", isEnabled: selectedIndex < infractions.count - 1) {
                selectPhoto(at: selectedIndex + 1)
            }
        }
        .frame(maxWidth: .infinity, alignment: .center)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Photo \(selectedIndex + 1) of \(infractions.count)")
    }

    private func photoControlButton(
        systemImage: String,
        isEnabled: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.caption.weight(.bold))
                .foregroundStyle(isEnabled ? Color.reportedOrange : Color.reportedOrange.opacity(0.28))
                .frame(width: 28, height: 28)
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .disabled(!isEnabled)
    }

    private var photoDragGesture: some Gesture {
        DragGesture(minimumDistance: 18, coordinateSpace: .local)
            .onEnded { value in
                guard infractions.count > 1,
                      abs(value.translation.width) > abs(value.translation.height),
                      abs(value.translation.width) > 44 else {
                    return
                }
                if value.translation.width < 0 {
                    selectPhoto(at: selectedIndex + 1)
                } else {
                    selectPhoto(at: selectedIndex - 1)
                }
            }
    }

    private func selectPhoto(at index: Int) {
        guard !infractions.isEmpty else { return }
        withAnimation(.easeInOut(duration: 0.18)) {
            selectedIndex = min(max(index, 0), infractions.count - 1)
        }
    }
}

private struct AutoReportPlateCropStrip: View {
    let infractions: [IOSDetectedInfraction]
    let selectedPlate: String?
    let onSelected: (IOSDetectedInfraction, ComposerState.PlateCandidate) -> Void

    var body: some View {
        if !items.isEmpty {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(items) { item in
                        Button {
                            onSelected(item.infraction, item.candidate)
                        } label: {
                            PlateCandidateCropPreview(
                                candidate: item.candidate,
                                sourceImage: item.sourceImage,
                                size: CGSize(width: 62, height: 34),
                                cornerRadius: 6
                            )
                            .overlay(
                                RoundedRectangle(cornerRadius: 6, style: .continuous)
                                    .stroke(Color(.separator), lineWidth: 1)
                            )
                            .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Show photo \(item.index + 1)")
                    }
                }
            }
            .frame(height: 38)
        }
    }

    private var items: [AutoReportPlateCropItem] {
        infractions.enumerated().compactMap { index, infraction in
            guard let candidate = infraction.representativePlateCandidate(selectedPlate: selectedPlate) else {
                return nil
            }
            return AutoReportPlateCropItem(
                index: index,
                infraction: infraction,
                candidate: candidate,
                sourceImage: UIImage(contentsOfFile: infraction.mediaURL.path)
            )
        }
    }
}

private struct AutoReportPlateCropItem: Identifiable {
    let index: Int
    let infraction: IOSDetectedInfraction
    let candidate: ComposerState.PlateCandidate
    let sourceImage: UIImage?

    var id: String {
        "\(index)-\(candidate.plate)"
    }
}

private struct AutoReportGroupedPhotoPage: View {
    let infraction: IOSDetectedInfraction
    let selectedPlate: String?
    let onTap: () -> Void

    private var candidates: [ComposerState.PlateCandidate] {
        infraction.composerPlateCandidates()
    }

    var body: some View {
        ZStack {
            if let image = UIImage(contentsOfFile: infraction.mediaURL.path) {
                PlateAwareImage(
                    image: image,
                    focalPoint: infraction.plateFocalPoint
                )
                .contentShape(Rectangle())
                .onTapGesture(perform: onTap)

                StillImagePlateOverlay(
                    imageSize: image.size,
                    candidates: candidates,
                    selectedPlate: selectedPlate,
                    focalPoint: infraction.plateFocalPoint,
                    contentMode: .fill
                )
            } else {
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(Color(.secondarySystemBackground))
                    .overlay {
                        VStack(spacing: 8) {
                            Image(systemName: "photo")
                                .font(.system(size: 32, weight: .medium))
                                .foregroundStyle(Color.reportedOrange)
                            Text("Image unavailable")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(.secondary)
                        }
                    }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(.secondarySystemBackground))
    }
}

private extension IOSDetectedInfraction {
    func composerPlateCandidates(loadCropPreviews: Bool = false) -> [ComposerState.PlateCandidate] {
        let sourceImage = loadCropPreviews ? UIImage(contentsOfFile: mediaURL.path) : nil
        return candidates.map { storedCandidate in
            let candidate = storedCandidate.toComposerPlateCandidate()
            guard let sourceImage,
                  let cropPreview = sourceImage.croppedPlatePreview(for: candidate) else {
                return candidate
            }
            return storedCandidate.toComposerPlateCandidate(plateCropPreview: cropPreview)
        }
    }

    var plateFocalPoint: CGPoint? {
        let mappedCandidates = composerPlateCandidates()
        return mappedCandidates.first { $0.plate == plate }?.normalizedFocalPoint
            ?? mappedCandidates.max(by: { $0.confidence < $1.confidence })?.normalizedFocalPoint
    }

    func representativePlateCandidate(selectedPlate: String?) -> ComposerState.PlateCandidate? {
        let mappedCandidates = composerPlateCandidates(loadCropPreviews: true)
        let normalizedSelection = normalizedAutoReportPlate(selectedPlate ?? "")
        if !normalizedSelection.isEmpty,
           let selectedCandidate = mappedCandidates.first(where: {
               normalizedAutoReportPlate($0.plate) == normalizedSelection
           }) {
            return selectedCandidate
        }
        return mappedCandidates.max(by: { $0.confidence < $1.confidence })
    }
}

private extension IOSDetectedInfraction.StoredCandidate {
    func toComposerPlateCandidate(plateCropPreview: UIImage? = nil) -> ComposerState.PlateCandidate {
        ComposerState.PlateCandidate(
            plate: plate,
            confidence: confidence,
            rawPlateText: rawPlateText,
            wasPlateCorrected: wasPlateCorrected ?? false,
            ownOcrText: rawPlateText,
            ownOcrConfidence: confidence,
            state: state,
            stateConfidence: stateConfidence,
            plateType: plateType,
            plateTypeLabel: plateTypeLabel,
            bounds: candidateBounds,
            cornerPoints: [],
            plateCropPreview: plateCropPreview,
            videoFramePreview: nil,
            videoFramePreviewURL: nil,
            videoFrameTimeSeconds: nil
        )
    }

    private var candidateBounds: CGRect? {
        guard let minX = boundsMinX,
              let minY = boundsMinY,
              let maxX = boundsMaxX,
              let maxY = boundsMaxY else {
            return nil
        }
        return CGRect(
            x: minX,
            y: minY,
            width: max(0, maxX - minX),
            height: max(0, maxY - minY)
        )
    }
}

private struct AutoReportGroupedPhotoStrip: View {
    let infractions: [IOSDetectedInfraction]

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("\(infractions.count) processed photo\(infractions.count == 1 ? "" : "s")")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
                .padding(.horizontal, reportedFieldLabelHorizontalInset)
            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(spacing: 8) {
                    ForEach(infractions, id: \.id) { infraction in
                        AutoReportProcessedPhotoThumbnail(infraction: infraction)
                    }
                }
                .padding(.horizontal, reportedFieldLabelHorizontalInset)
            }
        }
    }
}

private struct AutoReportProcessedPhotoThumbnail: View {
    let infraction: IOSDetectedInfraction

    var body: some View {
        ZStack {
            if let image = UIImage(contentsOfFile: infraction.mediaURL.path) {
                PlateAwareImage(
                    image: image,
                    focalPoint: infraction.plateFocalPoint
                )
            } else {
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(Color(.secondarySystemBackground))
                    .overlay {
                        Image(systemName: "photo")
                            .font(.body.weight(.semibold))
                            .foregroundStyle(.secondary)
                    }
            }
        }
        .frame(width: 84, height: 64)
        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
    }
}

private struct AutoReportReviewField: View {
    let title: String
    let value: String
    var valueFont: Font = .subheadline

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
            Text(value.isEmpty ? "Not set" : value)
                .font(valueFont)
                .foregroundStyle(.primary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
    }
}

private struct AutoReportSubmissionSummaryCard: View {
    let incidents: [AutoReportIncident]
    let processedPhotos: [IOSAutoReportProcessedPhoto]
    @State private var plateZoomSelection: AutoReportPlateZoomSelection?

    private var keptIncidents: [AutoReportIncident] {
        incidents.filter { $0.selected && $0.good }
    }

    private var invalidKeptIncidents: [AutoReportIncident] {
        keptIncidents.filter { !$0.isSubmittable }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text("Submission Summary")
                    .font(.headline)

                AutoReportReviewField(
                    title: "Reports to Submit",
                    value: "\(keptIncidents.count) report\(keptIncidents.count == 1 ? "" : "s") from \(keptIncidents.reduce(0) { $0 + $1.infractions.count }) photo\(keptIncidents.reduce(0) { $0 + $1.infractions.count } == 1 ? "" : "s")"
                )
                AutoReportReviewField(
                    title: "Processed Photos",
                    value: "\(processedPhotos.count) scanned, \(processedPhotos.filter { $0.matched }.count) matched, \(processedPhotos.filter { !$0.matched }.count) not queued"
                )
                if !invalidKeptIncidents.isEmpty {
                    AutoReportReviewField(
                        title: "Needs Edits",
                        value: "\(invalidKeptIncidents.count) kept report\(invalidKeptIncidents.count == 1 ? "" : "s") must be completed before submission."
                    )
                }

                if keptIncidents.isEmpty {
                    Text("No reports are currently marked Keep.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .padding(.vertical, 10)
                } else {
                    VStack(alignment: .leading, spacing: 10) {
                        ForEach(keptIncidents) { incident in
                            VStack(alignment: .leading, spacing: 4) {
                                HStack(alignment: .firstTextBaseline) {
                                    Text(incident.complaintTitle)
                                        .font(.subheadline.weight(.semibold))
                                    Spacer(minLength: 8)
                                    Text(incident.isSubmittable ? "Ready" : "Needs edits")
                                        .font(.caption.weight(.semibold))
                                        .foregroundStyle(incident.isSubmittable ? Color.reportedOrange : Color.red)
                                }
                                Text("\(incident.infractions.count) photo\(incident.infractions.count == 1 ? "" : "s") • \(incident.aiSummary)")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                    .lineLimit(3)
                                Text("\(incident.plate) - \(incident.plateRegion) • \(autoReportDisplayTime(incident.occurredAtIso))")
                                    .font(.subheadline.weight(.semibold))
                                AutoReportPlateCropStrip(
                                    infractions: incident.infractions,
                                    selectedPlate: incident.plate
                                ) { infraction, candidate in
                                    plateZoomSelection = AutoReportPlateZoomSelection(
                                        infraction: infraction,
                                        candidate: candidate,
                                        selectedPlate: incident.plate
                                    )
                                }
                                if !incident.address.isEmpty {
                                    Text(incident.address)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                        .lineLimit(2)
                                }
                            }
                            .padding(10)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color(.secondarySystemBackground))
                            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                        }
                    }
                }
            }
            .padding(12)
            .padding(.bottom, 16)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(.tertiarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        .fullScreenCover(item: $plateZoomSelection) { selection in
            if let image = UIImage(contentsOfFile: selection.infraction.mediaURL.path) {
                AutoReportPlateZoomViewer(
                    image: image,
                    candidates: selection.infraction.composerPlateCandidates(),
                    selectedPlate: selection.selectedPlate,
                    focalPoint: selection.candidate.normalizedFocalPoint ?? selection.infraction.plateFocalPoint
                ) {
                    plateZoomSelection = nil
                }
            }
        }
    }
}

private struct AutoReportPlateZoomSelection: Identifiable {
    let id = UUID()
    let infraction: IOSDetectedInfraction
    let candidate: ComposerState.PlateCandidate
    let selectedPlate: String?
}

private struct AutoReportPlateZoomViewer: View {
    let image: UIImage
    let candidates: [ComposerState.PlateCandidate]
    let selectedPlate: String?
    let focalPoint: CGPoint?
    let onDismiss: () -> Void

    @State private var scale: CGFloat = 1.35
    @State private var lastScale: CGFloat = 1.35
    @State private var offset: CGSize = .zero
    @State private var lastOffset: CGSize = .zero

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
                        contentMode: .fill
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
                    if scale > 1.01 {
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
    }
}

private struct AutoReportIncidentRow: View {
    @Binding var incident: AutoReportIncident

    var body: some View {
        HStack(spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(Color(.secondarySystemBackground))
                if let previewURL = incident.previewURL {
                    AsyncImage(url: previewURL) { image in
                        image
                            .resizable()
                            .scaledToFill()
                    } placeholder: {
                        ProgressView()
                    }
                }
            }
            .frame(width: 84, height: 64)
            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))

            VStack(alignment: .leading, spacing: 4) {
                Text(incident.complaintTitle)
                    .font(.subheadline.weight(.semibold))
                    .lineLimit(1)
                Text("\(incident.plate) - \(incident.plateRegion)")
                    .font(.subheadline)
                    .lineLimit(1)
                Text(autoReportDisplayTime(incident.occurredAtIso))
                    .font(.caption)
                    .foregroundStyle(.secondary)
                HStack(spacing: 10) {
                    Button("Good") { incident.good = true }
                        .foregroundStyle(incident.good ? Color.reportedOrange : .secondary)
                    Button("Bad") { incident.good = false }
                        .foregroundStyle(!incident.good ? Color.reportedOrange : .secondary)
                }
                .font(.caption.weight(.semibold))
            }
            Spacer(minLength: 8)
            Toggle("Submit", isOn: $incident.selected)
                .labelsHidden()
                .tint(Color.reportedOrange)
        }
        .padding(12)
        .background(Color(.tertiarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

private func buildAutoReportIncidents(from matches: [IOSDetectedInfraction]) -> [AutoReportIncident] {
    let sorted = uniqueAutoReportMatches(matches).sorted {
        (autoReportDate(from: $0.occurredAtIso) ?? .distantPast) < (autoReportDate(from: $1.occurredAtIso) ?? .distantPast)
    }
    var groups: [[IOSDetectedInfraction]] = []
    for infraction in sorted {
        if let lastIndex = groups.indices.last,
           groups[lastIndex].contains(where: { shouldGroupAutoReportInfractions($0, infraction) }) {
            groups[lastIndex].append(infraction)
        } else {
            groups.append([infraction])
        }
    }
    return groups.compactMap { group in
        guard let first = group.first else { return nil }
        let best = bestAutoReportInfraction(in: group) ?? first
        return AutoReportIncident(
            id: first.id,
            plate: best.plate,
            plateRegion: best.plateRegion,
            complaintId: first.complaintId,
            occurredAtIso: group.compactMap { autoReportDate(from: $0.occurredAtIso) }.min().map(scannerIsoStringUTCForAutoReport) ?? first.occurredAtIso,
            address: group.first { !$0.address.isEmpty }?.address ?? "",
            latitude: group.compactMap(\.latitude).first,
            longitude: group.compactMap(\.longitude).first,
            infractions: group
        )
    }
}

private func uniqueAutoReportMatches(_ matches: [IOSDetectedInfraction]) -> [IOSDetectedInfraction] {
    var seenHashes = Set<String>()
    var seenURLs = Set<String>()
    return matches.filter { match in
        if let contentHash = match.contentHash, !contentHash.isEmpty {
            return seenHashes.insert(contentHash).inserted
        }
        return seenURLs.insert(match.mediaURL.absoluteString).inserted
    }
}

private func shouldGroupAutoReportInfractions(_ existing: IOSDetectedInfraction, _ next: IOSDetectedInfraction) -> Bool {
    guard existing.complaintId == next.complaintId,
          existing.plateRegion == next.plateRegion,
          let existingDate = autoReportDate(from: existing.occurredAtIso),
          let nextDate = autoReportDate(from: next.occurredAtIso) else {
        return false
    }
    let timeGap = abs(existingDate.timeIntervalSince(nextDate))
    let distance = autoReportPlateDistance(existing.plate, next.plate)
    let minPlateLength = min(normalizedAutoReportPlate(existing.plate).count, normalizedAutoReportPlate(next.plate).count)
    let similarPlate = distance <= (minPlateLength >= 6 ? 2 : 1)
    if similarPlate {
        let window: TimeInterval = distance == 0 ? autoReportExactPlateGroupingWindow : autoReportSimilarPlateGroupingWindow
        return timeGap <= window
    }
    guard timeGap <= autoReportLocationRescueGroupingWindow else { return false }
    return autoReportHasCloseLocation(existing, next)
}

private let autoReportExactPlateGroupingWindow: TimeInterval = 5 * 60
private let autoReportSimilarPlateGroupingWindow: TimeInterval = 2 * 60
private let autoReportLocationRescueGroupingWindow: TimeInterval = 60
private let autoReportLocationRescueDistanceMeters: CLLocationDistance = 45

private func bestAutoReportInfraction(in group: [IOSDetectedInfraction]) -> IOSDetectedInfraction? {
    struct PlateCluster {
        var normalizedPlate: String
        var members: [IOSDetectedInfraction]
    }

    var clusters: [PlateCluster] = []
    for infraction in group {
        let normalized = normalizedAutoReportPlate(infraction.plate)
        guard !normalized.isEmpty else { continue }
        if let index = clusters.firstIndex(where: { cluster in
            let distance = autoReportPlateDistance(cluster.normalizedPlate, normalized)
            let minLength = min(cluster.normalizedPlate.count, normalized.count)
            return distance <= (minLength >= 6 ? 2 : 1)
        }) {
            clusters[index].members.append(infraction)
        } else {
            clusters.append(PlateCluster(normalizedPlate: normalized, members: [infraction]))
        }
    }

    let bestCluster = clusters.max { lhs, rhs in
        if lhs.members.count != rhs.members.count {
            return lhs.members.count < rhs.members.count
        }
        let lhsConfidence = lhs.members.map(\.plateConfidence).max() ?? 0
        let rhsConfidence = rhs.members.map(\.plateConfidence).max() ?? 0
        return lhsConfidence < rhsConfidence
    }
    return bestCluster?.members.max { lhs, rhs in
        lhs.plateConfidence < rhs.plateConfidence
    } ?? group.max { lhs, rhs in
        lhs.plateConfidence < rhs.plateConfidence
    }
}

private func autoReportHasCloseLocation(_ lhs: IOSDetectedInfraction, _ rhs: IOSDetectedInfraction) -> Bool {
    if let lhsLocation = autoReportLocation(for: lhs),
       let rhsLocation = autoReportLocation(for: rhs),
       lhsLocation.distance(from: rhsLocation) <= autoReportLocationRescueDistanceMeters {
        return true
    }
    let lhsAddress = normalizedAutoReportAddress(lhs.address)
    let rhsAddress = normalizedAutoReportAddress(rhs.address)
    return !lhsAddress.isEmpty && lhsAddress == rhsAddress
}

private func autoReportLocation(for infraction: IOSDetectedInfraction) -> CLLocation? {
    guard let latitude = infraction.latitude,
          let longitude = infraction.longitude,
          latitude.isFinite,
          longitude.isFinite,
          abs(latitude) <= 90,
          abs(longitude) <= 180,
          abs(latitude) > 0.000001 || abs(longitude) > 0.000001 else {
        return nil
    }
    return CLLocation(latitude: latitude, longitude: longitude)
}

private func normalizedAutoReportAddress(_ value: String) -> String {
    value
        .uppercased()
        .filter { $0.isLetter || $0.isNumber }
}

private func normalizedAutoReportPlate(_ value: String) -> String {
    value
        .uppercased()
        .filter { $0.isLetter || $0.isNumber }
}

private func autoReportPlateDistance(_ a: String, _ b: String) -> Int {
    let left = Array(normalizedAutoReportPlate(a))
    let right = Array(normalizedAutoReportPlate(b))
    if left == right { return 0 }
    if left.isEmpty { return right.count }
    if right.isEmpty { return left.count }

    var previous = Array(0...right.count)
    for (i, leftChar) in left.enumerated() {
        var current = Array(repeating: 0, count: right.count + 1)
        current[0] = i + 1
        for (j, rightChar) in right.enumerated() {
            let substitution = previous[j] + (leftChar == rightChar ? 0 : 1)
            current[j + 1] = min(previous[j + 1] + 1, current[j] + 1, substitution)
        }
        previous = current
    }
    return previous[right.count]
}

private func autoReportComplaintTitle(for complaintId: String) -> String {
    switch complaintId {
    case "Z8vjWz8uYr": return "Blocked bike lane"
    case "GzRxlMN1vl": return "Blocked crosswalk"
    default: return "Complaint"
    }
}

private func autoReportDate(from value: String) -> Date? {
    let formatter = ISO8601DateFormatter()
    formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    let fallback = ISO8601DateFormatter()
    fallback.formatOptions = [.withInternetDateTime]
    return formatter.date(from: value) ?? fallback.date(from: value)
}

private func scannerIsoStringUTCForAutoReport(from date: Date) -> String {
    let formatter = ISO8601DateFormatter()
    formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    formatter.timeZone = TimeZone(secondsFromGMT: 0)
    return formatter.string(from: date)
}

private func autoReportPercent(_ value: Double) -> String {
    let bounded = min(0.999, max(0, value))
    if bounded > 0, bounded < 0.01 {
        return "<1%"
    }
    return "\(Int(bounded * 100))%"
}

private func autoReportNormalizedPlateInput(_ value: String) -> String {
    let normalized = value
        .uppercased()
        .filter { $0.isLetter || $0.isNumber }
    return String(normalized.prefix(autoReportMaxLicensePlateLength))
}

private func autoReportDisplayTime(_ value: String) -> String {
    guard let date = autoReportDate(from: value) else { return value }
    return date.formatted(date: .abbreviated, time: .shortened)
}

struct SettingsScreen: View {
    @StateObject private var viewModel = ProfileViewModel()
    @State private var mediaScannerFeatureEnabled = RemoteConfigOverrides.shared.enableMediaScanner
    @State private var offlineProcessingFeatureEnabled = RemoteConfigOverrides.shared.enableOfflinePhotoProcessing
    @State private var mediaScannerEnabled = IOSMediaScannerSettings.isEnabled
    @State private var notificationsEnabled = IOSMediaScannerSettings.notificationsEnabled
    @State private var offlineProcessingEnabled = IOSMediaScannerSettings.isOfflineProcessingEnabled
    @State private var autoReportPlateThreshold = IOSMediaScannerSettings.autoReportPlateConfidenceThreshold
    @State private var autoReportStateThreshold = IOSMediaScannerSettings.autoReportStateConfidenceThreshold
    @State private var autoReportComplaintThreshold = IOSMediaScannerSettings.autoReportComplaintConfidenceThreshold
    let onThemeModeSelected: (AppThemeMode) -> Void

    var body: some View {
        GeometryReader { geometry in
            let isLandscape = currentInterfaceIsLandscape(fallbackSize: geometry.size)
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
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Reported AI")
                            .font(.headline)
                        Text(OnDeviceGemmaVoiceDraftEngine.settingsStatusText)
                            .font(.subheadline.weight(.semibold))
                        if !OnDeviceGemmaVoiceDraftEngine.accelerationMessage.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                            Text(OnDeviceGemmaVoiceDraftEngine.accelerationMessage)
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                    }
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Auto-Report")
                            .font(.headline)
                        Text("Confidence thresholds")
                            .font(.subheadline.weight(.semibold))
                        Text(autoReportConfidenceThresholdSummary)
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                        Text("Trained with Reported data.")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                        Link("View Roboflow project", destination: reportedRoboflowProjectURL)
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(Color.reportedOrange)
                        SettingsConfidenceThresholdSlider(
                            title: "Plate",
                            value: Binding(
                                get: { autoReportPlateThreshold },
                                set: { value in
                                    autoReportPlateThreshold = value
                                    IOSMediaScannerSettings.autoReportPlateConfidenceThreshold = value
                                    IOSMediaScannerSettings.autoReportPostInferencePlateConfidenceThreshold = value
                                }
                            )
                        )
                        SettingsConfidenceThresholdSlider(
                            title: "State",
                            value: Binding(
                                get: { autoReportStateThreshold },
                                set: { value in
                                    autoReportStateThreshold = value
                                    IOSMediaScannerSettings.autoReportStateConfidenceThreshold = value
                                }
                            )
                        )
                        SettingsConfidenceThresholdSlider(
                            title: "Infraction",
                            value: Binding(
                                get: { autoReportComplaintThreshold },
                                set: { value in
                                    autoReportComplaintThreshold = value
                                    IOSMediaScannerSettings.autoReportComplaintConfidenceThreshold = value
                                }
                            )
                        )
                        Button("Reset thresholds") {
                            IOSMediaScannerSettings.resetAutoReportConfidenceThresholds()
                            refreshAutoReportThresholds()
                        }
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.reportedOrange)
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
            viewModel.onAction(.load)
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
        viewModel.onAction(.themeModeChanged(mode))
        onThemeModeSelected(mode)
    }

    private func refreshMediaFlags() {
        mediaScannerFeatureEnabled = RemoteConfigOverrides.shared.enableMediaScanner
        offlineProcessingFeatureEnabled = RemoteConfigOverrides.shared.enableOfflinePhotoProcessing
        mediaScannerEnabled = IOSMediaScannerSettings.isEnabled
        notificationsEnabled = IOSMediaScannerSettings.notificationsEnabled
        offlineProcessingEnabled = IOSMediaScannerSettings.isOfflineProcessingEnabled
        refreshAutoReportThresholds()
    }

    private func refreshAutoReportThresholds() {
        autoReportPlateThreshold = IOSMediaScannerSettings.autoReportPlateConfidenceThreshold
        autoReportStateThreshold = IOSMediaScannerSettings.autoReportStateConfidenceThreshold
        autoReportComplaintThreshold = IOSMediaScannerSettings.autoReportComplaintConfidenceThreshold
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
    private var pageCount: Int { scannerAvailable ? 4 : 3 }
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
                ReportTutorialPage(
                    icon: "3",
                    title: "Reported AI + Auto-Report",
                    bodyText: "Reported AI can draft fields from photos. Auto-Report can scan recent photos, group likely blocked bike lane or crosswalk reports, and keeps you in review before submit."
                )
                .tag(2)
                if scannerAvailable {
                    ReportTutorialScannerPage()
                        .tag(3)
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

private struct SettingsConfidenceThresholdSlider: View {
    let title: String
    @Binding var value: Double

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(title)
                    .font(.subheadline.weight(.semibold))
                Spacer()
                Text(autoReportPercent(value))
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(Color.reportedOrange)
            }
            Slider(
                value: Binding(
                    get: { value },
                    set: { next in
                        let rounded = (next * 100).rounded() / 100
                        value = min(
                            IOSMediaScannerSettings.maximumAutoReportConfidenceThreshold,
                            max(IOSMediaScannerSettings.minimumAutoReportConfidenceThreshold, rounded)
                        )
                    }
                ),
                in: IOSMediaScannerSettings.minimumAutoReportConfidenceThreshold...IOSMediaScannerSettings.maximumAutoReportConfidenceThreshold,
                step: 0.01
            )
            .tint(Color.reportedOrange)
        }
        .padding(.vertical, 4)
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
                Text([candidate.state, candidate.shortDetectorConfidenceText].compactMap { $0 }.joined(separator: "  "))
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

private struct BasicVideoPlaybackView: View {
    let media: ComposerState.SubmissionMedia
    let onDismiss: () -> Void

    var body: some View {
        ZStack(alignment: .topTrailing) {
            Color.black.ignoresSafeArea()
            VideoPlaybackView(
                url: media.fileURL,
                startSeconds: 0,
                onTimeChanged: { _ in }
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
                    if let overlay = candidate.overlayShape(in: mediaRect) {
                        overlay.path
                            .stroke(candidate.plate == selectedPlate ? Color.green : Color.reportedOrange, lineWidth: 3)
                        Button {
                            onCandidateSelected(candidate)
                        } label: {
                            Text([candidate.plate, candidate.state, candidate.shortDetectorConfidenceText].compactMap { $0 }.joined(separator: "  "))
                                .font(.caption.weight(.bold))
                                .foregroundStyle(.white)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .background(Color.black.opacity(0.78))
                                .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
                        }
                        .buttonStyle(.plain)
                        .position(x: min(proxy.size.width - 70, max(70, overlay.bounds.midX)), y: max(18, overlay.bounds.minY - 16))
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

private let reportedFieldLabelSpacing: CGFloat = 10
private let reportedFieldLabelHorizontalInset: CGFloat = 2

private extension UITextAutocapitalizationType {
    var swiftUIValue: TextInputAutocapitalization {
        switch self {
        case .none:
            return .never
        case .words:
            return .words
        case .sentences:
            return .sentences
        case .allCharacters:
            return .characters
        @unknown default:
            return .sentences
        }
    }
}

private struct ReportedFieldLabel: View {
    let title: String
    var isError = false

    var body: some View {
        Text(title)
            .font(.headline)
            .foregroundStyle(isError ? Color.red : Color.reportedOrange)
            .lineLimit(1)
            .minimumScaleFactor(0.68)
            .allowsTightening(true)
            .padding(.horizontal, reportedFieldLabelHorizontalInset)
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
    var autocapitalizationType: UITextAutocapitalizationType = .sentences
    var autocorrectionDisabled = false
    var onClear: (() -> Void)? = nil
    var trailingIconSystemName: String? = nil
    var onTrailingIcon: (() -> Void)? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: reportedFieldLabelSpacing) {
            ReportedFieldLabel(title: title, isError: isError)
            HStack(spacing: 8) {
                inputControl
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

    @ViewBuilder
    private var inputControl: some View {
        if fieldMinHeight != nil {
            ZStack(alignment: .topLeading) {
                if text.isEmpty {
                    Text(title)
                        .foregroundStyle(Color(.placeholderText))
                        .allowsHitTesting(false)
                }
                TextEditor(text: $text)
                    .disabled(disabled)
                    .textInputAutocapitalization(autocapitalizationType.swiftUIValue)
                    .autocorrectionDisabled(autocorrectionDisabled)
                    .foregroundStyle(.primary)
                    .tint(.primary)
                    .scrollContentBackground(.hidden)
                    .frame(maxWidth: .infinity, minHeight: max(24, (fieldMinHeight ?? 68) - 22), alignment: .topLeading)
            }
            .frame(maxWidth: .infinity, alignment: .topLeading)
        } else {
            TextField(title, text: $text)
                .disabled(disabled)
                .keyboardType(keyboardType)
                .textContentType(textContentType)
                .textInputAutocapitalization(autocapitalizationType.swiftUIValue)
                .autocorrectionDisabled(autocorrectionDisabled)
                .foregroundStyle(.primary)
                .tint(.primary)
                .textFieldStyle(.plain)
                .submitLabel(.done)
                .onSubmit { dismissActiveKeyboard() }
                .frame(height: 24)
        }
    }
}

struct PasswordInputField: View {
    let title: String
    @Binding var text: String
    var disabled = false
    var isError = false
    var fieldMinHeight: CGFloat? = nil
    @State private var isPasswordVisible = false

    var body: some View {
        VStack(alignment: .leading, spacing: reportedFieldLabelSpacing) {
            ReportedFieldLabel(title: title, isError: isError)
            HStack(spacing: 8) {
                Group {
                    if isPasswordVisible {
                        TextField(title, text: $text)
                    } else {
                        SecureField(title, text: $text)
                    }
                }
                .disabled(disabled)
                .textContentType(.password)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled(true)
                .foregroundStyle(.primary)
                .tint(.primary)

                Button {
                    isPasswordVisible.toggle()
                } label: {
                    Image(systemName: isPasswordVisible ? "eye.slash" : "eye")
                        .font(.body.weight(.semibold))
                        .foregroundStyle(.primary)
                }
                .buttonStyle(.plain)
                .disabled(disabled)
                .accessibilityLabel(isPasswordVisible ? "Hide password" : "Show password")
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
        VStack(alignment: .leading, spacing: reportedFieldLabelSpacing) {
            ReportedFieldLabel(title: "Complaint", isError: isError)
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
    let text: String
    var isError = false
    let candidateCount: Int
    let onEdit: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: reportedFieldLabelSpacing) {
            ReportedFieldLabel(title: "Plate", isError: isError)
            Button(action: onEdit) {
                HStack(spacing: 6) {
                    Text(text.isEmpty ? "Plate" : text)
                        .foregroundStyle(text.isEmpty ? Color(.placeholderText) : .primary)
                        .lineLimit(1)
                        .minimumScaleFactor(0.72)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    if candidateCount > 0 {
                        Image(systemName: "text.viewfinder")
                            .font(.callout.weight(.semibold))
                            .foregroundStyle(Color.reportedOrange)
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
                .contentShape(RoundedRectangle(cornerRadius: 8))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Plate")
            .accessibilityValue(text.isEmpty ? "Empty" : text)
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
    struct OverlayShape {
        let path: Path
        let bounds: CGRect
    }

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
        parts.append(shortDetectorConfidenceText)
        if let plateTypeLabel { parts.append(plateTypeLabel) }
        return parts.joined(separator: "  ")
    }

    var detectorConfidenceText: String {
        "Plate detector: \(Int(confidence * 100))%"
    }

    var shortDetectorConfidenceText: String {
        "det \(Int(confidence * 100))%"
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

    var ocrSourceTexts: [String] {
        let own = normalizedOcrDisplayText(ownOcrText)
        var rows: [String] = []
        if let own {
            rows.append(ocrSourceText(label: "Reported OCR", text: own, confidence: ownOcrConfidence))
        }
        return rows
    }

    private func ocrSourceText(label: String, text: String, confidence: Double?) -> String {
        if let confidence {
            return "\(label): \(text) (\(Int(max(0, min(confidence, 1)) * 100))%)"
        }
        return "\(label): \(text)"
    }

    private func normalizedOcrDisplayText(_ text: String?) -> String? {
        guard let normalized = text?.trimmingCharacters(in: .whitespacesAndNewlines),
              !normalized.isEmpty
        else {
            return nil
        }
        return normalized
    }

    func overlayShape(in layout: PlateImageLayout) -> OverlayShape? {
        overlayShape(in: CGRect(origin: layout.offset, size: layout.displayedSize))
    }

    func overlayShape(in mediaRect: CGRect) -> OverlayShape? {
        if cornerPoints.count >= 4 {
            let points = cornerPoints.prefix(4).map { point in
                CGPoint(
                    x: mediaRect.minX + min(1, max(0, point.x)) * mediaRect.width,
                    y: mediaRect.minY + min(1, max(0, point.y)) * mediaRect.height
                )
            }
            let minX = points.map(\.x).min() ?? 0
            let maxX = points.map(\.x).max() ?? 0
            let minY = points.map(\.y).min() ?? 0
            let maxY = points.map(\.y).max() ?? 0
            guard maxX > minX, maxY > minY else { return nil }
            var path = Path()
            path.move(to: points[0])
            path.addLine(to: points[1])
            path.addLine(to: points[2])
            path.addLine(to: points[3])
            path.closeSubpath()
            return OverlayShape(
                path: path,
                bounds: CGRect(x: minX, y: minY, width: maxX - minX, height: maxY - minY)
            )
        }

        guard let bounds, bounds.width > 0, bounds.height > 0 else { return nil }
        let rect = CGRect(
            x: mediaRect.minX + bounds.minX * mediaRect.width,
            y: mediaRect.minY + bounds.minY * mediaRect.height,
            width: bounds.width * mediaRect.width,
            height: bounds.height * mediaRect.height
        )
        return OverlayShape(path: Path(rect), bounds: rect)
    }
}

private extension UIImage {
    func croppedPlatePreview(for candidate: ComposerState.PlateCandidate) -> UIImage? {
        guard let bounds = candidate.bounds else { return nil }
        let paddedBounds = bounds.insetBy(dx: -bounds.width * 0.08, dy: -bounds.height * 0.18)
        let clampedBounds = CGRect(
            x: min(1, max(0, paddedBounds.minX)),
            y: min(1, max(0, paddedBounds.minY)),
            width: min(1, max(0, paddedBounds.width)),
            height: min(1, max(0, paddedBounds.height))
        ).intersection(CGRect(x: 0, y: 0, width: 1, height: 1))
        let cropSource = orientationNormalizedForPlateCropping()
        guard let cgImage = cropSource.cgImage else { return nil }
        let pixelRect = CGRect(
            x: clampedBounds.minX * CGFloat(cgImage.width),
            y: clampedBounds.minY * CGFloat(cgImage.height),
            width: clampedBounds.width * CGFloat(cgImage.width),
            height: clampedBounds.height * CGFloat(cgImage.height)
        ).integral.intersection(CGRect(x: 0, y: 0, width: cgImage.width, height: cgImage.height))
        guard pixelRect.width > 0, pixelRect.height > 0, let cropped = cgImage.cropping(to: pixelRect) else {
            return nil
        }
        return UIImage(cgImage: cropped, scale: cropSource.scale, orientation: .up)
    }

    private func orientationNormalizedForPlateCropping() -> UIImage {
        guard let cgImage else { return self }
        let expectedWidth = size.width * scale
        let expectedHeight = size.height * scale
        let matchesDisplayedPixelSpace = abs(CGFloat(cgImage.width) - expectedWidth) < 1 &&
            abs(CGFloat(cgImage.height) - expectedHeight) < 1
        guard imageOrientation != .up || !matchesDisplayedPixelSpace else {
            return self
        }
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = scale
        return UIGraphicsImageRenderer(size: size, format: format).image { _ in
            draw(in: CGRect(origin: .zero, size: size))
        }
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
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(Color(.systemBackground))
    }
}

private struct PlateEntryScreen: View {
    @Environment(\.dismiss) private var dismiss
    @Binding var plate: String
    let candidates: [ComposerState.PlateCandidate]
    let selectedPlate: String?
    let sourceImage: UIImage?
    let isError: Bool
    let onCancel: () -> Void
    let onClear: () -> Void
    let onCandidateSelected: (ComposerState.PlateCandidate) -> Void
    @FocusState private var isPlateFocused: Bool

    var body: some View {
        VStack(spacing: 0) {
            plateEntryHeader
            Divider()
            ScrollView {
                LazyVStack(spacing: 10) {
                    if candidates.isEmpty {
                        plateEmptyState
                    } else {
                        ForEach(candidates) { candidate in
                            PlateCandidatePickerRow(
                                candidate: candidate,
                                sourceImage: sourceImage,
                                isSelected: candidate.plate == selectedPlate
                            ) {
                                onCandidateSelected(candidate)
                                dismissActiveKeyboard()
                                dismiss()
                            }
                        }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 14)
            }
            .scrollDismissesKeyboard(.interactively)
        }
        .background(Color(.systemBackground))
        .onAppear {
            isPlateFocused = true
        }
    }

    private var plateEntryHeader: some View {
        HStack(spacing: 10) {
            Button {
                onCancel()
                dismissActiveKeyboard()
                dismiss()
            } label: {
                Image(systemName: "chevron.left")
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(.primary)
                    .frame(width: 34, height: 58)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Back")

            HStack(spacing: 10) {
                Image(systemName: "text.viewfinder")
                    .font(.body.weight(.semibold))
                    .foregroundStyle(.secondary)
                TextField("Plate", text: $plate)
                    .focused($isPlateFocused)
                    .keyboardType(.asciiCapable)
                    .textInputAutocapitalization(.characters)
                    .autocorrectionDisabled(true)
                    .foregroundStyle(.primary)
                    .tint(.primary)
                    .textFieldStyle(.plain)
                    .submitLabel(.done)
                    .onSubmit {
                        dismissActiveKeyboard()
                        dismiss()
                    }
                if !plate.isEmpty {
                    Button {
                        onClear()
                        isPlateFocused = true
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.body.weight(.semibold))
                            .foregroundStyle(.secondary)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Clear plate")
                }
            }
            .padding(.horizontal, 12)
            .frame(height: 46)
            .background(Color(.secondarySystemBackground))
            .overlay(
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .stroke(isError ? Color.red : Color.clear, lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))

            Button {
                dismissActiveKeyboard()
                dismiss()
            } label: {
                Text("Done")
                    .font(.body.weight(.semibold))
                    .foregroundStyle(Color.reportedOrange)
                    .frame(height: 42)
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 12)
        .padding(.top, 10)
        .padding(.bottom, 10)
        .background(Color(.systemBackground))
    }

    private var plateEmptyState: some View {
        VStack(spacing: 10) {
            Image(systemName: "text.viewfinder")
                .font(.title2.weight(.semibold))
                .foregroundStyle(Color.reportedOrange)
                .frame(width: 48, height: 48)
                .background(Color.reportedOrange.opacity(0.12))
                .clipShape(Circle())
            Text("No plate candidates")
                .font(.headline.weight(.semibold))
            Text("Enter the plate manually.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .multilineTextAlignment(.center)
        .frame(maxWidth: .infinity)
        .padding(.vertical, 34)
    }
}

private enum VoiceReportPermissionState {
    case unknown
    case authorized
    case denied

    var isAuthorized: Bool {
        self == .authorized
    }
}

@MainActor
private final class VoiceReportAudioController: ObservableObject {
    @Published var isRecording = false
    @Published var amplitude: Double = 0
    @Published var permissionState: VoiceReportPermissionState = .unknown
    @Published var errorMessage: String?

    private var recorder: AVAudioRecorder?
    private var recordingURL: URL?
    private var meterTimer: Timer?

    init() {
        refreshPermissionState()
    }

    var hasPermission: Bool {
        permissionState.isAuthorized
    }

    func requestPermissions() async -> Bool {
        let microphoneGranted = await requestMicrophonePermission()
        permissionState = microphoneGranted ? .authorized : .denied
        if !microphoneGranted {
            errorMessage = "Microphone access is needed to talk through report fields."
        } else {
            errorMessage = nil
        }
        return microphoneGranted
    }

    func startRecording() async -> Bool {
        errorMessage = nil
        guard await requestPermissions() else { return false }

        _ = stopRecording()

        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("reported-voice-report-\(UUID().uuidString)")
            .appendingPathExtension("wav")
        let settings: [String: Any] = [
            AVFormatIDKey: kAudioFormatLinearPCM,
            AVSampleRateKey: 16_000,
            AVNumberOfChannelsKey: 1,
            AVLinearPCMBitDepthKey: 16,
            AVLinearPCMIsFloatKey: false,
            AVLinearPCMIsBigEndianKey: false
        ]

        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.record, mode: .measurement, options: [])
            try session.setActive(true)

            let recorder = try AVAudioRecorder(url: url, settings: settings)
            recorder.isMeteringEnabled = true
            recorder.prepareToRecord()
            guard recorder.record() else {
                errorMessage = "Microphone recording could not start."
                try? session.setActive(false, options: .notifyOthersOnDeactivation)
                return false
            }
            self.recorder = recorder
            recordingURL = url
            isRecording = true
            startMetering()
            return true
        } catch {
            errorMessage = error.localizedDescription
            _ = stopRecording()
            return false
        }
    }

    func stopRecording() -> URL? {
        recorder?.stop()
        stopMetering()
        recorder = nil
        isRecording = false
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        let url = recordingURL
        recordingURL = nil
        guard let url else { return nil }
        let fileSize = (try? FileManager.default.attributesOfItem(atPath: url.path)[.size] as? NSNumber)?.intValue ?? 0
        if fileSize <= 44 {
            try? FileManager.default.removeItem(at: url)
            return nil
        }
        return url
    }

    func reset() {
        if let url = stopRecording() {
            try? FileManager.default.removeItem(at: url)
        }
        errorMessage = nil
    }

    private func startMetering() {
        stopMetering(resetAmplitude: false)
        meterTimer = Timer.scheduledTimer(withTimeInterval: 0.06, repeats: true) { [weak self] _ in
            Task { @MainActor in
                self?.updateMeter()
            }
        }
    }

    private func stopMetering(resetAmplitude: Bool = true) {
        meterTimer?.invalidate()
        meterTimer = nil
        if resetAmplitude {
            amplitude = 0
        }
    }

    private func updateMeter() {
        guard let recorder else { return }
        recorder.updateMeters()
        let averagePower = max(-60.0, Double(recorder.averagePower(forChannel: 0)))
        let peakPower = max(-60.0, Double(recorder.peakPower(forChannel: 0)))
        let normalizedAverage = (averagePower + 60.0) / 60.0
        let normalizedPeak = (peakPower + 60.0) / 60.0
        let blendedLevel = max(normalizedPeak, normalizedAverage * 1.18)
        amplitude = min(1.0, max(0.0, pow(blendedLevel, 1.35)))
    }

    func refreshPermissionState() {
        let microphoneAuthorized = AVAudioApplication.shared.recordPermission == .granted
        permissionState = microphoneAuthorized ? .authorized : .unknown
    }

    private func requestMicrophonePermission() async -> Bool {
        await withCheckedContinuation { continuation in
            AVAudioApplication.requestRecordPermission { granted in
                continuation.resume(returning: granted)
            }
        }
    }
}

private struct VoiceReportGemmaResult {
    let transcript: String?
    let draft: VoiceReportDraft
}

private enum OnDeviceGemmaVoiceDraftEngine {
    static let modelDownloadSizeLabel = "2.6 GB"
    static var accelerationMessage: String {
        supportsFastOnDeviceAI ? "" : slowAccelerationMessage
    }
    private static let slowAccelerationMessage = "Hardware acceleration is unavailable for this model on this device. REPORTED AI may run slowly."
    private static let modelDownloadFileName = "gemma-4-E2B-it.litertlm"
    private static let modelDownloadEstimatedBytes: Int64 = 2_590_000_000
    private static let modelDownloadMinimumBytes: Int64 = 512 * 1024 * 1024
    private static let modelDownloadURL = URL(
        string: "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm?download=true"
    )!
    private static let modelFileNames = [
        "gemma-voice-report.litertlm",
        "gemma-4-E2B-it.litertlm",
        "gemma-4-E4B-it.litertlm"
    ]

    static func isModelInstalled() -> Bool {
        resolveModelURL() != nil
    }

    private static var supportsFastOnDeviceAI: Bool {
        isFastOnDeviceAIModelIdentifier(currentDeviceModelIdentifier())
    }

    private static func currentDeviceModelIdentifier() -> String {
        if let simulatedModel = ProcessInfo.processInfo.environment["SIMULATOR_MODEL_IDENTIFIER"],
           !simulatedModel.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return simulatedModel
        }

        var systemInfo = utsname()
        uname(&systemInfo)
        var machine = systemInfo.machine
        let machineSize = MemoryLayout.size(ofValue: machine)
        return withUnsafePointer(to: &machine) { pointer in
            pointer.withMemoryRebound(to: CChar.self, capacity: machineSize) { rebound in
                String(cString: rebound)
            }
        }
    }

    private static func isFastOnDeviceAIModelIdentifier(_ identifier: String) -> Bool {
        if identifier.hasPrefix("iPhone") {
            return isIPhone16OrNewerModelIdentifier(identifier)
        }
        if identifier.hasPrefix("iPad") {
            return isAppleIntelligenceCapableIPadModelIdentifier(identifier)
        }
        return false
    }

    private static func isIPhone16OrNewerModelIdentifier(_ identifier: String) -> Bool {
        guard let model = modelIdentifierParts(identifier, prefix: "iPhone") else { return false }
        // Apple's model identifiers for the iPhone 16 family start at iPhone17,x.
        return model.major >= 17
    }

    private static func isAppleIntelligenceCapableIPadModelIdentifier(_ identifier: String) -> Bool {
        guard let model = modelIdentifierParts(identifier, prefix: "iPad") else { return false }
        switch model.major {
        case 13:
            return (4...11).contains(model.minor) || (16...17).contains(model.minor)
        case 14:
            return (3...6).contains(model.minor) || (8...11).contains(model.minor)
        case 15:
            return (3...6).contains(model.minor)
        default:
            return model.major >= 16
        }
    }

    private static func modelIdentifierParts(_ identifier: String, prefix: String) -> (major: Int, minor: Int)? {
        guard identifier.hasPrefix(prefix) else { return nil }
        let suffix = identifier.dropFirst(prefix.count)
        let parts = suffix.split(separator: ",", maxSplits: 1)
        guard parts.count == 2,
              let major = Int(parts[0]),
              let minor = Int(parts[1]) else {
            return nil
        }
        return (major, minor)
    }

    static var settingsStatusText: String {
        guard let modelURL = resolveModelURL() else { return "Not installed" }
        guard let attributes = try? FileManager.default.attributesOfItem(atPath: modelURL.path),
              let size = attributes[.size] as? NSNumber else {
            return "Installed"
        }
        return "Installed (\(formatModelBytes(size.int64Value)))"
    }

    static func downloadModel(onProgress: @escaping (Int64, Int64) -> Void) async throws {
        if resolveModelURL() != nil { return }
        let destinationURL = try modelStorageDirectory()
            .appendingPathComponent(modelDownloadFileName)
        let partialURL = destinationURL
            .deletingLastPathComponent()
            .appendingPathComponent("\(modelDownloadFileName).part")
        try? FileManager.default.removeItem(at: partialURL)
        let downloader = GemmaModelDownloader(
            destinationURL: partialURL,
            estimatedTotalBytes: modelDownloadEstimatedBytes,
            onProgress: onProgress
        )
        do {
            let downloadedURL = try await downloader.download(from: modelDownloadURL)
            try? FileManager.default.removeItem(at: destinationURL)
            try FileManager.default.moveItem(at: downloadedURL, to: destinationURL)
            guard usableModelFile(at: destinationURL) else {
                throw VoiceReportGemmaError.message("REPORTED AI did not finish installing correctly.")
            }
        } catch {
            try? FileManager.default.removeItem(at: partialURL)
            throw error
        }
    }

    static func generateDraft(
        audioURL: URL,
        imageURL: URL? = nil,
        complaintOptions: [ComplaintOption],
        voiceContext: VoiceReportContext
    ) async throws -> VoiceReportGemmaResult {
        let generationTask = Task.detached(priority: .userInitiated) {
            try Task.checkCancellation()
            let attributes = try FileManager.default.attributesOfItem(atPath: audioURL.path)
            let fileSize = (attributes[.size] as? NSNumber)?.intValue ?? 0
            guard fileSize > 44 else {
                throw VoiceReportGemmaError.message("The recording was too short to process.")
            }
            guard let modelURL = resolveModelURL() else {
                throw VoiceReportGemmaError.message("REPORTED AI is not installed. Install REPORTED AI (\(installSizeLabel(from: modelDownloadSizeLabel))) before voice drafting can run.")
            }
            let prompt = buildVoiceReportGemmaPrompt(
                complaintOptions: complaintOptions,
                voiceContext: voiceContext
            )
            let cacheURL = try resolveCacheURL()

            do {
                return try await generateDraftResponse(
                    audioURL: audioURL,
                    imageURL: imageURL,
                    modelURL: modelURL,
                    prompt: prompt,
                    cacheURL: cacheURL,
                    complaintOptions: complaintOptions
                )
            } catch is CancellationError {
                throw CancellationError()
            } catch {
                try Task.checkCancellation()
                guard imageURL != nil else { throw error }
                return try await generateDraftResponse(
                    audioURL: audioURL,
                    imageURL: nil,
                    modelURL: modelURL,
                    prompt: prompt,
                    cacheURL: cacheURL,
                    complaintOptions: complaintOptions
                )
            }
        }
        return try await withTaskCancellationHandler(operation: {
            try await generationTask.value
        }, onCancel: {
            generationTask.cancel()
        })
    }

    static func generateImageContext(imageURL: URL) async -> String? {
        let contextTask = Task.detached(priority: .utility) { () -> String? in
            guard !Task.isCancelled else { return nil }
            guard FileManager.default.fileExists(atPath: imageURL.path),
                  let modelURL = resolveModelURL(),
                  let cacheURL = try? resolveCacheURL() else {
                return nil
            }
            do {
                let engineConfig = try LiteRTLM.EngineConfig(
                    modelPath: modelURL.path,
                    backend: .cpu(),
                    visionBackend: .cpu(),
                    cacheDir: cacheURL.path
                )
                let engine = LiteRTLM.Engine(engineConfig: engineConfig)
                try await engine.initialize()
                let samplerConfig = try LiteRTLM.SamplerConfig(
                    topK: 1,
                    topP: 0.1,
                    temperature: 0.0
                )
                let conversation = try await engine.createConversation(
                    with: LiteRTLM.ConversationConfig(samplerConfig: samplerConfig)
                )
                let response = try await sendVoiceMessage(
                    conversation: conversation,
                    message: LiteRTLM.Message(contents: [
                        .imageFile(imageURL.path),
                        .text("""
                        Briefly inspect this report photo for form-filling context. Return one concise sentence with only clearly visible facts: possible complaint type, vehicle make/model/color/type, visible license plate text, location clues, and scene details. If uncertain, say uncertain rather than guessing.
                        """)
                    ])
                )
                let cleaned = response.toString
                    .trimmingCharacters(in: .whitespacesAndNewlines)
                    .replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
                return cleaned.isEmpty ? nil : String(cleaned.prefix(1200))
            } catch {
                return nil
            }
        }
        return await withTaskCancellationHandler(operation: {
            await contextTask.value
        }, onCancel: {
            contextTask.cancel()
        })
    }

    private static func generateDraftResponse(
        audioURL: URL,
        imageURL: URL?,
        modelURL: URL,
        prompt: String,
        cacheURL: URL,
        complaintOptions: [ComplaintOption]
    ) async throws -> VoiceReportGemmaResult {
            let engineConfig = try LiteRTLM.EngineConfig(
                modelPath: modelURL.path,
                backend: .cpu(),
                visionBackend: imageURL == nil ? nil : .cpu(),
                audioBackend: .cpu(),
                cacheDir: cacheURL.path
            )
            let engine = LiteRTLM.Engine(engineConfig: engineConfig)
            try await engine.initialize()
            let samplerConfig = try LiteRTLM.SamplerConfig(
                topK: 1,
                topP: 0.1,
                temperature: 0.0
            )
            let conversationConfig = LiteRTLM.ConversationConfig(samplerConfig: samplerConfig)
            let conversation = try await engine.createConversation(with: conversationConfig)
            var contents: [LiteRTLM.Content] = []
            if let imagePath = imageURL?.path {
                contents.append(.imageFile(imagePath))
            }
            contents.append(.audioFile(audioURL.path))
            contents.append(.text(prompt))
            let response = try await sendVoiceMessage(
                conversation: conversation,
                message: LiteRTLM.Message(contents: contents)
            )
            try Task.checkCancellation()
            let parsed = try parseVoiceReportGemmaJson(response.toString, complaintOptions: complaintOptions)
            let resolved = await resolveVoiceReportAddress(parsed)
            try Task.checkCancellation()
            return resolved
    }

    private static func sendVoiceMessage(
        conversation: LiteRTLM.Conversation,
        message: LiteRTLM.Message
    ) async throws -> LiteRTLM.Message {
        try Task.checkCancellation()
        return try await withTaskCancellationHandler(operation: {
            let response = try await conversation.sendMessage(message)
            try Task.checkCancellation()
            return response
        }, onCancel: {
            try? conversation.cancel()
        })
    }

    private static func resolveModelURL() -> URL? {
        let fileManager = FileManager.default
        for directory in modelSearchDirectories() {
            for modelFileName in modelFileNames {
                let url = directory.appendingPathComponent(modelFileName)
                if fileManager.fileExists(atPath: url.path), usableModelFile(at: url) {
                    return url
                }
            }
        }

        for modelFileName in modelFileNames {
            let name = URL(fileURLWithPath: modelFileName).deletingPathExtension().lastPathComponent
            let ext = URL(fileURLWithPath: modelFileName).pathExtension
            if let bundledURL = Bundle.main.url(forResource: name, withExtension: ext),
               usableModelFile(at: bundledURL) {
                return bundledURL
            }
        }
        return nil
    }

    private static func modelSearchDirectories() -> [URL] {
        let fileManager = FileManager.default
        var directories: [URL] = []
        if let applicationSupport = fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask).first {
            directories.append(applicationSupport.appendingPathComponent("models", isDirectory: true))
            directories.append(applicationSupport)
        }
        if let documents = fileManager.urls(for: .documentDirectory, in: .userDomainMask).first {
            directories.append(documents.appendingPathComponent("models", isDirectory: true))
            directories.append(documents)
        }
        return directories
    }

    private static func resolveCacheURL() throws -> URL {
        guard let cachesURL = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first else {
            throw VoiceReportGemmaError.message("No writable cache directory is available for Gemma.")
        }
        let fallbackURL = cachesURL.appendingPathComponent("litertlm", isDirectory: true)
        try FileManager.default.createDirectory(at: fallbackURL, withIntermediateDirectories: true)
        return fallbackURL
    }

    private static func modelStorageDirectory() throws -> URL {
        guard let applicationSupport = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first else {
            throw VoiceReportGemmaError.message("No writable storage directory is available for the Gemma model.")
        }
        let modelsURL = applicationSupport.appendingPathComponent("models", isDirectory: true)
        try FileManager.default.createDirectory(at: modelsURL, withIntermediateDirectories: true)
        return modelsURL
    }

    private static func usableModelFile(at url: URL) -> Bool {
        guard let attributes = try? FileManager.default.attributesOfItem(atPath: url.path),
              let size = attributes[.size] as? NSNumber else {
            return false
        }
        return size.int64Value >= modelDownloadMinimumBytes
    }

    private static func formatModelBytes(_ bytes: Int64) -> String {
        let gib = Double(bytes) / (1024.0 * 1024.0 * 1024.0)
        return String(format: "%.1f GB", gib)
    }
}

private final class GemmaModelDownloader: NSObject, URLSessionDownloadDelegate {
    private let destinationURL: URL
    private let estimatedTotalBytes: Int64
    private let onProgress: (Int64, Int64) -> Void
    private var continuation: CheckedContinuation<URL, Error>?
    private var session: URLSession?

    init(
        destinationURL: URL,
        estimatedTotalBytes: Int64,
        onProgress: @escaping (Int64, Int64) -> Void
    ) {
        self.destinationURL = destinationURL
        self.estimatedTotalBytes = estimatedTotalBytes
        self.onProgress = onProgress
    }

    func download(from url: URL) async throws -> URL {
        try await withCheckedThrowingContinuation { continuation in
            self.continuation = continuation
            let configuration = URLSessionConfiguration.default
            configuration.timeoutIntervalForRequest = 60
            configuration.timeoutIntervalForResource = 60 * 60
            let session = URLSession(configuration: configuration, delegate: self, delegateQueue: nil)
            self.session = session
            var request = URLRequest(url: url)
            request.setValue("Reported iOS", forHTTPHeaderField: "User-Agent")
            session.downloadTask(with: request).resume()
        }
    }

    func urlSession(
        _ session: URLSession,
        downloadTask: URLSessionDownloadTask,
        didWriteData bytesWritten: Int64,
        totalBytesWritten: Int64,
        totalBytesExpectedToWrite: Int64
    ) {
        let totalBytes = totalBytesExpectedToWrite > 0 ? totalBytesExpectedToWrite : estimatedTotalBytes
        DispatchQueue.main.async {
            self.onProgress(totalBytesWritten, totalBytes)
        }
    }

    func urlSession(
        _ session: URLSession,
        downloadTask: URLSessionDownloadTask,
        didFinishDownloadingTo location: URL
    ) {
        do {
            try FileManager.default.createDirectory(
                at: destinationURL.deletingLastPathComponent(),
                withIntermediateDirectories: true
            )
            try? FileManager.default.removeItem(at: destinationURL)
            try FileManager.default.moveItem(at: location, to: destinationURL)
            continuation?.resume(returning: destinationURL)
            continuation = nil
            session.finishTasksAndInvalidate()
        } catch {
            continuation?.resume(throwing: error)
            continuation = nil
            session.invalidateAndCancel()
        }
    }

    func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        didCompleteWithError error: Error?
    ) {
        guard let error, let continuation else { return }
        continuation.resume(throwing: error)
        self.continuation = nil
        session.invalidateAndCancel()
    }
}

private enum VoiceReportGemmaError: LocalizedError {
    case message(String)

    var errorDescription: String? {
        switch self {
        case .message(let message):
            return message
        }
    }
}

private struct VoiceDraftChangeRow {
    let field: VoiceDraftField
    let label: String
    let currentValue: String
    let nextValue: String
}

private enum VoiceDraftField: String, CaseIterable, Hashable {
    case complaint
    case plate
    case state
    case address
    case occurredAt
    case description
    case notes
}

private enum VoiceDraftSource: String, Identifiable {
    case current
    case reportedAI

    var id: String { rawValue }

    var title: String {
        switch self {
        case .current:
            return "Current"
        case .reportedAI:
            return "Reported AI"
        }
    }
}

private struct VoiceReportDraft {
    let complaintId: String?
    let complaintTitle: String?
    let plate: String?
    let plateRegion: String?
    let address: String?
    let occurredAtIso: String?
    let make: String?
    let model: String?
    let yearRange: String?
    let description: String?
    let notes: String?

    var hasAnyFillableField: Bool {
        [complaintId, plate, plateRegion, address, occurredAtIso, description, notes]
            .contains { value in
                !(value ?? "").trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            }
    }

    func keepingReportedFields(_ reportedFields: Set<VoiceDraftField>) -> VoiceReportDraft {
        VoiceReportDraft(
            complaintId: reportedFields.contains(.complaint) ? complaintId : nil,
            complaintTitle: reportedFields.contains(.complaint) ? complaintTitle : nil,
            plate: reportedFields.contains(.plate) ? plate : nil,
            plateRegion: reportedFields.contains(.state) ? plateRegion : nil,
            address: reportedFields.contains(.address) ? address : nil,
            occurredAtIso: reportedFields.contains(.occurredAt) ? occurredAtIso : nil,
            make: make,
            model: model,
            yearRange: yearRange,
            description: reportedFields.contains(.description) ? description : nil,
            notes: reportedFields.contains(.notes) ? notes : nil
        )
    }

    func changeRows(
        currentState: ComposerState,
        complaintOptions: [ComplaintOption]
    ) -> [VoiceDraftChangeRow] {
        var rows: [VoiceDraftChangeRow] = []
        let currentComplaint = complaintOptions.first { $0.id == currentState.selectedComplaintId }?.title
        addChangeRow(&rows, field: .complaint, label: "Complaint", currentValue: currentComplaint, nextValue: complaintTitle)
        addChangeRow(&rows, field: .plate, label: "Plate", currentValue: currentState.plate, nextValue: plate)
        addChangeRow(&rows, field: .state, label: "State", currentValue: currentState.plateRegion, nextValue: plateRegion)
        addChangeRow(
            &rows,
            field: .address,
            label: "Address",
            currentValue: currentState.addressQuery.isEmpty ? currentState.address : currentState.addressQuery,
            nextValue: address
        )
        addChangeRow(&rows, field: .occurredAt, label: "Occurred At", currentValue: currentState.occurredAtIso, nextValue: occurredAtIso)
        addChangeRow(&rows, field: .description, label: "Description", currentValue: currentState.description, nextValue: description)
        addChangeRow(&rows, field: .notes, label: "Notes", currentValue: currentState.notes, nextValue: notes)
        return rows
    }

    func replacingAddress(_ nextAddress: String?) -> VoiceReportDraft {
        VoiceReportDraft(
            complaintId: complaintId,
            complaintTitle: complaintTitle,
            plate: plate,
            plateRegion: plateRegion,
            address: nextAddress,
            occurredAtIso: occurredAtIso,
            make: make,
            model: model,
            yearRange: yearRange,
            description: description,
            notes: notes
        )
    }
}

private func addChangeRow(
    _ rows: inout [VoiceDraftChangeRow],
    field: VoiceDraftField,
    label: String,
    currentValue: String?,
    nextValue: String?
) {
    guard let cleanedNext = cleanedVoicePreviewValue(nextValue) else { return }
    rows.append(VoiceDraftChangeRow(
        field: field,
        label: label,
        currentValue: cleanedVoicePreviewValue(currentValue) ?? "Empty",
        nextValue: cleanedNext
    ))
}

private func cleanedVoicePreviewValue(_ value: String?) -> String? {
    let cleaned = value?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
    return cleaned.isEmpty ? nil : cleaned
}

private struct VoiceReportContext {
    let currentDeviceTimeIso: String
    let currentAddress: String?
    let currentLatitude: Double?
    let currentLongitude: Double?
    let imageAddress: String?
    let imageOccurredAtIso: String?
    let imageVisualContext: String?
    let currentOccurredAtIso: String?
    let currentComplaintTitle: String?
    let currentPlate: String?
    let currentPlateRegion: String?
    let currentDescription: String?
    let currentNotes: String?

    init(
        state: ComposerState,
        complaintOptions: [ComplaintOption],
        imageAddressSuggestion: ComposerState.AddressSuggestion?,
        imageVisualContext: String? = nil,
        resolvedCurrentAddress: String? = nil
    ) {
        let formatter = ISO8601DateFormatter()
        let currentAddress = Self.cleaned(state.addressQuery.isEmpty ? state.address : state.addressQuery)
            ?? Self.cleaned(resolvedCurrentAddress)
        let usableCoordinate = Self.usableCoordinate(latitude: state.latitude, longitude: state.longitude)
        let selectedComplaint = complaintOptions.first { $0.id == state.selectedComplaintId }

        self.currentDeviceTimeIso = formatter.string(from: Date())
        self.currentAddress = currentAddress
        self.currentLatitude = usableCoordinate ? state.latitude : nil
        self.currentLongitude = usableCoordinate ? state.longitude : nil
        self.imageAddress = Self.cleaned(imageAddressSuggestion?.label)
        self.imageOccurredAtIso = Self.cleaned(state.photoOccurredAtIso)
        self.imageVisualContext = Self.cleaned(imageVisualContext)
        self.currentOccurredAtIso = Self.cleaned(state.occurredAtIso)
        self.currentComplaintTitle = Self.cleaned(selectedComplaint?.title)
        self.currentPlate = Self.cleaned(state.plate)
        self.currentPlateRegion = Self.cleaned(state.plateRegion)
        self.currentDescription = Self.cleaned(state.description)
        self.currentNotes = Self.cleaned(state.notes)
    }

    var promptBlock: String {
        var lines = ["- currentDeviceTimeIso: \(currentDeviceTimeIso)"]
        if let currentAddress { lines.append("- currentAddress: \(currentAddress)") }
        if let currentLatitude, let currentLongitude {
            lines.append(String(format: "- currentCoordinates: %.6f, %.6f", currentLatitude, currentLongitude))
        }
        if let imageAddress { lines.append("- imageAddress: \(imageAddress)") }
        if let imageOccurredAtIso { lines.append("- imageOccurredAtIso: \(imageOccurredAtIso)") }
        if let imageVisualContext { lines.append("- imageVisualContext: \(imageVisualContext)") }
        if let currentOccurredAtIso { lines.append("- currentOccurredAtIso: \(currentOccurredAtIso)") }
        if let currentComplaintTitle { lines.append("- currentComplaint: \(currentComplaintTitle)") }
        if let currentPlate { lines.append("- currentPlate: \(currentPlate)") }
        if let currentPlateRegion { lines.append("- currentPlateState: \(currentPlateRegion)") }
        if let currentDescription { lines.append("- currentDescription: \(currentDescription)") }
        if let currentNotes { lines.append("- currentNotes: \(currentNotes)") }
        return lines.joined(separator: "\n")
    }

    private static func cleaned(_ value: String?) -> String? {
        let cleaned = value?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return cleaned.isEmpty ? nil : cleaned
    }

    private static func usableCoordinate(latitude: Double?, longitude: Double?) -> Bool {
        guard let latitude, let longitude else { return false }
        guard latitude.isFinite, longitude.isFinite else { return false }
        guard abs(latitude) <= 90, abs(longitude) <= 180 else { return false }
        return abs(latitude) > 0.000001 || abs(longitude) > 0.000001
    }

    static func build(
        state: ComposerState,
        complaintOptions: [ComplaintOption],
        imageAddressSuggestion: ComposerState.AddressSuggestion?,
        imageVisualContext: String? = nil
    ) async -> VoiceReportContext {
        let resolvedCurrentAddress = await resolveVoiceCurrentAddress(state: state)
        return VoiceReportContext(
            state: state,
            complaintOptions: complaintOptions,
            imageAddressSuggestion: imageAddressSuggestion,
            imageVisualContext: imageVisualContext,
            resolvedCurrentAddress: resolvedCurrentAddress
        )
    }
}

private func resolveVoiceCurrentAddress(state: ComposerState) async -> String? {
    let existingAddress = (state.addressQuery.isEmpty ? state.address : state.addressQuery)
        .trimmingCharacters(in: .whitespacesAndNewlines)
    if !existingAddress.isEmpty { return nil }
    guard let latitude = state.latitude, let longitude = state.longitude else { return nil }
    guard latitude.isFinite, longitude.isFinite else { return nil }
    guard abs(latitude) <= 90, abs(longitude) <= 180 else { return nil }
    guard abs(latitude) > 0.000001 || abs(longitude) > 0.000001 else { return nil }
    return await reverseGeocodeAddress(latitude: latitude, longitude: longitude)?.label
        ?? formattedCoordinateText(latitude: latitude, longitude: longitude)
}

private var voiceAssistantCompactSheetHeight: CGFloat {
    UIDevice.current.userInterfaceIdiom == .pad ? 430 : 390
}

private var voiceAssistantMinimizedSheetHeight: CGFloat {
    UIDevice.current.userInterfaceIdiom == .pad ? 126 : 118
}

private let voiceAssistantFloatingSheetHorizontalPadding: CGFloat = 14
private let voiceAssistantFloatingSheetBottomPadding: CGFloat = 6
private let voiceAssistantFloatingSheetCornerRadius: CGFloat = 26

private struct VoiceReportAssistantSheet: View {
    @ObservedObject var audio: VoiceReportAudioController
    let transcript: String
    let draft: VoiceReportDraft?
    let changeRows: [VoiceDraftChangeRow]
    let isProcessing: Bool
    let isMinimized: Bool
    let modelInstalled: Bool
    let modelDownloading: Bool
    let modelDownloadProgress: Double?
    let modelDownloadSizeLabel: String
    let accelerationMessage: String
    let error: String?
    let onRequestPermission: () -> Void
    let onDownloadModel: () -> Void
    let onTalk: () -> Void
    let onStop: () -> Void
    let onCancelProcessing: () -> Void
    let onClear: () -> Void
    let onApply: (VoiceReportDraft) -> Void
    let onDismiss: () -> Void
    @State private var elapsedSeconds: TimeInterval = 0
    @State private var currentSourceFields: Set<VoiceDraftField> = []
    @State private var undoCurrentSourceFields: Set<VoiceDraftField>?
    @State private var pendingBulkSource: VoiceDraftSource?

    private let maxRecordingSeconds: TimeInterval = 29.9
    private enum ScrollTarget {
        static let transcript = "voice-assist-transcript"
        static let changes = "voice-assist-changes"
    }

    private var reviewContentKey: String {
        let cleanedTranscript = transcript.trimmingCharacters(in: .whitespacesAndNewlines)
        let fields = changeRows.map(\.field.rawValue).joined(separator: ",")
        let vehicle = [
            draft?.yearRange,
            draft?.make,
            draft?.model
        ]
        .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
        .joined(separator: ",")
        return [cleanedTranscript, fields, vehicle]
            .filter { !$0.isEmpty }
            .joined(separator: "|")
    }

    var body: some View {
        let rowFields = Set(changeRows.map(\.field))
        let reportedFields = rowFields.subtracting(currentSourceFields)

        Group {
            if isMinimized && audio.isRecording {
                VoiceMinimizedRecordingControl(
                    amplitude: audio.amplitude,
                    elapsedSeconds: elapsedSeconds,
                    maxSeconds: maxRecordingSeconds,
                    action: onStop
                )
                .padding(.horizontal, 18)
                .padding(.top, 12)
                .padding(.bottom, 8)
            } else {
                ScrollViewReader { proxy in
                    ScrollView {
                        VStack(alignment: .center, spacing: 14) {
                            Capsule()
                                .fill(Color(.separator).opacity(0.45))
                                .frame(width: 40, height: 4)
                                .padding(.top, 2)

                            HStack(spacing: 10) {
                                AnimatedSparkleIcon(size: 20)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("Reported AI")
                                        .font(.title3.weight(.semibold))
                                    Text("Dictate the complaint, plate, state, time, address, description, and notes, and we'll fill in the fields.")
                                        .font(.subheadline)
                                        .foregroundStyle(.secondary)
                                }
                                Spacer(minLength: 0)
                                Button(action: onDismiss) {
                                    Image(systemName: "chevron.down")
                                        .font(.body.weight(.semibold))
                                        .foregroundStyle(.primary)
                                        .frame(width: 34, height: 34)
                                }
                                .buttonStyle(.plain)
                                .accessibilityLabel("Dismiss voice assistant")
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)

                            if !accelerationMessage.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                                Text(accelerationMessage)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                            }

                            if !modelInstalled {
                                if modelDownloading {
                                    if let modelDownloadProgress {
                                        ProgressView(value: modelDownloadProgress)
                                            .progressViewStyle(.linear)
                                    } else {
                                        ProgressView()
                                    }
                                    Text("Installing REPORTED AI...")
                                        .font(.subheadline)
                                        .foregroundStyle(.secondary)
                                } else {
                                    VoicePrimaryAction(title: "Install REPORTED AI (\(installSizeLabel(from: modelDownloadSizeLabel)))", action: onDownloadModel)
                                }
                            } else if !audio.hasPermission {
                                VoiceReportMessage(
                                    title: "Microphone access needed",
                                    message: "Reported needs microphone access before you can talk through report fields."
                                )
                                VoicePrimaryAction(title: "Allow microphone", action: onRequestPermission)
                            } else if isProcessing {
                                VoiceProcessingPanel(onCancel: onCancelProcessing)
                            } else {
                                VoiceCaptureControl(
                                    isRecording: audio.isRecording,
                                    amplitude: audio.amplitude,
                                    elapsedSeconds: elapsedSeconds,
                                    maxSeconds: maxRecordingSeconds,
                                    action: audio.isRecording ? onStop : onTalk
                                )
                            }

                            if let displayedError = error ?? audio.errorMessage {
                                ValidationMessage(text: displayedError)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                            }

                            if !transcript.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                                VoiceReportBlock(title: "Transcript", message: transcript)
                                    .id(Self.ScrollTarget.transcript)
                            }

                            if let draft {
                                if !changeRows.isEmpty {
                                    VoiceReportBlockHeader(title: "Changes to apply")
                                        .id(Self.ScrollTarget.changes)
                                    VoiceDraftSourceHeader(
                                        activeSource: activeHeaderSource(for: rowFields),
                                        canUndo: undoCurrentSourceFields != nil,
                                        onSelect: { pendingBulkSource = $0 },
                                        onUndo: undoBulkSelection
                                    )
                                    VStack(alignment: .leading, spacing: 8) {
                                        ForEach(Array(changeRows.enumerated()), id: \.offset) { item in
                                            VoiceDraftDataRow(
                                                row: item.element,
                                                selectedSource: currentSourceFields.contains(item.element.field) ? .current : .reportedAI,
                                                onSelect: { source in
                                                    selectField(item.element.field, source: source)
                                                }
                                            )
                                        }
                                    }
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                }

                                if draft.make != nil || draft.model != nil || draft.yearRange != nil {
                                    VoiceReportBlockHeader(title: "Extracted vehicle")
                                    VStack(alignment: .leading, spacing: 8) {
                                        if let yearRange = draft.yearRange {
                                            VoiceDraftMetadataRow(label: "Vehicle Year", value: yearRange)
                                        }
                                        if let make = draft.make {
                                            VoiceDraftMetadataRow(label: "Make", value: make)
                                        }
                                        if let model = draft.model {
                                            VoiceDraftMetadataRow(label: "Model", value: model)
                                        }
                                    }
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                }

                                if changeRows.isEmpty && draft.make == nil && draft.model == nil && draft.yearRange == nil {
                                    VoiceReportMessage(title: "Nothing to change", message: "Reported AI did not find any form fields to update.")
                                }

                                HStack(spacing: 10) {
                                    VoiceSecondaryAction(title: "Clear", action: onClear)
                                    VoicePrimaryAction(title: "Fill form") {
                                        onApply(draft.keepingReportedFields(reportedFields))
                                    }
                                    .disabled(!draft.keepingReportedFields(reportedFields).hasAnyFillableField)
                                }
                            }
                        }
                    }
                    .padding(.horizontal, 18)
                    .padding(.top, 14)
                    .padding(.bottom, 0)
                    .onChange(of: reviewContentKey) { _, key in
                        scrollToReviewContent(proxy: proxy, key: key)
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(Color(.systemBackground))
        .clipShape(RoundedRectangle(cornerRadius: voiceAssistantFloatingSheetCornerRadius, style: .continuous))
        .shadow(color: Color.black.opacity(0.18), radius: 18, x: 0, y: 8)
        .padding(.horizontal, voiceAssistantFloatingSheetHorizontalPadding)
        .padding(.bottom, voiceAssistantFloatingSheetBottomPadding)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
        .task(id: audio.isRecording) {
            guard audio.isRecording else {
                elapsedSeconds = 0
                return
            }
            let startDate = Date()
            while audio.isRecording {
                elapsedSeconds = min(maxRecordingSeconds, max(0, Date().timeIntervalSince(startDate)))
                if elapsedSeconds >= maxRecordingSeconds {
                    onStop()
                    break
                }
                try? await Task.sleep(nanoseconds: 100_000_000)
            }
        }
        .onChange(of: changeRows.map(\.field)) { _, _ in
            currentSourceFields = []
            undoCurrentSourceFields = nil
            pendingBulkSource = nil
        }
        .alert(item: $pendingBulkSource) { source in
            Alert(
                title: Text("Use \(source.title) for all fields?"),
                message: Text("This changes every field in this Reported AI draft."),
                primaryButton: .default(Text("Use \(source.title)")) {
                    applyBulkSelection(source, fields: rowFields)
                },
                secondaryButton: .cancel {
                    pendingBulkSource = nil
                }
            )
        }
    }

    private func scrollToReviewContent(proxy: ScrollViewProxy, key: String) {
        guard !key.isEmpty else { return }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.18) {
            withAnimation(.snappy) {
                if !changeRows.isEmpty || draft != nil {
                    proxy.scrollTo(Self.ScrollTarget.changes, anchor: .center)
                } else {
                    proxy.scrollTo(Self.ScrollTarget.transcript, anchor: .center)
                }
            }
        }
    }

    private func activeHeaderSource(for fields: Set<VoiceDraftField>) -> VoiceDraftSource? {
        guard !fields.isEmpty else { return nil }
        if fields.isSubset(of: currentSourceFields) {
            return .current
        }
        if currentSourceFields.isDisjoint(with: fields) {
            return .reportedAI
        }
        return nil
    }

    private func applyBulkSelection(_ source: VoiceDraftSource, fields: Set<VoiceDraftField>) {
        undoCurrentSourceFields = currentSourceFields
        switch source {
        case .current:
            currentSourceFields = fields
        case .reportedAI:
            currentSourceFields = []
        }
        pendingBulkSource = nil
    }

    private func undoBulkSelection() {
        guard let undoCurrentSourceFields else { return }
        currentSourceFields = undoCurrentSourceFields
        self.undoCurrentSourceFields = nil
    }

    private func selectField(_ field: VoiceDraftField, source: VoiceDraftSource) {
        undoCurrentSourceFields = nil
        switch source {
        case .current:
            currentSourceFields.insert(field)
        case .reportedAI:
            currentSourceFields.remove(field)
        }
    }
}

private struct VoiceCaptureControl: View {
    let isRecording: Bool
    let amplitude: Double
    let elapsedSeconds: TimeInterval
    let maxSeconds: TimeInterval
    let action: () -> Void

    var body: some View {
        VStack(spacing: 2) {
            VStack(spacing: 4) {
                VoiceLevelMeter(amplitude: amplitude, isActive: isRecording)
                    .frame(width: 170, height: 26)
                    .opacity(isRecording ? 1 : 0)

                HStack(spacing: 8) {
                    Text(formatVoiceElapsedTenths(elapsedSeconds))
                        .font(.caption.weight(.semibold))
                        .monospacedDigit()
                    Text("/ \(formatVoiceElapsedTenths(maxSeconds))")
                        .font(.caption)
                        .monospacedDigit()
                        .foregroundStyle(.secondary)
                }
                .opacity(isRecording ? 1 : 0)
                .accessibilityHidden(!isRecording)
            }
            .frame(height: 48)

            Button(action: action) {
                VStack(spacing: 10) {
                    Text(isRecording ? "STOP" : "TALK")
                        .font(.headline.weight(.bold))
                        .foregroundStyle(Color(.systemBackground))
                    Image(systemName: isRecording ? "stop.fill" : "mic.fill")
                        .font(.title3.weight(.semibold))
                        .foregroundStyle(Color(.systemBackground))
                        .accessibilityHidden(true)
                }
                .frame(width: 112, height: 112)
                .background(isRecording ? Color.red : Color.primary, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(isRecording ? "Stop" : "Talk")
            .padding(8)
        }
        .frame(maxWidth: .infinity)
    }
}

private struct VoiceMinimizedRecordingControl: View {
    let amplitude: Double
    let elapsedSeconds: TimeInterval
    let maxSeconds: TimeInterval
    let action: () -> Void

    private var progress: Double {
        guard maxSeconds > 0 else { return 0 }
        return min(1, max(0, elapsedSeconds / maxSeconds))
    }

    private var remainingSeconds: TimeInterval {
        max(0, maxSeconds - elapsedSeconds)
    }

    var body: some View {
        HStack(spacing: 12) {
            VoiceLevelMeter(amplitude: amplitude, isActive: true)
                .frame(width: 94, height: 30)

            VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: 8) {
                    Text("Recording")
                        .font(.caption.weight(.semibold))
                    Spacer(minLength: 0)
                    Text("\(formatVoiceElapsedTenths(remainingSeconds)) left")
                        .font(.caption.weight(.semibold))
                        .monospacedDigit()
                        .foregroundStyle(.secondary)
                }

                ProgressView(value: progress)
                    .progressViewStyle(.linear)
                    .tint(.primary)

                Text("\(formatVoiceElapsedTenths(elapsedSeconds)) / \(formatVoiceElapsedTenths(maxSeconds))")
                    .font(.caption2)
                    .monospacedDigit()
                    .foregroundStyle(.secondary)
            }

            Button(action: action) {
                VStack(spacing: 4) {
                    Text("STOP")
                        .font(.caption.weight(.bold))
                    Image(systemName: "stop.fill")
                        .font(.caption.weight(.semibold))
                        .accessibilityHidden(true)
                }
                .foregroundStyle(Color(.systemBackground))
                .frame(width: 62, height: 54)
                .background(Color.red, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Stop recording")
        }
        .frame(maxWidth: .infinity)
    }
}

private struct VoiceLevelMeter: View {
    let amplitude: Double
    let isActive: Bool

    var body: some View {
        let level = CGFloat(min(1, max(0, amplitude)))
        HStack(alignment: .center, spacing: 4) {
            ForEach(0..<18, id: \.self) { index in
                let pattern = CGFloat((index * 7) % 11) / 10
                let liveHeight = 5 + (8 + pattern * 15) * max(0.08, level)
                RoundedRectangle(cornerRadius: 2, style: .continuous)
                    .fill(isActive ? Color.primary.opacity(0.72) : Color.secondary.opacity(0.22))
                    .frame(width: 4, height: isActive ? liveHeight : 5)
                    .animation(.easeOut(duration: 0.08), value: level)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
    }
}

private struct VoiceProcessingPanel: View {
    let onCancel: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 10) {
                ProgressView()
                Text("Processing audio")
                    .font(.headline.weight(.semibold))
            }
            VoiceProcessingStep(text: "Recording captured", state: .complete)
            VoiceProcessingStep(text: "Transcribing", state: .active)
            VoiceProcessingStep(text: "Generating form fields", state: .pending)
            Button(role: .cancel, action: onCancel) {
                Text("Cancel processing")
                    .font(.body.weight(.semibold))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 11)
            }
            .buttonStyle(.bordered)
            .accessibilityLabel("Cancel Reported AI processing")
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.secondarySystemBackground).opacity(0.68), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(Color(.separator).opacity(0.35), lineWidth: 1)
        )
    }
}

private struct VoiceProcessingStep: View {
    enum State {
        case complete
        case active
        case pending
    }

    let text: String
    let state: State

    var body: some View {
        HStack(spacing: 10) {
            ZStack {
                Circle()
                    .fill(indicatorFill)
                    .frame(width: 18, height: 18)
                if state != .pending {
                    Circle()
                        .fill(state == .complete ? Color(.systemBackground) : Color.primary)
                        .frame(width: 7, height: 7)
                }
            }
            Text(text)
                .font(.subheadline)
                .foregroundStyle(state == .pending ? Color.secondary.opacity(0.55) : Color.primary)
            Spacer(minLength: 0)
        }
    }

    private var indicatorFill: Color {
        switch state {
        case .complete:
            return .primary
        case .active:
            return Color.primary.opacity(0.18)
        case .pending:
            return Color(.separator).opacity(0.35)
        }
    }
}

private struct VoiceDraftDataRow: View {
    let row: VoiceDraftChangeRow
    let selectedSource: VoiceDraftSource
    let onSelect: (VoiceDraftSource) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(row.label.uppercased())
                .font(.caption2.weight(.bold))
                .foregroundStyle(.secondary)
            HStack(alignment: .top, spacing: 8) {
                VoiceDraftValueColumn(
                    value: row.currentValue,
                    isSelected: selectedSource == .current
                ) {
                    onSelect(.current)
                }
                VoiceDraftValueColumn(
                    value: row.nextValue,
                    isSelected: selectedSource == .reportedAI
                ) {
                    onSelect(.reportedAI)
                }
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(Color(.systemBackground), in: RoundedRectangle(cornerRadius: 8, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 8, style: .continuous)
                .stroke(Color(.separator).opacity(0.35), lineWidth: 1)
        )
    }
}

private struct VoiceDraftSourceHeader: View {
    let activeSource: VoiceDraftSource?
    let canUndo: Bool
    let onSelect: (VoiceDraftSource) -> Void
    let onUndo: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                VoiceDraftSourceHeaderButton(
                    title: VoiceDraftSource.current.title,
                    isSelected: activeSource == .current
                ) {
                    onSelect(.current)
                }
                VoiceDraftSourceHeaderButton(
                    title: VoiceDraftSource.reportedAI.title,
                    isSelected: activeSource == .reportedAI
                ) {
                    onSelect(.reportedAI)
                }
                if canUndo {
                    Button("Undo", action: onUndo)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(Color.reportedOrange)
                        .buttonStyle(.plain)
                        .padding(.horizontal, 10)
                        .frame(height: 34)
                }
            }
            if activeSource == nil {
                Text("Mixed field sources")
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct VoiceDraftSourceHeaderButton: View {
    let title: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.caption.weight(.semibold))
                .foregroundStyle(isSelected ? Color.green : Color.secondary)
                .frame(maxWidth: .infinity)
                .frame(height: 34)
                .background(
                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                        .fill(isSelected ? Color.green.opacity(0.16) : Color(.secondarySystemBackground))
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                        .stroke(isSelected ? Color.green.opacity(0.45) : Color(.separator).opacity(0.3), lineWidth: 1)
                )
        }
        .buttonStyle(.plain)
    }
}

private struct VoiceDraftValueColumn: View {
    let value: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(value)
                .font(.subheadline)
                .foregroundStyle(.primary)
                .multilineTextAlignment(.leading)
                .padding(.horizontal, 10)
                .padding(.vertical, 9)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(
                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                        .fill(isSelected ? Color.green.opacity(0.16) : Color(.secondarySystemBackground).opacity(0.45))
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                        .stroke(isSelected ? Color.green.opacity(0.45) : Color(.separator).opacity(0.24), lineWidth: 1)
                )
        }
        .buttonStyle(.plain)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct VoiceReportBlockHeader: View {
    let title: String

    var body: some View {
        Text(title)
            .font(.caption.weight(.semibold))
            .foregroundStyle(.secondary)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct VoiceDraftMetadataRow: View {
    let label: String
    let value: String

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Text(label.uppercased())
                .font(.caption2.weight(.bold))
                .foregroundStyle(.secondary)
                .frame(width: 98, alignment: .leading)
            Text(value)
                .font(.subheadline)
                .foregroundStyle(.primary)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(Color(.systemBackground), in: RoundedRectangle(cornerRadius: 8, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 8, style: .continuous)
                .stroke(Color(.separator).opacity(0.35), lineWidth: 1)
        )
    }
}

private struct VoicePrimaryAction: View {
    let title: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.body.weight(.semibold))
                .foregroundStyle(Color(.systemBackground))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
        }
        .buttonStyle(.plain)
        .background(Color.primary, in: RoundedRectangle(cornerRadius: 8, style: .continuous))
    }
}

private struct VoiceSecondaryAction: View {
    let title: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.body.weight(.semibold))
                .foregroundStyle(.primary)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
        }
        .buttonStyle(.plain)
        .background(Color(.systemBackground), in: RoundedRectangle(cornerRadius: 8, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 8, style: .continuous)
                .stroke(Color(.separator).opacity(0.5), lineWidth: 1)
        )
    }
}

private func formatVoiceElapsedTenths(_ seconds: TimeInterval) -> String {
    String(format: "%.1fs", min(29.9, max(0, seconds)))
}

private func installSizeLabel(from value: String) -> String {
    value.replacingOccurrences(of: " ", with: "").lowercased()
}

private struct VoiceReportMessage: View {
    let title: String
    let message: String

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.subheadline.weight(.semibold))
            Text(message)
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.secondarySystemBackground).opacity(0.68), in: RoundedRectangle(cornerRadius: 8, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 8, style: .continuous)
                .stroke(Color(.separator).opacity(0.35), lineWidth: 1)
        )
    }
}

private struct VoiceReportBlock: View {
    let title: String
    let message: String

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.secondary)
            Text(message)
                .font(.subheadline)
                .foregroundStyle(.primary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(12)
                .background(Color(.secondarySystemBackground).opacity(0.68), in: RoundedRectangle(cornerRadius: 8, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                        .stroke(Color(.separator).opacity(0.35), lineWidth: 1)
                )
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private func generateVoiceReportDraft(
    transcript: String,
    complaintOptions: [ComplaintOption]
) -> VoiceReportDraft {
    let cleaned = transcript.trimmingCharacters(in: .whitespacesAndNewlines)
    let complaint = inferVoiceComplaint(cleaned, complaintOptions: complaintOptions)
    return VoiceReportDraft(
        complaintId: complaint?.id,
        complaintTitle: complaint?.title,
        plate: inferVoicePlate(cleaned),
        plateRegion: inferVoicePlateRegion(cleaned),
        address: inferVoiceAddress(cleaned),
        occurredAtIso: inferVoiceOccurredAt(cleaned),
        make: inferVoiceVehicleMake(cleaned),
        model: inferVoiceVehicleModel(cleaned),
        yearRange: inferVoiceVehicleYearRange(cleaned),
        description: inferVoiceDescription(cleaned),
        notes: inferVoiceNotes(cleaned)
    )
}

private func buildVoiceReportGemmaPrompt(
    complaintOptions: [ComplaintOption],
    voiceContext: VoiceReportContext
) -> String {
    let complaints = complaintOptions
        .map { "- \($0.id): \($0.title)" }
        .joined(separator: "\n")
    let contextBlock = voiceContext.promptBlock
    let now = Date()
    let currentYear = Calendar.current.component(.year, from: now)
    let currentDateFormatter = DateFormatter()
    currentDateFormatter.locale = Locale(identifier: "en_US_POSIX")
    currentDateFormatter.dateFormat = "yyyy-MM-dd"
    let currentDate = currentDateFormatter.string(from: now)
    return """
    You are filling a Reported traffic complaint form from one spoken audio recording and an optional attached report photo.
    Transcribe the speech, then return only one strict JSON object.
    Do not include markdown or prose.

    Available complaints:
    \(complaints)

    Current form and device context:
    \(contextBlock)

    JSON keys:
    {
      "transcript": string or null,
      "complaint": one available complaint title or null,
      "complaintId": one available complaint ID or null,
      "timeofincident": ISO-8601 incident datetime with timezone or null,
      "occurredAtIso": same value as timeofincident or null,
      "plate": uppercase license plate letters/numbers only, max 8 characters, or null,
      "state": two-letter US plate state, default "NY" only when the speaker implies New York or says no state,
      "address": incident address or null,
      "make": vehicle make, for example "Honda" from "2024 Honda Acura", or null,
      "model": vehicle model, for example "Acura" from "2024 Honda Acura", or null,
      "yearRange": vehicle year or spoken year range, for example "2024" or "2021-2024", or null,
      "description": vehicle description and public-facing incident details or null,
      "notes": extra private details that do not fit another field or null
    }

    Rules:
    - Prefer exact spoken values over guesses.
    - If a report photo is attached, use it as supporting visual context for visible plate text, vehicle details, location clues, and complaint type before producing JSON.
    - If imageVisualContext is present, treat it as a pre-read summary of the attached photo.
    - Spoken values win when audio conflicts with the photo. Do not invent fields from the photo unless they are clearly visible.
    - The current date is \(currentDate) and the current year is \(currentYear). For spoken dates without an explicit year: if the current month is January and the spoken incident month is December, use \(currentYear - 1). Otherwise, use \(currentYear).
    - timeofincident and occurredAtIso must use that month/year rule; do not roll non-December dates back to a previous year.
    - Use current context only when the speaker explicitly refers to it, such as "here", "this location", "current address", "same address", "now", "today", "same time as the photo", "the image time", "same plate", or "keep the state".
    - If the speaker says "here" or "current address", use currentAddress when present; otherwise use imageAddress when present; otherwise use currentCoordinates when present; otherwise use null.
    - If the speaker says "now" or gives a relative time, resolve it against currentDeviceTimeIso.
    - If the speaker says "time from the photo" or "same time as the photo", use imageOccurredAtIso when present.
    - If a field was not spoken, use null.
    - Extract vehicle make, model, and yearRange only when the speaker says them. Do not infer trim, color, or vehicle type into these keys.
    - If the speaker says a phrase like "2024 Honda Acura", set yearRange to "2024", make to "Honda", and model to "Acura".
    - Put any extra information in notes.
    - Pick complaintId only from the available IDs and complaint only from the available titles.
    """
}

private func parseVoiceReportGemmaJson(
    _ jsonText: String,
    complaintOptions: [ComplaintOption]
) throws -> VoiceReportGemmaResult {
    let objectText = try extractFirstJsonObject(from: jsonText)
    let data = Data(objectText.utf8)
    guard let object = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
        throw VoiceReportGemmaError.message("Gemma did not return a JSON object.")
    }
    let transcript = nullableVoiceString(object["transcript"])
    let transcriptFallback = transcript.map {
        generateVoiceReportDraft(transcript: $0, complaintOptions: complaintOptions)
    }
    let complaintId = resolveVoiceComplaintId(
        rawComplaintId: nullableVoiceString(object["complaintId"]),
        rawComplaint: nullableVoiceString(object["complaint"]),
        complaintOptions: complaintOptions
    )
        ?? transcriptFallback?.complaintId
    let complaintTitle = complaintOptions.first(where: { $0.id == complaintId })?.title
    let plate = nullableVoiceString(object["plate"])
        .map(normalizeVoicePlateText)
        .flatMap { $0.isEmpty ? nil : $0 }
        ?? transcriptFallback?.plate
    let draft = VoiceReportDraft(
        complaintId: complaintId,
        complaintTitle: complaintTitle,
        plate: plate,
        plateRegion: nullableVoiceString(object["state"]).map { String($0.uppercased().prefix(2)) } ?? transcriptFallback?.plateRegion,
        address: nullableVoiceString(object["address"]) ?? transcriptFallback?.address,
        occurredAtIso: sanitizeVoiceOccurredAt(
            nullableVoiceString(object["occurredAtIso"])
                ?? nullableVoiceString(object["timeofincident"])
                ?? nullableVoiceString(object["timeOfIncident"])
                ?? nullableVoiceString(object["time_of_incident"])
        ) ?? transcriptFallback?.occurredAtIso,
        make: nullableVoiceString(object["make"])
            ?? nullableVoiceString(object["vehicleMake"])
            ?? nullableVoiceString(object["vehicle_make"])
            ?? transcriptFallback?.make,
        model: nullableVoiceString(object["model"])
            ?? nullableVoiceString(object["vehicleModel"])
            ?? nullableVoiceString(object["vehicle_model"])
            ?? transcriptFallback?.model,
        yearRange: nullableVoiceString(object["yearRange"])
            ?? nullableVoiceString(object["vehicleYearRange"])
            ?? nullableVoiceString(object["vehicle_year_range"])
            ?? nullableVoiceString(object["year"])
            ?? transcriptFallback?.yearRange,
        description: nullableVoiceString(object["description"])
            ?? transcript.flatMap { extractVoiceSection($0, label: "description") },
        notes: nullableVoiceString(object["notes"]) ?? transcriptFallback?.notes
    )
    return VoiceReportGemmaResult(
        transcript: transcript,
        draft: draft
    )
}

private func resolveVoiceReportAddress(_ result: VoiceReportGemmaResult) async -> VoiceReportGemmaResult {
    guard let rawAddress = result.draft.address?.trimmingCharacters(in: .whitespacesAndNewlines),
          !rawAddress.isEmpty else {
        return result
    }
    guard let resolvedAddress = await resolveVoiceAddressLabel(rawAddress),
          resolvedAddress.caseInsensitiveCompare(rawAddress) != .orderedSame else {
        return result
    }
    return VoiceReportGemmaResult(
        transcript: result.transcript,
        draft: result.draft.replacingAddress(resolvedAddress)
    )
}

private func resolveVoiceAddressLabel(_ rawAddress: String) async -> String? {
    if let coordinate = parseVoiceCoordinate(rawAddress) {
        return await reverseGeocodeAddress(latitude: coordinate.latitude, longitude: coordinate.longitude)?.label
            ?? formattedCoordinateText(latitude: coordinate.latitude, longitude: coordinate.longitude)
    }
    for query in voiceAddressSearchQueries(rawAddress) {
        let suggestions = await searchReportAddresses(
            query: query,
            latitude: nil,
            longitude: nil,
            address: rawAddress
        )
        if let chosen = chooseVoiceAddressSuggestion(rawAddress: rawAddress, suggestions: suggestions) {
            return chosen.label
        }
    }
    return nil
}

private func parseVoiceCoordinate(_ rawAddress: String) -> (latitude: Double, longitude: Double)? {
    guard let match = firstVoiceRegexMatch(
        in: rawAddress,
        pattern: #"^\s*(-?\d{1,2}(?:\.\d+)?)\s*,\s*(-?\d{1,3}(?:\.\d+)?)\s*$"#
    ),
          let latitudeText = voiceRegexGroup(in: rawAddress, match: match, group: 1),
          let longitudeText = voiceRegexGroup(in: rawAddress, match: match, group: 2),
          let latitude = Double(latitudeText),
          let longitude = Double(longitudeText),
          abs(latitude) <= 90,
          abs(longitude) <= 180 else {
        return nil
    }
    return (latitude, longitude)
}

private func voiceAddressSearchQueries(_ rawAddress: String) -> [String] {
    let cleaned = cleanVoiceAddressQuery(rawAddress)
    var variants: [String] = []
    func add(_ value: String?) {
        guard let value else { return }
        let normalized = value
            .trimmingCharacters(in: CharacterSet(charactersIn: " ,.;"))
            .replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
        guard normalized.count >= 4 else { return }
        if !variants.contains(where: { $0.caseInsensitiveCompare(normalized) == .orderedSame }) {
            variants.append(normalized)
        }
    }
    add(cleaned)
    if let match = firstVoiceRegexMatch(
        in: cleaned,
        pattern: #"\b(\d{1,6}(?:-\d{1,6})?[A-Za-z]?)\s+(.+)$"#
    ),
       let houseNumber = voiceRegexGroup(in: cleaned, match: match, group: 1),
       let streetText = voiceRegexGroup(in: cleaned, match: match, group: 2) {
        let street = voiceAddressPrefixBeforeStopWords(streetText)
            .trimmingCharacters(in: CharacterSet(charactersIn: " ,.;"))
        add("\(houseNumber) \(street)")
    }
    return variants
}

private func cleanVoiceAddressQuery(_ rawAddress: String) -> String {
    rawAddress
        .replacingOccurrences(
            of: #"\b(?:address\s+is|address|near|at)\b"#,
            with: " ",
            options: [.regularExpression, .caseInsensitive]
        )
        .replacingOccurrences(
            of: #"\b(?:new\s+york|nyc|ny|usa|united\s+states)\b"#,
            with: " ",
            options: [.regularExpression, .caseInsensitive]
        )
        .replacingOccurrences(of: #"[^A-Za-z0-9\s-]"#, with: " ", options: .regularExpression)
        .replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
        .trimmingCharacters(in: .whitespacesAndNewlines)
}

private func voiceAddressPrefixBeforeStopWords(_ value: String) -> String {
    guard let range = value.range(
        of: #"\b(?:new\s+york|ny|usa|united\s+states|apt|apartment|unit|floor|fl)\b"#,
        options: [.regularExpression, .caseInsensitive]
    ) else {
        return value
    }
    return String(value[..<range.lowerBound])
}

private func chooseVoiceAddressSuggestion(
    rawAddress: String,
    suggestions: [ComposerState.AddressSuggestion]
) -> ComposerState.AddressSuggestion? {
    guard !suggestions.isEmpty else { return nil }
    let expectedHouse = firstVoiceRegexGroup(
        in: rawAddress,
        pattern: #"\b(\d{1,6}(?:-\d{1,6})?[A-Za-z]?)\b"#
    )?.lowercased()
    let rawTokens = cleanVoiceAddressQuery(rawAddress)
        .lowercased()
        .components(separatedBy: CharacterSet.alphanumerics.inverted)
        .filter { $0.count >= 3 && !$0.allSatisfy(\.isNumber) }

    func score(_ suggestion: ComposerState.AddressSuggestion) -> Int {
        let label = suggestion.label.lowercased()
        var value = 0
        if let expectedHouse, label.contains(expectedHouse) { value += 6 }
        value += rawTokens.filter { label.contains($0) }.count
        return value
    }
    let chosen = suggestions.max { score($0) < score($1) }
    guard let chosen else { return nil }
    return score(chosen) >= (expectedHouse == nil ? 1 : 6) ? chosen : nil
}

private func extractFirstJsonObject(from text: String) throws -> String {
    guard let start = text.firstIndex(of: "{"), let end = text.lastIndex(of: "}"), start < end else {
        throw VoiceReportGemmaError.message("Gemma did not return a JSON object.")
    }
    return String(text[start...end])
}

private func nullableVoiceString(_ value: Any?) -> String? {
    guard let string = value as? String else { return nil }
    let cleaned = string.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !cleaned.isEmpty, cleaned.lowercased() != "null" else { return nil }
    return cleaned
}

private let voicePlateMaxLength = 8

private func resolveVoiceComplaintId(
    rawComplaintId: String?,
    rawComplaint: String?,
    complaintOptions: [ComplaintOption]
) -> String? {
    if let rawComplaintId,
       complaintOptions.contains(where: { $0.id == rawComplaintId }) {
        return rawComplaintId
    }
    guard let rawComplaint else { return nil }
    let normalizedComplaint = normalizeVoiceComplaintText(rawComplaint)
    guard !normalizedComplaint.isEmpty else { return nil }
    return complaintOptions.first { option in
        option.id.caseInsensitiveCompare(rawComplaint) == .orderedSame ||
            normalizeVoiceComplaintText(option.title) == normalizedComplaint
    }?.id
        ?? inferVoiceComplaint(normalizedComplaint, complaintOptions: complaintOptions)?.id
}

private func normalizeVoiceComplaintText(_ value: String) -> String {
    value
        .lowercased()
        .components(separatedBy: CharacterSet.alphanumerics.inverted)
        .filter { !$0.isEmpty }
        .joined(separator: " ")
}

private func sanitizeVoiceOccurredAt(_ rawValue: String?) -> String? {
    guard let cleaned = rawValue?.trimmingCharacters(in: .whitespacesAndNewlines),
          !cleaned.isEmpty,
          cleaned.lowercased() != "null" else {
        return nil
    }
    let formatter = ISO8601DateFormatter()
    formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    let date = formatter.date(from: cleaned) ?? ISO8601DateFormatter().date(from: cleaned)
    guard let date else { return cleaned }
    return voiceIsoString(from: normalizeVoiceIncidentYear(for: date))
}

private func normalizeVoiceIncidentYear(for date: Date) -> Date {
    let calendar = Calendar.current
    let now = Date()
    let currentYear = calendar.component(.year, from: now)
    let currentMonth = calendar.component(.month, from: now)
    let incidentMonth = calendar.component(.month, from: date)
    let targetYear = currentMonth == 1 && incidentMonth == 12 ? currentYear - 1 : currentYear
    var components = calendar.dateComponents([.month, .day, .hour, .minute, .second, .nanosecond], from: date)
    components.year = targetYear
    return calendar.date(from: components) ?? date
}

private func normalizeVoicePlateText(_ raw: String) -> String {
    String(
        raw
            .uppercased()
            .filter { $0.isLetter || $0.isNumber }
            .prefix(voicePlateMaxLength)
    )
}

private func inferVoiceComplaint(
    _ transcript: String,
    complaintOptions: [ComplaintOption]
) -> ComplaintOption? {
    let normalized = transcript.lowercased()
    let aliases: [([String], [String])] = [
        (["blocked bike lane", "bike lane"], ["bike", "lane"]),
        (["blocked crosswalk", "crosswalk"], ["crosswalk"]),
        (["ran red light", "red light", "stop sign"], ["red", "light"]),
        (["parked illegally", "illegal parking"], ["park"]),
        (["reckless driving", "reckless"], ["reckless"])
    ]
    for (phrases, optionTerms) in aliases {
        if phrases.contains(where: { normalized.contains($0) }) {
            if let option = complaintOptions.first(where: { option in
                let haystack = "\(option.id) \(option.title)".lowercased()
                return optionTerms.allSatisfy { haystack.contains($0) }
            }) {
                return option
            }
        }
    }
    return complaintOptions.first { option in
        let words = option.title
            .lowercased()
            .components(separatedBy: CharacterSet.alphanumerics.inverted)
            .filter { $0.count > 2 }
        return !words.isEmpty && words.allSatisfy { normalized.contains($0) }
    }
}

private func inferVoicePlate(_ transcript: String) -> String? {
    let explicit = firstVoiceRegexGroup(
        in: transcript,
        pattern: #"\b(?:license\s+plate|plate|tag)\s*(?:is|number|#|:)?\s*([a-z0-9][a-z0-9 -]{1,12}?)(?=\s+(?:state|address|complaint|description|notes|time|at|near)\b|[.,;]|$)"#
    )
    let fallback = allVoiceRegexGroups(in: transcript, pattern: #"\b([a-z0-9]{5,10})\b"#)
        .first { token in
            token.contains { $0.isNumber } && token.contains { $0.isLetter }
        }
    let raw = explicit ?? fallback ?? ""
    let normalized = raw
        .uppercased()
        .filter { $0.isLetter || $0.isNumber }
        .prefix(voicePlateMaxLength)
    let plate = String(normalized)
    return plate.isEmpty ? nil : plate
}

private func inferVoicePlateRegion(_ transcript: String) -> String? {
    let normalized = transcript.lowercased()
    let explicit = firstVoiceRegexGroup(
        in: normalized,
        pattern: #"\b(?:state|plate\s+state)\s*(?:is|:)?\s*([a-z]{2}|new york|new jersey|connecticut|pennsylvania)\b"#
    )
    let state = explicit ?? {
        if normalized.contains("new york") { return "new york" }
        if normalized.contains("new jersey") { return "new jersey" }
        if normalized.contains("connecticut") { return "connecticut" }
        if normalized.contains("pennsylvania") { return "pennsylvania" }
        return nil
    }()
    switch state?.trimmingCharacters(in: .whitespacesAndNewlines) {
    case "new york", "ny":
        return "NY"
    case "new jersey", "nj":
        return "NJ"
    case "connecticut", "ct":
        return "CT"
    case "pennsylvania", "pa":
        return "PA"
    default:
        let upper = state?.uppercased()
        return upper?.count == 2 ? upper : nil
    }
}

private func inferVoiceAddress(_ transcript: String) -> String? {
    guard let match = firstVoiceRegexGroup(
        in: transcript,
        pattern: #"\b(?:address\s+is|address|near|at)\s+(.+)$"#
    ) else {
        return nil
    }
    let candidate = voicePrefixBeforeLabels(match)
        .trimmingCharacters(in: CharacterSet(charactersIn: " ,.;"))
    if firstVoiceRegexGroup(in: candidate, pattern: #"^(\d{1,2}(?::\d{2})?\s*(?:am|pm).*)$"#) != nil {
        return nil
    }
    return candidate.count >= 4 ? candidate : nil
}

private func inferVoiceOccurredAt(_ transcript: String) -> String? {
    let normalized = transcript.lowercased()
    if normalized.contains("right now") || normalized.contains("now") {
        return voiceIsoString(from: Date())
    }

    var date = Date()
    if normalized.contains("yesterday"), let yesterday = Calendar.current.date(byAdding: .day, value: -1, to: date) {
        date = yesterday
    }

    if let match = firstVoiceRegexMatch(
        in: transcript,
        pattern: #"\b(?:at\s*)?(\d{1,2})(?::(\d{2}))?\s*(a\.?m\.?|p\.?m\.?)\b"#
    ) {
        let hourText = voiceRegexGroup(in: transcript, match: match, group: 1)
        let minuteText = voiceRegexGroup(in: transcript, match: match, group: 2)
        let meridiem = voiceRegexGroup(in: transcript, match: match, group: 3)?.lowercased() ?? ""
        let rawHour = Int(hourText ?? "") ?? 0
        let minute = Int(minuteText ?? "") ?? 0
        let isPm = meridiem.hasPrefix("p")
        let hour: Int
        if isPm && rawHour < 12 {
            hour = rawHour + 12
        } else if !isPm && rawHour == 12 {
            hour = 0
        } else {
            hour = rawHour
        }

        var components = Calendar.current.dateComponents([.year, .month, .day], from: date)
        components.hour = hour
        components.minute = minute
        components.second = 0
        if let incidentDate = Calendar.current.date(from: components) {
            return voiceIsoString(from: incidentDate)
        }
    }

    if normalized.contains("today") || normalized.contains("yesterday") {
        return voiceIsoString(from: Calendar.current.startOfDay(for: date))
    }
    return nil
}

private func inferVoiceVehicleYearRange(_ transcript: String) -> String? {
    guard let match = firstVoiceRegexMatch(
        in: transcript,
        pattern: #"\b((?:19|20)\d{2})(?:\s*(?:-|to|through)\s*((?:19|20)\d{2}))?\b"#
    ),
          let firstYear = voiceRegexGroup(in: transcript, match: match, group: 1) else {
        return nil
    }
    if let secondYear = voiceRegexGroup(in: transcript, match: match, group: 2) {
        return "\(firstYear)-\(secondYear)"
    }
    return firstYear
}

private func inferVoiceVehicleMake(_ transcript: String) -> String? {
    if let explicit = firstVoiceRegexGroup(
        in: transcript,
        pattern: #"\b(?:make|vehicle\s+make)\s*(?:is|:)?\s*([A-Za-z][A-Za-z -]{1,28}?)(?=\s+(?:model|year|plate|state|address|complaint|description|notes|time)\b|[.,;]|$)"#
    ) {
        return voiceVehicleToken(explicit)
    }
    return voiceVehicleMakeModelPhrase(transcript)?.make
}

private func inferVoiceVehicleModel(_ transcript: String) -> String? {
    if let explicit = firstVoiceRegexGroup(
        in: transcript,
        pattern: #"\b(?:model|vehicle\s+model)\s*(?:is|:)?\s*([A-Za-z0-9][A-Za-z0-9 -]{1,32}?)(?=\s+(?:make|year|plate|state|address|complaint|description|notes|time)\b|[.,;]|$)"#
    ) {
        return voiceVehicleToken(explicit)
    }
    return voiceVehicleMakeModelPhrase(transcript)?.model
}

private func voiceVehicleMakeModelPhrase(_ transcript: String) -> (make: String, model: String)? {
    guard let match = firstVoiceRegexMatch(
        in: transcript,
        pattern: #"\b(?:19|20)\d{2}(?:\s*(?:-|to|through)\s*(?:19|20)\d{2})?\s+([A-Za-z][A-Za-z-]+)\s+([A-Za-z][A-Za-z0-9-]+)\b"#
    ),
          let makeText = voiceRegexGroup(in: transcript, match: match, group: 1),
          let modelText = voiceRegexGroup(in: transcript, match: match, group: 2),
          let make = voiceVehicleToken(makeText),
          let model = voiceVehicleToken(modelText) else {
        return nil
    }
    return (make, model)
}

private func voiceVehicleToken(_ raw: String) -> String? {
    let cleaned = raw
        .trimmingCharacters(in: CharacterSet(charactersIn: " ,.;:"))
        .replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
    guard !cleaned.isEmpty else { return nil }
    return cleaned
        .split(separator: " ")
        .map { token in
            let tokenText = String(token)
            if tokenText.count <= 4, tokenText.uppercased() == tokenText {
                return tokenText
            }
            return tokenText.prefix(1).uppercased() + tokenText.dropFirst().lowercased()
        }
        .joined(separator: " ")
}

private func inferVoiceDescription(_ transcript: String) -> String? {
    let value = extractVoiceSection(transcript, label: "description") ?? transcript.trimmingCharacters(in: .whitespacesAndNewlines)
    let limited = String(value.prefix(280))
    return limited.isEmpty ? nil : limited
}

private func inferVoiceNotes(_ transcript: String) -> String? {
    extractVoiceSection(transcript, label: "notes") ?? extractVoiceSection(transcript, label: "note")
}

private func extractVoiceSection(_ transcript: String, label: String) -> String? {
    let pattern = "\\b\(NSRegularExpression.escapedPattern(for: label))\\s*(?:are|is|:)?\\s+(.+)$"
    guard let match = firstVoiceRegexGroup(in: transcript, pattern: pattern) else { return nil }
    let value = voicePrefixBeforeLabels(match)
        .trimmingCharacters(in: CharacterSet(charactersIn: " ,.;"))
    return value.isEmpty ? nil : value
}

private func voicePrefixBeforeLabels(_ value: String) -> String {
    let pattern = #"\b(?:plate|license\s+plate|state|complaint|description|notes|address|time|when|occurred)\b"#
    guard let range = value.range(of: pattern, options: [.regularExpression, .caseInsensitive]) else {
        return value
    }
    return String(value[..<range.lowerBound])
}

private func firstVoiceRegexGroup(in text: String, pattern: String, group: Int = 1) -> String? {
    guard let match = firstVoiceRegexMatch(in: text, pattern: pattern) else { return nil }
    return voiceRegexGroup(in: text, match: match, group: group)
}

private func allVoiceRegexGroups(in text: String, pattern: String, group: Int = 1) -> [String] {
    guard let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive]) else { return [] }
    let nsRange = NSRange(text.startIndex..<text.endIndex, in: text)
    return regex.matches(in: text, options: [], range: nsRange).compactMap { match in
        voiceRegexGroup(in: text, match: match, group: group)
    }
}

private func firstVoiceRegexMatch(in text: String, pattern: String) -> NSTextCheckingResult? {
    guard let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive]) else { return nil }
    let nsRange = NSRange(text.startIndex..<text.endIndex, in: text)
    return regex.firstMatch(in: text, options: [], range: nsRange)
}

private func voiceRegexGroup(in text: String, match: NSTextCheckingResult, group: Int) -> String? {
    guard match.numberOfRanges > group else { return nil }
    let nsRange = match.range(at: group)
    guard nsRange.location != NSNotFound, let range = Range(nsRange, in: text) else { return nil }
    let value = String(text[range]).trimmingCharacters(in: .whitespacesAndNewlines)
    return value.isEmpty ? nil : value
}

private func voiceIsoString(from date: Date) -> String {
    let formatter = ISO8601DateFormatter()
    formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    return formatter.string(from: date)
}

#if DEBUG
@MainActor
func runReportedVoiceSmokeTestIfRequested() async {
    let arguments = ProcessInfo.processInfo.arguments
    guard arguments.contains("--reported-voice-smoke-test") else { return }

    let fileManager = FileManager.default
    guard let documentsURL = fileManager.urls(for: .documentDirectory, in: .userDomainMask).first else {
        return
    }

    let audioURL = arguments
        .first { $0.hasPrefix("--reported-voice-smoke-audio=") }
        .flatMap { argument -> URL? in
            let path = String(argument.dropFirst("--reported-voice-smoke-audio=".count))
            guard !path.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }
            return URL(fileURLWithPath: path)
        }
        ?? documentsURL.appendingPathComponent("reported-voice-smoke.wav")
    let resultURL = arguments
        .first { $0.hasPrefix("--reported-voice-smoke-result=") }
        .flatMap { argument -> URL? in
            let path = String(argument.dropFirst("--reported-voice-smoke-result=".count))
            guard !path.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }
            return URL(fileURLWithPath: path)
        }
        ?? documentsURL.appendingPathComponent("reported-voice-smoke-result.json")

    func write(_ object: [String: Any]) {
        guard JSONSerialization.isValidJSONObject(object),
              let data = try? JSONSerialization.data(withJSONObject: object, options: [.prettyPrinted, .sortedKeys]) else {
            return
        }
        try? data.write(to: resultURL, options: .atomic)
    }

    guard fileManager.fileExists(atPath: audioURL.path) else {
        write([
            "status": "error",
            "error": "Smoke audio file was not found.",
            "audioPath": audioURL.path,
            "modelInstalled": OnDeviceGemmaVoiceDraftEngine.isModelInstalled()
        ])
        return
    }

    write([
        "status": "running",
        "audioPath": audioURL.path,
        "modelInstalled": OnDeviceGemmaVoiceDraftEngine.isModelInstalled()
    ])

    do {
        var state = ComposerState()
        state.latitude = 40.7128
        state.longitude = -74.0060
        let context = VoiceReportContext(
            state: state,
            complaintOptions: complaintOptionsList,
            imageAddressSuggestion: nil
        )
        let result = try await OnDeviceGemmaVoiceDraftEngine.generateDraft(
            audioURL: audioURL,
            complaintOptions: complaintOptionsList,
            voiceContext: context
        )
        write([
            "status": "success",
            "audioPath": audioURL.path,
            "modelInstalled": OnDeviceGemmaVoiceDraftEngine.isModelInstalled(),
            "transcript": result.transcript ?? NSNull(),
            "draft": voiceSmokeJsonObject(from: result.draft)
        ])
    } catch {
        write([
            "status": "error",
            "audioPath": audioURL.path,
            "modelInstalled": OnDeviceGemmaVoiceDraftEngine.isModelInstalled(),
            "error": error.localizedDescription
        ])
    }
}

private func voiceSmokeJsonObject(from draft: VoiceReportDraft) -> [String: Any] {
    [
        "complaint": draft.complaintTitle ?? NSNull(),
        "complaintId": draft.complaintId ?? NSNull(),
        "plate": draft.plate ?? NSNull(),
        "state": draft.plateRegion ?? NSNull(),
        "address": draft.address ?? NSNull(),
        "timeofincident": draft.occurredAtIso ?? NSNull(),
        "occurredAtIso": draft.occurredAtIso ?? NSNull(),
        "make": draft.make ?? NSNull(),
        "model": draft.model ?? NSNull(),
        "yearRange": draft.yearRange ?? NSNull(),
        "description": draft.description ?? NSNull(),
        "notes": draft.notes ?? NSNull()
    ]
}
#endif

private struct ImageAddressPickerRow: View {
    let suggestion: ComposerState.AddressSuggestion?
    let hasImageAddressSource: Bool
    let isRefreshing: Bool
    let onSelect: (ComposerState.AddressSuggestion) -> Void

    var body: some View {
        if let suggestion {
            Button {
                onSelect(suggestion)
            } label: {
                rowContent(
                    title: "Use address from image",
                    subtitle: suggestion.label,
                    showsProgress: false
                )
            }
            .buttonStyle(.plain)
        } else if hasImageAddressSource {
            rowContent(
                title: isRefreshing ? "Reading address from image" : "No image address found",
                subtitle: isRefreshing ? "Checking the selected photo GPS." : "This image does not include readable GPS.",
                showsProgress: isRefreshing
            )
        }
    }

    private func rowContent(title: String, subtitle: String, showsProgress: Bool) -> some View {
        HStack(spacing: 10) {
            Image(systemName: "mappin.circle.fill")
                .font(.title3.weight(.semibold))
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.subheadline.weight(.semibold))
                Text(subtitle)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
            }
            Spacer(minLength: 0)
            if showsProgress {
                ProgressView()
                    .tint(Color.reportedOrange)
            }
        }
        .foregroundStyle(Color.reportedOrange)
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.reportedOrange.opacity(0.08))
        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
    }
}

private struct AddressSearchTextField: UIViewRepresentable {
    let placeholder: String
    @Binding var text: String
    @Binding var isFirstResponder: Bool
    let onSubmit: () -> Void

    func makeUIView(context: Context) -> UITextField {
        let textField = UITextField(frame: .zero)
        textField.delegate = context.coordinator
        textField.placeholder = placeholder
        textField.borderStyle = .none
        textField.font = .preferredFont(forTextStyle: .body)
        textField.adjustsFontForContentSizeCategory = true
        textField.textColor = .label
        textField.tintColor = .label
        textField.autocapitalizationType = .words
        textField.autocorrectionType = .yes
        textField.spellCheckingType = .yes
        textField.textContentType = .fullStreetAddress
        textField.returnKeyType = .done
        textField.setContentHuggingPriority(.defaultLow, for: .horizontal)
        textField.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        textField.addTarget(context.coordinator, action: #selector(Coordinator.textDidChange(_:)), for: .editingChanged)
        return textField
    }

    func updateUIView(_ textField: UITextField, context: Context) {
        context.coordinator.parent = self
        if textField.text != text {
            textField.text = text
        }

        if isFirstResponder {
            if !textField.isFirstResponder {
                DispatchQueue.main.async {
                    guard self.isFirstResponder else { return }
                    textField.becomeFirstResponder()
                    context.coordinator.placeCaretAtStartIfNeeded(in: textField)
                }
            } else {
                context.coordinator.placeCaretAtStartIfNeeded(in: textField)
            }
        } else if textField.isFirstResponder {
            textField.resignFirstResponder()
        }
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(parent: self)
    }

    final class Coordinator: NSObject, UITextFieldDelegate {
        var parent: AddressSearchTextField
        private var shouldPlaceCaretAtStart = true

        init(parent: AddressSearchTextField) {
            self.parent = parent
        }

        @objc func textDidChange(_ textField: UITextField) {
            parent.text = textField.text ?? ""
        }

        func textFieldDidBeginEditing(_ textField: UITextField) {
            parent.isFirstResponder = true
            placeCaretAtStartIfNeeded(in: textField)
        }

        func textFieldDidEndEditing(_ textField: UITextField) {
            parent.isFirstResponder = false
        }

        func textFieldShouldReturn(_ textField: UITextField) -> Bool {
            parent.isFirstResponder = false
            parent.onSubmit()
            return false
        }

        func placeCaretAtStartIfNeeded(in textField: UITextField) {
            guard shouldPlaceCaretAtStart else { return }
            shouldPlaceCaretAtStart = false
            DispatchQueue.main.async {
                guard textField.isFirstResponder else { return }
                let start = textField.beginningOfDocument
                textField.selectedTextRange = textField.textRange(from: start, to: start)
            }
        }
    }
}

private struct AddressSearchScreen: View {
    @Environment(\.dismiss) private var dismiss
    @Binding var query: String
    let suggestions: [ComposerState.AddressSuggestion]
    let addressProvider: ReportAddressProvider
    let isLoading: Bool
    let isError: Bool
    let imageAddressSuggestion: ComposerState.AddressSuggestion?
    let hasImageAddressSource: Bool
    let isRefreshingImageAddress: Bool
    let onCancel: () -> Void
    let onClear: () -> Void
    let onOpenMap: () -> Void
    let onSelect: (ComposerState.AddressSuggestion) -> Void
    let onUseImageAddress: (ComposerState.AddressSuggestion) -> Void
    @State private var isSearchFocused = false

    var body: some View {
        VStack(spacing: 0) {
            addressSearchHeader
            Divider()
            if isLoading {
                HStack(spacing: 10) {
                    ProgressView()
                        .controlSize(.small)
                    Text(addressProvider.searchingLabel)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            }
            if query.isEmpty && (imageAddressSuggestion != nil || hasImageAddressSource) {
                ImageAddressPickerRow(
                    suggestion: imageAddressSuggestion,
                    hasImageAddressSource: hasImageAddressSource,
                    isRefreshing: isRefreshingImageAddress
                ) { suggestion in
                    onUseImageAddress(suggestion)
                    dismissActiveKeyboard()
                    dismiss()
                }
                .padding(.horizontal, 16)
                .padding(.top, 12)
                .padding(.bottom, 6)
            }
            if !isLoading && !query.isEmpty && suggestions.isEmpty {
                HStack(spacing: 10) {
                    Image(systemName: "magnifyingglass")
                        .font(.body.weight(.semibold))
                        .foregroundStyle(.secondary)
                    Text(addressProvider.noMatchesLabel)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            }
            ScrollView {
                LazyVStack(spacing: 0) {
                    ForEach(suggestions) { suggestion in
                        Button {
                            onSelect(suggestion)
                            dismissActiveKeyboard()
                            dismiss()
                        } label: {
                            HStack(spacing: 12) {
                                Image(systemName: "mappin.circle.fill")
                                    .font(.title3)
                                    .foregroundStyle(Color.reportedOrange)
                                Text(suggestion.label)
                                    .font(.body)
                                    .foregroundStyle(.primary)
                                    .multilineTextAlignment(.leading)
                                    .lineLimit(2)
                                Spacer(minLength: 0)
                            }
                            .padding(.horizontal, 16)
                            .padding(.vertical, 14)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        if suggestion.id != suggestions.last?.id {
                            Divider()
                                .padding(.leading, 50)
                        }
                    }
                }
            }
            .scrollDismissesKeyboard(.interactively)
        }
        .background(Color(.systemBackground))
        .onAppear {
            DispatchQueue.main.async {
                isSearchFocused = true
            }
        }
    }

    private var addressSearchHeader: some View {
        HStack(spacing: 10) {
            Button {
                onCancel()
                dismissActiveKeyboard()
                dismiss()
            } label: {
                Image(systemName: "chevron.left")
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(.primary)
                    .frame(width: 34, height: 42)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Back")

            HStack(spacing: 10) {
                Image(systemName: "magnifyingglass")
                    .font(.body.weight(.semibold))
                    .foregroundStyle(.secondary)
                AddressSearchTextField(
                    placeholder: "Search address",
                    text: $query,
                    isFirstResponder: $isSearchFocused
                ) {
                    dismissActiveKeyboard()
                }
                .frame(minWidth: 0, maxWidth: .infinity, minHeight: 24, maxHeight: 24)
                if !query.isEmpty {
                    Button {
                        onClear()
                        isSearchFocused = true
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.body.weight(.semibold))
                            .foregroundStyle(.secondary)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Clear address")
                }
            }
            .padding(.horizontal, 12)
            .frame(height: 46)
            .frame(maxWidth: .infinity)
            .background(Color(.secondarySystemBackground))
            .overlay(
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .stroke(isError ? Color.red : Color.clear, lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))

            Button {
                onOpenMap()
                dismissActiveKeyboard()
                dismiss()
            } label: {
                Image(systemName: "map")
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(.primary)
                    .frame(width: 34, height: 42)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Show Map")
        }
        .padding(.horizontal, 12)
        .padding(.top, 10)
        .padding(.bottom, 10)
        .background(Color(.systemBackground))
    }
}

private struct AddressMapSheet: View {
    private static let defaultCoordinate = CLLocationCoordinate2D(latitude: 40.71305, longitude: -74.00723)
    private static let defaultSpan = MKCoordinateSpan(latitudeDelta: 0.0045, longitudeDelta: 0.0045)

    let initialLatitude: Double?
    let initialLongitude: Double?
    let initialAddress: String
    let imageAddressSuggestion: ComposerState.AddressSuggestion?
    let hasImageAddressSource: Bool
    let isRefreshingImageAddress: Bool
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
        imageAddressSuggestion: ComposerState.AddressSuggestion?,
        hasImageAddressSource: Bool = false,
        isRefreshingImageAddress: Bool = false,
        onCancel: @escaping () -> Void,
        onDone: @escaping (ComposerState.AddressSuggestion) -> Void
    ) {
        self.initialLatitude = initialLatitude
        self.initialLongitude = initialLongitude
        self.initialAddress = initialAddress
        self.imageAddressSuggestion = imageAddressSuggestion
        self.hasImageAddressSource = hasImageAddressSource
        self.isRefreshingImageAddress = isRefreshingImageAddress
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

            if hasImageAddressSource || imageAddressSuggestion != nil {
                ImageAddressPickerRow(
                    suggestion: imageAddressSuggestion,
                    hasImageAddressSource: hasImageAddressSource,
                    isRefreshing: isRefreshingImageAddress
                ) { suggestion in
                    useImageAddress(suggestion)
                }
                .padding(.horizontal, 20)
            }

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

    private func useImageAddress(_ suggestion: ComposerState.AddressSuggestion) {
        guard let coordinate = Self.validCoordinate(latitude: suggestion.latitude, longitude: suggestion.longitude) else {
            return
        }
        lookupTask?.cancel()
        let region = MKCoordinateRegion(center: coordinate, span: Self.defaultSpan)
        addressCache[addressCacheKey(for: coordinate)] = suggestion
        selectedCoordinate = coordinate
        pendingSuggestion = suggestion
        resolvedAddress = suggestion.label
        lookupInFlight = false
        currentRegion = region
        position = .region(region)
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
            let suggestion = await reverseGeocodeAddress(latitude: coordinate.latitude, longitude: coordinate.longitude)
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
        coordinateAddressSuggestion(latitude: coordinate.latitude, longitude: coordinate.longitude)
    }

    private func coordinateText(_ coordinate: CLLocationCoordinate2D) -> String {
        formattedCoordinateText(latitude: coordinate.latitude, longitude: coordinate.longitude)
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
    let hasImageTimeSource: Bool
    let isRefreshingImageTime: Bool
    let onSelected: (String) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var month: Int
    @State private var day: Int
    @State private var year: Int
    @State private var hour: Int
    @State private var minute: Int
    @State private var meridiem: String

    init(
        isoValue: String,
        photoIsoValue: String?,
        hasImageTimeSource: Bool = false,
        isRefreshingImageTime: Bool = false,
        onSelected: @escaping (String) -> Void
    ) {
        self.isoValue = isoValue
        self.photoIsoValue = photoIsoValue
        self.hasImageTimeSource = hasImageTimeSource
        self.isRefreshingImageTime = isRefreshingImageTime
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
            VStack(alignment: .leading, spacing: 12) {
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
                if let photoDate {
                    Button {
                        useImageTime(photoDate)
                    } label: {
                        HStack(spacing: 10) {
                            Image(systemName: "camera")
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Use time from image")
                                    .font(.subheadline.weight(.semibold))
                                Text(photoDate.formatted(.dateTime.month(.abbreviated).day().year().hour(.defaultDigits(amPM: .abbreviated)).minute()))
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer(minLength: 0)
                        }
                        .foregroundStyle(Color.reportedOrange)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 10)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.reportedOrange.opacity(0.08))
                        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    }
                    .buttonStyle(.plain)
                } else if hasImageTimeSource {
                    HStack(spacing: 10) {
                        Image(systemName: "camera")
                        VStack(alignment: .leading, spacing: 2) {
                            Text(isRefreshingImageTime ? "Reading time from image" : "No image time found")
                                .font(.subheadline.weight(.semibold))
                            Text(isRefreshingImageTime ? "Checking the selected media metadata." : "This image does not include a readable capture time.")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        Spacer(minLength: 0)
                        if isRefreshingImageTime {
                            ProgressView()
                        }
                    }
                    .foregroundStyle(Color.reportedOrange)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.reportedOrange.opacity(0.08))
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                }
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
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 16)
    }

    private var photoDate: Date? {
        guard let photoIsoValue else { return nil }
        return Self.date(from: photoIsoValue)
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

    private func useImageTime(_ date: Date) {
        apply(date)
        onSelected(Self.isoString(from: date))
        dismiss()
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
        VStack(alignment: .leading, spacing: reportedFieldLabelSpacing) {
            ReportedFieldLabel(title: title, isError: isError)
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

private enum ReportLongTextField: String, Identifiable {
    case description
    case notes

    var id: String { rawValue }

    var title: String {
        switch self {
        case .description: return "Description (public facing)"
        case .notes: return "Notes (for your records)"
        }
    }

    var placeholder: String {
        switch self {
        case .description: return "Describe what happened"
        case .notes: return "Add private notes"
        }
    }
}

private struct ReportVerifyFields: View {
    let complaintValue: String
    let plateValue: String
    let plateCandidateCount: Int
    let plateRegionValue: String
    let vehicleLookupDetails: VehicleLookupDetails? = nil
    let vehicleLookupInFlight: Bool = false
    let vehicleLookupMessage: String? = nil
    let addressValue: String
    let occurredAtValue: String
    let validationErrors: ComposerState.ValidationErrors
    @Binding var descriptionText: String
    @Binding var notesText: String
    let philadelphiaDetails: PhiladelphiaMobilityAccessDetails?
    let isLandscape: Bool
    let isTablet: Bool
    var usesCompactFieldRow = false
    let onComplaintTapped: () -> Void
    let onPlateTapped: () -> Void
    let onStateTapped: () -> Void
    let onAddressTapped: () -> Void
    let onAddressClear: (() -> Void)?
    let onAddressMapTapped: (() -> Void)?
    let onOccurredAtTapped: () -> Void
    var onDescriptionTapped: (() -> Void)? = nil
    var onNotesTapped: (() -> Void)? = nil
    var onPhiladelphiaMobilityAccessChanged: (PhiladelphiaMobilityAccessDetails) -> Void
    var onDescriptionChanged: (() -> Void)? = nil
    var onNotesChanged: (() -> Void)? = nil

    var body: some View {
        let fieldSpacing: CGFloat = usesCompactFieldRow ? 8 : 12
        let complaintMaxWidth: CGFloat = isLandscape ? .infinity : (isTablet ? 312 : (usesCompactFieldRow ? 104 : 156))
        let complaintMinWidth: CGFloat = isLandscape ? 160 : (isTablet ? 260 : (usesCompactFieldRow ? 92 : 130))
        let plateWidth: CGFloat = isLandscape || isTablet ? 150 : (usesCompactFieldRow ? 120 : 134)
        let stateWidth: CGFloat = isLandscape || isTablet ? 76 : (usesCompactFieldRow ? 48 : 62)
        let multilineFieldMinHeight: CGFloat? = isLandscape || isTablet ? 104 : nil

        HStack(alignment: .top, spacing: fieldSpacing) {
            ComplaintPickerField(
                value: complaintValue,
                isError: validationErrors.complaint != nil,
                action: onComplaintTapped
            )
            .frame(minWidth: complaintMinWidth, maxWidth: complaintMaxWidth)
            PlateInputField(
                text: plateValue,
                isError: validationErrors.plate != nil,
                candidateCount: plateCandidateCount,
                onEdit: onPlateTapped
            )
            .frame(width: plateWidth)
            PickerField(
                title: "State",
                value: plateRegionValue,
                isError: validationErrors.plateRegion != nil,
                showsChevron: false,
                alignment: .center,
                action: onStateTapped
            )
            .frame(width: stateWidth)
        }
        FieldErrorGroup([
            validationErrors.complaint,
            validationErrors.plate,
            validationErrors.plateRegion
        ])
        vehicleLookupStatus
        if isLandscape {
            HStack(alignment: .top, spacing: 12) {
                addressField
                occurredAtField
            }
        } else {
            addressField
            occurredAtField
        }
        if let philadelphiaDetails {
            descriptionField(multilineFieldMinHeight)
            PhiladelphiaMobilityAccessFields(
                details: philadelphiaDetails,
                isLandscape: isLandscape || isTablet,
                onChanged: onPhiladelphiaMobilityAccessChanged
            )
            notesField(multilineFieldMinHeight)
        } else if isLandscape {
            HStack(alignment: .top, spacing: 12) {
                descriptionField(multilineFieldMinHeight)
                notesField(multilineFieldMinHeight)
            }
        } else {
            descriptionField(multilineFieldMinHeight)
            notesField(multilineFieldMinHeight)
        }
    }

    @ViewBuilder
    private var vehicleLookupStatus: some View {
        if vehicleLookupInFlight || vehicleLookupDetails != nil || vehicleLookupMessage != nil {
            HStack(spacing: 10) {
                if vehicleLookupInFlight {
                    ProgressView()
                        .controlSize(.small)
                }
                VStack(alignment: .leading, spacing: 2) {
                    if vehicleLookupInFlight {
                        Text("Looking up vehicle details for \(plateValue) in \(plateRegionValue)…")
                    } else if let details = vehicleLookupDetails {
                        Text(details.summary)
                        Text("Vehicle details from LookupAPlate")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    } else if let vehicleLookupMessage {
                        Text(vehicleLookupMessage)
                    }
                }
                .font(.subheadline)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .background(Color.secondary.opacity(0.1))
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
    }

    private var addressField: some View {
        ReportAddressPickerField(
            value: addressValue,
            isError: validationErrors.address != nil,
            onSearch: onAddressTapped,
            onClear: addressValue.isEmpty ? nil : onAddressClear,
            onMap: onAddressMapTapped
        )
    }

    private var occurredAtField: some View {
        PickerField(
            title: "Occurred At",
            value: occurredAtValue,
            isError: validationErrors.occurredAt != nil,
            action: onOccurredAtTapped
        )
    }

    private func descriptionField(_ minHeight: CGFloat?) -> some View {
        Group {
            if let onDescriptionTapped {
                MultilineSelectionField(
                    title: ReportLongTextField.description.title,
                    value: descriptionText,
                    placeholder: ReportLongTextField.description.placeholder,
                    minHeight: minHeight ?? 62,
                    action: onDescriptionTapped
                )
            } else {
                InputField(
                    title: ReportLongTextField.description.title,
                    text: $descriptionText,
                    fieldMinHeight: minHeight
                )
            }
        }
        .onChange(of: descriptionText) { _, _ in onDescriptionChanged?() }
    }

    private func notesField(_ minHeight: CGFloat?) -> some View {
        Group {
            if let onNotesTapped {
                MultilineSelectionField(
                    title: ReportLongTextField.notes.title,
                    value: notesText,
                    placeholder: ReportLongTextField.notes.placeholder,
                    minHeight: minHeight ?? 62,
                    action: onNotesTapped
                )
            } else {
                InputField(
                    title: ReportLongTextField.notes.title,
                    text: $notesText,
                    fieldMinHeight: minHeight
                )
            }
        }
        .onChange(of: notesText) { _, _ in onNotesChanged?() }
    }
}

private struct PhiladelphiaMobilityAccessFields: View {
    let details: PhiladelphiaMobilityAccessDetails
    let isLandscape: Bool
    let onChanged: (PhiladelphiaMobilityAccessDetails) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Philadelphia mobility access")
                .font(.headline)
                .foregroundStyle(Color.reportedOrange)
                .padding(.horizontal, reportedFieldLabelHorizontalInset)

            fieldRow {
                InputField(
                    title: "Block Number",
                    text: Binding(get: { details.blockNumber }, set: { update(blockNumber: $0) }),
                    keyboardType: .numberPad,
                    autocapitalizationType: .none,
                    autocorrectionDisabled: true
                )
                PhiladelphiaOptionMenuField(
                    title: "Zip Code",
                    value: details.zipCode,
                    options: ppaPhiladelphiaZipCodes,
                    onSelected: { update(zipCode: $0) }
                )
            }

            InputField(
                title: "Street Name",
                text: Binding(get: { details.streetName }, set: { update(streetName: $0) }),
                autocapitalizationType: .words
            )

            fieldRow {
                PhiladelphiaAutocompleteField(
                    title: "Vehicle Make",
                    value: details.vehicleMake,
                    options: ppaCatalogArray(PhiladelphiaMobilityAccessCatalogs.shared.vehicleMakes),
                    onChanged: { update(vehicleMake: $0) }
                )
                PhiladelphiaAutocompleteField(
                    title: "Vehicle Model",
                    value: details.vehicleModel,
                    options: ppaVehicleModelSuggestions,
                    onChanged: { update(vehicleModel: $0) }
                )
            }

            fieldRow {
                PhiladelphiaOptionMenuField(
                    title: "Body Style",
                    value: details.bodyStyle,
                    options: ppaCatalogArray(PhiladelphiaMobilityAccessCatalogs.shared.bodyStyles),
                    onSelected: { update(bodyStyle: $0) }
                )
                PhiladelphiaOptionMenuField(
                    title: "Vehicle Color",
                    value: details.vehicleColor,
                    options: ppaCatalogArray(PhiladelphiaMobilityAccessCatalogs.shared.vehicleColors),
                    onSelected: { update(vehicleColor: $0) }
                )
            }

            PhiladelphiaOptionMenuField(
                title: "Violation Observed",
                value: details.violationObserved,
                options: ppaCatalogArray(PhiladelphiaMobilityAccessCatalogs.shared.violationObservedOptions),
                onSelected: { update(violationObserved: $0) }
            )
            PhiladelphiaOptionMenuField(
                title: "How Frequently Does This Occur?",
                value: details.frequency,
                options: ppaCatalogArray(PhiladelphiaMobilityAccessCatalogs.shared.frequencyOptions),
                onSelected: { update(frequency: $0) }
            )
        }
    }

    @ViewBuilder
    private func fieldRow<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        if isLandscape {
            HStack(alignment: .top, spacing: 12, content: content)
        } else {
            VStack(alignment: .leading, spacing: 10, content: content)
        }
    }

    private func update(
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
        onChanged(PhiladelphiaMobilityAccessDetails(
            blockNumber: blockNumber ?? details.blockNumber,
            streetName: streetName ?? details.streetName,
            zipCode: zipCode ?? details.zipCode,
            vehicleMake: vehicleMake ?? details.vehicleMake,
            vehicleModel: vehicleModel ?? details.vehicleModel,
            bodyStyle: bodyStyle ?? details.bodyStyle,
            vehicleColor: vehicleColor ?? details.vehicleColor,
            violationObserved: violationObserved ?? details.violationObserved,
            frequency: frequency ?? details.frequency
        ))
    }
}

private struct PhiladelphiaAutocompleteField: View {
    let title: String
    let value: String
    let options: [String]
    let onChanged: (String) -> Void
    @FocusState private var focused: Bool

    private var suggestions: [String] {
        let query = value.trimmingCharacters(in: .whitespacesAndNewlines)
        let matches = query.isEmpty ? options : options.filter { $0.localizedCaseInsensitiveContains(query) }
        return Array(matches.removingCaseInsensitiveDuplicates().prefix(6))
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            InputField(
                title: title,
                text: Binding(get: { value }, set: onChanged),
                autocapitalizationType: .words
            )
            .focused($focused)

            if focused && !suggestions.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(suggestions, id: \.self) { suggestion in
                            Button(suggestion) {
                                onChanged(suggestion)
                                focused = false
                            }
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(Color.reportedOrange)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 7)
                            .background(Color.reportedOrange.opacity(0.10))
                            .clipShape(Capsule())
                        }
                    }
                    .padding(.horizontal, 2)
                }
            }
        }
    }
}

private struct PhiladelphiaOptionMenuField: View {
    let title: String
    let value: String
    let options: [String]
    let onSelected: (String) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: reportedFieldLabelSpacing) {
            ReportedFieldLabel(title: title)
            Menu {
                ForEach(options, id: \.self) { option in
                    Button(option) { onSelected(option) }
                }
            } label: {
                HStack(spacing: 6) {
                    AutoFitFieldText(value.isEmpty ? "Select" : value)
                        .foregroundStyle(value.isEmpty ? Color(.placeholderText) : .primary)
                    Image(systemName: "chevron.down")
                        .foregroundStyle(.secondary)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 11)
                .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                .background(Color(.systemBackground))
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(Color(.separator), lineWidth: 1)
                )
                .clipShape(RoundedRectangle(cornerRadius: 8))
            }
        }
    }
}

private func ppaCatalogArray(_ value: Any) -> [String] {
    if let strings = value as? [String] {
        return strings
    }
    if let array = value as? NSArray {
        return array.compactMap { $0 as? String }
    }
    return []
}

private let ppaPhiladelphiaZipCodes = [
    "19102", "19103", "19104", "19106", "19107", "19111", "19112", "19114",
    "19115", "19116", "19118", "19119", "19120", "19121", "19122", "19123",
    "19124", "19125", "19126", "19127", "19128", "19129", "19130", "19131",
    "19132", "19133", "19134", "19135", "19136", "19137", "19138", "19139",
    "19140", "19141", "19142", "19143", "19144", "19145", "19146", "19147",
    "19148", "19149", "19150", "19151", "19152", "19153", "19154"
]

private let ppaVehicleModelSuggestions = [
    "3 Series", "5 Series", "Accord", "Acadia", "Altima", "Atlas", "Bolt", "Bronco",
    "Camaro", "Camry", "Canyon", "Civic", "Colorado", "Corolla", "CR-V", "CX-5",
    "CX-30", "CX-50", "Edge", "Elantra", "Equinox", "Escape", "Explorer", "F-150",
    "Focus", "Forester", "Forte", "Frontier", "Grand Caravan", "Grand Cherokee",
    "Highlander", "HR-V", "Impala", "Jetta", "K5", "Leaf", "Malibu", "Maxima",
    "Model 3", "Model S", "Model X", "Model Y", "Murano", "Odyssey", "Optima",
    "Outback", "Palisade", "Pathfinder", "Pilot", "Prius", "RAV4", "Rogue",
    "Santa Fe", "Savana", "Sentra", "Sienna", "Sierra", "Silverado", "Sonata",
    "Soul", "Sportage", "Suburban", "Tacoma", "Tahoe", "Telluride", "Terrain",
    "Tiguan", "Transit", "Traverse", "Tucson", "Tundra", "Versa", "Wrangler",
    "X1", "X3", "X5", "XC40", "XC60", "XC90", "Yukon"
]

private extension Array where Element == String {
    func removingCaseInsensitiveDuplicates() -> [String] {
        var seen = Set<String>()
        return filter { value in
            let key = value.lowercased()
            if seen.contains(key) {
                return false
            }
            seen.insert(key)
            return true
        }
    }
}

private struct MultilineSelectionField: View {
    let title: String
    let value: String
    let placeholder: String
    let minHeight: CGFloat
    let action: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: reportedFieldLabelSpacing) {
            ReportedFieldLabel(title: title)
            Button(action: action) {
                HStack(alignment: .top, spacing: 10) {
                    Text(value.isEmpty ? placeholder : value)
                        .font(.body)
                        .foregroundStyle(value.isEmpty ? Color(.placeholderText) : .primary)
                        .lineLimit(4)
                        .multilineTextAlignment(.leading)
                        .frame(maxWidth: .infinity, alignment: .topLeading)
                    Image(systemName: "square.and.pencil")
                        .font(.body.weight(.semibold))
                        .foregroundStyle(Color.reportedOrange)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 11)
                .frame(minHeight: minHeight, alignment: .topLeading)
                .frame(maxWidth: .infinity, alignment: .topLeading)
                .background(Color(.systemBackground))
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(Color(.separator), lineWidth: 1)
                )
                .clipShape(RoundedRectangle(cornerRadius: 8))
                .contentShape(RoundedRectangle(cornerRadius: 8))
            }
            .buttonStyle(.plain)
        }
    }
}

private struct FullScreenReportTextEditor: View {
    let title: String
    let placeholder: String
    @Binding var text: String
    let onDone: () -> Void
    @FocusState private var focused: Bool

    var body: some View {
        NavigationStack {
            ZStack(alignment: .topLeading) {
                TextEditor(text: $text)
                    .focused($focused)
                    .font(.body)
                    .textInputAutocapitalization(.sentences)
                    .autocorrectionDisabled(false)
                    .padding(16)
                    .scrollContentBackground(.hidden)
                    .background(Color(.systemBackground))

                if text.isEmpty {
                    Text(placeholder)
                        .font(.body)
                        .foregroundStyle(Color(.placeholderText))
                        .padding(.horizontal, 21)
                        .padding(.vertical, 24)
                        .allowsHitTesting(false)
                }
            }
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done", action: onDone)
                        .font(.body.weight(.semibold))
                        .foregroundStyle(Color.reportedOrange)
                }
            }
        }
        .onAppear {
            focused = true
        }
    }
}

private struct ReportAddressPickerField: View {
    let value: String
    let isError: Bool
    let onSearch: () -> Void
    let onClear: (() -> Void)?
    let onMap: (() -> Void)?

    var body: some View {
        VStack(alignment: .leading, spacing: reportedFieldLabelSpacing) {
            ReportedFieldLabel(title: "Address", isError: isError)
            HStack(spacing: 10) {
                Button(action: onSearch) {
                    HStack(spacing: 10) {
                        Image(systemName: "magnifyingglass")
                            .font(.body.weight(.semibold))
                            .foregroundStyle(.secondary)
                        Text(value.isEmpty ? "Search address" : value)
                            .foregroundStyle(value.isEmpty ? Color(.placeholderText) : .primary)
                            .lineLimit(1)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                if let onClear {
                    Button(action: onClear) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.body.weight(.semibold))
                            .foregroundStyle(.secondary)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Clear address")
                }
                if let onMap {
                    Button(action: onMap) {
                        Image(systemName: "map")
                            .font(.title3.weight(.semibold))
                            .foregroundStyle(.primary)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Show Map")
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 11)
            .frame(minHeight: 46)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color(.systemBackground))
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .stroke(isError ? Color.red : Color(.separator), lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
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

enum ReportedButtonSize {
    case regular
    case compact

    var font: Font {
        switch self {
        case .regular:
            return .body.weight(.semibold)
        case .compact:
            return .callout.weight(.semibold)
        }
    }

    var verticalPadding: CGFloat {
        switch self {
        case .regular:
            return 14
        case .compact:
            return 10
        }
    }
}

struct PrimaryButton: View {
    let title: String
    var size: ReportedButtonSize = .regular
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(size.font)
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, size.verticalPadding)
        }
        .buttonStyle(.borderedProminent)
        .tint(Color.reportedOrange)
    }
}

struct SecondaryButton: View {
    let title: String
    var size: ReportedButtonSize = .regular
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(size.font)
                .foregroundStyle(Color.reportedOrange)
                .frame(maxWidth: .infinity)
                .padding(.vertical, size.verticalPadding)
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
