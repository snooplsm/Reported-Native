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

struct SettingsFeatureState {
    var mediaScannerFeatureEnabled = false
    var offlineProcessingFeatureEnabled = false
    var mediaScannerEnabled = false
    var notificationsEnabled = false
    var offlineProcessingEnabled = false
    var autoReportPlateThreshold = IOSMediaScannerSettings.defaultAutoReportConfidenceThreshold
    var autoReportStateThreshold = IOSMediaScannerSettings.defaultAutoReportConfidenceThreshold
    var autoReportComplaintThreshold = IOSMediaScannerSettings.defaultAutoReportConfidenceThreshold

    var confidenceThresholdSummary: String {
        reportedLocalizedFormat(
            "Thresholds: plate %@+, state %@+, infraction %@+.",
            autoReportPercent(autoReportPlateThreshold),
            autoReportPercent(autoReportStateThreshold),
            autoReportPercent(autoReportComplaintThreshold)
        )
    }
}

enum SettingsFeatureAction {
    case load
    case remoteConfigUpdated
    case mediaScannerChanged(Bool)
    case notificationsChanged(Bool)
    case offlineProcessingChanged(Bool)
    case plateThresholdChanged(Double)
    case stateThresholdChanged(Double)
    case complaintThresholdChanged(Double)
    case resetThresholds
}

@MainActor
final class SettingsFeatureViewModel: ObservableObject, UdfStore {
    @Published private(set) var state = SettingsFeatureState()
    private var scannerTask: Task<Void, Never>?

    func onAction(_ action: SettingsFeatureAction) {
        switch action {
        case .load, .remoteConfigUpdated:
            refresh()
        case .mediaScannerChanged(let enabled):
            setMediaScannerEnabled(enabled)
        case .notificationsChanged(let enabled):
            IOSMediaScannerSettings.notificationsEnabled = enabled
            state.notificationsEnabled = IOSMediaScannerSettings.notificationsEnabled
        case .offlineProcessingChanged(let enabled):
            IOSMediaScannerSettings.isOfflineProcessingEnabled = enabled
            state.offlineProcessingEnabled = IOSMediaScannerSettings.isOfflineProcessingEnabled
        case .plateThresholdChanged(let value):
            IOSMediaScannerSettings.autoReportPlateConfidenceThreshold = value
            IOSMediaScannerSettings.autoReportPostInferencePlateConfidenceThreshold = value
            state.autoReportPlateThreshold = IOSMediaScannerSettings.autoReportPlateConfidenceThreshold
        case .stateThresholdChanged(let value):
            IOSMediaScannerSettings.autoReportStateConfidenceThreshold = value
            state.autoReportStateThreshold = IOSMediaScannerSettings.autoReportStateConfidenceThreshold
        case .complaintThresholdChanged(let value):
            IOSMediaScannerSettings.autoReportComplaintConfidenceThreshold = value
            state.autoReportComplaintThreshold = IOSMediaScannerSettings.autoReportComplaintConfidenceThreshold
        case .resetThresholds:
            IOSMediaScannerSettings.resetAutoReportConfidenceThresholds()
            refresh()
        }
    }

    private func refresh() {
        state = SettingsFeatureState(
            mediaScannerFeatureEnabled: RemoteConfigOverrides.shared.enableMediaScanner,
            offlineProcessingFeatureEnabled: RemoteConfigOverrides.shared.enableOfflinePhotoProcessing,
            mediaScannerEnabled: IOSMediaScannerSettings.isEnabled,
            notificationsEnabled: IOSMediaScannerSettings.notificationsEnabled,
            offlineProcessingEnabled: IOSMediaScannerSettings.isOfflineProcessingEnabled,
            autoReportPlateThreshold: IOSMediaScannerSettings.autoReportPlateConfidenceThreshold,
            autoReportStateThreshold: IOSMediaScannerSettings.autoReportStateConfidenceThreshold,
            autoReportComplaintThreshold: IOSMediaScannerSettings.autoReportComplaintConfidenceThreshold
        )
    }

    private func setMediaScannerEnabled(_ enabled: Bool) {
        scannerTask?.cancel()
        if enabled {
            scannerTask = Task { [weak self] in
                let resolved = await IOSMediaScanner.shared.enableScannerFromSettings()
                guard !Task.isCancelled else { return }
                self?.state.mediaScannerEnabled = resolved
                self?.scannerTask = nil
            }
        } else {
            IOSMediaScanner.shared.disableScannerFromSettings()
            state.mediaScannerEnabled = false
            scannerTask = nil
        }
    }
}

struct SettingsScreen: View {
    @StateObject private var viewModel = ProfileViewModel()
    @StateObject private var settingsViewModel = SettingsFeatureViewModel()
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
                    ReportedAiInstallPanel()
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Auto-Report")
                            .font(.headline)
                        Text("Confidence thresholds")
                            .font(.subheadline.weight(.semibold))
                        Text(settingsViewModel.state.confidenceThresholdSummary)
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
                                get: { settingsViewModel.state.autoReportPlateThreshold },
                                set: { settingsViewModel.onAction(.plateThresholdChanged($0)) }
                            )
                        )
                        SettingsConfidenceThresholdSlider(
                            title: "State",
                            value: Binding(
                                get: { settingsViewModel.state.autoReportStateThreshold },
                                set: { settingsViewModel.onAction(.stateThresholdChanged($0)) }
                            )
                        )
                        SettingsConfidenceThresholdSlider(
                            title: "Infraction",
                            value: Binding(
                                get: { settingsViewModel.state.autoReportComplaintThreshold },
                                set: { settingsViewModel.onAction(.complaintThresholdChanged($0)) }
                            )
                        )
                        Button("Reset thresholds") {
                            settingsViewModel.onAction(.resetThresholds)
                        }
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.reportedOrange)
                    }
                    if settingsViewModel.state.mediaScannerFeatureEnabled || settingsViewModel.state.offlineProcessingFeatureEnabled {
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
            settingsViewModel.onAction(.load)
        }
        .onReceive(NotificationCenter.default.publisher(for: .reportedRemoteConfigUpdated)) { _ in
            settingsViewModel.onAction(.remoteConfigUpdated)
        }
    }

    @ViewBuilder
    private func mediaSettingsContent(isLandscape: Bool) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Media")
                .font(.headline)
            if isLandscape {
                HStack(alignment: .top, spacing: 12) {
                    if settingsViewModel.state.offlineProcessingFeatureEnabled {
                        offlineProcessingToggle
                    }
                    if settingsViewModel.state.mediaScannerFeatureEnabled {
                        scannerNotificationsToggle
                    }
                }
                if settingsViewModel.state.mediaScannerFeatureEnabled {
                    mediaScannerToggle
                }
            } else {
                if settingsViewModel.state.offlineProcessingFeatureEnabled {
                    offlineProcessingToggle
                }
                if settingsViewModel.state.mediaScannerFeatureEnabled {
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
                get: { settingsViewModel.state.offlineProcessingEnabled },
                set: { settingsViewModel.onAction(.offlineProcessingChanged($0)) }
            )
        )
    }

    private var mediaScannerToggle: some View {
        SettingsToggleRow(
            title: "Media scanner",
            description: "Background photo library scanning is feature flagged and defaults off.",
            isOn: Binding(
                get: { settingsViewModel.state.mediaScannerEnabled },
                set: { settingsViewModel.onAction(.mediaScannerChanged($0)) }
            )
        )
    }

    private var scannerNotificationsToggle: some View {
        SettingsToggleRow(
            title: "Scanner notifications",
            description: "Show a notification when Reported finds a likely report candidate.",
            isOn: Binding(
                get: { settingsViewModel.state.notificationsEnabled },
                set: { settingsViewModel.onAction(.notificationsChanged($0)) }
            )
        )
    }

    private func setTheme(_ mode: AppThemeMode) {
        viewModel.onAction(.themeModeChanged(mode))
        onThemeModeSelected(mode)
    }

}

struct NewReportTutorialSheet: View {
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

struct ReportTutorialPage: View {
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
            Text(reportedLocalized(title))
                .font(.title2.bold())
                .multilineTextAlignment(.center)
            Text(reportedLocalized(bodyText))
                .font(.body)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(.horizontal, 6)
    }
}

struct ReportTutorialScannerPage: View {
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

struct SettingsConfidenceThresholdSlider: View {
    let title: String
    @Binding var value: Double

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(reportedLocalized(title))
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

struct SettingsToggleRow: View {
    let title: String
    let description: String
    @Binding var isOn: Bool

    var body: some View {
        HStack(alignment: .center, spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(reportedLocalized(title))
                    .font(.subheadline.weight(.semibold))
                Text(reportedLocalized(description))
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 8)
            Toggle(reportedLocalized(title), isOn: $isOn)
                .labelsHidden()
                .tint(Color.reportedOrange)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}
