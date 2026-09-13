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

extension ComposerScreen {
    func openVoiceAssistant() {
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

    func dismissVoiceAssistant() {
        cancelVoiceProcessing()
        _ = voiceAudio.stopRecording()
        showVoiceAssistSheet = false
    }

    var isVoiceAssistSheetMinimized: Bool {
        voiceAudio.isRecording && voiceAssistSheetDetent == .height(voiceAssistantMinimizedSheetHeight)
    }

    @MainActor
    func startVoiceAssistantCapture() async {
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
    func beginStoppingVoiceAssistantCapture() {
        guard voiceProcessingTask == nil else { return }
        let processingID = UUID()
        voiceProcessingID = processingID
        voiceProcessingTask = Task { @MainActor in
            await stopVoiceAssistantCapture(processingID: processingID)
        }
    }

    @MainActor
    func stopVoiceAssistantCapture(processingID: UUID) async {
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
    func processVoiceAudio(_ audioURL: URL, processingID: UUID) async {
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
    func cancelVoiceProcessing() {
        voiceProcessingID = nil
        voiceProcessingTask?.cancel()
        voiceProcessingTask = nil
        voiceImageContextTask?.cancel()
        voiceImageContextTask = nil
        voiceProcessing = false
    }

    @MainActor
    func finishVoiceProcessing(processingID: UUID) {
        guard voiceProcessingID == processingID else { return }
        voiceProcessing = false
        voiceProcessingTask = nil
        voiceProcessingID = nil
    }

    @MainActor
    func startVoiceImageContextWarmup() {
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
    func downloadVoiceModel() async {
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

    func resetVoiceAssistant() {
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

    func applyVoiceDraft(_ draft: VoiceReportDraft) {
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
    func completeReportTutorialFromButton() async {
        await completeReportTutorial()
    }

    @MainActor
    func completeReportTutorial() async {
        ReportTutorialSettings.markSeen()
        showReportTutorial = false
        IOSMediaScanner.shared.disableScannerFromSettings()
    }

    var isDetectionSheetVisible: Bool {
        viewModel.state.detectingPlates &&
        !viewModel.state.awaitingVideoProcessingDecision &&
        !detectionProgressMinimized
    }

    var showsBottomSubmitBar: Bool {
        viewModel.state.stage == .verify && !isDetectionSheetVisible && !isLandscapeComposer && !isKeyboardVisible
    }

    var isReportedAiFabVisible: Bool {
        viewModel.state.primaryMedia != nil && !isKeyboardVisible
    }

    @ViewBuilder
    var reportedAiFloatingButton: some View {
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
    var voiceAssistantFloatingOverlay: some View {
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
            .animation(.snappy, value: voiceAssistantMeasuredHeight)
        }
    }

    var voiceAssistantSheetContent: some View {
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
            onDismiss: dismissVoiceAssistant,
            onContentHeightChange: { height in
                if abs(voiceAssistantMeasuredHeight - height) > 1 {
                    voiceAssistantMeasuredHeight = height
                }
            }
        )
    }

    func voiceAssistantOverlayHeight(for availableHeight: CGFloat) -> CGFloat {
        let desiredHeight: CGFloat
        if isVoiceAssistSheetMinimized {
            desiredHeight = voiceAssistantMinimizedSheetHeight
        } else {
            desiredHeight = voiceAssistantMeasuredHeight
        }
        let maximumHeight = max(voiceAssistantMinimizedSheetHeight, availableHeight - 6)
        return min(max(desiredHeight, voiceAssistantMinimizedSheetHeight), maximumHeight)
    }

}
