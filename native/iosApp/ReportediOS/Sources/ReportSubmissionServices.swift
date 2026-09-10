import Foundation
import FirebaseAnalytics
import FirebaseCrashlytics
import ImageIO
import SharedCore
import UIKit
import UniformTypeIdentifiers

struct ReportedSubmissionValidationError: LocalizedError {
    let message: String

    var errorDescription: String? {
        message
    }
}

enum ParseMediaUploader {
    struct UploadProgress {
        let completedBytes: Int64
        let totalBytes: Int64
        let currentFileIndex: Int
        let totalFiles: Int
        let message: String

        var fileFraction: Double {
            guard totalBytes > 0 else { return 0 }
            return min(max(Double(completedBytes) / Double(totalBytes), 0), 1)
        }

        var overallFraction: Double {
            let count = max(totalFiles, 1)
            return min(max((Double(currentFileIndex) + fileFraction) / Double(count), 0), 1)
        }
    }

    static func uploadAll(
        _ media: [ComposerState.SubmissionMedia],
        onProgress: @escaping (UploadProgress) async -> Void = { _ in }
    ) async throws -> [SubmitReportMediaFile] {
        print("ReportedSubmit: media upload batch starting; count=\(media.count)")
        var uploaded: [SubmitReportMediaFile] = []
        for (index, item) in media.enumerated() {
            if let file = try await upload(item, index: index, totalFiles: media.count, onProgress: onProgress) {
                uploaded.append(file)
            }
        }
        print("ReportedSubmit: media upload batch finished; uploaded=\(uploaded.count)/\(media.count)")
        return uploaded
    }

    private static func upload(
        _ media: ComposerState.SubmissionMedia,
        index: Int,
        totalFiles: Int,
        onProgress: @escaping (UploadProgress) async -> Void
    ) async throws -> SubmitReportMediaFile? {
        guard let url = try await uploadUrl(media, index: index, totalFiles: totalFiles, onProgress: onProgress) else { return nil }
        return SubmitReportMediaFile(url: url, isVideo: media.isVideo)
    }

    private static func uploadUrl(
        _ media: ComposerState.SubmissionMedia,
        index: Int,
        totalFiles: Int,
        onProgress: @escaping (UploadProgress) async -> Void
    ) async throws -> String? {
        let payload = try await makePayload(for: media)
        let filename = sanitizeFilename(payload.filename)
        let encodedName = filename.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? filename
        guard let uploadURL = URL(string: "\(parseBaseUrl())/files/\(encodedName)") else {
            throw NSError(
                domain: "Reported.ParseMediaUploader",
                code: 1,
                userInfo: [NSLocalizedDescriptionKey: "Invalid Parse upload URL."]
            )
        }

        var request = URLRequest(url: uploadURL)
        request.httpMethod = "POST"
        request.timeoutInterval = 60
        request.setValue(parseApplicationId(), forHTTPHeaderField: "X-Parse-Application-Id")
        request.setValue(parseJavascriptKey(), forHTTPHeaderField: "X-Parse-JavaScript-Key")
        request.setValue(payload.mimeType, forHTTPHeaderField: "Content-Type")

        print("ReportedSubmit: uploading media \(index + 1)/\(totalFiles) name=\(filename) type=\(payload.mimeType) bytes=\(payload.data.count) compressed=\(payload.compressed)")
        let (responseData, response) = try await upload(
            request: request,
            data: payload.data,
            index: index,
            totalFiles: totalFiles,
            onProgress: onProgress
        )
        guard let httpResponse = response as? HTTPURLResponse else {
            throw NSError(
                domain: "Reported.ParseMediaUploader",
                code: 2,
                userInfo: [NSLocalizedDescriptionKey: "Parse media upload returned an invalid response."]
            )
        }
        print("ReportedSubmit: media upload response \(httpResponse.statusCode); name=\(filename)")
        guard (200...299).contains(httpResponse.statusCode) else {
            let body = String(data: responseData, encoding: .utf8) ?? ""
            throw NSError(
                domain: "Reported.ParseMediaUploader",
                code: httpResponse.statusCode,
                userInfo: [NSLocalizedDescriptionKey: "Media upload failed: \(httpResponse.statusCode) \(body)"]
            )
        }
        let json = try JSONSerialization.jsonObject(with: responseData) as? [String: Any]
        return json?["url"] as? String
    }

    private static func upload(
        request: URLRequest,
        data: Data,
        index: Int,
        totalFiles: Int,
        onProgress: @escaping (UploadProgress) async -> Void
    ) async throws -> (Data, URLResponse) {
        let delegate = UploadProgressDelegate { sent, total in
            Task {
                await onProgress(
                    UploadProgress(
                        completedBytes: sent,
                        totalBytes: total > 0 ? total : Int64(data.count),
                        currentFileIndex: index,
                        totalFiles: totalFiles,
                        message: "Uploading media \(index + 1) of \(totalFiles)"
                    )
                )
            }
        }
        let session = URLSession(configuration: .default, delegate: delegate, delegateQueue: nil)
        defer { session.invalidateAndCancel() }
        return try await withCheckedThrowingContinuation { continuation in
            let task = session.uploadTask(with: request, from: data) { responseData, response, error in
                if let error {
                    continuation.resume(throwing: error)
                    return
                }
                guard let responseData, let response else {
                    continuation.resume(
                        throwing: NSError(
                            domain: "Reported.ParseMediaUploader",
                            code: 3,
                            userInfo: [NSLocalizedDescriptionKey: "Parse media upload returned no response."]
                        )
                    )
                    return
                }
                continuation.resume(returning: (responseData, response))
            }
            task.resume()
        }
    }

    private static func makePayload(for media: ComposerState.SubmissionMedia) async throws -> UploadPayload {
        let original = try await Task.detached(priority: .utility) {
            try Data(contentsOf: media.fileURL)
        }.value
        let rawName = media.displayName.isEmpty ? defaultFilename(for: media) : media.displayName
        guard !media.isVideo else {
            return UploadPayload(
                data: original,
                filename: rawName,
                mimeType: mimeType(for: media),
                compressed: false
            )
        }
        return makeJpegPayload(original: original, rawName: rawName) ?? UploadPayload(
            data: original,
            filename: rawName,
            mimeType: mimeType(for: media),
            compressed: false
        )
    }

    private static func makeJpegPayload(
        original: Data,
        rawName: String
    ) -> UploadPayload? {
        guard
            let source = CGImageSourceCreateWithData(original as CFData, nil),
            let image = UIImage(data: original)?.cgImage
        else {
            return nil
        }
        let output = NSMutableData()
        guard let destination = CGImageDestinationCreateWithData(
            output as CFMutableData,
            UTType.jpeg.identifier as CFString,
            1,
            nil
        ) else {
            return nil
        }
        var properties = (CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any]) ?? [:]
        properties[kCGImageDestinationLossyCompressionQuality] = 0.84
        CGImageDestinationAddImage(destination, image, properties as CFDictionary)
        guard CGImageDestinationFinalize(destination) else {
            return nil
        }
        let baseName = rawName.replacingOccurrences(
            of: "\\.[^.]+$",
            with: "",
            options: .regularExpression
        )
        print("ReportedSubmit: JPEG image prepared; original=\(original.count) jpeg=\(output.length)")
        return UploadPayload(
            data: output as Data,
            filename: "\(baseName).jpg",
            mimeType: "image/jpeg",
            compressed: true
        )
    }

    private static func defaultFilename(for media: ComposerState.SubmissionMedia) -> String {
        let lastPath = media.fileURL.lastPathComponent
        if !lastPath.isEmpty { return lastPath }
        return media.isVideo ? "video.mov" : "photo.jpg"
    }

    private static func sanitizeFilename(_ value: String) -> String {
        let fallback = value.isEmpty ? "media" : value
        return fallback.replacingOccurrences(
            of: "[^A-Za-z0-9._-]",
            with: "_",
            options: .regularExpression
        )
    }

    private static func mimeType(for media: ComposerState.SubmissionMedia) -> String {
        if let type = UTType(filenameExtension: media.fileURL.pathExtension),
           let mimeType = type.preferredMIMEType {
            return mimeType
        }
        return media.isVideo ? "video/quicktime" : "image/jpeg"
    }

    private static func parseBaseUrl() -> String {
        let fallback = ProcessInfo.processInfo.environment["REPORTED_PARSE_SERVER_URL"] ?? "https://parseapi.back4app.com"
        let raw = RemoteConfigOverrides.shared.parseServerUrl(fallback: fallback)
        let trimmed = raw.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        if trimmed.hasSuffix("/parse") || trimmed.contains("parseapi.back4app.com") {
            return trimmed
        }
        return "\(trimmed)/parse"
    }

    private static func parseApplicationId() -> String {
        ProcessInfo.processInfo.environment["REPORTED_PARSE_APPLICATION_ID"] ?? "jkAZF8ojV4vOGnhSBjdwiMWBKpWML5tM4SWGKgOV"
    }

    private static func parseJavascriptKey() -> String {
        ProcessInfo.processInfo.environment["REPORTED_PARSE_JAVASCRIPT_KEY"] ?? "LeBKOerWTXGBGRLE0yvg2bXa5RRv4e8PuC6INEFA"
    }

    private struct UploadPayload {
        let data: Data
        let filename: String
        let mimeType: String
        let compressed: Bool
    }

    private final class UploadProgressDelegate: NSObject, URLSessionTaskDelegate {
        private let onProgress: (Int64, Int64) -> Void

        init(onProgress: @escaping (Int64, Int64) -> Void) {
            self.onProgress = onProgress
        }

        func urlSession(
            _ session: URLSession,
            task: URLSessionTask,
            didSendBodyData bytesSent: Int64,
            totalBytesSent: Int64,
            totalBytesExpectedToSend: Int64
        ) {
            onProgress(totalBytesSent, totalBytesExpectedToSend)
        }
    }
}
