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

struct SubmittedReportSnackbar: View {
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

struct DetectionCandidatePill: View {
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

struct VideoFrameScrubberPreview: View {
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

struct VideoPlaybackCandidateView: View {
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

struct BasicVideoPlaybackView: View {
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

struct VideoPlaybackView: UIViewRepresentable {
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

struct VideoSeekPreview: UIViewRepresentable {
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

final class PlayerContainerView: UIView {
    override static var layerClass: AnyClass { AVPlayerLayer.self }
    var playerLayer: AVPlayerLayer { layer as! AVPlayerLayer }
}

struct VideoPlateOverlay: View {
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

let reportedFieldLabelSpacing: CGFloat = 10
let reportedFieldLabelHorizontalInset: CGFloat = 2

extension UITextAutocapitalizationType {
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

struct ReportedFieldLabel: View {
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

struct ComplaintPickerField: View {
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

struct PlateInputField: View {
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

struct ValidationMessage: View {
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

struct FieldErrorGroup: View {
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

extension ComposerState.PlateCandidate {
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

extension UIImage {
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

struct PlateRegionSheet: View {
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

struct PlateEntryScreen: View {
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
