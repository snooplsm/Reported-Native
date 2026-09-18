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

enum VoiceReportPermissionState {
    case unknown
    case authorized
    case denied

    var isAuthorized: Bool {
        self == .authorized
    }
}

@MainActor
final class VoiceReportAudioController: ObservableObject {
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

struct VoiceReportGemmaResult {
    let transcript: String?
    let draft: VoiceReportDraft
}

enum OnDeviceGemmaVoiceDraftEngine {
    static let modelDownloadSizeLabel = "2.6 GB"
    static var accelerationMessage: String {
        supportsFastOnDeviceAI ? "" : slowAccelerationMessage
    }
    private static var slowAccelerationMessage: String {
        reportedLocalized("Hardware acceleration is unavailable for this model on this device. REPORTED AI may run slowly.")
    }
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
        guard let modelURL = resolveModelURL() else { return reportedLocalized("Not installed") }
        guard let attributes = try? FileManager.default.attributesOfItem(atPath: modelURL.path),
              let size = attributes[.size] as? NSNumber else {
            return reportedLocalized("Installed")
        }
        return reportedLocalizedFormat("Installed (%@)", formatModelBytes(size.int64Value))
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
                throw VoiceReportGemmaError.message(reportedLocalized("REPORTED AI did not finish installing correctly."))
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
                throw VoiceReportGemmaError.message(
                    reportedLocalizedFormat(
                        "REPORTED AI is not installed. Install REPORTED AI (%@) before voice drafting can run.",
                        installSizeLabel(from: modelDownloadSizeLabel)
                    )
                )
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

final class GemmaModelDownloader: NSObject, URLSessionDownloadDelegate {
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

enum VoiceReportGemmaError: LocalizedError {
    case message(String)

    var errorDescription: String? {
        switch self {
        case .message(let message):
            return message
        }
    }
}

struct VoiceDraftChangeRow {
    let field: VoiceDraftField
    let label: String
    let currentValue: String
    let nextValue: String
}

enum VoiceDraftField: String, CaseIterable, Hashable {
    case complaint
    case plate
    case state
    case address
    case occurredAt
    case description
    case notes
}

enum VoiceDraftSource: String, Identifiable {
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

struct VoiceReportDraft {
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

func addChangeRow(
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

func cleanedVoicePreviewValue(_ value: String?) -> String? {
    let cleaned = value?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
    return cleaned.isEmpty ? nil : cleaned
}

struct VoiceReportContext {
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

func resolveVoiceCurrentAddress(state: ComposerState) async -> String? {
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

var voiceAssistantCompactSheetHeight: CGFloat {
    UIDevice.current.userInterfaceIdiom == .pad ? 430 : 390
}

var voiceAssistantMinimizedSheetHeight: CGFloat {
    UIDevice.current.userInterfaceIdiom == .pad ? 126 : 118
}

let voiceAssistantFloatingSheetHorizontalPadding: CGFloat = 14
let voiceAssistantFloatingSheetBottomPadding: CGFloat = 6
let voiceAssistantFloatingSheetCornerRadius: CGFloat = 26
