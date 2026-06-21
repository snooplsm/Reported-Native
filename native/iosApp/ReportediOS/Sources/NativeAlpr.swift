import AVFoundation
import CoreGraphics
import Foundation
import OnnxRuntimeBindings
import SharedCore
import UIKit

private let detectorModelName = "yolo-v9-t-640-license-plates-end2end"
private let ocrModelName = "global_mobile_vit_v2_ocr"
private let plateStateModelName = "reported-plate-class-best"
private let complaintModelName = "reported-v13-optimized"
private let plateSegmentationModelName = "reported-plate-seg"
private let vehicleSegmentationModelName = "yolov8n-seg"
private let vehicleSegmentationNmsModelName = "nms-yolov8"
private let detectorSize = 640
private let complaintDetectorSize = 512
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
private let ocrLegacyBatchSize = 8
private let detectionThreshold: Float = 0.2
private let complaintDetectionThreshold: Float = 0.65
private let complaintClassBlockedBikeLane = 0
private let complaintClassBlockedCrosswalk = 1
private let segmentationMaskThreshold: Float = 0.5
private let maxCandidates = 8
private let deskewTriggerDegrees: CGFloat = 1.5
private let maxDeskewDegrees = 20
private let edgeThreshold = 32
private let plateAxisPadding: CGFloat = 0.45
private let plateCrossAxisPadding: CGFloat = 0.65
private let plateLikeMinAspectRatio: CGFloat = 2.0
private let plateLikeMaxAspectRatio: CGFloat = 6.5
private let rotatedMatchDistanceMultiplier: CGFloat = 2.5
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

struct ComplaintInferenceResult {
    let complaintId: String
    let confidence: Float
    let accepted: Bool
}

private struct PlateCrop {
    let ocrImage: UIImage
    let previewImage: UIImage
    let detection: Detection
    let cornerPoints: [CGPoint]
    let rotationDegrees: CGFloat
}

private struct RotatedImage {
    let image: UIImage
    let sourceToRotated: CGAffineTransform
}

private struct RotatedPlateMatch {
    let rotatedImage: RotatedImage
    let detection: Detection
    let appliedRotationDegrees: CGFloat
    let matchDistanceSquared: CGFloat
}

private struct OcrResult {
    let text: String
    let confidence: Double?
}

private struct PlateOcrSource {
    let text: String
    let confidence: Double?
    let preferred: Bool
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

    private func runOcr(image: UIImage, session: ORTSession) -> OcrResult {
        runOcrBatched(images: [image], session: session).first ?? OcrResult(text: "", confidence: nil)
    }

    private func runOcrBatched(images: [UIImage], session: ORTSession) -> [OcrResult] {
        guard !images.isEmpty else { return [] }
        var results: [OcrResult] = []
        var index = 0
        while index < images.count {
            let end = min(index + ocrLegacyBatchSize, images.count)
            let chunk = Array(images[index..<end])
            results.append(contentsOf: runOcrLegacyChunk(images: chunk, session: session))
            index = end
        }
        return results
    }

    private func runOcrLegacyChunk(images: [UIImage], session: ORTSession) -> [OcrResult] {
        guard !images.isEmpty, images.count <= ocrLegacyBatchSize else { return [] }
        let imageByteCount = ocrWidth * ocrHeight
        var bytes = [UInt8](repeating: 0, count: ocrLegacyBatchSize * imageByteCount)
        for (index, image) in images.enumerated() {
            let imageBytes = grayscaleBytes(from: image, width: ocrWidth, height: ocrHeight)
            let offset = index * imageByteCount
            bytes.replaceSubrange(offset..<(offset + imageByteCount), with: imageBytes)
        }
        let data = bytes.withUnsafeBufferPointer { pointer in
            NSMutableData(bytes: pointer.baseAddress, length: bytes.count)
        }
        guard
            let inputName = try? session.inputNames().first,
            let outputNames = try? session.outputNames(),
            let input = try? ORTValue(
                tensorData: data,
                elementType: .uInt8,
                shape: [ocrLegacyBatchSize, ocrHeight, ocrWidth, 1].map(NSNumber.init(value:))
            ),
            let outputs = try? session.run(withInputs: [inputName: input], outputNames: Set(outputNames), runOptions: nil),
            let output = outputs[outputNames[0]],
            let raw = try? output.tensorData().asFloatArray(),
            raw.count >= images.count
        else {
            return images.map { _ in OcrResult(text: "", confidence: nil) }
        }
        let rowSize = raw.count / ocrLegacyBatchSize
        guard rowSize > 0 else {
            return images.map { _ in OcrResult(text: "", confidence: nil) }
        }
        return (0..<images.count).map { index in
            let start = index * rowSize
            let end = min(raw.count, start + rowSize)
            return decodeOcrResult(Array(raw[start..<end]))
        }
    }

    private func decodeOcrResult(_ raw: [Float]) -> OcrResult {
        let slotCount = raw.count / ocrAlphabet.count
        guard slotCount > 0 else { return OcrResult(text: "", confidence: nil) }
        var slotConfidences: [Double] = []
        let text = (0..<slotCount).map { slotIndex in
            let start = slotIndex * ocrAlphabet.count
            let end = start + ocrAlphabet.count
            let bestPair = raw[start..<end].enumerated().max { $0.element < $1.element }
            let best = bestPair?.offset ?? 0
            slotConfidences.append(Double(bestPair?.element ?? 0))
            return String(ocrAlphabet[best])
        }.joined()
        let visibleConfidences = zip(Array(text), slotConfidences)
            .filter { character, _ in character != "_" }
            .map(\.1)
        let confidenceSource = visibleConfidences.isEmpty ? slotConfidences : visibleConfidences
        let averageConfidence = confidenceSource.isEmpty ? nil : confidenceSource.reduce(0, +) / Double(confidenceSource.count)
        return OcrResult(text: text, confidence: averageConfidence)
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

    private func segmentPlateCrop(
        source: UIImage,
        expandedDetection: Detection,
        expandedCrop: UIImage,
        session: ORTSession?
    ) -> PlateCrop? {
        guard let session else {
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
        guard let cropPoints = maskToOrientedBox(raw: raw, side: side, cropSize: expandedCrop.size) else {
            return nil
        }
        let sourcePoints = cropPoints.map { point in
            CGPoint(
                x: (point.x + expandedDetection.x1).clamped(to: 0...source.size.width),
                y: (point.y + expandedDetection.y1).clamped(to: 0...source.size.height)
            )
        }
        let detection = boundingDetection(
            points: sourcePoints,
            score: expandedDetection.score,
            fallback: expandedDetection
        )
        let bitmap = crop(image: source, detection: detection) ?? expandedCrop
        return PlateCrop(
            ocrImage: bitmap,
            previewImage: bitmap,
            detection: detection,
            cornerPoints: sourcePoints,
            rotationDegrees: estimateAngle(from: sourcePoints)
        )
    }

    private func maskToOrientedBox(raw: [Float], side: Int, cropSize: CGSize) -> [CGPoint]? {
        var points: [CGPoint] = []
        let logitOutput = raw.contains { $0 < 0 || $0 > 1 }
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
                points.append(CGPoint(x: px, y: py))
            }
        }
        guard points.count >= 8 else {
            return nil
        }

        let meanX = points.reduce(CGFloat(0)) { $0 + $1.x } / CGFloat(points.count)
        let meanY = points.reduce(CGFloat(0)) { $0 + $1.y } / CGFloat(points.count)
        var xx: CGFloat = 0
        var yy: CGFloat = 0
        var xy: CGFloat = 0
        points.forEach { point in
            let dx = point.x - meanX
            let dy = point.y - meanY
            xx += dx * dx
            yy += dy * dy
            xy += dx * dy
        }
        let axisAngle = 0.5 * atan2(2 * xy, xx - yy)
        let axisX = cos(axisAngle)
        let axisY = sin(axisAngle)
        let crossX = -axisY
        let crossY = axisX
        var minAxis = CGFloat.greatestFiniteMagnitude
        var maxAxis = -CGFloat.greatestFiniteMagnitude
        var minCross = CGFloat.greatestFiniteMagnitude
        var maxCross = -CGFloat.greatestFiniteMagnitude
        points.forEach { point in
            let dx = point.x - meanX
            let dy = point.y - meanY
            let axis = dx * axisX + dy * axisY
            let cross = dx * crossX + dy * crossY
            minAxis = min(minAxis, axis)
            maxAxis = max(maxAxis, axis)
            minCross = min(minCross, cross)
            maxCross = max(maxCross, cross)
        }
        guard maxAxis - minAxis >= 4, maxCross - minCross >= 4 else {
            return nil
        }

        func corner(axis: CGFloat, cross: CGFloat) -> CGPoint {
            CGPoint(
                x: (meanX + axis * axisX + cross * crossX).clamped(to: 0...cropSize.width),
                y: (meanY + axis * axisY + cross * crossY).clamped(to: 0...cropSize.height)
            )
        }
        return [
            corner(axis: minAxis, cross: minCross),
            corner(axis: maxAxis, cross: minCross),
            corner(axis: maxAxis, cross: maxCross),
            corner(axis: minAxis, cross: maxCross)
        ]
    }

    private func estimateAngle(from points: [CGPoint]) -> CGFloat {
        guard points.count >= 2 else { return 0 }
        let dx = points[1].x - points[0].x
        let dy = points[1].y - points[0].y
        return normalizePlateAngle(atan2(dy, dx) * 180 / .pi)
    }

    private func boundingDetection(points: [CGPoint], score: Float, fallback: Detection) -> Detection {
        guard points.count >= 4 else { return fallback }
        let minX = points.map(\.x).min() ?? fallback.x1
        let maxX = points.map(\.x).max() ?? fallback.x2
        let minY = points.map(\.y).min() ?? fallback.y1
        let maxY = points.map(\.y).max() ?? fallback.y2
        guard maxX - minX >= 8, maxY - minY >= 8 else {
            return fallback
        }
        return Detection(x1: minX, y1: minY, x2: maxX, y2: maxY, score: score)
    }

    private func refinedPlateCrop(
        image: UIImage,
        detection: Detection,
        detector: ORTSession,
        plateSegmentation: ORTSession?
    ) -> PlateCrop? {
        guard let initialPlateCrop = crop(image: image, detection: detection) else {
            return nil
        }
        if detection.hasPlateLikeAspectRatio {
            return PlateCrop(
                ocrImage: initialPlateCrop,
                previewImage: initialPlateCrop,
                detection: detection,
                cornerPoints: detection.cornerPoints(),
                rotationDegrees: 0
            )
        }

        let expandedDetection = expandedPlateDetection(image: image, detection: detection)
        let initialAngle = estimateDeskewAngle(source: initialPlateCrop)
        guard let expandedCrop = crop(image: image, detection: expandedDetection) else {
            return PlateCrop(
                ocrImage: initialPlateCrop,
                previewImage: initialPlateCrop,
                detection: detection,
                cornerPoints: detection.cornerPoints(),
                rotationDegrees: 0
            )
        }

        let angle: CGFloat
        if abs(initialAngle) >= deskewTriggerDegrees {
            angle = initialAngle
        } else {
            angle = estimateDeskewAngle(source: expandedCrop)
        }
        guard abs(angle) >= deskewTriggerDegrees else {
            return PlateCrop(
                ocrImage: initialPlateCrop,
                previewImage: initialPlateCrop,
                detection: detection,
                cornerPoints: detection.cornerPoints(),
                rotationDegrees: 0
            )
        }

        guard let rotatedMatch = findBestRotatedPlateMatch(
            image: image,
            detection: detection,
            estimatedAngle: angle,
            detector: detector
        ) else {
            return PlateCrop(
                ocrImage: initialPlateCrop,
                previewImage: initialPlateCrop,
                detection: detection,
                cornerPoints: detection.cornerPoints(),
                rotationDegrees: 0
            )
        }

        let rotatedExpandedDetection = expandedPlateDetection(
            image: rotatedMatch.rotatedImage.image,
            detection: rotatedMatch.detection
        )
        let rotatedExpandedCrop = crop(image: rotatedMatch.rotatedImage.image, detection: rotatedExpandedDetection)
        let segmentedRotatedCrop = rotatedExpandedCrop.flatMap {
            segmentPlateCrop(
                source: rotatedMatch.rotatedImage.image,
                expandedDetection: rotatedExpandedDetection,
                expandedCrop: $0,
                session: plateSegmentation
            )
        }
        let ocrDetection = segmentedRotatedCrop?.detection ?? rotatedMatch.detection
        guard let plateImage = crop(image: rotatedMatch.rotatedImage.image, detection: ocrDetection)
            ?? crop(image: rotatedMatch.rotatedImage.image, detection: rotatedMatch.detection)
        else {
            return nil
        }

        return PlateCrop(
            ocrImage: plateImage,
            previewImage: initialPlateCrop,
            detection: detection.withScore(ocrDetection.score),
            // Keep the OCR crop refined, but render the stable detector rectangle.
            // The segmentation polygon can overfit bumper text or plate trim and show a fake slant.
            cornerPoints: detection.cornerPoints(),
            rotationDegrees: -rotatedMatch.appliedRotationDegrees
        )
    }

    private func findBestRotatedPlateMatch(
        image: UIImage,
        detection: Detection,
        estimatedAngle: CGFloat,
        detector: ORTSession
    ) -> RotatedPlateMatch? {
        var rotations: [CGFloat] = []
        for rotation in [-estimatedAngle, estimatedAngle] {
            let rounded = Int(rotation.rounded())
            guard !rotations.contains(where: { Int($0.rounded()) == rounded }) else { continue }
            rotations.append(rotation)
        }

        let matches = rotations.compactMap { rotationDegrees -> RotatedPlateMatch? in
            let rotatedImage = image.rotatedWithTransform(byDegrees: rotationDegrees)
            let expectedDetection = detection.mapped(with: rotatedImage.sourceToRotated)
            guard let rotatedDetection = bestMatch(
                in: detect(in: rotatedImage.image, session: detector),
                for: expectedDetection
            ) else {
                return nil
            }
            return RotatedPlateMatch(
                rotatedImage: rotatedImage,
                detection: rotatedDetection,
                appliedRotationDegrees: rotationDegrees,
                matchDistanceSquared: rotatedDetection.centerDistanceSquared(from: expectedDetection)
            )
        }

        return matches.sorted { lhs, rhs in
            if abs(lhs.detection.score - rhs.detection.score) > 0.0001 {
                return lhs.detection.score > rhs.detection.score
            }
            return lhs.matchDistanceSquared < rhs.matchDistanceSquared
        }.first
    }

    private func bestMatch(in detections: [Detection], for expected: Detection) -> Detection? {
        guard !detections.isEmpty else { return nil }
        let maxDistance = max(expected.width, expected.height) * rotatedMatchDistanceMultiplier
        return detections.min { lhs, rhs in
            lhs.centerDistanceSquared(from: expected) < rhs.centerDistanceSquared(from: expected)
        }.flatMap { detection in
            detection.centerDistanceSquared(from: expected) <= maxDistance * maxDistance ? detection : nil
        }
    }

    private func expandedPlateDetection(image: UIImage, detection: Detection) -> Detection {
        let width = detection.width
        let height = detection.height
        return Detection(
            x1: (detection.x1 - width * plateAxisPadding).clamped(to: 0...image.size.width),
            y1: (detection.y1 - height * plateCrossAxisPadding).clamped(to: 0...image.size.height),
            x2: (detection.x2 + width * plateAxisPadding).clamped(to: 0...image.size.width),
            y2: (detection.y2 + height * plateCrossAxisPadding).clamped(to: 0...image.size.height),
            score: detection.score
        )
    }

    private func estimateDeskewAngle(source: UIImage) -> CGFloat {
        let analysisWidth = 220
        let scale = CGFloat(analysisWidth) / max(source.size.width, 1)
        let analysisHeight = max(48, Int((source.size.height * scale).rounded()))
        let rgba = rgbaBytes(from: source, width: analysisWidth, height: analysisHeight)
        var grayscale = [Int](repeating: 0, count: analysisWidth * analysisHeight)
        for index in 0..<(analysisWidth * analysisHeight) {
            let offset = index * 4
            let r = Int(rgba[offset])
            let g = Int(rgba[offset + 1])
            let b = Int(rgba[offset + 2])
            grayscale[index] = (r * 30 + g * 59 + b * 11) / 100
        }

        var allEdgePoints: [(x: Int, y: Int)] = []
        var horizontalEdgePoints: [(x: Int, y: Int)] = []
        var orientationVotes: [Int: Int] = [:]
        for y in 1..<(analysisHeight - 1) {
            for x in 1..<(analysisWidth - 1) {
                let index = y * analysisWidth + x
                let gx = grayscale[index + 1] - grayscale[index - 1]
                let gy = grayscale[index + analysisWidth] - grayscale[index - analysisWidth]
                let magnitude = abs(gx) + abs(gy)
                guard magnitude >= edgeThreshold else { continue }

                allEdgePoints.append((x, y))
                if CGFloat(abs(gy)) > CGFloat(abs(gx)) * 0.65 {
                    horizontalEdgePoints.append((x, y))
                    let edgeAngle = normalizePlateAngle(
                        CGFloat(atan2(Double(-gx), Double(gy)) * 180 / Double.pi)
                    )
                    let angleBin = Int(edgeAngle.rounded()).clamped(to: -maxDeskewDegrees...maxDeskewDegrees)
                    orientationVotes[angleBin, default: 0] += magnitude
                }
            }
        }

        if let bestOrientation = orientationVotes.max(by: { $0.value < $1.value }),
           bestOrientation.value >= edgeThreshold * 18 {
            return CGFloat(bestOrientation.key)
        }

        let edgePoints = horizontalEdgePoints.count >= 24 ? horizontalEdgePoints : allEdgePoints
        guard edgePoints.count >= 24 else { return 0 }

        var bestAngle = 0
        var bestVotes = 0
        for angle in -maxDeskewDegrees...maxDeskewDegrees {
            let normalRadians = CGFloat(angle + 90) * .pi / 180
            let cosTheta = cos(normalRadians)
            let sinTheta = sin(normalRadians)
            var rhoVotes: [Int: Int] = [:]
            for point in edgePoints {
                let rho = Int((CGFloat(point.x) * cosTheta + CGFloat(point.y) * sinTheta).rounded())
                let votes = rhoVotes[rho, default: 0] + 1
                rhoVotes[rho] = votes
                if votes > bestVotes {
                    bestVotes = votes
                    bestAngle = angle
                }
            }
        }
        return CGFloat(bestAngle)
    }

    private func normalizePlateAngle(_ degrees: CGFloat) -> CGFloat {
        var normalized = degrees
        while normalized > 45 { normalized -= 90 }
        while normalized < -45 { normalized += 90 }
        return normalized.clamped(to: -CGFloat(maxDeskewDegrees)...CGFloat(maxDeskewDegrees))
    }

    private func crop(image: UIImage, detection: Detection) -> UIImage? {
        guard let cgImage = image.cgImage else { return nil }
        let scaleX = CGFloat(cgImage.width) / image.size.width
        let scaleY = CGFloat(cgImage.height) / image.size.height
        let left = max(0, Int(detection.x1 * scaleX))
        let top = max(0, Int(detection.y1 * scaleY))
        let right = min(cgImage.width, Int(detection.x2 * scaleX))
        let bottom = min(cgImage.height, Int(detection.y2 * scaleY))
        let width = right - left
        let height = bottom - top
        guard width > 2, height > 2 else { return nil }
        let rect = CGRect(x: left, y: top, width: width, height: height)
        guard let cropped = cgImage.cropping(to: rect) else { return nil }
        return UIImage(cgImage: cropped)
    }

    private func letterbox(image: UIImage, targetSize: Int) -> LetterboxedImage {
        let target = CGFloat(targetSize)
        let scale = min(target / image.size.width, target / image.size.height)
        let scaledWidth = max(1, floor(image.size.width * scale))
        let scaledHeight = max(1, floor(image.size.height * scale))
        let padX = (target - scaledWidth) / 2
        let padY = (target - scaledHeight) / 2
        let rendererFormat = UIGraphicsImageRendererFormat()
        rendererFormat.scale = 1
        rendererFormat.opaque = true
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: target, height: target), format: rendererFormat)
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
        let rendererFormat = UIGraphicsImageRendererFormat()
        rendererFormat.scale = 1
        rendererFormat.opaque = true
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: target, height: target), format: rendererFormat)
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
        return raw.replacingOccurrences(of: "_", with: "")
            .filter { $0.isLetter || $0.isNumber }
            .uppercased()
    }
}

private extension UIImage {
    func resized(to size: CGSize) -> UIImage {
        if self.size == size {
            return self
        }
        let rendererFormat = UIGraphicsImageRendererFormat()
        rendererFormat.scale = 1
        rendererFormat.opaque = true
        let renderer = UIGraphicsImageRenderer(size: size, format: rendererFormat)
        return renderer.image { _ in
            self.draw(in: CGRect(origin: .zero, size: size))
        }
    }

    func rotated(byDegrees degrees: CGFloat) -> UIImage {
        rotatedWithTransform(byDegrees: degrees).image
    }

    func rotatedWithinBounds(byDegrees degrees: CGFloat) -> UIImage {
        if abs(degrees) < 0.0001 {
            return self
        }
        let radians = degrees * .pi / 180
        let rendererFormat = UIGraphicsImageRendererFormat()
        rendererFormat.scale = 1
        rendererFormat.opaque = true
        let renderer = UIGraphicsImageRenderer(size: size, format: rendererFormat)
        return renderer.image { context in
            let rect = CGRect(origin: .zero, size: size)
            self.draw(in: rect)
            let cgContext = context.cgContext
            cgContext.translateBy(x: size.width / 2, y: size.height / 2)
            cgContext.rotate(by: radians)
            self.draw(in: CGRect(
                x: -size.width / 2,
                y: -size.height / 2,
                width: size.width,
                height: size.height
            ))
        }
    }

    func rotatedWithTransform(byDegrees degrees: CGFloat) -> RotatedImage {
        if abs(degrees) < 0.0001 {
            return RotatedImage(image: self, sourceToRotated: .identity)
        }
        let radians = degrees * .pi / 180
        let sourceRect = CGRect(origin: .zero, size: size)
        let rotatedRect = sourceRect.applying(CGAffineTransform(rotationAngle: radians))
        let outputSize = CGSize(
            width: max(1, abs(rotatedRect.width).rounded()),
            height: max(1, abs(rotatedRect.height).rounded())
        )
        var sourceToRotated = CGAffineTransform.identity
        sourceToRotated = sourceToRotated.translatedBy(x: outputSize.width / 2, y: outputSize.height / 2)
        sourceToRotated = sourceToRotated.rotated(by: radians)
        sourceToRotated = sourceToRotated.translatedBy(x: -size.width / 2, y: -size.height / 2)
        let rendererFormat = UIGraphicsImageRendererFormat()
        rendererFormat.scale = 1
        rendererFormat.opaque = true
        let renderer = UIGraphicsImageRenderer(size: outputSize, format: rendererFormat)
        let rendered = renderer.image { context in
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
        return RotatedImage(image: rendered, sourceToRotated: sourceToRotated)
    }
}

private extension Detection {
    var width: CGFloat {
        max(0, x2 - x1)
    }

    var height: CGFloat {
        max(0, y2 - y1)
    }

    var area: CGFloat {
        width * height
    }

    var hasPlateLikeAspectRatio: Bool {
        guard width > 0, height > 0 else { return false }
        let aspectRatio = width / height
        return aspectRatio >= plateLikeMinAspectRatio && aspectRatio <= plateLikeMaxAspectRatio
    }

    var logDescription: String {
        "box=[\(Int(x1)),\(Int(y1)),\(Int(x2)),\(Int(y2))] score=\(score) size=\(Int(width))x\(Int(height))"
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

    func centerDistanceSquared(from expected: Detection) -> CGFloat {
        let centerX = (x1 + x2) / 2
        let centerY = (y1 + y2) / 2
        let expectedCenterX = (expected.x1 + expected.x2) / 2
        let expectedCenterY = (expected.y1 + expected.y2) / 2
        let dx = centerX - expectedCenterX
        let dy = centerY - expectedCenterY
        return dx * dx + dy * dy
    }

    func mapped(with transform: CGAffineTransform) -> Detection {
        let points = [
            CGPoint(x: x1, y: y1),
            CGPoint(x: x2, y: y1),
            CGPoint(x: x2, y: y2),
            CGPoint(x: x1, y: y2)
        ].map { $0.applying(transform) }
        let minX = points.map(\.x).min() ?? x1
        let maxX = points.map(\.x).max() ?? x2
        let minY = points.map(\.y).min() ?? y1
        let maxY = points.map(\.y).max() ?? y2
        guard maxX - minX >= 8, maxY - minY >= 8 else {
            return self
        }
        return Detection(x1: minX, y1: minY, x2: maxX, y2: maxY, score: score)
    }

    func cornerPoints() -> [CGPoint] {
        [
            CGPoint(x: x1, y: y1),
            CGPoint(x: x2, y: y1),
            CGPoint(x: x2, y: y2),
            CGPoint(x: x1, y: y2)
        ]
    }

    func originalImageCornerPoints(rotatedImage: RotatedImage, originalDetection: Detection) -> [CGPoint] {
        let rotatedToSource = rotatedImage.sourceToRotated.inverted()
        let points = cornerPoints().map { $0.applying(rotatedToSource) }
        return points.count >= 4 ? points : originalDetection.cornerPoints()
    }

    func withScore(_ score: Float) -> Detection {
        Detection(x1: x1, y1: y1, x2: x2, y2: y2, score: score)
    }

    func clamped(to size: CGSize) -> Detection {
        Detection(
            x1: x1.clamped(to: 0...size.width),
            y1: y1.clamped(to: 0...size.height),
            x2: x2.clamped(to: 0...size.width),
            y2: y2.clamped(to: 0...size.height),
            score: score
        )
    }
}

private extension CGPoint {
    func clamped(to size: CGSize) -> CGPoint {
        CGPoint(
            x: x.clamped(to: 0...size.width),
            y: y.clamped(to: 0...size.height)
        )
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
    func complaintLocationScore(regions: [Detection], imageSize: CGSize) -> Double {
        guard let bounds, !regions.isEmpty else { return 0 }
        let center = CGPoint(
            x: bounds.midX * imageSize.width,
            y: bounds.midY * imageSize.height
        )
        return regions.map { region in
            let inside = centerInside(region: region, center: center)
            let regionCenter = CGPoint(x: (region.x1 + region.x2) / 2, y: (region.y1 + region.y2) / 2)
            let dx = (center.x - regionCenter.x) / max(imageSize.width, 1)
            let dy = (center.y - regionCenter.y) / max(imageSize.height, 1)
            let proximity = max(0, min(1, 1 - sqrt(dx * dx + dy * dy) / 0.70710677))
            let overlap = normalizedOverlap(with: region, imageSize: imageSize)
            return (inside ? 2 : 0) + proximity + overlap
        }.max() ?? 0
    }

    private func centerInside(region: Detection, center: CGPoint) -> Bool {
        let paddingX = (region.x2 - region.x1) * 0.04
        let paddingY = (region.y2 - region.y1) * 0.04
        return center.x >= region.x1 - paddingX &&
            center.x <= region.x2 + paddingX &&
            center.y >= region.y1 - paddingY &&
            center.y <= region.y2 + paddingY
    }

    private func normalizedOverlap(with region: Detection, imageSize: CGSize) -> Double {
        guard let bounds else { return 0 }
        let plateRect = CGRect(
            x: bounds.minX * imageSize.width,
            y: bounds.minY * imageSize.height,
            width: bounds.width * imageSize.width,
            height: bounds.height * imageSize.height
        )
        let regionRect = CGRect(x: region.x1, y: region.y1, width: region.x2 - region.x1, height: region.y2 - region.y1)
        let intersection = plateRect.intersection(regionRect)
        guard !intersection.isNull, plateRect.width > 0, plateRect.height > 0 else { return 0 }
        return max(0, min(1, (intersection.width * intersection.height) / (plateRect.width * plateRect.height)))
    }

    func centerInside(anyOf regions: [Detection], imageSize: CGSize) -> Bool {
        guard let bounds else { return false }
        let center = CGPoint(
            x: bounds.midX * imageSize.width,
            y: bounds.midY * imageSize.height
        )
        return regions.contains { centerInside(region: $0, center: center) }
    }

    func centerBiasedScore() -> Double {
        guard let bounds else { return confidence }
        let dx = bounds.midX - 0.5
        let dy = bounds.midY - 0.5
        let distance = sqrt(dx * dx + dy * dy)
        let centerScore = max(0, min(1, 1 - distance / 0.70710677))
        return confidence * 0.72 + centerScore * 0.28
    }

    func mapFromRotatedToSource(sourceImage: UIImage, rotatedSize: CGSize, sourceSize: CGSize, degrees: CGFloat) -> ComposerState.PlateCandidate? {
        guard
            let bounds,
            sourceSize.width > 0,
            sourceSize.height > 0,
            rotatedSize.width > 0,
            rotatedSize.height > 0
        else {
            return nil
        }

        let radians = degrees * .pi / 180
        var sourceToRotated = CGAffineTransform.identity
        sourceToRotated = sourceToRotated.translatedBy(x: rotatedSize.width / 2, y: rotatedSize.height / 2)
        sourceToRotated = sourceToRotated.rotated(by: radians)
        sourceToRotated = sourceToRotated.translatedBy(x: -sourceSize.width / 2, y: -sourceSize.height / 2)
        let rotatedToSource = sourceToRotated.inverted()

        let rotatedPoints: [CGPoint]
        if cornerPoints.count >= 4 {
            rotatedPoints = cornerPoints.prefix(4).map {
                CGPoint(x: $0.x * rotatedSize.width, y: $0.y * rotatedSize.height)
            }
        } else {
            rotatedPoints = [
                CGPoint(x: bounds.minX * rotatedSize.width, y: bounds.minY * rotatedSize.height),
                CGPoint(x: bounds.maxX * rotatedSize.width, y: bounds.minY * rotatedSize.height),
                CGPoint(x: bounds.maxX * rotatedSize.width, y: bounds.maxY * rotatedSize.height),
                CGPoint(x: bounds.minX * rotatedSize.width, y: bounds.maxY * rotatedSize.height)
            ]
        }
        let points = rotatedPoints.map { $0.applying(rotatedToSource).clamped(to: sourceSize) }

        let minX = points.map(\.x).min() ?? 0
        let maxX = points.map(\.x).max() ?? 0
        let minY = points.map(\.y).min() ?? 0
        let maxY = points.map(\.y).max() ?? 0
        let mappedBounds = CGRect(
            x: (minX / sourceSize.width).clamped(to: 0...1),
            y: (minY / sourceSize.height).clamped(to: 0...1),
            width: ((maxX - minX) / sourceSize.width).clamped(to: 0...1),
            height: ((maxY - minY) / sourceSize.height).clamped(to: 0...1)
        )
        guard mappedBounds.width > 0, mappedBounds.height > 0 else {
            return nil
        }

        return ComposerState.PlateCandidate(
            plate: plate,
            confidence: confidence,
            rawPlateText: rawPlateText,
            wasPlateCorrected: wasPlateCorrected,
            ownOcrText: ownOcrText,
            ownOcrConfidence: ownOcrConfidence,
            state: state,
            stateConfidence: stateConfidence,
            plateType: plateType,
            plateTypeLabel: plateTypeLabel,
            bounds: mappedBounds,
            cornerPoints: points.map {
                CGPoint(
                    x: ($0.x / sourceSize.width).clamped(to: 0...1),
                    y: ($0.y / sourceSize.height).clamped(to: 0...1)
                )
            },
            plateCropPreview: plateCropPreview ?? sourceImage.cropped(normalizedBounds: mappedBounds),
            videoFramePreview: videoFramePreview,
            videoFramePreviewURL: videoFramePreviewURL,
            videoFrameTimeSeconds: videoFrameTimeSeconds
        )
    }

    func withVideoFrame(preview: UIImage?, timeSeconds: Double) -> ComposerState.PlateCandidate {
        ComposerState.PlateCandidate(
            plate: plate,
            confidence: confidence,
            rawPlateText: rawPlateText,
            wasPlateCorrected: wasPlateCorrected,
            ownOcrText: ownOcrText,
            ownOcrConfidence: ownOcrConfidence,
            state: state,
            stateConfidence: stateConfidence,
            plateType: plateType,
            plateTypeLabel: plateTypeLabel,
            bounds: bounds,
            cornerPoints: cornerPoints,
            plateCropPreview: plateCropPreview,
            videoFramePreview: preview,
            videoFramePreviewURL: nil,
            videoFrameTimeSeconds: timeSeconds
        )
    }
}

private extension Optional where Wrapped == String {
    func expectedComplaintClass() -> Int? {
        let normalized = self?.lowercased() ?? ""
        if normalized.contains("bike"), normalized.contains("lane") {
            return complaintClassBlockedBikeLane
        }
        if normalized.contains("crosswalk") {
            return complaintClassBlockedCrosswalk
        }
        return nil
    }
}

private extension String {
    var nilIfEmpty: String? {
        isEmpty ? nil : self
    }
}

private extension Comparable {
    func clamped(to range: ClosedRange<Self>) -> Self {
        min(max(self, range.lowerBound), range.upperBound)
    }
}

private extension UIImage {
    func cropped(normalizedBounds bounds: CGRect) -> UIImage? {
        guard let cgImage else { return nil }
        let pixelRect = CGRect(
            x: bounds.minX.clamped(to: 0...1) * CGFloat(cgImage.width),
            y: bounds.minY.clamped(to: 0...1) * CGFloat(cgImage.height),
            width: bounds.width.clamped(to: 0...1) * CGFloat(cgImage.width),
            height: bounds.height.clamped(to: 0...1) * CGFloat(cgImage.height)
        ).integral.intersection(CGRect(x: 0, y: 0, width: cgImage.width, height: cgImage.height))
        guard pixelRect.width > 0, pixelRect.height > 0, let cropped = cgImage.cropping(to: pixelRect) else {
            return nil
        }
        return UIImage(cgImage: cropped)
    }
}

private extension Array {
    subscript(safe index: Index) -> Element? {
        indices.contains(index) ? self[index] : nil
    }
}
