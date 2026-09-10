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

struct AutoReportIncident: Identifiable {
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

let autoReportMaxLicensePlateLength = 10

struct AutoReportScanWindow: Identifiable, Hashable {
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

struct AutoReportState {
    var scanning = false
    var submitting = false
    var processed = 0
    var total = 0
    var incidents: [AutoReportIncident] = []
    var processedPhotos: [IOSAutoReportProcessedPhoto] = []
    var scanWindow = AutoReportScanWindow.sevenDays
    var message: String?
    var showingReviewSheet = false
    var scanStartedAt: Date?
}

enum AutoReportAction {
    case scanWindowChanged(AutoReportScanWindow)
    case startScan
    case cancelScan
    case reviewVisibilityChanged(Bool)
    case incidentsChanged([AutoReportIncident])
    case submitSelected(isAuthorized: Bool)
    case effectHandled
}

enum AutoReportEffectValue {
    case requireLogin
    case reportSubmitted(String)
}

struct AutoReportEffect: Identifiable {
    let id = UUID()
    let value: AutoReportEffectValue
}

@MainActor
final class AutoReportViewModel: ObservableObject, UdfStore {
    @Published private(set) var state = AutoReportState()
    @Published private(set) var effect: AutoReportEffect?
    private var scanTask: Task<Void, Never>?

    func onAction(_ action: AutoReportAction) {
        switch action {
        case .scanWindowChanged(let window):
            guard !state.scanning, !state.submitting else { return }
            state.scanWindow = window
        case .startScan:
            startScan()
        case .cancelScan:
            cancelScan()
        case .reviewVisibilityChanged(let isPresented):
            state.showingReviewSheet = isPresented
        case .incidentsChanged(let incidents):
            state.incidents = incidents
        case .submitSelected(let isAuthorized):
            submitSelected(isAuthorized: isAuthorized)
        case .effectHandled:
            effect = nil
        }
    }

    private func startScan() {
        scanTask?.cancel()
        state.scanStartedAt = Date()
        ReportedAnalytics.logAutoReportScanStarted(scanWindow: state.scanWindow.id)
        state.scanning = true
        state.showingReviewSheet = false
        state.message = nil
        state.processed = 0
        state.total = 0
        state.incidents = []
        state.processedPhotos = []
        let selectedWindow = state.scanWindow
        scanTask = Task { [weak self] in
            guard let self else { return }
            let summary = await IOSMediaScanner.shared.runAutoReportScan(lookback: selectedWindow.lookback) { processed, total in
                Task { @MainActor [weak self] in
                    self?.state.processed = processed
                    self?.state.total = total
                }
            }
            guard !Task.isCancelled else { return }
            scanTask = nil
            state.scanning = false
            state.processed = summary.scannedCount
            state.total = summary.scannedCount
            state.incidents = buildAutoReportIncidents(from: summary.matches)
            state.processedPhotos = summary.processedPhotos
            if !summary.photoAccessGranted {
                state.message = "Photo library permission is needed to scan \(selectedWindow.sentenceLabel) without opening the gallery."
            } else if summary.scannedCount == 0 {
                state.message = "No new photos from \(selectedWindow.sentenceLabel) were available to scan."
            } else if state.incidents.isEmpty {
                state.message = "Scanned \(summary.scannedCount) photo\(summary.scannedCount == 1 ? "" : "s") and did not find a blocked bike lane or blocked crosswalk report to review."
            } else {
                state.message = nil
            }
            if summary.photoAccessGranted && summary.scannedCount > 0 {
                DispatchQueue.main.async { [weak self] in
                    self?.state.showingReviewSheet = true
                }
            }
        }
    }

    private func cancelScan() {
        scanTask?.cancel()
        scanTask = nil
        state.scanning = false
        state.scanStartedAt = nil
        state.processed = 0
        state.total = 0
        state.message = "Photo scan cancelled."
    }

    private func submitSelected(isAuthorized: Bool) {
        let selectedIncidents = state.incidents.filter { $0.selected && $0.good }
        guard !selectedIncidents.isEmpty else { return }
        let selectedMediaCount = selectedIncidents.reduce(0) { $0 + $1.media.count }
        let selectedComplaintCount = selectedIncidents.reduce(0) { $0 + ($1.complaintId.isEmpty ? 0 : 1) }
        let invalidCount = selectedIncidents.filter { !$0.isSubmittable }.count
        let scanToSubmitMillis = state.scanStartedAt.map { max(0, Int64(Date().timeIntervalSince($0) * 1000)) }
        ReportedAnalytics.logAutoReportSummarySubmitTapped(
            reportCount: selectedIncidents.count,
            mediaCount: selectedMediaCount,
            complaintCount: selectedComplaintCount,
            invalidCount: invalidCount,
            isAuthorized: isAuthorized,
            scanToSubmitMillis: scanToSubmitMillis
        )
        guard invalidCount == 0 else {
            state.message = "\(invalidCount) kept auto-report\(invalidCount == 1 ? "" : "s") need edits before submission."
            state.showingReviewSheet = true
            return
        }
        guard isAuthorized else {
            effect = AutoReportEffect(value: .requireLogin)
            return
        }
        ReportedAnalytics.logReportedAiBulkSubmit(
            surface: "auto_report_review",
            reportCount: selectedIncidents.count,
            mediaCount: selectedMediaCount,
            complaintCount: selectedComplaintCount,
            scanToSubmitMillis: scanToSubmitMillis
        )
        state.submitting = true
        state.message = nil
        Task { [weak self] in
            guard let self else { return }
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
                    let mediaFiles = try await ParseMediaUploader.uploadAll(incident.media)
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
                    PersistentMediaStore.deleteStoredMedia(incident.media.map(\.fileURL))
                    incident.infractions.forEach { IOSDetectedInfractionStore.remove(id: $0.id) }
                }
                state.incidents.removeAll { incident in
                    selectedIncidents.contains { $0.id == incident.id }
                }
                state.submitting = false
                state.showingReviewSheet = false
                state.scanStartedAt = nil
                state.message = "Submitted \(selectedIncidents.count) auto-report\(selectedIncidents.count == 1 ? "" : "s")."
                if let submittedObjectId {
                    effect = AutoReportEffect(value: .reportSubmitted(submittedObjectId))
                }
            } catch {
                let failedSession = try? await SharedBridge.shared.container.loadSessionUseCase.execute()
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
                state.submitting = false
                state.message = ReportedAnalytics.userFacingSubmitFailureMessage(
                    for: error,
                    fallback: "Auto-Report submission failed. Review the selected reports and try again."
                )
            }
        }
    }
}

struct AutoReportScreen: View {
    let isAuthorized: Bool
    let onRequireLogin: () -> Void
    let onReportSubmitted: (String) -> Void
    @StateObject private var viewModel = AutoReportViewModel()

    var body: some View {
        ScrollView {
            ScreenCard {
                VStack(alignment: .leading, spacing: 14) {
                    Text("Scan photos into a bulk review")
                        .font(.title3.weight(.semibold))
                    Text("Reported will scan \(viewModel.state.scanWindow.sentenceLabel) of photos on this device and use on-device AI to determine if they are reported worthy. We look for vehicle photos where the infraction model shows a blocked bike lane or blocked crosswalk, then ask you before anything is submitted.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)

                    ReportedAiInstallPanel()

                    HStack(spacing: 10) {
                        Picker("Scan window", selection: Binding(
                            get: { viewModel.state.scanWindow },
                            set: { viewModel.onAction(.scanWindowChanged($0)) }
                        )) {
                            ForEach(AutoReportScanWindow.options) { option in
                                Text(option.label).tag(option)
                            }
                        }
                        .pickerStyle(.menu)
                        .tint(Color.reportedOrange)
                        .disabled(viewModel.state.scanning || viewModel.state.submitting)
                        .frame(minWidth: 92, alignment: .leading)

                        Button {
                            viewModel.onAction(.startScan)
                        } label: {
                            HStack {
                                if viewModel.state.scanning {
                                    ProgressView()
                                        .tint(.white)
                                }
                                Text(viewModel.state.scanning ? "Scanning..." : "Scan Photos")
                                    .font(.body.weight(.semibold))
                            }
                            .foregroundStyle(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(Color.reportedOrange)
                        .disabled(viewModel.state.scanning || viewModel.state.submitting)
                    }
                }

                AutoReportSummaryView(incidents: viewModel.state.incidents)

                if let message = viewModel.state.message {
                    MessageView(text: message)
                }

                if !viewModel.state.incidents.isEmpty || !viewModel.state.processedPhotos.isEmpty {
                    Button {
                        viewModel.onAction(.reviewVisibilityChanged(true))
                    } label: {
                        Text("Review Results")
                            .font(.body.weight(.semibold))
                            .foregroundStyle(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(Color.reportedOrange)
                    .disabled(viewModel.state.scanning || viewModel.state.submitting)
                }
            }
            .padding(16)
        }
        .background(Color(.systemBackground))
        .sheet(isPresented: Binding(
            get: { viewModel.state.scanning },
            set: { if !$0 { viewModel.onAction(.cancelScan) } }
        )) {
            AutoReportScanProgressSheet(
                processed: viewModel.state.processed,
                total: viewModel.state.total,
                onCancel: { viewModel.onAction(.cancelScan) }
            )
            .presentationDetents([.height(220)])
            .presentationDragIndicator(.visible)
            .interactiveDismissDisabled(true)
        }
        .sheet(isPresented: Binding(
            get: { viewModel.state.showingReviewSheet },
            set: { viewModel.onAction(.reviewVisibilityChanged($0)) }
        )) {
            AutoReportReviewSheet(
                incidents: Binding(
                    get: { viewModel.state.incidents },
                    set: { viewModel.onAction(.incidentsChanged($0)) }
                ),
                processedPhotos: viewModel.state.processedPhotos,
                submitting: viewModel.state.submitting,
                onSubmit: { viewModel.onAction(.submitSelected(isAuthorized: isAuthorized)) }
            )
            .presentationDetents([.large])
            .presentationDragIndicator(.visible)
            .presentationBackground(Color(.systemBackground))
        }
        .onChange(of: viewModel.effect?.id) { _, _ in
            guard let effect = viewModel.effect else { return }
            switch effect.value {
            case .requireLogin:
                onRequireLogin()
            case .reportSubmitted(let objectId):
                onReportSubmitted(objectId)
            }
            viewModel.onAction(.effectHandled)
        }
    }

}
