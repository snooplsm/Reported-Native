import AVFoundation
import CoreGraphics
import Foundation
import OnnxRuntimeBindings
import SharedCore
import UIKit

extension UIImage {
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

extension Detection {
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

extension CGPoint {
    func clamped(to size: CGSize) -> CGPoint {
        CGPoint(
            x: x.clamped(to: 0...size.width),
            y: y.clamped(to: 0...size.height)
        )
    }
}

extension NSMutableData {
    func asFloatArray() -> [Float] {
        let count = length / MemoryLayout<Float>.size
        let base = bytes.assumingMemoryBound(to: Float.self)
        return Array(UnsafeBufferPointer(start: base, count: count))
    }
}

extension ComposerState.PlateCandidate {
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

extension Optional where Wrapped == String {
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

extension String {
    var nilIfEmpty: String? {
        isEmpty ? nil : self
    }
}

extension Comparable {
    func clamped(to range: ClosedRange<Self>) -> Self {
        min(max(self, range.lowerBound), range.upperBound)
    }
}

extension UIImage {
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

extension Array {
    subscript(safe index: Index) -> Element? {
        indices.contains(index) ? self[index] : nil
    }
}
