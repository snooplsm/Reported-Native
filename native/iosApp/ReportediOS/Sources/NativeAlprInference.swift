import AVFoundation
import CoreGraphics
import Foundation
import OnnxRuntimeBindings
import SharedCore
import UIKit

func mergeDetections(direct: [Detection], vehicle: [Detection]) -> [Detection] {
    guard !direct.isEmpty else { return vehicle.sorted { $0.score > $1.score } }
    guard !vehicle.isEmpty else { return direct.sorted { $0.score > $1.score } }
    var merged: [Detection] = []
    (direct + vehicle).sorted { $0.score > $1.score }.forEach { detection in
        guard !merged.contains(where: { $0.iou(with: detection) > 0.55 }) else { return }
        merged.append(detection)
    }
    return merged.sorted { $0.score > $1.score }
}

func detectPlatesViaVehicleSegmentation(
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

func detectVehicles(in image: UIImage, segmenter: ORTSession, nms: ORTSession) -> [Detection] {
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

func inferVehicleSegmentationRowSize(_ flatSize: Int) -> Int {
    if flatSize % vehicleSegmentationRowSize == 0 {
        return vehicleSegmentationRowSize
    }
    if flatSize % (vehicleSegmentationClasses + 4) == 0 {
        return vehicleSegmentationClasses + 4
    }
    return vehicleSegmentationRowSize
}

func detect(in image: UIImage, session: ORTSession) -> [Detection] {
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

func detectBatched(images: [UIImage], session: ORTSession) -> [[Detection]] {
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

func runOcr(image: UIImage, session: ORTSession) -> OcrResult {
    runOcrBatched(images: [image], session: session).first ?? OcrResult(text: "", confidence: nil)
}

func runOcrBatched(images: [UIImage], session: ORTSession) -> [OcrResult] {
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

func runOcrLegacyChunk(images: [UIImage], session: ORTSession) -> [OcrResult] {
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

func decodeOcrResult(_ raw: [Float]) -> OcrResult {
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

func classifyPlateState(image: UIImage, session: ORTSession) -> PlateStateClassification? {
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

func classifyPlateStatesBatched(images: [UIImage], session: ORTSession) -> [PlateStateClassification?] {
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

func decodePlateStateClassification(_ raw: [Float]) -> PlateStateClassification? {
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

func runFloatModel(session: ORTSession, input: [Float], shape: [NSNumber]) -> [Float]? {
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
