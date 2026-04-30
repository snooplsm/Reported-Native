import UIKit
import UniformTypeIdentifiers

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

final class ShareViewController: UIViewController {
    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        importSharedMedia()
    }

    private func importSharedMedia() {
        let providers = extensionContext?.inputItems
            .compactMap { $0 as? NSExtensionItem }
            .flatMap { $0.attachments ?? [] }
            .filter { provider in
                provider.hasItemConformingToTypeIdentifier(UTType.image.identifier) ||
                    provider.hasItemConformingToTypeIdentifier(UTType.movie.identifier) ||
                    provider.hasItemConformingToTypeIdentifier(UTType.video.identifier)
            } ?? []

        guard !providers.isEmpty,
              let container = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: sharedMediaAppGroupId) else {
            extensionContext?.completeRequest(returningItems: nil)
            return
        }

        let importId = UUID().uuidString
        let importDirectory = container.appendingPathComponent("SharedMediaImports", isDirectory: true)
            .appendingPathComponent(importId, isDirectory: true)
        try? FileManager.default.createDirectory(at: importDirectory, withIntermediateDirectories: true)

        let group = DispatchGroup()
        let lock = NSLock()
        var manifestItems: [SharedMediaImportManifest.Item] = []

        for provider in providers {
            let isVideo = provider.hasItemConformingToTypeIdentifier(UTType.movie.identifier) ||
                provider.hasItemConformingToTypeIdentifier(UTType.video.identifier)
            let typeIdentifier = isVideo ? UTType.movie.identifier : UTType.image.identifier

            group.enter()
            provider.loadFileRepresentation(forTypeIdentifier: typeIdentifier) { sourceURL, _ in
                defer { group.leave() }
                guard let sourceURL else { return }

                let fallbackExtension = isVideo ? "mov" : "jpg"
                let sourceExtension = sourceURL.pathExtension.isEmpty ? fallbackExtension : sourceURL.pathExtension
                let fileName = "\(UUID().uuidString).\(sourceExtension)"
                let targetURL = importDirectory.appendingPathComponent(fileName)

                do {
                    if FileManager.default.fileExists(atPath: targetURL.path) {
                        try FileManager.default.removeItem(at: targetURL)
                    }
                    try FileManager.default.copyItem(at: sourceURL, to: targetURL)

                    let relativePath = "SharedMediaImports/\(importId)/\(fileName)"
                    let item = SharedMediaImportManifest.Item(
                        relativePath: relativePath,
                        displayName: sourceURL.lastPathComponent,
                        isVideo: isVideo
                    )
                    lock.lock()
                    manifestItems.append(item)
                    lock.unlock()
                } catch {
                    return
                }
            }
        }

        group.notify(queue: .main) {
            self.finishImport(id: importId, items: manifestItems)
        }
    }

    private func finishImport(id: String, items: [SharedMediaImportManifest.Item]) {
        guard !items.isEmpty,
              let defaults = UserDefaults(suiteName: sharedMediaAppGroupId),
              let data = try? JSONEncoder().encode(SharedMediaImportManifest(id: id, items: items)) else {
            extensionContext?.completeRequest(returningItems: nil)
            return
        }

        defaults.set(data, forKey: sharedMediaImportKeyPrefix + id)
        defaults.synchronize()

        guard let url = URL(string: "reported://share?import=\(id)") else {
            extensionContext?.completeRequest(returningItems: nil)
            return
        }

        extensionContext?.open(url) { _ in
            self.extensionContext?.completeRequest(returningItems: nil)
        }
    }
}
