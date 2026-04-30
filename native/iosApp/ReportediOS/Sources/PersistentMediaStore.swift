import Foundation

enum PersistentMediaStore {
    private static let directoryName = "ReportMedia"

    static func save(data: Data, preferredName: String, fileExtension: String) throws -> URL {
        let targetURL = try uniqueFileURL(preferredName: preferredName, fileExtension: fileExtension)
        try data.write(to: targetURL, options: .atomic)
        return targetURL
    }

    static func saveReplacing(data: Data, preferredName: String, fileExtension: String) throws -> URL {
        let targetURL = try fileURL(preferredName: preferredName, fileExtension: fileExtension)
        if FileManager.default.fileExists(atPath: targetURL.path) {
            try? FileManager.default.removeItem(at: targetURL)
        }
        try data.write(to: targetURL, options: .atomic)
        return targetURL
    }

    static func persistableURL(for sourceURL: URL, displayName: String) -> URL {
        guard FileManager.default.fileExists(atPath: sourceURL.path) else {
            return sourceURL
        }
        guard !isStoredMedia(sourceURL) else {
            return sourceURL
        }
        do {
            let ext = sourceURL.pathExtension.isEmpty ? "dat" : sourceURL.pathExtension
            let destinationURL = try uniqueFileURL(preferredName: displayName, fileExtension: ext)
            try FileManager.default.copyItem(at: sourceURL, to: destinationURL)
            return destinationURL
        } catch {
            return sourceURL
        }
    }

    static func restoredURL(from storedURL: URL) -> URL? {
        if FileManager.default.fileExists(atPath: storedURL.path) {
            return storedURL
        }
        do {
            let fallbackURL = try directoryURL().appendingPathComponent(storedURL.lastPathComponent)
            return FileManager.default.fileExists(atPath: fallbackURL.path) ? fallbackURL : nil
        } catch {
            return nil
        }
    }

    static func deleteStoredMedia(_ urls: [URL]) {
        for url in urls where isStoredMedia(url) {
            try? FileManager.default.removeItem(at: url)
        }
    }

    private static func isStoredMedia(_ url: URL) -> Bool {
        guard let directory = try? directoryURL().standardizedFileURL else {
            return false
        }
        return url.standardizedFileURL.path.hasPrefix(directory.path)
    }

    private static func uniqueFileURL(preferredName: String, fileExtension: String) throws -> URL {
        let directory = try directoryURL()
        let sanitizedBase = sanitizedBaseName(preferredName)
        let sanitizedExtension = sanitizedExtension(fileExtension)
        var candidate = directory.appendingPathComponent(sanitizedBase).appendingPathExtension(sanitizedExtension)
        if !FileManager.default.fileExists(atPath: candidate.path) {
            return candidate
        }
        candidate = directory
            .appendingPathComponent("\(sanitizedBase)-\(UUID().uuidString)")
            .appendingPathExtension(sanitizedExtension)
        return candidate
    }

    private static func fileURL(preferredName: String, fileExtension: String) throws -> URL {
        try directoryURL()
            .appendingPathComponent(sanitizedBaseName(preferredName))
            .appendingPathExtension(sanitizedExtension(fileExtension))
    }

    private static func directoryURL() throws -> URL {
        let root = try FileManager.default.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )
        let directory = root.appendingPathComponent(directoryName, isDirectory: true)
        if !FileManager.default.fileExists(atPath: directory.path) {
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        }
        return directory
    }

    private static func sanitizedBaseName(_ name: String) -> String {
        let base = (name as NSString).deletingPathExtension
        let allowed = CharacterSet.alphanumerics.union(CharacterSet(charactersIn: "-_"))
        let sanitized = base.unicodeScalars.map { allowed.contains($0) ? Character($0) : "-" }
        let value = String(sanitized).trimmingCharacters(in: CharacterSet(charactersIn: "-_"))
        return value.isEmpty ? UUID().uuidString : value
    }

    private static func sanitizedExtension(_ value: String) -> String {
        let allowed = CharacterSet.alphanumerics
        let scalars = value.lowercased().unicodeScalars.filter { allowed.contains($0) }
        let ext = String(String.UnicodeScalarView(scalars))
        return ext.isEmpty ? "dat" : ext
    }
}
