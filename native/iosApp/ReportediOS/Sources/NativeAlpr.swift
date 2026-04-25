import AVFoundation
import CoreGraphics
import Foundation
import OnnxRuntimeBindings
import SharedCore
import UIKit

private let detectorModelName = "yolo-v9-t-640-license-plates-end2end"
private let ocrModelName = "global_mobile_vit_v2_ocr"
private let plateStateModelName = "reported-plate-class-best"
private let plateSegmentationModelName = "reported-plate-seg"
private let vehicleSegmentationModelName = "yolov8n-seg"
private let vehicleSegmentationNmsModelName = "nms-yolov8"
private let detectorSize = 640
private let vehicleSegmentationSize = 640
private let vehicleSegmentationClasses = 80
private let vehicleSegmentationRowSize = 116
private let vehicleSegmentationTopK: Float = 100
private let vehicleSegmentationIouThreshold: Float = 0.4
private let vehicleSegmentationScoreThreshold: Float = 0.2
private let vehicleSegmentationMinScore: Float = 0.5
private let vehicleSegmentationMinSide: CGFloat = 60
private let plateStateSize = 160
private let plateSegmentationSize = 160
private let ocrWidth = 140
private let ocrHeight = 70
private let detectionThreshold: Float = 0.2
private let segmentationMaskThreshold: Float = 0.5
private let maxCandidates = 8
private let ocrAlphabet = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ_")
private let plateStateLabels = ["CT", "00", "NJ", "NY", "NY_PD", "NY_TLC", "PA"]
private let videoFrameStride = 3
private let videoDetectionBatchSize = 3
private let videoRotationProbeStride = 9
private let videoDryProcessedFrameCount = 3
private let videoRotationProbeDegrees: [CGFloat] = [-90, 90, 180]
private let vehicleSegmentationLabels: Set<Int> = [2, 5, 7]

private struct LetterboxedImage {
    let image: UIImage
    let scale: CGFloat
    let padX: CGFloat
    let padY: CGFloat
}

private struct VehicleSegmentationImage {
    let image: UIImage
    let scale: CGFloat
}

private struct Detection {
    let x1: CGFloat
    let y1: CGFloat
    let x2: CGFloat
    let y2: CGFloat
    let score: Float
}

private struct PlateStateClassification {
    let state: String?
    let confidence: Double
}

struct VideoFrameScanProgress {
    let processedFrames: Int
    let totalFrames: Int
    let frameTimeSeconds: Double
    let durationSeconds: Double
    let candidatesFound: Int
    let framePreview: UIImage?
    let frameCandidates: [ComposerState.PlateCandidate]
    let allCandidates: [ComposerState.PlateCandidate]
}

private struct VideoFrameSample {
    let frameIndex: Int
    let frameTimeSeconds: Double
    let image: UIImage
}

final class NativeAlprEngine {
    static let shared = NativeAlprEngine()

    private lazy var env = try? ORTEnv(loggingLevel: .warning)
    private var detectorSession: ORTSession?
    private var ocrSession: ORTSession?
    private var plateStateSession: ORTSession?
    private var plateSegmentationSession: ORTSession?
    private var vehicleSegmentationSession: ORTSession?
    private var vehicleSegmentationNmsSession: ORTSession?

    func detectLicensePlates(media: ComposerState.SubmissionMedia) async -> [ComposerState.PlateCandidate] {
        guard !media.isVideo else { return [] }
        return await Task.detached(priority: .userInitiated) {
            self.detectLicensePlatesSync(media: media)
        }.value
    }

    func detectLicensePlatesInVideo(
        media: ComposerState.SubmissionMedia,
        onProgress: @escaping (VideoFrameScanProgress) async -> Void
    ) async -> [ComposerState.PlateCandidate] {
        guard media.isVideo else {
            return await detectLicensePlates(media: media)
        }
        return await Task.detached(priority: .userInitiated) {
            await self.detectLicensePlatesInVideoSync(media: media, onProgress: onProgress)
        }.value
    }

    private func detectLicensePlatesSync(media: ComposerState.SubmissionMedia) -> [ComposerState.PlateCandidate] {
        guard
            let image = UIImage(contentsOfFile: media.fileURL.path),
            let detector = try? session(for: detectorModelName, cached: \.detectorSession),
            let ocr = try? session(for: ocrModelName, cached: \.ocrSession),
            let plateState = try? session(for: plateStateModelName, cached: \.plateStateSession)
        else {
            return []
        }

        return detectLicensePlates(
            in: image,
            detector: detector,
            ocr: ocr,
            plateState: plateState,
            plateSegmentation: nil,
            vehicleSegmentation: nil,
            vehicleSegmentationNms: nil
        )
    }

    private func detectLicensePlatesInVideoSync(
        media: ComposerState.SubmissionMedia,
        onProgress: (VideoFrameScanProgress) async -> Void
    ) async -> [ComposerState.PlateCandidate] {
        guard
            let detector = try? session(for: detectorModelName, cached: \.detectorSession),
            let ocr = try? session(for: ocrModelName, cached: \.ocrSession),
            let plateState = try? session(for: plateStateModelName, cached: \.plateStateSession)
        else {
            return []
        }
        let asset = AVAsset(url: media.fileURL)
        let videoTrack = asset.tracks(withMediaType: .video).first
        let durationSeconds = CMTimeGetSeconds(asset.duration)
        guard durationSeconds.isFinite, durationSeconds > 0 else {
            await onProgress(VideoFrameScanProgress(
                processedFrames: 0,
                totalFrames: 0,
                frameTimeSeconds: 0,
                durationSeconds: 0,
                candidatesFound: 0,
                framePreview: nil,
                frameCandidates: [],
                allCandidates: []
            ))
            return []
        }

        let frameRate = Double(videoTrack?.nominalFrameRate ?? 0) > 0 ? Double(videoTrack?.nominalFrameRate ?? 30) : 30
        let totalFrames = max(1, Int(ceil(durationSeconds * frameRate)))
        let generator = AVAssetImageGenerator(asset: asset)
        generator.appliesPreferredTrackTransform = true
        generator.requestedTimeToleranceBefore = .zero
        generator.requestedTimeToleranceAfter = .zero

        var bestCandidatesByPlate: [String: ComposerState.PlateCandidate] = [:]
        var plateOrder: [String] = []
        var recentProcessedFramesHadPlate: [Bool] = []
        var lastSuccessfulFallbackRotationDegrees: CGFloat?
        var index = 0
        while index < totalFrames {
            if Task.isCancelled { break }
            var samples: [VideoFrameSample] = []
            while index < totalFrames && samples.count < videoDetectionBatchSize {
                let seconds = min(Double(index) / frameRate, durationSeconds)
                let time = CMTime(seconds: seconds, preferredTimescale: 600)
                if let cgImage = try? generator.copyCGImage(at: time, actualTime: nil) {
                    samples.append(VideoFrameSample(
                        frameIndex: index,
                        frameTimeSeconds: seconds,
                        image: UIImage(cgImage: cgImage)
                    ))
                } else {
                    recentProcessedFramesHadPlate.append(false)
                    if recentProcessedFramesHadPlate.count > videoDryProcessedFrameCount {
                        recentProcessedFramesHadPlate.removeFirst()
                    }
                    await onProgress(VideoFrameScanProgress(
                        processedFrames: index + 1,
                        totalFrames: totalFrames,
                        frameTimeSeconds: seconds,
                        durationSeconds: durationSeconds,
                        candidatesFound: bestCandidatesByPlate.count,
                        framePreview: nil,
                        frameCandidates: [],
                        allCandidates: plateOrder.compactMap { bestCandidatesByPlate[$0] }
                    ))
                }
                index += videoFrameStride
            }
            if samples.isEmpty { continue }
            let batchedCandidates = detectLicensePlatesBatched(
                images: samples.map(\.image),
                detector: detector,
                ocr: ocr,
                plateState: plateState,
                plateSegmentation: nil,
                vehicleSegmentation: nil,
                vehicleSegmentationNms: nil
            )
            for (sampleIndex, sample) in samples.enumerated() {
                if Task.isCancelled { break }
                var image = sample.image
                var candidates = batchedCandidates.indices.contains(sampleIndex) ? batchedCandidates[sampleIndex] : []
                if candidates.isEmpty && shouldProbeRotations(frameIndex: sample.frameIndex, recentProcessedFramesHadPlate: recentProcessedFramesHadPlate) {
                    for degrees in orderedVideoRotationProbeDegrees(lastSuccessfulRotationDegrees: lastSuccessfulFallbackRotationDegrees) {
                        let rotatedImage = image.rotated(byDegrees: degrees)
                        let rotatedCandidates = detectLicensePlates(
                            in: rotatedImage,
                            detector: detector,
                            ocr: ocr,
                            plateState: plateState,
                            plateSegmentation: nil,
                            vehicleSegmentation: nil,
                            vehicleSegmentationNms: nil
                        )
                        if !rotatedCandidates.isEmpty {
                            image = rotatedImage
                            candidates = rotatedCandidates
                            lastSuccessfulFallbackRotationDegrees = degrees
                            break
                        }
                    }
                }
                let framePreview = annotatedFramePreview(image: image, candidates: candidates)
                candidates = candidates.map {
                    $0.withVideoFrame(preview: framePreview, timeSeconds: sample.frameTimeSeconds)
                }
                for candidate in candidates {
                    if let existing = bestCandidatesByPlate[candidate.plate] {
                        if candidate.confidence > existing.confidence {
                            bestCandidatesByPlate[candidate.plate] = candidate
                        }
                    } else {
                        bestCandidatesByPlate[candidate.plate] = candidate
                        plateOrder.append(candidate.plate)
                    }
                }
                recentProcessedFramesHadPlate.append(!candidates.isEmpty)
                if recentProcessedFramesHadPlate.count > videoDryProcessedFrameCount {
                    recentProcessedFramesHadPlate.removeFirst()
                }
                let allCandidates = plateOrder.compactMap { bestCandidatesByPlate[$0] }
                    .sorted { $0.confidence > $1.confidence }
                    .prefix(maxCandidates)
                    .map { $0 }
                await onProgress(VideoFrameScanProgress(
                    processedFrames: sample.frameIndex + 1,
                    totalFrames: totalFrames,
                    frameTimeSeconds: sample.frameTimeSeconds,
                    durationSeconds: durationSeconds,
                    candidatesFound: bestCandidatesByPlate.count,
                    framePreview: framePreview,
                    frameCandidates: candidates,
                    allCandidates: allCandidates
                ))
            }
        }

        return plateOrder.compactMap { bestCandidatesByPlate[$0] }
            .sorted { $0.confidence > $1.confidence }
            .prefix(maxCandidates)
            .map { $0 }
    }

    private func shouldProbeRotations(frameIndex: Int, recentProcessedFramesHadPlate: [Bool]) -> Bool {
        frameIndex > 0 &&
            frameIndex % videoRotationProbeStride == 0 &&
            recentProcessedFramesHadPlate.count >= videoDryProcessedFrameCount &&
            !recentProcessedFramesHadPlate.contains(true)
    }

    private func orderedVideoRotationProbeDegrees(lastSuccessfulRotationDegrees: CGFloat?) -> [CGFloat] {
        var ordered: [CGFloat] = []
        func add(_ degrees: CGFloat?) {
            guard let degrees else { return }
            let normalized = normalizedVideoRotationDegrees(degrees)
            guard abs(normalized) > 0.5 else { return }
            if !ordered.contains(where: { abs($0 - normalized) < 0.5 }) {
                ordered.append(normalized)
            }
        }

        add(lastSuccessfulRotationDegrees)
        videoRotationProbeDegrees.forEach { add($0) }
        return ordered
    }

    private func normalizedVideoRotationDegrees(_ degrees: CGFloat) -> CGFloat {
        let rounded = Int((degrees / 90).rounded()) * 90
        let normalized = ((rounded % 360) + 360) % 360
        switch normalized {
        case 90:
            return 90
        case 180:
            return 180
        case 270:
            return -90
        default:
            return 0
        }
    }

    private func detectLicensePlates(
        in image: UIImage,
        detector: ORTSession,
        ocr: ORTSession,
        plateState: ORTSession,
        plateSegmentation: ORTSession?,
        vehicleSegmentation: ORTSession?,
        vehicleSegmentationNms: ORTSession?
    ) -> [ComposerState.PlateCandidate] {
        let detections = Array(detect(in: image, session: detector).prefix(maxCandidates))
        let works: [(image: UIImage, detection: Detection, crop: UIImage)] = detections.compactMap { detection in
            let refinedDetection = detection
            guard let crop = crop(image: image, detection: refinedDetection) else { return nil }
            return (image: image, detection: refinedDetection, crop: crop)
        }
        let ocrTexts = runOcrBatched(images: works.map(\.crop), session: ocr)
        let classifications = classifyPlateStatesBatched(images: works.map(\.crop), session: plateState)
        var candidates: [ComposerState.PlateCandidate] = []
        for (index, work) in works.enumerated() {
            let plate = normalizePlateText(ocrTexts[safe: index] ?? "")
            guard !plate.isEmpty else { continue }
            let classification = classifications[safe: index] ?? nil
            let patternMatch = PlatePatternClassifier.shared.classify(rawPlate: plate)
            let state = patternMatch?.state ?? (plate.hasPrefix("T") && plate.hasSuffix("C") ? "NY" : classification?.state)
            guard !candidates.contains(where: { $0.plate == plate }) else { continue }
            candidates.append(ComposerState.PlateCandidate(
                plate: plate,
                confidence: Double(min(1, work.detection.score)),
                state: state,
                stateConfidence: patternMatch.map { Double($0.confidence) } ?? classification?.confidence,
                plateType: patternMatch?.type.name,
                plateTypeLabel: patternMatch?.label,
                bounds: CGRect(
                    x: work.detection.x1 / image.size.width,
                    y: work.detection.y1 / image.size.height,
                    width: (work.detection.x2 - work.detection.x1) / image.size.width,
                    height: (work.detection.y2 - work.detection.y1) / image.size.height
                ),
                videoFramePreview: nil,
                videoFrameTimeSeconds: nil
            ))
        }
        return candidates.sorted { lhs, rhs in
            lhs.confidence > rhs.confidence
        }
    }

    private func detectLicensePlatesBatched(
        images: [UIImage],
        detector: ORTSession,
        ocr: ORTSession,
        plateState: ORTSession,
        plateSegmentation: ORTSession?,
        vehicleSegmentation: ORTSession?,
        vehicleSegmentationNms: ORTSession?
    ) -> [[ComposerState.PlateCandidate]] {
        guard !images.isEmpty else { return [] }
        if images.count == 1 {
            return [detectLicensePlates(
                in: images[0],
                detector: detector,
                ocr: ocr,
                plateState: plateState,
                plateSegmentation: plateSegmentation,
                vehicleSegmentation: vehicleSegmentation,
                vehicleSegmentationNms: vehicleSegmentationNms
            )]
        }
        let detectionsByImage = detectBatched(images: images, session: detector)
        let cropWorkByImage: [[(image: UIImage, detection: Detection, crop: UIImage)]] = images.enumerated().map { imageIndex, image in
            let detections = detectionsByImage[safe: imageIndex] ?? []
            return detections.prefix(maxCandidates).compactMap { detection in
                let refinedDetection = detection
                guard let crop = crop(image: image, detection: refinedDetection) else { return nil }
                return (image: image, detection: refinedDetection, crop: crop)
            }
        }
        let allWork = cropWorkByImage.flatMap { $0 }
        let ocrTexts = runOcrBatched(images: allWork.map(\.crop), session: ocr)
        let classifications = classifyPlateStatesBatched(images: allWork.map(\.crop), session: plateState)
        var ocrIndex = 0
        var classifierIndex = 0
        return cropWorkByImage.map { works in
            var candidates: [ComposerState.PlateCandidate] = []
            for work in works {
                let plate = normalizePlateText(ocrTexts[safe: ocrIndex] ?? "")
                ocrIndex += 1
                let classification = classifications[safe: classifierIndex] ?? nil
                classifierIndex += 1
                guard !plate.isEmpty else { continue }
                let patternMatch = PlatePatternClassifier.shared.classify(rawPlate: plate)
                let state = patternMatch?.state ?? (plate.hasPrefix("T") && plate.hasSuffix("C") ? "NY" : classification?.state)
                guard !candidates.contains(where: { $0.plate == plate }) else { continue }
                candidates.append(ComposerState.PlateCandidate(
                    plate: plate,
                    confidence: Double(min(1, work.detection.score)),
                    state: state,
                    stateConfidence: patternMatch.map { Double($0.confidence) } ?? classification?.confidence,
                    plateType: patternMatch?.type.name,
                    plateTypeLabel: patternMatch?.label,
                    bounds: CGRect(
                        x: work.detection.x1 / work.image.size.width,
                        y: work.detection.y1 / work.image.size.height,
                        width: (work.detection.x2 - work.detection.x1) / work.image.size.width,
                        height: (work.detection.y2 - work.detection.y1) / work.image.size.height
                    ),
                    videoFramePreview: nil,
                    videoFrameTimeSeconds: nil
                ))
            }
            return candidates.sorted { $0.confidence > $1.confidence }
        }
    }

    private func annotatedFramePreview(image: UIImage, candidates: [ComposerState.PlateCandidate]) -> UIImage {
        let maxWidth: CGFloat = 720
        let scale = min(1, maxWidth / max(image.size.width, 1))
        let size = CGSize(width: image.size.width * scale, height: image.size.height * scale)
        let renderer = UIGraphicsImageRenderer(size: size)
        return renderer.image { context in
            image.draw(in: CGRect(origin: .zero, size: size))
            let cgContext = context.cgContext
            cgContext.setStrokeColor(UIColor.systemGreen.cgColor)
            cgContext.setLineWidth(4)
            for candidate in candidates {
                guard let bounds = candidate.bounds else { continue }
                let rect = CGRect(
                    x: bounds.minX * size.width,
                    y: bounds.minY * size.height,
                    width: bounds.width * size.width,
                    height: bounds.height * size.height
                )
                cgContext.stroke(rect)
                let label = "\(candidate.plate) \(candidate.state ?? "") \(Int(candidate.confidence * 100))%"
                    .trimmingCharacters(in: .whitespaces)
                let attributes: [NSAttributedString.Key: Any] = [
                    .font: UIFont.boldSystemFont(ofSize: 24),
                    .foregroundColor: UIColor.white
                ]
                let labelSize = label.size(withAttributes: attributes)
                let labelRect = CGRect(
                    x: rect.minX,
                    y: max(4, rect.minY - labelSize.height - 10),
                    width: min(labelSize.width + 16, size.width - rect.minX - 4),
                    height: labelSize.height + 8
                )
                UIColor.black.withAlphaComponent(0.82).setFill()
                UIBezierPath(roundedRect: labelRect, cornerRadius: 8).fill()
                label.draw(
                    at: CGPoint(x: labelRect.minX + 8, y: labelRect.minY + 4),
                    withAttributes: attributes
                )
            }
        }
    }

    private func session(for modelName: String, cached keyPath: ReferenceWritableKeyPath<NativeAlprEngine, ORTSession?>) throws -> ORTSession {
        if let session = self[keyPath: keyPath] {
            return session
        }
        let modelPath = Bundle.main.path(forResource: modelName, ofType: "onnx", inDirectory: "models")
            ?? Bundle.main.path(forResource: modelName, ofType: "onnx")
        guard let env, let path = modelPath else {
            throw NSError(domain: "NativeAlpr", code: 1, userInfo: nil)
        }
        let session = try ORTSession(env: env, modelPath: path, sessionOptions: nil)
        self[keyPath: keyPath] = session
        return session
    }

    private func sessionIfPresent(
        for modelName: String,
        cached keyPath: ReferenceWritableKeyPath<NativeAlprEngine, ORTSession?>
    ) -> ORTSession? {
        if let session = self[keyPath: keyPath] {
            return session
        }
        let modelPath = Bundle.main.path(forResource: modelName, ofType: "onnx", inDirectory: "models")
            ?? Bundle.main.path(forResource: modelName, ofType: "onnx")
        guard let env, let path = modelPath else {
            return nil
        }
        guard let session = try? ORTSession(env: env, modelPath: path, sessionOptions: nil) else {
            return nil
        }
        self[keyPath: keyPath] = session
        return session
    }

    private func mergeDetections(direct: [Detection], vehicle: [Detection]) -> [Detection] {
        guard !direct.isEmpty else { return vehicle.sorted { $0.score > $1.score } }
        guard !vehicle.isEmpty else { return direct.sorted { $0.score > $1.score } }
        var merged: [Detection] = []
        (direct + vehicle).sorted { $0.score > $1.score }.forEach { detection in
            guard !merged.contains(where: { $0.iou(with: detection) > 0.55 }) else { return }
            merged.append(detection)
        }
        return merged.sorted { $0.score > $1.score }
    }

    private func detectPlatesViaVehicleSegmentation(
        in image: UIImage,
        detector: ORTSession,
        segmenter: ORTSession?,
        nms: ORTSession?
    ) -> [Detection] {
        guard let segmenter, let nms else { return [] }
        let vehicles = detectVehicles(in: image, segmenter: segmenter, nms: nms)
            .sorted {
                let lhsDistance = $0.centerDistanceSquared(in: image.size)
                let rhsDistance = $1.centerDistanceSquared(in: image.size)
                if abs(lhsDistance - rhsDistance) < 50 * 50 {
                    return $0.area > $1.area
                }
                return lhsDistance < rhsDistance
            }
            .prefix(4)
        var detections: [Detection] = []
        for vehicle in vehicles {
            guard let vehicleImage = crop(image: image, detection: vehicle) else { continue }
            detect(in: vehicleImage, session: detector).forEach { plate in
                detections.append(Detection(
                    x1: (vehicle.x1 + plate.x1).clamped(to: 0...image.size.width),
                    y1: (vehicle.y1 + plate.y1).clamped(to: 0...image.size.height),
                    x2: (vehicle.x1 + plate.x2).clamped(to: 0...image.size.width),
                    y2: (vehicle.y1 + plate.y2).clamped(to: 0...image.size.height),
                    score: (plate.score * 0.85 + vehicle.score * 0.15).clamped(to: 0...1)
                ))
            }
        }
        return detections.sorted { $0.score > $1.score }
    }

    private func detectVehicles(in image: UIImage, segmenter: ORTSession, nms: ORTSession) -> [Detection] {
        let prepared = vehicleSegmentationImage(image: image)
        let input = rgbFloatData(from: prepared.image, width: vehicleSegmentationSize, height: vehicleSegmentationSize)
        var mutableInput = input
        let inputByteCount = mutableInput.count * MemoryLayout<Float>.size
        let inputData = mutableInput.withUnsafeMutableBufferPointer { pointer in
            NSMutableData(bytes: pointer.baseAddress, length: inputByteCount)
        }
        guard
            let inputName = try? segmenter.inputNames().first,
            let outputNames = try? segmenter.outputNames(),
            let inputValue = try? ORTValue(
                tensorData: inputData,
                elementType: .float,
                shape: [1, 3, vehicleSegmentationSize, vehicleSegmentationSize].map(NSNumber.init(value:))
            ),
            let segmentOutputs = try? segmenter.run(withInputs: [inputName: inputValue], outputNames: Set(outputNames), runOptions: nil),
            let detectionOutput = segmentOutputs[outputNames[0]]
        else {
            return []
        }

        var config = [
            Float(vehicleSegmentationClasses),
            vehicleSegmentationTopK,
            vehicleSegmentationIouThreshold,
            vehicleSegmentationScoreThreshold
        ]
        let configByteCount = config.count * MemoryLayout<Float>.size
        let configData = config.withUnsafeMutableBufferPointer { pointer in
            NSMutableData(bytes: pointer.baseAddress, length: configByteCount)
        }
        guard
            let nmsOutputNames = try? nms.outputNames(),
            let configValue = try? ORTValue(
                tensorData: configData,
                elementType: .float,
                shape: [4].map(NSNumber.init(value:))
            ),
            let nmsOutputs = try? nms.run(
                withInputs: ["detection": detectionOutput, "config": configValue],
                outputNames: Set(nmsOutputNames),
                runOptions: nil
            ),
            let selected = nmsOutputs[nmsOutputNames[0]],
            let raw = try? selected.tensorData().asFloatArray(),
            !raw.isEmpty
        else {
            return []
        }

        let rowSize = inferVehicleSegmentationRowSize(raw.count)
        guard rowSize >= vehicleSegmentationClasses + 4 else { return [] }
        let rowCount = raw.count / rowSize
        var vehicles: [Detection] = []
        for rowIndex in 0..<rowCount {
            let offset = rowIndex * rowSize
            let centerX = CGFloat(raw[offset])
            let centerY = CGFloat(raw[offset + 1])
            let width = CGFloat(raw[offset + 2])
            let height = CGFloat(raw[offset + 3])
            var bestLabel = -1
            var bestScore = -Float.greatestFiniteMagnitude
            for labelIndex in 0..<vehicleSegmentationClasses {
                let score = raw[offset + 4 + labelIndex]
                if score > bestScore {
                    bestScore = score
                    bestLabel = labelIndex
                }
            }
            guard bestScore >= vehicleSegmentationMinScore, vehicleSegmentationLabels.contains(bestLabel) else {
                continue
            }
            let x1 = ((centerX - width / 2) / prepared.scale).clamped(to: 0...image.size.width)
            let y1 = ((centerY - height / 2) / prepared.scale).clamped(to: 0...image.size.height)
            let x2 = ((centerX + width / 2) / prepared.scale).clamped(to: 0...image.size.width)
            let y2 = ((centerY + height / 2) / prepared.scale).clamped(to: 0...image.size.height)
            guard x2 - x1 >= vehicleSegmentationMinSide, y2 - y1 >= vehicleSegmentationMinSide else {
                continue
            }
            vehicles.append(Detection(x1: x1, y1: y1, x2: x2, y2: y2, score: bestScore))
        }
        return vehicles
    }

    private func inferVehicleSegmentationRowSize(_ flatSize: Int) -> Int {
        if flatSize % vehicleSegmentationRowSize == 0 {
            return vehicleSegmentationRowSize
        }
        if flatSize % (vehicleSegmentationClasses + 4) == 0 {
            return vehicleSegmentationClasses + 4
        }
        return vehicleSegmentationRowSize
    }

    private func detect(in image: UIImage, session: ORTSession) -> [Detection] {
        let letterboxed = letterbox(image: image, targetSize: detectorSize)
        let input = rgbFloatData(from: letterboxed.image, width: detectorSize, height: detectorSize)
        guard let raw = runFloatModel(
            session: session,
            input: input,
            shape: [1, 3, detectorSize, detectorSize].map(NSNumber.init(value:))
        ), raw.count >= 7 else {
            return []
        }
        let imageWidth = image.size.width
        let imageHeight = image.size.height
        return stride(from: 0, to: raw.count - 6, by: 7).compactMap { index in
            let score = raw[index + 6]
            guard score >= detectionThreshold else { return nil }
            let x1 = ((CGFloat(raw[index + 1]) - letterboxed.padX) / letterboxed.scale).clamped(to: 0...imageWidth)
            let y1 = ((CGFloat(raw[index + 2]) - letterboxed.padY) / letterboxed.scale).clamped(to: 0...imageHeight)
            let x2 = ((CGFloat(raw[index + 3]) - letterboxed.padX) / letterboxed.scale).clamped(to: 0...imageWidth)
            let y2 = ((CGFloat(raw[index + 4]) - letterboxed.padY) / letterboxed.scale).clamped(to: 0...imageHeight)
            guard x2 - x1 >= 8, y2 - y1 >= 8 else { return nil }
            return Detection(x1: x1, y1: y1, x2: x2, y2: y2, score: score)
        }
        .sorted { $0.score > $1.score }
    }

    private func detectBatched(images: [UIImage], session: ORTSession) -> [[Detection]] {
        guard !images.isEmpty else { return [] }
        if images.count == 1 { return [detect(in: images[0], session: session)] }
        let letterboxed = images.map { letterbox(image: $0, targetSize: detectorSize) }
        let input = letterboxed.flatMap { rgbFloatData(from: $0.image, width: detectorSize, height: detectorSize) }
        guard let raw = runFloatModel(
            session: session,
            input: input,
            shape: [images.count, 3, detectorSize, detectorSize].map(NSNumber.init(value:))
        ), raw.count >= 7 else {
            return images.map { detect(in: $0, session: session) }
        }
        var grouped = Array(repeating: [Detection](), count: images.count)
        stride(from: 0, to: raw.count - 6, by: 7).forEach { index in
            let batchIndex = Int(raw[index].rounded())
            guard images.indices.contains(batchIndex) else { return }
            let score = raw[index + 6]
            guard score >= detectionThreshold else { return }
            let item = letterboxed[batchIndex]
            let image = images[batchIndex]
            let imageWidth = image.size.width
            let imageHeight = image.size.height
            let x1 = ((CGFloat(raw[index + 1]) - item.padX) / item.scale).clamped(to: 0...imageWidth)
            let y1 = ((CGFloat(raw[index + 2]) - item.padY) / item.scale).clamped(to: 0...imageHeight)
            let x2 = ((CGFloat(raw[index + 3]) - item.padX) / item.scale).clamped(to: 0...imageWidth)
            let y2 = ((CGFloat(raw[index + 4]) - item.padY) / item.scale).clamped(to: 0...imageHeight)
            guard x2 - x1 >= 8, y2 - y1 >= 8 else { return }
            grouped[batchIndex].append(Detection(x1: x1, y1: y1, x2: x2, y2: y2, score: score))
        }
        return grouped.map { $0.sorted { lhs, rhs in lhs.score > rhs.score } }
    }

    private func runOcr(image: UIImage, session: ORTSession) -> String {
        let bytes = grayscaleBytes(from: image, width: ocrWidth, height: ocrHeight)
        let data = bytes.withUnsafeBufferPointer { pointer in
            NSMutableData(bytes: pointer.baseAddress, length: bytes.count)
        }
        guard
            let inputName = try? session.inputNames().first,
            let outputNames = try? session.outputNames(),
            let input = try? ORTValue(
                tensorData: data,
                elementType: .uInt8,
                shape: [1, ocrHeight, ocrWidth, 1].map(NSNumber.init(value:))
            ),
            let outputs = try? session.run(withInputs: [inputName: input], outputNames: Set(outputNames), runOptions: nil),
            let output = outputs[outputNames[0]],
            let raw = try? output.tensorData().asFloatArray()
        else {
            return ""
        }
        return decodeOcrRow(raw)
    }

    private func runOcrBatched(images: [UIImage], session: ORTSession) -> [String] {
        guard !images.isEmpty else { return [] }
        if images.count == 1 { return [runOcr(image: images[0], session: session)] }
        let bytes = images.flatMap { grayscaleBytes(from: $0, width: ocrWidth, height: ocrHeight) }
        let data = bytes.withUnsafeBufferPointer { pointer in
            NSMutableData(bytes: pointer.baseAddress, length: bytes.count)
        }
        guard
            let inputName = try? session.inputNames().first,
            let outputNames = try? session.outputNames(),
            let input = try? ORTValue(
                tensorData: data,
                elementType: .uInt8,
                shape: [images.count, ocrHeight, ocrWidth, 1].map(NSNumber.init(value:))
            ),
            let outputs = try? session.run(withInputs: [inputName: input], outputNames: Set(outputNames), runOptions: nil),
            let output = outputs[outputNames[0]],
            let raw = try? output.tensorData().asFloatArray(),
            raw.count >= images.count
        else {
            return images.map { runOcr(image: $0, session: session) }
        }
        let rowSize = raw.count / images.count
        guard rowSize > 0 else {
            return images.map { runOcr(image: $0, session: session) }
        }
        return (0..<images.count).map { index in
            let start = index * rowSize
            let end = min(raw.count, start + rowSize)
            return decodeOcrRow(Array(raw[start..<end]))
        }
    }

    private func decodeOcrRow(_ raw: [Float]) -> String {
        let slotCount = raw.count / ocrAlphabet.count
        guard slotCount > 0 else { return "" }
        return (0..<slotCount).map { slotIndex in
            let start = slotIndex * ocrAlphabet.count
            let end = start + ocrAlphabet.count
            let best = raw[start..<end].enumerated().max { $0.element < $1.element }?.offset ?? 0
            return String(ocrAlphabet[best])
        }.joined()
    }

    private func classifyPlateState(image: UIImage, session: ORTSession) -> PlateStateClassification? {
        let resized = image.resized(to: CGSize(width: plateStateSize, height: plateStateSize))
        let input = rgbFloatData(from: resized, width: plateStateSize, height: plateStateSize)
        guard let raw = runFloatModel(
            session: session,
            input: input,
            shape: [1, 3, plateStateSize, plateStateSize].map(NSNumber.init(value:))
        ) else {
            return nil
        }
        let probabilities: [Float]
        if raw.count >= plateStateLabels.count + 4 {
            probabilities = Array(raw[4..<(4 + plateStateLabels.count)])
        } else if raw.count >= plateStateLabels.count {
            probabilities = Array(raw[0..<plateStateLabels.count])
        } else {
            return nil
        }
        guard let bestIndex = probabilities.indices.max(by: { probabilities[$0] < probabilities[$1] }) else {
            return nil
        }
        let label = plateStateLabels[bestIndex]
        return PlateStateClassification(
            state: label == "00" ? nil : String(label.split(separator: "_").first ?? ""),
            confidence: Double(probabilities[bestIndex].clamped(to: 0...1))
        )
    }

    private func classifyPlateStatesBatched(images: [UIImage], session: ORTSession) -> [PlateStateClassification?] {
        guard !images.isEmpty else { return [] }
        if images.count == 1 {
            return [classifyPlateState(image: images[0], session: session)]
        }
        let input = images.flatMap { image -> [Float] in
            let resized = image.resized(to: CGSize(width: plateStateSize, height: plateStateSize))
            return rgbFloatData(from: resized, width: plateStateSize, height: plateStateSize)
        }
        guard let raw = runFloatModel(
            session: session,
            input: input,
            shape: [images.count, 3, plateStateSize, plateStateSize].map(NSNumber.init(value:))
        ) else {
            return images.map { classifyPlateState(image: $0, session: session) }
        }
        let rowSize = raw.count / images.count
        guard rowSize >= plateStateLabels.count else {
            return images.map { classifyPlateState(image: $0, session: session) }
        }
        let classifications = images.indices.map { index -> PlateStateClassification? in
            let start = index * rowSize
            let end = min(raw.count, start + rowSize)
            return decodePlateStateClassification(Array(raw[start..<end]))
        }
        return classifications.count == images.count ? classifications : images.map { classifyPlateState(image: $0, session: session) }
    }

    private func decodePlateStateClassification(_ raw: [Float]) -> PlateStateClassification? {
        let probabilities: [Float]
        if raw.count >= plateStateLabels.count + 4 {
            probabilities = Array(raw[4..<(4 + plateStateLabels.count)])
        } else if raw.count >= plateStateLabels.count {
            probabilities = Array(raw[0..<plateStateLabels.count])
        } else {
            return nil
        }
        guard let bestIndex = probabilities.indices.max(by: { probabilities[$0] < probabilities[$1] }) else {
            return nil
        }
        let label = plateStateLabels[bestIndex]
        return PlateStateClassification(
            state: label == "00" ? nil : String(label.split(separator: "_").first ?? ""),
            confidence: Double(probabilities[bestIndex].clamped(to: 0...1))
        )
    }

    private func runFloatModel(session: ORTSession, input: [Float], shape: [NSNumber]) -> [Float]? {
        var mutableInput = input
        let byteCount = mutableInput.count * MemoryLayout<Float>.size
        let data = mutableInput.withUnsafeMutableBufferPointer { pointer in
            NSMutableData(bytes: pointer.baseAddress, length: byteCount)
        }
        guard
            let inputName = try? session.inputNames().first,
            let outputNames = try? session.outputNames(),
            let inputValue = try? ORTValue(tensorData: data, elementType: .float, shape: shape),
            let outputs = try? session.run(withInputs: [inputName: inputValue], outputNames: Set(outputNames), runOptions: nil),
            let output = outputs[outputNames[0]],
            let raw = try? output.tensorData().asFloatArray()
        else {
            return nil
        }
        return raw
    }

    private func segmentedDetection(
        image: UIImage,
        detection: Detection,
        session: ORTSession?
    ) -> Detection? {
        guard let session, let expandedCrop = crop(image: image, detection: detection) else {
            return nil
        }
        let resized = expandedCrop.resized(to: CGSize(width: plateSegmentationSize, height: plateSegmentationSize))
        guard let raw = runFloatModel(
            session: session,
            input: rgbFloatData(from: resized, width: plateSegmentationSize, height: plateSegmentationSize),
            shape: [1, 3, plateSegmentationSize, plateSegmentationSize].map(NSNumber.init(value:))
        ) else {
            return nil
        }
        let side = Int(sqrt(Double(raw.count)).rounded())
        guard side > 1, side * side <= raw.count else {
            return nil
        }
        guard let maskBounds = maskBounds(raw: raw, side: side, cropSize: expandedCrop.size) else {
            return nil
        }
        let refined = Detection(
            x1: (detection.x1 + maskBounds.minX).clamped(to: 0...image.size.width),
            y1: (detection.y1 + maskBounds.minY).clamped(to: 0...image.size.height),
            x2: (detection.x1 + maskBounds.maxX).clamped(to: 0...image.size.width),
            y2: (detection.y1 + maskBounds.maxY).clamped(to: 0...image.size.height),
            score: detection.score
        )
        guard refined.x2 - refined.x1 >= 8, refined.y2 - refined.y1 >= 8 else {
            return nil
        }
        return refined
    }

    private func maskBounds(raw: [Float], side: Int, cropSize: CGSize) -> CGRect? {
        let logitOutput = raw.contains { $0 < 0 || $0 > 1 }
        var minX = CGFloat.greatestFiniteMagnitude
        var minY = CGFloat.greatestFiniteMagnitude
        var maxX = -CGFloat.greatestFiniteMagnitude
        var maxY = -CGFloat.greatestFiniteMagnitude
        var count = 0
        for y in 0..<side {
            for x in 0..<side {
                let rawValue = raw[y * side + x]
                let probability: Float
                if logitOutput {
                    probability = 1 / (1 + exp(-rawValue))
                } else {
                    probability = rawValue
                }
                guard probability >= segmentationMaskThreshold else { continue }
                let px = (CGFloat(x) + 0.5) / CGFloat(side) * cropSize.width
                let py = (CGFloat(y) + 0.5) / CGFloat(side) * cropSize.height
                minX = min(minX, px)
                minY = min(minY, py)
                maxX = max(maxX, px)
                maxY = max(maxY, py)
                count += 1
            }
        }
        guard count >= 8, maxX > minX, maxY > minY else {
            return nil
        }
        return CGRect(x: minX, y: minY, width: maxX - minX, height: maxY - minY)
    }

    private func crop(image: UIImage, detection: Detection) -> UIImage? {
        guard let cgImage = image.cgImage else { return nil }
        let scaleX = CGFloat(cgImage.width) / image.size.width
        let scaleY = CGFloat(cgImage.height) / image.size.height
        let rect = CGRect(
            x: detection.x1 * scaleX,
            y: detection.y1 * scaleY,
            width: (detection.x2 - detection.x1) * scaleX,
            height: (detection.y2 - detection.y1) * scaleY
        ).integral
        guard let cropped = cgImage.cropping(to: rect) else { return nil }
        return UIImage(cgImage: cropped)
    }

    private func letterbox(image: UIImage, targetSize: Int) -> LetterboxedImage {
        let target = CGFloat(targetSize)
        let scale = min(target / image.size.width, target / image.size.height)
        let scaledWidth = image.size.width * scale
        let scaledHeight = image.size.height * scale
        let padX = (target - scaledWidth) / 2
        let padY = (target - scaledHeight) / 2
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: target, height: target))
        let rendered = renderer.image { context in
            UIColor(red: 114 / 255, green: 114 / 255, blue: 114 / 255, alpha: 1).setFill()
            context.fill(CGRect(x: 0, y: 0, width: target, height: target))
            image.draw(in: CGRect(x: padX, y: padY, width: scaledWidth, height: scaledHeight))
        }
        return LetterboxedImage(image: rendered, scale: scale, padX: padX, padY: padY)
    }

    private func vehicleSegmentationImage(image: UIImage) -> VehicleSegmentationImage {
        let target = CGFloat(vehicleSegmentationSize)
        let scale = target / max(image.size.width, image.size.height)
        let scaledSize = CGSize(width: image.size.width * scale, height: image.size.height * scale)
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: target, height: target))
        let rendered = renderer.image { context in
            UIColor.black.setFill()
            context.fill(CGRect(x: 0, y: 0, width: target, height: target))
            image.draw(in: CGRect(origin: .zero, size: scaledSize))
        }
        return VehicleSegmentationImage(image: rendered, scale: scale)
    }

    private func rgbFloatData(from image: UIImage, width: Int, height: Int) -> [Float] {
        let rgba = rgbaBytes(from: image, width: width, height: height)
        var floats = [Float](repeating: 0, count: width * height * 3)
        let pixelCount = width * height
        for channel in 0..<3 {
            for pixelIndex in 0..<pixelCount {
                floats[channel * pixelCount + pixelIndex] = Float(rgba[pixelIndex * 4 + channel]) / 255
            }
        }
        return floats
    }

    private func grayscaleBytes(from image: UIImage, width: Int, height: Int) -> [UInt8] {
        let rgba = rgbaBytes(from: image, width: width, height: height)
        var grayscale = [UInt8](repeating: 0, count: width * height)
        for index in 0..<(width * height) {
            let offset = index * 4
            let r = Int(rgba[offset])
            let g = Int(rgba[offset + 1])
            let b = Int(rgba[offset + 2])
            grayscale[index] = UInt8((r * 30 + g * 59 + b * 11) / 100)
        }
        return grayscale
    }

    private func rgbaBytes(from image: UIImage, width: Int, height: Int) -> [UInt8] {
        let resized = image.resized(to: CGSize(width: width, height: height))
        var bytes = [UInt8](repeating: 0, count: width * height * 4)
        let colorSpace = CGColorSpaceCreateDeviceRGB()
        bytes.withUnsafeMutableBytes { pointer in
            guard let context = CGContext(
                data: pointer.baseAddress,
                width: width,
                height: height,
                bitsPerComponent: 8,
                bytesPerRow: width * 4,
                space: colorSpace,
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
            ), let cgImage = resized.cgImage else {
                return
            }
            context.draw(cgImage, in: CGRect(x: 0, y: 0, width: width, height: height))
        }
        return bytes
    }

    private func normalizePlateText(_ raw: String) -> String {
        let cleaned = raw.replacingOccurrences(of: "_", with: "")
            .filter { $0.isLetter || $0.isNumber }
            .uppercased()
        guard cleaned.count == 7, cleaned.hasPrefix("T"), cleaned.hasSuffix("C") else {
            return cleaned
        }
        return cleaned
            .replacingOccurrences(of: "I", with: "1")
            .replacingOccurrences(of: "L", with: "1")
            .replacingOccurrences(of: "Z", with: "2")
            .replacingOccurrences(of: "G", with: "6")
            .replacingOccurrences(of: "B", with: "8")
            .replacingOccurrences(of: "A", with: "4")
            .replacingOccurrences(of: "O", with: "0")
    }
}

private extension UIImage {
    func resized(to size: CGSize) -> UIImage {
        if self.size == size {
            return self
        }
        let renderer = UIGraphicsImageRenderer(size: size)
        return renderer.image { _ in
            self.draw(in: CGRect(origin: .zero, size: size))
        }
    }

    func rotated(byDegrees degrees: CGFloat) -> UIImage {
        let radians = degrees * .pi / 180
        let sourceRect = CGRect(origin: .zero, size: size)
        let rotatedRect = sourceRect.applying(CGAffineTransform(rotationAngle: radians))
        let outputSize = CGSize(width: abs(rotatedRect.width), height: abs(rotatedRect.height))
        let renderer = UIGraphicsImageRenderer(size: outputSize)
        return renderer.image { context in
            let cgContext = context.cgContext
            cgContext.translateBy(x: outputSize.width / 2, y: outputSize.height / 2)
            cgContext.rotate(by: radians)
            self.draw(in: CGRect(
                x: -size.width / 2,
                y: -size.height / 2,
                width: size.width,
                height: size.height
            ))
        }
    }
}

private extension Detection {
    var area: CGFloat {
        max(0, x2 - x1) * max(0, y2 - y1)
    }

    func centerDistanceSquared(in imageSize: CGSize) -> CGFloat {
        let centerX = (x1 + x2) / 2
        let centerY = (y1 + y2) / 2
        let dx = centerX - imageSize.width / 2
        let dy = centerY - imageSize.height / 2
        return dx * dx + dy * dy
    }

    func iou(with other: Detection) -> CGFloat {
        let left = max(x1, other.x1)
        let top = max(y1, other.y1)
        let right = min(x2, other.x2)
        let bottom = min(y2, other.y2)
        let intersection = max(0, right - left) * max(0, bottom - top)
        let union = area + other.area - intersection
        return union <= 0 ? 0 : intersection / union
    }
}

private extension NSMutableData {
    func asFloatArray() -> [Float] {
        let count = length / MemoryLayout<Float>.size
        let base = bytes.assumingMemoryBound(to: Float.self)
        return Array(UnsafeBufferPointer(start: base, count: count))
    }
}

private extension ComposerState.PlateCandidate {
    func withVideoFrame(preview: UIImage?, timeSeconds: Double) -> ComposerState.PlateCandidate {
        ComposerState.PlateCandidate(
            plate: plate,
            confidence: confidence,
            state: state,
            stateConfidence: stateConfidence,
            plateType: plateType,
            plateTypeLabel: plateTypeLabel,
            bounds: bounds,
            videoFramePreview: preview,
            videoFrameTimeSeconds: timeSeconds
        )
    }
}

private extension Comparable {
    func clamped(to range: ClosedRange<Self>) -> Self {
        min(max(self, range.lowerBound), range.upperBound)
    }
}

private extension Array {
    subscript(safe index: Index) -> Element? {
        indices.contains(index) ? self[index] : nil
    }
}
