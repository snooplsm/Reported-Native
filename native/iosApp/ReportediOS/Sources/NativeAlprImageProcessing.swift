import AVFoundation
import CoreGraphics
import Foundation
import OnnxRuntimeBindings
import SharedCore
import UIKit

func segmentPlateCrop(
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

func maskToOrientedBox(raw: [Float], side: Int, cropSize: CGSize) -> [CGPoint]? {
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

func estimateAngle(from points: [CGPoint]) -> CGFloat {
    guard points.count >= 2 else { return 0 }
    let dx = points[1].x - points[0].x
    let dy = points[1].y - points[0].y
    return normalizePlateAngle(atan2(dy, dx) * 180 / .pi)
}

func boundingDetection(points: [CGPoint], score: Float, fallback: Detection) -> Detection {
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

func refinedPlateCrop(
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

func findBestRotatedPlateMatch(
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

func bestMatch(in detections: [Detection], for expected: Detection) -> Detection? {
    guard !detections.isEmpty else { return nil }
    let maxDistance = max(expected.width, expected.height) * rotatedMatchDistanceMultiplier
    return detections.min { lhs, rhs in
        lhs.centerDistanceSquared(from: expected) < rhs.centerDistanceSquared(from: expected)
    }.flatMap { detection in
        detection.centerDistanceSquared(from: expected) <= maxDistance * maxDistance ? detection : nil
    }
}

func expandedPlateDetection(image: UIImage, detection: Detection) -> Detection {
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

func estimateDeskewAngle(source: UIImage) -> CGFloat {
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

func normalizePlateAngle(_ degrees: CGFloat) -> CGFloat {
    var normalized = degrees
    while normalized > 45 { normalized -= 90 }
    while normalized < -45 { normalized += 90 }
    return normalized.clamped(to: -CGFloat(maxDeskewDegrees)...CGFloat(maxDeskewDegrees))
}

func crop(image: UIImage, detection: Detection) -> UIImage? {
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

func letterbox(image: UIImage, targetSize: Int) -> LetterboxedImage {
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

func vehicleSegmentationImage(image: UIImage) -> VehicleSegmentationImage {
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

func rgbFloatData(from image: UIImage, width: Int, height: Int) -> [Float] {
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

func grayscaleBytes(from image: UIImage, width: Int, height: Int) -> [UInt8] {
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

func rgbaBytes(from image: UIImage, width: Int, height: Int) -> [UInt8] {
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

func normalizePlateText(_ raw: String) -> String {
    return raw.replacingOccurrences(of: "_", with: "")
        .filter { $0.isLetter || $0.isNumber }
        .uppercased()
}
