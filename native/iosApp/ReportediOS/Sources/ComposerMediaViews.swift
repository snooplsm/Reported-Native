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

struct PrimarySubmissionPreview: View {
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

enum PlateOverlayContentMode {
    case fit
    case fill
}

struct PlateImageLayout {
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

struct PlateAwareImage: View {
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

struct StillImagePlateOverlay: View {
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

struct FullScreenImageViewer: View {
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

var plateCandidateSheetDetentHeight: CGFloat {
    UIDevice.current.userInterfaceIdiom == .pad ? 470 : 320
}

@MainActor
func complaintChooserSheetDetentHeight(optionCount: Int) -> CGFloat {
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
func currentBottomSafeAreaInset(fallbackLongSide: CGFloat) -> CGFloat {
    let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
    let foregroundScene = scenes.first { $0.activationState == .foregroundActive }
        ?? scenes.first { $0.activationState == .foregroundInactive }
        ?? scenes.first
    if let bottomInset = foregroundScene?.windows.first(where: \.isKeyWindow)?.safeAreaInsets.bottom {
        return bottomInset
    }
    return fallbackLongSide >= 780 ? 34 : 0
}

struct PlateCandidateChip: View {
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

struct UsePlateCandidateSheet: View {
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

struct PlateCandidatePickerSheet: View {
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

struct PlateCandidatePickerRow: View {
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

struct PlateCandidateCropPreview: View {
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

struct MediaAttachmentChip: View {
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

func complaintUIImage(option: ComplaintOption) -> UIImage? {
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

func formatVideoDuration(_ seconds: Double) -> String {
    let totalSeconds = max(0, Int(seconds.rounded(.down)))
    return "\(totalSeconds / 60):\(String(format: "%02d", totalSeconds % 60))"
}

func loadSubmissionMedia(from item: PhotosPickerItem) async -> ComposerState.SubmissionMedia? {
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
