import AVFoundation
import CoreGraphics
import Foundation
import OnnxRuntimeBindings
import SharedCore
import UIKit

let detectorModelName = "yolo-v9-t-640-license-plates-end2end"
let ocrModelName = "global_mobile_vit_v2_ocr"
let plateStateModelName = "reported-plate-class-best"
let complaintModelName = "reported-v13-optimized"
let plateSegmentationModelName = "reported-plate-seg"
let vehicleSegmentationModelName = "yolov8n-seg"
let vehicleSegmentationNmsModelName = "nms-yolov8"
let detectorSize = 640
let complaintDetectorSize = 512
let vehicleSegmentationSize = 640
let vehicleSegmentationClasses = 80
let vehicleSegmentationRowSize = 116
let vehicleSegmentationTopK: Float = 100
let vehicleSegmentationIouThreshold: Float = 0.4
let vehicleSegmentationScoreThreshold: Float = 0.2
let vehicleSegmentationMinScore: Float = 0.5
let vehicleSegmentationMinSide: CGFloat = 60
let plateStateSize = 160
let plateSegmentationSize = 160
let ocrWidth = 140
let ocrHeight = 70
let ocrLegacyBatchSize = 8
let detectionThreshold: Float = 0.2
let complaintDetectionThreshold: Float = 0.65
let complaintClassBlockedBikeLane = 0
let complaintClassBlockedCrosswalk = 1
let segmentationMaskThreshold: Float = 0.5
let maxCandidates = 8
let deskewTriggerDegrees: CGFloat = 1.5
let maxDeskewDegrees = 20
let edgeThreshold = 32
let plateAxisPadding: CGFloat = 0.45
let plateCrossAxisPadding: CGFloat = 0.65
let plateLikeMinAspectRatio: CGFloat = 2.0
let plateLikeMaxAspectRatio: CGFloat = 6.5
let rotatedMatchDistanceMultiplier: CGFloat = 2.5
let ocrAlphabet = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ_")
let plateStateLabels = ["CT", "00", "NJ", "NY", "NY_PD", "NY_TLC", "PA"]
let videoFrameStride = 3
let videoDetectionBatchSize = 3
let videoRotationProbeStride = 9
let videoDryProcessedFrameCount = 3
let videoRotationProbeDegrees: [CGFloat] = [-90, 90, 180]
let vehicleSegmentationLabels: Set<Int> = [2, 5, 7]

struct LetterboxedImage {
    let image: UIImage
    let scale: CGFloat
    let padX: CGFloat
    let padY: CGFloat
}

struct VehicleSegmentationImage {
    let image: UIImage
    let scale: CGFloat
}

struct Detection {
    let x1: CGFloat
    let y1: CGFloat
    let x2: CGFloat
    let y2: CGFloat
    let score: Float
}

struct ComplaintInferenceResult {
    let complaintId: String
    let confidence: Float
    let accepted: Bool
}

struct PlateCrop {
    let ocrImage: UIImage
    let previewImage: UIImage
    let detection: Detection
    let cornerPoints: [CGPoint]
    let rotationDegrees: CGFloat
}

struct RotatedImage {
    let image: UIImage
    let sourceToRotated: CGAffineTransform
}

struct RotatedPlateMatch {
    let rotatedImage: RotatedImage
    let detection: Detection
    let appliedRotationDegrees: CGFloat
    let matchDistanceSquared: CGFloat
}

struct OcrResult {
    let text: String
    let confidence: Double?
}

struct PlateOcrSource {
    let text: String
    let confidence: Double?
    let preferred: Bool
}

struct PlateStateClassification {
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

struct VideoFrameSample {
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
    private var complaintSession: ORTSession?
    private var plateSegmentationSession: ORTSession?
    private var vehicleSegmentationSession: ORTSession?
    private var vehicleSegmentationNmsSession: ORTSession?

    func detectLicensePlates(
        media: ComposerState.SubmissionMedia,
        expectedComplaintHint: String? = nil
    ) async -> [ComposerState.PlateCandidate] {
        guard !media.isVideo else { return [] }
        return await Task.detached(priority: .userInitiated) {
            self.detectLicensePlatesSync(media: media, expectedComplaintHint: expectedComplaintHint)
        }.value
    }

    func detectLicensePlatesInVideo(
        media: ComposerState.SubmissionMedia,
        startTimeSeconds: Double = 0,
        expectedComplaintHint: String? = nil,
        onProgress: @escaping (VideoFrameScanProgress) async -> Void
    ) async -> [ComposerState.PlateCandidate] {
        guard media.isVideo else {
            return await detectLicensePlates(media: media, expectedComplaintHint: expectedComplaintHint)
        }
        return await Task.detached(priority: .userInitiated) {
            await self.detectLicensePlatesInVideoSync(
                media: media,
                startTimeSeconds: startTimeSeconds,
                expectedComplaintHint: expectedComplaintHint,
                onProgress: onProgress
            )
        }.value
    }

    func inferComplaintId(media: ComposerState.SubmissionMedia) async -> String? {
        guard let result = await inferComplaint(media: media), result.accepted else { return nil }
        return result.complaintId
    }

    func inferComplaint(media: ComposerState.SubmissionMedia) async -> ComplaintInferenceResult? {
        guard !media.isVideo else { return nil }
        return await Task.detached(priority: .utility) {
            guard
                let image = UIImage(contentsOfFile: media.fileURL.path),
                let complaint = try? self.session(for: complaintModelName, cached: \.complaintSession)
            else {
                return nil
            }
            return self.detectComplaintInference(in: image, session: complaint)
        }.value
    }

    private func detectLicensePlatesSync(
        media: ComposerState.SubmissionMedia,
        expectedComplaintHint: String?
    ) -> [ComposerState.PlateCandidate] {
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
            complaint: try? session(for: complaintModelName, cached: \.complaintSession),
            expectedComplaintHint: expectedComplaintHint,
            plateSegmentation: sessionIfPresent(for: plateSegmentationModelName, cached: \.plateSegmentationSession),
            vehicleSegmentation: nil,
            vehicleSegmentationNms: nil
        )
    }

    private func detectLicensePlatesInVideoSync(
        media: ComposerState.SubmissionMedia,
        startTimeSeconds: Double,
        expectedComplaintHint: String?,
        onProgress: (VideoFrameScanProgress) async -> Void
    ) async -> [ComposerState.PlateCandidate] {
        guard
            let detector = try? session(for: detectorModelName, cached: \.detectorSession),
            let ocr = try? session(for: ocrModelName, cached: \.ocrSession),
            let plateState = try? session(for: plateStateModelName, cached: \.plateStateSession)
        else {
            return []
        }
        let complaint = try? session(for: complaintModelName, cached: \.complaintSession)
        let plateSegmentation = sessionIfPresent(for: plateSegmentationModelName, cached: \.plateSegmentationSession)
        let asset = AVAsset(url: media.fileURL)
        let videoTrack = (try? await asset.loadTracks(withMediaType: .video))?.first
        let duration = (try? await asset.load(.duration)) ?? .zero
        let durationSeconds = CMTimeGetSeconds(duration)
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

        let nominalFrameRate: Float
        if let videoTrack {
            nominalFrameRate = (try? await videoTrack.load(.nominalFrameRate)) ?? 0
        } else {
            nominalFrameRate = 0
        }
        let frameRate = Double(nominalFrameRate) > 0 ? Double(nominalFrameRate) : 30
        let totalFrames = max(1, Int(ceil(durationSeconds * frameRate)))
        let generator = AVAssetImageGenerator(asset: asset)
        generator.appliesPreferredTrackTransform = true
        generator.requestedTimeToleranceBefore = .zero
        generator.requestedTimeToleranceAfter = .zero

        var bestCandidatesByPlate: [String: ComposerState.PlateCandidate] = [:]
        var plateOrder: [String] = []
        var recentProcessedFramesHadPlate: [Bool] = []
        var lastSuccessfulFallbackRotationDegrees: CGFloat?
        let requestedStartFrame = max(0, Int((min(max(startTimeSeconds, 0), durationSeconds) * frameRate).rounded(.down)))
        let alignedStartFrame = min(totalFrames - 1, (requestedStartFrame / videoFrameStride) * videoFrameStride)
        var index = alignedStartFrame
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
                complaint: complaint,
                expectedComplaintHint: expectedComplaintHint,
                plateSegmentation: plateSegmentation,
                vehicleSegmentation: nil,
                vehicleSegmentationNms: nil
            )
            for (sampleIndex, sample) in samples.enumerated() {
                if Task.isCancelled { break }
                let image = sample.image
                var candidates = batchedCandidates.indices.contains(sampleIndex) ? batchedCandidates[sampleIndex] : []
                if candidates.isEmpty && shouldProbeRotations(frameIndex: sample.frameIndex, recentProcessedFramesHadPlate: recentProcessedFramesHadPlate) {
                    for degrees in orderedVideoRotationProbeDegrees(lastSuccessfulRotationDegrees: lastSuccessfulFallbackRotationDegrees) {
                        let rotatedImage = image.rotated(byDegrees: degrees)
                        let rotatedCandidates = detectLicensePlates(
                            in: rotatedImage,
                            detector: detector,
                            ocr: ocr,
                            plateState: plateState,
                            complaint: complaint,
                            expectedComplaintHint: expectedComplaintHint,
                            plateSegmentation: plateSegmentation,
                            vehicleSegmentation: nil,
                            vehicleSegmentationNms: nil
                        )
                        if !rotatedCandidates.isEmpty {
                            candidates = rotatedCandidates.compactMap {
                                $0.mapFromRotatedToSource(
                                    sourceImage: image,
                                    rotatedSize: rotatedImage.size,
                                    sourceSize: image.size,
                                    degrees: degrees
                                )
                            }
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
        complaint: ORTSession?,
        expectedComplaintHint: String?,
        plateSegmentation: ORTSession?,
        vehicleSegmentation: ORTSession?,
        vehicleSegmentationNms: ORTSession?
    ) -> [ComposerState.PlateCandidate] {
        let detections = Array(detect(in: image, session: detector).prefix(maxCandidates))
#if DEBUG
        let detectionSummary = detections.map(\.logDescription).joined(separator: ", ")
        print("ReportedALPRCrop: detector returned \(detections.count) raw detection(s) for \(Int(image.size.width))x\(Int(image.size.height)): \(detectionSummary)")
#endif
        let works: [(image: UIImage, detection: Detection, crop: PlateCrop)] = detections.compactMap { detection in
            guard let crop = refinedPlateCrop(
                image: image,
                detection: detection,
                detector: detector,
                plateSegmentation: plateSegmentation
            ) else { return nil }
#if DEBUG
            print("ReportedALPRCrop: prepared source=\(detection.logDescription) refined=\(crop.detection.logDescription) rotation=\(crop.rotationDegrees) crop=\(Int(crop.ocrImage.size.width))x\(Int(crop.ocrImage.size.height)) pixels=\(crop.ocrImage.cgImage?.width ?? 0)x\(crop.ocrImage.cgImage?.height ?? 0)")
#endif
            return (image: image, detection: detection, crop: crop)
        }
        let ocrResults = runOcrBatched(images: works.map(\.crop.ocrImage), session: ocr)
        let classifications = classifyPlateStatesBatched(images: works.map(\.crop.ocrImage), session: plateState)
#if DEBUG
        let ocrSummary = works.indices.map { index in
            let ocr = ocrResults[safe: index] ?? OcrResult(text: "", confidence: nil)
            let state = classifications[safe: index] ?? nil
            return "#\(index) rawOcr='\(ocr.text)' state=\(state?.state ?? "-") stateConfidence=\(state?.confidence ?? -1)"
        }.joined(separator: ", ")
        print("ReportedALPRCrop: OCR/state results \(ocrSummary)")
#endif
        var candidates: [ComposerState.PlateCandidate] = []
        for (index, work) in works.enumerated() {
            let ownOcrResult = ocrResults[safe: index] ?? OcrResult(text: "", confidence: nil)
            let preferredPlate = normalizePlateText(ownOcrResult.text)
            let classification = classifications[safe: index] ?? nil
            let refinedDetection = work.crop.detection
            for source in plateCandidateSources(
                ownOcr: ownOcrResult,
                preferredPlate: preferredPlate
            ) {
                guard let candidate = makePlateCandidate(
                    plateText: source.text,
                    ownOcrText: source.text,
                    ownOcrConfidence: source.confidence,
                    imageSize: image.size,
                    sourceDetection: work.detection,
                    refinedDetection: refinedDetection,
                    cornerPoints: work.crop.cornerPoints,
                    cropPreview: work.crop.previewImage,
                    classification: classification,
                    preferred: source.preferred
                ), !candidates.contains(where: { $0.plate == candidate.plate }) else { continue }
                candidates.append(candidate)
            }
        }
        let sorted = candidates.sorted { lhs, rhs in
            lhs.confidence > rhs.confidence
        }
        return rankCandidatesForComplaint(
            sorted,
            image: image,
            complaint: complaint,
            expectedComplaintHint: expectedComplaintHint
        )
    }

    private func detectLicensePlatesBatched(
        images: [UIImage],
        detector: ORTSession,
        ocr: ORTSession,
        plateState: ORTSession,
        complaint: ORTSession?,
        expectedComplaintHint: String?,
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
                complaint: complaint,
                expectedComplaintHint: expectedComplaintHint,
                plateSegmentation: plateSegmentation,
                vehicleSegmentation: vehicleSegmentation,
                vehicleSegmentationNms: vehicleSegmentationNms
            )]
        }
        let detectionsByImage = detectBatched(images: images, session: detector)
        let cropWorkByImage: [[(image: UIImage, detection: Detection, crop: PlateCrop)]] = images.enumerated().map { imageIndex, image in
            let detections = detectionsByImage[safe: imageIndex] ?? []
            return detections.prefix(maxCandidates).compactMap { detection in
                guard let crop = refinedPlateCrop(
                    image: image,
                    detection: detection,
                    detector: detector,
                    plateSegmentation: plateSegmentation
                ) else { return nil }
                return (image: image, detection: detection, crop: crop)
            }
        }
        let allWork = cropWorkByImage.flatMap { $0 }
        let ocrResults = runOcrBatched(images: allWork.map(\.crop.ocrImage), session: ocr)
        let classifications = classifyPlateStatesBatched(images: allWork.map(\.crop.ocrImage), session: plateState)
        var ocrIndex = 0
        var classifierIndex = 0
        return cropWorkByImage.map { works in
            let image = works.first?.image
            var candidates: [ComposerState.PlateCandidate] = []
            for work in works {
                let primaryOcr = ocrResults[safe: ocrIndex] ?? OcrResult(text: "", confidence: nil)
                ocrIndex += 1
                let preferredPlate = normalizePlateText(primaryOcr.text)
                let classification = classifications[safe: classifierIndex] ?? nil
                classifierIndex += 1
                let refinedDetection = work.crop.detection
                for source in plateCandidateSources(
                    ownOcr: primaryOcr,
                    preferredPlate: preferredPlate
                ) {
                    guard let candidate = makePlateCandidate(
                        plateText: source.text,
                        ownOcrText: source.text,
                        ownOcrConfidence: source.confidence,
                        imageSize: work.image.size,
                        sourceDetection: work.detection,
                        refinedDetection: refinedDetection,
                        cornerPoints: work.crop.cornerPoints,
                        cropPreview: work.crop.previewImage,
                        classification: classification,
                        preferred: source.preferred
                    ), !candidates.contains(where: { $0.plate == candidate.plate }) else { continue }
                    candidates.append(candidate)
                }
            }
            let sorted = candidates.sorted { $0.confidence > $1.confidence }
            guard let image else { return sorted }
            return rankCandidatesForComplaint(
                sorted,
                image: image,
                complaint: complaint,
                expectedComplaintHint: expectedComplaintHint
            )
        }
    }

    private func plateCandidateSources(
        ownOcr: OcrResult,
        preferredPlate: String
    ) -> [PlateOcrSource] {
        let reported = normalizePlateText(ownOcr.text)
        var sources: [PlateOcrSource] = []
        if !reported.isEmpty {
            sources.append(PlateOcrSource(
                text: reported,
                confidence: ownOcr.confidence,
                preferred: reported == preferredPlate
            ))
        }
        return sources.sorted { lhs, rhs in
            if lhs.preferred != rhs.preferred { return lhs.preferred }
            return lhs.text < rhs.text
        }
    }

    private func makePlateCandidate(
        plateText: String,
        ownOcrText: String?,
        ownOcrConfidence: Double?,
        imageSize: CGSize,
        sourceDetection: Detection,
        refinedDetection: Detection,
        cornerPoints: [CGPoint],
        cropPreview: UIImage,
        classification: PlateStateClassification?,
        preferred: Bool
    ) -> ComposerState.PlateCandidate? {
        let plate = normalizePlateText(plateText)
        guard !plate.isEmpty else { return nil }
        let patternMatch = PlatePatternClassifier.shared.classify(rawPlate: plate)
        let correctedPlate = patternMatch?.normalizedPlate ?? plate
        let wasPlateCorrected = correctedPlate != plate
        let state = patternMatch?.state ?? (correctedPlate.hasPrefix("T") && correctedPlate.hasSuffix("C") ? "NY" : classification?.state)
        let baseConfidence = Double(min(1, max(sourceDetection.score, refinedDetection.score)))
        let confidence = min(1, baseConfidence + (preferred ? 0.0001 : 0))
        return ComposerState.PlateCandidate(
            plate: correctedPlate,
            confidence: confidence,
            rawPlateText: wasPlateCorrected ? plate : nil,
            wasPlateCorrected: wasPlateCorrected,
            ownOcrText: ownOcrText.flatMap { normalizePlateText($0).nilIfEmpty },
            ownOcrConfidence: ownOcrConfidence,
            state: state,
            stateConfidence: patternMatch.map { Double($0.confidence) } ?? classification?.confidence,
            plateType: patternMatch?.type.name,
            plateTypeLabel: patternMatch?.label,
            bounds: CGRect(
                x: refinedDetection.x1 / imageSize.width,
                y: refinedDetection.y1 / imageSize.height,
                width: (refinedDetection.x2 - refinedDetection.x1) / imageSize.width,
                height: (refinedDetection.y2 - refinedDetection.y1) / imageSize.height
            ),
            cornerPoints: normalizedCornerPoints(cornerPoints, imageSize: imageSize),
            plateCropPreview: cropPreview,
            videoFramePreview: nil,
            videoFramePreviewURL: nil,
            videoFrameTimeSeconds: nil
        )
    }

    private func normalizedCornerPoints(_ points: [CGPoint], imageSize: CGSize) -> [CGPoint] {
        guard points.count >= 4, imageSize.width > 0, imageSize.height > 0 else { return [] }
        return points.prefix(4).map { point in
            CGPoint(
                x: (point.x / imageSize.width).clamped(to: 0...1),
                y: (point.y / imageSize.height).clamped(to: 0...1)
            )
        }
    }

    private func rankCandidatesForComplaint(
        _ candidates: [ComposerState.PlateCandidate],
        image: UIImage,
        complaint: ORTSession?,
        expectedComplaintHint: String?
    ) -> [ComposerState.PlateCandidate] {
        guard candidates.count > 1,
              let expectedLabel = expectedComplaintHint.expectedComplaintClass(),
              let complaint
        else {
            return candidates
        }
        let regions = detectComplaintRegions(in: image, session: complaint, expectedLabel: expectedLabel)
        guard !regions.isEmpty else { return candidates }
        return candidates.sorted { lhs, rhs in
            let lhsScore = lhs.complaintLocationScore(regions: regions, imageSize: image.size)
            let rhsScore = rhs.complaintLocationScore(regions: regions, imageSize: image.size)
            if abs(lhsScore - rhsScore) > 0.001 { return lhsScore > rhsScore }
            if abs(lhs.confidence - rhs.confidence) > 0.001 { return lhs.confidence > rhs.confidence }
            return lhs.centerBiasedScore() > rhs.centerBiasedScore()
        }
    }

    private func detectComplaintInference(
        in image: UIImage,
        session: ORTSession
    ) -> ComplaintInferenceResult? {
        let letterboxed = letterbox(image: image, targetSize: complaintDetectorSize)
        guard let raw = runFloatModel(
            session: session,
            input: rgbFloatData(from: letterboxed.image, width: complaintDetectorSize, height: complaintDetectorSize),
            shape: [1, 3, complaintDetectorSize, complaintDetectorSize].map(NSNumber.init(value:))
        ), !raw.isEmpty else {
            return nil
        }
        let rowSize = inferComplaintRowSize(raw.count)
        guard rowSize >= 2 else { return nil }
        let rowCount = raw.count / rowSize
        let scores = (0..<rowCount)
            .compactMap { rowIndex -> (label: Int, score: Float)? in
                let offset = rowIndex * rowSize
                guard let (label, score) = decodeComplaintScore(raw: raw, offset: offset, rowSize: rowSize),
                      label == complaintClassBlockedBikeLane || label == complaintClassBlockedCrosswalk else {
                    return nil
                }
                return (label, score)
            }
        let bikeLaneScore = scores
            .filter { $0.label == complaintClassBlockedBikeLane }
            .map(\.score)
            .max()
        let crosswalkScore = scores
            .filter { $0.label == complaintClassBlockedCrosswalk }
            .map(\.score)
            .max()
        let best = [
            bikeLaneScore.map { (label: complaintClassBlockedBikeLane, score: $0) },
            crosswalkScore.map { (label: complaintClassBlockedCrosswalk, score: $0) }
        ]
        .compactMap { $0 }
        .max { lhs, rhs in lhs.score < rhs.score }
        guard let best else { return nil }
        return ComplaintInferenceResult(
            complaintId: best.label == complaintClassBlockedBikeLane ? "Z8vjWz8uYr" : "GzRxlMN1vl",
            confidence: best.score,
            accepted: best.score >= complaintDetectionThreshold
        )
    }

    private func detectComplaintRegions(
        in image: UIImage,
        session: ORTSession,
        expectedLabel: Int
    ) -> [Detection] {
        let letterboxed = letterbox(image: image, targetSize: complaintDetectorSize)
        guard let raw = runFloatModel(
            session: session,
            input: rgbFloatData(from: letterboxed.image, width: complaintDetectorSize, height: complaintDetectorSize),
            shape: [1, 3, complaintDetectorSize, complaintDetectorSize].map(NSNumber.init(value:))
        ), !raw.isEmpty else {
            return []
        }
        let rowSize = inferComplaintRowSize(raw.count)
        guard rowSize >= 6 else { return [] }
        let rowCount = raw.count / rowSize
        return (0..<rowCount).compactMap { rowIndex in
            let offset = rowIndex * rowSize
            guard let (label, score) = decodeComplaintScore(raw: raw, offset: offset, rowSize: rowSize),
                  label == expectedLabel,
                  score >= complaintDetectionThreshold
            else {
                return nil
            }
            let coordinateOffset = rowSize >= 7 ? 1 : 0
            let x1 = ((CGFloat(raw[offset + coordinateOffset]) - letterboxed.padX) / letterboxed.scale)
                .clamped(to: 0...image.size.width)
            let y1 = ((CGFloat(raw[offset + coordinateOffset + 1]) - letterboxed.padY) / letterboxed.scale)
                .clamped(to: 0...image.size.height)
            let x2 = ((CGFloat(raw[offset + coordinateOffset + 2]) - letterboxed.padX) / letterboxed.scale)
                .clamped(to: 0...image.size.width)
            let y2 = ((CGFloat(raw[offset + coordinateOffset + 3]) - letterboxed.padY) / letterboxed.scale)
                .clamped(to: 0...image.size.height)
            guard x2 - x1 >= 8, y2 - y1 >= 8 else { return nil }
            return Detection(x1: x1, y1: y1, x2: x2, y2: y2, score: score)
        }
    }

    private func inferComplaintRowSize(_ flatSize: Int) -> Int {
        if flatSize % 6 == 0 { return 6 }
        if flatSize % 7 == 0 { return 7 }
        if flatSize % 5 == 0 { return 5 }
        return flatSize
    }

    private func decodeComplaintScore(raw: [Float], offset: Int, rowSize: Int) -> (Int, Float)? {
        if rowSize >= 7 {
            return (Int(raw[offset + 5].rounded()), normalizedComplaintScore(raw[offset + 6]))
        }
        if rowSize == 6 {
            let bikeLaneScore = normalizedComplaintScore(raw[offset + 4])
            let crosswalkScore = normalizedComplaintScore(raw[offset + 5])
            if bikeLaneScore >= crosswalkScore {
                return (complaintClassBlockedBikeLane, bikeLaneScore)
            }
            return (complaintClassBlockedCrosswalk, crosswalkScore)
        }
        if rowSize == 5 {
            return (Int(raw[offset + 4].rounded()), normalizedComplaintScore(raw[offset + 3]))
        }
        guard rowSize >= 2 else { return nil }
        let label = (0..<rowSize).max { raw[offset + $0] < raw[offset + $1] } ?? 0
        return (label, normalizedComplaintScore(raw[offset + label]))
    }

    private func normalizedComplaintScore(_ value: Float) -> Float {
        guard value.isFinite else { return 0 }
        return min(1, max(0, value))
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

}
