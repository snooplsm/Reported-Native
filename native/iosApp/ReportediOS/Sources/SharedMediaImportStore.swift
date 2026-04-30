import Foundation

private let sharedMediaAppGroupId = "group.cab.reported.nyc"
private let sharedMediaImportKeyPrefix = "sharedMediaImport."

private struct SharedMediaImportManifest: Codable {
    struct Item: Codable {
        let relativePath: String
        let displayName: String
        let isVideo: Bool
    }

    let id: String
    let items: [Item]
}

enum SharedMediaImportStore {
    static func consumeImport(id: String) -> [ComposerState.SubmissionMedia] {
        guard let defaults = UserDefaults(suiteName: sharedMediaAppGroupId),
              let container = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: sharedMediaAppGroupId),
              let data = defaults.data(forKey: sharedMediaImportKeyPrefix + id),
              let manifest = try? JSONDecoder().decode(SharedMediaImportManifest.self, from: data) else {
            return []
        }

        defaults.removeObject(forKey: sharedMediaImportKeyPrefix + id)

        return manifest.items.compactMap { item in
            let url = container.appendingPathComponent(item.relativePath)
            guard FileManager.default.fileExists(atPath: url.path) else { return nil }
            return ComposerState.SubmissionMedia(
                fileURL: url,
                displayName: item.displayName,
                isVideo: item.isVideo
            )
        }
    }
}
