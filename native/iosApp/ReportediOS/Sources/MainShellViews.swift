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

enum ReportTutorialSettings {
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

extension MainShellDestination {
    var requiresAuthorization: Bool {
        switch self {
        case .reports, .profile:
            return true
        case .report, .autoReport, .settings:
            return false
        }
    }
}

struct MainShellToolbar: View {
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

struct SystemNoticeBanner: View {
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

struct AnimatedSparkleIcon: View {
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

struct ReportedAiModelState {
    var isInstalled = false
    var isDownloading = false
    var progress: Double?
    var message: String?
    var statusText = "Not installed"
    var accelerationMessage = ""
}

enum ReportedAiModelAction {
    case refresh
    case install
}

@MainActor
final class ReportedAiModelDownloadController: ObservableObject, UdfStore {
    static let shared = ReportedAiModelDownloadController()

    @Published private(set) var state: ReportedAiModelState

    private var downloadTask: Task<Void, Never>?

    private init() {
        state = Self.currentState()
    }

    func onAction(_ action: ReportedAiModelAction) {
        switch action {
        case .refresh:
            refresh()
        case .install:
            install()
        }
    }

    private func refresh() {
        state = Self.currentState(
            isDownloading: state.isDownloading,
            progress: state.progress,
            message: state.message
        )
    }

    private func install() {
        guard !state.isDownloading else { return }
        refresh()
        guard !state.isInstalled else { return }

        state.isDownloading = true
        state.progress = nil
        state.message = nil
        downloadTask = Task { [weak self] in
            guard let self else { return }
            do {
                try await OnDeviceGemmaVoiceDraftEngine.downloadModel { downloadedBytes, totalBytes in
                    Task { @MainActor [weak self] in
                        guard let self else { return }
                        if totalBytes > 0 {
                            state.progress = min(1, max(0, Double(downloadedBytes) / Double(totalBytes)))
                        } else {
                            state.progress = nil
                        }
                    }
                }
                let installed = OnDeviceGemmaVoiceDraftEngine.isModelInstalled()
                state = Self.currentState(
                    progress: installed ? 1 : nil,
                    message: installed ? "REPORTED AI installed." : "REPORTED AI did not finish installing."
                )
            } catch {
                state = Self.currentState(message: error.localizedDescription)
            }
            downloadTask = nil
        }
    }

    private static func currentState(
        isDownloading: Bool = false,
        progress: Double? = nil,
        message: String? = nil
    ) -> ReportedAiModelState {
        ReportedAiModelState(
            isInstalled: OnDeviceGemmaVoiceDraftEngine.isModelInstalled(),
            isDownloading: isDownloading,
            progress: progress,
            message: message,
            statusText: OnDeviceGemmaVoiceDraftEngine.settingsStatusText,
            accelerationMessage: OnDeviceGemmaVoiceDraftEngine.accelerationMessage
        )
    }
}

struct ReportedAiInstallPanel: View {
    @ObservedObject private var controller = ReportedAiModelDownloadController.shared
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var sparkleArrived = false

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .center, spacing: 10) {
                AnimatedSparkleIcon(size: 20)
                    .frame(width: 36, height: 36)
                    .background(Color.reportedOrange.opacity(0.14))
                    .overlay(
                        Circle()
                            .stroke(Color.reportedOrange.opacity(0.35), lineWidth: 1)
                    )
                    .clipShape(Circle())
                    .offset(x: sparkleArrived || reduceMotion ? 0 : 260)
                    .opacity(sparkleArrived || reduceMotion ? 1 : 0)

                VStack(alignment: .leading, spacing: 2) {
                    Text("Reported AI")
                        .font(.headline)
                    Text(controller.state.statusText)
                        .font(.subheadline.weight(.semibold))
                }

                Spacer(minLength: 0)
            }

            if !controller.state.accelerationMessage.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                Text(controller.state.accelerationMessage)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }

            if controller.state.isDownloading {
                if let progress = controller.state.progress {
                    ProgressView(value: progress)
                        .progressViewStyle(.linear)
                    Text("Installing REPORTED AI… \(Int(progress * 100))%")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                } else {
                    HStack(spacing: 10) {
                        ProgressView()
                        Text("Installing REPORTED AI…")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                }
            } else if !controller.state.isInstalled {
                Text("Install the optional on-device model to use Reported AI without sending report details to an AI service.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)

                Button {
                    controller.onAction(.install)
                } label: {
                    Label(
                        "Install REPORTED AI (\(OnDeviceGemmaVoiceDraftEngine.modelDownloadSizeLabel))",
                        systemImage: "arrow.down.circle.fill"
                    )
                    .font(.subheadline.weight(.semibold))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
                }
                .buttonStyle(.borderedProminent)
                .tint(Color.reportedOrange)
            }

            if let message = controller.state.message {
                Text(message)
                    .font(.subheadline)
                    .foregroundStyle(controller.state.isInstalled ? Color.secondary : Color.red)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.reportedOrange.opacity(0.08))
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(Color.reportedOrange.opacity(0.28), lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        .onAppear {
            controller.onAction(.refresh)
            sparkleArrived = reduceMotion
            guard !reduceMotion else { return }
            DispatchQueue.main.async {
                withAnimation(.spring(response: 0.7, dampingFraction: 0.76).delay(0.12)) {
                    sparkleArrived = true
                }
            }
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
