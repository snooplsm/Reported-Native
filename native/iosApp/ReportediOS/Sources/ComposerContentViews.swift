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
    var composerContent: some View {
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

    var pendingComplaintSheet: some View {
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

    func handleAddressQueryChanged(_ oldValue: String, _ newValue: String) {
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

    func startVideoScan(from startTimeSeconds: Double = 0, resetProgress: Bool = true) {
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

    func cycleComplaintAnimations() async {
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
    func pickMediaContent(availableWidth: CGFloat) -> some View {
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
    var detectionProgressModal: some View {
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

    func selectVideoCandidate(_ candidate: ComposerState.PlateCandidate) {
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

    func cancelVideoScan() {
        videoScanGeneration += 1
        videoScanTask?.cancel()
        videoScanTask = nil
        videoScanPaused = false
        detectionProgressMinimized = false
        viewModel.onAction(.videoProcessingCancelled)
    }

    @ViewBuilder
    var detectionProgressChip: some View {
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
}
