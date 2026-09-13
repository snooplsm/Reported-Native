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

private struct VoiceAssistantContentHeightKey: PreferenceKey {
    static var defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = max(value, nextValue())
    }
}

struct VoiceReportAssistantSheet: View {
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
    var onContentHeightChange: (CGFloat) -> Void = { _ in }
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
                        .background {
                            GeometryReader { geometry in
                                Color.clear.preference(key: VoiceAssistantContentHeightKey.self, value: geometry.size.height)
                            }
                        }
                    }
                    .padding(.horizontal, 18)
                    .padding(.top, 14)
                    .padding(.bottom, 18)
                    .onChange(of: reviewContentKey) { _, key in
                        scrollToReviewContent(proxy: proxy, key: key)
                    }
                }
            }
        }
        .onPreferenceChange(VoiceAssistantContentHeightKey.self) { height in
            guard height > 0 else { return }
            onContentHeightChange(height + 14 + 18 + voiceAssistantFloatingSheetBottomPadding)
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

struct VoiceCaptureControl: View {
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

struct VoiceMinimizedRecordingControl: View {
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

struct VoiceLevelMeter: View {
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

struct VoiceProcessingPanel: View {
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

struct VoiceProcessingStep: View {
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

struct VoiceDraftDataRow: View {
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

struct VoiceDraftSourceHeader: View {
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

struct VoiceDraftSourceHeaderButton: View {
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

struct VoiceDraftValueColumn: View {
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

struct VoiceReportBlockHeader: View {
    let title: String

    var body: some View {
        Text(title)
            .font(.caption.weight(.semibold))
            .foregroundStyle(.secondary)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

struct VoiceDraftMetadataRow: View {
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

struct VoicePrimaryAction: View {
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

struct VoiceSecondaryAction: View {
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

func formatVoiceElapsedTenths(_ seconds: TimeInterval) -> String {
    String(format: "%.1fs", min(29.9, max(0, seconds)))
}

func installSizeLabel(from value: String) -> String {
    value.replacingOccurrences(of: " ", with: "").lowercased()
}

struct VoiceReportMessage: View {
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

struct VoiceReportBlock: View {
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
